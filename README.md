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

## 4. 권장 환경변수

필수는 아니지만, PC별 환경 차이를 줄이려면 아래 값을 환경변수로 맞추는 것을 권장합니다.

```bash
export SPRING_PROFILES_ACTIVE=local
export LOGIN_AES_KEY='This is key!!!!!'
export MEDIPLAT_SSO_SHARED_SECRET='change-me'
export COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET='change-me'
export MEDIPLAT_PLATFORM_BASE_URL='http://localhost:8082'
export COUNSELMAN_BASE_URL='http://localhost:8081/csm'
export PLATFORM_RUNTIME_ENV='LOCAL'
```

주의:

- `MEDIPLAT_SSO_SHARED_SECRET`
- `COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET`

위 두 값은 동일해야 합니다.

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

## 14. 최근 작업 정리 (GitHub push 이력 + 현재 반영분)

기준:

- 확인일: `2026-04-15`
- 작업 브랜치: `main`
- 원격 최신 커밋: `origin/main = 3fbf1e0`

최근 push 이력 요약:

- `2026-04-14` (`3fbf1e0`, `b2e7ba9`): 공지 팝업 렌더링 보완, 상담예약/공지 기능 및 UI 개선
- `2026-04-14` (`41f69d2`, `e63c14b`, `bef811a`): 환경별 서비스 URL, SSO 회귀 테스트, DB 드라이버 자동 감지
- `2026-04-13` (`f4e3436`, `ee36269`, `f63d995`, `133206a`): mediplat 관리자 고도화, 배포 태스크, 기관 자동 연동
- `2026-04-12` (`947d0b4`): 모듈 기능 관리, 룸보드 설정/가져오기 개선

이번 반영(현재 작업분):

- 상담내역 화면에 상담예약 리스트 통합(상단 고정) 및 우선순위 정렬 적용
- `/notice/popup/next` NPE 보완: `Map.of(...)` 기반 응답을 null-safe `HashMap` 응답으로 변경
- 서비스 페이지를 단일 카드 목록으로 통합하고, 사용 불가 서비스는 버튼만 비활성화(시각적 구분 최소화)
- `ROOM_BOARD` 모듈 비활성화 시 mediplat 병실현황판 노출/접근 조건도 함께 반영
- csm 로그인에서 기관코드/기관명 모두 입력 가능하도록 인증 로직 확장
- csm 좌측 탭에 `병실현황판` 메뉴 추가 및 모바일 병실현황판 상단 메뉴바 구성
- mediplat에 통합 병실현황판 뷰어 페이지 추가(다기관 iframe 뷰 + 모바일 탭 전환)
- mediplat 최고관리자 페이지에 병실현황판 전용계정 관리 추가
- 전용계정 다중 생성/수정 지원 + 아이디 중복 방지(기관 계정/플랫폼 계정 전체 검사)
- mediplat 로그인 분기 개선: 기관코드/기관명 + 아이디 + 비밀번호(기본 로그인), 기관 입력 없이 아이디 + 비밀번호(전용계정 로그인)
- mediplat 로그인 입력값 저장(기관/아이디) 및 비밀번호 입력 포커스 UX 보강

## 15. TODO-List

- 전용계정 보안정책 보강: 비밀번호 정책, 변경 이력, 잠금/해제 정책 정리
- 전용계정 운영기능 보강: 마지막 로그인 시각, 실패 횟수, 계정 잠금 상태 표시
- 통합 병실현황판 UX 개선: 기관 수가 많을 때 모바일 탭 스크롤/검색 UX 추가
- 병실현황판 성능 개선: iframe 새로고침/로딩 전략(자동 갱신 주기 포함) 정리
- 회귀 테스트 확장: 로그인 분기(기관계정/전용계정), 기능 비활성화(ROOM_BOARD), 권한 노출 케이스 자동화
- 운영 문서 정리: 전용계정 생성/권한 범위 설정/장애 대응 절차를 관리자 매뉴얼에 추가
