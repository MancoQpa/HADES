package com.harmonicmonitor.comtrade;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Genera el archivo de reporte de texto (.txt) que acompaña a cada registro COMTRADE.
 *
 * Contiene también las constantes de límites normativos compartidas con
 * {@link ComtradeTriggerEngine}:
 * <ul>
 *   <li>IEC 61000-3-6 Tabla 1 — planning levels de tensión para 1kV–36kV</li>
 *   <li>IEEE 519-2022 Tabla 2 — límites de armónicos de corriente individuales</li>
 * </ul>
 */
final class ComtradeReportWriter {

    private ComtradeReportWriter() {}

    // ── Límites normativos (accesibles desde ComtradeTriggerEngine) ───────────

    /** Órdenes armónicos de tensión monitorizados (IEC 61000-3-6 Tabla 1, 1kV–36kV) */
    static final int[]    V_HARM_ORDERS = { 3,   5,   7,   9,   11,  13,  15,  17,  19,  21,  23,  25  };
    /** Planning levels correspondientes a V_HARM_ORDERS (%) */
    static final double[] V_HARM_LIMITS = { 4.0, 5.0, 4.0, 1.2, 3.0, 2.5, 0.3, 1.6, 1.2, 0.2, 1.2, 0.8 };

    /**
     * Límite de armónico individual de corriente IEEE 519-2022 Tabla 2
     * (% de IL, para Isc/IL = 20–50, 1kV–69kV).
     */
    static double ieee519CurrentLimit(int h) {
        if (h < 11)  return 4.0;
        if (h <= 16) return 2.0;
        if (h <= 22) return 1.5;
        if (h <= 34) return 0.6;
        return 0.3;
    }

    // ── Generación del reporte ────────────────────────────────────────────────

    /**
     * Escribe el archivo de reporte .txt junto al archivo CFG de COMTRADE.
     *
     * @param m        Medición en el instante del evento
     * @param cfg      Configuración del feeder
     * @param cause    Código de causa del disparo (ej. "THDv_CRIT")
     * @param reason   Descripción legible del evento
     * @param level    Nivel de severidad
     * @param cfgFile  Archivo .cfg COMTRADE ya generado (define el nombre base)
     * @return         Archivo de reporte generado
     */
    static File write(FeederMeasurement m, FeederConfig cfg,
                      String cause, String reason,
                      ComtradeTriggerEngine.TriggerLevel level,
                      File cfgFile) throws IOException {

        String base = cfgFile.getName().replaceAll("(?i)\\.cfg$", "");
        File rpt    = new File(cfgFile.getParentFile(), base + "_report.txt");

        DateTimeFormatter ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        LocalDateTime dt     = LocalDateTime.ofInstant(m.getTimestamp(), ZoneId.systemDefault());

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(rpt), StandardCharsets.UTF_8))) {

            pw.println("==========================================================");
            pw.println("  REPORTE DE EVENTO — HADES v1.0");
            pw.println("==========================================================");
            pw.println("Fecha/Hora    : " + dt.format(ts));
            pw.println("Feeder ID     : " + m.getFeederId());
            pw.println("Feeder Nombre : " + cfg.getFeederName());
            pw.println("Nivel         : " + level);
            pw.println("Causa         : " + cause);
            pw.println("Descripción   : " + reason.replace("\n", "\n               "));
            pw.println("Archivo COMTRADE: " + cfgFile.getName());
            pw.println();

            pw.println("── MEDICIÓN EN EL INSTANTE DEL EVENTO ──────────────────");
            pw.printf("  Tensión      L1 / L2 / L3  : %9.2f / %9.2f / %9.2f  V%n",
                m.getVoltageL1(), m.getVoltageL2(), m.getVoltageL3());
            pw.printf("  Corriente    L1 / L2 / L3  : %9.3f / %9.3f / %9.3f  A%n",
                m.getCurrentL1(), m.getCurrentL2(), m.getCurrentL3());
            pw.printf("  Potencia act / react / apa : %9.1f / %9.1f / %9.1f  kW/kVAR/kVA%n",
                m.getActivePower(), m.getReactivePower(), m.getApparentPower());
            pw.printf("  Factor de potencia         : %9.4f%n", m.getPowerFactor());
            pw.printf("  Frecuencia                 : %9.3f  Hz%n", m.getFrequency());
            pw.printf("  THD_V L1/L2/L3             : %6.2f%% / %6.2f%% / %6.2f%%%n",
                m.getThdVoltageL1(), m.getThdVoltageL2(), m.getThdVoltageL3());
            pw.printf("  THD_I L1/L2/L3             : %6.2f%% / %6.2f%% / %6.2f%%%n",
                m.getThdCurrentL1(), m.getThdCurrentL2(), m.getThdCurrentL3());
            pw.printf("  Carga detectada            : %s%n", m.getDetectedLoadType().getDisplayName());
            pw.printf("  K-Factor                   : %6.2f%n", m.getKFactorL1());
            pw.println();

            double[] spec = m.getHarmonicCurrentL1();
            if (spec != null && spec.length > 1 && spec[0] > 1e-6) {
                pw.println("── ESPECTRO ARMÓNICO CORRIENTE L1 (% del fundamental) ──");
                double h1 = spec[0];
                pw.printf("  H1  = %8.3f A  (referencia, 100%%)%n", h1);
                for (int h = 1; h < Math.min(spec.length, 25); h++) {
                    if (spec[h] > 1e-6) {
                        double pct = 100.0 * spec[h] / h1;
                        double lim = ieee519CurrentLimit(h + 1);
                        String flag = (pct > lim) ? "  ← SUPERA IEEE 519" : "";
                        pw.printf("  H%-2d = %8.3f A  (%5.1f%%)  [límite %.1f%%]%s%n",
                            h + 1, spec[h], pct, lim, flag);
                    }
                }
                pw.println();
            }

            double[] vspec = m.getHarmonicVoltageL1();
            if (vspec != null && vspec.length > 1 && vspec[0] > 1.0) {
                pw.println("── ESPECTRO ARMÓNICO TENSIÓN L1 (% del fundamental) ────");
                double v1 = vspec[0];
                pw.printf("  H1  = %9.2f V  (referencia, 100%%)%n", v1);
                for (int k = 0; k < V_HARM_ORDERS.length; k++) {
                    int h = V_HARM_ORDERS[k];
                    if (h - 1 < vspec.length && vspec[h - 1] > 0.1) {
                        double pct = 100.0 * vspec[h - 1] / v1;
                        double lim = V_HARM_LIMITS[k];
                        String flag = (pct > lim) ? "  ← SUPERA IEC 61000-3-6" : "";
                        pw.printf("  H%-2d = %9.2f V  (%5.2f%%)  [planning level %.1f%%]%s%n",
                            h, vspec[h - 1], pct, lim, flag);
                    }
                }
                pw.println();
            }

            pw.println("── NORMAS DE REFERENCIA ─────────────────────────────────");
            pw.println("  IEC 61000-3-6:2008   Planning levels armónicos de tensión, red MT (1kV-36kV)");
            pw.println("  EN 50160:2010        Características de tensión en redes públicas");
            pw.println("  IEEE 519-2022        Límites de armónicos de corriente (1kV-69kV)");
            pw.println("  IEC 61000-4-30:2015  Métodos de medición de calidad de energía (Clase A)");
            pw.println("  IEEE C37.111-1999    Formato COMTRADE para registros de perturbaciones");
            pw.println("==========================================================");
        }

        return rpt;
    }
}
