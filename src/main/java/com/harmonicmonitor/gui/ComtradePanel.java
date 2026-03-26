package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.comtrade.ComtradeReader;
import com.harmonicmonitor.comtrade.ComtradeReader.ComtradeRecord;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.Arrays;
import java.util.concurrent.Executors;

/**
 * ComtradePanel — Visor avanzado de archivos COMTRADE (IEEE C37.111 / IEC 60255-24).
 *
 * Funcionalidades:
 *   - Formas de onda multi-canal con zoom y cursor de medición
 *   - Espectro FFT + tabla de armónicos H1..H50 con % de cada canal
 *   - Diagrama fasorial (magnitude/fase de H1 para cada canal)
 *   - Componentes simétricas (Fortescue) para selección de 3 canales
 *   - Análisis de potencia (P, Q, S, FP, THDv, THDi) para 2 canales V+I
 *   - Exportar CSV completo
 */
public class ComtradePanel {

    private static final int MAX_DISPLAY_POINTS = 2000;
    private static final String[] CHANNEL_COLORS = {
        "#0078D4", "#CA5010", "#4CAF50", "#C42B1C",
        "#9C27B0", "#00BCD4", "#FF9800", "#8BC34A"
    };

    private final HarmonicMonitorApp app;
    private final BorderPane root;
    private ComtradeRecord currentRecord = null;

    // Header
    private TextField tfFilePath;

    // Sidebar
    private Label lblInfo;
    private ListView<String> channelList;

    // Waveform tab
    private LineChart<Number, Number> waveformChart;
    private Slider  zoomSlider;
    private Label   lblZoom;
    private Label   lblCursor;
    private Node    chartPlotBg; // cached for cursor

    // FFT tab
    private BarChart<String, Number>           fftChart;
    private TableView<ObservableList<String>>  harmonicsTable;

    // Phasors tab
    private Canvas   phasorCanvas;
    private Label    lblPhasorValues;
    private CheckBox cbNormIndep;     // normalizar V e I con escalas independientes

    // Per-channel colors (keyed by channel index) – populated when record is loaded
    private final Map<Integer, String> channelColors = new HashMap<>();
    private HBox dynamicColorBar;      // populated dynamically in populateChannels()
    private CheckBox cbNormWaveforms;  // normalizar V/I independiente en formas de onda

    // Sequence/Power tab
    private Label lblSeqResult;
    private Label lblPowerResult;

    // Analysis window (sample indices into currentRecord; -1 = use all)
    private int    winStartSample = 0;
    private int    winEndSample   = -1;
    private Slider winStartSlider;
    private Slider winEndSlider;
    private Label  lblWinInfo;

    // Status
    private Label lblStatus;

    public ComtradePanel(HarmonicMonitorApp app) {
        this.app  = app;
        this.root = buildUI();
    }

