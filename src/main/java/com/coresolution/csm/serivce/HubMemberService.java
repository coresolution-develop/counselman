package com.coresolution.csm.serivce;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.coresolution.csm.vo.HubMember;
import com.coresolution.csm.vo.HubMemberSession;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * 허브 개인화 계층의 스키마 소유 + 독립 회원 계정(가입/로그인/비번) 서비스.
 *
 * <p>스키마는 CompanyLinkService와 동일한 관행을 따른다: {@code csm.} 접두사,
 * {@code CREATE TABLE IF NOT EXISTS} 멱등 생성, InnoDB/utf8mb4, use_yn 소프트삭제.
 * 링크 카탈로그(csm.company_link)는 재사용하므로 여기서 생성하지 않는다.
 *
 * <p>인증은 csm 직원/기관 로그인과 독립된 신원이다. 비밀번호는 BCrypt 해시로만 저장한다.
 */
@Service
@RequiredArgsConstructor
public class HubMemberService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HubMemberService.class);

    private static final String ROLE_USER = "USER";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int PASSWORD_MIN_LENGTH = 1;

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 자가가입 검증 코드. 비어 있으면 코드 검증을 생략한다(완전 오픈, 비권장). */
    @Value("${hub.signup.code:}")
    private String signupCode;

    /**
     * 부팅 시 한 번 테이블을 보장한다. DB 일시 장애로 전체 기동이 막히지 않도록
     * 실패는 WARN으로 흘리고, 이후 첫 서비스 호출의 ensureTables()가 재시도한다.
     */
    @PostConstruct
    public void warmUp() {
        try {
            ensureTables();
        } catch (RuntimeException e) {
            log.warn("[hub] ensureTables warm-up skipped: {}", e.getMessage());
        }
    }

    // ── 가입 / 로그인 ─────────────────────────────────────────────────────────

    /**
     * 자가가입: 가입코드 검증 → 이메일 정규화/중복 검사 → BCrypt 저장 → 자동 로그인용 세션 반환.
     * 검증 실패는 IllegalArgumentException(필드 메시지)로 던지고 컨트롤러가 폼을 재표시한다.
     */
    @Transactional
    public HubMemberSession signup(String email, String rawPassword, String name, String signupCodeInput) {
        ensureTables();
        validateSignupCode(signupCodeInput);
        String normalizedEmail = requireEmail(email);
        String normalizedName = requireText(name, "이름", 100);
        validatePassword(rawPassword);
        if (existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        String hash = passwordEncoder.encode(rawPassword.trim());
        jdbcTemplate.update("""
                INSERT INTO csm.hub_member (email, password, name)
                VALUES (?, ?, ?)
                """, normalizedEmail, hash, normalizedName);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        touchLastLogin(id);
        return new HubMemberSession(id, normalizedEmail, normalizedName, ROLE_USER);
    }

    /**
     * 로그인: 이메일로 회원 조회 → status ACTIVE 확인 → BCrypt 매칭 → last_login_at 갱신.
     * 실패 시 null(동일 메시지로 사용자/비번 노출 차이를 숨긴다).
     */
    public HubMemberSession authenticate(String email, String rawPassword) {
        ensureTables();
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || !StringUtils.hasText(rawPassword)) {
            return null;
        }
        HubMember member = findByEmail(normalizedEmail);
        if (member == null) {
            return null;
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(member.getStatus())) {
            return null;
        }
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            return null;
        }
        touchLastLogin(member.getId());
        return new HubMemberSession(member.getId(), member.getEmail(), member.getName(),
                StringUtils.hasText(member.getRole()) ? member.getRole() : ROLE_USER);
    }

    /**
     * 비밀번호 변경: 현재 비밀번호를 BCrypt로 확인한 뒤에만 새 비밀번호로 교체한다.
     * 현재 비번 불일치/계정 없음은 IllegalArgumentException.
     */
    @Transactional
    public void changePassword(long memberId, String currentPassword, String newPassword) {
        ensureTables();
        if (memberId <= 0) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        HubMember member = findById(memberId);
        if (member == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        validatePassword(newPassword);
        jdbcTemplate.update(
                "UPDATE csm.hub_member SET password = ? WHERE id = ?",
                passwordEncoder.encode(newPassword.trim()), memberId);
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private void validateSignupCode(String input) {
        if (!StringUtils.hasText(signupCode)) {
            return; // 코드 미설정 = 검증 생략
        }
        if (input == null || !signupCode.trim().equals(input.trim())) {
            throw new IllegalArgumentException("가입코드가 올바르지 않습니다.");
        }
    }

    private void validatePassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword) || rawPassword.trim().length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("비밀번호를 입력해주세요.");
        }
    }

    private boolean existsByEmail(String normalizedEmail) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM csm.hub_member WHERE email = ?",
                Integer.class, normalizedEmail);
        return count != null && count > 0;
    }

    private HubMember findByEmail(String normalizedEmail) {
        List<HubMember> rows = jdbcTemplate.query("""
                SELECT id, email, password, name, role, status,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                       DATE_FORMAT(last_login_at, '%Y-%m-%d %H:%i:%s') AS last_login_at
                  FROM csm.hub_member
                 WHERE email = ?
                 LIMIT 1
                """, (rs, rowNum) -> {
            HubMember member = new HubMember();
            member.setId(rs.getLong("id"));
            member.setEmail(rs.getString("email"));
            member.setPassword(rs.getString("password"));
            member.setName(rs.getString("name"));
            member.setRole(rs.getString("role"));
            member.setStatus(rs.getString("status"));
            member.setCreatedAt(rs.getString("created_at"));
            member.setLastLoginAt(rs.getString("last_login_at"));
            return member;
        }, normalizedEmail);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 자동 재로그인 등에서 세션 복원용 — 활성(status=ACTIVE) 회원만 반환, 아니면 null. */
    public HubMember findActiveById(long id) {
        ensureTables();
        if (id <= 0) {
            return null;
        }
        HubMember member = findById(id);
        if (member == null || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            return null;
        }
        return member;
    }

    private HubMember findById(long id) {
        List<HubMember> rows = jdbcTemplate.query("""
                SELECT id, email, password, name, role, status,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                       DATE_FORMAT(last_login_at, '%Y-%m-%d %H:%i:%s') AS last_login_at
                  FROM csm.hub_member
                 WHERE id = ?
                 LIMIT 1
                """, (rs, rowNum) -> {
            HubMember member = new HubMember();
            member.setId(rs.getLong("id"));
            member.setEmail(rs.getString("email"));
            member.setPassword(rs.getString("password"));
            member.setName(rs.getString("name"));
            member.setRole(rs.getString("role"));
            member.setStatus(rs.getString("status"));
            member.setCreatedAt(rs.getString("created_at"));
            member.setLastLoginAt(rs.getString("last_login_at"));
            return member;
        }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void touchLastLogin(Long id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE csm.hub_member SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    private String requireEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
        return normalized;
    }

    /** 이메일 정규화(trim+소문자) 및 최소 형식 검사. 형식 오류면 null. */
    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 190 || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return null;
        }
        return normalized;
    }

    private String requireText(String value, String label, int maxLen) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(label + "을(를) 입력해주세요.");
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    // ── 스키마 ────────────────────────────────────────────────────────────────

    /** 4개 개인화 테이블을 멱등 생성한다. 모든 public 서비스 메서드 진입부에서 호출. */
    public void ensureTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.hub_member (
                    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                    email         VARCHAR(190) NOT NULL UNIQUE,
                    password      VARCHAR(100) NOT NULL,
                    name          VARCHAR(100) NOT NULL,
                    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
                    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    last_login_at DATETIME     NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.hub_member_favorite (
                    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                    member_id  BIGINT NOT NULL,
                    link_id    BIGINT NOT NULL,
                    sort_order INT    NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_fav (member_id, link_id),
                    KEY idx_fav_member (member_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.hub_member_custom_link (
                    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                    member_id  BIGINT       NOT NULL,
                    title      VARCHAR(200) NOT NULL,
                    url        VARCHAR(500) NOT NULL,
                    memo       VARCHAR(300) NULL,
                    sort_order INT          NOT NULL DEFAULT 0,
                    use_yn     CHAR(1)      NOT NULL DEFAULT 'Y',
                    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_custom_member (member_id, use_yn, sort_order)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.hub_member_link_history (
                    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                    member_id      BIGINT       NOT NULL,
                    link_type      VARCHAR(10)  NOT NULL,
                    link_id        BIGINT       NULL,
                    custom_link_id BIGINT       NULL,
                    title_snapshot VARCHAR(200) NOT NULL,
                    url_snapshot   VARCHAR(500) NOT NULL,
                    accessed_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_hist_member_time (member_id, accessed_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        // "이 기기 기억하기" 영속 로그인 토큰. selector로 행을 찾고 validator의 해시(token_hash)를
        // 상수시간 비교한다. validator 평문은 절대 저장하지 않는다. 인증 근거가 메모리 세션이 아니라
        // 이 테이블 + 브라우저 쿠키이므로 서버 재시작/배포 후에도 세션을 복원할 수 있다.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.hub_member_token (
                    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                    member_id    BIGINT       NOT NULL,
                    selector     VARCHAR(24)  NOT NULL UNIQUE,
                    token_hash   VARCHAR(100) NOT NULL,
                    expires_at   DATETIME     NOT NULL,
                    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    last_used_at DATETIME     NULL,
                    user_agent   VARCHAR(255) NULL,
                    KEY idx_token_member (member_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }
}
