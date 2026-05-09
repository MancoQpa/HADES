package com.harmonicmonitor.comm;

import com.beanit.iec61850bean.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Low-level MMS node reading and caching for IEC 61850 MX/ST attributes.
 *
 * Extracted from {@link MmsDataMapper} (refactor F9-001).
 * Handles:
 * <ul>
 *   <li>Node cache (avoids repeated {@code findModelNode()} per polling cycle)</li>
 *   <li>Float reads for WYE/DEL/SEQ and scalar MV nodes (FC=MX)</li>
 *   <li>INT64 reads for BCR energy counters (FC=ST)</li>
 *   <li>Harmonic array batch reads</li>
 *   <li>Model structure diagnostics</li>
 * </ul>
 */
class MmsNodeReader {

    private static final Logger LOG = Logger.getLogger(MmsNodeReader.class.getName());

    // ── NodePair ─────────────────────────────────────────────────────────────
    /** Pairs the FC=MX parent node (used for getDataValues) with its leaf BDA. */
    static final class NodePair {
        final FcModelNode parent;
        final ModelNode   leaf;
        NodePair(FcModelNode parent, ModelNode leaf) {
            this.parent = parent;
            this.leaf   = leaf;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Map<String, NodePair>  mxNodeCache = new HashMap<>();
    private final Map<String, ModelNode> stNodeCache = new HashMap<>();
    private ServerModel serverModel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    void setServerModel(ServerModel model) {
        this.serverModel = model;
    }

    void clearCache() {
        mxNodeCache.clear();
        stNodeCache.clear();
    }

    // ── Node caching ──────────────────────────────────────────────────────────

    /**
     * Discovers and caches a FC=MX node.
     * Supports WYE/DEL/SEQ (ref.cVal.mag.f) and scalar MV (ref.mag.f).
     */
    void cacheNodePair(String ref) {
        if (ref == null || serverModel == null) return;

        // Attempt 1: WYE/DEL/SEQ sub-element → ref.cVal.mag.f
        String cValRef = ref + ".cVal.mag.f";
        ModelNode leaf = serverModel.findModelNode(cValRef, Fc.MX);
        if (leaf == null) leaf = serverModel.findModelNode(cValRef, null);
        if (leaf != null) {
            ModelNode parent = serverModel.findModelNode(ref, Fc.MX);
            FcModelNode fcParent = (parent instanceof FcModelNode) ? (FcModelNode) parent
                                 : (leaf   instanceof FcModelNode) ? (FcModelNode) leaf
                                 : null;
            if (fcParent != null) mxNodeCache.put(ref, new NodePair(fcParent, leaf));
            return;
        }

        // Attempt 2: scalar MV → ref.mag.f
        String magRef = ref + ".mag.f";
        leaf = serverModel.findModelNode(magRef, Fc.MX);
        if (leaf == null) leaf = serverModel.findModelNode(magRef, null);
        if (leaf != null) {
            ModelNode parent = serverModel.findModelNode(ref, Fc.MX);
            FcModelNode fcParent = (parent instanceof FcModelNode) ? (FcModelNode) parent
                                 : (leaf   instanceof FcModelNode) ? (FcModelNode) leaf
                                 : null;
            if (fcParent != null) mxNodeCache.put(ref, new NodePair(fcParent, leaf));
        }
    }

    /**
     * Discovers and caches the actVal (FC=ST, INT64) of a BCR energy node.
     */
    void cacheStNode(String ref) {
        if (ref == null || serverModel == null) return;
        ModelNode node = serverModel.findModelNode(ref + ".actVal", Fc.ST);
        if (node != null) stNodeCache.put(ref, node);
    }

    // ── Reference helpers ─────────────────────────────────────────────────────

    /**
     * Returns the first available FC=MX reference from the candidates list,
     * or the first candidate as fallback if none found in the model.
     */
    String findMxRef(String base, String[] candidates) {
        if (serverModel != null) {
            for (String c : candidates) {
                String ref = base + "." + c;
                if (serverModel.findModelNode(ref, Fc.MX) != null) return ref;
            }
        }
        return base + "." + candidates[0];
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Reads a float analog measurement (FC=MX).
     * Supports WYE/DEL/SEQ sub-elements ({@code ref.cVal.mag.f}) and scalar MV ({@code ref.mag.f}).
     * Uses the node cache when available; falls back to a full model traversal otherwise.
     */
    float readMxFloat(String ref, ClientAssociation association) throws ServiceError, IOException {
        if (ref == null) return 0.0f;
        if (association == null || serverModel == null)
            throw new IOException("Conexión cerrada durante la lectura");

        // Fast path: node already cached from buildRefs()
        NodePair pair = mxNodeCache.get(ref);
        if (pair != null) {
            try {
                association.getDataValues(pair.parent);
            } catch (ServiceError se) {
                if (pair.leaf instanceof FcModelNode)
                    association.getDataValues((FcModelNode) pair.leaf);
            }
            if (pair.leaf instanceof BdaFloat32) return ((BdaFloat32) pair.leaf).getFloat();
            if (pair.leaf instanceof BdaFloat64) return (float)((double) ((BdaFloat64) pair.leaf).getDouble());
            return 0.0f;
        }

        // Slow path — WYE/DEL/SEQ: ref.cVal.mag.f
        String cValMagFRef = ref + ".cVal.mag.f";
        ModelNode cValMagF = serverModel.findModelNode(cValMagFRef, Fc.MX);
        if (cValMagF == null) cValMagF = serverModel.findModelNode(cValMagFRef, null);
        if (cValMagF != null) {
            ModelNode phsNode = serverModel.findModelNode(ref, Fc.MX);
            if (phsNode instanceof FcModelNode) {
                try {
                    association.getDataValues((FcModelNode) phsNode);
                } catch (ServiceError se) {
                    if (cValMagF instanceof FcModelNode)
                        association.getDataValues((FcModelNode) cValMagF);
                }
            } else if (cValMagF instanceof FcModelNode) {
                association.getDataValues((FcModelNode) cValMagF);
            }
            if (cValMagF instanceof BdaFloat32) return ((BdaFloat32) cValMagF).getFloat();
            if (cValMagF instanceof BdaFloat64) return (float)((double) ((BdaFloat64) cValMagF).getDouble());
            return 0.0f;
        }

        // Slow path — scalar MV / HAR50_t DA: ref.mag.f
        String magFRef = ref + ".mag.f";
        ModelNode magF = serverModel.findModelNode(magFRef, Fc.MX);
        if (magF == null) magF = serverModel.findModelNode(magFRef, null);
        if (magF != null) {
            ModelNode mvNode = serverModel.findModelNode(ref, Fc.MX);
            if (mvNode instanceof FcModelNode) {
                try {
                    association.getDataValues((FcModelNode) mvNode);
                } catch (ServiceError se) {
                    if (magF instanceof FcModelNode)
                        association.getDataValues((FcModelNode) magF);
                }
            } else if (magF instanceof FcModelNode) {
                association.getDataValues((FcModelNode) magF);
            }
            if (magF instanceof BdaFloat32) return ((BdaFloat32) magF).getFloat();
            if (magF instanceof BdaFloat64) return (float)((double) ((BdaFloat64) magF).getDouble());
        }

        return 0.0f;
    }

    /**
     * Reads actVal (INT64) from a BCR energy node (FC=ST) — for MMTR counters.
     */
    long readStInt64(String ref, ClientAssociation association) throws ServiceError, IOException {
        if (ref == null || association == null || serverModel == null) return 0L;

        ModelNode cached = stNodeCache.get(ref);
        if (cached instanceof FcModelNode) {
            association.getDataValues((FcModelNode) cached);
            if (cached instanceof BdaInt64) return ((BdaInt64) cached).getValue();
            if (cached instanceof BdaInt32) return ((BdaInt32) cached).getValue();
            return 0L;
        }

        String actValRef = ref + ".actVal";
        ModelNode node = serverModel.findModelNode(actValRef, Fc.ST);
        if (node instanceof FcModelNode) {
            association.getDataValues((FcModelNode) node);
            if (node instanceof BdaInt64) return ((BdaInt64) node).getValue();
            if (node instanceof BdaInt32) return ((BdaInt32) node).getValue();
        }
        return 0L;
    }

    /**
     * Reads one channel of the HA harmonic array (phsXHar01..phsXHar50).
     * Returns double[50]: index 0 = H1 (fundamental), index 1 = H2, …, index 49 = H50.
     */
    double[] readHarmonicArray(String[] harRefs, ClientAssociation association) {
        double[] result = new double[50];
        if (association == null || serverModel == null) return result;
        for (int i = 0; i < 50 && i < harRefs.length; i++) {
            try {
                result[i] = readMxFloat(harRefs[i], association);
            } catch (Exception ignored) {}
        }
        return result;
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    /**
     * Dumps the node structure under mhaiBase to the log for harmonic path diagnosis.
     */
    void dumpMhaiStructure(String mhaiBase) {
        if (serverModel == null) return;
        ModelNode mhai = serverModel.findModelNode(mhaiBase, null);
        if (mhai == null) {
            LOG.warning("MHAI no encontrado en modelo: " + mhaiBase);
            return;
        }
        StringBuilder sb = new StringBuilder("Estructura MHAI [" + mhaiBase + "]:\n");
        dumpNode(mhai, "  ", sb, 0);
        LOG.info(sb.toString());
    }

    private void dumpNode(ModelNode node, String indent, StringBuilder sb, int depth) {
        if (depth > 4) return;
        if (node instanceof LogicalNode || node instanceof FcDataObject || node instanceof FcModelNode) {
            for (ModelNode child : node) {
                sb.append(indent).append(child.getName());
                if (child instanceof FcModelNode)
                    sb.append(" [FC=").append(((FcModelNode) child).getFc()).append("]");
                sb.append("\n");
                dumpNode(child, indent + "  ", sb, depth + 1);
            }
        }
    }
}
