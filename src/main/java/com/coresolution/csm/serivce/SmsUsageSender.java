package com.coresolution.csm.serivce;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * outbox 전송 스케줄러 (CSM-4).
 *
 * <p>발송 트랜잭션과 완전히 분리돼 있다 — <b>여기서 무슨 일이 나도 문자는 이미 나갔고
 * {@code sms_batch} 에 기록돼 있다.</b> 최악의 경우 사용량 회신만 늦어진다.
 *
 * <p>── 하는 일 세 가지 ──
 * <ol>
 *   <li><b>전송</b> — 보낼 것을 꺼내 POST. 4xx 는 영구 실패로 닫고 5xx·네트워크는 백오프</li>
 *   <li><b>누락 복구</b> — outbox 에 없는 배치를 뒤늦게 채운다</li>
 *   <li><b>하트비트</b> — 매 실행마다 기록한다</li>
 * </ol>
 *
 * <p>── 왜 하트비트를 남기나 ──
 * 스케줄러가 <b>죽은 것</b>과 <b>보낼 게 없는 것</b>은 로그에서 똑같이 조용하다.
 * 실행 자체를 기록해야 구분된다. 플랫폼 배치들과 같은 이유다 —
 * <b>0건 처리와 실패도 남긴다.</b> 남기지 않으면 "조용하니 정상" 으로 읽힌다.
 *
 * <p>── 즉시 전송 트리거는 만들지 않는다 ──
 * {@link PlatformPricePoller} 와 같은 판단이다. 1분 지연은 감수한다.
 */
@Service
public class SmsUsageSender {

    private static final Logger log = LoggerFactory.getLogger(SmsUsageSender.class);

    /** 백오프 상한. 이 이상 늘리지 않는다 — 늘려 봐야 복구가 느려질 뿐이다. */
    private static final int MAX_BACKOFF_MINUTES = 60;

    private final SmsUsageOutboxService outbox;
    private final PlatformUsageClient client;
    private final JdbcTemplate jdbcTemplate;

    @Value("${csm.sms.usage.batch-size:50}")
    private int batchSize;

    /**
     * 누락 스캔 지연(분). 근거는 {@link SmsUsageOutboxService#recoverMissing(int, int)} 참조 —
     * <b>가장 느린 배치보다 길어야</b> 정상 진행 중인 배치를 누락으로 오인하지 않는다.
     */
    @Value("${csm.sms.usage.recover-delay-minutes:10}")
    private int recoverDelayMinutes;

