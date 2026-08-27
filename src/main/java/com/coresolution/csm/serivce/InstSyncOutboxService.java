package com.coresolution.csm.serivce;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.coresolution.csm.serivce.CsmSchemaBootstrapService.InstChange;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기관 변경 통지 적재 (CSM-6).
 *
 * <p>── 왜 알려야 하나 ──
 * 신규 기관이 생겨도 플랫폼이 모르면 <b>사용량 이벤트가 404 로 쌓인다.</b>
 * 비활성화도 마찬가지다 — 플랫폼 화면에 죽은 기관이 계속 활성으로 보인다.
 *
 * <p>── 왜 {@code sms_usage_outbox} 와 테이블을 나눴나 ──
 * payload 모양과 엔드포인트가 다르다. 한 테이블에 섞으면 {@code type} 분기가 생겨
 * <b>전송 코드가 오히려 복잡해진다.</b> 스케줄러({@link SmsUsageSender})는 공유하되
 * 테이블은 나눈다 — 백오프·4xx 규칙·하트비트는 자동으로 같아진다.
 *
 * <p>── 기동을 무르게 하지 않는다 ──
 * {@code CsmSchemaBootstrapService} 는 {@code @PostConstruct} 로도 돈다.
 * <b>여기서 던지면 기동이 실패한다.</b> 플랫폼이 죽어 있어도 csm 은 떠야 한다.
 * {@link #enqueueQuietly} 가 전부 삼킨다.
 *
 * <p>── 같은 변경을 반복해 보내지 않는다 ──
 * PK 가 {@code (inst_code, change_type)} 이라, 아직 못 보낸 같은 변경이 있으면
 * <b>내용만 갱신되고 행은 하나로 유지된다.</b> 10분마다 도는 경로라 이게 없으면
 * 큐가 같은 통지로 찬다.
 */
@Service
public class InstSyncOutboxService {

    private static final Logger log = LoggerFactory.getLogger(InstSyncOutboxService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InstSyncOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 적재한다. <b>절대 던지지 않는다</b> — 기동 경로에서 불린다. */
    public void enqueueQuietly(InstChange change) {
        try {
            enqueue(change);
        } catch (DuplicateKeyException e) {
            // ON DUPLICATE KEY UPDATE 로 처리되므로 정상 경로에서는 오지 않는다.
            log.debug("[inst-sync] already queued inst={} type={}",
                    change.instCode(), change.changeType());
        } catch (Exception e) {
            log.error("[inst-sync] enqueue fail inst={} type={} — 통지가 누락됩니다.",
                    change.instCode(), change.changeType(), e);
        }
    }

    void enqueue(InstChange change) {
        String payload = writePayload(change);

        jdbcTemplate.update("""
                INSERT INTO csm.inst_sync_outbox (inst_code, change_type, payload)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    payload = VALUES(payload),
                    created_at = CURRENT_TIMESTAMP,
                    attempts = 0,
                    next_retry_at = NULL,
                    failed_reason = NULL,
                    -- **sent_at 도 지운다.** 안 지우면 이미 보낸 기관이 다시 바뀌었을 때
                    -- 행은 갱신되는데 전송 대상에서 빠져 **영영 안 나간다.**
                    sent_at = NULL
                """, change.instCode(), change.changeType(), payload);
    }

    /**
     * 플랫폼에 보낼 payload.
     *
     * <p>{@code instCode} 는 <b>정규형</b>이다 — {@code normalizeInstCode} 를 거친 값만
     * 여기 온다. 표기가 갈리면 <b>같은 기관이 두 시스템에서 다른 기관이 된다.</b>
     *
     * <p>{@code active} 는 {@code use_yn} 을 boolean 으로 바꾼 것이다. csm 의 {@code 'Y'/'N'}
     * 표기를 그대로 보내면 플랫폼이 <b>csm 의 표기 규칙을 알아야 한다.</b>
     */
    Map<String, Object> buildPayload(InstChange change) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("instCode", change.instCode());
        p.put("instName", change.instName());
        p.put("active", "Y".equalsIgnoreCase(change.useYn()));
        p.put("changeType", change.changeType());
        return p;
    }

    private String writePayload(InstChange change) {
        try {
            return objectMapper.writeValueAsString(buildPayload(change));
        } catch (Exception e) {
            throw new IllegalStateException("payload 직렬화 실패: " + e, e);
        }
    }
}
