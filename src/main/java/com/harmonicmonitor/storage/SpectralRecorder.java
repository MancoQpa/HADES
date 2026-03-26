package com.harmonicmonitor.storage;

import com.harmonicmonitor.comm.MeasurementPoller;
import com.harmonicmonitor.model.FeederMeasurement;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Registrador de espectros armónicos para campaña de caracterización ML.
 *
 * Diseño:
 *   Se registra como MeasurementListener en MeasurementPoller pero opera a un
 *   ritmo propio (sampleIntervalMs), independiente del intervalo de polling del
 *   dashboard. Cuando el poller dispara una medición, este módulo decide si ya
 *   corresponde guardar una muestra de campaña.
 *
 * ION 7400 — clase de exactitud 0.2S (IEC 62053-22), NO PQM Clase A:
 *   El ION 7400 es un contador de energía de alta precisión (±0.2% en Wh).
 *   Sus valores de THD/armónicos via IEC 61850 MHAI son mediciones instantáneas
 *   o de ventana corta interna del equipo — NO son el agregado RMS de 10 min
 *   que exige IEC 61000-4-30 Clase A. Por lo tanto, la agregación temporal
 *   normativa recae completamente en esta aplicación.
 *   El buffer rmsAccumSumSq/rmsAccumCount implementa esa capa de agregación:
 *   acumula cuadráticamente las lecturas de polling recibidas durante el
 *   intervalo de campaña y produce un único THD_rms_window representativo.
 *
 * Esquema de la tabla harmonic_spectra (en DataStorage):
 *   timestamp, feeder_id, session_id
 *   i1_a, i1_b, i1_c                    ← fundamental (A, RMS)
 *   h02..h50_a, h02..h50_b, h02..h50_c  ← armónicos normalizados por I1 (adim.)
 *   thd_i_a, thd_i_b, thd_i_c           ← del ION (Clase A, 10-min RMS)
 *   thd_i_rms_window                     ← RMS del THD promedio trifásico
 *                                           acumulado en la ventana de campaña
 *   cv_current, p_kw, pf, freq_hz
 *   spectrum_estimated                   ← 1 si el espectro fue estimado, 0 si medido
 *   ion_update_lag_s                     ← segundos desde último cambio detectado en THD
 *                                           (detecta si el ION actualizó su ventana 10-min)
 *
 * Volumen estimado:
 *   1 muestra/min × 60 min/h × 24 h × 7 días = 10 080 filas/feeder/semana
 *   Cada fila ≈ 200 columnas (espectro L1+L2+L3 H1-H50 + escalares)
 *   Tamaño SQLite estimado: ~25 MB por feeder por semana
 */
public class SpectralRecorder implements MeasurementPoller.MeasurementListener {

    private static final Logger LOG = Logger.getLogger(SpectralRecorder.class.getName());

    // ── Configuración ─────────────────────────────────────────────────────────
    /** Intervalo de campaña. Default: 60 000 ms (1 minuto). */
    private volatile long sampleIntervalMs;

    /** Duración máxima de campaña. 0 = sin límite. */
    private volatile long campaignDurationMs;

    /** ID del feeder al que pertenece este recorder. */
    private final String feederId;

    // ── Estado de campaña ─────────────────────────────────────────────────────
    private volatile boolean    recording       = false;
    private volatile String     sessionId       = null;
    private volatile Instant    sessionStart    = null;
    private volatile Instant    lastSampleTime  = Instant.EPOCH;
    private final AtomicLong    samplesCollected = new AtomicLong(0);

    // ── Buffer RMS de la ventana de campaña ───────────────────────────────────
    // Acumula THD promedio trifásico² de cada poll recibido dentro de la ventana
    // actual, para producir el THD_rms_window correcto al persistir la muestra.
    // Ref: IEC 61000-4-30 §5.8 — agregación cuadrática.
    private double   rmsAccumSumSq = 0.0;
    private int      rmsAccumCount = 0;

