package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coresolution.csm.mapper.CsmMapper;

@ExtendWith(MockitoExtension.class)
class CsmAuthServiceTransactionTest {

    @Mock
    private CsmMapper cs;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CsmAuthService service;

    // ── coreTemplateMainDeleteCascade ──────────────────────────────────────────

    @Test
    void mainDeleteCascade_deletesOptionsAndSubsBeforeMain() {
        List<Map<String, Object>> subs = new ArrayList<>();
        subs.add(Map.of("idx", 10));
        subs.add(Map.of("idx", 20));
        when(cs.coreTemplateSubSelect(5)).thenReturn(subs);

        service.coreTemplateMainDeleteCascade(5);

        InOrder order = inOrder(cs);
        order.verify(cs).coreTemplateOptionDeleteBySubIdx(10);
        order.verify(cs).coreTemplateOptionDeleteBySubIdx(20);
        order.verify(cs).coreTemplateSubDeleteByMainIdx(5);
        order.verify(cs).coreTemplateMainDelete(5);
    }

    @Test
    void mainDeleteCascade_emptySubList_stillDeletesMain() {
        when(cs.coreTemplateSubSelect(7)).thenReturn(Collections.emptyList());

        service.coreTemplateMainDeleteCascade(7);

        verify(cs, never()).coreTemplateOptionDeleteBySubIdx(anyInt());
        verify(cs).coreTemplateSubDeleteByMainIdx(7);
        verify(cs).coreTemplateMainDelete(7);
    }

    @Test
    void mainDeleteCascade_nullEntryInSubList_skipped() {
        List<Map<String, Object>> subs = new ArrayList<>();
        subs.add(null);
        subs.add(Map.of("idx", 30));
        when(cs.coreTemplateSubSelect(9)).thenReturn(subs);

        service.coreTemplateMainDeleteCascade(9);

        verify(cs, times(1)).coreTemplateOptionDeleteBySubIdx(30);
        verify(cs).coreTemplateSubDeleteByMainIdx(9);
    }

    // ── coreTemplateSubDeleteCascade ───────────────────────────────────────────

    @Test
    void subDeleteCascade_deletesOptionsBeforeSub() {
        service.coreTemplateSubDeleteCascade(15);

        InOrder order = inOrder(cs);
        order.verify(cs).coreTemplateOptionDeleteBySubIdx(15);
        order.verify(cs).coreTemplateSubDelete(15);
    }

    // ── deleteCategory ─────────────────────────────────────────────────────────

    @Test
    void deleteCategory_parent_cascadesCategory3ThenCategory2ThenCategory1() {
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(1)))
                .thenReturn(List.of(100, 200));

        service.deleteCategory("FALH", "parent", 1);

        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update(contains("counsel_category3_FALH"), eq(100));
        order.verify(jdbcTemplate).update(contains("counsel_category3_FALH"), eq(200));
        order.verify(jdbcTemplate).update(contains("counsel_category2_FALH WHERE cc_col_03"), eq(1));
        order.verify(jdbcTemplate).update(contains("counsel_category1_FALH"), eq(1));
    }

    @Test
    void deleteCategory_child_deletesCategory3ThenCategory2() {
        service.deleteCategory("FALH", "child", 5);

        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update(contains("counsel_category3_FALH"), eq(5));
        order.verify(jdbcTemplate).update(contains("counsel_category2_FALH WHERE cc_col_01"), eq(5));
    }

    @Test
    void deleteCategory_option_deletesOnlyCategory3() {
        service.deleteCategory("FALH", "option", 8);

        verify(jdbcTemplate).update(contains("counsel_category3_FALH WHERE cc_col_01"), eq(8));
        verify(jdbcTemplate, times(1)).update(anyString(), anyInt());
    }

    @Test
    void deleteCategory_invalidId_returnsZeroWithoutDbCalls() {
        int result = service.deleteCategory("FALH", "parent", 0);

        assert result == 0;
        verify(jdbcTemplate, never()).update(anyString(), anyInt());
        verify(jdbcTemplate, never()).queryForList(anyString(), eq(Integer.class), anyInt());
    }

    // ── coreNoticeSave validation ──────────────────────────────────────────────

    @Test
    void noticeSave_blankTitle_throwsIllegalArgument() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "  ");

        assertThatThrownBy(() -> service.coreNoticeSave(payload, List.of("FALH")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("제목");
    }

    @Test
    void noticeSave_emptyTargets_throwsIllegalArgument() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "공지 제목");

        assertThatThrownBy(() -> service.coreNoticeSave(payload, Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기관");
    }

    @Test
    void noticeSave_startAfterEnd_throwsIllegalArgument() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "공지 제목");
        payload.put("startAt", "2026-12-31 00:00:00");
        payload.put("endAt", "2026-01-01 00:00:00");

        assertThatThrownBy(() -> service.coreNoticeSave(payload, List.of("FALH")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시작일");
    }

    @Test
    void noticeSave_nullPayload_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.coreNoticeSave(null, List.of("FALH")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("제목");
    }
}
