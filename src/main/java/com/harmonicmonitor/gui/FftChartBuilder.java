package com.harmonicmonitor.gui;

import com.harmonicmonitor.comtrade.ComtradeDsp;
import com.harmonicmonitor.comtrade.ComtradeReader.ComtradeRecord;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One-shot builder that populates an FFT BarChart and harmonics TableView from a ComtradeRecord.
 * Extracted from ComtradePanel.plotFft() as part of refactoring F4-001.
 *
 * DSP helpers (calculateFFTMagnitude, calculateComplexSpectrum, fft, extractWindow) are shared
 * with other methods in ComtradePanel and remain there as package-private static methods.
 */
class FftChartBuilder {

    private final ComtradeRecord                      record;
    private final BarChart<String, Number>            fftChart;
    private final TableView<ObservableList<String>>   harmonicsTable;
    private final int                                 winStartSample;
    private final int                                 winEndSample;
    private final Consumer<String>                    status;
    private final List<Integer>                       selectedIndices;

    FftChartBuilder(
            ComtradeRecord                    record,
            BarChart<String, Number>          fftChart,
            TableView<ObservableList<String>> harmonicsTable,
            int                               winStartSample,
            int                               winEndSample,
            Consumer<String>                  status,
            List<Integer>                     selectedIndices) {

        this.record          = record;
        this.fftChart        = fftChart;
        this.harmonicsTable  = harmonicsTable;
        this.winStartSample  = winStartSample;
        this.winEndSample    = winEndSample;
        this.status          = status;
        this.selectedIndices = selectedIndices;
    }

    void render() {
        fftChart.getData().clear();
        harmonicsTable.getColumns().clear();
        harmonicsTable.getItems().clear();

        if (record == null || record.analogData == null) return;
        List<Integer> sel = selectedIndices;
        if (sel.isEmpty()) return;

        double fs = record.getEffectiveSampleRate();
        double f0 = record.nominalFrequency;

        // Build spectra for all selected channels using the analysis window
        int maxOrder   = 50; // FFT calculado hasta H50 (THD correcto)
        int chartOrder = 13; // gráfico de barras limitado a H13 (legibilidad)
        List<String>   chNames = new ArrayList<>();
        List<double[]> mags    = new ArrayList<>();
        List<String>   units   = new ArrayList<>();

        for (int idx : sel) {
            if (idx >= record.numAnalogChannels) continue;
            chNames.add(WaveformChartBuilder.chNameWithUnit(record, idx));
            mags.add(ComtradeDsp.calculateFFTMagnitude(
                ComtradeDsp.extractWindow(record.analogData[idx], winStartSample, winEndSample),
                fs, f0, maxOrder));
            units.add(idx < record.analogChannelUnits.size()
                ? record.analogChannelUnits.get(idx).trim() : "");
        }
        if (mags.isEmpty()) return;

        // FFT Bar chart — show as % of H1 — series names include unit — limitado a H13
        for (int c = 0; c < chNames.size(); c++) {
            double h1 = mags.get(c)[0];
            if (h1 <= 0) continue;
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(chNames.get(c));
            for (int h = 1; h <= chartOrder; h++) {
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
            String unit   = units.get(c);
            String header = chNames.get(c);

            TableColumn<ObservableList<String>, String> colMag = new TableColumn<>(header + " Mag [" + unit + "]");
            colMag.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(2 + ci * 2)));
            harmonicsTable.getColumns().add(colMag);

            TableColumn<ObservableList<String>, String> colPct = new TableColumn<>(header + " % H1");
            colPct.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(2 + ci * 2 + 1)));
            harmonicsTable.getColumns().add(colPct);
        }

        // Fill rows H1..H13
        for (int h = 1; h <= chartOrder; h++) {
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
        status.accept(statusSb.toString() + "  |  Fs=" + String.format("%.0f Hz", fs));
    }
}
