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
  기관코드가 되지만, **기관코드가 4자가 아닌 기관이 생기면 동일하게 깨진다.**

**조치 방향:** refkey 포맷을 `{instCode}-{messageId}` 같은 결정적 형식으로 재정의하고,
수신부의 `substring(0, 4)` 를 구분자 파싱으로 교체한다. 과금 도입 시 refkey가 차감 원장과
메시지를 잇는 유일한 키가 되므로 **선행 과제**다.

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

## 미확인 — 실동작 확인 필요

`POST /csm/api/external/sendSMS` 는 `SecurityConfig` 의 CSRF 예외 목록에 없어 토큰이
필요하지만, 호출 측 [list.js:1264](../src/main/resources/static/js/csm/counsel/list.js) 의
`fetch` 는 `Content-Type` 만 보내고 CSRF 헤더를 붙이지 않는다. 같은 파일 안의 다른 호출
(L518, L644)은 `meta[name="_csrf"]` 를 읽어 헤더에 넣고 있어 **대량 발송 경로만 누락된
것으로 보인다.**

현재 구성대로면 403이 예상되나, 운영에서 대량 발송이 정상 동작한다는 보고가 있으면 전제가
틀린 것이다. **이번 작업 범위(발송 동작 변경 금지)에 해당하므로 수정하지 않았다.**
운영 로그에서 다음을 확인한 뒤 별건으로 처리한다.

```bash
sudo journalctl -u csm-next --since "30 days ago" | grep -c "api/external/sendSMS"
```
