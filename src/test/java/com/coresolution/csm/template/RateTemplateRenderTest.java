package com.coresolution.csm.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.coresolution.csm.util.JeonFormat;

/**
 * {@code /rate} 화면을 <b>실제 템플릿으로 렌더해서</b> 표시 금액을 확인한다.
 *
 * <p>── 왜 계산 함수 테스트로는 부족한가 ──
 * 이 수정 전에 나는 "{@code 3 × 9.6 = 28.799999999999997} 이 화면에 그대로 나간다" 고
 * 진단했다. <b>틀린 진단이었다.</b> 템플릿이
 * {@code #numbers.formatDecimal(x, 1, 'COMMA', 0, 'POINT')} 로 소수점 0자리 반올림을
 * 하므로 화면에는 {@code 29원} 으로 나갔다.
 *
 * <p>원인은 <b>계산 함수만 보고 화면을 판단한 것</b>이다. 표시 계층에서 한 번 더
 * 변환이 일어나는데 그 단계를 안 봤다. 그래서 이 테스트는 모델 값이 아니라
 * <b>렌더된 HTML 문자열</b>을 본다.
 *
 * <p>── 그래서 안 고쳐도 됐나 ──
 * 아니다. {@link #볼륨_할인_단가에서는_화면_표시가_실제로_갈린다()} 가 그 이유다.
 * 정확한 값이 반올림 경계({@code .5})에 걸리고 double 이 그 아래로 떨어지면
 * 표시가 1원 갈린다. 현재 단가(9.6/30/90)에서 그 조합이 없을 뿐이다.
 */
class RateTemplateRenderTest {

    private static final String TEMPLATE = "design/rate-management";

    /** 운영 단가. 전 단위 정수. */
    private static final long PROD_SMS = 960;
    private static final long PROD_LMS = 3000;
    private static final long PROD_MMS = 9000;

    /** 볼륨 할인이 배포되면 들어올 수 있는 소수 단가. */
    private static final long VOL_SMS = 870;   // 8.7원
    private static final long VOL_LMS = 2990;  // 29.9원
    private static final long VOL_MMS = 8950;  // 89.5원

    private static TemplateEngine engine;
    private static MockServletContext servletContext;
    private static JakartaServletWebApplication webApp;

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

    // ── 1. 화면이 수정 전후 동일한가 (검증 기준) ────────────────────

    /**
     * ⭐ <b>이것이 이번 수정의 합격 기준이다.</b>
     *
     * <p>운영 단가에서 33개 조합(3채널 × 11건수)을 렌더해,
     * 전 단위 정수 계산(수정 후)과 double 계산(수정 전)의
     * <b>화면 출력이 한 글자도 다르지 않음</b>을 확인한다.
     *
     * <p>즉 이 수정은 <b>운영 화면을 바꾸지 않는다.</b> 회귀 위험이 없다는 뜻이다.
     */
    @Test
    void 운영_단가에서는_수정_전후_화면이_동일하다() {
        List<String> mismatches = new ArrayList<>();

        for (int count : COUNTS) {
            String after = renderSmsAmount(PROD_SMS, count);
            String before = renderSmsAmountLegacy(PROD_SMS, count);
            if (!after.equals(before)) {
                mismatches.add("SMS×" + count + ": 전=" + before + " 후=" + after);
            }

            after = renderLmsAmount(PROD_LMS, count);
            before = renderLmsAmountLegacy(PROD_LMS, count);
            if (!after.equals(before)) {
                mismatches.add("LMS×" + count + ": 전=" + before + " 후=" + after);
            }

            after = renderMmsAmount(PROD_MMS, count);
            before = renderMmsAmountLegacy(PROD_MMS, count);
            if (!after.equals(before)) {
                mismatches.add("MMS×" + count + ": 전=" + before + " 후=" + after);
            }
        }

        assertThat(mismatches)
                .as("운영 단가 9.6/30/90 에서 화면 표시가 달라진 조합")
                .isEmpty();
    }

    /**
     * 손으로 계산한 표시 금액과 렌더 결과가 같아야 한다.
     *
     * <p>기대값은 코드 출력이 아니라 <b>{@code 단가(전) × 건수 ÷ 100} 을 반올림한 값</b>이다.
     * 예: {@code 960 × 3 = 2880전 = 28.8원 → 29원}.
     */
    @Test
    void 운영_단가_표시_금액이_손으로_계산한_값과_같다() {
        assertThat(renderSmsAmount(PROD_SMS, 0)).isEqualTo("0원");
        assertThat(renderSmsAmount(PROD_SMS, 1)).isEqualTo("10원");      // 9.6  → 10
        assertThat(renderSmsAmount(PROD_SMS, 2)).isEqualTo("19원");      // 19.2 → 19
        assertThat(renderSmsAmount(PROD_SMS, 3)).isEqualTo("29원");      // 28.8 → 29
        assertThat(renderSmsAmount(PROD_SMS, 333)).isEqualTo("3,197원"); // 3196.8
        assertThat(renderSmsAmount(PROD_SMS, 12345)).isEqualTo("118,512원");

        assertThat(renderLmsAmount(PROD_LMS, 333)).isEqualTo("9,990원");
        assertThat(renderMmsAmount(PROD_MMS, 12345)).isEqualTo("1,111,050원");
    }

