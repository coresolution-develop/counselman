package com.coresolution.csm.serivce;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(PermissionResolver.class);

    private final JdbcTemplate jdbcTemplate;

    public PermissionResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 사용자의 최종 권한 집합을 계산한다.
     * 알고리즘: (역할 권한 합집합) + (사용자 오버라이드 적용)
     *
     * @param userId  user_data_{inst}.us_col_01
     * @param inst    sanitized inst code (e.g. "CS")
     * @return 권한 코드 집합 (e.g. {"COUNSEL:READ", "COUNSEL:CREATE", ...})
     */
    public Set<String> resolveUserPermissions(long userId, String inst) {
        String safe = sanitize(inst);
        Set<String> perms = new HashSet<>();

        // 1. 역할 권한 합집합
        try {
            List<String> roleCodes = jdbcTemplate.queryForList(
                    "SELECT rp.permission_code"
                    + " FROM csm.user_role_" + safe + " ur"
                    + " JOIN csm.role_permission_" + safe + " rp ON rp.role_id = ur.role_id"
                    + " WHERE ur.user_id = ?",
                    String.class, userId);
            perms.addAll(roleCodes);
        } catch (Exception e) {
            log.warn("[perm] role perm query failed inst={} userId={}: {}", safe, userId, e.toString());
        }

        // 2. 사용자별 오버라이드 적용
        try {
            jdbcTemplate.query(
                    "SELECT permission_code, granted FROM csm.user_permission_" + safe + " WHERE user_id = ?",
                    rs -> {
                        String code = rs.getString("permission_code");
                        int granted = rs.getInt("granted");
                        if (granted == 1) {
                            perms.add(code);
                        } else {
                            perms.remove(code);
                        }
                    }, userId);
        } catch (Exception e) {
            log.warn("[perm] override query failed inst={} userId={}: {}", safe, userId, e.toString());
        }

        return perms;
    }

    /**
     * us_col_08 값이 0(PLATFORM_ADMIN)인 경우 모든 권한을 반환한다.
     */
    public Set<String> allPermissions() {
        Set<String> all = new HashSet<>();
        try {
            List<String> codes = jdbcTemplate.queryForList(
                    "SELECT code FROM csm.permission_master", String.class);
            all.addAll(codes);
        } catch (Exception e) {
            log.warn("[perm] allPermissions query failed: {}", e.toString());
        }
        return all;
    }

    private String sanitize(String inst) {
        if (inst == null || !inst.matches("^[A-Za-z0-9_]{2,20}$")) {
            throw new IllegalArgumentException("invalid inst: " + inst);
        }
        return inst;
    }
}
