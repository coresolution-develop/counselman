package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * error/403 replaces the Whitelabel page for signed-in users without a role (e.g. staff who came through SSO before
 * an admin gave them one). It must render without any model attributes — Spring Boot's error view passes none we use.
 */
class AccessDeniedPageTest {

    @Test
    void rendersWithoutModel_andLinksUnderTheContextPath() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        MockServletContext servletContext = new MockServletContext();
        servletContext.setContextPath("/csm");
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/csm/counsel/list");
        request.setContextPath("/csm");
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, new MockHttpServletResponse());

        String html = engine.process("error/403", new WebContext(exchange));

        assertThat(html).contains("이 화면을 열 권한이 없어요", "관리자에게 역할");
        assertThat(html).contains("href=\"/csm/\"", "href=\"/csm/logout\"");
        assertThat(html).doesNotContain("Whitelabel");
    }
}
