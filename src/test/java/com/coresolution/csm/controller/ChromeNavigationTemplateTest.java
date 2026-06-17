package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ChromeNavigationTemplateTest {

    @Test
    void adminNavigationHighlightsAllAdminSubPages() throws Exception {
        String chrome = Files.readString(Path.of("src/main/resources/static/assets/js/chrome.js"));

        // active 배열에 핵심 admin sub-paths가 모두 포함되어야 함 (확장될 수 있어 부분 검사)
        assertThat(chrome).contains("'/roles'", "'/users'", "'/access'",
                "'/room-board/manage'", "'/admin/room-board'");
        assertThat(chrome).contains("const activePaths = [it.href, ...(it.active || [])].map(path);");
        assertThat(chrome).contains("const active = activePaths.includes(pathNow) ? ' is-active' : '';");
        // stats 항목은 항상 존재해야 함 (permKey 추가 등 부가 필드는 무관)
        assertThat(chrome).contains("id: 'stats'", "label: '상담통계'", "href: '/statistics'");
        // 상담일지 관리 경로 (신규 + 레거시) — admin active 배열에 포함되어 있어야 함
        assertThat(chrome).contains("'/counsel/log-settings'", "'/admin/counsel/log-settings'");
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
        assertThat(roomBoard).contains("/room-board/manage/room/save");
        assertThat(roomBoard).contains("/room-board/manage/room/delete");
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
