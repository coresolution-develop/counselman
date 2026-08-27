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

# ⚠️ 아래 둘은 2026-08-27 대조에서 **이 표에 빠져 있던 것**이다.
#    미설정이어도 **기동은 된다** — @ConfigurationProperties 는 미해결 플레이스홀더를
#    조용히 문자열로 남긴다. clientId 가 "${KAKAO_CLIENT_ID}" 인 채로 뜨고,
#    **사용자가 카카오 로그인을 눌러야** 깨진 것이 드러난다.
export KAKAO_CLIENT_ID='<카카오 앱 REST API 키>'
export KAKAO_CLIENT_SECRET='<카카오 앱 client secret>'
```

### 3.1 이 표가 완전한지 확인하는 방법

손으로 관리하면 또 빠진다. **코드가 목록을 들고 있게** 했다.

```bash
./gradlew test --tests '*ContextWiringTest' --console=plain
```

`ContextWiringTest` 는 dev 프로파일로 컨텍스트를 띄우고 **해석되지 않은 플레이스홀더가
남았는지** 검사한다. 새 필수 env 가 생기면 이 테스트가 먼저 터진다.

⚠️ **다만 dev 프로파일 기준이다.** prod 전용 값(`BIZPPURIO_PROD_*`)은 프로파일이
달라 이 테스트가 직접 확인하지 못한다. prod 프로파일의 필수 env 는 아래로 뽑는다.

```bash
grep -hoE '\$\{[A-Z0-9_]+\}' \
  src/main/resources/application.properties \
  src/main/resources/application-prod.properties \
  | tr -d '${}' | sort -u
```

기본값이 있는 것(`${X:기본값}`)은 위 정규식에 안 걸린다 — **걸리는 것이 곧 필수**다.

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

---

## 9. CSM-3 단가 연동 배포 순서

작성일: 2026-08-26

csm 단가를 플랫폼(MediCast)이 배포하는 구조로 바꾸면서 **순서 제약**이 생겼다.
코드 주석에도 같은 내용이 있다 — `PlatformPricePoller` 클래스 주석.

### 9.0 배포 묶음 — CSM-3 은 **연동을 끈 채로 먼저** 나간다 (확정 2026-08-26)

CSM-3(단가 pull)과 CSM-2(단가 화면 읽기 전용화)는 **공지는 한 번**, **배포는 두 번**으로 간다.

| | 배포 | 운영자가 보는 변화 | 공지 |
|---|---|---|---|
| 1차 | **CSM-3 코드만.** `CSM_PRICE_PLATFORM_BASE_URL` 미설정 | **없음** — 단가 동작이 배포 전과 동일 | 안 함 |
| 2차 | CSM-2 코드 + 1차에서 뺀 URL 주입 | 단가 화면이 읽기 전용이 된다 | **여기서 한 번** |

**왜 나누나.** 배포를 나눠야 문제 시 원인이 분리된다. 그리고 1차는
`@Autowired(required = false)` + URL 미설정 덕에 **기존 경로 그대로 동작**하므로
공지할 것이 없다 (`PlatformPriceFallbackIntegrationTest.캐시_빈이_없어도_기존_경로로_동작한다`).

**왜 공지는 한 번인가.** 1차와 2차 사이에 CSM-3 만 켜면 *"단가를 고쳐도 5분 뒤 되돌아가는"*
중간 상태가 생긴다. 그걸 공지하면 곧 무의미해질 설명을 운영자에게 시키는 것이다.
병원 담당자에게 중요한 변화는 "요금 계산 개선"이 아니라 **"단가를 여기서 못 고치게 된다"** 이고,
그건 CSM-2 가 들어가야 완성된다.

> ⛔ **CSM-2 의 화면 변경을 1차 배포본에 담지 않는다.**
> URL 로 끄는 것이 이 방식의 요점인데, **화면 변경은 URL 과 무관하게 바로 보인다.**
> 담으면 "배포했지만 아무것도 안 변한다" 가 성립하지 않고 중간 상태가 그대로 노출된다.
> 재시작이 한 번 더 드는 것은 감수한다.

아래 9.1~9.6 의 "순서" 는 **2차 배포(URL 주입)** 시점에 적용된다.

### 9.1 왜 순서가 중요한가

단가는 3단계로 폴백한다.

| 단계 | 출처 | 오염 가능성 |
|---|---|---|
| 1 | `csm.platform_price_cache` | 플랫폼이 덮어씀 |
| 2 | `csm.inst_data_cs` | **플랫폼이 덮어씀** ← 여기가 함정 |
| 3 | `application.properties` 폴백값 | 안전 |

`PlatformPriceCache.store()` 는 캐시 테이블뿐 아니라 **기존 `inst_data_cs` 컬럼도
덮어쓴다** (기존 화면·조회가 그 컬럼을 읽기 때문이다).

즉 **플랫폼에 잘못된 단가가 있는 상태로 연동을 켜면 1단계와 2단계가 동시에 오염된다.**
연동을 다시 꺼도 2단계에 잘못된 값이 남아 그대로 청구된다. 되돌리려면 두 곳을 다 고쳐야 한다.

### 9.2 순서

| 순서 | 작업 | 성공 판정 |
|---|---|---|
| 1 | **csm 배포** (`CSM_PRICE_PLATFORM_BASE_URL` **미설정**) | 기동 로그에 `PLATFORM_CACHE_DISABLED` DEBUG 만. WARN 없음 |
| 2 | `/rate` 화면 확인 | 배포 전과 **금액이 동일** (전 단위 수정은 표시를 바꾸지 않는다) |
| 3 | **플랫폼 단가 API 가동 + 기관별 단가 시드** | 아래 9.3 대조 |
| 4 | csm 에 `CSM_PRICE_PLATFORM_BASE_URL`·`CSM_PRICE_PLATFORM_API_KEY` 주입 후 재시작 | 30초 뒤 첫 폴링. 로그에 실패 WARN 없음 |
| 5 | 폴링 반영 확인 | 아래 9.4 |

### 9.3 3단계 — 연동을 켜기 전에 반드시 대조

플랫폼 API 를 **직접 호출해서** 기관코드와 금액을 눈으로 본다.
csm 을 붙이기 전에 하는 것이 요점이다 — 붙인 뒤에는 이미 덮어쓴 뒤다.

`PlatformPriceClient.fetch()` 가 실제로 부르는 것과 같은 요청이다.

```bash
curl -s -H "X-Internal-Api-Key: $CSM_PRICE_PLATFORM_API_KEY" -H "Accept: application/json" \
  "$CSM_PRICE_PLATFORM_BASE_URL/internal/prices?instCode=COHS" | python3 -m json.tool
