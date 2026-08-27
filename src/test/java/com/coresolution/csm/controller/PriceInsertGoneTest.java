package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 단가 수정 엔드포인트가 <b>실제 HTTP 요청에서</b> 거부되는지 확인한다 (CSM-2).
 *
 * <p>── 왜 화면 테스트로는 부족한가 ──
 * {@code SmsSettingTemplateRenderTest} 는 <b>화면에 버튼이 없다</b> 는 것만 본다.
 * 버튼이 없어도 엔드포인트가 살아 있으면 예전 탭·북마크·직접 호출이 그대로 성공한다.
 * 그게 <b>"막았다고 믿는데 안 막힌 상태"</b> 다.
 *
 * <p>── 왜 404 가 아니라 410 인가 ──
 * 404 는 "그런 경로 없다" 라 <b>왜 안 되는지 알 수 없다.</b>
 * 410 Gone 은 "있었지만 없앴다" 이고, 본문에 어디서 관리하는지가 들어간다.
 *
 * <p>{@code standaloneSetup} 을 쓴다 — 이 엔드포인트는 {@code ensureInst(session)} 로
 * 세션만 보므로 전체 컨텍스트가 필요 없다. 그래도 <b>디스패처를 통과하는 진짜 요청</b>이다.
 */
class PriceInsertGoneTest {

    private static final String PATH = "/core/smssetting/priceInsert";
    private static final String BODY =
            "{\"id_col_03\":\"COHS\",\"sms_price\":\"1\",\"lms_price\":\"1\",\"mms_price\":\"1\"}";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PageController()).build();
    }

    private MockHttpSession sessionOf(String inst) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("inst", inst);
        return session;
    }

    // ── 거부 ──────────────────────────────────────────────────

    /** ⭐ core 계정이어도 거부한다. 권한 문제가 아니라 <b>경로가 폐지된 것</b>이다. */
    @Test
    void core_계정이_보내도_410_으로_거부한다() throws Exception {
        mvc.perform(post(PATH)
                        .session(sessionOf("core"))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isGone());
    }

    /**
     * 응답이 <b>어디서 관리하는지</b> 를 알려줘야 한다.
     *
     * <p>410 만 돌려주면 예전 화면은 "저장 실패" 만 띄운다 — 운영자는 장애로 오해한다.
     */
    @Test
    void 응답이_어디서_관리하는지_알려준다() throws Exception {
        MvcResult result = mvc.perform(post(PATH)
                        .session(sessionOf("core"))
                        .contentType("application/json")
                        .content(BODY))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("MediCast");
        assertThat(body).contains("수정할 수 없습니다");
        assertThat(body).contains("\"result\":\"0\"");
    }

    /** 전체 단가 일괄 변경({@code id_col_03 = "all"})도 같이 막힌다. */
    @Test
    void 전체_단가_일괄_변경도_막힌다() throws Exception {
        mvc.perform(post(PATH)
                        .session(sessionOf("core"))
                        .contentType("application/json")
                        .content("{\"id_col_03\":\"all\",\"sms_price\":\"1\","
                                + "\"lms_price\":\"1\",\"mms_price\":\"1\"}"))
                .andExpect(status().isGone());
    }

    /** 본문이 비어도 500 이 아니라 410 이다. 예전 클라이언트가 무엇을 보내든 같은 답이다. */
    @Test
    void 본문이_없어도_410_이다() throws Exception {
        mvc.perform(post(PATH)
                        .session(sessionOf("core"))
                        .contentType("application/json"))
                .andExpect(status().isGone());
    }

    // ── 권한 검사는 그대로 앞선다 ──────────────────────────────

    /**
     * core 가 아니면 <b>403</b> 이다. 410 이 아니다.
     *
     * <p>순서가 중요하다 — 폐지 사실을 아무에게나 알려 줄 이유가 없고,
     * 권한 검사를 건너뛰면 나중에 이 자리에 다른 처리가 들어올 때 구멍이 된다.
     */
    @Test
    void 권한이_없으면_410_이_아니라_403_이다() throws Exception {
        mvc.perform(post(PATH)
                        .session(sessionOf("COHS"))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void 세션이_없어도_403_이다() throws Exception {
        mvc.perform(post(PATH)
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isForbidden());
    }
}
