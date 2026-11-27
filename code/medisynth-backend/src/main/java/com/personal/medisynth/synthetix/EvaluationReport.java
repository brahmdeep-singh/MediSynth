package com.personal.medisynth.synthetix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Result of evaluating a synthetic dataset. Stored as plain columns plus three small text blocks (one line per
 * item, fields separated by '|'), so no JSON library is involved.
 */
public record EvaluationReport(
        double overallScore, double marginalScore, double relationshipScore, double validityScore,
        double privacyScore, boolean passed, int k, double kAnonymityShare, int minClassSize, double uniqueShare,
        List<ColumnScore> columns, List<Check> checks, List<String> failureReasons) {

    /** How closely one column matches its reference (0..1). */
    public record ColumnScore(String column, String kind, double score, String detail) {
    }

    /** One relationship the data must preserve (for example: older patients have higher blood pressure). */
    public record Check(String key, String label, double observed, double threshold, boolean passed) {
    }

    // ------------------------------------------------------------------ text (de)serialisation

    public static String columnsToText(List<ColumnScore> columns) {
        StringBuilder sb = new StringBuilder();
        for (ColumnScore c : columns) {
            sb.append(clean(c.column())).append('|').append(c.kind()).append('|')
                    .append(String.format(Locale.ROOT, "%.4f", c.score())).append('|').append(clean(c.detail())).append('\n');
        }
        return sb.toString();
    }

    public static List<ColumnScore> columnsFromText(String text) {
        List<ColumnScore> out = new ArrayList<>();
        for (String line : lines(text)) {
            String[] f = line.split("\\|", 4);
            if (f.length == 4) {
                out.add(new ColumnScore(f[0], f[1], Double.parseDouble(f[2]), f[3]));
            }
        }
        return out;
    }

    public static String checksToText(List<Check> checks) {
        StringBuilder sb = new StringBuilder();
        for (Check c : checks) {
            sb.append(c.key()).append('|').append(clean(c.label())).append('|')
                    .append(String.format(Locale.ROOT, "%.4f", c.observed())).append('|')
                    .append(String.format(Locale.ROOT, "%.4f", c.threshold())).append('|').append(c.passed()).append('\n');
        }
        return sb.toString();
    }

    public static List<Check> checksFromText(String text) {
        List<Check> out = new ArrayList<>();
        for (String line : lines(text)) {
            String[] f = line.split("\\|", 5);
            if (f.length == 5) {
                out.add(new Check(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]), Boolean.parseBoolean(f[4])));
            }
        }
        return out;
    }

    public static String reasonsToText(List<String> reasons) {
        StringBuilder sb = new StringBuilder();
        for (String r : reasons) {
            sb.append(clean(r)).append('\n');
        }
        return sb.toString();
    }

    public static List<String> reasonsFromText(String text) {
        return lines(text);
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String line : text.split("\n")) {
            if (!line.isBlank()) {
                out.add(line);
            }
        }
        return out;
    }

    private static String clean(String s) {
        return s.replace('|', '/').replace('\n', ' ');
    }
}
