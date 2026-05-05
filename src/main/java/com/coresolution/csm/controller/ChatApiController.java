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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;

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

    @PostMapping("/room")
    public Map<String, Object> createRoom(
            @AuthenticationPrincipal OAuth2User user,
            @RequestParam String inst) {

        String safe = inst.replaceAll("[^A-Za-z0-9_]", "");
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

        // 관리자에게 새 채팅방 알림
        messagingTemplate.convertAndSend("/topic/admin/rooms/" + safe,
            Map.of("type", "NEW_ROOM", "roomId", roomId, "nickname", nickname));

        return Map.of("roomId", roomId, "status", "WAITING");
    }

    @GetMapping("/room/{roomId}/messages")
    public List<Map<String, Object>> messages(
            @PathVariable Long roomId,
            @RequestParam String inst) {

        String safe = inst.replaceAll("[^A-Za-z0-9_]", "");
        return jdbcTemplate.queryForList(
            "SELECT id, sender_type, sender_name, content, sent_at"
            + " FROM csm.chat_message_" + safe
            + " WHERE room_id = ? ORDER BY sent_at ASC", roomId);
    }

    @GetMapping("/rooms")
    public List<Map<String, Object>> rooms(@RequestParam String inst) {
        String safe = inst.replaceAll("[^A-Za-z0-9_]", "");
        return jdbcTemplate.queryForList(
            "SELECT id, kakao_id, kakao_nickname, kakao_thumbnail, counselor_id, status, created_at"
            + " FROM csm.chat_room_" + safe
            + " WHERE status IN ('WAITING','ACTIVE') ORDER BY created_at DESC");
    }

    @PutMapping("/room/{roomId}/join")
    public Map<String, Object> joinRoom(
            @PathVariable Long roomId,
            @RequestParam String inst,
            @RequestParam Long counselorId,
            @RequestParam String counselorName) {

        String safe = inst.replaceAll("[^A-Za-z0-9_]", "");
        jdbcTemplate.update(
            "UPDATE csm.chat_room_" + safe
            + " SET status = 'ACTIVE', counselor_id = ? WHERE id = ?",
            counselorId, roomId);

        // 고객에게 상담사 연결 알림
        messagingTemplate.convertAndSend("/topic/chat/" + roomId,
            Map.of("type", "COUNSELOR_JOINED", "counselorName", counselorName));

        return Map.of("ok", true);
    }

    @PutMapping("/room/{roomId}/close")
    public Map<String, Object> closeRoom(
            @PathVariable Long roomId,
            @RequestParam String inst) {

        String safe = inst.replaceAll("[^A-Za-z0-9_]", "");
        jdbcTemplate.update(
            "UPDATE csm.chat_room_" + safe + " SET status = 'CLOSED' WHERE id = ?", roomId);

        messagingTemplate.convertAndSend("/topic/chat/" + roomId,
            Map.of("type", "ROOM_CLOSED"));

        return Map.of("ok", true);
    }
}
