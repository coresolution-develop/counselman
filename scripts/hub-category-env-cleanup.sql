-- ============================================================
-- 링크 허브 — 분류명에 섞인 환경을 env 컬럼으로 분리
--
-- 목적: "조이랜드 실서버 / 조이랜드 개발서버"처럼 환경이 분류명에 들어가 있는 것을
--       분류 하나(조이랜드) + env 컬럼으로 정리한다.
--
-- 실행: DBeaver에서 위에서부터 한 단계씩. 1~2단계 결과를 눈으로 확인한 뒤 3단계로.
-- 대상: dev 먼저. prod는 dev에서 확인한 뒤 같은 순서로 따로 실행한다.
-- ============================================================


-- ── 0. 사전 확인 ──────────────────────────────────────────────
-- env 컬럼이 있어야 한다. 앱이 뜰 때 자동 추가되므로, 0이 나오면
-- /csm/links 를 한 번 열어 앱을 깨운 뒤 다시 확인한다.
SELECT COUNT(*) AS env_column_exists
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = 'csm'
   AND TABLE_NAME   = 'company_link'
   AND COLUMN_NAME  = 'env';
-- 기대값: 1


-- ── 1. 백업 ──────────────────────────────────────────────────
-- 되돌릴 일이 생기면 이 표에서 복원한다. 날짜는 실행일로 바꿔 쓴다.
CREATE TABLE csm.company_link_bak_20260816
    AS SELECT * FROM csm.company_link;

CREATE TABLE csm.company_link_category_bak_20260816
    AS SELECT * FROM csm.company_link_category;


-- ── 2. 현재 상태 확인 (여기서 분류명을 실제 값과 대조한다) ──────
-- 아래 3단계의 분류명은 화면을 보고 적은 것이다.
-- 이 결과에 나오는 문자열과 정확히 같은지 확인하고, 다르면 3단계 문자열을 고친다.
-- (앞뒤 공백이나 전각 문자가 섞여 있으면 UPDATE가 0건으로 끝난다)
SELECT category,
       COALESCE(env, '(자동판정)') AS env,
       COUNT(*)                    AS cnt
  FROM csm.company_link
 WHERE use_yn = 'Y'
 GROUP BY category, env
 ORDER BY category;

-- 바뀔 링크만 따로 본다
SELECT id, category, COALESCE(env, '(자동판정)') AS env, title, url
  FROM csm.company_link
 WHERE use_yn = 'Y'
   AND category IN ('조이랜드 실서버', '조이랜드 개발서버', 'ATS 군산 DEMO')
 ORDER BY category, sort_order, id;
-- 기대: 조이랜드 실서버 4건 + 조이랜드 개발서버 4건 + ATS 군산 DEMO 7건 = 15건


-- ── 3. 분류 병합 + 환경 지정 ──────────────────────────────────
UPDATE csm.company_link
   SET category = '조이랜드',
       env      = 'prod'
 WHERE use_yn = 'Y' AND category = '조이랜드 실서버';

UPDATE csm.company_link
   SET category = '조이랜드',
       env      = 'dev'
 WHERE use_yn = 'Y' AND category = '조이랜드 개발서버';

-- ATS는 분류명에서 DEMO를 떼고 환경으로 옮긴다.
UPDATE csm.company_link
   SET category = 'ATS 군산',
       env      = 'demo'
 WHERE use_yn = 'Y' AND category = 'ATS 군산 DEMO';


-- ── 4. 분류 메타(순서·색상) 정리 ──────────────────────────────
-- 새 분류가 예전 분류의 정렬 순서를 물려받게 한다. 이미 있으면 건드리지 않는다.
INSERT INTO csm.company_link_category (category_name, sort_order)
SELECT '조이랜드', COALESCE(MIN(sort_order), 9999)
  FROM (SELECT sort_order
          FROM csm.company_link_category
         WHERE category_name IN ('조이랜드 실서버', '조이랜드 개발서버')) t
ON DUPLICATE KEY UPDATE sort_order = sort_order;

