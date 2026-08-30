package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
        // ensureTables()의 멱등 컬럼 마이그레이션 조회는 "컬럼 있음"으로 응답해 ALTER를 건너뛰게 한다.
        lenient().when(jdbcTemplate.queryForObject(contains("information_schema"), eq(Integer.class), any(), any()))
                .thenReturn(1);
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

    // ── 비밀번호 찾기 (이메일 + 이름 + 가입코드) ────────────────────────────

    @Test
    void verifyForReset_rejectsWrongSignupCode() {
        // 가입코드가 실제 방어선이다. 이메일·이름이 맞아도 코드가 틀리면 통과하지 못한다.
        assertThatThrownBy(() -> service.verifyForReset("a@coresolution.kr", "홍길동", "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가입코드");
    }

    @Test
    void verifyForReset_rejectsWrongName() {
        stubFindByEmail("a@coresolution.kr",
                java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));
        assertThatThrownBy(() -> service.verifyForReset("a@coresolution.kr", "김철수", "core"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyForReset_hidesWhetherTheEmailExists() {
        // 없는 계정과 이름 불일치가 **같은 문구**여야 한다.
        // 갈리면 어떤 이메일이 가입돼 있는지 알려주는 셈이다(회원 열거).
        stubFindByEmail("known@coresolution.kr",
                java.util.List.of(member("known@coresolution.kr", "password123", "ACTIVE")));
        stubFindByEmail("ghost@coresolution.kr", java.util.List.of());

        String wrongName = catchMessage(() -> service.verifyForReset("known@coresolution.kr", "김철수", "core"));
        String noAccount = catchMessage(() -> service.verifyForReset("ghost@coresolution.kr", "홍길동", "core"));

        assertThat(wrongName).isNotNull().isEqualTo(noAccount);
    }

    @Test
    void verifyForReset_rejectsDisabledAccount() {
        stubFindByEmail("a@coresolution.kr",
                java.util.List.of(member("a@coresolution.kr", "password123", "DISABLED")));
        assertThatThrownBy(() -> service.verifyForReset("a@coresolution.kr", "홍길동", "core"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyForReset_succeeds_andNormalizesInput() {
        stubFindByEmail("a@coresolution.kr",
                java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));
        assertThat(service.verifyForReset("  A@Coresolution.KR ", "  홍길동 ", "core")).isEqualTo(7L);
    }

    @Test
    void resetPassword_storesBcryptHash_withoutAskingCurrentPassword() {
        stubFindById(7L, java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));

        service.resetPassword(7L, "newpassword456");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> hash = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sql.capture(), hash.capture(), eq(7L));
        assertThat(sql.getValue()).contains("UPDATE csm.hub_member SET password");
        assertThat(hash.getValue().toString()).startsWith("$2");
        assertThat(hash.getValue().toString()).isNotEqualTo("newpassword456");
    }

    @Test
    void resetPassword_rejectsBlankPassword() {
        stubFindById(7L, java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));
        assertThatThrownBy(() -> service.resetPassword(7L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jdbcTemplate, never()).update(contains("SET password"), any(), any());
    }

    /**
     * <b>비밀번호에 길이·문자 종류 제한을 두지 않는다 (2026-08-30 결정).</b>
     *
     * <p>{@code PASSWORD_MIN_LENGTH = 1} 은 실수가 아니라 결정이다. 빈 값만 막는다.
     * 사내 전용 링크 허브라 계정 탈취의 실익이 작고, 규칙을 걸면 사람들이 규칙에 맞춘
     * 뻔한 비밀번호를 만든다는 판단이다.
     *
     * <p>⚠️ <b>이 테스트가 깨졌다면 누군가 최소 길이를 올린 것이다.</b> 되돌리기 전에
     * 결정이 바뀐 것인지 먼저 확인할 것 — 바뀌었다면 회원가입 · 계정설정 화면의
     * "8자 이상, 영문·숫자 포함" 문구도 함께 손봐야 한다(지금은 그 두 화면만 그 문구가 남아 있다).
     */
    @Test
    void resetPassword_hasNoLengthRequirement_byDecision() {
        stubFindById(7L, java.util.List.of(member("a@coresolution.kr", "password123", "ACTIVE")));
        service.resetPassword(7L, "a");
        verify(jdbcTemplate).update(contains("SET password"), any(), eq(7L));
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
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
