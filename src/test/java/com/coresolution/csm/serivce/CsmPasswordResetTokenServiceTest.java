package com.coresolution.csm.serivce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsmPasswordResetTokenServiceTest {

    private CsmPasswordResetTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new CsmPasswordResetTokenService();
    }

    @Test
    void generateToken_bindsEmailInstAndUserId() {
        String token = tokenService.generateToken("  user@test.com ", " FALH ", " 123 ");

        CsmPasswordResetTokenService.ResetTokenContext context = tokenService.getTokenContext(token);
        assertNotNull(context);
        assertEquals("user@test.com", context.email());
        assertEquals("FALH", context.inst());
        assertEquals("123", context.userId());
    }

    @Test
    void getTokenContext_returnsNull_whenTokenBlank() {
        assertNull(tokenService.getTokenContext(null));
        assertNull(tokenService.getTokenContext(""));
        assertNull(tokenService.getTokenContext("   "));
    }

    @Test
    void invalidateToken_removesTokenContext() {
        String token = tokenService.generateToken("user@test.com", "FALH", "123");

        tokenService.invalidateToken(token);

        assertNull(tokenService.getTokenContext(token));
        assertNull(tokenService.getEmailByToken(token));
    }

    @Test
    void purgeExpiredTokens_removesOnlyExpiredEntries() throws Exception {
        String liveToken = tokenService.generateToken("live@test.com", "FALH", "1");
        String expiredToken = tokenService.generateToken("expired@test.com", "FALH", "2");

        // 만료 시각을 과거로 강제 설정
        Field expirationField = CsmPasswordResetTokenService.class.getDeclaredField("expirationStorage");
        expirationField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Long> expirationStorage =
                (java.util.Map<String, Long>) expirationField.get(tokenService);
        expirationStorage.put(expiredToken, System.currentTimeMillis() - 1000);

        tokenService.purgeExpiredTokens();

        assertNotNull(tokenService.getTokenContext(liveToken));
        assertNull(tokenService.getTokenContext(expiredToken));
    }
}
