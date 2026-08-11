# 문자 발송 진입점 인벤토리

> 작성: 2026-08-11 · 대상 커밋: Task 0-E 시점
> 목적: 선불 충전 과금 도입 전에 "문자가 나갈 수 있는 모든 경로"를 확정한다.
> 여기에 없는 경로로 문자가 나가면 잔액 차감도 이력 추적도 되지 않는다.

## 전수 목록

| 진입점 | URL | 인증 수준 | 이력 기록 | refkey 사용 | 과금 대상(제안) |
|---|---|---|---|:---:|:---:|
| `PageController.relaySendSms`<br>([PageController.java:3421](../src/main/java/com/coresolution/csm/controller/PageController.java)) | `POST /csm/api/external/sendSMS`<br>`POST /csm/counsel/api/external/sendSMS` | Spring Security 인증 필수 + `ensureInst(session)` 로 세션 기관 확인.<br>CSRF 보호 대상(예외 목록에 없음) — 아래 §미확인 참고 | ✅ `transmission_history_<inst>`<br>성공/실패 모두 | ✅ 서버 생성 | **Y** |
| `PageController.sendSmsByLegacyContract`<br>([PageController.java:3586](../src/main/java/com/coresolution/csm/controller/PageController.java)) | `POST /csm/sms/sendSMS` | **차단됨 — 410 Gone 반환** (Task 0-E) | ❌ (차단 전에도 없었음) | ❌ | **차단** |
| `PageController.sendOtpBySms`<br>([PageController.java:406](../src/main/java/com/coresolution/csm/controller/PageController.java)) | `POST /csm/findpwd/post`<br>(`channel=sms` 일 때만) | **비인증 공개** — `/findpwd/**` 가 `PUBLIC_PATHS` | ❌ **기록 없음** | ⚠️ `pwd-otp-…` (형식 불일치) | **N (회사 부담)** |
| `SmsSendController.send`<br>([SmsSendController.java:28](../sms/src/main/java/com/coresolution/sms/controller/SmsSendController.java)) | `POST /sms/api/sms/send` | SSO 세션(`SmsSession`) | ✅ `transmission_history_<inst>` | ✅ 서버 생성 | **N/A — 앱 폐기** |

### 부가 경로 (게이트웨이 직접 호출은 아님)

| 항목 | URL | 비고 |
|---|---|---|
| 결과 리포트 수신 | `POST /csm/api/external/SMSRequest` | 발송이 아니라 상태 갱신. 인증·서명 검증 없음(공개 + CSRF 예외) |

## OTP를 과금 제외로 분류한 근거

`sendOtpBySms` 는 **비밀번호 재설정 전용**이다. 다음 이유로 `billable = N`(회사 부담)으로 둔다.

1. **기본 채널이 이메일이다.** `Findpwd.html` 의 라디오 기본 선택은 `email` 이며, SMS는
   사용자가 직접 선택해야 한다.
2. **발송 실패가 로그인 불가로 이어지지 않는다.** 실패 시 발급한 OTP를 즉시 폐기하고
   안내 문구만 반환한다. 정상 로그인 경로에는 아무 영향이 없고, 이메일 채널로 재설정할 수
   있다.
3. 기관 사용자의 업무 발송이 아니라 **서비스 제공자의 계정 복구 수단**이다. 기관 지갑에서
   차감하면 "비밀번호를 잊을수록 기관이 돈을 낸다"는 부당한 과금이 된다.

## Phase 1 후속 과제

### ① OTP refkey 형식이 결과 매핑을 100% 실패시킨다

- OTP는 refkey를 `"pwd-otp-" + inst + "-" + userId + "-" + millis` 로 만든다
  ([PageController.java:438](../src/main/java/com/coresolution/csm/controller/PageController.java)).
- 결과 리포트 수신부는 기관코드를 **`refkey.substring(0, 4)`** 로 복원한다
  ([PageController.java:3639](../src/main/java/com/coresolution/csm/controller/PageController.java)).
- 따라서 OTP 건의 기관코드는 **항상 `"pwd-"`** 로 읽힌다. 존재하지 않는 기관이므로
  `updateMessageHistoryStatus` 가 0건을 갱신하고 `no history row updated` 경고만 남는다.
- 다른 발송 경로는 `buildSmsRefkey` 가 `{inst}{yyyyMMddHHmmss}{rand4}` 를 만들어 앞 4자리가
  기관코드가 된다. **기관코드가 정확히 4자일 때만 성립한다.**

#### 기관코드 길이 — prod 확인이 필요한 구조적 위험

코드가 허용하는 기관코드 범위와 콜백의 가정이 어긋나 있다.

| 위치 | 규칙 |
|---|---|
| `safeInst()` ([SmsService.java:218](../src/main/java/com/coresolution/csm/serivce/SmsService.java)) | `[A-Za-z0-9_]{2,20}` — **2~20자 허용** |
| 콜백 기관 복원 ([PageController.java:3639](../src/main/java/com/coresolution/csm/controller/PageController.java)) | `refkey.substring(0, 4)` — **4자 고정 가정** |
| `mp_institution.inst_code` | `VARCHAR(50)` |

