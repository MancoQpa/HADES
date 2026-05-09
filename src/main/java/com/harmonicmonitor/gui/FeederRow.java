package com.harmonicmonitor.gui;

import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;

/**
 * Mutable data model for one row in the multi-feeder SCADA table of
 * {@link MultiFeederMonitorPanel}.
 *
 * Previously a {@code public static} inner class of {@link MultiFeederMonitorPanel};
 * extracted to its own file (refactor F19-001).
 */
public class FeederRow {

    private int index;
    private final FeederConfig feeder;
    private String feederName;
    /** LED de conexión: CONNECTED | CONNECTING | ERROR | DISCONNECTED | SIM */
    private String connLed;
    private String status;
    private String voltageKv;
    private String currentA;
    private String activePower;
    private String reactivePower;
    private String powerFactor;
    private String thdi;
    private String thdv;
    private String loadType;
    private int    alarmCount;

    public FeederRow(int index, FeederConfig feeder, boolean simulated) {
        this.index          = index;
        this.feeder         = feeder;
        this.feederName     = feeder.getFeederName();
        this.connLed        = simulated ? "SIM" : "CONNECTING";
        this.status         = "\u25CF ESPERANDO";
        this.voltageKv      = "\u2014";
        this.currentA       = "\u2014";
        this.activePower    = "\u2014";
        this.reactivePower  = "\u2014";
        this.powerFactor    = "\u2014";
        this.thdi           = "\u2014";
        this.thdv           = "\u2014";
        this.loadType       = "\u2014";
        this.alarmCount     = 0;
    }

    public void clear() {
        this.voltageKv     = "\u2014";
        this.currentA      = "\u2014";
        this.activePower   = "\u2014";
        this.reactivePower = "\u2014";
        this.powerFactor   = "\u2014";
        this.thdi          = "\u2014";
        this.thdv          = "\u2014";
        this.loadType      = "\u2014";
        this.status        = "\u25CF ESPERANDO";
    }

    public void update(FeederMeasurement m, int alarms) {
        this.voltageKv     = String.format("%.3f", m.getVoltageAvg() / 1000.0);
        this.currentA      = String.format("%.1f", m.getCurrentAvg());
        this.activePower   = String.format("%.0f", m.getActivePower());
        this.reactivePower = String.format("%.0f", m.getReactivePower());
        this.powerFactor   = String.format("%.3f", Math.min(1.0, Math.max(-1.0, m.getPowerFactor())));
        this.thdi          = String.format("%.1f", m.getThdCurrentAvg());
        this.thdv          = String.format("%.1f", m.getThdVoltageAvg());
        this.loadType      = m.getDetectedLoadType().getDisplayName();
        this.alarmCount    = alarms;

        double thdiv = m.getThdCurrentAvg();
        if (alarms > 0 && thdiv > 8) this.status = "\u25CF WARN";
        else if (alarms > 2)         this.status = "\u25CF WARN";
        else                         this.status = "\u25CF OK";
    }

    public FeederConfig getFeeder()       { return feeder; }
    public int    getIndex()              { return index; }
    public String getFeederId()           { return feeder.getFeederId(); }
    public String getFeederName()         { return feederName; }
    public String getConnLed()            { return connLed; }
    public void   setConnLed(String s)    { this.connLed = s; }
    public String getStatus()             { return status; }
    public String getVoltageKv()          { return voltageKv; }
    public String getCurrentA()           { return currentA; }
    public String getActivePower()        { return activePower; }
    public String getReactivePower()      { return reactivePower; }
    public String getPowerFactor()        { return powerFactor; }
    public String getThdi()               { return thdi; }
    public String getThdv()               { return thdv; }
    public String getLoadType()           { return loadType; }
    public int    getAlarmCount()         { return alarmCount; }
}
