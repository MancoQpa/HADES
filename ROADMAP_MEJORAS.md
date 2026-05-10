# HADES — Roadmap de Mejoras Técnicas
**Proyecto:** ANDE-SIGFE · HarmonicMonitor
**Fecha de última actualización:** 2026-03-25
**Estado del clasificador actual:** Árbol de decisión determinístico (Opción A/B parcial)

---

## Contexto del instrumento de campo

El **ION 7400** es un contador de energía clase **0.2S** (IEC 62053-22):
- Alta precisión en medición de energía: ±0.2% en Wh/VArh/VAh
- **NO es PQM Clase A** (IEC 61000-4-30): sus valores de THD/armónicos son
  mediciones instantáneas o de ventana corta interna, sin agregación cuadrática
  de 10 minutos
- Consecuencia: la agregación temporal normativa recae completamente en HADES

Esta distinción es crítica para interpretar los datos y para el disclaimer de la
aplicación ("herramienta exploratoria, no normativa").

---

## Mejoras implementadas en 2026-03-25

### Corrección A — THD trifásico por RMS cuadrático
**Archivo:** `model/FeederMeasurement.java`
**Método:** `getThdCurrentAvg()`, `getThdVoltageAvg()`

```java
// ANTES (promedio aritmético escalar):
return (thdL1 + thdL2 + thdL3) / 3.0;

// DESPUÉS (RMS cuadrático, IEC 61000-4-30):
return Math.sqrt((thdL1² + thdL2² + thdL3²) / n_fases_activas);
```

**Impacto real en detección: ~0%**
Para red MT balanceada el resultado es idéntico. Para desbalance típico
(±2 pp entre fases) la diferencia es <0.1 pp absolutos — no mueve ningún
umbral del clasificador.
**Valor:** corrección de coherencia matemática con la definición de THD.

---

### Corrección B — Infraestructura de campaña espectral
**Archivos nuevos/modificados:**
- `storage/SpectralRecorder.java` (nuevo)
- `storage/DataStorage.java` (extensión)

**Qué hace:**
- `SpectralRecorder` implementa `MeasurementPoller.MeasurementListener`
- Opera a ritmo propio (default 60 s), independiente del polling del dashboard
- Acumula cuadráticamente las lecturas de polling dentro de cada ventana →
  produce `thdRmsWindow` (RMS correcto del intervalo, ya que el ION no lo provee)
- Persiste espectro completo H1-H50 normalizado (Hn/I1) para las 3 fases
- Nueva tabla SQLite `harmonic_spectra`: ~162 columnas por fila (147 armónicos + escalares)
- Export CSV ML-ready: `exportSpectraToCsv(feederId, sessionId)`
- Resumen de sesión: `getCampaignSummary(sessionId)`

**Impacto real en detección hoy: 0%**
`thdRmsWindow` se calcula y guarda pero NO se pasa al `ElectronicLoadDetector`.
El clasificador sigue usando el valor instantáneo del último poll.

**Valor real:** habilita la campaña de caracterización ML. Sin este módulo,
el dataset para entrenar un modelo supervisado no puede construirse.

**Volumen de datos campaña estándar (1 semana):**
```
1 muestra/min × 60 × 24 × 7 = 10 080 filas/feeder
Columnas por fila: ~162  (147 armónicos + 15 escalares)
Tamaño SQLite estimado: ~25 MB/feeder/semana
```

---

## Qué NO mejoró y por qué

| Elemento | Situación |
|---|---|
| Clasificador `ElectronicLoadDetector` | Sin cambios — árbol determinístico original |
| Score logístico HADES | No implementado — requiere coeficientes calibrados |
| Flatness espectral | No implementado — pendiente (ver roadmap) |
| Segmentación P20/P80 | No implementada — pendiente |
| Confidence(t) de HADES | No implementada — pendiente |

---

## Roadmap de mejoras de alto impacto real

Ordenado de mayor a menor impacto esperado en la tasa de detección.

### [F0] Flatness espectral como discriminador VFD/cripto
**Impacto estimado:** reducción del 30–50% de falsos positivos CRYPTO sobre
feeders con VFDs industriales.
**Dificultad:** Baja
**Archivo:** `analysis/ElectronicLoadDetector.java`

```java
// Agregar como feature en classifyInternal():
double flatness = (h5h1 + h7h1) / Math.max(h11h1 + h13h1, 0.01);
// Cripto:  flatness > 3.5  (H5/H7 dominan, H11/H13 caen rápido)
// VFD:     flatness < 2.0  (H11/H13 siguen siendo significativos)
// Mixto:   2.0 ≤ flatness ≤ 3.5
```

El criterio INDUSTRIAL actual (H5>12% ∧ H7>8% ∧ H11>5% ∧ H13>4%) es
correcto pero incompleto — Flatness añade el perfil de decaimiento espectral
completo, no solo la presencia individual de cada armónico.

---

