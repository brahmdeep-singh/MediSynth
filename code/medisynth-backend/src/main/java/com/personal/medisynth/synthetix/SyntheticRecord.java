package com.personal.medisynth.synthetix;

/**
 * One synthetic patient-year. Every value is sampled; none is copied from a real patient. The identifier is a
 * random-looking sequence number that has no link to any real record. Ages of 90 and over are top-coded to 90.
 */
public record SyntheticRecord(
        String syntheticId,
        int ageYears,
        String gender,
        String bloodGroup,
        double bmi,
        boolean smoker,
        String diagnosisCode,
        int systolicBp,
        int diastolicBp,
        int heartRate,
        int fastingGlucose,
        double hba1c,
        double hemoglobin,
        int totalCholesterol,
        double creatinine,
        int visitsLast12m,
        boolean admitted,
        int lengthOfStayDays,
        String outcome) {

    public int ageBand() {
        return Math.min(ageYears / 10, 9);
    }
}
