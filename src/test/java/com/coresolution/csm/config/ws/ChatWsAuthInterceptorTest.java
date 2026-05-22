package com.coresolution.csm.config.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.coresolution.csm.serivce.ChatTokenService;
import com.coresolution.csm.serivce.CsmAuthService;

class ChatWsAuthInterceptorTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private MessageChannel channel;
    @Mock private CsmAuthService csmAuthService;
    @Mock private ChatTokenService chatTokenService;

    private ChatWsAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Default: token resolver returns null (unknown), legacy resolver echoes input.
        when(chatTokenService.resolveInst(anyString())).thenReturn(null);
        when(csmAuthService.resolveInst(anyString())).thenAnswer(inv -> inv.getArgument(0));
        interceptor = new ChatWsAuthInterceptor(jdbcTemplate, csmAuthService, chatTokenService);
    }

    // --- /topic/admin/rooms/{inst} ---

    @Test
    void subscribe_admin_allowed_whenSessionInstMatches() {
        Message<?> msg = subscribeMessage("/topic/admin/rooms/FALH",
                Map.of("inst", "FALH"));

        assertThat(interceptor.preSend(msg, channel)).isNotNull();
    }

    @Test
    void subscribe_admin_rejected_whenSessionInstDiffers() {
        Message<?> msg = subscribeMessage("/topic/admin/rooms/FALH",
                Map.of("inst", "OTHER"));

        assertThat(interceptor.preSend(msg, channel)).isNull();
    }

    @Test
    void subscribe_admin_rejected_whenNoSessionInst() {
        Message<?> msg = subscribeMessage("/topic/admin/rooms/FALH", Map.of());

        assertThat(interceptor.preSend(msg, channel)).isNull();
    }

    // --- /topic/chat/{inst}/{roomId} ---

    @Test
    void subscribe_chat_admin_allowed_whenInstMatches() {
        Message<?> msg = subscribeMessage("/topic/chat/FALH/5",
                Map.of("inst", "FALH"));

        assertThat(interceptor.preSend(msg, channel)).isNotNull();
        // Admin path must not query chat_room table (cheap allow)
        verify(jdbcTemplate, never()).queryForList(startsWith("SELECT kakao_id"), eq(5L));
    }

    @Test
    void subscribe_chat_admin_rejected_whenCrossInst() {
        Message<?> msg = subscribeMessage("/topic/chat/OTHER/5",
                Map.of("inst", "FALH"));

        assertThat(interceptor.preSend(msg, channel)).isNull();
    }

    @Test
    void subscribe_chat_customer_allowed_whenOwnsRoom() {
        when(jdbcTemplate.queryForList(
                "SELECT kakao_id FROM csm.chat_room_FALH WHERE id = ?", 5L))
            .thenReturn(List.of(Map.of("kakao_id", "kakao-123")));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(oauthAuth("kakao-123")));

        Message<?> msg = subscribeMessage("/topic/chat/FALH/5", attrs);

        assertThat(interceptor.preSend(msg, channel)).isNotNull();
    }

    @Test
    void subscribe_chat_customer_rejected_whenNotOwner() {
        when(jdbcTemplate.queryForList(
                "SELECT kakao_id FROM csm.chat_room_FALH WHERE id = ?", 5L))
            .thenReturn(List.of(Map.of("kakao_id", "kakao-999")));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(oauthAuth("kakao-123")));

        Message<?> msg = subscribeMessage("/topic/chat/FALH/5", attrs);

        assertThat(interceptor.preSend(msg, channel)).isNull();
    }

    @Test
    void subscribe_chat_customer_rejected_whenRoomMissing() {
        when(jdbcTemplate.queryForList(
                "SELECT kakao_id FROM csm.chat_room_FALH WHERE id = ?", 99L))
            .thenReturn(List.of());

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(oauthAuth("kakao-123")));

        Message<?> msg = subscribeMessage("/topic/chat/FALH/99", attrs);

        assertThat(interceptor.preSend(msg, channel)).isNull();
    }

    @Test
    void subscribe_chat_rejected_whenAnonymous() {
        Message<?> msg = subscribeMessage("/topic/chat/FALH/5", Map.of());

        assertThat(interceptor.preSend(msg, channel)).isNull();
    }

    @Test
    void subscribe_chat_rejected_whenMalformedDestination() {
        Message<?> bad1 = subscribeMessage("/topic/chat/FALH",
                Map.of("inst", "FALH"));
        Message<?> bad2 = subscribeMessage("/topic/chat/FALH/notanumber",
                Map.of("inst", "FALH"));

        assertThat(interceptor.preSend(bad1, channel)).isNull();
        assertThat(interceptor.preSend(bad2, channel)).isNull();
    }

    @Test
    void subscribe_chat_rejected_whenInstHasSqlInjection() {
        // Sanitization strips non-[A-Za-z0-9_], so injected payload becomes plain text
        // and the per-inst chat_room table won't exist → query throws → reject.
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(oauthAuth("kakao-123")));

        Message<?> msg = subscribeMessage("/topic/chat/FALH;DROP/5", attrs);
        // Sanitizer turns "FALH;DROP" into "FALHDROP"; let's just assert it's rejected
        // (because chat_room_FALHDROP doesn't exist → JdbcTemplate throws → false)
        when(jdbcTemplate.queryForList(
                "SELECT kakao_id FROM csm.chat_room_FALHDROP WHERE id = ?", 5L))
            .thenThrow(new RuntimeException("table not found"));

        assertThat(interceptor.preSend(msg, channel)).isNull();
    }

    // --- non-chat destinations pass through untouched ---

    @Test
    void send_passesThrough() {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SEND);
        acc.setDestination("/app/chat/5/send");
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());

        assertThat(interceptor.preSend(msg, channel)).isSameAs(msg);
    }

    @Test
    void subscribe_unrelatedTopic_passesThrough() {
        Message<?> msg = subscribeMessage("/topic/other/thing", Map.of());

        assertThat(interceptor.preSend(msg, channel)).isNotNull();
    }

    // --- helpers ---

    private static Message<byte[]> subscribeMessage(String destination, Map<String, Object> sessionAttrs) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setDestination(destination);
        acc.setSessionAttributes(new HashMap<>(sessionAttrs));
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    private static UsernamePasswordAuthenticationToken oauthAuth(String kakaoId) {
        OAuth2User user = new DefaultOAuth2User(
                List.of(),
                Map.of("id", kakaoId, "nickname", "tester"),
                "id");
        return new UsernamePasswordAuthenticationToken(user, "", List.of());
    }
}
