# Bibliografía de HADES — fundamentos y trazabilidad de cada afirmación

> **Propósito**: hacer técnicamente defendible cada valor tabulado, umbral y regla del clasificador,
> declarando **qué sostiene cada referencia, por qué es válida como respaldo, y qué NO sostiene**.
> Reemplaza a la lista anterior de enlaces (`papers hades.txt`), cuya colección — verificada DOI por
> DOI — trataba exclusivamente de *metodología de medición* y no respaldaba las firmas armónicas.
>
> **Convención**: cada entrada declara `Sostiene en HADES` (con ubicación en código/docs),
> `Fundamento` (el argumento técnico que la hace citable) y `Límites` (lo que no debe atribuírsele).

---

## 1. Normas — respaldo `[NORMATIVO]`

### IEEE Std 519-2022 — *Recommended Practice for Harmonic Control in Electric Power Systems*
- **Sostiene en HADES**: umbral THD_I ≈ 5% como frontera de carga lineal (nodo [3] de
  `ElectronicLoadDetector`); límites de la pantalla Normas (`CompliancePanel`).
- **Fundamento**: práctica recomendada IEEE vigente, elaborada por consenso de comité; sus tablas de
  límites de distorsión en el PCC son la referencia de facto en América para evaluación de armónicos.
- **Límites**: IEEE 519 limita **TDD** (referido a la corriente de máxima demanda I_L), no THD_I.
  La comparación de HADES es orientativa y así está declarada (`help_08`, Disclaimer §3). IEEE 519
  **no define firmas de tipos de carga**; sus anexos informativos traen espectros *típicos* de
  convertidores que son ilustrativos, no normativos.

### IEC 61000-3-6:2008 — *Assessment of emission limits for distorting loads in MV and HV power systems*
- **Sostiene en HADES**: umbral THD_V > 5% del nodo [1] `UPSTREAM_DISTORTION` (nivel de
  planificación MT); límites por armónico individual de la pantalla Normas (H5 ≤ 4%, etc.);
  **ley de suma exponencial** (α=1 para n<5; 1.4 para 5≤n≤10; 2 para n>10) usada por el estudio
  de dominancia (`docs/estudio_dominancia.md`, `DominanceStudy.java`).
- **Fundamento**: informe técnico IEC de la serie EMC; los niveles de planificación MT/AT y la ley
  de suma por diversidad de fase son el método estándar de las distribuidoras para coordinar emisión.
- **Límites**: son niveles de *planificación* (internos de la empresa), no límites de inmunidad ni
  de contrato. La ley α modela diversidad típica, no el peor caso coherente.

### IEC 61000-3-12:2011 — *Limits for harmonic currents produced by equipment >16 A and ≤75 A per phase*
- **Sostiene en HADES**: contexto normativo de emisión para equipos trifásicos de la potencia de
  drivers/cargadores; citada en README y docs como marco de límites de corriente.
- **Fundamento**: norma de producto IEC aplicable al rango de corriente de los equipos que HADES
  pretende caracterizar.
- **Límites**: aplica al *equipo individual* en BT en el punto de conexión, no al agregado del feeder
  MT que HADES mide.

### IEC 61000-3-2 (ed. vigente) — *Limits for harmonic current emissions (equipment ≤16 A per phase)*, Clase C
- **Sostiene en HADES**: la premisa de que **H3 es el armónico característico de la iluminación**
  (regla LIGHTING, nodo [4]). La Clase C limita explícitamente el H3 de equipos de iluminación
  (30·λ % del fundamental, con λ = factor de potencia de circuito) precisamente porque es su emisión
  dominante.
- **Fundamento**: la estructura misma de la norma es evidencia: el comité dedicó una clase entera a
  la iluminación con límite específico para H3.
- **Límites**: no dice nada sobre la **propagación** del H3 a MT (ver §6, transformadores Dyn); el
  perfil `lighting` del simulador (H3=40%) representa lámparas no conformes o <25 W medidas aguas
  abajo del delta.

### EN 50160:2010 — *Voltage characteristics of electricity supplied by public distribution networks*
- **Sostiene en HADES**: umbral THD_V 8% (percentil 95 semanal, MT) usado en la normalización del
  índice de electrónica (`calculateElectronicIndex`, componente THD_V) y en Normas; límite de
  desbalance de tensión 2%.
