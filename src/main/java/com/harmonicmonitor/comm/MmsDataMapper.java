package com.harmonicmonitor.comm;

import com.beanit.iec61850bean.*;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Maps IEC 61850 MMS model nodes to FeederMeasurement domain objects.
 * Handles reference building, node caching (via {@link MmsNodeReader}), and BDA reading.
 * Extracted from IEC61850Communicator (refactor F2-001).
 * Low-level node I/O extracted to MmsNodeReader (refactor F9-001).
 */
public class MmsDataMapper {

    private static final Logger LOG = Logger.getLogger(MmsDataMapper.class.getName());

    // --- MMS references DO/DA for MMXU (with prefix) ---

    private String phsAPhVRef;
    private String phsBPhVRef;
    private String phsCPhVRef;
    private String phsAARef;
    private String phsBARef;
    private String phsCARef;
    private String wRef;
    private String varRef;
    private String vaRef;
    private String pfRef;
    private String hzRef;

    // --- MHAI references (THD from meter) ---
    private String thdAL1Ref, thdAL2Ref, thdAL3Ref;
    private String thdPpvL12Ref, thdPpvL23Ref, thdPpvL31Ref;
    private String kfL1Ref, kfL2Ref, kfL3Ref;
    private String thdOddAL1Ref, thdEvenAL1Ref;

    // --- MSQI references (symmetrical components) ---
    private String seqAposRef, seqAnegRef;
    private String seqVposRef, seqVnegRef;

    // --- MMTR references (energy, FC=ST, INT64) ---
    private String totWhRef, totVAhRef, totVArhRef, supWhRef, supVArhRef;

    // --- MSTA references (demand, FC=MX, scalar MV) ---
    private String avgWRef, maxWRef, minWRef, avgVArRef, avgVARef;

    // --- Harmonic array HA (phsXHar01..50) ---
    private final String[] haPhsAHarRef;
    private final String[] haPhsBHarRef;
    private final String[] haPhsCHarRef;

    private boolean harmonicsAvailable   = false;
    private boolean harmonicArrayInModel = false;
    private boolean powerScaleDetected   = false;

    // Harmonic read throttle: read every HARMONICS_READ_EVERY_N polling cycles (~30s at 5s/cycle)
    private static final int HARMONICS_READ_EVERY_N = 6;
    private int    harmonicsPollCounter = HARMONICS_READ_EVERY_N;
    private double[] cachedHarPhsA = null;
    private double[] cachedHarPhsB = null;
    private double[] cachedHarPhsC = null;

    private ServerModel serverModel;

    private final FeederConfig     config;
    private final Consumer<String> logInfo;
    private final MmsNodeReader    reader = new MmsNodeReader();

    public MmsDataMapper(FeederConfig config, Consumer<String> logInfo) {
        this.config   = config;
        this.logInfo  = logInfo != null ? logInfo : s -> {};
        this.haPhsAHarRef = new String[50];
        this.haPhsBHarRef = new String[50];
        this.haPhsCHarRef = new String[50];
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    public boolean isHarmonicArrayInModel() { return harmonicArrayInModel; }
    public boolean isHarmonicsAvailable()   { return harmonicsAvailable; }
    public ServerModel getServerModel()     { return serverModel; }

    /**
     * Clears node caches and resets scale-detection flag.
     * Called by IEC61850Communicator.disconnect() and associationClosed().
     */
    public void clearCache() {
        reader.clearCache();
        powerScaleDetected = false;
        serverModel = null;
    }

    // ── buildRefs() — called by IEC61850Communicator.connectInternal() ────────

    /**
     * Builds references for all MMXU/MHAI/MSQI/MMTR/MSTA attributes in the format
     * required by iec61850bean: "IEDNameLDInst/LNInst.DO.DA" with separate FC.
     */
    public void buildRefs(ServerModel model, ClientAssociation association) {
        this.serverModel = model;
        reader.setServerModel(model);

        // Reset harmonic cache on new connection
        cachedHarPhsA = null;
        cachedHarPhsB = null;
        cachedHarPhsC = null;
        harmonicsPollCounter = HARMONICS_READ_EVERY_N;
        reader.clearCache();

        // Auto-discovery: if MMXU LN ref is not configured, run IEDModelDiscovery
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
                logInfo.accept("Auto-discovery: " +
                    config.getMmxuPrefix() + config.getMmxuLnRef() + " @ " + config.getLdInst());
                LOG.info("[" + config.getFeederId() + "] Auto-discovery OK: " +
                    config.getMmxuPrefix() + config.getMmxuLnRef() + " @ " + config.getLdInst());
            } else {
                LOG.warning("[" + config.getFeederId() + "] Auto-discovery: no se encontró LN de medición");
            }
        }