INSERT INTO csm.company_link_category (category_name, sort_order)
SELECT 'ATS 군산', COALESCE(MIN(sort_order), 9999)
  FROM (SELECT sort_order
          FROM csm.company_link_category
         WHERE category_name = 'ATS 군산 DEMO') t
ON DUPLICATE KEY UPDATE sort_order = sort_order;

-- 링크가 하나도 남지 않은 예전 분류 메타를 지운다.
DELETE FROM csm.company_link_category
 WHERE category_name IN ('조이랜드 실서버', '조이랜드 개발서버', 'ATS 군산 DEMO');


-- ── 5. (선택) 나머지 링크의 환경도 확정 ────────────────────────
-- 지금은 env가 비어 있으면 이름·host로 자동 판정한다. 그대로 둬도 동작하지만,
-- 값을 박아두면 나중에 URL이 바뀌어도 표시가 흔들리지 않는다.
--
-- 먼저 무엇이 어떻게 바뀔지 본다.
SELECT id, category, title, url,
       CASE
           WHEN UPPER(title) LIKE '%DEMO%' THEN 'demo'
           WHEN url LIKE '%://dev.%'       THEN 'dev'
           WHEN url LIKE '%-dev.%'         THEN 'dev'
           WHEN url LIKE '%.dev.%'         THEN 'dev'
           ELSE 'prod'
       END AS env_to_set
  FROM csm.company_link
 WHERE use_yn = 'Y'
   AND (env IS NULL OR env = '')
 ORDER BY env_to_set DESC, category, id;

-- 위 결과가 맞으면 실행한다.
-- 주의: LIKE '%-dev.%' 는 host가 아니라 URL 전체를 본다.
--       경로에 "-dev."가 들어간 운영 링크가 있으면 위 SELECT에서 걸러내고,
--       그런 링크는 WHERE 에 AND id NOT IN (...) 로 빼고 실행한다.
UPDATE csm.company_link
   SET env = CASE
           WHEN UPPER(title) LIKE '%DEMO%' THEN 'demo'
           WHEN url LIKE '%://dev.%'       THEN 'dev'
           WHEN url LIKE '%-dev.%'         THEN 'dev'
           WHEN url LIKE '%.dev.%'         THEN 'dev'
           ELSE 'prod'
       END
 WHERE use_yn = 'Y'
   AND (env IS NULL OR env = '');


-- ── 6. 결과 확인 ─────────────────────────────────────────────
SELECT category,
       COALESCE(env, '(자동판정)') AS env,
       COUNT(*)                    AS cnt
  FROM csm.company_link
 WHERE use_yn = 'Y'
 GROUP BY category, env
 ORDER BY category, env;
-- 기대: '조이랜드 실서버' / '조이랜드 개발서버' / 'ATS 군산 DEMO' 가 사라지고
--       '조이랜드'(prod 4 + dev 4), 'ATS 군산'(demo 7) 이 보인다.

-- 분류 메타에 고아 행이 남았는지 확인 (0건이어야 한다)
SELECT c.category_name
  FROM csm.company_link_category c
  LEFT JOIN (SELECT DISTINCT category FROM csm.company_link WHERE use_yn = 'Y') l
         ON l.category = c.category_name
 WHERE l.category IS NULL;

-- 화면 확인: https://dev.sosyge.net/csm/links
--   사이드바 분류 12개 → 11개, "조이랜드" 하나에 운영·개발이 함께 보인다.


-- ── 되돌리기 ─────────────────────────────────────────────────
-- 문제가 있으면 백업에서 복원한다.
--
-- UPDATE csm.company_link t
--   JOIN csm.company_link_bak_20260816 b ON b.id = t.id
--    SET t.category = b.category, t.env = b.env;
--
-- DELETE FROM csm.company_link_category;
-- INSERT INTO csm.company_link_category SELECT * FROM csm.company_link_category_bak_20260816;
--
-- 정리가 끝나고 문제없으면 백업 표를 지운다.
-- DROP TABLE csm.company_link_bak_20260816;
-- DROP TABLE csm.company_link_category_bak_20260816;
