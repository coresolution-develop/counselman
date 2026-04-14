package com.coresolution.mediplat.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.mediplat.model.PlatformService;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.coresolution.mediplat.service.CounselManSsoLinkService;
import com.coresolution.mediplat.service.PlatformStoreService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class MediplatController {

    private static final String SESSION_USER = "mediplatUser";

    private final PlatformStoreService storeService;
    private final CounselManSsoLinkService counselManSsoLinkService;

    public MediplatController(
            PlatformStoreService storeService,
            CounselManSsoLinkService counselManSsoLinkService) {
        this.storeService = storeService;
        this.counselManSsoLinkService = counselManSsoLinkService;
    }

    @GetMapping({ "", "/" })
    public String root(HttpSession session) {
        return sessionUser(session) == null ? "redirect:/login" : "redirect:/services";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(name = "error", required = false) String error, Model model, HttpSession session) {
        if (sessionUser(session) != null) {
            return "redirect:/services";
        }
        model.addAttribute("error", error);
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam("instCode") String instCode,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = storeService.authenticate(instCode, username, password);
        if (user == null) {
            redirectAttributes.addAttribute("error", "기관코드 또는 계정정보가 올바르지 않습니다.");
            return "redirect:/login";
        }
        session.setAttribute(SESSION_USER, user);
        return "redirect:/services";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/services")
    public String servicesPage(Model model, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        List<PlatformService> services = storeService.listServicesForUser(user);
        model.addAttribute("user", user);
        model.addAttribute("availableServices", services.stream().filter(PlatformService::isAccessible).toList());
        model.addAttribute("unavailableServices", services.stream().filter(service -> !service.isAccessible()).toList());
        return "services";
    }

    @GetMapping("/launch/{serviceCode}")
    public String launchService(@PathVariable("serviceCode") String serviceCode, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        var service = storeService.findService(serviceCode);
        if (service == null) {
            return "redirect:/services";
        }
        if (!service.isEnabled()) {
            return "redirect:/services";
        }
        List<String> enabledCodes = user.isPlatformAdmin()
                ? List.of(service.getServiceCode())
                : storeService.listEnabledServiceCodes(user.getInstCode());
        if (!user.isPlatformAdmin() && !enabledCodes.contains(service.getServiceCode())) {
            return "redirect:/services";
        }
        String launchUrl = counselManSsoLinkService.createLaunchUrl(service, user);
        return "redirect:" + launchUrl;
    }

    @GetMapping("/admin")
    public String adminPage(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "error", required = false) String error,
            Model model,
            HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!user.isPlatformAdmin()) {
            return "redirect:/services";
        }
        String selectedInstCode = instCode == null || instCode.isBlank()
                ? user.getInstCode()
                : ("core".equalsIgnoreCase(instCode.trim()) ? "core" : instCode.trim().toUpperCase());
        model.addAttribute("user", user);
        model.addAttribute("institutions", storeService.listInstitutions());
        model.addAttribute("services", storeService.listAllServices());
        model.addAttribute("runtimeEnvCode", storeService.getRuntimeEnvCode());
        model.addAttribute("selectedInstCode", selectedInstCode);
        model.addAttribute("enabledServiceCodes", storeService.listEnabledServiceCodes(selectedInstCode));
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "admin";
    }

    @PostMapping("/admin/institutions")
    public String saveInstitution(
            @RequestParam("instCode") String instCode,
            @RequestParam("instName") String instName,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveInstitution(instCode, instName, useYn);
            redirectAttributes.addAttribute("message", "기관이 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/services")
    public String saveService(
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(name = "baseUrl", required = false) String baseUrl,
            @RequestParam(name = "baseUrlLocal", required = false) String baseUrlLocal,
            @RequestParam(name = "baseUrlDev", required = false) String baseUrlDev,
            @RequestParam(name = "baseUrlProd", required = false) String baseUrlProd,
            @RequestParam(name = "ssoEntryPath", defaultValue = "/mediplat/sso/entry") String ssoEntryPath,
            @RequestParam("userTarget") String userTarget,
            @RequestParam("adminTarget") String adminTarget,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            @RequestParam(name = "displayOrder", defaultValue = "0") Integer displayOrder,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveService(
                    serviceCode,
                    serviceName,
                    baseUrl,
                    baseUrlLocal,
                    baseUrlDev,
                    baseUrlProd,
                    ssoEntryPath,
                    userTarget,
                    adminTarget,
                    description,
                    useYn,
                    displayOrder);
            redirectAttributes.addAttribute("message", "서비스가 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/services/status")
    public String updateServiceStatus(
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.updateServiceStatus(serviceCode, useYn);
            redirectAttributes.addAttribute("message", "서비스 상태가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/access")
    public String saveAccess(
            @RequestParam("instCode") String instCode,
            @RequestParam(name = "enabledServiceCodes", required = false) List<String> enabledServiceCodes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveInstitutionServiceAccess(instCode, enabledServiceCodes);
            redirectAttributes.addAttribute("message", "기관별 서비스 권한이 저장되었습니다.");
            redirectAttributes.addAttribute("instCode", instCode);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    private PlatformSessionUser sessionUser(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER);
        return value instanceof PlatformSessionUser user ? user : null;
    }

    private boolean isAdminSession(HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        return user != null && user.isPlatformAdmin();
    }
}
