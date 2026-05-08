package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.comm.IEC61850Communicator;
import com.harmonicmonitor.model.AlarmEvent;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.LinkedList;
import java.util.List;

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

    // KPI tiles references [container, valueLabel, unitLabel, deltaLabel]
    private Label kpiVoltageVal;
    private Label kpiCurrentVal;
    private Label kpiPowerVal;
    private Label kpiThdiVal;
    private Label kpiLoadVal;
    private Label kpiVoltageDelta;
    private Label kpiCurrentDelta;
    private Label kpiPowerDelta;
    private Label kpiThdiDelta;
    private Label kpiLoadConf;

    // Metrics grid labels
    private Label metricQ;
    private Label metricS;
    private Label metricFP;
    private Label metricFreq;
    private Label metricTHDv;
    private Label metricCV;
    private Label metricH5;
    private Label metricH7;
    private Label metricRes;
    private Label metricL1;
    private Label metricL2;
    private Label metricL3;

    // Alarm mini list
    private VBox alarmListBox;

    // THDi trend chart
    private XYChart.Series<Number, Number> thdiSeries;
    private final LinkedList<Double> thdiHistory = new LinkedList<>();

    // Harmonic bar chart
    private XYChart.Series<String, Number> harmonicSeries;
    private Label harmonicInfoLabel;
    private FeederMeasurement lastMeasurement;

    // Previous values for deltas
    private double prevVoltage = Double.NaN;
    private double prevCurrent = Double.NaN;
    private double prevPower   = Double.NaN;
    private double prevThdi    = Double.NaN;

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
        HBox row = new HBox(10);

        // Voltage tile (blue border)
        VBox voltTile = buildKpiTile("TENSIÓN", "#0078D4");
        kpiVoltageVal = findKpiValue(voltTile);
        kpiVoltageDelta = findKpiDelta(voltTile);

        // Current tile (cyan border)
        VBox currTile = buildKpiTile("CORRIENTE", "#0099BC");
        kpiCurrentVal = findKpiValue(currTile);
        kpiCurrentDelta = findKpiDelta(currTile);

        // Power tile (green border)
        VBox powTile = buildKpiTile("POTENCIA", "#107C10");
        kpiPowerVal = findKpiValue(powTile);
        kpiPowerDelta = findKpiDelta(powTile);

        // THDi tile (amber border)
        VBox thdiTile = buildKpiTile("THDi", "#CA5010");
        kpiThdiVal = findKpiValue(thdiTile);
        kpiThdiDelta = findKpiDelta(thdiTile);

        // Load type tile
        VBox loadTile = buildKpiTile("TIPO CARGA", "#808080");
        kpiLoadVal = findKpiValue(loadTile);
        kpiLoadConf = findKpiDelta(loadTile);

        for (VBox tile : new VBox[]{voltTile, currTile, powTile, thdiTile, loadTile}) {
            HBox.setHgrow(tile, Priority.ALWAYS);
            row.getChildren().add(tile);
        }

        return row;
    }

    private VBox buildKpiTile(String title, String borderColor) {
        VBox tile = new VBox(4);
        tile.setPadding(new Insets(12, 14, 12, 16));
        tile.setMinHeight(88);
        tile.setStyle(
            "-fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: " + borderColor + " #E0E3E8 #E0E3E8 " + borderColor + ";" +
            "-fx-border-width: 0 1 1 4;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 1);");

        Label hdr = new Label(title);
        hdr.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Label value = new Label("—");
        value.setId("kpiValue");
        value.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Label delta = new Label("");
        delta.setId("kpiDelta");
        delta.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        tile.getChildren().addAll(hdr, value, delta);
        return tile;
    }

    private Label findKpiValue(VBox tile) {
        for (Node n : tile.getChildren()) {
            if (n instanceof Label && "kpiValue".equals(n.getId())) return (Label) n;
        }
        return new Label();
    }

    private Label findKpiDelta(VBox tile) {
        for (Node n : tile.getChildren()) {
            if (n instanceof Label && "kpiDelta".equals(n.getId())) return (Label) n;
        }
        return new Label();
    }

    // ── Middle row ────────────────────────────────────────────────────────────

    private HBox buildMiddleRow() {
        HBox row = new HBox(10);
        VBox metricsCard = buildMetricsCard();
        VBox alarmCard   = buildAlarmCard();
        HBox.setHgrow(metricsCard, Priority.ALWAYS);
        row.getChildren().addAll(metricsCard, alarmCard);
        return row;
    }

    private VBox buildMetricsCard() {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: #D0D3D8; -fx-border-width: 1;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(12));

        Label title = new Label("MÉTRICAS DE ALIMENTADOR");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        // Initialize all metric labels
        metricQ    = metricLabel(); metricS    = metricLabel();
        metricFP   = metricLabel(); metricFreq = metricLabel();
        metricTHDv = metricLabel(); metricCV   = metricLabel();
        metricH5   = metricLabel(); metricH7   = metricLabel();
        metricRes  = metricLabel();
        metricL1   = metricLabel(); metricL2   = metricLabel(); metricL3   = metricLabel();

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(5);

        addMetricRow(grid, 0, "Q Reactiva:", metricQ, "kVAR", "P Aparente:", metricS, "kVA");
        addMetricRow(grid, 1, "Factor Pot.:", metricFP, "", "Frecuencia:", metricFreq, "Hz");
        addMetricRow(grid, 2, "THDv:", metricTHDv, "%", "CV Corriente:", metricCV, "%");
        addMetricRow(grid, 3, "H5/H1:", metricH5, "%", "H7/H1:", metricH7, "%");

        HBox resRow = new HBox(8);
        resRow.setAlignment(Pos.CENTER_LEFT);
        Label resLbl = new Label("Resonancia:");
        resLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        resRow.getChildren().addAll(resLbl, metricRes);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #CCCCCC;");

        Label phaseTit = new Label("POR FASE");
        phaseTit.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        GridPane phaseGrid = new GridPane();
        phaseGrid.setHgap(16); phaseGrid.setVgap(3);
        addPhaseHeaderRow(phaseGrid);
        metricL1.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        metricL2.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        metricL3.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        phaseGrid.add(new Label("L1:"), 0, 1); phaseGrid.add(metricL1, 1, 1); GridPane.setColumnSpan(metricL1, 4);
        phaseGrid.add(new Label("L2:"), 0, 2); phaseGrid.add(metricL2, 1, 2); GridPane.setColumnSpan(metricL2, 4);
        phaseGrid.add(new Label("L3:"), 0, 3); phaseGrid.add(metricL3, 1, 3); GridPane.setColumnSpan(metricL3, 4);
        for (int r = 1; r <= 3; r++) {
            Label phl = (Label) phaseGrid.getChildren().get((r - 1) * 2);
            phl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        }

        card.getChildren().addAll(title, grid, resRow, sep, phaseTit, phaseGrid);
        return card;
    }

    private Label metricLabel() {
        Label l = new Label("—");
        l.setStyle("-fx-text-fill: #0099BC; -fx-font-size: 12px; -fx-font-weight: bold;");
        return l;
    }

    private void addMetricRow(GridPane g, int row,
                               String lbl1, Label val1, String unit1,
                               String lbl2, Label val2, String unit2) {
        HBox c1 = buildMetricCell(lbl1, val1, unit1);
        HBox c2 = buildMetricCell(lbl2, val2, unit2);
        g.add(c1, 0, row);
        g.add(c2, 1, row);
    }

    private HBox buildMetricCell(String lbl, Label val, String unit) {
        Label l = new Label(lbl);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + "; -fx-min-width: 95px;");
        Label u = new Label(unit);
        u.setStyle("-fx-font-size: 11px; -fx-text-fill: #0078D4;");
        HBox cell = new HBox(4, l, val, u);
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    private void addPhaseHeaderRow(GridPane g) {
        String[] hdrs = {"Fase", "Valores"};
        for (int i = 0; i < hdrs.length; i++) {
            Label l = new Label(hdrs[i]);
            l.setStyle("-fx-font-size: 10px; -fx-text-fill: #0078D4;");
            g.add(l, i, 0);
        }
    }

    private VBox buildAlarmCard() {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: #D0D3D8; -fx-border-width: 1;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(12));
        card.setPrefWidth(280);
        card.setMinWidth(200);

        Label title = new Label("ALARMAS ACTIVAS");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        alarmListBox = new VBox(5);
        Label emptyLbl = new Label("✓  Sin alarmas activas");
        emptyLbl.setStyle("-fx-text-fill: #107C10; -fx-font-size: 11px;");
        alarmListBox.getChildren().add(emptyLbl);

        ScrollPane scroll = new ScrollPane(alarmListBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(160);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button viewAll = new Button("Ver todas las alarmas →");
        viewAll.setMaxWidth(Double.MAX_VALUE);
        viewAll.setStyle("-fx-background-color: #EBF3FC; -fx-text-fill: #0078D4; -fx-font-size: 11px;" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 10 5 10; -fx-cursor: hand;");
        viewAll.setOnAction(e -> app.selectPanel(4));

        card.getChildren().addAll(title, scroll, viewAll);
        return card;
    }

    // ── Bottom row ────────────────────────────────────────────────────────────

    private HBox buildBottomRow() {
        HBox row = new HBox(10);
        VBox trendCard = buildThdiTrendChart();
        VBox harmCard  = buildHarmonicChart();
        HBox.setHgrow(trendCard, Priority.ALWAYS);
        HBox.setHgrow(harmCard, Priority.ALWAYS);
        row.getChildren().addAll(trendCard, harmCard);
        return row;
    }

    private VBox buildThdiTrendChart() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: #D0D3D8; -fx-border-width: 1;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(12));
        card.setMinHeight(220);

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

    private VBox buildHarmonicChart() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: #D0D3D8; -fx-border-width: 1;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(12));
        card.setMinHeight(220);

        Label title = new Label("ESPECTRO ARMÓNICO  (H1–H13, % de fundamental)");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Orden");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("%");
        yAxis.setForceZeroInRange(true);

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setAnimated(false);
        barChart.setBarGap(2);
        barChart.setCategoryGap(4);
        VBox.setVgrow(barChart, Priority.ALWAYS);

        harmonicSeries = new XYChart.Series<>();
        harmonicSeries.setName("H%");
        for (int i = 1; i <= 13; i++) {
            XYChart.Data<String, Number> d = new XYChart.Data<>("H" + i, 0.0);
            final int order = i;
            d.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-cursor: hand;");
                    newNode.setOnMouseClicked(ev -> showHarmonicDetail(order));
                }
            });
            harmonicSeries.getData().add(d);
        }
        barChart.getData().add(harmonicSeries);

        harmonicInfoLabel = new Label("Haz clic en una barra para ver el detalle del armonico");
        harmonicInfoLabel.setStyle(
            "-fx-font-size: 11px; -fx-text-fill: " + Theme.ACCENT + ";" +
            "-fx-padding: 4 8 4 8; -fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-radius: 4; -fx-background-radius: 4;");
        harmonicInfoLabel.setWrapText(true);

        card.getChildren().addAll(title, barChart, harmonicInfoLabel);
        return card;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void updateMeasurement(FeederMeasurement m) {
        if (m == null) return;
        lastMeasurement = m;

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
        updateKpis(m);
        updateMetrics(m);
        updateAlarmList();
        updateThdiTrend(m.getThdCurrentAvg());
        updateHarmonicSpectrum(m);
    }

    private void updateKpis(FeederMeasurement m) {
        double voltKv = m.getVoltageAvg() / 1000.0;
        double curr   = m.getCurrentAvg();
        double pow    = m.getActivePower();
        double thdi   = m.getThdCurrentAvg();

        kpiVoltageVal.setText(String.format("%.2f kV", voltKv));
        setDelta(kpiVoltageDelta, voltKv, prevVoltage);
        prevVoltage = voltKv;

        kpiCurrentVal.setText(String.format("%.1f A", curr));
        setDelta(kpiCurrentDelta, curr, prevCurrent);
        prevCurrent = curr;

        if (pow >= 1000) kpiPowerVal.setText(String.format("%.2f MW", pow / 1000.0));
        else             kpiPowerVal.setText(String.format("%.0f kW", pow));
        setDelta(kpiPowerDelta, pow, prevPower);
        prevPower = pow;

        kpiThdiVal.setText(String.format("%.1f %%", thdi));
        if (thdi > 12)
            kpiThdiVal.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #C42B1C;");
        else if (thdi > 8)
            kpiThdiVal.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #CA5010;");
        else
            kpiThdiVal.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #107C10;");
        setDelta(kpiThdiDelta, thdi, prevThdi);
        prevThdi = thdi;

        LoadType lt = m.getDetectedLoadType();
        kpiLoadVal.setText(lt.getDisplayName());
        kpiLoadVal.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + lt.getColorHex() + ";");
        double conf = computeConfidence(m);
        String confTxt = String.format("%.0f%% confianza", conf);
        if (m.isSpectrumEstimated()) confTxt += "  ~ espectro estimado";
        kpiLoadConf.setText(confTxt);
        kpiLoadConf.setStyle("-fx-font-size: 11px; -fx-text-fill: " + lt.getColorHex() + ";");
    }

    private void setDelta(Label lbl, double current, double prev) {
        if (Double.isNaN(prev)) { lbl.setText(""); return; }
        if (Math.abs(prev) < 1e-9) { lbl.setText(""); return; }
        double pct = 100.0 * (current - prev) / Math.abs(prev);
        if (pct >= 0) {
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #107C10;");
            lbl.setText(String.format("▲ +%.1f%%", pct));
        } else {
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #C42B1C;");
            lbl.setText(String.format("▼ %.1f%%", pct));
        }
    }

    private void updateMetrics(FeederMeasurement m) {
        metricQ.setText(String.format("%.0f", m.getReactivePower()));
        metricS.setText(String.format("%.1f", m.getApparentPower()));
        metricFP.setText(String.format("%.3f", m.getPowerFactor()));
        metricFreq.setText(String.format("%.2f", m.getFrequency()));
        metricTHDv.setText(String.format("%.2f", m.getThdVoltageAvg()));
        metricCV.setText(String.format("%.2f", m.getCvCurrent() * 100));
        metricH5.setText(String.format("%.1f", m.getH5h1Ratio() * 100));
        metricH7.setText(String.format("%.1f", m.getH7h1Ratio() * 100));

        if (m.getResonanceOrder() > 0) {
            metricRes.setText(String.format("%.0f Hz (H%d)", m.getResonanceFrequency(), m.getResonanceOrder()));
            metricRes.setStyle("-fx-text-fill: #C42B1C; -fx-font-size: 12px; -fx-font-weight: bold;");
        } else {
            metricRes.setText("Sin resonancia");
            metricRes.setStyle("-fx-text-fill: #107C10; -fx-font-size: 12px; -fx-font-weight: bold;");
        }

        metricL1.setText(String.format("V=%.1f kV  I=%.1f A  THDi=%.1f%%  THDv=%.1f%%",
            m.getVoltageL1() / 1000.0, m.getCurrentL1(), m.getThdCurrentL1(), m.getThdVoltageL1()));
        metricL2.setText(String.format("V=%.1f kV  I=%.1f A  THDi=%.1f%%  THDv=%.1f%%",
            m.getVoltageL2() / 1000.0, m.getCurrentL2(), m.getThdCurrentL2(), m.getThdVoltageL2()));
        metricL3.setText(String.format("V=%.1f kV  I=%.1f A  THDi=%.1f%%  THDv=%.1f%%",
            m.getVoltageL3() / 1000.0, m.getCurrentL3(), m.getThdCurrentL3(), m.getThdVoltageL3()));
    }

    private void updateAlarmList() {
        List<AlarmEvent> active = app.getAlarmEngine().getActiveAlarms();
        alarmListBox.getChildren().clear();
        if (active.isEmpty()) {
            Label l = new Label("✓  Sin alarmas activas");
            l.setStyle("-fx-text-fill: #107C10; -fx-font-size: 11px;");
            alarmListBox.getChildren().add(l);
            return;
        }
        int max = Math.min(active.size(), 7);
        for (int i = 0; i < max; i++) {
            AlarmEvent ev = active.get(i);
            Label row = new Label(levelIcon(ev.getLevel()) + "  " + shorten(ev.getMessage(), 60));
            row.setStyle("-fx-text-fill: " + ev.getLevel().getColorHex() +
                "; -fx-font-size: 11px;");
            row.setWrapText(false);
            alarmListBox.getChildren().add(row);
        }
        if (active.size() > max) {
            Label more = new Label("  ... y " + (active.size() - max) + " alarmas más");
            more.setStyle("-fx-text-fill: #0078D4; -fx-font-size: 11px;");
            alarmListBox.getChildren().add(more);
        }
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

    private void showHarmonicDetail(int order) {
        if (lastMeasurement == null) return;
        double[] spec = lastMeasurement.getHarmonicCurrentL1();
        if (spec == null || spec.length <= order || spec[0] < 1e-6) return;

        double h1A   = spec[0];                 // fundamental en A
        double hnA   = spec[order - 1];         // valor absoluto en A (spec[0]=H1, spec[1]=H2...)
        // spec[order-1] porque spec[0]=H1, spec[1]=H2, spec[n-1]=Hn
        double pct   = (order == 1) ? 100.0 : (hnA / h1A * 100.0);
        double freqHz = lastMeasurement.getFrequency() > 0 ? lastMeasurement.getFrequency() : 50.0;
        double harmFreq = order * freqHz;
        String estimated = lastMeasurement.isSpectrumEstimated() ? "  [espectro estimado]" : "";

        String txt;
        if (order == 1) {
            txt = String.format("H1 (fundamental %.0f Hz):  %.2f A  |  100%%  (referencia)%s",
                freqHz, h1A, estimated);
        } else {
            txt = String.format("H%d  (%.0f Hz):  %.3f A  |  %.1f%% de fundamental  |  H1=%.2f A%s",
                order, harmFreq, hnA, pct, h1A, estimated);
        }
        harmonicInfoLabel.setText(txt);
    }

    private void updateHarmonicSpectrum(FeederMeasurement m) {
        double[] spec = m.getHarmonicCurrentL1();
        if (spec == null || spec.length < 2 || spec[0] < 1e-6) return;
        double h1 = spec[0];
        ObservableList<XYChart.Data<String, Number>> data = harmonicSeries.getData();
        for (int i = 0; i < Math.min(13, data.size()); i++) {
            double pct = (i == 0) ? 100.0 : (spec[i] / h1 * 100.0);
            data.get(i).setYValue(pct);
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

    private double computeConfidence(FeederMeasurement m) {
        LoadType lt = m.getDetectedLoadType();
        if (lt == LoadType.UNKNOWN) return 0;
        if (lt == LoadType.CRYPTO_MINING || lt == LoadType.DATA_CENTER)
            return Math.min(98, 55 + m.getH5h1Ratio() * 200 + m.getThdCurrentAvg() * 0.4);
        return Math.min(92, 45 + m.getThdCurrentAvg() * 1.5);
    }

    private String levelIcon(AlarmEvent.Level level) {
        switch (level) {
            case WARNING:   return "🟡";
            case PQ_RISK:   return "🔴";
            case CRITICAL:  return "⛔";
            case DETECTION: return "🟣";
            default:        return "●";
        }
    }

    private String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
