# PROD 배포 전 체크리스트

작성일: 2026-05-08  
대상일: 2026-05-11 시연 및 CounselMan -> MediPlat 전환

## 1. 목적

PROD 배포 전 아래 네 가지를 먼저 검증한다.

1. CSM이 반드시 `prod` 프로파일로 기동되는지 확인
2. MediPlat -> CounselMan SSO 진입이 실제 운영 URL과 동일한 값으로 동작하는지 확인
3. 운영 DB, SMS, 파일 저장소 같은 외부 의존성이 준비되었는지 확인
4. 패키징/기동/스모크 테스트 결과를 배포 전에 남김

현재 운영 서버에서 이미 사용 중인 기존 프로젝트와 이 저장소는 별개다. 따라서 이 문서의 점검은
기존 운영 서비스 디렉터리에서 실행하지 않고, 별도 checkout/workspace 또는 별도 검증 서버에서 수행한다.
기존 운영 프로세스, WAR/JAR, systemd, Tomcat webapps를 덮어쓰지 않는다.

## 2. 우선순위

### P0 - 배포 전 반드시 확인

| 항목 | 확인값 | 실패 시 영향 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | dev 설정으로 기동될 수 있음 |
| `LOGIN_AES_KEY` | 16자 이상, 기존 데이터와 동일 키 | 로그인/암복호화 실패 |
| `MEDIPLAT_SSO_SHARED_SECRET` | 운영 secret | CSM SSO 검증 실패 |
| `COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET` | `MEDIPLAT_SSO_SHARED_SECRET`와 동일 | MediPlat에서 생성한 SSO 링크가 403 처리됨 |
| `COUNSELMAN_BASE_URL` | 운영 CSM URL, 예: `https://.../csm` | 브라우저가 localhost/dev로 이동 |
| `MEDIPLAT_PLATFORM_BASE_URL` | 운영 MediPlat URL | 로그아웃/iframe 허용 출처 오류 |
| Bizppurio IP whitelist | 운영 서버 공인 IP 등록 | 문자 발송 실패, `3010 IP blocking` |
| `/mnt/csm-audio` | 앱 실행 계정 쓰기 가능 | 녹취 저장 실패 |
| `/mnt/csm-counsel-files` | 앱 실행 계정 쓰기 가능 | 파일 업로드 실패 |

### P1 - 시연 전 확인

| 항목 | 확인 방법 | 실패 시 영향 |
|---|---|---|
| DB 백업 | 운영 DB 백업본 생성 확인 | 기동 중 schema 보정 실패 시 복구 어려움 |
| CSM/MediPlat 산출물 | `./gradlew packageProdDeploy --console=plain` | 배포 산출물 누락 |
| CSM 테스트 | `./gradlew test` | 현재 일부 테스트 실패, 회귀 위험 |
| schema bootstrap 로그 | 기동 로그에서 `[schema-bootstrap]`, `[schema-migrate-local]` 경고 확인 | 기관/사용자/권한 동기화 누락 |
| SMS 토큰 warmup | 기동 로그에서 Bizppurio token warmup 확인 | 문자 기능 장애 |

## 3. 서버 환경변수 대조표

운영 서버에서 아래 값을 설정한다. secret 값은 문서나 메신저에 평문으로 남기지 않는다.

```bash
export SPRING_PROFILES_ACTIVE=prod
export LOGIN_AES_KEY='<운영 AES 키>'
export MEDIPLAT_SSO_SHARED_SECRET='<운영 SSO secret>'
export COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET='<운영 SSO secret>'
export MEDIPLAT_PLATFORM_BASE_URL='https://<mediplat-domain>'
export COUNSELMAN_BASE_URL='https://<counselman-domain>/csm'
export PLATFORM_RUNTIME_ENV='PROD'

export SPRING_DATASOURCE_URL='jdbc:mysql://<host>:3306/csm?serverTimezone=Asia/Seoul&useSSL=true&characterEncoding=UTF-8'
export SPRING_DATASOURCE_USERNAME='<운영 DB 사용자>'
export SPRING_DATASOURCE_PASSWORD='<운영 DB 비밀번호>'

export MEDIPLAT_DATASOURCE_URL="${SPRING_DATASOURCE_URL}"
export MEDIPLAT_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}"
export MEDIPLAT_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}"

export PLATFORM_COUNSELMAN_DATASOURCE_URL="${SPRING_DATASOURCE_URL}"
export PLATFORM_COUNSELMAN_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}"
export PLATFORM_COUNSELMAN_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}"
export PLATFORM_COUNSELMAN_LOGIN_AES_KEY="${LOGIN_AES_KEY}"

export BIZPPURIO_PROD_ACCOUNT='<운영 Bizppurio account>'
export BIZPPURIO_PROD_USERNAME='<운영 Bizppurio username>'
export BIZPPURIO_PROD_PASSWORD='<운영 Bizppurio password>'
```

