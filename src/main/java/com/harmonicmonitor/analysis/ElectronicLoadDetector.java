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
 *       Umbral 15% para SMPS/cripto sin PFC: estimación bibliográfica genérica,
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
 *
 *   <li><b>Flatness = (H5+H7)/(H11+H13)</b> — Forma espectral del espectro armónico.
 *       &gt; 2.0: espectro frontal (SMPS/cripto — armónicos bajos dominan).
 *       1.3–2.0: 6-pulsos clásico equilibrado (drives industriales, UPS).
 *       &lt; 1.2: armónicos altos dominan → rectificador 12-pulsos (VFDs de alta pot.).
 *       Ref: Chapman, "Electric Machinery Fundamentals", cap. rectificadores
 *       de 12 pulsos; Mohan "Power Electronics" §3.3.</li>
 *
 *   <li><b>PF, Q/S, K-Factor, H5/H7</b> — Vector de detección CRYPTO_MINING_PFC.
 *       Los ASIC miners modernos (Antminer S19/S21, Whatsminer M30/M50) incorporan
 *       PFC boost converter activo (modo CCM) que suprime la distorsión de corriente
 *       hasta THD ≈ 3–5% y lleva FP ≈ 1.000. Esto hace que el clasificador
 *       espectral clásico los confunda con carga lineal.
 *       El vector multidimensional {PF, Q/S, K, H5/H7} los discrimina:
 *         - PF &gt; 0.998: PFC activo suprime reactiva (Mohan, "PE" §16).
 *         - Q/S &lt; 0.012: ángulo φ &lt; 0.69°, imposible en carga industrial real.
 *         - K ∈ [1.0,1.12]: calculado como K = 1 + Σ(n²·Iₙ²)/Σ(Iₙ²) (IEEE C57.110).
 *         - H5/H7 &gt; 8: PFC suprime H7 más que H5 por la topología boost.
 *       Validado en campo: ION7400-0d5885 (10.200.142.125),
 *       feeder exclusivo criptominería ASIC, 23 kV/50 Hz, 26/05/2026.</li>
 * </ol>
 *
 * <h3>Orden de evaluación (importante — primero los más restrictivos)</h3>
 * <ol>
 *   <li>UPSTREAM_DISTORTION — distorsión de tensión sin firma en corriente</li>
 *   <li>CRYPTO_MINING_PFC   — PFC activo: evaluar ANTES que LINEAR para evitar
 *                             captura prematura por la regla THDi &lt; 5%</li>
 *   <li>LINEAR              — THDi &lt; 5% y H5 &lt; 5%</li>
 *   <li>LIGHTING            — H3 dominante, FP medio</li>
 *   <li>INDUSTRIAL          — 6-pulsos clásico (H5+H7+H11+H13 altos)</li>
 *   <li>CRYPTO_MINING       — 6-pulsos con THD &gt; 15% (sin PFC)</li>
 *   <li>DATA_CENTER         — similar pero FP &lt; 0.92</li>
 *   <li>INDUSTRIAL          — 12-pulsos (H11/H13 dominantes)</li>
 *   <li>ELECTRONIC_LIGHT    — electrónica ligera genérica</li>
 *   <li>MIXED_ELECTRONIC    — mezcla de firmas</li>
 * </ol>
 *
 * <h3>Referencias normativas</h3>
 * <ul>
 *   <li>IEEE Std 519-2022 — Recommended Practice for Harmonic Control</li>
 *   <li>IEC 61000-3-6:2008 — Planning levels for harmonic voltages in MV/HV</li>
 *   <li>IEC 61000-3-12:2011 — Limits for harmonic currents &gt;16 A/phase</li>
 *   <li>EN 50160:2010 — Voltage characteristics in public distribution networks</li>
 *   <li>IEEE Std 1459-2010 — Definitions for the Measurement of Electric Power</li>
 *   <li>IEEE C57.110-2018 — Transformer derating (K-Factor definition)</li>
 *   <li>Mohan, Undeland, Robbins — "Power Electronics", §16 (PFC converters)</li>
 * </ul>
 */
public class ElectronicLoadDetector {

