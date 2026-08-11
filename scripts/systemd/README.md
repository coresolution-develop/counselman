# Nightly Auto-Deploy

작업자가 낮 동안 staging 디렉터리에 빌드 산출물을 올려두고 명시 마커를 만들면, **02:30 KST**에 systemd 타이머가 깨어나 csm / mediplat / cancer-treatment / links 중 staging에 올라온 것만 자동으로 라이브에 반영합니다.

기존 `nightly-maintenance.timer`(03:00)보다 30분 먼저 실행되도록 배치되어 있어, 새 산출물이 자리잡은 뒤 maintenance가 reload를 수행합니다.

## 서버 디렉터리 구조

```
/opt/deploy/
├── scripts/
│   └── deploy-nightly.sh                   # 이 저장소의 scripts/deploy-nightly.sh 사본
├── staging/                                # 작업자가 산출물을 올리는 위치
│   ├── csm.war                             # (선택) 이 파일이 있으면 csm 배포
│   ├── mediplat.jar                        # (선택) 있으면 mediplat 배포
│   ├── cancer-treatment.jar                # (선택) 있으면 cancer-treatment 배포
│   ├── links.jar                           # (선택) 있으면 links(hub) 배포
│   └── deploy.ok                           # ★ 트리거 마커 (없으면 02:30에 아무것도 안 함)
└── archive/
    └── 2026-05-19_023000/                  # 처리된 배포의 스냅샷 (롤백 시 참고)
        ├── csm.war
        ├── mediplat.jar
        └── deploy.ok
```

라이브 위치 (env로 override 가능):
- csm: `/usr/local/tomcat10/webapps/csm.war` (Tomcat hot-deploy, 재시작 없음)
- mediplat: `/opt/mediplat/app/mediplat.jar` (systemd: `mediplat`)
- cancer-treatment: `/opt/cancer-treatment/app/cancer-treatment.jar` (systemd: `cancer-treatment`)
- links(hub): `/opt/links/app/links.jar` (systemd: `links`, port 8085)

## 1회 설치 (서버에서)

> **links 최초 설치** (한 번만): `links`(link hub)는 별도 systemd 유닛이 필요합니다.
> ```bash
> sudo mkdir -p /opt/links/app
> sudo cp scripts/systemd/links.service /etc/systemd/system/
> # DB 접속정보/허브 옵션 주입 (운영 DB는 csm과 동일 DB 권장 — hub_member 공유)
> sudo tee /etc/default/links >/dev/null <<'EOF'
> SPRING_DATASOURCE_URL=jdbc:mysql://<prod-db-host>:3306/csm?serverTimezone=Asia/Seoul&useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
> SPRING_DATASOURCE_USERNAME=<user>
> SPRING_DATASOURCE_PASSWORD=<pass>
> LINKS_PORT=8085
> HUB_SIGNUP_CODE=<가입코드>
> HUB_REMEMBER_COOKIE_SECURE=true
> EOF
> sudo systemctl daemon-reload
> sudo systemctl enable --now links
> # 리버스 프록시(httpd/nginx)에서 /links, /hub/**, /admin/company-links, /api/company-links → 127.0.0.1:8085 라우팅 추가
> ```

```bash
# 1) 디렉터리 + 스크립트 배치
sudo mkdir -p /opt/deploy/{staging,archive,scripts}
sudo cp scripts/deploy-nightly.sh /opt/deploy/scripts/
sudo chmod +x /opt/deploy/scripts/deploy-nightly.sh

# 2) 환경 override가 필요하면 (선택)
sudo tee /etc/default/nightly-deploy >/dev/null <<'EOF'
# 기본값이면 비워두세요. 경로/서비스명이 다를 때만 작성.
# MEDIPLAT_SERVICE=mediplat-next
# CANCER_SERVICE=cancer-treatment-next
# BACKUP_KEEP=5
EOF

# 3) systemd 유닛 설치
sudo cp scripts/systemd/nightly-deploy.service /etc/systemd/system/
sudo cp scripts/systemd/nightly-deploy.timer   /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now nightly-deploy.timer

# 4) 동작 확인
systemctl list-timers nightly-deploy.timer
```

## 시크릿 주입 — 필수 (앱마다 주입 경로가 다르다)

properties 파일에서 평문 비밀값을 제거했습니다. **아래 값이 없으면 앱이 기동에 실패합니다.**
전체 키 목록은 `.env.example`, `mediplat/.env.example`, `cancer-treatment/.env.example` 참고.

| 앱 | 실행 형태 | 주입 경로 |
|---|---|---|
| **csm** | Tomcat WAR (자체 systemd 유닛 없음) | **Tomcat 의 `bin/setenv.sh`** ← 아래 주의 |
| mediplat | 독립 JAR (systemd) | `EnvironmentFile` |
| cancer-treatment | 독립 JAR (systemd) | `EnvironmentFile` |
| links | 독립 JAR (systemd) | `EnvironmentFile` |

