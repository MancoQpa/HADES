package com.harmonicmonitor.comtrade;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Escribe registros COMTRADE en formato IEEE C37.111-1999 ASCII.
 *
 * Estructura generada:
 *   records/ feederId_CAUSA_YYYYMMDD_HHmmss.cfg
 *   records/ feederId_CAUSA_YYYYMMDD_HHmmss.dat
 *   records/ feederId_CAUSA_YYYYMMDD_HHmmss_report.txt
 *
 * Canales analógicos (6):
 *   1=VA, 2=VB, 3=VC  (unidades: V)
 *   4=IA, 5=IB, 6=IC  (unidades: A)
 *
 * Canales digitales: 0
 *
 * Tasa de muestreo: 64 muestras/ciclo
 *   → 3200 Sa/s a 50 Hz
 *
 * Duración por defecto: PRE_CYCLES + POST_CYCLES ciclos (8 ciclos = 160 ms a 50 Hz)
 *
 * Escalado lineal: valor_fisico = a_mult × raw  (b_offset = 0)
 *   raw es entero INT16 en rango [-32768, 32767]
 */
public class ComtradeWriter {

    /** Ciclos antes del instante de disparo (pre-fault) */
    public static final int PRE_CYCLES  = 2;
    /** Ciclos después del disparo (post-fault) */
    public static final int POST_CYCLES = 6;
    /** Total de ciclos del registro */
    public static final int TOTAL_CYCLES = PRE_CYCLES + POST_CYCLES;

    private static final DateTimeFormatter FMT_FILE    =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter FMT_COMTRADE =
        DateTimeFormatter.ofPattern("dd/MM/yyyy,HH:mm:ss.SSSSSS");

    /**
     * Genera el par CFG + DAT para la medición dada.
     *
     * @param m         medición en el instante del evento
     * @param cfg       configuración del feeder (nombre, nominal V/I, frecuencia)
     * @param cause     etiqueta de la causa (p.ej. "THDi_CRIT", "DETECT_CRYPTO")
     * @param dir       directorio de salida (se crea si no existe)
     * @return          el archivo .cfg generado
     */
    public static File write(FeederMeasurement m, FeederConfig cfg,
                             String cause, File dir) throws IOException {
        dir.mkdirs();

        double freq    = (m.getFrequency() > 10) ? m.getFrequency() : 50.0;
        double sRate   = freq * WaveformSynthesizer.SAMPLES_PER_CYCLE;   // Sa/s
        int    nSamp   = WaveformSynthesizer.SAMPLES_PER_CYCLE * TOTAL_CYCLES;
        double dtUs    = 1_000_000.0 / sRate;                            // µs entre muestras

        // Síntesis de forma de onda
        double[][] wave = WaveformSynthesizer.synthesize(m, TOTAL_CYCLES);

        // Pico por grupo de canales → escalado
        double vPeak = 0, iPeak = 0;
        for (int ch = 0; ch < 3; ch++) vPeak = Math.max(vPeak, WaveformSynthesizer.peakAmplitude(wave, ch));
        for (int ch = 3; ch < 6; ch++) iPeak = Math.max(iPeak, WaveformSynthesizer.peakAmplitude(wave, ch));

        // Garantizar escala mínima razonable aunque la medición sea cero
        double vNomPeak = cfg.getNominalVoltageKv() * 1000.0 / Math.sqrt(3.0) * Math.sqrt(2.0);
        double iNomPeak = cfg.getNominalCurrentA() * Math.sqrt(2.0);
        if (vPeak < vNomPeak * 0.01) vPeak = vNomPeak * 1.2;
        if (iPeak < iNomPeak * 0.01) iPeak = iNomPeak * 1.5;

        // Multiplicadores lineal: physical = a * raw
        double aV = vPeak / 32767.0;
        double aI = iPeak / 32767.0;

        // Nombres de archivo
        LocalDateTime ldt = LocalDateTime.ofInstant(m.getTimestamp(), ZoneId.systemDefault());
        String base   = sanitize(m.getFeederId()) + "_" + sanitize(cause) + "_" + ldt.format(FMT_FILE);
        File cfgFile  = new File(dir, base + ".cfg");
        File datFile  = new File(dir, base + ".dat");

        // Inicio del registro = timestamp – PRE_CYCLES ciclos
        long preNanos  = (long)(PRE_CYCLES * 1_000_000_000.0 / freq);
        LocalDateTime startDt   = ldt.minusNanos(preNanos);
        // Instante de disparo = inicio + PRE_CYCLES ciclos = ldt
        LocalDateTime triggerDt = ldt;

        writeCfg(cfgFile, m, cfg, nSamp, sRate, aV, aI, freq,
                 startDt.format(FMT_COMTRADE), triggerDt.format(FMT_COMTRADE));
        writeDat(datFile, wave, nSamp, dtUs, aV, aI);

        return cfgFile;
    }

