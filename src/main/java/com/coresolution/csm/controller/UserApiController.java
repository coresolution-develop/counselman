package com.coresolution.csm.controller;

import java.security.SecureRandom;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.util.AES128;
import com.coresolution.csm.vo.Userdata;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private static final Logger log = LoggerFactory.getLogger(UserApiController.class);

    private final CsmAuthService cs;
    private final AES128 aes;
    private final JdbcTemplate jdbcTemplate;

    // ─────────────────────────────────────────────────
    // 사용자 추가
    // ─────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN') or hasAuthority('ROLE:ASSIGN')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        String loginId  = str(body.get("loginId"));
        String password = str(body.get("password"));
        String name     = str(body.get("name"));

        if (loginId.isBlank() || password.isBlank() || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "아이디, 비밀번호, 이름은 필수입니다."));
        }

        // 중복 ID 확인
        try {
            var existing = cs.loadUserInfo(inst, loginId);
            if (existing != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "이미 사용 중인 아이디입니다."));
            }
        } catch (Exception ignored) {}

        // roleId가 있으면 DB에서 is_system 확인해서 us_col_08 결정
        int userAuth = 2; // 기본: 일반 사용자
        Long roleId = toLong(body.get("roleId"));
        if (roleId != null) {
            userAuth = resolveUserAuth(safe, roleId);
        } else {
            userAuth = parseRole(str(body.get("role")));
        }

        Userdata ud = new Userdata();
        ud.setUs_col_02(loginId);
        ud.setUs_col_03(aes.encrypt(password));
        ud.setUs_col_04(inst);
        ud.setUs_col_05(str(body.get("orgName")));
        ud.setUs_col_06("");
        ud.setUs_col_07("y");
        ud.setUs_col_08(userAuth);
        ud.setUs_col_09(1);
        ud.setUs_col_10(str(body.get("phone")));
        ud.setUs_col_11(str(body.get("email")));
        ud.setUs_col_12(name);
        ud.setUs_col_13(str(body.get("dept")));
        ud.setUs_col_14(str(body.get("rank")));

        try {
            cs.userInsert(ud);
            long newUserId = ud.getUs_col_01(); // @Options(useGeneratedKeys = true)

            // RBAC 역할 배정
            if (roleId != null && newUserId > 0) {
                assignRole(safe, newUserId, roleId, session);
            }

            return ResponseEntity.ok(Map.of("ok", true, "userId", newUserId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "사용자 추가 실패: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // 사용자 수정
    // ─────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN') or hasAuthority('ROLE:ASSIGN')")
    public ResponseEntity<?> updateUser(@PathVariable int id,
                                        @RequestBody Map<String, Object> body,
                                        HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        Userdata ud = cs.userInfo(id, inst);
        if (ud == null) return ResponseEntity.notFound().build();

        ud.setUs_col_12(str(body.getOrDefault("name",  ud.getUs_col_12())));
        ud.setUs_col_13(str(body.getOrDefault("dept",  ud.getUs_col_13())));
        ud.setUs_col_14(str(body.getOrDefault("rank",  ud.getUs_col_14())));
        ud.setUs_col_11(str(body.getOrDefault("email", ud.getUs_col_11())));
        ud.setUs_col_10(str(body.getOrDefault("phone", ud.getUs_col_10())));
        ud.setUs_col_07(str(body.getOrDefault("status","y")));

        // roleId 기반으로 us_col_08 갱신
        Long roleId = toLong(body.get("roleId"));
        if (roleId != null) {
            ud.setUs_col_08(resolveUserAuth(safe, roleId));
        }

        try {
            cs.userUpdate(ud);

            // RBAC 역할 교체: 기존 역할 전부 제거 후 새 역할 배정
            if (roleId != null) {
                jdbcTemplate.update(
                    "DELETE FROM csm.user_role_" + safe + " WHERE user_id = ?", (long) id);
                assignRole(safe, id, roleId, session);
            }

            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "수정 실패: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // 비밀번호 초기화 (임시 비밀번호 발급)
    // ─────────────────────────────────────────────────
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN') or hasAuthority('ROLE:ASSIGN')")
    public ResponseEntity<?> resetPassword(@PathVariable int id, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Userdata ud = cs.userInfo(id, inst);
        if (ud == null) return ResponseEntity.notFound().build();

        String tempPw = genTempPassword();
        String encPw = aes.encrypt(tempPw);

        try {
            int updated = cs.updatePwd(inst, id, encPw);
            if (updated < 1) {
                log.warn("[resetPassword] updatePwd returned 0 rows: userId={} inst={}", id, inst);
                return ResponseEntity.status(500).body(Map.of("error", "비밀번호 초기화 실패 (사용자를 찾을 수 없습니다)"));
            }
            syncMpUserPassword(inst, ud.getUs_col_02(), tempPw);
            return ResponseEntity.ok(Map.of("ok", true, "tempPassword", tempPw));
        } catch (Exception e) {
            log.error("[resetPassword] failed userId={} inst={}", id, inst, e);
            return ResponseEntity.status(500).body(Map.of("error", "비밀번호 초기화 실패"));
        }
    }

    private void syncMpUserPassword(String inst, String loginId, String rawPassword) {
        try {
            String bcryptHash = new BCryptPasswordEncoder().encode(rawPassword);
            int rows = jdbcTemplate.update(
                "UPDATE mp_user SET password_hash = ? WHERE inst_code = ? AND username = ?",
                bcryptHash, inst, loginId);
            if (rows > 0) {
                log.info("[syncMpUserPassword] mp_user synced for inst={} username={}", inst, loginId);
            }
        } catch (Exception e) {
            log.warn("[syncMpUserPassword] inst={} username={}: {}", inst, loginId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    // 사용자 비활성화 (소프트 삭제)
    // ─────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable int id, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Userdata ud = cs.userInfo(id, inst);
        if (ud == null) return ResponseEntity.notFound().build();
        ud.setUs_col_09(2); // 삭제 상태

        try {
            cs.userUpdate(ud);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "삭제 실패"));
        }
    }

    // ─────────────────── helpers ───────────────────

    /** role_{inst} 에서 is_system 조회 → us_col_08 값 결정 */
    private int resolveUserAuth(String safe, long roleId) {
        try {
            Integer isSystem = jdbcTemplate.queryForObject(
                "SELECT is_system FROM csm.role_" + safe + " WHERE role_id = ?",
                Integer.class, roleId);
            return (isSystem != null && isSystem == 1) ? 1 : 2;
        } catch (Exception e) {
            log.warn("[resolveUserAuth] fallback to 2: {}", e.getMessage());
            return 2;
        }
    }

    /** user_role_{inst} 에 역할 배정 (INSERT IGNORE) */
    private void assignRole(String safe, long userId, long roleId, HttpSession session) {
        try {
            String assignedBy = (String) session.getAttribute("username");
            jdbcTemplate.update(
                "INSERT IGNORE INTO csm.user_role_" + safe
                    + " (user_id, role_id, assigned_by) VALUES (?, ?, ?)",
                userId, roleId, assignedBy);
        } catch (Exception e) {
            log.warn("[assignRole] userId={} roleId={}: {}", userId, roleId, e.getMessage());
        }
    }

    private String resolveInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        return v instanceof String s ? s : null;
    }

    private String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        try { return ((Number) v).longValue(); }
        catch (Exception e) { return null; }
    }

    private int parseRole(String role) {
        return switch (role) {
            case "super"  -> 0;
            case "admin"  -> 1;
            default       -> 2;
        };
    }

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private String genTempPassword() {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        return sb.toString();
    }
}
