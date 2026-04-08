package com.coresolution.csm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Legacy servlet-context.xml: /resources/** -> /resources/
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("classpath:/static/");
    }
}
