package com.coresolution.csm.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.type.JdbcType;

import com.coresolution.csm.vo.Card;
import com.coresolution.csm.vo.Category1;
import com.coresolution.csm.vo.Category2;
import com.coresolution.csm.vo.Category3;
import com.coresolution.csm.vo.CounselComment;
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

@Mapper
public interface CsmMapper {
  @SelectProvider(type = UserSqlProvider.class, method = "infoByUsername")
  @Results(id = "UserdataMap", value = {
      @Result(column = "us_col_01", property = "us_col_01", id = true, jdbcType = JdbcType.INTEGER),
      @Result(column = "us_col_02", property = "us_col_02", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_03", property = "us_col_03", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_04", property = "us_col_04", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_05", property = "us_col_05", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_06", property = "us_col_06", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_07", property = "us_col_07", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_08", property = "us_col_08", jdbcType = JdbcType.INTEGER),
      @Result(column = "us_col_09", property = "us_col_09", jdbcType = JdbcType.INTEGER),
      @Result(column = "us_col_10", property = "us_col_10", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_11", property = "us_col_11", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_12", property = "us_col_12", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_13", property = "us_col_13", jdbcType = JdbcType.VARCHAR),
      @Result(column = "us_col_14", property = "us_col_14", jdbcType = JdbcType.VARCHAR)
  })
  Userdata infoByUsername(@Param("inst") String inst, @Param("username") String username);

  // 특정 inst 테이블 존재 확인
  @Select("""
          SELECT #{inst}
            FROM information_schema.tables
           WHERE table_schema = 'csm'
             AND table_name   = CONCAT('user_data_', #{inst})
           LIMIT 1
      """)
  String resolveInstByTable(@Param("inst") String inst);

  // --- 로그인 관련 (inst가 동적이므로 Provider로 변경) ---
  @SelectProvider(type = UserSqlProvider.class, method = "loginSelect")
  Integer loginSelect(Userdata ud);

  @SelectProvider(type = UserSqlProvider.class, method = "info")
  @ResultMap("UserdataMap")
  Userdata Info(Userdata ud);

  @SelectProvider(type = UserSqlProvider.class, method = "loginCount")
  Integer loginCount(@Param("inst") String inst,
      @Param("username") String username,
      @Param("password") String enc);

  // --- 상담 리스트 / 카운트 ---
  @SelectProvider(type = CounselSqlProvider.class, method = "searchCounselData")
  @Results(id = "CounselDataMap", value = {
      @Result(column = "cs_idx", property = "cs_idx", id = true, javaType = Integer.class, jdbcType = JdbcType.INTEGER),
      @Result(column = "created_at", property = "created_at", jdbcType = JdbcType.TIMESTAMP),
      @Result(column = "updated_at", property = "updated_at", jdbcType = JdbcType.TIMESTAMP),
      @Result(column = "cs_col_01_hex", property = "cs_col_01", jdbcType = JdbcType.VARCHAR),
      // ▼ 리스트에서 쓰는 컬럼들 명시 매핑
      @Result(column = "cs_col_01", property = "cs_col_01", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_02", property = "cs_col_02", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_03", property = "cs_col_03", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_04", property = "cs_col_04", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_05", property = "cs_col_05", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_06", property = "cs_col_06", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_07", property = "cs_col_07", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_08", property = "cs_col_08", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_09", property = "cs_col_09", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_10", property = "cs_col_10", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_11", property = "cs_col_11", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_12", property = "cs_col_12", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_13", property = "cs_col_13", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_14", property = "cs_col_14", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_15", property = "cs_col_15", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_16", property = "cs_col_16", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_17", property = "cs_col_17", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_18", property = "cs_col_18", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_19", property = "cs_col_19", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_20", property = "cs_col_20", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_21", property = "cs_col_21", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_22", property = "cs_col_22", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_23", property = "cs_col_23", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_24", property = "cs_col_24", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_25", property = "cs_col_25", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_26", property = "cs_col_26", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_27", property = "cs_col_27", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_28", property = "cs_col_28", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_29", property = "cs_col_29", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_30", property = "cs_col_30", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_31", property = "cs_col_31", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_32", property = "cs_col_32", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_33", property = "cs_col_33", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_34", property = "cs_col_34", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_35", property = "cs_col_35", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_36", property = "cs_col_36", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_37", property = "cs_col_37", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_38", property = "cs_col_38", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_39", property = "cs_col_39", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_40", property = "cs_col_40", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_41", property = "cs_col_41", jdbcType = JdbcType.VARCHAR),
      @Result(column = "cs_col_42", property = "cs_col_42", jdbcType = JdbcType.VARCHAR),

      // 해시(BLOB)는 byte[]로
      @Result(column = "cs_col_01_hash", property = "cs_col_01_hash", javaType = byte[].class, jdbcType = JdbcType.BLOB),
  })
  List<CounselData> searchCounselData(Criteria cri);

  @SelectProvider(type = CounselSqlProvider.class, method = "countCounselList")
  int CounselListCnt(Criteria cri);

  // --- 네비 컬럼 세트(view_yn='y') ---
  @SelectProvider(type = ListSqlProvider.class, method = "getOrderItems")
  List<Map<String, Object>> getOrderItems(@Param("inst") String inst);

  // --- 네비에 안 보이는 inner 항목들(원형 유지 버전) ---
  @SelectProvider(type = ListSqlProvider.class, method = "getInnerContentItemsAsIs")
  List<Map<String, Object>> getInnerContentItems(@Param("inst") String inst);

  // --- 보호자 목록 (동적 테이블명 → Provider로 안전하게 전환 권장) ---
  @SelectProvider(type = GuardianSqlProvider.class, method = "getGuardiansById")
  @Results({
      @Result(column = "id",             property = "id"),
      @Result(column = "cs_idx",         property = "cs_idx"),
      @Result(column = "name",           property = "name"),
      @Result(column = "relationship",   property = "relationship"),
      @Result(column = "contact_number", property = "contact_number"),
      @Result(column = "created_at",     property = "created_at"),
      @Result(column = "updated_at",     property = "updated_at")
  })
  List<Guardian> getGuardiansById(@Param("inst") String inst, @Param("cs_idx") Integer cs_idx);

  @Select("""
              select * from
      csm.user_data_${us_col_04}
          """)
  @ResultMap("UserdataMap")
  List<Userdata> userSelect(Userdata ud);

  /** 기관의 전체 사용자 목록 (삭제 제외) */
  @SelectProvider(type = UserSqlProvider.class, method = "listUsersByInst")
  @ResultMap("UserdataMap")
  List<Userdata> listUsersByInst(@Param("inst") String inst);

  @SelectProvider(type = UserSqlProvider.class, method = "userInfoByIdx")
  @ResultMap("UserdataMap")
  Userdata userInfo(@Param("us_col_01") int us_col_01, @Param("instCode") String instCode);

  @InsertProvider(type = UserSqlProvider.class, method = "userInsert")
  @Options(useGeneratedKeys = true, keyProperty = "us_col_01")
  int userInsert(Userdata ud);

  @UpdateProvider(type = UserSqlProvider.class, method = "userUpdate")
  int userUpdate(Userdata ud);

  @UpdateProvider(type = UserSqlProvider.class, method = "userUpdatePassword")
  int userUpdatePassword(Userdata ud);

  @DeleteProvider(type = UserSqlProvider.class, method = "userDelete")
  int userDelete(Userdata ud);

  @Select("""
      SELECT *
      FROM csm.inst_data_cs
      ORDER BY id_col_01 DESC
      """)
  @Results(id = "InstdataMap", value = {
      @Result(column = "id_col_01", property = "id_col_01", id = true),
      @Result(column = "id_col_02", property = "id_col_02"),
      @Result(column = "id_col_03", property = "id_col_03"),
      @Result(column = "id_col_04", property = "id_col_04"),
      @Result(column = "id_col_05", property = "id_col_05"),
      @Result(column = "id_col_06", property = "id_col_06"),
      @Result(column = "id_col_07", property = "id_col_07"),
      @Result(column = "id_col_08", property = "id_col_08"),
      @Result(column = "id_col_09", property = "id_col_09"),
      @Result(column = "sms_price", property = "sms_price"),
      @Result(column = "lms_price", property = "lms_price"),
      @Result(column = "mms_price", property = "mms_price")
  })
  List<Instdata> coreInstSelect();

  @Select("""
      SELECT *
      FROM csm.inst_data_cs
      WHERE id_col_03 = #{id_col_03}
      LIMIT 1
      """)
  @ResultMap("InstdataMap")
  Instdata coreInstFindByCode(@Param("id_col_03") String id_col_03);

  @Select("""
      SELECT id_col_03
      FROM csm.inst_data_cs
      WHERE LOWER(TRIM(id_col_02)) = LOWER(TRIM(#{instName}))
      LIMIT 1
      """)
  String coreInstCodeFindByName(@Param("instName") String instName);

  @Select("""
      SELECT id_col_09
      FROM csm.inst_data_cs
      WHERE id_col_02 = #{id_col_02}
      LIMIT 1
      """)
  String coreInstNumberFind(@Param("id_col_02") String id_col_02);

  @Insert("""
      INSERT INTO csm.inst_data_cs
      (id_col_02, id_col_03, id_col_04, id_col_05, id_col_06, id_col_07, id_col_08, id_col_09)
      VALUES
      (#{id_col_02}, #{id_col_03}, COALESCE(#{id_col_04}, 'y'), #{id_col_05}, #{id_col_06}, #{id_col_07}, #{id_col_08}, #{id_col_09})
      """)
  int coreInstInsert(Instdata inst);

  @UpdateProvider(type = InstDdlSqlProvider.class, method = "createUserTableIfNotExists")
  int createUserTableIfNotExists(@Param("inst") String inst);

  @Update("""
      UPDATE csm.inst_data_cs
      SET id_col_02 = #{id_col_02},
          id_col_03 = #{id_col_03},
          id_col_04 = COALESCE(#{id_col_04}, id_col_04),
          id_col_05 = #{id_col_05},
          id_col_06 = #{id_col_06},
          id_col_07 = #{id_col_07},
          id_col_08 = #{id_col_08},
          id_col_09 = #{id_col_09}
      WHERE id_col_01 = #{id_col_01}
      """)
  int coreInstUpdate(Instdata inst);

  @Delete("""
      DELETE FROM csm.inst_data_cs WHERE id_col_01 = #{id_col_01}
      """)
  int coreInstDelete(@Param("id_col_01") int id_col_01);

  @Select("""
      SELECT id, us_col_01, us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, us_col_07
      FROM csm.user_data_cs
      ORDER BY id DESC
      """)
  @Results(id = "UserdataCsMap", value = {
      @Result(column = "id", property = "id", id = true),
      @Result(column = "us_col_01", property = "us_col_01"),
      @Result(column = "us_col_02", property = "us_col_02"),
      @Result(column = "us_col_03", property = "us_col_03"),
      @Result(column = "us_col_04", property = "us_col_04"),
      @Result(column = "us_col_05", property = "us_col_05"),
      @Result(column = "us_col_06", property = "us_col_06"),
      @Result(column = "us_col_07", property = "us_col_07"),
      @Result(column = "us_col_08", property = "us_col_08")
  })
  List<UserdataCs> userSelectCs();

  @Insert("""
      INSERT INTO csm.user_data_cs
      (us_col_01, us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, us_col_07)
      VALUES
      (#{us_col_01}, #{us_col_02}, #{us_col_03}, #{us_col_04}, #{us_col_05}, #{us_col_06}, #{us_col_07})
      """)
  int newuserInsertCs(UserdataCs us);

  @Delete("""
      DELETE FROM csm.user_data_cs WHERE id = #{id}
      """)
  int userDeleteCs(@Param("id") int id);

  @DeleteProvider(type = UserSqlProvider.class, method = "userDeleteByUsername")
  int userDeleteByUsername(@Param("inst") String inst, @Param("username") String username);

  @Select("""
      SELECT idx, name, description, del_yn
      FROM csm.template
      ORDER BY idx DESC
      """)
  List<Map<String, Object>> coreTemplateSelect();

  @Insert("""
      INSERT INTO csm.template (name, description, del_yn)
      VALUES (#{name}, #{description}, 'N')
      """)
  int coreTemplateInsert(@Param("name") String name, @Param("description") String description);

  @Update("""
      UPDATE csm.template
      SET name = #{name},
          description = #{description}
      WHERE idx = #{idx}
      """)
  int coreTemplateModify(@Param("idx") int idx, @Param("name") String name, @Param("description") String description);

  @Update("""
      UPDATE csm.template
      SET del_yn = 'Y'
      WHERE idx = #{idx}
      """)
  int coreTemplateDelete(@Param("idx") int idx);

  @Update("""
      UPDATE csm.inst_data_cs
      SET sms_price = #{sms_price},
          lms_price = #{lms_price},
          mms_price = #{mms_price}
      """)
  int corePriceInsertAll(@Param("sms_price") String smsPrice, @Param("lms_price") String lmsPrice, @Param("mms_price") String mmsPrice);

  @Update("""
      UPDATE csm.inst_data_cs
      SET sms_price = #{sms_price},
          lms_price = #{lms_price},
          mms_price = #{mms_price}
      WHERE id_col_03 = #{id_col_03}
      """)
  int corePriceInsert(@Param("id_col_03") String idCol03, @Param("sms_price") String smsPrice, @Param("lms_price") String lmsPrice, @Param("mms_price") String mmsPrice);

  @Select("""
      SELECT idx, template_idx, main_col_01, turn
      FROM csm.template_main_category
      WHERE template_idx = #{template_idx}
      ORDER BY turn ASC, idx ASC
      """)
  List<Map<String, Object>> coreTemplateMainSelect(@Param("template_idx") int templateIdx);

  @Insert("""
      INSERT INTO csm.template_main_category (template_idx, main_col_01, turn)
      VALUES (#{template_idx}, #{main_col_01}, #{turn})
      """)
  int coreTemplateMainInsert(@Param("template_idx") int templateIdx, @Param("main_col_01") String name, @Param("turn") int turn);

  @Update("""
      UPDATE csm.template_main_category
      SET main_col_01 = #{main_col_01}
      WHERE idx = #{idx}
      """)
  int coreTemplateMainUpdate(@Param("idx") int idx, @Param("main_col_01") String name);

  @Delete("""
      DELETE FROM csm.template_main_category WHERE idx = #{idx}
      """)
  int coreTemplateMainDelete(@Param("idx") int idx);

  @Update("""
      UPDATE csm.template_main_category
      SET turn = #{turn}
      WHERE idx = #{idx}
      """)
  int coreTemplateMainTurnUpdate(@Param("idx") int idx, @Param("turn") int turn);

  @Select("""
      SELECT idx, main_idx, sub_col_01, sub_col_02, sub_col_03, sub_col_04, sub_col_05, turn
      FROM csm.template_sub_category
      WHERE main_idx = #{main_idx}
      ORDER BY turn ASC, idx ASC
      """)
  List<Map<String, Object>> coreTemplateSubSelect(@Param("main_idx") int mainIdx);

  @Insert("""
      INSERT INTO csm.template_sub_category (main_idx, sub_col_01, sub_col_02, sub_col_03, sub_col_04, sub_col_05, turn)
      VALUES (#{main_idx}, #{sub_col_01}, #{sub_col_02}, #{sub_col_03}, #{sub_col_04}, #{sub_col_05}, #{turn})
      """)
  int coreTemplateSubInsert(@Param("main_idx") int mainIdx,
      @Param("sub_col_01") String name,
      @Param("sub_col_02") int chk,
      @Param("sub_col_03") int rad,
      @Param("sub_col_04") int txt,
      @Param("sub_col_05") int sel,
      @Param("turn") int turn);

  @Update("""
      UPDATE csm.template_sub_category
      SET sub_col_01 = #{sub_col_01}
      WHERE idx = #{idx}
      """)
  int coreTemplateSubUpdate(@Param("idx") int idx, @Param("sub_col_01") String name);

  @Update("""
      UPDATE csm.template_sub_category
      SET sub_col_01 = #{sub_col_01},
          sub_col_02 = COALESCE(#{sub_col_02}, sub_col_02),
          sub_col_03 = COALESCE(#{sub_col_03}, sub_col_03),
          sub_col_04 = COALESCE(#{sub_col_04}, sub_col_04),
          sub_col_05 = COALESCE(#{sub_col_05}, sub_col_05)
      WHERE idx = #{idx}
      """)
  int coreTemplateSubUpdateWithFlags(
      @Param("idx") int idx,
      @Param("sub_col_01") String name,
      @Param("sub_col_02") Integer chk,
      @Param("sub_col_03") Integer rad,
      @Param("sub_col_04") Integer txt,
      @Param("sub_col_05") Integer sel);

  @Delete("""
      DELETE FROM csm.template_sub_category WHERE idx = #{idx}
      """)
  int coreTemplateSubDelete(@Param("idx") int idx);

  @Update("""
      UPDATE csm.template_sub_category
      SET turn = #{turn}
      WHERE idx = #{idx}
      """)
  int coreTemplateSubTurnUpdate(@Param("idx") int idx, @Param("turn") int turn);

  @Select("""
      SELECT idx, sub_idx, option_col_01, turn
      FROM csm.template_option
      WHERE sub_idx = #{sub_idx}
      ORDER BY turn ASC, idx ASC
      """)
  List<Map<String, Object>> coreTemplateOptionSelect(@Param("sub_idx") int subIdx);

  @Insert("""
      INSERT INTO csm.template_option (sub_idx, option_col_01, turn)
      VALUES (#{sub_idx}, #{option_col_01}, #{turn})
      """)
  int coreTemplateOptionInsert(@Param("sub_idx") int subIdx, @Param("option_col_01") String option, @Param("turn") int turn);

  @Update("""
      UPDATE csm.template_option
      SET option_col_01 = #{option_col_01}
      WHERE idx = #{idx}
      """)
  int coreTemplateOptionUpdate(@Param("idx") int idx, @Param("option_col_01") String option);

  @Delete("""
      DELETE FROM csm.template_option WHERE idx = #{idx}
      """)
  int coreTemplateOptionDelete(@Param("idx") int idx);

  @Update("""
      UPDATE csm.template_option
      SET turn = #{turn}
      WHERE idx = #{idx}
      """)
  int coreTemplateOptionTurnUpdate(@Param("idx") int idx, @Param("turn") int turn);

  @Delete("""
      DELETE FROM csm.template_option WHERE sub_idx = #{sub_idx}
      """)
  int coreTemplateOptionDeleteBySubIdx(@Param("sub_idx") int subIdx);

  @Delete("""
      DELETE FROM csm.template_sub_category WHERE main_idx = #{main_idx}
      """)
  int coreTemplateSubDeleteByMainIdx(@Param("main_idx") int mainIdx);

  @Select("""
           SELECT * FROM csm.card_${inst}
      WHERE 1 = 1
      order by created_at desc
           """)
  @Results(id = "CardMap", value = {
      @Result(column = "id", property = "id", id = true, jdbcType = JdbcType.INTEGER),
      @Result(column = "title", property = "title", jdbcType = JdbcType.VARCHAR),
      @Result(column = "content", property = "content", jdbcType = JdbcType.VARCHAR),
      @Result(column = "created_at", property = "created_at", jdbcType = JdbcType.TIMESTAMP)
  })
  List<Card> SelectCard(String inst);

  @Select("""
      <script>
      SELECT COUNT(*)
        FROM csm.card_${inst}
       <if test="keyword != null and keyword != ''">
          WHERE title LIKE CONCAT('%', #{keyword}, '%')
             OR content LIKE CONCAT('%', #{keyword}, '%')
       </if>
      </script>
      """)
  int cardCnt(Criteria cri);

  @Select("""
      <script>
      SELECT *
        FROM csm.card_${inst}
       <if test="keyword != null and keyword != ''">
          WHERE title LIKE CONCAT('%', #{keyword}, '%')
             OR content LIKE CONCAT('%', #{keyword}, '%')
       </if>
       ORDER BY created_at DESC
       LIMIT #{pageStart}, #{perPageNum}
      </script>
      """)
  @Results(value = {
      @Result(column = "id", property = "id", id = true, jdbcType = JdbcType.INTEGER),
      @Result(column = "title", property = "title", jdbcType = JdbcType.VARCHAR),
      @Result(column = "content", property = "content", jdbcType = JdbcType.VARCHAR),
      @Result(column = "created_at", property = "created_at", jdbcType = JdbcType.TIMESTAMP)
  })
  List<Card> SelectCardSearch(Criteria cri);

  @Insert("""
      INSERT INTO csm.card_${inst} (title, content, created_at)
      VALUES (#{title}, #{content}, NOW())
      """)
  int InsertCard(Card card);

  @Update("""
      UPDATE csm.card_${inst}
         SET title = #{title},
             content = #{content}
       WHERE id = #{id}
      """)
  int UpdateCard(Card card);

  @Delete("""
      DELETE FROM csm.card_${inst}
       WHERE id = #{id}
      """)
  int DeleteCard(Card card);

  @Select("""
      SELECT
        id,
        phone_num,
        phone_name
      FROM csm.phone_number_${inst}
      ORDER BY id ASC
      """)
  @Results(id = "CounselPhoneMap", value = {
      @Result(column = "id", property = "id", id = true),
      @Result(column = "phone_num", property = "phone_num"),
      @Result(column = "phone_name", property = "phone_name")
  })
  List<Counsel_phone> selectPhone(Counsel_phone cp);

  @Results(id = "Category1Map", value = {
      @Result(column = "cc_col_01", property = "cc_col_01", id = true),
      @Result(column = "cc_col_02", property = "cc_col_02"),
      @Result(column = "inst", property = "inst", javaType = String.class, jdbcType = JdbcType.VARCHAR, one = @One(select = "")) // inst
                                                                                                                                 // 컬럼
                                                                                                                                 // 없으면
                                                                                                                                 // 제거
  })
  @Select("""
        <script>
        SELECT cc_col_01, cc_col_02
        FROM csm.counsel_category1_${inst}
        ORDER BY turn ASC
        </script>
      """)
  List<Category1> selectCategory1(@Param("inst") String inst);

  @Results(id = "Category2Map", value = {
      @Result(column = "cc_col_01", property = "cc_col_01", id = true),
      @Result(column = "cc_col_02", property = "cc_col_02"),
      @Result(column = "cc_col_03", property = "cc_col_03"),
      @Result(column = "cc_col_04", property = "cc_col_04"),
      @Result(column = "cc_col_05", property = "cc_col_05"),
      @Result(column = "cc_col_06", property = "cc_col_06"),
      @Result(column = "cc_col_07", property = "cc_col_07")
  })
  @Select("""
        <script>
        SELECT cc_col_01, cc_col_02, cc_col_03, cc_col_04, cc_col_05, cc_col_06, cc_col_07
        FROM csm.counsel_category2_${inst}
        WHERE cc_col_03 = #{cc_col_01}
        ORDER BY turn ASC
        </script>
      """)
  List<Category2> selectCategory2ByCategory1Id(@Param("inst") String inst,
      @Param("cc_col_01") int category1Id);

  @Results(id = "Category3Map", value = {
      @Result(column = "cc_col_01", property = "cc_col_01", id = true),
      @Result(column = "cc_col_02", property = "cc_col_02"),
      @Result(column = "cc_col_03", property = "category3"), // 임시로 받기(원문 저장)
      @Result(column = "cc_col_04", property = "cc_col_04")
  })
  @Select("""
        <script>
        SELECT cc_col_01, cc_col_02, cc_col_03, cc_col_04
        FROM csm.counsel_category3_${inst}
        WHERE cc_col_04 = #{cc_col_01}
        ORDER BY turn ASC
        </script>
      """)
  List<Category3> selectCategory3ByCategory2Id(@Param("inst") String inst,
      @Param("cc_col_01") int category2Id);

  @Results(id = "CounselDataMap", value = {
      @Result(property = "cs_idx", column = "cs_idx"),
      @Result(property = "cs_col_01", column = "cs_col_01"),
      @Result(property = "cs_col_02", column = "cs_col_02"),
      @Result(property = "cs_col_03", column = "cs_col_03"),
      @Result(property = "cs_col_04", column = "cs_col_04"),
      @Result(property = "cs_col_05", column = "cs_col_05"),
      @Result(property = "cs_col_06", column = "cs_col_06"),
      @Result(property = "cs_col_07", column = "cs_col_07"),
      @Result(property = "cs_col_08", column = "cs_col_08"),
      @Result(property = "cs_col_09", column = "cs_col_09"),
      @Result(property = "cs_col_10", column = "cs_col_10"),
      @Result(property = "cs_col_11", column = "cs_col_11"),
      @Result(property = "cs_col_16", column = "cs_col_16"),
      @Result(property = "cs_col_17", column = "cs_col_17"),
      @Result(property = "cs_col_18", column = "cs_col_18"),
      @Result(property = "cs_col_19", column = "cs_col_19"),
      @Result(property = "cs_col_20", column = "cs_col_20"),
      @Result(property = "cs_col_21", column = "cs_col_21"),
      @Result(property = "cs_col_22", column = "cs_col_22"),
      @Result(property = "cs_col_23", column = "cs_col_23"),
      @Result(property = "cs_col_24", column = "cs_col_24"),
      @Result(property = "cs_col_25", column = "cs_col_25"),
      @Result(property = "cs_col_26", column = "cs_col_26"),
      @Result(property = "cs_col_27", column = "cs_col_27"),
      @Result(property = "cs_col_28", column = "cs_col_28"),
      @Result(property = "cs_col_29", column = "cs_col_29"),
      @Result(property = "cs_col_30", column = "cs_col_30"),
      @Result(property = "cs_col_31", column = "cs_col_31"),
      @Result(property = "cs_col_32", column = "cs_col_32"),
      @Result(property = "cs_col_33", column = "cs_col_33"),
      @Result(property = "cs_col_34", column = "cs_col_34"),
      @Result(property = "cs_col_35", column = "cs_col_35"),
      @Result(property = "cs_col_36", column = "cs_col_36"),
      @Result(property = "cs_col_37", column = "cs_col_37"),
      @Result(property = "cs_col_38", column = "cs_col_38"),
      @Result(property = "cs_col_39", column = "cs_col_39"),
      @Result(property = "cs_col_40", column = "cs_col_40"),
      @Result(property = "cs_col_41", column = "cs_col_41"),
      @Result(property = "cs_col_42", column = "cs_col_42"),
      // 필요한 만큼만 이어서…
  })
  @Select("""
        SELECT
          cs_idx, created_at, updated_at,
          cs_col_01, cs_col_02, cs_col_03, cs_col_04, cs_col_05,
          cs_col_06, cs_col_07, cs_col_08, cs_col_09, cs_col_10,
          cs_col_11, cs_col_12, cs_col_13, cs_col_14, cs_col_15,
          cs_col_16, cs_col_17, cs_col_18, cs_col_19,
          cs_col_20, cs_col_21, cs_col_22, cs_col_23, cs_col_24,
          cs_col_25, cs_col_26, cs_col_27, cs_col_28, cs_col_29,
          cs_col_30, cs_col_31, cs_col_32, cs_col_33, cs_col_34,
          cs_col_35, cs_col_36, cs_col_37, cs_col_38, cs_col_39,
          cs_col_40, cs_col_41, cs_col_42
        FROM csm.counsel_data_${inst}
        WHERE cs_idx = #{csIdx}
      """)
  @ResultMap("CounselDataMap")
  CounselData getCounselById(@Param("inst") String inst, @Param("csIdx") int csIdx);

  @Select("""
        SELECT entry_id, cs_idx, category_id, subcategory_id, value, field_type
        FROM csm.counsel_data_${inst}_entries
        WHERE cs_idx = #{csIdx}
        ORDER BY entry_id
      """)
  @Results(id = "CounselEntryMap", value = {
      @Result(column = "entry_id", property = "entry_id"),
      @Result(column = "cs_idx", property = "cs_idx"),
      @Result(column = "category_id", property = "category_id"),
      @Result(column = "subcategory_id", property = "subcategory_id"),
      @Result(column = "field_type", property = "fieldType")
  })
  List<CounselDataEntry> getEntriesByInstAndCsIdx(@Param("inst") String inst, @Param("csIdx") int csIdx);

  @Select("""
      SELECT * FROM csm.counsel_log_${inst} WHERE cs_idx = #{csIdx} order by idx desc
      """)
  @Results(id = "CounselLogMap", value = {
      @Result(column = "idx", property = "idx", id = true, jdbcType = JdbcType.INTEGER),
      @Result(column = "cs_idx", property = "cs_idx", jdbcType = JdbcType.INTEGER),
      @Result(column = "name", property = "name", jdbcType = JdbcType.VARCHAR),
      @Result(column = "counsel_content", property = "counsel_content", jdbcType = JdbcType.LONGVARCHAR),
      @Result(column = "counsel_method", property = "counsel_method", jdbcType = JdbcType.VARCHAR),
      @Result(column = "counsel_result", property = "counsel_result", jdbcType = JdbcType.LONGVARCHAR),
      @Result(column = "counsel_name", property = "counsel_name", jdbcType = JdbcType.VARCHAR),
      @Result(column = "created_at", property = "created_at", jdbcType = JdbcType.TIMESTAMP),
      @Result(column = "updated_at", property = "updated_at", jdbcType = JdbcType.TIMESTAMP),
      @Result(column = "counsel_at", property = "counsel_at", jdbcType = JdbcType.VARCHAR)
  })
  List<CounselLog> getCounselLog(@Param("inst") String inst, @Param("csIdx") int csIdx);

  @Select("""
      SELECT idx, cs_idx, name, counsel_content, counsel_method, counsel_result, counsel_name, created_at, updated_at, counsel_at
      FROM csm.counsel_log_${inst}
      WHERE idx = #{logIdx}
      LIMIT 1
      """)
  @ResultMap("CounselLogMap")
  CounselLog getCounselLogByIdx(@Param("inst") String inst, @Param("logIdx") int logIdx);

  @Select("""
        SELECT g.*
       FROM csm.counsel_log_guardians_${inst} g
       JOIN csm.counsel_log_${inst} l
         ON g.log_idx = l.idx
      WHERE l.cs_idx = #{csIdx}
      ORDER BY g.idx DESC
        """)
  List<CounselLogGuardian> getCounselLogGuardian(@Param("inst") String inst, @Param("csIdx") int csIdx);

  // --- 통계 ---
  @SelectProvider(type = StatisticsSqlProvider.class, method = "getMonthlyCounselStatistics")
  List<Map<String, Object>> getMonthlyCounselStatistics(Map<String, Object> params);

  @SelectProvider(type = StatisticsSqlProvider.class, method = "getCounselDateRange")
  Map<String, Object> getCounselDateRange(Map<String, Object> params);

  @SelectProvider(type = StatisticsSqlProvider.class, method = "getTypeStatistics")
  List<Map<String, Object>> getTypeStatistics(Map<String, Object> params);

  @SelectProvider(type = StatisticsSqlProvider.class, method = "selectAdmissionSuccessStats")
  List<Map<String, Object>> selectAdmissionSuccessStats(Map<String, Object> params);

  @SelectProvider(type = StatisticsSqlProvider.class, method = "selectAdmissionTypeStats")
  List<Map<String, Object>> selectAdmissionTypeStats(Map<String, Object> params);

  @SelectProvider(type = StatisticsSqlProvider.class, method = "selectAdmissionTypeSuccessStats")
  List<Map<String, Object>> selectAdmissionTypeSuccessStats(Map<String, Object> params);

  @SelectProvider(type = StatisticsSqlProvider.class, method = "getCurrentLocationStats")
  List<Map<String, Object>> getCurrentLocationStats(Map<String, Object> params);

  @SelectProvider(type = StatisticsSqlProvider.class, method = "getCurrentLocationSuccessStats")
  List<Map<String, Object>> getCurrentLocationSuccessStats(Map<String, Object> params);

  @SelectProvider(type = StatisticsSqlProvider.class, method = "getNonAdmissionReasonStats")
  List<Map<String, Object>> getNonAdmissionReasonStats(Map<String, Object> params);

  /*
   * ===========================
   * Providers
   * ===========================
   */

  /** 로그인/사용자 테이블 전용 */
  class UserSqlProvider {
    public static String infoByUsername(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      return "SELECT us_col_01, us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, " +
          "       us_col_07, us_col_08, us_col_09, us_col_10, us_col_11, us_col_12, us_col_13, us_col_14 " +
          "  FROM csm.user_data_" + t +
          " WHERE us_col_02 = #{username} " +
          " LIMIT 1";
    }

    public static String loginSelect(Userdata ud) {
      String t = sanitizeInst(ud.getUs_col_04());
      return """
          SELECT COUNT(*)
            FROM csm.user_data_""" + t + """
               WHERE us_col_04 = #{us_col_04}
                 AND us_col_02 = #{us_col_02}
                 AND us_col_03 = #{us_col_03}
          """;
    }

    public static String info(Userdata ud) {
      String t = sanitizeInst(ud.getUs_col_04());
      return """
          SELECT us_col_01, us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, us_col_07,
                 us_col_08, us_col_09, us_col_10, us_col_11, us_col_12, us_col_13, us_col_14
            FROM csm.user_data_""" + t + """
               WHERE us_col_02 = #{us_col_02}
                 AND us_col_03 = #{us_col_03}
          """;
    }

    public static String loginCount(Map<String, Object> params) {
      String t = sanitizeInst((String) params.get("inst"));
      return """
          SELECT COUNT(*)
            FROM csm.user_data_""" + t + """
               WHERE us_col_04 = #{inst}
                 AND us_col_02 = #{username}
                 AND us_col_03 = #{password}
          """;
    }

    public static String userInfoByIdx(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("instCode"));
      return "SELECT us_col_01, us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, " +
          "       us_col_07, us_col_08, us_col_09, us_col_10, us_col_11, us_col_12, us_col_13, us_col_14 " +
          "  FROM csm.user_data_" + t +
          " WHERE us_col_01 = #{us_col_01} " +
          " LIMIT 1";
    }

    public static String userInsert(Userdata ud) {
      String t = sanitizeInst(ud.getUs_col_04());
      return """
          INSERT INTO csm.user_data_""" + t + """
            (us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, us_col_07, us_col_08, us_col_09, us_col_10, us_col_11, us_col_12, us_col_13, us_col_14)
          VALUES
            (#{us_col_02}, #{us_col_03}, #{us_col_04}, #{us_col_05}, #{us_col_06}, #{us_col_07}, #{us_col_08}, #{us_col_09}, #{us_col_10}, #{us_col_11}, #{us_col_12}, #{us_col_13}, #{us_col_14})
          """;
    }

    public static String userUpdate(Userdata ud) {
      String t = sanitizeInst(ud.getUs_col_04());
      return """
          UPDATE csm.user_data_""" + t + """
             SET us_col_02 = #{us_col_02},
                 us_col_06 = #{us_col_06},
                 us_col_07 = #{us_col_07},
                 us_col_08 = #{us_col_08},
                 us_col_11 = #{us_col_11},
                 us_col_12 = #{us_col_12},
                 us_col_13 = #{us_col_13},
                 us_col_14 = #{us_col_14}
           WHERE us_col_01 = #{us_col_01}
          """;
    }

    public static String userUpdatePassword(Userdata ud) {
      String t = sanitizeInst(ud.getUs_col_04());
      return """
          UPDATE csm.user_data_""" + t + """
             SET us_col_03 = #{us_col_03}
           WHERE us_col_01 = #{us_col_01}
          """;
    }

    public static String userDelete(Userdata ud) {
      String t = sanitizeInst(ud.getUs_col_04());
      return """
          DELETE FROM csm.user_data_""" + t + """
           WHERE us_col_01 = #{us_col_01}
          """;
    }

    public static String userDeleteByUsername(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      return """
          DELETE FROM csm.user_data_""" + t + """
           WHERE us_col_02 = #{username}
          """;
    }

    public static String listUsersByInst(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      return "SELECT us_col_01, us_col_02, us_col_04, us_col_05, us_col_06, " +
             "       us_col_07, us_col_08, us_col_09, us_col_10, us_col_11, us_col_12, us_col_13, us_col_14 " +
             "  FROM csm.user_data_" + t +
             " WHERE us_col_09 != 2" +
             " ORDER BY us_col_01";
    }

    // ✅ 여기 추가
    private static String sanitizeInst(String inst) {
      if (inst == null)
        throw new IllegalArgumentException("inst is null");
      String normalized = inst.trim();
      if (!normalized.matches("[A-Za-z0-9_]{2,20}"))
        throw new IllegalArgumentException("Invalid inst: " + inst);
      return normalized;
    }
  }

