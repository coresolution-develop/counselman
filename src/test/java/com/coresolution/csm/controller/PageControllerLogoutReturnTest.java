package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import com.coresolution.csm.config.SsoReturn;

/**
 * GET /logout: a user who came through SSO from moduplat goes back there; everyone else goes to MediPlat.
 */
class PageControllerLogoutReturnTest {

    private PageController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new PageController();
        set("mediplatPlatformBaseUrl", "https://mediplat.example/");
        set("ssoReturnAllow", "https://app.moduplat.com/");
    }

    private void set(String name, String value) throws Exception {
        Field f = PageController.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(controller, value);
    }

    private String logout(String remembered, String returnTo) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logout");
        MockHttpSession session = new MockHttpSession();
        if (remembered != null) {
            session.setAttribute(SsoReturn.SESSION_KEY, remembered);
        }
        request.setSession(session);
        String view = controller.logout(request, new MockHttpServletResponse(), session, returnTo, null);
        assertThat(session.isInvalid()).isTrue();
        return view;
    }

    @Test
    void cameFromModuplat_goesBackThere() {
        assertThat(logout("https://app.moduplat.com/", null)).isEqualTo("redirect:https://app.moduplat.com/");
    }

    @Test
    void cameFromMediplat_goesToMediplatAsBefore() {
        assertThat(logout(null, null)).isEqualTo("redirect:https://mediplat.example");
    }

    @Test
    void rememberedValueNoLongerAllowed_goesToMediplat() throws Exception {
        set("ssoReturnAllow", "");
        assertThat(logout("https://app.moduplat.com/", null)).isEqualTo("redirect:https://mediplat.example");
    }

    @Test
    void chatReturnStillWins() {
        assertThat(logout("https://app.moduplat.com/", "chat")).isEqualTo("redirect:/chat");
    }
}