    // Última muestra del ION para detectar si actualizó su ventana 10-min
    private double   lastIonThdAvg   = Double.NaN;
    private Instant  lastIonUpdateTs = Instant.EPOCH;

    // ── Dependencias ──────────────────────────────────────────────────────────
    private final DataStorage storage;

    // ── Listener de progreso ──────────────────────────────────────────────────
    public interface ProgressListener {
        /** Llamado cada vez que se guarda una muestra de campaña. */
        void onSampleRecorded(long samplesTotal, long elapsedMs, long remainingMs);
        /** Llamado cuando la campaña termina (duración cumplida o stop manual). */
        void onCampaignFinished(String sessionId, long totalSamples);
    }
    private volatile ProgressListener progressListener;

    private static final DateTimeFormatter SESSION_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param feederId         ID del alimentador (debe coincidir con FeederConfig)
     * @param storage          Instancia compartida de DataStorage (ya inicializada)
     * @param sampleIntervalMs Intervalo de campaña en ms (60 000 = 1 min)
     */
    public SpectralRecorder(String feederId, DataStorage storage, long sampleIntervalMs) {
        this.feederId         = feederId;
        this.storage          = storage;
        this.sampleIntervalMs = sampleIntervalMs;
        this.campaignDurationMs = 0; // sin límite
    }

    // ── Control de campaña ────────────────────────────────────────────────────

    /**
     * Inicia una nueva sesión de campaña.
     * Genera un sessionId único (feeder + timestamp) para agrupar las muestras.
     */
    public synchronized void startCampaign() {
        if (recording) {
            LOG.warning("SpectralRecorder [" + feederId + "]: campaña ya en curso — ignorado");
            return;
        }
        sessionId        = feederId + "_" + SESSION_FMT.format(Instant.now());
        sessionStart     = Instant.now();
        lastSampleTime   = Instant.EPOCH;
        samplesCollected.set(0);
        rmsAccumSumSq    = 0.0;
        rmsAccumCount    = 0;
        recording        = true;
        LOG.info("SpectralRecorder [" + feederId + "]: campaña iniciada — sesión " + sessionId
            + " intervalo=" + (sampleIntervalMs / 1000) + "s");
    }

    /** Detiene la campaña actual (las muestras ya guardadas quedan en DB). */
    public synchronized void stopCampaign() {
        if (!recording) return;
        recording = false;
        long total = samplesCollected.get();
        LOG.info("SpectralRecorder [" + feederId + "]: campaña detenida — "
            + total + " muestras en sesión " + sessionId);
        if (progressListener != null) {
            progressListener.onCampaignFinished(sessionId, total);
        }
    }

    /** Pausa sin cerrar la sesión. Las muestras se reanudan con el mismo sessionId. */
    public synchronized void pauseCampaign()  { recording = false; }
    public synchronized void resumeCampaign() { recording = true;  }

    public boolean  isRecording()       { return recording; }
    public String   getSessionId()      { return sessionId; }
    public long     getSamplesCount()   { return samplesCollected.get(); }
    public Instant  getSessionStart()   { return sessionStart; }

    public void setSampleIntervalMs(long ms)    { this.sampleIntervalMs    = ms; }
    public void setCampaignDurationMs(long ms)  { this.campaignDurationMs  = ms; }
    public void setProgressListener(ProgressListener l) { this.progressListener = l; }

    /**
     * Estimación del progreso de la campaña (0.0–1.0).
     * Retorna -1 si la duración es ilimitada.
     */
    public double getProgress() {
        if (campaignDurationMs <= 0 || sessionStart == null) return -1.0;
        long elapsed = Instant.now().toEpochMilli() - sessionStart.toEpochMilli();
        return Math.min(1.0, (double) elapsed / campaignDurationMs);
    }

    // ── MeasurementListener ───────────────────────────────────────────────────

