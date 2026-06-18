-- =====================================================================
-- cancer-treatment 더미 데이터 시드 (개발/테스트 전용)
--
-- 사용: 맨 아래 @inst 값을 본인 기관 코드로 바꾼 뒤, DBeaver에서
--       "Execute SQL Script" (Alt+X) 로 전체 실행.
--       ⚠️ 이 스크립트는 @inst 기관의 기존 데이터를 모두 지우고 더미로 채운다.
--       되돌릴 수 없으니 반드시 테스트/개발 DB에서만 실행할 것.
-- =====================================================================

-- ▼▼▼ 본인 기관 코드로 변경 ▼▼▼
SET @inst := 'CHANGE_ME';
-- ▲▲▲ 본인 기관 코드로 변경 ▲▲▲

START TRANSACTION;

-- ── 1) @inst 데이터 전체 삭제 (FK 의존: 자식 → 부모 순서) ──────────────
DELETE FROM ct_treatment_schedule       WHERE inst_code = @inst;
DELETE FROM ct_schedule_recurrence      WHERE inst_code = @inst;
DELETE FROM ct_patient_prescription_item WHERE patient_id IN (SELECT id FROM ct_patient WHERE inst_code = @inst);
DELETE FROM ct_treatment_package        WHERE inst_code = @inst;
DELETE FROM ct_treatment_room_item      WHERE treatment_room_id IN (SELECT id FROM ct_treatment_room WHERE inst_code = @inst);
DELETE FROM ct_treatment_seat           WHERE inst_code = @inst;
DELETE FROM ct_patient_room             WHERE inst_code = @inst;
DELETE FROM ct_therapist                WHERE inst_code = @inst;
DELETE FROM ct_patient                  WHERE inst_code = @inst;
DELETE FROM ct_treatment_room           WHERE inst_code = @inst;
DELETE FROM ct_treatment_type           WHERE inst_code = @inst;
DELETE FROM ct_treatment_option         WHERE inst_code = @inst;
DELETE FROM ct_treatment_status         WHERE inst_code = @inst;
DELETE FROM ct_time_slot                WHERE inst_code = @inst;
DELETE FROM ct_package_category         WHERE inst_code = @inst;
DELETE FROM ct_ward                     WHERE inst_code = @inst;

-- ── 2) 설정 마스터 ───────────────────────────────────────────────
-- 병동/외래 구분
INSERT INTO ct_ward (inst_code, ward_code, ward_name, admission_type, display_order) VALUES
 (@inst, 'W-3E',  '3병동', 'INPATIENT',  1),
 (@inst, 'W-5W',  '5병동', 'INPATIENT',  2),
 (@inst, 'W-OPD', '외래',  'OUTPATIENT', 3);

-- 시간 슬롯
INSERT INTO ct_time_slot (inst_code, start_time, display_order) VALUES
 (@inst, '09:00', 1), (@inst, '10:00', 2), (@inst, '11:00', 3), (@inst, '14:00', 4);

-- 치료 상태
INSERT INTO ct_treatment_status (inst_code, status_code, status_name, display_order) VALUES
 (@inst, 'RESERVED', '예약', 1), (@inst, 'COMPLETED', '치료완료', 2), (@inst, 'CANCELED', '예약취소', 3);

-- 치료 옵션 (색상)
INSERT INTO ct_treatment_option (inst_code, option_code, option_name, option_color, display_order) VALUES
 (@inst, 'OPT-A', 'A타입', '#1a74bf', 1),
 (@inst, 'OPT-B', 'B타입', '#e0691f', 2);

-- 치료 항목 (소요시간 분)
INSERT INTO ct_treatment_type (inst_code, treatment_name, room_name, duration_minutes, display_order) VALUES
 (@inst, '고주파',   '고주파실',   30, 1),
 (@inst, '도수치료', '도수치료실', 40, 2),
 (@inst, '림프',     '도수치료실', 60, 3);

-- 치료비 카테고리
INSERT INTO ct_package_category (inst_code, category_code, category_name, display_order) VALUES
 (@inst, 'CAT-DS', '도수', 1), (@inst, 'CAT-INJ', '주사', 2);

-- 치료실
INSERT INTO ct_treatment_room (inst_code, management_no, room_name, treatment_item, manager_name, note, display_order) VALUES
 (@inst, 'R-1', '고주파실',   '고주파', '김치료', '', 1),
 (@inst, 'R-2', '도수치료실', '도수',   '이치료', '', 2);

-- 치료사
INSERT INTO ct_therapist (inst_code, therapist_name, display_order) VALUES
 (@inst, '김치료', 1), (@inst, '이치료', 2), (@inst, '박치료', 3);

