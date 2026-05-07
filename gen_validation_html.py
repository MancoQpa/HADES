"""
HarmonicMonitor - Tabla de Validacion del Algoritmo de Deteccion de Carga
Genera: validation_test_table.html  (abrir en Chrome/Edge -> Ctrl+P -> PDF)
"""
import datetime, math

OUTPUT = "validation_test_table.html"
FECHA  = datetime.date.today().strftime("%d/%m/%Y")

# Datos de casos de prueba
LOAD_COLORS = {
    "LINEAR":           ("#1565C0", "#E3F2FD"),
    "LIGHTING":         ("#F57F17", "#FFFDE7"),
    "CRYPTO_MINING":    ("#B71C1C", "#FFEBEE"),
    "DATA_CENTER":      ("#4A148C", "#F3E5F5"),
    "MIXED_ELECTRONIC": ("#BF360C", "#FBE9E7"),
    "INDUSTRIAL":       ("#1B5E20", "#E8F5E9"),
    "ELECTRONIC_LIGHT": ("#E65100", "#FFF3E0"),
}

test_cases = [
    {"id":"TC-01","nombre":"Carga Lineal (motores directos)","load_type":"LINEAR",
     "i_fund":90,"thdi":4.0,"fp":0.85,"cv_pct":8.0,
     "H3":1.0,"H5":3.0,"H7":2.0,"H9":0.5,"H11":1.0,"H13":0.5,
     "notas":"THDi &lt; 5% y H5/H1 &lt; 5% → caso base lineal","color":"LINEAR"},
    {"id":"TC-02","nombre":"Iluminación LED (masiva)","load_type":"LIGHTING",
     "i_fund":60,"thdi":12.0,"fp":0.85,"cv_pct":6.0,
     "H3":40.0,"H5":6.0,"H7":3.0,"H9":2.0,"H11":1.5,"H13":1.0,
     "notas":"THDi &gt; 10%, H3 dominante<br>H5/H1 &lt; 8%, PF ∈ [0.75, 0.95]","color":"LIGHTING"},
    {"id":"TC-03","nombre":"Minería Cripto (SMPS con PFC)","load_type":"CRYPTO_MINING",
     "i_fund":170,"thdi":42.0,"fp":0.985,"cv_pct":0.8,
     "H3":3.0,"H5":35.0,"H7":22.0,"H9":8.0,"H11":7.0,"H13":5.0,
     "notas":"CV &lt; 5%, THDi &gt; 15%<br>H5/H1 &gt; 15%, H7/H1 &gt; 10%<br>FP &gt; 0.92","color":"CRYPTO_MINING"},
    {"id":"TC-04","nombre":"Centro de Datos (PFC parcial)","load_type":"DATA_CENTER",
     "i_fund":150,"thdi":20.0,"fp":0.88,"cv_pct":1.5,
     "H3":5.0,"H5":28.0,"H7":18.0,"H9":6.0,"H11":4.0,"H13":2.0,
     "notas":"CV &lt; 5%, THDi &gt; 15%<br>H5/H1 &gt; 15%, H7/H1 &gt; 10%<br>FP ≤ 0.92 (distingue de cripto)","color":"DATA_CENTER"},
    {"id":"TC-05","nombre":"Industrial 6-Pulsos (VFD)","load_type":"INDUSTRIAL",
     "i_fund":130,"thdi":26.0,"fp":0.93,"cv_pct":9.0,
     "H3":2.0,"H5":25.0,"H7":11.0,"H9":1.0,"H11":9.0,"H13":8.0,
     "notas":"THDi &gt; 8%<br>H5 &gt; 12%, H7 &gt; 8%<br>H11 &gt; 5%, H13 &gt; 4%<br>CV &gt; 5% (no estable)","color":"INDUSTRIAL"},
    {"id":"TC-06","nombre":"Electrónica Ligera (UPS / cargadores)","load_type":"ELECTRONIC_LIGHT",
     "i_fund":80,"thdi":10.0,"fp":0.88,"cv_pct":5.5,
     "H3":15.0,"H5":10.0,"H7":4.0,"H9":2.0,"H11":1.0,"H13":0.5,
     "notas":"THDi &gt; 8%<br>H5/H1 &gt; 8% ó H7/H1 &gt; 5%<br>NO firma 6-pulsos completa","color":"ELECTRONIC_LIGHT"},
    {"id":"TC-07","nombre":"Mixta Electrónica (comercial diurno)","load_type":"MIXED_ELECTRONIC",
     "i_fund":100,"thdi":7.0,"fp":0.91,"cv_pct":7.0,
     "H3":12.0,"H5":6.0,"H7":4.0,"H9":1.5,"H11":1.0,"H13":0.5,
     "notas":"5% &lt; THDi &lt; 15%<br>NO firma específica → mixta","color":"MIXED_ELECTRONIC"},
    {"id":"TC-08<br><small>(frontera)</small>","nombre":"Frontera Lineal / Mixta","load_type":"MIXED_ELECTRONIC",
     "i_fund":80,"thdi":5.2,"fp":0.89,"cv_pct":10.0,
     "H3":4.0,"H5":3.5,"H7":2.0,"H9":1.0,"H11":0.5,"H13":0.3,
     "notas":"THDi justo sobre 5%<br>→ MIXED (no LINEAR)<br>Validar frontera","color":"MIXED_ELECTRONIC"},
    {"id":"TC-09<br><small>(frontera)</small>","nombre":"Frontera Cripto / Datacenter","load_type":"CRYPTO_MINING",
     "i_fund":160,"thdi":22.0,"fp":0.93,"cv_pct":2.0,
     "H3":4.0,"H5":22.0,"H7":14.0,"H9":6.0,"H11":4.0,"H13":2.5,
     "notas":"FP = 0.93 (justo &gt; 0.92)<br>→ CRYPTO (no DATA_CENTER)<br>Si FP = 0.91 → DATA_CENTER","color":"CRYPTO_MINING"},
    {"id":"TC-10<br><small>(negativo)</small>","nombre":"Rechazo: Lineal con ruido armónico","load_type":"LINEAR",
     "i_fund":80,"thdi":3.5,"fp":0.90,"cv_pct":12.0,
     "H3":1.0,"H5":2.5,"H7":1.5,"H9":0.5,"H11":0.5,"H13":0.3,
     "notas":"THDi &lt; 5%, H5/H1 &lt; 5%<br>→ debe clasificar LINEAR<br>aun con ruido de red","color":"LINEAR"},
]

