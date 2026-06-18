package com.coresolution.cancertreatment.repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ScheduleRecurrenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ScheduleRecurrenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long createRule(
            String instCode, Long treatmentRoomId, Long seatId, Long patientId,
            int weekdayMask, String startDate, int occurrenceCount, String startTime,
            String treatmentName, String treatmentOption, String treatmentInfo, String note,
            String createdBy) {
        String sql = """
                INSERT INTO ct_schedule_recurrence (
                    inst_code, treatment_room_id, seat_id, patient_id, weekday_mask,
                    start_date, occurrence_count, start_time,
                    treatment_name_snapshot, treatment_option_snapshot, treatment_info, note, created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instCode);
            setNullableLong(ps, 2, treatmentRoomId);
            setNullableLong(ps, 3, seatId);
            setNullableLong(ps, 4, patientId);
            ps.setInt(5, weekdayMask);
            ps.setDate(6, Date.valueOf(LocalDate.parse(startDate)));
            ps.setInt(7, occurrenceCount);
            ps.setTime(8, Time.valueOf(LocalTime.parse(startTime).withSecond(0).withNano(0)));
            ps.setString(9, treatmentName);
            ps.setString(10, blankToNull(treatmentOption));
            ps.setString(11, blankToNull(treatmentInfo));
            ps.setString(12, blankToNull(note));
            ps.setString(13, blankToNull(createdBy));
            return ps;
        }, keyHolder);
        Number id = generatedId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("반복 규칙 저장 결과를 확인할 수 없습니다.");
        }
        return id.longValue();
    }

    private Number generatedId(KeyHolder keyHolder) {
        List<Map<String, Object>> keyList = keyHolder.getKeyList();
        if (keyList.isEmpty()) {
            return null;
        }
        Map<String, Object> keys = keyList.get(0);
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

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
