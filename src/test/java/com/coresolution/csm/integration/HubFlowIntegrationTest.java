package com.coresolution.csm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.csm.serivce.CompanyLinkService;
import com.coresolution.csm.serivce.HubCustomLinkService;
import com.coresolution.csm.serivce.HubFavoriteService;
import com.coresolution.csm.serivce.HubHistoryService;
import com.coresolution.csm.serivce.HubMemberService;
import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.HubHistoryView;
import com.coresolution.csm.vo.HubMemberSession;

/**
 * 실제 MySQL을 대상으로 허브 개인화 전체 플로우를 검증하는 통합 테스트.
 * (가입→로그인→★→개인페이지→커스텀→최근사용 중복제거→비밀번호 변경)
 *
 * <p>환경변수 HUB_IT_JDBC_URL 이 설정된 경우에만 실행되고, 없으면 skip 한다(CI/로컬 안전).
 * 예: HUB_IT_JDBC_URL=jdbc:mysql://127.0.0.1:3399/csm?... HUB_IT_USER=csdev HUB_IT_PASS=...
 */
class HubFlowIntegrationTest {

    private static JdbcTemplate jdbcTemplate;

    private static HubMemberService memberService;
    private static CompanyLinkService companyLinkService;
    private static HubFavoriteService favoriteService;
    private static HubCustomLinkService customLinkService;
    private static HubHistoryService historyService;

    @BeforeAll
    static void connect() {
        String url = System.getenv("HUB_IT_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), "HUB_IT_JDBC_URL 미설정 — 통합 테스트 skip");

        // 단일 물리 커넥션을 재사용한다: 순차 단일 스레드 테스트에서 @Transactional의
        // "한 작업=한 커넥션" 보장을 흉내내, LAST_INSERT_ID()가 같은 커넥션에서 보이도록 한다.
        SingleConnectionDataSource ds = new SingleConnectionDataSource(
                url,
                System.getenv().getOrDefault("HUB_IT_USER", "csdev"),
                System.getenv().getOrDefault("HUB_IT_PASS", ""),
                true);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");

        jdbcTemplate = new JdbcTemplate(ds);
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException e) {
            assumeTrue(false, "DB 연결 실패 — skip: " + e.getMessage());
        }

        memberService = new HubMemberService(jdbcTemplate);
        ReflectionTestUtils.setField(memberService, "signupCode", "core");
        companyLinkService = new CompanyLinkService(jdbcTemplate);
        favoriteService = new HubFavoriteService(jdbcTemplate, memberService);
        customLinkService = new HubCustomLinkService(jdbcTemplate, memberService);
        historyService = new HubHistoryService(jdbcTemplate, memberService);
    }