    // ── 2. 볼륨 할인 대조표 (회귀 감시) ─────────────────────────────

    /**
     * ⭐ <b>수정이 필요했던 이유.</b>
     *
     * <p>SMS 8.7원 × 12,345건 = <b>107,401.5원</b> 이다. 정확히 반올림 경계다.
     * <ul>
     *   <li>전 단위 정수: {@code 870 × 12345 = 10,740,150전} → {@code 107,401.5원} → <b>107,402원</b></li>
     *   <li>double: {@code 8.7} 은 {@code 8.6999999999999992894...} 이므로
     *       곱하면 {@code 107,401.49999...} → <b>107,401원</b></li>
     * </ul>
     *
     * <p>1원 차이지만 <b>청구서에 찍히는 숫자</b>다. 소수 단가가 실제로 들어오면
     * 이 테스트가 회귀를 잡는다.
     */
    @Test
    void 볼륨_할인_단가에서는_화면_표시가_실제로_갈린다() {
        assertThat(renderSmsAmount(VOL_SMS, 12345)).isEqualTo("107,402원");
        assertThat(renderSmsAmountLegacy(VOL_SMS, 12345)).isEqualTo("107,401원");
    }

    /**
     * 볼륨 할인 단가 33개 조합의 표시 금액 대조표.
     *
     * <p>기대값은 전부 {@code 단가(전) × 건수 ÷ 100} 을 손으로 반올림한 값이다.
     *
     * <p><b>{@code .5} 는 짝수 쪽으로 간다(HALF_EVEN).</b> Thymeleaf 의
     * {@code formatDecimal} 은 {@code DecimalFormat} 을 쓰고, 그 기본
     * 반올림이 HALF_EVEN 이다 — {@code 43.5 → 44}, 그러나 {@code 268.5 → 268}.
     *
     * <p>이건 이 테스트가 잡아낸 사실이다. 나는 처음에 HALF_UP({@code 269})으로
     * 적었고 렌더 결과가 {@code 268} 이었다. <b>계산 함수 테스트였다면 몰랐을 것이다.</b>
     */
    @Test
    void 볼륨_할인_대조표() {
        // SMS 8.7원
        assertThat(renderSmsAmount(VOL_SMS, 0)).isEqualTo("0원");
        assertThat(renderSmsAmount(VOL_SMS, 1)).isEqualTo("9원");        // 8.7
        assertThat(renderSmsAmount(VOL_SMS, 2)).isEqualTo("17원");       // 17.4
        assertThat(renderSmsAmount(VOL_SMS, 3)).isEqualTo("26원");       // 26.1
        assertThat(renderSmsAmount(VOL_SMS, 5)).isEqualTo("44원");       // 43.5  → 44 (짝수 쪽)
        assertThat(renderSmsAmount(VOL_SMS, 7)).isEqualTo("61원");       // 60.9
        assertThat(renderSmsAmount(VOL_SMS, 10)).isEqualTo("87원");
        assertThat(renderSmsAmount(VOL_SMS, 100)).isEqualTo("870원");
        assertThat(renderSmsAmount(VOL_SMS, 333)).isEqualTo("2,897원");  // 2897.1
        assertThat(renderSmsAmount(VOL_SMS, 1000)).isEqualTo("8,700원");
        assertThat(renderSmsAmount(VOL_SMS, 12345)).isEqualTo("107,402원"); // 107401.5 → 107402 (짝수 쪽)

        // LMS 29.9원
        assertThat(renderLmsAmount(VOL_LMS, 1)).isEqualTo("30원");       // 29.9
        assertThat(renderLmsAmount(VOL_LMS, 2)).isEqualTo("60원");       // 59.8
        assertThat(renderLmsAmount(VOL_LMS, 3)).isEqualTo("90원");       // 89.7
        assertThat(renderLmsAmount(VOL_LMS, 5)).isEqualTo("150원");      // 149.5 → 150 (짝수 쪽)
        assertThat(renderLmsAmount(VOL_LMS, 7)).isEqualTo("209원");      // 209.3
        assertThat(renderLmsAmount(VOL_LMS, 10)).isEqualTo("299원");
        assertThat(renderLmsAmount(VOL_LMS, 333)).isEqualTo("9,957원");  // 9956.7
        assertThat(renderLmsAmount(VOL_LMS, 12345)).isEqualTo("369,116원"); // 369115.5 → 369116 (짝수 쪽)

        // MMS 89.5원
        assertThat(renderMmsAmount(VOL_MMS, 1)).isEqualTo("90원");       // 89.5  → 90 (짝수 쪽)
        assertThat(renderMmsAmount(VOL_MMS, 2)).isEqualTo("179원");
        assertThat(renderMmsAmount(VOL_MMS, 3)).isEqualTo("268원");      // 268.5 → 268 (짝수 쪽)
        assertThat(renderMmsAmount(VOL_MMS, 5)).isEqualTo("448원");      // 447.5 → 448 (짝수 쪽)
        assertThat(renderMmsAmount(VOL_MMS, 7)).isEqualTo("626원");      // 626.5 → 626 (짝수 쪽)
        assertThat(renderMmsAmount(VOL_MMS, 10)).isEqualTo("895원");
        assertThat(renderMmsAmount(VOL_MMS, 333)).isEqualTo("29,804원"); // 29803.5 → 29804 (짝수 쪽)
        assertThat(renderMmsAmount(VOL_MMS, 12345)).isEqualTo("1,104,878원"); // 1104877.5 → 1104878 (짝수 쪽)
    }

