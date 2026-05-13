# Cancer Treatment — Production Deploy Checklist

대상: cancer-treatment 모듈 운영 신규 배포.
csm / mediplat 공통 항목은 [docs/prod-deploy-checklist.md](./prod-deploy-checklist.md)를 참고한다.

## 0. Repo 상태 (필수)

- [ ] `claude/amazing-fermat-f0fa7d` 브랜치가 `main`으로 머지/푸시됨
- [ ] 빌드 서버에서 `git pull` 후 최신 commit 확인
  - `28965ea` chore(cancer-treatment): split datasource config into dev/prod/local profiles
  - `d348912` chore(build): integrate cancer-treatment into deploy bundles + add prod schema check

## 1. DB 점검 (DBA)

- [ ] 운영 DB에 `scripts/cancer-treatment-schema-check.sql` 실행 (read-only)
- [ ] STEP 2 결과 — `missing_table` 0행 확인
- [ ] 누락 있을 경우:
  - [ ] 운영 DB 전체 백업 확보
  - [ ] `cancer-treatment/src/main/resources/schema.sql` 수동 적용 (전부 `IF NOT EXISTS` → 안전)
  - [ ] STEP 3 (컬럼) / STEP 4 (인덱스·FK) 재실행하여 정합성 확인

## 2. 운영 환경변수

### 2.1 cancer-treatment 전용 (`/etc/cancer-treatment/cancer-treatment.env`)

`scripts/cancer-treatment.env.example` 기반으로 작성.

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `CANCER_TREATMENT_PORT=8083` (또는 운영 정책 포트)
- [ ] `CANCER_TREATMENT_DATASOURCE_URL` — 운영 DB jdbc URL (`useSSL=true` 권장)
- [ ] `CANCER_TREATMENT_DATASOURCE_USERNAME`
- [ ] `CANCER_TREATMENT_DATASOURCE_PASSWORD`
- [ ] `CANCER_TREATMENT_SQL_INIT_MODE=never` (테이블 이미 존재 시. 신규 환경에서만 `always`로 1회 부팅 후 다시 `never`)
- [ ] `COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET` — **mediplat·csm과 완전히 동일**
- [ ] `CANCER_TREATMENT_MEDIPLAT_PORTAL_URL` — `https://<mediplat 운영 도메인>/portal`
- [ ] 파일 권한: `chown root:cancer`, `chmod 640`

### 2.2 mediplat 운영 env 보강

mediplat의 `EnvironmentFile`에 추가:

- [ ] `CANCER_TREATMENT_BASE_URL=https://<운영 외부 도메인>/cancer-treatment`
- [ ] `sudo systemctl restart mediplat`

### 2.3 preflight 실행

```bash
source /etc/cancer-treatment/cancer-treatment.env
./scripts/prod-preflight.sh
```

- [ ] `failures=0` 확인 (warnings는 상황에 따라 허용)

## 3. 운영 서버 인프라

### 3.1 systemd 유닛

- [ ] `scripts/cancer-treatment.service.example` → `/etc/systemd/system/cancer-treatment.service` 복사 및 편집
- [ ] User/Group 계정 생성 (`sudo useradd -r -s /usr/sbin/nologin cancer`)
- [ ] 디렉터리 준비:
  - `/opt/cancer-treatment/app/` (JAR 배치)
  - `/etc/cancer-treatment/` (env 파일)
- [ ] `sudo systemctl daemon-reload`

### 3.2 리버스 프록시 (nginx)

- [ ] `docs/cancer-treatment-nginx.conf.example` 스니펫을 운영 server 블록에 추가
- [ ] `nginx -t` 통과 확인
- [ ] `sudo systemctl reload nginx`
- [ ] SSE 동작 (`proxy_buffering off`) 반드시 포함 — 실시간 스케줄 동기화 의존

### 3.3 방화벽 / 보안

- [ ] 외부 노출: 443만 (nginx가 8083으로 reverse proxy)
- [ ] 8083은 `127.0.0.1` 바인딩 또는 내부망 ACL
- [ ] SELinux/AppArmor: java → DB(3306) outgoing 허용

## 4. 빌드 & 배포

### 4.1 빌드 (빌드 서버)

```bash
./gradlew packageProdDeploy
```

