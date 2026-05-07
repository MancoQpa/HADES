package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * HarmonicsPanel — Harmonic spectrum analysis with bar chart, summary cards, and data table.
 */
public class HarmonicsPanel {

    private final HarmonicMonitorApp app;
    private final BorderPane root;

    // Controls
    private ComboBox<String> feederCombo;
    private ToggleGroup phaseGroup;
    private RadioButton rbL1, rbL2, rbL3, rbAvg;

    private String selectedFeederId;
    private FeederMeasurement lastMeasurement;

    // Harmonic bar chart
    private BarChart<String, Number> barChart;
    private XYChart.Series<String, Number> currentSeries;

    // Summary cards (H2,H3,H5,H7,H9,H11,H13,H15)
    private static final int[] SUMMARY_ORDERS = {2, 3, 5, 7, 9, 11, 13, 15};
    private final Label[] summaryCurrentPct  = new Label[8];
    private final Label[] summaryCurrentAmp  = new Label[8];
    private final Label[] summaryVoltagePct  = new Label[8];

    // Table
    private TableView<HarmonicRow> table;
    private ObservableList<HarmonicRow> tableData;

    // Indicator shown when spectrum is estimated (IED doesn't provide harmonic array)
    private Label estimatedLabel;

    public HarmonicsPanel(HarmonicMonitorApp app) {
        this.app = app;
        root = buildUI();
    }

    public Node getNode() { return root; }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");

        // Top: header with controls
        pane.setTop(buildHeader());

        // Center: chart + cards + table
        VBox center = new VBox(10);
        center.setPadding(new Insets(12, 16, 16, 16));
        center.setStyle("-fx-background-color: " + Theme.BG + ";");

