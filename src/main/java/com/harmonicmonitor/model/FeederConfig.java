package com.harmonicmonitor.model;

/**
 * Configuración de un alimentador MT: conexión IEC 61850, parámetros de red
 * y umbrales de detección de carga electrónica / calidad de energía.
 */
public class FeederConfig {

    // --- Identificación ---
    private String feederId    = "AL-01";
    private String feederName  = "Alimentador MT 1";
    private String description = "";

    // --- Conexión IEC 61850 ---
    private String iedHost    = "127.0.0.1";
    private int    iedPort    = 102;
    private String iedName    = "IED1";
    private String mmxuLnRef  = "MMXU1";   // nodo lógico MMXU (sin prefijo LD)
    private String mmxuPrefix = "";         // prefijo del MMXU (ej. "M03_" para ION 7400)
    private String mhaiLnRef  = "MHAI1";   // nodo lógico MHAI (armónicos)
    private String msqiLnRef  = "MSQI1";   // nodo lógico MSQI (componentes simétricas)
    private String mmtrLnRef  = "MMTR1";   // nodo lógico MMTR (energía)
    private String mstaLnRef  = "MSTA1";   // nodo lógico MSTA (demanda)
    private String ldInst     = "LD0";      // instancia del LD
    private int    pollIntervalMs = 5000;   // ms entre lecturas

    // --- Parámetros de red MT ---
    private double nominalVoltageKv   = 23.0;  // kV
    private double nominalCurrentA    = 200.0; // A (In del feeder)
    private double shortCircuitMva    = 100.0; // MVA (Scc en barra)
    private double feederCapacitanceMicroF = 5.0; // µF total del alimentador

    // --- Umbrales de calidad de energía (IEC 61000 / IEEE 519) ---
    private double maxThdVoltagePct   = 5.0;   // %  THD de tensión máximo (IEC 61000-3-6)
    private double maxThdCurrentPct   = 8.0;   // %  THD de corriente máximo
    private double maxVoltageUnbalPct = 2.0;   // %  Desbalance de tensión (EN 50160)
    private double maxCurrentUnbalPct = 10.0;  // %  Desbalance de corriente

    // --- Umbrales de detección de carga electrónica ---
    private double maxCvElectronicThreshold  = 0.05; // CV < 5% → carga estable (electrónica)
    private double minThdICryptoThreshold    = 15.0; // % THDi para clasificar como cripto/datacenter
    private double minH5h1CryptoThreshold    = 0.15; // H5/H1 > 15% → firma de rectificador
    private double minH7h1CryptoThreshold    = 0.10; // H7/H1 > 10%

    // --- Umbrales de resonancia ---
    private double resonanceAmplificationMax = 3.0;  // veces la corriente fundamental

    // --- Topología de red ---
    private NetworkTopology topology = NetworkTopology.SINGLE_FEEDER;

    // --- Escalado de unidades del IED (depende del fabricante) ---
    // powerScaleFactor: 0.001 = IED reporta W (estandar IEC 61850), 1.0 = IED reporta kW (ION 7400)
    private double powerScaleFactor = 0.001;
    // pfScaleFactor: 1.0 = IED reporta FP como per-unit, 0.01 = IED reporta FP como % (ION 7400)
    private double pfScaleFactor    = 1.0;
    // analogScaleFactor: factor que se aplica a tensión (V), corriente (A) y frecuencia (Hz).
    // 1.0 = IED reporta en unidades SI directas (estándar, simuladores).
    // 0.0001 = IED usa units.multiplier=-4 (ION 7400: devuelve raw × 10⁴ sin escalar).
    private double analogScaleFactor = 1.0;

    // --- Perfil de simulación ---
    private SimProfile simProfile = SimProfile.CRYPTO_MINER;

    public FeederConfig() {}

