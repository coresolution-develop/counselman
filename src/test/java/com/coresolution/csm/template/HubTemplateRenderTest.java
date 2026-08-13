package com.coresolution.csm.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.HubCustomLink;
import com.coresolution.csm.vo.HubLinkView;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubLinkPresenter;

/**
 * 재설계된 허브 템플릿을 실제 Thymeleaf로 렌더해 표현식/프래그먼트/링크식 오류를 잡는다.
 * (기존 csm 템플릿 테스트는 문자열 검사만 하므로 런타임 렌더 오류를 못 잡음)
 */
class HubTemplateRenderTest {

    private static TemplateEngine engine;
    private static MockServletContext servletContext;
    private static JakartaServletWebApplication webApp;

    private final HubLinkPresenter presenter = new HubLinkPresenter();

    @BeforeAll
    static void setUp() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        servletContext = new MockServletContext();
        webApp = JakartaServletWebApplication.buildApplication(servletContext);
    }

    private WebContext baseContext() {
        // @{/...} 컨텍스트 상대 링크는 IWebContext가 필요하므로 목 서블릿 교환으로 WebContext를 만든다.
        MockHttpServletRequest req = new MockHttpServletRequest(servletContext);
        req.setContextPath("");
        IWebExchange exchange = webApp.buildExchange(req, new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange);
        // 표준 다이얼렉트에서 ${_csrf.token} 등은 Map property 접근으로 해석된다.
        ctx.setVariable("_csrf", Map.of("token", "tkn", "parameterName", "_csrf", "headerName", "X-CSRF-TOKEN"));
        return ctx;
    }

    /**
     * 허브 메인이 요구하는 모델 속성을 전부 빈 값으로 깔아둔다.
     * 컨트롤러는 항상 이 값들을 채우므로, 테스트가 일부만 세팅해 null로 깨지는 상황을 막는다.
     */
    private WebContext hubContext(HubMemberSession member) {
        WebContext ctx = baseContext();
        ctx.setVariable("hubMember", member);
        ctx.setVariable("linkRows", List.of());
        ctx.setVariable("linkGroupRows", new LinkedHashMap<String, List<HubLinkView>>());
        ctx.setVariable("categoryNav", List.of());
        ctx.setVariable("favoriteRows", List.of());
        ctx.setVariable("popularRows", List.of());
        ctx.setVariable("recentRows", List.of());
        ctx.setVariable("customRows", List.of());
        ctx.setVariable("customGroupRows", new LinkedHashMap<String, List<HubLinkView>>());
        ctx.setVariable("publicCount", 0);
        ctx.setVariable("customCount", 0);
        ctx.setVariable("prodCount", 0L);
        ctx.setVariable("devCount", 0L);
        ctx.setVariable("memo", "");
        return ctx;
    }

    private CompanyLink link(long id, String title, String url, String category, String desc) {
        CompanyLink l = new CompanyLink();
        l.setId(id);
        l.setTitle(title);
        l.setUrl(url);
        l.setCategory(category);
        l.setDescription(desc);
        return l;
    }

    /** 공용 링크 하나를 분류 그룹까지 포함해 컨텍스트에 심는다. */
    private void putPublicLink(WebContext ctx, CompanyLink l, boolean loggedIn, boolean favorite) {
        Set<Long> favIds = favorite ? Set.of(l.getId()) : Set.of();
        List<HubLinkView> rows = presenter.publicLinks(List.of(l), favIds, loggedIn);
        Map<String, List<HubLinkView>> groups = new LinkedHashMap<>();
        groups.put(l.getCategory(), rows);
        ctx.setVariable("linkRows", rows);
        ctx.setVariable("linkGroupRows", groups);
        ctx.setVariable("publicCount", rows.size());
    }

    private void putCustomLink(WebContext ctx, HubCustomLink c) {
        List<HubLinkView> rows = presenter.customLinks(List.of(c));
        Map<String, List<HubLinkView>> groups = new LinkedHashMap<>();
        groups.put(c.getCategory() == null ? "기타" : c.getCategory(), rows);
        ctx.setVariable("customRows", rows);
        ctx.setVariable("customGroupRows", groups);
        ctx.setVariable("customCount", rows.size());
    }

    @Test
    void companyLinks_loggedIn_rendersSidebarRowsAndStars() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        CompanyLink l = link(1L, "HARS", "https://hars-falh.sosyge.net/login", "병원", "병원 통합");
        putPublicLink(ctx, l, true, true);
        ctx.setVariable("favoriteRows", presenter.publicLinks(List.of(l), Set.of(1L), true));

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("hub-sidebar");            // 셸 프래그먼트 결합됨
        assertThat(html).contains("hub-nav__item--active");  // active=hub 표시
        assertThat(html).contains("이수민님");                 // 프로필 블록
        assertThat(html).contains("즐겨찾기");
        assertThat(html).contains("data-remove-on-unfav");   // 즐겨찾기 행 ★ 해제 시 제거
        assertThat(html).contains("lh-row");                 // 카드가 아닌 행 컴포넌트
        assertThat(html).contains("HARS");
        assertThat(html).contains("hars-falh.sosyge.net");   // host만 노출
        assertThat(html).contains("lh-fav--on");             // 즐겨찾기 active
        assertThat(html).contains("/hub/go/link/1");         // 로그인 시 추적 경유
        assertThat(html).contains("hubPalette");             // 커맨드 팔레트
    }

    /** 전체 URL은 화면 텍스트로 노출하지 않고 title 속성으로만 전달한다. */
    @Test
    void companyLinks_showsHostOnly_andKeepsFullUrlInTitle() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        putPublicLink(ctx, link(1L, "HARS", "https://hars-falh.sosyge.net/login/admin", "병원", null), true, false);

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("title=\"https://hars-falh.sosyge.net/login/admin\"");
        assertThat(html).doesNotContain(">https://hars-falh.sosyge.net/login/admin<");
    }

    /** 개발 서버는 색만이 아니라 라벨 + 점선 테두리(env-dev 클래스)로도 구분돼야 한다. */
    @Test
    void companyLinks_devHost_getsDevLabelAndDashedMarker() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        putPublicLink(ctx, link(2L, "예약 관리", "https://dev.sosyge.net/booking", "병원", null), true, false);

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("env-dev");
        assertThat(html).contains(">개발<");
    }

    /** 이름에 DEMO가 들어가면 운영으로 오인하지 않도록 DEMO로 표시한다. */
    @Test
    void companyLinks_demoName_getsDemoLabel() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        putPublicLink(ctx, link(3L, "ATS 군산 DEMO", "https://ats.example.com", "ATS 군산 DEMO", null), true, false);

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("env-demo");
        assertThat(html).contains(">DEMO<");
    }

    @Test
    void companyLinks_loggedIn_rendersMemoWithSavedContent() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        ctx.setVariable("memo", "월요일 배포 체크");

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("내 메모");
        assertThat(html).contains("/hub/me/memo");   // 저장 엔드포인트
        assertThat(html).contains("월요일 배포 체크"); // 저장된 내용 프리필
    }

    @Test
    void companyLinks_memoEscapesHtml_soStoredMarkupIsNotExecuted() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        ctx.setVariable("memo", "<script>alert(1)</script>");

        String html = engine.process("design/company-links", ctx);

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void companyLinks_anonymous_showsLoginAndNoStars() {
        WebContext ctx = hubContext(null);
        CompanyLink l = link(1L, "HARS", "https://hars-falh.sosyge.net/login", "병원", "병원 통합");
        putPublicLink(ctx, l, false, false);

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("hub-profile__login");  // 비로그인 → 로그인 버튼
        assertThat(html).doesNotContain("lh-fav");        // 별 미노출
        assertThat(html).doesNotContain("내 메모");         // 비로그인은 메모장 미렌더
        assertThat(html).contains("https://hars-falh.sosyge.net/login"); // 비로그인은 직접 URL
    }

    /** 예전 /hub/me(커스텀 링크 전용 화면)를 허브가 흡수했다 — 관리 폼까지 /links에서 렌더된다. */
    @Test
    void companyLinks_loggedIn_absorbsCustomLinksWithManageForms() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        HubCustomLink c = new HubCustomLink();
        c.setId(5L);
        c.setTitle("내 메모장");
        c.setUrl("https://memo.example.com");
        c.setMemo("업무");
        putCustomLink(ctx, c);

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("내 링크");
        assertThat(html).contains("customAddForm");
        assertThat(html).contains("/hub/me/custom-links");          // 추가 폼 action 보존
        assertThat(html).contains("/hub/me/custom-links/5");        // 수정 폼 action 보존
        assertThat(html).contains("/hub/me/custom-links/5/delete"); // 삭제 폼 action 보존
        assertThat(html).contains("edit-5");                        // 인라인 수정 토글 타겟
        assertThat(html).contains("/hub/go/custom/5");              // 커스텀 링크도 추적 경유
        assertThat(html).contains("hubSearch");                     // 팔레트 진입 검색바
    }

    /** 개인 링크 메모에 계정정보가 있으면 칩만 띄우고 값은 렌더하지 않는다. */
    @Test
    void companyLinks_accountMemo_showsChipWithoutValue() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        HubCustomLink c = new HubCustomLink();
        c.setId(5L);
        c.setTitle("사내 위키");
        c.setUrl("https://wiki.example.com");
        c.setMemo("계정 admin / 비밀번호 s3cret");
        putCustomLink(ctx, c);

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("lh-acct");
        // 목록 행에는 계정 값이 보이면 안 된다. 본인 수정 폼의 value 속성에 남는 것은 정상.
        assertThat(html).doesNotContain(">계정 admin / 비밀번호 s3cret<");
    }

    /** 메모/설명이 없는 링크의 data-* 에 "null"이 섞이면 검색이 전부 걸린다. */
    @Test
    void companyLinks_dataAttributes_omitNullForMissingMemoAndDescription() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        putPublicLink(ctx, link(1L, "HARS", "https://hars.example.com", "병원", null), true, false);
        HubCustomLink c = new HubCustomLink();
        c.setId(5L);
        c.setTitle("내 링크");
        c.setUrl("https://my.example.com");
        c.setMemo(null);
        putCustomLink(ctx, c);

        String html = engine.process("design/company-links", ctx);

        assertThat(html).doesNotContain("null");
    }

    @Test
    void companyLinks_emptyCustom_rendersAddPrompt() {
        WebContext ctx = hubContext(new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));

        String html = engine.process("design/company-links", ctx);

        assertThat(html).contains("자주 쓰는 개인 링크를 추가해보세요");
    }

    /** 비로그인은 개인 영역(내 링크 관리 폼·메모)이 전혀 렌더되지 않아야 한다. */
    @Test
    void companyLinks_anonymous_hasNoCustomLinkManageForms() {
        WebContext ctx = hubContext(null);

        String html = engine.process("design/company-links", ctx);

        assertThat(html).doesNotContain("내 링크");
        assertThat(html).doesNotContain("customAddForm");
        assertThat(html).doesNotContain("/hub/me/custom-links");
    }

    @Test
    void login_rendersBrandTonedFormWithRememberAndWarning() {
        WebContext ctx = baseContext();
        ctx.setVariable("email", "");

        String html = engine.process("hub/login", ctx);

        assertThat(html).contains("링크 허브");
        assertThat(html).contains("이 기기 기억하기");
        assertThat(html).contains("공용 단말");                 // 주의 문구
        assertThat(html).contains("name=\"remember\"");
        assertThat(html).contains("hub-btn--primary");
        assertThat(html).contains("/hub/login");
        assertThat(html).doesNotContain("👋");        // 이모지 없음
    }

    @Test
    void signup_rendersBrandTonedForm() {
        WebContext ctx = baseContext();
        ctx.setVariable("email", "");
        ctx.setVariable("name", "");

        String html = engine.process("hub/signup", ctx);

        assertThat(html).contains("회원가입");
        assertThat(html).contains("가입코드");
        assertThat(html).contains("/hub/signup");
        assertThat(html).contains("hub-btn--primary");
    }

    @Test
    void admin_rendersSidebarShellAndManagement() {
        WebContext ctx = baseContext();
        ctx.setVariable("hubMember", new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        CompanyLink l = link(1L, "HARS", "https://hars-falh.sosyge.net/login", "병원", "통합");
        ctx.setVariable("links", List.of(l));
        Map<String, List<CompanyLink>> groups = new LinkedHashMap<>();
        groups.put("병원", List.of(l));
        ctx.setVariable("linkGroups", groups);
        ctx.setVariable("categories", List.of(Map.of("category_name", "병원", "sort_order", 1)));

        String html = engine.process("design/company-links-admin", ctx);

        assertThat(html).contains("hub-sidebar");                 // 공통 셸
        assertThat(html).contains("서비스 링크 관리");
        assertThat(html).contains("/admin/company-links");        // 추가 폼 action
        assertThat(html).contains("/admin/company-links/category-order"); // 순서 저장
        assertThat(html).contains("/admin/company-links/1/delete");       // 삭제
        assertThat(html).contains("id=\"manageSearch\"");          // JS 훅 보존
        assertThat(html).contains("lh-cat-order");                 // 분류 순서 JS 훅
        // D1: 로그인 상태면 사이드바에 프로필(이름/로그아웃), 로그인 버튼 아님
        assertThat(html).contains("이수민님");
        assertThat(html).doesNotContain("hub-profile__login");
    }

    @Test
    void account_rendersPasswordAndDeviceManagement() {
        WebContext ctx = baseContext();
        ctx.setVariable("hubMember", new HubMemberSession(7L, "a@coresolution.kr", "이수민", "USER"));
        ctx.setVariable("customCount", 3);
        ctx.setVariable("memoLength", 154);

        String html = engine.process("hub/account", ctx);

        assertThat(html).contains("비밀번호 변경");
        assertThat(html).contains("개인 링크 3개 · 메모 154자");
        assertThat(html).contains("/hub/me/account/password");
        assertThat(html).contains("/hub/me/account/logout-all");
        assertThat(html).contains("hub-nav__item--active"); // active=account
    }
}
