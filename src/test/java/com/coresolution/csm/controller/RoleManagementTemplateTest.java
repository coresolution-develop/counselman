package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RoleManagementTemplateTest {

    @Test
    void roleManagementUsesPageScopedAlpineComponent() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/design/role-management.html"));
        String sharedApp = Files.readString(Path.of("src/main/resources/static/assets/js/app.js"));

        assertThat(sharedApp).contains("Alpine.data('roleManager'");
        assertThat(template).contains("x-data=\"roleManagementPage()\"");
        assertThat(template).contains("Alpine.data('roleManagementPage'");
        assertThat(template).contains("name=\"turbo-visit-control\" content=\"reload\"");
        assertThat(template).doesNotContain("x-data=\"roleManager()\"");
        assertThat(template).doesNotContain("Alpine.data('roleManager'");
    }
}