4자가 아닌 기관이 존재하면 그 기관의 발송 결과 리포트는 **전량 유실**된다(존재하지 않는
테이블을 UPDATE 하려다 0건 갱신 + 경고 로그).

**아직 prod 에서 확인되지 않았다.** dev 에는 9자 코드(`HSOP_0001`)가 있으나 테스트 잔재로
확인되어 판정 근거에서 제외했다. prod 에서 아래 한 번으로 결론난다.

```sql
-- 'transmission_history_' 는 21자이므로 22번째부터가 기관코드다
SELECT table_name, CHAR_LENGTH(SUBSTRING(table_name, 22)) AS inst_len
FROM information_schema.tables
WHERE table_schema='csm' AND table_name LIKE 'transmission_history_%'
  AND CHAR_LENGTH(SUBSTRING(table_name, 22)) <> 4;
```

- **0건** → 현재 prod 는 안전. Phase 1 에서는 "앞으로도 4자를 넘지 않도록 강제"만 하면 된다.
- **1건 이상** → 해당 기관은 이미 결과 리포트가 유실 중이다. 우선순위를 올려야 한다.

**조치 방향:** 어느 쪽이든 refkey 포맷을 `{instCode}-{messageId}` 같은 **구분자 있는 결정적
형식**으로 재정의하고, 수신부의 `substring(0, 4)` 를 파싱으로 교체한다. 과금 도입 시 refkey가
차감 원장과 메시지를 잇는 유일한 키가 되므로 **선행 과제**다.

### ② OTP는 발송 이력 자체가 남지 않아 발송량 추적이 불가능하다

- `sendOtpBySms` 는 `externalSmsGatewayService.send()` 를 직접 호출하고
  `insertTransmissionHistory()` 를 부르지 않는다.
- 따라서 `transmission_history_<inst>` 에 행이 없고, `CsmSmsOtpService` 는 인메모리
  `ConcurrentHashMap`(5분 TTL)이라 영속 기록도 없다.
- 성공 경로에는 로그 문장조차 없어(`[findpwd/sms]` 는 실패 시에만 기록) **현재 OTP 발송량은
  측정 자체가 불가능**하다.

**조치 방향:** 과금 제외 대상이라도 이력은 남겨야 한다. 벤더 비용은 실제로 발생하므로
"회사 부담분이 월 얼마인지"를 알 수 없으면 단가 협상도 이상 급증 탐지도 불가능하다.
`billable = N` 플래그를 가진 이력 행으로 기록하는 방향을 권한다.

## CSRF 처리 방식 (Task 0-G 조사 결과)

`POST /csm/api/external/sendSMS` 는 `SecurityConfig` 의 CSRF 예외 목록에 **없다**.
예외 목록에 있는 것은 결과 리포트 수신용 `/api/external/SMSRequest` 뿐이니 혼동하지 말 것.
따라서 발송 API는 **CSRF 토큰이 필수**다.

토큰 저장소는 커스터마이징이 없어 Spring Security 기본값인 `HttpSessionCsrfTokenRepository`
(세션 기반, 쿠키 기반 아님)를 쓴다. 전역 `fetch` 인터셉터나 공통 래퍼는 존재하지 않으므로,
**호출부가 매번 직접 헤더를 넣어야 한다.**

live 화면 두 곳 모두 `<meta name="_csrf">` / `<meta name="_csrf_header">` 를 읽어 헤더에
넣고 있어 정상 통과한다.

| 화면 | 토큰 주입 위치 |
|---|---|
| [design/consultation-list.html:1709](../src/main/resources/templates/design/consultation-list.html) | `sendBulkSms()` — meta → `[csrfHeader]: csrf` |
| [design/inpatient-consultation.html:1990](../src/main/resources/templates/design/inpatient-consultation.html) | `csrf()` 헬퍼 → `[c.header]: c.token` |

> 초판에는 "`list.js` 의 대량 발송 fetch 에 CSRF 헤더가 없어 403이 예상된다"고 적혀 있었다.
> **틀린 분석이었다.** 해당 파일은 활성 라우트가 없는 레거시 템플릿의 스크립트였고,
> `/counsel/list` 는 `design/consultation-list` 를 렌더링한다. 혼동을 막기 위해 죽은
> 템플릿·스크립트는 Task 0-H 에서 삭제했다.

### Phase 1 배치 발송 API 설계 시 주의

세션 기반 CSRF이므로 **호출자에 따라 처리가 갈린다.** 하나의 엔드포인트에 CSRF 예외를 걸어
두 용도를 겸하게 하면 브라우저 CSRF 방어가 통째로 사라진다. 경로를 분리할 것.

| 호출자 | CSRF | 필요 조치 |
|---|---|---|
| 브라우저 (csm 화면) | 통과 | 기존과 동일하게 meta → 헤더 주입 |
| mediplat → csm 서버 간 | **통과 불가** | 내부 전용 경로에만 CSRF 예외 + HMAC 서명 검증 + 소스 IP 제한 |
