package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.csm.vo.Instdata;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 단가 파싱(원 → 전) 검증.
 *
 * <p>실서버 단가는 9.6원(=960전)이라 소수점 경로가 과금 정확성에 직결된다.
 * 금액에 double/float 를 쓰지 않고 BigDecimal → 전 단위 정수로만 다룬다.
 */
class SmsServicePriceTest {

    private static final String INST = "COHS";

    private SmsService service;
    private ListAppender<ILoggingEvent> logs;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        service = spy(new SmsService());
        ReflectionTestUtils.setField(service, "fallbackSmsJeon", 960);
        ReflectionTestUtils.setField(service, "fallbackLmsJeon", 3000);
        ReflectionTestUtils.setField(service, "fallbackMmsJeon", 9000);

        serviceLogger = (Logger) LoggerFactory.getLogger(SmsService.class);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
    }

    private void givenPrice(String sms, String lms, String mms) {
        Instdata d = new Instdata();
        d.setId_col_03(INST);
        d.setSms_price(sms);
        d.setLms_price(lms);
        d.setMms_price(mms);
        doReturn(List.of(d)).when(service).price(INST);
    }

    private boolean warnedWithInstCode() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("[sms-price]")
                        && e.getFormattedMessage().contains("inst=" + INST));
    }

    // ── 소수점 단가 (실서버 경로) ────────────────────────────────

    @Test
    void decimalPriceConvertsToJeon() {
        givenPrice("9.6", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
    }

    @Test
    void twoDecimalPlacesConvertExactly() {
        givenPrice("9.65", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(965);
    }

    // ── 정수 단가 (개발서버 경로) ────────────────────────────────

    @Test
    void integerPricesConvertToJeon() {
        givenPrice("9", "12", "110");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(900);
        assertThat(service.unitCostJeon(INST, "lms")).isEqualTo(1200);
        assertThat(service.unitCostJeon(INST, "mms")).isEqualTo(11000);
    }

    // ── 반올림 규칙 ──────────────────────────────────────────────

    @Test
    void subJeonPrecisionIsRejected() {
        // **CSM-3 에서 HALF_UP 근사를 제거했다.**
        //
        // 9.655 원은 965.5전인데 전 미만은 표현할 수 없다. 예전에는 966전으로
        // 반올림했지만, 그러면 고객이 입력한 값과 실제 차감액이 갈린다 —
        // 그건 표시 버그가 아니라 요금 분쟁이다.
        //
        // 이제 거부하고 폴백 단가를 쓴다. 플랫폼 parseUnitPrice() 와 같은
        // 규칙이다 (pricing-vectors.json 의 P02).
        givenPrice("9.655", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960); // 폴백
        assertThat(warnedWithInstCode()).isTrue();
    }

    @Test
    void subJeonPrecisionIsRejectedBelowHalfToo() {
        // 반올림 방향과 무관하다. 전 미만 자릿수가 있으면 무조건 거부한다.
        givenPrice("9.654", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960); // 폴백
    }

    @Test
    void twoDecimalPlacesAreAccepted() {
        // 소수 2자리까지는 전 단위로 정확히 표현된다.
        givenPrice("9.65", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(965);
    }

    @Test
    void trailingZerosDoNotCountAsPrecision() {
        // "9.60" 은 소수 2자리지만 유효 자릿수는 1이다 — 통과해야 한다.
        // 플랫폼 벡터 P10 과 같은 케이스.
        givenPrice("9.60", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
    }

    @Test
    void manyTrailingZerosStillPass() {
        // "9.6000" 도 손실이 없으므로 통과한다 (벡터 P11).
        givenPrice("9.6000", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
    }

    // ── 폴백 경로 ────────────────────────────────────────────────

    @Test
    void emptyPriceFallsBackWithWarn() {
        givenPrice("", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
        assertThat(warnedWithInstCode()).isTrue();
    }

    @Test
    void nullPriceFallsBackWithWarn() {
        // 개발서버의 HSFH/HSJH/TEST/SLOM 처럼 단가가 비어 있는 기관 경로
        givenPrice(null, null, null);
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
        assertThat(service.unitCostJeon(INST, "lms")).isEqualTo(3000);
        assertThat(service.unitCostJeon(INST, "mms")).isEqualTo(9000);
        assertThat(warnedWithInstCode()).isTrue();
    }

    @Test
    void nonNumericPriceFallsBackWithWarn() {
        givenPrice("20원", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
        assertThat(warnedWithInstCode()).isTrue();
    }

    @Test
    void negativePriceFallsBackWithWarn() {
        givenPrice("-5", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
        assertThat(warnedWithInstCode()).isTrue();
    }

    @Test
    void noPriceRowFallsBackWithWarn() {
        doReturn(List.of()).when(service).price(INST);
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
        assertThat(warnedWithInstCode()).isTrue();
    }

    @Test
    void lookupFailureFallsBackWithoutPropagating() {
        doReturn(null).when(service).price(INST);
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
        assertThat(warnedWithInstCode()).isTrue();
    }

    @Test
    void validPriceDoesNotWarn() {
        givenPrice("9.6", "30", "90");
        service.unitCostJeon(INST, "sms");
        assertThat(warnedWithInstCode()).isFalse();
    }

    // ── CSM-4: 단가와 버전을 같은 자리에서 꺼낸다 ────────────────

    /**
     * ⭐ <b>기존 발송이 그대로 동작한다.</b>
     *
     * <p>{@code unitCostJeon} 은 CSM-4 에서 {@code unitPrice} 로 위임하게 바뀌었다.
     * 발송 경로가 쓰는 값이므로 <b>금액이 한 전도 달라지면 안 된다.</b>
     */
    @Test
    void CSM4_이후에도_단가는_그대로다() {
        stubPrice("9.6", "30", "90");

        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(960);
        assertThat(service.unitCostJeon(INST, "lms")).isEqualTo(3000);
        assertThat(service.unitCostJeon(INST, "mms")).isEqualTo(9000);
    }

    /** {@code unitPrice} 의 금액이 {@code unitCostJeon} 과 같아야 한다 — 두 경로가 갈리면 안 된다. */
    @Test
    void unitPrice_금액이_unitCostJeon_과_같다() {
        stubPrice("12.34", "30", "90");

        for (String type : new String[] { "sms", "lms", "mms" }) {
            assertThat(service.unitPrice(INST, type).jeon())
                    .as(type)
                    .isEqualTo(service.unitCostJeon(INST, type));
        }
    }

    /**
     * 2단계 폴백에서는 미러된 버전이 따라온다.
     *
     * <p>이 값이 사용량 이벤트로 회신된다 — 플랫폼이 "적용됐다고 믿은 버전" 과
     * 실제 과금 버전을 대조하는 근거다.
     */
    @Test
    void 이단계_폴백은_미러된_버전을_함께_돌려준다() {
        stubPrice("9.6", "30", "90", 11);

        assertThat(service.unitPrice(INST, "sms").version()).isEqualTo(11);
    }

    /**
     * ⭐ 3단계 폴백은 버전이 {@code null} 이다.
     *
     * <p>오류가 아니라 <b>플랫폼 단가를 못 받고 발송했다</b>는 정보다.
     * 0 이나 -1 로 채우면 "못 받았다" 와 "0번 버전" 이 섞인다.
     */
    @Test
    void 삼단계_폴백은_버전이_없다() {
        stubPrice(null, null, null);

        var price = service.unitPrice(INST, "sms");
        assertThat(price.jeon()).isEqualTo(960);
        assertThat(price.version()).isNull();
    }

    /** 미러 버전이 없는 기존 기관(연동 전 설정값)도 버전 없이 정상 동작한다. */
    @Test
    void 미러_버전이_없어도_단가는_나온다() {
        stubPrice("9.6", "30", "90", null);

        var price = service.unitPrice(INST, "sms");
        assertThat(price.jeon()).isEqualTo(960);
        assertThat(price.version()).isNull();
    }

    private void stubPrice(String sms, String lms, String mms) {
        stubPrice(sms, lms, mms, null);
    }

    private void stubPrice(String sms, String lms, String mms, Integer version) {
        Instdata d = new Instdata();
        d.setId_col_03(INST);
        d.setSms_price(sms);
        d.setLms_price(lms);
        d.setMms_price(mms);
        d.setSms_price_version(version);
        doReturn(List.of(d)).when(service).price(INST);
    }
}
