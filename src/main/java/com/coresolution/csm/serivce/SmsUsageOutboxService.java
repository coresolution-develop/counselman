package com.coresolution.csm.serivce;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 사용량 이벤트 outbox 적재 (CSM-4).
 *
 * <p>── 왜 트랜잭션으로 묶지 않았나 (재론 금지) ──
 * 플랫폼 CLAUDE.md §9.2 초안은 {@code sms_batch UPDATE} 와 outbox INSERT 를
 * <b>같은 트랜잭션</b>으로 묶는 그림이었다. <b>그 트랜잭션이 csm 에 없다.</b>
 * {@link SmsBatchService} 는 비즈뿌리오 호출을 트랜잭션 안에서 하지 않기로
 * 의도적으로 결정했고, 배치 집계 UPDATE 조차 실패해도 로그만 남기는 best-effort 다.
 *
 * <p>없는 트랜잭션을 만들어 스펙을 맞추면 <b>운영 중인 발송 경로의 동작이 바뀐다.</b>
 * 지금은 DB 가 잠깐 흔들려도 발송이 성공으로 끝나는데, 묶으면 그 경로가 달라진다.
 * 얻는 것은 명목상의 스펙 준수이고, 잃는 것은 실제 안정성이다.
 *
 * <p>대신 <b>{@code sms_batch} 를 진실로 두고 outbox 를 파생으로</b> 만든다.
 * outbox INSERT 가 실패해도 발송은 그대로 끝나고, 누락은
 * {@link #recoverMissing(int, int)} 가 뒤늦게 채운다.
 */
@Service
public class SmsUsageOutboxService {

    private static final Logger log = LoggerFactory.getLogger(SmsUsageOutboxService.class);

    /** 이 payload 의 건수가 <b>접수 시점</b> 기준임을 나타낸다. 플랫폼이 이 값으로 해석을 정한다. */
    public static final String COUNT_BASIS_ACCEPTED = "ACCEPTED";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SmsUsageOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 발송 직후 호출한다. <b>절대 던지지 않는다.</b>
     *
     * <p>여기서 던지면 발송이 실패로 뒤집힌다 — 문자는 이미 나갔는데.
     * 실패는 로그로 남기고, 스캐너가 나중에 채운다.
     */
    public void enqueueQuietly(String batchId) {
        try {
            enqueue(batchId, "SEND");
        } catch (DuplicateKeyException e) {
            // 이미 있다. 재시도·중복 호출에서 정상이다.
            log.debug("[usage-outbox] already queued batchId={}", batchId);
        } catch (Exception e) {
            // **발송을 무르게 하지 않는다.** 스캐너가 10분 뒤 잡는다.
            log.error("[usage-outbox] enqueue fail batchId={} — 스캐너가 복구합니다.", batchId, e);
        }
    }

    /**
     * {@code sms_batch} 한 행을 읽어 payload 를 만들고 outbox 에 넣는다.
     *
     * @param source {@code SEND}(정상 경로) 또는 {@code SCAN}(누락 복구)
     * @return 넣었으면 true. 대상 배치가 없으면 false
     */
    public boolean enqueue(String batchId, String source) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("""
                    SELECT batch_id, inst_code, send_type, total_count, success_count,
                           failed_count, unknown_count, unit_cost, total_cost, billable,
                           price_version, created_at
                    FROM csm.sms_batch WHERE batch_id = ?
                    """, batchId);
        } catch (Exception e) {
            log.warn("[usage-outbox] batch not found batchId={}", batchId);
            return false;
        }

        String payload = writePayload(buildPayload(row));

        jdbcTemplate.update("""
                INSERT INTO csm.sms_usage_outbox (batch_id, inst_code, payload, source)
                VALUES (?, ?, ?, ?)
                """, batchId, row.get("inst_code"), payload, source);
        return true;
    }

    /**
     * 플랫폼에 보낼 payload.
     *
     * <p>── {@code countBasis} 를 명시하는 이유 ──
     * csm 의 {@code success_count} 는 <b>비즈뿌리오가 접수한 시점</b>의 값이다.
     * 배달 리포트 콜백은 {@code transmission_history} 만 갱신하고 {@code sms_batch} 는
     * 건드리지 않는다. 즉 <b>최종 배달 결과가 아니다.</b>
     * 이 사실을 payload 에 적어 두지 않으면 플랫폼이 최종 결과로 오해한다.
     *
     * <p>── {@code billable} 을 따로 넣는 이유 ──
     * {@code totalCostJeon = 0} 만으로는 <b>무료 건(OTP)</b> 과
     * <b>단가가 0으로 잘못 들어온 건</b> 이 구분되지 않는다. 둘은 대응이 정반대다.
     *
     * <p>── {@code priceVersion} 이 null 일 수 있다 ──
     * 오류가 아니다. <b>플랫폼 단가를 못 받은 채로 발송했다</b>는 정보다.
     * 플랫폼은 "적용됐다고 믿었는데 아니었다" 를 여기서 안다.
     *
     * <p>── ⚠️ {@code totalCostJeon} 은 <b>문자열</b>이다 (재론 금지) ──
     * 숫자로 보내면 JS 의 {@code JSON.parse} 가 안전 정수 범위(2^53-1) 밖에서
     * <b>값을 조용히 바꾼다.</b> 그 시점에는 이미 손쓸 수 없다 — 플랫폼이 받아 봐야
     * 틀린 금액을 저장하는 것이고, 그건 요금 분쟁이다.
     *
     * <p>실제로 여기서 숫자로 보내고 있었고 플랫폼은 문자열만 받았다.
     * <b>모든 이벤트가 400 이었고 4xx 는 영구 실패라 그 사용량은 영영 안 들어갔을 것이다.</b>
     * 배포 직전에 잡았다. 계약은 {@code usage-event-vectors.json} 이 정한다.
     */
    Map<String, Object> buildPayload(Map<String, Object> row) {
        java.util.LinkedHashMap<String, Object> p = new java.util.LinkedHashMap<>();
        p.put("batchId", row.get("batch_id"));
        p.put("instCode", row.get("inst_code"));
        p.put("channel", row.get("send_type"));
        p.put("totalCount", intOf(row.get("total_count")));
        p.put("successCount", intOf(row.get("success_count")));
        p.put("failedCount", intOf(row.get("failed_count")));
        p.put("unknownCount", intOf(row.get("unknown_count")));
        p.put("unitCostJeon", intOf(row.get("unit_cost")));
        // 문자열이다. 위 주석 참조 — 숫자로 바꾸면 큰 금액이 조용히 틀어진다.
        p.put("totalCostJeon", String.valueOf(longOf(row.get("total_cost"))));
        p.put("billable", "Y".equalsIgnoreCase(String.valueOf(row.get("billable"))));
        p.put("priceVersion", row.get("price_version"));
        p.put("countBasis", COUNT_BASIS_ACCEPTED);
        p.put("sentAt", String.valueOf(row.get("created_at")));
        return p;
    }

    private static int intOf(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static long longOf(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("payload 직렬화 실패: " + e, e);
        }
    }

    // ── 누락 복구 ────────────────────────────────────────────────

    /**
     * outbox 에 없는 {@code sms_batch} 행을 찾아 뒤늦게 채운다.
     *
     * <p>── 왜 지연을 두나 ──
     * 발송 직후에는 <b>아직 outbox INSERT 전</b>일 수 있다. 지연 없이 스캔하면
     * 정상 진행 중인 배치를 "누락" 으로 잡아 {@code SCAN} 으로 기록하고,
     * 그러면 <b>"스캐너가 자주 잡는다" 는 신호가 무의미해진다.</b>
     *
     * <p>기준은 <b>10분</b>이다. 한 배치의 최대 소요 시간에서 나온다 —
     * {@code max-recipients=500} × {@code send-delay-ms=100} = 50초가 전송 지연이고,
     * 비즈뿌리오 응답 시간을 건당 1초로 잡아도 약 9분이다. 즉 <b>가장 느린 배치보다 길다.</b>
     * {@code CSM_SMS_BATCH_MAX_RECIPIENTS} 를 올리면 이 값도 같이 올려야 한다.
     *
     * @return 새로 채운 건수
     */
    public int recoverMissing(int delayMinutes, int limit) {
        List<String> missing = jdbcTemplate.queryForList("""
                SELECT b.batch_id
                FROM csm.sms_batch b
                LEFT JOIN csm.sms_usage_outbox o ON o.batch_id = b.batch_id
                WHERE o.batch_id IS NULL
                  AND b.created_at < NOW() - INTERVAL ? MINUTE
                ORDER BY b.created_at
                LIMIT ?
                """, String.class, delayMinutes, limit);

        int recovered = 0;
        for (String batchId : missing) {
            try {
                if (enqueue(batchId, "SCAN")) {
                    recovered++;
                }
            } catch (DuplicateKeyException e) {
                // 그 사이 정상 경로가 넣었다. 정상이다.
            } catch (Exception e) {
                log.warn("[usage-outbox] recover fail batchId={}", batchId, e);
            }
        }

        if (recovered > 0) {
            // **이 로그가 뜨는 것 자체가 신호다.** 발송 경로의 outbox INSERT 가
            // 실패하고 있다는 뜻이다. 조용히 복구하고 넘어가면 원인이 안 드러난다.
            log.warn("[usage-outbox] 누락 {}건을 복구했습니다 — 발송 경로의 outbox 적재가 "
                    + "실패하고 있는지 확인하세요. (source=SCAN 으로 기록됨)", recovered);
        }
        return recovered;
    }
}
