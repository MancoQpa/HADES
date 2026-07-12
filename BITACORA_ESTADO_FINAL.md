# Bitácora de Estado Final — HADES v1.2 (campaña de auditoría y mejoras, jul-2026)

> **Última actualización**: 2026-07-12 (ver secciones al final: revisión de
> pendientes, tanda P1–P5 resuelta, y pivote estratégico + app hermana
> PQMLogger).

> Instantánea del estado del proyecto al cierre de la campaña iniciada con la
> auditoría del clasificador (análisis, bibliografía, modelo de decisión) y
> cerrada con las mejoras de cálculo. Detalle por cambio en:
> `BITACORA_LIMPIEZA_MODO_DEGRADADO.md` (limpieza) y
> `BITACORA_MEJORAS_CALCULO.md` (M-001…M-005). Historia previa:
> `BITACORA_REFACTORIZACION.md`.

---

## Commits de la campaña

| Commit | Contenido |
|---|---|
| `dfc288a` | Limpieza modo degradado · suite JUnit · LIGHTING con H3/Dyn · estudios de dominancia y ROC · bibliografía defendible · LoadTypeSmoother (ventana + histéresis) |
| (este)   | Ratios por fase peor coherente · persistencia raw_label/estabilidad · teoría/fórmulas/bibliografía integradas en la app (Ayuda 23/24) · reclasificación del dataset simulado |

Baseline de rollback de toda la campaña: `9d4d570`.

## Estado del clasificador

- Árbol de 9 nodos determinista (`ElectronicLoadDetector`), sin cambios de umbrales en esta campaña.
- **Entrada espectral**: ratios H3/H5/H7/H11/H13 tomados de la **fase peor coherente**
  (mayor THD espectral; antes solo L1). Sin promedio entre fases (sin base normativa)
  ni máximo por orden (rompe coherencia intra-fase). [M-001]
- **Salida**: clase cruda por muestra (`rawLoadType`) + clase **estable** publicada
  (`detectedLoadType`, ventana N=15 con histéresis ⌈0.67·N⌉=10) + `loadTypeStability` (0–1).
- **LIGHTING** exige H3/H1 > 15% medido — en 23 kV tras trafos Dyn rara vez alcanzable
  (secuencia cero atrapada en el delta); la iluminación vista desde MT se reporta como
  ELECTRONIC_LIGHT/MIXED. Respaldo teórico (Arrillaga; Dugan); validación BT descartada
  por decisión del autor (sin puntos BT; otro nodo eléctrico). [M-005]
- **Modo degradado** (IED sin array HA): ratios en 0, clasificación solo sobre
  observables medidos (CV/THD/FP/Q/S/K). La estimación de espectro fue suprimida;
  restos eliminados y documentados.

## Estado de la persistencia y el dataset

- SQLite `measurements`: columnas nuevas `raw_load_type`, `load_type_stability`
  (migración automática tolerante; filas antiguas en NULL). [M-002]
- CSV/XLSX ML: `label` (estable) + `raw_label` (cruda) + `label_stability`;
  rotación automática de datasets con formato anterior (`*_dataset_vN.csv`).
- ⚠ **La base acumulada (~103 MB, 240 526 mediciones, mar–may 2026) es íntegramente
  de simulador** (confirmado por el autor). Sirve para validar la tubería y como
  regresión; NO calibra umbrales ni entrena ML. El dataset real empieza con la
  primera campaña de campo etiquetada. [M-003]

## Verificación

- Compilación: `.\compile_ps2.ps1` → Exit 0 (81 fuentes).
- Tests: `.\run_tests.ps1` → **39/39 en verde** (4 clases de test):
  árbol completo (10 perfiles de la tabla maestra + bordes PFC + guardas),
  smoother (flapping 50/50 → 0 conmutaciones; latencia exacta; transitorios),
  analizador (ratios por fase peor, coherencia intra-fase, THD, H3),
  estudio de dominancia (extremos del modelo de mezcla).

