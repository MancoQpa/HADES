package com.harmonicmonitor.model;

import java.time.Instant;

/**
 * Snapshot completo de mediciones de un alimentador MT en un instante dado.
 * Incluye valores fundamentales, armónicos hasta el orden 50, y metadatos.
 */
public class FeederMeasurement {

    // --- Identificación ---
    private final String feederId;
    private final String iedName;
    private final Instant timestamp;

    // --- Valores fundamentales (RMS) ---
    private double voltageL1;   // V
    private double voltageL2;   // V
    private double voltageL3;   // V
    private double currentL1;   // A
    private double currentL2;   // A
    private double currentL3;   // A
    private double activePower;     // kW  (P total 3φ)
    private double reactivePower;   // kVAR (Q total 3φ)
    private double apparentPower;   // kVA  (S total 3φ)
    private double powerFactor;     // cos φ (promedio 3φ)
    private double frequency;       // Hz

    // --- THD ---
    private double thdVoltageL1;   // %
    private double thdVoltageL2;   // %
    private double thdVoltageL3;   // %
    private double thdCurrentL1;   // %
    private double thdCurrentL2;   // %
    private double thdCurrentL3;   // %

    // --- Espectros armónicos (índice 0=H1, 1=H2, ..., 49=H50) ---
    private double[] harmonicCurrentL1  = new double[50];  // A por orden
    private double[] harmonicCurrentL2  = new double[50];
    private double[] harmonicCurrentL3  = new double[50];
    private double[] harmonicVoltageL1  = new double[50];  // V por orden
    private double[] harmonicVoltageL2  = new double[50];
    private double[] harmonicVoltageL3  = new double[50];

    // --- K-Factor ---
    private double kFactorL1;      // K-factor fase L1 (MHAI.HKf)
    private double kFactorL2;
    private double kFactorL3;

    // --- THD impar/par ---
    private double thdOddCurrentL1;   // THD armónicos impares L1 (%)
    private double thdOddCurrentL2;
    private double thdOddCurrentL3;
    private double thdEvenCurrentL1;  // THD armónicos pares L1 (%)
    private double thdEvenCurrentL2;
    private double thdEvenCurrentL3;

    // --- THD tensión fase-fase (PP) ---
    private double thdPpvL12;   // THD V_AB (%)
    private double thdPpvL23;   // THD V_BC (%)
    private double thdPpvL31;   // THD V_CA (%)

    // --- Energía (MMTR, kWh/kVAh/kVArh) ---
    private double totalEnergyKWh;
    private double totalEnergyKVAh;
    private double totalEnergyKVArh;
    private double supplyKWh;
    private double supplyKVArh;

    // --- Demanda (MSTA) ---
    private double demandAvgKW;
    private double demandMaxKW;
    private double demandMinKW;
    private double demandAvgKVAr;
    private double demandMaxKVAr;
    private double demandMinKVAr;
    private double demandAvgKVA;

    // --- Componentes simétricas (MSQI) ---
    private double seqCurrentPos;    // Secuencia positiva corriente (A)
    private double seqCurrentNeg;    // Secuencia negativa corriente (A)
    private double seqCurrentZero;   // Secuencia cero corriente (A)
    private double seqVoltagePos;    // Secuencia positiva tensión (V)
    private double seqVoltageNeg;    // Secuencia negativa tensión (V)

    // --- Desbalance calculado ---
    private double voltageUnbalancePct;  // % desbalance tensión = (Vneg/Vpos)*100
    private double currentUnbalancePct; // % desbalance corriente = (Ineg/Ipos)*100

    // --- Clasificación de carga ---
    private LoadType detectedLoadType = LoadType.UNKNOWN;
    private double cvCurrent  = 0.0;   // Coeficiente de variación de corriente
    private double h5h1Ratio  = 0.0;   // Relación H5/H1 de corriente
    private double h7h1Ratio  = 0.0;   // Relación H7/H1 de corriente
    private double h11h1Ratio = 0.0;   // Relación H11/H1 de corriente
    private double h13h1Ratio = 0.0;   // Relación H13/H1 de corriente

