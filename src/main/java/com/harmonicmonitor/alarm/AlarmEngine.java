package com.harmonicmonitor.alarm;

import com.harmonicmonitor.model.AlarmEvent;
import com.harmonicmonitor.model.AlarmEvent.Level;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Motor de alarmas de 4 niveles para el monitor de alimentadores MT.
 *
 * Niveles de alarma:
 *   1. WARNING     - Advertencia: valor se acerca al límite (80% del umbral)
 *   2. PQ_RISK     - Riesgo de calidad de energía: supera límite IEC/IEEE
 *   3. CRITICAL    - Condición crítica: amplificación de resonancia, desbalance severo
 *   4. DETECTION   - Detección de carga electrónica: cripto/datacenter identificado
 *
 * El motor evalúa cada medición nueva y emite eventos a los listeners registrados.
 * Implementa lógica de histeresis simple para evitar alarmas repetitivas.
 */
public class AlarmEngine {

    private final List<AlarmListener>  listeners   = new CopyOnWriteArrayList<>();
    private final List<AlarmEvent>     activeAlarms = new CopyOnWriteArrayList<>();
    private final List<AlarmEvent>     alarmHistory = new ArrayList<>();

    private static final int MAX_HISTORY = 1000;

    // Estado de supresión por histeresis
    private LoadType lastDetectedLoadType = LoadType.UNKNOWN;
    private boolean  resonanceAlarmActive = false;

    public void addListener(AlarmListener l)    { listeners.add(l); }
    public void removeListener(AlarmListener l) { listeners.remove(l); }

    public List<AlarmEvent> getActiveAlarms()  { return new ArrayList<>(activeAlarms); }
    public List<AlarmEvent> getAlarmHistory()  { return new ArrayList<>(alarmHistory); }

    /**
     * Evalúa una nueva medición y genera las alarmas correspondientes.
     */
    public void evaluate(FeederMeasurement m, FeederConfig cfg) {
        checkThdVoltage(m, cfg);
        checkThdCurrent(m, cfg);
        checkVoltageUnbalance(m, cfg);
        checkCurrentUnbalance(m, cfg);
        checkResonance(m, cfg);
        checkElectronicLoad(m, cfg);
        checkOvercurrent(m, cfg);
        checkLowPowerFactor(m, cfg);
    }

    public void acknowledgeAll() {
        for (AlarmEvent a : activeAlarms) a.acknowledge();
        activeAlarms.clear();
        fireActiveAlarmsChanged();
    }

    public void acknowledgeAlarm(AlarmEvent alarm) {
        alarm.acknowledge();
        activeAlarms.remove(alarm);
        fireActiveAlarmsChanged();
    }

    // ── Verificaciones individuales ────────────────────────────────────────────

    private void checkThdVoltage(FeederMeasurement m, FeederConfig cfg) {
        double max    = cfg.getMaxThdVoltagePct();
        double thdAvg = m.getThdVoltageAvg();

        if (thdAvg > max * 1.5) {
            fire(Level.CRITICAL, m.getFeederId(), "THDv",
                String.format("THD de tensión crítico: %.1f%% (límite IEC: %.1f%%)", thdAvg, max),
                thdAvg, max);
        } else if (thdAvg > max) {
            fire(Level.PQ_RISK, m.getFeederId(), "THDv",
                String.format("THD de tensión supera límite: %.1f%% (IEC 61000-3-6: %.1f%%)", thdAvg, max),
                thdAvg, max);
        } else if (thdAvg > max * 0.8) {
            fire(Level.WARNING, m.getFeederId(), "THDv",
                String.format("THD de tensión se acerca al límite: %.1f%% (máx %.1f%%)", thdAvg, max),
                thdAvg, max);
        }
    }

    private void checkThdCurrent(FeederMeasurement m, FeederConfig cfg) {
        double max    = cfg.getMaxThdCurrentPct();
        double thdAvg = m.getThdCurrentAvg();

        if (thdAvg > max * 1.5) {
            fire(Level.CRITICAL, m.getFeederId(), "THDi",
                String.format("THD de corriente crítico: %.1f%% (límite: %.1f%%)", thdAvg, max),
                thdAvg, max);
        } else if (thdAvg > max) {
            fire(Level.PQ_RISK, m.getFeederId(), "THDi",
                String.format("THD de corriente fuera de límite: %.1f%% (IEEE 519: %.1f%%)", thdAvg, max),
                thdAvg, max);
        } else if (thdAvg > max * 0.8) {
            fire(Level.WARNING, m.getFeederId(), "THDi",
                String.format("THD de corriente elevado: %.1f%%", thdAvg),
                thdAvg, max);
        }
    }

