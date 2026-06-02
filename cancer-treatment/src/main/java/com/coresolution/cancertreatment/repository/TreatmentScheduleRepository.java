package com.coresolution.cancertreatment.repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.TreatmentSchedule;

/**
 * Persistence for cancer-treatment schedules. Every query is scoped by inst_code
 * for tenant isolation. Patient name/ward are joined live from ct_patient when the
 * schedule is linked (patient_id); otherwise the stored snapshot is used.
 */
@Repository
public class TreatmentScheduleRepository {

    private static final String SELECT_SQL = """
            SELECT s.id, s.patient_id, s.treatment_date, s.start_time, s.status_code,
                   s.treatment_name_snapshot, s.treatment_option_snapshot,
                   s.patient_name_snapshot, s.ward AS schedule_ward,
                   s.treatment_info, s.note,
                   p.patient_name AS live_patient_name, p.ward AS live_ward, p.active_yn AS patient_active
            FROM ct_treatment_schedule s
            LEFT JOIN ct_patient p ON p.id = s.patient_id AND p.inst_code = s.inst_code
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TreatmentSchedule> rowMapper = (rs, rowNum) -> {
        Long patientId = (Long) rs.getObject("patient_id");
        boolean patientActive = "Y".equalsIgnoreCase(rs.getString("patient_active"));
        String liveName = rs.getString("live_patient_name");
        String liveWard = rs.getString("live_ward");
        String snapshotName = rs.getString("patient_name_snapshot");
        String scheduleWard = rs.getString("schedule_ward");

        String displayName = (patientActive && StringUtils.hasText(liveName)) ? liveName : snapshotName;
        String displayWard = (patientActive && StringUtils.hasText(liveWard)) ? liveWard : scheduleWard;

        return new TreatmentSchedule(
                rs.getLong("id"),
                patientId,
                rs.getDate("treatment_date").toLocalDate().toString(),
                formatTime(rs.getTime("start_time")),
                blankToEmpty(displayName),
                blankToEmpty(displayWard),
                blankToEmpty(rs.getString("treatment_name_snapshot")),
                blankToEmpty(rs.getString("treatment_option_snapshot")),
                statusToKorean(rs.getString("status_code")),
                blankToEmpty(rs.getString("treatment_info")),
                blankToEmpty(rs.getString("note")));
    };

    public TreatmentScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TreatmentSchedule> findSchedules(
            String instCode, String date, String keyword, String treatmentName, String status) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_SQL).append(" WHERE s.inst_code = ? ");
        args.add(instCode);

        if (StringUtils.hasText(date)) {
            sql.append("AND s.treatment_date = ? ");
            args.add(Date.valueOf(LocalDate.parse(date.trim())));
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append("""
                    AND (
                        LOWER(COALESCE(p.patient_name, s.patient_name_snapshot)) LIKE ?
                        OR LOWER(COALESCE(p.ward, s.ward, '')) LIKE ?
                    )
                    """);
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(treatmentName)) {
            sql.append("AND s.treatment_name_snapshot = ? ");
            args.add(treatmentName.trim());
        }
        if (StringUtils.hasText(status)) {
            sql.append("AND s.status_code = ? ");
            args.add(koreanToStatus(status.trim()));
        }
        sql.append("ORDER BY s.treatment_date ASC, s.start_time ASC, s.id ASC");
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    public Optional<TreatmentSchedule> findById(String instCode, Long id) {
        String sql = SELECT_SQL + " WHERE s.inst_code = ? AND s.id = ?";
        return jdbcTemplate.query(sql, rowMapper, instCode, id).stream().findFirst();
    }

    public TreatmentSchedule create(
            String instCode, Long patientId, String treatmentDate, String startTime,
            String patientNameSnapshot, String ward, String treatmentName, String treatmentOption,
            String koreanStatus, String treatmentInfo, String note) {
        Long linkedPatientId = resolveLinkedPatientId(instCode, patientId);
        String sql = """
                INSERT INTO ct_treatment_schedule (
                    inst_code, patient_id, treatment_date, start_time, status_code, ward,
                    patient_name_snapshot, treatment_name_snapshot, treatment_option_snapshot,
                    treatment_info, note
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instCode);
            setNullableLong(ps, 2, linkedPatientId);
            ps.setDate(3, Date.valueOf(LocalDate.parse(treatmentDate)));
            ps.setTime(4, parseTime(startTime));
            ps.setString(5, koreanToStatus(koreanStatus));
            ps.setString(6, blankToNull(ward));
            ps.setString(7, patientNameSnapshot);
            ps.setString(8, treatmentName);
            ps.setString(9, blankToNull(treatmentOption));
            ps.setString(10, blankToNull(treatmentInfo));
            ps.setString(11, blankToNull(note));
            return ps;
        }, keyHolder);

        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new IllegalStateException("스케줄 등록 결과를 확인할 수 없습니다.");
        }
        return findById(instCode, key.longValue())
                .orElseThrow(() -> new IllegalStateException("등록된 스케줄을 찾을 수 없습니다."));
    }

    public TreatmentSchedule update(
            String instCode, Long id, Long patientId, String treatmentDate, String startTime,
            String patientNameSnapshot, String ward, String treatmentName, String treatmentOption,
            String koreanStatus, String treatmentInfo, String note) {
        Long linkedPatientId = resolveLinkedPatientId(instCode, patientId);
        String sql = """
                UPDATE ct_treatment_schedule
                SET patient_id = ?, treatment_date = ?, start_time = ?, status_code = ?, ward = ?,
                    patient_name_snapshot = ?, treatment_name_snapshot = ?, treatment_option_snapshot = ?,
                    treatment_info = ?, note = ?
                WHERE inst_code = ? AND id = ?
                """;
        int updated = jdbcTemplate.update(
                sql,
                linkedPatientId,
                Date.valueOf(LocalDate.parse(treatmentDate)),
                parseTime(startTime),
                koreanToStatus(koreanStatus),
                blankToNull(ward),
                patientNameSnapshot,
                treatmentName,
                blankToNull(treatmentOption),
                blankToNull(treatmentInfo),
                blankToNull(note),
                instCode,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("치료 스케줄을 찾을 수 없습니다.");
        }
        return requireById(instCode, id);
    }

    public TreatmentSchedule updateStatus(String instCode, Long id, String koreanStatus) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_treatment_schedule SET status_code = ? WHERE inst_code = ? AND id = ?",
                koreanToStatus(koreanStatus), instCode, id);
        if (updated == 0) {
            throw new IllegalArgumentException("치료 스케줄을 찾을 수 없습니다.");
        }
        return requireById(instCode, id);
    }

    public TreatmentSchedule updateStartTime(String instCode, Long id, String startTime) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_treatment_schedule SET start_time = ? WHERE inst_code = ? AND id = ?",
                parseTime(startTime), instCode, id);
        if (updated == 0) {
            throw new IllegalArgumentException("치료 스케줄을 찾을 수 없습니다.");
        }
        return requireById(instCode, id);
    }

    public TreatmentSchedule updateTextField(String instCode, Long id, String column, String value) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_treatment_schedule SET " + column + " = ? WHERE inst_code = ? AND id = ?",
                blankToNull(value), instCode, id);
        if (updated == 0) {
            throw new IllegalArgumentException("치료 스케줄을 찾을 수 없습니다.");
        }
        return requireById(instCode, id);
    }

    public void delete(String instCode, Long id) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM ct_treatment_schedule WHERE inst_code = ? AND id = ?", instCode, id);
        if (deleted == 0) {
            throw new IllegalArgumentException("치료 스케줄을 찾을 수 없습니다.");
        }
    }

    private TreatmentSchedule requireById(String instCode, Long id) {
        return findById(instCode, id)
                .orElseThrow(() -> new IllegalStateException("수정된 스케줄을 찾을 수 없습니다."));
    }

    /** Returns patientId only when it belongs to instCode; otherwise null (tenant isolation). */
    private Long resolveLinkedPatientId(String instCode, Long patientId) {
        if (patientId == null) {
            return null;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ct_patient WHERE id = ? AND inst_code = ?",
                Integer.class, patientId, instCode);
        return (count != null && count > 0) ? patientId : null;
    }

    private static String statusToKorean(String code) {
        if (code == null) {
            return "예약";
        }
        return switch (code) {
            case "COMPLETED" -> "치료완료";
            case "CANCELED" -> "예약취소";
            default -> "예약";
        };
    }

    private static String koreanToStatus(String korean) {
        if (korean == null) {
            return "RESERVED";
        }
        return switch (korean.trim()) {
            case "치료완료" -> "COMPLETED";
            case "예약취소" -> "CANCELED";
            default -> "RESERVED";
        };
    }

    private static Time parseTime(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        return Time.valueOf(time.withSecond(0).withNano(0));
    }

    private static String formatTime(Time time) {
        if (time == null) {
            return "";
        }
        LocalTime local = time.toLocalTime();
        return String.format("%02d:%02d", local.getHour(), local.getMinute());
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
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

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
