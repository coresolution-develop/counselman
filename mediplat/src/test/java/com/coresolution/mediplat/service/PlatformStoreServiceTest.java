package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:platform-store-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        CounselManAccountService counselManAccountService = org.mockito.Mockito.mock(CounselManAccountService.class);

        when(counselManAccountService.listInstitutions()).thenReturn(List.of(
                new CounselManAccountService.CounselManInstitution("FALH", "기관A", "Y"),
                new CounselManAccountService.CounselManInstitution("ABCD", "기관B", "Y")));
        when(counselManAccountService.hasAvailableUser("FALH", "sharedid")).thenReturn(true);
        when(counselManAccountService.hasAvailableUser("ABCD", "sharedid")).thenReturn(true);
        when(counselManAccountService.isRoomBoardEnabled("FALH")).thenReturn(true);
        when(counselManAccountService.isRoomBoardEnabled("ABCD")).thenReturn(true);

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

        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveInstitution("ABCD", "기관B", "Y");
        storeService.saveInstitutionServiceAccess("FALH", List.of("COUNSELMAN"));
        storeService.saveInstitutionServiceAccess("ABCD", List.of("COUNSELMAN"));

        PlatformSessionUser user = new PlatformSessionUser("FALH", "sharedid", "Shared ID", "USER");
        List<PlatformInstitution> result = storeService.listRoomBoardViewerInstitutions(user);

        assertEquals(1, result.size());
        assertEquals("FALH", result.get(0).getInstCode());
        verify(counselManAccountService, never()).hasAvailableUser("ABCD", "sharedid");
        verify(counselManAccountService, never()).isRoomBoardEnabled("ABCD");
    }
}
