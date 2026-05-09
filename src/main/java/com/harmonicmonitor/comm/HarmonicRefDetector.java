package com.harmonicmonitor.comm;

import com.beanit.iec61850bean.Fc;
import com.beanit.iec61850bean.ServerModel;

import java.util.logging.Logger;

/**
 * Detects the harmonic array reference pattern used by an IEC 61850 MHAI logical node
 * and rewrites the per-harmonic reference arrays accordingly.
 *
 * Four patterns are probed (ION 7400, simulator v1, simulator v2, legacy zero-padded).
 *
 * Extracted from MmsDataMapper.buildRefs() (refactor F21-001).
 */
final class HarmonicRefDetector {

    private static final Logger LOG = Logger.getLogger(HarmonicRefDetector.class.getName());

    private HarmonicRefDetector() {}

    /**
     * Identifies the harmonic reference pattern and rewrites {@code harA/B/C} in-place.
     *
     * @param model    server model to probe
     * @param mhaiBase base reference for the MHAI LN (e.g. "cbo2LD0/MHAI1")
     * @param harA     50-element array for phase A harmonic refs — modified in-place
     * @param harB     50-element array for phase B harmonic refs — modified in-place
     * @param harC     50-element array for phase C harmonic refs — modified in-place
     * @param reader   used to dump MHAI structure when pattern is not found
     * @return {@code true} if a harmonic array was found in the model
     */
    static boolean detect(ServerModel model, String mhaiBase,
                          String[] harA, String[] harB, String[] harC,
                          MmsNodeReader reader) {
        String[] candidates = {
            mhaiBase + ".HarA.h01.mag.f",
            mhaiBase + ".HA.phsAHar.0.cVal.mag.f",
            mhaiBase + ".HA.phsAHar.1.cVal.mag.f",
            mhaiBase + ".HA.phsAHar01.cVal.mag.f",
        };

        String pattern = null;
        for (String c : candidates) {
            if (model.findModelNode(c, Fc.MX) != null || model.findModelNode(c, null) != null) {
                pattern = c;
                break;
            }
        }

        boolean found = (pattern != null);
        LOG.info("Harmonicos en modelo: " + found + (found ? "  patron: " + pattern : "  (no encontrado)"));

        if (!found) {
            reader.dumpMhaiStructure(mhaiBase);
            return false;
        }

        if (pattern.contains(".HarA.h01.")) {
            for (int h = 0; h < 50; h++) {
                String hn = String.format(".h%02d", h + 1);
                harA[h] = mhaiBase + ".HarA" + hn;
                harB[h] = mhaiBase + ".HarB" + hn;
                harC[h] = mhaiBase + ".HarC" + hn;
            }
            LOG.info("Refs HA: HAR50_t h01..h50 (simulador nuevo)");
        } else if (pattern.contains(".phsAHar.1.")) {
            for (int h = 0; h < 50; h++) {
                harA[h] = mhaiBase + ".HA.phsAHar." + (h + 1);
                harB[h] = mhaiBase + ".HA.phsBHar." + (h + 1);
                harC[h] = mhaiBase + ".HA.phsCHar." + (h + 1);
            }
            LOG.info("Refs HA: dot-index base 1 (ION 7400 real)");
        } else if (pattern.contains(".phsAHar01.")) {
            for (int h = 0; h < 50; h++) {
                harA[h] = mhaiBase + ".HA.phsAHar" + String.format("%02d", h + 1);
                harB[h] = mhaiBase + ".HA.phsBHar" + String.format("%02d", h + 1);
                harC[h] = mhaiBase + ".HA.phsCHar" + String.format("%02d", h + 1);
            }
            LOG.info("Refs HA: zero-padded legacy");
        } else {
            LOG.info("Refs HA: dot-index base 0 (simulador antiguo)");
        }

        return true;
    }
}
