package com.harmonicmonitor.comm;

import com.beanit.iec61850bean.*;
import com.harmonicmonitor.model.FeederConfig;

import java.util.*;
import java.util.logging.Logger;

/**
 * Descubrimiento automático de nodos lógicos IEC 61850 relevantes para
 * monitoreo de armónicos y calidad de energía.
 *
 * Recorre el ServerModel obtenido vía association.retrieveModel() y:
 *   1. Identifica los LogicalDevices disponibles.
 *   2. Para cada LD, itera sus LogicalNodes.
 *   3. Extrae la clase LN desde el nombre (IEC 61850-7-4: prefijo + CLase + inst).
 *   4. Filtra las clases de interés: MMXU, MHAI, MSQI, MMTR, MSTA, MMXN.
 *   5. Para cada LN de interés, prueba la existencia de DOs conocidos.
 *   6. Puntúa los MMXU por completitud y selecciona el mejor candidato.
 *   7. Construye una FeederConfig sugerida con los LN descubiertos.
 *
 * Compatible con cualquier fabricante (no tiene lógica ION 7400-específica).
 */
public class IEDModelDiscovery {

    private static final Logger LOG = Logger.getLogger(IEDModelDiscovery.class.getName());

    /** Clases LN que nos interesan para el monitoreo. */
    private static final Set<String> TARGET_CLASSES = new LinkedHashSet<>(Arrays.asList(
        "MMXU", "MHAI", "MSQI", "MMTR", "MSTA", "MMXN"
    ));

    /**
     * DOs a probar por clase LN.
     * El orden importa: los primeros son los más relevantes.
     */
    private static final Map<String, String[]> KNOWN_DOS;
    static {
        KNOWN_DOS = new LinkedHashMap<>();
        KNOWN_DOS.put("MMXU", new String[]{
            "PhV", "PPV", "A",
            "TotW", "W", "TotVAr", "VAr",
            "TotVA", "VA", "TotPF", "PF",
            "Hz", "PPV", "TotVAr"
        });
        KNOWN_DOS.put("MHAI", new String[]{
            "ThdA", "ThdV", "ThdPPV",
            "HKf", "ThdOddA", "ThdEvnA",
            "HA", "HV"
        });
        KNOWN_DOS.put("MSQI", new String[]{
            "SeqA", "SeqV",
            "NegSeqV", "PosSeqV", "ZerSeqV",
            "NegSeqA", "PosSeqA"
        });
        KNOWN_DOS.put("MMTR", new String[]{
            "TotWh", "TotVAh", "TotVArh",
            "SupWh", "SupVArh", "DmdWh",
            "DelWh", "RecWh"
        });
        KNOWN_DOS.put("MSTA", new String[]{
            "AvW", "MaxW", "MinW",
            "AvVAr", "AvVA", "AvA", "AvV"
        });
        KNOWN_DOS.put("MMXN", new String[]{
            "Vol", "Amp", "Watt", "VAr", "VA", "PF", "Hz"
        });
    }

