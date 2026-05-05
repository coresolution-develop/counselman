package com.coresolution.csm.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.coresolution.csm.serivce.CsmAuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/faq")
@RequiredArgsConstructor
public class FaqApiController {

    private static final Logger log = LoggerFactory.getLogger(FaqApiController.class);

    private final CsmAuthService cs;
    private final JdbcTemplate   jdbcTemplate;

    // ─────────────────────────────────────────────────
    // 목록 조회
    // ─────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAuthority('FAQ:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        try {
            StringBuilder sql = new StringBuilder(
                "SELECT id, category, question, answer, sort_order, use_yn," +
                " DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created_at" +
                " FROM csm.faq_" + safe + " WHERE use_yn = 'Y'");
            List<Object> params = new ArrayList<>();
            if (!category.isBlank()) {
                sql.append(" AND category = ?");
                params.add(category);
            }
            if (!keyword.isBlank()) {
                sql.append(" AND (question LIKE ? OR answer LIKE ?)");
                params.add("%" + keyword + "%");
                params.add("%" + keyword + "%");
            }
            sql.append(" ORDER BY sort_order ASC, id ASC");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.warn("[FAQ:list] inst={}: {}", safe, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // ─────────────────────────────────────────────────
    // 카테고리 목록
    // ─────────────────────────────────────────────────
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('FAQ:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> categories(HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);
        try {
            List<String> cats = jdbcTemplate.queryForList(
                "SELECT DISTINCT category FROM csm.faq_" + safe
                + " WHERE use_yn = 'Y' ORDER BY category",
                String.class);
            return ResponseEntity.ok(cats);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    // ─────────────────────────────────────────────────
    // 등록
    // ─────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAuthority('FAQ:CREATE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        String category = str(body.get("category"));
        String question = str(body.get("question"));
        String answer   = str(body.get("answer"));
        if (category.isBlank() || question.isBlank() || answer.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "카테고리, 질문, 답변은 필수입니다."));
        }

        try {
            int sortOrder = toInt(body.get("sortOrder"), 0);
            jdbcTemplate.update(
                "INSERT INTO csm.faq_" + safe
                + " (category, question, answer, sort_order, use_yn) VALUES (?, ?, ?, ?, 'Y')",
                category, question, answer, sortOrder);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("[FAQ:create] inst={}: {}", safe, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "등록 실패: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FAQ:EDIT') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        String category = str(body.get("category"));
        String question = str(body.get("question"));
        String answer   = str(body.get("answer"));
        if (category.isBlank() || question.isBlank() || answer.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "카테고리, 질문, 답변은 필수입니다."));
        }

        try {
            int sortOrder = toInt(body.get("sortOrder"), 0);
            int rows = jdbcTemplate.update(
                "UPDATE csm.faq_" + safe
                + " SET category=?, question=?, answer=?, sort_order=?, updated_at=NOW() WHERE id=?",
                category, question, answer, sortOrder, id);
            if (rows == 0) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("[FAQ:update] inst={} id={}: {}", safe, id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "수정 실패: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // 삭제 (소프트)
    // ─────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FAQ:DELETE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable long id, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        try {
            int rows = jdbcTemplate.update(
                "UPDATE csm.faq_" + safe + " SET use_yn='N', updated_at=NOW() WHERE id=?", id);
            if (rows == 0) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("[FAQ:delete] inst={} id={}: {}", safe, id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "삭제 실패"));
        }
    }

    // ─────────────────── helpers ───────────────────

    private String resolveInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        return v instanceof String s ? s : null;
    }

    private String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private int toInt(Object v, int def) {
        if (v == null) return def;
        try { return ((Number) v).intValue(); }
        catch (Exception e) { return def; }
    }
}
