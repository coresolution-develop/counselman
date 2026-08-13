# 문자 배치 발송 — 운영 쿼리·체크리스트

> 작성: 2026-08-12 (Phase 1-B)
> 대상: `POST /csm/api/counsel/sms/batch`, `csm.sms_batch`, `csm.transmission_history_<inst>`,
> `csm.v_transmission_history_all`

## 상태 체계

| 상태 | 시점 | 의미 |
|---|---|---|
| `READY` | 이력 INSERT 직후, 비즈뿌리오 호출 전 | 크래시 시 잔재로 남을 수 있음 (아래 관측 쿼리) |
| `SENT` | 접수 성공 | description==success 또는 code==1000 |
| `FAILED` | 접수 실패 | 명시적 실패 |
| `UNKNOWN` | 발송 호출 타임아웃 | **결과 불명. 재시도 금지, 환불 금지.** 과금 집계에 포함 |
| `DONE` | 콜백 최종 성공 | 신규 refkey(MP-) 건만 |
| `ERROR` | 콜백 최종 실패 | 신규 refkey(MP-) 건만 |
| `SUCCESS`/`FAILURE`/`전송완료`/`전송실패`/`전송중` | 구 데이터 | 마이그레이션하지 않음. 조회 필터가 신·구 모두 포함 |

## 운영 관측 쿼리 (DBeaver 복붙 가능)

### READY 24시간 이상 잔재 (크래시 윈도우 관측)

INSERT 후 발송 전에 앱이 죽으면 READY 로 남는다. 복구 로직은 의도적으로 없다 —
주기적으로 이 쿼리로 관측하고, 발견 시 벤더 발송 여부를 확인해 수동 정리한다.

```sql
-- 전 기관 한 번에 (뷰 사용)
SELECT inst_code, id, to_phone, created_at
FROM csm.v_transmission_history_all
WHERE status = 'READY' AND created_at < NOW() - INTERVAL 24 HOUR
ORDER BY created_at;
```

### UNKNOWN 건 관측 (결과 불명 — 콜백/벤더 콘솔로 확정 필요)

```sql
SELECT inst_code, id, refkey, to_phone, created_at
FROM csm.v_transmission_history_all
WHERE status = 'UNKNOWN' AND created_at >= NOW() - INTERVAL 7 DAY
ORDER BY created_at DESC;
```

### 전 기관 월별 발송량·비용 (전 단위 → 원 환산)

```sql
SELECT inst_code,
       DATE_FORMAT(created_at, '%Y-%m')                          AS month,
       send_type,
       COUNT(*)                                                  AS cnt,
       SUM(COALESCE(cost, 0))                                    AS cost_jeon,
       ROUND(SUM(COALESCE(cost, 0)) / 100, 1)                    AS cost_won
FROM csm.v_transmission_history_all
WHERE status IN ('SUCCESS', 'SENT', 'DONE', 'UNKNOWN')
  AND billable = 'Y'
GROUP BY inst_code, DATE_FORMAT(created_at, '%Y-%m'), send_type
ORDER BY month DESC, inst_code, send_type;
```

### 회사 부담분(OTP) 발송량 집계

`billable='N'` 행은 **cost 를 0 으로 기록**한다 — 차감 합산에서 billable 조건이 누락되어도
기관에 청구될 수 없게 하기 위함이다. 비용이 필요하면 조회 시점에 건수 × 단가로 계산한다.

```sql
-- 발송량 (건수 기준)
SELECT DATE_FORMAT(created_at, '%Y-%m') AS month,
       inst_code,
       COUNT(*) AS cnt
FROM csm.v_transmission_history_all
WHERE billable = 'N'
GROUP BY DATE_FORMAT(created_at, '%Y-%m'), inst_code
ORDER BY month DESC, inst_code;

-- 비용 추정이 필요하면 건수에 단가를 곱한다 (OTP 는 전부 SMS, 예: 9.6원)
SELECT DATE_FORMAT(created_at, '%Y-%m') AS month,
       COUNT(*)                AS cnt,
       ROUND(COUNT(*) * 9.6, 1) AS est_cost_won   -- 단가 변경 시 숫자만 교체
FROM csm.v_transmission_history_all
WHERE billable = 'N'
GROUP BY DATE_FORMAT(created_at, '%Y-%m')
ORDER BY month DESC;
```

### 단가 폴백 발생 관측

`inst_data_cs` 단가가 없거나 파싱 불가면 프로퍼티 기본값(960/3000/9000전)으로 폴백하고
WARN 로그에 기관코드가 남는다. 로그 검색 키: `[sms-price]`

```sql
-- 단가 데이터 정합 확인 (비숫자·NULL 검출)
SELECT id_col_03 AS inst, sms_price, lms_price, mms_price
FROM csm.inst_data_cs
WHERE sms_price IS NULL OR lms_price IS NULL OR mms_price IS NULL
   OR sms_price NOT REGEXP '^[0-9]+(\\.[0-9]+)?$'
   OR lms_price NOT REGEXP '^[0-9]+(\\.[0-9]+)?$'
   OR mms_price NOT REGEXP '^[0-9]+(\\.[0-9]+)?$';
```

### 단가 공백 기관 점검 — Phase 4 차감 시작 전 필수

아래 쿼리 결과가 **0건이 되어야 Phase 4 잔액 차감을 시작할 수 있다.** 단가가 비어 있으면
프로퍼티 기본값으로 폴백해 발송은 되지만, 기관이 실제로 계약한 단가와 다른 금액이
차감된다. (2026-08-12 dev 기준 HSFH·HSJH·TEST·SLOM 4개 기관이 공백)

