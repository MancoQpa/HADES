package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

/**
 * Clasificador de firma espectral de carga para alimentadores MT.
 *
 * <p>Aplica un árbol de decisión multivariable sobre señales armónicas
 * para identificar el patrón espectral predominante del feeder.
 *
 * <p><b>CONDICIÓN DE USO:</b> el resultado es confiable solo cuando la carga
 * de interés representa &gt;~80% de la demanda total del alimentador.
 * Con cargas mixtas, las firmas se superponen y la discriminación disminuye.
 *
 * <h3>Indicadores utilizados</h3>
 * <ol>
 *   <li><b>CV = σ(I)/μ(I)</b> — Coeficiente de variación de corriente.
 *       Umbral CV &lt; 5%: estimación de ingeniería para SMPS regulado.
 *       <em>Sin respaldo de estándar formal.</em></li>
 *
 *   <li><b>THDi</b> — THD de corriente.
 *       Umbral 5%: alineado con IEEE 519-2022 (TDD, tabla 2).
 *       Umbral 15% para SMPS/cripto: estimación bibliográfica genérica,
 *       no validada con campaña de campo en MT 23 kV.</li>
 *
 *   <li><b>THDv</b> — THD de tensión.
 *       Umbral 5% (UPSTREAM): IEC 61000-3-6:2008 §5, nivel de planif. MT.
 *       Umbral 8% (EN 50160): EN 50160:2010 §4.3.4, percentil 95% MT.</li>
 *
 *   <li><b>H5/H1, H7/H1</b> — Ratios de armónicos de rectificador 6-pulsos.
 *       Valores teóricos: H5 = 1/5 = 20%, H7 = 1/7 ≈ 14.3% (Fourier ideal).
 *       Fuente: teoría estándar de convertidores (Mohan, Undeland, Robbins,
 *       "Power Electronics", §3; Chapman, "Electric Machinery Fundamentals").
 *       Umbrales conservadores (15%, 10%) para tolerar filtrado parcial.</li>
 *
 *   <li><b>H11/H1, H13/H1</b> — Firma extendida 6-pulsos.
 *       Valores teóricos: H11 = 1/11 ≈ 9.1%, H13 = 1/13 ≈ 7.7%.</li>
 *
 *   <li><b>FP</b> — Factor de potencia (|cos φ|).
 *       Umbral 0.92 para distinguir CRYPTO vs DATA_CENTER:
 *       <em>criterio empírico sin validación formal.</em></li>
 * </ol>
 *
 * <h3>Referencias normativas (umbrales con respaldo formal)</h3>
 * <ul>
 *   <li>IEEE Std 519-2022 — Recommended Practice for Harmonic Control</li>
 *   <li>IEC 61000-3-6:2008 — Planning levels for harmonic voltages in MV/HV</li>
 *   <li>IEC 61000-3-12:2011 — Limits for harmonic currents &gt;16 A/phase</li>
 *   <li>EN 50160:2010 — Voltage characteristics in public distribution networks</li>
 *   <li>IEEE Std 1459-2010 — Definitions for the Measurement of Electric Power</li>
 * </ul>
 *
 * <h3>Umbrales de estimación (sin validación de campo)</h3>
 * <ul>
 *   <li>CV &lt; 5%: estimación para SMPS de alta densidad (literatura SMPS/UPS)</li>
 *   <li>THDi &gt; 15% cripto/DC: estimación bibliográfica genérica</li>
 *   <li>FP &gt; 0.92 para CRYPTO: criterio empírico</li>
 * </ul>
 */
public class ElectronicLoadDetector {

    /**
     * Clasifica el tipo de carga y actualiza la medición con el resultado.
     */
    public void classify(FeederMeasurement m, FeederConfig cfg) {
        double thdI  = m.getThdCurrentAvg();
        double thdV  = m.getThdVoltageAvg();
        double cv    = m.getCvCurrent();
        double h5h1  = m.getH5h1Ratio();
        double h7h1  = m.getH7h1Ratio();
        double h11h1 = m.getH11h1Ratio();
        double h13h1 = m.getH13h1Ratio();
        double pf    = Math.abs(m.getPowerFactor());

        LoadType type = classifyInternal(thdI, thdV, cv, h5h1, h7h1, h11h1, h13h1, pf, cfg);
        m.setDetectedLoadType(type);
    }

