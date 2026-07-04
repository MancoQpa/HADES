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
    void thdPercentCalculaSegunIec61000_4_7() {
        // THD = 100·√(ΣHn²)/H1 = 100·√(40²+30²)/100 = 50%
        double[] spec = new double[50];
        spec[0] = 100.0;
        spec[2] = 40.0;
        spec[4] = 30.0;
        assertEquals(50.0, analyzer.thdPercent(spec), 1e-9);
    }
}
