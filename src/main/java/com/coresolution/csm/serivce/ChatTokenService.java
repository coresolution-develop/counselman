package com.coresolution.csm.serivce;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps opaque public chat tokens to canonical institution codes.
 * Tokens are stable per institution (issued once, never rotated) so embed URLs do not change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTokenService {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    private final Map<String, String> tokenToInst = new ConcurrentHashMap<>();
    private final Map<String, String> instToToken = new ConcurrentHashMap<>();

    /**
     * Returns the existing token for the given institution, creating one if absent.
     * Canonical inst is required (e.g., "FALH") — callers must canonicalize first.
     */
    public String getOrCreateToken(String canonicalInst) {
        if (canonicalInst == null || canonicalInst.isBlank()) return null;
        String cached = instToToken.get(canonicalInst);
        if (cached != null) return cached;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT token FROM csm.chat_inst_token WHERE inst = ? LIMIT 1",
            canonicalInst);
        if (!rows.isEmpty()) {
            String token = String.valueOf(rows.get(0).get("token"));
            cache(token, canonicalInst);
            return token;
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = generate();
            try {
                jdbcTemplate.update(
                    "INSERT INTO csm.chat_inst_token (token, inst) VALUES (?, ?)",
                    candidate, canonicalInst);
                cache(candidate, canonicalInst);
                return candidate;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // Collision (extremely unlikely with 16-char alphabet of 62 chars → 62^16 ≈ 4.7e28).
                // If unique violation is on inst (race), re-read.
                List<Map<String, Object>> race = jdbcTemplate.queryForList(
                    "SELECT token FROM csm.chat_inst_token WHERE inst = ? LIMIT 1",
                    canonicalInst);
                if (!race.isEmpty()) {
                    String token = String.valueOf(race.get(0).get("token"));
                    cache(token, canonicalInst);
                    return token;
                }
            }
        }
        log.warn("Failed to allocate chat token for inst={} after 5 attempts", canonicalInst);
        return null;
    }

    /**
     * Returns the public display name for an institution (e.g. "효사랑가족요양병원"),
     * or the inst code itself if no name is registered.
     */
    public String getInstName(String canonicalInst) {
        if (canonicalInst == null || canonicalInst.isBlank()) return "";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT inst_name FROM csm.mp_institution WHERE inst_code = ? LIMIT 1",
                canonicalInst);
            if (!rows.isEmpty()) {
                Object n = rows.get(0).get("inst_name");
                if (n != null && !n.toString().isBlank()) return n.toString();
            }
        } catch (Exception e) {
            log.debug("getInstName failed for inst={}: {}", canonicalInst, e.toString());
        }
        return canonicalInst;
    }

    /**
     * Resolves token to canonical inst, or null if unknown.
     */
    public String resolveInst(String token) {
        if (token == null || token.isBlank()) return null;
        if (!token.matches("^[A-Za-z0-9]{1,32}$")) return null;
        String cached = tokenToInst.get(token);
        if (cached != null) return cached;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT inst FROM csm.chat_inst_token WHERE token = ? LIMIT 1",
            token);
        if (rows.isEmpty()) return null;
        String inst = String.valueOf(rows.get(0).get("inst"));
        cache(token, inst);
        return inst;
    }

    private void cache(String token, String inst) {
        tokenToInst.put(token, inst);
        instToToken.put(inst, token);
    }

    private static String generate() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
