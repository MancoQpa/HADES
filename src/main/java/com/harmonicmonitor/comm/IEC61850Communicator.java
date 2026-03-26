package com.harmonicmonitor.comm;

import com.beanit.iec61850bean.*;
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

        // Primer ciclo: auto-detectar escala de potencia (conexión ya estable)
        if (!powerScaleDetected) {
            autoDetectPowerScale();
            powerScaleDetected = true;
        }

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
            m.setPowerFactor(readMxFloat(pfRef) * pfs);
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

        // Array de armonicos HA — ION 7400 usa convencion:
        //   indice 0 = DC (H0, Float.MAX_VALUE = no aplicable)
        //   indice 1 = H1 (fundamental, per-unit = 1.0)
        //   indice 2 = H2 (per-unit respecto al fundamental)
        //   ...
        // Por eso mapeamos: haPhsAHarRef[n] -> phsAHar.(n+1), donde n=0 es H1
        for (int h = 0; h < 50; h++) {
            haPhsAHarRef[h] = mhaiBase + ".HA.phsAHar." + (h + 1);  // h=0 -> H1 (ion index 1)
            haPhsBHarRef[h] = mhaiBase + ".HA.phsBHar." + (h + 1);
            haPhsCHarRef[h] = mhaiBase + ".HA.phsCHar." + (h + 1);
        }

        // Verificar disponibilidad de MHAI en el modelo
        harmonicsAvailable = (serverModel != null &&
            serverModel.findModelNode(thdAL1Ref, Fc.MX) != null);

        // Verificar si el array HA existe en el modelo y descubrir su estructura real
        if (serverModel != null && harmonicsAvailable) {
            // Probar variantes de ruta conocidas para el array de armonicos
            String[] harVariants = {
                mhaiBase + ".HA.phsAHar.1.cVal.mag.f",    // ION 7400: H1 en indice 1 (0=DC)
                mhaiBase + ".HA.phsAHar.0.cVal.mag.f",    // alternativa indice 0 directo
                mhaiBase + ".HA.phsAHar01.cVal.mag.f",    // alternativa con sufijo 01
            };
            String workingPattern = null;
            for (String variant : harVariants) {
                if (serverModel.findModelNode(variant, Fc.MX) != null) {
                    workingPattern = variant;
                    break;
                }
            }
            harmonicArrayInModel = (workingPattern != null);
            LOG.info("HA array en modelo: " + harmonicArrayInModel +
                     (workingPattern != null ? "  patron: " + workingPattern : "  (no encontrado)"));

            // Si no se encontro con variantes conocidas, volcar los primeros DOs de MHAI para diagnostico
            if (!harmonicArrayInModel) {
                dumpMhaiStructure(mhaiBase);
            }
        }

        LOG.info("MMXU base: " + mmxuBase + "  MHAI: " + mhaiBase +
                 "  harmonics=" + harmonicsAvailable +
                 "  harmonicArray=" + harmonicArrayInModel +
                 "  powerScale=" + config.getPowerScaleFactor());
    }

    /**
     * Lee TotW.units.multiplier del modelo para determinar automáticamente powerScaleFactor.
     *   multiplier == 3  → IED reporta en kW  → ps = 1.0
     *   multiplier == 0  → IED reporta en W   → ps = 0.001
     *   otro valor       → deja el valor configurado sin cambios
     */
    private void autoDetectPowerScale() {
        if (serverModel == null || wRef == null) return;
        String multRef = wRef + ".units.multiplier";
        for (Fc fc : new Fc[]{Fc.CF, Fc.EX, Fc.MX}) {
            try {
                ModelNode multNode = serverModel.findModelNode(multRef, fc);
                if (multNode instanceof FcModelNode) {
                    association.getDataValues((FcModelNode) multNode);
                    if (multNode instanceof BdaInt8) {
                        byte mult = ((BdaInt8) multNode).getValue();
                        LOG.info("[" + config.getFeederId() + "] TotW.units.multiplier=" + mult + " (FC=" + fc + ")");
                        if (mult == 3) {
                            config.setPowerScaleFactor(1.0);
                            LOG.info("[" + config.getFeederId() + "] Auto-detect: powerScaleFactor=1.0 (IED reporta kW)");
                        } else if (mult == 0) {
                            config.setPowerScaleFactor(0.001);
                            LOG.info("[" + config.getFeederId() + "] Auto-detect: powerScaleFactor=0.001 (IED reporta W)");
                        } else {
                            LOG.info("[" + config.getFeederId() + "] Auto-detect: multiplier=" + mult + " no reconocido, manteniendo ps=" + config.getPowerScaleFactor());
                        }
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }
        LOG.info("[" + config.getFeederId() + "] TotW.units.multiplier no disponible, ps=" + config.getPowerScaleFactor());
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

        // Caso 1: WYE/DEL/SEQ sub-element → ref = "...LN.DO.phsX"
        // Detectar si existe cVal.mag.f bajo este nodo
        String cValMagFRef = ref + ".cVal.mag.f";
        ModelNode cValMagF = serverModel.findModelNode(cValMagFRef, Fc.MX);
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
        // Detectar si existe mag.f bajo este nodo
        String magFRef = ref + ".mag.f";
        ModelNode magF = serverModel.findModelNode(magFRef, Fc.MX);
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