> ⚠️ **csm 전용 `EnvironmentFile` 을 만들어도 동작하지 않습니다.**
> csm 은 자기 systemd 유닛이 없고 **Tomcat 프로세스 안에서 동작**합니다. 따라서 csm 이 보는
> 환경변수는 곧 Tomcat 프로세스의 환경입니다. `/etc/default/csm-next` 같은 파일을 만들어도
> 아무 유닛도 읽지 않습니다. Tomcat 유닛에 `EnvironmentFile` 을 추가하는 방법도 가능하지만,
> **현재 서버는 `setenv.sh` 방식을 쓰고 있으므로 그쪽에 맞춥니다.**

```bash
# ── csm (Tomcat WAR) ─────────────────────────────────────────────────────
# 반드시 백업 후 추가. 기존 export 는 지우지 말 것.
sudo cp -a /usr/local/tomcat10/bin/setenv.sh /usr/local/tomcat10/bin/setenv.sh.bak-$(date +%Y%m%d%H%M)

sudo tee -a /usr/local/tomcat10/bin/setenv.sh >/dev/null <<'EOF'

# --- 시크릿 환경변수화 이후 필수 ---
export SPRING_PROFILES_ACTIVE="prod"
export SPRING_DATASOURCE_URL="jdbc:mysql://<prod-db-host>:3306/csm?serverTimezone=Asia/Seoul&useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true"
export SPRING_DATASOURCE_USERNAME="<user>"
export SPRING_DATASOURCE_PASSWORD="<pass>"
export LOGIN_AES_KEY="<정확히 16자>"
export COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET="<전 서비스 공유값>"
export SPRING_MAIL_PASSWORD="<gmail 앱 비밀번호>"
export KAKAO_CLIENT_ID="<kakao app id>"
export KAKAO_CLIENT_SECRET="<kakao app secret>"
export BIZPPURIO_PROD_ACCOUNT="<account>"
export BIZPPURIO_PROD_USERNAME="<username>"
export BIZPPURIO_PROD_PASSWORD="<password>"
export CSM_BASE_URL="https://<prod-host>/csm"
EOF

# 이제 이 파일이 시크릿을 담으므로 권한을 조입니다. 소유자는 Tomcat 실행 계정.
sudo chmod 600 /usr/local/tomcat10/bin/setenv.sh
sudo chown tomcat /usr/local/tomcat10/bin/setenv.sh
sudo bash -n /usr/local/tomcat10/bin/setenv.sh && echo "문법 OK"
```

> 값에 공백(`LOGIN_AES_KEY`)이나 `&`(JDBC URL), `!`(비밀번호)가 들어가므로 **반드시 큰따옴표로
> 감쌉니다.** 따옴표를 빠뜨리면 셸이 값을 자르거나 히스토리 확장이 일어납니다.
> dev 프로파일이면 `BIZPPURIO_PROD_*` 대신 `BIZPPURIO_DEV_*` 를 씁니다.

```bash
# ── mediplat ─────────────────────────────────────────────────────────────
sudo tee /etc/default/mediplat-next >/dev/null <<'EOF'
PLATFORM_RUNTIME_ENV=PROD
SPRING_DATASOURCE_URL=jdbc:mysql://<prod-db-host>:3306/csm?serverTimezone=Asia/Seoul&useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<pass>
# DataSource가 2개다. 아래를 생략하면 위 spring.datasource.* 를 재사용한다.
# PLATFORM_COUNSELMAN_DATASOURCE_PASSWORD=<pass>
LOGIN_AES_KEY=<csm과 동일한 16자>
COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET=<csm과 동일한 값>
PLATFORM_ADMIN_PASSWORD=<플랫폼 관리자 비밀번호>
COUNSELMAN_BASE_URL=https://<prod-host>/csm
CANCER_TREATMENT_BASE_URL=https://<prod-host>/cancer-treatment
EOF
sudo chmod 600 /etc/default/mediplat-next
```

유닛 파일에 아래 지시자가 있는지 확인합니다. `-` 접두어는 "파일이 없어도 기동은 시도"를
뜻하므로, 필수 시크릿을 쓰는 유닛에서는 접두어 없이 두어 파일 누락을 즉시 드러내는 편이
안전합니다.

```ini
[Service]
EnvironmentFile=/etc/default/mediplat-next
```

적용 후 (유닛 이름은 환경마다 다릅니다 — dev 는 `mediplat`, prod 는 `mediplat-next`):

```bash
sudo systemctl daemon-reload
sudo systemctl restart mediplat
sudo journalctl -u mediplat -n 50 --no-pager
```

