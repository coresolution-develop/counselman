package com.coresolution.csm.serivce;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class MediplatSsoServiceTest {

    private final MediplatSsoService service =
            new MediplatSsoService("test-shared-secret", 60L, "/counsel/list");

    private long future() {
        return Instant.now().getEpochSecond() + 3600;
    }

    private long past() {
        return Instant.now().getEpochSecond() - 3600;
    }

    @Test
    void validateRoomBoardViewer_passes_whenSignatureValidAndNotExpired() {
        long expires = future();
        String sig = service.signRoomBoardViewer("FALH", "viewer1", expires);

        assertDoesNotThrow(() -> service.validateRoomBoardViewer("FALH", "viewer1", expires, sig));
    }

    @Test
    void validateRoomBoardViewer_throwsTokenExpired_whenSignatureValidButExpired() {
        long expires = past();
        String sig = service.signRoomBoardViewer("FALH", "viewer1", expires);

        // 서명은 유효하나 만료 → self-heal 가능하도록 전용 예외로 구분되어야 한다
        assertThrows(MediplatSsoService.TokenExpiredException.class,
                () -> service.validateRoomBoardViewer("FALH", "viewer1", expires, sig));
    }

    @Test
    void validateRoomBoardViewer_throwsForgery_notExpired_whenSignatureInvalidEvenIfExpired() {
        long expires = past();

        // 서명을 먼저 검증하므로, 만료된 위조 토큰은 만료가 아니라 위조로 거부되어야 한다(403 대상)
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.validateRoomBoardViewer("FALH", "viewer1", expires, "forged-signature"));
        assertFalse(ex instanceof MediplatSsoService.TokenExpiredException);
    }

    @Test
    void validateRoomBoardViewer_throwsForgery_whenSignatureInvalidAndNotExpired() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.validateRoomBoardViewer("FALH", "viewer1", future(), "forged-signature"));
        assertFalse(ex instanceof MediplatSsoService.TokenExpiredException);
    }

    @Test
    void tokenExpiredException_isIllegalArgument_forBackwardCompatibleHandling() {
        // 기존 catch(IllegalArgumentException) 호출부와의 호환성 보장
        assertTrue(IllegalArgumentException.class.isAssignableFrom(
                MediplatSsoService.TokenExpiredException.class));
    }
}