    // --- Indicadores derivados para detección CRYPTO_MINING_PFC ---
    // Calculados en ElectronicLoadDetector.classify() y expuestos
    // para visualización en dashboard y exportación a base de datos.
    //
    //   h5h7Ratio : H5/H7 — ratio de dominancia espectral del PFC.
    //               PFC activo: > 8 (H7 casi nulo). 6-pulsos clásico: ≈ 1.8.
    //   qsRatio   : |Q|/S  — fracción de potencia reactiva sobre aparente.
    //               PFC: < 0.005. Motores: 0.3-0.5. Inductivo genérico: 0.1-0.6.
    //   pfcCryptoScore : puntaje 0-100 de la firma PFC. Permite mostrar
    //               un indicador gradual en lugar de una clasificación binaria.
    private double h5h7Ratio      = 0.0;   // H5/H7 de corriente L1
    private double qsRatio        = 0.0;   // |Q|/S
    private double pfcCryptoScore = 0.0;   // 0-100

    // --- Resonancia ---
    private double resonanceFrequency = 0.0;  // Hz (estimada)
    private int    resonanceOrder     = 0;    // orden armónico de resonancia

    // --- Calidad de la medición ---
    private boolean dataValid = true;
    private String  qualityFlag = "GOOD";
    private boolean spectrumEstimated = false;  // true si el espectro fue estimado (IED no lo provee)

    public FeederMeasurement(String feederId, String iedName) {
        this.feederId  = feederId;
        this.iedName   = iedName;
        this.timestamp = Instant.now();
    }

