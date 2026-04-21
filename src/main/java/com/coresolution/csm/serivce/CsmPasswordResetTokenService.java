package com.coresolution.csm.serivce;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CsmPasswordResetTokenService {

    private final Map<String, ResetTokenContext> tokenStorage = new ConcurrentHashMap<>();
    private final Map<String, Long> expirationStorage = new ConcurrentHashMap<>();
    private final long tokenValidityDuration = TimeUnit.MINUTES.toMillis(1440); // 24시간

    public String generateToken(String email, String inst, String userId) {
        String token = UUID.randomUUID().toString();
        tokenStorage.put(token, new ResetTokenContext(normalize(email), normalize(inst), normalize(userId)));
        expirationStorage.put(token, System.currentTimeMillis() + tokenValidityDuration);
        return token;
    }

    public String generateToken(String email) {
        return generateToken(email, null, null);
    }

    public ResetTokenContext getTokenContext(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalizedToken = token.trim();
        if (!tokenStorage.containsKey(normalizedToken)) {
            return null;
        }
        Long expiresAt = expirationStorage.get(normalizedToken);
        if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
            tokenStorage.remove(normalizedToken);
            expirationStorage.remove(normalizedToken);
            return null;
        }
        return tokenStorage.get(normalizedToken);
    }

    public String getEmailByToken(String token) {
        ResetTokenContext context = getTokenContext(token);
        return context == null ? null : context.email();
    }

    public void invalidateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String normalizedToken = token.trim();
        tokenStorage.remove(normalizedToken);
        expirationStorage.remove(normalizedToken);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public record ResetTokenContext(String email, String inst, String userId) {
    }
}
