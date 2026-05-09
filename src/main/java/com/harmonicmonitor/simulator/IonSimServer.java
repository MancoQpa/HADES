package com.harmonicmonitor.simulator;

import com.beanit.iec61850bean.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Servidor IEC 61850 de escritorio que simula un ION 7400.
 *
 * Carga un CID, inicia ServerSap en el puerto indicado y actualiza
 * periódicamente los valores con ruido gaussiano sobre el perfil elegido.
 */
public class IonSimServer {

    private static final Logger LOG = Logger.getLogger(IonSimServer.class.getName());

    private ServerSap    serverSap;
    private ServerModel  serverModel;
    private ScheduledExecutorService scheduler;

    // Configuración
    private String iedName;
    private String ldInst;
    private String mmxuPrefix;
    private float  noiseFactor;   // e.g. 0.03 = ±3 %
    private String cidPath;       // guardado para recargar perfil en caliente

    private volatile boolean setValuesWarnedOnce = false; // evitar spam en log

    // Perfil cargado (volatile para cambio desde hilo stdin)
    private volatile Profile profile;

    // Acumuladores de energía (se incrementan cada ciclo)
    private long totWhAcc;
    private long totVAhAcc;
    private long totVArhAcc;
    private long supWhAcc;
    private long supVArhAcc;

    private final Random rnd = new Random();

    // ── API pública ───────────────────────────────────────────────────────────

    public void start(String cidPath, int port, String iedName, String ldInst,
                      String mmxuPrefix, String profileName,
                      float noiseFactor, int intervalMs) throws Exception {

        this.iedName      = iedName;
        this.ldInst       = ldInst;
        this.mmxuPrefix   = mmxuPrefix;
        this.noiseFactor  = noiseFactor;
        this.cidPath      = cidPath;

        // 1. Cargar perfil
        profile = SimProfileLoader.load(cidPath, profileName);
        totWhAcc   = profile.totWh;
        totVAhAcc  = profile.totVAh;
        totVArhAcc = profile.totVArh;
        supWhAcc   = profile.supWh;
        supVArhAcc = profile.supVArh;

        // 2. Parsear CID (sustituyendo el IED name si difiere de "SIM1")
        serverModel = parseCid(cidPath, iedName);
        LOG.info("Modelo cargado: " + serverModel.getChildren().size() + " LD(s)");

        // 3. Crear y arrancar ServerSap
        serverSap = new ServerSap(port, 0, null, serverModel, null);
        serverSap.startListening(null);
        LOG.info("Servidor IEC 61850 escuchando en puerto " + port);

        // 4. Aplicar valores iniciales
        applyProfile();

        // 5. Hilo lector de comandos stdin (SET_PROFILE:nombre)
        Thread stdinReader = new Thread(this::readStdinCommands, "StdinCmd-" + iedName);
        stdinReader.setDaemon(true);
        stdinReader.start();

        // 6. Ciclo de actualización periódico
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SimUpdater-" + iedName);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::updateCycle,
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        if (serverSap  != null) serverSap.stop();
        LOG.info("Simulador detenido: " + iedName);
    }

    // ── Comandos stdin ────────────────────────────────────────────────────────

