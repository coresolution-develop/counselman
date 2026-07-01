package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import com.coresolution.csm.serivce.CompanyLinkService;
import com.coresolution.csm.serivce.HubCustomLinkService;
import com.coresolution.csm.serivce.HubHistoryService;
import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.HubCustomLink;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubSessions;

/**
 * Phase 3 클릭 추적 검증: 로그인 시에만 이력 기록, 커스텀 링크는 본인 소유만.
 */
@ExtendWith(MockitoExtension.class)
class HubGoControllerTest {

    @Mock private CompanyLinkService companyLinkService;
    @Mock private HubCustomLinkService hubCustomLinkService;
    @Mock private HubHistoryService hubHistoryService;

    private HubGoController controller() {
        return new HubGoController(companyLinkService, hubCustomLinkService, hubHistoryService);
    }

    private MockHttpSession loggedInSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HubSessions.ATTR, new HubMemberSession(7L, "a@coresolution.kr", "홍길동", "USER"));
        return session;
    }

    @Test
    void link_loggedIn_recordsHistoryAndRedirectsToUrl() {
        when(companyLinkService.findActiveById(3L)).thenReturn(link(3L, "HARS", "https://hars.example.com"));

        String view = controller().go("link", 3L, loggedInSession());

        assertThat(view).isEqualTo("redirect:https://hars.example.com");
        verify(hubHistoryService).record(7L, "PUBLIC", 3L, null, "HARS", "https://hars.example.com");
    }

    @Test
    void link_anonymous_redirectsWithoutRecording() {
        when(companyLinkService.findActiveById(3L)).thenReturn(link(3L, "HARS", "https://hars.example.com"));

        String view = controller().go("link", 3L, new MockHttpSession());

        assertThat(view).isEqualTo("redirect:https://hars.example.com");
        verify(hubHistoryService, never()).record(anyLong(), anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void link_notFound_redirectsToHub() {
        when(companyLinkService.findActiveById(999L)).thenReturn(null);

        assertThat(controller().go("link", 999L, loggedInSession())).isEqualTo("redirect:/links");
        verifyNoInteractions(hubHistoryService);
    }

    @Test
    void custom_anonymous_redirectsToLogin() {
        String view = controller().go("custom", 5L, new MockHttpSession());

        assertThat(view).isEqualTo("redirect:/hub/login");
        verifyNoInteractions(hubCustomLinkService, hubHistoryService);
    }

    @Test
    void custom_owned_recordsAndRedirects() {
        when(hubCustomLinkService.findOwn(5L, 7L)).thenReturn(custom(5L, "내 메모장", "https://memo.example.com"));

        String view = controller().go("custom", 5L, loggedInSession());

        assertThat(view).isEqualTo("redirect:https://memo.example.com");
        verify(hubHistoryService).record(7L, "CUSTOM", null, 5L, "내 메모장", "https://memo.example.com");
    }

    @Test
    void custom_notOwned_redirectsToMe() {
        when(hubCustomLinkService.findOwn(5L, 7L)).thenReturn(null);

        assertThat(controller().go("custom", 5L, loggedInSession())).isEqualTo("redirect:/hub/me");
        verifyNoInteractions(hubHistoryService);
    }

    @Test
    void unknownType_redirectsToHub() {
        assertThat(controller().go("bogus", 1L, loggedInSession())).isEqualTo("redirect:/links");
    }

    private CompanyLink link(long id, String title, String url) {
        CompanyLink link = new CompanyLink();
        link.setId(id);
        link.setTitle(title);
        link.setUrl(url);
        return link;
    }

    private HubCustomLink custom(long id, String title, String url) {
        HubCustomLink link = new HubCustomLink();
        link.setId(id);
        link.setTitle(title);
        link.setUrl(url);
        return link;
    }
}
