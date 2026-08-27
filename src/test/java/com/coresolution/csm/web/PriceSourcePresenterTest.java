package com.coresolution.csm.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.coresolution.csm.serivce.PlatformPriceCache.InstPriceStatus;

/**
 * 단가 수신 상태 문구 (CSM-2).
 *
 * <p>기준 시각을 고정해서 판정한다 — {@code Instant.now()} 를 쓰면 경계에서 흔들린다.
 */
class PriceSourcePresenterTest {

    private static final Instant NOW = Instant.parse("2026-08-27T09:00:00Z");
    private static final int STALE_MINUTES = 15;   // 플랫폼 PLAT-1 과 같은 값

    private PriceSourcePresenter.View at(Duration ago, Integer version) {
        return PriceSourcePresenter.of(
                new InstPriceStatus(version, NOW.minus(ago)), STALE_MINUTES, NOW);
    }

    // ── 정상 ──────────────────────────────────────────────────

    @Test
    void 방금_받았으면_경고하지_않는다() {
        var v = at(Duration.ofMinutes(3), 7);

        assertThat(v.stale()).isFalse();
        assertThat(v.version()).isEqualTo("v7");
        assertThat(v.ageText()).isEqualTo("3분 전");
        assertThat(v.detail()).isEqualTo("3분 전 수신");
    }

    /**
     * 폴링 1회를 놓친 정도는 경고하지 않는다.
     *
     * <p>5분 주기에서 한 번 실패하면 10분이 될 수 있다. <b>일시적 오류를 경고로 만들면
     * 곧 아무도 안 본다</b> (§3.2 와 같은 판단).
     */
    @Test
    void 폴링_한_번을_놓친_정도는_경고하지_않는다() {
        assertThat(at(Duration.ofMinutes(10), 7).stale()).isFalse();
        assertThat(at(Duration.ofMinutes(14), 7).stale()).isFalse();
    }

    // ── 낡음 ──────────────────────────────────────────────────

    /**
     * ⭐ 임계값은 <b>플랫폼 PLAT-1 과 같은 15분</b>이다.
     *
     * <p>폴링 주기(5분)의 3배 — 2회 연속 실패부터 낡은 것으로 본다.
     * 두 화면이 같은 상황을 다르게 말하면 안 된다.
     */
    @Test
    void 십오분부터_낡은_것으로_본다() {
        assertThat(at(Duration.ofMinutes(14), 7).stale()).as("14분 — 아직 아니다").isFalse();
        assertThat(at(Duration.ofMinutes(15), 7).stale()).as("15분 — 여기부터").isTrue();
    }

    @Test
    void 낡으면_왜_문제인지_설명한다() {
        var v = at(Duration.ofHours(3), 7);

        assertThat(v.stale()).isTrue();
        assertThat(v.detail())
                .as("'낡았다' 만으로는 무엇을 해야 하는지 알 수 없다")
                .isEqualTo("마지막 수신 3시간 전 — 단가가 최신이 아닐 수 있습니다.");
    }

    // ── 수신 이력 없음 ────────────────────────────────────────

    /**
     * 한 번도 못 받은 것은 <b>경고가 아니다.</b>
     *
     * <p>배포 직후·기관 등록 직후의 정상 상태다. 플랫폼 PLAT-1 의 {@code 대기중} 과
     * 같은 판단이다 — 이걸 빨간색으로 만들면 배포할 때마다 전 기관이 빨개진다.
     */
    @Test
    void 수신_이력이_없으면_경고가_아니다() {
        var none = PriceSourcePresenter.of(null, STALE_MINUTES, NOW);

        assertThat(none.stale()).isFalse();
        assertThat(none.version()).isNull();
        assertThat(none.ageText()).isEqualTo("수신 이력 없음");
    }

    @Test
    void 행은_있는데_시각이_비어도_수신_이력_없음이다() {
        var v = PriceSourcePresenter.of(new InstPriceStatus(3, null), STALE_MINUTES, NOW);

        assertThat(v.stale()).isFalse();
        assertThat(v.ageText()).isEqualTo("수신 이력 없음");
    }

    /** 버전만 없는 경우 — 화면은 {@code -} 로 나온다. 경과 시간은 그대로 보여준다. */
    @Test
    void 버전이_없어도_경과_시간은_보여준다() {
        var v = at(Duration.ofMinutes(4), null);

        assertThat(v.version()).isNull();
        assertThat(v.ageText()).isEqualTo("4분 전");
    }

    // ── 경과 시간 표기 ────────────────────────────────────────

    @Test
    void 경과_시간을_사람이_읽는_단위로_줄인다() {
        assertThat(PriceSourcePresenter.describeAge(Duration.ofSeconds(30))).isEqualTo("1분 미만");
        assertThat(PriceSourcePresenter.describeAge(Duration.ofMinutes(1))).isEqualTo("1분");
        assertThat(PriceSourcePresenter.describeAge(Duration.ofMinutes(59))).isEqualTo("59분");
        assertThat(PriceSourcePresenter.describeAge(Duration.ofMinutes(60))).isEqualTo("1시간");
        assertThat(PriceSourcePresenter.describeAge(Duration.ofHours(23))).isEqualTo("23시간");
        assertThat(PriceSourcePresenter.describeAge(Duration.ofHours(24))).isEqualTo("1일");
        assertThat(PriceSourcePresenter.describeAge(Duration.ofDays(9))).isEqualTo("9일");
    }

    /**
     * 시계가 뒤로 갔거나 DB 시각이 앞선 경우.
     *
     * <p>{@code -3분 전} 같은 문구가 나오면 운영자가 <b>화면을 못 믿게 된다.</b>
     * 음수는 0으로 눌러서 "1분 미만" 으로 보여준다.
     */
    @Test
    void 미래_시각이어도_음수를_보여주지_않는다() {
        var v = PriceSourcePresenter.of(
                new InstPriceStatus(7, NOW.plus(Duration.ofMinutes(5))), STALE_MINUTES, NOW);

        assertThat(v.ageText()).isEqualTo("1분 미만 전");
        assertThat(v.stale()).isFalse();
    }

    // ── 임계값이 설정값이라는 것 ──────────────────────────────

    /**
     * 임계값은 <b>하드코딩이 아니다.</b> 폴링 주기를 바꾸면 같이 조정해야 한다.
     * 코드에 15가 박혀 있으면 그 조정이 배포를 요구하게 된다.
     */
    @Test
    void 임계값을_바꾸면_판정도_바뀐다() {
        var status = new InstPriceStatus(7, NOW.minus(Duration.ofMinutes(20)));

        assertThat(PriceSourcePresenter.of(status, 15, NOW).stale()).isTrue();
        assertThat(PriceSourcePresenter.of(status, 30, NOW).stale()).isFalse();
    }
}
