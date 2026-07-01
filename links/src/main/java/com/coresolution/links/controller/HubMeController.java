package com.coresolution.links.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.links.serivce.HubCustomLinkService;
import com.coresolution.links.serivce.HubMemberService;
import com.coresolution.links.serivce.HubRememberService;
import com.coresolution.links.vo.HubMemberSession;
import com.coresolution.links.web.HubRememberCookies;
import com.coresolution.links.web.HubSessions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 개인 페이지: 커스텀 링크 전용(목록 + 추가/수정/삭제) + 계정 설정.
 * 즐겨찾기는 허브 상단으로 이동했고, 최근 사용 화면은 제거됨(클릭 추적/기록은 별도로 유지).
 *
 * <p>인터셉터가 /hub/me/** 를 1차로 막지만, 각 메서드 진입부에서도 세션을 직접 가드하고
 * member_id를 세션에서만 취한다(가드레일 ①-a). 서비스 쿼리는 member_id로 강제 필터된다(①-b).
 */
@Controller
public class HubMeController {

    private final HubCustomLinkService hubCustomLinkService;
    private final HubMemberService hubMemberService;
    private final HubRememberService hubRememberService;

    public HubMeController(
            HubCustomLinkService hubCustomLinkService,
            HubMemberService hubMemberService,
            HubRememberService hubRememberService) {
        this.hubCustomLinkService = hubCustomLinkService;
        this.hubMemberService = hubMemberService;
        this.hubRememberService = hubRememberService;
    }

    @GetMapping("/hub/me")
    public String me(HttpSession session, Model model) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        // 내 페이지는 커스텀 링크 전용. 즐겨찾기는 허브 상단으로, 최근 사용 화면은 제거됨.
        // (클릭 추적/이력 기록은 HubGoController·HubHistoryService.record에서 그대로 유지)
        model.addAttribute("hubMember", member);
        model.addAttribute("customLinks", hubCustomLinkService.listOwn(member.getId()));
        return "hub/me";
    }

    @PostMapping("/hub/me/custom-links")
    public String createCustomLink(
            @RequestParam("title") String title,
            @RequestParam("url") String url,
            @RequestParam(value = "memo", required = false) String memo,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        try {
            hubCustomLinkService.create(member.getId(), title, url, memo, sortOrder);
            redirectAttributes.addFlashAttribute("customMessage", "링크가 추가되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("customError", e.getMessage());
        }
        return "redirect:/hub/me";
    }

    @PostMapping("/hub/me/custom-links/{id}")
    public String updateCustomLink(
            @PathVariable("id") long id,
            @RequestParam("title") String title,
            @RequestParam("url") String url,
            @RequestParam(value = "memo", required = false) String memo,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        try {
            boolean updated = hubCustomLinkService.update(id, member.getId(), title, url, memo, sortOrder);
            redirectAttributes.addFlashAttribute(
                    updated ? "customMessage" : "customError",
                    updated ? "링크가 수정되었습니다." : "수정할 링크를 찾을 수 없습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("customError", e.getMessage());
        }
        return "redirect:/hub/me";
    }

    @PostMapping("/hub/me/custom-links/{id}/delete")
    public String deleteCustomLink(
            @PathVariable("id") long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        boolean deleted = hubCustomLinkService.delete(id, member.getId());
        redirectAttributes.addFlashAttribute(
                deleted ? "customMessage" : "customError",
                deleted ? "링크가 삭제되었습니다." : "삭제할 링크를 찾을 수 없습니다.");
        return "redirect:/hub/me";
    }

    @GetMapping("/hub/me/account")
    public String account(HttpSession session, Model model) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        model.addAttribute("hubMember", member);
        return "hub/account";
    }

    @PostMapping("/hub/me/account/password")
    public String changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            HttpSession session,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        try {
            hubMemberService.changePassword(member.getId(), currentPassword, newPassword);
            // 비번 변경 시 현재 기기를 제외한 다른 기기의 기억 토큰을 모두 폐기한다.
            hubRememberService.deleteOthersForMember(member.getId(), HubRememberCookies.read(request));
            redirectAttributes.addFlashAttribute("accountMessage", "비밀번호가 변경되었습니다. 다른 기기는 다시 로그인해야 합니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("accountError", e.getMessage());
        }
        return "redirect:/hub/me/account";
    }

    @PostMapping("/hub/me/account/logout-all")
    public String logoutAllDevices(
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        // 이 회원의 모든 기억 토큰 폐기(현재 기기 포함) + 현재 쿠키 만료. 단, 현재 세션은 유지.
        hubRememberService.deleteAllForMember(member.getId());
        HubRememberCookies.clear(request, response, hubRememberService.isCookieSecure());
        redirectAttributes.addFlashAttribute("accountMessage", "모든 기기에서 로그아웃했습니다.");
        return "redirect:/hub/me/account";
    }
}
