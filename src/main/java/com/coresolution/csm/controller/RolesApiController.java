package com.coresolution.csm.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.coresolution.csm.serivce.CsmAuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolesApiController {

    private final JdbcTemplate jdbcTemplate;
    private final CsmAuthService cs;

    // ─────────────────────────────────────────────────
    // 역할 목록 조회 (사용자 수 포함)
    // ─────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> listRoles(HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                "SELECT r.role_id, r.role_code, r.role_name, r.description, r.is_system,"
                + " (SELECT COUNT(*) FROM csm.user_role_" + safe + " ur WHERE ur.role_id = r.role_id) AS user_count"
                + " FROM csm.role_" + safe + " r ORDER BY r.is_system DESC, r.role_id ASC");
        return ResponseEntity.ok(roles);
    }

    // ─────────────────────────────────────────────────
    // 역할 생성
    // ─────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE:CREATE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> createRole(@RequestBody Map<String, String> body, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        String roleName = trim(body.get("role_name"));
        String description = trim(body.get("description"));
        if (roleName == null || roleName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "role_name 필수"));
        }

        // role_code는 이름 기반 자동 생성 (UUID suffix)
        String roleCode = "CUSTOM_" + System.currentTimeMillis();

        jdbcTemplate.update(
                "INSERT INTO csm.role_" + safe + " (role_code, role_name, description, is_system) VALUES (?, ?, ?, 0)",
                roleCode, roleName, description);

        Long newId = jdbcTemplate.queryForObject(
                "SELECT role_id FROM csm.role_" + safe + " WHERE role_code = ?", Long.class, roleCode);
        return ResponseEntity.ok(Map.of("role_id", newId, "role_code", roleCode));
    }

    // ─────────────────────────────────────────────────
    // 역할 수정 (이름·설명, is_system=1 불가)
    // ─────────────────────────────────────────────────
    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE:EDIT') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> updateRole(@PathVariable long roleId,
                                        @RequestBody Map<String, String> body,
                                        HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        Integer isSystem = jdbcTemplate.queryForObject(
                "SELECT is_system FROM csm.role_" + safe + " WHERE role_id = ?", Integer.class, roleId);
        if (isSystem == null) return ResponseEntity.notFound().build();
        if (isSystem == 1) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "시스템 역할은 수정할 수 없습니다."));

        jdbcTemplate.update(
                "UPDATE csm.role_" + safe + " SET role_name = ?, description = ?, updated_at = NOW() WHERE role_id = ?",
                trim(body.get("role_name")), trim(body.get("description")), roleId);
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ─────────────────────────────────────────────────
    // 역할 삭제 (is_system=1 불가)
    // ─────────────────────────────────────────────────
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE:DELETE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> deleteRole(@PathVariable long roleId, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        Integer isSystem = jdbcTemplate.queryForObject(
                "SELECT is_system FROM csm.role_" + safe + " WHERE role_id = ?", Integer.class, roleId);
        if (isSystem == null) return ResponseEntity.notFound().build();
        if (isSystem == 1) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "시스템 역할은 삭제할 수 없습니다."));

        jdbcTemplate.update("DELETE FROM csm.role_permission_" + safe + " WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM csm.user_role_" + safe + " WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM csm.role_" + safe + " WHERE role_id = ?", roleId);
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ─────────────────────────────────────────────────
    // 역할 권한 목록 조회
    // ─────────────────────────────────────────────────
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> getRolePermissions(@PathVariable long roleId, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        List<String> perms = jdbcTemplate.queryForList(
                "SELECT permission_code FROM csm.role_permission_" + safe + " WHERE role_id = ?",
                String.class, roleId);
        return ResponseEntity.ok(perms);
    }

    // ─────────────────────────────────────────────────
    // 역할 권한 전체 교체
    // ─────────────────────────────────────────────────
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE:EDIT') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> setRolePermissions(@PathVariable long roleId,
                                                @RequestBody Map<String, Object> body,
                                                HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        Integer isSystem = jdbcTemplate.queryForObject(
                "SELECT is_system FROM csm.role_" + safe + " WHERE role_id = ?", Integer.class, roleId);
        if (isSystem == null) return ResponseEntity.notFound().build();
        if (isSystem == 1) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "시스템 역할의 권한은 변경할 수 없습니다."));

        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) body.get("permissions");
        if (codes == null) codes = new ArrayList<>();

        jdbcTemplate.update("DELETE FROM csm.role_permission_" + safe + " WHERE role_id = ?", roleId);
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                jdbcTemplate.update(
                        "INSERT IGNORE INTO csm.role_permission_" + safe + " (role_id, permission_code) VALUES (?, ?)",
                        roleId, code.trim());
            }
        }
        return ResponseEntity.ok(Map.of("result", "ok", "count", codes.size()));
    }

    // ─────────────────────────────────────────────────
    // 권한 마스터 목록 (메뉴별 그룹화)
    // ─────────────────────────────────────────────────
    @GetMapping("/permission-master")
    @PreAuthorize("hasAuthority('ROLE:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> getPermissionMaster(HttpSession session) {
        if (resolveInst(session) == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<Map<String, Object>> perms = jdbcTemplate.queryForList(
                "SELECT p.code, p.menu_key, p.resource, p.action, p.label_ko, p.sort_order,"
                + " m.label_ko AS menu_label"
                + " FROM csm.permission_master p"
                + " JOIN csm.menu_master m ON m.menu_key = p.menu_key"
                + " ORDER BY p.sort_order");
        return ResponseEntity.ok(perms);
    }

    // ─────────────────────────────────────────────────
    // 사용자 역할 조회
    // ─────────────────────────────────────────────────
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('ROLE:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> getUserRoles(@PathVariable long userId, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                "SELECT r.role_id, r.role_code, r.role_name, r.is_system"
                + " FROM csm.user_role_" + safe + " ur"
                + " JOIN csm.role_" + safe + " r ON r.role_id = ur.role_id"
                + " WHERE ur.user_id = ?", userId);
        return ResponseEntity.ok(roles);
    }

    // ─────────────────────────────────────────────────
    // 사용자에게 역할 부여
    // ─────────────────────────────────────────────────
    @PostMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('ROLE:ASSIGN') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> assignRole(@PathVariable long userId,
                                        @RequestBody Map<String, Object> body,
                                        HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        Object roleIdObj = body.get("role_id");
        if (roleIdObj == null) return ResponseEntity.badRequest().body(Map.of("error", "role_id 필수"));
        long roleId = ((Number) roleIdObj).longValue();

        String assignedBy = (String) session.getAttribute("username");
        jdbcTemplate.update(
                "INSERT IGNORE INTO csm.user_role_" + safe + " (user_id, role_id, assigned_by) VALUES (?, ?, ?)",
                userId, roleId, assignedBy);
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ─────────────────────────────────────────────────
    // 사용자 역할 제거
    // ─────────────────────────────────────────────────
    @DeleteMapping("/user/{userId}/{roleId}")
    @PreAuthorize("hasAuthority('ROLE:ASSIGN') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> unassignRole(@PathVariable long userId,
                                          @PathVariable long roleId,
                                          HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        jdbcTemplate.update(
                "DELETE FROM csm.user_role_" + safe + " WHERE user_id = ? AND role_id = ?",
                userId, roleId);
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ─────────────────────────────────────────────────
    // 역할 복사
    // ─────────────────────────────────────────────────
    @PostMapping("/{roleId}/copy")
    @PreAuthorize("hasAuthority('ROLE:CREATE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> copyRole(@PathVariable long roleId,
                                      @RequestBody Map<String, String> body,
                                      HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        Map<String, Object> src = jdbcTemplate.queryForMap(
                "SELECT role_name, description FROM csm.role_" + safe + " WHERE role_id = ?", roleId);

        String newName = trim(body.getOrDefault("role_name", src.get("role_name") + " (복사)"));
        String newCode = "CUSTOM_" + System.currentTimeMillis();

        jdbcTemplate.update(
                "INSERT INTO csm.role_" + safe + " (role_code, role_name, description, is_system) VALUES (?, ?, ?, 0)",
                newCode, newName, src.get("description"));
        Long newId = jdbcTemplate.queryForObject(
                "SELECT role_id FROM csm.role_" + safe + " WHERE role_code = ?", Long.class, newCode);

        // 권한 복사
        jdbcTemplate.update(
                "INSERT INTO csm.role_permission_" + safe + " (role_id, permission_code)"
                + " SELECT ?, permission_code FROM csm.role_permission_" + safe + " WHERE role_id = ?",
                newId, roleId);

        return ResponseEntity.ok(Map.of("role_id", newId, "role_code", newCode));
    }

    private String resolveInst(HttpSession session) {
        Object inst = session.getAttribute("inst");
        return inst instanceof String s ? s : null;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
