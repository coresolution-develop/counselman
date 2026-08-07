package com.coresolution.mediplat.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coresolution.mediplat.model.FleetDriverPrincipal;
import com.coresolution.mediplat.model.FleetTripLog;
import com.coresolution.mediplat.model.FleetVehicle;
import com.coresolution.mediplat.service.FleetPhotoService;
import com.coresolution.mediplat.service.FleetService;
import com.coresolution.mediplat.web.FleetSessions;

import jakarta.servlet.http.HttpSession;

/**
 * 운전자 운행 기록 API(출발/도착). 기기 신원(FleetDeviceInterceptor로 복원된 세션)이 필요하다.
 *
 * <p>사진은 DB 트랜잭션 밖에서 먼저 저장하고(되짚음 ①), 서비스 트랜잭션이 실패하면 방금 쓴 파일을
 * best-effort 삭제한다(되짚음 ④). 계기판 값 검증(단조증가·종료&gt;시작·상한)은 서버가 재수행한다(되짚음 ②).
 */
@RestController
public class FleetTripController {

    private final FleetService fleetService;
    private final FleetPhotoService photoService;

    public FleetTripController(FleetService fleetService, FleetPhotoService photoService) {
        this.fleetService = fleetService;
        this.photoService = photoService;
    }

    /** QR 토큰으로 차량 + 현재 운행 상태를 해석한다. */
    @GetMapping("/fleet/vehicles/by-token/{qrToken}")
    public ResponseEntity<?> byToken(@PathVariable String qrToken, HttpSession session) {
        FleetDriverPrincipal principal = FleetSessions.current(session);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("registered", false));
        }
        FleetVehicle vehicle = fleetService.findVehicleByQrToken(qrToken);
        if (vehicle == null || !vehicle.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "차량을 찾을 수 없습니다."));
        }
        FleetTripLog ongoing = fleetService.findOngoingTripByVehicle(vehicle.getInstCode(), vehicle.getId());

        Map<String, Object> vehiclePayload = new HashMap<>();
        vehiclePayload.put("id", vehicle.getId());
        vehiclePayload.put("plateNo", vehicle.getPlateNo());
        vehiclePayload.put("name", vehicle.getName());
        vehiclePayload.put("statusCode", vehicle.getStatusCode());
        vehiclePayload.put("currentOdometer", vehicle.getCurrentOdometer());

        Map<String, Object> body = new HashMap<>();
        body.put("vehicle", vehiclePayload);
        boolean mine = ongoing != null && principal.getDriverId() != null
                && principal.getDriverId().equals(ongoing.getDriverId());
        if (ongoing != null && mine) {
            Map<String, Object> ongoingPayload = new HashMap<>();
            ongoingPayload.put("tripId", ongoing.getId());
            ongoingPayload.put("departAt", ongoing.getDepartAt());
            ongoingPayload.put("odometerStart", ongoing.getOdometerStart());
            body.put("ongoingTrip", ongoingPayload);
        } else {
            body.put("ongoingTrip", null);
        }
        body.put("occupiedByOther", ongoing != null && !mine);
        return ResponseEntity.ok(body);
    }

    /** 출발 기록(계기판 사진 필수). */
    @PostMapping("/fleet/trips/depart")
    public ResponseEntity<?> depart(
            @RequestParam("vehicleId") Long vehicleId,
            @RequestParam("purpose") String purpose,
            @RequestParam(value = "purposeMemo", required = false) String purposeMemo,
            @RequestParam("odometerStart") Integer odometerStart,
            @RequestParam(value = "odometerStartSrc", required = false) String odometerStartSrc,
            @RequestParam("photo") MultipartFile photo,
            HttpSession session) {
        FleetDriverPrincipal principal = FleetSessions.current(session);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("registered", false));
        }

        String photoPath;
        try {
            photoPath = photoService.store(principal.getInstCode(), photo); // 트랜잭션 밖 저장(되짚음 ①)
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", messageOf(e, "사진 저장에 실패했습니다.")));
        }

        try {
            FleetTripLog trip = fleetService.depart(
                    principal.getInstCode(), vehicleId, principal.getDriverId(), principal.getUsername(),
                    purpose, purposeMemo, odometerStart, odometerStartSrc, photoPath);
            Map<String, Object> ok = new HashMap<>();
            ok.put("tripId", trip.getId());
            ok.put("status", trip.getStatusCode());
            ok.put("departAt", trip.getDepartAt());
            return ResponseEntity.status(HttpStatus.CREATED).body(ok);
        } catch (RuntimeException e) {
            photoService.delete(photoPath); // 트랜잭션 실패 → 고아 파일 삭제(되짚음 ④)
            return failure(e);
        }
    }

    /** 도착 기록(종료 계기판 사진 필수). */
    @PostMapping("/fleet/trips/arrive")
    public ResponseEntity<?> arrive(
            @RequestParam("tripId") Long tripId,
            @RequestParam("odometerEnd") Integer odometerEnd,
            @RequestParam(value = "odometerEndSrc", required = false) String odometerEndSrc,
            @RequestParam("photo") MultipartFile photo,
            HttpSession session) {
        FleetDriverPrincipal principal = FleetSessions.current(session);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("registered", false));
        }

        String photoPath;
        try {
            photoPath = photoService.store(principal.getInstCode(), photo);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", messageOf(e, "사진 저장에 실패했습니다.")));
        }

        try {
            FleetTripLog trip = fleetService.arrive(
                    principal.getInstCode(), tripId, principal.getDriverId(),
                    odometerEnd, odometerEndSrc, photoPath);
            Map<String, Object> ok = new HashMap<>();
            ok.put("tripId", trip.getId());
            ok.put("status", trip.getStatusCode());
            ok.put("distance", trip.getDistance());
            ok.put("overLimit", trip.isOverLimit());
            return ResponseEntity.ok(ok);
        } catch (RuntimeException e) {
            photoService.delete(photoPath); // 트랜잭션 실패 → 고아 파일 삭제(되짚음 ④)
            return failure(e);
        }
    }

    /** 상태 충돌(IllegalState)은 409, 검증 오류(IllegalArgument)는 422로 매핑. */
    private ResponseEntity<?> failure(RuntimeException e) {
        HttpStatus status = e instanceof IllegalStateException
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(Map.of("message", messageOf(e, "처리에 실패했습니다.")));
    }

    private String messageOf(RuntimeException e, String fallback) {
        return e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage();
    }
}
