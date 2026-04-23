package com.coresolution.csm.serivce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.mapper.CsmMapper;
import com.coresolution.csm.vo.AjaxResponse;
import com.coresolution.csm.vo.Card;
import com.coresolution.csm.vo.Category1;
import com.coresolution.csm.vo.Category1WithSubcategoriesAndOptions;
import com.coresolution.csm.vo.Category2;
import com.coresolution.csm.vo.Category2WithOptions;
import com.coresolution.csm.vo.Category3;
import com.coresolution.csm.vo.CounselData;
import com.coresolution.csm.vo.CounselDataEntry;
import com.coresolution.csm.vo.CounselLog;
import com.coresolution.csm.vo.CounselLogGuardian;
import com.coresolution.csm.vo.CounselReservation;
import com.coresolution.csm.vo.Counsel_phone;
import com.coresolution.csm.vo.Criteria;
import com.coresolution.csm.vo.Guardian;
import com.coresolution.csm.vo.Instdata;
import com.coresolution.csm.vo.Userdata;
import com.coresolution.csm.vo.UserdataCs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CsmAuthService {
    private static final Logger log = LoggerFactory.getLogger(CsmAuthService.class);
    private static final String PLATFORM_INTEGRATION_ROOMBOARD_CSM_LINK = "ROOMBOARD_CSM_LINK";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CsmMapper cs;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // public AjaxResponse loginSelect(Userdata ud) {
    // return cs.loginSelect(ud); // 여기서 예외 던지지 않기
    // }
    public Userdata loadUserInfo(String inst, String username) {
        return cs.infoByUsername(inst, username);
    }

    public Userdata Info(Userdata ud) {
        return cs.Info(ud);
    }

    // public Integer loginCount(Userdata ud) {
    // Integer cnt = cs.loginCount(ud);
    // return cnt == null ? 0 : cnt;
    // }

    public String resolveInst(String raw) {
        if (raw == null)
            return null;
        String candidate = raw.trim();
        if (candidate.isBlank()) {
            return null;
        }

        // 1) 기관코드로 직접 확인
        String resolved = resolveInstByCodeCandidate(candidate);
        if (resolved != null) {
            return resolved;
        }

        // 2) 기관명(id_col_02)으로 기관코드(id_col_03) 조회 후 확인
        String mappedCode = cs.coreInstCodeFindByName(candidate);
        if (mappedCode != null && !mappedCode.isBlank()) {
            return resolveInstByCodeCandidate(mappedCode.trim());
        }

        return null;
    }

    private String resolveInstByCodeCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        // 테이블명 안전성 보장 (영문/숫자/언더바만, 대소문자 허용)
        if (!candidate.matches("^[A-Za-z0-9_]{2,32}$")) {
            return null;
        }

        // 1) 입력값 그대로 확인
        String resolved = cs.resolveInstByTable(candidate);
        if (resolved != null) {
            return resolved;
        }

        // 2) 대문자/소문자 fallback 확인
        String upper = candidate.toUpperCase();
        if (!upper.equals(candidate)) {
            resolved = cs.resolveInstByTable(upper);
            if (resolved != null) {
                return resolved;
            }
        }
        String lower = candidate.toLowerCase();
        if (!lower.equals(candidate)) {
            resolved = cs.resolveInstByTable(lower);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    public int loginCount(String inst, String username, String encPwd) {
        Integer cnt = cs.loginCount(inst, username, encPwd);
        return cnt == null ? 0 : cnt;
    }

    public boolean isInstitutionAvailable(String inst) {
        if (inst == null || inst.isBlank()) {
            return false;
        }
        if ("core".equalsIgnoreCase(inst.trim())) {
            return true;
        }
        Instdata instdata = cs.coreInstFindByCode(inst.trim());
        if (instdata == null) {
            return true;
        }
        return !"n".equalsIgnoreCase(instdata.getId_col_04());
    }

    public boolean isUserAvailable(Userdata info) {
        if (info == null) {
            return false;
        }
        if ("n".equalsIgnoreCase(info.getUs_col_07())) {
            return false;
        }
        Integer userStatus = info.getUs_col_09();
        return userStatus == null || userStatus != 2;
    }

    public boolean isRoomBoardCounselLinkEnabled(String inst) {
        if (inst == null || inst.isBlank()) {
            return false;
        }
        String resolvedInst = resolveInst(inst);
        String candidate = resolvedInst == null ? inst.trim() : resolvedInst;
        String safeInst;
        try {
            safeInst = sanitizeInst(candidate);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return isPlatformIntegrationEnabled(safeInst);
    }

    private boolean isPlatformIntegrationEnabled(String inst) {
        try {
            if (!tableExistsInCsm("mp_institution_integration")) {
                return true;
            }
            List<String> rows = jdbcTemplate.query("""
                    SELECT use_yn
                    FROM csm.mp_institution_integration
                    WHERE inst_code = ?
                      AND integration_code = ?
                    LIMIT 1
                    """, (rs, rowNum) -> rs.getString("use_yn"), inst, PLATFORM_INTEGRATION_ROOMBOARD_CSM_LINK);
            if (rows.isEmpty()) {
                return true;
            }
            return !"N".equalsIgnoreCase(rows.get(0));
        } catch (Exception e) {
            log.warn("[platform] room-board integration check skipped inst={}, err={}", inst, e.toString());
            return true;
        }
    }

    public List<CounselData> searchCounselData(Criteria cri) {
        return cs.searchCounselData(cri);
    }

    public int CounselListCnt(Criteria cri) {
        return cs.CounselListCnt(cri);
    }

    public List<Map<String, Object>> getOrderItems(String inst) {
        return cs.getOrderItems(inst);
    }

    public List<Map<String, Object>> getInnerContentItems(String inst) {
        return cs.getInnerContentItems(inst);
    }

    public List<Guardian> getGuardiansById(String inst, Integer cs_idx) {
        return cs.getGuardiansById(inst, cs_idx);
    }

    public List<Userdata> userSelect(Userdata ud) {
        return cs.userSelect(ud);
    }

    public Userdata userInfo(int us_col_01, String instCode) {
        return cs.userInfo(us_col_01, instCode);
    }

    public int userInsert(Userdata ud) {
        return cs.userInsert(ud);
    }

    public int userUpdate(Userdata ud) {
        return cs.userUpdate(ud);
    }

    public int userUpdatePassword(Userdata ud) {
        return cs.userUpdatePassword(ud);
    }

    public int userDelete(Userdata ud) {
        return cs.userDelete(ud);
    }

    public List<Instdata> coreInstSelect() {
        return cs.coreInstSelect();
    }

    public Instdata coreInstFindByCode(String instCode) {
        return cs.coreInstFindByCode(instCode);
    }

    public int coreInstInsert(Instdata instdata) {
        return cs.coreInstInsert(instdata);
    }

    public int createUserTableIfNotExists(String inst) {
        return cs.createUserTableIfNotExists(inst);
    }

    public void createCoreInstSchemaTables(String inst) {
        String safe = sanitizeInst(inst);
        List<String> ddls = List.of(
                "CREATE TABLE IF NOT EXISTS csm.inst_data_" + safe + " ("
                        + "id_col_01 int not null auto_increment primary key,"
                        + "id_col_02 varchar(50) default null comment '기관명',"
                        + "id_col_03 varchar(50) default null comment '기관코드',"
                        + "id_col_04 varchar(5) default 'y' comment '사용상태',"
                        + "id_col_05 varchar(50) default null comment '비고'"
                        + ") comment='기관 테이블'",

                "CREATE TABLE IF NOT EXISTS csm.user_data_" + safe + " ("
                        + "us_col_01 int NOT NULL AUTO_INCREMENT COMMENT '사용자코드',"
                        + "us_col_02 varchar(100) NOT NULL COMMENT '아이디',"
                        + "us_col_03 varchar(100) DEFAULT NULL COMMENT '사용자비번',"
                        + "us_col_04 varchar(100) DEFAULT NULL COMMENT '소속회사코드',"
                        + "us_col_05 varchar(100) DEFAULT NULL COMMENT '소속회사명',"
                        + "us_col_06 varchar(100) DEFAULT NULL COMMENT '비고',"
                        + "us_col_07 varchar(100) DEFAULT 'y' COMMENT '사용상태',"
                        + "us_col_08 int DEFAULT '1' COMMENT '권한',"
                        + "us_col_09 int DEFAULT '1' COMMENT '1=사용,2=삭제',"
                        + "us_col_10 varchar(500) DEFAULT NULL COMMENT '연락처',"
                        + "us_col_11 varchar(100) DEFAULT NULL COMMENT '이메일',"
                        + "us_col_12 varchar(100) DEFAULT NULL COMMENT '이름',"
                        + "us_col_13 varchar(100) DEFAULT NULL COMMENT '부서',"
                        + "us_col_14 varchar(100) DEFAULT NULL COMMENT '직급',"
                        + "PRIMARY KEY (us_col_01)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자정보'",

                "CREATE TABLE IF NOT EXISTS csm.counsel_category1_" + safe + " ("
                        + "cc_col_01 int not null auto_increment primary key,"
                        + "cc_col_02 varchar(50) default null comment '대분류명',"
                        + "turn int default null comment '분류 순서'"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_category2_" + safe + " ("
                        + "cc_col_01 int not null auto_increment primary key,"
                        + "cc_col_02 varchar(100) default null comment '소분류명',"
                        + "cc_col_03 int not null comment '대분류idx',"
                        + "cc_col_04 tinyint(1) default 0 comment '체크박스여부',"
                        + "cc_col_05 tinyint(1) default 0 comment '라디오박스여부',"
                        + "cc_col_06 tinyint(1) default 0 comment '텍스트박스여부',"
                        + "cc_col_07 tinyint(1) default 0 comment '셀렉트박스여부',"
                        + "turn int default null comment '분류 순서'"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_category3_" + safe + " ("
                        + "cc_col_01 int not null auto_increment primary key,"
                        + "cc_col_02 varchar(100) default null comment '소분류 셀렉트박스 명',"
                        + "cc_col_03 varchar(255) default null comment '셀렉트박스 옵션',"
                        + "cc_col_04 int not null comment '소분류 idx',"
                        + "turn int default null comment '분류 순서'"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_data_" + safe + " ("
                        + "cs_idx int auto_increment primary key,"
                        + "created_at timestamp default current_timestamp,"
                        + "updated_at timestamp default current_timestamp on update current_timestamp,"
                        + "cs_col_01 varchar(500) default null,"
                        + "cs_col_02 varchar(50) default null,"
                        + "cs_col_03 varchar(50) default null,"
                        + "cs_col_04 varchar(50) default null,"
                        + "cs_col_05 varchar(50) default null,"
                        + "cs_col_06 varchar(50) default null,"
                        + "cs_col_07 varchar(50) default null,"
                        + "cs_col_08 varchar(50) default null,"
                        + "cs_col_09 varchar(50) default null,"
                        + "cs_col_10 varchar(50) default null,"
                        + "cs_col_11 varchar(500) default null,"
                        + "cs_col_12 varchar(500) default null,"
                        + "cs_col_13 varchar(500) default null,"
                        + "cs_col_14 varchar(50) default null,"
                        + "cs_col_15 varchar(500) default null,"
                        + "cs_col_16 varchar(10) default null,"
                        + "cs_col_17 varchar(50) default null,"
                        + "cs_col_18 varchar(50) default null,"
                        + "cs_col_19 varchar(50) default null,"
                        + "cs_col_20 varchar(100) default null,"
                        + "cs_col_21 varchar(100) default null,"
                        + "cs_col_22 varchar(50) default null,"
                        + "cs_col_23 varchar(500) default null,"
                        + "cs_col_24 varchar(50) default null,"
                        + "cs_col_25 varchar(50) default null,"
                        + "cs_col_26 varchar(50) default null,"
                        + "cs_col_27 varchar(50) default null,"
                        + "cs_col_28 varchar(50) default null,"
                        + "cs_col_29 varchar(50) default null,"
                        + "cs_col_30 varchar(50) default null,"
                        + "cs_col_31 varchar(50) default null,"
                        + "cs_col_32 longtext default null,"
                        + "cs_col_33 varchar(10) default null,"
                        + "cs_col_34 varchar(50) default null,"
                        + "cs_col_35 varchar(50) default null,"
                        + "cs_col_36 varchar(100) default null,"
                        + "cs_col_37 varchar(10) default null,"
                        + "cs_col_38 varchar(100) default null,"
                        + "cs_col_39 varchar(50) default null,"
                        + "cs_col_40 varchar(500) default null,"
                        + "cs_col_41 varchar(50) default null,"
                        + "cs_col_42 varchar(50) default null,"
                        + "cs_col_01_hash varbinary(32) default null"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_data_" + safe + "_entries ("
                        + "entry_id int auto_increment primary key,"
                        + "cs_idx int not null,"
                        + "category_id int not null,"
                        + "subcategory_id int null,"
                        + "field_type varchar(30) default null,"
                        + "value text"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_data_" + safe + "_guardians ("
                        + "id int auto_increment primary key,"
                        + "cs_idx int not null,"
                        + "name varchar(255) default null,"
                        + "relationship varchar(255) default null,"
                        + "contact_number varchar(255) default null,"
                        + "created_at timestamp default current_timestamp,"
                        + "updated_at timestamp default current_timestamp on update current_timestamp,"
                        + "name_hash varbinary(32) default null,"
                        + "contact_number_hash varbinary(32) default null"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_admission_pledge_" + safe + " ("
                        + "id bigint auto_increment primary key,"
                        + "cs_idx int not null,"
                        + "agreed_yn char(1) not null default 'N',"
                        + "signer_name varchar(100) default null,"
                        + "signer_relation varchar(50) default null,"
                        + "guardian_name varchar(100) default null,"
                        + "guardian_relation varchar(50) default null,"
                        + "guardian_addr varchar(255) default null,"
                        + "guardian_phone varchar(50) default null,"
                        + "guardian_cost_yn char(1) not null default 'N',"
                        + "sub_guardian_name varchar(100) default null,"
                        + "sub_guardian_relation varchar(50) default null,"
                        + "sub_guardian_addr varchar(255) default null,"
                        + "sub_guardian_phone varchar(50) default null,"
                        + "sub_guardian_cost_yn char(1) not null default 'N',"
                        + "signed_at varchar(19) default null,"
                        + "pledge_text text,"
                        + "signature_data longtext,"
                        + "page_ink_data longtext,"
                        + "created_at timestamp default current_timestamp,"
                        + "updated_at timestamp default current_timestamp on update current_timestamp,"
                        + "unique key uq_cs_idx (cs_idx)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_reservation_" + safe + " ("
                        + "id bigint auto_increment primary key,"
                        + "patient_name varchar(100) not null,"
                        + "patient_phone varchar(50) default null,"
                        + "guardian_name varchar(100) default null,"
                        + "call_summary text,"
                        + "priority int not null default 3,"
                        + "reserved_at datetime default null,"
                        + "status varchar(20) not null default 'RESERVED',"
                        + "linked_cs_idx int default null,"
                        + "created_by varchar(100) default null,"
                        + "completed_by varchar(100) default null,"
                        + "completed_at datetime default null,"
                        + "created_at timestamp default current_timestamp,"
                        + "updated_at timestamp default current_timestamp on update current_timestamp,"
                        + "key idx_status_priority_reserved (status, priority, reserved_at),"
                        + "key idx_linked_cs_idx (linked_cs_idx)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_list_" + safe + " ("
                        + "idx int auto_increment primary key,"
                        + "coulmn varchar(50) default null,"
                        + "comment varchar(50) default null,"
                        + "turn int default null,"
                        + "view_yn varchar(5) not null default 'y'"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_log_" + safe + " ("
                        + "idx int auto_increment primary key,"
                        + "cs_idx int not null,"
                        + "name varchar(100) not null,"
                        + "counsel_content text,"
                        + "counsel_method varchar(255) default null,"
                        + "counsel_result varchar(255) default null,"
                        + "counsel_name varchar(255) default null,"
                        + "created_at varchar(255) default null,"
                        + "updated_at varchar(255) default null,"
                        + "counsel_at varchar(255) default null"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_log_guardians_" + safe + " ("
                        + "idx int auto_increment primary key,"
                        + "log_idx int not null,"
                        + "counsel_guardian varchar(255) default null,"
                        + "counsel_relationship varchar(255) default null,"
                        + "counsel_number varchar(255) default null,"
                        + "counsel_at varchar(50) default null"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_audio_" + safe + " ("
                        + "id bigint auto_increment primary key,"
                        + "cs_idx int default null,"
                        + "temp_key varchar(80) default null,"
                        + "original_filename varchar(255) not null,"
                        + "stored_filename varchar(255) not null,"
                        + "mime_type varchar(100) default null,"
                        + "file_size bigint default 0,"
                        + "duration_seconds decimal(10,3) default null,"
                        + "transcript longtext,"
                        + "created_by varchar(100) default null,"
                        + "created_at timestamp default current_timestamp,"
                        + "updated_at timestamp default current_timestamp on update current_timestamp"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.counsel_file_" + safe + " ("
                        + "id bigint auto_increment primary key,"
                        + "cs_idx int default null,"
                        + "temp_key varchar(80) default null,"
                        + "original_filename varchar(255) not null,"
                        + "stored_filename varchar(255) not null,"
                        + "mime_type varchar(100) default null,"
                        + "file_size bigint default 0,"
                        + "created_by varchar(100) default null,"
                        + "created_at timestamp default current_timestamp,"
                        + "updated_at timestamp default current_timestamp on update current_timestamp"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.message_templates_" + safe + " ("
                        + "id int auto_increment primary key,"
                        + "title varchar(255) not null,"
                        + "template text not null,"
                        + "created_at datetime default current_timestamp"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.transmission_history_" + safe + " ("
                        + "id int auto_increment primary key,"
                        + "contents text not null,"
                        + "from_phone varchar(15) not null,"
                        + "to_phone varchar(15) not null,"
                        + "status varchar(20) not null,"
                        + "response text,"
                        + "created_at datetime default current_timestamp,"
                        + "send_type varchar(10),"
                        + "refkey varchar(50) default null,"
                        + "reserve_time datetime default null"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.phone_number_" + safe + " ("
                        + "id int auto_increment primary key,"
                        + "phone_num varchar(15) not null,"
                        + "phone_name varchar(100) not null"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.card_" + safe + " ("
                        + "id int auto_increment primary key,"
                        + "title varchar(50) not null,"
                        + "content varchar(1000) not null,"
                        + "created_at datetime default current_timestamp"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS csm.sms_request_" + safe + " ("
                        + "id bigint not null auto_increment primary key,"
                        + "device varchar(50) not null,"
                        + "cmsg_id varchar(50) not null,"
                        + "msg_id varchar(50) not null,"
                        + "phone varchar(50) not null,"
                        + "media varchar(50) not null,"
                        + "to_name varchar(50) default null,"
                        + "unix_time varchar(50) not null,"
                        + "result varchar(50) not null,"
                        + "refkey varchar(50) default null,"
                        + "insert_date datetime not null default current_timestamp"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        for (String ddl : ddls) {
            jdbcTemplate.execute(ddl);
        }
        ensureAdmissionPledgeTable(safe);
        ensureCounselDataEntryColumns(safe);
        ensureCounselGuardianColumns(safe);
        ensureRoleTables(safe);
    }

    private void ensureRoleTables(String safe) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS csm.role_" + safe + " ("
                + "role_id    bigint AUTO_INCREMENT PRIMARY KEY,"
                + "role_code  varchar(64) NOT NULL,"
                + "role_name  varchar(100),"
                + "description varchar(255),"
                + "is_system  tinyint(1) DEFAULT 0,"
                + "created_at datetime DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uq_role_code (role_code)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS csm.role_permission_" + safe + " ("
                + "role_id         bigint NOT NULL,"
                + "permission_code varchar(64) NOT NULL,"
                + "PRIMARY KEY (role_id, permission_code)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS csm.user_role_" + safe + " ("
                + "user_id     bigint NOT NULL,"
                + "role_id     bigint NOT NULL,"
                + "assigned_at datetime DEFAULT CURRENT_TIMESTAMP,"
                + "assigned_by varchar(100),"
                + "PRIMARY KEY (user_id, role_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS csm.user_permission_" + safe + " ("
                + "user_id         bigint NOT NULL,"
                + "permission_code varchar(64) NOT NULL,"
                + "granted         tinyint(1) NOT NULL DEFAULT 1,"
                + "PRIMARY KEY (user_id, permission_code)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        seedRoles(safe);
    }

    private void seedRoles(String safe) {
        String upsertRole = "INSERT INTO csm.role_" + safe
                + " (role_code, role_name, description, is_system)"
                + " VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description)";

        Object[][] roles = {
            {"SYSTEM_INST_ADMIN",          "기관 관리자",   "기관 내 전체 권한 (시스템 역할, 편집 불가)", 1},
            {"TEMPLATE_COUNSELOR",         "상담사",        "기본 상담사 권한 세트",                      0},
            {"TEMPLATE_RECEPTION",         "접수 담당",     "상담 접수 전담 권한",                        0},
            {"TEMPLATE_ROOM_BOARD_MANAGER","병실 관리자",   "병실현황판·입원예약 관리 권한",               0},
            {"TEMPLATE_VIEWER",            "뷰어",          "읽기 전용 권한",                             0},
            {"TEMPLATE_SMS_OPERATOR",      "문자 담당",     "문자 발송 전담 권한",                        0},
        };
        for (Object[] row : roles) {
            jdbcTemplate.update(upsertRole, row);
        }

        seedRolePermissions(safe);
    }

    private void seedRolePermissions(String safe) {
        String insertPerm = "INSERT IGNORE INTO csm.role_permission_" + safe
                + " (role_id, permission_code)"
                + " SELECT r.role_id, ? FROM csm.role_" + safe + " r WHERE r.role_code = ?";

        // SYSTEM_INST_ADMIN — all permissions from permission_master (if it exists)
        try {
            Integer pmCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='csm' AND table_name='permission_master'",
                Integer.class);
            if (pmCount != null && pmCount > 0) {
                List<String> allCodes = jdbcTemplate.queryForList(
                    "SELECT code FROM csm.permission_master", String.class);
                for (String code : allCodes) {
                    jdbcTemplate.update(insertPerm, code, "SYSTEM_INST_ADMIN");
                }
            }
        } catch (Exception e) {
            log.warn("[seed-roles] SYSTEM_INST_ADMIN perms skipped: {}", e.toString());
        }

        // TEMPLATE_COUNSELOR
        String[] counselorPerms = {
            "COUNSEL_RESERVATION:READ", "COUNSEL_RESERVATION:CREATE", "COUNSEL_RESERVATION:EDIT",
            "COUNSEL_RESERVATION:CANCEL",
            "COUNSEL:READ", "COUNSEL:CREATE", "COUNSEL:EDIT",
            "COUNSEL_LIST:READ",
            "NOTICE:READ",
            "SMS:SEND", "SMS:HISTORY_READ",
        };
        for (String code : counselorPerms) {
            jdbcTemplate.update(insertPerm, code, "TEMPLATE_COUNSELOR");
        }

        // TEMPLATE_RECEPTION
        String[] receptionPerms = {
            "COUNSEL_RESERVATION:READ", "COUNSEL_RESERVATION:CREATE", "COUNSEL_RESERVATION:EDIT",
            "COUNSEL_RESERVATION:CANCEL",
            "COUNSEL_LIST:READ",
        };
        for (String code : receptionPerms) {
            jdbcTemplate.update(insertPerm, code, "TEMPLATE_RECEPTION");
        }

        // TEMPLATE_ROOM_BOARD_MANAGER
        String[] roomBoardPerms = {
            "ROOM_BOARD:READ", "ROOM_BOARD:WRITE", "ROOM_BOARD:SNAPSHOT_MANAGE",
            "ADMISSION:READ", "ADMISSION:UPDATE_DETAILS", "ADMISSION:CONFIRM", "ADMISSION:CANCEL",
        };
        for (String code : roomBoardPerms) {
            jdbcTemplate.update(insertPerm, code, "TEMPLATE_ROOM_BOARD_MANAGER");
        }

        // TEMPLATE_VIEWER
        String[] viewerPerms = {
            "ROOM_BOARD:READ",
            "COUNSEL_LIST:READ",
        };
        for (String code : viewerPerms) {
            jdbcTemplate.update(insertPerm, code, "TEMPLATE_VIEWER");
        }

        // TEMPLATE_SMS_OPERATOR
        String[] smsPerms = {
            "SMS:SEND", "SMS:BULK_SEND", "SMS:HISTORY_READ", "SMS:TEMPLATE_EDIT",
            "COUNSEL_LIST:READ",
        };
        for (String code : smsPerms) {
            jdbcTemplate.update(insertPerm, code, "TEMPLATE_SMS_OPERATOR");
        }
    }

    private void ensureCounselDataEntryColumns(String safe) {
        ensureTableColumn("csm", "counsel_data_" + safe + "_entries", "field_type",
                "ALTER TABLE csm.counsel_data_" + safe + "_entries ADD COLUMN field_type varchar(30) default null");
        ensureTableColumn("csm", "counsel_data_" + safe, "cs_col_01_hash",
                "ALTER TABLE csm.counsel_data_" + safe + " ADD COLUMN cs_col_01_hash varbinary(32) default null");
    }

    private void ensureCounselGuardianColumns(String safe) {
        ensureTableColumn("csm", "counsel_data_" + safe + "_guardians", "name_hash",
                "ALTER TABLE csm.counsel_data_" + safe + "_guardians ADD COLUMN name_hash varbinary(32) default null");
        ensureTableColumn("csm", "counsel_data_" + safe + "_guardians", "contact_number_hash",
                "ALTER TABLE csm.counsel_data_" + safe + "_guardians ADD COLUMN contact_number_hash varbinary(32) default null");
    }

    public Map<String, Object> inspectCoreInstSchema(String inst) {
        String safe = sanitizeInst(inst);
        List<String> requiredTables = getRequiredCoreInstTables(safe);
        List<String> missingTables = new ArrayList<>();
        int existingTableCount = 0;

        for (String tableName : requiredTables) {
            if (tableExistsInCsm(tableName)) {
                existingTableCount++;
            } else {
                missingTables.add(tableName);
            }
        }

        List<String> missingColumns = new ArrayList<>();
        String admissionPledgeTable = "counsel_admission_pledge_" + safe;
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "page_ink_data")) {
            missingColumns.add(admissionPledgeTable + ".page_ink_data");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "guardian_name")) {
            missingColumns.add(admissionPledgeTable + ".guardian_name");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "guardian_relation")) {
            missingColumns.add(admissionPledgeTable + ".guardian_relation");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "guardian_addr")) {
            missingColumns.add(admissionPledgeTable + ".guardian_addr");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "guardian_phone")) {
            missingColumns.add(admissionPledgeTable + ".guardian_phone");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "guardian_cost_yn")) {
            missingColumns.add(admissionPledgeTable + ".guardian_cost_yn");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "sub_guardian_name")) {
            missingColumns.add(admissionPledgeTable + ".sub_guardian_name");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "sub_guardian_relation")) {
            missingColumns.add(admissionPledgeTable + ".sub_guardian_relation");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "sub_guardian_addr")) {
            missingColumns.add(admissionPledgeTable + ".sub_guardian_addr");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "sub_guardian_phone")) {
            missingColumns.add(admissionPledgeTable + ".sub_guardian_phone");
        }
        if (!missingTables.contains(admissionPledgeTable)
                && !columnExistsInCsm(admissionPledgeTable, "sub_guardian_cost_yn")) {
            missingColumns.add(admissionPledgeTable + ".sub_guardian_cost_yn");
        }

        Map<String, Object> out = new HashMap<>();
        out.put("inst", safe);
        out.put("required_table_count", requiredTables.size());
        out.put("existing_table_count", existingTableCount);
        out.put("missing_table_count", missingTables.size());
        out.put("missing_tables", missingTables);
        out.put("missing_column_count", missingColumns.size());
        out.put("missing_columns", missingColumns);
        out.put("ok", missingTables.isEmpty() && missingColumns.isEmpty());
        return out;
    }

    public Map<String, Object> repairCoreInstSchema(String inst) {
        String safe = sanitizeInst(inst);
        Map<String, Object> before = inspectCoreInstSchema(safe);
        createCoreInstSchemaTables(safe);
        Map<String, Object> after = inspectCoreInstSchema(safe);
        Map<String, Object> out = new HashMap<>();
        out.put("inst", safe);
        out.put("before_status", before);
        out.put("after_status", after);
        out.put("ok", Boolean.TRUE.equals(after.get("ok")));
        return out;
    }

    private List<String> getRequiredCoreInstTables(String safeInst) {
        return List.of(
                "inst_data_" + safeInst,
                "user_data_" + safeInst,
                "counsel_category1_" + safeInst,
                "counsel_category2_" + safeInst,
                "counsel_category3_" + safeInst,
                "counsel_data_" + safeInst,
                "counsel_data_" + safeInst + "_entries",
                "counsel_data_" + safeInst + "_guardians",
                "counsel_admission_pledge_" + safeInst,
                "counsel_reservation_" + safeInst,
                "counsel_list_" + safeInst,
                "counsel_log_" + safeInst,
                "counsel_log_guardians_" + safeInst,
                "counsel_audio_" + safeInst,
                "counsel_file_" + safeInst,
                "message_templates_" + safeInst,
                "transmission_history_" + safeInst,
                "phone_number_" + safeInst,
                "card_" + safeInst,
                "sms_request_" + safeInst);
    }

    private boolean tableExistsInCsm(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExistsInCsm(String tableName, String columnName) {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private String sanitizeInst(String inst) {
        if (inst == null) {
            throw new IllegalArgumentException("inst is null");
        }
        String normalized = inst.trim();
        if (!normalized.matches("[A-Za-z0-9_]{2,20}")) {
            throw new IllegalArgumentException("Invalid inst: " + inst);
        }
        return normalized;
    }

    public List<String> getCounselStatusOptions(String inst) {
        return getDistinctCounselColumnValues(inst, "cs_col_19");
    }

    public List<String> getCounselPathTypeOptions(String inst) {
        return getDistinctCounselColumnValues(inst, "cs_col_08");
    }

    private List<String> getDistinctCounselColumnValues(String inst, String columnName) {
        String safe = sanitizeInst(inst);
        if (!"cs_col_19".equals(columnName) && !"cs_col_08".equals(columnName)) {
            throw new IllegalArgumentException("Unsupported columnName: " + columnName);
        }

        String sql = "SELECT DISTINCT TRIM(" + columnName + ") AS v "
                + "FROM csm.counsel_data_" + safe + " "
                + "WHERE " + columnName + " IS NOT NULL AND TRIM(" + columnName + ") <> '' "
                + "ORDER BY v ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String value = Objects.toString(row.get("v"), "").trim();
            if (!value.isBlank()) {
                out.add(value);
            }
        }
        return out;
    }

    public int coreInstUpdate(Instdata instdata) {
        return cs.coreInstUpdate(instdata);
    }

    public int coreInstDelete(int idCol01) {
        return cs.coreInstDelete(idCol01);
    }

    public String coreInstNumberFind(String instName) {
        return cs.coreInstNumberFind(instName);
    }

    public List<UserdataCs> userSelectCs() {
        return cs.userSelectCs();
    }

    public int newuserInsertCs(UserdataCs us) {
        return cs.newuserInsertCs(us);
    }

    public int userDeleteCs(int id) {
        return cs.userDeleteCs(id);
    }

    public int userDeleteByUsername(String inst, String username) {
        return cs.userDeleteByUsername(inst, username);
    }

    public List<Map<String, Object>> coreTemplateSelect() {
        return cs.coreTemplateSelect();
    }

    public int coreTemplateInsert(String name, String description) {
        return cs.coreTemplateInsert(name, description);
    }

    public int coreTemplateModify(int idx, String name, String description) {
        return cs.coreTemplateModify(idx, name, description);
    }

    public int coreTemplateDelete(int idx) {
        return cs.coreTemplateDelete(idx);
    }

    public int corePriceInsertAll(String smsPrice, String lmsPrice, String mmsPrice) {
        return cs.corePriceInsertAll(smsPrice, lmsPrice, mmsPrice);
    }

    public int corePriceInsert(String idCol03, String smsPrice, String lmsPrice, String mmsPrice) {
        return cs.corePriceInsert(idCol03, smsPrice, lmsPrice, mmsPrice);
    }

    public List<Map<String, Object>> coreTemplateMainSelect(int templateIdx) {
        return cs.coreTemplateMainSelect(templateIdx);
    }

    public int coreTemplateMainInsert(int templateIdx, String name, int turn) {
        return cs.coreTemplateMainInsert(templateIdx, name, turn);
    }

    public int coreTemplateMainUpdate(int idx, String name) {
        return cs.coreTemplateMainUpdate(idx, name);
    }

    @Transactional
    public int coreTemplateMainDeleteCascade(int idx) {
        List<Map<String, Object>> subs = cs.coreTemplateSubSelect(idx);
        for (Map<String, Object> s : subs) {
            if (s == null || s.get("idx") == null)
                continue;
            int subIdx = Integer.parseInt(String.valueOf(s.get("idx")));
            cs.coreTemplateOptionDeleteBySubIdx(subIdx);
        }
        cs.coreTemplateSubDeleteByMainIdx(idx);
        return cs.coreTemplateMainDelete(idx);
    }

    public int coreTemplateMainTurnUpdate(int idx, int turn) {
        return cs.coreTemplateMainTurnUpdate(idx, turn);
    }

    public List<Map<String, Object>> coreTemplateSubSelect(int mainIdx) {
        return cs.coreTemplateSubSelect(mainIdx);
    }

    public int coreTemplateSubInsert(int mainIdx, String name, int chk, int rad, int txt, int sel, int turn) {
        return cs.coreTemplateSubInsert(mainIdx, name, chk, rad, txt, sel, turn);
    }

    public int coreTemplateSubUpdate(int idx, String name) {
        return cs.coreTemplateSubUpdate(idx, name);
    }

    public int coreTemplateSubUpdateWithFlags(int idx, String name, Integer chk, Integer rad, Integer txt, Integer sel) {
        return cs.coreTemplateSubUpdateWithFlags(idx, name, chk, rad, txt, sel);
    }

    @Transactional
    public int coreTemplateSubDeleteCascade(int idx) {
        cs.coreTemplateOptionDeleteBySubIdx(idx);
        return cs.coreTemplateSubDelete(idx);
    }

    public int coreTemplateSubTurnUpdate(int idx, int turn) {
        return cs.coreTemplateSubTurnUpdate(idx, turn);
    }

    public List<Map<String, Object>> coreTemplateOptionSelect(int subIdx) {
        return cs.coreTemplateOptionSelect(subIdx);
    }

    public int coreTemplateOptionInsert(int subIdx, String option, int turn) {
        return cs.coreTemplateOptionInsert(subIdx, option, turn);
    }

    public int coreTemplateOptionUpdate(int idx, String option) {
        return cs.coreTemplateOptionUpdate(idx, option);
    }

    public int coreTemplateOptionDelete(int idx) {
        return cs.coreTemplateOptionDelete(idx);
    }

    public int coreTemplateOptionTurnUpdate(int idx, int turn) {
        return cs.coreTemplateOptionTurnUpdate(idx, turn);
    }

    public List<Card> SelectCard(String inst) {
        return cs.SelectCard(inst);
    }

    public int cardCnt(Criteria cri) {
        return cs.cardCnt(cri);
    }

    public List<Card> SelectCardSearch(Criteria cri) {
        return cs.SelectCardSearch(cri);
    }

    public int InsertCard(Card card) {
        return cs.InsertCard(card);
    }

    public int UpdateCard(Card card) {
        return cs.UpdateCard(card);
    }

    public int DeleteCard(Card card) {
        return cs.DeleteCard(card);
    }

    public List<Counsel_phone> selectPhone(Counsel_phone cp) {
        return cs.selectPhone(cp);
    }

    public Map<String, Object> getCategoryData(String inst) {
        List<Category1> category1Raw = cs.selectCategory1(inst);

        List<Category1> category1List = Optional.ofNullable(category1Raw)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .toList();

        List<Category1WithSubcategoriesAndOptions> result = new ArrayList<>();
        Map<String, String> fieldTypeMapping = new HashMap<>();
        Map<String, List<Category3>> fieldOptionsMapping = new HashMap<>();

        for (Category1 category1 : category1List) {
            if (category1 == null)
                continue;

            int c1Id = category1.getCc_col_01();
            List<Category2> subcategories = Optional.ofNullable(cs.selectCategory2ByCategory1Id(inst, c1Id))
                    .orElse(Collections.emptyList())
                    .stream().filter(Objects::nonNull).toList();

            List<Category2WithOptions> subcategoriesWithOptions = new ArrayList<>();

            for (Category2 subcategory : subcategories) {
                if (subcategory == null)
                    continue;

                int c2Id = subcategory.getCc_col_01();
                List<Category3> options = Optional.ofNullable(cs.selectCategory3ByCategory2Id(inst, c2Id))
                        .orElse(Collections.emptyList())
                        .stream().filter(Objects::nonNull).toList();

                // ★ DB의 cc_col_03(문자열)을 VO의 List로 변환
                for (Category3 opt : options) {
                    if (opt == null)
                        continue;

                    // MyBatis에서 cc_col_03 → VO의 category3(String)으로 매핑해둔다고 가정
                    if (opt.getCategory3() != null && !opt.getCategory3().isBlank()) {
                        opt.setCc_col_03FromString(opt.getCategory3()); // 콤마면 분할, 단일값이면 1개짜리 리스트
                    }

                    // 그래도 비었다면 cc_col_02를 단일 라벨로 사용 (안전망)
                    if (opt.getCc_col_03() == null || opt.getCc_col_03().isEmpty()) {
                        if (opt.getCc_col_02() != null && !opt.getCc_col_02().isBlank()) {
                            opt.setCc_col_03FromString(opt.getCc_col_02());
                        }
                    }
                }

                Category2WithOptions dto = new Category2WithOptions();
                dto.setCategory2(subcategory);
                dto.setOptions(options);
                subcategoriesWithOptions.add(dto);

                // 타입 판별
                boolean chk = Integer.valueOf(subcategory.getCc_col_04()) == 1;
                boolean rad = Integer.valueOf(subcategory.getCc_col_05()) == 1;
                boolean txt = Integer.valueOf(subcategory.getCc_col_06()) == 1;
                boolean sel = Integer.valueOf(subcategory.getCc_col_07()) == 1;

                String key = "field_" + c1Id + "_" + c2Id;
                if (chk && !rad && !txt && !sel) {
                    fieldTypeMapping.put(key, "checkbox_only");
                } else if (rad && !chk && !txt && !sel) {
                    fieldTypeMapping.put(key, "radio_only");
                } else if (sel && !chk && !rad && !txt) {
                    fieldTypeMapping.put(key, "select_only");
                    fieldOptionsMapping.put(key, options);
                } else if (txt && !chk && !rad && !sel) {
                    fieldTypeMapping.put(key, "text_only");
                } else if (chk && txt && !rad && !sel) {
                    fieldTypeMapping.put(key, "checkbox_text");
                } else if (chk && sel && !rad && !txt) {
                    fieldTypeMapping.put(key, "checkbox_select");
                    fieldOptionsMapping.put(key, options);
                } else if (rad && txt && !chk && !sel) {
                    fieldTypeMapping.put(key, "radio_text");
                } else if (rad && sel && !chk && !txt) {
                    fieldTypeMapping.put(key, "radio_select");
                    fieldOptionsMapping.put(key, options);
                } else if (chk && txt && sel && !rad) {
                    fieldTypeMapping.put(key, "checkbox_select_text");
                    fieldOptionsMapping.put(key, options);
                } else if (rad && txt && sel && !chk) {
                    fieldTypeMapping.put(key, "radio_select_text");
                    fieldOptionsMapping.put(key, options);
                }
            }

            Category1WithSubcategoriesAndOptions c1 = new Category1WithSubcategoriesAndOptions();
            c1.setCategory1(category1);
            c1.setSubcategories(subcategoriesWithOptions);
            result.add(c1);
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("categoryData", result);
        resultMap.put("fieldTypeMapping", fieldTypeMapping);
        resultMap.put("fieldOptionsMapping", fieldOptionsMapping);
        return resultMap;
    }

    public List<Category1> selectCategory1(String inst) {
        return cs.selectCategory1(inst);
    }

    public List<Category2> getSubcategories(String inst, int categoryId) {
        return cs.selectCategory2ByCategory1Id(inst, categoryId);
    }

    public List<Category3> getOptions(String inst, int subCategoryId) {
        List<Category3> rows = Optional.ofNullable(cs.selectCategory3ByCategory2Id(inst, subCategoryId))
                .orElse(Collections.emptyList());
        for (Category3 row : rows) {
            if (row == null) {
                continue;
            }
            String raw = row.getCategory3();
            if (raw != null && !raw.isBlank()) {
                row.setCc_col_03FromString(raw);
            } else if (row.getCc_col_02() != null && !row.getCc_col_02().isBlank()) {
                row.setCc_col_03FromString(row.getCc_col_02());
            }
        }
        return rows;
    }

    public Category1 insertCategory1(String inst, String name) {
        String safe = sanitizeInst(inst);
        Integer turn = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(turn), 0) + 1 FROM csm.counsel_category1_" + safe,
                Integer.class);
        if (turn == null) {
            turn = 1;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = "INSERT INTO csm.counsel_category1_" + safe + " (cc_col_02, turn) VALUES (?, ?)";
        int finalTurn = turn;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setInt(2, finalTurn);
            return ps;
        }, keyHolder);

        Category1 row = new Category1();
        row.setCc_col_01(keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue());
        row.setCc_col_02(name);
        row.setInst(inst);
        return row;
    }

    public Category2 insertCategory2(String inst, int mainId, String name, int chk, int rad, int txt, int sel) {
        String safe = sanitizeInst(inst);
        Integer turn = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(turn), 0) + 1 FROM csm.counsel_category2_" + safe + " WHERE cc_col_03 = ?",
                Integer.class, mainId);
        if (turn == null) {
            turn = 1;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = "INSERT INTO csm.counsel_category2_" + safe
                + " (cc_col_02, cc_col_03, cc_col_04, cc_col_05, cc_col_06, cc_col_07, turn) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        int finalTurn = turn;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setInt(2, mainId);
            ps.setInt(3, chk);
            ps.setInt(4, rad);
            ps.setInt(5, txt);
            ps.setInt(6, sel);
            ps.setInt(7, finalTurn);
            return ps;
        }, keyHolder);

        Category2 row = new Category2();
        row.setCc_col_01(keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue());
        row.setCc_col_02(name);
        row.setCc_col_03(mainId);
        row.setCc_col_04(chk);
        row.setCc_col_05(rad);
        row.setCc_col_06(txt);
        row.setCc_col_07(sel);
        row.setInst(inst);
        return row;
    }

    public Category3 insertCategory3(String inst, int subId, String label, List<String> options) {
        String safe = sanitizeInst(inst);
        List<String> cleanOptions = Optional.ofNullable(options).orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
        String csv = String.join(",", cleanOptions);
        if (csv.isBlank()) {
            csv = Optional.ofNullable(label).orElse("").trim();
        }

        Integer turn = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(turn), 0) + 1 FROM csm.counsel_category3_" + safe + " WHERE cc_col_04 = ?",
                Integer.class, subId);
        if (turn == null) {
            turn = 1;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = "INSERT INTO csm.counsel_category3_" + safe + " (cc_col_02, cc_col_03, cc_col_04, turn) "
                + "VALUES (?, ?, ?, ?)";
        int finalTurn = turn;
        String finalCsv = csv;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, Optional.ofNullable(label).orElse(""));
            ps.setString(2, finalCsv);
            ps.setInt(3, subId);
            ps.setInt(4, finalTurn);
            return ps;
        }, keyHolder);

        Category3 row = new Category3();
        row.setCc_col_01(keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue());
        row.setCc_col_02(Optional.ofNullable(label).orElse(""));
        row.setCc_col_03FromString(finalCsv);
        row.setCc_col_04(subId);
        row.setInst(inst);
        row.setCategory3(finalCsv);
        return row;
    }

    @Transactional
    public int deleteCategory(String inst, String type, int id) {
        String safe = sanitizeInst(inst);
        if (id <= 0) {
            return 0;
        }
        String normalizedType = Optional.ofNullable(type).orElse("").trim().toLowerCase();

        if ("parent".equals(normalizedType) || "main".equals(normalizedType)) {
            List<Integer> subIds = jdbcTemplate.queryForList(
                    "SELECT cc_col_01 FROM csm.counsel_category2_" + safe + " WHERE cc_col_03 = ?",
                    Integer.class, id);
            for (Integer subId : subIds) {
                if (subId == null) {
                    continue;
                }
                jdbcTemplate.update("DELETE FROM csm.counsel_category3_" + safe + " WHERE cc_col_04 = ?", subId);
            }
            jdbcTemplate.update("DELETE FROM csm.counsel_category2_" + safe + " WHERE cc_col_03 = ?", id);
            return jdbcTemplate.update("DELETE FROM csm.counsel_category1_" + safe + " WHERE cc_col_01 = ?", id);
        }

        if ("child".equals(normalizedType) || "sub".equals(normalizedType)) {
            jdbcTemplate.update("DELETE FROM csm.counsel_category3_" + safe + " WHERE cc_col_04 = ?", id);
            return jdbcTemplate.update("DELETE FROM csm.counsel_category2_" + safe + " WHERE cc_col_01 = ?", id);
        }

        if ("option".equals(normalizedType) || "select".equals(normalizedType)) {
            return jdbcTemplate.update("DELETE FROM csm.counsel_category3_" + safe + " WHERE cc_col_01 = ?", id);
        }

        return 0;
    }

    public int modifyCategory(String inst, String type, int id, String inputValue, Integer chk, Integer rad, Integer txt, Integer sel) {
        String safe = sanitizeInst(inst);
        if (id <= 0) {
            return 0;
        }
        String normalizedType = Optional.ofNullable(type).orElse("").trim().toLowerCase();
        String value = Optional.ofNullable(inputValue).orElse("").trim();

        if ("parent".equals(normalizedType) || "main".equals(normalizedType)) {
            return jdbcTemplate.update("UPDATE csm.counsel_category1_" + safe + " SET cc_col_02 = ? WHERE cc_col_01 = ?", value, id);
        }

        if ("child".equals(normalizedType) || "sub".equals(normalizedType)) {
            if (chk == null && rad == null && txt == null && sel == null) {
                return jdbcTemplate.update("UPDATE csm.counsel_category2_" + safe + " SET cc_col_02 = ? WHERE cc_col_01 = ?", value, id);
            }
            return jdbcTemplate.update(
                    "UPDATE csm.counsel_category2_" + safe
                            + " SET cc_col_02 = ?, cc_col_04 = COALESCE(?, cc_col_04), cc_col_05 = COALESCE(?, cc_col_05), "
                            + "cc_col_06 = COALESCE(?, cc_col_06), cc_col_07 = COALESCE(?, cc_col_07) WHERE cc_col_01 = ?",
                    value, chk, rad, txt, sel, id);
        }

        if ("option".equals(normalizedType) || "select".equals(normalizedType)) {
            return jdbcTemplate.update(
                    "UPDATE csm.counsel_category3_" + safe + " SET cc_col_02 = ?, cc_col_03 = ? WHERE cc_col_01 = ?",
                    value, value, id);
        }
        return 0;
    }

    public int saveCategoryOrder(String inst, List<Map<String, Object>> rows) {
        String safe = sanitizeInst(inst);
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String type = Optional.ofNullable(row.get("type")).map(String::valueOf).orElse("").trim().toLowerCase();
            int id = parseInt(row.get("id"), parseInt(row.get("idx"), 0));
            int turn = parseInt(row.get("order"), parseInt(row.get("turn"), 0));
            if (id <= 0) {
                continue;
            }
            if ("parent".equals(type) || "main".equals(type)) {
                updated += jdbcTemplate.update("UPDATE csm.counsel_category1_" + safe + " SET turn = ? WHERE cc_col_01 = ?", turn, id);
            } else if ("child".equals(type) || "sub".equals(type)) {
                updated += jdbcTemplate.update("UPDATE csm.counsel_category2_" + safe + " SET turn = ? WHERE cc_col_01 = ?", turn, id);
            } else if ("option".equals(type) || "select".equals(type)) {
                updated += jdbcTemplate.update("UPDATE csm.counsel_category3_" + safe + " SET turn = ? WHERE cc_col_01 = ?", turn, id);
            }
        }
        return updated;
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public CounselData getCounselById(String inst, int csIdx) {
        CounselData counselData = cs.getCounselById(inst, csIdx);
        if (counselData != null) {
            List<CounselDataEntry> entries = cs.getEntriesByInstAndCsIdx(inst, csIdx);
            counselData.setEntries(entries);
        }

        return counselData;
    }

    public Map<String, Object> getAdmissionPledge(String inst, int csIdx) {
        if (csIdx <= 0) {
            return Collections.emptyMap();
        }
        String safe = sanitizeInst(inst);
        ensureAdmissionPledgeTable(safe);

        String sql = "SELECT cs_idx, agreed_yn, signer_name, signer_relation, "
                + "guardian_name, guardian_relation, guardian_addr, guardian_phone, guardian_cost_yn, "
                + "sub_guardian_name, sub_guardian_relation, sub_guardian_addr, sub_guardian_phone, sub_guardian_cost_yn, "
                + "signed_at, pledge_text, signature_data, page_ink_data "
                + "FROM csm.counsel_admission_pledge_" + safe + " WHERE cs_idx = ? LIMIT 1";
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql, csIdx);
        } catch (Exception e) {
            log.warn("[admission-pledge] select fail inst={}, cs_idx={}, err={}", safe, csIdx, e.toString());
            return Collections.emptyMap();
        }
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> row = rows.get(0);
        Map<String, Object> out = new HashMap<>();
        out.put("cs_idx", csIdx);
        out.put("agreed_yn", normalizeYn(row.get("agreed_yn")));
        out.put("signer_name", safeText(row.get("signer_name"), 100));
        out.put("signer_relation", safeText(row.get("signer_relation"), 50));
        out.put("guardian_name", safeText(row.get("guardian_name"), 100));
        out.put("guardian_relation", safeText(row.get("guardian_relation"), 50));
        out.put("guardian_addr", safeText(row.get("guardian_addr"), 255));
        out.put("guardian_phone", safeText(row.get("guardian_phone"), 50));
        out.put("guardian_cost_yn", normalizeYn(row.get("guardian_cost_yn")));
        out.put("sub_guardian_name", safeText(row.get("sub_guardian_name"), 100));
        out.put("sub_guardian_relation", safeText(row.get("sub_guardian_relation"), 50));
        out.put("sub_guardian_addr", safeText(row.get("sub_guardian_addr"), 255));
        out.put("sub_guardian_phone", safeText(row.get("sub_guardian_phone"), 50));
        out.put("sub_guardian_cost_yn", normalizeYn(row.get("sub_guardian_cost_yn")));
        out.put("signed_at", safeText(row.get("signed_at"), 19));
        out.put("pledge_text", safeText(row.get("pledge_text"), 5000));
        out.put("signature_data", safeText(row.get("signature_data"), 4_000_000));
        out.put("page_ink_data", safeText(row.get("page_ink_data"), 3_000_000));
        return out;
    }

    public int upsertAdmissionPledge(String inst, int csIdx, Map<String, Object> pledge) {
        if (csIdx <= 0) {
            return 0;
        }
        String safe = sanitizeInst(inst);
        ensureAdmissionPledgeTable(safe);

        String agreedYn = normalizeYn(pledge == null ? null : pledge.get("agreed_yn"));
        String signerName = safeText(pledge == null ? null : pledge.get("signer_name"), 100);
        String signerRelation = safeText(pledge == null ? null : pledge.get("signer_relation"), 50);
        String guardianName = safeText(pledge == null ? null : pledge.get("guardian_name"), 100);
        String guardianRelation = safeText(pledge == null ? null : pledge.get("guardian_relation"), 50);
        String guardianAddr = safeText(pledge == null ? null : pledge.get("guardian_addr"), 255);
        String guardianPhone = safeText(pledge == null ? null : pledge.get("guardian_phone"), 50);
        String guardianCostYn = normalizeYn(pledge == null ? null : pledge.get("guardian_cost_yn"));
        String subGuardianName = safeText(pledge == null ? null : pledge.get("sub_guardian_name"), 100);
        String subGuardianRelation = safeText(pledge == null ? null : pledge.get("sub_guardian_relation"), 50);
        String subGuardianAddr = safeText(pledge == null ? null : pledge.get("sub_guardian_addr"), 255);
        String subGuardianPhone = safeText(pledge == null ? null : pledge.get("sub_guardian_phone"), 50);
        String subGuardianCostYn = normalizeYn(pledge == null ? null : pledge.get("sub_guardian_cost_yn"));
        String signedAt = safeText(pledge == null ? null : pledge.get("signed_at"), 19);
        String pledgeText = safeText(pledge == null ? null : pledge.get("pledge_text"), 5000);
        String signatureData = safeText(pledge == null ? null : pledge.get("signature_data"), 4_000_000);
        String pageInkData = safeText(pledge == null ? null : pledge.get("page_ink_data"), 3_000_000);
        signatureData = normalizeAdmissionSignatureData(signatureData);
        pageInkData = normalizePngData(pageInkData, 3_000_000);

        String sql = "INSERT INTO csm.counsel_admission_pledge_" + safe
                + " (cs_idx, agreed_yn, signer_name, signer_relation, "
                + "guardian_name, guardian_relation, guardian_addr, guardian_phone, guardian_cost_yn, "
                + "sub_guardian_name, sub_guardian_relation, sub_guardian_addr, sub_guardian_phone, sub_guardian_cost_yn, "
                + "signed_at, pledge_text, signature_data, page_ink_data) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "agreed_yn = VALUES(agreed_yn), "
                + "signer_name = VALUES(signer_name), "
                + "signer_relation = VALUES(signer_relation), "
                + "guardian_name = VALUES(guardian_name), "
                + "guardian_relation = VALUES(guardian_relation), "
                + "guardian_addr = VALUES(guardian_addr), "
                + "guardian_phone = VALUES(guardian_phone), "
                + "guardian_cost_yn = VALUES(guardian_cost_yn), "
                + "sub_guardian_name = VALUES(sub_guardian_name), "
                + "sub_guardian_relation = VALUES(sub_guardian_relation), "
                + "sub_guardian_addr = VALUES(sub_guardian_addr), "
                + "sub_guardian_phone = VALUES(sub_guardian_phone), "
                + "sub_guardian_cost_yn = VALUES(sub_guardian_cost_yn), "
                + "signed_at = VALUES(signed_at), "
                + "pledge_text = VALUES(pledge_text), "
                + "signature_data = VALUES(signature_data), "
                + "page_ink_data = VALUES(page_ink_data)";
        try {
            return jdbcTemplate.update(sql, csIdx, agreedYn, signerName, signerRelation,
                    guardianName, guardianRelation, guardianAddr, guardianPhone, guardianCostYn,
                    subGuardianName, subGuardianRelation, subGuardianAddr, subGuardianPhone, subGuardianCostYn,
                    signedAt, pledgeText, signatureData, pageInkData);
        } catch (Exception e) {
            log.warn("[admission-pledge] upsert fail inst={}, cs_idx={}, err={}", safe, csIdx, e.toString());
            return 0;
        }
    }

    public int deleteAdmissionPledge(String inst, int csIdx) {
        if (csIdx <= 0) {
            return 0;
        }
        String safe = sanitizeInst(inst);
        ensureAdmissionPledgeTable(safe);
        String sql = "DELETE FROM csm.counsel_admission_pledge_" + safe + " WHERE cs_idx = ?";
        try {
            return jdbcTemplate.update(sql, csIdx);
        } catch (Exception e) {
            log.warn("[admission-pledge] delete fail inst={}, cs_idx={}, err={}", safe, csIdx, e.toString());
            return 0;
        }
    }

    public List<CounselReservation> listCounselReservations(String inst, String status, int limit) {
        String safe = sanitizeInst(inst);
        ensureCounselReservationTable(safe);
        int limitedRows = limit <= 0 ? 300 : Math.min(limit, 2000);
        String normalizedStatus = normalizeReservationStatus(status, true);

        StringBuilder sql = new StringBuilder()
                .append("SELECT id, patient_name, patient_phone, guardian_name, call_summary, priority, ")
                .append("DATE_FORMAT(reserved_at, '%Y-%m-%d %H:%i:%s') AS reserved_at, ")
                .append("status, linked_cs_idx, created_by, completed_by, ")
                .append("DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s') AS completed_at, ")
                .append("DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, ")
                .append("DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at, ")
                .append("DATE_FORMAT(opened_at, '%Y-%m-%d %H:%i:%s') AS opened_at ")
                .append("FROM csm.counsel_reservation_").append(safe).append(" ");
        List<Object> params = new ArrayList<>();
        if (!"ALL".equals(normalizedStatus)) {
            sql.append("WHERE status = ? ");
            params.add(normalizedStatus);
        }
        sql.append("ORDER BY CASE status WHEN 'RESERVED' THEN 0 WHEN 'COMPLETED' THEN 1 ELSE 2 END, ")
                .append("CASE WHEN priority IS NULL THEN 99 ELSE priority END ASC, ")
                .append("CASE WHEN reserved_at IS NULL THEN 1 ELSE 0 END ASC, reserved_at ASC, id DESC ")
                .append("LIMIT ?");
        params.add(limitedRows);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<CounselReservation> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(mapCounselReservationRow(row));
        }
        return out;
    }

    public CounselReservation getCounselReservationById(String inst, long reservationId) {
        if (reservationId <= 0) {
            return null;
        }
        String safe = sanitizeInst(inst);
        ensureCounselReservationTable(safe);
        String sql = "SELECT id, patient_name, patient_phone, guardian_name, call_summary, priority, "
                + "DATE_FORMAT(reserved_at, '%Y-%m-%d %H:%i:%s') AS reserved_at, "
                + "status, linked_cs_idx, created_by, completed_by, "
                + "DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s') AS completed_at, "
                + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, "
                + "DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at "
                + "FROM csm.counsel_reservation_" + safe + " WHERE id = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, reservationId);
        if (rows.isEmpty()) {
            return null;
        }
        return mapCounselReservationRow(rows.get(0));
    }

    public CounselReservation getCounselReservationByLinkedCsIdx(String inst, int csIdx) {
        if (csIdx <= 0) {
            return null;
        }
        String safe = sanitizeInst(inst);
        ensureCounselReservationTable(safe);
        String sql = "SELECT id, patient_name, patient_phone, guardian_name, call_summary, priority, "
                + "DATE_FORMAT(reserved_at, '%Y-%m-%d %H:%i:%s') AS reserved_at, "
                + "status, linked_cs_idx, created_by, completed_by, "
                + "DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s') AS completed_at, "
                + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, "
                + "DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at "
                + "FROM csm.counsel_reservation_" + safe + " WHERE linked_cs_idx = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, csIdx);
        if (rows.isEmpty()) {
            return null;
        }
        return mapCounselReservationRow(rows.get(0));
    }

    public long saveCounselReservation(String inst, CounselReservation reservation) {
        if (reservation == null) {
            return 0L;
        }
        String safe = sanitizeInst(inst);
        ensureCounselReservationTable(safe);

        String patientName = safeText(reservation.getPatient_name(), 100);
        if (patientName.isBlank()) {
            throw new IllegalArgumentException("환자명은 필수입니다.");
        }
        String patientPhone = safeText(reservation.getPatient_phone(), 50);
        String guardianName = safeText(reservation.getGuardian_name(), 100);
        String callSummary = safeText(reservation.getCall_summary(), 3000);
        int priority = normalizeReservationPriority(reservation.getPriority());
        String status = normalizeReservationStatus(reservation.getStatus(), false);
        Timestamp reservedAt = parseReservationTimestamp(reservation.getReserved_at());
        String createdBy = safeText(reservation.getCreated_by(), 100);

        Long id = reservation.getId();
        if (id == null || id <= 0) {
            String sql = "INSERT INTO csm.counsel_reservation_" + safe + " ("
                    + "patient_name, patient_phone, guardian_name, call_summary, priority, reserved_at, status, created_by"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                int idx = 1;
                ps.setString(idx++, patientName);
                ps.setString(idx++, patientPhone);
                ps.setString(idx++, guardianName);
                ps.setString(idx++, callSummary);
                ps.setInt(idx++, priority);
                ps.setTimestamp(idx++, reservedAt);
                ps.setString(idx++, status);
                ps.setString(idx++, createdBy);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return key == null ? 0L : key.longValue();
        }

        jdbcTemplate.update(
                "UPDATE csm.counsel_reservation_" + safe + " "
                        + "SET patient_name = ?, patient_phone = ?, guardian_name = ?, call_summary = ?, priority = ?, reserved_at = ?, status = ?, "
                        + "created_by = COALESCE(NULLIF(?, ''), created_by) "
                        + "WHERE id = ?",
                patientName,
                patientPhone,
                guardianName,
                callSummary,
                priority,
                reservedAt,
                status,
                createdBy,
                id);
        return id;
    }

    public int updateCounselReservationStatus(String inst, long reservationId, String status, String actor) {
        if (reservationId <= 0) {
            return 0;
        }
        String safe = sanitizeInst(inst);
        ensureCounselReservationTable(safe);
        String normalizedStatus = normalizeReservationStatus(status, false);
        String actorName = safeText(actor, 100);

        if ("COMPLETED".equals(normalizedStatus)) {
            return jdbcTemplate.update(
                    "UPDATE csm.counsel_reservation_" + safe + " "
                            + "SET status = ?, completed_at = NOW(), completed_by = COALESCE(NULLIF(?, ''), completed_by) "
                            + "WHERE id = ?",
                    normalizedStatus,
                    actorName,
                    reservationId);
        }
        if ("CANCELLED".equals(normalizedStatus)) {
            return jdbcTemplate.update(
                    "UPDATE csm.counsel_reservation_" + safe + " "
                            + "SET status = ?, completed_at = NOW(), completed_by = COALESCE(NULLIF(?, ''), completed_by) "
                            + "WHERE id = ?",
                    normalizedStatus,
                    actorName,
                    reservationId);
        }
        return jdbcTemplate.update(
                "UPDATE csm.counsel_reservation_" + safe + " "
                        + "SET status = ?, completed_at = NULL, completed_by = NULL "
                        + "WHERE id = ?",
                normalizedStatus,
                reservationId);
    }

    public int linkReservationToCounsel(String inst, long reservationId, int csIdx, String actor) {
        if (reservationId <= 0 || csIdx <= 0) {
            return 0;
        }
        String safe = sanitizeInst(inst);
        ensureCounselReservationTable(safe);
        String actorName = safeText(actor, 100);
        return jdbcTemplate.update(
                "UPDATE csm.counsel_reservation_" + safe + " "
                        + "SET linked_cs_idx = ?, status = 'COMPLETED', completed_at = NOW(), completed_by = COALESCE(NULLIF(?, ''), completed_by) "
                        + "WHERE id = ?",
                csIdx,
                actorName,
                reservationId);
    }

    private void ensureCounselReservationTable(String safeInst) {
        String tableName = "counsel_reservation_" + safeInst;
        String sql = "CREATE TABLE IF NOT EXISTS csm." + tableName + " ("
                + "id bigint auto_increment primary key,"
                + "patient_name varchar(100) not null,"
                + "patient_phone varchar(50) default null,"
                + "guardian_name varchar(100) default null,"
                + "call_summary text,"
                + "priority int not null default 3,"
                + "reserved_at datetime default null,"
                + "status varchar(20) not null default 'RESERVED',"
                + "linked_cs_idx int default null,"
                + "created_by varchar(100) default null,"
                + "completed_by varchar(100) default null,"
                + "completed_at datetime default null,"
                + "created_at timestamp default current_timestamp,"
                + "updated_at timestamp default current_timestamp on update current_timestamp,"
                + "key idx_status_priority_reserved (status, priority, reserved_at),"
                + "key idx_linked_cs_idx (linked_cs_idx)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        jdbcTemplate.execute(sql);
        ensureTableColumn("csm", tableName, "status",
                "ALTER TABLE csm." + tableName + " ADD COLUMN status varchar(20) not null default 'RESERVED'");
        ensureTableColumn("csm", tableName, "linked_cs_idx",
                "ALTER TABLE csm." + tableName + " ADD COLUMN linked_cs_idx int default null");
        ensureTableColumn("csm", tableName, "completed_by",
                "ALTER TABLE csm." + tableName + " ADD COLUMN completed_by varchar(100) default null");
        ensureTableColumn("csm", tableName, "completed_at",
                "ALTER TABLE csm." + tableName + " ADD COLUMN completed_at datetime default null");
        ensureTableColumn("csm", tableName, "opened_at",
                "ALTER TABLE csm." + tableName + " ADD COLUMN opened_at datetime default null");
    }

    public void touchOpenedAt(String inst, long reservationId) {
        if (reservationId <= 0) {
            return;
        }
        String safe = sanitizeInst(inst);
        ensureCounselReservationTable(safe);
        try {
            jdbcTemplate.update(
                    "UPDATE csm.counsel_reservation_" + safe + " SET opened_at = NOW() WHERE id = ?",
                    reservationId);
        } catch (Exception e) {
            log.warn("[reservation] touchOpenedAt fail inst={}, id={}, err={}", safe, reservationId, e.toString());
        }
    }

    private CounselReservation mapCounselReservationRow(Map<String, Object> row) {
        CounselReservation reservation = new CounselReservation();
        reservation.setId(row.get("id") == null ? null : ((Number) row.get("id")).longValue());
        reservation.setPatient_name(safeText(row.get("patient_name"), 100));
        reservation.setPatient_phone(safeText(row.get("patient_phone"), 50));
        reservation.setGuardian_name(safeText(row.get("guardian_name"), 100));
        reservation.setCall_summary(safeText(row.get("call_summary"), 3000));
        reservation.setPriority(normalizeReservationPriority(row.get("priority")));
        reservation.setReserved_at(safeText(row.get("reserved_at"), 19));
        reservation.setStatus(normalizeReservationStatus(row.get("status"), false));
        reservation.setLinked_cs_idx(row.get("linked_cs_idx") == null ? null : ((Number) row.get("linked_cs_idx")).intValue());
        reservation.setCreated_by(safeText(row.get("created_by"), 100));
        reservation.setCompleted_by(safeText(row.get("completed_by"), 100));
        reservation.setCompleted_at(safeText(row.get("completed_at"), 19));
        reservation.setCreated_at(safeText(row.get("created_at"), 19));
        reservation.setUpdated_at(safeText(row.get("updated_at"), 19));
        String openedAt = safeText(row.get("opened_at"), 19);
        reservation.setOpened_at(openedAt);
        if (!openedAt.isBlank()) {
            try {
                LocalDateTime openedTime = LocalDateTime.parse(openedAt,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                reservation.setBeingWorkedOn(
                        openedTime.isAfter(LocalDateTime.now().minusMinutes(60)));
            } catch (Exception ignored) {
                reservation.setBeingWorkedOn(false);
            }
        }
        return reservation;
    }

    private int normalizeReservationPriority(Object value) {
        int priority = parseInt(value, 3);
        if (priority < 1) {
            return 1;
        }
        if (priority > 5) {
            return 5;
        }
        return priority;
    }

    private String normalizeReservationStatus(Object value, boolean allowAll) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        String v = raw.toUpperCase();
        if (allowAll && (v.isBlank() || "ALL".equals(v))) {
            return "ALL";
        }
        if ("접수".equals(raw) || "예약".equals(raw) || "RESERVED".equals(v)) {
            return "RESERVED";
        }
        if ("입원상담연계".equals(raw) || "입원상담 연계".equals(raw)
                || "입원상담이관".equals(raw) || "입원상담 이관".equals(raw)
                || "완료".equals(raw) || "COMPLETED".equals(v)) {
            return "COMPLETED";
        }
        if ("취소".equals(raw) || "CANCELLED".equals(v) || "CANCELED".equals(v)) {
            return "CANCELLED";
        }
        return "RESERVED";
    }

    private Timestamp parseReservationTimestamp(String raw) {
        String value = safeText(raw, 19).replace('T', ' ').trim();
        if (value.isBlank()) {
            return null;
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDateTime dt = LocalDateTime.parse(value, formatter);
                return Timestamp.valueOf(dt);
            } catch (DateTimeParseException ignore) {
            }
        }
        return null;
    }

    public List<Map<String, Object>> coreNoticeList(String status, String keyword, int limit) {
        ensureCoreNoticeTables();
        int limitedRows = limit <= 0 ? 300 : Math.min(limit, 2000);
        String normalizedStatus = normalizeCoreNoticeStatus(status, true);
        String normalizedKeyword = safeText(keyword, 200);

        StringBuilder sql = new StringBuilder()
                .append("SELECT id, title, body, priority, pinned_yn, popup_yn, status, ")
                .append("DATE_FORMAT(start_at, '%Y-%m-%d %H:%i:%s') AS start_at, ")
                .append("DATE_FORMAT(end_at, '%Y-%m-%d %H:%i:%s') AS end_at, ")
                .append("DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, ")
                .append("DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at, ")
                .append("created_by ")
                .append("FROM csm.core_notice WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (!"ALL".equals(normalizedStatus)) {
            sql.append("AND status = ? ");
            params.add(normalizedStatus);
        }
        if (!normalizedKeyword.isBlank()) {
            sql.append("AND (title LIKE ? OR body LIKE ?) ");
            String like = "%" + normalizedKeyword + "%";
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY CASE status WHEN 'PUBLISHED' THEN 0 WHEN 'DRAFT' THEN 1 ELSE 2 END, ")
                .append("CASE WHEN pinned_yn = 'Y' THEN 0 ELSE 1 END, ")
                .append("priority ASC, id DESC ")
                .append("LIMIT ?");
        params.add(limitedRows);

        List<Map<String, Object>> notices = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        if (notices.isEmpty()) {
            return notices;
        }

        Map<String, String> instNameMap = new HashMap<>();
        for (Instdata inst : Optional.ofNullable(coreInstSelect()).orElse(Collections.emptyList())) {
            if (inst == null || inst.getId_col_03() == null) {
                continue;
            }
            instNameMap.put(inst.getId_col_03(), safeText(inst.getId_col_02(), 100));
        }

        List<Long> noticeIds = new ArrayList<>();
        for (Map<String, Object> notice : notices) {
            noticeIds.add(parseLong(notice.get("id"), 0L));
        }
        Map<Long, List<String>> targetMap = findCoreNoticeTargetCodes(noticeIds);
        for (Map<String, Object> notice : notices) {
            long noticeId = parseLong(notice.get("id"), 0L);
            List<String> codes = targetMap.getOrDefault(noticeId, Collections.emptyList());
            List<String> names = new ArrayList<>();
            for (String code : codes) {
                names.add(instNameMap.getOrDefault(code, code));
            }
            notice.put("status", normalizeCoreNoticeStatus(notice.get("status"), false));
            notice.put("pinned_yn", normalizeYn(notice.get("pinned_yn"), "N"));
            notice.put("popup_yn", normalizeYn(notice.get("popup_yn"), "N"));
            notice.put("target_inst_codes", codes);
            notice.put("target_codes_csv", String.join(",", codes));
            notice.put("target_names_csv", String.join(", ", names));
            notice.put("target_count", codes.size());
        }
        return notices;
    }

    public Map<String, Object> coreNoticeById(long noticeId) {
        ensureCoreNoticeTables();
        if (noticeId <= 0) {
            return null;
        }
        String sql = "SELECT id, title, body, priority, pinned_yn, popup_yn, status, "
                + "DATE_FORMAT(start_at, '%Y-%m-%d %H:%i:%s') AS start_at, "
                + "DATE_FORMAT(end_at, '%Y-%m-%d %H:%i:%s') AS end_at, "
                + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, "
                + "DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at, "
                + "created_by "
                + "FROM csm.core_notice WHERE id = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, noticeId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> notice = rows.get(0);
        List<String> codes = findCoreNoticeTargetCodes(List.of(noticeId)).getOrDefault(noticeId, Collections.emptyList());
        notice.put("status", normalizeCoreNoticeStatus(notice.get("status"), false));
        notice.put("pinned_yn", normalizeYn(notice.get("pinned_yn"), "N"));
        notice.put("popup_yn", normalizeYn(notice.get("popup_yn"), "N"));
        notice.put("target_inst_codes", codes);
        notice.put("target_codes_csv", String.join(",", codes));
        return notice;
    }

    @Transactional
    public long coreNoticeSave(Map<String, Object> payload, List<String> targetInstCodes) {
        ensureCoreNoticeTables();
        String title = safeText(payload == null ? null : payload.get("title"), 200);
        if (title.isBlank()) {
            throw new IllegalArgumentException("공지 제목은 필수입니다.");
        }
        String body = safeText(payload == null ? null : payload.get("body"), 20000);
        int priority = parseInt(payload == null ? null : payload.get("priority"), 3);
        if (priority < 0) {
            priority = 0;
        }
        if (priority > 99) {
            priority = 99;
        }
        String pinnedYn = normalizeYn(payload == null ? null : payload.get("pinnedYn"), "N");
        String popupYn = normalizeYn(payload == null ? null : payload.get("popupYn"), "N");
        String status = normalizeCoreNoticeStatus(payload == null ? null : payload.get("status"), false);
        Timestamp startAt = parseCoreNoticeTimestamp(payload == null ? null : payload.get("startAt"));
        Timestamp endAt = parseCoreNoticeTimestamp(payload == null ? null : payload.get("endAt"));
        if (startAt != null && endAt != null && startAt.after(endAt)) {
            throw new IllegalArgumentException("게시 시작일은 종료일보다 늦을 수 없습니다.");
        }
        String createdBy = safeText(payload == null ? null : payload.get("createdBy"), 100);

        List<String> normalizedTargets = normalizeCoreNoticeTargets(targetInstCodes);
        if (normalizedTargets.isEmpty()) {
            throw new IllegalArgumentException("대상 기관을 선택해 주세요.");
        }

        long noticeId = parseLong(payload == null ? null : payload.get("id"), 0L);
        if (noticeId <= 0) {
            String sql = "INSERT INTO csm.core_notice ("
                    + "title, body, priority, pinned_yn, popup_yn, start_at, end_at, status, created_by"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int finalPriority = priority;
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                int idx = 1;
                ps.setString(idx++, title);
                ps.setString(idx++, body);
                ps.setInt(idx++, finalPriority);
                ps.setString(idx++, pinnedYn);
                ps.setString(idx++, popupYn);
                ps.setTimestamp(idx++, startAt);
                ps.setTimestamp(idx++, endAt);
                ps.setString(idx++, status);
                ps.setString(idx++, createdBy);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            noticeId = key == null ? 0L : key.longValue();
        } else {
            jdbcTemplate.update(
                    "UPDATE csm.core_notice SET title = ?, body = ?, priority = ?, pinned_yn = ?, popup_yn = ?, "
                            + "start_at = ?, end_at = ?, status = ? WHERE id = ?",
                    title, body, priority, pinnedYn, popupYn, startAt, endAt, status, noticeId);
        }

        if (noticeId <= 0) {
            throw new IllegalStateException("공지 저장에 실패했습니다.");
        }

        jdbcTemplate.update("DELETE FROM csm.core_notice_target_inst WHERE notice_id = ?", noticeId);
        String insertTargetSql = "INSERT INTO csm.core_notice_target_inst (notice_id, inst_code) VALUES (?, ?)";
        for (String code : normalizedTargets) {
            jdbcTemplate.update(insertTargetSql, noticeId, code);
        }
        return noticeId;
    }

    public int coreNoticeUpdateStatus(long noticeId, Object status) {
        ensureCoreNoticeTables();
        if (noticeId <= 0) {
            return 0;
        }
        String normalized = normalizeCoreNoticeStatus(status, false);
        return jdbcTemplate.update("UPDATE csm.core_notice SET status = ? WHERE id = ?", normalized, noticeId);
    }

    public List<Map<String, Object>> listInstitutionNotices(String inst, String userId, int limit) {
        ensureCoreNoticeTables();
        String safeInst = sanitizeInst(inst);
        String safeUserId = safeText(userId, 100);
        if (safeUserId.isBlank()) {
            return Collections.emptyList();
        }
        int limitedRows = limit <= 0 ? 200 : Math.min(limit, 2000);
        String sql = "SELECT n.id, n.title, n.body, n.priority, n.pinned_yn, n.popup_yn, n.status, "
                + "DATE_FORMAT(n.start_at, '%Y-%m-%d %H:%i:%s') AS start_at, "
                + "DATE_FORMAT(n.end_at, '%Y-%m-%d %H:%i:%s') AS end_at, "
                + "DATE_FORMAT(n.created_at, '%Y-%m-%d %H:%i:%s') AS created_at, "
                + "CASE WHEN r.id IS NULL THEN 'N' ELSE 'Y' END AS read_yn, "
                + "DATE_FORMAT(r.read_at, '%Y-%m-%d %H:%i:%s') AS read_at "
                + "FROM csm.core_notice n "
                + "JOIN csm.core_notice_target_inst t ON t.notice_id = n.id AND t.inst_code = ? "
                + "LEFT JOIN csm.core_notice_read r ON r.notice_id = n.id AND r.inst_code = ? AND r.user_id = ? "
                + "WHERE n.status = 'PUBLISHED' "
                + "AND (n.start_at IS NULL OR n.start_at <= NOW()) "
                + "AND (n.end_at IS NULL OR n.end_at >= NOW()) "
                + "ORDER BY CASE WHEN n.pinned_yn = 'Y' THEN 0 ELSE 1 END, n.priority ASC, n.id DESC "
                + "LIMIT ?";
        List<Map<String, Object>> notices = jdbcTemplate.queryForList(sql, safeInst, safeInst, safeUserId, limitedRows);
        for (Map<String, Object> notice : notices) {
            notice.put("status", normalizeCoreNoticeStatus(notice.get("status"), false));
            notice.put("pinned_yn", normalizeYn(notice.get("pinned_yn"), "N"));
            notice.put("popup_yn", normalizeYn(notice.get("popup_yn"), "N"));
            notice.put("read_yn", normalizeYn(notice.get("read_yn"), "N"));
        }
        return notices;
    }

    public int markInstitutionNoticeRead(String inst, String userId, Integer userIdx, long noticeId) {
        ensureCoreNoticeTables();
        if (noticeId <= 0) {
            return 0;
        }
        String safeInst = sanitizeInst(inst);
        String safeUserId = safeText(userId, 100);
        if (safeUserId.isBlank()) {
            return 0;
        }
        Integer normalizedUserIdx = userIdx != null && userIdx > 0 ? userIdx : null;
        String sql = "INSERT INTO csm.core_notice_read (notice_id, inst_code, user_idx, user_id, read_at) "
                + "VALUES (?, ?, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE read_at = VALUES(read_at), user_idx = VALUES(user_idx)";
        return jdbcTemplate.update(sql, noticeId, safeInst, normalizedUserIdx, safeUserId);
    }

    public int countInstitutionUnreadNotices(String inst, String userId) {
        ensureCoreNoticeTables();
        String safeInst = sanitizeInst(inst);
        String safeUserId = safeText(userId, 100);
        if (safeUserId.isBlank()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM csm.core_notice n "
                + "JOIN csm.core_notice_target_inst t ON t.notice_id = n.id AND t.inst_code = ? "
                + "LEFT JOIN csm.core_notice_read r ON r.notice_id = n.id AND r.inst_code = ? AND r.user_id = ? "
                + "WHERE n.status = 'PUBLISHED' "
                + "AND (n.start_at IS NULL OR n.start_at <= NOW()) "
                + "AND (n.end_at IS NULL OR n.end_at >= NOW()) "
                + "AND r.id IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, safeInst, safeInst, safeUserId);
        return count == null ? 0 : count;
    }

    private Map<Long, List<String>> findCoreNoticeTargetCodes(List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        StringBuilder placeholders = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Long id : noticeIds) {
            long noticeId = id == null ? 0L : id;
            if (noticeId <= 0) {
                continue;
            }
            if (placeholders.length() > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
            params.add(noticeId);
        }
        if (placeholders.length() == 0) {
            return Collections.emptyMap();
        }
        String sql = "SELECT notice_id, inst_code FROM csm.core_notice_target_inst "
                + "WHERE notice_id IN (" + placeholders + ") ORDER BY id ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<Long, List<String>> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long noticeId = parseLong(row.get("notice_id"), 0L);
            String code = safeText(row.get("inst_code"), 32);
            if (noticeId <= 0 || code.isBlank()) {
                continue;
            }
            out.computeIfAbsent(noticeId, k -> new ArrayList<>()).add(code);
        }
        return out;
    }

    private List<String> normalizeCoreNoticeTargets(List<String> targetInstCodes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        boolean allSelected = false;
        for (String raw : targetInstCodes == null ? Collections.<String>emptyList() : targetInstCodes) {
            String value = safeText(raw, 40);
            if (value.isBlank()) {
                continue;
            }
            if ("ALL".equalsIgnoreCase(value)) {
                allSelected = true;
                continue;
            }
            try {
                String code = sanitizeInst(value);
                if (!"core".equalsIgnoreCase(code)) {
                    normalized.add(code);
                }
            } catch (Exception ignore) {
            }
        }
        if (allSelected || normalized.isEmpty()) {
            for (Instdata inst : Optional.ofNullable(coreInstSelect()).orElse(Collections.emptyList())) {
                if (inst == null || inst.getId_col_03() == null) {
                    continue;
                }
                String code = inst.getId_col_03().trim();
                if (code.isEmpty() || "core".equalsIgnoreCase(code)) {
                    continue;
                }
                String useYn = safeText(inst.getId_col_04(), 1);
                if (!useYn.isBlank() && !"Y".equalsIgnoreCase(useYn)) {
                    continue;
                }
                try {
                    normalized.add(sanitizeInst(code));
                } catch (Exception ignore) {
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeCoreNoticeStatus(Object value, boolean allowAll) {
        String v = value == null ? "" : String.valueOf(value).trim().toUpperCase();
        if (allowAll && (v.isBlank() || "ALL".equals(v) || "전체".equals(String.valueOf(value).trim()))) {
            return "ALL";
        }
        if ("PUBLISHED".equals(v) || "게시".equals(String.valueOf(value).trim())) {
            return "PUBLISHED";
        }
        if ("ARCHIVED".equals(v) || "보관".equals(String.valueOf(value).trim())) {
            return "ARCHIVED";
        }
        return "DRAFT";
    }

    private String normalizeYn(Object value, String defaultValue) {
        String v = value == null ? "" : String.valueOf(value).trim().toUpperCase();
        if ("Y".equals(v) || "1".equals(v) || "TRUE".equals(v)) {
            return "Y";
        }
        if ("N".equals(v) || "0".equals(v) || "FALSE".equals(v)) {
            return "N";
        }
        return "Y".equalsIgnoreCase(defaultValue) ? "Y" : "N";
    }

    private Timestamp parseCoreNoticeTimestamp(Object raw) {
        String value = safeText(raw, 25).replace('T', ' ').trim();
        if (value.isBlank()) {
            return null;
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDateTime dt = LocalDateTime.parse(value, formatter);
                return Timestamp.valueOf(dt);
            } catch (DateTimeParseException ignore) {
            }
        }
        return null;
    }

    private void ensureCoreNoticeTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS csm.core_notice ("
                + "id bigint auto_increment primary key,"
                + "title varchar(200) not null,"
                + "body text,"
                + "priority int not null default 3,"
                + "pinned_yn char(1) not null default 'N',"
                + "popup_yn char(1) not null default 'N',"
                + "start_at datetime default null,"
                + "end_at datetime default null,"
                + "status varchar(20) not null default 'DRAFT',"
                + "created_by varchar(100) default null,"
                + "created_at timestamp default current_timestamp,"
                + "updated_at timestamp default current_timestamp on update current_timestamp,"
                + "key idx_status_window (status, start_at, end_at),"
                + "key idx_pinned_priority (pinned_yn, priority)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS csm.core_notice_target_inst ("
                + "id bigint auto_increment primary key,"
                + "notice_id bigint not null,"
                + "inst_code varchar(32) not null,"
                + "created_at timestamp default current_timestamp,"
                + "unique key uq_notice_inst (notice_id, inst_code),"
                + "key idx_inst_notice (inst_code, notice_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS csm.core_notice_read ("
                + "id bigint auto_increment primary key,"
                + "notice_id bigint not null,"
                + "inst_code varchar(32) not null,"
                + "user_idx int default null,"
                + "user_id varchar(100) not null,"
                + "read_at datetime not null default current_timestamp,"
                + "unique key uq_notice_inst_user (notice_id, inst_code, user_id),"
                + "key idx_inst_user_read (inst_code, user_id, notice_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        ensureTableColumn("csm", "core_notice", "popup_yn",
                "ALTER TABLE csm.core_notice ADD COLUMN popup_yn char(1) not null default 'N'");
        ensureTableColumn("csm", "core_notice", "pinned_yn",
                "ALTER TABLE csm.core_notice ADD COLUMN pinned_yn char(1) not null default 'N'");
        ensureTableColumn("csm", "core_notice", "status",
                "ALTER TABLE csm.core_notice ADD COLUMN status varchar(20) not null default 'DRAFT'");
        ensureTableColumn("csm", "core_notice_target_inst", "inst_code",
                "ALTER TABLE csm.core_notice_target_inst ADD COLUMN inst_code varchar(32) not null");
        ensureTableColumn("csm", "core_notice_read", "user_id",
                "ALTER TABLE csm.core_notice_read ADD COLUMN user_id varchar(100) not null default ''");
    }

    private void ensureAdmissionPledgeTable(String safeInst) {
        String sql = "CREATE TABLE IF NOT EXISTS csm.counsel_admission_pledge_" + safeInst + " ("
                + "id bigint auto_increment primary key,"
                + "cs_idx int not null,"
                + "agreed_yn char(1) not null default 'N',"
                + "signer_name varchar(100) default null,"
                + "signer_relation varchar(50) default null,"
                + "guardian_name varchar(100) default null,"
                + "guardian_relation varchar(50) default null,"
                + "guardian_addr varchar(255) default null,"
                + "guardian_phone varchar(50) default null,"
                + "guardian_cost_yn char(1) not null default 'N',"
                + "sub_guardian_name varchar(100) default null,"
                + "sub_guardian_relation varchar(50) default null,"
                + "sub_guardian_addr varchar(255) default null,"
                + "sub_guardian_phone varchar(50) default null,"
                + "sub_guardian_cost_yn char(1) not null default 'N',"
                + "signed_at varchar(19) default null,"
                + "pledge_text text,"
                + "signature_data longtext,"
                + "page_ink_data longtext,"
                + "created_at timestamp default current_timestamp,"
                + "updated_at timestamp default current_timestamp on update current_timestamp,"
                + "unique key uq_cs_idx (cs_idx)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try {
            jdbcTemplate.execute(sql);
            ensureAdmissionPledgeColumns(safeInst);
        } catch (Exception e) {
            log.warn("[admission-pledge] ensure table fail inst={}, err={}", safeInst, e.toString());
        }
    }

    private void ensureAdmissionPledgeColumns(String safeInst) {
        ensureAdmissionPledgeColumn(
                safeInst,
                "page_ink_data",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN page_ink_data longtext");
        ensureAdmissionPledgeColumn(
                safeInst,
                "guardian_name",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN guardian_name varchar(100) default null");
        ensureAdmissionPledgeColumn(
                safeInst,
                "guardian_relation",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN guardian_relation varchar(50) default null");
        ensureAdmissionPledgeColumn(
                safeInst,
                "guardian_addr",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN guardian_addr varchar(255) default null");
        ensureAdmissionPledgeColumn(
                safeInst,
                "guardian_phone",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN guardian_phone varchar(50) default null");
        ensureAdmissionPledgeColumn(
                safeInst,
                "guardian_cost_yn",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN guardian_cost_yn char(1) not null default 'N'");
        ensureAdmissionPledgeColumn(
                safeInst,
                "sub_guardian_name",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN sub_guardian_name varchar(100) default null");
        ensureAdmissionPledgeColumn(
                safeInst,
                "sub_guardian_relation",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN sub_guardian_relation varchar(50) default null");
        ensureAdmissionPledgeColumn(
                safeInst,
                "sub_guardian_addr",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN sub_guardian_addr varchar(255) default null");
        ensureAdmissionPledgeColumn(
                safeInst,
                "sub_guardian_phone",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN sub_guardian_phone varchar(50) default null");
        ensureAdmissionPledgeColumn(
                safeInst,
                "sub_guardian_cost_yn",
                "ALTER TABLE csm.counsel_admission_pledge_" + safeInst + " ADD COLUMN sub_guardian_cost_yn char(1) not null default 'N'");
    }

    private void ensureAdmissionPledgeColumn(String safeInst, String columnName, String alterSql) {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        String tableName = "counsel_admission_pledge_" + safeInst;
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, columnName);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute(alterSql);
        } catch (Exception e) {
            log.warn("[admission-pledge] ensure column fail inst={}, col={}, err={}", safeInst, columnName,
                    e.toString());
        }
    }

    private String normalizeYn(Object value) {
        String v = value == null ? "" : String.valueOf(value).trim();
        return ("Y".equalsIgnoreCase(v) || "1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v))
                ? "Y"
                : "N";
    }

    private String normalizeAdmissionSignatureData(String raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isBlank()) {
            return "";
        }
        if (isValidPngData(text, 1_500_000)) {
            return text;
        }
        if (text.length() > 4_000_000) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root == null || !root.isObject()) {
                return "";
            }

            String primary = extractFirstSignature(root, "primary", "main", "signer", "final_signature", "signature");
            if (primary.isBlank()) {
                return "";
            }
            String guardian = extractFirstSignature(root, "guardian", "guardian_signature", "primary_guardian");
            String subGuardian = extractFirstSignature(root, "sub_guardian", "subGuardian", "sub_guardian_signature",
                    "secondary_guardian");

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("version", 2);
            out.put("primary", primary);
            if (!guardian.isBlank()) {
                out.put("guardian", guardian);
            }
            if (!subGuardian.isBlank()) {
                out.put("sub_guardian", subGuardian);
            }

            String normalized = objectMapper.writeValueAsString(out);
            return normalized.length() <= 4_000_000 ? normalized : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String extractFirstSignature(JsonNode root, String... keys) {
        if (root == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty() || !root.has(key)) {
                continue;
            }
            String value = root.path(key).asText("");
            if (value != null) {
                value = value.trim();
            }
            if (isValidPngData(value, 1_500_000)) {
                return value;
            }
        }
        return "";
    }

    private String normalizePngData(String raw, int maxLen) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isBlank()) {
            return "";
        }
        return isValidPngData(text, maxLen) ? text : "";
    }

    private boolean isValidPngData(String raw, int maxLen) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isBlank()) {
            return false;
        }
        if (!text.startsWith("data:image/png;base64,")) {
            return false;
        }
        return text.length() <= maxLen;
    }

    private String safeText(Object value, int maxLen) {
        String out = value == null ? "" : String.valueOf(value).trim();
        if (out.length() <= maxLen) {
            return out;
        }
        return out.substring(0, maxLen);
    }

    public List<CounselDataEntry> getEntriesByInstAndCsIdx(String inst, int csIdx) {
        log.debug("[CsmAuthService] getEntriesByInstAndCsIdx inst={}, csIdx={}", inst, csIdx); // ★
        return cs.getEntriesByInstAndCsIdx(inst, csIdx);
    }

    public List<CounselLog> getCounselLog(String inst, int csIdx) {
        return cs.getCounselLog(inst, csIdx);
    }

    public CounselLog getCounselLogByIdx(String inst, int logIdx) {
        return cs.getCounselLogByIdx(inst, logIdx);
    }

    public List<CounselLogGuardian> getCounselLogGuardian(String inst, int csIdx) {
        return cs.getCounselLogGuardian(inst, csIdx);
    }

    public List<Map<String, Object>> getMonthlyCounselStatistics(Map<String, Object> params) {
        return cs.getMonthlyCounselStatistics(params);
    }

    public Map<String, Object> getCounselDateRange(Map<String, Object> params) {
        return cs.getCounselDateRange(params);
    }

    public List<Map<String, Object>> getTypeStatistics(Map<String, Object> params) {
        return cs.getTypeStatistics(params);
    }

    public List<Map<String, Object>> selectAdmissionSuccessStats(Map<String, Object> params) {
        return cs.selectAdmissionSuccessStats(params);
    }

    public List<Map<String, Object>> selectAdmissionTypeStats(Map<String, Object> params) {
        return cs.selectAdmissionTypeStats(params);
    }

    public List<Map<String, Object>> selectAdmissionTypeSuccessStats(Map<String, Object> params) {
        return cs.selectAdmissionTypeSuccessStats(params);
    }

    public List<Map<String, Object>> getCurrentLocationStats(Map<String, Object> params) {
        return cs.getCurrentLocationStats(params);
    }

    public List<Map<String, Object>> getCurrentLocationSuccessStats(Map<String, Object> params) {
        return cs.getCurrentLocationSuccessStats(params);
    }

    public List<Map<String, Object>> getNonAdmissionReasonStats(Map<String, Object> params) {
        return cs.getNonAdmissionReasonStats(params);
    }

    public boolean findUserByIdAndName(String usCol02, String usCol12, String inst) {
        String safe = sanitizeInst(inst);
        String sql = "SELECT COUNT(*) FROM csm.user_data_" + safe + " WHERE us_col_02 = ? AND us_col_12 = ?";
        Integer cnt = jdbcTemplate.queryForObject(sql, Integer.class, usCol02, usCol12);
        return cnt != null && cnt > 0;
    }

    public Userdata userInfoById(String inst, String userId) {
        String safe = sanitizeInst(inst);
        String sql = "SELECT us_col_01, us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, us_col_07, us_col_08, "
                + "us_col_09, us_col_10, us_col_11, us_col_12, us_col_13, us_col_14 "
                + "FROM csm.user_data_" + safe + " WHERE us_col_02 = ? LIMIT 1";
        List<Userdata> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Userdata u = new Userdata();
            u.setUs_col_01(rs.getInt("us_col_01"));
            u.setUs_col_02(rs.getString("us_col_02"));
            u.setUs_col_03(rs.getString("us_col_03"));
            u.setUs_col_04(rs.getString("us_col_04"));
            u.setUs_col_05(rs.getString("us_col_05"));
            u.setUs_col_06(rs.getString("us_col_06"));
            u.setUs_col_07(rs.getString("us_col_07"));
            u.setUs_col_08(rs.getInt("us_col_08"));
            u.setUs_col_09(rs.getInt("us_col_09"));
            u.setUs_col_10(rs.getString("us_col_10"));
            u.setUs_col_11(rs.getString("us_col_11"));
            u.setUs_col_12(rs.getString("us_col_12"));
            u.setUs_col_13(rs.getString("us_col_13"));
            u.setUs_col_14(rs.getString("us_col_14"));
            return u;
        }, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int updatePwd(String inst, int userIdx, String encryptedPassword) {
        String safe = sanitizeInst(inst);
        String sql = "UPDATE csm.user_data_" + safe + " SET us_col_03 = ? WHERE us_col_01 = ?";
        return jdbcTemplate.update(sql, encryptedPassword, userIdx);
    }

    public int insertCounselData(CounselData d) {
        String safe = sanitizeInst(d.getInst());
        String sql = "INSERT INTO csm.counsel_data_" + safe + " ("
                + "cs_col_01, cs_col_01_hash, cs_col_02, cs_col_03, cs_col_04, cs_col_05, cs_col_06, "
                + "cs_col_07, cs_col_08, cs_col_09, cs_col_10, cs_col_11, cs_col_12, cs_col_13, cs_col_14, cs_col_15, "
                + "cs_col_16, cs_col_17, cs_col_18, cs_col_19, cs_col_20, cs_col_21, cs_col_22, cs_col_23, cs_col_24, "
                + "cs_col_25, cs_col_26, cs_col_27, cs_col_28, cs_col_29, cs_col_30, cs_col_31, cs_col_32, cs_col_33, cs_col_34, "
                + "cs_col_35, cs_col_36, cs_col_37, cs_col_38, cs_col_39, cs_col_40, cs_col_41, cs_col_42"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setString(i++, d.getCs_col_01());
            ps.setBytes(i++, d.getCs_col_01_hash());
            ps.setString(i++, d.getCs_col_02());
            ps.setString(i++, d.getCs_col_03());
            ps.setString(i++, d.getCs_col_04());
            ps.setString(i++, d.getCs_col_05());
            ps.setString(i++, d.getCs_col_06());
            ps.setString(i++, d.getCs_col_07());
            ps.setString(i++, d.getCs_col_08());
            ps.setString(i++, d.getCs_col_09());
            ps.setString(i++, d.getCs_col_10());
            ps.setString(i++, d.getCs_col_11());
            ps.setString(i++, d.getCs_col_12());
            ps.setString(i++, d.getCs_col_13());
            ps.setString(i++, d.getCs_col_14());
            ps.setString(i++, d.getCs_col_15());
            ps.setString(i++, d.getCs_col_16());
            ps.setString(i++, d.getCs_col_17());
            ps.setString(i++, d.getCs_col_18());
            ps.setString(i++, d.getCs_col_19());
            ps.setString(i++, d.getCs_col_20());
            ps.setString(i++, d.getCs_col_21());
            ps.setString(i++, d.getCs_col_22());
            ps.setString(i++, d.getCs_col_23());
            ps.setString(i++, d.getCs_col_24());
            ps.setString(i++, d.getCs_col_25());
            ps.setString(i++, d.getCs_col_26());
            ps.setString(i++, d.getCs_col_27());
            ps.setString(i++, d.getCs_col_28());
            ps.setString(i++, d.getCs_col_29());
            ps.setString(i++, d.getCs_col_30());
            ps.setString(i++, d.getCs_col_31());
            ps.setString(i++, d.getCs_col_32());
            ps.setString(i++, d.getCs_col_33());
            ps.setString(i++, d.getCs_col_34());
            ps.setString(i++, d.getCs_col_35());
            ps.setString(i++, d.getCs_col_36());
            ps.setString(i++, d.getCs_col_37());
            ps.setString(i++, d.getCs_col_38());
            ps.setString(i++, d.getCs_col_39());
            ps.setString(i++, d.getCs_col_40());
            ps.setString(i++, d.getCs_col_41());
            ps.setString(i++, d.getCs_col_42());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.intValue();
    }

    public int updateCounselData(CounselData d) {
        String safe = sanitizeInst(d.getInst());
        String sql = "UPDATE csm.counsel_data_" + safe + " SET "
                + "cs_col_01=?, cs_col_01_hash=?, cs_col_02=?, cs_col_03=?, cs_col_04=?, cs_col_05=?, cs_col_06=?, "
                + "cs_col_07=?, cs_col_08=?, cs_col_09=?, cs_col_10=?, cs_col_11=?, cs_col_12=?, cs_col_13=?, cs_col_14=?, cs_col_15=?, "
                + "cs_col_16=?, cs_col_17=?, cs_col_18=?, cs_col_19=?, cs_col_20=?, cs_col_21=?, cs_col_22=?, cs_col_23=?, cs_col_24=?, "
                + "cs_col_25=?, cs_col_26=?, cs_col_27=?, cs_col_28=?, cs_col_29=?, cs_col_30=?, cs_col_31=?, cs_col_32=?, cs_col_33=?, cs_col_34=?, "
                + "cs_col_35=?, cs_col_36=?, cs_col_37=?, cs_col_38=?, cs_col_39=?, cs_col_40=?, cs_col_41=?, cs_col_42=?, updated_at=? "
                + "WHERE cs_idx=?";
        return jdbcTemplate.update(sql,
                d.getCs_col_01(), d.getCs_col_01_hash(), d.getCs_col_02(), d.getCs_col_03(), d.getCs_col_04(), d.getCs_col_05(),
                d.getCs_col_06(), d.getCs_col_07(), d.getCs_col_08(), d.getCs_col_09(), d.getCs_col_10(), d.getCs_col_11(),
                d.getCs_col_12(), d.getCs_col_13(), d.getCs_col_14(), d.getCs_col_15(), d.getCs_col_16(), d.getCs_col_17(),
                d.getCs_col_18(), d.getCs_col_19(), d.getCs_col_20(), d.getCs_col_21(), d.getCs_col_22(), d.getCs_col_23(),
                d.getCs_col_24(), d.getCs_col_25(), d.getCs_col_26(), d.getCs_col_27(), d.getCs_col_28(), d.getCs_col_29(),
                d.getCs_col_30(), d.getCs_col_31(), d.getCs_col_32(), d.getCs_col_33(), d.getCs_col_34(), d.getCs_col_35(),
                d.getCs_col_36(), d.getCs_col_37(), d.getCs_col_38(), d.getCs_col_39(), d.getCs_col_40(), d.getCs_col_41(),
                d.getCs_col_42(), Timestamp.valueOf(java.time.LocalDateTime.now()), d.getCs_idx());
    }

    public int appendCounselContentIfMissing(String inst, int csIdx, String transcript) {
        if (csIdx <= 0 || transcript == null) {
            return 0;
        }
        String normalized = transcript.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return 0;
        }

        String safe = sanitizeInst(inst);
        String sql = "UPDATE csm.counsel_data_" + safe + " SET "
                + "cs_col_32 = CASE "
                + "  WHEN cs_col_32 IS NULL OR TRIM(cs_col_32) = '' THEN ? "
                + "  WHEN LOCATE(?, cs_col_32) > 0 THEN cs_col_32 "
                + "  ELSE CONCAT(cs_col_32, '\\n', ?) "
                + "END "
                + "WHERE cs_idx = ?";

        return jdbcTemplate.update(sql, normalized, normalized, normalized, csIdx);
    }

    public int insertCounselDataEntry(String inst, CounselDataEntry entry) {
        String safe = sanitizeInst(inst);
        String sql = "INSERT INTO csm.counsel_data_" + safe + "_entries (cs_idx, category_id, subcategory_id, value, field_type) "
                + "VALUES (?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, entry.getCs_idx(), entry.getCategory_id(), entry.getSubcategory_id(),
                entry.getValue(), entry.getFieldType());
    }

    public int insertGuardian(String inst, Guardian guardian) {
        String safe = sanitizeInst(inst);
        String sql = "INSERT INTO csm.counsel_data_" + safe + "_guardians "
                + "(cs_idx, name, relationship, contact_number, name_hash, contact_number_hash) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, guardian.getCs_idx(), guardian.getName(), guardian.getRelationship(),
                guardian.getContact_number(), guardian.getName_hash(), guardian.getContact_number_hash());
    }

    public int insertCounselLog(String inst, CounselLog counselLog) {
        String safe = sanitizeInst(inst);
        String tableName = "counsel_log_" + safe;
        boolean hasCounselAt = hasColumn("csm", tableName, "counsel_at");
        String sql = hasCounselAt
                ? "INSERT INTO csm." + tableName + " ("
                        + "cs_idx, name, counsel_content, counsel_method, counsel_result, counsel_name, created_at, updated_at, counsel_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT INTO csm." + tableName + " ("
                        + "cs_idx, name, counsel_content, counsel_method, counsel_result, counsel_name, created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int idx = 1;
            ps.setInt(idx++, counselLog.getCs_idx());
            ps.setString(idx++, Optional.ofNullable(counselLog.getName()).orElse(""));
            ps.setString(idx++, Optional.ofNullable(counselLog.getCounsel_content()).orElse(""));
            ps.setString(idx++, Optional.ofNullable(counselLog.getCounsel_method()).orElse(""));
            ps.setString(idx++, Optional.ofNullable(counselLog.getCounsel_result()).orElse(""));
            ps.setString(idx++, Optional.ofNullable(counselLog.getCounsel_name()).orElse(""));
            ps.setString(idx++, now);
            ps.setString(idx++, now);
            if (hasCounselAt) {
                ps.setString(idx++, Optional.ofNullable(counselLog.getCounsel_at()).orElse(""));
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.intValue();
    }

    public int insertCounselLogGuardian(String inst, CounselLogGuardian guardian) {
        String safe = sanitizeInst(inst);
        String tableName = "counsel_log_guardians_" + safe;
        boolean hasCounselAt = hasColumn("csm", tableName, "counsel_at");
        String sql = hasCounselAt
                ? "INSERT INTO csm." + tableName + " ("
                        + "log_idx, counsel_guardian, counsel_relationship, counsel_number, counsel_at"
                        + ") VALUES (?, ?, ?, ?, ?)"
                : "INSERT INTO csm." + tableName + " ("
                        + "log_idx, counsel_guardian, counsel_relationship, counsel_number"
                        + ") VALUES (?, ?, ?, ?)";
        if (hasCounselAt) {
            return jdbcTemplate.update(sql, guardian.getLog_idx(), guardian.getCounsel_guardian(),
                    guardian.getCounsel_relationship(), guardian.getCounsel_number(), guardian.getCounsel_at());
        }
        return jdbcTemplate.update(sql, guardian.getLog_idx(), guardian.getCounsel_guardian(),
                guardian.getCounsel_relationship(), guardian.getCounsel_number());
    }

    private boolean hasColumn(String schemaName, String tableName, String columnName) {
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, schemaName, tableName, columnName);
        return count != null && count > 0;
    }

    private void ensureTableColumn(String schemaName, String tableName, String columnName, String alterSql) {
        try {
            if (hasColumn(schemaName, tableName, columnName)) {
                return;
            }
            jdbcTemplate.execute(alterSql);
        } catch (Exception e) {
            log.warn("[schema] ensure column fail table={}, col={}, err={}", tableName, columnName, e.toString());
        }
    }

    private void ensureCounselAudioTable(String inst) {
        String safe = sanitizeInst(inst);
        String tableName = "counsel_audio_" + safe;
        String ddl = "CREATE TABLE IF NOT EXISTS csm.counsel_audio_" + safe + " ("
                + "id bigint auto_increment primary key,"
                + "cs_idx int default null,"
                + "temp_key varchar(80) default null,"
                + "original_filename varchar(255) not null,"
                + "stored_filename varchar(255) not null,"
                + "mime_type varchar(100) default null,"
                + "file_size bigint default 0,"
                + "duration_seconds decimal(10,3) default null,"
                + "transcript longtext,"
                + "created_by varchar(100) default null,"
                + "created_at timestamp default current_timestamp,"
                + "updated_at timestamp default current_timestamp on update current_timestamp"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        jdbcTemplate.execute(ddl);
        ensureTableColumn("csm", tableName, "summary_text",
                "ALTER TABLE csm." + tableName + " ADD COLUMN summary_text longtext");
    }

    public long insertCounselAudio(
            String inst,
            Integer csIdx,
            String tempKey,
            String originalFilename,
            String storedFilename,
            String mimeType,
            long fileSize,
            Double durationSeconds,
            String transcript,
            String createdBy) {
        ensureCounselAudioTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "INSERT INTO csm.counsel_audio_" + safe + " ("
                + "cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, duration_seconds, transcript, created_by"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            if (csIdx == null || csIdx <= 0) {
                ps.setObject(1, null);
            } else {
                ps.setInt(1, csIdx);
            }
            ps.setString(2, Optional.ofNullable(tempKey).orElse(""));
            ps.setString(3, Optional.ofNullable(originalFilename).orElse(""));
            ps.setString(4, Optional.ofNullable(storedFilename).orElse(""));
            ps.setString(5, Optional.ofNullable(mimeType).orElse("application/octet-stream"));
            ps.setLong(6, Math.max(fileSize, 0L));
            if (durationSeconds == null || durationSeconds < 0) {
                ps.setObject(7, null);
            } else {
                ps.setDouble(7, durationSeconds);
            }
            ps.setString(8, Optional.ofNullable(transcript).orElse(""));
            ps.setString(9, Optional.ofNullable(createdBy).orElse(""));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public int bindCounselAudioByTempKey(String inst, String tempKey, int csIdx) {
        if (tempKey == null || tempKey.isBlank() || csIdx <= 0) {
            return 0;
        }
        ensureCounselAudioTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "UPDATE csm.counsel_audio_" + safe
                + " SET cs_idx = ?, temp_key = '' WHERE temp_key = ? AND (cs_idx IS NULL OR cs_idx = 0)";
        return jdbcTemplate.update(sql, csIdx, tempKey.trim());
    }

    public List<Map<String, Object>> getCounselAudioList(String inst, Integer csIdx, String tempKey) {
        ensureCounselAudioTable(inst);
        String safe = sanitizeInst(inst);
        if (csIdx != null && csIdx > 0) {
            String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, duration_seconds, transcript, summary_text, created_by, created_at, updated_at "
                    + "FROM csm.counsel_audio_" + safe + " WHERE cs_idx = ? ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql, csIdx);
        }
        if (tempKey != null && !tempKey.isBlank()) {
            String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, duration_seconds, transcript, summary_text, created_by, created_at, updated_at "
                    + "FROM csm.counsel_audio_" + safe + " WHERE temp_key = ? ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql, tempKey.trim());
        }
        return Collections.emptyList();
    }

    public Map<String, Object> getCounselAudioById(String inst, long audioId) {
        if (audioId <= 0) {
            return null;
        }
        ensureCounselAudioTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, duration_seconds, transcript, summary_text, created_by, created_at, updated_at "
                + "FROM csm.counsel_audio_" + safe + " WHERE id = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, audioId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int updateCounselAudioTranscript(String inst, long audioId, String transcript) {
        if (audioId <= 0 || transcript == null) {
            return 0;
        }
        String normalized = transcript.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return 0;
        }

        ensureCounselAudioTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "UPDATE csm.counsel_audio_" + safe + " SET transcript = ?, summary_text = '' WHERE id = ?";
        return jdbcTemplate.update(sql, normalized, audioId);
    }

    public int updateCounselAudioSummary(String inst, long audioId, String summaryText) {
        if (audioId <= 0) {
            return 0;
        }
        ensureCounselAudioTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "UPDATE csm.counsel_audio_" + safe + " SET summary_text = ? WHERE id = ?";
        return jdbcTemplate.update(sql, Optional.ofNullable(summaryText).orElse("").trim(), audioId);
    }

    public int deleteCounselAudioById(String inst, long audioId) {
        if (audioId <= 0) {
            return 0;
        }
        ensureCounselAudioTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "DELETE FROM csm.counsel_audio_" + safe + " WHERE id = ?";
        return jdbcTemplate.update(sql, audioId);
    }

    private void ensureCounselFileTable(String inst) {
        String safe = sanitizeInst(inst);
        String tableName = "counsel_file_" + safe;
        String ddl = "CREATE TABLE IF NOT EXISTS csm.counsel_file_" + safe + " ("
                + "id bigint auto_increment primary key,"
                + "cs_idx int default null,"
                + "temp_key varchar(80) default null,"
                + "original_filename varchar(255) not null,"
                + "stored_filename varchar(255) not null,"
                + "mime_type varchar(120) default null,"
                + "file_size bigint default 0,"
                + "created_by varchar(100) default null,"
                + "created_at timestamp default current_timestamp,"
                + "updated_at timestamp default current_timestamp on update current_timestamp"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        jdbcTemplate.execute(ddl);
        ensureTableColumn("csm", tableName, "extracted_text",
                "ALTER TABLE csm." + tableName + " ADD COLUMN extracted_text longtext");
        ensureTableColumn("csm", tableName, "summary_text",
                "ALTER TABLE csm." + tableName + " ADD COLUMN summary_text longtext");
    }

    public long insertCounselFile(
            String inst,
            Integer csIdx,
            String tempKey,
            String originalFilename,
            String storedFilename,
            String mimeType,
            long fileSize,
            String createdBy) {
        ensureCounselFileTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "INSERT INTO csm.counsel_file_" + safe + " ("
                + "cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, created_by"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            if (csIdx == null || csIdx <= 0) {
                ps.setObject(1, null);
            } else {
                ps.setInt(1, csIdx);
            }
            ps.setString(2, Optional.ofNullable(tempKey).orElse(""));
            ps.setString(3, Optional.ofNullable(originalFilename).orElse(""));
            ps.setString(4, Optional.ofNullable(storedFilename).orElse(""));
            ps.setString(5, Optional.ofNullable(mimeType).orElse("application/octet-stream"));
            ps.setLong(6, Math.max(fileSize, 0L));
            ps.setString(7, Optional.ofNullable(createdBy).orElse(""));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public int bindCounselFileByTempKey(String inst, String tempKey, int csIdx) {
        if (tempKey == null || tempKey.isBlank() || csIdx <= 0) {
            return 0;
        }
        ensureCounselFileTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "UPDATE csm.counsel_file_" + safe
                + " SET cs_idx = ?, temp_key = '' WHERE temp_key = ? AND (cs_idx IS NULL OR cs_idx = 0)";
        return jdbcTemplate.update(sql, csIdx, tempKey.trim());
    }

    public List<Map<String, Object>> getCounselFileList(String inst, Integer csIdx, String tempKey) {
        ensureCounselFileTable(inst);
        String safe = sanitizeInst(inst);
        if (csIdx != null && csIdx > 0) {
            String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, extracted_text, summary_text, created_by, created_at, updated_at "
                    + "FROM csm.counsel_file_" + safe + " WHERE cs_idx = ? ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql, csIdx);
        }
        if (tempKey != null && !tempKey.isBlank()) {
            String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, extracted_text, summary_text, created_by, created_at, updated_at "
                    + "FROM csm.counsel_file_" + safe + " WHERE temp_key = ? ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql, tempKey.trim());
        }
        return Collections.emptyList();
    }

    public Map<String, Object> getCounselFileById(String inst, long fileId) {
        if (fileId <= 0) {
            return null;
        }
        ensureCounselFileTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, extracted_text, summary_text, created_by, created_at, updated_at "
                + "FROM csm.counsel_file_" + safe + " WHERE id = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, fileId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int updateCounselFileExtractedText(String inst, long fileId, String extractedText) {
        if (fileId <= 0) {
            return 0;
        }
        ensureCounselFileTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "UPDATE csm.counsel_file_" + safe + " SET extracted_text = ?, summary_text = '' WHERE id = ?";
        return jdbcTemplate.update(sql, Optional.ofNullable(extractedText).orElse(""), fileId);
    }

    public int updateCounselFileSummary(String inst, long fileId, String summaryText) {
        if (fileId <= 0) {
            return 0;
        }
        ensureCounselFileTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "UPDATE csm.counsel_file_" + safe + " SET summary_text = ? WHERE id = ?";
        return jdbcTemplate.update(sql, Optional.ofNullable(summaryText).orElse("").trim(), fileId);
    }

    public int deleteCounselFileById(String inst, long fileId) {
        if (fileId <= 0) {
            return 0;
        }
        ensureCounselFileTable(inst);
        String safe = sanitizeInst(inst);
        String sql = "DELETE FROM csm.counsel_file_" + safe + " WHERE id = ?";
        return jdbcTemplate.update(sql, fileId);
    }

    public List<CounselLogGuardian> getCounselLogGuardianByLogIdx(String inst, int logIdx) {
        String safe = sanitizeInst(inst);
        String sql = "SELECT idx, log_idx, counsel_guardian, counsel_relationship, counsel_number, counsel_at "
                + "FROM csm.counsel_log_guardians_" + safe + " "
                + "WHERE log_idx = ? ORDER BY idx";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            CounselLogGuardian row = new CounselLogGuardian();
            row.setIdx(rs.getInt("idx"));
            row.setLog_idx(rs.getInt("log_idx"));
            row.setCounsel_guardian(rs.getString("counsel_guardian"));
            row.setCounsel_relationship(rs.getString("counsel_relationship"));
            row.setCounsel_number(rs.getString("counsel_number"));
            row.setCounsel_at(rs.getString("counsel_at"));
            return row;
        }, logIdx);
    }

    public int deleteCounselDataEntriesByCsIdx(String inst, int csIdx) {
        String safe = sanitizeInst(inst);
        String sql = "DELETE FROM csm.counsel_data_" + safe + "_entries WHERE cs_idx = ?";
        return jdbcTemplate.update(sql, csIdx);
    }

    public int deleteGuardiansByCsIdx(String inst, int csIdx) {
        String safe = sanitizeInst(inst);
        String sql = "DELETE FROM csm.counsel_data_" + safe + "_guardians WHERE cs_idx = ?";
        return jdbcTemplate.update(sql, csIdx);
    }

    public int counselDelete(String inst, int csIdx) {
        String safe = sanitizeInst(inst);
        String sql = "DELETE FROM csm.counsel_data_" + safe + " WHERE cs_idx = ?";
        return jdbcTemplate.update(sql, csIdx);
    }

}
