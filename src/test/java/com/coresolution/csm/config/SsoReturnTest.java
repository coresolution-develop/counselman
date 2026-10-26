package com.coresolution.csm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/**
 * The logout return url must always be a configured entry, never the value sent with the SSO link.
 */
class SsoReturnTest {

    private static final String ALLOW = "https://app.moduplat.com/, https://eval.moduplat.com";

    @Test
    void allowed_returnsTheConfiguredEntry_forAnExactMatch() {
        assertThat(SsoReturn.allowed("https://app.moduplat.com", ALLOW)).contains("https://app.moduplat.com/");
        assertThat(SsoReturn.allowed("HTTPS://APP.MODUPLAT.COM/", ALLOW)).contains("https://app.moduplat.com/");
        assertThat(SsoReturn.allowed("https://eval.moduplat.com/", ALLOW)).contains("https://eval.moduplat.com");
    }

    @Test
    void allowed_rejectsAnythingElse() {
        assertThat(SsoReturn.allowed("https://app.moduplat.com.evil.example", ALLOW)).isEmpty();
        assertThat(SsoReturn.allowed("https://app.moduplat.com@evil.example", ALLOW)).isEmpty();
        assertThat(SsoReturn.allowed("https://app.moduplat.com/path", ALLOW)).isEmpty();
        assertThat(SsoReturn.allowed("//app.moduplat.com", ALLOW)).isEmpty();
        assertThat(SsoReturn.allowed("javascript:alert(1)", ALLOW)).isEmpty();
        assertThat(SsoReturn.allowed("moduplat", ALLOW)).isEmpty();
        assertThat(SsoReturn.allowed(null, ALLOW)).isEmpty();
        // Empty list (default): never redirects anywhere but MediPlat
        assertThat(SsoReturn.allowed("https://app.moduplat.com", "")).isEmpty();
        assertThat(SsoReturn.allowed("https://app.moduplat.com", null)).isEmpty();
        // Unsafe entries in the config are ignored
        assertThat(SsoReturn.allowed("http://app.moduplat.com", "http://app.moduplat.com")).isEmpty();
        assertThat(SsoReturn.allowed("javascript:x", "javascript:x")).isEmpty();
    }

    @Test
    void remember_storesTheEntry_andClearsItForOtherEntries() {
        MockHttpSession session = new MockHttpSession();
        SsoReturn.remember(session, "https://app.moduplat.com", ALLOW);
        assertThat(session.getAttribute(SsoReturn.SESSION_KEY)).isEqualTo("https://app.moduplat.com/");
        // Same browser comes back through MediPlat (no marker)
        SsoReturn.remember(session, null, ALLOW);
        assertThat(session.getAttribute(SsoReturn.SESSION_KEY)).isNull();
        // A value outside the list is never stored
        SsoReturn.remember(session, "https://evil.example", ALLOW);
        assertThat(session.getAttribute(SsoReturn.SESSION_KEY)).isNull();
    }

    @Test
    void recall_readsSessionOrRequest_andRechecksTheCurrentList() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SsoReturn.SESSION_KEY, "https://app.moduplat.com/");
        request.setSession(session);
        assertThat(SsoReturn.recall(request, ALLOW)).contains("https://app.moduplat.com/");
        // Config changed since the user logged in
        assertThat(SsoReturn.recall(request, "https://other.moduplat.com")).isEmpty();
        // After the session is gone the logout filter's copy on the request is used
        MockHttpServletRequest after = new MockHttpServletRequest();
        after.setAttribute(SsoReturn.REQUEST_KEY, "https://app.moduplat.com/");
        assertThat(SsoReturn.recall(after, ALLOW)).contains("https://app.moduplat.com/");
        assertThat(SsoReturn.recall(new MockHttpServletRequest(), ALLOW)).isEmpty();
    }
}