def p_kw(tc):
    return round(13.28 * tc["i_fund"] * 1.732 * tc["fp"] / 1000, 1)

def angle(fp):
    return round(math.degrees(math.acos(min(fp, 1.0))), 1)

HTML = f"""<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>HarmonicMonitor — Tabla de Validacion</title>
<style>
  @page {{
    size: A4 landscape;
    margin: 15mm 12mm 15mm 12mm;
  }}
  @media print {{
    .pagebreak {{ page-break-before: always; }}
    .noprint {{ display: none; }}
  }}
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    font-family: 'Segoe UI', Arial, sans-serif;
    font-size: 9pt;
    line-height: 1.45;
    color: #212121;
    background: #fff;
    max-width: 277mm;
    margin: 0 auto;
    padding: 8mm 10mm;
  }}
  .header-band {{
    background: #0D3B66;
    color: #fff;
    padding: 7px 14px;
    border-radius: 5px;
    margin-bottom: 14px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }}
  .header-band h1 {{ font-size: 11pt; font-weight: bold; }}
  .header-band .meta {{ font-size: 8pt; opacity: 0.85; }}

  h2 {{
    font-size: 10.5pt;
    color: #0D3B66;
    border-left: 4px solid #1565C0;
    padding-left: 9px;
    margin: 14px 0 7px;
  }}
  h2.teal {{ color: #00695C; border-color: #00695C; }}
  h2.grey  {{ color: #424242; border-color: #757575; }}

  p {{ margin-bottom: 6px; font-size: 8.5pt; }}
  .note {{ font-size: 7.5pt; color: #555; font-style: italic; margin: 5px 0 8px; }}

  table {{
    width: 100%;
    border-collapse: collapse;
    margin: 6px 0 10px;
    font-size: 8pt;
  }}
  th {{
    padding: 5px 6px;
    text-align: left;
    color: #fff;
    font-weight: bold;
    white-space: nowrap;
  }}
  th.blue   {{ background: #1976D2; }}
  th.teal   {{ background: #00695C; }}
  th.grey   {{ background: #424242; }}
  th.navy   {{ background: #0D3B66; }}
  td {{
    padding: 4px 6px;
    border-bottom: 1px solid #E0E0E0;
    vertical-align: top;
  }}
  tr:nth-child(even) td {{ background: #F5F5F5; }}
  td.center {{ text-align: center; }}
  td.mono {{ font-family: 'Courier New', monospace; font-size: 7.5pt; }}
  td.bold {{ font-weight: bold; }}

  .badge {{
    display: inline-block;
    border-radius: 3px;
    padding: 1px 6px;
    font-size: 7.5pt;
    font-weight: bold;
    color: #fff;
    white-space: nowrap;
  }}

  .sep-row td {{
    border-top: 2px solid #E65100 !important;
    background: #FFF8E1 !important;
  }}

  sub {{ font-size: 72%; vertical-align: sub; }}
  sup {{ font-size: 72%; vertical-align: super; }}

  .footer {{
    text-align: center;
    font-size: 7pt;
    color: #888;
    margin-top: 18px;
    padding-top: 6px;
    border-top: 1px solid #ddd;
  }}
  .step-num {{
    width: 24px;
    height: 24px;
    background: #0D3B66;
    color: #fff;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
    font-size: 10pt;
  }}
</style>
</head>
<body>

<div class="header-band">
  <h1>HarmonicMonitor v1.0 — Tabla de Entradas para Validacion del Algoritmo de Deteccion de Carga</h1>
  <div class="meta">Rev. 1.0 &nbsp;|&nbsp; {FECHA} &nbsp;|&nbsp; CONFIDENCIAL — Uso interno de pruebas</div>
</div>

<p>Inyeccion de perfiles armonicos desde Patron de Energia al medidor ION 7400 — Alimentador MT 23 kV</p>

<!-- ═══════════════════════════════════════════════════
     SECCION 1: Configuracion de la Aplicacion
═══════════════════════════════════════════════════ -->
<h2>1. Configuracion de la Aplicacion — HarmonicMonitor</h2>
<table>
  <tr>
    <th class="blue" style="width:16%">Parametro</th>
    <th class="blue" style="width:12%">Valor a configurar</th>
    <th class="blue" style="width:38%">Descripcion</th>
    <th class="blue" style="width:34%">Donde configurar (GUI)</th>
  </tr>
  <tr><td>Host ION 7400</td><td class="bold">169.254.0.10</td>
      <td>IP del medidor en red local (link-local)</td>
      <td>Gestion de Alimentadores → Boton ION 7400</td></tr>
  <tr><td>Puerto MMS</td><td class="bold">102</td>
      <td>Puerto estandar IEC 61850 MMS</td>
      <td>Gestion de Alimentadores → Puerto</td></tr>
  <tr><td>Nombre IED (iedName)</td><td class="bold">cbo2</td>
      <td>Nombre del IED en el modelo SCL del ION 7400</td>
      <td>Gestion de Alimentadores → Nombre IED</td></tr>
  <tr><td>Instancia LD (ldInst)</td><td class="bold">LD0</td>
      <td>Instancia del Logical Device en el ION 7400</td>
      <td>Gestion de Alimentadores → LD Inst</td></tr>
  <tr><td>Prefijo MMXU (mmxuPrefix)</td><td class="bold">M03_</td>
      <td>Prefijo del LN MMXU en el ION 7400 (cbo2LD0/M03_MMXU1)</td>
      <td>Gestion de Alimentadores → Prefijo MMXU</td></tr>
  <tr><td>LN MHAI (armonicos)</td><td class="bold">MHAI1</td>
      <td>Logical Node con ThdA, ThdPPV, HKf, harmonics[H1..H50]</td>
      <td>Gestion de Alimentadores → LN MHAI</td></tr>
  <tr><td>Intervalo de poll</td><td class="bold">5 000 ms</td>
      <td>Lectura cada 5 s durante la prueba</td>
      <td>Gestion de Alimentadores → Intervalo (ms)</td></tr>
  <tr style="border-top: 1.5px solid #1565C0;">
      <td>Tension nominal (kV)</td><td class="bold">23 kV</td>
      <td>Tension linea-linea del alimentador MT</td>
      <td>Gestion de Alimentadores → Tension nominal</td></tr>
  <tr><td>Corriente nominal (A)</td><td class="bold">200 A</td>
      <td>I<sub>n</sub> del alimentador (referencia de carga)</td>
      <td>Gestion de Alimentadores → Corriente nominal</td></tr>
  <tr><td>Capacitancia feeder (µF)</td><td class="bold">5.0 µF</td>
      <td>Para calculo de frecuencia de resonancia</td>
      <td>Gestion de Alimentadores → Capacitancia</td></tr>
  <tr><td>Scc en barra (MVA)</td><td class="bold">100 MVA</td>
      <td>Potencia de cortocircuito (para Z del sistema)</td>
      <td>Gestion de Alimentadores → Scc</td></tr>
</table>

<!-- ═══════════════════════════════════════════════════
     SECCION 2: Umbrales del algoritmo
═══════════════════════════════════════════════════ -->
<h2 class="teal">2. Umbrales del Algoritmo de Deteccion (FeederConfig)</h2>
<table>
  <tr>
    <th class="teal" style="width:22%">Parametro</th>
    <th class="teal" style="width:10%">Valor</th>
    <th class="teal" style="width:30%">Variable Java</th>
    <th class="teal" style="width:38%">Descripcion</th>
  </tr>
  <tr><td>CV max. carga electronica</td><td class="bold center">5 %</td>
      <td class="mono">maxCvElectronicThreshold = 0.05</td>
      <td>Coef. de Variacion (σ/μ) de corriente &lt; 5% → carga estable</td></tr>
  <tr><td>THD<sub>i</sub> min. cripto/datacenter</td><td class="bold center">15 %</td>
      <td class="mono">minThdICryptoThreshold = 15.0</td>
      <td>THD de corriente minimo para clasificar como cripto o datacenter</td></tr>
  <tr><td>H5/H1 min. firma rectificador</td><td class="bold center">15 %</td>
      <td class="mono">minH5h1CryptoThreshold = 0.15</td>
      <td>Ratio 5° armonico / fundamental &gt; 15%</td></tr>
  <tr><td>H7/H1 min. firma rectificador</td><td class="bold center">10 %</td>
      <td class="mono">minH7h1CryptoThreshold = 0.10</td>
      <td>Ratio 7° armonico / fundamental &gt; 10%</td></tr>
  <tr><td>FP alto (distingue cripto vs DC)</td><td class="bold center">0.92</td>
      <td class="mono">highPF hardcoded = 0.92</td>
      <td>FP &gt; 0.92 → SMPS con PFC activo → CRYPTO_MINING; ≤ 0.92 → DATA_CENTER</td></tr>
  <tr><td>THD<sub>i</sub> min. lineal → mixta</td><td class="bold center">5 %</td>
      <td class="mono">hardcoded = 5.0 %</td>
      <td>THD<sub>i</sub> &gt; 5% es requisito minimo para MIXED_ELECTRONIC</td></tr>
  <tr><td>Firma 6-pulsos (industrial)</td><td class="bold center">H5&gt;12% H7&gt;8% H11&gt;5% H13&gt;4%</td>
      <td class="mono">sixPulseSignature (hardcoded)</td>
      <td>Requiere adicionalmente THD<sub>i</sub> &gt; 8%</td></tr>
  <tr><td>THD tension max. (calidad)</td><td class="bold center">5 %</td>
      <td class="mono">maxThdVoltagePct = 5.0</td>
      <td>Limite IEC 61000-3-6 — genera alarma si se supera</td></tr>
  <tr><td>Amplif. resonancia max.</td><td class="bold center">3×</td>
      <td class="mono">resonanceAmplificationMax = 3.0</td>
      <td>Si I<sub>resonancia</sub> &gt; 3×I<sub>fund</sub> → alarma RESONANCE_RISK</td></tr>
</table>

<!-- ═══════════════════════════════════════════════════
     SECCION 3: Casos de prueba (PAGINA 2)
═══════════════════════════════════════════════════ -->
<div class="pagebreak"></div>

<div class="header-band">
  <h1>HarmonicMonitor v1.0 — Casos de Prueba</h1>
  <div class="meta">Rev. 1.0 &nbsp;|&nbsp; {FECHA} &nbsp;|&nbsp; CONFIDENCIAL</div>
</div>

<h2>3. Casos de Prueba — Configuracion del Patron de Energia</h2>
<p class="note">
  Cada fila define un escenario a programar en el patron. Los valores de corriente estan sobre base I<sub>n</sub> = 200 A (feeder MT 23 kV).
  Tension de prueba: 13 280 V (fase-neutro, derivada de 23 kV / √3). Frecuencia fundamental: 50 Hz.
  Las columnas H5/H1 y H7/H1 (<strong>negrita</strong>) son los discriminadores principales.
</p>

<table>
  <tr>
    <th class="navy" style="width:5%">ID</th>
    <th class="navy" style="width:13%">Escenario</th>
    <th class="navy" style="width:11%">Clasificacion<br>esperada</th>
    <th class="navy" style="width:4%">I<sub>fund</sub><br>(A)</th>
    <th class="navy" style="width:5%">THD<sub>i</sub><br>(%)</th>
    <th class="navy" style="width:4%">FP</th>
    <th class="navy" style="width:4%">CV<br>(%)</th>
    <th class="navy" style="width:4%">H3/H1<br>(%)</th>
    <th class="navy" style="width:4%"><strong>H5/H1</strong><br>(%)</th>
    <th class="navy" style="width:4%"><strong>H7/H1</strong><br>(%)</th>
    <th class="navy" style="width:4%">H9/H1<br>(%)</th>
    <th class="navy" style="width:4%">H11/H1<br>(%)</th>
    <th class="navy" style="width:4%">H13/H1<br>(%)</th>
    <th class="navy" style="width:30%">Condiciones para PASS</th>
  </tr>
"""

