package com.coresolution.csm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.csm.serivce.PlatformUsageClient;
import com.coresolution.csm.serivce.SmsUsageOutboxService;
import com.coresolution.csm.serivce.SmsUsageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * 전송 스케줄러를 <b>실제 HTTP 서버 + 실제 MySQL</b> 로 검증한다 (CSM-4).
 *
 * <p>── 왜 목이 아니라 진짜 서버인가 ──
 * 검증할 것이 <b>상태 코드에 따른 분기</b>다. 4xx 는 영구 실패, 5xx 는 재시도.
 * 목으로는 "내가 정한 대로 분기한다" 만 확인되고,
 * <b>HTTP 계층이 실제로 그 코드를 그렇게 전달하는지</b>는 검증되지 않는다.
 */
@Tag("integration")
class SmsUsageSenderIntegrationTest {

    private static final int TEST_PORT = 3309;
    private static final String URL =
            "jdbc:mysql://127.0.0.1:" + TEST_PORT + "/csm?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=Asia/Seoul&characterEncoding=UTF-8";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JdbcTemplate jdbc;
    private static HttpServer server;
    private static SmsUsageSender sender;
    private static SmsUsageOutboxService outbox;

    /** 다음 요청에 돌려줄 상태 코드. 테스트마다 바꾼다. */
    private static volatile int responseStatus = 200;

    /** 받은 요청 본문. 무엇을 보냈는지 확인한다. */
    private static final List<String> received = new ArrayList<>();

    private static final AtomicInteger requestCount = new AtomicInteger();

    @BeforeAll
    static void setUp() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(URL, "root", "csm_test_pw");
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        try {
            new JdbcTemplate(ds).queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException e) {
            assumeTrue(false, "csm-test-mysql(" + TEST_PORT + ") 없음 — skip");
            return;
        }

        com.coresolution.csm.support.TestDatabaseGuard.installMarker(ds, TEST_PORT);
        com.coresolution.csm.support.TestDatabaseGuard.assertTestDatabase(ds);

        jdbc = new JdbcTemplate(ds);
        jdbc.execute(ddl("csm\\.sms_batch"));
        jdbc.execute(ddl("csm\\.sms_usage_outbox"));
        jdbc.execute(ddl("csm\\.sms_usage_heartbeat"));
        addPriceVersionColumn();

        // 포트 0 = OS 가 빈 포트를 고른다. 고정 포트를 쓰면 다른 프로젝트와 부딪힌다.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/usage-events", exchange -> {
            requestCount.incrementAndGet();
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        PlatformUsageClient client = new PlatformUsageClient();
        ReflectionTestUtils.setField(client, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "requestTimeoutMs", 3000);