```sql
SELECT id_col_03 AS inst, sms_price, lms_price, mms_price
FROM csm.inst_data_cs
WHERE sms_price IS NULL OR sms_price = ''
   OR lms_price IS NULL OR lms_price = ''
   OR mms_price IS NULL OR mms_price = '';
```

단가 입력은 `/csm/core/smssetting` (플랫폼 관리자 전용) 화면에서 기관별 또는 전 기관 일괄로 한다.

### 단가 → 전(錢) 변환 규칙

금액은 전 단위 정수로만 다룬다(double/float 미사용). `BigDecimal` 파싱 후
`movePointRight(2)` → `setScale(0, RoundingMode.HALF_UP)` → `intValueExact()`.

| 입력값 | 결과 | 비고 |
|---|---|---|
| `9.6` | 960전 | 실서버 SMS 단가 |
| `9` | 900전 | |
| `12` | 1200전 | |
| `110` | 11000전 | |
| `9.65` | 965전 | 전 단위까지 정확 |
| `9.655` | 966전 | 전 미만은 HALF_UP 반올림 |
| `""` / `NULL` / `20원` / 음수 | 폴백 + WARN | 프로퍼티 기본값 사용 |

## 콜백 수동 검증 절차

**개발서버에는 비즈뿌리오 실제 콜백이 오지 않는다** — 결과 리포트 URL이 등록되어 있지 않다
(마지막 실제 콜백 2026-04-03). 따라서 콜백 처리 검증은 아래처럼 직접 주입해서 한다.
코드 문제가 아니므로 개발서버에서 상태가 `SENT` 에 머물러 있어도 정상이다.

```bash
curl -i -X POST http://localhost:8080/csm/api/external/SMSRequest \
  -H "Content-Type: application/json" \
  -d '{"REFKEY":"MP-{INST}-{ID}","RESULT":"4100","PHONE":"01000000000",
       "DEVICE":"SMS","MEDIA":"SMS","CMSGID":"test","MSGID":"test",
       "UNIXTIME":"1755050000"}'
```

- `REFKEY` 는 검증 대상 이력 행의 값으로 교체한다 (예: `MP-FALH-63`).
- **성공 코드는 `4100` 과 `6600` 두 가지다.** 그 외 값은 실패(`ERROR`)로 기록된다.
- 기대 결과: `transmission_history_<inst>.status` 가 `DONE` 으로 갱신되고,
  `sms_request_<inst>` 에 콜백 상세가 1행 기록된다.
- 이 엔드포인트는 CSRF 예외 대상이라 토큰 없이 호출된다 (콜백 수신 전용).

## 서버별 csm 위치

혼선이 잦은 부분이라 명시한다. **포트만 보고 판단하지 말 것.**

| 환경 | 포트 | catalina.base |
|---|---|---|
| 개발서버 | 8080 | `/usr/local/tomcat10` |
| 실서버 | **18081** | `/opt/csm-next/tomcat` |

- 개발서버 8084 는 csm 이 아닌 **별개 앱**이다.
- 실서버 8084 는 **ResvHub** 다.

## 제거 판단용 로그 검색 키

| 검색 키 | 의미 | 2주 무발생 시 |
|---|---|---|
| `[api/external/sendSMS][deprecated]` | 레거시 발송 경로 호출 | `relaySendSms` 메서드 제거 가능 |
| `[api/external/SMSRequest] legacy refkey fallback` | 구형식 refkey 콜백 도착 | 콜백의 구형식 폴백(substring) 제거 가능 |
| `[sms/sendSMS][gone]` | 폐기 엔드포인트 호출 (Task 0-E) | `sendSmsByLegacyContract` 제거 가능 |

## collation 점검 (1-B-0 후속)

이력 테이블은 부트스트랩이 utf8mb4_0900_ai_ci 로 자동 변환한다. **다른 기관별 테이블**의
분포는 아래로 확인한다 (통일 여부는 결과를 보고 별도 결정):

```sql
SELECT table_collation, COUNT(*) AS cnt,
       GROUP_CONCAT(table_name ORDER BY table_name SEPARATOR ', ') AS tables
FROM information_schema.tables
WHERE table_schema = 'csm' AND table_type = 'BASE TABLE'
  AND table_collation <> 'utf8mb4_0900_ai_ci'
GROUP BY table_collation;
```

## 완료 기록

- **dev 검증 완료**: 2026-08-12. 스키마(컬럼 5종·collation 변환·집계 뷰) 반영 확인,
  배치 발송(`MP-COHS-1`, `MP-FALH-63` → `SENT`, vendor_code=1000, message_key·batch_id 저장),
  콜백 수동 주입 → `DONE` 갱신 및 `sms_request_FALH` 기록 확인. placeholder 오류 없음.
  **실서버는 아직 구버전**(schema-bootstrap 로그 0건)이다.
- **refkey 중복 검증**: 2026-08-12, 7개 기관 전부 0건 → `uk_th_refkey` UNIQUE 즉시 적용
- **refkey 신규 형식**: `MP-{instCode}-{historyId}` (예: MP-COHS-123456). 구형식 콜백은
  폴백으로 처리 중
- **Phase 4 연동 예정**: `csm.sms_batch.total_cost`(전 단위)가 지갑 차감 금액,
  `sms_wallet_tx.ref_type='SMS_BATCH'` 의 참조 대상. 차감 지점은
  `SmsBatchService.send()` 내 주석 참조
