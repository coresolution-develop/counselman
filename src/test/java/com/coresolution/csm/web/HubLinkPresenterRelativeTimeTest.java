package com.coresolution.csm.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 최근 사용 목록의 상대 시간 표기.
 *
 * <p>목록에는 "3분 전"을 보여주고 정확한 시각은 tooltip 으로 남긴다. 초 단위 절대 시각
 * ({@code 2026-08-30 18:25:45})은 사람이 읽는 정보가 아니고 폭만 두 배로 먹는다.
 *
 * <p>기준 시각을 인자로 받지 않고 {@code LocalDateTime.now()} 를 쓰므로, 테스트도 now 를
 * 기준으로 입력을 만든다. 경계값은 안쪽으로 살짝 밀어 실행 지연에 흔들리지 않게 한다.
 */
class HubLinkPresenterRelativeTimeTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HubLinkPresenter presenter = new HubLinkPresenter();

    private String minutesAgo(long minutes) {
        return LocalDateTime.now().minusMinutes(minutes).format(FMT);
    }

    @Test
    @DisplayName("1분 미만은 '방금'")
    void justNow() {
        assertThat(presenter.relativeTime(minutesAgo(0))).isEqualTo("방금");
    }

    @Test
    @DisplayName("1시간 미만은 분 단위")
    void minutes() {
        assertThat(presenter.relativeTime(minutesAgo(3))).isEqualTo("3분 전");
        assertThat(presenter.relativeTime(minutesAgo(59))).isEqualTo("59분 전");
    }

    @Test
    @DisplayName("하루 미만은 시간 단위")
    void hours() {
        assertThat(presenter.relativeTime(minutesAgo(60))).isEqualTo("1시간 전");
        assertThat(presenter.relativeTime(minutesAgo(60 * 23))).isEqualTo("23시간 전");
    }

    @Test
    @DisplayName("7일 미만은 일 단위")
    void days() {
        assertThat(presenter.relativeTime(minutesAgo(60 * 24))).isEqualTo("1일 전");
        assertThat(presenter.relativeTime(minutesAgo(60 * 24 * 6))).isEqualTo("6일 전");
    }

    @Test
    @DisplayName("7일 이상은 날짜로 떨어진다 — '9일 전'은 오히려 안 읽힌다")
    void fallsBackToDate() {
        LocalDateTime at = LocalDateTime.now().minusDays(9);
        assertThat(presenter.relativeTime(at.format(FMT)))
                .isEqualTo(at.getMonthValue() + "월 " + at.getDayOfMonth() + "일");
    }

    @Test
    @DisplayName("미래 기록은 음수가 아니라 '방금' — 서버 시계가 뒤로 흘러도 '-3분 전'이 보이면 안 된다")
    void futureClampsToZero() {
        String future = LocalDateTime.now().plusMinutes(5).format(FMT);
        assertThat(presenter.relativeTime(future)).isEqualTo("방금");
    }

    @Test
    @DisplayName("파싱 실패는 원본을 그대로 — 표시용 값 때문에 화면이 죽으면 안 된다")
    void malformedFallsThrough() {
        assertThat(presenter.relativeTime("어제쯤")).isEqualTo("어제쯤");
        assertThat(presenter.relativeTime("2026-08-30")).isEqualTo("2026-08-30");
    }

    @Test
    @DisplayName("null·공백은 빈 문자열")
    void blank() {
        assertThat(presenter.relativeTime(null)).isEmpty();
        assertThat(presenter.relativeTime("   ")).isEmpty();
    }
}
