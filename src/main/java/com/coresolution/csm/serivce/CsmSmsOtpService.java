package com.coresolution.csm.serivce;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CsmSmsOtpService {

    private static final Logger log = LoggerFactory.getLogger(CsmSmsOtpService.class);
    private static final long OTP_VALIDITY_MS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    public String issue(String inst, String userId, String email, String phone) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = key(inst, userId);
        store.put(key, new OtpEntry(
                code,
                normalize(inst),
                normalize(userId),
                normalize(email),
                normalize(phone),
                System.currentTimeMillis() + OTP_VALIDITY_MS,
                new AtomicInteger(0)));
        return code;
    }

    public VerifyResult verify(String inst, String userId, String code) {
        String key = key(inst, userId);
        OtpEntry entry = store.get(key);
        if (entry == null) {
            return VerifyResult.NOT_FOUND;
        }
        if (System.currentTimeMillis() > entry.expiresAt) {
            store.remove(key);
            return VerifyResult.EXPIRED;
        }
        if (entry.attempts.incrementAndGet() > MAX_ATTEMPTS) {
            store.remove(key);
            return VerifyResult.TOO_MANY_ATTEMPTS;
        }
        if (!entry.code.equals(StringUtils.trimWhitespace(code))) {
            return VerifyResult.MISMATCH;
        }
        return VerifyResult.OK;
    }

    public OtpEntry consume(String inst, String userId) {
        return store.remove(key(inst, userId));
    }

    public OtpEntry peek(String inst, String userId) {
        OtpEntry entry = store.get(key(inst, userId));
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAt) {
            store.remove(key(inst, userId));
            return null;
        }
        return entry;
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        int[] removed = {0};
        store.forEach((k, v) -> {
            if (now > v.expiresAt) {
                store.remove(k);
                removed[0]++;
            }
        });
        if (removed[0] > 0) {
            log.debug("[otp-purge] removed {} expired otp entry(ies)", removed[0]);
        }
    }

    private String key(String inst, String userId) {
        return normalize(inst) + "::" + normalize(userId);
    }

    private String normalize(String v) {
        return StringUtils.hasText(v) ? v.trim() : "";
    }

    public enum VerifyResult {
        OK, NOT_FOUND, EXPIRED, MISMATCH, TOO_MANY_ATTEMPTS
    }

    public static final class OtpEntry {
        final String code;
        final String inst;
        final String userId;
        final String email;
        final String phone;
        final long expiresAt;
        final AtomicInteger attempts;

        OtpEntry(String code, String inst, String userId, String email, String phone,
                 long expiresAt, AtomicInteger attempts) {
            this.code = code;
            this.inst = inst;
            this.userId = userId;
            this.email = email;
            this.phone = phone;
            this.expiresAt = expiresAt;
            this.attempts = attempts;
        }

        public String inst() { return inst; }
        public String userId() { return userId; }
        public String email() { return email; }
        public String phone() { return phone; }
    }
}
