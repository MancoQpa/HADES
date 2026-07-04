package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Estudio ROC del clasificador HADES sobre los datos acumulados en SQLite
 * ({@code harmonic_monitor.db}, tabla {@code measurements}).
 *
 * <h3>Verdad de terreno</h3>
 * Las etiquetas provienen de sesiones de simulador con perfil conocido,
 * identificadas por coincidencia exacta de sus estadísticas con los perfiles
 * de {@code docs/tabla_patrones_armonicos.md} (I fundamental, S, Q, THD):
 * <ul>
 *   <li><b>Positivos</b> (SMPS de alta densidad): perfiles {@code crypto_mining}
 *       (BCP-2, LAV-1, MET-02, VIN-2, PIL-5) y {@code data_center}
 *       (BCP-4, LAV-3, MET-04).</li>
 *   <li><b>Negativos</b> (carga lineal): perfiles {@code linear_load}
 *       (BCP-3, LAV-2, MET-03) y {@code normal_load} (BCP-5, LAV-4, MET-05).</li>
 * </ul>
 * Excluidos: feeders AL-xx (larga duración, sin verdad de terreno por fila),
 * BCP-1/MET-01/AL-07/SLO-1 (escala de potencia corrupta: FP fuera de [-1,1]),
 * cbo-2/cbo2-AL1 (banco de pruebas ION, condiciones mezcladas).
 *
 * <p><b>Advertencia de circularidad (declarada)</b>: los positivos/negativos son
 * datos de simulador cuyos perfiles se diseñaron con los mismos criterios que el
 * clasificador. Este estudio calibra la separabilidad de las características y
 * la coherencia interna árbol↔perfiles; NO sustituye la calibración con campo real.
 *
 * <p>Ejecución:
 * <pre>java -cp "classes;classes_test;lib/sqlite-jdbc-3.45.3.0.jar" \
 *     com.harmonicmonitor.analysis.RocStudy harmonic_monitor.db</pre>
 */
public final class RocStudy {

    static final Map<String, String> TRUTH = new LinkedHashMap<>();
    static {
        for (String f : new String[]{"BCP-2", "LAV-1", "MET-02", "VIN-2", "PIL-5"})
            TRUTH.put(f, "crypto_mining");
        for (String f : new String[]{"BCP-4", "LAV-3", "MET-04"})
            TRUTH.put(f, "data_center");
        for (String f : new String[]{"BCP-3", "LAV-2", "MET-03"})
            TRUTH.put(f, "linear_load");
        for (String f : new String[]{"BCP-5", "LAV-4", "MET-05"})
            TRUTH.put(f, "normal_load");
    }

    static boolean isPositive(String profile) {
        return profile.equals("crypto_mining") || profile.equals("data_center");
    }

    /** Fila etiquetada con las características que consume el clasificador. */
    static final class Row {
        final String profile;
        final boolean positive;
        final double thdI, thdV, cv, h5, h7, h11, h13, pf, q, s;
        double index;           // índice de electrónica 0-100 (se calcula después)
        LoadType predicted;     // clase del árbol actual (se calcula después)

        Row(String profile, double thdI, double thdV, double cv,
            double h5, double h7, double h11, double h13,
            double pf, double q, double s) {
            this.profile = profile; this.positive = isPositive(profile);
            this.thdI = thdI; this.thdV = thdV; this.cv = cv;
            this.h5 = h5; this.h7 = h7; this.h11 = h11; this.h13 = h13;
            this.pf = pf; this.q = q; this.s = s;
        }
    }

    // ── Carga de datos ───────────────────────────────────────────────────────

