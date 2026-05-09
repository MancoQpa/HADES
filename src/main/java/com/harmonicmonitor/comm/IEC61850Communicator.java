package com.harmonicmonitor.comm;

import com.beanit.iec61850bean.*;
import com.harmonicmonitor.AppExecutors;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Módulo de comunicación IEC 61850 MMS para lectura de datos MMXU, MHAI, MSQI, MMTR, MSTA.
 *
 * Estrategia de conexión en dos fases (igual que IEDNavigator):
 *   Fase 1 - ACSE rápida: asociación MMS, sin cargar el modelo completo.
 *   Fase 2 - loadModel en background: descubrir jerarquía Server→LD→LN→DO→DA.
 *
 * Lectura de mediciones: accede directamente a los atributos del MMXU
 * sin depender de TCTR/TVTR (que suelen estar ausentes en IEDs de MT).
 *
 * Soporte para ION 7400 (cbo2):
 *   - MMXU con prefijo "M03_" → M03_MMXU1
 *   - MHAI1: ThdA, ThdPPV, HKf, ThdOddA, ThdEvnA, HA (harmonic array)
 *   - MSQI1: SeqA, SeqV (componentes simétricas)
 *   - MMTR1: TotWh, TotVAh, TotVArh, SupWh, SupVArh (FC=ST, INT64)
 *   - MSTA1: AvW, MaxW, MinW, AvVAr, AvVA (FC=MX, scalar MV → mag.f)
 *
 * Data mapping and node caching is delegated to {@link MmsDataMapper} (refactor F2-001).
 */
public class IEC61850Communicator implements ClientEventListener {

    private static final Logger LOG = Logger.getLogger(IEC61850Communicator.class.getName());
    private static final int CONNECT_TIMEOUT_MS    = 10_000;  // 10s como IEDNavigator
    private static final int MODEL_LOAD_TIMEOUT_MS = 35_000;

    // --- Estado de conexión ---
    public enum State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private static final int  RECONNECT_DELAY_SEC      = 10;  // 10s entre reintentos
    private static final int  MAX_RECONNECT_ATTEMPTS   = 9999; // prácticamente ilimitado

    private volatile State         state      = State.DISCONNECTED;
    private ClientSap              clientSap;
    private ClientAssociation      association;
    private final FeederConfig     config;
    private final List<CommListener> listeners = new ArrayList<>();
    private ExecutorService        executor;
    private ScheduledExecutorService reconnectScheduler;
    private volatile boolean       userDisconnected = false;
    private final AtomicInteger    reconnectAttempts = new AtomicInteger(0);
    private final AtomicInteger    pendingReconnects = new AtomicInteger(0); // evita tormenta de reconexiones

    // --- Data mapper (owns serverModel, ref strings, node caches) ---
    private MmsDataMapper mapper;

    public IEC61850Communicator(FeederConfig config) {
        this.config = config;
        this.mapper = new MmsDataMapper(config, msg -> fireEvent(CommEvent.Type.INFO, msg));
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public void addListener(CommListener l)    { listeners.add(l); }
    public void removeListener(CommListener l) { listeners.remove(l); }
    public State getState()                    { return state; }
    public boolean isConnected()               { return state == State.CONNECTED; }
    public FeederConfig getConfig()            { return config; }
    public ServerModel getServerModel()        { return mapper != null ? mapper.getServerModel() : null; }
    public boolean isHarmonicArrayInModel()    { return mapper != null && mapper.isHarmonicArrayInModel(); }

    /** Inicia la conexión MMS en un hilo separado para no bloquear la GUI. */
    public void connectAsync() {
        if (state == State.CONNECTING || state == State.CONNECTED) return;
        userDisconnected = false;
        reconnectAttempts.set(0);
        pendingReconnects.set(0);
        setState(State.CONNECTING);
        executor = AppExecutors.newDaemonExecutor("IEC61850-" + config.getFeederId());
        executor.submit(this::connectInternal);
    }

    /** Desconecta la sesión MMS (sin auto-reconexión). */
    public void disconnect() {
        userDisconnected = true;
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.shutdownNow();
        }
        if (association != null) {
            try { association.disconnect(); } catch (Exception ignored) {}
            association = null;
        }
        if (executor != null) executor.shutdown();
        mapper.clearCache();
        setState(State.DISCONNECTED);
        fireEvent(CommEvent.Type.DISCONNECTED, "Desconectado de " + config.getIedHost());
    }

    /**
     * Lee un snapshot completo de mediciones del MMXU + MHAI + MSQI + MMTR + MSTA.
     * Requiere que la conexión esté activa.
     */
    public FeederMeasurement readMeasurement() {
        if (!isConnected() || association == null) return null;
        FeederMeasurement m = mapper.readAll(association);
        if (m != null && !m.isDataValid()) {
            fireEvent(CommEvent.Type.READ_ERROR, "Error de lectura MMXU [" + config.getFeederId() + "]");
        }
        return m;
    }

    // ── ClientEventListener ───────────────────────────────────────────────────

    @Override
    public void newReport(Report report) {
        // No se usa reportes en este módulo (polling directo)
    }

