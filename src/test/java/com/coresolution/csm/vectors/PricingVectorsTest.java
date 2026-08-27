package com.coresolution.csm.vectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.coresolution.csm.util.JeonFormat;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 단가 파싱을 <b>플랫폼 벡터 파일로</b> 검증한다 (CSM-5).
 *
 * <p>── 전사본을 대체한다 ──
 * 예전에는 {@code JeonFormatRoundTripTest} 에 P01~P20 을 <b>손으로 옮겨</b> 뒀다.
 * 옮긴 것은 갈린다 — 원본이 바뀌어도 사본은 그대로다.
 * 이제 <b>파일 자체를 읽어</b> 돌리므로 케이스가 늘면 자동으로 같이 돈다.
 *
 * <p>(대조 결과 전사본은 실제로 20/20 일치했다. <b>운이 좋았던 것이지 보장은 아니었다</b> —
 * 갈렸는지 확인할 장치가 없었다.)
 *
 * <p>── csm 은 거부 <b>사유</b>를 구분하지 않는다 ──
 * 플랫폼은 {@code TOO_MANY_DECIMALS} / {@code NEGATIVE} / {@code NOT_NUMERIC} 을 나눠
 * 관리자 화면에 보여준다. csm 은 <b>거부하고 폴백</b>하면 그만이라
 * {@link JeonFormat#parseWonToJeon} 이 {@code null} 하나로 답한다.
 * 그래서 이 테스트는 <b>ok/거부와 금액</b>만 대조한다 — 그게 두 시스템이 갈리면
 * 안 되는 전부다.
 */
class PricingVectorsTest {

    private static final String FILE = "pricing-vectors.json";

    /** ⭐ 벡터가 바뀌었는데 해시를 갱신하지 않았으면 여기서 잡는다. */
    @Test
    void 벡터_파일이_기록된_해시와_일치한다() throws Exception {
        SharedVectors.verifyHash(FILE);
    }

    /**
     * ⭐ {@code parseUnitPrice} 케이스 전부를 csm 파서로 돌린다.
     *
     * <p>한 건씩 끊지 않고 <b>전부 모아서 한 번에</b> 보고한다 —
     * 첫 실패에서 멈추면 몇 개가 갈렸는지 알 수 없다.
     */
    @Test
    void 모든_단가_파싱_케이스가_플랫폼과_같은_결과를_낸다() {
        JsonNode root = SharedVectors.load(FILE);
        List<String> diffs = new ArrayList<>();
        int checked = 0;

        for (JsonNode c : root.get("parseUnitPrice")) {
            checked++;
            String input = SharedVectors.stringOrNull(c.get("input"));
            JsonNode expect = c.get("expect");
            Long actual = JeonFormat.parseWonToJeon(input);

            if (expect.get("ok").asBoolean()) {
                long wantJeon = expect.get("jeon").asLong();
                if (actual == null || actual != wantJeon) {
                    diffs.add(SharedVectors.describe(c) + ": 기대 " + wantJeon + "전, 실제 " + actual
                            + "   — " + c.path("note").asText(""));
                }
            } else if (actual != null) {
                diffs.add(SharedVectors.describe(c) + ": 거부해야 하는데 " + actual + "전으로 통과"
                        + "   (" + expect.path("reason").asText() + ")"
                        + "   — " + c.path("note").asText(""));
            }
        }

        assertThat(checked)
                .as("벡터 파일이 비었거나 그룹 이름이 바뀌었다")
                .isEqualTo(SharedVectors.size(root, "parseUnitPrice"))
                .isGreaterThan(0);

        assertThat(diffs)
                .as("csm 파서가 플랫폼 벡터와 갈렸다. 같은 문자열이 두 시스템에서 "
                        + "다른 단가가 되면 청구액이 갈린다.")
                .isEmpty();
    }

    /**
     * 벡터 파일이 <b>실제로 읽히고 있는지</b> 확인한다.
     *
     * <p>파일을 못 읽으면 위 테스트는 케이스 0건으로 <b>조용히 통과</b>할 수 있다.
     * 아는 케이스 하나를 콕 집어 확인해서 그 상황을 막는다.
     */
    @Test
    void 벡터_파일을_실제로_읽고_있다() {
        JsonNode root = SharedVectors.load(FILE);

        assertThat(SharedVectors.size(root, "parseUnitPrice"))
                .as("P01~P20 이 있어야 한다")
                .isGreaterThanOrEqualTo(20);

        boolean hasP01 = false;
        for (JsonNode c : root.get("parseUnitPrice")) {
            if ("P01".equals(c.path("id").asText())) {
                assertThat(c.get("input").asText()).isEqualTo("9.6");
                assertThat(c.get("expect").get("jeon").asInt()).isEqualTo(960);
                hasP01 = true;
            }
        }
        assertThat(hasP01).as("P01 이 없다 — 벡터 파일이 바뀌었는지 확인할 것").isTrue();
    }

    /**
     * ⭐ 벡터 파일이 <b>플랫폼 워크플로가 기대하는 경로</b>에 있는지 확인한다.
     *
     * <p>플랫폼 {@code vectors-cross-check.yml} 은 야간에
     * {@code counselman/src/test/resources/pricing-vectors.json} 을 읽는다.
     * 위치가 어긋나면 <b>"사본이 없다" 로 매일 빨개지고</b>, 그러면 곧 아무도 안 본다.
     *
     * <p>처음에 {@code src/test/resources/vectors/} 하위에 뒀다가 실제로 어긋났다.
     * 파일을 옮기려면 <b>플랫폼 워크플로도 같이</b> 고쳐야 한다.
     */
    @Test
    void 플랫폼_워크플로가_찾는_경로에_있다() {
        for (String name : new String[] {
                "pricing-vectors.json", "inst-code-vectors.json", "vectors.sha256" }) {
            assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("src/test/resources", name)))
                    .as("플랫폼 야간 교차 검증이 src/test/resources/%s 를 읽는다. "
                            + "옮기려면 sms-platform 의 vectors-cross-check.yml 도 같이 고칠 것.", name)
                    .isTrue();
        }
    }

    /**
     * 벡터의 단위가 <b>전(錢)</b> 인지 확인한다.
     *
     * <p>플랫폼이 단위를 원으로 바꾸면 모든 기대값이 100배 달라진다.
     * 그때 이 테스트가 <b>기대값 대조보다 먼저</b> 터져서 무슨 일인지 알려 준다.
     */
    @Test
    void 벡터의_단위가_전이다() {
        assertThat(SharedVectors.load(FILE).path("unit").asText())
                .as("단위가 바뀌면 모든 금액이 100배 어긋난다")
                .contains("jeon");
    }
}
