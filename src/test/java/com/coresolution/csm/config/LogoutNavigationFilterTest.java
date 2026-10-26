package com.coresolution.csm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * A background request to a GET logout url (extension fetch, prefetch) must not end the session; the user's own
 * navigation must. Requests without fetch metadata (old browsers, tools) log out as before.
 */
class LogoutNavigationFilterTest {

    private final LogoutNavigationFilter filter = new LogoutNavigationFilter();

    private MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/csm" + path);
        request.setContextPath("/csm");
        return request;
    }

    /** true when the request reached the controller (= logout runs) */
    private boolean passes(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() != null;
    }

    @Test
    void navigation_logsOut() throws Exception {
        MockHttpServletRequest request = get("/logout");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        request.addHeader("Sec-Fetch-Dest", "document");
        assertThat(passes(request, new MockHttpServletResponse())).isTrue();
    }

    @Test
    void noFetchMetadata_logsOutAsBefore() throws Exception {
        assertThat(passes(get("/logout"), new MockHttpServletResponse())).isTrue();
        MockHttpServletRequest chat = get("/logout");
        chat.setQueryString("returnTo=chat&t=abc");
        assertThat(passes(chat, new MockHttpServletResponse())).isTrue();
    }

    @Test
    void backgroundFetch_isIgnoredWith204() throws Exception {
        for (String mode : new String[] { "cors", "no-cors", "same-origin", "NO-CORS" }) {
            MockHttpServletRequest request = get("/logout");
            request.addHeader("Sec-Fetch-Mode", mode);
            request.addHeader("Sec-Fetch-Dest", "empty");
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(passes(request, response)).as(mode).isFalse();
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(response.getRedirectedUrl()).isNull();
        }
    }

    @Test
    void prefetch_isIgnored_evenAsANavigation() throws Exception {
        MockHttpServletRequest chrome = get("/logout");
        chrome.addHeader("Sec-Fetch-Mode", "navigate");
        chrome.addHeader("Sec-Purpose", "prefetch;prerender");
        assertThat(passes(chrome, new MockHttpServletResponse())).isFalse();
        MockHttpServletRequest old = get("/logout");
        old.addHeader("Purpose", "prefetch");
        assertThat(passes(old, new MockHttpServletResponse())).isFalse();
    }

    @Test
    void roomBoardReturn_isGuardedToo() throws Exception {
        MockHttpServletRequest request = get("/room-board/return");
        request.addHeader("Sec-Fetch-Mode", "cors");
        assertThat(passes(request, new MockHttpServletResponse())).isFalse();
        MockHttpServletRequest nav = get("/room-board/return/");
        nav.addHeader("Sec-Fetch-Mode", "navigate");
        assertThat(passes(nav, new MockHttpServletResponse())).isTrue();
    }

    @Test
    void otherRequests_areUntouched() throws Exception {
        // POST /logout is Spring Security's (CSRF token); other GETs are not logout
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/csm/logout");
        post.setContextPath("/csm");
        post.addHeader("Sec-Fetch-Mode", "cors");
        assertThat(passes(post, new MockHttpServletResponse())).isTrue();
        for (String path : new String[] { "/counsel/list", "/logout-all", "/hub/logout", "/api/chat/faqs" }) {
            MockHttpServletRequest request = get(path);
            request.addHeader("Sec-Fetch-Mode", "cors");
            assertThat(passes(request, new MockHttpServletResponse())).as(path).isTrue();
        }
    }
}
