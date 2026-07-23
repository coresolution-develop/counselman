package com.coresolution.csm.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.csm.serivce.CompanyLinkService;
import com.coresolution.csm.serivce.HubCustomLinkService;
import com.coresolution.csm.serivce.HubFavoriteService;
import com.coresolution.csm.serivce.HubHistoryService;
import com.coresolution.csm.serivce.HubMemoService;
import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.vo.Userdata;
import com.coresolution.csm.web.HubSessions;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CompanyLinkController {

    private final CompanyLinkService companyLinkService;
    private final HubFavoriteService hubFavoriteService;
    private final HubMemoService hubMemoService;
    private final HubCustomLinkService hubCustomLinkService;
    private final HubHistoryService hubHistoryService;

    @GetMapping("/links")
    public String links(Model model, HttpSession session) {
        List<CompanyLink> links = companyLinkService.listActiveLinks();
        model.addAttribute("links", links);
        model.addAttribute("linkGroups", groupByCategory(links));
        model.addAttribute("categories", companyLinkService.listCategories());
        // 로그인 상태면 개인 페이지 진입을, 아니면 로그인 버튼을 상단바에 노출(공개 허브는 항상 접근 가능).
        HubMemberSession hubMember = HubSessions.current(session);
        model.addAttribute("hubMember", hubMember);
        // 로그인 시에만 ★ 채움 표시용 즐겨찾기 link_id 집합 + 상단 "내 즐겨찾기" 섹션용 목록을 내려준다.
        model.addAttribute("favoriteLinkIds", hubMember == null
                ? java.util.Set.of()
                : new java.util.HashSet<>(hubFavoriteService.listFavoriteLinkIds(hubMember.getId())));
        model.addAttribute("favorites", hubMember == null
                ? java.util.List.of()
                : hubFavoriteService.listFavorites(hubMember.getId()));
        // 로그인 시에만 개인 메모장을 노출한다(미로그인은 조회 자체를 하지 않는다).
        model.addAttribute("memo", hubMember == null
                ? ""
                : hubMemoService.find(hubMember.getId()));
        // 개인 커스텀 링크. 예전 /hub/me를 이 페이지가 흡수했다(관리 폼도 여기서 렌더).
        model.addAttribute("customLinks", hubMember == null
                ? java.util.List.of()
                : hubCustomLinkService.listOwn(hubMember.getId()));
        // 최근 사용: 클릭 추적(hub_member_link_history)을 상단에 다시 노출한다(로그인 시에만).
        model.addAttribute("recent", hubMember == null
                ? java.util.List.of()
                : hubHistoryService.listRecent(hubMember.getId()));
        return "design/company-links";
    }

    @GetMapping("/admin/company-links")
    public String manage(Model model, HttpSession session) {
        List<CompanyLink> links = companyLinkService.listActiveLinks();
        model.addAttribute("links", links);
        model.addAttribute("linkGroups", groupByCategory(links));
        model.addAttribute("categories", companyLinkService.listCategories());
        model.addAttribute("hubMember", HubSessions.current(session)); // 사이드바 프로필 표시용
        return "design/company-links-admin";
    }

    @PostMapping("/admin/company-links/category-order")
    @ResponseBody
    public Map<String, Object> saveCategoryOrder(
            @RequestParam Map<String, String> params) {
        try {
            params.forEach((key, value) -> {
                if (key.startsWith("cat_")) {
                    String category = key.substring(4);
                    int order;
                    try { order = Integer.parseInt(value.trim()); } catch (NumberFormatException e) { order = 0; }
                    companyLinkService.saveCategoryOrder(category, order);
                }
            });
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "msg", e.getMessage());
        }
    }

    @PostMapping("/admin/company-links")
    public String createLink(
            @RequestParam("title") String title,
            @RequestParam("url") String url,
            @RequestParam(value = "description", required = false, defaultValue = "") String description,
            @RequestParam(value = "category", required = false, defaultValue = "") String category,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            companyLinkService.createLink(title, url, description, category, sortOrder, actor(session));
            redirectAttributes.addFlashAttribute("linkMessage", "링크가 추가되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("linkError", e.getMessage());
        }
        return "redirect:/admin/company-links";
    }

    @PostMapping("/admin/company-links/{id}")
    public String updateLink(
            @PathVariable("id") long id,
            @RequestParam("title") String title,
            @RequestParam("url") String url,
            @RequestParam(value = "description", required = false, defaultValue = "") String description,
            @RequestParam(value = "category", required = false, defaultValue = "") String category,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            boolean updated = companyLinkService.updateLink(id, title, url, description, category, sortOrder, actor(session));
            redirectAttributes.addFlashAttribute(
                    updated ? "linkMessage" : "linkError",
                    updated ? "링크가 수정되었습니다." : "수정할 링크를 찾을 수 없습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("linkError", e.getMessage());
        }
        return "redirect:/admin/company-links";
    }

    @PostMapping("/admin/company-links/{id}/delete")
    public String deleteLink(
            @PathVariable("id") long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        boolean deleted = companyLinkService.deleteLink(id, actor(session));
        redirectAttributes.addFlashAttribute(
                deleted ? "linkMessage" : "linkError",
                deleted ? "링크가 삭제되었습니다." : "삭제할 링크를 찾을 수 없습니다.");
        return "redirect:/admin/company-links";
    }

    @GetMapping("/api/company-links")
    @ResponseBody
    public Map<String, Object> listLinks() {
        return Map.of("links", companyLinkService.listActiveLinks());
    }

    private Map<String, List<CompanyLink>> groupByCategory(List<CompanyLink> links) {
        Map<String, List<CompanyLink>> groups = new LinkedHashMap<>();
        for (CompanyLink link : links) {
            String category = link.getCategory() == null || link.getCategory().isBlank()
                    ? "기타"
                    : link.getCategory().trim();
            groups.computeIfAbsent(category, key -> new java.util.ArrayList<>()).add(link);
        }
        return groups;
    }

    private String actor(HttpSession session) {
        Object info = session == null ? null : session.getAttribute("userInfo");
        if (info instanceof Userdata user && user.getUs_col_02() != null) {
            return user.getUs_col_02();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "public";
    }
}
