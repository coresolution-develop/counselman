-- ══════════════════════════════════════════════════════════════
-- 기관 코드 대소문자 갈림 전수 조사
--
-- hsop_0001 은 비활성이라 실피해가 없었다. **살아 있는 기관에서 같은 일이
-- 벌어졌는지** 확인한다. dev·prod 양쪽에서 돌린다.
--
-- 아무것도 바꾸지 않는다.
-- ══════════════════════════════════════════════════════════════

-- ── 1) 정규화하면 값이 바뀌는 코드 ──
-- 'core' 를 뺀 나머지가 대문자가 아니면 갈릴 여지가 있다.
SELECT
  id_col_03                                    AS `현재 코드`,
  CASE WHEN LOWER(id_col_03) = 'core' THEN 'core'
       ELSE UPPER(id_col_03) END               AS `정규화 결과`,
  id_col_04                                    AS `use_yn`,
  CASE WHEN BINARY id_col_03 <> BINARY (
         CASE WHEN LOWER(id_col_03)='core' THEN 'core' ELSE UPPER(id_col_03) END)
       THEN '⚠️ 갈림' ELSE 'OK' END            AS `판정`
FROM csm.inst_data_cs
ORDER BY `판정` DESC, id_col_03;

-- ── 2) 같은 종류의 테이블이 두 표기로 존재하는 기관 ──
-- **이것이 실제 피해다.** 데이터가 두 곳으로 나뉜다.
SELECT
  suffix_upper                                  AS `기관(대문자 기준)`,
  COUNT(DISTINCT BINARY suffix)                 AS `표기 가짓수`,
  GROUP_CONCAT(DISTINCT BINARY suffix)          AS `실제 표기들`,
  COUNT(*)                                      AS `테이블 수`
FROM (
  SELECT
    TABLE_NAME,
    SUBSTRING_INDEX(TABLE_NAME, '_', -1)              AS last_seg,
    UPPER(SUBSTRING_INDEX(TABLE_NAME, '_', -1))       AS suffix_upper,
    SUBSTRING_INDEX(TABLE_NAME, '_', -1)              AS suffix
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = 'csm'
) x
GROUP BY suffix_upper
HAVING `표기 가짓수` > 1
ORDER BY `테이블 수` DESC;

-- ── 3) mp_institution 원본 vs inst_data_cs ──
-- 두 곳의 표기가 다르면 동기화가 소문자 행을 갱신하고 있다는 뜻이다.
SELECT
  m.inst_code                                   AS `mp_institution`,
  i.id_col_03                                   AS `inst_data_cs`,
  CASE WHEN BINARY m.inst_code <> BINARY i.id_col_03
       THEN '⚠️ 표기 다름' ELSE 'OK' END        AS `판정`
FROM csm.mp_institution m
LEFT JOIN csm.inst_data_cs i ON LOWER(i.id_col_03) = LOWER(m.inst_code)
ORDER BY `판정` DESC, m.inst_code;

-- ── 4) 테이블명 대소문자 구분 여부 ──
-- 0 이면 구분한다 (리눅스 기본). macOS 는 보통 2 라 로컬에서 재현되지 않는다.
SELECT @@lower_case_table_names AS `lower_case_table_names`,
       CASE @@lower_case_table_names
         WHEN 0 THEN '구분함 — 대소문자가 다르면 다른 테이블이다'
         WHEN 1 THEN '전부 소문자로 저장 — 갈림이 발생하지 않는다'
         WHEN 2 THEN '저장은 원본, 비교는 무시 — macOS 기본'
       END AS `의미`;
