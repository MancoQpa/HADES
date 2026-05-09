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

        TableColumn<FeederRow, Integer> colNum  = colInt("#",           "index",         38);
        TableColumn<FeederRow, String>  colConn = colStr("Con.",        "connLed",       46);
        colConn.setMaxWidth(46);
        colConn.setCellFactory(col -> connLedCell());

        TableColumn<FeederRow, String>  colName   = colStr("ID. Feeder",  "feederId",     160);
        TableColumn<FeederRow, String>  colStatus = colStr("Estado",      "status",        80);
        colStatus.setCellFactory(col -> statusCell());

        TableColumn<FeederRow, String>  colVolt  = colStr("Va (kV)",    "voltageKv",      75);
        TableColumn<FeederRow, String>  colCurr  = colStr("Ia (A)",     "currentA",       75);
        TableColumn<FeederRow, String>  colP     = colStr("P (kW)",     "activePower",    80);
        TableColumn<FeederRow, String>  colQ     = colStr("Q (kVAR)",   "reactivePower",  85);
        TableColumn<FeederRow, String>  colFP    = colStr("FP",         "powerFactor",    60);
        TableColumn<FeederRow, String>  colTHDi  = colStr("THDi %",     "thdi",           70);
        colTHDi.setCellFactory(col -> thdiCell());

        TableColumn<FeederRow, String>  colTHDv  = colStr("THDv %",     "thdv",           70);
        TableColumn<FeederRow, String>  colLoad  = colStr("Tipo Carga", "loadType",      140);
        TableColumn<FeederRow, Integer> colAlarm = colInt("Alrm.",      "alarmCount",     55);

        tv.getColumns().addAll(colNum, colConn, colName, colStatus,
            colVolt, colCurr, colP, colQ, colFP, colTHDi, colTHDv, colLoad, colAlarm);

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

    private TableRow<FeederRow> buildRow() {
        TableRow<FeederRow> row = new TableRow<>();

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
