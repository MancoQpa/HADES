package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.comtrade.ComtradeTriggerEngine;
import com.harmonicmonitor.comtrade.ComtradeTriggerEngine.RecordEntry;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.storage.MLDataExporter;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RecordsPanel {

    private final HarmonicMonitorApp       app;
    private final ComtradeTriggerEngine    triggerEngine;
    private final MLDataExporter           mlExporter;
    private final VBox root;

    private final ObservableList<RecordEntry> tableItems = FXCollections.observableArrayList();
    private TableView<RecordEntry> table;
    private Label statusLbl;
    private Label countLbl;

    // ── Caracterización espectral ──────────────────────────────────────────────
    private Button   btnChar;
    private Label    charStatusLbl;
    private HBox     charBanner;
    private Timeline charPulse;
    private ScheduledExecutorService charScheduler;
    private volatile boolean         characterizing  = false;
    private final AtomicInteger      charSampleCount = new AtomicInteger(0);
    private volatile Instant         charStartTime;

    // ── Estilos del botón ──────────────────────────────────────────────────────
    private static final String STYLE_CHAR_OFF =
        "-fx-background-color: #E65C00;" +
        "-fx-text-fill: white;" +
        "-fx-font-size: 13px; -fx-font-weight: bold;" +
        "-fx-padding: 10 28;" +
        "-fx-background-radius: 8;" +
        "-fx-border-color: #FF8C00; -fx-border-width: 2; -fx-border-radius: 8;" +
        "-fx-cursor: hand;";

    private static final String STYLE_CHAR_ON_A =
        "-fx-background-color: #107C10;" +
        "-fx-text-fill: white;" +
        "-fx-font-size: 13px; -fx-font-weight: bold;" +
        "-fx-padding: 10 28;" +
        "-fx-background-radius: 8;" +
        "-fx-border-color: #FFFFFF; -fx-border-width: 2; -fx-border-radius: 8;" +
        "-fx-cursor: hand;";

    private static final String STYLE_CHAR_ON_B =
        "-fx-background-color: #0A5C0A;" +
        "-fx-text-fill: #CCFFCC;" +
        "-fx-font-size: 13px; -fx-font-weight: bold;" +
        "-fx-padding: 10 28;" +
        "-fx-background-radius: 8;" +
        "-fx-border-color: #50FF50; -fx-border-width: 2; -fx-border-radius: 8;" +
        "-fx-cursor: hand;";

    // ──────────────────────────────────────────────────────────────────────────

    public RecordsPanel(HarmonicMonitorApp app,
                        ComtradeTriggerEngine triggerEngine,
                        MLDataExporter mlExporter) {
        this.app           = app;
        this.triggerEngine = triggerEngine;
        this.mlExporter    = mlExporter;
        root = buildRoot();

        triggerEngine.addListener(entry -> Platform.runLater(() -> addEntry(entry)));
        Platform.runLater(this::refreshFromEngine);
    }

    public Node getNode() { return root; }

    /** Llamar desde HarmonicMonitorApp.shutdown() para detener el scheduler. */
    public void shutdown() {
        stopCharacterization();
    }

    // ── Construcción de UI ────────────────────────────────────────────────────

    private VBox buildRoot() {
        VBox vbox = new VBox(0);
        vbox.setStyle("-fx-background-color: " + Theme.BG + ";");

        vbox.getChildren().addAll(
            buildHeader(),
            buildToolbar(),
            buildCharBanner(),    // ← banner de caracterización
            buildTable(),
            buildStatusBar()
        );

        VBox.setVgrow(buildTable(), Priority.ALWAYS);
        return vbox;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(14, 18, 10, 18));
        h.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER
                + "; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title    = new Label("📼  Registros COMTRADE");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        Label subtitle = new Label(
            "Registros automáticos por trigger normativo  ·  IEEE C37.111-1999  ·  IEC 61000 / IEEE 519 / EN 50160");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        countLbl = new Label("0 registros");
        countLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #0078D4; -fx-font-weight: bold;");

        h.getChildren().addAll(titleBox, spacer, countLbl);
        return h;
    }

    private HBox buildToolbar() {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(8, 18, 8, 18));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: " + Theme.BG + ";");

        Button btnManual = new Button("⚡ Disparo manual");
        btnManual.setStyle("-fx-background-color: #0078D4; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-padding: 5 12; -fx-background-radius: 4;");
        btnManual.setTooltip(new Tooltip("Graba un registro COMTRADE ahora mismo (sin condición de trigger)"));
        btnManual.setOnAction(e -> handleManualTrigger());

        Button btnViewer = new Button("📂 Abrir en visor");
        btnViewer.setStyle("-fx-background-color: #E8F5EC; -fx-text-fill: #107C10; "
                + "-fx-padding: 5 12; -fx-background-radius: 4;");
        btnViewer.setOnAction(e -> handleOpenInViewer());

        Button btnReport = new Button("📄 Ver reporte");
        btnReport.setStyle("-fx-background-color: #EEE0FF; -fx-text-fill: #881798; "
                + "-fx-padding: 5 12; -fx-background-radius: 4;");
        btnReport.setOnAction(e -> handleOpenReport());

        Button btnFolder = new Button("🗂 Carpeta");
        btnFolder.setStyle("-fx-background-color: #F5F0D8; -fx-text-fill: " + Theme.TEXT + "; "
                + "-fx-padding: 5 12; -fx-background-radius: 4;");
        btnFolder.setOnAction(e -> handleOpenFolder());

        Button btnRefresh = new Button("↻ Refrescar");
        btnRefresh.setStyle("-fx-background-color: #E5F0FA; -fx-text-fill: #0078D4; "
                + "-fx-padding: 5 12; -fx-background-radius: 4;");
        btnRefresh.setOnAction(e -> refreshFromEngine());

        Button btnClear = new Button("✕ Limpiar lista");
        btnClear.setStyle("-fx-background-color: #FFE8E8; -fx-text-fill: #C42B1C; "
                + "-fx-padding: 5 12; -fx-background-radius: 4;");
        btnClear.setTooltip(new Tooltip("Limpia la lista en pantalla (no borra archivos del disco)"));
        btnClear.setOnAction(e -> { tableItems.clear(); updateCount(); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label trigInfo = new Label(
            "Triggers: THDv>6.5% · THDi>8% · FP<0.85 · Desbal>2% · Detección de carga · Alarmas CRITICAL");
        trigInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-style: italic;");

        bar.getChildren().addAll(
            btnManual,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            btnViewer, btnReport, btnFolder,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            btnRefresh, btnClear,
            spacer, trigInfo);
        return bar;
    }

    /**
     * Banner de caracterización espectral.
     * Contiene el botón grande y el indicador de estado de captura.
     */
    private HBox buildCharBanner() {
        charBanner = new HBox(16);
        charBanner.setAlignment(Pos.CENTER_LEFT);
        charBanner.setPadding(new Insets(10, 18, 10, 18));
        charBanner.setStyle(
            "-fx-background-color: #1A1A2E;" +
            "-fx-border-color: #E65C00;" +
            "-fx-border-width: 0 0 2 0;");

        // Botón principal
        btnChar = new Button("🧬  INICIAR CARACTERIZACIÓN ESPECTRAL");
        btnChar.setStyle(STYLE_CHAR_OFF);
        btnChar.setTooltip(new Tooltip(
            "Inicia la captura de datos espectrales cada 1 minuto para\n" +
            "construir un dataset de entrenamiento de modelos ML.\n" +
            "Los datos se guardan en <feeder>_dataset.csv dentro de cada carpeta de feeder."));
        btnChar.setOnAction(e -> toggleCharacterization());

        // Indicador de estado
        charStatusLbl = new Label("  Sin captura activa");
        charStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #808080; -fx-font-style: italic;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label("Genera  <feeder>_dataset.csv  →  listo para pandas / sklearn");
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: #606080; -fx-font-style: italic;");

        charBanner.getChildren().addAll(btnChar, charStatusLbl, spacer, hint);
        return charBanner;
    }

    // ── Lógica de caracterización ─────────────────────────────────────────────

    private void toggleCharacterization() {
        if (characterizing) stopCharacterization();
        else                startCharacterization();
    }

    private void startCharacterization() {
        if (characterizing) return;

        List<FeederConfig> feeders = app.getFeederConfigs();
        if (feeders.isEmpty()) {
            showInfo("Sin feeders", "Agregue al menos un feeder antes de iniciar la caracterización.");
            return;
        }

        characterizing  = true;
        charSampleCount.set(0);
        charStartTime   = Instant.now();

        // Scheduler dedicado: primer disparo inmediato, luego cada 5 minutos
        charScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CharSpectral-5min");
            t.setDaemon(true);
            return t;
        });
        charScheduler.scheduleWithFixedDelay(
            this::appendCharRow, 0, 60, TimeUnit.SECONDS);

        // Animación de pulso en el botón
        charPulse = new Timeline(
            new KeyFrame(Duration.millis(600), e ->
                btnChar.setStyle(STYLE_CHAR_ON_A)),
            new KeyFrame(Duration.millis(1200), e ->
                btnChar.setStyle(STYLE_CHAR_ON_B))
        );
        charPulse.setCycleCount(Animation.INDEFINITE);
        charPulse.play();

        btnChar.setText("⏹  FINALIZAR CARACTERIZACIÓN ESPECTRAL");

        charBanner.setStyle(
            "-fx-background-color: #0A2A0A;" +
            "-fx-border-color: #50FF50;" +
            "-fx-border-width: 0 0 2 0;");

        // Timeline de reloj que actualiza el label cada segundo
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1),
            e -> Platform.runLater(this::updateCharStatus)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
        // Guardamos la ref en el botón para poder detenerla
        btnChar.setUserData(clock);

        updateCharStatus();
        app.setStatusMessage("🧬 Caracterización espectral iniciada — capturando cada 1 min");
    }

    private void stopCharacterization() {
        if (!characterizing) return;
        characterizing = false;

        if (charScheduler != null) { charScheduler.shutdownNow(); charScheduler = null; }
        if (charPulse     != null) { charPulse.stop();            charPulse     = null; }

        // Detener el reloj
        if (btnChar.getUserData() instanceof Timeline) {
            ((Timeline) btnChar.getUserData()).stop();
            btnChar.setUserData(null);
        }

        btnChar.setText("🧬  INICIAR CARACTERIZACIÓN ESPECTRAL");
        btnChar.setStyle(STYLE_CHAR_OFF);

        charBanner.setStyle(
            "-fx-background-color: #1A1A2E;" +
            "-fx-border-color: #E65C00;" +
            "-fx-border-width: 0 0 2 0;");

        int total = charSampleCount.get();
        charStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #A0A0A0; -fx-font-style: italic;");
        charStatusLbl.setText(String.format(
            "  Captura finalizada  —  %d muestra%s guardada%s",
            total, total != 1 ? "s" : "", total != 1 ? "s" : ""));

        app.setStatusMessage(String.format(
            "🧬 Caracterización finalizada — %d muestras en <feeder>_dataset.csv", total));
    }

    /** Ejecutado en el hilo del scheduler cada 1 minuto. */
    private void appendCharRow() {
        List<FeederConfig> cfgs = new ArrayList<>(app.getFeederConfigs());
        int added = 0;
        for (FeederConfig cfg : cfgs) {
            FeederMeasurement m = app.getLatestMeasurements().get(cfg.getFeederId());
            if (m != null) {
                try {
                    mlExporter.appendRow(m, app.getComtradeTrigger().getFeederDir(cfg.getFeederId()));
                    added++;
                } catch (Exception ex) {
                    // log silencioso
                }
            }
        }
        final int n = added;
        charSampleCount.addAndGet(n);
        Platform.runLater(this::updateCharStatus);
    }

    private void updateCharStatus() {
        if (!characterizing || charStartTime == null) return;
        long secs  = ChronoUnit.SECONDS.between(charStartTime, Instant.now());
        long hh    = secs / 3600;
        long mm    = (secs % 3600) / 60;
        long ss    = secs % 60;
        int  count = charSampleCount.get();
        charStatusLbl.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #50FF50;");
        charStatusLbl.setText(String.format(
            "  ● CAPTURANDO  |  %d muestra%s  |  %02d:%02d:%02d  |  próx. muestra en ~1 min",
            count, count != 1 ? "s" : "", hh, mm, ss));
    }

    // ── Tabla ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<RecordEntry> buildTable() {
        table = new TableView<>(tableItems);
        table.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + ";");
        table.setPlaceholder(buildPlaceholder());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<RecordEntry, String> colTs = new TableColumn<>("Fecha / Hora");
        colTs.setMinWidth(160); colTs.setMaxWidth(200);
        colTs.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestampStr()));
        colTs.setCellFactory(c -> styledCell());

        TableColumn<RecordEntry, String> colFeeder = new TableColumn<>("Feeder");
        colFeeder.setMinWidth(100); colFeeder.setMaxWidth(160);
        colFeeder.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().feederId));
        colFeeder.setCellFactory(c -> styledCell());

        TableColumn<RecordEntry, String> colLevel = new TableColumn<>("Nivel");
        colLevel.setMinWidth(90); colLevel.setMaxWidth(110);
        colLevel.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().level.displayName()));
        colLevel.setCellFactory(c -> levelCell());

        TableColumn<RecordEntry, String> colCause = new TableColumn<>("Causa");
        colCause.setMinWidth(130); colCause.setMaxWidth(200);
        colCause.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().cause));
        colCause.setCellFactory(c -> styledCell());

        TableColumn<RecordEntry, String> colReason = new TableColumn<>("Descripción / Norma");
        colReason.setCellValueFactory(d -> {
            String r = d.getValue().reason;
            return new SimpleStringProperty(r.contains("\n") ? r.substring(0, r.indexOf('\n')) : r);
        });
        colReason.setCellFactory(c -> styledCell());

        TableColumn<RecordEntry, String> colFile = new TableColumn<>("Archivo COMTRADE");
        colFile.setMinWidth(220); colFile.setMaxWidth(320);
        colFile.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().cfgFile != null ? d.getValue().cfgFile.getName() : "—"));
        colFile.setCellFactory(c -> styledCell());

        table.getColumns().addAll(colTs, colFeeder, colLevel, colCause, colReason, colFile);

        table.setRowFactory(t -> new TableRow<RecordEntry>() {
            private void refresh() {
                RecordEntry item = getItem();
                if (item == null || isEmpty()) { setStyle("-fx-background-color: transparent;"); return; }
                if (isSelected()) { setStyle("-fx-background-color: #0078D4;"); return; }
                String bg;
                switch (item.level) {
                    case CRITICAL:  bg = "#FF2D5530"; break;
                    case PQ_RISK:   bg = "#C42B1C22"; break;
                    case DETECTION: bg = "#88179822"; break;
                    case WARNING:   bg = "#CA501018"; break;
                    default:        bg = "transparent"; break;
                }
                setStyle("-fx-background-color: " + bg + ";");
            }
            { selectedProperty().addListener((obs, o, n) -> refresh()); }
            @Override protected void updateItem(RecordEntry item, boolean empty) {
                super.updateItem(item, empty); refresh();
            }
        });

        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2) handleOpenInViewer(); });
        return table;
    }

    private Node buildPlaceholder() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        Label icon = new Label("📼");
        icon.setStyle("-fx-font-size: 48px;");
        Label msg = new Label("No hay registros COMTRADE aún");
        msg.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-weight: bold;");
        Label hint = new Label(
            "Los registros se generan automáticamente cuando los valores\n"
            + "superan los límites normativos o al detectar cargas electrónicas.\n\n"
            + "Use '⚡ Disparo manual' para capturar el estado actual de un feeder.\n"
            + "Use '🧬 INICIAR CARACTERIZACIÓN' para acumular dataset de ML.");
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-text-alignment: center;");
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        box.getChildren().addAll(icon, msg, hint);
        return box;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(5, 18, 5, 18));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER
                + "; -fx-border-width: 1 0 0 0;");
        statusLbl = new Label("Directorio: " + new File("records").getAbsolutePath());
        statusLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label normLabel = new Label(
            "Normas: IEC 61000-3-6 · IEEE 519-2022 · EN 50160 · IEC 61000-4-30 · IEEE C37.111-1999");
        normLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-style: italic;");
        bar.getChildren().addAll(statusLbl, spacer, normLabel);
        return bar;
    }

    // ── Cell factories ────────────────────────────────────────────────────────

    private <T> TableCell<RecordEntry, T> styledCell() {
        return new TableCell<RecordEntry, T>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else { setText(item.toString()); setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;"); }
            }
        };
    }

    private TableCell<RecordEntry, String> levelCell() {
        return new TableCell<RecordEntry, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                String color;
                switch (item) {
                    case "CRÍTICO":   color = "#FF4060"; break;
                    case "PQ-RIESGO": color = "#FF8040"; break;
                    case "DETECCIÓN": color = "#A070FF"; break;
                    case "WARNING":   color = "#F0A030"; break;
                    default:          color = "#90A0B0"; break;
                }
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-font-size: 11px;");
            }
        };
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleManualTrigger() {
        List<FeederConfig> feeders = app.getFeederConfigs();
        if (feeders.isEmpty()) {
            showInfo("Sin feeders", "No hay feeders configurados."); return;
        }
        FeederConfig chosen;
        if (feeders.size() == 1) {
            chosen = feeders.get(0);
        } else {
            ChoiceDialog<FeederConfig> dlg = new ChoiceDialog<>(feeders.get(0), feeders);
            dlg.setTitle("Disparo manual");
            dlg.setHeaderText("Seleccionar feeder para grabar");
            dlg.setContentText("Feeder:");
            var result = dlg.showAndWait();
            if (result.isEmpty()) return;
            chosen = result.get();
        }
        FeederMeasurement m = app.getLatestMeasurements().get(chosen.getFeederId());
        if (m == null) {
            showInfo("Sin datos", "No hay medición disponible para " + chosen.getFeederName()); return;
        }
        final FeederConfig cfg   = chosen;
        final FeederMeasurement meas = m;
        new Thread(() -> triggerEngine.triggerManual(meas, cfg), "ManualTrigger").start();
        app.setStatusMessage("Disparo manual: " + chosen.getFeederName());
    }

    private void handleOpenInViewer() {
        RecordEntry entry = table.getSelectionModel().getSelectedItem();
        if (entry == null || entry.cfgFile == null) { showInfo("Sin selección", "Seleccione un registro."); return; }
        if (!entry.cfgFile.exists()) { showInfo("No encontrado", entry.cfgFile.getAbsolutePath()); return; }
        app.openComtradeFile(entry.cfgFile);
    }

    private void handleOpenReport() {
        RecordEntry entry = table.getSelectionModel().getSelectedItem();
        if (entry == null || entry.reportFile == null || !entry.reportFile.exists()) {
            showInfo("Sin reporte", "No hay reporte para este registro."); return;
        }
        openDesktop(entry.reportFile);
    }

    private void handleOpenFolder() {
        File dir = new File("records");
        if (!dir.exists()) dir.mkdirs();
        openDesktop(dir);
    }

    private void openDesktop(File f) {
        try {
            if (Desktop.isDesktopSupported())
                new Thread(() -> {
                    try { Desktop.getDesktop().open(f); }
                    catch (IOException ex) {
                        Platform.runLater(() -> showInfo("Error", ex.getMessage()));
                    }
                }, "OpenDesktop").start();
        } catch (Exception ex) { showInfo("Error", ex.getMessage()); }
    }

    // ── Actualización ─────────────────────────────────────────────────────────

    private void addEntry(RecordEntry entry) {
        tableItems.add(0, entry);
        updateCount();
        table.getSelectionModel().select(0);
        table.scrollTo(0);
        if (statusLbl != null)
            statusLbl.setText("Nuevo registro: "
                + (entry.cfgFile != null ? entry.cfgFile.getName() : entry.cause)
                + "  —  Dir: " + new File("records").getAbsolutePath());
    }

    private void refreshFromEngine() {
        tableItems.setAll(triggerEngine.getRecords());
        updateCount();
    }

    private void updateCount() {
        int n = tableItems.size();
        if (countLbl != null)
            countLbl.setText(n + " registro" + (n != 1 ? "s" : ""));
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // fix: import faltante para List en appendCharRow
    private static class ArrayList<T> extends java.util.ArrayList<T> {
        ArrayList(java.util.Collection<? extends T> c) { super(c); }
    }
}
