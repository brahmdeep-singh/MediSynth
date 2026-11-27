package com.personal.medisynth.synthetix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import com.personal.medisynth.synthetix.EvaluationReport.Check;
import com.personal.medisynth.synthetix.EvaluationReport.ColumnScore;

/**
 * The "meets quality and privacy requirements?" decision from the activity diagram.
 *
 * Four independent tests, each 0..1:
 *  - marginal fidelity:  do single columns match the reference case mix and clinical ranges?
 *  - relationships:      do the clinical relationships (age and blood pressure, diabetes and glucose, ...) hold?
 *  - validity:           does every row obey hard clinical rules (systolic above diastolic, stay length only if admitted, ...)?
 *  - privacy:            k-anonymity on the quasi-identifiers (age band, gender, diagnosis), and no duplicate rows.
 *
 * A dataset passes only if the overall score and each gate are met. A failed dataset is regenerated, typically
 * with more records (small datasets have too many rare combinations to be k-anonymous).
 */
public final class DatasetEvaluator {

    public static final int K = 5;
    public static final double PASS_OVERALL = 0.90;
    public static final double GATE_VALIDITY = 0.999;
    public static final double GATE_PRIVACY = 0.95;
    public static final double GATE_RELATIONSHIPS = 0.75;
    public static final double GATE_MAX_UNIQUE = 0.01;

    /** Intended diagnosis mix. */
    private static final Map<String, Double> DX_REFERENCE = Map.of(
            "Z00", 0.24, "J06", 0.20, "M54", 0.09, "J45", 0.08, "I10", 0.16,
            "E11", 0.07, "E78", 0.05, "I25", 0.04, "J44", 0.04, "N18", 0.03);
    private static final double[] BLOOD_REF = {0.30, 0.31, 0.22, 0.07, 0.03, 0.03, 0.03, 0.01};
    private static final String[] BLOOD = {"O+", "B+", "A+", "AB+", "O-", "B-", "A-", "AB-"};
    private static final String[] GENDERS = {"MALE", "FEMALE", "OTHER"};

    private record NumRef(String name, ToDoubleFunction<SyntheticRecord> get, double mean, double sd) {
    }

    private static final List<NumRef> NUMERIC = List.of(
            new NumRef("bmi", SyntheticRecord::bmi, 24.5, 5.0),
            new NumRef("systolicBp", SyntheticRecord::systolicBp, 120, 17),
            new NumRef("diastolicBp", SyntheticRecord::diastolicBp, 80, 10),
            new NumRef("heartRate", SyntheticRecord::heartRate, 76, 10),
            new NumRef("fastingGlucose", SyntheticRecord::fastingGlucose, 96, 21),
            new NumRef("hba1c", SyntheticRecord::hba1c, 5.5, 0.9),
            new NumRef("hemoglobin", SyntheticRecord::hemoglobin, 13.8, 1.5),
            new NumRef("totalCholesterol", SyntheticRecord::totalCholesterol, 182, 30),
            new NumRef("creatinine", SyntheticRecord::creatinine, 0.90, 0.37),
            new NumRef("visitsLast12m", SyntheticRecord::visitsLast12m, 2.7, 2.0));

    private DatasetEvaluator() {
    }

