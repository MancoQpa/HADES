package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.comm.IEC61850Communicator;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.LoadType;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MultiFeederMonitorPanel — SCADA table view of all feeders simultaneously.
 */
public class MultiFeederMonitorPanel {

    private final HarmonicMonitorApp app;
    private final BorderPane root;

    private TableView<FeederRow> table;
    private ObservableList<FeederRow> allData;
    private FilteredList<FeederRow> filteredData;

    private TextField searchField;
    private Label lastUpdateLbl;

    // Map feederId -> FeederRow for quick update
    private final ConcurrentHashMap<String, FeederRow> rowMap = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public MultiFeederMonitorPanel(HarmonicMonitorApp app) {
        this.app = app;
        root = buildUI();
    }

    public Node getNode() { return root; }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setTop(buildHeader());
        pane.setCenter(buildTableArea());
        return pane;
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(12, 16, 12, 16));
        h.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("📋 MONITOR MULTI-FEEDER");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("🔍 Buscar feeder...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());

        Button btnExport = new Button("Exportar CSV");
        btnExport.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 5 12 5 12; -fx-cursor: hand;");
        btnExport.setOnAction(e -> exportCsv());

        lastUpdateLbl = new Label("Última actualización: —");
        lastUpdateLbl.setStyle("-fx-text-fill: #0078D4; -fx-font-size: 11px;");

        h.getChildren().addAll(title, spacer, searchField, btnExport, lastUpdateLbl);
        return h;
    }

    private VBox buildTableArea() {
        VBox box = new VBox(0);
        box.setStyle("-fx-background-color: " + Theme.BG + ";");
        box.setPadding(new Insets(12, 16, 16, 16));

        allData = FXCollections.observableArrayList();
        filteredData = new FilteredList<>(allData, r -> true);
        table = buildTable();
        table.setItems(filteredData);
        VBox.setVgrow(table, Priority.ALWAYS);

        box.getChildren().add(table);
        return box;
    }

    private TableView<FeederRow> buildTable() {
        TableView<FeederRow> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPlaceholder(new Label("Sin feeders configurados. Agregue un feeder con + Demo."));

        TableColumn<FeederRow, Integer>  colNum    = colInt("#", "index", 38);

        // LED de conexión
        TableColumn<FeederRow, String>   colConn   = colStr("Con.", "connLed", 46);
        colConn.setMaxWidth(46);
        colConn.setCellFactory(col -> new TableCell<FeederRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText("●");
                setAlignment(javafx.geometry.Pos.CENTER);
                switch (item) {
                    case "CONNECTED":
                        setStyle("-fx-text-fill: #00C853; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Conectado"));
                        break;
                    case "CONNECTING":
                        setStyle("-fx-text-fill: #FFB300; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Conectando…"));
                        break;
                    case "ERROR":
                        setStyle("-fx-text-fill: #D50000; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Error de conexión"));
                        break;
                    case "DISCONNECTED":
                        setStyle("-fx-text-fill: #78909C; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Desconectado"));
                        break;
                    case "SIM":
                        setStyle("-fx-text-fill: #00BCD4; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Simulado"));
                        break;
                    default:
                        setStyle("-fx-text-fill: #78909C; -fx-font-size: 18px;");
                        setTooltip(null);
                }
            }
        });

        TableColumn<FeederRow, String>   colName   = colStr("ID. Feeder", "feederId", 160);
        TableColumn<FeederRow, String>   colStatus = colStr("Estado", "status", 80);
        TableColumn<FeederRow, String>   colVolt   = colStr("Va (kV)", "voltageKv", 75);
        TableColumn<FeederRow, String>   colCurr   = colStr("Ia (A)", "currentA", 75);
        TableColumn<FeederRow, String>   colP      = colStr("P (kW)", "activePower", 80);
        TableColumn<FeederRow, String>   colQ      = colStr("Q (kVAR)", "reactivePower", 85);
        TableColumn<FeederRow, String>   colFP     = colStr("FP", "powerFactor", 60);
        TableColumn<FeederRow, String>   colTHDi   = colStr("THDi %", "thdi", 70);
        TableColumn<FeederRow, String>   colTHDv   = colStr("THDv %", "thdv", 70);
        TableColumn<FeederRow, String>   colLoad   = colStr("Tipo Carga", "loadType", 140);
        TableColumn<FeederRow, Integer>  colAlarm  = colInt("Alrm.", "alarmCount", 55);

        // Color THDi column
        colTHDi.setCellFactory(col -> new TableCell<FeederRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                try {
                    double v = Double.parseDouble(item.replace("%","").trim());
                    if (v > 12)      setStyle("-fx-text-fill: #C42B1C; -fx-font-weight: bold;");
                    else if (v > 8)  setStyle("-fx-text-fill: #CA5010; -fx-font-weight: bold;");
                    else             setStyle("-fx-text-fill: #107C10; -fx-font-weight: bold;");
                } catch (NumberFormatException ex) { setStyle(""); }
            }
        });

        // Color status column
        colStatus.setCellFactory(col -> new TableCell<FeederRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if (item.contains("OK"))       setStyle("-fx-text-fill: #107C10; -fx-font-weight: bold;");
                else if (item.contains("WARN")) setStyle("-fx-text-fill: #CA5010; -fx-font-weight: bold;");
                else if (item.contains("CRIT")) setStyle("-fx-text-fill: #C42B1C; -fx-font-weight: bold;");
                else                            setStyle("-fx-text-fill: " + Theme.TEXT + ";");
            }
        });

        tv.getColumns().addAll(colNum, colConn, colName, colStatus, colVolt, colCurr,
            colP, colQ, colFP, colTHDi, colTHDv, colLoad, colAlarm);

        // Double-click: go to Dashboard with selected feeder. Right-click: context menu
        tv.setRowFactory(t -> {
            TableRow<FeederRow> row = new TableRow<>();

            MenuItem miReconn = new MenuItem("🔄 Reconectar");
            miReconn.setOnAction(e -> {
                FeederRow item = row.getItem();
                if (item != null) app.reconnectFeeder(item.getFeeder().getFeederId());
            });

            MenuItem miDash = new MenuItem("📊 Ver en Dashboard");
            miDash.setOnAction(e -> {
                if (row.getItem() != null) {
                    app.selectPanel(0);
                    app.setStatusMessage("Feeder: " + row.getItem().getFeederName());
                }
            });

            ContextMenu menu = new ContextMenu(miReconn, miDash);
            menu.setOnShowing(e -> {
                FeederRow item = row.getItem();
                if (item == null) return;
                String led = item.getConnLed();
                miReconn.setDisable("CONNECTED".equals(led) || "CONNECTING".equals(led) || "SIM".equals(led));
            });

            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && row.getItem() != null) {
                    app.selectFeederOnDashboard(row.getItem().getFeeder().getFeederId());
                    app.setStatusMessage("Feeder seleccionado: " + row.getItem().getFeederId());
                }
            });
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(menu));
            return row;
        });

        return tv;
    }

    private TableColumn<FeederRow, String> colStr(String title, String prop, int width) {
        TableColumn<FeederRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setMinWidth(width);
        return c;
    }

    private TableColumn<FeederRow, Integer> colInt(String title, String prop, int width) {
        TableColumn<FeederRow, Integer> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setMinWidth(width);
        c.setMaxWidth(width + 20);
        return c;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void updateMeasurement(FeederMeasurement m) {
        if (m == null) return;

        int alarmCount = countActiveAlarmsFor(m.getFeederId());
        FeederRow row = rowMap.get(m.getFeederId());

        if (row == null) {
            refreshFeeders();
            row = rowMap.get(m.getFeederId());
            if (row == null) return;
        }

        row.update(m, alarmCount);
        // Si es feeder real y aún mostraba CONNECTING, promover a CONNECTED al recibir datos
        if ("CONNECTING".equals(row.getConnLed())) row.setConnLed("CONNECTED");
        lastUpdateLbl.setText("Última actualización: " + LocalDateTime.now().format(FMT));
        table.refresh();
    }

    public void refreshFeeders() {
        allData.clear();
        rowMap.clear();
        List<FeederConfig> cfgs = app.getFeederConfigs();
        int idx = 1;
        for (FeederConfig cfg : cfgs) {
            boolean sim = app.isSimulatedFeeder(cfg.getFeederId());
            FeederRow row = new FeederRow(idx++, cfg, sim);
            // Recuperar estado de conexión si ya se conoce
            if (!sim) {
                IEC61850Communicator.State cs = app.getCommunicatorState(cfg.getFeederId());
                if (cs != null) {
                    switch (cs) {
                        case CONNECTED:    row.setConnLed("CONNECTED");    break;
                        case CONNECTING:   row.setConnLed("CONNECTING");   break;
                        case ERROR:        row.setConnLed("ERROR");         break;
                        case DISCONNECTED: row.setConnLed("DISCONNECTED"); break;
                    }
                }
            }
            FeederMeasurement m = app.getLatestMeasurements().get(cfg.getFeederId());
            if (m != null) row.update(m, countActiveAlarmsFor(cfg.getFeederId()));
            allData.add(row);
            rowMap.put(cfg.getFeederId(), row);
        }
        applyFilter();
    }

    private int countActiveAlarmsFor(String feederId) {
        return (int) app.getAlarmEngine().getActiveAlarms().stream()
            .filter(a -> a.getFeederId().equals(feederId)).count();
    }

    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        filteredData.setPredicate(row ->
            q.isEmpty() ||
            row.getFeederName().toLowerCase().contains(q) ||
            row.getFeeder().getFeederId().toLowerCase().contains(q));
    }

    /** Actualiza el LED de conexión para un feeder. Llamado desde HarmonicMonitorApp. */
    public void updateConnectionState(String feederId, IEC61850Communicator.State state) {
        FeederRow row = rowMap.get(feederId);
        if (row == null) return;
        String led;
        switch (state) {
            case CONNECTED:    led = "CONNECTED";    break;
            case CONNECTING:   led = "CONNECTING";   break;
            case ERROR:        led = "ERROR";         break;
            case DISCONNECTED: led = "DISCONNECTED"; break;
            default:           led = "DISCONNECTED"; break;
        }
        row.setConnLed(led);
        table.refresh();
    }

    private void exportCsv() {
        if (app.getFeederConfigs().isEmpty()) { app.setStatusMessage("Sin feeders configurados"); return; }
        StringBuilder sb = new StringBuilder("Exportado: ");
        for (FeederConfig cfg : app.getFeederConfigs()) {
            try { sb.append(app.getDataStorage().exportToCsv(cfg.getFeederId(), null, null)).append("  "); }
            catch (Exception ex) { sb.append("[Error: ").append(cfg.getFeederId()).append("]  "); }
        }
        app.setStatusMessage(sb.toString().trim());
    }

    // ── FeederRow ─────────────────────────────────────────────────────────────

    public static class FeederRow {
        private int index;
        private final FeederConfig feeder;
        private String feederName;
        private String connLed;   // LED de conexión: CONNECTED | CONNECTING | ERROR | DISCONNECTED | SIM
        private String status;
        private String voltageKv;
        private String currentA;
        private String activePower;
        private String reactivePower;
        private String powerFactor;
        private String thdi;
        private String thdv;
        private String loadType;
        private int    alarmCount;

        public FeederRow(int index, FeederConfig feeder, boolean simulated) {
            this.index      = index;
            this.feeder     = feeder;
            this.feederName = feeder.getFeederName();
            this.connLed    = simulated ? "SIM" : "CONNECTING";
            this.status     = "● ESPERANDO";
            this.voltageKv  = "—";
            this.currentA   = "—";
            this.activePower    = "—";
            this.reactivePower  = "—";
            this.powerFactor    = "—";
            this.thdi       = "—";
            this.thdv       = "—";
            this.loadType   = "—";
            this.alarmCount = 0;
        }

        public void update(FeederMeasurement m, int alarms) {
            this.voltageKv      = String.format("%.3f", m.getVoltageAvg() / 1000.0);
            this.currentA       = String.format("%.1f", m.getCurrentAvg());
            this.activePower    = String.format("%.0f", m.getActivePower());
            this.reactivePower  = String.format("%.0f", m.getReactivePower());
            this.powerFactor    = String.format("%.3f", m.getPowerFactor());
            this.thdi           = String.format("%.1f", m.getThdCurrentAvg());
            this.thdv           = String.format("%.1f", m.getThdVoltageAvg());
            this.loadType       = m.getDetectedLoadType().getDisplayName();
            this.alarmCount     = alarms;

            double thdiv = m.getThdCurrentAvg();
            if (alarms > 0 && thdiv > 8)  this.status = "● WARN";
            else if (alarms > 2)           this.status = "● WARN";
            else                           this.status = "● OK";
        }

        public FeederConfig getFeeder() { return feeder; }
        public int    getIndex()         { return index; }
        public String getFeederId()      { return feeder.getFeederId(); }
        public String getFeederName()    { return feederName; }
        public String getConnLed()       { return connLed; }
        public void   setConnLed(String s) { this.connLed = s; }
        public String getStatus()        { return status; }
        public String getVoltageKv()     { return voltageKv; }
        public String getCurrentA()      { return currentA; }
        public String getActivePower()   { return activePower; }
        public String getReactivePower() { return reactivePower; }
        public String getPowerFactor()   { return powerFactor; }
        public String getThdi()          { return thdi; }
        public String getThdv()          { return thdv; }
        public String getLoadType()      { return loadType; }
        public int    getAlarmCount()    { return alarmCount; }
    }
}
