# Bitácora de Limpieza — Supresión de restos del Modo Degradado (estimación espectral)

> **Fecha**: 2026-07-02
> **Contexto**: la estimación de espectro en modo degradado fue suprimida del pipeline
> en una versión anterior (el `MeasurementPoller` dejó de llamar a `estimateMissingSpectrum`),
> pero quedaron restos: métodos muertos, rutas de GUI inalcanzables y documentación
> que describía el comportamiento antiguo como vigente. Esta limpieza elimina esos restos.
> **Baseline para rollback**: commit `9d4d57078b55dda27fcb1358aa09f2138b3c241a` (HEAD al iniciar; ningún archivo de esta limpieza tenía modificaciones locales previas).
> **Verificación**: `.\compile_ps2.ps1` → 80 archivos, Exit 0 (solo notas "unchecked" preexistentes).

---

## Cómo hacer rollback

```bash
# Opción A — restaurar UN archivo al estado previo a la limpieza
git checkout 9d4d570 -- "src/main/java/com/harmonicmonitor/analysis/HarmonicAnalyzer.java"

# Opción B — restaurar TODOS los archivos de esta limpieza
git checkout 9d4d570 -- \
  "src/main/java/com/harmonicmonitor/analysis/HarmonicAnalyzer.java" \
  "src/main/java/com/harmonicmonitor/gui/HarmonicSpectrumCard.java" \
  "src/main/java/com/harmonicmonitor/gui/KpiRow.java" \
  "src/main/java/com/harmonicmonitor/model/FeederMeasurement.java" \
  "src/main/java/com/harmonicmonitor/storage/MlXlsxWriter.java" \
  "src/main/java/com/harmonicmonitor/storage/SpectralRecorder.java" \
  "src/main/resources/com/harmonicmonitor/help/help_03_harmonics.txt" \
  "src/main/resources/com/harmonicmonitor/help/help_08_standards.txt" \
  "src/main/resources/com/harmonicmonitor/help/help_22_degraded_mode.txt" \
  "docs/hades_referencia_tecnica.md" \
  "BITACORA_LIMPIEZA_MODO_DEGRADADO.md"

# Recompilar para verificar
.\compile_ps2.ps1
```

---

## [L-001] HarmonicAnalyzer.java — eliminación de métodos muertos

- **Archivo**: `src/main/java/com/harmonicmonitor/analysis/HarmonicAnalyzer.java`
- **Tipo**: Eliminación de código muerto (sin cambio funcional — cero llamadores en todo el árbol de fuentes, verificado con grep)
- **Eliminado**:
  - `estimateCryptoSpectrum(double, double)` (~33 líneas) — generaba espectro con perfil SMPS fijo H3=40%, H5=35%, H7=20%... normalizado al THD medido. Único llamador: `estimateMissingSpectrum` (también eliminado).
  - `estimateSixPulseRectifierSpectrum(double, double)` (~24 líneas) — perfil 6 pulsos H5=25%, H7=11%, H11=9%, H13=8%. **Cero llamadores** (muerto desde su creación o desde refactor anterior).
  - `estimateMissingSpectrum(FeederMeasurement)` (~22 líneas) — orquestador de la estimación; poblaba L1/L2/L3 y marcaba `spectrumEstimated=true`. El `MeasurementPoller` ya no lo invocaba.
- **Modificado**: javadoc de clase — se retiró la responsabilidad "Estimación del espectro armónico típico si el IED no lo provee" y se añadió nota v1.2 explicando el comportamiento actual (ratios en 0, guarda en `calculateHarmonicRatios`).
- **Nota para eventual restauración**: los tres métodos completos están en el commit baseline `9d4d570` (líneas 69–172 del archivo original).
- **Estado**: ✅ Aplicado

## [L-002] HarmonicSpectrumCard.java — rama GUI inalcanzable

