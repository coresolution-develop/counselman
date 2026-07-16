package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 개인 메모장 검증. 핵심: 가드레일 ①-b(member_id 필터) + 길이 제한 절삭.
 */
@ExtendWith(MockitoExtension.class)
class HubMemoServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private HubMemberService hubMemberService;

    private HubMemoService service() {
        return new HubMemoService(jdbcTemplate, hubMemberService);
    }

    @Test
    void save_rejectsAnonymousMemberId_withoutWrite() {
        assertThatThrownBy(() -> service().save(0L, "메모"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("로그인");
        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    @Test
    void save_upsertsTrimmedContent_forSessionMember() {
        service().save(7L, "  월요일 배포 체크  ");

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member_memo"), args.capture(), args.capture());
        assertThat(args.getAllValues()).containsExactly(7L, "월요일 배포 체크");
    }

    @Test
    void save_truncatesContentToColumnLimit() {
        String tooLong = "가".repeat(HubMemoService.MAX_LENGTH + 50);

        service().save(7L, tooLong);

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member_memo"), args.capture(), args.capture());
        assertThat((String) args.getAllValues().get(1)).hasSize(HubMemoService.MAX_LENGTH);
    }

    @Test
    void save_allowsEmptyContent_soMemoCanBeCleared() {
        service().save(7L, "   ");

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(contains("INSERT INTO csm.hub_member_memo"), args.capture(), args.capture());
        assertThat(args.getAllValues()).containsExactly(7L, "");
    }

    @Test
    void find_filtersBySessionMemberId() {
        when(jdbcTemplate.queryForList(contains("WHERE member_id = ?"), eq(String.class), eq(7L)))
                .thenReturn(List.of("배포 체크"));

        assertThat(service().find(7L)).isEqualTo("배포 체크");
    }

    @Test
    void find_returnsEmpty_whenNeverSaved() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(7L)))
                .thenReturn(List.of());

        assertThat(service().find(7L)).isEmpty();
    }

    @Test
    void find_anonymousMemberId_skipsQuery() {
        assertThat(service().find(0L)).isEmpty();
        verify(jdbcTemplate, never()).queryForList(anyString(), eq(String.class), any());
    }
}
