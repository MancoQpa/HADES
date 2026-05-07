package com.harmonicmonitor.comm;

import com.beanit.iec61850bean.*;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private ServerModel            serverModel;
    private final FeederConfig     config;
    private final List<CommListener> listeners = new ArrayList<>();
    private ExecutorService        executor;
    private ScheduledExecutorService reconnectScheduler;
    private volatile boolean       userDisconnected = false;
    private final AtomicInteger    reconnectAttempts = new AtomicInteger(0);

    // --- Referencias DO/DA del MMXU (con prefijo) ---

    private String phsAPhVRef;   // p.ej. "cbo2LD0/M03_MMXU1.PhV.phsA"
    private String phsBPhVRef;
    private String phsCPhVRef;
    private String phsAARef;     // Corriente fase A
    private String phsBARef;
    private String phsCARef;
    private String wRef;         // Potencia activa total
    private String varRef;
    private String vaRef;
    private String pfRef;
    private String hzRef;

    // --- Referencias MHAI (THD directo del medidor) ---
    private String thdAL1Ref, thdAL2Ref, thdAL3Ref;
    private String thdPpvL12Ref, thdPpvL23Ref, thdPpvL31Ref;
    private String kfL1Ref, kfL2Ref, kfL3Ref;
    private String thdOddAL1Ref, thdEvenAL1Ref;

    // --- Referencias MSQI (componentes simétricas) ---
    private String seqAposRef, seqAnegRef;
    private String seqVposRef, seqVnegRef;

    // --- Referencias MMTR (energía, FC=ST, INT64) ---
    private String totWhRef, totVAhRef, totVArhRef, supWhRef, supVArhRef;

    // --- Referencias MSTA (demanda, FC=MX, scalar MV) ---
    private String avgWRef, maxWRef, minWRef, avgVArRef, avgVARef;

    // --- Array de armónicos HA (phsXHar01..50) ---
    private final String[] haPhsAHarRef = new String[50]; // índice 0=H1, 1=H2, etc.
    private final String[] haPhsBHarRef = new String[50];
    private final String[] haPhsCHarRef = new String[50];

    private boolean harmonicsAvailable    = false;
    private boolean harmonicArrayInModel  = false;  // true si phsAHar01 existe en el modelo
    private boolean powerScaleDetected    = false;  // true tras auto-detectar powerScaleFactor
    private final AtomicInteger pendingReconnects = new AtomicInteger(0); // evita tormenta de reconexiones

    // ── Node cache (patrón IEDNavigator nodeMap) ─────────────────────────────
    // Nodos descubiertos al conectar. Evita llamadas repetidas a findModelNode()
    // en cada ciclo de polling (~60 búsquedas de árbol → O(1) por ciclo).
    private static final class NodePair {
        final FcModelNode parent; // nodo FC=MX para getDataValues() (actualiza todos los DA del DO)
        final ModelNode   leaf;   // BDA hoja de la cual se extrae el valor float
        NodePair(FcModelNode parent, ModelNode leaf) {
            this.parent = parent;
            this.leaf   = leaf;
        }
    }
    private final Map<String, NodePair>  mxNodeCache = new HashMap<>();
    private final Map<String, ModelNode> stNodeCache = new HashMap<>();

    // Lectura periodica del array HA: cada HARMONICS_READ_EVERY_N ciclos (~30s a 5s/ciclo)
    private static final int HARMONICS_READ_EVERY_N = 6;
    private int    harmonicsPollCounter = HARMONICS_READ_EVERY_N; // primer ciclo lee inmediatamente
    private double[] cachedHarPhsA = null;
    private double[] cachedHarPhsB = null;
    private double[] cachedHarPhsC = null;

    public IEC61850Communicator(FeederConfig config) {
        this.config = config;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public void addListener(CommListener l)    { listeners.add(l); }
    public void removeListener(CommListener l) { listeners.remove(l); }
    public State getState()                    { return state; }
    public boolean isConnected()               { return state == State.CONNECTED; }
    public FeederConfig getConfig()            { return config; }
    public ServerModel getServerModel()        { return serverModel; }
    public boolean isHarmonicArrayInModel()    { return harmonicArrayInModel; }

    /** Inicia la conexión MMS en un hilo separado para no bloquear la GUI. */
    public void connectAsync() {
        if (state == State.CONNECTING || state == State.CONNECTED) return;
        userDisconnected = false;
        reconnectAttempts.set(0);
        pendingReconnects.set(0);
        setState(State.CONNECTING);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "IEC61850-" + config.getFeederId());
            t.setDaemon(true);
            return t;
        });
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
        serverModel = null;
        mxNodeCache.clear();
        stNodeCache.clear();
        powerScaleDetected = false;
        setState(State.DISCONNECTED);
        fireEvent(CommEvent.Type.DISCONNECTED, "Desconectado de " + config.getIedHost());
    }

    /**
     * Lee un snapshot completo de mediciones del MMXU + MHAI + MSQI + MMTR + MSTA.
     * Requiere que la conexión esté activa.
     */
    public FeederMeasurement readMeasurement() {
        if (!isConnected() || association == null) return null;

        // Escala de potencia detectada durante Descubrimiento (IEDModelDiscovery).
        // No se sobreescribe aquí para respetar lo detectado o configurado.

        FeederMeasurement m = new FeederMeasurement(config.getFeederId(), config.getIedName());
        final double as  = config.getAnalogScaleFactor(); // 1.0=estándar, 0.0001=ION 7400 (multiplier=-4)
        final double ps  = config.getPowerScaleFactor();  // 0.001=W->kW, 1.0=ya en kW (ION 7400)
        final double pfs = config.getPfScaleFactor();     // 1.0=per unit, 0.01=% (ION 7400)
        try {
            // MMXU — valores fundamentales
            m.setVoltageL1(readMxFloat(phsAPhVRef) * as);
            m.setVoltageL2(readMxFloat(phsBPhVRef) * as);
            m.setVoltageL3(readMxFloat(phsCPhVRef) * as);
            m.setCurrentL1(readMxFloat(phsAARef) * as);
            m.setCurrentL2(readMxFloat(phsBARef) * as);
            m.setCurrentL3(readMxFloat(phsCARef) * as);
            m.setActivePower(readMxFloat(wRef)   * ps);
            m.setReactivePower(readMxFloat(varRef) * ps);
            m.setApparentPower(readMxFloat(vaRef)  * ps);
            m.setPowerFactor(Math.min(1.0, readMxFloat(pfRef) * pfs));
            m.setFrequency(readMxFloat(hzRef) * as);
            m.setDataValid(true);
            m.setQualityFlag("GOOD");
        } catch (ServiceError | IOException e) {
            LOG.warning("Error leyendo MMXU [" + config.getFeederId() + "]: " + e.getMessage());
            m.setDataValid(false);
            m.setQualityFlag("COMM_ERROR");
            fireEvent(CommEvent.Type.READ_ERROR, e.getMessage());
        }

        // MHAI — THD, K-factor y array de armónicos (sólo si disponible)
        if (harmonicsAvailable) {
            try {
                m.setThdCurrentL1(readMxFloat(thdAL1Ref));
            } catch (Exception ignored) {}
            try {
                m.setThdCurrentL2(readMxFloat(thdAL2Ref));
            } catch (Exception ignored) {}
            try {
                m.setThdCurrentL3(readMxFloat(thdAL3Ref));
            } catch (Exception ignored) {}
            try {
                m.setThdPpvL12(readMxFloat(thdPpvL12Ref));
            } catch (Exception ignored) {}
            try {
                m.setThdPpvL23(readMxFloat(thdPpvL23Ref));
            } catch (Exception ignored) {}
            try {
                m.setThdPpvL31(readMxFloat(thdPpvL31Ref));
            } catch (Exception ignored) {}
            try {
                m.setKFactorL1(readMxFloat(kfL1Ref));
            } catch (Exception ignored) {}
            try {
                m.setKFactorL2(readMxFloat(kfL2Ref));
            } catch (Exception ignored) {}
            try {
                m.setKFactorL3(readMxFloat(kfL3Ref));
            } catch (Exception ignored) {}
            try {
                m.setThdOddCurrentL1(readMxFloat(thdOddAL1Ref));
            } catch (Exception ignored) {}
            try {
                m.setThdEvenCurrentL1(readMxFloat(thdEvenAL1Ref));
            } catch (Exception ignored) {}

            // Array HA (phsXHar01..50): se lee cada HARMONICS_READ_EVERY_N ciclos para
            // no saturar el ION 7400 con 150 peticiones MMS por ciclo.
            // Solo se intenta si el nodo existe en el modelo (harmonicArrayInModel=true).
            if (harmonicArrayInModel) {
                harmonicsPollCounter++;
                if (harmonicsPollCounter >= HARMONICS_READ_EVERY_N) {
                    harmonicsPollCounter = 0;
                    double[] tryA = readHarmonicArray(haPhsAHarRef);
                    // Filtrar Float.MAX_VALUE (centinela ION 7400 para "no aplicable")
                    for (int i = 0; i < tryA.length; i++) {
                        if (tryA[i] > 1e30f) tryA[i] = 0.0;
                    }
                    // Cachear solo si H1 tiene un valor valido (>0 y razonable)
                    if (tryA[0] > 1e-9) {
                        cachedHarPhsA = tryA;
                        double[] tryB = readHarmonicArray(haPhsBHarRef);
                        double[] tryC = readHarmonicArray(haPhsCHarRef);
                        for (int i = 0; i < 50; i++) {
                            if (tryB[i] > 1e30f) tryB[i] = 0.0;
                            if (tryC[i] > 1e30f) tryC[i] = 0.0;
                        }
                        cachedHarPhsB = tryB;
                        cachedHarPhsC = tryC;
                        LOG.info("Harmonicos leidos del IED [" + config.getFeederId() +
                                 "] H1(p.u.)=" + tryA[0] +
                                 " H3(p.u.)=" + tryA[2] +
                                 " H5(p.u.)=" + tryA[4] +
                                 " H7(p.u.)=" + tryA[6]);
                    } else {
                        LOG.warning("Lectura HA retorno ceros o invalidos [" + config.getFeederId() +
                                    "] tryA[0]=" + tryA[0]);
                    }
                }
                if (cachedHarPhsA != null) {
                    double[] harA = cachedHarPhsA.clone();
                    double[] harB = cachedHarPhsB.clone();
                    double[] harC = cachedHarPhsC.clone();
                    // Los valores HA del ION 7400 son per-unit (H1=1.0, H3=0.05, etc.)
                    // Si H1 <= 1.01 => per-unit: escalar por corriente medida del MMXU
                    // Si H1 > 1.01  => absolutos en A: escalar solo al valor actual
                    double iL1 = m.getCurrentL1() > 1e-6 ? m.getCurrentL1() : 1.0;
                    double iL2 = m.getCurrentL2() > 1e-6 ? m.getCurrentL2() : iL1;
                    double iL3 = m.getCurrentL3() > 1e-6 ? m.getCurrentL3() : iL1;
                    if (harA[0] > 1e-9) {
                        double scaleA = (harA[0] <= 1.01) ? iL1          : iL1 / harA[0];
                        double scaleB = (harB[0] <= 1.01) ? iL2          : iL2 / harB[0];
                        double scaleC = (harC[0] <= 1.01) ? iL3          : iL3 / harC[0];
                        for (int i = 0; i < 50; i++) { harA[i] *= scaleA; harB[i] *= scaleB; harC[i] *= scaleC; }
                        harA[0] = iL1; harB[0] = iL2; harC[0] = iL3;  // H1 exacto del MMXU
                    }
                    m.setHarmonicCurrentL1(harA);
                    m.setHarmonicCurrentL2(harB);
                    m.setHarmonicCurrentL3(harC);
                }
            }

            // MSQI — componentes simétricas (SeqA en A, SeqV en V → aplicar analogScaleFactor)
            try {
                double seqApos = readMxFloat(seqAposRef) * as;
                double seqAneg = readMxFloat(seqAnegRef) * as;
                double seqVpos = readMxFloat(seqVposRef) * as;
                double seqVneg = readMxFloat(seqVnegRef) * as;
                m.setSeqCurrentPos(seqApos);
                m.setSeqCurrentNeg(seqAneg);
                m.setSeqVoltagePos(seqVpos);
                m.setSeqVoltageNeg(seqVneg);
                if (seqVpos > 0) m.setVoltageUnbalancePct(seqVneg / seqVpos * 100.0);
                if (seqApos > 0) m.setCurrentUnbalancePct(seqAneg / seqApos * 100.0);
            } catch (Exception ignored) {}
        }

        // MMTR — energía (FC=ST, INT64 → dividir por 1000 → kWh/kVAh/kVArh)
        try { m.setTotalEnergyKWh(readStInt64(totWhRef) / 1000.0); } catch (Exception ignored) {}
        try { m.setTotalEnergyKVAh(readStInt64(totVAhRef) / 1000.0); } catch (Exception ignored) {}
        try { m.setTotalEnergyKVArh(readStInt64(totVArhRef) / 1000.0); } catch (Exception ignored) {}
        try { m.setSupplyKWh(readStInt64(supWhRef) / 1000.0); } catch (Exception ignored) {}
        try { m.setSupplyKVArh(readStInt64(supVArhRef) / 1000.0); } catch (Exception ignored) {}

        // MSTA — demanda: usar ps igual que MMXU (ION 7400: ps=1.0 ya en kW; simulador: ps=0.001 W→kW)
        try { m.setDemandAvgKW(readMxFloat(avgWRef) * ps); } catch (Exception ignored) {}
        try { m.setDemandMaxKW(readMxFloat(maxWRef) * ps); } catch (Exception ignored) {}
        try { m.setDemandMinKW(readMxFloat(minWRef) * ps); } catch (Exception ignored) {}
        try { m.setDemandAvgKVAr(readMxFloat(avgVArRef) * ps); } catch (Exception ignored) {}
        try { m.setDemandAvgKVA(readMxFloat(avgVARef) * ps); } catch (Exception ignored) {}

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
        serverModel = null;
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
            reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Reconnect-" + config.getFeederId());
                t.setDaemon(true);
                return t;
            });
        }
        pendingReconnects.incrementAndGet();
        reconnectScheduler.schedule(() -> {
            pendingReconnects.decrementAndGet();
            if (userDisconnected) return;
            // Doble-check: no conectar si ya hay una conexión activa o en curso
            if (state == State.CONNECTING || state == State.CONNECTED) return;
            setState(State.CONNECTING);
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "IEC61850-" + config.getFeederId());
                t.setDaemon(true);
                return t;
            });
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

            buildMmxuRefs();
            setState(State.CONNECTED);
            fireEvent(CommEvent.Type.MODEL_LOADED, "Modelo cargado. MMXU " +
                (harmonicsAvailable ? "con" : "sin") + " armónicos (MHAI).");

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
        serverModel = null;
        setState(State.ERROR);
        fireEvent(CommEvent.Type.INFO, "Reconexión forzada por fallos consecutivos...");
        scheduleReconnect();
    }

    /**
     * Construye las referencias de los atributos MMXU/MHAI/MSQI/MMTR/MSTA en el formato
     * requerido por iec61850bean: "IEDNameLDInst/LNInst.DO.DA" con FC separado.
     *
     * Para ION 7400 (cbo2):
     *   mmxuPrefix = "M03_"  →  mmxuLn = "M03_MMXU1"
     *   mhaiLn = "MHAI1", msqiLn = "MSQI1", mmtrLn = "MMTR1", mstaLn = "MSTA1"
     */
    private void buildMmxuRefs() {
        // Limpiar caché de armónicos: al conectar a un nuevo dispositivo los valores
        // del dispositivo anterior no deben contaminar la lectura del nuevo.
        cachedHarPhsA = null;
        cachedHarPhsB = null;
        cachedHarPhsC = null;
        harmonicsPollCounter = HARMONICS_READ_EVERY_N; // primer ciclo lee inmediatamente
        mxNodeCache.clear();
        stNodeCache.clear();

        // ── Auto-discovery (patrón IEDNavigator ConnectionManager) ────────────
        // Si mmxuLnRef no está configurado, ejecutar IEDModelDiscovery para encontrar
        // automáticamente los LN de medición disponibles en el IED conectado.
        if ((config.getMmxuLnRef() == null || config.getMmxuLnRef().isEmpty()) && serverModel != null) {
            LOG.info("[" + config.getFeederId() + "] LN refs vacíos — ejecutando auto-discovery...");
            DiscoveryResult dr = IEDModelDiscovery.discover(serverModel, config);
            FeederConfig suggested = dr.getSuggestedConfig();
            if (suggested != null && suggested.getMmxuLnRef() != null && !suggested.getMmxuLnRef().isEmpty()) {
                config.setLdInst(suggested.getLdInst() != null ? suggested.getLdInst() : config.getLdInst());
                config.setMmxuPrefix(suggested.getMmxuPrefix() != null ? suggested.getMmxuPrefix() : "");
                config.setMmxuLnRef(suggested.getMmxuLnRef());
                config.setMhaiLnRef(suggested.getMhaiLnRef() != null ? suggested.getMhaiLnRef() : "");
                config.setMsqiLnRef(suggested.getMsqiLnRef() != null ? suggested.getMsqiLnRef() : "");
                config.setMmtrLnRef(suggested.getMmtrLnRef() != null ? suggested.getMmtrLnRef() : "");
                config.setMstaLnRef(suggested.getMstaLnRef() != null ? suggested.getMstaLnRef() : "");
                fireEvent(CommEvent.Type.INFO, "Auto-discovery: " +
                    config.getMmxuPrefix() + config.getMmxuLnRef() + " @ " + config.getLdInst());
                LOG.info("[" + config.getFeederId() + "] Auto-discovery OK: " +
                    config.getMmxuPrefix() + config.getMmxuLnRef() + " @ " + config.getLdInst());
            } else {
                LOG.warning("[" + config.getFeederId() + "] Auto-discovery: no se encontró LN de medición");
            }
        }

        String ld      = config.getLdInst();       // "LD0"
        String iedName = config.getIedName();       // "cbo2"

        // MMXU con prefijo
        String mmxuLn = config.getMmxuPrefix() + config.getMmxuLnRef(); // "M03_MMXU1"
        String mhaiLn = config.getMhaiLnRef();  // "MHAI1"
        String msqiLn = config.getMsqiLnRef();  // "MSQI1"
        String mmtrLn = config.getMmtrLnRef();  // "MMTR1"
        String mstaLn = config.getMstaLnRef();  // "MSTA1"

        String mmxuBase = iedName + ld + "/" + mmxuLn;   // "cbo2LD0/M03_MMXU1"
        String mhaiBase = iedName + ld + "/" + mhaiLn;   // "cbo2LD0/MHAI1"
        String msqiBase = iedName + ld + "/" + msqiLn;   // "cbo2LD0/MSQI1"
        String mmtrBase = iedName + ld + "/" + mmtrLn;   // "cbo2LD0/MMTR1"
        String mstaBase = iedName + ld + "/" + mstaLn;   // "cbo2LD0/MSTA1"

        // MMXU — tensiones: probar PhV primero, luego PPV
        phsAPhVRef = findMxRef(mmxuBase, new String[]{"PhV.phsA", "PPV.phsAB"});
        phsBPhVRef = findMxRef(mmxuBase, new String[]{"PhV.phsB", "PPV.phsBC"});
        phsCPhVRef = findMxRef(mmxuBase, new String[]{"PhV.phsC", "PPV.phsCA"});

        // MMXU — corrientes (WYE type → cVal.mag.f bajo phsX)
        phsAARef = mmxuBase + ".A.phsA";
        phsBARef = mmxuBase + ".A.phsB";
        phsCARef = mmxuBase + ".A.phsC";

        // MMXU — potencias escalares (MV → mag.f)
        wRef   = mmxuBase + ".TotW";
        varRef = mmxuBase + ".TotVAr";
        vaRef  = mmxuBase + ".TotVA";
        pfRef  = mmxuBase + ".TotPF";
        hzRef  = mmxuBase + ".Hz";

        // MHAI — THD corriente (WYE type → phsX.cVal.mag.f)
        thdAL1Ref = mhaiBase + ".ThdA.phsA";
        thdAL2Ref = mhaiBase + ".ThdA.phsB";
        thdAL3Ref = mhaiBase + ".ThdA.phsC";

        // MHAI — THD tensión PP (DEL type → phsAB.cVal.mag.f)
        thdPpvL12Ref = mhaiBase + ".ThdPPV.phsAB";
        thdPpvL23Ref = mhaiBase + ".ThdPPV.phsBC";
        thdPpvL31Ref = mhaiBase + ".ThdPPV.phsCA";

        // MHAI — K-factor (WYE type → phsX.cVal.mag.f)
        kfL1Ref = mhaiBase + ".HKf.phsA";
        kfL2Ref = mhaiBase + ".HKf.phsB";
        kfL3Ref = mhaiBase + ".HKf.phsC";

        // MHAI — THD impar/par (WYE type)
        thdOddAL1Ref  = mhaiBase + ".ThdOddA.phsA";
        thdEvenAL1Ref = mhaiBase + ".ThdEvnA.phsA";

        // MSQI — componentes simétricas (SEQ type → c1/c2/c3.cVal.mag.f)
        seqAposRef = msqiBase + ".SeqA.c1";
        seqAnegRef = msqiBase + ".SeqA.c2";
        seqVposRef = msqiBase + ".SeqV.c1";
        seqVnegRef = msqiBase + ".SeqV.c2";

        // MMTR — energía (BCR type → actVal, FC=ST, INT64 → dividir por 1000 → kWh)
        totWhRef   = mmtrBase + ".TotWh";
        totVAhRef  = mmtrBase + ".TotVAh";
        totVArhRef = mmtrBase + ".TotVArh";
        supWhRef   = mmtrBase + ".SupWh";
        supVArhRef = mmtrBase + ".SupVArh";

        // MSTA — demanda (scalar MV → mag.f, W/VAr/VA → dividir por 1000 → kW/kVAr/kVA)
        avgWRef   = mstaBase + ".AvW";
        maxWRef   = mstaBase + ".MaxW";
        minWRef   = mstaBase + ".MinW";
        avgVArRef = mstaBase + ".AvVAr";
        avgVARef  = mstaBase + ".AvVA";

        // Array de armonicos HA — indice 0=H1 (fundamental), 1=H2, etc.
        // Formato por defecto: dot-index base 0 (simulador con count="50" en CID).
        // Si el IED real usa base 1 (ION 7400), se corrige tras la deteccion.
        for (int h = 0; h < 50; h++) {
            haPhsAHarRef[h] = mhaiBase + ".HA.phsAHar." + h;
            haPhsBHarRef[h] = mhaiBase + ".HA.phsBHar." + h;
            haPhsCHarRef[h] = mhaiBase + ".HA.phsCHar." + h;
        }

        // Verificar disponibilidad de MHAI en el modelo
        harmonicsAvailable = (serverModel != null &&
            serverModel.findModelNode(thdAL1Ref, Fc.MX) != null);

        // Verificar si el array de armónicos existe en el modelo y descubrir su estructura real.
        // Orden de prueba:
        //   1. HarA.h01  → simulador Android (HAR50_t con DAs únicos h01..h50) — nuevo
        //   2. HA.phsAHar.0  → simulador antiguo (count="50", base 0)
        //   3. HA.phsAHar.1  → ION 7400 real (array propietario, base 1)
        //   4. HA.phsAHar01  → formato zero-padded legacy
        if (serverModel != null && harmonicsAvailable) {
            String[] harVariants = {
                mhaiBase + ".HarA.h01.mag.f",              // HAR50_t con h01..h50 (simulador nuevo)
                mhaiBase + ".HA.phsAHar.0.cVal.mag.f",    // dot-index base 0 (simulador antiguo)
                mhaiBase + ".HA.phsAHar.1.cVal.mag.f",    // dot-index base 1 (ION 7400 real)
                mhaiBase + ".HA.phsAHar01.cVal.mag.f",    // zero-padded legacy
            };
            String workingPattern = null;
            for (String variant : harVariants) {
                // Try Fc.MX first; fall back to null because sub-BDAs in the
                // client model (from retrieveModel) may carry no explicit FC.
                if (serverModel.findModelNode(variant, Fc.MX) != null ||
                    serverModel.findModelNode(variant, null) != null) {
                    workingPattern = variant;
                    break;
                }
            }
            harmonicArrayInModel = (workingPattern != null);
            LOG.info("Harmonicos en modelo: " + harmonicArrayInModel +
                     (workingPattern != null ? "  patron: " + workingPattern : "  (no encontrado)"));

            // Reconstruir refs según el patrón detectado
            if (workingPattern != null) {
                if (workingPattern.contains(".HarA.h01.")) {
                    // HAR50_t nuevo: DAs únicos h01..h50, rutas HarA/HarB/HarC
                    for (int h = 0; h < 50; h++) {
                        String hn = String.format(".h%02d", h + 1);
                        haPhsAHarRef[h] = mhaiBase + ".HarA" + hn;
                        haPhsBHarRef[h] = mhaiBase + ".HarB" + hn;
                        haPhsCHarRef[h] = mhaiBase + ".HarC" + hn;
                    }
                    LOG.info("Refs HA: HAR50_t h01..h50 (simulador nuevo)");
                } else if (workingPattern.contains(".phsAHar.1.")) {
                    // ION 7400 real: base 1
                    for (int h = 0; h < 50; h++) {
                        haPhsAHarRef[h] = mhaiBase + ".HA.phsAHar." + (h + 1);
                        haPhsBHarRef[h] = mhaiBase + ".HA.phsBHar." + (h + 1);
                        haPhsCHarRef[h] = mhaiBase + ".HA.phsCHar." + (h + 1);
                    }
                    LOG.info("Refs HA: dot-index base 1 (ION 7400 real)");
                } else if (workingPattern.contains(".phsAHar01.")) {
                    // Legacy zero-padded
                    for (int h = 0; h < 50; h++) {
                        haPhsAHarRef[h] = mhaiBase + ".HA.phsAHar" + String.format("%02d", h + 1);
                        haPhsBHarRef[h] = mhaiBase + ".HA.phsBHar" + String.format("%02d", h + 1);
                        haPhsCHarRef[h] = mhaiBase + ".HA.phsCHar" + String.format("%02d", h + 1);
                    }
                    LOG.info("Refs HA: zero-padded legacy");
                } else {
                    // HA.phsAHar base 0 — ya configurado por defecto arriba
                    LOG.info("Refs HA: dot-index base 0 (simulador antiguo)");
                }
            }

            // Si no se encontro con variantes conocidas, volcar los primeros DOs de MHAI para diagnostico
            if (!harmonicArrayInModel) {
                dumpMhaiStructure(mhaiBase);
            }
        }

        // Auto-detectar powerScaleFactor desde TotW.units.multiplier del IED.
        // Siempre se ejecuta al conectar para no depender de la configuración manual.
        if (!powerScaleDetected) {
            autoDetectPowerScale();
            powerScaleDetected = true;
        }

        // ── Poblar node cache (patrón IEDNavigator nodeMap) ──────────────────
        // Construye Map<ref, NodePair> con los nodos del modelo descubiertos una vez,
        // para evitar findModelNode() en cada ciclo de polling.
        cacheNodePair(phsAPhVRef); cacheNodePair(phsBPhVRef); cacheNodePair(phsCPhVRef);
        cacheNodePair(phsAARef);   cacheNodePair(phsBARef);   cacheNodePair(phsCARef);
        cacheNodePair(wRef);       cacheNodePair(varRef);     cacheNodePair(vaRef);
        cacheNodePair(pfRef);      cacheNodePair(hzRef);
        if (harmonicsAvailable) {
            cacheNodePair(thdAL1Ref);    cacheNodePair(thdAL2Ref);    cacheNodePair(thdAL3Ref);
            cacheNodePair(thdPpvL12Ref); cacheNodePair(thdPpvL23Ref); cacheNodePair(thdPpvL31Ref);
            cacheNodePair(kfL1Ref);      cacheNodePair(kfL2Ref);      cacheNodePair(kfL3Ref);
            cacheNodePair(thdOddAL1Ref); cacheNodePair(thdEvenAL1Ref);
            cacheNodePair(seqAposRef);   cacheNodePair(seqAnegRef);
            cacheNodePair(seqVposRef);   cacheNodePair(seqVnegRef);
            cacheNodePair(avgWRef);      cacheNodePair(maxWRef);      cacheNodePair(minWRef);
            cacheNodePair(avgVArRef);    cacheNodePair(avgVARef);
            if (harmonicArrayInModel) {
                for (int h = 0; h < 50; h++) {
                    cacheNodePair(haPhsAHarRef[h]);
                    cacheNodePair(haPhsBHarRef[h]);
                    cacheNodePair(haPhsCHarRef[h]);
                }
            }
        }
        cacheStNode(totWhRef);   cacheStNode(totVAhRef);  cacheStNode(totVArhRef);
        cacheStNode(supWhRef);   cacheStNode(supVArhRef);
        LOG.info("[" + config.getFeederId() + "] Node cache: " +
                 mxNodeCache.size() + " nodos MX, " + stNodeCache.size() + " nodos ST");

        LOG.info("MMXU base: " + mmxuBase + "  MHAI: " + mhaiBase +
                 "  harmonics=" + harmonicsAvailable +
                 "  harmonicArray=" + harmonicArrayInModel +
                 "  powerScale=" + config.getPowerScaleFactor());
    }

    /**
     * Descubre y cachea un nodo FC=MX en el modelo.
     * Soporta WYE/DEL/SEQ (ref.cVal.mag.f) y scalar MV (ref.mag.f).
     * Si el nodo no existe en el modelo, no se agrega al cache (fallback a slow path).
     */
    private void cacheNodePair(String ref) {
        if (ref == null || serverModel == null) return;

        // Intento 1: WYE/DEL/SEQ sub-element → ref.cVal.mag.f
        String cValRef = ref + ".cVal.mag.f";
        ModelNode leaf = serverModel.findModelNode(cValRef, Fc.MX);
        if (leaf == null) leaf = serverModel.findModelNode(cValRef, null);
        if (leaf != null) {
            ModelNode parent = serverModel.findModelNode(ref, Fc.MX);
            FcModelNode fcParent = (parent instanceof FcModelNode) ? (FcModelNode) parent
                                 : (leaf   instanceof FcModelNode) ? (FcModelNode) leaf
                                 : null;
            if (fcParent != null) mxNodeCache.put(ref, new NodePair(fcParent, leaf));
            return;
        }

        // Intento 2: scalar MV → ref.mag.f
        String magRef = ref + ".mag.f";
        leaf = serverModel.findModelNode(magRef, Fc.MX);
        if (leaf == null) leaf = serverModel.findModelNode(magRef, null);
        if (leaf != null) {
            ModelNode parent = serverModel.findModelNode(ref, Fc.MX);
            FcModelNode fcParent = (parent instanceof FcModelNode) ? (FcModelNode) parent
                                 : (leaf   instanceof FcModelNode) ? (FcModelNode) leaf
                                 : null;
            if (fcParent != null) mxNodeCache.put(ref, new NodePair(fcParent, leaf));
        }
    }

    /**
     * Descubre y cachea el nodo actVal (FC=ST, INT64) de un BCR de energía.
     */
    private void cacheStNode(String ref) {
        if (ref == null || serverModel == null) return;
        ModelNode node = serverModel.findModelNode(ref + ".actVal", Fc.ST);
        if (node != null) stNodeCache.put(ref, node);
    }

    /**
     * Determina automáticamente powerScaleFactor comparando el valor raw de TotW
     * contra la potencia aparente calculada desde V×I del propio IED.
     *
     * Lógica física:
     *   Si  rawW / (3·V·I)  ≈ FP  (0.01 – 1.10)  → IED reporta en W  → ps = 0.001
     *   Si  rawW·1000 / (3·V·I) ≈ FP (0.01 – 1.10) → IED reporta en kW → ps = 1.0
     *
     * Este método es inmune al valor de TotW.units.multiplier, que iec61850bean
     * puede rellenar con el valor por defecto (3=kilo) aunque el CID no lo defina.
     */
    private void autoDetectPowerScale() {
        if (serverModel == null || wRef == null
                || phsAPhVRef == null || phsAARef == null) return;
        try {
            float rawW = readMxFloat(wRef);
            float vPhsA = readMxFloat(phsAPhVRef);
            float iPhsA = readMxFloat(phsAARef);

            if (rawW <= 0 || vPhsA <= 0 || iPhsA <= 0) {
                config.setPowerScaleFactor(0.001);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: valores nulos → ps=0.001 (fallback)");
                return;
            }

            double sApparent = 3.0 * vPhsA * iPhsA;   // VA estimados
            double ratioIfW  = rawW / sApparent;        // ≈ FP si rawW está en W
            double ratioIfKW = rawW * 1000.0 / sApparent; // ≈ FP si rawW está en kW

            LOG.info(String.format("[%s] Auto-detect sanity: rawW=%.1f V=%.1f I=%.1f S=%.1f ratioW=%.4f ratioKW=%.4f",
                    config.getFeederId(), rawW, vPhsA, iPhsA, sApparent, ratioIfW, ratioIfKW));

            // FP razonable: [0.01, 1.10]
            boolean wPlausible  = ratioIfW  >= 0.01 && ratioIfW  <= 1.10;
            boolean kwPlausible = ratioIfKW >= 0.01 && ratioIfKW <= 1.10;

            if (wPlausible && !kwPlausible) {
                config.setPowerScaleFactor(0.001);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: IED reporta W → ps=0.001");
            } else if (kwPlausible && !wPlausible) {
                config.setPowerScaleFactor(1.0);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: IED reporta kW → ps=1.0");
            } else {
                // Ambiguo: elegir el ratio más cercano a un FP típico (0.85)
                double diffW  = Math.abs(ratioIfW  - 0.85);
                double diffKW = Math.abs(ratioIfKW - 0.85);
                if (diffW <= diffKW) {
                    config.setPowerScaleFactor(0.001);
                    LOG.info("[" + config.getFeederId() + "] Auto-detect (ambiguo, mejor W): ps=0.001");
                } else {
                    config.setPowerScaleFactor(1.0);
                    LOG.info("[" + config.getFeederId() + "] Auto-detect (ambiguo, mejor kW): ps=1.0");
                }
            }
        } catch (Exception e) {
            config.setPowerScaleFactor(0.001);
            LOG.info("[" + config.getFeederId() + "] Auto-detect error: " + e.getMessage() + " → ps=0.001");
        }
    }

    /**
     * Encuentra la primera referencia de medición (FC=MX) disponible en el modelo.
     */
    private String findMxRef(String base, String[] candidates) {
        if (serverModel != null) {
            for (String c : candidates) {
                String ref = base + "." + c;
                ModelNode node = serverModel.findModelNode(ref, Fc.MX);
                if (node != null) return ref;
            }
        }
        return base + "." + candidates[0];  // fallback
    }

    /**
     * Lee el atributo de medición analógica (FC=MX).
     * Soporta los siguientes patrones de DO/DA:
     *   - WYE/DEL/SEQ sub-element: ref = "...LN.DO.phsX"  → subnodo "cVal.mag.f"
     *   - Scalar MV:               ref = "...LN.DO"        → subnodo "mag.f"
     *
     * Estrategia: leer al nivel DO/SDO padre (no al nivel hoja BDA) para garantizar
     * que el servidor actualice el árbol de nodos completo en cada ciclo de polling.
     */
    private float readMxFloat(String ref) throws ServiceError, IOException {
        if (ref == null) return 0.0f;
        if (association == null || serverModel == null)
            throw new IOException("Conexión cerrada durante la lectura");

        // Fast path: nodo ya cacheado desde buildMmxuRefs() (patrón IEDNavigator nodeMap)
        NodePair pair = mxNodeCache.get(ref);
        if (pair != null) {
            try {
                association.getDataValues(pair.parent);
            } catch (ServiceError se) {
                // Fallback: leer hoja directamente si el padre falla
                if (pair.leaf instanceof FcModelNode)
                    association.getDataValues((FcModelNode) pair.leaf);
            }
            if (pair.leaf instanceof BdaFloat32) return ((BdaFloat32) pair.leaf).getFloat();
            if (pair.leaf instanceof BdaFloat64) return (float)((double) ((BdaFloat64) pair.leaf).getDouble());
            return 0.0f;
        }

        // Slow path: nodo no cacheado (primera lectura o ref dinámica)
        // Caso 1: WYE/DEL/SEQ sub-element → ref = "...LN.DO.phsX"
        // Detectar si existe cVal.mag.f bajo este nodo
        String cValMagFRef = ref + ".cVal.mag.f";
        ModelNode cValMagF = serverModel.findModelNode(cValMagFRef, Fc.MX);
        if (cValMagF == null) cValMagF = serverModel.findModelNode(cValMagFRef, null);
        if (cValMagF != null) {
            // Leer al nivel del nodo CMV/SEQ (ref = phsX) para actualizar cVal, q, t a la vez
            ModelNode phsNode = serverModel.findModelNode(ref, Fc.MX);
            if (phsNode instanceof FcModelNode) {
                try {
                    association.getDataValues((FcModelNode) phsNode);
                } catch (ServiceError se) {
                    // Fallback: leer el nodo hoja directamente si el padre falla
                    if (cValMagF instanceof FcModelNode) {
                        association.getDataValues((FcModelNode) cValMagF);
                    }
                }
            } else if (cValMagF instanceof FcModelNode) {
                association.getDataValues((FcModelNode) cValMagF);
            }
            if (cValMagF instanceof BdaFloat32) return ((BdaFloat32) cValMagF).getFloat();
            if (cValMagF instanceof BdaFloat64) return (float)((double) ((BdaFloat64) cValMagF).getDouble());
            return 0.0f;
        }

        // Caso 2: Scalar MV → ref = "...LN.DO" (Hz, TotW, TotPF, AvW, etc.)
        // Also handles HAR50_t DAs: HarA.h01 → h01 has fc=MX but BDA f has no explicit FC.
        String magFRef = ref + ".mag.f";
        ModelNode magF = serverModel.findModelNode(magFRef, Fc.MX);
        if (magF == null) magF = serverModel.findModelNode(magFRef, null);
        if (magF != null) {
            // Leer al nivel del DO MV para actualizar mag, q, t a la vez
            ModelNode mvNode = serverModel.findModelNode(ref, Fc.MX);
            if (mvNode instanceof FcModelNode) {
                try {
                    association.getDataValues((FcModelNode) mvNode);
                } catch (ServiceError se) {
                    // Fallback: leer el nodo hoja directamente
                    if (magF instanceof FcModelNode) {
                        association.getDataValues((FcModelNode) magF);
                    }
                }
            } else if (magF instanceof FcModelNode) {
                association.getDataValues((FcModelNode) magF);
            }
            if (magF instanceof BdaFloat32) return ((BdaFloat32) magF).getFloat();
            if (magF instanceof BdaFloat64) return (float)((double) ((BdaFloat64) magF).getDouble());
        }

        return 0.0f;
    }

    /**
     * Lee actVal (INT64) de un nodo BCR (FC=ST) — para contadores de energía MMTR.
     * El DO referenciado es p.ej. "cbo2LD0/MMTR1.TotWh" y el DA es ".actVal" FC=ST.
     */
    private long readStInt64(String ref) throws ServiceError, IOException {
        if (ref == null || association == null || serverModel == null) return 0L;

        // Fast path: nodo ya cacheado desde buildMmxuRefs()
        ModelNode cached = stNodeCache.get(ref);
        if (cached instanceof FcModelNode) {
            association.getDataValues((FcModelNode) cached);
            if (cached instanceof BdaInt64) return ((BdaInt64) cached).getValue();
            if (cached instanceof BdaInt32) return ((BdaInt32) cached).getValue();
            return 0L;
        }

        // Slow path: buscar en modelo (ref no cacheada)
        String actValRef = ref + ".actVal";
        ModelNode node = serverModel.findModelNode(actValRef, Fc.ST);
        if (node instanceof FcModelNode) {
            association.getDataValues((FcModelNode) node);
            if (node instanceof BdaInt64) return ((BdaInt64) node).getValue();
            if (node instanceof BdaInt32) return ((BdaInt32) node).getValue();
        }
        return 0L;
    }

    /**
     * Lee un canal del array de armónicos HA (phsXHar01..phsXHar50).
     * Cada elemento es un SE_Cmv3 con cVal.mag.f (FC=MX).
     * Retorna double[50]: índice 0=H1 (fundamental), 1=H2, ..., 49=H50.
     */
    private double[] readHarmonicArray(String[] harRefs) {
        double[] result = new double[50];
        if (association == null || serverModel == null) return result;
        for (int i = 0; i < 50 && i < harRefs.length; i++) {
            try {
                result[i] = readMxFloat(harRefs[i]);
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Vuelca en el log la estructura de nodos bajo mhaiBase para diagnostico.
     * Permite descubrir la ruta real de los armonicos en el modelo del IED.
     */
    private void dumpMhaiStructure(String mhaiBase) {
        if (serverModel == null) return;
        ModelNode mhai = serverModel.findModelNode(mhaiBase, null);
        if (mhai == null) {
            LOG.warning("MHAI no encontrado en modelo: " + mhaiBase);
            return;
        }
        StringBuilder sb = new StringBuilder("Estructura MHAI [" + mhaiBase + "]:\n");
        dumpNode(mhai, "  ", sb, 0);
        LOG.info(sb.toString());
    }

    private void dumpNode(ModelNode node, String indent, StringBuilder sb, int depth) {
        if (depth > 4) return;  // limitar profundidad
        if (node instanceof LogicalNode || node instanceof FcDataObject || node instanceof FcModelNode) {
            for (ModelNode child : node) {
                sb.append(indent).append(child.getName());
                if (child instanceof FcModelNode) {
                    sb.append(" [FC=").append(((FcModelNode) child).getFc()).append("]");
                }
                sb.append("\n");
                dumpNode(child, indent + "  ", sb, depth + 1);
            }
        }
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