    public SmsUsageSender(SmsUsageOutboxService outbox, PlatformUsageClient client,
            JdbcTemplate jdbcTemplate) {
        this.outbox = outbox;
        this.client = client;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 1분마다. 백오프 최소 단위와 맞춘다. */
    @Scheduled(fixedDelayString = "${csm.sms.usage.send-interval-ms:60000}",
            initialDelayString = "${csm.sms.usage.send-initial-delay-ms:45000}")
    public void run() {
        if (!client.isConfigured()) {
            // 연동 미설정. 실패가 아니다 — 배포 1단계에서 정상 상태다.
            // outbox 에는 계속 쌓이고, 연동을 켜면 그때 나간다.
            return;
        }

        int sent = 0;
        int failed = 0;
        int permanent = 0;
        String error = null;

        try {
            for (Map<String, Object> row : pending()) {
                String batchId = String.valueOf(row.get("batch_id"));
                PlatformUsageClient.Result result = client.send(String.valueOf(row.get("payload")));

                if (result.ok()) {
                    markSent(batchId);
                    sent++;
                } else if (result.permanent()) {
                    markPermanentlyFailed(batchId, result.reason());
                    permanent++;
                } else {
                    scheduleRetry(batchId, intOf(row.get("attempts")), result.reason());
                    failed++;
                }
            }

            outbox.recoverMissing(recoverDelayMinutes, batchSize);
        } catch (Exception e) {
            // 스케줄러가 예외로 죽으면 다음 실행이 안 온다. 반드시 삼킨다.
            error = e.toString();
            log.error("[usage-send] 실행 중 오류", e);
        }

        writeHeartbeat(sent, failed, permanent, error);
    }

    /**
     * 보낼 것을 꺼낸다.
     *
     * <p>{@code next_retry_at IS NULL} 은 <b>아직 한 번도 시도하지 않은 것</b>이다.
     * {@code failed_reason IS NOT NULL} 은 영구 실패라 다시 꺼내지 않는다.
     */
    private List<Map<String, Object>> pending() {
        return jdbcTemplate.queryForList("""
                SELECT batch_id, payload, attempts
                FROM csm.sms_usage_outbox
                WHERE sent_at IS NULL
                  AND failed_reason IS NULL
                  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
                ORDER BY created_at
                LIMIT ?
                """, batchSize);
    }

    private void markSent(String batchId) {
        jdbcTemplate.update(
                "UPDATE csm.sms_usage_outbox SET sent_at = NOW(), attempts = attempts + 1 "
                        + "WHERE batch_id = ?", batchId);
    }

    /**
     * 4xx — 다시 시도하지 않는다.
     *
     * <p><b>조용히 버리지 않는다.</b> {@code failed_reason} 에 남기고 WARN 을 낸다.
     * 이 건은 플랫폼에 영영 안 들어가므로 <b>사후 대사에서 드러나야</b> 한다.
     */
    private void markPermanentlyFailed(String batchId, String reason) {
        jdbcTemplate.update(
                "UPDATE csm.sms_usage_outbox SET failed_reason = ?, attempts = attempts + 1 "
                        + "WHERE batch_id = ?", reason, batchId);
        log.warn("[usage-send] 영구 실패 batchId={} — 재시도하지 않습니다. 사유: {}", batchId, reason);
    }

    /**
     * 지수 백오프. 1 → 2 → 4 → 8 → 16 → 32 → 60(상한).
     *
     * <p>{@code attempts} 는 이 시점의 <b>직전</b> 값이다. 첫 실패면 0 이므로 1분 뒤가 된다.
     */
    private void scheduleRetry(String batchId, int attempts, String reason) {
        int minutes = backoffMinutes(attempts);
        jdbcTemplate.update(
                "UPDATE csm.sms_usage_outbox SET attempts = attempts + 1, "
                        + "next_retry_at = NOW() + INTERVAL ? MINUTE WHERE batch_id = ?",
                minutes, batchId);
        log.debug("[usage-send] 재시도 예약 batchId={} {}분 뒤 (시도 {}회) 사유: {}",
                batchId, minutes, attempts + 1, reason);
    }

    /** 시도 횟수 → 대기 분. 패키지 공개 — 경계를 테스트로 고정한다. */
    static int backoffMinutes(int attempts) {
        if (attempts < 0) {
            return 1;
        }
        // 1분 << attempts. attempts 가 커도 시프트 오버플로가 나지 않게 먼저 자른다.
        if (attempts >= 6) {
            return MAX_BACKOFF_MINUTES;
        }
        return Math.min(1 << attempts, MAX_BACKOFF_MINUTES);
    }

    /**
     * 하트비트. <b>0건 처리와 실패도 남긴다.</b>
     *
     * <p>기록에 실패해도 삼킨다 — 하트비트 때문에 전송이 멈추면 본말전도다.
     */
    private void writeHeartbeat(int sent, int failed, int permanent, String error) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO csm.sms_usage_heartbeat (id, ran_at, sent_count, failed_count, permanent_count, last_error)
                    VALUES (1, NOW(), ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        ran_at = NOW(), sent_count = VALUES(sent_count),
                        failed_count = VALUES(failed_count), permanent_count = VALUES(permanent_count),
                        last_error = VALUES(last_error)
                    """, sent, failed, permanent, error);
        } catch (Exception e) {
            log.debug("[usage-send] heartbeat write skipped: {}", e.toString());
        }
    }

    private static int intOf(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }
}
