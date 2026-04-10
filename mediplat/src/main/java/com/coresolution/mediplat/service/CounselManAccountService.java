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
}
