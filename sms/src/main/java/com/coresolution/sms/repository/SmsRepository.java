package com.coresolution.sms.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.coresolution.sms.model.HistoryQuery;
import com.coresolution.sms.model.InstPrice;

/**
 * Data access over the shared csm MySQL schema. All per-institution tables
 * (transmission_history_&lt;inst&gt;) are interpolated by name, so every method that
 * builds such a name MUST run the value through {@link #safeInst(String)} first.
 *
 * SQL ported from csm's SmsService so cost/history numbers match exactly. Because both
 * CounselMan and this app write to the same csm.transmission_history_&lt;inst&gt; tables,
 * the usage counts here inherently include CounselMan-originated sends.
 */
@Repository
public class SmsRepository {

    private final JdbcTemplate jdbcTemplate;

    public SmsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records one send attempt into csm.transmission_history_&lt;inst&gt;. */
    public int insertTransmissionHistory(
            String inst,
            String contents,
            String fromPhone,
            String toPhone,
            String status,
            String responseString,
            String refkey,
            String sendType,
            LocalDateTime reserveTime) {
        String t = safeInst(inst);
        String sql = "INSERT INTO csm.transmission_history_" + t
                + " (contents, from_phone, to_phone, status, response, refkey, created_at, send_type, reserve_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW(), ?, ?)";
        return jdbcTemplate.update(sql, contents, fromPhone, toPhone, status, responseString, refkey, sendType,
                reserveTime);
    }

    public List<Map<String, Object>> selectTransmissionHistory(HistoryQuery query) {
        String t = safeInst(query.getInst());
        StringBuilder sql = new StringBuilder()
                .append("SELECT id, contents, from_phone, to_phone, status, response, created_at, send_type, reserve_time ")
                .append("FROM csm.transmission_history_").append(t).append(" WHERE 1=1 ");

        if (query.getFail() == null || query.getFail().isBlank()) {
            sql.append(" AND status IN ('SUCCESS', '전송완료', '전송중') ");
        }
        appendHistoryTypeFilter(sql, query.getType());
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            sql.append(" AND (to_phone LIKE ? OR from_phone LIKE ? OR contents LIKE ?) ");
            return jdbcTemplate.queryForList(sql + " ORDER BY created_at DESC LIMIT ?, ? ",
                    "%" + query.getKeyword() + "%", "%" + query.getKeyword() + "%", "%" + query.getKeyword() + "%",
                    query.getPageStart(), query.getPerPageNum());
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?, ? ");
        return jdbcTemplate.queryForList(sql.toString(), query.getPageStart(), query.getPerPageNum());
    }

    public int smsCnt(HistoryQuery query) {
        String t = safeInst(query.getInst());
        StringBuilder sql = new StringBuilder()
                .append("SELECT COUNT(*) FROM csm.transmission_history_").append(t).append(" WHERE 1=1 ");
        if (query.getFail() == null || query.getFail().isBlank()) {
            sql.append(" AND status IN ('SUCCESS', '전송완료', '전송중') ");
        }
        appendHistoryTypeFilter(sql, query.getType());
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            sql.append(" AND (to_phone LIKE ? OR from_phone LIKE ? OR contents LIKE ?) ");
            Integer n = jdbcTemplate.queryForObject(sql.toString(), Integer.class,
                    "%" + query.getKeyword() + "%", "%" + query.getKeyword() + "%", "%" + query.getKeyword() + "%");
            return n == null ? 0 : n;
        }
        Integer n = jdbcTemplate.queryForObject(sql.toString(), Integer.class);
        return n == null ? 0 : n;
    }

    /** Successful-send counts per send_type for the whole history of one institution. */
    public Map<String, Integer> getSendTypeUsage(String inst) {
        String t = safeInst(inst);
        String sql = "SELECT send_type, COUNT(*) AS count FROM csm.transmission_history_" + t
                + " WHERE status='SUCCESS' GROUP BY send_type";
        return toUsageMap(jdbcTemplate.queryForList(sql));
    }

    public Map<String, Integer> getSendTypeUsageByMonth(String inst, LocalDate date) {
        String t = safeInst(inst);
        String month = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String sql = "SELECT send_type, COUNT(*) AS count FROM csm.transmission_history_" + t
                + " WHERE status='SUCCESS' AND DATE_FORMAT(created_at, '%Y-%m')=? GROUP BY send_type";
        return toUsageMap(jdbcTemplate.queryForList(sql, month));
    }

    public List<Map<String, Object>> getMonthlyUsage(String inst) {
        String t = safeInst(inst);
        String sql = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS month, "
                + "SUM(CASE WHEN send_type='sms' THEN 1 ELSE 0 END) AS sms, "
                + "SUM(CASE WHEN send_type='lms' THEN 1 ELSE 0 END) AS lms, "
                + "SUM(CASE WHEN send_type='mms' THEN 1 ELSE 0 END) AS mms "
                + "FROM csm.transmission_history_" + t
                + " WHERE status='SUCCESS' GROUP BY DATE_FORMAT(created_at, '%Y-%m') ORDER BY month DESC";
        return jdbcTemplate.queryForList(sql);
    }

    /** Per-unit prices for one institution (from csm.inst_data_cs). */
    public InstPrice price(String inst) {
        String normalized = safeInst(inst);
        String sql = "SELECT sms_price, lms_price, mms_price FROM csm.inst_data_cs WHERE id_col_03 = ?";
        List<InstPrice> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new InstPrice(
                normalized,
                parseDouble(rs.getString("sms_price")),
                parseDouble(rs.getString("lms_price")),
                parseDouble(rs.getString("mms_price"))), normalized);
        return rows.isEmpty() ? InstPrice.zero(normalized) : rows.get(0);
    }

    /** Institution codes registered in csm.inst_data_cs — used for the platform-wide aggregate. */
    public List<String> listInstitutionCodes() {
        String sql = "SELECT id_col_03 FROM csm.inst_data_cs "
                + "WHERE id_col_03 IS NOT NULL AND id_col_03 <> '' ORDER BY id_col_03";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * Validates a dynamic institution code before it is interpolated into a table name.
     * Ported verbatim from csm SmsService — the ONLY guard against SQL injection on the
     * per-institution table suffix.
     */
    public String safeInst(String inst) {
        if (inst == null) {
            throw new IllegalArgumentException("inst is null");
        }
        String normalized = inst.trim();
        if (!normalized.matches("[A-Za-z0-9_]{2,20}")) {
            throw new IllegalArgumentException("Invalid inst: " + inst);
        }
        return normalized;
    }

    private void appendHistoryTypeFilter(StringBuilder sql, String typeRaw) {
        String type = typeRaw == null ? "" : typeRaw.trim().toLowerCase();
        if ("reserved".equals(type)) {
            sql.append(" AND reserve_time IS NOT NULL ");
        } else if ("sent".equals(type)) {
            sql.append(" AND reserve_time IS NULL ");
        }
    }

    private Map<String, Integer> toUsageMap(List<Map<String, Object>> rows) {
        Map<String, Integer> out = new java.util.HashMap<>();
        for (Map<String, Object> r : rows) {
            String type = Objects.toString(r.get("send_type"), "");
            Number count = (Number) r.get("count");
            out.put(type, count == null ? 0 : count.intValue());
        }
        return out;
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
