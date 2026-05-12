package com.coresolution.cancertreatment.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.TreatmentRoom;

@Repository
public class TreatmentRoomRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TreatmentRoomRow> rowMapper = (rs, rowNum) -> new TreatmentRoomRow(
            rs.getLong("id"),
            rs.getString("management_no"),
            rs.getString("room_name"),
            rs.getString("treatment_item"),
            rs.getString("manager_name"),
            rs.getString("note"),
            rs.getInt("display_order"));

    public TreatmentRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TreatmentRoom> findRooms(String instCode) {
        String sql = """
                SELECT id, management_no, room_name, treatment_item, manager_name, note, display_order
                FROM ct_treatment_room
                WHERE inst_code = ? AND active_yn = 'Y'
                ORDER BY display_order ASC, management_no ASC, id ASC
                """;
        return hydrateItems(jdbcTemplate.query(sql, rowMapper, instCode));
    }

    public TreatmentRoom createRoom(
            String instCode,
            String managementNo,
            String roomName,
            List<String> treatmentItems,
            String managerName,
            String note,
            int displayOrder) {
        String sql = """
                INSERT INTO ct_treatment_room (
                    inst_code, management_no, room_name, treatment_item, manager_name, note, display_order
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instCode);
            ps.setString(2, managementNo);
            ps.setString(3, roomName);
            ps.setString(4, firstItem(treatmentItems));
            ps.setString(5, blankToNull(managerName));
            ps.setString(6, blankToNull(note));
            ps.setInt(7, displayOrder);
            return ps;
        }, keyHolder);
        Number id = generatedId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("치료실 등록 결과를 확인할 수 없습니다.");
        }
        replaceItems(id.longValue(), treatmentItems);
        return findById(instCode, id.longValue());
    }

    public TreatmentRoom updateRoom(
            String instCode,
            Long id,
            String managementNo,
            String roomName,
            List<String> treatmentItems,
            String managerName,
            String note,
            int displayOrder) {
        String sql = """
                UPDATE ct_treatment_room
                SET management_no = ?, room_name = ?, treatment_item = ?, manager_name = ?, note = ?, display_order = ?
                WHERE inst_code = ? AND id = ? AND active_yn = 'Y'
                """;
        int updated = jdbcTemplate.update(
                sql,
                managementNo,
                roomName,
                firstItem(treatmentItems),
                blankToNull(managerName),
                blankToNull(note),
                displayOrder,
                instCode,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 치료실을 찾을 수 없습니다.");
        }
        replaceItems(id, treatmentItems);
        return findById(instCode, id);
    }

    public void deactivateRoom(String instCode, Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_treatment_room SET active_yn = 'N' WHERE inst_code = ? AND id = ? AND active_yn = 'Y'",
                instCode,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("삭제할 치료실을 찾을 수 없습니다.");
        }
    }

    private TreatmentRoom findById(String instCode, Long id) {
        String sql = """
                SELECT id, management_no, room_name, treatment_item, manager_name, note, display_order
                FROM ct_treatment_room
                WHERE inst_code = ? AND id = ? AND active_yn = 'Y'
                """;
        return hydrateItems(jdbcTemplate.query(sql, rowMapper, instCode, id))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장된 치료실을 찾을 수 없습니다."));
    }

    private List<TreatmentRoom> hydrateItems(List<TreatmentRoomRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> itemMap = new LinkedHashMap<>();
        for (TreatmentRoomRow row : rows) {
            itemMap.put(row.id(), new ArrayList<>());
        }
        String placeholders = String.join(",", rows.stream().map(row -> "?").toList());
        String sql = "SELECT treatment_room_id, treatment_item FROM ct_treatment_room_item "
                + "WHERE active_yn = 'Y' AND treatment_room_id IN (" + placeholders + ") "
                + "ORDER BY treatment_room_id ASC, display_order ASC, id ASC";
        jdbcTemplate.query(sql, rs -> {
            Long roomId = rs.getLong("treatment_room_id");
            List<String> items = itemMap.get(roomId);
            if (items != null) {
                items.add(rs.getString("treatment_item"));
            }
        }, rows.stream().map(TreatmentRoomRow::id).toArray());

        return rows.stream()
                .map(row -> {
                    List<String> items = itemMap.getOrDefault(row.id(), List.of());
                    if (items.isEmpty() && StringUtils.hasText(row.legacyTreatmentItem())) {
                        items = List.of(row.legacyTreatmentItem());
                    }
                    return new TreatmentRoom(
                            row.id(),
                            row.managementNo(),
                            row.roomName(),
                            items,
                            row.managerName(),
                            row.note(),
                            row.displayOrder());
                })
                .toList();
    }

    private void replaceItems(Long roomId, List<String> treatmentItems) {
        jdbcTemplate.update("DELETE FROM ct_treatment_room_item WHERE treatment_room_id = ?", roomId);
        for (int index = 0; index < treatmentItems.size(); index++) {
            jdbcTemplate.update("""
                    INSERT INTO ct_treatment_room_item (treatment_room_id, treatment_item, display_order)
                    VALUES (?, ?, ?)
                    """, roomId, treatmentItems.get(index), index);
        }
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

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstItem(List<String> treatmentItems) {
        return treatmentItems.isEmpty() ? null : treatmentItems.get(0);
    }

    private record TreatmentRoomRow(
            Long id,
            String managementNo,
            String roomName,
            String legacyTreatmentItem,
            String managerName,
            String note,
            Integer displayOrder) {
    }
}
