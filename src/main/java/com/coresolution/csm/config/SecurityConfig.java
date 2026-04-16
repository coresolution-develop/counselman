package com.coresolution.csm.config;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${mediplat.platform.base-url:http://localhost:8082}")
        private String mediplatPlatformBaseUrl;

        private static final String[] PUBLIC_PATHS = {
                        "/login", "/login/**", "/csm/login", "/csm/login/**",
                        "/mediplat/sso/entry", "/csm/mediplat/sso/entry",
                        "/room-board", "/csm/room-board",
                        "/room-board/return", "/csm/room-board/return",
                        "/findpwd", "/findpwd/**", "/csm/findpwd", "/csm/findpwd/**",
                        "/ResetPwd", "/ResetPwd/**", "/csm/ResetPwd", "/csm/ResetPwd/**",
                        "/api/external/SMSRequest", "/csm/api/external/SMSRequest",
                        "/resources/**", "/csm/resources/**",
                        "/error", "/error/**", "/csm/error", "/csm/error/**",
                        "/css/**", "/js/**", "/img/**", "/webjars/**", "/icon/**", "/fonts/**", "/public/**",
                        "/csm/css/**", "/csm/js/**", "/csm/img/**", "/csm/webjars/**", "/csm/icon/**",
                        "/csm/fonts/**", "/csm/public/**",
                        "/favicon.ico", "/favicon/**", "/csm/favicon.ico", "/csm/favicon/**"
        };

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(PUBLIC_PATHS)
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .cors(Customizer.withDefaults())
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.disable())
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(buildFrameAncestorsPolicy())))
                                .csrf(csrf -> csrf.ignoringRequestMatchers(
                                                new AntPathRequestMatcher("/api/external/SMSRequest", "POST"),
                                                new AntPathRequestMatcher("/csm/api/external/SMSRequest", "POST")))
                                .exceptionHandling(ex -> ex
                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                new AntPathRequestMatcher("/api/**"))
                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                new AntPathRequestMatcher("/csm/api/**"))
                                                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                                // .formLogin(f -> f
                                // .loginPage("/login") // 내 로그인 페이지 사용
                                // .loginProcessingUrl("/login/post")// 폼 action과 동일
                                // .usernameParameter("us_col_02") // 아이디 input name
                                // .passwordParameter("us_col_03") // 비번 input name
                                // .authenticationDetailsSource(
                                // req -> new InstDetails(req.getParameter("us_col_04")))
                                // .successHandler((req, res, auth) -> { // ★ JSON 성공 응답
                                // res.setCharacterEncoding("UTF-8");
                                // res.setContentType("application/json");
                                // res.getWriter().write(
                                // "{\"result\":\"1\",\"message\":\"로그인 성공\"}");
                                // })
                                // .failureHandler((req, res, ex) -> { // ★ JSON 실패 응답
                                // res.setCharacterEncoding("UTF-8");
                                // res.setContentType("application/json");
                                // res.setStatus(401);
                                // res.getWriter().write(
                                // "{\"result\":\"0\",\"message\":\"아이디/비밀번호/기관코드가 올바르지 않습니다.\"}");
                                // })
                                // .permitAll())

                                // ★ 폼 로그인 필터가 /login/post를 가로채지 않도록 비활성화
                                .formLogin(form -> form.disable())
                                .httpBasic(basic -> basic.disable())
                                .logout(l -> l.logoutUrl("/logout")
                                                .logoutSuccessUrl(resolveMediplatRedirectUrl())
                                                .permitAll());
                return http.build();

        }

        private String resolveMediplatRedirectUrl() {
                String normalized = StringUtils.hasText(mediplatPlatformBaseUrl)
                                ? mediplatPlatformBaseUrl.trim()
                                : "http://localhost:8082";
                while (normalized.endsWith("/")) {
                        normalized = normalized.substring(0, normalized.length() - 1);
                }
                return normalized.isBlank() ? "http://localhost:8082" : normalized;
        }

        private String buildFrameAncestorsPolicy() {
                String mediplatOrigin = extractOrigin(mediplatPlatformBaseUrl);
                if (StringUtils.hasText(mediplatOrigin)) {
                        return "frame-ancestors 'self' " + mediplatOrigin + ";";
                }
                return "frame-ancestors 'self';";
        }

        private String extractOrigin(String rawUrl) {
                if (!StringUtils.hasText(rawUrl)) {
                        return null;
                }
                try {
                        URI uri = URI.create(rawUrl.trim());
                        if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                                return null;
                        }
                        String scheme = uri.getScheme().toLowerCase();
                        String host = uri.getHost().toLowerCase();
                        int port = uri.getPort();
                        if (port < 0
                                        || ("http".equals(scheme) && port == 80)
                                        || ("https".equals(scheme) && port == 443)) {
                                return scheme + "://" + host;
                        }
                        return scheme + "://" + host + ":" + port;
                } catch (IllegalArgumentException e) {
                        return null;
                }
        }

        @Bean
        AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
                return cfg.getAuthenticationManager();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(List.of("*"));
                configuration.setAllowedMethods(List.of("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE"));
                configuration.setAllowedHeaders(List.of(
                                "Content-Type",
                                "X-Requested-With",
                                "accept",
                                "Origin",
                                "Access-Control-Request-Method",
                                "Access-Control-Request-Headers"));
                configuration.setExposedHeaders(List.of(
                                "Access-Control-Allow-Origin",
                                "Access-Control-Allow-Credentials"));
                configuration.setAllowCredentials(false);
                configuration.setMaxAge(10L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
