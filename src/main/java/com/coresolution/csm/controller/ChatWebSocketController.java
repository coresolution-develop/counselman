package com.coresolution.csm.controller;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;

import com.coresolution.csm.serivce.ChatTokenService;
import com.coresolution.csm.serivce.CsmAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CsmAuthService csmAuthService;
    private final ChatTokenService chatTokenService;

    @MessageMapping("/chat/{roomId}/send")
    public void send(
            @DestinationVariable Long roomId,
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor accessor) {

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return;

        String adminInst = sanitize(stringAttr(attrs, "inst"));
        String kakaoId = extractKakaoId(attrs);

        // Prefer opaque token; fall back to legacy inst code.
        String requestedInst = resolveInst(str(payload.get("token")), str(payload.get("inst")));
        String content = str(payload.get("content"));
        if (requestedInst.isEmpty() || content.isEmpty()) return;

        String safe;
        String senderType;
        String senderName;

        if (!adminInst.isEmpty()) {
            // Admin path — must operate within own institution
            if (!adminInst.equalsIgnoreCase(requestedInst)) {
                log.warn("Reject WS send: admin inst mismatch session={} payload={}", adminInst, requestedInst);
                return;
            }
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM csm.chat_room_" + requestedInst + " WHERE id = ?",
                Integer.class, roomId);
            if (exists == null || exists == 0) return;
            safe = requestedInst;
            senderType = "COUNSELOR";
            senderName = sanitizeDisplay(str(payload.get("senderName")));
        } else if (kakaoId != null && !kakaoId.isBlank()) {
            // Customer path — server resolves sender from room ownership
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT kakao_id, kakao_nickname FROM csm.chat_room_" + requestedInst + " WHERE id = ?",
                    roomId);
                if (rows.isEmpty()) return;
                if (!kakaoId.equals(String.valueOf(rows.get(0).get("kakao_id")))) {
                    log.warn("Reject WS send: kakao_id mismatch inst={} room={}", requestedInst, roomId);
                    return;
                }
                safe = requestedInst;
                senderType = "USER";
                Object nick = rows.get(0).get("kakao_nickname");
                senderName = sanitizeDisplay(nick == null ? "" : nick.toString());
            } catch (Exception e) {
                log.warn("WS send lookup failed inst={} room={}: {}", requestedInst, roomId, e.getMessage());
                return;
            }
        } else {
            return;
        }

        jdbcTemplate.update(
            "INSERT INTO csm.chat_message_" + safe
            + " (room_id, sender_type, sender_name, content) VALUES (?, ?, ?, ?)",
            roomId, senderType, senderName, content);

        String token = chatTokenService.getOrCreateToken(safe);
        if (token == null) {
            log.warn("No chat token for inst={}; cannot broadcast room={}", safe, roomId);
            return;
        }
        messagingTemplate.convertAndSend("/topic/chat/" + token + "/" + roomId,
            Map.of("type", "MESSAGE",
                   "senderType", senderType,
                   "senderName", senderName,
                   "content", content));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9_]", "");
    }

    private String resolveInst(String token, String instRaw) {
        if (token != null && !token.isBlank()) {
            String fromToken = chatTokenService.resolveInst(token.trim());
            if (fromToken != null) return fromToken;
        }
        String sanitized = sanitize(instRaw);
        if (sanitized.isEmpty()) return "";
        try {
            String resolved = csmAuthService.resolveInst(sanitized);
            return resolved == null ? "" : resolved;
        } catch (Exception e) {
            return "";
        }
    }

    private static String sanitizeDisplay(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private static String stringAttr(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        return v == null ? "" : v.toString();
    }

    private static String extractKakaoId(Map<String, Object> attrs) {
        Object ctx = attrs.get("SPRING_SECURITY_CONTEXT");
        if (!(ctx instanceof SecurityContext sec)) return "";
        Authentication auth = sec.getAuthentication();
        if (auth == null) return "";
        Object principal = auth.getPrincipal();
        if (principal instanceof OAuth2User oauth) {
            Object id = oauth.getAttribute("id");
            return id == null ? "" : id.toString();
        }
        return "";
    }
}
