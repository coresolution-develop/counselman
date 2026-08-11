# CounselMan + MediPlat

로컬 개발 환경에서 `CounselMan(csm)`과 `MediPlat(mediplat)`를 함께 실행하기 위한 체크리스트입니다.

## 프로젝트 구성

- `csm`
  기존 상담관리 시스템
  Spring Boot + WAR 구조
  로컬 기본 주소: `http://localhost:8081/csm`

- `mediplat`
  통합 플랫폼
  별도 Spring Boot + JAR 구조
  로컬 기본 주소: `http://localhost:8082`

- `sms` — **미사용 모듈. 기동 금지**
  독립 문자 발송 앱이었으나 배포·포털 노출 이력이 없어 동결했습니다.
  **포트 8084는 ResvHub(예약 시스템, 별도 리포지토리)가 사용 중이므로 재기동하면 안 됩니다.**
  소스는 보존되어 있습니다. 상세는 [`sms/README.md`](sms/README.md),
  복원 절차는 [`docs/sms-portal-restore-checklist.md`](docs/sms-portal-restore-checklist.md).

## 빠른 시작 (두 서버 동시 실행)

`CounselMan`과 `MediPlat`를 동시에 시작하려면 프로젝트 루트에서 아래 명령을 실행합니다.

```bash
cd /Users/leesumin/csm
./scripts/local-up.sh
```

## 1. 사전 준비

아래 항목이 먼저 준비되어 있어야 합니다.

- Java 17
- Gradle Wrapper 사용 가능 환경
- MySQL 접속 가능
- 네트워크에서 개발 DB 접근 가능

확인 명령:

```bash
java -version
./gradlew -version
```

## 2. 소스 받기

```bash
git clone https://github.com/coresolution-develop/counselman.git
cd counselman
git pull origin main
```

## 3. 기본 설정 확인

`csm`은 기본적으로 `application.properties`에서 `dev` 프로파일을 기본으로 사용합니다.

주요 설정 파일:

- `src/main/resources/application.properties`
- `src/main/resources/application-local.properties`
- `src/main/resources/application-dev.properties`
- `mediplat/src/main/resources/application.properties`

로컬 실행 시 확인할 핵심 항목:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.datasource.driver-class-name` (필요 시에만 지정, 기본 자동 감지)
- `MEDIPLAT_DATASOURCE_*`
- `MEDIPLAT_DATASOURCE_DRIVER_CLASS_NAME` (선택)
- `login.aes-key`
- `mediplat.sso.shared-secret`
- `platform.counselman.datasource.*`
- `PLATFORM_COUNSELMAN_DATASOURCE_DRIVER_CLASS_NAME` (선택)
- `PLATFORM_RUNTIME_ENV` (`LOCAL`, `DEV`, `PROD`)
- `platform.counselman.login.aes-key`
- `platform.counselman.sso-shared-secret`

## 4. 환경변수 (필수)

**properties 파일에는 더 이상 비밀값 기본값이 없습니다.** 아래 값이 비어 있으면 애플리케이션이
기동 단계에서 실패합니다(의도된 fail-fast 동작).

설정 방법:

```bash
cp .env.example .env.local
# .env.local 을 열어 값을 채운다. 이 파일은 .gitignore 대상이다.
./scripts/local-up.sh
```

`scripts/local-up.sh` 가 `.env.local` 을 자동으로 읽고, 누락된 키가 있으면
어떤 키가 없는지 먼저 알려준 뒤 중단합니다.

전체 키 목록과 설명은 각 앱의 템플릿을 참고하세요.

| 앱 | 템플릿 |
|---|---|
| CounselMan (csm) | [`.env.example`](.env.example) |
| MediPlat | [`mediplat/.env.example`](mediplat/.env.example) |
| sms (미사용) | [`sms/.env.example`](sms/.env.example) |

로컬에서 최소한으로 필요한 키:

```
SPRING_PROFILES_ACTIVE=local
DEV_DB_PASSWORD=
LOGIN_AES_KEY=
SPRING_MAIL_PASSWORD=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET=
PLATFORM_ADMIN_PASSWORD=
```

주의:

- `MEDIPLAT_SSO_SHARED_SECRET` 과 `COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET` 는 동일해야 합니다.
  (`MEDIPLAT_SSO_SHARED_SECRET` 미설정 시 후자로 폴백하므로 보통 후자 하나만 채우면 됩니다.)
- `LOGIN_AES_KEY` 는 AES-128 키로 **정확히 16자**여야 합니다.
- 서버 환경에서는 `.env` 파일이 아니라 systemd `EnvironmentFile` 로 주입합니다.
  자세한 내용은 [`scripts/systemd/README.md`](scripts/systemd/README.md) 를 참고하세요.

## 5. 포트 점유 확인

기본 포트:

- `csm`: `8081`
- `mediplat`: `8082`

실행 전 확인:

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
lsof -nP -iTCP:8082 -sTCP:LISTEN
```

