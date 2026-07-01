package com.coresolution.csm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubSessions;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 가드레일 ① 1차 차단선 검증: /hub/me/** 진입 시 세션 가드 동작.
 */
class HubAuthInterceptorTest {

    private final HubAuthInterceptor interceptor = new HubAuthInterceptor();

    @Test
    void anonymous_browserRequest_redirectsToLoginWithContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hub/me");
        request.setContextPath("/csm");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/csm/hub/login");
    }

    @Test
    void anonymous_ajaxRequest_returns401NotRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/hub/me/favorites/toggle");
        request.setContextPath("/csm");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void authenticatedMember_proceeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hub/me");
        request.setContextPath("/csm");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HubSessions.ATTR, new HubMemberSession(7L, "a@coresolution.kr", "홍길동", "USER"));
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