        outbox = new SmsUsageOutboxService(jdbc, MAPPER);
        sender = new SmsUsageSender(outbox, client, jdbc);
        ReflectionTestUtils.setField(sender, "batchSize", 50);
        ReflectionTestUtils.setField(sender, "recoverDelayMinutes", 10);
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String ddl(String tablePattern) throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/coresolution/csm/serivce/CsmSchemaBootstrapService.java"));
        Matcher m = Pattern.compile(
                "(CREATE TABLE IF NOT EXISTS " + tablePattern + " \\(.*?utf8mb4_0900_ai_ci)",
                Pattern.DOTALL).matcher(source);
        assertThat(m.find()).as("%s DDL 을 찾지 못했다", tablePattern).isTrue();
        return m.group(1);
    }

    private static void addPriceVersionColumn() {
        try {
            jdbc.execute("ALTER TABLE csm.sms_batch ADD COLUMN price_version INT NULL");
        } catch (Exception e) {
            // 이미 있다.
        }
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM csm.sms_usage_outbox");
        jdbc.update("DELETE FROM csm.sms_batch");
        jdbc.update("DELETE FROM csm.sms_usage_heartbeat");
        received.clear();
        requestCount.set(0);
        responseStatus = 200;
    }

    // ── 정상 전송 ─────────────────────────────────────────────

    @Test
    void 성공하면_보낸_시각을_기록하고_다시_보내지_않는다() {
        queue("b-ok");

        sender.run();
        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(sentAt("b-ok")).isNotNull();

        sender.run();
        assertThat(requestCount.get())
                .as("이미 보낸 건을 다시 보내면 플랫폼에서 중복으로 잡힌다")
                .isEqualTo(1);
    }

    /**
     * ⭐ <b>outbox 에 저장된 payload 를 그대로 보낸다.</b>
     *
     * <p>전송 시점에 다시 만들면 "보내려던 것" 과 "보낸 것" 이 갈린다.
     */
    @Test
    void 저장된_payload_를_그대로_보낸다() throws Exception {
        queue("b-payload");
        String stored = jdbc.queryForObject(
                "SELECT payload FROM csm.sms_usage_outbox WHERE batch_id = ?", String.class, "b-payload");

        sender.run();

        assertThat(MAPPER.readTree(received.get(0)))
                .as("한 글자도 달라지면 안 된다")
                .isEqualTo(MAPPER.readTree(stored));
    }

    // ── 4xx 영구 실패 ─────────────────────────────────────────

    /**
     * ⭐ <b>4xx 는 재시도하지 않는다.</b>
     *
     * <p>형식 오류를 무한 재시도하면 큐가 막히고 그 뒤의 정상 이벤트까지 못 나간다.
     */
    @Test
    void 사백번대는_재시도하지_않는다() {
        queue("b-400");
        responseStatus = 400;

        sender.run();
        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(failedReason("b-400")).contains("HTTP 400");

        responseStatus = 200;   // 서버가 나아져도
        sender.run();
        assertThat(requestCount.get())
                .as("영구 실패는 다시 꺼내지 않는다")
                .isEqualTo(1);
        assertThat(sentAt("b-400")).isNull();
    }

    /** 409(이미 수신)도 4xx 다 — 멱등 처리된 것이므로 재시도할 이유가 없다. */
    @Test
    void 사백구도_영구_실패로_닫는다() {
        queue("b-409");
        responseStatus = 409;

        sender.run();

        assertThat(failedReason("b-409")).contains("HTTP 409");
    }

    /**
     * 영구 실패를 <b>조용히 버리지 않는다.</b> 사유가 남아야 사후 대사에서 드러난다.
     */
    @Test
    void 영구_실패_사유가_남는다() {
        queue("b-reason");
        responseStatus = 422;

        sender.run();

        assertThat(failedReason("b-reason")).isNotBlank().contains("422");
    }

    // ── 5xx 재시도 ────────────────────────────────────────────

    /** ⭐ 5xx 는 재시도한다. 백오프가 잡히고 사유는 남기지 않는다(영구 실패가 아니다). */
    @Test
    void 오백번대는_재시도를_예약한다() {
        queue("b-500");
        responseStatus = 500;

        sender.run();

        assertThat(failedReason("b-500")).as("영구 실패가 아니다").isNull();
        assertThat(attempts("b-500")).isEqualTo(1);
        assertThat(nextRetryAt("b-500")).as("백오프가 잡혀야 한다").isNotNull();
    }

    /** 백오프가 걸린 건은 아직 안 꺼낸다. 안 그러면 백오프가 의미가 없다. */
    @Test
    void 백오프_중인_건은_다시_꺼내지_않는다() {
        queue("b-backoff");
        responseStatus = 500;
        sender.run();

        sender.run();
        assertThat(requestCount.get()).isEqualTo(1);
    }

    /** 서버가 나아지면 재시도가 성공한다. */
    @Test
    void 서버가_복구되면_재시도가_성공한다() {
        queue("b-recover");
        responseStatus = 500;
        sender.run();

        // 백오프 시간이 지난 것으로 만든다.
        jdbc.update("UPDATE csm.sms_usage_outbox SET next_retry_at = NOW() - INTERVAL 1 MINUTE "
                + "WHERE batch_id = 'b-recover'");
        responseStatus = 200;
        sender.run();

        assertThat(sentAt("b-recover")).isNotNull();
        assertThat(attempts("b-recover")).isEqualTo(2);
    }

    // ── 하트비트 ──────────────────────────────────────────────

    /**
     * ⭐ <b>보낼 게 없어도 하트비트를 남긴다.</b>
     *
     * <p>스케줄러가 죽은 것과 보낼 게 없는 것은 로그에서 똑같이 조용하다.
     * 실행 자체를 기록해야 구분된다.
     */
    @Test
    void 보낼_것이_없어도_하트비트를_남긴다() {
        sender.run();

        Map<String, Object> hb = heartbeat();
        assertThat(hb).isNotNull();
        assertThat(hb.get("ran_at")).isNotNull();
        assertThat(((Number) hb.get("sent_count")).intValue()).isZero();
    }

    @Test
    void 하트비트에_처리_결과가_들어간다() {
        queue("b-hb1");
        queue("b-hb2");
        sender.run();

        Map<String, Object> hb = heartbeat();
        assertThat(((Number) hb.get("sent_count")).intValue()).isEqualTo(2);
        assertThat(((Number) hb.get("failed_count")).intValue()).isZero();
    }

    /** 실패도 하트비트에 남는다 — 실패가 조용하면 "정상" 으로 읽힌다. */
    @Test
    void 실패도_하트비트에_남는다() {
        queue("b-hbfail");
        responseStatus = 500;
        sender.run();

        Map<String, Object> hb = heartbeat();
        assertThat(((Number) hb.get("failed_count")).intValue()).isEqualTo(1);
        assertThat(((Number) hb.get("sent_count")).intValue()).isZero();
    }

    @Test
    void 영구_실패도_하트비트에_따로_센다() {
        queue("b-hbperm");
        responseStatus = 400;
        sender.run();

        Map<String, Object> hb = heartbeat();
        assertThat(((Number) hb.get("permanent_count")).intValue()).isEqualTo(1);
        assertThat(((Number) hb.get("failed_count")).intValue())
                .as("재시도 대상과 영구 실패는 대응이 다르므로 따로 센다")
                .isZero();
    }

    /**
     * ⭐ 스캐너 실행도 같은 하트비트에 포함된다.
     *
     * <p>전송과 스캔이 한 실행 안에서 돈다. 하트비트가 있으면 <b>둘 다 돌았다</b>는 뜻이다.
     */
    @Test
    void 스캐너가_복구한_건도_같은_실행에서_전송된다() {
        insertBatchAged("b-scanned", 30);   // outbox 없음

        sender.run();   // 1회차: 보낼 것 없음 → 스캔이 채운다
        assertThat(requestCount.get()).isZero();
        assertThat(sourceOf("b-scanned")).isEqualTo("SCAN");

        sender.run();   // 2회차: 채워진 것을 보낸다
        assertThat(sentAt("b-scanned")).isNotNull();
    }

    // ── 도우미 ────────────────────────────────────────────────

    private void queue(String batchId) {
        insertBatch(batchId);
        outbox.enqueueQuietly(batchId);
    }

    private void insertBatch(String batchId) {
        jdbc.update("""
                INSERT INTO csm.sms_batch
                    (batch_id, inst_code, idem_key, from_phone, send_type, total_count,
                     success_count, failed_count, unknown_count, unit_cost, total_cost,
                     billable, created_by, price_version)
                VALUES (?, 'COHS', ?, '01000000000', 'sms', 5, 5, 0, 0, 960, 4800, 'Y', 'test', 7)
                """, batchId, "idem-" + batchId);
    }

    private void insertBatchAged(String batchId, int minutesAgo) {
        insertBatch(batchId);
        jdbc.update("UPDATE csm.sms_batch SET created_at = NOW() - INTERVAL ? MINUTE "
                + "WHERE batch_id = ?", minutesAgo, batchId);
    }

    private Object sentAt(String batchId) {
        return one(batchId, "sent_at");
    }

    private String failedReason(String batchId) {
        return (String) one(batchId, "failed_reason");
    }

    private Object nextRetryAt(String batchId) {
        return one(batchId, "next_retry_at");
    }

    private String sourceOf(String batchId) {
        return (String) one(batchId, "source");
    }

    private int attempts(String batchId) {
        return ((Number) one(batchId, "attempts")).intValue();
    }

    private Object one(String batchId, String column) {
        return jdbc.queryForMap(
                "SELECT " + column + " FROM csm.sms_usage_outbox WHERE batch_id = ?", batchId)
                .get(column);
    }

    private Map<String, Object> heartbeat() {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM csm.sms_usage_heartbeat WHERE id = 1");
        return rows.isEmpty() ? null : rows.get(0);
    }
}