포트가 이미 사용 중이면 기존 프로세스를 종료한 후 실행합니다.

예시:

```bash
kill -15 <PID>
```

## 6. 한 번에 같이 실행

루트에서 아래 명령으로 `csm`과 `mediplat`를 함께 실행할 수 있습니다.

```bash
./scripts/local-up.sh
```

특징:

- `8081`, `8082` 포트가 이미 사용 중이면 바로 중단
- `csm`, `mediplat` 로그를 한 터미널에서 함께 출력
- `Ctrl + C`로 종료하면 두 앱이 같이 종료

## 7. CounselMan 단독 실행

```bash
./gradlew bootRun
```

실행 후 확인:

```bash
curl -I http://127.0.0.1:8081/csm/login
```

정상 응답:

- `HTTP/1.1 200`

## 8. MediPlat 단독 실행

새 터미널에서 실행:

```bash
cd mediplat
../gradlew bootRun
```

실행 후 확인:

```bash
curl -I http://127.0.0.1:8082/login
```

정상 응답:

- `HTTP/1.1 200`

## 9. 접속 주소

- CounselMan 로그인: `http://localhost:8081/csm/login`
- MediPlat 로그인: `http://localhost:8082/login`
- 병실현황판: `http://localhost:8081/csm/room-board`
- 병실현황판 관리: `http://localhost:8081/csm/admin/room-board`

## 10. 로컬 실행 점검 순서

1. Java 17 확인
2. `git pull`
3. `./gradlew compileJava`
4. `cd mediplat && ../gradlew compileJava`
5. `8081`, `8082` 포트 점유 확인
6. `./scripts/local-up.sh` 실행
7. 또는 필요 시 각 앱 단독 실행
8. 로그인 페이지 응답 확인
9. MediPlat 로그인 후 CounselMan 진입 확인

## 11. 배포 산출물 빌드

루트에서 한 번에 `csm WAR`와 `mediplat JAR`를 같이 만들 수 있습니다.

PROD 배포 전에는 [`docs/prod-deploy-checklist.md`](docs/prod-deploy-checklist.md)를 먼저 확인하고,
운영 서버에서 아래 사전 점검 스크립트를 실행합니다.

```bash
./scripts/prod-preflight.sh
```

DEV 배포 묶음:

```bash
./gradlew packageDevDeploy
```

산출물:

- `build/deploy/dev/csm-dev.war`
- `build/deploy/dev/mediplat-dev.jar`

PROD 배포 묶음:

```bash
./gradlew packageProdDeploy
```

산출물:

- `build/deploy/prod/csm-prod.war`
- `build/deploy/prod/mediplat-prod.jar`

기존 단독 빌드도 그대로 사용할 수 있습니다.

- `./gradlew devWar`
- `./gradlew prodWar`
- `./gradlew -p mediplat devJar`
- `./gradlew -p mediplat prodJar`

## 12. 병실현황판 기능 점검

1. `http://localhost:8081/csm/admin/room-board` 접속
2. 병실 기준정보 등록
3. EMR 복사 데이터 붙여넣기
4. `미리보기` 확인
5. `가져오기 저장`
6. `http://localhost:8081/csm/room-board`에서 결과 확인
7. 입원상담 화면에서 `병실현황판 보기` 버튼으로 병실 선택 확인

## 13. 참고 사항

- `mediplat` 플랫폼 설정 데이터는 기본적으로 공유 MySQL의 `mp_*` 테이블에 저장됩니다.
- 서비스 URL은 `mp_service_endpoint`에서 환경별(`LOCAL/DEV/PROD`)로 관리됩니다.
- 다른 저장소를 쓰고 싶으면 `MEDIPLAT_DATASOURCE_URL`, `MEDIPLAT_DATASOURCE_USERNAME`, `MEDIPLAT_DATASOURCE_PASSWORD`로 분리할 수 있습니다.
- `mediplat`를 `systemd`로 운영할 때는 `SPRING_DATASOURCE_*`와 `PLATFORM_COUNSELMAN_DATASOURCE_*`가 모두 같은 `csm` MySQL을 가리키도록 맞춰야 합니다. `SPRING_DATASOURCE_URL`이 H2로 남아 있으면 로컬과 다른 기관/권한 데이터가 보일 수 있습니다.
- 서버용 설정(`nginx`, `systemd`, `Tomcat`, `SSL 인증서`)은 로컬 실행에 포함되지 않습니다.
- 다른 PC에서 실행 시에도 DB 접속 정보와 SSO secret 값만 맞으면 동일하게 사용할 수 있습니다.

