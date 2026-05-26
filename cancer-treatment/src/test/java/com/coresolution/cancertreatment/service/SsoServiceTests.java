package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class SsoServiceTests {

    private static final String SECRET = "unit-test-secret";

    private final SsoService service = new SsoService(SECRET, 60L, "/cancer-treatment-schedule");

    @Test
    void validatesLegacyLaunchWithoutRole() {
        long expires = Instant.now().getEpochSecond() + 30;
        String target = base64Url("/cancer-treatment-schedule");
        String signature = hmac("inst=FALH&userId=alice&expires=" + expires + "&target=" + target);

        String resolved = service.validateAndResolveTarget("FALH", "alice", expires, target, null, signature);

        assertThat(resolved).isEqualTo("/cancer-treatment-schedule");
    }

    @Test
    void validatesNewLaunchWithRoleInPayload() {
        long expires = Instant.now().getEpochSecond() + 30;
        String target = base64Url("/cancer-treatment-schedule");
        String signature = hmac("inst=FALH&userId=alice&expires=" + expires + "&target=" + target + "&role=MEMBER");

        String resolved = service.validateAndResolveTarget("FALH", "alice", expires, target, "MEMBER", signature);

        assertThat(resolved).isEqualTo("/cancer-treatment-schedule");
    }

    @Test
    void rejectsTamperedRole() {
        long expires = Instant.now().getEpochSecond() + 30;
        String target = base64Url("/cancer-treatment-schedule");
        // Signature was computed with role=VIEWER, but caller claims role=MEMBER → must fail.
        String legitimateSignature = hmac("inst=FALH&userId=alice&expires=" + expires + "&target=" + target + "&role=VIEWER");

        assertThatThrownBy(() -> service.validateAndResolveTarget(
                "FALH", "alice", expires, target, "MEMBER", legitimateSignature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("서명");
    }

    @Test
    void rejectsRoleStrippedAfterSigning() {
        // Signature was computed with role=MEMBER, attacker drops role from URL → must fail.
        long expires = Instant.now().getEpochSecond() + 30;
        String target = base64Url("/cancer-treatment-schedule");
        String memberSignature = hmac("inst=FALH&userId=alice&expires=" + expires + "&target=" + target + "&role=MEMBER");

        assertThatThrownBy(() -> service.validateAndResolveTarget(
                "FALH", "alice", expires, target, null, memberSignature))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExpired() {
        long expired = Instant.now().getEpochSecond() - 3600;
        String target = base64Url("/cancer-treatment-schedule");
        String signature = hmac("inst=FALH&userId=alice&expires=" + expired + "&target=" + target);

        assertThatThrownBy(() -> service.validateAndResolveTarget(
                "FALH", "alice", expired, target, null, signature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료");
    }

    @Test
    void canonicalRoleNormalizesInput() {
        assertThat(service.canonicalRole("MEMBER")).isEqualTo("MEMBER");
        assertThat(service.canonicalRole("member")).isEqualTo("MEMBER");
        assertThat(service.canonicalRole(" MEMBER ")).isEqualTo("MEMBER");
        assertThat(service.canonicalRole("VIEWER")).isEqualTo("VIEWER");
        assertThat(service.canonicalRole(null)).isEqualTo("VIEWER");
        assertThat(service.canonicalRole("")).isEqualTo("VIEWER");
        assertThat(service.canonicalRole("ADMIN")).isEqualTo("VIEWER"); // unknown → default deny
    }

    // --- helpers ---

    private static String base64Url(String path) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
