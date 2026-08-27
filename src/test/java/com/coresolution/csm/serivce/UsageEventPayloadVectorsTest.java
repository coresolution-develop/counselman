package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.coresolution.csm.vectors.SharedVectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 사용량 이벤트 payload 를 <b>플랫폼과 공유하는 벡터로</b> 검증한다.
 *
 * <p>── 왜 이 테스트가 생겼나 (재론 금지) ──
 * csm 은 처음부터 {@code billable} · {@code countBasis} 를 보내고
 * {@code totalCostJeon} 을 <b>숫자</b>로 보내고 있었다. 플랫폼 DTO 에는 앞의 둘이
 * 없었고 뒤는 문자열만 받았다. 즉 <b>csm 이 보내는 모든 이벤트가 400 이었다.</b>
 * 4xx 는 영구 실패라({@link PlatformUsageClient}) 재시도 없이 {@code failed_reason}
 * 에 닫히므로, <b>배포했다면 사용량이 한 건도 안 들어갔을 것이다.</b>
 *
 * <p>그런데 양쪽 테스트는 초록이었다. 각자 <b>자기 구현에 맞춘 입력</b>을 손으로
 * 써서 돌렸기 때문이다. 바깥 기준이 없으면 두 시스템이 갈린 것을 원리상 못 본다.
 *
 * <p>── 이 테스트가 무는 것 ──
 * {@link SmsUsageOutboxService#buildPayload} 가 만드는 <b>키 집합과 각 값의 JSON 타입</b>이
 * 벡터의 {@code fields} 와 일치하는지 본다. 필드를 빠뜨리거나, 타입을 바꾸거나,
 * 계약에 없는 필드를 추가하면 여기서 문다.
 *
 * <p>플랫폼 쪽은 같은 벡터로 <b>DTO 가 그 필드를 실제로 받는지</b>를 본다
 * ({@code apps/api/test/usage-event-contract.test.ts}). 두 방향이 다 있어야 계약이다.
 */
class UsageEventPayloadVectorsTest {

    private static final String FILE = "usage-event-vectors.json";

    /** jdbcTemplate 을 쓰지 않는 메서드만 부른다. 스프링 컨텍스트가 필요 없다. */
    private final SmsUsageOutboxService service =
            new SmsUsageOutboxService(null, new ObjectMapper());

    /** ⭐ 벡터가 바뀌었는데 해시를 갱신하지 않았으면 여기서 잡는다. */
    @Test
    void 벡터_파일이_기록된_해시와_일치한다() throws Exception {
        SharedVectors.verifyHash(FILE);
    }

    /**
     * ⭐ <b>이 단언이 이번 사고를 막는 자리다.</b>
     *
     * <p>키 집합을 양방향으로 대조한다. 한쪽만 보면
     * "빠뜨린 것" 이나 "계약에 없는 것을 보내는 것" 중 하나를 놓친다.
     */
    @Test
    void payload_의_키_집합이_계약과_같다() {
        Map<String, Object> payload = service.buildPayload(sampleRow());

        Set<String> actual = new TreeSet<>(payload.keySet());
        Set<String> expected = new TreeSet<>();
        for (JsonNode field : SharedVectors.load(FILE).get("fields")) {
            expected.add(field.get("name").asText());
        }

        assertThat(actual)
                .as("csm 이 만드는 키와 계약(usage-event-vectors.json)의 키가 다르다.%n"
                        + "  플랫폼은 알 수 없는 필드를 400 으로 거부하고, 4xx 는 영구 실패다.%n"
                        + "  필드를 늘렸다면 **플랫폼 리포와 이 벡터를 함께** 고칠 것.")
                .isEqualTo(expected);
    }

    /**
     * ⭐ 각 값의 <b>JSON 타입</b>이 계약과 같은지.
     *
     * <p>키가 맞아도 타입이 갈리면 거부된다 — 실제로 {@code totalCostJeon} 이 그랬다.
     * 자바 쪽에서 {@code long} 을 그대로 넣으면 JSON 숫자가 되고, 플랫폼은 문자열을
     * 기다린다. <b>직렬화한 뒤에 확인한다</b> — Map 의 자바 타입이 아니라 실제로
     * 전선에 나가는 JSON 이 계약이기 때문이다.
     */
    @Test
    void payload_의_JSON_타입이_계약과_같다() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode serialized = mapper.readTree(mapper.writeValueAsString(service.buildPayload(sampleRow())));

        List<String> diffs = new ArrayList<>();
        for (JsonNode field : SharedVectors.load(FILE).get("fields")) {
            String name = field.get("name").asText();
            String want = field.get("jsonType").asText();
            JsonNode value = serialized.get(name);

            // null 은 타입 검사 대상이 아니다. nullable 로 선언됐는지만 본다.
            if (value == null || value.isNull()) {
                if (!field.path("nullable").asBoolean(false)) {
                    diffs.add(name + ": null 인데 계약은 nullable 이 아니다");
                }
                continue;
            }

            String got = jsonTypeOf(value);
            if (!got.equals(want)) {
                diffs.add(name + ": 계약 " + want + " / 실제 " + got);
            }
        }

        assertThat(diffs)
                .as("payload 의 JSON 타입이 계약과 다르다. 플랫폼이 400 으로 거부하고,%n"
                        + "  4xx 는 영구 실패라 그 사용량은 영영 들어가지 않는다.")
                .isEmpty();
    }

    /**
     * 무료 건(OTP)은 {@code billable=false} 로 나간다.
     *
     * <p>{@code totalCostJeon=0} 만으로는 <b>무료 건</b>과 <b>단가가 0으로 잘못 들어온 건</b>이
     * 구분되지 않는다. 둘은 대응이 정반대다 — 전자는 정상, 후자는 사고다.
     */
    @Test
    void 과금_대상_여부가_boolean_으로_나간다() {
        Map<String, Object> row = sampleRow();
        row.put("billable", "N");

        Map<String, Object> payload = service.buildPayload(row);

        // 'N' 을 그대로 보내면 안 된다 — JS 에서 문자열 "N" 은 truthy 라
        // **무료 건이 과금 대상으로 뒤집힌다** (벡터 U07).
        assertThat(payload.get("billable")).isEqualTo(false);
    }

    /** 합계는 문자열이다. 숫자로 보내면 큰 금액이 JS 쪽에서 조용히 틀어진다. */
    @Test
    void 합계_금액은_문자열로_나간다() {
        Map<String, Object> row = sampleRow();
        // csm 의 total_cost 는 BIGINT 다 (CSM-1). 안전 정수 범위를 넘는 값도 담긴다.
        row.put("total_cost", 9007199254740993L);

        Map<String, Object> payload = service.buildPayload(row);

        assertThat(payload.get("totalCostJeon"))
                .as("숫자로 보내면 JSON.parse 가 값을 바꾼다. 그 시점에는 손쓸 수 없다")
                .isEqualTo("9007199254740993");
    }

    private static String jsonTypeOf(JsonNode node) {
        if (node.isTextual()) {
            return "string";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isNumber()) {
            return "number";
        }
        return node.getNodeType().toString().toLowerCase();
    }

    /** {@code sms_batch} 한 행. 컬럼 이름은 {@link SmsUsageOutboxService} 의 조회와 같다. */
    private static Map<String, Object> sampleRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batch_id", "b-0001");
        row.put("inst_code", "COHS");
        row.put("send_type", "SMS");
        row.put("total_count", 100);
        row.put("success_count", 95);
        row.put("failed_count", 3);
        row.put("unknown_count", 2);
        row.put("unit_cost", 1000);
        row.put("total_cost", 97000L);
        row.put("billable", "Y");
        row.put("price_version", 1);
        row.put("created_at", "2026-08-27 21:46:00.0");
        return row;
    }
}
