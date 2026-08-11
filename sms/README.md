# sms — 미사용 모듈 (기동 금지)

> 상태: **동결(frozen)**. 배포되지 않으며 실행해서도 안 된다.
> 동결 시점: 2026-08-11 (Task 0-D)

## 왜 동결했나

문자 발송·이력·비용 화면을 담당하던 독립 앱이다. 다음 사실이 확인되어 사용을 중단했다.

1. **운영에 배포된 적이 없다.** `deploy-dev.sh` / `deploy-prod.sh` / `deploy-nightly.sh`
   어디에도 sms 배포 단계가 없고, systemd 유닛도 존재하지 않는다.
2. **포털에 노출된 적도 없다.** MediPlat 부트스트랩이 `SMS_BASE_URL` 미설정으로 인해
   localhost 기본값을 쓰다가 DEV/PROD 엔드포인트 검증에 걸려 서비스 등록에 실패해 왔다
   (WARN 로그만 남기고 skip). 그 결과 `mp_service` / `mp_institution_service` 에 SMS 행이
   존재하지 않는다.
3. **포트 8084는 ResvHub(예약 시스템, 별도 리포지토리)가 점유 중이다.**
   문서상 8084가 sms로 배정되어 있었으나 실제와 다른 잘못된 기재였다.

## ⚠️ 이 앱을 실행하면 안 되는 이유

- **포트 충돌.** 기본 포트가 8084로 남아 있었다면 기동 즉시 ResvHub와 충돌해 **예약 시스템
  장애**를 유발한다. 지금은 `SMS_PORT` 기본값을 `0`(랜덤 포트)으로 바꿔 두었다.
  **`SMS_PORT=8084` 로 되돌리지 말 것.**
- **SSO 서명 유출.** MediPlat 에 `SMS_BASE_URL` 을 다시 설정하면 포털이
  `http://<host>:8084/sms/mediplat/sso/entry?...&signature=<HMAC>` 로 브라우저를 보낸다.
  8084는 ResvHub 이므로 **유효한 SSO 서명·기관코드·계정ID가 ResvHub 액세스 로그에 평문으로
  기록된다.** 서명 재사용을 막는 nonce가 없어 유효창 안에서는 재생 가능하다.

## 코드를 지우지 않은 이유

결제(선불 충전) 연동 이후 문자 기능을 포털에 다시 올릴 예정이다. 발송·이력·비용 로직은
그때 참고 자산이 된다. 안정화 후 제거 여부를 다시 판단한다.

**제거 대상 후보:** 이 모듈 전체(`sms/`). 문자 기능이 csm 배치 발송 API로 통합되고
운영에서 2개 분기 이상 문제가 없으면 삭제한다.

## 현재 동결 상태 (무엇이 꺼져 있나)

| 항목 | 상태 |
|---|---|
| `SMS_PORT` 기본값 | `0` (랜덤 포트) — 8084 지정 금지 |
| 루트 `build.gradle` 의 `smsDevJar` / `smsProdJar` | **제거됨** |
| `packageDevDeploy` / `packageProdDeploy` 번들 | **sms jar 미포함** |
| MediPlat `platform.bootstrap.sms-base-url` | **제거됨** |
| MediPlat 부트스트랩 SMS 서비스 등록 | **제거됨** |
| `Portal.html` 의 SMS 카드 정의 | **제거됨** |
| 소스 코드 (`sms/src/**`) | **그대로 보존** |

## 복원하려면

`docs/sms-portal-restore-checklist.md` 를 따를 것. 특히 **포트 재배정이 선행 과제**다.

## 비즈뿌리오 계정 관련

이 앱은 csm 과 **동일한 비즈뿌리오 계정**(`BIZPPURIO_*` 환경변수)을 쓰도록 되어 있었다.
계정이 분리되어 있지 않으므로 **벤더 측에서 회수할 별도 API 권한이 없다.** 코드 차단만으로
충분하다. 만약 sms 앱 전용 계정이 별도로 발급되어 있다면 벤더에 회수를 요청할 것.
