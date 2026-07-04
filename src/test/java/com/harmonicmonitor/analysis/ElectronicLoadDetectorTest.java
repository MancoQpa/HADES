package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del árbol de decisión de {@link ElectronicLoadDetector}.
 *
 * <p>Los 10 perfiles parametrizados son la versión ejecutable de la tabla
 * maestra de {@code docs/tabla_patrones_armonicos.md} (§1, §2, §4 y §7):
 * cada fila reproduce los valores exactos del perfil del simulador y
 * verifica que el clasificador devuelve el {@code LoadType} esperado.
 * Si un cambio de umbral en {@code FeederConfig} o en el árbol rompe una
 * traza documentada, estos tests lo detectan.
 *
 * <p>Convenciones: THD por fase idéntico en L1/L2/L3 (el promedio RMS
 * coincide con el valor de la tabla); CV = 0.02 salvo indicación (perfiles
 * de simulador 24/7); Q y S en kVAR/kVA según tabla §7.
 */
class ElectronicLoadDetectorTest {

    private final ElectronicLoadDetector detector = new ElectronicLoadDetector();

    // ── Perfiles de tabla_patrones_armonicos.md ─────────────────────────────

    /** { nombre, LoadType esperado, thdI%, thdV%, cv, h3, h5, h7, h11, h13, fp, K, Q_kvar, S_kva } */
    static Stream<Object[]> perfilesTablaMaestra() {
        return Stream.of(
            //          perfil                 esperado                        thdI  thdV  cv    h3     h5     h7     h11    h13    fp      K     Q      S
            new Object[]{"normal_load",        LoadType.LINEAR,                5.0,  2.5, 0.02, 0.030, 0.040, 0.020, 0.008, 0.006, 0.98,  1.20,   79.0,  398.0},
            new Object[]{"linear_load",        LoadType.LINEAR,                4.0,  2.0, 0.02, 0.010, 0.030, 0.020, 0.010, 0.005, 0.85,  1.10, 1889.0, 3586.0},
            new Object[]{"lighting",           LoadType.LIGHTING,             12.0,  2.5, 0.02, 0.400, 0.060, 0.030, 0.015, 0.010, 0.85,  1.50, 1259.0, 2390.0},
            new Object[]{"electronic_light",   LoadType.ELECTRONIC_LIGHT,     10.0,  2.8, 0.02, 0.150, 0.100, 0.040, 0.010, 0.005, 0.88,  1.50, 1514.0, 3187.0},
            new Object[]{"industrial",         LoadType.INDUSTRIAL,           26.0,  4.2, 0.02, 0.020, 0.250, 0.110, 0.090, 0.080, 0.93,  4.80, 1903.0, 5179.0},
            new Object[]{"data_center",        LoadType.DATA_CENTER,          20.0,  3.8, 0.02, 0.050, 0.280, 0.180, 0.040, 0.020, 0.88,  4.60, 2839.0, 5976.0},
            new Object[]{"mixed_electronic",   LoadType.MIXED_ELECTRONIC,      7.0,  2.2, 0.02, 0.120, 0.060, 0.040, 0.010, 0.005, 0.91,  1.30, 1652.0, 3984.0},
            new Object[]{"crypto_mining",      LoadType.CRYPTO_MINING,        42.0,  4.5, 0.02, 0.030, 0.350, 0.220, 0.070, 0.050, 0.985, 6.80, 1167.0, 6773.0},
            new Object[]{"crypto_mining_pfc",  LoadType.CRYPTO_MINING_PFC,     3.0,  1.9, 0.01, 0.002, 0.039, 0.002, 0.009, 0.000, 1.0000, 1.05,   6.0, 1875.0},
            new Object[]{"upstream_distortion",LoadType.UPSTREAM_DISTORTION,   3.5,  6.5, 0.03, 0.010, 0.025, 0.015, 0.005, 0.003, 0.95,  1.10, 1749.0, 3790.0}
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("perfilesTablaMaestra")
    void perfilDeTablaMaestraClasificaSegunLoDocumentado(
            String perfil, LoadType esperado,
            double thdI, double thdV, double cv,
            double h3, double h5, double h7, double h11, double h13,
            double fp, double k, double qKvar, double sKva) {

        FeederMeasurement m = medicion(thdI, thdV, cv, h3, h5, h7, h11, h13, fp, k, qKvar, sKva);
        detector.classify(m, new FeederConfig());

        assertEquals(esperado, m.getDetectedLoadType(),
            "Perfil '" + perfil + "' debe clasificar como " + esperado
            + " según docs/tabla_patrones_armonicos.md §4");
    }

    // ── Indicadores derivados del perfil PFC (tabla §3 y §6) ────────────────

    @Test
    void perfilPfcPersisteIndicadoresDerivadosEsperados() {
        FeederMeasurement m = medicion(3.0, 1.9, 0.01,
            0.002, 0.039, 0.002, 0.009, 0.000, 1.0000, 1.05, 6.0, 1875.0);
        detector.classify(m, new FeederConfig());

        assertEquals(19.5, m.getH5h7Ratio(), 0.01, "H5/H7 = 0.039/0.002 (tabla §6)");
        assertEquals(0.0032, m.getQsRatio(), 0.0001, "Q/S = 6/1875 (tabla §6)");
        assertTrue(m.getPfcCryptoScore() >= 60.0,
            "PFC score esperado ~92 (tabla §3); >= 60 indica firma PFC relevante. Fue: "
            + m.getPfcCryptoScore());
    }

    @Test
    void indicadoresDerivadosSePersistenAunqueLaClaseNoSeaPfc() {
        // Perfil industrial: la clase es INDUSTRIAL pero h5h7Ratio y qsRatio
        // deben quedar calculados igualmente (contrato de classify()).
        FeederMeasurement m = medicion(26.0, 4.2, 0.02,
            0.020, 0.250, 0.110, 0.090, 0.080, 0.93, 4.80, 1903.0, 5179.0);
        detector.classify(m, new FeederConfig());

        assertEquals(0.250 / 0.110, m.getH5h7Ratio(), 0.01);
        assertEquals(1903.0 / 5179.0, m.getQsRatio(), 0.001);
    }

    // ── Nodo [2] CRYPTO_MINING_PFC: bordes del vector ───────────────────────

    @Test
    void pfcConFactorDePotenciaBajoUmbralCaeEnLinear() {
        // Mismo perfil PFC pero FP = 0.99 (< 0.998): no cumple el vector y,
        // con THD 3% y H5 3.9%, la regla LINEAR del nodo [3] lo captura.
        FeederMeasurement m = medicion(3.0, 1.9, 0.01,
            0.002, 0.039, 0.002, 0.009, 0.000, 0.99, 1.05, 6.0, 1875.0);
        detector.classify(m, new FeederConfig());

        assertEquals(LoadType.LINEAR, m.getDetectedLoadType());
    }

    @Test
    void pfcSinKFactorDelIedSigueDetectandoConKNeutro() {
        // K = 0 (IED no reporta HKf): la condición D es neutra y el AND
        // completo pasa con las 5 restantes (THD, FP, Q/S, H5/H7, CV).
        FeederMeasurement m = medicion(3.0, 1.9, 0.01,
            0.002, 0.039, 0.002, 0.009, 0.000, 1.0000, 0.0, 6.0, 1875.0);
        detector.classify(m, new FeederConfig());

        assertEquals(LoadType.CRYPTO_MINING_PFC, m.getDetectedLoadType());
    }

    @Test
    void pfcSinKFactorUsaModoRelajadoCuatroDeCinco() {
        // Ruta relajada genuina: K = 0 y CV = 0.06 (falla la condición F).
        // Cumplen 4 de 5 (THD, FP, Q/S, H5/H7) → suficiente según nodo [2].
        FeederMeasurement m = medicion(3.0, 1.9, 0.06,
            0.002, 0.039, 0.002, 0.009, 0.000, 1.0000, 0.0, 6.0, 1875.0);
        detector.classify(m, new FeederConfig());

        assertEquals(LoadType.CRYPTO_MINING_PFC, m.getDetectedLoadType());
    }

    @Test
    void pfcConKFactorAltoNoClasificaComoPfc() {
        // K = 1.5 (rectificador sin PFC) rompe la condición D del vector.
        FeederMeasurement m = medicion(3.0, 1.9, 0.01,
            0.002, 0.039, 0.002, 0.009, 0.000, 1.0000, 1.5, 6.0, 1875.0);
        detector.classify(m, new FeederConfig());

        assertEquals(LoadType.LINEAR, m.getDetectedLoadType());
    }

    // ── Guardas de los helpers estáticos ────────────────────────────────────

    @Test
    void h5h7RatioConH7NuloDevuelveVeinteSiH5EsMedible() {
        assertEquals(20.0, ElectronicLoadDetector.computeH5h7Ratio(0.039, 0.0), 1e-9,
            "H7 ≈ 0 con H5 medible → 20.0 (semántica: PFC puro)");
    }

    @Test
    void h5h7RatioConEspectroNuloDevuelveUno() {
        assertEquals(1.0, ElectronicLoadDetector.computeH5h7Ratio(0.0, 0.0), 1e-9,
            "Sin H5 ni H7 → 1.0 (neutro)");
    }

    @Test
    void qsRatioConPotenciaAparenteNulaDevuelveCero() {
        FeederMeasurement m = new FeederMeasurement("F1", "IED1");
        m.setReactivePower(5.0);
        m.setApparentPower(0.0);
        assertEquals(0.0, ElectronicLoadDetector.computeQsRatio(m), 1e-9,
            "S = 0 → 0.0 (guarda de división por cero)");
    }

    // ── Modo degradado: sin espectro (ratios en 0) ──────────────────────────

    @Test
    void sinEspectroYTodoEnCeroClasificaLinearPorDefecto() {
        FeederMeasurement m = new FeederMeasurement("F1", "IED1");
        detector.classify(m, new FeederConfig());
        assertEquals(LoadType.LINEAR, m.getDetectedLoadType());
    }

    @Test
    void sinEspectroConThdAltoYFpMedioYaNoCaeEnLighting() {
        // Antes de exigir H3 medido, la regla LIGHTING capturaba mediciones
        // sin espectro (ratios = 0) solo por THD alto + FP medio. Con la
        // condición h3h1 > 0.15, en modo degradado cae en MIXED_ELECTRONIC.
        FeederMeasurement m = medicion(12.0, 2.5, 0.10,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.85, 0.0, 1259.0, 2390.0);
        detector.classify(m, new FeederConfig());

        assertEquals(LoadType.MIXED_ELECTRONIC, m.getDetectedLoadType());
    }

    // ── LIGHTING y transformadores Dyn en feeders 23 kV ─────────────────────

    @Test
    void iluminacionVistaDesdeMtTrasDynSinH3NoReclamaLighting() {
        // Feeder 23 kV tras transformadores Dyn (23000/380 V): el H3 balanceado
        // (secuencia cero) queda atrapado en el delta y no llega al punto de
        // medición. Del espectro LED solo sobreviven H5/H7 moderados.
        // Sin evidencia H3, el clasificador NO debe reclamar LIGHTING:
        // el residuo es indistinguible de otra electrónica monofásica.
        FeederMeasurement m = medicion(11.0, 2.5, 0.08,
            0.02, 0.060, 0.030, 0.010, 0.008, 0.85, 1.20, 1259.0, 2390.0);
        detector.classify(m, new FeederConfig());

        assertEquals(LoadType.MIXED_ELECTRONIC, m.getDetectedLoadType(),
            "Sin H3 visible (Dyn lo atrapa) no hay evidencia de iluminación");
    }

    @Test
    void iluminacionConH3VisiblePeroEnElBordeNoReclamaLighting() {
        // h3h1 = 0.15 exacto no supera el umbral estricto (> 0.15).
        FeederMeasurement m = medicion(12.0, 2.5, 0.08,
            0.15, 0.060, 0.030, 0.010, 0.008, 0.85, 1.20, 1259.0, 2390.0);
        detector.classify(m, new FeederConfig());

        assertEquals(LoadType.MIXED_ELECTRONIC, m.getDetectedLoadType());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Construye una medición con los campos que consume classify().
     * THD idéntico por fase (promedio RMS = valor); K idéntico por fase.
     */
    private static FeederMeasurement medicion(double thdI, double thdV, double cv,
                                              double h3, double h5, double h7,
                                              double h11, double h13,
                                              double fp, double k,
                                              double qKvar, double sKva) {
        FeederMeasurement m = new FeederMeasurement("F1", "IED1");
        m.setThdCurrentL1(thdI); m.setThdCurrentL2(thdI); m.setThdCurrentL3(thdI);
        m.setThdVoltageL1(thdV); m.setThdVoltageL2(thdV); m.setThdVoltageL3(thdV);
        m.setCvCurrent(cv);
        m.setH3h1Ratio(h3);
        m.setH5h1Ratio(h5);
        m.setH7h1Ratio(h7);
        m.setH11h1Ratio(h11);
        m.setH13h1Ratio(h13);
        m.setPowerFactor(fp);
        m.setKFactorL1(k); m.setKFactorL2(k); m.setKFactorL3(k);
        m.setReactivePower(qKvar);
        m.setApparentPower(sKva);
        return m;
    }
}
