# HADES — Referencia Técnica Completa
## Monitor de Calidad de Energía, Caracterización Probabilística y Análisis Comparativo

> **Autor:** Emilio Medina
> **Audiencia:** Ingenieros de distribución, técnicos de calidad de energía, investigadores
> **Nota:** Herramienta exploratoria. Los resultados no sustituyen medición normativa IEC 61000-4-30 Clase A.

**Convenciones de etiquetado usadas en este documento:**

| Etiqueta | Significado |
|---|---|
| `[MEDICIÓN]` | Valor leído directamente del IED vía MMS/IEC 61850 |
| `[DERIVACIÓN]` | Calculado matemáticamente a partir de valores `[MEDICIÓN]`; resultado exacto, no estimado |
| `[EMPÍRICO]` | Umbral o criterio sin respaldo de estándar formal; basado en literatura o experiencia de ingeniería |
| `[NORMATIVO]` | Umbral con respaldo explícito en IEEE, IEC o EN |
| `[TEÓRICO]` | Valor derivado de análisis matemático de circuitos (Fourier, teoría de convertidores) |
| `[ESTIMACIÓN-DEGRADADA]` | Valor generado por un modelo interno cuando el IED no provee el dato. **No es una medición independiente; las conclusiones basadas en él tienen validez reducida.** |

---

## Índice

