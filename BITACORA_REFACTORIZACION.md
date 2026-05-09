# Bitácora de Refactorización — HADES v1.0

> Cada entrada documenta UN cambio atómico. En caso de fallo, revertir en orden inverso.
> Tag de baseline: `refactor-baseline-v1.0`

---

## Cómo hacer rollback de cualquier paso

```bash
# Opción A — restaurar archivo específico desde baseline
git checkout refactor-baseline-v1.0 -- src/main/java/com/harmonicmonitor/ruta/Archivo.java

# Opción B — eliminar archivo nuevo si fue creado
rm src/main/java/com/harmonicmonitor/ruta/ArchivoNuevo.java

# Opción C — rollback completo al baseline
git checkout refactor-baseline-v1.0

# Recompilar para verificar
cd HarmonicMonitor
.\compile_ps2.ps1
```

---

## FASE 0 — Baseline y verificación

### [F0-001] Snapshot baseline + verificación de compilación
- **Fecha**: 2026-05-07
- **Tipo**: Preparación (sin cambio funcional)
- **Acción**: `git tag refactor-baseline-v1.0`
- **Estado de compilación baseline**:
  - Archivos fuente: 39 clases Java
  - Archivo más grande: HelpPanel.java (1408 líneas)
  - Total líneas código: ~13.800
- **Rollback**: `git checkout refactor-baseline-v1.0`
- **Compilación baseline**: 42 clases, 0 errores, notas unchecked (pre-existentes)
- **Estado**: ✅ Aplicado

---

## FASE 1 — Extracción de contenido estático a recursos

### Objetivo
`HelpPanel.java` (1408 líneas) contiene HTML hardcodeado como strings Java.
Moverlo a `src/main/resources/com/harmonicmonitor/help/*.html`
y cargarlo con `getClass().getResourceAsStream()`.

**Meta**: HelpPanel.java → ~150 líneas de Java puro.

### [F1-001] Crear carpeta de recursos help/ + extraer 21 topics de HelpPanel
- **Fecha**: 2026-05-07
- **Análisis**: 1238 de 1408 líneas son el array TOPICS (78% contenido puro)
  - Líneas 22-1259: array TOPICS con 21 entradas de texto
  - Líneas 1261-1407: lógica Java real (buildUI, toHtml, escHtml, helpers) — SE MANTIENEN
