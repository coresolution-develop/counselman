package com.coresolution.csm.serivce;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
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
    private final ChatTokenService chatTokenService;

    public CsmSchemaBootstrapService(JdbcTemplate jdbcTemplate,
                                     CsmAuthService csmAuthService,
                                     PlatformTransactionManager transactionManager,
                                     MediplatRoleMapper mediplatRoleMapper,
                                     ChatTokenService chatTokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.csmAuthService = csmAuthService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.mediplatRoleMapper = mediplatRoleMapper;
        this.chatTokenService = chatTokenService;
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
            List<String> historyReadyInsts = new ArrayList<>();
            for (Map<String, Object> row : institutions) {
                String instCode = normalizeInstCode(Objects.toString(row.get("inst_code"), null));
                if (!StringUtils.hasText(instCode)) {
                    continue;
                }
                String instName = normalizeInstName(Objects.toString(row.get("inst_name"), instCode), instCode);
                String useYn = toCounselmanYn(Objects.toString(row.get("use_yn"), "Y"));

                try {
                    csmAuthService.createCoreInstSchemaTables(instCode);
                    ensureTransmissionHistoryColumns(instCode);
                    historyReadyInsts.add(instCode.replaceAll("[^a-zA-Z0-9_]", "_"));
                    ensureRoleIconColumn(instCode);
                    transactionTemplate.executeWithoutResult(status -> {
                        upsertCoreInstitution(instCode, instName, useYn);
                        syncUsersFromPlatform(instCode, instName);
                    });
                    chatTokenService.getOrCreateToken(instCode);
                } catch (Exception e) {
                    log.warn("[schema-bootstrap] inst={} skipped: {}", instCode, e.toString());
                }
            }
            try {
                // 컬럼·collation 보강이 끝난 기관만 포함한다. 실패가 기동을 막으면 안 된다.
                csmAuthService.recreateTransmissionHistoryAllView(historyReadyInsts);
            } catch (Exception e) {
                log.warn("[schema-bootstrap] v_transmission_history_all recreate failed: {}", e.toString());
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
            ensureChatInstTokenTable();
            ensureSmsBatchTable();
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

    /**
     * 문자 배치 발송 단위 테이블. 기관별 분할 대상이 아니다 — 단일 테이블 + inst_code 컬럼.
     *
     * <p>Phase 4 선불 지갑 도입 시 이 테이블이 지갑 원장의 참조 대상이 된다
     * (sms_wallet_tx.ref_type='SMS_BATCH', ref_id=batch_id). 차감 금액은 total_cost(전 단위)를 쓴다.
     * idem_key 는 기관 단위로만 유일하면 되므로 (inst_code, idem_key) 복합 UNIQUE 다.
     */
    private void ensureSmsBatchTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.sms_batch (
                    batch_id       VARCHAR(64)  NOT NULL PRIMARY KEY,
                    inst_code      VARCHAR(50)  NOT NULL,
                    idem_key       VARCHAR(64)  NOT NULL,
                    from_phone     VARCHAR(20)  NOT NULL,
                    send_type      VARCHAR(10)  NOT NULL,
                    total_count    INT          NOT NULL DEFAULT 0,
                    success_count  INT          NOT NULL DEFAULT 0,
                    failed_count   INT          NOT NULL DEFAULT 0,
                    unknown_count  INT          NOT NULL DEFAULT 0,
                    unit_cost      INT          NOT NULL,
                    total_cost     BIGINT       NOT NULL DEFAULT 0,
                    billable       CHAR(1)      NOT NULL DEFAULT 'Y',
                    created_by     VARCHAR(100),
                    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_batch_idem (inst_code, idem_key),
                    KEY ix_batch_inst (inst_code, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        ensureSmsBatchColumnTypes();
    }

    /**
     * 이미 만들어진 sms_batch 의 total_cost 를 INT → BIGINT 로 넓힌다.
     *
     * <p>CREATE TABLE IF NOT EXISTS 는 기존 테이블의 컬럼 타입을 바꾸지 않으므로
     * 운영 DB 에는 이 보정이 따로 필요하다. 다른 스키마 보정과 같은 방식으로
     * INFORMATION_SCHEMA 로 먼저 확인하고, 필요한 경우에만 ALTER 한다.
     *
     * <p>MySQL 의 INT → BIGINT 확대는 값 손실이 없고 온라인(INPLACE) 으로 처리되므로
     * 무중단이다. 값을 좁히는 방향이 아니라 넓히는 방향이라 롤백도 안전하다.
     */
    private void ensureSmsBatchColumnTypes() {
        try {
            String dataType = jdbcTemplate.queryForObject("""
                    SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = 'sms_batch' AND COLUMN_NAME = 'total_cost'
                    """, String.class);
            if (dataType != null && "int".equalsIgnoreCase(dataType)) {
                jdbcTemplate.execute(
                        "ALTER TABLE csm.sms_batch MODIFY COLUMN total_cost BIGINT NOT NULL DEFAULT 0");
                log.info("[schema-bootstrap] sms_batch.total_cost widened INT -> BIGINT");
            }
        } catch (Exception e) {
            // 다른 스키마 보정과 같은 원칙 — 실패가 기동을 막지 않는다.
            log.warn("[schema-bootstrap] sms_batch.total_cost widening skipped: {}", e.toString());
        }
    }

    private void ensureChatInstTokenTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.chat_inst_token (
                    token      VARCHAR(32)  NOT NULL PRIMARY KEY,
                    inst       VARCHAR(50)  NOT NULL,
                    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_chat_inst_token_inst (inst)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
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
            {"faq_manage",          "FAQ 관리",     "/faq-manage",          110},
            {"chat_admin",          "채팅 관리",    "/chat-admin",          120},
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
            // faq_manage
            {"FAQ:READ",   "faq_manage", "FAQ", "READ",   "FAQ 조회", 131},
            {"FAQ:CREATE", "faq_manage", "FAQ", "CREATE", "FAQ 등록", 132},
            {"FAQ:EDIT",   "faq_manage", "FAQ", "EDIT",   "FAQ 수정", 133},
            {"FAQ:DELETE", "faq_manage", "FAQ", "DELETE", "FAQ 삭제", 134},
            // chat_admin
            {"CHAT:READ",  "chat_admin", "CHAT", "READ",  "채팅 조회",  141},
            {"CHAT:ADMIN", "chat_admin", "CHAT", "ADMIN", "채팅 관리자", 142},
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
                mergeAuthorityWithRoles(safeInst, userTableName, username, authority);
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

    /**
     * 플랫폼 동기화 후 CSM 역할 배정을 반영해 us_col_08을 보정한다.
     * 플랫폼 권한(authority)과 CSM 시스템 역할 권한 중 더 높은 값(숫자가 낮을수록 높은 권한)을 사용한다.
     *
     * 예) 플랫폼=USER(2), CSM=시스템역할 보유(1) → LEAST(2,1)=1 → INST_ADMIN 유지
     *     플랫폼=USER(2), CSM=역할 없음(2)      → LEAST(2,2)=2 → ROLE_USER
     *     플랫폼=INST_ADMIN(1), CSM=역할 없음  → LEAST(1,2)=1 → INST_ADMIN
     */
    private void mergeAuthorityWithRoles(String safeInst, String userTableName, String username, int platformAuthority) {
        try {
            List<Long> userIds = jdbcTemplate.queryForList(
                    "SELECT us_col_01 FROM " + userTableName + " WHERE LOWER(us_col_02) = LOWER(?)",
                    Long.class, username);
            if (userIds.isEmpty()) return;
            long userId = userIds.get(0);

            Integer systemRoleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM csm.user_role_" + safeInst + " ur"
                    + " JOIN csm.role_" + safeInst + " r ON r.role_id = ur.role_id"
                    + " WHERE ur.user_id = ? AND r.is_system = 1",
                    Integer.class, userId);

            int roleAuthority = (systemRoleCount != null && systemRoleCount > 0) ? 1 : 2;
            int finalAuthority = Math.min(platformAuthority, roleAuthority);
            if (finalAuthority != platformAuthority) {
                jdbcTemplate.update("UPDATE " + userTableName + " SET us_col_08 = ? WHERE us_col_01 = ?",
                        finalAuthority, userId);
                log.info("[sync] {} authority adjusted {} → {} (has system role)", username, platformAuthority, finalAuthority);
            }
        } catch (Exception e) {
            log.warn("[sync] mergeAuthorityWithRoles failed for {}: {}", username, e.getMessage());
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

    private void ensureRoleIconColumn(String instCode) {
        String safe = instCode.replaceAll("[^a-zA-Z0-9_]", "_");
        String tableName = "role_" + safe;
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ? AND COLUMN_NAME = 'icon_name'",
                Integer.class, tableName);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE csm." + tableName + " ADD COLUMN icon_name VARCHAR(30) DEFAULT NULL");
                log.info("[schema-bootstrap] added column icon_name to {}", tableName);
            }
        } catch (Exception e) {
            log.warn("[schema-bootstrap] icon_name column migration skipped {}: {}", tableName, e.toString());
        }
    }

    /**
     * 기관별 이력 테이블의 collation 을 utf8mb4_0900_ai_ci 로 통일한다.
     * 생성 시기에 따라 unicode_ci / 0900_ai_ci 가 갈려 있어, UNION ALL 집계 뷰가
     * "Illegal mix of collations" 로 실패하는 것을 막는다. 실패해도 기동은 계속한다.
     */
    private void ensureTransmissionHistoryCollation(String tableName) {
        try {
            String collation = jdbcTemplate.queryForObject(
                "SELECT COALESCE(table_collation, '') FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ?",
                String.class, tableName);
            if (collation != null && !collation.isBlank()
                    && !"utf8mb4_0900_ai_ci".equalsIgnoreCase(collation)) {
                jdbcTemplate.execute("ALTER TABLE csm." + tableName
                        + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                log.info("[schema-bootstrap] converted collation {} -> utf8mb4_0900_ai_ci on {}", collation, tableName);
            }
        } catch (Exception e) {
            log.warn("[schema-bootstrap] collation migration skipped {}: {}", tableName, e.toString());
        }
    }

    private void ensureTransmissionHistoryColumns(String instCode) {
        String safe = instCode.replaceAll("[^a-zA-Z0-9_]", "_");
        String tableName = "transmission_history_" + safe;
        ensureTransmissionHistoryCollation(tableName);
        String[] columns = {
            "reserve_time datetime DEFAULT NULL",
            "send_type varchar(10) DEFAULT NULL",
            "refkey varchar(50) DEFAULT NULL",
            "cost int DEFAULT NULL",                  // 발송 시점 단가 스냅샷, 전(錢) 단위 (9.6원 = 960)
            "billable char(1) NOT NULL DEFAULT 'Y'",  // 과금 대상 여부. OTP는 'N'
            "message_key varchar(64) DEFAULT NULL",   // 비즈뿌리오 접수 응답 messagekey
            "vendor_code varchar(10) DEFAULT NULL",   // 비즈뿌리오 접수 응답 code
            "batch_id varchar(64) DEFAULT NULL"       // csm.sms_batch 참조. 단건 발송도 배치 1건
        };
        String[] columnNames = { "reserve_time", "send_type", "refkey",
                "cost", "billable", "message_key", "vendor_code", "batch_id" };
        for (int i = 0; i < columnNames.length; i++) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, tableName, columnNames[i]);
                if (cnt == null || cnt == 0) {
                    jdbcTemplate.execute("ALTER TABLE csm." + tableName + " ADD COLUMN " + columns[i]);
                    log.info("[schema-bootstrap] added column {} to {}", columnNames[i], tableName);
                }
            } catch (Exception e) {
                log.warn("[schema-bootstrap] column migration skipped {}.{}: {}", tableName, columnNames[i], e.toString());
            }
        }
        // refkey UNIQUE: 중복 발송 구조적 차단 (2026-08-12 전 기관 중복 0건 검증 완료).
        // 만에 하나 중복이 생겨 ALTER 가 실패하면 WARN 만 남기고 기동은 계속한다.
        ensureIndex(tableName, "uk_th_refkey", "refkey", true);
        ensureIndex(tableName, "ix_th_batch_id", "batch_id", false);
    }

    private void ensureIndex(String tableName, String indexName, String columnExpr, boolean unique) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, tableName, indexName);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE csm." + tableName
                        + " ADD " + (unique ? "UNIQUE INDEX " : "INDEX ") + indexName + " (" + columnExpr + ")");
                log.info("[schema-bootstrap] added index {} to {}", indexName, tableName);
            }
        } catch (Exception e) {
            log.warn("[schema-bootstrap] index migration skipped {}.{}: {}", tableName, indexName, e.toString());
        }
    }
}
