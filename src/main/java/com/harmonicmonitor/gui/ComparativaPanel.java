package com.harmonicmonitor.gui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

/**
 * Pestaña "¿Por qué HADES?" — Análisis técnico comparativo
 * frente a alternativas de código abierto (NILM-TK, GridLAB-D, PandaPower, OpenDSS).
 *
 * Renderiza HTML con WebView de JavaFX para aprovechar encabezados,
 * tablas y código formateados visualmente.
 */
public class ComparativaPanel {

    private final StackPane root;

    public ComparativaPanel() {
        root = new StackPane();
        root.setStyle("-fx-background-color: #FFFFFF;");

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().loadContent(buildHtml());

        root.getChildren().add(webView);
    }

    public Node getNode() { return root; }

    // ── HTML content ─────────────────────────────────────────────────────────

    private static String buildHtml() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
        "<style>" +
        "  body {" +
        "    background: #F8F9FA;" +
        "    color: #1A1A1A;" +
        "    font-family: 'Segoe UI', Arial, sans-serif;" +
        "    font-size: 13px;" +
        "    line-height: 1.65;" +
        "    margin: 0;" +
        "    padding: 28px 40px 48px 40px;" +
        "  }" +
        "  h1 {" +
        "    color: #1A1A1A;" +
        "    font-size: 22px;" +
        "    border-bottom: 2px solid #0078D4;" +
        "    padding-bottom: 10px;" +
        "    margin-bottom: 4px;" +
        "  }" +
        "  .subtitle {" +
        "    color: #222222;" +
        "    font-size: 12px;" +
        "    margin-bottom: 32px;" +
        "  }" +
        "  h2 {" +
        "    color: #0078D4;" +
        "    font-size: 14px;" +
        "    font-weight: bold;" +
        "    text-transform: uppercase;" +
        "    letter-spacing: 0.5px;" +
        "    border-bottom: 1px solid #CCCCCC;" +
        "    padding-bottom: 5px;" +
        "    margin-top: 36px;" +
        "    margin-bottom: 14px;" +
        "  }" +
        "  h3 {" +
        "    color: #005A9E;" +
        "    font-size: 13px;" +
        "    margin-top: 22px;" +
        "    margin-bottom: 8px;" +
        "  }" +
        "  table {" +
        "    width: 100%;" +
        "    border-collapse: collapse;" +
        "    margin: 14px 0 20px 0;" +
        "    font-size: 12px;" +
        "  }" +
        "  th {" +
        "    background: #F5F5F5;" +
        "    color: #222222;" +
        "    font-weight: bold;" +
        "    text-align: left;" +
        "    padding: 8px 12px;" +
        "    border: 1px solid #CCCCCC;" +
        "    text-transform: uppercase;" +
        "    font-size: 11px;" +
        "    letter-spacing: 0.3px;" +
        "  }" +
        "  td {" +
        "    padding: 7px 12px;" +
        "    border: 1px solid #F0F0F0;" +
        "    vertical-align: top;" +
        "  }" +
        "  tr:nth-child(even) td { background: #FFFFFF; }" +
        "  tr:nth-child(odd)  td { background: #F8F8F8; }" +
        "  .check   { color: #107C10; font-weight: bold; }" +
        "  .cross   { color: #C42B1C; font-weight: bold; }" +
        "  .calc    { color: #CA5010; }" +
        "  .na      { color: #555E6E; }" +
        "  .star    { color: #CA5010; font-size: 15px; }" +
        "  pre, code {" +
        "    background: #F5F5F5;" +
        "    color: #003070;" +
        "    font-family: 'Consolas', 'Courier New', monospace;" +
        "    font-size: 11.5px;" +
        "    border: 1px solid #CCCCCC;" +
        "    border-radius: 5px;" +
        "  }" +
        "  pre {" +
        "    padding: 14px 18px;" +
        "    overflow-x: auto;" +
        "    line-height: 1.55;" +
        "    margin: 10px 0 18px 0;" +
        "  }" +
        "  code { padding: 1px 5px; border-radius: 3px; }" +
        "  ul, ol { margin: 8px 0 12px 0; padding-left: 24px; }" +
        "  li { margin-bottom: 4px; }" +
        "  .callout {" +
        "    background: #E8F0FA;" +
        "    border-left: 4px solid #0078D4;" +
        "    padding: 12px 18px;" +
        "    margin: 18px 0;" +
        "    border-radius: 0 6px 6px 0;" +
        "    font-style: italic;" +
        "    color: #333333;" +
        "  }" +
        "  .callout-warn {" +
        "    background: #FFF8E0;" +
        "    border-left: 4px solid #CA5010;" +
        "    padding: 12px 18px;" +
        "    margin: 18px 0;" +
        "    border-radius: 0 6px 6px 0;" +
        "    color: #7A4000;" +
        "  }" +
        "  .badge {" +
        "    display: inline-block;" +
        "    padding: 2px 8px;" +
        "    border-radius: 10px;" +
        "    font-size: 11px;" +
        "    font-weight: bold;" +
        "  }" +
        "  .badge-blue  { background:#D0E8FF; color:#005A9E; }" +
        "  .badge-green { background:#D0F0D0; color:#107C10; }" +
        "  .badge-amber { background:#FFF0CC; color:#CA5010; }" +
        "  .section-map {" +
        "    background: #F0F4FA;" +
        "    border: 1px solid #CCCCCC;" +
        "    border-radius: 6px;" +
        "    padding: 16px 20px;" +
        "    font-family: 'Consolas', monospace;" +
        "    font-size: 12px;" +
        "    color: #222222;" +
        "    line-height: 1.7;" +
        "    margin: 14px 0 20px 0;" +
        "  }" +
        "  .section-map .hl { color: #CA5010; font-weight: bold; }" +
        "  .section-map .dim { color: #3A4560; }" +
        "  .flow-box {" +
        "    display: inline-block;" +
        "    background: #F5F5F5;" +
        "    border: 1px solid #CCCCCC;" +
        "    border-radius: 5px;" +
        "    padding: 14px 20px;" +
        "    width: 45%;" +
        "    vertical-align: top;" +
        "    font-size: 12px;" +
        "    margin: 0 1% 10px 0;" +
        "  }" +
        "  .flow-box h4 { margin: 0 0 10px 0; font-size: 12px; }" +
        "  .flow-box.bad  h4 { color: #C42B1C; }" +
        "  .flow-box.good h4 { color: #107C10; }" +
        "  .flow-step { color: #222222; margin: 3px 0; }" +
        "  .flow-result { margin-top: 10px; font-weight: bold; font-size: 11px; }" +
        "  .flow-box.bad  .flow-result { color: #C42B1C; }" +
        "  .flow-box.good .flow-result { color: #107C10; }" +
        "  hr { border: none; border-top: 1px solid #F0F0F0; margin: 30px 0; }" +
        "  .refs { font-size: 11px; color: #5A6680; }" +
        "  .refs li { margin-bottom: 2px; }" +
        "</style></head><body>" +

