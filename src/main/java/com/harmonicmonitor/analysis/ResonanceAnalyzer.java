package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

/**
 * Análisis de resonancia armónica en alimentadores MT.
 *
 * La resonancia paralela ocurre cuando la reactancia capacitiva de los cables MT
 * (o banco de condensadores de corrección FP) resuena con la reactancia inductiva
 * del sistema (transformador + red de suministro).
 *
 * Frecuencia de resonancia:
 *   f_res = f1 × √(Scc / Qc)
 *   Donde:
 *     f1   = frecuencia fundamental (50 o 60 Hz)
 *     Scc  = potencia de cortocircuito en el nodo (MVA)
 *     Qc   = potencia reactiva capacitiva del banco o del cable (MVAR)
 *
 * Equivalente en circuito LC serie/paralelo:
 *   f_res = 1 / (2π × √(L×C))
 *
 * Orden de resonancia: h_res = f_res / f1
 *
 * Riesgo: si h_res coincide con un armónico de corriente importante (H5, H7, etc.),
 * puede producirse una amplificación de tensión armónica peligrosa.
 *
 * Referencias: IEC 61000-3-6:2008, Cigré WG C4.109.
 */
public class ResonanceAnalyzer {

    /**
     * Analiza el riesgo de resonancia y actualiza la medición.
     */
    public void analyze(FeederMeasurement m, FeederConfig cfg) {
        double f1     = (m.getFrequency() > 0) ? m.getFrequency() : 50.0;
        double sccMva = cfg.getShortCircuitMva();
        double capUf  = cfg.getFeederCapacitanceMicroF();

        // --- Método 1: f_res = 1 / (2π√LC) ---
        // L estimado a partir de Scc: Xs = Un²/Scc → L = Xs/(2πf1)
        double vn      = cfg.getNominalVoltageKv() * 1000;       // V
        double xs      = (vn * vn) / (sccMva * 1e6);             // Ω
        double l       = xs / (2 * Math.PI * f1);                // H
        double c       = capUf * 1e-6;                           // F
        double fResHz  = (l > 0 && c > 0) ? 1.0 / (2 * Math.PI * Math.sqrt(l * c)) : 0.0;

        // --- Método 2: h_res = √(Scc/Qc) (método por impedancias) ---
        // Qc estimado de la capacitancia del cable
        double xc      = (c > 0) ? 1.0 / (2 * Math.PI * f1 * c) : 0.0;
        double qcMvar  = (xc > 0) ? (vn * vn) / xc / 1e6 : 0.0;
        double hRes    = (qcMvar > 0) ? Math.sqrt(sccMva / qcMvar) : 0.0;

        // Usar el promedio de ambos métodos si ambos son válidos
        double finalFreq;
        int finalOrder;
        if (fResHz > f1 && hRes > 1) {
            finalFreq  = (fResHz + hRes * f1) / 2.0;
            finalOrder = (int) Math.round((fResHz / f1 + hRes) / 2.0);
        } else if (fResHz > f1) {
            finalFreq  = fResHz;
            finalOrder = (int) Math.round(fResHz / f1);
        } else if (hRes > 1) {
            finalFreq  = hRes * f1;
            finalOrder = (int) Math.round(hRes);
        } else {
            finalFreq  = 0;
            finalOrder = 0;
        }

        m.setResonanceFrequency(finalFreq);
        m.setResonanceOrder(finalOrder);
    }

    /**
     * Evalúa el riesgo de resonancia dado el orden armónico estimado
     * y la amplitud de las corrientes armónicas medidas.
     *
     * @param m    medición con espectro y orden de resonancia calculados por {@link #analyze}
     * @param cfg  configuración del alimentador (para obtener el umbral de amplificación)
     * @return descripción textual del riesgo
     */
    public String assessResonanceRisk(FeederMeasurement m, FeederConfig cfg) {
        int hr = m.getResonanceOrder();
        if (hr <= 0) return "Sin datos suficientes para evaluar resonancia";

        double[] spec = m.getHarmonicCurrentL1();
        if (spec == null || spec.length <= hr) return "Orden de resonancia H" + hr + " fuera del espectro medido";

        double h1     = spec[0];
        double hResAmp = (hr < spec.length) ? spec[hr - 1] : 0.0;
        double ratio   = (h1 > 0) ? hResAmp / h1 : 0.0;

        double maxRatio = (cfg != null) ? cfg.getResonanceAmplificationMax() : 3.0;
        if (ratio > maxRatio) {
            return String.format("RIESGO ALTO: H%d amplificado %.1f\u00d7 el fundamental. Posible resonancia activa.", hr, ratio);
        }

        // Verificar si hay corrientes significativas cerca del orden de resonancia
        boolean nearbyHarmonics = false;
        for (int delta = -1; delta <= 1; delta++) {
            int idx = hr + delta - 1;
            if (idx >= 0 && idx < spec.length && h1 > 0 && spec[idx] / h1 > 0.05) {
                nearbyHarmonics = true;
                break;
            }
        }

        if (nearbyHarmonics) {
            return String.format("PRECAUCIÓN: Frecuencia de resonancia estimada H%d (%.0f Hz). Armónicos cercanos presentes.",
                hr, m.getResonanceFrequency());
        }

        return String.format("Resonancia estimada en H%d (%.0f Hz). Sin amplificación significativa detectada.",
            hr, m.getResonanceFrequency());
    }

    /**
     * Calcula el orden de resonancia teórico para una configuración de red dada.
     * Útil para el módulo de ayuda / simulación.
     *
     * @param sccMva     Potencia de cortocircuito (MVA)
     * @param qcMvar     Potencia reactiva capacitiva (MVAR)
     * @return           Orden armónico de resonancia (e.g. 4.8 ≈ entre H4 y H5)
     */
    public static double theoreticalResonanceOrder(double sccMva, double qcMvar) {
        if (qcMvar <= 0) return 0;
        return Math.sqrt(sccMva / qcMvar);
    }

    /**
     * Calcula la frecuencia de resonancia LC.
     *
     * @param inductanceH    Inductancia equivalente del sistema (H)
     * @param capacitanceMicroF  Capacitancia total del feeder (µF)
     * @return               Frecuencia de resonancia (Hz)
     */
    public static double lcResonanceFrequency(double inductanceH, double capacitanceMicroF) {
        double c = capacitanceMicroF * 1e-6;
        if (inductanceH <= 0 || c <= 0) return 0;
        return 1.0 / (2 * Math.PI * Math.sqrt(inductanceH * c));
    }
}
