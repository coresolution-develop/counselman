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
- `MEDIPLAT_DATASOURCE_*`
- `login.aes-key`
- `mediplat.sso.shared-secret`
- `platform.counselman.datasource.*`
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

## 11. 병실현황판 기능 점검

1. `http://localhost:8081/csm/admin/room-board` 접속
2. 병실 기준정보 등록
3. EMR 복사 데이터 붙여넣기
4. `미리보기` 확인
5. `가져오기 저장`
6. `http://localhost:8081/csm/room-board`에서 결과 확인
7. 입원상담 화면에서 `병실현황판 보기` 버튼으로 병실 선택 확인

## 12. 참고 사항

- `mediplat` 플랫폼 설정 데이터는 기본적으로 공유 MySQL의 `mp_*` 테이블에 저장됩니다.
- 다른 저장소를 쓰고 싶으면 `MEDIPLAT_DATASOURCE_URL`, `MEDIPLAT_DATASOURCE_USERNAME`, `MEDIPLAT_DATASOURCE_PASSWORD`로 분리할 수 있습니다.
- 서버용 설정(`nginx`, `systemd`, `Tomcat`, `SSL 인증서`)은 로컬 실행에 포함되지 않습니다.
- 다른 PC에서 실행 시에도 DB 접속 정보와 SSO secret 값만 맞으면 동일하게 사용할 수 있습니다.