for i, tc in enumerate(test_cases):
    ct, cb = LOAD_COLORS[tc["color"]]
    sep = ' class="sep-row"' if i == 7 else ''
    HTML += f"""  <tr{sep}>
    <td class="center" style="font-weight:bold">{tc['id']}</td>
    <td>{tc['nombre']}</td>
    <td class="center"><span class="badge" style="background:{ct}">{tc['load_type']}</span></td>
    <td class="center">{tc['i_fund']}</td>
    <td class="center bold">{tc['thdi']:.1f}</td>
    <td class="center">{tc['fp']:.3f}</td>
    <td class="center">{tc['cv_pct']:.1f}</td>
    <td class="center">{tc['H3']:.1f}</td>
    <td class="center bold">{tc['H5']:.1f}</td>
    <td class="center bold">{tc['H7']:.1f}</td>
    <td class="center">{tc['H9']:.1f}</td>
    <td class="center">{tc['H11']:.1f}</td>
    <td class="center">{tc['H13']:.1f}</td>
    <td style="font-size:7.5pt;color:#444">{tc['notas']}</td>
  </tr>
"""

HTML += """</table>
<p class="note">
  * La linea naranja separa los casos de frontera y negativo (TC-08/09/10).
  El CV se obtiene del historial de corriente (ventana de 60 muestras = 5 min @ 5 s/poll).
</p>

<!-- ═══════════════════════════════════════════════════
     SECCION 4: Programacion del patron (PAGINA 3)
═══════════════════════════════════════════════════ -->
<div class="pagebreak"></div>

<div class="header-band">
  <h1>HarmonicMonitor v1.0 — Programacion del Patron de Energia</h1>
  <div class="meta">Rev. 1.0 &nbsp;|&nbsp; """ + FECHA + """ &nbsp;|&nbsp; CONFIDENCIAL</div>
</div>

<h2>4. Programacion del Patron de Energia — Detalle por Caso</h2>
<p class="note">
  Parametros a ingresar en el patron para cada caso. Tension fija: V<sub>LL</sub> = 23 kV → V<sub>LN</sub> = 13 280 V.
  Armonicos en % respecto al fundamental. Angulo I-V calculado como arccos(FP).
</p>
<table>
  <tr>
    <th class="navy" style="width:4.5%">ID</th>
    <th class="navy" style="width:9%">Escenario</th>
    <th class="navy" style="width:5%">V<sub>LN</sub> (V)</th>
    <th class="navy" style="width:4.5%">I<sub>fund</sub> (A)</th>
    <th class="navy" style="width:5%">Angulo I-V (°)</th>
    <th class="navy" style="width:4.5%">THD<sub>i</sub> (%)</th>
    <th class="navy" style="width:3.5%">H2 (%)</th>
    <th class="navy" style="width:3.5%">H3 (%)</th>
    <th class="navy" style="width:3.5%">H4 (%)</th>
    <th class="navy" style="width:3.5%">H5 (%)</th>
    <th class="navy" style="width:3.5%">H6 (%)</th>
    <th class="navy" style="width:3.5%">H7 (%)</th>
    <th class="navy" style="width:3.5%">H8 (%)</th>
    <th class="navy" style="width:3.5%">H9 (%)</th>
    <th class="navy" style="width:3.5%">H10 (%)</th>
    <th class="navy" style="width:3.5%">H11 (%)</th>
    <th class="navy" style="width:3.5%">H13 (%)</th>
    <th class="navy" style="width:5%">P aprox (kW)</th>
    <th class="navy" style="width:5%">Duracion (min)</th>
  </tr>
"""

