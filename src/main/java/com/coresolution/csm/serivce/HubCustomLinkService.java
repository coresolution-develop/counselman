package com.coresolution.csm.serivce;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.vo.HubCustomLink;
import com.coresolution.csm.web.HubUrls;

import lombok.RequiredArgsConstructor;

/**
 * 개인 커스텀 링크 CRUD. 모든 조회/수정/삭제는 세션 member_id로 강제 필터한다(가드레일 ①-b).
 * URL은 http(s)만 허용한다(가드레일 ②, {@link HubUrls}).
 */
@Service
@RequiredArgsConstructor
public class HubCustomLinkService {

    private final JdbcTemplate jdbcTemplate;
    private final HubMemberService hubMemberService;

    public List<HubCustomLink> listOwn(long memberId) {
        hubMemberService.ensureTables();
        if (memberId <= 0) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id, member_id, title, url, memo, sort_order,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                  FROM csm.hub_member_custom_link
                 WHERE member_id = ? AND use_yn = 'Y'
                 ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> mapRow(rs), memberId);
    }

    /** /hub/go/custom/{id} 해석용 — 본인 소유의 활성 링크만 반환(없으면 null). */
    public HubCustomLink findOwn(long id, long memberId) {
        hubMemberService.ensureTables();
        if (id <= 0 || memberId <= 0) {
            return null;
        }
        List<HubCustomLink> rows = jdbcTemplate.query("""
                SELECT id, member_id, title, url, memo, sort_order,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                  FROM csm.hub_member_custom_link
                 WHERE id = ? AND member_id = ? AND use_yn = 'Y'
                 LIMIT 1
                """, (rs, rowNum) -> mapRow(rs), id, memberId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public long create(long memberId, String title, String url, String memo, Integer sortOrder) {
        hubMemberService.ensureTables();
        if (memberId <= 0) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        String safeTitle = requireText(title, "링크 이름", 200);
        String safeUrl = HubUrls.normalizeHttpUrl(url);
        String safeMemo = trimTo(memo, 300);
        int safeSort = sortOrder == null ? 0 : Math.max(0, Math.min(sortOrder, 9999));
        jdbcTemplate.update("""
                INSERT INTO csm.hub_member_custom_link (member_id, title, url, memo, sort_order)
                VALUES (?, ?, ?, ?, ?)
                """, memberId, safeTitle, safeUrl, safeMemo.isBlank() ? null : safeMemo, safeSort);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    @Transactional
    public boolean update(long id, long memberId, String title, String url, String memo, Integer sortOrder) {
        hubMemberService.ensureTables();
        if (id <= 0 || memberId <= 0) {
            throw new IllegalArgumentException("잘못된 요청입니다.");
        }
        String safeTitle = requireText(title, "링크 이름", 200);
        String safeUrl = HubUrls.normalizeHttpUrl(url);
        String safeMemo = trimTo(memo, 300);
        int safeSort = sortOrder == null ? 0 : Math.max(0, Math.min(sortOrder, 9999));
        // WHERE에 member_id를 포함해 타인 링크 수정 불가(가드레일 ①-b)
        return jdbcTemplate.update("""
                UPDATE csm.hub_member_custom_link
                   SET title = ?, url = ?, memo = ?, sort_order = ?
                 WHERE id = ? AND member_id = ? AND use_yn = 'Y'
                """, safeTitle, safeUrl, safeMemo.isBlank() ? null : safeMemo, safeSort, id, memberId) > 0;
    }

    @Transactional
    public boolean delete(long id, long memberId) {
        hubMemberService.ensureTables();
        if (id <= 0 || memberId <= 0) {
            throw new IllegalArgumentException("잘못된 요청입니다.");
        }
        return jdbcTemplate.update("""
                UPDATE csm.hub_member_custom_link
                   SET use_yn = 'N'
                 WHERE id = ? AND member_id = ? AND use_yn = 'Y'
                """, id, memberId) > 0;
    }

    private HubCustomLink mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        HubCustomLink link = new HubCustomLink();
        link.setId(rs.getLong("id"));
        link.setMemberId(rs.getLong("member_id"));
        link.setTitle(rs.getString("title"));
        link.setUrl(rs.getString("url"));
        link.setMemo(rs.getString("memo"));
        link.setSortOrder(rs.getInt("sort_order"));
        link.setCreatedAt(rs.getString("created_at"));
        return link;
    }

    private String requireText(String value, String label, int maxLen) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(label + "을 입력해주세요.");
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private String trimTo(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
