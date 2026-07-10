package com.coresolution.mediplat.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 운전자 기기 기억 쿠키 읽기/쓰기. 속성: HttpOnly, Secure(설정), SameSite=Lax,
 * Path={@code /fleet}(운전자 경로로 스코프 제한). validator는 쿠키에만 있고 DB에는 해시만 저장된다.
 *
 * <p>이름 {@code FLEET_DEVICE}는 links의 {@code HUB_REMEMBER} 및 세션 쿠키(JSESSIONID)와 충돌하지 않는다.
 */
public final class FleetDeviceCookies {

    public static final String NAME = "FLEET_DEVICE";
    /** 운전자 경로로 한정. mediplat은 루트 서빙이라 컨텍스트 경로가 없다. */
    public static final String PATH = "/fleet";

    private FleetDeviceCookies() {
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

    public static void write(HttpServletResponse response, String value, long maxAgeSeconds, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(value, maxAgeSeconds, secure).toString());
    }

    /** 쿠키 즉시 만료(삭제). */
    public static void clear(HttpServletResponse response, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", 0, secure).toString());
    }

    private static ResponseCookie build(String value, long maxAgeSeconds, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
