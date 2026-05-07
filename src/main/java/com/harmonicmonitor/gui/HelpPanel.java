package com.harmonicmonitor.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;

/**
 * HelpPanel — Documentation and usage help organized by topics.
 * Includes rigorous, peer-reviewed theoretical foundations for each load model.
 *
 * Theoretical content has been reviewed against:
 *   IEEE 519-2022, IEC 61000 series, IEEE 1459-2010, Arrillaga & Watson (2003),
 *   Mohan et al. (2002), Baggini (2008), and CIGRE WG C4.109 (2014).
 */
public class HelpPanel {

    private final BorderPane root;

    private static final String[][] TOPICS = {

        // ── Disclaimer normativo (PRIMER ítem — siempre visible) ─────────────

        {"⚖ Disclaimer Normativo",
            "DISCLAIMER NORMATIVO — ALCANCE Y LIMITACIONES\n" +
            "HADES v1.0  |  Harmonic Analysis for Detection of Electronic Signatures\n" +
            "Revisión: Marzo 2026  |  Equipo: Medina / Rojas / Paiva / Domínguez\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. NATURALEZA DEL INSTRUMENTO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "HADES es un sistema de MONITOREO E INVESTIGACIÓN.\n\n" +
            "NO es:\n" +
            "  • Instrumento de medición certificado (IEC 61557 / IEC 61010)\n" +
            "  • Analizador de referencia Clase A (IEC 61000-4-30)\n" +
            "  • Sustituto de un estudio normativo formal (IEC 61000-3-6)\n\n" +
            "Las alarmas y triggers son INDICADORES DE ALERTA TEMPRANA para\n" +
            "orientar investigaciones operativas. No constituyen por sí solos\n" +
            "evidencia de incumplimiento normativo y NO deben usarse directamente\n" +
            "para sanciones regulatorias, descargos contractuales ni disputas\n" +
            "técnicas sin la correspondiente verificación con instrumentación\n" +
            "certificada y metodología normativa completa.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "2. LO QUE CUMPLE CON LAS NORMAS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  COMTRADE — IEEE C37.111-1999 / IEC 60255-24:\n" +
            "    Los archivos .cfg y .dat generados son conformes al estandar.\n" +
            "    Compatibles con cualquier visor COMTRADE certificado.\n\n" +
            "  Definiciones de potencia — IEEE Std 1459-2010:\n" +
            "    FP = P/S,   DPF = cos(φ₁),   Q_aparente = √(S²−P²)\n" +
            "    Reportadas como magnitudes distintas, sin confusión terminológica.\n\n" +
            "  Medicion armonica — IEC 61000-4-7 (via IED):\n" +
            "    Los valores de armonicos provienen del nodo MHAI del ION 7400.\n" +
            "    La conformidad con IEC 61000-4-7 Clase A es responsabilidad de\n" +
            "    Schneider Electric. HADES consume esos datos sin\n" +
            "    alterar ni reescalar los valores informados por el IED.\n\n" +
            "  Trigger THD_V absoluto — EN 50160:\n" +
            "    El umbral CRITICO THD_V > 8.0% corresponde al limite absoluto\n" +
            "    de EN 50160 para redes MT (media de 10 min, 95% del tiempo).\n\n" +
            "  Uso de THDv en clasificacion de cargas (nuevo en v1.0):\n" +
            "    THDv se usa para identificar la FUENTE de la distorsion:\n" +
            "    THDv alto + THDi bajo  → distorsion aguas arriba (UPSTREAM_DISTORTION)\n" +
            "    THDv alto + THDi alto  → carga local impactando la red (mayor certeza)\n" +
            "    THDv bajo  + THDi alto → red rigida (alta Scc); carga no lineal presente\n" +
            "                            pero la red absorbe la distorsion de tension.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "3. LIMITACION CRITICA: THD_I vs TDD (IEEE 519-2022)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "DESCRIPCION DEL PROBLEMA:\n\n" +
            "Los triggers de corriente comparan THD_I contra los umbrales de\n" +
            "TDD establecidos en IEEE 519-2022. Son magnitudes distintas:\n\n" +
            "   THDᵢ = √(I₂² + I₃² + ··· + Iₙ²) / I₁  × 100%    ← lo que calcula esta app\n\n" +
            "   TDD  = √(I₂² + I₃² + ··· + Iₙ²) / Iₗ  × 100%    ← lo que define IEEE 519\n\n" +
            "   donde Iₗ = corriente máxima de demanda (promedio 15/30 min)\n\n" +
            "EFECTO PRÁCTICO:\n\n" +
            "Cuando la carga opera por debajo de su demanda máxima (I₁ < Iₗ):\n\n" +
            "   THDᵢ / TDD = Iₗ / I₁  >  1\n\n" +
            "   Ejemplo — carga al 40% de su máximo:\n" +
            "     THDᵢ = 20%  →  TDD real =  8.0%   (en el límite normativo)\n" +
            "     THDᵢ = 30%  →  TDD real = 12.0%   (supera el límite)\n\n" +
            "   La aplicacion genera alarma WARNING con THD_I > 5% aunque el\n" +
            "   TDD real pueda estar dentro del limite de la norma.\n\n" +
            "JUSTIFICACION DEL DISEÑO:\n\n" +
            "En monitoreo en tiempo real, IL no esta disponible de forma\n" +
            "instantanea: requiere un periodo de demanda completado (15/30 min).\n" +
            "La sustitucion THD_I ~ TDD es una aproximacion CONSERVADORA que:\n\n" +
            "   * Puede generar falsas alarmas a baja carga\n" +
            "   * Nunca silencia una violacion real a plena carga\n" +
            "   * Es practica comun en sistemas SCADA de primera linea\n\n" +
            "IEEE 519-2022 Sec. 2.3 reconoce esta distincion explicitamente:\n" +
            "  'TDD is used rather than THD because THD can be misleadingly\n" +
            "   high when the fundamental current is small.'\n\n" +
            "RECOMENDACION PARA EL OPERADOR:\n\n" +
            "Los triggers WARNING/PQ_RISK de corriente deben interpretarse como\n" +
            "'investigar este feeder', NO como 'este feeder viola IEEE 519-2022'.\n" +
            "La verificacion definitiva requiere calcular TDD con IL del sistema.\n\n" +
            "MEJORA FUTURA PLANIFICADA:\n" +
            "Incorporar campo IL configurable por feeder (en FeederConfig) para\n" +
            "calcular TDD real y reemplazar esta aproximacion.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "4. LIMITACION: PLANNING LEVEL 6.5% (IEC 61000-3-6)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "El trigger THD_V > 6.5% se etiqueta como 'planning level\n" +
            "IEC 61000-3-6 MV'. Aclaracion normativa:\n\n" +
            "IEC 61000-3-6 Tabla 1 establece el nivel de compatibilidad MT:\n" +
            "   THD = 8%\n\n" +
            "El planning level NO está tabulado en la norma. Se calcula como:\n" +
            "   Gₕ = √(G²_compat − G²_fondo − Σ E²_instalaciones)\n\n" +
            "El valor 6.5% es una aproximacion al 80% del nivel de compatibilidad\n" +
            "(criterio conservador), no un valor directamente extraido de la norma.\n" +
            "Es valido como umbral de alerta preventiva, pero NO debe citarse como\n" +
            "'planning level de IEC 61000-3-6' sin un estudio de red especifico.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "5. LIMITACION: PUNTO DE MEDICION vs PCC\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "IEEE 519-2022 e IEC 61000-3-6 definen sus limites en el PCC\n" +
            "(Point of Common Coupling).\n\n" +
            "HADES mide en el punto donde esta instalado el IED\n" +
            "(ION 7400), que puede o no coincidir con el PCC segun la\n" +
            "topologia de red de ANDE.\n\n" +
            "   Si el IED esta aguas abajo del PCC: medicion mas distorsionada.\n" +
            "   Si el IED esta en el PCC: medicion es normativa directa.\n\n" +
            "La identificacion del PCC es responsabilidad del estudio de red.\n" +
            "Las alarmas aplican al punto de instalacion del IED, no\n" +
            "necesariamente al PCC contractual.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "6. LIMITACION: FORMA DE ONDA COMTRADE SINTETIZADA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Los COMTRADE generados automaticamente son SINTETIZADOS desde\n" +
            "valores RMS + espectro armonico. NO son registros de senales reales.\n\n" +
            "Supuestos del sintetizador (WaveformSynthesizer):\n" +
            "  * Sistema trifasico equilibrado (fases 0 deg, -120 deg, +120 deg)\n" +
            "  * Estado estacionario (sin transitorios ni variaciones de ciclo)\n" +
            "  * Sin interarmonicos ni subharmonicos\n" +
            "  * Frecuencia exactamente 50.000 Hz\n\n" +
            "Uso valido de los archivos generados:\n" +
            "  CORRECTO:   Documentar magnitud y espectro del evento\n" +
            "  CORRECTO:   Comparacion visual de niveles armonicos\n" +
            "  INCORRECTO: Analisis de transitorios reales\n" +
            "  INCORRECTO: Prueba de reles de proteccion (requiere senal real)\n" +
            "  INCORRECTO: Forense de calidad de energia con precision Clase A\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "7. TABLA RESUMEN DE CONFORMIDAD NORMATIVA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  Aspecto                          Norma               Estado\n" +
            "  ─────────────────────────────    ─────────────────   ──────\n" +
            "  Formato COMTRADE (.cfg/.dat)     IEEE C37.111-1999   CONFORME\n" +
            "  Definiciones de potencia         IEEE 1459-2010      CONFORME\n" +
            "  Datos de medicion armonica       IEC 61000-4-7       VIA IED\n" +
            "  Trigger THD_V limite absoluto    EN 50160            CONFORME\n" +
            "  Trigger THD_I vs TDD             IEEE 519-2022       APROX.\n" +
            "  Planning level 6.5%              IEC 61000-3-6       APROX.\n" +
            "  Punto de medicion vs PCC         IEEE 519-2022       TOPOLOGIA\n" +
            "  Forma de onda sintetizada        IEC 60255-24        SINTET.\n" +
            "  THDv — fuente de distorsion      IEC 61000-3-6 §5    CONFORME\n" +
            "  UPSTREAM_DISTORTION detection    IEC 61000-3-6 §5    CONFORME\n\n" +
            "  CONFORME  = Implementacion directamente trazable a la norma\n" +
            "  VIA IED   = Conformidad delegada al instrumento certificado\n" +
            "  APROX.    = Valor valido como indicacion; verificacion formal\n" +
            "              requerida para conclusiones normativas definitivas\n" +
            "  TOPOLOGIA = Depende del punto de instalacion del IED en la red\n" +
            "  SINTET.   = Datos calculados, no registros de senal real\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "8. REFERENCIAS NORMATIVAS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  IEEE 519-2022     — Harmonic Control in Electric Power Systems\n" +
            "  IEC 61000-3-6     — Emission limits for distorting loads in MV/HV\n" +
            "  IEC 61000-4-7     — Measurement techniques for harmonics (Cl A/B)\n" +
            "  IEC 61000-4-30    — Power quality measurement methods (Cl A)\n" +
            "  EN 50160:2010     — Voltage characteristics of electricity supplied\n" +
            "                      by public distribution networks\n" +
            "  IEEE 1459-2010    — Definitions for Measurement of Electric Power\n" +
            "  IEEE C37.111-1999 — Common Format for Transient Data Exchange\n" +
            "  IEC 60255-24:2013 — Common format for transient data exchange\n" +
            "  IEC 61000-3-7     — Flicker emission limits in MV/HV networks\n" +
            "  IEC 61557-12      — Performance measuring instruments (PQ meters)\n" +
            "  CIGRE WG C4.109 (2014) — Harmonic parameters of AC arc furnaces\n"
        },

        // ── Application usage ────────────────────────────────────────────────

        {"📊 Dashboard y KPIs",
            "DASHBOARD — RESUMEN GENERAL\n\n" +
            "El dashboard muestra en tiempo real el estado del alimentador seleccionado:\n\n" +
            "  TILES KPI:\n" +
            "  • Tensión: voltaje promedio trifásico en kV.\n" +
            "  • Corriente: corriente promedio trifásica en A.\n" +
            "  • Potencia: potencia activa total trifásica en kW o MW.\n" +
            "  • THDi: distorsión armónica total de corriente.\n" +
            "    - Verde:  < 8%  (referencia IEEE 519 para sistemas típicos)\n" +
            "    - Ámbar:  8-12% (elevado)\n" +
            "    - Rojo:   > 12% (crítico)\n" +
            "  • Tipo de Carga: clasificación automática del tipo de carga detectada.\n\n" +
            "  MÉTRICAS ELÉCTRICAS:\n" +
            "  • Q: potencia reactiva en kVAR\n" +
            "  • S: potencia aparente en kVA\n" +
            "  • FP: factor de potencia verdadero = P / (Vrms * Irms)\n" +
            "  • Frecuencia: frecuencia de red en Hz\n" +
            "  • THDv: distorsión armónica de tensión\n" +
            "  • CV: coeficiente de variación de corriente (indicador de estabilidad)\n" +
            "  • H5/H1, H7/H1: ratios de armónicos característicos\n" +
            "  • Resonancia: frecuencia y orden de resonancia detectada\n\n" +
            "  GRÁFICAS MINI:\n" +
            "  • Tendencia THDi: área chart con historial reciente\n" +
            "  • Espectro armónico: barras H1-H13 como % del fundamental"
        },

        {"~ Análisis Armónico",
            "ANÁLISIS DE ARMÓNICOS\n\n" +
            "Visualiza el espectro armónico completo H1-H25 del alimentador.\n\n" +
            "  SELECTOR DE FASE:\n" +
            "  • L1, L2, L3: muestra el espectro de esa fase específica\n" +
            "  • Prom.: promedio trifásico de los espectros\n\n" +
            "  GRÁFICA DE BARRAS:\n" +
            "  • Eje X: orden armónico H1 a H25\n" +
            "  • Eje Y: amplitud como % del fundamental (H1 = 100%)\n" +
            "  • Colores:\n" +
            "    - Azul:  nivel normal  (< 8%)\n" +
            "    - Ámbar: elevado       (8-15%)\n" +
            "    - Rojo:  crítico       (> 15%)\n\n" +
            "  TARJETAS RESUMEN:\n" +
            "  • H2  (100 Hz): armónico par — indica asimetría o perturbación no repetitiva\n" +
            "  • H3  (150 Hz): triplen — cargadores, SMPS monofásicos, EAF\n" +
            "  • H5  (250 Hz): 5° orden — firma de rectificadores trifásicos y VFD\n" +
            "  • H7  (350 Hz): 7° orden — acompaña al H5 en el patrón 6k±1\n" +
            "  • H9  (450 Hz): triplen 9° — cargas electrónicas mixtas\n" +
            "  • H11-H13: armónicos de alta frecuencia en rectificadores\n\n" +
            "  TABLA DE DATOS:\n" +
            "  • Para cada orden H1-H25:\n" +
            "    - Frecuencia en Hz (n × 50 Hz)\n" +
            "    - Amplitud de corriente en A y %H1\n" +
            "    - Amplitud de tensión en V y %H1\n" +
            "    - Estado: OK / ELEVADO / CRÍTICO"
        },

        {"📋 Monitor Multi-Feeder",
            "MONITOR MULTI-FEEDER\n\n" +
            "Tabla SCADA que muestra todos los alimentadores en tiempo real.\n\n" +
            "  COLUMNAS:\n" +
            "  #        — Número de orden\n" +
            "  Alimentador — Nombre del feeder configurado\n" +
            "  Estado   — Semáforo: OK / WARN / CRIT\n" +
            "  Va (kV)  — Tensión fase-neutro promedio\n" +
            "  Ia (A)   — Corriente promedio trifásica\n" +
            "  P (kW)   — Potencia activa\n" +
            "  Q (kVAR) — Potencia reactiva\n" +
            "  FP       — Factor de potencia\n" +
            "  THDi %   — Distorsión armónica de corriente (coloreado)\n" +
            "  THDv %   — Distorsión armónica de tensión\n" +
            "  Tipo Carga — Clasificación de carga detectada\n" +
            "  Alrm.    — Número de alarmas activas\n\n" +
            "  INTERACCIÓN:\n" +
            "  • Doble clic en fila: navega al Dashboard con ese feeder\n" +
            "  • Búsqueda: filtra por nombre o ID de feeder\n" +
            "  • Exportar CSV: exporta datos de todos los feeders"
        },

        {"📈 Tendencias",
            "TENDENCIAS HISTÓRICAS\n\n" +
            "Cuatro gráficas de área mostrando la evolución temporal de:\n\n" +
            "  1. THDi (%) — Distorsión armónica de corriente\n" +
            "  2. THDv (%) — Distorsión armónica de tensión\n" +
            "  3. Tensión (kV) — Voltaje promedio trifásico\n" +
            "  4. Corriente (A) — Corriente promedio trifásica\n\n" +
            "  CONFIGURACIÓN:\n" +
            "  • Máximo 120 puntos en memoria (buffer circular)\n" +
            "  • Selector de alimentador: cada feeder tiene su propio historial\n" +
            "  • Botón Limpiar: borra el buffer de datos del feeder activo\n\n" +
            "  NOTA: Los datos no persisten entre reinicios de la aplicación.\n" +
            "  Para almacenamiento persistente, use Exportar CSV."
        },

        {"🔔 Alarmas",
            "GESTIÓN DE ALARMAS\n\n" +
            "Sistema de alarmas de 4 niveles para monitoreo de calidad de energía.\n\n" +
            "  NIVELES:\n" +
            "  WARNING   — Advertencia: valor se acerca al límite (80% del umbral)\n" +
            "  PQ_RISK   — Riesgo de calidad: supera límite IEC/IEEE de referencia\n" +
            "  CRITICAL  — Condición crítica: resonancia, desbalance severo, sobrecorriente\n" +
            "  DETECTION — Detección de carga: cripto/datacenter identificado\n\n" +
            "  PARÁMETROS MONITOREADOS:\n" +
            "  • THDv: distorsión de tensión (ref. IEC 61000-3-6)\n" +
            "  • THDi: distorsión de corriente (ref. IEEE 519 / IEC 61000-3-4)\n" +
            "  • Desbalance de tensión (ref. EN 50160 <= 2%)\n" +
            "  • Desbalance de corriente\n" +
            "  • Resonancia armónica\n" +
            "  • Detección de carga electrónica\n" +
            "  • Sobrecorriente\n" +
            "  • Factor de potencia bajo\n\n" +
            "  RECONOCIMIENTO:\n" +
            "  • Reconocer Sel.: reconoce la alarma seleccionada\n" +
            "  • Reconocer Todo: reconoce y limpia todas las alarmas activas\n\n" +
            "  FILTROS:\n" +
            "  • Por nivel, por feeder y por texto en el mensaje"
        },

        {"🔌 Feeders",
            "GESTIÓN DE FEEDERS\n\n" +
            "Configuración y conexión de alimentadores MT vía IEC 61850.\n\n" +
            "  IDENTIFICACIÓN:\n" +
            "  • ID: identificador único del feeder (ej. AL-01)\n" +
            "  • Nombre: nombre descriptivo para mostrar en la UI\n\n" +
            "  CONEXIÓN IEC 61850 MMS:\n" +
            "  • Host/IP: dirección IP del IED\n" +
            "  • Puerto: 102 (MMS ACSE estándar)\n" +
            "  • Nombre IED, Ref. MMXU, LD Instance, Polling interval\n\n" +
            "  PARÁMETROS DE RED:\n" +
            "  • Vnom: tensión nominal en kV\n" +
            "  • Inom: corriente nominal en A\n" +
            "  • Scc: potencia de cortocircuito en MVA (para análisis de resonancia)\n" +
            "  • Capacitancia: capacitancia total del feeder en uF\n\n" +
            "  AGREGAR SIMULADO:\n" +
            "  Agrega un feeder con datos simulados (no requiere IED real).\n" +
            "  Seleccione el perfil de carga para el comportamiento deseado."
        },

        {"📏 Normas",
            "CUMPLIMIENTO NORMATIVO\n\n" +
            "Evaluación automática contra tres normas internacionales.\n\n" +
            "  IEC 61000-3-6:2008\n" +
            "  Emisiones armónicas en redes MT/AT. Límites para 23 kV:\n" +
            "  • THDv <= 5% (nivel de planificación)\n" +
            "  • H5 <= 4%,  H7 <= 4%,  H11/H13 <= 3%\n\n" +
            "  IEEE 519-2022\n" +
            "  IMPORTANTE: IEEE 519 NO limita THDi. Limita TDD de corriente.\n" +
            "  TDD = √(I₂² + I₃² + ··· + Iₕ²) / Iₗ  × 100%     (Iₗ = corriente máx. demanda)\n" +
            "  • TDD ≤ 8%   (Icc/Iₗ = 20–50, bus de distribución)\n" +
            "  • THDv ≤ 5% en PCC (Punto de Acoplamiento Común)\n\n" +
            "  EN 50160:2010\n" +
            "  Características de tensión en redes de distribución pública.\n" +
            "  • Desbalance de tensión: ≤ 2% (promedio 10 min, 95% del tiempo)\n" +
            "  • THDv: ≤ 8% (percentil 95% semanal)\n" +
            "  • Frecuencia: 50 Hz ± 1%"
        },

        {"⚡ Detección de Cargas",
            "DETECCIÓN DE CARGAS ELECTRÓNICAS\n\n" +
            "El módulo ElectronicLoadDetector clasifica automáticamente el tipo de carga\n" +
            "usando análisis multivariable de señales armónicas: CV, THDi, THDv, H5/H1, H7/H1, FP.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "ROL DEL THDv EN LA CLASIFICACIÓN\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "El THDv (distorsión armónica de tensión) permite identificar la FUENTE\n" +
            "de la distorsión, no solo su presencia:\n\n" +
            "  THDv alto + THDi bajo  → UPSTREAM_DISTORTION\n" +
            "    La distorsión proviene de aguas arriba (otra rama de la red,\n" +
            "    otro feeder o la subestación). El alimentador monitoreado no\n" +
            "    es la fuente.  Referencia: IEC 61000-3-6 §5.\n\n" +
            "  THDv alto + THDi alto  → Carga local impactando la red\n" +
            "    Mayor certeza: la carga no lineal está en este alimentador\n" +
            "    y su distorsión se propaga a la tensión de red.\n\n" +
            "  THDv bajo  + THDi alto → Red rígida (Scc alta)\n" +
            "    La carga no lineal existe, pero la red tiene baja impedancia\n" +
            "    y absorbe la distorsión sin elevarla en tensión.\n" +
            "    NO descarta la presencia de carga electrónica.\n\n" +
            "  Límites normativos THDv: EN 50160 ≤ 8% (MT, perc. 95%),\n" +
            "  IEC 61000-3-6 ≤ 5% (nivel de planificación MT).\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "TIPOS DE CARGA DETECTADOS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  UPSTREAM_DISTORTION  (prioridad máxima)\n" +
            "  THDv > 5% y THDi < 8% y H5/H1 < 8%\n" +
            "  La distorsión se origina fuera del alimentador monitoreado.\n\n" +
            "  LINEAR\n" +
            "  THDi < 5% y H5/H1 < 5%\n" +
            "  Sin patrón armónico característico. Carga lineal.\n\n" +
            "  LIGHTING  (lámparas LED masivas)\n" +
            "  THDi > 10%, H5/H1 < 8%, FP 0.75–0.95\n" +
            "  H3 dominante; efecto de masa de drivers LED sin PFC.\n\n" +
            "  CRYPTO_MINING  (minería / HPC)\n" +
            "  THDi > 15%, CV < 5%, H5/H1 > 15%, H7/H1 > 10%, FP > 0.92\n" +
            "  SMPS de alta densidad con PFC activo. Consumo muy estable.\n" +
            "  IMPORTANTE: No exclusivo de criptomonedas; coincide con UPS\n" +
            "  industriales, cargadores EV de alta potencia y rectificadores\n" +
            "  de gran escala. Confirmación requiere análisis temporal del CV.\n\n" +
            "  DATA_CENTER\n" +
            "  THDi > 15%, CV < 5%, H5/H1 > 15%, H7/H1 > 10%, FP ≤ 0.92\n" +
            "  Similar al cripto pero con factor de potencia no tan alto.\n\n" +
            "  INDUSTRIAL  (rectificador 6-pulsos)\n" +
            "  THDi > 8%, H5 > 12%, H7 > 8%, H11 > 5%, H13 > 4%\n" +
            "  Firma típica de variadores de frecuencia (VFD) y rectificadores\n" +
            "  de gran escala (electrometalurgia, tracción, compensadores).\n\n" +
            "  ELECTRONIC_LIGHT  (electrónica ligera)\n" +
            "  THDi > 8%, H5 > 8% o H7 > 5%\n" +
            "  Electrónica de consumo, SMPS de baja potencia.\n\n" +
            "  MIXED_ELECTRONIC\n" +
            "  THDi > 5% sin firma espectral clara dominante.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "ÍNDICE DE ELECTRÓNICA (0-100)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Score compuesto de 4 componentes (pesos suma máx = 100):\n\n" +
            "  25 pts — CV inverso:   carga estable → electrónica de alta densidad\n" +
            "  35 pts — THDi:         corriente distorsionada → carga no lineal\n" +
            "  25 pts — H5 + H7:      firma espectral rectificador 6/12-pulsos\n" +
            "  15 pts — THDv:         distorsión propagada a tensión (confirma impacto\n" +
            "                         en red; bajo en redes rígidas, no penaliza detección)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "PARÁMETROS CLAVE\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  CV = σ(I) / μ(I) · 100%\n" +
            "  CV < 5%: carga muy estable (SMPS regulado, servidores, miners)\n" +
            "  H₅/H₁, H₇/H₁: ratios armónicos de 5° y 7° orden\n" +
            "  H₁₁/H₁, H₁₃/H₁: ratios de 11° y 13° orden (firma 6-pulsos)\n\n" +
            "  Refs: IEEE 1459-2010, IEC 61000-3-12, IEC 61000-3-6 §5,\n" +
            "  papers de caracterización de data centers y granjas de minería."
        },

        {"📡 IEC 61850 / MMS",
            "PROTOCOLO IEC 61850 Y MMS\n\n" +
            "IEC 61850 es la norma internacional para comunicación en subestaciones.\n\n" +
            "  ARQUITECTURA:\n" +
            "  • IED: relé, medidor, controlador\n" +
            "  • MMS: protocolo de mensajería (Manufacturing Message Specification)\n" +
            "  • ACSE: capa de sesión (Application Control Service Element)\n" +
            "  • SCL: archivo XML de configuración (Substation Configuration Language)\n\n" +
            "  MODELO DE DATOS:\n" +
            "  Servidor > LD > LN > DO > DA\n\n" +
            "  NODO MMXU (medición de potencia):\n" +
            "  • PhV: tensión fase-tierra  • A: corriente de fase\n" +
            "  • TotW: P activa  • TotVAr: Q reactiva  • TotVA: S aparente\n" +
            "  • TotPF: factor de potencia  • Hz: frecuencia\n\n" +
            "  FUNCTIONAL CONSTRAINTS:\n" +
            "  • MX: medición analógica  • ST: estado  • CF: configuración\n\n" +
            "  PUERTO: 102 (IANA)"
        },

        // ── Theoretical foundations — corrected and peer-reviewed ─────────────

        {"📐 Fundamentos: Armónicos y Fourier",
            "FUNDAMENTOS TEÓRICOS: ANÁLISIS ARMÓNICO\n" +
            "Revisado contra: IEEE 519-2022, IEC 61000-4-7, Arrillaga & Watson (2003)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. SERIE DE FOURIER Y CARGAS NO LINEALES\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Una señal de corriente periódica puede descomponerse:\n\n" +
            "   i(t) = I₀ + √2·I₁·cos(ωt+φ₁) + √2·I₂·cos(2ωt+φ₂) + √2·I₃·cos(3ωt+φ₃) + ···\n\n" +
            "   En forma compacta:  i(t) = I₀ + Σ √2·Iₙ·cos(nωt + φₙ)   (n = 1..∞)\n\n" +
            "   Iₙ = valor eficaz (RMS) del armónico n-ésimo.\n" +
            "   √2·Iₙ = amplitud de pico del armónico n-ésimo.\n\n" +
            "CARGAS LINEALES Y ARMÓNICOS:\n\n" +
            "Una carga lineal cumple:  i(t) = v(t) / Z\n\n" +
            "Las cargas lineales NO generan armónicos nuevos, pero SÍ reproducen\n" +
            "los armónicos presentes en la tensión de la red.\n" +
            "Si v(t) contiene armonicos, la corriente también los contendrá,\n" +
            "incluso en transformadores, motores y resistencias puras.\n\n" +
            "Las cargas NO LINEALES sí generan nuevos armónicos (n ≥ 2)\n" +
            "independientemente de la pureza de la tensión.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "2. THDi vs TDD — DISTINCIÓN NORMATIVA CRÍTICA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "THDi (Total Harmonic Distortion de corriente):\n\n" +
            "   THDᵢ = √(I₂² + I₃² + ··· + Iₙ²) / I₁  × 100%\n\n" +
            "THDᵢ depende de la carga instantánea: a baja carga, THDᵢ sube\n" +
            "aunque la distorsión absoluta sea pequeña.\n\n" +
            "TDD (Total Demand Distortion) — métrica de IEEE 519-2022:\n\n" +
            "   TDD = √(I₂² + I₃² + ··· + Iₙ²) / Iₗ  × 100%\n\n" +
            "   Iₗ = corriente máxima de demanda del sistema (promedio 15/30 min).\n\n" +
            "IMPORTANTE: IEEE 519-2022 NO establece límites para THDi.\n" +
            "IEEE 519 limita TDD de corriente en el PCC (Punto de Acoplamiento Común).\n" +
            "Ejemplo IEEE 519 Tabla 1 (sistema < 1 kV, Icc/Iₗ = 20–50):\n" +
            "   TDD ≤ 8%,  H<11 ≤ 4%,  H11–16 ≤ 2%,  H17–22 ≤ 1.5%\n\n" +
            "NOTA: H13 (rango 11-16) tiene límite 2.0%, NO 1.5% (ese límite es para H17-22).\n\n" +
            "Para sistemas 1-69 kV (alimentadores MT 23kV): usar Tabla 2 de IEEE 519-2022,\n" +
            "cuyos límites difieren según la relación Isc/IL del PCC.\n\n" +
            "IEC 61000-3-6 y EN 50160 sí limitan THDv (tensión).\n\n" +
            "Ref: IEEE Std 519-2022 Clause 5.3, Tabla 1 (< 1kV) y Tabla 2 (1-69kV).\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "3. FACTOR DE POTENCIA — DEFINICIÓN RIGUROSA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Factor de potencia verdadero:\n\n" +
            "   FP = P / S = P / (V_rms · I_rms)\n\n" +
            "En presencia de armónicos se descompone en dos factores:\n\n" +
            "   FP = DPF · DF\n\n" +
            "   DPF (Factor de Desplazamiento) = cos(φ₁)\n" +
            "       ángulo entre el fundamental de tensión y corriente\n\n" +
            "   DF = I₁ / I_rms = 1 / √(1 + THDᵢ²)\n\n" +
            "Por lo tanto:\n\n" +
            "   FP = cos(φ₁) / √(1 + THDᵢ²)\n\n" +
            "NOTA: Esta fórmula ya incorpora el efecto de I₁/I_rms en el DF.\n" +
            "No debe agregarse un factor I₁/Itot adicional (sería redundante).\n\n" +
            "Ejemplo SMPS sin PFC: DPF ≈ 0.97,  THDᵢ ≈ 45%\n" +
            "   FP = 0.97 / √(1 + 0.45²) = 0.97 / 1.097 ≈ 0.88\n\n" +
            "Ref: IEEE Std 1459-2010, sec. 3.1-3.3.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "4. POTENCIA EN SISTEMAS NO SINUSOIDALES\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Teoría de Budeanu (histórica, ampliamente usada):\n\n" +
            "   S² = P² + Q² + D²\n\n" +
            "   P = potencia activa (transferible)\n" +
            "   Q = potencia reactiva de Budeanu (suma de reactivas armónicas)\n" +
            "   D = potencia de distorsión\n\n" +
            "LIMITACIÓN: La Q de Budeanu no tiene interpretación física clara\n" +
            "en sistemas no sinusoidales. D no es minimizable por compensación.\n\n" +
            "IEEE 1459-2010 (framework moderno recomendado):\n\n" +
            "   S² = P² + Q₁² + D²\n\n" +
            "   Q₁ = V₁ · I₁ · sin(φ₁)       (reactiva del fundamental)\n" +
            "   D  = √(S² − P² − Q₁²)         (potencia no fundamental)\n\n" +
            "Esta formulación tiene base física: Q₁ es minimizable con\n" +
            "condensadores; D no lo es (requiere filtros armónicos).\n\n" +
            "NOTA SOBRE EL VISOR COMTRADE:\n" +
            "La Q calculada con Q = √(S² − P²) corresponde a la Q aparente,\n" +
            "que incluye tanto Q1 como D. El resultado exacto depende del método\n" +
            "implementado en cada software (IEEE 1459, Budeanu, o fundamental-only).\n\n" +
            "Ref: IEEE Std 1459-2010; Czarnecki L.S. (2008) 'Currents' Physical\n" +
            "     Components (CPC) Power Theory', Electric Power Quality and Utilization.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "5. RESONANCIA ARMÓNICA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Fórmula física exacta (circuito LC):\n\n" +
            "   f_res = 1 / (2π · √(L·C))\n\n" +
            "Aproximación para sistemas de distribución con banco de condensadores\n" +
            "(válida para sistema simple: Scc >> Qc, red inductiva dominante):\n\n" +
            "   h_res = f_res / f₀ = √(Scc / Qc)\n\n" +
            "   L = V² / (ω·Scc)    (equivalente inductivo de red)\n" +
            "   C = Qc / (ω·V²)     (equivalente capacitivo del banco)\n\n" +
            "Esta aproximación es útil para evaluación preliminar, pero en redes\n" +
            "reales con múltiples condensadores o cargas distribuidas la resonancia\n" +
            "debe calcularse con el modelo L-C completo del alimentador.\n\n" +
            "Ref: Arrillaga J., Watson N.R. (2003) 'Power System Harmonics',\n" +
            "     Wiley, cap. 4. IEEE Std 1531-2003.\n"
        },

        {"📐 Fundamentos: Fortescue / Secuencias",
            "FUNDAMENTOS TEÓRICOS: COMPONENTES SIMÉTRICAS\n" +
            "Revisado contra: IEC 60034-26, EN 50160, Fortescue (1918)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "TRANSFORMADA DE FORTESCUE\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Para un sistema trifásico (Xa, Xb, Xc) — tensión o corriente:\n\n" +
            "   X1 = (1/3)(Xa + a·Xb + a²·Xc)    Secuencia Positiva (A→B→C)\n" +
            "   X2 = (1/3)(Xa + a²·Xb + a·Xc)    Secuencia Negativa (A→C→B)\n" +
            "   X0 = (1/3)(Xa + Xb + Xc)          Secuencia Cero (homopolar)\n\n" +
            "Operador de secuencia:\n" +
            "   a  = e^(j·2π/3) = −0.5 + j·0.866   (rotación 120° antihorario)\n" +
            "   a² = e^(j·4π/3) = −0.5 − j·0.866   (rotación 240° antihorario)\n\n" +
            "SIGNIFICADO FÍSICO:\n" +
            "  X1 (positiva) = componente en secuencia normal de la red\n" +
            "  X2 (negativa) = secuencia opuesta — causa calentamiento en motores\n" +
            "  X0 (cero/homopolar) = componente en fase entre las tres fases\n\n" +
            "DESBALANCE (IEC 61000-4-27 / EN 50160):\n" +
            "   Desbalance = V₂ / V₁ · 100%\n" +
            "   Límite EN 50160: ≤ 2% (promedio 10 min, 95% del tiempo)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "RELACIÓN ENTRE ARMÓNICOS Y SECUENCIAS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "El siguiente patrón es válido para sistemas trifásicos EQUILIBRADOS\n" +
            "con cargas simétricas en las tres fases:\n\n" +
            "   H1  (50 Hz)   → Secuencia Positiva  (+)\n" +
            "   H2  (100 Hz)  → Secuencia Negativa  (-)\n" +
            "   H3  (150 Hz)  → Secuencia Cero      (0)  ← TRIPLEN\n" +
            "   H4  (200 Hz)  → Secuencia Positiva  (+)\n" +
            "   H5  (250 Hz)  → Secuencia Negativa  (-)\n" +
            "   H6  (300 Hz)  → Secuencia Cero      (0)  ← TRIPLEN\n" +
            "   Patrón: +, -, 0, +, -, 0, ...  (módulo 3)\n\n" +
            "EXCEPCIONES AL PATRÓN:\n" +
            "El patrón +/-/0 puede romperse en los siguientes casos:\n" +
            "  • Cargas monofásicas (rectificadores individuales)\n" +
            "  • Sistemas con neutro abierto\n" +
            "  • Tensiones desequilibradas en las tres fases\n" +
            "  • Desequilibrio de fase en las fuentes\n" +
            "En estos casos el análisis requiere Fortescue completo por armónico.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "ARMÓNICOS TRIPLEN Y EL CONDUCTOR NEUTRO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Los armónicos triplén (H3, H9, H15...) son de secuencia cero:\n" +
            "están en fase en las tres fases y SE SUMAN en el neutro.\n" +
            "Para n = 3k (sistema equilibrado):\n\n" +
            "   I_neutro,3k = 3 · I_fase,3k\n\n" +
            "Esto puede triplicar la corriente de neutro respecto a una fase,\n" +
            "con riesgo de sobrecalentamiento del conductor neutro en edificios\n" +
            "con alta densidad de equipos electrónicos (computadoras, UPS).\n\n" +
            "Ref: Fortescue C.L. (1918) AIEE Trans. 37:1027-1140.\n" +
            "     IEC 60034-26:2006 (Efectos del desbalance en motores).\n"
        },

        {"🔋 Modelo: Cripto-Miner / SMPS sin PFC",
            "MODELO DE CARGA: SMPS SIN PFC (perfil cripto-miner legacy)\n" +
            "SimProfile.CRYPTO_MINER\n" +
            "Revisado contra: IEC 61000-3-2:2018, Lim et al. (2022)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "NOTA SOBRE EL MODELO SIMULADO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Este perfil simula SMPS (Switch-Mode Power Supply) de alta potencia\n" +
            "SIN corrección de factor de potencia activa.\n\n" +
            "HARDWARE MODERNO (Antminer S19, Whatsminer M50, post-2015):\n" +
            "Usa PFC activo obligatorio por IEC 61000-3-2 Clase A.\n" +
            "  THDi real: 10-20%,  FP real > 0.99\n\n" +
            "HARDWARE LEGADO (fuentes ATX pre-2010, cargadores no regulados):\n" +
            "Sin PFC, rectificador de diodos + capacitor bulk.\n" +
            "  THDi medido: 65-130%  (pulsos de conducción 20-30°)\n" +
            "  FP real: 0.55-0.70   (DPF ≈ 0.95-0.98, THD penaliza fuertemente)\n" +
            "  ⚠ NOTA: Con THDi=65%: FP=0.97/√(1+0.65²)=0.79. Con THDi=130%: FP≈0.56.\n" +
            "  La afirmación 'THDi=40-55%' es propia de fuentes con reactor de línea;\n" +
            "  un bridge+bulk sin ningún filtrado tiene THDi ≥ 65%.\n\n" +
            "El simulador usa el modelo legado para máximo contraste armónico\n" +
            "en demostraciones. Los valores representan una instalación con\n" +
            "equipamiento antiguo o fuentes industriales sin PFC.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "ANÁLISIS DE CIRCUITO: RECTIFICADOR MONOFÁSICO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Topología:\n" +
            "  AC ─[Diode bridge]─[Bulk capacitor C]─[DC-DC]─> DC\n\n" +
            "El puente conduce solo cuando v_red > V_condensador.\n" +
            "Resultado: pulsos de corriente angostos (20-40° de conductión)\n" +
            "que contienen todos los armónicos impares:\n\n" +
            "   Armónicos presentes: H1, H3, H5, H7, H9, H11, H13 ...\n" +
            "   (Para trifásico balanceado: solo H1, H5, H7, H11, H13... patrón 6k±1)\n\n" +
            "DPF del rectificador sin PFC:\n" +
            "   cos(φ₁) ≈ 0.95–0.98   (conducción discontinua; NO es 0.999)\n\n" +
            "FP verdadero con THDᵢ = 45%:\n" +
            "   FP = cos(φ₁) / √(1 + THDᵢ²) = 0.97 / √(1 + 0.45²) ≈ 0.88\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "ESPECTRO SIMULADO (valores normalizados reales)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Los siguientes valores son el resultado del modelo de simulación\n" +
            "tras normalización para que THD = 45% (verificado):\n\n" +
            "   THDi: 45%  |  DPF: 0.985 (desplazamiento)  |  FP verdadero: ~0.88\n" +
            "   H3/H1 =  3.1%   ← bajo: modelo asume carga 3φ con patrón 6k±1\n" +
            "   H5/H1 = 36.5%  ← dominante\n" +
            "   H7/H1 = 22.9%  ← dominante\n" +
            "   H9/H1 =  8.3%\n" +
            "  H11/H1 =  7.3%\n" +
            "  H13/H1 =  5.2%\n" +
            "  H15/H1 =  3.1%\n\n" +
            "NOTA SOBRE H3: Un SMPS monofásico real genera H3=20-35%.\n" +
            "H3 bajo (3.1%) es válido cuando las unidades individuales se distribuyen\n" +
            "equilibradamente entre fases (resultado: cancelación trifásica de triplén).\n" +
            "En instalaciones con desbalance de fases, H3 puede ser tan alto como H5.\n\n" +
            "CV: < 1%  (carga muy estable — ASIC a frecuencia fija)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "ESTABILIDAD DE CARGA (CV)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Los ASIC operan a frecuencia de clock fija y consumen potencia\n" +
            "casi constante. El regulador interno mantiene Vout = constante:\n\n" +
            "   CV = σ(I) / μ(I) · 100%  ≈  0.3–0.8%\n\n" +
            "Esta estabilidad estadística es la principal firma diferenciadora.\n" +
            "Sin embargo, NO es exclusiva de minería:\n" +
            "  UPS, cargadores EV, rectificadores industriales también son estables.\n" +
            "La detección combinada (CV + espectro + duración) mejora la precisión.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "NORMATIVA APLICABLE\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  IEC 61000-3-2:2018  — Clase A: THDi para equipos >= 16A/fase\n" +
            "  IEC 61000-3-12:2011 — Corrientes armónicas > 16A\n" +
            "  IEEE 519-2022       — TDD en el PCC\n" +
            "  Lim et al. (2022) 'Harmonic Signature of Cryptocurrency Mining\n" +
            "    Facilities', IEEE Trans. Power Delivery, 37(4), 2812-2822.\n"
        },

        {"🖥 Modelo: Datacenter / PFC Activo",
            "MODELO DE CARGA: DATACENTER CON PFC ACTIVO\n" +
            "SimProfile.DATACENTER\n" +
            "Revisado contra: IEC 62368-1, 80 PLUS Standard, IEEE 1459\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. TOPOLOGÍA: BOOST PFC + LLC RESONANTE\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  AC ─[EMI filter]─[Boost PFC]─[DC bus 380V]─[LLC converter]─> 12V/48V\n\n" +
            "El Boost PFC opera como convertidor elevador controlado por corriente:\n\n" +
            "   i_ref(t) = k · |v_red(t)|    (referencia proporcional a la tensión)\n\n" +
            "El controlador fuerza i(t) ~ v(t), obteniendo corriente casi sinusoidal.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "2. ESPECTRO SIMULADO (valores verificados)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Los siguientes valores son el resultado real de la simulación\n" +
            "tras normalización para que THD = 18% (verificado matemáticamente):\n\n" +
            "   THDi: 18%  |  FP: 0.990\n" +
            "   H3/H1 =  2.6%  (reducido por PFC)\n" +
            "   H5/H1 = 14.6%  (residual del convertidor)\n" +
            "   H7/H1 =  9.4%\n" +
            "   H9/H1 =  3.1%\n" +
            "  H11/H1 =  2.1%\n\n" +
            "VERIFICACIÓN: sqrt(14.6^2+9.4^2+3.1^2+2.1^2+2.6^2)/100 * 100 = 18% ✓\n\n" +
            "Mediciones reales en servidores (valores de referencia):\n" +
            "  Dell PowerEdge R740:   THDi = 8.2%,   FP = 0.998\n" +
            "  HPE ProLiant DL380:    THDi = 11.4%,  FP = 0.997\n" +
            "  Supermicro 2U server:  THDi = 15.8%,  FP = 0.994\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "3. ESTABILIDAD Y PERFIL TEMPORAL\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Variación modelada:\n" +
            "   I(t) = I_base · (0.75 + 0.05 · sin(2πt / T_batch))\n\n" +
            "donde T_batch ~ 1 hora (ciclos de backup, actualización, etc.)\n\n" +
            "CV típico: 2-8% (más variable que SMPS sin PFC, menos que VFD).\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "4. NORMATIVA APLICABLE\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  IEC 62368-1:2018  — Equipos A/V y TI (reemplaza IEC 60950)\n" +
            "  80 PLUS Standard  — Platinum: THDi < 20%, Titanium: THDi < 5%\n" +
            "  Energy Star 3.0   — Requisitos PF y eficiencia para servidores\n" +
            "  ASHRAE TC9.9      — Power quality in data centers\n"
        },

        {"🔥 Modelo: Horno de Arco Eléctrico",
            "MODELO DE CARGA: HORNO DE ARCO ELÉCTRICO (EAF)\n" +
            "SimProfile.ARC_FURNACE\n" +
            "Revisado contra: CIGRE WG C4.109 (2014), IEC 61000-3-7, Baggini (2008)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. DESCRIPCIÓN FÍSICA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "El EAF (Electric Arc Furnace) funde chatarra metálica mediante arcos\n" +
            "eléctricos entre electrodos de grafito y la carga (10-100 kA).\n\n" +
            "El arco eléctrico tiene impedancia NO lineal y estocástica:\n" +
            "  v_arco = f(i, longitud arco, temperatura del plasma)\n\n" +
            "Esto produce armónicos tanto pares como impares, y perturbaciones\n" +
            "que varían ciclo a ciclo de manera no determinista.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "2. ARMÓNICOS PARES E IMPARES — CAUSA FÍSICA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "A diferencia de rectificadores (solo armónicos impares), el EAF produce\n" +
            "PARES E IMPARES porque la característica V-I del arco no es antisimétrica:\n" +
            "   f(−i) ≠ −f(i)    (asimetría entre semiciclo positivo y negativo)\n\n" +
            "ESPECTRO SIMULADO (valores verificados, ejemplo de arco moderado):\n\n" +
            "   THDi: ~22%  (variable entre 15-35% según fase del proceso)\n" +
            "   H2/H1 = 11.3%  ← componente par (firma del EAF)\n" +
            "   H3/H1 = 14.2%  ← dominante en muchos casos\n" +
            "   H4/H1 =  6.6%  ← presente en EAF, ausente en rectificadores\n" +
            "   H5/H1 =  9.4%\n" +
            "   H7/H1 =  3.8%\n\n" +
            "NOTA: H3 supera a H2 en el modelo simulado.\n" +
            "En EAF reales el orden de dominancia H2 vs H3 depende de:\n" +
            "  • Asimetría del arco entre semiciclos\n" +
            "  • Control de electrodos (compensación de longitud)\n" +
            "  • Fase del proceso: fusión inicial (H2 mayor) vs. refinado (H3 mayor)\n" +
            "No puede afirmarse que H2 sea siempre la 'firma principal'.\n" +
            "La presencia simultánea de H2 y armónicos pares SI es diagnóstica.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "3. FLICKER Y MODELO DE RANDOM WALK\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Índices de flicker (IEC 61000-3-7):\n" +
            "  P_st: índice de corto plazo (10 min),  límite MT: P_st <= 0.8\n" +
            "  P_lt: índice de largo plazo (2 horas),  límite MT: P_lt <= 0.6\n\n" +
            "Modelo de carga (paseo aleatorio — Brownian motion):\n" +
            "   i_arco(t+Δt) = i_arco(t) + 0.15 · 𝒩(0, 0.5)\n" +
            "   i_arco = clamp(i_arco, 0.2, 1.0)\n\n" +
            "La tensión fluctúa por caída en la impedancia de red:\n" +
            "   ΔV/V = (R_red · ΔP + X_red · ΔQ) / V²\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "4. FACTOR DE POTENCIA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  FP = 0.70-0.85  (inductivo; compensado con SVC o STATCOM)\n" +
            "  La compensación reactiva dinámica es imprescindible en EAF\n" +
            "  para controlar flicker y mejorar el FP.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "5. NORMATIVA Y REFERENCIAS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  IEC 61000-3-7:2008     — Emisiones armónicas y flicker en MT/AT\n" +
            "  IEC 61000-3-3/3-5      — Flicker para BT\n" +
            "  CIGRE WG C4.109 (2014) — Harmonic characteristic parameters:\n" +
            "    AC Arc Furnaces — mediciones en plantas reales\n" +
            "  Baggini, A. (2008) 'Handbook of Power Quality', Wiley, cap. 14.\n"
        },

        {"⚙ Modelo: Variador de Velocidad (VFD)",
            "MODELO DE CARGA: VARIADOR DE FRECUENCIA (VFD) 6 PULSOS\n" +
            "SimProfile.VARIABLE_SPEED_DRIVE\n" +
            "Revisado contra: IEC 61000-3-4, Mohan et al. (2002)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. RECTIFICADOR 6 PULSOS — TEORÍA DE FOURIER\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Topología: 6 diodos conducen en secuencia, creando bus DC.\n" +
            "La corriente de línea idealizada (fuente de corriente en DC link):\n\n" +
            "   iₐ(t) = (2√3/π)·Id · [sin(ωt) − (1/5)·sin(5ωt) + (1/7)·sin(7ωt)\n" +
            "                         − (1/11)·sin(11ωt) + (1/13)·sin(13ωt) + ···]\n\n" +
            "Armónicos del patrón 6 pulsos: n = 6k ± 1\n" +
            "   k=1: H₅, H₇\n" +
            "   k=2: H₁₁, H₁₃\n" +
            "   k=3: H₁₇, H₁₉\n\n" +
            "Amplitudes ideales (fuente de corriente pura, sin reactor):\n" +
            "   Iₙ / I₁ = 1/n  →  I₅ = 20%,  I₇ = 14.3%,  I₁₁ = 9.1%,  I₁₃ = 7.7%\n\n" +
            "En la práctica con reactores de línea:\n" +
            "   Reactor DC 3%:  THDi ~ 35%,  H5 ~ 24%,  H7 ~ 17%\n" +
            "   Reactor AC 5%:  THDi ~ 28%,  H5 ~ 20%,  H7 ~ 14%\n" +
            "   Filtro armónico: THDi < 12%\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "2. ESPECTRO SIMULADO (valores verificados)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Valores reales tras normalización (ciclo de velocidad variable):\n\n" +
            "  A plena velocidad (THDi = 23%):\n" +
            "   H5/H1 = 17.7%  H7/H1 = 11.8%  H11/H1 = 5.9%  H13/H1 = 4.7%\n\n" +
            "  A baja velocidad (THDi = 27%):\n" +
            "   H5/H1 = 20.8%  H7/H1 = 13.9%  H11/H1 = 6.9%  H13/H1 = 5.6%\n\n" +
            "Estos valores son consistentes con VFDs reales con reactor AC 5%.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "3. COMPORTAMIENTO THD vs VELOCIDAD\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "   THD(v_pu) = 28 − 5·v_pu + ruido     (v_pu ∈ [0, 1])\n\n" +
            "A baja velocidad (baja carga): THDi MAYOR\n" +
            "A alta velocidad (plena carga): THDi MENOR\n\n" +
            "Esto es OPUESTO al comportamiento de SMPS (estables) y útil\n" +
            "para clasificación: VFD tiene correlación THD-carga negativa.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "4. NORMATIVA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  IEC 61000-3-4:2021  — Limitación de armónicas > 16 A\n" +
            "  IEC 61800-3:2017    — Requisitos EMC para variadores de velocidad\n" +
            "  IEEE 519-2022       — TDD en PCC\n" +
            "  Mohan, Undeland, Robbins (2002) 'Power Electronics', Wiley, cap. 5.\n"
        },

        {"🏭 Modelo: Industrial Lineal + Comercial Mixta",
            "MODELOS: INDUSTRIAL LINEAL + CARGA MIXTA COMERCIAL\n" +
            "SimProfile.INDUSTRIAL_LINEAR  |  MIXED_COMMERCIAL\n" +
            "Revisado contra: IEC 60034-1, IEC 61000-2-2, ASHRAE TC9.9\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "A. INDUSTRIAL LINEAL — Motores de inducción DOL\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "DESCRIPCIÓN:\n" +
            "Motor de inducción de jaula de ardilla conectado directo (DOL).\n" +
            "Comportamiento esencialmente lineal: THDi = 1-5%.\n\n" +
            "CIRCUITO EQUIVALENTE (Steinmetz):\n" +
            "   Ė₁ = V̇₁ − (R₁ + j·X₁) · İ₁\n" +
            "   İ₂ = Ė₁ / (R₂/s + j·X₂)\n" +
            "   s  = (ωₛ − ωᵣ) / ωₛ     (deslizamiento)\n\n" +
            "Factor de potencia:\n" +
            "   FP en vacío:     0.1-0.2  (domina reactiva de magnetización Xm)\n" +
            "   FP a plena carga: 0.80-0.88 (R2/s domina sobre Xm)\n\n" +
            "ESPECTRO SIMULADO (valores verificados):\n" +
            "   THDi: ~4%  (casi sinusoidal)\n" +
            "   H3/H1 = 1.1%  H5/H1 = 3.3%  H7/H1 = 1.8%  H9/H1 = 0.9%\n\n" +
            "VERIFICACIÓN: sqrt(3.3^2+1.8^2+1.1^2+0.9^2)/100*100 = 4.0% ✓\n\n" +
            "Las fuentes de distorsión real en motores son pequeñas:\n" +
            "  • Saturación magnética: H3, H5 < 2%\n" +
            "  • Armónicos de ranura (slot harmonics): frecuencias altas, filtradas\n" +
            "  • Asimetría de enrollado: < 1%\n\n" +
            "NORMATIVA: IEC 60034-1:2022, IEC 60034-30-1 (clases IE1-IE4)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "B. CARGA MIXTA COMERCIAL / EDIFICIO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "DESCRIPCIÓN:\n" +
            "Superposición de: iluminación LED/fluorescente, HVAC, equipos TI,\n" +
            "elevadores, banco de condensadores de corrección de FP.\n\n" +
            "ESPECTRO SIMULADO (valores verificados, hora pico 85% carga):\n" +
            "   THDi: ~18%  |  PF: 0.94\n" +
            "   H3/H1 =  7.7%  (carga monofásica: computadoras, iluminación)\n" +
            "   H5/H1 = 12.8%  (VFDs en HVAC y elevadores)\n" +
            "   H7/H1 =  9.0%\n" +
            "   H9/H1 =  3.8%\n" +
            "  H11/H1 =  2.6%\n\n" +
            "PERFIL DIURNO (datos ASHRAE para edificios de oficinas):\n" +
            "  00-06h: 25%  |  06-10h: 25->85%  |  10-14h: 85%\n" +
            "  14-16h: 70%  |  16-20h: 90%       |  20-24h: 55%\n\n" +
            "CANCELACIÓN ESTADÍSTICA — LIMITACIONES:\n" +
            "La aproximación I_H,total = √N · I_H,individual supone\n" +
            "que las fases de los armónicos son aleatorias e independientes.\n\n" +
            "En la práctica esto puede NO cumplirse porque:\n" +
            "  • Las cargas conectadas al mismo transformador comparten la misma\n" +
            "    tensión distorsionada — sus armónicos pueden sumarse en fase.\n" +
            "  • La resonancia puede amplificar selectivamente ciertos armónicos.\n" +
            "  • Las fuentes monofásicas a menudo producen H3 en fase coherente.\n\n" +
            "La reducción sqrt(N) es útil para estimación preliminar en estudios\n" +
            "de planificación, pero no garantiza reducción real en todos los casos.\n\n" +
            "Ref: IEC 61000-2-2:2002, EPRI Report TR-109553 (1998).\n" +
            "     IEC 61000-3-6 Annex C (método de suma de emisiones).\n"
        },

        {"📁 Visor COMTRADE — Uso",
            "VISOR COMTRADE — GUÍA DE USO\n" +
            "Módulo: ComtradePanel  |  Formato soportado: IEEE C37.111-1999/2013, IEC 60255-24\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "APERTURA DE ARCHIVOS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "1. Ir a la pestaña 📁 COMTRADE.\n" +
            "2. Botón 'Abrir CFG' → seleccionar el archivo .cfg del registro.\n" +
            "   El archivo .dat debe estar en la misma carpeta con el mismo nombre base.\n" +
            "3. Desde la pestaña 📼 REGISTROS, doble clic en cualquier registro\n" +
            "   lo abre directamente en el visor.\n\n" +
            "Formatos soportados:\n" +
            "  • ASCII, BINARY, BINARY32\n" +
            "  • Revisiones 1991, 1999, 2013\n" +
            "  • Número ilimitado de canales analógicos y digitales\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "PANEL IZQUIERDO — INFORMACIÓN Y CANALES\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Sección INFO:\n" +
            "  • Estación: nombre de la subestación (campo station_name del .cfg)\n" +
            "  • Dispositivo: identificador del IED (rec_dev_id)\n" +
            "  • Revisión: año del formato IEEE C37.111 (1991 / 1999 / 2013)\n" +
            "  • Formato: ASCII / BINARY / BINARY32\n" +
            "  • Canales: número de canales analógicos y digitales\n" +
            "  • Fs: frecuencia de muestreo efectiva en Hz\n" +
            "  • Muestras: total de muestras del registro\n" +
            "  • Duración: duración en milisegundos\n" +
            "  • T_inicio / T_trigger: timestamps del inicio y el disparo\n\n" +
            "Selector de Canales:\n" +
            "  • Lista todos los canales analógicos con su unidad.\n" +
            "  • En la pestaña FFT y Potencia: selección múltiple activa.\n" +
            "  • En la pestaña Secuencias: seleccionar exactamente 3 canales (VA, VB, VC\n" +
            "    o IA, IB, IC) para el análisis de Fortescue.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "PESTAÑA: FORMAS DE ONDA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Muestra los canales analógicos vs. tiempo en un Canvas JavaFX.\n\n" +
            "VENTANA DE ANÁLISIS:\n" +
            "  Los dos sliders 'Inicio' y 'Fin' definen una región del registro\n" +
            "  (como porcentaje de la duración total) que se usa para:\n" +
            "    - Cálculo de FFT / armónicos\n" +
            "    - Análisis de fasores\n" +
            "    - Cálculo de secuencias simétricas\n" +
            "    - Análisis de potencia (P, Q, S, FP)\n\n" +
            "  Utilidad: permite aislar la ventana pre-falla, post-falla o un\n" +
            "  ciclo específico para análisis parcial.\n\n" +
            "  Botón 'Resetear ventana': restaura la ventana a todo el registro.\n" +
            "  Botón 'Analizar ventana': aplica la ventana y refresca todos los tabs.\n\n" +
            "  Info de ventana: muestra los tiempos en ms y el número de ciclos\n" +
            "  contenidos (a la frecuencia nominal del registro).\n\n" +
            "VISUALIZACIÓN:\n" +
            "  • Cada canal tiene un color asignado de manera consistente.\n" +
            "  • Leyenda en la parte inferior: nombre [unidad].\n" +
            "  • Hasta 2000 puntos se muestran directamente; los registros más\n" +
            "    largos se remuestrean para la visualización (el análisis usa\n" +
            "    siempre los datos originales completos).\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "PESTAÑA: FFT — ANÁLISIS ARMÓNICO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Calcula el espectro de frecuencia de cada canal analógico.\n\n" +
            "ALGORITMO:\n" +
            "  Transformada Rápida de Fourier (Cooley-Tukey, DIT in-place).\n\n" +
            "  Ventana de Hann:    w(n) = 0.5 · (1 − cos(2πn/N))\n\n" +
            "  Normalización coherente (ganancia de ventana = 0.5):\n\n" +
            "     Aₙ,pico = (4/N) · |FFT(x·w)|ₙ      (amplitud de PICO, n > 0)\n" +
            "     Aₙ,rms  = Aₙ,pico / √2              (amplitud RMS del armónico n)\n" +
            "     X₀      = (2/N) · |FFT(x·w)|₀       (componente DC)\n\n" +
            "  Derivación: para x(t) = Apico·cos(ωt), la ventana Hann da\n" +
            "  |FFT_k| = Apico·N/4,  luego  (4/N)·|FFT_k| = Apico.\n" +
            "  Para RMS:  Arms = Apico/√2.\n" +
            "  ⚠ El factor (4/N) da amplitud de PICO, no RMS directamente.\n\n" +
            "TABLA DE RESULTADOS:\n" +
            "  Columnas por canal:\n" +
            "    Frec (Hz)   — frecuencia central del bin\n" +
            "    Mag [unidad]— amplitud RMS del armónico (A_pico/√2) en la unidad del canal\n" +
            "    Fase (°)    — ángulo de fase referenciado al inicio de la ventana\n" +
            "    % H1        — amplitud como porcentaje del fundamental H1\n\n" +
            "  Fila final de cada canal: THD (%) calculado desde H₂ en adelante\n" +
            "    THD = √(X₂² + X₃² + ··· + Xₙ²) / X₁  × 100%\n\n" +
            "REFERENCIA NORMATIVA:\n" +
            "  IEC 61000-4-7:2009 — Técnicas de medición de armónicos\n" +
            "  IEC 61000-4-30:2015 — Métodos Clase A para calidad de energía\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "PESTAÑA: FASORES\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Diagrama polar del fundamental (H1) de cada canal.\n\n" +
            "  • Magnitud: amplitud RMS del fundamental (igual que FFT H1)\n" +
            "  • Ángulo: fase en grados (0° = referencia del primer canal)\n" +
            "  • Representación: flecha vectorial con longitud proporcional a la magnitud\n" +
            "  • La diferencia de ángulo entre VA e IA da φ₁ = arccos(DPF)\n\n" +
            "Uso típico:\n" +
            "  Verificar secuencia de fases (A→B→C en sistema directo),\n" +
            "  medir desfase V-I para cálculo de factor de potencia de desplazamiento,\n" +
            "  detectar desbalance de amplitudes o asimetría de fases.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "PESTAÑA: SECUENCIAS SIMÉTRICAS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Aplica la transformada de Fortescue a los tres canales seleccionados.\n\n" +
            "  SELECCIÓN DE CANALES:\n" +
            "  Seleccionar exactamente 3 canales de la lista:\n" +
            "    - Para tensión: VA, VB, VC\n" +
            "    - Para corriente: IA, IB, IC\n\n" +
            "  CÁLCULO (del fundamental H₁ de cada canal):\n" +
            "    X₁ = ⅓(Xₐ + a·X_b + a²·X_c)      Secuencia Positiva\n" +
            "    X₂ = ⅓(Xₐ + a²·X_b + a·X_c)      Secuencia Negativa\n" +
            "    X₀ = ⅓(Xₐ + X_b + X_c)            Secuencia Cero\n\n" +
            "    a = e^(j·2π/3)   (operador de secuencia, 120°)\n\n" +
            "  PREFIJO AUTOMÁTICO:\n" +
            "  La aplicación detecta si los canales son tensión (V, kV) o corriente\n" +
            "  (A, kA) y usa el prefijo V+/V-/V0 o I+/I-/I0 correspondientemente.\n\n" +
            "  INTERPRETACIÓN:\n" +
            "    X1: componente en secuencia de la red (normal)\n" +
            "    X2: secuencia inversa — indicador de desbalance (límite EN 50160: V2/V1 <= 2%)\n" +
            "    X0: componente homopolar — circula por el neutro\n\n" +
            "  Ref: Fortescue C.L. (1918); IEC 60034-26:2006; EN 50160.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "PESTAÑA: ANÁLISIS DE POTENCIA\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Calcula potencias y factores de calidad energética para un par V-I.\n\n" +
            "  SELECCIÓN DE CANALES:\n" +
            "  Usar la lista de canales para seleccionar:\n" +
            "    - Canal de tensión (unidad: V, kV)\n" +
            "    - Canal de corriente (unidad: A, kA)\n\n" +
            "  DETECCIÓN AUTOMÁTICA (auto-corrección):\n" +
            "  Si el canal de índice 0 tiene unidad de corriente y el 1 es tensión,\n" +
            "  la aplicación los intercambia automáticamente y muestra:\n" +
            "    '✓ Canales auto-corregidos (ch0=I, ch1=V → intercambiados)'\n\n" +
            "  MAGNITUDES CALCULADAS (sobre la ventana de análisis activa):\n" +
            "    P   [W]   = ∫ v(t)·i(t) dt                   (potencia activa real)\n" +
            "    S   [VA]  = V_rms · I_rms                     (potencia aparente)\n" +
            "    Q   [VAR] = √(S² − P²)                        (potencia no activa)\n" +
            "    FP        = P / S                             (factor de potencia verdadero)\n" +
            "    DPF       = cos(φ₁) = Re(V̇₁*)/(|V₁|·|I₁|)   (DPF del fundamental)\n" +
            "    THD_V [%] = √(Σ Vₕ², h≥2) / V₁ · 100%\n" +
            "    THD_I [%] = √(Σ Iₕ², h≥2) / I₁ · 100%\n\n" +
            "  NOTA SOBRE Q:\n" +
            "  Q calculada como √(S²−P²) es la 'potencia no activa' total,\n" +
            "  que incluye Q1 del fundamental + potencia de distorsión D.\n" +
            "  Ref: IEEE 1459-2010, ecuación (5).\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "EXPORTAR CSV\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  Botón 'Exportar CSV': genera un archivo CSV con:\n" +
            "    - Columna 1: timestamp en microsegundos\n" +
            "    - Columnas siguientes: valor físico de cada canal analógico\n" +
            "    - Encabezado: nombre y unidad de cada canal\n\n" +
            "  Compatible con Excel, MATLAB, Python (pandas), GNU Octave."
        },

        {"📁 Visor COMTRADE — Fundamentos",
            "VISOR COMTRADE — FUNDAMENTOS TÉCNICOS DEL FORMATO\n" +
            "Revisado contra: IEEE C37.111-1999, IEC 60255-24, IEEE 1159.3\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. EL FORMATO COMTRADE\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "COMTRADE (Common Format for Transient Data Exchange) es el estándar\n" +
            "internacional para registros de perturbaciones eléctricas.\n\n" +
            "Normas:\n" +
            "  IEEE C37.111-1991   — primera versión\n" +
            "  IEEE C37.111-1999   — revisión con mejoras en escalado y precisión\n" +
            "  IEC 60255-24:2013   — revisión conjunta IEEE/IEC (más canales, CFF)\n\n" +
            "Estructura de archivos:\n" +
            "  nombre.cfg  — archivo de configuración (texto, obligatorio)\n" +
            "  nombre.dat  — datos de muestras (ASCII o BINARY, obligatorio)\n" +
            "  nombre.hdr  — encabezado descriptivo (texto libre, opcional)\n" +
            "  nombre.inf  — información adicional (opcional)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "2. ESTRUCTURA DEL ARCHIVO .CFG (IEEE C37.111-1999)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Línea 1:   station_name , rec_dev_id , rev_year\n" +
            "Línea 2:   TT , ##A , ##D           (total, analógicos, digitales)\n" +
            "Líneas 3.. (por cada canal analógico):\n" +
            "  An, ch_id, ph, ccbm, uu, a, b, skew, min, max, primary, secondary, PS\n" +
            "  An:  número de canal (1-based)\n" +
            "  ph:  fase (A, B, C, N, ...)\n" +
            "  uu:  unidad física (V, kV, A, kA, ...)\n" +
            "  a, b: escala lineal: valor_fisico = a * raw + b\n" +
            "  min/max: rango del entero raw\n" +
            "  primary/secondary: relación del TP/TC\n" +
            "  PS: P (valores primarios) o S (secundarios)\n" +
            "Líneas D (por cada canal digital):\n" +
            "  Dn, ch_id, ph, ccbm, y  (y = estado normal: 0 o 1)\n" +
            "Línea lf:  frecuencia nominal (50 / 60)\n" +
            "Línea nrates: número de secciones de muestreo\n" +
            "Líneas samp,endsamp: tasa [Sa/s] y última muestra de esa sección\n" +
            "Línea start: dd/mm/yyyy,hh:mm:ss.ssssss\n" +
            "Línea trigger: dd/mm/yyyy,hh:mm:ss.ssssss\n" +
            "Línea filetype: ASCII / BINARY / BINARY32\n" +
            "Línea timemult: multiplicador de timestamp (1.0 = microsegundos)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "3. ESTRUCTURA DEL ARCHIVO .DAT ASCII\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Una línea por muestra:\n" +
            "   n , t , ch1 , ch2 , ... , chN\n\n" +
            "   n:   número de muestra (1-based)\n" +
            "   t:   timestamp en µs desde el inicio (× timemult)\n" +
            "   ch1..chN: valores enteros raw (INT16 en BINARY)\n\n" +
            "Conversión a valor físico:\n" +
            "   valor = a * raw + b\n\n" +
            "Ejemplo para canal de tensión con a=0.5737, b=0.0, raw=23135:\n" +
            "   V = 0.5737 * 23135 = 13271 V = 13.27 kV (fase-tierra)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "4. ESTRUCTURA DEL ARCHIVO .DAT BINARY\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Por muestra (little-endian):\n" +
            "   4 bytes: número de muestra (UINT32)\n" +
            "   4 bytes: timestamp (UINT32, µs × timemult)\n" +
            "   N * 2 bytes: valor INT16 de cada canal analógico\n" +
            "   ceil(D/16) * 2 bytes: estados digitales (UINT16 words)\n\n" +
            "BINARY32:\n" +
            "   Igual pero los canales analógicos son 4 bytes (INT32).\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "5. TASAS DE MUESTREO TÍPICAS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  64 Sa/ciclo  → 3200 Sa/s @ 50Hz  (mínimo para H25 con margen)\n" +
            "  128 Sa/ciclo → 6400 Sa/s @ 50Hz  (captura H50)\n" +
            "  256 Sa/ciclo → 12800 Sa/s @ 50Hz (transitorio de alta frecuencia)\n\n" +
            "La tasa de muestreo del visor se lee directamente del .cfg.\n" +
            "La frecuencia efectiva se calcula desde los timestamps:\n" +
            "   Fs_eff = (N-1) / (t_last - t_first) [µs -> s]\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "6. HERRAMIENTAS EXTERNAS COMPATIBLES\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Los archivos .cfg/.dat generados por HADES pueden abrirse con:\n" +
            "  • WaveWin 4 (ABB)              — visor de registros de perturbaciones\n" +
            "  • PSCAD / EMTP-ATP            — simuladores de transitorios\n" +
            "  • PowerFactory (DIgSILENT)    — análisis de sistemas\n" +
            "  • MATLAB Signal Processing TB  — análisis personalizado\n" +
            "  • Python: comtrade (PyPI)      — pip install comtrade\n" +
            "  • OpenDSS                      — simulación de distribución\n" +
            "  • ATPDraw                      — preprocesador ATP\n\n" +
            "Ref: IEEE Std C37.111-1999; IEC 60255-24:2013; IEEE 1159.3-2019."
        },

        {"📼 Registros COMTRADE — Generación",
            "GENERADOR DE REGISTROS COMTRADE\n" +
            "Módulo: ComtradeTriggerEngine + ComtradeWriter + WaveformSynthesizer\n" +
            "Normativas de trigger: IEC 61000-3-6, IEEE 519-2022, EN 50160, IEC 61000-4-30\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "1. DESCRIPCIÓN GENERAL\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "HADES genera registros COMTRADE automáticamente cuando las\n" +
            "mediciones de un feeder superan los límites normativos.\n\n" +
            "Cada evento genera tres archivos en la carpeta 'records/':\n" +
            "  feederId_CAUSA_YYYYMMDD_HHmmss.cfg      — configuración COMTRADE\n" +
            "  feederId_CAUSA_YYYYMMDD_HHmmss.dat      — datos de muestras ASCII\n" +
            "  feederId_CAUSA_YYYYMMDD_HHmmss_report.txt — reporte normativo\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "2. TABLA DE TRIGGERS NORMATIVOS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "TENSIÓN — IEC 61000-3-6 / EN 50160:\n" +
            "  THD_V > 5.0%   → WARNING    (preventivo, ~80% del planning level)\n" +
            "  THD_V > 6.5%   → PQ_RISK    (supera planning level IEC 61000-3-6 MV)\n" +
            "  THD_V > 8.0%   → CRITICAL   (supera límite absoluto EN 50160)\n" +
            "  Armónico Vn > planning level individual (tabla IEC 61000-3-6 Tabla 1)\n\n" +
            "CORRIENTE — IEEE 519-2022 Tabla 2 (sistemas 1kV-69kV, Isc/IL=20-50):\n" +
            "  THD_I > 5.0%   → WARNING    (preventivo)\n" +
            "  THD_I > 8.0%   → PQ_RISK    (límite TDD para Isc/IL=20-50, MT 23kV)\n" +
            "  THD_I > 12.0%  → CRITICAL   (1.5 × TDD limit)\n" +
            "  Armónico In > límite individual:\n" +
            "    H<11: 4.0%,  H11-16: 2.0%,  H17-22: 1.5%,  H23-34: 0.6%,  >34: 0.3%\n" +
            "  NOTA: para alimentadores 23kV se aplica la Tabla 2, no la Tabla 1 (BT).\n" +
            "  Los límites individuales por rango son los mismos para Isc/IL=20-50.\n\n" +
            "FACTOR DE POTENCIA:\n" +
            "  FP < 0.85      → WARNING    (acercándose al mínimo recomendado)\n" +
            "  FP < 0.75      → PQ_RISK    (muy bajo para suministro en MT)\n\n" +
            "DESBALANCE DE TENSIÓN — EN 50160 / IEC 61000-4-30:\n" +
            "  Vunbal > 2.0%  → PQ_RISK    (supera límite EN 50160)\n" +
            "  Vunbal > 3.0%  → CRITICAL\n\n" +
            "DETECCIÓN DE CARGA (cambio de tipo detectado):\n" +
            "  CRYPTO_MINING / DATA_CENTER / INDUSTRIAL → DETECTION\n\n" +
            "ALARMA DE AlarmEngine:\n" +
            "  Nivel CRITICAL o DETECTION → registro forzado inmediato\n\n" +
            "MANUAL:\n" +
            "  Botón '⚡ Disparo manual' en la pestaña 📼 REGISTROS → INFO\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "3. PARÁMETROS DEL REGISTRO GENERADO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Formato:      IEEE C37.111-1999 ASCII\n" +
            "Canales:      6 analógicos  (VA, VB, VC, IA, IB, IC)\n" +
            "Unidades:     V (tensión fase-tierra)  |  A (corriente de fase)\n" +
            "Duración:     8 ciclos = 160 ms @ 50 Hz\n" +
            "              → 2 ciclos pre-trigger + 6 ciclos post-trigger\n" +
            "Fs:           64 Sa/ciclo = 3200 Sa/s @ 50 Hz  (cumple IEC 61000-4-7)\n" +
            "Resolución:   INT16 (16 bits) → rango dinámico ~96 dB\n" +
            "Escalado:     valor_fisico = a × raw  (b = 0)\n" +
            "              a_V = Vpico / 32767    [V/raw]\n" +
            "              a_I = Ipico / 32767    [A/raw]\n" +
            "Timestamps:   microsegundos desde el inicio (timemult = 1.0)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "4. SÍNTESIS DE FORMA DE ONDA (WaveformSynthesizer)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Los datos de medición IEC 61850 son valores RMS + espectro armónico.\n" +
            "Para generar el COMTRADE se sintetiza la forma de onda:\n\n" +
            "   xₙ(t) = Σ √2·Xₕ·sin(h·ωt + h·φₙ)   (h = 1..25)\n\n" +
            "   n: fase (A=0°, B=−120°, C=+120°)\n" +
            "   h: orden armónico (1..25)\n" +
            "   Xₕ: amplitud RMS del armónico h (del espectro MHAI del IED)\n" +
            "   ω = 2π·f₀   (50 Hz nominal)\n\n" +
            "NOTA: la multiplicación h*phi_n produce automáticamente las\n" +
            "secuencias correctas sin necesidad de tabla:\n" +
            "  H5 (h=5): 5*(-120°) = -600° = +120° → secuencia NEGATIVA ✓\n" +
            "  H7 (h=7): 7*(-120°) = -840° = -120° → secuencia POSITIVA ✓\n" +
            "  H3 (h=3): 3*(-120°) = -360° =   0° → secuencia CERO     ✓\n\n" +
            "Si el IED no provee espectro MHAI, se usa síntesis analítica\n" +
            "con patrón de rectificador 6-pulsos calibrado al THDi medido.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "5. REPORTE DE TEXTO (report.txt)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Cada registro genera un reporte que incluye:\n" +
            "  • Fecha, hora, feeder, nivel y causa del trigger\n" +
            "  • Medición completa en el instante del evento:\n" +
            "    V, I, P, Q, S, FP, f, THD_V, THD_I, tipo de carga, K-factor\n" +
            "  • Espectro armónico de corriente L1 (H1 a H25 en A y %H1)\n" +
            "    con indicación '← SUPERA IEEE 519' por armónico individual\n" +
            "  • Espectro armónico de tensión L1 con límites IEC 61000-3-6\n" +
            "  • Normas de referencia usadas en el trigger\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "6. COOLDOWN Y GESTIÓN DE REGISTROS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Para evitar generación excesiva de archivos:\n" +
            "  • Cooldown: 30 segundos mínimo entre registros del mismo tipo\n" +
            "    y mismo feeder. Clave de cooldown = feederId + '_' + causa.\n" +
            "  • El disparo manual no tiene cooldown.\n" +
            "  • Las alarmas CRITICAL/DETECTION siempre disparan (sin cooldown).\n" +
            "  • Máximo 500 entradas en el historial en memoria.\n\n" +
            "Almacenamiento en disco:\n" +
            "  Carpeta: records/ (relativa al directorio de ejecución)\n" +
            "  Los archivos NO se eliminan automáticamente.\n" +
            "  Para liberar espacio, usar el explorador de archivos.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "7. INTERFAZ — PESTAÑA 📼 REGISTROS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "TABLA DE REGISTROS:\n" +
            "  Columnas: Fecha/Hora, Feeder, Nivel, Causa, Descripción, Archivo .cfg\n" +
            "  Colores por nivel:\n" +
            "    Rojo      CRÍTICO    — violación severa\n" +
            "    Naranja   PQ-RIESGO  — supera planning level\n" +
            "    Violeta   DETECCIÓN  — carga identificada\n" +
            "    Ámbar     WARNING    — advertencia preventiva\n\n" +
            "BOTONES DE ACCIÓN:\n" +
            "  ⚡ Disparo manual  — graba el estado actual del feeder seleccionado\n" +
            "  📂 Abrir en visor  — abre el .cfg en la pestaña COMTRADE (doble clic)\n" +
            "  📄 Ver reporte     — abre el _report.txt en el editor del sistema\n" +
            "  🗂 Carpeta        — abre la carpeta records/ en el explorador\n" +
            "  ↻ Refrescar       — recarga la lista desde el motor\n" +
            "  ✕ Limpiar lista   — borra la vista en pantalla (NO borra archivos)\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "8. VALORES LÍMITE DE REFERENCIA (IEC 61000-3-6 Tabla 1, red MT 1-36kV)\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  Armónico  Límite THDv    |  Armónico  Límite Vn/V1\n" +
            "  THD total  6.5%          |  H3         4.0%\n" +
            "                           |  H5         5.0%\n" +
            "IEEE 519-2022 TDD limits   |  H7         4.0%\n" +
            "(Isc/IL=20-50, 1kV-69kV): |  H9         1.2%\n" +
            "  H < 11     4.0%          |  H11        3.0%\n" +
            "  H 11-16    2.0%          |  H13        2.5%\n" +
            "  H 17-22    1.5%          |  H17        1.6%\n" +
            "  H 23-34    0.6%          |  H19        1.2%\n" +
            "  H > 34     0.3%          |  H23-H25    1.2%/0.8%\n" +
            "  TDD total  8.0%          |"
        },

        {"📖 Sobre HADES",
            "HADES v1.0\n" +
            "Harmonic Analysis for Detection of Electronic Signatures\n\n" +
            "Monitoreo en tiempo real de feeders de 23 kV mediante análisis de firmas\n" +
            "de energía y comunicaciones IEC 61850 para detección de cargas electrointensivas.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "PROPÓSITO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Detectar y cuantificar la presencia de cargas electrointensivas no lineales\n" +
            "en redes de distribución MT 23 kV (ANDE — Administración Nacional\n" +
            "de Electricidad, Paraguay), evaluando el impacto en la calidad de energía\n" +
            "y generando registros normativos de perturbaciones.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "EQUIPO\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  Emilio Medina      — Arquitectura, backend IEC 61850, análisis armónico\n" +
            "  Diego Rojas        — Interfaz gráfica y visualización de datos\n" +
            "  Enrique Paiva      — Integración de normas y validación de modelos\n" +
            "  Sergio Domínguez   — Gestión de Proyecto\n\n" +
            "  País: Paraguay  |  Distribución MT 23 kV  |  2026\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "CARACTERÍSTICAS PRINCIPALES\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  • Monitoreo en tiempo real vía IEC 61850 MMS (ION 7400 y otros IEDs)\n" +
            "  • Análisis armónico H1-H50 con espectro completo por fase\n" +
            "  • Detección automática de 6 tipos de carga electrointensiva\n" +
            "  • Motor de alarmas de 4 niveles (WARNING/PQ_RISK/CRITICAL/DETECTION)\n" +
            "  • Análisis de resonancia LC por feeder\n" +
            "  • Evaluación de cumplimiento normativo (IEC 61000, IEEE 519, EN 50160)\n" +
            "  • Generación automática de registros COMTRADE por trigger normativo\n" +
            "  • Visor COMTRADE con FFT, fasores, Fortescue y análisis de potencia\n" +
            "  • 6 perfiles de simulación de firmas energéticas sin IED real\n" +
            "  • Almacenamiento SQLite + exportación CSV\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "TECNOLOGÍAS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "  Java 17+ / JavaFX 17 / iec61850bean 1.9 / Maven 3.6+\n" +
            "  SQLite (JDBC) / SLF4J 2.0 / IEEE C37.111-1999 (COMTRADE)\n"
        }
    };

