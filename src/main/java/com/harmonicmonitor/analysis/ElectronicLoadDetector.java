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
 *   <li><b>CV = \u03C3(I)/\u03BC(I)</b> — Coeficiente de variaci\u00F3n de corriente.
 *       Umbral CV &lt; 5%: estimaci\u00F3n de ingenier\u00EDa para SMPS regulado.
 *       <em>Sin respaldo de est\u00E1ndar formal.</em></li>
 *
 *   <li><b>THDi</b> — THD de corriente.
 *       Umbral 5%: alineado con IEEE 519-2022 (TDD, tabla 2).
 *       Umbral 15% para SMPS/cripto: estimaci\u00F3n bibliogr\u00E1fica gen\u00E9rica,
 *       no validada con campa\u00F1a de campo en MT 23 kV.</li>
 *
 *   <li><b>THDv</b> — THD de tensi\u00F3n.
 *       Umbral 5% (UPSTREAM): IEC 61000-3-6:2008 \u00A75, nivel de planif. MT.
 *       Umbral 8% (EN 50160): EN 50160:2010 \u00A74.3.4, percentil 95% MT.</li>
 *
 *   <li><b>H5/H1, H7/H1</b> — Ratios de arm\u00F3nicos de rectificador 6-pulsos.
 *       Valores te\u00F3ricos: H5 = 1/5 = 20%, H7 = 1/7 \u2248 14.3% (Fourier ideal).
 *       Fuente: teor\u00EDa est\u00E1ndar de convertidores (Mohan, Undeland, Robbins,
 *       "Power Electronics", \u00A73; Chapman, "Electric Machinery Fundamentals").
 *       Umbrales conservadores (15%, 10%) para tolerar filtrado parcial.</li>
 *
 *   <li><b>H11/H1, H13/H1</b> — Firma extendida 6-pulsos.
 *       Valores te\u00F3ricos: H11 = 1/11 \u2248 9.1%, H13 = 1/13 \u2248 7.7%.</li>
 *
 *   <li><b>FP</b> — Factor de potencia (|cos \u03C6|).
 *       Umbral 0.92 para distinguir CRYPTO vs DATA_CENTER:
 *       <em>criterio emp\u00EDrico sin validaci\u00F3n formal.</em></li>
 *
 *   <li><b>Flatness = (H5+H7)/(H11+H13)</b> — Forma espectral del espectro arm\u00F3nico.
 *       &gt; 2.0: espectro frontal (SMPS/cripto — arm\u00F3nicos bajos dominan).
 *       1.3\u20132.0: 6-pulsos cl\u00E1sico equilibrado (drives industriales, UPS).
 *       &lt; 1.2: arm\u00F3nicos altos dominan \u2192 rectificador 12-pulsos (VFDs de alta pot.).
 *       Ref: Chapman, "Electric Machinery Fundamentals", cap. rectificadores
 *       de 12 pulsos; Mohan "Power Electronics" \u00A73.3.</li>
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

        // ── Flatness espectral: (H5+H7) / (H11+H13) ────────────────────────────
        // Discrimina la forma del espectro arm\u00F3nico:
        //   > 2.0 : espectro frontal (SMPS 6-pulsos, cripto, datacenter)
        //   1.3\u20132.0: 6-pulsos cl\u00E1sico equilibrado (drives industriales)
        //   < 1.2 : H11/H13 dominantes \u2192 rectificador 12-pulsos (VFDs industriales)
        // Guarda: si H11+H13 < 0.5% se asume espectro muy frontal (flatness = 10);
        //         si adem\u00E1s H5+H7 < 2%, carga pr\u00E1cticamente lineal (flatness = 1).
        double flatness = computeFlatness(h5h1, h7h1, h11h1, h13h1);

        // ── Distorsi\u00F3n de tensi\u00F3n elevada con corriente limpia ──────────────────
        // THDv alto pero THDi bajo y sin firma espectral notable → la distorsi\u00F3n
        // no se origina en este alimentador; proviene de aguas arriba (otra rama,
        // otro feeder, o la propia subestaci\u00F3n).  Referencia: IEC 61000-3-6 \u00A75.
        if (thdV > 5.0 && thdI < 8.0 && h5h1 < 0.08) {
            return LoadType.UPSTREAM_DISTORTION;
        }

        // ── Carga lineal ────────────────────────────────────────────────────────
        if (thdI < 5.0 && h5h1 < 0.05) {
            return LoadType.LINEAR;
        }

        // ── Iluminaci\u00F3n (l\u00E1mparas LED masivas) ──────────────────────────────────
        // H3 dominante, H5/H1 < 10%, factor de potencia medio
        if (thdI > 10.0 && h5h1 < 0.08 && pf > 0.75 && pf < 0.95) {
            return LoadType.LIGHTING;
        }

        // ── Rectificador industrial 6-pulsos (verificar ANTES que cripto/datacenter) ──
        // Un VFD de 6 pulsos puede tener CV bajo en ventana corta (velocidad casi
        // constante entre muestras) y PF alto (0.93+), satisfaciendo inadvertidamente
        // todas las condiciones cripto. La presencia de H11 y H13 significativos
        // más flatness < 3.5 discrimina 6-pulsos (flatness ≈ 2.1–2.8) de SMPS/cripto
        // (flatness ≈ 4–8). El límite < 3.5 deja margen entre VFD máx (≈2.8) y
        // cripto mín (≈4.7) sin solapamiento en los perfiles de referencia.
        boolean sixPulseSignature = h5h1 > 0.12 && h7h1 > 0.08
            && h11h1 > 0.05 && h13h1 > 0.04
            && flatness >= 1.3 && flatness < 3.5;
        if (thdI > 8.0 && sixPulseSignature) {
            return LoadType.INDUSTRIAL;
        }

        // ── Firma cripto/datacenter ─────────────────────────────────────────────
        // CV muy bajo (consumo estable) + THDi alto + H5 y H7 dominantes.
        // PF > 0.92 distingue CRYPTO_MINING (PFC activo) de DATA_CENTER (PFC parcial).
        // THDv elevado confirma que la distorsión se propaga a la tensión de red.
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

        // ── Rectificador 12-pulsos (VFDs industriales de alta potencia) ─────────
        // En el puente de 12 pulsos, dos rectificadores de 6 pulsos operan con
        // 30° de desfase. Los armónicos H5 y H7 se cancelan mutuamente; H11 y H13
        // emergen como los dominantes (orden 12k±1). La flatness se invierte (<1.2).
        // Este check puede ir después de cripto: en 12-pulsos H5 es cancelado
        // (<7%), h5Dominant=false, por lo que el check cripto nunca se dispara.
        // Ref: Chapman "Electric Machinery Fundamentals", rectificadores 12-pulsos.
        boolean twelvePulseSignature = h11h1 > 0.07 && h13h1 > 0.06 && flatness < 1.2;
        if (thdI > 8.0 && twelvePulseSignature) {
            return LoadType.INDUSTRIAL;
        }

        // ── Carga electr\u00F3nica ligera ────────────────────────────────────────────
        if (thdI > 8.0 && (h5h1 > 0.08 || h7h1 > 0.05)) {
            return LoadType.ELECTRONIC_LIGHT;
        }

        // ── Mixta electr\u00F3nica ───────────────────────────────────────────────────
        if (thdI > 5.0) {
            return LoadType.MIXED_ELECTRONIC;
        }

        return LoadType.LINEAR;
    }

    /**
     * Calcula el ratio de forma espectral (H5+H7)/(H11+H13).
     * <ul>
     *   <li>&gt; 2.0 \u2192 espectro frontal (SMPS, cripto, datacenter)</li>
     *   <li>1.3\u20132.0 \u2192 6-pulsos equilibrado (industrial)</li>
     *   <li>&lt; 1.2 \u2192 12-pulsos (H11/H13 dominantes)</li>
     * </ul>
     * Guarda de divisi\u00F3n por cero: si H11+H13 &lt; 0.5% se devuelve 10.0
     * (espectro muy frontal) si H5+H7 es medible, o 1.0 (neutro) si todo es bajo.
     */
    private static double computeFlatness(double h5h1, double h7h1,
                                          double h11h1, double h13h1) {
        double denom = h11h1 + h13h1;
        if (denom > 0.005) return (h5h1 + h7h1) / denom;
        return (h5h1 + h7h1 > 0.02) ? 10.0 : 1.0;
    }

    /**
     * Calcula un "\u00EDndice de electr\u00F3nica" de 0-100 para visualizaci\u00F3n en gauge.
     * Combina CV, THDi, THDv, ratios de arm\u00F3nicos y flatness espectral.
     *
     * Pesos (suma m\u00E1x = 100):
     *   25 pts \u2014 CV inverso:       carga estable \u2192 electr\u00F3nica densa
     *   35 pts \u2014 THDi:             corriente distorsionada \u2192 no lineal
     *   20 pts \u2014 Ratios H5+H7:     firma espectral de arm\u00F3nicos bajos
     *    5 pts \u2014 Flatness bonus:   espectro frontal (H5+H7 >> H11+H13) confirma SMPS
     *   15 pts \u2014 THDv:             distorsi\u00F3n propagada a la tensi\u00F3n (confirma impacto
     *                             en red; bajo en redes r\u00EDgidas sin penalizar)
     */
    public double calculateElectronicIndex(FeederMeasurement m, FeederConfig cfg) {
        double cvThresh = cfg.getMaxCvElectronicThreshold();

        // Componente 1: inversa del CV normalizado \u2192 max 25 pts
        double cvComponent = 0.0;
        if (m.getCvCurrent() < cvThresh * 5) {
            cvComponent = Math.max(0, (cvThresh * 5 - m.getCvCurrent()) / (cvThresh * 5)) * 25;
        }

        // Componente 2: THDi normalizado a 40% \u2192 max 35 pts
        double thdComponent = Math.min(m.getThdCurrentAvg() / 40.0, 1.0) * 35;

        // Componente 3a: ratios H5+H7 (suma normalizada a 0.5) \u2192 max 20 pts
        double ratioComponent = Math.min(
            (m.getH5h1Ratio() + m.getH7h1Ratio()) / 0.5, 1.0) * 20;

        // Componente 3b: bonus de flatness \u2192 max 5 pts
        // Espectro frontal (flatness > 1) indica que H5+H7 dominan sobre H11+H13,
        // firma t\u00EDpica de SMPS.  Se normaliza al rango flatness 1\u20136 (linealmente).
        double flatness = computeFlatness(
            m.getH5h1Ratio(), m.getH7h1Ratio(),
            m.getH11h1Ratio(), m.getH13h1Ratio());
        double flatnessBonus = Math.min(Math.max(flatness - 1.0, 0.0) / 5.0, 1.0) * 5;

        // Componente 4: THDv normalizado al l\u00EDmite EN 50160 (8%) \u2192 max 15 pts
        double thdvComponent = Math.min(m.getThdVoltageAvg() / 8.0, 1.0) * 15;

        return Math.min(cvComponent + thdComponent + ratioComponent
                        + flatnessBonus + thdvComponent, 100.0);
    }
}
