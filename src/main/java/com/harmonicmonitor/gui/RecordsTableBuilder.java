package com.harmonicmonitor.gui;

import com.harmonicmonitor.comtrade.ComtradeTriggerEngine.RecordEntry;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/**
 * Builds the {@link TableView} for {@link RecordsPanel}: columns, row factory,
 * cell factories, and placeholder node.
 *
 * Extracted from RecordsPanel.buildTable() + helpers (refactor F27-001).
 */
final class RecordsTableBuilder {

    private final ObservableList<RecordEntry> items;
    private final Runnable onDoubleClick;

    RecordsTableBuilder(ObservableList<RecordEntry> items, Runnable onDoubleClick) {
        this.items        = items;
        this.onDoubleClick = onDoubleClick;
    }

    @SuppressWarnings("unchecked")
    TableView<RecordEntry> build() {
        TableView<RecordEntry> tv = new TableView<>(items);
        tv.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + ";");
        tv.setPlaceholder(buildPlaceholder());
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tv, javafx.scene.layout.Priority.ALWAYS);

        TableColumn<RecordEntry, String> colTs = new TableColumn<>("Fecha / Hora");
        colTs.setMinWidth(160); colTs.setMaxWidth(200);
        colTs.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestampStr()));
        colTs.setCellFactory(c -> styledCell());

        TableColumn<RecordEntry, String> colFeeder = new TableColumn<>("Feeder");
        colFeeder.setMinWidth(100); colFeeder.setMaxWidth(160);
        colFeeder.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().feederId));
        colFeeder.setCellFactory(c -> styledCell());

        TableColumn<RecordEntry, String> colLevel = new TableColumn<>("Nivel");
        colLevel.setMinWidth(90); colLevel.setMaxWidth(110);
        colLevel.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().level.displayName()));
        colLevel.setCellFactory(c -> levelCell());

        TableColumn<RecordEntry, String> colCause = new TableColumn<>("Causa");
        colCause.setMinWidth(130); colCause.setMaxWidth(200);
        colCause.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().cause));
        colCause.setCellFactory(c -> styledCell());

        TableColumn<RecordEntry, String> colReason = new TableColumn<>("Descripci\u00F3n / Norma");
        colReason.setCellValueFactory(d -> {
            String r = d.getValue().reason;
            return new SimpleStringProperty(r.contains("\n") ? r.substring(0, r.indexOf('\n')) : r);
        });
        colReason.setCellFactory(c -> styledCell());

        TableColumn<RecordEntry, String> colFile = new TableColumn<>("Archivo COMTRADE");
        colFile.setMinWidth(220); colFile.setMaxWidth(320);
        colFile.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().cfgFile != null ? d.getValue().cfgFile.getName() : "\u2014"));
        colFile.setCellFactory(c -> styledCell());

        tv.getColumns().addAll(colTs, colFeeder, colLevel, colCause, colReason, colFile);

        tv.setRowFactory(t -> new TableRow<RecordEntry>() {
            private void refresh() {
                RecordEntry item = getItem();
                if (item == null || isEmpty()) { setStyle("-fx-background-color: transparent;"); return; }
                if (isSelected()) { setStyle("-fx-background-color: #0078D4;"); return; }
                String bg;
                switch (item.level) {
                    case CRITICAL:  bg = "#FF2D5530"; break;
                    case PQ_RISK:   bg = "#C42B1C22"; break;
                    case DETECTION: bg = "#88179822"; break;
                    case WARNING:   bg = "#CA501018"; break;
                    default:        bg = "transparent"; break;
                }
                setStyle("-fx-background-color: " + bg + ";");
            }
            { selectedProperty().addListener((obs, o, n) -> refresh()); }
            @Override protected void updateItem(RecordEntry item, boolean empty) {
                super.updateItem(item, empty); refresh();
            }
        });

        tv.setOnMouseClicked(e -> { if (e.getClickCount() == 2) onDoubleClick.run(); });
        return tv;
    }

    private Node buildPlaceholder() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        Label icon = new Label("\uD83D\uDCFC");
        icon.setStyle("-fx-font-size: 48px;");
        Label msg = new Label("No hay registros COMTRADE a\u00FAn");
        msg.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-weight: bold;");
        Label hint = new Label(
            "Los registros se generan autom\u00E1ticamente cuando los valores\n"
            + "superan los l\u00EDmites normativos o al detectar cargas electr\u00F3nicas.\n\n"
            + "Use '\u26A1 Disparo manual' para capturar el estado actual de un feeder.\n"
            + "Use '\uD83E\uDDEC INICIAR CARACTERIZACI\u00D3N' para acumular dataset de ML.");
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-text-alignment: center;");
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        box.getChildren().addAll(icon, msg, hint);
        return box;
    }

    private <T> TableCell<RecordEntry, T> styledCell() {
        return new TableCell<RecordEntry, T>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else { setText(item.toString()); setStyle("-fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 11px;"); }
            }
        };
    }

    private TableCell<RecordEntry, String> levelCell() {
        return new TableCell<RecordEntry, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                String color;
                switch (item) {
                    case "CR\u00CDTICO":   color = "#FF4060"; break;
                    case "PQ-RIESGO":      color = "#FF8040"; break;
                    case "DETECCI\u00D3N": color = "#A070FF"; break;
                    case "WARNING":        color = "#F0A030"; break;
                    default:               color = "#90A0B0"; break;
                }
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-font-size: 11px;");
            }
        };
    }
}