    /**
     * Clasifica el tipo de carga y actualiza la medición con el resultado.
     * También calcula y persiste los indicadores derivados PFC
     * (h5h7Ratio, qsRatio, pfcCryptoScore) para uso en dashboard y BD.
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

        // Calcular y persistir indicadores derivados PFC antes de clasificar.
        // Esto permite visualizarlos en el dashboard incluso si la clase final
        // no es CRYPTO_MINING_PFC.
        double h5h7  = computeH5h7Ratio(h5h1, h7h1);
        double qsR   = computeQsRatio(m);
        double kAvg  = (m.getKFactorL1() + m.getKFactorL2() + m.getKFactorL3()) / 3.0;
        m.setH5h7Ratio(h5h7);
        m.setQsRatio(qsR);
        m.setPfcCryptoScore(calculatePfcScore(pf, qsR, kAvg, h5h7, cv, thdI, cfg));

        LoadType type = classifyInternal(thdI, thdV, cv, h5h1, h7h1, h11h1, h13h1,
                                         pf, h5h7, qsR, kAvg, cfg);
        m.setDetectedLoadType(type);
    }

    private LoadType classifyInternal(double thdI, double thdV, double cv,
                                      double h5h1,  double h7h1,
                                      double h11h1, double h13h1,
                                      double pf, double h5h7, double qsR,
                                      double kAvg, FeederConfig cfg) {

        double cvThresh  = cfg.getMaxCvElectronicThreshold();  // 0.05
        double thdThresh = cfg.getMinThdICryptoThreshold();    // 15.0 %
        double h5Thresh  = cfg.getMinH5h1CryptoThreshold();    // 0.15
        double h7Thresh  = cfg.getMinH7h1CryptoThreshold();    // 0.10

        // ── Flatness espectral: (H5+H7) / (H11+H13) ─────────────────────────────
        // Discrimina la forma del espectro armónico:
        //   > 2.0 : espectro frontal (SMPS/cripto/datacenter — H5+H7 dominan)
        //   1.3–2.0: 6-pulsos clásico equilibrado (drives industriales)
        //   < 1.2 : H11/H13 dominantes → rectificador 12-pulsos (VFDs industriales)
        // Guarda: si H11+H13 < 0.5% se asume espectro muy frontal (flatness = 10);
        //         si además H5+H7 < 2%, carga prácticamente lineal (flatness = 1).
        double flatness = computeFlatness(h5h1, h7h1, h11h1, h13h1);

        // ── [1] Distorsión de tensión elevada con corriente limpia ───────────────
        // THDv alto pero THDi bajo y sin firma espectral notable → la distorsión
        // no se origina en este alimentador; proviene de aguas arriba (otra rama,
        // otro feeder, o la propia subestación).  Ref: IEC 61000-3-6 §5.
        if (thdV > 5.0 && thdI < 8.0 && h5h1 < 0.08) {
            return LoadType.UPSTREAM_DISTORTION;
        }

        // ── [2] Criptominería ASIC con PFC activo (NUEVO — evaluar ANTES que LINEAR)
        //
        // PROBLEMA ORIGINAL: el clasificador capturaba los ASIC miners modernos
        // como LoadType.LINEAR porque su THD ≈ 3–4% pasa la guardia (thdI < 5%)
        // y su H5 ≈ 3.9% pasa (h5h1 < 0.05). El PFC activo hace que la firma
        // espectral de primer orden sea indistinguible de una carga resistiva.
        //
        // SOLUCIÓN: evaluar el vector multidimensional {PF, Q/S, K, H5/H7}
        // ANTES de la regla LINEAR, porque estos indicadores son ortogonales
        // al espectro armónico clásico.
        //
        // CONDICIONES (todas deben cumplirse — AND lógico):
        //
        //  A. thdI ∈ [thdPfcMin, thdPfcMax]:
        //     El PFC deja un residual de H5 (típico 2–5%). Por debajo de 1.5%
        //     la carga es linealmente pura (motor síncrono, resistencia).
        //     Por encima de 6.5% puede ser PFC de baja eficiencia o carga mixta.
        //
        //  B. pf > pfMin (0.998):
        //     El PFC boost en modo CCM eleva el FP a > 0.99 a plena carga.
        //     Ninguna carga industrial convencional alcanza FP > 0.998 sostenido.
        //     Motor a plena carga: FP ≈ 0.85–0.95. Con capacitores: hasta 0.97.
        //     Ref: Mohan et al., "Power Electronics" §16 (Active PFC).
        //
        //  C. qsR < qsMax (0.012):
        //     Q/S < 0.012 equivale a un ángulo de desfase φ < 0.69°.
        //     Un motor de 1.875 MVA a FP=0.90 tendría Q ≈ 0.81 MVAR → Q/S ≈ 0.43.
        //     Medido en campo: Q=0.006 MVAR, S=1.875 MVA → Q/S = 0.0032.
        //     Ref: IEEE Std 1459-2010 §3 (power definitions for nonsinusoidal).
        //
        //  D. kAvg ∈ [1.0, kMax] (1.0–1.12):
        //     K = 1 + Σ(n²·Iₙ²) / I₁²  (IEEE C57.110-2018, Ec. 1).
        //     Con THD=4% y H5=3.9%: K ≈ 1 + (25×0.039²) / 1² = 1.038.
        //     Medido: K = 1.05. Umbral 1.12 cubre THD hasta ~6%.
        //     Cualquier carga con H5>15% (rectificador sin PFC) tiene K > 1.5.
        //     Solo se aplica si el IED reportó K (kAvg > 0).
        //
        //  E. h5h7 > h5h7Min (8.0):
        //     El PFC boost suprime H7, H11, H13 más agresivamente que H5
        //     por la dinámica del lazo de control del boost (Mohan §16.3).
        //     Medido: H5=3.9%, H7=0.2% → H5/H7 = 19.5.
        //     6-pulsos clásico: H5=25%, H7=14% → H5/H7 ≈ 1.8.
        //     Umbral 8.0 separa PFC (≥8) de 6-pulsos clásico (≤3) con margen.
        //     NOTA: si H7=0 (no reportado), ratio = ∞ → se toma como 20.0 (PFC).
        //
        //  F. cv < cvThresh (0.05):
        //     Los ASIC miners operan 24/7 a carga constante. CV bajo confirma
        //     consumo estable, discriminando de cargas variables (hornos, motores
        //     con variaciones de carga). Ref: estimación ingeniería SMPS.
        {
            double pfMin  = cfg.getPfCryptoMinThreshold();        // 0.998
            double qsMax  = cfg.getQsRatioCryptoMaxThreshold();   // 0.012
            double kMax   = cfg.getKFactorCryptoMaxThreshold();   // 1.12
            double h5h7Min= cfg.getH5h7RatioCryptoMinThreshold(); // 8.0
            double thdMax = cfg.getThdCryptoPfcMaxThreshold();    // 6.5%
            double thdMin = cfg.getThdCryptoPfcMinThreshold();    // 1.5%

            boolean thdInRange  = thdI >= thdMin && thdI <= thdMax;    // cond. A
            boolean pfHigh      = pf >= pfMin;                          // cond. B
            boolean qsLow       = qsR <= qsMax;                        // cond. C
            boolean kOk         = kAvg <= 0.001                        // cond. D
                                  || (kAvg >= 1.0 && kAvg <= kMax);
            boolean h5h7High    = h5h7 >= h5h7Min;                     // cond. E
            boolean stableCV    = cv < cvThresh;                        // cond. F

            if (thdInRange && pfHigh && qsLow && kOk && h5h7High && stableCV) {
                return LoadType.CRYPTO_MINING_PFC;
            }

            // Versión relajada: si K no está disponible (IED no lo reporta),
            // 4 de 5 condiciones restantes son suficientes con alta confianza.
            if (kAvg <= 0.001) {
                int score = 0;
                if (thdInRange) score++;
                if (pfHigh)     score++;
                if (qsLow)      score++;
                if (h5h7High)   score++;
                if (stableCV)   score++;
                if (score >= 4) return LoadType.CRYPTO_MINING_PFC;
            }
        }

        // ── [3] Carga lineal ──────────────────────────────────────────────────────
        // NOTA: esta regla ya no captura ASIC miners con PFC porque fueron
        // interceptados en el bloque [2] anterior.
        if (thdI < 5.0 && h5h1 < 0.05) {
            return LoadType.LINEAR;
        }

        // ── [4] Iluminación (lámparas LED masivas) ────────────────────────────────
        // H3 dominante, H5/H1 < 10%, factor de potencia medio
        if (thdI > 10.0 && h5h1 < 0.08 && pf > 0.75 && pf < 0.95) {
            return LoadType.LIGHTING;
        }

        // ── [5] Rectificador industrial 6-pulsos ──────────────────────────────────
        // Un VFD de 6 pulsos puede tener CV bajo en ventana corta (velocidad casi
        // constante entre muestras) y PF alto (0.93+). La presencia de H11 y H13
        // significativos más flatness < 3.5 discrimina 6-pulsos (flatness ≈ 2.1–2.8)
        // de SMPS/cripto (flatness ≈ 4–8).
        // Ref: Mohan "Power Electronics" §3.3.
        boolean sixPulseSignature = h5h1 > 0.12 && h7h1 > 0.08
            && h11h1 > 0.05 && h13h1 > 0.04
            && flatness >= 1.3 && flatness < 3.5;
        if (thdI > 8.0 && sixPulseSignature) {
            return LoadType.INDUSTRIAL;
        }

        // ── [6] Firma cripto/datacenter SIN PFC (rectificador clásico) ───────────
        // CV muy bajo + THDi alto + H5 y H7 dominantes.
        // PF > 0.92 distingue CRYPTO_MINING (PFC parcial o sin PFC, older ASICs)
        // de DATA_CENTER (PFC parcial, servidores x86).
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

        // ── [7] Rectificador 12-pulsos (VFDs industriales de alta potencia) ──────
        // Dos rectificadores de 6 pulsos con 30° de desfase cancelan H5 y H7;
        // H11 y H13 emergen como dominantes (orden 12k±1). Flatness < 1.2.
        // Ref: Chapman "Electric Machinery Fundamentals", rectificadores 12-pulsos.
        boolean twelvePulseSignature = h11h1 > 0.07 && h13h1 > 0.06 && flatness < 1.2;
        if (thdI > 8.0 && twelvePulseSignature) {
            return LoadType.INDUSTRIAL;
        }

        // ── [8] Carga electrónica ligera ──────────────────────────────────────────
        if (thdI > 8.0 && (h5h1 > 0.08 || h7h1 > 0.05)) {
            return LoadType.ELECTRONIC_LIGHT;
        }

        // ── [9] Mixta electrónica ─────────────────────────────────────────────────
        if (thdI > 5.0) {
            return LoadType.MIXED_ELECTRONIC;
        }

        return LoadType.LINEAR;
    }

    /**
     * Calcula el ratio de forma espectral (H5+H7)/(H11+H13).
     * <ul>
     *   <li>&gt; 2.0 → espectro frontal (SMPS, cripto, datacenter)</li>
     *   <li>1.3–2.0 → 6-pulsos equilibrado (industrial)</li>
     *   <li>&lt; 1.2 → 12-pulsos (H11/H13 dominantes)</li>
     * </ul>
     * Guarda de división por cero: si H11+H13 &lt; 0.5% se devuelve 10.0
     * (espectro muy frontal) si H5+H7 es medible, o 1.0 (neutro) si todo es bajo.
     */
    private static double computeFlatness(double h5h1, double h7h1,
                                          double h11h1, double h13h1) {
        double denom = h11h1 + h13h1;
        if (denom > 0.005) return (h5h1 + h7h1) / denom;
        return (h5h1 + h7h1 > 0.02) ? 10.0 : 1.0;
    }

