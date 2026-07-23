package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 3 커스텀 링크 검증. 핵심: 가드레일 ②(http(s)만) + ①-b(member_id 필터).
 */
@ExtendWith(MockitoExtension.class)
class HubCustomLinkServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private HubMemberService hubMemberService;

    private HubCustomLinkService service() {
        return new HubCustomLinkService(jdbcTemplate, hubMemberService);
    }

    @Test
    void create_rejectsJavascriptUrl_withoutInsert() {
        assertThatThrownBy(() -> service().create(7L, "위험", "javascript:alert(1)", null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http 또는 https");
        verify(jdbcTemplate, never()).update(contains("INSERT INTO csm.hub_member_custom_link"),
                any(), any(), any(), any(), any());
    }

    @Test
    void create_rejectsNonHttpScheme() {
        assertThatThrownBy(() -> service().create(7L, "파일", "file:///etc/passwd", null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http 또는 https");
    }

    @Test
    void create_storesWithMemberId() {
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(11L);

        long id = service().create(7L, "  내 링크 ", "https://x.example.com", null, 3);

        assertThat(id).isEqualTo(11L);
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member_custom_link"),
                eq(7L), eq("내 링크"), eq("https://x.example.com"), isNull(), eq(3));
    }

    @Test
    void update_scopesToMemberId() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), eq(9L), eq(7L))).thenReturn(1);

        boolean updated = service().update(9L, 7L, "수정", "https://y.example.com", "메모", 0);

        assertThat(updated).isTrue();
        // WHERE 절이 member_id로 제한되어 타인 링크 수정 불가
        verify(jdbcTemplate).update(contains("member_id = ?"),
                eq("수정"), eq("https://y.example.com"), eq("메모"), eq(0), eq(9L), eq(7L));
    }

    @Test
    void delete_softDeletesScopedToMemberId() {
        when(jdbcTemplate.update(contains("SET use_yn = 'N'"), eq(9L), eq(7L))).thenReturn(1);

        boolean deleted = service().delete(9L, 7L);

        assertThat(deleted).isTrue();
        verify(jdbcTemplate).update(contains("member_id = ?"), eq(9L), eq(7L));
    }

    @Test
    void importBookmarks_insertsNewHttpLinks_skipsDupAndInvalid() {
        // 이미 가진 URL 하나 — 중복 제거 대상
        when(jdbcTemplate.queryForList(contains("SELECT url"), eq(String.class), eq(7L)))
                .thenReturn(java.util.List.of("https://dup.example.com"));

        String html = """
                <DL><p>
                  <DT><A HREF="https://new1.example.com">New One</A>
                  <DT><A HREF="https://dup.example.com">Dup</A>
                  <DT><A HREF="javascript:alert(1)">Bad</A>
                  <DT><A HREF="https://new2.example.com">New &amp; Two</A>
                </DL>
                """;

        int[] result = service().importBookmarks(7L, html);

        assertThat(result[0]).isEqualTo(2); // new1, new2 등록
        assertThat(result[1]).isEqualTo(2); // dup, javascript 건너뜀
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member_custom_link"),
                eq(7L), eq("New One"), eq("https://new1.example.com"), eq(0));
        // HTML 엔티티(&amp;)가 &로 풀려 저장된다
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member_custom_link"),
                eq(7L), eq("New & Two"), eq("https://new2.example.com"), eq(0));
        // 기존 URL은 재삽입되지 않는다
        verify(jdbcTemplate, never()).update(contains("INSERT INTO csm.hub_member_custom_link"),
                eq(7L), anyString(), eq("https://dup.example.com"), eq(0));
    }
}
