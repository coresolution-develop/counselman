package com.coresolution.csm.serivce;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.vo.Instdata;
import com.coresolution.csm.vo.ModuleFeatureDefinition;
import com.coresolution.csm.vo.ModuleFeatureRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModuleFeatureService {
    public static final String FEATURE_ROOM_BOARD = "ROOM_BOARD";
    public static final String FEATURE_COUNSEL_AUDIO = "COUNSEL_AUDIO";
    public static final String FEATURE_ADMISSION_PLEDGE = "ADMISSION_PLEDGE";
    public static final String FEATURE_COUNSEL_FILE = "COUNSEL_FILE";

    private static final List<ModuleFeatureDefinition> FEATURE_DEFINITIONS = List.of(
            new ModuleFeatureDefinition(FEATURE_ROOM_BOARD, "병실현황판", "병실현황판 조회 및 관리"),
            new ModuleFeatureDefinition(FEATURE_COUNSEL_AUDIO, "녹음", "상담 녹음 및 음성 파일 업로드"),
            new ModuleFeatureDefinition(FEATURE_ADMISSION_PLEDGE, "서약서", "입원서약서 작성 및 서명"),
            new ModuleFeatureDefinition(FEATURE_COUNSEL_FILE, "파일 업로드", "상담 파일 업로드 및 추출"));
    private static final Set<String> FEATURE_CODES = FEATURE_DEFINITIONS.stream()
            .map(ModuleFeatureDefinition::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final JdbcTemplate jdbcTemplate;

    public void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.module_feature_setting (
                    inst_code varchar(20) not null,
                    feature_code varchar(50) not null,
                    enabled_yn char(1) not null default 'Y',
                    updated_at timestamp default current_timestamp on update current_timestamp,
                    updated_by varchar(100) default null,
                    primary key (inst_code, feature_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    public List<ModuleFeatureDefinition> getFeatureDefinitions() {
        ensureTable();
        return FEATURE_DEFINITIONS;
    }

    public Map<String, Boolean> getFeatureStateMap(String instCode) {
        ensureTable();
        String safeInst = sanitizeInstCode(instCode);
        ensureDefaultRows(safeInst);

        Map<String, Boolean> states = defaultFeatureStateMap();
        jdbcTemplate.query(
                """
                        SELECT feature_code, enabled_yn
                          FROM csm.module_feature_setting
                         WHERE inst_code = ?
                        """,
                (rs, rowNum) -> {
                    states.put(rs.getString("feature_code"), "Y".equalsIgnoreCase(rs.getString("enabled_yn")));
                    return null;
                },
                safeInst);
        return states;
    }

    public boolean isEnabled(String instCode, String featureCode) {
        if (instCode == null || instCode.isBlank()) {
            return false;
        }
        String safeFeature = sanitizeFeatureCode(featureCode);
        return Boolean.TRUE.equals(getFeatureStateMap(instCode).getOrDefault(safeFeature, Boolean.TRUE));
    }

    public List<ModuleFeatureRow> getFeatureRows(List<Instdata> institutions) {
        ensureTable();
        return institutions == null ? List.of() : institutions.stream()
                .filter(Objects::nonNull)
                .filter(inst -> inst.getId_col_03() != null && !inst.getId_col_03().isBlank())
                .filter(inst -> !"core".equalsIgnoreCase(inst.getId_col_03()))
                .map(inst -> {
                    ModuleFeatureRow row = new ModuleFeatureRow();
                    row.setInstCode(inst.getId_col_03());
                    row.setInstName(inst.getId_col_02());
                    row.setFeatureStates(getFeatureStateMap(inst.getId_col_03()));
                    return row;
                })
                .toList();
    }

    @Transactional
    public void updateFeature(String instCode, String featureCode, boolean enabled, String updatedBy) {
        ensureTable();
        String safeInst = sanitizeInstCode(instCode);
        String safeFeature = sanitizeFeatureCode(featureCode);
        ensureDefaultRows(safeInst);
        jdbcTemplate.update(
                """
                        UPDATE csm.module_feature_setting
                           SET enabled_yn = ?, updated_by = ?
                         WHERE inst_code = ? AND feature_code = ?
                        """,
                enabled ? "Y" : "N",
                safeString(updatedBy),
                safeInst,
                safeFeature);
    }

    private void ensureDefaultRows(String instCode) {
        for (ModuleFeatureDefinition feature : FEATURE_DEFINITIONS) {
            jdbcTemplate.update(
                    """
                            INSERT IGNORE INTO csm.module_feature_setting (
                                inst_code, feature_code, enabled_yn, updated_by
                            ) VALUES (?, ?, 'Y', 'system')
                            """,
                    instCode,
                    feature.getCode());
        }
    }

    private Map<String, Boolean> defaultFeatureStateMap() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (ModuleFeatureDefinition feature : FEATURE_DEFINITIONS) {
            states.put(feature.getCode(), Boolean.TRUE);
        }
        return states;
    }

    private String sanitizeInstCode(String instCode) {
        String value = safeString(instCode).trim();
        if (!value.matches("[A-Za-z0-9_]{2,20}")) {
            throw new IllegalArgumentException("기관코드 형식이 올바르지 않습니다.");
        }
        return value;
    }

    private String sanitizeFeatureCode(String featureCode) {
        String value = safeString(featureCode).trim().toUpperCase();
        if (!FEATURE_CODES.contains(value)) {
            throw new IllegalArgumentException("지원하지 않는 기능 코드입니다.");
        }
        return value;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
