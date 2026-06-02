CREATE TABLE IF NOT EXISTS ct_patient (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    chart_no VARCHAR(50),
    patient_name VARCHAR(100) NOT NULL,
    room VARCHAR(50),
    ward VARCHAR(50),
    admission_date DATE,
    discharge_date DATE,
    treatment_info TEXT,
    note TEXT,
    prescription_weeks INT NOT NULL DEFAULT 0,
    copayment_rate INT NOT NULL DEFAULT 100,
    total_discount_type VARCHAR(10) NOT NULL DEFAULT 'NONE',
    total_discount_value INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ct_patient_inst_active (inst_code, active_yn),
    INDEX idx_ct_patient_inst_name (inst_code, patient_name),
    INDEX idx_ct_patient_inst_chart (inst_code, chart_no)
);

CREATE TABLE IF NOT EXISTS ct_treatment_type (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    treatment_name VARCHAR(100) NOT NULL,
    room_name VARCHAR(100),
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_treatment_type_name (inst_code, treatment_name),
    INDEX idx_ct_treatment_type_order (inst_code, active_yn, display_order)
);

CREATE TABLE IF NOT EXISTS ct_treatment_option (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    option_code VARCHAR(50) NOT NULL,
    option_name VARCHAR(100) NOT NULL,
    option_color VARCHAR(20) NOT NULL DEFAULT '#1a74bf',
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_treatment_option_code (inst_code, option_code),
    INDEX idx_ct_treatment_option_order (inst_code, active_yn, display_order)
);

CREATE TABLE IF NOT EXISTS ct_treatment_status (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    status_code VARCHAR(30) NOT NULL,
    status_name VARCHAR(30) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_treatment_status_code (inst_code, status_code),
    INDEX idx_ct_treatment_status_order (inst_code, active_yn, display_order)
);

CREATE TABLE IF NOT EXISTS ct_time_slot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_time_slot_start (inst_code, start_time),
    INDEX idx_ct_time_slot_order (inst_code, active_yn, display_order)
);

CREATE TABLE IF NOT EXISTS ct_ward (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    ward_code VARCHAR(50) NOT NULL,
    ward_name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_ward_code (inst_code, ward_code),
    INDEX idx_ct_ward_order (inst_code, active_yn, display_order)
);

CREATE TABLE IF NOT EXISTS ct_package_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_package_category_code (inst_code, category_code),
    INDEX idx_ct_package_category_order (inst_code, active_yn, display_order)
);

CREATE TABLE IF NOT EXISTS ct_treatment_room (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    management_no VARCHAR(50),
    room_name VARCHAR(100) NOT NULL,
    treatment_item VARCHAR(100),
    manager_name VARCHAR(100),
    note TEXT,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ct_treatment_room_order (inst_code, active_yn, display_order),
    INDEX idx_ct_treatment_room_no (inst_code, management_no),
    INDEX idx_ct_treatment_room_name (inst_code, room_name)
);

CREATE TABLE IF NOT EXISTS ct_treatment_room_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    treatment_room_id BIGINT NOT NULL,
    treatment_item VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_treatment_room_item (treatment_room_id, treatment_item),
    INDEX idx_ct_treatment_room_item_order (treatment_room_id, active_yn, display_order),
    CONSTRAINT fk_ct_treatment_room_item_room FOREIGN KEY (treatment_room_id) REFERENCES ct_treatment_room (id)
);

CREATE TABLE IF NOT EXISTS ct_treatment_package (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    category_id BIGINT NOT NULL,
    treatment_room_id BIGINT NOT NULL,
    package_name VARCHAR(200) NOT NULL,
    abbreviation VARCHAR(50),
    unit_price INT NOT NULL DEFAULT 0,
    billing_unit VARCHAR(10) NOT NULL DEFAULT 'WEEK',
    frequency INT NOT NULL DEFAULT 1,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_treatment_package_name (inst_code, treatment_room_id, package_name),
    INDEX idx_ct_treatment_package_inst (inst_code, active_yn, display_order),
    INDEX idx_ct_treatment_package_category (category_id),
    INDEX idx_ct_treatment_package_room (treatment_room_id),
    CONSTRAINT fk_ct_treatment_package_category FOREIGN KEY (category_id) REFERENCES ct_package_category (id),
    CONSTRAINT fk_ct_treatment_package_room FOREIGN KEY (treatment_room_id) REFERENCES ct_treatment_room (id)
);

CREATE TABLE IF NOT EXISTS ct_patient_prescription_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_patient_prescription_item (patient_id, package_id),
    INDEX idx_ct_patient_prescription_patient (patient_id),
    CONSTRAINT fk_ct_patient_prescription_patient FOREIGN KEY (patient_id) REFERENCES ct_patient (id),
    CONSTRAINT fk_ct_patient_prescription_package FOREIGN KEY (package_id) REFERENCES ct_treatment_package (id)
);

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
    -- Denormalized snapshots (justified): patient_name preserves a label when the
    -- linked patient is removed; treatment_name/option hold free-typed values that
    -- have no row in the treatment catalog. Live patient data is joined when linked.
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
    INDEX idx_ct_schedule_patient (patient_id, treatment_date),
    CONSTRAINT fk_ct_schedule_patient FOREIGN KEY (patient_id) REFERENCES ct_patient (id),
    CONSTRAINT fk_ct_schedule_type FOREIGN KEY (treatment_type_id) REFERENCES ct_treatment_type (id),
    CONSTRAINT fk_ct_schedule_option FOREIGN KEY (treatment_option_id) REFERENCES ct_treatment_option (id)
);
