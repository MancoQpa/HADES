package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * TrendChartsPanel — 2x2 grid of SCADA-style area charts for THDi, THDv, Voltage, Current.
 */
public class TrendChartsPanel {

    private final HarmonicMonitorApp app;
    private final BorderPane root;

    private int windowSeconds = 60;  // default: 1 minuto

    private ComboBox<String> feederCombo;
    private String selectedFeederId;

    // Chart series
    private XYChart.Series<Number, Number> thdiSeries;
    private XYChart.Series<Number, Number> thdvSeries;
    private XYChart.Series<Number, Number> voltSeries;
    private XYChart.Series<Number, Number> currSeries;

    // Data buffers per chart
    private final LinkedList<Double> thdiData = new LinkedList<>();
    private final LinkedList<Double> thdvData = new LinkedList<>();
    private final LinkedList<Double> voltData = new LinkedList<>();
    private final LinkedList<Double> currData = new LinkedList<>();
    private int pointIndex = 0;

    public TrendChartsPanel(HarmonicMonitorApp app) {
        this.app = app;
        root = buildUI();
    }

    public Node getNode() { return root; }

    private int computeMaxPoints() {
        int pollMs = 5000; // default 5s
        if (selectedFeederId != null) {
            for (FeederConfig cfg : app.getFeederConfigs()) {
                if (cfg.getFeederId().equals(selectedFeederId)) {
                    pollMs = cfg.getPollIntervalMs();
                    break;
                }
            }
        }
        int pts = Math.max(20, windowSeconds * 1000 / pollMs);
        return Math.min(pts, 1800);
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setTop(buildHeader());
        pane.setCenter(buildChartsGrid());
        return pane;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(12, 16, 12, 16));
        h.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("📈 TENDENCIAS HISTÓRICAS");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label feederLbl = new Label("Alimentador:");
        feederLbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");

        feederCombo = new ComboBox<>();
        feederCombo.setPrefWidth(240);
        feederCombo.setOnAction(e -> {
            String val = feederCombo.getValue();
            if (val != null) {
                String newId = extractFeederId(val);
                if (!newId.equals(selectedFeederId)) {
                    selectedFeederId = newId;
                    clearBuffers();
                }
            }
        });
        refreshFeederSelector();

        Button btnClear = new Button("Limpiar");
        btnClear.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 10 5 10; -fx-cursor: hand; -fx-font-size: 11px;");
        btnClear.setOnAction(e -> clearBuffers());

        Label winLbl = new Label("Ventana:");
        winLbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");

        ComboBox<String> windowCombo = new ComboBox<>();
        windowCombo.getItems().addAll("1 min", "5 min", "15 min");
        windowCombo.setValue("1 min");
        windowCombo.setPrefWidth(80);
        windowCombo.setOnAction(e -> {
            String v = windowCombo.getValue();
            if ("1 min".equals(v))  windowSeconds = 60;
            else if ("5 min".equals(v))  windowSeconds = 300;
            else if ("15 min".equals(v)) windowSeconds = 900;
            clearBuffers();
        });

        h.getChildren().addAll(title, spacer, feederLbl, feederCombo, btnClear, winLbl, windowCombo);
        return h;
    }

    public void refreshFeederSelector() {
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

    private void clearBuffers() {
        thdiData.clear(); thdvData.clear();
        voltData.clear(); currData.clear();
        pointIndex = 0;
        if (thdiSeries != null) thdiSeries.getData().clear();
        if (thdvSeries != null) thdvSeries.getData().clear();
        if (voltSeries != null) voltSeries.getData().clear();
        if (currSeries != null) currSeries.getData().clear();
    }

    // ── Charts grid ───────────────────────────────────────────────────────────

    private GridPane buildChartsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12, 16, 16, 16));
        grid.setStyle("-fx-background-color: " + Theme.BG + ";");

        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(50);
        cc.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc, new ColumnConstraints());
        grid.getColumnConstraints().get(1).setPercentWidth(50);
        grid.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);

        RowConstraints rc = new RowConstraints();
        rc.setPercentHeight(50);
        rc.setVgrow(Priority.ALWAYS);
        grid.getRowConstraints().addAll(rc, new RowConstraints());
        grid.getRowConstraints().get(1).setPercentHeight(50);
        grid.getRowConstraints().get(1).setVgrow(Priority.ALWAYS);

        // THDi chart
        VBox thdiCard = buildChartCard("THDi (%)", "THDi", "#CA5010");
        thdiSeries = extractSeries(thdiCard);
        grid.add(thdiCard, 0, 0);

        // THDv chart
        VBox thdvCard = buildChartCard("THDv (%)", "THDv", "#C42B1C");
        thdvSeries = extractSeries(thdvCard);
        grid.add(thdvCard, 1, 0);

        // Current chart — izquierda (col 0)
        VBox currCard = buildChartCard("Corriente (A)", "Corriente", "#107C10");
        currSeries = extractSeries(currCard);
        grid.add(currCard, 0, 1);

        // Voltage chart — derecha (col 1)
        VBox voltCard = buildChartCard("Tensión (kV)", "Tensión", "#0078D4");
        voltSeries = extractSeries(voltCard);
        grid.add(voltCard, 1, 1);

        return grid;
    }

    private VBox buildChartCard(String yLabel, String seriesName, String lineColor) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1;" +
            "-fx-border-radius: 6; -fx-background-radius: 6;");
        card.setPadding(new Insets(8));

        Label title = new Label(seriesName.toUpperCase());
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        NumberAxis xAxis = new NumberAxis();
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setForceZeroInRange(false);
        xAxis.setLabel("Tiempo →");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        yAxis.setForceZeroInRange(false);

        AreaChart<Number, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        VBox.setVgrow(chart, Priority.ALWAYS);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(seriesName);
        chart.getData().add(series);

        // Store line color on chart for use after layout if needed
        chart.setUserData(lineColor);

        card.getChildren().addAll(title, chart);
        card.setUserData(series); // stash series for later extraction
        return card;
    }

    @SuppressWarnings("unchecked")
    private XYChart.Series<Number, Number> extractSeries(VBox card) {
        return (XYChart.Series<Number, Number>) card.getUserData();
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void updateMeasurement(FeederMeasurement m) {
        if (m == null) return;

        if (selectedFeederId == null && !app.getFeederConfigs().isEmpty()) {
            selectedFeederId = app.getFeederConfigs().get(0).getFeederId();
        }
        if (feederCombo.getItems().size() != app.getFeederConfigs().size()) {
            refreshFeederSelector();
        }
        if (!m.getFeederId().equals(selectedFeederId)) return;

        addPoint(thdiData, thdiSeries, m.getThdCurrentAvg());
        addPoint(thdvData, thdvSeries, m.getThdVoltageAvg());
        addPoint(voltData, voltSeries, m.getVoltageAvg() / 1000.0);
        addPoint(currData, currSeries, m.getCurrentAvg());
        pointIndex++;
    }

    private void addPoint(LinkedList<Double> buffer,
                          XYChart.Series<Number, Number> series, double value) {
        if (series == null) return;
        int maxPts = computeMaxPoints();
        buffer.addLast(value);
        while (buffer.size() > maxPts) buffer.removeFirst();

        series.getData().clear();
        int idx = 0;
        for (double v : buffer) {
            series.getData().add(new XYChart.Data<>(idx++, v));
        }
    }
}
