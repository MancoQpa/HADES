package com.harmonicmonitor.gui;

import com.beanit.iec61850bean.*;
import com.harmonicmonitor.comm.DiscoveryResult;
import com.harmonicmonitor.comm.DiscoveryResult.FoundNode;
import com.harmonicmonitor.comm.IEDModelDiscovery;
import com.harmonicmonitor.model.FeederConfig;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Diálogo modal que conecta al IED, descubre sus nodos lógicos y
 * permite al usuario confirmar la configuración sugerida.
 *
 * Flujo:
 *  1. Se instancia con la FeederConfig actual (host/port/iedName).
 *  2. Al hacer clic en "Descubrir", se conecta en background via IEC 61850.
 *  3. Muestra la lista de nodos encontrados y el informe textual.
 *  4. El usuario puede aceptar la configuración sugerida o cancelar.
 *  5. Si acepta, getAcceptedConfig() retorna la FeederConfig actualizada.
 */
public class DiscoveryPanel {

    private static final int CONNECT_TIMEOUT_MS = 20_000;

    private final Stage stage;
    private final FeederConfig baseConfig;
    private FeederConfig acceptedConfig = null;

    // Controles UI
    private Label lblStatus;
    private ProgressIndicator progress;
    private TextArea taReport;
    private TreeView<String> nodeTree;
    private Button btnAccept;
    private Button btnDiscover;

