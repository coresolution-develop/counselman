package com.coresolution.mediplat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Login routing asks moduplat with the adapter's request signature and fails open.
 */
class LoginRouteServiceTest {

    private static final String SECRET = "test-only-route-secret-0123456789abcdef";

    private HttpServer server;
    private final AtomicReference<String> answer = new AtomicReference<>("{\"url\":\"https://app.moduplat.com/cohs\"}");
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<HttpExchange> last = new AtomicReference<>();
    private volatile long delayMs = 0;
    private LoginRouteService service;
    private PlatformStoreService store;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            last.set(exchange);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        store = mock(PlatformStoreService.class);
        when(store.resolveInstCode("COHS")).thenReturn("COHS");
        when(store.resolveInstCode("코어요양병원")).thenReturn("COHS");
        service = new LoginRouteService(store);
        service.apiUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        service.secret = SECRET;
        service.allowedPrefix = "https://app.moduplat.com/";
        service.timeoutMs = 500;
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sign_matchesModuplat() {
        // Same vector as moduplat's signRoster (apps/api/src/core/legacy/legacy-roster.ts)
        assertThat(LoginRouteService.sign(SECRET, "GET", "/api/legacy/route?inst=COHS", "1700000000"))
                .isEqualTo("a03487c986cded8f35c973e6e98f05a308da4185ac37bb877cc23752c1553ffa");
    }

    @Test
    void routeFor_asksWithTheSignature_andResolvesInstitutionNames() {
        assertThat(service.routeFor("코어요양병원")).contains("https://app.moduplat.com/cohs");
        HttpExchange ex = last.get();
        assertThat(ex.getRequestURI().toString()).isEqualTo("/api/legacy/route?inst=COHS");
        String ts = ex.getRequestHeaders().getFirst("X-Legacy-Timestamp");
        assertThat(ex.getRequestHeaders().getFirst("X-Legacy-Signature"))
                .isEqualTo(LoginRouteService.sign(SECRET, "GET", "/api/legacy/route?inst=COHS", ts));
        // Cached for a while: the next login does not ask again
        assertThat(service.routeFor("COHS")).contains("https://app.moduplat.com/cohs");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void routeFor_failsOpen() {
        answer.set("{\"url\":null}");
        assertThat(service.routeFor("COHS")).isEmpty();
        // Unknown institution: not asked at all
        assertThat(service.routeFor("NOPE")).isEmpty();
    }

    @Test
    void routeFor_ignoresAnswersOutsideTheAllowedPrefix() {
        answer.set("{\"url\":\"https://evil.example/cohs\"}");
        assertThat(service.routeFor("COHS")).isEmpty();
    }

    @Test
    void routeFor_errorsAndTimeoutsKeepTheOldLogin() {
        status.set(404);
        assertThat(service.routeFor("COHS")).isEmpty();
        LoginRouteService slow = new LoginRouteService(store);
        slow.apiUrl = service.apiUrl;
        slow.secret = SECRET;
        slow.allowedPrefix = service.allowedPrefix;
        slow.timeoutMs = 200;
        status.set(200);
        delayMs = 1000;
        assertThat(slow.routeFor("COHS")).isEmpty();
    }

    @Test
    void notConfigured_neverAsks() {
        service.secret = "";
        assertThat(service.routeFor("COHS")).isEmpty();
        assertThat(calls.get()).isZero();
    }

    @Test
    void allow_limitsPerIpPerMinute() {
        long[] now = { 0 };
        service.clock = () -> now[0];
        for (int i = 0; i < 30; i++) {
            assertThat(service.allow("1.2.3.4")).isTrue();
        }
        assertThat(service.allow("1.2.3.4")).isFalse();
        assertThat(service.allow("5.6.7.8")).isTrue();
        now[0] = 60_000;
        assertThat(service.allow("1.2.3.4")).isTrue();
    }
}