## Estudios y documentación viva

| Documento | Contenido | Regenerable |
|---|---|---|
| `docs/estudio_dominancia.md` | Dominancia mínima por clase (mezcla sintética, ley IEC 61000-3-6): PFC ~97–98%, espectrales ~52–71% | `DominanceStudy` |
| `docs/estudio_roc.md` | Separabilidad de características + matriz de confusión sobre 9 339 filas etiquetadas (simulador) | `RocStudy` (añadir feeders de campo a `TRUTH`) |
| `docs/bibliografia_fundamentos.md` | Cada referencia: qué sostiene / fundamento / límites; afirmaciones propias declaradas | manual |
| Ayuda en la app (temas 23 y 24) | Fórmulas exactas del código con etiquetas [NORMATIVO]/[TEÓRICO]/[EMPÍRICO] + bibliografía operativa | manual [M-004] |

## Pendiente (en orden de valor)

1. **Campaña de campo etiquetada** — habilita: ROC real (añadir feeders a `TRUTH`),
   calibración de CV/FP/Q/S/K, validación N>1 de la hipótesis H5/H7 (hoy N=1),
   y el inicio del dataset ML real. Es el cuello de botella de todo lo demás.
2. Análisis temporal constante/variable (NILM) sobre el acumulado de campo — ataca
   la dominancia <97% del PFC.
3. Integrar MSTA (MinW/MaxW) y rampa MMTR al árbol (observables ya registrados).
4. Revisar FP=0.92 (CRYPTO vs DATA_CENTER) y matizar `CRYPTO_MINING_PFC`
   (cargas gemelas: EV, AFE, UPS) — ambos esperan datos de campo.
5. Reportes URCB/BRCB en lugar de polling.
6. Menores: umbrales editables desde GUI; desacoplar ruta JDK de los scripts.
7. Publicación (ICHQP / IEEE PES T&D): vector PFC + hipótesis H5/H7 + curva de
   dominancia + campaña de campo = paper completo.

---

## Revisión de pendientes — 2026-07-07

Revisión del código contra la lista anterior (working tree limpio y sincronizado
con `origin/main` en `60d01e8`). Los 7 pendientes estructurales siguen vigentes
sin cambios. La revisión detectó además **deuda técnica menor nueva**, derivada
de los propios cambios de la campaña:

### Deuda técnica detectada (verificada en código)

| # | Hallazgo | Impacto | Esfuerzo |
|---|---|---|---|
| P1 | **`h3h1` no se persiste en la tabla `measurements`** (sin columna; `grep h3h1 storage/` → 0 resultados). El nodo LIGHTING lo usa desde v1.2: las clasificaciones históricas no son reproducibles desde la BD. Mitigado parcialmente: el CSV ML conserva H3 vía `H3_I_pct` (espectro) y la campaña espectral guarda H1–H50 completo. | Medio — trazabilidad del árbol | Bajo (columna + migración, patrón ya existente de M-002) |
| P2 | **`DataStorage.exportToCsv`** (exportación clásica por rango, `DataStorage.java:190-225`) quedó con el header de 27 columnas anterior a v1.2: no exporta `raw_load_type`, `load_type_stability` ni H3. El dataset ML (`MLDataExporter`) sí está completo; este es el export "manual" de la GUI. | Bajo — inconsistencia entre exports | Bajo |
| P3 | `SpectraCampaignStore` no registra clase cruda/estabilidad. Mitigado: su propósito es el espectro (H1–H50); la clase vive en `measurements`. Evaluar si el join por timestamp es suficiente para ML antes de duplicar columnas. | Bajo | Bajo |
| P4 | `help_02_dashboard.txt` no menciona el indicador "estab. %" del KPI de carga (solo `help_09` lo explica). | Cosmético | Trivial |
| P5 | **`ROADMAP_MEJORAS.md` desactualizado** (última actualización 2026-03-25, pre-campaña): no refleja smoother, H3/Dyn, estudios ni tests. Marcar como histórico con puntero a esta bitácora, o retirarlo. | Confusión documental | Trivial |
| P6 | ~~`help_09` sin `CRYPTO_MINING_PFC` en el catálogo de clases~~ **CORREGIDO 2026-07-09**: la clase estrella de v1.2 no figuraba en "TIPOS DE CARGA DETECTADOS", la entrada de `CRYPTO_MINING` aún decía "con PFC activo" (obsoleto tras la separación de clases) y no se mencionaba la variante relajada 4/5 sin K-Factor. Se añadió la entrada completa (vector, cargas gemelas, N=1, punteros a temas 23/24), se corrigió CRYPTO_MINING ("SIN PFC efectivo"), se amplió la lista de señales de entrada y se añadió el vector PFC a "Umbrales de estimación". | Confusión documental | Hecho |

