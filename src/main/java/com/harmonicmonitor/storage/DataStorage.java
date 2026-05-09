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
 *
 * La campaña de caracterización espectral (tabla harmonic_spectra) se delega
 * a {@link SpectraCampaignStore}.
 */
public class DataStorage {

    private static final Logger LOG = Logger.getLogger(DataStorage.class.getName());
    private static final String DB_NAME = "harmonic_monitor.db";
    private static final String CSV_DIR = "exports";
    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private Connection          db;
    private boolean             initialized   = false;
    private SpectraCampaignStore spectraCampaign;

    public DataStorage() {}

    /**
     * Inicializa la base de datos SQLite.
     * Crea las tablas si no existen.
     */
    public boolean initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            db = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
            spectraCampaign = new SpectraCampaignStore(db);
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

    // ── Campaña espectral — delegación a SpectraCampaignStore ─────────────────

    /**
     * Almacena una muestra de espectro completo (H1-H50 por fase).
     *
     * @param m              Medición con espectro completo
     * @param sessionId      ID de sesión de campaña
     * @param thdRmsWindow   THD trifásico RMS agregado en la ventana de campaña
     * @param ionLagS        Segundos desde que el ION actualizó su ventana 10-min
     */
    public void storeSpectrum(FeederMeasurement m, String sessionId,
                              double thdRmsWindow, long ionLagS) {
        if (spectraCampaign == null) return;
        spectraCampaign.store(m, sessionId, thdRmsWindow, ionLagS);
    }

    /**
     * Exporta las muestras de espectro de una sesión de campaña a CSV.
     *
     * @param feederId   ID del alimentador (null = todos)
     * @param sessionId  ID de sesión (null = todas las sesiones del feeder)
     * @return           Path del CSV generado
     */
    public String exportSpectraToCsv(String feederId, String sessionId) throws IOException {
        if (spectraCampaign == null) {
            LOG.warning("exportSpectraToCsv: DB no disponible.");
            return "";
        }
        return spectraCampaign.exportToCsv(feederId, sessionId);
    }

    /**
     * Resumen estadístico de una sesión de campaña.
     */
    public String getCampaignSummary(String sessionId) {
        if (spectraCampaign == null) return "DB no disponible";
        return spectraCampaign.getSummary(sessionId);
    }

    // ── DDL ──────────────────────────────────────────────────────────────────

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

            spectraCampaign.createTable(st);

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
}
