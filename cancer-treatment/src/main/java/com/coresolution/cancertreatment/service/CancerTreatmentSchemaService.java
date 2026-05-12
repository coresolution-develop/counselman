package com.coresolution.cancertreatment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class CancerTreatmentSchemaService {

    private final JdbcTemplate jdbcTemplate;

    public CancerTreatmentSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        String product = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        if (product != null && product.toLowerCase().contains("mysql")) {
            ensureTreatmentOptionColor();
            relaxTreatmentRoomManagementNo();
            relaxTreatmentRoomTreatmentItem();
        }
    }

    private void ensureTreatmentOptionColor() {
        if (!columnExists("ct_treatment_option", "option_color")) {
            jdbcTemplate.execute("ALTER TABLE ct_treatment_option ADD COLUMN option_color VARCHAR(20) NOT NULL DEFAULT '#1a74bf'");
        }
    }

    private void relaxTreatmentRoomManagementNo() {
        jdbcTemplate.execute("ALTER TABLE ct_treatment_room MODIFY management_no VARCHAR(50) NULL");
        if (indexExists("uk_ct_treatment_room_no")) {
            jdbcTemplate.execute("ALTER TABLE ct_treatment_room DROP INDEX uk_ct_treatment_room_no");
        }
        if (!indexExists("idx_ct_treatment_room_no")) {
            jdbcTemplate.execute("CREATE INDEX idx_ct_treatment_room_no ON ct_treatment_room (inst_code, management_no)");
        }
    }

    private void relaxTreatmentRoomTreatmentItem() {
        jdbcTemplate.execute("ALTER TABLE ct_treatment_room MODIFY treatment_item VARCHAR(100) NULL");
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'ct_treatment_room'
                  AND index_name = ?
                """, Integer.class, indexName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
