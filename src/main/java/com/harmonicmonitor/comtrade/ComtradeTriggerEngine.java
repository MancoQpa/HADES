package com.harmonicmonitor.comtrade;

import com.harmonicmonitor.model.AlarmEvent;
import com.harmonicmonitor.model.AlarmEvent.Level;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Motor de disparo automático de registros COMTRADE.
 *
 * Triggers implementados por normativa:
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  TENSIÓN  —  IEC 61000-3-6 (red MT 1kV–36kV) / EN 50160               │
 * │  THD_V > 5.0%  → WARNING     (preventivo, ~80% del planning level)     │
 * │  THD_V > 6.5%  → PQ_RISK     (supera planning level IEC 61000-3-6)     │
 * │  THD_V > 8.0%  → CRITICAL    (supera límite absoluto EN 50160)         │
 * │  Armónico Vn > planning level individual (tabla IEC 61000-3-6 Tabla 1) │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  CORRIENTE  —  IEEE 519-2022 (1kV–69kV, Isc/IL = 20–50)                │
 * │  THD_I > 5.0%  → WARNING     (preventivo)                              │
 * │  THD_I > 8.0%  → PQ_RISK     (≈ TDD limit, IEEE 519 Tabla 2)           │
 * │  THD_I > 12.0% → CRITICAL    (1.5× TDD limit)                          │
 * │  Armónico In > límite individual IEEE 519 (% de IL por orden)           │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  DESBALANCE  —  EN 50160 / IEC 61000-4-30                               │
 * │  Vunbal > 2.0% → PQ_RISK     (supera límite EN 50160)                  │
 * │  Vunbal > 3.0% → CRITICAL                                              │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  FACTOR DE POTENCIA  —  recomendación distribuidora                     │
 * │  FP < 0.85 → WARNING                                                   │
 * │  FP < 0.75 → PQ_RISK                                                   │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  DETECCIÓN DE CARGA  —  motor propio                                    │
 * │  Cambio a CRYPTO_MINING / DATA_CENTER / ARC_FURNACE → DETECTION        │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  ALARMA CRÍTICA  —  AlarmEngine                                         │
 * │  Alarma CRITICAL o DETECTION del AlarmEngine → force-record             │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  MANUAL  —  usuario                                                     │
 * │  triggerManual() → graba siempre, sin cooldown                          │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Cooldown: 30 s mínimo entre registros del mismo tipo para un mismo feeder.
 */
public class ComtradeTriggerEngine {

    private static final Logger LOG = Logger.getLogger(ComtradeTriggerEngine.class.getName());

    // ── Límites normativos ────────────────────────────────────────────────────

    // IEC 61000-3-6 Tabla 1 — planning levels de tensión para 1kV–36kV
    private static final int[]    V_HARM_ORDERS = { 3,   5,   7,   9,   11,  13,  15,  17,  19,  21,  23,  25  };
    private static final double[] V_HARM_LIMITS = { 4.0, 5.0, 4.0, 1.2, 3.0, 2.5, 0.3, 1.6, 1.2, 0.2, 1.2, 0.8 };

    // THD tensión
    private static final double THD_V_WARN     = 5.0;   // 80% del planning level
    private static final double THD_V_PQ_RISK  = 6.5;   // planning level IEC 61000-3-6 MV
    private static final double THD_V_CRITICAL = 8.0;   // límite absoluto EN 50160

    // THD corriente (IEEE 519-2022, Isc/IL 20-50, 1kV-69kV)
    private static final double THD_I_WARN     = 5.0;
    private static final double THD_I_PQ_RISK  = 8.0;   // TDD limit IEEE 519 Tabla 2
    private static final double THD_I_CRITICAL = 12.0;  // 1.5× TDD limit

    // Factor de potencia
    private static final double PF_WARN    = 0.85;
    private static final double PF_PQ_RISK = 0.75;

    // Desbalance de tensión (EN 50160)
    private static final double UNBAL_PQ_RISK  = 2.0;
    private static final double UNBAL_CRITICAL = 3.0;

    // Cooldown entre registros del mismo tipo por feeder
    private static final long COOLDOWN_MS = 30_000L;

