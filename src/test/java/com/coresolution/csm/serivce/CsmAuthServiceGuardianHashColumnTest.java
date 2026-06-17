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
 * Regression test for the counsel/guardian save failure on institutions whose
 * SHA-256 hash columns were misprovisioned. Two distinct bugs were observed,
 * each of which fails every such save under STRICT_TRANS_TABLES:
 *   - char(64) utf8mb4 columns (observed on COHS) reject the raw 32-byte digest
 *     bound via PreparedStatement#setBytes -> "Incorrect string value" (error 1366);
 *   - binary(32) NOT NULL columns (observed on HSFH, HSJH) reject the null digest
 *     written when a guardian's name/phone is left blank (e.g. relationship-only
 *     input) -> "Column ... cannot be null" (error 1048).
 * ensureBinaryHashColumn must convert either case to a nullable varbinary(32), add
 * the column when absent, and leave an already-nullable binary/varbinary column
 * untouched (so a healthy, possibly large table is never needlessly rebuilt).
 */
@ExtendWith(MockitoExtension.class)
class CsmAuthServiceGuardianHashColumnTest {

    @Mock
    private CsmMapper cs;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CsmAuthService service;

    private void stubColumn(String dataType, String isNullable) {
        Map<String, Object> row = new HashMap<>();
        row.put("data_type", dataType);
        row.put("is_nullable", isNullable);
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(row));
    }

    private void stubMissingColumn() {
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void convertsCharHashColumnToVarbinary() {
        stubColumn("char", "YES");

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
    void convertsNotNullBinaryHashColumnToNullable() {
        // HSFH/HSJH provisioned name_hash/contact_number_hash as binary(32) NOT NULL.
        // A guardian saved with name/phone blank (relationship-only) writes a null
        // digest, which the NOT NULL column rejects (error 1048), rolling back the whole
        // counsel save. The column must be relaxed to a nullable varbinary(32).
        stubColumn("binary", "NO");

        service.ensureBinaryHashColumn("csm", "counsel_data_HSFH_guardians", "contact_number_hash");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertThat(sql.getValue())
                .contains("MODIFY COLUMN")
                .contains("counsel_data_HSFH_guardians")
                .contains("contact_number_hash")
                .contains("varbinary(32)");
    }

    @Test
    void convertsNotNullVarbinaryHashColumnToNullable() {
        stubColumn("varbinary", "NO");

        service.ensureBinaryHashColumn("csm", "counsel_data_HSJH_guardians", "name_hash");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertThat(sql.getValue())
                .contains("MODIFY COLUMN")
                .contains("varbinary(32)");
    }

    @Test
    void leavesNullableVarbinaryHashColumnUntouched() {
        stubColumn("varbinary", "YES");

        service.ensureBinaryHashColumn("csm", "counsel_data_HSOP_0001_guardians", "name_hash");

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void leavesNullableBinaryHashColumnUntouched() {
        // A nullable binary(32) (e.g. COHS after its earlier char->binary fix) stores
        // the 32-byte digest AND accepts null, so it must NOT be rewritten — converting
        // large prod tables needlessly would risk a lock/rebuild.
        stubColumn("binary", "YES");

        service.ensureBinaryHashColumn("csm", "counsel_data_COHS_guardians", "name_hash");

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void addsMissingHashColumnAsVarbinary() {
        stubMissingColumn();

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
