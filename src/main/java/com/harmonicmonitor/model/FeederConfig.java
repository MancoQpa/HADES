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

    // --- Umbrales de detección de carga electrónica (rectificadores clásicos) ---
    private double maxCvElectronicThreshold  = 0.05; // CV < 5% → carga estable (electrónica)
    private double minThdICryptoThreshold    = 15.0; // % THDi para clasificar como cripto/datacenter sin PFC
    private double minH5h1CryptoThreshold    = 0.15; // H5/H1 > 15% → firma de rectificador sin PFC
    private double minH7h1CryptoThreshold    = 0.10; // H7/H1 > 10%

    // --- Umbrales de detección CRYPTO_MINING_PFC (PFC activo — ASIC miners) ---
    // Derivados de medición de campo ION7400-0d5885, alimentador exclusivo
    // de criptominería ASIC, 23 kV / 50 Hz, 26/05/2026.
    //
    // Justificación de cada umbral:
    //
    //   pfCryptoMinThreshold = 0.998
    //     El PFC boost converter en modo CCM produce FP > 0.99 a plena carga.
    //     Ninguna carga industrial convencional (motores, iluminación, hornos)
    //     alcanza FP > 0.998 de forma sostenida. Valor medido: 1.0000.
    //     Margen de tolerancia: ±0.002 para variaciones de carga y medición.
    //     Ref: Mohan, Undeland, Robbins, "Power Electronics" §16 (PFC converters).
    //
    //   qsRatioCryptoMaxThreshold = 0.012
    //     Relación Q/S < 0.012 (ángulo φ < 0.69°). Valor medido: 0.006/1.875 = 0.0032.
    //     El PFC activo anula la reactiva inyectando corriente en fase con la tensión.
    //     Margen: 0.012 cubre variaciones de carga parcial y errores de medición.
    //     Ref: IEEE Std 1459-2010, definiciones de potencia con cargas no lineales.
    //
    //   kFactorCryptoMaxThreshold = 1.12
    //     K = 1 + Σ(n² × Iₙ²) / Σ(Iₙ²)  (IEEE C57.110-2018, Ec. 1).
    //     Con THD ≈ 4% y H5 dominante: K ≈ 1 + (25 × 0.039²) / 1 ≈ 1.038.
    //     Valor medido: 1.05. Umbral 1.12 deja margen para THD hasta 6%.
    //     Cualquier carga con distorsión significativa sin PFC tiene K > 1.5.
    //
    //   h5h7RatioCryptoMinThreshold = 8.0
    //     El PFC boost suprime H7, H11, H13 de forma más agresiva que H5.
    //     Valor medido: H5/H7 = 3.9% / 0.2% = 19.5.
    //     Un rectificador de 6 pulsos clásico tiene H5/H7 ≈ 25%/14% = 1.8.
    //     Umbral conservador: 8.0 separa PFC (≥8) de 6-pulsos clásico (≤3).
    //
    //   thdCryptoPfcMaxThreshold = 6.5
    //     Límite superior del rango de THD esperado para ASIC con PFC.
    //     Rango medido: 3.0%–4.4%. Margen hasta 6.5% cubre carga parcial
    //     y modelos más antiguos con PFC de menor eficiencia.
    //
    //   thdCryptoPfcMinThreshold = 1.5
    //     Límite inferior: un THD < 1.5% indicaría carga verdaderamente lineal
    //     (motores síncronos, resistencias puras). El PFC siempre deja un residual
    //     de H5 detectable (≥2% típico).
    private double pfCryptoMinThreshold          = 0.998;  // FP mínimo (PFC activo)
    private double qsRatioCryptoMaxThreshold     = 0.012;  // Q/S máximo (reactiva nula)
    private double kFactorCryptoMaxThreshold     = 1.12;   // K-factor máximo
    private double h5h7RatioCryptoMinThreshold   = 8.0;    // H5/H7 mínimo (PFC suprime H7)
    private double thdCryptoPfcMaxThreshold      = 6.5;    // THD máximo (%)
    private double thdCryptoPfcMinThreshold      = 1.5;    // THD mínimo (%)

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

    public double getMaxCvElectronicThreshold()      { return maxCvElectronicThreshold; }
    public double getMinThdICryptoThreshold()        { return minThdICryptoThreshold; }
    public double getMinH5h1CryptoThreshold()        { return minH5h1CryptoThreshold; }
    public double getMinH7h1CryptoThreshold()        { return minH7h1CryptoThreshold; }
    public double getPfCryptoMinThreshold()          { return pfCryptoMinThreshold; }
    public double getQsRatioCryptoMaxThreshold()     { return qsRatioCryptoMaxThreshold; }
    public double getKFactorCryptoMaxThreshold()     { return kFactorCryptoMaxThreshold; }
    public double getH5h7RatioCryptoMinThreshold()   { return h5h7RatioCryptoMinThreshold; }
    public double getThdCryptoPfcMaxThreshold()      { return thdCryptoPfcMaxThreshold; }
    public double getThdCryptoPfcMinThreshold()      { return thdCryptoPfcMinThreshold; }
    public double getResonanceAmplificationMax()     { return resonanceAmplificationMax; }

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

    public void setMaxCvElectronicThreshold(double v)      { maxCvElectronicThreshold      = v; }
    public void setMinThdICryptoThreshold(double v)        { minThdICryptoThreshold        = v; }
    public void setMinH5h1CryptoThreshold(double v)        { minH5h1CryptoThreshold        = v; }
    public void setMinH7h1CryptoThreshold(double v)        { minH7h1CryptoThreshold        = v; }
    public void setPfCryptoMinThreshold(double v)          { pfCryptoMinThreshold          = v; }
    public void setQsRatioCryptoMaxThreshold(double v)     { qsRatioCryptoMaxThreshold     = v; }
    public void setKFactorCryptoMaxThreshold(double v)     { kFactorCryptoMaxThreshold     = v; }
    public void setH5h7RatioCryptoMinThreshold(double v)   { h5h7RatioCryptoMinThreshold   = v; }
    public void setThdCryptoPfcMaxThreshold(double v)      { thdCryptoPfcMaxThreshold      = v; }
    public void setThdCryptoPfcMinThreshold(double v)      { thdCryptoPfcMinThreshold      = v; }
    public void setResonanceAmplificationMax(double v)     { resonanceAmplificationMax     = v; }
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
