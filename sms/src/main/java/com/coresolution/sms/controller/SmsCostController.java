package com.coresolution.sms.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.coresolution.sms.model.SessionUser;
import com.coresolution.sms.service.SmsCostService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/sms")
public class SmsCostController {

    private final SmsCostService costService;
    private final String platformAdminInst;

    public SmsCostController(
            SmsCostService costService,
            @Value("${sms.platform-admin-inst:core}") String platformAdminInst) {
        this.costService = costService;
        this.platformAdminInst = platformAdminInst == null ? "core" : platformAdminInst.trim();
    }

    /** Per-institution cost for the session's institution (CounselMan usage auto-included). */
    @GetMapping("/cost")
    public Map<String, Object> cost(HttpSession session) {
        SessionUser user = SmsSession.require(session);
        return costService.buildInstCost(user.getInstCode());
    }

    /** Cross-institution aggregate — platform admin (core) only. */
    @GetMapping("/cost/platform")
    public Map<String, Object> platformCost(HttpSession session) {
        SessionUser user = SmsSession.require(session);
        if (!isPlatformAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "플랫폼 합계는 관리자만 조회할 수 있습니다.");
        }
        return costService.buildPlatformCost();
    }

    private boolean isPlatformAdmin(SessionUser user) {
        return user.getInstCode() != null && platformAdminInst.equalsIgnoreCase(user.getInstCode().trim());
    }
}
