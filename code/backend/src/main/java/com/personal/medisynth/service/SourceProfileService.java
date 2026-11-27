package com.personal.medisynth.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.personal.medisynth.synthetix.SourceProfile;

/**
 * Reads only grouped counts of the real patient table (ten-year age bands, gender). Never a row, never a name.
 * Small cells and small samples are suppressed inside {@link SourceProfile#fromCounts}.
 */
@Service
public class SourceProfileService {

    private static final String[] GENDERS = {"MALE", "FEMALE", "OTHER"};

    private final JdbcTemplate jdbc;

    public SourceProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SourceProfile currentProfile() {
        long[] bands = new long[10];
        List<Map<String, Object>> byAge = jdbc.queryForList("""
                SELECT LEAST(FLOOR(EXTRACT(YEAR FROM AGE(date_of_birth)) / 10), 9)::int AS band, COUNT(*) AS n
                FROM patient_profiles WHERE date_of_birth IS NOT NULL GROUP BY 1""");
        for (Map<String, Object> row : byAge) {
            int band = ((Number) row.get("band")).intValue();
            if (band >= 0 && band < 10) {
                bands[band] = ((Number) row.get("n")).longValue();
            }
        }
        long[] genders = new long[3];
        List<Map<String, Object>> byGender = jdbc.queryForList(
                "SELECT gender, COUNT(*) AS n FROM patient_profiles WHERE gender IS NOT NULL GROUP BY gender");
        for (Map<String, Object> row : byGender) {
            for (int i = 0; i < GENDERS.length; i++) {
                if (GENDERS[i].equals(row.get("gender"))) {
                    genders[i] = ((Number) row.get("n")).longValue();
                }
            }
        }
        return SourceProfile.fromCounts(bands, genders);
    }
}
