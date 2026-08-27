package com.coresolution.csm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.csm.serivce.PlatformPriceCache;
import com.coresolution.csm.serivce.SmsService;
import com.coresolution.csm.support.TestDatabaseGuard;

/**
 * 단가 폴백 3단계를 <b>실제 MySQL 로</b> 검증한다.
 *
 * <p>── 왜 실제 DB 여야 하는가 ──
 * 이 설계의 핵심 주장은 <b>"재시작을 넘긴다"</b> 이다.
 * {@code PlatformPriceCache} 를 DB 로 만든 이유가 그것이다 — 메모리 캐시면
 * 재시작 직후 비어 있고, 그때 플랫폼이 죽어 있으면 폴백이 <b>조용히 한 단계
 * 아래로 떨어진다.</b> 발송은 계속되므로 며칠이 지나도 아무도 모른다.
 *
 * <p>Mockito 로는 이 주장을 검증할 수 없다. 목은 재시작을 넘길 것도 없다.
 * <b>같은 DB 에 새 객체를 붙였을 때 값이 그대로인가</b> 가 유일한 증거다.
 *
 * <p>── 실행 방법 ──
 * <pre>
 *   docker compose -f docker-compose.test.yml up -d
 *   ./gradlew test --tests '*PlatformPriceFallbackIntegrationTest'
 * </pre>
 * 컨테이너가 없으면 <b>skip 된다</b> (실패가 아니다). CI 는 {@code -PexcludeIntegration} 으로 뺀다.
 *
 * <p>⚠️ 접속 대상은 {@link TestDatabaseGuard} 가 <b>DB 안의 마커</b>로 확인한다.
 * 운영·개발 DB 에 붙으면 즉시 실패한다.
 */
@Tag("integration")
class PlatformPriceFallbackIntegrationTest {

    private static final int TEST_PORT = 3309;
    private static final String URL =
            "jdbc:mysql://127.0.0.1:" + TEST_PORT + "/csm?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=Asia/Seoul&characterEncoding=UTF-8";

    private static final String INST = "COHS";

    /** 프로퍼티 폴백(3단계). 운영 기본값과 같은 값을 쓴다. */
    private static final int FALLBACK_SMS_JEON = 960;
    private static final int FALLBACK_LMS_JEON = 3000;
    private static final int FALLBACK_MMS_JEON = 9000;

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void connect() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(URL, "root", "csm_test_pw");
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");

        try {
            new JdbcTemplate(ds).queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException e) {
            assumeTrue(false, "csm-test-mysql(" + TEST_PORT + ") 없음 — skip. "
                    + "띄우려면: docker compose -f docker-compose.test.yml up -d");
            return;
        }

        // ⚠️ 마커부터 심는다. installMarker 자체가 포트를 확인하므로,
        //    운영 DB 에 붙어 있으면 여기서 던진다.
        TestDatabaseGuard.installMarker(ds, TEST_PORT);
        TestDatabaseGuard.assertTestDatabase(ds);

        dataSource = ds;
        jdbc = new JdbcTemplate(ds);

        jdbc.execute(platformPriceCacheDdl());
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS csm.inst_data_cs (
                    id_col_02 VARCHAR(100) NULL,
                    id_col_03 VARCHAR(50)  NOT NULL PRIMARY KEY,
                    sms_price VARCHAR(20)  NULL,
                    lms_price VARCHAR(20)  NULL,
                    mms_price VARCHAR(20)  NULL,
                    sms_price_version INT  NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }

    /**
     * DDL 을 <b>운영 코드에서 읽어 온다.</b>
     *
     * <p>테스트에 DDL 을 손으로 베껴 두면 갈라진다 — 컬럼 타입이 바뀌어도 테스트는 초록이다.
     * {@code CsmSchemaBootstrapService} 가 실제로 실행하는 문장을 그대로 쓴다.
     */
    private static String platformPriceCacheDdl() throws Exception {
        Path src = Path.of("src/main/java/com/coresolution/csm/serivce/CsmSchemaBootstrapService.java");
        String source = Files.readString(src);
        Matcher m = Pattern
                .compile("(CREATE TABLE IF NOT EXISTS csm\\.platform_price_cache.*?utf8mb4_0900_ai_ci)",
                        Pattern.DOTALL)
                .matcher(source);
        assertThat(m.find())
                .as("CsmSchemaBootstrapService 에서 platform_price_cache DDL 을 찾지 못했다."
                        + " DDL 이 옮겨졌으면 이 테스트도 같이 고칠 것.")
                .isTrue();
        return m.group(1);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM csm.platform_price_cache");
        jdbc.update("DELETE FROM csm.inst_data_cs");
    }

