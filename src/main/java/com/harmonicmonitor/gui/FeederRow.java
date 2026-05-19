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
    // Per-phase voltages (kV)
    private String voltageL1Kv;
    private String voltageL2Kv;
    private String voltageL3Kv;
    // Per-phase currents (A)
    private String currentL1;
    private String currentL2;
    private String currentL3;
    // Total 3-phase powers
    private String activePower;    // kW
    private String reactivePower;  // kVAR
    private String apparentPower;  // kVA
    // Metering energies (MMTR)
    private String energyKwh;
    private String energyKvarh;
    private String energyKvah;
    // THDi and alarms
    private String thdi;
    private int    alarmCount;

    public FeederRow(int index, FeederConfig feeder, boolean simulated) {
        this.index          = index;
        this.feeder         = feeder;
        this.feederName     = feeder.getFeederName();
        this.connLed        = simulated ? "SIM" : "CONNECTING";
        this.status         = "\u25CF ESPERANDO";
        this.voltageL1Kv    = "\u2014";
        this.voltageL2Kv    = "\u2014";
        this.voltageL3Kv    = "\u2014";
        this.currentL1      = "\u2014";
        this.currentL2      = "\u2014";
        this.currentL3      = "\u2014";
        this.activePower    = "\u2014";
        this.reactivePower  = "\u2014";
        this.apparentPower  = "\u2014";
        this.energyKwh      = "\u2014";
        this.energyKvarh    = "\u2014";
        this.energyKvah     = "\u2014";
        this.thdi           = "\u2014";
        this.alarmCount     = 0;
    }

    public void clear() {
        this.voltageL1Kv   = "\u2014";
        this.voltageL2Kv   = "\u2014";
        this.voltageL3Kv   = "\u2014";
        this.currentL1     = "\u2014";
        this.currentL2     = "\u2014";
        this.currentL3     = "\u2014";
        this.activePower   = "\u2014";
        this.reactivePower = "\u2014";
        this.apparentPower = "\u2014";
        this.energyKwh     = "\u2014";
        this.energyKvarh   = "\u2014";
        this.energyKvah    = "\u2014";
        this.thdi          = "\u2014";
        this.status        = "\u25CF ESPERANDO";
    }

    public void update(FeederMeasurement m, int alarms) {
        this.voltageL1Kv   = String.format("%.3f", m.getVoltageL1() / 1000.0);
        this.voltageL2Kv   = String.format("%.3f", m.getVoltageL2() / 1000.0);
        this.voltageL3Kv   = String.format("%.3f", m.getVoltageL3() / 1000.0);
        this.currentL1     = String.format("%.1f", m.getCurrentL1());
        this.currentL2     = String.format("%.1f", m.getCurrentL2());
        this.currentL3     = String.format("%.1f", m.getCurrentL3());
        this.activePower   = String.format("%.1f", m.getActivePower());
        this.reactivePower = String.format("%.1f", m.getReactivePower());
        this.apparentPower = String.format("%.1f", m.getApparentPower());
        this.energyKwh     = String.format("%.1f", m.getTotalEnergyKWh());
        this.energyKvarh   = String.format("%.1f", m.getTotalEnergyKVArh());
        this.energyKvah    = String.format("%.1f", m.getTotalEnergyKVAh());
        this.thdi          = String.format("%.1f", m.getThdCurrentAvg());
        this.alarmCount    = alarms;

        double thdiv = m.getThdCurrentAvg();
        if (alarms > 0 && thdiv > 8) this.status = "\u25CF WARN";
        else if (alarms > 2)         this.status = "\u25CF WARN";
        else                         this.status = "\u25CF OK";
    }

    public FeederConfig getFeeder()        { return feeder; }
    public int    getIndex()               { return index; }
    public String getFeederId()            { return feeder.getFeederId(); }
    public String getFeederName()          { return feederName; }
    public String getConnLed()             { return connLed; }
    public void   setConnLed(String s)     { this.connLed = s; }
    public String getStatus()              { return status; }
    // Per-phase voltages
    public String getVoltageL1Kv()         { return voltageL1Kv; }
    public String getVoltageL2Kv()         { return voltageL2Kv; }
    public String getVoltageL3Kv()         { return voltageL3Kv; }
    // Per-phase currents
    public String getCurrentL1()           { return currentL1; }
    public String getCurrentL2()           { return currentL2; }
    public String getCurrentL3()           { return currentL3; }
    // Powers
    public String getActivePower()         { return activePower; }
    public String getReactivePower()       { return reactivePower; }
    public String getApparentPower()       { return apparentPower; }
    // Energies (MMTR)
    public String getEnergyKwh()           { return energyKwh; }
    public String getEnergyKvarh()         { return energyKvarh; }
    public String getEnergyKvah()          { return energyKvah; }
    // Quality / alarms
    public String getThdi()                { return thdi; }
    public int    getAlarmCount()          { return alarmCount; }
}
