package com.coresolution.mediplat.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.coresolution.mediplat.service.FleetDeviceTokenService;
import com.coresolution.mediplat.service.FleetService;

/**
 * 운전자 기기 인식 인터셉터 등록. mediplat 최초의 WebMvcConfigurer이며 관심사는 fleet에 한정한다.
 *
 * <p>등록 경로는 운전자 진입/작업 경로({@code /fleet/**})로 스코프를 제한한다. 관리자 경로(/ams류 없음,
 * mediplat은 자체 세션)나 정적 리소스({@code /css/**} 등)는 대상이 아니다.
 */
@Configuration
public class FleetMvcConfig implements WebMvcConfigurer {

    /** 기기 자동 인식(복원)을 시도할 경로. */
    public static final List<String> FLEET_DEVICE_PATHS = List.of("/fleet/**");

    private final FleetDeviceTokenService deviceTokenService;
    private final FleetService fleetService;

    public FleetMvcConfig(FleetDeviceTokenService deviceTokenService, FleetService fleetService) {
        this.deviceTokenService = deviceTokenService;
        this.fleetService = fleetService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new FleetDeviceInterceptor(deviceTokenService, fleetService))
                .addPathPatterns(FLEET_DEVICE_PATHS);
    }
}
