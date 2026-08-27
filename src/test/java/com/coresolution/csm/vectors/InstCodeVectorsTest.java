package com.coresolution.csm.vectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.coresolution.csm.serivce.CsmSchemaBootstrapService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 기관코드 정규화를 <b>플랫폼 벡터 파일로</b> 검증한다 (CSM-5).
 *
 * <p>── 왜 갈리면 안 되나 ──
 * 같은 기관이 두 시스템에서 다른 코드가 되면 <b>단가·사용량이 서로 다른 기관에 붙는다.</b>
 * 플랫폼은 {@code COHS} 로 단가를 배포하는데 csm 이 {@code cohs} 로 찾으면 못 받는다.
 *
 * <p>실제로 {@code hsop_0001} 이 검증 없이 들어와 테이블이 두 표기로 갈라졌다 (약 30쌍).
 *
 * <p>── {@code normalizeInstCode} 와 {@code requireValidInstCode} 는 다른 일이다 ──
 * 벡터가 고정하는 것은 <b>정규화</b>다 — 기존 데이터를 읽을 때 쓴다.
 * <b>검증</b>({@code requireValidInstCode})은 새로 들어올 때만 쓰고 더 좁다.
 * 그쪽은 csm 고유 규칙이라 {@code InstCodeValidationTest} 가 따로 본다.
 */
class InstCodeVectorsTest {

    private static final String FILE = "inst-code-vectors.json";

    @Test
    void 벡터_파일이_기록된_해시와_일치한다() throws Exception {
        SharedVectors.verifyHash(FILE);
    }

    /** ⭐ {@code normalizeInstCode} 케이스 전부를 csm 구현으로 돌린다. */
    @Test
    void 모든_정규화_케이스가_플랫폼과_같은_결과를_낸다() {
        JsonNode root = SharedVectors.load(FILE);
        List<String> diffs = new ArrayList<>();
        int checked = 0;

        for (JsonNode c : root.get("normalizeInstCode")) {
            checked++;
            String input = SharedVectors.stringOrNull(c.get("input"));
            String want = SharedVectors.stringOrNull(c.get("expect"));
            String actual = CsmSchemaBootstrapService.normalizeInstCode(input);

            if (want == null ? actual != null : !want.equals(actual)) {
                diffs.add(SharedVectors.describe(c) + ": 기대 " + quote(want) + ", 실제 " + quote(actual)
                        + "   — " + c.path("note").asText(""));
            }
        }

        assertThat(checked)
                .isEqualTo(SharedVectors.size(root, "normalizeInstCode"))
                .isGreaterThan(0);

        assertThat(diffs)
                .as("csm 정규화가 플랫폼 벡터와 갈렸다. 같은 기관이 두 시스템에서 "
                        + "다른 코드가 되면 단가·사용량이 엉뚱한 기관에 붙는다.")
                .isEmpty();
    }

    /**
     * 벡터 파일을 실제로 읽고 있는지. 못 읽으면 위 테스트가 0건으로 조용히 통과한다.
     *
     * <p>{@code core} 예약어는 특히 콕 집어 둔다 — 이게 갈리면
     * <b>SUPER 권한 판정이 어긋난다.</b>
     */
    @Test
    void 벡터_파일을_실제로_읽고_있다() {
        JsonNode root = SharedVectors.load(FILE);
        assertThat(SharedVectors.size(root, "normalizeInstCode")).isGreaterThanOrEqualTo(13);

        boolean hasCore = false;
        for (JsonNode c : root.get("normalizeInstCode")) {
            if ("CORE".equals(SharedVectors.stringOrNull(c.get("input")))) {
                assertThat(SharedVectors.stringOrNull(c.get("expect")))
                        .as("core 예약어는 소문자를 유지해야 SUPER 판정이 맞는다")
                        .isEqualTo("core");
                hasCore = true;
            }
        }
        assertThat(hasCore).as("CORE 케이스가 없다 — 벡터 파일이 바뀌었는지 확인할 것").isTrue();
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }
}
