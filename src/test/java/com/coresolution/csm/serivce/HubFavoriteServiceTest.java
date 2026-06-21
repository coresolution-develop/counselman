package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 2 즐겨찾기 검증. 핵심은 가드레일 ③: 등록 전 company_link 실재+활성 확인,
 * 아니면 insert 없이 거부.
 */
@ExtendWith(MockitoExtension.class)
class HubFavoriteServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private HubMemberService hubMemberService;

    private HubFavoriteService service() {
        return new HubFavoriteService(jdbcTemplate, hubMemberService);
    }

    @Test
    void toggle_existingFavorite_isRemovedAndReturnsFalse() {
        when(jdbcTemplate.queryForObject(contains("FROM csm.hub_member_favorite"), eq(Integer.class), eq(7L), eq(3L)))
                .thenReturn(1);

        boolean favorited = service().toggle(7L, 3L);

        assertThat(favorited).isFalse();
        verify(jdbcTemplate).update(contains("DELETE FROM csm.hub_member_favorite"), eq(7L), eq(3L));
        verify(jdbcTemplate, never()).update(contains("INSERT INTO csm.hub_member_favorite"), eq(7L), eq(3L));
    }

    @Test
    void toggle_newFavoriteOnActiveLink_isInsertedAndReturnsTrue() {
        when(jdbcTemplate.queryForObject(contains("FROM csm.hub_member_favorite"), eq(Integer.class), eq(7L), eq(3L)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("FROM csm.company_link WHERE id = ? AND use_yn = 'Y'"), eq(Integer.class), eq(3L)))
                .thenReturn(1);

        boolean favorited = service().toggle(7L, 3L);

        assertThat(favorited).isTrue();
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member_favorite"), eq(7L), eq(3L));
    }

    @Test
    void toggle_newFavoriteOnMissingOrInactiveLink_isRejectedWithoutInsert() {
        when(jdbcTemplate.queryForObject(contains("FROM csm.hub_member_favorite"), eq(Integer.class), eq(7L), eq(99L)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("FROM csm.company_link WHERE id = ? AND use_yn = 'Y'"), eq(Integer.class), eq(99L)))
                .thenReturn(0);

        assertThatThrownBy(() -> service().toggle(7L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용할 수 없는 링크");
        verify(jdbcTemplate, never()).update(contains("INSERT INTO csm.hub_member_favorite"), eq(7L), eq(99L));
    }

    @Test
    void toggle_rejectsNonPositiveIds() {
        assertThatThrownBy(() -> service().toggle(0L, 3L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().toggle(7L, 0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listFavoriteLinkIds_returnsIdsForMember() {
        when(jdbcTemplate.queryForList(contains("SELECT link_id FROM csm.hub_member_favorite"), eq(Long.class), eq(7L)))
                .thenReturn(java.util.List.of(3L, 5L));

        assertThat(service().listFavoriteLinkIds(7L)).containsExactly(3L, 5L);
    }
}
