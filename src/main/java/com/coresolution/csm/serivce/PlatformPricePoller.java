package com.coresolution.csm.serivce;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 단가 폴링.
 *
 * <p>── 즉시 트리거를 만들지 않는다 ──
 * 플랫폼에서 csm 을 호출해 "지금 가져가라" 고 하면 <b>방향이 뒤집혀 격리가 깨진다.</b>
 * 5분 지연은 감수하고, 대신 적용 버전을 회신해 플랫폼 화면이 실측값을 보여준다.
 *
 * <p>── 실패 로깅이 설계의 일부다 ──
 * 플랫폼 미배포 상태에서 5분마다 스택트레이스가 찍히면 <b>진짜 오류가 묻힌다.</b>
 * 최초 실패와 복구만 눈에 띄게 하고, 중간은 시간당 1회로 요약한다.
 *
 * <pre>
 *   1회차 실패   WARN  연결 실패 — 이전 단가를 유지합니다. (연속 1회)
 *   2~N회차      (조용히 센다)
 *   1시간마다    WARN  연속 12회 실패 중 (최초 08:00). 이전 단가로 발송하고 있습니다.
 *   복구         INFO  복구됨 — 55분간 12회 실패했습니다.
 * </pre>
 *
 * §3.2 의 노란색 판단, CSM-7 의 상시 빨간색 회피와 같은 계열이다 —
 * <b>항상 켜져 있는 경고는 곧 아무도 안 본다.</b>
 *
 * <p>━━━ ⚠️ 배포 순서 (체크리스트를 안 보고 배포하는 경우가 있어 여기에도 남긴다) ━━━
 *
 * <p>{@code CSM_PRICE_PLATFORM_BASE_URL} 은 <b>맨 마지막에 주입한다.</b>
 * <ol>
 *   <li><b>csm 배포</b> — URL 미설정. 폴러가 조용히 쉬고, 단가는 기존
 *       {@code inst_data_cs} → 프로퍼티 폴백으로 <b>배포 전과 똑같이</b> 동작한다.</li>
 *   <li><b>플랫폼 단가 API 가동 + 기관별 단가 시드 확인</b> —
 *       실제 조회해서 기관코드·금액이 맞는지 눈으로 본다.</li>
 *   <li><b>csm 에 URL·API 키 주입 후 재시작</b> — 이때부터 폴링이 돈다.</li>
 * </ol>
 *
 * <p><b>순서를 뒤집으면 폴백까지 오염된다.</b> {@code PlatformPriceCache.store()} 가
 * {@code inst_data_cs} 를 <b>덮어쓰기</b> 때문이다. 플랫폼에 잘못된 단가가 있는 상태로
 * URL 을 주입하면 1단계(캐시)뿐 아니라 <b>2단계 폴백(inst_data_cs)까지 같은
 * 잘못된 값</b>이 된다. 되돌리려면 URL 을 빼는 것으로 부족하고 두 곳을 다 고쳐야 한다.
 * 깨끗하게 남는 것은 3단계(프로퍼티)뿐이다.
 *
 * <p>━━━ ⛔ 1차 배포에 CSM-2(단가 화면 읽기 전용화)를 같이 담지 않는다 ━━━
 *
 * <p>1차 배포의 요점은 <b>"배포했지만 아무것도 안 변한다"</b> 이다 — URL 이 없으면
 * 이 폴러가 쉬고 단가 동작이 배포 전과 같다. 그래야 공지 없이 나갈 수 있다.
 *
 * <p><b>화면 변경은 URL 과 무관하게 바로 보인다.</b> 같이 담으면 그 전제가 깨지고,
 * "단가를 고쳐도 5분 뒤 되돌아가는" 중간 상태를 운영자에게 설명해야 한다.
 * 그 설명은 CSM-2 가 들어가는 순간 무의미해진다. 재시작 1회 추가는 감수한다.
 *
 * <p>절차서: {@code docs/prod-deploy-checklist.md} §9 (묶음은 §9.0)
 */
@Service
public class PlatformPricePoller {

    private static final Logger log = LoggerFactory.getLogger(PlatformPricePoller.class);

    /** 연속 실패 중 요약 로그를 남기는 간격. */
    private static final Duration SUMMARY_INTERVAL = Duration.ofHours(1);

    private final PlatformPriceClient client;
    private final PlatformPriceCache cache;
    private final JdbcTemplate jdbcTemplate;

    /** 업무 상한. 플랫폼이 보낸 값이라도 이걸 넘으면 거부한다. */
    @Value("${csm.sms.price.max-jeon:100000}")
    private int maxJeon;

    // ── 실패 상태 (로깅 전용) ────────────────────────────────
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> firstFailureAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastSummaryAt = new AtomicReference<>();