- **Fundamento**: norma europea de características de tensión en redes públicas; define lo que el
  usuario puede esperar de la red, con base estadística de 10 minutos.
- **Límites**: caracteriza la *tensión de suministro*, no la emisión de una carga; sus percentiles
  requieren agregación semanal que HADES no implementa formalmente.

### IEEE Std 1459-2010 — *Definitions for the Measurement of Electric Power Quantities under Sinusoidal, Nonsinusoidal, Balanced, or Unbalanced Conditions*
- **Sostiene en HADES**: la validez del indicador **Q/S** bajo distorsión (condición C del vector
  PFC, `computeQsRatio`); la separación conceptual P/Q/D (potencia de distorsión).
- **Fundamento**: el estándar (línea de trabajo de A. E. Emanuel) es la referencia moderna para
  definiciones de potencia no sinusoidal; sin él, "Q" es ambigua bajo distorsión.
- **Límites**: el estándar define las magnitudes; **el umbral Q/S ≤ 0.012 es empírico de HADES**
  (una campaña de campo). Además, la "Q" que reporta cada IED puede seguir una definición distinta
  (Budeanu, fundamental, vectorial) — el umbral puede no ser portable entre marcas de medidor.

### IEEE Std C57.110-2018 — *Recommended Practice for Establishing Liquid-Filled and Dry-Type Power and Distribution Transformer Capability when Supplying Nonsinusoidal Load Currents*
- **Sostiene en HADES**: la definición y el cálculo del **K-Factor** (condición D del vector PFC;
  `hKfL1/2/3` del simulador; K recalculado en `DominanceStudy`): K = Σ(h²·I_h²)/Σ(I_h²).
- **Fundamento**: práctica recomendada IEEE para derrateo de transformadores con carga no senoidal;
  la fórmula es exacta dada la medición del espectro.
- **Límites**: el rango K ∈ [1.0, 1.12] como firma PFC es **empírico de HADES** (derivado de
  THD≈4%, coherente con la fórmula, pero validado con N=1).

### IEC 61000-4-7 (ed. vigente) — *General guide on harmonics and interharmonics measurements*
### IEC 61000-4-30 (ed. vigente) — *Power quality measurement methods* (Clases A/S/B)
- **Sostienen en HADES**: la **cadena de medición**: cómo se calculan THD y armónicos individuales
  (ventanas DFT de 10/12 ciclos, agrupación de bins), y por qué HADES declara que sus resultados
  "no sustituyen medición IEC 61000-4-30 Clase A" (disclaimer global).
- **Fundamento**: son las normas que definen el instrumento de referencia; citar la clase del
  instrumento es lo que hace comparable (o no) una medición.
- **Límites**: el ION 7400 es un medidor clase 0.2S de energía, no un analizador Clase A
  certificado; los 9 papers de la sección §4 documentan las diferencias que esto introduce.

---

## 2. Libros de referencia — respaldo `[TEÓRICO]`

### Mohan, Undeland, Robbins — *Power Electronics: Converters, Applications, and Design* (3ª ed., Wiley, 2003)
- **Sostiene en HADES**: teoría del rectificador de 6 pulsos (armónicos característicos h=6k±1 con
  amplitud ideal I_h = I₁/h → H5=20%, H7=14.3%, H11=9.1%, H13=7.7%, base de los nodos [5][6][7]);
  teoría del **PFC boost activo** (FP→1, supresión de armónicos, condición B del vector PFC).
- **Fundamento**: libro de texto canónico de electrónica de potencia; las derivaciones son análisis
  de Fourier exacto bajo hipótesis ideales (conducción rectangular 120°, L_dc→∞, conmutación
  instantánea) — matemática verificable, no opinión.
- **Límites**: los valores 1/h son ideales; en campo son 15–30% menores (solapamiento de conmutación,
  rizado DC, impedancia de fuente) — razón de los umbrales conservadores de HADES (15%/10% en lugar
  de 20%/14.3%). El libro **no** trata el ratio H5/H7 de un PFC como discriminador (ver §5).

### Erickson & Maksimović — *Fundamentals of Power Electronics* (3ª ed., Springer, 2020)
- **Sostiene en HADES**: profundiza el fundamento del lazo de control del PFC boost (compensador de
  corriente con ganancia decreciente en frecuencia) — el mecanismo físico detrás de la *hipótesis*
  H5/H7 de HADES.