    /** FCs a intentar al probar la existencia de un DO, en orden. */
    private static final Fc[] PROBE_FCS = { Fc.MX, Fc.ST, Fc.CF, Fc.SP };

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Ejecuta el descubrimiento sobre un ServerModel ya cargado.
     *
     * @param serverModel  modelo del servidor (obtenido con association.retrieveModel())
     * @param baseConfig   configuración base con host/port/iedName para la sugerencia
     * @return             resultado con nodos encontrados y configuración sugerida
     */
    public static DiscoveryResult discover(ServerModel serverModel, FeederConfig baseConfig) {
        DiscoveryResult result = new DiscoveryResult();
        StringBuilder report = new StringBuilder();

        report.append("=== Descubrimiento IEC 61850 ===\n");
        report.append("Host: ").append(baseConfig.getIedHost())
              .append(":").append(baseConfig.getIedPort()).append("\n\n");

        // ── Paso 1: Enumerar LDs ─────────────────────────────────────────────
        List<LogicalDevice> lds = new ArrayList<>();
        for (ModelNode child : serverModel.getChildren()) {
            if (child instanceof LogicalDevice) {
                lds.add((LogicalDevice) child);
            }
        }

        if (lds.isEmpty()) {
            report.append("ERROR: No se encontraron Logical Devices.\n");
            result.setReport(report.toString());
            return result;
        }

        report.append("Logical Devices encontrados: ").append(lds.size()).append("\n");
        for (LogicalDevice ld : lds) {
            report.append("  ").append(ld.getName()).append("\n");
        }
        report.append("\n");

        // ── Paso 2: Derivar el nombre del IED ────────────────────────────────
        String iedName = extractIedName(lds);
        result.setIedName(iedName);
        report.append("IED Name (inferido): ").append(iedName).append("\n\n");

        // ── Paso 3: Recorrer LDs y LNs ───────────────────────────────────────
        for (LogicalDevice ld : lds) {
            String ldInst = deriveLdInst(ld.getName(), iedName);
            report.append("── LD: ").append(ldInst)
                  .append(" (").append(ld.getName()).append(")\n");

            for (ModelNode lnNode : ld.getChildren()) {
                if (!(lnNode instanceof LogicalNode)) continue;
                LogicalNode ln = (LogicalNode) lnNode;
                String lnName = ln.getName();   // e.g. "M03_MMXU1" or "LLN0" or "LPHD1"

                // Extraer clase LN
                String[] parsed = parseLnName(lnName);
                if (parsed == null) continue;   // LLN0, LPHD → no nos interesan aquí
                String lnClass = parsed[0];
                String prefix  = parsed[1];
                String inst    = parsed[2];

                if (!TARGET_CLASSES.contains(lnClass)) continue;

                // Construir base de referencia: "IEDNameLDInst/LNName"
                String base = ld.getName() + "/" + lnName;

                // Probar DOs disponibles
                List<String> foundDOs = probeDOs(serverModel, base, KNOWN_DOS.get(lnClass));

                report.append("  [").append(lnClass).append("] ").append(lnName);
                if (!prefix.isEmpty()) report.append(" (prefijo='").append(prefix).append("')");
                report.append("\n");
                report.append("    DOs: ").append(foundDOs).append("\n");

                DiscoveryResult.FoundNode node = new DiscoveryResult.FoundNode(
                    ldInst, lnName, lnClass, prefix, inst, foundDOs
                );
                result.addNode(lnClass, node);
            }
        }

        // ── Paso 4: Seleccionar mejores candidatos ────────────────────────────
        result.selectBestCandidates();

        // ── Paso 5: Construir configuración sugerida ──────────────────────────
        FeederConfig suggested = buildSuggestedConfig(result, baseConfig, iedName);
        result.setSuggestedConfig(suggested);

        // ── Paso 6: Resumen ───────────────────────────────────────────────────
        report.append("\n=== Candidatos seleccionados ===\n");
        appendNodeSummary(report, "MMXU", result.getBestMMXU());
        appendNodeSummary(report, "MHAI", result.getBestMHAI());
        appendNodeSummary(report, "MSQI", result.getBestMSQI());
        appendNodeSummary(report, "MMTR", result.getBestMMTR());
        appendNodeSummary(report, "MSTA", result.getBestMSTA());

        if (!result.hasAnyMonitoringNode()) {
            report.append("\n⚠ No se encontró ningún LN de monitoreo (MMXU/MMXN).\n");
            report.append("  Verifica que el IED implemente IEC 61850-7-4 Measurement LNs.\n");
        } else {
            report.append("\n✓ Configuración sugerida generada.\n");
        }

        result.setReport(report.toString());
        return result;
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Infiere el nombre del IED a partir de los nombres de los LD.
     * En IEC 61850, los nombres de LD se forman como "IEDNameLDInst"
     * (p.ej. "cbo2LD0" → iedName="cbo2", ldInst="LD0").
     * Retorna el prefijo común más largo de todos los LDs.
     */
    static String extractIedName(List<LogicalDevice> lds) {
        if (lds.isEmpty()) return "";
        if (lds.size() == 1) {
            // Separar al primer dígito → todo antes es el nombre del IED
            String name = lds.get(0).getName();
            int i = 0;
            while (i < name.length() && !Character.isDigit(name.charAt(i))) i++;
            // Retroceder para no consumir letras que son parte del ldInst suffix (LD, MT, etc.)
            // Heurística: si el sufijo empieza con "LD", "MT", "MU" → prefijo = todo antes
            return extractIedPrefix(name);
        }
        // Prefijo común de todos los nombres de LD
        String common = lds.get(0).getName();
        for (int k = 1; k < lds.size(); k++) {
            common = commonPrefix(common, lds.get(k).getName());
        }
        return common;
    }

    /**
     * Dado el nombre completo del LD (e.g. "cbo2LD0") y el nombre del IED,
     * devuelve la instancia del LD (e.g. "LD0").
     */
    static String deriveLdInst(String ldFullName, String iedName) {
        if (iedName != null && !iedName.isEmpty() && ldFullName.startsWith(iedName)) {
            return ldFullName.substring(iedName.length());
        }
        return ldFullName;
    }

    /**
     * Parsea el nombre de un LN según IEC 61850-7-1:
     *   name = [prefix] LNClass inst
     * donde LNClass es 3-4 letras mayúsculas, inst son dígitos.
     *
     * Retorna String[]{lnClass, prefix, inst} o null si no es una clase de interés
     * o no tiene el patrón esperado.
     */
    static String[] parseLnName(String lnName) {
        if (lnName == null || lnName.isEmpty()) return null;

        // Buscar la clase LN como secuencia de letras mayúsculas de 3-4 chars
        // seguida de dígitos, posiblemente precedida por un prefijo arbitrario.
        // Probar cada posición donde podría empezar la clase LN.
        for (int start = 0; start <= lnName.length() - 3; start++) {
            // Intentar extraer clase en posición 'start'
            int end = start;
            while (end < lnName.length() && Character.isUpperCase(lnName.charAt(end))) end++;
            int classLen = end - start;
            if (classLen < 2 || classLen > 6) continue;

            String candidate = lnName.substring(start, end);

            // La clase debe estar en nuestro mapa o al menos ser mayúsculas
            // Solo interesamos en TARGET_CLASSES; también aceptamos LLN0/LPHD para descartarlos
            if (!TARGET_CLASSES.contains(candidate)) continue;

            // Después de la clase debe venir dígitos (instancia)
            if (end >= lnName.length() || !Character.isDigit(lnName.charAt(end))) continue;

            String prefix = lnName.substring(0, start);
            String inst   = lnName.substring(end);
            // inst debe ser todo dígitos
            if (!inst.matches("\\d+")) continue;

            return new String[]{ candidate, prefix, inst };
        }
        return null;
    }

    /**
     * Prueba la existencia de DOs en el modelo intentando findModelNode con FCs conocidas.
     * No lanza excepción si el DO no existe (retorna null).
     *
     * @param model  ServerModel ya cargado
     * @param base   referencia base: "IEDNameLDInst/LNName"
     * @param dos    lista de nombres de DO a probar
     * @return lista de DOs que existen en el modelo
     */
    static List<String> probeDOs(ServerModel model, String base, String[] dos) {
        List<String> found = new ArrayList<>();
        if (dos == null) return found;
        Set<String> seen = new HashSet<>();
        for (String doName : dos) {
            if (!seen.add(doName)) continue;  // evitar duplicados en la lista de entrada
            String ref = base + "." + doName;
            for (Fc fc : PROBE_FCS) {
                try {
                    if (model.findModelNode(ref, fc) != null) {
                        found.add(doName);
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        return found;
    }

    /**
     * Construye la FeederConfig sugerida a partir de los mejores candidatos.
     */
    private static FeederConfig buildSuggestedConfig(
            DiscoveryResult result, FeederConfig base, String iedName) {

        FeederConfig cfg = new FeederConfig();
        // Copiar datos de conexión desde la configuración base
        cfg.setIedHost(base.getIedHost());
        cfg.setIedPort(base.getIedPort());
        cfg.setFeederId(base.getFeederId());
        cfg.setFeederName(base.getFeederName());
        cfg.setDescription(base.getDescription());
        cfg.setNominalVoltageKv(base.getNominalVoltageKv());
        cfg.setNominalCurrentA(base.getNominalCurrentA());
        cfg.setPollIntervalMs(base.getPollIntervalMs());

        // IED name
        cfg.setIedName(iedName.isEmpty() ? base.getIedName() : iedName);

        // MMXU (o MMXN como fallback)
        DiscoveryResult.FoundNode mmxu = result.getBestMMXU();
        if (mmxu != null) {
            cfg.setLdInst(mmxu.ldInst);
            cfg.setMmxuPrefix(mmxu.prefix);
            cfg.setMmxuLnRef(mmxu.lnClass + mmxu.inst);  // e.g. "MMXU1"
        } else {
            cfg.setLdInst(base.getLdInst());
        }

        // MHAI
        DiscoveryResult.FoundNode mhai = result.getBestMHAI();
        cfg.setMhaiLnRef(mhai != null
            ? mhai.prefix + mhai.lnClass + mhai.inst
            : "");

        // MSQI
        DiscoveryResult.FoundNode msqi = result.getBestMSQI();
        cfg.setMsqiLnRef(msqi != null
            ? msqi.prefix + msqi.lnClass + msqi.inst
            : "");

        // MMTR
        DiscoveryResult.FoundNode mmtr = result.getBestMMTR();
        cfg.setMmtrLnRef(mmtr != null
            ? mmtr.prefix + mmtr.lnClass + mmtr.inst
            : "");

        // MSTA
        DiscoveryResult.FoundNode msta = result.getBestMSTA();
        cfg.setMstaLnRef(msta != null
            ? msta.prefix + msta.lnClass + msta.inst
            : "");

        return cfg;
    }

    private static void appendNodeSummary(StringBuilder sb, String cls,
                                           DiscoveryResult.FoundNode node) {
        sb.append("  ").append(cls).append(": ");
        if (node == null) {
            sb.append("no encontrado\n");
        } else {
            sb.append(node.lnName).append(" @ ").append(node.ldInst);
            if (!node.prefix.isEmpty()) sb.append(" [prefijo='").append(node.prefix).append("']");
            sb.append("\n    DOs: ").append(node.availableDOs).append("\n");
        }
    }

    private static String commonPrefix(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) i++;
        return a.substring(0, i);
    }

    private static String extractIedPrefix(String ldName) {
        // Intentar detectar sufijos comunes de ldInst: LD, MU, MT, CF + dígitos
        // Probar sufijos conocidos de largo decreciente
        String[] knownSuffixes = { "MeasLD", "CtrlLD", "LD", "MU", "MT", "CF" };
        for (String suffix : knownSuffixes) {
            int idx = ldName.lastIndexOf(suffix);
            if (idx > 0) {
                // Verificar que después del sufijo solo haya dígitos
                String rest = ldName.substring(idx + suffix.length());
                if (rest.matches("\\d*")) {
                    return ldName.substring(0, idx);
                }
            }
        }
        // Fallback: todo antes del primer dígito
        int i = 0;
        while (i < ldName.length() && !Character.isDigit(ldName.charAt(i))) i++;
        return (i > 0) ? ldName.substring(0, i) : ldName;
    }
}