    public FeederMeasurement(String feederId, String iedName, Instant timestamp) {
        this.feederId  = feederId;
        this.iedName   = iedName;
        this.timestamp = timestamp;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String   getFeederId()   { return feederId; }
    public String   getIedName()    { return iedName; }
    public Instant  getTimestamp()  { return timestamp; }

    public double getVoltageL1()   { return voltageL1; }
    public double getVoltageL2()   { return voltageL2; }
    public double getVoltageL3()   { return voltageL3; }
    public double getCurrentL1()   { return currentL1; }
    public double getCurrentL2()   { return currentL2; }
    public double getCurrentL3()   { return currentL3; }
    public double getActivePower()    { return activePower; }
    public double getReactivePower()  { return reactivePower; }
    public double getApparentPower()  { return apparentPower; }
    public double getPowerFactor()    { return powerFactor; }
    public double getFrequency()      { return frequency; }

    public double getThdVoltageL1()  { return thdVoltageL1; }
    public double getThdVoltageL2()  { return thdVoltageL2; }
    public double getThdVoltageL3()  { return thdVoltageL3; }
    public double getThdCurrentL1()  { return thdCurrentL1; }
    public double getThdCurrentL2()  { return thdCurrentL2; }
    public double getThdCurrentL3()  { return thdCurrentL3; }

    /** Espectro de corriente L1: índice 0 = H1 (fundamental), índice n-1 = Hn */
    public double[] getHarmonicCurrentL1()  { return harmonicCurrentL1; }
    public double[] getHarmonicCurrentL2()  { return harmonicCurrentL2; }
    public double[] getHarmonicCurrentL3()  { return harmonicCurrentL3; }
    public double[] getHarmonicVoltageL1()  { return harmonicVoltageL1; }
    public double[] getHarmonicVoltageL2()  { return harmonicVoltageL2; }
    public double[] getHarmonicVoltageL3()  { return harmonicVoltageL3; }

    public LoadType getDetectedLoadType()  { return detectedLoadType; }
    public double   getCvCurrent()         { return cvCurrent; }
    public double   getH5h1Ratio()         { return h5h1Ratio; }
    public double   getH7h1Ratio()         { return h7h1Ratio; }
    public double   getH11h1Ratio()        { return h11h1Ratio; }
    public double   getH13h1Ratio()        { return h13h1Ratio; }

    public double getResonanceFrequency()  { return resonanceFrequency; }
    public int    getResonanceOrder()      { return resonanceOrder; }

    public boolean isDataValid()     { return dataValid; }
    public String  getQualityFlag()  { return qualityFlag; }

    public void setVoltageL1(double v)   { voltageL1 = v; }
    public void setVoltageL2(double v)   { voltageL2 = v; }
    public void setVoltageL3(double v)   { voltageL3 = v; }
    public void setCurrentL1(double v)   { currentL1 = v; }
    public void setCurrentL2(double v)   { currentL2 = v; }
    public void setCurrentL3(double v)   { currentL3 = v; }
    public void setActivePower(double v)    { activePower   = v; }
    public void setReactivePower(double v)  { reactivePower = v; }
    public void setApparentPower(double v)  { apparentPower = v; }
    public void setPowerFactor(double v)    { powerFactor   = v; }
    public void setFrequency(double v)      { frequency     = v; }

    public void setThdVoltageL1(double v)  { thdVoltageL1 = v; }
    public void setThdVoltageL2(double v)  { thdVoltageL2 = v; }
    public void setThdVoltageL3(double v)  { thdVoltageL3 = v; }
    public void setThdCurrentL1(double v)  { thdCurrentL1 = v; }
    public void setThdCurrentL2(double v)  { thdCurrentL2 = v; }
    public void setThdCurrentL3(double v)  { thdCurrentL3 = v; }

    public void setHarmonicCurrentL1(double[] v)  { harmonicCurrentL1 = v; }
    public void setHarmonicCurrentL2(double[] v)  { harmonicCurrentL2 = v; }
    public void setHarmonicCurrentL3(double[] v)  { harmonicCurrentL3 = v; }
    public void setHarmonicVoltageL1(double[] v)  { harmonicVoltageL1 = v; }
    public void setHarmonicVoltageL2(double[] v)  { harmonicVoltageL2 = v; }
    public void setHarmonicVoltageL3(double[] v)  { harmonicVoltageL3 = v; }

    // K-Factor getters/setters
    public double getKFactorL1()  { return kFactorL1; }
    public double getKFactorL2()  { return kFactorL2; }
    public double getKFactorL3()  { return kFactorL3; }
    public void setKFactorL1(double v)  { kFactorL1 = v; }
    public void setKFactorL2(double v)  { kFactorL2 = v; }
    public void setKFactorL3(double v)  { kFactorL3 = v; }

    // THD impar/par getters/setters
    public double getThdOddCurrentL1()   { return thdOddCurrentL1; }
    public double getThdOddCurrentL2()   { return thdOddCurrentL2; }
    public double getThdOddCurrentL3()   { return thdOddCurrentL3; }
    public double getThdEvenCurrentL1()  { return thdEvenCurrentL1; }
    public double getThdEvenCurrentL2()  { return thdEvenCurrentL2; }
    public double getThdEvenCurrentL3()  { return thdEvenCurrentL3; }
    public void setThdOddCurrentL1(double v)   { thdOddCurrentL1  = v; }
    public void setThdOddCurrentL2(double v)   { thdOddCurrentL2  = v; }
    public void setThdOddCurrentL3(double v)   { thdOddCurrentL3  = v; }
    public void setThdEvenCurrentL1(double v)  { thdEvenCurrentL1 = v; }
    public void setThdEvenCurrentL2(double v)  { thdEvenCurrentL2 = v; }
    public void setThdEvenCurrentL3(double v)  { thdEvenCurrentL3 = v; }

    // THD tensión PP getters/setters
    public double getThdPpvL12()  { return thdPpvL12; }
    public double getThdPpvL23()  { return thdPpvL23; }
    public double getThdPpvL31()  { return thdPpvL31; }
    public void setThdPpvL12(double v)  { thdPpvL12 = v; }
    public void setThdPpvL23(double v)  { thdPpvL23 = v; }
    public void setThdPpvL31(double v)  { thdPpvL31 = v; }

    // Energía getters/setters
    public double getTotalEnergyKWh()    { return totalEnergyKWh; }
    public double getTotalEnergyKVAh()   { return totalEnergyKVAh; }
    public double getTotalEnergyKVArh()  { return totalEnergyKVArh; }
    public double getSupplyKWh()         { return supplyKWh; }
    public double getSupplyKVArh()       { return supplyKVArh; }
    public void setTotalEnergyKWh(double v)    { totalEnergyKWh   = v; }
    public void setTotalEnergyKVAh(double v)   { totalEnergyKVAh  = v; }
    public void setTotalEnergyKVArh(double v)  { totalEnergyKVArh = v; }
    public void setSupplyKWh(double v)         { supplyKWh        = v; }
    public void setSupplyKVArh(double v)       { supplyKVArh      = v; }

    // Demanda getters/setters
    public double getDemandAvgKW()    { return demandAvgKW; }
    public double getDemandMaxKW()    { return demandMaxKW; }
    public double getDemandMinKW()    { return demandMinKW; }
    public double getDemandAvgKVAr()  { return demandAvgKVAr; }
    public double getDemandMaxKVAr()  { return demandMaxKVAr; }
    public double getDemandMinKVAr()  { return demandMinKVAr; }
    public double getDemandAvgKVA()   { return demandAvgKVA; }
    public void setDemandAvgKW(double v)    { demandAvgKW   = v; }
    public void setDemandMaxKW(double v)    { demandMaxKW   = v; }
    public void setDemandMinKW(double v)    { demandMinKW   = v; }
    public void setDemandAvgKVAr(double v)  { demandAvgKVAr = v; }
    public void setDemandMaxKVAr(double v)  { demandMaxKVAr = v; }
    public void setDemandMinKVAr(double v)  { demandMinKVAr = v; }
    public void setDemandAvgKVA(double v)   { demandAvgKVA  = v; }

    // Componentes simétricas getters/setters
    public double getSeqCurrentPos()   { return seqCurrentPos; }
    public double getSeqCurrentNeg()   { return seqCurrentNeg; }
    public double getSeqCurrentZero()  { return seqCurrentZero; }
    public double getSeqVoltagePos()   { return seqVoltagePos; }
    public double getSeqVoltageNeg()   { return seqVoltageNeg; }
    public void setSeqCurrentPos(double v)   { seqCurrentPos  = v; }
    public void setSeqCurrentNeg(double v)   { seqCurrentNeg  = v; }
    public void setSeqCurrentZero(double v)  { seqCurrentZero = v; }
    public void setSeqVoltagePos(double v)   { seqVoltagePos  = v; }
    public void setSeqVoltageNeg(double v)   { seqVoltageNeg  = v; }

    // Desbalance getters/setters
    public double getVoltageUnbalancePct()  { return voltageUnbalancePct; }
    public double getCurrentUnbalancePct()  { return currentUnbalancePct; }
    public void setVoltageUnbalancePct(double v)  { voltageUnbalancePct = v; }
    public void setCurrentUnbalancePct(double v)  { currentUnbalancePct = v; }

    public void setDetectedLoadType(LoadType t)  { detectedLoadType = t; }
    public void setCvCurrent(double v)            { cvCurrent   = v; }
    public void setH5h1Ratio(double v)            { h5h1Ratio   = v; }
    public void setH7h1Ratio(double v)            { h7h1Ratio   = v; }
    public void setH11h1Ratio(double v)           { h11h1Ratio  = v; }
    public void setH13h1Ratio(double v)           { h13h1Ratio  = v; }
    public double getH5h7Ratio()                  { return h5h7Ratio; }
    public double getQsRatio()                    { return qsRatio; }
    public double getPfcCryptoScore()             { return pfcCryptoScore; }
    public void setH5h7Ratio(double v)            { h5h7Ratio      = v; }
    public void setQsRatio(double v)              { qsRatio        = v; }
    public void setPfcCryptoScore(double v)       { pfcCryptoScore = v; }

    public void setResonanceFrequency(double v)  { resonanceFrequency = v; }
    public void setResonanceOrder(int v)          { resonanceOrder     = v; }

    public void setDataValid(boolean v)          { dataValid         = v; }
    public void setQualityFlag(String v)         { qualityFlag       = v; }
    public boolean isSpectrumEstimated()         { return spectrumEstimated; }
    public void setSpectrumEstimated(boolean v)  { spectrumEstimated = v; }

    /** Voltaje promedio trifásico */
    public double getVoltageAvg() {
        return (voltageL1 + voltageL2 + voltageL3) / 3.0;
    }

    /** Corriente promedio trifásico */
    public double getCurrentAvg() {
        return (currentL1 + currentL2 + currentL3) / 3.0;
    }

    /**
     * THD de tensión promedio trifásico por RMS cuadrático (IEC 61000-4-30).
     * Combina fases disponibles (ignora las que son cero).
     */
    public double getThdVoltageAvg() {
        return rms3(thdVoltageL1, thdVoltageL2, thdVoltageL3);
    }

    /**
     * THD de corriente promedio trifásico por RMS cuadrático (IEC 61000-4-30).
     * Combina fases disponibles (ignora las que son cero).
     */
    public double getThdCurrentAvg() {
        return rms3(thdCurrentL1, thdCurrentL2, thdCurrentL3);
    }

    /** Combina hasta 3 valores por RMS, ignorando los que son cero. */
    private static double rms3(double a, double b, double c) {
        double sumSq = 0; int n = 0;
        if (a > 0) { sumSq += a * a; n++; }
        if (b > 0) { sumSq += b * b; n++; }
        if (c > 0) { sumSq += c * c; n++; }
        return (n > 0) ? Math.sqrt(sumSq / n) : 0.0;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s @ %s  V=%.1f/%.1f/%.1f  I=%.1f/%.1f/%.1f  P=%.1fkW  THDi=%.1f%%  Load=%s",
            feederId, iedName, timestamp,
            voltageL1, voltageL2, voltageL3,
            currentL1, currentL2, currentL3,
            activePower, getThdCurrentAvg(), detectedLoadType);
    }
}
