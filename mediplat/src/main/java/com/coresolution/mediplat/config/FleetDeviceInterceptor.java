package com.coresolution.mediplat.config;

import org.springframework.web.servlet.HandlerInterceptor;

import com.coresolution.mediplat.model.FleetDriver;
import com.coresolution.mediplat.model.FleetDriverPrincipal;
import com.coresolution.mediplat.service.FleetDeviceTokenService;
import com.coresolution.mediplat.service.FleetService;
import com.coresolution.mediplat.web.FleetDeviceCookies;
import com.coresolution.mediplat.web.FleetSessions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 운전자 기기 자동 인식(복원 전용). 세션에 fleetDriver가 없고 FLEET_DEVICE 쿠키가 있으면
 * 토큰을 검증·회전해 세션을 복원한다.
 *
 * <p>이 인터셉터는 절대 요청을 막지 않는다(복원만 시도). 등록 여부에 따른 분기는 컨트롤러가 한다.
 * 세션이 이미 있으면 short-circuit 하므로 매 요청 회전이 일어나지 않아 병렬 회전 함정이 없다.
 */
public class FleetDeviceInterceptor implements HandlerInterceptor {

    private final FleetDeviceTokenService deviceTokenService;
    private final FleetService fleetService;

    public FleetDeviceInterceptor(FleetDeviceTokenService deviceTokenService, FleetService fleetService) {
        this.deviceTokenService = deviceTokenService;
        this.fleetService = fleetService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession existing = request.getSession(false);
        if (existing != null && FleetSessions.current(existing) != null) {
            return true; // 이미 인식됨 — 회전하지 않음
        }
        String cookieValue = FleetDeviceCookies.read(request);
        if (cookieValue == null) {
            return true; // 기억된 기기 아님 — 컨트롤러에서 등록 유도
        }
        boolean secure = deviceTokenService.isCookieSecure();
        FleetDeviceTokenService.Result result = deviceTokenService.validateAndRotate(
                cookieValue, request.getHeader("User-Agent"));
        if (!result.valid()) {
            FleetDeviceCookies.clear(response, secure); // 무효 토큰 → 쿠키 삭제
            return true;
        }
        FleetDriver driver = fleetService.findDriverById(result.driverId());
        if (driver == null || !driver.isEnabled()) {
            FleetDeviceCookies.clear(response, secure);
            return true;
        }
        // 세션 복원 + 회전된 쿠키 재발급
        FleetSessions.store(request.getSession(true), new FleetDriverPrincipal(
                driver.getId(), driver.getInstCode(), driver.getName(), driver.getUsername()));
        FleetDeviceCookies.write(response, result.newCookieValue(),
                deviceTokenService.getCookieMaxAgeSeconds(), secure);
        return true;
    }
}
