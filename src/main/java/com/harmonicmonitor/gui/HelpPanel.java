package com.harmonicmonitor.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * HelpPanel — Documentation and usage help organized by topics.
 * Includes rigorous, peer-reviewed theoretical foundations for each load model.
 *
 * Theoretical content has been reviewed against:
 *   IEEE 519-2022, IEC 61000 series, IEEE 1459-2010, Arrillaga & Watson (2003),
 *   Mohan et al. (2002), Baggini (2008), and CIGRE WG C4.109 (2014).
 */
public class HelpPanel {

    private final BorderPane root;

    private static final String[][] TOPICS = {
        {"⚖ Disclaimer Normativo",               loadResource("help_01_disclaimer.txt")},
        {"📊 Dashboard y KPIs",                  loadResource("help_02_dashboard.txt")},
        {"〜 Análisis Armónico",                  loadResource("help_03_harmonics.txt")},
        {"📋 Monitor Multi-Feeder",              loadResource("help_04_multifeeder.txt")},
        {"📈 Tendencias",                         loadResource("help_05_trends.txt")},
        {"🔔 Alarmas",                            loadResource("help_06_alarms.txt")},
        {"🔌 Feeders",                            loadResource("help_07_feeders.txt")},
        {"📏 Normas",                             loadResource("help_08_standards.txt")},
        {"⚡ Detección de Cargas",               loadResource("help_09_load_detection.txt")},
        {"📡 IEC 61850 / MMS",                   loadResource("help_10_iec61850.txt")},
        {"📐 Fundamentos: Armónicos y Fourier",  loadResource("help_11_fundamentals_harmonics.txt")},
        {"📐 Fundamentos: Fortescue / Secuencias", loadResource("help_12_fundamentals_fortescue.txt")},
        {"🔋 Modelo: Cripto-Miner / SMPS sin PFC", loadResource("help_13_model_crypto.txt")},
        {"🖥 Modelo: Datacenter / PFC Activo",   loadResource("help_14_model_datacenter.txt")},
        {"🔥 Modelo: Horno de Arco Eléctrico",  loadResource("help_15_model_arc_furnace.txt")},
        {"⚙ Modelo: Variador de Velocidad (VFD)", loadResource("help_16_model_vfd.txt")},
        {"🏭 Modelo: Industrial Lineal + Comercial Mixta", loadResource("help_17_model_industrial.txt")},
        {"📁 Visor COMTRADE — Uso",              loadResource("help_18_comtrade_usage.txt")},
        {"📁 Visor COMTRADE — Fundamentos",      loadResource("help_19_comtrade_fundamentals.txt")},
        {"📼 Registros COMTRADE — Generación",   loadResource("help_20_comtrade_generation.txt")},
        {"📖 Sobre HADES",                        loadResource("help_21_about_hades.txt")},
    };

    private static String loadResource(String name) {
        try (InputStream is = HelpPanel.class.getResourceAsStream("/com/harmonicmonitor/help/" + name)) {
            if (is == null) return "[Recurso no encontrado: " + name + "]";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[Error al cargar: " + name + "]";
        }
    }

    public HelpPanel() {
        root = buildUI();
    }