- **Fundamento**: referencia académica estándar de convertidores conmutados, con el tratamiento de
  control más riguroso disponible en libro de texto.
- **Límites**: explica el mecanismo; no publica el ratio H5/H7 ni umbrales de detección.

### Arrillaga & Watson — *Power System Harmonics* (2ª ed., Wiley, 2003); Arrillaga, Bradley, Bodger (1ª ed., 1985)
- **Sostiene en HADES**: la sistematización de los **armónicos característicos** de convertidores
  (regla kp±1 para p pulsos; cancelación de H5/H7 en 12 pulsos → base del indicador Flatness y del
  nodo [7]); el comportamiento de los **triplens en devanados delta** (secuencia cero atrapada —
  base de la limitación LIGHTING en 23 kV, ver §6).
- **Fundamento**: primer libro íntegramente dedicado a armónicos en sistemas de potencia (1985) y su
  edición moderna; es la referencia citada por la propia literatura de normas.
- **Límites**: teoría de convertidores y propagación; no trata cargas TI modernas (SMPS con PFC,
  ASIC miners) que son posteriores.

### Kimbark — *Direct Current Transmission, Vol. I* (Wiley-Interscience, 1971)
- **Sostiene en HADES**: el tratamiento canónico original de los armónicos de convertidores
  polifásicos (la regla 6k±1 y el efecto del ángulo de conmutación μ sobre las amplitudes).
- **Fundamento**: obra clásica de HVDC donde la teoría de armónicos de rectificadores quedó
  formalizada para ingeniería; útil para mostrar que la regla no es reciente ni discutida.
- **Límites**: contexto HVDC con convertidores de gran potencia; los valores cuantitativos deben
  tomarse de las hipótesis de cada caso.

### Dugan, McGranaghan, Santoso, Beaty — *Electrical Power Systems Quality* (3ª ed., McGraw-Hill, 2012)
- **Sostiene en HADES**: los **espectros "típicos" medidos** (no ideales) de convertidores — p. ej.
  VFD 6 pulsos con H5≈17–20%, H7≈9–11% — que justifican que los perfiles del simulador
  (`industrial`: H5=25%, H7=11%) estén entre el ideal de Fourier y el típico de campo; el marco
  general de calidad de energía (resonancia paralela, triplens, K-factor aplicado).
- **Fundamento**: el manual de referencia de la industria de distribución; sus tablas provienen de
  campañas de medición reales.
- **Límites**: valores "típicos" con dispersión amplia; no sustituyen la medición del feeder propio.

### Chapman — *Electric Machinery Fundamentals* (McGraw-Hill)
- **Sostiene en HADES**: apoyo secundario para rectificadores de 12 pulsos (cancelación H5/H7 por
  desfase de 30°), citado en el nodo [7].
- **Fundamento**: texto estándar de máquinas eléctricas.
- **Límites**: tratamiento introductorio; para 12 pulsos la referencia fuerte es Arrillaga/Kimbark.

---

## 3. Papers de aplicación directa

### G. W. Hart — "Nonintrusive appliance load monitoring", *Proceedings of the IEEE*, vol. 80, n.º 12, pp. 1870–1891, 1992
- **Sostiene en HADES**: el fundamento del enfoque de **acumulación temporal** (separar la
  componente constante del espectro — carga persistente — de la variable — cargas fluctuantes),
  descrito en `hades_referencia_tecnica.md` §2 y §8. Es el paper fundacional de NILM.
- **Fundamento**: artículo seminal (decenas de miles de citas); define el problema de desagregación
  con un solo punto de medición y demuestra que la dimensión temporal lo hace tratable.
- **Límites**: NILM clásico opera en BT con eventos de conmutación de electrodomésticos; la
  extrapolación a cabecera de feeder MT es la *hipótesis de investigación* de HADES, no un resultado
  de Hart.

### "A Power Quality and Load Analysis of a Cryptocurrency Mine" — IEEE Rural Electric Power Conference (REPC), 2019. IEEE Xplore: <https://ieeexplore.ieee.org/document/8598358>
- **Sostiene en HADES**: la caracterización de campo de una mina real: **FP estacionario
  0.994–0.995**, carga plana 24/7, comportamiento no lineal solo en transitorios. Respaldo directo
  de las condiciones B (FP alto) y F (CV bajo) del vector `CRYPTO_MINING_PFC` y del perfil
  `crypto_mining_pfc` del simulador.
