# HADES: Caracterización Probabilística para Detección de Cargas en Feeders

> **Versión**: 2.0 — actualización post-campaña de campo ION7400-0d5885
> **Fecha**: 26/05/2026
> **Basado en**: `ElectronicLoadDetector.java`, `FeederConfig.java`, `FeederMeasurement.java`, `LoadType.java`

---

## Punto de partida: HADES es un monitor de calidad de energía

HADES nació como un **monitor de calidad de energía sobre IEC 61850** que:

- Se conecta a medidores compatibles (ION 7400 y similares) vía MMS/ACSE
- Lee nodos lógicos MMXU, MHAI, MSQI, MMTR, MSTA en tiempo real
- Registra espectro armónico H1–H50, THD de tensión/corriente, secuencias simétricas y potencia
- Acumula todo en SQLite cada 60 segundos, de forma continua
- Exporta a CSV/XLSX para análisis externo y eventual entrenamiento de modelos ML

A partir de esa base, HADES implementa un **clasificador de firma espectral** basado en un árbol de decisión multivariable: la hipótesis es que la acumulación continua de espectro armónico, combinada con indicadores de potencia, puede identificar patrones de carga no lineal — en particular ASIC miners de criptomonedas, tanto en su variante clásica (sin PFC) como en la moderna (con PFC activo).

**Estado de validación**:
- Validado en laboratorio con cargas controladas y simuladores IEC 61850 internos (8 perfiles de carga).
- **Validado en campo real** con feeder exclusivo de criptominería ASIC: ION7400-0d5885 (10.200.142.125), 23 kV / 50 Hz, 26/05/2026. Esta campaña motivó la extensión del clasificador para detectar `CRYPTO_MINING_PFC`.
- Validación en campo con feeders de carga mixta real: **pendiente** — es el paso crítico siguiente.

---

## El problema central: los ASIC miners modernos evaden el clasificador espectral

### El mecanismo de evasión

Las fuentes de alimentación de los miners ASIC modernos (Antminer S19/S21, Whatsminer M30/M50, Jasminer X16) incorporan un **PFC boost converter en modo CCM** (Continuous Conduction Mode). El PFC es un convertidor adicional que precede al rectificador y corrige activamente la distorsión de corriente inyectando una corriente en fase con la tensión.

El efecto en el espectro eléctrico es radical:

```
Rectificador 6 pulsos SIN PFC:        THD_I ≈ 25-30%,  FP ≈ 0.70,  H5 ≈ 25%,  H7 ≈ 14%
ASIC miner CON PFC activo (medido):   THD_I ≈ 3-4.4%,  FP ≈ 1.000, H5 ≈ 3.9%, H7 ≈ 0.2%
```

Valores medidos en campo (ION7400-0d5885, 10.200.142.125, 26/05/2026):

| Parámetro | Valor medido | Umbral "carga lineal" |
|---|---|---|
| THD_I (L1/L2/L3) | 3.0 / 3.1 / 3.0 % | < 5 % |
| THD_V (L1/L2/L3) | 1.91 / 1.85 / 1.89 % | < 3 % |
| Factor de potencia | 1.0000 | 0.95–1.00 |
| K-Factor | 1.05 | ≈ 1.0 |
| Crest Factor (I) | 1.45–1.46 | 1.41–1.50 |
| H5 relativo | 3.9 % | < 3 % (lineal típico) |
| H7 relativo | 0.2 % | < 1 % |
| Q/S | 0.0032 | < 0.05 (lineal) |

**Conclusión del medidor espectral clásico**: carga lineal. El clasificador original de HADES los clasificaba como `LINEAR` porque THD_I ≈ 3–4 % y H5 ≈ 3.9 % ambos pasan las guardias del nodo [3] (`thdI < 5.0 && h5h1 < 0.05`), sin llegar nunca al nodo `CRYPTO_MINING` que requería THD > 15 %.

### La solución: vector multidimensional ortogonal al espectro clásico

El clasificador espectral trabaja sobre amplitudes armónicas. El PFC corrompe esa firma. La solución es usar **indicadores que el PFC no puede suprimir** — indicadores de calidad de potencia que son ortogonales a la amplitud del espectro:

```
Vector PFC = { PF,  Q/S,  K-Factor,  H5/H7,  CV,  THD_I (rango) }
```

Estos seis indicadores forman la base de la clase `CRYPTO_MINING_PFC`.

---

## El vector de caracterización completo

### Dimensiones del clasificador v2

```
v = [ CV,  THD_I,  THD_V,  H5/H1,  H7/H1,  H11/H1,  H13/H1,  FP,
      H5/H7,  Q/S,  K-Factor,  Flatness ]
```

Las cuatro dimensiones nuevas respecto a v1:

