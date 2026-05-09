package com.harmonicmonitor.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.TextAlignment;

/**
 * Builds the entire UI of {@link AboutPanel}.
 *
 * All methods are static; this class has no mutable state.
 * Extracted from {@link AboutPanel} (refactor F17-001).
 */
final class AboutPanelBuilder {

    private AboutPanelBuilder() {}

    // ── Entry point ───────────────────────────────────────────────────────────

    static ScrollPane build() {
        VBox page = new VBox(0);
        page.setAlignment(Pos.TOP_CENTER);
        page.setStyle("-fx-background-color: " + Theme.BG + ";");

        page.getChildren().add(buildHero());

        // Two-column main area
        HBox cols = new HBox(0);
        cols.setAlignment(Pos.TOP_CENTER);
        cols.setMaxWidth(880);
        cols.getChildren().addAll(buildLeftCol(), buildRightCol());

        HBox colsWrapper = new HBox();
        colsWrapper.setAlignment(Pos.CENTER);
        colsWrapper.getChildren().add(cols);
        page.getChildren().add(colsWrapper);

        page.getChildren().add(buildFooter());

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + Theme.BG + "; -fx-background: " + Theme.BG + ";");
        return scroll;
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private static VBox buildHero() {
        VBox hero = new VBox(10);
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(new Insets(36, 40, 28, 40));
        hero.setStyle(
            "-fx-background-color: " + Theme.HEADER + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");

        Label iconLbl = new Label("\u26A1");
        iconLbl.setStyle("-fx-font-size: 56px;");

        Label titleLbl = new Label("HADES");
        titleLbl.setStyle(
            "-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";" +
            "-fx-effect: dropshadow(gaussian, #0078D488, 18, 0.3, 0, 0);");

        Label sigfeLbl = new Label("Harmonic Analysis for Detection of Electronic Signatures");
        sigfeLbl.setStyle(
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");

        Label subtitleLbl = new Label(
            "Monitoreo en tiempo real de feeders de 23 kV mediante an\u00E1lisis de firmas de energ\u00EDa\n" +
            "y comunicaciones IEC 61850 para detecci\u00F3n de cargas electrointensivas.");
        subtitleLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-text-alignment: center;");
        subtitleLbl.setTextAlignment(TextAlignment.CENTER);

        Label versionLbl = new Label("v 1.0  \u2014  2026");
        versionLbl.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: #0078D4; -fx-font-weight: bold;" +
            "-fx-background-color: " + Theme.SEL + "; -fx-background-radius: 20;" +
            "-fx-padding: 3 14 3 14;");

        hero.getChildren().addAll(iconLbl, titleLbl, sigfeLbl, subtitleLbl, versionLbl);
        return hero;
    }

    private static VBox buildLeftCol() {
        VBox col = new VBox(16);
        col.setPadding(new Insets(24, 20, 24, 32));
        col.setPrefWidth(440);

        VBox descCard = buildCard();
        descCard.getChildren().addAll(
            sectionTitle("\uD83D\uDCCB DESCRIPCI\u00D3N"),
            sep(),
            bodyText(
                "HADES (Harmonic Analysis for Detection of Electronic Signatures) es una " +
                "plataforma de escritorio para el monitoreo en tiempo real " +
                "de la calidad de energ\u00EDa en alimentadores de media tensi\u00F3n 23 kV, " +
                "desarrollada sobre el est\u00E1ndar IEC 61850 / MMS.\n\n" +
                "Su objetivo es detectar y cuantificar la presencia de cargas " +
                "electrointensivas no lineales \u2014 miner\u00EDa de criptomonedas, " +
                "datacenters, hornos de arco el\u00E9ctrico y variadores de frecuencia \u2014 " +
                "mediante el an\u00E1lisis de firmas de energ\u00EDa: espectro arm\u00F3nico, " +
                "factor de potencia, coeficiente de variaci\u00F3n y patrones temporales " +
                "de consumo."),
            sep(),
            sectionTitle("\u26A1 FUNCIONALIDADES"),
            sep(),
            featureRow("\uD83D\uDCE1", "Conexi\u00F3n IEC 61850 MMS al ION 7400 y otros IEDs"),
            featureRow("\u301C",       "An\u00E1lisis arm\u00F3nico H1-H50 con FFT en tiempo real"),
            featureRow("\uD83D\uDD0D", "Detecci\u00F3n autom\u00E1tica del tipo de carga (6 categor\u00EDas)"),
            featureRow("\uD83D\uDD14", "Motor de alarmas de 4 niveles (WARNING/PQ_RISK/CRITICAL/DETECTION)"),
            featureRow("\uD83D\uDCC8", "Tendencias hist\u00F3ricas y exportaci\u00F3n CSV"),
            featureRow("\uD83D\uDCC1", "Visor COMTRADE \u2014 FFT, fasores, Fortescue, potencia"),
            featureRow("\u2699",       "Simulador con 6 perfiles de carga sin IED real"),
            featureRow("\uD83D\uDCCF", "Evaluaci\u00F3n normativa IEC 61000 / IEEE 519 / EN 50160"),
            featureRow("\uD83D\uDD01", "Reconexi\u00F3n autom\u00E1tica con backoff exponencial")
        );

        VBox capCard = buildCard();
        capCard.getChildren().addAll(
            sectionTitle("\uD83C\uDFD7 ARQUITECTURA"),
            sep(),
            featureRow("\uD83D\uDCE1", "Comunicaci\u00F3n \u2014 IEC61850Communicator, MeasurementPoller"),
            featureRow("\u301C",       "An\u00E1lisis \u2014 HarmonicAnalyzer, ElectronicLoadDetector"),
            featureRow("\uD83D\uDD2C", "An\u00E1lisis \u2014 ResonanceAnalyzer, LoadStabilityAnalyzer"),
            featureRow("\uD83D\uDD14", "Alarmas \u2014 AlarmEngine (4 niveles, umbralización)"),
            featureRow("\uD83D\uDCBE", "Persistencia \u2014 DataStorage (SQLite + CSV)"),
            featureRow("\uD83D\uDDA5", "Presentaci\u00F3n \u2014 JavaFX 17 (panels MVC ligero)"),
            featureRow("\u2699",       "Simulaci\u00F3n \u2014 SimulatedPoller (8 perfiles sint\u00E9ticos)")
        );

        col.getChildren().addAll(descCard, capCard);
        return col;
    }

    private static VBox buildRightCol() {
        VBox col = new VBox(16);
        col.setPadding(new Insets(24, 32, 24, 20));
        col.setPrefWidth(440);

        // Author card with Paraguay flag
        VBox authorCard = buildCard();
        authorCard.setAlignment(Pos.CENTER);

        HBox flagRow = new HBox(20);
        flagRow.setAlignment(Pos.CENTER);
        Canvas flag = buildFlag(108, 72);
        flag.setStyle("-fx-effect: dropshadow(gaussian, #00000088, 12, 0.4, 0, 3);");

        VBox flagInfo = new VBox(4);
        flagInfo.setAlignment(Pos.CENTER_LEFT);
        Label flagTitle = new Label("\uD83C\uDDF5\uD83C\uDDFE  Rep\u00FAblica del Paraguay");
        flagTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        Label flagSub = new Label("Am\u00E9rica del Sur  \u2022  Asunci\u00F3n");
        flagSub.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        Label flagStripes = new Label("Rojo \u00B7 Blanco \u00B7 Azul  \u2014  desde 1842");
        flagStripes.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-style: italic;");
        flagInfo.getChildren().addAll(flagTitle, flagSub, flagStripes);
        flagRow.getChildren().addAll(flag, flagInfo);

        VBox teamBox = new VBox(6);
        teamBox.getChildren().addAll(
            devRow("Emilio Medina",    "Arquitectura, backend IEC 61850, an\u00E1lisis arm\u00F3nico"),
            devRow("Diego Rojas",      "Interfaz gr\u00E1fica y visualizaci\u00F3n de datos"),
            devRow("Enrique Paiva",    "Integraci\u00F3n de normas y validaci\u00F3n de modelos"),
            devRow("Sergio Dom\u00EDnguez", "Gesti\u00F3n de Proyecto")
        );

        authorCard.getChildren().addAll(
            sectionTitle("\uD83D\uDC65 EQUIPO DE DESARROLLO"),
            sep(),
            flagRow,
            sep(),
            teamBox,
            sep(),
            infoRow("Pa\u00EDs",      "Paraguay  \uD83C\uDDF5\uD83C\uDDFE"),
            infoRow("A\u00F1o",       "2026"),
            infoRow("Sector",         "Distribuci\u00F3n el\u00E9ctrica MT 23 kV \u2014 ANDE"),
            infoRow("Protocolo",      "IEC 61850 / MMS / ACSE"),
            infoRow("Versi\u00F3n",   "v1.0")
        );

        // Standards card
        VBox normsCard = buildCard();
        normsCard.getChildren().addAll(
            sectionTitle("\uD83D\uDCCF NORMAS DE REFERENCIA"),
            sep(),
            normRow("IEC 61850",      "Comunicaci\u00F3n en subestaciones (MMS/GOOSE/SV)"),
            normRow("IEC 61000-3-6",  "Emisiones arm\u00F3nicas en redes MT/AT"),
            normRow("IEC 61000-3-7",  "Fluctuaciones de tensi\u00F3n / Flicker"),
            normRow("IEC 61000-4-7",  "T\u00E9cnicas de medici\u00F3n de arm\u00F3nicos"),
            normRow("IEC 61000-4-30", "M\u00E9todos de medici\u00F3n de calidad de energ\u00EDa"),
            normRow("IEEE 519-2022",  "Harmonic control in electric power systems"),
            normRow("EN 50160:2010",  "Caracter\u00EDsticas de tensi\u00F3n en redes p\u00FAblicas"),
            normRow("IEC 60255-24",   "COMTRADE \u2014 formato de registros de perturbaciones")
        );

        // Technology card
        VBox techCard = buildCard();
        techCard.getChildren().addAll(sectionTitle("\uD83D\uDEE0 TECNOLOG\u00CDAS"), sep());
        HBox r1 = new HBox(8);  r1.setAlignment(Pos.CENTER_LEFT);
        r1.getChildren().addAll(badge("Java 17+", "#0078D4"), badge("JavaFX 17", "#0078D4"), badge("Maven 3.6+", "#0078D4"));
        HBox r2 = new HBox(8);  r2.setAlignment(Pos.CENTER_LEFT);
        r2.getChildren().addAll(badge("iec61850bean 1.9", "#4CAF50"), badge("asn1bean 1.13", "#4CAF50"), badge("SLF4J 2.0", "#4CAF50"));
        HBox r3 = new HBox(8);  r3.setAlignment(Pos.CENTER_LEFT);
        r3.getChildren().addAll(badge("SQLite (JDBC)", "#CA5010"), badge("FFT Cooley-Tukey", "#CA5010"), badge("Fortescue", "#CA5010"));
        techCard.getChildren().addAll(r1, r2, r3);

        col.getChildren().addAll(authorCard, normsCard, techCard);
        return col;
    }

    private static HBox buildFooter() {
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(14, 32, 20, 32));
        footer.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1 0 0 0;");
        Label lbl = new Label(
            "HADES v1.0  \u2022  " +
            "Emilio Medina \u00B7 Diego Rojas \u00B7 Enrique Paiva \u00B7 Sergio Dom\u00EDnguez  \u2022  " +
            "Asunci\u00F3n, Paraguay  \uD83C\uDDF5\uD83C\uDDFE  \u2022  2026");
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #444444; -fx-text-alignment: center;");
        lbl.setWrapText(true);
        footer.getChildren().add(lbl);
        return footer;
    }

