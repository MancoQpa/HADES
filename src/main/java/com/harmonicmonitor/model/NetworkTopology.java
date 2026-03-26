package com.harmonicmonitor.model;

/**
 * Topologías de red MT soportadas por el monitor de armónicos.
 * Define cómo interactúan múltiples alimentadores entre sí y la barra MT.
 */
public enum NetworkTopology {
    SINGLE_FEEDER     ("Alimentador único",                        "Un solo feeder monitoreado"),
    MIXED_FEEDER      ("Feeder mixto (industrial + electrónico)",   "Cargas lineales y electrónicas en el mismo feeder"),
    DEDICATED_CRYPTO  ("Feeder dedicado cripto/datacenter",         "Todo el feeder con cargas SMPS de alta densidad"),
    MULTI_FEEDER      ("Multi-feeder con barra común",              "Varios feeders en la misma barra MT"),
    PARALLEL_FEEDERS  ("Feeders en paralelo",                      "Dos feeders en paralelo con la misma carga"),
    BUS_COUPLER       ("Acoplador de barra",                       "Dos semibuses MT acoplados"),
    DUAL_BUS          ("Doble barra",                              "Sistema con barra principal y barra reserva");

    private final String displayName;
    private final String description;

    NetworkTopology(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName()  { return displayName; }
    public String getDescription()  { return description; }

    @Override
    public String toString() { return displayName; }
}
