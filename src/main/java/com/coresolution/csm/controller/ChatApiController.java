package com.coresolution.csm.controller;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.csm.serivce.ChatTokenService;
import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.vo.CounselReservation;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CsmAuthService csmAuthService;
    private final ChatTokenService chatTokenService;

    @GetMapping("/faqs")
    public List<Map<String, Object>> faqs(
            @RequestParam(value = "t", required = false) String token,
            @RequestParam(value = "inst", required = false) String inst) {
        String safe = resolveInst(token, inst);
        if (safe.isEmpty()) return List.of();
        try {
            return jdbcTemplate.queryForList(
                "SELECT id, category, question, answer FROM csm.faq_" + safe
                + " WHERE use_yn = 'Y' ORDER BY sort_order ASC, id ASC");
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) return Map.of("loggedIn", false);
        return Map.of(
            "loggedIn", true,
            "id", user.getAttribute("id"),
            "nickname", user.getAttribute("nickname"),
            "thumbnail", user.getAttribute("thumbnail")
        );
    }

    @GetMapping("/room/my")
    public Map<String, Object> myRoom(
            @AuthenticationPrincipal OAuth2User user,
            @RequestParam(value = "t", required = false) String token,
            @RequestParam(value = "inst", required = false) String inst) {

        if (user == null) return Map.of();
        String safe = resolveInst(token, inst);
        if (safe.isEmpty()) return Map.of();
        String kakaoId = user.getAttribute("id");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status FROM csm.chat_room_" + safe
                + " WHERE kakao_id = ? AND status IN ('WAITING','ACTIVE') ORDER BY created_at DESC LIMIT 1",
                kakaoId);
            return rows.isEmpty() ? Map.of() : rows.get(0);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @PostMapping("/room")
    public Map<String, Object> createRoom(
            @AuthenticationPrincipal OAuth2User user,
            @RequestParam(value = "t", required = false) String token,
            @RequestParam(value = "inst", required = false) String inst) {

        String safe = resolveInst(token, inst);
        if (safe.isEmpty()) return Map.of("error", "unknown inst");
        String kakaoId = user.getAttribute("id");
        String nickname = user.getAttribute("nickname");
        String thumbnail = user.getAttribute("thumbnail");

        // 기존 WAITING 채팅방이 있으면 재사용
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT id FROM csm.chat_room_" + safe
            + " WHERE kakao_id = ? AND status = 'WAITING' LIMIT 1", kakaoId);

        if (!existing.isEmpty()) {
            Long roomId = ((Number) existing.get(0).get("id")).longValue();
            return Map.of("roomId", roomId, "status", "WAITING");
        }

        jdbcTemplate.update(
            "INSERT INTO csm.chat_room_" + safe
            + " (kakao_id, kakao_nickname, kakao_thumbnail, status) VALUES (?, ?, ?, 'WAITING')",
            kakaoId, nickname, thumbnail);

        Long roomId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 관리자에게 새 채팅방 알림 (토큰 토픽)
        String adminToken = chatTokenService.getOrCreateToken(safe);
        if (adminToken != null) {
            messagingTemplate.convertAndSend("/topic/admin/rooms/" + adminToken,
                Map.of("type", "NEW_ROOM", "roomId", roomId, "nickname", nickname));
        }

        return Map.of("roomId", roomId, "status", "WAITING");
    }

    @GetMapping("/room/{roomId}/status")
    public Map<String, Object> roomStatus(
            @PathVariable Long roomId,
            @RequestParam(value = "t", required = false) String token,
            @RequestParam(value = "inst", required = false) String inst,
            @AuthenticationPrincipal OAuth2User user,
            HttpSession session) {

        String safe = resolveAccessibleInst(roomId, token, inst, user, session);
        if (safe.isEmpty()) return Map.of("status", "UNKNOWN");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, counselor_id FROM csm.chat_room_" + safe + " WHERE id = ?", roomId);
            return rows.isEmpty() ? Map.of("status", "UNKNOWN") : rows.get(0);
        } catch (Exception e) {
            return Map.of("status", "UNKNOWN");
        }
    }

    @GetMapping("/room/{roomId}/messages")
    public List<Map<String, Object>> messages(
            @PathVariable Long roomId,
            @RequestParam(value = "t", required = false) String token,
            @RequestParam(value = "inst", required = false) String inst,
            @AuthenticationPrincipal OAuth2User user,
            HttpSession session) {

        String safe = resolveAccessibleInst(roomId, token, inst, user, session);
        if (safe.isEmpty()) return List.of();
        return jdbcTemplate.queryForList(
            "SELECT id, sender_type, sender_name, content, sent_at"
            + " FROM csm.chat_message_" + safe
            + " WHERE room_id = ? ORDER BY sent_at ASC", roomId);
    }

    @GetMapping("/rooms")
    public List<Map<String, Object>> rooms(HttpSession session) {
        String safe = sessionInstOrEmpty(session);
        if (safe.isEmpty()) return List.of();
        return jdbcTemplate.queryForList(
            "SELECT id, kakao_id, kakao_nickname, kakao_thumbnail, counselor_id, status, created_at"
            + " FROM csm.chat_room_" + safe
            + " ORDER BY FIELD(status,'WAITING','ACTIVE','CLOSED'), created_at DESC");
    }

    @GetMapping("/waiting")
    public List<Map<String, Object>> waiting(HttpSession session) {
        String safe = sessionInstOrEmpty(session);
        if (safe.isEmpty()) return List.of();
        try {
            return jdbcTemplate.queryForList(
                "SELECT id, kakao_nickname, created_at FROM csm.chat_room_" + safe
                + " WHERE status = 'WAITING' ORDER BY created_at DESC");
        } catch (Exception e) {
            return List.of();
        }
    }

    @PutMapping("/room/{roomId}/join")
    public Map<String, Object> joinRoom(
            @PathVariable Long roomId,
            @RequestParam Long counselorId,
            @RequestParam String counselorName,
            HttpSession session) {

        String safe = sessionInstOrEmpty(session);
        if (safe.isEmpty()) return Map.of("ok", false, "error", "no inst");
        jdbcTemplate.update(
            "UPDATE csm.chat_room_" + safe
            + " SET status = 'ACTIVE', counselor_id = ? WHERE id = ?",
            counselorId, roomId);

        // 고객에게 상담사 연결 알림 (토큰 토픽)
        String chatTopic = chatTopic(safe, roomId);
        if (chatTopic != null) {
            messagingTemplate.convertAndSend(chatTopic,
                Map.of("type", "COUNSELOR_JOINED", "counselorName", counselorName));
        }

        return Map.of("ok", true);
    }

    @PutMapping("/room/{roomId}/close")
    public Map<String, Object> closeRoom(
            @PathVariable Long roomId,
            HttpSession session) {

        String safe = sessionInstOrEmpty(session);
        if (safe.isEmpty()) return Map.of("ok", false, "error", "no inst");
        jdbcTemplate.update(
            "UPDATE csm.chat_room_" + safe + " SET status = 'CLOSED' WHERE id = ?", roomId);

        String chatTopic = chatTopic(safe, roomId);
        if (chatTopic != null) {
            messagingTemplate.convertAndSend(chatTopic, Map.of("type", "ROOM_CLOSED"));
        }

        return Map.of("ok", true);
    }

    private String chatTopic(String canonicalInst, Long roomId) {
        String tk = chatTokenService.getOrCreateToken(canonicalInst);
        return tk == null ? null : "/topic/chat/" + tk + "/" + roomId;
    }

    private static String sessionInstOrEmpty(HttpSession session) {
        if (session == null) return "";
        Object v = session.getAttribute("inst");
        if (v == null) return "";
        return v.toString().replaceAll("[^A-Za-z0-9_]", "");
    }

    // Returns sanitized inst if caller may access roomId, else empty.
    // Admin (session.inst set): inst must equal session.inst (case-insensitive after canonicalization).
    // Customer (OAuth2User): chat_room.kakao_id must equal user's kakao id.
    private String resolveAccessibleInst(Long roomId, String token, String inst, OAuth2User user, HttpSession session) {
        String safe = resolveInst(token, inst);
        if (safe.isEmpty()) return "";

        String adminInst = sessionInstOrEmpty(session);
        if (!adminInst.isEmpty()) {
            return adminInst.equalsIgnoreCase(safe) ? safe : "";
        }

        if (user == null) return "";
        String kakaoId = user.getAttribute("id");
        if (kakaoId == null || kakaoId.isBlank()) return "";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT kakao_id FROM csm.chat_room_" + safe + " WHERE id = ?", roomId);
            if (rows.isEmpty()) return "";
            return kakaoId.equals(String.valueOf(rows.get(0).get("kakao_id"))) ? safe : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Resolves the canonical institution code from either an opaque token (preferred)
     * or a legacy inst code. Returns "" if both are missing/unknown.
     */
    private String resolveInst(String token, String inst) {
        if (token != null && !token.isBlank()) {
            String fromToken = chatTokenService.resolveInst(token.trim());
            if (fromToken != null) return fromToken;
        }
        if (inst != null && !inst.isBlank()) {
            String sanitized = inst.trim().replaceAll("[^A-Za-z0-9_]", "");
            if (sanitized.isEmpty()) return "";
            try {
                String resolved = csmAuthService.resolveInst(sanitized);
                if (resolved != null) return resolved;
            } catch (Exception ignored) {}
        }
        return "";
    }

    @PostMapping("/counsel")
    public Map<String, Object> submitCounsel(
            @RequestParam(value = "t", required = false) String token,
            @RequestParam(value = "inst", required = false) String inst,
            @RequestParam String name,
            @RequestParam String phone,
            @RequestParam String content) {

        String safe = resolveInst(token, inst);
        if (safe.isEmpty()) return Map.of("ok", false, "error", "unknown inst");
        try {
            CounselReservation reservation = new CounselReservation();
            reservation.setPatient_name(name);
            reservation.setPatient_phone(phone);
            reservation.setCall_summary(content);
            reservation.setCreated_by("챗봇");
            reservation.setStatus("pending");
            csmAuthService.saveCounselReservation(safe, reservation);
            String adminToken = chatTokenService.getOrCreateToken(safe);
            if (adminToken != null) {
                messagingTemplate.convertAndSend("/topic/admin/rooms/" + adminToken,
                    Map.of("type", "NEW_COUNSEL", "name", name));
            }
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }
}
