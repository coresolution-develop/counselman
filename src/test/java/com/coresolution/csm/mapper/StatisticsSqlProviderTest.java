package com.coresolution.csm.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 상담통계 SQL 빌더(StatisticsSqlProvider) 회귀 테스트.
 *
 * 배경: 통계 쿼리에 `AND (cs_col_34 IS NULL OR cs_col_34 = #{instname})` 소속기관명 필터가 있었는데,
 * 입원상담 폼이 같은 컬럼에 코로나 여부('N')를 저장하면서 2026-06 이후 상담이 통계에서 전부 제외됐다.
 * 상담 데이터는 이미 기관별 테이블(counsel_data_{inst})로 분리돼 있어 이 필터는 필요 없다.
 */
class StatisticsSqlProviderTest {

    private Map<String, Object> params() {
        Map<String, Object> p = new HashMap<>();
        p.put("inst", "demo");
        p.put("instname", "데모요양병원");
        p.put("year", 2026);
        p.put("month", 8);
        return p;
    }

    private List<Function<Map<String, Object>, String>> allBuilders() {
        return List.of(
                CsmMapper.StatisticsSqlProvider::getCounselDateRange,
                CsmMapper.StatisticsSqlProvider::getMonthlyCounselStatistics,
                CsmMapper.StatisticsSqlProvider::getTypeStatistics,
                CsmMapper.StatisticsSqlProvider::selectAdmissionSuccessStats,
                CsmMapper.StatisticsSqlProvider::selectAdmissionTypeStats,
                CsmMapper.StatisticsSqlProvider::selectAdmissionTypeSuccessStats,
                CsmMapper.StatisticsSqlProvider::getCurrentLocationStats,
                CsmMapper.StatisticsSqlProvider::getCurrentLocationSuccessStats,
                CsmMapper.StatisticsSqlProvider::getNonAdmissionReasonStats);
    }

    @Test
    void allStatisticsQueries_doNotFilterByInstName() {
        // instname을 넘겨도 cs_col_34 술어가 붙지 않아야 한다.
        allBuilders().forEach(builder -> {
            String sql = builder.apply(params());
            assertThat(sql).doesNotContain("cs_col_34");
            assertThat(sql).doesNotContain("#{instname}");
        });
    }

    @Test
    void dateRange_scansWholeInstitutionTable() {
        // 년/월 드롭다운 상한을 정하는 쿼리도 필터 없이 전체 기간을 봐야 한다.
        String sql = CsmMapper.StatisticsSqlProvider.getCounselDateRange(params());
        assertThat(sql).contains("MAX(STR_TO_DATE(cs_col_16, '%Y-%m-%d')) AS last_date");
        assertThat(sql).contains("FROM csm.counsel_data_demo");
        assertThat(sql).doesNotContain("cs_col_34");
    }

    @Test
    void counselorFilter_stillApplies() {
        // 상담자 필터는 그대로 동작해야 한다(제거 대상 아님).
        Map<String, Object> p = params();
        p.put("counselor", "홍길동");
        String sql = CsmMapper.StatisticsSqlProvider.getMonthlyCounselStatistics(p);
        assertThat(sql).contains("TRIM(cs_col_17) = #{counselor}");
    }
}