  /** 상담 목록/카운트 전용 */
  class CounselSqlProvider {
    public static String searchCounselData(Criteria cri) {
      String inst = sanitizeInst(cri.getInst());
      StringBuilder sb = new StringBuilder()
          .append("SELECT DISTINCT c.*, HEX(c.cs_col_01) AS cs_col_01_hex ")
          .append("FROM csm.counsel_data_").append(inst).append(" c ")
          .append("LEFT JOIN csm.counsel_data_").append(inst).append("_guardians g ON c.cs_idx = g.cs_idx ")
          .append("WHERE 1=1 ");

      if (nonEmpty(cri.getStartDate()) && nonEmpty(cri.getEndDate())) {
        sb.append(
            " AND STR_TO_DATE(c.cs_col_16, '%Y-%m-%d') BETWEEN STR_TO_DATE(#{startDate}, '%Y-%m-%d') AND STR_TO_DATE(#{endDate}, '%Y-%m-%d') ");
      } else if (nonEmpty(cri.getDateRange()) && !"all".equals(cri.getDateRange()) && !"custom".equals(cri.getDateRange())) {
        sb.append(" AND STR_TO_DATE(c.cs_col_16, '%Y-%m-%d') >= DATE_SUB(CURDATE(), INTERVAL #{dateRange} DAY) ");
      }
      if (!nonEmpty(cri.getEnd())) {
        sb.append(" AND c.cs_col_19 != '입원완료' ");
      }
      if (nonEmpty(cri.getStatus()) && !"all".equalsIgnoreCase(cri.getStatus())) {
        sb.append(" AND c.cs_col_19 = #{status} ");
      }
      if (nonEmpty(cri.getPathType()) && !"all".equalsIgnoreCase(cri.getPathType())) {
        sb.append(" AND c.cs_col_08 = #{pathType} ");
      }

      boolean hasKeyword = nonEmpty(cri.getKeyword()) || cri.getKeywordBytes() != null;
      if (hasKeyword) {
        switch (String.valueOf(cri.getSearchType())) {
          case "patient" -> sb.append(
              " AND AES_DECRYPT(UNHEX(c.cs_col_01), #{aesKey}) LIKE CONCAT('%', #{keyword}, '%') ");
          case "guardian" -> sb.append(
              " AND AES_DECRYPT(UNHEX(g.name), #{aesKey}) LIKE CONCAT('%', #{keyword}, '%') ");
          case "phone" -> {
            sb.append(" AND ( ");
            sb.append("  (#{keywordBytes} IS NOT NULL AND g.contact_number_hash = #{keywordBytes}) ");
            sb.append("  OR (#{keywordBytes} IS NULL AND ( ");
            sb.append(
                "       AES_DECRYPT(UNHEX(g.contact_number), #{aesKey}) LIKE CONCAT('%', #{keyword}, '%') ");
            sb.append("    OR RIGHT(AES_DECRYPT(UNHEX(g.contact_number), #{aesKey}), 4) = #{keyword} ");
            sb.append("    OR MID(AES_DECRYPT(UNHEX(g.contact_number), #{aesKey}), ");
            sb.append("       LENGTH(AES_DECRYPT(UNHEX(g.contact_number), #{aesKey})) - 8, 4) = #{keyword} ");
            sb.append("  )) ) ");
          }
          case "counselor" -> sb.append(" AND c.cs_col_17 LIKE CONCAT('%', #{keyword}, '%') ");
          case "content" ->
            sb.append(" AND REPLACE(REPLACE(c.cs_col_32, '\n', ''), '\r', '') LIKE CONCAT('%', #{keyword}, '%') ");
          default -> {
            /* no-op */ }
        }
      }

      sb.append(" ORDER BY STR_TO_DATE(c.cs_col_16, '%Y-%m-%d') DESC ");
      sb.append(" LIMIT #{pageStart}, #{perPageNum} ");
      return sb.toString();
    }