> **기동 실패 시 확인할 로그**
> `Could not resolve placeholder 'SPRING_DATASOURCE_PASSWORD'` 같은 메시지가 보이면
> 해당 키가 주입되지 않았다는 뜻입니다. 값을 채우고 재기동하세요.
>
> `@Value("${...:기본값}")` 에 기본값이 있어도 소용없습니다. 기본값은 **키 자체가 없을 때만**
> 적용되고, 지금처럼 키의 값이 미해석 플레이스홀더(`${ENV}`)면 그대로 예외가 납니다.
> 실제 사례: `platform.bootstrap.admin-password` 는 `:ChangeMe123!` 기본값이 있는데도
> `PLATFORM_ADMIN_PASSWORD` 미주입으로 mediplat 이 크래시 루프에 빠졌습니다.

> **DEV/PROD 에서 `PLATFORM_RUNTIME_ENV` 를 반드시 설정하세요.** 미설정 시 LOCAL 로
> 간주되어 서비스 base URL 검증이 생략되고 localhost 주소가 그대로 등록됩니다.
> 다만 **이미 localhost 로 등록된 상태에서 갑자기 DEV/PROD 로 바꾸면** 해당 서비스들이
> `Skipping bootstrap of service` 로 빠져 포털에서 사라집니다. 순서를 지키세요:
> ① `COUNSELMAN_BASE_URL` / `CANCER_TREATMENT_BASE_URL` 을 실제 도메인으로 먼저 채운다
> → ② 그다음 `PLATFORM_RUNTIME_ENV` 를 설정한다.

## 🔴 운영(prod) 배포 전 필수 점검

**변경 전 `application-prod.properties` 는 DB 접속정보를 파일에 하드코딩하고 있었습니다.**
즉 운영 csm 은 지금까지 `SPRING_DATASOURCE_*` 를 **한 번도 주입받은 적이 없습니다.**
같은 이유로 `LOGIN_AES_KEY`, `KAKAO_CLIENT_*`, `SPRING_MAIL_PASSWORD`, `BIZPPURIO_PROD_*`
도 없을 가능성이 높습니다.

**이 상태로 신규 WAR 를 배포하면 다음 배포 시점에 csm 이 죽습니다.**
(dev 에서 실제로 발생했고, `setenv.sh` 주입으로 복구했습니다.)

배포 **전에** 아래를 확인하세요.

```bash
# 현재 Tomcat 프로세스가 실제로 들고 있는 키 (값은 출력되지 않는다)
sudo tr '\0' '\n' < /proc/$(pgrep -f tomcat | head -1)/environ \
  | grep -oE '^(SPRING_DATASOURCE_[A-Z]+|LOGIN_AES_KEY|KAKAO_CLIENT_[A-Z]+|SPRING_MAIL_PASSWORD|BIZPPURIO_PROD_[A-Z]+|COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET|SPRING_PROFILES_ACTIVE)=' \
  | sort -u
```

아래 12개가 모두 나와야 합니다. 하나라도 빠지면 **배포하지 말고 먼저 `setenv.sh` 를 채우세요.**

```
SPRING_PROFILES_ACTIVE
SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD
LOGIN_AES_KEY
COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET
SPRING_MAIL_PASSWORD
KAKAO_CLIENT_ID / KAKAO_CLIENT_SECRET
BIZPPURIO_PROD_ACCOUNT / _USERNAME / _PASSWORD
```

mediplat·cancer-treatment 도 동일하게 확인합니다.

```bash
for u in mediplat-next cancer-treatment-next; do
  echo "=== $u"; systemctl show "$u" -p EnvironmentFiles --no-pager
done
```

> mediplat 은 `PLATFORM_ADMIN_PASSWORD` 가 **필수**입니다(없으면 크래시 루프).
> cancer-treatment 는 `COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET` 이 필수입니다.

## 배포 검증 — Tomcat hot-deploy 는 조용히 실패한다

csm 은 WAR 를 교체하면 Tomcat 이 재시작 없이 hot-deploy 합니다. **이때 Spring 컨텍스트가
죽어도 `Deployment ... has finished` 는 정상적으로 찍힙니다.** 배포 로그만 보고 성공으로
판단하면 안 됩니다.

실제 사례: 환경변수 미주입 상태에서 신규 WAR 가 hot-deploy 되어 컨텍스트가
`Could not resolve placeholder 'LOGIN_AES_KEY'` 로 실패했는데, 배포 로그에는
`finished in [15,960] ms` 만 남았습니다.

WAR 교체 후 아래 3가지를 **모두** 확인하세요.

```bash
# ① 응답 확인 — 200/302 여야 한다. 404 면 컨텍스트가 안 올라온 것이다.
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/csm/login
```

