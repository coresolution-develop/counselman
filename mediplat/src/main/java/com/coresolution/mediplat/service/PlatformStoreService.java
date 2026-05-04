package com.coresolution.mediplat.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import com.coresolution.mediplat.model.RoomBoardViewerAccount;

import jakarta.annotation.PostConstruct;

@Service
public class PlatformStoreService {

    private static final String USE_Y = "Y";
    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String ROLE_INSTITUTION_ADMIN = "INSTITUTION_ADMIN";
    private static final String ROLE_ROOM_BOARD_VIEWER = "ROOM_BOARD_VIEWER";
    private static final String ROLE_USER = "USER";
    private static final String SERVICE_CODE_COUNSELMAN = "COUNSELMAN";
    private static final String SERVICE_CODE_ROOM_BOARD = "ROOM_BOARD";
    private static final String SERVICE_CODE_SEMINAR_ROOM = "SEMINAR_ROOM";
    private static final String INTEGRATION_CODE_ROOMBOARD_CSM_LINK = "ROOMBOARD_CSM_LINK";
    private static final String DEFAULT_SERVICE_CODE = SERVICE_CODE_COUNSELMAN;
    private static final String VIEWER_ACCOUNT_INST_CODE = "core";
    private static final String ENV_LOCAL = "LOCAL";
    private static final String ENV_DEV = "DEV";
    private static final String ENV_PROD = "PROD";

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

    @Value("${platform.runtime-env:}")
    private String configuredRuntimeEnv;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

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

    public PlatformSessionUser authenticate(String instCodeOrName, String username, String rawPassword) {
        String normalizedInstInput = StringUtils.hasText(instCodeOrName) ? instCodeOrName.trim() : "";
        String normalizedUsername = normalizeUsername(username);
        String normalizedRawPassword = StringUtils.hasText(rawPassword) ? rawPassword.trim() : "";

        if (!StringUtils.hasText(normalizedInstInput)) {
            PlatformSessionUser roomBoardViewerSession = authenticateRoomBoardViewerAccount(normalizedUsername, rawPassword);
            if (roomBoardViewerSession != null) {
                return roomBoardViewerSession;
            }
            return null;
        }

        String resolvedInstCode = resolveLoginInstCode(normalizedInstInput);
        if (!StringUtils.hasText(resolvedInstCode)) {
            return null;
        }
        if (!StringUtils.hasText(normalizedUsername) || !StringUtils.hasText(normalizedRawPassword)) {
            return null;
        }
        PlatformInstitution institution = findStoredInstitution(resolvedInstCode);
        if (institution == null || !USE_Y.equalsIgnoreCase(institution.getUseYn())) {
            return null;
        }
        PlatformUser managedUser = findUser(resolvedInstCode, normalizedUsername);
        if (managedUser != null) {
            if (!USE_Y.equalsIgnoreCase(managedUser.getUseYn())) {
                return null;
            }
            if (ROLE_ROOM_BOARD_VIEWER.equalsIgnoreCase(managedUser.getRoleCode())) {
                return null;
            }
            if (!passwordEncoder.matches(normalizedRawPassword, managedUser.getPasswordHash())) {
                return null;
            }
            String roleCode = resolveSessionRole(resolvedInstCode, normalizedUsername, managedUser);
            String displayName = StringUtils.hasText(managedUser.getDisplayName())
                    ? managedUser.getDisplayName().trim()
                    : managedUser.getUsername();
            return new PlatformSessionUser(
                    resolvedInstCode,
                    managedUser.getUsername(),
                    displayName,
                    roleCode);
        }

        CounselManAccountService.AuthenticatedUser authenticatedUser = counselManAccountService.authenticate(
                resolvedInstCode,
                normalizedUsername,
                normalizedRawPassword);
        if (authenticatedUser == null) {
            return null;
        }
        return new PlatformSessionUser(
                resolvedInstCode,
                authenticatedUser.username(),
                authenticatedUser.displayName(),
                ROLE_USER);
    }