        String ld      = config.getLdInst();
        String iedName = config.getIedName();

        String mmxuLn = config.getMmxuPrefix() + config.getMmxuLnRef();
        String mhaiLn = config.getMhaiLnRef();
        String msqiLn = config.getMsqiLnRef();
        String mmtrLn = config.getMmtrLnRef();
        String mstaLn = config.getMstaLnRef();

        String mmxuBase = iedName + ld + "/" + mmxuLn;
        String mhaiBase = iedName + ld + "/" + mhaiLn;
        String msqiBase = iedName + ld + "/" + msqiLn;
        String mmtrBase = iedName + ld + "/" + mmtrLn;
        String mstaBase = iedName + ld + "/" + mstaLn;

        // MMXU — voltages: try PhV first, then PPV
        phsAPhVRef = reader.findMxRef(mmxuBase, new String[]{"PhV.phsA", "PPV.phsAB"});
        phsBPhVRef = reader.findMxRef(mmxuBase, new String[]{"PhV.phsB", "PPV.phsBC"});
        phsCPhVRef = reader.findMxRef(mmxuBase, new String[]{"PhV.phsC", "PPV.phsCA"});

        phsAARef = mmxuBase + ".A.phsA";
        phsBARef = mmxuBase + ".A.phsB";
        phsCARef = mmxuBase + ".A.phsC";

        wRef   = mmxuBase + ".TotW";
        varRef = mmxuBase + ".TotVAr";
        vaRef  = mmxuBase + ".TotVA";
        pfRef  = mmxuBase + ".TotPF";
        hzRef  = mmxuBase + ".Hz";

        thdAL1Ref = mhaiBase + ".ThdA.phsA";
        thdAL2Ref = mhaiBase + ".ThdA.phsB";
        thdAL3Ref = mhaiBase + ".ThdA.phsC";

        thdPpvL12Ref = mhaiBase + ".ThdPPV.phsAB";
        thdPpvL23Ref = mhaiBase + ".ThdPPV.phsBC";
        thdPpvL31Ref = mhaiBase + ".ThdPPV.phsCA";

        kfL1Ref = mhaiBase + ".HKf.phsA";
        kfL2Ref = mhaiBase + ".HKf.phsB";
        kfL3Ref = mhaiBase + ".HKf.phsC";

        thdOddAL1Ref  = mhaiBase + ".ThdOddA.phsA";
        thdEvenAL1Ref = mhaiBase + ".ThdEvnA.phsA";

        seqAposRef = msqiBase + ".SeqA.c1";
        seqAnegRef = msqiBase + ".SeqA.c2";
        seqVposRef = msqiBase + ".SeqV.c1";
        seqVnegRef = msqiBase + ".SeqV.c2";

        totWhRef   = mmtrBase + ".TotWh";
        totVAhRef  = mmtrBase + ".TotVAh";
        totVArhRef = mmtrBase + ".TotVArh";
        supWhRef   = mmtrBase + ".SupWh";
        supVArhRef = mmtrBase + ".SupVArh";

        avgWRef   = mstaBase + ".AvW";
        maxWRef   = mstaBase + ".MaxW";
        minWRef   = mstaBase + ".MinW";
        avgVArRef = mstaBase + ".AvVAr";
        avgVARef  = mstaBase + ".AvVA";

