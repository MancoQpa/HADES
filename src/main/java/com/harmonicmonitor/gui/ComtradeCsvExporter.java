package com.harmonicmonitor.gui;

import com.harmonicmonitor.comtrade.ComtradeReader.ComtradeRecord;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * Exports a {@link ComtradeRecord} to a CSV file chosen by the user.
 *
 * The export is synchronous and runs on the calling (JavaFX Application) thread.
 *
 * Extracted from ComtradePanel.exportCsv() (refactor F24-001).
 */
final class ComtradeCsvExporter {

    private ComtradeCsvExporter() {}

    /**
     * Shows a save-file dialog and writes the record's analog data as CSV.
     *
     * @param record the loaded COMTRADE record (may be {@code null} — handled gracefully)
     * @param stage  owner stage for the FileChooser dialog
     * @param status callback to update the panel status label
     */
    static void export(ComtradeRecord record, Stage stage, Consumer<String> status) {
        if (record == null || record.analogData == null) {
            status.accept("Sin datos para exportar");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar COMTRADE a CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        String base = record.cfgFile != null
            ? record.cfgFile.getName().replaceAll("(?i)\\.cfg$", "") : "comtrade";
        fc.setInitialFileName(base + "_export.csv");

        File out = fc.showSaveDialog(stage);
        if (out == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            // Header row
            StringBuilder hdr = new StringBuilder("timestamp_us");
            for (String name : record.analogChannelNames)
                hdr.append(",").append(name.replace(",", ";"));
            pw.println(hdr);

            // Data rows
            for (int s = 0; s < record.numSamples; s++) {
                StringBuilder row = new StringBuilder();
                row.append(record.timestamps != null && s < record.timestamps.length
                    ? record.timestamps[s] : s);
                for (int ch = 0; ch < record.numAnalogChannels; ch++) {
                    row.append(",");
                    if (ch < record.analogData.length && s < record.analogData[ch].length)
                        row.append(record.analogData[ch][s]);
                }
                pw.println(row);
            }
            status.accept("CSV exportado: " + out.getAbsolutePath());
        } catch (Exception ex) {
            status.accept("Error exportando: " + ex.getMessage());
        }
    }
}