    private PlatformSessionUser authenticateRoomBoardViewerAccount(String username, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername) || !StringUtils.hasText(rawPassword)) {
            return null;
        }
        PlatformUser viewerUser = findRoomBoardViewerUser(normalizedUsername);
        if (viewerUser == null || !"Y".equalsIgnoreCase(viewerUser.getUseYn())) {
            return null;
        }
        if (!passwordEncoder.matches(rawPassword, viewerUser.getPasswordHash())) {
            return null;
        }
        return new PlatformSessionUser(
                VIEWER_ACCOUNT_INST_CODE,
                viewerUser.getUsername(),
                viewerUser.getDisplayName(),
                ROLE_ROOM_BOARD_VIEWER);
    }

    private String resolveLoginInstCode(String instCodeOrName) {
        if (!StringUtils.hasText(instCodeOrName)) {
            return null;
        }
        String candidate = instCodeOrName.trim();
        String normalizedCandidateCode = normalizeInstCode(candidate);

        List<PlatformInstitution> institutions = listInstitutions();
        for (PlatformInstitution institution : institutions) {
            if (institution == null || !StringUtils.hasText(institution.getInstCode())) {
                continue;
            }
            String instCode = normalizeInstCode(institution.getInstCode());
            if (StringUtils.hasText(instCode) && instCode.equalsIgnoreCase(normalizedCandidateCode)) {
                return instCode;
            }
        }
        for (PlatformInstitution institution : institutions) {
            if (institution == null) {
                continue;
            }
            String institutionName = institution.getInstName();
            if (StringUtils.hasText(institutionName) && institutionName.trim().equalsIgnoreCase(candidate)) {
                return normalizeInstCode(institution.getInstCode());
            }
        }
        return normalizedCandidateCode;
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

    private String resolveSessionRole(String instCode, String username, PlatformUser managedUser) {
        if (isPlatformAdminAccount(instCode, username, managedUser)) {
            return ROLE_PLATFORM_ADMIN;
        }
        String normalizedRole = normalizeRole(managedUser == null ? null : managedUser.getRoleCode());
        if (ROLE_INSTITUTION_ADMIN.equalsIgnoreCase(normalizedRole)) {
            return ROLE_INSTITUTION_ADMIN;
        }
        return ROLE_USER;
    }

    public List<PlatformService> listAccessibleServices(PlatformSessionUser user) {
        return listServicesForUser(user).stream()
                .filter(PlatformService::isAccessible)
                .toList();
    }

    public List<PlatformService> listServicesForUser(PlatformSessionUser user) {
        if (user == null) {
            return List.of();
        }
        if (user.isPlatformAdmin()) {
            return listAllServices();
        }
        String normalizedInstCode = normalizeInstCode(user.getInstCode());
        if (!StringUtils.hasText(normalizedInstCode)) {
            return listAllServices().stream()
                    .map(service -> withAccess(service, "N"))
                    .toList();
        }
        PlatformInstitution institution = findInstitution(user.getInstCode());
        boolean institutionEnabled = institution == null || USE_Y.equalsIgnoreCase(institution.getUseYn());
        String runtimeEnvCode = resolveRuntimeEnvCode();
        List<PlatformService> services = jdbcTemplate.query("""
                SELECT s.id, s.service_code, s.service_name,
                       COALESCE(se_runtime.base_url, s.base_url) AS base_url,
                       se_local.base_url AS base_url_local,
                       se_dev.base_url AS base_url_dev,
                       se_prod.base_url AS base_url_prod,
                       s.sso_entry_path, s.user_target, s.admin_target,
                       s.description, s.use_yn, s.display_order, COALESCE(a.use_yn, 'N') AS access_yn
                FROM mp_service s
                LEFT JOIN mp_service_endpoint se_runtime
                  ON se_runtime.service_code = s.service_code
                 AND se_runtime.env_code = ?
                LEFT JOIN mp_service_endpoint se_local
                  ON se_local.service_code = s.service_code
                 AND se_local.env_code = 'LOCAL'
                LEFT JOIN mp_service_endpoint se_dev
                  ON se_dev.service_code = s.service_code
                 AND se_dev.env_code = 'DEV'
                LEFT JOIN mp_service_endpoint se_prod
                  ON se_prod.service_code = s.service_code
                 AND se_prod.env_code = 'PROD'
                LEFT JOIN mp_institution_service a
                  ON a.service_code = s.service_code
                 AND a.inst_code = ?
                ORDER BY s.display_order ASC, s.id ASC
                """, (rs, rowNum) -> mapPlatformService(rs, rs.getString("access_yn")),
                runtimeEnvCode,
                normalizedInstCode);
        List<PlatformService> scopedServices;
        if (institutionEnabled) {
            scopedServices = appendRoomBoardServiceCard(services, normalizedInstCode);
        } else {
            scopedServices = services.stream()
                    .map(service -> withAccess(service, "N"))
                    .toList();
        }
        return applyUserServiceAccess(scopedServices, user);
    }

    public List<PlatformInstitution> listInstitutions() {
        return listStoredInstitutions().stream()
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
                SELECT id, inst_code, username, password_hash, display_name, COALESCE(dept, '') AS dept, role_code, use_yn
                FROM mp_user
                ORDER BY id ASC
                """, (rs, rowNum) -> new PlatformUser(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getString("dept"),
                        rs.getString("role_code"),
                        rs.getString("use_yn")));
    }

    public List<PlatformUser> listInstitutionAdminUsers() {
        return listUsers().stream()
                .filter(user -> user != null && ROLE_INSTITUTION_ADMIN.equalsIgnoreCase(user.getRoleCode()))
                .sorted((left, right) -> {
                    int instCompare = String.CASE_INSENSITIVE_ORDER.compare(
                            Objects.toString(left.getInstCode(), ""),
                            Objects.toString(right.getInstCode(), ""));
                    if (instCompare != 0) {
                        return instCompare;
                    }
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            Objects.toString(left.getUsername(), ""),
                            Objects.toString(right.getUsername(), ""));
                })
                .toList();
    }

    public List<PlatformUser> listInstitutionUsers(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return List.of();
        }
        return listUsers().stream()
                .filter(user -> user != null)
                .filter(user -> normalizedInstCode.equalsIgnoreCase(normalizeInstCode(user.getInstCode())))
                .filter(user -> isUserManageableRole(user.getRoleCode()))
                .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(
                        Objects.toString(left.getUsername(), ""),
                        Objects.toString(right.getUsername(), "")))
                .toList();
    }

    public List<String> listEnabledServiceCodesForUser(PlatformSessionUser user) {
        if (user == null) {
            return List.of();
        }
        if (user.isPlatformAdmin()) {
            return listAllServices().stream()
                    .filter(PlatformService::isEnabled)
                    .map(PlatformService::getServiceCode)
                    .toList();
        }
        return listServicesForUser(user).stream()
                .filter(PlatformService::isAccessible)
                .map(PlatformService::getServiceCode)
                .toList();
    }

    public List<String> listEnabledServiceCodesForUser(String instCode, String username) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return List.of();
        }
        List<String> institutionEnabledCodes = listEnabledServiceCodes(normalizedInstCode).stream()
                .map(this::normalizeServiceCode)
                .filter(StringUtils::hasText)
                .toList();
        if (institutionEnabledCodes.isEmpty()) {
            return List.of();
        }
        if (!hasUserServiceAccessConfig(normalizedInstCode, normalizedUsername)) {
            return institutionEnabledCodes;
        }
        List<String> userEnabledCodes = listConfiguredEnabledUserServiceCodes(normalizedInstCode, normalizedUsername);
        return institutionEnabledCodes.stream()
                .filter(code -> userEnabledCodes.stream().anyMatch(code::equalsIgnoreCase))
                .toList();
    }

    public List<RoomBoardViewerAccount> listRoomBoardViewerAccounts() {
        List<PlatformUser> viewerUsers = jdbcTemplate.query("""
                SELECT id, inst_code, username, password_hash, display_name, COALESCE(dept, '') AS dept, role_code, use_yn
                FROM mp_user
                WHERE role_code = ?
                ORDER BY username ASC
                """, (rs, rowNum) -> new PlatformUser(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getString("dept"),
                        rs.getString("role_code"),
                        rs.getString("use_yn")),
                ROLE_ROOM_BOARD_VIEWER);

        Map<String, String> institutionNameMap = new LinkedHashMap<>();
        for (PlatformInstitution institution : listInstitutions()) {
            if (institution == null || !StringUtils.hasText(institution.getInstCode())) {
                continue;
            }
            institutionNameMap.put(normalizeInstCode(institution.getInstCode()), institution.getInstName());
        }

        List<RoomBoardViewerAccount> result = new ArrayList<>();
        for (PlatformUser viewerUser : viewerUsers) {
            List<String> scopeInstCodes = listRoomBoardViewerScopeInstCodes(viewerUser.getUsername());
            List<String> scopeInstNames = scopeInstCodes.stream()
                    .map(code -> institutionNameMap.getOrDefault(code, code))
                    .toList();
            String scopeSummary = scopeInstNames.isEmpty() ? "-" : String.join(", ", scopeInstNames);
            result.add(new RoomBoardViewerAccount(
                    viewerUser.getUsername(),
                    viewerUser.getDisplayName(),
                    normalizeYn(viewerUser.getUseYn()),
                    scopeInstCodes,
                    scopeInstNames,
                    scopeSummary));
        }
        return result;
    }

    public RoomBoardViewerAccount findRoomBoardViewerAccount(String username) {
        PlatformUser viewerUser = findRoomBoardViewerUser(username);
        if (viewerUser == null) {
            return null;
        }
        List<String> scopeInstCodes = listRoomBoardViewerScopeInstCodes(viewerUser.getUsername());
        Map<String, String> institutionNameMap = new LinkedHashMap<>();
        for (PlatformInstitution institution : listInstitutions()) {
            if (institution == null || !StringUtils.hasText(institution.getInstCode())) {
                continue;
            }
            institutionNameMap.put(normalizeInstCode(institution.getInstCode()), institution.getInstName());
        }
        List<String> scopeInstNames = scopeInstCodes.stream()
                .map(code -> institutionNameMap.getOrDefault(code, code))
                .toList();
        String scopeSummary = scopeInstNames.isEmpty() ? "-" : String.join(", ", scopeInstNames);
        return new RoomBoardViewerAccount(
                viewerUser.getUsername(),
                viewerUser.getDisplayName(),
                normalizeYn(viewerUser.getUseYn()),
                scopeInstCodes,
                scopeInstNames,
                scopeSummary);
    }

    public List<PlatformInstitution> listRoomBoardScopeCandidateInstitutions() {
        return listInstitutions().stream()
                .filter(Objects::nonNull)
                .filter(institution -> !"core".equalsIgnoreCase(institution.getInstCode()))
                .filter(institution -> USE_Y.equalsIgnoreCase(institution.getUseYn()))
                .filter(institution -> isRoomBoardServiceEnabledForInstitution(institution.getInstCode()))
                .filter(institution -> isRoomBoardCounselLinkEnabled(institution.getInstCode()))
                .toList();
    }

    public void saveRoomBoardViewerAccount(
            String username,
            String rawPassword,
            String displayName,
            String useYn,
            List<String> scopedInstCodes) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedDisplayName = StringUtils.hasText(displayName) ? displayName.trim() : null;
        if (!StringUtils.hasText(normalizedUsername) || !normalizedUsername.matches("^[A-Za-z0-9_]{4,40}$")) {
            throw new IllegalArgumentException("아이디는 4~40자의 영문/숫자/언더스코어만 사용할 수 있습니다.");
        }
        if (!StringUtils.hasText(normalizedDisplayName)) {
            throw new IllegalArgumentException("표시 이름을 입력해 주세요.");
        }
        List<String> normalizedScopeInstCodes = normalizeScopeInstitutionCodes(scopedInstCodes);
        if (normalizedScopeInstCodes.isEmpty()) {
            throw new IllegalArgumentException("최소 1개 기관을 선택해 주세요.");
        }
        PlatformUser existingViewerUser = findRoomBoardViewerUser(normalizedUsername);
        if (isUsernameUsedByOtherPlatformUsers(normalizedUsername, existingViewerUser)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다. 다른 아이디를 사용해 주세요.");
        }

        String normalizedUseYn = normalizeYn(useYn);
        String passwordHash;
        if (existingViewerUser == null) {
            if (!StringUtils.hasText(rawPassword)) {
                throw new IllegalArgumentException("새 계정은 비밀번호를 입력해야 합니다.");
            }
            passwordHash = passwordEncoder.encode(rawPassword.trim());
        } else if (StringUtils.hasText(rawPassword)) {
            passwordHash = passwordEncoder.encode(rawPassword.trim());
        } else {
            passwordHash = existingViewerUser.getPasswordHash();
        }

        upsertUser(
                VIEWER_ACCOUNT_INST_CODE,
                normalizedUsername,
                passwordHash,
                normalizedDisplayName,
                "",
                ROLE_ROOM_BOARD_VIEWER,
                normalizedUseYn);
        replaceRoomBoardViewerScopes(normalizedUsername, normalizedScopeInstCodes);
    }

    public List<String> listRoomBoardViewerScopeInstCodes(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return List.of();
        }
        List<String> rows = jdbcTemplate.query("""
                SELECT inst_code
                FROM mp_user_scope
                WHERE LOWER(username) = LOWER(?)
                  AND use_yn = 'Y'
                ORDER BY inst_code ASC
                """, (rs, rowNum) -> normalizeInstCode(rs.getString("inst_code")), normalizedUsername);
        return rows.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public List<PlatformService> listAllServices() {
        String runtimeEnvCode = resolveRuntimeEnvCode();
        return jdbcTemplate.query("""
                SELECT s.id, s.service_code, s.service_name,
                       COALESCE(se_runtime.base_url, s.base_url) AS base_url,
                       se_local.base_url AS base_url_local,
                       se_dev.base_url AS base_url_dev,
                       se_prod.base_url AS base_url_prod,
                       s.sso_entry_path, s.user_target, s.admin_target,
                       s.description, s.use_yn, s.display_order, 'Y' AS access_yn
                FROM mp_service s
                LEFT JOIN mp_service_endpoint se_runtime
                  ON se_runtime.service_code = s.service_code
                 AND se_runtime.env_code = ?
                LEFT JOIN mp_service_endpoint se_local
                  ON se_local.service_code = s.service_code
                 AND se_local.env_code = 'LOCAL'
                LEFT JOIN mp_service_endpoint se_dev
                  ON se_dev.service_code = s.service_code
                 AND se_dev.env_code = 'DEV'
                LEFT JOIN mp_service_endpoint se_prod
                  ON se_prod.service_code = s.service_code
                 AND se_prod.env_code = 'PROD'
                ORDER BY s.display_order ASC, s.id ASC
                """, (rs, rowNum) -> mapPlatformService(rs, rs.getString("access_yn")),
                runtimeEnvCode);
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

    public List<String> listEnabledIntegrationCodes(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return List.of();
        }
        List<String> enabledCodes = jdbcTemplate.query("""
                SELECT integration_code
                FROM mp_institution_integration
                WHERE inst_code = ?
                  AND use_yn = 'Y'
                ORDER BY integration_code ASC
                """, (rs, rowNum) -> rs.getString("integration_code"), normalizedInstCode);
        if (!enabledCodes.isEmpty()) {
            return enabledCodes;
        }
        return isRoomBoardFeatureEnabled(normalizedInstCode)
                ? List.of(INTEGRATION_CODE_ROOMBOARD_CSM_LINK)
                : List.of();
    }

    public List<PlatformInstitution> listRoomBoardViewerInstitutions(PlatformSessionUser user) {
        if (user == null || !StringUtils.hasText(user.getUsername())) {
            return List.of();
        }
        String username = user.getUsername().trim();
        if (user.isRoomBoardViewer()) {
            return listRoomBoardViewerScopeInstCodes(username).stream()
                    .map(this::findInstitution)
                    .filter(Objects::nonNull)
                    .filter(institution -> USE_Y.equalsIgnoreCase(institution.getUseYn()))
                    .filter(institution -> isRoomBoardServiceEnabledForInstitution(institution.getInstCode()))
                    .filter(institution -> isRoomBoardCounselLinkEnabled(institution.getInstCode()))
                    .toList();
        }
        String normalizedInstCode = normalizeInstCode(user.getInstCode());
        if (!StringUtils.hasText(normalizedInstCode) || "core".equalsIgnoreCase(normalizedInstCode)) {
            return List.of();
        }
        PlatformInstitution loginInstitution = findInstitution(normalizedInstCode);
        if (loginInstitution == null) {
            return List.of();
        }
        if (!USE_Y.equalsIgnoreCase(loginInstitution.getUseYn())) {
            return List.of();
        }
        if (!counselManAccountService.hasAvailableUser(normalizedInstCode, username)) {
            return List.of();
        }
        if (!isRoomBoardServiceEnabledForInstitution(normalizedInstCode)) {
            return List.of();
        }
        if (!isRoomBoardCounselLinkEnabled(normalizedInstCode)) {
            return List.of();
        }
        return List.of(loginInstitution);
    }

    public boolean isRoomBoardCounselPairEnabled(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return false;
        }
        return isRoomBoardServiceEnabledForInstitution(normalizedInstCode)
                && isRoomBoardFeatureEnabled(normalizedInstCode);
    }

    public boolean isRoomBoardCounselLinkEnabled(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return false;
        }
        if (!isRoomBoardFeatureEnabled(normalizedInstCode)) {
            return false;
        }
        return isIntegrationEnabledForInstitution(normalizedInstCode, INTEGRATION_CODE_ROOMBOARD_CSM_LINK);
    }

    public String getRuntimeEnvCode() {
        return resolveRuntimeEnvCode();
    }

    public PlatformService findService(String serviceCode) {
        String runtimeEnvCode = resolveRuntimeEnvCode();
        List<PlatformService> result = jdbcTemplate.query("""
                SELECT s.id, s.service_code, s.service_name,
                       COALESCE(se_runtime.base_url, s.base_url) AS base_url,
                       se_local.base_url AS base_url_local,
                       se_dev.base_url AS base_url_dev,
                       se_prod.base_url AS base_url_prod,
                       s.sso_entry_path, s.user_target, s.admin_target,
                       s.description, s.use_yn, s.display_order, 'Y' AS access_yn
                FROM mp_service s
                LEFT JOIN mp_service_endpoint se_runtime
                  ON se_runtime.service_code = s.service_code
                 AND se_runtime.env_code = ?
                LEFT JOIN mp_service_endpoint se_local
                  ON se_local.service_code = s.service_code
                 AND se_local.env_code = 'LOCAL'
                LEFT JOIN mp_service_endpoint se_dev
                  ON se_dev.service_code = s.service_code
                 AND se_dev.env_code = 'DEV'
                LEFT JOIN mp_service_endpoint se_prod
                  ON se_prod.service_code = s.service_code
                 AND se_prod.env_code = 'PROD'
                WHERE s.service_code = ?
                LIMIT 1
                """, (rs, rowNum) -> mapPlatformService(rs, rs.getString("access_yn")),
                runtimeEnvCode,
                normalizeServiceCode(serviceCode));
        return result.isEmpty() ? null : result.get(0);
    }

    private boolean isServiceEnabledForInstitution(String instCode, String serviceCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedServiceCode = normalizeServiceCode(serviceCode);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedServiceCode)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM mp_institution_service
                WHERE inst_code = ?
                  AND service_code = ?
                  AND use_yn = 'Y'
                """, Integer.class, normalizedInstCode, normalizedServiceCode);
        return count != null && count > 0;
    }

    private boolean isRoomBoardFeatureEnabled(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return false;
        }
        return counselManAccountService.isRoomBoardEnabled(normalizedInstCode);
    }

    private boolean isRoomBoardServiceEnabledForInstitution(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return false;
        }
        PlatformService roomBoardService = findService(SERVICE_CODE_ROOM_BOARD);
        if (roomBoardService == null) {
            return true;
        }
        if (!roomBoardService.isEnabled()) {
            return false;
        }
        return isServiceEnabledForInstitution(normalizedInstCode, SERVICE_CODE_ROOM_BOARD);
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

    public boolean institutionExists(String instCode) {
        return findStoredInstitution(instCode) != null;
    }

    public void setInstitutionUseYn(String instCode, String useYn) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!org.springframework.util.StringUtils.hasText(normalizedInstCode)) return;
        jdbcTemplate.update(
                "UPDATE mp_institution SET use_yn = ? WHERE inst_code = ?",
                normalizeYn(useYn), normalizedInstCode);
    }

    public void saveUser(String instCode, String username, String rawPassword, String displayName, String roleCode, String useYn) {
        saveUser(instCode, username, rawPassword, displayName, "", roleCode, useYn);
    }

    public void saveUser(String instCode, String username, String rawPassword, String displayName, String dept, String roleCode, String useYn) {
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
        upsertUser(normalizedInstCode, normalizedUsername, hash, displayName.trim(), dept, normalizedRole, normalizedUseYn);
    }

    public void bulkSaveUsers(String instCode, List<Map<String, String>> users) {
        if (users == null || users.isEmpty()) return;
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            throw new IllegalArgumentException("기관코드가 올바르지 않습니다.");
        }
        if (ensureInstitutionRegistered(normalizedInstCode, null) == null) {
            throw new IllegalArgumentException("먼저 기관을 등록해 주세요.");
        }
        for (Map<String, String> u : users) {
            String username    = normalizeUsername(u.get("username"));
            String rawPassword = u.getOrDefault("password", "");
            String displayName = u.getOrDefault("displayName", "");
            String dept        = u.getOrDefault("dept", "");
            String roleCode    = normalizeRole(u.getOrDefault("roleCode", "USER"));
            String useYn       = normalizeYn(u.getOrDefault("useYn", "Y"));
            if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword) || !StringUtils.hasText(displayName)) {
                continue;
            }
            String hash = passwordEncoder.encode(rawPassword.trim());
            upsertUser(normalizedInstCode, username, hash, displayName.trim(), dept, roleCode, useYn);
        }
    }

    public void saveUserServiceAccess(String instCode, String username, List<String> enabledServiceCodes) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            throw new IllegalArgumentException("기관과 사용자 정보를 확인해 주세요.");
        }
        PlatformUser targetUser = findUser(normalizedInstCode, normalizedUsername);
        if (targetUser == null || !isUserManageableRole(targetUser.getRoleCode())) {
            throw new IllegalArgumentException("권한을 관리할 수 있는 사용자가 아닙니다.");
        }
        List<String> allowedServiceCodes = listEnabledServiceCodes(normalizedInstCode).stream()
                .map(this::normalizeServiceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (allowedServiceCodes.isEmpty()) {
            throw new IllegalArgumentException("해당 기관에 활성화된 서비스가 없습니다.");
        }
        List<String> normalizedEnabledCodes = new ArrayList<>();
        if (enabledServiceCodes != null) {
            enabledServiceCodes.stream()
                    .filter(Objects::nonNull)
                    .map(this::normalizeServiceCode)
                    .filter(StringUtils::hasText)
                    .filter(allowedServiceCodes::contains)
                    .distinct()
                    .forEach(normalizedEnabledCodes::add);
        }
        for (String serviceCode : allowedServiceCodes) {
            upsertUserServiceAccess(
                    normalizedInstCode,
                    normalizedUsername,
                    serviceCode,
                    normalizedEnabledCodes.contains(serviceCode) ? "Y" : "N");
        }
    }

    public void saveService(
            String serviceCode,
            String serviceName,
            String baseUrl,
            String baseUrlLocal,
            String baseUrlDev,
            String baseUrlProd,
            String ssoEntryPath,
            String userTarget,
            String adminTarget,
            String description,
            String useYn,
            Integer displayOrder) {
        String normalizedCode = normalizeServiceCode(serviceCode);
        if (!StringUtils.hasText(normalizedCode) || !StringUtils.hasText(serviceName)) {
            throw new IllegalArgumentException("서비스 코드와 이름은 필수입니다.");
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String normalizedLocalBaseUrl = normalizeBaseUrl(baseUrlLocal);
        String normalizedDevBaseUrl = normalizeBaseUrl(baseUrlDev);
        String normalizedProdBaseUrl = normalizeBaseUrl(baseUrlProd);
        validateEndpointUrl(ENV_DEV, normalizedDevBaseUrl);
        validateEndpointUrl(ENV_PROD, normalizedProdBaseUrl);

        String runtimeEnvCode = resolveRuntimeEnvCode();
        String resolvedDefaultBaseUrl = firstNonBlank(
                selectByRuntimeEnv(runtimeEnvCode, normalizedLocalBaseUrl, normalizedDevBaseUrl, normalizedProdBaseUrl),
                normalizedBaseUrl,
                normalizedLocalBaseUrl,
                normalizedDevBaseUrl,
                normalizedProdBaseUrl);
        if (!StringUtils.hasText(resolvedDefaultBaseUrl)) {
            throw new IllegalArgumentException("base URL은 최소 1개(Local/Dev/Prod 중 하나) 입력해 주세요.");
        }

        upsertService(
                normalizedCode,
                serviceName.trim(),
                resolvedDefaultBaseUrl,
                normalizePath(ssoEntryPath, "/mediplat/sso/entry"),
                normalizePath(userTarget, "/counsel/list?page=1&perPageNum=10&comment="),
                normalizePath(adminTarget, "/core/admin"),
                trimToNull(description),
                normalizeYn(useYn),
                displayOrder == null ? 0 : displayOrder);
        upsertServiceEndpoint(normalizedCode, ENV_LOCAL, normalizedLocalBaseUrl);
        upsertServiceEndpoint(normalizedCode, ENV_DEV, normalizedDevBaseUrl);
        upsertServiceEndpoint(normalizedCode, ENV_PROD, normalizedProdBaseUrl);
    }

    public void updateServiceStatus(String serviceCode, String useYn) {
        String normalizedCode = normalizeServiceCode(serviceCode);
        if (!StringUtils.hasText(normalizedCode)) {
            throw new IllegalArgumentException("등록된 서비스가 아닙니다.");
        }
        int updated = jdbcTemplate.update("""
                UPDATE mp_service
                SET use_yn = ?
                WHERE service_code = ?
                """, normalizeYn(useYn), normalizedCode);
        if (updated == 0) {
            throw new IllegalArgumentException("등록된 서비스가 아닙니다.");
        }
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
        syncDefaultIntegrationFlags(normalizedInstCode, normalizedCodes);
    }

    public void saveInstitutionIntegrationAccess(String instCode, List<String> enabledIntegrationCodes) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            throw new IllegalArgumentException("기관을 선택해 주세요.");
        }
        if (ensureInstitutionRegistered(normalizedInstCode, null) == null) {
            throw new IllegalArgumentException("등록되지 않은 기관코드입니다.");
        }
        List<String> normalizedCodes = new ArrayList<>();
        if (enabledIntegrationCodes != null) {
            enabledIntegrationCodes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(INTEGRATION_CODE_ROOMBOARD_CSM_LINK::equals)
                    .distinct()
                    .forEach(normalizedCodes::add);
        }
        if (normalizedCodes.contains(INTEGRATION_CODE_ROOMBOARD_CSM_LINK)
                && !isRoomBoardFeatureEnabled(normalizedInstCode)) {
            throw new IllegalArgumentException("ROOM_BOARD 기능이 활성화되어야 연동을 사용할 수 있습니다.");
        }
        if (normalizedCodes.contains(INTEGRATION_CODE_ROOMBOARD_CSM_LINK)
                && !isRoomBoardServiceEnabledForInstitution(normalizedInstCode)) {
            throw new IllegalArgumentException("ROOM_BOARD 서비스 권한이 활성화되어야 연동을 사용할 수 있습니다.");
        }
        upsertInstitutionIntegration(
                normalizedInstCode,
                INTEGRATION_CODE_ROOMBOARD_CSM_LINK,
                normalizedCodes.contains(INTEGRATION_CODE_ROOMBOARD_CSM_LINK) ? "Y" : "N");
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
                    dept VARCHAR(100) NOT NULL DEFAULT '',
                    role_code VARCHAR(30) NOT NULL DEFAULT 'USER',
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    UNIQUE KEY uq_mp_user_inst_username (inst_code, username)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        Integer deptColExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_user' AND COLUMN_NAME = 'dept'",
                Integer.class);
        if (deptColExists == null || deptColExists == 0) {
            jdbcTemplate.execute("ALTER TABLE mp_user ADD COLUMN dept VARCHAR(100) NOT NULL DEFAULT ''");
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_user_scope (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    inst_code VARCHAR(50) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    UNIQUE KEY uq_mp_user_scope (username, inst_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_user_service (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    service_code VARCHAR(50) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    UNIQUE KEY uq_mp_user_service (inst_code, username, service_code)
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

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_institution_integration (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    integration_code VARCHAR(80) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'N',
                    UNIQUE KEY uq_mp_institution_integration (inst_code, integration_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_service_endpoint (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    service_code VARCHAR(50) NOT NULL,
                    env_code VARCHAR(20) NOT NULL,
                    base_url VARCHAR(255) NOT NULL,
                    UNIQUE KEY uq_mp_service_endpoint (service_code, env_code)
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
                    dept VARCHAR(100) NOT NULL DEFAULT '',
                    role_code VARCHAR(30) NOT NULL DEFAULT 'USER',
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    UNIQUE (inst_code, username)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_user_scope (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    inst_code VARCHAR(50) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    UNIQUE (username, inst_code)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_user_service (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    service_code VARCHAR(50) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    UNIQUE (inst_code, username, service_code)
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

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_institution_integration (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    integration_code VARCHAR(80) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'N',
                    UNIQUE (inst_code, integration_code)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_service_endpoint (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    service_code VARCHAR(50) NOT NULL,
                    env_code VARCHAR(20) NOT NULL,
                    base_url VARCHAR(255) NOT NULL,
                    UNIQUE (service_code, env_code)
                )
                """);
    }

    private void bootstrapDefaults() {
        String runtimeEnvCode = resolveRuntimeEnvCode();
        String localBaseUrl = ENV_LOCAL.equals(runtimeEnvCode) ? bootstrapCounselmanBaseUrl : null;
        String devBaseUrl = ENV_DEV.equals(runtimeEnvCode) ? bootstrapCounselmanBaseUrl : null;
        String prodBaseUrl = ENV_PROD.equals(runtimeEnvCode) ? bootstrapCounselmanBaseUrl : null;
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
                localBaseUrl,
                devBaseUrl,
                prodBaseUrl,
                "/mediplat/sso/entry",
                "/counsel/list?page=1&perPageNum=10&comment=",
                "/core/admin",
                "기관별 상담관리 서비스",
                USE_Y,
                1);
        saveService(
                SERVICE_CODE_ROOM_BOARD,
                "병실현황판",
                bootstrapCounselmanBaseUrl,
                localBaseUrl,
                devBaseUrl,
                prodBaseUrl,
                "/mediplat/sso/entry",
                "/room-board?popup=1",
                "/room-board?popup=1",
                "기관별 병실현황판 서비스",
                USE_Y,
                2);
        saveService(
                SERVICE_CODE_SEMINAR_ROOM,
                "세미나실 예약",
                bootstrapCounselmanBaseUrl,
                localBaseUrl,
                devBaseUrl,
                prodBaseUrl,
                "/mediplat/sso/entry",
                "/seminar-room",
                "/seminar-room",
                "기관별 세미나실 예약 관리 서비스",
                USE_Y,
                3);
        saveInstitutionServiceAccess(
                bootstrapAdminInstCode,
                List.of(DEFAULT_SERVICE_CODE, SERVICE_CODE_ROOM_BOARD, SERVICE_CODE_SEMINAR_ROOM));
    }

    private PlatformUser findUser(String instCode, String username) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return null;
        }
        List<PlatformUser> users = jdbcTemplate.query("""
                SELECT id, inst_code, username, password_hash, display_name, COALESCE(dept, '') AS dept, role_code, use_yn
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
                        rs.getString("dept"),
                        rs.getString("role_code"),
                        rs.getString("use_yn")),
                normalizedInstCode,
                normalizedUsername);
        return users.isEmpty() ? null : users.get(0);
    }

    private PlatformUser findRoomBoardViewerUser(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return null;
        }
        List<PlatformUser> users = jdbcTemplate.query("""
                SELECT id, inst_code, username, password_hash, display_name, COALESCE(dept, '') AS dept, role_code, use_yn
                FROM mp_user
                WHERE role_code = ?
                  AND LOWER(username) = LOWER(?)
                LIMIT 1
                """, (rs, rowNum) -> new PlatformUser(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getString("dept"),
                        rs.getString("role_code"),
                        rs.getString("use_yn")),
                ROLE_ROOM_BOARD_VIEWER,
                normalizedUsername);
        return users.isEmpty() ? null : users.get(0);
    }

    private boolean isUsernameUsedByOtherPlatformUsers(String username, PlatformUser existingViewerUser) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return false;
        }
        List<PlatformUser> users = jdbcTemplate.query("""
                SELECT id, inst_code, username, password_hash, display_name, COALESCE(dept, '') AS dept, role_code, use_yn
                FROM mp_user
                WHERE LOWER(username) = LOWER(?)
                """, (rs, rowNum) -> new PlatformUser(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getString("dept"),
                        rs.getString("role_code"),
                        rs.getString("use_yn")),
                normalizedUsername);
        for (PlatformUser user : users) {
            if (user == null) {
                continue;
            }
            if (existingViewerUser != null && Objects.equals(existingViewerUser.getId(), user.getId())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private List<String> normalizeScopeInstitutionCodes(List<String> scopedInstCodes) {
        if (scopedInstCodes == null) {
            return List.of();
        }
        LinkedHashSet<String> allowedCodes = listRoomBoardScopeCandidateInstitutions().stream()
                .map(PlatformInstitution::getInstCode)
                .filter(StringUtils::hasText)
                .map(this::normalizeInstCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return scopedInstCodes.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeInstCode)
                .filter(StringUtils::hasText)
                .filter(code -> !"core".equalsIgnoreCase(code))
                .filter(allowedCodes::contains)
                .distinct()
                .toList();
    }

    private void replaceRoomBoardViewerScopes(String username, List<String> scopedInstCodes) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return;
        }
        jdbcTemplate.update("""
                DELETE FROM mp_user_scope
                WHERE LOWER(username) = LOWER(?)
                """, normalizedUsername);

        List<String> normalizedScopeInstCodes = normalizeScopeInstitutionCodes(scopedInstCodes);
        for (String instCode : normalizedScopeInstCodes) {
            jdbcTemplate.update("""
                    INSERT INTO mp_user_scope (username, inst_code, use_yn)
                    VALUES (?, ?, 'Y')
                    """,
                    normalizedUsername,
                    instCode);
        }
    }

    private PlatformInstitution findInstitution(String instCode) {
        return findStoredInstitution(instCode);
    }

    public String getInstName(String instCode) {
        PlatformInstitution inst = findStoredInstitution(instCode);
        return (inst != null && StringUtils.hasText(inst.getInstName())) ? inst.getInstName() : instCode;
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
        return findStoredInstitution(instCode);
    }

    private String resolveInstitutionName(String instCode, String preferredInstName) {
        if (StringUtils.hasText(preferredInstName)) {
            return preferredInstName.trim();
        }
        if (StringUtils.hasText(instCode)) {
            return instCode;
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

    private void upsertUser(String instCode, String username, String passwordHash, String displayName, String dept, String roleCode, String useYn) {
        String safeDept = dept != null ? dept.trim() : "";
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_user (inst_code, username, password_hash, display_name, dept, role_code, use_yn)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        password_hash = ?,
                        display_name = ?,
                        dept = ?,
                        role_code = ?,
                        use_yn = ?
                    """,
                    instCode,
                    username,
                    passwordHash,
                    displayName,
                    safeDept,
                    roleCode,
                    useYn,
                    passwordHash,
                    displayName,
                    safeDept,
                    roleCode,
                    useYn);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_user (inst_code, username, password_hash, display_name, dept, role_code, use_yn)
                KEY (inst_code, username)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, instCode, username, passwordHash, displayName, safeDept, roleCode, useYn);
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

    private void upsertUserServiceAccess(String instCode, String username, String serviceCode, String useYn) {
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_user_service (inst_code, username, service_code, use_yn)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        use_yn = ?
                    """,
                    instCode,
                    username,
                    serviceCode,
                    useYn,
                    useYn);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_user_service (inst_code, username, service_code, use_yn)
                KEY (inst_code, username, service_code)
                VALUES (?, ?, ?, ?)
                """, instCode, username, serviceCode, useYn);
    }

    private void upsertInstitutionIntegration(String instCode, String integrationCode, String useYn) {
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_institution_integration (inst_code, integration_code, use_yn)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        use_yn = ?
                    """,
                    instCode,
                    integrationCode,
                    useYn,
                    useYn);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_institution_integration (inst_code, integration_code, use_yn)
                KEY (inst_code, integration_code)
                VALUES (?, ?, ?)
                """, instCode, integrationCode, useYn);
    }

    private boolean isIntegrationEnabledForInstitution(String instCode, String integrationCode) {
        List<String> rows = jdbcTemplate.query("""
                SELECT use_yn
                FROM mp_institution_integration
                WHERE inst_code = ?
                  AND integration_code = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("use_yn"), instCode, integrationCode);
        if (rows.isEmpty()) {
            return true;
        }
        return USE_Y.equalsIgnoreCase(rows.get(0));
    }

    private void syncDefaultIntegrationFlags(String instCode, List<String> enabledServiceCodes) {
        boolean roomBoardServiceEnabled = enabledServiceCodes != null
                && enabledServiceCodes.stream().anyMatch(code -> SERVICE_CODE_ROOM_BOARD.equalsIgnoreCase(code));
        boolean roomBoardEnabled = isRoomBoardFeatureEnabled(instCode);
        if (!roomBoardEnabled || !roomBoardServiceEnabled) {
            upsertInstitutionIntegration(instCode, INTEGRATION_CODE_ROOMBOARD_CSM_LINK, "N");
            return;
        }
        List<String> rows = jdbcTemplate.query("""
                SELECT use_yn
                FROM mp_institution_integration
                WHERE inst_code = ?
                  AND integration_code = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("use_yn"), instCode, INTEGRATION_CODE_ROOMBOARD_CSM_LINK);
        if (rows.isEmpty()) {
            upsertInstitutionIntegration(instCode, INTEGRATION_CODE_ROOMBOARD_CSM_LINK, "Y");
        }
    }

    private void upsertServiceEndpoint(String serviceCode, String envCode, String baseUrl) {
        String normalizedEnvCode = normalizeEnvCode(envCode);
        if (!StringUtils.hasText(baseUrl)) {
            jdbcTemplate.update("""
                    DELETE FROM mp_service_endpoint
                    WHERE service_code = ?
                      AND env_code = ?
                    """, serviceCode, normalizedEnvCode);
            return;
        }
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_service_endpoint (service_code, env_code, base_url)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        base_url = ?
                    """,
                    serviceCode,
                    normalizedEnvCode,
                    baseUrl,
                    baseUrl);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_service_endpoint (service_code, env_code, base_url)
                KEY (service_code, env_code)
                VALUES (?, ?, ?)
                """, serviceCode, normalizedEnvCode, baseUrl);
    }

    private PlatformService mapPlatformService(java.sql.ResultSet rs, String accessYn) throws java.sql.SQLException {
        return new PlatformService(
                rs.getLong("id"),
                rs.getString("service_code"),
                rs.getString("service_name"),
                rs.getString("base_url"),
                rs.getString("base_url_local"),
                rs.getString("base_url_dev"),
                rs.getString("base_url_prod"),
                rs.getString("sso_entry_path"),
                rs.getString("user_target"),
                rs.getString("admin_target"),
                rs.getString("description"),
                rs.getString("use_yn"),
                rs.getInt("display_order"),
                accessYn);
    }

    private PlatformService withAccess(PlatformService service, String accessYn) {
        return new PlatformService(
                service.getId(),
                service.getServiceCode(),
                service.getServiceName(),
                service.getBaseUrl(),
                service.getBaseUrlLocal(),
                service.getBaseUrlDev(),
                service.getBaseUrlProd(),
                service.getSsoEntryPath(),
                service.getUserTarget(),
                service.getAdminTarget(),
                service.getDescription(),
                service.getUseYn(),
                service.getDisplayOrder(),
                accessYn);
    }

    private List<PlatformService> appendRoomBoardServiceCard(List<PlatformService> services, String instCode) {
        if (!StringUtils.hasText(instCode) || !isRoomBoardCounselLinkEnabled(instCode)) {
            return services;
        }
        boolean alreadyExists = services.stream()
                .anyMatch(service -> SERVICE_CODE_ROOM_BOARD.equalsIgnoreCase(service.getServiceCode()));
        if (alreadyExists) {
            return services;
        }
        List<PlatformService> merged = new ArrayList<>(services);
        int insertIndex = merged.size();
        for (int i = 0; i < merged.size(); i++) {
            PlatformService current = merged.get(i);
            if (current != null && SERVICE_CODE_COUNSELMAN.equalsIgnoreCase(current.getServiceCode())) {
                insertIndex = i + 1;
                break;
            }
        }
        merged.add(insertIndex, new PlatformService(
                null,
                SERVICE_CODE_ROOM_BOARD,
                "병실현황판",
                null,
                null,
                null,
                null,
                "/mediplat/sso/entry",
                "/room-board?popup=1",
                "/room-board?popup=1",
                "입원상담 연동 병실현황판",
                USE_Y,
                9999,
                USE_Y));
        return merged;
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

    private String resolveRuntimeEnvCode() {
        if (StringUtils.hasText(configuredRuntimeEnv)) {
            return normalizeEnvCode(configuredRuntimeEnv);
        }
        if (StringUtils.hasText(activeProfiles)) {
            for (String profile : activeProfiles.split(",")) {
                String normalizedProfile = profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT);
                if (normalizedProfile.startsWith("prod")) {
                    return ENV_PROD;
                }
                if (normalizedProfile.startsWith("dev")) {
                    return ENV_DEV;
                }
            }
        }
        return ENV_LOCAL;
    }

    private String normalizeEnvCode(String envCode) {
        if (!StringUtils.hasText(envCode)) {
            return ENV_LOCAL;
        }
        String normalized = envCode.trim().toUpperCase(Locale.ROOT);
        if (ENV_PROD.equals(normalized) || "PRODUCTION".equals(normalized)) {
            return ENV_PROD;
        }
        if (ENV_DEV.equals(normalized) || "DEVELOPMENT".equals(normalized) || "STAGE".equals(normalized)
                || "STAGING".equals(normalized)) {
            return ENV_DEV;
        }
        return ENV_LOCAL;
    }

    private String selectByRuntimeEnv(String runtimeEnvCode, String localBaseUrl, String devBaseUrl, String prodBaseUrl) {
        if (ENV_PROD.equals(runtimeEnvCode)) {
            return prodBaseUrl;
        }
        if (ENV_DEV.equals(runtimeEnvCode)) {
            return devBaseUrl;
        }
        return localBaseUrl;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void validateEndpointUrl(String envCode, String baseUrl) {
        if (!StringUtils.hasText(baseUrl) || ENV_LOCAL.equals(envCode)) {
            return;
        }
        if (isLoopbackUrl(baseUrl)) {
            throw new IllegalArgumentException(envCode + " URL에는 localhost를 사용할 수 없습니다.");
        }
    }

    private boolean isLoopbackUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "0.0.0.0".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
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
        if (ROLE_PLATFORM_ADMIN.equalsIgnoreCase(roleCode)) {
            return ROLE_PLATFORM_ADMIN;
        }
        if (ROLE_INSTITUTION_ADMIN.equalsIgnoreCase(roleCode)) {
            return ROLE_INSTITUTION_ADMIN;
        }
        if (ROLE_ROOM_BOARD_VIEWER.equalsIgnoreCase(roleCode)) {
            return ROLE_ROOM_BOARD_VIEWER;
        }
        return ROLE_USER;
    }

    private List<PlatformService> applyUserServiceAccess(List<PlatformService> services, PlatformSessionUser user) {
        if (user == null || user.isPlatformAdmin() || user.isRoomBoardViewer()) {
            return services;
        }
        String normalizedInstCode = normalizeInstCode(user.getInstCode());
        String normalizedUsername = normalizeUsername(user.getUsername());
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return services;
        }
        if (!hasUserServiceAccessConfig(normalizedInstCode, normalizedUsername)) {
            return services;
        }
        List<String> enabledCodes = listConfiguredEnabledUserServiceCodes(normalizedInstCode, normalizedUsername);
        return services.stream()
                .map(service -> {
                    if (service == null) {
                        return null;
                    }
                    boolean accessible = service.isAccessible()
                            && enabledCodes.stream().anyMatch(code -> code.equalsIgnoreCase(service.getServiceCode()));
                    return withAccess(service, accessible ? "Y" : "N");
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean hasUserServiceAccessConfig(String instCode, String username) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM mp_user_service
                WHERE inst_code = ?
                  AND LOWER(username) = LOWER(?)
                """, Integer.class, instCode, username);
        return count != null && count > 0;
    }

    private List<String> listConfiguredEnabledUserServiceCodes(String instCode, String username) {
        return jdbcTemplate.query("""
                SELECT service_code
                FROM mp_user_service
                WHERE inst_code = ?
                  AND LOWER(username) = LOWER(?)
                  AND use_yn = 'Y'
                ORDER BY service_code ASC
                """, (rs, rowNum) -> normalizeServiceCode(rs.getString("service_code")), instCode, username).stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean isUserManageableRole(String roleCode) {
        return ROLE_USER.equalsIgnoreCase(roleCode) || ROLE_INSTITUTION_ADMIN.equalsIgnoreCase(roleCode);
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

    private String normalizeBaseUrl(String value) {
        return StringUtils.hasText(value) ? trimTrailingSlash(value) : null;
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
