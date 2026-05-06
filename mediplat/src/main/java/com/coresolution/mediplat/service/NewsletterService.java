package com.coresolution.mediplat.service;

import java.sql.DatabaseMetaData;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.mediplat.model.Newsletter;
import com.coresolution.mediplat.model.PlatformSessionUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class NewsletterService {

    private static final String USE_Y = "Y";
    private static final String USE_N = "N";

    private final JdbcTemplate jdbcTemplate;
    private final NewsletterAiRecommendationService aiRecommendationService;
    private final ObjectMapper objectMapper;
    private final long aiCacheHours;
    private boolean mysql;

    public NewsletterService(
            JdbcTemplate jdbcTemplate,
            NewsletterAiRecommendationService aiRecommendationService,
            ObjectMapper objectMapper,
            @Value("${platform.newsletter.ai.cache-hours:12}") long aiCacheHours) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiRecommendationService = aiRecommendationService;
        this.objectMapper = objectMapper;
        this.aiCacheHours = aiCacheHours;
    }

    @PostConstruct
    public void initialize() {
        mysql = detectMySql();
        createTables();
        bootstrapDefaults();
    }

    public Map<String, Object> buildPortalPayload(PlatformSessionUser user) {
        List<Newsletter> newsletters = listEnabledNewsletters(user);
        Set<String> subscribedCodes = listSubscribedCodes(user);
        Map<String, String> feedbackByCode = listFeedback(user);
        List<Map<String, Object>> items = newsletters.stream()
                .map(newsletter -> withFeedback(toItem(newsletter, subscribedCodes.contains(newsletter.getNewsletterCode())), feedbackByCode))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("subscriptions", new ArrayList<>(subscribedCodes));
        result.put("recommendations", recommend(user, newsletters, subscribedCodes, feedbackByCode));
        return result;
    }

    public List<Map<String, Object>> listAdminNewsletters() {
        return jdbcTemplate.query("""
                SELECT id, newsletter_code, title, summary, category, tags, cadence, external_url, use_yn, display_order
                FROM mp_newsletter
                WHERE COALESCE(owner_inst_code, '') = ''
                ORDER BY display_order ASC, id ASC
                """, (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("code", rs.getString("newsletter_code"));
                    item.put("title", rs.getString("title"));
                    item.put("summary", rs.getString("summary"));
                    item.put("category", rs.getString("category"));
                    item.put("tags", rs.getString("tags"));
                    item.put("cadence", rs.getString("cadence"));
                    item.put("url", rs.getString("external_url"));
                    item.put("useYn", rs.getString("use_yn"));
                    item.put("displayOrder", rs.getInt("display_order"));
                    return item;
                });
    }

    public void saveAdminNewsletter(
            String code,
            String title,
            String summary,
            String category,
            String tags,
            String cadence,
            String url,
            String useYn,
            Integer displayOrder) {
        String normalizedCode = normalizeCode(code);
        String normalizedTitle = trimRequired(title, "뉴스/기사 제목을 입력해주세요.", 120);
        String normalizedSummary = trimRequired(summary, "요약을 입력해주세요.", 500);
        String normalizedCategory = trimRequired(category, "카테고리를 입력해주세요.", 60);
        String normalizedTags = trimRequired(tags, "태그를 입력해주세요.", 500);
        String normalizedCadence = trimRequired(cadence, "출처를 입력해주세요.", 40);
        String normalizedUrl = normalizeUrl(url);
        String normalizedUseYn = "N".equalsIgnoreCase(useYn) ? USE_N : USE_Y;
        int order = displayOrder == null ? 0 : displayOrder;
        if (!StringUtils.hasText(normalizedCode)) {
            throw new IllegalArgumentException("뉴스/기사 코드를 입력해주세요.");
        }
        if (!StringUtils.hasText(normalizedUrl)) {
            throw new IllegalArgumentException("http 또는 https 주소를 입력해주세요.");
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mp_newsletter WHERE newsletter_code = ? AND COALESCE(owner_inst_code, '') = ''",
                Integer.class,
                normalizedCode);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE mp_newsletter
                    SET title = ?, summary = ?, category = ?, tags = ?, cadence = ?, external_url = ?,
                        use_yn = ?, display_order = ?, owner_inst_code = '', owner_username = ''
                    WHERE newsletter_code = ?
                      AND COALESCE(owner_inst_code, '') = ''
                    """, normalizedTitle, normalizedSummary, normalizedCategory, normalizedTags, normalizedCadence,
                    normalizedUrl, normalizedUseYn, order, normalizedCode);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO mp_newsletter
                        (newsletter_code, title, summary, category, tags, cadence, external_url, use_yn, display_order, owner_inst_code, owner_username)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '', '')
                    """, normalizedCode, normalizedTitle, normalizedSummary, normalizedCategory, normalizedTags,
                    normalizedCadence, normalizedUrl, normalizedUseYn, order);
        }
        clearRecommendationCache();
    }

    public void updateAdminNewsletterStatus(String code, String useYn) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(normalizedCode)) {
            throw new IllegalArgumentException("뉴스/기사 코드를 확인할 수 없습니다.");
        }
        String normalizedUseYn = "N".equalsIgnoreCase(useYn) ? USE_N : USE_Y;
        int updated = jdbcTemplate.update("""
                UPDATE mp_newsletter
                SET use_yn = ?
                WHERE newsletter_code = ?
                  AND COALESCE(owner_inst_code, '') = ''
                """, normalizedUseYn, normalizedCode);
        if (updated == 0) {
            throw new IllegalArgumentException("등록된 뉴스/기사가 아닙니다.");
        }
        clearRecommendationCache();
    }

    public void setSubscribed(PlatformSessionUser user, String newsletterCode, boolean subscribed) {
        if (user == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        String normalizedCode = normalizeCode(newsletterCode);
        if (!StringUtils.hasText(normalizedCode) || findEnabledNewsletter(user, normalizedCode) == null) {
            throw new IllegalArgumentException("등록된 뉴스/기사가 아닙니다.");
        }
        String instCode = normalizeInst(user.getInstCode());
        String username = normalizeUsername(user.getUsername());
        if (!StringUtils.hasText(instCode) || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("사용자 정보를 확인할 수 없습니다.");
        }

        int updated = jdbcTemplate.update("""
                UPDATE mp_newsletter_subscription
                SET use_yn = ?, updated_at = CURRENT_TIMESTAMP
                WHERE inst_code = ? AND username = ? AND newsletter_code = ?
                """, subscribed ? USE_Y : USE_N, instCode, username, normalizedCode);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO mp_newsletter_subscription
                        (inst_code, username, newsletter_code, use_yn, created_at, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, instCode, username, normalizedCode, subscribed ? USE_Y : USE_N);
        }
    }

    public void setFeedback(PlatformSessionUser user, String newsletterCode, String feedback) {
        if (user == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        String normalizedCode = normalizeCode(newsletterCode);
        if (!StringUtils.hasText(normalizedCode) || findEnabledNewsletter(user, normalizedCode) == null) {
            throw new IllegalArgumentException("등록된 뉴스/기사가 아닙니다.");
        }
        String normalizedFeedback = normalizeFeedback(feedback);
        if (!StringUtils.hasText(normalizedFeedback)) {
            throw new IllegalArgumentException("피드백 값을 확인할 수 없습니다.");
        }
        String instCode = normalizeInst(user.getInstCode());
        String username = normalizeUsername(user.getUsername());
        if (!StringUtils.hasText(instCode) || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("사용자 정보를 확인할 수 없습니다.");
        }

        int updated = jdbcTemplate.update("""
                UPDATE mp_newsletter_feedback
                SET feedback_type = ?, updated_at = CURRENT_TIMESTAMP
                WHERE inst_code = ? AND username = ? AND newsletter_code = ?
                """, normalizedFeedback, instCode, username, normalizedCode);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO mp_newsletter_feedback
                        (inst_code, username, newsletter_code, feedback_type, created_at, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, instCode, username, normalizedCode, normalizedFeedback);
        }
    }

    public void addCustomSubscription(PlatformSessionUser user, String title, String url) {
        if (user == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : "";
        String normalizedUrl = normalizeUrl(url);
        if (!StringUtils.hasText(normalizedTitle)) {
            throw new IllegalArgumentException("기사 제목을 입력해주세요.");
        }
        if (!StringUtils.hasText(normalizedUrl)) {
            throw new IllegalArgumentException("http 또는 https 주소를 입력해주세요.");
        }
        if (normalizedTitle.length() > 120) {
            normalizedTitle = normalizedTitle.substring(0, 120);
        }

        String instCode = normalizeInst(user.getInstCode());
        String username = normalizeUsername(user.getUsername());
        if (!StringUtils.hasText(instCode) || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("사용자 정보를 확인할 수 없습니다.");
        }

        String code = customCode(instCode, username, normalizedUrl);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mp_newsletter WHERE newsletter_code = ?",
                Integer.class,
                code);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE mp_newsletter
                    SET title = ?, external_url = ?, use_yn = 'Y', owner_inst_code = ?, owner_username = ?
                    WHERE newsletter_code = ?
                    """, normalizedTitle, normalizedUrl, instCode, username, code);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO mp_newsletter
                        (newsletter_code, title, summary, category, tags, cadence, external_url, use_yn, display_order, owner_inst_code, owner_username)
                    VALUES (?, ?, ?, '직접추가', 'custom,korean,healthcare,article', '직접 추가', ?, 'Y', 900, ?, ?)
                    """, code, normalizedTitle, "직접 추가한 뉴스/기사입니다.", normalizedUrl, instCode, username);
        }
        setSubscribed(user, code, true);
    }

    private List<Newsletter> listEnabledNewsletters(PlatformSessionUser user) {
        return jdbcTemplate.query("""
                SELECT id, newsletter_code, title, summary, category, tags, cadence, external_url, use_yn, display_order
                FROM mp_newsletter
                WHERE use_yn = 'Y'
                  AND (
                    COALESCE(owner_inst_code, '') = ''
                    OR (owner_inst_code = ? AND owner_username = ?)
                  )
                ORDER BY display_order ASC, id ASC
                """, (rs, rowNum) -> new Newsletter(
                        rs.getLong("id"),
                        rs.getString("newsletter_code"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        rs.getString("category"),
                        rs.getString("tags"),
                        rs.getString("cadence"),
                        rs.getString("external_url"),
                        rs.getString("use_yn"),
                        rs.getInt("display_order")),
                normalizeInst(user == null ? "" : user.getInstCode()),
                normalizeUsername(user == null ? "" : user.getUsername()));
    }

    private Newsletter findEnabledNewsletter(PlatformSessionUser user, String newsletterCode) {
        List<Newsletter> rows = jdbcTemplate.query("""
                SELECT id, newsletter_code, title, summary, category, tags, cadence, external_url, use_yn, display_order
                FROM mp_newsletter
                WHERE newsletter_code = ?
                  AND use_yn = 'Y'
                  AND (
                    COALESCE(owner_inst_code, '') = ''
                    OR (owner_inst_code = ? AND owner_username = ?)
                  )
                LIMIT 1
                """, (rs, rowNum) -> new Newsletter(
                        rs.getLong("id"),
                        rs.getString("newsletter_code"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        rs.getString("category"),
                        rs.getString("tags"),
                        rs.getString("cadence"),
                        rs.getString("external_url"),
                        rs.getString("use_yn"),
                        rs.getInt("display_order")),
                newsletterCode,
                normalizeInst(user == null ? "" : user.getInstCode()),
                normalizeUsername(user == null ? "" : user.getUsername()));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Set<String> listSubscribedCodes(PlatformSessionUser user) {
        if (user == null) {
            return Set.of();
        }
        String instCode = normalizeInst(user.getInstCode());
        String username = normalizeUsername(user.getUsername());
        if (!StringUtils.hasText(instCode) || !StringUtils.hasText(username)) {
            return Set.of();
        }
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT newsletter_code
                FROM mp_newsletter_subscription
                WHERE inst_code = ?
                  AND username = ?
                  AND use_yn = 'Y'
                ORDER BY newsletter_code ASC
                """, String.class, instCode, username);
        return new LinkedHashSet<>(rows.stream().map(this::normalizeCode).filter(StringUtils::hasText).toList());
    }

    private Map<String, String> listFeedback(PlatformSessionUser user) {
        if (user == null) {
            return Map.of();
        }
        String instCode = normalizeInst(user.getInstCode());
        String username = normalizeUsername(user.getUsername());
        if (!StringUtils.hasText(instCode) || !StringUtils.hasText(username)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT newsletter_code, feedback_type
                FROM mp_newsletter_feedback
                WHERE inst_code = ?
                  AND username = ?
                """, rs -> {
                    String code = normalizeCode(rs.getString("newsletter_code"));
                    String feedback = normalizeFeedback(rs.getString("feedback_type"));
                    if (StringUtils.hasText(code) && StringUtils.hasText(feedback)) {
                        result.put(code, feedback);
                    }
                }, instCode, username);
        return result;
    }

    private List<Map<String, Object>> recommend(
            PlatformSessionUser user,
            List<Newsletter> newsletters,
            Set<String> subscribedCodes,
            Map<String, String> feedbackByCode) {
        if (!aiRecommendationService.isAvailable()) {
            return recommendByTags(newsletters, subscribedCodes, feedbackByCode);
        }
        String cacheKey = recommendationCacheKey(user, newsletters, subscribedCodes, feedbackByCode);
        List<Map<String, Object>> cachedRecommendations = findCachedRecommendations(cacheKey);
        if (!cachedRecommendations.isEmpty()) {
            return cachedRecommendations;
        }
        List<Map<String, Object>> aiRecommendations = aiRecommendationService.recommend(user, newsletters, subscribedCodes, feedbackByCode);
        if (!aiRecommendations.isEmpty()) {
            List<Map<String, Object>> withFeedback = aiRecommendations.stream()
                    .map(item -> withFeedback(item, feedbackByCode))
                    .toList();
            saveCachedRecommendations(cacheKey, withFeedback);
            return withFeedback;
        }
        return recommendByTags(newsletters, subscribedCodes, feedbackByCode);
    }

    private List<Map<String, Object>> recommendByTags(
            List<Newsletter> newsletters,
            Set<String> subscribedCodes,
            Map<String, String> feedbackByCode) {
        Set<String> subscribedTags = new LinkedHashSet<>();
        Set<String> likedTags = new LinkedHashSet<>();
        for (Newsletter newsletter : newsletters) {
            if (subscribedCodes.contains(newsletter.getNewsletterCode())) {
                subscribedTags.addAll(splitTags(newsletter.getTags()));
            }
            if ("LIKE".equalsIgnoreCase(feedbackByCode.get(newsletter.getNewsletterCode()))) {
                likedTags.addAll(splitTags(newsletter.getTags()));
            }
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Newsletter newsletter : newsletters) {
            if (subscribedCodes.contains(newsletter.getNewsletterCode())) {
                continue;
            }
            String feedback = feedbackByCode.get(newsletter.getNewsletterCode());
            if ("DISLIKE".equalsIgnoreCase(feedback)) {
                continue;
            }
            Set<String> tags = splitTags(newsletter.getTags());
            int score = 0;
            for (String tag : tags) {
                if (subscribedTags.contains(tag)) {
                    score++;
                }
                if (likedTags.contains(tag)) {
                    score += 2;
                }
            }
            if ("LIKE".equalsIgnoreCase(feedback)) {
                score += 5;
            }
            if (subscribedTags.isEmpty() && likedTags.isEmpty()) {
                score = Math.max(1, 1000 - (newsletter.getDisplayOrder() == null ? 999 : newsletter.getDisplayOrder()));
            }
            if (score > 0) {
                Map<String, Object> item = withFeedback(toItem(newsletter, false), feedbackByCode);
                item.put("score", score);
                item.put("reason", subscribedTags.isEmpty()
                        ? "처음 시작하기 좋은 추천"
                        : "내 관심 기사/피드백과 주제가 겹칩니다");
                candidates.add(item);
            }
        }
        candidates.sort((left, right) -> {
            int scoreCompare = Integer.compare(
                    ((Number) right.getOrDefault("score", 0)).intValue(),
                    ((Number) left.getOrDefault("score", 0)).intValue());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(
                    String.valueOf(left.getOrDefault("title", "")),
                    String.valueOf(right.getOrDefault("title", "")));
        });
        return candidates.stream().limit(3).toList();
    }

    Map<String, Object> toItem(Newsletter newsletter, boolean subscribed) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", newsletter.getNewsletterCode());
        item.put("title", newsletter.getTitle());
        item.put("summary", newsletter.getSummary());
        item.put("category", newsletter.getCategory());
        item.put("tags", new ArrayList<>(splitTags(newsletter.getTags())));
        item.put("cadence", newsletter.getCadence());
        item.put("subscribed", subscribed);
        item.put("url", newsletter.getExternalUrl());
        return item;
    }

    private Map<String, Object> withFeedback(Map<String, Object> item, Map<String, String> feedbackByCode) {
        String code = String.valueOf(item.getOrDefault("code", ""));
        String feedback = feedbackByCode.getOrDefault(code, "");
        item.put("feedback", feedback);
        return item;
    }

    Set<String> splitTags(String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .forEach(result::add);
        return result;
    }

    private void createTables() {
        if (mysql) {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS mp_newsletter (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        newsletter_code VARCHAR(60) NOT NULL UNIQUE,
                        title VARCHAR(120) NOT NULL,
                        summary VARCHAR(500) NOT NULL,
                        category VARCHAR(60) NOT NULL,
                        tags VARCHAR(500) NOT NULL,
                        cadence VARCHAR(40) NOT NULL,
                        external_url VARCHAR(500) NOT NULL DEFAULT '',
                        use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                        display_order INT NOT NULL DEFAULT 0,
                        owner_inst_code VARCHAR(50) NOT NULL DEFAULT '',
                        owner_username VARCHAR(100) NOT NULL DEFAULT ''
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS mp_newsletter_subscription (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        inst_code VARCHAR(50) NOT NULL,
                        username VARCHAR(100) NOT NULL,
                        newsletter_code VARCHAR(60) NOT NULL,
                        use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_mp_newsletter_subscription (inst_code, username, newsletter_code)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS mp_newsletter_recommendation_cache (
                        cache_key VARCHAR(128) NOT NULL PRIMARY KEY,
                        recommendations_json TEXT NOT NULL,
                        expires_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS mp_newsletter_feedback (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        inst_code VARCHAR(50) NOT NULL,
                        username VARCHAR(100) NOT NULL,
                        newsletter_code VARCHAR(60) NOT NULL,
                        feedback_type VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_mp_newsletter_feedback (inst_code, username, newsletter_code)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureNewsletterColumns();
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_newsletter (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    newsletter_code VARCHAR(60) NOT NULL UNIQUE,
                    title VARCHAR(120) NOT NULL,
                    summary VARCHAR(500) NOT NULL,
                    category VARCHAR(60) NOT NULL,
                    tags VARCHAR(500) NOT NULL,
                    cadence VARCHAR(40) NOT NULL,
                    external_url VARCHAR(500) NOT NULL DEFAULT '',
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    display_order INT NOT NULL DEFAULT 0,
                    owner_inst_code VARCHAR(50) NOT NULL DEFAULT '',
                    owner_username VARCHAR(100) NOT NULL DEFAULT ''
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_newsletter_subscription (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    newsletter_code VARCHAR(60) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (inst_code, username, newsletter_code)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_newsletter_recommendation_cache (
                    cache_key VARCHAR(128) NOT NULL PRIMARY KEY,
                    recommendations_json CLOB NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_newsletter_feedback (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    newsletter_code VARCHAR(60) NOT NULL,
                    feedback_type VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (inst_code, username, newsletter_code)
                )
                """);
        ensureNewsletterColumns();
    }

    private void bootstrapDefaults() {
        seed("CARE_OPS_WEEKLY", "간호조무사는 왜 안돼? 뜨거운 댓글 논쟁", "간호사국시 응시자격과 의료법 개정 논란을 다룬 보건의료 정책 기사입니다.", "의료정책", "korea,policy,doctor,hospital,article", "청년의사", "https://www.docdocdoc.co.kr/news/articleView.html?idxno=39203", 10);
        seed("COUNSEL_GROWTH", "병원 경영 악화돼 해임…급여 못받은 의료재단 임원", "의료법인 임원 보수 지급 판단을 통해 병원 운영과 책임경영 이슈를 확인합니다.", "병원경영", "korea,operations,hospital,management,article", "데일리메디", "https://www.dailymedi.com/dmedi/news/news_view.php?ca_id=2201&wr_id=933786", 20);
        seed("WARD_INSIGHT", "의료계 디지털 전환 가속화…의료 정책 플랫폼 출시", "의료계 디지털 전환과 실명 기반 의료 정책 플랫폼 출시 흐름을 다룬 기사입니다.", "의료계", "korea,medical,policy,operations,digital-health,article", "메디칼타임즈", "https://www.medicaltimes.com/Main/News/NewsView.html?ID=1167550", 30);
        seed("MEDIPLAT_RELEASE", "AI로 진단과 치료, 수술 계획…CES 디지털헬스 혁신상", "AI 진단, 재활, 치료 기술 등 디지털헬스 제품과 서비스 동향을 확인합니다.", "디지털헬스", "korea,digital-health,platform,healthcare,ai,article", "메디게이트뉴스", "https://www.medigatenews.com/news/2178363047", 40);
        seed("COMPLIANCE_CHECK", "미국바이오협회, 의약품 관세가 바이오 리더십 약화", "의약품 관세, 규제, 글로벌 공급망 이슈가 제약·바이오 산업에 미치는 영향을 다룹니다.", "산업동향", "korea,bio,pharma,regulation,article", "히트뉴스", "https://www.hitnews.co.kr/news/articleView.html?idxno=64610", 50);
        seed("MESSAGE_PLAYBOOK", "보령, 미국 액시엄에 추가 투자…우주 헬스케어", "바이오·헬스케어 기업의 신사업 투자와 글로벌 협력 흐름을 살펴봅니다.", "투자동향", "korea,bio,investment,healthcare,article", "바이오스펙테이터", "https://www.biospectator.com/news/view/17931", 60);
    }

    private void seed(String code, String title, String summary, String category, String tags, String cadence, String externalUrl, int order) {
        String normalizedCode = normalizeCode(code);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mp_newsletter WHERE newsletter_code = ?",
                Integer.class,
                normalizedCode);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE mp_newsletter
                    SET title = ?, summary = ?, category = ?, tags = ?, cadence = ?, external_url = ?, display_order = ?,
                        owner_inst_code = '', owner_username = ''
                    WHERE newsletter_code = ?
                    """, title, summary, category, tags, cadence, externalUrl, order, normalizedCode);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO mp_newsletter
                    (newsletter_code, title, summary, category, tags, cadence, external_url, use_yn, display_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'Y', ?)
                """, normalizedCode, title, summary, category, tags, cadence, externalUrl, order);
    }

    private void ensureNewsletterColumns() {
        ensureNewsletterColumn("external_url", "VARCHAR(500) NOT NULL DEFAULT ''");
        ensureNewsletterColumn("owner_inst_code", "VARCHAR(50) NOT NULL DEFAULT ''");
        ensureNewsletterColumn("owner_username", "VARCHAR(100) NOT NULL DEFAULT ''");
    }

    private void ensureNewsletterColumn(String columnName, String definition) {
        boolean exists = Boolean.TRUE.equals(jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (java.sql.ResultSet columns = metadata.getColumns(catalog, null, "mp_newsletter", columnName)) {
                if (columns.next()) {
                    return true;
                }
            }
            try (java.sql.ResultSet columns = metadata.getColumns(catalog, null, "MP_NEWSLETTER", columnName.toUpperCase(Locale.ROOT))) {
                return columns.next();
            }
        }));
        if (!exists) {
            jdbcTemplate.execute("ALTER TABLE mp_newsletter ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean detectMySql() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
                DatabaseMetaData metadata = connection.getMetaData();
                String name = metadata == null ? "" : metadata.getDatabaseProductName();
                return name != null && name.toLowerCase(Locale.ROOT).contains("mysql");
            }));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeInst(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeUsername(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String candidate = value.trim();
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || !StringUtils.hasText(uri.getHost())) {
                return "";
            }
            return candidate;
        } catch (URISyntaxException e) {
            return "";
        }
    }

    private String customCode(String instCode, String username, String url) {
        String source = instCode + ":" + username + ":" + url.toLowerCase(Locale.ROOT);
        return "CUSTOM_" + Integer.toUnsignedString(source.hashCode()).toUpperCase(Locale.ROOT);
    }

    private String trimRequired(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String recommendationCacheKey(
            PlatformSessionUser user,
            List<Newsletter> newsletters,
            Set<String> subscribedCodes,
            Map<String, String> feedbackByCode) {
        String instCode = normalizeInst(user == null ? "" : user.getInstCode());
        String roleCode = normalizeCode(user == null ? "" : user.getRoleCode());
        String subscriptionPart = subscribedCodes.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.joining(","));
        String catalogPart = newsletters.stream()
                .sorted(Comparator.comparing(Newsletter::getNewsletterCode, String.CASE_INSENSITIVE_ORDER))
                .map(newsletter -> String.join(":",
                        safe(newsletter.getNewsletterCode()),
                        safe(newsletter.getTitle()),
                        safe(newsletter.getCategory()),
                        safe(newsletter.getTags()),
                        safe(newsletter.getExternalUrl())))
                .collect(java.util.stream.Collectors.joining("|"));
        String feedbackPart = feedbackByCode.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
        return "AI_" + sha256(instCode + "\n" + roleCode + "\n" + subscriptionPart + "\n" + feedbackPart + "\n" + catalogPart);
    }

    private List<Map<String, Object>> findCachedRecommendations(String cacheKey) {
        if (!StringUtils.hasText(cacheKey)) {
            return List.of();
        }
        try {
            List<String> rows = jdbcTemplate.queryForList("""
                    SELECT recommendations_json
                    FROM mp_newsletter_recommendation_cache
                    WHERE cache_key = ?
                      AND expires_at > CURRENT_TIMESTAMP
                    LIMIT 1
                    """, String.class, cacheKey);
            if (rows.isEmpty() || !StringUtils.hasText(rows.get(0))) {
                return List.of();
            }
            return objectMapper.readValue(rows.get(0), new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void saveCachedRecommendations(String cacheKey, List<Map<String, Object>> recommendations) {
        if (!StringUtils.hasText(cacheKey) || recommendations == null || recommendations.isEmpty() || aiCacheHours <= 0) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(recommendations);
            Timestamp expiresAt = Timestamp.from(Instant.now().plus(Duration.ofHours(aiCacheHours)));
            int updated = jdbcTemplate.update("""
                    UPDATE mp_newsletter_recommendation_cache
                    SET recommendations_json = ?, expires_at = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE cache_key = ?
                    """, json, expiresAt, cacheKey);
            if (updated == 0) {
                jdbcTemplate.update("""
                        INSERT INTO mp_newsletter_recommendation_cache
                            (cache_key, recommendations_json, expires_at, updated_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        """, cacheKey, json, expiresAt);
            }
        } catch (Exception ignored) {
            // AI recommendation cache is an optimization. Failing to cache must not block the portal.
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toUnsignedString(value.hashCode()).toUpperCase(Locale.ROOT);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeFeedback(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "LIKE".equals(normalized) || "DISLIKE".equals(normalized) ? normalized : "";
    }

    private void clearRecommendationCache() {
        try {
            jdbcTemplate.update("DELETE FROM mp_newsletter_recommendation_cache");
        } catch (Exception ignored) {
            // Cache invalidation is best effort. Missing cache table must not block admin saves.
        }
    }
}
