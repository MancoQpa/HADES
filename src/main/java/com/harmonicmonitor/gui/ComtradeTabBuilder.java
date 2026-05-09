package com.harmonicmonitor.gui;

import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Builds the 4 COMTRADE analysis tabs for {@link ComtradePanel}.
 *
 * After {@link #build()} returns the {@link TabPane}, all widget references
 * are available as package-private fields so that {@code ComtradePanel} can
 * copy them into its own fields.
 *
 * Event handlers delegate back to the parent panel via package-private methods:
 * {@code plotWaveforms()}, {@code drawPhasors()}, {@code analyzeAll()},
 * {@code updateAnalysisWindow()}, and {@code selectedIndices()}.
 *
 * Extracted from {@link ComtradePanel} (refactor F10-001).
 */
final class ComtradeTabBuilder {

    private final ComtradePanel panel;

    // ── Output fields (assigned during build, read back by ComtradePanel) ────

    LineChart<Number, Number>          waveformChart;
    BarChart<String, Number>           fftChart;
    TableView<ObservableList<String>>  harmonicsTable;
    Canvas                             phasorCanvas;
    Slider                             zoomSlider;
    Label                              lblZoom;
    Slider                             winStartSlider;
    Slider                             winEndSlider;
    Label                              lblCursor;
    Label                              lblWinInfo;
    Label                              lblPhasorValues;
    Label                              lblSeqResult;
    Label                              lblPowerResult;
    HBox                               dynamicColorBar;
    CheckBox                           cbNormWaveforms;
    CheckBox                           cbNormIndep;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    ComtradeTabBuilder(ComtradePanel panel) {
        this.panel = panel;
    }

    TabPane build() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: " + Theme.BG + ";");
        tabs.getTabs().addAll(
            buildWaveformTab(),
            buildFftTab(),
            buildPhasorTab(),
            buildSeqPowerTab()
        );
        return tabs;
    }

    // ── Tab 1: Formas de onda ─────────────────────────────────────────────────

    private Tab buildWaveformTab() {
        Tab tab = new Tab("\uD83D\uDCC8 Formas de Onda");

        VBox content = new VBox(6);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        // Zoom + cursor bar
        HBox ctrlBar = new HBox(10);
        ctrlBar.setAlignment(Pos.CENTER_LEFT);
        Label zlbl = new Label("Zoom:");
        zlbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");

        zoomSlider = new Slider(5, 100, 100);
        zoomSlider.setPrefWidth(140);
        lblZoom = new Label("100%");
        lblZoom.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px; -fx-min-width: 38px;");
        zoomSlider.valueProperty().addListener((obs, ov, nv) -> {
            lblZoom.setText((int) zoomSlider.getValue() + "%");
            panel.plotWaveforms();
        });

        Button btnFit = panel.mkSmBtn("Ajustar todo");
        btnFit.setOnAction(e -> { zoomSlider.setValue(100); panel.plotWaveforms(); });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        lblCursor = new Label("Cursor: \u2014");
        lblCursor.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");

        ctrlBar.getChildren().addAll(zlbl, zoomSlider, lblZoom, btnFit, sp, lblCursor);

        // ── Color pickers row (populated dynamically when record is loaded) ──
        dynamicColorBar = new HBox(6);
        dynamicColorBar.setAlignment(Pos.CENTER_LEFT);
        dynamicColorBar.setPadding(new Insets(2, 0, 2, 0));
        Label colorLbl = new Label("\uD83C\uDFA8 Colores:");
        colorLbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        dynamicColorBar.getChildren().add(colorLbl);

        // ── Normalizar V/I independiente en formas de onda ───────────────────
        cbNormWaveforms = new CheckBox("Normalizar V/I (p.u.)");
        cbNormWaveforms.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        cbNormWaveforms.setTooltip(new Tooltip(
            "Escala tensiones y corrientes a sus propios picos (p.u.)\n" +
            "para que ambas sean visibles aunque V >> I (ej. 13200V vs 140A).\n" +
            "El eje Y muestra amplitud normalizada."));
        cbNormWaveforms.setSelected(false);
        cbNormWaveforms.setOnAction(e -> panel.plotWaveforms());
        dynamicColorBar.getChildren().addAll(new Label("   "), cbNormWaveforms);

        // Waveform chart
        NumberAxis xW = new NumberAxis(); xW.setLabel("Tiempo (ms)");
        xW.setStyle("-fx-tick-label-fill: #000000;");
        NumberAxis yW = new NumberAxis(); yW.setLabel("Amplitud");
        yW.setStyle("-fx-tick-label-fill: #000000;");

        waveformChart = new LineChart<>(xW, yW);
        waveformChart.setCreateSymbols(false);
        waveformChart.setAnimated(false);
        waveformChart.setStyle("-fx-background-color: " + Theme.BG + ";");
        waveformChart.setLegendVisible(true);
        VBox.setVgrow(waveformChart, Priority.ALWAYS);

        // Cursor: convert scene coords via axis
        waveformChart.setOnMouseMoved(e -> {
            if (panel.currentRecord == null) return;
            try {
                NumberAxis xa = (NumberAxis) waveformChart.getXAxis();
                NumberAxis ya = (NumberAxis) waveformChart.getYAxis();
                double t = xa.getValueForDisplay(xa.sceneToLocal(e.getSceneX(), e.getSceneY()).getX()).doubleValue();
                double v = ya.getValueForDisplay(ya.sceneToLocal(e.getSceneX(), e.getSceneY()).getY()).doubleValue();
                lblCursor.setText(String.format("t = %.3f ms   val = %.4f", t, v));
            } catch (Exception ignored) {}
        });

        // ── Analysis window selection ─────────────────────────────────────────
        HBox winBar = new HBox(8);
        winBar.setAlignment(Pos.CENTER_LEFT);
        winBar.setPadding(new Insets(4, 0, 2, 0));
        winBar.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: #0078D4; " +
                        "-fx-border-width: 0 0 0 3; -fx-padding: 6 10 6 10;");

        Label winTitle = new Label("VENTANA DE AN\u00C1LISIS  \u2014  regi\u00F3n del registro usada para FFT / Fasores / Potencia:");
        winTitle.setStyle("-fx-text-fill: #0078D4; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label lbFrom = new Label("Inicio:");
        lbFrom.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        winStartSlider = new Slider(0, 100, 0);
        winStartSlider.setPrefWidth(160);
        winStartSlider.setShowTickLabels(false);

        Label lbTo = new Label("Fin:");
        lbTo.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        winEndSlider = new Slider(0, 100, 100);
        winEndSlider.setPrefWidth(160);
        winEndSlider.setShowTickLabels(false);

        lblWinInfo = new Label("Todo el registro");
        lblWinInfo.setStyle("-fx-text-fill: #CA5010; -fx-font-size: 11px; -fx-min-width: 240px;");

        Button btnWinReset = panel.mkSmBtn("Todo");
        btnWinReset.setOnAction(e -> { winStartSlider.setValue(0); winEndSlider.setValue(100); });

        Button btnWinApply = panel.mkBtn("Analizar ventana", "#0078D4");
        btnWinApply.setOnAction(e -> panel.analyzeAll());

        ChangeListener<Number> winCL = (obs, o, n) -> panel.updateAnalysisWindow();
        winStartSlider.valueProperty().addListener(winCL);
        winEndSlider.valueProperty().addListener(winCL);

        winBar.getChildren().addAll(lbFrom, winStartSlider, lbTo, winEndSlider,
                                    lblWinInfo, btnWinReset, btnWinApply);

        VBox winSection = new VBox(3, winTitle, winBar);
        winSection.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-radius: 4;");
        winSection.setPadding(new Insets(6));

        content.getChildren().addAll(ctrlBar, dynamicColorBar, waveformChart, winSection);
        tab.setContent(content);
        return tab;
    }

    // ── Tab 2: FFT + Tabla de Armonicos ──────────────────────────────────────

    private Tab buildFftTab() {
        Tab tab = new Tab("\uD83D\uDCCA Espectro FFT / Arm\u00F3nicos");

        VBox content = new VBox(6);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        Label fftDesc = new Label(
            "AN\u00C1LISIS ESPECTRAL (FFT \u2014 Transformada de Fourier)\n" +
            "Descompone la se\u00F1al en sus componentes de frecuencia. " +
            "H1 = fundamental (50 Hz nominal), H2 = 100 Hz, H3 = 150 Hz, etc.\n" +
            "THD = Distorsi\u00F3n Arm\u00F3nica Total = \u221A(H2\u00B2+H3\u00B2+\u2026+H50\u00B2) / H1 \u00D7 100%.\n" +
            "L\u00EDmites t\u00EDpicos (IEC 61000-2-2 / IEEE 519):  THDv \u2264 8%  |  H5 \u2264 6%  |  H7 \u2264 5%  |  H11 \u2264 3.5%  |  H13 \u2264 3%");
        fftDesc.setWrapText(true);
        fftDesc.setStyle("-fx-font-size: 10.5px; -fx-text-fill: " + Theme.TEXT + "; -fx-background-color: " + Theme.BG + ";" +
                         "-fx-border-color: #0078D4; -fx-border-width: 0 0 0 3; -fx-padding: 6 10 6 10;");

        CategoryAxis xF = new CategoryAxis(); xF.setLabel("Orden Arm\u00F3nico (Hn = n \u00D7 50 Hz)");
        xF.setStyle("-fx-tick-label-fill: #000000;");
        NumberAxis yF = new NumberAxis();  yF.setLabel("Magnitud relativa (% de H1 fundamental)");
        yF.setStyle("-fx-tick-label-fill: #000000;");

        fftChart = new BarChart<>(xF, yF);
        fftChart.setAnimated(false);
        fftChart.setPrefHeight(220);
        fftChart.setStyle("-fx-background-color: " + Theme.BG + ";");
        fftChart.setLegendVisible(true);

        Label tblTitle = panel.sectionLbl("TABLA DE ARM\u00D3NICOS  \u2014  H1 es el fundamental (100%).  Mag = valor RMS del arm\u00F3nico.  % = proporci\u00F3n respecto a H1.");

        harmonicsTable = new TableView<>();
        harmonicsTable.setStyle("-fx-background-color: " + Theme.BG + ";");
        harmonicsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(harmonicsTable, Priority.ALWAYS);

        content.getChildren().addAll(fftDesc, fftChart, tblTitle, harmonicsTable);
        tab.setContent(content);
        return tab;
    }

    // ── Tab 3: Diagrama Fasorial ──────────────────────────────────────────────

    private Tab buildPhasorTab() {
        Tab tab = new Tab("\uD83D\uDD04 Fasores");

        BorderPane content = new BorderPane();
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        AnchorPane canvasPane = new AnchorPane();
        canvasPane.setStyle("-fx-background-color: " + Theme.BG + ";");
        canvasPane.setMinSize(0, 0);
        phasorCanvas = new Canvas(10, 10);
        canvasPane.getChildren().add(phasorCanvas);
        canvasPane.widthProperty().addListener((obs, ov, nv) -> {
            phasorCanvas.setWidth(Math.max(10, nv.doubleValue() - 16));
            panel.drawPhasors();
        });
        canvasPane.heightProperty().addListener((obs, ov, nv) -> {
            phasorCanvas.setHeight(Math.max(10, nv.doubleValue() - 16));
            panel.drawPhasors();
        });

        VBox infoPanel = new VBox(8);
        infoPanel.setPadding(new Insets(12));
        infoPanel.setPrefWidth(230);
        infoPanel.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 0 1;");

        Label phasTitle = panel.sectionLbl("VECTORES FUNDAMENTALES (H1)");
        lblPhasorValues = new Label("Seleccione 2\u20136 canales para ver el diagrama fasorial.\n\nCada vector muestra la magnitud y fase del arm\u00F3nico fundamental.");
        lblPhasorValues.setWrapText(true);
        lblPhasorValues.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-family: monospace;");

        cbNormIndep = new CheckBox("Normalizar V/I independiente");
        cbNormIndep.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        cbNormIndep.setTooltip(new Tooltip(
            "Cuando est\u00E1 activo, las tensiones y corrientes se escalan\n" +
            "a su propio m\u00E1ximo, haci\u00E9ndose ambas visibles aunque\n" +
            "la proporci\u00F3n V/I sea muy grande (ej. 13200V vs 140A).\n\n" +
            "El c\u00EDrculo de referencia = 100% del m\u00E1ximo de cada grupo."));
        cbNormIndep.setSelected(true);
        cbNormIndep.setOnAction(e -> panel.drawPhasors());

        Label tip = new Label("Tip: seleccione Va, Vb, Vc (o Ia, Ib, Ic) para an\u00E1lisis trif\u00E1sico.");
        tip.setWrapText(true);
        tip.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + ";");

        infoPanel.getChildren().addAll(phasTitle, cbNormIndep, new Separator(), lblPhasorValues, new Separator(), tip);

        content.setCenter(canvasPane);
        content.setRight(infoPanel);
        tab.setContent(content);
        return tab;
    }

    // ── Tab 4: Secuencias Simetricas + Potencia ───────────────────────────────

    private Tab buildSeqPowerTab() {
        Tab tab = new Tab("\u26A1 Secuencias / Potencia");

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        // Sequence section
        Label seqHdr = panel.sectionLbl("COMPONENTES SIM\u00C9TRICAS \u2014 Transformada de Fortescue");
        Label seqInstr = new Label(
            "Seleccione exactamente 3 canales (fase A, B, C) de tensi\u00F3n o corriente para calcular.");
        seqInstr.setWrapText(true);
        seqInstr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        lblSeqResult = new Label("\u2014 Seleccione 3 canales para calcular \u2014");
        lblSeqResult.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-family: monospace; -fx-wrap-text: true;");
        lblSeqResult.setWrapText(true);

        // Capture refs at build time; panel fields are assigned from these afterwards.
        final Label seqResultRef = lblSeqResult;
        Button btnSeq = panel.mkBtn("Calcular secuencias", "#0078D4");
        btnSeq.setOnAction(e -> {
            List<Integer> sel = panel.selectedIndices();
            seqResultRef.setText(new SequencePowerAnalyzer(
                panel.currentRecord, panel.winStartSample, panel.winEndSample)
                .calculateSequences(sel));
        });

        // Power section
        Label powHdr  = panel.sectionLbl("AN\u00C1LISIS DE POTENCIA");
        Label powInstr = new Label(
            "Seleccione exactamente 2 canales: primero la tensi\u00F3n (V), luego la corriente (A).");
        powInstr.setWrapText(true);
        powInstr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        lblPowerResult = new Label("\u2014 Seleccione 2 canales (V + I) para calcular \u2014");
        lblPowerResult.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-family: monospace; -fx-wrap-text: true;");
        lblPowerResult.setWrapText(true);

        final Label powResultRef = lblPowerResult;
        Button btnPow = panel.mkBtn("Calcular potencia", "#CA5010");
        btnPow.setOnAction(e -> {
            List<Integer> sel = panel.selectedIndices();
            powResultRef.setText(new SequencePowerAnalyzer(
                panel.currentRecord, panel.winStartSample, panel.winEndSample)
                .calculatePower(sel));
        });

        content.getChildren().addAll(
            seqHdr, seqInstr, lblSeqResult, btnSeq,
            new Separator(),
            powHdr, powInstr, lblPowerResult, btnPow
        );

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: " + Theme.BG + "; -fx-background: " + Theme.BG + ";");
        tab.setContent(sp);
        return tab;
    }
}