    public static EvaluationReport evaluate(List<SyntheticRecord> rows, SourceProfile profile) {
        int n = rows.size();
        if (n == 0) {
            throw new IllegalArgumentException("dataset has no records");
        }
        List<ColumnScore> columns = new ArrayList<>();

        // ---- marginal fidelity
        double catSum = 0;
        int catCount = 0;
        double[] ageObs = new double[10];
        double[] genderObs = new double[3];
        double[] bloodObs = new double[8];
        Map<String, Double> dxObs = new HashMap<>();
        double smokers = 0;
        for (SyntheticRecord r : rows) {
            ageObs[r.ageBand()]++;
            genderObs[indexOf(GENDERS, r.gender())]++;
            bloodObs[indexOf(BLOOD, r.bloodGroup())]++;
            dxObs.merge(r.diagnosisCode(), 1.0, Double::sum);
            if (r.smoker()) {
                smokers++;
            }
        }
        double tvdAge = tvd(ageObs, profile.ageBandWeights(), n);
        double tvdGender = tvd(genderObs, profile.genderWeights(), n);
        double tvdBlood = tvd(bloodObs, BLOOD_REF, n);
        double[] dxArr = new double[Diagnosis.values().length];
        double[] dxRef = new double[dxArr.length];
        for (int i = 0; i < dxArr.length; i++) {
            String code = Diagnosis.values()[i].code();
            dxArr[i] = dxObs.getOrDefault(code, 0.0);
            dxRef[i] = DX_REFERENCE.get(code);
        }
        double tvdDx = tvd(dxArr, dxRef, n);
        for (Object[] c : new Object[][] {{"ageBand", tvdAge}, {"gender", tvdGender}, {"bloodGroup", tvdBlood}, {"diagnosis", tvdDx}}) {
            double score = 1 - (double) c[1];
            columns.add(new ColumnScore((String) c[0], "categorical", score,
                    String.format(Locale.ROOT, "total variation distance %.3f", (double) c[1])));
            catSum += score;
            catCount++;
        }
        double numSum = 0;
        for (NumRef ref : NUMERIC) {
            double mean = 0;
            for (SyntheticRecord r : rows) {
                mean += ref.get().applyAsDouble(r);
            }
            mean /= n;
            double var = 0;
            for (SyntheticRecord r : rows) {
                double d = ref.get().applyAsDouble(r) - mean;
                var += d * d;
            }
            double sd = Math.sqrt(var / n);
            double meanScore = Math.max(0, 1 - Math.abs(mean - ref.mean()) / ref.sd());
            double sdScore = Math.max(0, 1 - Math.abs(sd / ref.sd() - 1));
            double score = 0.5 * meanScore + 0.5 * sdScore;
            columns.add(new ColumnScore(ref.name(), "numeric", score,
                    String.format(Locale.ROOT, "mean %.2f (ref %.2f), sd %.2f (ref %.2f)", mean, ref.mean(), sd, ref.sd())));
            numSum += score;
        }
        double marginal = 0.5 * (catSum / catCount) + 0.5 * (numSum / NUMERIC.size());

        // ---- relationships
        List<Check> checks = relationshipChecks(rows);
        long passedChecks = checks.stream().filter(Check::passed).count();
        double relationship = (double) passedChecks / checks.size();

        // ---- validity
        long valid = rows.stream().filter(DatasetEvaluator::isValid).count();
        double validity = (double) valid / n;

        // ---- privacy: k-anonymity on (age band, gender, diagnosis), plus exact duplicate rows
        Map<String, Integer> classes = new HashMap<>();
        for (SyntheticRecord r : rows) {
            classes.merge(r.ageBand() + "|" + r.gender() + "|" + r.diagnosisCode(), 1, Integer::sum);
        }
        long inLargeClasses = 0;
        long unique = 0;
        int minClass = Integer.MAX_VALUE;
        for (int size : classes.values()) {
            minClass = Math.min(minClass, size);
            if (size >= K) {
                inLargeClasses += size;
            }
            if (size == 1) {
                unique++;
            }
        }
        double kShare = (double) inLargeClasses / n;
        double uniqueShare = (double) unique / n;
        Map<String, Integer> tuples = new HashMap<>();
        long duplicateRows = 0;
        for (SyntheticRecord r : rows) {
            String key = r.ageYears() + "|" + r.gender() + "|" + r.bloodGroup() + "|" + r.bmi() + "|" + r.diagnosisCode()
                    + "|" + r.systolicBp() + "|" + r.diastolicBp() + "|" + r.heartRate() + "|" + r.fastingGlucose()
                    + "|" + r.hemoglobin() + "|" + r.totalCholesterol() + "|" + r.creatinine();
            if (tuples.merge(key, 1, Integer::sum) > 1) {
                duplicateRows++;
            }
        }
        double duplicateShare = (double) duplicateRows / n;
        double privacy = Math.max(0, kShare - 5 * uniqueShare - duplicateShare);

        double overall = 0.35 * marginal + 0.20 * relationship + 0.15 * validity + 0.30 * privacy;

        List<String> reasons = new ArrayList<>();
        if (overall < PASS_OVERALL) {
            reasons.add(String.format(Locale.ROOT, "Overall score %.3f is below the required %.2f.", overall, PASS_OVERALL));
        }
        if (validity < GATE_VALIDITY) {
            reasons.add(String.format(Locale.ROOT, "%.2f%% of records break a clinical consistency rule.", (1 - validity) * 100));
        }
        if (kShare < GATE_PRIVACY) {
            reasons.add(String.format(Locale.ROOT,
                    "Only %.1f%% of records sit in a group of at least %d look-alikes (age band, gender, diagnosis); %.0f%% needed. "
                            + "Regenerate with more records.", kShare * 100, K, GATE_PRIVACY * 100));
        }
        if (uniqueShare > GATE_MAX_UNIQUE) {
            reasons.add(String.format(Locale.ROOT, "%.2f%% of records are unique on the quasi-identifiers (limit %.1f%%).",
                    uniqueShare * 100, GATE_MAX_UNIQUE * 100));
        }
        if (duplicateShare > 0) {
            reasons.add(String.format(Locale.ROOT, "%.2f%% of records duplicate another record.", duplicateShare * 100));
        }
        if (relationship < GATE_RELATIONSHIPS) {
            reasons.add(String.format(Locale.ROOT, "Only %d of %d clinical relationships were preserved.", passedChecks, checks.size()));
        }
        boolean passed = reasons.isEmpty();
        return new EvaluationReport(overall, marginal, relationship, validity, privacy, passed, K, kShare,
                minClass == Integer.MAX_VALUE ? 0 : minClass, uniqueShare, columns, checks, reasons);
    }

