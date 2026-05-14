package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Regression: PUT /api/users/{id} silently dropped role updates because
 * {@link UserApiController#toLong(Object)} cast any non-Number value
 * (e.g. JSON strings from Alpine {@code x-model} on a {@code <select>})
 * to {@code null}, skipping {@code us_col_08} and {@code user_role_*} updates.
 */
class UserApiControllerToLongTest {

    @Test
    void toLong_acceptsNumericString() {
        assertThat(UserApiController.toLong("5")).isEqualTo(5L);
        assertThat(UserApiController.toLong("  42 ")).isEqualTo(42L);
    }

    @Test
    void toLong_acceptsNumber() {
        assertThat(UserApiController.toLong(5)).isEqualTo(5L);
        assertThat(UserApiController.toLong(5L)).isEqualTo(5L);
        assertThat(UserApiController.toLong(5.0)).isEqualTo(5L);
    }

    @Test
    void toLong_returnsNullForBlankOrInvalid() {
        assertThat(UserApiController.toLong(null)).isNull();
        assertThat(UserApiController.toLong("")).isNull();
        assertThat(UserApiController.toLong("   ")).isNull();
        assertThat(UserApiController.toLong("null")).isNull();
        assertThat(UserApiController.toLong("abc")).isNull();
    }
}
