package com.harmonicmonitor.gui;

import com.harmonicmonitor.comtrade.ComtradeReader.ComtradeRecord;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One-shot builder that populates a waveform LineChart from a ComtradeRecord.
 * Extracted from ComtradePanel.plotWaveforms() as part of refactoring F4-001.
 */
class WaveformChartBuilder {

    private static final int MAX_DISPLAY_POINTS = 2000;

    private final ComtradeRecord               record;
    private final LineChart<Number, Number>    chart;
    private final Slider                       zoomSlider;
    private final boolean                      normWaveforms;
    private final Map<Integer, String>         channelColors;
    private final int                          winStartSample;
    private final int                          winEndSample;
    private final Consumer<String>             status;
    private final List<Integer>                selectedIndices;

    WaveformChartBuilder(
            ComtradeRecord            record,
            LineChart<Number, Number> chart,
            Slider                    zoomSlider,
            boolean                   normWaveforms,
            Map<Integer, String>      channelColors,
            int                       winStartSample,
            int                       winEndSample,
            Consumer<String>          status,
            List<Integer>             selectedIndices) {

        this.record          = record;
        this.chart           = chart;
        this.zoomSlider      = zoomSlider;
        this.normWaveforms   = normWaveforms;
        this.channelColors   = channelColors;
        this.winStartSample  = winStartSample;
        this.winEndSample    = winEndSample;
        this.status          = status;
        this.selectedIndices = selectedIndices;
    }

    void render() {
        chart.getData().clear();
        if (record == null || record.analogData == null) return;
        List<Integer> sel = selectedIndices;
        if (sel.isEmpty()) return;

        int n     = record.numSamples;
        double fs = record.getEffectiveSampleRate();

        // Determine visible range: zoom applies from start, then offset by window start
        double zoomFraction = zoomSlider != null ? zoomSlider.getValue() / 100.0 : 1.0;
        int visibleSamples  = (int) Math.max(2, n * zoomFraction);
        int step            = Math.max(1, visibleSamples / MAX_DISPLAY_POINTS);

        // Analysis window boundaries (in time) for the status label
        int wsS = (winEndSample < 0) ? 0 : winStartSample;
        int weS = (winEndSample < 0) ? n : winEndSample;

        // Compute per-unit scale if normalization is enabled
        boolean normWave = normWaveforms;
        double scaleV = 1.0, scaleI = 1.0;
        if (normWave) {
            for (int idx : sel) {
                if (idx >= record.numAnalogChannels) continue;
                String unit = idx < record.analogChannelUnits.size()
                    ? record.analogChannelUnits.get(idx).trim() : "";
                boolean isV = isVoltageUnit(unit);
                double peak = 0;
                for (int s = 0; s < n; s++) peak = Math.max(peak, Math.abs(record.analogData[idx][s]));
                if (isV) scaleV = Math.max(scaleV, peak);
                else     scaleI = Math.max(scaleI, peak);
            }
            if (scaleV <= 0) scaleV = 1.0;
            if (scaleI <= 0) scaleI = 1.0;
        }
        ((NumberAxis) chart.getYAxis()).setLabel(normWave ? "Amplitud (p.u.)" : "Amplitud");

        // Build series with per-channel colors
        List<String> seriesColors = new ArrayList<>();
        for (int idx : sel) {
            if (idx >= record.numAnalogChannels) continue;
            String unit = idx < record.analogChannelUnits.size()
                ? record.analogChannelUnits.get(idx).trim() : "";
            boolean isV = isVoltageUnit(unit);
            double scale = normWave ? (isV ? scaleV : scaleI) : 1.0;
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(chNameWithUnit(record, idx));
            for (int s = 0; s < visibleSamples; s += step) {
                double tMs = record.timestamps != null && s < record.timestamps.length
                    ? record.timestamps[s] / 1000.0
                    : s / fs * 1000.0;
                series.getData().add(new XYChart.Data<>(tMs, record.analogData[idx][s] / scale));
            }
            chart.getData().add(series);
            seriesColors.add(channelColors.getOrDefault(idx,
                ComtradePanel.CHANNEL_COLORS[idx % ComtradePanel.CHANNEL_COLORS.length]));
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
        status.accept(String.format("Formas de onda: %d canal(es), %d puntos de %d  |  %s",
            sel.size(), Math.min(visibleSamples, MAX_DISPLAY_POINTS), n, winStr));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Aplica color hex al nodo de la serie nro. slot en el waveformChart (llamar en JavaFX thread). */
    private void applySeriesColor(int slot, String hexColor) {
        if (slot >= chart.getData().size()) return;
        XYChart.Series<?, ?> s = chart.getData().get(slot);
        String style = "-fx-stroke: " + hexColor + "; -fx-background-color: " + hexColor + ", white;";
        if (s.getNode() != null) {
            s.getNode().setStyle("-fx-stroke: " + hexColor + ";");
        }
        // Colorear también la línea de leyenda y todos los símbolos de datos
        for (XYChart.Data<?, ?> d : s.getData()) {
            if (d.getNode() != null) d.getNode().setStyle(style);
        }
    }

    /** True si la unidad del canal es tipo voltaje (V, kV, etc.). */
    private static boolean isVoltageUnit(String unit) {
        String u = unit.trim().toLowerCase();
        return u.equals("v") || u.equals("kv") || u.equals("mv") || u.startsWith("v/") || u.contains("volt");
    }

    /** Channel name including unit suffix, e.g. "TC83:I A [A]". */
    static String chNameWithUnit(ComtradeRecord record, int idx) {
        String name = chName(record, idx);
        if (record == null) return name;
        String unit = idx < record.analogChannelUnits.size()
            ? record.analogChannelUnits.get(idx).trim() : "";
        return unit.isEmpty() ? name : name + " [" + unit + "]";
    }

    static String chName(ComtradeRecord record, int idx) {
        return (record != null && idx < record.analogChannelNames.size())
            ? record.analogChannelNames.get(idx) : "Ch" + (idx + 1);
    }
}
