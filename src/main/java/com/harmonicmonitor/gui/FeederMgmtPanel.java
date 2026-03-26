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

    // Form fields — Identification
    private TextField tfFeederId;
    private TextField tfFeederName;
    private TextField tfDescription;

    // Form fields — IEC 61850 connection
    private TextField tfHost;
    private TextField tfPort;
    private TextField tfIedName;
    private TextField tfMmxuRef;
    private TextField tfMmxuPrefix;
    private TextField tfMhaiRef;
    private TextField tfLdInst;
    private TextField tfPollMs;

    // Form fields — Network parameters
    private TextField tfVnom;
    private TextField tfInom;
    private TextField tfScc;
    private TextField tfCap;

    // Form fields — Unit scaling (meter-specific)
    private TextField tfPowerScale;
    private TextField tfPfScale;
    private TextField tfAnalogScale;

    // Form fields — Thresholds
    private TextField tfMaxTHDv;
    private TextField tfMaxTHDi;
    private TextField tfCvMax;
    private TextField tfCryptoTHDi;

    // Topology
    private ComboBox<NetworkTopology> topologyCombo;

    // Simulation profile
    private ComboBox<SimProfile> cbSimProfile;

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

        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color: " + Theme.BG + ";");
        split.setDividerPositions(0.28);
        split.getItems().addAll(buildFeederList(), buildFormArea());
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

    // ── Right: form ────────────────────────────────────────────────────────────

    private ScrollPane buildFormArea() {
        VBox form = new VBox(8);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: " + Theme.BG + ";");

        Label formTitle = new Label("CONFIGURACIÓN DE FEEDER");
        formTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        // Identification
        VBox idSection = buildSection("IDENTIFICACIÓN");
        tfFeederId    = field("AL-01"); addFormRow(idSection, "ID Feeder:", tfFeederId);
        tfFeederName  = field("Alimentador MT 1"); addFormRow(idSection, "Nombre:", tfFeederName);
        tfDescription = field(""); addFormRow(idSection, "Descripción:", tfDescription);

        // IEC 61850
        VBox iecSection = buildSection("CONEXIÓN IEC 61850");
        tfHost       = field("127.0.0.1"); addFormRow(iecSection, "Host / IP:", tfHost);
        tfPort       = field("102");       addFormRow(iecSection, "Puerto MMS:", tfPort);
        tfIedName    = field("IED1");      addFormRow(iecSection, "Nombre IED:", tfIedName);
        tfMmxuPrefix = field("");          addFormRow(iecSection, "Prefijo MMXU:", tfMmxuPrefix);
        tfMmxuRef    = field("MMXU1");     addFormRow(iecSection, "Ref. MMXU LN:", tfMmxuRef);
        tfMhaiRef    = field("MHAI1");     addFormRow(iecSection, "Ref. MHAI LN:", tfMhaiRef);
        tfLdInst     = field("LD0");       addFormRow(iecSection, "LD Instance:", tfLdInst);
        tfPollMs     = field("5000");      addFormRow(iecSection, "Polling (ms):", tfPollMs);
        tfPowerScale  = field("0.001");    addFormRow(iecSection, "Escala Potencia:", tfPowerScale);
        tfAnalogScale = field("1.0");      addFormRow(iecSection, "Escala V/I/Hz:", tfAnalogScale);
        Label scaleTip = new Label("  0.001=IED en W (estandar)  |  1.0=IED en kW (ION 7400)");
        scaleTip.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT2 + ";");
        iecSection.getChildren().add(scaleTip);
        tfPfScale    = field("1.0");       addFormRow(iecSection, "Escala FP:", tfPfScale);
        Label pfTip = new Label("  1.0=per-unit (estandar)  |  0.01=porcentaje (ION 7400)");
        pfTip.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT2 + ";");
        iecSection.getChildren().add(pfTip);

        // Discover IED button
        Button btnDiscover = new Button("🔍 Descubrir IED");
        btnDiscover.setStyle("-fx-background-color: #0078D420; -fx-text-fill: #0078D4;" +
            "-fx-border-color: #0078D4; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 12 5 12; -fx-cursor: hand;");
        btnDiscover.setTooltip(new Tooltip(
            "Conecta al IED y descubre automáticamente sus Logical Nodes (MMXU, MHAI, MSQI, MMTR, MSTA)\n" +
            "También detecta automáticamente la escala de potencia (W o kW) según el modelo del IED."));
        btnDiscover.setOnAction(e -> openDiscoveryDialog());

        HBox presetRow = new HBox(8, btnDiscover);
        iecSection.getChildren().add(presetRow);

        // Network parameters
        VBox netSection = buildSection("PARÁMETROS DE RED MT");
        tfVnom = field("23.0"); addFormRow(netSection, "Vnom (kV):", tfVnom);
        tfInom = field("200.0"); addFormRow(netSection, "Inom (A):", tfInom);
        tfScc  = field("100.0"); addFormRow(netSection, "Scc (MVA):", tfScc);
        tfCap  = field("5.0");  addFormRow(netSection, "Capacitancia (µF):", tfCap);

        // Thresholds
        VBox thrSection = buildSection("UMBRALES DE CALIDAD");
        tfMaxTHDv   = field("5.0");  addFormRow(thrSection, "THDv máx. (%):", tfMaxTHDv);
        tfMaxTHDi   = field("8.0");  addFormRow(thrSection, "THDi máx. (%):", tfMaxTHDi);
        tfCvMax     = field("5.0");  addFormRow(thrSection, "CV electrónico (%):", tfCvMax);
        tfCryptoTHDi = field("15.0"); addFormRow(thrSection, "THDi cripto/DC (%):", tfCryptoTHDi);

        // Topology
        VBox topoSection = buildSection("TOPOLOGÍA DE RED");
        topologyCombo = new ComboBox<>(FXCollections.observableArrayList(NetworkTopology.values()));
        topologyCombo.setValue(NetworkTopology.SINGLE_FEEDER);
        topologyCombo.setMaxWidth(Double.MAX_VALUE);
        addFormRow(topoSection, "Topología:", topologyCombo);

        // Simulation profile
        VBox simSection = buildSection("PERFIL DE SIMULACIÓN");
        cbSimProfile = new ComboBox<>(FXCollections.observableArrayList(SimProfile.values()));
        cbSimProfile.setValue(SimProfile.CRYPTO_MINER);
        cbSimProfile.setMaxWidth(Double.MAX_VALUE);
        cbSimProfile.setTooltip(new Tooltip("Perfil de carga para el feeder simulado"));
        addFormRow(simSection, "Perfil:", cbSimProfile);

        Label lblSimDesc = new Label();
        lblSimDesc.setWrapText(true);
        lblSimDesc.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + ";");
        cbSimProfile.valueProperty().addListener((obs, o, n) -> {
            if (n != null) lblSimDesc.setText(n.description);
        });
        lblSimDesc.setText(cbSimProfile.getValue().description);
        simSection.getChildren().add(lblSimDesc);

        // Buttons
        HBox btnRow = buildActionButtons();

        form.getChildren().addAll(
            formTitle,
            idSection, iecSection, netSection, thrSection, topoSection, simSection,
            btnRow
        );

        ScrollPane sp = new ScrollPane(form);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: " + Theme.BG + "; -fx-background: " + Theme.BG + ";");
        return sp;
    }

    private VBox buildSection(String title) {
        VBox sec = new VBox(5);
        sec.setPadding(new Insets(8, 0, 4, 0));
        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0078D4; -fx-padding: 0 0 3 0;");
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #CCCCCC;");
        sec.getChildren().addAll(lbl, sep);
        return sec;
    }

    private TextField field(String defaultVal) {
        TextField tf = new TextField(defaultVal);
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private void addFormRow(VBox container, String label, Control control) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + "; -fx-min-width: 145px;");
        HBox.setHgrow(control, Priority.ALWAYS);
        row.getChildren().addAll(lbl, control);
        container.getChildren().add(row);
    }

    private HBox buildActionButtons() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 0, 0, 0));

        Button btnConnect = new Button("⚡  CONECTAR Y AGREGAR");
        btnConnect.setStyle("-fx-background-color: #0078D4; -fx-text-fill: #FFFFFF; -fx-font-weight: bold;" +
            "-fx-border-color: #0078D4; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 8 18 8 18; -fx-cursor: hand;");
        btnConnect.setOnAction(e -> connectAndAdd());

        Button btnSim = new Button("🔮  AGREGAR SIMULADO");
        btnSim.setStyle("-fx-background-color: #CA5010; -fx-text-fill: #F0F0F0; -fx-font-weight: bold;" +
            "-fx-border-color: #CA5010; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 8 18 8 18; -fx-cursor: hand;");
        btnSim.setOnAction(e -> addSimulated());

        Button btnClear = new Button("Limpiar");
        btnClear.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 8 14 8 14; -fx-cursor: hand;");
        btnClear.setOnAction(e -> clearForm());

        row.getChildren().addAll(btnConnect, btnSim, btnClear);
        return row;
    }

    // ── Form operations ───────────────────────────────────────────────────────

    private void applyIedServerSimPreset() {
        tfHost.setText("127.0.0.1");       // IEDNavigator servidor local
        tfPort.setText("102");
        tfIedName.setText("AndroidIED");   // IED name del SimpleIED.icd (el más completo con MMXU)
        tfMmxuPrefix.setText("");          // Sin prefijo para IED estándar
        tfMmxuRef.setText("MMXU1");
        tfLdInst.setText("LD0");
        tfMhaiRef.setText("MHAI1");
        tfVnom.setText("23.0");
        tfInom.setText("200.0");
        tfScc.setText("100.0");
        tfPollMs.setText("3000");
        tfPowerScale.setText("0.001");     // Servidor reporta en W (IEC 61850 estándar)
        tfPfScale.setText("1.0");          // FP como per-unit
        tfAnalogScale.setText("1.0");      // Valores en unidades SI directas
    }

    private void applyAndroidSimPreset() {
        tfHost.setText("192.168.43.1");   // IP típica de hotspot Android
        tfPort.setText("10102");           // Puerto sin root en Android
        tfIedName.setText("AndroidIED");   // IED name del simulador Android (SimpleIED.icd)
        tfMmxuPrefix.setText("");          // Sin prefijo (MMXU1 estándar)
        tfMmxuRef.setText("MMXU1");
        tfLdInst.setText("LD0");
        tfMhaiRef.setText("MHAI1");
        tfVnom.setText("13.28");           // 23 kV / sqrt(3)
        tfInom.setText("200.0");
        tfScc.setText("80.0");
        tfPollMs.setText("3000");
        tfPowerScale.setText("0.001");     // Simulador reporta en W → HarmonicMonitor necesita kW
        tfPfScale.setText("1.0");          // Simulador reporta FP como per-unit (0.0–1.0)
        tfAnalogScale.setText("1.0");      // Valores en unidades SI directas
    }

    private void applyIon7400Preset() {
        tfHost.setText("169.254.150.104");
        tfPort.setText("102");
        tfIedName.setText("cbo2");
        tfMmxuPrefix.setText("M03_");
        tfMmxuRef.setText("MMXU1");
        tfLdInst.setText("LD0");
        tfMhaiRef.setText("MHAI1");
        tfVnom.setText("23.0");
        tfInom.setText("200.0");
        tfScc.setText("80.0");
        tfPollMs.setText("5000");
        tfPowerScale.setText("1.0");      // ION 7400 reporta potencia en kW, no en W
        tfPfScale.setText("0.01");        // ION 7400 reporta FP como porcentaje
        tfAnalogScale.setText("0.0001");  // ION 7400: units.multiplier=-4 → dividir por 10000
    }

    private void clearForm() {
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

        try { cfg.setIedPort(Integer.parseInt(tfPort.getText().trim())); }
        catch (NumberFormatException e) { cfg.setIedPort(102); }

        try { cfg.setPollIntervalMs((int) Double.parseDouble(tfPollMs.getText().trim())); }
        catch (NumberFormatException e) { cfg.setPollIntervalMs(5000); }

        try { cfg.setNominalVoltageKv(Double.parseDouble(tfVnom.getText().trim())); }
        catch (NumberFormatException e) { cfg.setNominalVoltageKv(23.0); }

        try { cfg.setNominalCurrentA(Double.parseDouble(tfInom.getText().trim())); }
        catch (NumberFormatException e) { cfg.setNominalCurrentA(200.0); }

        try { cfg.setShortCircuitMva(Double.parseDouble(tfScc.getText().trim())); }
        catch (NumberFormatException e) { cfg.setShortCircuitMva(100.0); }

        try { cfg.setFeederCapacitanceMicroF(Double.parseDouble(tfCap.getText().trim())); }
        catch (NumberFormatException e) { cfg.setFeederCapacitanceMicroF(5.0); }

        try { cfg.setMaxThdVoltagePct(Double.parseDouble(tfMaxTHDv.getText().trim())); }
        catch (NumberFormatException e) { cfg.setMaxThdVoltagePct(5.0); }

        try { cfg.setMaxThdCurrentPct(Double.parseDouble(tfMaxTHDi.getText().trim())); }
        catch (NumberFormatException e) { cfg.setMaxThdCurrentPct(8.0); }

        try { cfg.setMaxCvElectronicThreshold(Double.parseDouble(tfCvMax.getText().trim()) / 100.0); }
        catch (NumberFormatException e) { cfg.setMaxCvElectronicThreshold(0.05); }

        try { cfg.setMinThdICryptoThreshold(Double.parseDouble(tfCryptoTHDi.getText().trim())); }
        catch (NumberFormatException e) { cfg.setMinThdICryptoThreshold(15.0); }

        try { cfg.setPowerScaleFactor(Double.parseDouble(tfPowerScale.getText().trim())); }
        catch (NumberFormatException e) { cfg.setPowerScaleFactor(0.001); }

        try { cfg.setPfScaleFactor(Double.parseDouble(tfPfScale.getText().trim())); }
        catch (NumberFormatException e) { cfg.setPfScaleFactor(1.0); }
        try { cfg.setAnalogScaleFactor(Double.parseDouble(tfAnalogScale.getText().trim())); }
        catch (NumberFormatException e) { cfg.setAnalogScaleFactor(1.0); }

        return cfg;
    }

    private void connectAndAdd() {
        FeederConfig cfg = buildConfigFromForm();
        if (cfg == null) return;
        app.addFeeder(cfg);
        refreshList();
        app.setStatusMessage("Conectando: " + cfg.getFeederName() + " → " + cfg.getIedHost() + ":" + cfg.getIedPort());
    }

    private void addSimulated() {
        FeederConfig cfg = buildConfigFromForm();
        if (cfg == null) return;
        cfg.setSimProfile(cbSimProfile.getValue());
        app.addSimulatedFeeder(cfg);
        refreshList();
    }

    private void openDiscoveryDialog() {
        // Build a temporary config from the form so the dialog uses current host/port/iedName
        String host = tfHost.getText().trim();
        if (host.isEmpty()) { showError("Ingrese el Host / IP del IED antes de descubrir."); return; }

        FeederConfig tempCfg = new FeederConfig();
        tempCfg.setFeederId(tfFeederId.getText().trim().isEmpty() ? "DISC" : tfFeederId.getText().trim());
        tempCfg.setFeederName(tfFeederName.getText().trim());
        tempCfg.setDescription(tfDescription.getText().trim());
        tempCfg.setIedHost(host);
        try { tempCfg.setIedPort(Integer.parseInt(tfPort.getText().trim())); }
        catch (NumberFormatException ex) { tempCfg.setIedPort(102); }
        tempCfg.setIedName(tfIedName.getText().trim());
        tempCfg.setLdInst(tfLdInst.getText().trim());
        try { tempCfg.setNominalVoltageKv(Double.parseDouble(tfVnom.getText().trim())); }
        catch (NumberFormatException ex) { tempCfg.setNominalVoltageKv(23.0); }
        try { tempCfg.setNominalCurrentA(Double.parseDouble(tfInom.getText().trim())); }
        catch (NumberFormatException ex) { tempCfg.setNominalCurrentA(200.0); }
        try { tempCfg.setPollIntervalMs(Integer.parseInt(tfPollMs.getText().trim())); }
        catch (NumberFormatException ex) { tempCfg.setPollIntervalMs(5000); }

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
}
