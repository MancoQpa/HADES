package com.harmonicmonitor.comtrade;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lector de archivos COMTRADE (IEEE C37.111 / IEC 60255-24).
 *
 * Soporta:
 *   - Versiones 1991, 1999 y 2013
 *   - Formato ASCII y BINARY
 *   - Canales analógicos y digitales
 *
 * Uso:
 *   ComtradeRecord rec = ComtradeReader.load(new File("registro.cfg"));
 */
public class ComtradeReader {

    private static final Logger LOG = Logger.getLogger(ComtradeReader.class.getName());

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Carga un registro COMTRADE a partir del archivo .cfg.
     * El archivo .dat debe estar en el mismo directorio con el mismo nombre base.
     *
     * @param cfgFile  archivo .cfg (obligatorio)
     * @return         ComtradeRecord con todos los datos
     * @throws IOException si hay error de lectura o formato no reconocido
     */
    public static ComtradeRecord load(File cfgFile) throws IOException {
        if (cfgFile == null || !cfgFile.exists()) {
            throw new IOException("Archivo .cfg no encontrado: " + cfgFile);
        }
        ComtradeReader reader = new ComtradeReader();
        return reader.parse(cfgFile);
    }

    // ── Implementación interna ────────────────────────────────────────────────

    private ComtradeRecord parse(File cfgFile) throws IOException {
        ComtradeRecord rec = new ComtradeRecord();
        rec.cfgFile = cfgFile;

        // Determinar archivo .dat
        String baseName = cfgFile.getName().replaceAll("(?i)\\.cfg$", "");
        File datFile = new File(cfgFile.getParent(), baseName + ".dat");
        if (!datFile.exists()) {
            datFile = new File(cfgFile.getParent(), baseName + ".DAT");
        }

        // Parsear .cfg
        parseCfg(cfgFile, rec);

        // Parsear .dat si existe
        if (datFile.exists()) {
            if ("BINARY".equalsIgnoreCase(rec.dataFormat) || "BINARY32".equalsIgnoreCase(rec.dataFormat)) {
                parseDatBinary(datFile, rec);
            } else {
                parseDatAscii(datFile, rec);
            }
        } else {
            LOG.warning("Archivo .dat no encontrado: " + datFile.getAbsolutePath());
        }

        return rec;
    }

    // ── Parser CFG ────────────────────────────────────────────────────────────

