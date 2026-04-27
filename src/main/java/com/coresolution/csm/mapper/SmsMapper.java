package com.coresolution.csm.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

import com.coresolution.csm.vo.Criteria;
import com.coresolution.csm.vo.SmsTemplate;

@Mapper
public interface SmsMapper {

    @Select("""
                        SELECT * FROM csm.message_templates_${inst}
            order by created_at desc
                    """)
    @Results(id = "SmsTemplateMap", value = {
            @Result(column = "id", property = "id", id = true, jdbcType = JdbcType.INTEGER),
            @Result(column = "title", property = "title", jdbcType = JdbcType.VARCHAR),
            @Result(column = "template", property = "template", jdbcType = JdbcType.VARCHAR),
            @Result(column = "created_at", property = "created_at", jdbcType = JdbcType.TIMESTAMP)
    })
    List<SmsTemplate> SelectTemplateView(SmsTemplate st);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM csm.message_templates_${inst}
             <if test="keyword != null and keyword != ''">
                WHERE title LIKE CONCAT('%', #{keyword}, '%')
                   OR template LIKE CONCAT('%', #{keyword}, '%')
             </if>
            </script>
            """)
    int SelectTemplateCnt(Criteria cri);

    @Select("""
            <script>
            SELECT *
              FROM csm.message_templates_${inst}
             <if test="keyword != null and keyword != ''">
                WHERE title LIKE CONCAT('%', #{keyword}, '%')
                   OR template LIKE CONCAT('%', #{keyword}, '%')
             </if>
             ORDER BY created_at DESC
             LIMIT #{pageStart}, #{perPageNum}
            </script>
            """)
    @Results(value = {
            @Result(column = "id", property = "id", id = true, jdbcType = JdbcType.INTEGER),
            @Result(column = "title", property = "title", jdbcType = JdbcType.VARCHAR),
            @Result(column = "template", property = "template", jdbcType = JdbcType.VARCHAR),
            @Result(column = "created_at", property = "created_at", jdbcType = JdbcType.TIMESTAMP)
    })
    List<SmsTemplate> SelectTemplate(Criteria cri);

    @Insert("""
            INSERT INTO csm.message_templates_${inst} (title, template, created_at)
            VALUES (#{title}, #{template}, NOW())
            """)
    int smsInsert(SmsTemplate st);

    @Update("""
            UPDATE csm.message_templates_${inst}
               SET title = #{title},
                   template = #{template}
             WHERE id = #{id}
            """)
    int smsUpdate(SmsTemplate st);

    @Delete("""
            DELETE FROM csm.message_templates_${inst}
             WHERE id = #{id}
            """)
    int smsDelete(SmsTemplate st);

    @Insert("""
            INSERT INTO csm.transmission_history_${inst}
                (contents, from_phone, to_phone, status, response, refkey, created_at, send_type, reserve_time)
            VALUES
                (#{contents}, #{fromPhone}, #{toPhone}, #{status}, #{responseString}, #{refkey}, NOW(), #{sendType}, #{reserveTime})
            """)
    int insertTransmissionHistory(
            @Param("inst") String inst,
            @Param("contents") String contents,
            @Param("fromPhone") String fromPhone,
            @Param("toPhone") String toPhone,
            @Param("status") String status,
            @Param("responseString") String responseString,
            @Param("refkey") String refkey,
            @Param("sendType") String sendType,
            @Param("reserveTime") java.time.LocalDateTime reserveTime);

    @Select("""
            <script>
            SELECT contents, to_phone, created_at, status, response, send_type
              FROM csm.transmission_history_${inst}
             <if test="toPhones != null and toPhones.size() > 0">
              WHERE REPLACE(to_phone, '-', '') IN
              <foreach item="phone" collection="toPhones" open="(" separator="," close=")">
                REPLACE(#{phone}, '-', '')
              </foreach>
             </if>
             ORDER BY created_at DESC
             LIMIT 200
            </script>
            """)
    List<Map<String, Object>> getSmsLogs(
            @Param("inst") String inst,
            @Param("toPhones") List<String> toPhones);
}
