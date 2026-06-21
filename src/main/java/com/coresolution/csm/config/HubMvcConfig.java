package com.coresolution.csm.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 허브 개인화 경로의 인터셉터 등록. 기존 WebMvcResourceConfig와 관심사를 분리.
 *
 * <p>컨텍스트 경로(/csm)가 프록시에서 벗겨지는지 여부에 따라 경로가 두 형태로
 * 들어올 수 있어 {@code /hub/me/**} 와 {@code /csm/hub/me/**} 를 모두 등록한다(가드레일 ①).
 */
@Configuration
public class HubMvcConfig implements WebMvcConfigurer {

    /** 허브 회원 인증이 필요한 경로 패턴(컨텍스트 경로 유무 양쪽). */
    public static final List<String> HUB_AUTH_PATHS = List.of("/hub/me/**", "/csm/hub/me/**");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HubAuthInterceptor())
                .addPathPatterns(HUB_AUTH_PATHS);
    }
}