    /**
     * Calcula el ratio H5/H7 de corriente L1.
     *
     * <p>En un PFC boost activo, el lazo de control suprime H7 más agresivamente
     * que H5 porque el compensador de corriente tiene mayor ganancia en frecuencias
     * bajas. Esto produce un ratio H5/H7 >> 1 (típico: 10–25 para ASIC miners).
     * En un rectificador de 6 pulsos sin PFC el ratio es ≈ 1.8 (Mohan §3).
     *
     * <p>Guarda: si H7 < 0.001 (prácticamente nulo) se retorna 20.0 como
     * valor representativo de "PFC puro", evitando división por cero y
     * manteniendo la semántica del indicador (ratio muy alto → PFC activo).
     *
     * @param h5h1  relación H5/H1
     * @param h7h1  relación H7/H1
     * @return ratio H5/H7, o 20.0 si H7 es despreciable
     */
    static double computeH5h7Ratio(double h5h1, double h7h1) {
        if (h7h1 < 0.001) return (h5h1 > 0.005) ? 20.0 : 1.0;
        return h5h1 / h7h1;
    }

    /**
     * Calcula el ratio |Q|/S a partir de la medición.
     *
     * <p>El PFC activo inyecta corriente en fase con la tensión, anulando la
     * potencia reactiva. Para una carga de 1.875 MVA con FP=1.0000 se obtiene
     * Q ≈ 0 → Q/S ≈ 0. Un motor de igual potencia a FP=0.90 tendría Q/S ≈ 0.44.
     *
     * <p>Ref: IEEE Std 1459-2010 §3 — definiciones de potencia para formas de onda
     * no sinusoidales; la potencia reactiva se define como Q = √(S²−P²−D²) donde
     * D es la potencia de distorsión. El ION7400 reporta Q directamente via MMXU.
     *
     * @param m medición con potencia activa (kW), reactiva (kVAR) y aparente (kVA)
     * @return |Q|/S si S > 0, o 0.0 si no hay potencia
     */
    static double computeQsRatio(FeederMeasurement m) {
        double s = m.getApparentPower();
        if (s < 1e-6) return 0.0;
        return Math.abs(m.getReactivePower()) / s;
    }

