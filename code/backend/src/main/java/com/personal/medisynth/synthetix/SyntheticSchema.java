package com.personal.medisynth.synthetix;

import java.util.List;
import java.util.Locale;

/** Column catalogue and CSV export for synthetic records. */
public final class SyntheticSchema {

    public record Column(String name, String type, String role, String description) {
    }

    public static final List<Column> COLUMNS = List.of(
            new Column("syntheticId", "text", "identifier", "Random sequence id. Not linked to any real patient."),
            new Column("ageYears", "integer", "quasi-identifier", "Age in years; 90 and over are top-coded to 90."),
            new Column("gender", "category", "quasi-identifier", "MALE, FEMALE or OTHER."),
            new Column("bloodGroup", "category", "attribute", "ABO/Rh blood group."),
            new Column("bmi", "decimal", "attribute", "Body mass index, kg/m2."),
            new Column("smoker", "boolean", "attribute", "Current smoker."),
            new Column("diagnosisCode", "category", "quasi-identifier", "Primary diagnosis (ICD-10 code)."),
            new Column("systolicBp", "integer", "attribute", "Systolic blood pressure, mmHg."),
            new Column("diastolicBp", "integer", "attribute", "Diastolic blood pressure, mmHg."),
            new Column("heartRate", "integer", "attribute", "Resting heart rate, beats per minute."),
            new Column("fastingGlucose", "integer", "attribute", "Fasting plasma glucose, mg/dL."),
            new Column("hba1c", "decimal", "attribute", "Glycated haemoglobin, %."),
            new Column("hemoglobin", "decimal", "attribute", "Haemoglobin, g/dL."),
            new Column("totalCholesterol", "integer", "attribute", "Total cholesterol, mg/dL."),
            new Column("creatinine", "decimal", "attribute", "Serum creatinine, mg/dL."),
            new Column("visitsLast12m", "integer", "attribute", "Outpatient visits in the last 12 months."),
            new Column("admitted", "boolean", "attribute", "Admitted to hospital during the year."),
            new Column("lengthOfStayDays", "integer", "attribute", "Length of stay in days (0 if not admitted)."),
            new Column("outcome", "category", "attribute", "RECOVERED, IMPROVED, STABLE or READMITTED."));

    private SyntheticSchema() {
    }

    public static String csvHeader() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COLUMNS.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(COLUMNS.get(i).name());
        }
        return sb.append('\n').toString();
    }

    /** All values are generated numbers, flags or fixed codes, so no quoting or escaping is ever needed. */
    public static void appendCsvRow(StringBuilder sb, SyntheticRecord r) {
        sb.append(r.syntheticId()).append(',')
                .append(r.ageYears()).append(',')
                .append(r.gender()).append(',')
                .append(r.bloodGroup()).append(',')
                .append(String.format(Locale.ROOT, "%.1f", r.bmi())).append(',')
                .append(r.smoker()).append(',')
                .append(r.diagnosisCode()).append(',')
                .append(r.systolicBp()).append(',')
                .append(r.diastolicBp()).append(',')
                .append(r.heartRate()).append(',')
                .append(r.fastingGlucose()).append(',')
                .append(String.format(Locale.ROOT, "%.1f", r.hba1c())).append(',')
                .append(String.format(Locale.ROOT, "%.1f", r.hemoglobin())).append(',')
                .append(r.totalCholesterol()).append(',')
                .append(String.format(Locale.ROOT, "%.2f", r.creatinine())).append(',')
                .append(r.visitsLast12m()).append(',')
                .append(r.admitted()).append(',')
                .append(r.lengthOfStayDays()).append(',')
                .append(r.outcome()).append('\n');
    }
}
