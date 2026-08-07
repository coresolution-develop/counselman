package com.coresolution.csm.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.csm.web.HubIds;

import com.coresolution.csm.serivce.HubCustomLinkService;
import com.coresolution.csm.serivce.HubMemberService;
import com.coresolution.csm.serivce.HubRememberService;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubRememberCookies;
import com.coresolution.csm.web.HubSessions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 커스텀 링크 쓰기(추가/수정/삭제) + 계정 설정.
 * 목록 화면은 허브(/links)가 흡수했으므로 여기서는 폼 처리 후 /links로 돌려보낸다.
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

    /**
     * 예전 개인 페이지. 커스텀 링크가 허브(/links)로 흡수되어 화면이 사라졌지만,
     * 기존 북마크·링크가 깨지지 않도록 리다이렉트만 남긴다.
     */
    @GetMapping("/hub/me")
    public String me() {
        return "redirect:/links";
    }

    @PostMapping("/hub/me/custom-links")
    public String createCustomLink(
            @RequestParam("title") String title,
            @RequestParam("url") String url,
            @RequestParam(value = "memo", required = false) String memo,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        try {
            hubCustomLinkService.create(member.getId(), title, url, memo, category, sortOrder);
            redirectAttributes.addFlashAttribute("customMessage", "링크가 추가되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("customError", e.getMessage());
        }
        return "redirect:/links";
    }

    @PostMapping("/hub/me/custom-links/{id}")
    public String updateCustomLink(
            @PathVariable("id") long id,
            @RequestParam("title") String title,
            @RequestParam("url") String url,
            @RequestParam(value = "memo", required = false) String memo,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        try {
            boolean updated = hubCustomLinkService.update(id, member.getId(), title, url, memo, category, sortOrder);
            redirectAttributes.addFlashAttribute(
                    updated ? "customMessage" : "customError",
                    updated ? "링크가 수정되었습니다." : "수정할 링크를 찾을 수 없습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("customError", e.getMessage());
        }
        return "redirect:/links";
    }

    /** 브라우저 북마크 HTML 업로드 → 개인 링크 일괄 등록. 완료 후 /links로 리다이렉트. */
    @PostMapping("/hub/me/custom-links/import")
    public String importCustomLinks(
            @RequestParam("file") MultipartFile file,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("customError", "가져올 북마크 파일을 선택해주세요.");
            return "redirect:/links";
        }
        try {
            String html = new String(file.getBytes(), StandardCharsets.UTF_8);
            int[] result = hubCustomLinkService.importBookmarks(member.getId(), html);
            String message = result[0] + "개의 링크를 가져왔습니다."
                    + (result[1] > 0 ? " (" + result[1] + "개는 중복·무효로 건너뜀)" : "");
            redirectAttributes.addFlashAttribute("customMessage", message);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("customError", "북마크 파일을 읽지 못했습니다.");
        }
        return "redirect:/links";
    }

    /** 드래그 재정렬 저장 (AJAX). ids = 새 순서의 커스텀 링크 id CSV. 응답: {"ok": true}. */
    @PostMapping("/hub/me/custom-links/reorder")
    @ResponseBody
    public Map<String, Object> reorderCustomLinks(
            @RequestParam("ids") String ids,
            HttpSession session) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return Map.of("ok", false, "error", "로그인이 필요합니다.");
        }
        List<Long> ordered = HubIds.parseCsv(ids);
        hubCustomLinkService.reorder(member.getId(), ordered);
        return Map.of("ok", true);
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
        return "redirect:/links";
    }

    /** 선택 삭제. ids = 삭제할 커스텀 링크 id CSV. 완료 후 /links로 리다이렉트. */
    @PostMapping("/hub/me/custom-links/bulk-delete")
    public String bulkDeleteCustomLinks(
            @RequestParam("ids") String ids,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return "redirect:/hub/login";
        }
        int deleted = hubCustomLinkService.deleteMany(member.getId(), HubIds.parseCsv(ids));
        redirectAttributes.addFlashAttribute(
                deleted > 0 ? "customMessage" : "customError",
                deleted > 0 ? deleted + "개의 링크를 삭제했습니다." : "삭제할 링크를 선택해주세요.");
        return "redirect:/links";
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
