package com.coresolution.csm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.coresolution.csm.serivce.SmsUsageOutboxService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 사용량 outbox 를 <b>실제 MySQL 로</b> 검증한다 (CSM-4).
 *
 * <p>설계 승인 시 정한 7항목이 여기 있다. 특히 <b>payload 고정</b>은
 * "통째로 저장" 이라는 결정의 근거이므로 빠뜨리면 안 된다.
 *
 * <p>실행: {@code docker compose -f docker-compose.test.yml up -d} 후
 * {@code ./gradlew test --tests '*SmsUsageOutboxIntegrationTest'}
 */
@Tag("integration")
class SmsUsageOutboxIntegrationTest {

    private static final int TEST_PORT = 3309;
    private static final String URL =
            "jdbc:mysql://127.0.0.1:" + TEST_PORT + "/csm?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=Asia/Seoul&characterEncoding=UTF-8";

    private static final String INST = "COHS";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JdbcTemplate jdbc;
    private static SmsUsageOutboxService outbox;

    @BeforeAll
    static void connect() throws Exception {
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
        addColumn("sms_batch", "price_version");
        outbox = new SmsUsageOutboxService(jdbc, MAPPER);
    }

    /**
     * DDL 을 <b>운영 코드에서 읽어 온다.</b> 테스트에 베껴 두면 컬럼이 바뀌어도 초록이다.
     */
    private static String ddl(String tablePattern) throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/coresolution/csm/serivce/CsmSchemaBootstrapService.java"));
        Matcher m = Pattern.compile(
                "(CREATE TABLE IF NOT EXISTS " + tablePattern + " \\(.*?utf8mb4_0900_ai_ci)",
                Pattern.DOTALL).matcher(source);
        assertThat(m.find()).as("%s DDL 을 찾지 못했다", tablePattern).isTrue();
        return m.group(1);
    }

    /**
     * {@code CREATE TABLE} 에 없고 {@code addColumnIfMissing} 으로 붙는 컬럼.
     *
     * <p>기존 설치에 나중에 추가된 컬럼이라 CREATE 문에 없다.
     * <b>정의를 여기 베끼지 않고</b> 운영 코드의 호출 인자에서 읽는다 —
     * 타입이 바뀌면 이 테스트도 같이 따라가야 한다.
     */
    private static void addColumn(String table, String column) throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/coresolution/csm/serivce/CsmSchemaBootstrapService.java"));
        Matcher m = Pattern.compile(
                "addColumnIfMissing\\(\"" + table + "\", \"" + column + "\", \"([^\"]+)\"\\)")
                .matcher(source);
        assertThat(m.find())
                .as("%s.%s 의 addColumnIfMissing 호출을 찾지 못했다", table, column)
                .isTrue();
        try {
            jdbc.execute("ALTER TABLE csm." + table + " ADD COLUMN " + column + " " + m.group(1));
        } catch (Exception e) {
            // 이미 있다.
        }
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM csm.sms_usage_outbox");
        jdbc.update("DELETE FROM csm.sms_batch");
    }

    // ── ① 멱등 ────────────────────────────────────────────────

    /**
     * 같은 배치를 두 번 넣어도 1건이다.
     *
     * <p>플랫폼 멱등키가 {@code (companyId, batchId)} 이므로 csm 쪽에서도
     * 배치당 1건이 <b>구조적으로</b> 보장돼야 한다. PK 가 그 장치다.
     */
    @Test
    void 같은_배치를_두_번_넣어도_한_건이다() {
        insertBatch("b-1", 10, 8, 1, 1, 960, 7);

        outbox.enqueueQuietly("b-1");
        outbox.enqueueQuietly("b-1");

        assertThat(count("b-1")).isEqualTo(1);
    }

    // ── ② payload 고정 (통째 저장의 근거) ─────────────────────

    /**
     * ⭐ <b>outbox 생성 후 {@code sms_batch} 가 바뀌어도 보낼 내용은 안 바뀐다.</b>
     *
     * <p>전송 시점에 다시 읽는 구조였다면 <b>보내려던 것과 보낸 것이 갈린다.</b>
     * payload 를 통째로 저장한 유일한 이유가 이것이다.
     */
    @Test
    void 적재_후_배치가_바뀌어도_payload_는_고정된다() throws Exception {
        insertBatch("b-2", 10, 8, 1, 1, 960, 7);
        outbox.enqueueQuietly("b-2");

        // 배치를 바꾼다 — 리포트 반영, 운영자 수정, 무엇이든.
        jdbc.update("UPDATE csm.sms_batch SET success_count = 999, total_cost = 123456 "
                + "WHERE batch_id = 'b-2'");

        JsonNode payload = payloadOf("b-2");
        assertThat(payload.get("successCount").asInt())
                .as("적재 시점 값이어야 한다")
                .isEqualTo(8);
        assertThat(payload.get("totalCostJeon").asLong()).isEqualTo(8640L);
    }

    // ── ③ 접수 시점 기준임을 밝힌다 ───────────────────────────

    /**
     * csm 의 {@code success_count} 는 <b>접수 시점</b> 값이다 —
     * 배달 리포트 콜백은 {@code sms_batch} 를 갱신하지 않는다.
     * 그 사실이 payload 에 있어야 플랫폼이 최종 결과로 오해하지 않는다.
     */
    @Test
    void payload_가_접수_시점_기준임을_밝힌다() throws Exception {
        insertBatch("b-3", 10, 8, 1, 1, 960, 7);
        outbox.enqueueQuietly("b-3");

        assertThat(payloadOf("b-3").get("countBasis").asText())
                .isEqualTo(SmsUsageOutboxService.COUNT_BASIS_ACCEPTED);
    }

    // ── ④ 무료 건과 단가 0 을 구분한다 ────────────────────────

    /**
     * ⭐ {@code totalCostJeon = 0} 만으로는 두 상황이 구분되지 않는다.
     * <ul>
     *   <li><b>무료 건(OTP)</b> — 정상. 발송량 집계에는 들어가야 한다</li>
     *   <li><b>단가가 0으로 잘못 들어온 건</b> — 사고. 조사해야 한다</li>
     * </ul>
     * 대응이 정반대이므로 {@code billable} 플래그로 나눈다.
     */
    @Test
    void 무료_건과_단가_0_을_구분한다() throws Exception {
        insertBatch("b-free", 3, 3, 0, 0, 0, 7, "N");
        insertBatch("b-zero", 3, 3, 0, 0, 0, 7, "Y");
        outbox.enqueueQuietly("b-free");
        outbox.enqueueQuietly("b-zero");

        assertThat(payloadOf("b-free").get("billable").asBoolean())
                .as("OTP 등 무료 건")
                .isFalse();
        assertThat(payloadOf("b-zero").get("billable").asBoolean())
                .as("과금 대상인데 금액이 0 — 조사 대상이다")
                .isTrue();

        assertThat(payloadOf("b-free").get("totalCount").asInt())
                .as("무료여도 발송량은 집계된다")
                .isEqualTo(3);
    }

    // ── ⑤ price_version ──────────────────────────────────────

    @Test
    void 과금에_쓴_단가_버전을_함께_보낸다() throws Exception {
        insertBatch("b-v", 5, 5, 0, 0, 960, 11);
        outbox.enqueueQuietly("b-v");

        assertThat(payloadOf("b-v").get("priceVersion").asInt()).isEqualTo(11);
    }

    /**
     * 버전이 {@code null} 인 것은 <b>오류가 아니라 정보</b>다 —
     * 플랫폼 단가를 못 받은 채 3단계 폴백으로 발송했다는 뜻이다.
     */
    @Test
    void 버전이_없으면_null_그대로_보낸다() throws Exception {
        insertBatch("b-nov", 5, 5, 0, 0, 960, null);
        outbox.enqueueQuietly("b-nov");

        assertThat(payloadOf("b-nov").get("priceVersion").isNull())
                .as("0 이나 -1 로 바꾸면 '못 받았다' 와 '0번 버전' 이 섞인다")
                .isTrue();
    }

    // ── ⑥ 누락 복구 스캐너 ────────────────────────────────────

    /**
     * ⭐ 발송은 됐는데 outbox 에 없는 배치를 뒤늦게 채운다.
     *
     * <p>이것이 <b>트랜잭션 대신</b> 선택한 장치다. outbox INSERT 가 실패해도
     * {@code sms_batch} 가 진실로 남아 있으므로 복구가 가능하다.
     */
    @Test
    void 누락된_배치를_스캐너가_채운다() {
        insertBatchAged("b-lost", 30);   // 30분 전 발송, outbox 없음

        int recovered = outbox.recoverMissing(10, 50);

        assertThat(recovered).isEqualTo(1);
        assertThat(count("b-lost")).isEqualTo(1);
    }

    /**
     * ⭐ <b>정상 경로와 스캐너를 구분할 수 있어야 한다.</b>
     *
     * <p>스캐너가 자주 잡으면 그 자체가 신호다 — 발송 경로의 outbox 적재가
     * 계속 실패하고 있다는 뜻이다. 구분해 두지 않으면 그걸 알 수 없다.
     */
    @Test
    void 스캐너가_만든_것과_정상_경로를_구분한다() {
        insertBatch("b-normal", 5, 5, 0, 0, 960, 7);
        outbox.enqueueQuietly("b-normal");

        insertBatchAged("b-scan", 30);
        outbox.recoverMissing(10, 50);

        assertThat(sourceOf("b-normal")).isEqualTo("SEND");
        assertThat(sourceOf("b-scan")).isEqualTo("SCAN");
    }

    /**
     * ⭐ <b>아직 진행 중일 수 있는 배치를 누락으로 잡지 않는다.</b>
     *
     * <p>지연 없이 스캔하면 정상 배치를 {@code SCAN} 으로 기록하고,
     * 그러면 "스캐너가 자주 잡는다" 는 신호가 무의미해진다.
     */
    @Test
    void 지연_시간_안의_배치는_건드리지_않는다() {
        insertBatchAged("b-fresh", 2);   // 2분 전 — 아직 발송 중일 수 있다

        assertThat(outbox.recoverMissing(10, 50)).isZero();
        assertThat(count("b-fresh")).isZero();
    }

    /** 이미 적재된 배치를 스캐너가 다시 넣지 않는다. */
    @Test
    void 이미_적재된_배치는_스캐너가_건드리지_않는다() {
        insertBatchAged("b-done", 30);
        outbox.enqueueQuietly("b-done");

        assertThat(outbox.recoverMissing(10, 50)).isZero();
        assertThat(count("b-done")).isEqualTo(1);
    }

    // ── ⑦ 발송을 무르게 하지 않는다 ───────────────────────────

    /**
     * ⭐ <b>outbox 적재 실패가 예외로 새어 나가면 안 된다.</b>
     *
     * <p>문자는 이미 나갔다. 여기서 던지면 발송이 실패로 뒤집힌다.
     * 존재하지 않는 배치를 넣어 실패를 만든다 — 정상 흐름에는 없는 상태다.
     */
    @Test
    void 적재_실패가_예외로_새어_나가지_않는다() {
        assertThatCode(() -> outbox.enqueueQuietly("존재하지-않는-배치"))
                .doesNotThrowAnyException();

        assertThat(count("존재하지-않는-배치")).isZero();
    }

    /** outbox 테이블 자체가 없어도 던지지 않는다 (CSM-4 미배포 DB). */
    @Test
    void 테이블이_없어도_예외로_새어_나가지_않는다() {
        insertBatch("b-notable", 5, 5, 0, 0, 960, 7);
        jdbc.execute("DROP TABLE csm.sms_usage_outbox");
        try {
            assertThatCode(() -> outbox.enqueueQuietly("b-notable")).doesNotThrowAnyException();
        } finally {
            try {
                jdbc.execute(ddl("csm\\.sms_usage_outbox"));
            } catch (Exception e) {
                throw new IllegalStateException("테이블 복구 실패", e);
            }
        }
    }

    // ── 도우미 ────────────────────────────────────────────────

    private void insertBatch(String batchId, int total, int success, int failed, int unknown,
            int unitCost, Integer version) {
        insertBatch(batchId, total, success, failed, unknown, unitCost, version, "Y");
    }

    private void insertBatch(String batchId, int total, int success, int failed, int unknown,
            int unitCost, Integer version, String billable) {
        jdbc.update("""
                INSERT INTO csm.sms_batch
                    (batch_id, inst_code, idem_key, from_phone, send_type, total_count,
                     success_count, failed_count, unknown_count, unit_cost, total_cost,
                     billable, created_by, price_version)
                VALUES (?, ?, ?, '01000000000', 'sms', ?, ?, ?, ?, ?, ?, ?, 'test', ?)
                """, batchId, INST, "idem-" + batchId, total, success, failed, unknown,
                unitCost, (long) unitCost * (success + unknown), billable, version);
    }

    /** {@code minutesAgo} 분 전에 만들어진 배치. 스캐너 지연 판정을 보려면 필요하다. */
    private void insertBatchAged(String batchId, int minutesAgo) {
        insertBatch(batchId, 5, 5, 0, 0, 960, 7);
        jdbc.update("UPDATE csm.sms_batch SET created_at = NOW() - INTERVAL ? MINUTE "
                + "WHERE batch_id = ?", minutesAgo, batchId);
    }

    private int count(String batchId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM csm.sms_usage_outbox WHERE batch_id = ?", Integer.class, batchId);
        return n == null ? 0 : n;
    }

    private String sourceOf(String batchId) {
        return jdbc.queryForObject(
                "SELECT source FROM csm.sms_usage_outbox WHERE batch_id = ?", String.class, batchId);
    }

    private JsonNode payloadOf(String batchId) throws Exception {
        String json = jdbc.queryForObject(
                "SELECT payload FROM csm.sms_usage_outbox WHERE batch_id = ?", String.class, batchId);
        return MAPPER.readTree(json);
    }
}
