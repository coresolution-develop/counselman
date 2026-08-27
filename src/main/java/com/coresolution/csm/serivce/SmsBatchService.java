package com.coresolution.csm.serivce;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.coresolution.csm.util.SmsRefkey;

/**
 * 배치 문자 발송. 단건 발송도 배치 1건으로 취급한다.
 *
 * <p>발송 플로우 (트랜잭션 없음 — 비즈뿌리오 호출을 트랜잭션 안에서 하지 않는다):
 * <ol>
 *   <li>sms_batch INSERT — (inst_code, idem_key) UNIQUE 로 중복 요청을 구조적으로 차단</li>
 *   <li>수신자별: 이력 READY INSERT → id 확보 → refkey(MP-{inst}-{id}) UPDATE
 *       → 비즈뿌리오 호출 → 접수 결과(SENT/FAILED/UNKNOWN) UPDATE</li>
 *   <li>sms_batch 집계(건수·total_cost) UPDATE</li>
 * </ol>
 *
 * <p>발송은 순차 처리한다. 현 규모(일 40~50건)에서 병렬의 이점이 없고 비즈뿌리오
 * Rate Limit 리스크만 늘린다. 자동 재시도는 하지 않는다 — 타임아웃 시 이미 접수됐을 수 있어
 * 재요청하면 중복 발송된다(UNKNOWN 상태로 남긴다).
 */
@Service
public class SmsBatchService {

    private static final Logger log = LoggerFactory.getLogger(SmsBatchService.class);

    private final SmsService smsService;
    private final SmsMessageTypeResolver typeResolver;
    private final ExternalSmsGatewayService gateway;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 사용량 outbox (CSM-4). {@code required = false} — 없어도 발송은 그대로 동작한다.
     * 기존 발송 경로가 이 빈에 의존하지 않는다는 것을 타입으로 못박는다.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SmsUsageOutboxService usageOutbox;

    @Value("${csm.sms.batch.max-recipients:500}")
    private int maxRecipients;

    /** 비즈뿌리오 호출 간 지연(ms). Rate Limit 완충용. */
    @Value("${csm.sms.batch.send-delay-ms:100}")
    private long sendDelayMs;

