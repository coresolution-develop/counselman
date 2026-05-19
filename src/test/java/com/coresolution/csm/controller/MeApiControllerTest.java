package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.util.AES128;
import com.coresolution.csm.vo.Userdata;

class MeApiControllerTest {

    @Mock private CsmAuthService cs;
    @Mock private AES128 aes;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private HttpSession session;

    private MeApiController controller;
    private Userdata user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new MeApiController(cs, aes, jdbcTemplate);
        user = new Userdata();
        user.setUs_col_01(42);
        user.setUs_col_02("alice");
        when(session.getAttribute("inst")).thenReturn("FALH");
        when(session.getAttribute("userInfo")).thenReturn(user);
    }

    @Test
    void changePassword_returnsUnauthorized_whenSessionMissing() {
        when(session.getAttribute("inst")).thenReturn(null);
        ResponseEntity<?> res = controller.changePassword(Map.of("currentPassword", "a", "newPassword", "b"), session);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changePassword_returnsBadRequest_whenFieldsBlank() {
        ResponseEntity<?> res = controller.changePassword(Map.of("currentPassword", "", "newPassword", "new"), session);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(cs, never()).updatePwd(anyString(), anyInt(), anyString());
    }

    @Test
    void changePassword_returnsBadRequest_whenNewSameAsCurrent() {
        ResponseEntity<?> res = controller.changePassword(Map.of("currentPassword", "same", "newPassword", "same"), session);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(cs, never()).updatePwd(anyString(), anyInt(), anyString());
    }

    @Test
    void changePassword_returnsUnauthorized_whenCurrentPasswordMismatch() {
        when(aes.encrypt("oldpw")).thenReturn("ENC_OLD");
        when(aes.encrypt("newpw")).thenReturn("ENC_NEW");
        when(cs.loginCount("FALH", "alice", "ENC_OLD")).thenReturn(0);

        ResponseEntity<?> res = controller.changePassword(
                Map.of("currentPassword", "oldpw", "newPassword", "newpw"), session);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(cs, never()).updatePwd(anyString(), anyInt(), anyString());
    }

    @Test
    void changePassword_updatesAndSyncs_whenCurrentMatches() {
        when(aes.encrypt("oldpw")).thenReturn("ENC_OLD");
        when(aes.encrypt("newpw")).thenReturn("ENC_NEW");
        when(cs.loginCount("FALH", "alice", "ENC_OLD")).thenReturn(1);
        when(cs.updatePwd("FALH", 42, "ENC_NEW")).thenReturn(1);

        ResponseEntity<?> res = controller.changePassword(
                Map.of("currentPassword", "oldpw", "newPassword", "newpw"), session);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isInstanceOf(Map.class);
        verify(cs, times(1)).updatePwd("FALH", 42, "ENC_NEW");
    }

    @Test
    void changePassword_returns500_whenUpdateReturnsZeroRows() {
        when(aes.encrypt("oldpw")).thenReturn("ENC_OLD");
        when(aes.encrypt("newpw")).thenReturn("ENC_NEW");
        when(cs.loginCount("FALH", "alice", "ENC_OLD")).thenReturn(1);
        when(cs.updatePwd("FALH", 42, "ENC_NEW")).thenReturn(0);

        ResponseEntity<?> res = controller.changePassword(
                Map.of("currentPassword", "oldpw", "newPassword", "newpw"), session);

        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }
}
