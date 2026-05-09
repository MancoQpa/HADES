package com.harmonicmonitor.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Genera el archivo .xlsx del dataset ML (pure-Java OOXML, sin dependencias externas).
 *
 * Un archivo .xlsx es un ZIP que contiene XML segun la especificacion OOXML.
 * Hoja 1: datos del dataset (una fila por muestra).
 * Hoja 2: diccionario de variables con unidades, descripcion y formulas.
 *
 * Estilos definidos (indice s= en cada celda):
 *   0 = normal
 *   1 = negrita (nombres de columna en hoja 2)
 *   2 = negrita blanca sobre azul oscuro (encabezados de seccion)
 *   3 = negrita blanca sobre azul medio (fila de cabecera de tabla)
 *   4 = fondo azul muy claro (filas pares de la tabla)
 */
final class MlXlsxWriter {

    private MlXlsxWriter() {}

    // ── Metadatos de variables: { nombre, unidad, descripcion, formula/criterio } ──
    private static final String[][] COL_META = {
        // Identificacion
        {"timestamp",          "—",     "Fecha y hora de la muestra (zona horaria local)",
         "Generado en cada ciclo de caracterizacion (1 min)"},
        {"feeder_id",          "—",     "Identificador unico del alimentador",
         "Configurado en Gestion de Alimentadores"},
        {"spectrum_estimated", "0/1",   "Indica si el espectro fue estimado por SW (1) o medido en el IED (0)",
         "1 si el IED no dispone de MHAI; espectro calculado a partir de THDi y firma tipica"},
        {"quality_flag",       "—",     "Calidad de la medicion",
         "GOOD | COMM_ERROR | SIMULATED"},
        // Tensiones
        {"V_L1_V",  "V", "Tension fase-neutro L1 (RMS)",
         "V_LN = V_LL / sqrt(3) = 23000 / 1.732 aprox 13 280 V  (nominal)"},
        {"V_L2_V",  "V", "Tension fase-neutro L2 (RMS)", "Idem L1"},
        {"V_L3_V",  "V", "Tension fase-neutro L3 (RMS)", "Idem L1"},
        {"V_avg_V", "V", "Tension fase-neutro promedio trifasico",
         "(V_L1 + V_L2 + V_L3) / 3"},
        // Corrientes
        {"I_L1_A",  "A", "Corriente de linea fase L1 (RMS)",
         "Medido por MMXU.A.phsA via IEC 61850 MMS"},
        {"I_L2_A",  "A", "Corriente de linea fase L2 (RMS)", "Idem L1"},
        {"I_L3_A",  "A", "Corriente de linea fase L3 (RMS)", "Idem L1"},
        {"I_avg_A", "A", "Corriente de linea promedio trifasico",
         "(I_L1 + I_L2 + I_L3) / 3"},
        // Potencias
        {"P_kW",   "kW",   "Potencia activa total trifasica",
         "P = 3 x V_LN x I x cos(phi)   [MMXU.TotW]"},
        {"Q_kVAR", "kVAR", "Potencia reactiva total trifasica",
         "Q = 3 x V_LN x I x sin(phi)   [MMXU.TotVAr]"},
        {"S_kVA",  "kVA",  "Potencia aparente total trifasica",
         "S = sqrt(P^2 + Q^2)   [MMXU.TotVA]"},
        {"PF",     "—",    "Factor de potencia total (cos phi)",
         "PF = P / S   (rango 0..1)   [MMXU.TotPF]"},
        {"freq_Hz","Hz",   "Frecuencia de la red electrica",
         "Nominal 50 Hz   [MMXU.Hz]"},
        // THD tension
        {"THD_V_L1_pct",  "%", "Distorsion armonica total de tension fase L1",
         "THD_V = (sqrt(sum Vn^2) / V1) x 100   n = 2..50"},
        {"THD_V_L2_pct",  "%", "Distorsion armonica total de tension fase L2", "Idem L1"},
        {"THD_V_L3_pct",  "%", "Distorsion armonica total de tension fase L3", "Idem L1"},
        {"THD_V_avg_pct", "%", "THD de tension promedio trifasico",
         "(THD_V_L1 + THD_V_L2 + THD_V_L3) / 3"},
        // THD corriente
        {"THD_I_L1_pct",      "%", "Distorsion armonica total de corriente fase L1",
         "THD_I = (sqrt(sum In^2) / I1) x 100   n = 2..50   [MHAI.ThdA.phsA]"},
        {"THD_I_L2_pct",      "%", "Distorsion armonica total de corriente fase L2", "Idem L1"},
        {"THD_I_L3_pct",      "%", "Distorsion armonica total de corriente fase L3", "Idem L1"},
        {"THD_I_avg_pct",     "%", "THD de corriente promedio trifasico",
         "(THD_I_L1 + THD_I_L2 + THD_I_L3) / 3"},
        {"THD_I_odd_L1_pct",  "%", "THD de armonicos impares de corriente L1",
         "sqrt(sum I(2k+1)^2) / I1 x 100   Alto en cargas electronicas (H3, H5, H7...)"},
        {"THD_I_even_L1_pct", "%", "THD de armonicos pares de corriente L1",
         "sqrt(sum I(2k)^2) / I1 x 100   Alto indica asimetria (arco electrico, rectificador media onda)"},
        // Ratios clave
        {"H5_H1_I_pct",  "%", "Relacion 5o armonico / fundamental de corriente L1",
         "(I5 / I1) x 100   Criterio cripto/DC: > 15%   IEEE 519 limite: 4%"},
        {"H7_H1_I_pct",  "%", "Relacion 7o armonico / fundamental de corriente L1",
         "(I7 / I1) x 100   Criterio cripto/DC: > 10%"},
        {"H11_H1_I_pct", "%", "Relacion 11o armonico / fundamental de corriente L1",
         "(I11 / I1) x 100   Criterio VFD 6-pulsos: > 5%"},
        {"H13_H1_I_pct", "%", "Relacion 13o armonico / fundamental de corriente L1",
         "(I13 / I1) x 100   Criterio VFD 6-pulsos: > 4%"},
        {"CV_I",         "—", "Coeficiente de variacion de corriente (ventana 60 muestras aprox 5 min)",
         "CV = sigma(I) / mu(I)   < 5%: carga estable (SMPS/PFC, datacenter)   > 5%: variable (industrial, motores)"},
        {"K_factor_L1",  "—", "K-Factor de corriente fase L1",
         "K = sum(n^2 x (In/IRMS)^2)   K > 4: transformador especial recomendado   [MHAI.HKf.phsA]"},
        // Espectro corriente H2..H25
        {"H2_I_pct",  "%", "Armonico orden 2 corriente L1 (% del fundamental)",
         "(I2 / I1) x 100   Bajo en condiciones normales"},
        {"H3_I_pct",  "%", "Armonico orden 3 corriente L1 (% del fundamental)",
         "(I3 / I1) x 100   Dominante en LED, UPS monofasicos   IEEE 519 limite: 4%"},
        {"H4_I_pct",  "%", "Armonico orden 4 corriente L1 (% del fundamental)",
         "(I4 / I1) x 100"},
        {"H5_I_pct",  "%", "Armonico orden 5 corriente L1 (% del fundamental)",
         "(I5 / I1) x 100   IEEE 519 limite: 4%   Firma de rectificadores SMPS"},
        {"H6_I_pct",  "%", "Armonico orden 6 corriente L1 (% del fundamental)",
         "(I6 / I1) x 100"},
        {"H7_I_pct",  "%", "Armonico orden 7 corriente L1 (% del fundamental)",
         "(I7 / I1) x 100   IEEE 519 limite: 4%   Presente junto con H5 en rectificadores"},
        {"H8_I_pct",  "%", "Armonico orden 8 corriente L1 (% del fundamental)",
         "(I8 / I1) x 100"},
        {"H9_I_pct",  "%", "Armonico orden 9 corriente L1 (% del fundamental)",
         "(I9 / I1) x 100   Triplen: circula por neutro"},
        {"H10_I_pct", "%", "Armonico orden 10 corriente L1 (% del fundamental)",
         "(I10 / I1) x 100"},
        {"H11_I_pct", "%", "Armonico orden 11 corriente L1 (% del fundamental)",
         "(I11 / I1) x 100   IEEE 519 limite: 2%   Presente en convertidores 12-pulsos"},
        {"H12_I_pct", "%", "Armonico orden 12 corriente L1 (% del fundamental)",
         "(I12 / I1) x 100"},
        {"H13_I_pct", "%", "Armonico orden 13 corriente L1 (% del fundamental)",
         "(I13 / I1) x 100   IEEE 519 limite: 2%"},
        {"H14_I_pct", "%", "Armonico orden 14 corriente L1 (% del fundamental)",
         "(I14 / I1) x 100"},
        {"H15_I_pct", "%", "Armonico orden 15 corriente L1 (% del fundamental)",
         "(I15 / I1) x 100   Triplen orden 5"},
        {"H16_I_pct", "%", "Armonico orden 16 corriente L1 (% del fundamental)",
         "(I16 / I1) x 100"},
        {"H17_I_pct", "%", "Armonico orden 17 corriente L1 (% del fundamental)",
         "(I17 / I1) x 100   IEEE 519 limite: 1.5%"},
        {"H18_I_pct", "%", "Armonico orden 18 corriente L1 (% del fundamental)",
         "(I18 / I1) x 100"},
        {"H19_I_pct", "%", "Armonico orden 19 corriente L1 (% del fundamental)",
         "(I19 / I1) x 100   IEEE 519 limite: 1.5%"},
        {"H20_I_pct", "%", "Armonico orden 20 corriente L1 (% del fundamental)",
         "(I20 / I1) x 100"},
        {"H21_I_pct", "%", "Armonico orden 21 corriente L1 (% del fundamental)",
         "(I21 / I1) x 100   Triplen orden 7"},
        {"H22_I_pct", "%", "Armonico orden 22 corriente L1 (% del fundamental)",
         "(I22 / I1) x 100"},
        {"H23_I_pct", "%", "Armonico orden 23 corriente L1 (% del fundamental)",
         "(I23 / I1) x 100   IEEE 519 limite: 0.6%"},
        {"H24_I_pct", "%", "Armonico orden 24 corriente L1 (% del fundamental)",
         "(I24 / I1) x 100"},
        {"H25_I_pct", "%", "Armonico orden 25 corriente L1 (% del fundamental)",
         "(I25 / I1) x 100   IEEE 519 limite: 0.6%"},
        // Espectro tension
        {"H3_V_pct",  "%", "Armonico orden 3 de tension L1 (% del fundamental)",
         "(V3 / V1) x 100   IEC 61000-3-6 nivel de planificacion MT: 4%"},
        {"H5_V_pct",  "%", "Armonico orden 5 de tension L1 (% del fundamental)",
         "(V5 / V1) x 100   IEC 61000-3-6 nivel de planificacion MT: 3%"},
        {"H7_V_pct",  "%", "Armonico orden 7 de tension L1 (% del fundamental)",
         "(V7 / V1) x 100   IEC 61000-3-6 nivel de planificacion MT: 3%"},
        {"H9_V_pct",  "%", "Armonico orden 9 de tension L1 (% del fundamental)",
         "(V9 / V1) x 100   IEC 61000-3-6 nivel de planificacion MT: 1.5%"},
        {"H11_V_pct", "%", "Armonico orden 11 de tension L1 (% del fundamental)",
         "(V11 / V1) x 100   IEC 61000-3-6 nivel de planificacion MT: 2%"},
        {"H13_V_pct", "%", "Armonico orden 13 de tension L1 (% del fundamental)",
         "(V13 / V1) x 100   IEC 61000-3-6 nivel de planificacion MT: 2%"},
        // Componentes simetricas
        {"I_pos_A",     "A", "Corriente de secuencia positiva (componente util)",
         "I+ = (1/3)(IL1 + a*IL2 + a^2*IL3),  a = e^j120   [MSQI.SeqA.c1]"},
        {"I_neg_A",     "A", "Corriente de secuencia negativa (indica desbalance)",
         "I- = (1/3)(IL1 + a^2*IL2 + a*IL3)   Crea par frenante en motores   [MSQI.SeqA.c2]"},
        {"I_zero_A",    "A", "Corriente de secuencia cero (circula por neutro/tierra)",
         "I0 = (1/3)(IL1 + IL2 + IL3)   Causada por triplens H3, H9, H15..."},
        {"V_pos_V",     "V", "Tension de secuencia positiva",
         "Componente trifasico balanceado   [MSQI.SeqV.c1]"},
        {"V_neg_V",     "V", "Tension de secuencia negativa",
         "Indica desbalance de tension en la red   [MSQI.SeqV.c2]"},
        {"V_unbal_pct", "%", "Desbalance de tension trifasico",
         "(V_neg / V_pos) x 100   Limite EN 50160: 2%   Afecta rendimiento de motores"},
        {"I_unbal_pct", "%", "Desbalance de corriente trifasico",
         "(I_neg / I_pos) x 100   Alto indica carga monofasica dominante o asimetria"},
        // Resonancia
        {"res_freq_Hz", "Hz", "Frecuencia estimada de resonancia paralela red-capacitores",
         "f_res = f1 x sqrt(Scc / Qc)   equivalente a   1 / (2*pi*sqrt(L*C))"},
        {"res_order",   "—",  "Orden armonico de resonancia  (h = f_res / f1)",
         "Si coincide con H5 o H7 activos - riesgo de amplificacion de tension"},
        // Etiqueta
        {"label", "—", "Clasificacion de tipo de carga detectada por el algoritmo",
         "LINEAR: THDi<5%, H5<5% | ELECTRONIC_LIGHT: THDi>8%, H5>8% o H7>5% | " +
         "CRYPTO_MINING: CV<5%, THDi>15%, H5>15%, H7>10%, PF>0.92 | " +
         "DATA_CENTER: idem cripto pero PF<=0.92 | " +
         "INDUSTRIAL: firma 6-pulsos (H5>12%, H7>8%, H11>5%, H13>4%) | " +
         "MIXED_ELECTRONIC: 5%<THDi<15%, sin firma especifica | " +
         "LIGHTING: THDi>10%, H3 dominante"},
    };

