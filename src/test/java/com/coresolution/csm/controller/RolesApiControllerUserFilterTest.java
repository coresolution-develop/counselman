package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Regression: GET /api/roles/users (역할 사용자 추가 모달) used
 * {@code WHERE us_col_09 = 1}, while the user list page renders
 * with {@code WHERE us_col_09 != 2}. Legacy rows with NULL or 0
 * appeared in the list but were missing from the modal.
 * Both must use the {@code != 2} (not-deleted) filter.
 */
class RolesApiControllerUserFilterTest {

    @Test
    void rolesUsersEndpointFiltersDeletedRowsConsistentlyWithUserListPage() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/coresolution/csm/controller/RolesApiController.java"));
        String mapper = Files.readString(Path.of(
                "src/main/java/com/coresolution/csm/mapper/CsmMapper.java"));

        assertThat(mapper).contains("us_col_09 != 2");

        // The /api/roles/users endpoint (modal user picker) must NOT use the stricter
        // `us_col_09 = 1` filter — it has to match the user list page filter.
        assertThat(controller)
                .as("RolesApiController.getAllUsers must filter deleted only (us_col_09 != 2)")
                .contains("us_col_09 != 2 ORDER BY us_col_12")
                .doesNotContain("us_col_09 = 1 ORDER BY us_col_12");
    }
}
