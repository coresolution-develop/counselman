package com.coresolution.mediplat.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.mediplat.model.PlatformInstitution;
import com.coresolution.mediplat.model.PlatformService;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.coresolution.mediplat.model.PlatformUser;

import jakarta.annotation.PostConstruct;

@Service
public class PlatformStoreService {

    private static final String USE_Y = "Y";
    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String DEFAULT_SERVICE_CODE = "COUNSELMAN";

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private DatabaseDialect databaseDialect = DatabaseDialect.H2;

    @Value("${platform.bootstrap.admin-inst-code:core}")
    private String bootstrapAdminInstCode;

    @Value("${platform.bootstrap.admin-inst-name:MediPlat Platform}")
    private String bootstrapAdminInstName;

    @Value("${platform.bootstrap.admin-username:platformadmin}")
    private String bootstrapAdminUsername;

    @Value("${platform.bootstrap.admin-password:ChangeMe123!}")
    private String bootstrapAdminPassword;

    @Value("${platform.bootstrap.admin-name:Platform Admin}")
    private String bootstrapAdminName;

    @Value("${platform.bootstrap.counselman-base-url:http://localhost:8081/csm}")
    private String bootstrapCounselmanBaseUrl;

    private final CounselManAccountService counselManAccountService;

    public PlatformStoreService(JdbcTemplate jdbcTemplate, CounselManAccountService counselManAccountService) {
        this.jdbcTemplate = jdbcTemplate;
        this.counselManAccountService = counselManAccountService;
    }

    @PostConstruct
    public void initialize() {
        databaseDialect = detectDatabaseDialect();
        createTables();
        bootstrapDefaults();
    }

    public PlatformSessionUser authenticate(String instCode, String username, String rawPassword) {
        CounselManAccountService.AuthenticatedUser authenticatedUser = counselManAccountService.authenticate(
                instCode,
                username,
                rawPassword);
        if (authenticatedUser == null) {
            return null;
        }
        PlatformUser managedUser = findUser(authenticatedUser.instCode(), authenticatedUser.username());
        String roleCode = isPlatformAdminAccount(authenticatedUser.instCode(), authenticatedUser.username(), managedUser)
                        ? ROLE_PLATFORM_ADMIN
                        : ROLE_USER;
        return new PlatformSessionUser(
                authenticatedUser.instCode(),
                authenticatedUser.username(),
                authenticatedUser.displayName(),
                roleCode);
    }

