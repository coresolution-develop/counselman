package com.coresolution.links.web;

import java.net.URI;

/**
 * 허브 사용자 입력 URL 검증 (가드레일 ②).
 * http/https 스킴 + 도메인 권한부만 허용한다 — javascript:, data:, 상대경로 등
 * 오픈 리다이렉트·XSS 벡터를 차단한다. CompanyLinkService.normalizeUrl과 동일한 규칙.
 */
public final class HubUrls {

    private HubUrls() {
    }

    /** http(s) URL이면 그대로 반환, 아니면 IllegalArgumentException. */
    public static String normalizeHttpUrl(String url) {
        if (url == null || url.trim().isBlank()) {
            throw new IllegalArgumentException("URL을 입력해주세요.");
        }
        String value = url.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL 형식이 올바르지 않습니다.");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("URL은 http 또는 https로 시작해야 합니다.");
        }
        if (!hasAuthority(uri)) {
            throw new IllegalArgumentException("URL에 도메인이 필요합니다.");
        }
        return value;
    }

    private static boolean hasAuthority(URI uri) {
        if (uri.getHost() != null && !uri.getHost().isBlank()) {
            return true;
        }
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank()) {
            return false;
        }
        String hostAndPort = authority;
        int userInfoEnd = hostAndPort.lastIndexOf('@');
        if (userInfoEnd >= 0) {
            hostAndPort = hostAndPort.substring(userInfoEnd + 1);
        }
        if (hostAndPort.startsWith("[")) {
            return hostAndPort.contains("]");
        }
        int portStart = hostAndPort.lastIndexOf(':');
        String host = portStart >= 0 ? hostAndPort.substring(0, portStart) : hostAndPort;
        return !host.isBlank();
    }
}
