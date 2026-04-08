package com.coresolution.csm.serivce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

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
import com.coresolution.csm.vo.Counsel_phone;
import com.coresolution.csm.vo.Criteria;
import com.coresolution.csm.vo.Guardian;
import com.coresolution.csm.vo.Instdata;
import com.coresolution.csm.vo.Userdata;
import com.coresolution.csm.vo.UserdataCs;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CsmAuthService {
    private static final Logger log = LoggerFactory.getLogger(CsmAuthService.class);

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
        // 테이블명 안전성 보장 (영문/숫자/언더바만, 대소문자 허용)
        if (!candidate.matches("^[A-Za-z0-9_]{2,32}$"))
            return null;

        // 1) 입력값 그대로 확인
        String resolved = cs.resolveInstByTable(candidate);
        if (resolved != null)
            return resolved;

        // 2) 대문자/소문자 fallback 확인
        String upper = candidate.toUpperCase();
        if (!upper.equals(candidate)) {
            resolved = cs.resolveInstByTable(upper);
            if (resolved != null)
                return resolved;
        }
        String lower = candidate.toLowerCase();
        if (!lower.equals(candidate)) {
            resolved = cs.resolveInstByTable(lower);
            if (resolved != null)
                return resolved;
        }
        return null;
    }

    public int loginCount(String inst, String username, String encPwd) {
        Integer cnt = cs.loginCount(inst, username, encPwd);
        return cnt == null ? 0 : cnt;
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

    public CounselData getCounselById(String inst, int csIdx) {
        CounselData counselData = cs.getCounselById(inst, csIdx);
        if (counselData != null) {
            List<CounselDataEntry> entries = cs.getEntriesByInstAndCsIdx(inst, csIdx);
            counselData.setEntries(entries);
        }

        return counselData;
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

    private void ensureCounselAudioTable(String inst) {
        String safe = sanitizeInst(inst);
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
            String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, duration_seconds, transcript, created_by, created_at, updated_at "
                    + "FROM csm.counsel_audio_" + safe + " WHERE cs_idx = ? ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql, csIdx);
        }
        if (tempKey != null && !tempKey.isBlank()) {
            String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, duration_seconds, transcript, created_by, created_at, updated_at "
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
        String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, duration_seconds, transcript, created_by, created_at, updated_at "
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
        String sql = "UPDATE csm.counsel_audio_" + safe + " SET transcript = ? WHERE id = ?";
        return jdbcTemplate.update(sql, normalized, audioId);
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
            String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, created_by, created_at, updated_at "
                    + "FROM csm.counsel_file_" + safe + " WHERE cs_idx = ? ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql, csIdx);
        }
        if (tempKey != null && !tempKey.isBlank()) {
            String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, created_by, created_at, updated_at "
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
        String sql = "SELECT id, cs_idx, temp_key, original_filename, stored_filename, mime_type, file_size, created_by, created_at, updated_at "
                + "FROM csm.counsel_file_" + safe + " WHERE id = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, fileId);
        return rows.isEmpty() ? null : rows.get(0);
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
