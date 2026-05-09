package com.harmonicmonitor.gui;

import com.harmonicmonitor.comtrade.ComtradeDsp;
import com.harmonicmonitor.comtrade.ComtradeReader.ComtradeRecord;

import java.util.List;

/**
 * Computes Fortescue sequence components and single-phase power analysis
 * from a COMTRADE record window.
 *
 * Methods return formatted result strings; the caller is responsible for
 * displaying them in the appropriate UI label.
 */
class SequencePowerAnalyzer {

    private final ComtradeRecord record;
    private final int            winStart;
    private final int            winEnd;

    SequencePowerAnalyzer(ComtradeRecord record, int winStart, int winEnd) {
        this.record   = record;
        this.winStart = winStart;
        this.winEnd   = winEnd;
    }

    // ── Fortescue ─────────────────────────────────────────────────────────────

    /**
     * Returns a formatted string with positive/negative/zero sequence components
     * and imbalance percentage, or an error message if fewer than 3 channels are selected.
     */
    String calculateSequences(List<Integer> sel) {
        if (record == null) return "";
        if (sel.size() < 3) return "Necesita exactamente 3 canales seleccionados (A, B, C).";

        double fs = record.getEffectiveSampleRate();
        double f0 = record.nominalFrequency;

        double[] ma = complexH1(sel.get(0), fs, f0);
        double[] mb = complexH1(sel.get(1), fs, f0);
        double[] mc = complexH1(sel.get(2), fs, f0);

        // Fortescue operator a = e^(j*2π/3)
        double a_re  = Math.cos(2 * Math.PI / 3), a_im  = Math.sin(2 * Math.PI / 3);
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

        double V1    = Math.sqrt(V1_re * V1_re + V1_im * V1_im);
        double V2    = Math.sqrt(V2_re * V2_re + V2_im * V2_im);
        double V0    = Math.sqrt(V0_re * V0_re + V0_im * V0_im);
        double unbal = V1 > 0 ? V2 / V1 * 100.0 : 0;

        String unit0 = sel.get(0) < record.analogChannelUnits.size()
            ? record.analogChannelUnits.get(sel.get(0)).trim() : "";
        String prefix = unitLooksLikeVoltage(unit0) ? "V"
            : unitLooksLikeCurrent(unit0) ? "I" : "X";

        String na = chName(sel.get(0)), nb = chName(sel.get(1)), nc = chName(sel.get(2));
        return String.format(
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
            prefix, prefix, unbal);
    }

    // ── Power analysis ────────────────────────────────────────────────────────

