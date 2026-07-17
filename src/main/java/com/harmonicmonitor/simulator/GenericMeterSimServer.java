package com.harmonicmonitor.simulator;

import com.beanit.iec61850bean.*;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * Servidor IEC 61850 de escritorio que simula un multimedidor genérico.
 *
 * Carga un CID, inicia ServerSap en el puerto indicado y actualiza
 * periódicamente los valores con ruido gaussiano sobre el perfil elegido.
 */
public class GenericMeterSimServer {

    private static final Logger LOG = Logger.getLogger(GenericMeterSimServer.class.getName());

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
        // setValues() exige BDAs de la copia interna del ServerSap (con bdaMirror
        // enlazado); escribir sobre el modelo parseado original lanza NPE.
        serverModel = serverSap.getModelCopy();
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

        // ── MMXU: tensiones fase-neutro ───────────────────────────────────
        setMx(mmx + "PhV.phsA", n(profile.phVL1), changed);
        setMx(mmx + "PhV.phsB", n(profile.phVL2), changed);
        setMx(mmx + "PhV.phsC", n(profile.phVL3), changed);

        // ── MMXU: tensiones línea-línea (≈ √3·fase-neutro); si el CID no
        //    modela el DO PPV (p.ej. el CID aplanado), setMx lo omite ──────
        final float SQRT3 = 1.7320508f;
        setMx(mmx + "PPV.phsAB", n(profile.phVL1) * SQRT3, changed);
        setMx(mmx + "PPV.phsBC", n(profile.phVL2) * SQRT3, changed);
        setMx(mmx + "PPV.phsCA", n(profile.phVL3) * SQRT3, changed);

        // ── MMXU: corrientes ──────────────────────────────────────────────
        setMx(mmx + "A.phsA", n(profile.aL1), changed);
        setMx(mmx + "A.phsB", n(profile.aL2), changed);
        setMx(mmx + "A.phsC", n(profile.aL3), changed);

        // ── MMXU: potencias / FP / Hz (MV, mag = BdaFloat32 directo) ────
        setMx(mmx + "TotW.mag",   n(profile.totW),   changed);
        setMx(mmx + "TotVAr.mag", n(profile.totVAr), changed);
        setMx(mmx + "TotVA.mag",  n(profile.totVA),  changed);
        setMx(mmx + "TotPF.mag",  n(profile.totPF),  changed);
        setMx(mmx + "Hz.mag",     n(profile.hz),     changed);

        // ── MHAI: THD corriente ───────────────────────────────────────────
        setMx(mhai + "ThdA.phsA", n(profile.thdAL1), changed);
        setMx(mhai + "ThdA.phsB", n(profile.thdAL2), changed);
        setMx(mhai + "ThdA.phsC", n(profile.thdAL3), changed);

        // ── MHAI: THD tensión línea (DEL) ─────────────────────────────────
        setMx(mhai + "ThdPPV.phsAB", n(profile.thdPpvL12), changed);
        setMx(mhai + "ThdPPV.phsBC", n(profile.thdPpvL23), changed);
        setMx(mhai + "ThdPPV.phsCA", n(profile.thdPpvL31), changed);

        // ── MHAI: K-factor ────────────────────────────────────────────────
        setMx(mhai + "HKf.phsA", n(profile.hKfL1), changed);
        setMx(mhai + "HKf.phsB", n(profile.hKfL2), changed);
        setMx(mhai + "HKf.phsC", n(profile.hKfL3), changed);

        // ── MHAI: THD impar/par ───────────────────────────────────────────
        setMx(mhai + "ThdOddA.phsA", n(profile.thdOddA), changed);
        setMx(mhai + "ThdEvnA.phsA", n(profile.thdEvnA), changed);

        // ── MSQI: componentes simétricas ──────────────────────────────────
        setMx(msqi + "SeqA.c1", n(profile.seqAPos),  changed);
        setMx(msqi + "SeqA.c2", n(profile.seqANeg),  changed);
        setMx(msqi + "SeqA.c3", n(profile.seqAZero), changed);
        setMx(msqi + "SeqV.c1", n(profile.seqVPos),  changed);
        setMx(msqi + "SeqV.c2", n(profile.seqVNeg),  changed);

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
        setMx(msta + "AvW.mag",   n(profile.avW),   changed);
        setMx(msta + "MaxW.mag",  n(profile.maxW),  changed);
        setMx(msta + "MinW.mag",  n(profile.minW),  changed);
        setMx(msta + "AvVAr.mag", n(profile.avVAr), changed);
        setMx(msta + "AvVA.mag",  n(profile.avVA),  changed);