    public static String countCounselList(Criteria cri) {
      String inst = sanitizeInst(cri.getInst());
      StringBuilder sb = new StringBuilder()
          .append("SELECT COUNT(DISTINCT c.cs_idx) ")
          .append("FROM csm.counsel_data_").append(inst).append(" c ")
          .append("LEFT JOIN csm.counsel_data_").append(inst).append("_guardians g ON c.cs_idx = g.cs_idx ")
          .append("WHERE 1=1 ");

      if (nonEmpty(cri.getStartDate()) && nonEmpty(cri.getEndDate())) {
        sb.append(
            " AND STR_TO_DATE(c.cs_col_16, '%Y-%m-%d') BETWEEN STR_TO_DATE(#{startDate}, '%Y-%m-%d') AND STR_TO_DATE(#{endDate}, '%Y-%m-%d') ");
      } else if (nonEmpty(cri.getDateRange()) && !"all".equals(cri.getDateRange()) && !"custom".equals(cri.getDateRange())) {
        sb.append(" AND STR_TO_DATE(c.cs_col_16, '%Y-%m-%d') >= DATE_SUB(CURDATE(), INTERVAL #{dateRange} DAY) ");
      }
      if (!nonEmpty(cri.getEnd())) {
        sb.append(" AND c.cs_col_19 != '입원완료' ");
      }
      if (nonEmpty(cri.getStatus()) && !"all".equalsIgnoreCase(cri.getStatus())) {
        sb.append(" AND c.cs_col_19 = #{status} ");
      }
      if (nonEmpty(cri.getPathType()) && !"all".equalsIgnoreCase(cri.getPathType())) {
        sb.append(" AND c.cs_col_08 = #{pathType} ");
      }

      boolean hasKeyword = nonEmpty(cri.getKeyword()) || cri.getKeywordBytes() != null;
      if (hasKeyword) {
        String type = String.valueOf(cri.getSearchType());
        switch (type) {
          case "patient" -> sb.append(" AND c.cs_col_01_hash = #{keywordBytes} ");
          case "guardian" -> sb.append(" AND g.name_hash    = #{keywordBytes} ");
          case "phone" -> {
            if (cri.getKeywordBytes() != null) {
              sb.append(" AND g.contact_number_hash = #{keywordBytes} ");
            } else {
              sb.append(" AND ( ")
                  .append(
                      " AES_DECRYPT(UNHEX(g.contact_number), #{aesKey}) LIKE CONCAT('%', #{keyword}, '%') ")
                  .append(" OR RIGHT(AES_DECRYPT(UNHEX(g.contact_number), #{aesKey}), 4) = #{keyword} ")
                  .append(" OR MID( ")
                  .append("     AES_DECRYPT(UNHEX(g.contact_number), #{aesKey}), ")
                  .append("     LENGTH(AES_DECRYPT(UNHEX(g.contact_number), #{aesKey})) - 8, 4 ")
                  .append(" ) = #{keyword} ) ");
            }
          }
          case "counselor" -> sb.append(" AND c.cs_col_17 LIKE CONCAT('%', #{keyword}, '%') ");
          case "content" -> sb.append(" AND c.cs_col_32 LIKE CONCAT('%', #{keyword}, '%') ");
          default -> {
            /* no-op */ }
        }
      }
      return sb.toString();
    }

