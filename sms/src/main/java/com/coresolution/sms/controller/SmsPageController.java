package com.coresolution.sms.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.coresolution.sms.model.SessionUser;
import com.coresolution.sms.service.SsoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Page routes + the MediPlat SSO entry handshake. Mirrors cancer-treatment's controller
 * pattern (plain HttpSession, no Spring Security).
 */
@Controller
public class SmsPageController {

    private final SsoService ssoService;
    private final String mediplatPortalUrl;
    private final String platformAdminInst;

    public SmsPageController(
            SsoService ssoService,
            @Value("${sms.mediplat-portal-url:http://localhost:8082/portal}") String mediplatPortalUrl,
            @Value("${sms.platform-admin-inst:core}") String platformAdminInst) {
        this.ssoService = ssoService;
        this.mediplatPortalUrl = mediplatPortalUrl;
        this.platformAdminInst = platformAdminInst == null ? "core" : platformAdminInst.trim();
    }

    @GetMapping({ "", "/" })
    public String root(HttpSession session) {
        return SmsSession.current(session) == null ? "redirect:/login-required" : "redirect:/sms-send";
    }

    @GetMapping("/login-required")
    public String loginRequired() {
        return "redirect:" + mediplatPortalUrl;
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:" + mediplatPortalUrl;
    }

    @GetMapping({ "/mediplat/sso/entry", "mediplat/sso/entry" })
    public String ssoEntry(
            @RequestParam("inst") String inst,
            @RequestParam("userId") String userId,
            @RequestParam("expires") long expires,
            @RequestParam("signature") String signature,
            @RequestParam(name = "target", required = false) String target,
            @RequestParam(name = "role", required = false) String role,
            HttpServletRequest request) {
        String redirectTarget;
        try {
            redirectTarget = ssoService.validateAndResolveTarget(inst, userId, expires, target, role, signature);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session = request.getSession(false);
        session.setAttribute(SmsSession.SESSION_USER,
                new SessionUser(inst.trim(), userId.trim(), ssoService.canonicalRole(role)));
        return "redirect:" + redirectTarget;
    }

    @GetMapping("/sms-send")
    public String sendPage(Model model, HttpSession session) {
        SessionUser user = SmsSession.current(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user, "send");
        return "sms-send";
    }

    @GetMapping("/sms-history")
    public String historyPage(Model model, HttpSession session) {
        SessionUser user = SmsSession.current(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user, "history");
        return "sms-history";
    }

    @GetMapping("/sms-cost")
    public String costPage(Model model, HttpSession session) {
        SessionUser user = SmsSession.current(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user, "cost");
        return "sms-cost";
    }

    private void populateShell(Model model, SessionUser user, String activeMenu) {
        model.addAttribute("user", user);
        model.addAttribute("activeMenu", activeMenu);
        model.addAttribute("mediplatPortalUrl", mediplatPortalUrl);
        model.addAttribute("isPlatformAdmin", isPlatformAdmin(user));
    }

    private boolean isPlatformAdmin(SessionUser user) {
        return user != null && user.getInstCode() != null
                && platformAdminInst.equalsIgnoreCase(user.getInstCode().trim());
    }
}
