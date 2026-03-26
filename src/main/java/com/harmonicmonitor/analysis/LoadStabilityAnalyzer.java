package com.harmonicmonitor.analysis;

import java.util.List;

/**
 * Análisis de estabilidad de carga mediante el Coeficiente de Variación (CV).
 *
 * CV = σ / μ  (desviación estándar / media)
 *
 * Interpretación para alimentadores MT con cargas electrónicas:
 *
 * - CV < 0.05 (5%):  Carga muy estable → firma de fuentes de alimentación SMPS
 *                    (minería cripto, datacenters). Operan casi en régimen permanente.
 * - CV 0.05-0.15:    Carga semi-estable → mix de electrónica y cargas normales.
 * - CV > 0.15:       Carga variable → carga industrial/comercial convencional.
 * - CV > 0.30:       Carga altamente variable → ciclos de arranque, hornos, etc.
 *
 * Referencia: Caracterización de cargas electrónicas de potencia en redes de
 * distribución - CIGRÉ WG C4.605.
 */
public class LoadStabilityAnalyzer {

    /**
     * Calcula el Coeficiente de Variación de la corriente.
     *
     * @param currentHistory  Lista de valores de corriente (últimas N muestras)
     * @return CV = σ/μ, o 0.0 si hay menos de 2 muestras
     */
    public double calculateCV(List<Double> currentHistory) {
        if (currentHistory == null || currentHistory.size() < 2) return 0.0;

        double mean = calculateMean(currentHistory);
        if (mean < 1e-9) return 0.0;    // evitar división por cero con cargas desenergizadas

        double stdDev = calculateStdDev(currentHistory, mean);
        return stdDev / mean;
    }

    /**
     * Calcula el CV para un array primitivo.
     */
    public double calculateCV(double[] values) {
        if (values == null || values.length < 2) return 0.0;
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;
        if (mean < 1e-9) return 0.0;
        double variance = 0;
        for (double v : values) variance += (v - mean) * (v - mean);
        variance /= values.length;
        return Math.sqrt(variance) / mean;
    }

    private double calculateMean(List<Double> values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private double calculateStdDev(List<Double> values, double mean) {
        double variance = 0;
        for (double v : values) variance += (v - mean) * (v - mean);
        variance /= values.size();
        return Math.sqrt(variance);
    }

    /**
     * Evalúa el nivel de estabilidad de la carga.
     */
    public StabilityLevel assessStability(double cv) {
        if      (cv < 0.02)  return StabilityLevel.VERY_STABLE;
        else if (cv < 0.05)  return StabilityLevel.STABLE;
        else if (cv < 0.15)  return StabilityLevel.MODERATE;
        else if (cv < 0.30)  return StabilityLevel.VARIABLE;
        else                 return StabilityLevel.HIGHLY_VARIABLE;
    }

    public enum StabilityLevel {
        VERY_STABLE     ("Muy estable (CV<2%)",    "Firma típica de minería cripto / HPC"),
        STABLE          ("Estable (CV<5%)",         "Posible carga electrónica de alta densidad"),
        MODERATE        ("Semi-estable (CV<15%)",   "Mix de cargas electrónicas y convencionales"),
        VARIABLE        ("Variable (CV<30%)",        "Carga industrial / comercial convencional"),
        HIGHLY_VARIABLE ("Altamente variable (>30%)","Carga con ciclos de arranque/parada frecuentes");

        public final String label;
        public final String interpretation;
        StabilityLevel(String label, String interpretation) {
            this.label          = label;
            this.interpretation = interpretation;
        }
    }
}
