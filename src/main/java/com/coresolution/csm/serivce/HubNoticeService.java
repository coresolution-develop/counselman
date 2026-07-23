package com.coresolution.csm.serivce;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.vo.HubNotice;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 공지 배너. 단일 행(id=1)만 사용해 upsert한다.
 * 화면 노출은 활성(active_yn='Y') + 메시지 존재일 때만.
 */
@Service
@RequiredArgsConstructor
public class HubNoticeService {

    private final JdbcTemplate jdbcTemplate;
    private final HubMemberService hubMemberService;

    /** 관리 화면 표시용 — 현재 공지(없으면 비활성 빈 공지). */
    public HubNotice find() {
        hubMemberService.ensureTables();
        List<HubNotice> rows = jdbcTemplate.query("""
                SELECT message, level, active_yn,
                       DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at
                  FROM csm.hub_notice WHERE id = 1 LIMIT 1
                """, (rs, n) -> map(rs));
        if (rows.isEmpty()) {
            HubNotice empty = new HubNotice();
            empty.setMessage("");
            empty.setLevel("info");
            empty.setActiveYn("N");
            empty.setUpdatedAt("");
            return empty;
        }
        return rows.get(0);
    }

    /** 배너 노출용 — 비활성이거나 메시지가 비면 null. */
    public HubNotice findActive() {
        HubNotice notice = find();
        if (!"Y".equals(notice.getActiveYn()) || notice.getMessage() == null || notice.getMessage().isBlank()) {
            return null;
        }
        return notice;
    }

    @Transactional
    public void save(String message, String level, boolean active) {
        hubMemberService.ensureTables();
        String text = message == null ? "" : message.trim();
        if (text.length() > 500) {
            text = text.substring(0, 500);
        }
        String safeLevel = "warn".equalsIgnoreCase(level) ? "warn" : "info";
        jdbcTemplate.update("""
                INSERT INTO csm.hub_notice (id, message, level, active_yn) VALUES (1, ?, ?, ?)
                ON DUPLICATE KEY UPDATE message = VALUES(message), level = VALUES(level), active_yn = VALUES(active_yn)
                """, text, safeLevel, active ? "Y" : "N");
    }

    private HubNotice map(ResultSet rs) throws SQLException {
        HubNotice notice = new HubNotice();
        notice.setMessage(rs.getString("message"));
        notice.setLevel(rs.getString("level"));
        notice.setActiveYn(rs.getString("active_yn"));
        notice.setUpdatedAt(rs.getString("updated_at"));
        return notice;
    }
}