    private void checkVoltageUnbalance(FeederMeasurement m, FeederConfig cfg) {
        double max = cfg.getMaxVoltageUnbalPct();

        // Preferir el valor pre-calculado por MmsDataMapper (Fortescue: Vneg/Vpos×100,
        // más preciso cuando el IED provee MSQI con componentes simétricas).
        // Fallback: calcular por máxima desviación si el IED no proveyó ese valor.
        double unbal = m.getVoltageUnbalancePct();
        if (unbal < 1e-6) {
            double avg = m.getVoltageAvg();
            if (avg < 1e-6) return;
            double maxDev = Math.max(
                Math.max(Math.abs(m.getVoltageL1() - avg), Math.abs(m.getVoltageL2() - avg)),
                Math.abs(m.getVoltageL3() - avg));
            unbal = 100.0 * maxDev / avg;
        }

        if (unbal > max * 1.5) {
            fire(Level.CRITICAL, m.getFeederId(), "DesbalV",
                String.format("Desbalance de tensión crítico: %.1f%% (límite EN 50160: %.1f%%)", unbal, max),
                unbal, max);
        } else if (unbal > max) {
            fire(Level.PQ_RISK, m.getFeederId(), "DesbalV",
                String.format("Desbalance de tensión: %.1f%% supera %.1f%%", unbal, max),
                unbal, max);
        }
    }

    private void checkCurrentUnbalance(FeederMeasurement m, FeederConfig cfg) {
        double max = cfg.getMaxCurrentUnbalPct();
        double avg = m.getCurrentAvg();
        if (avg < 1e-6) return;

        double maxDev = Math.max(
            Math.max(Math.abs(m.getCurrentL1() - avg), Math.abs(m.getCurrentL2() - avg)),
            Math.abs(m.getCurrentL3() - avg));
        double unbal = 100.0 * maxDev / avg;

        if (unbal > max) {
            fire(Level.PQ_RISK, m.getFeederId(), "DesbalI",
                String.format("Desbalance de corriente: %.1f%% (máx %.1f%%)", unbal, max),
                unbal, max);
        }
    }

    private void checkResonance(FeederMeasurement m, FeederConfig cfg) {
        int hr = m.getResonanceOrder();
        if (hr <= 0) return;

        double[] spec = m.getHarmonicCurrentL1();
        if (spec == null || spec.length == 0 || spec[0] < 1e-6) return;

        double hResAmp = (hr <= spec.length) ? spec[hr - 1] : 0.0;
        double ratio   = hResAmp / spec[0];
        double maxRatio = cfg.getResonanceAmplificationMax();

        if (ratio > maxRatio) {
            if (!resonanceAlarmActive) {
                resonanceAlarmActive = true;
                fire(Level.CRITICAL, m.getFeederId(), "Resonancia",
                    String.format("Resonancia armónica activa en H%d (%.0f Hz). Amplificación: %.1f×",
                        hr, m.getResonanceFrequency(), ratio),
                    ratio, maxRatio);
            }
        } else if (ratio > maxRatio * 0.6 && hr >= 3 && hr <= 13) {
            fire(Level.PQ_RISK, m.getFeederId(), "Resonancia",
                String.format("Riesgo de resonancia: H%d cerca del orden estimado H%d. Amplitud H%d/H1=%.1f%%",
                    hr, hr, hr, ratio * 100),
                ratio, maxRatio);
            resonanceAlarmActive = false;
        } else {
            resonanceAlarmActive = false;
        }
    }

