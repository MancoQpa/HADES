package com.harmonicmonitor.model;

/**
 * Clasificación del tipo de carga detectada en el alimentador MT.
 */
public enum LoadType {
    UNKNOWN         ("Desconocido",         "#808080"),
    LINEAR          ("Carga Lineal",         "#2196F3"),
    ELECTRONIC_LIGHT("Carga Electrónica Ligera", "#FF9800"),
    CRYPTO_MINING   ("Minería Cripto",       "#F44336"),
    DATA_CENTER     ("Centro de Datos",      "#9C27B0"),
    MIXED_ELECTRONIC("Mixta Electrónica",    "#FF5722"),
    INDUSTRIAL      ("Industrial",           "#4CAF50"),
    LIGHTING        ("Iluminación",          "#FFEB3B"),
    UPSTREAM_DISTORTION("Distorsión Aguas Arriba", "#00BCD4");

    private final String displayName;
    private final String colorHex;

    LoadType(String displayName, String colorHex) {
        this.displayName = displayName;
        this.colorHex    = colorHex;
    }

    public String getDisplayName() { return displayName; }
    public String getColorHex()    { return colorHex; }

    @Override
    public String toString() { return displayName; }
}
