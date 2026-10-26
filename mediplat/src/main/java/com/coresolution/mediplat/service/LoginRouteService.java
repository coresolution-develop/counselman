package com.coresolution.mediplat.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sends staff of hospitals that moved to moduplat to their moduplat login address (moduplat bridge, option A).
 *
 * The MediPlat server asks moduplat {@code GET /api/legacy/route?inst=<code>} with the same request signature the
 * legacy adapter uses (HMAC-SHA256 over "GET\n<path?query>\n<unix seconds>", secret shared with moduplat, IP allow-list
 * on the moduplat side). Fail open: any error, timeout or unexpected answer keeps the old MediPlat login.
 * The answer is used only when it starts with the configured moduplat address, so it cannot become an open redirect.
 */
@Service
public class LoginRouteService {

    private static final Logger log = LoggerFactory.getLogger(LoginRouteService.class);
    private static final long CACHE_MILLIS = 30_000;
    private static final int LIMIT_PER_MINUTE = 30;

    private final PlatformStoreService storeService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private HttpClient httpClient;
    LongSupplier clock = System::currentTimeMillis;

    /** moduplat API address without a path, e.g. https://eval.moduplat.com. Empty turns routing off */
    @Value("${mediplat.route.api-url:}")
    String apiUrl;
    @Value("${mediplat.route.secret:}")
    String secret;
    /** Only answers starting with this are followed, e.g. https://app.moduplat.com/ */
    @Value("${mediplat.route.allowed-prefix:}")
    String allowedPrefix;
    @Value("${mediplat.route.timeout-ms:1500}")
    long timeoutMs;
    /** Lets an admin keep the old login with ?legacy=1 */
    @Value("${mediplat.route.bypass-enabled:false}")
    boolean bypassEnabled;

    private record Cached(Optional<String> url, long until) {
    }

    private static final class Window {
        long start;
        int count;
    }

    public LoginRouteService(PlatformStoreService storeService) {
        this.storeService = storeService;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiUrl) && StringUtils.hasText(secret) && StringUtils.hasText(allowedPrefix);
    }

    public boolean isBypassEnabled() {
        return bypassEnabled;
    }

    /** Per-IP limit for the public lookup used by the login page. Over the limit the page simply keeps the old login */
    public boolean allow(String ip) {
        long now = clock.getAsLong();
        Window w = windows.computeIfAbsent(ip == null ? "-" : ip, k -> new Window());
        synchronized (w) {
            if (now - w.start >= 60_000) {
                w.start = now;
                w.count = 0;
            }
            return ++w.count <= LIMIT_PER_MINUTE;
        }
    }

    /** Where staff entering this institution code or name should log in, or empty to keep the old login */
    public Optional<String> routeFor(String instCodeOrName) {
        if (!isConfigured() || !StringUtils.hasText(instCodeOrName)) {
            return Optional.empty();
        }
        String inst = storeService.resolveInstCode(instCodeOrName);
        if (!StringUtils.hasText(inst)) {
            return Optional.empty();
        }
        long now = clock.getAsLong();
        Cached hit = cache.get(inst);
        if (hit != null && hit.until() > now) {
            return hit.url();
        }
        Optional<String> url = ask(inst);
        cache.put(inst, new Cached(url, now + CACHE_MILLIS));
        return url;
    }

    static String sign(String secret, String method, String pathWithQuery, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = method + "\n" + pathWithQuery + "\n" + timestamp;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private Optional<String> ask(String inst) {
        try {
            String base = apiUrl.trim().replaceAll("/+$", "");
            String pathWithQuery = "/api/legacy/route?inst=" + URLEncoder.encode(inst, StandardCharsets.UTF_8);
            String ts = String.valueOf(Instant.ofEpochMilli(clock.getAsLong()).getEpochSecond());
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + pathWithQuery))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .header("X-Legacy-Timestamp", ts)
                    .header("X-Legacy-Signature", sign(secret, "GET", pathWithQuery, ts))
                    .GET()
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[login-route] moduplat answered {} — keeping the old login", response.statusCode());
                return Optional.empty();
            }
            JsonNode url = objectMapper.readTree(response.body()).get("url");
            if (url == null || !url.isTextual()) {
                return Optional.empty();
            }
            String value = url.asText();
            if (!value.startsWith(allowedPrefix.trim())) {
                log.warn("[login-route] answer outside mediplat.route.allowed-prefix — ignored");
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (Exception e) {
            log.warn("[login-route] moduplat lookup failed ({}) — keeping the old login", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private HttpClient client() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        }
        return httpClient;
    }
}
