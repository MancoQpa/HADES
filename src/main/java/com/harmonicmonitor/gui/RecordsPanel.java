package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.comtrade.ComtradeTriggerEngine;
import com.harmonicmonitor.comtrade.ComtradeTriggerEngine.RecordEntry;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.storage.MLDataExporter;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RecordsPanel {

    private final HarmonicMonitorApp       app;
    private final ComtradeTriggerEngine    triggerEngine;
    private final MLDataExporter           mlExporter;
    private final VBox root;

    private final ObservableList<RecordEntry> tableItems = FXCollections.observableArrayList();
    private TableView<RecordEntry> table;
    private Label statusLbl;
    private Label countLbl;

    private final CharacterizationController charController;

    // ──────────────────────────────────────────────────────────────────────────

    public RecordsPanel(HarmonicMonitorApp app,
                        ComtradeTriggerEngine triggerEngine,
                        MLDataExporter mlExporter) {
        this.app           = app;
        this.triggerEngine = triggerEngine;
        this.mlExporter    = mlExporter;
        charController = new CharacterizationController(app, mlExporter);
        root = buildRoot();

        triggerEngine.addListener(entry -> Platform.runLater(() -> addEntry(entry)));
        Platform.runLater(this::refreshFromEngine);
    }

    public Node getNode() { return root; }

    /** Llamar desde HarmonicMonitorApp.shutdown() para detener el scheduler. */
    public void shutdown() {
        charController.shutdown();
    }

    // ── Construcción de UI ────────────────────────────────────────────────────

    private VBox buildRoot() {
        VBox vbox = new VBox(0);
        vbox.setStyle("-fx-background-color: " + Theme.BG + ";");
        table = new RecordsTableBuilder(tableItems, this::handleOpenInViewer).build();
        vbox.getChildren().addAll(
            buildHeader(),
            buildToolbar(),
            charController.getNode(),
            table,
            buildStatusBar()
        );
        VBox.setVgrow(table, Priority.ALWAYS);
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

}
