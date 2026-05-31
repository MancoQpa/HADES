# Tabla de Patrones Armónicos — HADES Simulator

> Referencia para configurar perfiles de simulación y verificar la detección de `ElectronicLoadDetector`.
> Cada fila es un perfil completo con los valores exactos a usar en el JSON del simulador.
> **Validado trazando cada perfil contra el árbol de decisión de `classifyInternal()`.**

---

## 1. Tabla maestra de perfiles

| Perfil (`--profile`) | `LoadType` esperado | Nodo árbol | I (A) | FP | THD\_I (%) | THD\_V (%) | K-Factor |
|---|---|---|---|---|---|---|---|
| `normal_load` | `LINEAR` | [9]→default | 10 | 0.98 | 5.0 | 2.5 | 1.20 |
| `linear_load` | `LINEAR` | [3] | 90 | 0.85 | 4.0 | 2.0 | 1.10 |
| `lighting` | `LIGHTING` | [4] | 60 | 0.85 | 12.0 | 2.5 | 1.50 |
| `electronic_light` | `ELECTRONIC_LIGHT` | [8] | 80 | 0.88 | 10.0 | 2.8 | 1.50 |
| `industrial` | `INDUSTRIAL` | [5] (6P) | 130 | 0.93 | 26.0 | 4.2 | 4.80 |
| `data_center` | `DATA_CENTER` | [6] | 150 | 0.88 | 20.0 | 3.8 | 4.60 |
| `mixed_electronic` | `MIXED_ELECTRONIC` | [9] | 100 | 0.91 | 7.0 | 2.2 | 1.30 |
| `crypto_mining` | `CRYPTO_MINING` | [6] | 170 | 0.985 | 42.0 | 4.5 | 6.80 |
| **`crypto_mining_pfc`** | **`CRYPTO_MINING_PFC`** | **[2]** | **47** | **1.0000** | **3.0** | **1.9** | **1.05** |
| `upstream_distortion` | `UPSTREAM_DISTORTION` | [1] | 80 | 0.95 | 3.5 | 6.5 | 1.10 |

---

## 2. Espectro armónico — ratios Hₙ/H₁ (corriente, normalizado a H1 = 1.000)

Los valores corresponden al array `harA/harB/harC` en el JSON. Índice = orden − 1.

| Perfil | H1 | H2 | H3 | H4 | H5 | H6 | H7 | H8 | H9 | H10 | H11 | H12 | H13 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `normal_load` | 1.000 | 0.005 | 0.030 | 0.004 | 0.040 | 0.003 | 0.020 | 0.002 | 0.010 | 0.002 | 0.008 | 0.002 | 0.006 |
| `linear_load` | 1.000 | 0.000 | 0.010 | 0.000 | 0.030 | 0.000 | 0.020 | 0.000 | 0.005 | 0.000 | 0.010 | 0.000 | 0.005 |
| `lighting` | 1.000 | 0.000 | **0.400** | 0.000 | 0.060 | 0.000 | 0.030 | 0.000 | 0.020 | 0.000 | 0.015 | 0.000 | 0.010 |
| `electronic_light` | 1.000 | 0.000 | 0.150 | 0.000 | 0.100 | 0.000 | 0.040 | 0.000 | 0.020 | 0.000 | 0.010 | 0.000 | 0.005 |
| `industrial` | 1.000 | 0.000 | 0.020 | 0.000 | **0.250** | 0.000 | **0.110** | 0.000 | 0.010 | 0.000 | **0.090** | 0.000 | **0.080** |
| `data_center` | 1.000 | 0.000 | 0.050 | 0.000 | **0.280** | 0.000 | **0.180** | 0.000 | 0.060 | 0.000 | 0.040 | 0.000 | 0.020 |
| `mixed_electronic` | 1.000 | 0.000 | 0.120 | 0.000 | 0.060 | 0.000 | 0.040 | 0.000 | 0.015 | 0.000 | 0.010 | 0.000 | 0.005 |
| `crypto_mining` | 1.000 | 0.000 | 0.030 | 0.000 | **0.350** | 0.000 | **0.220** | 0.000 | 0.080 | 0.000 | 0.070 | 0.000 | 0.050 |
| **`crypto_mining_pfc`** | **1.000** | 0.001 | 0.002 | 0.001 | **0.039** | 0.000 | **0.002** | 0.000 | 0.000 | 0.000 | 0.009 | 0.000 | 0.000 |
| `upstream_distortion` | 1.000 | 0.001 | 0.010 | 0.001 | 0.025 | 0.001 | 0.015 | 0.000 | 0.005 | 0.000 | 0.005 | 0.000 | 0.003 |