- **Fundamento**: mediciones reales de 3 días (datos de 15 minutos + formas de onda) en una
  instalación de minería conectada a distribución — el escenario exacto de HADES.
- **Límites**: una instalación, un mix de hardware de su fecha; el FP medido (0.994–0.995) es
  ligeramente inferior al umbral de HADES (0.998, medido en su propia campaña) — la diferencia entre
  generaciones de fuentes justifica hacer configurable el umbral.

### "A Comparative Power Quality Analysis of Cryptocurrency Mining Loads" — IEEE, 2020. IEEE Xplore: <https://ieeexplore.ieee.org/document/9052404>
- **Sostiene en HADES**: el modelado de las fuentes de miners como **convertidores PFC-boost
  activos**; armónicos de hasta ~9% en Antminer S9 sin devanado delta en el transformador de
  subestación — evidencia de que la topología del transformador condiciona lo que se mide (coherente
  con la limitación Dyn de HADES).
- **Fundamento**: análisis comparativo publicado por IEEE de distintas cargas de minería.
- **Límites**: hardware específico (S9, generación sin PFC completo); los porcentajes dependen de la
  configuración del transformador.

### O. García, J. A. Cobos, R. Prieto, P. Alou, J. Uceda — "Single phase power factor correction: a survey", *IEEE Transactions on Power Electronics*, vol. 18, n.º 3, pp. 749–755, 2003
- **Sostiene en HADES**: el panorama de topologías PFC monofásicas y su desempeño alcanzable
  (FP > 0.99, THD de corriente de pocos %) — contexto de las condiciones A y B del vector PFC.
- **Fundamento**: survey en la revista de referencia del área.
- **Límites**: anterior a los ASIC miners; describe capacidades de las topologías, no firmas de
  detección.

---

## 4. Metodología de medición — los 9 DOI heredados de `papers hades.txt`

**Hallazgo de la auditoría bibliográfica (jul-2026)**: estos 9 papers tratan de *cómo medir*
(ventanas DFT, clases de instrumento, errores de procedimiento). **Ninguno estudia firmas armónicas
de tipos de carga** — no citan ni respaldan la tabla de patrones. Se conservan porque sí sostienen
la cadena de medición de HADES (THD/armónicos según IEC 61000-4-7 leídos de un IED que no es
Clase A). Uno (Kunkel & Klezar) ni siquiera trata de armónicos y debería retirarse.

