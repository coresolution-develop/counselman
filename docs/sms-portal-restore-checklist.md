# 문자 기능 포털 복원 체크리스트

> 작성: 2026-08-11 (Task 0-D) · 갱신: 2026-08-11 (dev 배포 실측 반영)
> 대상: 선불 충전 결제 연동 이후 "문자" 기능을 MediPlat 포털에 다시 노출할 때
> 전제: 발송 실행은 **csm** 이 담당하고, 지갑·결제는 **mediplat** 에 둔다
> (포트는 환경마다 다르다 — `docs/links-deploy.md` 참고)

---

## ⚠️ 먼저 읽을 것 — `mp_service` 는 코드가 아니라 운영자가 관리한다

`PlatformStoreService.bootstrapDefaults()` 는 **기본 서비스의 씨앗만 심는다.** 실제 운영
DB 에는 코드에 존재하지 않는 서비스가 관리자 화면을 통해 추가되어 있다.

dev 실측(2026-08-11)에서 확인된 서비스: `MEDITOKS`, `TRAMS`, `AMB`, `BEAUDESK`,
`RESVHUB`, `BEAUTYHUB` — 전부 부트스트랩 코드에 없다.

여기서 두 가지가 따라온다.

1. **코드에서 등록을 제거해도 기존 DB 행은 사라지지 않는다.** 부트스트랩이 더 이상
   `upsert` 하지 않을 뿐이다. 포털 카드를 없애려면 **DB 행을 직접 지워야 한다.**
2. **복원할 때도 코드 수정 없이 관리자 화면으로 등록하는 선택지가 있다.** 아래 3번은
   "부트스트랩이 자동으로 심게 하려면" 의 절차다. 일회성이면 화면 등록이 더 간단하다.

---

## 0. 먼저 정하고 시작할 것

| # | 결정 사항 | 왜 선행인가 |
|---|---|---|
| 0-1 | **문자 화면을 어디에 둘 것인가** — csm 안의 페이지인가, 별도 앱인가 | 별도 앱이면 1번(포트)부터, csm 안이면 3번부터 시작한다 |
| 0-2 | **포트 재배정** (별도 앱인 경우) | **8084는 절대 쓰지 말 것.** ResvHub(예약 시스템)가 점유 중이다 |
| 0-3 | 서비스 코드를 `SMS` 로 유지할지 | `PlatformStoreService.SERVICE_CODE_SMS` 상수는 남겨 두었다 |

> **가장 단순한 복원 경로는 "별도 앱을 되살리지 않는 것"이다.**
> 발송이 이미 csm 에 있으므로, 포털 카드의 base URL 을 csm 으로 두고
> `user_target` 을 csm 의 문자 화면 경로로 지정하면 새 포트도 새 배포 단위도 필요 없다.

---

## 1. 포트 확보 (별도 앱으로 되살릴 때만)

```bash
# 실제로 비어 있는 포트인지 서버에서 확인한다. 문서만 믿지 말 것 —
# 8084가 sms로 문서화되어 있었지만 실제로는 ResvHub가 쓰고 있었다.
sudo ss -lntp
```

- [ ] 비어 있는 포트를 고르고 `sms/src/main/resources/application.properties` 의
      `server.port=${SMS_PORT:0}` 기본값을 갱신
- [ ] `docs/links-deploy.md` 포트 배정표에 반영
- [ ] 리버스 프록시(httpd/nginx)에 라우팅 추가

---

## 2. 빌드·배포 파이프라인 복구 (별도 앱인 경우)

- [ ] `build.gradle` 에 `smsDevJar` / `smsProdJar` (`GradleBuild`, `dir = file('sms')`) 재등록
- [ ] `packageDevDeploy` / `packageProdDeploy` 의 `dependsOn` 과 `copy { from ... }` 에 산출물 추가
- [ ] `settings.gradle` 은 손댈 필요 없다 — 애초에 어떤 모듈도 `include` 하지 않는다
      (각 앱이 독립 Gradle 빌드이고 루트는 `GradleBuild` 태스크로만 호출한다)
