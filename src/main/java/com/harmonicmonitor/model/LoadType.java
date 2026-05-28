package com.harmonicmonitor.model;

/**
 * Clasificación del tipo de carga detectada en el alimentador MT.
 */
public enum LoadType {
    UNKNOWN              ("Desconocido",                    "#808080"),
    LINEAR               ("Carga Lineal",                    "#2196F3"),
    ELECTRONIC_LIGHT     ("Carga Electrónica Ligera",        "#FF9800"),
    CRYPTO_MINING        ("Minería Cripto",                  "#F44336"),
    /**
     * Minería de criptomonedas con PFC activo (Active Power Factor Correction).
     *
     * Equipos ASIC modernos (Antminer S19/S21, Whatsminer M30/M50, Jasminer X16)
     * incorporan fuentes de alimentación con PFC boost converter en modo CCM
     * (Continuous Conduction Mode). El PFC suprime la distorsión de corriente
     * logrando THD < 6% y FP ≈ 1.000, haciendo que la firma espectral se asemeje
     * a una carga lineal para clasificadores basados únicamente en THD.
     *
     * La detección se basa en el vector multidimensional:
     *   {PF > 0.998, Q/S < 0.012, K-Factor ∈ [1.0,1.12], H5/H7 > 8.0,
     *    THD ∈ [1.5%,6.5%], CV < 5%}
     *
     * Validado con medición de campo ION7400-0d5885 (10.200.142.125),
     * alimentador exclusivo de criptominería, 26/05/2026.
     */
    CRYPTO_MINING_PFC    ("Minería Cripto (PFC Activo)",     "#FF6D00"),
    DATA_CENTER          ("Centro de Datos",                 "#9C27B0"),
    MIXED_ELECTRONIC     ("Mixta Electrónica",               "#FF5722"),
    INDUSTRIAL           ("Industrial",                      "#4CAF50"),
    LIGHTING             ("Iluminación",                     "#FFEB3B"),
    UPSTREAM_DISTORTION  ("Distorsión Aguas Arriba",         "#00BCD4");

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
