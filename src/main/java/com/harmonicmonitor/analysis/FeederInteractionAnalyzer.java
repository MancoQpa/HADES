package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederMeasurement;

import java.util.List;
import java.util.ArrayList;

/**
 * Análisis de interacción entre alimentadores en configuraciones de barra MT común.
 *
 * Cuando múltiples alimentadores comparten la misma barra MT, los armónicos de
 * corriente de un feeder con carga electrónica se propagan a los demás a través
 * de la impedancia de la barra. Este módulo detecta y cuantifica esa interacción.
 *
 * Indicadores de interacción:
 * 1. Correlación de espectros armónicos entre feeders (mismos picos → fuente común)
 * 2. Diferencia de fase entre armónicos (señal de cancelación o adición)
 * 3. Contribución de un feeder al THD de tensión de barra
 *
 * Referencia: IEC 61000-3-6:2008 §7.4 - Método de superposición de contribuciones.
 */
public class FeederInteractionAnalyzer {

    /**
     * Calcula el índice de similitud espectral entre dos feeders (0-1).
     * Un valor alto indica que los armónicos tienen origen común o propagación.
     *
     * @param m1 Medición del feeder 1
     * @param m2 Medición del feeder 2
     * @return   Índice de similitud [0,1]
     */
    public double spectrumSimilarity(FeederMeasurement m1, FeederMeasurement m2) {
        double[] s1 = normalizeSpectrum(m1.getHarmonicCurrentL1());
        double[] s2 = normalizeSpectrum(m2.getHarmonicCurrentL1());
        if (s1 == null || s2 == null) return 0.0;

        // Cosine similarity
        double dot = 0, norm1 = 0, norm2 = 0;
        int len = Math.min(s1.length, s2.length);
        for (int i = 1; i < len; i++) {  // saltar H1 (fundamental)
            dot   += s1[i] * s2[i];
            norm1 += s1[i] * s1[i];
            norm2 += s2[i] * s2[i];
        }
        if (norm1 < 1e-12 || norm2 < 1e-12) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Estima la contribución de un feeder al THD de tensión en la barra MT.
     *
     * Usando el principio de superposición (IEC 61000-3-6):
     *   Uh_barra² ≈ Σ (Ih_feeder_k × Zth_h)²
     *
     * @param feederMeasurement  Medición del feeder cuya contribución se evalúa
     * @param busVoltageKv       Tensión de barra MT (kV)
     * @param sccMva             Potencia de cortocircuito en barra (MVA)
     * @return                   Contribución al THD de tensión (%)
     */
    public double estimateVoltageContribution(FeederMeasurement feederMeasurement,
                                               double busVoltageKv, double sccMva) {
        double vn  = busVoltageKv * 1000;           // V
        double zth = (vn * vn) / (sccMva * 1e6);   // Impedancia de Thévenin (Ω)
        double[] iSpec = feederMeasurement.getHarmonicCurrentL1();
        if (iSpec == null || iSpec[0] < 1e-6) return 0.0;

        double vHarmSumSq = 0;
        for (int h = 2; h <= Math.min(iSpec.length, 50); h++) {
            // Impedancia armónica aproximada: Zh ≈ h × Zth (reactancia dominante)
            double zh = h * zth;
            double vh = iSpec[h - 1] * zh;  // Caída de tensión por armónico h
            vHarmSumSq += vh * vh;
        }
        double vThd = Math.sqrt(vHarmSumSq) / vn * 100;
        return vThd;
    }

    /**
     * Genera un reporte de interacción entre una lista de feeders.
     *
     * @param feeders  Lista de mediciones simultáneas de todos los feeders en la barra
     * @return         Lista de strings con el reporte de interacción
     */
    public List<String> generateInteractionReport(List<FeederMeasurement> feeders) {
        List<String> report = new ArrayList<>();
        if (feeders == null || feeders.size() < 2) {
            report.add("Se requieren al menos 2 feeders para análisis de interacción.");
            return report;
        }

        report.add("=== ANÁLISIS DE INTERACCIÓN ENTRE ALIMENTADORES ===");
        report.add(String.format("Número de alimentadores analizados: %d", feeders.size()));
        report.add("");

        for (int i = 0; i < feeders.size(); i++) {
            for (int j = i + 1; j < feeders.size(); j++) {
                FeederMeasurement m1 = feeders.get(i);
                FeederMeasurement m2 = feeders.get(j);
                double similarity = spectrumSimilarity(m1, m2);
                String level = similarity > 0.8 ? "MUY ALTA" :
                               similarity > 0.6 ? "ALTA" :
                               similarity > 0.4 ? "MODERADA" : "BAJA";
                report.add(String.format("  %s ↔ %s: Similitud espectral = %.1f%% [%s]",
                    m1.getFeederId(), m2.getFeederId(), similarity * 100, level));
                if (similarity > 0.7) {
                    report.add("    → Posible propagación de armónicos entre feeders o fuente común.");
                }
            }
        }

        report.add("");
        report.add("THDi por feeder:");
        for (FeederMeasurement m : feeders) {
            report.add(String.format("  %-10s: THDi = %.1f%%  Carga: %s",
                m.getFeederId(), m.getThdCurrentAvg(), m.getDetectedLoadType()));
        }

        return report;
    }

    /** Normaliza el espectro dividiendo cada componente por el fundamental. */
    private double[] normalizeSpectrum(double[] spectrum) {
        if (spectrum == null || spectrum.length < 2) return null;
        double h1 = spectrum[0];
        if (h1 < 1e-9) return null;
        double[] norm = new double[spectrum.length];
        for (int i = 0; i < spectrum.length; i++) {
            norm[i] = spectrum[i] / h1;
        }
        return norm;
    }
}