```bash
# ② 재기동 이후 구간에 오류가 없어야 한다.
sudo tail -300 /usr/local/tomcat10/logs/catalina.out \
  | grep -iE "SEVERE|Could not resolve placeholder|startup failed|Server startup in"
```

```bash
# ③ 실제로 새 빌드가 서비스 중인지 — build.time 이 방금 빌드 시각이어야 한다.
sudo cat /usr/local/tomcat10/webapps/csm/WEB-INF/classes/META-INF/build-info.properties
```

> ②에서 `Caused by:` 줄은 스택트레이스 연속 줄이라 **타임스탬프가 없습니다.** `grep` 만
> 걸면 1년 전 오류와 방금 오류를 구분할 수 없으니, 반드시 `tail` 로 구간을 좁혀서 보세요.

## 일상 사용 (작업자)

```bash
# 1) 산출물 빌드 (로컬)
./gradlew packageProdDeploy
# (cancer-treatment까지 묶으려면 build.gradle의 packageProdDeploy에
#  cancerTreatmentProdJar 의존성을 추가해야 합니다. 현재는 csm/mediplat만 묶임.)

# 2) staging으로 업로드 — 그날 배포할 것만 올리세요
scp build/deploy/prod/csm-prod.war           PROD:/opt/deploy/staging/csm.war
scp mediplat/build/libs/mediplat-prod.jar    PROD:/opt/deploy/staging/mediplat.jar
scp cancer-treatment/build/libs/cancer-treatment-prod.jar \
    PROD:/opt/deploy/staging/cancer-treatment.jar
scp links/build/libs/links-prod.jar          PROD:/opt/deploy/staging/links.jar

# 3) ★ 마커 생성 — 이 명령이 떨어진 다음날 02:30 KST에 배포됨
ssh PROD 'touch /opt/deploy/staging/deploy.ok'

# (선택) 마지막 순간 취소 — 02:30 전에 실행
ssh PROD 'rm -f /opt/deploy/staging/deploy.ok'

# (선택) 다음 실행 시각 확인
ssh PROD 'systemctl list-timers nightly-deploy.timer'

# (선택) 지금 당장 한 번 돌리고 싶을 때 (수동 실행)
ssh PROD 'sudo systemctl start nightly-deploy.service && journalctl -u nightly-deploy -n 200 --no-pager'
```

## 로그 보기

```bash
journalctl -u nightly-deploy.service -n 200 --no-pager
journalctl -u nightly-deploy.service --since today
```

모든 출력은 `[nightly-deploy HH:MM:SS]` 접두사로 journald에 들어갑니다.

## 롤백

스크립트는 라이브 파일을 덮어쓰기 전에 `*.bak-<TS>` 사본을 남깁니다 (앱당 최근 `BACKUP_KEEP`개, 기본 5개).

```bash
# 직전 배포로 되돌리기 (mediplat 예)
ssh PROD 'ls -1t /opt/mediplat/app/mediplat.jar.bak-*' | head -1
ssh PROD '
  LATEST=$(ls -1t /opt/mediplat/app/mediplat.jar.bak-* | head -1)
  sudo mv "$LATEST" /opt/mediplat/app/mediplat.jar
  sudo systemctl restart mediplat
'
```

`/opt/deploy/archive/<TS>/` 디렉터리에는 그날 staging에서 옮겨진 원본 산출물이 그대로 보존되므로, 동일 산출물을 staging에 다시 올려 재배포할 수도 있습니다.

## 안전장치 요약

| 상황 | 동작 |
|------|------|
| `deploy.ok` 없음 | 로그만 한 줄 남기고 exit 0 (no-op) |
| staging에 산출물 없음 + 마커만 있음 | 마커만 아카이브로 옮기고 종료 |
| 동일 타이머가 이미 실행 중 | `flock`으로 차단, exit 2 |
| 라이브 디렉터리 부재 | 해당 앱만 건너뜀, WARN 로그 |
| `systemctl restart` 실패 | 비-zero exit, 마커는 그대로 두지 않고 아카이브됨 — 운영자가 journal 확인 후 수동 대응 |
| 호스트 down at 02:30 | `Persistent=true` 로 부팅 후 1회 보충 실행 |

## 트레이드오프

- staging에 올린 뒤 `touch deploy.ok`까지 해야 배포됩니다. "파일만 올리면 자동"이 아니므로 한 단계 더 필요하지만, 그만큼 실수 배포가 줄어듭니다.
- 03:00 maintenance와 별개 타이머라 한쪽이 실패해도 다른 쪽에 영향이 없습니다 (`Before=` 순서만 보장).
- 산출물은 항상 staging→archive로 이동(복사 아님)되므로 같은 파일이 다음날 다시 배포되는 일은 없습니다.