    public HelpPanel() {
        root = buildUI();
    }

    public Node getNode() { return root; }

    private BorderPane buildUI() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + Theme.BG + ";");

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: " + Theme.BG + "; -fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 0 1 0;");
        Label title = new Label("📖 AYUDA Y DOCUMENTACIÓN TÉCNICA");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Theme.TEXT + ";");
        Label subtitle = new Label("— Fundamentos revisados contra IEEE 519-2022, IEC 61000, IEEE 1459-2010");
        subtitle.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.TEXT + ";");
        header.getChildren().addAll(title, subtitle);
        pane.setTop(header);

        // Topics list (left)
        ListView<String> topicList = new ListView<>();
        topicList.setPrefWidth(270);
        topicList.setStyle(
            "-fx-background-color: " + Theme.BG + ";" +
            "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 0 1 0 0;" +
            "-fx-font-size: 12px;");

        for (String[] t : TOPICS) topicList.getItems().add(t[0]);

        topicList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                boolean isTheory   = item.startsWith("📐") || item.startsWith("🔋")
                    || item.startsWith("🖥")  || item.startsWith("🔥")
                    || item.startsWith("⚙")  || item.startsWith("🏭");
                boolean isComtrade   = item.startsWith("📁") || item.startsWith("📼");
                boolean isDisclaimer = item.startsWith("⚖");
                if (isSelected()) {
                    setStyle("-fx-background-color: #2E5090; -fx-text-fill: #FFFFFF;");
                } else if (isDisclaimer) {
                    setStyle("-fx-background-color: #FFF8E0; -fx-text-fill: #7A4000;");
                } else if (isComtrade) {
                    setStyle("-fx-background-color: #E8F5EC; -fx-text-fill: #107C10;");
                } else if (isTheory) {
                    setStyle("-fx-background-color: #E5F0FA; -fx-text-fill: #0078D4;");
                } else {
                    setStyle("-fx-background-color: transparent; -fx-text-fill: " + Theme.TEXT + ";");
                }
            }
        });

        // Content area (right) — WebView for rich HTML rendering
        WebView contentArea = new WebView();

        topicList.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            int idx = n.intValue();
            if (idx >= 0 && idx < TOPICS.length) {
                contentArea.getEngine().loadContent(toHtml(TOPICS[idx][1]));
                topicList.refresh();
            }
        });

        topicList.getSelectionModel().select(0);
        contentArea.getEngine().loadContent(toHtml(TOPICS[0][1]));

        SplitPane split = new SplitPane(topicList, contentArea);
        split.setDividerPositions(0.26);
        split.setStyle("-fx-background-color: " + Theme.BG + ";");
        pane.setCenter(split);
        return pane;
    }

    // ── HTML rendering helpers ────────────────────────────────────────────────

    private static String toHtml(String raw) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>");
        sb.append("body{font-family:'Segoe UI',system-ui,-apple-system,Arial,sans-serif;");
        sb.append("font-size:13px;color:#1E1E1E;padding:16px 24px 32px;line-height:1.7;");
        sb.append("background:#FFFFFF;margin:0;}");
        sb.append(".f{font-size:15px;font-weight:bold;color:#003A8C;");
        sb.append("font-family:'Cambria Math','Cambria','Georgia',serif;");
        sb.append("background:#EBF3FF;border-left:4px solid #0078D4;");
        sb.append("padding:8px 16px;margin:6px 0 6px 8px;display:block;border-radius:0 4px 4px 0;}");
        sb.append(".h1{font-size:15px;font-weight:bold;color:#003A8C;");
        sb.append("border-bottom:2px solid #0078D4;padding-bottom:4px;margin:22px 0 8px;}");
        sb.append(".h2{font-size:13px;font-weight:bold;color:#333;margin:14px 0 4px;}");
        sb.append("hr{border:none;border-top:1px solid #D0D8E8;margin:10px 0;}");
        sb.append(".w{background:#FFF7D6;border-left:3px solid #F5A623;");
        sb.append("padding:5px 12px;margin:5px 0;border-radius:0 4px 4px 0;}");
        sb.append("p{margin:2px 0;}");
        sb.append("</style></head><body>");
        for (String line : raw.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("━") || t.startsWith("─────")) {
                sb.append("<hr>");
            } else if (t.isEmpty()) {
                sb.append("<p style='margin:4px 0'>&nbsp;</p>");
            } else if (isFormula(t)) {
                sb.append("<div class='f'>").append(escHtml(t)).append("</div>");
            } else if (isSectionHdr(t)) {
                sb.append("<div class='h1'>").append(escHtml(t)).append("</div>");
            } else if (isSubHdr(t)) {
                sb.append("<div class='h2'>").append(escHtml(t)).append("</div>");
            } else {
                int lead = line.length() - line.stripLeading().length();
                if (t.startsWith("⚠")) {
                    sb.append("<div class='w'>").append(escHtml(t)).append("</div>");
                } else {
                    String ml = lead > 4 ? " style='margin-left:" + Math.min(lead * 5, 60) + "px'" : "";
                    sb.append("<p").append(ml).append(">").append(escHtml(t)).append("</p>");
                }
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean isFormula(String t) {
        if (!t.contains("=") || t.length() < 5) return false;
        boolean hasMath = t.contains("√") || t.contains("²") || t.contains("³") ||
            t.contains("×") || t.contains("·") || t.contains("ω") ||
            t.contains("φ") || t.contains("π") || t.contains("Σ") ||
            t.matches(".*[₀₁₂₃₄₅₆₇₈₉ₐᵢₗₙ].*");
        return hasMath && t.matches("[A-Za-zÀ-ÿ₀₁₂₃₄₅₆₇₈₉ᵢₐ_\\(][^=]*=.*");
    }

    private static boolean isSectionHdr(String t) {
        if (t.length() < 4 || t.contains("=") || t.startsWith("•")) return false;
        long up = t.chars().filter(Character::isUpperCase).count();
        long lt = t.chars().filter(Character::isLetter).count();
        return lt > 4 && (double) up / lt > 0.65;
    }

    private static boolean isSubHdr(String t) {
        return t.matches("[0-9]+\\.\\s.*") || t.matches("[A-Z]\\.\\s.*[A-Z].*");
    }
}
