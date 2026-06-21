package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * "이 기기 기억하기" 토큰 검증: 평문 미저장(해시만), 상수시간 비교, 만료/불일치 거부,
 * 토큰 회전, 기기별 폐기.
 */
@ExtendWith(MockitoExtension.class)
class HubRememberServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private HubMemberService hubMemberService;

    private HubRememberService service;

    @BeforeEach
    void setUp() {
        service = new HubRememberService(jdbcTemplate, hubMemberService);
        ReflectionTestUtils.setField(service, "rememberDays", 30);
        ReflectionTestUtils.setField(service, "cookieSecure", true);
    }

    @Test
    void issue_storesHashNotPlaintext_andReturnsSelectorValidatorCookie() {
        String cookie = service.issue(7L, "Mozilla/5.0");

        assertThat(cookie).contains(":");
        String selector = cookie.substring(0, cookie.indexOf(':'));
        String validator = cookie.substring(cookie.indexOf(':') + 1);

        ArgumentCaptor<String> selCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hashCap = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member_token"),
                eq(7L), selCap.capture(), hashCap.capture(), any(), any(), any());

        assertThat(selCap.getValue()).isEqualTo(selector);
        // DB에는 validator의 SHA-256 해시만 — 평문 validator 저장 금지
        assertThat(hashCap.getValue()).isNotEqualTo(validator);
        assertThat(hashCap.getValue()).isEqualTo(sha256Hex(validator));
    }

    @Test
    void validateAndRotate_match_restoresAndRotates() {
        String validator = "known-validator-secret";
        stubSelector("sel", new HubRememberService.TokenRow(1L, 7L, sha256Hex(validator), future()));
        when(jdbcTemplate.query(contains("SELECT status"), any(RowMapper.class), eq(7L)))
                .thenReturn(List.of("ACTIVE"));

        HubRememberService.Result r = service.validateAndRotate("sel:" + validator, "UA");

        assertThat(r.valid()).isTrue();
        assertThat(r.memberId()).isEqualTo(7L);
        assertThat(r.newCookieValue()).startsWith("sel:").isNotEqualTo("sel:" + validator);
        // 회전: token_hash·expires·last_used 갱신
        verify(jdbcTemplate).update(contains("UPDATE csm.hub_member_token"),
                any(), any(), any(), any(), eq(1L));
    }

    @Test
    void validateAndRotate_unknownSelector_invalid() {
        when(jdbcTemplate.query(contains("hub_member_token"), any(RowMapper.class), eq("nope")))
                .thenReturn(List.of());

        assertThat(service.validateAndRotate("nope:whatever", "UA").valid()).isFalse();
    }

    @Test
    void validateAndRotate_expired_deletedAndInvalid() {
        stubSelector("sel", new HubRememberService.TokenRow(1L, 7L, sha256Hex("v"), past()));

        assertThat(service.validateAndRotate("sel:v", "UA").valid()).isFalse();
        verify(jdbcTemplate).update(contains("DELETE FROM csm.hub_member_token WHERE id"), eq(1L));
    }

    @Test
    void validateAndRotate_validatorMismatch_revokesTokenAndInvalid() {
        stubSelector("sel", new HubRememberService.TokenRow(1L, 7L, sha256Hex("real-validator"), future()));

        HubRememberService.Result r = service.validateAndRotate("sel:wrong-validator", "UA");

        assertThat(r.valid()).isFalse();
        verify(jdbcTemplate).update(contains("DELETE FROM csm.hub_member_token WHERE id"), eq(1L));
        verify(jdbcTemplate, never()).update(contains("UPDATE csm.hub_member_token"),
                any(), any(), any(), any(), anyLong());
    }

    @Test
    void validateAndRotate_inactiveMember_invalid() {
        String validator = "v";
        stubSelector("sel", new HubRememberService.TokenRow(1L, 7L, sha256Hex(validator), future()));
        when(jdbcTemplate.query(contains("SELECT status"), any(RowMapper.class), eq(7L)))
                .thenReturn(List.of("DISABLED"));

        assertThat(service.validateAndRotate("sel:" + validator, "UA").valid()).isFalse();
        verify(jdbcTemplate).update(contains("DELETE FROM csm.hub_member_token WHERE id"), eq(1L));
    }

    @Test
    void deleteByCookie_deletesBySelector() {
        service.deleteByCookie("sel:validator");
        verify(jdbcTemplate).update(contains("DELETE FROM csm.hub_member_token WHERE selector"), eq("sel"));
    }

    @Test
    void deleteOthersForMember_keepsCurrentSelector() {
        service.deleteOthersForMember(7L, "sel:validator");
        verify(jdbcTemplate).update(contains("member_id = ? AND selector <> ?"), eq(7L), eq("sel"));
    }

    @Test
    void deleteOthersForMember_noCookie_deletesAll() {
        service.deleteOthersForMember(7L, null);
        verify(jdbcTemplate).update(contains("DELETE FROM csm.hub_member_token WHERE member_id = ?"), eq(7L));
    }

    @SuppressWarnings("unchecked")
    private void stubSelector(String selector, HubRememberService.TokenRow row) {
        when(jdbcTemplate.query(contains("hub_member_token"), any(RowMapper.class), eq(selector)))
                .thenReturn(List.of(row));
    }

    private LocalDateTime future() {
        return LocalDateTime.now().plusDays(10);
    }

    private LocalDateTime past() {
        return LocalDateTime.now().minusDays(1);
    }

    private String sha256Hex(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
