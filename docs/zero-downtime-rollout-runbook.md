# 무중단 배포 인프라 적용 순서 (Runbook) — ⚠️ DRAFT / 미적용

> csm A′(일시적 블루-그린, conf-swap) 무중단 체계를 운영에 올리는 절차.
> **모든 단계는 운영자가 검토 후 수동 적용**한다. 각 단계는 독립적으로 **롤백 가능**하며,
> 앞 단계까지는 기존 동작에 영향이 없도록(behavior-neutral) 설계했다.
> 확정 토폴로지: a=`csm-next`(18081, `/opt/csm-next/tomcat`), httpd 평문 프록시,
> 세션 Spring Session(JDBC) 공유. 상세는 [deploy-automation.md](deploy-automation.md).

## 롤아웃 분할 (연속 적용 금지)
P0~Step6 을 한 번에 적용하지 않는다. 아래로 쪼개고, 각 묶음 사이에 관측을 둔다.

1. **본문 검토** (이 문서 확정)
2. **P0 단독** — 세션 외부화(DDL + 첫 JDBC WAR + 로그인/세션 검증). **이것만으로 03:00·배포 시 강제 로그아웃이 사라진다.**
3. **며칠 안정 관측** — P0 이상 없음 확인 후 진행
4. **Step1~5** — 무중단 인프라 + 수동 리허설(트래픽 공백 제거 준비)
5. **Step6** — 자동 타이머 연결(무중단 자동화 on)

> 무중단(트래픽 공백 제거)은 P0 의 세션 효과와 독립이며 급하지 않다. P0 안정화를 우선한다.

## 표기
- `a` = 기존 운영 인스턴스 `csm-next`(HTTP 18081, shutdown 18005)
- `b` = 신규 임시 인스턴스 `csm-next-b`(HTTP 18083, shutdown 18006) — 배포 시에만 기동
- 산출물 스크립트: `scripts/lib-csm-bluegreen.sh`, `deploy-nightly-zdd.sh`, `csm-next-rolling-restart.sh`

## 전역 킬스위치 (언제든 단일-a 로 복귀)
```bash
ln -sfn /opt/csm-next/httpd/csm-route-a.conf /opt/csm-next/httpd/csm-route.conf
apachectl configtest && systemctl reload httpd     # 트래픽 a 로
systemctl stop csm-next-b 2>/dev/null || true      # b 내림
systemctl is-active csm-next || systemctl restart csm-next
```

---

## P0. 세션 외부화 단독 적용·검증 (무중단 인프라보다 먼저, 업무시간 외)

> 이전엔 인메모리였다. 이 단계가 **세션 JDBC 의 첫 실가동**이며, 무중단 스크립트는 아직
> 연결 안 됨 → **기존 배포 방식 + 업무시간 외 + 직접 관찰**로 진행한다.
> 이 단계만으로 03:00·배포 시 강제 로그아웃이 사라진다.

### P0.1 DDL 대상 DB 재확인 (엉뚱한 DB 방지)
csm-next 가 **실제로 연결하는 DB** 에 DDL 을 넣어야 한다(dev/prod DB 호스트가 다를 수 있어, 틀리면 부팅 실패).
```bash
# (a) env 의 datasource 확인 (없으면 application-prod 기본값 csm.sosyge.net:3306/csm 적용)
sudo grep -E 'SPRING_DATASOURCE_(URL|USERNAME)' /opt/csm-next/env/csm-next.env \
  || echo "env 미설정 → prod 기본값(csm.sosyge.net:3306/csm)"
# (b) 실가동 프로세스의 3306 목적지 교차확인
PID=$(systemctl show -p MainPID --value csm-next); sudo ss -tnp 2>/dev/null | grep ':3306' | grep "pid=${PID}"
# (c) 확인된 host/db 가 운영 csm DB 인지 기존 테이블로 확증
mysql -h <host> -u <user> -p <db> -e "SHOW TABLES LIKE 'user_data%';"
```
**판정:** env `SPRING_DATASOURCE_URL` 호스트/DB == ss 목적지 == 기존 csm 테이블 존재 — 셋 일치해야 진행.

