package com.harmonicmonitor.gui;

import com.harmonicmonitor.alarm.AlarmEngine;
import com.harmonicmonitor.model.AlarmEvent;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Card de alarmas activas: lista compacta (máx 7) + botón "Ver todas".
 */
class AlarmMiniCard {

    private final VBox  card;
    private final VBox  alarmListBox;

    AlarmMiniCard(AlarmEngine alarmEngine, Runnable onViewAll) {
        card = new VBox(8);
        card.setStyle(
            "-fx-background-color: " + Theme.CARD + "; -fx-border-color: #D0D3D8; -fx-border-width: 1;" +
            "-fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(12));
        card.setPrefWidth(280);
        card.setMinWidth(200);

        Label title = new Label("ALARMAS ACTIVAS");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        alarmListBox = new VBox(5);
        Label emptyLbl = new Label("✓  Sin alarmas activas");
        emptyLbl.setStyle("-fx-text-fill: #107C10; -fx-font-size: 11px;");
        alarmListBox.getChildren().add(emptyLbl);

        ScrollPane scroll = new ScrollPane(alarmListBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(160);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button viewAll = new Button("Ver todas las alarmas →");
        viewAll.setMaxWidth(Double.MAX_VALUE);
        viewAll.setStyle(
            "-fx-background-color: #EBF3FC; -fx-text-fill: #0078D4; -fx-font-size: 11px;" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; -fx-border-radius: 4;" +
            "-fx-background-radius: 4; -fx-padding: 5 10 5 10; -fx-cursor: hand;");
        viewAll.setOnAction(e -> onViewAll.run());

        card.getChildren().addAll(title, scroll, viewAll);

        // store engine ref for updateAlarms
        this.engine = alarmEngine;
    }

    private final AlarmEngine engine;

    VBox getNode() { return card; }

    void updateAlarms() {
        List<AlarmEvent> active = engine.getActiveAlarms();
        alarmListBox.getChildren().clear();
        if (active.isEmpty()) {
            Label l = new Label("✓  Sin alarmas activas");
            l.setStyle("-fx-text-fill: #107C10; -fx-font-size: 11px;");
            alarmListBox.getChildren().add(l);
            return;
        }
        int max = Math.min(active.size(), 7);
        for (int i = 0; i < max; i++) {
            AlarmEvent ev = active.get(i);
            Label row = new Label(levelIcon(ev.getLevel()) + "  " + shorten(ev.getMessage(), 60));
            row.setStyle("-fx-text-fill: " + ev.getLevel().getColorHex() + "; -fx-font-size: 11px;");
            row.setWrapText(false);
            alarmListBox.getChildren().add(row);
        }
        if (active.size() > max) {
            Label more = new Label("  ... y " + (active.size() - max) + " alarmas más");
            more.setStyle("-fx-text-fill: #0078D4; -fx-font-size: 11px;");
            alarmListBox.getChildren().add(more);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String levelIcon(AlarmEvent.Level level) {
        switch (level) {
            case WARNING:   return "🟡";
            case PQ_RISK:   return "🔴";
            case CRITICAL:  return "⛔";
            case DETECTION: return "🟣";
            default:        return "●";
        }
    }

    private String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
