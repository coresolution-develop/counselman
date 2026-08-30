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

    // ── 비밀번호 찾기 ────────────────────────────────────────────────────────
    //
    // 이메일 링크·OTP 없이 한 브라우저 안에서 끝낸다. 그래서 토큰 테이블이 필요 없고,
    // 1단계 통과 사실을 **세션 마커**로만 들고 다닌다.
    //
    // 🔴 마커가 곧 인증이다. resetPassword 는 본인 확인을 하지 않으므로,
    //    2단계 진입·제출 양쪽에서 마커를 반드시 확인해야 한다.

    /** 1단계 통과 회원 id. 이 값이 있는 세션만 2단계로 들어갈 수 있다. */
    private static final String RESET_MEMBER_ID = "hubResetMemberId";
    /** 마커 발급 시각. 브라우저를 열어둔 채 자리를 비운 경우를 대비해 만료를 둔다. */
    private static final String RESET_ISSUED_AT = "hubResetIssuedAt";
    /** 같은 세션에서의 1단계 실패 횟수. 무한 대입을 막는다. */
    private static final String RESET_ATTEMPTS = "hubResetAttempts";

    private static final long RESET_TTL_MILLIS = 10 * 60 * 1000L;
    private static final int RESET_MAX_ATTEMPTS = 5;

    @GetMapping("/hub/find-password")
    public String findPasswordForm(HttpSession session, Model model) {
        if (HubSessions.current(session) != null) {
            return "redirect:/links";
        }
        if (!model.containsAttribute("email")) {
            model.addAttribute("email", "");
        }
        if (!model.containsAttribute("name")) {
            model.addAttribute("name", "");
        }
        return "hub/find-password";
    }

    @PostMapping("/hub/find-password")
    public String findPassword(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "signupCode", required = false) String signupCode,
            HttpSession session, RedirectAttributes redirect) {

        int attempts = attemptsOf(session);
        if (attempts >= RESET_MAX_ATTEMPTS) {
            // 세션 기준이라 쿠키를 지우면 우회된다. 완벽한 차단이 아니라 실수·자동화 억제용이고,
            // 실제 방어선은 가입코드다(HubMemberService.verifyForReset 주석 참고).
            redirect.addFlashAttribute("resetError", "시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/hub/find-password";
        }

        try {
            long memberId = hubMemberService.verifyForReset(email, name, signupCode);
            session.setAttribute(RESET_MEMBER_ID, memberId);
            session.setAttribute(RESET_ISSUED_AT, System.currentTimeMillis());
            session.removeAttribute(RESET_ATTEMPTS);
            return "redirect:/hub/reset-password";
        } catch (IllegalArgumentException e) {
            session.setAttribute(RESET_ATTEMPTS, attempts + 1);
            redirect.addFlashAttribute("resetError", e.getMessage());
            redirect.addFlashAttribute("email", email == null ? "" : email);
            redirect.addFlashAttribute("name", name == null ? "" : name);
            return "redirect:/hub/find-password";
        }
    }

    @GetMapping("/hub/reset-password")
    public String resetPasswordForm(HttpSession session) {
        return resetMemberId(session) == null ? "redirect:/hub/find-password" : "hub/reset-password";
    }

    @PostMapping("/hub/reset-password")
    public String resetPassword(
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "passwordConfirm", required = false) String passwordConfirm,
            HttpSession session, RedirectAttributes redirect) {

        Long memberId = resetMemberId(session);
        if (memberId == null) {
            redirect.addFlashAttribute("resetError", "다시 확인해주세요. 시간이 지나 만료되었습니다.");
            return "redirect:/hub/find-password";
        }
        if (password == null || !password.equals(passwordConfirm)) {
            redirect.addFlashAttribute("resetError", "새 비밀번호가 서로 다릅니다.");
            return "redirect:/hub/reset-password";
        }
        try {
            hubMemberService.resetPassword(memberId, password);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("resetError", e.getMessage());
            return "redirect:/hub/reset-password";
        }
        // 🔴 비번을 바꾼 이유가 도난이면 남아 있는 "이 기기 기억" 토큰도 함께 끊어야 한다.
        //    끊지 않으면 이전 기기가 그대로 로그인 상태로 남는다.
        hubRememberService.deleteAllForMember(memberId);
        clearResetMarker(session);
        redirect.addFlashAttribute("loginNotice", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.");
        return "redirect:/hub/login";
    }

    /** 마커가 유효할 때만 회원 id. 만료됐으면 지우고 null. */
    private Long resetMemberId(HttpSession session) {
        Object id = session.getAttribute(RESET_MEMBER_ID);
        Object issuedAt = session.getAttribute(RESET_ISSUED_AT);
        if (!(id instanceof Long memberId) || !(issuedAt instanceof Long at)) {
            return null;
        }
        if (System.currentTimeMillis() - at > RESET_TTL_MILLIS) {
            clearResetMarker(session);
            return null;
        }
        return memberId;
    }

    private void clearResetMarker(HttpSession session) {
        session.removeAttribute(RESET_MEMBER_ID);
        session.removeAttribute(RESET_ISSUED_AT);
    }

    private int attemptsOf(HttpSession session) {
        Object value = session.getAttribute(RESET_ATTEMPTS);
        return value instanceof Integer count ? count : 0;
    }

    private boolean isChecked(String value) {
        return value != null && ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)
                || "1".equals(value) || "yes".equalsIgnoreCase(value));
    }
}
