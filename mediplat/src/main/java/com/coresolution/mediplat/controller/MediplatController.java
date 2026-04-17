package com.coresolution.mediplat.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.DateTimeException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.util.StringUtils;

import com.coresolution.mediplat.model.PlatformInstitution;
import com.coresolution.mediplat.model.PlatformService;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.coresolution.mediplat.model.PlatformUser;
import com.coresolution.mediplat.model.RoomBoardViewerAccount;
import com.coresolution.mediplat.model.RoomBoardViewerInstitution;
import com.coresolution.mediplat.model.SeminarNotification;
import com.coresolution.mediplat.model.SeminarReservation;
import com.coresolution.mediplat.model.SeminarRoom;
import com.coresolution.mediplat.service.CounselManSsoLinkService;
import com.coresolution.mediplat.service.PlatformStoreService;
import com.coresolution.mediplat.service.SeminarRoomService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class MediplatController {

    private static final String SESSION_USER = "mediplatUser";
    private static final String SERVICE_CODE_ROOM_BOARD = "ROOM_BOARD";
    private static final String SERVICE_CODE_SEMINAR_ROOM = "SEMINAR_ROOM";

    private final PlatformStoreService storeService;
    private final CounselManSsoLinkService counselManSsoLinkService;
    private final SeminarRoomService seminarRoomService;

    public MediplatController(
            PlatformStoreService storeService,
            CounselManSsoLinkService counselManSsoLinkService,
            SeminarRoomService seminarRoomService) {
        this.storeService = storeService;
        this.counselManSsoLinkService = counselManSsoLinkService;
        this.seminarRoomService = seminarRoomService;
    }

    @GetMapping({ "", "/" })
    public String root(HttpSession session) {
        return sessionUser(session) == null ? "redirect:/login" : "redirect:/services";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(name = "error", required = false) String error, Model model, HttpSession session) {
        if (sessionUser(session) != null) {
            return "redirect:/services";
        }
        model.addAttribute("error", error);
        if (!model.containsAttribute("loginInstCode")) {
            model.addAttribute("loginInstCode", "");
        }
        if (!model.containsAttribute("loginUsername")) {
            model.addAttribute("loginUsername", "");
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = storeService.authenticate(instCode, username, password);
        if (user == null) {
            redirectAttributes.addAttribute("error", "등록된 기관코드/기관명 또는 계정정보가 올바르지 않습니다.");
            redirectAttributes.addFlashAttribute("loginInstCode", StringUtils.hasText(instCode) ? instCode.trim() : "");
            redirectAttributes.addFlashAttribute("loginUsername", StringUtils.hasText(username) ? username.trim() : "");
            return "redirect:/login";
        }
        session.setAttribute(SESSION_USER, user);
        return "redirect:/services";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/services")
    public String servicesPage(Model model, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (user.isRoomBoardViewer()) {
            return "redirect:/room-board-viewer";
        }
        List<PlatformService> services = storeService.listServicesForUser(user);
        List<PlatformInstitution> roomBoardViewerInstitutions = storeService.listRoomBoardViewerInstitutions(user);
        model.addAttribute("user", user);
        model.addAttribute("services", services);
        model.addAttribute("roomBoardViewerInstitutionCount", roomBoardViewerInstitutions.size());
        model.addAttribute("roomBoardViewerEnabled", !roomBoardViewerInstitutions.isEmpty());
        model.addAttribute("availableServices", services.stream().filter(PlatformService::isAccessible).toList());
        model.addAttribute("unavailableServices", services.stream().filter(service -> !service.isAccessible()).toList());
        return "services";
    }

    @GetMapping("/room-board-viewer")
    public String roomBoardViewerPage(Model model, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        PlatformService roomBoardLaunchService = resolveRoomBoardLaunchService();
        if (roomBoardLaunchService == null) {
            model.addAttribute("user", user);
            model.addAttribute("viewerItems", List.of());
            model.addAttribute("viewerError", "병실현황판 진입 서비스 설정이 없어 통합 병실현황판을 사용할 수 없습니다.");
            return "room-board-viewer";
        }

        List<RoomBoardViewerInstitution> viewerItems = new ArrayList<>();
        for (PlatformInstitution institution : storeService.listRoomBoardViewerInstitutions(user)) {
            if (institution == null || !StringUtils.hasText(institution.getInstCode())) {
                continue;
            }
            String launchUrl = counselManSsoLinkService.createLaunchUrl(
                    roomBoardLaunchService,
                    user,
                    institution.getInstCode(),
                    "/room-board?popup=1");
            viewerItems.add(new RoomBoardViewerInstitution(
                    institution.getInstCode(),
                    institution.getInstName(),
                    launchUrl));
        }

        model.addAttribute("user", user);
        model.addAttribute("viewerItems", viewerItems);
        model.addAttribute("viewerError", viewerItems.isEmpty()
                ? "조회 가능한 병실현황판 기관이 없습니다. 계정 권한 또는 기관 기능 설정을 확인해 주세요."
                : null);
        return "room-board-viewer";
    }

    @GetMapping("/launch/{serviceCode}")
    public String launchService(@PathVariable("serviceCode") String serviceCode, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        String normalizedServiceCode = serviceCode == null ? "" : serviceCode.trim().toUpperCase(Locale.ROOT);
        if (SERVICE_CODE_SEMINAR_ROOM.equals(normalizedServiceCode)) {
            PlatformService seminarRoomServiceEntry = storeService.findService(SERVICE_CODE_SEMINAR_ROOM);
            if (seminarRoomServiceEntry == null || !seminarRoomServiceEntry.isEnabled()) {
                return "redirect:/services";
            }
            List<String> enabledCodes = user.isPlatformAdmin()
                    ? List.of(SERVICE_CODE_SEMINAR_ROOM)
                    : storeService.listEnabledServiceCodesForUser(user);
            if (!user.isPlatformAdmin() && !enabledCodes.contains(SERVICE_CODE_SEMINAR_ROOM)) {
                return "redirect:/services";
            }
            return "redirect:/seminar-room";
        }
        if (SERVICE_CODE_ROOM_BOARD.equals(normalizedServiceCode)) {
            if (user.isPlatformAdmin()) {
                return "redirect:/services";
            }
            List<String> enabledCodes = storeService.listEnabledServiceCodesForUser(user);
            if (enabledCodes == null || !enabledCodes.contains(SERVICE_CODE_ROOM_BOARD)) {
                return "redirect:/services";
            }
            PlatformService roomBoardService = storeService.findService(SERVICE_CODE_ROOM_BOARD);
            if (roomBoardService != null && !roomBoardService.isEnabled()) {
                return "redirect:/services";
            }
            PlatformService roomBoardLaunchService = roomBoardService;
            if (roomBoardLaunchService == null) {
                if (!storeService.isRoomBoardCounselLinkEnabled(user.getInstCode())) {
                    return "redirect:/services";
                }
                roomBoardLaunchService = resolveRoomBoardLaunchService();
            }
            if (roomBoardLaunchService == null) {
                return "redirect:/services";
            }
            String launchUrl = counselManSsoLinkService.createLaunchUrl(
                    roomBoardLaunchService,
                    user,
                    user.getInstCode(),
                    "/room-board?popup=1");
            return "redirect:" + launchUrl;
        }
        var service = storeService.findService(normalizedServiceCode);
        if (service == null) {
            return "redirect:/services";
        }
        if (!service.isEnabled()) {
            return "redirect:/services";
        }
        List<String> enabledCodes = user.isPlatformAdmin()
                ? List.of(service.getServiceCode())
                : storeService.listEnabledServiceCodesForUser(user);
        if (!user.isPlatformAdmin() && !enabledCodes.contains(service.getServiceCode())) {
            return "redirect:/services";
        }
        String launchUrl = counselManSsoLinkService.createLaunchUrl(service, user);
        return "redirect:" + launchUrl;
    }

    private PlatformService resolveRoomBoardLaunchService() {
        PlatformService roomBoardService = storeService.findService(SERVICE_CODE_ROOM_BOARD);
        if (roomBoardService != null && roomBoardService.isEnabled()) {
            return roomBoardService;
        }
        PlatformService counselManService = storeService.findService("COUNSELMAN");
        if (counselManService != null && counselManService.isEnabled()) {
            return counselManService;
        }
        return null;
    }

    @GetMapping("/seminar-room")
    public String seminarRoomPage(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam(name = "seminarId", required = false) Long seminarId,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "error", required = false) String error,
            Model model,
            HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isServiceAccessible(user, SERVICE_CODE_SEMINAR_ROOM)) {
            return "redirect:/services";
        }
        String selectedInstCode = resolveServiceInstCode(user, instCode);
        if (!StringUtils.hasText(selectedInstCode)) {
            return "redirect:/services";
        }
        boolean canManageSeminars = user.isPlatformAdmin() || user.isInstitutionAdmin();
        List<PlatformInstitution> institutions = user.isPlatformAdmin()
                ? storeService.listInstitutions()
                : resolveAdminInstitutions(user, selectedInstCode);
        List<SeminarRoom> seminars = seminarRoomService.listSeminars(selectedInstCode);
        Long selectedSeminarId = resolveSelectedSeminarId(seminarId, seminars);
        List<PlatformUser> institutionUsers = canManageSeminars
                ? storeService.listInstitutionUsers(selectedInstCode).stream()
                        .filter(this::isActiveUser)
                        .toList()
                : List.of();
        List<String> selectedManagerUsernames = selectedSeminarId == null
                ? List.of()
                : seminarRoomService.listManagerUsernames(selectedInstCode, selectedSeminarId);
        List<SeminarReservation> myReservations = seminarRoomService.listMyReservations(selectedInstCode, user.getUsername());
        boolean seminarManager = seminarRoomService.isSeminarManager(selectedInstCode, user.getUsername());
        int unreadNotificationCount = seminarManager
                ? seminarRoomService.countUnreadNotifications(selectedInstCode, user.getUsername())
                : 0;
        List<SeminarNotification> notifications = seminarManager
                ? seminarRoomService.listNotifications(selectedInstCode, user.getUsername())
                : List.of();
        List<SeminarReservation> pendingReservations = seminarManager
                ? seminarRoomService.listPendingReservationsForManager(selectedInstCode, user.getUsername())
                : List.of();
        YearMonth calendarMonth = resolveCalendarMonth(year, month);
        LocalDate calendarStartDate = calendarMonth.atDay(1);
        LocalDate calendarEndDate = calendarMonth.atEndOfMonth();
        List<SeminarReservation> calendarReservations = seminarRoomService.listReservationsForCalendar(
                selectedInstCode,
                calendarStartDate,
                calendarEndDate);
        YearMonth previousMonth = calendarMonth.minusMonths(1);
        YearMonth nextMonth = calendarMonth.plusMonths(1);
        int calendarStartOffset = calendarStartDate.getDayOfWeek().getValue() % 7;

        model.addAttribute("user", user);
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        model.addAttribute("canManageSeminars", canManageSeminars);
        model.addAttribute("institutions", institutions);
        model.addAttribute("selectedInstCode", selectedInstCode);
        model.addAttribute("seminars", seminars);
        model.addAttribute("selectedSeminarId", selectedSeminarId);
        model.addAttribute("institutionUsers", institutionUsers);
        model.addAttribute("selectedManagerUsernames", selectedManagerUsernames);
        model.addAttribute("myReservations", myReservations);
        model.addAttribute("seminarManager", seminarManager);
        model.addAttribute("pendingReservations", pendingReservations);
        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadNotificationCount", unreadNotificationCount);
        model.addAttribute("calendarMonth", calendarMonth);
        model.addAttribute("calendarYearMonth", calendarMonth.toString());
        model.addAttribute("calendarMonthTitle", calendarMonth.getYear() + "년 " + calendarMonth.getMonthValue() + "월");
        model.addAttribute("calendarMonthLength", calendarMonth.lengthOfMonth());
        model.addAttribute("calendarStartOffset", calendarStartOffset);
        model.addAttribute("calendarPrevYear", previousMonth.getYear());
        model.addAttribute("calendarPrevMonth", previousMonth.getMonthValue());
        model.addAttribute("calendarNextYear", nextMonth.getYear());
        model.addAttribute("calendarNextMonth", nextMonth.getMonthValue());
        model.addAttribute("calendarToday", LocalDate.now().toString());
        model.addAttribute("calendarReservations", calendarReservations);
        return "seminar-room";
    }

    @GetMapping("/seminar-room/async-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> seminarRoomAsyncData(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam(name = "seminarId", required = false) Long seminarId,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요합니다."));
        }
        if (!isServiceAccessible(user, SERVICE_CODE_SEMINAR_ROOM)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "세미나실 서비스 접근 권한이 없습니다."));
        }
        String selectedInstCode = resolveServiceInstCode(user, instCode);
        if (!StringUtils.hasText(selectedInstCode)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "유효한 기관코드를 확인해 주세요."));
        }

        boolean canManageSeminars = user.isPlatformAdmin() || user.isInstitutionAdmin();
        List<SeminarRoom> seminars = seminarRoomService.listSeminars(selectedInstCode);
        Long selectedSeminarId = resolveSelectedSeminarId(seminarId, seminars);
        List<PlatformUser> institutionUsers = canManageSeminars
                ? storeService.listInstitutionUsers(selectedInstCode).stream()
                        .filter(this::isActiveUser)
                        .toList()
                : List.of();
        List<String> selectedManagerUsernames = selectedSeminarId == null
                ? List.of()
                : seminarRoomService.listManagerUsernames(selectedInstCode, selectedSeminarId);

        YearMonth calendarMonth = resolveCalendarMonth(year, month);
        LocalDate calendarStartDate = calendarMonth.atDay(1);
        LocalDate calendarEndDate = calendarMonth.atEndOfMonth();
        List<SeminarReservation> calendarReservations = seminarRoomService.listReservationsForCalendar(
                selectedInstCode,
                calendarStartDate,
                calendarEndDate);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selectedInstCode", selectedInstCode);
        payload.put("selectedSeminarId", selectedSeminarId);
        payload.put("seminars", seminars.stream().map(seminar -> {
            Map<String, Object> seminarMap = new LinkedHashMap<>();
            seminarMap.put("id", seminar.getId());
            seminarMap.put("seminarName", seminar.getSeminarName());
            seminarMap.put("roomName", seminar.getRoomName());
            seminarMap.put("capacity", seminar.getCapacity());
            seminarMap.put("useYn", seminar.getUseYn());
            seminarMap.put("enabled", seminar.isEnabled());
            return seminarMap;
        }).toList());
        payload.put("institutionUsers", institutionUsers.stream().map(institutionUser -> {
            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("username", institutionUser.getUsername());
            userMap.put("displayName", institutionUser.getDisplayName());
            return userMap;
        }).toList());
        payload.put("selectedManagerUsernames", selectedManagerUsernames);
        payload.put("calendarReservations", calendarReservations.stream().map(reservation -> {
            Map<String, Object> reservationMap = new LinkedHashMap<>();
            reservationMap.put("seminarId", reservation.getSeminarId());
            reservationMap.put("reservationDate", reservation.getReservationDate() == null ? "" : reservation.getReservationDate().toString());
            reservationMap.put("startTime", reservation.getStartTime() == null ? "" : reservation.getStartTime().toString());
            reservationMap.put("endTime", reservation.getEndTime() == null ? "" : reservation.getEndTime().toString());
            reservationMap.put("seminarName", reservation.getSeminarName());
            reservationMap.put("statusCode", reservation.getStatusCode());
            return reservationMap;
        }).toList());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/seminar-room/seminars")
    public String saveSeminarRoom(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam(name = "seminarId", required = false) Long seminarId,
            @RequestParam("seminarName") String seminarName,
            @RequestParam("roomName") String roomName,
            @RequestParam("capacity") Integer capacity,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!(user.isPlatformAdmin() || user.isInstitutionAdmin())) {
            return "redirect:/seminar-room";
        }
        String managedInstCode = resolveServiceInstCode(user, instCode);
        try {
            seminarRoomService.saveSeminar(
                    managedInstCode,
                    seminarId,
                    seminarName,
                    roomName,
                    capacity,
                    useYn,
                    user.getUsername());
            redirectAttributes.addAttribute("message", seminarId == null ? "세미나 정보가 저장되었습니다." : "세미나 정보가 수정되었습니다.");
            redirectAttributes.addAttribute("instCode", managedInstCode);
            if (seminarId != null) {
                redirectAttributes.addAttribute("seminarId", seminarId);
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            if (StringUtils.hasText(managedInstCode)) {
                redirectAttributes.addAttribute("instCode", managedInstCode);
            }
            if (seminarId != null) {
                redirectAttributes.addAttribute("seminarId", seminarId);
            }
        }
        return "redirect:/seminar-room";
    }

    @PostMapping("/seminar-room/seminar-managers")
    public String saveSeminarManagers(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam("seminarId") Long seminarId,
            @RequestParam(name = "managerUsernames", required = false) List<String> managerUsernames,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!(user.isPlatformAdmin() || user.isInstitutionAdmin())) {
            return "redirect:/seminar-room";
        }
        String managedInstCode = resolveServiceInstCode(user, instCode);
        try {
            seminarRoomService.saveSeminarManagers(managedInstCode, seminarId, managerUsernames);
            redirectAttributes.addAttribute("message", "세미나 관리자 설정이 저장되었습니다.");
            redirectAttributes.addAttribute("instCode", managedInstCode);
            redirectAttributes.addAttribute("seminarId", seminarId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            if (StringUtils.hasText(managedInstCode)) {
                redirectAttributes.addAttribute("instCode", managedInstCode);
            }
            redirectAttributes.addAttribute("seminarId", seminarId);
        }
        return "redirect:/seminar-room";
    }

    @PostMapping("/seminar-room/reservations")
    public String createSeminarReservation(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam("seminarId") Long seminarId,
            @RequestParam("reservationDate") String reservationDate,
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime,
            @RequestParam("attendeeCount") Integer attendeeCount,
            @RequestParam(name = "usedItems", required = false) String usedItems,
            @RequestParam(name = "neededItems", required = false) String neededItems,
            @RequestParam(name = "purpose", required = false) String purpose,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isServiceAccessible(user, SERVICE_CODE_SEMINAR_ROOM)) {
            return "redirect:/services";
        }
        String selectedInstCode = resolveServiceInstCode(user, instCode);
        try {
            seminarRoomService.createReservation(
                    selectedInstCode,
                    seminarId,
                    user.getUsername(),
                    user.getDisplayName(),
                    LocalDate.parse(reservationDate),
                    LocalTime.parse(startTime),
                    LocalTime.parse(endTime),
                    attendeeCount,
                    usedItems,
                    neededItems,
                    purpose);
            redirectAttributes.addAttribute("message", "세미나실 예약 신청이 등록되었습니다.");
            redirectAttributes.addAttribute("instCode", selectedInstCode);
        } catch (DateTimeParseException e) {
            redirectAttributes.addAttribute("error", "날짜 또는 시간 형식을 확인해 주세요.");
            redirectAttributes.addAttribute("instCode", selectedInstCode);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("instCode", selectedInstCode);
        }
        return "redirect:/seminar-room";
    }

    @PostMapping("/seminar-room/reservations/decision")
    public String decideSeminarReservation(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam("reservationId") Long reservationId,
            @RequestParam("decision") String decision,
            @RequestParam(name = "managerNote", required = false) String managerNote,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isServiceAccessible(user, SERVICE_CODE_SEMINAR_ROOM)) {
            return "redirect:/services";
        }
        String selectedInstCode = resolveServiceInstCode(user, instCode);
        try {
            seminarRoomService.decideReservation(
                    selectedInstCode,
                    reservationId,
                    user.getUsername(),
                    decision,
                    managerNote);
            redirectAttributes.addAttribute("message", "예약 상태가 저장되었습니다.");
            redirectAttributes.addAttribute("instCode", selectedInstCode);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("instCode", selectedInstCode);
        }
        return "redirect:/seminar-room";
    }

    @PostMapping("/seminar-room/notifications/read-all")
    public String markSeminarNotificationsRead(
            @RequestParam(name = "instCode", required = false) String instCode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isServiceAccessible(user, SERVICE_CODE_SEMINAR_ROOM)) {
            return "redirect:/services";
        }
        String selectedInstCode = resolveServiceInstCode(user, instCode);
        if (!seminarRoomService.isSeminarManager(selectedInstCode, user.getUsername())) {
            redirectAttributes.addAttribute("error", "알림을 처리할 권한이 없습니다.");
            redirectAttributes.addAttribute("instCode", selectedInstCode);
            return "redirect:/seminar-room";
        }
        seminarRoomService.markNotificationsRead(selectedInstCode, user.getUsername());
        redirectAttributes.addAttribute("message", "알림이 모두 읽음 처리되었습니다.");
        redirectAttributes.addAttribute("instCode", selectedInstCode);
        return "redirect:/seminar-room";
    }

    @GetMapping("/admin")
    public String adminPage(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam(name = "viewerUsername", required = false) String viewerUsername,
            @RequestParam(name = "userAccessUsername", required = false) String userAccessUsername,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "error", required = false) String error,
            Model model,
            HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isAdminUser(user)) {
            return "redirect:/services";
        }
        String selectedInstCode = resolveSelectedInstCode(user, instCode);
        List<PlatformInstitution> institutions = resolveAdminInstitutions(user, selectedInstCode);
        List<String> institutionEnabledServiceCodes = storeService.listEnabledServiceCodes(selectedInstCode);
        List<PlatformService> institutionEnabledServices = storeService.listAllServices().stream()
                .filter(service -> institutionEnabledServiceCodes.stream()
                        .anyMatch(enabledCode -> enabledCode.equalsIgnoreCase(service.getServiceCode())))
                .toList();
        List<PlatformUser> institutionUsers = storeService.listInstitutionUsers(selectedInstCode);
        String selectedUserAccessUsername = resolveSelectedUserAccessUsername(userAccessUsername, institutionUsers);
        List<String> userEnabledServiceCodes = StringUtils.hasText(selectedUserAccessUsername)
                ? storeService.listEnabledServiceCodesForUser(selectedInstCode, selectedUserAccessUsername)
                : List.of();

        model.addAttribute("user", user);
        model.addAttribute("canManagePlatform", user.isPlatformAdmin());
        model.addAttribute("canManageInstitutionUsers", user.isPlatformAdmin() || user.isInstitutionAdmin());
        model.addAttribute("institutions", institutions);
        model.addAttribute("services", storeService.listAllServices());
        model.addAttribute("runtimeEnvCode", storeService.getRuntimeEnvCode());
        model.addAttribute("selectedInstCode", selectedInstCode);
        model.addAttribute("enabledServiceCodes", institutionEnabledServiceCodes);
        model.addAttribute("enabledIntegrationCodes", storeService.listEnabledIntegrationCodes(selectedInstCode));
        model.addAttribute("roomBoardCounselPairEnabled", storeService.isRoomBoardCounselPairEnabled(selectedInstCode));
        model.addAttribute("institutionAdminUsers", user.isPlatformAdmin() ? storeService.listInstitutionAdminUsers() : List.of());
        model.addAttribute("institutionUsers", institutionUsers);
        model.addAttribute("institutionEnabledServices", institutionEnabledServices);
        model.addAttribute("selectedUserAccessUsername", selectedUserAccessUsername);
        model.addAttribute("userEnabledServiceCodes", userEnabledServiceCodes);
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        if (user.isPlatformAdmin()) {
            populateViewerAdminModel(model, selectedInstCode, viewerUsername);
        } else {
            model.addAttribute("viewerAccounts", List.of());
            model.addAttribute("viewerScopeInstitutions", List.of());
            model.addAttribute("viewerEditMode", false);
            model.addAttribute("viewerFormUsername", "");
            model.addAttribute("viewerFormDisplayName", "");
            model.addAttribute("viewerFormUseYn", "Y");
            model.addAttribute("viewerSelectedInstCodes", List.of());
            model.addAttribute("viewerSelectedInstCode", selectedInstCode);
        }
        return "admin";
    }

    @GetMapping("/admin/async-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> adminAsyncData(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam(name = "userAccessUsername", required = false) String userAccessUsername,
            HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요합니다."));
        }
        if (!isAdminUser(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "관리자 권한이 필요합니다."));
        }

        String selectedInstCode = resolveSelectedInstCode(user, instCode);
        List<PlatformService> services = storeService.listAllServices();
        List<String> enabledServiceCodes = storeService.listEnabledServiceCodes(selectedInstCode);
        List<String> enabledIntegrationCodes = storeService.listEnabledIntegrationCodes(selectedInstCode);
        boolean roomBoardCounselPairEnabled = storeService.isRoomBoardCounselPairEnabled(selectedInstCode);
        List<PlatformUser> institutionUsers = storeService.listInstitutionUsers(selectedInstCode);
        List<PlatformService> institutionEnabledServices = services.stream()
                .filter(service -> enabledServiceCodes.stream()
                        .anyMatch(enabledCode -> enabledCode.equalsIgnoreCase(service.getServiceCode())))
                .toList();
        String selectedUserAccess = resolveSelectedUserAccessUsername(userAccessUsername, institutionUsers);
        List<String> userEnabledServiceCodes = StringUtils.hasText(selectedUserAccess)
                ? storeService.listEnabledServiceCodesForUser(selectedInstCode, selectedUserAccess)
                : List.of();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selectedInstCode", selectedInstCode);
        payload.put("enabledServiceCodes", enabledServiceCodes);
        payload.put("enabledIntegrationCodes", enabledIntegrationCodes);
        payload.put("roomBoardCounselPairEnabled", roomBoardCounselPairEnabled);
        payload.put("services", services.stream().map(service -> {
            Map<String, Object> serviceMap = new LinkedHashMap<>();
            serviceMap.put("serviceCode", service.getServiceCode());
            serviceMap.put("serviceName", service.getServiceName());
            return serviceMap;
        }).toList());
        payload.put("institutionUsers", institutionUsers.stream().map(institutionUser -> {
            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("instCode", institutionUser.getInstCode());
            userMap.put("username", institutionUser.getUsername());
            userMap.put("displayName", institutionUser.getDisplayName());
            userMap.put("roleCode", institutionUser.getRoleCode());
            userMap.put("useYn", institutionUser.getUseYn());
            return userMap;
        }).toList());
        payload.put("selectedUserAccessUsername", selectedUserAccess);
        payload.put("institutionEnabledServices", institutionEnabledServices.stream().map(service -> {
            Map<String, Object> serviceMap = new LinkedHashMap<>();
            serviceMap.put("serviceCode", service.getServiceCode());
            serviceMap.put("serviceName", service.getServiceName());
            return serviceMap;
        }).toList());
        payload.put("userEnabledServiceCodes", userEnabledServiceCodes);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/admin/room-board-viewer/accounts")
    public String saveRoomBoardViewerAccount(
            @RequestParam("username") String username,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam("displayName") String displayName,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            @RequestParam(name = "instCodes", required = false) List<String> instCodes,
            @RequestParam(name = "instCode", required = false) String selectedInstCode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isPlatformAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveRoomBoardViewerAccount(username, password, displayName, useYn, instCodes);
            redirectAttributes.addAttribute("message", "병실현황판 전용 계정이 저장되었습니다.");
            redirectAttributes.addAttribute("viewerUsername", StringUtils.hasText(username) ? username.trim() : "");
            if (StringUtils.hasText(selectedInstCode)) {
                redirectAttributes.addAttribute("instCode", selectedInstCode.trim());
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            if (StringUtils.hasText(selectedInstCode)) {
                redirectAttributes.addAttribute("instCode", selectedInstCode.trim());
            }
            if (StringUtils.hasText(username)) {
                redirectAttributes.addAttribute("viewerUsername", username.trim());
            }
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/institutions")
    public String saveInstitution(
            @RequestParam("instCode") String instCode,
            @RequestParam("instName") String instName,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isPlatformAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveInstitution(instCode, instName, useYn);
            redirectAttributes.addAttribute("message", "기관이 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/users")
    public String saveInstitutionAdminUser(
            @RequestParam("instCode") String instCode,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("displayName") String displayName,
            @RequestParam(name = "roleCode", defaultValue = "USER") String roleCode,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser sessionUser = sessionUser(session);
        if (!isAdminUser(sessionUser)) {
            return "redirect:/login";
        }
        String managedInstCode = resolveManagedInstCode(sessionUser, instCode);
        String managedRoleCode = resolveManagedRoleCode(sessionUser, roleCode);
        if (!StringUtils.hasText(managedInstCode)) {
            redirectAttributes.addAttribute("error", "관리 대상 기관을 찾을 수 없습니다.");
            return "redirect:/admin";
        }
        try {
            storeService.saveUser(managedInstCode, username, password, displayName, managedRoleCode, useYn);
            String savedMessage = "INSTITUTION_ADMIN".equalsIgnoreCase(managedRoleCode)
                    ? "기관 관리자 계정이 저장되었습니다."
                    : "기관 사용자 계정이 저장되었습니다.";
            redirectAttributes.addAttribute("message", savedMessage);
            redirectAttributes.addAttribute("instCode", managedInstCode);
            redirectAttributes.addAttribute("userAccessUsername", username);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            if (StringUtils.hasText(managedInstCode)) {
                redirectAttributes.addAttribute("instCode", managedInstCode);
            }
            if (StringUtils.hasText(username)) {
                redirectAttributes.addAttribute("userAccessUsername", username.trim());
            }
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/user-access")
    public String saveUserAccess(
            @RequestParam("instCode") String instCode,
            @RequestParam("username") String username,
            @RequestParam(name = "enabledServiceCodes", required = false) List<String> enabledServiceCodes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser sessionUser = sessionUser(session);
        if (!isAdminUser(sessionUser)) {
            return "redirect:/login";
        }
        String managedInstCode = resolveManagedInstCode(sessionUser, instCode);
        if (!StringUtils.hasText(managedInstCode)) {
            redirectAttributes.addAttribute("error", "관리 대상 기관을 찾을 수 없습니다.");
            return "redirect:/admin";
        }
        try {
            storeService.saveUserServiceAccess(managedInstCode, username, enabledServiceCodes);
            redirectAttributes.addAttribute("message", "사용자 기능 권한이 저장되었습니다.");
            redirectAttributes.addAttribute("instCode", managedInstCode);
            redirectAttributes.addAttribute("userAccessUsername", username);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            if (StringUtils.hasText(managedInstCode)) {
                redirectAttributes.addAttribute("instCode", managedInstCode);
            }
            if (StringUtils.hasText(username)) {
                redirectAttributes.addAttribute("userAccessUsername", username.trim());
            }
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/services")
    public String saveService(
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(name = "baseUrl", required = false) String baseUrl,
            @RequestParam(name = "baseUrlLocal", required = false) String baseUrlLocal,
            @RequestParam(name = "baseUrlDev", required = false) String baseUrlDev,
            @RequestParam(name = "baseUrlProd", required = false) String baseUrlProd,
            @RequestParam(name = "ssoEntryPath", defaultValue = "/mediplat/sso/entry") String ssoEntryPath,
            @RequestParam("userTarget") String userTarget,
            @RequestParam("adminTarget") String adminTarget,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            @RequestParam(name = "displayOrder", defaultValue = "0") Integer displayOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isPlatformAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveService(
                    serviceCode,
                    serviceName,
                    baseUrl,
                    baseUrlLocal,
                    baseUrlDev,
                    baseUrlProd,
                    ssoEntryPath,
                    userTarget,
                    adminTarget,
                    description,
                    useYn,
                    displayOrder);
            redirectAttributes.addAttribute("message", "서비스가 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/services/status")
    public String updateServiceStatus(
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isPlatformAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.updateServiceStatus(serviceCode, useYn);
            redirectAttributes.addAttribute("message", "서비스 상태가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/access")
    public String saveAccess(
            @RequestParam("instCode") String instCode,
            @RequestParam(name = "enabledServiceCodes", required = false) List<String> enabledServiceCodes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isPlatformAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveInstitutionServiceAccess(instCode, enabledServiceCodes);
            redirectAttributes.addAttribute("message", "기관별 서비스 권한이 저장되었습니다.");
            redirectAttributes.addAttribute("instCode", instCode);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/integrations")
    public String saveIntegrations(
            @RequestParam("instCode") String instCode,
            @RequestParam(name = "enabledIntegrationCodes", required = false) List<String> enabledIntegrationCodes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isPlatformAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveInstitutionIntegrationAccess(instCode, enabledIntegrationCodes);
            redirectAttributes.addAttribute("message", "기관별 연동 설정이 저장되었습니다.");
            redirectAttributes.addAttribute("instCode", instCode);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("instCode", instCode);
        }
        return "redirect:/admin";
    }

    private boolean isServiceAccessible(PlatformSessionUser user, String serviceCode) {
        if (user == null || !StringUtils.hasText(serviceCode)) {
            return false;
        }
        if (user.isPlatformAdmin()) {
            return true;
        }
        return storeService.listEnabledServiceCodesForUser(user).stream()
                .anyMatch(code -> serviceCode.equalsIgnoreCase(code));
    }

    private PlatformSessionUser sessionUser(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER);
        return value instanceof PlatformSessionUser user ? user : null;
    }

    private boolean isAdminUser(PlatformSessionUser user) {
        return user != null && (user.isPlatformAdmin() || user.isInstitutionAdmin());
    }

    private boolean isPlatformAdminSession(HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        return user != null && user.isPlatformAdmin();
    }

    private String resolveManagedInstCode(PlatformSessionUser user, String requestedInstCode) {
        if (user == null) {
            return null;
        }
        if (user.isInstitutionAdmin()) {
            return normalizeInstCode(user.getInstCode());
        }
        return normalizeInstCode(requestedInstCode);
    }

    private String resolveServiceInstCode(PlatformSessionUser user, String requestedInstCode) {
        if (user == null) {
            return null;
        }
        if (!user.isPlatformAdmin()) {
            return normalizeInstCode(user.getInstCode());
        }
        if (!StringUtils.hasText(requestedInstCode)) {
            return normalizeInstCode(user.getInstCode());
        }
        return normalizeInstCode(requestedInstCode);
    }

    private String resolveManagedRoleCode(PlatformSessionUser user, String requestedRoleCode) {
        if (user == null || user.isInstitutionAdmin()) {
            return "USER";
        }
        return "INSTITUTION_ADMIN".equalsIgnoreCase(requestedRoleCode) ? "INSTITUTION_ADMIN" : "USER";
    }

    private String resolveSelectedInstCode(PlatformSessionUser user, String requestedInstCode) {
        if (user == null) {
            return null;
        }
        if (user.isInstitutionAdmin()) {
            return normalizeInstCode(user.getInstCode());
        }
        if (!StringUtils.hasText(requestedInstCode)) {
            return normalizeInstCode(user.getInstCode());
        }
        return normalizeInstCode(requestedInstCode);
    }

    private List<PlatformInstitution> resolveAdminInstitutions(PlatformSessionUser user, String selectedInstCode) {
        List<PlatformInstitution> allInstitutions = storeService.listInstitutions();
        if (user != null && user.isPlatformAdmin()) {
            return allInstitutions;
        }
        return allInstitutions.stream()
                .filter(institution -> institution != null
                        && StringUtils.hasText(institution.getInstCode())
                        && institution.getInstCode().equalsIgnoreCase(selectedInstCode))
                .toList();
    }

    private String resolveSelectedUserAccessUsername(String requestedUsername, List<PlatformUser> institutionUsers) {
        if (StringUtils.hasText(requestedUsername) && institutionUsers != null) {
            String trimmedRequestedUsername = requestedUsername.trim();
            boolean exists = institutionUsers.stream()
                    .filter(user -> user != null && StringUtils.hasText(user.getUsername()))
                    .anyMatch(user -> user.getUsername().equalsIgnoreCase(trimmedRequestedUsername));
            if (exists) {
                return trimmedRequestedUsername;
            }
        }
        if (institutionUsers == null || institutionUsers.isEmpty()) {
            return "";
        }
        return institutionUsers.get(0).getUsername();
    }

    private Long resolveSelectedSeminarId(Long requestedSeminarId, List<SeminarRoom> seminars) {
        if (seminars == null || seminars.isEmpty()) {
            return null;
        }
        if (requestedSeminarId == null) {
            return seminars.get(0).getId();
        }
        boolean exists = seminars.stream()
                .filter(Objects::nonNull)
                .anyMatch(seminar -> Objects.equals(seminar.getId(), requestedSeminarId));
        return exists ? requestedSeminarId : seminars.get(0).getId();
    }

    private boolean isActiveUser(PlatformUser user) {
        return user != null && "Y".equalsIgnoreCase(user.getUseYn());
    }

    private YearMonth resolveCalendarMonth(Integer year, Integer month) {
        YearMonth current = YearMonth.now();
        if (year == null || month == null) {
            return current;
        }
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException e) {
            return current;
        }
    }

    private String normalizeInstCode(String instCode) {
        if (!StringUtils.hasText(instCode)) {
            return null;
        }
        String trimmedCode = instCode.trim();
        if ("core".equalsIgnoreCase(trimmedCode)) {
            return "core";
        }
        return trimmedCode.toUpperCase(Locale.ROOT);
    }

    private void populateViewerAdminModel(Model model, String selectedInstCode, String viewerUsername) {
        List<RoomBoardViewerAccount> viewerAccounts = storeService.listRoomBoardViewerAccounts();
        model.addAttribute("viewerAccounts", viewerAccounts);
        model.addAttribute("viewerScopeInstitutions", storeService.listRoomBoardScopeCandidateInstitutions());

        RoomBoardViewerAccount editAccount = StringUtils.hasText(viewerUsername)
                ? storeService.findRoomBoardViewerAccount(viewerUsername.trim())
                : null;
        boolean editMode = editAccount != null;
        model.addAttribute("viewerEditMode", editMode);
        model.addAttribute("viewerFormUsername", editMode ? editAccount.getUsername() : "");
        model.addAttribute("viewerFormDisplayName", editMode ? editAccount.getDisplayName() : "");
        model.addAttribute("viewerFormUseYn", editMode ? editAccount.getUseYn() : "Y");
        model.addAttribute("viewerSelectedInstCodes", editMode ? editAccount.getScopeInstCodes() : List.of());
        model.addAttribute("viewerSelectedInstCode", selectedInstCode);
    }
}
