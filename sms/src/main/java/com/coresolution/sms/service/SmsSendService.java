package com.coresolution.sms.service;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.coresolution.sms.model.SmsSendRequest;
import com.coresolution.sms.repository.SmsRepository;

/**
 * Orchestrates a single send: build refkey → resolve reservation time → call Bizppurio →
 * record the result in csm.transmission_history_&lt;inst&gt;. Mirrors csm's relaySendSms flow
 * (PageController) so both apps produce identical history rows.
 */
@Service
public class SmsSendService {

    private static final Logger log = LoggerFactory.getLogger(SmsSendService.class);

    private final ExternalSmsGatewayService gateway;
    private final SmsRepository repository;

    public SmsSendService(ExternalSmsGatewayService gateway, SmsRepository repository) {
        this.gateway = gateway;
        this.repository = repository;
    }

    public static final class SendResult {
        public final boolean success;
        public final Map<String, Object> response;

        SendResult(boolean success, Map<String, Object> response) {
            this.success = success;
            this.response = response;
        }
    }

    public SendResult send(String inst, SmsSendRequest request) {
        String type = Optional.ofNullable(request.getType()).orElse("sms").toLowerCase(Locale.ROOT);
        String from = Optional.ofNullable(request.getFrom()).orElse("");
        String to = Optional.ofNullable(request.getTo()).orElse("");
        String message = Optional.ofNullable(request.getMessage()).orElse("");
        String refkey = buildSmsRefkey(inst, request.getRefkey());

        Map<String, Object> typed = new HashMap<>();
        typed.put("message", message);
        if ("lms".equals(type) || "mms".equals(type)) {
            typed.put("subject", Optional.ofNullable(request.getSubject()).orElse(""));
        }
        if ("mms".equals(type) && request.getFiles() != null) {
            typed.put("file", request.getFiles());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("from", from);
        payload.put("to", to);
        payload.put("refkey", refkey);
        payload.put("content", Map.of(type, typed));
        if (request.getSendtime() != null && !request.getSendtime().isBlank()) {
            payload.put("sendtime", request.getSendtime());
        }

        LocalDateTime reserveTime = resolveSmsReserveTime(request.getSendtime());

        try {
            Map<String, Object> resp = gateway.send(payload);
            String desc = Optional.ofNullable(resp.get("description")).map(String::valueOf).orElse("");
            boolean success = "success".equalsIgnoreCase(desc);
            recordHistory(inst, message, from, to, success ? "SUCCESS" : "FAILURE",
                    String.valueOf(resp.getOrDefault("_raw", resp.toString())), refkey, type, reserveTime);
            return new SendResult(success, resp);
        } catch (Exception e) {
            log.error("[sms/send] send fail inst={}, to={}", inst, to, e);
            recordHistory(inst, message, from, to, "FAILURE", e.getMessage(), refkey, type, reserveTime);
            Map<String, Object> err = new HashMap<>();
            err.put("description", "fail");
            err.put("message", "문자 전송 중 오류 발생: " + e.getMessage());
            return new SendResult(false, err);
        }
    }

    private void recordHistory(String inst, String message, String from, String to, String status,
            String response, String refkey, String type, LocalDateTime reserveTime) {
        try {
            repository.insertTransmissionHistory(inst, message, from, to, status, response, refkey, type, reserveTime);
        } catch (Exception e) {
            log.error("[sms/send] history insert fail inst={}", inst, e);
        }
    }

    /** Ported from csm PageController.buildSmsRefkey. */
    private String buildSmsRefkey(String inst, String incomingRefkey) {
        String normalizedInst = inst == null ? "" : inst.replaceAll("[^A-Za-z0-9_]", "");
        if (incomingRefkey != null) {
            String trimmed = incomingRefkey.trim();
            if (!trimmed.isBlank() && trimmed.startsWith(normalizedInst)) {
                return trimmed;
            }
        }
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int rnd = 1000 + (int) (Math.random() * 9000);
        return normalizedInst + ts + rnd;
    }

    /** Ported from csm PageController.resolveSmsReserveTime. */
    private LocalDateTime resolveSmsReserveTime(Object sendtime) {
        if (sendtime == null || String.valueOf(sendtime).isBlank()) {
            return null;
        }
        try {
            long epochSeconds = Long.parseLong(String.valueOf(sendtime));
            Instant scheduledAt = Instant.ofEpochSecond(epochSeconds);
            if (!scheduledAt.isAfter(Instant.now().plusSeconds(60))) {
                return null;
            }
            return LocalDateTime.ofInstant(scheduledAt, ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }
}
