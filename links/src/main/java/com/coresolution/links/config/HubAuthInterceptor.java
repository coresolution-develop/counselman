package com.coresolution.links.config;

import org.springframework.web.servlet.HandlerInterceptor;

import com.coresolution.links.web.HubSessions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * /hub/me/** 진입 시 허브 회원 세션을 강제하는 1차 차단선.
 *
 * <p>이것만으로 인가를 끝내지 않는다 — 컨트롤러 진입부 null 가드와
 * 개인 쿼리의 member_id 필터가 실제 차단선이다(가드레일 ①). 인터셉터는
 * 미로그인 사용자를 로그인 화면으로 보내는 UX 역할에 가깝다.
 *
 * <p>AJAX(fetch) 요청은 302 리다이렉트 대신 401을 반환해 호출 측이
 * HTML 로그인 페이지를 본문으로 받지 않도록 한다.
 */
public class HubAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HubSessions.current(request) != null) {
            return true;
        }
        if (isAjaxRequest(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다.");
            return false;
        }
        response.sendRedirect(request.getContextPath() + "/hub/login");
        return false;
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }
}