- [ ] systemd 유닛 작성 + `EnvironmentFile` 로 시크릿 주입 (`sms/.env.example` 참고)
- [ ] `scripts/deploy-*.sh` 및 `deploy-nightly.sh` 에 배포 단계 추가

---

## 3. MediPlat 포털 노출 복구

- [ ] `mediplat/src/main/resources/application.properties` 에 base URL 프로퍼티 추가
      ```
      platform.bootstrap.sms-base-url=${SMS_BASE_URL}
      ```
      **기본값을 넣지 말 것.** 예전에 `http://localhost:8084/sms` 기본값 때문에
      DEV/PROD 검증에 걸려 등록이 조용히 실패했다.
- [ ] `PlatformStoreService` 에 `@Value` 필드 `bootstrapSmsBaseUrl` 복구
- [ ] `bootstrapDefaults()` 에 `smsLocal/Dev/ProdBaseUrl` 변수와
      `bootstrapService(SERVICE_CODE_SMS, () -> saveService(...))` 복구
- [ ] `saveInstitutionServiceAccess(bootstrapAdminInstCode, List.of(...))` 목록에
      `SERVICE_CODE_SMS` 추가
- [ ] `mediplat/src/main/resources/templates/design/Portal.html` 의 `ICON_MAP` 에 `'SMS'` 항목 복구
- [ ] `mediplat/.env.example` 에 `SMS_BASE_URL` 키 추가

### 등록이 실제로 됐는지 확인 (가장 흔한 실패 지점)

```bash
sudo journalctl -u mediplat-next --since "10 min ago" | grep -i "Skipping bootstrap of service"
```
→ 출력이 있으면 등록에 **실패**한 것이다. WARN 만 남기고 앱은 정상 기동하므로 조용히 지나간다.

```sql
SELECT service_code, use_yn, base_url, display_order FROM csm.mp_service ORDER BY display_order;
SELECT inst_code, service_code, use_yn FROM csm.mp_institution_service WHERE service_code = 'SMS';
```

> **주의 1**: `DEV`/`PROD` 런타임에서는 base URL 에 `localhost` / `127.0.0.1` / `0.0.0.0` 을
> 쓸 수 없다. `validateEndpointUrl()` 이 거부한다.
>
> **주의 2**: `PLATFORM_RUNTIME_ENV` 가 비어 있으면 `spring.profiles.active` 로 추론하고,
> 그것도 없으면 `LOCAL` 로 간주해 검증을 건너뛴다. 운영에서는 반드시 명시할 것.
>
> **주의 3**: `upsertService` 는 `ON DUPLICATE KEY UPDATE` 로 `use_yn` 까지 덮어쓴다.
> 관리자 UI 에서 서비스를 꺼도 다음 재기동에 `Y` 로 되돌아온다.

---

## 3-A. 동결 당시 DB 정리 이력 (Task 0-D 의 D-6 정정)

Task 0-D 는 "`mp_service` / `mp_institution_service` 에 SMS 행이 없으므로 **DB 정리
불필요**"로 판단했다. **이 판단은 환경 하나에만 맞았다.**

| 환경 | 동결 시점 SMS 행 | 원인 |
|---|---|---|
| **prod** | 없음 | `SMS_BASE_URL` 미설정 → localhost 기본값 → DEV/PROD 엔드포인트 검증에 걸려 `Skipping bootstrap of service 'SMS'` 로 **조용히 실패**해 왔다 |
| **dev** | **있었음** (`use_yn='Y'`, `base_url=https://dev.sosyge.net/sms`) | `SMS_BASE_URL` 이 **정상적으로 주입돼 있었다.** localhost 가 아니므로 검증을 통과해 정상 등록됨 |

즉 "조용한 실패"는 **prod 한정 현상**이었다. dev 는 설정이 갖춰져 있어 제대로 등록됐고,
코드에서 등록을 제거한 뒤에도 행이 그대로 남아 포털에 카드가 계속 노출됐다.

