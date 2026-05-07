package com.harmonicmonitor.storage;

import com.harmonicmonitor.model.FeederMeasurement;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exportador incremental de datos para modelado ML.
 *
 * Por cada feeder genera un único archivo CSV que crece en cada ciclo de 1 minuto:
 *   records/BPA/BPA-15/BPA-15_dataset.csv
 *
 * Columnas exportadas (65 features + label):
 *
 * IDENTIFICACIÓN:
 *   timestamp, feeder_id, spectrum_estimated, quality_flag
 *
 * FUNDAMENTALES:
 *   V_L1_V, V_L2_V, V_L3_V, V_avg_V
 *   I_L1_A, I_L2_A, I_L3_A, I_avg_A
 *   P_kW, Q_kVAR, S_kVA, PF, freq_Hz
 *
 * THD:
 *   THD_V_L1, THD_V_L2, THD_V_L3, THD_V_avg
 *   THD_I_L1, THD_I_L2, THD_I_L3, THD_I_avg
 *   THD_I_odd_L1, THD_I_even_L1
 *
 * RATIOS ARMÓNICOS CLAVE (features discriminativas):
 *   H5_H1_I_pct, H7_H1_I_pct, H11_H1_I_pct, H13_H1_I_pct
 *   CV_I, K_factor_L1
 *
 * ESPECTRO CORRIENTE L1 (% del fundamental, H2..H25):
 *   H2_I..H25_I  (24 columnas)
 *
 * ESPECTRO TENSIÓN L1 (% del fundamental, órdenes clave):
 *   H3_V, H5_V, H7_V, H9_V, H11_V, H13_V
 *
 * COMPONENTES SIMÉTRICAS Y DESBALANCE:
 *   I_pos_A, I_neg_A, I_zero_A, V_pos_V, V_neg_V
 *   V_unbal_pct, I_unbal_pct
 *
 * RESONANCIA:
 *   res_freq_Hz, res_order
 *
 * ETIQUETA (target para clasificación supervisada):
 *   label  (UNKNOWN / NORMAL_LOAD / ELECTRONIC_LIGHT / CRYPTO_MINING /
 *            DATA_CENTER / INDUSTRIAL / ARC_FURNACE / MIXED)
 */
public class MLDataExporter {

    private static final Logger LOG = Logger.getLogger(MLDataExporter.class.getName());

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Header ────────────────────────────────────────────────────────────────

