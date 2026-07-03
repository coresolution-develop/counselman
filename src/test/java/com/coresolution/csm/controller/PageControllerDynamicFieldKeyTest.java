package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the inpatient-consultation dynamic-field persistence bug.
 *
 * Bug: the inpatient page rendered dynamic fields from the orphaned __LOG_SETTINGS__
 * store (getLogSettings) whenever a stale row lingered. That store's fieldKeys are
 * "ls_&lt;item&gt;_&lt;value&gt;", but both save (parseDynamicEntries, which only accepts
 * params starting with "field_") and load (buildValueMap, which emits "field_&lt;cat&gt;_&lt;sub&gt;")
 * speak the category-table format. So 상태 dynamic fields such as 배뇨/배변 and
 * 기저귀사용유무 were dropped on save and never restored on reload.
 *
 * Fix: render from the category tables (SSOT) so the fieldKeys round-trip. This test
 * pins the reason the log-settings format cannot be used for persistence.
 */
class PageControllerDynamicFieldKeyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Mirrors parseDynamicEntries' gate: only "field_"-prefixed params are persisted. */
    private static boolean isPersistedOnSave(String fieldKey) {
        return (fieldKey + "_select").startsWith("field_");
    }

    @Test
    void logSettingsFieldKeys_areNotPersistable() throws Exception {
        // 상태 카테고리에 배뇨/배변 (checkbox_select) 이 있는 최소 로그세팅 스냅샷.
        String logSettingsJson = """
            [
              {
                "name": "상태",
                "values": [
                  {"name": "배뇨/배변", "kind": ["checkbox", "select"],
                   "options": [{"name": "유"}, {"name": "무"}]}
                ]
              }
            ]
            """;

        String rendered = new PageController().toLogSettingsDynamicJson(logSettingsJson);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = mapper.readValue(rendered, List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) rows.get(0).get("values");
        String fieldKey = (String) values.get(0).get("fieldKey");

        // The log-settings store emits "ls_*" keys...
        assertThat(fieldKey).startsWith("ls_");
        // ...which the save path silently drops, so the value can never round-trip.
        assertThat(isPersistedOnSave(fieldKey)).isFalse();
    }

    @Test
    void categoryTableFieldKeys_arePersistable() {
        // The category-table render uses "field_<cat>_<sub>", which save accepts.
        assertThat(isPersistedOnSave("field_5_12")).isTrue();
    }
}