    public SmsBatchService(SmsService smsService,
                           SmsMessageTypeResolver typeResolver,
                           ExternalSmsGatewayService gateway,
                           JdbcTemplate jdbcTemplate) {
        this.smsService = smsService;
        this.typeResolver = typeResolver;
        this.gateway = gateway;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record RecipientResult(String recipient, String status, Long historyId, String reason) {
    }

    public record BatchOutcome(String batchId, int total, int success, int failed, int unknown,
                               List<RecipientResult> results, boolean duplicate) {
    }

    /** 요청 검증 실패(400 대상)를 게이트웨이 오류와 구분하기 위한 예외. */
    public static class BatchRequestException extends RuntimeException {
        public BatchRequestException(String message) {
            super(message);
        }
    }

    public BatchOutcome send(String inst, String createdBy, String idemKey,
            String fromPhone, String message, List<String> recipients, String clientType) {

        if (idemKey == null || idemKey.isBlank() || idemKey.trim().length() > 64) {
            throw new BatchRequestException("idemKey가 없거나 형식이 올바르지 않습니다.");
        }
        String idem = idemKey.trim();

        // 수신번호 서버 측 정규화: 하이픈·공백 제거, 숫자만. 중복은 순서 유지로 제거한다.
        if (recipients == null || recipients.isEmpty()) {
            throw new BatchRequestException("수신자가 없습니다.");
        }
        Set<String> normalized = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String r : recipients) {
            String digits = r == null ? "" : r.replaceAll("[^0-9]", "");
            if (digits.length() < 8 || digits.length() > 15) {
                if (!digits.isBlank() || (r != null && !r.isBlank())) {
                    invalid.add(r);
                }
                continue;
            }
            normalized.add(digits);
        }
        if (normalized.isEmpty()) {
            throw new BatchRequestException("유효한 수신번호가 없습니다.");
        }
        if (normalized.size() > maxRecipients) {
            throw new BatchRequestException(
                    "수신자가 최대 " + maxRecipients + "건을 초과했습니다. (요청 " + normalized.size() + "건)");
        }

        // 발신번호 검증: 기관에 등록된 번호만 허용한다 (타 기관 번호 사칭 차단).
        String fromDigits = fromPhone == null ? "" : fromPhone.replaceAll("[^0-9]", "");
        if (fromDigits.isBlank() || !smsService.isRegisteredSenderNumber(inst, fromDigits)) {
            throw new BatchRequestException("기관에 등록되지 않은 발신번호입니다.");
        }

        // 메시지 타입은 서버가 확정한다. 클라이언트 계산값은 관측용으로만 비교한다.
        SmsMessageTypeResolver.Resolved resolved;
        try {
            resolved = typeResolver.resolve(message);
        } catch (IllegalArgumentException e) {
            throw new BatchRequestException(e.getMessage());
        }
        if (clientType != null && !clientType.isBlank()
                && !resolved.type().equalsIgnoreCase(clientType.trim())) {
            log.warn("[sms-batch] type mismatch inst={} client={} server={} bytes={}",
                    inst, clientType, resolved.type(), resolved.bytes());
        }

        // 단가와 **그 단가의 버전**을 한 번에 가져온다 (CSM-4).
        // 따로 조회하면 그 사이 폴링이 값을 바꿔 과금 버전과 회신 버전이 갈린다.
        SmsService.UnitPrice price = smsService.unitPrice(inst, resolved.type());
        int unitCost = price.jeon();
        String batchId = UUID.randomUUID().toString();

        try {
            jdbcTemplate.update("""
                    INSERT INTO csm.sms_batch
                        (batch_id, inst_code, idem_key, from_phone, send_type, total_count, unit_cost, billable, created_by, price_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'Y', ?, ?)
                    """,
                    batchId, inst, idem, fromDigits, resolved.type(),
                    normalized.size() + invalid.size(), unitCost, createdBy, price.version());
        } catch (DuplicateKeyException e) {
            // 같은 idemKey 의 배치가 이미 처리됨(더블클릭·네트워크 재시도) — 재발송하지 않고 기존 결과를 반환한다.
            log.info("[sms-batch] duplicate idemKey inst={} idemKey={} — returning existing batch", inst, idem);
            return loadExisting(inst, idem);
        }

        // ────────────────────────────────────────────────────────────────
        // [Phase 4 지갑 차감 지점]
        // 선불 지갑 도입 시 여기서 배치 단위로 잔액을 차감한다:
        //   차감액 = unit_cost × 발송 대상 건수 (전 단위 정수)
        //   sms_wallet_tx.ref_type='SMS_BATCH', ref_id=batchId
        // 차감 실패(잔액 부족) 시 발송 루프에 진입하지 않고 배치를 거절한다.
        // 발송 후 FAILED 건은 환불 보정하되, UNKNOWN 은 환불 금지(발송됐을 수 있음).
        // ────────────────────────────────────────────────────────────────

        List<RecipientResult> results = new ArrayList<>();
        for (String bad : invalid) {
            results.add(new RecipientResult(bad, "FAILED", null, "잘못된 수신번호"));
        }

        int success = 0;
        int failed = invalid.size();
        int unknown = 0;
        boolean first = true;
        for (String to : normalized) {
            if (!first) {
                sleepQuietly(sendDelayMs);
            }
            first = false;
            RecipientResult r = sendOne(inst, message, null, fromDigits, to,
                    resolved.type(), resolved.subject(), unitCost, "Y", batchId);
            results.add(r);
            switch (r.status()) {
                case "SUCCESS" -> success++;
                case "UNKNOWN" -> unknown++;
                default -> failed++;
            }
        }

        // total_cost 는 Phase 4 차감 금액의 근거다. UNKNOWN 은 환불 금지 원칙이므로 포함한다.
        //
        // long 인 이유: int 로 계산하면 unit_cost × 건수가 2,147,483,647전을 넘는 순간
        // 조용히 음수가 된다. 현재 max-recipients=500 에서는 단가가 4,294,967전(42,949원)을
        // 넘어야 도달하므로 정상 운영에서는 닿지 않지만, 두 경로로 실현될 수 있다.
        //   ① CSM_SMS_BATCH_MAX_RECIPIENTS 를 올리는 경우
        //      — 대량 발송 방향이라 가정이 아니라 예정된 변경에 가깝다.
        //        상향 시 주의사항은 application.properties 의 max-recipients 주석 참조.
        //   ② 단가 입력 오류 (단가 화면에 자릿수 검증이 없다)
        //      — CSM-2 에서 단가 설정 화면·API 가 제거되고 마스터가 플랫폼으로 넘어가면
        //        이 경로는 닫힌다. 그때는 CSM-3 의 단가 수신 검증이 방어선이 된다.
        // 캐스트를 왼쪽 피연산자에 붙여야 한다 — (long)(a * b) 는 이미 넘친 값을 넓히는 것이라
        // 아무 것도 고치지 못한다.
        long totalCost = (long) unitCost * (success + unknown);
        try {
            jdbcTemplate.update("""
                    UPDATE csm.sms_batch
                    SET success_count = ?, failed_count = ?, unknown_count = ?, total_cost = ?
                    WHERE batch_id = ?
                    """, success, failed, unknown, totalCost, batchId);
        } catch (Exception e) {
            log.error("[sms-batch] batch summary update fail inst={} batchId={}", inst, batchId, e);
        }

        // ── 사용량 이벤트 적재 (CSM-4) ──
        //
        // **발송 경로를 무르게 하지 않는다.** 집계 UPDATE 와 트랜잭션으로 묶지 않았고,
        // 여기서 실패해도 발송은 그대로 성공으로 끝난다.
        // sms_batch 가 진실이고 outbox 는 파생이다 — 누락은 스캐너가 복구한다.
        // 근거는 SmsUsageOutboxService 클래스 주석에 있다.
        if (usageOutbox != null) {
            usageOutbox.enqueueQuietly(batchId);
        }

        return new BatchOutcome(batchId, results.size(), success, failed, unknown, results, false);
    }

