package com.harmonicmonitor.storage;

import com.harmonicmonitor.model.FeederMeasurement;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Almacenamiento de la campaña de caracterización espectral (tabla harmonic_spectra).
 *
 * Separado de {@link DataStorage} para aislar la lógica de los 147 canales armónicos
 * (H2..H50 para tres fases) del almacenamiento básico de mediciones y alarmas.
 *
 * Requiere una {@link Connection} SQLite ya abierta e inicializada.
 */
class SpectraCampaignStore {

    private static final Logger LOG = Logger.getLogger(SpectraCampaignStore.class.getName());

    private static final String CSV_DIR = "exports";
    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Connection db;

    SpectraCampaignStore(Connection db) {
        this.db = db;
    }

    // ── DDL ──────────────────────────────────────────────────────────────────

    /**
     * Crea la tabla harmonic_spectra con todas sus columnas (fijas + armónicos).
     * Llamado desde {@link DataStorage#createTables()}.
     */
    void createTable(Statement st) throws SQLException {
        StringBuilder ddl = new StringBuilder(
            "CREATE TABLE IF NOT EXISTS harmonic_spectra (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "timestamp TEXT NOT NULL, feeder_id TEXT NOT NULL, session_id TEXT NOT NULL," +
            "i1_a REAL, i1_b REAL, i1_c REAL,");

        for (String ph : new String[]{"a","b","c"}) {
            for (int h = 2; h <= 50; h++) {
                ddl.append(String.format("h%02d_%s REAL,", h, ph));
            }
        }

        ddl.append(
            "thd_i_a REAL, thd_i_b REAL, thd_i_c REAL," +
            "thd_i_rms_window REAL," +
            "cv_current REAL, p_kw REAL, pf REAL, freq_hz REAL," +
            "spectrum_estimated INTEGER DEFAULT 0," +
            "ion_update_lag_s INTEGER DEFAULT 0)");

        st.execute(ddl.toString());
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    /**
     * Almacena una muestra de espectro completo (H1-H50 por fase).
     *
     * El espectro se normaliza por I1 de cada fase (Hn/I1, adimensional).
     *
     * @param m              Medición con espectro completo
     * @param sessionId      ID de sesión de campaña
     * @param thdRmsWindow   THD trifásico RMS agregado en la ventana de campaña
     * @param ionLagS        Segundos desde que el ION actualizó su ventana 10-min
     */
    void store(FeederMeasurement m, String sessionId, double thdRmsWindow, long ionLagS) {
        StringBuilder cols = new StringBuilder(
            "INSERT INTO harmonic_spectra (timestamp, feeder_id, session_id," +
            " i1_a, i1_b, i1_c,");
        StringBuilder vals = new StringBuilder("VALUES (?,?,?,?,?,?,");

        for (String ph : new String[]{"a","b","c"}) {
            for (int h = 2; h <= 50; h++) {
                cols.append(String.format(" h%02d_%s,", h, ph));
                vals.append("?,");
            }
        }

        cols.append(" thd_i_a, thd_i_b, thd_i_c, thd_i_rms_window," +
                    " cv_current, p_kw, pf, freq_hz," +
                    " spectrum_estimated, ion_update_lag_s)");
        vals.append("?,?,?,?,?,?,?,?,?,?)");

        String sql = cols.toString() + " " + vals.toString();

        try (PreparedStatement ps = db.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, TS_FMT.format(m.getTimestamp()));
            ps.setString(idx++, m.getFeederId());
            ps.setString(idx++, sessionId);

            double[] specA = m.getHarmonicCurrentL1();
            double[] specB = m.getHarmonicCurrentL2();
            double[] specC = m.getHarmonicCurrentL3();

            double i1a = (specA != null && specA.length > 0) ? specA[0] : m.getCurrentL1();
            double i1b = (specB != null && specB.length > 0) ? specB[0] : m.getCurrentL2();
            double i1c = (specC != null && specC.length > 0) ? specC[0] : m.getCurrentL3();

            ps.setDouble(idx++, i1a);
            ps.setDouble(idx++, i1b);
            ps.setDouble(idx++, i1c);

            for (int h = 2; h <= 50; h++) {
                double val = (specA != null && specA.length > h-1 && i1a > 1e-6)
                    ? specA[h-1] / i1a : 0.0;
                ps.setDouble(idx++, val);
            }
            for (int h = 2; h <= 50; h++) {
                double val = (specB != null && specB.length > h-1 && i1b > 1e-6)
                    ? specB[h-1] / i1b : 0.0;
                ps.setDouble(idx++, val);
            }
            for (int h = 2; h <= 50; h++) {
                double val = (specC != null && specC.length > h-1 && i1c > 1e-6)
                    ? specC[h-1] / i1c : 0.0;
                ps.setDouble(idx++, val);
            }

            ps.setDouble(idx++, m.getThdCurrentL1());
            ps.setDouble(idx++, m.getThdCurrentL2());
            ps.setDouble(idx++, m.getThdCurrentL3());
            ps.setDouble(idx++, thdRmsWindow);
            ps.setDouble(idx++, m.getCvCurrent());
            ps.setDouble(idx++, m.getActivePower());
            ps.setDouble(idx++, Math.abs(m.getPowerFactor()));
            ps.setDouble(idx++, m.getFrequency());
            ps.setInt   (idx++, m.isSpectrumEstimated() ? 1 : 0);
            ps.setLong  (idx,   ionLagS);

            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Error almacenando espectro [" + sessionId + "]: " + e.getMessage());
        }
    }

