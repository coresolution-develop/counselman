package com.coresolution.csm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.context.TestPropertySource;

/**
 * <b>DB 없이</b> 스프링 컨텍스트가 뜨는지 확인한다 (CSM-7). <b>CI 게이트에서 돈다.</b>
 *
 * <p>━━━ 이 테스트가 검증하는 것 ━━━
 * <ul>
 *   <li><b>{@code @Value} 플레이스홀더가 전부 해결되는가</b> — 가장 중요하다.
 *       2026-08-13 mediplat 12.5시간 중단이 정확히 이것이었다
 *       ({@code PLATFORM_ADMIN_PASSWORD} 미주입 → {@code PlaceholderResolutionException} 크래시 루프)</li>
 *   <li>빈 배선 — 순환참조, 없는 의존성, 중복 빈 이름</li>
 *   <li>{@code @Scheduled} 표현식 — 잘못되면 기동에서 터진다</li>
 *   <li>컴포넌트 스캔 범위 — 새 빈이 실제로 잡히는가</li>
 *   <li><b>운영과 같은 프로파일·같은 properties 를 읽는다</b> (아래 참조)</li>
 * </ul>
 *
 * <p>━━━ 이 테스트가 검증하지 <b>않는</b> 것 ━━━
 * <ul>
 *   <li><b>MyBatis 매퍼 SQL 문법</b> — DataSource 를 뺐으므로 SQL 이 DB 에 닿지 않는다.
 *       매퍼 인터페이스의 어노테이션 SQL 에 오타가 있어도 여기서는 통과한다</li>
 *   <li><b>스키마 부트스트랩</b> — {@code CsmSchemaBootstrapService} 의 DDL 이 실행되지 않는다</li>
 *   <li><b>세션 저장소(JDBC)</b> — 자동설정을 제외했다</li>
 *   <li>실제 DB 연결·커넥션 풀 설정</li>
 * </ul>
 * 위 네 가지는 {@link CsmApplicationTests}(전체 컨텍스트, 컨테이너 필요)가 본다.
 * <b>둘 중 하나만으로는 부족하다.</b>
 *
 * <p>━━━ 왜 프로파일을 새로 만들지 않았나 ━━━
 * 테스트 전용 프로파일({@code application-test.properties})을 두면 컨텍스트는 쉽게 뜬다.
 * 그러나 <b>운영이 읽는 {@code application-dev.properties} 를 안 읽게 되므로</b>
 * 그 파일의 플레이스홀더 누락을 영영 못 잡는다 — 이 테스트의 존재 이유가 사라진다.
 *
 * <p>그래서 <b>프로파일은 {@code dev} 그대로</b> 두고, DataSource 자동설정만 제외한다.
 * 아래 {@code @TestPropertySource} 는 <b>접속 정보를 가짜로 채우는 것이 아니라</b>,
 * DataSource 를 만들지 않겠다고 선언하는 것이다.
 *
 * <p>{@code contextLoads} 가 게이트에서 빠져 있던 2개월 동안 실패 원인이 두 번 바뀌었다.
 * 근거: {@code CLAUDE.md} §3.2 "빨간 테스트를 게이트에서 빼면 원인이 바뀌어도 모르게 된다".
 */
@SpringBootTest(
        properties = {
                // 진짜 커넥션 풀과 JPA·JDBC 세션만 뺀다.
                //
                // **DataSource 자체를 없애면 안 된다** — JdbcTemplate 을 주입받는 빈이
                // 여럿이라 컨텍스트가 통째로 안 뜬다. 그러면 검증할 것이 남지 않는다.
                // 대신 아래 StubDataSourceConfig 가 **연결하지 않는** DataSource 를 넣는다.
                // JPA 는 **의존성만 있고 코드에서 쓰지 않는다** (@Entity·JpaRepository 0건).
                // 그래도 자동설정이 EntityManagerFactory 를 만들려 하므로 통째로 뺀다.
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
                // application-dev.properties 의 store-type=jdbc 를 덮는다.
                "spring.session.store-type=none",
                // 기동 시 DB 에 쓰기를 시도하는 유일한 경로를 끈다.
                // 이 빈은 실패를 삼키지 않으므로 켜 두면 컨텍스트가 안 뜬다.
                "platform.admin.bootstrap.enabled=false",
        })
