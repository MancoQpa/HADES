package com.harmonicmonitor.comm;

import com.beanit.iec61850bean.ClientAssociation;
import com.harmonicmonitor.model.FeederConfig;

import java.util.logging.Logger;

/**
 * Auto-detects the power scale factor for a feeder by comparing the raw TotW
 * reading from the IED against the apparent power derived from V and I readings.
 *
 * This approach is immune to TotW.units.multiplier default values in iec61850bean.
 *
 * Extracted from MmsDataMapper.autoDetectPowerScale() (refactor F21-001).
 */
final class PowerScaleDetector {

    private static final Logger LOG = Logger.getLogger(PowerScaleDetector.class.getName());

    private PowerScaleDetector() {}

    /**
     * Reads TotW, V and I from the IED and updates {@code config.powerScaleFactor}.
     * Falls back to {@code 0.001} (W→kW) on any error or zero reading.
     */
    static void detect(FeederConfig config,
                       String wRef, String phsAPhVRef, String phsAARef,
                       ClientAssociation association, MmsNodeReader reader) {
        if (wRef == null || phsAPhVRef == null || phsAARef == null) return;
        try {
            float rawW  = reader.readMxFloat(wRef, association);
            float vPhsA = reader.readMxFloat(phsAPhVRef, association);
            float iPhsA = reader.readMxFloat(phsAARef, association);

            if (rawW <= 0 || vPhsA <= 0 || iPhsA <= 0) {
                config.setPowerScaleFactor(0.001);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: valores nulos -> ps=0.001 (fallback)");
                return;
            }

            double sApparent = 3.0 * vPhsA * iPhsA;
            double ratioIfW  = rawW / sApparent;
            double ratioIfKW = rawW * 1000.0 / sApparent;

            LOG.info(String.format(
                "[%s] Auto-detect sanity: rawW=%.1f V=%.1f I=%.1f S=%.1f ratioW=%.4f ratioKW=%.4f",
                config.getFeederId(), rawW, vPhsA, iPhsA, sApparent, ratioIfW, ratioIfKW));

            boolean wPlausible  = ratioIfW  >= 0.01 && ratioIfW  <= 1.10;
            boolean kwPlausible = ratioIfKW >= 0.01 && ratioIfKW <= 1.10;

            if (wPlausible && !kwPlausible) {
                config.setPowerScaleFactor(0.001);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: IED reporta W -> ps=0.001");
            } else if (kwPlausible && !wPlausible) {
                config.setPowerScaleFactor(1.0);
                LOG.info("[" + config.getFeederId() + "] Auto-detect: IED reporta kW -> ps=1.0");
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
            LOG.info("[" + config.getFeederId() + "] Auto-detect error: " + e.getMessage() + " -> ps=0.001");
        }
    }
}
