package com.coresolution.csm.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.context.support.StaticApplicationContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.coresolution.csm.serivce.PlatformPriceCache.InstPriceStatus;
import com.coresolution.csm.vo.Instdata;
import com.coresolution.csm.web.PriceSourcePresenter;

/**
 * {@code /core/smssetting} 이 <b>정말 읽기 전용으로 렌더되는지</b> 확인한다 (CSM-2).
 *
 * <p>── 왜 렌더로 보나 ──
 * "버튼을 지웠다" 는 소스에서 지웠다는 뜻일 뿐이다. 프래그먼트가 다시 넣거나
 * 조건이 뒤집히면 화면에는 남는다. <b>렌더된 HTML 에 없어야</b> 지운 것이다.
 * ({@code CLAUDE.md} §3.5 — 화면 값은 렌더 결과로 확인한다)
 *
 * <p>이 테스트는 <b>화면</b>만 본다. 서버가 실제로 거부하는지는
 * {@code PageControllerPriceInsertGoneTest} 가 본다 — 화면만 막고 엔드포인트를
 * 두면 "막았다고 믿는데 안 막힌 상태" 가 되기 때문에 둘 다 필요하다.
 */
class SmsSettingTemplateRenderTest {

    private static final String TEMPLATE = "csm/core/admin/smssetting";
    private static final Instant NOW = Instant.parse("2026-08-27T09:00:00Z");
    private static final int STALE_MINUTES = 15;

    private static SpringTemplateEngine engine;
    private static StaticApplicationContext appContext;
    private static MockServletContext servletContext;
    private static JakartaServletWebApplication webApp;

