package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederMeasurement;

/**
 * Módulo de análisis armónico.
 *
 * Responsabilidades:
 *  - Cálculo de THD (Total Harmonic Distortion) a partir del espectro armónico.
 *  - Cálculo de relaciones de armónicos individuales (H5/H1, H7/H1, etc.).
 *
 * NOTA (v1.2): la estimación de espectro para IEDs sin array MHAI.HA fue
 * suprimida. Si el IED no expone el espectro, los ratios armónicos quedan
 * en 0 y el clasificador opera solo sobre CV, THD y FP (ver guarda en
 * calculateHarmonicRatios). Nunca se fabrican valores espectrales.
 *
 * Fórmula THD:
 *   THD% = 100 × √(H2² + H3² + ... + Hn²) / H1
 *
 * Referencia: IEC 61000-4-7, IEEE 519-2022.
 */
public class HarmonicAnalyzer {

    /**
     * Calcula el THD de tensión y corriente a partir del espectro
     * almacenado en la medición.
     * Solo se ejecuta si el IED no proporcionó el THD directamente.
     */
    public void calculateThd(FeederMeasurement m) {
        m.setThdCurrentL1(thdPercent(m.getHarmonicCurrentL1()));
        m.setThdCurrentL2(thdPercent(m.getHarmonicCurrentL2()));
        m.setThdCurrentL3(thdPercent(m.getHarmonicCurrentL3()));
        m.setThdVoltageL1(thdPercent(m.getHarmonicVoltageL1()));
        m.setThdVoltageL2(thdPercent(m.getHarmonicVoltageL2()));
        m.setThdVoltageL3(thdPercent(m.getHarmonicVoltageL3()));
    }

    /**
     * THD% = 100 × √(ΣHn² para n≥2) / H1
     * Índice 0 del array = H1 (fundamental).
     */
    public double thdPercent(double[] spectrum) {
        if (spectrum == null || spectrum.length < 2) return 0.0;
        double h1 = spectrum[0];
        if (h1 < 1e-9) return 0.0;
        double sumSq = 0.0;
        for (int i = 1; i < spectrum.length; i++) {
            sumSq += spectrum[i] * spectrum[i];
        }
        return 100.0 * Math.sqrt(sumSq) / h1;
    }

    /**
     * Calcula las relaciones de armónicos individuales respecto al fundamental.
     * Actualiza los campos h3h1Ratio, h5h1Ratio, h7h1Ratio, h11h1Ratio, h13h1Ratio.
     *
     * <h3>Selección de fase: fase peor COHERENTE (v1.2)</h3>
     * Las normas de medición tratan cada fase como un canal independiente
     * (IEC 61000-4-7 mide por fase; IEC 61000-4-30 agrega por canal, no entre
     * fases) y la práctica de evaluación de IEEE 519 / EN 50160 juzga el
     * cumplimiento sobre la fase más desfavorable. Siguiendo ese criterio,
     * los ratios se toman TODOS de una misma fase: la de mayor distorsión
     * (mayor THD espectral), con L1 como desempate.
     *
     * <p>Por qué no las alternativas:
     * <ul>
     *   <li><b>Solo L1 (comportamiento anterior)</b>: un desequilibrio o un
     *       canal defectuoso en L1 sesga todo el vector; la carga puede estar
     *       concentrada en otra fase.</li>
     *   <li><b>Promedio entre fases</b>: sin base normativa (las normas no
     *       promedian espectros entre fases) y diluye la firma de cargas
     *       monofásicas concentradas en una fase.</li>
     *   <li><b>Máximo por orden (mezclando fases)</b>: rompe la coherencia
     *       intra-fase que necesitan los indicadores de forma. Ejemplo real:
     *       L1 con H5=3.9%, H7=0.2% (PFC, H5/H7=19.5) y L2 con H5=4.2%,
     *       H7=1.0%; el máximo por orden daría H5=4.2%, H7=1.0% → H5/H7=4.2,
     *       destruyendo la firma PFC que sí existe en cada fase.</li>
     * </ul>
     *
     * Estos ratios son la firma característica de los rectificadores de potencia:
     *   - Rectificador 6-pulsos: H5, H7, H11, H13 dominantes
     *   - Fuentes SMPS (crypto/datacenter): H3, H5, H7 dominantes
     *
     * NOTA sobre H3 en feeders MT (23 kV): los transformadores de distribución
     * Dyn (delta/estrella) atrapan la secuencia cero — H3 balanceado circula
     * en el delta y no llega al lado MT. El H3 medido en 23 kV es solo el
     * residuo por desequilibrio de carga y magnetización del transformador.
     */
    public void calculateHarmonicRatios(FeederMeasurement m) {
        double[] spec = worstPhaseSpectrum(m);
        if (spec == null || spec.length < 14) return;
        double h1 = spec[0];
        if (h1 < 1e-9) return;

        m.setH3h1Ratio (spec[2]  / h1);   // H3  (índice 2)
        m.setH5h1Ratio (spec[4]  / h1);   // H5  (índice 4)
        m.setH7h1Ratio (spec[6]  / h1);   // H7  (índice 6)
        m.setH11h1Ratio(spec[10] / h1);   // H11 (índice 10)
        m.setH13h1Ratio(spec[12] / h1);   // H13 (índice 12)
    }

