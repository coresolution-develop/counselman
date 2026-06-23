package com.coresolution.links.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Mirrors csm's stance for the hub: every route is permitAll at the Spring
 * Security layer. Hub member authorization is enforced independently by
 * HubAuthInterceptor + controller guards + member_id-scoped queries (see
 * HubMvcConfig). CSRF stays enabled (Spring Security default) so the hub
 * templates' ${_csrf} tokens render and POST forms validate. Form login /
 * basic auth are disabled — the hub has its own /hub/login.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }
}
