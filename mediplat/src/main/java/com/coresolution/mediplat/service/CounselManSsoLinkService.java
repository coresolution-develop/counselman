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
        return createLaunchUrl(service, user, user.getInstCode(), target);
    }

    public String createLaunchUrl(
            PlatformService service,
            PlatformSessionUser user,
            String instCode,
            String targetPath) {
        String resolvedInstCode = StringUtils.hasText(instCode) ? instCode.trim() : user.getInstCode();
        String resolvedTargetPath = StringUtils.hasText(targetPath)
                ? targetPath.trim()
                : (user.isPlatformAdmin() ? service.getAdminTarget() : service.getUserTarget());
        String resolvedBaseUrl = resolveBaseUrl(service.getBaseUrl());
        String normalizedTargetPath = stripContextPath(resolvedTargetPath, resolvedBaseUrl);
        String targetToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalizedTargetPath.getBytes(StandardCharsets.UTF_8));
        long expires = Instant.now().getEpochSecond() + expireSeconds;
        String signature = sign(resolvedInstCode, user.getUsername(), expires, targetToken);
        String resolvedEntryPath = normalizeEntryPath(service.getSsoEntryPath());
        String dedupedEntryPath = dedupeEntryPath(resolvedBaseUrl, resolvedEntryPath);
        return resolvedBaseUrl + dedupedEntryPath
                + "?inst=" + encode(resolvedInstCode)
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
        HttpServletRequest request = currentRequest();
        String upgradedBaseUrl = upgradeToHttpsWhenNeeded(normalizedBaseUrl, request);
        if (!isLoopbackUrl(upgradedBaseUrl)) {
            return upgradedBaseUrl;
        }

        String configuredBaseUrl = trimTrailingSlash(configuredCounselManBaseUrl);
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

    private String normalizeEntryPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/mediplat/sso/entry";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String dedupeEntryPath(String baseUrl, String entryPath) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(entryPath)) {
            return entryPath;
        }
        try {
            URI uri = URI.create(baseUrl);
            String basePath = trimTrailingSlash(uri.getPath());
            if (!StringUtils.hasText(basePath) || "/".equals(basePath)) {
                return entryPath;
            }
            if (entryPath.equals(basePath)) {
                return "/";
            }
            String prefix = basePath + "/";
            if (entryPath.startsWith(prefix)) {
                String deduped = entryPath.substring(basePath.length());
                return StringUtils.hasText(deduped) ? deduped : "/";
            }
            return entryPath;
        } catch (IllegalArgumentException e) {
            return entryPath;
        }
    }

    private String stripContextPath(String targetPath, String baseUrl) {
        if (!StringUtils.hasText(targetPath) || !StringUtils.hasText(baseUrl)) {
            return targetPath;
        }
        try {
            String contextPath = trimTrailingSlash(URI.create(baseUrl).getPath());
            if (!StringUtils.hasText(contextPath) || "/".equals(contextPath)) {
                return targetPath;
            }
            if (targetPath.equals(contextPath)) {
                return "/";
            }
            String prefix = contextPath + "/";
            if (targetPath.startsWith(prefix)) {
                return targetPath.substring(contextPath.length());
            }
        } catch (IllegalArgumentException e) {
            // ignore
        }
        return targetPath;
    }

    private String upgradeToHttpsWhenNeeded(String baseUrl, HttpServletRequest request) {
        if (!StringUtils.hasText(baseUrl) || request == null) {
            return baseUrl;
        }
        if (!"https".equalsIgnoreCase(request.getScheme())) {
            return baseUrl;
        }
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(scheme) || !StringUtils.hasText(host)) {
                return baseUrl;
            }
            String requestHost = request.getServerName();
            if (!StringUtils.hasText(requestHost) || !host.equalsIgnoreCase(requestHost.trim())) {
                return baseUrl;
            }
            int port = uri.getPort();
            StringBuilder rebuilt = new StringBuilder("https://").append(host);
            if (port > 0 && port != 80 && port != 443) {
                rebuilt.append(':').append(port);
            }
            if (StringUtils.hasText(uri.getPath())) {
                rebuilt.append(uri.getPath());
            }
            if (StringUtils.hasText(uri.getQuery())) {
                rebuilt.append('?').append(uri.getQuery());
            }
            if (StringUtils.hasText(uri.getFragment())) {
                rebuilt.append('#').append(uri.getFragment());
            }
            return rebuilt.toString();
        } catch (IllegalArgumentException e) {
            return baseUrl;
        }
    }
}