    private static boolean nonEmpty(String s) {
      return s != null && !s.isBlank();
    }

    private static String sanitizeInst(String inst) {
      if (inst == null)
        throw new IllegalArgumentException("inst is null");
      String normalized = inst.trim();
      if (!normalized.matches("[A-Za-z0-9_]{2,20}"))
        throw new IllegalArgumentException("Invalid inst: " + inst);
      return normalized;
    }
  }

  /** 좌측 네비 메뉴/내부 항목 전용 */
  class ListSqlProvider {
    public static String getOrderItems(Map<String, Object> params) {
      String safe = sanitizeInst((String) params.get("inst"));
      // 테이블명 뒤 공백 주의! comment는 백틱으로 감싸고, 문자열 변환
      return String.format("""
          SELECT
            coulmn AS column_name,
            COALESCE(CONVERT(`comment` USING utf8mb4), '') AS column_comment,
            turn
          FROM csm.counsel_list_%s
          WHERE view_yn = 'y'
          ORDER BY turn ASC
          """, safe);
    }

    // XML 원형 유지 버전
    public static String getInnerContentItemsAsIs(Map<String, Object> params) {
      String safe = sanitizeInst((String) params.get("inst"));
      return String.format("""
          SELECT
            c.column_name,
            COALESCE(CAST(c.column_comment AS CHAR CHARACTER SET utf8mb4), '') AS column_comment
          FROM information_schema.columns c
          LEFT JOIN csm.counsel_list_%s cl
            ON c.column_name = cl.coulmn AND cl.view_yn = 'y'
          WHERE c.table_schema = 'csm'
            AND c.table_name = 'counsel_data_%s'
            AND (cl.coulmn IS NULL OR cl.view_yn = 'n')
          ORDER BY c.ordinal_position
          """, safe, safe);
    }

