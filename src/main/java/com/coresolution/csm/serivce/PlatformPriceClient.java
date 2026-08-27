package com.coresolution.csm.serivce;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 플랫폼 단가 조회 클라이언트.
 *
 * <p>── 방향은 한쪽이다 ──
 * <b>csm 이 플랫폼을 부른다. 그 반대는 없다.</b> 플랫폼에서 csm 을 호출하는 경로를
 * 만들면 격리 구조가 깨지고, 플랫폼 장애가 csm 발송을 멈추게 된다.
 * 그래서 "단가를 바꿨으니 지금 당장 가져가라" 는 즉시 트리거도 만들지 않는다.
 *
 * <p>── 실패는 정상 경로다 ──
 * 플랫폼이 아직 배포되지 않았거나 잠시 죽어 있을 수 있다. 그때 csm 은
 * <b>이전 단가로 계속 발송한다.</b> 이 클래스는 실패를 예외로 던지지 않고
 * {@link Optional#empty()} 로 돌려주며, 판단은 호출부가 한다.
 */
@Service
public class PlatformPriceClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${csm.sms.price.platform-base-url:}")
    private String baseUrl;

    @Value("${csm.sms.price.platform-api-key:}")
    private String apiKey;

    @Value("${csm.sms.price.request-timeout-ms:5000}")
    private long requestTimeoutMs;

    /** 설정이 없으면 폴링 자체를 하지 않는다. */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    /**
     * 한 기관의 단가를 가져온다.
     *
     * @param appliedVersion 지금 csm 에 적용된 버전. 플랫폼이 이 값으로
     *                       <b>실제 적용 여부</b>를 판단한다 (PLAT-1).
     *                       안내 문구보다 이 실측값이 낫다.
     * @return 실패하면 empty. <b>예외를 던지지 않는다</b> — 실패는 정상 경로다.
     */
    public Optional<PriceResponse> fetch(String instCode, Integer appliedVersion) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        try {
            StringBuilder uri = new StringBuilder(baseUrl)
                    .append("/internal/prices?instCode=")
                    .append(URI.create("http://x/").resolve("./" + instCode).getPath().substring(1));
            if (appliedVersion != null) {
                uri.append("&appliedVersion=").append(appliedVersion);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri.toString()))
                    .header("X-Internal-Api-Key", apiKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 거부 회신.
     *
     * <p><b>조용히 폴백하지 않는다.</b> 플랫폼 관리자가 "적용됐다" 고 믿는 상태를
     * 막는 것이 목적이다 (PLAT-1). 회신 자체가 실패해도 발송은 계속된다.
     */
    public void reportRejection(PriceRejection rejection) {
        if (!isConfigured()) {
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(rejection);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/internal/price-rejections"))
                    .header("X-Internal-Api-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 회신 실패가 발송을 막지 않는다. 폴링이 다음 회차에 다시 시도한다.
        }
    }

    private Optional<PriceResponse> parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.has("data") ? root.get("data") : root;

        JsonNode versionNode = data.get("version");
        if (versionNode == null || !versionNode.isInt()) {
            return Optional.empty();
        }

        List<ChannelPrice> prices = new ArrayList<>();
        JsonNode items = data.get("items");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                JsonNode channel = item.get("channel");
                JsonNode jeon = item.get("unitCostJeon");
                if (channel == null || jeon == null || !jeon.isInt()) {
                    continue;
                }
                prices.add(new ChannelPrice(
                        channel.asText().toLowerCase(java.util.Locale.ROOT),
                        jeon.asInt()));
            }
        }

        if (prices.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PriceResponse(versionNode.asInt(), prices));
    }

    /** 플랫폼이 돌려준 단가표. 금액은 <b>전 단위 정수</b>다 — 소수를 주고받지 않는다. */
    public record PriceResponse(int version, List<ChannelPrice> items) {
    }

    public record ChannelPrice(String channel, int unitCostJeon) {
    }

    /** 거부 회신 본문. */
    public record PriceRejection(
            String instCode,
            String channel,
            String rejectedValue,
            String reason,
            Integer appliedVersion) {
    }
}
