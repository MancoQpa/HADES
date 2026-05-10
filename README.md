# HADES

**H**armonic **A**nalysis for **D**etection of **E**lectronic **S**ignatures

Herramienta de escritorio Java/JavaFX para **caracterización espectral de calidad de energía** en alimentadores de media tensión (23 kV) mediante el protocolo **IEC 61850**.

Registra el espectro armónico H1–H50, THD de tensión y corriente, secuencias simétricas y potencia activa/reactiva/aparente a través de medidores compatibles (ION 7400 y similares). Incluye un clasificador de firma espectral basado en umbrales bibliográficos (IEEE 519-2022, IEC 61000-3-6, IEC 61000-3-12) validados en laboratorio con patrones de energía replicados; la clasificación opera en pocos ciclos de red y fue exitosa para cargas individuales en condiciones de laboratorio.

La caracterización espectral se realiza mediante capturas de espectro completo cada 60 segundos, acumuladas en una base de datos SQLite local. Esta colección continua está diseñada para alimentar en el futuro un modelo de aprendizaje automático que esperamos sea capaz de identificar patrones de carga en condiciones de campo reales, incluyendo composiciones mixtas de usuarios.

> **Nota de alcance**: para cargas **solapadas** (varios usuarios en el mismo feeder), la firma espectral resultante es la superposición de todas ellas. La clasificación es confiable solo cuando la carga de interés representa ≥80% de la demanda total del alimentador. Los umbrales del clasificador fueron replicados en laboratorio con patrones controlados; no sustituyen una campaña de medición en campo bajo IEC 61000-4-30.

De uso libre bajo licencia GPL v3.

Desarrollado por **Emilio Medina**.

---

## Características

### Monitor de armónicos
- Conexión MMS/ACSE a medidores IEC 61850 (ION 7400 y compatibles)
- Lectura de nodos lógicos MMXU, MHAI, MSQI, MMTR, MSTA
- Espectro de armónicos hasta el orden 50 (fases A, B, C)
- THD de tensión y corriente
- Secuencias simétricas (positiva, negativa, cero)
- Energía activa, reactiva y aparente (BCR, FC=ST)
- Demanda máxima y factor de potencia

### Clasificación de firma espectral
- Árbol de decisión multivariable: CV = σ(I)/μ(I), THD_I, H5/H1, H7/H1, H11/H1, FP
- Patrones implementados: carga lineal, iluminación LED, SMPS alta densidad, rectificador 6-pulsos, electrónica ligera, mixta
- Clasificación en pocos ciclos de red; validada en laboratorio con patrones de energía replicados
- Índice de electrónica 0–100 (score compuesto)
- Análisis de resonancia LC por feeder
- Requiere dominancia ≥80% en el feeder para cargas solapadas

### Acumulación de datos para ML
- Captura de espectro completo H1–H50 cada 60 segundos por alimentador
- Registro continuo en SQLite: sesiones, mediciones, espectros, alarmas
- Exportación CSV compatible con pipelines de aprendizaje automático
- Diseñado para construir datasets de caracterización espectral en campo, como base de un modelo ML futuro de identificación de patrones de carga

### Multi-alimentador
- Monitoreo simultáneo de hasta N alimentadores
- Panel consolidado con semáforos de estado por alimentador

### Almacenamiento y exportación
- Base de datos SQLite local (sesiones, mediciones, alarmas)
- Exportación CSV por rango de tiempo
- Visor COMTRADE con gráfico de forma de onda y FFT

### Simulación interna
- Modo Demo integrado: genera perfiles realistas sin hardware conectado
- Ocho perfiles de carga: `crypto_mining`, `linear_load`, `data_center`, `industrial`,
  `lighting`, `mixed_electronic`, `normal_load`, `electronic_light`

---

## Requisitos previos

### 1. Java 17 o superior
Descarga gratuita: https://adoptium.net
O la distribución Liberica JRE 17 (https://bell-sw.com/pages/downloads/)

### 2. JavaFX 17
Incluido en el ejecutable distribuido (jpackage con JRE embebido).
Para desarrollo: https://gluonhq.com/products/javafx/

### 3. Medidor compatible IEC 61850 (opcional)
- ION 7400: Host `169.254.0.10`, Puerto `102`, IED `cbo2`, Prefijo MMXU `M03_`, LD `LD0`
- Cualquier IED que exponga MMXU / MHAI / MSQI / MMTR vía MMS

Sin hardware, usar el **Modo Demo** integrado.

---

## Instalación y ejecución

### Opción A — Ejecutable Windows (distribución)
Descargar `HADES_v1.0_Windows.rar` de la sección Releases, extraer y ejecutar:

    HarmonicMonitor.exe

No requiere Java instalado (JRE embebido).

### Opción B — Maven

    mvn clean package -DskipTests
    java --module-path /ruta/javafx-sdk-17/lib --add-modules javafx.controls,javafx.fxml \
         -jar target/harmonic-monitor-1.0.0-jar-with-dependencies.jar

### Opción C — Scripts de compilación (Windows)

    .\compile_ps2.ps1       # compila fuentes
    .\run.bat               # ejecuta

---

## Estructura del proyecto

```
src/main/java/com/harmonicmonitor/
  app/                     # Punto de entrada (HarmonicMonitorApp.java)
  comm/                    # Comunicación IEC 61850 (IEC61850Communicator, MeasurementPoller)
  analysis/                # Análisis armónico (HarmonicAnalyzer, ElectronicLoadDetector,
  |                        #   ResonanceAnalyzer, SpectralRecorder)
  alarm/                   # Motor de alarmas (AlarmEngine)
  storage/                 # Persistencia SQLite y exportación CSV (DataStorage)
  simulator/               # Simulación interna (IonSimServer, SimulatorLauncher)
  comtrade/                # Parser y visor COMTRADE
  gui/                     # Paneles JavaFX (MultiFeederMonitorPanel, HarmonicsPanel,
                           #   DiscoveryPanel, FeederMgmtPanel, ComtradePanel, ...)
simulator/
  generic_meter_sim.cid    # Archivo SCL del simulador IEC 61850
  templates/               # Perfiles JSON de carga (8 perfiles)
```

---

## Conexión al ION 7400

| Parámetro   | Valor           |
|-------------|-----------------|
| Host        | 169.254.0.10    |
| Puerto      | 102             |
| IED Name    | cbo2            |
| Prefijo MMXU| M03_            |
| LD Instance | LD0             |

---

## Licencia

HADES se distribuye bajo la **GNU General Public License v3**.
Ver archivo [LICENSE](LICENSE) para el texto completo.

Las dependencias de terceros tienen sus propias licencias.
Ver [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) para el detalle.
