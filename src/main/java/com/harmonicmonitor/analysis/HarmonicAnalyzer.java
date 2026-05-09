package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederMeasurement;

/**
 * Módulo de análisis armónico.
 *
 * Responsabilidades:
 *  - Cálculo de THD (Total Harmonic Distortion) a partir del espectro armónico.
 *  - Cálculo de relaciones de armónicos individuales (H5/H1, H7/H1, etc.).
 *  - Estimación del espectro armónico típico si el IED no lo provee.
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
     * Actualiza los campos h5h1Ratio, h7h1Ratio, h11h1Ratio, h13h1Ratio.
     *
     * Estos ratios son la firma característica de los rectificadores de potencia:
     *   - Rectificador 6-pulsos: H5, H7, H11, H13 dominantes
     *   - Fuentes SMPS (crypto/datacenter): H3, H5, H7 dominantes
     */
    public void calculateHarmonicRatios(FeederMeasurement m) {
        double[] spec = m.getHarmonicCurrentL1();  // usar L1 como referencia
        if (spec == null || spec.length < 14) return;
        double h1 = spec[0];
        if (h1 < 1e-9) return;

        m.setH5h1Ratio (spec[4]  / h1);   // H5  (índice 4)
        m.setH7h1Ratio (spec[6]  / h1);   // H7  (índice 6)
        m.setH11h1Ratio(spec[10] / h1);   // H11 (índice 10)
        m.setH13h1Ratio(spec[12] / h1);   // H13 (índice 12)
    }

    /**
     * Genera un espectro armónico estimado típico para cargas electrónicas
     * de alta densidad (SMPS, rectificadores PFC activos).
     *
     * Se usa cuando el IED no reporta espectro completo pero sí el THD total.
     * La distribución sigue el perfil típico medido en granjas de minería:
     *   H1=100%, H3=40%, H5=35%, H7=20%, H9=10%, H11=8%, H13=5%, ...
     *
     * @param fundamental  valor de la componente fundamental (A o V)
     * @param thdPct       THD total en %
     * @return array de 50 posiciones con el espectro estimado
     */
    public double[] estimateCryptoSpectrum(double fundamental, double thdPct) {
        double[] spectrum = new double[50];
        spectrum[0] = fundamental;  // H1

        // Perfil SMPS: relativeAmplitudes[i] → spectrum[i+1] = H(i+2)
        // i=0→H2, i=1→H3, i=2→H4, i=3→H5 ...  solo impares tienen valor
        double[] relativeAmplitudes = {
            0, 0.40, 0, 0.35, 0, 0.20, 0, 0.10, 0, 0.08, 0, 0.05, 0, 0.03,
            0, 0.02, 0, 0.015, 0, 0.01, 0, 0.008, 0, 0.006, 0
        };

        // Normalizar para que el THD resultante = thdPct
        double sumSq = 0;
        for (double v : relativeAmplitudes) sumSq += v * v;
        double normFactor = (thdPct / 100.0) / Math.sqrt(sumSq);

        for (int i = 0; i < relativeAmplitudes.length && i < 49; i++) {
            spectrum[i + 1] = relativeAmplitudes[i] * fundamental * normFactor;
        }
        return spectrum;
    }

    /**
     * Genera un espectro armónico estimado típico para un rectificador industrial
     * de 6 pulsos (variadores de frecuencia, grandes motores con VFD).
     *
     * Perfil: H1=100%, H5=25%, H7=11%, H11=9%, H13=8%, H17=3%, H19=2.5%
     */
    public double[] estimateSixPulseRectifierSpectrum(double fundamental, double thdPct) {
        double[] spectrum = new double[50];
        spectrum[0] = fundamental;

        // relativeAmplitudes[i] → spectrum[i+1] = H(i+2), por eso H5 necesita i=3
        double[] relativeAmplitudes = new double[49];
        relativeAmplitudes[3]  = 0.25;  // H5
        relativeAmplitudes[5]  = 0.11;  // H7
        relativeAmplitudes[9]  = 0.09;  // H11
        relativeAmplitudes[11] = 0.08;  // H13
        relativeAmplitudes[15] = 0.03;  // H17
        relativeAmplitudes[17] = 0.025; // H19
        relativeAmplitudes[21] = 0.015; // H23
        relativeAmplitudes[23] = 0.012; // H25

        double sumSq = 0;
        for (double v : relativeAmplitudes) sumSq += v * v;
        double normFactor = sumSq > 0 ? (thdPct / 100.0) / Math.sqrt(sumSq) : 1.0;

        for (int i = 0; i < relativeAmplitudes.length && i < 49; i++) {
            spectrum[i + 1] = relativeAmplitudes[i] * fundamental * normFactor;
        }
        return spectrum;
    }

    /**
     * Cuando el IED provee THD pero NO el espectro armónico completo (array en ceros),
     * estima el espectro usando el perfil SMPS (rectificador con condensador bulk, H5/H7 dominantes)
     * normalizado al THD medido.
     *
     * <p><b>LIMITACIÓN IMPORTANTE:</b> este método siempre aplica el perfil SMPS/CRYPTO
     * (H5 ≈ 35%, H7 ≈ 22% del fundamental), independientemente del tipo real de carga.
     * Si la carga es un rectificador industrial de 6 pulsos (VFD), horno de arco u otro
     * tipo no-SMPS, los ratios estimados serán incorrectos y el clasificador podría
     * producir una detección errónea. Solo invocar cuando se sospecha carga SMPS/electrónica
     * de alta densidad, o cuando se requiere una estimación genérica de fallback.
     *
     * <p>Se llama en el poller ANTES de calculateHarmonicRatios(). Marca
     * m.spectrumEstimated=true para que la GUI pueda indicarlo visualmente.
     *
     * <p>No hace nada si el espectro ya tiene datos (H1 > 0).
     */
    public void estimateMissingSpectrum(FeederMeasurement m) {
        if (m.getHarmonicCurrentL1()[0] > 1e-9) return;  // ya tiene espectro

        double thd1 = m.getThdCurrentL1();
        if (thd1 < 0.5) return;  // THD demasiado bajo para estimar de forma fiable

        double thd2 = m.getThdCurrentL2() > 0.5 ? m.getThdCurrentL2() : thd1;
        double thd3 = m.getThdCurrentL3() > 0.5 ? m.getThdCurrentL3() : thd1;

        double i1 = m.getCurrentL1();
        double i2 = m.getCurrentL2();
        double i3 = m.getCurrentL3();
        if (i1 < 1e-6) i1 = m.getCurrentAvg();
        if (i2 < 1e-6) i2 = i1;
        if (i3 < 1e-6) i3 = i1;
        if (i1 < 1e-6) return;  // sin corriente, no se puede escalar el espectro

        m.setHarmonicCurrentL1(estimateCryptoSpectrum(i1, thd1));
        m.setHarmonicCurrentL2(estimateCryptoSpectrum(i2, thd2));
        m.setHarmonicCurrentL3(estimateCryptoSpectrum(i3, thd3));
        m.setSpectrumEstimated(true);
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
