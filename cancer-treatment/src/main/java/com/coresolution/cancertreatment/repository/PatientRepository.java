package com.coresolution.cancertreatment.repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
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

import com.coresolution.cancertreatment.model.Patient;

@Repository
public class PatientRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Patient> rowMapper = (rs, rowNum) -> new Patient(
            rs.getLong("id"),
            rs.getString("patient_name"),
            rs.getString("chart_no"),
            rs.getString("room"),
            rs.getString("ward"),
            toStringDate(rs.getDate("admission_date")),
            toStringDate(rs.getDate("discharge_date")),
            rs.getString("treatment_info"),
            rs.getString("note"));

    public PatientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Patient> findPatients(String instCode, String keyword, String ward) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, patient_name, chart_no, room, ward, admission_date, discharge_date, treatment_info, note
                FROM ct_patient
                WHERE inst_code = ? AND active_yn = 'Y'
                """);
        args.add(instCode);

        if (StringUtils.hasText(keyword)) {
            String likeKeyword = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append("""
                    AND (
                        LOWER(patient_name) LIKE ?
                        OR LOWER(COALESCE(chart_no, '')) LIKE ?
                        OR LOWER(COALESCE(room, '')) LIKE ?
                    )
                    """);
            args.add(likeKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }

        if (StringUtils.hasText(ward)) {
            sql.append("AND ward = ? ");
            args.add(ward.trim());
        }

        sql.append("ORDER BY patient_name ASC, id DESC");
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    public Patient createPatient(
            String instCode,
            String name,
            String chartNo,
            String room,
            String ward,
            LocalDate admissionDate,
            LocalDate dischargeDate,
            String treatmentInfo,
            String note) {
        String sql = """
                INSERT INTO ct_patient (
                    inst_code, chart_no, patient_name, room, ward,
                    admission_date, discharge_date, treatment_info, note
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instCode);
            ps.setString(2, blankToNull(chartNo));
            ps.setString(3, name);
            ps.setString(4, blankToNull(room));
            ps.setString(5, blankToNull(ward));
            ps.setDate(6, toSqlDate(admissionDate));
            ps.setDate(7, toSqlDate(dischargeDate));
            ps.setString(8, blankToNull(treatmentInfo));
            ps.setString(9, blankToNull(note));
            return ps;
        }, keyHolder);

        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new IllegalStateException("환자 등록 결과를 확인할 수 없습니다.");
        }
        return findById(instCode, key.longValue())
                .orElseThrow(() -> new IllegalStateException("등록된 환자를 찾을 수 없습니다."));
    }

    public Patient updateTextField(String instCode, Long id, String field, String value) {
        String column = switch (field) {
            case "name" -> "patient_name";
            case "chartNo" -> "chart_no";
            case "room" -> "room";
            case "ward" -> "ward";
            case "admissionDate" -> "admission_date";
            case "dischargeDate" -> "discharge_date";
            case "treatmentInfo" -> "treatment_info";
            case "note" -> "note";
            default -> throw new IllegalArgumentException("수정할 수 없는 항목입니다.");
        };
        Object bindValue = switch (field) {
            case "admissionDate", "dischargeDate" -> toSqlDate(StringUtils.hasText(value) ? LocalDate.parse(value) : null);
            default -> blankToNull(value);
        };
        int updated = jdbcTemplate.update(
                "UPDATE ct_patient SET " + column + " = ? WHERE inst_code = ? AND id = ? AND active_yn = 'Y'",
                bindValue,
                instCode,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 환자를 찾을 수 없습니다.");
        }
        return findById(instCode, id)
                .orElseThrow(() -> new IllegalStateException("수정된 환자를 찾을 수 없습니다."));
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

    private Optional<Patient> findById(String instCode, Long id) {
        String sql = """
                SELECT id, patient_name, chart_no, room, ward, admission_date, discharge_date, treatment_info, note
                FROM ct_patient
                WHERE inst_code = ? AND id = ? AND active_yn = 'Y'
                """;
        return jdbcTemplate.query(sql, rowMapper, instCode, id).stream().findFirst();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Date toSqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private String toStringDate(Date date) {
        return date == null ? "" : date.toLocalDate().toString();
    }
}
