package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Estudio sintético de dominancia de carga para el clasificador HADES.
 *
 * <p>La documentación del proyecto afirma que la clasificación "es confiable
 * solo cuando la carga de interés representa ≥~80% de la demanda del feeder".
 * Ese umbral estaba ASUMIDO, no medido. Esta herramienta lo mide: genera
 * mezclas sintéticas (carga dominante + carga de fondo) barriendo la fracción
 * de dominancia w de 100% a 0% y registra en qué punto el clasificador pierde
 * la clase dominante y en qué clase degrada.
 *
 * <h3>Modelo de mezcla (supuestos documentados)</h3>
 * <ul>
 *   <li><b>Fundamental</b>: suma vectorial. Cada carga aporta un fasor de
 *       corriente con ángulo φ = arccos(FP) (ambas inductivas).
 *       P y Q se suman linealmente; FP_mix = P/|I1|.</li>
 *   <li><b>Armónicos</b>: ley de suma de IEC 61000-3-6:2008 §4.4 (ley
 *       exponencial): I_n = (Σ I_n,i^α)^(1/α) con α=1.0 para n&lt;5,
 *       α=1.4 para 5≤n≤10, α=2.0 para n&gt;10. Modela la diversidad de
 *       fase entre fuentes armónicas independientes.</li>
 *   <li><b>THD</b>: derivado del espectro mezclado (autoconsistente).</li>
 *   <li><b>CV</b>: variaciones independientes → suma RMS de σ:
 *       CV_mix = √((I_dom·CV_dom)² + (I_bg·CV_bg)²) / |I1|.</li>
 *   <li><b>K-Factor</b>: recalculado del espectro mezclado
 *       (IEEE C57.110-2018): K = Σ(n²·I_n²)/Σ(I_n²), n=1..13.</li>
 *   <li><b>THD_V</b>: promedio ponderado por w (aproximación; la distorsión
 *       de tensión es propiedad de red, no se mezcla por carga).</li>
 * </ul>
 *
 * <p>Espectros: perfiles de {@code docs/tabla_patrones_armonicos.md} §2,
 * ratios crudos tal como los publica el simulador. El CV de cada perfil es
 * un supuesto de este estudio (dominantes estables 1–4%; fondo residencial
 * lineal 15%, fondo comercial electrónico 10%).
 *
 * <p>Salida: {@code docs/estudio_dominancia.md} (Markdown, UTF-8).
 *
 * <p>Ejecución:
 * <pre>java -cp "classes;classes_test" com.harmonicmonitor.analysis.DominanceStudy</pre>
 */
public final class DominanceStudy {

