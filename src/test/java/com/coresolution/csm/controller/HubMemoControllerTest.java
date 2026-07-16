package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import com.coresolution.csm.serivce.HubMemoService;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubSessions;

/**
 * 가드레일 ①-a 검증: 인터셉터와 별개로 컨트롤러 진입부에서도 세션을 직접 가드하고,
 * member_id는 세션에서만 취한다(요청 본문 신뢰하지 않음).
 */
@ExtendWith(MockitoExtension.class)
class HubMemoControllerTest {

    @Mock
    private HubMemoService hubMemoService;

    @Test
    void save_anonymousSession_returns401_andNeverTouchesService() {
        HubMemoController controller = new HubMemoController(hubMemoService);
        MockHttpSession session = new MockHttpSession();

        ResponseEntity<Map<String, Object>> response = controller.save("메모", session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(hubMemoService, never()).save(anyLong(), anyString());
    }

    @Test
    void save_loggedIn_usesSessionMemberId() {
        HubMemoController controller = new HubMemoController(hubMemoService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HubSessions.ATTR, new HubMemberSession(7L, "a@coresolution.kr", "홍길동", "USER"));

        ResponseEntity<Map<String, Object>> response = controller.save("배포 체크", session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("ok", true);
        verify(hubMemoService).save(7L, "배포 체크"); // 세션 id(7) 사용
    }
}