        "<h1>&#127942; ¿Por qué HADES?</h1>" +
        "<div class='subtitle'>Análisis técnico comparativo frente a alternativas de código abierto &nbsp;·&nbsp; v1.0 &nbsp;·&nbsp; Marzo 2026</div>" +

        // ── 1. Definición del problema ────────────────────────────────────────
        "<h2>1. Definición del problema</h2>" +
        "<div class='callout'>" +
        "Monitoreo en tiempo real de calidad de energía y detección de cargas electrónicas " +
        "en alimentadores MT 23&nbsp;kV, adquiriendo datos directamente desde IEDs que " +
        "implementan el protocolo IEC&nbsp;61850." +
        "</div>" +
        "<p>Esta definición precisa es importante. Elimina la confusión con herramientas que resuelven " +
        "problemas distintos — aunque superficialmente parezcan similares.</p>" +

        // ── 2. Mapa del ecosistema ─────────────────────────────────────────────
        "<h2>2. Mapa del ecosistema de herramientas</h2>" +
        "<div class='section-map'>" +
        "<span class='dim'>┌─────────────────────────────────────────────────────┐</span><br>" +
        "<span class='dim'>│</span>  SIMULACIÓN DE RED (offline, requieren modelo)      <span class='dim'>│</span><br>" +
        "<span class='dim'>│</span>  GridLAB-D &nbsp; PandaPower &nbsp; OpenDSS               <span class='dim'>│</span><br>" +
        "<span class='dim'>│</span>  <span style='color:#555E6E'>→ Calculan desde modelos matemáticos. Sin IEDs reales.</span> <span class='dim'>│</span><br>" +
        "<span class='dim'>└─────────────────────────────────────────────────────┘</span><br>" +
        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↕ <span style='color:#3A4560'>no se intersectan</span><br>" +
        "<span class='dim'>┌─────────────────────────────────────────────────────┐</span><br>" +
        "<span class='dim'>│</span>  DESAGREGACIÓN DE CARGA (medidor domiciliario BT)   <span class='dim'>│</span><br>" +
        "<span class='dim'>│</span>  NILM-TK                                             <span class='dim'>│</span><br>" +
        "<span class='dim'>│</span>  <span style='color:#555E6E'>→ Patrones ON/OFF desde un único medidor. Sin IEC 61850.</span> <span class='dim'>│</span><br>" +
        "<span class='dim'>└─────────────────────────────────────────────────────┘</span><br>" +
        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↕ <span style='color:#3A4560'>no se intersectan</span><br>" +
        "<span class='dim'>┌─────────────────────────────────────────────────────┐</span><br>" +
        "<span class='dim'>│</span>  MONITOREO IEC&nbsp;61850 + ARMÓNICOS EN TIEMPO REAL  <span class='dim'>│</span><br>" +
        "<span class='dim'>│</span>  <span class='hl'>★ HADES</span>                                  <span class='dim'>│</span><br>" +
        "<span class='dim'>│</span>  <span style='color:#107C10'>→ Único sistema gratuito en este cuadrante.</span>         <span class='dim'>│</span><br>" +
        "<span class='dim'>└─────────────────────────────────────────────────────┘</span>" +
        "</div>" +
        "<p><strong>Diferencia estructural:</strong> los simuladores <em>calculan</em> valores a partir de modelos matemáticos. " +
        "HADES <em>lee</em> valores del equipo físico real mediante el protocolo estándar IEC&nbsp;61850.</p>" +

