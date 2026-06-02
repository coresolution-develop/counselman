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
            ensurePatientPrescriptionColumns();
            ensureTreatmentPackageAbbreviation();
            ensureTreatmentScheduleTable();
            ensureTreatmentScheduleSnapshotColumns();
        }
    }

    /**
     * Guarantees the schedule table on MySQL where schema.sql is not auto-applied
     * (prod runs with spring.sql.init.mode=never). Idempotent via IF NOT EXISTS.
     */
    private void ensureTreatmentScheduleTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ct_treatment_schedule (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    inst_code VARCHAR(50) NOT NULL,
                    patient_id BIGINT,
                    treatment_date DATE NOT NULL,
                    start_time TIME NOT NULL,
                    treatment_type_id BIGINT,
                    treatment_option_id BIGINT,
                    status_code VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
                    ward VARCHAR(50),
                    patient_name_snapshot VARCHAR(100) NOT NULL,
                    treatment_name_snapshot VARCHAR(100) NOT NULL,
                    treatment_option_snapshot VARCHAR(100),
                    treatment_info TEXT,
                    note TEXT,
                    created_by VARCHAR(100),
                    updated_by VARCHAR(100),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    INDEX idx_ct_schedule_daily (inst_code, treatment_date, start_time),
                    INDEX idx_ct_schedule_status (inst_code, status_code, treatment_date),
                    INDEX idx_ct_schedule_patient (patient_id, treatment_date)
                )
                """);
    }

    /** Adds the free-text snapshot columns to a pre-existing schedule table. */
    private void ensureTreatmentScheduleSnapshotColumns() {
        if (!columnExists("ct_treatment_schedule", "treatment_name_snapshot")) {
            jdbcTemplate.execute(
                    "ALTER TABLE ct_treatment_schedule ADD COLUMN treatment_name_snapshot VARCHAR(100) NOT NULL DEFAULT ''");
        }
        if (!columnExists("ct_treatment_schedule", "treatment_option_snapshot")) {
            jdbcTemplate.execute(
                    "ALTER TABLE ct_treatment_schedule ADD COLUMN treatment_option_snapshot VARCHAR(100) NULL");
        }
    }

    private void ensureTreatmentPackageAbbreviation() {
        if (!columnExists("ct_treatment_package", "abbreviation")) {
            jdbcTemplate.execute("ALTER TABLE ct_treatment_package ADD COLUMN abbreviation VARCHAR(50) NULL");
        }
    }

    private void ensurePatientPrescriptionColumns() {
        if (!columnExists("ct_patient", "prescription_weeks")) {
            jdbcTemplate.execute("ALTER TABLE ct_patient ADD COLUMN prescription_weeks INT NOT NULL DEFAULT 0");
        }
        if (!columnExists("ct_patient", "copayment_rate")) {
            jdbcTemplate.execute("ALTER TABLE ct_patient ADD COLUMN copayment_rate INT NOT NULL DEFAULT 100");
        }
        if (!columnExists("ct_patient", "total_discount_type")) {
            jdbcTemplate.execute("ALTER TABLE ct_patient ADD COLUMN total_discount_type VARCHAR(10) NOT NULL DEFAULT 'NONE'");
        }
        if (!columnExists("ct_patient", "total_discount_value")) {
            jdbcTemplate.execute("ALTER TABLE ct_patient ADD COLUMN total_discount_value INT NOT NULL DEFAULT 0");
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
