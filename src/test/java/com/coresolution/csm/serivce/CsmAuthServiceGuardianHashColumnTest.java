package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coresolution.csm.mapper.CsmMapper;

/**
 * Regression test for the counsel/guardian save failure on institutions whose
 * SHA-256 hash columns were provisioned as char(64) instead of varbinary(32)
 * (observed on the COHS institution). The app binds raw 32-byte digests via
 * PreparedStatement#setBytes, which MySQL rejects on a utf8mb4 char column
 * ("Incorrect string value", error 1366) under STRICT_TRANS_TABLES — failing
 * every patient/guardian save. ensureBinaryHashColumn must convert such a column
 * to varbinary(32), add it when absent, and leave a correct column untouched.
 */
@ExtendWith(MockitoExtension.class)
class CsmAuthServiceGuardianHashColumnTest {

    @Mock
    private CsmMapper cs;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CsmAuthService service;

    private void stubColumnType(List<String> result) {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString(), anyString(), anyString()))
                .thenReturn(result);
    }

    @Test
    void convertsCharHashColumnToVarbinary() {
        stubColumnType(List.of("char"));

        service.ensureBinaryHashColumn("csm", "counsel_data_COHS_guardians", "name_hash");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertThat(sql.getValue())
                .contains("MODIFY COLUMN")
                .contains("counsel_data_COHS_guardians")
                .contains("name_hash")
                .contains("varbinary(32)");
    }

    @Test
    void leavesVarbinaryHashColumnUntouched() {
        stubColumnType(List.of("varbinary"));

        service.ensureBinaryHashColumn("csm", "counsel_data_HSOP_0001_guardians", "name_hash");

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void leavesBinaryHashColumnUntouched() {
        // Production provisioned some hash columns as binary(32). A fixed-length
        // binary column stores the exact 32-byte digest fine, so it must NOT be
        // rewritten — converting large prod tables needlessly would risk a lock/rebuild.
        stubColumnType(List.of("binary"));

        service.ensureBinaryHashColumn("csm", "counsel_data_HSFH_guardians", "name_hash");

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void addsMissingHashColumnAsVarbinary() {
        stubColumnType(Collections.emptyList());

        service.ensureBinaryHashColumn("csm", "counsel_data_COHS", "cs_col_01_hash");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertThat(sql.getValue())
                .contains("ADD COLUMN")
                .contains("counsel_data_COHS")
                .contains("cs_col_01_hash")
                .contains("varbinary(32)");
    }
}