-- ── 3) 병동/치료실 id 확보 ──────────────────────────────────────
SELECT id INTO @ward_3   FROM ct_ward WHERE inst_code = @inst AND ward_name = '3병동';
SELECT id INTO @ward_5   FROM ct_ward WHERE inst_code = @inst AND ward_name = '5병동';
SELECT id INTO @ward_opd FROM ct_ward WHERE inst_code = @inst AND ward_name = '외래';
SELECT id INTO @room_hf  FROM ct_treatment_room WHERE inst_code = @inst AND room_name = '고주파실';
SELECT id INTO @room_ds  FROM ct_treatment_room WHERE inst_code = @inst AND room_name = '도수치료실';
SELECT id INTO @cat_ds   FROM ct_package_category WHERE inst_code = @inst AND category_name = '도수';
SELECT id INTO @cat_inj  FROM ct_package_category WHERE inst_code = @inst AND category_name = '주사';

-- 병실 마스터 (ward 연결)
INSERT INTO ct_patient_room (inst_code, room_code, room_name, ward_id, display_order) VALUES
 (@inst, '301', '301호',  @ward_3,   1),
 (@inst, '302', '302호',  @ward_3,   2),
 (@inst, '501', '501호',  @ward_5,   3),
 (@inst, 'OPD', '외래실', @ward_opd, 4);

-- 자리(호실) (치료실 하위)
INSERT INTO ct_treatment_seat (inst_code, treatment_room_id, seat_code, seat_name, display_order) VALUES
 (@inst, @room_hf, 'A', 'A자리', 1),
 (@inst, @room_hf, 'B', 'B자리', 2),
 (@inst, @room_ds, '1', '1번',  1),
 (@inst, @room_ds, '2', '2번',  2);

-- 치료비 패키지
INSERT INTO ct_treatment_package (inst_code, category_id, treatment_room_id, package_name, abbreviation, unit_price, billing_unit, frequency, display_order) VALUES
 (@inst, @cat_ds,  @room_ds, '도수 주2회', 'DS2', 50000, 'WEEK', 2, 1),
 (@inst, @cat_inj, @room_hf, '주사 주1회', 'INJ1', 30000, 'WEEK', 1, 2);

-- ── 4) 환자 (주치의/치료시작일/병실 매칭 다양화) ──────────────────
-- room 값: 301=코드매칭, 302호=이름매칭, 501=코드매칭, 외래실=이름매칭, B-99=미등록(진단용)
INSERT INTO ct_patient (inst_code, chart_no, patient_name, room, ward, attending_doctor, admission_date, treatment_start_date, treatment_info, note, prescription_weeks, copayment_rate) VALUES
 (@inst, 'C-001', '홍길동', '301',    '3병동', '김의사', '2026-06-01', '2026-06-02', '고주파 주1', '난소암, 항암중', 4, 100),
 (@inst, 'C-002', '김영희', '302호',  '3병동', '이의사', '2026-06-03', '2026-06-04', '도수 주2',   '',             6, 80),
 (@inst, 'C-003', '박철수', '501',    '5병동', '김의사', '2026-06-05', '2026-06-06', '림프',       '',             4, 100),
 (@inst, 'C-004', '이순신', '외래실', '외래',  '',        '2026-06-02', '2026-06-08', '고주파',     '외래 환자',     8, 100),
 (@inst, 'C-005', '최미등', 'B-99',   '3병동', '박의사', '2026-06-07', '2026-06-09', '도수',       '병실 미등록 케이스', 4, 100);

SELECT id INTO @p_hong FROM ct_patient WHERE inst_code = @inst AND chart_no = 'C-001';
SELECT id INTO @p_kim  FROM ct_patient WHERE inst_code = @inst AND chart_no = 'C-002';
SELECT id INTO @p_park FROM ct_patient WHERE inst_code = @inst AND chart_no = 'C-003';
SELECT id INTO @p_lee  FROM ct_patient WHERE inst_code = @inst AND chart_no = 'C-004';

-- 처방 항목(본인부담 금액·처방 회차 기본값용)
INSERT INTO ct_patient_prescription_item (patient_id, package_id) VALUES
 (@p_hong, (SELECT id FROM ct_treatment_package WHERE inst_code = @inst AND abbreviation = 'INJ1')),
 (@p_kim,  (SELECT id FROM ct_treatment_package WHERE inst_code = @inst AND abbreviation = 'DS2'));

