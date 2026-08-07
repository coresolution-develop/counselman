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
        // 행 수는 fetchLimit — probeExtra면 perPageNum+1을 읽어 hasMore를 직접 판정한다.
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(baseCri());
        assertThat(sql).contains("LIMIT #{pageStart}, #{fetchLimit}");
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

    /**
     * 목록과 카운트가 동일한 검색 술어를 써야 한다.
     *
     * 이전에는 목록이 부분일치(AES_DECRYPT LIKE)인데 카운트는 이름 전체 해시 정확일치라,
     * "김" 같은 부분검색이면 목록엔 첫 페이지가 뜨지만 카운트가 0이 되어 무한 스크롤이
     * 31번째부터 영구히 멈추고 빠른필터 배지도 0으로 찍혔다.
     */
    @Test
    void listAndCount_shareSameKeywordPredicate() {
        for (String type : new String[] { "patient", "guardian", "phone", "counselor", "content", "" }) {
            Criteria cri = baseCri();
            cri.setSearchType(type);
            cri.setKeyword("김");
            String list = CsmMapper.CounselSqlProvider.searchCounselData(cri);
            String count = CsmMapper.CounselSqlProvider.countCounselList(cri);

            // LIMIT/ORDER BY만 다르고 검색 술어는 동일해야 한다. (공백 차이는 무시)
            String listPredicate = squashSpaces(list.substring(list.indexOf("WHERE 1=1"), list.indexOf("ORDER BY")));
            String countPredicate = squashSpaces(count.substring(count.indexOf("WHERE 1=1")));
            assertThat(countPredicate).as("searchType=%s", type).isEqualTo(listPredicate);
        }
    }

    private String squashSpaces(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    @Test
    void patientSearch_usesPartialMatchNotFullNameHash() {
        Criteria cri = baseCri();
        cri.setSearchType("patient");
        cri.setKeyword("김");
        // 카운트도 부분일치여야 한다. 해시 정확일치로 돌아가면 안 된다.
        String count = CsmMapper.CounselSqlProvider.countCounselList(cri);
        assertThat(count).contains("AES_DECRYPT(UNHEX(c.cs_col_01), #{aesKey}) LIKE");
        assertThat(count).doesNotContain("cs_col_01_hash");
    }

    @Test
    void phoneSearch_matchesPartialDigitsRegardlessOfLength() {
        Criteria cri = baseCri();
        cri.setSearchType("phone");
        cri.setKeyword("12345678"); // 4자리도 전체번호도 아닌 부분 입력
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(cri);
        // 저장된 번호의 하이픈/공백을 제거한 뒤 부분일치로 비교해야 한다.
        assertThat(sql).contains("LIKE CONCAT('%', #{keyword}, '%')");
        assertThat(sql).contains("'-', ''");
        // 해시는 정확일치 경로로 함께 남아 있어야 한다(OR 조건).
        assertThat(sql).contains("g.contact_number_hash = #{keywordBytes}");
        // 4자리 전용 RIGHT/MID 특수 분기는 더 이상 필요 없다.
        assertThat(sql).doesNotContain("RIGHT(AES_DECRYPT");
    }

    @Test
    void unknownSearchType_stillFiltersByKeyword() {
        // 이전에는 no-op이라 URL 직접 진입 시 검색어가 조용히 무시됐다.
        Criteria cri = baseCri();
        cri.setSearchType("");
        cri.setKeyword("김");
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(cri);
        assertThat(sql).contains("AES_DECRYPT(UNHEX(c.cs_col_01), #{aesKey}) LIKE");
    }

    @Test
    void noKeyword_addsNoKeywordPredicate() {
        Criteria cri = baseCri();
        cri.setSearchType("patient");
        String sql = CsmMapper.CounselSqlProvider.searchCounselData(cri);
        assertThat(sql).doesNotContain("AES_DECRYPT");
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
