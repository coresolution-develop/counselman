-- ============================================================================
-- [PROD ONLY]  counsel_data 컬럼 드리프트 수정  —  host: csm.sosyge.net
-- ============================================================================
-- 이 파일은 PROD DB 에만 실행하세요.
--   mysql -h csm.sosyge.net -u csdev -p --skip-ssl csm < scripts/fix-counsel-column-drift-prod.sql
--
-- 원인: 기관별 counsel_data_* 테이블 컬럼이 표준 정의와 어긋나 INSERT 거부 → 저장 실패.
--       앱 코드는 정상. 컬럼만 표준으로 맞추면 해결되며, 앱 재시작/재배포 불필요.
-- 안전: 전부 확장/완화(폭 증가, NOT NULL→NULL, binary→varbinary 무손실). 절단·손실 없음.
--       DDL 은 롤백 불가 → 적용 전 대상 테이블 백업 권장.
-- 주의: HSFH/HSJH 는 ~2만 행 → varchar→longtext/길이변경 시 테이블 리빌드(수 초).
--       저트래픽 시간대 실행 권장.
--
-- 대상/변경 (확인된 현재 → 표준):
--   HSFH/HSJH  cs_col_32      varchar(500)      → longtext        (긴 상담내용 저장 실패 해결)
--   HSFH/HSJH  cs_col_01_hash binary(32) NOT NULL → varbinary(32) NULL (환자명 공백 저장 실패 해결)
--   HSFH/HSJH  cs_col_21      varchar(10)       → varchar(100)
--   COHS       cs_col_21      varchar(10)       → varchar(100)
--   COHS       cs_col_23      varchar(50)       → varchar(500)
--   * prod 보호자 해시(binary(32)) 와 prod COHS.cs_col_01_hash(binary(32) NULL) 는
--     정상 동작 → 변경하지 않음.
-- ============================================================================

-- HSFH
ALTER TABLE csm.counsel_data_HSFH MODIFY COLUMN cs_col_32      longtext      DEFAULT NULL;
ALTER TABLE csm.counsel_data_HSFH MODIFY COLUMN cs_col_01_hash varbinary(32) DEFAULT NULL;
ALTER TABLE csm.counsel_data_HSFH MODIFY COLUMN cs_col_21      varchar(100)  DEFAULT NULL;

-- HSJH
ALTER TABLE csm.counsel_data_HSJH MODIFY COLUMN cs_col_32      longtext      DEFAULT NULL;
ALTER TABLE csm.counsel_data_HSJH MODIFY COLUMN cs_col_01_hash varbinary(32) DEFAULT NULL;
ALTER TABLE csm.counsel_data_HSJH MODIFY COLUMN cs_col_21      varchar(100)  DEFAULT NULL;

-- COHS
ALTER TABLE csm.counsel_data_COHS MODIFY COLUMN cs_col_21 varchar(100) DEFAULT NULL;
ALTER TABLE csm.counsel_data_COHS MODIFY COLUMN cs_col_23 varchar(500) DEFAULT NULL;

-- 검증: HSFH/HSJH 는 cs_col_32=longtext, cs_col_01_hash=varbinary(32)/NULL 허용,
--       cs_col_21=varchar(100); COHS 는 cs_col_21=varchar(100), cs_col_23=varchar(500)
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.columns
--  WHERE TABLE_SCHEMA='csm' AND TABLE_NAME IN
--        ('counsel_data_HSFH','counsel_data_HSJH','counsel_data_COHS')
--    AND COLUMN_NAME IN ('cs_col_01_hash','cs_col_21','cs_col_23','cs_col_32')
--  ORDER BY TABLE_NAME, COLUMN_NAME;