        // ── 3. Adquisición de datos ────────────────────────────────────────────
        "<h2>3. Adquisición de datos desde IEDs físicos</h2>" +
        "<table>" +
        "<tr><th>Capacidad</th><th>HADES</th><th>NILM-TK</th><th>OpenDSS</th><th>PandaPower</th><th>GridLAB-D</th></tr>" +
        "<tr><td>IEC 61850 MMS nativo</td><td><span class='check'>✓</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td></tr>" +
        "<tr><td>Auto-discovery de Logical Nodes</td><td><span class='check'>✓</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td></tr>" +
        "<tr><td>Polling en tiempo real</td><td><span class='check'>✓</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td></tr>" +
        "<tr><td>MMXU — V, I, P, Q, f</td><td><span class='check'>✓ medido</span></td><td><span class='cross'>✗</span></td><td><span class='calc'>Calculado</span></td><td><span class='calc'>Calculado</span></td><td><span class='calc'>Calculado</span></td></tr>" +
        "<tr><td>MHAI — THD, espectro H1..H50</td><td><span class='check'>✓ medido</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td></tr>" +
        "<tr><td>MSQI — componentes simétricas</td><td><span class='check'>✓ medido</span></td><td><span class='cross'>✗</span></td><td><span class='calc'>Calculado</span></td><td><span class='calc'>Calculado</span></td><td><span class='calc'>Calculado</span></td></tr>" +
        "<tr><td>MMTR — energía acumulada</td><td><span class='check'>✓</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td><td><span class='cross'>✗</span></td></tr>" +
        "<tr><td>Compatible multi-fabricante</td><td><span class='check'>✓</span></td><td><span class='na'>N/A</span></td><td><span class='na'>N/A</span></td><td><span class='na'>N/A</span></td><td><span class='na'>N/A</span></td></tr>" +
        "</table>" +

