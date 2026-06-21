package com.coresolution.csm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.csm.serivce.HubMemberService;
import com.coresolution.csm.serivce.HubRememberService;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubRememberCookies;
import com.coresolution.csm.web.HubSessions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 허브 개인화 인증: 자가가입 / 로그인 / 로그아웃.
 * 가입·로그인 성공 시 공개 허브(/links)로 복귀한다(기존 흐름을 막지 않는 "추가 기능").
 */
@Controller
public class HubAuthController {

    private final HubMemberService hubMemberService;
    private final HubRememberService hubRememberService;

    public HubAuthController(HubMemberService hubMemberService, HubRememberService hubRememberService) {
        this.hubMemberService = hubMemberService;
        this.hubRememberService = hubRememberService;
    }

    @GetMapping("/hub/signup")
    public String signupForm(HttpSession session, Model model) {
        if (HubSessions.current(session) != null) {
            return "redirect:/links";
        }
        if (!model.containsAttribute("email")) {
            model.addAttribute("email", "");
        }
        if (!model.containsAttribute("name")) {
            model.addAttribute("name", "");
        }
        return "hub/signup";
    }

    @PostMapping("/hub/signup")
    public String signup(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("name") String name,
            @RequestParam(value = "signupCode", required = false) String signupCode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            HubMemberSession member = hubMemberService.signup(email, password, name, signupCode);
            HubSessions.store(session, member);
            return "redirect:/links";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("signupError", e.getMessage());
            redirectAttributes.addFlashAttribute("email", email == null ? "" : email.trim());
            redirectAttributes.addFlashAttribute("name", name == null ? "" : name.trim());
            return "redirect:/hub/signup";
        }
    }

    @GetMapping("/hub/login")
    public String loginForm(HttpSession session) {
        if (HubSessions.current(session) != null) {
            return "redirect:/links";
        }
        return "hub/login";
    }

    @PostMapping("/hub/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam(value = "remember", required = false) String remember,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        HubMemberSession member = hubMemberService.authenticate(email, password);
        if (member == null) {
            redirectAttributes.addFlashAttribute("loginError", "이메일 또는 비밀번호가 올바르지 않습니다.");
            redirectAttributes.addFlashAttribute("email", email == null ? "" : email.trim());
            return "redirect:/hub/login";
        }
        HubSessions.store(session, member);
        // "이 기기 기억하기" 체크 시에만 영속 토큰 발급 + 쿠키 굽기
        if (isChecked(remember)) {
            String cookieValue = hubRememberService.issue(member.getId(), request.getHeader("User-Agent"));
            HubRememberCookies.write(request, response, cookieValue,
                    hubRememberService.getCookieMaxAgeSeconds(), hubRememberService.isCookieSecure());
        }
        return "redirect:/links";
    }

    @PostMapping("/hub/logout")
    public String logout(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        // 현재 기기의 영속 토큰 삭제 + 쿠키 만료
        hubRememberService.deleteByCookie(HubRememberCookies.read(request));
        HubRememberCookies.clear(request, response, hubRememberService.isCookieSecure());
        // 허브 회원 신원만 제거한다. csm 직원 세션(userInfo)은 건드리지 않는다.
        HubSessions.clear(session);
        return "redirect:/links";
    }

    private boolean isChecked(String value) {
        return value != null && ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)
                || "1".equals(value) || "yes".equalsIgnoreCase(value));
    }
}