@Import(ContextWiringTest.StubDataSourceConfig.class)
@TestPropertySource(properties = {
        // ⚠️ 여기 있는 값은 **형식만 갖춘 더미**다. 운영 값을 흉내 내지 않는다.
        //
        // 이 목록 자체가 산출물이다 — **dev 프로파일 기동에 필수인 환경변수**가
        // 무엇인지 코드로 남는다. 새 필수 env 가 생기면 이 테스트가 먼저 터진다.
        // (배포 절차서 §3 대조표와 같이 봐야 한다)
        "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3309/csm",
        "SPRING_DATASOURCE_USERNAME=unused",
        "SPRING_DATASOURCE_PASSWORD=unused",
        "LOGIN_AES_KEY=0123456789abcdef0123456789abcdef",
        "MEDIPLAT_SSO_SHARED_SECRET=context-test",
        "COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET=context-test",
        "BIZPPURIO_DEV_ACCOUNT=context-test",
        "BIZPPURIO_DEV_USERNAME=context-test",
        "BIZPPURIO_DEV_PASSWORD=context-test",
        "BIZPPURIO_PROD_ACCOUNT=context-test",
        "BIZPPURIO_PROD_USERNAME=context-test",
        "BIZPPURIO_PROD_PASSWORD=context-test",
        // ⚠️ 아래 셋은 @ConfigurationProperties 로 바인딩된다 — 없어도 **기동은 된다.**
        //    그래서 위 목록에 빠져 있었고, 절차서 §3 대조표에도 없었다.
        //    해석되지_않은_플레이스홀더가_남지_않는다() 가 이제 이걸 잡는다.
        "KAKAO_CLIENT_ID=context-test",
        "KAKAO_CLIENT_SECRET=context-test",
        "SPRING_MAIL_PASSWORD=context-test",
})
class ContextWiringTest {

    @Autowired
    private ApplicationContext ctx;

    /**
     * ⭐ 컨텍스트가 뜨는 것 자체가 검증이다.
     *
     * <p>플레이스홀더 하나만 빠져도 여기서 터진다. 08-13 장애가 그랬다.
     */
    @Test
    void DB_없이도_컨텍스트가_뜬다() {
        assertThat(ctx).isNotNull();
        assertThat(ctx.getBeanDefinitionCount()).isGreaterThan(50);
    }

    /**
     * ⭐ 운영과 <b>같은 프로파일</b>을 읽는지 확인한다.
     *
     * <p>테스트 전용 프로파일로 도망가면 이 테스트가 검증하는 것이 사라진다.
     * {@link CsmApplicationTests} 와 <b>같은 프로파일이어야</b> 둘이 갈리지 않는다.
     */
    @Test
    void 운영과_같은_dev_프로파일을_읽는다() {
        assertThat(ctx.getEnvironment().getActiveProfiles())
                .as("test 같은 별도 프로파일을 쓰면 application-dev.properties 를 안 읽게 된다")
                .containsExactly("dev");
    }

    /**
     * ⭐ 운영과 <b>같은 properties 파일</b>을 읽는지 확인한다.
     *
     * <p>프로파일이 같아도 파일을 안 읽으면 소용없다. dev 프로파일에만 있는 값을
     * 실제로 읽어서 확인한다.
     */
    @Test
    void 운영과_같은_properties_를_읽는다() {
        // application.properties (공통)
        assertThat(ctx.getEnvironment().getProperty("csm.sms.price.stale-minutes"))
                .as("공통 properties 를 읽지 않고 있다")
                .isEqualTo("15");

        // application-dev.properties (프로파일별)
        assertThat(ctx.getEnvironment().getProperty("logging.level.com.coresolution.csm"))
                .as("dev 프로파일 properties 를 읽지 않고 있다")
                .isEqualTo("DEBUG");
    }

    /**
     * ⭐ DataSource 는 <b>스텁</b>이어야 한다. 진짜면 CI 가 DB 를 찾다가 실패한다.
     *
     * <p>스텁인지 확인하는 방법은 <b>실제로 연결을 시도해 보는 것</b>이다.
     * 타입 이름만 보면 자동설정이 바뀌었을 때 조용히 통과한다.
     */
    @Test
    void DataSource_는_연결하지_않는_스텁이다() {
        javax.sql.DataSource ds = ctx.getBean(javax.sql.DataSource.class);

        assertThatThrownBy(ds::getConnection)
                .as("진짜 DataSource 면 CI 에서 DB 를 찾다가 실패한다")
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ContextWiringTest");
    }

