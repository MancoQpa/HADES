package com.harmonicmonitor;

import com.harmonicmonitor.alarm.AlarmEngine;
import com.harmonicmonitor.comm.IEC61850Communicator;
import com.harmonicmonitor.comm.MeasurementPoller;
import com.harmonicmonitor.comtrade.ComtradeTriggerEngine;
import com.harmonicmonitor.gui.*;
import com.harmonicmonitor.gui.ComtradePanel;
import com.harmonicmonitor.model.*;
import com.harmonicmonitor.model.AlarmEvent;
import com.harmonicmonitor.storage.DataStorage;
import com.harmonicmonitor.storage.MLDataExporter;

import java.io.File;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * HADES v1.0 — SCADA Dark Theme con TabPane.
 * Monitor de armónicos y detección de cargas electrónicas en alimentadores MT 23kV.
 */
public class HarmonicMonitorApp extends Application {

    private static final Logger LOG = Logger.getLogger(HarmonicMonitorApp.class.getName());

    // ── Subsistemas ────────────────────────────────────────────────────────────
    private final DataStorage            dataStorage     = new DataStorage();
    private final AlarmEngine            alarmEngine     = new AlarmEngine();
    private final ComtradeTriggerEngine  comtradeTrigger =
        new ComtradeTriggerEngine(new File("records"));
    private final MLDataExporter         mlExporter      = new MLDataExporter();
    private AlarmEngine.AlarmListener    storageAlarmListener;

    // ── Feeders ────────────────────────────────────────────────────────────────
    final FeederLifecycleManager feederMgr =
        new FeederLifecycleManager(this, dataStorage, comtradeTrigger);

    // ── Theme ──────────────────────────────────────────────────────────────────
    public static boolean isDark = false;

    // ── Disparo periódico ──────────────────────────────────────────────────────
    private ScheduledExecutorService periodicScheduler;

    // ── Stage / UI refs ────────────────────────────────────────────────────────
    private Stage primaryStage;
    // Package-private: read/written by AppSceneBuilder
    TabPane tabPane;
    Label   statusMsgLbl;
    Label   statusStatsLbl;
    Label   pollingIndicatorLbl;
    Label   feederCountLbl;
    int     currentTabIndex = 0;

    // ── Panels (package-private: accessed by FeederLifecycleManager + AppSceneBuilder) ──
    DashboardPanel          dashboardPanel;
    HarmonicsPanel          harmonicsPanel;
    AlarmsPanel             alarmsPanel;
    FeederMgmtPanel         feederMgmtPanel;
    MultiFeederMonitorPanel multiFeederPanel;
    TrendChartsPanel        trendsPanel;
    CompliancePanel         compliancePanel;
    HelpPanel               helpPanel;
    AboutPanel              aboutPanel;
    ComtradePanel           comtradePanel;
    ComparativaPanel        comparativaPanel;
    RecordsPanel            recordsPanel;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        dataStorage.initialize();
        setupAlarmStorage();
        startPeriodicTrigger();
        buildScene();
        if (feederMgr.feederConfigs.isEmpty()) showWelcomeDialog();