### P0.2 세션 DDL 적용 (운영 DB 첫 쓰기)
**선행:** 정기 백업 존재 확인 + (가능하면) 적용 직전 스냅샷.
```bash
mysqldump -h <host> -u <user> -p <db> > /tmp/csm-pre-session-ddl-$(date +%Y%m%d-%H%M%S).sql
```
**적용 (P0.1 결과 검토 + 실행 지시 후):**
```bash
mysql -h <확인된 host> -u <user> -p <db> < scripts/spring-session-mysql.sql
mysql -h <host> -u <user> -p <db> -e "SHOW TABLES LIKE 'SPRING_SESSION%';"   # 2개(테이블/속성) 확인
```
**롤백:** 이 시점 운영은 **아직 인메모리 옛 WAR** → **조치 불필요**. 빈 `SPRING_SESSION` 테이블은 무해하게 잔존(원하면 `DROP TABLE SPRING_SESSION_ATTRIBUTES, SPRING_SESSION;`).
> ※ '설정에서 `spring.session.*` 제거 + 재배포' 롤백은 **P0.4(첫 jdbc WAR) 이후**에만 의미가 있다(그 전엔 운영이 테이블을 보지 않음). 긴박한 순간 혼동 방지를 위해 P0.2 ↔ P0.4 롤백을 분리한다.

### P0.3 buildInfo+세션설정 WAR 빌드·검증 + 스크립트 배치
**빌드 (첫 배포는 통제된 수동 권장):**
```bash
./gradlew packageProdDeploy        # → build/deploy/prod/csm-prod.war  (CI: prod 브랜치 push 도 동일 산출)
```
**WAR 내용 검증 — 소스 변경이 산출물에 실제로 담겼는지 첫 실전 전 1회 못 박기:**
```bash
WAR=build/deploy/prod/csm-prod.war
unzip -p "$WAR" WEB-INF/classes/application-prod.properties     | grep -n 'spring.session.store-type=jdbc'  # 있어야 함
unzip -p "$WAR" WEB-INF/classes/META-INF/build-info.properties | grep -n 'build.time'                      # 있어야 함
unzip -l "$WAR" | grep -i 'spring-session-jdbc'                                                             # WEB-INF/lib 에 존재
```
> 로컬 사전검증 완료(2026-06-16): `store-type=jdbc`·`initialize-schema=never`·`build.time`·`spring-session-jdbc-3.4.5.jar` 모두 산출물에 포함 확인. 운영 빌드에서도 위 3줄이 비어 있지 않아야 P0.4 진행.

**스크립트 배치 (미사용 — Step6 전):**
```bash
sudo cp scripts/{lib-csm-bluegreen.sh,deploy-nightly-zdd.sh,csm-next-rolling-restart.sh} \
        /opt/csm-next/deploy/scripts/ && sudo chmod +x /opt/csm-next/deploy/scripts/*.sh
```
`bash -n` 3개 통과 확인. **롤백:** 파일 삭제(미참조 무영향).

### P0.4 첫 JDBC WAR 배포 — 기존 방식 + 업무시간 외 (세션 외부화 첫 실전)
> 무중단 스크립트 없이 **기존 절차**(staging+`deploy.ok` 야간 스크립트, 또는 수동 scp + restart)로.
> **타이밍 주의:** 야간 타이머(02:30·12:30·14:30 deploy / 03:00 restart)와 겹치지 않는 시간에 진행하고,
> 시작 전 `ls /opt/csm-next/deploy/staging/` 로 **`deploy.ok` 마커가 없는지** 확인(있으면 nightly 자동배포가 수동 작업과 충돌). 의도치 않은 csm.war 가 staging 에 남아있지 않은지도 함께 확인.
```bash
sudo cp -a /opt/csm-next/tomcat/webapps/csm.war /opt/csm-next/tomcat/webapps/csm.war.bak-$(date +%Y%m%d-%H%M%S)
sudo cp build/deploy/prod/csm-prod.war /opt/csm-next/tomcat/webapps/csm.war
sudo systemctl restart csm-next
```
**검증 체크리스트 (반드시 직접 확인):**
1. 기동/첫로그인 로그 관찰(`journalctl -u csm-next -f`) — 특히 `NotSerializableException`/`SerializationException`(세션 직렬화), `Table 'csm.SPRING_SESSION' doesn't exist`(DB 오인) 없을 것. `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18081/csm/login` = 200/302.
2. **로그인 성공** → 브라우저 세션 확보.
3. **세션 유지(핵심):** 로그인 상태에서 `sudo systemctl restart csm-next` 1회 더 → 재기동 후에도 **로그아웃 없이** 동일 세션 유지(인메모리였다면 튕겼을 것).
4. **DB 행 생성:** `SELECT COUNT(*) FROM SPRING_SESSION;` 가 로그인 시 증가.
5. (선택) 30분 idle 후 만료. ※ 폴링으로 idle 만료가 약화됨은 기보고, 절대캡은 미적용 확정.

