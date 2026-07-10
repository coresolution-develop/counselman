package com.coresolution.mediplat.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.mediplat.model.FleetDriver;
import com.coresolution.mediplat.model.FleetTripLog;
import com.coresolution.mediplat.model.FleetVehicle;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.coresolution.mediplat.service.FleetPhotoService;
import com.coresolution.mediplat.service.FleetService;

import jakarta.servlet.http.HttpSession;

/**
 * 차량운행관리 관리자 콘솔. 세션 {@code mediplatUser}(플랫폼/기관 관리자)로 게이트한다.
 *
 * <p><b>스코프(의식적 결정)</b>: 모든 조회·등록은 관리자 세션의 {@code instCode}로 스코프된다.
 * 즉 core 관리자는 core 전체 운행/차량/사진을 볼 수 있다(사내 단일 테넌트 운영 기준). 다른 기관의
 * 데이터에는 접근할 수 없다.
 */
@Controller
public class FleetAdminController {

    private final FleetService fleetService;
    private final FleetPhotoService photoService;

    public FleetAdminController(FleetService fleetService, FleetPhotoService photoService) {
        this.fleetService = fleetService;
        this.photoService = photoService;
    }

    // ── 차량 · 운전자 ────────────────────────────────────────────────────
    @GetMapping("/fleet/admin")
    public String adminHome(HttpSession session, Model model) {
        PlatformSessionUser user = requireAdmin(session);
        if (user == null) {
            return redirectForGuest(session);
        }
        model.addAttribute("user", user);
        model.addAttribute("vehicles", fleetService.listVehicles(user.getInstCode()));
        model.addAttribute("drivers", fleetService.listDrivers(user.getInstCode()));
        return "fleet-admin";
    }

    @PostMapping("/fleet/admin/vehicles")
    public String registerVehicle(
            @RequestParam("plateNo") String plateNo,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "initialOdometer", required = false) Integer initialOdometer,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = requireAdmin(session);
        if (user == null) {
            return redirectForGuest(session);
        }
        try {
            fleetService.registerVehicle(user.getInstCode(), plateNo, name, modelName, department, initialOdometer);
            redirectAttributes.addFlashAttribute("message", "차량이 등록되었습니다.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", messageOf(e, "차량 등록에 실패했습니다."));
        }
        return "redirect:/fleet/admin";
    }

    @PostMapping("/fleet/admin/vehicles/{vehicleId}/qr")
    public String regenerateQr(
            @PathVariable Long vehicleId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = requireAdmin(session);
        if (user == null) {
            return redirectForGuest(session);
        }
        try {
            fleetService.regenerateQrToken(user.getInstCode(), vehicleId);
            redirectAttributes.addFlashAttribute("message", "QR을 재발급했습니다. 기존 인쇄물은 폐기하세요.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", messageOf(e, "QR 재발급에 실패했습니다."));
        }
        return "redirect:/fleet/admin";
    }

    @PostMapping("/fleet/admin/drivers")
    public String registerDriver(
            @RequestParam("name") String name,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "employeeNumber", required = false) String employeeNumber,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "phone", required = false) String phone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = requireAdmin(session);
        if (user == null) {
            return redirectForGuest(session);
        }
        try {
            fleetService.registerDriver(user.getInstCode(), name, username, employeeNumber, department, phone);
            redirectAttributes.addFlashAttribute("message", "운전자가 등록되었습니다.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", messageOf(e, "운전자 등록에 실패했습니다."));
        }
        return "redirect:/fleet/admin";
    }

    // ── 운행 기록 ────────────────────────────────────────────────────────
    @GetMapping("/fleet/admin/trips")
    public String trips(
            @RequestParam(value = "vehicleId", required = false) Long vehicleId,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            HttpSession session,
            Model model) {
        PlatformSessionUser user = requireAdmin(session);
        if (user == null) {
            return redirectForGuest(session);
        }
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        List<FleetTripLog> trips = fleetService.listTrips(user.getInstCode(), vehicleId, purpose, fromDate, toDate);

        Map<Long, FleetVehicle> vehiclesById = new LinkedHashMap<>();
        for (FleetVehicle vehicle : fleetService.listVehicles(user.getInstCode())) {
            vehiclesById.put(vehicle.getId(), vehicle);
        }
        Map<Long, FleetDriver> driversById = new LinkedHashMap<>();
        for (FleetDriver driver : fleetService.listDrivers(user.getInstCode())) {
            driversById.put(driver.getId(), driver);
        }

        model.addAttribute("user", user);
        model.addAttribute("trips", trips);
        model.addAttribute("vehicles", vehiclesById.values());
        model.addAttribute("vehiclesById", vehiclesById);
        model.addAttribute("driversById", driversById);
        model.addAttribute("filterVehicleId", vehicleId);
        model.addAttribute("filterPurpose", purpose);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        return "fleet-admin-trips";
    }

    /**
     * 계기판 사진 스트리밍. IDOR 방지: (1) 관리자 세션 필수, (2) tripId를 <b>관리자 세션 inst로 스코프</b>
     * 조회해 존재 확인 — 다른 기관/임의 tripId 대입은 404. 파일은 base-dir 하위로만 해석된다.
     */
    @GetMapping("/fleet/admin/trips/{tripId}/photo")
    public ResponseEntity<Resource> tripPhoto(
            @PathVariable Long tripId,
            @RequestParam(value = "pos", defaultValue = "start") String pos,
            HttpSession session) {
        PlatformSessionUser user = requireAdmin(session);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        FleetTripLog trip = fleetService.findTrip(user.getInstCode(), tripId);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }
        String relativePath = "end".equalsIgnoreCase(pos) ? trip.getEndPhotoPath() : trip.getStartPhotoPath();
        if (!StringUtils.hasText(relativePath)) {
            return ResponseEntity.notFound().build();
        }
        Path file = photoService.resolve(relativePath);
        if (file == null || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(mediaTypeFor(relativePath))
                .body(new FileSystemResource(file));
    }

    // ── 내부 ──────────────────────────────────────────────────────────────
    private PlatformSessionUser requireAdmin(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(MediplatController.SESSION_USER);
        if (!(value instanceof PlatformSessionUser user)) {
            return null;
        }
        return (user.isPlatformAdmin() || user.isInstitutionAdmin()) ? user : null;
    }

    /** 미로그인/비관리자 공통 리다이렉트: 로그인 안 됐으면 로그인, 로그인은 됐으나 권한 없으면 포털. */
    private String redirectForGuest(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(MediplatController.SESSION_USER);
        return value == null ? "redirect:/login" : "redirect:/portal";
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private MediaType mediaTypeFor(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    private String messageOf(RuntimeException e, String fallback) {
        return e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage();
    }
}
