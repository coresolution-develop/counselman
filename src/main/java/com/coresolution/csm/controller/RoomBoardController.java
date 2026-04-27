package com.coresolution.csm.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.csm.vo.AdmissionReservationItem;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.serivce.MediplatSsoService;
import com.coresolution.csm.serivce.ModuleFeatureService;
import com.coresolution.csm.serivce.RoomBoardService;
import com.coresolution.csm.serivce.SmsService;
import com.coresolution.csm.vo.Counsel_phone;
import com.coresolution.csm.vo.Instdata;
import com.coresolution.csm.vo.RoomBoardRoomConfig;
import com.coresolution.csm.vo.Userdata;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class RoomBoardController {
    @Value("${mediplat.platform.base-url:http://localhost:8082}")
    private String mediplatPlatformBaseUrl;

    private final RoomBoardService roomBoardService;
    private final ModuleFeatureService moduleFeatureService;
    private final CsmAuthService cs;
    private final MediplatSsoService mediplatSsoService;
    private final SmsService ss;
    private final ObjectMapper objectMapper;

    @GetMapping({ "room-board", "/room-board" })
    public String roomBoard(
            @RequestParam(value = "snapshotDate", required = false) String snapshotDate,
            @RequestParam(value = "popup", required = false, defaultValue = "0") int popup,
            @RequestParam(value = "patientName", required = false) String patientName,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "expectedDate", required = false) String expectedDate,
            @RequestParam(value = "mpInst", required = false) String mpInst,
            @RequestParam(value = "mpUser", required = false) String mpUser,
            @RequestParam(value = "mpExpires", required = false) Long mpExpires,
            @RequestParam(value = "mpSignature", required = false) String mpSignature,
            Model model,
            HttpSession session) {
        RoomBoardViewerAccess viewerAccess = resolveViewerAccess(mpInst, mpUser, mpExpires, mpSignature);
        String inst;
        Userdata userinfo;
        if (viewerAccess != null) {
            inst = viewerAccess.inst();
            userinfo = viewerAccess.userinfo();
        } else {
            inst = ensureInst(session);
            if (inst == null) {
                return "redirect:/login";
            }
            userinfo = ensureUserInfo(session, inst);
        }
        if (!isRoomBoardEnabled(inst)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "병실현황판 기능이 비활성화되었습니다.");
        }
        populateCommon(model, inst, userinfo);
        var board = roomBoardService.getBoard(inst, snapshotDate);
        model.addAttribute("board", board);
        try {
            model.addAttribute("boardJson", objectMapper.writeValueAsString(board));
        } catch (JsonProcessingException e) {
            model.addAttribute("boardJson", "{}");
        }
        model.addAttribute("canManageRoomBoard", canManageRoomBoard(userinfo));
        model.addAttribute("popupMode", popup == 1);
        model.addAttribute("selectedPatientName", safeString(patientName));
        model.addAttribute("selectedGender", safeString(gender));
        model.addAttribute("selectedExpectedDate", safeString(expectedDate));
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");
        return "design/ward-status";
    }

    @GetMapping({ "room-board/return", "/room-board/return" })
    public String returnToMediplat(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        SecurityContextHolder.clearContext();
        return "redirect:" + resolveMediplatRedirectUrl();
    }

    @GetMapping({ "room-board/discharge-notice", "/room-board/discharge-notice" })
    public String dischargeNotice(
            @RequestParam(value = "date", required = false) String date,
            Model model,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        if (!isRoomBoardEnabled(inst)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "병실현황판 기능이 비활성화되었습니다.");
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        populateCommon(model, inst, userinfo);
        String targetDate = safeString(date);
        if (targetDate.isBlank()) {
            targetDate = java.time.LocalDate.now().toString();
        }
        List<Map<String, Object>> notices = roomBoardService.listDischargeNotices(inst, targetDate);
        model.addAttribute("targetDate", targetDate);
        model.addAttribute("notices", notices);
        model.addAttribute("currentPatients", roomBoardService.listCurrentRoomBoardPatients(inst));
        model.addAttribute("plannedCount", notices.stream().filter(n -> "PLANNED".equals(String.valueOf(n.get("status")))).count());
        model.addAttribute("completedCount", notices.stream().filter(n -> "COMPLETED".equals(String.valueOf(n.get("status")))).count());
        model.addAttribute("afternoonAvailableCount", notices.stream().filter(n -> "오후 입원 가능".equals(String.valueOf(n.get("availabilityLabel")))).count());
        model.addAttribute("canManageRoomBoard", canManageRoomBoard(userinfo));
        return "design/discharge-notice";
    }

    @PostMapping({ "room-board/discharge-notice/save", "/room-board/discharge-notice/save" })
    public String saveDischargeNotice(
            @RequestParam("rbpId") Long rbpId,
            @RequestParam("dischargeDate") String dischargeDate,
            @RequestParam(value = "dischargeTime", defaultValue = "AM") String dischargeTime,
            @RequestParam(value = "status", defaultValue = "PLANNED") String status,
            @RequestParam(value = "note", defaultValue = "") String note,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!isRoomBoardEnabled(inst) || !canManageRoomBoard(userinfo)) {
            return "redirect:/room-board/discharge-notice";
        }
        try {
            roomBoardService.saveDischargeNotice(
                    inst,
                    rbpId,
                    dischargeDate,
                    dischargeTime,
                    status,
                    note,
                    userinfo == null ? "" : userinfo.getUs_col_02());
        } catch (Exception e) {
            log.warn("[room-board] discharge notice save fail inst={}, err={}", inst, e.toString());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        redirectAttributes.addAttribute("date", dischargeDate);
        return "redirect:/room-board/discharge-notice";
    }

    @PostMapping({ "room-board/discharge-notice/status", "/room-board/discharge-notice/status" })
    public String updateDischargeNoticeStatus(
            @RequestParam("noticeId") long noticeId,
            @RequestParam("status") String status,
            @RequestParam("date") String date,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (isRoomBoardEnabled(inst) && canManageRoomBoard(userinfo)) {
            roomBoardService.updateDischargeNoticeStatus(
                    inst,
                    noticeId,
                    status,
                    userinfo == null ? "" : userinfo.getUs_col_02());
        }
        return "redirect:/room-board/discharge-notice?date=" + safeString(date);
    }

    @GetMapping({ "admin/room-board", "/admin/room-board" })
    public String roomBoardAdmin(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        if (!isRoomBoardEnabled(inst)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "병실현황판 기능이 비활성화되었습니다.");
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!canManageRoomBoard(userinfo)) {
            return "redirect:/admin";
        }
        populateCommon(model, inst, userinfo);
        model.addAttribute("roomConfigs", roomBoardService.getRoomConfigs(inst));
        model.addAttribute("snapshotHistory", roomBoardService.getSnapshotHistory(inst, 10));
        model.addAttribute("roomConfigForm", new RoomBoardRoomConfig());
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");
        return "design/room-board-admin";
    }

    @PostMapping({ "admin/room-board/room/save", "/admin/room-board/room/save" })
    public String saveRoomConfig(RoomBoardRoomConfig form, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        if (!isRoomBoardEnabled(inst)) {
            return "redirect:/admin";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!canManageRoomBoard(userinfo)) {
            return "redirect:/admin";
        }
        roomBoardService.saveRoomConfig(inst, form, userinfo == null ? "" : userinfo.getUs_col_02());
        return "redirect:/admin/room-board";
    }

    @PostMapping({ "admin/room-board/room/delete", "/admin/room-board/room/delete" })
    public String deleteRoomConfig(@RequestParam("id") long id, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        if (!isRoomBoardEnabled(inst)) {
            return "redirect:/admin";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!canManageRoomBoard(userinfo)) {
            return "redirect:/admin";
        }
        roomBoardService.deleteRoomConfig(inst, id);
        return "redirect:/admin/room-board";
    }

    @PostMapping({ "admin/room-board/room-config/preview", "/admin/room-board/room-config/preview" })
    public ResponseEntity<?> previewRoomConfigPaste(
            @RequestParam("rawText") String rawText,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        if (!isRoomBoardEnabled(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "병실현황판 기능이 비활성화되었습니다."));
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!canManageRoomBoard(userinfo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한이 없습니다."));
        }
        try {
            return ResponseEntity.ok(roomBoardService.previewRoomConfigPaste(inst, rawText));
        } catch (Exception e) {
            log.warn("[room-board] room config preview fail inst={}, err={}", inst, e.toString());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping({ "admin/room-board/room-config/save", "/admin/room-board/room-config/save" })
    public ResponseEntity<?> saveRoomConfigPaste(
            @RequestParam("rawText") String rawText,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        if (!isRoomBoardEnabled(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "병실현황판 기능이 비활성화되었습니다."));
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!canManageRoomBoard(userinfo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한이 없습니다."));
        }
        try {
            return ResponseEntity.ok(roomBoardService.saveRoomConfigPaste(
                    inst,
                    rawText,
                    userinfo == null ? "" : userinfo.getUs_col_02()));
        } catch (Exception e) {
            log.warn("[room-board] room config save fail inst={}, err={}", inst, e.toString());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping({ "admin/room-board/import/preview", "/admin/room-board/import/preview" })
    public ResponseEntity<?> previewImport(
            @RequestParam("sourceType") String sourceType,
            @RequestParam(value = "snapshotDate", required = false) String snapshotDate,
            @RequestParam(value = "snapshotTime", required = false) String snapshotTime,
            @RequestParam("rawText") String rawText,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        if (!isRoomBoardEnabled(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "병실현황판 기능이 비활성화되었습니다."));
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!canManageRoomBoard(userinfo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한이 없습니다."));
        }
        try {
            return ResponseEntity.ok(roomBoardService.previewImport(inst, sourceType, snapshotDate, snapshotTime, rawText));
        } catch (Exception e) {
            log.warn("[room-board] preview fail inst={}, err={}", inst, e.toString());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping({ "admin/room-board/import/save", "/admin/room-board/import/save" })
    public ResponseEntity<?> saveImport(
            @RequestParam("sourceType") String sourceType,
            @RequestParam(value = "snapshotDate", required = false) String snapshotDate,
            @RequestParam(value = "snapshotTime", required = false) String snapshotTime,
            @RequestParam("rawText") String rawText,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        if (!isRoomBoardEnabled(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "병실현황판 기능이 비활성화되었습니다."));
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!canManageRoomBoard(userinfo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한이 없습니다."));
        }
        try {
            return ResponseEntity.ok(roomBoardService.importSnapshot(
                    inst,
                    sourceType,
                    snapshotDate,
                    snapshotTime,
                    rawText,
                    userinfo == null ? "" : userinfo.getUs_col_02()));
        } catch (Exception e) {
            log.warn("[room-board] import fail inst={}, err={}", inst, e.toString());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private String ensureInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        if (auth.getDetails() instanceof InstDetails details) {
            String inst = details.normalized();
            if (inst != null && !inst.isBlank()) {
                session.setAttribute("inst", inst);
                return inst;
            }
        }
        return null;
    }

    private Userdata ensureUserInfo(HttpSession session, String inst) {
        Userdata userinfo = (Userdata) session.getAttribute("userInfo");
        if (userinfo != null) {
            return userinfo;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Userdata reloaded = cs.loadUserInfo(inst, auth.getName());
        if (reloaded != null) {
            session.setAttribute("userInfo", reloaded);
        }
        return reloaded;
    }

    private boolean canManageRoomBoard(Userdata userinfo) {
        if (userinfo == null) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> "ROOM_BOARD:SNAPSHOT_MANAGE".equals(a.getAuthority())
                            || "ROLE_PLATFORM_ADMIN".equals(a.getAuthority())
                            || "ROLE_INST_ADMIN".equals(a.getAuthority()));
        }
        return false;
    }

    private boolean isRoomBoardEnabled(String inst) {
        return moduleFeatureService.isEnabled(inst, ModuleFeatureService.FEATURE_ROOM_BOARD);
    }

    private RoomBoardViewerAccess resolveViewerAccess(
            String mpInst,
            String mpUser,
            Long mpExpires,
            String mpSignature) {
        boolean hasAnyTokenParam = StringUtils.hasText(mpInst)
                || StringUtils.hasText(mpUser)
                || mpExpires != null
                || StringUtils.hasText(mpSignature);
        if (!hasAnyTokenParam) {
            return null;
        }
        if (!StringUtils.hasText(mpInst)
                || !StringUtils.hasText(mpUser)
                || mpExpires == null
                || !StringUtils.hasText(mpSignature)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "병실현황판 접근 토큰이 올바르지 않습니다.");
        }
        try {
            mediplatSsoService.validateRoomBoardViewer(mpInst, mpUser, mpExpires, mpSignature);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }

        String inst = cs.resolveInst(mpInst);
        if (!StringUtils.hasText(inst)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "유효하지 않은 기관코드입니다.");
        }
        if (!cs.isInstitutionAvailable(inst)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "현재 기관은 CounselMan 사용이 중지되었습니다.");
        }
        if (!cs.isRoomBoardCounselLinkEnabled(inst)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "현재 기관은 병실현황판-입원상담 연동이 비활성화되었습니다.");
        }
        String viewerUserId = mpUser.trim();
        Userdata userinfo = cs.loadUserInfo(inst, viewerUserId);
        if (!cs.isUserAvailable(userinfo)) {
            userinfo = buildVirtualViewerUser(inst, viewerUserId);
        }
        return new RoomBoardViewerAccess(inst, userinfo);
    }

    private Userdata buildVirtualViewerUser(String inst, String userId) {
        Userdata virtualUser = new Userdata();
        String normalizedUserId = StringUtils.hasText(userId) ? userId.trim() : "";
        Instdata institution = cs.coreInstFindByCode(inst);
        String instName = institution != null && StringUtils.hasText(institution.getId_col_02())
                ? institution.getId_col_02().trim()
                : inst;
        virtualUser.setUs_col_02(normalizedUserId);
        virtualUser.setUs_col_04(inst);
        virtualUser.setUs_col_05(instName);
        virtualUser.setUs_col_07("y");
        virtualUser.setUs_col_08(3);
        virtualUser.setUs_col_09(1);
        virtualUser.setUs_col_12(normalizedUserId);
        return virtualUser;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  입원 예약 관리 페이지
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping({ "counsel/admission-reservation", "/counsel/admission-reservation" })
    public String admissionReservationPage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        List<AdmissionReservationItem> items = roomBoardService.listAdmissionReservations(inst);
        List<Map<String, String>> rooms = roomBoardService.listAvailableRooms(inst);

        model.addAttribute("info", userinfo);
        model.addAttribute("items", items);
        model.addAttribute("rooms", rooms);
        return "design/admission-reservation";
    }

    private void populateCommon(Model model, String inst, Userdata userinfo) {
        Userdata ud = new Userdata();
        ud.setUs_col_04(inst);
        model.addAttribute("user", cs.userSelect(ud));
        model.addAttribute("info", userinfo);

        Counsel_phone cp = new Counsel_phone();
        cp.setInst(inst);
        model.addAttribute("ph", cs.selectPhone(cp));

        Map<String, Object> categoryDataMap = cs.getCategoryData(inst);
        model.addAttribute("categoryData", categoryDataMap.getOrDefault("categoryData", Collections.emptyList()));
        model.addAttribute("fieldTypeMapping", categoryDataMap.getOrDefault("fieldTypeMapping", Collections.emptyMap()));
        model.addAttribute("fieldOptionsMapping",
                categoryDataMap.getOrDefault("fieldOptionsMapping", Collections.emptyMap()));
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveMediplatRedirectUrl() {
        String normalized = StringUtils.hasText(mediplatPlatformBaseUrl)
                ? mediplatPlatformBaseUrl.trim()
                : "http://localhost:8082";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "http://localhost:8082" : normalized;
    }

    private record RoomBoardViewerAccess(
            String inst,
            Userdata userinfo) {
    }
}