```

확인 항목:

- `instCode` 가 **대문자 정규형**인가 (`COHS`, `hsop_0001` 같은 값이 아님)
- `unitCostJeon` 이 **전 단위 정수**인가 — `960` 이지 `9.6` 이 아니다
- 운영 기준값과 같은가: SMS `960` / LMS `3000` / MMS `9000`
- 기관이 **빠짐없이** 나오는가 — 빠진 기관은 2단계 폴백으로 남는다(정상)

하나라도 어긋나면 **4단계로 넘어가지 않는다.** 플랫폼에서 먼저 고친다.

### 9.4 5단계 — 반영 확인

```sql
SELECT inst_code, channel, unit_cost_jeon, price_version, received_at
  FROM csm.platform_price_cache ORDER BY inst_code, channel;
```

```sql
-- 미러가 같은 값인지. 두 결과의 금액이 달라야 할 이유가 없다.
SELECT id_col_03, sms_price, lms_price, mms_price, sms_price_version
  FROM csm.inst_data_cs ORDER BY id_col_03;
```

- `received_at` 이 **방금 시각**이면 폴링이 도는 것이다
- `sms_price` 는 **원 단위 문자열**이다 (`9.6`). 전 단위 `960` 이 들어가 있으면 미러가 잘못된 것이다
- `/rate` 화면 금액이 1단계에서 본 값과 **같아야** 한다

### 9.5 비상 절차 — 플랫폼이 잘못된 단가를 배포했을 때

**CSM-2 이후 csm 화면에서는 단가를 고칠 수 없다.** 편집 UI 도 없고
`POST /core/smssetting/priceInsert` 는 410 을 돌려준다. 그래서 이 절차가 유일한 수동 경로다.

#### 9.5.0 먼저 확인 — 정말 비상인가

| 상황 | 조치 |
|---|---|
| 폴링이 실패만 함 (플랫폼 응답 없음) | **그대로 둔다.** 이전 단가로 발송이 계속된다. 설계된 동작이다 |
| 플랫폼 단가가 틀렸는데 **플랫폼이 살아 있음** | **9.5.1 만 한다.** 5분 뒤 자동 정정된다. 아래 SQL 은 쓰지 않는다 |
| 플랫폼 단가가 틀렸는데 **플랫폼이 죽었음** | 9.5.1 이 불가능하다. 9.5.2 로 간다 |

> ⚠️ **플랫폼이 살아 있으면 SQL 을 만지지 않는다.** 고쳐 봐야 다음 폴링(5분)이 덮어쓰고,
> 그 사이 무엇이 진짜 값인지 아무도 모르는 구간이 생긴다.

#### 9.5.1 정상 경로 — 플랫폼에서 고친다

MediCast 관리자 화면에서 단가를 수정한다. csm 은 최대 5분 뒤 자동 반영한다.

확인:

```sql
SELECT inst_code, channel, unit_cost_jeon, price_version, received_at
  FROM csm.platform_price_cache
 WHERE inst_code = 'COHS'
 ORDER BY channel;