    // ── Estado ───────────────────────────────────────────────────────────────

    private final File recordsDir;
    private final Map<String, Long>     lastTriggerTime = new HashMap<>();
    private final Map<String, LoadType> lastLoadType    = new HashMap<>();
    private final List<RecordEntry>     records         = new CopyOnWriteArrayList<>();
    private final List<RecordSavedListener> listeners   = new CopyOnWriteArrayList<>();

    /** Cache: feederId → directorio de salida ya resuelto */
    private final Map<String, File> feederDirCache = new HashMap<>();
    /** Contador para feeders sin guión → CARPETA/SUBCARPETA, CARPETA1/SUBCARPETA1, … */
    private int nonHyphenIndex = 0;

    public ComtradeTriggerEngine(File recordsDir) {
        this.recordsDir = recordsDir;
        recordsDir.mkdirs();
    }

    /**
     * Resuelve el subdirectorio de salida para un feeder según su ID:
     * <ul>
     *   <li>"BPA-5"    → records/BPA/BPA-5/</li>
     *   <li>"cbo2-AL1" → records/cbo2/cbo2-AL1/</li>
     *   <li>Sin guión  → records/CARPETA/SUBCARPETA/  (primer feeder sin guión)
     *                     records/CARPETA1/SUBCARPETA1/ (segundo), etc.</li>
     * </ul>
     */
    private synchronized File resolveFeederDir(String feederId) {
        return feederDirCache.computeIfAbsent(feederId, id -> {
            String safe = ComtradeWriter.sanitize(id);
            int dash = safe.indexOf('-');
            File dir;
            if (dash > 0) {
                String prefix = safe.substring(0, dash);
                dir = new File(new File(recordsDir, prefix), safe);
            } else {
                String suffix = (nonHyphenIndex == 0) ? "" : String.valueOf(nonHyphenIndex);
                nonHyphenIndex++;
                dir = new File(new File(recordsDir, "CARPETA" + suffix), "SUBCARPETA" + suffix);
            }
            dir.mkdirs();
            return dir;
        });
    }

    /** Devuelve el directorio de salida del feeder (lo crea si no existe). */
    public File getFeederDir(String feederId) {
        return resolveFeederDir(feederId);
    }

    /**
     * Pre-crea el directorio del feeder en el momento en que se conecta,
     * sin esperar al primer registro. Llamar desde HarmonicMonitorApp al agregar un feeder.
     */
    public void prepareFeederDir(String feederId) {
        resolveFeederDir(feederId);   // mkdirs() se invoca dentro
        LOG.info("[ComtradeTrigger] Carpeta preparada para feeder: " + feederId
                 + " → " + feederDirCache.get(feederId));
    }

    public void addListener(RecordSavedListener l)    { listeners.add(l); }
    public void removeListener(RecordSavedListener l) { listeners.remove(l); }

    /** Devuelve copia de la lista de registros guardados (más reciente primero) */
    public List<RecordEntry> getRecords() { return new ArrayList<>(records); }

    // ── Evaluación de medición ────────────────────────────────────────────────

    /**
     * Evalúa una nueva medición y dispara registros según normas.
     * Llamar desde el hilo de polling; el write ocurre en el mismo hilo.
     */
    public void evaluate(FeederMeasurement m, FeederConfig cfg) {
        checkThdVoltage(m, cfg);
        checkThdCurrent(m, cfg);
        checkIndividualVoltageHarmonics(m);
        checkIndividualCurrentHarmonics(m);
        checkPowerFactor(m, cfg);
        checkVoltageUnbalance(m);
        checkLoadDetection(m, cfg);
    }

    /** Disparo forzado por alarma crítica/detección del AlarmEngine */
    public void triggerAlarm(FeederMeasurement m, FeederConfig cfg, AlarmEvent alarm) {
        if (m == null || cfg == null) return;
        if (alarm.getLevel() != Level.CRITICAL && alarm.getLevel() != Level.DETECTION) return;
        String cause  = "ALARM_" + ComtradeWriter.sanitize(alarm.getParameter());
        String reason = String.format("[%s] %s", alarm.getLevel(), alarm.getMessage());
        triggerRecord(m, cfg, cause, reason, TriggerLevel.CRITICAL, false);
    }

