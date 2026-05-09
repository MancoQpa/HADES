package com.harmonicmonitor.gui;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link CompRow} lists for the normative compliance table in
 * {@link CompliancePanel}.
 *
 * All methods are static; this class has no mutable state.
 * Normative references:
 * <ul>
 *   <li>IEC 61000-3-6  — Planning levels for voltage harmonics (MT 1kV–36kV)</li>
 *   <li>IEEE 519-2022  — Current and voltage harmonic limits</li>
 *   <li>EN 50160       — Voltage characteristics in public distribution networks</li>
 *   <li>IEC 61000-2-12 — Compatibility levels for MT networks</li>
 * </ul>
 *
 * Extracted from {@link CompliancePanel} (refactor F13-001).
 */
final class ComplianceRowBuilder {

    private ComplianceRowBuilder() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Builds the 15 normative compliance rows for one feeder measurement.
     *
     * @param m   measurement snapshot (non-null)
     * @param cfg feeder configuration (may be null for simulated feeders)
     */
    static List<CompRow> buildRows(FeederMeasurement m, FeederConfig cfg) {
        String feederLabel = m.getFeederId();

        double thdi   = m.getThdCurrentAvg();
        double thdv   = m.getThdVoltageAvg();
        double pf     = Math.abs(m.getPowerFactor());
        double freq   = m.getFrequency();
        double h5iPct = m.getH5h1Ratio() * 100.0;
        double h7iPct = m.getH7h1Ratio() * 100.0;

        double[] vSpec = m.getHarmonicVoltageL1();
        double v1      = (vSpec != null && vSpec.length > 0) ? vSpec[0] : 0;
        double h5vPct  = (v1 > 1e-6 && vSpec != null && vSpec.length >  5) ? (vSpec[4]  / v1 * 100) : 0;
        double h7vPct  = (v1 > 1e-6 && vSpec != null && vSpec.length >  7) ? (vSpec[6]  / v1 * 100) : 0;
        double h11vPct = (v1 > 1e-6 && vSpec != null && vSpec.length > 11) ? (vSpec[10] / v1 * 100) : 0;
        double h13vPct = (v1 > 1e-6 && vSpec != null && vSpec.length > 13) ? (vSpec[12] / v1 * 100) : 0;

        double voltAvg = m.getVoltageAvg();
        double unbal   = 0;
        if (voltAvg > 1e-6) {
            double maxDev = Math.max(
                Math.max(Math.abs(m.getVoltageL1() - voltAvg), Math.abs(m.getVoltageL2() - voltAvg)),
                Math.abs(m.getVoltageL3() - voltAvg));
            unbal = 100.0 * maxDev / voltAvg;
        }

        List<CompRow> rows = new ArrayList<>();

        // IEC 61000-3-6
        rows.add(ev(feederLabel, "IEC 61000-3-6",  "THDv total (%)",          thdv,   5.0, "%.2f%%", "5.0%",  "Promedio trif\u00E1sico"));
        rows.add(ev(feederLabel, "IEC 61000-3-6",  "H5 Tensi\u00F3n (%)",     h5vPct, 4.0, "%.2f%%", "4.0%",  "5\u00B0 orden tensi\u00F3n L1"));
        rows.add(ev(feederLabel, "IEC 61000-3-6",  "H7 Tensi\u00F3n (%)",     h7vPct, 4.0, "%.2f%%", "4.0%",  "7\u00B0 orden tensi\u00F3n L1"));
        rows.add(ev(feederLabel, "IEC 61000-3-6",  "H11 Tensi\u00F3n (%)",   h11vPct, 3.0, "%.2f%%", "3.0%",  "11\u00B0 orden tensi\u00F3n L1"));
        rows.add(ev(feederLabel, "IEC 61000-3-6",  "H13 Tensi\u00F3n (%)",   h13vPct, 3.0, "%.2f%%", "3.0%",  "13\u00B0 orden tensi\u00F3n L1"));

        // IEEE 519-2022
        rows.add(ev(feederLabel, "IEEE 519-2022", "THDi corriente (%)",        thdi,   8.0, "%.2f%%", "8.0%",  "TDD promedio trif\u00E1sico"));
        rows.add(ev(feederLabel, "IEEE 519-2022", "THDv tensi\u00F3n (%)",     thdv,   5.0, "%.2f%%", "5.0%",  "Distorsi\u00F3n total tensi\u00F3n"));
        rows.add(ev(feederLabel, "IEEE 519-2022", "H5/H1 corriente (%)",     h5iPct,   4.0, "%.2f%%", "4.0%",  "5\u00B0 orden corriente"));
        rows.add(ev(feederLabel, "IEEE 519-2022", "H7/H1 corriente (%)",     h7iPct,   4.0, "%.2f%%", "4.0%",  "7\u00B0 orden corriente"));
        rows.add(evPF(feederLabel, pf));

        // EN 50160
        rows.add(ev(feederLabel, "EN 50160",       "Desbalance tensi\u00F3n (%)", unbal, 2.0, "%.2f%%", "2.0%", "Desequilibrio trif\u00E1sico"));
        rows.add(ev(feederLabel, "EN 50160",       "THDv total (%)",          thdv,   8.0, "%.2f%%", "8.0%",  "L\u00EDmite semanal EN 50160"));
        rows.add(evFreq(feederLabel, freq));

        // IEC 61000-2-12
        rows.add(ev(feederLabel, "IEC 61000-2-12", "Nivel compatib. THDv",    thdv,   6.0, "%.2f%%", "6.0%",  "Nivel planificaci\u00F3n MT"));
        rows.add(ev(feederLabel, "IEC 61000-2-12", "H5 nivel compat. (%)",  h5vPct,   5.0, "%.2f%%", "5.0%",  "5\u00B0 orden tensi\u00F3n MT"));

        return rows;
    }