    private boolean isPlatformAdminAccount(String instCode, String username, PlatformUser managedUser) {
        String normalizedBootstrapInstCode = normalizeInstCode(bootstrapAdminInstCode);
        String normalizedBootstrapUsername = normalizeUsername(bootstrapAdminUsername);
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!Objects.equals(normalizedBootstrapInstCode, normalizedInstCode)
                || !Objects.equals(normalizedBootstrapUsername, normalizedUsername)) {
            return false;
        }
        return managedUser != null
                && USE_Y.equalsIgnoreCase(managedUser.getUseYn())
                && ROLE_PLATFORM_ADMIN.equalsIgnoreCase(managedUser.getRoleCode());
    }

    public List<PlatformService> listAccessibleServices(PlatformSessionUser user) {
        if (user == null) {
            return List.of();
        }
        if (user.isPlatformAdmin()) {
            return listAllServices();
        }
        PlatformInstitution institution = findInstitution(user.getInstCode());
        if (institution != null && !USE_Y.equalsIgnoreCase(institution.getUseYn())) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT s.id, s.service_code, s.service_name, s.base_url, s.sso_entry_path, s.user_target, s.admin_target,
                       s.description, s.use_yn, s.display_order, COALESCE(a.use_yn, 'N') AS access_yn
                FROM mp_service s
                LEFT JOIN mp_institution_service a
                  ON a.service_code = s.service_code
                 AND a.inst_code = ?
                WHERE s.use_yn = 'Y'
                  AND COALESCE(a.use_yn, 'N') = 'Y'
                ORDER BY s.display_order ASC, s.id ASC
                """, (rs, rowNum) -> new PlatformService(
                        rs.getLong("id"),
                        rs.getString("service_code"),
                        rs.getString("service_name"),
                        rs.getString("base_url"),
                        rs.getString("sso_entry_path"),
                        rs.getString("user_target"),
                        rs.getString("admin_target"),
                        rs.getString("description"),
                        rs.getString("use_yn"),
                        rs.getInt("display_order"),
                        rs.getString("access_yn")),
                user.getInstCode());
    }

    public List<PlatformInstitution> listInstitutions() {
        Map<String, PlatformInstitution> mergedInstitutions = new LinkedHashMap<>();
        for (CounselManAccountService.CounselManInstitution institution : counselManAccountService.listInstitutions()) {
            mergedInstitutions.put(
                    institution.instCode(),
                    new PlatformInstitution(
                            null,
                            institution.instCode(),
                            institution.instName(),
                            institution.useYn()));
        }
        for (PlatformInstitution institution : listStoredInstitutions()) {
            mergedInstitutions.put(institution.getInstCode(), institution);
        }
        return mergedInstitutions.values().stream()
                .sorted((left, right) -> {
                    int rankCompare = Integer.compare(institutionRank(left.getInstCode()), institutionRank(right.getInstCode()));
                    if (rankCompare != 0) {
                        return rankCompare;
                    }
                    int nameCompare = String.CASE_INSENSITIVE_ORDER.compare(
                            Objects.toString(left.getInstName(), ""),
                            Objects.toString(right.getInstName(), ""));
                    if (nameCompare != 0) {
                        return nameCompare;
                    }
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            Objects.toString(left.getInstCode(), ""),
                            Objects.toString(right.getInstCode(), ""));
                })
                .toList();
    }

    public List<PlatformUser> listUsers() {
        return jdbcTemplate.query("""
                SELECT id, inst_code, username, password_hash, display_name, role_code, use_yn
                FROM mp_user
                ORDER BY id ASC
                """, (rs, rowNum) -> new PlatformUser(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getString("role_code"),
                        rs.getString("use_yn")));
    }

    public List<PlatformService> listAllServices() {
        return jdbcTemplate.query("""
                SELECT s.id, s.service_code, s.service_name, s.base_url, s.sso_entry_path, s.user_target, s.admin_target,
                       s.description, s.use_yn, s.display_order, 'Y' AS access_yn
                FROM mp_service s
                ORDER BY s.display_order ASC, s.id ASC
                """, (rs, rowNum) -> new PlatformService(
                        rs.getLong("id"),
                        rs.getString("service_code"),
                        rs.getString("service_name"),
                        rs.getString("base_url"),
                        rs.getString("sso_entry_path"),
                        rs.getString("user_target"),
                        rs.getString("admin_target"),
                        rs.getString("description"),
                        rs.getString("use_yn"),
                        rs.getInt("display_order"),
                        rs.getString("access_yn")));
    }

    public List<String> listEnabledServiceCodes(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return List.of();
        }
        PlatformInstitution institution = findInstitution(normalizedInstCode);
        if (institution != null && !USE_Y.equalsIgnoreCase(institution.getUseYn())) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT service_code
                FROM mp_institution_service
                WHERE inst_code = ?
                  AND use_yn = 'Y'
                ORDER BY service_code ASC
                """, (rs, rowNum) -> rs.getString("service_code"), normalizedInstCode);
    }

    public PlatformService findService(String serviceCode) {
        List<PlatformService> result = jdbcTemplate.query("""
                SELECT s.id, s.service_code, s.service_name, s.base_url, s.sso_entry_path, s.user_target, s.admin_target,
                       s.description, s.use_yn, s.display_order, 'Y' AS access_yn
                FROM mp_service s
                WHERE s.service_code = ?
                LIMIT 1
                """, (rs, rowNum) -> new PlatformService(
                        rs.getLong("id"),
                        rs.getString("service_code"),
                        rs.getString("service_name"),
                        rs.getString("base_url"),
                        rs.getString("sso_entry_path"),
                        rs.getString("user_target"),
                        rs.getString("admin_target"),
                        rs.getString("description"),
                        rs.getString("use_yn"),
                        rs.getInt("display_order"),
                        rs.getString("access_yn")),
                normalizeServiceCode(serviceCode));
        return result.isEmpty() ? null : result.get(0);
    }

    public void saveInstitution(String instCode, String instName, String useYn) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUseYn = normalizeYn(useYn);
        String resolvedInstName = resolveInstitutionName(normalizedInstCode, instName);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(resolvedInstName)) {
            throw new IllegalArgumentException("기관코드와 기관명은 필수입니다.");
        }
        upsertInstitution(normalizedInstCode, resolvedInstName, normalizedUseYn);
    }

    public void saveUser(String instCode, String username, String rawPassword, String displayName, String roleCode, String useYn) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(username);
        String normalizedUseYn = normalizeYn(useYn);
        String normalizedRole = normalizeRole(roleCode);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)
                || !StringUtils.hasText(rawPassword) || !StringUtils.hasText(displayName)) {
            throw new IllegalArgumentException("사용자 등록 필수값이 누락되었습니다.");
        }
        if (ensureInstitutionRegistered(normalizedInstCode, null) == null) {
            throw new IllegalArgumentException("먼저 기관을 등록해 주세요.");
        }
        String hash = passwordEncoder.encode(rawPassword.trim());
        upsertUser(normalizedInstCode, normalizedUsername, hash, displayName.trim(), normalizedRole, normalizedUseYn);
    }

    public void saveService(
            String serviceCode,
            String serviceName,
            String baseUrl,
            String ssoEntryPath,
            String userTarget,
            String adminTarget,
            String description,
            String useYn,
            Integer displayOrder) {
        String normalizedCode = normalizeServiceCode(serviceCode);
        if (!StringUtils.hasText(normalizedCode) || !StringUtils.hasText(serviceName) || !StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("서비스 코드, 이름, base URL은 필수입니다.");
        }
        upsertService(
                normalizedCode,
                serviceName.trim(),
                trimTrailingSlash(baseUrl),
                normalizePath(ssoEntryPath, "/mediplat/sso/entry"),
                normalizePath(userTarget, "/counsel/list?page=1&perPageNum=10&comment="),
                normalizePath(adminTarget, "/core/admin"),
                trimToNull(description),
                normalizeYn(useYn),
                displayOrder == null ? 0 : displayOrder);
    }

    public void saveInstitutionServiceAccess(String instCode, List<String> enabledServiceCodes) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            throw new IllegalArgumentException("기관을 선택해 주세요.");
        }
        if (ensureInstitutionRegistered(normalizedInstCode, null) == null) {
            throw new IllegalArgumentException("등록되지 않은 기관코드입니다.");
        }
        List<String> normalizedCodes = new ArrayList<>();
        if (enabledServiceCodes != null) {
            enabledServiceCodes.stream()
                    .filter(Objects::nonNull)
                    .map(this::normalizeServiceCode)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .forEach(normalizedCodes::add);
        }
        for (PlatformService service : listAllServices()) {
            upsertInstitutionService(
                    normalizedInstCode,
                    service.getServiceCode(),
                    normalizedCodes.contains(service.getServiceCode()) ? "Y" : "N");
        }
    }

    private void createTables() {
        if (isMySql()) {
            createMySqlTables();
            return;
        }
        createH2Tables();
    }

    private void createMySqlTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_institution (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL UNIQUE,
                    inst_name VARCHAR(100) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y'
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_user (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    display_name VARCHAR(100) NOT NULL,
                    role_code VARCHAR(30) NOT NULL DEFAULT 'USER',
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    UNIQUE KEY uq_mp_user_inst_username (inst_code, username)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_service (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    service_code VARCHAR(50) NOT NULL UNIQUE,
                    service_name VARCHAR(100) NOT NULL,
                    base_url VARCHAR(255) NOT NULL,
                    sso_entry_path VARCHAR(120) NOT NULL,
                    user_target VARCHAR(255) NOT NULL,
                    admin_target VARCHAR(255) NOT NULL,
                    description VARCHAR(255),
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    display_order INT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_institution_service (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    service_code VARCHAR(50) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'N',
                    UNIQUE KEY uq_mp_institution_service (inst_code, service_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void createH2Tables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_institution (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL UNIQUE,
                    inst_name VARCHAR(100) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y'
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_user (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    display_name VARCHAR(100) NOT NULL,
                    role_code VARCHAR(30) NOT NULL DEFAULT 'USER',
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    UNIQUE (inst_code, username)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_service (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    service_code VARCHAR(50) NOT NULL UNIQUE,
                    service_name VARCHAR(100) NOT NULL,
                    base_url VARCHAR(255) NOT NULL,
                    sso_entry_path VARCHAR(120) NOT NULL,
                    user_target VARCHAR(255) NOT NULL,
                    admin_target VARCHAR(255) NOT NULL,
                    description VARCHAR(255),
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    display_order INT NOT NULL DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_institution_service (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    service_code VARCHAR(50) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'N',
                    UNIQUE (inst_code, service_code)
                )
                """);
    }

    private void bootstrapDefaults() {
        saveInstitution(bootstrapAdminInstCode, bootstrapAdminInstName, USE_Y);
        saveUser(
                bootstrapAdminInstCode,
                bootstrapAdminUsername,
                bootstrapAdminPassword,
                bootstrapAdminName,
                ROLE_PLATFORM_ADMIN,
                USE_Y);
        saveService(
                DEFAULT_SERVICE_CODE,
                "CounselMan",
                bootstrapCounselmanBaseUrl,
                "/mediplat/sso/entry",
                "/counsel/list?page=1&perPageNum=10&comment=",
                "/core/admin",
                "기관별 상담관리 서비스",
                USE_Y,
                1);
        saveInstitutionServiceAccess(bootstrapAdminInstCode, List.of(DEFAULT_SERVICE_CODE));
    }

    private PlatformUser findUser(String instCode, String username) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return null;
        }
        List<PlatformUser> users = jdbcTemplate.query("""
                SELECT id, inst_code, username, password_hash, display_name, role_code, use_yn
                FROM mp_user
                WHERE inst_code = ?
                  AND username = ?
                LIMIT 1
                """, (rs, rowNum) -> new PlatformUser(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getString("role_code"),
                        rs.getString("use_yn")),
                normalizedInstCode,
                normalizedUsername);
        return users.isEmpty() ? null : users.get(0);
    }

    private PlatformInstitution findInstitution(String instCode) {
        PlatformInstitution storedInstitution = findStoredInstitution(instCode);
        if (storedInstitution != null) {
            return storedInstitution;
        }
        CounselManAccountService.CounselManInstitution counselManInstitution = counselManAccountService.findInstitution(instCode);
        if (counselManInstitution == null) {
            return null;
        }
        return new PlatformInstitution(
                null,
                counselManInstitution.instCode(),
                counselManInstitution.instName(),
                counselManInstitution.useYn());
    }

    private List<PlatformInstitution> listStoredInstitutions() {
        return jdbcTemplate.query("""
                SELECT id, inst_code, inst_name, use_yn
                FROM mp_institution
                ORDER BY id ASC
                """, (rs, rowNum) -> new PlatformInstitution(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("inst_name"),
                        rs.getString("use_yn")));
    }

    private PlatformInstitution findStoredInstitution(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return null;
        }
        List<PlatformInstitution> institutions = jdbcTemplate.query("""
                SELECT id, inst_code, inst_name, use_yn
                FROM mp_institution
                WHERE inst_code = ?
                LIMIT 1
                """, (rs, rowNum) -> new PlatformInstitution(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("inst_name"),
                        rs.getString("use_yn")),
                normalizedInstCode);
        return institutions.isEmpty() ? null : institutions.get(0);
    }

    private PlatformInstitution ensureInstitutionRegistered(String instCode, String preferredInstName) {
        PlatformInstitution storedInstitution = findStoredInstitution(instCode);
        if (storedInstitution != null) {
            return storedInstitution;
        }
        String normalizedInstCode = normalizeInstCode(instCode);
        String resolvedInstName = resolveInstitutionName(normalizedInstCode, preferredInstName);
        if (!StringUtils.hasText(resolvedInstName)) {
            return null;
        }
        CounselManAccountService.CounselManInstitution counselManInstitution = counselManAccountService.findInstitution(normalizedInstCode);
        upsertInstitution(
                normalizedInstCode,
                resolvedInstName,
                counselManInstitution == null ? USE_Y : normalizeYn(counselManInstitution.useYn()));
        return findStoredInstitution(normalizedInstCode);
    }

    private String resolveInstitutionName(String instCode, String preferredInstName) {
        if (StringUtils.hasText(preferredInstName)) {
            return preferredInstName.trim();
        }
        CounselManAccountService.CounselManInstitution counselManInstitution = counselManAccountService.findInstitution(instCode);
        if (counselManInstitution != null && StringUtils.hasText(counselManInstitution.instName())) {
            return counselManInstitution.instName().trim();
        }
        return null;
    }

    private void upsertInstitution(String instCode, String instName, String useYn) {
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_institution (inst_code, inst_name, use_yn)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        inst_name = ?,
                        use_yn = ?
                    """,
                    instCode,
                    instName,
                    useYn,
                    instName,
                    useYn);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_institution (inst_code, inst_name, use_yn)
                KEY (inst_code)
                VALUES (?, ?, ?)
                """, instCode, instName, useYn);
    }

    private void upsertUser(String instCode, String username, String passwordHash, String displayName, String roleCode, String useYn) {
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_user (inst_code, username, password_hash, display_name, role_code, use_yn)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        password_hash = ?,
                        display_name = ?,
                        role_code = ?,
                        use_yn = ?
                    """,
                    instCode,
                    username,
                    passwordHash,
                    displayName,
                    roleCode,
                    useYn,
                    passwordHash,
                    displayName,
                    roleCode,
                    useYn);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_user (inst_code, username, password_hash, display_name, role_code, use_yn)
                KEY (inst_code, username)
                VALUES (?, ?, ?, ?, ?, ?)
                """, instCode, username, passwordHash, displayName, roleCode, useYn);
    }

    private void upsertService(
            String serviceCode,
            String serviceName,
            String baseUrl,
            String ssoEntryPath,
            String userTarget,
            String adminTarget,
            String description,
            String useYn,
            Integer displayOrder) {
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_service (service_code, service_name, base_url, sso_entry_path, user_target, admin_target,
                                            description, use_yn, display_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        service_name = ?,
                        base_url = ?,
                        sso_entry_path = ?,
                        user_target = ?,
                        admin_target = ?,
                        description = ?,
                        use_yn = ?,
                        display_order = ?
                    """,
                    serviceCode,
                    serviceName,
                    baseUrl,
                    ssoEntryPath,
                    userTarget,
                    adminTarget,
                    description,
                    useYn,
                    displayOrder,
                    serviceName,
                    baseUrl,
                    ssoEntryPath,
                    userTarget,
                    adminTarget,
                    description,
                    useYn,
                    displayOrder);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_service (service_code, service_name, base_url, sso_entry_path, user_target, admin_target,
                                       description, use_yn, display_order)
                KEY (service_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                serviceCode,
                serviceName,
                baseUrl,
                ssoEntryPath,
                userTarget,
                adminTarget,
                description,
                useYn,
                displayOrder);
    }

    private void upsertInstitutionService(String instCode, String serviceCode, String useYn) {
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_institution_service (inst_code, service_code, use_yn)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        use_yn = ?
                    """,
                    instCode,
                    serviceCode,
                    useYn,
                    useYn);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_institution_service (inst_code, service_code, use_yn)
                KEY (inst_code, service_code)
                VALUES (?, ?, ?)
                """, instCode, serviceCode, useYn);
    }

    private DatabaseDialect detectDatabaseDialect() {
        DatabaseDialect detected = jdbcTemplate.execute((ConnectionCallback<DatabaseDialect>) connection -> {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName != null && productName.toLowerCase(Locale.ROOT).contains("mysql")) {
                return DatabaseDialect.MYSQL;
            }
            return DatabaseDialect.H2;
        });
        return detected == null ? DatabaseDialect.H2 : detected;
    }

    private boolean isMySql() {
        return databaseDialect == DatabaseDialect.MYSQL;
    }

    private int institutionRank(String instCode) {
        return "core".equalsIgnoreCase(instCode) ? 0 : 1;
    }

    private String normalizeInstCode(String instCode) {
        if (!StringUtils.hasText(instCode)) {
            return null;
        }
        String normalized = instCode.trim();
        if ("core".equalsIgnoreCase(normalized)) {
            return "core";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return username.trim();
    }

    private String normalizeServiceCode(String serviceCode) {
        if (!StringUtils.hasText(serviceCode)) {
            return null;
        }
        return serviceCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRole(String roleCode) {
        return ROLE_PLATFORM_ADMIN.equalsIgnoreCase(roleCode) ? ROLE_PLATFORM_ADMIN : ROLE_USER;
    }

    private String normalizeYn(String useYn) {
        return "N".equalsIgnoreCase(useYn) ? "N" : "Y";
    }

    private String normalizePath(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String normalized = value.trim();
        if (normalized.startsWith("/")) {
            return normalized;
        }
        return "/" + normalized;
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private enum DatabaseDialect {
        H2,
        MYSQL
    }
}
