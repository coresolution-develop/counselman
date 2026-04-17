package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        ReflectionTestUtils.setField(storeService, "configuredRuntimeEnv", "LOCAL");
        ReflectionTestUtils.setField(storeService, "activeProfiles", "local");
        storeService.initialize();
        return storeService;
    }
}