    /** Órdenes armónicos modelados (índice k ↔ orden ORDERS[k]). */
    static final int[] ORDERS = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};

    // Índices en ORDERS de los armónicos que consume el clasificador
    private static final int IDX_H3 = 1, IDX_H5 = 3, IDX_H7 = 5, IDX_H11 = 9, IDX_H13 = 11;

    /** Perfil sintético (valores de tabla_patrones_armonicos.md §1/§2). */
    static final class Profile {
        final String id;
        final LoadType expected;
        final double pf, thdV, cv;
        final double[] hn;   // ratios Hn/H1 para ORDERS

        Profile(String id, LoadType expected, double pf, double thdV, double cv, double[] hn) {
            this.id = id; this.expected = expected;
            this.pf = pf; this.thdV = thdV; this.cv = cv; this.hn = hn;
        }

        double thdPct() {
            double s = 0;
            for (double v : hn) s += v * v;
            return Math.sqrt(s) * 100.0;
        }
    }

    // ── Cargas dominantes ────────────────────────────────────────────────────
    static final Profile CRYPTO_PFC = new Profile("crypto_mining_pfc", LoadType.CRYPTO_MINING_PFC,
        1.0000, 1.9, 0.01,
        new double[]{0.001, 0.002, 0.001, 0.039, 0.000, 0.002, 0.000, 0.000, 0.000, 0.009, 0.000, 0.000});

    static final Profile CRYPTO = new Profile("crypto_mining", LoadType.CRYPTO_MINING,
        0.985, 4.5, 0.02,
        new double[]{0.000, 0.030, 0.000, 0.350, 0.000, 0.220, 0.000, 0.080, 0.000, 0.070, 0.000, 0.050});

    static final Profile DATA_CENTER = new Profile("data_center", LoadType.DATA_CENTER,
        0.88, 3.8, 0.03,
        new double[]{0.000, 0.050, 0.000, 0.280, 0.000, 0.180, 0.000, 0.060, 0.000, 0.040, 0.000, 0.020});

    static final Profile INDUSTRIAL = new Profile("industrial", LoadType.INDUSTRIAL,
        0.93, 4.2, 0.04,
        new double[]{0.000, 0.020, 0.000, 0.250, 0.000, 0.110, 0.000, 0.010, 0.000, 0.090, 0.000, 0.080});

    static final Profile LIGHTING = new Profile("lighting", LoadType.LIGHTING,
        0.85, 2.5, 0.03,
        new double[]{0.000, 0.400, 0.000, 0.060, 0.000, 0.030, 0.000, 0.020, 0.000, 0.015, 0.000, 0.010});

    // ── Cargas de fondo ──────────────────────────────────────────────────────
    static final Profile BG_LINEAR = new Profile("linear_load (residencial lineal)", LoadType.LINEAR,
        0.85, 2.0, 0.15,
        new double[]{0.000, 0.010, 0.000, 0.030, 0.000, 0.020, 0.000, 0.005, 0.000, 0.010, 0.000, 0.005});

    static final Profile BG_MIXED = new Profile("mixed_electronic (comercial)", LoadType.MIXED_ELECTRONIC,
        0.91, 2.2, 0.10,
        new double[]{0.000, 0.120, 0.000, 0.060, 0.000, 0.040, 0.000, 0.015, 0.000, 0.010, 0.000, 0.005});

    static final Profile[] DOMINANTS   = {CRYPTO_PFC, CRYPTO, DATA_CENTER, INDUSTRIAL, LIGHTING};
    static final Profile[] BACKGROUNDS = {BG_LINEAR, BG_MIXED};

    /**
     * Construye la medición sintética de la mezcla: carga dominante con
     * fracción w del total de corriente fundamental, fondo con (1-w).
     */
    static FeederMeasurement mix(Profile dom, Profile bg, double w) {
        double iDom = w * 100.0;
        double iBg  = (1.0 - w) * 100.0;

        // Fundamental: suma vectorial (ambas cargas inductivas)
        double phiD = Math.acos(Math.min(dom.pf, 1.0));
        double phiB = Math.acos(Math.min(bg.pf, 1.0));
        double p  = iDom * Math.cos(phiD) + iBg * Math.cos(phiB);
        double q  = iDom * Math.sin(phiD) + iBg * Math.sin(phiB);
        double i1 = Math.hypot(p, q);
        double pfMix = (i1 > 1e-9) ? p / i1 : 1.0;

        // Armónicos: ley de suma IEC 61000-3-6 (α = 1 / 1.4 / 2 según orden)
        double[] hn = new double[ORDERS.length];
        double sumSq = 0, sumN2Sq = 0;
        for (int k = 0; k < ORDERS.length; k++) {
            int n = ORDERS[k];
            double alpha = (n < 5) ? 1.0 : (n <= 10 ? 1.4 : 2.0);
            double in = Math.pow(
                Math.pow(iDom * dom.hn[k], alpha) + Math.pow(iBg * bg.hn[k], alpha),
                1.0 / alpha);
            hn[k] = (i1 > 1e-9) ? in / i1 : 0.0;
            sumSq   += in * in;
            sumN2Sq += (double) n * n * in * in;
        }
        double thd  = (i1 > 1e-9) ? Math.sqrt(sumSq) / i1 * 100.0 : 0.0;
        double thdV = w * dom.thdV + (1.0 - w) * bg.thdV;
        double cv   = (i1 > 1e-9) ? Math.hypot(iDom * dom.cv, iBg * bg.cv) / i1 : 0.0;
        double kf   = (i1 * i1 + sumN2Sq) / (i1 * i1 + sumSq);   // IEEE C57.110, n=1..13

        FeederMeasurement m = new FeederMeasurement("STUDY", "SYN");
        m.setThdCurrentL1(thd);  m.setThdCurrentL2(thd);  m.setThdCurrentL3(thd);
        m.setThdVoltageL1(thdV); m.setThdVoltageL2(thdV); m.setThdVoltageL3(thdV);
        m.setCvCurrent(cv);
        m.setH3h1Ratio(hn[IDX_H3]);
        m.setH5h1Ratio(hn[IDX_H5]);
        m.setH7h1Ratio(hn[IDX_H7]);
        m.setH11h1Ratio(hn[IDX_H11]);
        m.setH13h1Ratio(hn[IDX_H13]);
        m.setPowerFactor(pfMix);
        m.setKFactorL1(kf); m.setKFactorL2(kf); m.setKFactorL3(kf);
        m.setReactivePower(q * 10.0);   // escala arbitraria consistente:
        m.setApparentPower(i1 * 10.0);  // el clasificador usa solo el ratio |Q|/S
        return m;
    }

    /** Tramo contiguo de dominancia [wLow, wHigh] con la misma clase. */
    static final class Segment {
        final double wHigh; double wLow;
        final LoadType type;
        Segment(double wHigh, LoadType type) { this.wHigh = wHigh; this.wLow = wHigh; this.type = type; }
    }

    /** Barre w de 1.00 a 0.00 y devuelve los tramos de clasificación. */
    static List<Segment> sweep(Profile dom, Profile bg) {
        ElectronicLoadDetector det = new ElectronicLoadDetector();
        FeederConfig cfg = new FeederConfig();
        List<Segment> segs = new ArrayList<>();
        for (int i = 1000; i >= 0; i--) {
            double w = i / 1000.0;
            FeederMeasurement m = mix(dom, bg, w);
            det.classify(m, cfg);
            LoadType t = m.getDetectedLoadType();
            if (segs.isEmpty() || segs.get(segs.size() - 1).type != t) {
                segs.add(new Segment(w, t));
            }
            segs.get(segs.size() - 1).wLow = w;
        }
        return segs;
    }

    /** Dominancia mínima (w más bajo del primer tramo, si arranca en la clase esperada). */
    static double breakpoint(List<Segment> segs, LoadType expected) {
        if (segs.isEmpty() || segs.get(0).type != expected) return Double.NaN;
        return segs.get(0).wLow;
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.US);
        StringBuilder md = new StringBuilder();

        md.append("# Estudio sintético de dominancia de carga — clasificador HADES\n\n");
        md.append("> **Generado por**: `src/test/java/com/harmonicmonitor/analysis/DominanceStudy.java` — ")
          .append("regenerar con `java -cp \"classes;classes_test\" com.harmonicmonitor.analysis.DominanceStudy`\n");
        md.append("> **Perfiles**: `docs/tabla_patrones_armonicos.md` §2 | **Umbrales**: `FeederConfig` por defecto\n\n");

        md.append("## Objetivo\n\n");
        md.append("La documentación afirmaba que la clasificación \"es confiable solo con dominancia ≥~80%\" ");
        md.append("de la carga de interés. Ese umbral estaba **asumido**. Este estudio lo **mide** con mezclas ");
        md.append("sintéticas: se barre la fracción de dominancia w de 100% a 0% (paso 0.1%) y se registra ");
        md.append("dónde el clasificador pierde la clase dominante y en qué clase degrada.\n\n");

        md.append("## Metodología y supuestos\n\n");
        md.append("| Magnitud | Modelo de mezcla | Fundamento |\n|---|---|---|\n");
        md.append("| Fundamental | Suma vectorial de fasores (φ = arccos FP, cargas inductivas) | Circuitos AC elemental |\n");
        md.append("| Armónicos H2–H13 | Ley exponencial: I_n=(Σ I_n,i^α)^(1/α); α=1 (n<5), 1.4 (5≤n≤10), 2 (n>10) | IEC 61000-3-6:2008 §4.4 (diversidad de fase) |\n");
        md.append("| THD_I | Derivado del espectro mezclado (autoconsistente) | IEC 61000-4-7 |\n");
        md.append("| CV | Suma RMS de desviaciones independientes | Estadística (varianzas independientes) |\n");
        md.append("| K-Factor | Recalculado del espectro mezclado, n=1..13 | IEEE C57.110-2018 |\n");
        md.append("| FP, Q/S | P y Q lineales desde los fasores | IEEE 1459-2010 (componentes fundamentales) |\n");
        md.append("| THD_V | Promedio ponderado por w (aproximación) | — (propiedad de red, no de carga) |\n\n");
        md.append("**Supuestos de CV por perfil** (no provienen de la tabla): dominantes estables 1–4% ");
        md.append("(operación 24/7); fondo residencial lineal 15%; fondo comercial electrónico 10%.\n\n");
        md.append("**Límites del estudio**: mezcla sintética de 2 componentes; sin dinámica temporal; ");
        md.append("THD_I derivado del espectro puede diferir del `ThdA` declarado en la tabla §1; ");
        md.append("la ley α de IEC 61000-3-6 modela diversidad de fase típica, no el peor caso coherente.\n\n");

        // ── Verificación de extremos ─────────────────────────────────────────
        md.append("## Verificación de extremos (w=100%: perfil puro)\n\n");
        md.append("| Perfil | THD espectral (%) | Clase esperada | Clase obtenida |\n|---|---|---|---|\n");
        ElectronicLoadDetector det = new ElectronicLoadDetector();
        FeederConfig cfg = new FeederConfig();
        for (Profile pr : DOMINANTS) {
            FeederMeasurement m = mix(pr, BG_LINEAR, 1.0);
            det.classify(m, cfg);
            md.append(String.format("| `%s` | %.1f | %s | %s %s |%n",
                pr.id, pr.thdPct(), pr.expected,
                m.getDetectedLoadType(),
                m.getDetectedLoadType() == pr.expected ? "✓" : "✗ **DISCREPANCIA**"));
        }
        md.append("\n");

        // ── Resumen de breakpoints ───────────────────────────────────────────
        md.append("## Resultado principal — dominancia mínima por clase\n\n");
        md.append("Dominancia mínima w (en % de la corriente fundamental total) con la que el ");
        md.append("clasificador aún reporta la clase dominante:\n\n");
        md.append("| Carga dominante | vs. fondo lineal residencial | vs. fondo comercial electrónico |\n|---|---|---|\n");
        for (Profile dom : DOMINANTS) {
            md.append(String.format("| `%s` (%s) |", dom.id, dom.expected));
            for (Profile bg : BACKGROUNDS) {
                double bp = breakpoint(sweep(dom, bg), dom.expected);
                md.append(Double.isNaN(bp)
                    ? " no detectada ni al 100% |"
                    : String.format(" **%.1f%%** |", bp * 100.0));
            }
            md.append("\n");
        }
        md.append("\n");

        // ── Tablas de transición detalladas ──────────────────────────────────
        md.append("## Curvas de degradación (tramos de clasificación)\n\n");
        for (Profile dom : DOMINANTS) {
            for (Profile bg : BACKGROUNDS) {
                md.append(String.format("### `%s` + fondo `%s`%n%n", dom.id, bg.id));
                md.append("| Dominancia w | Clase reportada |\n|---|---|\n");
                for (Segment s : sweep(dom, bg)) {
                    md.append(String.format("| %.1f%% – %.1f%% | %s%s |%n",
                        s.wHigh * 100.0, s.wLow * 100.0, s.type,
                        s.type == dom.expected ? " ✓" : ""));
                }
                md.append("\n");
            }
        }

        md.append("## Interpretación\n\n");
        md.append("- El umbral único \"≥80%\" **no describe el comportamiento real**: la dominancia mínima ");
        md.append("depende fuertemente de la clase. Las clases con guardas de baja tolerancia (CRYPTO_MINING_PFC: ");
        md.append("Q/S ≤ 0.012 y FP ≥ 0.998) pierden la detección con muy poca carga de fondo, porque la reactiva ");
        md.append("del fondo contamina Q/S mucho antes de que se diluya el espectro.\n");
        md.append("- Las clases espectrales (INDUSTRIAL, DATA_CENTER, CRYPTO_MINING) resisten más: sus armónicos ");
        md.append("dominan la mezcla hasta fracciones menores. El CV del fondo suele ser la condición que rompe primero.\n");
        md.append("- Uso operativo: consultar la tabla de dominancia mínima por clase en lugar del 80% global. ");
        md.append("Cuando la dominancia estimada del feeder esté por debajo del valor de su clase objetivo, ");
        md.append("la clase reportada por HADES debe tratarse como orientativa y complementarse con el análisis ");
        md.append("temporal (componente constante vs. variable del espectro acumulado).\n");

        Path out = Path.of(args.length > 0 ? args[0] : "docs/estudio_dominancia.md");
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("Estudio escrito en: " + out.toAbsolutePath());

        // Resumen a consola
        for (Profile dom : DOMINANTS) {
            for (Profile bg : BACKGROUNDS) {
                double bp = breakpoint(sweep(dom, bg), dom.expected);
                System.out.printf("%-22s vs %-32s -> dominancia minima %s%n",
                    dom.id, bg.id, Double.isNaN(bp) ? "N/A" : String.format("%.1f%%", bp * 100));
            }
        }
    }

    private DominanceStudy() {}
}
