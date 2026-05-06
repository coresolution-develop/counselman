package com.coresolution.mediplat.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.mediplat.model.Newsletter;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class NewsletterAiRecommendationService {

    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public NewsletterAiRecommendationService(
            ObjectMapper objectMapper,
            @Value("${platform.newsletter.ai.api-key:}") String apiKey,
            @Value("${platform.newsletter.ai.model:gpt-5.2}") String model,
            @Value("${platform.newsletter.ai.enabled:false}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = StringUtils.hasText(model) ? model.trim() : "gpt-5.2";
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public List<Map<String, Object>> recommend(
            PlatformSessionUser user,
            List<Newsletter> newsletters,
            Set<String> subscribedCodes,
            Map<String, String> feedbackByCode) {
        if (!isAvailable() || newsletters == null || newsletters.isEmpty()) {
            return List.of();
        }

        List<Newsletter> candidates = newsletters.stream()
                .filter(newsletter -> !subscribedCodes.contains(newsletter.getNewsletterCode()))
                .filter(newsletter -> !"DISLIKE".equalsIgnoreCase(feedbackByCode.get(newsletter.getNewsletterCode())))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        try {
            Map<String, Object> requestBody = buildRequestBody(user, newsletters, subscribedCodes, feedbackByCode);
            HttpRequest request = HttpRequest.newBuilder(RESPONSES_URI)
                    .timeout(Duration.ofSeconds(4))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            return toRecommendations(response.body(), candidates, subscribedCodes);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public boolean isAvailable() {
        return enabled && StringUtils.hasText(apiKey);
    }

    private Map<String, Object> buildRequestBody(
            PlatformSessionUser user,
            List<Newsletter> newsletters,
            Set<String> subscribedCodes,
            Map<String, String> feedbackByCode) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("user", Map.of(
                "institutionCode", safe(user == null ? "" : user.getInstCode()),
                "role", safe(user == null ? "" : user.getRoleCode())));
        context.put("subscribedCodes", new ArrayList<>(subscribedCodes));
        context.put("likedCodes", feedbackByCode.entrySet().stream()
                .filter(entry -> "LIKE".equalsIgnoreCase(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList());
        context.put("hiddenCodes", feedbackByCode.entrySet().stream()
                .filter(entry -> "DISLIKE".equalsIgnoreCase(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList());
        context.put("catalog", newsletters.stream().map(this::toPromptItem).toList());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("input", List.of(
                Map.of(
                        "role", "developer",
                        "content", """
                                You recommend healthcare operations news articles for a MediPlat portal user.
                                Select up to 3 article codes from the catalog that are not already saved.
                                Do not select hiddenCodes. Treat likedCodes as positive interest signals.
                                Prefer practical operational relevance to the user's saved articles and role.
                                Return concise Korean reasons. Do not invent codes.
                                """),
                Map.of(
                        "role", "user",
                        "content", objectToJson(context))));
        request.put("text", Map.of("format", recommendationSchema()));
        return request;
    }

    private Map<String, Object> toPromptItem(Newsletter newsletter) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", newsletter.getNewsletterCode());
        item.put("title", newsletter.getTitle());
        item.put("summary", newsletter.getSummary());
        item.put("category", newsletter.getCategory());
        item.put("tags", splitTags(newsletter.getTags()));
        item.put("cadence", newsletter.getCadence());
        return item;
    }

    private Map<String, Object> recommendationSchema() {
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("required", List.of("code", "reason"));
        itemSchema.put("properties", Map.of(
                "code", Map.of("type", "string"),
                "reason", Map.of("type", "string")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("recommendations"));
        schema.put("properties", Map.of(
                "recommendations", Map.of(
                        "type", "array",
                        "minItems", 0,
                        "maxItems", 3,
                        "items", itemSchema)));

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "newsletter_recommendations");
        format.put("strict", true);
        format.put("schema", schema);
        return format;
    }

    private List<Map<String, Object>> toRecommendations(
            String responseBody,
            List<Newsletter> candidates,
            Set<String> subscribedCodes) throws Exception {
        String outputText = extractOutputText(responseBody);
        if (!StringUtils.hasText(outputText)) {
            return List.of();
        }
        Map<String, Object> payload = objectMapper.readValue(outputText, new TypeReference<>() {});
        Object rawRecommendations = payload.get("recommendations");
        if (!(rawRecommendations instanceof List<?> rawList)) {
            return List.of();
        }

        Map<String, Newsletter> candidatesByCode = candidates.stream()
                .collect(Collectors.toMap(
                        item -> item.getNewsletterCode().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> usedCodes = new LinkedHashSet<>();
        for (Object raw : rawList) {
            if (!(raw instanceof Map<?, ?> rawItem)) {
                continue;
            }
            String code = normalizeCode(rawItem.get("code"));
            if (!StringUtils.hasText(code) || subscribedCodes.contains(code) || usedCodes.contains(code)) {
                continue;
            }
            Newsletter newsletter = candidatesByCode.get(code);
            if (newsletter == null) {
                continue;
            }
            Map<String, Object> item = toItem(newsletter, false);
            item.put("score", 1000 - result.size());
            item.put("reason", normalizeReason(rawItem.get("reason")));
            item.put("aiRecommended", true);
            result.add(item);
            usedCodes.add(code);
        }
        return result.stream()
                .sorted(Comparator.comparingInt(item -> -((Number) item.getOrDefault("score", 0)).intValue()))
                .limit(3)
                .toList();
    }

    private Map<String, Object> toItem(Newsletter newsletter, boolean subscribed) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", newsletter.getNewsletterCode());
        item.put("title", newsletter.getTitle());
        item.put("summary", newsletter.getSummary());
        item.put("category", newsletter.getCategory());
        item.put("tags", splitTags(newsletter.getTags()));
        item.put("cadence", newsletter.getCadence());
        item.put("subscribed", subscribed);
        item.put("url", newsletter.getExternalUrl());
        return item;
    }

    private String extractOutputText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }
        JsonNode output = root.get("output");
        if (output == null || !output.isArray()) {
            return "";
        }
        for (JsonNode outputItem : output) {
            JsonNode content = outputItem.get("content");
            if (content == null || !content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                JsonNode text = contentItem.get("text");
                if (text != null && text.isTextual()) {
                    return text.asText();
                }
            }
        }
        return "";
    }

    private String objectToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private List<String> splitTags(String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return List.of();
        }
        return java.util.Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .toList();
    }

    private String normalizeCode(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeReason(Object value) {
        String reason = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(reason) ? reason : "AI가 현재 관심 기사와 유사한 주제로 추천했습니다.";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
