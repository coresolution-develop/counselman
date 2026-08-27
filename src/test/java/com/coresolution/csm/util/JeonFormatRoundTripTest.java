package com.coresolution.csm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 전 ↔ 원 변환의 <b>왕복 성질</b>과 플랫폼 벡터 적합성.
 *
 * <p>── 왕복이 왜 더 강한 보장인가 ──
 * 플랫폼 {@code pricing-vectors.json} 은 {@code 문자열 → 전} 방향만 고정한다
 * ({@code parseUnitPrice}). 역방향({@code 전 → 문자열}) 벡터는 없다.
 * 그래서 {@link JeonFormat#toWon(long)} 이 혼자 틀려도 벡터는 못 잡는다.
 *
 * <p>{@code parseWonToJeon(toWon(x)) == x} 를 걸어 두면 <b>두 함수가 갈라지는 것</b>을
 * 잡는다. 실제로 {@code inst_data_cs} 미러링(쓰기)과 {@code /rate} 표시(읽기)가
 * 이 두 함수를 각각 쓰므로, 갈라지면 <b>저장된 값과 보이는 값이 달라진다.</b>
 *
 * <p><b>CSM-5 예고:</b> 아래 P01~P20 은 플랫폼 벡터 파일을 손으로 옮긴 것이다.
 * CSM-5 에서 파일 자체를 공유하고 SHA-256 트립와이어를 걸면 이 전사본은 대체된다.
 * 그때까지는 <b>플랫폼 벡터를 고치면 여기도 같이 고쳐야 한다.</b>
 */
class JeonFormatRoundTripTest {

    // ── 1. 왕복 ────────────────────────────────────────────────

    /**
     * ⭐ {@code parseWonToJeon(toWon(x)) == x} 가 전 구간에서 성립해야 한다.
     *
     * <p>0전부터 100,000전(=1,000원)까지 전수 확인한다. 업무 상한
     * ({@code csm.sms.price.max-jeon}) 이 100,000 이므로 실제 단가는 전부 이 안에 있다.
     */
    @Test
    void 업무_구간_전체에서_왕복이_성립한다() {
        List<String> broken = new ArrayList<>();
        for (long jeon = 0; jeon <= 100_000; jeon++) {
            String won = JeonFormat.toWon(jeon);
            Long back = JeonFormat.parseWonToJeon(won);
            if (back == null || back != jeon) {
                broken.add(jeon + "전 → \"" + won + "\" → " + back);
                if (broken.size() >= 10) {
                    break;
                }
            }
        }
        assertThat(broken).as("왕복이 깨진 값").isEmpty();
    }

    /**
     * 업무 상한 밖에서도 <b>표현 가능한 구간까지는</b> 왕복이 성립한다.
     *
     * <p>왕복은 {@link JeonFormat#MAX_UNIT_COST_JEON} 까지만 정의된다 —
     * 그 위는 플랫폼이 표현할 수 없으므로 <b>왕복이 깨지는 것이 정답</b>이다.
     * 처음에 나는 1,000억 전을 넣고 왕복을 기대했다가 실패했다. 기대가 틀렸다.
     */
    @Test
    void 표현_가능한_구간_끝까지_왕복이_성립한다() {
        for (long jeon : new long[] { 999_999L, 1_000_000_000L, JeonFormat.MAX_UNIT_COST_JEON }) {
            assertThat(JeonFormat.parseWonToJeon(JeonFormat.toWon(jeon)))
                    .as("%d전", jeon)
                    .isEqualTo(jeon);
        }
    }

    /**
     * 상한을 넘으면 <b>양쪽 다</b> 거부해야 한다.
     *
     * <p>{@code toWon} 은 표기를 만들 수 있지만 {@code parseWonToJeon} 이 되돌리지 않는다.
     * 조용히 절단하거나 오버플로하는 것보다 낫다 — 그래야 폴백이 작동한다.
     */
    @Test
    void 상한을_넘으면_되돌리지_않는다() {
        long overflow = JeonFormat.MAX_UNIT_COST_JEON + 1;
        assertThat(JeonFormat.toWon(overflow)).isEqualTo("21474836.48");
        assertThat(JeonFormat.parseWonToJeon(JeonFormat.toWon(overflow))).isNull();
    }

    /**
     * 손으로 확인한 표기. 왕복만 보면 <b>두 함수가 같이 틀린 것</b>은 못 잡는다.
     * 예를 들어 둘 다 100 이 아니라 1000 으로 나누면 왕복은 여전히 성립한다.
     */
    @Test
    void 표기가_손으로_계산한_값과_같다() {
        assertThat(JeonFormat.toWon(0)).isEqualTo("0");
        assertThat(JeonFormat.toWon(1)).isEqualTo("0.01");
        assertThat(JeonFormat.toWon(50)).isEqualTo("0.5");
        assertThat(JeonFormat.toWon(960)).isEqualTo("9.6");      // 운영 SMS
        assertThat(JeonFormat.toWon(1000)).isEqualTo("10");
        assertThat(JeonFormat.toWon(3000)).isEqualTo("30");      // 운영 LMS
        assertThat(JeonFormat.toWon(9000)).isEqualTo("90");      // 운영 MMS
        assertThat(JeonFormat.toWon(870)).isEqualTo("8.7");      // 볼륨 할인 가정
    }

    // ── 2. 벡터 적합성은 여기 없다 ────────────────────────────
    //
    // P01~P20 을 **손으로 옮겨 뒀던 블록을 지웠다** (CSM-5).
    // 이제 PricingVectorsTest 가 플랫폼 벡터 **파일을 직접 읽어** 돌린다.
    //
    // 옮긴 것은 갈린다 — 원본이 바뀌어도 사본은 그대로다.
    // 지울 때 대조해 보니 20/20 일치했지만, **그건 운이 좋았던 것이지 보장이 아니었다.**
    // 갈렸는지 확인할 장치가 없었다.
    //
    // 위 왕복·표기 검증은 남긴다 — 벡터가 고정하지 않는 성질이라 대체되지 않는다.
}
