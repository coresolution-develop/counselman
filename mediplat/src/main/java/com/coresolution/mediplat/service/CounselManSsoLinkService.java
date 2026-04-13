package com.coresolution.mediplat.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.coresolution.mediplat.model.PlatformService;
import com.coresolution.mediplat.model.PlatformSessionUser;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class CounselManSsoLinkService {

    @Value("${platform.bootstrap.counselman-base-url:http://localhost:8081/csm}")
    private String configuredCounselManBaseUrl;

    @Value("${platform.counselman.sso-shared-secret:change-me}")
    private String sharedSecret;

    @Value("${platform.counselman.sso-expire-seconds:60}")
    private long expireSeconds;

    public String createLaunchUrl(PlatformService service, PlatformSessionUser user) {
        String target = user.isPlatformAdmin() ? service.getAdminTarget() : service.getUserTarget();
        String targetToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(target.getBytes(StandardCharsets.UTF_8));
        long expires = Instant.now().getEpochSecond() + expireSeconds;
        String signature = sign(user.getInstCode(), user.getUsername(), expires, targetToken);
        return resolveBaseUrl(service.getBaseUrl()) + service.getSsoEntryPath()
                + "?inst=" + encode(user.getInstCode())
                + "&userId=" + encode(user.getUsername())
                + "&expires=" + expires
                + "&target=" + encode(targetToken)
                + "&signature=" + encode(signature);
    }

    private String sign(String instCode, String username, long expires, String targetToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = "inst=" + instCode + "&userId=" + username + "&expires=" + expires + "&target=" + targetToken;
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("CounselMan SSO signature generation failed.", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolveBaseUrl(String baseUrl) {
        String normalizedBaseUrl = trimTrailingSlash(baseUrl);
        if (!isLoopbackUrl(normalizedBaseUrl)) {
            return normalizedBaseUrl;
        }

        String configuredBaseUrl = trimTrailingSlash(configuredCounselManBaseUrl);
        HttpServletRequest request = currentRequest();
        if (StringUtils.hasText(configuredBaseUrl)) {
            if (request == null || isLoopbackHost(request.getServerName())) {
                return configuredBaseUrl;
            }
            if (!isLoopbackUrl(configuredBaseUrl)) {
                return configuredBaseUrl;
            }
        }

        if (request == null) {
            return StringUtils.hasText(configuredBaseUrl) ? configuredBaseUrl : normalizedBaseUrl;
        }

        String rewriteSource = StringUtils.hasText(configuredBaseUrl) ? configuredBaseUrl : normalizedBaseUrl;
        URI original = URI.create(rewriteSource);
        String scheme = StringUtils.hasText(request.getScheme()) ? request.getScheme() : original.getScheme();
        String host = StringUtils.hasText(request.getServerName()) ? request.getServerName() : original.getHost();
        int port = original.getPort() > 0 ? original.getPort() : request.getServerPort();

        StringBuilder rebuilt = new StringBuilder();
        rebuilt.append(scheme).append("://").append(host);
        if (shouldAppendPort(scheme, port)) {
            rebuilt.append(':').append(port);
        }
        if (StringUtils.hasText(original.getPath())) {
            rebuilt.append(original.getPath());
        }
        return rebuilt.toString();
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private boolean isLoopbackUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            String host = URI.create(value).getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "0.0.0.0".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isLoopbackHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "0.0.0.0".equals(host);
    }

    private boolean shouldAppendPort(String scheme, int port) {
        if (port <= 0) {
            return false;
        }
        if ("http".equalsIgnoreCase(scheme) && port == 80) {
            return false;
        }
        if ("https".equalsIgnoreCase(scheme) && port == 443) {
            return false;
        }
        return true;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
