package com.coresolution.csm.serivce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsmSmsOtpServiceTest {

    private CsmSmsOtpService service;

    @BeforeEach
    void setUp() {
        service = new CsmSmsOtpService();
    }

    @Test
    void issue_generates6DigitCode() {
        String code = service.issue("FALH", "123", "user@test.com", "01012345678");
        assertNotNull(code);
        assertTrue(code.matches("\\d{6}"), "OTP must be exactly 6 digits, but was: " + code);
    }

    @Test
    void verify_returnsOk_whenCodeMatches() {
        String code = service.issue("FALH", "123", "user@test.com", "01012345678");
        assertEquals(CsmSmsOtpService.VerifyResult.OK, service.verify("FALH", "123", code));
    }

    @Test
    void verify_returnsMismatch_whenCodeWrong() {
        service.issue("FALH", "123", "user@test.com", "01012345678");
        assertEquals(CsmSmsOtpService.VerifyResult.MISMATCH, service.verify("FALH", "123", "000000"));
    }

    @Test
    void verify_returnsNotFound_whenNoIssued() {
        assertEquals(CsmSmsOtpService.VerifyResult.NOT_FOUND, service.verify("FALH", "999", "123456"));
    }

    @Test
    void verify_returnsTooManyAttempts_after5Wrong() {
        service.issue("FALH", "123", "user@test.com", "01012345678");
        for (int i = 0; i < 5; i++) {
            service.verify("FALH", "123", "000000");
        }
        // 6번째 시도는 잠금
        assertEquals(CsmSmsOtpService.VerifyResult.TOO_MANY_ATTEMPTS,
                service.verify("FALH", "123", "000000"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verify_returnsExpired_whenTtlPassed() throws Exception {
        service.issue("FALH", "123", "user@test.com", "01012345678");

        Field storeField = CsmSmsOtpService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        Map<String, Object> store = (Map<String, Object>) storeField.get(service);
        // 만료 시각을 과거로 강제 변경
        Object entry = store.values().iterator().next();
        Field expiresAtField = entry.getClass().getDeclaredField("expiresAt");
        expiresAtField.setAccessible(true);
        expiresAtField.setLong(entry, System.currentTimeMillis() - 1000);

        assertEquals(CsmSmsOtpService.VerifyResult.EXPIRED, service.verify("FALH", "123", "000000"));
    }

    @Test
    void consume_removesEntry() {
        service.issue("FALH", "123", "user@test.com", "01012345678");
        CsmSmsOtpService.OtpEntry removed = service.consume("FALH", "123");
        assertNotNull(removed);
        assertEquals("FALH", removed.inst());
        assertEquals("123", removed.userId());
        assertEquals(CsmSmsOtpService.VerifyResult.NOT_FOUND, service.verify("FALH", "123", "000000"));
    }

    @Test
    void issue_overridesPreviousCodeForSameUser() {
        String first = service.issue("FALH", "123", "user@test.com", "01012345678");
        String second = service.issue("FALH", "123", "user@test.com", "01012345678");
        // 이전 코드는 더 이상 검증되지 않음
        assertEquals(CsmSmsOtpService.VerifyResult.MISMATCH, service.verify("FALH", "123", first));
        // 새 코드는 검증됨
        assertEquals(CsmSmsOtpService.VerifyResult.OK, service.verify("FALH", "123", second));
    }

    @Test
    void peek_returnsNullWhenExpired() throws Exception {
        service.issue("FALH", "123", "user@test.com", "01012345678");
        Field storeField = CsmSmsOtpService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> store = (Map<String, Object>) storeField.get(service);
        Object entry = store.values().iterator().next();
        Field expiresAtField = entry.getClass().getDeclaredField("expiresAt");
        expiresAtField.setAccessible(true);
        expiresAtField.setLong(entry, System.currentTimeMillis() - 1000);

        assertNull(service.peek("FALH", "123"));
    }
}
