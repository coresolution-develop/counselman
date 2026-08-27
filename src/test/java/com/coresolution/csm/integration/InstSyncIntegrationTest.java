package com.coresolution.csm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.coresolution.csm.serivce.CsmSchemaBootstrapService.InstChange;
import com.coresolution.csm.serivce.InstSyncOutboxService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기관 변경 통지 적재를 <b>실제 MySQL 로</b> 검증한다 (CSM-6).
 *
 * <p>설계 승인 시 정한 7항목 중 적재 쪽을 본다.
 * <b>"변경 없으면 통지 없음" 이 특히 중요하다</b> — 10분마다 6건씩 나가면
 * 진짜 변경이 그 안에 묻힌다.
 */
@Tag("integration")
class InstSyncIntegrationTest {

    private static final int TEST_PORT = 3309;
    private static final String URL =
            "jdbc:mysql://127.0.0.1:" + TEST_PORT + "/csm?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=Asia/Seoul&characterEncoding=UTF-8";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JdbcTemplate jdbc;
    private static InstSyncOutboxService outbox;

    @BeforeAll
    static void connect() {
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
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS csm.inst_sync_outbox (
                    inst_code     VARCHAR(50)  NOT NULL,
                    change_type   VARCHAR(20)  NOT NULL,
                    payload       JSON         NOT NULL,
                    attempts      INT          NOT NULL DEFAULT 0,
                    next_retry_at DATETIME     NULL,
                    sent_at       DATETIME     NULL,
                    failed_reason VARCHAR(500) NULL,
                    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (inst_code, change_type),
                    KEY ix_inst_sync_pending (sent_at, next_retry_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        outbox = new InstSyncOutboxService(jdbc, MAPPER);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM csm.inst_sync_outbox");
    }

    // ── 통지 타입 셋 ──────────────────────────────────────────

    @Test
    void 신규_기관을_통지한다() throws Exception {
        outbox.enqueueQuietly(new InstChange("COHS", "코어병원", "Y", "CREATED"));

        JsonNode p = payloadOf("COHS", "CREATED");
        assertThat(p.get("instCode").asText()).isEqualTo("COHS");
        assertThat(p.get("instName").asText()).isEqualTo("코어병원");
        assertThat(p.get("active").asBoolean()).isTrue();
        assertThat(p.get("changeType").asText()).isEqualTo("CREATED");
    }

    /** ⭐ 비활성화. 이게 예전에는 <b>영영 반영되지 않던</b> 변경이다. */
    @Test
    void 비활성화를_통지한다() throws Exception {
        outbox.enqueueQuietly(new InstChange("COHS", "코어병원", "N", "USE_YN_CHANGED"));

        JsonNode p = payloadOf("COHS", "USE_YN_CHANGED");
        assertThat(p.get("active").asBoolean())
                .as("use_yn='N' 은 active=false 로 보낸다 — 플랫폼이 csm 표기 규칙을 알 필요가 없다")
                .isFalse();
    }

    /**
     * 이름 변경도 통지한다.
     *
     * <p>반영이 안 되면 플랫폼 관리자 화면에 옛 이름이 남는다.
     * <b>기관을 코드가 아니라 이름으로 찾는 사람에게는 실제 문제다.</b>
     */
    @Test
    void 이름_변경을_통지한다() throws Exception {
        outbox.enqueueQuietly(new InstChange("COHS", "코어의료재단", "Y", "RENAMED"));

        assertThat(payloadOf("COHS", "RENAMED").get("instName").asText()).isEqualTo("코어의료재단");
    }

    // ── ⭐ 같은 변경을 반복 적재하지 않는다 ────────────────────

    /**
     * ⭐ 같은 변경을 여러 번 넣어도 <b>행은 하나</b>다.
     *
     * <p>이 경로는 <b>10분마다 돈다.</b> 반복 적재하면 큐가 같은 통지로 차고,
     * 그러면 진짜 변경이 그 안에 묻힌다.
     */
    @Test
    void 같은_변경을_반복_적재해도_행은_하나다() {
        for (int i = 0; i < 5; i++) {
            outbox.enqueueQuietly(new InstChange("COHS", "코어병원", "N", "USE_YN_CHANGED"));
        }

        assertThat(count("COHS", "USE_YN_CHANGED")).isEqualTo(1);
    }

    /** 다른 종류의 변경은 <b>따로</b> 쌓인다 — 이름과 활성 상태가 같이 바뀔 수 있다. */
    @Test
    void 다른_종류의_변경은_따로_쌓인다() {
        outbox.enqueueQuietly(new InstChange("COHS", "코어병원", "N", "USE_YN_CHANGED"));
        outbox.enqueueQuietly(new InstChange("COHS", "코어의료재단", "N", "RENAMED"));

        assertThat(count("COHS", "USE_YN_CHANGED")).isEqualTo(1);
        assertThat(count("COHS", "RENAMED")).isEqualTo(1);
    }

    /** 반복 적재 시 <b>내용은 최신으로</b> 갱신된다. 낡은 이름이 나가면 안 된다. */
    @Test
    void 반복_적재하면_내용이_최신으로_갱신된다() throws Exception {
        outbox.enqueueQuietly(new InstChange("COHS", "옛이름", "Y", "RENAMED"));
        outbox.enqueueQuietly(new InstChange("COHS", "새이름", "Y", "RENAMED"));

        assertThat(payloadOf("COHS", "RENAMED").get("instName").asText()).isEqualTo("새이름");
    }

    /**
     * ⭐ 이미 보낸 뒤 <b>다시</b> 바뀌면 또 나가야 한다.
     *
     * <p>{@code ON DUPLICATE KEY UPDATE} 가 {@code sent_at} 을 안 지우면
     * 행은 갱신되는데 전송 대상에서 빠져 <b>영영 안 나간다.</b>
     */
    @Test
    void 이미_보낸_뒤_다시_바뀌면_또_나간다() {
        outbox.enqueueQuietly(new InstChange("COHS", "코어병원", "N", "USE_YN_CHANGED"));
        jdbc.update("UPDATE csm.inst_sync_outbox SET sent_at = NOW() "
                + "WHERE inst_code = 'COHS' AND change_type = 'USE_YN_CHANGED'");

        outbox.enqueueQuietly(new InstChange("COHS", "코어병원", "Y", "USE_YN_CHANGED"));

        assertThat(one("COHS", "USE_YN_CHANGED", "sent_at"))
                .as("sent_at 이 남아 있으면 재활성화 통지가 영영 안 나간다")
                .isNull();
    }

    /** 영구 실패로 닫힌 뒤 다시 바뀌면 <b>사유를 지우고</b> 재시도 대상이 된다. */
    @Test
    void 영구_실패_뒤_다시_바뀌면_사유를_지운다() {
        outbox.enqueueQuietly(new InstChange("COHS", "코어병원", "Y", "CREATED"));
        jdbc.update("UPDATE csm.inst_sync_outbox SET failed_reason = 'HTTP 404', attempts = 3 "
                + "WHERE inst_code = 'COHS' AND change_type = 'CREATED'");

        outbox.enqueueQuietly(new InstChange("COHS", "코어병원", "Y", "CREATED"));

        assertThat(one("COHS", "CREATED", "failed_reason"))
                .as("엔드포인트가 생긴 뒤에는 다음 변경부터 나가야 한다")
                .isNull();
        assertThat(((Number) one("COHS", "CREATED", "attempts")).intValue()).isZero();
    }

    // ── 정규화 ────────────────────────────────────────────────

    /**
     * 통지 코드는 <b>정규형</b>이다.
     *
     * <p>표기가 갈리면 같은 기관이 두 시스템에서 다른 기관이 된다.
     * (정규화 자체는 {@code InstCodeVectorsTest} 가 벡터로 본다 — 여기서는
     * <b>정규화된 값이 그대로 payload 에 실리는지</b>만 확인한다.)
     */
    @Test
    void 통지_코드는_정규형_그대로_실린다() throws Exception {
        String normalized = com.coresolution.csm.serivce.CsmSchemaBootstrapService
                .normalizeInstCode("hsop_0001");
        assertThat(normalized).isEqualTo("HSOP_0001");

        outbox.enqueueQuietly(new InstChange(normalized, "테스트병원", "Y", "CREATED"));

        assertThat(payloadOf("HSOP_0001", "CREATED").get("instCode").asText())
                .isEqualTo("HSOP_0001");
    }

    // ── ⭐ 기동을 무르게 하지 않는다 ───────────────────────────

    /**
     * ⭐ 적재 실패가 예외로 새어 나가면 <b>기동이 실패한다.</b>
     *
     * <p>이 경로는 {@code @PostConstruct} 에서도 불린다. 플랫폼 연동 때문에
     * csm 이 못 뜨는 상황을 만들지 않는다.
     */
    @Test
    void 적재_실패가_예외로_새어_나가지_않는다() {
        jdbc.execute("DROP TABLE csm.inst_sync_outbox");
        try {
            assertThatCode(() -> outbox.enqueueQuietly(
                    new InstChange("COHS", "코어병원", "Y", "CREATED")))
                    .doesNotThrowAnyException();
        } finally {
            connect();
        }
    }

    // ── 도우미 ────────────────────────────────────────────────

    private int count(String inst, String type) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM csm.inst_sync_outbox WHERE inst_code = ? AND change_type = ?",
                Integer.class, inst, type);
        return n == null ? 0 : n;
    }

    private Object one(String inst, String type, String column) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + column + " FROM csm.inst_sync_outbox "
                        + "WHERE inst_code = ? AND change_type = ?", inst, type);
        return rows.isEmpty() ? null : rows.get(0).get(column);
    }

    private JsonNode payloadOf(String inst, String type) throws Exception {
        String json = jdbc.queryForObject(
                "SELECT payload FROM csm.inst_sync_outbox WHERE inst_code = ? AND change_type = ?",
                String.class, inst, type);
        return MAPPER.readTree(json);
    }
}