    /**
     * Returns a formatted string with P, Q, S, PF, THDv, THDi values,
     * or an error message if fewer than 2 channels are selected.
     */
    String calculatePower(List<Integer> sel) {
        if (record == null) return "";
        if (sel.size() < 2) return "Necesita exactamente 2 canales: tensión (V) y corriente (A).";

        int cvIdx = sel.get(0), ciIdx = sel.get(1);

        String vuRaw = cvIdx < record.analogChannelUnits.size()
            ? record.analogChannelUnits.get(cvIdx) : "";
        String iuRaw = ciIdx < record.analogChannelUnits.size()
            ? record.analogChannelUnits.get(ciIdx) : "";

        // Auto-swap if ch0 is current and ch1 is voltage
        boolean wasSwapped = false;
        if (unitLooksLikeCurrent(vuRaw) && unitLooksLikeVoltage(iuRaw)) {
            int tmp = cvIdx; cvIdx = ciIdx; ciIdx = tmp;
            wasSwapped = true;
        }

        double[] v = ComtradeDsp.extractWindow(record.analogData[cvIdx], winStart, winEnd);
        double[] i = ComtradeDsp.extractWindow(record.analogData[ciIdx], winStart, winEnd);
        int n = Math.min(v.length, i.length);
        if (n == 0) return "Sin muestras en ventana seleccionada.";

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

        double fs   = record.getEffectiveSampleRate();
        double f0   = record.nominalFrequency;
        double[] magV = ComtradeDsp.calculateFFTMagnitude(v, fs, f0, 25);
        double[] magI = ComtradeDsp.calculateFFTMagnitude(i, fs, f0, 25);

        double thdVsq = 0, thdIsq = 0;
        for (int h = 1; h < magV.length; h++) thdVsq += magV[h] * magV[h];
        for (int h = 1; h < magI.length; h++) thdIsq += magI[h] * magI[h];
        double thdV = magV[0] > 0 ? Math.sqrt(thdVsq) / magV[0] * 100.0 : 0;
        double thdI = magI[0] > 0 ? Math.sqrt(thdIsq) / magI[0] * 100.0 : 0;

        String vn = chName(cvIdx), in2 = chName(ciIdx);
        String vu = cvIdx < record.analogChannelUnits.size()
            ? record.analogChannelUnits.get(cvIdx) : "";
        String iu = ciIdx < record.analogChannelUnits.size()
            ? record.analogChannelUnits.get(ciIdx) : "";

        boolean ch0isV = unitLooksLikeVoltage(vu);
        boolean ch1isI = unitLooksLikeCurrent(iu);

        String swapNote = wasSwapped
            ? "✓ Canales auto-corregidos (orden invertido → intercambiados para el cálculo).\n" : "";
        String warning = "";
        if (!ch0isV && !unitLooksLikeCurrent(vu))
            warning += "⚠ Unidad de V (" + vu + ") no reconocida — verifique selección.\n";
        if (!ch1isI && !unitLooksLikeVoltage(iu))
            warning += "⚠ Unidad de I (" + iu + ") no reconocida — verifique selección.\n";

        return String.format(
            "V = %s [%s]    I = %s [%s]\n%s%s\n" +
            "Vrms:            %10.4f  %s\n" +
            "Irms:            %10.4f  %s\n" +
            "P  (activa):     %10.4f  W\n" +
            "Q  (reactiva):   %10.4f  VAr  (*)\n" +
            "S  (aparente):   %10.4f  VA\n" +
            "FP (cos φ):      %10.4f\n\n" +
            "THDv:            %10.2f  %%\n" +
            "THDi:            %10.2f  %%\n\n" +
            "(*) Q = √(S²−P²) incluye potencia de distorsión D en sistemas no sinusoidales.",
            vn, vu, in2, iu, swapNote, warning,
            vrms, vu, irms, iu, P, Q, S, pf, thdV, thdI);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private double[] complexH1(int chIdx, double fs, double f0) {
        if (chIdx >= record.numAnalogChannels) return new double[]{0, 0};
        double[][] sp = ComtradeDsp.calculateComplexSpectrum(
            ComtradeDsp.extractWindow(record.analogData[chIdx], winStart, winEnd), fs, f0, 1);
        return new double[]{sp[0][0] * Math.cos(sp[0][1]), sp[0][0] * Math.sin(sp[0][1])};
    }

    private double[] cmul(double aR, double aI, double bR, double bI) {
        return new double[]{aR*bR - aI*bI, aR*bI + aI*bR};
    }

    private String chName(int idx) {
        return (record != null && idx < record.analogChannelNames.size())
            ? record.analogChannelNames.get(idx) : "Ch" + (idx + 1);
    }

    private boolean unitLooksLikeVoltage(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toUpperCase();
        return u.equals("V") || u.equals("KV") || u.equals("MV")
            || (u.endsWith("V") && !u.equals("AV") && !u.equals("VAR") && !u.equals("VA"));
    }

    private boolean unitLooksLikeCurrent(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toUpperCase();
        return u.equals("A") || u.equals("KA") || u.equals("MA")
            || (u.endsWith("A") && !u.endsWith("VA") && !u.endsWith("KVA"));
    }
}
