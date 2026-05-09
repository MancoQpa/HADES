package com.harmonicmonitor.gui;

import com.harmonicmonitor.model.FeederMeasurement;

import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.control.RadioButton;

/**
 * Updates the harmonic bar chart, summary cards, and data table in
 * {@link HarmonicsPanel} on every polling cycle.
 *
 * Extracted from {@link HarmonicsPanel} (refactor F12-001).
 * Reads panel fields via package-private access; never modifies panel structure.
 */
class HarmonicsDisplayUpdater {

    private final HarmonicsPanel panel;

    HarmonicsDisplayUpdater(HarmonicsPanel panel) {
        this.panel = panel;
    }

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Refreshes all harmonic display widgets from the given measurement.
     * Must be called on the JavaFX Application Thread.
     *
     * @param m non-null measurement (guard is in {@link HarmonicsPanel#refreshDisplay()})
     */
    void refresh(FeederMeasurement m) {
        if (panel.estimatedLabel != null) {
            panel.estimatedLabel.setVisible(m.isSpectrumEstimated());
        }

        double[] iSpec = getSelectedCurrentSpectrum(m);
        double[] vSpec = getSelectedVoltageSpectrum(m);
        if (iSpec == null) return;

        double h1 = iSpec[0];
        double v1 = (vSpec != null && vSpec.length > 0) ? vSpec[0] : 1.0;

        updateBarChart(iSpec, h1);
        updateSummaryCards(iSpec, vSpec, h1, v1);
        updateTable(iSpec, vSpec, h1, v1);
        panel.table.refresh();
    }

    // ── Bar chart ─────────────────────────────────────────────────────────────

    private void updateBarChart(double[] iSpec, double h1) {
        ObservableList<XYChart.Data<String, Number>> data = panel.currentSeries.getData();
        for (int i = 0; i < Math.min(15, data.size()); i++) {
            double pct = (h1 > 1e-6) ? (iSpec[i] / h1 * 100.0) : 0.0;
            if (i == 0) pct = 100.0; // H1 is fundamental = 100%
            data.get(i).setYValue(pct);
        }
        // Color nodes by severity (applied after setYValue so nodes exist)
        for (int i = 0; i < data.size(); i++) {
            XYChart.Data<String, Number> d = data.get(i);
            if (d.getNode() != null) {
                double pct = d.getYValue().doubleValue();
                if      (pct > 15) d.getNode().setStyle("-fx-bar-fill: #C42B1C;");
                else if (pct >  8) d.getNode().setStyle("-fx-bar-fill: #CA5010;");
                else               d.getNode().setStyle("-fx-bar-fill: #0078D4;");
            }
        }
    }

    // ── Summary cards ─────────────────────────────────────────────────────────

    private void updateSummaryCards(double[] iSpec, double[] vSpec, double h1, double v1) {
        for (int idx = 0; idx < HarmonicsPanel.SUMMARY_ORDERS.length; idx++) {
            int order = HarmonicsPanel.SUMMARY_ORDERS[idx];
            if (order > iSpec.length) continue;

            double iAmp = iSpec[order - 1];
            double iPct = (h1 > 1e-6) ? (iAmp / h1 * 100.0) : 0.0;
            double vAmp = (vSpec != null && order <= vSpec.length) ? vSpec[order - 1] : 0.0;
            double vPct = (v1 > 1e-6 && vSpec != null) ? (vAmp / v1 * 100.0) : 0.0;

            panel.summaryCurrentPct[idx].setText(String.format("%.1f%%", iPct));
            panel.summaryCurrentAmp[idx].setText(String.format("%.2f A", iAmp));
            panel.summaryVoltagePct[idx].setText(String.format("%.1f V%%", vPct));

            String color;
            if      (iPct > 15) color = "#C42B1C";
            else if (iPct >  8) color = "#CA5010";
            else                color = "#107C10";
            panel.summaryCurrentPct[idx].setStyle(
                "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        }
    }

    // ── Data table ────────────────────────────────────────────────────────────

    private void updateTable(double[] iSpec, double[] vSpec, double h1, double v1) {
        for (int i = 0; i < Math.min(15, panel.tableData.size()); i++) {
            int order = i + 1;
            double iAmp = (i < iSpec.length) ? iSpec[i] : 0.0;
            double iPct = (h1 > 1e-6 && order > 1)
                ? (iAmp / h1 * 100.0) : (order == 1 ? 100.0 : 0.0);
            double vAmp = (vSpec != null && i < vSpec.length) ? vSpec[i] : 0.0;
            double vPct = (v1 > 1e-6 && vSpec != null && order > 1)
                ? (vAmp / v1 * 100.0) : (order == 1 ? 100.0 : 0.0);

            String status;
            if      (order == 1) status = "\u2014";
            else if (iPct > 15)  status = "\u26D4 CR\u00CDTICO";
            else if (iPct >  8)  status = "\u26A0 ELEVADO";
            else                 status = "\u2713 OK";

            panel.tableData.get(i).update(
                String.format("%.1f",  order * 50.0),
                String.format("%.3f",  iAmp),
                String.format("%.2f",  iPct),
                String.format("%.3f",  vAmp),
                String.format("%.2f",  vPct),
                status
            );
        }
    }

    // ── Spectrum selection helpers ────────────────────────────────────────────

    double[] getSelectedCurrentSpectrum(FeederMeasurement m) {
        RadioButton sel = (RadioButton) panel.phaseGroup.getSelectedToggle();
        if (sel == panel.rbL1 || sel == null) return m.getHarmonicCurrentL1();
        if (sel == panel.rbL2) return m.getHarmonicCurrentL2();
        if (sel == panel.rbL3) return m.getHarmonicCurrentL3();
        return average(m.getHarmonicCurrentL1(), m.getHarmonicCurrentL2(), m.getHarmonicCurrentL3());
    }

    double[] getSelectedVoltageSpectrum(FeederMeasurement m) {
        RadioButton sel = (RadioButton) panel.phaseGroup.getSelectedToggle();
        if (sel == panel.rbL1 || sel == null) return m.getHarmonicVoltageL1();
        if (sel == panel.rbL2) return m.getHarmonicVoltageL2();
        if (sel == panel.rbL3) return m.getHarmonicVoltageL3();
        return average(m.getHarmonicVoltageL1(), m.getHarmonicVoltageL2(), m.getHarmonicVoltageL3());
    }

    private static double[] average(double[] l1, double[] l2, double[] l3) {
        if (l1 == null) return null;
        double[] avg = new double[l1.length];
        for (int i = 0; i < l1.length; i++) {
            avg[i] = (l1[i] + (l2 != null ? l2[i] : 0) + (l3 != null ? l3[i] : 0)) / 3.0;
        }
        return avg;
    }
}
