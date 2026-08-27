package com.coresolution.csm.serivce;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.coresolution.csm.util.JeonFormat;

/**
 * 플랫폼이 배포한 단가의 last-known-good 보관소.
 *
 * <p>── 왜 DB 인가 ──
 * 메모리 캐시면 <b>재시작 직후 비어 있다.</b> 그때 플랫폼이 죽어 있으면 폴백이
 * 한 단계 아래로 떨어지고, <b>발송은 그대로 계속된다.</b> 멈추는 곳이 없으므로
 * 잘못된 단가로 며칠치가 청구된 뒤에야 드러난다.
 * 재시작을 넘겨야 "마지막으로 확인된 단가" 라는 말이 의미를 갖는다.
 *
 * <p>{@code PLATFORM_CACHE_EMPTY} WARN 이 뜨긴 한다. 그러나 그 로그는
 * <b>"플랫폼에서 아직 한 번도 못 받았다" 와 구분되지 않고</b>, 시간당 1회로 억제된다.
 * 로그는 신호일 뿐 방어가 아니다 — 돈은 그동안 계속 움직인다.
 *
 * <p>실측: 캐시를 메모리로 되돌리면 재시작 후 단가가 812전에서
 * <b>폴백값 960전으로 바뀐다</b> ({@code PlatformPriceFallbackIntegrationTest}).
 * 4건이 무는 것으로 이 문단이 뒷받침된다.
 *
 * <p>── 쓰기 경로는 여기 하나뿐이다 ──
 * {@code inst_data_cs.sms_price} 는 이제 <b>플랫폼이 배포한 값을 담는 캐시</b>다.
 * csm 화면에서 수정해도 다음 폴링이 덮어쓴다 (CSM-2 에서 화면을 읽기 전용으로 바꾼다).
 */
@Service
public class PlatformPriceCache {

    private static final Logger log = LoggerFactory.getLogger(PlatformPriceCache.class);

    private final JdbcTemplate jdbcTemplate;

    public PlatformPriceCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 한 기관의 한 채널 단가. 없으면 empty. */
    public Optional<CachedPrice> find(String instCode, String channel) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT unit_cost_jeon, price_version
                    FROM csm.platform_price_cache
                    WHERE inst_code = ? AND channel = ?
                    """, instCode, channel);

            return Optional.of(new CachedPrice(
                    ((Number) row.get("unit_cost_jeon")).intValue(),
                    ((Number) row.get("price_version")).intValue()));
        } catch (Exception e) {
            // 행이 없거나 테이블이 아직 없다. 폴백이 다음 단계로 넘어간다.
            return Optional.empty();
        }
    }

    /**
     * 수신한 단가를 저장한다.
     *
     * <p>{@code inst_data_cs} 도 함께 갱신한다 — 기존 화면·조회가 그 컬럼을 읽기 때문이다.
     * 두 곳이 같은 값을 갖게 유지하되, <b>진실은 이 캐시 테이블</b>이다.
     */
    public void store(String instCode, String channel, int unitCostJeon, int priceVersion) {
        jdbcTemplate.update("""
                INSERT INTO csm.platform_price_cache (inst_code, channel, unit_cost_jeon, price_version)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    unit_cost_jeon = VALUES(unit_cost_jeon),
                    price_version  = VALUES(price_version),
                    received_at    = CURRENT_TIMESTAMP
                """, instCode, channel, unitCostJeon, priceVersion);

        mirrorToInstData(instCode, channel, unitCostJeon, priceVersion);
    }

    /**
     * 이 기관에 적용된 단가 버전. 폴링 파라미터로 플랫폼에 회신한다.
     *
     * <p>플랫폼은 이 값으로 <b>"보낸 버전이 실제로 적용됐는지"</b> 를 판단한다.
     * 관리자 화면에 안내 문구를 띄우는 것보다 이 실측값이 낫다 (PLAT-1).
     */
    public Optional<Integer> appliedVersion(String instCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT MIN(price_version) FROM csm.platform_price_cache WHERE inst_code = ?
                    """, Integer.class, instCode));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 기존 컬럼에도 반영한다.
     *
     * <p>⚠️ {@code inst_data_cs} 의 단가 컬럼은 <b>플랫폼이 배포한 값을 저장하는
     * 읽기 전용 캐시</b>다. csm 내부에서 이 값을 수정하지 않는다 — 다음 폴링이 덮어쓴다.
     * 쓰기 경로는 이 메서드 하나여야 한다.
     *
     * <p>⚠️ <b>이 덮어쓰기가 배포 순서 제약을 만든다.</b> {@code inst_data_cs} 는
     * {@code SmsService} 의 <b>2단계 폴백</b>이기도 하다. 플랫폼에 잘못된 단가가 있는
     * 상태로 연동을 켜면 1·2단계가 동시에 오염된다.
     * 순서는 {@code PlatformPricePoller} 클래스 주석에 있다.
     */
    private void mirrorToInstData(String instCode, String channel, int unitCostJeon, int priceVersion) {
        String column = switch (channel) {
            case "lms" -> "lms_price";
            case "mms" -> "mms_price";
            default -> "sms_price";
        };

        // 전 → 원 문자열. 기존 컬럼이 VARCHAR 라 표기를 유지한다.
        String won = JeonFormat.toWon(unitCostJeon);

        try {
            jdbcTemplate.update(
                    "UPDATE csm.inst_data_cs SET " + column + " = ?, sms_price_version = ? WHERE id_col_03 = ?",
                    won, priceVersion, instCode);
        } catch (Exception e) {
            // 반영 실패가 캐시 저장을 무르게 하지 않는다. 진실은 캐시 테이블이다.
            log.warn("[price-cache] inst={} {} mirror to inst_data_cs failed: {}",
                    instCode, channel, e.toString());
        }
    }

    /**
     * 기관별 <b>수신 상태</b>. 관리자 화면이 "언제 받은 값인가" 를 보여주는 데 쓴다.
     *
     * <p>── 왜 MIN 인가 ──
     * 채널이 3개고 <b>일부만 거부될 수 있다</b> (업무 상한 초과 등). 그러면 그 채널만
     * {@code received_at} 이 멈춘다. 기관의 상태는 <b>가장 오래된 채널</b>을 따라야
     * 한다 — 하나라도 낡았으면 그 기관 단가는 낡은 것이다.
     * {@link #appliedVersion} 이 {@code MIN(price_version)} 인 것과 같은 이유다.
     */
    public Map<String, InstPriceStatus> statuses() {
        String sql = "SELECT inst_code, MIN(price_version) AS applied_version,"
                + " MIN(received_at) AS oldest_received_at"
                + " FROM csm.platform_price_cache GROUP BY inst_code";
        try {
            return jdbcTemplate.query(sql, rs -> {
                Map<String, InstPriceStatus> out = new java.util.LinkedHashMap<>();
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("oldest_received_at");
                    out.put(rs.getString("inst_code"), new InstPriceStatus(
                            (Integer) rs.getObject("applied_version"),
                            ts == null ? null : ts.toInstant()));
                }
                return out;
            });
        } catch (Exception e) {
            // 테이블이 아직 없다 (CSM-3 미배포). 화면은 "수신 이력 없음" 으로 나온다.
            return Map.of();
        }
    }

    /** 캐시에 담긴 단가와 그 버전. */
    public record CachedPrice(int unitCostJeon, int priceVersion) {
    }

    /** 한 기관의 단가 수신 상태. 값이 없으면 {@code null} 이다 — 아직 한 번도 못 받았다. */
    public record InstPriceStatus(Integer appliedVersion, java.time.Instant oldestReceivedAt) {
    }
}
