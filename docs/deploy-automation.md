# 배포 자동화 구조 + 무중단(Phase 3) 초안

> 작성 맥락: 2026-06-16 운영 서버 점검(ps/ss/systemctl)으로 확인된 **확정 구조** 기록 +
> Phase 3(무중단) **미적용 초안**. 비밀값은 제외하고 구조만 기록한다.
> 기존 `docs/prod-deploy-checklist.md` 의 `/usr/local/tomcat10` 기술은 stale이며, 실제는 아래와 같다.

---

## 1. 현재 확정 구조 (no secrets)

### 1.1 런타임 프로세스 / 포트

| 앱 | 경로 | 포트 | 비고 |
|---|---|---|---|
| **csm (운영)** | 전용 Tomcat `CATALINA_BASE=/opt/csm-next/tomcat` (Java 17) | HTTP **18081**, shutdown **18005**(127.0.0.1) | systemd `csm-next.service`, `SPRING_PROFILES_ACTIVE=prod`, **인스턴스 1개** |
| mediplat | `/opt/csm-next/app/mediplat.jar` | **18082** | systemd |
| 레거시 Tomcat | `CATALINA_BASE=/usr/local/tomcat` (구 JVM 플래그) | **AJP 8009**(127.0.0.1) | 레거시 ROOT.war, `/api/external/*` 전용 |
| formflow | `/opt/formflow/formflow.jar` | 9000 | 무관 앱 |

### 1.2 systemd 서비스

`csm-next.service` (확인됨):
- `Type=simple`, `ExecStart=/opt/csm-next/tomcat/bin/catalina.sh run`, `ExecStop=… stop`
- `Restart=on-failure`, `RestartSec=10`, `TimeoutStopSec=60`, `SuccessExitStatus=143`
- `EnvironmentFile=/opt/csm-next/env/csm-next.env`

### 1.3 타이머 4개

| 시각(KST) | 유닛 | 동작 |
|---|---|---|
| 02:30 / 12:30 / 14:30 | `deploy-nightly.service` (oneshot) | `/opt/csm-next/deploy/scripts/deploy-nightly.sh` 실행 (staging+marker 있을 때만 배포) |
| 03:00 | `csm-next-restart.service` | `systemctl restart csm-next` + `systemctl reload httpd` (무조건) |

`deploy-nightly` 환경변수(확정):
```
STAGING_DIR=/opt/csm-next/deploy/staging
ARCHIVE_DIR=/opt/csm-next/deploy/archive
TOMCAT_WEBAPPS=/opt/csm-next/tomcat/webapps
CSM_SERVICE=csm-next
```

> **현 다운타임 원천:** 03:00 은 **매일 무조건** csm-next 재시작 → JVM 완전 정지/기동 동안 httpd→18081 502/503 + (현재) 인메모리 세션 소실. 02:30/12:30/14:30 은 staged 배포가 있을 때만 추가 재시작.
> **Phase 2(JDBC) 효과:** 위 모든 재시작에서 **세션 소실 제거**. **Phase 3/4** 가 502/503(트래픽 공백)을 제거.

### 1.4 프록시 / 도메인

- 공개 URL: `https://csm.sosyge.net/csm`
- **HTTPS 종단은 외부**(앞단). httpd는 **평문으로 18081 프록시**.
- 매핑(문서 TODO 기준, httpd.conf 실측 대기): `/csm/*`→18081, `/resources/*`→18081/csm/, `/api/external/*`→AJP 8009(레거시), `/`→18082(mediplat).

### 1.5 정합성 확인

- csm-next 는 `SPRING_PROFILES_ACTIVE=prod` → 우리가 **dev/prod 프로파일에만** 건 `spring.session.store-type=jdbc` 가 **운영에 적용됨** ✅. (local 은 인메모리 유지)
- 따라서 운영 배포 전 **prod DB(csm)에 `scripts/spring-session-mysql.sql` 선적용 필수** (없으면 부팅 실패).

### 1.6 메모리 제약 🔴 (아키텍처 결정 근거)

- 총 **5.5GB** / 가용 **2.0GB** / **swap 0**.
- 상시 2-인스턴스 블루-그린은 OOM 위험 → **불채택**.
- Phase 3 는 **A′(배포 시에만 2nd 인스턴스 일시 기동→종료)** 로 진행. 상시 메모리 잠식 없음.

---

## 2. deploy-nightly.sh 동작 요약 (env 기반)

기준 본문: 저장소 `scripts/deploy-nightly.sh` (서버 `/opt/csm-next/deploy/scripts/deploy-nightly.sh` 는 이 사본 + 위 env override). Phase 4 는 이 위에 drain 로직을 얹는다.

