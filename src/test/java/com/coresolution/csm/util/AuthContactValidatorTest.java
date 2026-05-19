package com.coresolution.csm.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthContactValidatorTest {

    // ─── 이메일 검증 ──────────────────────────────────

    @Test
    void isValidEmail_acceptsStandardAddresses() {
        assertThat(AuthContactValidator.isValidEmail("alice@gmail.com")).isTrue();
        assertThat(AuthContactValidator.isValidEmail("user.name+tag@sub.example.co.kr")).isTrue();
        assertThat(AuthContactValidator.isValidEmail("a@b.io")).isTrue();
        assertThat(AuthContactValidator.isValidEmail("  trim@me.com  ")).isTrue();
    }

    @Test
    void isValidEmail_rejectsMalformed() {
        assertThat(AuthContactValidator.isValidEmail(null)).isFalse();
        assertThat(AuthContactValidator.isValidEmail("")).isFalse();
        assertThat(AuthContactValidator.isValidEmail("   ")).isFalse();
        assertThat(AuthContactValidator.isValidEmail("plain")).isFalse();
        assertThat(AuthContactValidator.isValidEmail("no@dot")).isFalse();
        assertThat(AuthContactValidator.isValidEmail("@example.com")).isFalse();
        assertThat(AuthContactValidator.isValidEmail("user@")).isFalse();
        assertThat(AuthContactValidator.isValidEmail("010-1234-5678")).isFalse();
        assertThat(AuthContactValidator.isValidEmail("user@domain.c")).isFalse(); // TLD 1자
    }

    // ─── 이메일 마스킹 ──────────────────────────────────

    @Test
    void maskEmail_hidesLocalPart() {
        assertThat(AuthContactValidator.maskEmail("alice@gmail.com")).isEqualTo("al***@gmail.com");
        assertThat(AuthContactValidator.maskEmail("ab@gmail.com")).isEqualTo("a***@gmail.com");
        assertThat(AuthContactValidator.maskEmail("a@gmail.com")).isEqualTo("***@gmail.com");
        assertThat(AuthContactValidator.maskEmail("verylongname@gmail.com")).isEqualTo("ve***@gmail.com");
    }

    @Test
    void maskEmail_returnsEmptyForInvalidInput() {
        assertThat(AuthContactValidator.maskEmail(null)).isEqualTo("");
        assertThat(AuthContactValidator.maskEmail("not-an-email")).isEqualTo("");
    }

    // ─── 휴대폰 정규화 ──────────────────────────────────

    @Test
    void normalizePhone_stripsNonDigits() {
        assertThat(AuthContactValidator.normalizePhone("010-1234-5678")).isEqualTo("01012345678");
        assertThat(AuthContactValidator.normalizePhone("010 1234 5678")).isEqualTo("01012345678");
        assertThat(AuthContactValidator.normalizePhone("+82 10 1234 5678")).isEqualTo("821012345678");
        assertThat(AuthContactValidator.normalizePhone(null)).isEqualTo("");
    }

    // ─── 휴대폰 검증 ──────────────────────────────────

    @Test
    void isValidKrMobile_acceptsValid010Numbers() {
        assertThat(AuthContactValidator.isValidKrMobile("01012345678")).isTrue();
        assertThat(AuthContactValidator.isValidKrMobile("0101234567")).isTrue(); // 10자리
    }

    @Test
    void isValidKrMobile_acceptsLegacyPrefixes() {
        assertThat(AuthContactValidator.isValidKrMobile("01112345678")).isTrue();
        assertThat(AuthContactValidator.isValidKrMobile("01612345678")).isTrue();
        assertThat(AuthContactValidator.isValidKrMobile("01712345678")).isTrue();
        assertThat(AuthContactValidator.isValidKrMobile("01812345678")).isTrue();
        assertThat(AuthContactValidator.isValidKrMobile("01912345678")).isTrue();
    }

    @Test
    void isValidKrMobile_rejectsInvalid() {
        assertThat(AuthContactValidator.isValidKrMobile(null)).isFalse();
        assertThat(AuthContactValidator.isValidKrMobile("")).isFalse();
        assertThat(AuthContactValidator.isValidKrMobile("0212345678")).isFalse();   // 02 유선
        assertThat(AuthContactValidator.isValidKrMobile("01512345678")).isFalse();  // 015 없음
        assertThat(AuthContactValidator.isValidKrMobile("0101234")).isFalse();      // 너무 짧음
        assertThat(AuthContactValidator.isValidKrMobile("010123456789")).isFalse(); // 너무 김
        assertThat(AuthContactValidator.isValidKrMobile("8210123456")).isFalse();   // 국제번호 표기
        assertThat(AuthContactValidator.isValidKrMobile("abc12345678")).isFalse();
    }

    // ─── 휴대폰 마스킹 ──────────────────────────────────

    @Test
    void maskKrMobile_keepsHeadAndTail() {
        assertThat(AuthContactValidator.maskKrMobile("01012345678")).isEqualTo("010-****-5678");
        assertThat(AuthContactValidator.maskKrMobile("0101234567")).isEqualTo("010-****-4567");
    }

    @Test
    void maskKrMobile_returnsPlaceholderForShortInput() {
        assertThat(AuthContactValidator.maskKrMobile(null)).isEqualTo("***-****-****");
        assertThat(AuthContactValidator.maskKrMobile("123")).isEqualTo("***-****-****");
    }
}
