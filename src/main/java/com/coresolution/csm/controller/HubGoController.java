package com.coresolution.csm.controller;

import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.coresolution.csm.serivce.CompanyLinkService;
import com.coresolution.csm.serivce.HubCustomLinkService;
import com.coresolution.csm.serivce.HubHistoryService;
import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.HubCustomLink;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubSessions;

import jakarta.servlet.http.HttpSession;

/**
 * 클릭 추적 경유 리다이렉트. 로그인 상태에서만 최근 사용 이력을 남긴 뒤 실제 URL로 302.
 * 리다이렉트 대상은 운영자 큐레이션 공용 링크 또는 본인 소유 커스텀 링크뿐이라 오픈 리다이렉트가 아니다.
 */
@Controller
public class HubGoController {

    private final CompanyLinkService companyLinkService;
    private final HubCustomLinkService hubCustomLinkService;
    private final HubHistoryService hubHistoryService;

    public HubGoController(
            CompanyLinkService companyLinkService,
            HubCustomLinkService hubCustomLinkService,
            HubHistoryService hubHistoryService) {
        this.companyLinkService = companyLinkService;
        this.hubCustomLinkService = hubCustomLinkService;
        this.hubHistoryService = hubHistoryService;
    }

    @GetMapping("/hub/go/{type}/{id}")
    public String go(@PathVariable("type") String type, @PathVariable("id") long id, HttpSession session) {
        HubMemberSession member = HubSessions.current(session);
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);

        if ("link".equals(normalizedType)) {
            CompanyLink link = companyLinkService.findActiveById(id);
            if (link == null) {
                return "redirect:/links";
            }
            if (member != null) {
                hubHistoryService.record(member.getId(), "PUBLIC", link.getId(), null, link.getTitle(), link.getUrl());
            }
            return "redirect:" + link.getUrl();
        }

        if ("custom".equals(normalizedType)) {
            if (member == null) {
                return "redirect:/hub/login";
            }
            HubCustomLink link = hubCustomLinkService.findOwn(id, member.getId());
            if (link == null) {
                return "redirect:/links";
            }
            hubHistoryService.record(member.getId(), "CUSTOM", null, link.getId(), link.getTitle(), link.getUrl());
            return "redirect:" + link.getUrl();
        }

        return "redirect:/links";
    }
}
