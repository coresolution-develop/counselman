package com.coresolution.csm.serivce;

import org.springframework.stereotype.Component;

/**
 * 메시지 타입(sms/lms)을 서버에서 확정한다.
 *
 * <p>클라이언트가 계산해 보낸 type 은 신뢰하지 않는다 — type=sms 로 LMS 분량을 보내면
 * SMS 단가로 차감되고 LMS 요금이 청구되는 요금 사기 벡터가 되기 때문이다.
 *
 * <p>바이트 계산 기준은 기존 화면(consultation-list, inpatient-consultation)의 표시 로직과
 * 동일하게 맞춘다: char > 127 이면 2바이트, 아니면 1바이트, 90바이트 초과 시 LMS.
 * 화면 표시와 어긋나면 사용자가 혼란스러워하므로 임의로 바꾸지 않는다.
 *
 * <p>MMS 는 현재 발송 경로가 없어 판정 대상에서 제외한다 (Phase 1 범위 외).
 */
@Component
public class SmsMessageTypeResolver {

    public static final int SMS_MAX_BYTES = 90;
    public static final int LMS_MAX_BYTES = 2000;
    public static final int LMS_SUBJECT_LENGTH = 20;

    public record Resolved(String type, int bytes, String subject) {
    }

    /**
     * @throws IllegalArgumentException 본문이 비었거나 LMS 최대 바이트(2000)를 초과한 경우
     */
    public Resolved resolve(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("메시지 본문이 비어 있습니다.");
        }
        int bytes = countBytes(message);
        if (bytes > LMS_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "메시지가 최대 " + LMS_MAX_BYTES + "바이트를 초과했습니다. (현재 " + bytes + "바이트)");
        }
        if (bytes > SMS_MAX_BYTES) {
            return new Resolved("lms", bytes, subjectFor(message));
        }
        return new Resolved("sms", bytes, null);
    }

    public int countBytes(String text) {
        int bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            bytes += text.charAt(i) > 127 ? 2 : 1;
        }
        return bytes;
    }

    /**
     * LMS subject 는 본문 앞 20자로 통일하되 <b>반드시 한 줄로</b> 만든다.
     *
     * <p>제목에 줄바꿈이 들어가면 비즈뿌리오가 즉시 거부한다 — 접수는 {@code code=1000}으로
     * 성공하고 0~1초 뒤 결과 리포트가 {@code 9020}으로 돌아온다. 화면에는 전송된 것처럼 보이지만
     * 실제로는 발송되지 않는다.
     *
     * <p>2026-08-14 실제로 이 문제로 7건이 유실됐다. 본문 첫 줄이 짧은 인사말("건강하세요~\n…")이면
     * 앞 20자에 줄바꿈이 포함되기 때문이다. 개행·탭을 공백으로 바꾸고 연속 공백을 접은 뒤 자른다.
     */
    public String subjectFor(String message) {
        String oneLine = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        return oneLine.length() <= LMS_SUBJECT_LENGTH
                ? oneLine
                : oneLine.substring(0, LMS_SUBJECT_LENGTH).trim();
    }
}
