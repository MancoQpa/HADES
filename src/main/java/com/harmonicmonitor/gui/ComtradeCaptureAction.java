package com.harmonicmonitor.gui;

import com.harmonicmonitor.AppExecutors;
import com.harmonicmonitor.HarmonicMonitorApp;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

import javafx.application.Platform;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Executes a manual COMTRADE capture: triggers all active feeders, waits for the
 * writer to finish, then finds the newest .cfg file and passes it to the panel.
 *
 * Extracted from ComtradePanel.captureNow() / findNewestCfgFile() (refactor F24-001).
 */
final class ComtradeCaptureAction {

    private final HarmonicMonitorApp app;
    private final Consumer<String>   status;
    private final Consumer<File>     onLoaded;

    /**
     * @param app      application reference for feeder/trigger access
     * @param status   callback to update the panel status label
     * @param onLoaded callback to load the captured .cfg file in the panel
     */
    ComtradeCaptureAction(HarmonicMonitorApp app,
                          Consumer<String> status,
                          Consumer<File>   onLoaded) {
        this.app      = app;
        this.status   = status;
        this.onLoaded = onLoaded;
    }

    /** Triggers the capture and schedules auto-load of the newest .cfg file. */
    void execute() {
        List<FeederConfig> configs = app.getFeederConfigs();
        if (configs.isEmpty()) {
            status.accept("Sin feeders activos \u2014 no se puede capturar");
            return;
        }
        status.accept("Disparando captura COMTRADE...");

        int triggered = 0;
        for (FeederConfig cfg : configs) {
            FeederMeasurement m = app.getLatestMeasurements().get(cfg.getFeederId());
            if (m != null) {
                app.getComtradeTrigger().triggerManual(m, cfg);
                triggered++;
            }
        }
        if (triggered == 0) {
            status.accept("Sin mediciones disponibles \u2014 conecte un feeder primero");
            return;
        }

        final int count = triggered;
        // Wait for the writer to finish (~800 ms), then load the newest .cfg
        AppExecutors.ioPool().execute(() -> {
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            File newestCfg = findNewestCfgFile(new File("records"));
            Platform.runLater(() -> {
                if (newestCfg != null) {
                    onLoaded.accept(newestCfg);
                    status.accept("Captura completada (" + count
                        + " feeder(s)) \u2014 " + newestCfg.getName());
                } else {
                    status.accept("Captura disparada pero no se encontr\u00F3"
                        + " archivo .cfg en records/");
                }
            });
        });
    }

    /**
     * Returns the most-recently-modified .cfg file under {@code dir}
     * (searches one level of subdirectories if none found at root).
     */
    static File findNewestCfgFile(File dir) {
        if (dir == null || !dir.exists()) return null;
        File[] cfgFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".cfg"));
        if (cfgFiles == null || cfgFiles.length == 0) {
            File[] subdirs = dir.listFiles(File::isDirectory);
            if (subdirs == null) return null;
            File newest = null;
            for (File sub : subdirs) {
                File candidate = findNewestCfgFile(sub);
                if (candidate != null
                        && (newest == null || candidate.lastModified() > newest.lastModified())) {
                    newest = candidate;
                }
            }
            return newest;
        }
        Arrays.sort(cfgFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return cfgFiles[0];
    }
}