    @Test
    void fullPersonalizationFlow() {
        // 0) 4테이블 생성 (멱등)
        memberService.ensureTables();
        for (String table : List.of("hub_member", "hub_member_favorite", "hub_member_custom_link", "hub_member_link_history")) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'csm' AND table_name = ?",
                    Integer.class, table);
            assertThat(exists).as("table %s created", table).isEqualTo(1);
        }
        System.out.println("[1] 4개 테이블 생성 확인 OK");

        String email = "it_" + System.nanoTime() + "@coresolution.kr";

        // 1) 가입 (가입코드 core)
        assertThatThrownBy(() -> memberService.signup(email, "pw1", "통합테스트", "wrong-code"))
                .hasMessageContaining("가입코드");
        HubMemberSession signed = memberService.signup(email, "pw1", "통합테스트", "core");
        assertThat(signed.getId()).isPositive();
        long memberId = signed.getId();
        System.out.println("[2] 가입 OK (가입코드 오류 거부 → core 성공), memberId=" + memberId);

        // 2) 로그인 (오답 거부 → 정답 성공)
        assertThat(memberService.authenticate(email, "wrong")).isNull();
        assertThat(memberService.authenticate(email, "pw1")).isNotNull();
        System.out.println("[3] 로그인 OK (오답 거부 → 정답 성공)");

        // 공용 링크 시드 (관리자 큐레이션 가정)
        long linkId = companyLinkService.createLink("HARS", "https://hars.example.com", "병원 HARS", "병원", 1, "it");

        // 3) ★ 즐겨찾기 토글
        assertThat(favoriteService.toggle(memberId, linkId)).isTrue();
        assertThat(favoriteService.listFavoriteLinkIds(memberId)).contains(linkId);
        assertThat(favoriteService.toggle(memberId, linkId)).isFalse();   // 해제
        assertThat(favoriteService.toggle(memberId, linkId)).isTrue();    // 다시 등록
        // 가드레일 ③: 존재하지 않는 링크는 거부
        assertThatThrownBy(() -> favoriteService.toggle(memberId, 99999999L))
                .hasMessageContaining("사용할 수 없는 링크");
        System.out.println("[4] ★ 토글 OK (등록/해제/재등록 + 유령링크 거부)");

        // 4) 개인 페이지 — 즐겨찾기 조인 조회
        List<CompanyLink> favorites = favoriteService.listFavorites(memberId);
        assertThat(favorites).extracting(CompanyLink::getId).contains(linkId);
        System.out.println("[5] 개인페이지 즐겨찾기 조회 OK (" + favorites.size() + "건)");

        // 5) 커스텀 링크 — http(s)만 허용
        assertThatThrownBy(() -> customLinkService.create(memberId, "위험", "javascript:alert(1)", null, 0))
                .hasMessageContaining("http 또는 https");
        long customId = customLinkService.create(memberId, "내 메모장", "https://memo.example.com", "업무 메모", 0);
        assertThat(customLinkService.listOwn(memberId)).extracting("id").contains(customId);
        // 타인(다른 memberId)은 수정 불가
        assertThat(customLinkService.update(customId, memberId + 1, "탈취", "https://evil.example.com", null, 0)).isFalse();
        System.out.println("[6] 커스텀 링크 OK (javascript: 거부 + 생성 + 타인 수정 차단)");

        // 6) 최근 사용 — 같은 url 중복 클릭 + 다수 클릭 → url별 1건, 최신순 상위 8
        historyService.record(memberId, "PUBLIC", linkId, null, "HARS", "https://hars.example.com");
        historyService.record(memberId, "PUBLIC", linkId, null, "HARS", "https://hars.example.com"); // 동일 url 재클릭(동초 가능)
        historyService.record(memberId, "CUSTOM", null, customId, "내 메모장", "https://memo.example.com");
        for (int i = 0; i < 10; i++) {
            historyService.record(memberId, "PUBLIC", null, null, "기타" + i, "https://other.example.com/" + i);
        }
        // HARS를 한 번 더 클릭 → 최신으로 올라와야 함
        historyService.record(memberId, "PUBLIC", linkId, null, "HARS", "https://hars.example.com");

        List<HubHistoryView> recent = historyService.listRecent(memberId);
        assertThat(recent).hasSizeLessThanOrEqualTo(8);
        // 중복 제거: 같은 url이 두 번 나오지 않는다
        assertThat(recent).extracting(HubHistoryView::getUrl).doesNotHaveDuplicates();
        // HARS는 정확히 1번만, 그리고 마지막 재클릭으로 맨 앞
        assertThat(recent).filteredOn(v -> "https://hars.example.com".equals(v.getUrl())).hasSize(1);
        assertThat(recent.get(0).getUrl()).isEqualTo("https://hars.example.com");
        System.out.println("[7] 최근사용 중복제거 OK (총 " + recent.size() + "건, url 중복 없음, 재클릭 최상단)");
        recent.forEach(v -> System.out.println("     - " + v.getAccessedAt() + "  " + v.getTitle() + "  " + v.getUrl()));

        // 7) 비밀번호 변경 — 현재 비번 확인 후 교체
        assertThatThrownBy(() -> memberService.changePassword(memberId, "wrong", "pw2"))
                .hasMessageContaining("현재 비밀번호");
        memberService.changePassword(memberId, "pw1", "pw2");
        assertThat(memberService.authenticate(email, "pw1")).isNull();        // 옛 비번 불가
        assertThat(memberService.authenticate(email, "pw2")).isNotNull();     // 새 비번 성공
        System.out.println("[8] 비밀번호 변경 OK (옛 비번 거부 → 새 비번 로그인 성공)");

        // 정리
        cleanup(memberId, linkId);
        System.out.println("[9] 전체 플로우 통과 ✅");
    }

    private void cleanup(long memberId, long linkId) {
        try {
            jdbcTemplate.update("DELETE FROM csm.hub_member_link_history WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM csm.hub_member_favorite WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM csm.hub_member_custom_link WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM csm.hub_member WHERE id = ?", memberId);
            jdbcTemplate.update("DELETE FROM csm.company_link WHERE id = ?", linkId);
        } catch (RuntimeException ignored) {
            // 임시 컨테이너라 정리는 best-effort
        }
    }
}
