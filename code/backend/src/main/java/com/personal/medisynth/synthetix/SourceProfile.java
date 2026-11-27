package com.personal.medisynth.synthetix;

import java.util.Arrays;

/**
 * What the generator is allowed to learn from the hospital's own data: only coarse, suppressed aggregates
 * (ten-year age bands and gender shares). Row-level data never enters the engine.
 */
public record SourceProfile(String source, String sampleSizeBucket, double[] ageBandWeights, double[] genderWeights) {

    public static final String BUILT_IN = "BUILT_IN_PRIORS";
    public static final String REAL_AGGREGATES = "REAL_AGGREGATES";

    /** Cells with fewer real patients than this are never used (small-cell suppression). */
    public static final int MIN_CELL = 5;
    /** Fewer real patients than this and the built-in priors are used instead. */
    public static final int MIN_SAMPLE = 50;
    /** Strength of the pull towards the priors; keeps a small real sample from dominating. */
    private static final double SHRINKAGE = 50.0;

    static final double[] PRIOR_AGE_BANDS = normalise(new double[] {0.06, 0.07, 0.12, 0.16, 0.17, 0.16, 0.14, 0.08, 0.035, 0.005});
    /** MALE, FEMALE, OTHER */
    static final double[] PRIOR_GENDER = normalise(new double[] {0.495, 0.495, 0.010});

    public SourceProfile {
        ageBandWeights = normalise(ageBandWeights);
        genderWeights = normalise(genderWeights);
    }

    public static SourceProfile builtIn() {
        return new SourceProfile(BUILT_IN, "none", PRIOR_AGE_BANDS, PRIOR_GENDER);
    }

    /**
     * @param ageBandCounts real patients per ten-year age band (10 cells)
     * @param genderCounts  real patients per gender (MALE, FEMALE, OTHER)
     */
    public static SourceProfile fromCounts(long[] ageBandCounts, long[] genderCounts) {
        long total = Arrays.stream(genderCounts).sum();
        if (total < MIN_SAMPLE || ageBandCounts.length != 10 || genderCounts.length != 3) {
            return builtIn();
        }
        return new SourceProfile(REAL_AGGREGATES, bucket(total),
                shrink(ageBandCounts, PRIOR_AGE_BANDS), shrink(genderCounts, PRIOR_GENDER));
    }

    private static double[] shrink(long[] counts, double[] prior) {
        double total = 0;
        double[] used = new double[counts.length];
        for (int i = 0; i < counts.length; i++) {
            used[i] = counts[i] < MIN_CELL ? 0 : counts[i]; // suppressed cell: contributes nothing
            total += used[i];
        }
        double[] out = new double[counts.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (used[i] + SHRINKAGE * prior[i]) / (total + SHRINKAGE);
        }
        return out;
    }

    private static String bucket(long n) {
        return n < 200 ? "50-199" : n < 1000 ? "200-999" : "1000+";
    }

    static double[] normalise(double[] w) {
        double sum = 0;
        for (double v : w) {
            sum += v;
        }
        double[] out = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            out[i] = w[i] / sum;
        }
        return out;
    }
}
