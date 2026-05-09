package com.harmonicmonitor.gui;

import com.harmonicmonitor.model.AlarmEvent;

/**
 * Immutable data model for one row in the alarm tables of {@link AlarmsPanel}.
 *
 * Previously a {@code public static} inner class of {@link AlarmsPanel};
 * extracted to its own file (refactor F14-001).
 */
public class AlarmRow {

    private final String timestamp;
    private final String level;
    private final String feederId;
    private final String parameter;
    private final String message;
    private final String measuredValue;
    private final String threshold;
    private final String ack;

    public AlarmRow(AlarmEvent ev) {
        this.timestamp     = ev.getFormattedTimestamp();
        this.level         = ev.getLevel().name();
        this.feederId      = ev.getFeederId();
        this.parameter     = ev.getParameter();
        this.message       = ev.getMessage();
        this.measuredValue = String.format("%.3f", ev.getMeasuredValue());
        this.threshold     = String.format("%.3f", ev.getThreshold());
        this.ack           = ev.isAcknowledged() ? "\u2713" : "\u2014";
    }

    public String getTimestamp()     { return timestamp; }
    public String getLevel()         { return level; }
    public String getFeederId()      { return feederId; }
    public String getParameter()     { return parameter; }
    public String getMessage()       { return message; }
    public String getMeasuredValue() { return measuredValue; }
    public String getThreshold()     { return threshold; }
    public String getAck()           { return ack; }
}