- **Archivo**: `src/main/java/com/harmonicmonitor/gui/HarmonicSpectrumCard.java`
- **Tipo**: Poda de código inalcanzable (`isSpectrumEstimated()` es siempre false en runtime actual)
- **Cambio**: en `showHarmonicDetail(int)` se eliminó la variable `estimated` (sufijo `"  [espectro estimado]"`) y los format specifiers `%s` asociados en los dos `String.format`.
- **Estado**: ✅ Aplicado

## [L-003] KpiRow.java — rama GUI inalcanzable

- **Archivo**: `src/main/java/com/harmonicmonitor/gui/KpiRow.java`
- **Tipo**: Poda de código inalcanzable
- **Cambio**: eliminada la línea `if (m.isSpectrumEstimated()) confTxt += "  ~ espectro estimado";` en el update de KPIs.
- **Estado**: ✅ Aplicado

## [L-004] Flag `spectrumEstimated` — CONSERVADO (decisión explícita)

- **Archivos**: `FeederMeasurement.java`, `SpectraCampaignStore.java`, `MLDataExporter.java`, `MlXlsxWriter.java`, `SpectralRecorder.java`
- **Decisión**: el campo `spectrumEstimated` y las columnas `spectrum_estimated` de SQLite/CSV/XLSX **se conservan** (siempre 0 en datos nuevos) por dos razones:
  1. Compatibilidad de esquema: bases SQLite y datasets CSV existentes tienen la columna; eliminarla rompería lectores y mezclaría esquemas.
  2. Datos históricos: registros generados por versiones ≤ v1.1 pueden tener `spectrum_estimated = 1`; la columna permite filtrarlos en análisis/ML.
- **Cambios aplicados** (solo comentarios/metadatos, sin cambio funcional):
  - `FeederMeasurement.java`: comentario del campo actualizado ("siempre false desde v1.2...").
  - `MlXlsxWriter.java`: descripción de la columna en el diccionario de variables XLSX actualizada.
  - `SpectralRecorder.java`: comentario de esquema actualizado.
  - `SpectraCampaignStore.java` y `MLDataExporter.java`: **sin cambios** (persisten el flag tal cual; correcto).
- **Estado**: ✅ Aplicado

## [L-005] help_03_harmonics.txt — nota de modo degradado

- **Archivo**: `src/main/resources/com/harmonicmonitor/help/help_03_harmonics.txt`
- **Cambio**: la nota decía que en modo degradado los Hn "son ESTIMACIONES bajo un perfil SMPS genérico". Ahora dice que los Hn no están disponibles, la gráfica queda vacía y todos los valores mostrados son mediciones reales.
- **Estado**: ✅ Aplicado

## [L-006] help_08_standards.txt — advertencia de cumplimiento normativo

- **Archivo**: `src/main/resources/com/harmonicmonitor/help/help_08_standards.txt`
- **Cambio**: la advertencia decía que los Hn de la pantalla Normas "son ESTIMADOS, no medidos". Ahora dice que los límites por armónico de IEC 61000-3-6 no son evaluables (sin datos) y que solo el THD medido mantiene valor orientativo.
- **Estado**: ✅ Aplicado

## [L-007] help_22_degraded_mode.txt — reescritura completa del topic

- **Archivo**: `src/main/resources/com/harmonicmonitor/help/help_22_degraded_mode.txt`
- **Tipo**: Reescritura (el contenido anterior documentaba la estimación con fórmulas, ejemplo numérico y condiciones de validez estadística — todo obsoleto)
- **Estructura nueva**:
  1. Qué es el modo degradado (sin cambios de fondo; texto del banner actualizado al real de `HarmonicsPanel`)
  2. Qué se mide y qué NO está disponible (antes: "qué se estima")
  3. Efecto sobre el clasificador (ratios en 0; qué clases son alcanzables y cuáles no; cautela con LIGHTING)
  4. Efecto sobre la pantalla Normas
  5. **Nota histórica** sobre la estimación suprimida en v1.2 y el significado de `spectrum_estimated = 1` en datos antiguos
  6. Recomendación operativa (opciones A/B/C conservadas, adaptadas)
