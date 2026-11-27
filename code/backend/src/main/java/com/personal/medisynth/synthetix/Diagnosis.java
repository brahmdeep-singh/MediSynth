package com.personal.medisynth.synthetix;

/**
 * Primary diagnoses the Synthetix generator can assign. Plain codes and labels only: no real patient data is
 * attached to any of them. Per-diagnosis parameters shape the vitals, labs and utilisation of a synthetic record.
 */
public enum Diagnosis {
    //   code   label                               chronic adultOnly visits admit  losMean sbpShift
    Z00("Z00", "Routine health check",               false, false, 0.4, 0.010, 2.0,  0),
    J06("J06", "Acute upper respiratory infection",  false, false, 1.0, 0.020, 2.5,  0),
    M54("M54", "Back pain",                          false, false, 1.6, 0.020, 2.5,  0),
    J45("J45", "Asthma",                             true,  false, 2.2, 0.060, 3.0,  0),
    I10("I10", "Essential hypertension",             true,  true,  2.6, 0.050, 3.0, 24),
    E11("E11", "Type 2 diabetes mellitus",           true,  true,  3.1, 0.090, 4.0,  6),
    E78("E78", "Hyperlipidaemia",                    true,  true,  2.1, 0.030, 2.5,  3),
    I25("I25", "Chronic ischaemic heart disease",    true,  true,  3.6, 0.280, 5.0,  8),
    J44("J44", "Chronic obstructive pulmonary disease", true, true, 3.3, 0.220, 5.5,  2),
    N18("N18", "Chronic kidney disease",             true,  true,  4.0, 0.250, 6.0, 14);

    private final String code;
    private final String label;
    private final boolean chronic;
    private final boolean adultOnly;
    private final double visitBase;
    private final double admitProbability;
    private final double losMean;
    private final int sbpShift;

    Diagnosis(String code, String label, boolean chronic, boolean adultOnly, double visitBase,
            double admitProbability, double losMean, int sbpShift) {
        this.code = code;
        this.label = label;
        this.chronic = chronic;
        this.adultOnly = adultOnly;
        this.visitBase = visitBase;
        this.admitProbability = admitProbability;
        this.losMean = losMean;
        this.sbpShift = sbpShift;
    }

    public String code() { return code; }
    public String label() { return label; }
    public boolean chronic() { return chronic; }
    public boolean adultOnly() { return adultOnly; }
    public double visitBase() { return visitBase; }
    public double admitProbability() { return admitProbability; }
    public double losMean() { return losMean; }
    public int sbpShift() { return sbpShift; }

    public static Diagnosis fromCode(String code) {
        for (Diagnosis d : values()) {
            if (d.code.equals(code)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Unknown diagnosis code");
    }
}
