package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.comm.IEC61850Communicator;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.LinkedList;

/**
 * DashboardPanel — PowerBI-style overview with KPI tiles, metrics, and mini charts.
 */
public class DashboardPanel {

    private static final int MAX_POINTS = 120;

    private final HarmonicMonitorApp app;
    private final ScrollPane root;

    // Feeder selector
    private ComboBox<String> feederCombo;
    private String selectedFeederId;
    private Label connStateLbl;
    private Label lastDataLbl;

    // Sub-components
    private KpiRow        kpiRow;
    private MetricsCard   metricsCard;
    private AlarmMiniCard alarmCard;

    // THDi trend chart
    private XYChart.Series<Number, Number> thdiSeries;
    private final LinkedList<Double> thdiHistory = new LinkedList<>();

    // Harmonic bar chart (delegado a HarmonicSpectrumCard)
    private HarmonicSpectrumCard harmonicCard;

    public DashboardPanel(HarmonicMonitorApp app) {
        this.app = app;
        root = buildUI();
    }

    public Node getNode() { return root; }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private ScrollPane buildUI() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #D6D9DF;");

        content.getChildren().addAll(
            buildHeader(),
            buildKpiRow(),
            buildMiddleRow(),
            buildBottomRow()
        );

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #D6D9DF; -fx-background: #D6D9DF;");
        return sp;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("RESUMEN GENERAL");
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 13px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label feederLbl = new Label("Alimentador:");
        feederLbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");

        feederCombo = new ComboBox<>();
        feederCombo.setPrefWidth(240);
        feederCombo.setOnAction(e -> {
            String val = feederCombo.getValue();
            if (val != null) {
                selectedFeederId = extractFeederId(val);
                FeederMeasurement m = app.getLatestMeasurements().get(selectedFeederId);
                if (m != null) updateAllWidgets(m);
            }
        });
        refreshFeederCombo();

        connStateLbl = new Label("○ Sin datos");
        connStateLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #78909C;");

        lastDataLbl = new Label("");
        lastDataLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #78909C;");

        h.getChildren().addAll(title, spacer, connStateLbl, lastDataLbl, feederLbl, feederCombo);
        return h;
    }

    /** Selecciona un feeder específico en el combo del Dashboard. */
    public void selectFeeder(String feederId) {
        for (int i = 0; i < feederCombo.getItems().size(); i++) {
            if (feederCombo.getItems().get(i).contains("[" + feederId + "]")) {
                feederCombo.getSelectionModel().select(i);
                return;
            }
        }
        refreshFeederCombo();
        for (int i = 0; i < feederCombo.getItems().size(); i++) {
            if (feederCombo.getItems().get(i).contains("[" + feederId + "]")) {
                feederCombo.getSelectionModel().select(i);
                return;
            }
        }
    }

    private void refreshFeederCombo() {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (FeederConfig cfg : app.getFeederConfigs()) {
            items.add(cfg.getFeederName() + " [" + cfg.getFeederId() + "]");
        }
        feederCombo.setItems(items);
        if (!items.isEmpty() && feederCombo.getSelectionModel().isEmpty()) {
            feederCombo.getSelectionModel().select(0);
            selectedFeederId = app.getFeederConfigs().get(0).getFeederId();
        }
    }

    private String extractFeederId(String comboText) {
        int s = comboText.lastIndexOf('[');
        int e = comboText.lastIndexOf(']');
        if (s >= 0 && e > s) return comboText.substring(s + 1, e);
        return comboText;
    }

    // ── KPI Row ───────────────────────────────────────────────────────────────

    private HBox buildKpiRow() {
        kpiRow = new KpiRow();
        return kpiRow.getNode();
    }

    // ── Middle row ────────────────────────────────────────────────────────────

    private HBox buildMiddleRow() {
        metricsCard = new MetricsCard();
        alarmCard   = new AlarmMiniCard(app.getAlarmEngine(), () -> app.selectPanel(4));
        HBox row = new HBox(10);
        HBox.setHgrow(metricsCard.getNode(), Priority.ALWAYS);
        row.getChildren().addAll(metricsCard.getNode(), alarmCard.getNode());
        return row;
    }

    // ── Bottom row ────────────────────────────────────────────────────────────

    private HBox buildBottomRow() {
        HBox row = new HBox(10);
        VBox trendCard = buildThdiTrendChart();
        harmonicCard   = new HarmonicSpectrumCard();
        VBox harmNode  = harmonicCard.getNode();
        HBox.setHgrow(trendCard, Priority.ALWAYS);
        HBox.setHgrow(harmNode, Priority.ALWAYS);
        row.getChildren().addAll(trendCard, harmNode);
        return row;
    }

    private VBox buildThdiTrendChart() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: #D0D3D8; -fx-border-width: 1;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(12));
        card.setMinHeight(170);

        Label title = new Label("TENDENCIA THDi  (últimas mediciones)");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        NumberAxis xAxis = new NumberAxis();
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("%");

        AreaChart<Number, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        VBox.setVgrow(chart, Priority.ALWAYS);

        thdiSeries = new XYChart.Series<>();
        thdiSeries.setName("THDi %");
        chart.getData().add(thdiSeries);

        card.getChildren().addAll(title, chart);
        return card;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void updateMeasurement(FeederMeasurement m) {
        if (m == null) return;

        if (selectedFeederId == null && !app.getFeederConfigs().isEmpty()) {
            selectedFeederId = app.getFeederConfigs().get(0).getFeederId();
            refreshFeederCombo();
        }

        // Refresh combo if feeder count changed
        if (feederCombo.getItems().size() != app.getFeederConfigs().size()) {
            refreshFeederCombo();
        }

        if (!m.getFeederId().equals(selectedFeederId)) return;

        // Actualizar indicador de estado
        if (connStateLbl != null) {
            connStateLbl.setText("● DATOS ACTIVOS");
            connStateLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #00C853;");
        }
        if (lastDataLbl != null) {
            lastDataLbl.setText("  " + java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            lastDataLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #78909C;");
        }

        updateAllWidgets(m);
    }

    private void updateAllWidgets(FeederMeasurement m) {
        kpiRow.update(m);
        metricsCard.update(m);
        alarmCard.updateAlarms();
        updateThdiTrend(m.getThdCurrentAvg());
        harmonicCard.update(m);
    }

    private void updateThdiTrend(double thdi) {
        thdiHistory.addLast(thdi);
        if (thdiHistory.size() > MAX_POINTS) thdiHistory.removeFirst();
        thdiSeries.getData().clear();
        int idx = 0;
        for (double v : thdiHistory) {
            thdiSeries.getData().add(new XYChart.Data<>(idx++, v));
        }
    }

    /** Llamado desde HarmonicMonitorApp cuando cambia el estado de conexion del comunicador. */
    public void updateConnectionState(String feederId, IEC61850Communicator.State state) {
        if (!feederId.equals(selectedFeederId)) return;
        if (connStateLbl == null) return;
        switch (state) {
            case CONNECTED:
                connStateLbl.setText("● CONECTADO");
                connStateLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #00C853;");
                break;
            case CONNECTING:
                connStateLbl.setText("◌ CONECTANDO...");
                connStateLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #FFB300;");
                break;
            case ERROR:
                connStateLbl.setText("● ERROR DE CONEXION");
                connStateLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #D50000;");
                if (lastDataLbl != null)
                    lastDataLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #D50000;");
                break;
            case DISCONNECTED:
                connStateLbl.setText("○ DESCONECTADO");
                connStateLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #78909C;");
                break;
        }
    }

}
