package com.coresolution.csm.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Sends a user back to the platform they came from when they log out (moduplat bridge).
 *
 * moduplat appends {@code from=<return url>} to the SSO link, outside the signed payload. Only when that value
 * equals one entry of {@code csm.sso.return-allow} (comma list) do we store <b>the configured entry</b> in the
 * session and redirect there on logout. The request value itself is never used as a redirect target, so this
 * cannot become an open redirect. Without the marker (entered from MediPlat) logout goes to MediPlat as before.
 */
public final class SsoReturn {

    /** Session attribute holding the return url (always a configured entry) */
    public static final String SESSION_KEY = "csmSsoReturnUrl";
    /** Request attribute the logout filter copies the url into before the session is invalidated */
    public static final String REQUEST_KEY = SsoReturn.class.getName() + ".url";

    private SsoReturn() {
    }

    /** Trailing slashes removed and lower-cased (comparison only) */
    private static String key(String url) {
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * The allow-list entry (as configured) that equals {@code from}, or empty.
     * Entries must be https (plain http only for localhost, for local development).
     */
    public static Optional<String> allowed(String from, String allowCsv) {
        if (!StringUtils.hasText(from) || !StringUtils.hasText(allowCsv)) {
            return Optional.empty();
        }
        String wanted = key(from);
        return Arrays.stream(allowCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(SsoReturn::safeEntry)
                .filter(entry -> key(entry).equals(wanted))
                .findFirst();
    }

    private static boolean safeEntry(String entry) {
        String lower = entry.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://")
                || lower.startsWith("http://localhost")
                || lower.startsWith("http://127.0.0.1");
    }

    /** On SSO entry: remember an allowed origin, otherwise clear it (the same browser may come back through MediPlat) */
    public static void remember(HttpSession session, String from, String allowCsv) {
        Optional<String> url = allowed(from, allowCsv);
        if (url.isPresent()) {
            session.setAttribute(SESSION_KEY, url.get());
        } else {
            session.removeAttribute(SESSION_KEY);
        }
    }

    /**
     * Where to go after logout: the remembered url, only if it is still in the current allow-list (config may
     * have changed). Must be called <b>before</b> the session is invalidated.
     */
    public static Optional<String> recall(HttpServletRequest request, String allowCsv) {
        Object kept = request.getAttribute(REQUEST_KEY);
        if (kept == null) {
            HttpSession session = request.getSession(false);
            kept = session == null ? null : session.getAttribute(SESSION_KEY);
        }
        return kept instanceof String s ? allowed(s, allowCsv) : Optional.empty();
    }
}
