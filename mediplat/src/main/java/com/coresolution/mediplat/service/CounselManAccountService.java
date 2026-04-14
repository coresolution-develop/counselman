package com.coresolution.mediplat.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CounselManAccountService {

    private final JdbcTemplate counselManJdbcTemplate;
    private final SecretKeySpec keySpec;
    private final byte[] ivBytes;

    public CounselManAccountService(
            @Qualifier("counselManJdbcTemplate") JdbcTemplate counselManJdbcTemplate,
            @Value("${platform.counselman.login.aes-key}") String aesKey) {
        if (!StringUtils.hasText(aesKey) || aesKey.length() < 16) {
            throw new IllegalArgumentException("platform.counselman.login.aes-key must be at least 16 characters.");
        }
        byte[] keyBytes = Arrays.copyOf(aesKey.getBytes(StandardCharsets.UTF_8), 16);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.ivBytes = aesKey.substring(0, 16).getBytes(StandardCharsets.UTF_8);
        this.counselManJdbcTemplate = counselManJdbcTemplate;
    }

    public AuthenticatedUser authenticate(String instCode, String username, String rawPassword) {
        String resolvedInst = resolveInst(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(resolvedInst) || !StringUtils.hasText(normalizedUsername) || !StringUtils.hasText(rawPassword)) {
            return null;
        }
        if (!isInstitutionAvailable(resolvedInst)) {
            return null;
        }
        String encryptedPassword = encrypt(rawPassword);
        if (!isPasswordMatched(resolvedInst, normalizedUsername, encryptedPassword)) {
            return null;
        }
        CounselManUserRow user = findUser(resolvedInst, normalizedUsername);
        if (!isUserAvailable(user)) {
            return null;
        }
        String displayName = StringUtils.hasText(user.name()) ? user.name() : normalizedUsername;
        return new AuthenticatedUser(resolvedInst, normalizedUsername, displayName);
    }

    public List<CounselManInstitution> listInstitutions() {
        return counselManJdbcTemplate.query("""
                SELECT id_col_03, id_col_02, COALESCE(NULLIF(id_col_04, ''), 'Y') AS use_yn
                FROM inst_data_cs
                WHERE id_col_03 IS NOT NULL
                  AND TRIM(id_col_03) <> ''
                ORDER BY CASE WHEN LOWER(id_col_03) = 'core' THEN 0 ELSE 1 END,
                         id_col_02 ASC,
                         id_col_03 ASC
                """, (rs, rowNum) -> new CounselManInstitution(
                        normalizeInstCode(rs.getString("id_col_03")),
                        normalizeInstitutionName(rs.getString("id_col_02"), rs.getString("id_col_03")),
                        normalizeYn(rs.getString("use_yn"))));
    }

    public CounselManInstitution findInstitution(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return null;
        }
        List<CounselManInstitution> institutions = counselManJdbcTemplate.query("""
                SELECT id_col_03, id_col_02, COALESCE(NULLIF(id_col_04, ''), 'Y') AS use_yn
                FROM inst_data_cs
                WHERE LOWER(id_col_03) = LOWER(?)
                LIMIT 1
                """, (rs, rowNum) -> new CounselManInstitution(
                        normalizeInstCode(rs.getString("id_col_03")),
                        normalizeInstitutionName(rs.getString("id_col_02"), rs.getString("id_col_03")),
                        normalizeYn(rs.getString("use_yn"))),
                normalizedInstCode);
        return institutions.isEmpty() ? null : institutions.get(0);
    }

    public boolean hasAvailableUser(String instCode, String username) {
        String resolvedInst = resolveInst(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(resolvedInst) || !StringUtils.hasText(normalizedUsername)) {
            return false;
        }
        if (!isInstitutionAvailable(resolvedInst)) {
            return false;
        }
        CounselManUserRow user = findUser(resolvedInst, normalizedUsername);
        return isUserAvailable(user);
    }

    public boolean isRoomBoardEnabled(String instCode) {
        String resolvedInst = resolveInst(instCode);
        if (!StringUtils.hasText(resolvedInst)) {
            return false;
        }
        try {
            List<String> rows = counselManJdbcTemplate.query("""
                    SELECT enabled_yn
                    FROM csm.module_feature_setting
                    WHERE inst_code = ?
                      AND feature_code = 'ROOM_BOARD'
                    LIMIT 1
                    """, (rs, rowNum) -> rs.getString("enabled_yn"), resolvedInst);
            if (rows.isEmpty()) {
                return true;
            }
            return !"N".equalsIgnoreCase(rows.get(0));
        } catch (Exception e) {
            // 테이블 미생성 등 초기 환경에서는 기본적으로 사용 가능 상태로 간주
            return true;
        }
    }

    public boolean isUsernameUsedInAnyInstitution(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return false;
        }
        for (CounselManInstitution institution : listInstitutions()) {
            if (institution == null || !StringUtils.hasText(institution.instCode())) {
                continue;
            }
            String safeInstCode = sanitizeInst(institution.instCode());
            try {
                Integer count = counselManJdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)
                                FROM user_data_%s
                                WHERE LOWER(us_col_02) = LOWER(?)
                                """.formatted(safeInstCode),
                        Integer.class,
                        normalizedUsername);
                if (count != null && count > 0) {
                    return true;
                }
            } catch (Exception e) {
                // 일부 기관 테이블이 비정상인 경우 해당 기관은 건너뜀
            }
        }
        return false;
    }

    private String resolveInst(String rawInstCode) {
        if (!StringUtils.hasText(rawInstCode)) {
            return null;
        }
        String candidate = rawInstCode.trim();
        if (!candidate.matches("^[A-Za-z0-9_]{2,32}$")) {
            return null;
        }
        List<String> candidates = List.of(
                candidate,
                "core".equalsIgnoreCase(candidate) ? "core" : candidate.toUpperCase(Locale.ROOT),
                candidate.toLowerCase(Locale.ROOT));
        for (String value : candidates.stream().distinct().toList()) {
            Integer exists = counselManJdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = CONCAT('user_data_', ?)
                    """, Integer.class, value);
            if (exists != null && exists > 0) {
                return value;
            }
        }
        return null;
    }

    private boolean isInstitutionAvailable(String instCode) {
        if ("core".equalsIgnoreCase(instCode)) {
            return true;
        }
        List<String> useYn = counselManJdbcTemplate.query("""
                SELECT id_col_04
                FROM inst_data_cs
                WHERE id_col_03 = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("id_col_04"), instCode);
        if (useYn.isEmpty()) {
            return true;
        }
        return !"n".equalsIgnoreCase(useYn.get(0));
    }

    private boolean isPasswordMatched(String instCode, String username, String encryptedPassword) {
        String safeInstCode = sanitizeInst(instCode);
        Integer count = counselManJdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM user_data_%s
                        WHERE us_col_04 = ?
                          AND us_col_02 = ?
                          AND us_col_03 = ?
                        """.formatted(safeInstCode),
                Integer.class,
                instCode,
                username,
                encryptedPassword);
        return count != null && count > 0;
    }

    private CounselManUserRow findUser(String instCode, String username) {
        String safeInstCode = sanitizeInst(instCode);
        List<CounselManUserRow> users = counselManJdbcTemplate.query(
                """
                        SELECT us_col_02, us_col_05, us_col_07, us_col_09, us_col_12
                        FROM user_data_%s
                        WHERE us_col_02 = ?
                        LIMIT 1
                        """.formatted(safeInstCode),
                (rs, rowNum) -> new CounselManUserRow(
                        rs.getString("us_col_02"),
                        rs.getString("us_col_05"),
                        rs.getString("us_col_07"),
                        (Integer) rs.getObject("us_col_09"),
                        rs.getString("us_col_12")),
                username);
        return users.isEmpty() ? null : users.get(0);
    }

    private boolean isUserAvailable(CounselManUserRow user) {
        if (user == null) {
            return false;
        }
        if ("n".equalsIgnoreCase(user.useYn())) {
            return false;
        }
        return user.status() == null || user.status() != 2;
    }

    private String normalizeUsername(String username) {
        return StringUtils.hasText(username) ? username.trim() : null;
    }

    private String normalizeInstCode(String instCode) {
        if (!StringUtils.hasText(instCode)) {
            return null;
        }
        String normalized = instCode.trim();
        if ("core".equalsIgnoreCase(normalized)) {
            return "core";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeInstitutionName(String institutionName, String instCode) {
        if (StringUtils.hasText(institutionName)) {
            return institutionName.trim();
        }
        return normalizeInstCode(instCode);
    }

    private String normalizeYn(String useYn) {
        return "N".equalsIgnoreCase(useYn) ? "N" : "Y";
    }

    private String sanitizeInst(String instCode) {
        if (!StringUtils.hasText(instCode) || !instCode.matches("^[A-Za-z0-9_]{2,32}$")) {
            throw new IllegalArgumentException("유효하지 않은 기관코드입니다.");
        }
        return instCode;
    }

    private String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("CounselMan password encryption failed.", e);
        }
    }

    private record CounselManUserRow(
            String username,
            String institutionName,
            String useYn,
            Integer status,
            String name) {
    }

    public record AuthenticatedUser(
            String instCode,
            String username,
            String displayName) {
    }

    public record CounselManInstitution(
            String instCode,
            String instName,
            String useYn) {
    }
}