    /**
     * Genera (o regenera) el archivo XLSX leyendo el CSV actualizado.
     * Llamado desde {@link MLDataExporter#appendRow} tras cada escritura CSV.
     */
    static void write(File csv, File xlsx, String feederId) throws IOException {
        List<String[]> rows = readCsvRows(csv);
        if (rows.isEmpty()) return;

        String sheet1 = feederId.length() > 31 ? feederId.substring(0, 31) : feederId;

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(xlsx)))) {

            // ── Infraestructura OOXML ──────────────────────────────────────────
            addEntry(zos, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>");

            addEntry(zos, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>");

            addEntry(zos, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>");

            addEntry(zos, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets>" +
                "<sheet name=\"" + escXml(sheet1) + "\" sheetId=\"1\" r:id=\"rId1\"/>" +
                "<sheet name=\"Diccionario de Variables\" sheetId=\"2\" r:id=\"rId2\"/>" +
                "</sheets></workbook>");

            // ── Estilos ────────────────────────────────────────────────────────
            addEntry(zos, "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"3\">" +
                  "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                  "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                  "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
                "</fonts>" +
                "<fills count=\"4\">" +
                  "<fill><patternFill patternType=\"none\"/></fill>" +
                  "<fill><patternFill patternType=\"gray125\"/></fill>" +
                  "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1565C0\"/></patternFill></fill>" +
                  "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE3F2FD\"/></patternFill></fill>" +
                "</fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"4\">" +
                  "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                  "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                  "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"0\" xfId=\"0\"/>" +
                  "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\"/>" +
                "</cellXfs>" +
                "</styleSheet>");

            addEntry(zos, "xl/worksheets/sheet1.xml", buildDataSheet(rows));
            addEntry(zos, "xl/worksheets/sheet2.xml", buildDictionarySheet());
        }
    }

    private static String buildDataSheet(List<String[]> rows) {
        StringBuilder sd = new StringBuilder(2048 + rows.size() * 512);
        sd.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sd.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sd.append("<cols>");
        sd.append("<col min=\"1\" max=\"1\" width=\"22\" customWidth=\"1\"/>");
        sd.append("<col min=\"2\" max=\"2\" width=\"14\" customWidth=\"1\"/>");
        sd.append("<col min=\"3\" max=\"73\" width=\"14\" customWidth=\"1\"/>");
        sd.append("</cols>");
        sd.append("<sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            String[] cols = rows.get(r);
            int rowStyle = (r == 0) ? 1 : 0;
            sd.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < cols.length; c++) {
                String val = cols[c].trim();
                String ref = colRef(c) + (r + 1);
                Double num = tryParseDouble(val);
                if (num != null) {
                    sd.append("<c r=\"").append(ref).append("\" s=\"").append(rowStyle).append("\"><v>")
                      .append(val).append("</v></c>");
                } else {
                    sd.append("<c r=\"").append(ref).append("\" s=\"").append(rowStyle)
                      .append("\" t=\"inlineStr\"><is><t>").append(escXml(val)).append("</t></is></c>");
                }
            }
            sd.append("</row>");
        }
        sd.append("</sheetData></worksheet>");
        return sd.toString();
    }

    private static String buildDictionarySheet() {
        StringBuilder s = new StringBuilder(16384);
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        s.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        s.append("<cols>");
        s.append("<col min=\"1\" max=\"1\" width=\"26\" customWidth=\"1\"/>");
        s.append("<col min=\"2\" max=\"2\" width=\"9\"  customWidth=\"1\"/>");
        s.append("<col min=\"3\" max=\"3\" width=\"55\" customWidth=\"1\"/>");
        s.append("<col min=\"4\" max=\"4\" width=\"70\" customWidth=\"1\"/>");
        s.append("</cols>");
        s.append("<sheetData>");

        int row = 1;

        s.append("<row r=\"").append(row++).append("\">");
        txt(s, "A", row - 1, 2,
            "DICCIONARIO DE VARIABLES - HADES v1.0  |  Alimentador MT 23 kV  |  IEEE 519-2022 / IEC 61000-3-6");
        s.append("</row>");

        s.append("<row r=\"").append(row++).append("\">");
        txt(s, "A", row - 1, 2,
            "Fuente: ION 7400 via IEC 61850 MMS (MMXU / MHAI / MSQI / MMTR / MSTA)  |  Normas: IEEE 519-2022, IEC 61000-3-6:2008, EN 50160:2010, IEC 61000-4-30:2015");
        s.append("</row>");

        s.append("<row r=\"").append(row++).append("\"></row>");

        s.append("<row r=\"").append(row++).append("\">");
        txt(s, "A", row - 1, 2, "Variable");
        txt(s, "B", row - 1, 2, "Unidad");
        txt(s, "C", row - 1, 2, "Descripcion");
        txt(s, "D", row - 1, 2, "Formula / Criterio / Norma");
        s.append("</row>");

        for (int i = 0; i < COL_META.length; i++) {
            String[] m = COL_META[i];
            int bg = (i % 2 == 0) ? 3 : 0;
            s.append("<row r=\"").append(row++).append("\">");
            txt(s, "A", row - 1, 1,  m[0]);
            txt(s, "B", row - 1, bg, m[1]);
            txt(s, "C", row - 1, bg, m[2]);
            txt(s, "D", row - 1, bg, m[3]);
            s.append("</row>");
        }

        s.append("<row r=\"").append(row++).append("\"></row>");
        s.append("<row r=\"").append(row).append("\">");
        txt(s, "A", row, 0, "Generado automaticamente por HADES v1.0  |  Emilio Medina");
        s.append("</row>");

        s.append("</sheetData></worksheet>");
        return s.toString();
    }

    private static void txt(StringBuilder sb, String col, int rowNum, int style, String value) {
        sb.append("<c r=\"").append(col).append(rowNum)
          .append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t>")
          .append(escXml(value)).append("</t></is></c>");
    }

    private static List<String[]> readCsvRows(File csv) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) rows.add(line.split(",", -1));
            }
        }
        return rows;
    }

    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String colRef(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col + 1;
        while (c > 0) {
            c--;
            sb.insert(0, (char) ('A' + c % 26));
            c /= 26;
        }
        return sb.toString();
    }

    private static Double tryParseDouble(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return null; }
    }

    private static String escXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
