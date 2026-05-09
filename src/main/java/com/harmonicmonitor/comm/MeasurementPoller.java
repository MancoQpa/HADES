package com.harmonicmonitor.comm;

import com.harmonicmonitor.AppExecutors;
import com.harmonicmonitor.analysis.*;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Motor de adquisición periódica de datos para un alimentador.
 * Coordina lectura IEC 61850 → análisis armónico → análisis de cargas → listeners.
 */
public class MeasurementPoller {

    private static final Logger LOG = Logger.getLogger(MeasurementPoller.class.getName());

    private final FeederConfig          config;
    private final IEC61850Communicator  comm;
    private final HarmonicAnalyzer      harmonicAnalyzer;
    private final ElectronicLoadDetector loadDetector;
    private final ResonanceAnalyzer     resonanceAnalyzer;
    private final LoadStabilityAnalyzer stabilityAnalyzer;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>        pollTask;
    private volatile boolean          running = false;

    private final List<MeasurementListener> listeners = new CopyOnWriteArrayList<>();

    // Historial reciente para cálculo de CV (últimas 60 muestras ≈ 5 min a 5s)
    private final int MAX_HISTORY = 60;
    private final List<Double> currentHistory = new ArrayList<>();

    // Detector de conexión zombie: N fallos consecutivos → forceReconnect()
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private int consecutiveReadFailures = 0;

    public MeasurementPoller(FeederConfig config, IEC61850Communicator comm) {
        this.config            = config;
        this.comm              = comm;
        this.harmonicAnalyzer  = new HarmonicAnalyzer();
        this.loadDetector      = new ElectronicLoadDetector();
        this.resonanceAnalyzer = new ResonanceAnalyzer();
        this.stabilityAnalyzer = new LoadStabilityAnalyzer();
    }

    public void addListener(MeasurementListener l)    { listeners.add(l); }
    public void removeListener(MeasurementListener l) { listeners.remove(l); }
    public boolean isRunning()                        { return running; }
    public FeederConfig getConfig()                   { return config; }

    /**
     * Inicia el polling periódico según el intervalo configurado.
     */
    public void start() {
        if (running) return;
        running   = true;
        scheduler = AppExecutors.newDaemonScheduler("Poller-" + config.getFeederId());
        long intervalMs = config.getPollIntervalMs();
        pollTask = scheduler.scheduleAtFixedRate(this::poll, 0, intervalMs, TimeUnit.MILLISECONDS);
        LOG.info("Poller iniciado para " + config.getFeederId() + " cada " + intervalMs + " ms");
    }

    /**
     * Detiene el polling periódico.
     */
    public void stop() {
        running = false;
        if (pollTask != null)     { pollTask.cancel(false); }
        if (scheduler != null)    { scheduler.shutdown(); }
        LOG.info("Poller detenido para " + config.getFeederId());
    }

    /**
     * Cambia el intervalo de polling en tiempo real.
     * Si el poller está corriendo, lo detiene y reinicia con el nuevo intervalo.
     */
    public void setInterval(int intervalMs) {
        boolean wasRunning = running;
        if (wasRunning) stop();
        config.setPollIntervalMs(intervalMs);
        if (wasRunning) start();
    }

    // ── Ciclo de adquisición y análisis ───────────────────────────────────────

    private void poll() {
        if (!comm.isConnected()) return;
        try {
            // 1. Leer medición cruda desde el IED
            FeederMeasurement m = comm.readMeasurement();
            if (m == null || !m.isDataValid()) {
                // Detectar conexión zombie (TCP muerto pero state=CONNECTED, ej. tras hibernate)
                consecutiveReadFailures++;
                if (consecutiveReadFailures >= MAX_CONSECUTIVE_FAILURES) {
                    consecutiveReadFailures = 0;
                    LOG.warning("Poller [" + config.getFeederId() + "]: " +
                        MAX_CONSECUTIVE_FAILURES + " fallos consecutivos — forzando reconexión");
                    comm.forceReconnect();
                }
                return;
            }
            consecutiveReadFailures = 0;

            // 2. Calcular THD si el IED no lo provee
            if (m.getThdCurrentL1() == 0 && m.getHarmonicCurrentL1()[0] > 0) {
                harmonicAnalyzer.calculateThd(m);
            }

            // 2b. Si el IED provee THD pero no el espectro, estimarlo.
            //     SOLO cuando el IED no tiene array de armónicos en su modelo;
            //     si lo tiene (harmonicArrayInModel=true) los valores reales ya
            //     vienen del IED y la estimación sobreescribiría datos correctos.
            if (!comm.isHarmonicArrayInModel()) {
                harmonicAnalyzer.estimateMissingSpectrum(m);
            }

            // 3. Calcular ratios de armónicos (H5/H1, H7/H1, etc.)
            harmonicAnalyzer.calculateHarmonicRatios(m);

            // 4. Actualizar historial de corriente para cálculo de CV
            updateCurrentHistory(m.getCurrentAvg());
            double cv = stabilityAnalyzer.calculateCV(currentHistory);
            m.setCvCurrent(cv);

            // 5. Detectar y clasificar tipo de carga
            loadDetector.classify(m, config);

            // 6. Análisis de resonancia
            resonanceAnalyzer.analyze(m, config);

            // 7. Notificar a los listeners (GUI, alarmEngine, storage)
            fireMeasurement(m);

        } catch (Exception e) {
            LOG.warning("Error en ciclo de polling [" + config.getFeederId() + "]: " + e.getMessage());
        }
    }

    private void updateCurrentHistory(double currentAvg) {
        currentHistory.add(currentAvg);
        if (currentHistory.size() > MAX_HISTORY) {
            currentHistory.remove(0);
        }
    }

    private void fireMeasurement(FeederMeasurement m) {
        for (MeasurementListener l : listeners) {
            try { l.onMeasurement(m); } catch (Exception ignored) {}
        }
    }

    // ── Interface de callback ─────────────────────────────────────────────────

    public interface MeasurementListener {
        void onMeasurement(FeederMeasurement measurement);
    }
}
