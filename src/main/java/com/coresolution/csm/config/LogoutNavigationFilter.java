package com.coresolution.csm.config;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GET logout links end the session only when the user actually navigates to them.
 *
 * A background request to the same url (a browser extension's fetch, link prefetch) used to log the user out first:
 * the redirect it got was blocked as a cross-origin fetch, and the user's own click then found no session and went to
 * the MediPlat login instead of the SSO return url. Browsers mark every request with {@code Sec-Fetch-Mode}; a
 * navigation is {@code navigate}. Anything else (fetch, no-cors, cors, same-origin, websocket) or a prefetch
 * ({@code Sec-Purpose}/{@code Purpose: prefetch}) gets 204 and the session is left alone. Requests without the
 * header (old browsers, tools) behave as before.
 *
 * Only the GET routes that end the session: {@code /logout} (PageController, includes the chat returnTo) and
 * {@code /room-board/return}. POST /logout (Spring Security LogoutFilter) needs a CSRF token and is not covered here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LogoutNavigationFilter extends OncePerRequestFilter {

    private static final Set<String> LOGOUT_PATHS = Set.of("/logout", "/room-board/return");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod()) || !LOGOUT_PATHS.contains(pathOf(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isBackground(request)) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            response.setHeader("Cache-Control", "no-store");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Not a user navigation: a fetch/subresource, or a prefetch. No header at all counts as a navigation */
    static boolean isBackground(HttpServletRequest request) {
        String mode = request.getHeader("Sec-Fetch-Mode");
        if (StringUtils.hasText(mode) && !"navigate".equals(mode.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return isPrefetch(request.getHeader("Sec-Purpose")) || isPrefetch(request.getHeader("Purpose"));
    }

    private static boolean isPrefetch(String purpose) {
        return StringUtils.hasText(purpose) && purpose.toLowerCase(Locale.ROOT).contains("prefetch");
    }

    /** The path inside the app (without the /csm context path) */
    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = StringUtils.hasText(context) && uri.startsWith(context) ? uri.substring(context.length()) : uri;
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
