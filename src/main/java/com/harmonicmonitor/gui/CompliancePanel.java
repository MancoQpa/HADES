package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CompliancePanel — Normative compliance checker: IEC 61000-3-6, IEEE 519-2022, EN 50160.
 * Supports multi-feeder selection via dropdown and shows feeder column in the table.
 */
public class CompliancePanel {

    private static final String ALL_FEEDERS = "— Todos los feeders —";

    private final HarmonicMonitorApp app;
    private final BorderPane root;

    private TableView<CompRow> table;
    private ObservableList<CompRow> tableData;

    private ComboBox<String> feederSelector;
    private Label feederStatusLbl;

    /** feederId → last measurement */
    private final Map<String, FeederMeasurement> measurements = new LinkedHashMap<>();
    /** feederId → config */
    private final Map<String, FeederConfig>      configs      = new LinkedHashMap<>();

    public CompliancePanel(HarmonicMonitorApp app) {
        this.app = app;
        root = buildUI();
    }

    public Node getNode() { return root; }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setTop(buildHeader());
        pane.setCenter(buildContent());
        return pane;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(12, 16, 12, 16));
        h.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER
                + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("📏 CUMPLIMIENTO NORMATIVO  —  IEC / IEEE / EN");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Feeder selector
        Label selectorLbl = new Label("Feeder:");
        selectorLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        feederSelector = new ComboBox<>();
        feederSelector.setPrefWidth(240);
        feederSelector.setStyle("-fx-font-size: 11px;");
        feederSelector.getItems().add(ALL_FEEDERS);
        feederSelector.getSelectionModel().selectFirst();
        feederSelector.setOnAction(e -> refreshFromSelector());

        feederStatusLbl = new Label("Sin datos");
        feederStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #0078D4;");

        h.getChildren().addAll(title, spacer, selectorLbl, feederSelector, feederStatusLbl);
        return h;
    }

    private VBox buildContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        HBox legend = buildLegend();

        VBox tableCard = buildTableCard();
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox notesCard = buildNotesCard();

        content.getChildren().addAll(legend, tableCard, notesCard);
        return content;
    }

    private HBox buildLegend() {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 0, 4, 0));
        row.getChildren().addAll(
            legendItem("✓ CUMPLE",    "#107C10"),
            legendItem("⚠ LÍMITE",   "#CA5010"),
            legendItem("✗ INCUMPLE", "#C42B1C"),
            legendItem("— Sin datos", "#1A4A80")
        );
        return row;
    }

    private HBox legendItem(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return new HBox(l);
    }

    private VBox buildTableCard() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER
                + "; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");
        card.setPadding(new Insets(12));

        Label title = new Label("EVALUACIÓN POR NORMA Y PARÁMETRO");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(420);
        VBox.setVgrow(table, Priority.ALWAYS);

        // ── Columna Feeder (identificatoria) ──────────────────────────────────
        TableColumn<CompRow, String> colFeeder = col("Feeder", "feeder", 120);
        colFeeder.setStyle("-fx-font-weight: bold;");
        colFeeder.setCellFactory(c -> new TableCell<CompRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("-fx-font-weight: bold; -fx-text-fill: #0078D4;");
            }
        });

        TableColumn<CompRow, String> colStd    = col("Norma",          "standard",  150);
        TableColumn<CompRow, String> colParam  = col("Parámetro",      "parameter", 150);
        TableColumn<CompRow, String> colMeas   = col("Medido",         "measured",   90);
        TableColumn<CompRow, String> colLimit  = col("Límite",         "limit",      80);
        TableColumn<CompRow, String> colStatus = col("Estado",         "status",    100);
        TableColumn<CompRow, String> colNotes  = col("Observaciones",  "notes",     240);

        // Color column Estado
        colStatus.setCellFactory(c -> new TableCell<CompRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if      (item.startsWith("✓")) setStyle("-fx-text-fill: #107C10; -fx-font-weight: bold;");
                else if (item.startsWith("⚠")) setStyle("-fx-text-fill: #CA5010; -fx-font-weight: bold;");
                else if (item.startsWith("✗")) setStyle("-fx-text-fill: #C42B1C; -fx-font-weight: bold;");
                else                           setStyle("-fx-text-fill: #0078D4;");
            }
        });

        table.getColumns().addAll(colFeeder, colStd, colParam, colMeas, colLimit, colStatus, colNotes);

        tableData = FXCollections.observableArrayList();
        table.setItems(tableData);

        // Row background by status
        table.setRowFactory(tv -> new TableRow<CompRow>() {
            @Override
            protected void updateItem(CompRow item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setStyle(""); return; }
                String s = item.getStatus();
                if      (s.startsWith("✗")) setStyle("-fx-background-color: #C42B1C15;");
                else if (s.startsWith("⚠")) setStyle("-fx-background-color: #CA501015;");
                else if (s.startsWith("✓")) setStyle("-fx-background-color: #107C1008;");
                else                        setStyle("");
            }
        });

        buildDefaultRows();
        card.getChildren().addAll(title, table);
        return card;
    }

    private VBox buildNotesCard() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER
                + "; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");
        card.setPadding(new Insets(10, 12, 10, 12));

        Label title = new Label("REFERENCIAS NORMATIVAS");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Label refs = new Label(
            "• IEC 61000-3-6:2008 — Límites de emisión de armónicas en redes MT/AT\n" +
            "• IEEE 519-2022 — Recomendaciones y requerimientos para control de armónicas en sistemas eléctricos\n" +
            "• EN 50160:2010 — Características de tensión en redes de distribución pública\n" +
            "• IEC 61000-2-12 — Niveles de compatibilidad para perturbaciones de baja frecuencia en redes MT"
        );
        refs.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + "; -fx-wrap-text: true;");
        refs.setWrapText(true);

        card.getChildren().addAll(title, refs);
        return card;
    }

    private TableColumn<CompRow, String> col(String title, String prop, int minW) {
        TableColumn<CompRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setMinWidth(minW);
        return c;
    }

    // ── Default placeholder rows ──────────────────────────────────────────────

    private void buildDefaultRows() {
        tableData.clear();
        tableData.addAll(ComplianceRowBuilder.buildDefaultRows());
    }

    // ── Update (called from polling thread via Platform.runLater in app) ───────

    // Called from Platform.runLater in HarmonicMonitorApp — already on FX thread.
    public void updateMeasurement(FeederMeasurement m) {
        if (m == null) return;

        measurements.put(m.getFeederId(), m);

        FeederConfig cfg = getConfig(m.getFeederId());
        if (cfg != null) configs.put(m.getFeederId(), cfg);

        syncDropdownOptions();

        // Refresh table if this feeder is currently selected (or "All feeders" mode)
        String sel = feederSelector.getValue();
        if (sel == null || sel.equals(ALL_FEEDERS)
                || sel.equals(feederLabel(cfg, m.getFeederId()))) {
            refreshFromSelector();
        }
    }

    /** Refreshes table content based on the current dropdown selection. */
    private void refreshFromSelector() {
        String sel = feederSelector.getValue();
        if (sel == null || sel.equals(ALL_FEEDERS)) {
            showAllFeeders();
        } else {
            // Find feeder ID matching the selected label
            for (Map.Entry<String, FeederConfig> e : configs.entrySet()) {
                String id = e.getKey();
                FeederConfig cfg = e.getValue();
                if (sel.equals(feederLabel(cfg, id))) {
                    FeederMeasurement m = measurements.get(id);
                    if (m != null) showSingleFeeder(m, cfg);
                    return;
                }
            }
            // Feeder with no config yet
            for (Map.Entry<String, FeederMeasurement> e : measurements.entrySet()) {
                if (sel.equals(e.getKey())) {
                    showSingleFeeder(e.getValue(), null);
                    return;
                }
            }
        }
    }

    /** Shows all feeders' rows together (one block per feeder). */
    private void showAllFeeders() {
        tableData.clear();
        if (measurements.isEmpty()) { buildDefaultRows(); return; }

        for (String feederId : measurements.keySet()) {
            FeederMeasurement m = measurements.get(feederId);
            FeederConfig cfg    = configs.get(feederId);
            tableData.addAll(buildRows(m, cfg));
        }

        feederStatusLbl.setText(measurements.size() + " feeder(s)");
        table.refresh();
    }

    /** Shows rows for a single feeder. */
    private void showSingleFeeder(FeederMeasurement m, FeederConfig cfg) {
        tableData.clear();
        tableData.addAll(buildRows(m, cfg));
        String name = cfg != null ? cfg.getFeederName() : m.getFeederId();
        feederStatusLbl.setText(name);
        table.refresh();
    }

    /** Builds the 15 compliance rows for one feeder measurement. */
    private List<CompRow> buildRows(FeederMeasurement m, FeederConfig cfg) {
        return ComplianceRowBuilder.buildRows(m, cfg);
    }

    // ── Dropdown helpers ──────────────────────────────────────────────────────

    /** Keeps dropdown in sync with connected feeders. */
    private void syncDropdownOptions() {
        String current = feederSelector.getValue();
        List<String> items = new ArrayList<>();
        items.add(ALL_FEEDERS);
        for (Map.Entry<String, FeederMeasurement> e : measurements.entrySet()) {
            FeederConfig cfg = configs.get(e.getKey());
            items.add(feederLabel(cfg, e.getKey()));
        }
        feederSelector.getItems().setAll(items);
        // Restore selection if still valid
        if (current != null && items.contains(current)) {
            feederSelector.setValue(current);
        } else {
            feederSelector.getSelectionModel().selectFirst();
        }
    }

    /** Display string for the dropdown: "BPA-5 — Alimentador BPA-5" */
    private String feederLabel(FeederConfig cfg, String feederId) {
        if (cfg == null) return feederId;
        return feederId.equals(cfg.getFeederName())
               ? feederId
               : feederId + "  —  " + cfg.getFeederName();
    }

    private FeederConfig getConfig(String feederId) {
        for (FeederConfig cfg : app.getFeederConfigs()) {
            if (cfg.getFeederId().equals(feederId)) return cfg;
        }
        return null;
    }

}
