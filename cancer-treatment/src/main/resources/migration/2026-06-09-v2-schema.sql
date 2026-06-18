-- =====================================================================
-- cancer-treatment v2 스키마 마이그레이션 (2026-06-09)
--
-- 배경: schema.sql 은 `CREATE TABLE IF NOT EXISTS` 라 기존 테이블에 컬럼을
--       추가하지 못하고, spring.sql.init.mode=embedded 라 MySQL 에서는
--       schema.sql 이 실행되지 않는다. 따라서 v2 에서 추가된 컬럼/테이블은
--       기존 MySQL 스키마에 자동 반영되지 않는다.
--
-- 사용: 기존(v1) cancer-treatment MySQL 스키마에 대해 "한 번" 실행한다.
--       ADD COLUMN/ADD CONSTRAINT 는 MySQL 에서 IF NOT EXISTS 를 지원하지
--       않으므로, v2 미적용 스키마에서만 그대로 실행할 것. (운영 적용 전
--       스테이징 사본에서 먼저 검증 권장)
-- =====================================================================

-- 1) ct_patient: 주치의 + 치료시작일 추가
ALTER TABLE ct_patient ADD COLUMN attending_doctor VARCHAR(100) AFTER ward;
ALTER TABLE ct_patient ADD COLUMN treatment_start_date DATE AFTER admission_date;
-- (선택) 퇴원일 제거: 애플리케이션은 더 이상 discharge_date 를 사용하지 않는다.
--        데이터 보존이 불필요하면 아래 주석을 해제해 제거한다.
-- ALTER TABLE ct_patient DROP COLUMN discharge_date;

-- 2) ct_treatment_type: 치료별 소요시간(분)
ALTER TABLE ct_treatment_type ADD COLUMN duration_minutes INT AFTER room_name;

-- 3) ct_ward: 입원/외래 구분
ALTER TABLE ct_ward ADD COLUMN admission_type VARCHAR(10) NOT NULL DEFAULT 'INPATIENT' AFTER ward_name;
-- 기존 '외래' 병동을 OUTPATIENT 로 보정
UPDATE ct_ward SET admission_type = 'OUTPATIENT' WHERE ward_name LIKE '%외래%';

-- 4) 병실 마스터 (FK -> ct_ward)
CREATE TABLE IF NOT EXISTS ct_patient_room (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    room_code VARCHAR(50) NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    ward_id BIGINT,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_patient_room_code (inst_code, room_code),
    INDEX idx_ct_patient_room_order (inst_code, active_yn, display_order),
    INDEX idx_ct_patient_room_ward (ward_id),
    CONSTRAINT fk_ct_patient_room_ward FOREIGN KEY (ward_id) REFERENCES ct_ward (id)
);

-- 5) 자리/베드 마스터 (FK -> ct_treatment_room)
CREATE TABLE IF NOT EXISTS ct_treatment_seat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    treatment_room_id BIGINT NOT NULL,
    seat_code VARCHAR(50) NOT NULL,
    seat_name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_treatment_seat_code (treatment_room_id, seat_code),
    INDEX idx_ct_treatment_seat_order (treatment_room_id, active_yn, display_order),
    CONSTRAINT fk_ct_treatment_seat_room FOREIGN KEY (treatment_room_id) REFERENCES ct_treatment_room (id)
);

-- 6) 치료사 명부
CREATE TABLE IF NOT EXISTS ct_therapist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    therapist_name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_therapist_name (inst_code, therapist_name),
    INDEX idx_ct_therapist_order (inst_code, active_yn, display_order)
);

-- 7) 반복 일정 규칙 (FK -> ct_treatment_room, ct_treatment_seat, ct_patient)
--    seat/room/patient 가 먼저 존재해야 하므로 이 순서를 지킨다.
CREATE TABLE IF NOT EXISTS ct_schedule_recurrence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inst_code VARCHAR(50) NOT NULL,
    treatment_room_id BIGINT,
    seat_id BIGINT,
    patient_id BIGINT,
    weekday_mask INT NOT NULL,
    start_date DATE NOT NULL,
    occurrence_count INT NOT NULL,
    start_time TIME NOT NULL,
    treatment_name_snapshot VARCHAR(100) NOT NULL,
    treatment_option_snapshot VARCHAR(100),
    status_code VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    treatment_info TEXT,
    note TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ct_recurrence_inst (inst_code, start_date),
    INDEX idx_ct_recurrence_room (treatment_room_id),
    CONSTRAINT fk_ct_recurrence_room FOREIGN KEY (treatment_room_id) REFERENCES ct_treatment_room (id),
    CONSTRAINT fk_ct_recurrence_seat FOREIGN KEY (seat_id) REFERENCES ct_treatment_seat (id),
    CONSTRAINT fk_ct_recurrence_patient FOREIGN KEY (patient_id) REFERENCES ct_patient (id)
);

-- 8) ct_treatment_schedule: room/seat/recurrence 컬럼 + 인덱스 + FK
--    (recurrence_id FK 는 ct_schedule_recurrence 가 먼저 생성된 뒤여야 한다 — 7 이후)
--    변경 1개당 ALTER 1문으로 분리(부분 실패 진단 용이).
ALTER TABLE ct_treatment_schedule ADD COLUMN treatment_room_id BIGINT AFTER patient_id;
ALTER TABLE ct_treatment_schedule ADD COLUMN seat_id BIGINT AFTER treatment_room_id;
ALTER TABLE ct_treatment_schedule ADD COLUMN recurrence_id BIGINT AFTER seat_id;

ALTER TABLE ct_treatment_schedule ADD INDEX idx_ct_schedule_room (treatment_room_id, treatment_date, start_time);
ALTER TABLE ct_treatment_schedule ADD INDEX idx_ct_schedule_recurrence (recurrence_id);

ALTER TABLE ct_treatment_schedule ADD CONSTRAINT fk_ct_schedule_room FOREIGN KEY (treatment_room_id) REFERENCES ct_treatment_room (id);
ALTER TABLE ct_treatment_schedule ADD CONSTRAINT fk_ct_schedule_seat FOREIGN KEY (seat_id) REFERENCES ct_treatment_seat (id);
ALTER TABLE ct_treatment_schedule ADD CONSTRAINT fk_ct_schedule_recurrence FOREIGN KEY (recurrence_id) REFERENCES ct_schedule_recurrence (id);
