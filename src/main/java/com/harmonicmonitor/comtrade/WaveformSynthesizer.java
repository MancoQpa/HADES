package com.harmonicmonitor.comtrade;

import com.harmonicmonitor.model.FeederMeasurement;

/**
 * Sintetiza formas de onda en el dominio del tiempo a partir de
 * mediciones RMS + espectro armónico (FeederMeasurement).
 *
 * Genera 6 canales: VA, VB, VC, IA, IB, IC
 * Tasa de muestreo: 64 muestras/ciclo
 *   → 3200 Sa/s a 50 Hz (IEC 61000-4-7, ventana de análisis 200ms = 10 ciclos)
 *   → 3840 Sa/s a 60 Hz
 *
 * Modelo matemático (sistema trifásico equilibrado):
 *
 *   x_n(t) = Σ_h  X_h·√2 · sin(h·ω₀·t + h·φ_n + θ_h)
 *
 * donde φ_A=0°, φ_B=-120°, φ_C=+120°
 *
 * La multiplicación h·φ_n reproduce automáticamente las secuencias:
 *   h≡1 (mod 3) → secuencia positiva  (H1, H4, H7, H10, H13...)
 *   h≡2 (mod 3) → secuencia negativa  (H2, H5, H8, H11, H14...)
 *   h≡0 (mod 3) → secuencia cero      (H3, H6, H9, H12, H15...)
 *
 * Si el espectro de la medición no tiene datos válidos, se sintetizan
 * armónicos usando un patrón de rectificador 6-pulsos (H5/H7/H11/H13).
 */
public class WaveformSynthesizer {

    public static final int SAMPLES_PER_CYCLE = 64;

    /**
     * Sintetiza las 6 formas de onda para un ciclo completo.
     *
     * @param m        medición con RMS, THD y espectro armónico por orden (A o V)
     * @param nCycles  número de ciclos a generar (mínimo 1)
     * @return double[6][nSamples] — índices: 0=VA, 1=VB, 2=VC, 3=IA, 4=IB, 5=IC
     */
    public static double[][] synthesize(FeederMeasurement m, int nCycles) {
        double freq  = (m.getFrequency() > 10) ? m.getFrequency() : 50.0;
        int nSamples = SAMPLES_PER_CYCLE * nCycles;
        double dt    = 1.0 / (freq * SAMPLES_PER_CYCLE);
        double omega = 2.0 * Math.PI * freq;

        // Ángulos de fase por canal (sistema equilibrado directo)
        double[] vPhase = { 0.0, -2.0 * Math.PI / 3.0, +2.0 * Math.PI / 3.0 };

        // Desfase por factor de potencia (corriente retrasada respecto a tensión)
        double pf = m.getPowerFactor();
        if (Math.abs(pf) < 0.01) pf = 0.90;
        double phiPf = -Math.acos(Math.min(1.0, Math.abs(pf)));
        double[] iPhase = {
            phiPf,
            phiPf - 2.0 * Math.PI / 3.0,
            phiPf + 2.0 * Math.PI / 3.0
        };

        // Datos por fase
        double[] vRms = { m.getVoltageL1(), m.getVoltageL2(), m.getVoltageL3() };
        double[] iRms = { m.getCurrentL1(), m.getCurrentL2(), m.getCurrentL3() };
        double[] vThd = { m.getThdVoltageL1(), m.getThdVoltageL2(), m.getThdVoltageL3() };
        double[] iThd = { m.getThdCurrentL1(), m.getThdCurrentL2(), m.getThdCurrentL3() };
        double[][] vSpec = {
            m.getHarmonicVoltageL1(), m.getHarmonicVoltageL2(), m.getHarmonicVoltageL3()
        };
        double[][] iSpec = {
            m.getHarmonicCurrentL1(), m.getHarmonicCurrentL2(), m.getHarmonicCurrentL3()
        };

        double[][] data = new double[6][nSamples];

        for (int ph = 0; ph < 3; ph++) {
            double[] va = buildAmplitudes(vRms[ph], vThd[ph], vSpec[ph], false);
            double[] ia = buildAmplitudes(iRms[ph], iThd[ph], iSpec[ph], true);

            for (int s = 0; s < nSamples; s++) {
                double t  = s * dt;
                double v  = 0.0;
                double i  = 0.0;

                for (int h = 0; h < va.length; h++) {
                    if (va[h] < 1e-9) continue;
                    int n = h + 1;  // orden armónico (1-based)
                    v += va[h] * Math.sqrt(2) * Math.sin(n * omega * t + n * vPhase[ph]);
                }
                for (int h = 0; h < ia.length; h++) {
                    if (ia[h] < 1e-9) continue;
                    int n = h + 1;
                    i += ia[h] * Math.sqrt(2) * Math.sin(n * omega * t + n * iPhase[ph]);
                }

                data[ph    ][s] = v;
                data[ph + 3][s] = i;
            }
        }

        return data;
    }

    /**
     * Construye el array de amplitudes RMS por orden armónico (índice 0 = H1).
     *
     * Prioridad:
     *   1. Si el espectro tiene H1 válido → lo usa directamente (hasta H25)
     *   2. Si no → sintetiza desde totalRms + thdPct con patrón 6-pulsos o carga lineal
     */
    static double[] buildAmplitudes(double totalRms, double thdPct, double[] spec,
                                    boolean isCurrent) {
        if (totalRms < 1e-9) return new double[]{ 0.0 };

        boolean hasSpectrum = (spec != null && spec.length > 1 && spec[0] > 1e-6);

        if (hasSpectrum) {
            int maxH = Math.min(spec.length, 25);
            double[] amps = new double[maxH];
            for (int h = 0; h < maxH; h++) {
                amps[h] = Math.max(0, spec[h]);
            }
            return amps;
        }

        // Sin espectro → síntesis analítica
        double thdFrac = Math.max(0, thdPct) / 100.0;
        double fund    = totalRms / Math.sqrt(1.0 + thdFrac * thdFrac);

        // Pesos relativos: patrón de rectificador 6-pulsos (H5, H7, H11, H13 dominantes)
        //   Índice 0=H1, 1=H2, ..., 16=H17
        double[] relWts = new double[17];
        relWts[0]  = 1.0;   // H1 = fundamental (no se toca)
        if (thdPct > 3.0) {
            // Perfil no lineal: 6-pulsos
            relWts[4]  = 0.60;  // H5
            relWts[6]  = 0.38;  // H7
            relWts[10] = 0.15;  // H11
            relWts[12] = 0.10;  // H13
            relWts[16] = 0.05;  // H17
        }

        // Normalizar armónicos para que THD = thdFrac
        double sumSq = 0;
        for (int h = 1; h < relWts.length; h++) sumSq += relWts[h] * relWts[h];
        double k = (sumSq > 0) ? (thdFrac / Math.sqrt(sumSq)) : 0;

        double[] amps = new double[relWts.length];
        amps[0] = fund;
        for (int h = 1; h < relWts.length; h++) {
            amps[h] = fund * relWts[h] * k;
        }
        return amps;
    }

    /** Pico absoluto máximo de un canal de la matriz de datos */
    public static double peakAmplitude(double[][] data, int ch) {
        double max = 0;
        if (data == null || ch >= data.length) return 1.0;
        for (double v : data[ch]) max = Math.max(max, Math.abs(v));
        return max > 1e-12 ? max : 1.0;
    }
}