    // ── Exportación ──────────────────────────────────────────────────────────

    /**
     * Exporta las muestras de espectro de una sesión de campaña a CSV (ML-ready).
     *
     * @param feederId   ID del alimentador (null = todos)
     * @param sessionId  ID de sesión (null = todas las sesiones del feeder)
     * @return           Path del CSV generado
     */
    String exportToCsv(String feederId, String sessionId) throws IOException {
        Path dir = Paths.get(CSV_DIR);
        Files.createDirectories(dir);

        String tag = (sessionId != null) ? sessionId
            : (feederId != null ? feederId : "all");
        String filename = String.format("spectra_%s_%s.csv", tag,
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneId.systemDefault()).format(Instant.now()));
        Path csvPath = dir.resolve(filename);

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvPath.toFile()))) {
            StringBuilder hdr = new StringBuilder(
                "timestamp,feeder_id,session_id,i1_a,i1_b,i1_c");
            for (String ph : new String[]{"a","b","c"}) {
                for (int h = 2; h <= 50; h++) {
                    hdr.append(String.format(",h%02d_%s", h, ph));
                }
            }
            hdr.append(",thd_i_a,thd_i_b,thd_i_c,thd_i_rms_window");
            hdr.append(",cv_current,p_kw,pf,freq_hz,spectrum_estimated,ion_update_lag_s");
            pw.println(hdr);

            StringBuilder sql = new StringBuilder("SELECT * FROM harmonic_spectra WHERE 1=1");
            if (feederId  != null) sql.append(" AND feeder_id=?");
            if (sessionId != null) sql.append(" AND session_id=?");
            sql.append(" ORDER BY timestamp");

            try (PreparedStatement ps = db.prepareStatement(sql.toString())) {
                int p = 1;
                if (feederId  != null) ps.setString(p++, feederId);
                if (sessionId != null) ps.setString(p,   sessionId);

                ResultSet rs = ps.executeQuery();
                List<String> harmCols = new ArrayList<>();
                for (String ph : new String[]{"a","b","c"}) {
                    for (int h = 2; h <= 50; h++) {
                        harmCols.add(String.format("h%02d_%s", h, ph));
                    }
                }

                while (rs.next()) {
                    StringBuilder row = new StringBuilder();
                    row.append(rs.getString("timestamp")).append(',');
                    row.append(rs.getString("feeder_id")).append(',');
                    row.append(rs.getString("session_id")).append(',');
                    row.append(String.format("%.4f,%.4f,%.4f",
                        rs.getDouble("i1_a"),
                        rs.getDouble("i1_b"),
                        rs.getDouble("i1_c")));
                    for (String col : harmCols) {
                        row.append(String.format(",%.6f", rs.getDouble(col)));
                    }
                    row.append(String.format(",%.3f,%.3f,%.3f,%.3f",
                        rs.getDouble("thd_i_a"),
                        rs.getDouble("thd_i_b"),
                        rs.getDouble("thd_i_c"),
                        rs.getDouble("thd_i_rms_window")));
                    row.append(String.format(",%.5f,%.3f,%.4f,%.3f,%d,%d",
                        rs.getDouble("cv_current"),
                        rs.getDouble("p_kw"),
                        rs.getDouble("pf"),
                        rs.getDouble("freq_hz"),
                        rs.getInt("spectrum_estimated"),
                        rs.getLong("ion_update_lag_s")));
                    pw.println(row);
                }
            } catch (SQLException e) {
                LOG.warning("Error exportando espectros CSV: " + e.getMessage());
            }
        }
        LOG.info("Espectros exportados: " + csvPath.toAbsolutePath()
            + "  sesión=" + sessionId + "  feeder=" + feederId);
        return csvPath.toAbsolutePath().toString();
    }

    // ── Resumen ───────────────────────────────────────────────────────────────

    /**
     * Resumen estadístico de una sesión: nº muestras, período, THD_rms_window
     * medio y porcentaje con espectro estimado.
     */
    String getSummary(String sessionId) {
        String sql = "SELECT COUNT(*) as n, MIN(timestamp) as t0, MAX(timestamp) as t1," +
            " AVG(thd_i_rms_window) as thd_avg," +
            " SUM(spectrum_estimated) as estimated" +
            " FROM harmonic_spectra WHERE session_id=?";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int n   = rs.getInt("n");
                int est = rs.getInt("estimated");
                return String.format(
                    "Sesión: %s | Muestras: %d | Período: %s → %s | " +
                    "THDi_rms_medio: %.1f%% | Espectro estimado: %d (%.0f%%)",
                    sessionId, n,
                    rs.getString("t0"), rs.getString("t1"),
                    rs.getDouble("thd_avg"),
                    est, (n > 0 ? 100.0 * est / n : 0));
            }
        } catch (SQLException e) {
            LOG.warning("getSummary error: " + e.getMessage());
        }
        return "Sin datos para sesión " + sessionId;
    }
}
