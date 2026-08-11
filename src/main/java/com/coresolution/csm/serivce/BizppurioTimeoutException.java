package com.coresolution.csm.serivce;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Bizppurio 호출이 타임아웃으로 끝났을 때 던진다.
 *
 * <p>일반적인 호출 실패(게이트웨이가 명시적으로 실패 응답을 준 경우)와 반드시 구분해야 한다.
 * {@link Phase#MESSAGE} 타임아웃은 <b>결과 불명(unknown)</b>이다 — 요청이 이미 접수되어
 * 문자가 실제로 발송되었을 수 있다. 따라서 다음을 지켜야 한다.
 *
 * <ul>
 *   <li>자동 재시도 금지. 재요청하면 중복 발송된다.</li>
 *   <li>즉시 환불/취소 처리 금지. 결과 리포트나 발송 결과 조회로 확정한 뒤 판단한다.</li>
 * </ul>
 *
 * <p>{@link Phase#TOKEN} 타임아웃은 문자를 보내기 전 단계이므로 발송이 일어나지 않았음이
 * 확실하다. 이 경우는 안전하게 실패로 취급할 수 있다.
 */
public class BizppurioTimeoutException extends IOException {

    private static final long serialVersionUID = 1L;

    /** 타임아웃이 발생한 호출 단계. 발송 여부 판정이 달라지므로 구분한다. */
    public enum Phase {
        /** 토큰 발급 호출. 발송 전 단계이므로 문자는 나가지 않았다. */
        TOKEN,
        /** 메시지 발송 호출. 접수 여부를 알 수 없다(결과 불명). */
        MESSAGE
    }

    private final Phase phase;
    private final String url;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public BizppurioTimeoutException(
            Phase phase,
            String url,
            int connectTimeoutMs,
            int readTimeoutMs,
            SocketTimeoutException cause) {
        super(buildMessage(phase, url, connectTimeoutMs, readTimeoutMs, cause), cause);
        this.phase = phase;
        this.url = url;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    private static String buildMessage(
            Phase phase, String url, int connectTimeoutMs, int readTimeoutMs, SocketTimeoutException cause) {
        String outcome = phase == Phase.MESSAGE ? "발송 결과 불명(중복 발송 위험 — 재시도 금지)" : "발송 전 단계";
        return "Bizppurio " + phase + " 호출 타임아웃 [" + outcome + "] url=" + url
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", cause=" + (cause == null ? "" : cause.getMessage());
    }

    public Phase getPhase() {
        return phase;
    }

    /**
     * 발송 여부를 알 수 없는 타임아웃인지 여부.
     * {@code true}이면 실패로 단정하지 말고 결과 확인 후 판단해야 한다.
     */
    public boolean isDeliveryOutcomeUnknown() {
        return phase == Phase.MESSAGE;
    }

    public String getUrl() {
        return url;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }
}
