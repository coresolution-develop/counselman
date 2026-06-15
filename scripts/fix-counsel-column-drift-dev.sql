-- ============================================================================
-- [DEV ONLY]  counsel_data 컬럼 드리프트 수정  —  host: 49.247.42.59
-- ============================================================================
-- 이 파일은 DEV DB 에만 실행하세요.
--   mysql -h 49.247.42.59 -u csdev -p --skip-ssl csm < scripts/fix-counsel-column-drift-dev.sql
--
-- 원인: 기관별 counsel_data_* 테이블 컬럼이 표준 정의와 어긋나 INSERT 거부 → 저장 실패.
--       앱 코드는 정상. 컬럼만 표준으로 맞추면 해결되며, 앱 재시작 불필요.
-- 대상: COHS (dev 에서 드리프트된 유일 기관)
-- 안전: 전부 확장/완화(폭 증가, char→varbinary). 해시 컬럼 값은 전부 NULL 확인됨 → 무손실.
--       DDL 은 롤백 불가 → 적용 전 백업 권장.
-- 확인된 현재 정의 → 표준:
--   cs_col_01_hash       char(64)     → varbinary(32)
--   cs_col_21            varchar(10)  → varchar(100)
--   cs_col_32            varchar(500) → longtext
--   guardians.name_hash           char(64) → varbinary(32)
--   guardians.contact_number_hash char(64) → varbinary(32)
--   (cs_col_23 은 dev COHS 에서 이미 varchar(500) → 변경 없음)
-- ============================================================================

ALTER TABLE csm.counsel_data_COHS           MODIFY COLUMN cs_col_01_hash      varbinary(32) DEFAULT NULL;
ALTER TABLE csm.counsel_data_COHS           MODIFY COLUMN cs_col_21           varchar(100)  DEFAULT NULL;
ALTER TABLE csm.counsel_data_COHS           MODIFY COLUMN cs_col_32           longtext      DEFAULT NULL;
ALTER TABLE csm.counsel_data_COHS_guardians MODIFY COLUMN name_hash           varbinary(32) DEFAULT NULL;
ALTER TABLE csm.counsel_data_COHS_guardians MODIFY COLUMN contact_number_hash varbinary(32) DEFAULT NULL;

-- 검증: 아래가 모두 varbinary(32)/varchar(100)/longtext 여야 함
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.columns
--  WHERE TABLE_SCHEMA='csm' AND TABLE_NAME='counsel_data_COHS'
--    AND COLUMN_NAME IN ('cs_col_01_hash','cs_col_21','cs_col_23','cs_col_32');
-- SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.columns
--  WHERE TABLE_SCHEMA='csm' AND TABLE_NAME='counsel_data_COHS_guardians'
--    AND COLUMN_NAME IN ('name_hash','contact_number_hash');