    private void parseCfg(File cfgFile, ComtradeRecord rec) throws IOException {
        List<String> lines = readLines(cfgFile);
        if (lines.isEmpty()) throw new IOException("Archivo .cfg vacío");

        int lineIdx = 0;

        // Línea 1: station_name,rec_dev_id[,rev_year]
        String[] line1 = splitLine(lines.get(lineIdx++));
        rec.stationName = line1.length > 0 ? line1[0].trim() : "";
        rec.deviceId    = line1.length > 1 ? line1[1].trim() : "";
        rec.revisionYear = line1.length > 2 ? line1[2].trim() : "1991";

        // Línea 2: TT,##A,##D  (total channels, analog count, digital count)
        String[] line2 = splitLine(lines.get(lineIdx++));
        // line2[0] = total (puede ser vacío en formato antiguo)
        // line2[1] = "##A"  line2[2] = "##D"
        int numAnalog  = 0;
        int numDigital = 0;
        for (String part : line2) {
            String p = part.trim().toUpperCase();
            if (p.endsWith("A")) {
                try { numAnalog  = Integer.parseInt(p.replace("A", "")); } catch (NumberFormatException ignored) {}
            } else if (p.endsWith("D")) {
                try { numDigital = Integer.parseInt(p.replace("D", "")); } catch (NumberFormatException ignored) {}
            }
        }
        rec.numAnalogChannels  = numAnalog;
        rec.numDigitalChannels = numDigital;
        rec.analogChannelNames = new ArrayList<>();
        rec.analogChannelUnits = new ArrayList<>();
        rec.analogMultipliers  = new double[numAnalog];
        rec.analogOffsets      = new double[numAnalog];

        // Líneas de canales analógicos: An,ch_id,ph,ccbm,uu,a,b,skew,min,max[,primary,secondary,PS]
        for (int i = 0; i < numAnalog && lineIdx < lines.size(); i++, lineIdx++) {
            String[] cf = splitLine(lines.get(lineIdx));
            rec.analogChannelNames.add(cf.length > 1 ? cf[1].trim() : "A" + (i + 1));
            rec.analogChannelUnits.add(cf.length > 4 ? cf[4].trim() : "");
            double a = 1.0, b = 0.0;
            try { if (cf.length > 5) a = Double.parseDouble(cf[5].trim()); } catch (NumberFormatException ignored) {}
            try { if (cf.length > 6) b = Double.parseDouble(cf[6].trim()); } catch (NumberFormatException ignored) {}
            rec.analogMultipliers[i] = a;
            rec.analogOffsets[i]     = b;
        }

        // Líneas de canales digitales (las omitimos en detalle pero las saltamos)
        for (int i = 0; i < numDigital && lineIdx < lines.size(); i++, lineIdx++) {
            // Ignorar definición de digitales por ahora
        }

        // Línea de frecuencia nominal
        if (lineIdx < lines.size()) {
            try { rec.nominalFrequency = Double.parseDouble(lines.get(lineIdx).trim()); }
            catch (NumberFormatException ignored) { rec.nominalFrequency = 50.0; }
            lineIdx++;
        }

        // Línea de número de secciones de muestreo: nrates
        int nrates = 1;
        if (lineIdx < lines.size()) {
            try { nrates = Integer.parseInt(lines.get(lineIdx).trim()); }
            catch (NumberFormatException ignored) {}
            lineIdx++;
        }

        // Líneas de tasas de muestreo: samp,endsamp
        rec.sampleRate = 1000.0;  // default
        for (int i = 0; i < nrates && lineIdx < lines.size(); i++, lineIdx++) {
            String[] sr = splitLine(lines.get(lineIdx));
            try {
                if (sr.length > 0) rec.sampleRate = Double.parseDouble(sr[0].trim());
                if (sr.length > 1) rec.numSamples  = Integer.parseInt(sr[1].trim());
            } catch (NumberFormatException ignored) {}
        }

        // Línea de fecha/hora de inicio: dd/mm/yyyy,hh:mm:ss.ssssss
        if (lineIdx < lines.size()) {
            rec.startTimestamp = lines.get(lineIdx).trim();
            lineIdx++;
        }
        // Línea de fecha/hora del primer trigger
        if (lineIdx < lines.size()) {
            rec.triggerTimestamp = lines.get(lineIdx).trim();
            lineIdx++;
        }

        // Línea de formato de datos: ASCII / BINARY / BINARY32
        if (lineIdx < lines.size()) {
            rec.dataFormat = lines.get(lineIdx).trim().toUpperCase();
            lineIdx++;
        } else {
            rec.dataFormat = "ASCII";
        }

        // Línea opcional: time_mult (multiplicador de tiempo)
        rec.timeMult = 1.0;
        if (lineIdx < lines.size()) {
            try { rec.timeMult = Double.parseDouble(lines.get(lineIdx).trim()); }
            catch (NumberFormatException ignored) {}
        }
    }

    // ── Parser DAT ASCII ──────────────────────────────────────────────────────

