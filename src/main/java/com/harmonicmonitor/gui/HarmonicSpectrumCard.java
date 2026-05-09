package com.harmonicmonitor.gui;

import com.harmonicmonitor.model.FeederMeasurement;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Card del espectro armónico H1–H13 con clic interactivo para detalle.
 *
 * <p>Autocontenido: crea su propia UI, gestiona la serie de datos y responde
 * a clics en las barras mostrando información del armónico seleccionado.
 * Se integra en DashboardPanel vía {@link #getNode()}.
 */
class HarmonicSpectrumCard {

    private final VBox  card;
    private final XYChart.Series<String, Number> harmonicSeries;
    private final Label harmonicInfoLabel;
    private FeederMeasurement lastMeasurement;

    HarmonicSpectrumCard() {
        card = new VBox(6);
        card.setStyle(
            "-fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: #D0D3D8; -fx-border-width: 1;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(12));
        card.setMinHeight(220);

        Label title = new Label("ESPECTRO ARMÓNICO  (H1–H13, % de fundamental)");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Orden");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("%");
        yAxis.setForceZeroInRange(true);

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setAnimated(false);
        barChart.setBarGap(2);
        barChart.setCategoryGap(4);
        VBox.setVgrow(barChart, Priority.ALWAYS);

        harmonicSeries = new XYChart.Series<>();
        harmonicSeries.setName("H%");
        for (int i = 1; i <= 13; i++) {
            XYChart.Data<String, Number> d = new XYChart.Data<>("H" + i, 0.0);
            final int order = i;
            d.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-cursor: hand;");
                    newNode.setOnMouseClicked(ev -> showHarmonicDetail(order));
                }
            });
            harmonicSeries.getData().add(d);
        }
        barChart.getData().add(harmonicSeries);

        harmonicInfoLabel = new Label("Haz clic en una barra para ver el detalle del armonico");
        harmonicInfoLabel.setStyle(
            "-fx-font-size: 11px; -fx-text-fill: " + Theme.ACCENT + ";" +
            "-fx-padding: 4 8 4 8; -fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-radius: 4; -fx-background-radius: 4;");
        harmonicInfoLabel.setWrapText(true);

        card.getChildren().addAll(title, barChart, harmonicInfoLabel);
    }

    /** Nodo raíz listo para insertar en un layout. */
    VBox getNode() { return card; }

    /**
     * Actualiza el espectro con la medición más reciente.
     * Almacena la medición para el handler de clic.
     */
    void update(FeederMeasurement m) {
        lastMeasurement = m;
        double[] spec = m.getHarmonicCurrentL1();
        if (spec == null || spec.length < 2 || spec[0] < 1e-6) return;
        double h1 = spec[0];
        ObservableList<XYChart.Data<String, Number>> data = harmonicSeries.getData();
        for (int i = 0; i < Math.min(13, data.size()); i++) {
            double pct = (i == 0) ? 100.0 : (spec[i] / h1 * 100.0);
            data.get(i).setYValue(pct);
        }
    }

    private void showHarmonicDetail(int order) {
        if (lastMeasurement == null) return;
        double[] spec = lastMeasurement.getHarmonicCurrentL1();
        if (spec == null || spec.length <= order || spec[0] < 1e-6) return;

        double h1A      = spec[0];
        double hnA      = spec[order - 1];
        double pct      = (order == 1) ? 100.0 : (hnA / h1A * 100.0);
        double freqHz   = lastMeasurement.getFrequency() > 0 ? lastMeasurement.getFrequency() : 50.0;
        double harmFreq = order * freqHz;
        String estimated = lastMeasurement.isSpectrumEstimated() ? "  [espectro estimado]" : "";

        String txt;
        if (order == 1) {
            txt = String.format("H1 (fundamental %.0f Hz):  %.2f A  |  100%%  (referencia)%s",
                freqHz, h1A, estimated);
        } else {
            txt = String.format("H%d  (%.0f Hz):  %.3f A  |  %.1f%% de fundamental  |  H1=%.2f A%s",
                order, harmFreq, hnA, pct, h1A, estimated);
        }
        harmonicInfoLabel.setText(txt);
    }
}
