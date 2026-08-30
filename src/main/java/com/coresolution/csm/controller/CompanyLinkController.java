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
import com.coresolution.csm.serivce.HubNoticeService;
import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.HubCustomLink;
import com.coresolution.csm.vo.HubLinkView;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.vo.Userdata;
import com.coresolution.csm.web.HubLinkPresenter;
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
    private final HubNoticeService hubNoticeService;
    private final HubLinkPresenter hubLinkPresenter;

    @GetMapping("/links")
    public String links(Model model, HttpSession session) {
        // 로그인 상태면 개인 영역(즐겨찾기·내 링크·메모)을, 아니면 공용 링크만 렌더한다.
        HubMemberSession hubMember = HubSessions.current(session);
        boolean loggedIn = hubMember != null;
        model.addAttribute("hubMember", hubMember);

        java.util.Set<Long> favoriteIds = loggedIn
                ? new java.util.HashSet<>(hubFavoriteService.listFavoriteLinkIds(hubMember.getId()))
                : java.util.Set.of();

        // 운영자가 지정한 분류 색상·축약. 비어 있는 항목은 presenter가 기본값으로 채운다.
        Map<String, HubLinkPresenter.Category> styles =
                hubLinkPresenter.categoryStyles(companyLinkService.listCategories());

        List<CompanyLink> links = companyLinkService.listActiveLinks();
        List<HubLinkView> linkRows = hubLinkPresenter.publicLinks(links, favoriteIds, loggedIn, styles);
        model.addAttribute("linkRows", linkRows);
        model.addAttribute("linkGroupRows", groupRowsByCategory(linkRows));
        // 🔴 **허브에는 categoryNav 를 내리지 않는다 — 사이드바 분류 목록을 끄기 위한 것이다.**
        //    본문 「전체 분류」가 같은 12개를 이름·색·개수까지 그대로 다시 그리고 있었다.
        //    본문 쪽은 링크 57개를 담은 실제 목록이라 지울 수 없으므로, 중복인 사이드바를 끈다.
        //    (shell.html 은 categoryNav 가 있을 때만 그 블록을 렌더한다 — 템플릿은 안 건드린다.)
        //
        // ⚠️ 링크 관리 화면(admin)에서는 계속 내린다. 그쪽 본문은 분류 없는 평평한 표라
        //    사이드바 목록이 유일한 분류 인덱스이고, 중복이 아니다.
        //
        // ⚠️ 사이드바 항목은 본문 그룹으로 점프하는 앵커(`#cat-{name}`)였다. 그 이동 수단이
        //    사라지므로, 되돌릴 때는 이 한 줄만 살리면 된다.

        // 필터 칩 개수 — 환경별 집계는 링크 목록에서 바로 센다.
        model.addAttribute("publicCount", linkRows.size());
        model.addAttribute("prodCount", countEnv(linkRows, "prod"));
        model.addAttribute("devCount", countEnv(linkRows, "dev") + countEnv(linkRows, "demo"));

        model.addAttribute("favoriteRows", loggedIn
                ? hubLinkPresenter.publicLinks(hubFavoriteService.listFavorites(hubMember.getId()), favoriteIds, true, styles)
                : java.util.List.of());
        // 로그인 시에만 개인 메모장을 노출한다(미로그인은 조회 자체를 하지 않는다).
        model.addAttribute("memo", loggedIn ? hubMemoService.find(hubMember.getId()) : "");

        // 개인 커스텀 링크. 예전 /hub/me를 이 페이지가 흡수했다(관리 폼도 여기서 렌더).
        // 카테고리별 그룹으로 내려준다(미분류는 "기타"). 개수 표시용 합계도 함께 전달.
        List<HubCustomLink> customLinks = loggedIn
                ? hubCustomLinkService.listOwn(hubMember.getId())
                : java.util.List.of();
        List<HubLinkView> customRows = hubLinkPresenter.customLinks(customLinks, styles);
        model.addAttribute("customRows", customRows);
        model.addAttribute("customGroupRows", groupRowsByCategory(customRows));
        model.addAttribute("customCount", customRows.size());

        // 최근 사용: 클릭 추적(hub_member_link_history) 기반(로그인 시에만).
        model.addAttribute("recentRows", loggedIn
                ? hubLinkPresenter.historyLinks(hubHistoryService.listRecent(hubMember.getId()))
                : java.util.List.of());
        // 인기 링크: 전 직원 클릭 집계 TOP(최근 30일). 개인정보 없는 집계라 미로그인도 노출.
        model.addAttribute("popularRows",
                hubLinkPresenter.publicLinks(hubHistoryService.listPopularPublic(6, 30), favoriteIds, loggedIn, styles));
        // 관리자 공지 배너(활성일 때만 non-null).
        model.addAttribute("notice", hubNoticeService.findActive());
        return "design/company-links";
    }

    @GetMapping("/admin/company-links")
    public String manage(Model model, HttpSession session) {
        Map<String, HubLinkPresenter.Category> styles =
                hubLinkPresenter.categoryStyles(companyLinkService.listCategories());
        List<CompanyLink> links = companyLinkService.listActiveLinks();
        // 관리 화면은 링크를 열지 않고 표로만 다루므로 즐겨찾기·경유 경로 없이 매핑한다.
        List<HubLinkView> linkRows = hubLinkPresenter.publicLinks(links, java.util.Set.of(), false, styles);
        model.addAttribute("linkRows", linkRows);
        model.addAttribute("publicCount", linkRows.size());
        model.addAttribute("categoryNav", categoryNav(linkRows, styles)); // 사이드바 분류 목록
        model.addAttribute("categoryRows", categoryRows(linkRows, styles)); // 탭 2(분류 순서)
        model.addAttribute("envPalette", HubLinkPresenter.ENV_OPTIONS);
        model.addAttribute("colorPalette", HubLinkPresenter.PALETTE_OPTIONS);
        model.addAttribute("hubMember", HubSessions.current(session)); // 사이드바 프로필 표시용
        model.addAttribute("notice", hubNoticeService.find()); // 공지 배너 관리 폼 현재값
        return "design/company-links-admin";
    }

    @PostMapping("/admin/company-links/notice")
    public String saveNotice(
            @RequestParam(value = "message", required = false, defaultValue = "") String message,
            @RequestParam(value = "level", required = false, defaultValue = "info") String level,
            @RequestParam(value = "active", required = false) String active,
            RedirectAttributes redirectAttributes) {
        hubNoticeService.save(message, level, active != null);
        redirectAttributes.addFlashAttribute("linkMessage", "공지 배너가 저장되었습니다.");
        return "redirect:/admin/company-links";
    }

    /** 분류 색상·축약 저장. 색상은 8색 세트 중에서만 오고, 서비스가 #rrggbb 형식을 한 번 더 검증한다. */
    @PostMapping("/admin/company-links/category-style")
    @ResponseBody
    public Map<String, Object> saveCategoryStyle(
            @RequestParam("category") String category,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "colorDark", required = false) String colorDark,
            @RequestParam(value = "shortLabel", required = false) String shortLabel) {
        try {
            companyLinkService.saveCategoryStyle(category, color, colorDark, shortLabel);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "msg", String.valueOf(e.getMessage()));
        }
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
            @RequestParam(value = "env", required = false, defaultValue = "") String env,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            companyLinkService.createLink(title, url, description, category, env, sortOrder, actor(session));
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
            @RequestParam(value = "env", required = false, defaultValue = "") String env,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            boolean updated = companyLinkService.updateLink(id, title, url, description, category, env, sortOrder, actor(session));
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

    /** 화면용 행을 분류별로 묶는다. 미분류는 "기타"로 모은다(공용·개인 공통). */
    private Map<String, List<HubLinkView>> groupRowsByCategory(List<HubLinkView> rows) {
        Map<String, List<HubLinkView>> groups = new LinkedHashMap<>();
        for (HubLinkView row : rows) {
            String category = row.getCategory() == null || row.getCategory().isBlank()
                    ? "기타"
                    : row.getCategory().trim();
            groups.computeIfAbsent(category, key -> new java.util.ArrayList<>()).add(row);
        }
        return groups;
    }

    /** 사이드바 분류 목록 — 이름·색·개수. 링크 목록 순서(분류 sort_order)를 그대로 따른다. */
    private List<Map<String, Object>> categoryNav(List<HubLinkView> rows, Map<String, HubLinkPresenter.Category> styles) {
        List<Map<String, Object>> nav = new java.util.ArrayList<>();
        for (Map.Entry<String, List<HubLinkView>> entry : groupRowsByCategory(rows).entrySet()) {
            HubLinkPresenter.Category style = hubLinkPresenter.category(entry.getKey(), styles);
            nav.add(Map.of(
                    "name", entry.getKey(),
                    "color", style.color(),
                    "colorDark", style.colorDark(),
                    "count", entry.getValue().size()));
        }
        return nav;
    }

    /** 분류 순서 탭용 — 사이드바 항목에 저장된 sort_order를 붙인다. */
    private List<Map<String, Object>> categoryRows(List<HubLinkView> rows, Map<String, HubLinkPresenter.Category> styles) {
        Map<String, Object> orders = new LinkedHashMap<>();
        for (Map<String, Object> row : companyLinkService.listCategories()) {
            Object name = row.get("category_name");
            if (name != null) {
                orders.put(name.toString(), row.get("sort_order"));
            }
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> nav : categoryNav(rows, styles)) {
            Map<String, Object> merged = new LinkedHashMap<>(nav);
            merged.put("sortOrder", orders.getOrDefault(nav.get("name"), 9999));
            merged.put("shortLabel", hubLinkPresenter.category(nav.get("name").toString(), styles).shortLabel());
            out.add(merged);
        }
        return out;
    }

    private long countEnv(List<HubLinkView> rows, String env) {
        return rows.stream().filter(row -> env.equals(row.getEnv())).count();
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