    private void parseDatAscii(File datFile, ComtradeRecord rec) throws IOException {
        List<String> lines = readLines(datFile);
        int numSamples = lines.size();
        if (rec.numSamples > 0 && rec.numSamples < numSamples) numSamples = rec.numSamples;

        rec.numSamples   = numSamples;
        rec.timestamps   = new long[numSamples];
        rec.analogData   = new double[rec.numAnalogChannels][numSamples];

        for (int s = 0; s < numSamples; s++) {
            if (s >= lines.size()) break;
            String[] parts = lines.get(s).split(",");
            // parts[0] = sample number, parts[1] = timestamp (µs)
            try {
                if (parts.length > 1) {
                    rec.timestamps[s] = (long)(Double.parseDouble(parts[1].trim()) * rec.timeMult);
                }
                for (int ch = 0; ch < rec.numAnalogChannels; ch++) {
                    int idx = ch + 2;  // skip sample# and timestamp
                    if (idx < parts.length) {
                        double raw = Double.parseDouble(parts[idx].trim());
                        rec.analogData[ch][s] = raw * rec.analogMultipliers[ch] + rec.analogOffsets[ch];
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // ── Parser DAT BINARY ─────────────────────────────────────────────────────

    private void parseDatBinary(File datFile, ComtradeRecord rec) throws IOException {
        // Estructura binaria por muestra:
        //   - 4 bytes: número de muestra (uint32)
        //   - 4 bytes: timestamp en µs (uint32)
        //   - numAnalogChannels * 2 bytes: valor INT16 de cada canal analógico
        //   - ceil(numDigitalChannels/16) * 2 bytes: estados digitales (uint16 words)
        int numDigWords = (rec.numDigitalChannels + 15) / 16;
        int bytesPerSample = 4 + 4 + rec.numAnalogChannels * 2 + numDigWords * 2;

        try (FileInputStream fis = new FileInputStream(datFile)) {
            byte[] buf = fis.readAllBytes();
            int totalSamples = buf.length / bytesPerSample;
            if (rec.numSamples > 0 && rec.numSamples < totalSamples) totalSamples = rec.numSamples;

            rec.numSamples = totalSamples;
            rec.timestamps  = new long[totalSamples];
            rec.analogData  = new double[rec.numAnalogChannels][totalSamples];

            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            for (int s = 0; s < totalSamples; s++) {
                int offset = s * bytesPerSample;
                if (offset + bytesPerSample > buf.length) break;
                bb.position(offset);
                /* sample number = */ bb.getInt();
                long ts = Integer.toUnsignedLong(bb.getInt());
                rec.timestamps[s] = (long)(ts * rec.timeMult);
                for (int ch = 0; ch < rec.numAnalogChannels; ch++) {
                    short raw = bb.getShort();
                    rec.analogData[ch][s] = raw * rec.analogMultipliers[ch] + rec.analogOffsets[ch];
                }
                // Saltamos los words digitales
                for (int w = 0; w < numDigWords; w++) bb.getShort();
            }
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private List<String> readLines(File f) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private String[] splitLine(String line) {
        return line.split(",", -1);
    }

    // ── ComtradeRecord ────────────────────────────────────────────────────────

    /**
     * Registro COMTRADE cargado en memoria.
     */
    public static class ComtradeRecord {

        /** Archivo .cfg de origen */
        public File cfgFile;

        /** Metadatos de cabecera */
        public String stationName    = "";
        public String deviceId       = "";
        public String revisionYear   = "1991";
        public String startTimestamp = "";
        public String triggerTimestamp = "";
        public String dataFormat     = "ASCII";

        /** Canales analógicos */
        public int          numAnalogChannels  = 0;
        public int          numDigitalChannels = 0;
        public List<String> analogChannelNames = new ArrayList<>();
        public List<String> analogChannelUnits = new ArrayList<>();

        /** Escala lineal: valor_fisico = raw * a + b */
        public double[] analogMultipliers;
        public double[] analogOffsets;

        /** Parámetros de muestreo */
        public double nominalFrequency = 50.0;
        public double sampleRate       = 1000.0;  // Hz
        public int    numSamples       = 0;
        public double timeMult         = 1.0;

        /**
         * Datos analógicos: [channelIndex][sampleIndex]
         * Los valores ya tienen aplicada la escala lineal (a*raw + b).
         */
        public double[][] analogData;

        /**
         * Marcas de tiempo de cada muestra en microsegundos desde el inicio del registro.
         */
        public long[] timestamps;

        /** Duración del registro en segundos */
        public double getDurationSeconds() {
            if (timestamps == null || timestamps.length < 2) return 0.0;
            return (timestamps[timestamps.length - 1] - timestamps[0]) * 1e-6;
        }

        /** Frecuencia de muestreo efectiva (Hz) calculada desde los timestamps */
        public double getEffectiveSampleRate() {
            double dur = getDurationSeconds();
            if (dur <= 0 || numSamples < 2) return sampleRate;
            return (numSamples - 1) / dur;
        }

        @Override
        public String toString() {
            return String.format("COMTRADE[%s / %s  rev=%s  fmt=%s  ch_a=%d  ch_d=%d  fs=%.0fHz  n=%d]",
                stationName, deviceId, revisionYear, dataFormat,
                numAnalogChannels, numDigitalChannels, sampleRate, numSamples);
        }
    }
}
