package com.coresolution.csm.serivce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.csm.util.AES128;
import com.coresolution.csm.vo.Userdata;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformAdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrapService.class);
    private static final String CORE_INST_CODE = "core";
    private static final String CORE_INST_NAME = "MediPlat Platform";

    private final CsmAuthService cs;
    private final JdbcTemplate jdbcTemplate;
    private final AES128 aes128;

    @Value("${platform.admin.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${platform.admin.username:}")
    private String username;

    @Value("${platform.admin.password:}")
    private String password;

    @Value("${platform.admin.name:Platform Admin}")
    private String name;

    @Value("${platform.admin.email:}")
    private String email;

    @Value("${platform.admin.phone:}")
    private String phone;

    @Value("${platform.admin.department:MediPlat}")
    private String department;

    @Value("${platform.admin.position:Platform Administrator}")
    private String position;

    @Value("${platform.admin.note:Bootstrap platform administrator}")
    private String note;

    @Value("${platform.admin.sync-password-on-startup:false}")
    private boolean syncPasswordOnStartup;

    @PostConstruct
    public void bootstrapPlatformAdmin() {
        if (!bootstrapEnabled) {
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("platform admin bootstrap skipped: username/password not configured");
            return;
        }

        ensureCoreInstitution();
        cs.createUserTableIfNotExists(CORE_INST_CODE);
        ensurePlatformAdminUser();
    }

    private void ensureCoreInstitution() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM csm.inst_data_cs WHERE id_col_03 = ?",
                Integer.class,
                CORE_INST_CODE);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE csm.inst_data_cs
                    SET id_col_02 = COALESCE(NULLIF(id_col_02, ''), ?),
                        id_col_04 = 'y'
                    WHERE id_col_03 = ?
                    """, CORE_INST_NAME, CORE_INST_CODE);
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO csm.inst_data_cs
                (id_col_02, id_col_03, id_col_04, id_col_05, id_col_06, id_col_07, id_col_08, id_col_09)
                VALUES (?, ?, 'y', ?, ?, ?, ?, ?)
                """,
                CORE_INST_NAME,
                CORE_INST_CODE,
                "MediPlat platform core administrator space",
                "",
                "",
                "",
                "");
    }

    private void ensurePlatformAdminUser() {
        String normalizedUsername = username.trim();
        String encryptedPassword = aes128.encrypt(password.trim());
        Userdata existing = cs.loadUserInfo(CORE_INST_CODE, normalizedUsername);

        if (existing == null) {
            Userdata user = new Userdata();
            user.setUs_col_02(normalizedUsername);
            user.setUs_col_03(encryptedPassword);
            user.setUs_col_04(CORE_INST_CODE);
            user.setUs_col_05(CORE_INST_NAME);
            user.setUs_col_06(note);
            user.setUs_col_07("y");
            user.setUs_col_08(0);
            user.setUs_col_09(1);
            user.setUs_col_10(trimToNull(phone));
            user.setUs_col_11(trimToNull(email));
            user.setUs_col_12(trimToNull(name));
            user.setUs_col_13(trimToNull(department));
            user.setUs_col_14(trimToNull(position));
            cs.userInsert(user);
            ensurePlatformAdminMirrorRow(normalizedUsername);
            log.info("bootstrapped platform admin account: {}", normalizedUsername);
            return;
        }

        jdbcTemplate.update("""
                UPDATE csm.user_data_core
                SET us_col_03 = CASE WHEN ? THEN ? ELSE us_col_03 END,
                    us_col_05 = ?,
                    us_col_06 = ?,
                    us_col_07 = 'y',
                    us_col_08 = 0,
                    us_col_09 = 1,
                    us_col_10 = ?,
                    us_col_11 = ?,
                    us_col_12 = ?,
                    us_col_13 = ?,
                    us_col_14 = ?
                WHERE us_col_02 = ?
                """,
                syncPasswordOnStartup,
                encryptedPassword,
                CORE_INST_NAME,
                note,
                trimToNull(phone),
                trimToNull(email),
                trimToNull(name),
                trimToNull(department),
                trimToNull(position),
                normalizedUsername);
        ensurePlatformAdminMirrorRow(normalizedUsername);
        log.info("platform admin account is ready: {}", normalizedUsername);
    }

    private void ensurePlatformAdminMirrorRow(String normalizedUsername) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM csm.user_data_cs WHERE us_col_01 = ? AND us_col_02 = ?",
                Integer.class,
                normalizedUsername,
                CORE_INST_CODE);

        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE csm.user_data_cs
                    SET us_col_03 = ?,
                        us_col_04 = ?,
                        us_col_05 = ?,
                        us_col_06 = ?,
                        us_col_07 = ?
                    WHERE us_col_01 = ?
                      AND us_col_02 = ?
                    """,
                    CORE_INST_NAME,
                    note,
                    trimToNull(phone),
                    trimToNull(email),
                    "platform_admin",
                    normalizedUsername,
                    CORE_INST_CODE);
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO csm.user_data_cs
                (us_col_01, us_col_02, us_col_03, us_col_04, us_col_05, us_col_06, us_col_07)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                normalizedUsername,
                CORE_INST_CODE,
                CORE_INST_NAME,
                note,
                trimToNull(phone),
                trimToNull(email),
                "platform_admin");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
