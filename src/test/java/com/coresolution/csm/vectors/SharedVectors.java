package com.coresolution.csm.vectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 플랫폼과 공유하는 벡터 파일 로더 (CSM-5).
 *
 * <p>── 왜 사본인가, submodule 이 아니라 ──
 * csm 빌드가 플랫폼 리포에 의존하면 안 된다. 플랫폼이 깨져도 csm 은 빌드·배포돼야 한다.
 * 대신 <b>SHA-256 트립와이어</b>로 한쪽이 고쳐진 것을 잡는다.
 *
 * <p>── 이 방식의 한계 (알고 쓸 것) ──
 * 해시 파일도 사본이라, <b>양쪽 해시를 각각 따로 고치면 둘 다 통과한다.</b>
 * 그 구멍은 야간 교차 검증(플랫폼 {@code vectors-cross-check.yml})이 메운다.
 * 벡터 규칙을 바꿀 때는 <b>두 리포를 같은 날 함께</b> 고친다.
 *
 * <p>원본: {@code sms-platform/packages/shared/*.json}
 */
public final class SharedVectors {

    /**
     * ⚠️ 이 경로는 <b>플랫폼 워크플로가 기대하는 경로</b>다
     * ({@code vectors-cross-check.yml} 이 {@code counselman/src/test/resources/$file} 을 본다).
     *
     * <p>처음에 {@code vectors/} 하위에 뒀다가 어긋난 것을 발견해 옮겼다.
     * 그대로 뒀으면 야간 검증이 "사본이 없다" 로 <b>매일 빨개졌을 것</b>이고,
     * 그러면 곧 아무도 안 보게 된다 (CLAUDE.md §3.2).
     *
     * <p><b>옮기려면 플랫폼 워크플로도 같이 고쳐야 한다.</b>
     */
    private static final Path DIR = Path.of("src/test/resources");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, JsonNode> CACHE = new HashMap<>();

    private SharedVectors() {
    }

    /** 벡터 파일을 읽는다. 파일이 없거나 깨졌으면 그 자리에서 실패한다. */
    public static synchronized JsonNode load(String fileName) {
        return CACHE.computeIfAbsent(fileName, name -> {
            try {
                return MAPPER.readTree(DIR.resolve(name).toFile());
            } catch (IOException e) {
                throw new IllegalStateException(
                        "벡터 파일을 읽지 못했다: " + DIR.resolve(name) + "\n"
                                + "  플랫폼에서 복사한다: sms-platform/packages/shared/" + name, e);
            }
        });
    }

    /**
     * 기록된 해시와 실제 파일이 일치하는지.
     *
     * <p>여기서 실패하면 <b>누군가 벡터를 고쳤는데 해시를 갱신하지 않았거나,
     * 해시만 고치고 파일을 안 옮긴 것</b>이다. 둘 다 두 시스템의 규칙이 갈리는 상황이다.
     */
    public static void verifyHash(String fileName) throws Exception {
        String expected = expectedHash(fileName);
        assertThat(expected)
                .as("vectors.sha256 에 %s 항목이 없다", fileName)
                .isNotNull();

        assertThat(sha256(DIR.resolve(fileName)))
                .as("%s 의 해시가 vectors.sha256 과 다르다.%n"
                        + "  벡터를 고쳤으면 **플랫폼 리포도 같이** 고치고 양쪽 해시를 갱신할 것.%n"
                        + "  플랫폼: pnpm vectors:update", fileName)
                .isEqualTo(expected);
    }

    static String expectedHash(String fileName) throws IOException {
        for (String line : Files.readAllLines(DIR.resolve("vectors.sha256"))) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[1].equals(fileName)) {
                return parts[0];
            }
        }
        return null;
    }

    static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** 벡터 파일에 적힌 케이스 수. "몇 건을 돌렸는지" 를 로그가 아니라 단언으로 남긴다. */
    public static int size(JsonNode root, String group) {
        JsonNode node = root.get(group);
        assertThat(node)
                .as("벡터 파일에 '%s' 그룹이 없다", group)
                .isNotNull();
        return node.size();
    }

    static String describe(JsonNode c) {
        JsonNode input = c.get("input");
        return c.path("id").asText("?") + " (" + (input == null || input.isNull()
                ? "null" : "\"" + input.asText() + "\"") + ")";
    }

    /** {@code null} JSON 노드와 실제 {@code null} 을 구분해서 문자열로. */
    static String stringOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    static {
        // UTF-8 로 읽히는지 한 번 확인한다. 벡터에 한글 주석·기대값이 들어 있다.
        assertThat(StandardCharsets.UTF_8.name()).isEqualTo("UTF-8");
    }
}