-- ── 5) 자리 id 확보 ─────────────────────────────────────────────
SELECT id INTO @seat_hf_a FROM ct_treatment_seat WHERE inst_code = @inst AND treatment_room_id = @room_hf AND seat_code = 'A';
SELECT id INTO @seat_hf_b FROM ct_treatment_seat WHERE inst_code = @inst AND treatment_room_id = @room_hf AND seat_code = 'B';
SELECT id INTO @seat_ds_1 FROM ct_treatment_seat WHERE inst_code = @inst AND treatment_room_id = @room_ds AND seat_code = '1';
SELECT id INTO @seat_ds_2 FROM ct_treatment_seat WHERE inst_code = @inst AND treatment_room_id = @room_ds AND seat_code = '2';

-- ── 6) 반복 규칙 1건 (홍길동, 월·수·금 = 0b0010101 = 21, 3회) ──────
INSERT INTO ct_schedule_recurrence (inst_code, treatment_room_id, seat_id, patient_id, weekday_mask, start_date, occurrence_count, start_time, treatment_name_snapshot, treatment_option_snapshot, status_code, created_by) VALUES
 (@inst, @room_hf, @seat_hf_a, @p_hong, 21, '2026-06-15', 3, '10:00', '고주파', 'A타입', 'RESERVED', 'seed');
SELECT id INTO @rec FROM ct_schedule_recurrence WHERE inst_code = @inst AND patient_id = @p_hong AND start_date = '2026-06-15';

-- ── 7) 치료 스케줄 (단건 여러 날짜 + 반복 3건 + 상태/자리 다양화) ──────
-- 단건 (이번 주 + 같은 달 다른 주 → 일/주/월 뷰 모두 데이터)
INSERT INTO ct_treatment_schedule (inst_code, patient_id, treatment_room_id, seat_id, recurrence_id, treatment_date, start_time, status_code, ward, patient_name_snapshot, treatment_name_snapshot, treatment_option_snapshot, treatment_info, note) VALUES
 (@inst, @p_hong, @room_hf, @seat_hf_a, NULL, '2026-06-08', '10:00', 'RESERVED',  '3병동', '홍길동', '고주파',   'A타입', '고주파 주1', ''),
 (@inst, @p_kim,  @room_hf, @seat_hf_b, NULL, '2026-06-09', '10:00', 'RESERVED',  '3병동', '김영희', '고주파',   NULL,    '도수 주2',   ''),
 (@inst, @p_park, @room_ds, @seat_ds_1, NULL, '2026-06-09', '11:00', 'COMPLETED', '5병동', '박철수', '도수치료', NULL,    '림프',       ''),
 (@inst, @p_lee,  @room_ds, @seat_ds_2, NULL, '2026-06-10', '14:00', 'RESERVED',  '외래',  '이순신', '림프',     'B타입', '고주파',     '외래'),
 (@inst, @p_hong, @room_hf, @seat_hf_a, NULL, '2026-06-11', '10:00', 'CANCELED',  '3병동', '홍길동', '고주파',   NULL,    '',           '환자 사정'),
 (@inst, @p_kim,  @room_ds, NULL,       NULL, '2026-06-12', '09:00', 'RESERVED',  '3병동', '김영희', '도수치료', NULL,    '',           '자리 미지정 케이스'),
 (@inst, @p_park, @room_hf, @seat_hf_b, NULL, '2026-06-22', '10:00', 'RESERVED',  '5병동', '박철수', '고주파',   NULL,    '',           ''),
 (@inst, @p_lee,  @room_ds, @seat_ds_1, NULL, '2026-06-29', '11:00', 'RESERVED',  '외래',  '이순신', '도수치료', NULL,    '',           '');

-- 반복(홍길동 월·수·금, 06-15 / 06-17 / 06-19) — recurrence_id 연결
INSERT INTO ct_treatment_schedule (inst_code, patient_id, treatment_room_id, seat_id, recurrence_id, treatment_date, start_time, status_code, ward, patient_name_snapshot, treatment_name_snapshot, treatment_option_snapshot) VALUES
 (@inst, @p_hong, @room_hf, @seat_hf_a, @rec, '2026-06-15', '10:00', 'RESERVED', '3병동', '홍길동', '고주파', 'A타입'),
 (@inst, @p_hong, @room_hf, @seat_hf_a, @rec, '2026-06-17', '10:00', 'RESERVED', '3병동', '홍길동', '고주파', 'A타입'),
 (@inst, @p_hong, @room_hf, @seat_hf_a, @rec, '2026-06-19', '10:00', 'RESERVED', '3병동', '홍길동', '고주파', 'A타입');

COMMIT;

-- ── 확인용 (선택) ───────────────────────────────────────────────
-- SELECT (SELECT COUNT(*) FROM ct_patient WHERE inst_code=@inst) AS patients,
--        (SELECT COUNT(*) FROM ct_treatment_schedule WHERE inst_code=@inst) AS schedules,
--        (SELECT COUNT(*) FROM ct_patient_room WHERE inst_code=@inst) AS rooms;