1. [Punto de partida: HADES es un monitor de calidad de energía](#1-punto-de-partida)
2. [El problema que se intenta resolver](#2-el-problema-que-se-intenta-resolver)
3. [Modos de operación — medición real vs. modo degradado](#3-modos-de-operación)
4. [Nodos lógicos IEC 61850 — qué mide cada uno y por qué importa](#4-nodos-lógicos-iec-61850)
5. [El vector de caracterización — cálculo exacto de cada dimensión](#5-el-vector-de-caracterización)
6. [El árbol de decisión — lógica y umbrales reales del código](#6-el-árbol-de-decisión)
7. [El índice de electrónica 0–100](#7-el-índice-de-electrónica-0100)
8. [Por qué la caracterización probabilística supera a otros métodos](#8-por-qué-la-caracterización-probabilística-supera-a-otros-métodos)
9. [Descripción funcional — lo que el usuario puede hacer](#9-descripción-funcional)
10. [Análisis comparativo frente al ecosistema de herramientas](#10-análisis-comparativo)
11. [Alcance real y limitaciones honestas](#11-alcance-real-y-limitaciones-honestas)
12. [Escenarios de uso recomendados](#12-escenarios-de-uso-recomendados)
13. [Hoja de ruta](#13-hoja-de-ruta)

---

## 1. Punto de partida

HADES no nació como una herramienta de detección de cargas específicas. Es un **monitor de calidad de energía sobre IEC 61850** que:

- Se conecta a medidores compatibles (ION 7400 y similares) vía MMS/ACSE sobre TCP/IP puerto 102
- Lee nodos lógicos MMXU, MHAI, MSQI, MMTR, MSTA en tiempo real mediante polling configurable
- Registra espectro armónico H1–H50, THD de tensión y corriente, secuencias simétricas, potencia y energía
- Acumula todo en base de datos SQLite local cada 60 segundos, de forma continua y sin intervención del usuario

A partir de esa base, el proyecto plantea una hipótesis: **la acumulación continua de espectro armónico medido, combinada con análisis temporal, puede contribuir a identificar patrones de carga no lineal** — en particular cargas del tipo SMPS de alta densidad como miners de criptomonedas.

**Validación actual:** probado en laboratorio con cargas controladas y con simuladores IEC 61850 internos (modo Demo con 8 perfiles de carga). Los resultados son satisfactorios en esas condiciones. La validación en campo real, con feeders bajo condiciones de carga mixta real, **aún no se ha realizado** — es el paso siguiente del roadmap.

---

## 2. El problema que se intenta resolver

Un feeder con múltiples usuarios presenta en su punto de medición la **superposición de todas las corrientes**:

```
i_total(t) = i_miner(t) + i_iluminación(t) + i_residencial(t) + i_industrial(t) + ...
```

Aplicar FFT al total da la suma de los espectros:

```
I_total[k] = I_miner[k] + I_otros[k]
```

Con un solo sensor, este es un sistema de ecuaciones subdeterminado: con un solo observable (la corriente total) y N fuentes desconocidas, no existe solución algebraica única. Sin embargo, las cargas residenciales y comerciales **varían naturalmente** a lo largo del día, mientras que un miner opera a carga cuasi-constante las 24 horas. Esa diferencia de comportamiento temporal es el fundamento del enfoque de HADES.

```
Acumulando N muestras:
  la componente CONSTANTE del espectro ≈ firma de la carga persistente
  la componente VARIABLE del espectro  ≈ contribución de cargas que varían en el tiempo
```

Este es el fundamento de NILM (Non-Intrusive Load Monitoring): descomposición temporal en lugar de descomposición espectral instantánea. El clasificador actual trabaja muestra a muestra; la acumulación en SQLite habilita el análisis temporal en una etapa futura.

Las consecuencias prácticas de cargas electrónicas no declaradas en un feeder son:

- **Calentamiento excesivo** en transformadores y cables por pérdidas adicionales en componentes armónicas
- **Resonancia paralela** entre capacitancia de cables MT e inductancia de transformadores, que puede amplificar armónicos específicos hasta niveles dañinos
- **Afectación a otros clientes** del mismo alimentador por distorsión de la onda de tensión
- **Imprecisión en la facturación** si el medidor no compensa el desplazamiento de potencia reactiva inducido por los armónicos

---

## 3. Modos de operación

Esta sección es crítica para evaluar la validez de los resultados. HADES opera en dos modos según las capacidades del IED conectado.

### Modo primario — espectro medido (harmonicArrayInModel = true)

**Condición:** el IED expone el array `MHAI.HA.phsXHar.[1..50]` en su modelo MMS.

En este modo, las seis dimensiones del vector de caracterización provienen de mediciones directas del IED o de derivaciones matemáticamente exactas de esas mediciones. La clasificación opera sobre observables reales.

| Dimensión | Origen |
|---|---|
| CV | `[DERIVACIÓN]` — calculado desde historial de `MMXU.A` (medición real) |
| THD_I | `[MEDICIÓN]` directa desde `MHAI.ThdA`, o `[DERIVACIÓN]` exacta desde array HA |
| H5/H1 | `[MEDICIÓN]` — ratio de dos valores del array `MHAI.HA` leído del IED |
| H7/H1 | `[MEDICIÓN]` — ídem |
| H11/H1 | `[MEDICIÓN]` — ídem |
| FP | `[MEDICIÓN]` directa desde `MMXU.TotPF` |

### Modo degradado — espectro estimado (harmonicArrayInModel = false)

**Condición:** el IED expone `MHAI.ThdA` pero no el array `MHAI.HA`. Se da en medidores con modelo MMS simplificado.

En este modo, `MeasurementPoller` ejecuta la siguiente secuencia:

```java
// Paso 1: el IED solo provee THD_I; el array HA está en ceros
if (!comm.isHarmonicArrayInModel()) {
    harmonicAnalyzer.estimateMissingSpectrum(m);
    // → genera espectro con H5=35%, H7=20%, H11=8% relativos (template SMPS fijo)
    // → escala para que THD_estimado = THD_medido
    // → m.setSpectrumEstimated(true)
}

// Paso 2: calcula ratios desde el espectro (que puede ser estimado)
harmonicAnalyzer.calculateHarmonicRatios(m);
// → H5/H1, H7/H1, H11/H1 derivados del template, NO del IED

// Paso 3: clasifica
loadDetector.classify(m, config);
```

**Problema de circularidad:** el template `estimateCryptoSpectrum` asigna por construcción H5/H1 ≈ 0.35 y H7/H1 ≈ 0.20 (normalizados al THD medido). Cuando el clasificador verifica `H5/H1 > 0.15` y `H7/H1 > 0.10`, la condición es trivialmente verdadera para cualquier carga con THD_I > 15%, **independientemente del tipo real de carga**. Las dimensiones H5/H1, H7/H1 y H11/H1 del vector no aportan poder discriminante en este modo; son byproducts del THD.

| Dimensión | Origen en modo degradado | Validez para clasificación |
|---|---|---|
| CV | `[DERIVACIÓN]` desde `MMXU.A` — no afectado | Válida |
| THD_I | `[MEDICIÓN]` directa desde `MHAI.ThdA` — no afectado | Válida |
| H5/H1 | `[ESTIMACIÓN-DEGRADADA]` — template SMPS | **No válida como observable independiente** |
| H7/H1 | `[ESTIMACIÓN-DEGRADADA]` — template SMPS | **No válida como observable independiente** |
| H11/H1 | `[ESTIMACIÓN-DEGRADADA]` — template SMPS | **No válida como observable independiente** |
| FP | `[MEDICIÓN]` directa desde `MMXU.TotPF` — no afectado | Válida |

**Consecuencia práctica:** en modo degradado, el clasificador opera efectivamente como un umbral de dos dimensiones: `CV < 5% AND THD_I > 15%`. Las otras dimensiones del vector no contribuyen información nueva. Esto reduce la capacidad discriminante respecto al modo primario y aumenta el riesgo de falsos positivos.

La GUI marca visualmente las mediciones con espectro estimado (`m.spectrumEstimated = true`) para que el operador pueda identificar este modo.

**El ION 7400 de campo expone el array HA** (`harmonicArrayInModel = true`) en el esquema de LN del proyecto. El modo degradado se presenta principalmente con IEDs de terceros o con versiones de firmware simplificadas.

---

## 4. Nodos lógicos IEC 61850

IEC 61850 organiza las mediciones de un IED en **Nodos Lógicos (LN)**, cada uno con **Data Objects (DO)** específicos y **Data Attributes (DA)** que contienen el valor medido. HADES lee cinco LN del medidor; cada uno aporta información distinta al análisis.

La referencia de acceso tiene el formato:
```
IEDName + LDInst + "/" + LNClass + LNInst + "." + DOName + "." + DAName
→ ejemplo: cbo2LD0/M03_MMXU1.TotW.mag.f   (FC=MX)
```

---

### 4.1 MMXU — Measurement Unit (grandezas eléctricas fundamentales)

**Por qué existe:** es el nodo de medición principal de cualquier IED de media tensión. Expone las magnitudes eléctricas fundamentales. Sin MMXU no existe base de medición.

| DO | DA | Tipo IEC 61850 | Qué mide | Cómo lo usa HADES | Etiqueta |
|---|---|---|---|---|---|
| `PhV.phsA/B/C` | `cVal.mag.f` | WYE (CMV) | Tensión de fase L1, L2, L3 en V | Monitoreo de tensión; base para auto-detección de escala de potencia | `[MEDICIÓN]` |
| `A.phsA/B/C` | `cVal.mag.f` | WYE (CMV) | Corriente de fase L1, L2, L3 en A | Fuente para cálculo de CV (historial); escala del array HA a valores absolutos | `[MEDICIÓN]` |
| `TotW` | `mag.f` | MV scalar | Potencia activa total (W o kW) | Monitoreo de demanda; auto-detección de escala de potencia | `[MEDICIÓN]` |
| `TotVAr` | `mag.f` | MV scalar | Potencia reactiva total | Indicador de carácter inductivo/capacitivo de la carga | `[MEDICIÓN]` |
| `TotVA` | `mag.f` | MV scalar | Potencia aparente total | Referencia para FP | `[MEDICIÓN]` |
| `TotPF` | `mag.f` | MV scalar | Factor de potencia total | **Dimensión FP del vector 6D** | `[MEDICIÓN]` |
| `Hz` | `mag.f` | MV scalar | Frecuencia en Hz | Monitoreo de calidad de frecuencia | `[MEDICIÓN]` |

**Tipo WYE (CMV):** la corriente y tensión de fase se almacenan en `cVal.mag.f` dentro de un tipo CMV (Complex Measured Value). HADES accede al nodo padre `phsX` para que el servidor actualice simultáneamente magnitud, calidad (`q`) y timestamp (`t`), extrayendo luego el float de `cVal.mag.f`.

**Auto-detección de escala de potencia:** el ION 7400 puede reportar `TotW` en W o en kW según la configuración de firmware. HADES detecta automáticamente la unidad comparando el valor raw con `3·V·I` y eligiendo el factor de escala que produce un FP físicamente plausible (rango 0.01–1.10). Este proceso es una inferencia a partir de mediciones, no una estimación del valor de potencia.

---

### 4.2 MHAI — Harmonic Analysis (espectro armónico)

**Por qué existe:** el MMXU mide la corriente eficaz total (RMS de todos los armónicos combinados). El MHAI descompone esa corriente en sus componentes frecuenciales individuales. Sin MHAI no existe información espectral.

| DO | DA | Tipo IEC 61850 | Qué mide | Cómo lo usa HADES | Etiqueta |
|---|---|---|---|---|---|
| `ThdA.phsA/B/C` | `cVal.mag.f` | WYE (CMV) | THD de corriente por fase (%) | **Dimensión THD_I del vector 6D**; dispara alarmas | `[MEDICIÓN]` |
| `ThdPPV.phsAB/BC/CA` | `cVal.mag.f` | DEL (CMV) | THD de tensión fase-fase (%) | Entrada al árbol de decisión (UPSTREAM_DISTORTION); componente del índice 0–100 | `[MEDICIÓN]` |
| `HKf.phsA/B/C` | `cVal.mag.f` | WYE (CMV) | K-factor por fase | Estrés armónico en el transformador (registrado; sin uso activo en clasificador actual) | `[MEDICIÓN]` |
| `ThdOddA.phsA` | `cVal.mag.f` | WYE (CMV) | THD de armónicas impares (%) | Caracteriza tipo de no-linealidad; registrado para análisis futuro | `[MEDICIÓN]` |
| `ThdEvnA.phsA` | `cVal.mag.f` | WYE (CMV) | THD de armónicas pares (%) | Armónicas pares presentes indican semi-onda; ausentes en rectificadores simétricos | `[MEDICIÓN]` |
| `HA.phsAHar.[1..50]` | `cVal.mag.f` | array (MV) | Espectro H1–H50 por fase A, B, C | **Fuente de H5/H1, H7/H1, H11/H1 del vector 6D** (modo primario); gráfica de espectro | `[MEDICIÓN]` (modo primario) |

**Frecuencia de lectura del array HA:** el array completo (150 peticiones MMS por ciclo = 3 fases × 50 armónicos) se lee cada 6 ciclos de polling para no saturar el medidor. Entre lecturas se usa el último valor cacheado.

**Escala per-unit del ION 7400:** el ION 7400 reporta el array HA en valores per-unit (H1 = 1.0, H3 = 0.05, etc.). HADES detecta si `HA[0] ≤ 1.01` y en ese caso escala el array por la corriente real medida en `MMXU.A` para obtener valores en Amperios. Esto es una conversión de unidades, no una estimación del valor.

**Modo degradado (IED sin array HA):** si `harmonicArrayInModel = false`, HADES genera un espectro con el perfil fijo SMPS escalado al THD medido. Las implicaciones de rigor de este modo están documentadas en la Sección 3.

---

### 4.3 MSQI — Sequence and Imbalance (componentes simétricas)

**Por qué existe:** en una red trifásica equilibrada solo existe componente de secuencia positiva. La presencia de secuencia negativa indica desequilibrio de carga o de tensión; la secuencia cero indica corrientes de neutro (características de cargas monofásicas no lineales con armónicas triples).

| DO | DA | Tipo IEC 61850 | Qué mide | Cómo lo usa HADES | Etiqueta |
|---|---|---|---|---|---|
| `SeqA.c1` | `cVal.mag.f` | SEQ | Corriente secuencia positiva (A) | Base para desequilibrio de corriente | `[MEDICIÓN]` |
| `SeqA.c2` | `cVal.mag.f` | SEQ | Corriente secuencia negativa (A) | Desequilibrio I (%) = (c2/c1)×100 | `[MEDICIÓN]` |
| `SeqV.c1` | `cVal.mag.f` | SEQ | Tensión secuencia positiva (V) | Base para desequilibrio de tensión | `[MEDICIÓN]` |
| `SeqV.c2` | `cVal.mag.f` | SEQ | Tensión secuencia negativa (V) | Desequilibrio V (%) = (c2/c1)×100; alarma si supera 2% (EN 50160) | `[MEDICIÓN]` |

**Relevancia para caracterización:** un miner trifásico balanceado en racks produce desequilibrio de corriente bajo. Un miner monofásico o racks concentrados en una sola fase produce desequilibrio alto — información complementaria al clasificador espectral (no integrada aún en el árbol de decisión del clasificador).

---

### 4.4 MMTR — Metering (energía acumulada)

**Por qué existe:** registra la energía consumida en el tiempo. En IEC 61850 los contadores de energía usan el tipo BCR (Binary Counter Reading) con FC=ST (Status), no FC=MX (Medición), porque son valores acumulados de solo lectura.

| DO | DA | FC | Qué mide | Cómo lo usa HADES | Etiqueta |
|---|---|---|---|---|---|
| `TotWh` | `actVal` (INT64) | ST | Energía activa total acumulada (Wh) | Monitoreo de consumo histórico; ÷ 1000 → kWh | `[MEDICIÓN]` |
| `TotVAh` | `actVal` (INT64) | ST | Energía aparente total (VAh) | Referencia para FP promedio histórico | `[MEDICIÓN]` |
| `TotVArh` | `actVal` (INT64) | ST | Energía reactiva total (VArh) | Consumo reactivo persistente | `[MEDICIÓN]` |
| `SupWh` | `actVal` (INT64) | ST | Energía activa suministrada (Wh) | Importación/exportación en feeders con generación | `[MEDICIÓN]` |
| `SupVArh` | `actVal` (INT64) | ST | Energía reactiva suministrada (VArh) | Ídem para reactiva | `[MEDICIÓN]` |

**Relevancia para caracterización:** una carga de minería activa produce incremento constante de `TotWh` a tasa fija (derivada ≈ constante). Esta información es coherente con el CV bajo del vector 6D y está disponible en el dataset SQLite para análisis temporal futuro.

---

### 4.5 MSTA — Measurement Statistics (demanda)

**Por qué existe:** mientras MMXU reporta el valor instantáneo de potencia, MSTA reporta estadísticas de demanda integradas sobre ventanas de tiempo configurables en el IED (típicamente 15 o 30 minutos). Esto es relevante para la facturación por demanda máxima y para detectar perfiles de carga sostenida.

| DO | DA | Tipo IEC 61850 | Qué mide | Cómo lo usa HADES | Etiqueta |
|---|---|---|---|---|---|
| `AvW` | `mag.f` | MV scalar | Demanda activa promedio en el período (W o kW) | Monitoreo de demanda media integrada | `[MEDICIÓN]` |
| `MaxW` | `mag.f` | MV scalar | Demanda activa máxima en el período | Detección de picos de carga | `[MEDICIÓN]` |
| `MinW` | `mag.f` | MV scalar | Demanda activa mínima en el período | Carga base constante (indicador complementario al CV) | `[MEDICIÓN]` |
| `AvVAr` | `mag.f` | MV scalar | Demanda reactiva promedio | Complemento del FP promedio integrado | `[MEDICIÓN]` |
| `AvVA` | `mag.f` | MV scalar | Demanda aparente promedio | Base para análisis de capacidad del feeder | `[MEDICIÓN]` |

**Relevancia para caracterización:** la relación `MinW / MaxW` ≈ 1.0 en un miner (demanda prácticamente flat). En cargas residenciales o comerciales esta relación es típicamente 0.1–0.5. Este dato es información complementaria al CV; no está integrado formalmente en el clasificador actual.

---

## 5. El vector de caracterización

HADES calcula un **vector de 6 dimensiones** por cada muestra de polling. La validez de cada dimensión depende del modo de operación (ver Sección 3).

```
v = [ CV ,  THD_I ,  H5/H1 ,  H7/H1 ,  H11/H1 ,  FP ]
      (1)     (2)     (3)       (4)       (5)       (6)
```

**Matriz de validez según modo:**

| Dimensión | Modo primario (array HA del IED) | Modo degradado (espectro estimado) |
|---|---|---|
| CV | Válida — derivación de medición real | Válida — no afectada |
| THD_I | Válida — medición directa | Válida — medición directa |
| H5/H1 | Válida — medición directa del array HA | **No válida** como observable independiente |
| H7/H1 | Válida — medición directa del array HA | **No válida** como observable independiente |
| H11/H1 | Válida — medición directa del array HA | **No válida** como observable independiente |
| FP | Válida — medición directa | Válida — no afectada |

---

### Dimensión 1 — CV: coeficiente de variación de corriente

**Fuente:** `MMXU.A.phsA/B/C` `[MEDICIÓN]`

**Naturaleza:** `[DERIVACIÓN]` — calculado desde el historial de mediciones reales de corriente.

**Fórmula exacta** (del código `MeasurementPoller.java` + `LoadStabilityAnalyzer`):

```
CV = σ(Ī) / μ(Ī)

donde:
  Ī[i]  = (IL1[i] + IL2[i] + IL3[i]) / 3   corriente promedio de fase en la muestra i
  μ(Ī)  = (1/N) · Σ Ī[i]                    media sobre las últimas N muestras
  σ(Ī)  = √( (1/N) · Σ (Ī[i] - μ)² )       desviación estándar
  N     ≤ 60 muestras (ventana deslizante, ≈ 5 min a 5 s/ciclo)
```

**Por qué importa:**

El CV mide la variabilidad relativa de la corriente en el tiempo. Está normalizado por la media, por lo que es comparable entre feeders de distinta carga base.

- **CV muy bajo (< 5%):** carga de potencia cuasi-constante. Las fuentes de alimentación conmutadas (SMPS) regulan activamente la potencia consumida mediante lazo de control PWM, resultando en corriente de entrada casi constante independientemente de la carga computacional.
- **CV alto (> 15%):** carga variable. Cargas residenciales (electrodomésticos que encienden/apagan), iluminación con switching, procesos industriales batch.

**Umbral de clasificación:** `CV < 5%`
> `[EMPÍRICO]` — estimación de ingeniería para SMPS de alta densidad basada en literatura de fuentes conmutadas. Sin respaldo de estándar formal publicado. El valor exacto no ha sido calibrado con campaña de campo.

**Importancia conceptual:** el CV es la única dimensión temporal del vector. Mientras las dimensiones 2–6 son instantáneas (capturan el "qué" espectral en un instante), el CV captura "cómo se comporta la carga en el tiempo". Un espectro con H5 elevado y CV alto puede ser un VFD industrial con carga variable — no un miner. El CV filtra ese caso.

---

### Dimensión 2 — THD_I: distorsión armónica total de corriente

**Fuente primaria:** `MHAI.ThdA.phsA/B/C` `[MEDICIÓN]` — lectura directa del medidor.

**Fuente secundaria** (cuando el IED no provee `ThdA` directamente pero sí el array HA):

**Naturaleza:** `[DERIVACIÓN]` — cálculo matemáticamente exacto desde mediciones del array HA.

**Fórmula exacta** (del código `HarmonicAnalyzer.java`; referencia: IEC 61000-4-7, IEEE 519-2022):

```
THD_I (%) = 100 × √(H2² + H3² + H4² + ... + H50²) / H1

donde H1, H2, ..., H50 son las amplitudes de cada componente armónica
del array HA (índice 0 = H1 fundamental, índice k-1 = Hk)
```

Esta fórmula es la definición normalizada del THD según IEC 61000-4-7 §3.2.1. No hay aproximación ni estimación; es el cálculo exacto aplicado a los valores medidos.

**Distinción importante:** THD_I calculado desde el array HA medido (`[DERIVACIÓN]`) es fundamentalmente diferente del THD_I calculado desde un espectro estimado (`[ESTIMACIÓN-DEGRADADA]`). En el primer caso el resultado es matemáticamente exacto; en el segundo es circular (ver Sección 3).

**Por qué importa:**

El THD_I es la medida global de la no-linealidad de la corriente. Una corriente perfectamente senoidal tiene THD_I = 0%. La distorsión armónica causa pérdidas adicionales, calentamiento, y puede excitar resonancias en el sistema.

| Rango THD_I | Tipo de carga típico |
|---|---|
| < 5% | Lineal: motores, transformadores, hornos resistivos |
| 5–15% | Electrónica con PFC activo, variadores con filtro |
| 15–40% | SMPS sin PFC, iluminación LED masiva de baja calidad |
| > 40% | SMPS de alta densidad, rectificadores sin filtro |

**Umbrales en el árbol:**
- THD_I < 5%: límite para clasificar como carga lineal > `[NORMATIVO]` — alineado con IEEE 519-2022 (TDD, tabla 2)
- THD_I > 15%: umbral mínimo para rama cripto/datacenter > `[EMPÍRICO]` — "estimación bibliográfica genérica, no validada con campaña de campo en MT 23 kV" (Javadoc del código fuente)

---

### Dimensión 3 — H5/H1: ratio del quinto armónico

**Fuente:** `MHAI.HA.phsAHar.[5]` (índice 4 del array, base 0) `[MEDICIÓN]` — **solo en modo primario**.

**Fórmula exacta** (del código `HarmonicAnalyzer.java`):

```java
m.setH5h1Ratio(spec[4] / h1);   // spec[4] = H5; h1 = spec[0] = H1
```

```
H5/H1 = amplitud_H5_medida / amplitud_H1_medida
```

**Base teórica del valor de referencia:**

En un rectificador de 6 pulsos (puente trifásico de diodos), la teoría de convertidores demuestra que las componentes armónicas de corriente de la fuente son:

```
n = 6k ± 1   para k = 1, 2, 3, ...  →  H5, H7, H11, H13, H17, H19, ...

Amplitud teórica (corriente de carga ideal, sin filtrado):
  Hn = H1 / n  →  H5 = H1/5 = 20.0% de H1
```

> `[TEÓRICO]` — Mohan, Undeland, Robbins, "Power Electronics" §3; Chapman, "Electric Machinery Fundamentals". La amplitud real varía según el filtrado, la impedancia de la fuente y el tipo de SMPS.

**Por qué H5 y no H3:**

H3 es el armónico característico de cargas monofásicas no lineales (se suma en la corriente de neutro del sistema trifásico). H5 es el primer armónico no-triplén relevante para cargas trifásicas de alta potencia. Los miners con SMPS trifásico o puente de diodos trifásico tienen H5 notable; la iluminación LED monofásica tiene H3 dominante y H5 bajo. Esta diferencia permite al árbol separar ambos casos.

**Umbral de clasificación:** `H5/H1 > 15%` para rama cripto/datacenter
> `[EMPÍRICO]` — conservador respecto al valor teórico del 20% para tolerar filtrado parcial. Sin campaña de campo de validación.

---

### Dimensión 4 — H7/H1: ratio del séptimo armónico

**Fuente:** `MHAI.HA.phsAHar.[7]` (índice 6) `[MEDICIÓN]` — **solo en modo primario**.

**Fórmula exacta:**

```java
m.setH7h1Ratio(spec[6] / h1);   // spec[6] = H7
```

**Valor teórico de referencia:**

```
H7 = H1/7 = 14.3% de H1   [TEÓRICO]
```

**Por qué importa — poder discriminante combinado con H5/H1:**

H7 es el segundo armónico de la serie del rectificador de 6 pulsos. La presencia simultánea de H5 y H7 elevados es la firma dual del rectificador trifásico. Cargas con H5 alto pero H7 bajo son sospechosas de ser superposición de fuentes monofásicas (H3 dominante en cada una), no un rectificador trifásico.

| Perfil de carga | H5/H1 | H7/H1 | Interpretación |
|---|---|---|---|
| Iluminación LED monofásica | < 8% | < 5% | H3 dominante; H5 y H7 ausentes |
| SMPS alta densidad (miner) | > 15% | > 10% | Firma dual característica |
| Rectificador 6P industrial | > 12% | > 8% | Firma dual + H11/H13 presentes |

**Umbral de clasificación:** `H7/H1 > 10%`
> `[EMPÍRICO]` — estimación conservadora respecto al valor teórico 14.3%.

---

### Dimensión 5 — H11/H1: ratio del undécimo armónico

**Fuente:** `MHAI.HA.phsAHar.[11]` (índice 10) `[MEDICIÓN]` — **solo en modo primario**.

**Fórmula exacta:**

```java
m.setH11h1Ratio(spec[10] / h1);  // spec[10] = H11
```

**Valor teórico de referencia:**

```
H11 = H1/11 = 9.1% de H1   [TEÓRICO]
```

**Por qué importa — discriminación entre tipos de rectificador:**

H11 es el tercer armónico de la serie de 6 pulsos. Su presencia relativa distingue tipos de conversores:

- **Rectificador industrial de 6 pulsos (variador de velocidad, drive):** H5, H7, H11, H13 todos presentes con proporciones próximas a la serie 1/n. El árbol utiliza la condición combinada `H5>12% AND H7>8% AND H11>5% AND H13>4%` para identificar esta firma.
- **SMPS de alta densidad (miner):** H5 y H7 dominantes; H11 relativamente débil porque la topología de conmutado de alta frecuencia distribuye la energía de forma diferente al rectificador pasivo ideal.
- **Rectificador de 12 pulsos (grandes UPS):** H11 y H13 prominentes; H5 y H7 atenuados por cancelación de fase entre los dos puentes de 6 pulsos desfasados 30°.

H11 es la dimensión que permite al árbol separar la clasificación `INDUSTRIAL` de `CRYPTO_MINING` cuando ambas tienen alta THD y H5/H7 elevados.

**Umbral en el árbol:** `H11/H1 > 5%` (parte de la firma completa de 6 pulsos junto con H13 > 4%)
> `[TEÓRICO/EMPÍRICO]` — el valor teórico para rectificador ideal es 9.1%; el umbral de 5% es conservador para tolerar filtrado.

---

### Dimensión 6 — FP: factor de potencia

**Fuente:** `MMXU.TotPF.mag.f` `[MEDICIÓN]` — lectura directa del medidor.

**Procesamiento en HADES:**

```java
m.setPowerFactor(Math.min(1.0, readMxFloat(pfRef, association) * pfs));
// pfs = pfScaleFactor: 1.0 si IED reporta per-unit; 0.01 si reporta en porcentaje (ION 7400)
```

**Definición (IEEE Std 1459-2010):**

```
FP = P / S = Potencia activa (W) / Potencia aparente (VA)

En presencia de armónicas:
FP = cos(φ₁) · [H1 / √(H1² + H2² + ... + Hn²)]
   = FP_desplazamiento × FP_distorsión
```

**Por qué importa para la clasificación:**

El FP refleja simultáneamente el desfase entre tensión y corriente fundamental (FP_desplazamiento) y el contenido armónico (FP_distorsión). En el contexto del clasificador:

- **SMPS sin PFC activo:** rectificación directa con capacitor de filtro; corriente en pulsos. FP resultante ~ 0.60–0.75.
- **SMPS con PFC activo (Gold/Platinum efficiency):** el circuito PFC fuerza una corriente casi senoidal en fase con la tensión. FP ~ 0.95–0.99. Los miners modernos de alta eficiencia (ejemplos típicos de la industria) incorporan PFC.
- **Motores de inducción en carga nominal:** FP inductivo ~ 0.80–0.95.
- **Rectificadores industriales sin compensación:** FP ~ 0.85.

**Umbral en el árbol:** `FP > 0.92` distingue `CRYPTO_MINING` de `DATA_CENTER` dentro de la rama de firma estable + alta THD + H5/H7 dominantes.
> `[EMPÍRICO]` — "criterio empírico sin validación formal" (Javadoc del código fuente). No existe en la actualidad una base normativa que fije el FP de distinción entre tipos de instalación SMPS.

---

## 6. El árbol de decisión

El árbol navega el vector de 6 dimensiones en secuencia. Cada umbral está etiquetado con su origen.

```
INPUT: [CV, THD_I, THD_V, H5/H1, H7/H1, H11/H1, H13/H1, FP]
(THD_V y H13 participan en el árbol aunque no son dimensiones del vector 6D principal)

┌─────────────────────────────────────────────────────────────────────────────┐
│  ¿THD_V > 5% Y THD_I < 8% Y H5/H1 < 8%?                                  │
│  Ref.: IEC 61000-3-6:2008 §5 — nivel de planificación MT [NORMATIVO]       │
│  SÍ → [UPSTREAM_DISTORTION]                                                 │
│       La distorsión en tensión no se origina en corriente de este feeder    │
│  NO ↓                                                                       │
└─────────────────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  ¿THD_I < 5% Y H5/H1 < 5%?                                                 │
│  Ref.: IEEE 519-2022 tabla 2 (TDD) [NORMATIVO]                              │
│  SÍ → [LINEAR]  — motores, transformadores, resistencias                    │
│  NO ↓                                                                       │
└─────────────────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  ¿THD_I > 10% Y H5/H1 < 8% Y FP entre 0.75 y 0.95?                        │
│  (H3 dominante, H5 ausente: patrón de carga monofásica no lineal) [EMPÍRICO]│
│  SÍ → [LIGHTING]  — iluminación LED masiva                                  │
│  NO ↓                                                                       │
└─────────────────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  ¿CV < 5%?  [EMPÍRICO]                                                      │
│  SÍ: carga estable en el tiempo                                             │
│    ¿THD_I > 15% Y H5/H1 > 15% Y H7/H1 > 10%?  [EMPÍRICO + TEÓRICO]       │
│    SÍ:                                                                      │
│      ¿FP > 0.92?  [EMPÍRICO]                                                │
│        SÍ → [CRYPTO_MINING]                                                 │
│        NO → [DATA_CENTER]                                                   │
│    NO ↓                                                                     │
│                                                                             │
│  NO: carga variable → continúa abajo                                        │
└─────────────────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  ¿THD_I > 8% Y H5/H1>12% Y H7/H1>8% Y H11/H1>5% Y H13/H1>4%?             │
│  (firma completa de 6 pulsos con H11 y H13) [TEÓRICO + EMPÍRICO]           │
│  Ref.: Mohan-Undeland-Robbins "Power Electronics" §3                        │
│  SÍ → [INDUSTRIAL]  — variadores de velocidad, drives, rectificadores      │
│  NO ↓                                                                       │
└─────────────────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  ¿THD_I > 8% Y (H5/H1 > 8% O H7/H1 > 5%)?  [EMPÍRICO]                    │
│  SÍ → [ELECTRONIC_LIGHT]  — electrónica de consumo variada                 │
│  NO ↓                                                                       │
└─────────────────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  ¿THD_I > 5%?  [NORMATIVO — IEEE 519-2022]                                  │
│  SÍ → [MIXED_ELECTRONIC]                                                    │
│  NO → [LINEAR]                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Resumen de proveniencia de umbrales:**

| Umbral | Clasificación | Origen |
|---|---|---|
| THD_V > 5% (distorsión upstream) | UPSTREAM_DISTORTION | `[NORMATIVO]` IEC 61000-3-6 §5 nivel MT |
| THD_I < 5% (carga lineal) | LINEAR | `[NORMATIVO]` IEEE 519-2022 tabla 2 |
| THD_I > 15% (cripto/DC) | CRYPTO / DATA_CENTER | `[EMPÍRICO]` sin validación de campo |
| CV < 5% (carga estable) | rama cripto/DC | `[EMPÍRICO]` estimación de ingeniería |
| H5/H1 > 15% (cripto/DC) | CRYPTO / DATA_CENTER | `[EMPÍRICO]` conservador respecto a teórico 20% |
| H7/H1 > 10% (cripto/DC) | CRYPTO / DATA_CENTER | `[EMPÍRICO]` conservador respecto a teórico 14.3% |
| FP > 0.92 (CRYPTO vs DATA_CENTER) | CRYPTO | `[EMPÍRICO]` sin validación formal |
| H5>12%, H7>8%, H11>5%, H13>4% (industrial) | INDUSTRIAL | `[TEÓRICO]` serie 1/n del rectificador 6P |
| THD_V < 8% (EN 50160) | índice 0–100 | `[NORMATIVO]` EN 50160:2010 §4.3.4 |

---

## 7. El índice de electrónica 0–100

Score continuo que permite visualizar el grado de presencia de carga electrónica sin salto brusco entre categorías.

**Fórmula exacta** (del código `ElectronicLoadDetector.java`):

```
Score = C_CV + C_THDi + C_Ratios + C_THDv   (máximo 100 pts)

C_CV     = max(0, (cvThresh×5 - CV) / (cvThresh×5)) × 25
           [DERIVACIÓN desde MEDICIÓN MMXU.A]
           Máx 25 pts cuando CV=0; 0 pts cuando CV ≥ 5×cvThresh

C_THDi   = min(THD_I / 40.0 , 1.0) × 35
           [MEDICIÓN desde MHAI.ThdA]
           Máx 35 pts cuando THD_I ≥ 40%; normalizado al 40% como referencia de saturación
           Referencia: THD_I = 40% representa distorsión severa [EMPÍRICO como límite de saturación]

C_Ratios = min((H5/H1 + H7/H1) / 0.5 , 1.0) × 25
           [MEDICIÓN desde array HA — válido solo en modo primario]
           Máx 25 pts cuando (H5/H1 + H7/H1) ≥ 0.5
           Referencia: suma de teóricos ideales = 0.20 + 0.143 = 0.343 [TEÓRICO]

C_THDv   = min(THD_V / 8.0 , 1.0) × 15
           [MEDICIÓN desde MHAI.ThdPPV]
           Máx 15 pts cuando THD_V ≥ 8% (límite EN 50160 para MT) [NORMATIVO]
           En redes rígidas (alta Scc) este componente puede ser bajo aunque la carga exista.
           Por diseño, no cancela la detección — solo añade certeza cuando está presente.

Score_total = min(suma, 100)
```

**Pesos y razonamiento:**

| Componente | Peso | Justificación |
|---|---|---|
| CV (estabilidad temporal) | 25 pts | Discriminador exclusivo de carga persistente; sin contrapartida en herramientas estáticas |
| THD_I (distorsión) | 35 pts | Indicador primario de no-linealidad; mayor peso porque su base normativa es más sólida |
| H5+H7 (firma espectral) | 25 pts | Discrimina tipo de rectificador dentro de la no-linealidad; válido solo en modo primario |
| THD_V (propagación a red) | 15 pts | Confirma impacto real en tensión; bajo en redes rígidas sin penalizar detección |

**Advertencia en modo degradado:** cuando `spectrumEstimated = true`, `C_Ratios` se calcula desde el template y no aporta información independiente. El score efectivo en modo degradado depende solo de `C_CV + C_THDi + C_THDv` (máx 75 pts).

---

## 8. Por qué la caracterización probabilística supera a otros métodos

### El problema matemático fundamental

La separación exacta de fuentes en un feeder con un solo punto de medición es un sistema subdeterminado. Toda solución práctica es probabilística por naturaleza. La pregunta relevante es qué tan bien fundamentada está esa probabilidad y sobre qué observables opera.

### Frente a alarma por umbral de THD (método más común en SCADA)

Un umbral único `THD_I > 15% → alarma` dispara igual para un VFD industrial, un banco de LEDs y un miner. El vector 6D — cuando opera en modo primario con espectro medido — separa esos casos porque las **relaciones entre armónicas específicas son distintas para cada tipo de carga**, según predice la teoría de convertidores.

### Frente a analizadores portátiles de calidad de energía

Son el gold standard técnico para medición normativa, pero realizan capturas puntuales en el tiempo. Si la carga se apaga durante la visita de medición, no hay evidencia. HADES opera sobre el medidor ya instalado en la subestación, 24/7, sin intervención. La acumulación continua convierte una firma débil en evidencia estadística sobre el tiempo.

### Frente a modelos ML de caja negra

Un modelo ML necesita un dataset de entrenamiento etiquetado en condiciones de campo reales — que no existe actualmente para este problema. HADES **está construyendo ese dataset** (SQLite, H1–H50 medidos cada 60 s, exportable a CSV con ~162 features por fila). La caracterización probabilística actual es la infraestructura de datos que habilita el ML en una etapa futura.

### La ventaja de la dimensión temporal

HADES no intenta resolver el sistema subdeterminado en el espacio frecuencial instantáneo. Lo aborda en el espacio estadístico-temporal: la componente constante del espectro acumulado converge hacia la firma de la carga persistente, mientras que la componente variable refleja las cargas que fluctúan. Esta estrategia está respaldada por el campo de NILM y es matemáticamente sólida — **siempre que las dimensiones del vector operen sobre mediciones reales** (modo primario).

---

## 9. Descripción funcional

### 9.1 Conexión y configuración

- **IED real:** ingresar IP, nombre del IED y configuración de LN, o usar auto-discovery que detecta automáticamente MMXU, MHAI, MSQI, MMTR y MSTA disponibles en el modelo MMS.
- **Preset ION 7400:** un clic pre-llena todos los campos (IP: 169.254.0.10, Puerto: 102, IED: cbo2, Prefijo MMXU: M03\_, LD: LD0).
- **Modo demo:** simulador IEC 61850 integrado con 8 perfiles de carga; no requiere hardware.
- **Multi-feeder:** monitoreo simultáneo de N alimentadores, cada uno con su medidor IEC 61850.

### 9.2 Dashboard en tiempo real

Actualizado cada 1–60 s (polling configurable):
- Tensión y corriente eficaz por fase con gráfica de tendencia histórica
- Potencia activa, reactiva, aparente y factor de potencia
- THD de corriente y tensión por fase
- Clasificación actual de tipo de carga + score 0–100
- Indicación visual de modo degradado cuando `spectrumEstimated = true`
- Color de fondo según nivel de alarma: verde / amarillo / naranja / rojo

### 9.3 Panel de espectro

- Gráfica de barras H1–H50 normalizada (H1 = 100%)
- Tres barras por armónico (una por fase) o vista de fase promedio
- Marcadores visuales en H5, H7, H11, H13
- Indicación visual cuando el espectro es estimado

### 9.4 Sistema de alarmas

| Nivel | Condición típica |
|---|---|
| INFO | THD_I 5–15% |
| WARNING | THD_I 15–25%; CV elevado; H5/H1 > 20% |
| ALERT | THD_I 25–40%; clasificación `ELECTRONIC_LIGHT` |
| CRITICAL | THD_I > 40%; H5/H1 > 35%; clasificación `CRYPTO_MINING` o `DATA_CENTER` |

### 9.5 Almacenamiento y exportación

- SQLite automático continuo: sesiones, mediciones, espectros H1–H50, alarmas
- Exportación CSV por rango de tiempo en formato ML-ready (columna `spectrum_estimated` indica el modo de cada muestra)
- Visor COMTRADE: archivos `.cfg`+`.dat` con gráfica temporal y FFT por canal analógico

---

## 10. Análisis comparativo

| Funcionalidad | HADES | PQM Clase A (portátil) | Software PQ comercial | SCADA + módulo PQ |
|---|:---:|:---:|:---:|:---:|
| Protocolo IEC 61850 nativo | Sí | No | No | Sí |
| Múltiples feeders simultáneos | Sí | No | Sí (con hardware) | Sí |
| Espectro H1–H50 en tiempo real | Sí | Sí (Clase A) | Variable | Básico |
| Detección automática de tipo de carga | **Sí** | No | No | No |
| Clasificación cripto/datacenter/industrial | **Sí** | No | No | No |
| Almacenamiento continuo ML-ready | **Sí** | Variable | Variable | No |
| Simulador integrado para capacitación | **Sí** | No | No | No |
| Visor COMTRADE integrado | **Sí** | No | Variable | Variable |
| Conformidad normativa IEC 61000-4-30 | **No** | Clase A/B | Sí | Variable |
| Costo de licencia | **Gratuito** | 3k–15k USD/punto | 2k–8k USD | Parte de proyecto SCADA |

**Combinación sin equivalente conocido:** IEC 61850 nativo + análisis de armónicos en tiempo real + detección de tipo de carga + código abierto + GUI sin programación requerida.

**Limitación normativa fundamental:** los datos de HADES son **exploratorios, no normativos**. El ION 7400 es un medidor clase 0.2S, no un analizador IEC 61000-4-30 Clase A. Los resultados no constituyen evidencia normativa para procedimientos regulatorios ni certificaciones. Su uso correcto es identificar feeders sospechosos que luego se verifican con instrumentación Clase A si el resultado justifica la intervención.

---

## 11. Alcance real y limitaciones honestas

| Aspecto | Estado actual |
|---|---|
| Monitor de calidad de energía vía IEC 61850 | **Implementado y funcional** |
| Clasificador multivariable en modo primario (espectro medido) | **Implementado** — validado en laboratorio y simulador IEC 61850 |
| Clasificador en modo degradado (espectro estimado) | **Implementado** pero con validez reducida (circularidad, ver Sección 3) |
| Índice de electrónica 0–100 | **Implementado** |
| Acumulación SQLite + exportación CSV | **Implementado** |
| Multi-feeder simultáneo | **Implementado** |
| Visor COMTRADE | **Implementado** |
| Auto-discovery de LN en el IED | **Implementado** |
| Validación en campo real con cargas mixtas | **Pendiente** — paso crítico siguiente |
| Calibración de umbrales empíricos con datos de campo | **Pendiente** — tasa real de error desconocida |
| Separación de fuentes con solapamiento >= 20% | **No resuelto** — clasificador confiable solo con dominancia >= 80% |
| Análisis temporal de persistencia (explotación del dataset acumulado) | **Roadmap** |
| Modelo ML de desagregación | **Roadmap futuro** — condicionado a acumulación de datos en campo |

### Limitaciones específicas de rigor

**Modo degradado:** cuando el IED no expone el array HA, las dimensiones H5/H1, H7/H1 y H11/H1 del vector son `[ESTIMACIÓN-DEGRADADA]`. La clasificación en este modo opera efectivamente sobre dos observables independientes (CV y THD_I) y no sobre seis. El riesgo de falsos positivos es significativamente mayor.

**Umbrales empíricos:** CV < 5%, THD_I > 15% para cripto, y FP > 0.92 son estimaciones de ingeniería sin validación con campaña de campo. La tasa real de error del clasificador no es conocida hasta completar esa campaña.

**Latencia de detección:** HADES usa polling, no reportes MMS (URCB/BRCB). Eventos de duración inferior al período de polling no son capturados en la medición.

**Diseñado para ION 7400:** la configuración de referencias IEC 61850 está optimizada para el esquema de LN del ION 7400. Otros fabricantes o estructuras de LN requieren ajustes en `FeederConfig` e `IEC61850Communicator`.

---

## 12. Escenarios de uso recomendados

### 12.1 Screening inicial de feeders con carga electrónica sospechosa

1. Conectar HADES a los medidores IEC 61850 de los feeders de interés
2. Verificar que `harmonicArrayInModel = true` (modo primario); si no, los resultados del clasificador tienen validez reducida
3. Monitorear 24–48 horas para capturar el perfil diario completo
4. Revisar el panel de espectro: ¿H5/H1 y H7/H1 elevados en horario constante?
5. Revisar alarmas y score en el tiempo: ¿valores persistentes ALERT o CRITICAL?
6. Exportar CSV para análisis complementario

**Resultado:** HADES identifica cuáles feeders tienen firma compatible con carga electrónica pesada. Solo esos requieren verificación con instrumentación normativa Clase A.

### 12.2 Evaluación de impacto de un nuevo cliente electrónico

1. Monitorear el feeder en modo primario durante la semana previa a la conexión del nuevo cliente
2. Exportar la baseline (THD, espectro, clasificación, score)
3. Monitorear después de la conexión
4. Comparar: ¿aumentó THD? ¿cambió la clasificación? ¿subió el score?

### 12.3 Capacitación con el simulador

El simulador IonSimServer implementa exactamente el mismo modelo MMS que el medidor real. HADES no distingue entre datos del simulador y datos reales a nivel de protocolo.

1. Perfil `crypto_mining` → aprender la firma H5+H7 dominantes con CV bajo
2. Cambiar a `linear_load` → espectro colapsado, contraste visual directo
3. Perfil `industrial_drive` → aparecen H11 y H13; discutir diferencia con cripto
4. Perfil `capacitor_bank` → observar resonancia paralela amplificada

### 12.4 Investigación: construcción de dataset para ML

1. Desplegar HADES en feeders con tipos de carga conocidos, **en modo primario**
2. Operar durante 2–4 semanas (muestreo cada 60 s)
3. Etiquetar manualmente períodos según tipo de carga predominante
4. Exportar espectros H1–H50 (SQLite → CSV): ~162 features por fila, ~10 000+ filas, filtrando filas con `spectrum_estimated = true`
5. Entrenar clasificador estadístico con scikit-learn u otro
6. Reimplantar coeficientes en `ElectronicLoadDetector.java`

---

## 13. Hoja de ruta

| Prioridad | Mejora | Impacto |
|---|---|---|
| Alta | Campaña de campo + calibración de umbrales empíricos | Convierte estimaciones empíricas en umbrales validados |
| Alta | Inhabilitar clasificación automática en modo degradado (spectrumEstimated) | Elimina la circularidad; solo reportar THD_I y CV como observables válidos |
| Alta | Informe automático PDF con etiquetado de modo primario/degradado | Permite al receptor evaluar la calidad de los datos |
| Media | Ratio Flatness (H5+H7)/(H11+H13) en el clasificador | Discriminación adicional cripto vs. datacenter |
| Media | Análisis temporal de persistencia (ventana sobre historial CV + espectro) | Ataca el problema de solapamiento de cargas |
| Media | Reportes URCB en lugar de polling | Latencia de detección sub-segundo |
| Media | Soporte para más modelos de medidor (tabla de refs configurables) | Amplía aplicabilidad |
| Baja | Clasificador ML supervisado calibrado con datos de campo | Reemplaza árbol determinístico con umbrales empíricos |
| Baja | Dashboard web (servidor HTTP embebido) | Acceso sin instalación de cliente |

---

*HADES / HarmonicMonitor — Referencia Técnica Completa*
*Desarrollado por Emilio Medina — Licencia GPL v3*
