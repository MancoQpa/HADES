package com.harmonicmonitor.gui;

import com.harmonicmonitor.model.NetworkTopology;
import com.harmonicmonitor.model.SimProfile;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Builds the right-side configuration form of {@link FeederMgmtPanel}.
 *
 * Constructs all form fields and exposes them as package-private references
 * so the panel can bind them after calling {@link #build()}.
 *
 * Extracted from {@link FeederMgmtPanel} (refactor F15-001).
 */
final class FeederFormBuilder {

    private final FeederMgmtPanel panel;

    // ── Output fields (package-private) — copied by FeederMgmtPanel.buildUI() ─

    // Identification
    TextField tfFeederId;
    TextField tfFeederName;
    TextField tfDescription;

    // IEC 61850 connection
    TextField tfHost;
    TextField tfPort;
    TextField tfIedName;
    TextField tfMmxuRef;
    TextField tfMmxuPrefix;
    TextField tfMhaiRef;
    TextField tfLdInst;
    TextField tfPollMs;

    // Network parameters
    TextField tfVnom;
    TextField tfInom;
    TextField tfScc;
    TextField tfCap;

    // Unit scaling
    TextField tfPowerScale;
    TextField tfPfScale;
    TextField tfAnalogScale;

    // Thresholds
    TextField tfMaxTHDv;
    TextField tfMaxTHDi;
    TextField tfCvMax;
    TextField tfCryptoTHDi;

    // Combo boxes
    ComboBox<NetworkTopology> topologyCombo;
    ComboBox<SimProfile>      cbSimProfile;

    // ── Constructor ───────────────────────────────────────────────────────────

    FeederFormBuilder(FeederMgmtPanel panel) {
        this.panel = panel;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    ScrollPane build() {
        VBox form = new VBox(8);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: " + Theme.BG + ";");

        Label formTitle = new Label("CONFIGURACI\u00D3N DE FEEDER");
        formTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        // Identification
        VBox idSection = buildSection("IDENTIFICACI\u00D3N");
        tfFeederId    = field("AL-01");              addFormRow(idSection, "ID Feeder:",    tfFeederId);
        tfFeederName  = field("Alimentador MT 1");   addFormRow(idSection, "Nombre:",       tfFeederName);
        tfDescription = field("");                   addFormRow(idSection, "Descripci\u00F3n:", tfDescription);

        // IEC 61850
        VBox iecSection = buildSection("CONEXI\u00D3N IEC 61850");
        tfHost       = field("127.0.0.1"); addFormRow(iecSection, "Host / IP:",      tfHost);
        tfPort       = field("102");       addFormRow(iecSection, "Puerto MMS:",     tfPort);
        tfIedName    = field("IED1");      addFormRow(iecSection, "Nombre IED:",     tfIedName);
        tfMmxuPrefix = field("");          addFormRow(iecSection, "Prefijo MMXU:",   tfMmxuPrefix);
        tfMmxuRef    = field("MMXU1");     addFormRow(iecSection, "Ref. MMXU LN:",  tfMmxuRef);
        tfMhaiRef    = field("MHAI1");     addFormRow(iecSection, "Ref. MHAI LN:",  tfMhaiRef);
        tfLdInst     = field("LD0");       addFormRow(iecSection, "LD Instance:",   tfLdInst);
        tfPollMs     = field("5000");      addFormRow(iecSection, "Polling (ms):",  tfPollMs);
        tfPowerScale = field("0.001");     addFormRow(iecSection, "Escala Potencia:", tfPowerScale);
        tfAnalogScale = field("1.0");      addFormRow(iecSection, "Escala V/I/Hz:", tfAnalogScale);

        Label scaleTip = new Label("  0.001=IED en W (estandar)  |  1.0=IED en kW (ION 7400)");
        scaleTip.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT2 + ";");
        iecSection.getChildren().add(scaleTip);

        tfPfScale = field("1.0"); addFormRow(iecSection, "Escala FP:", tfPfScale);

        Label pfTip = new Label("  1.0=per-unit (estandar)  |  0.01=porcentaje (ION 7400)");
        pfTip.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT2 + ";");
        iecSection.getChildren().add(pfTip);

        // Discover IED button
        Button btnDiscover = new Button("\uD83D\uDD0D Descubrir IED");
        btnDiscover.setStyle("-fx-background-color: #0078D420; -fx-text-fill: #0078D4;" +
            "-fx-border-color: #0078D4; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 12 5 12; -fx-cursor: hand;");
        btnDiscover.setTooltip(new Tooltip(
            "Conecta al IED y descubre autom\u00E1ticamente sus Logical Nodes (MMXU, MHAI, MSQI, MMTR, MSTA)\n" +
            "Tambi\u00E9n detecta autom\u00E1ticamente la escala de potencia (W o kW) seg\u00FAn el modelo del IED."));
        btnDiscover.setOnAction(e -> panel.openDiscoveryDialog());

        HBox presetRow = new HBox(8, btnDiscover);
        iecSection.getChildren().add(presetRow);

        // Network parameters
        VBox netSection = buildSection("PAR\u00C1METROS DE RED MT");
        tfVnom = field("23.0");  addFormRow(netSection, "Vnom (kV):",           tfVnom);
        tfInom = field("200.0"); addFormRow(netSection, "Inom (A):",            tfInom);
        tfScc  = field("100.0"); addFormRow(netSection, "Scc (MVA):",           tfScc);
        tfCap  = field("5.0");   addFormRow(netSection, "Capacitancia (\u00B5F):", tfCap);

        // Thresholds
        VBox thrSection = buildSection("UMBRALES DE CALIDAD");
        tfMaxTHDv    = field("5.0");  addFormRow(thrSection, "THDv m\u00E1x. (%):",      tfMaxTHDv);
        tfMaxTHDi    = field("8.0");  addFormRow(thrSection, "THDi m\u00E1x. (%):",      tfMaxTHDi);
        tfCvMax      = field("5.0");  addFormRow(thrSection, "CV electr\u00F3nico (%):", tfCvMax);
        tfCryptoTHDi = field("15.0"); addFormRow(thrSection, "THDi cripto/DC (%):",      tfCryptoTHDi);

        // Topology
        VBox topoSection = buildSection("TOPOLOG\u00CDA DE RED");
        topologyCombo = new ComboBox<>(FXCollections.observableArrayList(NetworkTopology.values()));
        topologyCombo.setValue(NetworkTopology.SINGLE_FEEDER);
        topologyCombo.setMaxWidth(Double.MAX_VALUE);
        addFormRow(topoSection, "Topolog\u00EDa:", topologyCombo);

        // Simulation profile
        VBox simSection = buildSection("PERFIL DE SIMULACI\u00D3N");
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

        // Action buttons
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

    // ── Private helpers ───────────────────────────────────────────────────────

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

        Button btnConnect = new Button("\u26A1  CONECTAR Y AGREGAR");
        btnConnect.setStyle("-fx-background-color: #0078D4; -fx-text-fill: #FFFFFF; -fx-font-weight: bold;" +
            "-fx-border-color: #0078D4; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 8 18 8 18; -fx-cursor: hand;");
        btnConnect.setOnAction(e -> panel.connectAndAdd());

        Button btnSim = new Button("\uD83D\uDD2E  AGREGAR SIMULADO");
        btnSim.setStyle("-fx-background-color: #CA5010; -fx-text-fill: #F0F0F0; -fx-font-weight: bold;" +
            "-fx-border-color: #CA5010; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 8 18 8 18; -fx-cursor: hand;");
        btnSim.setOnAction(e -> panel.addSimulated());

        Button btnClear = new Button("Limpiar");
        btnClear.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 8 14 8 14; -fx-cursor: hand;");
        btnClear.setOnAction(e -> panel.clearForm());

        row.getChildren().addAll(btnConnect, btnSim, btnClear);
        return row;
    }
}
