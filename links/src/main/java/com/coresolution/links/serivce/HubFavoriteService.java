package com.coresolution.links.serivce;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 공개 허브 ★ 즐겨찾기. 공용 링크(csm.company_link)를 회원이 핀으로 고정한다.
 *
 * <p>모든 쿼리는 세션의 member_id로 강제 필터한다(가드레일 ①-b). 토글 insert 전에는
 * 대상 링크가 company_link에 실재하고 활성(use_yn='Y')인지 확인한다(가드레일 ③) —
 * 임의 link_id 주입으로 유령 즐겨찾기가 생기는 것을 막는다.
 */
@Service
@RequiredArgsConstructor
public class HubFavoriteService {

    private final JdbcTemplate jdbcTemplate;
    private final HubMemberService hubMemberService;

    /**
     * 즐겨찾기 토글. 이미 있으면 해제(false), 없으면 등록(true).
     * 등록 시 link_id가 company_link에 실재+활성이어야 한다(아니면 IllegalArgumentException).
     */
    @Transactional
    public boolean toggle(long memberId, long linkId) {
        hubMemberService.ensureTables();
        if (memberId <= 0 || linkId <= 0) {
            throw new IllegalArgumentException("잘못된 요청입니다.");
        }
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM csm.hub_member_favorite WHERE member_id = ? AND link_id = ?",
                Integer.class, memberId, linkId);
        if (existing != null && existing > 0) {
            jdbcTemplate.update(
                    "DELETE FROM csm.hub_member_favorite WHERE member_id = ? AND link_id = ?",
                    memberId, linkId);
            return false;
        }
        if (!isActiveLink(linkId)) {
            throw new IllegalArgumentException("존재하지 않거나 사용할 수 없는 링크입니다.");
        }
        jdbcTemplate.update(
                "INSERT INTO csm.hub_member_favorite (member_id, link_id) VALUES (?, ?)",
                memberId, linkId);
        return true;
    }

    /** 개인 페이지 표시용 — 회원의 즐겨찾기를 활성 공용 링크와 조인해 반환(정렬: 즐겨찾기 등록순). */
    public List<com.coresolution.links.vo.CompanyLink> listFavorites(long memberId) {
        hubMemberService.ensureTables();
        if (memberId <= 0) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT cl.id, cl.title, cl.url, cl.description, cl.category
                  FROM csm.hub_member_favorite f
                  JOIN csm.company_link cl ON cl.id = f.link_id AND cl.use_yn = 'Y'
                 WHERE f.member_id = ?
                 ORDER BY f.sort_order ASC, f.id ASC
                """, (rs, rowNum) -> {
            com.coresolution.links.vo.CompanyLink link = new com.coresolution.links.vo.CompanyLink();
            link.setId(rs.getLong("id"));
            link.setTitle(rs.getString("title"));
            link.setUrl(rs.getString("url"));
            link.setDescription(rs.getString("description"));
            link.setCategory(rs.getString("category"));
            return link;
        }, memberId);
    }

    /** 공개 허브 렌더 시 ★ 채움 표시용 — 해당 회원의 즐겨찾기 link_id 목록. */
    public List<Long> listFavoriteLinkIds(long memberId) {
        hubMemberService.ensureTables();
        if (memberId <= 0) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT link_id FROM csm.hub_member_favorite WHERE member_id = ?",
                Long.class, memberId);
    }

    private boolean isActiveLink(long linkId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM csm.company_link WHERE id = ? AND use_yn = 'Y'",
                Integer.class, linkId);
        return count != null && count > 0;
    }
}