```

`received_at` 이 갱신되고 `unit_cost_jeon` 이 고친 값이면 끝이다.
`/core/smssetting` 화면의 "단가 수신" 열에서도 확인된다.

#### 9.5.2 비상 경로 — 플랫폼이 죽었을 때

> ⛔ **순서를 지킨다.** 틀리면 재시작 후 폴링이 다시 덮어쓴다.
> **URL 제거 → 재시작 → SQL 수정** 이다. SQL 을 먼저 고치면 안 된다.

**1단계. 연동을 끈다** (아직 재시작하지 않는다)

운영 환경 파일에서 아래를 지우거나 빈 값으로 둔다.

```bash
CSM_PRICE_PLATFORM_BASE_URL=
```

**2단계. csm 을 재시작한다**

```bash
sudo systemctl restart csm    # 환경에 맞게
```

기동 로그 확인 — 폴링이 멈춘 것을 눈으로 본다.

```bash
journalctl -u csm --since '2 min ago' | grep -i 'price-poll'
# 아무것도 안 나오면 정상이다. 폴러가 조용히 쉰다.
```

**3단계. 오염된 두 곳을 모두 고친다**

폴백은 3단계인데 **플랫폼이 1단계와 2단계를 모두 덮어쓴다.**
`PlatformPriceCache.store()` 가 `platform_price_cache` 와 `inst_data_cs` 를 같이 쓰기 때문이다.
**URL 을 지우는 것만으로는 2단계에 잘못된 값이 남아 그대로 청구된다.**

먼저 **지금 값을 본다.** 되돌릴 목표값을 모르면 고칠 수 없다.

```sql
-- ① 1단계 (캐시). 전 단위 정수다.
SELECT inst_code, channel, unit_cost_jeon, price_version, received_at
  FROM csm.platform_price_cache
 ORDER BY inst_code, channel;

-- ② 2단계 (미러). 원 단위 문자열이다. 9.6 이지 960 이 아니다.
SELECT id_col_03, sms_price, lms_price, mms_price, sms_price_version
  FROM csm.inst_data_cs
 ORDER BY id_col_03;
```

**백업을 먼저 뜬다.** 되돌릴 방법 없이 UPDATE 하지 않는다.

```sql
CREATE TABLE csm.inst_data_cs_bak_20260827 AS SELECT * FROM csm.inst_data_cs;
CREATE TABLE csm.platform_price_cache_bak_20260827 AS SELECT * FROM csm.platform_price_cache;
```

**1단계(캐시)를 비운다.** 고치는 것보다 비우는 것이 낫다 —
비면 폴백이 2단계로 내려가고, 진실이 한 곳(2단계)만 남는다.

```sql
DELETE FROM csm.platform_price_cache WHERE inst_code = 'COHS';
-- 전 기관이면 WHERE 를 빼되, 반드시 위 SELECT 로 대상을 먼저 확인할 것
```

**2단계(미러)를 올바른 값으로 되돌린다.** **원 단위 문자열**이다.

```sql
UPDATE csm.inst_data_cs
   SET sms_price = '9.6',
       lms_price = '30',
       mms_price = '90',
       sms_price_version = NULL
 WHERE id_col_03 = 'COHS';
```

> `sms_price_version = NULL` 로 두는 이유: 이 값은 "플랫폼에서 받은 버전" 이라는 뜻이다.
> 손으로 고친 값에 버전을 남겨 두면 **플랫폼이 배포한 값으로 오인**된다.

**4단계. 확인한다**

```sql
SELECT id_col_03, sms_price, lms_price, mms_price, sms_price_version
  FROM csm.inst_data_cs ORDER BY id_col_03;

SELECT COUNT(*) FROM csm.platform_price_cache;   -- 비웠으면 0
```

`/rate` 화면(기관 로그인)에서 금액이 의도한 값인지 본다.
`/core/smssetting` 은 전 기관 "수신 이력 없음" 으로 나온다 — 연동을 껐으니 맞다.

**5단계. 발송으로 최종 확인**

단가는 발송 시점에 결정된다. 문자 1건을 보내고 이력의 `cost` 를 본다.

```sql
SELECT refkey, message_type, cost, billable, reg_date
  FROM csm.transmission_history_COHS
 ORDER BY reg_date DESC LIMIT 3;
```

`cost` 는 **전 단위 정수**다. SMS 1건이면 `960` 이어야 한다 (`9.6` 이 아니다).

#### 9.5.3 복구 — 연동을 다시 켠다

플랫폼이 살아나고 **단가가 올바른 것을 9.3 대조로 확인한 뒤에** 켠다.

1. 플랫폼에서 단가 확인 (§9.3 의 `curl`)
2. `CSM_PRICE_PLATFORM_BASE_URL` 복원
3. csm 재시작 → 30초 뒤 첫 폴링
4. §9.4 로 반영 확인

> 켜는 순간 폴링이 `inst_data_cs` 를 **다시 덮어쓴다.** 그래서 2번 전에 1번이 필수다.

#### 9.5.4 백업 테이블 정리

복구 확인 후 지운다. 남겨 두면 다음 사고 때 어느 것이 언제 것인지 헷갈린다.

```sql
DROP TABLE csm.inst_data_cs_bak_20260827;
DROP TABLE csm.platform_price_cache_bak_20260827;
```

### 9.6 중단 기준

- 9.3 대조에서 기관코드가 소문자·혼합 표기로 나옴
- 9.3 에서 금액이 전 단위가 아니라 원 단위로 옴
- 4단계 후 `/rate` 금액이 1단계와 다름
