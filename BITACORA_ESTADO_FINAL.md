# Bitácora de Estado Final — HADES v1.2 (campaña de auditoría y mejoras, jul-2026)

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