    // ------------------------------------------------------------------ relationships

    private static List<Check> relationshipChecks(List<SyntheticRecord> rows) {
        List<Check> out = new ArrayList<>();
        double[] age = new double[rows.size()];
        double[] sbp = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            age[i] = rows.get(i).ageYears();
            sbp[i] = rows.get(i).systolicBp();
        }
        double corr = correlation(age, sbp);
        out.add(new Check("age_sbp", "Older patients have higher systolic blood pressure (correlation)", corr, 0.20, corr >= 0.20));

        double d1 = meanDiff(rows, r -> r.diagnosisCode().equals("E11"), SyntheticRecord::fastingGlucose);
        out.add(new Check("diabetes_glucose", "Diabetic patients have higher fasting glucose (mg/dL difference)", d1, 35, d1 >= 35));

        double d2 = meanDiff(rows, r -> r.diagnosisCode().equals("I10"), SyntheticRecord::systolicBp);
        out.add(new Check("htn_sbp", "Hypertensive patients have higher systolic pressure (mmHg difference)", d2, 12, d2 >= 12));

        double d3 = meanDiff(rows, r -> r.diagnosisCode().equals("N18"), SyntheticRecord::creatinine);
        out.add(new Check("ckd_creatinine", "Kidney disease patients have higher creatinine (mg/dL difference)", d3, 0.6, d3 >= 0.6));

        double d4 = meanDiff(rows, r -> r.diagnosisCode().equals("E78"), SyntheticRecord::totalCholesterol);
        out.add(new Check("lipid_cholesterol", "Hyperlipidaemia patients have higher total cholesterol (mg/dL difference)", d4, 25, d4 >= 25));

        double admitHigh = share(rows, r -> isHighAcuity(r), SyntheticRecord::admitted);
        double admitLow = share(rows, r -> !isHighAcuity(r), SyntheticRecord::admitted);
        double ratio = admitLow <= 0 ? 99 : admitHigh / admitLow;
        out.add(new Check("acuity_admission", "Heart, lung and kidney disease are admitted more often (ratio)", ratio, 2.0, ratio >= 2.0));