    private static String sanitizeInst(String inst) {
      if (inst == null)
        throw new IllegalArgumentException("inst is null");
      String normalized = inst.trim();
      if (!normalized.matches("[A-Za-z0-9_]{2,20}"))
        throw new IllegalArgumentException("Invalid inst: " + inst);
      return normalized;
    }
  }

  /** 보호자 테이블 전용 */
  class GuardianSqlProvider {
    public static String getGuardiansById(Map<String, Object> params) {
      String safe = sanitizeInst((String) params.get("inst"));
      return "SELECT id, cs_idx, name, contact_number, relationship, created_at, updated_at "
          + "FROM csm.counsel_data_" + safe + "_guardians "
          + "WHERE cs_idx = #{cs_idx} ORDER BY id";
    }

    private static String sanitizeInst(String inst) {
      if (inst == null)
        throw new IllegalArgumentException("inst is null");
      String normalized = inst.trim();
      if (!normalized.matches("[A-Za-z0-9_]{2,20}"))
        throw new IllegalArgumentException("Invalid inst: " + inst);
      return normalized;
    }
  }

  /** 통계 전용 */
  class StatisticsSqlProvider {
    public static String getCounselDateRange(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" MIN(STR_TO_DATE(cs_col_16, '%Y-%m-%d')) AS first_date, ")
          .append(" MAX(STR_TO_DATE(cs_col_16, '%Y-%m-%d')) AS last_date ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      return sb.toString();
    }