    // ── 폴백 3단계 ────────────────────────────────────────────────

    /**
     * 1단계 — 캐시에 값이 있으면 그것을 쓴다.
     *
     * <p>{@code inst_data_cs} 에 <b>다른 값</b>을 넣어 둔다. 같은 값이면 어느 쪽을
     * 읽었는지 구분되지 않는다 (§3.3 "두 구현이 동등하면 검증되지 않는다").
     */
    @Test
    void 일단계_캐시가_있으면_캐시를_쓴다() {
        jdbc.update("INSERT INTO csm.platform_price_cache "
                + "(inst_code, channel, unit_cost_jeon, price_version) VALUES (?, 'sms', 777, 5)", INST);
        writeInstData("9.6", "30", "90");   // 2단계에는 960 이 들어 있다

        assertThat(newSmsService().unitCostJeon(INST, "sms")).isEqualTo(777);
    }

    /** 2단계 — 캐시가 비면 {@code inst_data_cs}. 3단계 폴백과 다른 값을 넣어 구분한다. */
    @Test
    void 이단계_캐시가_비면_inst_data_cs_를_쓴다() {
        writeInstData("12.34", "30", "90");

        assertThat(newSmsService().unitCostJeon(INST, "sms")).isEqualTo(1234);
    }

    /** 3단계 — 둘 다 없으면 프로퍼티 폴백. */
    @Test
    void 삼단계_둘_다_없으면_프로퍼티_폴백() {
        SmsService sms = newSmsService();

        assertThat(sms.unitCostJeon(INST, "sms")).isEqualTo(FALLBACK_SMS_JEON);
        assertThat(sms.unitCostJeon(INST, "lms")).isEqualTo(FALLBACK_LMS_JEON);
        assertThat(sms.unitCostJeon(INST, "mms")).isEqualTo(FALLBACK_MMS_JEON);
    }

    /**
     * 빈이 아예 없는 경우(CSM-3 미배포)에도 2단계부터 정상 동작한다.
     *
     * <p>{@code @Autowired(required = false)} 가 실제로 이 상태를 만든다.
     * <b>배포 순서 1단계</b>(URL 미설정)에서 운영이 이 상태로 돈다.
     */
    @Test
    void 캐시_빈이_없어도_기존_경로로_동작한다() {
        writeInstData("9.6", "30", "90");

        SmsService sms = new SmsService();
        ReflectionTestUtils.setField(sms, "jdbcTemplate", jdbc);
        setFallbacks(sms);
        // platformPriceCache 를 주입하지 않는다 = 빈 없음

        assertThat(sms.unitCostJeon(INST, "sms")).isEqualTo(960);
    }

    // ── ⭐ 재시작 생존 ─────────────────────────────────────────────

    /**
     * ⭐ <b>이 설계의 핵심 주장이다.</b>
     *
     * <p>플랫폼에서 단가를 받아 저장한 뒤, <b>모든 객체를 버리고 새로 만든다.</b>
     * 애플리케이션 재시작과 같은 상태다 — JVM 안의 것은 전부 사라지고 DB 만 남는다.
     *
     * <p>확인 순서:
     * <ol>
     *   <li>단가 저장 (플랫폼에서 받은 것처럼)</li>
     *   <li><b>{@code inst_data_cs} 를 비운다</b> — 2단계가 살아 있으면 캐시가 죽어도
     *       티가 안 난다. 비워야 "캐시가 살아남았다" 와 "2단계로 떨어졌다" 가 구분된다</li>
     *   <li>새 {@code PlatformPriceCache} + 새 {@code SmsService} 로 조회</li>
     * </ol>
     *
     * <p>캐시가 메모리였다면 여기서 <b>3단계 폴백값(960)</b> 이 나온다.
     */
    @Test
    void 재시작을_넘겨_캐시_단가가_유지된다() {
        newCache().store(INST, "sms", 812, 7);

        // 2단계를 없애서 캐시가 죽었는지 살았는지 구분되게 한다.
        jdbc.update("DELETE FROM csm.inst_data_cs");

        // ── 여기서부터 "재시작 후" ──
        SmsService afterRestart = newSmsService();

        assertThat(afterRestart.unitCostJeon(INST, "sms"))
                .as("캐시가 재시작을 넘기지 못하면 3단계 폴백값 %d 이 나온다", FALLBACK_SMS_JEON)
                .isEqualTo(812);
    }

