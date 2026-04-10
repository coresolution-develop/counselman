package com.coresolution.csm.serivce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MediplatSsoService {

    private final String sharedSecret;
    private final long allowedClockSkewSeconds;
    private final String defaultTarget;

    public MediplatSsoService(
            @Value("${mediplat.sso.shared-secret:}") String sharedSecret,
            @Value("${mediplat.sso.allowed-clock-skew-seconds:60}") long allowedClockSkewSeconds,
            @Value("${mediplat.sso.default-target:/counsel/list?page=1&perPageNum=10&comment=}") String defaultTarget) {
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
        this.allowedClockSkewSeconds = allowedClockSkewSeconds;
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
        return StringUtils.hasText(defaultTarget) ? defaultTarget : "/counsel/list?page=1&perPageNum=10&comment=";
    }

    private String canonicalPayload(String inst, String userId, long expires, String targetToken) {
        String normalizedTargetToken = targetToken == null ? "" : targetToken.trim();
        return "inst=" + inst.trim()
                + "&userId=" + userId.trim()
                + "&expires=" + expires
                + "&target=" + normalizedTargetToken;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
