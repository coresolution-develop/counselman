package com.coresolution.mediplat.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.coresolution.mediplat.model.PlatformInstitution;
import com.coresolution.mediplat.model.PlatformService;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.coresolution.mediplat.model.PlatformUser;
import com.coresolution.mediplat.service.CounselManSsoLinkService;
import com.coresolution.mediplat.service.PlatformStoreService;
import com.coresolution.mediplat.service.SeminarRoomService;

@ExtendWith(MockitoExtension.class)
class MediplatControllerTest {

    private static final String SESSION_USER = "mediplatUser";

    @Mock
    private PlatformStoreService storeService;

    @Mock
    private CounselManSsoLinkService counselManSsoLinkService;

    @Mock
    private SeminarRoomService seminarRoomService;

    private MediplatController controller;

    @BeforeEach
    void setUp() {
        controller = new MediplatController(storeService, counselManSsoLinkService, seminarRoomService);
    }

    @Test
    void launchService_roomBoard_redirectsServices_whenRoomBoardPermissionMissing() {
        MockHttpSession session = new MockHttpSession();
        PlatformSessionUser user = new PlatformSessionUser("FALH", "user1", "User One", "USER");
        session.setAttribute(SESSION_USER, user);
        when(storeService.listEnabledServiceCodesForUser(user)).thenReturn(List.of("COUNSELMAN"));

        String view = controller.launchService("room_board", session);

        assertEquals("redirect:/services", view);
        verify(storeService, never()).findService(any());
        verify(counselManSsoLinkService, never()).createLaunchUrl(any(), any(), any(), any());
    }

    @Test
    void launchService_roomBoard_redirectsLaunchUrl_whenIntegrationEnabled() {
        MockHttpSession session = new MockHttpSession();
        PlatformSessionUser user = new PlatformSessionUser("FALH", "user1", "User One", "USER");
        session.setAttribute(SESSION_USER, user);

        PlatformService counselMan = new PlatformService(
                1L,
                "COUNSELMAN",
                "CounselMan",
                "http://localhost:8081/csm",
                null,
                null,
                null,
                "/mediplat/sso/entry",
                "/counsel/list?page=1&perPageNum=10",
                "/admin/main",
                "",
                "Y",
                1,
                "Y");

        when(storeService.isRoomBoardCounselLinkEnabled("FALH")).thenReturn(true);
        when(storeService.listEnabledServiceCodesForUser(user)).thenReturn(List.of("ROOM_BOARD"));
        when(storeService.findService("ROOM_BOARD")).thenReturn(null);
        when(storeService.findService("COUNSELMAN")).thenReturn(counselMan);
        when(counselManSsoLinkService.createLaunchUrl(counselMan, user, "FALH", "/room-board?popup=1"))
                .thenReturn("http://localhost:8081/csm/mediplat/sso/entry?token=abc");

        String view = controller.launchService("ROOM_BOARD", session);

        assertEquals("redirect:http://localhost:8081/csm/mediplat/sso/entry?token=abc", view);
        verify(counselManSsoLinkService).createLaunchUrl(counselMan, user, "FALH", "/room-board?popup=1");
    }

    @Test
    void launchService_roomBoard_redirectsLaunchUrl_whenCounselManServiceDisabledAndRoomBoardServiceMissing() {
        MockHttpSession session = new MockHttpSession();
        PlatformSessionUser user = new PlatformSessionUser("FALH", "user1", "User One", "USER");
        session.setAttribute(SESSION_USER, user);
        PlatformService disabledService = new PlatformService(
                1L,
                "COUNSELMAN",
                "CounselMan",
                "http://localhost:8081/csm",
                null,
                null,
                null,
                "/mediplat/sso/entry",
                "/counsel/list?page=1&perPageNum=10",
                "/admin/main",
                "",
                "N",
                1,
                "Y");

        when(storeService.isRoomBoardCounselLinkEnabled("FALH")).thenReturn(true);
        when(storeService.listEnabledServiceCodesForUser(user)).thenReturn(List.of("ROOM_BOARD"));
        when(storeService.findService("ROOM_BOARD")).thenReturn(null);
        when(storeService.findService("COUNSELMAN")).thenReturn(disabledService);

        String view = controller.launchService("room_board", session);

        assertEquals("redirect:/services", view);
        verify(counselManSsoLinkService, never()).createLaunchUrl(any(), any(), any(), any());
    }

    @Test
    void launchService_roomBoard_usesRoomBoardService_whenConfigured() {
        MockHttpSession session = new MockHttpSession();
        PlatformSessionUser user = new PlatformSessionUser("FALH", "user1", "User One", "USER");
        session.setAttribute(SESSION_USER, user);
        PlatformService roomBoardService = new PlatformService(
                2L,
                "ROOM_BOARD",
                "Room Board",
                "http://room-board-host:8081/csm",
                null,
                null,
                null,
                "/mediplat/sso/entry",
                "/room-board?popup=1",
                "/room-board?popup=1",
                "",
                "Y",
                2,
                "Y");

        when(storeService.findService("ROOM_BOARD")).thenReturn(roomBoardService);
        when(storeService.listEnabledServiceCodesForUser(user)).thenReturn(List.of("ROOM_BOARD"));
        when(counselManSsoLinkService.createLaunchUrl(roomBoardService, user, "FALH", "/room-board?popup=1"))
                .thenReturn("http://room-board-host:8081/csm/mediplat/sso/entry?token=rb");

        String view = controller.launchService("room_board", session);

        assertEquals("redirect:http://room-board-host:8081/csm/mediplat/sso/entry?token=rb", view);
        verify(storeService, never()).findService("COUNSELMAN");
        verify(counselManSsoLinkService).createLaunchUrl(roomBoardService, user, "FALH", "/room-board?popup=1");
    }

