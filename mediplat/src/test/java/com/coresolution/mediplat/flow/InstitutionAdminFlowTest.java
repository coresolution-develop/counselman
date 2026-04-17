package com.coresolution.mediplat.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.coresolution.mediplat.controller.MediplatController;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.coresolution.mediplat.model.PlatformUser;
import com.coresolution.mediplat.service.CounselManAccountService;
import com.coresolution.mediplat.service.CounselManSsoLinkService;
import com.coresolution.mediplat.service.PlatformStoreService;
import com.coresolution.mediplat.service.SeminarRoomService;

class InstitutionAdminFlowTest {

    private static final String SESSION_USER = "mediplatUser";

    @Test
    void institutionAdminFlow_addUserManageAccessAndLaunchService() {
        CounselManAccountService counselManAccountService = mock(CounselManAccountService.class);
        when(counselManAccountService.isRoomBoardEnabled(any())).thenReturn(true);
        when(counselManAccountService.listInstitutions()).thenReturn(List.of());

        PlatformStoreService storeService = newInitializedStoreService(counselManAccountService);
        storeService.saveInstitution("FALH", "기관A", "Y");
        storeService.saveInstitutionServiceAccess("FALH", List.of("COUNSELMAN", "ROOM_BOARD"));
        storeService.saveUser("FALH", "instadmin", "AdminPass1!", "기관 관리자", "INSTITUTION_ADMIN", "Y");

        CounselManSsoLinkService counselManSsoLinkService = mock(CounselManSsoLinkService.class);
        when(counselManSsoLinkService.createLaunchUrl(any(), any())).thenReturn("http://launch-host/csm/entry?token=ok");
        SeminarRoomService seminarRoomService = mock(SeminarRoomService.class);

        MediplatController controller = new MediplatController(storeService, counselManSsoLinkService, seminarRoomService);

        MockHttpSession adminSession = new MockHttpSession();
        String loginView = controller.login(
                "FALH",
                "instadmin",
                "AdminPass1!",
                adminSession,
                new RedirectAttributesModelMap());
        assertEquals("redirect:/services", loginView);

        PlatformSessionUser adminUser = (PlatformSessionUser) adminSession.getAttribute(SESSION_USER);
        assertNotNull(adminUser);
        assertTrue(adminUser.isInstitutionAdmin());

        RedirectAttributesModelMap addUserRedirect = new RedirectAttributesModelMap();
        String addUserView = controller.saveInstitutionAdminUser(
                "ABCD",
                "staff1",
                "UserPass1!",
                "일반 사용자",
                "INSTITUTION_ADMIN",
                "Y",
                adminSession,
                addUserRedirect);
        assertEquals("redirect:/admin", addUserView);

        PlatformUser createdUser = storeService.listInstitutionUsers("FALH").stream()
                .filter(user -> "staff1".equalsIgnoreCase(user.getUsername()))
                .findFirst()
                .orElse(null);
        assertNotNull(createdUser);
        assertEquals("FALH", createdUser.getInstCode());
        assertEquals("USER", createdUser.getRoleCode());

        PlatformSessionUser deniedDifferentInstitution = storeService.authenticate("ABCD", "staff1", "UserPass1!");
        assertNull(deniedDifferentInstitution);

        RedirectAttributesModelMap saveAccessRedirect = new RedirectAttributesModelMap();
        String saveAccessView = controller.saveUserAccess(
                "ABCD",
                "staff1",
                List.of("COUNSELMAN"),
                adminSession,
                saveAccessRedirect);
        assertEquals("redirect:/admin", saveAccessView);

        PlatformSessionUser normalUser = storeService.authenticate("FALH", "staff1", "UserPass1!");
        assertNotNull(normalUser);
        assertEquals("USER", normalUser.getRoleCode());

        MockHttpSession userSession = new MockHttpSession();
        userSession.setAttribute(SESSION_USER, normalUser);

        String launchCounselManView = controller.launchService("COUNSELMAN", userSession);
        assertEquals("redirect:http://launch-host/csm/entry?token=ok", launchCounselManView);
        verify(counselManSsoLinkService).createLaunchUrl(any(), eq(normalUser));

        String launchRoomBoardView = controller.launchService("ROOM_BOARD", userSession);
        assertEquals("redirect:/services", launchRoomBoardView);
        verify(counselManSsoLinkService, never()).createLaunchUrl(any(), eq(normalUser), any(), any());
    }

    private PlatformStoreService newInitializedStoreService(CounselManAccountService counselManAccountService) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:institution-admin-flow-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");

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