**롤백:** `spring.session.*` 제거 WAR 또는 직전 `csm.war.bak-*` 로 복원 후 `systemctl restart csm-next`(인메모리 복귀).

### P0.5 며칠 안정 관측
로그인/세션/03:00 재기동 시 로그아웃 없음을 며칠 확인한 뒤에만 **Step1** 로 진행.

---

## Step 1. tomcat-b 별도 CATALINA_BASE

**작업**
```bash
sudo mkdir -p /opt/csm-next/tomcat-b/{conf,logs,temp,work,webapps,bin}
sudo cp -a /opt/csm-next/tomcat/conf/* /opt/csm-next/tomcat-b/conf/
# server.xml 포트/모드만 수정 (아래 값으로)
```
`/opt/csm-next/tomcat-b/conf/server.xml` 핵심:
```xml
<Server port="18006" shutdown="SHUTDOWN">
  <Service name="Catalina">
    <Connector port="18083" protocol="HTTP/1.1" connectionTimeout="20000" redirectPort="18446"/>
    <!-- AJP 비활성(httpd 는 mod_proxy HTTP). 레거시 8009 와 무관 -->
    <Engine name="Catalina" defaultHost="localhost">          <!-- jvmRoute 불필요(conf-swap) -->
      <Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="false"/>
    </Engine>
  </Service>
</Server>
```
**검증**
```bash
CATALINA_BASE=/opt/csm-next/tomcat-b CATALINA_HOME=/opt/csm-next/tomcat \
  /opt/csm-next/tomcat/bin/catalina.sh configtest   # server.xml 문법 OK
sudo ss -ltn | grep -E ':(18083|18006)'             # 아직 미기동 → 비어 있어야 정상
```
**롤백:** `sudo rm -rf /opt/csm-next/tomcat-b` (미참조 → 무영향)

---

## Step 2. csm-next-b.service (on-demand)

**작업** — `/etc/systemd/system/csm-next-b.service`
```ini
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
```bash
sudo systemctl daemon-reload      # enable 하지 않는다(on-demand)
```
**검증** (현재 WAR 로 1회 기동 테스트 → 정지)
```bash
sudo cp -a /opt/csm-next/tomcat/webapps/csm.war /opt/csm-next/tomcat-b/webapps/csm.war
sudo systemctl start csm-next-b
sleep 8; curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18083/csm/login   # 200 기대
sudo systemctl stop csm-next-b
sudo rm -rf /opt/csm-next/tomcat-b/webapps/csm /opt/csm-next/tomcat-b/webapps/csm.war
```
**롤백:** `systemctl stop csm-next-b; systemctl disable csm-next-b 2>/dev/null; rm /etc/systemd/system/csm-next-b.service; systemctl daemon-reload`

---

## Step 3. 힙 캡 setenv (-Xmx512m -Xms256m, 양 인스턴스)

**작업**
```bash
# b
printf 'export CATALINA_HOME=/opt/csm-next/tomcat\nexport JAVA_OPTS="$JAVA_OPTS -Xms256m -Xmx512m"\n' \
  | sudo tee /opt/csm-next/tomcat-b/bin/setenv.sh >/dev/null
sudo chmod +x /opt/csm-next/tomcat-b/bin/setenv.sh
# a (별도 작은 변경 — 적용 효과는 다음 csm-next 재기동 시)
printf 'export JAVA_OPTS="$JAVA_OPTS -Xms256m -Xmx512m"\n' \
  | sudo tee -a /opt/csm-next/tomcat/bin/setenv.sh >/dev/null
