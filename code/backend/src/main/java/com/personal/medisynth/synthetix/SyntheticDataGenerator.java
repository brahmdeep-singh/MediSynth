package com.personal.medisynth.synthetix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Synthetix generator: samples synthetic patient-years from a small causal model
 * (age, gender -> BMI, smoking -> diagnosis -> vitals, labs, utilisation).
 *
 * It is deterministic for a given (count, seed, profile), never reads a real record, and only takes the age and
 * gender mix from the {@link SourceProfile}. Everything else comes from the model below, which encodes general
 * clinical relationships (for example: blood pressure rises with age and hypertension, glucose is high in diabetes).
 */
public final class SyntheticDataGenerator {

    public static final int TOP_CODED_AGE = 90;

    private static final String[] GENDERS = {"MALE", "FEMALE", "OTHER"};
    private static final String[] BLOOD = {"O+", "B+", "A+", "AB+", "O-", "B-", "A-", "AB-"};
    private static final double[] BLOOD_W = SourceProfile.normalise(new double[] {0.30, 0.31, 0.22, 0.07, 0.03, 0.03, 0.03, 0.01});
    private static final Diagnosis[] DX = Diagnosis.values();

    private SyntheticDataGenerator() {
    }

    public static List<SyntheticRecord> generate(int count, long seed, SourceProfile profile) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        Random rnd = new Random(seed);
        List<SyntheticRecord> out = new ArrayList<>(count);
        // Identifiers are a seeded shuffle of 1..count: unique, unordered, and unrelated to any real id.
        int[] order = shuffled(count, rnd);
        for (int i = 0; i < count; i++) {
            out.add(one(String.format(Locale.ROOT, "SYN-%07d", order[i]), rnd, profile));
        }
        return out;
    }

    private static SyntheticRecord one(String id, Random rnd, SourceProfile p) {
        int band = pick(rnd, p.ageBandWeights());
        int age = Math.min(band * 10 + rnd.nextInt(10), TOP_CODED_AGE);
        int g = pick(rnd, p.genderWeights());
        boolean male = g == 0;
        String blood = BLOOD[pick(rnd, BLOOD_W)];

        double bmiMean = age < 18 ? 15.5 + 0.28 * age : 22.0 + 0.10 * Math.min(age - 18, 40) + (male ? 0.4 : 0.0);
        double bmi = round1(clamp(bmiMean + 4.2 * rnd.nextGaussian(), 12.0, 55.0));

        double smokeP = age < 16 ? 0.0 : (male ? 0.26 : 0.05) * Math.min(1.0, (age - 15) / 15.0) * (age > 70 ? 0.6 : 1.0);
        boolean smoker = rnd.nextDouble() < smokeP;

        Diagnosis dx = diagnosis(rnd, age, bmi, smoker, male);
        boolean adult = age >= 18;

        // Blood pressure
        double sbpMean = adult ? 103 + 0.30 * age + 0.45 * (bmi - 25) : 92 + 1.0 * age;
        int sbp = (int) Math.round(clamp(sbpMean + dx.sbpShift() + 10.5 * rnd.nextGaussian(), 80, 230));
        double dbpMean = 0.46 * sbp + 26;
        int dbp = (int) Math.round(clamp(dbpMean + 6.0 * rnd.nextGaussian(), 45, 130));
        if (sbp - dbp < 20) {
            dbp = sbp - 20 - rnd.nextInt(10);
        }
        dbp = Math.max(dbp, 45);
        if (sbp - dbp < 20) {
            sbp = dbp + 20;
        }

        // Heart rate
        double hrShift = switch (dx) {
            case J06, J44 -> 6;
            case J45 -> 3;
            case I25 -> -4;
            default -> 0;
        };
        int hr = (int) Math.round(clamp((adult ? 74 : 88 - 0.6 * age) + hrShift - 0.05 * (age - 40) + 9 * rnd.nextGaussian(), 40, 160));

        // Glucose and HbA1c
        double gluMean = 90 + 0.12 * (age - 40) + 0.25 * (bmi - 25);
        double glu = dx == Diagnosis.E11 ? gluMean + 55 + 26 * Math.abs(rnd.nextGaussian()) * 0.8 + 8 * rnd.nextGaussian()
                : gluMean + 8 * rnd.nextGaussian();
        int fastingGlucose = (int) Math.round(clamp(glu, 60, 420));
        double hba1c = round1(clamp((fastingGlucose * 1.15 + 46.7) / 28.7 + 0.2 * rnd.nextGaussian(), 4.0, 14.0));

        // Haemoglobin
        double hbBase = !adult ? 12.8 : male ? 14.8 : 13.0;
        double hb = hbBase - (dx == Diagnosis.N18 ? 1.8 : 0) + 1.1 * rnd.nextGaussian();
        double hemoglobin = round1(clamp(hb, 6.0, 19.0));

        // Cholesterol
        double cholShift = switch (dx) {
            case E78 -> 45;
            case I25 -> 25;
            case E11 -> 12;
            default -> 0;
        };
        int chol = (int) Math.round(clamp(176 + 0.35 * (Math.max(age, 20) - 40) + cholShift + 27 * rnd.nextGaussian(), 100, 380));

        // Creatinine
        double crBase = !adult ? 0.5 : male ? 0.95 : 0.78;
        double cr = crBase + 0.004 * (Math.max(age, 18) - 40) + (dx == Diagnosis.N18 ? 1.3 + 0.5 * Math.abs(rnd.nextGaussian()) : 0)
                + 0.12 * rnd.nextGaussian();
        double creatinine = round2(clamp(cr, 0.3, 9.0));

        // Utilisation
        double lambda = 0.5 + dx.visitBase() + 0.01 * age;
        int visits = Math.min(poisson(rnd, lambda), 24);
        double ageFactor = Math.max(0.5, 1 + 0.012 * (age - 40));
        boolean admitted = rnd.nextDouble() < Math.min(0.9, dx.admitProbability() * ageFactor);
        int los = admitted ? Math.min(1 + poisson(rnd, Math.max(0.2, dx.losMean() - 1)), 30) : 0;

        String outcome = outcome(rnd, dx, admitted);
        return new SyntheticRecord(id, age, GENDERS[g], blood, bmi, smoker, dx.code(), sbp, dbp, hr, fastingGlucose,
                hba1c, hemoglobin, chol, creatinine, visits, admitted, los, outcome);
    }

    private static Diagnosis diagnosis(Random rnd, int age, double bmi, boolean smoker, boolean male) {
        double[] logit = new double[DX.length];
        double s = smoker ? 1 : 0;
        double m = male ? 1 : 0;
        double b = bmi - 25;
        for (int i = 0; i < DX.length; i++) {
            double l = switch (DX[i]) {
                case Z00 -> 0.45 - 0.004 * age;
                case J06 -> 1.00 - 0.025 * age;
                case M54 -> -1.20 + 0.012 * age;
                case J45 -> -0.55 - 0.015 * age + (age < 18 ? 1.1 : 0);
                case I10 -> -2.55 + 0.050 * age + 0.07 * b + 0.45 * s;
                case E11 -> -3.15 + 0.045 * age + 0.10 * b;
                case E78 -> -2.85 + 0.036 * age + 0.05 * b;
                case I25 -> -5.75 + 0.075 * age + 1.0 * s + 0.4 * m;
                case J44 -> -6.25 + 0.082 * age + 2.0 * s;
                case N18 -> -5.15 + 0.065 * age + 0.03 * b;
            };
            if (age < 18 && DX[i].adultOnly()) {
                l -= 7;
            }
            logit[i] = l;
        }
        double max = Double.NEGATIVE_INFINITY;
        for (double l : logit) {
            max = Math.max(max, l);
        }
        double[] w = new double[logit.length];
        for (int i = 0; i < w.length; i++) {
            w[i] = Math.exp(logit[i] - max);
        }
        return DX[pick(rnd, SourceProfile.normalise(w))];
    }

    private static String outcome(Random rnd, Diagnosis dx, boolean admitted) {
        double u = rnd.nextDouble();
        if (!dx.chronic()) {
            return u < 0.85 ? "RECOVERED" : u < 0.97 ? "IMPROVED" : "STABLE";
        }
        if (admitted) {
            return u < 0.45 ? "IMPROVED" : u < 0.80 ? "STABLE" : "READMITTED";
        }
        return u < 0.35 ? "IMPROVED" : u < 0.92 ? "STABLE" : "READMITTED";
    }

    // ------------------------------------------------------------------ sampling helpers

    private static int pick(Random rnd, double[] weights) {
        double u = rnd.nextDouble();
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (u < acc) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static int poisson(Random rnd, double lambda) {
        double limit = Math.exp(-lambda);
        double prod = 1.0;
        int k = 0;
        do {
            k++;
            prod *= rnd.nextDouble();
        } while (prod > limit && k < 100);
        return k - 1;
    }

    private static int[] shuffled(int n, Random rnd) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i + 1;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
