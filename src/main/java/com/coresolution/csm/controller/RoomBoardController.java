package com.coresolution.csm.controller;

import java.util.Collections;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.serivce.RoomBoardService;
import com.coresolution.csm.serivce.SmsService;
import com.coresolution.csm.vo.Counsel_phone;
import com.coresolution.csm.vo.RoomBoardRoomConfig;
import com.coresolution.csm.vo.Userdata;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class RoomBoardController {
    private final RoomBoardService roomBoardService;
    private final CsmAuthService cs;
    private final SmsService ss;

    @GetMapping({ "room-board", "/room-board" })
    public String roomBoard(
            @RequestParam(value = "snapshotDate", required = false) String snapshotDate,
            @RequestParam(value = "popup", required = false, defaultValue = "0") int popup,
            @RequestParam(value = "patientName", required = false) String patientName,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "expectedDate", required = false) String expectedDate,
            Model model,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        populateCommon(model, inst, userinfo);
        model.addAttribute("board", roomBoardService.getBoard(inst, snapshotDate));
        model.addAttribute("canManageRoomBoard", canManageRoomBoard(userinfo));
        model.addAttribute("popupMode", popup == 1);
        model.addAttribute("selectedPatientName", safeString(patientName));
        model.addAttribute("selectedGender", safeString(gender));
        model.addAttribute("selectedExpectedDate", safeString(expectedDate));
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");
        return "csm/counsel/roomBoard";
    }

    @GetMapping({ "admin/room-board", "/admin/room-board" })
    public String roomBoardAdmin(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
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
        return "csm/admin/roomBoardAdmin";
    }

    @PostMapping({ "admin/room-board/room/save", "/admin/room-board/room/save" })
    public String saveRoomConfig(RoomBoardRoomConfig form, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
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
        Userdata userinfo = ensureUserInfo(session, inst);
        if (!canManageRoomBoard(userinfo)) {
            return "redirect:/admin";
        }
        roomBoardService.deleteRoomConfig(inst, id);
        return "redirect:/admin/room-board";
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
        if ("coreadmin".equalsIgnoreCase(safeString(userinfo.getUs_col_02()))) {
            return true;
        }
        Integer grade = userinfo.getUs_col_08();
        return grade != null && (grade == 1 || grade == 2);
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
}
