package com.coresolution.mediplat.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.util.StringUtils;

import com.coresolution.mediplat.model.PlatformInstitution;
import com.coresolution.mediplat.model.PlatformService;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.coresolution.mediplat.model.RoomBoardViewerAccount;
import com.coresolution.mediplat.model.RoomBoardViewerInstitution;
import com.coresolution.mediplat.service.CounselManSsoLinkService;
import com.coresolution.mediplat.service.PlatformStoreService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class MediplatController {

    private static final String SESSION_USER = "mediplatUser";
    private static final String SERVICE_CODE_ROOM_BOARD = "ROOM_BOARD";

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
        if (!model.containsAttribute("loginInstCode")) {
            model.addAttribute("loginInstCode", "");
        }
        if (!model.containsAttribute("loginUsername")) {
            model.addAttribute("loginUsername", "");
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam(name = "instCode", required = false) String instCode,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PlatformSessionUser user = storeService.authenticate(instCode, username, password);
        if (user == null) {
            redirectAttributes.addAttribute("error", "기관코드/기관명 또는 계정정보가 올바르지 않습니다.");
            redirectAttributes.addFlashAttribute("loginInstCode", StringUtils.hasText(instCode) ? instCode.trim() : "");
            redirectAttributes.addFlashAttribute("loginUsername", StringUtils.hasText(username) ? username.trim() : "");
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
        if (user.isRoomBoardViewer()) {
            return "redirect:/room-board-viewer";
        }
        List<PlatformService> services = storeService.listServicesForUser(user);
        List<PlatformInstitution> roomBoardViewerInstitutions = storeService.listRoomBoardViewerInstitutions(user);
        model.addAttribute("user", user);
        model.addAttribute("services", services);
        model.addAttribute("roomBoardViewerInstitutionCount", roomBoardViewerInstitutions.size());
        model.addAttribute("roomBoardViewerEnabled", !roomBoardViewerInstitutions.isEmpty());
        model.addAttribute("availableServices", services.stream().filter(PlatformService::isAccessible).toList());
        model.addAttribute("unavailableServices", services.stream().filter(service -> !service.isAccessible()).toList());
        return "services";
    }

    @GetMapping("/room-board-viewer")
    public String roomBoardViewerPage(Model model, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        PlatformService counselManService = storeService.findService("COUNSELMAN");
        if (counselManService == null || !counselManService.isEnabled()) {
            model.addAttribute("user", user);
            model.addAttribute("viewerItems", List.of());
            model.addAttribute("viewerError", "CounselMan 서비스가 비활성화되어 통합 병실현황판을 사용할 수 없습니다.");
            return "room-board-viewer";
        }

        List<RoomBoardViewerInstitution> viewerItems = new ArrayList<>();
        for (PlatformInstitution institution : storeService.listRoomBoardViewerInstitutions(user)) {
            if (institution == null || !StringUtils.hasText(institution.getInstCode())) {
                continue;
            }
            String launchUrl = counselManSsoLinkService.createLaunchUrl(
                    counselManService,
                    user,
                    institution.getInstCode(),
                    "/room-board?popup=1");
            viewerItems.add(new RoomBoardViewerInstitution(
                    institution.getInstCode(),
                    institution.getInstName(),
                    launchUrl));
        }

        model.addAttribute("user", user);
        model.addAttribute("viewerItems", viewerItems);
        model.addAttribute("viewerError", viewerItems.isEmpty()
                ? "조회 가능한 병실현황판 기관이 없습니다. 계정 권한 또는 기관 기능 설정을 확인해 주세요."
                : null);
        return "room-board-viewer";
    }

    @GetMapping("/launch/{serviceCode}")
    public String launchService(@PathVariable("serviceCode") String serviceCode, HttpSession session) {
        PlatformSessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        String normalizedServiceCode = serviceCode == null ? "" : serviceCode.trim().toUpperCase(Locale.ROOT);
        if (SERVICE_CODE_ROOM_BOARD.equals(normalizedServiceCode)) {
            if (user.isPlatformAdmin() || !storeService.isRoomBoardCounselLinkEnabled(user.getInstCode())) {
                return "redirect:/services";
            }
            PlatformService counselManService = storeService.findService("COUNSELMAN");
            if (counselManService == null || !counselManService.isEnabled()) {
                return "redirect:/services";
            }
            String launchUrl = counselManSsoLinkService.createLaunchUrl(
                    counselManService,
                    user,
                    user.getInstCode(),
                    "/room-board?popup=1");
            return "redirect:" + launchUrl;
        }
        var service = storeService.findService(normalizedServiceCode);
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
            @RequestParam(name = "viewerUsername", required = false) String viewerUsername,
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
        model.addAttribute("enabledIntegrationCodes", storeService.listEnabledIntegrationCodes(selectedInstCode));
        model.addAttribute("roomBoardCounselPairEnabled", storeService.isRoomBoardCounselPairEnabled(selectedInstCode));
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        populateViewerAdminModel(model, selectedInstCode, viewerUsername);
        return "admin";
    }

    @PostMapping("/admin/room-board-viewer/accounts")
    public String saveRoomBoardViewerAccount(
            @RequestParam("username") String username,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam("displayName") String displayName,
            @RequestParam(name = "useYn", defaultValue = "Y") String useYn,
            @RequestParam(name = "instCodes", required = false) List<String> instCodes,
            @RequestParam(name = "instCode", required = false) String selectedInstCode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveRoomBoardViewerAccount(username, password, displayName, useYn, instCodes);
            redirectAttributes.addAttribute("message", "병실현황판 전용 계정이 저장되었습니다.");
            redirectAttributes.addAttribute("viewerUsername", StringUtils.hasText(username) ? username.trim() : "");
            if (StringUtils.hasText(selectedInstCode)) {
                redirectAttributes.addAttribute("instCode", selectedInstCode.trim());
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            if (StringUtils.hasText(selectedInstCode)) {
                redirectAttributes.addAttribute("instCode", selectedInstCode.trim());
            }
            if (StringUtils.hasText(username)) {
                redirectAttributes.addAttribute("viewerUsername", username.trim());
            }
        }
        return "redirect:/admin";
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

    @PostMapping("/admin/integrations")
    public String saveIntegrations(
            @RequestParam("instCode") String instCode,
            @RequestParam(name = "enabledIntegrationCodes", required = false) List<String> enabledIntegrationCodes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminSession(session)) {
            return "redirect:/login";
        }
        try {
            storeService.saveInstitutionIntegrationAccess(instCode, enabledIntegrationCodes);
            redirectAttributes.addAttribute("message", "기관별 연동 설정이 저장되었습니다.");
            redirectAttributes.addAttribute("instCode", instCode);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("instCode", instCode);
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

    private void populateViewerAdminModel(Model model, String selectedInstCode, String viewerUsername) {
        List<RoomBoardViewerAccount> viewerAccounts = storeService.listRoomBoardViewerAccounts();
        model.addAttribute("viewerAccounts", viewerAccounts);
        model.addAttribute("viewerScopeInstitutions", storeService.listRoomBoardScopeCandidateInstitutions());

        RoomBoardViewerAccount editAccount = StringUtils.hasText(viewerUsername)
                ? storeService.findRoomBoardViewerAccount(viewerUsername.trim())
                : null;
        boolean editMode = editAccount != null;
        model.addAttribute("viewerEditMode", editMode);
        model.addAttribute("viewerFormUsername", editMode ? editAccount.getUsername() : "");
        model.addAttribute("viewerFormDisplayName", editMode ? editAccount.getDisplayName() : "");
        model.addAttribute("viewerFormUseYn", editMode ? editAccount.getUseYn() : "Y");
        model.addAttribute("viewerSelectedInstCodes", editMode ? editAccount.getScopeInstCodes() : List.of());
        model.addAttribute("viewerSelectedInstCode", selectedInstCode);
    }
}