```
> a 의 힙 캡은 **재기동 때 반영**된다. 힙 변경은 **롤백에도 재기동이 드는 유일한 단계**라 무중단 흐름에 투입하기 전 **단독 선검증**한다.

**검증 (a 단독 선검증 — 업무시간 외, 무중단 흐름 투입 전):**
```bash
sudo systemctl restart csm-next            # 짧은 공백(업무시간 외; 아직 무중단 자동화 전이라 허용)
sleep 8; curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18081/csm/login   # 200
PID=$(systemctl show -p MainPID --value csm-next)
sudo jcmd "$PID" VM.flags | tr ' ' '\n' | grep -i MaxHeapSize    # 512m 환산값(536870912) 확인
free -m                                     # 가용 여유(오버랩 대비; 두 인스턴스 합 ≈1.6GB < 2.0GB)
```
정상(200 + 512m + 메모리 여유) 확인 후에만 Step4 이후 무중단 흐름에서 a 를 재기동 대상으로 사용.
**롤백:** setenv.sh 의 해당 라인 제거(b 는 파일 삭제) → `sudo systemctl restart csm-next`(롤백도 재기동 필요 → 그래서 선검증).

---

## Step 4. csm-route conf + httpd Include (behavior-neutral)

**작업**
```bash
sudo mkdir -p /opt/csm-next/httpd
# a 라우팅(기존과 동일 동작)
sudo tee /opt/csm-next/httpd/csm-route-a.conf >/dev/null <<'EOF'
ProxyPass        /csm        http://127.0.0.1:18081/csm/ retry=0 timeout=3600 disablereuse=on
ProxyPassReverse /csm        http://127.0.0.1:18081/csm/
ProxyPass        /resources  http://127.0.0.1:18081/csm/ retry=0 timeout=3600 disablereuse=on
ProxyPassReverse /resources  http://127.0.0.1:18081/csm/
EOF
# b 라우팅(18083)
sudo sed 's/18081/18083/g' /opt/csm-next/httpd/csm-route-a.conf \
  | sudo tee /opt/csm-next/httpd/csm-route-b.conf >/dev/null
