package com.personal.medisynth.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.personal.medisynth.synthetix.SyntheticRecord;

/** JDBC access to synthetic_patient_records: thousands of rows per dataset, written in batches. */
@Component
public class SyntheticRecordStore {

    private static final int BATCH = 1000;

    private static final String INSERT = """
            INSERT INTO synthetic_patient_records (dataset_id, synthetic_id, age_years, gender, blood_group, bmi, smoker,
                diagnosis_code, systolic_bp, diastolic_bp, heart_rate, fasting_glucose, hba1c, hemoglobin,
                total_cholesterol, creatinine, visits_last_12m, admitted, length_of_stay_days, outcome)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String SELECT = """
            SELECT synthetic_id, age_years, gender, blood_group, bmi, smoker, diagnosis_code, systolic_bp, diastolic_bp,
                heart_rate, fasting_glucose, hba1c, hemoglobin, total_cholesterol, creatinine, visits_last_12m, admitted,
                length_of_stay_days, outcome
            FROM synthetic_patient_records WHERE dataset_id = ? ORDER BY id""";

    private final JdbcTemplate jdbc;

    public SyntheticRecordStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAll(long datasetId, List<SyntheticRecord> rows) {
        jdbc.batchUpdate(INSERT, rows, BATCH, (ps, r) -> {
            int i = 1;
            ps.setLong(i++, datasetId);
            ps.setString(i++, r.syntheticId());
            ps.setInt(i++, r.ageYears());
            ps.setString(i++, r.gender());
            ps.setString(i++, r.bloodGroup());
            ps.setDouble(i++, r.bmi());
            ps.setBoolean(i++, r.smoker());
            ps.setString(i++, r.diagnosisCode());
            ps.setInt(i++, r.systolicBp());
            ps.setInt(i++, r.diastolicBp());
            ps.setInt(i++, r.heartRate());
            ps.setInt(i++, r.fastingGlucose());
            ps.setDouble(i++, r.hba1c());
            ps.setDouble(i++, r.hemoglobin());
            ps.setInt(i++, r.totalCholesterol());
            ps.setDouble(i++, r.creatinine());
            ps.setInt(i++, r.visitsLast12m());
            ps.setBoolean(i++, r.admitted());
            ps.setInt(i++, r.lengthOfStayDays());
            ps.setString(i, r.outcome());
        });
    }

    public void deleteAll(long datasetId) {
        jdbc.update("DELETE FROM synthetic_patient_records WHERE dataset_id = ?", datasetId);
    }

    public long count(long datasetId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_patient_records WHERE dataset_id = ?", Long.class, datasetId);
        return n == null ? 0 : n;
    }

    public List<SyntheticRecord> loadAll(long datasetId) {
        return jdbc.query(SELECT, (rs, n) -> map(rs), datasetId);
    }

    public List<SyntheticRecord> loadFirst(long datasetId, int limit) {
        return jdbc.query(SELECT.replace("ORDER BY id", "ORDER BY id LIMIT ?"), (rs, n) -> map(rs), datasetId, limit);
    }

    private static SyntheticRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SyntheticRecord(rs.getString("synthetic_id"), rs.getInt("age_years"), rs.getString("gender"),
                rs.getString("blood_group"), rs.getBigDecimal("bmi").doubleValue(), rs.getBoolean("smoker"),
                rs.getString("diagnosis_code"), rs.getInt("systolic_bp"), rs.getInt("diastolic_bp"), rs.getInt("heart_rate"),
                rs.getInt("fasting_glucose"), rs.getBigDecimal("hba1c").doubleValue(),
                rs.getBigDecimal("hemoglobin").doubleValue(), rs.getInt("total_cholesterol"),
                rs.getBigDecimal("creatinine").doubleValue(), rs.getInt("visits_last_12m"), rs.getBoolean("admitted"),
                rs.getInt("length_of_stay_days"), rs.getString("outcome"));
    }
}