    /**
     * ⚠️ <b>페이지 안에서 반올림 규칙이 두 갈래다.</b> (기존 동작, 이번 수정과 무관)
     *
     * <ul>
     *   <li>대부분의 금액 → 템플릿의 {@code formatDecimal} → <b>HALF_EVEN</b></li>
     *   <li>월별 표의 <b>합계</b> 열 → 컨트롤러가 미리 포맷 → <b>HALF_UP</b>
     *       (원래 {@code Math.round}, 즉 {@code floor(x+0.5)} 였다)</li>
     * </ul>
     *
     * <p>{@code 268.5원} 이면 한 화면에서 {@code 268} 과 {@code 269} 가 같이 보인다.
     * 현재 단가(9.6/30/90)에서는 {@code .5} 가 안 나오므로 드러나지 않는다.
     * <b>소수 단가가 들어오면 드러난다.</b>
     *
     * <p>이번 수정 범위 밖이라 동작을 유지했다 — 어느 쪽으로 통일할지는
     * 청구액에 영향을 주는 판단이므로 별도 티켓(원 단위 반올림)에서 정한다.
     * 여기서는 <b>현재 상태를 고정</b>해 두어, 티켓 처리 시 이 테스트가 같이 바뀌게 한다.
     */
    @Test
    void 합계_열과_나머지_열의_반올림_규칙이_다르다() {
        // MMS 89.5원 × 3건 = 268.5원. 같은 값을 두 경로로 렌더한다.
        assertThat(renderMmsAmount(VOL_MMS, 3)).isEqualTo("268원");   // 템플릿 HALF_EVEN

        String html = render(ctx -> ctx.setVariable("monthlyBilling",
                List.of(monthRow("2026-07", 0, 0, 3))));
        assertThat(html).contains("269원");                            // 컨트롤러 HALF_UP
    }

    // ── 3. 월별 청구서 표 ─────────────────────────────────────────

    /**
     * 월별 표는 {@code totalText} 를 컨트롤러가 미리 포맷해서 넣는다.
     * 표시 계층이 두 갈래이므로 <b>여기도 렌더로 확인한다.</b>
     */
    @Test
    void 월별_청구서_표의_합계가_손으로_계산한_값과_같다() {
        // SMS 12,345건 × 8.7원 = 107,401.5원 → 107,402원
        // LMS      3건 × 29.9원 =      89.7원 →      90원
        // 합계 107,491.2원 → 107,491원
        String html = render(ctx -> {
            ctx.setVariable("monthlyBilling", List.of(monthRow("2026-07", 12345, 3, 0)));
        });

        assertThat(html).contains("107,491원");
        assertThat(html).contains("2026-07");
    }

    @Test
    void 월별_청구서가_비면_안내_문구가_나온다() {
        String html = render(ctx -> ctx.setVariable("monthlyBilling", List.of()));
        assertThat(html).contains("청구서 조회 내역이 없습니다");
    }

    // ── 렌더 도우미 ────────────────────────────────────────────────

    private static final int[] COUNTS = { 0, 1, 2, 3, 5, 7, 10, 100, 333, 1000, 12345 };

    /**
     * 수정 <b>후</b>: 전 단위 정수로 곱하고 {@link BigDecimal} 로 모델에 넣는다.
     * 컨트롤러가 하는 것과 같은 경로다.
     */
    private String renderSmsAmount(long unitJeon, int count) {
        return renderAmount("smsTotal", JeonFormat.toWonDecimal(JeonFormat.multiply(unitJeon, count)));
    }

