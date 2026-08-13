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
    void subJeonPrecisionRoundsHalfUp() {
        // 전 미만 자릿수는 폴백이 아니라 HALF_UP 반올림으로 근사값을 유지한다
        givenPrice("9.655", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(966);
    }

    @Test
    void subJeonPrecisionRoundsDownBelowHalf() {
        givenPrice("9.654", "30", "90");
        assertThat(service.unitCostJeon(INST, "sms")).isEqualTo(965);
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
}
