package com.coresolution.cancertreatment.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.coresolution.cancertreatment.model.TreatmentSeat;

@Repository
public class TreatmentSeatRepository {

    private static final String SELECT_COLUMNS = """
            SELECT s.id, s.treatment_room_id, s.seat_code, s.seat_name, s.display_order,
                   r.room_name AS room_name
            FROM ct_treatment_seat s
            JOIN ct_treatment_room r ON r.id = s.treatment_room_id
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TreatmentSeat> rowMapper = (rs, rowNum) -> new TreatmentSeat(
            rs.getLong("id"),
            rs.getLong("treatment_room_id"),
            rs.getString("room_name"),
            rs.getString("seat_code"),
            rs.getString("seat_name"),
            rs.getInt("display_order"));

    public TreatmentSeatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TreatmentSeat> findSeats(String instCode, Long treatmentRoomId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append(" WHERE s.inst_code = ? AND s.active_yn = 'Y'");
        args.add(instCode);
        if (treatmentRoomId != null) {
            sql.append(" AND s.treatment_room_id = ?");
            args.add(treatmentRoomId);
        }
        sql.append(" ORDER BY s.treatment_room_id ASC, s.display_order ASC, s.seat_code ASC, s.id ASC");
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    public TreatmentSeat createSeat(
            String instCode,
            Long treatmentRoomId,
            String seatCode,
            String seatName,
            int displayOrder) {
        String sql = """
                INSERT INTO ct_treatment_seat (inst_code, treatment_room_id, seat_code, seat_name, display_order)
                VALUES (?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instCode);
            ps.setLong(2, treatmentRoomId);
            ps.setString(3, seatCode);
            ps.setString(4, seatName);
            ps.setInt(5, displayOrder);
            return ps;
        }, keyHolder);
        Number id = generatedId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("자리 등록 결과를 확인할 수 없습니다.");
        }
        return findById(instCode, id.longValue());
    }

    public TreatmentSeat updateSeat(
            String instCode,
            Long id,
            Long treatmentRoomId,
            String seatCode,
            String seatName,
            int displayOrder) {
        String sql = """
                UPDATE ct_treatment_seat
                SET treatment_room_id = ?, seat_code = ?, seat_name = ?, display_order = ?
                WHERE inst_code = ? AND id = ? AND active_yn = 'Y'
                """;
        int updated = jdbcTemplate.update(sql, treatmentRoomId, seatCode, seatName, displayOrder, instCode, id);
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 자리를 찾을 수 없습니다.");
        }
        return findById(instCode, id);
    }

    public void deactivateSeat(String instCode, Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_treatment_seat SET active_yn = 'N' WHERE inst_code = ? AND id = ? AND active_yn = 'Y'",
                instCode,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("삭제할 자리를 찾을 수 없습니다.");
        }
    }

    private TreatmentSeat findById(String instCode, Long id) {
        String sql = SELECT_COLUMNS + " WHERE s.inst_code = ? AND s.id = ? AND s.active_yn = 'Y'";
        return jdbcTemplate.query(sql, rowMapper, instCode, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장된 자리를 찾을 수 없습니다."));
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
