package com.coresolution.mediplat.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cache rules for the React/Babel pages (login, portal, institution admin).
 *
 * <p>application.properties sets no-store for all static resources, so the 3 MB
 * babel-standalone bundle was downloaded on every visit and the login page stayed
 * blank for several seconds (most noticeable right after logout).
 *
 * <ul>
 *   <li>{@code /vendor/**}: third-party bundles. Cached by the browser. Put the version
 *       in the file name when replacing one (e.g. react-18.3.1...), so a new file is
 *       a new URL.</li>
 *   <li>{@code /jsx/**}: our page code. {@code no-cache} = always revalidate, so a deploy
 *       is picked up immediately while an unchanged file comes back as 304.</li>
 * </ul>
 * Everything else keeps the global no-store setting.
 */
@Configuration
public class StaticResourceCacheConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
        registry.addResourceHandler("/jsx/**")
                .addResourceLocations("classpath:/static/jsx/")
                .setCacheControl(CacheControl.noCache());
    }

    /** Serve .jsx as text so server.compression gzips it (it was application/octet-stream). */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> jsxMimeMapping() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("jsx", "text/javascript;charset=UTF-8");
            factory.setMimeMappings(mappings);
        };
    }
}
