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
import com.coresolution.csm.serivce.HubRememberService;
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
    private static HubRememberService rememberService;

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
        rememberService = new HubRememberService(jdbcTemplate, memberService);
        ReflectionTestUtils.setField(rememberService, "rememberDays", 30);
        ReflectionTestUtils.setField(rememberService, "cookieSecure", true);
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

    @Test
    void rememberMeFlow_restoresSessionFromCookieOnly_andRotates() {
        memberService.ensureTables();
        String email = "rm_" + System.nanoTime() + "@coresolution.kr";
        long memberId = memberService.signup(email, "pw1", "기억테스트", "core").getId();
        System.out.println("[R1] 회원 생성 memberId=" + memberId);

        // 1) "기억하기" 토큰 발급 (로그인 시 발급되는 쿠키 값)
        String a0 = rememberService.issue(memberId, "JUnit-UA");
        assertThat(a0).contains(":");
        System.out.println("[R2] 영속 토큰 발급 (쿠키 selector:validator)");

        // 2) 세션이 전혀 없는 상태에서 쿠키만으로 재로그인 → memberId 복원 + 토큰 회전
        HubRememberService.Result r1 = rememberService.validateAndRotate(a0, "JUnit-UA");
        assertThat(r1.valid()).isTrue();
        assertThat(r1.memberId()).isEqualTo(memberId);
        String a1 = r1.newCookieValue();
        assertThat(a1).isNotEqualTo(a0);
        System.out.println("[R3] 세션 없이 쿠키만으로 재로그인 OK (memberId 복원 + 토큰 회전)");

        // 3) 회전된 새 쿠키로 계속 사용 가능 (정상 슬라이딩 체인)
        HubRememberService.Result r2 = rememberService.validateAndRotate(a1, "JUnit-UA");
        assertThat(r2.valid()).isTrue();
        assertThat(r2.newCookieValue()).isNotEqualTo(a1);
        System.out.println("[R4] 회전 체인 지속 OK");

        // 4) 로그아웃: 현재 기기 토큰 삭제 → 이후 검증 무효
        rememberService.deleteByCookie(r2.newCookieValue());
        assertThat(rememberService.validateAndRotate(r2.newCookieValue(), "JUnit-UA").valid()).isFalse();
        System.out.println("[R5] 로그아웃(토큰 삭제) 후 재로그인 불가 OK");

        // 5) 도난 감지: 옛 쿠키(이미 회전됨)를 다시 제시하면 validator 불일치 → 토큰 체인 전체 폐기.
        //    그 후 회전됐던 새 쿠키도 무효가 되어 강제 재로그인을 유도한다.
        String b0 = rememberService.issue(memberId, "UA");
        String b1 = rememberService.validateAndRotate(b0, "UA").newCookieValue();
        assertThat(rememberService.validateAndRotate(b0, "UA").valid()).isFalse();   // 옛(도난) 쿠키 재사용
        assertThat(rememberService.validateAndRotate(b1, "UA").valid()).isFalse();   // 체인 폐기됨
        System.out.println("[R6] 도난 감지 OK (옛 쿠키 재사용 시 토큰 체인 폐기)");

        // 6) 비활성 회원은 복원 거부
        String c0 = rememberService.issue(memberId, "UA");
        jdbcTemplate.update("UPDATE csm.hub_member SET status = 'DISABLED' WHERE id = ?", memberId);
        assertThat(rememberService.validateAndRotate(c0, "UA").valid()).isFalse();
        System.out.println("[R7] 비활성(DISABLED) 회원 복원 거부 OK");

        jdbcTemplate.update("DELETE FROM csm.hub_member_token WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM csm.hub_member WHERE id = ?", memberId);
        System.out.println("[R8] remember-me 플로우 통과 ✅ (인증 근거가 DB 토큰+쿠키 → 재시작에도 유지)");
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