## 13-A. 보안 — 시크릿 관리와 회수(rotation) 대상

### 현재 상태

properties 파일에서 평문 비밀값을 모두 제거하고 환경변수로 전환했습니다. 값은
로컬은 `.env.local`, 서버는 systemd `EnvironmentFile` 로 주입합니다.

### ⚠️ 아직 끝나지 않았습니다 — 사람이 해야 할 일

**파일에서 값을 지워도 git 이력에 남은 과거 비밀번호는 그대로입니다.**
리포지토리 접근 권한을 가진 사람은 `git log -p` 로 전부 복원할 수 있습니다.
따라서 아래 자격증명은 **모두 실제로 교체해야** 이 작업이 완료됩니다.

| # | 대상 | 교체 난이도 | 비고 |
|---|---|---|---|
| 1 | **csdev DB 비밀번호** | 무중단 가능 | 아래 절차 참고 |
| 2 | **SSO 공유 시크릿** | **점검창 필요** | 무중단 수단 없음 |
| 3 | Kakao client-secret | 무중단 가능 | 개발자센터에서 재발급 후 `KAKAO_CLIENT_SECRET` 갱신 |
| 4 | 메일 계정 비밀번호 | 무중단 가능 | Gmail 앱 비밀번호 재발급 후 `SPRING_MAIL_PASSWORD` 갱신 |
| 5 | 플랫폼 관리자 비밀번호 | 무중단 가능 | `PLATFORM_ADMIN_PASSWORD` 변경 후 MediPlat 재기동(계정이 갱신됨) |

#### 1) csdev DB 비밀번호 — MySQL 8 이중 비밀번호로 무중단 교체

`csdev` 계정은 **csm, mediplat(DataSource 2개), ResvHub** 가 공유합니다.
한 번에 바꾸면 갱신되지 않은 앱이 즉시 접속 실패하므로, 구/신 비밀번호를 함께
받아주는 기간을 두고 순차 갱신합니다.

```sql
-- ① 새 비밀번호 설정 + 기존 비밀번호도 당분간 유효하게 유지
ALTER USER 'csdev'@'%' IDENTIFIED BY '<새비밀번호>' RETAIN CURRENT PASSWORD;
```

```bash
# ② 앱을 하나씩 갱신하고 재기동 (각 단계마다 정상 동작 확인 후 다음으로)
#    - csm            : /etc/default/... 의 SPRING_DATASOURCE_PASSWORD
#    - mediplat       : MEDIPLAT_DATASOURCE_PASSWORD + PLATFORM_COUNSELMAN_DATASOURCE_PASSWORD (2개)
#    - ResvHub        : 별도 리포지토리. 담당자 확인 필요
```

```sql
-- ③ 전 앱이 새 비밀번호로 붙은 것을 확인한 뒤에만 실행
SELECT user, host, db, COUNT(*) FROM information_schema.processlist
 WHERE user = 'csdev' GROUP BY user, host, db;

ALTER USER 'csdev'@'%' DISCARD OLD PASSWORD;
```

> mediplat 은 DataSource 가 2개(`mediplat-store`, `mediplat-counselman`)라 환경변수도
> 2개를 함께 바꿔야 합니다. 하나만 바꾸면 포털은 뜨지만 CounselMan 계정 로그인이 깨집니다.

#### 2) SSO 공유 시크릿 — 무중단 교체 수단 없음

`COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET` 는 발급자(mediplat)와 검증자들이 **같은 값**을
가져야만 서명이 맞습니다. 구/신 키를 동시에 받아주는 로직이 없으므로 **점검창을 잡고
전 앱을 동시에 갱신·재기동**해야 합니다.

대상: **csm**, **mediplat**, **ResvHub**(별도 리포지토리), 그리고 `mp_service` 에 등록된
서비스들 — `FORMFLOW`, `COUNSELMAN`, `ROOM_BOARD`, `SEMINAR_ROOM`, `CANCER_TREATMENT`,
`RESVHUB`, `FLEET`.

> 교체 전에 각 서비스가 이 환경변수를 실제로 쓰는지 확인하세요.
> `for p in $(pgrep -f 'java -jar'); do sudo tr '\0' '\n' < /proc/$p/environ | grep SSO_SHARED; done`

#### git 이력 정리

`git filter-repo` 등으로 과거 커밋에서 비밀값을 지우는 작업은 **이 문서에서 수행하지
않습니다.** 전 개발자의 클론을 무효화하고 CI/배포 이력에 영향을 주므로 운영 판단이
필요합니다. 위 1~5번 교체를 먼저 끝내면 이력에 남은 값은 무력화되므로, 이력 정리는
그다음에 별도로 결정하십시오.

### 후속 과제

