package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Verifies that the password-reset link is built from the explicit
 * {@code csm.base-url} property whenever it is configured, and only falls
 * back to {@code HttpServletRequest} headers when the property is empty.
 *
 * Background: relying on {@code request.getServerName()} is exposed to a
 * Host Header Injection vector — an attacker can set an arbitrary
 * {@code Host}/{@code X-Forwarded-Host} header and trick the email
 * containing the reset link into pointing at an attacker-controlled
 * domain.
 */
class PageControllerResetLinkTest {

    @Mock private HttpServletRequest request;

    private PageController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PageController();
        // request headers are only consulted on fallback; default them so
        // the host-injection scenarios are reproducible.
        lenient().when(request.getScheme()).thenReturn("http");
        lenient().when(request.getServerName()).thenReturn("attacker.example");
        lenient().when(request.getServerPort()).thenReturn(80);
        lenient().when(request.getContextPath()).thenReturn("/csm");
    }

    private void setCsmBaseUrl(String value) throws Exception {
        Field f = PageController.class.getDeclaredField("csmBaseUrl");
        f.setAccessible(true);
        f.set(controller, value);
    }

    @Test
    void buildResetLink_usesConfiguredBaseUrl_overRequestHost() throws Exception {
        setCsmBaseUrl("https://csm.sosyge.net/csm");

        String link = controller.buildResetLink(request, "42", "FALH", "TOKEN");

        assertThat(link).isEqualTo("https://csm.sosyge.net/csm/ResetPwd?us_col_01=42&inst=FALH&token=TOKEN");
        // Even though request.getServerName() returns "attacker.example",
        // the configured base wins.
        assertThat(link).doesNotContain("attacker.example");
    }

    @Test
    void buildResetLink_stripsTrailingSlashFromConfiguredBase() throws Exception {
        setCsmBaseUrl("https://csm.sosyge.net/csm///");

        String link = controller.buildResetLink(request, "42", "FALH", "TOKEN");

        assertThat(link).isEqualTo("https://csm.sosyge.net/csm/ResetPwd?us_col_01=42&inst=FALH&token=TOKEN");
    }

    @Test
    void buildResetLink_fallsBackToRequest_whenBaseEmpty() throws Exception {
        setCsmBaseUrl("");
        when(request.getServerName()).thenReturn("csm.sosyge.net");
        when(request.getServerPort()).thenReturn(443);
        when(request.getScheme()).thenReturn("https");

        String link = controller.buildResetLink(request, "42", "FALH", "TOKEN");

        assertThat(link).isEqualTo("https://csm.sosyge.net/csm/ResetPwd?us_col_01=42&inst=FALH&token=TOKEN");
    }

    @Test
    void buildResetLink_fallsBackToRequest_whenBaseBlank() throws Exception {
        setCsmBaseUrl("   ");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8081);
        when(request.getScheme()).thenReturn("http");

        String link = controller.buildResetLink(request, "42", "FALH", "TOKEN");

        assertThat(link).isEqualTo("http://localhost:8081/csm/ResetPwd?us_col_01=42&inst=FALH&token=TOKEN");
    }

    @Test
    void resolveCsmBaseUrl_skipsPort80And443InFallback() throws Exception {
        setCsmBaseUrl("");
        when(request.getServerName()).thenReturn("csm.sosyge.net");
        when(request.getServerPort()).thenReturn(443);
        when(request.getScheme()).thenReturn("https");

        assertThat(controller.resolveCsmBaseUrl(request)).isEqualTo("https://csm.sosyge.net/csm");
    }
}
