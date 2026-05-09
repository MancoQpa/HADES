package com.harmonicmonitor.gui;

/**
 * Data model for one row in the harmonic table of {@link HarmonicsPanel}.
 *
 * Previously a {@code public static} inner class of {@link HarmonicsPanel};
 * extracted to its own file (refactor F12-001) to reduce panel size.
 */
public class HarmonicRow {

    private int    order;
    private String frequency;
    private String currentAmp;
    private String currentPct;
    private String voltageVolt;
    private String voltagePct;
    private String status;

    public HarmonicRow(int order) {
        this.order       = order;
        this.frequency   = String.format("%.1f", order * 50.0);
        this.currentAmp  = "\u2014";
        this.currentPct  = "\u2014";
        this.voltageVolt = "\u2014";
        this.voltagePct  = "\u2014";
        this.status      = "\u2014";
    }

    public void update(String freq, String iAmp, String iPct,
                       String vVolt, String vPct, String stat) {
        this.frequency   = freq;
        this.currentAmp  = iAmp;
        this.currentPct  = iPct;
        this.voltageVolt = vVolt;
        this.voltagePct  = vPct;
        this.status      = stat;
    }

    public int    getOrder()       { return order; }
    public String getFrequency()   { return frequency; }
    public String getCurrentAmp()  { return currentAmp; }
    public String getCurrentPct()  { return currentPct; }
    public String getVoltageVolt() { return voltageVolt; }
    public String getVoltagePct()  { return voltagePct; }
    public String getStatus()      { return status; }
}