        // ── 4. Detección de cargas ─────────────────────────────────────────────
        "<h2>4. Detección e identificación de cargas electrónicas</h2>" +
        "<p>Esta es la capacidad más diferenciadora. <strong>Ninguna herramienta comparable la implementa.</strong></p>" +
        "<pre>" +
        "Parámetros de entrada:\n" +
        "  CV    = σ(I) / μ(I)   — Coef. de variación de corriente (ventana deslizante)\n" +
        "  THDi  = THD de corriente (%)\n" +
        "  H5/H1 = ratio 5° armónico / fundamental\n" +
        "  H7/H1 = ratio 7° armónico / fundamental\n\n" +
        "Clasificación:\n" +
        "  CV &lt; 5%  AND THDi &gt; 15% AND H5/H1 &gt; 15% AND H7/H1 &gt; 10%\n" +
        "    → CRIPTO / DATACENTER     (rectificador 6 pulsos no controlado)\n\n" +
        "  CV &lt; 5%  AND THDi &gt; 8%\n" +
        "    → CARGA ELECTRÓNICA       (SMPS, VFD, UPS, cargadores)\n\n" +
        "  CV &gt; 20% AND THDi &lt; 5%\n" +
        "    → CARGA LINEAL VARIABLE   (motores arranque/parada, hornos)\n\n" +
        "  CV &lt; 5%  AND THDi &lt; 5%\n" +
        "    → CARGA LINEAL ESTABLE    (resistencias, iluminación)" +
        "</pre>" +
        "<h3>Por qué NILM-TK no resuelve este problema</h3>" +
        "<ul>" +
        "<li>NILM-TK usa transiciones ON/OFF — las granjas cripto operan 24/7 sin transiciones discretas.</li>" +
        "<li>La firma de identificación está en el <strong>espectro de frecuencias</strong>, no en la forma de onda temporal.</li>" +
        "<li>Opera en MT a través de TP/TC, no en el tablero de baja tensión donde NILM-TK funciona.</li>" +
        "<li>Las cargas individuales son invisibles en el agregado del alimentador a escala MT.</li>" +
        "</ul>" +

        // ── 5. Análisis de armónicos ───────────────────────────────────────────
        "<h2>5. Análisis de armónicos</h2>" +
        "<table>" +
        "<tr><th>Capacidad</th><th>HADES</th><th>OpenDSS</th><th>PandaPower</th></tr>" +
        "<tr><td>Fuente de datos</td><td><span class='check'>IED real (MHAI)</span></td><td><span class='calc'>Modelo matemático</span></td><td><span class='calc'>Modelo matemático</span></td></tr>" +
        "<tr><td>THD tensión / corriente</td><td>Medido</td><td>Calculado</td><td>No nativo</td></tr>" +
        "<tr><td>Espectro H1..H50</td><td>MHAI.HA del IED</td><td>Solver Harmonics</td><td>Plugin externo</td></tr>" +
        "<tr><td>K-Factor</td><td>MHAI.HKf del IED</td><td>Calculable</td><td>No</td></tr>" +
        "<tr><td>Desbalance (IEEE 1459-2010)</td><td>MSQI — SeqA, SeqV</td><td>Calculable</td><td>Calculable</td></tr>" +
        "<tr><td>Análisis de resonancia</td><td>f = 1/2π√LC</td><td>Barrido Z(f) completo</td><td>No</td></tr>" +
        "<tr><td><strong>Requiere modelo de red</strong></td><td><span class='check'><strong>NO</strong></span></td><td><span class='cross'><strong>SÍ</strong></span></td><td><span class='cross'><strong>SÍ</strong></span></td></tr>" +
        "</table>" +
        "<p>OpenDSS puede calcular curvas Z(f) completas para la red — análisis más riguroso. " +
        "Pero requiere construir el modelo topológico completo del alimentador: " +
        "semanas de trabajo por feeder que se desactualiza con cada reconfiguración. " +
        "HADES entrega el dato <strong>medido del equipo real</strong>, sin modelo previo.</p>" +

