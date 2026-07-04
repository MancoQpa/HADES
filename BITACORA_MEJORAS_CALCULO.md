# Bitácora de Mejoras de Cálculo — HADES v1.2

> **Fecha**: 2026-07-04
> **Baseline para rollback**: commit `dfc288a` (todos los archivos de esta tanda estaban limpios en ese commit, salvo los archivos nuevos).
> Cada entrada documenta el cambio, la **justificación técnica con fuentes**, las alternativas descartadas y el rollback.
> **Verificación**: `.\compile_ps2.ps1` Exit 0; suite completa de tests en verde.

---

## Cómo hacer rollback

```bash
# Restaurar un archivo al estado del baseline
git checkout dfc288a -- "src/main/java/com/harmonicmonitor/analysis/HarmonicAnalyzer.java"

# Archivos nuevos de esta tanda: eliminar directamente
rm BITACORA_MEJORAS_CALCULO.md
rm src/main/resources/com/harmonicmonitor/help/help_23_formulas.txt
rm src/main/resources/com/harmonicmonitor/help/help_24_bibliografia.txt

# Recompilar y correr tests
.\compile_ps2.ps1
.\run_tests.ps1
```

---

## [M-001] Ratios armónicos por fase — selección de fase peor coherente

- **Archivo**: `src/main/java/com/harmonicmonitor/analysis/HarmonicAnalyzer.java` (`calculateHarmonicRatios`, nuevo helper `worstPhaseSpectrum`)
- **Antes**: todos los ratios (H3/H1, H5/H1, H7/H1, H11/H1, H13/H1) se calculaban **exclusivamente de L1**. Un desequilibrio, una carga concentrada en L2/L3, o un canal de medición defectuoso en L1 sesgaba el vector completo del clasificador.
- **Ahora**: los ratios se toman **todos de una misma fase: la de mayor THD espectral** (fases con fundamental medible; desempate L1 → L2 → L3).

### Justificación con publicaciones

1. **Las normas de medición tratan cada fase como canal independiente.**
   IEC 61000-4-7 define la medición de armónicos **por fase**; IEC 61000-4-30 especifica la agregación **temporal por canal** — ninguna norma define un "espectro promedio entre fases". Promediar espectros de fases distintas no tiene respaldo normativo.
2. **La práctica de evaluación es "fase más desfavorable".**
   La aplicación de IEEE 519 evalúa los límites en el PCC contra cada fase, y el juicio de cumplimiento se hace sobre la fase que peor está; EN 50160 caracteriza cada fase de la tensión de suministro. Para un clasificador cuyo propósito es *detectar* la carga distorsionante, el criterio conservador (fase peor) es el coherente con esa práctica.
3. **Por qué NO promedio (instrucción explícita del autor, confirmada por la teoría):**
   una carga monofásica o bifásica concentrada (racks en una fase, iluminación mal repartida) produce firma fuerte en una fase y débil en las otras; el promedio la diluye hasta perderla. Además del punto 1 (sin base normativa).
4. **Por qué NO "máximo por orden" (mezclando fases):**
   rompe la coherencia intra-fase que necesitan los indicadores de **forma** espectral. Contraejemplo numérico (documentado también en el Javadoc y fijado como test):
   - L1: H5=3.9%, H7=0.2% → H5/H7 = 19.5 (firma PFC)
   - L2: H5=4.2%, H7=1.0% → H5/H7 = 4.2
   - Máximo por orden: H5=4.2% (de L2) con H7=1.0% (de L2)... pero si L1 tuviera el H5 mayor: H5=4.2%, H7=1.0% mezclados darían un ratio que **no existe en ninguna fase**, destruyendo la detección PFC. El mismo problema afecta a Flatness = (H5+H7)/(H11+H13).
5. **Guardas**: fases sin fundamental (arrays en ceros, IEDs monofásicos, modo degradado) se excluyen de la selección; si ninguna fase tiene espectro, los ratios quedan en 0 (comportamiento del modo degradado intacto).

- **Tests**: `HarmonicAnalyzerTest.ratiosUsanLaFaseDeMayorDistorsion` (carga concentrada en L2) y `ratiosMantienenCoherenciaIntraFase` (contraejemplo PFC). Los tests previos (solo L1 poblada) pasan sin cambios — con una sola fase, la fase peor ES esa fase.
- **Impacto en datos históricos**: ninguno (la BD guarda los ratios ya calculados; el cambio aplica a mediciones nuevas).
- **Estado**: ✅ Aplicado

## [M-002] Persistencia de la clase cruda y la estabilidad del smoother

