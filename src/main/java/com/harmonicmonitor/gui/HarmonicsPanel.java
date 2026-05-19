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

/**
 * HarmonicsPanel — Harmonic spectrum analysis with bar chart, summary cards, and data table.
 */
public class HarmonicsPanel {

    private final HarmonicMonitorApp app;
    private final BorderPane root;

    // Controls
    private ComboBox<String> feederCombo;
    // Package-private: accessed by HarmonicsDisplayUpdater for phase selection.
    ToggleGroup phaseGroup;
    RadioButton rbL1, rbL2, rbL3, rbAvg;

    private String selectedFeederId;
    private FeederMeasurement lastMeasurement;

    // Harmonic bar chart
    private BarChart<String, Number> barChart;
    // Package-private: updated by HarmonicsDisplayUpdater each polling cycle.
    XYChart.Series<String, Number> currentSeries;

    // Summary cards (H2,H3,H5,H7,H9,H11,H13,H15)
    // Package-private: read by HarmonicsDisplayUpdater.
    static final int[] SUMMARY_ORDERS = {2, 3, 5, 7, 9, 11, 13, 15};
    final Label[] summaryCurrentPct  = new Label[8];
    final Label[] summaryCurrentAmp  = new Label[8];
    final Label[] summaryVoltagePct  = new Label[8];
    final Label[] summaryFreqLbl     = new Label[8];

    // Table — package-private: updated by HarmonicsDisplayUpdater.
    TableView<HarmonicRow> table;
    ObservableList<HarmonicRow> tableData;

    // No-harmonics panel (shown when IED doesn't expose individual harmonic arrays)
    private VBox harmonicContent;
    private VBox noHarmonicsPane;
    private Label lblNhTHDi, lblNhTHDv, lblNhIrms, lblNhVrms, lblNhFP, lblNhFreq, lblNhKfactor;
    private Label noHarmonicsNotice;

    private final HarmonicsDisplayUpdater updater = new HarmonicsDisplayUpdater(this);

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

        // Center: harmonic content OR no-harmonics info panel
        VBox center = new VBox(10);
        center.setPadding(new Insets(12, 16, 16, 16));
        center.setStyle("-fx-background-color: " + Theme.BG + ";");

        harmonicContent = new VBox(10);
        harmonicContent.getChildren().addAll(buildBarChart(), buildSummaryCards(), buildTable());

        noHarmonicsPane = buildNoHarmonicsPane();
        noHarmonicsPane.setVisible(false);
        noHarmonicsPane.setManaged(false);

        center.getChildren().addAll(noHarmonicsPane, harmonicContent);

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

        h.getChildren().addAll(title, spacer, phaseBox, sep, feederLbl, feederCombo);
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

    // ── No-harmonics panel (replaces spectrum when IED doesn't expose HarA/B/C) ──

    private VBox buildNoHarmonicsPane() {
        VBox pane = new VBox(20);
        pane.setPadding(new Insets(32, 40, 32, 40));
        pane.setAlignment(Pos.TOP_CENTER);

        // Info message
        Label icon = new Label("ℹ");
        icon.setStyle("-fx-font-size: 36px; -fx-text-fill: #0078D4;");

        Label title = new Label("Este medidor no expone los valores de armónicos individuales");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        title.setWrapText(true);

        Label subtitle = new Label("El IED no publica el nodo MHAI.HarA / HarB / HarC en su modelo IEC 61850.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888; ");
        subtitle.setWrapText(true);

        VBox header = new VBox(6, icon, title, subtitle);
        header.setAlignment(Pos.CENTER);

        // Real measured values grid
        Label sectionLbl = new Label("VALORES MEDIDOS DISPONIBLES");
        sectionLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #888; -fx-padding: 0 0 4 0;");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(24);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        grid.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER +
            "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 16 24 16 24;");

        lblNhTHDi    = nhValueLabel("—");
        lblNhTHDv    = nhValueLabel("—");
        lblNhIrms    = nhValueLabel("—");
        lblNhVrms    = nhValueLabel("—");
        lblNhFP      = nhValueLabel("—");
        lblNhFreq    = nhValueLabel("—");
        lblNhKfactor = nhValueLabel("—");

