-- ══════════════════════════════════════════════════════════════
-- hsop_0001 / HSOP_0001 정리 — 2단계: DROP 문 생성
--
-- **이 스크립트도 아무것도 지우지 않는다.** 실행할 SQL 을 문자열로 출력만 한다.
-- 출력을 눈으로 확인한 뒤 복사해서 실행한다.
--
-- 동적 SQL 로 바로 DROP 하지 않는 이유: 무엇이 지워지는지 보지 못한 채
-- 실행되면 되돌릴 수 없다. **사람이 목록을 보는 단계를 강제한다.**
-- ══════════════════════════════════════════════════════════════

-- ── 0) 접속 대상 재확인 (1단계와 같은 판정) ──
SELECT
  CASE
    WHEN EXISTS (SELECT 1 FROM csm.inst_data_cs WHERE UPPER(id_col_03) IN ('DCHS','SLAH'))
      THEN '⛔ 중단 — prod 다'
    WHEN EXISTS (SELECT 1 FROM csm.inst_data_cs WHERE UPPER(id_col_03) IN ('SLOM','TEST'))
      THEN '✅ dev'
    ELSE '⛔ 중단 — 판정 불가'
  END AS `접속 대상`;

-- ── 1) 백업 명령 (테이블 목록 포함) ──
-- 지우기 전에 반드시 받아 둔다.
SELECT CONCAT(
  'mysqldump -h <host> -u <user> -p csm ',
  GROUP_CONCAT(CONCAT('`', TABLE_NAME, '`') SEPARATOR ' '),
  ' > backup-hsop-$(date +%Y%m%d-%H%M%S).sql'
) AS `-- 1) 먼저 이 백업을 받는다`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'csm'
  AND (TABLE_NAME LIKE '%\_hsop\_0001' OR TABLE_NAME LIKE '%\_HSOP\_0001');

-- ── 2) DROP 문 ──
-- 한 문장으로 묶는다. 부분 실패로 절반만 지워지는 상태를 만들지 않는다.
SELECT CONCAT(
  'DROP TABLE IF EXISTS ',
  GROUP_CONCAT(CONCAT('csm.`', TABLE_NAME, '`') SEPARATOR ',\n  '),
  ';'
) AS `-- 2) 테이블 삭제`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'csm'
  AND (TABLE_NAME LIKE '%\_hsop\_0001' OR TABLE_NAME LIKE '%\_HSOP\_0001');

-- ── 3) 행 삭제 ──
-- **물리 삭제다.** use_yn='N' 으로 두면 mp_institution 에 코드가 남아
-- 다음 동기화가 다시 테이블을 만든다 — 같은 갈림이 재현된다.
SELECT '
DELETE FROM csm.inst_data_cs   WHERE LOWER(id_col_03)  = ''hsop_0001'';
DELETE FROM csm.mp_institution WHERE LOWER(inst_code) = ''hsop_0001'';
' AS `-- 3) 기관 행 삭제`;

-- ── 4) 검증 ──
SELECT '
SELECT COUNT(*) AS `남은 테이블` FROM information_schema.TABLES
 WHERE TABLE_SCHEMA=''csm''
   AND (TABLE_NAME LIKE ''%\\_hsop\\_0001'' OR TABLE_NAME LIKE ''%\\_HSOP\\_0001'');
SELECT COUNT(*) AS `남은 inst_data_cs`   FROM csm.inst_data_cs   WHERE LOWER(id_col_03)=''hsop_0001'';
SELECT COUNT(*) AS `남은 mp_institution` FROM csm.mp_institution WHERE LOWER(inst_code)=''hsop_0001'';
-- 셋 다 0 이어야 한다.
' AS `-- 4) 삭제 후 검증`;