        // Harmonic array HA — default: dot-index base 0
        for (int h = 0; h < 50; h++) {
            haPhsAHarRef[h] = mhaiBase + ".HA.phsAHar." + h;
            haPhsBHarRef[h] = mhaiBase + ".HA.phsBHar." + h;
            haPhsCHarRef[h] = mhaiBase + ".HA.phsCHar." + h;
        }

        // Verify MHAI availability and detect harmonic array pattern
        harmonicsAvailable = (serverModel != null &&
            serverModel.findModelNode(thdAL1Ref, Fc.MX) != null);

        if (serverModel != null && harmonicsAvailable) {
            String[] harVariants = {
                mhaiBase + ".HarA.h01.mag.f",
                mhaiBase + ".HA.phsAHar.0.cVal.mag.f",
                mhaiBase + ".HA.phsAHar.1.cVal.mag.f",
                mhaiBase + ".HA.phsAHar01.cVal.mag.f",
            };
            String workingPattern = null;
            for (String variant : harVariants) {
                if (serverModel.findModelNode(variant, Fc.MX) != null ||
                    serverModel.findModelNode(variant, null) != null) {
                    workingPattern = variant;
                    break;
                }
            }
            harmonicArrayInModel = (workingPattern != null);
            LOG.info("Harmonicos en modelo: " + harmonicArrayInModel +
                     (workingPattern != null ? "  patron: " + workingPattern : "  (no encontrado)"));

            if (workingPattern != null) {
                if (workingPattern.contains(".HarA.h01.")) {
                    for (int h = 0; h < 50; h++) {
                        String hn = String.format(".h%02d", h + 1);
                        haPhsAHarRef[h] = mhaiBase + ".HarA" + hn;
                        haPhsBHarRef[h] = mhaiBase + ".HarB" + hn;
                        haPhsCHarRef[h] = mhaiBase + ".HarC" + hn;
                    }
                    LOG.info("Refs HA: HAR50_t h01..h50 (simulador nuevo)");
                } else if (workingPattern.contains(".phsAHar.1.")) {
                    for (int h = 0; h < 50; h++) {
                        haPhsAHarRef[h] = mhaiBase + ".HA.phsAHar." + (h + 1);
                        haPhsBHarRef[h] = mhaiBase + ".HA.phsBHar." + (h + 1);
                        haPhsCHarRef[h] = mhaiBase + ".HA.phsCHar." + (h + 1);
                    }
                    LOG.info("Refs HA: dot-index base 1 (ION 7400 real)");
                } else if (workingPattern.contains(".phsAHar01.")) {
                    for (int h = 0; h < 50; h++) {
                        haPhsAHarRef[h] = mhaiBase + ".HA.phsAHar" + String.format("%02d", h + 1);
                        haPhsBHarRef[h] = mhaiBase + ".HA.phsBHar" + String.format("%02d", h + 1);
                        haPhsCHarRef[h] = mhaiBase + ".HA.phsCHar" + String.format("%02d", h + 1);
                    }
                    LOG.info("Refs HA: zero-padded legacy");
                } else {
                    LOG.info("Refs HA: dot-index base 0 (simulador antiguo)");
                }
            }

            if (!harmonicArrayInModel) reader.dumpMhaiStructure(mhaiBase);
        }

        if (!powerScaleDetected) {
            autoDetectPowerScale(association);
            powerScaleDetected = true;
        }

