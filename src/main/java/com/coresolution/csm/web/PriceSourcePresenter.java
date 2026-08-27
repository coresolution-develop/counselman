package com.coresolution.csm.web;

import java.time.Duration;
import java.time.Instant;

import com.coresolution.csm.serivce.PlatformPriceCache.InstPriceStatus;

/**
 * 단가 수신 상태를 화면 문구로 바꾼다.
 *
 * <p>── 왜 "플랫폼에서 관리합니다" 만으로는 부족한가 ──
 * 그 문구만 있으면 <b>폴링이 멈춰도 화면이 똑같아 보인다.</b> 운영자는 값이 낡은 것을
 * 알 방법이 없고, 그 사이 발송은 낡은 단가로 계속 나간다.
 * <b>마지막 수신 시각이 오래된 것 자체가 신호여야 한다.</b>
 *
 * <p>── 임계값을 왜 15분으로 잡았나 (재론 금지) ──
 * 처음에 24시간을 제안했다가 바꿨다. 24시간은 폴링 주기(5분)의 <b>288배</b>라
 * 사실상 아무것도 안 잡는다 — 하루 종일 낡은 단가로 청구된 뒤에야 뜬다.
 *
 * <p>플랫폼 PLAT-1 이 "폴링 끊김" 을 <b>{@code PRICE_POLL_STALE_MINUTES} 기본 15분</b>
 * 으로 판정한다. 폴링 주기의 3배 — 2회 연속 실패까지는 일시적 오류로 본다는 근거다.
 * <b>같은 근거를 쓴다.</b> 두 화면이 같은 상황을 다르게 말하면 안 된다.
 *
 * <p>── 두 화면이 재는 것은 조금 다르다 ──
 * <table border="1">
 *   <tr><th></th><th>재는 것</th></tr>
 *   <tr><td>플랫폼 PLAT-1</td><td>csm 이 나를 마지막으로 <b>조회한</b> 시각</td></tr>
 *   <tr><td>csm (이 화면)</td><td>이 기관 단가를 마지막으로 <b>수신한</b> 시각</td></tr>
 * </table>
 *
 * <p>차이가 나는 경우는 하나뿐이다 — <b>폴링은 도착했는데 값이 거부된 경우</b>
 * (업무 상한 초과 등). 그때 플랫폼은 "폴링 정상", csm 은 "수신 낡음" 이 된다.
 * 둘 다 사실이고, 그 상황에서 플랫폼은 <b>거부 회신을 받아 "불일치"</b> 로 표시한다.
 *
 * <p>중요한 것은 방향이다: csm 의 시각은 플랫폼의 것보다 <b>같거나 더 오래됐다.</b>
 * 절대 더 최신일 수 없다. 그래서 같은 임계값을 쓰면 csm 이 <b>먼저 또는 동시에</b>
 * 경고한다. "플랫폼은 끊김인데 csm 은 정상" 은 나올 수 없다.
 */
public final class PriceSourcePresenter {

    private PriceSourcePresenter() {
    }

    /**
     * @param staleMinutes {@code csm.sms.price.stale-minutes}. 플랫폼 PLAT-1 과 같은 값을 쓴다.
     */
    public static View of(InstPriceStatus status, int staleMinutes, Instant now) {
        if (status == null || status.oldestReceivedAt() == null) {
            // 아직 한 번도 못 받았다. 배포 직후의 정상 상태일 수도 있으므로
            // 경고(stale)로 만들지 않는다 — PLAT-1 의 `대기중` 과 같은 판단이다.
            return new View(null, "수신 이력 없음", false, "아직 단가를 받지 못했습니다.");
        }

        Duration age = Duration.between(status.oldestReceivedAt(), now);
        boolean stale = age.toMinutes() >= staleMinutes;
        String ago = describeAge(age);

        String version = status.appliedVersion() == null ? null : "v" + status.appliedVersion();
        String detail = stale
                ? "마지막 수신 " + ago + " 전 — 단가가 최신이 아닐 수 있습니다."
                : ago + " 전 수신";

        return new View(version, ago + " 전", stale, detail);
    }

    /**
     * 사람이 읽는 경과 시간.
     *
     * <p>분 단위까지만 내려간다. 초 단위는 5분마다 도는 폴링에서 의미가 없고,
     * "방금" 처럼 보여서 <b>정확한 시각을 확인해야 할 때 오히려 방해</b>가 된다.
     */
    static String describeAge(Duration age) {
        long minutes = Math.max(0, age.toMinutes());
        if (minutes < 1) {
            return "1분 미만";
        }
        if (minutes < 60) {
            return minutes + "분";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "시간";
        }
        return (hours / 24) + "일";
    }

    /**
     * @param version    적용 버전 표기 ({@code v7}). 못 받았으면 {@code null}
     * @param ageText    경과 시간 짧은 표기 ({@code 3분 전})
     * @param stale      임계값을 넘었는가. 화면에서 경고로 표시한다
     * @param detail     한 줄 설명. 배너에 그대로 쓴다
     */
    public record View(String version, String ageText, boolean stale, String detail) {
    }
}
