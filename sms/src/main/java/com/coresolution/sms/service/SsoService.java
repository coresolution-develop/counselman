package com.coresolution.sms.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Validates the HMAC-SHA256 SSO handshake issued by the MediPlat portal
 * ({@code /launch/SMS}). Ported from cancer-treatment's SsoService — the SMS service
 * is NOT in the portal's SERVICES_WITH_ROLE set, so launches arrive with no role and
 * verify against the 4-field payload.
 */
@Service
public class SsoService {

    private static final String DEFAULT_TARGET = "/sms-send";

    private final String sharedSecret;
    private final long allowedClockSkewSeconds;
    private final String defaultTarget;

    public SsoService(
            @Value("${sms.sso.shared-secret:}") String sharedSecret,
            @Value("${sms.sso.allowed-clock-skew-seconds:60}") long allowedClockSkewSeconds,
            @Value("${sms.sso.default-target:/sms-send}") String defaultTarget) {
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
        this.allowedClockSkewSeconds = allowedClockSkewSeconds;
        this.defaultTarget = normalizeTarget(defaultTarget);
    }

    /**
     * Validates the SSO signature and returns the resolved target path.
     * Backwards compatible: legacy launches with no role still verify against the 4-field
     * payload; launches with role verify against the 5-field payload.
     */
    public String validateAndResolveTarget(
            String inst,
            String userId,
            long expires,
            String targetToken,
            String role,
            String signature) {
        if (!StringUtils.hasText(sharedSecret)) {
            throw new IllegalArgumentException("SSO shared secret is not configured.");
        }
        if (!StringUtils.hasText(inst) || !StringUtils.hasText(userId) || !StringUtils.hasText(signature)) {
            throw new IllegalArgumentException("필수 SSO 파라미터가 누락되었습니다.");
        }

        long now = Instant.now().getEpochSecond();
        if (expires < now - allowedClockSkewSeconds) {
            throw new IllegalArgumentException("SSO 요청이 만료되었습니다.");
        }

        String normalizedTargetToken = targetToken == null ? "" : targetToken.trim();
        String normalizedRole = role == null ? null : role.trim();
        if (normalizedRole != null && normalizedRole.isEmpty()) normalizedRole = null;

        String expectedSignature = sign(inst.trim(), userId.trim(), expires, normalizedTargetToken, normalizedRole);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("SSO 서명이 올바르지 않습니다.");
        }
        return decodeTarget(normalizedTargetToken);
    }

    /** Returns canonical role: "MEMBER" or "VIEWER" (default). Anything else maps to VIEWER. */
    public String canonicalRole(String role) {
        return "MEMBER".equalsIgnoreCase(role == null ? null : role.trim()) ? "MEMBER" : "VIEWER";
    }

    private String sign(String inst, String userId, long expires, String targetToken, String role) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(canonicalPayload(inst, userId, expires, targetToken, role).getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SSO signature generation failed.", e);
        }
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
            return DEFAULT_TARGET;
        }
        String target = rawTarget.trim();
        if (!target.startsWith("/") || target.startsWith("//") || target.contains("\r") || target.contains("\n")) {
            return DEFAULT_TARGET;
        }
        return target;
    }

    private String canonicalPayload(String inst, String userId, long expires, String targetToken, String role) {
        String normalizedTargetToken = targetToken == null ? "" : targetToken.trim();
        String payload = "inst=" + inst.trim()
                + "&userId=" + userId.trim()
                + "&expires=" + expires
                + "&target=" + normalizedTargetToken;
        if (role != null) {
            payload = payload + "&role=" + role;
        }
        return payload;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
