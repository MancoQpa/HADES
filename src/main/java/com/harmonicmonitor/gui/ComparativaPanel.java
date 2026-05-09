package com.harmonicmonitor.gui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Pestaña "¿Por qué HADES?" — Análisis técnico comparativo
 * frente a categorías de herramientas de análisis de red y calidad de energía.
 *
 * HTML content loaded from resource: comparativa.html (refactor F18-001).
 */
public class ComparativaPanel {

    private final StackPane root;

    public ComparativaPanel() {
        root = new StackPane();
        root.setStyle("-fx-background-color: #FFFFFF;");

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().loadContent(loadHtml());

        root.getChildren().add(webView);
    }

    public Node getNode() { return root; }

    private static String loadHtml() {
        try (InputStream is = ComparativaPanel.class
                .getResourceAsStream("/com/harmonicmonitor/comparativa.html")) {
            if (is == null) return "<html><body>Error: comparativa.html not found</body></html>";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<html><body>Error loading content: " + e.getMessage() + "</body></html>";
        }
    }
}