    /**
     * 단건 발송: 이력 READY INSERT → refkey 확정 → 게이트웨이 호출 → 접수 결과 반영.
     * OTP 등 배치 외 단건 경로도 이 메서드를 사용한다 (billable='N' 지정).
     * 반환 status: SUCCESS / FAILED / UNKNOWN
     *
     * @param historyContents 이력에 남길 본문. null 이면 발송 본문을 그대로 남긴다.
     *                        OTP 처럼 평문을 남기면 안 되는 경우 마스킹본을 넘긴다.
     */
    public RecipientResult sendOne(String inst, String contents, String historyContents,
            String fromDigits, String toDigits,
            String type, String subject, Integer costJeon, String billable, String batchId) {
        long historyId;
        String refkey;
        try {
            historyId = smsService.insertHistoryReady(
                    inst, historyContents != null ? historyContents : contents,
                    fromDigits, toDigits, type, costJeon, billable, batchId);
            refkey = SmsRefkey.format(inst, historyId);
            smsService.assignHistoryRefkey(inst, historyId, refkey);
        } catch (Exception e) {
            log.error("[sms-batch] history insert fail inst={} to={}", inst, toDigits, e);
            return new RecipientResult(toDigits, "FAILED", null, "이력 기록 실패: " + e.getMessage());
        }

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("refkey", refkey);
        payload.put("type", type);
        payload.put("from", fromDigits);
        payload.put("to", toDigits);
        Map<String, Object> typed = new java.util.HashMap<>();
        typed.put("message", contents);
        if ("lms".equals(type)) {
            typed.put("subject", subject == null ? "" : subject);
        }
        payload.put("content", Map.of(type, typed));

        try {
            Map<String, Object> resp = gateway.send(payload);
            boolean success = judgeAcceptSuccess(inst, refkey, resp);
            String messageKey = truncate(firstNonBlank(resp, "messagekey", "messageKey", "msgkey"), 64);
            String vendorCode = truncate(str(resp.get("code")), 10);
            String raw = str(resp.getOrDefault("_raw", resp.toString()));
            if (success) {
                smsService.finalizeHistory(inst, historyId, SmsService.STATUS_SENT, raw, messageKey, vendorCode);
                return new RecipientResult(toDigits, "SUCCESS", historyId, null);
            }
            smsService.finalizeHistory(inst, historyId, SmsService.STATUS_FAILED, raw, messageKey, vendorCode);
            return new RecipientResult(toDigits, "FAILED", historyId,
                    str(resp.getOrDefault("description", "접수 실패")));
        } catch (BizppurioTimeoutException e) {
            if (e.isDeliveryOutcomeUnknown()) {
                // 결과 불명: 이미 접수됐을 수 있다. 재시도 금지, 환불 금지 — UNKNOWN 으로 남긴다.
                finalizeQuietly(inst, historyId, SmsService.STATUS_UNKNOWN, e.getMessage());
                return new RecipientResult(toDigits, "UNKNOWN", historyId, "게이트웨이 응답 시간 초과 (결과 불명)");
            }
            // 토큰 발급 단계 타임아웃은 발송 전이므로 안전하게 실패로 취급한다.
            finalizeQuietly(inst, historyId, SmsService.STATUS_FAILED, e.getMessage());
            return new RecipientResult(toDigits, "FAILED", historyId, "게이트웨이 인증 시간 초과");
        } catch (Exception e) {
            log.error("[sms-batch] send fail inst={} refkey={}", inst, refkey, e);
            finalizeQuietly(inst, historyId, SmsService.STATUS_FAILED, e.getMessage());
            return new RecipientResult(toDigits, "FAILED", historyId, e.getMessage());
        }
    }

