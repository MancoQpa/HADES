package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cordura del modelo de mezcla de {@link DominanceStudy}: en los extremos
 * del barrido (w=1 perfil puro, w=0 solo fondo) el clasificador debe
 * reproducir la clase propia de cada perfil. Si estos tests fallan tras un
 * cambio de umbrales, el estudio de dominancia debe regenerarse.
 */
class DominanceStudyTest {

    private final ElectronicLoadDetector detector = new ElectronicLoadDetector();
    private final FeederConfig cfg = new FeederConfig();

    @Test
    void perfilesDominantesPurosClasificanSuClase() {
        for (DominanceStudy.Profile dom : DominanceStudy.DOMINANTS) {
            FeederMeasurement m = DominanceStudy.mix(dom, DominanceStudy.BG_LINEAR, 1.0);
            detector.classify(m, cfg);
            assertEquals(dom.expected, m.getDetectedLoadType(),
                "Perfil puro (w=1.0): " + dom.id);
        }
    }

    @Test
    void fondosPurosClasificanSuClase() {
        for (DominanceStudy.Profile bg : DominanceStudy.BACKGROUNDS) {
            FeederMeasurement m = DominanceStudy.mix(DominanceStudy.CRYPTO, bg, 0.0);
            detector.classify(m, cfg);
            assertEquals(bg.expected, m.getDetectedLoadType(),
                "Fondo puro (w=0.0): " + bg.id);
        }
    }

    @Test
    void laMezclaConservaLaCorrienteFundamentalAprox() {
        // Con FP iguales (fasores colineales) la fundamental se conserva exacta.
        FeederMeasurement m = DominanceStudy.mix(
            DominanceStudy.LIGHTING, DominanceStudy.BG_LINEAR, 0.5); // ambos FP=0.85
        // apparentPower = |I1| * 10 → debe ser 100 A * 10
        assertEquals(1000.0, m.getApparentPower(), 1e-6);
    }

    @Test
    void pfcPuroConservaSusIndicadores() {
        FeederMeasurement m = DominanceStudy.mix(
            DominanceStudy.CRYPTO_PFC, DominanceStudy.BG_LINEAR, 1.0);
        detector.classify(m, cfg);
        assertEquals(LoadType.CRYPTO_MINING_PFC, m.getDetectedLoadType());
        assertEquals(0.0, m.getQsRatio(), 1e-9, "FP=1.0000 → Q=0 → Q/S=0");
    }
}
