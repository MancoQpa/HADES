package com.harmonicmonitor.gui;

import com.harmonicmonitor.model.FeederMeasurement;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;

/**
 * Card de métricas de alimentador: potencias, FP, frecuencia, THDv, CV,
 * ratios armónicos H5/H7, resonancia y valores por fase L1/L2/L3.
 */
class MetricsCard {

    private final VBox  card;
    private final Label metricQ;
    private final Label metricS;
    private final Label metricFP;
    private final Label metricFreq;
    private final Label metricTHDv;
    private final Label metricCV;
    private final Label metricH5;
    private final Label metricH7;
    private final Label metricRes;
    private final Label metricL1;
    private final Label metricL2;
    private final Label metricL3;

    MetricsCard() {
        metricQ    = metricLabel(); metricS    = metricLabel();
        metricFP   = metricLabel(); metricFreq = metricLabel();
        metricTHDv = metricLabel(); metricCV   = metricLabel();
        metricH5   = metricLabel(); metricH7   = metricLabel();
        metricRes  = metricLabel();
        metricL1   = metricLabel(); metricL2   = metricLabel(); metricL3 = metricLabel();

        Label title = new Label("MÉTRICAS DE ALIMENTADOR");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(5);
        addMetricRow(grid, 0, "Q Reactiva:",   metricQ,    "kVAR", "P Aparente:",   metricS,    "kVA");
        addMetricRow(grid, 1, "Factor Pot.:",  metricFP,   "",     "Frecuencia:",   metricFreq, "Hz");
        addMetricRow(grid, 2, "THDv:",         metricTHDv, "%",    "CV Corriente:", metricCV,   "%");
        addMetricRow(grid, 3, "H5/H1:",        metricH5,   "%",    "H7/H1:",        metricH7,   "%");

        HBox resRow = new HBox(8);
        resRow.setAlignment(Pos.CENTER_LEFT);
        Label resLbl = new Label("Resonancia:");
        resLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        resRow.getChildren().addAll(resLbl, metricRes);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #CCCCCC;");

        Label phaseTit = new Label("POR FASE");
        phaseTit.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        GridPane phaseGrid = new GridPane();
        phaseGrid.setHgap(16);
        phaseGrid.setVgap(3);
        addPhaseHeaderRow(phaseGrid);
        metricL1.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        metricL2.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        metricL3.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        phaseGrid.add(new Label("L1:"), 0, 1); phaseGrid.add(metricL1, 1, 1); GridPane.setColumnSpan(metricL1, 4);
        phaseGrid.add(new Label("L2:"), 0, 2); phaseGrid.add(metricL2, 1, 2); GridPane.setColumnSpan(metricL2, 4);
        phaseGrid.add(new Label("L3:"), 0, 3); phaseGrid.add(metricL3, 1, 3); GridPane.setColumnSpan(metricL3, 4);
        for (int r = 1; r <= 3; r++) {
            Label phl = (Label) phaseGrid.getChildren().get((r - 1) * 2);
            phl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        }

        card = new VBox(8);
        card.setStyle(
            "-fx-background-color: " + Theme.CARD + "; -fx-border-color: #D0D3D8; -fx-border-width: 1;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(12));
        card.getChildren().addAll(title, grid, resRow, sep, phaseTit, phaseGrid);
    }

    VBox getNode() { return card; }

    void update(FeederMeasurement m) {
        metricQ.setText(String.format("%.0f",  m.getReactivePower()));
        metricS.setText(String.format("%.1f",  m.getApparentPower()));
        metricFP.setText(String.format("%.3f", m.getPowerFactor()));
        metricFreq.setText(String.format("%.2f", m.getFrequency()));
        metricTHDv.setText(String.format("%.2f", m.getThdVoltageAvg()));
        metricCV.setText(String.format("%.2f",   m.getCvCurrent() * 100));
        metricH5.setText(String.format("%.1f",   m.getH5h1Ratio() * 100));
        metricH7.setText(String.format("%.1f",   m.getH7h1Ratio() * 100));

        if (m.getResonanceOrder() > 0) {
            metricRes.setText(String.format("%.0f Hz (H%d)", m.getResonanceFrequency(), m.getResonanceOrder()));
            metricRes.setStyle("-fx-text-fill: #C42B1C; -fx-font-size: 12px; -fx-font-weight: bold;");
        } else {
            metricRes.setText("Sin resonancia");
            metricRes.setStyle("-fx-text-fill: #107C10; -fx-font-size: 12px; -fx-font-weight: bold;");
        }

        metricL1.setText(String.format("V=%.1f kV  I=%.1f A  THDi=%.1f%%  THDv=%.1f%%",
            m.getVoltageL1() / 1000.0, m.getCurrentL1(), m.getThdCurrentL1(), m.getThdVoltageL1()));
        metricL2.setText(String.format("V=%.1f kV  I=%.1f A  THDi=%.1f%%  THDv=%.1f%%",
            m.getVoltageL2() / 1000.0, m.getCurrentL2(), m.getThdCurrentL2(), m.getThdVoltageL2()));
        metricL3.setText(String.format("V=%.1f kV  I=%.1f A  THDi=%.1f%%  THDv=%.1f%%",
            m.getVoltageL3() / 1000.0, m.getCurrentL3(), m.getThdCurrentL3(), m.getThdVoltageL3()));
    }

    // ── Helpers de construcción ───────────────────────────────────────────────

    private Label metricLabel() {
        Label l = new Label("—");
        l.setStyle("-fx-text-fill: #0099BC; -fx-font-size: 12px; -fx-font-weight: bold;");
        return l;
    }

    private void addMetricRow(GridPane g, int row,
                               String lbl1, Label val1, String unit1,
                               String lbl2, Label val2, String unit2) {
        g.add(buildMetricCell(lbl1, val1, unit1), 0, row);
        g.add(buildMetricCell(lbl2, val2, unit2), 1, row);
    }

    private HBox buildMetricCell(String lbl, Label val, String unit) {
        Label l = new Label(lbl);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + "; -fx-min-width: 95px;");
        Label u = new Label(unit);
        u.setStyle("-fx-font-size: 11px; -fx-text-fill: #0078D4;");
        HBox cell = new HBox(4, l, val, u);
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    private void addPhaseHeaderRow(GridPane g) {
        String[] hdrs = {"Fase", "Valores"};
        for (int i = 0; i < hdrs.length; i++) {
            Label l = new Label(hdrs[i]);
            l.setStyle("-fx-font-size: 10px; -fx-text-fill: #0078D4;");
            g.add(l, i, 0);
        }
    }
}
