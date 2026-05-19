package com.coresolution.csm.util;

import java.util.regex.Pattern;

/**
 * 비밀번호 찾기/변경 등에서 사용하는 이메일·휴대폰 형식 검증과 마스킹 헬퍼.
 *
 * - 이메일: 단순화된 RFC 5322 호환 정규식 (대부분의 실용 케이스 커버)
 * - 휴대폰: 대한민국 휴대폰 prefix(010/011/016-019) + 10~11자리
 */
public final class AuthContactValidator {

    private AuthContactValidator() {}

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private static final Pattern KR_MOBILE_PATTERN = Pattern.compile(
            "^01(?:0|1|[6-9])\\d{7,8}$");

    public static boolean isValidEmail(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 254) return false;
        return EMAIL_PATTERN.matcher(s).matches();
    }

    /**
     * 이메일을 사용자에게 확인용으로 보여줄 마스킹 문자열 반환.
     * 예) "alice@gmail.com" -> "al***@gmail.com"
     *     "ab@gmail.com"    -> "a***@gmail.com"
     *     "a@gmail.com"     -> "***@gmail.com"
     */
    public static String maskEmail(String raw) {
        if (!isValidEmail(raw)) return "";
        String s = raw.trim();
        int at = s.indexOf('@');
        String local = s.substring(0, at);
        String domain = s.substring(at);
        String visible;
        if (local.length() >= 3) {
            visible = local.substring(0, 2);
        } else if (local.length() == 2) {
            visible = local.substring(0, 1);
        } else {
            visible = "";
        }
        return visible + "***" + domain;
    }

    /**
     * 입력값에서 숫자만 추출.
     */
    public static String normalizePhone(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9]", "");
    }

    /**
     * 대한민국 휴대폰 prefix(010/011/016-019) + 10~11자리 검증.
     * 입력은 normalize 된 숫자열을 기대.
     */
    public static boolean isValidKrMobile(String digits) {
        if (digits == null) return false;
        return KR_MOBILE_PATTERN.matcher(digits).matches();
    }

    /**
     * 휴대폰 번호를 'XXX-****-XXXX' 형태로 마스킹.
     * 입력은 normalize 된 숫자열을 기대.
     */
    public static String maskKrMobile(String digits) {
        if (digits == null || digits.length() < 7) return "***-****-****";
        int len = digits.length();
        String head = digits.substring(0, 3);
        String tail = digits.substring(len - 4);
        return head + "-****-" + tail;
    }
}