    @Override
    public void onMeasurement(FeederMeasurement m) {
        if (!recording || m == null || !feederId.equals(m.getFeederId())) return;

        // ── Acumular en buffer RMS para la ventana actual ─────────────────────
        // THD promedio trifásico instantáneo (valor del ION o calculado)
        double thdAvgNow = thdRms3phase(m);
        rmsAccumSumSq += thdAvgNow * thdAvgNow;
        rmsAccumCount++;

        // Detectar si el ION actualizó su ventana 10-min (cambio en THD)
        if (Double.isNaN(lastIonThdAvg) || Math.abs(thdAvgNow - lastIonThdAvg) > 0.01) {
            lastIonThdAvg   = thdAvgNow;
            lastIonUpdateTs = m.getTimestamp();
        }

        // ── Decidir si corresponde guardar muestra de campaña ─────────────────
        Instant now = m.getTimestamp();
        long elapsed = now.toEpochMilli() - lastSampleTime.toEpochMilli();
        if (elapsed < sampleIntervalMs) return;

        // ── Calcular THD_rms_window: RMS del THD dentro del intervalo ─────────
        double thdRmsWindow = (rmsAccumCount > 0)
            ? Math.sqrt(rmsAccumSumSq / rmsAccumCount)
            : thdAvgNow;

        // Lag desde última actualización del ION
        long ionLagS = (now.toEpochMilli() - lastIonUpdateTs.toEpochMilli()) / 1000L;

        // ── Persistir ─────────────────────────────────────────────────────────
        storage.storeSpectrum(m, sessionId, thdRmsWindow, ionLagS);

        // Reset del buffer RMS para la siguiente ventana
        rmsAccumSumSq = 0.0;
        rmsAccumCount = 0;
        lastSampleTime = now;

        long count = samplesCollected.incrementAndGet();
        LOG.fine("SpectralRecorder [" + feederId + "]: muestra #" + count
            + " THD_rms_window=" + String.format("%.2f", thdRmsWindow) + "%");

        // ── Notificar progreso ────────────────────────────────────────────────
        if (progressListener != null) {
            long elapsedTotal   = now.toEpochMilli() - sessionStart.toEpochMilli();
            long remaining      = (campaignDurationMs > 0)
                ? Math.max(0, campaignDurationMs - elapsedTotal) : -1;
            progressListener.onSampleRecorded(count, elapsedTotal, remaining);
        }

        // ── Verificar duración máxima ─────────────────────────────────────────
        if (campaignDurationMs > 0 && sessionStart != null) {
            long totalElapsed = now.toEpochMilli() - sessionStart.toEpochMilli();
            if (totalElapsed >= campaignDurationMs) {
                stopCampaign();
            }
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * THD trifásico por RMS cuadrático (Corrección A).
     * Correcto para combinar magnitudes definidas como raíz de suma de cuadrados.
     * IEC 61000-4-30: agregación cuadrática de valores por fase.
     */
    private static double thdRms3phase(FeederMeasurement m) {
        double a = m.getThdCurrentL1();
        double b = m.getThdCurrentL2();
        double c = m.getThdCurrentL3();
        // Si alguna fase es cero (no disponible), usar las que sí tienen datos
        int n = 0;
        double sumSq = 0;
        if (a > 0) { sumSq += a * a; n++; }
        if (b > 0) { sumSq += b * b; n++; }
        if (c > 0) { sumSq += c * c; n++; }
        return (n > 0) ? Math.sqrt(sumSq / n) : 0.0;
    }

    // ── Consultas de estado (para la GUI) ─────────────────────────────────────

    /** Descripción de estado legible para la barra de status. */
    public String getStatusText() {
        if (!recording) return "Campaña detenida";
        long n = samplesCollected.get();
        long intervalS = sampleIntervalMs / 1000;
        if (campaignDurationMs > 0 && sessionStart != null) {
            long elap = (Instant.now().toEpochMilli() - sessionStart.toEpochMilli()) / 1000;
            long total = campaignDurationMs / 1000;
            return String.format("Campaña activa: %d muestras · %ds/%ds · intervalo %ds",
                n, elap, total, intervalS);
        }
        return String.format("Campaña activa: %d muestras · intervalo %ds", n, intervalS);
    }
}