        // Publicar todos los valores cambiados al buffer activo del ServerSap.
        // Necesario en iec61850bean incluso para polling: setFloat() escribe en el
        // buffer pendiente, pero setValues() lo promueve al buffer que responde
        // a GetDataValues. Sin este llamado el cliente siempre recibe el valor inicial.
        if (!changed.isEmpty()) {
            try {
                serverSap.setValues(changed);
            } catch (Exception e) {
                // Loguear SIEMPRE para diagnóstico (sin setValuesWarnedOnce)
                LOG.warning("[setValues ERROR] " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
                if (e.getCause() != null)
                    LOG.warning("[setValues CAUSE] " + e.getCause());
            }
        }
    }

    // ── Helpers de escritura en modelo ────────────────────────────────────────

    private void setMx(String ref, float value, List<BasicDataAttribute> out) {
        // Soporta CID aplanado (DA FLOAT32 directo), MV estándar (ref.mag → mag.f)
        // y CMV estándar de CIDs de fabricante (ref.phsX → cVal.mag.f)
        for (String cand : new String[]{ref, ref + ".f", ref + ".cVal.mag.f"}) {
            ModelNode node = serverModel.findModelNode(cand, Fc.MX);
            if (node instanceof BdaFloat32) {
                ((BdaFloat32) node).setFloat(value);
                out.add((BdaFloat32) node);
                return;
            }
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

        // Expandir arrays SCL con count> 1 (ej: armónicos en CIDs de medidores reales)
        cid = expandSclArraysInMemory(cid);

        // Auto-detectar el IED name en el CID y reemplazarlo si se pidió otro
        Matcher m = Pattern.compile("<IED[^>]+\\bname=\"([^\"]+)\"").matcher(cid);
        if (m.find()) {
            String originalName = m.group(1);
            if (!originalName.equals(targetIedName)) {
                LOG.info("Reemplazando IED name '" + originalName + "' → '" + targetIedName + "'");
                cid = cid.replace("name=\"" + originalName + "\"",
                                  "name=\"" + targetIedName + "\"");
            }
        }
        // Garantizar que this.iedName coincide con lo que está en el CID
        this.iedName = targetIedName;

        try (InputStream is = new ByteArrayInputStream(cid.getBytes(StandardCharsets.UTF_8))) {
            List<ServerModel> models = SclParser.parse(is);
            if (models == null || models.isEmpty()) {
                throw new IOException("El CID no contiene ningún modelo de servidor");
            }
            return models.get(0);
        }
    }

    /**
     * Expande en memoria los elementos SDO/DA/BDA con atributo count>1.
     * Ej: {@code <SDO count="51" name="phsAHar"/>} se expande en 51 SDOs
     * individuales phsAHar00..phsAHar50.
     * Idéntico a IEC61850Server.expandSclArrays() pero sin archivo temporal.
     */
    private String expandSclArraysInMemory(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // Suprimir mensajes de validación DTD
            builder.setErrorHandler(null);
            Document doc = builder.parse(
                new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            int totalExpanded = 0;
            for (String tag : new String[]{"SDO", "DA", "BDA"}) {
                NodeList nodes = doc.getElementsByTagNameNS("*", tag);
                List<Element> toExpand = new ArrayList<>();
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    String countStr = el.getAttribute("count").trim();
                    if (!countStr.isEmpty()) {
                        try {
                            if (Integer.parseInt(countStr) > 1) toExpand.add(el);
                        } catch (NumberFormatException ignore) {}
                    }
                }
                for (Element el : toExpand) {
                    int count    = Integer.parseInt(el.getAttribute("count").trim());
                    String name  = el.getAttribute("name");
                    Node parent  = el.getParentNode();
                    String nsUri = el.getNamespaceURI();
                    NamedNodeMap attrs = el.getAttributes();
                    int digits = Math.max(2, String.valueOf(count).length());
                    for (int i = 0; i < count; i++) {
                        String indexedName = name + String.format("%0" + digits + "d", i);
                        Element newEl = (nsUri != null)
                            ? doc.createElementNS(nsUri, tag)
                            : doc.createElement(tag);
                        for (int a = 0; a < attrs.getLength(); a++) {
                            Attr attr = (Attr) attrs.item(a);
                            String an = attr.getName();
                            if (!an.equals("count") && !an.startsWith("xmlns"))
                                newEl.setAttribute(an, attr.getValue());
                        }
                        newEl.setAttribute("name", indexedName);
                        parent.insertBefore(newEl, el);
                    }
                    parent.removeChild(el);
                    totalExpanded++;
                }
            }

            if (totalExpanded == 0) return xmlContent;

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            LOG.info("SCL array expansion: " + totalExpanded + " arrays expandidos en memoria");
            return sw.toString();

        } catch (Exception e) {
            LOG.warning("SCL array expansion fallida: " + e.getMessage()
                + " — usando CID sin expandir");
            return xmlContent;
        }
    }

}