| DOI | Referencia (primer autor, año, venue) | Qué sostiene en HADES |
|---|---|---|
| [10.1049/iet-gtd.2015.0366](https://doi.org/10.1049/iet-gtd.2015.0366) | Dalali, 2015, *IET Gener. Transm. Distrib.* — índices de distorsión según IEC 61000-4-7 | Elección de índices THD/THDG y su sensibilidad |
| [10.1109/TIM.2009.2019308](https://doi.org/10.1109/TIM.2009.2019308) | Tarasiuk, 2009, *IEEE Trans. Instrum. Meas.* — métodos DFT bajo IEC 61000-4-7 | Diferencias entre métodos de cálculo DFT de analizadores comerciales |
| [10.1109/IECON.2004.1431792](https://doi.org/10.1109/IECON.2004.1431792) | Xie, 2004, IECON — medición de armónicos de carga de tracción | Problemas de medición con cargas fluctuantes |
| [10.1109/TDC.2006.1668490](https://doi.org/10.1109/TDC.2006.1668490) | Gunther, 2006, IEEE PES T&D — medición según IEEE 519 e IEC 61000-4-7 | Reconciliación de las dos metodologías que HADES referencia |
| [10.1109/AUPEC.2007.4548082](https://doi.org/10.1109/AUPEC.2007.4548082) | Prieto, 2007, AUPEC — errores del procedimiento IEC 61000-4-7 en hornos de arco | Límites del procedimiento con cargas no estacionarias |
| [10.1109/ICEMIC.2003.238091](https://doi.org/10.1109/ICEMIC.2003.238091) | Kunkel, 2003, INCEMIC — transitorios rápidos, IEC 61000-4-4 | **Ninguna relación con armónicos — retirar** |
| [10.1109/IMTC.2004.1351420](https://doi.org/10.1109/IMTC.2004.1351420) | Aiello, 2004, IMTC — instrumento reconfigurable de bajo costo para PQ | Precedente de instrumentación PQ no certificada |
| [10.1109/EPQU.2007.4424226](https://doi.org/10.1109/EPQU.2007.4424226) | Neumann, 2007, EPQU — relevancia de IEC 61000-4-30 Clase A | Por qué HADES declara no sustituir Clase A |
| [10.1109/IMCCC.2011.259](https://doi.org/10.1109/IMCCC.2011.259) | Bellan, 2011, IMCCC — estadística de factores de distorsión IEC 61000-4-7 | Comportamiento estadístico de los índices que HADES promedia |

---

## 5. Afirmaciones originales de HADES — sin respaldo bibliográfico externo

Declaradas explícitamente para que ningún lector les atribuya autoridad normativa. Son el material
de investigación propio del proyecto (candidatas a publicación si se validan con N>1).

| Afirmación | Estado de validación | Dónde vive |
|---|---|---|
| **H5/H7 > 8 discrimina PFC activo** (el lazo del boost suprime H7 más que H5) | Hipótesis original. Mecanismo plausible (Erickson & Maksimović, ganancia del compensador decreciente en f) pero **ninguna publicación tabula este ratio**. Medido en campo una vez (19.5). **N=1** | Condición E del nodo [2]; `computeH5h7Ratio` |
| FP ≥ 0.998 sostenido como firma PFC | Coherente con REPC 2019 (0.994–0.995) pero umbral ajustado a una única campaña propia | Condición B del nodo [2] |
| Q/S ≤ 0.012 | Definiciones de IEEE 1459; umbral empírico N=1; sensible a la definición de Q del IED | Condición C del nodo [2] |
| K ∈ [1.0, 1.12] como firma PFC | Fórmula C57.110 exacta; rango empírico N=1 | Condición D del nodo [2] |
| CV < 5% para SMPS regulado | Estimación de ingeniería; sin estándar | `maxCvElectronicThreshold` |
| FP = 0.92 para separar CRYPTO de DATA_CENTER | Criterio empírico admitido; dudoso con PSU modernas (80Plus obliga PFC) | Nodo [6] |
| Dominancia mínima por clase | **Medida en mezcla sintética** (ley de suma IEC 61000-3-6): PFC ~97–98%, espectrales ~52–71%, ver `docs/estudio_dominancia.md`. Pendiente de contraste con campo | Estudio de dominancia |
| Campaña ION7400-0d5885 (26/05/2026, feeder cripto exclusivo, 23 kV/50 Hz) | Única medición de campo del proyecto; origen de los umbrales PFC | `hades_caracterizacion_probabilistica.md` |

---

## 6. Nota técnica: H3 y transformadores Dyn en 23 kV

- **Afirmación**: en feeders de 23 kV con transformadores de distribución 23000/380 V tipo **Dyn**,
  el H3 balanceado de las cargas BT no llega al medidor de cabecera.
- **Fundamento**: los triplens balanceados (H3, H9, H15…) son de **secuencia cero**; en un devanado
  delta la corriente de secuencia cero circula internamente y no tiene camino hacia la línea MT.
  Tratamiento estándar en Arrillaga & Watson (cap. de propagación/transformadores) y Dugan et al.
  El residuo medible en MT proviene del desequilibrio de carga (la parte no-secuencia-cero del H3)
  y de la corriente de magnetización del transformador.
- **Estado de validación en el proyecto**: **solo teórico, y así permanecerá**: validarlo requeriría
  medición simultánea en BT, y (a) el proyecto no dispone de puntos de medición en BT, (b) aunque
  existieran, corresponderían a otro nodo eléctrico distinto del que HADES monitorea — la
  comparación quedaría fuera del alcance del sistema. La consecuencia de diseño (la clase LIGHTING
  exige H3 medido > 15% y rara vez es alcanzable desde MT) es conservadora: ante la duda, el
  clasificador **no** reclama iluminación.

---

*Documento generado como parte de la auditoría bibliográfica de HADES (julio 2026).*
*Mantenimiento: al añadir un umbral o regla, registrar aquí su respaldo — o su ausencia.*
