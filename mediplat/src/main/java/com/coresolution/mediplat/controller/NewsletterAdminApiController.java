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
@RequestMapping("/api/admin/newsletters")
public class NewsletterAdminApiController {

    private static final String SESSION_USER = "mediplatUser";

    private final NewsletterService newsletterService;

    public NewsletterAdminApiController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        if (!user.isPlatformAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "플랫폼 관리자 권한이 필요합니다."));
        }
        return ResponseEntity.ok(Map.of("items", newsletterService.listAdminNewsletters()));
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        if (!user.isPlatformAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "플랫폼 관리자 권한이 필요합니다."));
        }
        try {
            newsletterService.saveAdminNewsletter(
                    text(body, "code"),
                    text(body, "title"),
                    text(body, "summary"),
                    text(body, "category"),
                    text(body, "tags"),
                    text(body, "cadence"),
                    text(body, "url"),
                    text(body, "useYn"),
                    intValue(body, "displayOrder"));
            return ResponseEntity.ok(Map.of("items", newsletterService.listAdminNewsletters()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/status")
    public ResponseEntity<?> status(@RequestBody Map<String, Object> body, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        if (!user.isPlatformAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "플랫폼 관리자 권한이 필요합니다."));
        }
        try {
            newsletterService.updateAdminNewsletterStatus(text(body, "code"), text(body, "useYn"));
            return ResponseEntity.ok(Map.of("items", newsletterService.listAdminNewsletters()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private PlatformSessionUser sessionUser(HttpSession session) {
        Object user = session == null ? null : session.getAttribute(SESSION_USER);
        return user instanceof PlatformSessionUser sessionUser ? sessionUser : null;
    }

    private String text(Map<String, Object> body, String key) {
        return body == null ? "" : String.valueOf(body.getOrDefault(key, ""));
    }

    private Integer intValue(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
