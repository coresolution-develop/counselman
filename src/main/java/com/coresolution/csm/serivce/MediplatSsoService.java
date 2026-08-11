package com.coresolution.csm.serivce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MediplatSsoService {

    private static final Logger log = LoggerFactory.getLogger(MediplatSsoService.class);

    private final String sharedSecret;
    private final long allowedClockSkewSeconds;
    private final long maxTtlSeconds;
    private final String defaultTarget;

    public MediplatSsoService(
            @Value("${mediplat.sso.shared-secret:}") String sharedSecret,
            @Value("${mediplat.sso.allowed-clock-skew-seconds:60}") long allowedClockSkewSeconds,
            @Value("${mediplat.sso.max-ttl-seconds:300}") long maxTtlSeconds,
            @Value("${mediplat.sso.default-target:/counsel/list}") String defaultTarget) {
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
        this.allowedClockSkewSeconds = allowedClockSkewSeconds;
        this.maxTtlSeconds = maxTtlSeconds;
        this.defaultTarget = normalizeTarget(defaultTarget);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(sharedSecret);
    }

    public String validateAndResolveTarget(
            String inst,
            String userId,
            long expires,
            String targetToken,
            String signature) {
        if (!isConfigured()) {
            throw new IllegalArgumentException("MediPlat SSO shared secret is not configured.");
        }
        if (!StringUtils.hasText(inst) || !StringUtils.hasText(userId) || !StringUtils.hasText(signature)) {
            throw new IllegalArgumentException("필수 SSO 파라미터가 누락되었습니다.");
        }

        long now = Instant.now().getEpochSecond();
        if (expires < now - allowedClockSkewSeconds) {
            throw new IllegalArgumentException("SSO 요청이 만료되었습니다.");
        }
        // 상한 검증. 하한만 보면 발급 측이 expires를 크게 잡을수록 토큰이 오래 살아남는다.
        // 서명이 유효해도 유효기간이 비정상적으로 길면 거부한다 — 유출된 URL의 재사용 창을 제한한다.
        // 발급 측(mediplat) 기본값은 60초이므로 정상 트래픽은 영향을 받지 않는다.
        if (maxTtlSeconds > 0 && expires - now > maxTtlSeconds) {
            log.warn("[mediplat-sso] rejected: expires too far in the future. inst={}, userId={}, ttl={}s, maxTtl={}s",
                    inst, userId, expires - now, maxTtlSeconds);
            throw new IllegalArgumentException("SSO 요청의 유효기간이 허용 범위를 초과했습니다.");
        }

        String normalizedTargetToken = targetToken == null ? "" : targetToken.trim();
        String expectedSignature = sign(inst.trim(), userId.trim(), expires, normalizedTargetToken);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("SSO 서명이 올바르지 않습니다.");
        }

        return decodeTarget(normalizedTargetToken);
    }

    public String sign(String inst, String userId, long expires, String targetToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(canonicalPayload(inst, userId, expires, targetToken).getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("MediPlat SSO signature generation failed.", e);
        }
    }

    public String signRoomBoardViewer(String inst, String userId, long expires) {
        if (!isConfigured()) {
            throw new IllegalStateException("MediPlat SSO shared secret is not configured.");
        }
        if (!StringUtils.hasText(inst) || !StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("병실현황판 토큰 생성 파라미터가 올바르지 않습니다.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(canonicalRoomBoardPayload(inst, userId, expires).getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("MediPlat room-board token signature generation failed.", e);
        }
    }

    public void validateRoomBoardViewer(String inst, String userId, long expires, String signature) {
        if (!isConfigured()) {
            throw new IllegalArgumentException("MediPlat SSO shared secret is not configured.");
        }
        if (!StringUtils.hasText(inst) || !StringUtils.hasText(userId) || !StringUtils.hasText(signature)) {
            throw new IllegalArgumentException("병실현황판 접근 토큰 파라미터가 누락되었습니다.");
        }
        // 서명을 먼저 검증한다: 위조(서명 불일치)는 즉시 거부 대상이고, 서명이 유효한데 만료된
        // 경우만 별도 예외로 구분해 호출부가 self-heal(포털 재발급 유도)로 흘려보낼 수 있게 한다.
        String expectedSignature = signRoomBoardViewer(inst.trim(), userId.trim(), expires);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("병실현황판 접근 토큰이 올바르지 않습니다.");
        }

        long now = Instant.now().getEpochSecond();
        if (expires < now - allowedClockSkewSeconds) {
            throw new TokenExpiredException("병실현황판 접근 토큰이 만료되었습니다.");
        }
        // 여기에는 mediplat.sso.max-ttl-seconds 상한을 적용하지 않는다.
        // 이 토큰은 SSO 진입 토큰과 달리 csm이 스스로 발급하는 뷰어 쿠키로,
        // RoomBoardController.VIEWER_PASS_TTL_SECONDS(30일)를 의도적으로 사용한다.
        // 상한을 걸면 정상 뷰어 쿠키가 전부 거부된다.
    }

    private String decodeTarget(String targetToken) {
        if (!StringUtils.hasText(targetToken)) {
            return defaultTarget;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(targetToken);
            return normalizeTarget(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("target 값이 올바르지 않습니다.");
        }
    }

    private String normalizeTarget(String rawTarget) {
        if (!StringUtils.hasText(rawTarget)) {
            return defaultTargetOrFallback();
        }
        String target = rawTarget.trim();
        if (target.startsWith("/csm/")) {
            target = target.substring(4);
        } else if ("/csm".equals(target)) {
            target = "/";
        }
        if (!target.startsWith("/") || target.startsWith("//") || target.contains("\r") || target.contains("\n")) {
            return defaultTargetOrFallback();
        }
        return target;
    }

    private String defaultTargetOrFallback() {
        return StringUtils.hasText(defaultTarget) ? defaultTarget : "/counsel/list";
    }

    private String canonicalPayload(String inst, String userId, long expires, String targetToken) {
        String normalizedTargetToken = targetToken == null ? "" : targetToken.trim();
        return "inst=" + inst.trim()
                + "&userId=" + userId.trim()
                + "&expires=" + expires
                + "&target=" + normalizedTargetToken;
    }

    private String canonicalRoomBoardPayload(String inst, String userId, long expires) {
        return "room-board|inst=" + inst.trim()
                + "&userId=" + userId.trim()
                + "&expires=" + expires;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 서명은 유효하나 만료된 병실현황판 토큰을 나타낸다. 위조(서명 불일치)와 구분해, 호출부가
     * 만료를 "토큰 없음"처럼 취급하고 self-heal(쿠키 폴백 → 포털 재발급)로 처리하도록 한다.
     * IllegalArgumentException 을 상속하므로 기존 호출부의 포괄 처리와도 호환된다.
     */
    public static class TokenExpiredException extends IllegalArgumentException {
        public TokenExpiredException(String message) {
            super(message);
        }
    }
}
