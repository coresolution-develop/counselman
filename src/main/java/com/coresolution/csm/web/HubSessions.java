package com.coresolution.csm.web;

import com.coresolution.csm.vo.HubMemberSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 허브 회원 세션 접근 유틸. 인터셉터와 컨트롤러가 동일한 규칙을 공유한다.
 *
 * <p>인증 차단선은 3중이다:
 * <ol>
 *   <li>{@code HubAuthInterceptor} — /hub/me/** 경로 진입 차단</li>
 *   <li>각 컨트롤러 진입부의 {@link #current(HttpSession)} null 가드</li>
 *   <li>모든 개인 데이터 쿼리의 {@code WHERE member_id = ?} (세션 id)</li>
 * </ol>
 * 인터셉터를 우회하더라도 2·3이 실제 데이터 노출을 막는다.
 */
public final class HubSessions {

    /** HttpSession 속성 키. csm 직원 세션("userInfo")과 분리된 네임스페이스. */
    public static final String ATTR = "hubMember";

    private HubSessions() {
    }

    /** 세션의 허브 회원을 반환하거나, 로그인하지 않았으면 null. */
    public static HubMemberSession current(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(ATTR);
        return value instanceof HubMemberSession member ? member : null;
    }

    public static HubMemberSession current(HttpServletRequest request) {
        return request == null ? null : current(request.getSession(false));
    }

    public static void store(HttpSession session, HubMemberSession member) {
        session.setAttribute(ATTR, member);
    }

    public static void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ATTR);
        }
    }
}
