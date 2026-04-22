package com.coresolution.csm.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.RoomBoardService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 입원 예약 관리 AJAX API
 * - 경로 충돌 없이 독립 동작하도록 전용 RestController 분리
 */
@RestController
@RequestMapping("/api/admission-reservation")
@RequiredArgsConstructor
public class AdmissionReservationApiController {

    private static final Logger log = LoggerFactory.getLogger(AdmissionReservationApiController.class);

    private final RoomBoardService roomBoardService;

    // ── 입원예정일 & 병실 저장 ──────────────────────────────────────────
    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> update(
            @RequestParam("csIdx") long csIdx,
            @RequestParam(value = "plannedDate", required = false, defaultValue = "") String plannedDate,
            @RequestParam(value = "roomName",    required = false, defaultValue = "") String roomName,
            HttpSession session) {

        String inst = resolveInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        try {
            roomBoardService.updateAdmissionDetails(inst, csIdx, plannedDate, roomName);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.warn("[ar-api] update fail inst={} csIdx={}: {}", inst, csIdx, e.toString());
            return ResponseEntity.ok(Map.of("success", false, "message", "저장 중 오류가 발생했습니다."));
        }
    }

    // ── 입원완료 처리 ───────────────────────────────────────────────────
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirm(
            @RequestParam("csIdx") long csIdx,
            HttpSession session) {

        String inst = resolveInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        try {
            roomBoardService.confirmAdmission(inst, csIdx);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.warn("[ar-api] confirm fail inst={} csIdx={}: {}", inst, csIdx, e.toString());
            return ResponseEntity.ok(Map.of("success", false, "message", "처리 중 오류가 발생했습니다."));
        }
    }

    // ── 예약 취소 ───────────────────────────────────────────────────────
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @RequestParam("csIdx") long csIdx,
            @RequestParam(value = "revertStatus", defaultValue = "상담중") String revertStatus,
            HttpSession session) {

        String inst = resolveInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        try {
            roomBoardService.cancelAdmissionReservation(inst, csIdx, revertStatus);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.warn("[ar-api] cancel fail inst={} csIdx={}: {}", inst, csIdx, e.toString());
            return ResponseEntity.ok(Map.of("success", false, "message", "처리 중 오류가 발생했습니다."));
        }
    }

    // ── 세션에서 inst 조회 (RoomBoardController 방식 동일) ──────────────
    private String resolveInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object details = auth.getDetails();
        if (details instanceof InstDetails id) {
            String inst = id.normalized();
            if (inst != null && !inst.isBlank()) {
                session.setAttribute("inst", inst);
                return inst;
            }
        }
        return null;
    }
}