        stage.setTitle("HADES v1.0  |  Harmonic Analysis for Detection of Electronic Signatures  |  IEC 61850");
        stage.setMinWidth(980);
        stage.setMinHeight(620);
        Rectangle2D vis = Screen.getPrimary().getVisualBounds();
        double w = Math.min(1440, vis.getWidth()  * 0.92);
        double h = Math.min(900,  vis.getHeight() * 0.92);
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setX(vis.getMinX() + (vis.getWidth()  - w) / 2.0);
        stage.setY(vis.getMinY() + (vis.getHeight() - h) / 2.0);
        stage.setOnCloseRequest(e -> { e.consume(); shutdown(); });
        stage.show();
    }

    // ── Scene construction ─────────────────────────────────────────────────────

    /** Package-private: called by AppSceneBuilder's theme-toggle button handler. */
    void buildScene() {
        Theme.apply(isDark);
        createPanels();

        AppSceneBuilder sb = new AppSceneBuilder(this);
        BorderPane root = new BorderPane();
        root.setTop(sb.buildToolbar());
        root.setCenter(sb.buildTabPane());
        root.setBottom(sb.buildStatusBar());
        root.setStyle("-fx-background-color: " + Theme.BG + ";");

        feederCountLbl      = sb.feederCountLbl;
        statusMsgLbl        = sb.statusMsgLbl;
        statusStatsLbl      = sb.statusStatsLbl;
        pollingIndicatorLbl = sb.pollingIndicatorLbl;
        tabPane             = sb.tabPane;

        java.net.URL cssUrl = getClass().getResource(Theme.css());
        Scene scene = new Scene(root);
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        primaryStage.setScene(scene);

        if (tabPane != null && currentTabIndex < tabPane.getTabs().size()) {
            tabPane.getSelectionModel().select(currentTabIndex);
        }
    }

    private void createPanels() {
        if (alarmsPanel != null) alarmEngine.removeListener(alarmsPanel);

        dashboardPanel   = new DashboardPanel(this);
        harmonicsPanel   = new HarmonicsPanel(this);
        alarmsPanel      = new AlarmsPanel(this, alarmEngine);
        feederMgmtPanel  = new FeederMgmtPanel(this);
        multiFeederPanel = new MultiFeederMonitorPanel(this);
        trendsPanel      = new TrendChartsPanel(this);
        compliancePanel  = new CompliancePanel(this);
        helpPanel        = new HelpPanel();
        aboutPanel       = new AboutPanel();
        comtradePanel    = new ComtradePanel(this);
        comparativaPanel = new ComparativaPanel();
        recordsPanel     = new RecordsPanel(this, comtradeTrigger, mlExporter);

        alarmEngine.addListener(alarmsPanel);
    }

    // ── Measurement dispatch ───────────────────────────────────────────────────

    /** Called by pollers (via FeederLifecycleManager); package-private intentionally. */
    void onMeasurement(FeederMeasurement m, FeederConfig cfg) {
        feederMgr.latestMeasurements.put(m.getFeederId(), m);
        dataStorage.storeMeasurement(m);
        alarmEngine.evaluate(m, cfg);
        // Evaluar triggers COMTRADE en hilo de polling (no bloquea la UI)
        comtradeTrigger.evaluate(m, cfg);
        Platform.runLater(() -> {
            if (dashboardPanel   != null) dashboardPanel.updateMeasurement(m);
            if (harmonicsPanel   != null) harmonicsPanel.updateMeasurement(m);
            if (multiFeederPanel != null) multiFeederPanel.updateMeasurement(m);
            if (trendsPanel      != null) trendsPanel.updateMeasurement(m);
            if (compliancePanel  != null) compliancePanel.updateMeasurement(m);
            if (statusStatsLbl   != null)
                statusStatsLbl.setText(String.format(
                    "V=%.2f kV   I=%.1f A   THDi=%.1f%%   P=%.0f kW   %s",
                    m.getVoltageAvg() / 1000.0, m.getCurrentAvg(),
                    m.getThdCurrentAvg(), m.getActivePower(),
                    m.getDetectedLoadType().getDisplayName()));
        });
    }

    // ── Feeder management (delegated to FeederLifecycleManager) ───────────────

    public void addDemoFeeder()                       { feederMgr.addDemoFeeder(); }

    private void showWelcomeDialog() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("HADES — Inicio");
            alert.setHeaderText("No hay feeders configurados");
            alert.setContentText("¿Conectar al ION 7400 (IP: 169.254.150.104) o iniciar en modo demostración?");
            ButtonType btnIon    = new ButtonType("Conectar ION 7400");
            ButtonType btnDemo   = new ButtonType("Modo Demo");
            ButtonType btnConfig = new ButtonType("Configurar manualmente");
            alert.getButtonTypes().setAll(btnIon, btnDemo, btnConfig);
            alert.showAndWait().ifPresent(bt -> {
                if (bt == btnIon) {
                    FeederConfig cfg = new FeederConfig("cbo2-AL1", "169.254.150.104");
                    cfg.setFeederName("Alimentador cbo2 ION7400");
                    cfg.setIedName("cbo2");
                    cfg.setMmxuPrefix("M03_");
                    cfg.setMmxuLnRef("MMXU1");
                    cfg.setLdInst("LD0");
                    cfg.setMhaiLnRef("MHAI1");
                    cfg.setMsqiLnRef("MSQI1");
                    cfg.setMmtrLnRef("MMTR1");
                    cfg.setMstaLnRef("MSTA1");
                    cfg.setNominalVoltageKv(23.0);
                    cfg.setNominalCurrentA(200.0);
                    cfg.setShortCircuitMva(80.0);
                    cfg.setPowerScaleFactor(1.0);   // ION 7400 reporta en kW, no en W
                    cfg.setPfScaleFactor(0.01);     // ION 7400 reporta FP como % (ej. 87.97 = 0.8797)
                    addFeeder(cfg);
                } else if (bt == btnDemo) {
                    addDemoFeeder();
                }
                // else: usuario va a la pestaña Feeders para configurar manualmente
            });
        });
    }

    public void addSimulatedFeeder(FeederConfig cfg)    { feederMgr.addSimulatedFeeder(cfg); }
    public void addFeeder(FeederConfig cfg)             { feederMgr.addFeeder(cfg); }
    public void removeFeeder(String feederId)           { feederMgr.removeFeeder(feederId); }

    /** Actualiza el testigo de polling en la barra de estado. */
    public void updatePollingIndicator() {
        Platform.runLater(() -> {
            if (pollingIndicatorLbl == null) return;
            long total     = feederMgr.communicators.size();
            long connected = feederMgr.communicators.stream()
                .filter(c -> c.getState() == IEC61850Communicator.State.CONNECTED)
                .count();
            if (total == 0) {
                if (feederMgr.feederConfigs.isEmpty()) {
                    pollingIndicatorLbl.setText("\u25CF SIN FEEDERS");
                    pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #78909C;");
                } else {
                    pollingIndicatorLbl.setText("\u25CF SIM");
                    pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #00BCD4;");
                }
            } else if (connected == total) {
                pollingIndicatorLbl.setText("\u25CF POLLING OK (" + connected + "/" + total + ")");
                pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #00C853;");
            } else if (connected > 0) {
                pollingIndicatorLbl.setText("\u25CF PARCIAL (" + connected + "/" + total + ")");
                pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #FFB300;");
            } else {
                pollingIndicatorLbl.setText("\u25CF DESCONECTADO (0/" + total + ")");
                pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #D50000;");
            }
        });
    }

    /** Fuerza la reconexion de un feeder real desconectado o en error. */
    public void reconnectFeeder(String feederId)   { feederMgr.reconnectFeeder(feederId); }
    public void setPollingInterval(int ms)         { feederMgr.setPollingInterval(ms); }

    /** Called by FeederLifecycleManager after collection mutations. Package-private intentionally. */
    void updateFeedersIndicator() {
        Platform.runLater(() -> {
            int n = feederMgr.feederConfigs.size();
            if (feederCountLbl != null)
                feederCountLbl.setText("\u25CF " + n + " feeder" + (n != 1 ? "s" : ""));
        });
        updatePollingIndicator();
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    public void selectPanel(int idx) {
        Platform.runLater(() -> {
            if (tabPane != null && idx >= 0 && idx < tabPane.getTabs().size()) {
                tabPane.getSelectionModel().select(idx);
            }
        });
    }

    /** Navega al Dashboard y selecciona el feeder indicado. */
    public void selectFeederOnDashboard(String feederId) {
        Platform.runLater(() -> {
            selectPanel(0);
            if (dashboardPanel != null) dashboardPanel.selectFeeder(feederId);
        });
    }

    // ── Status message ─────────────────────────────────────────────────────────

    public void setStatusMessage(String msg) {
        Platform.runLater(() -> { if (statusMsgLbl != null) statusMsgLbl.setText(msg); });
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    // ── Disparo periódico automático ───────────────────────────────────────────

    /** Inicia el scheduler que dispara un registro COMTRADE cada 5 minutos para cada feeder activo. */
    private void startPeriodicTrigger() {
        periodicScheduler = AppExecutors.newDaemonScheduler("PeriodicTrigger-5min");
        // Primer disparo a los 10 s del inicio.
        // scheduleWithFixedDelay: espera 5 min DESPUÉS de cada ejecución completada.
        // A diferencia de scheduleAtFixedRate, NO acumula ejecuciones perdidas
        // si el sistema estuvo en pausa (suspensión, GC, etc.).
        periodicScheduler.scheduleWithFixedDelay(this::runPeriodicTrigger, 10, 300, TimeUnit.SECONDS);
        LOG.info("[PeriodicTrigger] Disparo automático programado: primer disparo en 10 s, luego cada 5 min");
    }

    /** Ejecuta el disparo periódico obligatorio para todos los feeders con medición disponible. */
    private void runPeriodicTrigger() {
        List<FeederConfig> cfgs = new ArrayList<>(feederMgr.feederConfigs);
        for (FeederConfig cfg : cfgs) {
            FeederMeasurement m = feederMgr.latestMeasurements.get(cfg.getFeederId());
            if (m != null) {
                try {
                    comtradeTrigger.triggerScheduled(m, cfg);
                    LOG.info("[PeriodicTrigger] Registro guardado: " + cfg.getFeederId());
                } catch (Exception ex) {
                    LOG.warning("[PeriodicTrigger] Error en feeder " + cfg.getFeederId()
                                + ": " + ex.getMessage());
                }
            } else {
                LOG.info("[PeriodicTrigger] Sin medición disponible para: " + cfg.getFeederId());
            }
        }
        setStatusMessage("Registro periódico guardado — " + cfgs.size() + " feeder(s)");
    }

    private void setupAlarmStorage() {
        if (storageAlarmListener != null) alarmEngine.removeListener(storageAlarmListener);
        storageAlarmListener = new AlarmEngine.AlarmListener() {
            @Override
            public void onAlarm(com.harmonicmonitor.model.AlarmEvent event) {
                dataStorage.storeAlarm(event);
                // Forzar registro COMTRADE en alarmas críticas y detecciones
                com.harmonicmonitor.model.AlarmEvent.Level lvl = event.getLevel();
                if (lvl == com.harmonicmonitor.model.AlarmEvent.Level.CRITICAL
                        || lvl == com.harmonicmonitor.model.AlarmEvent.Level.DETECTION) {
                    FeederMeasurement m = feederMgr.latestMeasurements.get(event.getFeederId());
                    FeederConfig cfg = feederMgr.feederConfigs.stream()
                        .filter(c -> c.getFeederId().equals(event.getFeederId()))
                        .findFirst().orElse(null);
                    if (m != null && cfg != null) {
                        AppExecutors.ioPool().execute(
                            () -> comtradeTrigger.triggerAlarm(m, cfg, event));
                    }
                }
            }
        };
        alarmEngine.addListener(storageAlarmListener);
    }

    private void shutdown() {
        if (periodicScheduler != null) periodicScheduler.shutdownNow();
        AppExecutors.shutdownAll();
        if (recordsPanel != null) recordsPanel.shutdown();
        for (MeasurementPoller p : feederMgr.pollers)          { try { p.stop();       } catch (Exception ignored) {} }
        for (IEC61850Communicator c : feederMgr.communicators) { try { c.disconnect(); } catch (Exception ignored) {} }
        dataStorage.close();
        Platform.exit();
    }

    // ── Public accessors ───────────────────────────────────────────────────────

    /** Navega a la pestaña COMTRADE y carga el archivo indicado */
    public void openComtradeFile(java.io.File cfgFile) {
        Platform.runLater(() -> {
            // Índice de la pestaña COMTRADE (posición 7 en el tab pane)
            if (tabPane != null) {
                for (int i = 0; i < tabPane.getTabs().size(); i++) {
                    if (tabPane.getTabs().get(i).getText().contains("COMTRADE")) {
                        tabPane.getSelectionModel().select(i);
                        break;
                    }
                }
            }
            if (comtradePanel != null) comtradePanel.loadFile(cfgFile);
        });
    }

    public List<FeederConfig>  getFeederConfigs()      { return feederMgr.feederConfigs; }
    public AlarmEngine         getAlarmEngine()         { return alarmEngine; }
    public DataStorage         getDataStorage()         { return dataStorage; }
    public Stage               getPrimaryStage()        { return primaryStage; }
    public List<MeasurementPoller> getPollers()         { return feederMgr.pollers; }
    public ComtradeTriggerEngine getComtradeTrigger()   { return comtradeTrigger; }
    public ConcurrentHashMap<String, FeederMeasurement> getLatestMeasurements() {
        return feederMgr.latestMeasurements;
    }

    /** Retorna el estado de conexion del comunicador para un feeder, o null si es simulado. */
    public IEC61850Communicator.State getCommunicatorState(String feederId) {
        return feederMgr.getCommunicatorState(feederId);
    }

    /** True si el feeder corre con SimulatedPoller (sin comunicador IEC 61850). */
    public boolean isSimulatedFeeder(String feederId) {
        return feederMgr.isSimulatedFeeder(feederId);
    }

    public static void main(String[] args) { launch(args); }
}
