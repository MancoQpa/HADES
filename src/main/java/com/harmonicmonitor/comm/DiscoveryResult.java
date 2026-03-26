package com.harmonicmonitor.comm;

import com.harmonicmonitor.model.FeederConfig;
import java.util.*;

/**
 * Resultado del descubrimiento automático de nodos lógicos IEC 61850.
 * Contiene los LNs encontrados, punteados por relevancia, y la configuración sugerida.
 */
public class DiscoveryResult {

    private String iedName = "";
    private String report  = "";
    private FeederConfig suggestedConfig;

    private final List<FoundNode> mmxuNodes = new ArrayList<>();
    private final List<FoundNode> mhaiNodes = new ArrayList<>();
    private final List<FoundNode> msqiNodes = new ArrayList<>();
    private final List<FoundNode> mmtrNodes = new ArrayList<>();
    private final List<FoundNode> mstaNodes = new ArrayList<>();
    private final List<FoundNode> mmxnNodes = new ArrayList<>();

    private FoundNode bestMMXU, bestMHAI, bestMSQI, bestMMTR, bestMSTA;

    // ── Package-private mutation API (used by IEDModelDiscovery) ──────────────

    void setIedName(String v)              { iedName = v; }
    void setReport(String v)               { report  = v; }
    void setSuggestedConfig(FeederConfig v){ suggestedConfig = v; }

    void addNode(String lnClass, FoundNode node) {
        switch (lnClass) {
            case "MMXU": mmxuNodes.add(node); break;
            case "MHAI": mhaiNodes.add(node); break;
            case "MSQI": msqiNodes.add(node); break;
            case "MMTR": mmtrNodes.add(node); break;
            case "MSTA": mstaNodes.add(node); break;
            case "MMXN": mmxnNodes.add(node); break;
        }
    }

    /**
     * Selecciona el mejor candidato de cada clase usando una puntuación
     * basada en la completitud de los DOs disponibles.
     */
    void selectBestCandidates() {
        bestMMXU = mmxuNodes.stream()
            .max(Comparator.comparingInt(this::scoreMMXU)).orElse(null);
        // Fallback: si no hay MMXU usar MMXN (estructura similar)
        if (bestMMXU == null && !mmxnNodes.isEmpty()) bestMMXU = mmxnNodes.get(0);

        bestMHAI = mhaiNodes.isEmpty() ? null : mhaiNodes.get(0);
        bestMSQI = msqiNodes.isEmpty() ? null : msqiNodes.get(0);
        bestMMTR = mmtrNodes.isEmpty() ? null : mmtrNodes.get(0);
        bestMSTA = mstaNodes.isEmpty() ? null : mstaNodes.get(0);
    }

    private int scoreMMXU(FoundNode n) {
        int s = 0;
        List<String> d = n.availableDOs;
        if (d.contains("PhV"))   s += 25; else if (d.contains("PPV")) s += 12;
        if (d.contains("A"))     s += 25;
        if (d.contains("TotW"))  s += 15; else if (d.contains("W"))   s +=  8;
        if (d.contains("TotVAr"))s += 10;
        if (d.contains("TotVA")) s +=  8;
        if (d.contains("TotPF")) s +=  8;
        if (d.contains("Hz"))    s += 10;
        // Preferir instancia más baja
        try { s -= Integer.parseInt(n.inst) * 3; } catch (Exception ignored) {}
        return s;
    }

    // ── Public read API ───────────────────────────────────────────────────────

    public String      getIedName()         { return iedName; }
    public String      getReport()          { return report; }
    public FeederConfig getSuggestedConfig(){ return suggestedConfig; }
    public FoundNode   getBestMMXU()        { return bestMMXU; }
    public FoundNode   getBestMHAI()        { return bestMHAI; }
    public FoundNode   getBestMSQI()        { return bestMSQI; }
    public FoundNode   getBestMMTR()        { return bestMMTR; }
    public FoundNode   getBestMSTA()        { return bestMSTA; }
    public List<FoundNode> getMmxuNodes()   { return Collections.unmodifiableList(mmxuNodes); }
    public List<FoundNode> getMhaiNodes()   { return Collections.unmodifiableList(mhaiNodes); }
    public List<FoundNode> getMsqiNodes()   { return Collections.unmodifiableList(msqiNodes); }
    public List<FoundNode> getMmtrNodes()   { return Collections.unmodifiableList(mmtrNodes); }
    public List<FoundNode> getMstaNodes()   { return Collections.unmodifiableList(mstaNodes); }

    public boolean hasAnyMonitoringNode() {
        return bestMMXU != null;
    }

    // ── FoundNode ─────────────────────────────────────────────────────────────

    public static class FoundNode {
        public final String       ldInst;
        public final String       lnName;      // e.g. "M03_MMXU1"
        public final String       lnClass;     // e.g. "MMXU"
        public final String       prefix;      // e.g. "M03_"
        public final String       inst;        // e.g. "1"
        public final List<String> availableDOs;

        FoundNode(String ldInst, String lnName, String lnClass,
                  String prefix, String inst, List<String> availableDOs) {
            this.ldInst       = ldInst;
            this.lnName       = lnName;
            this.lnClass      = lnClass;
            this.prefix       = prefix;
            this.inst         = inst;
            this.availableDOs = Collections.unmodifiableList(availableDOs);
        }

        @Override
        public String toString() {
            return lnClass + ": " + lnName + " @ " + ldInst
                + "  DOs: [" + String.join(", ", availableDOs) + "]";
        }
    }
}