    // ── Escritura del archivo .cfg ────────────────────────────────────────────

    private static void writeCfg(File cfgFile, FeederMeasurement m, FeederConfig cfg,
                                  int nSamp, double sRate, double aV, double aI,
                                  double freq, String startStr, String trigStr) throws IOException {

        double vLN   = cfg.getNominalVoltageKv() * 1000.0 / Math.sqrt(3.0);
        double iNom  = cfg.getNominalCurrentA();

        // Rango entero INT16 cubriendo ±1.5× nominal
        int minRawV  = clip16(-Math.round(vLN * Math.sqrt(2) * 1.5 / aV));
        int maxRawV  = clip16( Math.round(vLN * Math.sqrt(2) * 1.5 / aV));
        int minRawI  = clip16(-Math.round(iNom * Math.sqrt(2) * 1.5 / aI));
        int maxRawI  = clip16( Math.round(iNom * Math.sqrt(2) * 1.5 / aI));

        // Usar Locale.US para todos los números: el separador decimal DEBE ser punto.
        // En Windows con locale español, printf/format usa coma → rompe el parser CSV.
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(cfgFile), StandardCharsets.ISO_8859_1))) {

            // Línea 1: station_name , rec_dev_id , rev_year
            pw.printf(Locale.US, "%s,%s,1999%n",
                "HarmonicMonitor-" + sanitize(cfg.getFeederName()),
                sanitize(m.getFeederId()));

            // Línea 2: TT , ##A , ##D
            pw.println("6,6A,0D");

            // Canales de tensión (1–3)
            // Formato: An,ch_id,ph,ccbm,uu,a,b,skew,min,max,primary,secondary,PS
            String[] vNames  = { "VA", "VB", "VC" };
            String[] phases  = { "A",  "B",  "C"  };
            for (int i = 0; i < 3; i++) {
                pw.printf(Locale.US, "%d,%s,%s,,V,%.8f,0.0,0,%d,%d,%.2f,1.0,P%n",
                    i + 1, vNames[i], phases[i], aV, minRawV, maxRawV, vLN);
            }

            // Canales de corriente (4–6)
            String[] iNames = { "IA", "IB", "IC" };
            for (int i = 0; i < 3; i++) {
                pw.printf(Locale.US, "%d,%s,%s,,A,%.8f,0.0,0,%d,%d,%.2f,1.0,P%n",
                    i + 4, iNames[i], phases[i], aI, minRawI, maxRawI, iNom);
            }

            // Frecuencia nominal
            pw.printf(Locale.US, "%.0f%n", freq);

            // Número de secciones de muestreo
            pw.println("1");

            // Tasa de muestreo , última muestra de esta sección
            pw.printf(Locale.US, "%.0f,%d%n", sRate, nSamp);

            // Fechas inicio y disparo
            pw.println(startStr);
            pw.println(trigStr);

            // Formato de datos
            pw.println("ASCII");

            // Multiplicador de tiempo (1.0 = microsegundos reales)
            pw.println("1.0");
        }
    }

    // ── Escritura del archivo .dat ────────────────────────────────────────────

    private static void writeDat(File datFile, double[][] wave, int nSamp,
                                  double dtUs, double aV, double aI) throws IOException {

        double[] mult = { aV, aV, aV, aI, aI, aI };

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(datFile), StandardCharsets.ISO_8859_1))) {

            for (int s = 0; s < nSamp; s++) {
                // Timestamp en microsegundos (entero redondeado)
                long ts = Math.round(s * dtUs);

                StringBuilder sb = new StringBuilder(64);
                sb.append(s + 1).append(',').append(ts);

                for (int ch = 0; ch < 6; ch++) {
                    int raw = clip16(Math.round(wave[ch][s] / mult[ch]));
                    sb.append(',').append(raw);
                }
                pw.println(sb);
            }
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /** Reemplaza caracteres no permitidos en nombres de archivo */
    static String sanitize(String s) {
        if (s == null) return "X";
        return s.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    /** Limita a rango INT16 */
    private static int clip16(long v) {
        return (int) Math.max(-32768, Math.min(32767, v));
    }

    private static int clip16(double v) {
        return clip16(Math.round(v));
    }
}
