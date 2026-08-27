package com.coresolution.csm.serivce;

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
import com.coresolution.csm.util.JeonFormat;
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

    /**
     * 플랫폼 단가 캐시 (폴백 1단계).
     *
     * <p>{@code required = false} 인 이유: CSM-3 이전 배포나 테스트에서는 빈이
     * 없을 수 있다. 없으면 폴백이 2단계부터 시작한다 — <b>기존 동작 그대로다.</b>
     */
    @Autowired(required = false)
    private PlatformPriceCache platformPriceCache;

    /** 폴백 사유별 마지막 로그 시각. 발송마다 찍히는 것을 막는다. */
    private final java.util.Map<String, Long> priceFallbackLoggedAt =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PRICE_FALLBACK_LOG_INTERVAL_MS = 10 * 60 * 1000L;

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
        // sms_price_version 은 CSM-4 가 쓴다 — **같은 조회에서** 꺼내야 단가와 버전이 안 갈린다.
        String sql = "SELECT id_col_02, id_col_03, sms_price, lms_price, mms_price, sms_price_version"
                + " FROM csm.inst_data_cs WHERE id_col_03 = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instdata d = new Instdata();
            d.setId_col_02(rs.getString("id_col_02"));
            d.setId_col_03(rs.getString("id_col_03"));
            d.setSms_price(rs.getString("sms_price"));
            d.setLms_price(rs.getString("lms_price"));
            d.setMms_price(rs.getString("mms_price"));
            d.setSms_price_version((Integer) rs.getObject("sms_price_version"));
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
     * 발송 시점 단가(전 단위).
     *
     * <p>── 폴백 3단계 ──
     * 플랫폼이 죽어도 발송이 멈추면 안 된다. 위에서부터 순서대로 찾는다.
     *
     * <pre>
     *   1. platform_price_cache   플랫폼에서 받은 last-known-good (DB 에 영속)
     *   2. inst_data_cs           기존 값
     *   3. 프로퍼티 폴백           마지막 방어선
     * </pre>
     *
     * <p>단계가 셋인 이유: "방금 받은 값" 과 last-known-good 을 따로 세지 않는다.
     * 폴링이 받는 즉시 캐시에 쓰므로 <b>조회 시점에는 같은 곳</b>이다.
     * 메모리 캐시를 하나 더 두면 재시작 때 두 값이 갈리는 경로만 생긴다.
     *
     * <p>1번이 <b>메모리가 아니라 DB</b> 인 것이 핵심이다. 메모리면 재시작 직후
     * 비어 있고, 그때 플랫폼이 죽어 있으면 조용히 2번으로 떨어진다.
     * 발송은 계속되므로 그 상태가 며칠 이어져도 아무도 모른다.
     *
     * <p>── 소수 3자리는 거부한다 ──
     * 전(錢) 미만 자릿수는 표현할 수 없다. 예전에는 HALF_UP 으로 근사했지만
     * <b>고객이 보는 금액과 실제 차감액이 달라진다.</b> 플랫폼의
     * {@code parseUnitPrice()} 와 같은 규칙으로 거부하고 폴백한다.
     */
    public int unitCostJeon(String inst, String sendType) {
        return unitPrice(inst, sendType).jeon();
    }

    /**
     * 단가와 <b>그 단가가 어느 버전에서 왔는지</b>를 함께 돌려준다 (CSM-4).
     *
     * <p>── 왜 같이 돌려주나 ──
     * 사용량 이벤트가 "이 배치를 어느 버전으로 과금했는지" 를 회신해야 한다.
     * <b>단가를 정한 자리에서 같이 꺼내지 않으면</b> 나중에 다시 조회하게 되고,
     * 그 사이 폴링이 값을 바꾸면 <b>과금한 버전과 회신한 버전이 갈린다.</b>
     *
     * <p>{@link UnitPrice#version()} 이 {@code null} 인 것은 오류가 아니다 —
     * <b>플랫폼 단가를 못 받은 채로 발송했다</b>는 정보다 (3단계 폴백).
     * 플랫폼은 그걸 보고 "적용됐다고 믿었는데 아니었다" 를 알 수 있다.
     */
    public UnitPrice unitPrice(String inst, String sendType) {
        // ── 1단계: 플랫폼 캐시 (last-known-good) ──
        //
        // **두 상황을 구분해서 남긴다.** 로그에서 같아 보이면 진단이 안 된다.
        //   빈 없음        CSM-3 미배포 또는 설정 미완 → 기존 동작 그대로
        //   빈 있고 값 없음  플랫폼에서 한 번도 못 받았다 → 폴링을 확인해야 한다
        if (platformPriceCache == null) {
            // **경고하지 않는다.** 빈이 없다는 건 플랫폼 연동을 아직 켜지 않았다는
            // 뜻이고, 그건 정상 상태다. 여기서 WARN 을 내면 연동 전까지 발송마다
            // 경고가 쌓이고, 그러면 진짜 경고가 묻힌다 (§3.2 와 같은 판단).
            if (log.isDebugEnabled()) {
                log.debug("[sms-price] inst={} type={} PLATFORM_CACHE_DISABLED — 기존 경로로 동작합니다.",
                        inst, sendType);
            }
        } else {
            var cached = platformPriceCache.find(inst, sendType);
            if (cached.isPresent()) {
                return new UnitPrice(cached.get().unitCostJeon(), cached.get().priceVersion());
            }
            // 빈이 있는데 값이 없다 = 폴링이 한 번도 성공하지 못했다.
            // **이건 정상이 아니다.** 위(빈 없음)와 구분해서 남긴다.
            logPriceFallbackOnce(inst, sendType, "PLATFORM_CACHE_EMPTY",
                    "플랫폼 단가를 아직 한 번도 받지 못했습니다. 폴링 상태를 확인하세요.");
        }

        // ── 2단계: inst_data_cs ──
        //
        // 버전은 **같은 SELECT 에서** 꺼낸다. 따로 조회하면 그 사이 폴링이 값을 바꿔
        // 단가와 버전이 갈릴 수 있다.
        String priceStr = null;
        Integer mirroredVersion = null;
        try {
            List<Instdata> rows = price(inst);
            if (!rows.isEmpty()) {
                Instdata d = rows.get(0);
                mirroredVersion = d.getSms_price_version();
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
            Integer parsed = parseUnitPriceJeon(inst, sendType, priceStr);
            if (parsed != null) {
                return new UnitPrice(parsed, mirroredVersion);
            }
        } else {
            log.warn("[sms-price] inst={} type={} price not configured, falling back to default", inst, sendType);
        }

        // ── 3단계: 프로퍼티 폴백 ──
        // 버전은 null 이다. **플랫폼 단가를 못 받고 발송했다**는 뜻이라 그 자체가 정보다.
        int jeon = switch (sendType) {
            case "lms" -> fallbackLmsJeon;
            case "mms" -> fallbackMmsJeon;
            default -> fallbackSmsJeon;
        };
        return new UnitPrice(jeon, null);
    }

    /**
     * 단가(전)와 그 출처 버전.
     *
     * @param version 플랫폼이 배포한 단가 버전. {@code null} 이면 <b>플랫폼 단가가 아니다</b>
     *                (프로퍼티 폴백 또는 플랫폼 연동 전에 설정된 값)
     */
    public record UnitPrice(int jeon, Integer version) {
    }


    /**
     * 폴백 사유를 남기되 <b>발송마다 찍지 않는다.</b>
     *
     * <p>발송 한 건마다 WARN 이 나오면 대량 발송 시 로그가 그것으로 가득 찬다.
     * 같은 (기관·채널·사유) 조합은 {@code PRICE_FALLBACK_LOG_INTERVAL} 마다 한 번만 남긴다.
     * §3.2 노란색 판단과 같은 계열 — <b>항상 켜져 있는 경고는 곧 아무도 안 본다.</b>
     */
    private void logPriceFallbackOnce(String inst, String sendType, String reason, String message) {
        String key = inst + "|" + sendType + "|" + reason;
        long now = System.currentTimeMillis();
        Long last = priceFallbackLoggedAt.get(key);
        if (last != null && now - last < PRICE_FALLBACK_LOG_INTERVAL_MS) {
            return;
        }
        priceFallbackLoggedAt.put(key, now);
        log.warn("[sms-price] inst={} type={} {} — {}", inst, sendType, reason, message);
    }

    /**
     * 원 문자열 → 전 단위 정수. 실패하면 null 을 돌려 호출부가 폴백하게 한다.
     *
     * <p><b>소수 3자리 이상은 거부한다.</b> 9.655원은 965.5전인데 전 미만은
     * 표현할 수 없다. 반올림하면 고객이 입력한 값과 실제 차감액이 갈린다 —
     * 그건 표시 버그가 아니라 요금 분쟁이다.
     */
    /**
     * 원 단위 문자열 → 전. 거부하면 {@code null} 이고 호출부가 폴백으로 넘어간다.
     *
     * <p><b>규칙은 {@link JeonFormat#parseWonToJeon} 하나뿐이다.</b> 예전에는 여기에
     * 같은 규칙을 따로 구현해 뒀는데, 플랫폼 벡터로 대조해 보니 갈라져 있었다 —
     * {@code "1e2"} 를 플랫폼은 거부하는데 여기서는 <b>100원(10,000전)으로 통과</b>시켰다.
     * 청구 경로라 같은 문자열이 두 시스템에서 다른 금액이 되는 상황이었다.
     *
     * <p>거부 사유별 문구는 없앴다. 운영자가 고쳐야 하는 것은 <b>거부된 값 자체</b>이고,
     * 그 값은 로그에 그대로 남는다.
     */
    private Integer parseUnitPriceJeon(String inst, String sendType, String priceStr) {
        Long jeon = JeonFormat.parseWonToJeon(priceStr);
        if (jeon == null) {
            log.warn("[sms-price] inst={} type={} 단가 '{}' 를 거부했습니다 — 폴백 단가를 씁니다."
                    + " (허용: 0 이상, 소수 2자리까지, 지수 표기·부호·구분자 불가)",
                    inst, sendType, priceStr);
            return null;
        }
        return jeon.intValue();
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
