# HADES — Descripción Técnica Completa
## Harmonic Analysis and Detection Engine for Substations

> Documento generado para estudio y defensa técnica del sistema.
> Versión v1.0 — Alimentadores MT 23 kV — Protocolo IEC 61850 MMS.

---

## 1. ¿Qué es HADES y qué problema resuelve?

HADES es una aplicación de escritorio (Java/JavaFX) que se conecta en tiempo real a medidores de
calidad de energía en subestaciones eléctricas de media tensión (MT, típicamente 23 kV) y realiza
dos funciones principales:

1. **Monitoreo continuo de calidad de energía**: mide tensiones, corrientes, potencias, factor de
   potencia, distorsión armónica (THD), desequilibrio de tensión y frecuencia en cada alimentador.

2. **Detección y clasificación de cargas electrónicas**: identifica automáticamente si el
   alimentador está siendo consumido por cargas no lineales de alta densidad como granjas de
   minería de criptomonedas, centros de datos, variadores industriales o iluminación LED masiva.

### ¿Por qué esto importa?

Las cargas electrónicas modernas (fuentes switching, rectificadores) no consumen corriente
sinusoidal. Consumen pulsos de corriente en cada semiciclo, inyectando armónicos de corriente
en la red. Esto provoca:

- Calentamiento de transformadores (pérdidas adicionales por corrientes armónicas en el núcleo).
- Sobretensiones armónicas si la red tiene resonancia LC en esa frecuencia.
- Errores de medición en otros equipos conectados a la misma barra.
- Incumplimiento de normas de calidad de energía (IEC, IEEE, EN).
- Degradación del factor de potencia real (distorsión FP ≠ desplazamiento FP).

Una granja de minería de criptomonedas conectada a un alimentador MT de 23 kV puede inyectar
THD de corriente del 15–40%, con H5 y H7 dominantes, todo de forma estable y continua.
HADES detecta ese patrón específico y lo distingue de otras cargas.

---

## 2. Arquitectura General

```
  Medidor ION 7400          IEC 61850 MMS           HADES (PC Operador)
  (en subestación)    ──────────────────────────>   MmsDataMapper
  Puerto 102/10102          TCP/IP                   |
                                                     +─> HarmonicAnalyzer
                                                     +─> ElectronicLoadDetector
                                                     +─> ResonanceAnalyzer
                                                     +─> AlarmEngine
                                                     +─> DataStorage (SQLite + CSV)
                                                     +─> JavaFX GUI (dashboard, alarmas,
                                                              espectro, COMTRADE, tendencias)
```

### Componentes principales

| Módulo | Función |
|--------|---------|
| `IEC61850Communicator` | Conecta al IED vía MMS, suscribe al modelo de datos |
| `MmsDataMapper` | Mapea nodos IEC 61850 (MMXU, MHAI, MSQI, MMTR, MSTA) a modelo interno |
| `MeasurementPoller` | Ciclo de polling configurable (cada N segundos por feeder) |
| `SimulatedPoller` | Genera datos sintéticos realistas para demo/desarrollo sin IED físico |
| `HarmonicAnalyzer` | Calcula THD, ratios armónicos, estima espectro si el IED no lo provee |
| `ElectronicLoadDetector` | Árbol de decisión para clasificar tipo de carga |
| `ResonanceAnalyzer` | Calcula frecuencia de resonancia LC del circuito del alimentador |
| `AlarmEngine` | Motor de alarmas de 4 niveles con histéresis de 60 segundos |
| `DataStorage` | Persistencia SQLite + exportación CSV |
| `SpectralRecorder` | Campaña de caracterización espectral (H1–H50, sesiones temporales) |
| `ComtradeTriggerEngine` | Dispara capturas COMTRADE ante eventos de calidad de energía |
| `IonSimServer` | Servidor IEC 61850 simulado que responde como un ION 7400 real |

---

## 3. Protocolo de Comunicación: IEC 61850 MMS

IEC 61850 es la norma internacional para comunicación en subestaciones eléctricas. Define un
modelo de datos jerárquico y el protocolo de transporte MMS (Manufacturing Message Specification,
ISO 9506) sobre TCP/IP.

### Jerarquía del modelo de datos

```
IED (dispositivo físico)
  └── LogicalDevice (LD): LD0
        ├── MMXU1  — Medición trifásica fundamental (V, I, P, Q, S, PF, frecuencia)
        ├── M03_MMXU1 — Igual, con prefijo ION 7400
        ├── MHAI1  — Análisis armónico (THD, espectro H1–H50 por fase)
        ├── MSQI1  — Componentes simétricas (Fortescue: V+, V-, V0, I+, I-, I0)
        ├── MMTR1  — Energía acumulada (Wh activa, VArh reactiva, VAh aparente)
        └── MSTA1  — Demanda (potencia de demanda en ventana de 15 min)
```

HADES lee estos nodos usando referencias del tipo `cbo2LD0/M03_MMXU1.PhV.phsA.cVal.mag.f`
(tensión fase A, valor instantáneo, componente mag, tipo float).

### Por qué IEC 61850 y no Modbus/DNP3

- Es el estándar de facto en protección y medición de subestaciones modernas.
- El medidor ION 7400 (Schneider Electric) expone su modelo completo en IEC 61850.
- Permite leer espectro armónico completo (H1–H50) directamente sin procesamiento externo.
- El modelo de datos es auto-descriptivo (no requiere mapa de registros manual).

---

## 4. Matemáticas del Sistema

### 4.1 THD — Distorsión Armónica Total

**Definición (IEC 61000-4-7, IEEE 519-2022):**

```
         √(H2² + H3² + H4² + ... + Hn²)
THD% = ─────────────────────────────────── × 100
                      H1
```

Donde `H1` es la amplitud RMS del fundamental (50 o 60 Hz) y `Hn` son las amplitudes RMS
de los armónicos de orden n.

**Implementación en HADES** (`HarmonicAnalyzer.thdPercent()`):
- El array `spectrum[]` tiene índice 0 = H1 (fundamental), índice 1 = H2, etc.
- Se calcula la suma cuadrática desde índice 1 en adelante y se divide por H1.
- Si el IED ya reporta THD directamente (ION 7400 lo hace), no se recalcula.

**Diferencia entre THD y TDD (IEEE 519-2022):**
- THD usa H1 actual como denominador → varía según la carga.
- TDD usa la corriente de demanda máxima del contrato como denominador → más estable.
- HADES usa THD porque es lo que reporta el ION 7400. Para cumplimiento estricto IEEE 519
  en el PCC, se requeriría TDD, lo cual requiere conocer la demanda contratada.