    public Node getNode() { return root; }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setTop(buildHeader());

        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color: " + Theme.BG + ";");
        split.setDividerPositions(0.22);
        split.getItems().addAll(buildSidebar(), buildTabs());
        pane.setCenter(split);
        pane.setBottom(buildStatusBar());
        return pane;
    }

    private HBox buildHeader() {
        HBox h = new HBox(10);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(10, 16, 10, 16));
        h.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("📁 COMTRADE VIEWER  —  ION 7400 / IEC 60255-24");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        tfFilePath = new TextField();
        tfFilePath.setPromptText("Seleccione un archivo .cfg ...");
        tfFilePath.setEditable(false);
        tfFilePath.setPrefWidth(360);
        tfFilePath.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 3; -fx-background-radius: 3;");

        Button btnOpen = mkBtn("📂 Abrir .cfg", "#0078D4");
        btnOpen.setOnAction(e -> openCfgFile());

        Button btnAnalyze = mkBtn("🔍 Analizar", "#4CAF50");
        btnAnalyze.setOnAction(e -> analyzeAll());

        Button btnCapture = mkBtn("📸 Capturar ahora", "#E67E22");
        btnCapture.setTooltip(new Tooltip("Dispara registro COMTRADE con los datos actuales del IED y lo abre automáticamente"));
        btnCapture.setOnAction(e -> captureNow());

        Button btnCsv = mkBtn("💾 CSV", "#6C757D");
        btnCsv.setOnAction(e -> exportCsv());

        h.getChildren().addAll(title, spacer, btnCapture, new Separator(), tfFilePath, btnOpen, btnAnalyze, btnCsv);
        return h;
    }

    private VBox buildSidebar() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setPrefWidth(215);
        box.setStyle("-fx-background-color: " + Theme.BG + ";");

        Label lblInfoTitle = sectionLbl("INFORMACIÓN DEL REGISTRO");
        lblInfo = new Label("Sin archivo cargado");
        lblInfo.setWrapText(true);
        lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        Label lblCh = sectionLbl("CANALES ANALÓGICOS");
        Label hint = new Label("Ctrl+clic para multi-selección");
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + "; -fx-wrap-text: true;");

        channelList = new ListView<>();
        channelList.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + ";");
        channelList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        VBox.setVgrow(channelList, Priority.ALWAYS);
        channelList.getSelectionModel().selectedItemProperty()
            .addListener((obs, o, n) -> { if (n != null) onSelectionChanged(); });

        Button btnAll = mkSmBtn("Sel. todos");
        btnAll.setMaxWidth(Double.MAX_VALUE);
        btnAll.setOnAction(e -> channelList.getSelectionModel().selectAll());

        Button btnNone = mkSmBtn("Deseleccionar");
        btnNone.setMaxWidth(Double.MAX_VALUE);
        btnNone.setOnAction(e -> channelList.getSelectionModel().clearSelection());

        HBox selBtns = new HBox(4, btnAll, btnNone);

        box.getChildren().addAll(lblInfoTitle, lblInfo, new Separator(),
                                 lblCh, hint, channelList, selBtns);
        return box;
    }

    private TabPane buildTabs() {
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
        Tab tab = new Tab("📈 Formas de Onda");

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
            plotWaveforms();
        });

        Button btnFit = mkSmBtn("Ajustar todo");
        btnFit.setOnAction(e -> { zoomSlider.setValue(100); plotWaveforms(); });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        lblCursor = new Label("Cursor: —");
        lblCursor.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");

        ctrlBar.getChildren().addAll(zlbl, zoomSlider, lblZoom, btnFit, sp, lblCursor);

        // ── Color pickers row (populated dynamically when record is loaded) ──────
        dynamicColorBar = new HBox(6);
        dynamicColorBar.setAlignment(Pos.CENTER_LEFT);
        dynamicColorBar.setPadding(new Insets(2, 0, 2, 0));
        Label colorLbl = new Label("🎨 Colores:");
        colorLbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        dynamicColorBar.getChildren().add(colorLbl);
        HBox colorBar = dynamicColorBar; // alias used in layout below

        // ── Normalizar V/I independiente en formas de onda ───────────────────
        cbNormWaveforms = new CheckBox("Normalizar V/I (p.u.)");
        cbNormWaveforms.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        cbNormWaveforms.setTooltip(new Tooltip(
            "Escala tensiones y corrientes a sus propios picos (p.u.)\n" +
            "para que ambas sean visibles aunque V >> I (ej. 13200V vs 140A).\n" +
            "El eje Y muestra amplitud normalizada."));
        cbNormWaveforms.setSelected(false);
        cbNormWaveforms.setOnAction(e -> plotWaveforms());
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
            if (currentRecord == null) return;
            try {
                NumberAxis xa = (NumberAxis) waveformChart.getXAxis();
                NumberAxis ya = (NumberAxis) waveformChart.getYAxis();
                double t = xa.getValueForDisplay(xa.sceneToLocal(e.getSceneX(), e.getSceneY()).getX()).doubleValue();
                double v = ya.getValueForDisplay(ya.sceneToLocal(e.getSceneX(), e.getSceneY()).getY()).doubleValue();
                lblCursor.setText(String.format("t = %.3f ms   val = %.4f", t, v));
            } catch (Exception ignored) {}
        });

        // ── Analysis window selection ─────────────────────────────────────────
        // Two sliders to define the sub-segment used for FFT / Phasor / Potencia.
        // Moving them changes winStartSample / winEndSample used by all analysis.
        HBox winBar = new HBox(8);
        winBar.setAlignment(Pos.CENTER_LEFT);
        winBar.setPadding(new Insets(4, 0, 2, 0));
        winBar.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: #0078D4; " +
                        "-fx-border-width: 0 0 0 3; -fx-padding: 6 10 6 10;");

        Label winTitle = new Label("VENTANA DE ANÁLISIS  —  región del registro usada para FFT / Fasores / Potencia:");
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

        Button btnWinReset = mkSmBtn("Todo");
        btnWinReset.setOnAction(e -> { winStartSlider.setValue(0); winEndSlider.setValue(100); });

        Button btnWinApply = mkBtn("Analizar ventana", "#0078D4");
        btnWinApply.setOnAction(e -> analyzeAll());

        ChangeListener<Number> winCL = (obs, o, n) -> updateAnalysisWindow();
        winStartSlider.valueProperty().addListener(winCL);
        winEndSlider.valueProperty().addListener(winCL);

        winBar.getChildren().addAll(lbFrom, winStartSlider, lbTo, winEndSlider,
                                    lblWinInfo, btnWinReset, btnWinApply);

        VBox winSection = new VBox(3, winTitle, winBar);
        winSection.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-radius: 4;");
        winSection.setPadding(new Insets(6));

        content.getChildren().addAll(ctrlBar, colorBar, waveformChart, winSection);
        tab.setContent(content);
        return tab;
    }

    private void updateAnalysisWindow() {
        if (currentRecord == null || winStartSlider == null) {
            winStartSample = 0; winEndSample = -1; return;
        }
        int n = currentRecord.numSamples;
        double startPct = winStartSlider.getValue() / 100.0;
        double endPct   = winEndSlider.getValue()   / 100.0;
        if (endPct <= startPct + 0.005) endPct = Math.min(1.0, startPct + 0.01);
        winStartSample = (int)(n * startPct);
        winEndSample   = (int)(n * endPct);
        double fs     = currentRecord.getEffectiveSampleRate();
        double tStart = winStartSample / fs * 1000.0;
        double tEnd   = winEndSample   / fs * 1000.0;
        int    nSamp  = winEndSample - winStartSample;
        if (lblWinInfo != null)
            lblWinInfo.setText(String.format("%.2f ms → %.2f ms  (%d muestras = %.1f ciclos)",
                tStart, tEnd, nSamp, nSamp / fs * currentRecord.nominalFrequency));
        plotWaveforms();
    }

    // ── Tab 2: FFT + Tabla de Armónicos ──────────────────────────────────────

    private Tab buildFftTab() {
        Tab tab = new Tab("📊 Espectro FFT / Armónicos");

        VBox content = new VBox(6);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        // Description panel
        Label fftDesc = new Label(
            "ANÁLISIS ESPECTRAL (FFT — Transformada de Fourier)\n" +
            "Descompone la señal en sus componentes de frecuencia. " +
            "H1 = fundamental (50 Hz nominal), H2 = 100 Hz, H3 = 150 Hz, etc.\n" +
            "THD = Distorsión Armónica Total = √(H2²+H3²+…+H50²) / H1 × 100%.\n" +
            "Límites típicos (IEC 61000-2-2 / IEEE 519):  THDv ≤ 8%  |  H5 ≤ 6%  |  H7 ≤ 5%  |  H11 ≤ 3.5%  |  H13 ≤ 3%");
        fftDesc.setWrapText(true);
        fftDesc.setStyle("-fx-font-size: 10.5px; -fx-text-fill: " + Theme.TEXT + "; -fx-background-color: " + Theme.BG + ";" +
                         "-fx-border-color: #0078D4; -fx-border-width: 0 0 0 3; -fx-padding: 6 10 6 10;");

        CategoryAxis xF = new CategoryAxis(); xF.setLabel("Orden Armónico (Hn = n × 50 Hz)");
        xF.setStyle("-fx-tick-label-fill: #000000;");
        NumberAxis yF = new NumberAxis();  yF.setLabel("Magnitud relativa (% de H1 fundamental)");
        yF.setStyle("-fx-tick-label-fill: #000000;");

        fftChart = new BarChart<>(xF, yF);
        fftChart.setAnimated(false);
        fftChart.setPrefHeight(220);
        fftChart.setStyle("-fx-background-color: " + Theme.BG + ";");
        fftChart.setLegendVisible(true);

        Label tblTitle = sectionLbl("TABLA DE ARMÓNICOS  —  H1 es el fundamental (100%).  Mag = valor RMS del armónico.  % = proporción respecto a H1.");

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
        Tab tab = new Tab("🔄 Fasores");

        BorderPane content = new BorderPane();
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        // AnchorPane evita la dependencia circular de tamaño que tenía StackPane:
        // StackPane se dimensionaba por sus hijos (el Canvas), y el Canvas estaba
        // vinculado (bind) al tamaño del StackPane → layout inestable → centro erróneo.
        // Con AnchorPane en el CENTER del BorderPane, el BorderPane dicta el tamaño
        // del AnchorPane; los listeners actualizan el Canvas sin retroalimentación.
        AnchorPane canvasPane = new AnchorPane();
        canvasPane.setStyle("-fx-background-color: " + Theme.BG + ";");
        canvasPane.setMinSize(0, 0);
        phasorCanvas = new Canvas(10, 10);
        canvasPane.getChildren().add(phasorCanvas);
        canvasPane.widthProperty().addListener((obs, ov, nv) -> {
            phasorCanvas.setWidth(Math.max(10, nv.doubleValue() - 16));
            drawPhasors();
        });
        canvasPane.heightProperty().addListener((obs, ov, nv) -> {
            phasorCanvas.setHeight(Math.max(10, nv.doubleValue() - 16));
            drawPhasors();
        });

        VBox infoPanel = new VBox(8);
        infoPanel.setPadding(new Insets(12));
        infoPanel.setPrefWidth(230);
        infoPanel.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 0 1;");

        Label phasTitle = sectionLbl("VECTORES FUNDAMENTALES (H1)");
        lblPhasorValues = new Label("Seleccione 2–6 canales para ver el diagrama fasorial.\n\nCada vector muestra la magnitud y fase del armónico fundamental.");
        lblPhasorValues.setWrapText(true);
        lblPhasorValues.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-family: monospace;");

        cbNormIndep = new CheckBox("Normalizar V/I independiente");
        cbNormIndep.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
        cbNormIndep.setTooltip(new Tooltip(
            "Cuando está activo, las tensiones y corrientes se escalan\n" +
            "a su propio máximo, haciéndose ambas visibles aunque\n" +
            "la proporción V/I sea muy grande (ej. 13200V vs 140A).\n\n" +
            "El círculo de referencia = 100% del máximo de cada grupo."));
        cbNormIndep.setSelected(true);
        cbNormIndep.setOnAction(e -> drawPhasors());

        Label tip = new Label("Tip: seleccione Va, Vb, Vc (o Ia, Ib, Ic) para análisis trifásico.");
        tip.setWrapText(true);
        tip.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + ";");

        infoPanel.getChildren().addAll(phasTitle, cbNormIndep, new Separator(), lblPhasorValues, new Separator(), tip);

        content.setCenter(canvasPane);
        content.setRight(infoPanel);
        tab.setContent(content);
        return tab;
    }

    // ── Tab 4: Secuencias Simétricas + Potencia ───────────────────────────────

    private Tab buildSeqPowerTab() {
        Tab tab = new Tab("⚡ Secuencias / Potencia");

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: " + Theme.BG + ";");

        // Sequence section
        Label seqHdr = sectionLbl("COMPONENTES SIMÉTRICAS — Transformada de Fortescue");
        Label seqInstr = new Label(
            "Seleccione exactamente 3 canales (fase A, B, C) de tensión o corriente para calcular.");
        seqInstr.setWrapText(true);
        seqInstr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        lblSeqResult = new Label("— Seleccione 3 canales para calcular —");
        lblSeqResult.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-family: monospace; -fx-wrap-text: true;");
        lblSeqResult.setWrapText(true);

        Button btnSeq = mkBtn("Calcular secuencias", "#0078D4");
        btnSeq.setOnAction(e -> calculateSequences());

        // Power section
        Label powHdr  = sectionLbl("ANÁLISIS DE POTENCIA");
        Label powInstr = new Label(
            "Seleccione exactamente 2 canales: primero la tensión (V), luego la corriente (A).");
        powInstr.setWrapText(true);
        powInstr.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        lblPowerResult = new Label("— Seleccione 2 canales (V + I) para calcular —");
        lblPowerResult.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-family: monospace; -fx-wrap-text: true;");
        lblPowerResult.setWrapText(true);

        Button btnPow = mkBtn("Calcular potencia", "#CA5010");
        btnPow.setOnAction(e -> calculatePower());

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

    private HBox buildStatusBar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(4, 16, 4, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1 0 0 0;");
        lblStatus = new Label("Listo — Abra un archivo .cfg para comenzar");
        lblStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        bar.getChildren().add(lblStatus);
        return bar;
    }

    // ── File operations ───────────────────────────────────────────────────────

    /**
     * Disparo manual de COMTRADE: graba un registro con el estado actual de todos los feeders
     * activos y lo carga automáticamente en el visor.
     * No tiene cooldown (triggerManual bypasses it).
     */
    private void captureNow() {
        List<FeederConfig> configs = app.getFeederConfigs();
        if (configs.isEmpty()) {
            status("Sin feeders activos — no se puede capturar");
            return;
        }

        status("Disparando captura COMTRADE...");

        // Disparo para todos los feeders activos con datos recientes
        int triggered = 0;
        for (FeederConfig cfg : configs) {
            FeederMeasurement m = app.getLatestMeasurements().get(cfg.getFeederId());
            if (m != null) {
                app.getComtradeTrigger().triggerManual(m, cfg);
                triggered++;
            }
        }

        if (triggered == 0) {
            status("Sin mediciones disponibles — conecte un feeder primero");
            return;
        }

        final int count = triggered;
        // Esperar a que el escritor termine (~500ms) y luego cargar el archivo más reciente
        Executors.newSingleThreadExecutor().execute(() -> {
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            File newestCfg = findNewestCfgFile(new File("records"));
            Platform.runLater(() -> {
                if (newestCfg != null) {
                    loadFile(newestCfg);
                    status("Captura completada (" + count + " feeder(s)) — " + newestCfg.getName());
                } else {
                    status("Captura disparada pero no se encontró archivo .cfg en records/");
                }
            });
        });
    }

    /** Devuelve el archivo .cfg más reciente dentro de dir (recursivo 1 nivel) */
    private File findNewestCfgFile(File dir) {
        if (dir == null || !dir.exists()) return null;
        File[] cfgFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".cfg"));
        if (cfgFiles == null || cfgFiles.length == 0) {
            // Buscar en subdirectorios de primer nivel
            File[] subdirs = dir.listFiles(File::isDirectory);
            if (subdirs == null) return null;
            File newest = null;
            for (File sub : subdirs) {
                File candidate = findNewestCfgFile(sub);
                if (candidate != null && (newest == null || candidate.lastModified() > newest.lastModified())) {
                    newest = candidate;
                }
            }
            return newest;
        }
        Arrays.sort(cfgFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return cfgFiles[0];
    }

    private void openCfgFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Abrir archivo COMTRADE (.cfg)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("COMTRADE CFG", "*.cfg", "*.CFG"));
        // Iniciar en el directorio de registros si existe
        File recDir = new File("records");
        if (recDir.exists()) fc.setInitialDirectory(recDir);
        File f = fc.showOpenDialog(app.getPrimaryStage());
        if (f == null) return;
        loadFile(f);
    }

    /** Carga programáticamente un archivo .cfg (p.ej. desde RecordsPanel) */
    public void loadFile(File f) {
        if (f == null || !f.exists()) return;
        try {
            currentRecord = ComtradeReader.load(f);
            if (tfFilePath != null) tfFilePath.setText(f.getAbsolutePath());
            populateInfo();
            populateChannels();
            clearCharts();
            status("Cargado: " + currentRecord);
        } catch (Exception ex) {
            status("Error cargando: " + ex.getMessage());
            new Alert(Alert.AlertType.ERROR, "Error COMTRADE:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void populateInfo() {
        if (currentRecord == null) { lblInfo.setText("Sin archivo"); return; }
        String s = String.format(
            "Estación: %s\nDispositivo: %s\nRev: %s\nFormato: %s\nCh A: %d  Ch D: %d\n" +
            "Fs: %.0f Hz\nMuestras: %d\nDuración: %.4f s\nInicio: %s",
            currentRecord.stationName, currentRecord.deviceId, currentRecord.revisionYear,
            currentRecord.dataFormat, currentRecord.numAnalogChannels, currentRecord.numDigitalChannels,
            currentRecord.sampleRate, currentRecord.numSamples,
            currentRecord.getDurationSeconds(), currentRecord.startTimestamp);
        lblInfo.setText(s);
    }

    private void populateChannels() {
        channelList.getItems().clear();
        if (currentRecord == null) return;

        // Initialize per-channel colors and rebuild dynamic color pickers
        channelColors.clear();
        for (int i = 0; i < currentRecord.numAnalogChannels; i++) {
            channelColors.put(i, CHANNEL_COLORS[i % CHANNEL_COLORS.length]);
        }
        if (dynamicColorBar != null) {
            // Keep only the "🎨 Colores:" label (index 0) and the cbNormWaveforms group
            // Rebuild: clear all except the first label, then add pickers, then normWave checkbox
            dynamicColorBar.getChildren().clear();
            Label colorLbl = new Label("🎨 Colores:");
            colorLbl.setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;");
            dynamicColorBar.getChildren().add(colorLbl);
            for (int i = 0; i < currentRecord.numAnalogChannels; i++) {
                final int chIdx = i;
                ColorPicker cp = new ColorPicker(Color.web(channelColors.get(i)));
                cp.setPrefWidth(50); cp.setPrefHeight(24);
                cp.setStyle("-fx-color-label-visible: false; -fx-font-size: 10px;");
                String chName = currentRecord.analogChannelNames.get(i);
                cp.setTooltip(new Tooltip(chName));
                cp.setOnAction(e -> {
                    channelColors.put(chIdx, toHex(cp.getValue()));
                    plotWaveforms();
                    drawPhasors();
                });
                dynamicColorBar.getChildren().add(cp);
            }
            Button btnReset = mkSmBtn("Reset");
            btnReset.setOnAction(e -> {
                for (int i = 0; i < currentRecord.numAnalogChannels; i++) {
                    String def = CHANNEL_COLORS[i % CHANNEL_COLORS.length];
                    channelColors.put(i, def);
                    // pickers start at index 1 (index 0 = label)
                    if (dynamicColorBar.getChildren().size() > i + 1) {
                        Node node = dynamicColorBar.getChildren().get(i + 1);
                        if (node instanceof ColorPicker) ((ColorPicker) node).setValue(Color.web(def));
                    }
                }
                plotWaveforms(); drawPhasors();
            });
            dynamicColorBar.getChildren().addAll(btnReset, new Label("   "), cbNormWaveforms);
        }

        for (int i = 0; i < currentRecord.analogChannelNames.size(); i++) {
            String unit = i < currentRecord.analogChannelUnits.size()
                ? " [" + currentRecord.analogChannelUnits.get(i) + "]" : "";
            channelList.getItems().add((i + 1) + ": " + currentRecord.analogChannelNames.get(i) + unit);
        }
    }

    private void clearCharts() {
        // Reset analysis window
        winStartSample = 0; winEndSample = -1;
        if (winStartSlider != null) winStartSlider.setValue(0);
        if (winEndSlider   != null) winEndSlider.setValue(100);
        if (lblWinInfo     != null) lblWinInfo.setText("Todo el registro");
        if (waveformChart  != null) waveformChart.getData().clear();
        if (fftChart       != null) fftChart.getData().clear();
        if (harmonicsTable != null) harmonicsTable.getItems().clear();
        drawPhasors();
        if (lblSeqResult   != null) lblSeqResult.setText("— Seleccione 3 canales —");
        if (lblPowerResult != null) lblPowerResult.setText("— Seleccione 2 canales (V + I) —");
    }

    private void onSelectionChanged() {
        plotWaveforms();
        plotFft();
        drawPhasors();
    }

    private void analyzeAll() {
        plotWaveforms();
        plotFft();
        drawPhasors();
        List<Integer> sel = selectedIndices();
        if (sel.size() == 3) calculateSequences();
        if (sel.size() == 2) calculatePower();
    }

    // ── Waveform plotting ─────────────────────────────────────────────────────

    private void plotWaveforms() {
        waveformChart.getData().clear();
        if (currentRecord == null || currentRecord.analogData == null) return;
        List<Integer> sel = selectedIndices();
        if (sel.isEmpty()) return;

        int n     = currentRecord.numSamples;
        double fs = currentRecord.getEffectiveSampleRate();

        // Determine visible range: zoom applies from start, then offset by window start
        double zoomFraction = zoomSlider != null ? zoomSlider.getValue() / 100.0 : 1.0;
        int visibleSamples  = (int) Math.max(2, n * zoomFraction);
        int step            = Math.max(1, visibleSamples / MAX_DISPLAY_POINTS);

        // Analysis window boundaries (in time) for the status label
        int wsS = (winEndSample < 0) ? 0 : winStartSample;
        int weS = (winEndSample < 0) ? n : winEndSample;

        // Compute per-unit scale if normalization is enabled
        boolean normWave = cbNormWaveforms != null && cbNormWaveforms.isSelected();
        double scaleV = 1.0, scaleI = 1.0;
        if (normWave) {
            for (int idx : sel) {
                if (idx >= currentRecord.numAnalogChannels) continue;
                String unit = idx < currentRecord.analogChannelUnits.size()
                    ? currentRecord.analogChannelUnits.get(idx).trim() : "";
                boolean isV = isVoltageUnit(unit);
                double peak = 0;
                for (int s = 0; s < n; s++) peak = Math.max(peak, Math.abs(currentRecord.analogData[idx][s]));
                if (isV) scaleV = Math.max(scaleV, peak);
                else     scaleI = Math.max(scaleI, peak);
            }
            if (scaleV <= 0) scaleV = 1.0;
            if (scaleI <= 0) scaleI = 1.0;
        }
        ((NumberAxis) waveformChart.getYAxis()).setLabel(normWave ? "Amplitud (p.u.)" : "Amplitud");

        // Build series with per-channel colors
        List<String> seriesColors = new ArrayList<>();
        for (int idx : sel) {
            if (idx >= currentRecord.numAnalogChannels) continue;
            String unit = idx < currentRecord.analogChannelUnits.size()
                ? currentRecord.analogChannelUnits.get(idx).trim() : "";
            boolean isV = isVoltageUnit(unit);
            double scale = normWave ? (isV ? scaleV : scaleI) : 1.0;
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(chNameWithUnit(idx));
            for (int s = 0; s < visibleSamples; s += step) {
                double tMs = currentRecord.timestamps != null && s < currentRecord.timestamps.length
                    ? currentRecord.timestamps[s] / 1000.0
                    : s / fs * 1000.0;
                series.getData().add(new XYChart.Data<>(tMs, currentRecord.analogData[idx][s] / scale));
            }
            waveformChart.getData().add(series);
            seriesColors.add(channelColors.getOrDefault(idx, CHANNEL_COLORS[idx % CHANNEL_COLORS.length]));
        }

        // Apply colors after chart has rendered (Timeline ensures nodes exist)
        new Timeline(new KeyFrame(Duration.millis(60), ev -> {
            for (int i = 0; i < seriesColors.size(); i++) {
                applySeriesColor(i, seriesColors.get(i));
            }
        })).play();

        double tWinStart = wsS / fs * 1000.0;
        double tWinEnd   = weS / fs * 1000.0;
        String winStr = (winEndSample < 0)
            ? "ventana: todo el registro"
            : String.format("ventana análisis: %.2f–%.2f ms", tWinStart, tWinEnd);
        status(String.format("Formas de onda: %d canal(es), %d puntos de %d  |  %s",
            sel.size(), Math.min(visibleSamples, MAX_DISPLAY_POINTS), n, winStr));
    }

    // ── FFT + Harmonics table ─────────────────────────────────────────────────

    private void plotFft() {
        fftChart.getData().clear();
        harmonicsTable.getColumns().clear();
        harmonicsTable.getItems().clear();

        if (currentRecord == null || currentRecord.analogData == null) return;
        List<Integer> sel = selectedIndices();
        if (sel.isEmpty()) return;

        double fs = currentRecord.getEffectiveSampleRate();
        double f0 = currentRecord.nominalFrequency;

        // Build spectra for all selected channels using the analysis window
        int maxOrder = 50;
        List<String>   chNames = new ArrayList<>();
        List<double[]> mags    = new ArrayList<>();
        List<String>   units   = new ArrayList<>();

        for (int idx : sel) {
            if (idx >= currentRecord.numAnalogChannels) continue;
            chNames.add(chNameWithUnit(idx));
            mags.add(calculateFFTMagnitude(extractWindow(currentRecord.analogData[idx]), fs, f0, maxOrder));
            units.add(idx < currentRecord.analogChannelUnits.size()
                ? currentRecord.analogChannelUnits.get(idx).trim() : "");
        }
        if (mags.isEmpty()) return;

        // FFT Bar chart — show as % of H1 — series names include unit
        for (int c = 0; c < chNames.size(); c++) {
            double h1 = mags.get(c)[0];
            if (h1 <= 0) continue;
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(chNames.get(c));
            for (int h = 1; h <= maxOrder; h++) {
                series.getData().add(new XYChart.Data<>("H" + h, mags.get(c)[h - 1] / h1 * 100.0));
            }
            fftChart.getData().add(series);
        }

        // Table columns: Orden | Frec(Hz) | [per channel: Mag(unit) | %H1]
        TableColumn<ObservableList<String>, String> colOrder = new TableColumn<>("Orden");
        colOrder.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(0)));
        colOrder.setPrefWidth(52);
        TableColumn<ObservableList<String>, String> colFreq = new TableColumn<>("Frec. (Hz)");
        colFreq.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(1)));
        colFreq.setPrefWidth(72);
        harmonicsTable.getColumns().addAll(colOrder, colFreq);

        for (int c = 0; c < chNames.size(); c++) {
            final int ci = c;
            String unit = units.get(c);
            String header = chNames.get(c);

            TableColumn<ObservableList<String>, String> colMag = new TableColumn<>(header + " Mag [" + unit + "]");
            colMag.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(2 + ci * 2)));
            harmonicsTable.getColumns().add(colMag);

            TableColumn<ObservableList<String>, String> colPct = new TableColumn<>(header + " % H1");
            colPct.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(2 + ci * 2 + 1)));
            harmonicsTable.getColumns().add(colPct);
        }

        // Fill rows H1..H50
        for (int h = 1; h <= maxOrder; h++) {
            ObservableList<String> row = FXCollections.observableArrayList();
            row.add("H" + h);
            row.add(String.format("%.1f", h * f0));  // frequency
            for (int c = 0; c < mags.size(); c++) {
                double mag = mags.get(c)[h - 1];
                double h1  = mags.get(c)[0];
                row.add(String.format("%.4f", mag));
                row.add(h > 1 && h1 > 0 ? String.format("%.2f%%", mag / h1 * 100.0) : (h == 1 ? "100.00%" : "—"));
            }
            harmonicsTable.getItems().add(row);
        }

        // THD summary row
        ObservableList<String> thdRow = FXCollections.observableArrayList();
        thdRow.add("THD");
        thdRow.add("—");
        StringBuilder statusSb = new StringBuilder("FFT  |");
        for (int c = 0; c < mags.size(); c++) {
            double[] m = mags.get(c);
            double thdSq = 0;
            for (int h = 1; h < m.length; h++) thdSq += m[h] * m[h];
            double thd = m[0] > 0 ? Math.sqrt(thdSq) / m[0] * 100.0 : 0;
            thdRow.add("—");
            thdRow.add(String.format("THD=%.2f%%", thd));
            statusSb.append(String.format("  %s: H1=%.4f  THD=%.2f%%", chNames.get(c), m[0], thd));
        }
        harmonicsTable.getItems().add(thdRow);
        status(statusSb.toString() + "  |  Fs=" + String.format("%.0f Hz", fs));
    }

    // ── Phasor diagram ────────────────────────────────────────────────────────

    private void drawPhasors() {
        GraphicsContext gc = phasorCanvas.getGraphicsContext2D();
        double W  = phasorCanvas.getWidth();
        double H  = phasorCanvas.getHeight();
        if (W <= 0 || H <= 0) return;
        double cx = W / 2, cy = H / 2;
        double R  = Math.min(W, H) * 0.36;

        // Background
        gc.setFill(Color.web("#F0F0F0")); gc.fillRect(0, 0, W, H);

        // Reference circle + axes
        gc.setStroke(Color.web("#CCCCCC")); gc.setLineWidth(1);
        gc.strokeOval(cx - R, cy - R, R * 2, R * 2);
        gc.setStroke(Color.web("#CCCCCC")); gc.setLineDashes(4);
        gc.strokeLine(cx - R * 1.12, cy, cx + R * 1.12, cy);
        gc.strokeLine(cx, cy - R * 1.12, cx, cy + R * 1.12);
        gc.setLineDashes(0);

        // Axis labels
        gc.setFill(Color.web("#333333")); gc.setFont(javafx.scene.text.Font.font(11));
        gc.fillText("0°",   cx + R * 1.04, cy + 4);
        gc.fillText("90°",  cx - 22,       cy - R * 1.04);
        gc.fillText("180°", cx - R * 1.14, cy + 4);
        gc.fillText("270°", cx - 22,       cy + R * 1.08);

        if (currentRecord == null) {
            lblPhasorValues.setText("Cargue un archivo para ver el diagrama.");
            return;
        }
        List<Integer> sel = selectedIndices();
        if (sel.isEmpty()) {
            gc.setFill(Color.web("#333333")); gc.setFont(javafx.scene.text.Font.font(13));
            gc.fillText("Seleccione canales para el diagrama fasorial", 20, H / 2);
            lblPhasorValues.setText("Sin canales seleccionados.");
            return;
        }

        double fs = currentRecord.getEffectiveSampleRate();
        double f0 = currentRecord.nominalFrequency;

        // Compute H1 complex for each channel
        List<double[]> phasors  = new ArrayList<>(); // [mag, phase_rad]
        List<String>   names    = new ArrayList<>();
        List<Boolean>  isVolt   = new ArrayList<>();
        double maxMag = 0, maxV = 0, maxI = 0;
        for (int idx : sel) {
            if (idx >= currentRecord.numAnalogChannels) continue;
            double[][] spec = calculateComplexSpectrum(currentRecord.analogData[idx], fs, f0, 1);
            double mag = spec[0][0];
            phasors.add(new double[]{mag, spec[0][1]});
            maxMag = Math.max(maxMag, mag);
            names.add(chNameWithUnit(idx));
            String unit = idx < currentRecord.analogChannelUnits.size()
                ? currentRecord.analogChannelUnits.get(idx).trim() : "";
            boolean isV = isVoltageUnit(unit);
            isVolt.add(isV);
            if (isV) maxV = Math.max(maxV, mag);
            else     maxI = Math.max(maxI, mag);
        }
        if (maxMag <= 0) { lblPhasorValues.setText("No se pudo calcular espectro."); return; }
        boolean normIndep = cbNormIndep != null && cbNormIndep.isSelected();
        if (maxV <= 0) maxV = maxMag;   // fallback si no hay canales V
        if (maxI <= 0) maxI = maxMag;   // fallback si no hay canales I

        // Referencia de fase: primer canal de tensión (VA/VR) → se coloca en 0°.
        // Si no hay canales V, usar el primer canal disponible como referencia.
        double phaseRef = 0;
        for (int i = 0; i < phasors.size(); i++) {
            if (isVolt.get(i)) { phaseRef = phasors.get(i)[1]; break; }
        }
        if (phaseRef == 0 && !phasors.isEmpty()) phaseRef = phasors.get(0)[1];

        // Segunda pasada: dibujar dos círculos si hay V e I en modo independiente
        if (normIndep && maxV > 0 && maxI > 0 && maxV != maxI) {
            // Círculo auxiliar para el grupo menor (indicativo visual)
            double scaleMinor = Math.min(maxV, maxI) / Math.max(maxV, maxI);
            gc.setStroke(Color.web("#AAAAAA")); gc.setLineWidth(0.8); gc.setLineDashes(3);
            double Rminor = R * scaleMinor;
            gc.strokeOval(cx - Rminor, cy - Rminor, Rminor * 2, Rminor * 2);
            gc.setLineDashes(0);
        }

        // Draw phasors
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Ref: %.1f° → VA en 0°\n", Math.toDegrees(phaseRef)));
        if (normIndep && maxV != maxI) {
            sb.append(String.format("V: max=%.2f  I: max=%.2f\n", maxV, maxI));
        }
        sb.append("\n");
        for (int i = 0; i < phasors.size(); i++) {
            double mag   = phasors.get(i)[0];
            // Restar phaseRef para que VA/VR quede en 0° (horizontal derecha)
            double phase = phasors.get(i)[1] - phaseRef;
            double refMax = normIndep ? (isVolt.get(i) ? maxV : maxI) : maxMag;
            double norm   = mag / refMax * R;
            double ex     = cx + norm * Math.cos(phase);
            double ey     = cy - norm * Math.sin(phase); // screen Y inverted

            int chIdx = i < sel.size() ? sel.get(i) : i;
            Color color = Color.web(channelColors.getOrDefault(chIdx, CHANNEL_COLORS[chIdx % CHANNEL_COLORS.length]));
            gc.setStroke(color); gc.setLineWidth(2.5);
            gc.strokeLine(cx, cy, ex, ey);

            // Arrowhead
            double arrowLen = 10, arrowAng = Math.PI / 8;
            gc.strokeLine(ex, ey,
                ex - arrowLen * Math.cos(phase - arrowAng),
                ey + arrowLen * Math.sin(phase - arrowAng));
            gc.strokeLine(ex, ey,
                ex - arrowLen * Math.cos(phase + arrowAng),
                ey + arrowLen * Math.sin(phase + arrowAng));

            // Label
            gc.setFill(color);
            gc.fillText(names.get(i), ex + 6, ey - 2);

            // Mostrar ángulo relativo a VA (phaseRef)
            double deg = Math.toDegrees(phase);
            sb.append(String.format("%s:  %.4f  ∠ %.1f°\n", names.get(i), mag, deg));
        }
        lblPhasorValues.setText(sb.toString().trim());
    }

    // ── Sequence components (Fortescue) ───────────────────────────────────────

    private void calculateSequences() {
        if (currentRecord == null) return;
        List<Integer> sel = selectedIndices();
        if (sel.size() < 3) {
            lblSeqResult.setText("Necesita exactamente 3 canales seleccionados (A, B, C).");
            return;
        }
        double fs = currentRecord.getEffectiveSampleRate();
        double f0 = currentRecord.nominalFrequency;

        // Complex H1 for each of the first 3 channels
        double[] ma = complexH1(sel.get(0), fs, f0);
        double[] mb = complexH1(sel.get(1), fs, f0);
        double[] mc = complexH1(sel.get(2), fs, f0);

        // Fortescue operator a = e^(j*2π/3)
        double a_re = Math.cos(2 * Math.PI / 3), a_im = Math.sin(2 * Math.PI / 3);
        double a2_re = Math.cos(4 * Math.PI / 3), a2_im = Math.sin(4 * Math.PI / 3);

        double[] aVb  = cmul(a_re,  a_im,  mb[0], mb[1]);
        double[] a2Vc = cmul(a2_re, a2_im, mc[0], mc[1]);
        double[] a2Vb = cmul(a2_re, a2_im, mb[0], mb[1]);
        double[] aVc  = cmul(a_re,  a_im,  mc[0], mc[1]);

        double V1_re = (ma[0] + aVb[0]  + a2Vc[0]) / 3;
        double V1_im = (ma[1] + aVb[1]  + a2Vc[1]) / 3;
        double V2_re = (ma[0] + a2Vb[0] + aVc[0])  / 3;
        double V2_im = (ma[1] + a2Vb[1] + aVc[1])  / 3;
        double V0_re = (ma[0] + mb[0]   + mc[0])    / 3;
        double V0_im = (ma[1] + mb[1]   + mc[1])    / 3;

        double V1 = Math.sqrt(V1_re * V1_re + V1_im * V1_im);
        double V2 = Math.sqrt(V2_re * V2_re + V2_im * V2_im);
        double V0 = Math.sqrt(V0_re * V0_re + V0_im * V0_im);
        double unbal = V1 > 0 ? V2 / V1 * 100.0 : 0;

        // Detect channel type from unit string of first channel
        String unit0 = sel.get(0) < currentRecord.analogChannelUnits.size()
            ? currentRecord.analogChannelUnits.get(sel.get(0)).trim() : "";
        String prefix = unitLooksLikeVoltage(unit0) ? "V" : unitLooksLikeCurrent(unit0) ? "I" : "X";

        String na = chName(sel.get(0)), nb = chName(sel.get(1)), nc = chName(sel.get(2));
        lblSeqResult.setText(String.format(
            "Canales:  %s  /  %s  /  %s\n\n" +
            "%s+ (Secuencia Positiva):  %8.4f   ∠ %6.1f°\n" +
            "%s- (Secuencia Negativa):  %8.4f   ∠ %6.1f°\n" +
            "%s0 (Secuencia Cero):      %8.4f   ∠ %6.1f°\n\n" +
            "Desbalance  %s-/%s+:  %.2f%%\n" +
            "(Límite EN 50160 / IEC 61000-4-27: ≤ 2%%)",
            na, nb, nc,
            prefix, V1, Math.toDegrees(Math.atan2(V1_im, V1_re)),
            prefix, V2, Math.toDegrees(Math.atan2(V2_im, V2_re)),
            prefix, V0, Math.toDegrees(Math.atan2(V0_im, V0_re)),
            prefix, prefix, unbal));
    }

    // ── Power analysis ────────────────────────────────────────────────────────

    private void calculatePower() {
        if (currentRecord == null) return;
        List<Integer> sel = selectedIndices();
        if (sel.size() < 2) {
            lblPowerResult.setText("Necesita exactamente 2 canales: tensión (V) y corriente (A).");
            return;
        }
        int cvIdx = sel.get(0), ciIdx = sel.get(1);

        // Read units for both channels before we potentially swap
        String vuRaw = cvIdx < currentRecord.analogChannelUnits.size()
            ? currentRecord.analogChannelUnits.get(cvIdx) : "";
        String iuRaw = ciIdx < currentRecord.analogChannelUnits.size()
            ? currentRecord.analogChannelUnits.get(ciIdx) : "";

        // Auto-swap if ch0 is current and ch1 is voltage
        boolean wasSwapped = false;
        if (unitLooksLikeCurrent(vuRaw) && unitLooksLikeVoltage(iuRaw)) {
            int tmp = cvIdx; cvIdx = ciIdx; ciIdx = tmp;
            wasSwapped = true;
        }

        double[] v  = extractWindow(currentRecord.analogData[cvIdx]);
        double[] i  = extractWindow(currentRecord.analogData[ciIdx]);
        int n       = Math.min(v.length, i.length);
        if (n == 0) return;

        double vrms2 = 0, irms2 = 0, pSum = 0;
        for (int s = 0; s < n; s++) {
            vrms2 += v[s] * v[s];
            irms2 += i[s] * i[s];
            pSum  += v[s] * i[s];
        }
        double vrms = Math.sqrt(vrms2 / n);
        double irms = Math.sqrt(irms2 / n);
        double P    = pSum / n;
        double S    = vrms * irms;
        double Q    = Math.sqrt(Math.max(0, S * S - P * P));
        double pf   = S > 0 ? P / S : 0;

        double fs = currentRecord.getEffectiveSampleRate();
        double f0 = currentRecord.nominalFrequency;
        double[] magV = calculateFFTMagnitude(v, fs, f0, 25);
        double[] magI = calculateFFTMagnitude(i, fs, f0, 25);

        double thdVsq = 0, thdIsq = 0;
        for (int h = 1; h < magV.length; h++) thdVsq += magV[h] * magV[h];
        for (int h = 1; h < magI.length; h++) thdIsq += magI[h] * magI[h];
        double thdV = magV[0] > 0 ? Math.sqrt(thdVsq) / magV[0] * 100.0 : 0;
        double thdI = magI[0] > 0 ? Math.sqrt(thdIsq) / magI[0] * 100.0 : 0;

        // Units and names after potential swap
        String vn = chName(cvIdx), in2 = chName(ciIdx);
        String vu = cvIdx < currentRecord.analogChannelUnits.size()
            ? currentRecord.analogChannelUnits.get(cvIdx) : "";
        String iu = ciIdx < currentRecord.analogChannelUnits.size()
            ? currentRecord.analogChannelUnits.get(ciIdx) : "";

        // Validate remaining types (after possible swap)
        boolean ch0isV = unitLooksLikeVoltage(vu);
        boolean ch1isI = unitLooksLikeCurrent(iu);

        String swapNote = wasSwapped
            ? "✓ Canales auto-corregidos (orden invertido → intercambiados para el cálculo).\n"
            : "";
        String warning = "";
        if (!ch0isV && !unitLooksLikeCurrent(vu)) {
            warning += "⚠ Unidad de V (" + vu + ") no reconocida — verifique selección.\n";
        }
        if (!ch1isI && !unitLooksLikeVoltage(iu)) {
            warning += "⚠ Unidad de I (" + iu + ") no reconocida — verifique selección.\n";
        }

        lblPowerResult.setText(String.format(
            "V = %s [%s]    I = %s [%s]\n" +
            "%s%s\n" +
            "Vrms:            %10.4f  %s\n" +
            "Irms:            %10.4f  %s\n" +
            "P  (activa):     %10.4f  W\n" +
            "Q  (reactiva):   %10.4f  VAr  (*)\n" +
            "S  (aparente):   %10.4f  VA\n" +
            "FP (cos φ):      %10.4f\n\n" +
            "THDv:            %10.2f  %%\n" +
            "THDi:            %10.2f  %%\n\n" +
            "(*) Q = √(S²−P²) incluye potencia de distorsión D en sistemas no sinusoidales.",
            vn, vu, in2, iu,
            swapNote, warning,
            vrms, vu, irms, iu, P, Q, S, pf, thdV, thdI));
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    private void exportCsv() {
        if (currentRecord == null || currentRecord.analogData == null) {
            status("Sin datos para exportar");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar COMTRADE a CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        String base = currentRecord.cfgFile != null
            ? currentRecord.cfgFile.getName().replaceAll("(?i)\\.cfg$", "") : "comtrade";
        fc.setInitialFileName(base + "_export.csv");
        File out = fc.showSaveDialog(app.getPrimaryStage());
        if (out == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            StringBuilder hdr = new StringBuilder("timestamp_us");
            for (String name : currentRecord.analogChannelNames) hdr.append(",").append(name.replace(",", ";"));
            pw.println(hdr);
            for (int s = 0; s < currentRecord.numSamples; s++) {
                StringBuilder row = new StringBuilder();
                row.append(currentRecord.timestamps != null && s < currentRecord.timestamps.length
                    ? currentRecord.timestamps[s] : s);
                for (int ch = 0; ch < currentRecord.numAnalogChannels; ch++) {
                    row.append(",");
                    if (ch < currentRecord.analogData.length && s < currentRecord.analogData[ch].length)
                        row.append(currentRecord.analogData[ch][s]);
                }
                pw.println(row);
            }
            status("CSV exportado: " + out.getAbsolutePath());
        } catch (Exception ex) {
            status("Error exportando: " + ex.getMessage());
        }
    }

    // ── FFT / Signal processing ───────────────────────────────────────────────

    /** Returns magnitude[maxOrder] (RMS). */
    private double[] calculateFFTMagnitude(double[] samples, double fs, double f0, int maxOrder) {
        double[][] c = calculateComplexSpectrum(samples, fs, f0, maxOrder);
        double[] mag = new double[maxOrder];
        for (int i = 0; i < maxOrder; i++) mag[i] = c[i][0];
        return mag;
    }

    /**
     * Returns [maxOrder][2] where [h][0] = magnitude_rms, [h][1] = phase_radians.
     */
    private double[][] calculateComplexSpectrum(double[] samples, double fs, double f0, int maxOrder) {
        double[][] result = new double[maxOrder][2];
        if (samples == null || samples.length < 4 || fs <= 0 || f0 <= 0) return result;

        int sampPerCycle = (int) Math.round(fs / f0);
        int numCycles    = Math.max(1, samples.length / sampPerCycle);
        int N            = Math.min(numCycles * sampPerCycle, samples.length);

        int fftSize = 1;
        while (fftSize < N) fftSize <<= 1;

        double[] re = new double[fftSize];
        double[] im = new double[fftSize];
        for (int i = 0; i < N; i++) {
            double w = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / Math.max(1, N - 1)));
            re[i] = samples[i] * w;
        }
        fft(re, im, fftSize);

        // Hann window coherent gain = 0.5 (sum of coefficients ≈ N/2).
        // Correct peak amplitude: A_peak = |X[bin]| * 4 / N  (not 2/N).
        // Convert to RMS: A_rms = A_peak / sqrt(2).
        double freqRes = fs / fftSize;
        for (int h = 1; h <= maxOrder; h++) {
            int bin = (int) Math.round(h * f0 / freqRes);
            if (bin < fftSize / 2) {
                double mag   = Math.sqrt(re[bin] * re[bin] + im[bin] * im[bin]) * 4.0 / N;
                result[h-1][0] = mag / Math.sqrt(2.0);        // RMS
                result[h-1][1] = Math.atan2(im[bin], re[bin]); // phase
            }
        }
        return result;
    }

    /** Cooley-Tukey in-place FFT (n must be power of 2). */
    private void fft(double[] re, double[] im, int n) {
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2.0 * Math.PI / len;
            double wR  = Math.cos(ang), wI = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cR = 1.0, cI = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    double uR = re[i+k], uI = im[i+k];
                    double vR = re[i+k+len/2]*cR - im[i+k+len/2]*cI;
                    double vI = re[i+k+len/2]*cI + im[i+k+len/2]*cR;
                    re[i+k]         = uR + vR; im[i+k]         = uI + vI;
                    re[i+k+len/2]   = uR - vR; im[i+k+len/2]   = uI - vI;
                    double nR = cR*wR - cI*wI;
                    double nI = cR*wI + cI*wR;
                    cR = nR; cI = nI;
                }
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Complex H1 as [re, im] for the given channel. */
    /** Returns true if the unit string looks like a voltage (V, kV, mV, p.u.). */
    private boolean unitLooksLikeVoltage(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toUpperCase();
        return u.equals("V") || u.equals("KV") || u.equals("MV") || u.endsWith("V")
            && !u.equals("AV") && !u.equals("VAR") && !u.equals("VA");
    }

    /** Returns true if the unit string looks like a current (A, kA, mA). */
    private boolean unitLooksLikeCurrent(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toUpperCase();
        return u.equals("A") || u.equals("KA") || u.equals("MA")
            || (u.endsWith("A") && !u.endsWith("VA") && !u.endsWith("KVA"));
    }

    /** Returns the sub-array of signal within the current analysis window. */
    private double[] extractWindow(double[] signal) {
        if (signal == null) return new double[0];
        if (winEndSample < 0) return signal;          // no window set — use all
        int s = Math.max(0, winStartSample);
        int e = Math.min(signal.length, winEndSample);
        if (s >= e) return signal;                     // degenerate — fall back to full
        return Arrays.copyOfRange(signal, s, e);
    }

    /** Channel name including unit suffix, e.g. "TC83:I A [A]". */
    private String chNameWithUnit(int idx) {
        String name = chName(idx);
        if (currentRecord == null) return name;
        String unit = idx < currentRecord.analogChannelUnits.size()
            ? currentRecord.analogChannelUnits.get(idx).trim() : "";
        return unit.isEmpty() ? name : name + " [" + unit + "]";
    }

    /** Complex H1 as [re, im] for the given channel, using the current analysis window. */
    private double[] complexH1(int chIdx, double fs, double f0) {
        if (chIdx >= currentRecord.numAnalogChannels) return new double[]{0, 0};
        double[][] sp = calculateComplexSpectrum(extractWindow(currentRecord.analogData[chIdx]), fs, f0, 1);
        return new double[]{sp[0][0] * Math.cos(sp[0][1]), sp[0][0] * Math.sin(sp[0][1])};
    }

    private double[] cmul(double aR, double aI, double bR, double bI) {
        return new double[]{aR*bR - aI*bI, aR*bI + aI*bR};
    }

    private String chName(int idx) {
        return (currentRecord != null && idx < currentRecord.analogChannelNames.size())
            ? currentRecord.analogChannelNames.get(idx) : "Ch" + (idx + 1);
    }

    private List<Integer> selectedIndices() {
        List<Integer> list = new ArrayList<>();
        for (String item : channelList.getSelectionModel().getSelectedItems()) {
            try { list.add(Integer.parseInt(item.split(":")[0].trim()) - 1); }
            catch (NumberFormatException ignored) {}
        }
        return list;
    }

    private void status(String msg) {
        if (lblStatus != null) lblStatus.setText(msg);
    }

    private Button mkBtn(String txt, String bg) {
        Button b = new Button(txt);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: #FFFFFF;" +
            "-fx-border-color: " + bg + "; -fx-border-width: 1; -fx-border-radius: 4;" +
            "-fx-background-radius: 4; -fx-padding: 5 12 5 12; -fx-cursor: hand;");
        return b;
    }

    private Button mkSmBtn(String txt) {
        Button b = new Button(txt);
        b.setStyle("-fx-background-color: #E1E1E1; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: #ADADAD; -fx-border-width: 1; -fx-border-radius: 4;" +
            "-fx-background-radius: 4; -fx-padding: 5 12 5 12; -fx-cursor: hand;");
        return b;
    }

    private Label sectionLbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0078D4;");
        return l;
    }

    /** Aplica color hex al nodo de la serie nro. slot en el waveformChart (llamar en JavaFX thread). */
    private void applySeriesColor(int slot, String hexColor) {
        if (slot >= waveformChart.getData().size()) return;
        XYChart.Series<?, ?> s = waveformChart.getData().get(slot);
        String style = "-fx-stroke: " + hexColor + "; -fx-background-color: " + hexColor + ", white;";
        if (s.getNode() != null) {
            s.getNode().setStyle("-fx-stroke: " + hexColor + ";");
        }
        // Colorear también la línea de leyenda y todos los símbolos de datos
        for (XYChart.Data<?, ?> d : s.getData()) {
            if (d.getNode() != null) d.getNode().setStyle(style);
        }
    }

    /** Convierte javafx Color a hex CSS (#RRGGBB). */
    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
            (int)(c.getRed()   * 255),
            (int)(c.getGreen() * 255),
            (int)(c.getBlue()  * 255));
    }

    /** True si la unidad del canal es tipo voltaje (V, kV, etc.). */
    private boolean isVoltageUnit(String unit) {
        String u = unit.trim().toLowerCase();
        return u.equals("v") || u.equals("kv") || u.equals("mv") || u.startsWith("v/") || u.contains("volt");
    }
}
