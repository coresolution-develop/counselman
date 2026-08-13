package com.coresolution.csm.serivce;

import java.net.URI;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.vo.CompanyLink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyLinkService {

    private final JdbcTemplate jdbcTemplate;

    public void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.company_link (
                    id bigint auto_increment primary key,
                    title varchar(100) not null,
                    url varchar(500) not null,
                    description varchar(500) default null,
                    category varchar(80) default null,
                    sort_order int not null default 0,
                    use_yn char(1) not null default 'Y',
                    created_at timestamp default current_timestamp,
                    created_by varchar(100) default null,
                    updated_at timestamp default current_timestamp on update current_timestamp,
                    updated_by varchar(100) default null,
                    key idx_company_link_use_sort (use_yn, sort_order, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.company_link_category (
                    category_name varchar(80) not null primary key,
                    sort_order int not null default 0,
                    updated_at timestamp default current_timestamp on update current_timestamp
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        // 운영자가 지정하는 값들. 비어 있으면 화면단에서 이름 기반 기본값으로 떨어진다.
        ensureColumn("company_link", "env", "varchar(10) DEFAULT NULL");
        ensureColumn("company_link_category", "color", "varchar(20) DEFAULT NULL");
        ensureColumn("company_link_category", "color_dark", "varchar(20) DEFAULT NULL");
        ensureColumn("company_link_category", "short_label", "varchar(10) DEFAULT NULL");
    }

    /** 컬럼이 없을 때만 추가한다. 실패해도 기동을 막지 않는다(기존 스키마 부트스트랩과 같은 방식). */
    private void ensureColumn(String table, String column, String definition) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """, Integer.class, table, column);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE csm." + table + " ADD COLUMN " + column + " " + definition);
                log.info("[company-link] added column {}.{}", table, column);
            }
        } catch (Exception e) {
            log.warn("[company-link] column migration skipped {}.{}: {}", table, column, e.toString());
        }
    }

    public List<java.util.Map<String, Object>> listCategories() {
        ensureTable();
        return jdbcTemplate.queryForList("""
                SELECT cl.category AS category_name,
                       COALESCE(cat.sort_order, 9999) AS sort_order,
                       cat.color AS color,
                       cat.color_dark AS color_dark,
                       cat.short_label AS short_label
                  FROM csm.company_link cl
                  LEFT JOIN csm.company_link_category cat ON cat.category_name = cl.category
                 WHERE cl.use_yn = 'Y'
                 GROUP BY cl.category, cat.sort_order, cat.color, cat.color_dark, cat.short_label
                 ORDER BY COALESCE(cat.sort_order, 9999) ASC, cl.category ASC
                """);
    }

    /** 분류 색상·축약 저장. 순서는 건드리지 않는다(순서 저장과 독립적으로 눌린다). */
    @Transactional
    public void saveCategoryStyle(String category, String color, String colorDark, String shortLabel) {
        ensureTable();
        String safeCategory = trimTo(category, 80);
        if (safeCategory.isBlank()) return;
        String safeColor = normalizeHex(color);
        String safeColorDark = normalizeHex(colorDark);
        String safeShort = trimTo(shortLabel, 10);
        jdbcTemplate.update("""
                INSERT INTO csm.company_link_category (category_name, sort_order, color, color_dark, short_label)
                VALUES (?, 9999, ?, ?, ?)
                ON DUPLICATE KEY UPDATE color = ?, color_dark = ?, short_label = ?
                """,
                safeCategory, safeColor, safeColorDark, safeShort.isBlank() ? null : safeShort,
                safeColor, safeColorDark, safeShort.isBlank() ? null : safeShort);
    }

    /** 아는 환경값만 저장한다. 비었거나 모르는 값이면 null → 화면단에서 이름·host로 자동 판정. */
    private String normalizeEnv(String value) {
        if (value == null) return null;
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (text) {
            case "prod", "dev", "demo" -> text;
            default -> null;
        };
    }

    /** #rrggbb 형태만 통과시킨다 — 색상값이 그대로 style 속성에 들어가므로 임의 문자열을 넣지 않는다. */
    private String normalizeHex(String value) {
        if (value == null) return null;
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        return text.matches("#[0-9a-f]{6}") ? text : null;
    }

    @Transactional
    public void saveCategoryOrder(String category, int sortOrder) {
        ensureTable();
        String safeCategory = trimTo(category, 80);
        if (safeCategory.isBlank()) return;
        int safeOrder = Math.max(0, Math.min(sortOrder, 9999));
        jdbcTemplate.update("""
                INSERT INTO csm.company_link_category (category_name, sort_order)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE sort_order = ?
                """, safeCategory, safeOrder, safeOrder);
    }

    public List<CompanyLink> listActiveLinks() {
        ensureTable();
        return jdbcTemplate.query("""
                SELECT cl.id, cl.title, cl.url, cl.description, cl.category, cl.env, cl.sort_order, cl.use_yn,
                       DATE_FORMAT(cl.created_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS created_at,
                       DATE_FORMAT(cl.updated_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS updated_at
                  FROM csm.company_link cl
                  LEFT JOIN csm.company_link_category cat ON cat.category_name = cl.category
                 WHERE cl.use_yn = 'Y'
                 ORDER BY COALESCE(cat.sort_order, 9999) ASC, cl.sort_order ASC, cl.title ASC, cl.id ASC
                """, (rs, rowNum) -> {
            CompanyLink link = new CompanyLink();
            link.setId(rs.getLong("id"));
            link.setTitle(rs.getString("title"));
            link.setUrl(rs.getString("url"));
            link.setDescription(rs.getString("description"));
            link.setCategory(rs.getString("category"));
            link.setEnv(rs.getString("env"));
            link.setSortOrder(rs.getInt("sort_order"));
            link.setUseYn(rs.getString("use_yn"));
            link.setCreatedAt(rs.getString("created_at"));
            link.setUpdatedAt(rs.getString("updated_at"));
            return link;
        });
    }

    /** 활성(use_yn='Y') 공용 링크 단건 조회 — /hub/go/link/{id} 해석용. 없으면 null. */
    public CompanyLink findActiveById(long id) {
        ensureTable();
        if (id <= 0) {
            return null;
        }
        List<CompanyLink> rows = jdbcTemplate.query("""
                SELECT cl.id, cl.title, cl.url, cl.description, cl.category, cl.env, cl.sort_order, cl.use_yn
                  FROM csm.company_link cl
                 WHERE cl.id = ? AND cl.use_yn = 'Y'
                 LIMIT 1
                """, (rs, rowNum) -> {
            CompanyLink link = new CompanyLink();
            link.setId(rs.getLong("id"));
            link.setTitle(rs.getString("title"));
            link.setUrl(rs.getString("url"));
            link.setDescription(rs.getString("description"));
            link.setCategory(rs.getString("category"));
            link.setEnv(rs.getString("env"));
            link.setSortOrder(rs.getInt("sort_order"));
            link.setUseYn(rs.getString("use_yn"));
            return link;
        }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public long createLink(String title, String url, String description, String category, String env, Integer sortOrder, String actor) {
        ensureTable();
        String safeTitle = requireText(title, "링크 이름", 100);
        String safeUrl = normalizeUrl(url);
        String safeDescription = trimTo(description, 500);
        String safeCategory = trimTo(category, 80);
        int safeSortOrder = sortOrder == null ? 0 : Math.max(0, Math.min(sortOrder, 9999));
        jdbcTemplate.update("""
                INSERT INTO csm.company_link
                (title, url, description, category, env, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                safeTitle,
                safeUrl,
                safeDescription.isBlank() ? null : safeDescription,
                safeCategory.isBlank() ? null : safeCategory,
                normalizeEnv(env),
                safeSortOrder,
                trimTo(actor, 100),
                trimTo(actor, 100));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    @Transactional
    public boolean deleteLink(long id, String actor) {
        ensureTable();
        if (id <= 0) {
            throw new IllegalArgumentException("삭제할 링크가 올바르지 않습니다.");
        }
        return jdbcTemplate.update(
                "UPDATE csm.company_link SET use_yn = 'N', updated_by = ? WHERE id = ? AND use_yn = 'Y'",
                trimTo(actor, 100),
                id) > 0;
    }

    @Transactional
    public boolean updateLink(long id, String title, String url, String description, String category, String env, Integer sortOrder, String actor) {
        ensureTable();
        if (id <= 0) {
            throw new IllegalArgumentException("수정할 링크가 올바르지 않습니다.");
        }
        String safeTitle = requireText(title, "링크 이름", 100);
        String safeUrl = normalizeUrl(url);
        String safeDescription = trimTo(description, 500);
        String safeCategory = trimTo(category, 80);
        int safeSortOrder = sortOrder == null ? 0 : Math.max(0, Math.min(sortOrder, 9999));
        return jdbcTemplate.update("""
                UPDATE csm.company_link
                   SET title = ?,
                       url = ?,
                       description = ?,
                       category = ?,
                       env = ?,
                       sort_order = ?,
                       updated_by = ?
                 WHERE id = ? AND use_yn = 'Y'
                """,
                safeTitle,
                safeUrl,
                safeDescription.isBlank() ? null : safeDescription,
                safeCategory.isBlank() ? null : safeCategory,
                normalizeEnv(env),
                safeSortOrder,
                trimTo(actor, 100),
                id) > 0;
    }

    private String normalizeUrl(String url) {
        String value = requireText(url, "URL", 500);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL 형식이 올바르지 않습니다.");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("URL은 http 또는 https로 시작해야 합니다.");
        }
        if (!hasUrlAuthority(uri)) {
            throw new IllegalArgumentException("URL에 도메인이 필요합니다.");
        }
        return value;
    }

    private boolean hasUrlAuthority(URI uri) {
        if (uri.getHost() != null && !uri.getHost().isBlank()) {
            return true;
        }
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank()) {
            return false;
        }
        String hostAndPort = authority;
        int userInfoEnd = hostAndPort.lastIndexOf('@');
        if (userInfoEnd >= 0) {
            hostAndPort = hostAndPort.substring(userInfoEnd + 1);
        }
        if (hostAndPort.startsWith("[")) {
            return hostAndPort.contains("]");
        }
        int portStart = hostAndPort.lastIndexOf(':');
        String host = portStart >= 0 ? hostAndPort.substring(0, portStart) : hostAndPort;
        return !host.isBlank();
    }

    private String requireText(String value, String label, int maxLen) {
        String text = trimTo(value, maxLen);
        if (text.isBlank()) {
            throw new IllegalArgumentException(label + "을 입력해주세요.");
        }
        return text;
    }

    private String trimTo(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
