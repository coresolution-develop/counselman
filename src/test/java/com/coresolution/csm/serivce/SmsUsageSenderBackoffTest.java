package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 재시도 백오프 (CSM-4).
 *
 * <p>설계에서 정한 것: <b>1분 → 2 → 4 … 최대 1시간</b>.
 * 상한이 없으면 실패가 길어질수록 복구도 무한정 늦어진다.
 */
class SmsUsageSenderBackoffTest {

    /** 첫 실패는 1분 뒤. {@code attempts} 는 <b>직전</b> 값이라 0 이다. */
    @Test
    void 첫_실패는_일분_뒤에_다시_시도한다() {
        assertThat(SmsUsageSender.backoffMinutes(0)).isEqualTo(1);
    }

    @Test
    void 지수적으로_늘어난다() {
        assertThat(SmsUsageSender.backoffMinutes(1)).isEqualTo(2);
        assertThat(SmsUsageSender.backoffMinutes(2)).isEqualTo(4);
        assertThat(SmsUsageSender.backoffMinutes(3)).isEqualTo(8);
        assertThat(SmsUsageSender.backoffMinutes(4)).isEqualTo(16);
        assertThat(SmsUsageSender.backoffMinutes(5)).isEqualTo(32);
    }

    /**
     * ⭐ 1시간에서 멈춘다.
     *
     * <p>{@code 1 << 6 = 64} 라 그냥 두면 64분이 된다. 상한이 실제로 걸리는 첫 지점이다.
     */
    @Test
    void 한_시간에서_멈춘다() {
        assertThat(SmsUsageSender.backoffMinutes(6)).isEqualTo(60);
        assertThat(SmsUsageSender.backoffMinutes(10)).isEqualTo(60);
    }

    /**
     * ⭐ 시도 횟수가 아주 커도 <b>음수가 나오면 안 된다.</b>
     *
     * <p>{@code 1 << 31} 은 {@code Integer.MIN_VALUE} 다. 그대로 쓰면
     * {@code next_retry_at} 이 과거가 되어 <b>백오프 없이 무한 재시도</b>가 된다 —
     * 막으려던 것과 정반대다. 상한을 먼저 자르는 이유가 이것이다.
     */
    @Test
    void 시도_횟수가_아주_커도_음수가_되지_않는다() {
        for (int attempts : new int[] { 31, 32, 63, 64, Integer.MAX_VALUE }) {
            assertThat(SmsUsageSender.backoffMinutes(attempts))
                    .as("attempts=%d", attempts)
                    .isEqualTo(60);
        }
    }

    /** 음수 입력(데이터 오염)도 안전하게 1분으로 본다. */
    @Test
    void 음수_시도_횟수도_안전하게_처리한다() {
        assertThat(SmsUsageSender.backoffMinutes(-1)).isEqualTo(1);
        assertThat(SmsUsageSender.backoffMinutes(Integer.MIN_VALUE)).isEqualTo(1);
    }
}
