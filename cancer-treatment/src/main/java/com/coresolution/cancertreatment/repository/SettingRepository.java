package com.coresolution.cancertreatment.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.coresolution.cancertreatment.model.SettingItem;

@Repository
public class SettingRepository {

    private final JdbcTemplate jdbcTemplate;

    public SettingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SettingItem> findItems(SettingCategory category, String instCode) {
        return jdbcTemplate.query(category.selectSql(), category.rowMapper(), instCode);
    }

    public SettingItem createItem(
            SettingCategory category,
            String instCode,
            String code,
            String name,
            String detail,
            String color,
            int displayOrder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(category.insertSql(), Statement.RETURN_GENERATED_KEYS);
            category.bindInsert(ps, instCode, code, name, detail, color, displayOrder);
            return ps;
        }, keyHolder);
        Number id = generatedId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("설정 등록 결과를 확인할 수 없습니다.");
        }
        return findById(category, instCode, id.longValue());
    }

    public SettingItem updateItem(
            SettingCategory category,
            String instCode,
            Long id,
            String code,
            String name,
            String detail,
            String color,
            int displayOrder) {
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(category.updateSql());
            category.bindUpdate(ps, code, name, detail, color, displayOrder, instCode, id);
            return ps;
        });
        if (updated == 0) {
            throw new IllegalArgumentException("수정할 설정을 찾을 수 없습니다.");
        }
        return findById(category, instCode, id);
    }

    public void deactivateItem(SettingCategory category, String instCode, Long id) {
        int updated = jdbcTemplate.update(category.deleteSql(), instCode, id);
        if (updated == 0) {
            throw new IllegalArgumentException("삭제할 설정을 찾을 수 없습니다.");
        }
    }

    private SettingItem findById(SettingCategory category, String instCode, Long id) {
        return jdbcTemplate.query(category.findByIdSql(), category.rowMapper(), instCode, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장된 설정을 찾을 수 없습니다."));
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

    public enum SettingCategory {
        TREATMENT_TYPES(
                "ct_treatment_type",
                "treatment_name",
                "treatment_name",
                "room_name",
                false),
        TREATMENT_OPTIONS(
                "ct_treatment_option",
                "option_code",
                "option_name",
                null,
                "option_color",
                false),
        TREATMENT_STATUSES(
                "ct_treatment_status",
                "status_code",
                "status_name",
                null,
                false),
        TIME_SLOTS(
                "ct_time_slot",
                "start_time",
                "start_time",
                null,
                true),
        WARDS(
                "ct_ward",
                "ward_code",
                "ward_name",
                null,
                false),
        PACKAGE_CATEGORIES(
                "ct_package_category",
                "category_code",
                "category_name",
                null,
                false);

        private final String table;
        private final String codeColumn;
        private final String nameColumn;
        private final String detailColumn;
        private final String colorColumn;
        private final boolean timeValue;

        SettingCategory(String table, String codeColumn, String nameColumn, String detailColumn, boolean timeValue) {
            this(table, codeColumn, nameColumn, detailColumn, null, timeValue);
        }

        SettingCategory(
                String table,
                String codeColumn,
                String nameColumn,
                String detailColumn,
                String colorColumn,
                boolean timeValue) {
            this.table = table;
            this.codeColumn = codeColumn;
            this.nameColumn = nameColumn;
            this.detailColumn = detailColumn;
            this.colorColumn = colorColumn;
            this.timeValue = timeValue;
        }

        public static SettingCategory fromKey(String key) {
            return switch (key) {
                case "treatment-types" -> TREATMENT_TYPES;
                case "treatment-options" -> TREATMENT_OPTIONS;
                case "treatment-statuses" -> TREATMENT_STATUSES;
                case "time-slots" -> TIME_SLOTS;
                case "wards" -> WARDS;
                case "package-categories" -> PACKAGE_CATEGORIES;
                default -> throw new IllegalArgumentException("지원하지 않는 설정 유형입니다.");
            };
        }

        String selectSql() {
            return baseSelect() + " WHERE inst_code = ? AND active_yn = 'Y' ORDER BY display_order ASC, id ASC";
        }

        String findByIdSql() {
            return baseSelect() + " WHERE inst_code = ? AND id = ? AND active_yn = 'Y'";
        }

        String insertSql() {
            if (timeValue) {
                return "INSERT INTO " + table + " (inst_code, " + codeColumn + ", display_order) VALUES (?, ?, ?)";
            }
            if (codeColumn.equals(nameColumn)) {
                String detail = detailColumn == null ? "" : ", " + detailColumn;
                String value = detailColumn == null ? "" : ", ?";
                return "INSERT INTO " + table + " (inst_code, " + nameColumn + detail
                        + ", display_order) VALUES (?, ?" + value + ", ?)";
            }
            String detail = detailColumn == null ? "" : ", " + detailColumn;
            String color = colorColumn == null ? "" : ", " + colorColumn;
            String value = (detailColumn == null ? "" : ", ?") + (colorColumn == null ? "" : ", ?");
            return "INSERT INTO " + table + " (inst_code, " + codeColumn + ", " + nameColumn + detail + color
                    + ", display_order) VALUES (?, ?, ?" + value + ", ?)";
        }

        String updateSql() {
            if (timeValue) {
                return "UPDATE " + table + " SET " + codeColumn + " = ?, display_order = ? WHERE inst_code = ? AND id = ? AND active_yn = 'Y'";
            }
            if (codeColumn.equals(nameColumn)) {
                String detail = detailColumn == null ? "" : ", " + detailColumn + " = ?";
                return "UPDATE " + table + " SET " + nameColumn + " = ?" + detail
                        + ", display_order = ? WHERE inst_code = ? AND id = ? AND active_yn = 'Y'";
            }
            String detail = detailColumn == null ? "" : ", " + detailColumn + " = ?";
            String color = colorColumn == null ? "" : ", " + colorColumn + " = ?";
            return "UPDATE " + table + " SET " + codeColumn + " = ?, " + nameColumn + " = ?" + detail + color
                    + ", display_order = ? WHERE inst_code = ? AND id = ? AND active_yn = 'Y'";
        }

        String deleteSql() {
            return "UPDATE " + table + " SET active_yn = 'N' WHERE inst_code = ? AND id = ? AND active_yn = 'Y'";
        }

        RowMapper<SettingItem> rowMapper() {
            return (rs, rowNum) -> {
                String code = timeValue ? rs.getTime("code").toLocalTime().toString() : rs.getString("code");
                String name = timeValue ? code : rs.getString("name");
                return new SettingItem(
                        rs.getLong("id"),
                        code,
                        name,
                        rs.getString("detail"),
                        rs.getString("color"),
                        rs.getInt("display_order"));
            };
        }

        void bindInsert(
                PreparedStatement ps,
                String instCode,
                String code,
                String name,
                String detail,
                String color,
                int displayOrder)
                throws java.sql.SQLException {
            ps.setString(1, instCode);
            if (timeValue) {
                ps.setTime(2, Time.valueOf(LocalTime.parse(code)));
                ps.setInt(3, displayOrder);
                return;
            }
            if (codeColumn.equals(nameColumn)) {
                ps.setString(2, name);
                if (detailColumn == null) {
                    ps.setInt(3, displayOrder);
                } else {
                    ps.setString(3, detail);
                    ps.setInt(4, displayOrder);
                }
                return;
            }
            ps.setString(2, code);
            ps.setString(3, name);
            int index = 4;
            if (detailColumn == null) {
                if (colorColumn != null) {
                    ps.setString(index++, color);
                }
                ps.setInt(index, displayOrder);
            } else {
                ps.setString(index++, detail);
                if (colorColumn != null) {
                    ps.setString(index++, color);
                }
                ps.setInt(index, displayOrder);
            }
        }

        void bindUpdate(
                PreparedStatement ps,
                String code,
                String name,
                String detail,
                String color,
                int displayOrder,
                String instCode,
                Long id)
                throws java.sql.SQLException {
            int index = 1;
            if (timeValue) {
                ps.setTime(index++, Time.valueOf(LocalTime.parse(code)));
                ps.setInt(index++, displayOrder);
            } else {
                if (!codeColumn.equals(nameColumn)) {
                    ps.setString(index++, code);
                }
                ps.setString(index++, name);
                if (detailColumn != null) {
                    ps.setString(index++, detail);
                }
                if (colorColumn != null) {
                    ps.setString(index++, color);
                }
                ps.setInt(index++, displayOrder);
            }
            ps.setString(index++, instCode);
            ps.setLong(index, id);
        }

        private String baseSelect() {
            String detail = detailColumn == null ? "NULL" : detailColumn;
            String color = colorColumn == null ? "NULL" : colorColumn;
            return "SELECT id, " + codeColumn + " AS code, " + nameColumn + " AS name, " + detail
                    + " AS detail, " + color + " AS color, display_order FROM " + table;
        }
    }
}
