package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmsMessageTypeResolverTest {

    private final SmsMessageTypeResolver resolver = new SmsMessageTypeResolver();

    @Test
    void asciiCountsOneBytePerChar() {
        assertThat(resolver.countBytes("hello")).isEqualTo(5);
    }

    @Test
    void koreanCountsTwoBytesPerChar() {
        // 기존 화면 로직과 동일: char > 127 → 2바이트
        assertThat(resolver.countBytes("안녕")).isEqualTo(4);
    }

    @Test
    void ninetyBytesIsSms() {
        String msg = "a".repeat(90);
        SmsMessageTypeResolver.Resolved resolved = resolver.resolve(msg);
        assertThat(resolved.type()).isEqualTo("sms");
        assertThat(resolved.bytes()).isEqualTo(90);
        assertThat(resolved.subject()).isNull();
    }

    @Test
    void ninetyOneBytesIsLms() {
        String msg = "a".repeat(91);
        SmsMessageTypeResolver.Resolved resolved = resolver.resolve(msg);
        assertThat(resolved.type()).isEqualTo("lms");
        assertThat(resolved.subject()).isEqualTo("a".repeat(20));
    }

    @Test
    void koreanBoundaryMatchesLegacyScreens() {
        // 한글 45자 = 90바이트 → SMS, 46자 = 92바이트 → LMS
        assertThat(resolver.resolve("가".repeat(45)).type()).isEqualTo("sms");
        assertThat(resolver.resolve("가".repeat(46)).type()).isEqualTo("lms");
    }

    @Test
    void subjectIsFullMessageWhenShorterThanTwentyChars() {
        String msg = "짧은 장문 " + "a".repeat(85);
        SmsMessageTypeResolver.Resolved resolved = resolver.resolve(msg);
        assertThat(resolved.type()).isEqualTo("lms");
        assertThat(resolved.subject()).isEqualTo(msg.substring(0, 20));
    }

    @Test
    void overTwoThousandBytesIsRejected() {
        assertThatThrownBy(() -> resolver.resolve("a".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void blankMessageIsRejected() {
        assertThatThrownBy(() -> resolver.resolve("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