    @Test
    void launchService_seminarRoom_redirectsLocalPage_whenAccessible() {
        MockHttpSession session = new MockHttpSession();
        PlatformSessionUser user = new PlatformSessionUser("FALH", "user1", "User One", "USER");
        session.setAttribute(SESSION_USER, user);
        PlatformService seminarService = new PlatformService(
                3L,
                "SEMINAR_ROOM",
                "세미나실 예약",
                "http://localhost:8081/csm",
                null,
                null,
                null,
                "/mediplat/sso/entry",
                "/seminar-room",
                "/seminar-room",
                "",
                "Y",
                3,
                "Y");

        when(storeService.findService("SEMINAR_ROOM")).thenReturn(seminarService);
        when(storeService.listEnabledServiceCodesForUser(user)).thenReturn(List.of("SEMINAR_ROOM"));

        String view = controller.launchService("seminar_room", session);

        assertEquals("redirect:/seminar-room", view);
        verify(counselManSsoLinkService, never()).createLaunchUrl(any(), any());
    }

    @Test
    void adminPage_allowsInstitutionAdmin_andScopesToOwnInstitution() {
        MockHttpSession session = new MockHttpSession();
        PlatformSessionUser user = new PlatformSessionUser("FALH", "instadmin", "기관관리자", "INSTITUTION_ADMIN");
        session.setAttribute(SESSION_USER, user);

        ExtendedModelMap model = new ExtendedModelMap();
        PlatformService service = new PlatformService(
                1L,
                "COUNSELMAN",
                "CounselMan",
                "http://localhost:8081/csm",
                null,
                null,
                null,
                "/mediplat/sso/entry",
                "/counsel/list?page=1&perPageNum=10",
                "/admin/main",
                "",
                "Y",
                1,
                "Y");
        when(storeService.listInstitutions()).thenReturn(List.of(
                new PlatformInstitution(1L, "FALH", "기관A", "Y"),
                new PlatformInstitution(2L, "ABCD", "기관B", "Y")));
        when(storeService.listEnabledServiceCodes("FALH")).thenReturn(List.of("COUNSELMAN"));
        when(storeService.listAllServices()).thenReturn(List.of(service));
        when(storeService.getRuntimeEnvCode()).thenReturn("LOCAL");
        when(storeService.listEnabledIntegrationCodes("FALH")).thenReturn(List.of());
        when(storeService.isRoomBoardCounselPairEnabled("FALH")).thenReturn(false);
        when(storeService.listInstitutionUsers("FALH")).thenReturn(List.of(
                new PlatformUser(1L, "FALH", "user1", "hash", "사용자", "USER", "Y")));
        when(storeService.listEnabledServiceCodesForUser("FALH", "user1")).thenReturn(List.of("COUNSELMAN"));

        String view = controller.adminPage("ABCD", null, "user1", null, null, model, session);

        assertEquals("admin", view);
        assertEquals("FALH", model.getAttribute("selectedInstCode"));
        @SuppressWarnings("unchecked")
        List<PlatformInstitution> institutions = (List<PlatformInstitution>) model.getAttribute("institutions");
        assertNotNull(institutions);
        assertEquals(1, institutions.size());
        assertEquals("FALH", institutions.get(0).getInstCode());
        verify(storeService, never()).listInstitutionUsers(eq("ABCD"));
        verify(storeService).listInstitutionUsers(eq("FALH"));
    }

    @Test
    void saveUserAccess_scopesInstitutionAdminToOwnInstitution() {
        MockHttpSession session = new MockHttpSession();
        PlatformSessionUser user = new PlatformSessionUser("FALH", "instadmin", "기관관리자", "INSTITUTION_ADMIN");
        session.setAttribute(SESSION_USER, user);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.saveUserAccess("ABCD", "user1", List.of("COUNSELMAN"), session, redirectAttributes);

        assertEquals("redirect:/admin", view);
        verify(storeService).saveUserServiceAccess("FALH", "user1", List.of("COUNSELMAN"));
    }

    @Test
    void markSeminarNotificationsRead_marksRead_whenManagerHasSeminarServiceAccess() {
        MockHttpSession session = new MockHttpSession();
        PlatformSessionUser user = new PlatformSessionUser("FALH", "manager1", "Manager", "USER");
        session.setAttribute(SESSION_USER, user);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        when(storeService.listEnabledServiceCodesForUser(user)).thenReturn(List.of("SEMINAR_ROOM"));
        when(seminarRoomService.isSeminarManager("FALH", "manager1")).thenReturn(true);

        String view = controller.markSeminarNotificationsRead("FALH", session, redirectAttributes);

        assertEquals("redirect:/seminar-room", view);
        verify(seminarRoomService).markNotificationsRead("FALH", "manager1");
    }
}
