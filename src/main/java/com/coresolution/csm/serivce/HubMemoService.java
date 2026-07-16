package com.coresolution.csm.serivce;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 허브 개인 메모장. 회원당 1행(csm.hub_member_memo)이며 본인만 조회/수정한다.
 *
 * <p>모든 쿼리는 세션에서 받은 member_id로 강제 필터한다(가드레일 ①-b).
 * 메모는 자유 텍스트라 URL 검증 대상이 아니며, 출력은 Thymeleaf th:text가 이스케이프한다.
 */
@Service
@RequiredArgsConstructor
public class HubMemoService {

    /** DDL의 VARCHAR(2000)과 맞춘다. 초과분은 잘라서 저장한다. */
    static final int MAX_LENGTH = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final HubMemberService hubMemberService;

    /** 본인 메모 내용. 아직 저장한 적이 없으면 빈 문자열. */
    public String find(long memberId) {
        hubMemberService.ensureTables();
        if (memberId <= 0) {
            return "";
        }
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT content FROM csm.hub_member_memo WHERE member_id = ?",
                String.class, memberId);
        return rows.isEmpty() || rows.get(0) == null ? "" : rows.get(0);
    }

    /**
     * 메모 저장(upsert). 첫 저장이면 INSERT, 이후엔 content만 교체한다.
     * 빈 내용도 정상 저장이다 — 사용자가 메모를 비우는 것과 같다.
     */
    @Transactional
    public void save(long memberId, String content) {
        hubMemberService.ensureTables();
        if (memberId <= 0) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        jdbcTemplate.update("""
                INSERT INTO csm.hub_member_memo (member_id, content)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE content = VALUES(content)
                """, memberId, trimTo(content));
    }

    private String trimTo(String value) {
        if (value == null) {
            return "";
        }
        String text = value.strip();
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }
}
