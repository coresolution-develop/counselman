package com.coresolution.csm.support;

import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트가 붙은 DB 가 <b>정말 테스트 DB 인지</b> 확인한다.
 *
 * <p>── 왜 필요한가 ──
 * csm 은 접속 정보를 환경변수로만 받는다. 셸에 운영 값이 남아 있거나 IDE 실행 구성이
 * 잘못돼 있으면, 테스트가 <b>병원 6곳이 쓰는 운영 DB</b> 에 붙는다.
 * 플랫폼 쪽에서 계약 테스트가 개발 DB 를 통째로 비운 사고가 있었다.
 * 그때는 개발 DB 였지만 여기서 같은 일이 나면 실제 환자 상담 데이터가 사라진다.
 *
 * <p>── 무엇으로 판정하는가 ──
 * 접속 문자열만 믿지 않는다. 문자열은 맞는데 터널이 다른 곳으로 연결돼 있을 수 있다
 * (Colima 포워딩·SSH 터널이 섞인 환경이다). <b>DB 안에 심어 둔 마커</b>를 읽어
 * 확인한다. 마커가 없으면 테스트 DB 가 아니다.
 *
 * <p>이 마커는 {@code docker-compose.test.yml} 로 띄운 컨테이너에만 만든다.
 * 운영·개발 DB 에는 없으므로, 잘못 붙으면 마커를 못 찾고 즉시 실패한다.
 */
public final class TestDatabaseGuard {

    /** 테스트 DB 임을 표시하는 테이블. 운영에는 존재하지 않는다. */
    public static final String MARKER_TABLE = "csm_test_marker";

    /** 마커에 들어가는 값. 컨테이너 이름과 같게 둔다. */
    public static final String MARKER_VALUE = "csm-test-mysql";

    private TestDatabaseGuard() {
    }

    /**
     * 테스트 DB 가 아니면 던진다.
     *
     * <p>모든 DB 통합 테스트의 {@code @BeforeAll} 에서 부른다.
     * 한 곳이라도 빠뜨리면 그 테스트가 운영 DB 를 건드릴 수 있다.
     */
    public static void assertTestDatabase(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        String url = describeUrl(jdbc);

        // 1) 마커 테이블이 있는가.
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, MARKER_TABLE);

        if (tableCount == null || tableCount == 0) {
            throw new IllegalStateException(
                    "테스트 DB 가 아닙니다. 마커 테이블 '" + MARKER_TABLE + "' 이 없습니다.\n"
                            + "  접속 대상: " + url + "\n"
                            + "  테스트 DB 를 띄우려면: docker compose -f docker-compose.test.yml up -d\n"
                            + "  ⚠️ 운영·개발 DB 에 붙은 것일 수 있습니다. 접속 정보를 확인하세요.");
        }

        // 2) 마커 값이 맞는가. 테이블만 흉내 낸 경우를 막는다.
        List<String> values = jdbc.queryForList("SELECT marker FROM " + MARKER_TABLE, String.class);
        if (!values.contains(MARKER_VALUE)) {
            throw new IllegalStateException(
                    "테스트 DB 마커 값이 다릅니다. 기대 '" + MARKER_VALUE + "', 실제 " + values + "\n"
                            + "  접속 대상: " + url);
        }
    }

    /**
     * 마커를 심는다. 테스트 DB 준비 단계에서 한 번 부른다.
     *
     * <p><b>이 메서드도 스스로를 보호한다.</b> 접속 대상이 테스트 포트가 아니면 던진다 —
     * 운영 DB 에 마커를 심어 버리면 가드가 통째로 무력화된다.
     */
    public static void installMarker(DataSource dataSource, int expectedPort) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String url = describeUrl(jdbc).toLowerCase(Locale.ROOT);

        if (!url.contains(":" + expectedPort + "/")) {
            throw new IllegalStateException(
                    "테스트 포트가 아닌 곳에 마커를 심으려 했습니다.\n"
                            + "  기대 포트: " + expectedPort + "\n"
                            + "  접속 대상: " + url + "\n"
                            + "  ⚠️ 운영 DB 에 마커를 심으면 가드가 무력화됩니다.");
        }

        jdbc.execute("CREATE TABLE IF NOT EXISTS " + MARKER_TABLE
                + " (marker VARCHAR(60) NOT NULL PRIMARY KEY)");
        jdbc.update("INSERT IGNORE INTO " + MARKER_TABLE + " (marker) VALUES (?)", MARKER_VALUE);
    }

    private static String describeUrl(JdbcTemplate jdbc) {
        try {
            return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                    connection.getMetaData().getURL());
        } catch (Exception e) {
            return "(주소를 읽을 수 없음: " + e + ")";
        }
    }
}
