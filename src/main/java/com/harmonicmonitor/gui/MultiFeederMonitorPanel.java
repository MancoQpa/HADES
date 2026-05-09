package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.comm.IEC61850Communicator;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MultiFeederMonitorPanel — SCADA table view of all feeders simultaneously.
 */
public class MultiFeederMonitorPanel {

    private final HarmonicMonitorApp app;
    private final BorderPane root;

    private TableView<FeederRow> table;
    private ObservableList<FeederRow> allData;
    private FilteredList<FeederRow> filteredData;

    private TextField searchField;
    private Label lastUpdateLbl;

    // Map feederId -> FeederRow for quick update
    private final ConcurrentHashMap<String, FeederRow> rowMap = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public MultiFeederMonitorPanel(HarmonicMonitorApp app) {
        this.app = app;
        root = buildUI();
    }

    public Node getNode() { return root; }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setTop(buildHeader());
        pane.setCenter(buildTableArea());
        return pane;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(12, 16, 12, 16));
        h.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("📋 MONITOR MULTI-FEEDER");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("🔍 Buscar feeder...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());

        Button btnExport = new Button("Exportar CSV");
        btnExport.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 12 5 12; -fx-cursor: hand;");
        btnExport.setOnAction(e -> exportCsv());

        lastUpdateLbl = new Label("Última actualización: —");
        lastUpdateLbl.setStyle("-fx-text-fill: #0078D4; -fx-font-size: 11px;");

        h.getChildren().addAll(title, spacer, searchField, btnExport, lastUpdateLbl);
        return h;
    }

    private VBox buildTableArea() {
        VBox box = new VBox(0);
        box.setStyle("-fx-background-color: " + Theme.BG + ";");
        box.setPadding(new Insets(12, 16, 16, 16));

        allData = FXCollections.observableArrayList();
        filteredData = new FilteredList<>(allData, r -> true);
        table = buildTable();
        table.setItems(filteredData);
        VBox.setVgrow(table, Priority.ALWAYS);

        box.getChildren().add(table);
        return box;
    }

    private TableView<FeederRow> buildTable() {
        return new MultiFeederTableBuilder(app).build();
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void updateMeasurement(FeederMeasurement m) {
        if (m == null) return;

        int alarmCount = countActiveAlarmsFor(m.getFeederId());
        FeederRow row = rowMap.get(m.getFeederId());

        if (row == null) {
            refreshFeeders();
            row = rowMap.get(m.getFeederId());
            if (row == null) return;
        }

        row.update(m, alarmCount);
        // Si es feeder real y aún mostraba CONNECTING, promover a CONNECTED al recibir datos
        if ("CONNECTING".equals(row.getConnLed())) row.setConnLed("CONNECTED");
        lastUpdateLbl.setText("Última actualización: " + LocalDateTime.now().format(FMT));
        table.refresh();
    }

    public void refreshFeeders() {
        allData.clear();
        rowMap.clear();
        List<FeederConfig> cfgs = app.getFeederConfigs();
        int idx = 1;
        for (FeederConfig cfg : cfgs) {
            boolean sim = app.isSimulatedFeeder(cfg.getFeederId());
            FeederRow row = new FeederRow(idx++, cfg, sim);
            // Recuperar estado de conexión si ya se conoce
            if (!sim) {
                IEC61850Communicator.State cs = app.getCommunicatorState(cfg.getFeederId());
                if (cs != null) {
                    switch (cs) {
                        case CONNECTED:    row.setConnLed("CONNECTED");    break;
                        case CONNECTING:   row.setConnLed("CONNECTING");   break;
                        case ERROR:        row.setConnLed("ERROR");         break;
                        case DISCONNECTED: row.setConnLed("DISCONNECTED"); break;
                    }
                }
            }
            FeederMeasurement m = app.getLatestMeasurements().get(cfg.getFeederId());
            if (m != null) row.update(m, countActiveAlarmsFor(cfg.getFeederId()));
            allData.add(row);
            rowMap.put(cfg.getFeederId(), row);
        }
        applyFilter();
    }

    private int countActiveAlarmsFor(String feederId) {
        return (int) app.getAlarmEngine().getActiveAlarms().stream()
            .filter(a -> a.getFeederId().equals(feederId)).count();
    }

    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        filteredData.setPredicate(row ->
            q.isEmpty() ||
            row.getFeederName().toLowerCase().contains(q) ||
            row.getFeeder().getFeederId().toLowerCase().contains(q));
    }

    /** Actualiza el LED de conexión para un feeder. Llamado desde HarmonicMonitorApp. */
    public void updateConnectionState(String feederId, IEC61850Communicator.State state) {
        FeederRow row = rowMap.get(feederId);
        if (row == null) return;
        String led;
        switch (state) {
            case CONNECTED:    led = "CONNECTED";    break;
            case CONNECTING:   led = "CONNECTING";   break;
            case ERROR:        led = "ERROR";         break;
            case DISCONNECTED: led = "DISCONNECTED"; break;
            default:           led = "DISCONNECTED"; break;
        }
        row.setConnLed(led);
        if (state == IEC61850Communicator.State.DISCONNECTED || state == IEC61850Communicator.State.ERROR) {
            row.clear();
        }
        table.refresh();
    }

    private void exportCsv() {
        if (app.getFeederConfigs().isEmpty()) { app.setStatusMessage("Sin feeders configurados"); return; }
        StringBuilder sb = new StringBuilder("Exportado: ");
        for (FeederConfig cfg : app.getFeederConfigs()) {
            try { sb.append(app.getDataStorage().exportToCsv(cfg.getFeederId(), null, null)).append("  "); }
            catch (Exception ex) { sb.append("[Error: ").append(cfg.getFeederId()).append("]  "); }
        }
        app.setStatusMessage(sb.toString().trim());
    }

}