# 평상시 a
sudo ln -sfn /opt/csm-next/httpd/csm-route-a.conf /opt/csm-next/httpd/csm-route.conf
# httpd.conf: 기존 /csm·/resources ProxyPass(408·411줄 부근)를 Include 로 교체
sudo cp -a /etc/httpd/conf/httpd.conf /etc/httpd/conf/httpd.conf.bak-$(date +%Y%m%d-%H%M%S)
#   → 해당 4줄 삭제하고 다음 한 줄 삽입:
#   Include /opt/csm-next/httpd/csm-route.conf
sudo apachectl configtest && sudo systemctl reload httpd
```
> `/api/external/*`→8009, `/`→18082 매핑은 **그대로 유지**. 이 단계는 라우팅 결과가 기존과 동일(a/18081)하므로 **무중단·무변화**.

**검증**
```bash
curl -s -o /dev/null -w '%{http_code}\n' https://csm.sosyge.net/csm/login   # 200(기존과 동일)
readlink /opt/csm-next/httpd/csm-route.conf   # …/csm-route-a.conf
```
**롤백:** `sudo cp -a /etc/httpd/conf/httpd.conf.bak-… /etc/httpd/conf/httpd.conf && apachectl configtest && systemctl reload httpd` (route conf 파일은 남겨도 무해)

---

## Step 5. 업무시간 외 수동 리허설 1회 (자동화 연결 전 필수)

> ‼️ 첫 실행이 02:30 자동 타이머가 되어선 안 된다. **사람이 지켜보는 수동 리허설**로 전체 흐름을 1회 검증한 뒤에만 Step6 진행.

**관측 터미널**
```bash
while :; do curl -s -o /dev/null -w '%{http_code} ' https://csm.sosyge.net/csm/login; sleep 0.5; done
# 리허설 내내 200 만 나와야 함(502/503 가 보이면 중단·원인 분석)
```
**5a. 배포 스크립트 리허설** (새 build.time WAR 사용)
```bash
sudo cp <새-csm.war> /opt/csm-next/deploy/staging/csm.war
sudo touch /opt/csm-next/deploy/staging/deploy.ok
sudo /opt/csm-next/deploy/scripts/deploy-nightly-zdd.sh    # 타이머 아님, 직접 실행
# 로그에서: b 기동 → 버전 일치 → 라우팅 b → a 드레인 → a 재기동 → 라우팅 a → b 정지 → end-on-a
```
**5b. 롤링 재시작 리허설**
```bash
sudo /opt/csm-next/deploy/scripts/csm-next-rolling-restart.sh
# 로그에서 동일 흐름(새 WAR 없음), 관측 터미널 200 유지
```
**판정:** 관측 터미널 비-200 0건 + 최종 상태 `route→a`, `csm-next active`, `csm-next-b inactive`.
**리허설 실패 시 복구:** 전역 킬스위치 실행 → 직전 `csm.war.bak-*` 로 복원 → `systemctl restart csm-next`.

---

## Step 6. 자동 타이머 ExecStart 교체 (마지막)

**작업** (각 unit 백업 후)
```bash
# 02:30 / 12:30 / 14:30 → zdd
sudo systemctl edit --full deploy-nightly.service   # ExecStart=/opt/csm-next/deploy/scripts/deploy-nightly-zdd.sh
# 03:00 → 롤링 재시작
sudo systemctl edit --full csm-next-restart.service # ExecStart=/opt/csm-next/deploy/scripts/csm-next-rolling-restart.sh
sudo systemctl daemon-reload
```
**검증:** 다음 자동 실행(또는 수동 `systemctl start <unit>`) 1회를 **지켜보며** 관측 터미널 200 유지 확인. `journalctl -u deploy-nightly -n 200`.
**롤백:** `systemctl edit --full` 로 ExecStart 를 원복(`deploy-nightly.sh` / 기존 `restart csm-next && reload httpd`), `daemon-reload`.

### Step 6b. CI 워크플로 reconciliation (deploy-prod.yml) — 잠복 지뢰 제거

> deploy-prod.yml 은 현재 **죽은 경로(`/usr/local/tomcat10/webapps`) + 테스트 게이트**로 동면 중이라 즉시
> 위험은 아니나, 누군가 "고장났네" 하며 경로만 고치면 무중단 체계를 통째로 우회한다. **Step6 에서 함께 정리.**
> (이 단계 전까지 deploy-prod.yml 미수정.)

**A안 전환 — webapps 직접 덮어쓰기 → staging/zdd 경유** (러너가 서버 내부이므로 로컬에서):
```bash
# (현재·삭제) cp artifact/csm-prod.war /usr/local/tomcat10/webapps/csm.war   ← 죽은 경로·직접 덮어쓰기
# (A안) 둘 중 하나
cp artifact/csm-prod.war /opt/csm-next/deploy/staging/csm.war && touch /opt/csm-next/deploy/staging/deploy.ok
#   → 다음 타이머가 zdd 블루그린으로 위임, 또는 즉시:
sudo /opt/csm-next/deploy/scripts/deploy-nightly-zdd.sh
```

**반드시 보존:** ① self-hosted 러너 모델(서버 내부 로컬 실행 → 인바운드 SSH·시크릿 0) · ② `environment: prod` 수동 승인 게이트 · ③ 호스트 빌드 + `./gradlew test` 게이트 · ④ 타임스탬프 백업(zdd 가 `*.bak-<TS>` 수행).

> ⚠️ **경고 — 경로만 교정 금지 (C안)**
> deploy-prod.yml 이 broken 으로 보여도 `/usr/local/tomcat10/webapps` → `/opt/csm-next/tomcat/webapps`
> **단순 경로 교정은 절대 금지.** 직접 덮어쓰기 = 블루그린/드레인/버전대조/`flock` **전체 우회** + nightly zdd 와
> **락 없는 레이스** → 다운타임 재유입. **반드시 A안(staging/zdd 경유)으로.**

**dev 분리:** deploy-dev.yml 의 `/usr/local/tomcat10/webapps` 는 **dev 서버(49.247.42.59)의 공유 tomcat10 모델과 일치할 수 있어**(prod 와 다른 토폴로지) 본 reconcile 대상에서 **제외**. dev 경로 검증/무중단 적용 여부는 **별도 항목**으로 다룬다.

**검증:** 전환 후 `prod` 푸시(또는 `workflow_dispatch`) 1회 → 승인 → 마커 생성/zdd 실행 → 관측 200 유지 + end-on-a. **롤백:** `deploy` 잡을 직전 버전으로 되돌리거나 비활성(git revert).

---

## 단계 의존도 / 중단 기준

- 순서 의존: P0(DDL) → … → Step4(라우팅) → Step5(리허설) → Step6(자동화). Step1~4 는 behavior-neutral 이라 중간에 멈춰도 운영 영향 없음.
- 중단 기준: 리허설/실가동에서 관측 200 이 깨지거나, `free -m` 가용 메모리가 위험(오버랩 중 swap 0 한계 근접), 또는 `verify_b_version` 불일치 발생 시 → 전역 킬스위치 후 원인 분석.
