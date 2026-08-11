package com.coresolution.csm.serivce;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class MediplatSsoServiceTest {

    private static final long MAX_TTL_SECONDS = 300L;

    private final MediplatSsoService service =
            new MediplatSsoService("test-shared-secret", 60L, MAX_TTL_SECONDS, "/counsel/list");

    private long future() {
        return Instant.now().getEpochSecond() + 3600;
    }

    private long past() {
        return Instant.now().getEpochSecond() - 3600;
    }

    private long inSeconds(long seconds) {
        return Instant.now().getEpochSecond() + seconds;
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
    void validateAndResolveTarget_passes_whenTtlWithinUpperBound() {
        // 발급 측(mediplat) 기본값은 60초 → 상한 이내이므로 정상 통과해야 한다
        long expires = inSeconds(60);
        String sig = service.sign("FALH", "user1", expires, "");

        assertDoesNotThrow(() -> service.validateAndResolveTarget("FALH", "user1", expires, "", sig));
    }

    @Test
    void validateAndResolveTarget_rejects_whenTtlExceedsUpperBound() {
        // 서명은 유효하지만 유효기간이 비정상적으로 길다 → 유출 URL 재사용 창을 막기 위해 거부
        long expires = inSeconds(MAX_TTL_SECONDS + 60);
        String sig = service.sign("FALH", "user1", expires, "");

        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndResolveTarget("FALH", "user1", expires, "", sig));
    }

    @Test
    void validateRoomBoardViewer_allowsLongLivedViewerCookie_notSubjectToSsoMaxTtl() {
        // 뷰어 쿠키는 csm이 스스로 30일짜리로 발급한다(RoomBoardController.VIEWER_PASS_TTL_SECONDS).
        // SSO 진입 토큰 상한을 여기까지 적용하면 정상 쿠키가 전부 거부된다.
        long expires = inSeconds(30L * 24 * 60 * 60);
        String sig = service.signRoomBoardViewer("FALH", "viewer1", expires);

        assertDoesNotThrow(() -> service.validateRoomBoardViewer("FALH", "viewer1", expires, sig));
    }

    @Test
    void tokenExpiredException_isIllegalArgument_forBackwardCompatibleHandling() {
        // 기존 catch(IllegalArgumentException) 호출부와의 호환성 보장
        assertTrue(IllegalArgumentException.class.isAssignableFrom(
                MediplatSsoService.TokenExpiredException.class));
    }
}
