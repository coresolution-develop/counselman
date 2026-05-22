package com.coresolution.csm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.coresolution.csm.serivce.ChatTokenService;
import com.coresolution.csm.serivce.CsmAuthService;

class ChatWebSocketControllerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private CsmAuthService csmAuthService;
    @Mock private ChatTokenService chatTokenService;

    private ChatWebSocketController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(csmAuthService.resolveInst(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(chatTokenService.resolveInst(anyString())).thenReturn(null);
        // For broadcast topic name: token equals inst for tests (simplifies assertions on topic path).
        when(chatTokenService.getOrCreateToken(anyString())).thenAnswer(inv -> inv.getArgument(0));
        controller = new ChatWebSocketController(jdbcTemplate, messagingTemplate, csmAuthService, chatTokenService);
    }

    @Test
    void send_admin_persistsAndBroadcasts_whenInstMatches() {
        SimpMessageHeaderAccessor acc = adminAccessor("FALH");
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM csm.chat_room_FALH WHERE id = ?"),
                eq(Integer.class), eq(7L)))
            .thenReturn(1);

        controller.send(7L,
                Map.of("inst", "FALH", "senderName", "상담사1", "content", "hi"),
                acc);

        verify(jdbcTemplate).update(
                eq("INSERT INTO csm.chat_message_FALH (room_id, sender_type, sender_name, content) VALUES (?, ?, ?, ?)"),
                eq(7L), eq("COUNSELOR"), eq("상담사1"), eq("hi"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/FALH/7"), body.capture());
        org.assertj.core.api.Assertions.assertThat(body.getValue())
                .containsEntry("senderType", "COUNSELOR")
                .containsEntry("senderName", "상담사1")
                .containsEntry("content", "hi");
    }

    @Test
    void send_admin_rejected_whenPayloadInstMismatch() {
        SimpMessageHeaderAccessor acc = adminAccessor("FALH");

        controller.send(7L,
                Map.of("inst", "OTHER", "senderName", "spoof", "content", "hi"),
                acc);

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void send_admin_rejected_whenRoomNotInInstitution() {
        SimpMessageHeaderAccessor acc = adminAccessor("FALH");
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM csm.chat_room_FALH WHERE id = ?"),
                eq(Integer.class), eq(9L)))
            .thenReturn(0);

        controller.send(9L,
                Map.of("inst", "FALH", "senderName", "상담사1", "content", "hi"),
                acc);

        verify(jdbcTemplate, never()).update(anyString(), anyLong(), anyString(), anyString(), anyString());
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void send_customer_persistsAsUser_withServerResolvedNickname() {
        SimpMessageHeaderAccessor acc = customerAccessor("kakao-1");
        when(jdbcTemplate.queryForList(
                "SELECT kakao_id, kakao_nickname FROM csm.chat_room_FALH WHERE id = ?", 7L))
            .thenReturn(List.of(Map.of(
                    "kakao_id", "kakao-1",
                    "kakao_nickname", "고객A")));

        // Customer tries to spoof senderType=COUNSELOR and senderName=fake — server must override
        controller.send(7L,
                Map.of("inst", "FALH",
                       "senderType", "COUNSELOR",
                       "senderName", "fake-admin",
                       "content", "hi"),
                acc);

        verify(jdbcTemplate).update(
                eq("INSERT INTO csm.chat_message_FALH (room_id, sender_type, sender_name, content) VALUES (?, ?, ?, ?)"),
                eq(7L), eq("USER"), eq("고객A"), eq("hi"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/FALH/7"), body.capture());
        org.assertj.core.api.Assertions.assertThat(body.getValue())
                .containsEntry("senderType", "USER")
                .containsEntry("senderName", "고객A");
    }

    @Test
    void send_customer_rejected_whenNotRoomOwner() {
        SimpMessageHeaderAccessor acc = customerAccessor("kakao-1");
        when(jdbcTemplate.queryForList(
                "SELECT kakao_id, kakao_nickname FROM csm.chat_room_FALH WHERE id = ?", 7L))
            .thenReturn(List.of(Map.of(
                    "kakao_id", "kakao-999",
                    "kakao_nickname", "다른고객")));

        controller.send(7L,
                Map.of("inst", "FALH", "content", "leak attempt"),
                acc);

        verify(jdbcTemplate, never()).update(anyString(), anyLong(), anyString(), anyString(), anyString());
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void send_rejected_whenAnonymousNoSession() {
        SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.create();
        acc.setSessionAttributes(new HashMap<>());

        controller.send(7L,
                Map.of("inst", "FALH", "content", "x"),
                acc);

        verify(jdbcTemplate, never()).update(anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void send_rejected_whenContentBlank() {
        SimpMessageHeaderAccessor acc = adminAccessor("FALH");

        controller.send(7L,
                Map.of("inst", "FALH", "senderName", "상담사", "content", "   "),
                acc);

        verify(jdbcTemplate, never()).update(anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    private static SimpMessageHeaderAccessor adminAccessor(String inst) {
        SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.create();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("inst", inst);
        acc.setSessionAttributes(attrs);
        return acc;
    }

    private static SimpMessageHeaderAccessor customerAccessor(String kakaoId) {
        SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.create();
        Map<String, Object> attrs = new HashMap<>();
        OAuth2User user = new DefaultOAuth2User(
                List.of(),
                Map.of("id", kakaoId, "nickname", "kakao-nick"),
                "id");
        attrs.put("SPRING_SECURITY_CONTEXT",
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken(user, "", List.of())));
        acc.setSessionAttributes(attrs);
        return acc;
    }
}