| Feature nuevo | Cálculo | Por qué importa |
|---|---|---|
| **H5/H7** | H5/H1 ÷ H7/H1 | El PFC boost suprime H7 más que H5 por dinámica del lazo de control. ASIC miner: H5/H7 ≈ 19.5. Rectificador 6P sin PFC: H5/H7 ≈ 1.8 |
| **Q/S** | \|Q\| / S | El PFC anula la reactiva. ASIC miner medido: Q/S = 0.0032. Motor industrial: Q/S ≈ 0.30–0.50 |
| **K-Factor** | 1 + Σ(n²·Iₙ²) / I₁² | Mide el estrés armónico en transformadores. ASIC miner con PFC: K ≈ 1.05. Rectificador sin PFC: K > 1.5 (IEEE C57.110-2018) |
| **Flatness** | (H5+H7) / (H11+H13) | Forma del espectro: > 2.0 = espectro frontal (SMPS/cripto); 1.3–2.0 = 6-pulsos equilibrado; < 1.2 = 12-pulsos (H11/H13 dominan) |

### Perfiles de carga en el espacio de features (actualizado)

```
Carga lineal:              THD_I < 5%,    FP ≈ 1,      CV variable,  H5/H1 < 3%,  K ≈ 1.0
ASIC miner SIN PFC:        THD_I > 15%,   FP ≈ 0.70,   CV muy bajo,  H5/H1 > 15%, H7/H1 > 10%
ASIC miner CON PFC:        THD_I 3–5%,    FP ≈ 1.000,  CV muy bajo,  H5/H7 > 8,   Q/S < 0.012,  K ∈ [1.0,1.12]
Rectificador 6P industria: THD_I > 8%,    FP ≈ 0.85,   H5+H7 altos,  H11/H13 presentes, flatness 1.3–2.0
Datacenter (sin PFC):      THD_I > 15%,   FP < 0.92,   CV bajo,      H5/H1 > 15%
LED iluminación:           THD_I > 10%,   FP 0.75–0.95, H3 dominante
```

---

## El árbol de decisión completo (9 nodos)

El orden de evaluación es crítico: los nodos más restrictivos van primero para evitar capturas prematuras.

```
ENTRADA: medición { thdI, thdV, cv, h5h1, h7h1, h11h1, h13h1, pf, h5h7, qsR, kAvg }

[1] thdV > 5%  AND  thdI < 8%  AND  h5h1 < 8%
        └─→  UPSTREAM_DISTORTION
             (distorsión viene de aguas arriba, no de este feeder)

[2] thdI ∈ [1.5%, 6.5%]  AND  pf > 0.998  AND  qsR < 0.012
    AND  kAvg ∈ [1.0, 1.12] (si disponible)  AND  h5h7 > 8.0  AND  cv < 5%
        └─→  CRYPTO_MINING_PFC
             ⚠️ EVALUAR ANTES DE LINEAR — el PFC hace que thdI < 5% y h5h1 < 5%,
             lo que llevaría al nodo [3] sin este bloque.
             Modo relajado (sin K del IED): 4/5 condiciones restantes suficientes.

[3] thdI < 5%  AND  h5h1 < 5%
        └─→  LINEAR
             (ya no captura ASIC miners con PFC, interceptados en [2])

[4] thdI > 10%  AND  h5h1 < 8%  AND  pf ∈ (0.75, 0.95)
        └─→  LIGHTING
             (H3 dominante — lámparas LED masivas)

[5] thdI > 8%  AND  h5h1 > 12%  AND  h7h1 > 8%  AND
    h11h1 > 5%  AND  h13h1 > 4%  AND  flatness ∈ [1.3, 3.5)
        └─→  INDUSTRIAL (6-pulsos)
             (VFDs, drives — H11/H13 presentes discriminan de SMPS)

[6] cv < 5%  AND  thdI > 15%  AND  h5h1 > 15%  AND  h7h1 > 10%
        ├─ pf > 0.92  →  CRYPTO_MINING  (ASICs antiguos o sin PFC)
        └─ pf ≤ 0.92  →  DATA_CENTER    (servidores x86, PFC parcial)

[7] thdI > 8%  AND  h11h1 > 7%  AND  h13h1 > 6%  AND  flatness < 1.2
        └─→  INDUSTRIAL (12-pulsos)
             (VFDs de alta potencia con rectificador de 12 pulsos)

[8] thdI > 8%  AND  (h5h1 > 8% OR h7h1 > 5%)
        └─→  ELECTRONIC_LIGHT
             (electrónica ligera genérica)

[9] thdI > 5%
        └─→  MIXED_ELECTRONIC
             (mezcla de firmas sin patrón dominante)

default → LINEAR
```

---

## Justificación técnica de cada umbral CRYPTO_MINING_PFC

Los umbrales están definidos en `FeederConfig` y documentados individualmente. Resumen:

