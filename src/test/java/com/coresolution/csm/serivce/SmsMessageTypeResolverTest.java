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

    // ── 2026-08-14 회귀: 제목에 줄바꿈이 들어가면 비즈뿌리오가 9020으로 즉시 거부한다 ──

    @Test
    void subjectCollapsesLineBreaksAndTabs() {
        assertThat(resolver.subjectFor("a\nb\tc  d")).isEqualTo("a b c d");
        assertThat(resolver.subjectFor("a\r\nb")).isEqualTo("a b");
    }

    @Test
    void subjectFromMultilineMessageHasNoLineBreak() {
        // 실제 유실된 본문 형태: 짧은 인사말 + 줄바꿈으로 시작
        String msg = "건강하세요~\n효사랑가족요양병원 입니다.\n필요서류 안내드립니다.\n" + "가".repeat(200);
        SmsMessageTypeResolver.Resolved resolved = resolver.resolve(msg);

        assertThat(resolved.type()).isEqualTo("lms");
        assertThat(resolved.subject())
                .doesNotContain("\n")
                .doesNotContain("\r")
                .doesNotContain("\t")
                .hasSizeLessThanOrEqualTo(SmsMessageTypeResolver.LMS_SUBJECT_LENGTH)
                .startsWith("건강하세요~ 효사랑");
    }

    @Test
    void subjectHasNoTrailingSpaceWhenCutAtWordBoundary() {
        String msg = "1234567890123456789 뒤에도 내용이 계속 이어집니다 " + "a".repeat(200);
        assertThat(resolver.resolve(msg).subject()).doesNotEndWith(" ");
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
