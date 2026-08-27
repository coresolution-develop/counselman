package com.coresolution.csm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * 전체 컨텍스트 기동 (CSM-7).
 *
 * <p><b>검증하는 것:</b> {@link ContextWiringTest} 가 보는 전부 + <b>DB 에 실제로 붙는 것</b>.
 * DataSource 생성, 스키마 부트스트랩, MyBatis 매퍼 SQL 문법, 세션 저장소(JDBC)까지 돈다.
 *
 * <p><b>실행 조건:</b> 테스트 컨테이너와 필수 환경변수. 둘 다 있어야 한다.
 * <pre>
 *   docker compose -f docker-compose.test.yml up -d
 *   ./scripts/run-context-test.sh
 * </pre>
 *
 * <p>── 왜 {@code @Tag("integration")} 인가, 그리고 그 위험 ──
 * DB 가 필요하므로 DB 없는 CI prod 게이트({@code -PexcludeIntegration})에서 빠진다.
 * <b>그래서 2026-06-17 ~ 08-27 동안 아무도 이 테스트를 보지 않았고, 그 사이 실패
 * 원인이 두 번 바뀌었다.</b> 문서에는 옛 원인(OAuth2)이 남아 있었다.
 *
 * <p>그 공백기에 <b>2026-08-13 mediplat 12.5시간 중단</b>이 났다 —
 * {@code PlaceholderResolutionException} 크래시 루프였고,
 * <b>컨텍스트 테스트가 CI 에서 돌았으면 잡혔을 종류다.</b>
 *
 * <p>그래서 CSM-7 에서 <b>DB 없이 도는 {@link ContextWiringTest} 를 따로 만들어 CI 에 넣었다.</b>
 * 이 테스트는 로컬·컨테이너 환경에서 계속 돈다. 둘 다 필요하다 —
 * 전부 빼는 것과 일부라도 남기는 것은 다르다.
 */
@Tag("integration")
@SpringBootTest
class CsmApplicationTests {

	/** dev 프로파일 기동에 필수인 값. 하나라도 없으면 컨텍스트가 뜨지 않는다. */
	private static final String[] REQUIRED_ENV = {
			"SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
			"LOGIN_AES_KEY", "MEDIPLAT_SSO_SHARED_SECRET", "COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET",
			"BIZPPURIO_DEV_ACCOUNT", "BIZPPURIO_DEV_USERNAME", "BIZPPURIO_DEV_PASSWORD",
			"BIZPPURIO_PROD_ACCOUNT", "BIZPPURIO_PROD_USERNAME", "BIZPPURIO_PROD_PASSWORD",
	};

	/**
	 * ⭐ 환경이 없으면 <b>실패가 아니라 skip</b> 이다.
	 *
	 * <p>── 왜 skip 인가 (재론 금지) ──
	 * 환경 없이 실패로 두면 {@code ./gradlew test} 가 <b>항상 빨갛다.</b> 그러면
	 * 아무도 안 보게 되고, 그 상태에서 원인이 바뀌어도 모른다 —
	 * <b>이 테스트가 정확히 그렇게 2개월을 보냈다</b> (CLAUDE.md §3.2).
	 *
	 * <p>같은 함정에 다시 빠지지 않도록, <b>돌 수 없는 환경에서는 조용히 건너뛰고
	 * 돌 수 있는 환경에서는 반드시 돈다.</b> 리포의 {@code HubFlowIntegrationTest} 와 같은 방식이다.
	 *
	 * <p>실행하려면: {@code ./scripts/run-context-test.sh}
	 */
	@BeforeAll
	static void requireEnvironment() {
		for (String key : REQUIRED_ENV) {
			String value = System.getenv(key);
			assumeTrue(value != null && !value.isBlank(),
					() -> key + " 미설정 — 전체 컨텍스트 테스트를 건너뜁니다. "
							+ "실행하려면: docker compose -f docker-compose.test.yml up -d "
							+ "&& ./scripts/run-context-test.sh");
		}
	}

	@Autowired
	private ApplicationContext ctx;

	@Test
	void contextLoads() {
		assertThat(ctx).isNotNull();
	}

	/**
	 * ⭐ CSM-2/3/4 로 추가한 빈이 <b>실제 컨텍스트에서 배선되는지</b> 확인한다.
	 *
	 * <p>이 검증이 없던 동안 빈 5개가 <b>한 번도 컨텍스트에서 확인되지 않은 채</b>
	 * 쌓였다. 단위 테스트는 전부 초록이었지만 그건 손으로 만든 객체였다.
	 *
	 * <p>{@link ContextWiringTest} 에도 같은 검증이 있다 — 그쪽은 DB 없이 돌아
	 * CI 에서 이걸 지킨다. 여기서는 <b>DB 가 있는 진짜 컨텍스트에서도</b> 같은지 본다.
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
	 * ⭐ {@link ContextWiringTest} 와 <b>같은 프로파일·같은 properties</b> 를 읽는지 확인한다.
	 *
	 * <p>둘이 다른 설정을 읽으면 <b>한쪽만 통과하는 상태</b>가 된다. 그러면 CI 초록이
	 * 로컬 초록을 보장하지 않고, 어느 쪽을 믿어야 할지 모르게 된다.
	 *
	 * <p>{@code ContextWiringTest} 에 같은 이름의 검증이 있다. <b>두 곳에서 같은 값을
	 * 확인하는 것이 요점</b>이다 — 한쪽 설정이 바뀌면 그쪽이 먼저 터진다.
	 */
	@Test
	void ContextWiringTest_와_같은_설정을_읽는다() {
		assertThat(ctx.getEnvironment().getActiveProfiles())
				.as("프로파일이 갈리면 두 테스트가 다른 것을 검증하게 된다")
				.containsExactly("dev");

		assertThat(ctx.getEnvironment().getProperty("csm.sms.price.stale-minutes"))
				.as("공통 properties")
				.isEqualTo("15");
		assertThat(ctx.getEnvironment().getProperty("logging.level.com.coresolution.csm"))
				.as("dev 프로파일 properties")
				.isEqualTo("DEBUG");
	}

	/**
	 * {@code @Autowired(required = false)} 자리가 <b>실제로 채워지는지</b> 확인한다.
	 *
	 * <p>이 자리들은 비어 있어도 컨텍스트가 뜬다 — 그게 설계다 (CSM-3 미배포 대응).
	 * <b>그래서 안 채워져도 아무 데서도 안 터진다.</b> 여기서 보지 않으면
	 * 기능이 조용히 꺼진 채로 배포된다.
	 */
	@Test
	void 선택적_주입_자리가_실제로_채워진다() {
		var page = ctx.getBean(com.coresolution.csm.controller.PageController.class);
		assertThat(org.springframework.test.util.ReflectionTestUtils
				.getField(page, "platformPriceCache"))
				.as("비어 있으면 /core/smssetting 이 전 기관 '수신 이력 없음' 으로 나온다")
				.isNotNull();

		var batch = ctx.getBean(com.coresolution.csm.serivce.SmsBatchService.class);
		assertThat(org.springframework.test.util.ReflectionTestUtils
				.getField(batch, "usageOutbox"))
				.as("비어 있으면 사용량이 아예 적재되지 않는다")
				.isNotNull();
	}
}