    public static String getMonthlyCounselStatistics(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" DATE_FORMAT(STR_TO_DATE(cs_col_16, '%Y-%m-%d'), '%Y-%m') AS month, ")
          .append(" TRIM(cs_col_17) AS counselor, ")
          .append(" COUNT(*) AS count ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      sb.append(" AND STR_TO_DATE(cs_col_16, '%Y-%m-%d') BETWEEN ")
          .append(" DATE_SUB(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d'), INTERVAL 12 MONTH) ")
          .append(" AND LAST_DAY(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d')) ");
      appendCounselorFilter(sb, p);
      sb.append(" GROUP BY month, counselor ")
          .append(" ORDER BY month ASC, counselor ASC ");
      return sb.toString();
    }

    public static String getTypeStatistics(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" DATE_FORMAT(STR_TO_DATE(cs_col_16, '%Y-%m-%d'), '%Y-%m') AS month, ")
          .append(" cs_col_18 AS method, ")
          .append(" COUNT(*) AS count ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      sb.append(" AND STR_TO_DATE(cs_col_16, '%Y-%m-%d') BETWEEN ")
          .append(" DATE_SUB(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d'), INTERVAL 12 MONTH) ")
          .append(" AND LAST_DAY(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d')) ");
      appendCounselorFilter(sb, p);
      sb.append(" GROUP BY month, method ")
          .append(" ORDER BY month ASC, method ASC ");
      return sb.toString();
    }

