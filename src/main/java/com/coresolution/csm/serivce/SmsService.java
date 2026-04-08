package com.coresolution.csm.serivce;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.coresolution.csm.mapper.SmsMapper;
import com.coresolution.csm.vo.Criteria;
import com.coresolution.csm.vo.Instdata;
import com.coresolution.csm.vo.SmsTemplate;

@Service
public class SmsService {

    @Autowired
    private SmsMapper mapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<SmsTemplate> SelectTemplateView(SmsTemplate st) {
        return mapper.SelectTemplateView(st);
    }

    public int SelectTemplateCnt(Criteria cri) {
        return mapper.SelectTemplateCnt(cri);
    }

    public List<SmsTemplate> SelectTemplate(Criteria cri) {
        return mapper.SelectTemplate(cri);
    }

    public int smsInsert(SmsTemplate st) {
        return mapper.smsInsert(st);
    }

    public int smsUpdate(SmsTemplate st) {
        return mapper.smsUpdate(st);
    }

    public int smsDelete(String inst, int id) {
        SmsTemplate st = new SmsTemplate();
        st.setInst(inst);
        st.setId(id);
        return mapper.smsDelete(st);
    }

    public int insertTransmissionHistory(
            String inst,
            String contents,
            String fromPhone,
            String toPhone,
            String status,
            String responseString,
            String refkey,
            String sendType) {
        return mapper.insertTransmissionHistory(inst, contents, fromPhone, toPhone, status, responseString, refkey, sendType);
    }

    public List<Map<String, Object>> getSmsLogs(String inst, List<String> toPhones) {
        return mapper.getSmsLogs(inst, toPhones);
    }

    public int saveTemplate(String inst, String title, String template) {
        SmsTemplate st = new SmsTemplate();
        st.setInst(safeInst(inst));
        st.setTitle(title);
        st.setTemplate(template);
        return mapper.smsInsert(st);
    }

    public List<Map<String, Object>> selectTransmissionHistory(Criteria cri) {
        String t = safeInst(cri.getInst());
        StringBuilder sql = new StringBuilder()
                .append("SELECT id, contents, from_phone, to_phone, status, response, created_at, send_type, reserve_time ")
                .append("FROM csm.transmission_history_").append(t).append(" WHERE 1=1 ");

        if (cri.getFail() == null || cri.getFail().isBlank()) {
            sql.append(" AND status = 'SUCCESS' ");
        }
        if (cri.getKeyword() != null && !cri.getKeyword().isBlank()) {
            sql.append(" AND to_phone LIKE ? ");
            return jdbcTemplate.queryForList(sql + " ORDER BY created_at DESC LIMIT ?, ? ",
                    "%" + cri.getKeyword() + "%", cri.getPageStart(), cri.getPerPageNum());
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?, ? ");
        return jdbcTemplate.queryForList(sql.toString(), cri.getPageStart(), cri.getPerPageNum());
    }

    public int smsCnt(Criteria cri) {
        String t = safeInst(cri.getInst());
        StringBuilder sql = new StringBuilder()
                .append("SELECT COUNT(*) FROM csm.transmission_history_").append(t).append(" WHERE 1=1 ");
        if (cri.getKeyword() != null && !cri.getKeyword().isBlank()) {
            sql.append(" AND to_phone LIKE ? ");
            Integer n = jdbcTemplate.queryForObject(sql.toString(), Integer.class, "%" + cri.getKeyword() + "%");
            return n == null ? 0 : n;
        }
        Integer n = jdbcTemplate.queryForObject(sql.toString(), Integer.class);
        return n == null ? 0 : n;
    }

    public Map<String, Integer> getSendTypeUsage(String inst) {
        String t = safeInst(inst);
        String sql = "SELECT send_type, COUNT(*) AS count FROM csm.transmission_history_" + t
                + " WHERE status='SUCCESS' GROUP BY send_type";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        for (Map<String, Object> r : rows) {
            String type = Objects.toString(r.get("send_type"), "");
            Number count = (Number) r.get("count");
            out.put(type, count == null ? 0 : count.intValue());
        }
        return out;
    }

    public List<Instdata> price(String inst) {
        String sql = "SELECT id_col_02, id_col_03, sms_price, lms_price, mms_price FROM csm.inst_data_cs WHERE id_col_03 = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instdata d = new Instdata();
            d.setId_col_02(rs.getString("id_col_02"));
            d.setId_col_03(rs.getString("id_col_03"));
            d.setSms_price(rs.getString("sms_price"));
            d.setLms_price(rs.getString("lms_price"));
            d.setMms_price(rs.getString("mms_price"));
            return d;
        }, inst);
    }

    public Map<String, Integer> getSendTypeUsageByMonth(String inst, java.time.LocalDate date) {
        String t = safeInst(inst);
        String month = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        String sql = "SELECT send_type, COUNT(*) AS count FROM csm.transmission_history_" + t
                + " WHERE status='SUCCESS' AND DATE_FORMAT(created_at, '%Y-%m')=? GROUP BY send_type";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, month);
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        for (Map<String, Object> r : rows) {
            String type = Objects.toString(r.get("send_type"), "");
            Number count = (Number) r.get("count");
            out.put(type, count == null ? 0 : count.intValue());
        }
        return out;
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

    public int insertSendResult(String inst, String device, String cmsgId, String msgId, String phone, String media,
            String toName, String unixTime, String result, String refkey) {
        String t = safeInst(inst);
        String sql = "INSERT INTO csm.sms_request_" + t
                + " (device, cmsg_id, msg_id, phone, media, to_name, unix_time, result, refkey, insert_date) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        return jdbcTemplate.update(sql, device, cmsgId, msgId, phone, media, toName, unixTime, result, refkey);
    }

    public int updateMessageHistoryStatus(String inst, String refkey, String status) {
        String t = safeInst(inst);
        String sql = "UPDATE csm.transmission_history_" + t + " SET status = ? WHERE refkey = ?";
        return jdbcTemplate.update(sql, status, refkey);
    }

    private String safeInst(String inst) {
        if (inst == null) {
            throw new IllegalArgumentException("inst is null");
        }
        String normalized = inst.trim();
        if (!normalized.matches("[A-Za-z0-9_]{2,20}")) {
            throw new IllegalArgumentException("Invalid inst: " + inst);
        }
        return normalized;
    }
}