2026-08-11 dev 에서 아래로 정리했다(백업 후 실행).

```sql
DELETE FROM csm.mp_institution_service WHERE service_code = 'SMS';  -- 3건
DELETE FROM csm.mp_service_endpoint    WHERE service_code = 'SMS';  -- 1건
DELETE FROM csm.mp_service             WHERE service_code = 'SMS';  -- 1건
```

**교훈:** 서비스 등록을 코드에서 제거할 때는 **환경마다 DB 상태를 따로 확인**해야 한다.
한 환경에서 행이 없다고 다른 환경도 같다고 가정하면 안 된다.

```sql
-- 환경별로 각각 실행할 것
SELECT service_code, base_url, use_yn FROM csm.mp_service WHERE service_code = 'SMS';
```

> 참고: `platform.bootstrap.sms-base-url` 프로퍼티를 제거했으므로, 각 환경의
> EnvironmentFile 에 남아 있는 `SMS_BASE_URL` 은 이제 **읽는 곳이 없는 값**이다. 지워도 된다.

---

## 4. 테스트 정리

- [ ] `mediplat/src/test/.../PlatformStoreServiceTest.bootstrap_doesNotRegisterSmsService()` 제거
      또는 반대 단언으로 전환
- [ ] `bootstrap_skipsServiceWithLocalhostDevUrl_andStillRegistersValidServices()` 는 **유지**할 것.
      이 테스트는 7541d45 의 회귀 방지 장치다 — 엔드포인트 하나가 잘못돼도 나머지 서비스는
      정상 등록되어야 하고, 포털 전체가 죽어서는 안 된다는 보장이다.
      (현재는 `CANCER_TREATMENT` 를 잘못된 URL 역할로 쓰고 있다. SMS 로 되돌릴 필요 없다.)

---

## 5. 과금 연동 전 반드시 해결할 선행 과제

문자 기능을 되살리는 것과 별개로, **과금을 붙이기 전에** 아래가 정리되어야 한다.
상세는 `docs/sms-send-entrypoints.md` 참고.

- [ ] **refkey 포맷 재정의** — 현재 결과 리포트 수신부가 기관코드를 `refkey.substring(0, 4)`
      로 복원한다. 기관코드가 4자가 아니면 결과 매핑이 전부 깨진다. OTP 발송은 이미 100%
      매핑 실패 중이다.
- [ ] **`transmission_history_<inst>.refkey` 에 인덱스 추가** — 콜백 UPDATE 가 풀스캔이다.
- [ ] **서버 측 바이트 계산으로 SMS/LMS 타입 결정** — 현재는 클라이언트가 타입을 정한다.
      과금이 붙으면 클라이언트가 `type=sms` 로 LMS 분량을 보내 단가를 낮출 수 있다.
- [ ] **발송 진입점 단일화** — 배치 발송 API 하나로 모으고 나머지는 차단한다.
      (`POST /csm/sms/sendSMS` 는 이미 410 Gone 처리했다.)
- [ ] **Rate limit / 동시성 제어** — 현재 대량 발송은 브라우저가 `Promise.all` 로 전 수신자에게
      동시 요청한다. 보류 문자 일괄 재발송 시 429 대량 발생 위험이 있다.
- [ ] **결과 리포트 엔드포인트 인증** — `POST /csm/api/external/SMSRequest` 는 인증·서명 검증이
      없다. 환불 트리거로 연결하기 전에 IP 화이트리스트 또는 서명 검증을 추가할 것.

---

## 6. 되돌릴 커밋 참조

이 문서의 동결 작업은 단일 커밋으로 분리되어 있다.

```bash
git log --oneline --grep="freeze unused sms app"
```

전체 되돌리기가 필요하면 해당 커밋을 `git revert` 한 뒤 위 1~3번을 검토한다.
단, **포트만은 되돌리지 말 것** — 8084 는 ResvHub 가 쓰고 있다.