### [F1] Segmentación P20/P80 + discriminador ΔTHD
**Impacto estimado:** mejora significativa en feeders mixtos con variación
de carga a lo largo del día.
**Dificultad:** Media
**Archivo:** `analysis/ElectronicLoadDetector.java` + buffer en `MeasurementPoller`

Requiere buffer circular de 24h de (I1_avg, THDi) para calcular percentiles.
Con el `SpectralRecorder` ya existe la infraestructura de buffer; se puede
reusar.

```
Sbajo:  muestras con I1 < P20(I1, 24h)
Salt:   muestras con I1 > P80(I1, 24h)
ΔTHD = THDi(Sbajo) - THDi(Salt)
```

ΔTHD alto (cripto sube el THD en horas de baja demanda porque su fracción
de la carga total aumenta) es el discriminador temporal más potente del modelo.

---

### [F2] Score logístico (HADES Capa 4) con coeficientes iniciales
**Impacto estimado:** eliminación de la clasificación binaria "todo o nada" —
cada feeder recibe CryptoScore ∈ (0,1), más informativo para gestión.
**Dificultad:** Media
**Requiere:** features F0 y F1 implementados primero

Pesos iniciales pre-calibración (ver `resumen_analisis_adversarial.txt`):
```
Z = 0.25·THD_low + 0.20·H5_low + 0.20·Flatness + 0.10·LF
  + 0.15·ΔTHD   + 0.05·P_H5   + 0.05·IEB
CryptoScore = 1 / (1 + exp(-Z))
```

---

### [F3] Confidence(t) — umbral de alarma basado en datos acumulados
**Impacto estimado:** eliminación de alarmas falsas tempranas (primeros
minutos de conexión con datos insuficientes).
**Dificultad:** Baja (una vez que F1 existe)

```java
// n_total = muestras de 10min acumuladas
// n_low = muestras en Sbajo, n_high = muestras en Salt
double confidence = Math.min(1, n_total/144.0)
                  * Math.min(1, n_low/12.0)
                  * Math.min(1, n_high/12.0);
// No disparar DETECTION hasta confidence >= 0.50 (~12h de datos)
```

---

### [F4] Alimentar thdRmsWindow al clasificador
**Impacto estimado:** pequeño pero inmediato — usa el THD correctamente
agregado en lugar del valor instantáneo del último poll.
**Dificultad:** Trivial (1 línea de integración)
**Requiere:** que `SpectralRecorder` sea accesible desde `MeasurementPoller`
o que el valor se propague via `FeederMeasurement`.

Opción más limpia: agregar campo `thdRmsAggregated` en `FeederMeasurement`
y que `SpectralRecorder` lo llene antes de pasar la medición al clasificador.

---

### [F5] Campaña de campo + calibración ML supervisada
**Impacto estimado:** transformación completa del clasificador — de heurístico
a modelo entrenado. Es el único cambio que puede cuantificarse en puntos de
precisión/recall reales.
**Dificultad:** Estratégica (requiere datos etiquetados)

**Mínimo para calibración:**
- ≥ 3 feeders con criptominería confirmada por inspección de campo
- ≥ 3 feeders industriales con VFDs (sin cripto)
- ≥ 3 feeders residenciales/mixtos sin electrónica intensiva
- 1 semana de datos continuos por feeder (10 080 muestras × 162 features)

**Pipeline ML sugerido:**
1. PCA sobre los 147 features de armónicos → reducir a 10-15 componentes
2. Clustering K-means/DBSCAN para exploración de perfiles naturales
3. Regresión logística regularizada (L2) para calibrar coeficientes HADES
4. Validación cruzada estratificada por feeder (no por muestra)

---

## Resumen de impacto por mejora

| ID | Mejora | Impacto detección | Dificultad | Prioridad |
|----|--------|-------------------|------------|-----------|
| Corr.A | RMS trifásico (ya hecho) | ~0% | Trivial | Hecho |
| Corr.B | SpectralRecorder (ya hecho) | 0% hoy | Media | Hecho |
| F0 | Flatness VFD/cripto | Alto | Baja | **Próxima** |
| F1 | Segmentación P20/P80 + ΔTHD | Alto | Media | **Próxima** |
| F2 | Score logístico | Medio | Media | Post F0+F1 |
| F3 | Confidence(t) | Medio | Baja | Post F1 |
| F4 | Usar thdRmsWindow en clasificador | Bajo | Trivial | Cuando convenga |
| F5 | Campaña campo + ML calibrado | **Transformador** | Estratégica | Meta final |

---

## Nota sobre el disclaimer de la aplicación

La aplicación es una **herramienta exploratoria de monitoreo**, no una
herramienta normativa. Esto cubre todas las limitaciones anteriores:

- El ION 7400 no es PQM Clase A → los THD no son comparables directamente
  con mediciones de referencia IEC 61000-4-30
- El clasificador usa coeficientes no calibrados con datos reales de ANDE
- Con M=1 punto de medición, la separación de fuentes es probabilística
- Toda alarma debe verificarse por inspección de campo antes de actuar

Este disclaimer debe mantenerse visible en la GUI (pantalla About y panel
de alertas) independientemente de las mejoras que se implementen.