- **Contenido descartado** (recuperable del baseline): secciones 3 "Cómo se estima el espectro", 4 "Por qué esto es insuficiente", 5 "¿Existe una ventana estadística?" — describían el mecanismo eliminado.
- **Nota**: el título del topic en `HelpPanel.java:46` ("⚠ Modo Degradado — IED sin espectro") sigue siendo válido; no se tocó.
- **Estado**: ✅ Aplicado

## [L-008] docs/hades_referencia_tecnica.md — actualización de 11 pasajes

- **Archivo**: `docs/hades_referencia_tecnica.md`
- **Cambios**:
  | # | Ubicación | Antes | Después |
  |---|---|---|---|
  | 1 | Leyenda de etiquetas | `[ESTIMACIÓN-DEGRADADA]` descrita como vigente | Marcada como **etiqueta histórica** (≤ v1.1), conservada para interpretar datos antiguos |
  | 2 | §3 "Modo degradado — espectro estimado" | Describía `estimateMissingSpectrum` en el poller + problema de circularidad como comportamiento actual | Reescrita: "espectro no disponible", secuencia real del poller, tabla de dimensiones actualizada, nota histórica sobre la estimación suprimida |
  | 3 | §4.2 nota final | "HADES genera un espectro con el perfil fijo SMPS" | "el espectro queda en ceros... No se genera ninguna estimación" |
  | 4 | §5 matriz de validez | Columna "Modo degradado (espectro estimado)" / "No válida" | "espectro no disponible" / "No disponible — queda en 0 (no se estima)" |
  | 5 | §5 dimensión THD_I | Distinción THD desde espectro estimado = circular | THD siempre desde medición directa o derivación exacta |
  | 6 | §7 advertencia score | "`C_Ratios` se calcula desde el template" | "los ratios quedan en 0 y `C_Ratios` no aporta puntos" |
  | 7 | §9.2 dashboard | Indicación visual "cuando `spectrumEstimated = true`" | "cuando el IED no expone el array HA" |
  | 8 | §9.3 panel espectro | "Indicación visual cuando el espectro es estimado" | "Banner de aviso... gráfica vacía; no se estima" |
  | 9 | §9.5 exportación | Columna indica "el modo de cada muestra" | Columna conservada por compatibilidad; siempre 0 en datos actuales |
  | 10 | §11 alcance + limitaciones | "Implementado pero con validez reducida (circularidad)" | "Operación reducida por diseño... sin circularidad" |
  | 11 | §13 hoja de ruta | Pendiente "Inhabilitar clasificación automática en modo degradado" | Marcada ✔ **Completado (v1.2)** |
- **Estado**: ✅ Aplicado

---

## Verificación final

- `grep estimateMissingSpectrum|estimateCryptoSpectrum|estimateSixPulseRectifierSpectrum` en `src/` → 0 resultados en código (solo mención histórica en docs).
- `grep isSpectrumEstimated` en `src/main/java` → solo persistencia (`SpectraCampaignStore`, `MLDataExporter`) y accessors del modelo; ninguna ruta de GUI.
- Compilación: `.\compile_ps2.ps1` → **Exit 0**, 80 archivos, recursos copiados. Notas "unchecked" preexistentes (documentadas en BITACORA_REFACTORIZACION.md F0-001).

## Qué NO se tocó (fuera de alcance de esta limpieza)

- `HarmonicsPanel.setDegradedMode()` y el banner "El IED no expone armónicos individuales — solo THD disponible": **vigentes y correctos** — el modo degradado como *modo de datos reducidos* sigue existiendo; lo suprimido fue la *estimación*.
- `FeederLifecycleManager.java:163-165`: activación del banner al conectar — vigente.
- Columnas `spectrum_estimated` en BD/exportadores — conservadas (ver L-004).
- `docs/hades_caracterizacion_probabilistica.md` — no menciona la estimación de espectro; sin cambios.
- `README.md` — ya describía el comportamiento actual correctamente; sin cambios.
