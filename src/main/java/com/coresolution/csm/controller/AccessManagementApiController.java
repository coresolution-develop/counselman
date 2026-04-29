package com.coresolution.csm.controller;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.coresolution.csm.serivce.CsmAuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class AccessManagementApiController {

    private static final Logger log = LoggerFactory.getLogger(AccessManagementApiController.class);
    private static final String SERVICE_CODE = "COUNSELMAN";

    private final JdbcTemplate jdbcTemplate;
    private final CsmAuthService cs;

    // ─────────────────────────────────────────────────
    // 기관 직원 목록 + CSM 접근 여부
    // ─────────────────────────────────────────────────
    @GetMapping("/users")
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> listUsers(HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT u.username,
                           u.display_name,
                           u.dept,
                           u.use_yn,
                           CASE WHEN s.use_yn = 'Y' THEN 1 ELSE 0 END AS csm_enabled
                    FROM mp_user u
                    LEFT JOIN mp_user_service s
                           ON s.inst_code = u.inst_code
                          AND s.username  = u.username
                          AND s.service_code = ?
                    WHERE u.inst_code = ?
                      AND u.role_code NOT IN ('PLATFORM_ADMIN', 'ROOM_BOARD_VIEWER')
                    ORDER BY u.dept ASC, u.display_name ASC
                    """, SERVICE_CODE, inst);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("[access/users] inst={}", inst, e);
            return ResponseEntity.status(500).body(Map.of("error", "직원 목록 조회 실패"));
        }
    }

    // ─────────────────────────────────────────────────
    // 개인 CSM 접근 설정
    // ─────────────────────────────────────────────────
    @PostMapping("/users/{username}/counselman")
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> setUserAccess(
            @PathVariable String username,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean enable = Boolean.TRUE.equals(body.get("enable"));
        String useYn = enable ? "Y" : "N";

        try {
            upsertServiceAccess(inst, username, useYn);
            return ResponseEntity.ok(Map.of("ok", true, "useYn", useYn));
        } catch (Exception e) {
            log.error("[access/user] inst={} username={}", inst, username, e);
            return ResponseEntity.status(500).body(Map.of("error", "접근 설정 실패"));
        }
    }

    // ─────────────────────────────────────────────────
    // 부서 전체 CSM 접근 설정
    // ─────────────────────────────────────────────────
    @PostMapping("/dept/counselman")
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> setDeptAccess(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String dept   = str(body.get("dept"));
        boolean enable = Boolean.TRUE.equals(body.get("enable"));
        String useYn  = enable ? "Y" : "N";

        if (!StringUtils.hasText(dept)) {
            return ResponseEntity.badRequest().body(Map.of("error", "부서명이 필요합니다."));
        }

        try {
            List<String> usernames = jdbcTemplate.queryForList(
                    "SELECT username FROM mp_user WHERE inst_code = ? AND dept = ? AND role_code NOT IN ('PLATFORM_ADMIN', 'ROOM_BOARD_VIEWER')",
                    String.class, inst, dept);
            for (String u : usernames) {
                upsertServiceAccess(inst, u, useYn);
            }
            return ResponseEntity.ok(Map.of("ok", true, "updated", usernames.size(), "useYn", useYn));
        } catch (Exception e) {
            log.error("[access/dept] inst={} dept={}", inst, dept, e);
            return ResponseEntity.status(500).body(Map.of("error", "부서 접근 설정 실패"));
        }
    }

    // ─────────────────────────────────────────────────
    // CSM 사용자 → mp_user 동기화
    // ─────────────────────────────────────────────────
    @PostMapping("/sync-csm-users")
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> syncCsmUsers(HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String safe = inst.replaceAll("[^a-zA-Z0-9_]", "_");
        try {
            List<Map<String, Object>> csmUsers = jdbcTemplate.queryForList(
                    "SELECT us_col_02 AS username," +
                    "       COALESCE(NULLIF(us_col_12,''), us_col_02) AS display_name," +
                    "       COALESCE(us_col_13, '') AS dept" +
                    " FROM csm.user_data_" + safe +
                    " WHERE us_col_09 = 1 AND us_col_07 = 'y'");

            int synced = 0;
            for (Map<String, Object> row : csmUsers) {
                String username    = str(row.get("username"));
                String displayName = str(row.get("display_name"));
                String dept        = str(row.get("dept"));
                if (username.isEmpty()) continue;

                jdbcTemplate.update("""
                        INSERT INTO mp_user (inst_code, username, password_hash, display_name, dept, role_code, use_yn)
                        VALUES (?, ?, '$CSM_MANAGED$', ?, ?, 'USER', 'Y')
                        ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), dept = VALUES(dept)
                        """, inst, username, displayName, dept);
                synced++;
            }
            return ResponseEntity.ok(Map.of("ok", true, "synced", synced));
        } catch (Exception e) {
            log.error("[access/sync-csm-users] inst={}", inst, e);
            return ResponseEntity.status(500).body(Map.of("error", "동기화 실패: " + e.getMessage()));
        }
    }

    // ─────────────────── helpers ───────────────────

    private void upsertServiceAccess(String inst, String username, String useYn) {
        jdbcTemplate.update("""
                INSERT INTO mp_user_service (inst_code, username, service_code, use_yn)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE use_yn = ?
                """, inst, username, SERVICE_CODE, useYn, useYn);
    }

    private String resolveInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        return v instanceof String s ? s : null;
    }

    private String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }
}
