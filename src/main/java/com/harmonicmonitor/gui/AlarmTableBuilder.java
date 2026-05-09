package com.harmonicmonitor.gui;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Builds the shared {@link TableView} used by both the active-alarms and
 * history sections of {@link AlarmsPanel}.
 *
 * Extracted from AlarmsPanel.buildAlarmTable() + col() (refactor F25-001).
 */
final class AlarmTableBuilder {

    private AlarmTableBuilder() {}

    /**
     * Creates a fully-configured {@code TableView<AlarmRow>} bound to
     * {@code data}, with colour-coded row styling by alarm level.
     */
    static TableView<AlarmRow> build(ObservableList<AlarmRow> data) {
        TableView<AlarmRow> tv = new TableView<>(data);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AlarmRow, String> colTime   = col("Timestamp",   "timestamp",     150);
        TableColumn<AlarmRow, String> colLevel  = col("Nivel",       "level",          90);
        TableColumn<AlarmRow, String> colFeeder = col("Feeder",      "feederId",       110);
        TableColumn<AlarmRow, String> colParam  = col("Par\u00E1metro", "parameter",  100);
        TableColumn<AlarmRow, String> colMsg    = col("Mensaje",     "message",        340);
        TableColumn<AlarmRow, String> colVal    = col("Valor",       "measuredValue",   80);
        TableColumn<AlarmRow, String> colThr    = col("Umbral",      "threshold",       80);
        TableColumn<AlarmRow, String> colAck    = col("Ack.",        "ack",             60);

        tv.getColumns().addAll(colTime, colLevel, colFeeder, colParam, colMsg, colVal, colThr, colAck);

        // Colour rows by alarm level; keep text readable when selected
        tv.setRowFactory(t -> new TableRow<AlarmRow>() {
            private void refresh() {
                AlarmRow item = getItem();
                if (item == null || isEmpty()) {
                    setStyle("-fx-background-color: transparent; -fx-text-fill: " + Theme.TEXT + ";");
                    return;
                }
                if (isSelected()) {
                    setStyle("-fx-background-color: #0078D4; -fx-text-fill: #FFFFFF;");
                    return;
                }
                String bg;
                switch (item.getLevel()) {
                    case "WARNING":   bg = "#CA501022"; break;
                    case "PQ_RISK":   bg = "#C42B1C22"; break;
                    case "CRITICAL":  bg = "#FF2D5530"; break;
                    case "DETECTION": bg = "#88179822"; break;
                    default:          bg = "transparent"; break;
                }
                setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + Theme.TEXT + ";");
            }
            {
                selectedProperty().addListener((obs, o, n) -> refresh());
            }
            @Override
            protected void updateItem(AlarmRow item, boolean empty) {
                super.updateItem(item, empty);
                refresh();
            }
        });

        return tv;
    }

    private static TableColumn<AlarmRow, String> col(String title, String prop, int width) {
        TableColumn<AlarmRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setMinWidth(width);
        return c;
    }
}
