package com.coresolution.sms.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.sms.model.SessionUser;
import com.coresolution.sms.model.SmsSendRequest;
import com.coresolution.sms.service.SmsSendService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/sms")
public class SmsSendController {

    private final SmsSendService sendService;

    public SmsSendController(SmsSendService sendService) {
        this.sendService = sendService;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(HttpSession session, @RequestBody SmsSendRequest request) {
        SessionUser user = SmsSession.require(session);
        if (request == null || isBlank(request.getTo()) || isBlank(request.getMessage())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "description", "fail",
                    "message", "수신번호와 내용을 입력해 주세요."));
        }
        SmsSendService.SendResult result = sendService.send(user.getInstCode(), request);
        HttpStatus status = result.success ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(result.response);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
