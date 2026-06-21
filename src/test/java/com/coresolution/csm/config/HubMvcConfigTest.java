package com.coresolution.csm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/**
 * 가드레일 ① 인터셉터 경로 등록 검증: 컨텍스트 경로 유무 양쪽(/hub/me/**·/csm/hub/me/**)이
 * 모두 등록 대상에 포함되어야 한다.
 */
class HubMvcConfigTest {

    @Test
    void authPaths_coverBothContextFormats() {
        assertThat(HubMvcConfig.HUB_AUTH_PATHS)
                .contains("/hub/me/**", "/csm/hub/me/**");
    }

    @Test
    void addInterceptors_registersWithoutError() {
        HubMvcConfig config = new HubMvcConfig();
        assertThatCode(() -> config.addInterceptors(new InterceptorRegistry()))
                .doesNotThrowAnyException();
    }
}