    /**
     * 접수 성공 판정: description=="success" 와 code==1000 을 병행한다.
     * 벤더가 응답 문구를 바꾸면 전건 실패 처리되는 위험이 있어 둘 중 하나만 맞아도 성공으로 보되,
     * 불일치는 WARN 으로 관측한다.
     */
    boolean judgeAcceptSuccess(String inst, String refkey, Map<String, Object> resp) {
        boolean byDesc = "success".equalsIgnoreCase(str(resp.get("description")));
        boolean byCode = "1000".equals(str(resp.get("code")));
        if (byDesc != byCode) {
            log.warn("[sms-batch] accept judgment mismatch inst={} refkey={} description='{}' code='{}'",
                    inst, refkey, resp.get("description"), resp.get("code"));
        }
        return byDesc || byCode;
    }

    private BatchOutcome loadExisting(String inst, String idemKey) {
        Map<String, Object> batch = jdbcTemplate.queryForMap(
                "SELECT batch_id, success_count, failed_count, unknown_count FROM csm.sms_batch"
                        + " WHERE inst_code = ? AND idem_key = ?",
                inst, idemKey);
        String batchId = str(batch.get("batch_id"));
        List<Map<String, Object>> rows = smsService.listHistoryByBatch(inst, batchId);
        List<RecipientResult> results = new ArrayList<>();
        int success = 0;
        int failed = 0;
        int unknown = 0;
        for (Map<String, Object> row : rows) {
            String status = mapHistoryStatus(str(row.get("status")));
            Number id = (Number) row.get("id");
            results.add(new RecipientResult(str(row.get("to_phone")), status,
                    id == null ? null : id.longValue(), null));
            switch (status) {
                case "SUCCESS" -> success++;
                case "UNKNOWN" -> unknown++;
                default -> failed++;
            }
        }
        return new BatchOutcome(batchId, results.size(), success, failed, unknown, results, true);
    }

    /** 이력 상태 → 응답 상태 요약. READY 는 접수 결과가 확정되지 않은 것이므로 UNKNOWN 으로 답한다. */
    private String mapHistoryStatus(String status) {
        return switch (status) {
            case SmsService.STATUS_SENT, SmsService.STATUS_DONE -> "SUCCESS";
            case SmsService.STATUS_FAILED, SmsService.STATUS_ERROR -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    private void finalizeQuietly(String inst, long historyId, String status, String response) {
        try {
            smsService.finalizeHistory(inst, historyId, status, response, null, null);
        } catch (Exception e) {
            log.error("[sms-batch] history finalize fail inst={} historyId={}", inst, historyId, e);
        }
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            String v = str(map.get(k));
            if (!v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String truncate(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