        // ── 6. Motor de alarmas ───────────────────────────────────────────────
        "<h2>6. Motor de alarmas en tiempo real</h2>" +
        "<p><span class='badge badge-blue'>INFO</span> &nbsp;→&nbsp; " +
        "<span class='badge badge-green'>WARNING</span> &nbsp;→&nbsp; " +
        "<span class='badge badge-amber'>CRITICAL</span> &nbsp;→&nbsp; " +
        "<span class='badge' style='background:#3A0010;color:#FF6080;'>EMERGENCY</span></p>" +
        "<p>Disparadores evaluados en cada ciclo de polling:</p>" +
        "<ul>" +
        "<li><strong>THD_V</strong> — umbral configurable (ref: EN 50160, IEC 61000-3-6)</li>" +
        "<li><strong>THD_I</strong> — umbral configurable (ref. orientativa: IEEE 519-2022 Tabla 2)</li>" +
        "<li><strong>Desbalance de tensión</strong> &gt; 2% (ref: EN 50160 §4.3)</li>" +
        "<li><strong>Desbalance de corriente</strong> &gt; 10%</li>" +
        "<li><strong>Frecuencia</strong> fuera de 50 Hz ± 0.5 Hz (ref: EN 50160 §4.1)</li>" +
        "<li><strong>Detección de carga electrónica</strong> activa</li>" +
        "<li><strong>Resonancia potencial</strong>: h_res cercano a armónico dominante</li>" +
        "</ul>" +
        "<div class='callout-warn'>" +
        "Ninguna alternativa gratuita tiene motor de alarmas en tiempo real. " +
        "OpenDSS y PandaPower son herramientas <em>batch</em>: ejecutan un análisis y terminan. " +
        "No monitorean continuamente." +
        "</div>" +

        // ── 7. Flujo de trabajo ────────────────────────────────────────────────
        "<h2>7. Flujo de trabajo comparado</h2>" +
        "<p><em>Escenario:</em> <strong>¿Hay una granja de criptomonedas en el alimentador AL-05?</strong></p>" +
        "<div class='flow-box bad'>" +
        "<h4>&#10060; Con OpenDSS</h4>" +
        "<div class='flow-step'>1. Obtener modelo topológico completo ............. días</div>" +
        "<div class='flow-step'>2. Parametrizar todas las cargas ................. horas</div>" +
        "<div class='flow-step'>3. Configurar fuente armónica y correr análisis .. horas</div>" +
        "<div class='flow-step'>4. Interpretar resultados ........................ horas</div>" +
        "<div class='flow-result'>→ Varios días — con datos sintéticos (simulación)</div>" +
        "</div>" +
        "<div class='flow-box good'>" +
        "<h4>&#10003; Con HADES</h4>" +
        "<div class='flow-step'>1. Conectar al IED del alimentador .............. 30 seg</div>" +
        "<div class='flow-step'>2. Auto-discovery de Logical Nodes .............. ~1 min</div>" +
        "<div class='flow-step'>3. Iniciar monitoreo .......................... inmediato</div>" +
        "<div class='flow-step'>4. Clasificador reporta CV=2.1%, THDi=38%, H5/H1=32%</div>" +
        "<div class='flow-result'>→ &lt; 5 minutos — datos medidos del IED real</div>" +
        "</div>" +

        // ── 8. Donde los competidores son mejores ─────────────────────────────
        "<h2>8. Donde las alternativas son técnicamente superiores</h2>" +
        "<p>Honestidad técnica — existen áreas donde los competidores ganan:</p>" +
        "<h3>OpenDSS supera a HADES en:</h3>" +
        "<ul>" +
        "<li>Propagación de armónicos en red — flujo entre nodos con impedancias de línea</li>" +
        "<li>Barrido de frecuencia completo — curva Z(f) para toda la red</li>" +
        "<li>Modelado de filtros de armónicos pasivos/activos y su efecto en la red</li>" +
        "<li>Análisis Monte Carlo con incertidumbre en parámetros de red</li>" +
        "</ul>" +
        "<h3>PandaPower supera en:</h3>" +
        "<ul>" +
        "<li>Flujo de potencia óptimo (OPF) con restricciones de seguridad</li>" +
        "<li>Análisis de cortocircuito riguroso (IEC 60909 / ANSI)</li>" +
        "<li>Integración con GIS y modelos de red complejos</li>" +
        "</ul>" +
        "<h3>NILM-TK supera en:</h3>" +
        "<ul>" +
        "<li>Desagregación fina de cargas individuales dentro de una instalación domiciliaria</li>" +
        "<li>Catálogo de algoritmos NILM publicados (Hart 1985, FHMM, CO, etc.)</li>" +
        "</ul>" +
        "<div class='callout'>" +
        "La diferencia de fondo: esas capacidades son para <strong>planificación e ingeniería de red</strong>. " +
        "HADES es para <strong>operación en tiempo real</strong>. Son herramientas complementarias, no competidoras." +
        "</div>" +

