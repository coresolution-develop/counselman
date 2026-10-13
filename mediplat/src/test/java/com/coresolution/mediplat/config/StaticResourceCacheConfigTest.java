package com.coresolution.mediplat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Regression test for the blank login page after logout: the 3 MB babel-standalone
 * bundle was served with no-store and re-downloaded on every visit.
 */
@SpringJUnitWebConfig(StaticResourceCacheConfigTest.TestConfig.class)
class StaticResourceCacheConfigTest {

    @Configuration
    @EnableWebMvc
    @Import(StaticResourceCacheConfig.class)
    static class TestConfig {
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StaticResourceCacheConfig config;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void vendorBundlesAreCacheable() throws Exception {
        mockMvc.perform(get("/vendor/babel-standalone-7.29.0.min.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=2592000, public"));
    }

    @Test
    void jsxIsAlwaysRevalidated() throws Exception {
        mockMvc.perform(get("/jsx/login-app.jsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void jsxIsServedAsTextSoItCanBeCompressed() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        config.jsxMimeMapping().customize(factory);
        assertEquals("text/javascript;charset=UTF-8", factory.getMimeMappings().get("jsx"));
    }
}
