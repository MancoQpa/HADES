package com.harmonicmonitor.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Evento de alarma generado por el motor de alarmas.
 */
public class AlarmEvent {

    public enum Level {
        WARNING     ("Advertencia",  "#FF9800", 1),
        PQ_RISK     ("Riesgo CC",    "#FF5722", 2),
        CRITICAL    ("Crítico",      "#F44336", 3),
        DETECTION   ("Detección",    "#9C27B0", 4);

        private final String label;
        private final String colorHex;
        private final int    priority;

        Level(String label, String colorHex, int priority) {
            this.label    = label;
            this.colorHex = colorHex;
            this.priority = priority;
        }
        public String getLabel()    { return label; }
        public String getColorHex() { return colorHex; }
        public int    getPriority() { return priority; }
    }

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Level   level;
    private final String  feederId;
    private final String  parameter;   // e.g. "THDi_L1", "CV_I", "ResonanceFreq"
    private final String  message;
    private final double  measuredValue;
    private final double  threshold;
    private final Instant timestamp;
    private boolean       acknowledged = false;
    private Instant       acknowledgedAt;

    public AlarmEvent(Level level, String feederId, String parameter,
                      String message, double measuredValue, double threshold) {
        this.level         = level;
        this.feederId      = feederId;
        this.parameter     = parameter;
        this.message       = message;
        this.measuredValue = measuredValue;
        this.threshold     = threshold;
        this.timestamp     = Instant.now();
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Level   getLevel()          { return level; }
    public String  getFeederId()       { return feederId; }
    public String  getParameter()      { return parameter; }
    public String  getMessage()        { return message; }
    public double  getMeasuredValue()  { return measuredValue; }
    public double  getThreshold()      { return threshold; }
    public Instant getTimestamp()      { return timestamp; }
    public boolean isAcknowledged()    { return acknowledged; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }

    public String getFormattedTimestamp() {
        return FMT.format(timestamp);
    }

    public void acknowledge() {
        this.acknowledged   = true;
        this.acknowledgedAt = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s | %s | %.3f (umbral %.3f)",
            level.getLabel(), FMT.format(timestamp), feederId, message,
            measuredValue, threshold);
    }
}
