package com.coresolution.mediplat.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coresolution.mediplat.model.PlatformService;
import com.coresolution.mediplat.model.PlatformSessionUser;

@Service
public class CounselManSsoLinkService {

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
        return service.getBaseUrl() + service.getSsoEntryPath()
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
}
