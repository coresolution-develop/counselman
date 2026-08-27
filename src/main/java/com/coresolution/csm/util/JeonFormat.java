package com.coresolution.csm.util;

import java.math.BigDecimal;

/**
 * 전(錢) ↔ 원 변환. <b>금액 표기의 단일 진실이다.</b>
 *
 * <p>── 왜 한 곳에 모으나 ──
 * 변환이 두 곳에 있으면 갈라진다. 실제로 {@code PlatformPriceCache} 가
 * {@code inst_data_cs} 에 미러링할 때와 {@code /rate} 화면이 표시할 때
 * 각각 필요했다. 규칙이 갈리면 <b>저장된 값과 보이는 값이 달라진다.</b>
 *
 * <p>── 부동소수를 거치지 않는다 ──
 * {@code 9.6} 은 double 로 정확히 표현되지 않는다
 * ({@code 9.5999999999999996447...}). 그래서 {@code 3 * 9.6 = 28.799999999999997}
 * 이 되고, 그 값이 화면에 그대로 나간다. 정수 나눗셈으로만 만든다.
 *
 * <p>── 왕복이 성립해야 한다 ──
 * {@code parseUnitPrice(toWon(x)) == x} 가 플랫폼 벡터
 * ({@code pricing-vectors.json}) 로 검증된다. 역방향 벡터는 없지만,
 * 왕복 성질이 두 함수가 갈리지 않는다는 더 강한 보장이다.
 */
public final class JeonFormat {

    private JeonFormat() {
    }

    /**
     * 전 → 원 문자열. {@code 960 → "9.6"}, {@code 1000 → "10"}.
     *
     * <p>후행 0 을 남기지 않는다 — {@code 960} 은 {@code "9.60"} 이 아니라 {@code "9.6"} 이다.
     * 플랫폼 벡터 P10({@code "9.60" → 960})이 두 표기를 같은 값으로 보므로 왕복은 성립한다.
     */
    public static String toWon(long jeon) {
        return BigDecimal.valueOf(jeon)
                .movePointLeft(2)
                .stripTrailingZeros()
                .toPlainString();
    }

    /**
     * 전 → 원, {@link BigDecimal} 로.
     *
     * <p><b>화면 모델에는 이것을 넣는다.</b> Thymeleaf 의
     * {@code #numbers.formatDecimal()} 은 {@code Number} 를 받으므로 그대로 쓰인다.
     * {@code double} 을 넣으면 부동소수 오차가 표시 계층까지 따라간다.
     *
     * <p>── "화면이 같으니 안 고쳐도 된다" 는 판단을 하지 말 것 ──
     * 템플릿이 소수점 0자리로 반올림하므로 {@code 28.799999999999997} 도
     * {@code 29원} 으로 나간다. 지금은 결과가 같다.
     *
     * <p><b>그건 보장이 아니라 우연이다.</b> 정확한 값이 반올림 경계
     * ({@code .5})에 걸리고 double 이 그 아래로 떨어지면 표시가 갈린다 —
     * {@code 28.5} 여야 할 값이 {@code 28.499999...} 가 되면
     * {@code 29} 가 아니라 {@code 28} 로 나간다.
     * 현재 단가에서 그 조합이 없을 뿐, 단가가 바뀌면 나타난다.
     */
    public static BigDecimal toWonDecimal(long jeon) {
        return BigDecimal.valueOf(jeon).movePointLeft(2);
    }

    /**
     * 전 → 원, 천단위 구분 표기. 화면 표시용.
     *
     * <p>{@code 118518720 → "1,185,187.2"}
     */
    public static String toWonDisplay(long jeon) {
        BigDecimal won = BigDecimal.valueOf(jeon).movePointLeft(2).stripTrailingZeros();
        BigDecimal integerPart = won.setScale(0, java.math.RoundingMode.DOWN);
        String grouped = String.format("%,d", integerPart.longValueExact());

        BigDecimal fraction = won.subtract(integerPart).abs();
        if (fraction.signum() == 0) {
            return grouped;
        }
        return grouped + fraction.toPlainString().substring(1);
    }

    /**
     * 플랫폼 {@code parseUnitPrice()} 의 {@code DECIMAL_PATTERN} 과 <b>같은 정규식</b>이다.
     *
     * <p>{@code new BigDecimal(String)} 만 믿으면 안 된다 — 자바는 지수 표기({@code 1e2})와
     * 명시적 양수 부호({@code +9.6})를 <b>받아들이지만</b> 플랫폼은 거부한다.
     * 같은 문자열이 두 시스템에서 다른 단가가 되면 청구액이 갈린다.
     */
    private static final java.util.regex.Pattern DECIMAL_PATTERN =
            java.util.regex.Pattern.compile("^-?(\\d+(\\.\\d*)?|\\.\\d+)$");

    /**
     * 플랫폼 {@code PriceItem.unitCostJeon} 이 {@code Int} 컬럼이라 여기가 상한이다.
     * csm 이 더 큰 값을 받아 두면 <b>플랫폼이 거부한 단가로 csm 만 청구</b>하게 된다.
     */
    public static final long MAX_UNIT_COST_JEON = 2_147_483_647L;

    /**
     * 원 문자열 → 전. 실패하면 {@code null}.
     *
     * <p><b>전 미만 자릿수(소수 3자리 이상)는 거부한다.</b> {@code 9.655} 원은
     * 965.5전인데 전 미만은 표현할 수 없다. 반올림하면 고객이 입력한 값과
     * 실제 차감액이 갈리고, 그건 표시 버그가 아니라 요금 분쟁이다.
     *
     * <p>플랫폼 {@code parseUnitPrice()} 와 <b>같은 규칙</b>이다 — 벡터 P01~P20 전부.
     * 형식 검사({@link #DECIMAL_PATTERN}) → 음수 → 자릿수 → 상한 순서도 같다.
     *
     * <p>── 왜 정규식을 따로 두나 ──
     * 처음에는 {@code new BigDecimal(won)} 의 예외에만 의존했다. 그랬더니
     * {@code "1e2"} 가 <b>100원(10,000전)으로 통과</b>했고 {@code "21474836.48"} 은
     * 플랫폼이 {@code TOO_LARGE} 로 거부하는데 csm 만 받아들였다.
     * <b>왕복 테스트가 아니라 벡터 대조가 이걸 잡았다.</b>
     */
    public static Long parseWonToJeon(String won) {
        if (won == null) {
            return null;
        }
        String trimmed = won.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!DECIMAL_PATTERN.matcher(trimmed).matches()) {
            return null;   // NOT_NUMERIC — 지수 표기, 양수 부호, 천단위 구분자
        }
        if (trimmed.startsWith("-")) {
            return null;   // NEGATIVE
        }
        try {
            BigDecimal bd = new BigDecimal(trimmed);
            if (bd.stripTrailingZeros().scale() > 2) {
                return null;   // TOO_MANY_DECIMALS
            }
            long jeon = bd.movePointRight(2).longValueExact();
            if (jeon > MAX_UNIT_COST_JEON) {
                return null;   // TOO_LARGE
            }
            return jeon;
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * 단가(전) × 건수 = 총액(전).
     *
     * <p><b>{@code long} 으로 곱한다.</b> {@code int} 로 곱하면 2,147,483,647전을
     * 넘는 순간 조용히 음수가 된다 (CSM-1 에서 고친 것과 같은 종류다).
     */
    public static long multiply(long unitJeon, long count) {
        return unitJeon * count;
    }
}
