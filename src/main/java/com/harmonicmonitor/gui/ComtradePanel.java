package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.comtrade.ComtradeReader;
import com.harmonicmonitor.comtrade.ComtradeReader.ComtradeRecord;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.*;

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
    // Package-private so builder classes in the same package can reference it.
    static final String[] CHANNEL_COLORS = {
        "#0078D4", "#CA5010", "#4CAF50", "#C42B1C",
        "#9C27B0", "#00BCD4", "#FF9800", "#8BC34A"
    };

    private final HarmonicMonitorApp app;
    private final BorderPane root;
    // Package-private: read by ComtradeTabBuilder event handlers at click time.
    ComtradeRecord currentRecord = null;

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
    // Package-private: read by ComtradeTabBuilder event handlers at click time.
    int    winStartSample = 0;
    int    winEndSample   = -1;
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
        ComtradeHeaderBuilder hb = new ComtradeHeaderBuilder(this);
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setTop(hb.buildHeader());

        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color: " + Theme.BG + ";");
        split.setDividerPositions(0.22);
        split.getItems().addAll(hb.buildSidebar(), buildTabs());
        pane.setCenter(split);
        pane.setBottom(hb.buildStatusBar());

        tfFilePath  = hb.tfFilePath;
        lblInfo     = hb.lblInfo;
        channelList = hb.channelList;
        lblStatus   = hb.lblStatus;
        return pane;
    }

    private TabPane buildTabs() {
        ComtradeTabBuilder tb = new ComtradeTabBuilder(this);
        TabPane tp = tb.build();
        waveformChart   = tb.waveformChart;
        fftChart        = tb.fftChart;
        harmonicsTable  = tb.harmonicsTable;
        phasorCanvas    = tb.phasorCanvas;
        zoomSlider      = tb.zoomSlider;
        lblZoom         = tb.lblZoom;
        winStartSlider  = tb.winStartSlider;
        winEndSlider    = tb.winEndSlider;
        lblCursor       = tb.lblCursor;
        lblWinInfo      = tb.lblWinInfo;
        lblPhasorValues = tb.lblPhasorValues;
        lblSeqResult    = tb.lblSeqResult;
        lblPowerResult  = tb.lblPowerResult;
        dynamicColorBar = tb.dynamicColorBar;
        cbNormWaveforms = tb.cbNormWaveforms;
        cbNormIndep     = tb.cbNormIndep;
        return tp;
    }

    /** Called by {@link ComtradeTabBuilder} slider listeners. */
    void updateAnalysisWindow() {
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
            lblWinInfo.setText(String.format("%.2f ms \u2192 %.2f ms  (%d muestras = %.1f ciclos)",
                tStart, tEnd, nSamp, nSamp / fs * currentRecord.nominalFrequency));
        plotWaveforms();
    }

    // ── File operations ───────────────────────────────────────────────────────

    /** Called by {@link ComtradeHeaderBuilder} capture button handler. */
    void captureNow() {
        new ComtradeCaptureAction(app, this::status, this::loadFile).execute();
    }

    /** Called by {@link ComtradeHeaderBuilder} open button handler. */
    void openCfgFile() {
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

    /** Called by {@link ComtradeHeaderBuilder} channel list selection listener. */
    void onSelectionChanged() {
        plotWaveforms();
        plotFft();
        drawPhasors();
    }

    /** Called by {@link ComtradeTabBuilder} button handlers and waveform window controls. */
    void analyzeAll() {
        plotWaveforms();
        plotFft();
        drawPhasors();
        List<Integer> sel = selectedIndices();
        SequencePowerAnalyzer spa = new SequencePowerAnalyzer(currentRecord, winStartSample, winEndSample);
        if (sel.size() == 3) lblSeqResult.setText(spa.calculateSequences(sel));
        if (sel.size() == 2) lblPowerResult.setText(spa.calculatePower(sel));
    }

    // ── Waveform plotting ─────────────────────────────────────────────────────

    /** Called by {@link ComtradeTabBuilder} slider and checkbox handlers. */
    void plotWaveforms() {
        if (currentRecord == null) return;
        new WaveformChartBuilder(
            currentRecord, waveformChart,
            zoomSlider, cbNormWaveforms != null && cbNormWaveforms.isSelected(),
            channelColors, winStartSample, winEndSample,
            this::status, selectedIndices()
        ).render();
    }

    // ── FFT + Harmonics table ─────────────────────────────────────────────────

    void plotFft() {
        if (currentRecord == null) return;
        new FftChartBuilder(
            currentRecord, fftChart, harmonicsTable,
            winStartSample, winEndSample,
            this::status, selectedIndices()
        ).render();
    }

    // ── Phasor diagram ────────────────────────────────────────────────────────

    /** Called by {@link ComtradeTabBuilder} canvas resize and checkbox handlers. */
    void drawPhasors() {
        new PhasorDiagramRenderer(
            currentRecord, phasorCanvas, lblPhasorValues,
            cbNormIndep, channelColors,
            winStartSample, winEndSample, selectedIndices()
        ).render();
    }


    // ── CSV export ────────────────────────────────────────────────────────────

    /** Called by {@link ComtradeHeaderBuilder} CSV button handler. */
    void exportCsv() {
        ComtradeCsvExporter.export(currentRecord, app.getPrimaryStage(), this::status);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Called by {@link ComtradeTabBuilder} sequence/power button handlers. */
    List<Integer> selectedIndices() {
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

    /** Used by {@link ComtradeTabBuilder} to create styled buttons. */
    Button mkBtn(String txt, String bg) {
        Button b = new Button(txt);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: #FFFFFF;" +
            "-fx-border-color: " + bg + "; -fx-border-width: 1; -fx-border-radius: 4;" +
            "-fx-background-radius: 4; -fx-padding: 5 12 5 12; -fx-cursor: hand;");
        return b;
    }

    Button mkSmBtn(String txt) {
        Button b = new Button(txt);
        b.setStyle("-fx-background-color: #E1E1E1; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: #ADADAD; -fx-border-width: 1; -fx-border-radius: 4;" +
            "-fx-background-radius: 4; -fx-padding: 5 12 5 12; -fx-cursor: hand;");
        return b;
    }

    Label sectionLbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0078D4;");
        return l;
    }

    /** Convierte javafx Color a hex CSS (#RRGGBB). */
    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
            (int)(c.getRed()   * 255),
            (int)(c.getGreen() * 255),
            (int)(c.getBlue()  * 255));
    }

}
