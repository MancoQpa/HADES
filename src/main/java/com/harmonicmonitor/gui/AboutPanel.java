package com.harmonicmonitor.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * AboutPanel — Application information, author, and technology credits.
 * Features a hand-drawn Paraguay flag and full project description.
 */
public class AboutPanel {

    private final ScrollPane root;

    public AboutPanel() {
        root = buildUI();
    }

    public Node getNode() { return root; }

    // ── Paraguay flag — drawn on Canvas ───────────────────────────────────────

    private Canvas buildFlag(double W, double H) {
        Canvas c = new Canvas(W, H);
        GraphicsContext gc = c.getGraphicsContext2D();

        double stripeH = H / 3.0;

        // Stripe 1 — Rojo
        gc.setFill(Color.web("#CC0001"));
        gc.fillRect(0, 0, W, stripeH);

        // Stripe 2 — Blanco
        gc.setFill(Color.WHITE);
        gc.fillRect(0, stripeH, W, stripeH);

        // Stripe 3 — Azul
        gc.setFill(Color.web("#0038A8"));
        gc.fillRect(0, stripeH * 2, W, stripeH);

        // Thin border around the whole flag
        gc.setStroke(Color.web("#CCCCCC"));
        gc.setLineWidth(1.0);
        gc.strokeRect(0.5, 0.5, W - 1, H - 1);

        // Central emblem on the white stripe — simplified Estrella de Mayo
        double cx = W / 2.0;
        double cy = stripeH + stripeH / 2.0;
        double R  = stripeH * 0.38;

        // Outer gold ring
        gc.setStroke(Color.web("#C8A000"));
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - R, cy - R, R * 2, R * 2);

        // Inner olive/palm wreath suggestion — two arcs
        gc.setStroke(Color.web("#3A7A2A"));
        gc.setLineWidth(1.2);
        gc.strokeArc(cx - R * 0.78, cy - R * 0.78, R * 1.56, R * 1.56, 20, 140, javafx.scene.shape.ArcType.OPEN);
        gc.strokeArc(cx - R * 0.78, cy - R * 0.78, R * 1.56, R * 1.56, 200, 140, javafx.scene.shape.ArcType.OPEN);

        // 5-pointed star in the center
        gc.setFill(Color.web("#C8A000"));
        gc.setStroke(Color.web("#A07800"));
        gc.setLineWidth(0.8);
        double[] sx = new double[5];
        double[] sy = new double[5];
        double rOuter = R * 0.46, rInner = R * 0.19;
        for (int i = 0; i < 5; i++) {
            double ang = Math.toRadians(-90 + i * 72);
            sx[i] = cx + rOuter * Math.cos(ang);
            sy[i] = cy + rOuter * Math.sin(ang);
        }
        double[] px = new double[10], py = new double[10];
        for (int i = 0; i < 5; i++) {
            double aOut = Math.toRadians(-90 + i * 72);
            double aIn  = Math.toRadians(-90 + i * 72 + 36);
            px[i * 2]     = cx + rOuter * Math.cos(aOut);
            py[i * 2]     = cy + rOuter * Math.sin(aOut);
            px[i * 2 + 1] = cx + rInner * Math.cos(aIn);
            py[i * 2 + 1] = cy + rInner * Math.sin(aIn);
        }
        gc.fillPolygon(px, py, 10);
        gc.strokePolygon(px, py, 10);

