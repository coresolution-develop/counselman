package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.mediplat.model.PlatformInstitution;
import com.coresolution.mediplat.model.PlatformLoginAudit;
import com.coresolution.mediplat.model.PlatformSessionUser;

class PlatformStoreServiceTest {

    @Test
    void listRoomBoardViewerInstitutions_forNormalUser_returnsOnlyLoginInstitution() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of(
                new CounselManAccountService.CounselManInstitution("FALH", "기관A", "Y"),
                new CounselManAccountService.CounselManInstitution("ABCD", "기관B", "Y")));
        when(counselManAccountService.hasAvailableUser("FALH", "sharedid")).thenReturn(true);
        when(counselManAccountService.hasAvailableUser("ABCD", "sharedid")).thenReturn(true);
        when(counselManAccountService.isRoomBoardEnabled("FALH")).thenReturn(true);
        when(counselManAccountService.isRoomBoardEnabled("ABCD")).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);

        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveInstitution("ABCD", "기관B", "Y");
        storeService.saveInstitutionServiceAccess("FALH", List.of("COUNSELMAN", "ROOM_BOARD"));
        storeService.saveInstitutionServiceAccess("ABCD", List.of("COUNSELMAN", "ROOM_BOARD"));

        PlatformSessionUser user = new PlatformSessionUser("FALH", "sharedid", "Shared ID", "USER");
        List<PlatformInstitution> result = storeService.listRoomBoardViewerInstitutions(user);

        assertEquals(1, result.size());
        assertEquals("FALH", result.get(0).getInstCode());
        verify(counselManAccountService, never()).hasAvailableUser("ABCD", "sharedid");
    }

    @Test
    void listInstitutions_returnsOnlyMediplatRegisteredInstitutions() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of(
                new CounselManAccountService.CounselManInstitution("GHOST", "외부기관", "Y")));
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");

        List<PlatformInstitution> institutions = storeService.listInstitutions();

        assertTrue(institutions.stream().anyMatch(inst -> "FALH".equalsIgnoreCase(inst.getInstCode())));
        assertFalse(institutions.stream().anyMatch(inst -> "GHOST".equalsIgnoreCase(inst.getInstCode())));
    }

    @Test
    void authenticate_usesMediplatInstitutionUserAccount() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveUser("FALH", "instadmin", "Passw0rd!", "기관 관리자", "USER", "Y");

        PlatformSessionUser sessionUser = storeService.authenticate("FALH", "instadmin", "Passw0rd!");

        assertNotNull(sessionUser);
        assertEquals("FALH", sessionUser.getInstCode());
        assertEquals("instadmin", sessionUser.getUsername());
        assertEquals("USER", sessionUser.getRoleCode());
        verify(counselManAccountService, never()).authenticate(anyString(), anyString(), anyString());
    }

    @Test
    void authenticate_usesInstitutionAdminRole_whenMediplatInstitutionAdminAccount() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveUser("FALH", "instadmin", "Passw0rd!", "기관 관리자", "INSTITUTION_ADMIN", "Y");

        PlatformSessionUser sessionUser = storeService.authenticate("FALH", "instadmin", "Passw0rd!");

        assertNotNull(sessionUser);
        assertEquals("FALH", sessionUser.getInstCode());
        assertEquals("instadmin", sessionUser.getUsername());
        assertEquals("INSTITUTION_ADMIN", sessionUser.getRoleCode());
        assertTrue(sessionUser.isInstitutionAdmin());
        verify(counselManAccountService, never()).authenticate(anyString(), anyString(), anyString());
    }

    @Test
    void authenticate_fallsBackToCounselManUser_whenMediplatUserNotFound() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);
        when(counselManAccountService.authenticate("FALH", "csmuser", "Passw0rd!"))
                .thenReturn(new CounselManAccountService.AuthenticatedUser("FALH", "csmuser", "CSM User"));

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");

        PlatformSessionUser sessionUser = storeService.authenticate("FALH", "csmuser", "Passw0rd!");

        assertNotNull(sessionUser);
        assertEquals("FALH", sessionUser.getInstCode());
        assertEquals("csmuser", sessionUser.getUsername());
        assertEquals("USER", sessionUser.getRoleCode());
        verify(counselManAccountService).authenticate("FALH", "csmuser", "Passw0rd!");
    }

    @Test
    void listEnabledServiceCodesForUser_inheritsInstitutionServices_whenUserConfigMissing() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveUser("FALH", "user1", "Passw0rd!", "일반 사용자", "USER", "Y");
        storeService.saveInstitutionServiceAccess("FALH", List.of("COUNSELMAN", "ROOM_BOARD"));

        List<String> enabledCodes = storeService.listEnabledServiceCodesForUser("FALH", "user1");

        assertEquals(List.of("COUNSELMAN", "ROOM_BOARD"), enabledCodes);
    }

    @Test
    void listEnabledServiceCodesForUser_appliesUserSpecificServiceConfig_whenConfigured() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveUser("FALH", "user1", "Passw0rd!", "일반 사용자", "USER", "Y");
        storeService.saveInstitutionServiceAccess("FALH", List.of("COUNSELMAN", "ROOM_BOARD"));
        storeService.saveUserServiceAccess("FALH", "user1", List.of("ROOM_BOARD"));

        List<String> enabledCodes = storeService.listEnabledServiceCodesForUser("FALH", "user1");

        assertEquals(List.of("ROOM_BOARD"), enabledCodes);
    }

    @Test
    void saveInstitutionIntegrationAccess_allowsRoomBoardOnlyInstitution_withoutCounselManAccess() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of(
                new CounselManAccountService.CounselManInstitution("FALH", "기관A", "Y")));
        when(counselManAccountService.hasAvailableUser("FALH", "sharedid")).thenReturn(true);
        when(counselManAccountService.isRoomBoardEnabled("FALH")).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);

        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveInstitutionServiceAccess("FALH", List.of("ROOM_BOARD"));
        assertDoesNotThrow(() -> storeService.saveInstitutionIntegrationAccess(
                "FALH",
                List.of("ROOMBOARD_CSM_LINK")));

        PlatformSessionUser user = new PlatformSessionUser("FALH", "sharedid", "Shared ID", "USER");
        List<PlatformInstitution> result = storeService.listRoomBoardViewerInstitutions(user);

        assertEquals(1, result.size());
        assertEquals("FALH", result.get(0).getInstCode());
    }

    @Test
    void roomBoardCounselPairEnabled_true_whenRoomBoardServiceAndFeatureEnabled() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of(
                new CounselManAccountService.CounselManInstitution("FALH", "기관A", "Y")));
        when(counselManAccountService.isRoomBoardEnabled("FALH")).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveInstitutionServiceAccess("FALH", List.of("ROOM_BOARD"));

        assertTrue(storeService.isRoomBoardCounselPairEnabled("FALH"));
    }

    @Test
    void saveUser_persistsEmailAndPhone_andListInstitutionUsersReturnsThem() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveUser("FALH", "jdoe", "Passw0rd!", "John Doe", "원무과",
                "jdoe@example.com", "010-1234-5678", "USER", "Y");

        List<com.coresolution.mediplat.model.PlatformUser> users = storeService.listInstitutionUsers("FALH");
        assertEquals(1, users.size());
        com.coresolution.mediplat.model.PlatformUser saved = users.get(0);
        assertEquals("jdoe", saved.getUsername());
        assertEquals("jdoe@example.com", saved.getEmail());
        assertEquals("010-1234-5678", saved.getPhone());
    }

    @Test
    void saveUser_blankEmailPhone_storedAsNull() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveUser("FALH", "alice", "Passw0rd!", "Alice", "", "  ", "", "USER", "Y");

        com.coresolution.mediplat.model.PlatformUser saved = storeService.listInstitutionUsers("FALH").get(0);
        assertNull(saved.getEmail());
        assertNull(saved.getPhone());
    }

    @Test
    void bulkSaveUsers_acceptsEmailAndPhone() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");

        java.util.Map<String, String> row1 = new java.util.HashMap<>();
        row1.put("username", "bob");
        row1.put("password", "Passw0rd!");
        row1.put("displayName", "Bob");
        row1.put("dept", "간호부");
        row1.put("email", "bob@example.com");
        row1.put("phone", "010-9999-0000");
        java.util.Map<String, String> row2 = new java.util.HashMap<>();
        row2.put("username", "carol");
        row2.put("password", "Passw0rd!");
        row2.put("displayName", "Carol");
        storeService.bulkSaveUsers("FALH", List.of(row1, row2));

        List<com.coresolution.mediplat.model.PlatformUser> users = storeService.listInstitutionUsers("FALH");
        assertEquals(2, users.size());
        com.coresolution.mediplat.model.PlatformUser bob = users.stream().filter(u -> "bob".equals(u.getUsername())).findFirst().orElseThrow();
        assertEquals("bob@example.com", bob.getEmail());
        assertEquals("010-9999-0000", bob.getPhone());
        com.coresolution.mediplat.model.PlatformUser carol = users.stream().filter(u -> "carol".equals(u.getUsername())).findFirst().orElseThrow();
        assertNull(carol.getEmail());
        assertNull(carol.getPhone());
    }

    @Test
    void saveUser_updatesExistingRowEmailAndPhone() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveUser("FALH", "jdoe", "Passw0rd!", "John", "", "old@example.com", "010-0000-0000", "USER", "Y");
        storeService.saveUser("FALH", "jdoe", "Passw0rd!", "John", "", "new@example.com", "010-1111-2222", "USER", "Y");

        com.coresolution.mediplat.model.PlatformUser saved = storeService.listInstitutionUsers("FALH").get(0);
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("010-1111-2222", saved.getPhone());
    }

    @Test
    void recordLogin_insertsAuditRow_andRecordLogoutFillsSessionSeconds() throws InterruptedException {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");

        PlatformSessionUser sessionUser = new PlatformSessionUser("FALH", "jdoe", "John Doe", "USER");
        Long auditId = storeService.recordLogin(sessionUser);
        assertNotNull(auditId);

        List<PlatformLoginAudit> auditsBeforeLogout = storeService.listLoginAudits("FALH", null, null, null, 50);
        assertEquals(1, auditsBeforeLogout.size());
        PlatformLoginAudit pending = auditsBeforeLogout.get(0);
        assertEquals("jdoe", pending.getUsername());
        assertEquals("기관A", pending.getInstName());
        assertNotNull(pending.getLoginAt());
        assertNull(pending.getLogoutAt());
        assertNull(pending.getSessionSeconds());

        Thread.sleep(1100);
        storeService.recordLogout(auditId);

        List<PlatformLoginAudit> auditsAfter = storeService.listLoginAudits("FALH", null, null, null, 50);
        assertEquals(1, auditsAfter.size());
        PlatformLoginAudit finished = auditsAfter.get(0);
        assertNotNull(finished.getLogoutAt());
        assertNotNull(finished.getSessionSeconds());
        assertTrue(finished.getSessionSeconds() >= 1L,
                () -> "expected session seconds >= 1 but was " + finished.getSessionSeconds());
    }

    @Test
    void listLoginAudits_filtersByInstitution() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveInstitution("ABCD", "기관B", "Y");

        storeService.recordLogin(new PlatformSessionUser("FALH", "alice", "Alice", "USER"));
        storeService.recordLogin(new PlatformSessionUser("ABCD", "bob", "Bob", "USER"));

        List<PlatformLoginAudit> falhAudits = storeService.listLoginAudits("FALH", null, null, null, 50);
        assertEquals(1, falhAudits.size());
        assertEquals("alice", falhAudits.get(0).getUsername());

        List<PlatformLoginAudit> allAudits = storeService.listLoginAudits(null, null, null, null, 50);
        assertEquals(2, allAudits.size());
    }

    @Test
    void recordLogout_isIdempotent() {
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());
        when(counselManAccountService.isRoomBoardEnabled(anyString())).thenReturn(true);

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");

        Long auditId = storeService.recordLogin(new PlatformSessionUser("FALH", "jdoe", "John Doe", "USER"));
        storeService.recordLogout(auditId);

        PlatformLoginAudit first = storeService.listLoginAudits("FALH", null, null, null, 50).get(0);
        java.time.LocalDateTime firstLogoutAt = first.getLogoutAt();
        Long firstSeconds = first.getSessionSeconds();
        assertNotNull(firstLogoutAt);

        storeService.recordLogout(auditId);

        PlatformLoginAudit second = storeService.listLoginAudits("FALH", null, null, null, 50).get(0);
        assertEquals(firstLogoutAt, second.getLogoutAt());
        assertEquals(firstSeconds, second.getSessionSeconds());
    }

    private PlatformStoreService newInitializedStoreService(CounselManAccountService counselManAccountService) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:platform-store-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        PlatformStoreService storeService = new PlatformStoreService(jdbcTemplate, counselManAccountService);
        ReflectionTestUtils.setField(storeService, "bootstrapAdminInstCode", "core");
        ReflectionTestUtils.setField(storeService, "bootstrapAdminInstName", "MediPlat Platform");
        ReflectionTestUtils.setField(storeService, "bootstrapAdminUsername", "platformadmin");
        ReflectionTestUtils.setField(storeService, "bootstrapAdminPassword", "ChangeMe123!");
        ReflectionTestUtils.setField(storeService, "bootstrapAdminName", "Platform Admin");
        ReflectionTestUtils.setField(storeService, "bootstrapCounselmanBaseUrl", "http://localhost:8081/csm");
        ReflectionTestUtils.setField(storeService, "bootstrapCancerTreatmentBaseUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(storeService, "configuredRuntimeEnv", "LOCAL");
        ReflectionTestUtils.setField(storeService, "activeProfiles", "local");
        storeService.initialize();
        return storeService;
    }
}
