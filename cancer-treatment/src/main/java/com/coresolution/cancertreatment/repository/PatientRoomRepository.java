package com.coresolution.cancertreatment.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.coresolution.cancertreatment.model.PatientRoom;

@Repository
public class PatientRoomRepository {

    private static final String SELECT_COLUMNS = """
            SELECT r.id, r.room_code, r.room_name, r.ward_id, r.display_order,
                   w.ward_name AS ward_name, w.admission_type AS admission_type
            FROM ct_patient_room r
            LEFT JOIN ct_ward w ON w.id = r.ward_id
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<PatientRoom> rowMapper = (rs, rowNum) -> new PatientRoom(
            rs.getLong("id"),
            rs.getString("room_code"),
            rs.getString("room_name"),
            (Long) rs.getObject("ward_id"),
            rs.getString("ward_name"),
            rs.getString("admission_type"),
            rs.getInt("display_order"));

    public PatientRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PatientRoom> findRooms(String instCode) {
        String sql = SELECT_COLUMNS
                + " WHERE r.inst_code = ? AND r.active_yn = 'Y'"
                + " ORDER BY r.display_order ASC, r.room_code ASC, r.id ASC";
        return jdbcTemplate.query(sql, rowMapper, instCode);
    }

    public PatientRoom createRoom(
            String instCode,
            String roomCode,
            String roomName,
            Long wardId,
            int displayOrder) {
        String sql = """
                INSERT INTO ct_patient_room (inst_code, room_code, room_name, ward_id, display_order)
                VALUES (?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instCode);
            ps.setString(2, roomCode);
            ps.setString(3, roomName);
            if (wardId == null) {
                ps.setNull(4, Types.BIGINT);
            } else {
                ps.setLong(4, wardId);
            }
            ps.setInt(5, displayOrder);
            return ps;
        }, keyHolder);
        Number id = generatedId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("병실 등록 결과를 확인할 수 없습니다.");
        }
        return findById(instCode, id.longValue());
    }

    public PatientRoom updateRoom(
            String instCode,
            Long id,
            String roomCode,
            String roomName,
            Long wardId,
            int displayOrder) {
        String sql = """
                UPDATE ct_patient_room
                SET room_code = ?, room_name = ?, ward_id = ?, display_order = ?
                WHERE inst_code = ? AND id = ? AND active_yn = 'Y'
                """;
        int updated = jdbcTemplate.update(sql, roomCode, roomName, wardId, displayOrder, instCode, id);
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 병실을 찾을 수 없습니다.");
        }
        return findById(instCode, id);
    }

    public void deactivateRoom(String instCode, Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_patient_room SET active_yn = 'N' WHERE inst_code = ? AND id = ? AND active_yn = 'Y'",
                instCode,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("삭제할 병실을 찾을 수 없습니다.");
        }
    }

    private PatientRoom findById(String instCode, Long id) {
        String sql = SELECT_COLUMNS + " WHERE r.inst_code = ? AND r.id = ? AND r.active_yn = 'Y'";
        return jdbcTemplate.query(sql, rowMapper, instCode, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장된 병실을 찾을 수 없습니다."));
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