    /**
     * Devuelve el espectro de la fase con mayor distorsión (THD espectral),
     * considerando solo fases con fundamental medible. Desempate: orden
     * L1, L2, L3 (una fase posterior debe SUPERAR a la mejor actual).
     * Si ninguna fase tiene fundamental, devuelve el espectro de L1
     * (la guarda de la llamada dejará los ratios en 0).
     */
    private static double[] worstPhaseSpectrum(FeederMeasurement m) {
        double[][] phases = {
            m.getHarmonicCurrentL1(),
            m.getHarmonicCurrentL2(),
            m.getHarmonicCurrentL3()
        };
        double[] best = phases[0];
        double bestThd = -1.0;
        for (double[] spec : phases) {
            if (spec == null || spec.length < 14 || spec[0] < 1e-9) continue;
            double sumSq = 0;
            for (int i = 1; i < spec.length; i++) sumSq += spec[i] * spec[i];
            double thd = Math.sqrt(sumSq) / spec[0];
            if (thd > bestThd) { bestThd = thd; best = spec; }
        }
        return best;
    }

    /**
     * Calcula el desbalance de tensión según EN 50160:2010 §4.3.4 / IEC 61000-4-30:
     *   Desbalance% = (max desviación de la media) / media × 100
     *
     * Nota: este método aplica el criterio de máxima desviación (EN 50160).
     * El método Fortescue (IEC 61000-2-2) — Vneg/Vpos × 100 — es más preciso
     * cuando se dispone de componentes simétricas (MSQI en IEC 61850).
     */
    public double calculateVoltageUnbalance(FeederMeasurement m) {
        double avg = m.getVoltageAvg();
        if (avg < 1e-6) return 0.0;
        double maxDev = Math.max(
            Math.max(Math.abs(m.getVoltageL1() - avg),
                     Math.abs(m.getVoltageL2() - avg)),
            Math.abs(m.getVoltageL3() - avg));
        return 100.0 * maxDev / avg;
    }

    /**
     * Calcula el desbalance de corriente.
     */
    public double calculateCurrentUnbalance(FeederMeasurement m) {
        double avg = m.getCurrentAvg();
        if (avg < 1e-6) return 0.0;
        double maxDev = Math.max(
            Math.max(Math.abs(m.getCurrentL1() - avg),
                     Math.abs(m.getCurrentL2() - avg)),
            Math.abs(m.getCurrentL3() - avg));
        return 100.0 * maxDev / avg;
    }
}