## 4. 사전 점검 명령

이 저장소를 별도 경로에 checkout한 뒤, 해당 프로젝트 루트에서 실행한다.
운영 중인 기존 프로젝트 디렉터리에서는 실행하지 않는다.

```bash
./scripts/prod-preflight.sh
./gradlew packageProdDeploy --console=plain
./gradlew test
```

운영 서버에서 같은 장비를 사용해야 한다면 아래 원칙을 지킨다.

- 기존 운영 디렉터리와 다른 경로에 clone
- 기존 Tomcat `webapps`, systemd 서비스 디렉터리, 배포 스크립트는 수정하지 않음
- 기존 서비스 포트와 겹치지 않는 임시 포트 사용
- 운영 DB에 직접 연결하는 기동 테스트는 DB 백업과 명시적 승인 후 진행
- `./gradlew test`는 운영 환경변수가 잡힌 서버에서 실행하지 않음

현재 확인된 상태:

- `./gradlew packageProdDeploy --console=plain`: 성공
- `./gradlew test`: 실패 3건
  - `CsmApplicationTests.contextLoads`: OAuth2 `ClientRegistrationRepository` bean 없음
  - `ChromeNavigationTemplateTest.adminNavigationHighlightsAllAdminSubPages`: 네비게이션 기대 문자열 불일치
  - `CsmAuthServiceTransactionTest.savePledgeTemplate_existingUpdatedRow_returnsId`: expected `7L`, actual `0L`

## 5. 배포 직후 스모크 테스트

### CSM

```bash
curl -I https://<counselman-domain>/csm/login
curl -I https://<counselman-domain>/csm/room-board
```

확인:

- 로그인 페이지 또는 MediPlat 로그인 리다이렉트가 의도대로 동작
- `/csm` context-path가 빠지지 않음
- 500 응답이 없음

### MediPlat

```bash
curl -I https://<mediplat-domain>/login
```

확인:

- 로그인 화면 접근 가능
- 운영 DB 연결 실패 로그 없음

### SSO 흐름

브라우저에서 확인한다.

1. MediPlat 로그인
2. CounselMan 서비스 클릭
3. `/csm/mediplat/sso/entry` 경유
4. 상담리스트 또는 설정된 target 진입
5. 로그아웃
6. MediPlat URL로 복귀

실패 시 우선 확인:

- `MEDIPLAT_SSO_SHARED_SECRET`와 `COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET` 동일 여부
- `COUNSELMAN_BASE_URL`이 localhost/dev가 아닌 운영 URL인지 여부
- 서버 시간 차이가 60초를 넘는지 여부

### SMS

1. 기동 로그에서 token warmup 결과 확인
2. 테스트 수신번호로 SMS 1건 발송
3. 실패 시 `3010 IP blocking` 여부 확인

## 6. 운영 리스크 메모

- `src/main/resources/application-prod.properties`에 운영 DB/SMS 기본값이 직접 들어 있다. 운영에서는 환경변수로 덮어쓴다.
- `mybatis.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl`가 공통 설정에 있다. 운영 로그에 SQL이 과도하게 출력되는지 확인한다.
- `CsmSchemaBootstrapService`는 기동 시 기관별 테이블과 권한 테이블을 보정한다. 첫 운영 기동 전 DB 백업을 선행한다.
- `server.servlet.session.timeout=0`은 세션 만료 정책상 위험할 수 있다. 시연 이후 운영 정책에 맞게 조정한다.
- CORS가 전체 origin pattern을 허용하고 frame options가 disable되어 있다. MediPlat iframe 연동 목적 외 노출 범위를 추후 줄인다.

