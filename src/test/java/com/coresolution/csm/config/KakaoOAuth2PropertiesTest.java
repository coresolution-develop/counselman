package com.coresolution.csm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Regression: Kakao OAuth2 client registration must live in the common
 * application.properties so it's available in all profiles (local/dev/prod).
 *
 * Before this fix the kakao config existed only in application-local.properties,
 * which caused the prod profile to skip `oauth2Login` wiring in SecurityConfig
 * (no ClientRegistrationRepository bean) — clicking the Kakao login button on
 * `/csm/chat` then fell through to `LoginUrlAuthenticationEntryPoint("/login")`
 * and redirected users to the staff login page.
 */
class KakaoOAuth2PropertiesTest {

    @Test
    void kakaoClientPropertiesArePresentInCommonApplicationProperties() throws Exception {
        Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(props.getProperty("spring.security.oauth2.client.registration.kakao.client-id"))
                .as("client-id placeholder (env override or default)")
                .isNotBlank();
        assertThat(props.getProperty("spring.security.oauth2.client.registration.kakao.client-secret"))
                .as("client-secret placeholder (env override or default)")
                .isNotBlank();
        assertThat(props.getProperty("spring.security.oauth2.client.registration.kakao.redirect-uri"))
                .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
        assertThat(props.getProperty("spring.security.oauth2.client.provider.kakao.authorization-uri"))
                .isEqualTo("https://kauth.kakao.com/oauth/authorize");
        assertThat(props.getProperty("spring.security.oauth2.client.provider.kakao.user-name-attribute"))
                .isEqualTo("id");
    }

    @Test
    void localProfileMustNotShadowCommonKakaoConfig() throws Exception {
        Properties localProps = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application-local.properties"));

        assertThat(localProps.getProperty("spring.security.oauth2.client.registration.kakao.client-id"))
                .as("local profile should inherit kakao config from application.properties")
                .isNull();
    }
}
