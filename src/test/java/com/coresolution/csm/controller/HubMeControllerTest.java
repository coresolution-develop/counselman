package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coresolution.csm.serivce.HubCustomLinkService;
import com.coresolution.csm.serivce.HubMemberService;
import com.coresolution.csm.serivce.HubRememberService;

/**
 * 커스텀 링크 목록이 허브(/links)로 흡수된 뒤의 /hub/me 동작 검증.
 */
@ExtendWith(MockitoExtension.class)
class HubMeControllerTest {

    @Mock
    private HubCustomLinkService hubCustomLinkService;
    @Mock
    private HubMemberService hubMemberService;
    @Mock
    private HubRememberService hubRememberService;

    private HubMeController controller() {
        return new HubMeController(hubCustomLinkService, hubMemberService, hubRememberService);
    }

    /** 예전 북마크가 깨지지 않도록 리다이렉트만 남긴다(화면은 사라졌다). */
    @Test
    void me_redirectsToHub_withoutLoadingCustomLinks() {
        assertThat(controller().me()).isEqualTo("redirect:/links");
        verifyNoInteractions(hubCustomLinkService);
    }
}
