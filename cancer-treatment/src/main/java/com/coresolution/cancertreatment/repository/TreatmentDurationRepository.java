package com.coresolution.cancertreatment.repository;

import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.coresolution.cancertreatment.model.TreatmentTypeDuration;

/**
 * Focused read/update of ct_treatment_type.duration_minutes only.
 * Treatment types themselves are still created/edited via the generic settings framework;
 * this repository touches the typed numeric duration column without changing that framework.
 */
@Repository
public class TreatmentDurationRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TreatmentTypeDuration> rowMapper = (rs, rowNum) -> new TreatmentTypeDuration(
            rs.getLong("id"),
            rs.getString("treatment_name"),
            rs.getString("room_name"),
            (Integer) rs.getObject("duration_minutes"));

    public TreatmentDurationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TreatmentTypeDuration> findAll(String instCode) {
        return jdbcTemplate.query(
                "SELECT id, treatment_name, room_name, duration_minutes FROM ct_treatment_type "
                        + "WHERE inst_code = ? AND active_yn = 'Y' "
                        + "ORDER BY display_order ASC, treatment_name ASC, id ASC",
                rowMapper, instCode);
    }

    public void updateDuration(String instCode, Long id, Integer durationMinutes) {
        int updated = jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "UPDATE ct_treatment_type SET duration_minutes = ? "
                            + "WHERE inst_code = ? AND id = ? AND active_yn = 'Y'");
            if (durationMinutes == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, durationMinutes);
            }
            ps.setString(2, instCode);
            ps.setLong(3, id);
            return ps;
        });
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 치료 항목을 찾을 수 없습니다.");
        }
    }
}
