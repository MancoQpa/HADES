package com.harmonicmonitor.simulator;

import com.beanit.iec61850bean.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.*;

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

    // Perfil cargado (volatile para cambio desde hilo stdin)
    private volatile Profile profile;

    // Acumuladores de energía (se incrementan cada ciclo)
    private long totWhAcc;
    private long totVAhAcc;
    private long totVArhAcc;
    private long supWhAcc;
    private long supVArhAcc;

    private final Random rnd = new Random();

    // ── Modelo de perfil ──────────────────────────────────────────────────────

    static class Profile {
        String name;
        float phVL1, phVL2, phVL3;
        float aL1, aL2, aL3;
        float totW, totVAr, totVA, totPF, hz;
        float thdAL1, thdAL2, thdAL3;
        float thdPpvL12, thdPpvL23, thdPpvL31;
        float hKfL1, hKfL2, hKfL3;
        float thdOddA, thdEvnA;
        float[] harA = new float[50];
        float[] harB = new float[50];
        float[] harC = new float[50];
        float seqAPos, seqANeg, seqAZero, seqVPos, seqVNeg;
        long  totWh, totVAh, totVArh, supWh, supVArh;
        float avW, maxW, minW, avVAr, avVA;
    }

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
        profile = loadProfile(cidPath, profileName);
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
                        Profile newProfile = loadProfile(cidPath, newName);
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

        // ── MHAI: espectro armónico ───────────────────────────────────────
        for (int k = 1; k <= 50; k++) {
            String suf = String.format("%02d", k);
            float ha = k <= profile.harA.length ? profile.harA[k - 1] : 0f;
            float hb = k <= profile.harB.length ? profile.harB[k - 1] : 0f;
            float hc = k <= profile.harC.length ? profile.harC[k - 1] : 0f;
            setMx(mhai + "HA.phsAHar" + suf + ".cVal.mag.f", n(ha), changed);
            setMx(mhai + "HA.phsBHar" + suf + ".cVal.mag.f", n(hb), changed);
            setMx(mhai + "HA.phsCHar" + suf + ".cVal.mag.f", n(hc), changed);
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
                LOG.warning("setValues error: " + e.getMessage());
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

    // ── Carga de perfil desde JSON ────────────────────────────────────────────

    /**
     * Busca el JSON en:
     *  1. simulator/templates/<profile>.json  (relativo al cwd)
     *  2. El directorio del CID
     * Si no lo encuentra usa crypto_mining embebido como fallback.
     */
    private Profile loadProfile(String cidPath, String profileName) throws IOException {
        String fileName = profileName + ".json";

        // Candidatos de ruta
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("simulator", "templates", fileName));
        candidates.add(Path.of(cidPath).getParent().resolve("templates").resolve(fileName));

        String json = null;
        for (Path p : candidates) {
            if (Files.exists(p)) {
                json = Files.readString(p, StandardCharsets.UTF_8);
                LOG.info("Perfil cargado desde: " + p);
                break;
            }
        }

        if (json == null) {
            LOG.warning("No se encontró " + fileName + " — usando perfil crypto_mining embebido");
            json = FALLBACK_CRYPTO_JSON;
        }

        return parseProfileJson(json);
    }

    private Profile parseProfileJson(String json) {
        // Extraer bloque "values": { ... }
        int start = json.indexOf("\"values\"");
        if (start >= 0) {
            int brace = json.indexOf('{', start);
            // Encontrar llave de cierre
            int depth = 0, end = brace;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) break; }
                end++;
            }
            json = json.substring(brace, end + 1);
        }

        Profile p = new Profile();
        p.name       = getStr(json, "name", "unknown");
        p.phVL1      = getF(json, "phVL1",  13280f);
        p.phVL2      = getF(json, "phVL2",  13280f);
        p.phVL3      = getF(json, "phVL3",  13280f);
        p.aL1        = getF(json, "aL1",    100f);
        p.aL2        = getF(json, "aL2",    100f);
        p.aL3        = getF(json, "aL3",    100f);
        p.totW       = getF(json, "totW",   3000000f);
        p.totVAr     = getF(json, "totVAr", 800000f);
        p.totVA      = getF(json, "totVA",  3100000f);
        p.totPF      = getF(json, "totPF",  0.96f);
        p.hz         = getF(json, "hz",     50f);
        p.thdAL1     = getF(json, "thdAL1", 5f);
        p.thdAL2     = getF(json, "thdAL2", 5f);
        p.thdAL3     = getF(json, "thdAL3", 5f);
        p.thdPpvL12  = getF(json, "thdPpvL12", 2f);
        p.thdPpvL23  = getF(json, "thdPpvL23", 2f);
        p.thdPpvL31  = getF(json, "thdPpvL31", 2f);
        p.hKfL1      = getF(json, "hKfL1",  1.5f);
        p.hKfL2      = getF(json, "hKfL2",  1.5f);
        p.hKfL3      = getF(json, "hKfL3",  1.5f);
        p.thdOddA    = getF(json, "thdOddA", 4.8f);
        p.thdEvnA    = getF(json, "thdEvnA", 0.8f);
        p.harA       = getArr(json, "harA", 50);
        p.harB       = getArr(json, "harB", 50);
        p.harC       = getArr(json, "harC", 50);
        p.seqAPos    = getF(json, "seqAPos",  100f);
        p.seqANeg    = getF(json, "seqANeg",  1f);
        p.seqAZero   = getF(json, "seqAZero", 0.2f);
        p.seqVPos    = getF(json, "seqVPos",  13280f);
        p.seqVNeg    = getF(json, "seqVNeg",  50f);
        p.totWh      = getL(json, "totWh",    10000000L);
        p.totVAh     = getL(json, "totVAh",   10400000L);
        p.totVArh    = getL(json, "totVArh",  2800000L);
        p.supWh      = getL(json, "supWh",    9000000L);
        p.supVArh    = getL(json, "supVArh",  2500000L);
        p.avW        = getF(json, "avW",   p.totW);
        p.maxW       = getF(json, "maxW",  p.totW * 1.05f);
        p.minW       = getF(json, "minW",  p.totW * 0.95f);
        p.avVAr      = getF(json, "avVAr", p.totVAr);
        p.avVA       = getF(json, "avVA",  p.totVA);
        return p;
    }

    // ── Mini parseador JSON para el formato de los templates ─────────────────

    private static float getF(String json, String key, float def) {
        Pattern pat = Pattern.compile("\"" + key + "\"\\s*:\\s*([+-]?[\\d.]+)");
        Matcher m = pat.matcher(json);
        return m.find() ? Float.parseFloat(m.group(1)) : def;
    }

    private static long getL(String json, String key, long def) {
        Pattern pat = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = pat.matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : def;
    }

    private static String getStr(String json, String key, String def) {
        Pattern pat = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = pat.matcher(json);
        return m.find() ? m.group(1) : def;
    }

    private static float[] getArr(String json, String key, int size) {
        Pattern pat = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]+)\\]");
        Matcher m = pat.matcher(json);
        if (!m.find()) return new float[size];
        String[] parts = m.group(1).split(",");
        float[] arr = new float[Math.max(size, parts.length)];
        for (int i = 0; i < parts.length; i++) {
            try { arr[i] = Float.parseFloat(parts[i].trim()); } catch (NumberFormatException ignored) {}
        }
        return arr;
    }

    // ── Perfil embebido de fallback (crypto mining) ───────────────────────────

    private static final String FALLBACK_CRYPTO_JSON =
        "{\"phVL1\":13280.0,\"phVL2\":13280.0,\"phVL3\":13280.0," +
        "\"aL1\":170.0,\"aL2\":170.0,\"aL3\":170.0," +
        "\"totW\":6671208.0,\"totVAr\":1167124.0,\"totVA\":6772800.0,\"totPF\":0.985,\"hz\":50.0," +
        "\"thdAL1\":42.0,\"thdAL2\":42.0,\"thdAL3\":42.0," +
        "\"thdPpvL12\":4.5,\"thdPpvL23\":4.5,\"thdPpvL31\":4.5," +
        "\"hKfL1\":6.8,\"hKfL2\":6.8,\"hKfL3\":6.8," +
        "\"thdOddA\":41.5,\"thdEvnA\":1.2," +
        "\"harA\":[1.0,0.0,0.03,0.0,0.35,0.0,0.22,0.0,0.08,0.0,0.07,0.0,0.05,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]," +
        "\"harB\":[1.0,0.0,0.03,0.0,0.35,0.0,0.22,0.0,0.08,0.0,0.07,0.0,0.05,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]," +
        "\"harC\":[1.0,0.0,0.03,0.0,0.35,0.0,0.22,0.0,0.08,0.0,0.07,0.0,0.05,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]," +
        "\"seqAPos\":170.0,\"seqANeg\":1.0,\"seqAZero\":0.2,\"seqVPos\":13280.0,\"seqVNeg\":55.0," +
        "\"totWh\":35000000,\"totVAh\":35540000,\"totVArh\":6100000,\"supWh\":31500000,\"supVArh\":5500000," +
        "\"avW\":6671208.0,\"maxW\":7000000.0,\"minW\":6400000.0,\"avVAr\":1167124.0,\"avVA\":6772800.0}";
}
