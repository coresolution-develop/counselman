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
import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.Userdata;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CompanyLinkController {

    private final CompanyLinkService companyLinkService;

    @GetMapping({ "links", "/links" })
    public String links(Model model, HttpSession session) {
        List<CompanyLink> links = companyLinkService.listActiveLinks();
        model.addAttribute("links", links);
        model.addAttribute("linkGroups", groupByCategory(links));
        model.addAttribute("canManageLinks", true);
        return "design/company-links";
    }

    @PostMapping({ "admin/company-links", "/admin/company-links" })
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
        return "redirect:/links";
    }

    @PostMapping({ "admin/company-links/{id}", "/admin/company-links/{id}" })
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
        return "redirect:/links";
    }

    @PostMapping({ "admin/company-links/{id}/delete", "/admin/company-links/{id}/delete" })
    public String deleteLink(
            @PathVariable("id") long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        boolean deleted = companyLinkService.deleteLink(id, actor(session));
        redirectAttributes.addFlashAttribute(
                deleted ? "linkMessage" : "linkError",
                deleted ? "링크가 삭제되었습니다." : "삭제할 링크를 찾을 수 없습니다.");
        return "redirect:/links";
    }

    @GetMapping({ "api/company-links", "/api/company-links" })
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
