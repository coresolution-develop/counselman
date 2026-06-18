package com.coresolution.cancertreatment.repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.Patient;

@Repository
public class PatientRepository {

    private static final String SELECT_COLUMNS = """
            id, patient_name, chart_no, room, ward, attending_doctor,
            admission_date, treatment_start_date, treatment_info, note,
            prescription_weeks, copayment_rate, total_discount_type, total_discount_value
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<PatientRow> rowMapper = (rs, rowNum) -> new PatientRow(
            rs.getLong("id"),
            rs.getString("patient_name"),
            rs.getString("chart_no"),
            rs.getString("room"),
            rs.getString("ward"),
            rs.getString("attending_doctor"),
            toStringDate(rs.getDate("admission_date")),
            toStringDate(rs.getDate("treatment_start_date")),
            rs.getString("treatment_info"),
            rs.getString("note"),
            rs.getInt("prescription_weeks"),
            rs.getInt("copayment_rate"),
            rs.getString("total_discount_type"),
            rs.getInt("total_discount_value"));

    public PatientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Patient> findPatients(String instCode, String keyword, String ward) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(SELECT_COLUMNS)
                .append(" FROM ct_patient WHERE inst_code = ? AND active_yn = 'Y' ");
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
        List<PatientRow> rows = jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
        return hydrate(rows);
    }

    /** Distinct non-blank attending doctors — used as an autocomplete source (no separate master). */
    public List<String> findDistinctDoctors(String instCode) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT attending_doctor FROM ct_patient "
                        + "WHERE inst_code = ? AND active_yn = 'Y' "
                        + "AND attending_doctor IS NOT NULL AND attending_doctor <> '' "
                        + "ORDER BY attending_doctor ASC",
                String.class, instCode);
    }

    @Transactional
    public Patient createPatient(
            String instCode,
            String name,
            String chartNo,
            String room,
            String ward,
            String attendingDoctor,
            LocalDate admissionDate,
            LocalDate treatmentStartDate,
            String treatmentInfo,
            String note,
            int prescriptionWeeks,
            int copaymentRate,
            String totalDiscountType,
            int totalDiscountValue,
            List<Long> prescriptionItemIds) {
        String sql = """
                INSERT INTO ct_patient (
                    inst_code, chart_no, patient_name, room, ward, attending_doctor,
                    admission_date, treatment_start_date, treatment_info, note,
                    prescription_weeks, copayment_rate, total_discount_type, total_discount_value
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instCode);
            ps.setString(2, blankToNull(chartNo));
            ps.setString(3, name);
            ps.setString(4, blankToNull(room));
            ps.setString(5, blankToNull(ward));
            ps.setString(6, blankToNull(attendingDoctor));
            ps.setDate(7, toSqlDate(admissionDate));
            ps.setDate(8, toSqlDate(treatmentStartDate));
            ps.setString(9, blankToNull(treatmentInfo));
            ps.setString(10, blankToNull(note));
            ps.setInt(11, prescriptionWeeks);
            ps.setInt(12, copaymentRate);
            ps.setString(13, totalDiscountType);
            ps.setInt(14, totalDiscountValue);
            return ps;
        }, keyHolder);

        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new IllegalStateException("환자 등록 결과를 확인할 수 없습니다.");
        }
        Long patientId = key.longValue();
        replacePrescriptionItems(patientId, prescriptionItemIds);
        return findById(instCode, patientId)
                .orElseThrow(() -> new IllegalStateException("등록된 환자를 찾을 수 없습니다."));
    }

    @Transactional
    public Patient updatePatient(
            String instCode,
            Long id,
            String name,
            String chartNo,
            String room,
            String ward,
            String attendingDoctor,
            LocalDate admissionDate,
            LocalDate treatmentStartDate,
            String treatmentInfo,
            String note,
            int prescriptionWeeks,
            int copaymentRate,
            String totalDiscountType,
            int totalDiscountValue,
            List<Long> prescriptionItemIds) {
        String sql = """
                UPDATE ct_patient
                SET chart_no = ?, patient_name = ?, room = ?, ward = ?, attending_doctor = ?,
                    admission_date = ?, treatment_start_date = ?, treatment_info = ?, note = ?,
                    prescription_weeks = ?, copayment_rate = ?,
                    total_discount_type = ?, total_discount_value = ?
                WHERE inst_code = ? AND id = ? AND active_yn = 'Y'
                """;
        int updated = jdbcTemplate.update(
                sql,
                blankToNull(chartNo),
                name,
                blankToNull(room),
                blankToNull(ward),
                blankToNull(attendingDoctor),
                toSqlDate(admissionDate),
                toSqlDate(treatmentStartDate),
                blankToNull(treatmentInfo),
                blankToNull(note),
                prescriptionWeeks,
                copaymentRate,
                totalDiscountType,
                totalDiscountValue,
                instCode,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 환자를 찾을 수 없습니다.");
        }
        replacePrescriptionItems(id, prescriptionItemIds);
        return findById(instCode, id)
                .orElseThrow(() -> new IllegalStateException("수정된 환자를 찾을 수 없습니다."));
    }

    public Patient updateTextField(String instCode, Long id, String field, String value) {
        String column = switch (field) {
            case "name" -> "patient_name";
            case "chartNo" -> "chart_no";
            case "room" -> "room";
            case "ward" -> "ward";
            case "attendingDoctor" -> "attending_doctor";
            case "admissionDate" -> "admission_date";
            case "treatmentStartDate" -> "treatment_start_date";
            case "treatmentInfo" -> "treatment_info";
            case "note" -> "note";
            default -> throw new IllegalArgumentException("수정할 수 없는 항목입니다.");
        };
        Object bindValue = switch (field) {
            case "admissionDate", "treatmentStartDate" -> toSqlDate(StringUtils.hasText(value) ? LocalDate.parse(value) : null);
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

    private void replacePrescriptionItems(Long patientId, List<Long> packageIds) {
        jdbcTemplate.update("DELETE FROM ct_patient_prescription_item WHERE patient_id = ?", patientId);
        if (packageIds == null || packageIds.isEmpty()) {
            return;
        }
        for (Long packageId : packageIds) {
            if (packageId == null) continue;
            jdbcTemplate.update(
                    "INSERT INTO ct_patient_prescription_item (patient_id, package_id) VALUES (?, ?)",
                    patientId,
                    packageId);
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

    private Optional<Patient> findById(String instCode, Long id) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM ct_patient WHERE inst_code = ? AND id = ? AND active_yn = 'Y'";
        return hydrate(jdbcTemplate.query(sql, rowMapper, instCode, id)).stream().findFirst();
    }

    private List<Patient> hydrate(List<PatientRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> itemMap = new LinkedHashMap<>();
        for (PatientRow row : rows) {
            itemMap.put(row.id(), new ArrayList<>());
        }
        String placeholders = String.join(",", rows.stream().map(r -> "?").toList());
        String sql = "SELECT patient_id, package_id FROM ct_patient_prescription_item "
                + "WHERE patient_id IN (" + placeholders + ") ORDER BY id ASC";
        jdbcTemplate.query(sql, rs -> {
            Long patientId = rs.getLong("patient_id");
            List<Long> items = itemMap.get(patientId);
            if (items != null) {
                items.add(rs.getLong("package_id"));
            }
        }, rows.stream().map(PatientRow::id).toArray());

        return rows.stream()
                .map(row -> new Patient(
                        row.id(),
                        row.name(),
                        row.chartNo(),
                        row.room(),
                        row.ward(),
                        row.attendingDoctor(),
                        row.admissionDate(),
                        row.treatmentStartDate(),
                        row.treatmentInfo(),
                        row.note(),
                        row.prescriptionWeeks(),
                        row.copaymentRate(),
                        row.totalDiscountType(),
                        row.totalDiscountValue(),
                        itemMap.getOrDefault(row.id(), List.of())))
                .toList();
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

    private record PatientRow(
            Long id,
            String name,
            String chartNo,
            String room,
            String ward,
            String attendingDoctor,
            String admissionDate,
            String treatmentStartDate,
            String treatmentInfo,
            String note,
            Integer prescriptionWeeks,
            Integer copaymentRate,
            String totalDiscountType,
            Integer totalDiscountValue) {
    }
}