        center.getChildren().addAll(
            buildBarChart(),
            buildSummaryCards(),
            buildTable()
        );

        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + Theme.BG + "; -fx-background: " + Theme.BG + ";");
        pane.setCenter(scroll);

        return pane;
    }

    private HBox buildHeader() {
        HBox h = new HBox(14);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(12, 16, 12, 16));
        h.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("〜 ANÁLISIS DE ARMÓNICOS");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Phase selector
        Label phaseLbl = new Label("Fase:");
        phaseLbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");

        phaseGroup = new ToggleGroup();
        rbL1 = phaseRadio("L1");  rbL2 = phaseRadio("L2");
        rbL3 = phaseRadio("L3");  rbAvg = phaseRadio("Prom.");
        rbL1.setSelected(true);
        rbL1.setToggleGroup(phaseGroup); rbL2.setToggleGroup(phaseGroup);
        rbL3.setToggleGroup(phaseGroup); rbAvg.setToggleGroup(phaseGroup);
        phaseGroup.selectedToggleProperty().addListener((obs, o, n) -> refreshDisplay());

        HBox phaseBox = new HBox(6, phaseLbl, rbL1, rbL2, rbL3, rbAvg);
        phaseBox.setAlignment(Pos.CENTER_LEFT);

        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);

        // Feeder selector
        Label feederLbl = new Label("Alimentador:");
        feederLbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");

        feederCombo = new ComboBox<>();
        feederCombo.setPrefWidth(240);
        feederCombo.setOnAction(e -> {
            String val = feederCombo.getValue();
            if (val != null) {
                selectedFeederId = extractFeederId(val);
                FeederMeasurement m = app.getLatestMeasurements().get(selectedFeederId);
                if (m != null) { lastMeasurement = m; refreshDisplay(); }
            }
        });
        refreshFeederCombo();

        estimatedLabel = new Label("~ espectro estimado (IED no provee armónicas)");
        estimatedLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #CA5010; -fx-font-style: italic;");
        estimatedLabel.setVisible(false);

        h.getChildren().addAll(title, spacer, estimatedLabel, phaseBox, sep, feederLbl, feederCombo);
        return h;
    }

    private RadioButton phaseRadio(String text) {
        RadioButton rb = new RadioButton(text);
        rb.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        return rb;
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

    // ── Bar chart ─────────────────────────────────────────────────────────────

    private VBox buildBarChart() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + ";" +
            "-fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");
        card.setPadding(new Insets(12));
        card.setMinHeight(280);

        Label title = new Label("ESPECTRO ARMÓNICO DE CORRIENTE  (%  de fundamental)");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Orden Armónico");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("% de H1");
        yAxis.setForceZeroInRange(true);

        barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setAnimated(false);
        barChart.setBarGap(1);
        barChart.setCategoryGap(3);
        barChart.setPrefHeight(240);
        VBox.setVgrow(barChart, Priority.ALWAYS);

        currentSeries = new XYChart.Series<>();
        currentSeries.setName("I %");
        for (int i = 1; i <= 15; i++) {
            currentSeries.getData().add(new XYChart.Data<>("H" + i, 0.0));
        }
        barChart.getData().add(currentSeries);

        card.getChildren().addAll(title, barChart);
        return card;
    }

    // ── Summary cards ─────────────────────────────────────────────────────────

    private HBox buildSummaryCards() {
        HBox row = new HBox(8);

        for (int idx = 0; idx < SUMMARY_ORDERS.length; idx++) {
            int order = SUMMARY_ORDERS[idx];
            VBox card = buildHarmonicCard(order, idx);
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }

        return row;
    }

    private VBox buildHarmonicCard(int order, int idx) {
        VBox card = new VBox(3);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(8, 6, 8, 6));
        card.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1;" +
            "-fx-border-radius: 6; -fx-background-radius: 6;");

        Label orderLbl = new Label("H" + order);
        orderLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0078D4;");

        String freqStr = String.format("%.0f Hz", order * 50.0);
        Label freqLbl = new Label(freqStr);
        freqLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #0078D4;");

        Label iPct = new Label("—");
        iPct.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        summaryCurrentPct[idx] = iPct;

        Label iAmp = new Label("— A");
        iAmp.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + ";");
        summaryCurrentAmp[idx] = iAmp;

        Label vPct = new Label("— V%");
        vPct.setStyle("-fx-font-size: 10px; -fx-text-fill: #0078D4;");
        summaryVoltagePct[idx] = vPct;

        card.getChildren().addAll(orderLbl, freqLbl, iPct, iAmp, vPct);
        return card;
    }

    // ── Data table ────────────────────────────────────────────────────────────

    private VBox buildTable() {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1;" +
            "-fx-border-radius: 6; -fx-background-radius: 6;");
        card.setPadding(new Insets(12));

        Label title = new Label("TABLA ARMÓNICA  H1–H15");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        table = new TableView<>();
        table.setPrefHeight(320);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<HarmonicRow, Integer> colOrder = new TableColumn<>("Orden");
        colOrder.setCellValueFactory(new PropertyValueFactory<>("order"));
        colOrder.setMinWidth(50);

        TableColumn<HarmonicRow, String> colFreq = new TableColumn<>("Freq (Hz)");
        colFreq.setCellValueFactory(new PropertyValueFactory<>("frequency"));
        colFreq.setMinWidth(80);

        TableColumn<HarmonicRow, String> colIAmp = new TableColumn<>("I (A)");
        colIAmp.setCellValueFactory(new PropertyValueFactory<>("currentAmp"));
        colIAmp.setMinWidth(80);

        TableColumn<HarmonicRow, String> colIPct = new TableColumn<>("I %");
        colIPct.setCellValueFactory(new PropertyValueFactory<>("currentPct"));
        colIPct.setMinWidth(80);

        TableColumn<HarmonicRow, String> colVVolt = new TableColumn<>("V (V)");
        colVVolt.setCellValueFactory(new PropertyValueFactory<>("voltageVolt"));
        colVVolt.setMinWidth(80);

        TableColumn<HarmonicRow, String> colVPct = new TableColumn<>("V %");
        colVPct.setCellValueFactory(new PropertyValueFactory<>("voltagePct"));
        colVPct.setMinWidth(80);

        TableColumn<HarmonicRow, String> colNorm = new TableColumn<>("Estado");
        colNorm.setCellValueFactory(new PropertyValueFactory<>("status"));
        colNorm.setMinWidth(90);

        table.getColumns().addAll(colOrder, colFreq, colIAmp, colIPct, colVVolt, colVPct, colNorm);

        tableData = FXCollections.observableArrayList();
        for (int i = 1; i <= 15; i++) {
            tableData.add(new HarmonicRow(i));
        }
        table.setItems(tableData);

        // Color rows by THD magnitude
        table.setRowFactory(tv -> new TableRow<HarmonicRow>() {
            @Override
            protected void updateItem(HarmonicRow item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else {
                    String rawPct = item.getCurrentPct().replace("%", "").replace("—", "0").trim();
                    try {
                        double pct = Double.parseDouble(rawPct);
                        if (pct > 15)       setStyle("-fx-background-color: #C42B1C18;");
                        else if (pct > 8)   setStyle("-fx-background-color: #CA501018;");
                        else                setStyle("");
                    } catch (NumberFormatException ex) { setStyle(""); }
                }
            }
        });

        card.getChildren().addAll(title, table);
        return card;
    }

    // ── Refresh display ───────────────────────────────────────────────────────

    private void refreshDisplay() {
        if (lastMeasurement == null) return;
        FeederMeasurement m = lastMeasurement;

        // Show warning when spectrum is estimated (IED doesn't provide harmonic array)
        if (estimatedLabel != null) {
            estimatedLabel.setVisible(m.isSpectrumEstimated());
        }

        double[] iSpec = getSelectedCurrentSpectrum(m);
        double[] vSpec = getSelectedVoltageSpectrum(m);
        if (iSpec == null) return;

        double h1 = iSpec[0];
        double v1 = (vSpec != null && vSpec.length > 0) ? vSpec[0] : 1.0;

        // Update bar chart
        ObservableList<XYChart.Data<String, Number>> data = currentSeries.getData();
        for (int i = 0; i < Math.min(15, data.size()); i++) {
            double pct = (h1 > 1e-6) ? (iSpec[i] / h1 * 100.0) : 0.0;
            if (i == 0) pct = 100.0; // H1 is fundamental = 100%
            data.get(i).setYValue(pct);
        }

        // Color bar chart nodes
        for (int i = 0; i < data.size(); i++) {
            XYChart.Data<String, Number> d = data.get(i);
            if (d.getNode() != null) {
                double pct = d.getYValue().doubleValue();
                if (pct > 15)      d.getNode().setStyle("-fx-bar-fill: #C42B1C;");
                else if (pct > 8)  d.getNode().setStyle("-fx-bar-fill: #CA5010;");
                else               d.getNode().setStyle("-fx-bar-fill: #0078D4;");
            }
        }

        // Update summary cards
        for (int idx = 0; idx < SUMMARY_ORDERS.length; idx++) {
            int order = SUMMARY_ORDERS[idx];
            if (order <= iSpec.length) {
                double iAmp = iSpec[order - 1];
                double iPct = (h1 > 1e-6) ? (iAmp / h1 * 100.0) : 0.0;
                double vAmp = (vSpec != null && order <= vSpec.length) ? vSpec[order - 1] : 0.0;
                double vPct = (v1 > 1e-6 && vSpec != null) ? (vAmp / v1 * 100.0) : 0.0;

                summaryCurrentPct[idx].setText(String.format("%.1f%%", iPct));
                summaryCurrentAmp[idx].setText(String.format("%.2f A", iAmp));
                summaryVoltagePct[idx].setText(String.format("%.1f V%%", vPct));

                String color;
                if (iPct > 15)     color = "#C42B1C";
                else if (iPct > 8) color = "#CA5010";
                else               color = "#107C10";
                summaryCurrentPct[idx].setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
            }
        }

        // Update table
        for (int i = 0; i < Math.min(15, tableData.size()); i++) {
            int order = i + 1;
            double iAmp = (i < iSpec.length) ? iSpec[i] : 0.0;
            double iPct = (h1 > 1e-6 && order > 1) ? (iAmp / h1 * 100.0) : (order == 1 ? 100.0 : 0.0);
            double vAmp = (vSpec != null && i < vSpec.length) ? vSpec[i] : 0.0;
            double vPct = (v1 > 1e-6 && vSpec != null && order > 1) ? (vAmp / v1 * 100.0) : (order == 1 ? 100.0 : 0.0);

            String status;
            if (order == 1)       status = "—";
            else if (iPct > 15)   status = "⛔ CRÍTICO";
            else if (iPct > 8)    status = "⚠ ELEVADO";
            else                  status = "✓ OK";

            tableData.get(i).update(
                String.format("%.1f", order * 50.0),
                String.format("%.3f", iAmp),
                String.format("%.2f", iPct),
                String.format("%.3f", vAmp),
                String.format("%.2f", vPct),
                status
            );
        }
        table.refresh();
    }

    private double[] getSelectedCurrentSpectrum(FeederMeasurement m) {
        RadioButton sel = (RadioButton) phaseGroup.getSelectedToggle();
        if (sel == rbL1 || sel == null) return m.getHarmonicCurrentL1();
        if (sel == rbL2) return m.getHarmonicCurrentL2();
        if (sel == rbL3) return m.getHarmonicCurrentL3();
        // Average
        double[] l1 = m.getHarmonicCurrentL1();
        double[] l2 = m.getHarmonicCurrentL2();
        double[] l3 = m.getHarmonicCurrentL3();
        if (l1 == null) return null;
        double[] avg = new double[l1.length];
        for (int i = 0; i < l1.length; i++) {
            avg[i] = (l1[i] + (l2 != null ? l2[i] : 0) + (l3 != null ? l3[i] : 0)) / 3.0;
        }
        return avg;
    }

    private double[] getSelectedVoltageSpectrum(FeederMeasurement m) {
        RadioButton sel = (RadioButton) phaseGroup.getSelectedToggle();
        if (sel == rbL1 || sel == null) return m.getHarmonicVoltageL1();
        if (sel == rbL2) return m.getHarmonicVoltageL2();
        if (sel == rbL3) return m.getHarmonicVoltageL3();
        double[] l1 = m.getHarmonicVoltageL1();
        double[] l2 = m.getHarmonicVoltageL2();
        double[] l3 = m.getHarmonicVoltageL3();
        if (l1 == null) return null;
        double[] avg = new double[l1.length];
        for (int i = 0; i < l1.length; i++) {
            avg[i] = (l1[i] + (l2 != null ? l2[i] : 0) + (l3 != null ? l3[i] : 0)) / 3.0;
        }
        return avg;
    }

    // ── Update from poller ────────────────────────────────────────────────────

    public void updateMeasurement(FeederMeasurement m) {
        if (m == null) return;

        if (selectedFeederId == null && !app.getFeederConfigs().isEmpty()) {
            selectedFeederId = app.getFeederConfigs().get(0).getFeederId();
            refreshFeederCombo();
        }
        if (feederCombo.getItems().size() != app.getFeederConfigs().size()) {
            refreshFeederCombo();
        }
        if (!m.getFeederId().equals(selectedFeederId)) return;

        lastMeasurement = m;
        refreshDisplay();
    }

    // ── HarmonicRow data class ─────────────────────────────────────────────────

    public static class HarmonicRow {
        private int    order;
        private String frequency;
        private String currentAmp;
        private String currentPct;
        private String voltageVolt;
        private String voltagePct;
        private String status;

        public HarmonicRow(int order) {
            this.order = order;
            this.frequency   = String.format("%.1f", order * 50.0);
            this.currentAmp  = "—";
            this.currentPct  = "—";
            this.voltageVolt = "—";
            this.voltagePct  = "—";
            this.status      = "—";
        }

        public void update(String freq, String iAmp, String iPct, String vVolt, String vPct, String stat) {
            this.frequency   = freq;
            this.currentAmp  = iAmp;
            this.currentPct  = iPct;
            this.voltageVolt = vVolt;
            this.voltagePct  = vPct;
            this.status      = stat;
        }

        public int    getOrder()       { return order; }
        public String getFrequency()   { return frequency; }
        public String getCurrentAmp()  { return currentAmp; }
        public String getCurrentPct()  { return currentPct; }
        public String getVoltageVolt() { return voltageVolt; }
        public String getVoltagePct()  { return voltagePct; }
        public String getStatus()      { return status; }
    }
}