        double d6 = meanDiff(rows, r -> Diagnosis.fromCode(r.diagnosisCode()).chronic(), SyntheticRecord::visitsLast12m);
        out.add(new Check("chronic_visits", "Chronic conditions have more visits (visits difference)", d6, 0.8, d6 >= 0.8));

        double smokeCopd = share(rows, r -> r.diagnosisCode().equals("J44"), SyntheticRecord::smoker);
        double smokeAll = share(rows, r -> true, SyntheticRecord::smoker);
        double ratio2 = smokeAll <= 0 ? 0 : smokeCopd / smokeAll;
        out.add(new Check("copd_smoking", "COPD patients smoke more than average (ratio)", ratio2, 2.0, ratio2 >= 2.0));
        return out;
    }

    private static boolean isHighAcuity(SyntheticRecord r) {
        String c = r.diagnosisCode();
        return c.equals("I25") || c.equals("J44") || c.equals("N18");
    }

    /** Hard clinical rules. A single failing rule makes the record invalid. */
    static boolean isValid(SyntheticRecord r) {
        return r.ageYears() >= 0 && r.ageYears() <= 110
                && r.systolicBp() >= r.diastolicBp() + 15
                && r.systolicBp() >= 70 && r.systolicBp() <= 260
                && r.diastolicBp() >= 35 && r.diastolicBp() <= 150
                && r.heartRate() >= 30 && r.heartRate() <= 200
                && r.bmi() >= 10 && r.bmi() <= 70
                && r.fastingGlucose() >= 40 && r.fastingGlucose() <= 600
                && r.hba1c() >= 3 && r.hba1c() <= 16
                && Math.abs(r.hba1c() - ((r.fastingGlucose() * 1.15 + 46.7) / 28.7)) <= 1.5
                && r.hemoglobin() >= 5 && r.hemoglobin() <= 22
                && r.totalCholesterol() >= 80 && r.totalCholesterol() <= 500
                && r.creatinine() >= 0.2 && r.creatinine() <= 15
                && r.visitsLast12m() >= 0
                && (r.admitted() ? r.lengthOfStayDays() >= 1 : r.lengthOfStayDays() == 0)
                && !(r.smoker() && r.ageYears() < 16);
    }

    // ------------------------------------------------------------------ statistics helpers

    private static double tvd(double[] observedCounts, double[] reference, int n) {
        double refSum = 0;
        for (double v : reference) {
            refSum += v;
        }
        double sum = 0;
        for (int i = 0; i < observedCounts.length; i++) {
            sum += Math.abs(observedCounts[i] / n - reference[i] / refSum);
        }
        return sum / 2;
    }

    private static double correlation(double[] x, double[] y) {
        int n = x.length;
        double mx = 0;
        double my = 0;
        for (int i = 0; i < n; i++) {
            mx += x[i];
            my += y[i];
        }
        mx /= n;
        my /= n;
        double sxy = 0;
        double sxx = 0;
        double syy = 0;
        for (int i = 0; i < n; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        return sxx == 0 || syy == 0 ? 0 : sxy / Math.sqrt(sxx * syy);
    }

    private static double meanDiff(List<SyntheticRecord> rows, java.util.function.Predicate<SyntheticRecord> group,
            ToDoubleFunction<SyntheticRecord> value) {
        double a = 0;
        double b = 0;
        int na = 0;
        int nb = 0;
        for (SyntheticRecord r : rows) {
            if (group.test(r)) {
                a += value.applyAsDouble(r);
                na++;
            } else {
                b += value.applyAsDouble(r);
                nb++;
            }
        }
        return na == 0 || nb == 0 ? 0 : a / na - b / nb;
    }

    private static double share(List<SyntheticRecord> rows, java.util.function.Predicate<SyntheticRecord> group,
            java.util.function.Predicate<SyntheticRecord> event) {
        int g = 0;
        int e = 0;
        for (SyntheticRecord r : rows) {
            if (group.test(r)) {
                g++;
                if (event.test(r)) {
                    e++;
                }
            }
        }
        return g == 0 ? 0 : (double) e / g;
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) {
                return i;
            }
        }
        throw new IllegalArgumentException("unexpected value");
    }
}