        return c;
    }

    // ── Full UI ───────────────────────────────────────────────────────────────

    private ScrollPane buildUI() {
        // Outer scroll container
        VBox page = new VBox(0);
        page.setAlignment(Pos.TOP_CENTER);
        page.setStyle("-fx-background-color: " + Theme.BG + ";");

        // ── Hero banner ───────────────────────────────────────────────────────
        VBox hero = new VBox(10);
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(new Insets(36, 40, 28, 40));
        hero.setStyle(
            "-fx-background-color: " + Theme.HEADER + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        // App icon + title row
        Label iconLbl = new Label("⚡");
        iconLbl.setStyle("-fx-font-size: 56px;");

        Label titleLbl = new Label("HADES");
        titleLbl.setStyle(
            "-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-effect: dropshadow(gaussian, #0078D488, 18, 0.3, 0, 0);");

        Label projectBadge = new Label("Proyecto ANDE-SIGFE");
        projectBadge.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #CA5010;" +
            "-fx-background-color: #FFF0E0; -fx-background-radius: 4;" +
            "-fx-border-color: #CA501040; -fx-border-width: 1; -fx-border-radius: 4;" +
            "-fx-padding: 3 14 3 14; -fx-letter-spacing: 1;");

        Label sigfeLbl = new Label("Sistema Inteligente de Gestión de Firmas Energéticas");
        sigfeLbl.setStyle(
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Label subtitleLbl = new Label(
            "Monitoreo en tiempo real de feeders de 23 kV mediante análisis de firmas de energía\n" +
            "y comunicaciones IEC 61850 para detección de cargas electrointensivas.");
        subtitleLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-text-alignment: center;");
        subtitleLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label versionLbl = new Label("v 1.0  —  2026");
        versionLbl.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: #0078D4; -fx-font-weight: bold;" +
            "-fx-background-color: " + Theme.SEL + "; -fx-background-radius: 20;" +
            "-fx-padding: 3 14 3 14;");

        hero.getChildren().addAll(iconLbl, titleLbl, projectBadge, sigfeLbl, subtitleLbl, versionLbl);
        page.getChildren().add(hero);

        // ── Two-column main area ──────────────────────────────────────────────
        HBox cols = new HBox(0);
        cols.setAlignment(Pos.TOP_CENTER);
        cols.setMaxWidth(880);

        // LEFT COLUMN — app description
        VBox leftCol = new VBox(16);
        leftCol.setPadding(new Insets(24, 20, 24, 32));
        leftCol.setPrefWidth(440);

        // Description card
        VBox descCard = buildCard();
        descCard.getChildren().addAll(
            sectionTitle("📋 DESCRIPCIÓN"),
            sep(),
            bodyText(
                "HADES (Harmonic Analysis for Detection of Electronic Signatures) es el software del proyecto ANDE-SIGFE " +
                "(Sistema Inteligente de Gestión de Firmas Energéticas), " +
                "una plataforma de escritorio para el monitoreo en tiempo real " +
                "de la calidad de energía en alimentadores de media tensión 23 kV, " +
                "desarrollada sobre el estándar IEC 61850 / MMS.\n\n" +
                "Su objetivo es detectar y cuantificar la presencia de cargas " +
                "electrointensivas no lineales — minería de criptomonedas, " +
                "datacenters, hornos de arco eléctrico y variadores de frecuencia — " +
                "mediante el análisis de firmas de energía: espectro armónico, " +
                "factor de potencia, coeficiente de variación y patrones temporales " +
                "de consumo."),
            sep(),
            sectionTitle("⚡ FUNCIONALIDADES"),
            sep(),
            featureRow("📡", "Conexión IEC 61850 MMS al ION 7400 y otros IEDs"),
            featureRow("〜", "Análisis armónico H1-H50 con FFT en tiempo real"),
            featureRow("🔍", "Detección automática del tipo de carga (6 categorías)"),
            featureRow("🔔", "Motor de alarmas de 4 niveles (WARNING/PQ_RISK/CRITICAL/DETECTION)"),
            featureRow("📈", "Tendencias históricas y exportación CSV"),
            featureRow("📁", "Visor COMTRADE — FFT, fasores, Fortescue, potencia"),
            featureRow("⚙", "Simulador con 6 perfiles de carga sin IED real"),
            featureRow("📏", "Evaluación normativa IEC 61000 / IEEE 519 / EN 50160"),
            featureRow("🔁", "Reconexión automática con backoff exponencial")
        );

        // Capabilities card
        VBox capCard = buildCard();
        capCard.getChildren().addAll(
            sectionTitle("🏗 ARQUITECTURA"),
            sep(),
            bodyText(
                "La aplicación sigue un diseño en capas:\n\n" +
                "  Capa de comunicación  — IEC61850Communicator\n" +
                "  Capa de análisis      — HarmonicAnalyzer, ElectronicLoadDetector,\n" +
                "                          ResonanceAnalyzer, LoadStabilityAnalyzer\n" +
                "  Capa de alarmas       — AlarmEngine (4 niveles, umbralización)\n" +
                "  Capa de persistencia  — DataStorage (SQLite + CSV)\n" +
                "  Capa de presentación  — JavaFX 17 (panels MVC ligero)\n\n" +
                "El módulo SimulatedPoller permite desarrollo y demostración\n" +
                "sin acceso a hardware real, generando señales sintéticas\n" +
                "basadas en modelos físicos de cada tipo de carga.")
        );

        leftCol.getChildren().addAll(descCard, capCard);

        // RIGHT COLUMN — author + flag + tech
        VBox rightCol = new VBox(16);
        rightCol.setPadding(new Insets(24, 32, 24, 20));
        rightCol.setPrefWidth(440);

        // Author card — with Paraguay flag
        VBox authorCard = buildCard();
        authorCard.setAlignment(Pos.CENTER);

        // Flag — centered, reasonable size
        HBox flagRow = new HBox(20);
        flagRow.setAlignment(Pos.CENTER);
        Canvas flag = buildFlag(108, 72);
        flag.setStyle("-fx-effect: dropshadow(gaussian, #00000088, 12, 0.4, 0, 3);");

        VBox flagInfo = new VBox(4);
        flagInfo.setAlignment(Pos.CENTER_LEFT);
        Label flagTitle = new Label("🇵🇾  República del Paraguay");
        flagTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        Label flagSub = new Label("América del Sur  •  Asunción");
        flagSub.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        Label flagStripes = new Label("Rojo · Blanco · Azul  —  desde 1842");
        flagStripes.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-style: italic;");
        flagInfo.getChildren().addAll(flagTitle, flagSub, flagStripes);
        flagRow.getChildren().addAll(flag, flagInfo);

        // Team cards per developer
        VBox dev1 = devRow("Emilio Medina",      "Arquitectura, backend IEC 61850, análisis armónico");
        VBox dev2 = devRow("Ubaldo Fernández",   "Procesamiento de señales y detección de cargas");
        VBox dev3 = devRow("Diego Rojas",        "Interfaz gráfica y visualización de datos");
        VBox dev4 = devRow("Enrique Paiva",      "Integración de normas y validación de modelos");
        VBox dev5 = devRow("Sergio Domínguez",   "Gestión de Proyecto");

        VBox teamBox = new VBox(6);
        teamBox.getChildren().addAll(dev1, dev2, dev3, dev4, dev5);

        authorCard.getChildren().addAll(
            sectionTitle("👥 EQUIPO DE DESARROLLO"),
            sep(),
            flagRow,
            sep(),
            teamBox,
            sep(),
            infoRow("Proyecto",       "ANDE-SIGFE"),
            infoRow("País",           "Paraguay  🇵🇾"),
            infoRow("Año",            "2026"),
            infoRow("Sector",         "Distribución eléctrica MT 23 kV — ANDE"),
            infoRow("Protocolo",      "IEC 61850 / MMS / ACSE"),
            infoRow("Versión",        "v1.0")
        );

        // Standards card
        VBox normsCard = buildCard();
        normsCard.getChildren().addAll(
            sectionTitle("📏 NORMAS DE REFERENCIA"),
            sep(),
            normRow("IEC 61850",      "Comunicación en subestaciones (MMS/GOOSE/SV)"),
            normRow("IEC 61000-3-6",  "Emisiones armónicas en redes MT/AT"),
            normRow("IEC 61000-3-7",  "Fluctuaciones de tensión / Flicker"),
            normRow("IEC 61000-4-7",  "Técnicas de medición de armónicos"),
            normRow("IEC 61000-4-30", "Métodos de medición de calidad de energía"),
            normRow("IEEE 519-2022",  "Harmonic control in electric power systems"),
            normRow("EN 50160:2010",  "Características de tensión en redes públicas"),
            normRow("IEC 60255-24",   "COMTRADE — formato de registros de perturbaciones")
        );

        // Technology card
        VBox techCard = buildCard();
        techCard.getChildren().addAll(
            sectionTitle("🛠 TECNOLOGÍAS"),
            sep()
        );
        HBox row1 = new HBox(8);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.getChildren().addAll(
            badge("Java 17+", "#0078D4"),
            badge("JavaFX 17", "#0078D4"),
            badge("Maven 3.6+", "#0078D4")
        );
        HBox row2 = new HBox(8);
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.getChildren().addAll(
            badge("iec61850bean 1.9", "#4CAF50"),
            badge("asn1bean 1.13", "#4CAF50"),
            badge("SLF4J 2.0", "#4CAF50")
        );
        HBox row3 = new HBox(8);
        row3.setAlignment(Pos.CENTER_LEFT);
        row3.getChildren().addAll(
            badge("SQLite (JDBC)", "#CA5010"),
            badge("FFT Cooley-Tukey", "#CA5010"),
            badge("Fortescue", "#CA5010")
        );
        techCard.getChildren().addAll(row1, row2, row3);

        rightCol.getChildren().addAll(authorCard, normsCard, techCard);

        cols.getChildren().addAll(leftCol, rightCol);

        // Center wrapper
        HBox colsWrapper = new HBox();
        colsWrapper.setAlignment(Pos.CENTER);
        colsWrapper.getChildren().add(cols);
        page.getChildren().add(colsWrapper);

        // ── Footer ────────────────────────────────────────────────────────────
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(14, 32, 20, 32));
        footer.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1 0 0 0;");

        Label footerLbl = new Label(
            "Proyecto ANDE-SIGFE  •  " +
            "Emilio Medina · Ubaldo Fernández · Diego Rojas · Enrique Paiva · Sergio Domínguez  •  " +
            "Asunción, Paraguay  🇵🇾  •  2026");
        footerLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #444444; -fx-text-alignment: center;");
        footerLbl.setWrapText(true);
        footer.getChildren().add(footerLbl);
        page.getChildren().add(footer);

        // Scroll container
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + Theme.BG + "; -fx-background: " + Theme.BG + ";");
        return scroll;
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private VBox buildCard() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle(
            "-fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: #2A3850;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;");
        return card;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold;" +
            "-fx-text-fill: #0078D4; -fx-letter-spacing: 0.8;");
        return l;
    }

    private Label bodyText(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-line-spacing: 2;");
        return l;
    }

    private HBox featureRow(String icon, String text) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label ic = new Label(icon);
        ic.setStyle("-fx-font-size: 14px; -fx-min-width: 22;");
        Label tx = new Label(text);
        tx.setWrapText(true);
        tx.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + ";");
        row.getChildren().addAll(ic, tx);
        return row;
    }

    private VBox devRow(String name, String role) {
        VBox box = new VBox(2);
        box.setPadding(new Insets(6, 10, 6, 10));
        box.setStyle(
            "-fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: " + Theme.ACCENT + ";" +
            "-fx-border-width: 0 0 0 3;" +
            "-fx-border-radius: 0 4 4 0;" +
            "-fx-background-radius: 0 4 4 0;");
        Label nameLbl = new Label("👤  " + name);
        nameLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        Label roleLbl = new Label(role);
        roleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #444444; -fx-font-style: italic;");
        box.getChildren().addAll(nameLbl, roleLbl);
        return box;
    }

    private HBox infoRow(String key, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label k = new Label(key + ":");
        k.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-min-width: 108;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-weight: bold;");
        row.getChildren().addAll(k, v);
        return row;
    }

    private HBox normRow(String std, String desc) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        Label k = new Label(std);
        k.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;" +
            "-fx-min-width: 118; -fx-background-color: #E8F5EC;" +
            "-fx-background-radius: 3; -fx-padding: 2 6 2 6;");
        Label d = new Label(desc);
        d.setWrapText(true);
        d.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        row.getChildren().addAll(k, d);
        return row;
    }

    private Label badge(String text, String color) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-background-color: " + color + "18;" +
            "-fx-text-fill: " + color + ";" +
            "-fx-font-size: 11px;" +
            "-fx-background-radius: 4;" +
            "-fx-padding: 4 10 4 10;" +
            "-fx-border-color: " + color + "40;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 4;");
        return l;
    }

    private Separator sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: " + Theme.BORDER + ";");
        return s;
    }
}
