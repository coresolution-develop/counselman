package com.coresolution.csm.serivce;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

@Service
public class CsmPasswordResetTokenService {

    private final Map<String, String> tokenStorage = new ConcurrentHashMap<>();
    private final Map<String, Long> expirationStorage = new ConcurrentHashMap<>();
    private final long tokenValidityDuration = TimeUnit.MINUTES.toMillis(1440); // 24시간

    public String generateToken(String email) {
        String token = UUID.randomUUID().toString();
        tokenStorage.put(token, email);
        expirationStorage.put(token, System.currentTimeMillis() + tokenValidityDuration);
        return token;
    }

    public String getEmailByToken(String token) {
        if (!tokenStorage.containsKey(token)) {
            return null;
        }
        Long expiresAt = expirationStorage.get(token);
        if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
            tokenStorage.remove(token);
            expirationStorage.remove(token);
            return null;
        }
        return tokenStorage.get(token);
    }

    public void invalidateToken(String token) {
        tokenStorage.remove(token);
        expirationStorage.remove(token);
    }
}
