package com.coresolution.csm.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.vo.Userdata;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/login")
public class LoginController {
    private final CsmAuthService cs; // 기존 cs.* 서비스(인터페이스 동일)
    @Value("${login.aes-key}")
    private String aesKey;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/post")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> postlogin(
            @RequestParam("us_col_02") String username,
            @RequestParam("us_col_03") String password,
            @RequestParam("us_col_04") String instInput,
            HttpServletRequest request) {

        var token = new UsernamePasswordAuthenticationToken(username, password);
        token.setDetails(new InstDetails(instInput)); // details에 기관코드/기관명 입력값 심음

        try {
            Authentication auth = authenticationManager.authenticate(token);

            // SecurityContext 저장
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            request.changeSessionId();
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            // 인증 Provider에서 정규화된 기관코드를 details로 다시 태워주므로 우선 사용
            String normalizedInst = extractResolvedInst(auth, instInput);
            session.setAttribute("inst", normalizedInst);

            // 로그인 사용자 정보 세션 저장 (기관코드 대소문자 이슈 대비 fallback)
            Userdata info = cs.loadUserInfo(normalizedInst, username);
            if (info == null && instInput != null) {
                String resolvedByInput = cs.resolveInst(instInput);
                if (resolvedByInput != null && !resolvedByInput.equalsIgnoreCase(normalizedInst)) {
                    info = cs.loadUserInfo(resolvedByInput, username);
                }
            }
            session.setAttribute("userInfo", info);
            if (info != null) {
                session.setAttribute("instname", info.getUs_col_05());
            }
            log.info("login success: sessionId={}, inst={}, userInfoExists={}",
                    session.getId(), session.getAttribute("inst"), info != null);
            if (info == null) {
                log.warn("userInfo is null after login. inst={}, username={}", normalizedInst, username);
            }

            // (선택) 프론트에서 그대로 따라가도록 redirect URL도 같이 내려주기
            String redirect = "core".equalsIgnoreCase(normalizedInst)
                    ? request.getContextPath() + "/core/admin"
                    : request.getContextPath() + "/counsel/list?page=1&perPageNum=10&comment=";
            return ResponseEntity.ok(Map.of("result", "1", "message", "로그인 성공", "redirect", redirect));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401)
                    .body(Map.of("result", "0", "message", "아이디/비밀번호/기관코드(기관명)가 올바르지 않습니다."));
        }
    }

    private String extractResolvedInst(Authentication auth, String instInput) {
        if (auth != null && auth.getDetails() instanceof InstDetails instDetails) {
            String resolved = instDetails.normalized();
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        String fallback = cs.resolveInst(instInput);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return new InstDetails(instInput).normalized();
    }
}