### 4.2 Ratios de Armónicos Individuales

```
H5/H1 = amplitud(H5) / amplitud(H1)    (expresado como fracción, no porcentaje)
H7/H1 = amplitud(H7) / amplitud(H1)
H11/H1 = amplitud(H11) / amplitud(H1)
H13/H1 = amplitud(H13) / amplitud(H1)
```

**Por qué estos órdenes específicos:**

Un rectificador de puente completo (6 pulsos) produce armónicos de orden `6k ± 1`:
- k=1: H5, H7
- k=2: H11, H13
- k=3: H17, H19
- ...

La amplitud teórica (Fourier de una onda cuadrada 6-pulsos) es `1/n`:
- H5 teórico: 1/5 = 20% del fundamental
- H7 teórico: 1/7 ≈ 14.3%
- H11 teórico: 1/11 ≈ 9.1%
- H13 teórico: 1/13 ≈ 7.7%

En la práctica (con filtros de entrada, inductancias de línea) estas amplitudes son menores.
HADES usa umbrales conservadores (H5 > 15%, H7 > 10%) para tolerar el filtrado parcial.

**Referencia**: Mohan, Undeland, Robbins — *Power Electronics* §3; Chapman —
*Electric Machinery Fundamentals* cap. rectificadores.

### 4.3 Flatness Espectral (Índice de Forma)

```
              H5/H1 + H7/H1
Flatness = ─────────────────────
             H11/H1 + H13/H1
```

Este índice mide si el espectro armónico es "frontal" (armónicos bajos dominan)
o "plano/trasero" (armónicos altos tienen peso comparativo).

| Flatness | Interpretación | Tipo de carga | Perfiles de referencia |
|----------|---------------|---------------|----------------------|
| > 3.5 | Espectro muy frontal | SMPS de alta densidad (cripto, datacenter) | Cripto ≈ 4.7 / DC ≈ 7.7 |
| 2.8 – 3.5 | Zona de transición | SMPS con mayor filtrado o PFC parcial | — |
| 1.3 – 2.8 | Espectro equilibrado | Rectificador 6-pulsos clásico (VFDs industriales) | VFD ≈ 2.1–2.8 |
| < 1.2 | Armónicos altos dominan | Rectificador 12-pulsos (H11/H13 dominantes, H5/H7 cancelados) | — |

**Uso del umbral 3.5 en el clasificador:** el árbol de decisión usa `flatness < 3.5` como
condición del check INDUSTRIAL 6-pulsos para separarlo de CRYPTO/SMPS. El margen entre el
VFD más "frontal" de los perfiles de referencia (2.8) y el SMPS menos "frontal" (4.7) es de
≈ 1.9 unidades, suficiente para absorber variaciones reales sin solapamiento.

**Por qué se cancela H5/H7 en 12 pulsos:**
Un rectificador de 12 pulsos conecta dos puentes de 6 pulsos con 30° de desfase
(usando un transformador con devanados delta-estrella y delta-delta). Los armónicos H5 y H7
quedan 180° desfasados entre sí y se cancelan. Solo sobreviven H11, H13 (y superiores de
orden 12k±1). Flatness < 1.2 es la firma característica de esta topología.

**Referencia**: Chapman — *Electric Machinery Fundamentals*, sección rectificadores de 12 pulsos.

### 4.4 Coeficiente de Variación de Corriente (CV)

```
       σ(I)     desviación estándar de la corriente en ventana temporal
CV = ─────── = ────────────────────────────────────────────────────────
       μ(I)              media de la corriente en la misma ventana
```

**Interpretación física:**
- CV bajo (< 5%): carga estable, consumo prácticamente constante → típico de SMPS regulados
  (fuentes de minería, servidores de datacenter que operan 24/7 a plena carga).
- CV alto (> 15%): carga variable → motores, hornos, proceso industrial intermitente.

**Limitación importante:** el umbral CV < 5% es una estimación de ingeniería basada en
literatura SMPS/UPS. No está respaldado por ningún estándar formal. Una carga industrial
que opere en modo continuo también podría tener CV bajo.

### 4.5 Desequilibrio de Tensión — Dos Métodos

HADES implementa dos métodos complementarios:

**Método 1 — Máxima Desviación (EN 50160 / IEC 61000-4-30):**

```
                máx(|VL1 - Vmedia|, |VL2 - Vmedia|, |VL3 - Vmedia|)
Desequilibrio% = ──────────────────────────────────────────────────────── × 100
                                    Vmedia
```

Es simple, no requiere ángulos de fase, solo módulos de tensión.

**Método 2 — Fortescue (IEC 61000-2-2 / IEC 61000-4-30):**

```
                V_secuencia_negativa
Desequilibrio% = ──────────────────── × 100
                V_secuencia_positiva
```

Requiere las componentes simétricas (V+, V-, V0) calculadas por el IED.
El ION 7400 las reporta en el nodo MSQI (Sequence Quantities).

**Por qué Fortescue es más preciso:**
El método de máxima desviación solo usa magnitudes. Si las tres fases tienen la misma
magnitud pero con ángulos desiguales (desequilibrio angular puro), el método de desviación
dice "desequilibrio = 0" cuando en realidad existe. Fortescue captura ambos tipos
(magnitud Y angular) en un solo número.

**Prioridad en HADES:** se usa el valor Fortescue del IED (si disponible) y se cae al
método de desviación solo si el IED no reportó MSQI.

### 4.6 Análisis de Resonancia

La resonancia paralela ocurre cuando la reactancia capacitiva de los cables MT (o banco de
condensadores de corrección de FP) resuena con la reactancia inductiva del transformador.
En la frecuencia de resonancia, la impedancia de la red sube bruscamente — una corriente
armónica pequeña puede generar una sobretensión armónica grande.

**Método 1 — Resonancia LC directa:**

```
            1
f_res = ──────────      donde L = Xs / (2π·f1)  y  Xs = Vn² / Scc
         2π · √(LC)
```

- `Scc` = potencia de cortocircuito en el nodo (MVA) — parámetro de la red
- `Vn` = tensión nominal (V)
- `C` = capacitancia del cable (µF) — parámetro del alimentador

**Método 2 — Orden de resonancia por impedancias (Cigré WG C4.109):**

```
           ┌─────
h_res = √  │ Scc
           │────
           └ Qc
```

Donde `Qc` es la potencia reactiva capacitiva del banco o del cable (MVAR).

HADES calcula ambos métodos y promedia el resultado. Si el orden de resonancia `h_res`
coincide con un armónico de corriente dominante en el espectro medido (H5, H7, H11...),
se emite alarma de riesgo de resonancia.