- `deploy.ok` 마커 없으면 no-op(exit 0). `flock` 단일 실행.
- staging 의 `csm.war` / `mediplat.jar` / `cancer-treatment.jar` 중 **존재하는 것만** 처리.
- 각 산출물: 라이브 백업(`*.bak-<TS>`) → temp 복사 후 `mv -f`(원자적 교체) → staging→archive 이동.
- 해당 앱 `service` 가 설정돼 있으면 `systemctl restart <service>` (csm 은 `CSM_SERVICE=csm-next`).
- 앱당 백업 `BACKUP_KEEP`(기본 5) 회전. 14일 경과 staging 파일 archive 청소.

> **Phase 4 가 바꿀 지점:** csm 의 `systemctl restart csm-next`(단일 인스턴스 즉시 재시작 = 다운타임)을 **A′ 블루-그린 drain 절차**로 대체.

---

## 3. Phase 3 초안 — A′ 일시적 블루-그린 (⚠️ 미적용 / 검토용)

### 3.1 모델

평상시 **csm-next(jvm1, 18081) 1개만** 가동. 배포 시에만 **csm-next-b(jvm2, 18083)** 를 띄워 무중단 전환 후 다시 내린다.

```
배포 흐름(개략, Phase 4에서 구체화):
1) csm-next-b webapps 에 새 WAR 투입 → csm-next-b 기동(18083)
2) 헬스체크 http://127.0.0.1:18083/csm/login 200 대기
3) balancer-manager: jvm2 활성 + jvm1 drain(처리중 요청만 마무리)
4) csm-next 정지 → 새 WAR 투입 → 재기동(18081)
5) 헬스체크 jvm1 → jvm1 활성 + jvm2 drain → csm-next-b 정지
종료 상태: csm-next(jvm1) 단일 가동 + 새 WAR, csm-next-b 정지(메모리 회수)
```

오버랩(두 인스턴스 동시 가동)은 **순간적**이며 swap 0 환경을 위해 힙을 캡한다(3.5).

### 3.2 포트 매핑 / 충돌 점검표

| 항목 | csm-next (jvm1, 기존) | **csm-next-b (jvm2, 신규 초안)** | 타 프로세스 | 충돌 |
|---|---|---|---|---|
| HTTP connector | 18081 | **18083** | mediplat 18082 / formflow 9000 | 無 |
| Shutdown(SHUTDOWN) | 18005 | **18006** | 레거시 shutdown(추정 8005) | 無(실측 확인) |
| AJP/1.3 | 비활성(추정) | **비활성 권장** | 레거시 8009 | 無(둘 다 미사용) |
| redirectPort | 8443(추정·비바인딩) | **18446**(비바인딩, 구분) | — | 無(소켓 바인딩 아님) |
| jvmRoute | **jvm1 (신규 추가)** | **jvm2** | — | — |

> 실측 확정(2026-06-16, §5 참조): jvmRoute 는 활성 Engine(123줄)에 **없음** → csm-next 에 `jvm1` 신규 추가 필요. HTTP 18081 / redirectPort 8443 / **AJP 활성(≈8009)** / autoDeploy=true unpackWARs=true. csm-next-b 는 AJP 비활성 유지(충돌 회피).

### 3.3 디렉터리 레이아웃 (별도 CATALINA_BASE, binaries 공유)

```
/opt/csm-next/
├── tomcat/            # 기존 csm-next: CATALINA_HOME=CATALINA_BASE (jvm1, 18081)
├── tomcat-b/          # 신규 csm-next-b: 별도 CATALINA_BASE (binaries는 tomcat/ 재사용)
│   ├── conf/          # tomcat/conf 복사 후 server.xml 포트만 변경(18083/18006/jvm2)
│   ├── logs/  temp/  work/
│   ├── webapps/       # 배포 시 csm.war 투입 지점
│   └── bin/setenv.sh  # CATALINA_HOME=/opt/csm-next/tomcat, 힙 캡
├── app/               # mediplat.jar
├── deploy/            # staging / archive / scripts (nightly)
└── env/csm-next.env
```

`/opt/csm-next/tomcat-b/bin/setenv.sh` (초안):
```sh
export CATALINA_HOME=/opt/csm-next/tomcat   # 바이너리 재사용(중복 설치 안 함)
export JAVA_OPTS="$JAVA_OPTS -Xms256m -Xmx512m"   # 확정값(§5.2): 실측 힙~120MB라 512m로 충분
```

### 3.4 csm-next-b systemd 유닛 (초안, on-demand)

