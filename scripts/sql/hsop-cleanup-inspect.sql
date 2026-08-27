-- ══════════════════════════════════════════════════════════════
-- hsop_0001 / HSOP_0001 정리 — 1단계: 조사만 한다
--
-- **아무것도 지우지 않는다.** 목록과 행 수를 출력하고 끝난다.
--
-- 배경: csm 은 mp_institution 을 읽을 때 normalizeInstCode() 로 대문자화하는데,
-- inst_data_cs 는 소문자로 남아 있었다. 두 경로가 되먹임 고리를 만들었다.
--
--   ① upsertCoreInstitution  WHERE LOWER(id_col_03)=LOWER(?) 로 찾고
--                            id_col_03 은 갱신하지 않는다 → 소문자가 영구히 남음
--   ② migrateLocalInstitutions  inst_data_cs 의 소문자 원본을 정규화 없이
--                            createCoreInstSchemaTables() 에 넘긴다 → 소문자 테이블 생성
--
-- 결과: 같은 기관의 테이블이 두 표기로 갈라졌다.
-- ══════════════════════════════════════════════════════════════

-- ── 0) 접속 대상이 dev 인가 ──
-- **접속 문자열을 믿지 않는다.** DB 내용으로 판정한다.
--   dev 에만 있는 기관: SLOM, TEST
--   prod 에만 있는 기관: DCHS, SLAH
SELECT
  CASE
    WHEN EXISTS (SELECT 1 FROM csm.inst_data_cs WHERE UPPER(id_col_03) IN ('DCHS','SLAH'))
      THEN '⛔ 중단 — prod 기관(DCHS/SLAH)이 있다. 여기는 운영 DB 다.'
    WHEN EXISTS (SELECT 1 FROM csm.inst_data_cs WHERE UPPER(id_col_03) IN ('SLOM','TEST'))
      THEN '✅ dev 로 판정됨 — 계속 진행 가능'
    ELSE '⛔ 중단 — dev 도 prod 도 아니다. 접속 대상을 확인하라.'
  END AS `접속 대상 판정`;

-- ── 1) 관련 행 ──
SELECT 'inst_data_cs'   AS `테이블`, id_col_03 AS `코드`, id_col_02 AS `이름`, id_col_04 AS `use_yn`
  FROM csm.inst_data_cs   WHERE LOWER(id_col_03)  = 'hsop_0001'
UNION ALL
SELECT 'mp_institution', inst_code, inst_name, COALESCE(use_yn,'Y')
  FROM csm.mp_institution WHERE LOWER(inst_code) = 'hsop_0001';

-- ── 2) 관련 테이블 전수 + 행 수 ──
-- TABLE_ROWS 는 InnoDB 에서 추정치다. **정확한 값이 필요하면 아래 3번을 쓴다.**
SELECT
  TABLE_NAME                                    AS `테이블`,
  CASE WHEN TABLE_NAME LIKE BINARY '%hsop\_0001'
       THEN '소문자' ELSE '대문자' END           AS `표기`,
  TABLE_ROWS                                    AS `행수(추정)`,
  ROUND((DATA_LENGTH + INDEX_LENGTH)/1024)      AS `크기KB`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'csm'
  AND (TABLE_NAME LIKE '%\_hsop\_0001' OR TABLE_NAME LIKE '%\_HSOP\_0001')
ORDER BY `표기`, TABLE_NAME;

-- ── 3) 정확한 행 수를 세는 SELECT 문 생성 ──
-- 출력된 SQL 을 복사해 실행하면 실제 행 수가 나온다.
-- TABLE_ROWS 추정치로 "비어 있다" 고 판단하면 안 된다.
SELECT GROUP_CONCAT(
         CONCAT('SELECT ''', TABLE_NAME, ''' AS t, COUNT(*) AS n FROM csm.`', TABLE_NAME, '`')
         SEPARATOR '\nUNION ALL '
       ) AS `-- 복사해서 실행할 행수 확인 SQL`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'csm'
  AND (TABLE_NAME LIKE '%\_hsop\_0001' OR TABLE_NAME LIKE '%\_HSOP\_0001');

-- ── 4) 한쪽 표기에만 있는 테이블 ──
-- 기능마다 어느 표기로 만들어졌는지가 갈렸다는 증거다.
SELECT
  base                                          AS `테이블 종류`,
  MAX(CASE WHEN lower_case THEN 'O' ELSE '' END) AS `소문자`,
  MAX(CASE WHEN NOT lower_case THEN 'O' ELSE '' END) AS `대문자`
FROM (
  SELECT
    LEFT(TABLE_NAME, LENGTH(TABLE_NAME) - LENGTH('_hsop_0001')) AS base,
    (TABLE_NAME LIKE BINARY '%hsop\_0001')                       AS lower_case
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = 'csm'
    AND (TABLE_NAME LIKE '%\_hsop\_0001' OR TABLE_NAME LIKE '%\_HSOP\_0001')
) x
GROUP BY base
HAVING `소문자` = '' OR `대문자` = ''
ORDER BY base;
