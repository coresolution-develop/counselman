package com.coresolution.mediplat.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
 * 운전자 기기 신원 API. 관리자 로그인과 무관한 경량 신원으로, FleetDeviceInterceptor가
 * 요청 전 세션을 복원한다.
 *
 * <p>P0는 사내 단일 테넌트({@code core})다. 운전자 풀은 fleet 자체 로스터({@code mp_fleet_driver})에서
 * 가져온다.
 */
@RestController
public class FleetDriverController {

    /** 사내 차량운행은 단일 테넌트(core). */
    static final String FLEET_INST_CODE = "core";

    private final FleetService fleetService;
    private final FleetDeviceTokenService deviceTokenService;

    public FleetDriverController(FleetService fleetService, FleetDeviceTokenService deviceTokenService) {
        this.fleetService = fleetService;
        this.deviceTokenService = deviceTokenService;
    }

    /** 현재 기기로 인식된 운전자. 미등록이면 registered=false. */
    @GetMapping("/fleet/me")
    public ResponseEntity<?> me(HttpSession session) {
        FleetDriverPrincipal principal = FleetSessions.current(session);
        if (principal == null) {
            return ResponseEntity.ok(Map.of("registered", false));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("registered", true);
        body.put("driver", principalPayload(principal));
        return ResponseEntity.ok(body);
    }

    /** 최초 등록용 운전자 선택 목록(로스터). */
    @GetMapping("/fleet/drivers")
    public ResponseEntity<?> drivers() {
        List<Map<String, Object>> items = fleetService.listDrivers(FLEET_INST_CODE).stream()
                .filter(FleetDriver::isEnabled)
                .map(this::driverPayload)
                .toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    /** 기기 등록: 로스터에서 고른 운전자에 이 기기를 바인딩하고 쿠키를 발급한다. */
    @PostMapping("/fleet/devices/register")
    public ResponseEntity<?> registerDevice(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session) {
        Long driverId = parseLong(body == null ? null : body.get("driverId"));
        if (driverId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "운전자를 선택해 주세요."));
        }
        FleetDriver driver = fleetService.findDriverById(driverId);
        if (driver == null || !driver.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "등록 가능한 운전자를 찾을 수 없습니다."));
        }

        String cookieValue = deviceTokenService.issue(driver.getId(), request.getHeader("User-Agent"));
        FleetDeviceCookies.write(response, cookieValue,
                deviceTokenService.getCookieMaxAgeSeconds(), deviceTokenService.isCookieSecure());
        FleetDriverPrincipal principal = new FleetDriverPrincipal(
                driver.getId(), driver.getInstCode(), driver.getName(), driver.getUsername());
        FleetSessions.store(session, principal);

        Map<String, Object> ok = new HashMap<>();
        ok.put("registered", true);
        ok.put("driver", driverPayload(driver));
        return ResponseEntity.ok(ok);
    }

    private Map<String, Object> driverPayload(FleetDriver driver) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", driver.getId());
        payload.put("name", driver.getName());
        payload.put("department", driver.getDepartment());
        return payload;
    }

    private Map<String, Object> principalPayload(FleetDriverPrincipal principal) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", principal.getDriverId());
        payload.put("name", principal.getName());
        return payload;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
