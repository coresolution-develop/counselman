package com.coresolution.csm.controller;

import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.util.AES128;
import com.coresolution.csm.vo.Userdata;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeApiController {

    private static final Logger log = LoggerFactory.getLogger(MeApiController.class);

    private final CsmAuthService cs;
    private final AES128 aes;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, HttpSession session) {
        String inst = (String) session.getAttribute("inst");
        Userdata u = (Userdata) session.getAttribute("userInfo");
        if (inst == null || u == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String currentPassword = body == null ? null : body.get("currentPassword");
        String newPassword = body == null ? null : body.get("newPassword");
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "현재 비밀번호와 새 비밀번호를 모두 입력해 주세요."));
        }
        if (currentPassword.equals(newPassword)) {
            return ResponseEntity.badRequest().body(Map.of("error", "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다."));
        }

        String username = u.getUs_col_02();
        int userIdx = u.getUs_col_01();

        String encCurrent;
        String encNew;
        try {
            encCurrent = aes.encrypt(currentPassword);
            encNew = aes.encrypt(newPassword);
        } catch (Exception e) {
            log.error("[me/password] encrypt fail userId={} inst={}", userIdx, inst, e);
            return ResponseEntity.status(500).body(Map.of("error", "비밀번호 변경 중 오류가 발생했습니다."));
        }

        if (cs.loginCount(inst, username, encCurrent) <= 0) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "현재 비밀번호가 일치하지 않습니다."));
        }

        try {
            int updated = cs.updatePwd(inst, userIdx, encNew);
            if (updated < 1) {
                log.warn("[me/password] updatePwd 0 rows userId={} inst={}", userIdx, inst);
                return ResponseEntity.status(500).body(Map.of("error", "비밀번호 변경에 실패했습니다."));
            }
            syncMpUserPassword(inst, username, newPassword);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("[me/password] fail userId={} inst={}", userIdx, inst, e);
            return ResponseEntity.status(500).body(Map.of("error", "비밀번호 변경에 실패했습니다."));
        }
    }

    private void syncMpUserPassword(String inst, String loginId, String rawPassword) {
        try {
            String bcryptHash = new BCryptPasswordEncoder().encode(rawPassword);
            int rows = jdbcTemplate.update(
                    "UPDATE mp_user SET password_hash = ? WHERE inst_code = ? AND username = ?",
                    bcryptHash, inst, loginId);
            if (rows > 0) {
                log.info("[me/password] mp_user synced inst={} username={}", inst, loginId);
            }
        } catch (Exception e) {
            log.warn("[me/password] mp_user sync skipped inst={} username={}: {}", inst, loginId, e.getMessage());
        }
    }

}