    /** Lee líneas de stdin. Formato: SET_PROFILE:<nombre> */
    private void readStdinCommands() {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("SET_PROFILE:")) {
                    String newName = line.substring(12).trim();
                    try {
                        Profile newProfile = SimProfileLoader.load(cidPath, newName);
                        profile = newProfile;  // swap atómico (volatile)
                        LOG.info("[" + iedName + "] Perfil cambiado a: " + newName);
                    } catch (Exception e) {
                        LOG.warning("[" + iedName + "] Error cargando perfil " + newName + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    // ── Ciclo de actualización ────────────────────────────────────────────────

    private void updateCycle() {
        try {
            applyProfile();
        } catch (Exception e) {
            LOG.warning("Error en ciclo de actualización: " + e.getMessage());
        }
    }

    /** Escribe todos los valores del perfil (con ruido) en el ServerModel. */
    private void applyProfile() {
        List<BasicDataAttribute> changed = new ArrayList<>();

        String ld  = iedName + ldInst + "/";
        String mmx = ld + mmxuPrefix + "MMXU1.";
        String mhai = ld + "MHAI1.";
        String msqi = ld + "MSQI1.";
        String mmtr = ld + "MMTR1.";
        String msta = ld + "MSTA1.";

        // ── MMXU: tensiones (WYE cVal.mag.f) ────────────────────────────────
        setMx(mmx + "PhV.phsA.cVal.mag.f", n(profile.phVL1), changed);
        setMx(mmx + "PhV.phsB.cVal.mag.f", n(profile.phVL2), changed);
        setMx(mmx + "PhV.phsC.cVal.mag.f", n(profile.phVL3), changed);

        // ── MMXU: corrientes ──────────────────────────────────────────────
        setMx(mmx + "A.phsA.cVal.mag.f", n(profile.aL1), changed);
        setMx(mmx + "A.phsB.cVal.mag.f", n(profile.aL2), changed);
        setMx(mmx + "A.phsC.cVal.mag.f", n(profile.aL3), changed);

        // ── MMXU: potencias / FP / Hz ────────────────────────────────────
        setMx(mmx + "TotW.mag.f",   n(profile.totW),   changed);
        setMx(mmx + "TotVAr.mag.f", n(profile.totVAr), changed);
        setMx(mmx + "TotVA.mag.f",  n(profile.totVA),  changed);
        setMx(mmx + "TotPF.mag.f",  n(profile.totPF),  changed);
        setMx(mmx + "Hz.mag.f",     n(profile.hz),     changed);

        // ── MHAI: THD corriente ───────────────────────────────────────────
        setMx(mhai + "ThdA.phsA.cVal.mag.f", n(profile.thdAL1), changed);
        setMx(mhai + "ThdA.phsB.cVal.mag.f", n(profile.thdAL2), changed);
        setMx(mhai + "ThdA.phsC.cVal.mag.f", n(profile.thdAL3), changed);

        // ── MHAI: THD tensión línea (DEL) ─────────────────────────────────
        setMx(mhai + "ThdPPV.phsAB.cVal.mag.f", n(profile.thdPpvL12), changed);
        setMx(mhai + "ThdPPV.phsBC.cVal.mag.f", n(profile.thdPpvL23), changed);
        setMx(mhai + "ThdPPV.phsCA.cVal.mag.f", n(profile.thdPpvL31), changed);

        // ── MHAI: K-factor ────────────────────────────────────────────────
        setMx(mhai + "HKf.phsA.cVal.mag.f", n(profile.hKfL1), changed);
        setMx(mhai + "HKf.phsB.cVal.mag.f", n(profile.hKfL2), changed);
        setMx(mhai + "HKf.phsC.cVal.mag.f", n(profile.hKfL3), changed);

        // ── MHAI: THD impar/par ───────────────────────────────────────────
        setMx(mhai + "ThdOddA.phsA.cVal.mag.f", n(profile.thdOddA), changed);
        setMx(mhai + "ThdEvnA.phsA.cVal.mag.f", n(profile.thdEvnA), changed);

        // ── MHAI: espectro armónico HarA/HarB/HarC (HAR50_t, DAs únicos h01..h50) ─
        // h01=H1 (fundamental), h02=H2, ..., h50=H50
        // Ruta: MHAI1.HarA.h03.mag.f  (H3 fase A)
        for (int k = 1; k <= 50; k++) {
            String hn = String.format("h%02d", k);
            float ha = k <= profile.harA.length ? profile.harA[k - 1] : 0f;
            float hb = k <= profile.harB.length ? profile.harB[k - 1] : 0f;
            float hc = k <= profile.harC.length ? profile.harC[k - 1] : 0f;
            setMx(mhai + "HarA." + hn + ".mag.f", n(ha), changed);
            setMx(mhai + "HarB." + hn + ".mag.f", n(hb), changed);
            setMx(mhai + "HarC." + hn + ".mag.f", n(hc), changed);
        }

        // ── MSQI: componentes simétricas ──────────────────────────────────
        setMx(msqi + "SeqA.c1.cVal.mag.f", n(profile.seqAPos),  changed);
        setMx(msqi + "SeqA.c2.cVal.mag.f", n(profile.seqANeg),  changed);
        setMx(msqi + "SeqA.c3.cVal.mag.f", n(profile.seqAZero), changed);
        setMx(msqi + "SeqV.c1.cVal.mag.f", n(profile.seqVPos),  changed);
        setMx(msqi + "SeqV.c2.cVal.mag.f", n(profile.seqVNeg),  changed);

        // ── MMTR: energía acumulada (se incrementa cada ciclo) ────────────
        long dWh  = (long)(profile.avW  / 3600.0 + 0.5);  // Wh por segundo ~ W/3600
        long dVAh = (long)(profile.avVA / 3600.0 + 0.5);
        long dVArh= (long)(Math.abs(profile.avVAr) / 3600.0 + 0.5);
        totWhAcc   += dWh;
        totVAhAcc  += dVAh;
        totVArhAcc += dVArh;
        supWhAcc   += dWh;
        supVArhAcc += dVArh;

        setSt(mmtr + "TotWh.actVal",   totWhAcc,   changed);
        setSt(mmtr + "TotVAh.actVal",  totVAhAcc,  changed);
        setSt(mmtr + "TotVArh.actVal", totVArhAcc, changed);
        setSt(mmtr + "SupWh.actVal",   supWhAcc,   changed);
        setSt(mmtr + "SupVArh.actVal", supVArhAcc, changed);

        // ── MSTA: demanda ──────────────────────────────────────────────────
        setMx(msta + "AvW.mag.f",   n(profile.avW),   changed);
        setMx(msta + "MaxW.mag.f",  n(profile.maxW),  changed);
        setMx(msta + "MinW.mag.f",  n(profile.minW),  changed);
        setMx(msta + "AvVAr.mag.f", n(profile.avVAr), changed);
        setMx(msta + "AvVA.mag.f",  n(profile.avVA),  changed);

        // Publicar todos los valores cambiados al buffer activo del ServerSap.
        // Necesario en iec61850bean incluso para polling: setFloat() escribe en el
        // buffer pendiente, pero setValues() lo promueve al buffer que responde
        // a GetDataValues. Sin este llamado el cliente siempre recibe el valor inicial.
        if (!changed.isEmpty()) {
            try {
                serverSap.setValues(changed);
            } catch (Exception e) {
                // En CIDs sin ReportControl, bdaMirror=null causa esta excepcion.
                // Los valores escritos con setFloat() siguen siendo servidos a clientes
                // MMS en respuesta a GetDataValues (iec61850bean lee del modelo directamente).
                if (!setValuesWarnedOnce) {
                    LOG.warning("setValues: " + e.getMessage() +
                        " — sin ReportControl en CID; valores siguen siendo legibles por polling");
                    setValuesWarnedOnce = true;
                }
            }
        }
    }

    // ── Helpers de escritura en modelo ────────────────────────────────────────

    private void setMx(String ref, float value, List<BasicDataAttribute> out) {
        ModelNode node = serverModel.findModelNode(ref, Fc.MX);
        if (node instanceof BdaFloat32) {
            ((BdaFloat32) node).setFloat(value);
            out.add((BdaFloat32) node);
        }
    }

    private void setSt(String ref, long value, List<BasicDataAttribute> out) {
        ModelNode node = serverModel.findModelNode(ref, Fc.ST);
        if (node instanceof BdaInt64) {
            ((BdaInt64) node).setValue(value);
            out.add((BdaInt64) node);
        }
    }

    /** Aplica ruido gaussiano (sigma = noiseFactor/3) al valor. */
    private float n(float base) {
        if (noiseFactor == 0 || base == 0) return base;
        float noisy = base * (1f + (float)(rnd.nextGaussian() * noiseFactor / 3.0));
        // Mantener el signo original
        return base >= 0 ? Math.max(0, noisy) : Math.min(0, noisy);
    }

    // ── Parseo del CID ────────────────────────────────────────────────────────

    private ServerModel parseCid(String cidPath, String targetIedName) throws Exception {
        String cid = Files.readString(Path.of(cidPath), StandardCharsets.UTF_8);

        // Sustituir IED name si el CID tiene "SIM1" y se pide otro
        if (!targetIedName.equals("SIM1")) {
            cid = cid.replace("name=\"SIM1\"", "name=\"" + targetIedName + "\"");
        }

        try (InputStream is = new ByteArrayInputStream(cid.getBytes(StandardCharsets.UTF_8))) {
            List<ServerModel> models = SclParser.parse(is);
            if (models == null || models.isEmpty()) {
                throw new IOException("El CID no contiene ningún modelo de servidor");
            }
            return models.get(0);
        }
    }

}
