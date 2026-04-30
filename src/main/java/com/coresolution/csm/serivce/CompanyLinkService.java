package com.coresolution.csm.serivce;

import java.net.URI;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.vo.CompanyLink;

import lombok.RequiredArgsConstructor;

@Service
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
    }

    public List<CompanyLink> listActiveLinks() {
        ensureTable();
        return jdbcTemplate.query("""
                SELECT id, title, url, description, category, sort_order, use_yn,
                       DATE_FORMAT(created_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS created_at,
                       DATE_FORMAT(updated_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS updated_at
                  FROM csm.company_link
                 WHERE use_yn = 'Y'
                 ORDER BY sort_order ASC, title ASC, id ASC
                """, (rs, rowNum) -> {
            CompanyLink link = new CompanyLink();
            link.setId(rs.getLong("id"));
            link.setTitle(rs.getString("title"));
            link.setUrl(rs.getString("url"));
            link.setDescription(rs.getString("description"));
            link.setCategory(rs.getString("category"));
            link.setSortOrder(rs.getInt("sort_order"));
            link.setUseYn(rs.getString("use_yn"));
            link.setCreatedAt(rs.getString("created_at"));
            link.setUpdatedAt(rs.getString("updated_at"));
            return link;
        });
    }

    @Transactional
    public long createLink(String title, String url, String description, String category, Integer sortOrder, String actor) {
        ensureTable();
        String safeTitle = requireText(title, "링크 이름", 100);
        String safeUrl = normalizeUrl(url);
        String safeDescription = trimTo(description, 500);
        String safeCategory = trimTo(category, 80);
        int safeSortOrder = sortOrder == null ? 0 : Math.max(0, Math.min(sortOrder, 9999));
        jdbcTemplate.update("""
                INSERT INTO csm.company_link
                (title, url, description, category, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                safeTitle,
                safeUrl,
                safeDescription.isBlank() ? null : safeDescription,
                safeCategory.isBlank() ? null : safeCategory,
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
    public boolean updateLink(long id, String title, String url, String description, String category, Integer sortOrder, String actor) {
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
                       sort_order = ?,
                       updated_by = ?
                 WHERE id = ? AND use_yn = 'Y'
                """,
                safeTitle,
                safeUrl,
                safeDescription.isBlank() ? null : safeDescription,
                safeCategory.isBlank() ? null : safeCategory,
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