    static List<Row> load(String db) throws Exception {
        StringBuilder in = new StringBuilder();
        for (String f : TRUTH.keySet()) in.append(in.length() == 0 ? "" : ",").append("'").append(f).append("'");
        String sql = "SELECT feeder_id, thd_i_l1, thd_i_l2, thd_i_l3, thd_v_l1, thd_v_l2, thd_v_l3, " +
                     "cv_current, h5h1, h7h1, h11h1, h13h1, pf, q_kvar, s_kva " +
                     "FROM measurements WHERE feeder_id IN (" + in + ") " +
                     "AND h5h1 > 0 AND pf BETWEEN -1.0 AND 1.0";
        List<Row> rows = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String prof = TRUTH.get(rs.getString(1));
                double thdI = rms3(rs.getDouble(2), rs.getDouble(3), rs.getDouble(4));
                double thdV = rms3(rs.getDouble(5), rs.getDouble(6), rs.getDouble(7));
                rows.add(new Row(prof, thdI, thdV,
                    rs.getDouble(8), rs.getDouble(9), rs.getDouble(10),
                    rs.getDouble(11), rs.getDouble(12),
                    Math.abs(rs.getDouble(13)), rs.getDouble(14), rs.getDouble(15)));
            }
        }
        return rows;
    }

    static double rms3(double a, double b, double c) {
        double s = 0; int n = 0;
        if (a > 0) { s += a * a; n++; }
        if (b > 0) { s += b * b; n++; }
        if (c > 0) { s += c * c; n++; }
        return n > 0 ? Math.sqrt(s / n) : 0;
    }

    // ── ROC ─────────────────────────────────────────────────────────────────

    /** AUC por suma de rangos (Mann-Whitney), con corrección de empates. */
    static double auc(double[] pos, double[] neg) {
        double[] all = new double[pos.length + neg.length];
        System.arraycopy(pos, 0, all, 0, pos.length);
        System.arraycopy(neg, 0, all, pos.length, neg.length);
        Integer[] idx = new Integer[all.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(all[a], all[b]));
        double[] rank = new double[all.length];
        int i = 0;
        while (i < idx.length) {
            int j = i;
            while (j + 1 < idx.length && all[idx[j + 1]] == all[idx[i]]) j++;
            double r = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) rank[idx[k]] = r;
            i = j + 1;
        }
        double sumPos = 0;
        for (int k = 0; k < pos.length; k++) sumPos += rank[k];
        double u = sumPos - pos.length * (pos.length + 1.0) / 2.0;
        return u / ((double) pos.length * neg.length);
    }

    /** Punto (FPR, TPR) para el umbral t: positivo si (asc ? v>=t : v<=t). */
    static double[] point(double[] pos, double[] neg, double t, boolean asc) {
        long tp = 0, fp = 0;
        for (double v : pos) if (asc ? v >= t : v <= t) tp++;
        for (double v : neg) if (asc ? v >= t : v <= t) fp++;
        return new double[]{(double) fp / neg.length, (double) tp / pos.length};
    }

    /** Umbral óptimo de Youden (max TPR-FPR) barriendo todos los valores. */
    static double[] youden(double[] pos, double[] neg, boolean asc) {
        TreeMap<Double, Boolean> vals = new TreeMap<>();
        for (double v : pos) vals.put(v, true);
        for (double v : neg) vals.putIfAbsent(v, true);
        double bestJ = -1, bestT = Double.NaN, bestTpr = 0, bestFpr = 0;
        for (double t : vals.keySet()) {
            double[] p = point(pos, neg, t, asc);
            double j = p[1] - p[0];
            if (j > bestJ) { bestJ = j; bestT = t; bestTpr = p[1]; bestFpr = p[0]; }
        }
        return new double[]{bestT, bestTpr, bestFpr, bestJ};
    }

    interface Feature { double get(Row r); }

    static void feature(StringBuilder md, List<Row> rows, String name, Feature f,
                        boolean asc, Double currentDefault) {
        double[] pos = rows.stream().filter(r -> r.positive).mapToDouble(f::get).toArray();
        double[] neg = rows.stream().filter(r -> !r.positive).mapToDouble(f::get).toArray();
        // Para dirección descendente (CV): invertir signo para AUC ascendente
        double a = asc ? auc(pos, neg) : auc(negArr(pos), negArr(neg));
        double[] y = youden(pos, neg, asc);
        md.append(String.format("| %s | %.4f | %s%.4g (TPR %.3f, FPR %.3f) |", name, a,
            asc ? "≥ " : "≤ ", y[0], y[1], y[2]));
        if (currentDefault != null) {
            double[] p = point(pos, neg, currentDefault, asc);
            md.append(String.format(" %s%.4g → TPR %.3f, FPR %.3f |%n",
                asc ? "≥ " : "≤ ", currentDefault, p[1], p[0]));
        } else {
            md.append(" — |\n");
        }
    }

    static double[] negArr(double[] v) {
        double[] o = new double[v.length];
        for (int i = 0; i < v.length; i++) o[i] = -v[i];
        return o;
    }

    // ── Main ────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        String db = args.length > 0 ? args[0] : "harmonic_monitor.db";
        List<Row> rows = load(db);

        // Índice de electrónica y clase del árbol ACTUAL por fila
        ElectronicLoadDetector det = new ElectronicLoadDetector();
        FeederConfig cfg = new FeederConfig();
        for (Row r : rows) {
            FeederMeasurement m = new FeederMeasurement("ROC", "DB");
            m.setThdCurrentL1(r.thdI); m.setThdCurrentL2(r.thdI); m.setThdCurrentL3(r.thdI);
            m.setThdVoltageL1(r.thdV); m.setThdVoltageL2(r.thdV); m.setThdVoltageL3(r.thdV);
            m.setCvCurrent(r.cv);
            m.setH5h1Ratio(r.h5); m.setH7h1Ratio(r.h7);
            m.setH11h1Ratio(r.h11); m.setH13h1Ratio(r.h13);
            m.setPowerFactor(r.pf);
            m.setReactivePower(r.q); m.setApparentPower(r.s);
            det.classify(m, cfg);                       // K no disponible en el esquema → 0
            r.predicted = m.getDetectedLoadType();
            r.index = det.calculateElectronicIndex(m, cfg);
        }

        long nPos = rows.stream().filter(r -> r.positive).count();
        long nNeg = rows.size() - nPos;

        StringBuilder md = new StringBuilder();
        md.append("# Estudio ROC — separabilidad de características del clasificador HADES\n\n");
        md.append("> **Generado por**: `src/test/java/com/harmonicmonitor/analysis/RocStudy.java` sobre `harmonic_monitor.db`\n");
        md.append("> Regenerar: `java -cp \"classes;classes_test;lib/sqlite-jdbc-3.45.3.0.jar\" com.harmonicmonitor.analysis.RocStudy`\n\n");

        md.append("## Dataset y verdad de terreno\n\n");
        md.append("Etiquetas por **sesiones de simulador con perfil conocido**, identificadas por la firma\n");
        md.append("estadística exacta de cada feeder contra `docs/tabla_patrones_armonicos.md`:\n\n");
        md.append("| Grupo | Feeders | Perfil (verdad) | Filas |\n|---|---|---|---|\n");
        for (String prof : new String[]{"crypto_mining", "data_center", "linear_load", "normal_load"}) {
            List<String> fs = new ArrayList<>();
            TRUTH.forEach((k, v) -> { if (v.equals(prof)) fs.add(k); });
            long n = rows.stream().filter(r -> r.profile.equals(prof)).count();
            md.append(String.format("| %s | %s | `%s` | %d |%n",
                isPositive(prof) ? "**Positivo** (SMPS densa)" : "Negativo (lineal)",
                String.join(", ", fs), prof, n));
        }
        md.append(String.format("%n**Total**: %d filas (%d positivas, %d negativas).%n%n", rows.size(), nPos, nNeg));
        md.append("**Excluidos**: feeders AL-xx y de campo/banco (103k+ filas) — sin verdad de terreno por fila; ");
        md.append("BCP-1, MET-01, AL-07, SLO-1 — escala de potencia corrupta (FP fuera de [-1,1]); ");
        md.append("filas sin espectro (h5h1 = 0).\n\n");
        md.append("> ⚠ **Naturaleza del dataset (confirmada por el autor, jul-2026)**: la TOTALIDAD de\n");
        md.append("> `harmonic_monitor.db` (~103 MB, 240k+ mediciones, incluidos los feeders AL-xx de larga\n");
        md.append("> duración) proviene de sesiones de **simulador**, no de campo. Consecuencias:\n");
        md.append("> (a) este estudio mide separabilidad de características y coherencia interna árbol↔perfiles,\n");
        md.append("> nada más; (b) ningún umbral empírico (CV, FP, Q/S) puede calibrarse con esta base;\n");
        md.append("> (c) el valor de la base actual para ML es de **validación de tubería** (esquema, exportación,\n");
        md.append("> regresión), no de entrenamiento. El dataset de entrenamiento real empieza con la primera\n");
        md.append("> campaña de campo etiquetada.\n\n");

        md.append("## ROC por característica (positivo = firma SMPS densa: cripto + datacenter)\n\n");
        md.append("| Característica | AUC | Óptimo de Youden | Umbral actual (`FeederConfig`) → desempeño |\n|---|---|---|---|\n");
        feature(md, rows, "THD_I (%)",   r -> r.thdI, true, 15.0);
        feature(md, rows, "H5/H1",       r -> r.h5,   true, 0.15);
        feature(md, rows, "H7/H1",       r -> r.h7,   true, 0.10);
        feature(md, rows, "CV (↓)",      r -> r.cv,   false, 0.05);
        feature(md, rows, "FP",          r -> r.pf,   true, null);
        feature(md, rows, "Índice de electrónica 0-100", r -> r.index, true, null);
        md.append("\n(↓ = discrimina hacia abajo: positivo si valor ≤ umbral. Para CV el AUC se calcula sobre -CV.)\n\n");

        // Matriz de confusión del árbol actual
        md.append("## Matriz de confusión — árbol de decisión ACTUAL sobre las filas etiquetadas\n\n");
        md.append("Nota: el esquema histórico de `measurements` no registra K-Factor ni H3 (columnas añadidas\n");
        md.append("después); el árbol opera aquí sin esas dimensiones (K=0 → condición neutra; H3=0 → LIGHTING inalcanzable).\n\n");
        Map<String, Map<LoadType, Long>> cm = new TreeMap<>();
        for (Row r : rows) {
            cm.computeIfAbsent(r.profile, k -> new TreeMap<>())
              .merge(r.predicted, 1L, Long::sum);
        }
        md.append("| Verdad \\ Predicción | ");
        List<LoadType> seen = new ArrayList<>();
        for (Map<LoadType, Long> m : cm.values())
            for (LoadType t : m.keySet()) if (!seen.contains(t)) seen.add(t);
        for (LoadType t : seen) md.append(t).append(" | ");
        md.append("\n|---|").append("---|".repeat(seen.size())).append("\n");
        for (Map.Entry<String, Map<LoadType, Long>> e : cm.entrySet()) {
            long tot = e.getValue().values().stream().mapToLong(Long::longValue).sum();
            md.append("| `").append(e.getKey()).append("` | ");
            for (LoadType t : seen) {
                long n = e.getValue().getOrDefault(t, 0L);
                md.append(n == 0 ? "—" : String.format("%d (%.1f%%)", n, 100.0 * n / tot)).append(" | ");
            }
            md.append("\n");
        }
        md.append("\n");

        // Distribuciones por grupo (percentiles) para contexto
        md.append("## Percentiles por grupo (contexto de las distribuciones)\n\n");
        md.append("| Perfil | THD_I p5/p50/p95 | H5/H1 p5/p50/p95 | CV p5/p50/p95 | FP p50 |\n|---|---|---|---|---|\n");
        for (String prof : new String[]{"crypto_mining", "data_center", "linear_load", "normal_load"}) {
            List<Row> g = rows.stream().filter(r -> r.profile.equals(prof)).toList();
            if (g.isEmpty()) continue;
            md.append(String.format("| `%s` | %.1f / %.1f / %.1f | %.3f / %.3f / %.3f | %.4f / %.4f / %.4f | %.3f |%n",
                prof,
                pct(g, r -> r.thdI, 5), pct(g, r -> r.thdI, 50), pct(g, r -> r.thdI, 95),
                pct(g, r -> r.h5, 5),   pct(g, r -> r.h5, 50),   pct(g, r -> r.h5, 95),
                pct(g, r -> r.cv, 5),   pct(g, r -> r.cv, 50),   pct(g, r -> r.cv, 95),
                pct(g, r -> r.pf, 50)));
        }
        md.append("\n");

        md.append("## Lectura de resultados\n\n");
        md.append("- **AUC ≈ 1.0 es esperable y NO es mérito del clasificador**: sobre perfiles de simulador\n");
        md.append("  las clases están separadas por construcción. El valor del estudio está en (a) verificar la\n");
        md.append("  coherencia árbol↔perfiles con el árbol *vigente* (matriz de confusión), (b) situar los\n");
        md.append("  umbrales actuales sobre la curva (¿dejan margen simétrico?) y (c) dejar la tubería ROC\n");
        md.append("  lista para re-ejecutarse cuando existan datos de campo etiquetados.\n");
        md.append("- Los umbrales actuales que muestren TPR < 1 con FPR = 0 están **dentro** del margen entre\n");
        md.append("  clases (conservadores); umbrales con FPR > 0 requieren revisión.\n");
        md.append("- La matriz de confusión con datos históricos expone dos efectos conocidos: el perfil\n");
        md.append("  `normal_load` oscila LINEAR/MIXED por estar clavado en el borde THD=5%, y el perfil\n");
        md.append("  `data_center` histórico (H5≈12% en estos datos, anterior a la tabla actual con H5=28%)\n");
        md.append("  cae en ELECTRONIC_LIGHT porque no supera la guarda H5>15% del nodo [6].\n");

        Path out = Path.of("docs/estudio_roc.md");
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("Estudio escrito en: " + out.toAbsolutePath());
        System.out.printf("Filas: %d (%d pos, %d neg)%n", rows.size(), nPos, nNeg);
    }

    static double pct(List<Row> g, Feature f, int p) {
        double[] v = g.stream().mapToDouble(f::get).sorted().toArray();
        return v[Math.min(v.length - 1, Math.max(0, (int) Math.round(p / 100.0 * (v.length - 1))))];
    }

    private RocStudy() {}
}
