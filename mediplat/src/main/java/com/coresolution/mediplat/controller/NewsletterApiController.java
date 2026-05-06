package com.coresolution.mediplat.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.mediplat.model.PlatformSessionUser;
import com.coresolution.mediplat.service.NewsletterService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/newsletters")
public class NewsletterApiController {

    private static final String SESSION_USER = "mediplatUser";

    private final NewsletterService newsletterService;

    public NewsletterApiController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        return ResponseEntity.ok(newsletterService.buildPortalPayload(user));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<?> setSubscription(@RequestBody Map<String, Object> body, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        String newsletterCode = body == null ? "" : String.valueOf(body.getOrDefault("newsletterCode", ""));
        boolean subscribed = body != null && Boolean.TRUE.equals(body.get("subscribed"));
        try {
            newsletterService.setSubscribed(user, newsletterCode, subscribed);
            return ResponseEntity.ok(newsletterService.buildPortalPayload(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/custom")
    public ResponseEntity<?> addCustom(@RequestBody Map<String, Object> body, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        String title = body == null ? "" : String.valueOf(body.getOrDefault("title", ""));
        String url = body == null ? "" : String.valueOf(body.getOrDefault("url", ""));
        try {
            newsletterService.addCustomSubscription(user, title, url);
            return ResponseEntity.ok(newsletterService.buildPortalPayload(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> setFeedback(@RequestBody Map<String, Object> body, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        String newsletterCode = body == null ? "" : String.valueOf(body.getOrDefault("newsletterCode", ""));
        String feedback = body == null ? "" : String.valueOf(body.getOrDefault("feedback", ""));
        try {
            newsletterService.setFeedback(user, newsletterCode, feedback);
            return ResponseEntity.ok(newsletterService.buildPortalPayload(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private PlatformSessionUser sessionUser(HttpSession session) {
        Object user = session == null ? null : session.getAttribute(SESSION_USER);
        return user instanceof PlatformSessionUser sessionUser ? sessionUser : null;
    }
}
