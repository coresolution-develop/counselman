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

import com.coresolution.csm.handler.MediplatRoleMapper;
import jakarta.annotation.PostConstruct;

@Service
public class CsmSchemaBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(CsmSchemaBootstrapService.class);
    private final JdbcTemplate jdbcTemplate;
    private final CsmAuthService csmAuthService;
    private final TransactionTemplate transactionTemplate;
    private final MediplatRoleMapper mediplatRoleMapper;

    public CsmSchemaBootstrapService(JdbcTemplate jdbcTemplate,
                                     CsmAuthService csmAuthService,
                                     PlatformTransactionManager transactionManager,
                                     MediplatRoleMapper mediplatRoleMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.csmAuthService = csmAuthService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.mediplatRoleMapper = mediplatRoleMapper;
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
            ensurePermissionMasterTables();
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

    private void ensurePermissionMasterTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.menu_master (
                    menu_key  varchar(32) PRIMARY KEY,
                    label_ko  varchar(100),
                    path      varchar(200),
                    sort_order int DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.permission_master (
                    code       varchar(64) PRIMARY KEY,
                    menu_key   varchar(32) NOT NULL,
                    resource   varchar(32) NOT NULL,
                    action     varchar(32) NOT NULL,
                    label_ko   varchar(100),
                    sort_order int DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.user_nav_order (
                    inst       varchar(20)  NOT NULL,
                    username   varchar(100) NOT NULL,
                    nav_key    varchar(64)  NOT NULL,
                    sort_order int          NOT NULL DEFAULT 0,
                    PRIMARY KEY (inst, username, nav_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        seedMenuMaster();
        seedPermissionMaster();
    }

    private void seedMenuMaster() {
        String upsert = """
                INSERT INTO csm.menu_master (menu_key, label_ko, path, sort_order)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE label_ko = VALUES(label_ko), path = VALUES(path), sort_order = VALUES(sort_order)
                """;
        Object[][] menus = {
            {"counsel_reservation", "상담 접수",   "/counsel/reservation", 10},
            {"counsel_write",       "입원상담",     "/counsel/new",         20},
            {"counsel_list",        "상담리스트",   "/counsel/list",        30},
            {"notice",              "공지사항",     "/notice",              40},
            {"stats",               "상담통계",     "/stats",               50},
            {"counsel_log",         "상담일지관리", "/counsel/log-settings", 60},
            {"sms",                 "문자관리",     "/sms",                 70},
            {"room_board",          "병실현황판",   "/room-board",          80},
            {"admission",           "입원예약관리", "/admission-reservation", 90},
            {"admin",               "관리자",       "/admin",               100},
        };
        for (Object[] row : menus) {
            jdbcTemplate.update(upsert, row);
        }
    }

    private void seedPermissionMaster() {
        String upsert = """
                INSERT INTO csm.permission_master (code, menu_key, resource, action, label_ko, sort_order)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE label_ko = VALUES(label_ko), sort_order = VALUES(sort_order)
                """;
        Object[][] perms = {
            // counsel_reservation
            {"COUNSEL_RESERVATION:READ",   "counsel_reservation", "COUNSEL_RESERVATION", "READ",   "상담접수 조회",   11},
            {"COUNSEL_RESERVATION:CREATE", "counsel_reservation", "COUNSEL_RESERVATION", "CREATE", "상담접수 등록",   12},
            {"COUNSEL_RESERVATION:EDIT",   "counsel_reservation", "COUNSEL_RESERVATION", "EDIT",   "상담접수 수정",   13},
            {"COUNSEL_RESERVATION:CANCEL", "counsel_reservation", "COUNSEL_RESERVATION", "CANCEL", "상담접수 취소",   14},
            {"COUNSEL_RESERVATION:DELETE", "counsel_reservation", "COUNSEL_RESERVATION", "DELETE", "상담접수 삭제",   15},
            // counsel_write
            {"COUNSEL:READ",   "counsel_write", "COUNSEL", "READ",   "입원상담 조회",   21},
            {"COUNSEL:CREATE", "counsel_write", "COUNSEL", "CREATE", "입원상담 등록",   22},
            {"COUNSEL:EDIT",   "counsel_write", "COUNSEL", "EDIT",   "입원상담 수정",   23},
            {"COUNSEL:DELETE", "counsel_write", "COUNSEL", "DELETE", "입원상담 삭제",   24},
            {"COUNSEL:EXPORT", "counsel_write", "COUNSEL", "EXPORT", "입원상담 내보내기", 25},
            // counsel_list
            {"COUNSEL_LIST:READ",     "counsel_list", "COUNSEL_LIST", "READ",     "상담리스트 조회",   31},
            {"COUNSEL_LIST:EXPORT",   "counsel_list", "COUNSEL_LIST", "EXPORT",   "상담리스트 내보내기", 32},
            {"COUNSEL_LIST:BULK_SMS", "counsel_list", "COUNSEL_LIST", "BULK_SMS", "상담리스트 일괄문자", 33},
            // notice
            {"NOTICE:READ",   "notice", "NOTICE", "READ",   "공지사항 조회", 41},
            {"NOTICE:CREATE", "notice", "NOTICE", "CREATE", "공지사항 등록", 42},
            {"NOTICE:EDIT",   "notice", "NOTICE", "EDIT",   "공지사항 수정", 43},
            {"NOTICE:DELETE", "notice", "NOTICE", "DELETE", "공지사항 삭제", 44},
            // stats
            {"STATS:READ",   "stats", "STATS", "READ",   "상담통계 조회",     51},
            {"STATS:EXPORT", "stats", "STATS", "EXPORT", "상담통계 내보내기", 52},
            // counsel_log
            {"COUNSEL_LOG:READ",   "counsel_log", "COUNSEL_LOG", "READ",   "상담일지 조회", 61},
            {"COUNSEL_LOG:EDIT",   "counsel_log", "COUNSEL_LOG", "EDIT",   "상담일지 수정", 62},
            {"COUNSEL_LOG:DELETE", "counsel_log", "COUNSEL_LOG", "DELETE", "상담일지 삭제", 63},
            // sms
            {"SMS:SEND",          "sms", "SMS", "SEND",          "문자 발송",      71},
            {"SMS:BULK_SEND",     "sms", "SMS", "BULK_SEND",     "문자 일괄발송",  72},
            {"SMS:HISTORY_READ",  "sms", "SMS", "HISTORY_READ",  "문자 발송이력",  73},
            {"SMS:TEMPLATE_EDIT", "sms", "SMS", "TEMPLATE_EDIT", "문자 템플릿 편집", 74},
            // room_board
            {"ROOM_BOARD:READ",            "room_board", "ROOM_BOARD", "READ",            "병실현황판 조회",     81},
            {"ROOM_BOARD:WRITE",           "room_board", "ROOM_BOARD", "WRITE",           "병실현황판 편집",     82},
            {"ROOM_BOARD:SNAPSHOT_MANAGE", "room_board", "ROOM_BOARD", "SNAPSHOT_MANAGE", "병실현황 스냅샷 관리", 83},
            // admission
            {"ADMISSION:READ",           "admission", "ADMISSION", "READ",           "입원예약 조회",   91},
            {"ADMISSION:UPDATE_DETAILS", "admission", "ADMISSION", "UPDATE_DETAILS", "입원예약 상세수정", 92},
            {"ADMISSION:CONFIRM",        "admission", "ADMISSION", "CONFIRM",        "입원 확정",       93},
            {"ADMISSION:CANCEL",         "admission", "ADMISSION", "CANCEL",         "입원예약 취소",   94},
            // admin — user
            {"USER:READ",     "admin", "USER", "READ",     "사용자 조회",     101},
            {"USER:CREATE",   "admin", "USER", "CREATE",   "사용자 등록",     102},
            {"USER:EDIT",     "admin", "USER", "EDIT",     "사용자 수정",     103},
            {"USER:DELETE",   "admin", "USER", "DELETE",   "사용자 삭제",     104},
            {"USER:RESET_PW", "admin", "USER", "RESET_PW", "사용자 비밀번호 초기화", 105},
            // admin — role
            {"ROLE:READ",   "admin", "ROLE", "READ",   "역할 조회",   111},
            {"ROLE:CREATE", "admin", "ROLE", "CREATE", "역할 생성",   112},
            {"ROLE:EDIT",   "admin", "ROLE", "EDIT",   "역할 편집",   113},
            {"ROLE:DELETE", "admin", "ROLE", "DELETE", "역할 삭제",   114},
            {"ROLE:ASSIGN", "admin", "ROLE", "ASSIGN", "역할 할당",   115},
            // admin — settings / category
            {"SETTINGS:READ", "admin", "SETTINGS", "READ", "설정 조회", 121},
            {"SETTINGS:EDIT", "admin", "SETTINGS", "EDIT", "설정 편집", 122},
            {"CATEGORY:EDIT", "admin", "CATEGORY", "EDIT", "카테고리 편집", 123},
        };
        for (Object[] row : perms) {
            jdbcTemplate.update(upsert, row);
        }
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
            String rawRoleCode = Objects.toString(userRow.get("role_code"), "USER");
            String roleCode = normalizeRole(rawRoleCode);
            String useYn = toCounselmanYn(Objects.toString(userRow.get("use_yn"), "Y"));
            MediplatRoleMapper.UserAuthInit authInit = mediplatRoleMapper.map(rawRoleCode);
            int authority = authInit.authority();
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
                if (authInit.autoRoleCode() != null) {
                    assignAutoRole(safeInst, userTableName, username, authInit.autoRoleCode());
                }
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

            if (authInit.autoRoleCode() != null) {
                assignAutoRole(safeInst, userTableName, username, authInit.autoRoleCode());
            }
        }
    }

    private void assignAutoRole(String safeInst, String userTableName, String username, String roleCode) {
        try {
            List<Long> userIds = jdbcTemplate.queryForList(
                    "SELECT us_col_01 FROM " + userTableName + " WHERE LOWER(us_col_02) = LOWER(?)",
                    Long.class, username);
            if (userIds.isEmpty()) return;
            long userId = userIds.get(0);

            List<Long> roleIds = jdbcTemplate.queryForList(
                    "SELECT role_id FROM csm.role_" + safeInst + " WHERE role_code = ?",
                    Long.class, roleCode);
            if (roleIds.isEmpty()) return;
            long roleId = roleIds.get(0);

            jdbcTemplate.update(
                    "INSERT IGNORE INTO csm.user_role_" + safeInst
                    + " (user_id, role_id, assigned_by) VALUES (?, ?, 'system')",
                    userId, roleId);
        } catch (Exception e) {
            log.warn("[assign-role] inst={} user={} role={} skipped: {}", safeInst, username, roleCode, e.toString());
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
