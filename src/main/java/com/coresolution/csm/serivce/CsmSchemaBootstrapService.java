package com.coresolution.csm.serivce;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

@Service
public class CsmSchemaBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(CsmSchemaBootstrapService.class);
    private final JdbcTemplate jdbcTemplate;
    private final CsmAuthService csmAuthService;
    private final TransactionTemplate transactionTemplate;

    public CsmSchemaBootstrapService(JdbcTemplate jdbcTemplate,
                                     CsmAuthService csmAuthService,
                                     PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.csmAuthService = csmAuthService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void bootstrapOnStartup() {
        refreshFromPlatform();
    }

    public synchronized void refreshFromPlatform() {
        try {
            ensureCoreRegistryTables();
            migrateLocalInstitutions();
            if (!tableExists("mp_institution")) {
                return;
            }

            List<Map<String, Object>> institutions = jdbcTemplate.queryForList("""
                    SELECT inst_code, inst_name, COALESCE(use_yn, 'Y') AS use_yn
                    FROM csm.mp_institution
                    ORDER BY id ASC
                    """);
            for (Map<String, Object> row : institutions) {
                String instCode = normalizeInstCode(Objects.toString(row.get("inst_code"), null));
                if (!StringUtils.hasText(instCode)) {
                    continue;
                }
                String instName = normalizeInstName(Objects.toString(row.get("inst_name"), instCode), instCode);
                String useYn = toCounselmanYn(Objects.toString(row.get("use_yn"), "Y"));

                try {
                    csmAuthService.createCoreInstSchemaTables(instCode);
                    transactionTemplate.executeWithoutResult(status -> {
                        upsertCoreInstitution(instCode, instName, useYn);
                        syncUsersFromPlatform(instCode, instName);
                    });
                } catch (Exception e) {
                    log.warn("[schema-bootstrap] inst={} skipped: {}", instCode, e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("[schema-bootstrap] refresh skipped: {}", e.toString());
        }
    }

    private void ensureCoreRegistryTables() {
        if (isMySql()) {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS csm.inst_data_cs (
                        id_col_01 INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        id_col_02 VARCHAR(50) DEFAULT NULL,
                        id_col_03 VARCHAR(50) DEFAULT NULL,
                        id_col_04 VARCHAR(5) DEFAULT 'y',
                        id_col_05 VARCHAR(255) DEFAULT NULL,
                        id_col_06 VARCHAR(255) DEFAULT NULL,
                        id_col_07 VARCHAR(255) DEFAULT NULL,
                        id_col_08 VARCHAR(255) DEFAULT NULL,
                        id_col_09 VARCHAR(255) DEFAULT NULL,
                        sms_price VARCHAR(20) DEFAULT NULL,
                        lms_price VARCHAR(20) DEFAULT NULL,
                        mms_price VARCHAR(20) DEFAULT NULL,
                        UNIQUE KEY uq_inst_data_cs_code (id_col_03)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS csm.user_data_cs (
                        id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        us_col_01 VARCHAR(100) NOT NULL,
                        us_col_02 VARCHAR(100) NOT NULL,
                        us_col_03 VARCHAR(100) DEFAULT NULL,
                        us_col_04 VARCHAR(255) DEFAULT NULL,
                        us_col_05 VARCHAR(255) DEFAULT NULL,
                        us_col_06 VARCHAR(255) DEFAULT NULL,
                        us_col_07 VARCHAR(100) DEFAULT NULL,
                        UNIQUE KEY uq_user_data_cs_user_inst (us_col_01, us_col_02)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            return;
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.inst_data_cs (
                    id_col_01 INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    id_col_02 VARCHAR(50),
                    id_col_03 VARCHAR(50),
                    id_col_04 VARCHAR(5) DEFAULT 'y',
                    id_col_05 VARCHAR(255),
                    id_col_06 VARCHAR(255),
                    id_col_07 VARCHAR(255),
                    id_col_08 VARCHAR(255),
                    id_col_09 VARCHAR(255),
                    sms_price VARCHAR(20),
                    lms_price VARCHAR(20),
                    mms_price VARCHAR(20),
                    UNIQUE (id_col_03)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.user_data_cs (
                    id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    us_col_01 VARCHAR(100) NOT NULL,
                    us_col_02 VARCHAR(100) NOT NULL,
                    us_col_03 VARCHAR(100),
                    us_col_04 VARCHAR(255),
                    us_col_05 VARCHAR(255),
                    us_col_06 VARCHAR(255),
                    us_col_07 VARCHAR(100),
                    UNIQUE (us_col_01, us_col_02)
                )
                """);
    }

    private void migrateLocalInstitutions() {
        try {
            if (!tableExists("inst_data_cs")) {
                return;
            }
            List<String> instCodes = jdbcTemplate.queryForList(
                    "SELECT id_col_03 FROM csm.inst_data_cs WHERE id_col_03 IS NOT NULL AND id_col_03 != ''",
                    String.class);
            for (String instCode : instCodes) {
                if (instCode == null || instCode.isBlank()) continue;
                try {
                    csmAuthService.createCoreInstSchemaTables(instCode.trim());
                } catch (Exception e) {
                    log.warn("[schema-migrate-local] inst={} skipped: {}", instCode, e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("[schema-migrate-local] skipped: {}", e.toString());
        }
    }

    private void upsertCoreInstitution(String instCode, String instName, String useYn) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM csm.inst_data_cs
                WHERE LOWER(id_col_03) = LOWER(?)
                """, Integer.class, instCode);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE csm.inst_data_cs
                    SET id_col_02 = ?,
                        id_col_04 = ?,
                        id_col_05 = COALESCE(id_col_05, '')
                    WHERE LOWER(id_col_03) = LOWER(?)
                    """, instName, useYn, instCode);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO csm.inst_data_cs
                (id_col_02, id_col_03, id_col_04, id_col_05, id_col_06, id_col_07, id_col_08, id_col_09)
                VALUES (?, ?, ?, ?, '', '', '', '')
                """, instName, instCode, useYn, "synced from mediplat");
    }

    private void syncUsersFromPlatform(String instCode, String instName) {
        if (!tableExists("mp_user")) {
            return;
        }

        String safeInst = sanitizeInst(instCode);
        String userTableName = "csm.user_data_" + safeInst;

        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
                SELECT username, display_name, role_code, COALESCE(use_yn, 'Y') AS use_yn
                FROM csm.mp_user
                WHERE LOWER(inst_code) = LOWER(?)
                ORDER BY id ASC
                """, instCode);

        for (Map<String, Object> userRow : users) {
            String username = normalizeUsername(Objects.toString(userRow.get("username"), null));
            if (!StringUtils.hasText(username)) {
                continue;
            }
            String displayName = normalizeDisplayName(Objects.toString(userRow.get("display_name"), username), username);
            String roleCode = normalizeRole(Objects.toString(userRow.get("role_code"), "USER"));
            String useYn = toCounselmanYn(Objects.toString(userRow.get("use_yn"), "Y"));
            int authority = "USER".equals(roleCode) ? 1 : 0;
            int status = "n".equalsIgnoreCase(useYn) ? 2 : 1;

            String updateSql = "UPDATE " + userTableName + " SET "
                    + "us_col_04 = ?, us_col_05 = ?, us_col_07 = ?, us_col_08 = ?, us_col_09 = ?, "
                    + "us_col_12 = COALESCE(NULLIF(us_col_12, ''), ?), "
                    + "us_col_13 = COALESCE(NULLIF(us_col_13, ''), ?) "
                    + "WHERE LOWER(us_col_02) = LOWER(?)";
            int updated = jdbcTemplate.update(
                    updateSql,
                    instCode,
                    instName,
                    useYn,
                    authority,
                    status,
                    displayName,
                    roleCode,
                    username);
            if (updated > 0) {
                continue;
            }

            String insertSql = "INSERT INTO " + userTableName + " "
                    + "(us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, us_col_07, us_col_08, us_col_09, us_col_12, us_col_13) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(
                    insertSql,
                    username,
                    "",
                    instCode,
                    instName,
                    "synced from mediplat",
                    useYn,
                    authority,
                    status,
                    displayName,
                    roleCode);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE LOWER(table_name) = LOWER(?)
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean isMySql() {
        try {
            Boolean mysql = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> isMySqlConnection(connection));
            return Boolean.TRUE.equals(mysql);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isMySqlConnection(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String product = metaData == null ? null : metaData.getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("mysql");
        } catch (Exception e) {
            return true;
        }
    }

    private String sanitizeInst(String instCode) {
        String normalized = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalized) || !normalized.matches("^[A-Za-z0-9_]{2,20}$")) {
            throw new IllegalArgumentException("invalid inst code: " + instCode);
        }
        return normalized;
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

    private String normalizeInstName(String instName, String fallbackInstCode) {
        if (StringUtils.hasText(instName)) {
            return instName.trim();
        }
        return fallbackInstCode;
    }

    private String normalizeUsername(String username) {
        return StringUtils.hasText(username) ? username.trim() : null;
    }

    private String normalizeDisplayName(String displayName, String username) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        return username;
    }

    private String normalizeRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return "USER";
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        if ("PLATFORM_ADMIN".equals(normalized) || "INSTITUTION_ADMIN".equals(normalized)) {
            return normalized;
        }
        return "USER";
    }

    private String toCounselmanYn(String platformYn) {
        return "N".equalsIgnoreCase(platformYn) ? "n" : "y";
    }
}