- [ ] `build/deploy/prod/cancer-treatment-prod.jar` 생성 확인 (약 27MB)

### 4.2 업로드

```bash
scp build/deploy/prod/cancer-treatment-prod.jar \
    <user>@<prod>:/opt/cancer-treatment/app/cancer-treatment.jar
```

- [ ] 운영 서버에서 파일 크기/체크섬 확인
- [ ] `chown cancer:cancer /opt/cancer-treatment/app/cancer-treatment.jar`

### 4.3 기동

```bash
sudo systemctl enable cancer-treatment
sudo systemctl start cancer-treatment
sudo journalctl -u cancer-treatment -f
```

기동 로그에서 확인:

- [ ] `The following 1 profile is active: "prod"`
- [ ] `HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@...`
- [ ] `Tomcat started on port 8083 (http) with context path '/cancer-treatment'`
- [ ] `Started CancerTreatmentApplication in ... seconds`

## 5. 스모크 테스트 (배포 직후)

### 5.1 HTTP 응답

```bash
curl -I https://<운영 도메인>/cancer-treatment/login-required
# → 200

curl -I https://<운영 도메인>/cancer-treatment/treatment-calendar
# → 302 (Location: .../login-required, 미인증 정상 동작)
```

### 5.2 SSO 흐름 (브라우저)

- [ ] mediplat 로그인
- [ ] 포털에서 "암센터 치료스케줄" 메뉴 클릭
- [ ] `/cancer-treatment/mediplat/sso/entry` 경유
- [ ] `/cancer-treatment/cancer-treatment-schedule` 또는 캘린더 페이지 진입
- [ ] dev에서 보던 데이터가 운영 DB 기준으로 표시되는지 확인

### 5.3 페이지별 동작

- [ ] 치료 스케줄 (일/주/월 뷰 전환)
- [ ] 캘린더 (월/주 토글, 날짜 클릭)
- [ ] 환자 관리 (목록, 등록, 인라인 수정)
- [ ] 치료실 관리 (CRUD)
- [ ] 설정 (카테고리별 항목)
- [ ] 대시보드 (날짜별 요약)

### 5.4 실시간 동기화 (SSE)

- [ ] 탭 2개에서 동일 날짜 스케줄 페이지 열기
- [ ] 한쪽에서 스케줄 추가 → 다른 탭에 즉시 반영되는지 (nginx buffering off 검증)

## 6. 롤백 계획

문제 발생 시 즉시:

```bash
# 1) 신규 서비스 중지
sudo systemctl stop cancer-treatment
sudo systemctl disable cancer-treatment

# 2) mediplat 메뉴 숨김 (선택)
#    /etc/mediplat/mediplat.env 에서 CANCER_TREATMENT_BASE_URL 주석 처리
sudo systemctl restart mediplat

# 3) nginx 라우팅 제거 (선택)
#    server 블록에서 location /cancer-treatment/ 주석 처리
sudo nginx -t && sudo systemctl reload nginx
```

DB는 `ct_*` 테이블이 다른 서비스와 무관하므로 **DROP 불필요**. 데이터가 들어갔어도 보관 가능.

## 7. 배포 후

- [ ] 로그 모니터링 24시간 (`journalctl -u cancer-treatment --since "1 hour ago"`)
- [ ] HikariCP connection leak 경고 없음
- [ ] mediplat 로그아웃 → cancer-treatment 세션도 만료되는지 확인
- [ ] 다음 영업일 사용자 피드백 수집

## 부록: 운영 리스크 메모

- `application-prod.properties`에 DB credential 평문 fallback이 들어 있다. 운영에서는 반드시 env로 덮어쓰며, 노출되더라도 운영 DB 방화벽이 외부 차단 상태여야 한다.
- SSO secret이 `change-me`인 채로 기동되면 cancer-treatment 진입이 항상 403. `prod-preflight.sh`가 잡아낸다.
- `CANCER_TREATMENT_SQL_INIT_MODE=always` 상태로 운영 서버를 두면 매 부팅 시 `schema.sql`이 재실행된다 (`IF NOT EXISTS`라 손상은 없지만 불필요한 부하). 첫 부트 후 `never`로 되돌릴 것.
- nginx `proxy_buffering off`가 빠지면 SSE 실시간 동기화가 깨진다.
