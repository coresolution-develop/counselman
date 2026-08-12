package com.coresolution.csm.serivce;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import com.coresolution.csm.mapper.SmsMapper;
import com.coresolution.csm.vo.Criteria;
import com.coresolution.csm.vo.Instdata;
import com.coresolution.csm.vo.SmsTemplate;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    /**
     * 신규 상태 체계 (구 데이터의 SUCCESS/FAILURE·한글 상태값은 마이그레이션하지 않는다):
     * READY → SENT/FAILED/UNKNOWN (접수 결과) → DONE/ERROR (콜백 최종 결과)
     * UNKNOWN 은 타임아웃 등 결과 불명 — 재시도 금지, 환불 금지.
     */
    public static final String STATUS_READY = "READY";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_ERROR = "ERROR";

    /** 기존 화면 표시 유지: 실패(FAILURE/전송실패/FAILED/ERROR)만 숨긴다. 신·구 상태값 모두 포함. */
    private static final String VISIBLE_STATUS_FILTER =
            " AND status IN ('SUCCESS', '전송완료', '전송중', 'READY', 'SENT', 'DONE', 'UNKNOWN') ";

    /**
     * 요금 집계 대상. 기존 'SUCCESS' 유지 + 신규 체계의 SENT/DONE/UNKNOWN.
     * UNKNOWN 은 발송됐을 수 있고 환불 금지 원칙이므로 과금 집계에 포함한다.
     * (구 데이터의 '전송완료'는 기존 집계에서도 제외였으므로 표시 불변을 위해 추가하지 않는다)
     */
    private static final String BILLABLE_STATUS_FILTER =
            " status IN ('SUCCESS', 'SENT', 'DONE', 'UNKNOWN') ";

    @Autowired
    private SmsMapper mapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 단가 폴백 (전 단위). inst_data_cs 값이 없거나 파싱 불가일 때만 사용한다. */
    @Value("${csm.sms.price-fallback.sms-jeon:960}")
    private int fallbackSmsJeon;
    @Value("${csm.sms.price-fallback.lms-jeon:3000}")
    private int fallbackLmsJeon;
    @Value("${csm.sms.price-fallback.mms-jeon:9000}")
    private int fallbackMmsJeon;

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
        return insertTransmissionHistory(inst, contents, fromPhone, toPhone, status, responseString, refkey, sendType, null);
    }

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
        return mapper.insertTransmissionHistory(inst, contents, fromPhone, toPhone, status, responseString, refkey, sendType, reserveTime);
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

    public int updateTemplate(String inst, int id, String title, String template) {
        SmsTemplate st = new SmsTemplate();
        st.setInst(safeInst(inst));
        st.setId(id);
        st.setTitle(title);
        st.setTemplate(template);
        return mapper.smsUpdate(st);
    }

    public int deleteTemplate(String inst, int id) {
        SmsTemplate st = new SmsTemplate();
        st.setInst(safeInst(inst));
        st.setId(id);
        return mapper.smsDelete(st);
    }

    public List<Map<String, Object>> selectTransmissionHistory(Criteria cri) {
        String t = safeInst(cri.getInst());
        StringBuilder sql = new StringBuilder()
                .append("SELECT id, contents, from_phone, to_phone, status, response, created_at, send_type, reserve_time ")
                .append("FROM csm.transmission_history_").append(t).append(" WHERE 1=1 ");

        if (cri.getFail() == null || cri.getFail().isBlank()) {
            sql.append(VISIBLE_STATUS_FILTER);
        }
        appendHistoryTypeFilter(sql, cri);
        if (cri.getKeyword() != null && !cri.getKeyword().isBlank()) {
            sql.append(" AND (to_phone LIKE ? OR from_phone LIKE ? OR contents LIKE ?) ");
            return jdbcTemplate.queryForList(sql + " ORDER BY created_at DESC LIMIT ?, ? ",
                    "%" + cri.getKeyword() + "%", "%" + cri.getKeyword() + "%", "%" + cri.getKeyword() + "%",
                    cri.getPageStart(), cri.getPerPageNum());
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?, ? ");
        return jdbcTemplate.queryForList(sql.toString(), cri.getPageStart(), cri.getPerPageNum());
    }

    public int smsCnt(Criteria cri) {
        String t = safeInst(cri.getInst());
        StringBuilder sql = new StringBuilder()
                .append("SELECT COUNT(*) FROM csm.transmission_history_").append(t).append(" WHERE 1=1 ");
        if (cri.getFail() == null || cri.getFail().isBlank()) {
            sql.append(VISIBLE_STATUS_FILTER);
        }
        appendHistoryTypeFilter(sql, cri);
        if (cri.getKeyword() != null && !cri.getKeyword().isBlank()) {
            sql.append(" AND (to_phone LIKE ? OR from_phone LIKE ? OR contents LIKE ?) ");
            Integer n = jdbcTemplate.queryForObject(sql.toString(), Integer.class,
                    "%" + cri.getKeyword() + "%", "%" + cri.getKeyword() + "%", "%" + cri.getKeyword() + "%");
            return n == null ? 0 : n;
        }
        Integer n = jdbcTemplate.queryForObject(sql.toString(), Integer.class);
        return n == null ? 0 : n;
    }

    public int countTransmissionHistoryByType(String inst, String type) {
        Criteria cri = new Criteria();
        cri.setInst(inst);
        cri.setType(type);
        return smsCnt(cri);
    }

    public Map<String, Integer> getSendTypeUsage(String inst) {
        String t = safeInst(inst);
        String sql = "SELECT send_type, COUNT(*) AS count FROM csm.transmission_history_" + t
                + " WHERE " + BILLABLE_STATUS_FILTER + " GROUP BY send_type";
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
                + " WHERE " + BILLABLE_STATUS_FILTER + " AND DATE_FORMAT(created_at, '%Y-%m')=? GROUP BY send_type";
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
                + " WHERE " + BILLABLE_STATUS_FILTER + " GROUP BY DATE_FORMAT(created_at, '%Y-%m') ORDER BY month DESC";
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

    /** 신규 refkey(MP-{inst}-{historyId}) 콜백용 — PK 로 직접 갱신한다. */
    public int updateMessageHistoryStatusById(String inst, long id, String status) {
        String t = safeInst(inst);
        String sql = "UPDATE csm.transmission_history_" + t + " SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    /**
     * 신규 발송 플로우 1단계: READY 상태로 먼저 INSERT 하고 생성된 id 를 반환한다.
     * refkey(MP-{inst}-{id})가 id 를 필요로 하므로 비즈뿌리오 호출 전에 이력이 먼저 있어야 한다.
     */
    public long insertHistoryReady(String inst, String contents, String fromPhone, String toPhone,
            String sendType, Integer costJeon, String billable, String batchId) {
        String t = safeInst(inst);
        String sql = "INSERT INTO csm.transmission_history_" + t
                + " (contents, from_phone, to_phone, status, created_at, send_type, cost, billable, batch_id)"
                + " VALUES (?, ?, ?, '" + STATUS_READY + "', NOW(), ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, contents);
            ps.setString(2, fromPhone);
            ps.setString(3, toPhone);
            ps.setString(4, sendType);
            if (costJeon == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, costJeon);
            }
            ps.setString(6, billable);
            ps.setString(7, batchId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("transmission_history insert did not return a generated id");
        }
        return key.longValue();
    }

    /** 신규 발송 플로우 2단계: 확보한 id 로 만든 refkey 를 반영한다. */
    public int assignHistoryRefkey(String inst, long id, String refkey) {
        String t = safeInst(inst);
        return jdbcTemplate.update(
                "UPDATE csm.transmission_history_" + t + " SET refkey = ? WHERE id = ?", refkey, id);
    }

    /** 신규 발송 플로우 3단계: 게이트웨이 접수 결과(SENT/FAILED/UNKNOWN)를 반영한다. */
    public int finalizeHistory(String inst, long id, String status, String response,
            String messageKey, String vendorCode) {
        String t = safeInst(inst);
        return jdbcTemplate.update(
                "UPDATE csm.transmission_history_" + t
                        + " SET status = ?, response = ?, message_key = ?, vendor_code = ? WHERE id = ?",
                status, response, messageKey, vendorCode, id);
    }

    /**
     * 발송 시점 단가(전 단위). 금액 계산에 double/float 를 쓰지 않는다 — BigDecimal 파싱 후
     * 전(錢) 단위 정수로 변환한다 (9.6원 → 960전).
     * inst_data_cs 값이 없거나 숫자가 아니면 프로퍼티 폴백을 쓰고, 관측을 위해 기관코드를 담아 WARN 을 남긴다.
     */
    public int unitCostJeon(String inst, String sendType) {
        String priceStr = null;
        try {
            List<Instdata> rows = price(inst);
            if (!rows.isEmpty()) {
                Instdata d = rows.get(0);
                priceStr = switch (sendType) {
                    case "lms" -> d.getLms_price();
                    case "mms" -> d.getMms_price();
                    default -> d.getSms_price();
                };
            }
        } catch (Exception e) {
            log.warn("[sms-price] inst={} price lookup failed: {}", inst, e.toString());
        }
        if (priceStr != null && !priceStr.isBlank()) {
            try {
                return new BigDecimal(priceStr.trim()).movePointRight(2).intValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                log.warn("[sms-price] inst={} type={} invalid price '{}', falling back to default",
                        inst, sendType, priceStr);
            }
        } else {
            log.warn("[sms-price] inst={} type={} price not configured, falling back to default", inst, sendType);
        }
        return switch (sendType) {
            case "lms" -> fallbackLmsJeon;
            case "mms" -> fallbackMmsJeon;
            default -> fallbackSmsJeon;
        };
    }

    /** 배치에 속한 이력 행 조회 (멱등 재요청 시 기존 결과 반환용). */
    public List<Map<String, Object>> listHistoryByBatch(String inst, String batchId) {
        String t = safeInst(inst);
        return jdbcTemplate.queryForList(
                "SELECT id, to_phone, status FROM csm.transmission_history_" + t
                        + " WHERE batch_id = ? ORDER BY id ASC",
                batchId);
    }

    /** 발신번호가 기관에 등록된 번호인지 확인한다 (하이픈 무시, 숫자 기준 비교). */
    public boolean isRegisteredSenderNumber(String inst, String fromPhoneDigits) {
        String t = safeInst(inst);
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM csm.phone_number_" + t
                        + " WHERE REPLACE(REPLACE(phone_num, '-', ''), ' ', '') = ?",
                Integer.class, fromPhoneDigits);
        return cnt != null && cnt > 0;
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

    private void appendHistoryTypeFilter(StringBuilder sql, Criteria cri) {
        String type = cri.getType() == null ? "" : cri.getType().trim().toLowerCase();
        if ("reserved".equals(type)) {
            sql.append(" AND reserve_time IS NOT NULL ");
        } else if ("sent".equals(type)) {
            sql.append(" AND reserve_time IS NULL ");
        }
    }
}