    /** Disparo manual sin cooldown */
    public void triggerManual(FeederMeasurement m, FeederConfig cfg) {
        if (m == null || cfg == null) return;
        triggerRecord(m, cfg, "MANUAL", "Disparo manual desde interfaz de usuario",
                      TriggerLevel.INFO, true /* skip cooldown */);
    }

    /** Disparo periódico automático (cada 1 minuto).
     *  Usa cooldown para evitar duplicados si el feeder aparece más de una vez en la lista. */
    public void triggerScheduled(FeederMeasurement m, FeederConfig cfg) {
        if (m == null || cfg == null) return;
        triggerRecord(m, cfg, "PERIODICO_1MIN",
                      "Registro periódico automático obligatorio (cada 1 minuto)",
                      TriggerLevel.INFO, false /* respetar cooldown: evita duplicados */);
    }

    // ── Verificaciones normativas ─────────────────────────────────────────────

    private void checkThdVoltage(FeederMeasurement m, FeederConfig cfg) {
        double thd = m.getThdVoltageAvg();
        if (thd <= 0) return;
        if (thd > THD_V_CRITICAL) {
            triggerRecord(m, cfg, "THDv_CRIT",
                String.format("THD de tensión CRÍTICO: %.1f%% > %.1f%% (límite absoluto EN 50160, red MT)",
                    thd, THD_V_CRITICAL),
                TriggerLevel.CRITICAL, false);
        } else if (thd > THD_V_PQ_RISK) {
            triggerRecord(m, cfg, "THDv_PQ",
                String.format("THD de tensión supera planning level: %.1f%% > %.1f%% (IEC 61000-3-6 Tabla 1, 1kV-36kV)",
                    thd, THD_V_PQ_RISK),
                TriggerLevel.PQ_RISK, false);
        } else if (thd > THD_V_WARN) {
            triggerRecord(m, cfg, "THDv_WARN",
                String.format("THD de tensión elevado: %.1f%% (acercándose al planning level IEC %.1f%%)",
                    thd, THD_V_PQ_RISK),
                TriggerLevel.WARNING, false);
        }
    }

    private void checkThdCurrent(FeederMeasurement m, FeederConfig cfg) {
        double thd = m.getThdCurrentAvg();
        if (thd <= 0) return;
        if (thd > THD_I_CRITICAL) {
            triggerRecord(m, cfg, "THDi_CRIT",
                String.format("THD de corriente CRÍTICO: %.1f%% > %.1f%% (1.5× TDD limit IEEE 519-2022)",
                    thd, THD_I_CRITICAL),
                TriggerLevel.CRITICAL, false);
        } else if (thd > THD_I_PQ_RISK) {
            triggerRecord(m, cfg, "THDi_PQ",
                String.format("THD de corriente fuera de límite: %.1f%% > TDD=%.1f%% (IEEE 519-2022, Isc/IL=20-50, 1kV-69kV)",
                    thd, THD_I_PQ_RISK),
                TriggerLevel.PQ_RISK, false);
        } else if (thd > THD_I_WARN) {
            triggerRecord(m, cfg, "THDi_WARN",
                String.format("THD de corriente elevado: %.1f%% (advertencia preventiva, límite IEEE 519: %.1f%%)",
                    thd, THD_I_PQ_RISK),
                TriggerLevel.WARNING, false);
        }
    }

    private void checkIndividualVoltageHarmonics(FeederMeasurement m) {
        double[] spec = m.getHarmonicVoltageL1();
        if (spec == null || spec.length == 0 || spec[0] < 1.0) return;
        double v1 = spec[0];

        for (int k = 0; k < V_HARM_ORDERS.length; k++) {
            int    h     = V_HARM_ORDERS[k];
            double limit = V_HARM_LIMITS[k];
            if (h - 1 >= spec.length) continue;
            double pct = 100.0 * spec[h - 1] / v1;
            if (pct > limit) {
                // Un solo disparo por evaluación (el armónico más violado)
                triggerRecord(m, findCfgStub(m), "Vh" + h + "_PQ",
                    String.format("Armónico de tensión H%d: %.1f%% > planning level %.1f%% (IEC 61000-3-6 Tabla 1)",
                        h, pct, limit),
                    TriggerLevel.PQ_RISK, false);
                return;
            }
        }
    }