    private String renderLmsAmount(long unitJeon, int count) {
        return renderAmount("lmsTotal", JeonFormat.toWonDecimal(JeonFormat.multiply(unitJeon, count)));
    }

    private String renderMmsAmount(long unitJeon, int count) {
        return renderAmount("mmsTotal", JeonFormat.toWonDecimal(JeonFormat.multiply(unitJeon, count)));
    }

    /**
     * 수정 <b>전</b>: {@code double} 단가 × 건수. 옛 코드를 그대로 재현한다.
     *
     * <p>옛 코드가 지워졌으므로 여기서 재현하지 않으면 대조가 불가능하다.
     * 단가는 {@code parseDouble(price.getSms_price())} 로 원 단위 문자열에서 왔다.
     */
    private String renderSmsAmountLegacy(long unitJeon, int count) {
        return renderAmount("smsTotal", legacy(unitJeon, count));
    }

    private String renderLmsAmountLegacy(long unitJeon, int count) {
        return renderAmount("lmsTotal", legacy(unitJeon, count));
    }

    private String renderMmsAmountLegacy(long unitJeon, int count) {
        return renderAmount("mmsTotal", legacy(unitJeon, count));
    }

    private static double legacy(long unitJeon, int count) {
        double unitWon = Double.parseDouble(JeonFormat.toWon(unitJeon));
        return unitWon * count;
    }

    /** 채널별 사용량 막대에서 {@code N건 · M원} 의 금액 부분만 뽑는다. */
    private String renderAmount(String variable, Object value) {
        String html = render(ctx -> ctx.setVariable(variable, value));
        String channel = switch (variable) {
            case "lmsTotal" -> "LMS";
            case "mmsTotal" -> "MMS";
            default -> "SMS";
        };
        Matcher m = Pattern
                .compile("<strong>" + channel + "</strong><span>[^·]*· ([^<]*)</span>")
                .matcher(html);
        assertThat(m.find())
                .as("렌더 결과에서 %s 금액을 못 찾았다. 템플릿 구조가 바뀌었는지 확인할 것:%n%s",
                        channel, html)
                .isTrue();
        return m.group(1).trim();
    }

    /**
     * 템플릿이 요구하는 모델을 전부 0으로 깔고, 필요한 것만 덮어쓴다.
     *
     * <p>일부만 세팅하면 나머지가 null 로 깨져서 <b>렌더 오류를 금액 오류로 착각</b>한다.
     */
    private String render(java.util.function.Consumer<WebContext> customizer) {
        MockHttpServletRequest req = new MockHttpServletRequest(servletContext);
        req.setContextPath("");
        IWebExchange exchange = webApp.buildExchange(req, new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange);

        BigDecimal zero = BigDecimal.ZERO;
        for (String v : new String[] {
                "smsPrice", "lmsPrice", "mmsPrice",
                "smsTotal", "lmsTotal", "mmsTotal", "total",
                "smsTotalThisMonth", "lmsTotalThisMonth", "mmsTotalThisMonth", "thisMonthTotal" }) {
            ctx.setVariable(v, zero);
        }
        for (String v : new String[] {
                "smsCount", "lmsCount", "mmsCount",
                "smsThisMonth", "lmsThisMonth", "mmsThisMonth",
                "totalThisMonth", "totalLastMonth" }) {
            ctx.setVariable(v, 0);
        }
        ctx.setVariable("monthlyBilling", List.of());
        ctx.setVariable("_csrf", Map.of("token", "tkn", "parameterName", "_csrf", "headerName", "X-CSRF-TOKEN"));

        customizer.accept(ctx);
        return engine.process(TEMPLATE, ctx);
    }

    /** 컨트롤러의 {@code buildMonthlyBillingRows} 와 같은 모양의 행. */
    private Map<String, Object> monthRow(String month, int sms, int lms, int mms) {
        long smsJeon = JeonFormat.multiply(VOL_SMS, sms);
        long lmsJeon = JeonFormat.multiply(VOL_LMS, lms);
        long mmsJeon = JeonFormat.multiply(VOL_MMS, mms);
        long totalJeon = smsJeon + lmsJeon + mmsJeon;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("month", month);
        row.put("sms", sms);
        row.put("lms", lms);
        row.put("mms", mms);
        row.put("smsPrice", JeonFormat.toWonDecimal(VOL_SMS));
        row.put("lmsPrice", JeonFormat.toWonDecimal(VOL_LMS));
        row.put("mmsPrice", JeonFormat.toWonDecimal(VOL_MMS));
        row.put("total", JeonFormat.toWonDecimal(totalJeon));
        row.put("totalText",
                java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA)
                        .format(JeonFormat.toWonDecimal(totalJeon)
                                .setScale(0, java.math.RoundingMode.HALF_UP))
                        + "원");
        return row;
    }
}
