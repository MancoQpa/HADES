# Estudio sintético de dominancia de carga — clasificador HADES

> **Generado por**: `src/test/java/com/harmonicmonitor/analysis/DominanceStudy.java` — regenerar con `java -cp "classes;classes_test" com.harmonicmonitor.analysis.DominanceStudy`
> **Perfiles**: `docs/tabla_patrones_armonicos.md` §2 | **Umbrales**: `FeederConfig` por defecto

## Objetivo

La documentación afirmaba que la clasificación "es confiable solo con dominancia ≥~80%" de la carga de interés. Ese umbral estaba **asumido**. Este estudio lo **mide** con mezclas sintéticas: se barre la fracción de dominancia w de 100% a 0% (paso 0.1%) y se registra dónde el clasificador pierde la clase dominante y en qué clase degrada.

## Metodología y supuestos

| Magnitud | Modelo de mezcla | Fundamento |
|---|---|---|
| Fundamental | Suma vectorial de fasores (φ = arccos FP, cargas inductivas) | Circuitos AC elemental |
| Armónicos H2–H13 | Ley exponencial: I_n=(Σ I_n,i^α)^(1/α); α=1 (n<5), 1.4 (5≤n≤10), 2 (n>10) | IEC 61000-3-6:2008 §4.4 (diversidad de fase) |
| THD_I | Derivado del espectro mezclado (autoconsistente) | IEC 61000-4-7 |
| CV | Suma RMS de desviaciones independientes | Estadística (varianzas independientes) |
| K-Factor | Recalculado del espectro mezclado, n=1..13 | IEEE C57.110-2018 |
| FP, Q/S | P y Q lineales desde los fasores | IEEE 1459-2010 (componentes fundamentales) |
| THD_V | Promedio ponderado por w (aproximación) | — (propiedad de red, no de carga) |

**Supuestos de CV por perfil** (no provienen de la tabla): dominantes estables 1–4% (operación 24/7); fondo residencial lineal 15%; fondo comercial electrónico 10%.

**Límites del estudio**: mezcla sintética de 2 componentes; sin dinámica temporal; THD_I derivado del espectro puede diferir del `ThdA` declarado en la tabla §1; la ley α de IEC 61000-3-6 modela diversidad de fase típica, no el peor caso coherente.

## Verificación de extremos (w=100%: perfil puro)

| Perfil | THD espectral (%) | Clase esperada | Clase obtenida |
|---|---|---|---|
| `crypto_mining_pfc` | 4.0 | Minería Cripto (PFC Activo) | Minería Cripto (PFC Activo) ✓ |
| `crypto_mining` | 43.1 | Minería Cripto | Minería Cripto ✓ |
| `data_center` | 34.5 | Centro de Datos | Centro de Datos ✓ |
| `industrial` | 29.9 | Industrial | Industrial ✓ |
| `lighting` | 40.6 | Iluminación | Iluminación ✓ |

## Resultado principal — dominancia mínima por clase

Dominancia mínima w (en % de la corriente fundamental total) con la que el clasificador aún reporta la clase dominante:

| Carga dominante | vs. fondo lineal residencial | vs. fondo comercial electrónico |
|---|---|---|
| `crypto_mining_pfc` (Minería Cripto (PFC Activo)) | **97.8%** | **97.2%** |
| `crypto_mining` (Minería Cripto) | **68.5%** | **51.5%** |
| `data_center` (Centro de Datos) | **69.8%** | **52.6%** |
| `industrial` (Industrial) | **71.2%** | **68.9%** |
| `lighting` (Iluminación) | **35.9%** | **10.7%** |

## Curvas de degradación (tramos de clasificación)

### `crypto_mining_pfc` + fondo `linear_load (residencial lineal)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 97.8% | Minería Cripto (PFC Activo) ✓ |
| 97.7% – 0.0% | Carga Lineal |

### `crypto_mining_pfc` + fondo `mixed_electronic (comercial)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 97.2% | Minería Cripto (PFC Activo) ✓ |
| 97.1% – 76.5% | Carga Lineal |
| 76.4% – 0.0% | Mixta Electrónica |

### `crypto_mining` + fondo `linear_load (residencial lineal)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 68.5% | Minería Cripto ✓ |
| 68.4% – 19.0% | Carga Electrónica Ligera |
| 18.9% – 5.4% | Mixta Electrónica |
| 5.3% – 0.0% | Carga Lineal |

### `crypto_mining` + fondo `mixed_electronic (comercial)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 51.5% | Minería Cripto ✓ |
| 51.4% – 11.4% | Carga Electrónica Ligera |
| 11.3% – 0.0% | Mixta Electrónica |

### `data_center` + fondo `linear_load (residencial lineal)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 69.8% | Centro de Datos ✓ |
| 69.7% – 24.0% | Carga Electrónica Ligera |
| 23.9% – 6.9% | Mixta Electrónica |
| 6.8% – 0.0% | Carga Lineal |

### `data_center` + fondo `mixed_electronic (comercial)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 52.6% | Centro de Datos ✓ |
| 52.5% – 14.9% | Carga Electrónica Ligera |
| 14.8% – 0.0% | Mixta Electrónica |

### `industrial` + fondo `linear_load (residencial lineal)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 71.2% | Industrial ✓ |
| 71.1% – 28.2% | Carga Electrónica Ligera |
| 28.1% – 8.6% | Mixta Electrónica |
| 8.5% – 0.0% | Carga Lineal |

### `industrial` + fondo `mixed_electronic (comercial)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 68.9% | Industrial ✓ |
| 68.8% – 19.8% | Carga Electrónica Ligera |
| 19.7% – 0.0% | Mixta Electrónica |

### `lighting` + fondo `linear_load (residencial lineal)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 35.9% | Iluminación ✓ |
| 35.8% – 6.1% | Mixta Electrónica |
| 6.0% – 0.0% | Carga Lineal |

### `lighting` + fondo `mixed_electronic (comercial)`

| Dominancia w | Clase reportada |
|---|---|
| 100.0% – 10.7% | Iluminación ✓ |
| 10.6% – 0.0% | Mixta Electrónica |

## Interpretación

- El umbral único "≥80%" **no describe el comportamiento real**: la dominancia mínima depende fuertemente de la clase. Las clases con guardas de baja tolerancia (CRYPTO_MINING_PFC: Q/S ≤ 0.012 y FP ≥ 0.998) pierden la detección con muy poca carga de fondo, porque la reactiva del fondo contamina Q/S mucho antes de que se diluya el espectro.
- Las clases espectrales (INDUSTRIAL, DATA_CENTER, CRYPTO_MINING) resisten más: sus armónicos dominan la mezcla hasta fracciones menores. El CV del fondo suele ser la condición que rompe primero.
- Uso operativo: consultar la tabla de dominancia mínima por clase en lugar del 80% global. Cuando la dominancia estimada del feeder esté por debajo del valor de su clase objetivo, la clase reportada por HADES debe tratarse como orientativa y complementarse con el análisis temporal (componente constante vs. variable del espectro acumulado).