    private void checkIndividualCurrentHarmonics(FeederMeasurement m) {
        double[] spec = m.getHarmonicCurrentL1();
        if (spec == null || spec.length == 0 || spec[0] < 0.1) return;
        double il = spec[0];   // H1 ≈ corriente de demanda fundamental

        for (int h = 3; h <= Math.min(25, spec.length); h++) {
            double limit = ieee519CurrentLimit(h);
            double pct   = 100.0 * spec[h - 1] / il;
            if (pct > limit) {
                triggerRecord(m, findCfgStub(m), "Ih" + h + "_PQ",
                    String.format("Armónico de corriente H%d: %.1f%% de IL > %.1f%% (IEEE 519-2022 Tabla 2)",
                        h, pct, limit),
                    TriggerLevel.PQ_RISK, false);
                return;
            }
        }
    }

    private void checkPowerFactor(FeederMeasurement m, FeederConfig cfg) {
        double pf = Math.abs(m.getPowerFactor());
        if (pf < 0.005) return;   // sin carga
        if (pf < PF_PQ_RISK) {
            triggerRecord(m, cfg, "FP_PQ",
                String.format("Factor de potencia muy bajo: %.3f < %.2f (recomendado mínimo 0.85)", pf, PF_PQ_RISK),
                TriggerLevel.PQ_RISK, false);
        } else if (pf < PF_WARN) {
            triggerRecord(m, cfg, "FP_WARN",
                String.format("Factor de potencia bajo: %.3f (advertencia, mínimo recomendado 0.85)", pf),
                TriggerLevel.WARNING, false);
        }
    }

    private void checkVoltageUnbalance(FeederMeasurement m) {
        double avg = m.getVoltageAvg();
        if (avg < 1.0) return;
        double dev = Math.max(
            Math.max(Math.abs(m.getVoltageL1() - avg), Math.abs(m.getVoltageL2() - avg)),
            Math.abs(m.getVoltageL3() - avg));
        double pct = 100.0 * dev / avg;

        if (pct > UNBAL_CRITICAL) {
            triggerRecord(m, findCfgStub(m), "UNBAL_CRIT",
                String.format("Desbalance de tensión CRÍTICO: %.1f%% > %.1f%% (EN 50160 / IEC 61000-4-30)",
                    pct, UNBAL_CRITICAL),
                TriggerLevel.CRITICAL, false);
        } else if (pct > UNBAL_PQ_RISK) {
            triggerRecord(m, findCfgStub(m), "UNBAL_PQ",
                String.format("Desbalance de tensión: %.1f%% > %.1f%% (límite EN 50160)",
                    pct, UNBAL_PQ_RISK),
                TriggerLevel.PQ_RISK, false);
        }
    }

    private void checkLoadDetection(FeederMeasurement m, FeederConfig cfg) {
        LoadType cur  = m.getDetectedLoadType();
        LoadType prev = lastLoadType.getOrDefault(m.getFeederId(), LoadType.UNKNOWN);

        if (cur == prev) return;
        lastLoadType.put(m.getFeederId(), cur);

        if (cur == LoadType.CRYPTO_MINING || cur == LoadType.DATA_CENTER
                || cur == LoadType.INDUSTRIAL) {
            String cause = "DETECT_" + cur.name();
            String reason = String.format(
                "DETECCIÓN: %s identificado\n  THDi=%.1f%%  H5/H1=%.1f%%  H7/H1=%.1f%%  CV=%.3f  FP=%.3f",
                cur.getDisplayName(),
                m.getThdCurrentAvg(),
                m.getH5h1Ratio()  * 100,
                m.getH7h1Ratio()  * 100,
                m.getCvCurrent(), m.getPowerFactor());
            triggerRecord(m, cfg, cause, reason, TriggerLevel.DETECTION, false);
        }
    }

    // ── Escritura del registro ────────────────────────────────────────────────