**Por qué es importante:** un rectificador 6-pulsos que inyecta H5 fuerte en un alimentador
con resonancia en H5 puede producir sobretensiones armónicas de 200–300% del valor sin
resonancia. Esto puede destruir condensadores, disyuntores y equipos del cliente.

**Referencia**: IEC 61000-3-6:2008 §5; Cigré Technical Brochure 543 (WG C4.109).

### 4.7 Índice Electrónico (0–100)

Indicador compuesto para la GUI que resume la probabilidad de que el alimentador esté
dominado por cargas electrónicas de alta densidad:

```
Índice = 25·(CV_comp) + 35·(THDi_comp) + 20·(ratio_comp) + 5·(flatness_bonus) + 15·(THDv_comp)
```

| Componente | Peso | Fórmula | Justificación |
|------------|------|---------|---------------|
| CV inverso | 25 | `máx(0, (5·cvThresh - CV) / (5·cvThresh)) × 25` | Carga estable → alta probabilidad SMPS |
| THDi | 35 | `mín(THDi / 40%, 1) × 35` | Distorsión alta → no lineal |
| H5+H7 | 20 | `mín((H5+H7) / 0.5, 1) × 20` | Firma espectral de rectificador |
| Flatness | 5 | `mín(máx(flatness-1, 0) / 5, 1) × 5` | Espectro frontal confirma SMPS |
| THDv | 15 | `mín(THDv / 8%, 1) × 15` | Distorsión propagada a red |

### 4.8 FFT en el módulo COMTRADE (ComtradeDsp)

Para el análisis de formas de onda capturadas, HADES implementa:

**Algoritmo:** Cooley-Tukey FFT in-place (decimación en tiempo, radix-2).
Complejidad O(N·log₂N) vs O(N²) de la DFT directa.

**Ventana de Hann** (para reducir spectral leakage):
```
w(n) = 0.5 · (1 - cos(2π·n / (N-1)))
```

**Corrección de ganancia:** la ventana Hann tiene ganancia coherente = 0.5 (suma de
coeficientes ≈ N/2). Para recuperar la amplitud correcta del pico:
```
A_pico = |X[bin]| × 4 / N        (no 2/N como en ventana rectangular)
A_rms = A_pico / √2
```

**Cálculo adaptativo de N y fftSize:**

El código no usa una ventana fija de N muestras. En cambio:
```
sampPerCycle = round(fs / f0)          // muestras por ciclo
numCycles    = samples.length / sampPerCycle   // ciclos completos disponibles
N            = numCycles × sampPerCycle        // trunca al último ciclo completo
fftSize      = siguiente potencia de 2 ≥ N    // zero-padding para Cooley-Tukey
freqRes      = fs / fftSize                   // resolución del espectro resultante
```

**Por qué ciclos completos:** usar exactamente `numCycles` ciclos garantiza que todas
las frecuencias armónicas (f0, 2f0, 3f0...) caen en bins exactos de la DFT de N puntos.
Tras el zero-padding a `fftSize`, siguen cayendo en bins exactos porque la relación de
frecuencias se conserva. No se necesita interpolación de pico.

**Resolución según duración del buffer:**

| fs (Sa/s) | Duración buffer | Ciclos | N | fftSize | Δf | Referencia |
|-----------|----------------|--------|---|---------|-----|------------|
| 6400 | 160 ms | 8 | 1024 | 1024 | **6.25 Hz** | — |
| 6400 | 200 ms | 10 | 1280 | 2048 | **3.125 Hz** | IEC 61000-4-7 |
| 6400 | 320 ms | 16 | 2048 | 2048 | **3.125 Hz** | — |
| 3200 | 200 ms | 10 | 640 | 1024 | **3.125 Hz** | IEC 61000-4-7 |

Para un buffer de 200 ms a 6400 Sa/s (IEC 61000-4-7: 10 ciclos a 50 Hz):
```
N = 1280  →  fftSize = 2048  →  Δf = 6400/2048 = 3.125 Hz
bin(H1) = round(50 / 3.125) = 16  (exacto)
bin(H5) = round(250 / 3.125) = 80  (exacto)
```

**Corrección de amplitud con zero-padding:** la corrección `4.0/N` usa siempre `N`
(muestras reales con ventana aplicada), no `fftSize`. Esto es correcto porque el
zero-padding no aporta energía adicional — solo interpola el espectro entre bins.
La magnitud en el bin exacto del harmónico es la misma que sin zero-padding.

---

## 5. Árbol de Decisión para Clasificación de Cargas

El `ElectronicLoadDetector` aplica un árbol de decisión con 8 categorías de salida:

```
                            ┌─ THDv > 5% Y THDi < 8% Y H5/H1 < 8% ?
                            │   └─> UPSTREAM_DISTORTION (distorsión viene de aguas arriba)
                            │
                            ├─ THDi < 5% Y H5/H1 < 5% ?
                            │   └─> LINEAR
                            │
                            ├─ THDi > 10% Y H5 < 8% Y 0.75 < FP < 0.95 ?
                            │   └─> LIGHTING (H3 dominante, FP medio: lámparas LED)
                            │
                            ├─ THDi > 8% Y H5>12% Y H7>8% Y H11>5% Y H13>4% Y flatness 1.3–3.5 ?
                            │   └─> INDUSTRIAL (rectificador 6-pulsos: VFDs, drives)  ← (1)
                            │
                            ├─ CV < 5% Y THDi > 15% Y H5 > 15% Y H7 > 10% Y FP > 0.92 ?
                            │   └─> CRYPTO_MINING
                            │
                            ├─ CV < 5% Y THDi > 15% Y H5 > 15% Y H7 > 10% ?
                            │   └─> DATA_CENTER (firma similar a cripto, FP más bajo)  ← (2)
                            │
                            ├─ THDi > 8% Y H11 > 7% Y H13 > 6% Y flatness < 1.2 ?
                            │   └─> INDUSTRIAL (rectificador 12-pulsos: H5/H7 cancelados)
                            │
                            ├─ THDi > 8% Y (H5 > 8% O H7 > 5%) ?
                            │   └─> ELECTRONIC_LIGHT
                            │
                            ├─ THDi > 5% ?
                            │   └─> MIXED_ELECTRONIC
                            │
                            └─ default → LINEAR
```