pat_data = [
    ("TC-01","Lineal",         0,  1.0,0, 3.0,0, 2.0,0, 0.5,0, 1.0,  0.5, 10),
    ("TC-02","Iluminacion",    0, 40.0,0, 6.0,0, 3.0,0, 2.0,0, 1.5,  1.0, 10),
    ("TC-03","Cripto-Mining",  0,  3.0,0,35.0,0,22.0,0, 8.0,0, 7.0,  5.0, 15),
    ("TC-04","Datacenter",     0,  5.0,0,28.0,0,18.0,0, 6.0,0, 4.0,  2.0, 15),
    ("TC-05","VFD 6-pulsos",   0,  2.0,0,25.0,0,11.0,0, 1.0,0, 9.0,  8.0, 15),
    ("TC-06","Electr. Ligera", 0, 15.0,0,10.0,0, 4.0,0, 2.0,0, 1.0,  0.5, 10),
    ("TC-07","Mixta Comerc.",  0, 12.0,0, 6.0,0, 4.0,0, 1.5,0, 1.0,  0.5, 10),
    ("TC-08","Front. Lin/Mix", 0,  4.0,0, 3.5,0, 2.0,0, 1.0,0, 0.5,  0.3,  5),
    ("TC-09","Front. Cripto",  0,  4.0,0,22.0,0,14.0,0, 6.0,0, 4.0,  2.5,  5),
    ("TC-10","Neg. Lineal",  0.5,  1.0,0.3,2.5,0,1.5,0, 0.5,0, 0.5,  0.3,  5),
]