    /** 재시작 후에도 <b>버전</b>이 남아야 한다. 플랫폼이 이 값으로 적용 여부를 판단한다. */
    @Test
    void 재시작을_넘겨_적용_버전이_유지된다() {
        PlatformPriceCache before = newCache();
        before.store(INST, "sms", 812, 7);
        before.store(INST, "lms", 2800, 9);

        // 새 객체 = 재시작 후
        assertThat(newCache().appliedVersion(INST))
                .as("여러 채널의 버전이 다르면 MIN 을 회신한다 — 하나라도 옛 버전이면 미적용이다")
                .contains(7);
    }

    /** 값이 정말 DB 에 있는지 직접 확인한다. 객체를 거치지 않는 증거다. */
    @Test
    void 캐시_값이_DB_행으로_남는다() {
        newCache().store(INST, "mms", 8950, 3);

        var row = jdbc.queryForMap(
                "SELECT unit_cost_jeon, price_version FROM csm.platform_price_cache "
                        + "WHERE inst_code = ? AND channel = 'mms'", INST);

        assertThat(((Number) row.get("unit_cost_jeon")).intValue()).isEqualTo(8950);
        assertThat(((Number) row.get("price_version")).intValue()).isEqualTo(3);
    }

    // ── 미러링 ────────────────────────────────────────────────────

    /**
     * {@code store()} 는 {@code inst_data_cs} 도 갱신한다 — 기존 화면이 그 컬럼을 읽는다.
     *
     * <p><b>원 단위 문자열로 들어가야 한다.</b> 전 단위 정수(`960`)가 그대로 들어가면
     * 화면에 <b>960원</b> 으로 나오고, 2단계 폴백도 96,000전으로 읽는다 — 100배다.
     */
    @Test
    void 미러링은_원_단위_문자열로_들어간다() {
        writeInstData(null, null, null);
        newCache().store(INST, "sms", 960, 11);

        var row = jdbc.queryForMap(
                "SELECT sms_price, sms_price_version FROM csm.inst_data_cs WHERE id_col_03 = ?", INST);

        assertThat(row.get("sms_price")).isEqualTo("9.6");
        assertThat(((Number) row.get("sms_price_version")).intValue()).isEqualTo(11);
    }

    /**
     * ⚠️ <b>배포 순서 제약의 근거다.</b>
     *
     * <p>미러링이 {@code inst_data_cs} 를 <b>덮어쓴다.</b> 그 컬럼은 2단계 폴백이기도 하므로,
     * 플랫폼이 잘못된 단가를 배포하면 <b>1단계와 2단계가 동시에 오염</b>된다.
     * 연동을 꺼도 2단계에 남는다.
     *
     * <p>절차: {@code docs/prod-deploy-checklist.md} §9
     */
    @Test
    void 미러링이_기존_단가를_덮어쓴다() {
        writeInstData("9.6", "30", "90");

        newCache().store(INST, "sms", 5000, 12);   // 플랫폼이 50원을 배포했다

        assertThat(jdbc.queryForObject(
                "SELECT sms_price FROM csm.inst_data_cs WHERE id_col_03 = ?", String.class, INST))
                .as("연동을 꺼도 이 값이 2단계 폴백으로 남는다")
                .isEqualTo("50");
    }

    // ── 도우미 ────────────────────────────────────────────────────

    /** 새 캐시 객체. <b>매번 새로 만드는 것이 요점이다</b> — 메모리 상태를 물려받지 않는다. */
    private PlatformPriceCache newCache() {
        return new PlatformPriceCache(new JdbcTemplate(dataSource));
    }

    /** 새 {@code SmsService}. 재시작 후 컨테이너가 만드는 것과 같은 상태다. */
    private SmsService newSmsService() {
        SmsService sms = new SmsService();
        ReflectionTestUtils.setField(sms, "jdbcTemplate", new JdbcTemplate(dataSource));
        ReflectionTestUtils.setField(sms, "platformPriceCache", newCache());
        setFallbacks(sms);
        return sms;
    }

    private void setFallbacks(SmsService sms) {
        ReflectionTestUtils.setField(sms, "fallbackSmsJeon", FALLBACK_SMS_JEON);
        ReflectionTestUtils.setField(sms, "fallbackLmsJeon", FALLBACK_LMS_JEON);
        ReflectionTestUtils.setField(sms, "fallbackMmsJeon", FALLBACK_MMS_JEON);
    }

    private void writeInstData(String sms, String lms, String mms) {
        jdbc.update("INSERT INTO csm.inst_data_cs (id_col_02, id_col_03, sms_price, lms_price, mms_price) "
                + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "sms_price = VALUES(sms_price), lms_price = VALUES(lms_price), mms_price = VALUES(mms_price)",
                "통합테스트병원", INST, sms, lms, mms);
    }
}
