package com.harmonicmonitor.gui;

/**
 * Tema visual de la aplicación.
 * Contiene los colores activos según modo claro/oscuro.
 * Los paneles leen estos valores en sus constructores; al cambiar el tema
 * se llama a apply() y luego se reconstruye la escena.
 */
public class Theme {

    public static boolean dark = false;

    // Backgrounds
    public static String BG     = "#E4E7EC";   // fondo principal de paneles
    public static String CARD   = "#F5F6F8";   // tarjetas / contenido
    public static String HEADER = "#E4E7EC";   // encabezados / toolbar

    // Text
    public static String TEXT   = "#000000";   // texto primario
    public static String TEXT2  = "#333333";   // texto secundario

    // UI chrome
    public static String BORDER  = "#CCCCCC";  // bordes generales
    public static String BORDER2 = "#AAAAAA";  // bordes más marcados
    public static String HOVER   = "#E5F3FF";  // hover de filas/botones
    public static String SEL     = "#CCE8FF";  // selección activa

    // Semantic (iguales en ambos temas)
    public static final String ACCENT  = "#0078D4";
    public static final String OK      = "#107C10";
    public static final String WARN    = "#CA5010";
    public static final String ERR     = "#C42B1C";
    public static final String PURPLE  = "#881798";
    public static final String CYAN    = "#0099BC";

    public static void apply(boolean d) {
        dark = d;
        if (d) {
            BG      = "#1E2130";
            CARD    = "#252A3A";
            HEADER  = "#181C28";
            TEXT    = "#E8E8E8";
            TEXT2   = "#BBBBBB";
            BORDER  = "#3A4060";
            BORDER2 = "#4A5070";
            HOVER   = "#1A3050";
            SEL     = "#1A4A80";
        } else {
            BG      = "#E4E7EC";
            CARD    = "#F5F6F8";
            HEADER  = "#E4E7EC";
            TEXT    = "#000000";
            TEXT2   = "#333333";
            BORDER  = "#CCCCCC";
            BORDER2 = "#AAAAAA";
            HOVER   = "#E5F3FF";
            SEL     = "#CCE8FF";
        }
    }

    public static String css() {
        return dark
            ? "/com/harmonicmonitor/styles-dark.css"
            : "/com/harmonicmonitor/styles.css";
    }
}
