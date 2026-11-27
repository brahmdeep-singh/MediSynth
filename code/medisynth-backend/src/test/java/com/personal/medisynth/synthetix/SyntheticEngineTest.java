package com.personal.medisynth.synthetix;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests: the engine needs no Spring context and no database. */
class SyntheticEngineTest {

    @Test
    void sameSeedGivesSameRowsAndAnotherSeedDiffers() {
        SourceProfile p = SourceProfile.builtIn();
        List<SyntheticRecord> a = SyntheticDataGenerator.generate(1000, 7, p);
        List<SyntheticRecord> b = SyntheticDataGenerator.generate(1000, 7, p);
        List<SyntheticRecord> c = SyntheticDataGenerator.generate(1000, 8, p);
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void identifiersAreUniqueAndAgesAreTopCoded() {
        List<SyntheticRecord> rows = SyntheticDataGenerator.generate(5000, 11, SourceProfile.builtIn());
        Set<String> ids = new HashSet<>();
        for (SyntheticRecord r : rows) {
            assertThat(ids.add(r.syntheticId())).isTrue();
            assertThat(r.ageYears()).isBetween(0, SyntheticDataGenerator.TOP_CODED_AGE);
        }
    }

    @Test
    void everyGeneratedRowObeysTheClinicalRules() {
        for (SyntheticRecord r : SyntheticDataGenerator.generate(5000, 3, SourceProfile.builtIn())) {
            assertThat(DatasetEvaluator.isValid(r)).as(r.toString()).isTrue();
        }
    }

    @Test
    void aLargeDatasetPassesEvaluation() {
        SourceProfile p = SourceProfile.builtIn();
        EvaluationReport report = DatasetEvaluator.evaluate(SyntheticDataGenerator.generate(5000, 1, p), p);
        assertThat(report.passed()).isTrue();
        assertThat(report.overallScore()).isGreaterThanOrEqualTo(DatasetEvaluator.PASS_OVERALL);
        assertThat(report.checks()).allMatch(EvaluationReport.Check::passed);
    }

    @Test
    void aTinyDatasetFailsOnPrivacyAndSaysWhy() {
        SourceProfile p = SourceProfile.builtIn();
        EvaluationReport report = DatasetEvaluator.evaluate(SyntheticDataGenerator.generate(500, 1, p), p);
        assertThat(report.passed()).isFalse();
        assertThat(report.kAnonymityShare()).isLessThan(DatasetEvaluator.GATE_PRIVACY);
        assertThat(report.failureReasons()).anyMatch(s -> s.contains("look-alikes"));
    }

    @Test
    void anImpossibleRowIsCaughtByTheValidityRules() {
        SyntheticRecord good = SyntheticDataGenerator.generate(1, 5, SourceProfile.builtIn()).get(0);
        SyntheticRecord bad = new SyntheticRecord(good.syntheticId(), good.ageYears(), good.gender(), good.bloodGroup(),
                good.bmi(), good.smoker(), good.diagnosisCode(), 90, 120, good.heartRate(), good.fastingGlucose(),
                good.hba1c(), good.hemoglobin(), good.totalCholesterol(), good.creatinine(), good.visitsLast12m(),
                good.admitted(), good.lengthOfStayDays(), good.outcome());
        assertThat(DatasetEvaluator.isValid(bad)).isFalse();
    }

    @Test
    void smallRealCellsAndSmallSamplesAreSuppressed() {
        assertThat(SourceProfile.fromCounts(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, new long[] {20, 20, 0}).source())
                .isEqualTo(SourceProfile.BUILT_IN); // 40 patients is below the minimum sample
        long[] ages = {0, 3, 40, 60, 80, 70, 50, 30, 10, 0};
        SourceProfile p = SourceProfile.fromCounts(ages, new long[] {200, 190, 4});
        assertThat(p.source()).isEqualTo(SourceProfile.REAL_AGGREGATES);
        // a band with 3 real patients is suppressed, so it carries only the prior's small share, never the raw count
        assertThat(p.ageBandWeights()[1]).isLessThan(0.03);
        assertThat(p.genderWeights()[2]).isLessThan(0.01);
    }

    @Test
    void reportTextRoundTrips() {
        SourceProfile p = SourceProfile.builtIn();
        EvaluationReport r = DatasetEvaluator.evaluate(SyntheticDataGenerator.generate(5000, 2, p), p);
        assertThat(EvaluationReport.checksFromText(EvaluationReport.checksToText(r.checks()))).hasSameSizeAs(r.checks());
        assertThat(EvaluationReport.columnsFromText(EvaluationReport.columnsToText(r.columns()))).hasSameSizeAs(r.columns());
    }

    @Test
    void csvHasOneHeaderColumnPerField() {
        SyntheticRecord r = SyntheticDataGenerator.generate(1, 1, SourceProfile.builtIn()).get(0);
        StringBuilder row = new StringBuilder();
        SyntheticSchema.appendCsvRow(row, r);
        assertThat(SyntheticSchema.csvHeader().trim().split(",")).hasSize(SyntheticSchema.COLUMNS.size());
        assertThat(row.toString().trim().split(",")).hasSize(SyntheticSchema.COLUMNS.size());
    }
}