---

## 3. Indicadores derivados (calculados por HADES en `classify()`)

Estos valores son los que realmente usa el árbol de decisión. Verificar que coincidan con los umbrales.

| Perfil | H5/H7 | Q/S | Flatness¹ | THD\_odd (%) | THD\_even (%) | PFC Score² |
|---|---|---|---|---|---|---|
| `normal_load` | 2.0 | 0.199 | 4.3 | 4.8 | 0.8 | ~10 |
| `linear_load` | 1.5 | 0.527 | 3.3 | 3.8 | 0.5 | ~5 |
| `lighting` | 2.0 | 0.527 | 3.6 | 11.8 | 0.8 | ~10 |
| `electronic_light` | 2.5 | 0.475 | 9.3 | 9.8 | 0.9 | ~15 |
| `industrial` | 2.3 | 0.374 | 2.1 | 25.5 | 1.8 | ~5 |
| `data_center` | 1.6 | 0.475 | 7.7 | 19.5 | 1.5 | ~5 |
| `mixed_electronic` | 1.5 | 0.416 | 6.7 | 6.8 | 0.7 | ~8 |
| `crypto_mining` | 1.6 | 0.172 | 4.8 | 41.5 | 1.2 | ~25 |
| **`crypto_mining_pfc`** | **19.5** | **0.0032** | **4.6** | **3.0** | **0.1** | **~92** |
| `upstream_distortion` | 1.7 | 0.312 | 4.3 | 3.4 | 0.4 | ~10 |

> ¹ Flatness = (H5+H7)/(H11+H13). Si H11+H13 < 0.5%: devuelve 10.0 (espectro frontal) o 1.0.
> ² PFC Score estimado según `calculatePfcScore()`. Un score ≥ 60 indica firma PFC relevante.

---

## 4. Traza del árbol de decisión por perfil

```
CONDICIONES del árbol (ver ElectronicLoadDetector.classifyInternal):
  [1] UPSTREAM:       thdV > 5.0 AND thdI < 8.0 AND h5h1 < 0.08
  [2] CRYPTO_PFC:     thdI ∈ [1.5,6.5] AND pf ≥ 0.998 AND qsR ≤ 0.012
                      AND kAvg ∈ [1.0,1.12] AND h5h7 ≥ 8.0 AND cv < 0.05
  [3] LINEAR:         thdI < 5.0 AND h5h1 < 0.05
  [4] LIGHTING:       thdI > 10.0 AND h5h1 < 0.08 AND pf ∈ (0.75,0.95)
  [5] INDUSTRIAL 6P:  thdI > 8.0 AND h5>12% AND h7>8% AND h11>5% AND h13>4% AND flatness∈[1.3,3.5)
  [6] CRYPTO/DC:      cv<5% AND thdI>15% AND h5>15% AND h7>10%  → CRYPTO si pf>0.92 else DC
  [7] INDUSTRIAL 12P: thdI > 8.0 AND h11>7% AND h13>6% AND flatness<1.2
  [8] ELECTRONIC_LT:  thdI > 8.0 AND (h5>8% OR h7>5%)
  [9] MIXED:          thdI > 5.0
  [?] default:        LINEAR
```