    public FeederConfig(String feederId, String iedHost) {
        this.feederId = feederId;
        this.iedHost  = iedHost;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getFeederId()      { return feederId; }
    public String getFeederName()    { return feederName; }
    public String getDescription()   { return description; }
    public String getIedHost()       { return iedHost; }
    public int    getIedPort()       { return iedPort; }
    public String getIedName()       { return iedName; }
    public String getMmxuLnRef()     { return mmxuLnRef; }
    public String getMmxuPrefix()    { return mmxuPrefix; }
    public String getMhaiLnRef()     { return mhaiLnRef; }
    public String getMsqiLnRef()     { return msqiLnRef; }
    public String getMmtrLnRef()     { return mmtrLnRef; }
    public String getMstaLnRef()     { return mstaLnRef; }
    public String getLdInst()        { return ldInst; }
    public int    getPollIntervalMs(){ return pollIntervalMs; }

    public double getNominalVoltageKv()      { return nominalVoltageKv; }
    public double getNominalCurrentA()       { return nominalCurrentA; }
    public double getShortCircuitMva()       { return shortCircuitMva; }
    public double getFeederCapacitanceMicroF(){ return feederCapacitanceMicroF; }

    public double getMaxThdVoltagePct()      { return maxThdVoltagePct; }
    public double getMaxThdCurrentPct()      { return maxThdCurrentPct; }
    public double getMaxVoltageUnbalPct()    { return maxVoltageUnbalPct; }
    public double getMaxCurrentUnbalPct()    { return maxCurrentUnbalPct; }

    public double getMaxCvElectronicThreshold()  { return maxCvElectronicThreshold; }
    public double getMinThdICryptoThreshold()    { return minThdICryptoThreshold; }
    public double getMinH5h1CryptoThreshold()    { return minH5h1CryptoThreshold; }
    public double getMinH7h1CryptoThreshold()    { return minH7h1CryptoThreshold; }
    public double getResonanceAmplificationMax() { return resonanceAmplificationMax; }

    public NetworkTopology getTopology()       { return topology; }
    public SimProfile getSimProfile()          { return simProfile; }
    public double getPowerScaleFactor()        { return powerScaleFactor; }
    public double getPfScaleFactor()           { return pfScaleFactor; }
    public double getAnalogScaleFactor()       { return analogScaleFactor; }

    public void setFeederId(String v)      { feederId    = v; }
    public void setFeederName(String v)    { feederName  = v; }
    public void setDescription(String v)   { description = v; }
    public void setIedHost(String v)       { iedHost     = v; }
    public void setIedPort(int v)          { iedPort     = v; }
    public void setIedName(String v)       { iedName     = v; }
    public void setMmxuLnRef(String v)     { mmxuLnRef   = v; }
    public void setMmxuPrefix(String v)    { mmxuPrefix  = v; }
    public void setMhaiLnRef(String v)     { mhaiLnRef   = v; }
    public void setMsqiLnRef(String v)     { msqiLnRef   = v; }
    public void setMmtrLnRef(String v)     { mmtrLnRef   = v; }
    public void setMstaLnRef(String v)     { mstaLnRef   = v; }
    public void setLdInst(String v)        { ldInst      = v; }
    public void setPollIntervalMs(int v)   { pollIntervalMs = v; }

    public void setNominalVoltageKv(double v)       { nominalVoltageKv       = v; }
    public void setNominalCurrentA(double v)        { nominalCurrentA        = v; }
    public void setShortCircuitMva(double v)        { shortCircuitMva        = v; }
    public void setFeederCapacitanceMicroF(double v){ feederCapacitanceMicroF= v; }

    public void setMaxThdVoltagePct(double v)       { maxThdVoltagePct       = v; }
    public void setMaxThdCurrentPct(double v)       { maxThdCurrentPct       = v; }
    public void setMaxVoltageUnbalPct(double v)     { maxVoltageUnbalPct     = v; }
    public void setMaxCurrentUnbalPct(double v)     { maxCurrentUnbalPct     = v; }

    public void setMaxCvElectronicThreshold(double v)  { maxCvElectronicThreshold  = v; }
    public void setMinThdICryptoThreshold(double v)    { minThdICryptoThreshold    = v; }
    public void setMinH5h1CryptoThreshold(double v)    { minH5h1CryptoThreshold    = v; }
    public void setMinH7h1CryptoThreshold(double v)    { minH7h1CryptoThreshold    = v; }
    public void setResonanceAmplificationMax(double v) { resonanceAmplificationMax = v; }
    public void setTopology(NetworkTopology v)          { topology         = v; }
    public void setSimProfile(SimProfile v)            { simProfile       = v; }
    public void setPowerScaleFactor(double v)          { powerScaleFactor   = v; }
    public void setPfScaleFactor(double v)             { pfScaleFactor      = v; }
    public void setAnalogScaleFactor(double v)         { analogScaleFactor  = v; }

    @Override
    public String toString() {
        return feederName + " [" + feederId + "] → " + iedHost + ":" + iedPort;
    }
}
