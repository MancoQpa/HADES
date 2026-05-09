package com.harmonicmonitor.gui;

import com.harmonicmonitor.alarm.AlarmEngine;
import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.model.AlarmEvent;
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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * AlarmsPanel — SCADA alarm center with counters, filtering, active alarms, and history.
 * Implements AlarmEngine.AlarmListener.
 */
public class AlarmsPanel implements AlarmEngine.AlarmListener {

    private final HarmonicMonitorApp app;
    private final AlarmEngine alarmEngine;
    private final BorderPane root;

    // Counter labels
    private Label countWarning;
    private Label countPqRisk;
    private Label countCritical;
    private Label countDetection;

    // Active alarms table
    private TableView<AlarmRow> activeTable;
    private ObservableList<AlarmRow> activeData;
    private FilteredList<AlarmRow> filteredActive;

    // History table
    private TableView<AlarmRow> historyTable;
    private ObservableList<AlarmRow> historyData;
    private FilteredList<AlarmRow> filteredHistory;

    // Filters
    private ComboBox<String> filterLevel;
    private ComboBox<String> filterFeeder;
    private TextField filterSearch;

    public AlarmsPanel(HarmonicMonitorApp app, AlarmEngine alarmEngine) {
        this.app = app;
        this.alarmEngine = alarmEngine;
        root = buildUI();
    }

    public Node getNode() { return root; }

    // ── AlarmListener ─────────────────────────────────────────────────────────

    @Override
    public void onAlarm(AlarmEvent event) {
        Platform.runLater(() -> {
            addToHistory(event);
            updateCounters();
        });
    }

    @Override
    public void onActiveAlarmsChanged(List<AlarmEvent> activeAlarms) {
        Platform.runLater(() -> {
            refreshActiveTable(activeAlarms);
            updateCounters();
        });
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");

        pane.setTop(buildHeader());

        VBox content = new VBox(10);
        content.setPadding(new Insets(12, 16, 16, 16));
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        content.getChildren().addAll(
            buildCounters(),
            buildFilterBar(),
            buildActiveSection(),
            buildHistorySection()
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + Theme.BG + "; -fx-background: " + Theme.BG + ";");
        pane.setCenter(scroll);

        return pane;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(12, 16, 12, 16));
        h.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("🔔 GESTIÓN DE ALARMAS");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAckSel = new Button("Reconocer Sel.");
        btnAckSel.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 12 5 12; -fx-cursor: hand;");
        btnAckSel.setOnAction(e -> acknowledgeSelected());

        Button btnAckAll = new Button("Reconocer Todo");
        btnAckAll.setStyle("-fx-background-color: #CA5010; -fx-text-fill: #F0F0F0; -fx-font-weight: bold;" +
            "-fx-border-color: #CA5010; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 12 5 12; -fx-cursor: hand;");
        btnAckAll.setOnAction(e -> {
            alarmEngine.acknowledgeAll();
            app.setStatusMessage("Todas las alarmas reconocidas");
        });

