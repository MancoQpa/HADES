package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.NetworkTopology;
import com.harmonicmonitor.model.SimProfile;
import com.harmonicmonitor.gui.DiscoveryPanel;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * FeederMgmtPanel — Feeder configuration and connection management.
 */
public class FeederMgmtPanel {

    private final HarmonicMonitorApp app;
    private final BorderPane root;

    // Left list
    private ListView<String> feederList;

    // Form fields — assigned from FeederFormBuilder in buildUI()
    private TextField tfFeederId, tfFeederName, tfDescription;
    private TextField tfHost, tfPort, tfIedName, tfMmxuRef, tfMmxuPrefix, tfMhaiRef, tfLdInst, tfPollMs;
    private TextField tfVnom, tfInom, tfScc, tfCap;
    private TextField tfPowerScale, tfPfScale, tfAnalogScale;
    private TextField tfMaxTHDv, tfMaxTHDi, tfCvMax, tfCryptoTHDi;
    private ComboBox<NetworkTopology> topologyCombo;
    private ComboBox<SimProfile>      cbSimProfile;

    public FeederMgmtPanel(HarmonicMonitorApp app) {
        this.app = app;
        root = buildUI();
    }

    public Node getNode() { return root; }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setTop(buildHeader());

        FeederFormBuilder fb = new FeederFormBuilder(this);
        ScrollPane formArea = fb.build();
        tfFeederId = fb.tfFeederId;       tfFeederName  = fb.tfFeederName;  tfDescription = fb.tfDescription;
        tfHost     = fb.tfHost;           tfPort        = fb.tfPort;        tfIedName     = fb.tfIedName;
        tfMmxuRef  = fb.tfMmxuRef;        tfMmxuPrefix  = fb.tfMmxuPrefix;  tfMhaiRef     = fb.tfMhaiRef;
        tfLdInst   = fb.tfLdInst;         tfPollMs      = fb.tfPollMs;
        tfVnom     = fb.tfVnom;            tfInom        = fb.tfInom;        tfScc         = fb.tfScc;
        tfCap      = fb.tfCap;            tfPowerScale  = fb.tfPowerScale;  tfPfScale     = fb.tfPfScale;
        tfAnalogScale = fb.tfAnalogScale;  tfMaxTHDv     = fb.tfMaxTHDv;    tfMaxTHDi     = fb.tfMaxTHDi;
        tfCvMax    = fb.tfCvMax;           tfCryptoTHDi  = fb.tfCryptoTHDi;
        topologyCombo = fb.topologyCombo;  cbSimProfile  = fb.cbSimProfile;

        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color: " + Theme.BG + ";");
        split.setDividerPositions(0.28);
        split.getItems().addAll(buildFeederList(), formArea);
        pane.setCenter(split);
        return pane;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(12, 16, 12, 16));
        h.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("🔌 GESTIÓN DE FEEDERS  —  Configuración IEC 61850");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        h.getChildren().add(title);
        return h;
    }

    // ── Left: feeder list ─────────────────────────────────────────────────────

    private VBox buildFeederList() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: " + Theme.CARD + ";");

        Label lbl = new Label("FEEDERS CONFIGURADOS");
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        feederList = new ListView<>();
        feederList.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1;");
        VBox.setVgrow(feederList, Priority.ALWAYS);
        feederList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) loadFeederIntoForm(findConfigByDisplay(n));
        });
        refreshList();

        Button btnNew = new Button("➕  Nuevo Feeder");
        btnNew.setMaxWidth(Double.MAX_VALUE);
        btnNew.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 6 12 6 12; -fx-cursor: hand;");
        btnNew.setOnAction(e -> clearForm());

        Button btnDel = new Button("🗑  Eliminar Feeder");
        btnDel.setMaxWidth(Double.MAX_VALUE);
        btnDel.setStyle("-fx-background-color: #C42B1C20; -fx-text-fill: #C42B1C;" +
            "-fx-border-color: #C42B1C; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 6 12 6 12; -fx-cursor: hand;");
        btnDel.setOnAction(e -> deleteSelected());

        Button btnReconnect = new Button("↻  Reconectar");
        btnReconnect.setMaxWidth(Double.MAX_VALUE);
        btnReconnect.setStyle("-fx-background-color: #1565C020; -fx-text-fill: #4FC3F7;" +
            "-fx-border-color: #4FC3F7; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 6 12 6 12; -fx-cursor: hand;");
        btnReconnect.setOnAction(e -> reconnectSelected());

        Button btnCsv = new Button("💾  Exportar CSV");
        btnCsv.setMaxWidth(Double.MAX_VALUE);
        btnCsv.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 6 12 6 12; -fx-cursor: hand;");
        btnCsv.setOnAction(e -> exportCsv());

        box.getChildren().addAll(lbl, feederList, btnNew, btnReconnect, btnDel, btnCsv);
        return box;
    }

    private void refreshList() {
        feederList.getItems().clear();
        for (FeederConfig cfg : app.getFeederConfigs()) {
            feederList.getItems().add(cfg.getFeederName() + "\n[" + cfg.getFeederId() + "]  " + cfg.getIedHost());
        }
    }

    private FeederConfig findConfigByDisplay(String display) {
        for (FeederConfig cfg : app.getFeederConfigs()) {
            // Use bracketed ID to avoid substring false-matches (e.g. "AL-1" inside "AL-10")
            if (display.contains("[" + cfg.getFeederId() + "]")) return cfg;
        }
        return null;
    }

    private void deleteSelected() {
        String sel = feederList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        FeederConfig cfg = findConfigByDisplay(sel);
        if (cfg == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "¿Eliminar feeder '" + cfg.getFeederName() + "'?",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirmar eliminación");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                app.removeFeeder(cfg.getFeederId());
                refreshList();
                clearForm();
            }
        });
    }

    private void reconnectSelected() {
        String sel = feederList.getSelectionModel().getSelectedItem();
        if (sel == null) { app.setStatusMessage("Seleccione un feeder para reconectar"); return; }
        FeederConfig cfg = findConfigByDisplay(sel);
        if (cfg == null) return;
        app.setStatusMessage("Reconectando " + cfg.getFeederId() + "...");
        app.reconnectFeeder(cfg.getFeederId());
    }

    private void exportCsv() {
        String sel = feederList.getSelectionModel().getSelectedItem();
        if (sel == null) { app.setStatusMessage("Seleccione un feeder"); return; }
        FeederConfig cfg = findConfigByDisplay(sel);
        if (cfg == null) return;
        try {
            String path = app.getDataStorage().exportToCsv(cfg.getFeederId(), null, null);
            app.setStatusMessage("CSV exportado: " + path);
        } catch (Exception ex) {
            app.setStatusMessage("Error exportando CSV: " + ex.getMessage());
        }
    }

    // ── Form operations ───────────────────────────────────────────────────────

    void clearForm() {
        tfFeederId.setText("AL-" + String.format("%02d", app.getFeederConfigs().size() + 1));
        tfFeederName.setText("Alimentador MT " + (app.getFeederConfigs().size() + 1));
        tfDescription.clear();
        tfHost.setText("192.168.1.100");
        tfPort.setText("102");
        tfIedName.setText("IED1");
        tfMmxuPrefix.setText("");
        tfMmxuRef.setText("MMXU1");
        tfMhaiRef.setText("MHAI1");
        tfLdInst.setText("LD0");
        tfPollMs.setText("5000");
        tfVnom.setText("23.0");
        tfInom.setText("200.0");
        tfScc.setText("100.0");
        tfCap.setText("5.0");
        tfMaxTHDv.setText("5.0");
        tfMaxTHDi.setText("8.0");
        tfCvMax.setText("5.0");
        tfCryptoTHDi.setText("15.0");
        tfPowerScale.setText("0.001");
        tfPfScale.setText("1.0");
        tfAnalogScale.setText("1.0");
        topologyCombo.setValue(NetworkTopology.SINGLE_FEEDER);
        cbSimProfile.setValue(SimProfile.CRYPTO_MINER);
        feederList.getSelectionModel().clearSelection();
    }

    private void loadFeederIntoForm(FeederConfig cfg) {
        if (cfg == null) return;
        tfFeederId.setText(cfg.getFeederId());
        tfFeederName.setText(cfg.getFeederName());
        tfDescription.setText(cfg.getDescription());
        tfHost.setText(cfg.getIedHost());
        tfPort.setText(String.valueOf(cfg.getIedPort()));
        tfIedName.setText(cfg.getIedName());
        tfMmxuPrefix.setText(cfg.getMmxuPrefix());
        tfMmxuRef.setText(cfg.getMmxuLnRef());
        tfMhaiRef.setText(cfg.getMhaiLnRef());
        tfLdInst.setText(cfg.getLdInst());
        tfPollMs.setText(String.valueOf(cfg.getPollIntervalMs()));
        tfVnom.setText(String.valueOf(cfg.getNominalVoltageKv()));
        tfInom.setText(String.valueOf(cfg.getNominalCurrentA()));
        tfScc.setText(String.valueOf(cfg.getShortCircuitMva()));
        tfCap.setText(String.valueOf(cfg.getFeederCapacitanceMicroF()));
        tfMaxTHDv.setText(String.valueOf(cfg.getMaxThdVoltagePct()));
        tfMaxTHDi.setText(String.valueOf(cfg.getMaxThdCurrentPct()));
        tfCvMax.setText(String.valueOf(cfg.getMaxCvElectronicThreshold() * 100));
        tfCryptoTHDi.setText(String.valueOf(cfg.getMinThdICryptoThreshold()));
        tfPowerScale.setText(String.valueOf(cfg.getPowerScaleFactor()));
        tfPfScale.setText(String.valueOf(cfg.getPfScaleFactor()));
        tfAnalogScale.setText(String.valueOf(cfg.getAnalogScaleFactor()));
        topologyCombo.setValue(cfg.getTopology());
        if (cfg.getSimProfile() != null) cbSimProfile.setValue(cfg.getSimProfile());
    }

    private FeederConfig buildConfigFromForm() {
        String id = tfFeederId.getText().trim();
        if (id.isEmpty()) { showError("El ID del feeder no puede estar vacío"); return null; }

        FeederConfig cfg = new FeederConfig(id, tfHost.getText().trim());
        cfg.setFeederName(tfFeederName.getText().trim());
        cfg.setDescription(tfDescription.getText().trim());
        cfg.setIedName(tfIedName.getText().trim());
        cfg.setMmxuPrefix(tfMmxuPrefix.getText().trim());
        cfg.setMmxuLnRef(tfMmxuRef.getText().trim());
        cfg.setMhaiLnRef(tfMhaiRef.getText().trim());
        cfg.setLdInst(tfLdInst.getText().trim());
        cfg.setTopology(topologyCombo.getValue());

        cfg.setIedPort(parseInt(tfPort, 102));
        cfg.setPollIntervalMs((int) parseDouble(tfPollMs, 5000));
        cfg.setNominalVoltageKv(parseDouble(tfVnom, 23.0));
        cfg.setNominalCurrentA(parseDouble(tfInom, 200.0));
        cfg.setShortCircuitMva(parseDouble(tfScc, 100.0));
        cfg.setFeederCapacitanceMicroF(parseDouble(tfCap, 5.0));
        cfg.setMaxThdVoltagePct(parseDouble(tfMaxTHDv, 5.0));
        cfg.setMaxThdCurrentPct(parseDouble(tfMaxTHDi, 8.0));
        cfg.setMaxCvElectronicThreshold(parseDouble(tfCvMax, 5.0) / 100.0);
        cfg.setMinThdICryptoThreshold(parseDouble(tfCryptoTHDi, 15.0));
        cfg.setPowerScaleFactor(parseDouble(tfPowerScale, 0.001));
        cfg.setPfScaleFactor(parseDouble(tfPfScale, 1.0));
        cfg.setAnalogScaleFactor(parseDouble(tfAnalogScale, 1.0));

        return cfg;
    }

    void connectAndAdd() {
        FeederConfig cfg = buildConfigFromForm();
        if (cfg == null) return;
        app.addFeeder(cfg);
        refreshList();
        app.setStatusMessage("Conectando: " + cfg.getFeederName() + " → " + cfg.getIedHost() + ":" + cfg.getIedPort());
    }

    void addSimulated() {
        FeederConfig cfg = buildConfigFromForm();
        if (cfg == null) return;
        cfg.setSimProfile(cbSimProfile.getValue());
        app.addSimulatedFeeder(cfg);
        refreshList();
    }

    void openDiscoveryDialog() {
        // Build a temporary config from the form so the dialog uses current host/port/iedName
        String host = tfHost.getText().trim();
        if (host.isEmpty()) { showError("Ingrese el Host / IP del IED antes de descubrir."); return; }

        FeederConfig tempCfg = new FeederConfig();
        tempCfg.setFeederId(tfFeederId.getText().trim().isEmpty() ? "DISC" : tfFeederId.getText().trim());
        tempCfg.setFeederName(tfFeederName.getText().trim());
        tempCfg.setDescription(tfDescription.getText().trim());
        tempCfg.setIedHost(host);
        tempCfg.setIedPort(parseInt(tfPort, 102));
        tempCfg.setIedName(tfIedName.getText().trim());
        tempCfg.setLdInst(tfLdInst.getText().trim());
        tempCfg.setNominalVoltageKv(parseDouble(tfVnom, 23.0));
        tempCfg.setNominalCurrentA(parseDouble(tfInom, 200.0));
        tempCfg.setPollIntervalMs(parseInt(tfPollMs, 5000));

        DiscoveryPanel dlg = new DiscoveryPanel(root.getScene().getWindow(), tempCfg);
        dlg.showAndWait();

        com.harmonicmonitor.model.FeederConfig accepted = dlg.getAcceptedConfig();
        if (accepted != null) {
            loadFeederIntoForm(accepted);
            app.setStatusMessage("Configuración descubierta aplicada: " +
                accepted.getMmxuPrefix() + accepted.getMmxuLnRef() +
                " @ " + accepted.getLdInst());
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText("Error de configuración");
        alert.showAndWait();
    }

    // ── Parse helpers ─────────────────────────────────────────────────────────

    private double parseDouble(TextField tf, double def) {
        try { return Double.parseDouble(tf.getText().trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private int parseInt(TextField tf, int def) {
        try { return Integer.parseInt(tf.getText().trim()); }
        catch (NumberFormatException e) { return def; }
    }
}