    private void checkElectronicLoad(FeederMeasurement m, FeederConfig cfg) {
        LoadType current = m.getDetectedLoadType();

        // Emitir alarma de detección solo cuando cambia el tipo detectado
        if (current != lastDetectedLoadType) {
            lastDetectedLoadType = current;

            if (current == LoadType.CRYPTO_MINING) {
                fire(Level.DETECTION, m.getFeederId(), "TipoCarga",
                    String.format("DETECCIÓN: Carga de Minería Cripto identificada. THDi=%.1f%%, CV=%.3f, H5/H1=%.1f%%, FP=%.2f",
                        m.getThdCurrentAvg(), m.getCvCurrent(), m.getH5h1Ratio() * 100, m.getPowerFactor()),
                    m.getThdCurrentAvg(), cfg.getMinThdICryptoThreshold());
            } else if (current == LoadType.DATA_CENTER) {
                fire(Level.DETECTION, m.getFeederId(), "TipoCarga",
                    String.format("DETECCIÓN: Centro de Datos identificado. THDi=%.1f%%, CV=%.3f, H5/H1=%.1f%%",
                        m.getThdCurrentAvg(), m.getCvCurrent(), m.getH5h1Ratio() * 100),
                    m.getThdCurrentAvg(), cfg.getMinThdICryptoThreshold());
            } else if (current == LoadType.INDUSTRIAL) {
                fire(Level.DETECTION, m.getFeederId(), "TipoCarga",
                    String.format("DETECCIÓN: Rectificador industrial identificado (6 o 12 pulsos). THDi=%.1f%%, H5/H1=%.1f%%, H7/H1=%.1f%%",
                        m.getThdCurrentAvg(), m.getH5h1Ratio() * 100, m.getH7h1Ratio() * 100),
                    m.getThdCurrentAvg(), cfg.getMinThdICryptoThreshold());
            } else if (current == LoadType.MIXED_ELECTRONIC || current == LoadType.ELECTRONIC_LIGHT) {
                fire(Level.WARNING, m.getFeederId(), "TipoCarga",
                    String.format("Carga electrónica detectada (%s). THDi=%.1f%%",
                        current.getDisplayName(), m.getThdCurrentAvg()),
                    m.getThdCurrentAvg(), cfg.getMinThdICryptoThreshold());
            }
        }
    }

    private void checkOvercurrent(FeederMeasurement m, FeederConfig cfg) {
        double in = cfg.getNominalCurrentA();
        if (in <= 0) return;
        double iMax = Math.max(Math.max(m.getCurrentL1(), m.getCurrentL2()), m.getCurrentL3());

        if (iMax > in * 1.2) {
            fire(Level.CRITICAL, m.getFeederId(), "Sobreintensidad",
                String.format("Sobreintensidad: I=%.1f A (In=%.1f A, %.0f%%)", iMax, in, iMax / in * 100),
                iMax, in);
        } else if (iMax > in * 1.05) {
            fire(Level.PQ_RISK, m.getFeederId(), "Sobreintensidad",
                String.format("Corriente supera nominal: %.1f A (In=%.1f A)", iMax, in),
                iMax, in);
        }
    }

    private void checkLowPowerFactor(FeederMeasurement m, FeederConfig cfg) {
        double pf = Math.abs(m.getPowerFactor());
        if (pf > 0.01 && pf < 0.80) {
            fire(Level.PQ_RISK, m.getFeederId(), "FP",
                String.format("Factor de potencia bajo: %.2f (mínimo recomendado: 0.85)", pf),
                pf, 0.80);
        } else if (pf > 0.01 && pf < 0.85) {
            fire(Level.WARNING, m.getFeederId(), "FP",
                String.format("Factor de potencia bajo: %.2f", pf),
                pf, 0.85);
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void fire(Level level, String feederId, String param,
                      String message, double value, double threshold) {
        AlarmEvent event = new AlarmEvent(level, feederId, param, message, value, threshold);
        addToActive(event);
        addToHistory(event);
        for (AlarmListener l : listeners) {
            try { l.onAlarm(event); } catch (Exception ignored) {}
        }
    }

    private synchronized void addToActive(AlarmEvent event) {
        // Evitar duplicados del mismo parámetro en la lista activa
        activeAlarms.removeIf(a ->
            a.getFeederId().equals(event.getFeederId()) &&
            a.getParameter().equals(event.getParameter()) &&
            a.getLevel() == event.getLevel());
        activeAlarms.add(0, event);
        fireActiveAlarmsChanged();
    }

    private synchronized void addToHistory(AlarmEvent event) {
        alarmHistory.add(0, event);
        if (alarmHistory.size() > MAX_HISTORY) {
            alarmHistory.remove(alarmHistory.size() - 1);
        }
    }

    private void fireActiveAlarmsChanged() {
        for (AlarmListener l : listeners) {
            try { l.onActiveAlarmsChanged(getActiveAlarms()); } catch (Exception ignored) {}
        }
    }

    // ── Interface ─────────────────────────────────────────────────────────────

    public interface AlarmListener {
        void onAlarm(AlarmEvent event);
        default void onActiveAlarmsChanged(List<AlarmEvent> activeAlarms) {}
    }
}