    /**
     * ⭐⭐ <b>미해결 플레이스홀더가 조용히 남지 않는지</b> 확인한다.
     *
     * <p>── 왜 컨텍스트가 뜨는 것만으로는 부족한가 (재론 금지) ──
     * 스프링은 두 경로에서 다르게 동작한다.
     * <table border="1">
     *   <tr><th>바인딩</th><th>env 가 없으면</th></tr>
     *   <tr><td>{@code @Value("${X}")}</td><td><b>던진다.</b> 기동 실패</td></tr>
     *   <tr><td>{@code @ConfigurationProperties}</td>
     *       <td><b>조용히 {@code "${X}"} 문자열을 그대로 넣는다.</b> 기동 성공</td></tr>
     * </table>
     *
     * <p>실측(2026-08-27): {@code KAKAO_CLIENT_ID} 없이 컨텍스트가 떴고,
     * {@code ClientRegistration.clientId} 가 문자열 {@code "${KAKAO_CLIENT_ID}"} 였다.
     * {@code spring.mail.password} 도 같았다.
     *
     * <p><b>그래서 "기동됐다" 는 "설정이 다 들어왔다" 를 뜻하지 않는다.</b>
     * 카카오 로그인은 배포 후 <b>사용자가 눌러 봐야</b> 깨진 것을 안다.
     * {@code application.properties} 에는 "미설정 시 기동 실패" 라고 적혀 있었는데
     * <b>틀린 주석이었다</b> (이번에 고쳤다).
     *
     * <p>이 검사가 그 구멍을 메운다 — 해석된 값에 {@code ${} 가 남아 있으면 실패시킨다.
     */
    @Test
    void 해석되지_않은_플레이스홀더가_남지_않는다() {
        var env = (org.springframework.core.env.ConfigurableEnvironment) ctx.getEnvironment();
        java.util.List<String> unresolved = new java.util.ArrayList<>();

        for (var source : env.getPropertySources()) {
            if (!(source instanceof org.springframework.core.env.EnumerablePropertySource<?> eps)) {
                continue;
            }
            // application*.properties 만 본다. 시스템 환경변수·테스트 주입은 대상이 아니다.
            if (!eps.getName().contains("application")) {
                continue;
            }
            for (String name : eps.getPropertyNames()) {
                String value = env.getProperty(name);
                if (value != null && value.contains("${")) {
                    unresolved.add(name + " = " + value + "   (출처: " + eps.getName() + ")");
                }
            }
        }

        assertThat(unresolved)
                .as("해석되지 않은 플레이스홀더. 기동은 되지만 그 기능은 런타임에 깨진다.%n"
                        + "필수 환경변수를 배포 절차서 §3 대조표에 넣었는지 확인할 것.")
                .isEmpty();
    }

    // ── 빈 배선 ───────────────────────────────────────────────

    /**
     * ⭐ CSM-2/3/4 로 추가한 빈이 배선되는지 <b>CI 에서</b> 확인한다.
     *
     * <p>이 검증이 없던 동안 빈 5개가 한 번도 컨텍스트에서 확인되지 않은 채 쌓였다.
     */
    @Test
    void CSM_2_3_4_빈이_배선된다() {
        assertThat(ctx.containsBean("platformPriceCache")).isTrue();
        assertThat(ctx.containsBean("platformPriceClient")).isTrue();
        assertThat(ctx.containsBean("platformPricePoller")).isTrue();
        assertThat(ctx.containsBean("smsUsageOutboxService")).isTrue();
        assertThat(ctx.containsBean("smsUsageSender")).isTrue();
        assertThat(ctx.containsBean("platformUsageClient")).isTrue();
    }

    /**
     * {@code @Scheduled} 가 실제로 등록되는지.
     *
     * <p>{@code @EnableScheduling} 이 빠지면 <b>폴러가 조용히 안 돈다.</b>
     * 예외도 로그도 없다 — 단가가 영영 갱신되지 않는 것으로만 드러난다.
     */
    @Test
    void 스케줄링이_켜져_있다() {
        assertThat(ctx.getBeanNamesForAnnotation(
                org.springframework.scheduling.annotation.EnableScheduling.class))
                .as("꺼져 있으면 단가 폴링·사용량 전송이 조용히 멈춘다")
                .isNotEmpty();
    }

    /** JPA·JDBC 세션 자동설정은 빠져야 한다 — 스텁 DataSource 로는 못 뜬다. */
    @Test
    void JPA_와_JDBC_세션은_빠졌다() {
        assertThat(ctx.getBeanNamesForType(jakarta.persistence.EntityManagerFactory.class))
                .as("JPA 가 살아 있으면 스텁 DataSource 로 EntityManagerFactory 를 만들다 실패한다")
                .isEmpty();
        assertThat(ctx.getEnvironment().getProperty("spring.session.store-type"))
                .isEqualTo("none");
    }

    /**
     * 연결하지 않는 {@link javax.sql.DataSource}.
     *
     * <p>{@code getConnection()} 이 <b>즉시</b> 던진다 — 네트워크를 타지 않으므로
     * CI 에서 타임아웃으로 느려지지 않는다.
     *
     * <p>기동 시 DB 를 만지는 빈({@code CsmSchemaBootstrapService},
     * {@code HubMemberService})은 <b>실패를 삼키고 경고만 남기도록</b> 이미 짜여 있다.
     * 그 덕에 컨텍스트는 뜬다. 삼키지 않는 {@code PlatformAdminBootstrapService} 는
     * 위 {@code properties} 에서 껐다.
     */
    @TestConfiguration
    static class StubDataSourceConfig {

        @Bean
        javax.sql.DataSource dataSource() {
            return new AbstractDataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    throw new SQLException(
                            "ContextWiringTest 는 DB 에 붙지 않는다. "
                                    + "DB 가 필요한 검증은 CsmApplicationTests 가 한다.");
                }

                @Override
                public Connection getConnection(String username, String password) throws SQLException {
                    return getConnection();
                }
            };
        }
    }
}