```ini
# /etc/systemd/system/csm-next-b.service  (초안 — enable 하지 않음; 배포 스크립트가 start/stop)
[Unit]
Description=CSM Next Tomcat (blue-green B, deploy-time only)
After=network.target

[Service]
Type=simple
User=tomcat
Group=tomcat
EnvironmentFile=/opt/csm-next/env/csm-next.env
Environment=CATALINA_BASE=/opt/csm-next/tomcat-b
Environment=CATALINA_HOME=/opt/csm-next/tomcat
ExecStart=/opt/csm-next/tomcat/bin/catalina.sh run
ExecStop=/opt/csm-next/tomcat/bin/catalina.sh stop
Restart=no
TimeoutStopSec=60
SuccessExitStatus=143
# WantedBy 없음 → 부팅 자동기동 안 함(평상시 메모리 0)
```

### 3.5 메모리 캡 권고 (swap 0)

- 실측: csm-next 힙 실사용 ≈120MB, 메타스페이스 ≈144MB(기본 상한 1.4GB 미사용).
- **확정: 양 인스턴스 각 `-Xmx512m -Xms256m`** → 총 ≈1.6GB(힙 1GB + 비힙 ~0.6GB) < 가용 2.0GB(swap 0) → A′ 오버랩 안전.
- 기존 csm-next 의 setenv.sh 힙 고정은 **별도 작은 변경**으로 분리 적용.

### 3.6 httpd.conf — balancer 전환 diff (⚠️ 미채택 대안 — §5.6 conf-swap 으로 대체)

> 아래는 TODO.md 매핑 + 주신 옵션(`retry=0 timeout=3600 disablereuse=on`) 기준 **초안**. 실제 httpd.conf 라인을 받으면 그에 맞춰 확정.

```diff
- ProxyPass        /csm        http://127.0.0.1:18081/csm  retry=0 timeout=3600 disablereuse=on
- ProxyPassReverse /csm        http://127.0.0.1:18081/csm
- ProxyPass        /resources  http://127.0.0.1:18081/csm/ retry=0 timeout=3600 disablereuse=on
- ProxyPassReverse /resources  http://127.0.0.1:18081/csm/
+ <Proxy balancer://csmcluster>
+     # 평상시 jvm1(18081)만 활성. jvm2(18083)는 배포 시에만 balancer-manager로
+     # 동적 추가/활성화하는 A′(일시적 블루-그린) 모델 → 평소 status=+D(disabled).
+     BalancerMember http://127.0.0.1:18081 route=jvm1 retry=0 timeout=3600 disablereuse=on
+     BalancerMember http://127.0.0.1:18083 route=jvm2 retry=0 timeout=3600 disablereuse=on status=+D
+     # 세션은 Spring Session(JDBC)로 외부화 → 인스턴스 간 공유되므로 sticky 불필요.
+     # route= 는 balancer-manager 식별/제어용. (sticky가 필요하면 ProxySet stickysession=ROUTEID 추가)
+ </Proxy>
+
+ ProxyPass        /csm        balancer://csmcluster/csm  retry=0 timeout=3600 disablereuse=on
+ ProxyPassReverse /csm        balancer://csmcluster/csm
+ ProxyPass        /resources  balancer://csmcluster/csm/ retry=0 timeout=3600 disablereuse=on
+ ProxyPassReverse /resources  balancer://csmcluster/csm/

  # 내부 전용 balancer-manager (배포 스크립트가 멤버 enable/drain 제어)
+ <Location /balancer-manager>
+     SetHandler balancer-manager
+     Require ip 127.0.0.1
+ </Location>
```

- `/api/external/*`→AJP 8009(레거시), `/`→18082(mediplat) 매핑은 **그대로 유지**(이번 변경 범위 아님).
- 전환 후 적용은 `apachectl configtest` 통과 후 `systemctl reload httpd`(graceful)로. 03:00 `csm-next-restart` 의 `reload httpd` 와 정합.

---

## 4. 보류 / 후속

- **Phase 4 (배포 스크립트 무중단화):** 보류. `deploy-nightly.sh` 본문(§2) 위에 drain 기반 블루-그린 로직을 얹어 작성 예정. 03:00 `csm-next-restart` 와의 타이밍 경합 회피 설계 포함.
- **보안(별도 태스크):** `/opt/csm-next/env/csm-next.env` 및 `application-prod.properties` 의 평문 비밀값 회전 + 접근권한 점검. 무중단과 분리, 별도 진행.

---

## 5. 실측 확정값 정정 (2026-06-16 server.xml / httpd / heap / base script)

§3 의 "추정"을 아래 실측으로 확정한다.

