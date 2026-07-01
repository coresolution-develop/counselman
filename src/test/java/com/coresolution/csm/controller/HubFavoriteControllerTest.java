package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import com.coresolution.csm.serivce.HubFavoriteService;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubSessions;

/**
 * 가드레일 ①-a 검증: 인터셉터와 별개로 컨트롤러 진입부에서도 세션을 직접 가드하고,
 * member_id는 세션에서만 취한다(요청 본문 신뢰하지 않음).
 */
@ExtendWith(MockitoExtension.class)
class HubFavoriteControllerTest {

    @Mock
    private HubFavoriteService hubFavoriteService;

    @Test
    void toggle_anonymousSession_returns401_andNeverTouchesService() {
        HubFavoriteController controller = new HubFavoriteController(hubFavoriteService);
        MockHttpSession session = new MockHttpSession();

        ResponseEntity<Map<String, Object>> response = controller.toggle(3L, session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(hubFavoriteService, never()).toggle(anyLong(), anyLong());
    }

    @Test
    void toggle_loggedIn_usesSessionMemberId_returnsFavoritedFlag() {
        HubFavoriteController controller = new HubFavoriteController(hubFavoriteService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HubSessions.ATTR, new HubMemberSession(7L, "a@coresolution.kr", "홍길동", "USER"));
        when(hubFavoriteService.toggle(7L, 3L)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.toggle(3L, session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("favorited", true);
        verify(hubFavoriteService).toggle(7L, 3L); // 세션 id(7) 사용
    }

    @Test
    void toggle_invalidLink_returns400() {
        HubFavoriteController controller = new HubFavoriteController(hubFavoriteService);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HubSessions.ATTR, new HubMemberSession(7L, "a@coresolution.kr", "홍길동", "USER"));
        when(hubFavoriteService.toggle(7L, 99L))
                .thenThrow(new IllegalArgumentException("존재하지 않거나 사용할 수 없는 링크입니다."));

        ResponseEntity<Map<String, Object>> response = controller.toggle(99L, session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }
}
