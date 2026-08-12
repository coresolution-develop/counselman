package com.coresolution.csm.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문자 발송 refkey 신규 형식: {@code MP-{instCode}-{historyId}} (예: MP-COHS-123456)
 *
 * <p>구형식 {@code {INST}{yyyyMMddHHmmss}{rand4}} 는 유일성 보장이 없고, 콜백이
 * {@code substring(0, 4)} 로 기관을 복원해 기관코드가 4자가 아니면 매핑이 깨졌다.
 * 신규 형식은 구분자 파싱으로 그 가정을 제거하고, historyId(PK)로 유일성을 구조적으로 보장한다.
 */
public final class SmsRefkey {

    public static final String PREFIX = "MP-";

    /** 기관코드 규칙은 SmsService.safeInst 와 동일하게 맞춘다. */
    private static final Pattern PARSE_PATTERN =
            Pattern.compile("^MP-([A-Za-z0-9_]{2,20})-(\\d{1,18})$");

    private SmsRefkey() {
    }

    public record Parsed(String instCode, long historyId) {
    }

    public static String format(String instCode, long historyId) {
        return PREFIX + instCode + "-" + historyId;
    }

    /** 신규 형식이 아니면 empty. 호출부는 구형식 폴백 파싱으로 넘어간다. */
    public static Optional<Parsed> parse(String refkey) {
        if (refkey == null) {
            return Optional.empty();
        }
        Matcher m = PARSE_PATTERN.matcher(refkey.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Parsed(m.group(1), Long.parseLong(m.group(2))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