| Umbral | Valor | Justificación |
|---|---|---|
| `pfCryptoMinThreshold` | 0.998 | PFC boost CCM produce FP > 0.99 a plena carga. Ninguna carga industrial convencional alcanza FP > 0.998 sostenido. Medido: FP = 1.0000. Margen ±0.002. Ref: Mohan et al., "Power Electronics" §16. |
| `qsRatioCryptoMaxThreshold` | 0.012 | Q/S < 0.012 equivale a φ < 0.69°. Medido: Q=0.006 MVAR, S=1.875 MVA → Q/S = 0.0032. Motor 1.875 MVA a FP=0.90 tendría Q/S ≈ 0.43. Ref: IEEE Std 1459-2010. |
| `kFactorCryptoMaxThreshold` | 1.12 | Con THD=4% y H5=3.9%: K ≈ 1 + (25×0.039²) / 1 ≈ 1.038. Medido: K=1.05. Margen hasta THD ≈ 6%. Rectificador sin PFC con H5>15%: K > 1.5. Ref: IEEE C57.110-2018, Ec. 1. |
| `h5h7RatioCryptoMinThreshold` | 8.0 | PFC boost suprime H7 más agresivamente que H5. Medido: H5/H7 = 3.9%/0.2% = 19.5. Rectificador 6P clásico: H5/H7 ≈ 25%/14% = 1.8. Umbral 8.0 separa PFC (≥8) de 6-pulsos (≤3) con margen amplio. |
| `thdCryptoPfcMaxThreshold` | 6.5 % | Límite superior: rango medido 3.0–4.4%. Margen cubre carga parcial y PFC de menor eficiencia. |
| `thdCryptoPfcMinThreshold` | 1.5 % | Límite inferior: THD < 1.5% indicaría carga verdaderamente lineal (motor síncrono, resistencias puras). El PFC siempre deja residual H5 ≥ 2% típico. |

---

## Indicadores derivados persistidos en FeederMeasurement

A partir de cada ciclo de clasificación, HADES calcula y persiste tres indicadores adicionales en `FeederMeasurement` **independientemente de la clase resultante**. Esto permite visualizarlos en el dashboard y exportarlos al dataset de ML aunque la carga no sea clasificada como `CRYPTO_MINING_PFC`:

### `h5h7Ratio` — Ratio H5/H7

```java
static double computeH5h7Ratio(double h5h1, double h7h1) {
    if (h7h1 < 0.001) return (h5h1 > 0.005) ? 20.0 : 1.0;
    return h5h1 / h7h1;
}
```

Guardia para H7 ≈ 0 (IED no reporta o PFC muy agresivo): se retorna 20.0 como valor representativo de "PFC puro", manteniendo la semántica del indicador (ratio muy alto = PFC activo).

### `qsRatio` — Ratio |Q|/S

```java
static double computeQsRatio(FeederMeasurement m) {
    double s = m.getApparentPower();
    if (s < 1e-6) return 0.0;
    return Math.abs(m.getReactivePower()) / s;
}
```

El ION7400 reporta Q directamente via MMXU. Ref: IEEE Std 1459-2010 §3.

### `pfcCryptoScore` — Score PFC 0–100

Score compuesto para gauge de dashboard. Pesos:

| Componente | Peso máx | Normalización |
|---|---|---|
| Factor de potencia | 30 pts | Lineal PF=0.95 (0 pts) → PF=1.000 (30 pts) |
| Q/S inverso | 25 pts | Lineal Q/S=0 (25 pts) → Q/S=0.05 (0 pts) |
| H5/H7 ratio | 20 pts | Lineal ratio=0 (0 pts) → ratio=20 (20 pts) |
| K-factor inverso | 15 pts | Lineal K=1.00 (15 pts) → K=1.12 (0 pts). Si K no disponible: 7.5 pts (neutro) |
| CV inverso | 10 pts | Lineal CV=0 (10 pts) → CV=0.25 (0 pts) |

Un score ≥ 75 con clase `LINEAR` es señal de alerta: el feeder puede estar siendo clasificado erróneamente por ausencia del IED del K-Factor o variación temporal.

---

## Por qué este enfoque supera a alternativas más simples

### Frente a alarma por umbral de THD

Un umbral único (`THD_I > 5% → alarma`) no dispara para ASIC miners con PFC activo porque THD_I ≈ 3–4 %. Un umbral único (`THD_I > 3% → alarma`) generaría falsos positivos en motores y transformadores en carga. El vector multidimensional resuelve la ambigüedad usando indicadores ortogonales.

### Frente a analizadores portátiles de calidad de energía

Son el gold standard técnico, pero realizan mediciones puntuales. Si la carga se reduce o apaga durante la medición, no hay evidencia. HADES opera sobre el medidor ya instalado en la subestación, sin intervención, 24/7. La acumulación continua convierte una firma débil en evidencia estadística robusta.

