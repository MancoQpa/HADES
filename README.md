# HADES

**H**armonic **A**nalysis for **D**etection of **E**lectronic **S**ignatures

Herramienta de escritorio Java/JavaFX para monitoreo de calidad de energía en alimentadores de media tensión (23 kV) mediante el protocolo **IEC 61850**.
Detecta automáticamente cargas electrónicas no lineales (cripto-minería, centros de datos, variadores de velocidad) a partir del análisis espectral de armónicos de corriente.

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

### Detección de carga electrónica
- Árbol de decisión basado en: coeficiente de variación de corriente (CV = σ/μ), THD_I, H5/H1, H7/H1
- Perfiles identificados: cripto-minería, centro de datos, variador de velocidad, iluminación electrónica, carga lineal
- Nivel de confianza por clasificación
- Análisis de resonancia (frecuencia de resonancia LC)

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
  ion7400sim.cid           # Archivo SCL del simulador IEC 61850
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
