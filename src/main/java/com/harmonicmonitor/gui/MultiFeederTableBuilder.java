package com.harmonicmonitor.gui;

import com.harmonicmonitor.HarmonicMonitorApp;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Builds the SCADA {@link TableView} for {@link MultiFeederMonitorPanel}, including
 * column definitions, cell factories, and row context menu / double-click handlers.
 *
 * The builder only needs the application reference to wire row-action callbacks;
 * it has no mutable state of its own.
 *
 * Extracted from MultiFeederMonitorPanel.buildTable() (refactor F23-001).
 */
final class MultiFeederTableBuilder {

    private final HarmonicMonitorApp app;

    MultiFeederTableBuilder(HarmonicMonitorApp app) {
        this.app = app;
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    TableView<FeederRow> build() {
        TableView<FeederRow> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPlaceholder(
            new Label("Sin feeders configurados. Agregue un feeder con + Demo."));

        // ── Identificación ────────────────────────────────────────────────────
        TableColumn<FeederRow, Integer> colNum  = colInt("#",     "index",    28);
        TableColumn<FeederRow, String>  colConn = colStr("●",     "connLed",  28);
        colConn.setMaxWidth(34);
        colConn.setCellFactory(col -> connLedCell());

        TableColumn<FeederRow, String> colName   = colStr("Feeder",  "feederId",  90);
        TableColumn<FeederRow, String> colStatus = colStr("Estado",  "status",    50);
        colStatus.setCellFactory(col -> statusCell());

        // ── MMXU — Tensiones de fase (kV) ─────────────────────────────────────
        TableColumn<FeederRow, String> colVL1 = colStr("VL1 kV", "voltageL1Kv", 52);
        TableColumn<FeederRow, String> colVL2 = colStr("VL2 kV", "voltageL2Kv", 52);
        TableColumn<FeederRow, String> colVL3 = colStr("VL3 kV", "voltageL3Kv", 52);

        // ── MMXU — Corrientes de fase (A) ─────────────────────────────────────
        TableColumn<FeederRow, String> colIL1 = colStr("IL1 A", "currentL1", 50);
        TableColumn<FeederRow, String> colIL2 = colStr("IL2 A", "currentL2", 50);
        TableColumn<FeederRow, String> colIL3 = colStr("IL3 A", "currentL3", 50);

        // ── MMXU — Potencias totales 3φ ───────────────────────────────────────
        TableColumn<FeederRow, String> colP = colStr("P kW",   "activePower",   54);
        TableColumn<FeederRow, String> colQ = colStr("Q kVAR", "reactivePower", 58);
        TableColumn<FeederRow, String> colS = colStr("S kVA",  "apparentPower", 54);

        // ── MMTR — Energías totales ───────────────────────────────────────────
        TableColumn<FeederRow, String> colEKwh   = colStr("Wh (k)",   "energyKwh",   56);
        TableColumn<FeederRow, String> colEKvarh = colStr("VArh (k)", "energyKvarh", 60);

        // ── THDi + Alarmas ────────────────────────────────────────────────────
        TableColumn<FeederRow, String>  colTHDi  = colStr("THDi%", "thdi",      50);
        colTHDi.setCellFactory(col -> thdiCell());
        TableColumn<FeederRow, Integer> colAlarm = colInt("⚑",    "alarmCount", 32);
        colAlarm.setMaxWidth(40);

        tv.getColumns().addAll(
            colNum, colConn, colName, colStatus,
            colVL1, colVL2, colVL3,
            colIL1, colIL2, colIL3,
            colP, colQ, colS,
            colEKwh, colEKvarh,
            colTHDi, colAlarm
        );

        tv.setRowFactory(t -> buildRow());
        return tv;
    }

    // ── Cell factories ─────────────────────────────────────────────────────────

    private TableCell<FeederRow, String> connLedCell() {
        return new TableCell<FeederRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText("\u25CF");
                setAlignment(Pos.CENTER);
                switch (item) {
                    case "CONNECTED":
                        setStyle("-fx-text-fill: #00C853; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Conectado"));       break;
                    case "CONNECTING":
                        setStyle("-fx-text-fill: #FFB300; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Conectando\u2026")); break;
                    case "ERROR":
                        setStyle("-fx-text-fill: #D50000; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Error de conexi\u00F3n")); break;
                    case "DISCONNECTED":
                        setStyle("-fx-text-fill: #78909C; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Desconectado"));    break;
                    case "SIM":
                        setStyle("-fx-text-fill: #00BCD4; -fx-font-size: 18px;");
                        setTooltip(new Tooltip("Simulado"));        break;
                    default:
                        setStyle("-fx-text-fill: #78909C; -fx-font-size: 18px;");
                        setTooltip(null);
                }
            }
        };
    }

    private TableCell<FeederRow, String> statusCell() {
        return new TableCell<FeederRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if      (item.contains("OK"))   setStyle("-fx-text-fill: #107C10; -fx-font-weight: bold;");
                else if (item.contains("WARN")) setStyle("-fx-text-fill: #CA5010; -fx-font-weight: bold;");
                else if (item.contains("CRIT")) setStyle("-fx-text-fill: #C42B1C; -fx-font-weight: bold;");
                else                            setStyle("-fx-text-fill: " + Theme.TEXT + ";");
            }
        };
    }

    private TableCell<FeederRow, String> thdiCell() {
        return new TableCell<FeederRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                try {
                    double v = Double.parseDouble(item.replace("%", "").trim());
                    if      (v > 12) setStyle("-fx-text-fill: #C42B1C; -fx-font-weight: bold;");
                    else if (v >  8) setStyle("-fx-text-fill: #CA5010; -fx-font-weight: bold;");
                    else             setStyle("-fx-text-fill: #107C10; -fx-font-weight: bold;");
                } catch (NumberFormatException ex) { setStyle(""); }
            }
        };
    }

    // ── Row factory ────────────────────────────────────────────────────────────

    private static final String ROW_EVEN = "-fx-background-color: #FFFFFF;";
    private static final String ROW_ODD  = "-fx-background-color: #EEF1F5;";

    private TableRow<FeederRow> buildRow() {
        TableRow<FeederRow> row = new TableRow<>();
        row.indexProperty().addListener((obs, oldIdx, newIdx) ->
            row.setStyle(newIdx.intValue() % 2 == 0 ? ROW_EVEN : ROW_ODD));

        MenuItem miReconn = new MenuItem("\uD83D\uDD04 Reconectar");
        miReconn.setOnAction(e -> {
            FeederRow item = row.getItem();
            if (item != null) app.reconnectFeeder(item.getFeeder().getFeederId());
        });

        MenuItem miDash = new MenuItem("\uD83D\uDCCA Ver en Dashboard");
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
            miReconn.setDisable("CONNECTED".equals(led)
                || "CONNECTING".equals(led)
                || "SIM".equals(led));
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
    }

    // ── Column helpers ─────────────────────────────────────────────────────────

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
}
