package com.coresolution.csm.controller;

import java.security.SecureRandom;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.util.AES128;
import com.coresolution.csm.vo.Userdata;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final CsmAuthService cs;
    private final AES128 aes;

    // ─────────────────────────────────────────────────
    // 사용자 추가
    // ─────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN') or hasAuthority('ROLE:ASSIGN')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

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

        Userdata ud = new Userdata();
        ud.setUs_col_02(loginId);
        ud.setUs_col_03(aes.encrypt(password));
        ud.setUs_col_04(inst);
        ud.setUs_col_05(str(body.get("orgName")));
        ud.setUs_col_06("");
        ud.setUs_col_07("y");
        ud.setUs_col_08(parseRole(str(body.get("role"))));
        ud.setUs_col_09(1);
        ud.setUs_col_10(str(body.get("phone")));
        ud.setUs_col_11(str(body.get("email")));
        ud.setUs_col_12(name);
        ud.setUs_col_13(str(body.get("dept")));
        ud.setUs_col_14(str(body.get("rank")));

        try {
            cs.userInsert(ud);
            return ResponseEntity.ok(Map.of("ok", true));
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

        Userdata ud = cs.userInfo(id, inst);
        if (ud == null) return ResponseEntity.notFound().build();

        ud.setUs_col_12(str(body.getOrDefault("name",  ud.getUs_col_12())));
        ud.setUs_col_13(str(body.getOrDefault("dept",  ud.getUs_col_13())));
        ud.setUs_col_14(str(body.getOrDefault("rank",  ud.getUs_col_14())));
        ud.setUs_col_11(str(body.getOrDefault("email", ud.getUs_col_11())));
        ud.setUs_col_10(str(body.getOrDefault("phone", ud.getUs_col_10())));
        ud.setUs_col_07(str(body.getOrDefault("status","y")));
        ud.setUs_col_08(parseRole(str(body.getOrDefault("role", roleStr(ud.getUs_col_08())))));

        try {
            cs.userUpdate(ud);
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
        ud.setUs_col_03(aes.encrypt(tempPw));

        try {
            cs.userUpdatePassword(ud);
            return ResponseEntity.ok(Map.of("ok", true, "tempPassword", tempPw));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "비밀번호 초기화 실패"));
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
    private String resolveInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        return v instanceof String s ? s : null;
    }

    private String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private int parseRole(String role) {
        return switch (role) {
            case "super"  -> 0;
            case "admin"  -> 1;
            default       -> 2;
        };
    }

    private String roleStr(int auth) {
        return switch (auth) {
            case 0 -> "super";
            case 1 -> "admin";
            default -> "staff";
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
