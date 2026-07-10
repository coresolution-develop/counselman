package com.coresolution.mediplat.service;

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

/**
 * 운전자 기기 기억(selector:validator) 토큰 서비스.
 *
 * <p>{@code links} 모듈의 검증된 {@code HubRememberService} 패턴을 mediplat으로 이식한 것이다
 * (Lombok 제거·명시 생성자·테이블명 비수식). 쿠키 값은 {@code selector:validator}이고, selector는
 * 행 조회용 공개 식별자, validator는 시크릿이다. DB에는 validator의 SHA-256 해시({@code token_hash})만
 * 저장하고 평문은 절대 저장하지 않으며, 검증은 {@link MessageDigest#isEqual} 상수시간 비교로 한다.
 *
 * <p>검증 성공 시 validator를 회전(rotate)해 도난 토큰 재사용을 막고 슬라이딩 만료를 적용한다.
 * 병렬 회전 함정은 인터셉터가 세션 복원 후 short-circuit 하므로 실질적으로 발생하지 않는다.
 * 테이블({@code mp_fleet_device_token})은 {@link FleetService} 부트스트랩에서 이미 생성된다.
 */
@Service
public class FleetDeviceTokenService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FleetDeviceTokenService.class);

    private static final int SELECTOR_BYTES = 12;   // base64url ~16자 (VARCHAR(24) 이내)
    private static final int VALIDATOR_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final JdbcTemplate jdbcTemplate;

    @Value("${fleet.device.remember-days:90}")
    private int rememberDays;

    /** 운영(HTTPS)에서는 true. 로컬 http 테스트 시에만 false로 내릴 수 있다. */
    @Value("${fleet.device.cookie-secure:true}")
    private boolean cookieSecure;

    public FleetDeviceTokenService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public long getCookieMaxAgeSeconds() {
        return (long) rememberDays * 24 * 60 * 60;
    }

    /** 새 기기 토큰을 발급하고 쿠키 값("selector:validator")을 반환한다. token_hash만 저장한다. */
    @Transactional
    public String issue(long driverId, String userAgent) {
        if (driverId <= 0) {
            throw new IllegalArgumentException("운전자 정보를 확인해 주세요.");
        }
        String selector = randomToken(SELECTOR_BYTES);
        String validator = randomToken(VALIDATOR_BYTES);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO mp_fleet_device_token (driver_id, selector, token_hash, expires_at, last_used_at, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                driverId, selector, sha256Hex(validator),
                Timestamp.valueOf(now.plusDays(rememberDays)),
                Timestamp.valueOf(now), trimTo(userAgent, 255));
        return selector + ":" + validator;
    }

    /**
     * 쿠키 값을 검증하고, 유효하면 validator를 회전한 뒤 결과를 반환한다.
     * 무효(없음/만료/불일치/비활성)면 valid=false이며 호출 측은 쿠키를 삭제해야 한다.
     */
    @Transactional
    public Result validateAndRotate(String cookieValue, String userAgent) {
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
        boolean matches = MessageDigest.isEqual(hexToBytes(row.tokenHash()), sha256Bytes(validator));
        if (!matches) {
            // validator 불일치 = 도난/위조 가능성 → 해당 selector 토큰 폐기
            deleteById(row.id());
            log.warn("[fleet-device] validator mismatch for selector, token revoked (driver={})", row.driverId());
            return Result.invalid();
        }
        if (!isDriverActive(row.driverId())) {
            deleteById(row.id());
            return Result.invalid();
        }

        String newValidator = randomToken(VALIDATOR_BYTES);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE mp_fleet_device_token
                   SET token_hash = ?, expires_at = ?, last_used_at = ?, user_agent = ?
                 WHERE id = ?
                """,
                sha256Hex(newValidator), Timestamp.valueOf(now.plusDays(rememberDays)),
                Timestamp.valueOf(now), trimTo(userAgent, 255), row.id());
        return Result.valid(row.driverId(), selector + ":" + newValidator);
    }

    /** 로그아웃/기기 해제: 현재 기기 쿠키의 selector 토큰만 삭제. */
    @Transactional
    public void deleteByCookie(String cookieValue) {
        String[] parts = split(cookieValue);
        if (parts == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM mp_fleet_device_token WHERE selector = ?", parts[0]);
    }

    /** 도난·분실 대비: 해당 운전자의 기기 토큰 전부 폐기(관리자 기기 해제). */
    @Transactional
    public void deleteAllForDriver(long driverId) {
        if (driverId <= 0) {
            return;
        }
        jdbcTemplate.update("DELETE FROM mp_fleet_device_token WHERE driver_id = ?", driverId);
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private void cleanupExpired() {
        try {
            jdbcTemplate.update("DELETE FROM mp_fleet_device_token WHERE expires_at < ?",
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("[fleet-device] expired-token cleanup skipped: {}", e.getMessage());
        }
    }

    private TokenRow findBySelector(String selector) {
        List<TokenRow> rows = jdbcTemplate.query("""
                SELECT id, driver_id, token_hash, expires_at
                  FROM mp_fleet_device_token
                 WHERE selector = ?
                 LIMIT 1
                """, (rs, rowNum) -> new TokenRow(
                        rs.getLong("id"),
                        rs.getLong("driver_id"),
                        rs.getString("token_hash"),
                        rs.getTimestamp("expires_at").toLocalDateTime()),
                selector);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean isDriverActive(long driverId) {
        List<String> useYn = jdbcTemplate.query(
                "SELECT use_yn FROM mp_fleet_driver WHERE id = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("use_yn"), driverId);
        return !useYn.isEmpty() && "Y".equalsIgnoreCase(useYn.get(0));
    }

    private void deleteById(long id) {
        jdbcTemplate.update("DELETE FROM mp_fleet_device_token WHERE id = ?", id);
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

    /** 패키지 가시성: 같은 패키지 단위 테스트 지원. */
    record TokenRow(long id, long driverId, String tokenHash, LocalDateTime expiresAt) {
    }

    /** 검증 결과: valid면 driverId와 회전된 새 쿠키 값을 담는다. */
    public record Result(boolean valid, Long driverId, String newCookieValue) {
        static Result invalid() {
            return new Result(false, null, null);
        }

        static Result valid(long driverId, String newCookieValue) {
            return new Result(true, driverId, newCookieValue);
        }
    }
}