**(1) El check 6-pulsos va ANTES de cripto/datacenter** — corrección de bug: un VFD
operando a velocidad cuasi-constante tiene CV < 5% en ventana corta y PF ≈ 0.93,
satisfaciendo todas las condiciones de CRYPTO_MINING. La condición `flatness < 3.5`
excluye SMPS/cripto (flatness ≈ 4.7–8) del check INDUSTRIAL. Ver sección 5.1.

**(2) DATA_CENTER llega solo si FP ≤ 0.92** — corrección de bug: el anterior "criterio
extendido" (flatness > 3.0 relajaba el requisito de FP) clasificaba DATA_CENTER como
CRYPTO_MINING. Eliminado. Ver sección 5.1.

**Limitación crítica del clasificador** (documentada en el código):

> El resultado es confiable solo cuando la carga de interés representa ~80% de la demanda
> total del alimentador. Con cargas mixtas, las firmas se superponen y la discriminación
> disminuye. Un alimentador industrial + carga electrónica ligera puede clasificarse
> erróneamente como MIXED_ELECTRONIC o INDUSTRIAL según cuál domine.

### 5.1 Bugs corregidos en el clasificador

**Bug 1 — VFD clasificado como CRYPTO_MINING** *(commit 56fdeb0)*

Un variador de frecuencia (VFD) de 6 pulsos operando a velocidad estable cumplía
inadvertidamente todas las condiciones del check CRYPTO_MINING:

| Condición CRYPTO | VFD a vel. estable | ¿Pasa? |
|-----------------|-------------------|--------|
| CV < 5% | Velocidad cuasi-constante en ventana corta → CV ≈ 1–3% | ✓ |
| THDi > 15% | THDi ≈ 23–28% | ✓ |
| H5/H1 > 15% | H5/H1 ≈ 19% (post-normalización) | ✓ |
| H7/H1 > 10% | H7/H1 ≈ 13% | ✓ |
| FP > 0.92 | FP ≈ 0.93–0.95 a plena carga | ✓ |

El check INDUSTRIAL 6-pulsos existía pero estaba DESPUÉS del check CRYPTO, por lo que
nunca se alcanzaba. La corrección fue moverlo antes y agregar `flatness < 3.5`:

```
VFD:   flatness = (0.30+0.20)/(0.10+0.08) = 2.78  → capturado por 6-pulsos ✓
CRYPTO: flatness = (0.35+0.22)/(0.07+0.05) = 4.75 → no capturado por 6-pulsos ✓
```

**Bug 2 — DATA_CENTER clasificado como CRYPTO_MINING** *(commit 56fdeb0)*

El "criterio extendido" (agregado para detectar SMPS sin PFC activo) decía:
```
si CV<5% Y THDi>15% Y H5>15% Y H7>10% Y flatness>3.0 → CRYPTO_MINING
```
El perfil DATA_CENTER tiene flatness = (0.28+0.18)/(0.04+0.02) = 7.67 > 3.0,
por lo que era capturado como CRYPTO_MINING antes de llegar al check DATA_CENTER.
PF = 0.88 (< 0.92) era irrelevante porque el criterio extendido no requería PF alto.

La corrección fue eliminar ese criterio extendido. Si una carga es estable, tiene
alta distorsión y espectro frontal pero PF < 0.92, la clasificación correcta
es DATA_CENTER (centro de datos con PFC parcial), no CRYPTO_MINING.

**Bug 3 — Perfil DATACENTER de SimulatedPoller clasificaba como ELECTRONIC_LIGHT** *(commit 1c13b4d)*

El perfil de simulación `simulateDatacenter()` usaba `thdI = 18%`, que al pasar por
`buildSpectrum` con los pesos relativos del datacenter producía:

```
normFactor = 0.18 / √(0.05²+0.28²+0.18²+0.06²+0.04²) = 0.18 / 0.344 = 0.523
H5/H1 = 0.28 × 0.523 = 0.147  <  umbral 0.15  → h5Dominant = false
H7/H1 = 0.18 × 0.523 = 0.094  <  umbral 0.10  → h7Present  = false
```

Al no satisfacer h5Dominant ni h7Present, el clasificador caía al check
`ELECTRONIC_LIGHT` (THDi > 8% con algo de H5 o H7). Adicionalmente, `pf = 0.99 > 0.92`
habría provocado una clasificación como CRYPTO_MINING si los ratios hubieran pasado.

Corrección aplicada en `SimulatedPoller.simulateDatacenter()`:

| Parámetro | Antes | Después | Efecto |
|-----------|-------|---------|--------|
| `thdI` | `18.0 + 2*g()` | `22.0 + 1.5*g()` | normFactor=0.639 → H5/H1=0.179 > 0.15 ✓ |
| `pf` | `0.990 + 0.003*g()` | `0.87 + 0.02*g()` | PF < 0.92 → DATA_CENTER, no CRYPTO ✓ |

Verificación a thdI = 22 %:
```
normFactor = 0.22 / 0.344 = 0.639
H5/H1  = 0.28 × 0.639 = 0.179 > 0.15  ✓
H7/H1  = 0.18 × 0.639 = 0.115 > 0.10  ✓
H11/H1 = 0.04 × 0.639 = 0.026 < 0.05  → sixPulseSignature = false ✓
flatness = (0.179+0.115)/0.026 ≈ 11.3  > 3.5  → no INDUSTRIAL ✓
PF = 0.87 < 0.92                        → highPF = false → no CRYPTO ✓
Resultado: stableLoad∧highTHD∧h5Dominant∧h7Present∧¬highPF → DATA_CENTER ✓
```

### 5.2 Verificación sistemática de todos los perfiles (post-corrección)

Tras aplicar los tres bugs, se verificaron los 14 perfiles de simulación contra el
árbol de decisión de `ElectronicLoadDetector` para confirmar clasificaciones correctas.

**IonSimServer — perfiles JSON** (8 perfiles, valores directos sin normalización):

| Perfil JSON | H5/H1 | H7/H1 | H11/H1 | H13/H1 | Flatness | PF | THDi | Clasificación esperada | Resultado |
|-------------|-------|-------|--------|--------|----------|----|------|------------------------|-----------|
| `crypto_mining` | 0.350 | 0.220 | 0.070 | 0.050 | 4.75 | 0.985 | 45% | CRYPTO_MINING | ✓ |
| `data_center` | 0.280 | 0.180 | 0.040 | 0.020 | 7.67 | 0.880 | 35% | DATA_CENTER | ✓ |
| `industrial_6pulse` | 0.250 | 0.110 | 0.090 | 0.080 | 2.12 | 0.930 | 28% | INDUSTRIAL | ✓ |
| `electronic_light` | 0.100 | 0.040 | — | — | — | 0.82 | 10% | ELECTRONIC_LIGHT | ✓ |
| `lighting_led` | 0.060 | 0.030 | — | — | — | 0.85 | 12% | LIGHTING | ✓ |
| `linear_load` | 0.030 | — | — | — | — | 0.96 | 4% | LINEAR | ✓ |
| `mixed_electronic` | 0.060 | 0.040 | — | — | — | 0.88 | 7% | MIXED_ELECTRONIC | ✓ |
| `normal_load` | 0.020 | — | — | — | — | 0.94 | 5.0% | LINEAR (default) | ✓ (*) |

