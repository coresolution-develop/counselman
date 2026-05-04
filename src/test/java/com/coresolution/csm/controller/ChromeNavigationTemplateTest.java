package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ChromeNavigationTemplateTest {

    @Test
    void adminNavigationHighlightsAllAdminSubPages() throws Exception {
        String chrome = Files.readString(Path.of("src/main/resources/static/assets/js/chrome.js"));

        assertThat(chrome).contains("active: ['/roles', '/users', '/access', '/room-board/manage', '/admin/room-board']");
        assertThat(chrome).contains("const activePaths = [it.href, ...(it.active || [])].map(path);");
        assertThat(chrome).contains("const active = activePaths.includes(pathNow) ? ' is-active' : '';");
        assertThat(chrome).contains("{ id: 'stats',         label: '상담통계',     icon: 'chart',      href: '/statistics' }");
        assertThat(chrome).contains("href: '/counsel/log-settings'");
        assertThat(chrome).contains("active: ['/counsel/log-settings', '/admin/counsel/log-settings']");
    }

    @Test
    void roomBoardManageLinksDoNotExposeAdminPath() throws Exception {
        String role = Files.readString(Path.of("src/main/resources/templates/design/role-management.html"));
        String users = Files.readString(Path.of("src/main/resources/templates/design/user-management.html"));
        String access = Files.readString(Path.of("src/main/resources/templates/design/access-management.html"));
        String roomBoard = Files.readString(Path.of("src/main/resources/templates/design/room-board-admin.html"));
        String ward = Files.readString(Path.of("src/main/resources/templates/design/ward-status.html"));

        assertThat(role).contains("@{/room-board/manage}").doesNotContain("@{/admin/room-board}");
        assertThat(users).contains("@{/room-board/manage}").doesNotContain("@{/admin/room-board}");
        assertThat(access).contains("@{/room-board/manage}").doesNotContain("@{/admin/room-board}");
        assertThat(ward).contains("@{/room-board/manage}").doesNotContain("@{/admin/room-board}");
        assertThat(roomBoard).contains("@{/room-board/manage/room/save}");
        assertThat(roomBoard).contains("/room-board/manage/import/preview");
        assertThat(roomBoard).doesNotContain("@{/admin/room-board");
        assertThat(roomBoard).doesNotContain("'/admin/room-board");
    }

    @Test
    void turboVisitedAdminPagesForceFullReload() throws Exception {
        String users = Files.readString(Path.of("src/main/resources/templates/design/user-management.html"));
        String access = Files.readString(Path.of("src/main/resources/templates/design/access-management.html"));

        assertThat(users).contains("name=\"turbo-visit-control\" content=\"reload\"");
        assertThat(users).doesNotContain("Alpine.initTree(root)");
        assertThat(access).contains("name=\"turbo-visit-control\" content=\"reload\"");
        assertThat(access).doesNotContain("Alpine.initTree(root)");
    }

    @Test
    void logSettingsUsesNonAdminPathAndReinitializesOnTurboVisit() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/design/consultation-log-settings.html"));
        String index = Files.readString(Path.of("src/main/resources/templates/design/index.html"));
        String bootstrap = Files.readString(Path.of("src/main/java/com/coresolution/csm/serivce/CsmSchemaBootstrapService.java"));

        assertThat(template).contains("x-data=\"logSettings()\"");
        assertThat(template).contains("name=\"turbo-visit-control\" content=\"reload\"");
        assertThat(template).doesNotContain("Alpine.initTree(root)");
        assertThat(template).contains("ctx + '/counsel/log-settings/save'");
        assertThat(template).doesNotContain("ctx + '/admin/counsel/log-settings/save'");
        assertThat(index).contains("@{/counsel/log-settings}").doesNotContain("@{/admin/counsel/log-settings}");
        assertThat(bootstrap).contains("\"/counsel/log-settings\"").doesNotContain("\"/admin/counsel/log-settings\"");
    }
}