    private void triggerRecord(FeederMeasurement m, FeederConfig cfg,
                                String cause, String reason,
                                TriggerLevel level, boolean skipCooldown) {
        if (cfg == null) return;
        String key = m.getFeederId() + "_" + cause;
        long   now = System.currentTimeMillis();

        if (!skipCooldown) {
            Long last = lastTriggerTime.get(key);
            if (last != null && (now - last) < COOLDOWN_MS) return;
        }
        lastTriggerTime.put(key, now);

        try {
            File feederDir = resolveFeederDir(m.getFeederId());
            File cfgFile = ComtradeWriter.write(m, cfg, cause, feederDir);
            File rptFile = writeReport(m, cfg, cause, reason, level, cfgFile);

            RecordEntry entry = new RecordEntry(
                m.getFeederId(), cause, reason, level, m.getTimestamp(), cfgFile, rptFile);
            records.add(0, entry);
            if (records.size() > 500) records.remove(records.size() - 1);

            for (RecordSavedListener l : listeners) {
                try { l.onRecordSaved(entry); } catch (Exception ignored) {}
            }
            LOG.info(String.format("[COMTRADE] %s  causa=%s  nivel=%s  archivo=%s",
                m.getFeederId(), cause, level, cfgFile.getName()));

        } catch (Exception ex) {
            LOG.warning("[COMTRADE] Error al escribir registro: " + ex.getMessage());
        }
    }

    // ── Reporte de texto ─────────────────────────────────────────────────────

    private File writeReport(FeederMeasurement m, FeederConfig cfg,
                              String cause, String reason, TriggerLevel level,
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

            // Espectro armónico de corriente L1
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

            // Espectro armónico de tensión L1
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

    // ── Utilidades ────────────────────────────────────────────────────────────

    /**
     * Límite de armónico individual de corriente IEEE 519-2022 Tabla 2
     * (% de IL, para Isc/IL = 20–50, 1kV–69kV)
     */
    private static double ieee519CurrentLimit(int h) {
        if (h < 11)  return 4.0;
        if (h <= 16) return 2.0;
        if (h <= 22) return 1.5;
        if (h <= 34) return 0.6;
        return 0.3;
    }

    /** Stub de FeederConfig mínimo para métodos que solo necesitan el ID */
    private FeederConfig findCfgStub(FeederMeasurement m) {
        FeederConfig stub = new FeederConfig(m.getFeederId(), "0.0.0.0");
        stub.setFeederName(m.getFeederId());
        stub.setNominalVoltageKv(m.getVoltageAvg() > 1000 ? m.getVoltageAvg() / 1000.0 : 23.0);
        stub.setNominalCurrentA(Math.max(m.getCurrentAvg() * 1.5, 10.0));
        return stub;
    }

    // ── Tipos públicos ────────────────────────────────────────────────────────

    public enum TriggerLevel {
        INFO, WARNING, PQ_RISK, CRITICAL, DETECTION;

        public String displayName() {
            switch (this) {
                case INFO:      return "INFO";
                case WARNING:   return "WARNING";
                case PQ_RISK:   return "PQ-RIESGO";
                case CRITICAL:  return "CRÍTICO";
                case DETECTION: return "DETECCIÓN";
                default:        return name();
            }
        }
    }

    /** Entrada de un registro guardado */
    public static class RecordEntry {
        public final String       feederId;
        public final String       cause;
        public final String       reason;
        public final TriggerLevel level;
        public final Instant      timestamp;
        public final File         cfgFile;
        public final File         reportFile;

        public RecordEntry(String feederId, String cause, String reason, TriggerLevel level,
                           Instant timestamp, File cfgFile, File reportFile) {
            this.feederId   = feederId;
            this.cause      = cause;
            this.reason     = reason;
            this.level      = level;
            this.timestamp  = timestamp;
            this.cfgFile    = cfgFile;
            this.reportFile = reportFile;
        }

        public String getTimestampStr() {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")
                .format(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()));
        }
    }

    /** Callback al guardar un nuevo registro */
    public interface RecordSavedListener {
        void onRecordSaved(RecordEntry entry);
    }
}