    // ── Paraguay flag (Canvas drawing) ────────────────────────────────────────

    private static Canvas buildFlag(double W, double H) {
        Canvas c = new Canvas(W, H);
        GraphicsContext gc = c.getGraphicsContext2D();
        double stripeH = H / 3.0;

        gc.setFill(Color.web("#CC0001")); gc.fillRect(0, 0, W, stripeH);
        gc.setFill(Color.WHITE);          gc.fillRect(0, stripeH, W, stripeH);
        gc.setFill(Color.web("#0038A8")); gc.fillRect(0, stripeH * 2, W, stripeH);

        gc.setStroke(Color.web("#CCCCCC")); gc.setLineWidth(1.0);
        gc.strokeRect(0.5, 0.5, W - 1, H - 1);

        double cx = W / 2.0, cy = stripeH + stripeH / 2.0, R = stripeH * 0.38;

        gc.setStroke(Color.web("#C8A000")); gc.setLineWidth(1.5);
        gc.strokeOval(cx - R, cy - R, R * 2, R * 2);

        gc.setStroke(Color.web("#3A7A2A")); gc.setLineWidth(1.2);
        gc.strokeArc(cx - R * 0.78, cy - R * 0.78, R * 1.56, R * 1.56, 20,  140, ArcType.OPEN);
        gc.strokeArc(cx - R * 0.78, cy - R * 0.78, R * 1.56, R * 1.56, 200, 140, ArcType.OPEN);

        gc.setFill(Color.web("#C8A000")); gc.setStroke(Color.web("#A07800")); gc.setLineWidth(0.8);
        double rOuter = R * 0.46, rInner = R * 0.19;
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

    // ── Factory helpers ───────────────────────────────────────────────────────

    private static VBox buildCard() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle(
            "-fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: #2A3850; -fx-border-width: 1;" +
            "-fx-border-radius: 8; -fx-background-radius: 8;");
        return card;
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0078D4; -fx-letter-spacing: 0.8;");
        return l;
    }

