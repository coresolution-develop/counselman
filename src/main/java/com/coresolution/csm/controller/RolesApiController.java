package com.coresolution.csm.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.coresolution.csm.serivce.CsmAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolesApiController {

    private static final Logger log = LoggerFactory.getLogger(RolesApiController.class);

    private static final Set<String> ALLOWED_ICONS = Set.of(
        "bed", "bell", "briefcase", "building", "calendar", "chart", "chat",
        "clipboard", "eye", "headset", "inbox", "key", "list", "megaphone",
        "message", "notebook", "phone", "send", "settings", "shield", "sliders", "users"
    );

    private final JdbcTemplate jdbcTemplate;
    private final CsmAuthService cs;

    @Autowired private ObjectMapper objectMapper;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.api.model:gpt-4.1-mini}")
    private String openAiModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

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
                "SELECT r.role_id, r.role_code, r.role_name, r.description, r.is_system, r.icon_name,"
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
        String iconName = trim(body.get("icon_name"));
        if (iconName != null && !ALLOWED_ICONS.contains(iconName)) iconName = null;

        jdbcTemplate.update(
                "INSERT INTO csm.role_" + safe + " (role_code, role_name, description, icon_name, is_system) VALUES (?, ?, ?, ?, 0)",
                roleCode, roleName, description, iconName);

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
        syncUserAuth(safe, userId);
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
        syncUserAuth(safe, userId);
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ─────────────────────────────────────────────────
    // 역할에 속한 사용자 목록
    // ─────────────────────────────────────────────────
    @GetMapping("/{roleId}/users")
    @PreAuthorize("hasAuthority('ROLE:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> getRoleUsers(@PathVariable long roleId, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT u.us_col_01 AS user_id, u.us_col_02 AS user_login,"
                + " u.us_col_12 AS user_name, u.us_col_13 AS dept, u.us_col_14 AS position"
                + " FROM csm.user_role_" + safe + " ur"
                + " JOIN csm.user_data_" + safe + " u ON u.us_col_01 = ur.user_id"
                + " WHERE ur.role_id = ? ORDER BY u.us_col_12", roleId);
        return ResponseEntity.ok(users);
    }

    // ─────────────────────────────────────────────────
    // 기관 전체 사용자 목록 (역할 추가용)
    // ─────────────────────────────────────────────────
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> getAllUsers(HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT us_col_01 AS user_id, us_col_02 AS user_login,"
                + " us_col_12 AS user_name, us_col_13 AS dept, us_col_14 AS position"
                + " FROM csm.user_data_" + safe
                + " WHERE us_col_09 = 1 ORDER BY us_col_12");
        return ResponseEntity.ok(users);
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

    // ─────────────────────────────────────────────────
    // AI 아이콘 추천
    // ─────────────────────────────────────────────────
    @PostMapping("/suggest-icon")
    @PreAuthorize("hasAuthority('ROLE:CREATE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> suggestIcon(@RequestBody Map<String, String> body, HttpSession session) {
        if (resolveInst(session) == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String roleName = trim(body.get("role_name"));
        if (roleName == null || roleName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "role_name 필수"));
        }

        String apiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
        String icon = apiKey.isBlank() ? keywordIcon(roleName) : callOpenAiForIcon(roleName, apiKey);
        return ResponseEntity.ok(Map.of("icon", icon));
    }

    private String callOpenAiForIcon(String roleName, String apiKey) {
        try {
            String base = openAiBaseUrl == null ? "https://api.openai.com/v1" : openAiBaseUrl.trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String endpoint = base.endsWith("/responses") ? base
                    : base.endsWith("/v1") ? base + "/responses"
                    : base + "/v1/responses";

            String prompt = "병원 관리 시스템에서 직원 역할 아이콘을 선택해야 합니다.\n"
                + "역할 이름: \"" + roleName + "\"\n"
                + "다음 목록에서 가장 적합한 아이콘 하나를 선택하세요:\n"
                + String.join(", ", ALLOWED_ICONS) + "\n"
                + "아이콘 이름만 출력하세요. 설명이나 다른 텍스트는 절대 쓰지 마세요.";

            String model = (openAiModel == null || openAiModel.isBlank()) ? "gpt-4.1-mini" : openAiModel.trim();
            String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "max_output_tokens", 10,
                "input", List.of(Map.of(
                    "role", "user",
                    "content", List.of(Map.of("type", "input_text", "text", prompt))
                ))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[suggest-icon] openai status={}", response.statusCode());
                return keywordIcon(roleName);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = extractIconText(root).trim().toLowerCase().replaceAll("[^a-z\\-]", "");
            return ALLOWED_ICONS.contains(text) ? text : keywordIcon(roleName);
        } catch (Exception e) {
            log.warn("[suggest-icon] openai error: {}", e.toString());
            return keywordIcon(roleName);
        }
    }

    private String extractIconText(JsonNode root) {
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual()) return outputText.asText("");
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                for (JsonNode c : item.path("content")) {
                    String t = c.path("text").asText("").trim();
                    if (!t.isBlank()) return t;
                }
            }
        }
        return "";
    }

    private String keywordIcon(String roleName) {
        String n = roleName.toLowerCase();
        if (n.contains("관리") || n.contains("admin")) return "shield";
        if (n.contains("상담")) return "headset";
        if (n.contains("접수")) return "clipboard";
        if (n.contains("법인") || n.contains("기관")) return "building";
        if (n.contains("문자") || n.contains("메시지")) return "message";
        if (n.contains("통계") || n.contains("리포트")) return "chart";
        if (n.contains("사용자") || n.contains("직원")) return "users";
        return "key";
    }

    /** Recalculates us_col_08 from the user's current roles and writes it back to user_data. */
    private void syncUserAuth(String safe, long userId) {
        try {
            List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                    "SELECT r.is_system FROM csm.user_role_" + safe + " ur"
                    + " JOIN csm.role_" + safe + " r ON r.role_id = ur.role_id"
                    + " WHERE ur.user_id = ?", userId);
            int auth = roles.stream()
                    .anyMatch(r -> Integer.valueOf(1).equals(r.get("is_system"))) ? 1 : 2;
            jdbcTemplate.update(
                    "UPDATE csm.user_data_" + safe + " SET us_col_08 = ? WHERE us_col_01 = ?",
                    auth, userId);
        } catch (Exception e) {
            // non-fatal; log only
            org.slf4j.LoggerFactory.getLogger(RolesApiController.class)
                    .warn("[syncUserAuth] userId={}: {}", userId, e.getMessage());
        }
    }

    private String resolveInst(HttpSession session) {
        Object inst = session.getAttribute("inst");
        return inst instanceof String s ? s : null;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
