package com.coresolution.links.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "이 기기 기억하기" 쿠키 읽기/쓰기. 속성: HttpOnly, Secure(설정), SameSite=Lax,
 * Path=컨텍스트 경로(/csm). validator는 쿠키에만 있고 DB에는 해시만 저장된다.
 */
public final class HubRememberCookies {

    public static final String NAME = "HUB_REMEMBER";

    private HubRememberCookies() {
    }

    public static String read(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static void write(HttpServletRequest request, HttpServletResponse response,
            String value, long maxAgeSeconds, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(request, value, maxAgeSeconds, secure).toString());
    }

    /** 쿠키 즉시 만료(삭제). */
    public static void clear(HttpServletRequest request, HttpServletResponse response, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(request, "", 0, secure).toString());
    }

    private static ResponseCookie build(HttpServletRequest request, String value, long maxAgeSeconds, boolean secure) {
        String path = StringUtils.hasText(request.getContextPath()) ? request.getContextPath() : "/";
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
