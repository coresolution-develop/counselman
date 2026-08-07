package com.coresolution.mediplat.web;

import com.coresolution.mediplat.model.FleetDriverPrincipal;

import jakarta.servlet.http.HttpSession;

/**
 * 운전자 기기 신원의 세션 저장/조회/삭제. 관리자 세션({@code mediplatUser})과 키를 분리한다.
 */
public final class FleetSessions {

    public static final String ATTRIBUTE = "fleetDriver";

    private FleetSessions() {
    }

    public static FleetDriverPrincipal current(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(ATTRIBUTE);
        return value instanceof FleetDriverPrincipal principal ? principal : null;
    }

    public static void store(HttpSession session, FleetDriverPrincipal principal) {
        session.setAttribute(ATTRIBUTE, principal);
    }

    public static void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }
}