| Perfil | [1] | [2] | [3] | [4] | [5] | [6] | [7] | [8] | [9] | Resultado |
|---|---|---|---|---|---|---|---|---|---|---|
| `normal_load` | ✗ thdV=2.5 | ✗ pf=0.98 | ✗ thdI=5.0 | ✗ thdI<10 | ✗ h5<12% | ✗ thdI<15 | ✗ | ✗ | ✗ | **default→LINEAR** |
| `linear_load` | ✗ | ✗ | ✓ thdI=4<5 h5=3%<5% | — | — | — | — | — | — | **LINEAR** |
| `lighting` | ✗ | ✗ thdI>6.5 | ✗ | ✓ thdI=12>10 h5<8% pf=0.85 | — | — | — | — | — | **LIGHTING** |
| `electronic_light` | ✗ | ✗ thdI>6.5 | ✗ | ✗ thdI=10 no>10 | ✗ h5=10%<12% | ✗ | ✗ | ✓ thdI=10>8 h5=10%>8% | — | **ELECTRONIC\_LIGHT** |
| `industrial` | ✗ | ✗ thdI>6.5 | ✗ | ✗ | ✓ 6P completo | — | — | — | — | **INDUSTRIAL** |
| `data_center` | ✗ | ✗ thdI>6.5 | ✗ | ✗ | ✗ flatness=7.7 | ✓ pf=0.88<0.92 | — | — | — | **DATA\_CENTER** |
| `mixed_electronic` | ✗ | ✗ thdI>6.5 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ thdI=7>5 | **MIXED\_ELECTRONIC** |
| `crypto_mining` | ✗ | ✗ thdI=42>6.5 | ✗ | ✗ | ✗ flatness=4.8 | ✓ pf=0.985>0.92 | — | — | — | **CRYPTO\_MINING** |
| **`crypto_mining_pfc`** | ✗ thdV=1.9 | **✓ 6/6 cond** | — | — | — | — | — | — | — | **CRYPTO\_MINING\_PFC** |
| `upstream_distortion` | **✓** thdV=6.5>5 thdI=3.5<8 h5=2.5%<8% | — | — | — | — | — | — | — | — | **UPSTREAM\_DISTORTION** |

---

## 5. Campos del JSON del simulador — correspondencia completa

```json
{
  "phVL1": 13280.0,      // V fase-neutro (23kV/√3)
  "phVL2": 13280.0,
  "phVL3": 13280.0,
  "aL1":   <A>,          // corriente RMS fundamental (A)
  "aL2":   <A>,
  "aL3":   <A>,
  "totW":  <W>,          // potencia activa total 3φ (vatios)
  "totVAr":<VAr>,        // potencia reactiva total 3φ (VAr)
  "totVA": <VA>,         // potencia aparente 3φ (VA)
  "totPF": <FP>,         // factor de potencia (per-unit, 0.0–1.0)
  "hz":    50.0,
  "thdAL1":<THD%>,       // THD corriente L1 (%)
  "thdAL2":<THD%>,
  "thdAL3":<THD%>,
  "thdPpvL12":<THD_V%>,  // THD tensión L12 (%) → usado como thdVoltageAvg
  "thdPpvL23":<THD_V%>,
  "thdPpvL31":<THD_V%>,
  "hKfL1": <K>,          // K-Factor L1 (IEEE C57.110-2018)
  "hKfL2": <K>,
  "hKfL3": <K>,
  "thdOddA":<Todd%>,     // THD armónicos impares (%)
  "thdEvnA":<Tevn%>,     // THD armónicos pares (%)
  "harA": [H1,H2,...H50], // espectro normalizado L1 (H1=1.0, resto=Hn/H1)
  "harB": [...],          // ídem L2
  "harC": [...],          // ídem L3
  "seqAPos":<A>,          // secuencia positiva corriente (A) ≈ aL1
  "seqANeg":<A>,          // secuencia negativa ≈ aL1 × desequilibrio
  "seqAZero":<A>,
  "seqVPos": 13280.0,
  "seqVNeg": <V>
}
```

### Relaciones clave para no equivocar los valores:

| Lo que HADES lee | De dónde viene en Profile | Cómo calcularlo |
|---|---|---|
| `thdCurrentAvg` | promedio de `thdAL1/L2/L3` | Ingresar el THD\_I medido en % |
| `thdVoltageAvg` | promedio de `thdPpvL12/L23/L31` | Ingresar el THD\_V medido en % |
| `powerFactor` | `totPF` directo | Valor per-unit 0.0–1.0 |
| `reactivePower` (kVAR) | `totVAr` × `powerScaleFactor` (0.001) | totVAr = Q\_kVAR × 1000 |
| `apparentPower` (kVA) | `totVA` × `powerScaleFactor` (0.001) | totVA = S\_kVA × 1000 |
| `qsRatio` = \|Q\|/S | calculado por classifier | totVAr/totVA directamente |
| `h5h1Ratio` | `harA[4]` | H5/H1 normalizado |
| `h7h1Ratio` | `harA[6]` | H7/H1 normalizado |
| `h11h1Ratio` | `harA[10]` | H11/H1 normalizado |
| `h13h1Ratio` | `harA[12]` | H13/H1 normalizado |
| `kFactorL1` | `hKfL1` directo | K = 1+Σ(n²·In²)/I₁² |
| `h5h7Ratio` | calculado: harA[4]/harA[6] | verificar > 8 para PFC |