## 7. 당일 진행 순서

현재 운영 프로젝트와 이 저장소가 별개인 상황에서는 아래 순서로 전환 준비만 한다.

1. 기존 운영 프로젝트 배포 구조 확인
2. 이 저장소를 별도 경로 또는 별도 서버에 checkout
3. 운영 환경변수 대조표 작성
4. `./scripts/prod-preflight.sh`로 설정 누락 확인
5. 별도 빌드 환경에서 `./gradlew packageProdDeploy --console=plain` 산출물 확인
6. 운영 DB 백업 계획 수립
7. 별도 검증 환경에서 CSM/MediPlat/SMS/SSO 스모크 테스트
8. 전환 승인 후에만 기존 운영 배포 절차에 반영

기존 운영 서버에 바로 덮어쓰는 배포는 하지 않는다.

## 8. 같은 서버 병행 검증 배포안

운영 서버 한 대 안에서 기존 운영 프로젝트와 이 프로젝트를 동시에 띄워 검증하려면
Tomcat 포트만 바꾸지 말고, Tomcat 인스턴스, 포트, 배포 경로, DB, 파일 저장소를 함께 분리한다.

### 8.1 권장 구조

| 구분 | 기존 운영 | 신규 검증용 |
|---|---|---|
| CSM Tomcat | 기존 경로/포트 유지 | `/opt/tomcat-csm-next` |
| CSM HTTP port | 기존 값 | `18081` |
| CSM shutdown port | 기존 값 | `18005` |
| CSM AJP port | 기존 값 | `18009` 또는 비활성화 |
| CSM WAR | 기존 `webapps` 유지 | `/opt/tomcat-csm-next/webapps/csm.war` |
| MediPlat service | 기존 서비스 유지 | `mediplat-next.service` |
| MediPlat port | 기존 값 | `18082` |
| DB | 기존 운영 DB | 운영 복제본 또는 검증 DB |
| audio path | 기존 경로 | `/mnt/csm-audio-next` |
| file path | 기존 경로 | `/mnt/csm-counsel-files-next` |

현재 검토 중인 서버 IP는 `115.68.177.207`이다. 도메인/리버스 프록시 전환 전 병행 검증 URL은
`http://115.68.177.207:18081/csm`, `http://115.68.177.207:18082`를 기준으로 한다.

### 8.2 병행 검증용 환경변수 예시

아래 값은 예시다. secret과 DB 비밀번호는 실제 값으로 대체하되 문서에 평문으로 남기지 않는다.

```bash
export SPRING_PROFILES_ACTIVE=prod
export SERVER_PORT=18081
export LOGIN_AES_KEY='<운영과 호환되는 AES 키>'

export MEDIPLAT_SSO_SHARED_SECRET='<검증용 SSO secret>'
export COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET='<검증용 SSO secret>'

export MEDIPLAT_PLATFORM_BASE_URL='http://115.68.177.207:18082'
export COUNSELMAN_BASE_URL='http://115.68.177.207:18081/csm'
export PLATFORM_RUNTIME_ENV='PROD'

export SPRING_DATASOURCE_URL='jdbc:mysql://<clone-db-host>:3306/csm?serverTimezone=Asia/Seoul&useSSL=true&characterEncoding=UTF-8'
export SPRING_DATASOURCE_USERNAME='<검증 DB 사용자>'
export SPRING_DATASOURCE_PASSWORD='<검증 DB 비밀번호>'

export MEDIPLAT_PORT=18082
export MEDIPLAT_DATASOURCE_URL="${SPRING_DATASOURCE_URL}"
export MEDIPLAT_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}"
export MEDIPLAT_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}"

export PLATFORM_COUNSELMAN_DATASOURCE_URL="${SPRING_DATASOURCE_URL}"
export PLATFORM_COUNSELMAN_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}"
export PLATFORM_COUNSELMAN_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}"
export PLATFORM_COUNSELMAN_LOGIN_AES_KEY="${LOGIN_AES_KEY}"

export COUNSEL_AUDIO_BASE_DIR='/mnt/csm-audio-next'
export COUNSEL_FILE_BASE_DIR='/mnt/csm-counsel-files-next'
```

주의:

