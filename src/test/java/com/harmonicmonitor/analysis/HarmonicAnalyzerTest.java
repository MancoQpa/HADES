package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederMeasurement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de {@link HarmonicAnalyzer}: cálculo de ratios armónicos
 * (incluido H3/H1, usado por la regla LIGHTING) y guardas.
 */
class HarmonicAnalyzerTest {

    private final HarmonicAnalyzer analyzer = new HarmonicAnalyzer();

    @Test
    void calculateHarmonicRatiosIncluyeH3() {
        FeederMeasurement m = new FeederMeasurement("F1", "IED1");
        double[] spec = new double[50];
        spec[0]  = 100.0;  // H1
        spec[2]  = 40.0;   // H3
        spec[4]  = 6.0;    // H5
        spec[6]  = 3.0;    // H7
        spec[10] = 1.5;    // H11
        spec[12] = 1.0;    // H13
        m.setHarmonicCurrentL1(spec);

        analyzer.calculateHarmonicRatios(m);

        assertEquals(0.40,  m.getH3h1Ratio(),  1e-9);
        assertEquals(0.06,  m.getH5h1Ratio(),  1e-9);
        assertEquals(0.03,  m.getH7h1Ratio(),  1e-9);
        assertEquals(0.015, m.getH11h1Ratio(), 1e-9);
        assertEquals(0.01,  m.getH13h1Ratio(), 1e-9);
    }

    @Test
    void calculateHarmonicRatiosSinEspectroDejaRatiosEnCero() {
        // Modo degradado: espectro en ceros (H1 < 1e-9) → guarda temprana,
        // los ratios quedan en 0 y no se estima nada.
        FeederMeasurement m = new FeederMeasurement("F1", "IED1");
        analyzer.calculateHarmonicRatios(m);

        assertEquals(0.0, m.getH3h1Ratio(), 1e-9);
        assertEquals(0.0, m.getH5h1Ratio(), 1e-9);
        assertEquals(0.0, m.getH7h1Ratio(), 1e-9);
    }

    @Test
    void ratiosUsanLaFaseDeMayorDistorsion() {
        // Selección de fase peor (práctica IEEE 519 / EN 50160): la carga
        // distorsionante está concentrada en L2; L1 casi limpia.
        FeederMeasurement m = new FeederMeasurement("F1", "IED1");
        double[] l1 = new double[50];
        l1[0] = 100.0; l1[4] = 2.0;                 // L1: THD 2%
        double[] l2 = new double[50];
        l2[0] = 100.0; l2[4] = 25.0; l2[6] = 11.0;  // L2: firma 6 pulsos
        m.setHarmonicCurrentL1(l1);
        m.setHarmonicCurrentL2(l2);

        analyzer.calculateHarmonicRatios(m);

        assertEquals(0.25, m.getH5h1Ratio(), 1e-9, "H5 debe salir de L2 (fase peor)");
        assertEquals(0.11, m.getH7h1Ratio(), 1e-9, "H7 debe salir de L2 (fase peor)");
    }

    @Test
    void ratiosMantienenCoherenciaIntraFase() {
        // Contraejemplo contra el "máximo por orden": L1 tiene firma PFC
        // (H5/H7 = 19.5) y L2 tiene THD apenas mayor con otra forma.
        // TODOS los ratios deben salir de la misma fase (L2, la peor);
        // mezclar órdenes entre fases fabricaría un vector que no existe.
        FeederMeasurement m = new FeederMeasurement("F1", "IED1");
        double[] l1 = new double[50];
        l1[0] = 100.0; l1[4] = 3.9; l1[6] = 0.2;    // PFC: THD ~3.9%
        double[] l2 = new double[50];
        l2[0] = 100.0; l2[4] = 4.2; l2[6] = 1.0;    // THD ~4.3% (peor)
        m.setHarmonicCurrentL1(l1);
        m.setHarmonicCurrentL2(l2);

        analyzer.calculateHarmonicRatios(m);

        assertEquals(0.042, m.getH5h1Ratio(), 1e-9);
        assertEquals(0.010, m.getH7h1Ratio(), 1e-9,
            "H7 debe ser el de L2, no el 0.002 de L1 (coherencia intra-fase)");
    }

    @Test
    void thdPercentCalculaSegunIec61000_4_7() {
        // THD = 100·√(ΣHn²)/H1 = 100·√(40²+30²)/100 = 50%
        double[] spec = new double[50];
        spec[0] = 100.0;
        spec[2] = 40.0;
        spec[4] = 30.0;
        assertEquals(50.0, analyzer.thdPercent(spec), 1e-9);
    }
}