---

## 6. Verificación manual rápida — checklist por LoadType

### CRYPTO\_MINING\_PFC (`crypto_mining_pfc`)
```
☑ totPF = 1.0000            (debe ser ≥ 0.998)
☑ totVAr / totVA = 0.0032   (debe ser ≤ 0.012)
☑ hKfL1 = 1.05              (debe estar en [1.0, 1.12])
☑ harA[4] / harA[6] = 19.5  (debe ser ≥ 8.0)
☑ thdAL1 = 3.0              (debe estar en [1.5%, 6.5%])
☑ CV ~ 0 (simulador 24/7)   (debe ser < 5%)
→ Nodo [2]: 6/6 condiciones → CRYPTO_MINING_PFC ✓
→ PFC Score esperado: ~92/100
```

### UPSTREAM\_DISTORTION (`upstream_distortion`)
```
☑ thdPpvL12 = 6.5           (debe ser > 5.0%)
☑ thdAL1 = 3.5              (debe ser < 8.0%)
☑ harA[4] = 0.025           (debe ser < 0.08 = 8%)
→ Nodo [1]: 3/3 condiciones → UPSTREAM_DISTORTION ✓
```

### INDUSTRIAL 6-pulsos (`industrial`)
```
☑ harA[4] = 0.250  (H5 > 12%)
☑ harA[6] = 0.110  (H7 > 8%)
☑ harA[10] = 0.090 (H11 > 5%)
☑ harA[12] = 0.080 (H13 > 4%)
☑ flatness = 2.12  (∈ [1.3, 3.5))
☑ thdAL1 = 26.0   (> 8%)
→ Nodo [5]: 6-pulsos → INDUSTRIAL ✓
```

---

## 7. Perfiles de potencia completos (valores para el JSON)

| Perfil | P (MW) | Q (MVAR) | S (MVA) | FP | aL1 (A) |
|---|---|---|---|---|---|
| `normal_load` | 0.390 | 0.079 | 0.398 | 0.98 | 10 |
| `linear_load` | 3.048 | 1.889 | 3.586 | 0.85 | 90 |
| `lighting` | 2.032 | 1.259 | 2.390 | 0.85 | 60 |
| `electronic_light` | 2.805 | 1.514 | 3.187 | 0.88 | 80 |
| `industrial` | 4.817 | 1.903 | 5.179 | 0.93 | 130 |
| `data_center` | 5.259 | 2.839 | 5.976 | 0.88 | 150 |
| `mixed_electronic` | 3.625 | 1.652 | 3.984 | 0.91 | 100 |
| `crypto_mining` | 6.671 | 1.167 | 6.773 | 0.985 | 170 |
| **`crypto_mining_pfc`** | **1.875** | **0.006** | **1.875** | **1.0000** | **47** |
| `upstream_distortion` | 3.336 | 1.749 | 3.790 | 0.880 | 80 |

> Fórmulas: S = 3 × V\_fase × I = 3 × 13280 × I\_A / 1000 kVA; P = S × FP; Q = S × sin(arccos(FP))

---

## 8. Cómo configurar el simulador paso a paso

```bash
# Opción A: usar perfil embebido (disponible tras el commit)
java -cp "classes;lib/*" com.harmonicmonitor.simulator.SimulatorMain \
  --ied SIM_PFC --port 10103 --profile crypto_mining_pfc \
  --noise 0.02 --interval 5000

# Opción B: usar archivo JSON externo
# Copiar crypto_mining_pfc.json en simulator/templates/
# y lanzar con el mismo comando — SimProfileLoader lo encontrará primero

# Opción C: cambiar perfil en caliente (simulador corriendo)
# Via dashboard en http://localhost:8765 → botón "Cambiar perfil"
# O via stdin: echo "SET_PROFILE:crypto_mining_pfc" | nc localhost <stdin_port>
```

### En HADES (cliente):
1. Crear feeder con `iedHost=127.0.0.1`, `iedPort=10103`
2. Verificar que el panel de dashboard muestra:
   - `Tipo de carga: Minería Cripto (PFC Activo)` (color `#FF6D00`)
   - `PFC Score ≈ 92/100`
   - `H5/H7 ≈ 19.5`
   - `Q/S ≈ 0.003`
