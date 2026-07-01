package com.coresolution.csm.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.coresolution.csm.serivce.HubMemberService;
import com.coresolution.csm.serivce.HubRememberService;

/**
 * 허브 개인화 경로의 인터셉터 등록. 기존 WebMvcResourceConfig와 관심사를 분리.
 *
 * <p>컨텍스트 경로(/csm)가 프록시에서 벗겨지는지 여부에 따라 경로가 두 형태로
 * 들어올 수 있어 {@code /hub/me/**} 와 {@code /csm/hub/me/**} 를 모두 등록한다(가드레일 ①).
 *
 * <p>등록 순서: 자동 재로그인(HubRememberInterceptor)을 인가 가드(HubAuthInterceptor)보다 먼저
 * 둬, 쿠키로 세션을 복원한 뒤 가드가 평가되게 한다.
 */
@Configuration
public class HubMvcConfig implements WebMvcConfigurer {

    /** 허브 회원 인증이 필요한 경로 패턴(컨텍스트 경로 유무 양쪽). */
    public static final List<String> HUB_AUTH_PATHS = List.of("/hub/me/**", "/csm/hub/me/**");

    /** 자동 재로그인을 시도할 경로 — 공개 /links 진입 시에도 동작해야 한다. */
    public static final List<String> HUB_REMEMBER_PATHS =
            List.of("/links", "/csm/links", "/hub/**", "/csm/hub/**");

    private final HubRememberService hubRememberService;
    private final HubMemberService hubMemberService;

    public HubMvcConfig(HubRememberService hubRememberService, HubMemberService hubMemberService) {
        this.hubRememberService = hubRememberService;
        this.hubMemberService = hubMemberService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1) 쿠키 기반 자동 재로그인(복원만, 차단 안 함)
        registry.addInterceptor(new HubRememberInterceptor(hubRememberService, hubMemberService))
                .addPathPatterns(HUB_REMEMBER_PATHS);
        // 2) /hub/me/** 인가 가드
        registry.addInterceptor(new HubAuthInterceptor())
                .addPathPatterns(HUB_AUTH_PATHS);
    }
}