for i, (tc, pat) in enumerate(zip(test_cases, pat_data)):
    ct, cb = LOAD_COLORS[tc["color"]]
    _, _, h2, h3, h4, h5, h6, h7, h8, h9, h10, h11, h13, dur = pat
    ang = angle(tc["fp"])
    pw  = p_kw(tc)
    sep = ' class="sep-row"' if i == 7 else ''
    HTML += f"""  <tr{sep} style="background:{cb}">
    <td class="center bold">{pat[0]}</td>
    <td>{pat[1]}</td>
    <td class="center">13 280</td>
    <td class="center bold">{tc['i_fund']}</td>
    <td class="center">{ang}</td>
    <td class="center bold">{tc['thdi']:.1f}</td>
    <td class="center">{h2}</td>
    <td class="center">{h3:.1f}</td>
    <td class="center">{h4}</td>
    <td class="center bold">{h5:.1f}</td>
    <td class="center">0</td>
    <td class="center bold">{h7:.1f}</td>
    <td class="center">0</td>
    <td class="center">{h9:.1f}</td>
    <td class="center">0</td>
    <td class="center">{h11:.1f}</td>
    <td class="center">{h13:.1f}</td>
    <td class="center">{pw}</td>
    <td class="center">{dur}</td>
  </tr>
"""

