package com.harmonicmonitor.gui;

import com.harmonicmonitor.AppExecutors;
import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.storage.MLDataExporter;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlador de caracterización espectral para construcción de dataset ML.
 *
 * <p>Gestiona el ciclo de vida del ScheduledExecutorService, la animación del
 * botón y la acumulación de filas en el CSV de entrenamiento. Genera
 * {@code <feeder>_dataset.csv} en el directorio de cada feeder.
 *
 * <p>Se instancia desde RecordsPanel; su nodo raíz ({@link #getNode()}) se
 * inserta directamente en el layout del panel.
 */
class CharacterizationController {

    private final HarmonicMonitorApp app;
    private final MLDataExporter     mlExporter;

    // UI
    private final HBox   charBanner;
    private final Button btnChar;
    private final Label  charStatusLbl;

    // Estado de la sesión
    private Timeline                 charPulse;
    private ScheduledExecutorService charScheduler;
    private volatile boolean         characterizing  = false;
    private final AtomicInteger      charSampleCount = new AtomicInteger(0);
    private volatile Instant         charStartTime;

    // Estilos del botón
    private static final String STYLE_CHAR_OFF =
        "-fx-background-color: #E65C00;" +
        "-fx-text-fill: white;" +
        "-fx-font-size: 13px; -fx-font-weight: bold;" +
        "-fx-padding: 10 28;" +
        "-fx-background-radius: 8;" +
        "-fx-border-color: #FF8C00; -fx-border-width: 2; -fx-border-radius: 8;" +
        "-fx-cursor: hand;";

    private static final String STYLE_CHAR_ON_A =
        "-fx-background-color: #107C10;" +
        "-fx-text-fill: white;" +
        "-fx-font-size: 13px; -fx-font-weight: bold;" +
        "-fx-padding: 10 28;" +
        "-fx-background-radius: 8;" +
        "-fx-border-color: #FFFFFF; -fx-border-width: 2; -fx-border-radius: 8;" +
        "-fx-cursor: hand;";

    private static final String STYLE_CHAR_ON_B =
        "-fx-background-color: #0A5C0A;" +
        "-fx-text-fill: #CCFFCC;" +
        "-fx-font-size: 13px; -fx-font-weight: bold;" +
        "-fx-padding: 10 28;" +
        "-fx-background-radius: 8;" +
        "-fx-border-color: #50FF50; -fx-border-width: 2; -fx-border-radius: 8;" +
        "-fx-cursor: hand;";

    // ─────────────────────────────────────────────────────────────────────────

    CharacterizationController(HarmonicMonitorApp app, MLDataExporter mlExporter) {
        this.app        = app;
        this.mlExporter = mlExporter;

        btnChar = new Button("🧬  INICIAR CARACTERIZACIÓN ESPECTRAL");
        btnChar.setStyle(STYLE_CHAR_OFF);
        btnChar.setTooltip(new Tooltip(
            "Inicia la captura de datos espectrales cada 1 minuto para\n" +
            "construir un dataset de entrenamiento de modelos ML.\n" +
            "Los datos se guardan en <feeder>_dataset.csv dentro de cada carpeta de feeder."));
        btnChar.setOnAction(e -> toggleCharacterization());

        charStatusLbl = new Label("  Sin captura activa");
        charStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #808080; -fx-font-style: italic;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label("Genera  <feeder>_dataset.csv  →  listo para pandas / sklearn");
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: #606080; -fx-font-style: italic;");

        charBanner = new HBox(16);
        charBanner.setAlignment(Pos.CENTER_LEFT);
        charBanner.setPadding(new Insets(10, 18, 10, 18));
        charBanner.setStyle(
            "-fx-background-color: #1A1A2E;" +
            "-fx-border-color: #E65C00;" +
            "-fx-border-width: 0 0 2 0;");
        charBanner.getChildren().addAll(btnChar, charStatusLbl, spacer, hint);
    }

    /** Nodo raíz — insertar en el layout del panel padre. */
    HBox getNode() { return charBanner; }

    /** Detiene la captura si está activa. Llamar desde {@code HarmonicMonitorApp.shutdown()}. */
    void shutdown() { stopCharacterization(); }

    // ── Lógica de caracterización ─────────────────────────────────────────────

    private void toggleCharacterization() {
        if (characterizing) stopCharacterization();
        else                startCharacterization();
    }

    private void startCharacterization() {
        if (characterizing) return;

        List<FeederConfig> feeders = app.getFeederConfigs();
        if (feeders.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Sin feeders");
            a.setHeaderText(null);
            a.setContentText("Agregue al menos un feeder antes de iniciar la caracterización.");
            a.showAndWait();
            return;
        }

        characterizing  = true;
        charSampleCount.set(0);
        charStartTime   = Instant.now();

        charScheduler = AppExecutors.newDaemonScheduler("CharSpectral-5min");
        charScheduler.scheduleWithFixedDelay(this::appendCharRow, 0, 60, TimeUnit.SECONDS);

        charPulse = new Timeline(
            new KeyFrame(Duration.millis(600),  e -> btnChar.setStyle(STYLE_CHAR_ON_A)),
            new KeyFrame(Duration.millis(1200), e -> btnChar.setStyle(STYLE_CHAR_ON_B))
        );
        charPulse.setCycleCount(Animation.INDEFINITE);
        charPulse.play();

        btnChar.setText("⏹  FINALIZAR CARACTERIZACIÓN ESPECTRAL");
        charBanner.setStyle(
            "-fx-background-color: #0A2A0A;" +
            "-fx-border-color: #50FF50;" +
            "-fx-border-width: 0 0 2 0;");

        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1),
            e -> Platform.runLater(this::updateCharStatus)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
        btnChar.setUserData(clock);

        updateCharStatus();
        app.setStatusMessage("🧬 Caracterización espectral iniciada — capturando cada 1 min");
    }

    private void stopCharacterization() {
        if (!characterizing) return;
        characterizing = false;

        if (charScheduler != null) { charScheduler.shutdownNow(); charScheduler = null; }
        if (charPulse     != null) { charPulse.stop();            charPulse     = null; }

        if (btnChar.getUserData() instanceof Timeline) {
            ((Timeline) btnChar.getUserData()).stop();
            btnChar.setUserData(null);
        }

        btnChar.setText("🧬  INICIAR CARACTERIZACIÓN ESPECTRAL");
        btnChar.setStyle(STYLE_CHAR_OFF);
        charBanner.setStyle(
            "-fx-background-color: #1A1A2E;" +
            "-fx-border-color: #E65C00;" +
            "-fx-border-width: 0 0 2 0;");

        int total = charSampleCount.get();
        charStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #A0A0A0; -fx-font-style: italic;");
        charStatusLbl.setText(String.format(
            "  Captura finalizada  —  %d muestra%s guardada%s",
            total, total != 1 ? "s" : "", total != 1 ? "s" : ""));
        app.setStatusMessage(String.format(
            "🧬 Caracterización finalizada — %d muestras en <feeder>_dataset.csv", total));
    }

    /** Ejecutado en el hilo del scheduler cada 60 segundos. */
    private void appendCharRow() {
        List<FeederConfig> cfgs = new ArrayList<>(app.getFeederConfigs());
        int added = 0;
        for (FeederConfig cfg : cfgs) {
            FeederMeasurement m = app.getLatestMeasurements().get(cfg.getFeederId());
            if (m != null) {
                try {
                    mlExporter.appendRow(m, app.getComtradeTrigger().getFeederDir(cfg.getFeederId()));
                    added++;
                } catch (Exception ex) {
                    // log silencioso — no interrumpir el ciclo de captura
                }
            }
        }
        charSampleCount.addAndGet(added);
        Platform.runLater(this::updateCharStatus);
    }

    private void updateCharStatus() {
        if (!characterizing || charStartTime == null) return;
        long secs  = ChronoUnit.SECONDS.between(charStartTime, Instant.now());
        long hh    = secs / 3600;
        long mm    = (secs % 3600) / 60;
        long ss    = secs % 60;
        int  count = charSampleCount.get();
        charStatusLbl.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #50FF50;");
        charStatusLbl.setText(String.format(
            "  ● CAPTURANDO  |  %d muestra%s  |  %02d:%02d:%02d  |  próx. muestra en ~1 min",
            count, count != 1 ? "s" : "", hh, mm, ss));
    }
}