    private static Label bodyText(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-line-spacing: 2;");
        return l;
    }

    private static HBox featureRow(String icon, String text) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label ic = new Label(icon); ic.setStyle("-fx-font-size: 14px; -fx-min-width: 22;");
        Label tx = new Label(text); tx.setWrapText(true);
        tx.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + ";");
        row.getChildren().addAll(ic, tx);
        return row;
    }

    private static VBox devRow(String name, String role) {
        VBox box = new VBox(2);
        box.setPadding(new Insets(6, 10, 6, 10));
        box.setStyle(
            "-fx-background-color: " + Theme.CARD + ";" +
            "-fx-border-color: " + Theme.ACCENT + "; -fx-border-width: 0 0 0 3;" +
            "-fx-border-radius: 0 4 4 0; -fx-background-radius: 0 4 4 0;");
        Label nameLbl = new Label("\uD83D\uDC64  " + name);
        nameLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        Label roleLbl = new Label(role);
        roleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #444444; -fx-font-style: italic;");
        box.getChildren().addAll(nameLbl, roleLbl);
        return box;
    }

    private static HBox infoRow(String key, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label k = new Label(key + ":"); k.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-min-width: 108;");
        Label v = new Label(value);     v.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Theme.TEXT + "; -fx-font-weight: bold;");
        row.getChildren().addAll(k, v);
        return row;
    }

    private static HBox normRow(String std, String desc) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        Label k = new Label(std);
        k.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;" +
            "-fx-min-width: 118; -fx-background-color: #E8F5EC;" +
            "-fx-background-radius: 3; -fx-padding: 2 6 2 6;");
        Label d = new Label(desc); d.setWrapText(true);
        d.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.TEXT + ";");
        row.getChildren().addAll(k, d);
        return row;
    }

    private static Label badge(String text, String color) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-background-color: " + color + "18; -fx-text-fill: " + color + ";" +
            "-fx-font-size: 11px; -fx-background-radius: 4; -fx-padding: 4 10 4 10;" +
            "-fx-border-color: " + color + "40; -fx-border-width: 1; -fx-border-radius: 4;");
        return l;
    }

    private static Separator sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: " + Theme.BORDER + ";");
        return s;
    }
}