    public PlatformPricePoller(PlatformPriceClient client, PlatformPriceCache cache,
            JdbcTemplate jdbcTemplate) {
        this.client = client;
        this.cache = cache;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 5분마다. 단가 변경은 최대 5분 뒤에 반영된다. */
    @Scheduled(fixedDelayString = "${csm.sms.price.poll-interval-ms:300000}",
            initialDelayString = "${csm.sms.price.poll-initial-delay-ms:30000}")
    public void poll() {
        if (!client.isConfigured()) {
            // 설정이 없으면 조용히 지나간다. 실패가 아니다.
            return;
        }

        List<String> instCodes = activeInstCodes();
        if (instCodes.isEmpty()) {
            return;
        }

        int succeeded = 0;
        int failed = 0;

        for (String instCode : instCodes) {
            if (pollOne(instCode)) {
                succeeded++;
            } else {
                failed++;
            }
        }

        // 전부 실패했으면 플랫폼이 죽은 것으로 본다. 일부 실패는 그 기관 문제다.
        if (succeeded == 0 && failed > 0) {
            noteFailure(failed);
        } else {
            noteSuccess();
        }
    }

    private boolean pollOne(String instCode) {
        Integer applied = cache.appliedVersion(instCode).orElse(null);
        Optional<PlatformPriceClient.PriceResponse> response = client.fetch(instCode, applied);

        if (response.isEmpty()) {
            return false;
        }

        PlatformPriceClient.PriceResponse price = response.get();
        for (PlatformPriceClient.ChannelPrice item : price.items()) {
            Optional<String> rejection = validate(item.unitCostJeon());

            if (rejection.isPresent()) {
                // **조용히 폴백하지 않는다.** 플랫폼이 "적용됐다" 고 믿게 두지 않는다.
                log.warn("[price-poll] inst={} channel={} 거부 — {} (값 {}전)",
                        instCode, item.channel(), rejection.get(), item.unitCostJeon());
                client.reportRejection(new PlatformPriceClient.PriceRejection(
                        instCode, item.channel(), String.valueOf(item.unitCostJeon()),
                        rejection.get(), applied));
                continue;
            }

            cache.store(instCode, item.channel(), item.unitCostJeon(), price.version());
        }
        return true;
    }

    /**
     * 업무 상한 로컬 가드.
     *
     * <p>플랫폼 쪽 실수가 csm 과금에 그대로 흘러드는 것을 막는다.
     * <b>거부하면 이전 값이 유지된다</b> — 폴백이 그 자리를 메운다.
     */
    private Optional<String> validate(int unitCostJeon) {
        if (unitCostJeon < 0) {
            return Optional.of("NEGATIVE");
        }
        if (unitCostJeon > maxJeon) {
            return Optional.of("EXCEEDS_LOCAL_MAX");
        }
        return Optional.empty();
    }

    private List<String> activeInstCodes() {
        try {
            return jdbcTemplate.queryForList("""
                    SELECT id_col_03 FROM csm.inst_data_cs
                    WHERE id_col_03 IS NOT NULL AND id_col_03 <> ''
                      AND (id_col_04 IS NULL OR LOWER(id_col_04) <> 'n')
                    """, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── 실패 로깅 ────────────────────────────────────────────

    private void noteFailure(int instCount) {
        int count = consecutiveFailures.incrementAndGet();
        Instant now = Instant.now();

        if (count == 1) {
            firstFailureAt.set(now);
            lastSummaryAt.set(now);
            log.warn("[price-poll] 플랫폼 연결 실패 — 이전 단가를 유지합니다. "
                    + "(연속 1회, 기관 {}곳)", instCount);
            return;
        }

        // 2회차부터는 조용히 센다. 시간당 한 번만 요약한다.
        Instant lastSummary = lastSummaryAt.get();
        if (lastSummary != null && Duration.between(lastSummary, now).compareTo(SUMMARY_INTERVAL) >= 0) {
            lastSummaryAt.set(now);
            log.warn("[price-poll] 연속 {}회 실패 중 (최초 {}). 이전 단가로 발송하고 있습니다.",
                    count, firstFailureAt.get());
        }
    }

    private void noteSuccess() {
        int failures = consecutiveFailures.getAndSet(0);
        if (failures == 0) {
            return;
        }

        Instant first = firstFailureAt.getAndSet(null);
        lastSummaryAt.set(null);

        // 복구는 반드시 남긴다. 얼마나 오래 이전 단가로 발송했는지가 정보다.
        if (first != null) {
            long minutes = Duration.between(first, Instant.now()).toMinutes();
            log.info("[price-poll] 복구됨 — {}분간 {}회 실패했습니다.", minutes, failures);
        } else {
            log.info("[price-poll] 복구됨 — {}회 실패 후 정상화.", failures);
        }
    }

    /** 테스트·운영 점검용. 지금 연속 몇 회 실패 중인지. */
    public int consecutiveFailureCount() {
        return consecutiveFailures.get();
    }
}
