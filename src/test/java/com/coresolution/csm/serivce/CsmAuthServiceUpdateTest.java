package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coresolution.csm.mapper.CsmMapper;

/**
 * Regression tests for the core_update management feature.
 *
 * Covers: validation guards, status transition SQL shape, and the user-side
 * read-tracking / popup query early-exit paths. JdbcTemplate is mocked so we
 * verify the SQL contract without hitting a real database.
 */
@ExtendWith(MockitoExtension.class)
class CsmAuthServiceUpdateTest {

    @Mock private CsmMapper cs;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private CsmAuthService service;

    // ── Validation guards ─────────────────────────────────────────────

    @Test
    void coreUpdateSave_rejectsBlankVersion() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("version", "  ");
        payload.put("title", "v1.0 출시");

        assertThatThrownBy(() -> service.coreUpdateSave(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("버전");
    }

    @Test
    void coreUpdateSave_rejectsBlankTitle() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("version", "v1.0.0");
        payload.put("title", "");

        assertThatThrownBy(() -> service.coreUpdateSave(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("제목");
    }

    // ── Status transition SQL ─────────────────────────────────────────

    @Test
    void updateStatus_published_setsPublishedAtIfNull() {
        // 이미 published_at이 있으면 NOW()로 덮어쓰지 않아야 함 → COALESCE 사용
        when(jdbcTemplate.update(contains("published_at = COALESCE(published_at, NOW())"),
                eq("PUBLISHED"), eq(42L))).thenReturn(1);

        int updated = service.coreUpdateUpdateStatus(42L, "PUBLISHED");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate).update(contains("published_at = COALESCE(published_at, NOW())"),
                eq("PUBLISHED"), eq(42L));
    }

    @Test
    void updateStatus_draft_doesNotTouchPublishedAt() {
        when(jdbcTemplate.update(contains("UPDATE csm.core_update SET status = ? WHERE id = ?"),
                eq("DRAFT"), eq(42L))).thenReturn(1);

        int updated = service.coreUpdateUpdateStatus(42L, "DRAFT");

        assertThat(updated).isEqualTo(1);
        // DRAFT 전환은 published_at을 건드리지 않는다 (COALESCE 절이 없어야 함)
        verify(jdbcTemplate, never()).update(contains("COALESCE(published_at, NOW())"), any(), any());
    }

    @Test
    void updateStatus_zeroId_returnsZeroWithoutQuerying() {
        // ensureCoreUpdateTables는 DDL 실행을 위해 jdbcTemplate.execute 호출. 그 외 update는 호출 안 됨.
        int updated = service.coreUpdateUpdateStatus(0L, "PUBLISHED");

        assertThat(updated).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    // ── User-side read tracking ───────────────────────────────────────

    @Test
    void markUpdateRead_blankUserId_returnsZero() {
        int result = service.markUpdateRead("FALH", "  ", null, 5L);
        assertThat(result).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    void markUpdateRead_validUser_callsUpsert() {
        when(jdbcTemplate.update(contains("INSERT INTO csm.core_update_read"),
                eq(5L), eq("FALH"), eq(101), eq("alice"))).thenReturn(1);

        int result = service.markUpdateRead("FALH", "alice", 101, 5L);

        assertThat(result).isEqualTo(1);
        verify(jdbcTemplate).update(contains("ON DUPLICATE KEY UPDATE"),
                eq(5L), eq("FALH"), eq(101), eq("alice"));
    }

    // ── Popup query ───────────────────────────────────────────────────

    @Test
    void listUnreadPopupUpdates_blankUserId_returnsEmpty() {
        List<Map<String, Object>> result = service.listUnreadPopupUpdates("FALH", "", 5);
        assertThat(result).isEmpty();
        // ensureCoreUpdateTables의 DDL은 호출됐지만, SELECT는 발생하지 않아야 함
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void listUnreadPopupUpdates_filtersPopupYAndUnread() {
        when(jdbcTemplate.queryForList(contains("u.popup_yn = 'Y' AND r.id IS NULL"),
                eq("FALH"), eq("alice"), eq(5)))
                .thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = service.listUnreadPopupUpdates("FALH", "alice", 5);

        assertThat(result).isEmpty();
        verify(jdbcTemplate).queryForList(contains("u.popup_yn = 'Y' AND r.id IS NULL"),
                eq("FALH"), eq("alice"), eq(5));
    }

    // ── Listing for non-logged-in callers ─────────────────────────────

    @Test
    void listPublishedUpdates_blankUserId_skipsReadJoin() {
        // 비로그인 사용자도 list는 볼 수 있어야 하지만, read 조인은 없어야 함
        when(jdbcTemplate.queryForList(anyString(), eq(200))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = service.listPublishedUpdates("FALH", "", 200);

        assertThat(result).isEmpty();
        verify(jdbcTemplate, times(1)).queryForList(anyString(), eq(200));
    }
}