        int row = 0;
        addNhRow(grid, row++, "THD Corriente",  lblNhTHDi,    "THD_I  (medido por el IED)");
        addNhRow(grid, row++, "THD Tensión",    lblNhTHDv,    "THD_V  (medido por el IED)");
        addNhRow(grid, row++, "Corriente RMS",  lblNhIrms,    "Promedio 3φ");
        addNhRow(grid, row++, "Tensión RMS",    lblNhVrms,    "Promedio 3φ");
        addNhRow(grid, row++, "Factor de Potencia", lblNhFP,  "Medido por el IED");
        addNhRow(grid, row++, "Frecuencia",     lblNhFreq,    "Hz");
        addNhRow(grid, row,   "K-factor",       lblNhKfactor, "Si el IED lo expone");

        // Recommendation
        Label rec = new Label(
            "Para obtener el espectro armónico completo, habilite el nodo MHAI.HarA/HarB/HarC " +
            "en la configuración del IED (consulte el manual del equipo).");
        rec.setWrapText(true);
        rec.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-font-style: italic;");
        rec.setMaxWidth(560);

        pane.getChildren().addAll(header, sectionLbl, grid, rec);
        return pane;
    }

    private Label nhValueLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + "; -fx-min-width: 100;");
        return l;
    }

    private void addNhRow(javafx.scene.layout.GridPane grid, int row, String name, Label valueLabel, String note) {
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-min-width: 160;");
        Label noteLbl = new Label(note);
        noteLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #888; -fx-min-width: 180;");
        grid.add(nameLbl,   0, row);
        grid.add(valueLabel, 1, row);
        grid.add(noteLbl,   2, row);
    }

    private void updateNoHarmonicsValues(FeederMeasurement m) {
        javafx.application.Platform.runLater(() -> {
            if (lblNhTHDi == null) return;
            lblNhTHDi.setText(String.format("%.1f %%", m.getThdCurrentAvg()));
            lblNhTHDv.setText(String.format("%.1f %%", m.getThdVoltageAvg()));
            lblNhIrms.setText(String.format("%.2f A",  m.getCurrentAvg()));
            lblNhVrms.setText(String.format("%.2f V",  m.getVoltageAvg()));
            double fp = Math.min(1.0, Math.max(-1.0, m.getPowerFactor()));
            lblNhFP.setText(String.format("%.3f", fp));
            lblNhFreq.setText(String.format("%.2f Hz", m.getFrequency()));
            double kf = m.getKFactorL1();
            lblNhKfactor.setText(kf > 0.01 ? String.format("%.2f", kf) : "—");
        });
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

        noHarmonicsNotice = new Label(
            "\u26A0  El IED no expone arm\u00F3nicos individuales \u2014 solo THD disponible");
        noHarmonicsNotice.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold;" +
            "-fx-text-fill: #7A5200;" +
            "-fx-background-color: #FFF3CD;" +
            "-fx-border-color: #FFCA28;" +
            "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 6 12 6 12;");
        noHarmonicsNotice.setVisible(false);
        noHarmonicsNotice.setManaged(false);

        card.getChildren().addAll(title, noHarmonicsNotice, barChart);
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

        Label freqLbl = new Label("— Hz");
        freqLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #0078D4;");
        summaryFreqLbl[idx] = freqLbl;

        Label iPct = new Label("—");
        iPct.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        summaryCurrentPct[idx] = iPct;

        Label iAmp = new Label("— A");
        iAmp.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + ";");
        summaryCurrentAmp[idx] = iAmp;

        Label vPct = new Label("—");
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

    // ── No-harmonics mode control (called from FeederLifecycleManager at connect) ──

    /** Muestra el aviso en el gráfico cuando el IED no expone armónicos individuales. */
    public void setDegradedMode(boolean noHarmonics) {
        javafx.application.Platform.runLater(() -> {
            if (noHarmonicsNotice == null) return;
            noHarmonicsNotice.setVisible(noHarmonics);
            noHarmonicsNotice.setManaged(noHarmonics);
        });
    }

    // ── Refresh display ───────────────────────────────────────────────────────

    private void refreshDisplay() {
        if (lastMeasurement == null) return;
        updater.refresh(lastMeasurement);
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

        // Update harmonic frequency labels using actual measured frequency
        double freq = m.getFrequency();
        if (freq > 10) {
            for (int i = 0; i < SUMMARY_ORDERS.length; i++) {
                if (summaryFreqLbl[i] != null)
                    summaryFreqLbl[i].setText(String.format("%.0f Hz", SUMMARY_ORDERS[i] * freq));
            }
        }

        refreshDisplay();
    }

}
