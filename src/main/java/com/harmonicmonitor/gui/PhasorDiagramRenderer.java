package com.harmonicmonitor.gui;

import com.harmonicmonitor.comtrade.ComtradeDsp;
import com.harmonicmonitor.comtrade.ComtradeReader.ComtradeRecord;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-shot renderer that draws a phasor diagram on a JavaFX Canvas.
 * Extracted from ComtradePanel.drawPhasors() as part of refactoring F4-001.
 *
 * DSP helpers (calculateComplexSpectrum, extractWindow) are shared with other methods
 * in ComtradePanel and are called via the package-private static methods on that class.
 */
class PhasorDiagramRenderer {

    private final ComtradeRecord       record;
    private final Canvas               canvas;
    private final Label                lblPhasorValues;
    private final CheckBox             cbNormIndep;
    private final Map<Integer, String> channelColors;
    private final int                  winStartSample;
    private final int                  winEndSample;
    private final List<Integer>        selectedIndices;

    PhasorDiagramRenderer(
            ComtradeRecord       record,
            Canvas               canvas,
            Label                lblPhasorValues,
            CheckBox             cbNormIndep,
            Map<Integer, String> channelColors,
            int                  winStartSample,
            int                  winEndSample,
            List<Integer>        selectedIndices) {

        this.record          = record;
        this.canvas          = canvas;
        this.lblPhasorValues = lblPhasorValues;
        this.cbNormIndep     = cbNormIndep;
        this.channelColors   = channelColors;
        this.winStartSample  = winStartSample;
        this.winEndSample    = winEndSample;
        this.selectedIndices = selectedIndices;
    }

    void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double W  = canvas.getWidth();
        double H  = canvas.getHeight();
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

        if (record == null) {
            lblPhasorValues.setText("Cargue un archivo para ver el diagrama.");
            return;
        }
        List<Integer> sel = selectedIndices;
        if (sel.isEmpty()) {
            gc.setFill(Color.web("#333333")); gc.setFont(javafx.scene.text.Font.font(13));
            gc.fillText("Seleccione canales para el diagrama fasorial", 20, H / 2);
            lblPhasorValues.setText("Sin canales seleccionados.");
            return;
        }

        double fs = record.getEffectiveSampleRate();
        double f0 = record.nominalFrequency;

        // Compute H1 complex for each channel
        List<double[]> phasors  = new ArrayList<>(); // [mag, phase_rad]
        List<String>   names    = new ArrayList<>();
        List<Boolean>  isVolt   = new ArrayList<>();
        double maxMag = 0, maxV = 0, maxI = 0;
        for (int idx : sel) {
            if (idx >= record.numAnalogChannels) continue;
            double[][] spec = ComtradeDsp.calculateComplexSpectrum(
                ComtradeDsp.extractWindow(record.analogData[idx], winStartSample, winEndSample),
                fs, f0, 1);
            double mag = spec[0][0];
            phasors.add(new double[]{mag, spec[0][1]});
            maxMag = Math.max(maxMag, mag);
            names.add(WaveformChartBuilder.chNameWithUnit(record, idx));
            String unit = idx < record.analogChannelUnits.size()
                ? record.analogChannelUnits.get(idx).trim() : "";
            boolean isV = unitLooksLikeVoltage(unit);
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
            Color color = Color.web(channelColors.getOrDefault(chIdx,
                ComtradePanel.CHANNEL_COLORS[chIdx % ComtradePanel.CHANNEL_COLORS.length]));
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

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns true if the unit string looks like a voltage (V, kV, mV, p.u.). */
    private static boolean unitLooksLikeVoltage(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toUpperCase();
        return u.equals("V") || u.equals("KV") || u.equals("MV") || u.endsWith("V")
            && !u.equals("AV") && !u.equals("VAR") && !u.equals("VA");
    }
}
