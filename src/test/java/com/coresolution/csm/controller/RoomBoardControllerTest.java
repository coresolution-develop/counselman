package com.coresolution.csm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.serivce.MediplatSsoService;
import com.coresolution.csm.serivce.ModuleFeatureService;
import com.coresolution.csm.serivce.RoomBoardService;
import com.coresolution.csm.serivce.SmsService;
import com.coresolution.csm.vo.RoomBoardView;
import com.coresolution.csm.vo.Userdata;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RoomBoardControllerTest {

    @Mock
    private RoomBoardService roomBoardService;

    @Mock
    private ModuleFeatureService moduleFeatureService;

    @Mock
    private CsmAuthService csmAuthService;

    @Mock
    private MediplatSsoService mediplatSsoService;

    @Mock
    private SmsService smsService;

    private RoomBoardController controller;

    @BeforeEach
    void setUp() {
        controller = new RoomBoardController(
                roomBoardService,
                moduleFeatureService,
                csmAuthService,
                mediplatSsoService,
                smsService,
                new ObjectMapper());
    }

    @Test
    void roomBoard_regularEntry_allowsAccessWithoutIntegrationToken() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("inst", "FALH");
        session.setAttribute("userInfo", managerUser("manager"));
        Model model = new ExtendedModelMap();

        when(moduleFeatureService.isEnabled("FALH", ModuleFeatureService.FEATURE_ROOM_BOARD)).thenReturn(true);
        stubCommonData("FALH");

        String view = controller.roomBoard(
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                model,
                session);

        assertEquals("design/ward-status", view);
        assertEquals(Boolean.FALSE, model.getAttribute("popupMode"));
        verify(mediplatSsoService, never()).validateRoomBoardViewer(any(), any(), anyLong(), any());
    }

    @Test
    void roomBoard_popupToken_throwsForbidden_whenIntegrationDisabled() {
        Model model = new ExtendedModelMap();

        when(csmAuthService.resolveInst("FALH")).thenReturn("FALH");
        when(csmAuthService.isInstitutionAvailable("FALH")).thenReturn(true);
        when(csmAuthService.isRoomBoardCounselLinkEnabled("FALH")).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> controller.roomBoard(
                null,
                1,
                null,
                null,
                null,
                "FALH",
                "viewer1",
                1893456000L,
                "sig",
                model,
                new MockHttpSession()));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals("현재 기관은 병실현황판-입원상담 연동이 비활성화되었습니다.", exception.getReason());
    }

    @Test
    void roomBoard_popupToken_allowsAccess_whenIntegrationEnabled() {
        Model model = new ExtendedModelMap();
        Userdata viewer = managerUser("viewer1");

        when(csmAuthService.resolveInst("FALH")).thenReturn("FALH");
        when(csmAuthService.isInstitutionAvailable("FALH")).thenReturn(true);
        when(csmAuthService.isRoomBoardCounselLinkEnabled("FALH")).thenReturn(true);
        when(csmAuthService.loadUserInfo("FALH", "viewer1")).thenReturn(viewer);
        when(csmAuthService.isUserAvailable(viewer)).thenReturn(true);
        when(moduleFeatureService.isEnabled("FALH", ModuleFeatureService.FEATURE_ROOM_BOARD)).thenReturn(true);
        stubCommonData("FALH");

        String view = controller.roomBoard(
                null,
                1,
                null,
                null,
                null,
                "FALH",
                "viewer1",
                1893456000L,
                "sig",
                model,
                new MockHttpSession());

        assertEquals("design/ward-status", view);
        assertEquals(Boolean.TRUE, model.getAttribute("popupMode"));
        verify(mediplatSsoService).validateRoomBoardViewer("FALH", "viewer1", 1893456000L, "sig");
    }

    private Userdata managerUser(String userId) {
        Userdata user = new Userdata();
        user.setUs_col_02(userId);
        user.setUs_col_08(1);
        user.setUs_col_09(1);
        user.setUs_col_07("y");
        return user;
    }

    private void stubCommonData(String inst) {
        when(roomBoardService.getBoard(eq(inst), any())).thenReturn(new RoomBoardView());
        when(csmAuthService.userSelect(any(Userdata.class))).thenReturn(List.of());
        when(csmAuthService.selectPhone(any())).thenReturn(List.of());
        when(csmAuthService.getCategoryData(inst)).thenReturn(Map.of(
                "categoryData", Collections.emptyList(),
                "fieldTypeMapping", Collections.emptyMap(),
                "fieldOptionsMapping", Collections.emptyMap()));
    }
}