    public Node getNode() { return root; }

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");
        Label title = new Label("📖 AYUDA Y DOCUMENTACIÓN TÉCNICA");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        Label subtitle = new Label("— Fundamentos revisados contra IEEE 519-2022, IEC 61000, IEEE 1459-2010");
        subtitle.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + ";");
        header.getChildren().addAll(title, subtitle);
        pane.setTop(header);

        // Topics list (left)
        ListView<String> topicList = new ListView<>();
        topicList.setPrefWidth(270);
        topicList.setStyle(
            "-fx-background-color: " + Theme.BG + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 1 0 0;" +
            "-fx-font-size: 12px;");

        for (String[] t : TOPICS) topicList.getItems().add(t[0]);

        topicList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                boolean isTheory   = item.startsWith("📐") || item.startsWith("🔋")
                    || item.startsWith("🖥")  || item.startsWith("🔥")
                    || item.startsWith("⚙")  || item.startsWith("🏭");
                boolean isComtrade   = item.startsWith("📁") || item.startsWith("📼");
                boolean isDisclaimer = item.startsWith("⚖");
                if (isSelected()) {
                    setStyle("-fx-background-color: #2E5090; -fx-text-fill: #FFFFFF;");
                } else if (isDisclaimer) {
                    setStyle("-fx-background-color: #FFF8E0; -fx-text-fill: #7A4000;");
                } else if (isComtrade) {
                    setStyle("-fx-background-color: #E8F5EC; -fx-text-fill: #107C10;");
                } else if (isTheory) {
                    setStyle("-fx-background-color: #E5F0FA; -fx-text-fill: #0078D4;");
                } else {
                    setStyle("-fx-background-color: transparent; -fx-text-fill: " + Theme.TEXT + ";");
                }
            }
        });

        // Content area (right) — WebView for rich HTML rendering
        WebView contentArea = new WebView();

        topicList.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            int idx = n.intValue();
            if (idx >= 0 && idx < TOPICS.length) {
                contentArea.getEngine().loadContent(toHtml(TOPICS[idx][1]));
                topicList.refresh();
            }
        });

        topicList.getSelectionModel().select(0);
        contentArea.getEngine().loadContent(toHtml(TOPICS[0][1]));

        SplitPane split = new SplitPane(topicList, contentArea);
        split.setDividerPositions(0.26);
        split.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setCenter(split);
        return pane;
    }

    // ── HTML rendering helpers ────────────────────────────────────────────────

    private static String toHtml(String raw) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>");
        sb.append("body{font-family:'Segoe UI',system-ui,-apple-system,Arial,sans-serif;");
        sb.append("font-size:13px;color:#1E1E1E;padding:16px 24px 32px;line-height:1.7;");
        sb.append("background:#FFFFFF;margin:0;}");
        sb.append(".f{font-size:15px;font-weight:bold;color:#003A8C;");
        sb.append("font-family:'Cambria Math','Cambria','Georgia',serif;");
        sb.append("background:#EBF3FF;border-left:4px solid #0078D4;");
        sb.append("padding:8px 16px;margin:6px 0 6px 8px;display:block;border-radius:0 4px 4px 0;}");
        sb.append(".h1{font-size:15px;font-weight:bold;color:#003A8C;");
        sb.append("border-bottom:2px solid #0078D4;padding-bottom:4px;margin:22px 0 8px;}");
        sb.append(".h2{font-size:13px;font-weight:bold;color:#333;margin:14px 0 4px;}");
        sb.append("hr{border:none;border-top:1px solid #D0D8E8;margin:10px 0;}");
        sb.append(".w{background:#FFF7D6;border-left:3px solid #F5A623;");
        sb.append("padding:5px 12px;margin:5px 0;border-radius:0 4px 4px 0;}");
        sb.append("p{margin:2px 0;}");
        sb.append("</style></head><body>");
        for (String line : raw.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("━") || t.startsWith("─────")) {
                sb.append("<hr>");
            } else if (t.isEmpty()) {
                sb.append("<p style='margin:4px 0'>&nbsp;</p>");
            } else if (isFormula(t)) {
                sb.append("<div class='f'>").append(escHtml(t)).append("</div>");
            } else if (isSectionHdr(t)) {
                sb.append("<div class='h1'>").append(escHtml(t)).append("</div>");
            } else if (isSubHdr(t)) {
                sb.append("<div class='h2'>").append(escHtml(t)).append("</div>");
            } else {
                int lead = line.length() - line.stripLeading().length();
                if (t.startsWith("⚠")) {
                    sb.append("<div class='w'>").append(escHtml(t)).append("</div>");
                } else {
                    String ml = lead > 4 ? " style='margin-left:" + Math.min(lead * 5, 60) + "px'" : "";
                    sb.append("<p").append(ml).append(">").append(escHtml(t)).append("</p>");
                }
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean isFormula(String t) {
        if (!t.contains("=") || t.length() < 5) return false;
        boolean hasMath = t.contains("√") || t.contains("²") || t.contains("³") ||
            t.contains("×") || t.contains("·") || t.contains("ω") ||
            t.contains("φ") || t.contains("π") || t.contains("Σ") ||
            t.matches(".*[₀₁₂₃₄₅₆₇₈₉ₐᵢₗₙ].*");
        return hasMath && t.matches("[A-Za-zÀ-ÿ₀₁₂₃₄₅₆₇₈₉ᵢₐ_\\(][^=]*=.*");
    }

    private static boolean isSectionHdr(String t) {
        if (t.length() < 4 || t.contains("=") || t.startsWith("•")) return false;
        long up = t.chars().filter(Character::isUpperCase).count();
        long lt = t.chars().filter(Character::isLetter).count();
        return lt > 4 && (double) up / lt > 0.65;
    }

    private static boolean isSubHdr(String t) {
        return t.matches("[0-9]+\\.\\s.*") || t.matches("[A-Z]\\.\\s.*[A-Z].*");
    }
}
