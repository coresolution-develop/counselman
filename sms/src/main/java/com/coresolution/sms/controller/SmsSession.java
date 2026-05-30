package com.coresolution.sms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.coresolution.sms.model.SessionUser;

import jakarta.servlet.http.HttpSession;

/** Shared session-access helpers for the SMS controllers. */
final class SmsSession {

    static final String SESSION_USER = "smsUser";

    private SmsSession() {
    }

    static SessionUser current(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(SESSION_USER);
        return value instanceof SessionUser user ? user : null;
    }

    /** Returns the session user or throws 401 (for API endpoints). */
    static SessionUser require(HttpSession session) {
        SessionUser user = current(session);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다.");
        }
        return user;
    }
}