    public static String selectAdmissionSuccessStats(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" DATE_FORMAT(STR_TO_DATE(cs_col_16, '%Y-%m-%d'), '%Y-%m') AS month, ")
          .append(" TRIM(cs_col_17) AS counselor, ")
          .append(" COUNT(*) AS count ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_19 = '입원완료' ")
          .append(" AND cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      sb.append(" AND STR_TO_DATE(cs_col_16, '%Y-%m-%d') BETWEEN ")
          .append(" DATE_SUB(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d'), INTERVAL 12 MONTH) ")
          .append(" AND LAST_DAY(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d')) ");
      appendCounselorFilter(sb, p);
      sb.append(" GROUP BY month, counselor ")
          .append(" ORDER BY month ASC, counselor ASC ");
      return sb.toString();
    }

    public static String selectAdmissionTypeStats(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" DATE_FORMAT(STR_TO_DATE(cs_col_16, '%Y-%m-%d'), '%Y-%m') AS month, ")
          .append(buildDetailedTypeExpression("cs_col_08")).append(" AS type, ")
          .append(" TRIM(cs_col_17) AS counselor, ")
          .append(" COUNT(*) AS count ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      sb.append(" AND STR_TO_DATE(cs_col_16, '%Y-%m-%d') BETWEEN ")
          .append(" DATE_SUB(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d'), INTERVAL 12 MONTH) ")
          .append(" AND LAST_DAY(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d')) ");
      appendCounselorFilter(sb, p);
      sb.append(" GROUP BY month, type, counselor ")
          .append(" ORDER BY month ASC, type ASC, counselor ASC ");
      return sb.toString();
    }