        // Populate node cache
        reader.cacheNodePair(phsAPhVRef); reader.cacheNodePair(phsBPhVRef); reader.cacheNodePair(phsCPhVRef);
        reader.cacheNodePair(phsAARef);   reader.cacheNodePair(phsBARef);   reader.cacheNodePair(phsCARef);
        reader.cacheNodePair(wRef);       reader.cacheNodePair(varRef);     reader.cacheNodePair(vaRef);
        reader.cacheNodePair(pfRef);      reader.cacheNodePair(hzRef);
        if (harmonicsAvailable) {
            reader.cacheNodePair(thdAL1Ref);    reader.cacheNodePair(thdAL2Ref);    reader.cacheNodePair(thdAL3Ref);
            reader.cacheNodePair(thdPpvL12Ref); reader.cacheNodePair(thdPpvL23Ref); reader.cacheNodePair(thdPpvL31Ref);
            reader.cacheNodePair(kfL1Ref);      reader.cacheNodePair(kfL2Ref);      reader.cacheNodePair(kfL3Ref);
            reader.cacheNodePair(thdOddAL1Ref); reader.cacheNodePair(thdEvenAL1Ref);
            reader.cacheNodePair(seqAposRef);   reader.cacheNodePair(seqAnegRef);
            reader.cacheNodePair(seqVposRef);   reader.cacheNodePair(seqVnegRef);
            reader.cacheNodePair(avgWRef);      reader.cacheNodePair(maxWRef);      reader.cacheNodePair(minWRef);
            reader.cacheNodePair(avgVArRef);    reader.cacheNodePair(avgVARef);
            if (harmonicArrayInModel) {
                for (int h = 0; h < 50; h++) {
                    reader.cacheNodePair(haPhsAHarRef[h]);
                    reader.cacheNodePair(haPhsBHarRef[h]);
                    reader.cacheNodePair(haPhsCHarRef[h]);
                }
            }
        }
        reader.cacheStNode(totWhRef);  reader.cacheStNode(totVAhRef);  reader.cacheStNode(totVArhRef);
        reader.cacheStNode(supWhRef);  reader.cacheStNode(supVArhRef);
        LOG.info("[" + config.getFeederId() + "] Node cache poblado");
        LOG.info("MMXU base: " + mmxuBase + "  MHAI: " + mhaiBase +
                 "  harmonics=" + harmonicsAvailable +
                 "  harmonicArray=" + harmonicArrayInModel +
                 "  powerScale=" + config.getPowerScaleFactor());
    }

    // ── readAll() — called by IEC61850Communicator.readMeasurement() ──────────

    /**
     * Reads a complete measurement snapshot from MMXU + MHAI + MSQI + MMTR + MSTA.
     */
    public FeederMeasurement readAll(ClientAssociation association) {
        FeederMeasurement m = new FeederMeasurement(config.getFeederId(), config.getIedName());
        final double as  = config.getAnalogScaleFactor();
        final double ps  = config.getPowerScaleFactor();
        final double pfs = config.getPfScaleFactor();
        try {
            m.setVoltageL1(reader.readMxFloat(phsAPhVRef, association) * as);
            m.setVoltageL2(reader.readMxFloat(phsBPhVRef, association) * as);
            m.setVoltageL3(reader.readMxFloat(phsCPhVRef, association) * as);
            m.setCurrentL1(reader.readMxFloat(phsAARef, association) * as);
            m.setCurrentL2(reader.readMxFloat(phsBARef, association) * as);
            m.setCurrentL3(reader.readMxFloat(phsCARef, association) * as);
            m.setActivePower(reader.readMxFloat(wRef, association)   * ps);
            m.setReactivePower(reader.readMxFloat(varRef, association) * ps);
            m.setApparentPower(reader.readMxFloat(vaRef, association)  * ps);
            m.setPowerFactor(Math.min(1.0, reader.readMxFloat(pfRef, association) * pfs));
            m.setFrequency(reader.readMxFloat(hzRef, association) * as);
            m.setDataValid(true);
            m.setQualityFlag("GOOD");
        } catch (ServiceError | IOException e) {
            LOG.warning("Error leyendo MMXU [" + config.getFeederId() + "]: " + e.getMessage());
            m.setDataValid(false);
            m.setQualityFlag("COMM_ERROR");
        }

        if (harmonicsAvailable) {
            try { m.setThdCurrentL1(reader.readMxFloat(thdAL1Ref, association)); } catch (Exception ignored) {}
            try { m.setThdCurrentL2(reader.readMxFloat(thdAL2Ref, association)); } catch (Exception ignored) {}
            try { m.setThdCurrentL3(reader.readMxFloat(thdAL3Ref, association)); } catch (Exception ignored) {}
            try { m.setThdPpvL12(reader.readMxFloat(thdPpvL12Ref, association)); } catch (Exception ignored) {}
            try { m.setThdPpvL23(reader.readMxFloat(thdPpvL23Ref, association)); } catch (Exception ignored) {}
            try { m.setThdPpvL31(reader.readMxFloat(thdPpvL31Ref, association)); } catch (Exception ignored) {}
            try { m.setKFactorL1(reader.readMxFloat(kfL1Ref, association)); }       catch (Exception ignored) {}
            try { m.setKFactorL2(reader.readMxFloat(kfL2Ref, association)); }       catch (Exception ignored) {}
            try { m.setKFactorL3(reader.readMxFloat(kfL3Ref, association)); }       catch (Exception ignored) {}
            try { m.setThdOddCurrentL1(reader.readMxFloat(thdOddAL1Ref, association)); }  catch (Exception ignored) {}
            try { m.setThdEvenCurrentL1(reader.readMxFloat(thdEvenAL1Ref, association)); } catch (Exception ignored) {}

            if (harmonicArrayInModel) {
                harmonicsPollCounter++;
                if (harmonicsPollCounter >= HARMONICS_READ_EVERY_N) {
                    harmonicsPollCounter = 0;
                    double[] tryA = reader.readHarmonicArray(haPhsAHarRef, association);
                    for (int i = 0; i < tryA.length; i++) { if (tryA[i] > 1e30f) tryA[i] = 0.0; }
                    if (tryA[0] > 1e-9) {
                        cachedHarPhsA = tryA;
                        double[] tryB = reader.readHarmonicArray(haPhsBHarRef, association);
                        double[] tryC = reader.readHarmonicArray(haPhsCHarRef, association);
                        for (int i = 0; i < 50; i++) {
                            if (tryB[i] > 1e30f) tryB[i] = 0.0;
                            if (tryC[i] > 1e30f) tryC[i] = 0.0;
                        }
                        cachedHarPhsB = tryB;
                        cachedHarPhsC = tryC;
                        LOG.info("Harmonicos leidos del IED [" + config.getFeederId() +
                                 "] H1(p.u.)=" + tryA[0] + " H3(p.u.)=" + tryA[2] +
                                 " H5(p.u.)=" + tryA[4] + " H7(p.u.)=" + tryA[6]);
                    } else {
                        LOG.warning("Lectura HA retorno ceros o invalidos [" + config.getFeederId() +
                                    "] tryA[0]=" + tryA[0]);
                    }
                }
                if (cachedHarPhsA != null) {
                    double[] harA = cachedHarPhsA.clone();
                    double[] harB = cachedHarPhsB.clone();
                    double[] harC = cachedHarPhsC.clone();
                    double iL1 = m.getCurrentL1() > 1e-6 ? m.getCurrentL1() : 1.0;
                    double iL2 = m.getCurrentL2() > 1e-6 ? m.getCurrentL2() : iL1;
                    double iL3 = m.getCurrentL3() > 1e-6 ? m.getCurrentL3() : iL1;
                    if (harA[0] > 1e-9) {
                        double scaleA = (harA[0] <= 1.01) ? iL1 : iL1 / harA[0];
                        double scaleB = (harB[0] <= 1.01) ? iL2 : iL2 / harB[0];
                        double scaleC = (harC[0] <= 1.01) ? iL3 : iL3 / harC[0];
                        for (int i = 0; i < 50; i++) { harA[i] *= scaleA; harB[i] *= scaleB; harC[i] *= scaleC; }
                        harA[0] = iL1; harB[0] = iL2; harC[0] = iL3;
                    }
                    m.setHarmonicCurrentL1(harA);
                    m.setHarmonicCurrentL2(harB);
                    m.setHarmonicCurrentL3(harC);
                }
            }

            try {
                double as2    = config.getAnalogScaleFactor();
                double seqApos = reader.readMxFloat(seqAposRef, association) * as2;
                double seqAneg = reader.readMxFloat(seqAnegRef, association) * as2;
                double seqVpos = reader.readMxFloat(seqVposRef, association) * as2;
                double seqVneg = reader.readMxFloat(seqVnegRef, association) * as2;
                m.setSeqCurrentPos(seqApos);
                m.setSeqCurrentNeg(seqAneg);
                m.setSeqVoltagePos(seqVpos);
                m.setSeqVoltageNeg(seqVneg);
                if (seqVpos > 0) m.setVoltageUnbalancePct(seqVneg / seqVpos * 100.0);
                if (seqApos > 0) m.setCurrentUnbalancePct(seqAneg / seqApos * 100.0);
            } catch (Exception ignored) {}
        }

        final double ps2 = config.getPowerScaleFactor();
        try { m.setTotalEnergyKWh(reader.readStInt64(totWhRef, association) / 1000.0); }   catch (Exception ignored) {}
        try { m.setTotalEnergyKVAh(reader.readStInt64(totVAhRef, association) / 1000.0); }  catch (Exception ignored) {}
        try { m.setTotalEnergyKVArh(reader.readStInt64(totVArhRef, association) / 1000.0); } catch (Exception ignored) {}
        try { m.setSupplyKWh(reader.readStInt64(supWhRef, association) / 1000.0); }          catch (Exception ignored) {}
        try { m.setSupplyKVArh(reader.readStInt64(supVArhRef, association) / 1000.0); }      catch (Exception ignored) {}

        try { m.setDemandAvgKW(reader.readMxFloat(avgWRef, association) * ps2); }   catch (Exception ignored) {}
        try { m.setDemandMaxKW(reader.readMxFloat(maxWRef, association) * ps2); }   catch (Exception ignored) {}
        try { m.setDemandMinKW(reader.readMxFloat(minWRef, association) * ps2); }   catch (Exception ignored) {}
        try { m.setDemandAvgKVAr(reader.readMxFloat(avgVArRef, association) * ps2); } catch (Exception ignored) {}
        try { m.setDemandAvgKVA(reader.readMxFloat(avgVARef, association) * ps2); }   catch (Exception ignored) {}

        return m;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Auto-detects powerScaleFactor by comparing raw TotW against V×I from the IED.
     * Immune to TotW.units.multiplier default values in iec61850bean.
     */
    private void autoDetectPowerScale(ClientAssociation association) {
        if (serverModel == null || wRef == null
                || phsAPhVRef == null || phsAARef == null) return;
        try {
            float rawW   = reader.readMxFloat(wRef, association);
            float vPhsA  = reader.readMxFloat(phsAPhVRef, association);
            float iPhsA  = reader.readMxFloat(phsAARef, association);

            if (rawW <= 0 || vPhsA <= 0 || iPhsA <= 0) {
                config.setPowerScaleFactor(0.001);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: valores nulos → ps=0.001 (fallback)");
                return;
            }

            double sApparent = 3.0 * vPhsA * iPhsA;
            double ratioIfW  = rawW / sApparent;
            double ratioIfKW = rawW * 1000.0 / sApparent;

            LOG.info(String.format("[%s] Auto-detect sanity: rawW=%.1f V=%.1f I=%.1f S=%.1f ratioW=%.4f ratioKW=%.4f",
                    config.getFeederId(), rawW, vPhsA, iPhsA, sApparent, ratioIfW, ratioIfKW));

            boolean wPlausible  = ratioIfW  >= 0.01 && ratioIfW  <= 1.10;
            boolean kwPlausible = ratioIfKW >= 0.01 && ratioIfKW <= 1.10;

            if (wPlausible && !kwPlausible) {
                config.setPowerScaleFactor(0.001);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: IED reporta W → ps=0.001");
            } else if (kwPlausible && !wPlausible) {
                config.setPowerScaleFactor(1.0);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: IED reporta kW → ps=1.0");
            } else {
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
}
