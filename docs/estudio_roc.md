# Estudio ROC — separabilidad de características del clasificador HADES

> **Generado por**: `src/test/java/com/harmonicmonitor/analysis/RocStudy.java` sobre `harmonic_monitor.db`
> Regenerar: `java -cp "classes;classes_test;lib/sqlite-jdbc-3.45.3.0.jar" com.harmonicmonitor.analysis.RocStudy`

## Dataset y verdad de terreno

Etiquetas por **sesiones de simulador con perfil conocido**, identificadas por la firma
estadística exacta de cada feeder contra `docs/tabla_patrones_armonicos.md`:

| Grupo | Feeders | Perfil (verdad) | Filas |
|---|---|---|---|
| **Positivo** (SMPS densa) | BCP-2, LAV-1, MET-02, VIN-2, PIL-5 | `crypto_mining` | 2481 |
| **Positivo** (SMPS densa) | BCP-4, LAV-3, MET-04 | `data_center` | 2298 |
| Negativo (lineal) | BCP-3, LAV-2, MET-03 | `linear_load` | 2309 |
| Negativo (lineal) | BCP-5, LAV-4, MET-05 | `normal_load` | 2251 |

**Total**: 9339 filas (4779 positivas, 4560 negativas).

**Excluidos**: feeders AL-xx y de campo/banco (103k+ filas) — sin verdad de terreno por fila; BCP-1, MET-01, AL-07, SLO-1 — escala de potencia corrupta (FP fuera de [-1,1]); filas sin espectro (h5h1 = 0).

> ⚠ **Circularidad declarada**: los datos etiquetados provienen del simulador, cuyos perfiles
> comparten diseño con los umbrales del clasificador. Este estudio mide separabilidad de
> características y coherencia interna; **no** sustituye la calibración con campo real mixto.

## ROC por característica (positivo = firma SMPS densa: cripto + datacenter)

| Característica | AUC | Óptimo de Youden | Umbral actual (`FeederConfig`) → desempeño |
|---|---|---|---|
| THD_I (%) | 0.9999 | ≥ 19.59 (TPR 1.000, FPR 0.000) | ≥ 15.00 → TPR 1.000, FPR 0.000 |
| H5/H1 | 0.9999 | ≥ 0.1159 (TPR 1.000, FPR 0.000) | ≥ 0.1500 → TPR 0.519, FPR 0.000 |
| H7/H1 | 0.9999 | ≥ 0.06625 (TPR 1.000, FPR 0.000) | ≥ 0.1000 → TPR 0.519, FPR 0.000 |
| CV (↓) | 0.5274 | ≤ 0.004702 (TPR 0.207, FPR 0.116) | ≤ 0.05000 → TPR 0.997, FPR 1.000 |
| FP | 0.6634 | ≥ 0.8639 (TPR 0.984, FPR 0.519) | — |
| Índice de electrónica 0-100 | 1.0000 | ≥ 34.48 (TPR 1.000, FPR 0.000) | — |

(↓ = discrimina hacia abajo: positivo si valor ≤ umbral. Para CV el AUC se calcula sobre -CV.)

## Matriz de confusión — árbol de decisión ACTUAL sobre las filas etiquetadas

Nota: el esquema histórico de `measurements` no registra K-Factor ni H3 (columnas añadidas
después); el árbol opera aquí sin esas dimensiones (K=0 → condición neutra; H3=0 → LIGHTING inalcanzable).

| Verdad \ Predicción | Carga Lineal | Carga Electrónica Ligera | Minería Cripto | Mixta Electrónica | 
|---|---|---|---|---|
| `crypto_mining` | 2 (0.1%) | 16 (0.6%) | 2463 (99.3%) | — | 
| `data_center` | — | 2298 (100.0%) | — | — | 
| `linear_load` | 2309 (100.0%) | — | — | — | 
| `normal_load` | 1141 (50.7%) | — | — | 1110 (49.3%) | 

## Percentiles por grupo (contexto de las distribuciones)

| Perfil | THD_I p5/p50/p95 | H5/H1 p5/p50/p95 | CV p5/p50/p95 | FP p50 |
|---|---|---|---|---|
| `crypto_mining` | 41.6 / 42.0 / 42.4 | 0.247 / 0.251 / 0.255 | 0.0000 / 0.0056 / 0.0072 | 0.985 |
| `data_center` | 19.8 / 20.0 / 20.2 | 0.118 / 0.119 / 0.122 | 0.0041 / 0.0056 / 0.0069 | 0.881 |
| `linear_load` | 4.0 / 4.0 / 4.0 | 0.023 / 0.024 / 0.024 | 0.0044 / 0.0055 / 0.0071 | 0.850 |
| `normal_load` | 5.0 / 5.0 / 5.0 | 0.029 / 0.030 / 0.030 | 0.0037 / 0.0057 / 0.0069 | 0.980 |

## Lectura de resultados

- **AUC ≈ 1.0 es esperable y NO es mérito del clasificador**: sobre perfiles de simulador
  las clases están separadas por construcción. El valor del estudio está en (a) verificar la
  coherencia árbol↔perfiles con el árbol *vigente* (matriz de confusión), (b) situar los
  umbrales actuales sobre la curva (¿dejan margen simétrico?) y (c) dejar la tubería ROC
  lista para re-ejecutarse cuando existan datos de campo etiquetados.
- Los umbrales actuales que muestren TPR < 1 con FPR = 0 están **dentro** del margen entre
  clases (conservadores); umbrales con FPR > 0 requieren revisión.
- La matriz de confusión con datos históricos expone dos efectos conocidos: el perfil
  `normal_load` oscila LINEAR/MIXED por estar clavado en el borde THD=5%, y el perfil
  `data_center` histórico (H5≈12% en estos datos, anterior a la tabla actual con H5=28%)
  cae en ELECTRONIC_LIGHT porque no supera la guarda H5>15% del nodo [6].