- **Archivos nuevos**:
  - `src/main/resources/com/harmonicmonitor/help/help_01_disclaimer.txt` (157 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_02_dashboard.txt` (26 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_03_harmonics.txt` (28 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_04_multifeeder.txt` (22 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_05_trends.txt` (15 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_06_alarms.txt` (24 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_07_feeders.txt` (20 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_08_standards.txt` (19 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_09_load_detection.txt` (72 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_10_iec61850.txt` (21 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_11_fundamentals_harmonics.txt` (94 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_12_fundamentals_fortescue.txt` (53 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_13_model_crypto.txt` (74 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_14_model_datacenter.txt` (43 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_15_model_arc_furnace.txt` (60 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_16_model_vfd.txt` (47 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_17_model_industrial.txt` (56 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_18_comtrade_usage.txt` (149 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_19_comtrade_fundamentals.txt` (85 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_20_comtrade_generation.txt` (129 líneas)
  - `src/main/resources/com/harmonicmonitor/help/help_21_about_hades.txt` (39 líneas)
- **Archivo modificado**: `src/main/java/com/harmonicmonitor/gui/HelpPanel.java`
  - Array TOPICS: 1238 líneas → 25 líneas (21 entradas `loadResource()`)
  - Nuevo método `loadResource(String)` — ~10 líneas
  - Resultado: 1408 → ~140 líneas (reducción 90%)
- **Patrón**: `private static String loadResource(String name)` con `getResourceAsStream`
- **Rollback**:
  ```bash
  git checkout refactor-baseline-v1.0 -- src/main/java/com/harmonicmonitor/gui/HelpPanel.java
  rm -rf src/main/resources/com/harmonicmonitor/help/
  ```
- **Verificación**: Compilar + abrir pestaña Ayuda → 21 secciones visibles con contenido correcto
- **Resultado**: HelpPanel.java 1408 → 204 líneas (85% reducción); 21 archivos .txt en resources/
- **Estado**: ✅ Aplicado

---

## FASE 2 — Extracción de MmsDataMapper

### Objetivo
`IEC61850Communicator.java` (1007 líneas) mezcla:
- Gestión de sesión MMS (conexión, reconexión, polling)
- Mapeo de refs IEC 61850 → objetos Java (`readMxFloat`, `readBcr`, etc.)
- 29 bloques `catch(Exception)` genéricos que enmascaran errores

Extraer lógica de mapeo/lectura a `MmsDataMapper.java`.

**Meta**: IEC61850Communicator.java → ~500 líneas; MmsDataMapper.java → ~400 líneas.

### [F2-001] Crear MmsDataMapper.java y mover métodos de lectura BDA
- **Fecha**: 2026-05-07
- **Análisis**: IEC61850Communicator mezcla lifecycle MMS + 9 métodos de mapeo BDA
- **Patrón**: Composición — IEC61850Communicator instancia MmsDataMapper internamente
- **Archivo nuevo**: `src/main/java/com/harmonicmonitor/comm/MmsDataMapper.java`
  - **Campos que se mueven** (50+ refs de String + caches):
    - phsA/B/CPhVRef, phsA/B/CARef (refs voltaje/corriente)
    - wRef, varRef, vaRef, pfRef, hzRef (refs potencia/freq)
    - thdAL1/2/3Ref, thdPpvL12/23/31Ref, kfL1/2/3Ref (refs THD/K-factor)
    - seqApos/neg/Vpos/negRef (refs secuencias)
    - totWhRef, totVAhRef, etc. (refs energía/demanda)
    - haPhsA/B/CHarRef[50] (refs armónicos)
    - harmonicsAvailable, harmonicArrayInModel, powerScaleDetected
    - mxNodeCache, stNodeCache
    - harmonicsPollCounter, cachedHarPhsA/B/C
    - HARMONICS_READ_EVERY_N
  - **Métodos que se mueven** (líneas exactas):
    - `buildMmxuRefs()` (477–702) — construye todas las refs, cachea nodos
    - `cacheNodePair()` (709–736)
    - `cacheStNode()` (741–745)
    - `autoDetectPowerScale()` (758–805) — catch Exception a 801
    - `findMxRef()` (810–819)
    - `readMxFloat()` (830–901) — catch ServiceError en 840, 862, 887
    - `readStInt64()` (907–928)
    - `readHarmonicArray()` (935–944) — catch Exception ignored en 941
    - `dumpMhaiStructure()` + `dumpNode()` (950–974)
  - **Dependencias del mapper hacia communicator**:
    - Necesita `association` (para getDataValues) — inyectado por constructor/setter
    - Necesita `serverModel` — inyectado después de connectInternal()
    - Necesita logger/fireEvent → inyectar Consumer<String> para log
  - **API pública del mapper**:
    - `buildRefs(ServerModel, ClientAssociation)` — inicializa todas las refs
    - `readMeasurement(ClientAssociation)` → `FeederMeasurement`
    - `clearCache()` — llamado por disconnect()
    - `isHarmonicArrayInModel()` — booleano
- **Archivo modificado**: `IEC61850Communicator.java`
  - Eliminar 50+ campos de refs y caches
  - Eliminar 9 métodos de mapeo
  - Agregar `private MmsDataMapper mapper`
  - `readMeasurement()` delega a `mapper.readMeasurement(association)`
  - `connectInternal()` llama `mapper.buildRefs(serverModel, association)`
  - `disconnect()` llama `mapper.clearCache()`
  - Resultado: 1007 → ~430 líneas
- **Rollback**:
  ```bash
  git checkout refactor-baseline-v1.0 -- src/main/java/com/harmonicmonitor/comm/IEC61850Communicator.java
  rm src/main/java/com/harmonicmonitor/comm/MmsDataMapper.java
  ```
- **Resultado**:
  - `MmsDataMapper.java` creado: 789 líneas
  - `IEC61850Communicator.java`: 1007 → 294 líneas (71% reducción)
  - Compilación: ✅ 46 archivos, exit 0
- **Estado**: ✅ Aplicado

---

## FASE 4 — Split de ComtradePanel

### Objetivo
`ComtradePanel.java` (1390 líneas) mezcla UI/layout, chart rendering y DSP.
45 métodos identificados. Extraer renderers de gráficos como builders.

### [F4-001] Extraer WaveformChartBuilder, FftChartBuilder, PhasorDiagramRenderer
- **Fecha**: 2026-05-07
- **Análisis**: 3 métodos de rendering son autocontenidos y pasan datos por parámetros
  - `plotWaveforms()` líneas 714–788 (72 líneas) → `WaveformChartBuilder.render()`
  - `plotFft()` líneas 789–880 (93 líneas) → `FftChartBuilder.render()`
  - `drawPhasors()` líneas 885–1008 (126 líneas) → `PhasorDiagramRenderer.render()`
- **Archivos nuevos**:
  - `WaveformChartBuilder.java` (~150 líneas)
  - `FftChartBuilder.java` (~130 líneas)
  - `PhasorDiagramRenderer.java` (~130 líneas)
- **Archivo modificado**: `ComtradePanel.java` (1390 → ~500 líneas)
- **Patrón**: `analyzeAll()` instancia cada builder con parámetros y llama `render()`
- **Threading**: Todos los builders asumen EDT (JavaFX thread) — OK, llamados desde event handlers
- **Rollback**:
  ```bash
  git checkout refactor-baseline-v1.0 -- src/main/java/com/harmonicmonitor/gui/ComtradePanel.java
  rm src/main/java/com/harmonicmonitor/gui/WaveformChartBuilder.java
  rm src/main/java/com/harmonicmonitor/gui/FftChartBuilder.java
  rm src/main/java/com/harmonicmonitor/gui/PhasorDiagramRenderer.java
  ```
- **Resultado**:
  - `WaveformChartBuilder.java` creado: 168 líneas
  - `FftChartBuilder.java` creado: 147 líneas
  - `PhasorDiagramRenderer.java` creado: 189 líneas
  - `ComtradePanel.java`: 1390 → 1116 líneas (reducción parcial — ver nota)
  - Compilación: ✅ 46 archivos, exit 0
- **Nota**: ComtradePanel quedó en 1116 líneas en lugar de ~500 — los builders existen y compilan, pero la delegación en analyzeAll() puede ser incompleta. Verificar manualmente si plotWaveforms/plotFft/drawPhasors delegan correctamente.
- **Estado**: ✅ Aplicado (compilación OK, verificación funcional pendiente)

---

## FASE 3 — Centralización de hilos (AppExecutors)

### Objetivo
30 instancias de `new Thread()` / `ExecutorService` dispersas en 9 clases.
Sin shutdown ordenado al cerrar la app.

### [F3-001] Crear AppExecutors.java — fábrica de hilos daemon + ioPool compartido
- **Fecha**: 2026-05-07
- **Análisis**:
  - 7 bloques lambda `r -> { Thread t = new Thread(r,name); t.setDaemon(true); return t; }` idénticos
  - 1 resource leak: `Executors.newSingleThreadExecutor().execute(...)` en ComtradePanel (executor no se cierra)
  - Pollers y communicators: schedulers por instancia (ciclo de vida per-feeder) — correcto, se mantienen
  - Simuladores (IonSimServer, SimulatorLauncher): procesos standalone — no tocar
- **Patrón**: Utility class con factory methods — no singleton stateful, pools per-instance se crean igual
- **Archivo nuevo**: `src/main/java/com/harmonicmonitor/AppExecutors.java`
  - `daemonFactory(String name)` → `ThreadFactory` — elimina boilerplate lambda
  - `newDaemonScheduler(String name)` → `ScheduledExecutorService`
  - `newDaemonExecutor(String name)` → `ExecutorService`
  - `ioPool()` → pool compartido 2 hilos para fire-and-forget
  - `shutdownAll()` → cierra ioPool al cerrar app
- **Archivos modificados** (solo reemplazo de lambda factory):
  - `HarmonicMonitorApp.java`: lambda PeriodicTrigger-5min → `AppExecutors.newDaemonScheduler(...)` + `AppExecutors.shutdownAll()` en `shutdown()`
  - `MeasurementPoller.java`: lambda Poller-feederId → `AppExecutors.newDaemonScheduler(...)`
  - `SimulatedPoller.java`: lambda SimPoller-feederId → `AppExecutors.newDaemonScheduler(...)`
  - `IEC61850Communicator.java`: 3 lambdas (IEC61850-feederId×2, Reconnect-feederId) → `AppExecutors.newDaemonExecutor/Scheduler(...)`
  - `RecordsPanel.java`: lambda CharSpectral-5min → `AppExecutors.newDaemonScheduler(...)`
  - `DiscoveryPanel.java`: lambda IED-Discovery → `AppExecutors.newDaemonExecutor(...)`
  - `ComtradePanel.java`: `Executors.newSingleThreadExecutor().execute(...)` → `AppExecutors.ioPool().execute(...)` (fix resource leak)
- **Rollback**:
  ```bash
  rm src/main/java/com/harmonicmonitor/AppExecutors.java
  git checkout refactor-baseline-v1.0 -- \
    src/main/java/com/harmonicmonitor/HarmonicMonitorApp.java \
    src/main/java/com/harmonicmonitor/comm/MeasurementPoller.java \
    src/main/java/com/harmonicmonitor/comm/SimulatedPoller.java \
    src/main/java/com/harmonicmonitor/comm/IEC61850Communicator.java \
    src/main/java/com/harmonicmonitor/gui/RecordsPanel.java \
    src/main/java/com/harmonicmonitor/gui/DiscoveryPanel.java \
    src/main/java/com/harmonicmonitor/gui/ComtradePanel.java
  ```
- **Resultado**:
  - `AppExecutors.java` creado: 70 líneas
  - 7 boilerplate lambdas eliminadas (~35 líneas menos en total)
  - 1 resource leak cerrado (ComtradePanel executor anónimo)
  - Compilación: ✅ 47 fuentes, 88 clases, exit 0
- **Estado**: ✅ Aplicado

---

## FASE 5 — Extracción de sub-componentes de paneles

### Objetivo
`RecordsPanel.java` (591 líneas) mezcla UI, lógica de caracterización espectral y handlers de archivos.
`DashboardPanel.java` (732 líneas) mezcla UI, espectro armónico interactivo y KPIs.

### [F5-001] Extraer CharacterizationController y HarmonicSpectrumCard
- **Fecha**: 2026-05-07
- **Análisis**:
  - RecordsPanel: 8 campos + 3 static finals + 5 métodos + buildCharBanner() = ~160 líneas de caracterización cohesiva
  - DashboardPanel: 3 campos (harmonicSeries, harmonicInfoLabel, lastMeasurement) + 3 métodos = ~82 líneas del espectro armónico
  - Ambos grupos son autocontenidos — no requieren callbacks hacia el panel padre
  - CharacterizationController construye su propio nodo HBox (banner); DashboardPanel ya no gestiona lastMeasurement
- **Archivos nuevos**:
  - `src/main/java/com/harmonicmonitor/gui/CharacterizationController.java` (236 líneas)
  - `src/main/java/com/harmonicmonitor/gui/HarmonicSpectrumCard.java` (121 líneas)
- **Archivos modificados**:
  - `RecordsPanel.java`: 591 → 391 líneas (−34%)
  - `DashboardPanel.java`: 732 → 643 líneas (−12%)
- **Patrón**: componente se construye en su propio constructor + expone `getNode()` + `shutdown()` cuando aplica
- **Rollback**:
  ```bash
  git checkout refactor-baseline-v1.0 -- \
    src/main/java/com/harmonicmonitor/gui/RecordsPanel.java \
    src/main/java/com/harmonicmonitor/gui/DashboardPanel.java
  rm src/main/java/com/harmonicmonitor/gui/CharacterizationController.java
  rm src/main/java/com/harmonicmonitor/gui/HarmonicSpectrumCard.java
  ```
- **Resultado**: Compilación: ✅ 49 fuentes, exit 0
- **Estado**: ✅ Aplicado

### [F5-002] Extraer KpiRow, MetricsCard, AlarmMiniCard de DashboardPanel

- **Fecha**: 2026-05-07
- **Análisis**:
  - DashboardPanel (643 líneas post-F5-001): 10 campos Label KPI + 4 prev-doubles + métodos buildKpiRow/buildKpiTile/findKpiValue/findKpiDelta/updateKpis/setDelta/computeConfidence = ~150 líneas
  - 12 campos Label métricas + métodos buildMetricsCard/metricLabel/addMetricRow/buildMetricCell/addPhaseHeaderRow/updateMetrics = ~120 líneas
  - alarmListBox + buildAlarmCard/updateAlarmList/levelIcon/shorten = ~80 líneas
  - Acoplamiento: KpiRow ninguno, MetricsCard ninguno, AlarmMiniCard necesita AlarmEngine + Runnable (no app ref)
- **Archivos nuevos**:
  - `src/main/java/com/harmonicmonitor/gui/KpiRow.java` (174 líneas)
  - `src/main/java/com/harmonicmonitor/gui/MetricsCard.java` (147 líneas)
  - `src/main/java/com/harmonicmonitor/gui/AlarmMiniCard.java` (100 líneas)
- **Archivo modificado**: `DashboardPanel.java`: 643 → 287 líneas (−55%)
  - 26 campos eliminados (kpiXxx, metricXxx, alarmListBox, prevXxx)
  - 3 campos añadidos (kpiRow, metricsCard, alarmCard)
  - updateAllWidgets: 5 llamadas → 5 delegaciones limpias
  - Imports eliminados: AlarmEvent, LoadType, Node (reimportado), List, Orientation
- **Rollback**:
  ```bash
  git checkout HEAD~1 -- src/main/java/com/harmonicmonitor/gui/DashboardPanel.java
  rm src/main/java/com/harmonicmonitor/gui/KpiRow.java
  rm src/main/java/com/harmonicmonitor/gui/MetricsCard.java
  rm src/main/java/com/harmonicmonitor/gui/AlarmMiniCard.java
  ```
- **Resultado**: Compilación: ✅ 52 fuentes, exit 0
- **Estado**: ✅ Aplicado

---

## FASE 6 — Separación DSP y lógica de análisis de ComtradePanel

### Objetivo
`ComtradePanel.java` quedó en 1116 líneas post-F4 (builders delegando a 3 clases).
Extraer DSP puro y cálculos de análisis (Fortescue + potencia) para desacoplar GUI de lógica.

### [F6-001] Crear ComtradeDsp.java — métodos DSP estáticos
- **Fecha**: 2026-05-08
- **Análisis**: 4 métodos `static` en ComtradePanel usados por FftChartBuilder y PhasorDiagramRenderer
  - `calculateFFTMagnitude`, `calculateComplexSpectrum`, `fft`, `extractWindow(static)`
  - Ninguno tiene dependencia de GUI — candidatos perfectos para separación
- **Archivo nuevo**: `src/main/java/com/harmonicmonitor/comtrade/ComtradeDsp.java`
  - Todos los métodos `public static`
- **Archivos modificados**:
  - `ComtradePanel.java`: 4 métodos estáticos eliminados; llamadas internas → `ComtradeDsp.xxx()`
  - `FftChartBuilder.java`: `ComtradePanel.xxx` → `ComtradeDsp.xxx` + import
  - `PhasorDiagramRenderer.java`: `ComtradePanel.xxx` → `ComtradeDsp.xxx` + import
  - `ComtradePanel.chNameWithUnit`: eliminado (código muerto — builders usan `WaveformChartBuilder.chNameWithUnit`)
- **Rollback**:
  ```bash
  rm src/main/java/com/harmonicmonitor/comtrade/ComtradeDsp.java
  git checkout HEAD~1 -- src/main/java/com/harmonicmonitor/gui/ComtradePanel.java \
    src/main/java/com/harmonicmonitor/gui/FftChartBuilder.java \
    src/main/java/com/harmonicmonitor/gui/PhasorDiagramRenderer.java
  ```
- **Resultado**: Compilación: ✅ 53 fuentes, exit 0
- **Estado**: ✅ Aplicado

### [F6-002] Crear SequencePowerAnalyzer.java + limpieza
- **Fecha**: 2026-05-08
- **Análisis**:
  - `calculateSequences()` (54 líneas) + `calculatePower()` (88 líneas) — algoritmos puros que solo necesitan record + sel + window
  - Helpers asociados: `complexH1`, `cmul`, `chName`, `unitLooksLikeVoltage`, `unitLooksLikeCurrent`
  - Patrón: clase que recibe record+winStart+winEnd en constructor, expone métodos que retornan `String`
  - Cleanup: `HarmonicMonitorApp.exportCsv()` dead code (private, sin botón en toolbar); raw `new Thread(...)` en `setupAlarmStorage`
- **Archivo nuevo**: `src/main/java/com/harmonicmonitor/gui/SequencePowerAnalyzer.java`
  - `String calculateSequences(List<Integer> sel)` — retorna texto formateado
  - `String calculatePower(List<Integer> sel)` — retorna texto formateado
- **Archivos modificados**:
  - `ComtradePanel.java`: eliminados 2 métodos de cálculo + 5 helpers (145+ líneas); botones "Calcular" actualizados; 1116 → 832 líneas (−25%)
  - `HarmonicMonitorApp.java`: eliminado `exportCsv()` dead code; `new Thread(...)` → `AppExecutors.ioPool().execute(...)` en `setupAlarmStorage`; 765 → 749 líneas
- **Rollback**:
  ```bash
  rm src/main/java/com/harmonicmonitor/gui/SequencePowerAnalyzer.java
  git checkout HEAD~1 -- src/main/java/com/harmonicmonitor/gui/ComtradePanel.java \
    src/main/java/com/harmonicmonitor/HarmonicMonitorApp.java
  ```
- **Resultado**: Compilación: ✅ 54 fuentes, exit 0
- **Estado**: ✅ Aplicado

---

## Registro de compilaciones

| Fecha | Fase | Resultado | Notas |
|-------|------|-----------|-------|
| 2026-05-07 | F0-001 baseline | ✅ OK — 42 clases, exit 0 | Tag `refactor-baseline-v1.0` creado; 0 errores compilación |
| 2026-05-07 | F1-001 HelpPanel | ✅ OK — 42 clases, exit 0 | HelpPanel 1408→204 líneas; 21 txt en resources/ |
| 2026-05-07 | F2-001 MmsDataMapper | ✅ OK — 46 clases, exit 0 | IEC61850Communicator 1007→294; MmsDataMapper 789 líneas |
| 2026-05-07 | F4-001 ComtradePanel split | ✅ OK — 46 clases, exit 0 | ComtradePanel 1390→1116; 3 builders (168+147+189 líneas) |
| 2026-05-07 | Commit `8191b16` | ✅ 34 archivos, 46 clases | Refactor F1+F2+F4 completo |
| 2026-05-07 | F3-001 AppExecutors | ✅ 47 fuentes, exit 0 | 7 boilerplate lambdas → AppExecutors; resource leak corregido |
| 2026-05-07 | F5-001 CharacterizationController+HarmonicSpectrumCard | ✅ 49 fuentes, exit 0 | RecordsPanel 591→391; DashboardPanel 732→643 |
| 2026-05-07 | F5-002 KpiRow+MetricsCard+AlarmMiniCard | ✅ 52 fuentes, exit 0 | DashboardPanel 643→287 (−55%) |
| 2026-05-08 | F6-001 ComtradeDsp | ✅ 53 fuentes, exit 0 | 4 métodos estáticos DSP → comtrade pkg; builders actualizados |
| 2026-05-08 | F6-002 SequencePowerAnalyzer + cleanup | ✅ 54 fuentes, exit 0 | ComtradePanel 1116→832 (−26%); HarmonicMonitorApp 765→749; raw Thread fix |
| 2026-05-08 | F7-002 MlXlsxWriter | ✅ 55 fuentes, exit 0 | MLDataExporter 620→193 (−69%); MlXlsxWriter 265 líneas creado |
| 2026-05-08 | F8-001 SpectraCampaignStore | ✅ 56 fuentes, exit 0 | DataStorage 529→307 (−42%); SpectraCampaignStore 264 líneas creado |
| 2026-05-08 | F8-002 ComtradeReportWriter | ✅ 57 fuentes, exit 0 | ComtradeTriggerEngine 532→433 (−19%); ComtradeReportWriter 147 líneas creado; constantes normativas centralizadas |
| 2026-05-08 | F9-001 MmsNodeReader | ✅ 58 fuentes, exit 0 | MmsDataMapper 789→465 (−41%); MmsNodeReader 252 líneas creado; NodePair + 8 helpers extraídos |
| 2026-05-08 | F10-001 ComtradeTabBuilder | ✅ 59 fuentes, exit 0 | ComtradePanel 832→~530 (−36%); ComtradeTabBuilder 261 líneas creado; 4 buildXxxTab + updateAnalysisWindow extraídos |
| 2026-05-08 | F11-001 FeederLifecycleManager | ✅ 60 fuentes, exit 0 | HarmonicMonitorApp 749→529 (−29%); FeederLifecycleManager 311 líneas creado; 4 colecciones + 9 métodos extraídos |
| 2026-05-08 | F12-001 HarmonicsDisplayUpdater + HarmonicRow | ✅ 62 fuentes, exit 0 | HarmonicsPanel 501→358 (−29%); HarmonicsDisplayUpdater 151 líneas; HarmonicRow 46 líneas; refreshDisplay + 2 helpers + inner class extraídos |
| 2026-05-08 | F13-001 ComplianceRowBuilder + CompRow | ✅ 64 fuentes, exit 0 | CompliancePanel 476→~340 (−28%); ComplianceRowBuilder 148 líneas; CompRow 42 líneas; buildRows + 3 factories + inner class extraídos |
| 2026-05-08 | F14-001 AlarmRow | ✅ 65 fuentes, exit 0 | AlarmsPanel 453→420 (−7%); AlarmRow 42 líneas; inner class extraído |
| 2026-05-08 | F15-001 FeederFormBuilder | ✅ 66 fuentes, exit 0 | FeederMgmtPanel 512→339 (−34%); FeederFormBuilder 220 líneas; buildFormArea + 4 helpers de construcción extraídos |
| 2026-05-08 | F16-001 SimProfileLoader + Profile | ✅ 68 fuentes, exit 0 | IonSimServer 575→288 (−50%); SimProfileLoader 291 líneas; Profile 24 líneas; JSON parser + 8 perfiles embebidos + inner class extraídos |
| 2026-05-08 | F17-001 AboutPanelBuilder | ✅ 69 fuentes, exit 0 | AboutPanel 429→18 (−96%); AboutPanelBuilder 280 líneas; toda la construcción UI extraída como clase estática pura |
| 2026-05-08 | F18-001 ComparativaPanel → recurso HTML | ✅ 69 fuentes, exit 0 | ComparativaPanel 375→41 (−89%); comparativa.html creado en resources/; patrón F1-001 |
| 2026-05-08 | F19-001 FeederRow | ✅ 70 fuentes, exit 0 | MultiFeederMonitorPanel 427→346 (−19%); FeederRow 93 líneas; inner class extraída |
| 2026-05-08 | F20-001 AppSceneBuilder | ✅ 71 fuentes, exit 0 | HarmonicMonitorApp 529→393 (−26%); AppSceneBuilder 155 líneas; toolbar+tabpane+statusbar extraídos |
| 2026-05-08 | F21-001 HarmonicRefDetector + PowerScaleDetector | ✅ 73 fuentes, exit 0 | MmsDataMapper 465→360 (−23%); HarmonicRefDetector 90 líneas; PowerScaleDetector 75 líneas; 2 bloques de lógica pura extraídos |
| 2026-05-09 | F22-001 ComtradeHeaderBuilder | ✅ 74 fuentes, exit 0 | ComtradePanel 556→465 (−16%); ComtradeHeaderBuilder 115 líneas; buildHeader+buildSidebar+buildStatusBar extraídos |
| 2026-05-09 | F23-001 MultiFeederTableBuilder | ✅ 75 fuentes, exit 0 | MultiFeederMonitorPanel 346→215 (−38%); MultiFeederTableBuilder 170 líneas; buildTable+3 cell factories+row factory+colStr+colInt extraídos |
| 2026-05-09 | F24-001 ComtradeCaptureAction + ComtradeCsvExporter | ✅ 77 fuentes, exit 0 | ComtradePanel 465→370 (−20%); ComtradeCaptureAction 95 líneas; ComtradeCsvExporter 70 líneas; captureNow+findNewestCfgFile+exportCsv extraídos |

---

## FASE 19 — Extracción de clase interna FeederRow

### [F19-001] Crear FeederRow.java — extracción del `public static` inner class de MultiFeederMonitorPanel
- **Fecha**: 2026-05-08
- **Análisis**:
  - `MultiFeederMonitorPanel.java` (427 líneas) contenía `public static class FeederRow` (líneas 346–426)
  - DTO mutable con 14 campos, constructor, `clear()`, `update(FeederMeasurement, int)` y 15 getters/setters
  - Sin dependencias de MultiFeederMonitorPanel — extracción directa
  - Patrón: data class standalone (igual que `AlarmRow`, `CompRow`, `HarmonicRow`)
- **Archivo nuevo**: `src/main/java/com/harmonicmonitor/gui/FeederRow.java` (93 líneas)
  - DTO mutable con `update()` y `clear()`
  - Unicode escapes: `\u2014` (—), `\u25CF` (●)
- **Archivo modificado**: `MultiFeederMonitorPanel.java` (427 → 346 líneas, −19%)
  - Inner class `FeederRow` (81 líneas) eliminada
  - Import `FeederMeasurement` conservado (aún usado en `updateMeasurement()` y `refreshAll()`)
- **Rollback**:
  ```bash
  rm src/main/java/com/harmonicmonitor/gui/FeederRow.java
  git checkout HEAD -- src/main/java/com/harmonicmonitor/gui/MultiFeederMonitorPanel.java
  ```
- **Compilación**: ✅ 70 fuentes, exit 0
- **Estado**: ✅ Aplicado

---

## FASE 13 — Extracción de lógica de filas de cumplimiento normativo

### [F13-001] Crear ComplianceRowBuilder.java + CompRow.java — extracción de buildRows(), factories e inner class de CompliancePanel
- **Fecha**: 2026-05-08
- **Análisis**:
  - `CompliancePanel.java` (476 líneas) contenía: inner class `CompRow` (DTO), método `buildRows()` con evaluación de 15 parámetros normativos (4 normas), y 3 factories (`ev`, `evPF`, `evFreq`) con lógica de umbrales
  - La lógica es pura (sin estado, sin dependencias de UI) — candidata a clase estática de utilidad
  - Patrón elegido: `final class` con `private constructor()` + métodos `static` (igual que `ComtradeReportWriter`)
- **Archivo nuevo**: `src/main/java/com/harmonicmonitor/gui/ComplianceRowBuilder.java` (148 líneas)
  - `static List<CompRow> buildRows(FeederMeasurement, FeederConfig)` — 15 filas IEC/IEEE/EN
  - `static List<CompRow> buildDefaultRows()` — filas placeholder sin datos
  - Factories package-private: `ev(...)`, `evPF(...)`, `evFreq(...)` con umbrales normativos
- **Archivo nuevo**: `src/main/java/com/harmonicmonitor/gui/CompRow.java` (42 líneas)
  - DTO inmutable: 7 campos final, constructor + 7 getters
  - Extraído del `public static` inner class de `CompliancePanel`
- **Archivo modificado**: `CompliancePanel.java` (476 → ~340 líneas, −28%)
  - `buildDefaultRows()` → delegador de 3 líneas a `ComplianceRowBuilder`
  - `buildRows()` → delegador de 1 línea
  - Factories `ev`, `evPF`, `evFreq` eliminadas
  - Inner class `CompRow` eliminada
- **Compilación**: ✅ 64 fuentes, exit 0

---

## FASE 10 — Extracción de constructores de pestañas COMTRADE

### [F10-001] Crear ComtradeTabBuilder.java — extracción de los 4 buildXxxTab() de ComtradePanel
- **Fecha**: 2026-05-08
- **Análisis**:
  - `ComtradePanel.java` (832 líneas) contiene 4 métodos `buildXxxTab()` + `updateAnalysisWindow()` que construyen la UI de las pestañas Formas de Onda, FFT, Fasores y Secuencias/Potencia
  - Los métodos asignan 16 campos de UI (waveformChart, fftChart, phasorCanvas, zoomSlider, etc.)
  - Los event handlers delegan a `plotWaveforms()`, `drawPhasors()`, `analyzeAll()`, `updateAnalysisWindow()`, `selectedIndices()`
  - Patrón: builder con campos de salida package-private; panel copia refs tras llamar a `build()`
- **Archivo nuevo**: `src/main/java/com/harmonicmonitor/gui/ComtradeTabBuilder.java` (261 líneas)
  - Construye `TabPane` con 4 tabs; expone 16 widgets como campos package-private
  - Callbacks via `panel.plotWaveforms()`, `panel.drawPhasors()`, `panel.analyzeAll()`, `panel.updateAnalysisWindow()`, `panel.selectedIndices()`
- **Archivo modificado**: `ComtradePanel.java` (832 → ~530 líneas, −36%)
  - `buildTabs()` delegado a `ComtradeTabBuilder.build()` + asignación de 16 campos
  - `updateAnalysisWindow()` queda en panel (package-private)
  - Métodos `plotWaveforms`, `drawPhasors`, `analyzeAll`, `selectedIndices`, `mkBtn`, `mkSmBtn`, `sectionLbl` ahora package-private
  - Campos `currentRecord`, `winStartSample`, `winEndSample` ahora package-private
  - Import `ChangeListener` eliminado (movido al builder)
- **Rollback**:
  ```bash
  rm src/main/java/com/harmonicmonitor/gui/ComtradeTabBuilder.java
  git checkout HEAD~1 -- src/main/java/com/harmonicmonitor/gui/ComtradePanel.java
  ```
- **Resultado**: Compilación: ✅ 59 fuentes, exit 0
- **Estado**: ✅ Aplicado

---

## FASE 8 — Extracción de responsabilidades de almacenamiento y reporte

### [F8-001] Crear SpectraCampaignStore.java — extracción de campaña espectral de DataStorage
- **Fecha**: 2026-05-08
- **Tipo**: Extracción de clase (lógica de tabla harmonic_spectra, 147 columnas H2..H50)
- **Análisis**:
  - `DataStorage.java` líneas 233–529: `storeSpectrum`, `exportSpectraToCsv`, `getCampaignSummary`,
    `createSpectraTable` — todo relacionado con la campaña ML de caracterización espectral
  - Todas usan `db` (Connection) como único estado compartido → patrón constructor con inyección
  - `exportSpectraToCsv` y `getCampaignSummary` sin callers externos → API planificada, se conservan
- **Archivo nuevo**: `storage/SpectraCampaignStore.java` (264 líneas, package-private)
  - Constructor `SpectraCampaignStore(Connection db)`
  - `createTable(Statement)`, `store(...)`, `exportToCsv(...)`, `getSummary(...)`
- **Archivo modificado**: `storage/DataStorage.java`
  - Campo `private SpectraCampaignStore spectraCampaign` creado en `initialize()`
  - `createTables()` llama `spectraCampaign.createTable(st)` en lugar de `createSpectraTable(st)`
  - 3 métodos públicos delegadores (`storeSpectrum`, `exportSpectraToCsv`, `getCampaignSummary`)
  - Imports eliminados: `ArrayList` (para harmCols), declaraciones locales de columnas armónicas
  - 529 → 307 líneas (−42%)
- **Rollback**:
  - `git checkout refactor-baseline-v1.0 -- src/main/java/com/harmonicmonitor/storage/DataStorage.java`
  - `rm src/main/java/com/harmonicmonitor/storage/SpectraCampaignStore.java`
- **Compilación**: ✅ 56 fuentes, exit 0
- **Estado**: ✅ Aplicado

### [F8-002] Crear ComtradeReportWriter.java — extracción de reporte + constantes normativas
- **Fecha**: 2026-05-08
- **Tipo**: Extracción de clase (generación texto + límites IEEE 519-2022 / IEC 61000-3-6)
- **Análisis**:
  - `ComtradeTriggerEngine.java` líneas 59–63: `V_HARM_ORDERS`, `V_HARM_LIMITS` (constantes IEC 61000-3-6)
  - Líneas 370–459: `writeReport()` (~90 líneas) — generación del archivo `_report.txt`
  - Líneas 467–473: `ieee519CurrentLimit()` — lookup de límites IEEE 519-2022
  - Estas 3 piezas son cohesivas (normativa PQ + texto) y sin estado de instancia
- **Archivo nuevo**: `comtrade/ComtradeReportWriter.java` (147 líneas, package-private, `final`)
  - `static final int[] V_HARM_ORDERS`, `static final double[] V_HARM_LIMITS`
  - `static double ieee519CurrentLimit(int h)`
  - `static File write(FeederMeasurement, FeederConfig, String cause, String reason, TriggerLevel, File cfgFile)`
- **Archivo modificado**: `comtrade/ComtradeTriggerEngine.java`
  - `checkIndividualVoltageHarmonics`: `V_HARM_ORDERS/LIMITS` → `ComtradeReportWriter.V_HARM_ORDERS/LIMITS`
  - `checkIndividualCurrentHarmonics`: `ieee519CurrentLimit(h)` → `ComtradeReportWriter.ieee519CurrentLimit(h)`
  - `writeReport()` → delegador de 1 línea a `ComtradeReportWriter.write(...)`
  - Imports: `java.io.*` → `java.io.File, java.io.IOException, java.time.Instant` (explícitos)
  - 532 → 433 líneas (−19%)
- **Rollback**:
  - `git checkout refactor-baseline-v1.0 -- src/main/java/com/harmonicmonitor/comtrade/ComtradeTriggerEngine.java`
  - `rm src/main/java/com/harmonicmonitor/comtrade/ComtradeReportWriter.java`
- **Compilación**: ✅ 57 fuentes, exit 0
- **Estado**: ✅ Aplicado

---

## FASE 7 — Extracción de generación XLSX

### [F7-002] Crear MlXlsxWriter.java — extracción del bloque XLSX de MLDataExporter
- **Fecha**: 2026-05-08
- **Tipo**: Extracción de clase (pure-Java OOXML, sin GUI, sin dependencias externas)
- **Análisis**:
  - `MLDataExporter.java` líneas 217–620: `COL_META`, `writeXlsx()`, `buildDataSheet()`,
    `buildDictionarySheet()`, `txt()`, `readCsvRows()`, `addEntry()`, `colRef()`,
    `tryParseDouble()`, `escXml()` — todo relacionado con generación XLSX, sin acceso a `this`
  - Frontera limpia: único call site `writeXlsx(csv, xlsx, m.getFeederId())` en `appendRow()`
- **Archivo nuevo**: `storage/MlXlsxWriter.java` (265 líneas)
  - `static void write(File csv, File xlsx, String feederId)` — entrada pública
  - Todos los helpers como métodos `private static`
  - `COL_META` → campo `private static final` en la nueva clase
- **Archivo modificado**: `storage/MLDataExporter.java`
  - `writeXlsx(...)` → `MlXlsxWriter.write(...)`
  - Imports eliminados: `ArrayList`, `List`, `ZipEntry`, `ZipOutputStream`
  - 620 → 193 líneas (−69%)
- **Rollback**:
  - `git checkout refactor-baseline-v1.0 -- src/main/java/com/harmonicmonitor/storage/MLDataExporter.java`
  - `rm src/main/java/com/harmonicmonitor/storage/MlXlsxWriter.java`
- **Compilación**: ✅ 55 fuentes, exit 0, 0 errores
- **Estado**: ✅ Aplicado

---

## Líneas de código por archivo (baseline)

| Archivo | Líneas | Fase de refactor |
|---------|--------|-----------------|
| HelpPanel.java | 1408 | F1 |
| ComtradePanel.java | 1390 | F4 |
| IEC61850Communicator.java | 1007 | F2 |
| HarmonicMonitorApp.java | 736 | F3 (parcial) |
| DashboardPanel.java | 730 | — (ok) |
| MLDataExporter.java | 620 | — (ok) |
| RecordsPanel.java | 599 | — (ok) |
| FeederMgmtPanel.java | 579 | — (ok) |
| IonSimServer.java | 575 | — (ok) |
| ComtradeTriggerEngine.java | 532 | — (ok) |
| DataStorage.java | 529 | — (ok) |