        // ── 9. Posicionamiento ─────────────────────────────────────────────────
        "<h2>9. Posicionamiento en el espacio de herramientas</h2>" +
        "<div class='section-map'>" +
        "                    <span style='color:#0078D4'>PLANIFICACIÓN</span>              <span style='color:#107C10'>OPERACIÓN</span><br>" +
        "                    (offline / batch)          (tiempo real)<br>" +
        "                         │                         │<br>" +
        "   NIVEL DE RED ─────────┤  OpenDSS                │<br>" +
        "   (topología completa)   │  PandaPower             │  <span class='dim'>← vacío en gratuitos</span><br>" +
        "                         │  GridLAB-D              │<br>" +
        "                         │                         │<br>" +
        "   NIVEL DE IED ─────────┤       <span class='dim'>(vacío)</span>           │  <span class='hl'>★ HADES</span><br>" +
        "   (IEC 61850)            │                         │  <span class='dim'>(único aquí)</span><br>" +
        "                         │                         │<br>" +
        "   NIVEL DE CARGA ───────┤  NILM-TK                │<br>" +
        "   (instalación BT)       │  (análisis histórico)   │<br>" +
        "</div>" +

        // ── 10. Conclusión ─────────────────────────────────────────────────────
        "<h2>10. Conclusión</h2>" +
        "<p>HADES no compite con OpenDSS para análisis de propagación de armónicos en red compleja. " +
        "No compite con PandaPower para estudios de flujo de potencia. " +
        "No compite con NILM-TK para desagregación de cargas domiciliarias.</p>" +
        "<p>Ocupa un espacio técnico que las herramientas gratuitas existentes no cubren:</p>" +
        "<div class='callout'>" +
        "Adquirir, procesar, clasificar y alarmar en tiempo real sobre la calidad de energía " +
        "de un alimentador MT, conectándose directamente al IED vía IEC&nbsp;61850, sin requerir " +
        "modelo previo de la red, identificando automáticamente la presencia de cargas electrónicas " +
        "de alta potencia a partir de su firma armónica." +
        "</div>" +
        "<p>Para ese problema específico, es la única herramienta gratuita disponible. " +
        "Las alternativas comerciales equivalentes (OMICRON IEDScout, Elspec PQZIP, software " +
        "propietario Schneider/ABB/GE) tienen costo significativo y no entregan el código fuente.</p>" +

        // ── Referencias ────────────────────────────────────────────────────────
        "<hr>" +
        "<h2>Referencias</h2>" +
        "<ul class='refs'>" +
        "<li>IEC 61850-7-4 — Basic communication structure: Compatible logical node classes and data classes</li>" +
        "<li>IEEE 519-2022 — Harmonic Control in Electric Power Systems</li>" +
        "<li>IEEE 1459-2010 — Definitions for the Measurement of Electric Power Quantities</li>" +
        "<li>IEC 61000-3-6 — Assessment of emission limits for distorting installations to MV/HV</li>" +
        "<li>EN 50160:2010 — Voltage characteristics of electricity supplied by public networks</li>" +
        "<li>IEEE C37.111-2013 (COMTRADE) — Standard Common Format for Transient Data Exchange</li>" +
        "<li>Kelly, J. &amp; Knottenbelt, W. (2015). Neural NILM. ACM BuildSys.</li>" +
        "<li>Dugan, R.C. (2012). OpenDSS Reference Guide. EPRI, Palo Alto CA.</li>" +
        "<li>Thurner et al. (2018). pandapower. IEEE Trans. Power Systems, 33(6).</li>" +
        "</ul>" +

        "</body></html>";
    }
}
