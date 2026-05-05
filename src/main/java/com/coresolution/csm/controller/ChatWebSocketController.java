package com.coresolution.csm.controller;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{roomId}/send")
    public void send(
            @DestinationVariable Long roomId,
            @Payload Map<String, Object> payload) {

        String inst = str(payload.get("inst"));
        String safe = inst.replaceAll("[^A-Za-z0-9_]", "");
        String senderType = str(payload.get("senderType")); // USER or COUNSELOR
        String senderName = str(payload.get("senderName"));
        String content = str(payload.get("content"));

        jdbcTemplate.update(
            "INSERT INTO csm.chat_message_" + safe
            + " (room_id, sender_type, sender_name, content) VALUES (?, ?, ?, ?)",
            roomId, senderType, senderName, content);

        messagingTemplate.convertAndSend("/topic/chat/" + roomId,
            Map.of("type", "MESSAGE",
                   "senderType", senderType,
                   "senderName", senderName,
                   "content", content));
    }

    private String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
