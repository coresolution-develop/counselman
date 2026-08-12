package com.coresolution.csm.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.SmsBatchService;

import jakarta.servlet.http.HttpSession;

/**
 * 브라우저용 배치 문자 발송 API.
 *
 * <p>세션 인증 + 기존 CSRF 정책을 그대로 따른다 (CSRF 예외 목록에 추가하지 않는다 —
 * 화면은 meta 태그의 토큰을 헤더로 보낸다). 서버 간(mediplat→csm) 경로는 Phase 3 에서
 * 별도 엔드포인트로 만든다. 하나로 합쳐 CSRF 만 예외 처리하면 브라우저 CSRF 방어가 사라진다.
 */
@RestController
public class SmsBatchController {

    private static final Logger log = LoggerFactory.getLogger(SmsBatchController.class);

    private final SmsBatchService smsBatchService;

    public SmsBatchController(SmsBatchService smsBatchService) {
        this.smsBatchService = smsBatchService;
    }

    @PostMapping(value = { "api/counsel/sms/batch", "/api/counsel/sms/batch" },
            produces = "application/json;charset=UTF-8")
    public ResponseEntity<Map<String, Object>> sendBatch(
            HttpSession session,
            @RequestBody(required = false) Map<String, Object> payload) {
        String inst = resolveInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("description", "fail", "message", "세션이 만료되었습니다."));
        }
        if (payload == null || payload.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("description", "fail", "message", "요청 payload가 비어 있습니다."));
        }

        String idemKey = str(payload.get("idemKey"));
        String fromPhone = str(payload.get("fromPhone"));
        String message = str(payload.get("message"));
        String clientType = str(payload.get("clientType"));
        List<String> recipients = payload.get("recipients") instanceof List<?> list
                ? list.stream().filter(Objects::nonNull).map(String::valueOf).toList()
                : List.of();

        try {
            SmsBatchService.BatchOutcome outcome = smsBatchService.send(
                    inst, currentUsername(), idemKey, fromPhone, message, recipients, clientType);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("batchId", outcome.batchId());
            body.put("duplicate", outcome.duplicate());
            body.put("total", outcome.total());
            body.put("success", outcome.success());
            body.put("failed", outcome.failed());
            body.put("unknown", outcome.unknown());
            body.put("results", outcome.results().stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("recipient", r.recipient());
                m.put("status", r.status());
                m.put("historyId", r.historyId());
                m.put("reason", r.reason());
                return m;
            }).toList());
            return ResponseEntity.ok(body);
        } catch (SmsBatchService.BatchRequestException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("description", "fail", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("[api/counsel/sms/batch] fail inst={}", inst, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("description", "fail", "message", "문자 발송 처리 중 오류가 발생했습니다."));
        }
    }

    /** PageController.ensureInst 와 동일한 규칙: 세션 attr 우선, SecurityContext 폴백. */
    private String resolveInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        var ctx = SecurityContextHolder.getContext();
        Authentication auth = ctx == null ? null : ctx.getAuthentication();
        if (auth != null && auth.getDetails() instanceof InstDetails id) {
            String inst = id.normalized();
            if (inst != null && !inst.isBlank()) {
                session.setAttribute("inst", inst);
                return inst;
            }
        }
        return null;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