    private static final String HEADER =
        "timestamp,feeder_id,spectrum_estimated,quality_flag," +
        // Fundamentales (voltajes en V)
        "V_L1_V,V_L2_V,V_L3_V,V_avg_V," +
        "I_L1_A,I_L2_A,I_L3_A,I_avg_A," +
        "P_kW,Q_kVAR,S_kVA,PF,freq_Hz," +
        // THD
        "THD_V_L1_pct,THD_V_L2_pct,THD_V_L3_pct,THD_V_avg_pct," +
        "THD_I_L1_pct,THD_I_L2_pct,THD_I_L3_pct,THD_I_avg_pct," +
        "THD_I_odd_L1_pct,THD_I_even_L1_pct," +
        // Ratios clave
        "H5_H1_I_pct,H7_H1_I_pct,H11_H1_I_pct,H13_H1_I_pct," +
        "CV_I,K_factor_L1," +
        // Espectro corriente L1 H2..H25
        "H2_I_pct,H3_I_pct,H4_I_pct,H5_I_pct,H6_I_pct,H7_I_pct," +
        "H8_I_pct,H9_I_pct,H10_I_pct,H11_I_pct,H12_I_pct,H13_I_pct," +
        "H14_I_pct,H15_I_pct,H16_I_pct,H17_I_pct,H18_I_pct,H19_I_pct," +
        "H20_I_pct,H21_I_pct,H22_I_pct,H23_I_pct,H24_I_pct,H25_I_pct," +
        // Espectro tensión L1 (órdenes clave)
        "H3_V_pct,H5_V_pct,H7_V_pct,H9_V_pct,H11_V_pct,H13_V_pct," +
        // Componentes simétricas y desbalance (voltajes en V)
        "I_pos_A,I_neg_A,I_zero_A,V_pos_V,V_neg_V," +
        "V_unbal_pct,I_unbal_pct," +
        // Resonancia
        "res_freq_Hz,res_order," +
        // Etiqueta
        "label";

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Agrega una fila al dataset CSV del feeder.
     * Si el archivo no existe, lo crea con encabezado.
     * Diseñado para ser llamado una vez cada 1 minuto desde el periodic trigger.
     *
     * @param m         medición a registrar
     * @param feederDir directorio del feeder (ej. records/BPA/BPA-15/)
     */
    public synchronized void appendRow(FeederMeasurement m, File feederDir) {
        if (m == null || feederDir == null) return;

        feederDir.mkdirs();
        File csv  = new File(feederDir, m.getFeederId() + "_dataset.csv");
        File xlsx = new File(feederDir, m.getFeederId() + "_dataset.xlsx");

        boolean writeHeader = !csv.exists() || csv.length() == 0;

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(csv, true /* append */), StandardCharsets.UTF_8))) {

            if (writeHeader) pw.println(HEADER);
            pw.println(buildRow(m));

        } catch (IOException ex) {
            LOG.warning("[MLExporter] Error al escribir dataset CSV de " + m.getFeederId()
                        + ": " + ex.getMessage());
            return;
        }

        // Regenerar xlsx completo desde el CSV actualizado
        try {
            writeXlsx(csv, xlsx, m.getFeederId());
        } catch (IOException ex) {
            LOG.warning("[MLExporter] Error al escribir dataset XLSX de " + m.getFeederId()
                        + ": " + ex.getMessage());
        }
    }

    // ── Construcción de la fila ───────────────────────────────────────────────

    private String buildRow(FeederMeasurement m) {
        StringBuilder sb = new StringBuilder(512);

        // ── Identificación ────────────────────────────────────────────────────
        LocalDateTime ldt = LocalDateTime.ofInstant(m.getTimestamp(), ZoneId.systemDefault());
        sb.append(ldt.format(TS_FMT)).append(',');
        sb.append(m.getFeederId()).append(',');
        sb.append(m.isSpectrumEstimated() ? 1 : 0).append(',');
        sb.append(m.getQualityFlag()).append(',');

        // ── Fundamentales ─────────────────────────────────────────────────────
        double vAvg = m.getVoltageAvg();
        double iAvg = m.getCurrentAvg();
        f(sb, m.getVoltageL1()); f(sb, m.getVoltageL2());
        f(sb, m.getVoltageL3()); f(sb, vAvg);
        f(sb, m.getCurrentL1()); f(sb, m.getCurrentL2()); f(sb, m.getCurrentL3()); f(sb, iAvg);
        f(sb, m.getActivePower());   f(sb, m.getReactivePower());
        f(sb, m.getApparentPower()); f(sb, m.getPowerFactor()); f(sb, m.getFrequency());

        // ── THD ───────────────────────────────────────────────────────────────
        f(sb, m.getThdVoltageL1()); f(sb, m.getThdVoltageL2());
        f(sb, m.getThdVoltageL3()); f(sb, m.getThdVoltageAvg());
        f(sb, m.getThdCurrentL1()); f(sb, m.getThdCurrentL2());
        f(sb, m.getThdCurrentL3()); f(sb, m.getThdCurrentAvg());
        f(sb, m.getThdOddCurrentL1()); f(sb, m.getThdEvenCurrentL1());

        // ── Ratios clave ──────────────────────────────────────────────────────
        f(sb, m.getH5h1Ratio()  * 100.0);
        f(sb, m.getH7h1Ratio()  * 100.0);
        f(sb, m.getH11h1Ratio() * 100.0);
        f(sb, m.getH13h1Ratio() * 100.0);
        f(sb, m.getCvCurrent());
        f(sb, m.getKFactorL1());

        // ── Espectro corriente L1, H2..H25 (% del fundamental) ───────────────
        double[] iSpec = m.getHarmonicCurrentL1();
        double   iH1   = (iSpec != null && iSpec.length > 0) ? iSpec[0] : 0.0;
        for (int h = 2; h <= 25; h++) {
            double pct = 0.0;
            if (iH1 > 1e-9 && iSpec != null && iSpec.length >= h) {
                pct = iSpec[h - 1] / iH1 * 100.0;
            }
            f(sb, pct);
        }

        // ── Espectro tensión L1 (órdenes 3,5,7,9,11,13 — % del fundamental) ──
        double[] vSpec = m.getHarmonicVoltageL1();
        double   vH1   = (vSpec != null && vSpec.length > 0) ? vSpec[0] : 0.0;
        int[]    vOrds = {3, 5, 7, 9, 11, 13};
        for (int h : vOrds) {
            double pct = 0.0;
            if (vH1 > 1.0 && vSpec != null && vSpec.length >= h) {
                pct = vSpec[h - 1] / vH1 * 100.0;
            }
            f(sb, pct);
        }

        // ── Componentes simétricas y desbalance ───────────────────────────────
        f(sb, m.getSeqCurrentPos());  f(sb, m.getSeqCurrentNeg());
        f(sb, m.getSeqCurrentZero()); f(sb, m.getSeqVoltagePos());
        f(sb, m.getSeqVoltageNeg());
        f(sb, m.getVoltageUnbalancePct());
        f(sb, m.getCurrentUnbalancePct());

        // ── Resonancia ────────────────────────────────────────────────────────
        f(sb, m.getResonanceFrequency());
        sb.append(m.getResonanceOrder()).append(',');

        // ── Etiqueta (sin coma al final) ──────────────────────────────────────
        sb.append(m.getDetectedLoadType().name());

        return sb.toString();
    }

    /** Agrega un double formateado con 4 decimales seguido de coma. */
    private void f(StringBuilder sb, double v) {
        sb.append(String.format(Locale.US, "%.4f", v)).append(',');
    }

    // ── Generador XLSX (pure Java, sin dependencias externas) ─────────────────
    //
    // Un archivo .xlsx es un ZIP que contiene XML según la especificación OOXML.
    // Hoja 1: datos del dataset (una fila por muestra).
    // Hoja 2: diccionario de variables con unidades, descripción y fórmulas.
    //
    // Estilos definidos (índice s= en cada celda):
    //   0 = normal
    //   1 = negrita (nombres de columna en hoja 2)
    //   2 = negrita blanca sobre azul oscuro (encabezados de sección)
    //   3 = negrita blanca sobre azul medio (fila de cabecera de tabla)
    //   4 = fondo azul muy claro (filas pares de la tabla)

    // ── Metadatos de variables: { nombre, unidad, descripción, fórmula/criterio } ──
    private static final String[][] COL_META = {
        // Identificación
        {"timestamp",          "—",     "Fecha y hora de la muestra (zona horaria local)",
         "Generado en cada ciclo de caracterización (1 min)"},
        {"feeder_id",          "—",     "Identificador único del alimentador",
         "Configurado en Gestión de Alimentadores"},
        {"spectrum_estimated", "0/1",   "Indica si el espectro fue estimado por SW (1) o medido en el IED (0)",
         "1 si el IED no dispone de MHAI; espectro calculado a partir de THDi y firma típica"},
        {"quality_flag",       "—",     "Calidad de la medición",
         "GOOD | COMM_ERROR | SIMULATED"},
        // Tensiones
        {"V_L1_V",  "V", "Tensión fase-neutro L1 (RMS)",
         "V_LN = V_LL / √3 = 23000 / 1.732 ≈ 13 280 V  (nominal)"},
        {"V_L2_V",  "V", "Tensión fase-neutro L2 (RMS)", "Ídem L1"},
        {"V_L3_V",  "V", "Tensión fase-neutro L3 (RMS)", "Ídem L1"},
        {"V_avg_V", "V", "Tensión fase-neutro promedio trifásico",
         "(V_L1 + V_L2 + V_L3) / 3"},
        // Corrientes
        {"I_L1_A",  "A", "Corriente de línea fase L1 (RMS)",
         "Medido por MMXU.A.phsA vía IEC 61850 MMS"},
        {"I_L2_A",  "A", "Corriente de línea fase L2 (RMS)", "Ídem L1"},
        {"I_L3_A",  "A", "Corriente de línea fase L3 (RMS)", "Ídem L1"},
        {"I_avg_A", "A", "Corriente de línea promedio trifásico",
         "(I_L1 + I_L2 + I_L3) / 3"},
        // Potencias
        {"P_kW",   "kW",   "Potencia activa total trifásica",
         "P = 3 × V_LN × I × cos(φ)   [MMXU.TotW]"},
        {"Q_kVAR", "kVAR", "Potencia reactiva total trifásica",
         "Q = 3 × V_LN × I × sin(φ)   [MMXU.TotVAr]"},
        {"S_kVA",  "kVA",  "Potencia aparente total trifásica",
         "S = √(P² + Q²)   [MMXU.TotVA]"},
        {"PF",     "—",    "Factor de potencia total (cos φ)",
         "PF = P / S   (rango 0..1)   [MMXU.TotPF]"},
        {"freq_Hz","Hz",   "Frecuencia de la red eléctrica",
         "Nominal 50 Hz   [MMXU.Hz]"},
        // THD tensión
        {"THD_V_L1_pct",  "%", "Distorsión armónica total de tensión fase L1",
         "THD_V = (√(∑Vn²) / V1) × 100   n = 2..50"},
        {"THD_V_L2_pct",  "%", "Distorsión armónica total de tensión fase L2", "Ídem L1"},
        {"THD_V_L3_pct",  "%", "Distorsión armónica total de tensión fase L3", "Ídem L1"},
        {"THD_V_avg_pct", "%", "THD de tensión promedio trifásico",
         "(THD_V_L1 + THD_V_L2 + THD_V_L3) / 3"},
        // THD corriente
        {"THD_I_L1_pct",      "%", "Distorsión armónica total de corriente fase L1",
         "THD_I = (√(∑In²) / I1) × 100   n = 2..50   [MHAI.ThdA.phsA]"},
        {"THD_I_L2_pct",      "%", "Distorsión armónica total de corriente fase L2", "Ídem L1"},
        {"THD_I_L3_pct",      "%", "Distorsión armónica total de corriente fase L3", "Ídem L1"},
        {"THD_I_avg_pct",     "%", "THD de corriente promedio trifásico",
         "(THD_I_L1 + THD_I_L2 + THD_I_L3) / 3"},
        {"THD_I_odd_L1_pct",  "%", "THD de armónicos impares de corriente L1",
         "√(∑ I(2k+1)²) / I1 × 100   Alto en cargas electrónicas (H3, H5, H7…)"},
        {"THD_I_even_L1_pct", "%", "THD de armónicos pares de corriente L1",
         "√(∑ I(2k)²) / I1 × 100   Alto indica asimetría (arco eléctrico, rectificador media onda)"},
        // Ratios clave
        {"H5_H1_I_pct",  "%", "Relación 5° armónico / fundamental de corriente L1",
         "(I5 / I1) × 100   Criterio cripto/DC: > 15%   IEEE 519 límite: 4%"},
        {"H7_H1_I_pct",  "%", "Relación 7° armónico / fundamental de corriente L1",
         "(I7 / I1) × 100   Criterio cripto/DC: > 10%"},
        {"H11_H1_I_pct", "%", "Relación 11° armónico / fundamental de corriente L1",
         "(I11 / I1) × 100   Criterio VFD 6-pulsos: > 5%"},
        {"H13_H1_I_pct", "%", "Relación 13° armónico / fundamental de corriente L1",
         "(I13 / I1) × 100   Criterio VFD 6-pulsos: > 4%"},
        {"CV_I",         "—", "Coeficiente de variación de corriente (ventana 60 muestras ≈ 5 min)",
         "CV = σ(I) / μ(I)   < 5%: carga estable (SMPS/PFC, datacenter)   > 5%: variable (industrial, motores)"},
        {"K_factor_L1",  "—", "K-Factor de corriente fase L1",
         "K = ∑(n² × (In/IRMS)²)   K > 4: transformador especial recomendado   [MHAI.HKf.phsA]"},
        // Espectro corriente H2..H25
        {"H2_I_pct",  "%", "Armónico orden 2 corriente L1 (% del fundamental)",
         "(I2 / I1) × 100   Bajo en condiciones normales"},
        {"H3_I_pct",  "%", "Armónico orden 3 corriente L1 (% del fundamental)",
         "(I3 / I1) × 100   Dominante en LED, UPS monofásicos   IEEE 519 límite: 4%"},
        {"H4_I_pct",  "%", "Armónico orden 4 corriente L1 (% del fundamental)",
         "(I4 / I1) × 100"},
        {"H5_I_pct",  "%", "Armónico orden 5 corriente L1 (% del fundamental)",
         "(I5 / I1) × 100   IEEE 519 límite: 4%   Firma de rectificadores SMPS"},
        {"H6_I_pct",  "%", "Armónico orden 6 corriente L1 (% del fundamental)",
         "(I6 / I1) × 100"},
        {"H7_I_pct",  "%", "Armónico orden 7 corriente L1 (% del fundamental)",
         "(I7 / I1) × 100   IEEE 519 límite: 4%   Presente junto con H5 en rectificadores"},
        {"H8_I_pct",  "%", "Armónico orden 8 corriente L1 (% del fundamental)",
         "(I8 / I1) × 100"},
        {"H9_I_pct",  "%", "Armónico orden 9 corriente L1 (% del fundamental)",
         "(I9 / I1) × 100   Triplen: circula por neutro"},
        {"H10_I_pct", "%", "Armónico orden 10 corriente L1 (% del fundamental)",
         "(I10 / I1) × 100"},
        {"H11_I_pct", "%", "Armónico orden 11 corriente L1 (% del fundamental)",
         "(I11 / I1) × 100   IEEE 519 límite: 2%   Presente en convertidores 12-pulsos"},
        {"H12_I_pct", "%", "Armónico orden 12 corriente L1 (% del fundamental)",
         "(I12 / I1) × 100"},
        {"H13_I_pct", "%", "Armónico orden 13 corriente L1 (% del fundamental)",
         "(I13 / I1) × 100   IEEE 519 límite: 2%"},
        {"H14_I_pct", "%", "Armónico orden 14 corriente L1 (% del fundamental)",
         "(I14 / I1) × 100"},
        {"H15_I_pct", "%", "Armónico orden 15 corriente L1 (% del fundamental)",
         "(I15 / I1) × 100   Triplen orden 5"},
        {"H16_I_pct", "%", "Armónico orden 16 corriente L1 (% del fundamental)",
         "(I16 / I1) × 100"},
        {"H17_I_pct", "%", "Armónico orden 17 corriente L1 (% del fundamental)",
         "(I17 / I1) × 100   IEEE 519 límite: 1.5%"},
        {"H18_I_pct", "%", "Armónico orden 18 corriente L1 (% del fundamental)",
         "(I18 / I1) × 100"},
        {"H19_I_pct", "%", "Armónico orden 19 corriente L1 (% del fundamental)",
         "(I19 / I1) × 100   IEEE 519 límite: 1.5%"},
        {"H20_I_pct", "%", "Armónico orden 20 corriente L1 (% del fundamental)",
         "(I20 / I1) × 100"},
        {"H21_I_pct", "%", "Armónico orden 21 corriente L1 (% del fundamental)",
         "(I21 / I1) × 100   Triplen orden 7"},
        {"H22_I_pct", "%", "Armónico orden 22 corriente L1 (% del fundamental)",
         "(I22 / I1) × 100"},
        {"H23_I_pct", "%", "Armónico orden 23 corriente L1 (% del fundamental)",
         "(I23 / I1) × 100   IEEE 519 límite: 0.6%"},
        {"H24_I_pct", "%", "Armónico orden 24 corriente L1 (% del fundamental)",
         "(I24 / I1) × 100"},
        {"H25_I_pct", "%", "Armónico orden 25 corriente L1 (% del fundamental)",
         "(I25 / I1) × 100   IEEE 519 límite: 0.6%"},
        // Espectro tensión
        {"H3_V_pct",  "%", "Armónico orden 3 de tensión L1 (% del fundamental)",
         "(V3 / V1) × 100   IEC 61000-3-6 nivel de planificación MT: 4%"},
        {"H5_V_pct",  "%", "Armónico orden 5 de tensión L1 (% del fundamental)",
         "(V5 / V1) × 100   IEC 61000-3-6 nivel de planificación MT: 3%"},
        {"H7_V_pct",  "%", "Armónico orden 7 de tensión L1 (% del fundamental)",
         "(V7 / V1) × 100   IEC 61000-3-6 nivel de planificación MT: 3%"},
        {"H9_V_pct",  "%", "Armónico orden 9 de tensión L1 (% del fundamental)",
         "(V9 / V1) × 100   IEC 61000-3-6 nivel de planificación MT: 1.5%"},
        {"H11_V_pct", "%", "Armónico orden 11 de tensión L1 (% del fundamental)",
         "(V11 / V1) × 100   IEC 61000-3-6 nivel de planificación MT: 2%"},
        {"H13_V_pct", "%", "Armónico orden 13 de tensión L1 (% del fundamental)",
         "(V13 / V1) × 100   IEC 61000-3-6 nivel de planificación MT: 2%"},
        // Componentes simétricas
        {"I_pos_A",     "A", "Corriente de secuencia positiva (componente útil)",
         "I+ = (1/3)(IL1 + a·IL2 + a²·IL3),  a = e^j120°   [MSQI.SeqA.c1]"},
        {"I_neg_A",     "A", "Corriente de secuencia negativa (indica desbalance)",
         "I- = (1/3)(IL1 + a²·IL2 + a·IL3)   Crea par frenante en motores   [MSQI.SeqA.c2]"},
        {"I_zero_A",    "A", "Corriente de secuencia cero (circula por neutro/tierra)",
         "I0 = (1/3)(IL1 + IL2 + IL3)   Causada por triplens H3, H9, H15…"},
        {"V_pos_V",     "V", "Tensión de secuencia positiva",
         "Componente trifásico balanceado   [MSQI.SeqV.c1]"},
        {"V_neg_V",     "V", "Tensión de secuencia negativa",
         "Indica desbalance de tensión en la red   [MSQI.SeqV.c2]"},
        {"V_unbal_pct", "%", "Desbalance de tensión trifásico",
         "(V_neg / V_pos) × 100   Límite EN 50160: 2%   Afecta rendimiento de motores"},
        {"I_unbal_pct", "%", "Desbalance de corriente trifásico",
         "(I_neg / I_pos) × 100   Alto indica carga monofásica dominante o asimetría"},
        // Resonancia
        {"res_freq_Hz", "Hz", "Frecuencia estimada de resonancia paralela red–capacitores",
         "f_res = f1 × √(Scc / Qc)   equivalente a   1 / (2π√(L·C))"},
        {"res_order",   "—",  "Orden armónico de resonancia  (h = f_res / f1)",
         "Si coincide con H5 o H7 activos → riesgo de amplificación de tensión"},
        // Etiqueta
        {"label", "—", "Clasificación de tipo de carga detectada por el algoritmo",
         "LINEAR: THDi<5%, H5<5% | ELECTRONIC_LIGHT: THDi>8%, H5>8% o H7>5% | " +
         "CRYPTO_MINING: CV<5%, THDi>15%, H5>15%, H7>10%, PF>0.92 | " +
         "DATA_CENTER: ídem cripto pero PF≤0.92 | " +
         "INDUSTRIAL: firma 6-pulsos (H5>12%, H7>8%, H11>5%, H13>4%) | " +
         "MIXED_ELECTRONIC: 5%<THDi<15%, sin firma específica | " +
         "LIGHTING: THDi>10%, H3 dominante"},
    };

    private static void writeXlsx(File csv, File xlsx, String feederId) throws IOException {
        List<String[]> rows = readCsvRows(csv);
        if (rows.isEmpty()) return;

        String sheet1 = feederId.length() > 31 ? feederId.substring(0, 31) : feederId;

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(xlsx)))) {

            // ── Infraestructura OOXML ──────────────────────────────────────────
            addEntry(zos, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>");

            addEntry(zos, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>");

            addEntry(zos, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>");

            addEntry(zos, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets>" +
                "<sheet name=\"" + escXml(sheet1) + "\" sheetId=\"1\" r:id=\"rId1\"/>" +
                "<sheet name=\"Diccionario de Variables\" sheetId=\"2\" r:id=\"rId2\"/>" +
                "</sheets></workbook>");

            // ── Estilos ────────────────────────────────────────────────────────
            // Font 0: normal | Font 1: negrita | Font 2: negrita blanca (para encabezados)
            // Fill 0: none | Fill 1: gray125 | Fill 2: azul oscuro | Fill 3: azul claro
            // xf 0: normal | xf 1: bold | xf 2: bold-blanco/azul-oscuro | xf 3: normal/azul-claro
            addEntry(zos, "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"3\">" +
                  "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                  "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                  "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
                "</fonts>" +
                "<fills count=\"4\">" +
                  "<fill><patternFill patternType=\"none\"/></fill>" +
                  "<fill><patternFill patternType=\"gray125\"/></fill>" +
                  "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1565C0\"/></patternFill></fill>" +
                  "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE3F2FD\"/></patternFill></fill>" +
                "</fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"4\">" +
                  "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                  "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                  "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"0\" xfId=\"0\"/>" +
                  "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\"/>" +
                "</cellXfs>" +
                "</styleSheet>");

            // ── Hoja 1: datos del dataset ──────────────────────────────────────
            addEntry(zos, "xl/worksheets/sheet1.xml", buildDataSheet(rows));

            // ── Hoja 2: diccionario de variables ──────────────────────────────
            addEntry(zos, "xl/worksheets/sheet2.xml", buildDictionarySheet());
        }
    }

    /** Construye el XML de la hoja 1 (datos del dataset). */
    private static String buildDataSheet(List<String[]> rows) {
        StringBuilder sd = new StringBuilder(2048 + rows.size() * 512);
        sd.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sd.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        // Anchos de columna: primeras 4 cols de texto más anchas
        sd.append("<cols>");
        sd.append("<col min=\"1\" max=\"1\" width=\"22\" customWidth=\"1\"/>");  // timestamp
        sd.append("<col min=\"2\" max=\"2\" width=\"14\" customWidth=\"1\"/>");  // feeder_id
        sd.append("<col min=\"3\" max=\"73\" width=\"14\" customWidth=\"1\"/>"); // numéricas
        sd.append("</cols>");
        sd.append("<sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            String[] cols = rows.get(r);
            // Fila de cabecera (r==0): estilo bold (s=1)
            int rowStyle = (r == 0) ? 1 : 0;
            sd.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < cols.length; c++) {
                String val = cols[c].trim();
                String ref = colRef(c) + (r + 1);
                Double num = tryParseDouble(val);
                if (num != null) {
                    sd.append("<c r=\"").append(ref).append("\" s=\"").append(rowStyle).append("\"><v>")
                      .append(val).append("</v></c>");
                } else {
                    sd.append("<c r=\"").append(ref).append("\" s=\"").append(rowStyle)
                      .append("\" t=\"inlineStr\"><is><t>").append(escXml(val)).append("</t></is></c>");
                }
            }
            sd.append("</row>");
        }
        sd.append("</sheetData></worksheet>");
        return sd.toString();
    }

    /** Construye el XML de la hoja 2 (diccionario de variables). */
    private static String buildDictionarySheet() {
        StringBuilder s = new StringBuilder(16384);
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        s.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");

        // Anchos de columna
        s.append("<cols>");
        s.append("<col min=\"1\" max=\"1\" width=\"26\" customWidth=\"1\"/>");  // A: Variable
        s.append("<col min=\"2\" max=\"2\" width=\"9\"  customWidth=\"1\"/>");  // B: Unidad
        s.append("<col min=\"3\" max=\"3\" width=\"55\" customWidth=\"1\"/>");  // C: Descripción
        s.append("<col min=\"4\" max=\"4\" width=\"70\" customWidth=\"1\"/>");  // D: Fórmula
        s.append("</cols>");

        s.append("<sheetData>");

        int row = 1;

        // Fila 1: título principal (s=2: bold blanco sobre azul)
        s.append("<row r=\"").append(row++).append("\">");
        txt(s, "A", row - 1, 2,
            "DICCIONARIO DE VARIABLES — HADES v1.0  |  Alimentador MT 23 kV  |  IEEE 519-2022 / IEC 61000-3-6");
        s.append("</row>");

        // Fila 2: sub-título con normas de referencia (s=2)
        s.append("<row r=\"").append(row++).append("\">");
        txt(s, "A", row - 1, 2,
            "Fuente: ION 7400 via IEC 61850 MMS (MMXU / MHAI / MSQI / MMTR / MSTA)  |  Normas: IEEE 519-2022, IEC 61000-3-6:2008, EN 50160:2010, IEC 61000-4-30:2015");
        s.append("</row>");

        // Fila 3: vacía
        s.append("<row r=\"").append(row++).append("\"></row>");

        // Fila 4: cabecera de tabla (s=2: bold blanco sobre azul)
        s.append("<row r=\"").append(row++).append("\">");
        txt(s, "A", row - 1, 2, "Variable");
        txt(s, "B", row - 1, 2, "Unidad");
        txt(s, "C", row - 1, 2, "Descripcion");
        txt(s, "D", row - 1, 2, "Formula / Criterio / Norma");
        s.append("</row>");

        // Filas de datos
        for (int i = 0; i < COL_META.length; i++) {
            String[] m = COL_META[i];
            // Filas pares (i par) → fondo azul claro (s=3); impares → normal (s=0)
            int bg = (i % 2 == 0) ? 3 : 0;
            s.append("<row r=\"").append(row++).append("\">");
            txt(s, "A", row - 1, 1,  m[0]);        // nombre: bold (s=1)
            txt(s, "B", row - 1, bg, m[1]);         // unidad
            txt(s, "C", row - 1, bg, m[2]);         // descripción
            txt(s, "D", row - 1, bg, m[3]);         // fórmula
            s.append("</row>");
        }

        // Fila final: nota al pie (s=0)
        s.append("<row r=\"").append(row++).append("\"></row>");
        s.append("<row r=\"").append(row).append("\">");
        txt(s, "A", row, 0,
            "Generado automaticamente por HADES v1.0  |  Emilio Medina");
        s.append("</row>");

        s.append("</sheetData></worksheet>");
        return s.toString();
    }

    /** Emite una celda de texto inline con estilo s= en la columna col de la fila rowNum. */
    private static void txt(StringBuilder sb, String col, int rowNum, int style, String value) {
        sb.append("<c r=\"").append(col).append(rowNum)
          .append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t>")
          .append(escXml(value)).append("</t></is></c>");
    }

    /** Lee todas las líneas del CSV como arrays de campos. */
    private static List<String[]> readCsvRows(File csv) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) rows.add(line.split(",", -1));
            }
        }
        return rows;
    }

    /** Agrega una entrada de texto UTF-8 al ZIP. */
    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /** Convierte índice de columna 0-based a letras Excel (A, B, …, Z, AA, …). */
    private static String colRef(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col + 1;
        while (c > 0) {
            c--;
            sb.insert(0, (char) ('A' + c % 26));
            c /= 26;
        }
        return sb.toString();
    }

    /** Intenta parsear como double; retorna null si es texto. */
    private static Double tryParseDouble(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return null; }
    }

    /** Escapa caracteres especiales XML. */
    private static String escXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
