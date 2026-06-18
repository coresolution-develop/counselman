package com.coresolution.cancertreatment.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.coresolution.cancertreatment.model.Therapist;

@Repository
public class TherapistRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Therapist> rowMapper = (rs, rowNum) -> new Therapist(
            rs.getLong("id"),
            rs.getString("therapist_name"),
            rs.getInt("display_order"));

    public TherapistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Therapist> findTherapists(String instCode) {
        return jdbcTemplate.query(
                "SELECT id, therapist_name, display_order FROM ct_therapist "
                        + "WHERE inst_code = ? AND active_yn = 'Y' "
                        + "ORDER BY display_order ASC, therapist_name ASC, id ASC",
                rowMapper, instCode);
    }

    public Therapist createTherapist(String instCode, String name, int displayOrder) {
        String sql = "INSERT INTO ct_therapist (inst_code, therapist_name, display_order) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instCode);
            ps.setString(2, name);
            ps.setInt(3, displayOrder);
            return ps;
        }, keyHolder);
        Number id = generatedId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("치료사 등록 결과를 확인할 수 없습니다.");
        }
        return findById(instCode, id.longValue());
    }

    public Therapist updateTherapist(String instCode, Long id, String name, int displayOrder) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_therapist SET therapist_name = ?, display_order = ? "
                        + "WHERE inst_code = ? AND id = ? AND active_yn = 'Y'",
                name, displayOrder, instCode, id);
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 치료사를 찾을 수 없습니다.");
        }
        return findById(instCode, id);
    }

    public void deactivateTherapist(String instCode, Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_therapist SET active_yn = 'N' WHERE inst_code = ? AND id = ? AND active_yn = 'Y'",
                instCode, id);
        if (updated == 0) {
            throw new IllegalArgumentException("삭제할 치료사를 찾을 수 없습니다.");
        }
    }

    private Therapist findById(String instCode, Long id) {
        return jdbcTemplate.query(
                "SELECT id, therapist_name, display_order FROM ct_therapist "
                        + "WHERE inst_code = ? AND id = ? AND active_yn = 'Y'",
                rowMapper, instCode, id)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("저장된 치료사를 찾을 수 없습니다."));
    }

    private Number generatedId(KeyHolder keyHolder) {
        if (keyHolder.getKeyList().isEmpty()) {
            return null;
        }
        Map<String, Object> keys = keyHolder.getKeyList().get(0);
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number number) {
                return number;
            }
        }
        return keys.values().stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .findFirst()
                .orElse(null);
    }
}
