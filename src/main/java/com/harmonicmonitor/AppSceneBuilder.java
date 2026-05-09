package com.harmonicmonitor;

import com.harmonicmonitor.gui.Theme;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

/**
 * Constructs the main scene widgets (toolbar, tab pane, status bar) for
 * {@link HarmonicMonitorApp}.
 *
 * Output fields are package-private; HarmonicMonitorApp copies them after each call to
 * {@link #buildToolbar()}, {@link #buildTabPane()}, {@link #buildStatusBar()}.
 *
 * Extracted from HarmonicMonitorApp (refactor F20-001).
 */
final class AppSceneBuilder {

    private final HarmonicMonitorApp app;

    // ── Output fields — copied by HarmonicMonitorApp after each build ──────────
    Label   feederCountLbl;
    Label   statusMsgLbl;
    Label   statusStatsLbl;
    Label   pollingIndicatorLbl;
    TabPane tabPane;

    AppSceneBuilder(HarmonicMonitorApp app) {
        this.app = app;
    }

    // ── Toolbar ────────────────────────────────────────────────────────────────

    HBox buildToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.getStyleClass().add("toolbar-main");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 16, 8, 16));

        VBox titleBox = new VBox(1);
        Label appTitle = new Label("\u26A1 HADES v1.0");
        appTitle.getStyleClass().add("toolbar-title");
        Label appSub = new Label(
            "Harmonic Analysis for Detection of Electronic Signatures"
            + "  \u00B7  MT 23kV  \u00B7  IEC 61850");
        appSub.getStyleClass().add("toolbar-subtitle");
        titleBox.getChildren().addAll(appTitle, appSub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        feederCountLbl = new Label("\u25CF 0 feeders");
        feederCountLbl.getStyleClass().addAll("lbl-accent");
        feederCountLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        Label pollLbl = new Label("Polling:");
        pollLbl.getStyleClass().add("lbl-secondary");
        Spinner<Integer> pollSpinner = new Spinner<>(1, 60, 2);
        pollSpinner.setPrefWidth(65);
        Label secLbl = new Label("s");
        secLbl.getStyleClass().add("lbl-muted");
        Button btnApply = new Button("OK");
        btnApply.getStyleClass().add("btn-sm");
        btnApply.setOnAction(e -> app.setPollingInterval(pollSpinner.getValue() * 1000));

        Button btnTheme = new Button(HarmonicMonitorApp.isDark ? "\u2600 Claro" : "\uD83C\uDF19 Oscuro");
        btnTheme.getStyleClass().add("btn-sm");
        btnTheme.setOnAction(e -> {
            HarmonicMonitorApp.isDark = !HarmonicMonitorApp.isDark;
            app.buildScene();
        });

        toolbar.getChildren().addAll(
            titleBox, spacer,
            feederCountLbl,
            new Separator(Orientation.VERTICAL),
            pollLbl, pollSpinner, secLbl, btnApply,
            new Separator(Orientation.VERTICAL),
            btnTheme
        );
        return toolbar;
    }

    // ── Tab pane ───────────────────────────────────────────────────────────────

    TabPane buildTabPane() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: " + Theme.BG + ";");

        tabPane.getTabs().addAll(
            tab("\uD83D\uDCCA RESUMEN",        app.dashboardPanel.getNode()),
            tab("\u301C ARM\u00D3NICOS",        app.harmonicsPanel.getNode()),
            tab("\uD83D\uDCCB MULTI",           app.multiFeederPanel.getNode()),
            tab("\uD83D\uDCC8 TENDENCIAS",      app.trendsPanel.getNode()),
            tab("\uD83D\uDD14 ALARMAS",         app.alarmsPanel.getNode()),
            tab("\uD83D\uDD0C FEEDERS",         app.feederMgmtPanel.getNode()),
            tab("\uD83D\uDCCF NORMAS",          app.compliancePanel.getNode()),
            tab("\uD83D\uDCC1 COMTRADE",        app.comtradePanel.getNode()),
            tab("\uD83D\uDCFC REGISTROS",       app.recordsPanel.getNode()),
            tab("\uD83D\uDCD6 AYUDA",           app.helpPanel.getNode()),
            tab("\uD83C\uDFC6 \u00BFPOR QU\u00C9?", app.comparativaPanel.getNode()),
            tab("\u2139 ACERCA DE",             app.aboutPanel.getNode())
        );

        tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) app.currentTabIndex = newV.intValue();
        });

        return tabPane;
    }

    // ── Status bar ─────────────────────────────────────────────────────────────

    HBox buildStatusBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 16, 4, 16));

        Label statusIcon = new Label("\u25CF");
        statusIcon.getStyleClass().add("lbl-success");

        statusMsgLbl = new Label("Listo  \u2014  HADES iniciado");
        statusMsgLbl.getStyleClass().add("status-msg");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusStatsLbl = new Label("");
        statusStatsLbl.getStyleClass().add("status-stats");

        pollingIndicatorLbl = new Label("\u25CF SIN FEEDERS");
        pollingIndicatorLbl.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #78909C;");
        Tooltip.install(pollingIndicatorLbl, new Tooltip("Estado de polling IEC 61850"));

        Label sep2 = new Label("|");
        sep2.getStyleClass().add("lbl-muted");
        Label sep  = new Label("|");
        sep.getStyleClass().add("lbl-muted");

        Label timeLbl = new Label("");
        timeLbl.getStyleClass().add("status-time");

        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), ev ->
            timeLbl.setText(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")))));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        bar.getChildren().addAll(
            statusIcon, statusMsgLbl, spacer,
            statusStatsLbl, sep2, pollingIndicatorLbl, sep, timeLbl);
        return bar;
    }

    // ── Private ────────────────────────────────────────────────────────────────

    private Tab tab(String title, Node content) {
        return new Tab(title, content);
    }
}
