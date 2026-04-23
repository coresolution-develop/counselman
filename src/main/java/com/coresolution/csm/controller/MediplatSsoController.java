package com.coresolution.csm.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.serivce.CsmSchemaBootstrapService;
import com.coresolution.csm.serivce.MediplatSsoService;
import com.coresolution.csm.serivce.PermissionResolver;
import com.coresolution.csm.vo.Userdata;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping
public class MediplatSsoController {

    private final MediplatSsoService mediplatSsoService;
    private final CsmAuthService cs;
    private final CsmSchemaBootstrapService schemaBootstrapService;
    private final PermissionResolver permissionResolver;

    @GetMapping({ "mediplat/sso/entry", "/mediplat/sso/entry" })
    public String mediplatSsoEntry(
            @RequestParam("inst") String inst,
            @RequestParam("userId") String userId,
            @RequestParam("expires") long expires,
            @RequestParam("signature") String signature,
            @RequestParam(name = "target", required = false) String target,
            HttpServletRequest request) {
        String normalizedInst = cs.resolveInst(inst);
        if (normalizedInst == null) {
            schemaBootstrapService.refreshFromPlatform();
            normalizedInst = cs.resolveInst(inst);
        }
        if (normalizedInst == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 기관코드입니다.");
        }

        String redirectTarget;
        try {
            redirectTarget = mediplatSsoService.validateAndResolveTarget(
                    normalizedInst,
                    userId,
                    expires,
                    target,
                    signature);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }

        if (!cs.isInstitutionAvailable(normalizedInst)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 기관은 CounselMan 사용이 중지되었습니다.");
        }

        if (isRoomBoardViewerTarget(redirectTarget)) {
            if (!cs.isRoomBoardCounselLinkEnabled(normalizedInst)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 기관은 병실현황판-입원상담 연동이 비활성화되었습니다.");
            }
            String roomBoardTarget = appendRoomBoardViewerToken(
                    redirectTarget,
                    normalizedInst,
                    userId,
                    expires,
                    mediplatSsoService.signRoomBoardViewer(normalizedInst, userId, expires));
            return "redirect:" + request.getContextPath() + roomBoardTarget;
        }

        Userdata info = cs.loadUserInfo(normalizedInst, userId);
        if (info == null) {
            schemaBootstrapService.refreshFromPlatform();
            info = cs.loadUserInfo(normalizedInst, userId);
        }
        if (!cs.isUserAvailable(info)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용 가능한 계정이 아닙니다.");
        }

        var auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                buildAuthorities(info, normalizedInst));
        auth.setDetails(new InstDetails(normalizedInst));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session = request.getSession(false);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("inst", normalizedInst);
        session.setAttribute("userInfo", info);
        session.setAttribute("instname", info.getUs_col_05());

        return "redirect:" + request.getContextPath() + redirectTarget;
    }

    private List<GrantedAuthority> buildAuthorities(Userdata info, String inst) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (info == null) return authorities;
        int col08 = info.getUs_col_08();
        if (col08 == 0) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
            for (String code : permissionResolver.allPermissions()) {
                authorities.add(new SimpleGrantedAuthority(code));
            }
            return authorities;
        }
        if (col08 == 1) {
            authorities.add(new SimpleGrantedAuthority("ROLE_INST_ADMIN"));
        }
        try {
            for (String code : permissionResolver.resolveUserPermissions(info.getUs_col_01(), inst)) {
                authorities.add(new SimpleGrantedAuthority(code));
            }
        } catch (Exception ignored) {}
        return authorities;
    }

    private boolean isRoomBoardViewerTarget(String redirectTarget) {
        if (!StringUtils.hasText(redirectTarget)) {
            return false;
        }
        try {
            URI uri = URI.create(redirectTarget.trim());
            if (!"/room-board".equals(uri.getPath())) {
                return false;
            }
            String query = uri.getQuery();
            if (!StringUtils.hasText(query)) {
                return false;
            }
            for (String token : query.split("&")) {
                if ("popup=1".equalsIgnoreCase(token.trim())) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String appendRoomBoardViewerToken(
            String redirectTarget,
            String inst,
            String userId,
            long expires,
            String signature) {
        StringBuilder sb = new StringBuilder(redirectTarget);
        sb.append(redirectTarget.contains("?") ? "&" : "?");
        sb.append("mpInst=").append(encode(inst));
        sb.append("&mpUser=").append(encode(userId));
        sb.append("&mpExpires=").append(expires);
        sb.append("&mpSignature=").append(encode(signature));
        return sb.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