### Verificaciones sin hallazgo (descartadas como pendientes)

- `FeederConfig`: no existe mecanismo de serialización/persistencia de feeders
  (sin JSON/Properties en `src/`) — los parámetros nuevos del smoother no tienen
  problema de compatibilidad al reiniciar; toman defaults.
- `DominanceStudy`: no afectado por M-001 (construye los ratios directamente,
  no pasa por `calculateHarmonicRatios`); sus resultados siguen válidos.
- Integración del smoother: instancia por poller, recreada al reconectar —
  sin arrastre de ventana entre sesiones. Alarmas y storage consumen la clase
  estable (menos falsas alarmas por parpadeo), y la cruda queda en BD (M-002).
- Submódulo `simulator` y `build_paquetes_v11.ps1`: modificaciones preexistentes
  a la campaña, intactas, fuera de los commits — decisión pendiente del autor.

### Recomendación de orden

P1+P2 en una sola tanda pequeña (misma mecánica de M-002: columna + migración +
export), P4+P5 de pasada en el mismo commit. P3 esperar al diseño del análisis
temporal (ítem 2 estructural) para no duplicar esquema sin necesidad.

---

## Tanda P1+P2+P4+P5 — RESUELTA 2026-07-10

- **P1** ✔: columna `h3h1` en `measurements` (DDL + `addColumnIfMissing`,
  mecánica M-002) e INSERT desde `m.getH3h1Ratio()`. Filas anteriores quedan
  en NULL (no se estima).
- **P2** ✔: `exportToCsv` con header v1.2 completo (30 columnas): `H3/H1`,
  `TipoCargaCrudo`, `Estabilidad`. NULLs de filas pre-migración se exportan
  vacíos (no medido ≠ 0.0).
- **P2b (hallazgo nuevo durante la verificación)** ✔: `exportToCsv` y el
  export CSV de `SpectraCampaignStore` usaban `printf`/`String.format` con
  el locale del sistema — en es-* emitían decimales con COMA dentro de un
  CSV separado por comas, corrompiendo las columnas. Corregido con
  `Locale.ROOT`. `MLDataExporter` ya usaba `Locale.US` (no afectado); los
  formatos de GUI y reportes de texto conservan el locale (correcto para
  display). ⚠ Los CSV exportados ANTES de este fix desde máquinas con
  locale español están corruptos: re-exportar desde la BD si se necesitan.
- **P4** ✔: `help_02` explica el indicador "estab. %" del KPI de carga con
  puntero al tema 9.
- **P5** ✔: `ROADMAP_MEJORAS.md` marcado como documento histórico con
  puntero a esta bitácora y a PQMLogger.
- **Verificación**: compilación 81 fuentes Exit 0; suite 39/39 en verde;
  round-trip manual en BD temporal (esquema nuevo, migración de esquema
  viejo, export con NULLs y decimales con punto) — sin tocar la BD real.

Pendiente de deuda técnica queda solo **P3** (esperando el diseño del
análisis temporal). El rol de logger/dataset de campo vive en **PQMLogger**
(repo separado, `..\..\PQMLogger`).

