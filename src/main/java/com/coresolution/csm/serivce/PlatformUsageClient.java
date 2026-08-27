package com.coresolution.csm.serivce;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 사용량 이벤트 전송 (CSM-4).
 *
 * <p>단가 수신({@link PlatformPriceClient})과 같은 설정을 쓴다 — 같은 플랫폼이다.
 * 둘 중 하나만 켜지는 상황을 만들지 않는다.
 *
 * <p>── 4xx 와 5xx 를 구분해서 돌려주는 이유 ──
 * <b>4xx 는 영구 실패다.</b> 형식 오류를 무한 재시도하면 큐가 막히고,
 * 그 뒤의 정상 이벤트까지 못 나간다. 5xx·네트워크만 재시도한다.
 */
@Service
public class PlatformUsageClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformUsageClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${csm.sms.price.platform-base-url:}")
    private String baseUrl;

    @Value("${csm.sms.price.platform-api-key:}")
    private String apiKey;

    @Value("${csm.sms.usage.request-timeout-ms:5000}")
    private int requestTimeoutMs;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    /**
     * 한 건 보낸다. <b>예외를 던지지 않는다</b> — 실패는 정상 경로다.
     *
     * @param payload outbox 에 저장해 둔 JSON 을 <b>그대로</b> 보낸다.
     *                여기서 다시 만들면 "보내려던 것" 과 "보낸 것" 이 갈린다.
     */
    public Result send(String payload) {
        return sendTo("/internal/usage-events", payload);
    }

    /**
     * 임의의 내부 엔드포인트로 보낸다 (CSM-6 이 기관 통지에 쓴다).
     *
     * <p>인증·타임아웃·4xx/5xx 판정을 <b>한 곳에 모은다.</b> 경로마다 클라이언트를
     *따로 만들면 그 규칙이 갈린다 — 한쪽만 4xx 를 재시도하는 식으로.
     */
    public Result sendTo(String path, String payload) {
        if (!isConfigured()) {
            return Result.retryableFailure("연동 미설정");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("X-Internal-Api-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                return Result.succeeded();
            }
            if (status >= 400 && status < 500) {
                // **재시도하지 않는다.** 같은 payload 를 다시 보내도 같은 답이 온다.
                // 409(이미 수신)도 여기 들어온다 — 멱등 처리된 것이므로 재시도할 이유가 없다.
                return Result.permanentFailure("HTTP " + status + " " + truncate(response.body()));
            }
            return Result.retryableFailure("HTTP " + status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retryableFailure("중단됨");
        } catch (Exception e) {
            return Result.retryableFailure(e.toString());
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200);
    }

    /**
     * @param ok        성공
     * @param permanent 영구 실패. 재시도하지 않고 {@code failed_reason} 에 남긴다
     * @param reason    실패 사유. 성공이면 {@code null}
     */
    public record Result(boolean ok, boolean permanent, String reason) {

        static Result succeeded() {
            return new Result(true, false, null);
        }

        /** 재시도해도 같은 답이 온다. {@code failed_reason} 에 남기고 닫는다. */
        static Result permanentFailure(String reason) {
            return new Result(false, true, reason);
        }

        /** 일시적 실패. 백오프 후 다시 시도한다. */
        static Result retryableFailure(String reason) {
            return new Result(false, false, reason);
        }
    }
}
