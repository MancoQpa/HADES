package com.harmonicmonitor;

import com.harmonicmonitor.alarm.AlarmEngine;
import com.harmonicmonitor.comm.IEC61850Communicator;
import com.harmonicmonitor.comm.MeasurementPoller;
import com.harmonicmonitor.comm.SimulatedPoller;
import com.harmonicmonitor.comtrade.ComtradeTriggerEngine;
import com.harmonicmonitor.gui.*;
import com.harmonicmonitor.gui.ComtradePanel;
import com.harmonicmonitor.model.*;
import com.harmonicmonitor.model.AlarmEvent;
import com.harmonicmonitor.storage.DataStorage;
import com.harmonicmonitor.storage.MLDataExporter;

import java.io.File;
import java.util.Optional;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
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
    private final List<FeederConfig>         feederConfigs  = new ArrayList<>();
    private final List<IEC61850Communicator> communicators  = new ArrayList<>();
    private final List<MeasurementPoller>    pollers        = new ArrayList<>();
    private final ConcurrentHashMap<String, FeederMeasurement> latestMeasurements
        = new ConcurrentHashMap<>();

    // ── Theme ──────────────────────────────────────────────────────────────────
    public static boolean isDark = false;

    // ── Disparo periódico ──────────────────────────────────────────────────────
    private ScheduledExecutorService periodicScheduler;

    // ── Stage / UI refs ────────────────────────────────────────────────────────
    private Stage    primaryStage;
    private TabPane  tabPane;
    private Label    statusMsgLbl;
    private Label    statusStatsLbl;
    private Label    pollingIndicatorLbl;
    private Label    feederCountLbl;
    private int      currentTabIndex = 0;

    // ── Panels ─────────────────────────────────────────────────────────────────
    private DashboardPanel          dashboardPanel;
    private HarmonicsPanel          harmonicsPanel;
    private AlarmsPanel             alarmsPanel;
    private FeederMgmtPanel         feederMgmtPanel;
    private MultiFeederMonitorPanel multiFeederPanel;
    private TrendChartsPanel        trendsPanel;
    private CompliancePanel         compliancePanel;
    private HelpPanel               helpPanel;
    private AboutPanel              aboutPanel;
    private ComtradePanel           comtradePanel;
    private ComparativaPanel        comparativaPanel;
    private RecordsPanel            recordsPanel;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        dataStorage.initialize();
        setupAlarmStorage();
        startPeriodicTrigger();
        buildScene();
        if (feederConfigs.isEmpty()) showWelcomeDialog();

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

    private void buildScene() {
        Theme.apply(isDark);
        createPanels();

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildTabPane());
        root.setBottom(buildStatusBar());
        root.setStyle("-fx-background-color: " + Theme.BG + ";");

        java.net.URL cssUrl = getClass().getResource(Theme.css());

        Scene scene = new Scene(root);
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        primaryStage.setScene(scene);

        // Restore tab selection after rebuild
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

    // ── Toolbar ────────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.getStyleClass().add("toolbar-main");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 16, 8, 16));

        // Logo / title
        VBox titleBox = new VBox(1);
        Label appTitle = new Label("⚡ HADES v1.0");
        appTitle.getStyleClass().add("toolbar-title");
        Label appSub = new Label("Harmonic Analysis for Detection of Electronic Signatures  ·  MT 23kV  ·  IEC 61850");
        appSub.getStyleClass().add("toolbar-subtitle");
        titleBox.getChildren().addAll(appTitle, appSub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Feeder count badge
        feederCountLbl = new Label("● 0 feeders");
        feederCountLbl.getStyleClass().addAll("lbl-accent");
        feederCountLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        // Polling spinner
        Label pollLbl = new Label("Polling:");
        pollLbl.getStyleClass().add("lbl-secondary");
        Spinner<Integer> pollSpinner = new Spinner<>(1, 60, 2);
        pollSpinner.setPrefWidth(65);
        Label secLbl = new Label("s");
        secLbl.getStyleClass().add("lbl-muted");
        Button btnApply = new Button("OK");
        btnApply.getStyleClass().add("btn-sm");
        btnApply.setOnAction(e -> setPollingInterval(pollSpinner.getValue() * 1000));

        Button btnTheme = new Button(isDark ? "☀ Claro" : "🌙 Oscuro");
        btnTheme.getStyleClass().add("btn-sm");
        btnTheme.setOnAction(e -> {
            isDark = !isDark;
            buildScene();
        });

        toolbar.getChildren().addAll(
            titleBox, spacer,
            feederCountLbl,
            new Separator(Orientation.VERTICAL),
            pollLbl, pollSpinner, secLbl, btnApply,
            new Separator(Orientation.VERTICAL),
            btnTheme
        );

        return toolbar;
    }

    // ── TabPane ────────────────────────────────────────────────────────────────

    private TabPane buildTabPane() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: " + Theme.BG + ";");

        tabPane.getTabs().addAll(
            buildTab("📊 RESUMEN",     dashboardPanel.getNode()),
            buildTab("〜 ARMÓNICOS",    harmonicsPanel.getNode()),
            buildTab("📋 MULTI",        multiFeederPanel.getNode()),
            buildTab("📈 TENDENCIAS",   trendsPanel.getNode()),
            buildTab("🔔 ALARMAS",      alarmsPanel.getNode()),
            buildTab("🔌 FEEDERS",      feederMgmtPanel.getNode()),
            buildTab("📏 NORMAS",       compliancePanel.getNode()),
            buildTab("📁 COMTRADE",     comtradePanel.getNode()),
            buildTab("📼 REGISTROS",    recordsPanel.getNode()),
            buildTab("📖 AYUDA",        helpPanel.getNode()),
            buildTab("🏆 ¿POR QUÉ?",   comparativaPanel.getNode()),
            buildTab("ℹ ACERCA DE",    aboutPanel.getNode())
        );

        tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) currentTabIndex = newV.intValue();
        });

        return tabPane;
    }

    private Tab buildTab(String title, Node content) {
        Tab tab = new Tab(title, content);
        return tab;
    }

    // ── Status bar ─────────────────────────────────────────────────────────────

    private HBox buildStatusBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 16, 4, 16));

        Label statusIcon = new Label("●");
        statusIcon.getStyleClass().add("lbl-success");

        statusMsgLbl = new Label("Listo  —  HADES iniciado");
        statusMsgLbl.getStyleClass().add("status-msg");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusStatsLbl = new Label("");
        statusStatsLbl.getStyleClass().add("status-stats");

        pollingIndicatorLbl = new Label("● SIN FEEDERS");
        pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #78909C;");
        Tooltip pollTip = new Tooltip("Estado de polling IEC 61850");
        Tooltip.install(pollingIndicatorLbl, pollTip);

        Label sep2 = new Label("|");
        sep2.getStyleClass().add("lbl-muted");

        Label sep = new Label("|");
        sep.getStyleClass().add("lbl-muted");

        Label timeLbl = new Label("");
        timeLbl.getStyleClass().add("status-time");

        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), ev ->
            timeLbl.setText(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")))));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        bar.getChildren().addAll(statusIcon, statusMsgLbl, spacer, statusStatsLbl, sep2, pollingIndicatorLbl, sep, timeLbl);
        return bar;
    }

    // ── Measurement dispatch ───────────────────────────────────────────────────

    private void onMeasurement(FeederMeasurement m, FeederConfig cfg) {
        latestMeasurements.put(m.getFeederId(), m);
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

    // ── Feeder management ──────────────────────────────────────────────────────

    public void addDemoFeeder() {
        int n = feederConfigs.size() + 1;
        FeederConfig cfg = new FeederConfig("AL-DEMO-" + n, "127.0.0.1");
        cfg.setFeederName("Alimentador Demo " + n);
        cfg.setIedName("IED_DEMO_" + n);
        cfg.setNominalVoltageKv(23.0);
        cfg.setNominalCurrentA(150.0 + (n - 1) * 25);
        cfg.setShortCircuitMva(80.0);
        cfg.setFeederCapacitanceMicroF(3.5);
        feederConfigs.add(cfg);
        comtradeTrigger.prepareFeederDir(cfg.getFeederId());
        startSimulatedPoller(cfg);
        updateFeedersIndicator();
        if (multiFeederPanel != null) multiFeederPanel.refreshFeeders();
        if (trendsPanel      != null) trendsPanel.refreshFeederSelector();
        setStatusMessage("Feeder demo agregado: " + cfg.getFeederName());
    }

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

    /** Agrega un feeder con SimulatedPoller usando el perfil definido en cfg.getSimProfile(). */
    public void addSimulatedFeeder(FeederConfig cfg) {
        // Evitar ID duplicado
        String id = cfg.getFeederId();
        boolean dup = feederConfigs.stream().anyMatch(c -> c.getFeederId().equals(id));
        if (dup) {
            cfg.setFeederId(id + "-" + (feederConfigs.size() + 1));
        }
        feederConfigs.add(cfg);
        comtradeTrigger.prepareFeederDir(cfg.getFeederId());
        startSimulatedPoller(cfg);
        updateFeedersIndicator();
        if (multiFeederPanel != null) multiFeederPanel.refreshFeeders();
        if (trendsPanel      != null) trendsPanel.refreshFeederSelector();
        if (alarmsPanel      != null) alarmsPanel.refreshFeederFilter();
        String profileName = cfg.getSimProfile() != null ? cfg.getSimProfile().displayName : "Demo";
        setStatusMessage("Feeder simulado: " + cfg.getFeederName() + "  [" + profileName + "]");
    }

    public void addFeeder(FeederConfig cfg) {
        boolean dup = feederConfigs.stream().anyMatch(c -> c.getFeederId().equals(cfg.getFeederId()));
        if (dup) {
            setStatusMessage("Ya existe un feeder con ID \"" + cfg.getFeederId() + "\" — ignorado");
            return;
        }
        feederConfigs.add(cfg);
        comtradeTrigger.prepareFeederDir(cfg.getFeederId());
        IEC61850Communicator comm = new IEC61850Communicator(cfg);
        communicators.add(comm);
        comm.addListener(ev -> Platform.runLater(() -> {
            setStatusMessage(ev.feederId + ": " + ev.message);
            // Actualizar LED de conexion en pestana MULTI
            if (multiFeederPanel != null) {
                IEC61850Communicator.State ledState;
                switch (ev.type) {
                    case CONNECTED:
                    case MODEL_LOADED:  ledState = IEC61850Communicator.State.CONNECTED;    break;
                    case DISCONNECTED:  ledState = IEC61850Communicator.State.DISCONNECTED; break;
                    case CONNECTION_FAILED:
                    case READ_ERROR:    ledState = IEC61850Communicator.State.ERROR;         break;
                    default:            ledState = IEC61850Communicator.State.CONNECTING;   break;
                }
                multiFeederPanel.updateConnectionState(cfg.getFeederId(), ledState);
            }
            // Actualizar dashboard con estado de conexion
            if (dashboardPanel != null) {
                IEC61850Communicator.State ds;
                switch (ev.type) {
                    case CONNECTED:
                    case MODEL_LOADED:  ds = IEC61850Communicator.State.CONNECTED;    break;
                    case DISCONNECTED:  ds = IEC61850Communicator.State.DISCONNECTED; break;
                    case CONNECTION_FAILED:
                    case READ_ERROR:    ds = IEC61850Communicator.State.ERROR;         break;
                    default:            ds = IEC61850Communicator.State.CONNECTING;   break;
                }
                dashboardPanel.updateConnectionState(cfg.getFeederId(), ds);
            }
            // Registrar perdida/recuperacion de conexion como alarma
            if (ev.type == IEC61850Communicator.CommEvent.Type.DISCONNECTED ||
                ev.type == IEC61850Communicator.CommEvent.Type.CONNECTION_FAILED) {
                AlarmEvent connAlarm = new AlarmEvent(
                    AlarmEvent.Level.WARNING, cfg.getFeederId(),
                    "Conexion",
                    "Perdida de conexion [" + cfg.getFeederId() + "]: " + ev.message,
                    0.0, 0.0);
                dataStorage.storeAlarm(connAlarm);
                if (alarmsPanel != null) alarmsPanel.onAlarm(connAlarm);
            } else if (ev.type == IEC61850Communicator.CommEvent.Type.MODEL_LOADED) {
                // Registrar reconexion exitosa si ya habia habido un ciclo previo (reconnectAttempts > 0 se maneja en comm)
                AlarmEvent connOk = new AlarmEvent(
                    AlarmEvent.Level.WARNING, cfg.getFeederId(),
                    "Conexion",
                    "Conexion establecida/restablecida [" + cfg.getFeederId() + "]: " + ev.message,
                    1.0, 1.0);
                dataStorage.storeAlarm(connOk);
            }
            if (ev.type == IEC61850Communicator.CommEvent.Type.MODEL_LOADED) {
                // Advertir al usuario si el IED no expone el array de armónicos
                // y operará en modo degradado (espectro estimado, clasificación con validez reducida).
                if (!comm.isHarmonicArrayInModel()) {
                    Alert warn = new Alert(Alert.AlertType.WARNING);
                    warn.setTitle("Modo degradado — armónicos no disponibles");
                    warn.setHeaderText("El IED \"" + cfg.getIedName() + "\" no expone el array de armónicos (MHAI.HA)");
                    warn.setContentText(
                        "Sin el array H1–H13, las dimensiones espectrales del clasificador\n" +
                        "(H5/H1, H7/H1, H11/H1) serán ESTIMADAS con un perfil SMPS genérico,\n" +
                        "no medidas desde el IED.\n\n" +
                        "En modo degradado solo THD_I, CV y FP son observables reales.\n" +
                        "Los resultados de clasificación de tipo de carga tienen validez reducida\n" +
                        "y el espectro mostrado NO proviene del instrumento.\n\n" +
                        "¿Desea continuar de todas formas?");
                    ButtonType btnContinuar = new ButtonType("Continuar en modo degradado", ButtonBar.ButtonData.OK_DONE);
                    ButtonType btnCancelar  = new ButtonType("Cancelar conexión",           ButtonBar.ButtonData.CANCEL_CLOSE);
                    warn.getButtonTypes().setAll(btnContinuar, btnCancelar);
                    Optional<ButtonType> res = warn.showAndWait();
                    if (res.isEmpty() || res.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                        comm.disconnect();
                        communicators.remove(comm);
                        feederConfigs.remove(cfg);
                        setStatusMessage(cfg.getFeederId() + ": conexión cancelada — el IED no provee armónicos");
                        if (multiFeederPanel != null) {
                            multiFeederPanel.updateConnectionState(cfg.getFeederId(), IEC61850Communicator.State.DISCONNECTED);
                            multiFeederPanel.refreshFeeders();
                        }
                        updateFeedersIndicator();
                        return;
                    }
                }
                startPoller(comm, cfg);
                updateFeedersIndicator();
                if (multiFeederPanel != null) multiFeederPanel.refreshFeeders();
                if (trendsPanel      != null) trendsPanel.refreshFeederSelector();
                if (alarmsPanel      != null) alarmsPanel.refreshFeederFilter();
            }
            updatePollingIndicator();
        }));
        comm.connectAsync();
        // LED a CONNECTING inmediatamente
        if (multiFeederPanel != null)
            multiFeederPanel.updateConnectionState(cfg.getFeederId(), IEC61850Communicator.State.CONNECTING);
        updateFeedersIndicator();
        setStatusMessage("Conectando a " + cfg.getFeederName() + " (" + cfg.getIedHost() + ")...");
    }

    public void removeFeeder(String feederId) {
        // Detener y eliminar pollers asociados al feeder
        List<MeasurementPoller> toRemove = new ArrayList<>();
        for (MeasurementPoller p : pollers) {
            if (p.getConfig().getFeederId().equals(feederId)) {
                try { p.stop(); } catch (Exception ignored) {}
                toRemove.add(p);
            }
        }
        pollers.removeAll(toRemove);

        // Desconectar el comunicador IEC 61850 antes de removerlo
        communicators.stream()
            .filter(c -> c.getConfig().getFeederId().equals(feederId))
            .findFirst()
            .ifPresent(c -> { try { c.disconnect(); } catch (Exception ignored) {} });
        communicators.removeIf(c -> c.getConfig().getFeederId().equals(feederId));

        feederConfigs.removeIf(c -> c.getFeederId().equals(feederId));
        latestMeasurements.remove(feederId);
        updateFeedersIndicator();
        if (multiFeederPanel != null) multiFeederPanel.refreshFeeders();
        if (trendsPanel      != null) trendsPanel.refreshFeederSelector();
        if (alarmsPanel      != null) alarmsPanel.refreshFeederFilter();
        setStatusMessage("Feeder eliminado: " + feederId);
    }

    /** Actualiza el testigo de polling en la barra de estado. */
    public void updatePollingIndicator() {
        Platform.runLater(() -> {
            if (pollingIndicatorLbl == null) return;
            long total     = communicators.size();
            long connected = communicators.stream()
                .filter(c -> c.getState() == IEC61850Communicator.State.CONNECTED)
                .count();
            if (total == 0) {
                if (feederConfigs.isEmpty()) {
                    pollingIndicatorLbl.setText("● SIN FEEDERS");
                    pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #78909C;");
                } else {
                    pollingIndicatorLbl.setText("● SIM");
                    pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #00BCD4;");
                }
            } else if (connected == total) {
                pollingIndicatorLbl.setText("● POLLING OK (" + connected + "/" + total + ")");
                pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #00C853;");
            } else if (connected > 0) {
                pollingIndicatorLbl.setText("● PARCIAL (" + connected + "/" + total + ")");
                pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #FFB300;");
            } else {
                pollingIndicatorLbl.setText("● DESCONECTADO (0/" + total + ")");
                pollingIndicatorLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #D50000;");
            }
        });
    }

    /** Fuerza la reconexion de un feeder real desconectado o en error. */
    public void reconnectFeeder(String feederId) {
        IEC61850Communicator comm = null;
        for (IEC61850Communicator c : communicators) {
            if (c.getConfig().getFeederId().equals(feederId)) { comm = c; break; }
        }
        if (comm == null) { setStatusMessage(feederId + ": es simulado, no requiere reconexion"); return; }
        IEC61850Communicator.State st = comm.getState();
        if (st == IEC61850Communicator.State.CONNECTED || st == IEC61850Communicator.State.CONNECTING) {
            setStatusMessage(feederId + ": ya esta conectado o conectandose");
            return;
        }
        // Detener pollers existentes para evitar duplicados
        List<MeasurementPoller> toStop = new ArrayList<>();
        for (MeasurementPoller p : pollers) {
            if (p.getConfig().getFeederId().equals(feederId)) toStop.add(p);
        }
        for (MeasurementPoller p : toStop) { try { p.stop(); } catch (Exception ignored) {} }
        pollers.removeAll(toStop);

        comm.connectAsync();
        if (multiFeederPanel != null)
            multiFeederPanel.updateConnectionState(feederId, IEC61850Communicator.State.CONNECTING);
        setStatusMessage("Reconectando " + feederId + "...");
        updatePollingIndicator();
    }

    public void setPollingInterval(int ms) {
        for (MeasurementPoller p : pollers) p.setInterval(ms);
        setStatusMessage("Intervalo de polling: " + ms / 1000 + " seg");
    }

    private void startPoller(IEC61850Communicator comm, FeederConfig cfg) {
        // Detener pollers anteriores para este feeder (evita duplicados en reconexion)
        List<MeasurementPoller> existing = new ArrayList<>();
        for (MeasurementPoller px : pollers) {
            if (px.getConfig().getFeederId().equals(cfg.getFeederId())) existing.add(px);
        }
        for (MeasurementPoller px : existing) { try { px.stop(); } catch (Exception ignored) {} }
        pollers.removeAll(existing);

        MeasurementPoller p = new MeasurementPoller(cfg, comm);
        pollers.add(p);
        p.addListener(m -> onMeasurement(m, cfg));
        p.start();
    }

    private void startSimulatedPoller(FeederConfig cfg) {
        SimulatedPoller p = new SimulatedPoller(cfg);
        pollers.add(p);
        p.addListener(m -> onMeasurement(m, cfg));
        p.start();
    }

    private void updateFeedersIndicator() {
        Platform.runLater(() -> {
            int n = feederConfigs.size();
            if (feederCountLbl != null)
                feederCountLbl.setText("● " + n + " feeder" + (n != 1 ? "s" : ""));
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

    // ── CSV export ─────────────────────────────────────────────────────────────

    private void exportCsv() {
        if (feederConfigs.isEmpty()) { setStatusMessage("Sin feeders configurados"); return; }
        StringBuilder msg = new StringBuilder("Exportado: ");
        for (FeederConfig cfg : feederConfigs) {
            try {
                String path = dataStorage.exportToCsv(cfg.getFeederId(), null, null);
                msg.append(path).append("  ");
            } catch (Exception ex) {
                msg.append("[Error: ").append(cfg.getFeederId()).append("]  ");
            }
        }
        setStatusMessage(msg.toString().trim());
    }

    // ── Status message ─────────────────────────────────────────────────────────

    public void setStatusMessage(String msg) {
        Platform.runLater(() -> { if (statusMsgLbl != null) statusMsgLbl.setText(msg); });
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    // ── Disparo periódico automático ───────────────────────────────────────────

    /** Inicia el scheduler que dispara un registro COMTRADE cada 5 minutos para cada feeder activo. */
    private void startPeriodicTrigger() {
        periodicScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PeriodicTrigger-5min");
            t.setDaemon(true);
            return t;
        });
        // Primer disparo a los 10 s del inicio.
        // scheduleWithFixedDelay: espera 5 min DESPUÉS de cada ejecución completada.
        // A diferencia de scheduleAtFixedRate, NO acumula ejecuciones perdidas
        // si el sistema estuvo en pausa (suspensión, GC, etc.).
        periodicScheduler.scheduleWithFixedDelay(this::runPeriodicTrigger, 10, 300, TimeUnit.SECONDS);
        LOG.info("[PeriodicTrigger] Disparo automático programado: primer disparo en 10 s, luego cada 5 min");
    }

    /** Ejecuta el disparo periódico obligatorio para todos los feeders con medición disponible. */
    private void runPeriodicTrigger() {
        List<FeederConfig> cfgs = new ArrayList<>(feederConfigs);
        for (FeederConfig cfg : cfgs) {
            FeederMeasurement m = latestMeasurements.get(cfg.getFeederId());
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
                    FeederMeasurement m = latestMeasurements.get(event.getFeederId());
                    FeederConfig cfg = feederConfigs.stream()
                        .filter(c -> c.getFeederId().equals(event.getFeederId()))
                        .findFirst().orElse(null);
                    if (m != null && cfg != null) {
                        new Thread(() -> comtradeTrigger.triggerAlarm(m, cfg, event),
                            "ComtradeTrigger-Alarm").start();
                    }
                }
            }
        };
        alarmEngine.addListener(storageAlarmListener);
    }

    private void shutdown() {
        if (periodicScheduler != null) periodicScheduler.shutdownNow();
        for (MeasurementPoller p : pollers)          { try { p.stop();       } catch (Exception ignored) {} }
        for (IEC61850Communicator c : communicators) { try { c.disconnect(); } catch (Exception ignored) {} }
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

    public List<FeederConfig>  getFeederConfigs()      { return feederConfigs; }
    public AlarmEngine         getAlarmEngine()         { return alarmEngine; }
    public DataStorage         getDataStorage()         { return dataStorage; }
    public Stage               getPrimaryStage()        { return primaryStage; }
    public List<MeasurementPoller> getPollers()         { return pollers; }
    public ComtradeTriggerEngine getComtradeTrigger()   { return comtradeTrigger; }
    public ConcurrentHashMap<String, FeederMeasurement> getLatestMeasurements() {
        return latestMeasurements;
    }

    /** Retorna el estado de conexión del comunicador para un feeder, o null si es simulado. */
    public IEC61850Communicator.State getCommunicatorState(String feederId) {
        return communicators.stream()
            .filter(c -> c.getConfig().getFeederId().equals(feederId))
            .findFirst()
            .map(IEC61850Communicator::getState)
            .orElse(null);
    }

    /** True si el feeder corre con SimulatedPoller (sin comunicador IEC 61850). */
    public boolean isSimulatedFeeder(String feederId) {
        return communicators.stream().noneMatch(c -> c.getConfig().getFeederId().equals(feederId));
    }

    public static void main(String[] args) { launch(args); }
}