- **Archivos**: `DataStorage.java` (esquema + INSERT), `MLDataExporter.java` (CSV), `MlXlsxWriter.java` (diccionario XLSX)
- **Problema**: la ventana con histéresis (commit `dfc288a`) hizo que `detectedLoadType` fuera la clase **suavizada**. La BD y los exportadores solo guardaban esa columna → el dataset perdía la clase cruda por muestra y la estabilidad — exactamente la información fina que un entrenamiento ML necesita (la clase suavizada arrastra la histéresis: filas consecutivas dejan de ser independientes).
- **Cambios**:
  - Tabla `measurements`: columnas nuevas `raw_load_type TEXT` y `load_type_stability REAL`, con **migración tolerante** (`ALTER TABLE ... ADD COLUMN` ignorando "duplicate column"; SQLite no soporta `IF NOT EXISTS` en columnas). Las bases existentes se migran solas al abrir; las filas antiguas quedan con NULL (distinguible de 0).
  - CSV ML: columnas `raw_label` y `label_stability` **al final** del header (minimiza impacto en lectores posicionales); el significado de `label` (ahora estable) quedó documentado en el diccionario.
  - **Rotación de formato**: si un `*_dataset.csv` existente tiene un header distinto, se renombra a `*_dataset_vN.csv` y se arranca archivo nuevo — evita filas desalineadas al mezclar formatos (los CSV viejos con header de 66 columnas recibirían filas de 68).
  - Diccionario XLSX: entradas para las dos columnas nuevas, con la guía de uso ("para ML usar `raw_label`; `label` arrastra histéresis").
- **Justificación**: principio de no-destrucción de información en la capa de persistencia — el suavizado es una decisión de *presentación/operación*, no de *registro*. El dato crudo es el que permite recalcular cualquier suavizado futuro (ventana distinta, otro algoritmo) sin recapturar.
- **Estado**: ✅ Aplicado

## [M-003] Reclasificación del valor del dataset acumulado (feedback del autor)

- **Hecho registrado**: el autor confirmó (jul-2026) que la **totalidad** de `harmonic_monitor.db` (~103 MB, 240 526 mediciones, mar–may 2026, incluidos los feeders AL-xx de larga duración) proviene de sesiones de **simulador**, no de campo.
- **Consecuencias documentadas**:
  1. El estudio ROC (`docs/estudio_roc.md`) queda acotado a lo que ya declaraba: separabilidad de características y coherencia interna árbol↔perfiles. **Ningún umbral empírico (CV, FP, Q/S, K) puede calibrarse con esta base.** La advertencia se endureció en el documento y en `RocStudy.java` (para regeneraciones futuras).
  2. El valor de la base actual para ML es de **validación de tubería** (esquema, exportación, regresión del clasificador), no de entrenamiento. Nota añadida en `hades_caracterizacion_probabilistica.md`.
  3. El "dataset de entrenamiento real" empieza en cero: primera campaña de campo etiquetada. Esto **eleva la prioridad** de M-002 (persistir crudo + estabilidad desde el primer día de captura de campo) y de registrar cada feeder de campo en el mapa `TRUTH` de `RocStudy`.
- **Archivos**: `docs/estudio_roc.md`, `src/test/java/.../RocStudy.java`, `docs/hades_caracterizacion_probabilistica.md`
- **Estado**: ✅ Documentado

## [M-004] Teoría, fórmulas y bibliografía integradas en la aplicación

- **Archivos**: `help_23_formulas.txt`, `help_24_bibliografia.txt` (nuevos recursos), `HelpPanel.java` (registro de temas + estilo)
- **Contenido**: 13 secciones de fórmulas con las expresiones exactas del código, cada una etiquetada `[NORMATIVO]/[TEÓRICO]/[EMPÍRICO]` y con su fuente; bibliografía completa con el formato "Sostiene / Límites" y la sección de afirmaciones propias sin respaldo externo (H5/H7>8 con N=1, CV<5%, FP 0.92, etc.).
- **Justificación**: la defensa técnica de la herramienta no puede depender de documentos del repositorio que el usuario final no ve; el operador que muestra un resultado a un tercero necesita la fuente de cada umbral a un clic. La sección §3 de fórmulas se actualizó en la misma tanda para reflejar M-001 (fase peor coherente).
- **Estado**: ✅ Aplicado

## [M-005] Descartado: validación BT/MT de la atenuación Dyn (decisión del autor)

- Propuesto originalmente como "medición simultánea BT/MT para cuantificar la transferencia del H3". **Descartado**: (a) el proyecto no dispone de puntos de medición en BT; (b) aunque existieran, corresponderían a otro nodo eléctrico distinto del monitoreado — la comparación quedaría fuera del alcance del sistema.
- La atenuación Dyn queda respaldada **solo por teoría** (secuencia cero atrapada en el delta: Arrillaga & Watson; Dugan et al.), y así está declarado en `help_23`/`help_24` y en `docs/bibliografia_fundamentos.md` §6. La consecuencia de diseño (LIGHTING exige H3 medido) es conservadora: ante la duda, no se reclama la clase.
- **Estado**: ✅ Cerrado sin implementación (decisión registrada)