(*) thdI = 5.0% exacto: el check `thdI < 5.0` es falso y `thdI > 5.0` también es falso,
cae al `return LoadType.LINEAR` final. Comportamiento correcto.

**SimulatedPoller — perfiles sintéticos** (6 perfiles, valores post-normalización por `buildSpectrum`):

| Perfil | THDi típico | H5/H1 | H7/H1 | H11/H1 | H13/H1 | Flatness | PF | Clasificación esperada | Resultado |
|--------|-------------|-------|-------|--------|--------|----------|----|------------------------|-----------|
| `CRYPTO_MINER` | ~35% | 0.284 | 0.178 | 0.052 | 0.041 | 4.75 | ~0.95 | CRYPTO_MINING | ✓ |
| `DATACENTER` | ~22% | 0.179 | 0.115 | 0.026 | — | ~11 | ~0.87 | DATA_CENTER | ✓ (bug 3 corregido) |
| `VARIABLE_SPEED_DRIVE` | ~25% | 0.196 | 0.131 | 0.065 | 0.052 | 2.78 | ~0.94 | INDUSTRIAL | ✓ (bug 1 corregido) |
| `INDUSTRIAL_LINEAR` | ~4% | 0.033 | — | — | — | — | ~0.85 | LINEAR | ✓ |
| `ARC_FURNACE` | ~22–34% | ~0.107 | — | — | — | variable | ~0.77 | ELECTRONIC_LIGHT (†) | ✓ |
| `MIXED_COMMERCIAL` | ~14–22% | ~0.096 | ~0.068 | — | — | variable | ~0.93 | ELECTRONIC_LIGHT (†) | ✓ |

(†) ARC_FURNACE y MIXED_COMMERCIAL no tienen categoría `LoadType` dedicada. La clasificación
en ELECTRONIC_LIGHT o MIXED_ELECTRONIC es aceptable — en ambos casos indica "carga no
lineal sin firma espectral definida", que es la conclusión correcta para esas topologías.

**Cobertura total: 14/14 perfiles clasificados correctamente** tras las tres correcciones.

---

## 6. Normas Utilizadas y Sus Alcances

### 6.1 IEC 61000-4-7:2002 — Método de Medición de Armónicos

**Qué define:** el método exacto para medir THD y armónicos individuales. Especifica:
- Ventana de análisis: 10 ciclos a 50 Hz (200 ms) o 12 ciclos a 60 Hz.
- Tipo de ventana: rectangular (IEC), aunque en la práctica se usan ventanas de Hann.
- Agrupación de armónicos: subgrupos armónicos (incluyen componentes interarmónicas adyacentes).

**Alcance:** aplica a equipos de medición. HADES lo usa como referencia para la fórmula
THD = √(ΣHn²)/H1. El ION 7400 implementa esta norma internamente.

**Limitación para HADES:** el ION 7400 tiene clase de exactitud 0.2S (IEC 62053-22), que
es una clase de medidores de energía activa, no una clase PQM (Clase A per IEC 61000-4-30).
Esto significa que la medición de armónicos del ION 7400 es "buena para monitoreo" pero no
"certificable para cumplimiento legal" sin validación adicional.

### 6.2 IEC 61000-3-6:2008 — Niveles de Planificación MT/AT

**Qué define:** los niveles de planificación de distorsión armónica de tensión en redes de
media tensión (MT: 1–35 kV), alta tensión (AT: 35–230 kV) y extra alta tensión (EAT).

**Valores clave para MT (tabla 1 de la norma):**

| Armónico | Nivel de planificación MT |
|----------|--------------------------|
| H5 (250 Hz) | 3% de tensión nominal |
| H7 (350 Hz) | 3% |
| H11 (550 Hz) | 2% |
| H13 (650 Hz) | 2% |
| THD total | 5% |

**Alcance y limitación importante:**

> IEC 61000-3-6 define niveles de *planificación* para la red de distribución, no
> límites de emisión individuales por instalación. Son objetivos del operador de red
> para el diseño de la infraestructura, no obligaciones directas del consumidor.

HADES los usa como referencia de umbral en el motor de alarmas (`AlarmEngine`). Esto es
correcto para una herramienta de monitoreo operacional, pero no equivale a decir que un
cliente que supere estos niveles esté "incumpliendo IEC 61000-3-6" — el cumplimiento de
emisión por instalación se rige por otras normas (IEC 61000-3-12 para corrientes >16A/fase).

### 6.3 EN 50160:2010 — Características de Tensión en Redes Públicas

**Qué define:** los parámetros y límites de calidad de tensión que el operador de red
(DSO/TSO) debe garantizar en el punto de conexión del cliente. Es una norma europea
(CENELEC), adoptada en muchos países latinoamericanos como referencia.

**Valores clave para MT (percentil 95%, ventana semanal):**

| Parámetro | Límite EN 50160 MT |
|-----------|-------------------|
| Desequilibrio de tensión | ≤ 2% (Fortescue Vneg/Vpos) |
| THDv total | ≤ 8% |
| Variación de frecuencia | 49.5–50.5 Hz (95% del tiempo) |

**Alcance:**

> EN 50160 aplica al punto de conexión del cliente (PCC), medido por el operador de red.
> No aplica a puntos interiores de la instalación del cliente.

HADES usa el límite de 8% THDv como umbral de alarma crítica y el 2% de desequilibrio
como umbral de riesgo PQ. Esto es conceptualmente correcto para monitorear si la
condición de la red en el primario del transformador de distribución está dentro de lo
que el cliente debería recibir.

### 6.4 IEEE Std 519-2022 — Control de Armónicos (Límites de Emisión)

**Qué define:** los límites de distorsión armónica de corriente y tensión en el PCC
(Point of Common Coupling) entre el sistema eléctrico y las instalaciones del usuario.

**Valores clave (tabla 2, corriente, ISC/IL > 1000, baja impedancia):**

| Armónico | TDD máximo permitido |
|----------|---------------------|
| H < 11 | 15% |
| 11 ≤ H < 17 | 7% |
| 17 ≤ H < 23 | 5.5% |
| THD total | 20% |