    /**
     * Builds placeholder rows shown when no feeder data is available yet.
     */
    static List<CompRow> buildDefaultRows() {
        String f = "\u2014";
        List<CompRow> rows = new ArrayList<>();
        rows.add(new CompRow(f, "IEC 61000-3-6",  "THDv total (%)",          "\u2014", "5.0%",   "\u2014 Sin datos", "Valor promedio 3 fases"));
        rows.add(new CompRow(f, "IEC 61000-3-6",  "H5 Tensi\u00F3n (%)",    "\u2014", "4.0%",   "\u2014 Sin datos", "Componente de 5\u00B0 orden"));
        rows.add(new CompRow(f, "IEC 61000-3-6",  "H7 Tensi\u00F3n (%)",    "\u2014", "4.0%",   "\u2014 Sin datos", "Componente de 7\u00B0 orden"));
        rows.add(new CompRow(f, "IEC 61000-3-6",  "H11 Tensi\u00F3n (%)",   "\u2014", "3.0%",   "\u2014 Sin datos", "Componente de 11\u00B0 orden"));
        rows.add(new CompRow(f, "IEC 61000-3-6",  "H13 Tensi\u00F3n (%)",   "\u2014", "3.0%",   "\u2014 Sin datos", "Componente de 13\u00B0 orden"));
        rows.add(new CompRow(f, "IEEE 519-2022",  "THDi corriente (%)",      "\u2014", "8.0%",   "\u2014 Sin datos", "TDD m\u00E1ximo admisible"));
        rows.add(new CompRow(f, "IEEE 519-2022",  "THDv tensi\u00F3n (%)",   "\u2014", "5.0%",   "\u2014 Sin datos", "Distorsi\u00F3n total tensi\u00F3n"));
        rows.add(new CompRow(f, "IEEE 519-2022",  "H5/H1 corriente (%)",     "\u2014", "4.0%",   "\u2014 Sin datos", "5\u00B0 orden arm\u00F3nico"));
        rows.add(new CompRow(f, "IEEE 519-2022",  "H7/H1 corriente (%)",     "\u2014", "4.0%",   "\u2014 Sin datos", "7\u00B0 orden arm\u00F3nico"));
        rows.add(new CompRow(f, "IEEE 519-2022",  "Factor de Potencia",       "\u2014", ">0.85",  "\u2014 Sin datos", "M\u00EDnimo recomendado"));
        rows.add(new CompRow(f, "EN 50160",       "Desbalance tensi\u00F3n (%)","—",  "2.0%",   "\u2014 Sin datos", "Promedio 10 minutos"));
        rows.add(new CompRow(f, "EN 50160",       "THDv total (%)",           "\u2014", "8.0%",   "\u2014 Sin datos", "L\u00EDmite EN 50160 semanal"));
        rows.add(new CompRow(f, "EN 50160",       "Frecuencia (Hz)",          "\u2014", "50\u00B11Hz","\u2014 Sin datos","L\u00EDmite nominal \u00B12%"));
        rows.add(new CompRow(f, "IEC 61000-2-12", "Nivel compatib. THDv",    "\u2014", "6.0%",   "\u2014 Sin datos", "Nivel de planif. MT"));
        rows.add(new CompRow(f, "IEC 61000-2-12", "H5 nivel compat. (%)",    "\u2014", "5.0%",   "\u2014 Sin datos", "5\u00B0 orden, nivel MT"));
        return rows;
    }

    // ── Row factories (package-private for testability) ───────────────────────

    static CompRow ev(String feeder, String std, String param,
                      double measured, double limit,
                      String fmt, String limitStr, String notes) {
        String measStr = String.format(fmt, measured);
        String status;
        if      (measured > limit * 1.2)  status = "\u2717 INCUMPLE";
        else if (measured > limit)        status = "\u26A0 L\u00CDMITE";
        else if (measured > limit * 0.85) status = "\u26A0 L\u00CDMITE";
        else                              status = "\u2713 CUMPLE";
        return new CompRow(feeder, std, param, measStr, limitStr, status, notes);
    }

    static CompRow evPF(String feeder, double pf) {
        String measStr = String.format("%.3f", pf);
        String status;
        if      (pf < 0.85 * 0.9) status = "\u2717 INCUMPLE";
        else if (pf < 0.85)       status = "\u26A0 L\u00CDMITE";
        else                      status = "\u2713 CUMPLE";
        return new CompRow(feeder, "IEEE 519-2022", "Factor de Potencia",
                           measStr, ">0.85", status, "Factor de potencia desplazamiento");
    }

    static CompRow evFreq(String feeder, double freq) {
        String measStr = String.format("%.3f Hz", freq);
        String status;
        if      (freq < 49.0 || freq > 51.0) status = "\u2717 INCUMPLE";
        else if (freq < 49.5 || freq > 50.5) status = "\u26A0 L\u00CDMITE";
        else                                  status = "\u2713 CUMPLE";
        return new CompRow(feeder, "EN 50160", "Frecuencia (Hz)",
                           measStr, "50 \u00B1 1 Hz", status, "Frecuencia nominal 50 Hz");
    }
}
