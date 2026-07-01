package com.coresolution.csm.serivce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * "이 기기 기억하기" 영속 로그인 토큰 서비스.
 *
 * <p>쿠키 값은 {@code selector:validator} 형식이다. selector는 행을 빠르게 찾기 위한 공개
 * 식별자이고, validator는 시크릿이다. DB에는 validator의 SHA-256 해시(token_hash)만 저장하며
 * 평문은 절대 저장하지 않는다. 검증은 {@link MessageDigest#isEqual}로 상수시간 비교한다.
 *
 * <p>인증 근거가 메모리 세션이 아니라 DB 토큰 + 브라우저 쿠키이므로, 서버가 배포/재시작돼도
 * 쿠키를 가진 기기는 세션을 복원할 수 있다(자동 재로그인). 검증 성공 시 validator를 회전(rotate)해
 * 도난 토큰 재사용을 막고 슬라이딩 만료를 적용한다.
 *
 * <p>servlet에 의존하지 않는 순수 로직이라 mock JdbcTemplate으로 단위 테스트한다. 쿠키 읽기/쓰기는
 * 웹 계층(HubRememberCookies)이 담당한다.
 */
@Service
@RequiredArgsConstructor
public class HubRememberService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HubRememberService.class);

    private static final int SELECTOR_BYTES = 12;   // base64url ~16자 (VARCHAR(24) 이내)
    private static final int VALIDATOR_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final JdbcTemplate jdbcTemplate;
    private final HubMemberService hubMemberService;

    @Value("${hub.remember.days:30}")
    private int rememberDays;

    /** 운영(HTTPS)에서는 true. 로컬 http 테스트 시에만 false로 내릴 수 있다. */
    @Value("${hub.remember.cookie-secure:true}")
    private boolean cookieSecure;

    public int getRememberDays() {
        return rememberDays;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public long getCookieMaxAgeSeconds() {
        return (long) rememberDays * 24 * 60 * 60;
    }

    /**
     * 새 영속 토큰을 발급하고 쿠키 값("selector:validator")을 반환한다.
     * token_hash(validator의 SHA-256)만 저장한다.
     */
    @Transactional
    public String issue(long memberId, String userAgent) {
        hubMemberService.ensureTables();
        if (memberId <= 0) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        String selector = randomToken(SELECTOR_BYTES);
        String validator = randomToken(VALIDATOR_BYTES);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(rememberDays);
        jdbcTemplate.update("""
                INSERT INTO csm.hub_member_token (member_id, selector, token_hash, expires_at, last_used_at, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                memberId, selector, sha256Hex(validator), Timestamp.valueOf(expiresAt),
                Timestamp.valueOf(LocalDateTime.now()), trimTo(userAgent, 255));
        return selector + ":" + validator;
    }

    /**
     * 쿠키 값을 검증하고, 유효하면 validator를 회전한 뒤 결과를 반환한다.
     * 무효(없음/만료/불일치/비활성)면 valid=false이며 호출 측은 쿠키를 삭제해야 한다.
     */
    @Transactional
    public Result validateAndRotate(String cookieValue, String userAgent) {
        hubMemberService.ensureTables();
        cleanupExpired();

        String[] parts = split(cookieValue);
        if (parts == null) {
            return Result.invalid();
        }
        String selector = parts[0];
        String validator = parts[1];

        TokenRow row = findBySelector(selector);
        if (row == null) {
            return Result.invalid();
        }
        if (row.expiresAt().isBefore(LocalDateTime.now())) {
            deleteById(row.id());
            return Result.invalid();
        }
        // 상수시간 비교: 저장된 해시와 validator의 해시를 바이트로 비교
        boolean matches = MessageDigest.isEqual(hexToBytes(row.tokenHash()), sha256Bytes(validator));
        if (!matches) {
            // validator 불일치 = 도난/위조 가능성 → 해당 selector 토큰 폐기
            deleteById(row.id());
            log.warn("[hub-remember] validator mismatch for selector, token revoked (member={})", row.memberId());
            return Result.invalid();
        }
        if (!isMemberActive(row.memberId())) {
            deleteById(row.id());
            return Result.invalid();
        }

        // 토큰 회전: 새 validator 발급 + 해시·만료·last_used 갱신
        String newValidator = randomToken(VALIDATOR_BYTES);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE csm.hub_member_token
                   SET token_hash = ?, expires_at = ?, last_used_at = ?, user_agent = ?
                 WHERE id = ?
                """,
                sha256Hex(newValidator), Timestamp.valueOf(now.plusDays(rememberDays)),
                Timestamp.valueOf(now), trimTo(userAgent, 255), row.id());
        return Result.valid(row.memberId(), selector + ":" + newValidator);
    }

    /** 로그아웃: 현재 기기 쿠키의 selector 토큰만 삭제. */
    @Transactional
    public void deleteByCookie(String cookieValue) {
        String[] parts = split(cookieValue);
        if (parts == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM csm.hub_member_token WHERE selector = ?", parts[0]);
    }

    /** 모든 기기 로그아웃: 해당 회원의 토큰 전부 삭제. */
    @Transactional
    public void deleteAllForMember(long memberId) {
        if (memberId <= 0) {
            return;
        }
        jdbcTemplate.update("DELETE FROM csm.hub_member_token WHERE member_id = ?", memberId);
    }

    /** 현재 기기(쿠키의 selector)를 제외한 다른 기기 토큰 폐기. 쿠키 없으면 전부 삭제. */
    @Transactional
    public void deleteOthersForMember(long memberId, String currentCookieValue) {
        if (memberId <= 0) {
            return;
        }
        String[] parts = split(currentCookieValue);
        if (parts == null) {
            deleteAllForMember(memberId);
            return;
        }
        jdbcTemplate.update(
                "DELETE FROM csm.hub_member_token WHERE member_id = ? AND selector <> ?",
                memberId, parts[0]);
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private void cleanupExpired() {
        try {
            jdbcTemplate.update("DELETE FROM csm.hub_member_token WHERE expires_at < ?",
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("[hub-remember] expired-token cleanup skipped: {}", e.getMessage());
        }
    }

    private TokenRow findBySelector(String selector) {
        List<TokenRow> rows = jdbcTemplate.query("""
                SELECT id, member_id, token_hash, expires_at
                  FROM csm.hub_member_token
                 WHERE selector = ?
                 LIMIT 1
                """, (rs, rowNum) -> new TokenRow(
                        rs.getLong("id"),
                        rs.getLong("member_id"),
                        rs.getString("token_hash"),
                        rs.getTimestamp("expires_at").toLocalDateTime()),
                selector);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean isMemberActive(long memberId) {
        List<String> status = jdbcTemplate.query(
                "SELECT status FROM csm.hub_member WHERE id = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("status"), memberId);
        return !status.isEmpty() && "ACTIVE".equalsIgnoreCase(status.get(0));
    }

    private void deleteById(long id) {
        jdbcTemplate.update("DELETE FROM csm.hub_member_token WHERE id = ?", id);
    }

    /** "selector:validator" 분리. 형식 오류면 null. */
    private String[] split(String cookieValue) {
        if (!StringUtils.hasText(cookieValue)) {
            return null;
        }
        int idx = cookieValue.indexOf(':');
        if (idx <= 0 || idx >= cookieValue.length() - 1) {
            return null;
        }
        return new String[] { cookieValue.substring(0, idx), cookieValue.substring(idx + 1) };
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return B64.encodeToString(buf);
    }

    private String sha256Hex(String value) {
        return toHex(sha256Bytes(value));
    }

    private byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || (hex.length() & 1) == 1) {
            return new byte[0];
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return new byte[0];
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private String trimTo(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    /** 패키지 가시성: 같은 패키지 단위 테스트가 findBySelector 결과를 stub 하기 위함. */
    record TokenRow(long id, long memberId, String tokenHash, LocalDateTime expiresAt) {
    }

    /** 검증 결과: valid면 memberId와 회전된 새 쿠키 값을 담는다. */
    public record Result(boolean valid, Long memberId, String newCookieValue) {
        static Result invalid() {
            return new Result(false, null, null);
        }

        static Result valid(long memberId, String newCookieValue) {
            return new Result(true, memberId, newCookieValue);
        }
    }
}