### 5.1 server.xml (`/opt/csm-next/tomcat/conf/server.xml`)
- **jvmRoute 비활성**: 활성 Engine(123줄)에 jvmRoute 없음(120–121줄 `jvm1` 은 주석 예시). → **csm-next 에 `jvmRoute="jvm1"` 신규 추가** 필요, csm-next-b 는 `jvm2`.
- HTTP **18081**, **redirectPort 8443**(비바인딩), **AJP Connector 활성**(≈8009 — 레거시와 동일 포트 표기라 운영자 확인 권장; **csm-next-b 는 AJP 비활성**이라 충돌 무관), **autoDeploy="true" unpackWARs="true"**.
- 전환 안정화: **csm-next-b 는 autoDeploy="false"**, (권고) **csm-next 도 전환 기간 autoDeploy="false"** — `mv` 순간 자동 redeploy 가 명시적 restart 와 경합하는 것 방지.

### 5.2 힙 확정 — 각 `-Xmx512m -Xms256m`
- 실측 힙 ≈120MB, 메타스페이스 ≈144MB. 두 인스턴스 합 ≈1.6GB < 가용 2.0GB(swap 0) → A′ 오버랩 안전.
- csm-next 힙 캡은 별도 작은 변경으로 분리 적용.

### 5.3 기준 스크립트 = 서버 실물
- 운영 기준은 **서버 실물** `/opt/csm-next/deploy/scripts/deploy-nightly.sh` (165줄, sha256 `05eb1f7e…754d0`). 저장소 사본과 다를 수 있음(자동 동기화 없음).
- Phase 4 는 저장소 파일 미수정, **새 파일**로 산출 → 운영자가 서버에서 **비-csm 구간을 실물과 diff** 후 배치.

### 5.4 httpd 실측 라인
- `/csm/` → `127.0.0.1:18081/csm/` (**411줄**), `/resources/` 동일 백엔드(**408줄**), 옵션 `retry=0 timeout=3600 disablereuse=on`.
- §3.6 balancer diff 가 이 라인 기준(멤버 `:18081`·`:18083`, ProxyPass `balancer://csmcluster/csm`·`/csm/`).

### 5.5 Phase 4 산출물 (⚠️ 미적용 / 새 파일, `bash -n` 통과)
- `scripts/lib-csm-bluegreen.sh` — 공유 primitive(balancer-manager enable/drain/disable, 헬스 폴링, b 기동/정지).
- `scripts/deploy-nightly-zdd.sh` — 무중단 야간 배포(csm 만 블루-그린 치환; mediplat/cancer·marker/flock/백업/회전/exit 보존).
- `scripts/csm-next-rolling-restart.sh` — 03:00 재시작 롤링화(코드 변경 없이 공백 제거, httpd reload 보존).
- 적용/배치는 운영자 검토 후. 저장소 파일은 산출물(초안)이며 서버 자동 반영 아님.

### 5.6 라우팅 방식 — balancer-manager → **conf-swap (채택)**

개선 검토 결과 §3.6 의 balancer-manager 방식을 **폐기**하고 conf 심볼릭 링크 스왑 방식을 채택한다.

- **근거:** balancer-manager 제어는 nonce 를 HTML 에서 스크레이핑해야 해 httpd 버전/마크업 변화에 취약. conf-swap 은 `apachectl configtest` + `systemctl reload httpd`(graceful=무중단)만 사용 → 버전 독립·견고. 세션이 JDBC 공유라 sticky 불필요 → **balancer / balancer-manager / jvmRoute 자체가 불필요**(§5.1 의 jvmRoute 신규 추가도 불필요해짐).
- **구성(운영자 적용, 미적용):**
  - `/opt/csm-next/httpd/csm-route-a.conf` — `/csm`·`/resources` → `127.0.0.1:18081/csm/` (옵션 `retry=0 timeout=3600 disablereuse=on`)
  - `/opt/csm-next/httpd/csm-route-b.conf` — 위와 동일하되 **18083**
  - `/opt/csm-next/httpd/csm-route.conf` — 심볼릭 링크(기본 → `csm-route-a.conf`)
  - httpd.conf 의 기존 `/csm`·`/resources` ProxyPass(408·411줄)를 `Include /opt/csm-next/httpd/csm-route.conf` 한 줄로 대체
  - 전환은 스크립트 `route_to` 가 수행: `ln -sfn … && apachectl configtest && systemctl reload httpd` (configtest 실패 시 심볼릭 원복·reload 생략)
  - `/api/external/*`→8009, `/`→18082 매핑은 그대로 유지
