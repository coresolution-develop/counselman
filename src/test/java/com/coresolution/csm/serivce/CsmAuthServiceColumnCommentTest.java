package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coresolution.csm.mapper.CsmMapper;

/**
 * Regression test for institutions created without column comments on
 * counsel_data_{inst}: the list-settings popup showed raw names (cs_col_01 ...)
 * instead of labels. ensureCounselDataColumnComments must backfill only the
 * empty comments while keeping the existing column definition.
 */
@ExtendWith(MockitoExtension.class)
class CsmAuthServiceColumnCommentTest {

    @Mock
    private CsmMapper cs;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CsmAuthService service;

    private static Map<String, Object> column(String name, String type, String nullable, String def) {
        Map<String, Object> row = new HashMap<>();
        row.put("column_name", name);
        row.put("column_type", type);
        row.put("is_nullable", nullable);
        row.put("column_default", def);
        row.put("character_set_name", "utf8mb4");
        row.put("collation_name", "utf8mb4_unicode_ci");
        return row;
    }

    @Test
    void backfillsMissingCommentsKeepingDefinition() {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(List.of(
                column("cs_col_01", "varchar(500)", "YES", null),
                column("cs_col_32", "longtext", "YES", "NULL"),
                column("cs_col_35", "varchar(50)", "NO", "'y'"),
                column("cs_col_01_hash", "varbinary(32)", "YES", null)));

        service.ensureCounselDataColumnComments("TEST");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertThat(sql.getValue())
                .startsWith("ALTER TABLE csm.counsel_data_TEST ")
                .contains("MODIFY COLUMN `cs_col_01` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '환자명'")
                .contains("MODIFY COLUMN `cs_col_32` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '상담내용'")
                .contains("MODIFY COLUMN `cs_col_35` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'y' COMMENT '보이기=y/감추기=n'")
                .doesNotContain("cs_col_01_hash");
    }

    @Test
    void doesNothingWhenAllCommentsPresent() {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(Collections.emptyList());

        service.ensureCounselDataColumnComments("TEST");

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