    private LoadType classifyInternal(double thdI, double thdV, double cv,
                                      double h5h1, double h7h1,
                                      double h11h1, double h13h1,
                                      double pf, FeederConfig cfg) {

        double cvThresh   = cfg.getMaxCvElectronicThreshold();  // 0.05
        double thdThresh  = cfg.getMinThdICryptoThreshold();    // 15.0 %
        double h5Thresh   = cfg.getMinH5h1CryptoThreshold();    // 0.15
        double h7Thresh   = cfg.getMinH7h1CryptoThreshold();    // 0.10

        // ── Distorsión de tensión elevada con corriente limpia ──────────────────
        // THDv alto pero THDi bajo y sin firma espectral notable → la distorsión
        // no se origina en este alimentador; proviene de aguas arriba (otra rama,
        // otro feeder, o la propia subestación).  Referencia: IEC 61000-3-6 §5.
        if (thdV > 5.0 && thdI < 8.0 && h5h1 < 0.08) {
            return LoadType.UPSTREAM_DISTORTION;
        }

        // ── Carga lineal ────────────────────────────────────────────────────────
        if (thdI < 5.0 && h5h1 < 0.05) {
            return LoadType.LINEAR;
        }

        // ── Iluminación (lámparas LED masivas) ──────────────────────────────────
        // H3 dominante, H5/H1 < 10%, factor de potencia medio
        if (thdI > 10.0 && h5h1 < 0.08 && pf > 0.75 && pf < 0.95) {
            return LoadType.LIGHTING;
        }

        // ── Firma cripto/datacenter ─────────────────────────────────────────────
        // CV muy bajo (consumo estable) + THDi alto + H5 y H7 dominantes.
        // THDv elevado (>2%) confirma que la distorsión se propaga a la tensión
        // y afecta la red.  THDv bajo indica red rígida (Scc alta): la carga
        // existe igual, pero la red absorbe la distorsión sin elevar la tensión.
        boolean stableLoad = cv < cvThresh;
        boolean highTHD    = thdI > thdThresh;
        boolean h5Dominant = h5h1 > h5Thresh;
        boolean h7Present  = h7h1 > h7Thresh;
        boolean highPF     = pf > 0.92;

        if (stableLoad && highTHD && h5Dominant && h7Present && highPF) {
            return LoadType.CRYPTO_MINING;
        }

        if (stableLoad && highTHD && h5Dominant && h7Present) {
            return LoadType.DATA_CENTER;
        }

        // ── Rectificador industrial 6-pulsos ────────────────────────────────────
        // H5 y H7 dominantes + H11 y H13 significativos + CV variable
        boolean sixPulseSignature = h5h1 > 0.12 && h7h1 > 0.08 && h11h1 > 0.05 && h13h1 > 0.04;
        if (thdI > 8.0 && sixPulseSignature) {
            return LoadType.INDUSTRIAL;
        }

        // ── Carga electrónica ligera ────────────────────────────────────────────
        if (thdI > 8.0 && (h5h1 > 0.08 || h7h1 > 0.05)) {
            return LoadType.ELECTRONIC_LIGHT;
        }

        // ── Mixta electrónica ───────────────────────────────────────────────────
        if (thdI > 5.0) {
            return LoadType.MIXED_ELECTRONIC;
        }

        return LoadType.LINEAR;
    }

    /**
     * Calcula un "índice de electrónica" de 0-100 para visualización en gauge.
     * Combina CV, THDi, THDv y ratios de armónicos en un score único.
     *
     * Pesos (suma máx = 100):
     *   25 pts — CV inverso:    carga estable → electrónica densa
     *   35 pts — THDi:          corriente distorsionada → no lineal
     *   25 pts — Ratios H5+H7:  firma espectral característica
     *   15 pts — THDv:          distorsión propagada a la tensión (confirma impacto en red;
     *                           bajo en redes rígidas sin penalizar la detección)
     */
    public double calculateElectronicIndex(FeederMeasurement m, FeederConfig cfg) {
        double cvThresh = cfg.getMaxCvElectronicThreshold();

        // Componente 1: inversa del CV normalizado → max 25 pts
        double cvComponent = 0.0;
        if (m.getCvCurrent() < cvThresh * 5) {
            cvComponent = Math.max(0, (cvThresh * 5 - m.getCvCurrent()) / (cvThresh * 5)) * 25;
        }

        // Componente 2: THDi normalizado a 40% → max 35 pts
        double thdComponent = Math.min(m.getThdCurrentAvg() / 40.0, 1.0) * 35;

        // Componente 3: ratios H5+H7 (suma normalizada a 0.5) → max 25 pts
        double ratioComponent = Math.min(
            (m.getH5h1Ratio() + m.getH7h1Ratio()) / 0.5, 1.0) * 25;

        // Componente 4: THDv normalizado al límite EN 50160 (8%) → max 15 pts
        // Confirma que la distorsión se propaga a la tensión.
        // En redes rígidas (Scc alta) este componente es bajo aunque la carga sea real,
        // por lo que NO cancela la detección — solo añade certeza cuando está presente.
        double thdvComponent = Math.min(m.getThdVoltageAvg() / 8.0, 1.0) * 15;

        return Math.min(cvComponent + thdComponent + ratioComponent + thdvComponent, 100.0);
    }
}