- **cancer-treatment 앱**의 `cancer-treatment.sso.shared-secret` 기본값 `change-me` 는
  이번 범위에 포함하지 않았습니다. 실제 비밀값은 아니지만 동일하게 정리 대상입니다.
- **SSO `expires` 상한 검증**은 csm(`MediplatSsoService`)에만 추가했습니다.
  동일 결함이 **ResvHub 및 그 외 SSO 수신 앱**에도 있으나 별도 리포지토리이므로
  각 리포에서 같은 검증을 추가해야 합니다.

## 14. 작업 현황 정리 (GitHub push + 로컬 반영분)

기준:

- 확인일: `2026-04-16`
- 작업 브랜치: `main`
- 원격 최신 커밋: `origin/main = 1dd1ead`

완료된 작업:

- `2026-04-15` (`1dd1ead`): room-board SSO 안정화, 모바일 메뉴/레이아웃 개선, 상담서류 `추출`/`적용` 분리
- `2026-04-15` (`f7e25e2`): 병실현황판 전용계정(모델/관리화면) 추가, mediplat 로그인 분기 개선, 통합 병실현황판 뷰어 연동
- `2026-04-14` (`3fbf1e0`, `b2e7ba9`): 공지 팝업 렌더링 보완, 상담예약/공지사항 기능 및 UI 개선
- `2026-04-14` (`41f69d2`, `e63c14b`, `bef811a`): 환경별 서비스 URL 관리, SSO 리다이렉트 회귀 테스트, datasource 드라이버 자동 감지
- `2026-04-13` (`f4e3436`, `ee36269`, `f63d995`, `133206a`): mediplat 관리자 기능 고도화, 배포 패키징 태스크 정리, 기관 자동 연동 확장

2026-04-16 업무일지 (이번 반영분):

- 로그인 동선 정리:
  - `http://localhost:8081/csm/login` 진입 시 mediplat 로그인(`/login`)으로 리다이렉트
  - 로컬/서버 공통으로 `mediplat.platform.base-url` 기준 리다이렉트 동작 통일
- 상담예약 용어/기본값 정리:
  - 명칭을 `상담 접수`로 변경, `예약일시`를 `접수 시간`으로 변경
  - 신규 저장 시 `접수 시간=현재`, `상태=접수(RESERVED)` 기본 적용
  - 상태 라벨을 `접수 / 입원상담 연계 / 취소` 기준으로 정리
- mediplat 서비스 화면 개선:
  - `서비스 목록` 문구 제거, 카드형 서비스 보드만 노출
  - 카드 영역 중앙 정렬, 카드/폰트 크기 축소, 최대 4열 반응형 그리드 적용
  - `COUNSELMAN` + `병실현황판` 연동 기관은 services 화면에 `병실현황판` 카드도 함께 표시
- 병실현황판 서비스 분리 및 복귀 동선:
  - 병실현황판을 mediplat 내부 화면이 아닌 별도 서비스 진입 흐름으로 분리
  - 팝업 모드 상단에 `로그아웃` 버튼 추가, 클릭 시 mediplat 서비스 화면으로 복귀
- 기관별 연동 제어 추가:
  - mediplat 관리자에 `ROOMBOARD_CSM_LINK` 토글 추가
  - 연동 비활성 기관은 SSO/진입 경로에서 403 차단
  - `mp_institution_integration` 테이블 및 기본 동기화 로직 추가
- 브랜드 UI 반영:
  - csm 로그인/mediplat 로그인 색상을 CORE 계열 팔레트(네이비/블루/시안/라임)로 정리
  - mediplat 로그인에 병원전용 CORE 로고 적용
- 상담일지 관리 UX 개선:
  - 톱니 버튼 없이 좌측 드래그 핸들(⋮⋮)로 즉시 순서 변경 가능하도록 변경
  - 체크박스 표시를 제거하고 선택 항목 하이라이트 방식으로 변경
  - `순서저장` 버튼 항상 노출

진행중인 작업:

- 없음 (현재 로컬 변경분은 이번 커밋으로 정리 예정)

작업해야 할 내용 (TODO):

- 병실현황판 단독 사용 기관과 카운셀맨+병실현황판 동시 사용 기관의 정책/운영 가이드를 분리 문서화
- 연동 토글 ON/OFF 시 mediplat 진입, csm popup 토큰 진입, 일반 room-board 진입에 대한 회귀 시나리오 고정
- 전용계정 보안정책 보강: 비밀번호 정책, 변경 이력, 잠금/해제 정책 정리
- 전용계정 운영기능 보강: 마지막 로그인 시각, 실패 횟수, 계정 잠금 상태 표시
- 병실현황판 성능 개선: iframe 새로고침/로딩 전략(자동 갱신 주기 포함) 정리
