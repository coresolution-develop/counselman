package com.coresolution.csm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class SmsRefkeyTest {

    @Test
    void formatProducesParsableRefkey() {
        String refkey = SmsRefkey.format("COHS", 123456L);
        assertThat(refkey).isEqualTo("MP-COHS-123456");

        Optional<SmsRefkey.Parsed> parsed = SmsRefkey.parse(refkey);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().instCode()).isEqualTo("COHS");
        assertThat(parsed.get().historyId()).isEqualTo(123456L);
    }

    @Test
    void parseAcceptsUnderscoreAndLongInstCodes() {
        Optional<SmsRefkey.Parsed> parsed = SmsRefkey.parse("MP-HSOP_0001-42");
        assertThat(parsed).isPresent();
        assertThat(parsed.get().instCode()).isEqualTo("HSOP_0001");
        assertThat(parsed.get().historyId()).isEqualTo(42L);
    }

    @Test
    void parseRejectsLegacyFormat() {
        // 구형식 {INST}{yyyyMMddHHmmss}{rand4} 는 폴백 경로로 가야 한다
        assertThat(SmsRefkey.parse("COHS202608121230001234")).isEmpty();
    }

    @Test
    void parseRejectsOtpLegacyFormat() {
        assertThat(SmsRefkey.parse("pwd-otp-COHS-1-1755000000000")).isEmpty();
    }

    @Test
    void parseRejectsMalformedInput() {
        assertThat(SmsRefkey.parse(null)).isEmpty();
        assertThat(SmsRefkey.parse("")).isEmpty();
        assertThat(SmsRefkey.parse("MP-")).isEmpty();
        assertThat(SmsRefkey.parse("MP-COHS-")).isEmpty();
        assertThat(SmsRefkey.parse("MP-COHS-abc")).isEmpty();
        assertThat(SmsRefkey.parse("MP--123")).isEmpty();
        assertThat(SmsRefkey.parse("MP-한글-123")).isEmpty();
    }
}