---

## Actualización 2026-07-12 — Pivote estratégico y app hermana PQMLogger

### Decisión de dirección (2026-07-09)

HADES aún no puede ser el detector de cargas no lineales en producción que
pretendía: toda la base acumulada es de simulador y solo existe una medición
de campo (ION7400, N=1). La fase previa es operar como **PQM logger y
generador de datasets etiquetados** hasta acumular diversidad de datos de
campo; luego análisis (clustering para validar la taxonomía) y recién
entonces ML. Ese rol se implementó en una **app nueva y separada** para no
tocar HADES: **PQMLogger** (`C:\Users\admin\Documents\proyectos IA\PQMLogger`,
repo privado `MancoQpa/PQMLogger`).

El árbol de HADES NO se descarta — cambia de rol: etiquetador débil
(fuente `HADES_WEAK_LABEL` en las etiquetas de PQMLogger), baseline ROC
para cualquier ML futuro, y priorizador de ventanas a etiquetar.

### PQMLogger — estado al 2026-07-12 (v0.4, 34/34 tests en verde)

| Commit | Contenido |
|---|---|
| `7bd51c8` | Esqueleto: CLI headless, registro de feeders con metadatos (grupo vectorial, Scc, mezcla de clientes, Udin), SQLite |
| `8f32592` | Agregación IEC 61000-4-30: sub-intervalos 3 s → 10 min alineado al reloj, cuadrática + min/max/P95, flagging (huecos, parciales, dip/swell/interrupción vs Udin) |
| `04318b6` | CLI de etiquetado de verdad de terreno (label/list/delete/export-labels), solapes advertidos, fuentes con alias |
| `5b3c08c` | export-dataset: join intervals × labels × feeder, reglas de higiene (flagged/ambiguos/parciales excluidos), esquema versionado con rotación |
| `06c628d` | Espectro H1–H50 por intervalo (tabla interval_spectra, formato ancho como harmonic_spectra) y features espectrales de la **fase peor coherente** (M-001) en el dataset: thd_spectral, h3/h5/h7/h11/h13_h1 |

Reutiliza la capa de adquisición de HADES por classpath
(`HarmonicMonitor\classes` — compilar HADES antes). El dataset que produce
tiene exactamente las features que consume el árbol: la comparación
baseline-vs-ML sobre campo será directa. Pendientes propios: URCB/BRCB,
weak labels automáticas, agregados por fase en `intervals`.

### Publicación

Resumen SESEP reescrito (`Downloads\20_4278_resumo_v2.docx`, 2026-07-09)
alineado al pivote: plataforma de monitoreo/generación de datasets, hallazgo
PFC con N=1 como resultado destacado, estudio de dominancia, límites
explícitos. **Pendiente del autor**: revisión, lista de autores (J. Paris
confirmado), formato de plantilla y envío. Clasificación propuesta: C4.

### Pendientes vigentes (reordenados tras el pivote)

1. **Campaña de campo etiquetada** — ahora habilitada por PQMLogger; lo que
   falta es operativo: elegir feeders, relevar metadatos, determinar Udin
   en las unidades del ION por feeder, y una prueba de la tubería contra
   un ION real antes de la campaña formal. Sigue siendo el cuello de
   botella de todo lo demás.
2. Estructurales de HADES sin cambios: NILM sobre acumulado de campo,
   MSTA/MMTR al árbol, revisar FP=0.92 y cargas gemelas del PFC (esperan
   campo), URCB/BRCB (aplica a ambas apps), umbrales editables en GUI,
   desacoplar ruta JDK (hecho en PQMLogger; pendiente en HADES).
3. P3 de deuda técnica (diferido al diseño del análisis temporal).
4. Paper ICHQP/IEEE PES: espera los datos de la campaña.
5. Decisión del autor: submódulo `simulator` modificado y
   `build_paquetes_v11.ps1` sin trackear (preexistentes, fuera de commits).
