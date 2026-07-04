package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de {@link LoadTypeSmoother}: ventana deslizante + histéresis.
 *
 * <p>El caso central reproduce el hallazgo empírico de {@code docs/estudio_roc.md}:
 * el perfil {@code normal_load} (THD clavado en 5.0%) oscilaba 50.7% LINEAR /
 * 49.3% MIXED_ELECTRONIC muestra a muestra. Con el smoother, la clase reportada
 * no debe parpadear.
 */
class LoadTypeSmootherTest {

    private static final int N = 15;
    private static final double F = 0.67;   // supermayoría: ceil(0.67*15) = 11

    @Test
    void oscilacionCincuentaCincuentaNoParpadea() {
        // Caso empírico normal_load: LINEAR/MIXED alternando por ruido.
        LoadTypeSmoother s = new LoadTypeSmoother(N, F);
        LoadType first = s.add(LoadType.LINEAR);
        assertEquals(LoadType.LINEAR, first, "adopción inicial reactiva");

        int switches = 0;
        LoadType prev = first;
        for (int i = 0; i < 2000; i++) {
            LoadType raw = (i % 2 == 0) ? LoadType.MIXED_ELECTRONIC : LoadType.LINEAR;
            LoadType stable = s.add(raw);
            if (stable != prev) switches++;
            prev = stable;
        }
        assertEquals(0, switches,
            "una oscilación 50/50 nunca alcanza supermayoría: cero conmutaciones");
        assertEquals(LoadType.LINEAR, s.getStable());
    }

    @Test
    void cambioRealDeCargaConmutaTrasSupermayoria() {
        LoadTypeSmoother s = new LoadTypeSmoother(N, F);
        for (int i = 0; i < 30; i++) s.add(LoadType.LINEAR);
        assertEquals(LoadType.LINEAR, s.getStable());

        // Cambio real: la carga pasa a firma cripto de forma sostenida
        int samplesToSwitch = 0;
        while (s.getStable() != LoadType.CRYPTO_MINING && samplesToSwitch < 100) {
            s.add(LoadType.CRYPTO_MINING);
            samplesToSwitch++;
        }
        assertEquals(LoadType.CRYPTO_MINING, s.getStable(), "el cambio sostenido conmuta");
        assertEquals((int) Math.ceil(F * N), samplesToSwitch,
            "latencia de conmutación = supermayoría (11 muestras con N=15, f=0.67)");
    }

    @Test
    void transitorioBreveNoConmuta() {
        LoadTypeSmoother s = new LoadTypeSmoother(N, F);
        for (int i = 0; i < 30; i++) s.add(LoadType.LINEAR);

        // 5 muestras de transitorio (arranque de un motor, hueco, etc.)
        for (int i = 0; i < 5; i++) {
            assertEquals(LoadType.LINEAR, s.add(LoadType.INDUSTRIAL),
                "un transitorio < supermayoría no debe conmutar la clase estable");
        }
        // La carga vuelve a lineal
        for (int i = 0; i < 15; i++) s.add(LoadType.LINEAR);
        assertEquals(LoadType.LINEAR, s.getStable());
    }

    @Test
    void estabilidadReflejaLaFraccionDeVentanaCoincidente() {
        LoadTypeSmoother s = new LoadTypeSmoother(10, 0.7);
        for (int i = 0; i < 10; i++) s.add(LoadType.LINEAR);
        assertEquals(1.0, s.stability(), 1e-9, "ventana unánime");

        for (int i = 0; i < 3; i++) s.add(LoadType.MIXED_ELECTRONIC);
        assertEquals(0.7, s.stability(), 1e-9, "7 de 10 coinciden con la estable");
    }

    @Test
    void resetVuelveAlEstadoInicial() {
        LoadTypeSmoother s = new LoadTypeSmoother(N, F);
        for (int i = 0; i < 20; i++) s.add(LoadType.CRYPTO_MINING);
        s.reset();
        assertEquals(LoadType.UNKNOWN, s.getStable());
        assertEquals(0.0, s.stability(), 1e-9);
        assertEquals(LoadType.LINEAR, s.add(LoadType.LINEAR), "readopción reactiva tras reset");
    }

    @Test
    void ventanaDeUnoEquivaleASinSuavizado() {
        LoadTypeSmoother s = new LoadTypeSmoother(1, 0.67);
        assertEquals(LoadType.LINEAR, s.add(LoadType.LINEAR));
        assertEquals(LoadType.CRYPTO_MINING, s.add(LoadType.CRYPTO_MINING),
            "con N=1 cada muestra conmuta (comportamiento anterior)");
    }

    // ── Integración árbol + smoother: el caso normal_load de la ROC ─────────

    @Test
    void integracionArbolMasSmootherEliminaElFlappingDelBordeThd() {
        // Mediciones reales del borde: THD oscilando 4.97–5.03 alrededor del
        // umbral LINEAR (thdI < 5.0). Sin smoother, la clase cruda parpadea;
        // con smoother, la clase estable no cambia nunca tras la adopción.
        ElectronicLoadDetector det = new ElectronicLoadDetector();
        FeederConfig cfg = new FeederConfig();
        LoadTypeSmoother s = new LoadTypeSmoother(
            cfg.getLoadTypeWindowSize(), cfg.getLoadTypeSwitchFraction());

        int rawChanges = 0, stableChanges = 0;
        LoadType prevRaw = null, prevStable = null;
        for (int i = 0; i < 500; i++) {
            double thd = (i % 2 == 0) ? 4.97 : 5.03;   // ruido en el borde
            FeederMeasurement m = new FeederMeasurement("F1", "IED1");
            m.setThdCurrentL1(thd); m.setThdCurrentL2(thd); m.setThdCurrentL3(thd);
            m.setThdVoltageL1(2.5); m.setThdVoltageL2(2.5); m.setThdVoltageL3(2.5);
            m.setCvCurrent(0.006);
            m.setH3h1Ratio(0.030); m.setH5h1Ratio(0.030); m.setH7h1Ratio(0.017);
            m.setH11h1Ratio(0.008); m.setH13h1Ratio(0.006);
            m.setPowerFactor(0.98);
            m.setReactivePower(79.0); m.setApparentPower(398.0);
            det.classify(m, cfg);

            LoadType raw = m.getDetectedLoadType();
            LoadType stable = s.add(raw);
            if (prevRaw != null && raw != prevRaw) rawChanges++;
            if (prevStable != null && stable != prevStable) stableChanges++;
            prevRaw = raw; prevStable = stable;
        }

        assertTrue(rawChanges > 400, "la clase cruda parpadea en el borde (evidencia ROC)");
        assertEquals(0, stableChanges, "la clase estable no parpadea con el smoother");
    }
}