    public static String selectAdmissionTypeSuccessStats(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" DATE_FORMAT(STR_TO_DATE(cs_col_16, '%Y-%m-%d'), '%Y-%m') AS month, ")
          .append(buildDetailedTypeExpression("cs_col_08")).append(" AS type, ")
          .append(" TRIM(cs_col_17) AS counselor, ")
          .append(" COUNT(*) AS count ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_19 = '입원완료' ")
          .append(" AND cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      sb.append(" AND STR_TO_DATE(cs_col_16, '%Y-%m-%d') BETWEEN ")
          .append(" DATE_SUB(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d'), INTERVAL 12 MONTH) ")
          .append(" AND LAST_DAY(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d')) ");
      appendCounselorFilter(sb, p);
      sb.append(" GROUP BY month, type, counselor ")
          .append(" ORDER BY month ASC, type ASC, counselor ASC ");
      return sb.toString();
    }

    public static String getCurrentLocationStats(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" DATE_FORMAT(STR_TO_DATE(cs_col_16, '%Y-%m-%d'), '%Y-%m') AS month, ")
          .append(buildDetailedTypeExpression("cs_col_07")).append(" AS type, ")
          .append(" TRIM(cs_col_17) AS counselor, ")
          .append(" COUNT(*) AS count ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      sb.append(" AND STR_TO_DATE(cs_col_16, '%Y-%m-%d') BETWEEN ")
          .append(" DATE_SUB(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d'), INTERVAL 12 MONTH) ")
          .append(" AND LAST_DAY(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d')) ");
      appendCounselorFilter(sb, p);
      sb.append(" GROUP BY month, type, counselor ")
          .append(" ORDER BY month ASC, type ASC, counselor ASC ");
      return sb.toString();
    }

    public static String getCurrentLocationSuccessStats(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" DATE_FORMAT(STR_TO_DATE(cs_col_16, '%Y-%m-%d'), '%Y-%m') AS month, ")
          .append(buildDetailedTypeExpression("cs_col_07")).append(" AS type, ")
          .append(" TRIM(cs_col_17) AS counselor, ")
          .append(" COUNT(*) AS count ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_19 = '입원완료' ")
          .append(" AND cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      sb.append(" AND STR_TO_DATE(cs_col_16, '%Y-%m-%d') BETWEEN ")
          .append(" DATE_SUB(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d'), INTERVAL 12 MONTH) ")
          .append(" AND LAST_DAY(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d')) ");
      appendCounselorFilter(sb, p);
      sb.append(" GROUP BY month, type, counselor ")
          .append(" ORDER BY month ASC, type ASC, counselor ASC ");
      return sb.toString();
    }

    public static String getNonAdmissionReasonStats(Map<String, Object> p) {
      String t = sanitizeInst((String) p.get("inst"));
      StringBuilder sb = new StringBuilder()
          .append("SELECT ")
          .append(" DATE_FORMAT(STR_TO_DATE(cs_col_16, '%Y-%m-%d'), '%Y-%m') AS month, ")
          .append(" TRIM(IFNULL(cs_col_20, '미지정')) AS type, ")
          .append(" COUNT(*) AS count ")
          .append("FROM csm.counsel_data_").append(t).append(" ")
          .append("WHERE cs_col_19 = '입원안함' ")
          .append(" AND cs_col_16 IS NOT NULL ");
      appendInstNameFilter(sb, p);
      sb.append(" AND STR_TO_DATE(cs_col_16, '%Y-%m-%d') BETWEEN ")
          .append(" DATE_SUB(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d'), INTERVAL 12 MONTH) ")
          .append(" AND LAST_DAY(STR_TO_DATE(CONCAT(#{year}, '-', LPAD(#{month},2,'0'), '-01'), '%Y-%m-%d')) ");
      appendCounselorFilter(sb, p);
      sb.append(" GROUP BY month, type ")
          .append(" ORDER BY month ASC, type ASC ");
      return sb.toString();
    }

    private static void appendCounselorFilter(StringBuilder sb, Map<String, Object> p) {
      Object counselor = p.get("counselor");
      if (counselor instanceof String c && !c.isBlank()) {
        sb.append(" AND TRIM(cs_col_17) = #{counselor} ");
      }
    }

    private static void appendInstNameFilter(StringBuilder sb, Map<String, Object> p) {
      Object instname = p.get("instname");
      if (instname instanceof String n && !n.isBlank()) {
        sb.append(" AND (cs_col_34 IS NULL OR cs_col_34 = #{instname}) ");
      }
    }

    private static String buildDetailedTypeExpression(String column) {
      return "CASE WHEN TRIM(IFNULL(" + column + ", '')) = '' THEN '미지정' " +
          "ELSE REPLACE(TRIM(" + column + "), ',', ' / ') END";
    }

    private static String sanitizeInst(String inst) {
      if (inst == null)
        throw new IllegalArgumentException("inst is null");
      String normalized = inst.trim();
      if (!normalized.matches("[A-Za-z0-9_]{2,20}"))
        throw new IllegalArgumentException("Invalid inst: " + inst);
      return normalized;
    }
  }

  /** core 기관 생성 시 DDL 전용 */
  class InstDdlSqlProvider {
    public static String createUserTableIfNotExists(Map<String, Object> p) {
      String inst = sanitizeInst((String) p.get("inst"));
      return """
          CREATE TABLE IF NOT EXISTS csm.user_data_""" + inst + """
          (
            us_col_01 INT NOT NULL AUTO_INCREMENT COMMENT '사용자코드',
            us_col_02 VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '아이디',
            us_col_03 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '사용자비번',
            us_col_04 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '소속회사코드',
            us_col_05 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '소속회사명',
            us_col_06 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '비고',
            us_col_07 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT 'y' COMMENT '사용상태',
            us_col_08 INT DEFAULT '1' COMMENT '권한',
            us_col_09 INT DEFAULT '1' COMMENT '1=사용,2=삭제',
            us_col_10 VARCHAR(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '연락처',
            us_col_11 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '이메일',
            us_col_12 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '이름',
            us_col_13 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '부서',
            us_col_14 VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '직급',
            PRIMARY KEY (us_col_01)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자정보'
          """;
    }

    private static String sanitizeInst(String inst) {
      if (inst == null)
        throw new IllegalArgumentException("inst is null");
      String normalized = inst.trim();
      if (!normalized.matches("[A-Za-z0-9_]{2,20}"))
        throw new IllegalArgumentException("Invalid inst: " + inst);
      return normalized;
    }
  }

}
