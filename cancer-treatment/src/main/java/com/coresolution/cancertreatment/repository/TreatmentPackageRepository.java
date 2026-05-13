package com.coresolution.cancertreatment.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.coresolution.cancertreatment.model.TreatmentPackage;

@Repository
public class TreatmentPackageRepository {

    private static final String SELECT_BASE = """
            SELECT p.id, p.category_id, c.category_name,
                   p.treatment_room_id, r.room_name AS treatment_room_name,
                   p.package_name, p.abbreviation, p.unit_price, p.billing_unit, p.frequency, p.display_order
            FROM ct_treatment_package p
            JOIN ct_package_category c ON c.id = p.category_id
            JOIN ct_treatment_room   r ON r.id = p.treatment_room_id
            WHERE p.inst_code = ? AND p.active_yn = 'Y'
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TreatmentPackage> rowMapper = (rs, rowNum) -> new TreatmentPackage(
            rs.getLong("id"),
            rs.getLong("category_id"),
            rs.getString("category_name"),
            rs.getLong("treatment_room_id"),
            rs.getString("treatment_room_name"),
            rs.getString("package_name"),
            rs.getString("abbreviation"),
            rs.getInt("unit_price"),
            rs.getString("billing_unit"),
            rs.getInt("frequency"),
            rs.getInt("display_order"));

    public TreatmentPackageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TreatmentPackage> findPackages(String instCode, Long treatmentRoomId, Long categoryId) {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(instCode);
        if (treatmentRoomId != null) {
            sql.append(" AND p.treatment_room_id = ?");
            args.add(treatmentRoomId);
        }
        if (categoryId != null) {
            sql.append(" AND p.category_id = ?");
            args.add(categoryId);
        }
        sql.append(" ORDER BY c.display_order ASC, r.display_order ASC, p.display_order ASC, p.id ASC");
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    public TreatmentPackage findById(String instCode, Long id) {
        return jdbcTemplate.query(SELECT_BASE + " AND p.id = ?", rowMapper, instCode, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("등록된 치료 항목을 찾을 수 없습니다."));
    }

    public TreatmentPackage createPackage(
            String instCode,
            Long categoryId,
            Long treatmentRoomId,
            String packageName,
            String abbreviation,
            int unitPrice,
            String billingUnit,
            int frequency,
            int displayOrder) {
        String sql = """
                INSERT INTO ct_treatment_package (
                    inst_code, category_id, treatment_room_id, package_name, abbreviation,
                    unit_price, billing_unit, frequency, display_order
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, instCode);
                ps.setLong(2, categoryId);
                ps.setLong(3, treatmentRoomId);
                ps.setString(4, packageName);
                ps.setString(5, abbreviation);
                ps.setInt(6, unitPrice);
                ps.setString(7, billingUnit);
                ps.setInt(8, frequency);
                ps.setInt(9, displayOrder);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("같은 치료실에 동일한 항목명이 이미 있습니다.");
        }
        Long id = extractId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("치료 항목 등록 결과를 확인할 수 없습니다.");
        }
        return findById(instCode, id);
    }

    public TreatmentPackage updatePackage(
            String instCode,
            Long id,
            Long categoryId,
            Long treatmentRoomId,
            String packageName,
            String abbreviation,
            int unitPrice,
            String billingUnit,
            int frequency,
            int displayOrder) {
        String sql = """
                UPDATE ct_treatment_package
                SET category_id = ?, treatment_room_id = ?, package_name = ?, abbreviation = ?,
                    unit_price = ?, billing_unit = ?, frequency = ?, display_order = ?
                WHERE inst_code = ? AND id = ? AND active_yn = 'Y'
                """;
        int updated;
        try {
            updated = jdbcTemplate.update(
                    sql,
                    categoryId,
                    treatmentRoomId,
                    packageName,
                    abbreviation,
                    unitPrice,
                    billingUnit,
                    frequency,
                    displayOrder,
                    instCode,
                    id);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("같은 치료실에 동일한 항목명이 이미 있습니다.");
        }
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 치료 항목을 찾을 수 없습니다.");
        }
        return findById(instCode, id);
    }

    public void deactivatePackage(String instCode, Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_treatment_package SET active_yn = 'N' WHERE inst_code = ? AND id = ? AND active_yn = 'Y'",
                instCode,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("삭제할 치료 항목을 찾을 수 없습니다.");
        }
    }

    private Long extractId(KeyHolder keyHolder) {
        if (keyHolder.getKeyList().isEmpty()) {
            return null;
        }
        Map<String, Object> keys = keyHolder.getKeyList().get(0);
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number n) {
                return n.longValue();
            }
        }
        return keys.values().stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .findFirst()
                .orElse(null);
    }
}