    @BeforeAll
    static void setUp() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        // ── SpringTemplateEngine 을 쓰는 이유 ──
        // 공통 레이아웃이 `@moduleFeatureService.isEnabled(...)` 를 부른다.
        // 순수 Thymeleaf 엔진은 스프링 빈을 못 찾아 렌더가 통째로 실패한다.
        // **레이아웃을 빼고 렌더하면 "실제 화면" 을 본 것이 아니다.**
        appContext = new StaticApplicationContext();
        appContext.getBeanFactory().registerSingleton("moduleFeatureService", new StubModuleFeatures());
        appContext.refresh();

        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        servletContext = new MockServletContext();
        webApp = JakartaServletWebApplication.buildApplication(servletContext);
    }

    // ── 편집 UI 가 없다 ────────────────────────────────────────

    /**
     * ⭐ 단가 편집으로 이어지는 <b>모든 자국</b>이 화면에 없어야 한다.
     *
     * <p>버튼 하나만 지우고 모달을 남기면 CSS 나 다른 스크립트가 그걸 열 수 있다.
     * id 단위로 전부 확인한다.
     */
    @Test
    void 단가_편집_UI_가_화면에_없다() {
        String html = render();

        assertThat(html)
                .doesNotContain("modi-price")        // 단가수정 버튼
                .doesNotContain("new-price")         // 단가등록 버튼
                .doesNotContain("price-one-modal")   // 기관 단가 수정 모달
                .doesNotContain("price-all-modal")   // 전체 단가 등록 모달
                .doesNotContain("price-one-save")
                .doesNotContain("price-all-save");

        assertThat(html)
                .as("단가수정/단가등록 문구도 남지 않아야 한다")
                .doesNotContain("단가수정")
                .doesNotContain("단가등록");
    }

    /** 편집 전용 스크립트가 붙지 않는다. 파일 자체를 지웠으므로 붙으면 404 가 된다. */
    @Test
    void 편집_전용_스크립트를_읽지_않는다() {
        assertThat(render()).doesNotContain("core-smssetting.js");
    }

    /**
     * 단가 <b>표시</b>는 그대로다. 편집만 막는 것이지 값을 숨기는 것이 아니다.
     *
     * <p>숨기면 운영자가 "지금 얼마로 나가고 있는지" 를 csm 에서 못 본다.
     */
    @Test
    void 단가_값은_계속_보인다() {
        String html = render();

        assertThat(html).contains("9.6 / 30 / 90");
        assertThat(html).contains("통합테스트병원");
    }

    // ── 배너와 수신 상태 ──────────────────────────────────────

    @Test
    void 단가_출처를_화면에서_알려준다() {
        String html = render();

        assertThat(html).contains("MediCast");
        assertThat(html).contains("이 화면에서는 수정할 수 없습니다");
    }

    @Test
    void 정상_수신이면_버전과_경과_시간이_보인다() {
        String html = render(status(Duration.ofMinutes(3), 7));

        assertThat(html).contains("v7");
        assertThat(html).contains("3분 전");
        assertThat(html).doesNotContain("is-stale");
    }

    /**
     * ⭐ <b>폴링이 멈추면 화면이 달라져야 한다.</b>
     *
     * <p>"MediCast 가 관리합니다" 만 있으면 폴링이 멈춰도 화면이 똑같다.
     * 그러면 운영자는 낡은 단가로 청구되는 것을 알 수 없다.
     */
    @Test
    void 수신이_낡으면_화면에서_구분된다() {
        String normal = render(status(Duration.ofMinutes(3), 7));
        String stale = render(status(Duration.ofHours(3), 7));

        assertThat(normal).doesNotContain("is-stale");
        assertThat(stale)
                .as("낡은 상태가 정상과 같아 보이면 안 된다")
                .contains("is-stale")
                .contains("3시간 전")
                .contains("단가가 최신이 아닐 수 있습니다");
    }

    /**
     * 색만으로 구분하지 않는다 (WCAG 1.4.1).
     *
     * <p>경고 기호(⚠)가 텍스트로 들어가야 흑백 출력·색각 이상에서도 구분된다.
     */
    @Test
    void 낡은_상태를_색_말고도_표시한다() {
        assertThat(render(status(Duration.ofHours(3), 7)))
                .as("경고 기호가 있어야 색에 의존하지 않는다")
                .contains("⚠");
    }

    /** 수신 이력이 없는 기관도 행이 나온다. 빈칸이면 운영자가 이유를 추측하게 된다. */
    @Test
    void 수신_이력이_없어도_행이_나온다() {
        String html = render(PriceSourcePresenter.of(null, STALE_MINUTES, NOW));

        assertThat(html).contains("수신 이력 없음");
        assertThat(html).doesNotContain("is-stale");
        assertThat(html).contains("통합테스트병원");
    }

    /**
     * {@code priceStatus} 자체가 없어도 화면이 깨지지 않는다.
     *
     * <p>CSM-3 미배포 상태({@code platformPriceCache} 빈 없음)에서 실제로 이렇게 된다.
     */
    @Test
    void 수신_상태_모델이_없어도_렌더된다() {
        String html = render(ctx -> ctx.setVariable("priceStatus", null));

        assertThat(html).contains("통합테스트병원");
        assertThat(html).contains("수신 이력 없음");
    }

    // ── 도우미 ────────────────────────────────────────────────

    private PriceSourcePresenter.View status(Duration ago, Integer version) {
        return PriceSourcePresenter.of(
                new InstPriceStatus(version, NOW.minus(ago)), STALE_MINUTES, NOW);
    }

    private String render() {
        return render(status(Duration.ofMinutes(3), 7));
    }

    private String render(PriceSourcePresenter.View view) {
        return render(ctx -> ctx.setVariable("priceStatus", Map.of("COHS", view)));
    }

    private String render(java.util.function.Consumer<WebContext> customizer) {
        MockHttpServletRequest req = new MockHttpServletRequest(servletContext);
        req.setContextPath("");
        IWebExchange exchange = webApp.buildExchange(req, new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange);

        // SpEL 이 `@bean` 을 찾으려면 이 변수가 있어야 한다.
        ctx.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(appContext, null));
        ctx.setVariable("_csrf", Map.of("token", "tkn", "parameterName", "_csrf", "headerName", "X-CSRF-TOKEN"));
        ctx.setVariable("info", null);
        ctx.setVariable("list", List.of(inst("COHS", "통합테스트병원", "9.6", "30", "90")));
        ctx.setVariable("instCount", 1L);
        ctx.setVariable("priceStatus", new LinkedHashMap<String, PriceSourcePresenter.View>());

        customizer.accept(ctx);
        return engine.process(TEMPLATE, ctx);
    }

    /** 레이아웃이 부르는 기능 토글. 화면 구조만 보면 되므로 전부 끈 상태로 둔다. */
    public static class StubModuleFeatures {
        public boolean isEnabled(String instCode, String featureCode) {
            return false;
        }
    }

    private Instdata inst(String code, String name, String sms, String lms, String mms) {
        Instdata d = new Instdata();
        d.setId_col_02(name);
        d.setId_col_03(code);
        d.setId_col_04("정상");
        d.setSms_price(sms);
        d.setLms_price(lms);
        d.setMms_price(mms);
        return d;
    }
}