**Diferencia clave con IEC:** IEEE 519 usa TDD (Total Demand Distortion) en lugar de THD:

```
TDD% = √(ΣIh²) / IL × 100
```

Donde `IL` es la corriente de demanda máxima de carga en el PCC, NO el fundamental actual.

**Alcance:**

> IEEE 519 aplica a la interfaz entre la utilidad (red) y el usuario final.
> Los límites son responsabilidad compartida: la utilidad limita la distorsión de tensión
> que entrega; el usuario limita la distorsión de corriente que inyecta.

HADES referencia IEEE 519 en los umbrales de THDi (5% para carga lineal, 15% para
detección de carga electrónica). Como HADES usa THD (no TDD), los umbrales son
aproximados y conservadores en comparación con un análisis riguroso IEEE 519.

### 6.5 IEC 61000-3-12:2011 — Límites de Emisión >16 A/fase

**Qué define:** límites de emisión de corrientes armónicas para equipos con corriente de
entrada superior a 16 A/fase y hasta 75 A/fase en redes públicas de baja tensión.

**Relevancia para HADES:** referencia normativa en la documentación del clasificador de
cargas para justificar los umbrales de THDi. La norma aplica a baja tensión (≤ 1 kV), no
directamente a los alimentadores MT de 23 kV que monitorea HADES.

### 6.6 IEEE Std 1459-2010 — Definiciones de Medición de Potencia

**Qué define:** las definiciones consensuadas de potencia activa, reactiva, aparente y
factor de potencia para sistemas no sinusoidales y desequilibrados.

**Relevancia:** en presencia de armónicos, el factor de potencia tiene dos componentes:
- FP desplazamiento: cos(φ₁) entre fundamental de V e I.
- FP distorsión: depende del THD.
- FP total = FP_desplazamiento × FP_distorsión.

HADES usa el FP reportado directamente por el ION 7400, que implementa IEEE 1459 internamente.

### 6.7 IEC 61850 — Comunicación en Subestaciones

**Qué define:** el estándar completo de comunicación para protección, control, monitoreo
y automatización de subestaciones. Series principales:
- IEC 61850-1/-2: introducción y vocabulario.
- IEC 61850-6: lenguaje de configuración SCL (archivos ICD/CID/SCD).
- IEC 61850-7: modelo de datos (LN, DO, DA).
- IEC 61850-8-1: ACSE/MMS (protocolo de transporte TCP/IP).
- IEC 61850-9-2: Sampled Values (SV).

**Relevancia para HADES:** HADES usa IEC 61850-8-1 (MMS) para leer datos del ION 7400.
La capa de acceso es `ClientSap.associate()` de la biblioteca iec61850bean (Java).

---

## 7. El Visor COMTRADE — Importancia y Limitaciones

### ¿Qué es COMTRADE?

COMTRADE (Common Format for Transient Data Exchange for Power Systems) es el formato
estándar IEEE Std C37.111-2013 / IEC 60255-24 para almacenar registros de perturbaciones
en sistemas eléctricos de potencia. Consiste en dos archivos:
- `.cfg` (header): metadatos (número de canales, frecuencia de muestreo, fecha/hora, unidades).
- `.dat` (datos): muestras temporales en binario o ASCII.

### ¿Qué hace el visor COMTRADE de HADES?

HADES incluye un módulo que:
1. **Lee archivos COMTRADE** (ASCII y binario) almacenados en disco.
2. **Grafica las formas de onda** de los canales analógicos (V e I trifásico).
3. **Calcula la FFT** de cualquier canal seleccionado y muestra el espectro hasta H50.
4. **Exporta a CSV** los datos del registro.
5. **Genera capturas automáticas** (`ComtradeTriggerEngine`): cuando una medición
   supera un umbral (THD, desequilibrio, etc.), HADES sintetiza una forma de onda
   (`WaveformSynthesizer`) a partir del espectro medido y la escribe en formato COMTRADE.

### Evaluación de importancia

**Relevancia real (moderada-baja para el caso de uso primario):**

El visor COMTRADE es una herramienta de análisis post-evento, no de detección en tiempo real.
Su valor principal está en:

- **Documentación forense**: guardar evidencia de perturbaciones para disputas contractuales
  con el operador de red (demostrar que el THDv vino de afuera, no de la instalación del cliente).
- **Análisis profundo de un evento específico**: la forma de onda sintetizada permite
  visualizar cómo luce la corriente distorsionada de una carga tipo cripto-minería.
- **Interoperabilidad**: los archivos COMTRADE pueden abrirse en ATP-EMTP, PSCAD, MATLAB,
  DIgSILENT PowerFactory, SEL AcSELerator, etc.

**Limitación importante — formas de onda sintéticas:**

Las capturas COMTRADE que genera HADES son **síntesis matemáticas**, no registros de
oscilografía real. `WaveformSynthesizer` reconstruye la forma de onda sumando sinusoides:

```
i(t) = H1·sin(2π·f1·t + φ1) + H5·sin(2π·5f1·t + φ5) + H7·sin(2π·7f1·t + φ7) + ...
```

Los ángulos de fase son asumidos (no medidos). El resultado es educativo y representativo,
pero no es un oscilograma real del sistema.

**Para registros reales** se necesitaría un relé de protección o un registrador de
perturbaciones (IED con función de registro digital de perturbación, DFR) con acceso
a las muestras analógicas a alta velocidad (típicamente 256 muestras/ciclo = 12800 Hz @ 50 Hz).
El ION 7400 puede generar registros COMTRADE reales, pero HADES no los extrae
directamente por MMS — los lee si ya están disponibles en disco.

**Conclusión sobre el visor COMTRADE:**
Es una funcionalidad de valor añadido útil para documentación y análisis de segundo nivel,
pero no es el núcleo de HADES. El sistema de detección y alarmas en tiempo real opera
completamente sin él.

---

## 8. Módulo Simulador (IonSimServer)

Para desarrollo y demostración sin hardware real, HADES incluye un servidor IEC 61850
simulado que responde exactamente como un ION 7400:

- **Protocolo**: IEC 61850 MMS real (iec61850bean), no un mock.
- **Modelo**: archivo CID con MMXU, MHAI, MSQI, MMTR, MSTA.
- **Perfiles de carga**: 8 perfiles JSON (crypto_mining, linear_load, data_center,
  industrial_6pulse, industrial_12pulse, lighting_led, mixed, upstream_distortion).
