package com.coresolution.csm.config.ws;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import com.coresolution.csm.serivce.ChatTokenService;
import com.coresolution.csm.serivce.CsmAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces per-institution access on STOMP SUBSCRIBE/SEND for chat.
 * Topics:
 *   /topic/admin/rooms/{inst}        — admin only, session.inst must equal inst
 *   /topic/chat/{inst}/{roomId}      — admin (session.inst match) OR room owner (kakao_id match)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWsAuthInterceptor implements ChannelInterceptor {

    private static final String CHAT_PREFIX = "/topic/chat/";
    private static final String ADMIN_PREFIX = "/topic/admin/rooms/";

    private final JdbcTemplate jdbcTemplate;
    private final CsmAuthService csmAuthService;
    private final ChatTokenService chatTokenService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand cmd = accessor.getCommand();
        if (!StompCommand.SUBSCRIBE.equals(cmd)) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) attrs = Map.of();

        String adminInst = sanitize(stringAttr(attrs, "inst"));
        String kakaoId = extractKakaoId(attrs);

        if (destination.startsWith(ADMIN_PREFIX)) {
            String segment = sanitize(destination.substring(ADMIN_PREFIX.length()));
            String inst = resolveInstFromSegment(segment);
            if (inst.isEmpty() || !inst.equalsIgnoreCase(adminInst)) {
                log.warn("Reject WS subscribe to admin topic dest={} adminInst={}", destination, adminInst);
                return null;
            }
            return message;
        }

        if (destination.startsWith(CHAT_PREFIX)) {
            String tail = destination.substring(CHAT_PREFIX.length());
            int slash = tail.indexOf('/');
            if (slash <= 0) {
                log.warn("Reject WS subscribe with malformed chat topic dest={}", destination);
                return null;
            }
            String inst = resolveInstFromSegment(tail.substring(0, slash));
            Long roomId;
            try {
                roomId = Long.parseLong(tail.substring(slash + 1));
            } catch (NumberFormatException e) {
                log.warn("Reject WS subscribe with non-numeric roomId dest={}", destination);
                return null;
            }
            if (!canAccessRoom(inst, roomId, adminInst, kakaoId)) {
                log.warn("Reject WS subscribe inst={} room={} adminInst={} kakaoId={}",
                        inst, roomId, adminInst, kakaoId == null ? "" : "(set)");
                return null;
            }
            return message;
        }

        return message;
    }

    /**
     * Path segment may be either an opaque chat token or a legacy inst code.
     * Returns canonical inst, or empty if neither resolves.
     */
    private String resolveInstFromSegment(String segment) {
        if (segment == null || segment.isEmpty()) return "";
        String fromToken = chatTokenService.resolveInst(segment);
        if (fromToken != null) return fromToken;
        try {
            String resolved = csmAuthService.resolveInst(segment);
            return resolved == null ? "" : resolved;
        } catch (Exception e) {
            return "";
        }
    }

    private boolean canAccessRoom(String inst, Long roomId, String adminInst, String kakaoId) {
        if (inst.isEmpty() || roomId == null) return false;
        if (!adminInst.isEmpty()) {
            return adminInst.equalsIgnoreCase(inst);
        }
        if (kakaoId == null || kakaoId.isBlank()) return false;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT kakao_id FROM csm.chat_room_" + inst + " WHERE id = ?", roomId);
            if (rows.isEmpty()) return false;
            return kakaoId.equals(String.valueOf(rows.get(0).get("kakao_id")));
        } catch (Exception e) {
            return false;
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9_]", "");
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