- WAR를 외부 Tomcat에 배포하는 경우 `server.port`는 Tomcat connector 설정을 따른다. `SERVER_PORT`는 내장 Tomcat 실행 시에만 직접 의미가 있다.
- `server.servlet.context-path=/csm`은 유지한다. 검증 URL은 `http://115.68.177.207:18081/csm` 형태가 된다.
- `MEDIPLAT_PLATFORM_BASE_URL`과 `COUNSELMAN_BASE_URL`은 서로 새 검증 포트를 바라봐야 한다.

### 8.3 새 Tomcat 분리 원칙

새 Tomcat의 `conf/server.xml`에서 기존 운영과 겹치는 포트가 없어야 한다.

```xml
<Server port="18005" shutdown="SHUTDOWN">
  <Service name="Catalina">
    <Connector port="18081" protocol="HTTP/1.1"
               connectionTimeout="20000"
               redirectPort="18443" />

    <!-- AJP를 쓰지 않으면 비활성화 권장 -->
    <!-- <Connector protocol="AJP/1.3" port="18009" redirectPort="18443" /> -->
  </Service>
</Server>
```

기존 운영 Tomcat의 `server.xml`, `webapps`, `logs`, `systemd` 설정은 수정하지 않는다.

### 8.4 systemd 서비스 분리 예시

서비스명도 기존 운영과 다르게 둔다.

```ini
[Unit]
Description=CSM Next Tomcat
After=network.target

[Service]
Type=forking
User=tomcat
Group=tomcat
EnvironmentFile=/etc/csm-next/csm-next.env
Environment=CATALINA_HOME=/opt/tomcat-csm-next
Environment=CATALINA_BASE=/opt/tomcat-csm-next
ExecStart=/opt/tomcat-csm-next/bin/startup.sh
ExecStop=/opt/tomcat-csm-next/bin/shutdown.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

MediPlat도 기존 서비스와 분리한다.

```ini
[Unit]
Description=MediPlat Next
After=network.target

[Service]
User=mediplat
Group=mediplat
EnvironmentFile=/etc/csm-next/mediplat-next.env
ExecStart=/usr/bin/java -jar /opt/mediplat-next/app/mediplat.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

### 8.5 검증 순서

1. 운영 DB 복제본 또는 검증 DB 준비
2. `/mnt/csm-audio-next`, `/mnt/csm-counsel-files-next` 생성 및 앱 계정 쓰기권한 부여
3. 새 Tomcat 경로 생성
4. 새 Tomcat 포트 `18005`, `18081`, `18009`가 기존과 겹치지 않는지 확인
5. `csm-prod.war`를 새 Tomcat `webapps/csm.war`로 배포
6. `mediplat-prod.jar`를 `/opt/mediplat-next/app/mediplat.jar`로 배포
7. `csm-next.service`, `mediplat-next.service` 등록
8. 새 서비스만 기동
9. `http://115.68.177.207:18081/csm/login`, `http://115.68.177.207:18082/login` 확인
10. MediPlat -> CounselMan SSO 확인
11. 문자 발송은 Bizppurio whitelist가 검증 포트 서버 IP 기준으로 준비된 뒤 1건만 확인

### 8.6 중단 기준

아래 상황이면 병행 검증을 멈추고 원인 확인 후 재시도한다.

- 신규 Tomcat 포트가 기존 운영 포트와 충돌
- 신규 서비스가 기존 `webapps` 또는 기존 systemd 서비스를 참조
- 검증용 DB가 아니라 운영 DB에 연결된 상태에서 schema bootstrap이 실행될 예정
- `COUNSELMAN_BASE_URL` 또는 `MEDIPLAT_PLATFORM_BASE_URL`이 기존 운영 URL을 바라봄
- 파일 저장 경로가 기존 운영 경로와 동일함
- Bizppurio 운영 계정으로 대량 발송 가능성이 있음

### 8.7 운영 전환 시점

병행 검증이 통과해도 바로 덮어쓰지 않는다. 운영 전환은 별도 승인 후 아래를 다시 확인한다.

1. 운영 DB 백업
2. 운영 URL 전환 방식 결정
3. 기존 운영 롤백 방법 확보
4. 배포 창구 시간 확정
5. 시연 계정/기관/권한 최종 확인
