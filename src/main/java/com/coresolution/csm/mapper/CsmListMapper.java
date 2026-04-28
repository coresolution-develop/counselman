package com.coresolution.csm.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.springframework.lang.NonNull;

import com.coresolution.csm.vo.OrderedItem;

@Mapper
public interface CsmListMapper {

    @DeleteProvider(type = Sql.class, method = "deleteAll")
    int deleteAll(@Param("inst") String inst);

    /** 단건 insert (컨트롤러 loop 유지 시 사용) */
    @InsertProvider(type = Sql.class, method = "insertOne")
    int insertOne(@Param("inst") String inst, @Param("item") OrderedItem item);

    /** 배치 insert (권장) */
    @InsertProvider(type = Sql.class, method = "insertBatch")
    int insertBatch(@Param("inst") String inst, @Param("items") List<OrderedItem> items);

    /** 상담일지관리 설정 JSON 조회 */
    @SelectProvider(type = Sql.class, method = "selectLogSettings")
    String selectLogSettings(@Param("inst") String inst);

    /** 상담일지관리 설정 JSON 삭제 */
    @DeleteProvider(type = Sql.class, method = "deleteLogSettings")
    int deleteLogSettings(@Param("inst") String inst);

    /** 상담일지관리 설정 JSON 저장 */
    @InsertProvider(type = Sql.class, method = "insertLogSettings")
    int insertLogSettings(@Param("inst") String inst, @Param("json") String json);

    // -------- SQL 빌더 --------
    class Sql {
        private static String s(@NonNull String inst) {
            // inst 화이트리스팅: 영문/숫자/언더스코어만 허용
            if (!inst.matches("[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException("Invalid inst: " + inst);
            }
            return inst;
        }

        public static String deleteAll(Map<String, Object> params, ProviderContext ctx) {
            String inst = (String) params.get("inst");
            return "DELETE FROM csm.counsel_list_" + s(inst) + " WHERE coulmn != '__LOG_SETTINGS__'";
        }

        public static String insertOne(Map<String, Object> params, ProviderContext ctx) {
            String inst = (String) params.get("inst");
            // 주의: DB 컬럼명은 'coulmn' (오타지만 스키마에 맞춤)
            return "INSERT INTO csm.counsel_list_" + s(inst) +
                    " (coulmn, comment, turn, view_yn) " +
                    "VALUES (#{item.column}, #{item.comment}, #{item.turn}, #{item.viewYn})";
        }

        public static String selectLogSettings(Map<String, Object> params, ProviderContext ctx) {
            String inst = (String) params.get("inst");
            return "SELECT comment FROM csm.counsel_list_" + s(inst) +
                    " WHERE coulmn = '__LOG_SETTINGS__' LIMIT 1";
        }

        public static String deleteLogSettings(Map<String, Object> params, ProviderContext ctx) {
            String inst = (String) params.get("inst");
            return "DELETE FROM csm.counsel_list_" + s(inst) +
                    " WHERE coulmn = '__LOG_SETTINGS__'";
        }

        public static String insertLogSettings(Map<String, Object> params, ProviderContext ctx) {
            String inst = (String) params.get("inst");
            return "INSERT INTO csm.counsel_list_" + s(inst) +
                    " (coulmn, comment, turn, view_yn) VALUES ('__LOG_SETTINGS__', #{json}, 0, 'y')";
        }

        public static String insertBatch(Map<String, Object> params, ProviderContext ctx) {
            String inst = (String) params.get("inst");
            List<?> items = (List<?>) params.get("items");

            if (items == null || items.isEmpty()) {
                // 빈 insert 방지
                return "SELECT 1";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("INSERT INTO csm.counsel_list_").append(s(inst))
                    .append(" (coulmn, comment, turn, view_yn) VALUES ");

            for (int i = 0; i < items.size(); i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append("(#{items[").append(i).append("].column}, ")
                        .append("#{items[").append(i).append("].comment}, ")
                        .append("#{items[").append(i).append("].turn}, ")
                        .append("#{items[").append(i).append("].viewYn})");
            }
            return sb.toString();
        }
    }

}
