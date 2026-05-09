package com.harmonicmonitor.gui;

/**
 * Immutable data model for one row in the normative compliance table
 * of {@link CompliancePanel}.
 *
 * Previously a {@code public static} inner class of {@link CompliancePanel};
 * extracted to its own file (refactor F13-001).
 */
public class CompRow {

    private final String feeder;
    private final String standard;
    private final String parameter;
    private final String measured;
    private final String limit;
    private final String status;
    private final String notes;

    public CompRow(String feeder, String standard, String parameter,
                   String measured, String limit, String status, String notes) {
        this.feeder    = feeder;
        this.standard  = standard;
        this.parameter = parameter;
        this.measured  = measured;
        this.limit     = limit;
        this.status    = status;
        this.notes     = notes;
    }

    public String getFeeder()    { return feeder; }
    public String getStandard()  { return standard; }
    public String getParameter() { return parameter; }
    public String getMeasured()  { return measured; }
    public String getLimit()     { return limit; }
    public String getStatus()    { return status; }
    public String getNotes()     { return notes; }
}
