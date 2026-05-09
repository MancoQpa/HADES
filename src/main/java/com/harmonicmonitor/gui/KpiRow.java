package com.harmonicmonitor.gui;

import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Fila de tiles KPI: Tensión, Corriente, Potencia, THDi y Tipo de Carga.
 * Cada tile muestra el valor actual y el delta respecto a la medición anterior.
 */
class KpiRow {

    private final HBox row;

    // Valor actual
    private final Label kpiVoltageVal;
    private final Label kpiCurrentVal;
    private final Label kpiPowerVal;
    private final Label kpiThdiVal;
    private final Label kpiLoadVal;

    // Indicador de tendencia (▲/▼ %)
    private final Label kpiVoltageDelta;
    private final Label kpiCurrentDelta;
    private final Label kpiPowerDelta;
    private final Label kpiThdiDelta;
    private final Label kpiLoadConf;

    // Valores previos para calcular delta
    private double prevVoltage = Double.NaN;
    private double prevCurrent = Double.NaN;
    private double prevPower   = Double.NaN;
    private double prevThdi    = Double.NaN;

    KpiRow() {
        row = new HBox(10);

        VBox voltTile = buildKpiTile("TENSIÓN",    "#0078D4");
        kpiVoltageVal   = findKpiValue(voltTile);
        kpiVoltageDelta = findKpiDelta(voltTile);

        VBox currTile = buildKpiTile("CORRIENTE",  "#0099BC");
        kpiCurrentVal   = findKpiValue(currTile);
        kpiCurrentDelta = findKpiDelta(currTile);

        VBox powTile  = buildKpiTile("POTENCIA",   "#107C10");
        kpiPowerVal   = findKpiValue(powTile);
        kpiPowerDelta = findKpiDelta(powTile);

        VBox thdiTile = buildKpiTile("THDi",       "#CA5010");
        kpiThdiVal   = findKpiValue(thdiTile);
        kpiThdiDelta = findKpiDelta(thdiTile);

        VBox loadTile = buildKpiTile("TIPO CARGA", "#808080");
        kpiLoadVal  = findKpiValue(loadTile);
        kpiLoadConf = findKpiDelta(loadTile);

        for (VBox tile : new VBox[]{voltTile, currTile, powTile, thdiTile, loadTile}) {
            HBox.setHgrow(tile, Priority.ALWAYS);
            row.getChildren().add(tile);
        }
    }

    HBox getNode() { return row; }

    void update(FeederMeasurement m) {
        double voltKv = m.getVoltageAvg() / 1000.0;
        double curr   = m.getCurrentAvg();
        double pow    = m.getActivePower();
        double thdi   = m.getThdCurrentAvg();

        kpiVoltageVal.setText(String.format("%.2f kV", voltKv));
        setDelta(kpiVoltageDelta, voltKv, prevVoltage);
        prevVoltage = voltKv;

        kpiCurrentVal.setText(String.format("%.1f A", curr));
        setDelta(kpiCurrentDelta, curr, prevCurrent);
        prevCurrent = curr;

        if (pow >= 1000) kpiPowerVal.setText(String.format("%.2f MW", pow / 1000.0));
        else             kpiPowerVal.setText(String.format("%.0f kW", pow));
        setDelta(kpiPowerDelta, pow, prevPower);
        prevPower = pow;

        kpiThdiVal.setText(String.format("%.1f %%", thdi));
        if (thdi > 12)
            kpiThdiVal.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #C42B1C;");
        else if (thdi > 8)
            kpiThdiVal.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #CA5010;");
        else
            kpiThdiVal.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #107C10;");
        setDelta(kpiThdiDelta, thdi, prevThdi);
        prevThdi = thdi;

        LoadType lt = m.getDetectedLoadType();
        kpiLoadVal.setText(lt.getDisplayName());
        kpiLoadVal.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + lt.getColorHex() + ";");

        double conf = computeConfidence(m);
        String confTxt = String.format("%.0f%% confianza", conf);
        if (m.isSpectrumEstimated()) confTxt += "  ~ espectro estimado";
        kpiLoadConf.setText(confTxt);
        kpiLoadConf.setStyle("-fx-font-size: 11px; -fx-text-fill: " + lt.getColorHex() + ";");
    }

    // ── Helpers de construcción ───────────────────────────────────────────────

    private VBox buildKpiTile(String title, String borderColor) {
        VBox tile = new VBox(4);
        tile.setPadding(new Insets(12, 14, 12, 16));
        tile.setMinHeight(88);
        tile.setStyle(
            "-fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: " + borderColor + " #E0E3E8 #E0E3E8 " + borderColor + ";" +
            "-fx-border-width: 0 1 1 4;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 1);");

        Label hdr = new Label(title);
        hdr.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Label value = new Label("—");
        value.setId("kpiValue");
        value.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Label delta = new Label("");
        delta.setId("kpiDelta");
        delta.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        tile.getChildren().addAll(hdr, value, delta);
        return tile;
    }

    private Label findKpiValue(VBox tile) {
        for (Node n : tile.getChildren()) {
            if (n instanceof Label && "kpiValue".equals(n.getId())) return (Label) n;
        }
        return new Label();
    }

    private Label findKpiDelta(VBox tile) {
        for (Node n : tile.getChildren()) {
            if (n instanceof Label && "kpiDelta".equals(n.getId())) return (Label) n;
        }
        return new Label();
    }

    private void setDelta(Label lbl, double current, double prev) {
        if (Double.isNaN(prev)) { lbl.setText(""); return; }
        if (Math.abs(prev) < 1e-9) { lbl.setText(""); return; }
        double pct = 100.0 * (current - prev) / Math.abs(prev);
        if (pct >= 0) {
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #107C10;");
            lbl.setText(String.format("▲ +%.1f%%", pct));
        } else {
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #C42B1C;");
            lbl.setText(String.format("▼ %.1f%%", pct));
        }
    }

    private double computeConfidence(FeederMeasurement m) {
        LoadType lt = m.getDetectedLoadType();
        if (lt == LoadType.UNKNOWN) return 0;
        if (lt == LoadType.CRYPTO_MINING || lt == LoadType.DATA_CENTER)
            // H5/H1 dominante + THDi elevado = espectro SMPS claro
            return Math.min(98, 55 + m.getH5h1Ratio() * 200 + m.getThdCurrentAvg() * 0.4);
        if (lt == LoadType.INDUSTRIAL)
            // Firma 6-pulsos: H5+H7+H11+H13 presentes; mayor certeza si H11/H13 también son altos
            return Math.min(95, 50 + m.getH5h1Ratio() * 150 + m.getH7h1Ratio() * 100
                               + m.getH11h1Ratio() * 80 + m.getH13h1Ratio() * 60);
        return Math.min(92, 45 + m.getThdCurrentAvg() * 1.5);
    }
}