- **Variación temporal**: los valores oscilan levemente alrededor del perfil para
  simular mediciones reales (ruido + ciclos de demanda).

Esto permite demostrar y validar todos los algoritmos de HADES sin conectarse a una
subestación real.

---

## 9. Limitaciones Honestas del Sistema

| Limitación | Impacto |
|-----------|---------|
| ION 7400 no es PQM Clase A (IEC 61000-4-30) | La medición de armónicos es de calidad de monitoreo, no certificable legalmente |
| THD vs TDD | HADES usa THD; IEEE 519 requiere TDD para cumplimiento estricto |
| Clasificador solo funciona bien con >80% de carga dominante | En alimentadores con cargas mixtas, la clasificación es orientativa |
| Formas de onda COMTRADE son sintéticas | No son registros reales de oscilografía |
| Espectro estimado (cuando IED no lo provee) usa perfil SMPS fijo | Puede clasificar erróneamente una carga industrial como electrónica |
| Ángulos de fase de armónicos no medidos | El análisis de potencia armónica (Pn, Qn por orden) no es posible sin fase |
| Resonancia calculada con parámetros estáticos | La red cambia con switching de cargas; la frecuencia de resonancia real puede variar |

---

## 10. Preguntas Frecuentes para Defensa

**¿Por qué IEC 61000-3-6 y no directamente IEC 61000-3-12?**
IEC 61000-3-12 aplica a BT (< 1kV). Para MT, IEC 61000-3-6 es la norma de referencia
para planificación. Los alimentadores que monitorea HADES son MT 23 kV — 61000-3-6 es
la elección correcta.

**¿Puede HADES probar legalmente que un cliente inyecta armónicos?**
No directamente. Para una disputa contractual se requiere: (a) instrumentación certificada
Clase A, (b) campaña de medición continua de 7 días mínimo, (c) aplicación de TDD (no THD),
(d) medición en el PCC oficial definido en el contrato. HADES es una herramienta de
monitoreo y alerta temprana, no un analizador de calidad certificado.

**¿Por qué Fortescue es mejor que máxima desviación para desequilibrio?**
Porque el desequilibrio real tiene dos componentes: magnitud Y ángulo de fase.
Si las tres fases tienen el mismo módulo pero ángulos no equidistantes (no 120°),
el método de desviación dice "desequilibrio = 0" aunque Fortescue daría un valor
significativo. Las redes reales pueden tener ambos tipos.

**¿Qué ocurre si H5+H7 son bajos pero hay THDi alto?**
El árbol de decisión cae en MIXED_ELECTRONIC si THDi > 5%, o queda en LINEAR si
THDi < 5%. Un THDi alto con H5/H7 bajos sugiere armónicos de tercer orden dominantes
(H3, H9) — típico de cargas monofásicas no balanceadas (iluminación, pequeños SMPS
monofásicos). El clasificador actual no tiene categoría específica para "H3 dominante"
más allá del caso de iluminación LED.

**¿Por qué la flatness usa (H5+H7)/(H11+H13) y no otro par?**
Porque H5, H7, H11, H13 son los armónicos canónicos del rectificador 6-pulsos (6k±1).
La relación entre los dos pares inferiores y superiores captura directamente si el
espectro "decae rápido" (SMPS, alto filtrado) o "decae lento" (rectificador clásico
con baja inductancia de entrada). El par H3/H9 describe cargas monofásicas, no
rectificadores trifásicos.

---

## 11. Referencias

### Normas y Estándares

**[IEC 61000-4-7]**
IEC 61000-4-7:2002+AMD1:2008 — *Testing and measurement techniques — General guide on
harmonics and interharmonics measurements and instrumentation, for power supply systems
and equipment connected thereto.*
International Electrotechnical Commission, Geneva.
> Uso en HADES: fórmula THD, ventana de análisis 200 ms (10 ciclos a 50 Hz), método de
> agrupación de armónicos. El ION 7400 implementa esta norma internamente.

**[IEC 61000-4-30]**
IEC 61000-4-30:2015 Ed.3 — *Testing and measurement techniques — Power quality measurement
methods.*
International Electrotechnical Commission, Geneva.
> Uso en HADES: definición de Clase A (instrumento certificado) vs clase de monitoreo.
> Agrupación cuadrática de valores por fase para THD trifásico promedio.
> El ION 7400 no es Clase A per esta norma — es clase 0.2S per IEC 62053-22.

**[IEC 61000-3-6]**
IEC 61000-3-6:2008 Ed.2 — *Limits — Assessment of emission limits for the connection of
distorting installations to MV, HV and EHV power systems.*
International Electrotechnical Commission, Geneva.
> Uso en HADES: niveles de planificación THDv para MT (5% normal, 8% límite absoluto);
> niveles individuales por armónico (H5: 3%, H7: 3%, H11: 2%, H13: 2%); principio de
> superposición para estimar contribución de una instalación al nivel de perturbación.
> **Alcance real**: define niveles de planificación del operador de red, no límites de
> emisión individuales por instalación.

**[IEC 61000-3-12]**
IEC 61000-3-12:2011 Ed.2 — *Limits for harmonic currents produced by equipment connected
to public low-voltage systems with input current > 16 A and ≤ 75 A per phase.*
International Electrotechnical Commission, Geneva.
> Uso en HADES: referencia bibliográfica para umbrales de THDi en el clasificador de
> cargas. Aplica a BT (< 1 kV); los alimentadores MT de 23 kV que monitorea HADES quedan
> fuera de su alcance técnico directo.

**[IEC 61000-2-2]**
IEC 61000-2-2:2002 — *Electromagnetic compatibility — Environment — Compatibility levels
for low-frequency conducted disturbances and signalling in public low-voltage power supply
systems.*
International Electrotechnical Commission, Geneva.
> Uso en HADES: método Fortescue para desbalance de tensión (Vneg/Vpos × 100), más
> preciso que el método de máxima desviación porque captura desequilibrio angular además
> del de magnitud.

**[EN 50160]**
EN 50160:2010+A1:2015+A2:2019+A3:2019 — *Voltage characteristics of electricity supplied
by public electricity networks.*
CENELEC, Brussels.
> Uso en HADES: límite de desequilibrio de tensión 2% (percentil 95%, ventana semanal);
> límite THDv 8% para MT; referencia para el método de máxima desviación de desequilibrio
> (§4.3.4). Aplica al punto de conexión del cliente (PCC).

