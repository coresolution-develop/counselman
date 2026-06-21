package com.coresolution.csm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.csm.serivce.HubCustomLinkService;
import com.coresolution.csm.serivce.HubFavoriteService;
import com.coresolution.csm.serivce.HubHistoryService;
import com.coresolution.csm.serivce.HubMemberService;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubSessions;

import jakarta.servlet.http.HttpSession;

/**
 * 개인 페이지: 내 즐겨찾기 / 커스텀 링크 / 최근 사용. 커스텀 링크 CRUD 포함.
 *
 * <p>인터셉터가 /hub/me/** 를 1차로 막지만, 각 메서드 진입부에서도 세션을 직접 가드하고
 * member_id를 세션에서만 취한다(가드레일 ①-a). 서비스 쿼리는 member_id로 강제 필터된다(①-b).
 */
@Controller
public class HubMeController {

    private final HubFavoriteService hubFavoriteService;
    private final HubCustomLinkService hubCustomLinkService;
    private final HubHistoryService hubHistoryService;
    private final HubMemberService hubMemberService;

    public HubMeController(
            HubFavoriteService hubFavoriteService,
            HubCustomLinkService hubCustomLinkService,
            HubHistoryService hubHistoryService,
            HubMemberService hubMemberService) {
        this.hubFavoriteService = hubFavoriteService;
        this.hubCustomLinkService = hubCustomLinkService;
        this.hubHistoryService = hubHistoryService;
        this.hubMemberService = hubMemberService;
    }

    @GetMapping("/hub/me")
    public String me(HttpSession session, Model model) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        model.addAttribute("hubMember", member);
        model.addAttribute("favorites", hubFavoriteService.listFavorites(member.getId()));
        model.addAttribute("customLinks", hubCustomLinkService.listOwn(member.getId()));
        model.addAttribute("recent", hubHistoryService.listRecent(member.getId()));
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
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        try {
            hubMemberService.changePassword(member.getId(), currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("accountMessage", "비밀번호가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("accountError", e.getMessage());
        }
        return "redirect:/hub/me/account";
    }
}
