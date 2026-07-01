package com.coresolution.links.serivce;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.coresolution.links.vo.HubHistoryView;

import lombok.RequiredArgsConstructor;

/**
 * 최근 사용 이력. 링크 클릭 시 스냅샷으로 기록하고(원본 삭제돼도 표시 유지),
 * 조회 시 url_snapshot 기준 중복 제거 후 최신 상위 8개를 반환한다.
 */
@Service
@RequiredArgsConstructor
public class HubHistoryService {

    private static final int RECENT_LIMIT = 8;

    private final JdbcTemplate jdbcTemplate;
    private final HubMemberService hubMemberService;

    public void record(long memberId, String linkType, Long linkId, Long customLinkId,
            String titleSnapshot, String urlSnapshot) {
        hubMemberService.ensureTables();
        if (memberId <= 0 || urlSnapshot == null || urlSnapshot.isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO csm.hub_member_link_history
                    (member_id, link_type, link_id, custom_link_id, title_snapshot, url_snapshot)
                VALUES (?, ?, ?, ?, ?, ?)
                """, memberId, linkType, linkId, customLinkId,
                trimTo(titleSnapshot, 200), trimTo(urlSnapshot, 500));
    }

    /** url_snapshot 기준 중복 제거 후 최신순 상위 8개. */
    public List<HubHistoryView> listRecent(long memberId) {
        hubMemberService.ensureTables();
        if (memberId <= 0) {
            return List.of();
        }
        // url_snapshot별 최신 1건만: PK MAX(id)로 묶어 같은 초에 여러 번 클릭해도 중복이 남지 않게 한다
        // (accessed_at은 DATETIME 1초 해상도라 MAX(accessed_at) 기준이면 동초 클릭 시 중복 가능).
        // LIMIT은 내부 상수 RECENT_LIMIT을 직접 연결한다 — SQL에 DATE_FORMAT의 '%Y' 등 '%'가 있어
        // String.formatted()를 쓰면 포맷 지정자로 오인되므로 사용하지 않는다.
        return jdbcTemplate.query("""
                SELECT h.link_type, h.link_id, h.custom_link_id, h.title_snapshot, h.url_snapshot,
                       DATE_FORMAT(h.accessed_at, '%Y-%m-%d %H:%i:%s') AS accessed_at
                  FROM csm.hub_member_link_history h
                  JOIN (
                        SELECT MAX(id) AS max_id
                          FROM csm.hub_member_link_history
                         WHERE member_id = ?
                         GROUP BY url_snapshot
                       ) latest
                    ON latest.max_id = h.id
                 ORDER BY h.accessed_at DESC, h.id DESC
                """ + " LIMIT " + RECENT_LIMIT, (rs, rowNum) -> {
            HubHistoryView view = new HubHistoryView();
            view.setLinkType(rs.getString("link_type"));
            view.setLinkId((Long) rs.getObject("link_id"));
            view.setCustomLinkId((Long) rs.getObject("custom_link_id"));
            view.setTitle(rs.getString("title_snapshot"));
            view.setUrl(rs.getString("url_snapshot"));
            view.setAccessedAt(rs.getString("accessed_at"));
            return view;
        }, memberId);
    }

    private String trimTo(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
