package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.coresolution.mediplat.model.PlatformService;
import com.coresolution.mediplat.model.PlatformSessionUser;

class CounselManSsoLinkServiceTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createLaunchUrl_usesConfiguredExternalBaseUrl_whenServiceBaseUrlIsLoopback() {
        CounselManSsoLinkService linkService = newLinkService("https://dev.sosyge.net/csm");
        bindRequest("https", "dev.sosyge.net", 443);

        String launchUrl = linkService.createLaunchUrl(defaultService("http://localhost:8082/csm"), normalUser());
        URI uri = URI.create(launchUrl);
        Map<String, String> query = parseQuery(uri);

        assertEquals("https", uri.getScheme());
        assertEquals("dev.sosyge.net", uri.getHost());
        assertEquals(-1, uri.getPort());
        assertEquals("/csm/mediplat/sso/entry", uri.getPath());
        assertEquals("FALH", query.get("inst"));
        assertEquals("coreadmin", query.get("userId"));
        assertTrue(Long.parseLong(query.get("expires")) >= Instant.now().getEpochSecond());
        assertNotNull(query.get("signature"));
        assertFalse(query.get("signature").isBlank());
        assertEquals("/counsel/list?page=1&perPageNum=10", decodeTarget(query.get("target")));
    }

    @Test
    void createLaunchUrl_preservesConfiguredCounselManPort_whenRequestPortDiffers() {
        CounselManSsoLinkService linkService = newLinkService("http://localhost:8081/csm");
        bindRequest("http", "dev.sosyge.net", 8082);

        String launchUrl = linkService.createLaunchUrl(defaultService("http://localhost:8082/csm"), normalUser());
        URI uri = URI.create(launchUrl);

        assertEquals("http", uri.getScheme());
        assertEquals("dev.sosyge.net", uri.getHost());
        assertEquals(8081, uri.getPort());
        assertEquals("/csm/mediplat/sso/entry", uri.getPath());
    }

    @Test
    void createLaunchUrl_usesAdminTarget_forPlatformAdminUser() {
        CounselManSsoLinkService linkService = newLinkService("https://dev.sosyge.net/csm");

        String launchUrl = linkService.createLaunchUrl(defaultService("http://localhost:8082/csm"), adminUser());
        Map<String, String> query = parseQuery(URI.create(launchUrl));

        assertEquals("/admin/main", decodeTarget(query.get("target")));
    }

    private CounselManSsoLinkService newLinkService(String configuredBaseUrl) {
        CounselManSsoLinkService service = new CounselManSsoLinkService();
        ReflectionTestUtils.setField(service, "configuredCounselManBaseUrl", configuredBaseUrl);
        ReflectionTestUtils.setField(service, "sharedSecret", "test-shared-secret");
        ReflectionTestUtils.setField(service, "expireSeconds", 120L);
        return service;
    }

    private PlatformService defaultService(String baseUrl) {
        return new PlatformService(
                1L,
                "counselman",
                "CounselMan",
                baseUrl,
                null,
                null,
                null,
                "/mediplat/sso/entry",
                "/counsel/list?page=1&perPageNum=10",
                "/admin/main",
                "description",
                "Y",
                1,
                "Y");
    }

    private PlatformSessionUser normalUser() {
        return new PlatformSessionUser("FALH", "coreadmin", "Core Admin", "USER");
    }

    private PlatformSessionUser adminUser() {
        return new PlatformSessionUser("core", "coreadmin", "Platform Admin", "PLATFORM_ADMIN");
    }

    private void bindRequest(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new LinkedHashMap<>();
        if (uri.getQuery() == null || uri.getQuery().isBlank()) {
            return map;
        }
        for (String part : uri.getQuery().split("&")) {
            int separator = part.indexOf('=');
            String key = separator >= 0 ? part.substring(0, separator) : part;
            String value = separator >= 0 ? part.substring(separator + 1) : "";
            map.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return map;
    }

    private String decodeTarget(String targetToken) {
        return new String(Base64.getUrlDecoder().decode(targetToken), StandardCharsets.UTF_8);
    }
}