    private DiscoveryResult lastResult;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IED-Discovery");
        t.setDaemon(true);
        return t;
    });

    public DiscoveryPanel(Window owner, FeederConfig baseConfig) {
        this.baseConfig = baseConfig;
        stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setTitle("🔍 Descubrimiento de Nodos IEC 61850 — " +
            baseConfig.getIedHost() + ":" + baseConfig.getIedPort());
        stage.setScene(buildScene());
        stage.setOnHidden(e -> executor.shutdownNow());
    }

    /** Muestra el diálogo (bloqueante hasta que el usuario cierre). */
    public void showAndWait() {
        stage.showAndWait();
    }

    /**
     * Retorna la FeederConfig aceptada por el usuario,
     * o null si canceló.
     */
    public FeederConfig getAcceptedConfig() {
        return acceptedConfig;
    }

    // ── Construcción de la escena ──────────────────────────────────────────

    private Scene buildScene() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: " + Theme.BG + ";");

        // Header
        root.getChildren().add(buildHeader());

        // Body: izquierda=árbol, derecha=informe
        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color: " + Theme.BG + ";");
        split.setDividerPositions(0.40);
        VBox.setVgrow(split, Priority.ALWAYS);

        split.getItems().addAll(buildTreePane(), buildReportPane());
        root.getChildren().add(split);

        // Footer
        root.getChildren().add(buildFooter());

        Scene scene = new Scene(root, 820, 560);
        scene.setFill(javafx.scene.paint.Color.web("#F0F0F0"));
        return scene;
    }

    private HBox buildHeader() {
        HBox hbox = new HBox(12);
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setPadding(new Insets(12, 16, 12, 16));
        hbox.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER + "; " +
            "-fx-border-width: 0 0 1 0;");

        Label title = new Label("Descubrimiento automático de Logical Nodes IEC 61850");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Label sub = new Label("Host: " + baseConfig.getIedHost() + ":" + baseConfig.getIedPort());
        sub.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        progress = new ProgressIndicator(-1);
        progress.setMaxSize(22, 22);
        progress.setVisible(false);

        lblStatus = new Label("Listo. Presione 'Descubrir' para iniciar.");
        lblStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");

        hbox.getChildren().addAll(title, sub, spacer, progress, lblStatus);
        return hbox;
    }

    private VBox buildTreePane() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: " + Theme.CARD + ";");

        Label lbl = new Label("NODOS ENCONTRADOS");
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        nodeTree = new TreeView<>();
        nodeTree.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + ";");
        nodeTree.setShowRoot(true);
        VBox.setVgrow(nodeTree, Priority.ALWAYS);

        TreeItem<String> placeholder = new TreeItem<>("(sin datos)");
        nodeTree.setRoot(placeholder);

        box.getChildren().addAll(lbl, nodeTree);
        return box;
    }

    private VBox buildReportPane() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: " + Theme.BG + ";");

        Label lbl = new Label("INFORME DE DESCUBRIMIENTO");
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        taReport = new TextArea();
        taReport.setEditable(false);
        taReport.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-text-fill: " + Theme.TEXT + "; " +
            "-fx-font-family: monospace; -fx-font-size: 11px; " +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1;");
        VBox.setVgrow(taReport, Priority.ALWAYS);
        taReport.setText("Esperando descubrimiento...");

        box.getChildren().addAll(lbl, taReport);
        return box;
    }

    private HBox buildFooter() {
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.CENTER_RIGHT);
        hbox.setPadding(new Insets(10, 16, 10, 16));
        hbox.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-border-color: " + Theme.BORDER + "; " +
            "-fx-border-width: 1 0 0 0;");

        btnDiscover = new Button("🔍  Descubrir IED");
        btnDiscover.setStyle("-fx-background-color: #0078D4; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-border-radius: 4; -fx-background-radius: 4; " +
            "-fx-padding: 7 16 7 16; -fx-cursor: hand;");
        btnDiscover.setOnAction(e -> startDiscovery());

        btnAccept = new Button("✓  Aplicar Configuración");
        btnAccept.setStyle("-fx-background-color: #28A745; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-border-radius: 4; -fx-background-radius: 4; " +
            "-fx-padding: 7 16 7 16; -fx-cursor: hand;");
        btnAccept.setDisable(true);
        btnAccept.setOnAction(e -> acceptConfig());

        Button btnCancel = new Button("Cancelar");
        btnCancel.setStyle("-fx-background-color: " + Theme.BG + "; -fx-text-fill: " + Theme.TEXT + "; " +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1; " +
            "-fx-border-radius: 4; -fx-background-radius: 4; " +
            "-fx-padding: 7 14 7 14; -fx-cursor: hand;");
        btnCancel.setOnAction(e -> stage.close());

        hbox.getChildren().addAll(btnDiscover, btnAccept, btnCancel);
        return hbox;
    }

    // ── Lógica de descubrimiento ──────────────────────────────────────────────

    private void startDiscovery() {
        btnDiscover.setDisable(true);
        btnAccept.setDisable(true);
        progress.setVisible(true);
        setStatus("Conectando a " + baseConfig.getIedHost() + ":" + baseConfig.getIedPort() + " ...");
        taReport.setText("Conectando...\n");
        TreeItem<String> loadingRoot = new TreeItem<>("Conectando...");
        nodeTree.setRoot(loadingRoot);

        executor.submit(() -> {
            try {
                // Fase 1: conectar
                ClientSap clientSap = new ClientSap();
                clientSap.setResponseTimeout(CONNECT_TIMEOUT_MS);

                Platform.runLater(() -> setStatus("Cargando modelo del IED..."));

                ClientAssociation association = clientSap.associate(
                    InetAddress.getByName(baseConfig.getIedHost()),
                    baseConfig.getIedPort(),
                    null,
                    null   // no listener (síncrono para discovery)
                );

                // Fase 2: obtener el modelo
                Platform.runLater(() -> setStatus("Descubriendo logical nodes..."));
                ServerModel serverModel = association.retrieveModel();

                // Fase 3: discovery (pasa la association para detectar escala de potencia)
                Platform.runLater(() -> setStatus("Detectando escalas de potencia..."));
                DiscoveryResult result = IEDModelDiscovery.discover(serverModel, baseConfig, association);
                lastResult = result;

                // Fase 4: desconectar (solo necesitábamos el modelo y las escalas)
                try { association.disconnect(); } catch (Exception ignored) {}

                // Fase 5: actualizar UI
                Platform.runLater(() -> {
                    populateTree(result);
                    taReport.setText(result.getReport());
                    progress.setVisible(false);
                    btnDiscover.setDisable(false);
                    if (result.hasAnyMonitoringNode()) {
                        btnAccept.setDisable(false);
                        setStatus("✓ Descubrimiento completo. " + countNodes(result) +
                            " nodos relevantes encontrados.");
                    } else {
                        setStatus("⚠ No se encontraron nodos de monitoreo (MMXU/MMXN).");
                    }
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                    setStatus("Error: " + msg);
                    taReport.setText("Error durante el descubrimiento:\n" + msg + "\n\n" +
                        "Verifica:\n" +
                        " - Host/puerto correcto\n" +
                        " - IED encendido y accesible en la red\n" +
                        " - Sin firewall bloqueando puerto MMS (102)");
                    TreeItem<String> errRoot = new TreeItem<>("Error de conexión");
                    nodeTree.setRoot(errRoot);
                    progress.setVisible(false);
                    btnDiscover.setDisable(false);
                });
            }
        });
    }

    private void populateTree(DiscoveryResult result) {
        TreeItem<String> root = new TreeItem<>(
            "IED: " + (result.getIedName().isEmpty() ? baseConfig.getIedHost() : result.getIedName())
        );
        root.setExpanded(true);

        addLnGroup(root, "MMXU (Medición MT)", result.getMmxuNodes(), result.getBestMMXU());
        addLnGroup(root, "MHAI (Armónicos)", result.getMhaiNodes(), result.getBestMHAI());
        addLnGroup(root, "MSQI (Seq. Simétricas)", result.getMsqiNodes(), result.getBestMSQI());
        addLnGroup(root, "MMTR (Energía)", result.getMmtrNodes(), result.getBestMMTR());
        addLnGroup(root, "MSTA (Demanda)", result.getMstaNodes(), result.getBestMSTA());

        nodeTree.setRoot(root);
    }

    private void addLnGroup(TreeItem<String> parent, String groupLabel,
                             List<FoundNode> nodes, FoundNode best) {
        if (nodes.isEmpty()) return;

        TreeItem<String> group = new TreeItem<>(
            groupLabel + " (" + nodes.size() + ")"
        );
        group.setExpanded(true);

        for (FoundNode node : nodes) {
            boolean isBest = node == best;
            String label = (isBest ? "★ " : "   ") + node.lnName + " @ " + node.ldInst;
            TreeItem<String> item = new TreeItem<>(label);

            // DOs como hijos
            for (String doName : node.availableDOs) {
                item.getChildren().add(new TreeItem<>("  ." + doName));
            }
            group.getChildren().add(item);
        }
        parent.getChildren().add(group);
    }

    private void acceptConfig() {
        if (lastResult == null || lastResult.getSuggestedConfig() == null) return;
        acceptedConfig = lastResult.getSuggestedConfig();
        // Preservar campos de identificación del formulario original
        acceptedConfig.setFeederId(baseConfig.getFeederId());
        acceptedConfig.setFeederName(baseConfig.getFeederName());
        acceptedConfig.setDescription(baseConfig.getDescription());
        stage.close();
    }

    private void setStatus(String msg) {
        lblStatus.setText(msg);
    }

    private int countNodes(DiscoveryResult r) {
        int count = 0;
        if (r.getBestMMXU() != null) count++;
        if (r.getBestMHAI() != null) count++;
        if (r.getBestMSQI() != null) count++;
        if (r.getBestMMTR() != null) count++;
        if (r.getBestMSTA() != null) count++;
        return count;
    }
}
