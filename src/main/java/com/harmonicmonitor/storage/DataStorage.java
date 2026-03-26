package com.harmonicmonitor.storage;

import com.harmonicmonitor.model.AlarmEvent;
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
 * Módulo de almacenamiento local de mediciones y alarmas.
 *
 * Estrategia dual:
 *   1. SQLite: historial estructurado, consultas, tendencias.
 *   2. CSV: exportación simple, compatibilidad con Excel/MatLab.
 *
 * Base de datos SQLite: harmonic_monitor.db en el directorio de ejecución.
 * Exportaciones CSV: directorio "exports/" relativo al directorio de ejecución.
 */
public class DataStorage {

    private static final Logger LOG = Logger.getLogger(DataStorage.class.getName());
    private static final String DB_NAME   = "harmonic_monitor.db";
    private static final String CSV_DIR   = "exports";
    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private Connection db;
    private boolean    initialized = false;

    public DataStorage() {}

    /**
     * Inicializa la base de datos SQLite.
     * Crea las tablas si no existen.
     */
    public boolean initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            db = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
            createTables();
            initialized = true;
            LOG.info("Base de datos SQLite inicializada: " + DB_NAME);
            return true;
        } catch (ClassNotFoundException e) {
            LOG.warning("SQLite JDBC no disponible. Solo CSV habilitado.");
            return false;
        } catch (SQLException e) {
            LOG.warning("Error inicializando SQLite: " + e.getMessage() + ". Solo CSV habilitado.");
            return false;
        }
    }

    /**
     * Almacena una medición en la base de datos.
     */
    public void storeMeasurement(FeederMeasurement m) {
        if (!initialized || db == null) return;
        String sql = "INSERT INTO measurements " +
            "(timestamp, feeder_id, v_l1, v_l2, v_l3, i_l1, i_l2, i_l3, " +
            "p_kw, q_kvar, s_kva, pf, freq_hz, " +
            "thd_v_l1, thd_v_l2, thd_v_l3, thd_i_l1, thd_i_l2, thd_i_l3, " +
            "cv_current, h5h1, h7h1, h11h1, h13h1, " +
            "resonance_freq, resonance_order, load_type) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, TS_FMT.format(m.getTimestamp()));
            ps.setString(2, m.getFeederId());
            ps.setDouble(3,  m.getVoltageL1());
            ps.setDouble(4,  m.getVoltageL2());
            ps.setDouble(5,  m.getVoltageL3());
            ps.setDouble(6,  m.getCurrentL1());
            ps.setDouble(7,  m.getCurrentL2());
            ps.setDouble(8,  m.getCurrentL3());
            ps.setDouble(9,  m.getActivePower());
            ps.setDouble(10, m.getReactivePower());
            ps.setDouble(11, m.getApparentPower());
            ps.setDouble(12, m.getPowerFactor());
            ps.setDouble(13, m.getFrequency());
            ps.setDouble(14, m.getThdVoltageL1());
            ps.setDouble(15, m.getThdVoltageL2());
            ps.setDouble(16, m.getThdVoltageL3());
            ps.setDouble(17, m.getThdCurrentL1());
            ps.setDouble(18, m.getThdCurrentL2());
            ps.setDouble(19, m.getThdCurrentL3());
            ps.setDouble(20, m.getCvCurrent());
            ps.setDouble(21, m.getH5h1Ratio());
            ps.setDouble(22, m.getH7h1Ratio());
            ps.setDouble(23, m.getH11h1Ratio());
            ps.setDouble(24, m.getH13h1Ratio());
            ps.setDouble(25, m.getResonanceFrequency());
            ps.setInt   (26, m.getResonanceOrder());
            ps.setString(27, m.getDetectedLoadType().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Error almacenando medición: " + e.getMessage());
        }
    }

    /**
     * Almacena un evento de alarma en la base de datos.
     */
    public void storeAlarm(AlarmEvent alarm) {
        if (!initialized || db == null) return;
        String sql = "INSERT INTO alarms (timestamp, feeder_id, level, parameter, message, value, threshold) " +
            "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, TS_FMT.format(alarm.getTimestamp()));
            ps.setString(2, alarm.getFeederId());
            ps.setString(3, alarm.getLevel().name());
            ps.setString(4, alarm.getParameter());
            ps.setString(5, alarm.getMessage());
            ps.setDouble(6, alarm.getMeasuredValue());
            ps.setDouble(7, alarm.getThreshold());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Error almacenando alarma: " + e.getMessage());
        }
    }

    /**
     * Recupera las últimas N mediciones de un feeder.
     */
    public List<FeederMeasurement> getRecentMeasurements(String feederId, int limit) {
        List<FeederMeasurement> result = new ArrayList<>();
        if (!initialized || db == null) return result;
        String sql = "SELECT * FROM measurements WHERE feeder_id=? ORDER BY timestamp DESC LIMIT ?";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, feederId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                FeederMeasurement m = new FeederMeasurement(feederId, feederId,
                    Instant.parse(rs.getString("timestamp").replace(" ", "T") + "Z"));
                m.setVoltageL1(rs.getDouble("v_l1"));
                m.setVoltageL2(rs.getDouble("v_l2"));
                m.setVoltageL3(rs.getDouble("v_l3"));
                m.setCurrentL1(rs.getDouble("i_l1"));
                m.setCurrentL2(rs.getDouble("i_l2"));
                m.setCurrentL3(rs.getDouble("i_l3"));
                m.setActivePower(rs.getDouble("p_kw"));
                m.setThdCurrentL1(rs.getDouble("thd_i_l1"));
                m.setThdCurrentL2(rs.getDouble("thd_i_l2"));
                m.setThdCurrentL3(rs.getDouble("thd_i_l3"));
                result.add(m);
            }
        } catch (SQLException e) {
            LOG.warning("Error consultando mediciones: " + e.getMessage());
        }
        return result;
    }

    /**
     * Exporta las mediciones de un feeder a CSV.
     *
     * @param feederId  ID del alimentador
     * @param fromTime  Inicio del período (null = sin límite)
     * @param toTime    Fin del período (null = hasta ahora)
     * @return          Path del archivo CSV generado
     */
    public String exportToCsv(String feederId, Instant fromTime, Instant toTime) throws IOException {
        Path dir = Paths.get(CSV_DIR);
        Files.createDirectories(dir);

        String filename = String.format("mediciones_%s_%s.csv",
            feederId, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneId.systemDefault()).format(Instant.now()));
        Path csvPath = dir.resolve(filename);

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvPath.toFile()))) {
            // Encabezado (voltajes en kV, igual que el dashboard)
            pw.println("Timestamp,FeederID,V_L1(kV),V_L2(kV),V_L3(kV),I_L1(A),I_L2(A),I_L3(A)," +
                "P(kW),Q(kVAR),S(kVA),FP,Freq(Hz)," +
                "THDv_L1(%),THDv_L2(%),THDv_L3(%)," +
                "THDi_L1(%),THDi_L2(%),THDi_L3(%)," +
                "CV_I,H5/H1,H7/H1,H11/H1,H13/H1," +
                "FResHz,HResOrden,TipoCarga");

            if (initialized && db != null) {
                String sql = "SELECT * FROM measurements WHERE feeder_id=? ORDER BY timestamp";
                List<Object> params = new ArrayList<>();
                params.add(feederId);
                if (fromTime != null && toTime != null) {
                    sql = "SELECT * FROM measurements WHERE feeder_id=? AND timestamp BETWEEN ? AND ? ORDER BY timestamp";
                    params.add(TS_FMT.format(fromTime));
                    params.add(TS_FMT.format(toTime));
                }
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        // Voltajes divididos por 1000: BD almacena en V, CSV exporta en kV
                        pw.printf("%s,%s,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.3f,%.3f,%.3f,%.3f,%.3f," +
                            "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f," +
                            "%.4f,%.4f,%.4f,%.4f,%.4f," +
                            "%.1f,%d,%s%n",
                            rs.getString("timestamp"), rs.getString("feeder_id"),
                            rs.getDouble("v_l1") / 1000.0, rs.getDouble("v_l2") / 1000.0,
                            rs.getDouble("v_l3") / 1000.0,
                            rs.getDouble("i_l1"), rs.getDouble("i_l2"), rs.getDouble("i_l3"),
                            rs.getDouble("p_kw"), rs.getDouble("q_kvar"), rs.getDouble("s_kva"),
                            rs.getDouble("pf"), rs.getDouble("freq_hz"),
                            rs.getDouble("thd_v_l1"), rs.getDouble("thd_v_l2"), rs.getDouble("thd_v_l3"),
                            rs.getDouble("thd_i_l1"), rs.getDouble("thd_i_l2"), rs.getDouble("thd_i_l3"),
                            rs.getDouble("cv_current"), rs.getDouble("h5h1"), rs.getDouble("h7h1"),
                            rs.getDouble("h11h1"), rs.getDouble("h13h1"),
                            rs.getDouble("resonance_freq"), rs.getInt("resonance_order"),
                            rs.getString("load_type"));
                    }
                } catch (SQLException e) {
                    LOG.warning("Error exportando CSV: " + e.getMessage());
                }
            }
        }
        LOG.info("CSV exportado: " + csvPath.toAbsolutePath());
        return csvPath.toAbsolutePath().toString();
    }

    public void close() {
        if (db != null) {
            try { db.close(); } catch (SQLException ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Campaña de caracterización espectral — tabla harmonic_spectra
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Almacena una muestra de espectro completo (H1-H50 por fase) en la tabla
     * harmonic_spectra.
     *
     * El espectro se normaliza por I1 de cada fase (Hn/I1, adimensional) para
     * que las muestras de distintos feeders sean comparables independientemente
     * de la magnitud de corriente. I1 absoluto se guarda por separado.
     *
     * Nota sobre el ION 7400 (clase 0.2S, IEC 62053-22 — NO PQM Clase A):
     *   Los valores THDi_L1/L2/L3 son mediciones instantáneas/ventana corta del
     *   equipo, de alta precisión pero sin agregación normativa 10-min interna.
     *   thdRmsWindow es la agregación RMS cuadrática implementada por
     *   SpectralRecorder sobre las muestras de polling del intervalo de campaña.
     *
     * @param m              Medición con espectro completo (del MeasurementPoller)
     * @param sessionId      ID de sesión de campaña (agrupa muestras de la misma semana)
     * @param thdRmsWindow   THD trifásico RMS agregado en la ventana de campaña
     * @param ionLagS        Segundos desde que el ION actualizó su ventana 10-min
     */
    public void storeSpectrum(FeederMeasurement m, String sessionId,
                              double thdRmsWindow, long ionLagS) {
        if (!initialized || db == null) return;

        // Construir SQL dinámico con las 49 columnas de armónicos H2..H50
        // por cada fase (147 columnas de armónicos + escalares)
        StringBuilder cols = new StringBuilder(
            "INSERT INTO harmonic_spectra (timestamp, feeder_id, session_id," +
            " i1_a, i1_b, i1_c,");
        StringBuilder vals = new StringBuilder("VALUES (?,?,?,?,?,?,");

        // H02..H50 para las tres fases
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

            // Armónicos normalizados H2..H50 fase A
            for (int h = 2; h <= 50; h++) {
                double val = (specA != null && specA.length > h-1 && i1a > 1e-6)
                    ? specA[h-1] / i1a : 0.0;
                ps.setDouble(idx++, val);
            }
            // Fase B
            for (int h = 2; h <= 50; h++) {
                double val = (specB != null && specB.length > h-1 && i1b > 1e-6)
                    ? specB[h-1] / i1b : 0.0;
                ps.setDouble(idx++, val);
            }
            // Fase C
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

    /**
     * Exporta las muestras de espectro de una sesión de campaña a CSV.
     *
     * Formato ML-ready: una fila por muestra, columnas:
     *   timestamp, feeder_id, session_id, i1_a, i1_b, i1_c,
     *   h02_a..h50_a (49 cols), h02_b..h50_b, h02_c..h50_c,
     *   thd_i_a, thd_i_b, thd_i_c, thd_i_rms_window,
     *   cv_current, p_kw, pf, freq_hz, spectrum_estimated, ion_update_lag_s
     *
     * Total: 3 + 3 + 147 + 4 + 5 = 162 columnas por fila.
     *
     * @param feederId   ID del alimentador (null = todos)
     * @param sessionId  ID de sesión (null = todas las sesiones del feeder)
     * @return           Path del CSV generado
     */
    public String exportSpectraToCsv(String feederId, String sessionId) throws IOException {
        Path dir = Paths.get(CSV_DIR);
        Files.createDirectories(dir);

        String tag = (sessionId != null) ? sessionId
            : (feederId != null ? feederId : "all");
        String filename = String.format("spectra_%s_%s.csv", tag,
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneId.systemDefault()).format(Instant.now()));
        Path csvPath = dir.resolve(filename);

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvPath.toFile()))) {
            // Encabezado
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

            if (!initialized || db == null) {
                LOG.warning("exportSpectraToCsv: DB no disponible.");
                return csvPath.toAbsolutePath().toString();
            }

            // Query dinámica con filtros opcionales
            StringBuilder sql = new StringBuilder("SELECT * FROM harmonic_spectra WHERE 1=1");
            if (feederId  != null) sql.append(" AND feeder_id=?");
            if (sessionId != null) sql.append(" AND session_id=?");
            sql.append(" ORDER BY timestamp");

            try (PreparedStatement ps = db.prepareStatement(sql.toString())) {
                int p = 1;
                if (feederId  != null) ps.setString(p++, feederId);
                if (sessionId != null) ps.setString(p,   sessionId);

                ResultSet rs = ps.executeQuery();
                // Construir lista de columnas de armónicos para iterar
                java.util.List<String> harmCols = new java.util.ArrayList<>();
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

    /**
     * Resumen estadístico de una sesión de campaña: cuántas muestras,
     * período, THD_rms_window medio, porcentaje con espectro estimado.
     * Útil para la GUI antes de exportar.
     */
    public String getCampaignSummary(String sessionId) {
        if (!initialized || db == null) return "DB no disponible";
        String sql = "SELECT COUNT(*) as n, MIN(timestamp) as t0, MAX(timestamp) as t1," +
            " AVG(thd_i_rms_window) as thd_avg," +
            " SUM(spectrum_estimated) as estimated" +
            " FROM harmonic_spectra WHERE session_id=?";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int n    = rs.getInt("n");
                int est  = rs.getInt("estimated");
                return String.format(
                    "Sesión: %s | Muestras: %d | Período: %s → %s | " +
                    "THDi_rms_medio: %.1f%% | Espectro estimado: %d (%.0f%%)",
                    sessionId, n,
                    rs.getString("t0"), rs.getString("t1"),
                    rs.getDouble("thd_avg"),
                    est, (n > 0 ? 100.0 * est / n : 0));
            }
        } catch (SQLException e) {
            LOG.warning("getCampaignSummary error: " + e.getMessage());
        }
        return "Sin datos para sesión " + sessionId;
    }

    private void createTables() throws SQLException {
        try (Statement st = db.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS measurements (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "timestamp TEXT, feeder_id TEXT," +
                "v_l1 REAL, v_l2 REAL, v_l3 REAL," +
                "i_l1 REAL, i_l2 REAL, i_l3 REAL," +
                "p_kw REAL, q_kvar REAL, s_kva REAL, pf REAL, freq_hz REAL," +
                "thd_v_l1 REAL, thd_v_l2 REAL, thd_v_l3 REAL," +
                "thd_i_l1 REAL, thd_i_l2 REAL, thd_i_l3 REAL," +
                "cv_current REAL, h5h1 REAL, h7h1 REAL, h11h1 REAL, h13h1 REAL," +
                "resonance_freq REAL, resonance_order INTEGER, load_type TEXT)");

            st.execute("CREATE TABLE IF NOT EXISTS alarms (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "timestamp TEXT, feeder_id TEXT, level TEXT," +
                "parameter TEXT, message TEXT, value REAL, threshold REAL)");

            // ── Tabla de espectros para campaña de caracterización ML ──────────
            // Columnas de armónicos H2..H50 generadas dinámicamente por código
            // (SQLite las crea la primera vez que se hace INSERT con storeSpectrum)
            // Aquí solo se crean las columnas fijas; las de armónicos se añaden
            // con ALTER TABLE IF NOT EXISTS la primera vez que se usa.
            createSpectraTable(st);

            st.execute("CREATE INDEX IF NOT EXISTS idx_meas_feeder_ts " +
                "ON measurements(feeder_id, timestamp)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_alarm_feeder_ts " +
                "ON alarms(feeder_id, timestamp)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_spectra_session " +
                "ON harmonic_spectra(session_id, timestamp)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_spectra_feeder " +
                "ON harmonic_spectra(feeder_id, timestamp)");
        }
    }

    /**
     * Crea la tabla harmonic_spectra con todas sus columnas (fijas + armónicos).
     * Se llama una sola vez desde createTables(). Incluye las 147 columnas de
     * armónicos normalizados H02..H50 para las tres fases.
     */
    private void createSpectraTable(Statement st) throws SQLException {
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
}