        h.getChildren().addAll(title, spacer, btnAckSel, btnAckAll);
        return h;
    }

    // ── Counter tiles ─────────────────────────────────────────────────────────

    private HBox buildCounters() {
        HBox row = new HBox(10);

        countWarning   = buildCountTile("WARNING",   "#CA5010", "#CA501018");
        countPqRisk    = buildCountTile("PQ_RISK",   "#C42B1C", "#C42B1C18");
        countCritical  = buildCountTile("CRITICAL",  "#FF2D55", "#FF2D5518");
        countDetection = buildCountTile("DETECTION", "#881798", "#88179818");

        for (Node n : new Node[]{countWarning.getParent() != null ? countWarning.getParent() : countWarning}) {
            // handled below
        }

        VBox tileWarn = buildCounterCard("⚠ WARNING",   countWarning,   "#CA5010");
        VBox tilePQ   = buildCounterCard("⬆ PQ_RISK",   countPqRisk,    "#C42B1C");
        VBox tileCrit = buildCounterCard("⛔ CRITICAL",  countCritical,  "#FF2D55");
        VBox tileDet  = buildCounterCard("🔍 DETECTION", countDetection, "#881798");

        for (VBox t : new VBox[]{tileWarn, tilePQ, tileCrit, tileDet}) {
            HBox.setHgrow(t, Priority.ALWAYS);
            row.getChildren().add(t);
        }

        return row;
    }

    private Label buildCountTile(String level, String color, String bg) {
        Label l = new Label("0");
        l.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return l;
    }

    private VBox buildCounterCard(String title, Label countLabel, String borderColor) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setMinHeight(80);
        card.setStyle("-fx-background-color: " + Theme.BG + ";" +
            "-fx-border-color: " + borderColor + " transparent transparent transparent;" +
            "-fx-border-width: 0 0 0 3;" +
            "-fx-border-radius: 0 6 6 0; -fx-background-radius: 6;");

        Label tLbl = new Label(title);
        tLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        card.getChildren().addAll(tLbl, countLabel);
        return card;
    }

    // ── Filter bar ────────────────────────────────────────────────────────────

    private HBox buildFilterBar() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        filterLevel = new ComboBox<>(FXCollections.observableArrayList(
            "Todos", "WARNING", "PQ_RISK", "CRITICAL", "DETECTION"));
        filterLevel.setValue("Todos");
        filterLevel.setPrefWidth(130);
        filterLevel.setOnAction(e -> applyFilters());

        filterFeeder = new ComboBox<>();
        filterFeeder.setPrefWidth(200);
        filterFeeder.setValue("Todos los feeders");
        filterFeeder.setOnAction(e -> applyFilters());
        refreshFeederFilter();

        filterSearch = new TextField();
        filterSearch.setPromptText("🔍 Buscar en mensaje...");
        filterSearch.setPrefWidth(220);
        filterSearch.textProperty().addListener((obs, o, n) -> applyFilters());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClear = new Button("Limpiar Filtros");
        btnClear.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 10 5 10; -fx-cursor: hand; -fx-font-size: 11px;");
        btnClear.setOnAction(e -> {
            filterLevel.setValue("Todos");
            filterFeeder.setValue("Todos los feeders");
            filterSearch.clear();
        });

        row.getChildren().addAll(filterLevel, filterFeeder, filterSearch, spacer, btnClear);
        return row;
    }

    public void refreshFeederFilter() {
        String current = filterFeeder.getValue();
        ObservableList<String> items = FXCollections.observableArrayList("Todos los feeders");
        for (FeederConfig cfg : app.getFeederConfigs()) {
            items.add(cfg.getFeederId() + " — " + cfg.getFeederName());
        }
        filterFeeder.setItems(items);
        // Keep previous selection if still valid
        if (current != null && items.contains(current)) filterFeeder.setValue(current);
        else filterFeeder.setValue("Todos los feeders");
    }

    // ── Active alarms table ───────────────────────────────────────────────────

    private VBox buildActiveSection() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1;" +
            "-fx-border-radius: 6; -fx-background-radius: 6;");
        card.setPadding(new Insets(12));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("ALARMAS ACTIVAS");
        title.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #C42B1C;");
        header.getChildren().add(title);

        activeData = FXCollections.observableArrayList();
        filteredActive = new FilteredList<>(activeData, row -> true);
        activeTable = buildAlarmTable(filteredActive);
        activeTable.setPrefHeight(220);

        card.getChildren().addAll(header, activeTable);
        return card;
    }

    // ── History table ─────────────────────────────────────────────────────────

    private VBox buildHistorySection() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1;" +
            "-fx-border-radius: 6; -fx-background-radius: 6;");
        card.setPadding(new Insets(12));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("HISTORIAL DE ALARMAS");
        title.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnClear = new Button("Limpiar Historial");
        btnClear.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 4 10 4 10; -fx-cursor: hand; -fx-font-size: 11px;");
        btnClear.setOnAction(e -> historyData.clear());
        header.getChildren().addAll(title, spacer, btnClear);

        historyData = FXCollections.observableArrayList();
        filteredHistory = new FilteredList<>(historyData, row -> true);
        historyTable = buildAlarmTable(filteredHistory);
        historyTable.setPrefHeight(260);

        card.getChildren().addAll(header, historyTable);
        return card;
    }

    private TableView<AlarmRow> buildAlarmTable(ObservableList<AlarmRow> data) {
        TableView<AlarmRow> tv = new TableView<>(data);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AlarmRow, String> colTime = col("Timestamp", "timestamp", 150);
        TableColumn<AlarmRow, String> colLevel = col("Nivel", "level", 90);
        TableColumn<AlarmRow, String> colFeeder = col("Feeder", "feederId", 110);
        TableColumn<AlarmRow, String> colParam = col("Parámetro", "parameter", 100);
        TableColumn<AlarmRow, String> colMsg = col("Mensaje", "message", 340);
        TableColumn<AlarmRow, String> colVal = col("Valor", "measuredValue", 80);
        TableColumn<AlarmRow, String> colThr = col("Umbral", "threshold", 80);
        TableColumn<AlarmRow, String> colAck = col("Ack.", "ack", 60);

        tv.getColumns().addAll(colTime, colLevel, colFeeder, colParam, colMsg, colVal, colThr, colAck);

        // Color rows by alarm level; also handle selected state to keep text readable
        tv.setRowFactory(t -> new TableRow<AlarmRow>() {
            private void refresh() {
                AlarmRow item = getItem();
                if (item == null || isEmpty()) {
                    setStyle("-fx-background-color: transparent; -fx-text-fill: " + Theme.TEXT + ";");
                    return;
                }
                if (isSelected()) {
                    setStyle("-fx-background-color: #0078D4; -fx-text-fill: #FFFFFF;");
                    return;
                }
                String bg;
                switch (item.getLevel()) {
                    case "WARNING":   bg = "#CA501022"; break;
                    case "PQ_RISK":   bg = "#C42B1C22"; break;
                    case "CRITICAL":  bg = "#FF2D5530"; break;
                    case "DETECTION": bg = "#88179822"; break;
                    default:          bg = "transparent"; break;
                }
                setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + Theme.TEXT + ";");
            }
            {
                selectedProperty().addListener((obs, o, n) -> refresh());
            }
            @Override
            protected void updateItem(AlarmRow item, boolean empty) {
                super.updateItem(item, empty);
                refresh();
            }
        });

        return tv;
    }

    private TableColumn<AlarmRow, String> col(String title, String prop, int width) {
        TableColumn<AlarmRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setMinWidth(width);
        return c;
    }

    // ── Update helpers ────────────────────────────────────────────────────────

    private void refreshActiveTable(List<AlarmEvent> activeAlarms) {
        activeData.clear();
        for (AlarmEvent ev : activeAlarms) {
            activeData.add(new AlarmRow(ev));
        }
        applyFilters();
        updateCounters();
    }

    private void addToHistory(AlarmEvent event) {
        historyData.add(0, new AlarmRow(event));
        if (historyData.size() > 500) historyData.remove(historyData.size() - 1);
    }

    private void updateCounters() {
        List<AlarmEvent> active = alarmEngine.getActiveAlarms();
        int warn = 0, pq = 0, crit = 0, det = 0;
        for (AlarmEvent ev : active) {
            switch (ev.getLevel()) {
                case WARNING:   warn++; break;
                case PQ_RISK:   pq++;   break;
                case CRITICAL:  crit++; break;
                case DETECTION: det++;  break;
            }
        }
        countWarning.setText(String.valueOf(warn));
        countPqRisk.setText(String.valueOf(pq));
        countCritical.setText(String.valueOf(crit));
        countDetection.setText(String.valueOf(det));
    }

    private void acknowledgeSelected() {
        AlarmRow sel = activeTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        List<AlarmEvent> active = alarmEngine.getActiveAlarms();
        for (AlarmEvent ev : active) {
            if (ev.getFormattedTimestamp().equals(sel.getTimestamp()) &&
                ev.getFeederId().equals(sel.getFeederId())) {
                alarmEngine.acknowledgeAlarm(ev);
                break;
            }
        }
    }

    private void applyFilters() {
        String level  = filterLevel.getValue();
        String feeder = filterFeeder.getValue();
        String search = filterSearch.getText() == null ? "" : filterSearch.getText().toLowerCase();

        filteredActive.setPredicate(row -> matchesFilter(row, level, feeder, search));
        filteredHistory.setPredicate(row -> matchesFilter(row, level, feeder, search));
    }

    private boolean matchesFilter(AlarmRow row, String level, String feeder, String search) {
        if (!"Todos".equals(level) && !row.getLevel().equals(level)) return false;
        if (!"Todos los feeders".equals(feeder) && feeder != null
                && !feeder.startsWith(row.getFeederId() + " —")) return false;
        if (!search.isEmpty() && !row.getMessage().toLowerCase().contains(search)) return false;
        return true;
    }

    public void updateMeasurement(FeederMeasurement m) {
        // Refresh feeder filter list if new feeders added
        if (filterFeeder.getItems().size() - 1 != app.getFeederConfigs().size()) {
            Platform.runLater(this::refreshFeederFilter);
        }
    }

}