    /**
     * Calcula el puntaje de firma PFC (0–100) para visualización en gauge.
     *
     * <p>Combina los 6 indicadores del vector multidimensional en un score
     * continuo que permite mostrar el "grado de certeza" de la detección
     * CRYPTO_MINING_PFC, incluso cuando la clase detectada es LINEAR.
     *
     * <h3>Pesos (suma máx = 100)</h3>
     * <ul>
     *   <li>30 pts — PF: indicador más fuerte. PF ≥ 0.998 → 30 pts lineales
     *               desde PF=0.95 hasta PF=1.000. Justificación: el PFC
     *               activo es la causa primaria de FP≈1 en cargas no lineales.</li>
     *   <li>25 pts — Q/S: potencia reactiva nula. Inversa de Q/S normalizada
     *               a 0.05 (5%). Justificación: Q/S=0 es imposible en motores
     *               o transformadores; confirma PFC de forma independiente al FP.</li>
     *   <li>20 pts — H5/H7: dominancia espectral del PFC. Normalizado a ratio 20.
     *               Justificación: el control del boost suprime H7 más que H5.</li>
     *   <li>15 pts — K-factor: proximidad a K=1.0. Penaliza K > 1.12 linealmente.
     *               Si K no disponible (kAvg=0), se asignan 7.5 pts (neutro).</li>
     *   <li>10 pts — CV inverso: carga estable 24/7. Normalizado a CV=0.25.</li>
     * </ul>
     *
     * @param pf     factor de potencia (valor absoluto)
     * @param qsR    ratio |Q|/S
     * @param kAvg   K-factor promedio trifásico (0 si no disponible)
     * @param h5h7   ratio H5/H7
     * @param cv     coeficiente de variación de corriente
     * @param thdI   THD de corriente promedio (%)
     * @param cfg    configuración del feeder (umbrales)
     * @return puntaje 0–100
     */
    public double calculatePfcScore(double pf, double qsR, double kAvg,
                                    double h5h7, double cv, double thdI,
                                    FeederConfig cfg) {
        // Componente 1 — PF (30 pts)
        // Lineal desde PF=0.95 (0 pts) hasta PF=1.000 (30 pts).
        double pfComponent = Math.min(Math.max((pf - 0.95) / 0.05, 0.0), 1.0) * 30.0;

        // Componente 2 — Q/S inversa (25 pts)
        // Q/S=0 → 25 pts; Q/S=0.05 → 0 pts. Lineal.
        double qsComponent = Math.min(Math.max(1.0 - qsR / 0.05, 0.0), 1.0) * 25.0;

        // Componente 3 — H5/H7 ratio (20 pts)
        // Ratio=0 → 0 pts; ratio=20 → 20 pts. Lineal (saturado en 20).
        double h5h7Component = Math.min(h5h7 / 20.0, 1.0) * 20.0;

        // Componente 4 — K-factor (15 pts)
        // K=1.00 → 15 pts; K=1.12 → 0 pts. Lineal.
        // Si K no disponible (kAvg ≤ 0), asignar 7.5 pts (incertidumbre).
        double kComponent;
        if (kAvg <= 0.001) {
            kComponent = 7.5;
        } else {
            kComponent = Math.min(Math.max(1.0 - (kAvg - 1.0) / 0.12, 0.0), 1.0) * 15.0;
        }

        // Componente 5 — CV inverso (10 pts)
        // CV=0 → 10 pts; CV=0.25 → 0 pts. Lineal.
        double cvComponent = Math.min(Math.max(1.0 - cv / 0.25, 0.0), 1.0) * 10.0;

        return Math.min(pfComponent + qsComponent + h5h7Component
                        + kComponent + cvComponent, 100.0);
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
