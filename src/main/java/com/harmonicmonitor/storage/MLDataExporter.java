package com.harmonicmonitor.storage;

import com.harmonicmonitor.model.FeederMeasurement;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

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
            MlXlsxWriter.write(csv, xlsx, m.getFeederId());
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
}