**[IEEE 519-2022]**
IEEE Std 519-2022 — *IEEE Recommended Practice and Requirements for Harmonic Control in
Electric Power Systems.*
Institute of Electrical and Electronics Engineers, New York.
> Uso en HADES: límites de TDD por orden armónico (tabla 2); umbral THDi 5% como
> frontera carga lineal/no lineal; referencia para el módulo de cumplimiento normativo
> (CompliancePanel). **Nota**: HADES usa THD en lugar de TDD (que requiere conocer la
> demanda de contrato IL), lo que hace los umbrales conservadores pero aproximados.

**[IEEE 1459-2010]**
IEEE Std 1459-2010 — *IEEE Standard Definitions for the Measurement of Electric Power
Quantities Under Sinusoidal, Nonsinusoidal, Balanced, or Unbalanced Conditions.*
Institute of Electrical and Electronics Engineers, New York.
> Uso en HADES: definición de factor de potencia en presencia de armónicos (FP total =
> FP_desplazamiento × FP_distorsión). El ION 7400 implementa esta norma internamente.

**[IEC 62053-22]**
IEC 62053-22:2020 Ed.2 — *Electricity metering equipment — Particular requirements —
Part 22: Static meters for AC active energy (classes 0.1S, 0.2S and 0.5S).*
International Electrotechnical Commission, Geneva.
> Uso en HADES: contexto de exactitud del ION 7400 (clase 0.2S = error < 0.2% en
> energía activa). Esta clase es de medidores de energía, no es equivalente a la
> Clase A de IEC 61000-4-30 para calidad de energía.

**[IEC 61850]** (serie completa relevante)
- IEC 61850-7-1:2011 — *Basic communication structure — Principles and models.*
- IEC 61850-7-4:2010 — *Basic communication structure — Compatible logical node classes
  and data object classes.* (define MMXU, MHAI, MSQI, MMTR, MSTA y sus atributos)
- IEC 61850-8-1:2011 — *Specific communication service mapping (SCSM) — Mappings to
  MMS (ISO 9506-1 and ISO 9506-2).*
International Electrotechnical Commission, Geneva.
> Uso en HADES: protocolo de comunicación completo. MMXU para medición trifásica,
> MHAI para análisis armónico (H1–H50), MSQI para componentes simétricas, MMTR para
> energía, MSTA para demanda.

**[IEEE C37.111 / IEC 60255-24]**
- IEEE Std C37.111-2013 — *IEEE Standard for Common Format for Transient Data Exchange
  (COMTRADE) for Power Systems.*
  Institute of Electrical and Electronics Engineers, New York.
- IEC 60255-24:2013 — *Measuring relays and protection equipment — Common format for
  transient data exchange (COMTRADE) for power systems.*
  International Electrotechnical Commission, Geneva.
> Uso en HADES: formato de los archivos `.cfg` + `.dat` que lee y escribe el módulo
> COMTRADE. Las capturas automáticas de HADES usan el formato ASCII de C37.111-1999
> (subconjunto compatible con lectores modernos).

---

### Libros de Texto

**[Mohan 2003]**
Mohan, N., Undeland, T. M., & Robbins, W. P. (2003).
*Power Electronics: Converters, Applications, and Design* (3rd ed.).
John Wiley & Sons.
ISBN: 978-0-471-22693-2
> Uso en HADES: teoría de rectificadores de 6 y 12 pulsos (§3), amplitudes teóricas de
> armónicos (Hn = 1/n para rectificador ideal), filtrado de armónicos, PFC activo.

**[Chapman 2011]**
Chapman, S. J. (2011).
*Electric Machinery Fundamentals* (5th ed.).
McGraw-Hill Education.
ISBN: 978-0-07-352954-7
> Uso en HADES: rectificadores de 12 pulsos y cancelación de H5/H7 por desfase de 30°
> (justificación del criterio flatness < 1.2 para INDUSTRIAL 12-pulsos). También
> referenciado para la firma espectral de rectificadores 6-pulsos.

**[Arrillaga 2003]**
Arrillaga, J., & Watson, N. R. (2003).
*Power System Harmonics* (2nd ed.).
John Wiley & Sons.
ISBN: 978-0-470-85129-6
> Uso en HADES: referencia general para análisis armónico en redes de potencia,
> modelado de fuentes de armónicos, propagación en redes MT/AT, resonancia paralela
> y análisis de frecuencia de resonancia. Citado en SpectralRecorder.

**[Baggini 2008]**
Baggini, A. (Ed.). (2008).
*Handbook of Power Quality.*
John Wiley & Sons.
ISBN: 978-0-470-06561-7
> Uso en HADES: referencia para umbrales operacionales de calidad de energía,
> indicadores de distorsión armónica, y contexto normativo europeo (EN 50160,
> IEC 61000 series). Citado en SpectralRecorder.

---

### Informes Técnicos CIGRE

**[CIGRE C4.109]**
CIGRE Working Group C4.109 (2014).
*Resonance and Ferresonance in Power Networks* (relacionado) /
*Modelling and Aggregation of Loads — Harmonic Studies in Distribution Systems.*
CIGRE Technical Brochure, Paris.
> Uso en HADES: fórmula de orden de resonancia h_res = √(Scc/Qc) para estimación de
> la frecuencia de resonancia paralela en redes MT con condensadores de corrección de FP.
> Citado en ResonanceAnalyzer.java y SpectralRecorder.java.
> **Nota**: verificar número exacto de Technical Brochure en el catálogo CIGRE
> (e-CIGRE: www.e-cigre.org) — el WG C4.109 estuvo activo en el período 2010–2014.

---

### Biblioteca de Software

**[iec61850bean]**
Feuerhahn, S. et al. (contributors).
*iec61850bean — An IEC 61850 MMS client/server library for Java*, versión 1.9.0.
Licencia Apache 2.0.
Repositorio: https://github.com/beanit/iec61850bean
> Uso en HADES: implementación del stack MMS/ACSE/TCP para comunicación con el ION 7400
> y para el servidor simulador (IonSimServer). Clases clave: `ClientSap`, `ServerModel`,
> `BdaFloat32`, `BdaFloat64`.

---

### Nota sobre Alcance y Validación

Los umbrales del clasificador de cargas marcados como "estimación de ingeniería" en el
código fuente (CV < 5%, THDi > 15% para cripto, FP > 0.92 para CRYPTO vs DATA_CENTER)
**no están respaldados por estándar formal** y derivan de bibliografía general de SMPS/UPS
y criterio de ingeniería. Para uso en dictámenes técnicos, auditorías o disputas
contractuales, estos valores deben ser validados mediante una campaña de medición de campo
con instrumentación certificada (analizador de calidad de energía Clase A per
IEC 61000-4-30, mínimo 7 días continuos en el PCC).