HTML += """</table>

<h2 class="grey">5. Procedimiento de Prueba</h2>
<table>
  <tr>
    <th class="grey" style="width:4%">Paso</th>
    <th class="grey" style="width:12%">Accion</th>
    <th class="grey" style="width:54%">Detalle</th>
    <th class="grey" style="width:30%">Criterio de exito</th>
  </tr>
  <tr>
    <td class="center bold" style="color:#0D3B66;font-size:13pt">1</td>
    <td><strong>Conectar equipo</strong></td>
    <td>Patron → TC de corriente → entrada I del ION 7400.
        Generador de tension → borneras V del ION 7400 (o usar tension de red si el patron lo soporta).</td>
    <td>ION 7400 enciende y responde ping en 169.254.0.10</td>
  </tr>
  <tr>
    <td class="center bold" style="color:#0D3B66;font-size:13pt">2</td>
    <td><strong>Configurar app</strong></td>
    <td>Abrir HarmonicMonitor → Gestion de Alimentadores → Boton 'ION 7400 Preset'.
        Verificar: host=169.254.0.10, iedName=cbo2, mmxuPrefix=M03_, ldInst=LD0, V<sub>nom</sub>=23 kV.</td>
    <td>Panel de alimentadores muestra el feeder configurado</td>
  </tr>
  <tr>
    <td class="center bold" style="color:#0D3B66;font-size:13pt">3</td>
    <td><strong>Conectar IEC 61850</strong></td>
    <td>Boton 'Conectar'. Esperar confirmacion de conexion MMS.
        Verificar que la app recibe lecturas (valores distintos de 0 en dashboard).</td>
    <td>Indicador de estado = CONECTADO, datos validos en dashboard</td>
  </tr>
  <tr>
    <td class="center bold" style="color:#0D3B66;font-size:13pt">4</td>
    <td><strong>Ejecutar caso de prueba</strong></td>
    <td>Programar el patron con los valores de la Seccion 4 para el TC correspondiente.
        Inyectar señal durante al menos <strong>5 min</strong> (60 ciclos de poll × 5 s) para estabilizar CV.
        Observar la etiqueta de clasificacion en el dashboard.</td>
    <td>La clasificacion mostrada coincide con la columna 'Clasificacion esperada' de la Seccion 3</td>
  </tr>
  <tr>
    <td class="center bold" style="color:#0D3B66;font-size:13pt">5</td>
    <td><strong>Registrar resultado</strong></td>
    <td>Anotar: LoadType mostrado, THD<sub>i</sub> leido, H5/H1, H7/H1, CV, FP, indice de electronica (0-100).
        Comparar con umbrales de la Seccion 2. Exportar CSV si se requiere evidencia.</td>
    <td>PASS: LoadType == esperado.<br>FAIL: LoadType difiere → revisar umbrales en FeederConfig.</td>
  </tr>
  <tr>
    <td class="center bold" style="color:#0D3B66;font-size:13pt">6</td>
    <td><strong>Prueba de alarmas</strong></td>
    <td>Para TC-03 y TC-05: verificar alarma en panel de Alarmas (CRITICAL para CRYPTO_MINING, WARNING para INDUSTRIAL).
        Para TC-09 frontera: ajustar FP a 0.91 y verificar cambio a DATA_CENTER.</td>
    <td>Alarmas aparecen con nivel y timestamp correcto</td>
  </tr>
</table>

<div class="footer">
  Documento generado automaticamente | HADES v1.0 — Paraguay |
  Emilio Medina, Diego Rojas, Enrique Paiva, Sergio Dominguez | """ + FECHA + """
</div>

</body>
</html>
"""

with open(OUTPUT, "w", encoding="utf-8") as f:
    f.write(HTML)

print(f"HTML generado: {OUTPUT}")
print("Abrir en Chrome/Edge -> Ctrl+P -> Guardar como PDF")
print("Configuracion: Tamano A4 Horizontal, Margenes Minimo, Graficos de fondo: SI")