### Frente a modelos ML de caja negra

Un modelo ML necesita un dataset de entrenamiento etiquetado en condiciones de campo reales. HADES **está construyendo ese dataset** (SQLite, H1–H50 + indicadores derivados cada 60 s, exportable a CSV/XLSX via `MLDataExporter`). La caracterización determinista de hoy es la tubería de datos que habilita el ML de mañana.

---

## Alcance real y limitaciones

| Aspecto | Estado |
|---|---|
| Monitor de calidad de energía vía IEC 61850 | **Implementado y funcional** |
| Clasificador multivariable — árbol de 9 nodos | **Implementado**, validado en laboratorio, simulador y campo |
| Detección `CRYPTO_MINING_PFC` (ASIC con PFC activo) | **Implementado y validado en campo** — ION7400-0d5885, 26/05/2026 |
| Score PFC 0–100 para gauge de dashboard | **Implementado** |
| Indicadores derivados persistidos (h5h7Ratio, qsRatio, pfcCryptoScore) | **Implementado** |
| Acumulación SQLite + exportación CSV/XLSX | **Implementado** |
| Multi-feeder simultáneo | **Implementado** |
| Validación en campo con feeders de carga **mixta** real | **Pendiente** — paso crítico siguiente |
| Separación de fuentes con solapamiento ≥ 20% | **No resuelto** — el clasificador requiere dominancia ≥ 80% de la carga de interés |
| Modelo ML de desagregación (NILM) | **Roadmap futuro**, condicionado a acumulación de datos de campo |

### Limitación crítica: dominancia de carga

El clasificador trabaja sobre la señal agregada del feeder. Si la carga de interés (e.g., miner ASIC) representa menos del ~80% de la demanda total, su firma se diluye con la de otras cargas. En ese escenario:

- El árbol de decisión producirá resultados de menor confianza.
- El `pfcCryptoScore` puede ser útil como indicador continuo incluso cuando la clase no es `CRYPTO_MINING_PFC`.
- La acumulación temporal (análisis de 24h, 7d) puede revelar el componente constante de la carga de interés vs. la variabilidad natural de las cargas residenciales/comerciales.

### El caso del feeder exclusivo de criptominería

La campaña de campo (ION7400-0d5885) correspondía a un **feeder exclusivo**: 100% de la carga eran ASIC miners. En ese escenario ideal el vector PFC era perfectamente discriminante. La extrapolación a feeders mixtos requiere verificación adicional.

---

## Estructura de clasificación en el código

```
ElectronicLoadDetector.classify(FeederMeasurement m, FeederConfig cfg)
    ├── computeH5h7Ratio(h5h1, h7h1)          → m.h5h7Ratio
    ├── computeQsRatio(m)                       → m.qsRatio
    ├── kAvg = (K_L1 + K_L2 + K_L3) / 3
    ├── calculatePfcScore(...)                  → m.pfcCryptoScore
    └── classifyInternal(...)                   → m.detectedLoadType
            ├── [1] UPSTREAM_DISTORTION
            ├── [2] CRYPTO_MINING_PFC  ⟵ nuevo, antes de [3]
            ├── [3] LINEAR
            ├── [4] LIGHTING
            ├── [5] INDUSTRIAL (6-pulsos)
            ├── [6] CRYPTO_MINING / DATA_CENTER
            ├── [7] INDUSTRIAL (12-pulsos)
            ├── [8] ELECTRONIC_LIGHT
            └── [9] MIXED_ELECTRONIC
```

Los indicadores derivados se calculan y persisten **en todos los casos**, no solo cuando la clase es `CRYPTO_MINING_PFC`. Esto permite que el dashboard muestre el score PFC como señal de alerta incluso para feeders clasificados como `LINEAR`.

---

## Referencias

- IEEE Std 519-2022 — Recommended Practice for Harmonic Control in Electric Power Systems
- IEC 61000-3-6:2008 — Planning levels for harmonic voltages in MV/HV/EHV
- IEC 61000-3-12:2011 — Limits for harmonic currents produced by equipment > 16 A/phase
- EN 50160:2010 — Voltage characteristics of electricity supplied by public distribution networks
- IEEE Std 1459-2010 — Definitions for the Measurement of Electric Power Quantities
- IEEE C57.110-2018 — Recommended Practice for Establishing Liquid-Filled and Dry-Type Power and Distribution Transformer Capability When Supplying Nonsinusoidal Load Currents
- Mohan, Undeland, Robbins — *Power Electronics: Converters, Applications, and Design*, §16 (Active PFC converters)
- Chapman — *Electric Machinery Fundamentals*, capítulo rectificadores 12-pulsos
- Medición de campo: ION7400-0d5885 (10.200.142.125), feeder exclusivo criptominería ASIC, 23 kV / 50 Hz, 26/05/2026
