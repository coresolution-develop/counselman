package com.coresolution.csm.serivce;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class ExternalSmsGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ExternalSmsGatewayService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter expiredFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${sms.bizppurio.token-url:https://api.bizppurio.com/v1/token}")
    private String tokenUrl;

    @Value("${sms.bizppurio.message-url:https://api.bizppurio.com/v3/message}")
    private String messageUrl;

    @Value("${sms.bizppurio.account:}")
    private String configuredAccount;

    @Value("${sms.bizppurio.username:}")
    private String username;

    @Value("${sms.bizppurio.password:}")
    private String password;

    private volatile String token;
    private volatile Instant tokenExpiry;

    /**
     * 서버 기동 시점에 토큰을 미리 발급 시도한다.
     * 자격 정보가 없으면 경고만 남기고 런타임 전송 시점 검증에 맡긴다.
     */
    @PostConstruct
    public void warmupTokenOnStartup() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("[sms] Bizppurio username/password is empty at startup. token warmup skipped.");
            return;
        }
        try {
            getToken();
            log.info("[sms] Bizppurio token warmup success. expiresAt={}", tokenExpiry);
        } catch (Exception e) {
            log.error("[sms] Bizppurio token warmup failed", e);
        }
    }

    /**
     * 1시간마다 토큰 상태를 점검한다.
     * 만료 임박(60초 이전) 또는 토큰 미보유 상태에서만 실제 재발급 호출이 수행된다.
     */
    @Scheduled(fixedDelayString = "${sms.bizppurio.token-check-ms:3600000}")
    public void refreshTokenPeriodically() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        try {
            getToken();
        } catch (Exception e) {
            log.error("[sms] Bizppurio scheduled token refresh failed", e);
        }
    }

    public Map<String, Object> send(Map<String, Object> payload) throws Exception {
        String account = !safeString(configuredAccount).isBlank()
                ? safeString(configuredAccount)
                : safeString(payload.get("account"));
        String refkey = safeString(payload.get("refkey"));
        String type = safeString(payload.get("type")).toLowerCase(Locale.ROOT);
        String from = safeString(payload.get("from"));
        String to = safeString(payload.get("to"));

        if (account.isBlank()) {
            throw new IllegalStateException("Bizppurio account is not configured. Set sms.bizppurio.account.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> content = payload.get("content") instanceof Map
                ? (Map<String, Object>) payload.get("content")
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> typedContent = content.get(type) instanceof Map
                ? (Map<String, Object>) content.get(type)
                : Map.of();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("account", account);
        requestBody.put("refkey", refkey);
        // Bizppurio는 type 값을 소문자(sms/lms/mms)로 받는다.
        requestBody.put("type", type);
        requestBody.put("from", from);
        requestBody.put("to", to);

        Object sendtime = payload.get("sendtime");
        if (sendtime != null && !String.valueOf(sendtime).isBlank()) {
            requestBody.put("sendtime", Long.parseLong(String.valueOf(sendtime)));
        }

        Map<String, Object> normalizedTyped = new HashMap<>();
        normalizedTyped.put("message", safeString(typedContent.get("message")));
        if ("lms".equals(type) || "mms".equals(type)) {
            normalizedTyped.put("subject", safeString(typedContent.get("subject")));
        }
        if ("mms".equals(type)) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> files = typedContent.get("file") instanceof List
                    ? (List<Map<String, String>>) typedContent.get("file")
                    : List.of();
            normalizedTyped.put("file", files);
        }
        requestBody.put("content", Map.of(type, normalizedTyped));

        String bearer = getToken();
        HttpResult result = postJson(messageUrl, requestBody, Map.of("Authorization", "Bearer " + bearer));
        Map<String, Object> body = parseBody(result.body);
        body.put("_http_status", result.status);
        body.put("_raw", result.body);
        return body;
    }

    private synchronized String getToken() throws Exception {
        if (token != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return token;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Bizppurio credentials are not configured. Set sms.bizppurio.username/password.");
        }
        String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpResult result = postJson(tokenUrl, Map.of(), Map.of("Authorization", "Basic " + basic));
        Map<String, Object> body = parseBody(result.body);
        String code = safeString(body.get("code"));
        String desc = safeString(body.get("description"));
        if ("3010".equals(code)) {
            throw new IllegalStateException("Bizppurio IP 차단(3010): 발신 서버 공인 IP를 Bizppurio 화이트리스트에 등록해야 합니다. description=" + desc);
        }
        String newToken = firstNonBlank(body, "accesstoken", "accessToken", "token");
        if (newToken == null) {
            throw new IllegalStateException("토큰 발급 실패: " + result.body);
        }
        String expired = safeString(body.get("expired"));
        if (!expired.isBlank()) {
            LocalDateTime dt = LocalDateTime.parse(expired, expiredFmt);
            tokenExpiry = dt.atZone(ZoneId.systemDefault()).toInstant();
        } else {
            tokenExpiry = Instant.now().plusSeconds(23 * 3600);
        }
        token = newToken;
        return token;
    }

    private HttpResult postJson(String url, Map<String, Object> payload, Map<String, String> headers) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept-Charset", "UTF-8");
        for (Map.Entry<String, String> e : headers.entrySet()) {
            conn.setRequestProperty(e.getKey(), e.getValue());
        }
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);

        String body = objectMapper.writeValueAsString(payload);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
        }
        conn.disconnect();
        return new HttpResult(status, sb.toString());
    }

    private Map<String, Object> parseBody(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("description", "fail");
            raw.put("message", json);
            return raw;
        }
    }

    private String safeString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            String v = safeString(map.get(k));
            if (!v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static final class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
