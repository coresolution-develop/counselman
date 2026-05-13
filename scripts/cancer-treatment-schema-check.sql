-- cancer-treatment 운영 DB 점검 SQL
-- 대상: 운영 MySQL (csm.sosyge.net:3306/csm 또는 운영 호스트)
-- 사용법: DBA에게 전달하여 운영 DB에서 read-only로 실행
-- 작성일: 2026-05-13

-- ============================================================
-- STEP 1: 현재 어떤 DB/사용자/시간으로 접속했는지 확인
-- ============================================================
SELECT
    DATABASE()             AS current_schema,
    CURRENT_USER()         AS connected_user,
    @@hostname             AS db_hostname,
    @@version              AS db_version,
    NOW()                  AS db_now,
    @@global.time_zone     AS global_tz,
    @@session.time_zone    AS session_tz;

-- ============================================================
-- STEP 2: ct_* 테이블 존재 여부 (있어야 하는 9개)
-- 기대 결과: 9 rows
-- ============================================================
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'ct\\_%'
ORDER BY TABLE_NAME;

-- 누락 테이블 명시적으로 보기 (있으면 누락된 이름이 출력됨)
SELECT expected.t AS missing_table
FROM (
    SELECT 'ct_patient'             AS t UNION ALL
    SELECT 'ct_treatment_type'           UNION ALL
    SELECT 'ct_treatment_option'         UNION ALL
    SELECT 'ct_treatment_status'         UNION ALL
    SELECT 'ct_time_slot'                UNION ALL
    SELECT 'ct_ward'                     UNION ALL
    SELECT 'ct_treatment_room'           UNION ALL
    SELECT 'ct_treatment_room_item'      UNION ALL
    SELECT 'ct_treatment_schedule'
) expected
LEFT JOIN information_schema.TABLES t
  ON t.TABLE_SCHEMA = DATABASE()
 AND t.TABLE_NAME   = expected.t
WHERE t.TABLE_NAME IS NULL;

-- ============================================================
-- STEP 3: 각 테이블 컬럼 스키마 검증
-- (cancer-treatment/src/main/resources/schema.sql 과 대조)
-- 컬럼 이름/타입/NULL 여부 확인
-- ============================================================
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'ct\\_%'
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- ============================================================
-- STEP 4: 인덱스/유니크/외래키 검증
-- ============================================================
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'ct\\_%'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME,
       REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'ct\\_%'
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- ============================================================
-- STEP 5: 기존 데이터 건수 (위험 없음, 단순 SELECT COUNT)
-- 운영에 데이터가 이미 있으면 마이그레이션/시드 시 주의
-- ============================================================
SELECT 'ct_patient'             AS t, COUNT(*) AS c FROM ct_patient             UNION ALL
SELECT 'ct_treatment_type',         COUNT(*) FROM ct_treatment_type             UNION ALL
SELECT 'ct_treatment_option',       COUNT(*) FROM ct_treatment_option           UNION ALL
SELECT 'ct_treatment_status',       COUNT(*) FROM ct_treatment_status           UNION ALL
SELECT 'ct_time_slot',              COUNT(*) FROM ct_time_slot                  UNION ALL
SELECT 'ct_ward',                   COUNT(*) FROM ct_ward                       UNION ALL
SELECT 'ct_treatment_room',         COUNT(*) FROM ct_treatment_room             UNION ALL
SELECT 'ct_treatment_room_item',    COUNT(*) FROM ct_treatment_room_item        UNION ALL
SELECT 'ct_treatment_schedule',     COUNT(*) FROM ct_treatment_schedule;

-- ============================================================
-- STEP 6: (테이블 누락 시에만) 스키마 적용
-- 적용 스크립트: cancer-treatment/src/main/resources/schema.sql
-- 전부 IF NOT EXISTS 형태라 기존 테이블에는 영향 없음.
-- 사전에 운영 DB 백업 확보 후 실행 권장.
-- ============================================================
-- mysql -h <host> -u <user> -p <db>  <  cancer-treatment/src/main/resources/schema.sql

-- 또는 앱이 기동 시 자동 적용하도록 환경변수로 일회 부팅:
--   CANCER_TREATMENT_SQL_INIT_MODE=always (기본값 never)
--
-- 단, 자동 적용은 ct_treatment_schedule.status_code 의 외래키 제약 없이 정의된
-- 점에 유의 (서비스 레이어에서 status_code 검증). schema.sql 주석 참고.
