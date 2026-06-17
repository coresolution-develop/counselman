package com.coresolution.csm.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.coresolution.csm.vo.Criteria;
import org.junit.jupiter.api.Test;

/**
 * 상담 목록 조회 SQL 빌더(CounselSqlProvider) 회귀 테스트.
 *
 * 배경: 리스트 페이지가 첫 30건만 받아 "전체기간"·"최근 90일" 선택 시 31번째부터 보이지 않던 버그.
 * 해결: 목록은 LIMIT 페이지네이션(무한 스크롤)을 유지하고, 월별 캘린더는 그 달만 fetchAll로 조회한다.
 * 상단 빠른필터(오늘/미완료)는 quickFilter 술어로 서버에서 처리한다.
 */
class CounselSqlProviderListTest {

    /** end="on" → 완료 포함(완료 제외 술어 비활성), dateRange="all" → 날짜 술어 없음 → quickFilter 술어를 단독 검증. */
    private Criteria baseCri() {
        Criteria cri = new Criteria();
        cri.setInst("demo");
        cri.setDateRange("all");
        cri.setEnd("on");
        return cri;
    }

    @Test
    void searchCounselData_appliesLimitByDefault() {
        // 목록(무한 스크롤) 경로는 페이지네이션(LIMIT)을 유지한다.
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(baseCri());
        assertThat(sql).contains("LIMIT #{pageStart}, #{perPageNum}");
    }

    @Test
    void searchCounselData_skipsLimitWhenFetchAll() {
        // 월별 캘린더는 그 달 전체를 받아야 하므로 LIMIT이 없어야 한다.
        Criteria cri = baseCri();
        cri.setFetchAll(true);
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(cri);
        assertThat(sql).doesNotContain("LIMIT");
    }

    @Test
    void searchCounselData_quickFilterAll_addsNoExtraPredicate() {
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(baseCri());
        assertThat(sql).doesNotContain("CURDATE()");
        assertThat(sql).doesNotContain("입원완료");
    }

    @Test
    void searchCounselData_quickFilterToday_filtersByToday() {
        Criteria cri = baseCri();
        cri.setQuickFilter("today");
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(cri);
        assertThat(sql).contains("STR_TO_DATE(c.cs_col_16, '%Y-%m-%d') = CURDATE()");
    }

    @Test
    void searchCounselData_quickFilterIncomplete_excludesAdmitted() {
        Criteria cri = baseCri();
        cri.setQuickFilter("incomplete");
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(cri);
        assertThat(sql).contains("c.cs_col_19 != '입원완료'");
    }

    @Test
    void countCounselList_honorsQuickFilter() {
        // 상단 빠른필터 건수가 목록과 동일 조건으로 집계되도록 count 쿼리도 quickFilter를 반영한다.
        Criteria cri = baseCri();
        cri.setQuickFilter("today");
        String sql = CsmMapper.CounselSqlProvider.countCounselList(cri);
        assertThat(sql).contains("STR_TO_DATE(c.cs_col_16, '%Y-%m-%d') = CURDATE()");
    }

    @Test
    void searchCounselData_presetRange_appliesIntervalFilter() {
        // "최근 90일" 등 preset은 INTERVAL 필터를 적용한다.
        Criteria cri = baseCri();
        cri.setDateRange("90");
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(cri);
        assertThat(sql).contains("DATE_SUB(CURDATE(), INTERVAL #{dateRange} DAY)");
    }
}
