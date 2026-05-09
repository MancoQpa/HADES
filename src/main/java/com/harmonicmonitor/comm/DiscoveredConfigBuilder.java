package com.harmonicmonitor.comm;

import com.harmonicmonitor.model.FeederConfig;

/**
 * Builds the suggested {@link FeederConfig} from a {@link DiscoveryResult}
 * by mapping the best LN candidates onto the configuration fields.
 *
 * Extracted from IEDModelDiscovery.buildSuggestedConfig() (refactor F26-001).
 */
final class DiscoveredConfigBuilder {

    private DiscoveredConfigBuilder() {}

    /**
     * Creates a new {@code FeederConfig} by copying connection parameters from
     * {@code base} and filling LN references from the discovery result.
     *
     * @param result   discovery result with best LN candidates already selected
     * @param base     base config supplying connection and nominal-value fields
     * @param iedName  inferred IED name (used only if {@code base.getIedName()} is blank)
     * @return         a ready-to-use {@code FeederConfig} for the discovered IED
     */
    static FeederConfig build(DiscoveryResult result, FeederConfig base, String iedName) {
        FeederConfig cfg = new FeederConfig();

        // Copy connection fields from base
        cfg.setIedHost(base.getIedHost());
        cfg.setIedPort(base.getIedPort());
        cfg.setFeederId(base.getFeederId());
        cfg.setFeederName(base.getFeederName());
        cfg.setDescription(base.getDescription());
        cfg.setNominalVoltageKv(base.getNominalVoltageKv());
        cfg.setNominalCurrentA(base.getNominalCurrentA());
        cfg.setPollIntervalMs(base.getPollIntervalMs());

        cfg.setIedName(iedName.isEmpty() ? base.getIedName() : iedName);

        // MMXU (or MMXN fallback)
        DiscoveryResult.FoundNode mmxu = result.getBestMMXU();
        if (mmxu != null) {
            cfg.setLdInst(mmxu.ldInst);
            cfg.setMmxuPrefix(mmxu.prefix);
            cfg.setMmxuLnRef(mmxu.lnClass + mmxu.inst);   // e.g. "MMXU1"
        } else {
            cfg.setLdInst(base.getLdInst());
        }

        // MHAI
        DiscoveryResult.FoundNode mhai = result.getBestMHAI();
        cfg.setMhaiLnRef(mhai != null ? mhai.prefix + mhai.lnClass + mhai.inst : "");

        // MSQI
        DiscoveryResult.FoundNode msqi = result.getBestMSQI();
        cfg.setMsqiLnRef(msqi != null ? msqi.prefix + msqi.lnClass + msqi.inst : "");

        // MMTR
        DiscoveryResult.FoundNode mmtr = result.getBestMMTR();
        cfg.setMmtrLnRef(mmtr != null ? mmtr.prefix + mmtr.lnClass + mmtr.inst : "");

        // MSTA
        DiscoveryResult.FoundNode msta = result.getBestMSTA();
        cfg.setMstaLnRef(msta != null ? msta.prefix + msta.lnClass + msta.inst : "");

        return cfg;
    }
}