    @Override
    public void associationClosed(IOException e) {
        LOG.warning("Asociación cerrada: " + (e != null ? e.getMessage() : "sin error"));
        association = null;
        mapper.clearCache();
        setState(State.ERROR);
        String reason = (e != null ? e.getMessage() : "Conexión cerrada remotamente");
        fireEvent(CommEvent.Type.CONNECTION_FAILED, reason);

        if (!userDisconnected) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        // Si ya hay una reconexión pendiente o en curso, no encolar otra
        if (pendingReconnects.get() > 0) {
            LOG.fine("scheduleReconnect ignorado: ya hay " + pendingReconnects.get() + " reintento(s) pendiente(s)");
            return;
        }
        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            LOG.warning("Reconexión: máximo de intentos alcanzado (" + MAX_RECONNECT_ATTEMPTS + ")");
            fireEvent(CommEvent.Type.INFO, "Reconexión abandonada tras " + MAX_RECONNECT_ATTEMPTS + " intentos");
            return;
        }
        // Backoff: 5s, 10s, 15s → 30s fijo (mínimo 5s para no saturar el IED)
        int delaySec = Math.min(attempt * 5, RECONNECT_DELAY_SEC * 3);
        LOG.info("Reconexión programada en " + delaySec + "s (intento " + attempt + "/" + MAX_RECONNECT_ATTEMPTS + ")");
        fireEvent(CommEvent.Type.INFO, "Reconectando en " + delaySec + "s (intento " + attempt + ")...");

        if (reconnectScheduler == null || reconnectScheduler.isShutdown()) {
            reconnectScheduler = AppExecutors.newDaemonScheduler("Reconnect-" + config.getFeederId());
        }
        pendingReconnects.incrementAndGet();
        reconnectScheduler.schedule(() -> {
            pendingReconnects.decrementAndGet();
            if (userDisconnected) return;
            // Doble-check: no conectar si ya hay una conexión activa o en curso
            if (state == State.CONNECTING || state == State.CONNECTED) return;
            setState(State.CONNECTING);
            executor = AppExecutors.newDaemonExecutor("IEC61850-" + config.getFeederId());
            executor.submit(this::connectInternal);
        }, delaySec, TimeUnit.SECONDS);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void connectInternal() {
        try {
            LOG.info("Conectando a " + config.getIedHost() + ":" + config.getIedPort());
            clientSap = new ClientSap();
            clientSap.setResponseTimeout(60000);
            clientSap.setMessageFragmentTimeout(30000);

            InetAddress address = InetAddress.getByName(config.getIedHost());

            // Fase 1: Asociación ACSE con timeout
            ExecutorService connExec = Executors.newSingleThreadExecutor();
            Future<ClientAssociation> future = connExec.submit(() ->
                clientSap.associate(address, config.getIedPort(), null, IEC61850Communicator.this));
            try {
                association = future.get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new IOException("Timeout ACSE después de " + CONNECT_TIMEOUT_MS / 1000 + "s");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw new IOException("Error de conexión: " + cause.getMessage(), cause);
            } finally {
                connExec.shutdown();
            }

            fireEvent(CommEvent.Type.CONNECTED, "Asociación ACSE establecida con " + config.getIedHost());

            // Fase 2: Cargar modelo en background con timeout
            LOG.info("Cargando modelo de datos del servidor...");
            ExecutorService modelExec = Executors.newSingleThreadExecutor();
            Future<ServerModel> modelFuture = modelExec.submit(() -> association.retrieveModel());
            ServerModel serverModel = null;
            try {
                serverModel = modelFuture.get(MODEL_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                modelFuture.cancel(false);
                LOG.warning("Timeout cargando modelo — usando modelo parcial");
            } catch (ExecutionException e) {
                throw new IOException("Error cargando modelo: " + e.getCause().getMessage(), e.getCause());
            } finally {
                modelExec.shutdown();
            }

            mapper.buildRefs(serverModel, association);
            setState(State.CONNECTED);
            fireEvent(CommEvent.Type.MODEL_LOADED, "Modelo cargado. MMXU " +
                (mapper.isHarmonicsAvailable() ? "con" : "sin") + " armónicos (MHAI).");

        } catch (Exception e) {
            LOG.severe("Error conectando a " + config.getIedHost() + ": " + e.getMessage());
            setState(State.ERROR);
            fireEvent(CommEvent.Type.CONNECTION_FAILED, e.getMessage());
            if (association != null) {
                try { association.disconnect(); } catch (Exception ignored) {}
                association = null;
            }
            // Volver a programar reconexión si no fue el usuario quien desconectó
            if (!userDisconnected) {
                scheduleReconnect();
            }
        }
    }

    /**
     * Fuerza una reconexión inmediata (ej. detección de conexión zombie por el poller).
     * No tiene efecto si el usuario desconectó manualmente.
     */
    public void forceReconnect() {
        if (userDisconnected) return;
        if (state == State.CONNECTING || pendingReconnects.get() > 0) return;
        LOG.warning("Forzando reconexión [" + config.getFeederId() + "]");
        if (association != null) {
            try { association.disconnect(); } catch (Exception ignored) {}
            association = null;
        }
        mapper.clearCache();
        setState(State.ERROR);
        fireEvent(CommEvent.Type.INFO, "Reconexión forzada por fallos consecutivos...");
        scheduleReconnect();
    }

    private void setState(State newState) {
        this.state = newState;
    }

    private void fireEvent(CommEvent.Type type, String message) {
        CommEvent event = new CommEvent(type, config.getFeederId(), message);
        for (CommListener l : listeners) {
            try { l.onCommEvent(event); } catch (Exception ignored) {}
        }
    }

    // ── Interfaces ────────────────────────────────────────────────────────────

    public interface CommListener {
        void onCommEvent(CommEvent event);
    }

    public static class CommEvent {
        public enum Type {
            CONNECTED, DISCONNECTED, MODEL_LOADED, CONNECTION_FAILED,
            READ_ERROR, INFO
        }
        public final Type   type;
        public final String feederId;
        public final String message;
        public CommEvent(Type type, String feederId, String message) {
            this.type     = type;
            this.feederId = feederId;
            this.message  = message;
        }
    }
}
