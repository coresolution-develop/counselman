package com.coresolution.csm.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.HubLinkView;

class HubLinkPresenterTest {

    private final HubLinkPresenter presenter = new HubLinkPresenter();

    /**
     * 사내 호스트에는 언더스코어가 흔한데(ocean_plt, amz_dj) RFC상 유효한 호스트명이 아니라
     * URI.getHost()가 null을 돌려준다. 그 탓에 목록에 URL 전체가 노출된 적이 있다.
     */
    @ParameterizedTest
    @CsvSource({
            "https://ocean_plt.cspay.co.kr:8230/login,      ocean_plt.cspay.co.kr:8230",
            "https://amz_dj.cspay.co.kr/,                   amz_dj.cspay.co.kr",
            "https://amz_dj.cspay.co.kr:8210/reservations,  amz_dj.cspay.co.kr:8210",
            "https://did-g.spoplat.net,                     did-g.spoplat.net",
            "https://lqt-ps.yanolja.com/a/b?q=1,            lqt-ps.yanolja.com",
            "https://www.example.com/path,                  example.com",
            "https://user:pw@intra.example.com/x,           intra.example.com",
            "https://hars-falh.sosyge.net/login#frag,       hars-falh.sosyge.net",
    })
    void hostOf_stripsPathAndKeepsPort(String url, String expected) {
        assertThat(HubLinkPresenter.hostOf(url)).isEqualTo(expected.trim());
    }

    /** 포트로만 갈리는 링크가 많아 포트를 지우면 서로 구분되지 않는다. */
    @Test
    void hostOf_keepsPort_soSameHostDifferentPortsStayDistinct() {
        assertThat(HubLinkPresenter.hostOf("https://ocean_plt.cspay.co.kr:8210/login"))
                .isNotEqualTo(HubLinkPresenter.hostOf("https://ocean_plt.cspay.co.kr:8220/"));
    }

    @Test
    void hostOf_blankOrGarbage_doesNotBlowUp() {
        assertThat(HubLinkPresenter.hostOf(null)).isEmpty();
        assertThat(HubLinkPresenter.hostOf("   ")).isEmpty();
        assertThat(HubLinkPresenter.hostOf("not a url")).isEqualTo("not a url");
    }

    @ParameterizedTest
    @CsvSource({
            "예약 관리,        https://dev.sosyge.net/booking,      dev",
            "예약 관리,        https://booking-dev.sosyge.net,      dev",
            "ATS 군산 DEMO,    https://ats.example.com,             demo",
            "오션팔레트 군산,   https://ocean_plt.cspay.co.kr:8230,  prod",
    })
    void envOf_marksNonProductionHosts(String title, String url, String expected) {
        assertThat(HubLinkPresenter.envOf(title, HubLinkPresenter.hostOf(url))).isEqualTo(expected.trim());
    }

    /** 표에 없는 분류도 같은 이름이면 항상 같은 색이어야 화면이 흔들리지 않는다. */
    @Test
    void category_unknownName_isStableAndShortened() {
        HubLinkPresenter.Category first = presenter.category("사내 위키 포털");
        HubLinkPresenter.Category second = presenter.category("사내 위키 포털");

        assertThat(first.color()).isEqualTo(second.color());
        assertThat(first.shortLabel()).isEqualTo("사내");
    }

    @Test
    void category_knownName_usesHandoffPalette() {
        assertThat(presenter.category("병원").color()).isEqualTo("#2f5bb8");
        assertThat(presenter.category("병원").shortLabel()).isEqualTo("병원");
    }

    // ── 운영자 지정값 우선 (2단계 컬럼) ─────────────────────────────────

    @Test
    void category_savedStyle_overridesDefault() {
        Map<String, HubLinkPresenter.Category> styles = presenter.categoryStyles(List.of(
                Map.of("category_name", "병원", "color", "#8c2f5c", "color_dark", "#d67ba0", "short_label", "메디")));

        HubLinkPresenter.Category cat = presenter.category("병원", styles);

        assertThat(cat.color()).isEqualTo("#8c2f5c");
        assertThat(cat.colorDark()).isEqualTo("#d67ba0");
        assertThat(cat.shortLabel()).isEqualTo("메디");
    }

    /** 색만 지정하고 축약은 비워둘 수 있다 — 빈 항목만 기본값으로 채워야 한다. */
    @Test
    void category_partialStyle_fillsOnlyMissingFields() {
        Map<String, HubLinkPresenter.Category> styles = presenter.categoryStyles(List.of(
                mapWithNulls("병원", "#8c2f5c", "#d67ba0", null)));

        HubLinkPresenter.Category cat = presenter.category("병원", styles);

        assertThat(cat.color()).isEqualTo("#8c2f5c");
        assertThat(cat.shortLabel()).isEqualTo("병원"); // 기본값 유지
    }

    /** env 컬럼이 채워져 있으면 host 자동 판정을 덮어써야 한다. */
    @Test
    void publicLink_storedEnv_overridesHostDetection() {
        CompanyLink link = new CompanyLink();
        link.setId(1L);
        link.setTitle("예약 관리");
        link.setUrl("https://booking.sosyge.net");  // host만 보면 prod
        link.setEnv("dev");

        HubLinkView view = presenter.publicLink(link, false, true);

        assertThat(view.getEnv()).isEqualTo("dev");
        assertThat(view.getEnvLabel()).isEqualTo("개발");
        assertThat(view.getEnvSource()).isEqualTo("dev");
    }

    /** env가 비어 있으면 자동 판정으로 떨어지고, 관리 화면 셀렉트는 "자동 판정"으로 남아야 한다. */
    @Test
    void publicLink_blankEnv_fallsBackToDetectionAndKeepsSourceEmpty() {
        CompanyLink link = new CompanyLink();
        link.setId(1L);
        link.setTitle("예약 관리");
        link.setUrl("https://dev.sosyge.net/booking");
        link.setEnv(null);

        HubLinkView view = presenter.publicLink(link, false, true);

        assertThat(view.getEnv()).isEqualTo("dev");
        assertThat(view.getEnvSource()).isEmpty();
    }

    private Map<String, Object> mapWithNulls(String name, String color, String colorDark, String shortLabel) {
        Map<String, Object> row = new HashMap<>();
        row.put("category_name", name);
        row.put("color", color);
        row.put("color_dark", colorDark);
        row.put("short_label", shortLabel);
        return row;
    }
}
