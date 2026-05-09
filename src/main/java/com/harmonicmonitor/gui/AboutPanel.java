package com.harmonicmonitor.gui;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/**
 * AboutPanel — Application information, author, and technology credits.
 *
 * UI construction delegated to {@link AboutPanelBuilder} (refactor F17-001).
 */
public class AboutPanel {

    private final ScrollPane root;

    public AboutPanel() { root = AboutPanelBuilder.build(); }

    public Node getNode() { return root; }
}
