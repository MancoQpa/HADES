package com.harmonicmonitor.model;

/**
 * Perfiles de carga simulada para SimulatedPoller.
 * Cada perfil modela el comportamiento armónico y eléctrico de un tipo de carga real.
 */
public enum SimProfile {

    CRYPTO_MINER(
        "Cripto-Miner / SMPS sin PFC",
        "THD ~45%, H5/H7 dominantes, muy estable (CV<1%), FP ~0.99"),

    DATACENTER(
        "Datacenter (servidores con PFC activo)",
        "THD ~18%, H3 reducido por PFC, muy estable, FP ~0.99, alta densidad de potencia"),

    ARC_FURNACE(
        "Electrointensiva — Horno de Arco Eléctrico",
        "H2/H3 pares+impares, alta reactiva fluctuante, flicker, FP 0.70–0.85"),

    VARIABLE_SPEED_DRIVE(
        "Variador de Velocidad — VFD / Inversor",
        "H5/H7/H11/H13 (patrón 6 pulsos), THD ~25%, FP ~0.94, ciclos rampa"),

    INDUSTRIAL_LINEAR(
        "Industrial Lineal — Motores directos",
        "THD <5%, motores jaula de ardilla, FP 0.80–0.88, carga variable"),

    MIXED_COMMERCIAL(
        "Carga Mixta Comercial / Edificio",
        "THD 10–20%, variaciones diurnas, FP 0.88–0.95, mezcla residencial/comercial");

    public final String displayName;
    public final String description;

    SimProfile(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public String toString() { return displayName; }
}
