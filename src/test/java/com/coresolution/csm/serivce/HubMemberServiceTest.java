package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.csm.vo.HubMember;
import com.coresolution.csm.vo.HubMemberSession;

/**
 * Phase 1 인증 로직 검증: 가입코드 차단, 이메일 중복, BCrypt 저장, 로그인 분기.
 */
@ExtendWith(MockitoExtension.class)
class HubMemberServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private HubMemberService service;

    @BeforeEach
    void setUp() {
        service = new HubMemberService(jdbcTemplate);
        ReflectionTestUtils.setField(service, "signupCode", "core");
    }

    @Test
    void signup_rejectsWrongSignupCode() {
        assertThatThrownBy(() -> service.signup("a@coresolution.kr", "password123", "홍길동", "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가입코드");
        verify(jdbcTemplate, never()).update(contains("INSERT INTO csm.hub_member"), any(), any(), any());
    }

    @Test
    void signup_acceptsSingleCharPassword() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM csm.hub_member"), eq(Integer.class), eq("a@coresolution.kr")))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(1L);

        HubMemberSession member = service.signup("a@coresolution.kr", "x", "홍길동", "core");

        assertThat(member.getId()).isEqualTo(1L);
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member"), eq("a@coresolution.kr"), any(), eq("홍길동"));
    }

    @Test
    void signup_rejectsBlankPassword() {
        assertThatThrownBy(() -> service.signup("a@coresolution.kr", "   ", "홍길동", "core"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호");
    }

    @Test
    void signup_rejectsInvalidEmail() {
        assertThatThrownBy(() -> service.signup("not-an-email", "password123", "홍길동", "core"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일");
    }

    @Test
    void signup_rejectsDuplicateEmail() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM csm.hub_member"), eq(Integer.class), eq("a@coresolution.kr")))
                .thenReturn(1);

        assertThatThrownBy(() -> service.signup("A@coresolution.KR", "password123", "홍길동", "core"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 가입");
    }

    @Test
    void signup_storesBcryptHashAndNormalizedEmail_thenReturnsSession() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM csm.hub_member"), eq(Integer.class), eq("a@coresolution.kr")))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);

        HubMemberSession member = service.signup("  A@Coresolution.KR ", "password123", "  홍길동 ", "core");

        assertThat(member.getId()).isEqualTo(42L);
        assertThat(member.getEmail()).isEqualTo("a@coresolution.kr");
        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getRole()).isEqualTo("USER");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member"),
                eq("a@coresolution.kr"), hash.capture(), eq("홍길동"));
        // 평문 저장 금지 + BCrypt 매칭 확인
        assertThat(hash.getValue()).isNotEqualTo("password123");
        assertThat(new BCryptPasswordEncoder().matches("password123", hash.getValue())).isTrue();
        verify(jdbcTemplate).update(contains("UPDATE csm.hub_member SET last_login_at"), eq(42L));
    }

    @Test
    void authenticate_returnsNullForUnknownEmail() {
        stubFindByEmail("ghost@coresolution.kr", java.util.List.of());
        assertThat(service.authenticate("ghost@coresolution.kr", "password123")).isNull();
    }

    @Test
    void authenticate_returnsNullForDisabledStatus() {
        stubFindByEmail("a@coresolution.kr",
                java.util.List.of(member("a@coresolution.kr", "password123", "DISABLED")));
        assertThat(service.authenticate("a@coresolution.kr", "password123")).isNull();
    }

    @Test
    void authenticate_returnsNullForWrongPassword() {
        stubFindByEmail("a@coresolution.kr",
                java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));
        assertThat(service.authenticate("a@coresolution.kr", "wrong-password")).isNull();
    }

    @Test
    void authenticate_succeeds_andTouchesLastLogin() {
        stubFindByEmail("a@coresolution.kr",
                java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));

        HubMemberSession session = service.authenticate("  A@Coresolution.KR ", "password123");

        assertThat(session).isNotNull();
        assertThat(session.getEmail()).isEqualTo("a@coresolution.kr");
        assertThat(session.getName()).isEqualTo("홍길동");
        verify(jdbcTemplate).update(contains("UPDATE csm.hub_member SET last_login_at"), eq(7L));
    }

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        stubFindById(7L, java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));

        assertThatThrownBy(() -> service.changePassword(7L, "wrong-current", "newpass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호");
        verify(jdbcTemplate, never()).update(contains("SET password"), any(), any());
    }

    @Test
    void changePassword_rejectsBlankNewPassword() {
        stubFindById(7L, java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));

        assertThatThrownBy(() -> service.changePassword(7L, "password123", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호");
    }

    @Test
    void changePassword_succeeds_storesNewBcryptHash() {
        stubFindById(7L, java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));

        service.changePassword(7L, "password123", "brandNew9");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(contains("UPDATE csm.hub_member SET password"), hash.capture(), eq(7L));
        assertThat(hash.getValue()).isNotEqualTo("brandNew9");
        assertThat(new BCryptPasswordEncoder().matches("brandNew9", hash.getValue())).isTrue();
    }

    @SuppressWarnings("unchecked")
    private void stubFindByEmail(String email, java.util.List<HubMember> rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(email))).thenReturn(rows);
    }

    @SuppressWarnings("unchecked")
    private void stubFindById(long id, java.util.List<HubMember> rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(id))).thenReturn(rows);
    }

    private HubMember member(String email, String rawPassword, String status) {
        HubMember m = new HubMember();
        m.setId(7L);
        m.setEmail(email);
        m.setPassword(new BCryptPasswordEncoder().encode(rawPassword));
        m.setName("홍길동");
        m.setRole("USER");
        m.setStatus(status);
        return m;
    }
}
