package com.harmonicmonitor.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Builds the header bar, sidebar, and status bar for {@link ComtradePanel}.
 *
 * Output fields are package-private; ComtradePanel copies them after each call.
 * Callbacks invoke package-private methods on the panel instance.
 *
 * Extracted from ComtradePanel (refactor F22-001).
 */
final class ComtradeHeaderBuilder {

    private final ComtradePanel panel;

    // ── Output fields — copied by ComtradePanel after buildXxx() calls ─────────
    TextField        tfFilePath;
    Label            lblInfo;
    ListView<String> channelList;
    Label            lblStatus;

    ComtradeHeaderBuilder(ComtradePanel panel) {
        this.panel = panel;
    }

    // ── Header bar ─────────────────────────────────────────────────────────────

    HBox buildHeader() {
        HBox h = new HBox(10);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(10, 16, 10, 16));
        h.setStyle("-fx-background-color: " + Theme.BG
            + "; -fx-border-color: " + Theme.BORDER
            + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label(
            "\uD83D\uDCC1 COMTRADE VIEWER  \u2014  ION 7400 / IEC 60255-24");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;"
            + " -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        tfFilePath = new TextField();
        tfFilePath.setPromptText("Seleccione un archivo .cfg ...");
        tfFilePath.setEditable(false);
        tfFilePath.setPrefWidth(360);
        tfFilePath.setStyle("-fx-background-color: " + Theme.CARD
            + "; -fx-text-fill: " + Theme.TEXT
            + "; -fx-border-color: " + Theme.BORDER
            + "; -fx-border-width: 1; -fx-border-radius: 3; -fx-background-radius: 3;");

        Button btnOpen = panel.mkBtn("\uD83D\uDCC2 Abrir .cfg", "#0078D4");
        btnOpen.setOnAction(e -> panel.openCfgFile());

        Button btnAnalyze = panel.mkBtn("\uD83D\uDD0D Analizar", "#4CAF50");
        btnAnalyze.setOnAction(e -> panel.analyzeAll());

        Button btnCapture = panel.mkBtn("\uD83D\uDCF8 Capturar ahora", "#E67E22");
        btnCapture.setTooltip(new Tooltip(
            "Dispara registro COMTRADE con los datos actuales del IED"
            + " y lo abre autom\u00E1ticamente"));
        btnCapture.setOnAction(e -> panel.captureNow());

        Button btnCsv = panel.mkBtn("\uD83D\uDCBE CSV", "#6C757D");
        btnCsv.setOnAction(e -> panel.exportCsv());

        h.getChildren().addAll(title, spacer,
            btnCapture, new Separator(),
            tfFilePath, btnOpen, btnAnalyze, btnCsv);
        return h;
    }

    // ── Sidebar ────────────────────────────────────────────────────────────────

    VBox buildSidebar() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setPrefWidth(215);
        box.setStyle("-fx-background-color: " + Theme.BG + ";");

        Label lblInfoTitle = panel.sectionLbl("INFORMACI\u00D3N DEL REGISTRO");
        lblInfo = new Label("Sin archivo cargado");
        lblInfo.setWrapText(true);
        lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        Label lblCh = panel.sectionLbl("CANALES ANAL\u00D3GICOS");
        Label hint  = new Label("Ctrl+clic para multi-selecci\u00F3n");
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: "
            + Theme.TEXT + "; -fx-wrap-text: true;");

        channelList = new ListView<>();
        channelList.setStyle("-fx-background-color: " + Theme.BG
            + "; -fx-border-color: " + Theme.BORDER + ";");
        channelList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        VBox.setVgrow(channelList, Priority.ALWAYS);
        channelList.getSelectionModel().selectedItemProperty()
            .addListener((obs, o, n) -> { if (n != null) panel.onSelectionChanged(); });

        Button btnAll  = panel.mkSmBtn("Sel. todos");
        btnAll.setMaxWidth(Double.MAX_VALUE);
        btnAll.setOnAction(e -> channelList.getSelectionModel().selectAll());

        Button btnNone = panel.mkSmBtn("Deseleccionar");
        btnNone.setMaxWidth(Double.MAX_VALUE);
        btnNone.setOnAction(e -> channelList.getSelectionModel().clearSelection());

        box.getChildren().addAll(
            lblInfoTitle, lblInfo, new Separator(),
            lblCh, hint, channelList, new HBox(4, btnAll, btnNone));
        return box;
    }

    // ── Status bar ─────────────────────────────────────────────────────────────

    HBox buildStatusBar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(4, 16, 4, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: " + Theme.BG
            + "; -fx-border-color: " + Theme.BORDER
            + "; -fx-border-width: 1 0 0 0;");
        lblStatus = new Label("Listo \u2014 Abra un archivo .cfg para comenzar");
        lblStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        bar.getChildren().add(lblStatus);
        return bar;
    }
}