- **드레인(개선②):** 고정 sleep 폐기 → `ss` 로 백엔드(`dst 127.0.0.1:<port>`) established 연결 0 폴링(상한 `DRAIN_CAP`=60s, 초과 시 경고 후 진행). `disablereuse=on` 이라 established≈in-flight.
- **선행 인프라 갱신:** balancer/balancer-manager/jvmRoute 제거 → 필요한 것은 ① `tomcat-b` 별도 CATALINA_BASE, ② `csm-next-b.service`(on-demand), ③ 양 setenv `-Xmx512m -Xms256m`, ④ `csm-route-*.conf` 3개 + httpd `Include` 한 줄.

### 5.7 리뷰 반영 — 버그 2 수정 + 위험 2 반영 (스크립트 v3)

- **[버그1] 새 버전 보장:** `copy_war_to_b` 가 b webapps 를 비우고(옛 `csm/`·`csm.war*` 제거) 새 WAR 만 배치 → autoDeploy 가 옛 컨텍스트를 재기동해 옛 버전이 헬스 200 을 통과하던 문제 차단. 추가로 `build.gradle` 에 `springBoot { buildInfo() }` 임베드(`build.time`), `verify_b_version` 가 staged WAR ↔ b 전개본 build.time 대조(불일치=하드 실패, 판정불가=경고·진행).
  - **Actuator `/actuator/info` 미채택 사유:** WAR(외장 Tomcat)는 management 포트 분리가 안 돼 `/csm/actuator/*` 가 공개 커넥터로 노출(정보노출 표면). clean-webapps + 신규 JVM 기동 + 단일 WAR 면 새 버전 기동이 결정적이라, 엔드포인트 없이 파일 기반 build.time 대조로 동등 보장. HTTP 검증을 원하면 Security 로 `/actuator/**` 제한 후 도입 가능(별도 논의).
- **[버그2] 드레인 정확도:** `_active_conns` = `ss -tnp … | grep "${HTTPD_PROC}"` 로 **httpd 프로세스 소유 백엔드 연결만** 카운트(헬스 curl 등 제외). `wait_drained` = 0 도달 또는 **plateau(연속 `DRAIN_STABLE`회 불변)** 시 진행 → 매번 CAP 소진(=60s 후 절단) 방지.
  - **확정 필요(실측 1회):** 운영에서 `sudo ss -tnp state established 'dst 127.0.0.1:18081'` 출력 샘플 → 프로세스명(`httpd` vs `apache2`)·형식에 맞춰 필터 확정.
- **[위험1] reload↔드레인 경쟁:** `route_to` 가 graceful reload 후 `HTTPD_SETTLE`(3s) 대기 → 옛 워커 in-flight 가 드레인 카운트에 잡힌 뒤 진행.
- **[위험2] 첫 실행 수동 리허설:** 02:30 자동 타이머가 첫 실행이 되지 않도록 **업무시간 외 1회 수동 리허설**을 인프라 적용 순서의 선행 단계로 포함(다음 단계).
- 검증: 3개 `bash -n` 통과 + `verify_b_version`(일치/불일치/판정불가)·`wait_drained`(plateau 조기 종료) 기능 테스트 통과.

### 5.8 최종 게이트 3건 확정 (스크립트 v4)

- **게이트1 build.time 비결정성:** 연속 빌드 실측 → `2026-06-16T07:04:01.97Z` vs `…07:04:05.37Z` 상이(`bootBuildInfo` 가 UP-TO-DATE 아님, 매 빌드 재생성). build.time 이 빌드별 고유 → 버전 대조 키로 충분, **대안 키(git hash/sha256) 불필요.**
- **게이트2 clean-webapps 불변식:** `ensure_b_stopped`(`systemctl is-active` + 18083 LISTEN 소멸 확인) 통과 후에만 `rm -rf`. 정지 미확인 시 `copy_war_to_b` 가 클린 없이 `return 1` → 호출부 abort/폴백(러닝 인스턴스 보호). 테스트: 정지=0 / 가동→정지=0 / LISTEN영구=1.
- **게이트3 드레인 최소 관측:** `wait_drained` 가 0 을 **연속 `DRAIN_ZERO_CONFIRM`(2)회** 확인해야 인정(+ `route_to` 의 `HTTPD_SETTLE` 3s 선행). 단일 스퓨리어스 0 은 리셋 → 즉시통과 무력화 방지. 테스트: 즉시0=0확인2회 / 스퓨리어스0=plateau 종료(오통과 없음) / 확정0=0확인.
- **적용 순서 runbook:** [zero-downtime-rollout-runbook.md](zero-downtime-rollout-runbook.md) (단계별 검증·롤백 + 수동 리허설).
