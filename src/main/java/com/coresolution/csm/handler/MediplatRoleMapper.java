package com.coresolution.csm.handler;

import org.springframework.stereotype.Component;

/**
 * Mediplat role_code → CSM us_col_08(authority) + 자동 부여 역할 매핑.
 * CsmSchemaBootstrapService의 인라인 매핑("USER" ? 1 : 0)을 대체한다.
 */
@Component
public class MediplatRoleMapper {

    public record UserAuthInit(int authority, String autoRoleCode) {}

    public UserAuthInit map(String roleCode) {
        if (roleCode == null) roleCode = "";
        return switch (roleCode.trim().toUpperCase()) {
            case "PLATFORM_ADMIN"    -> new UserAuthInit(0, null);
            case "INSTITUTION_ADMIN" -> new UserAuthInit(1, "SYSTEM_INST_ADMIN");
            default                  -> new UserAuthInit(2, null);
        };
    }
}
