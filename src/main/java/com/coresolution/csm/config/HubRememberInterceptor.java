package com.coresolution.csm.config;

import org.springframework.web.servlet.HandlerInterceptor;

import com.coresolution.csm.serivce.HubMemberService;
import com.coresolution.csm.serivce.HubRememberService;
import com.coresolution.csm.vo.HubMember;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubRememberCookies;
import com.coresolution.csm.web.HubSessions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * "이 기기 기억하기" 자동 재로그인. 세션에 hubMember가 없고 HUB_REMEMBER 쿠키가 있으면
 * 토큰을 검증·회전해 세션을 복원한다. 공개 /links 진입 시에도 동작하도록 넓게 등록한다.
 *
 * <p>이 인터셉터는 절대 요청을 막지 않는다(복원만 시도). 인가 차단은 HubAuthInterceptor가 한다.
 * 인증 근거가 메모리 세션이 아니라 DB 토큰 + 쿠키이므로 서버 재시작/배포 후에도 복원된다.
 */
public class HubRememberInterceptor implements HandlerInterceptor {

    private final HubRememberService hubRememberService;
    private final HubMemberService hubMemberService;

    public HubRememberInterceptor(HubRememberService hubRememberService, HubMemberService hubMemberService) {
        this.hubRememberService = hubRememberService;
        this.hubMemberService = hubMemberService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null && HubSessions.current(session) != null) {
            return true; // 이미 로그인 — 아무것도 하지 않음
        }
        String cookieValue = HubRememberCookies.read(request);
        if (cookieValue == null) {
            return true; // 기억된 기기 아님
        }
        boolean secure = hubRememberService.isCookieSecure();
        HubRememberService.Result result = hubRememberService.validateAndRotate(
                cookieValue, request.getHeader("User-Agent"));
        if (!result.valid()) {
            HubRememberCookies.clear(request, response, secure); // 무효 토큰 → 쿠키 삭제
            return true;
        }
        HubMember member = hubMemberService.findActiveById(result.memberId());
        if (member == null) {
            HubRememberCookies.clear(request, response, secure);
            return true;
        }
        // 세션 복원 + 회전된 쿠키 재발급
        HubSessions.store(request.getSession(true), new HubMemberSession(
                member.getId(), member.getEmail(), member.getName(), member.getRole()));
        HubRememberCookies.write(request, response, result.newCookieValue(),
                hubRememberService.getCookieMaxAgeSeconds(), secure);
        return true;
    }
}
