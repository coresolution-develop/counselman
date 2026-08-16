# MediPlat 작업 현황

> 최종 업데이트: 2026-08-14

---

## 🎯 다음 진행 큐 (우선순위순)

> 각 항목은 시작 위치·수정 방향·예상 작업량을 포함합니다. 위에서부터 picking 가능.

### 🔥 P0 — 운영 차단 / 명시적 요청

> ✅ **P0-1 / P0-2 / P0-3 모두 2026-06-02 완료** — 상세는 아래 "완료된 작업" 참조.

#### [P0-4] mediplat 설정 파일 하드코딩 시크릿 제거 — **코드 완료(`b11efa2`, 08-11) / 후속 미완**
- **문제**: [application.properties](mediplat/src/main/resources/application.properties)의 `${ENV:기본값}` 구조에서 기본값 자리에 실제 운영 DB 접속정보(13·15행), 개인정보 복호화 AES 키(21행), 부트스트랩 관리자 비밀번호(26행), SSO 공유 시크릿(33행)이 들어 있음
- **위험**: 환경변수만 누락되면 조용히 실제 값으로 동작. 저장소 접근자 누구나 운영 DB 접속 가능. git 이력에도 잔존
- ✅ **완료**: 기본값 전부 제거 → 미설정 시 기동 실패 (`b11efa2`, 2026-08-11)
- ⚠️ **이 항목의 "선행 확인"을 건너뛰어 2026-08-13 운영 장애 발생 (mediplat 12.5시간 중단).**
  `PLATFORM_ADMIN_PASSWORD` 미주입 상태로 신규 jar 배포 → `PlaceholderResolutionException` 크래시 루프.
  경위·재발 방지는 [docs/prod-deploy-phase1b.md](docs/prod-deploy-phase1b.md) "장애 기록" 참조
- **후속 미완 ①**: 노출된 DB 비밀번호·AES 키 **로테이션**. AES 키 교체 시 기존 암호화 데이터 재암호화 계획 별도 수립 필요
- **후속 미완 ②**: `PLATFORM_ADMIN_PASSWORD` 정식 값 교체 (현재 임시값. 이 값은 기동 시마다 운영 관리자 비밀번호를 덮어씀)
- **후속 미완 ③**: 배포 전 필수 env 대조를 절차로 강제 — 절차서 0-0 단계에 반영됨. 자동화는 미착수
- **출처**: 2026-08-09 mediplat 점검 (상세: [docs/handoff-2026-08-09.md](docs/handoff-2026-08-09.md))

#### [P0-6] 서비스 다운 알림 부재 — **미착수**
- **문제**: 2026-08-13 mediplat 12.5시간 중단을 **사용자 신고로 발견**. 모니터링·알림 장치가 전혀 없음
- **증폭 요인**: `Restart=always`라 크래시 루프 중 상태가 `failed`가 아니라 `activating (auto-restart)`로 보여 눈에 띄지 않음
- **수정 방향**: `curl 18081` / `curl 18082` 주기 확인 후 실패 시 알림. systemd timer + 스크립트면 충분
- **작업량**: 2~3시간

#### [P0-5] mediplat CSRF 보호 도입 — **미착수**
- **문제**: Spring Security 미도입(`spring-security-crypto`만 의존)이라 `SecurityFilterChain` 부재 → `_csrf`가 항상 null. 템플릿이 `th:if="${_csrf != null}"`로 방어적으로 짜여 있어 **조용히 무력화**됨
- **영향**: `/admin/users`, `/admin/access`, `/admin/services`, `/admin/institutions`, `/fleet/admin/*`, `/seminar-room/*` 등 상태변경 POST 전부
- **증폭 요인**: `/admin/maintenance`는 임의 HTML을 웹 루트 파일로 기록 → 관리자 세션 탈취 없이도 점검 페이지에 스크립트 주입 가능
- **수정 방향**: `spring-boot-starter-security` 추가 후 **CSRF만 활성화**, 인가는 기존 세션 가드 유지. 폼이 많아 단계적 적용 권장
- **작업량**: 4~6시간 (회귀 위험 있음)

#### [P0-6] mediplat 세션 고정(Session Fixation) 대응 — **미착수 / 착수 쉬움**
- **문제**: [MediplatController.java:130](mediplat/src/main/java/com/coresolution/mediplat/controller/MediplatController.java#L130) 인증 성공 후 세션 ID 회전 없이 `setAttribute`
- **수정 방향**: 인증 직후 `session.invalidate()` → 새 세션에 사용자 심기 (CSM `MediplatSsoController`의 `request.changeSessionId()` 패턴 참고)
- **확인 필요**: `LoginAuditSessionListener`가 세션 파기 이벤트를 듣고 있어 로그아웃 감사 로그 중복/오탐 여부 검증
- **작업량**: 30분 + 검증

### ⚠️ P1 — 반복 버그 / 사용자 경험

#### [P1-1] 상담 통계 Alpine ECharts 간헐 충돌
- **증상**: Turbo 이동 시 `Cannot convert undefined or null to object`
- **원인**: `_charts: []` 가 Alpine reactive state에 있어 ECharts 인스턴스 push 시 deep-proxy 시도
- **수정 방향**: `_charts`를 Alpine state 밖 클로저 변수로 이동
- **시작**: [consultation-stats.html](src/main/resources/templates/design/consultation-stats.html) noticePage 비슷한 패턴
- **작업량**: 1시간

#### [P1-2] 병실현황판 Alpine 간헐 버그
- **증상**: P1-1과 동일 에러 패턴
- **원인**: `mapWard()` 반환 객체의 `get discharge()` / `get afternoon()` getter가 Alpine reactive proxy 초기화 중 잘못된 `this` 컨텍스트
- **수정 방향**: getter 제거 → 값 즉시 계산으로 대체
- **시작**: [ward-status.html](src/main/resources/templates/design/ward-status.html)
- **작업량**: 1시간

#### [P1-3] 상담 리스트 "상담중" 상태 오표시 + 30분 락 제거
- **증상**: 진입 후 퇴장 시에도 "상담중" 유지
- **수정 방향**: 30분 락 개념 삭제 후 단순 상태 표시로 전환
- **연관**: 상담 접수 페이지의 "상담중 진입 차단" 작업 (P1-4)과 함께 검토 권장
- **작업량**: 2~3시간

#### [P1-4] 상담 접수 — 행 클릭 상세 패널 + 진입 차단
- 행 클릭 → 우측 상세 패널 즉시 노출 (별도 "수정" 버튼 경유 없이)
- 다른 사용자가 진행 중인 상담은 진입 불가 (락 표시 또는 disable)
- **시작**: [consultation-intake.html](src/main/resources/templates/design/consultation-intake.html)
- **작업량**: 3~4시간

#### [P1-5] 추천기사 알고리즘 수정 (2026-05-29 요청)
- **목표**: 추천기사 노출 알고리즘 개선
- **상태**: 요청 접수 — 구체 요구사항/현행 로직 위치 확인 필요
- **시작**: 추천기사 관련 코드 위치 미확정 (csm `src/` 내 검색 결과 없음 — mediplat/cancer-treatment 등 타 모듈 가능성)
- **확인 필요**: 어떤 화면의 추천기사인지, 현재 정렬/가중치 기준, 원하는 변경 방향
- **작업량**: 미정 (스코프 확정 후 산정)

### 🧹 P2 — 정리성 (방금 작업 연장선)

#### [P2-1] 옛 페이지 redirect 정리
- **대상**: `/smsSetting`, `/cardsetting`, `/smslog` 등 옛 디자인 URL
- **수정 방향**: PageController에서 새 URL로 301 redirect (`return "redirect:/message/..."`)
- **시작**: [PageController.java](src/main/java/com/coresolution/csm/controller/PageController.java) line 2692 (smsSetting), 2790 (cardsetting)
- **작업량**: 30분

#### [P2-2] 공지 읽음 추적 서버 통합
- **현황**: client `localStorage` (`csm-read-notices-<userId>`) 기반
- **수정 방향**: `core_notice_read` 테이블 + `/notice/read/{id}` 엔드포인트 활용 (이미 존재)
- **변경 위치**: [chrome.js:328](src/main/resources/static/assets/js/chrome.js#L328) `markNoticesRead` — fetch 추가
- **작업량**: 1~2시간

#### [P2-3] inst_notice 시스템 deprecate 정책 확정
- 일반 기관 자체 공지 작성 기능 사용 여부 결정 (현재 read-only)
- 사용 안 함이면 `inst_notice_<INST>` 테이블, `/notices/save`, `/notices/delete` 제거
- **블로커**: 정책 결정 필요 (코드 작업은 결정 후 30분)

### 📌 P3 — 작은 개선 / 정리

- [ ] CSM 허용 버튼 (`/csm/access`) toggle POST 검증 — `mp_user_service` 실제 insert/update 확인
- [ ] 채팅 페이지 폰트 CORS 교체 — `fonts.gstatic.com/ea/notosanskr/v2/` deprecated → `fonts.googleapis.com/css2`
- [ ] 기본 아바타 이미지 누락 — `/img/default-avatar.png` 추가
- [ ] 챗봇 FAQ 검색 비로그인 접근 — 로그인 전 FAQ 패널 노출 검토
- [ ] 좌측 네비게이션 스크롤 CSS 수정
- [ ] 링크 허브 prod 배포 + 분류/환경 정리 SQL 실행 — dev만 반영됨. 상세는 완료 섹션 2026-08-16 항목

**2026-08-09 mediplat 점검에서 나온 항목** (상세: [docs/handoff-2026-08-09.md](docs/handoff-2026-08-09.md))

- [ ] mediplat 로그인 시도 제한 없음 — brute force 무제한. `recordLogin`은 성공만 기록, 실패 카운트 부재. IP+계정 단위 제한 필요
- [ ] mediplat 미인증 프리뷰 엔드포인트 제거 — `MediplatController:1361,1366`의 `/design/login`, `/design/portal` (데이터 노출은 없음)
- [ ] mediplat 세션 타임아웃 명시 — 현재 Boot 기본 30분 암묵 의존. `server.servlet.session.timeout=30m` 명시
- [ ] `NewsletterService` 테스트 0건 — 802줄 서비스에 테스트 없음 (AI 추천 캐시·피드백 로직 미검증)
- [ ] N+1 제거 — `PlatformStoreService:434` 뷰어 계정마다 `listRoomBoardViewerScopeInstCodes` 1쿼리. `IN (...)` 일괄 조회로 변경
- [ ] mediplat 폼 접근성 — 입력 컨트롤 대비 라벨 부족 (`institution-admin-app.jsx` 32개 중 2개, `portal-app.jsx` 14개 중 2개). placeholder만으로는 WCAG 3.3.2 위반. 최소 `aria-label` 부여
- [ ] `deploy-dev.yml`/`deploy-prod.yml` 아티팩트 전송 안정화 검토 — 146MB 다운로드에 13분 소요. 잘림 재발 시 아티팩트 경유 제거(러너 직접 빌드 등) 고려

---

## ✅ 완료된 작업

### 2026-08-16 링크 허브 리디자인 (UI 전면 교체 + 환경·분류 컬럼)

> 디자인 스펙: [docs/design/link-hub/README.md](docs/design/link-hub/README.md) · 남은 항목: [docs/design/link-hub/PHASE2.md](docs/design/link-hub/PHASE2.md)
> 확인: https://dev.sosyge.net/csm/links · https://dev.sosyge.net/csm/admin/company-links

#### 1단계 — 허브를 카드 갤러리에서 런처로 — **완료 / dev 반영**
- 사이드바 232px + 상단바 + 검색 + 필터 칩 + 우측 개인 레일. 링크는 40px 컴팩트 행
- 커맨드 팔레트(⌘/Ctrl+K) — 이름·분류·host 검색, `↑↓` 이동, `↵` 열기, `⌘↵` 새 탭
- 운영/개발/DEMO를 **색 + 라벨 + 점선 테두리 3중 표시**. 색만으로 구분하지 않는다(개발서버 오인 클릭 방지)
- 다크 모드 · 밀도(행/그리드) 토글. 첫 페인트 전에 적용해 깜빡임 없음
- 로그인·회원가입 400px 중앙 단일 폼, 계정 설정 프로필 헤더 + 2열 카드(비밀번호 강도 표시)
- **엔드포인트·필드명 무변경.** 즐겨찾기·개인 링크·메모·최근 사용·인기 링크·공지 배너·PWA 전부 유지
- 행은 `div` + 전체 덮는 링크로 구성 — `<a>` 안에 `<button>`은 유효하지 않은 마크업이라 스크린리더가 깨진다
- 커밋: `582764e` `81ad59d` `4168394`

#### 2단계 — 운영자가 환경·분류 색을 직접 지정 — **완료 / dev 반영**
- `company_link.env` + `company_link_category.color / color_dark / short_label` 추가.
  `CompanyLinkService.ensureTable()`의 `ensureColumn`이 기동 시 처리하므로 **마이그레이션 스크립트 없음**
- 값이 비면 예전처럼 이름·host로 자동 판정. 우선순위 **운영자 지정 → 핸드오프 기본표 → 이름 해시**
- 링크 관리 화면을 탭 3개(링크 / 분류 순서 / 공지)로 재구성.
  링크 추가는 우측 슬라이드 패널, 분류는 드래그 정렬, 공지는 실시간 배너 미리보기
- 분류 색상은 10색 세트에서만 선택(색약 대응) + 서비스가 `#rrggbb` 형식 재검증(값이 `style` 속성에 들어감)
- 커밋: `e4e7482` `c8e64db`

#### 표시 버그 2건 — **완료 / dev 반영**
- **host 대신 URL 전체가 노출** — `ocean_plt.cspay.co.kr`처럼 호스트명에 언더스코어가 있으면
  `URI.getHost()`가 null을 반환해 폴백이 URL 전체를 내보냈다. 문자열에서 authority를 직접 파싱하도록 교체.
  포트는 유지 — `:8210` / `:8220` / `:8230`으로만 갈리는 링크가 많다
- **행 높이가 벌어지고 이름이 잘림** — 그리드는 4열인데 자식이 5개라 ★가 다음 줄로 밀렸다.
  뒤쪽 요소를 `.lh-tail`로 묶어 열 수를 맞춤
- 분류 목록은 최종적으로 **이름만** 표시(링크 55개 기준 URL이 이름 폭을 먹음). 전체 URL은 행 tooltip
- 커밋: `51cf0c2` `bd8f0fe`

#### 남은 것
- [ ] **prod 배포 미완** — 지금까지 전부 dev. prod는 `prod` 브랜치 푸시 또는 workflow_dispatch로 별도 배포
- [ ] **분류/환경 정리 SQL 실행 미완** — [scripts/hub-category-env-cleanup.sql](scripts/hub-category-env-cleanup.sql) (`9c0bf7e`).
  `조이랜드 실서버`/`조이랜드 개발서버` → `조이랜드` + env, `ATS 군산 DEMO` → `ATS 군산` + env demo.
  dev·prod DB 각각 실행 필요. 분류명은 화면을 보고 적은 것이라 2단계 확인 쿼리로 대조 후 실행
- 기기·세션 목록은 `HubRememberService`에 조회 메서드가 없어 보류. 회원가입 소속 분류는 `hub_member` 컬럼 필요.
  공지 강조는 안내/주의 2택 (디자인은 3택이나 `HubNoticeService`가 `info`/`warn`만 받음)
- `links/` standalone 모듈(포트 8085)은 2026-06-23 스냅샷 그대로 — 이번 작업 미반영. 동기화 여부는 별도 판단
- 폰트는 Inter(Google Fonts) 기준 디자인이나 사내망 CDN 접근을 고려해 **CDN 링크를 넣지 않음**.
  설치돼 있으면 쓰고 없으면 시스템 폰트로 폴백

### 2026-08-13~14 Phase 1-B 문자 배치 발송 + 운영 장애 대응

> 운영 절차서: [docs/prod-deploy-phase1b.md](docs/prod-deploy-phase1b.md) · 운영 쿼리: [docs/sms-batch-ops.md](docs/sms-batch-ops.md)

#### Phase 1-B 문자 발송 서버 통합 — **완료 / 운영 반영(08-13 20:34)**
- 배치 발송 API `POST /api/counsel/sms/batch` 신설. 두 live 화면(상담리스트·입원상담)을 단일 배치 요청으로 이관
- 멱등 처리: `csm.sms_batch` 테이블(`inst_code`+`idem_key` UNIQUE)로 더블클릭·재시도 중복 발송 구조적 차단
- 상태 체계 신설: `READY → SENT/FAILED/UNKNOWN → DONE/ERROR`. 타임아웃은 FAILED가 아닌 **UNKNOWN**(재시도·환불 금지)
- refkey 재설계: `MP-{instCode}-{historyId}`. 콜백의 `substring(0,4)` 기관코드 가정 제거, 구형식은 폴백 유지
- 메시지 타입 서버 확정(`SmsMessageTypeResolver`) — 클라이언트가 보낸 type을 신뢰하지 않음(요금 사기 벡터 차단)
- 이력 확장: `cost`(전 단위 정수)·`billable`·`message_key`·`vendor_code`·`batch_id` + `refkey` UNIQUE
- 단가 파싱: BigDecimal → `setScale(0, HALF_UP)` → 전 단위 정수. 음수·비숫자·NULL은 폴백+WARN
- OTP 발송 이력 기록(`billable='N'`, `cost=0`, 본문 마스킹). 기존 `pwd-otp-` refkey는 콜백 매핑 100% 실패였음
- 기관별 테이블 collation 통일 + 전 기관 집계 뷰 `v_transmission_history_all`
- 예약발송 UI 숨김 (90일 실사용 0건, sendtime 형식 불일치로 미동작)
- 커밋: `bdd77e0` `26eec18` `53d06a4` `eaf7a44` `db111ed` `cb5eb36` `7345595` `ee03b31`

#### 콜백 라우팅 전환 — **완료(08-14 09:23)**
- 문제: `/api/external/SMSRequest`가 httpd에서 AJP 8009(레거시 ROOT.war)로 가서 csm-next 수신 0건.
  신규 `MP-` refkey를 레거시 파서가 못 읽어 결과 리포트 전량 유실될 상황이었음
- 조치: `httpd.conf`의 `<VirtualHost *:443>`에 정확 경로 예외 2줄 추가(기존 규칙보다 위)
- 검증: HTTPS로 빈 refkey 콜백 → csm-next 로그에 `callback ignored: empty refkey` 확인

#### mediplat 12.5시간 중단 — **복구 + 신버전 재배포 완료(08-16 14:06)**
- 원인: `PLATFORM_ADMIN_PASSWORD` 미주입 (`b11efa2`가 기본값 제거 → 신규 jar 배포 시 크래시 루프)
- 08-13 20:34 배포 → 08-14 09:0x 롤백까지 포털·SSO 중단. csm은 전 구간 정상
- 조치: env 추가 후 구버전(`bak-20260813-203439`)으로 복구
- 재발 방지: 절차서에 **0-0 필수 env 대조**, **2-E mediplat 헬스체크** 신설
- ✅ **신버전 재배포 완료 (08-16 14:06)** — 0-0 게이트가 `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`,
  `LOGIN_AES_KEY` **4개 추가 누락**을 사전에 잡아냈다. 그대로 배포했으면 같은 크래시 루프 재발.
  중첩 폴백(`${MEDIPLAT_DATASOURCE_URL:${SPRING_DATASOURCE_URL}}`)은 바깥 변수가 있어도 안쪽이 평가된다.
  같은 파일의 `MEDIPLAT_DATASOURCE_*`·`PLATFORM_COUNSELMAN_LOGIN_AES_KEY` 값을 폴백 이름으로 복제해 해결.
  기동 4.2초, 60초 헬스체크 `302` 안정

#### Phase 2 (콜백 엔드포인트 보호) — **조사 완료 / 구현 보류**
- 상세·재개 방법: [docs/phase2-hold.md](docs/phase2-hold.md)
- 콜백 발신 IP 실측 확보. Phase 3(지갑) 착수 전 필수

#### Phase F (토스 심사용 정책 문서) — **F-1 조사 완료 / 문서 미작성**
- 약관·개인정보처리방침·환불정책·사업자 footer **전부 없음**
- 환자 건강정보(민감정보) 대량 처리 확인. 동의 절차·보유기간·파기 구현 전무
- OpenAI(국외 이전)·NCP Clova로 건강정보 유출 경로 존재. OpenAI 사용량은 2.5개월 16건 $0.05로 미미
- 법적 지위(처리자 vs 수탁자) 결정이 선행되어야 문서 작성 가능

### 2026-08-09 배포 파이프라인 정비 + 운영 장애 대응 + mediplat 점검

> 세션 상세 기록: [docs/handoff-2026-08-09.md](docs/handoff-2026-08-09.md)

#### mediplat 앱 등록 설명칸 — **완료 / 운영 반영**
- 증상: 기관 관리 > 앱 등록에서 서비스명은 수정되는데 설명은 수정 불가
- 원인: 폼 state·저장 전송·서버 저장은 모두 정상이었고 **입력 UI만 누락**. 구 `admin.html`엔 있었으나 React 패널로 옮기며 빠짐
- 수정: [institution-admin-app.jsx:387](mediplat/src/main/resources/static/jsx/institution-admin-app.jsx#L387) 전체 폭 설명 입력칸 추가 (`cff7f4c`)

#### 배포 파이프라인 — mediplat 누락 해소 + 검증 게이트
- [x] `deploy-prod.yml`이 `prodWar`만 호출해 **mediplat이 prod 파이프라인에 아예 없었음** → `packageProdDeploy`로 전환, `mediplat.jar` 스테이징 추가 (`49a44af`)
  - 이 때문에 운영 mediplat이 6/18 빌드로 7주간 방치. 서버측 `deploy-nightly.sh`·`/etc/default/csm-next-deploy`는 이미 mediplat을 지원하고 있었음
- [x] 아티팩트 **체크섬 + zip 무결성 검증 게이트** prod/dev 양쪽 도입 (`6852146`, `d2375a1`)
  - 실제 장애 파일과 동일하게 잘라 재현 테스트 → `sha256sum -c` exit 1, `unzip -t` exit 9로 차단 확인
  - dev는 스테이징이 없어 즉시 라이브 덮어쓰기 → 첫 `cp` 전에 3개 아티팩트 일괄 검증
- [x] dev ↔ prod 완전 동기화 (백머지 후 격차 0/0)

#### 운영 장애 — `/csm` 전체 404 (08:35 발견 → 08:42 복구)
- 증상: CounselMan·병실현황판 SSO 진입 모두 실패, Tomcat 404, 앱 예외 로그 없음
- 원인: 02:30 야간 배포가 적용한 `csm.war`가 **127,920,134 → 52,287,189 bytes로 잘린 파일**. 컨텍스트 explode 실패
- 확정 근거: 동일 커밋 로컬 빌드는 정상(921 엔트리, 무결성 통과) / 문제 파일 `file` 결과 `Zip archive data`(앞부분만 온전) / 워크플로가 `cp`→`mv`를 완료했으므로 러너 수신 시점에 이미 잘려 있었음 → **아티팩트 전송 중 잘림**
- 배제된 가설: 디스크 풀(20G 여유), 이중 압축(재압축 시 118MB), SSO 서명·시크릿
- 복구: `csm.war.bak-20260809-023026`(8/5 빌드) 롤백 + explode 디렉터리 정리 + 재시작
- 재발 방지: 위 검증 게이트

#### mediplat 앱 전체 점검 (보안 > 성능 > 접근성 > 코드품질)
- CRITICAL 1 / HIGH 2 / MEDIUM 4 / LOW 2 도출 → P0-4·P0-5·P0-6 및 P3 항목으로 등록
- 양호 확인: SQL 전량 파라미터 바인딩, BCrypt, fleet 기기토큰(selector/validator + SecureRandom + 상수시간 비교), SSO HMAC-SHA256, 파일 업로드 path traversal 차단, IDOR 테넌트 스코프, 멀티테넌시 `inst_code` 일관 적용

### 2026-06-02 P0 일괄 처리 (헤더 / 권한 UI / 스케줄 영속화)

#### [P0-1] 헤더 고정 — **완료(close)**
- 헤더 `position: sticky; top: 0`은 [layout.css](src/main/resources/static/assets/css/layout.css)에 이미 적용돼 있어 "콘텐츠 영역 확보 + 고정" 목적 달성
- 헤더 높이 64px → 48px 축소는 **현행 64px 유지로 결정** (운영자: "64px가 시원해서 보기 좋음"). 코드 변경 없음

#### [P0-2] cancer-treatment VIEWER/MEMBER 권한 UI — **완료 + dev 검증 통과**
- 근본 원인: 권한 라디오가 라우팅 안 되는 `admin.html`(Thymeleaf)에만 있었음. 실제 렌더되는 React 화면(`institution-admin-app.jsx`)엔 누락 (`ed3bdb5`가 잘못된 템플릿에 추가)
- [x] React "사용자 권한" 패널에 암센터 권한 라디오(`기본/전체사용(MEMBER)/조회만(VIEWER)`) 추가 + `/admin/users/service-role` 연동
- [x] `/admin/async-data` payload에 `userServiceRoleOverrides` 추가
- [x] 회귀 테스트 1건 (`MediplatControllerTest.adminAsyncData_includesUserServiceRoleOverrides`)
- [x] dev 브라우저 검증 통과 (VIEWER 강등 → 등록 버튼 숨김)

#### [P0-3] cancer-treatment 스케줄 DB 영속화 + 환자 FK 연동 — **완료**
- [x] `TreatmentScheduleRepository`(JDBC) 신설 — `ct_treatment_schedule` 사용, 전 쿼리 `inst_code` 스코프 (테넌트 격리)
- [x] 인메모리 `CopyOnWriteArrayList` + 하드코딩 시드 제거 → **재시작 시 소실 문제 해결**
- [x] `patientId` FK — 모달에서 환자 선택 시 id 캡처/저장, 자유입력 시 링크 해제
- [x] 환자명·병동은 조회마다 `ct_patient` live join (항상 최신), 자유입력/삭제 환자는 `patient_name_snapshot` fallback
- [x] 치료명/옵션은 자유텍스트 스냅샷 컬럼(`treatment_name_snapshot`/`treatment_option_snapshot`)에 저장 — `treatment_type_id` FK 전환은 별도 작업으로 미룸
- [x] prod(`SQL_INIT_MODE=never`) 대응 — `CancerTreatmentSchemaService`가 테이블/컬럼 자동 보강 (수동 DBA 불필요)
- [x] 통합 테스트 7건 (영속성·테넌트 격리·status 매핑·live-join 이름 갱신·스냅샷 fallback·시간 정규화·삭제)
- 프론트 JSON 계약 유지 → 캘린더·대시보드 무수정 동작 (`patientId`만 추가)

### 2026-05-29 입원상담 SMS 최근 전송 내역 표시·레이아웃 개선

#### 입원상담 (`inpatient-consultation.html`) — 최근 전송 내역 수정
- [x] **보낸 내용 미표시 버그 수정** — 프론트 매핑이 백엔드 반환 키와 불일치(`message`/`sent_at` 참조)하여 본문·날짜가 빈 값이던 문제 → `contents`/`created_at` 기준으로 정정
- [x] **전송 직후 내역 미갱신 버그 수정** — `sendSms()` 성공 시 `refreshSmsHistory()` 호출 추가
- [x] **날짜 포맷** — `formatSmsDate()` 추가, ISO(`2026-04-21T14:56:52`) → `YYYY-MM-DD HH:mm` 표시
- [x] **번호 포맷** — 기존 `formatPhone()` 재사용, 하이픈 표기(`010-1234-5678`)
- [x] **전송 종류 배지** — `send_type` 매핑 추가, SMS/LMS/MMS 배지 표시 (MMS 본문 없으면 `(이미지 전송)` 안내)
- [x] **패널 너비 확대** — 우측 컬럼 280px → 360px, 메타 줄 `white-space: nowrap` 으로 번호·날짜 줄바꿈 방지
- 참고: MMS 이미지 미리보기는 서버 부하·보관 비용 우려로 **철회**. "이미지 첨부 (MMS)" 버튼은 여전히 핸들러 없는 placeholder

### 2026-05-22 ~ 2026-05-26 채팅 기관별 격리 + 토큰 URL + 암센터 권한 관리

#### CSM 채팅 — 기관별 격리 + 토큰 URL ([커밋 be3bb0f](https://github.com/coresolution-develop/counselman/commit/be3bb0f), [870bdaa](https://github.com/coresolution-develop/counselman/commit/870bdaa))
- [x] **WebSocket 토픽을 기관별로 네임스페이스** — `/topic/chat/{token}/{roomId}`, `/topic/admin/rooms/{token}`. 토픽 SUBSCRIBE 시 새 `ChatWsAuthInterceptor`가 발신자 신원과 자원 소유권 검증
- [x] **관리자 chat API를 세션 inst로 강제** — `/api/chat/rooms` · `/room/{id}/join` · `/room/{id}/close` 가 더 이상 `?inst=` 쿼리를 신뢰하지 않음
- [x] **고객 chat API에 채팅방 소유권 검증** — `/room/{id}/messages` · `/room/{id}/status` 가 kakao_id로 본인 방인지 확인
- [x] **WebSocket SEND 발신자 신뢰성 확보** — payload의 senderType/senderName 무시, 세션 주체로 서버가 직접 결정 (고객의 COUNSELOR 사칭 차단)
- [x] **inst 코드를 16자리 불투명 토큰으로 대체** — `csm.chat_inst_token` 테이블 + `ChatTokenService`, 부트스트랩 시 기관별 토큰 자동 발급. 임베드 URL은 `?t=Xj7nQ...` 형태로 영구 고정
- [x] **레거시 `?inst=` URL 자동 redirect** — `?inst=falh` 도 case-insensitive resolve 후 토큰 URL로 302
- [x] **기관명 동적 렌더링** — 하드코딩된 "효사랑가족요양병원" 제거, `mp_institution.inst_name` 조회 결과를 모델로 주입
- [x] **회귀 테스트 20케이스** — `ChatWsAuthInterceptorTest`(13), `ChatWebSocketControllerTest`(7)

#### Cancer-treatment 권한 관리 (VIEWER / MEMBER) ([커밋 72c10f9](https://github.com/coresolution-develop/counselman/commit/72c10f9), [64fa5d9](https://github.com/coresolution-develop/counselman/commit/64fa5d9), [50a35d4](https://github.com/coresolution-develop/counselman/commit/50a35d4), [ed3bdb5](https://github.com/coresolution-develop/counselman/commit/ed3bdb5), [a92809d](https://github.com/coresolution-develop/counselman/commit/a92809d))
- [x] **`mp_user_service_role` 테이블 신설** — `(user_id, service_code) → role_code (VIEWER|MEMBER)`. 부트스트랩 시 PLATFORM_ADMIN 자동 MEMBER 시드
- [x] **SSO launch URL에 role 포함** — CANCER_TREATMENT 서비스만 5-필드 HMAC 페이로드(`inst|userId|expires|target|role`). CSM/RoomBoard/SeminarRoom 시그니처는 기존 4-필드 유지(receiver 무영향)
- [x] **cancer-treatment 측 role 검증 + 쓰기 가드** — `SessionUser.role`, `requireMember(session)` 헬퍼, 18개 write 엔드포인트(POST/PUT/PATCH/DELETE) 가드. GET 엔드포인트는 VIEWER 통과
- [x] **기관 관리자/기관 사용자 기본 MEMBER** — `resolveServiceRoleByUsername` fallback: PLATFORM_ADMIN / INSTITUTION_ADMIN / USER → MEMBER, ROOM_BOARD_VIEWER 등 → VIEWER. 명시 행이 있으면 그 값이 우선
- [x] **mediplat admin UI — 사용자별 service-role 오버라이드** — `/admin` "사용자 기능 권한" 카드에 라디오 (기본/MEMBER/VIEWER), `POST /admin/users/service-role` 엔드포인트
- [x] **cancer-treatment 프론트 VIEWER UI 가드** — `body[data-user-role]` + `role-guard.js` (CSS 주입 + click intercept). 등록/저장/삭제 버튼 숨김 + 인라인 편집 차단
- [x] **회귀 테스트 6케이스** — `SsoServiceTests` (legacy 4-필드 launch, 5-필드 launch, role 위변조, role drop, 만료, canonical 정규화)

#### Cancer-treatment 운영 편의 ([커밋 17b0c11](https://github.com/coresolution-develop/counselman/commit/17b0c11), [05794f4](https://github.com/coresolution-develop/counselman/commit/05794f4))
- [x] 환자명·치료종류 자동완성 (서버 검색 + 키보드 ↑↓Enter Esc, 차트번호·병실 표시)
- [x] 일별 스케줄 인쇄 레이아웃 (`@page A4`, 치료정보·메모 노출)
- [x] 스케줄 드래그&드롭 시간 변경 + `PATCH /api/treatment-schedules/{id}/time` 엔드포인트
- [x] 암센터 사용자 매뉴얼 (`docs/cancer-treatment-user-guide.md`)
- [x] JS API ReferenceError 핫픽스 — `API` 변수가 IIFE 안에 갇혀 saveScheduleModal/deleteScheduleFromModal에서 깨지던 문제 (var 선언을 IIFE 밖으로 이동)

#### MediPlat 운영 자잘한 추가 ([커밋 47b0c28](https://github.com/coresolution-develop/counselman/commit/47b0c28))
- [x] 포털 헤더에 "로그인 기록" 바로가기 버튼 추가

#### Dev 인프라 (운영자 시스템 수정 — 코드 변경 없음)
- [x] mediplat systemd unit에 `CANCER_TREATMENT_BASE_URL=https://dev.sosyge.net/cancer-treatment` 추가
- [x] cancer-treatment systemd unit에 `CANCER_TREATMENT_MEDIPLAT_PORTAL_URL=https://dev.sosyge.net/portal` 추가
- [x] nginx에 `location /cancer-treatment/` proxy_pass 추가 (백엔드는 포트 18083)

### 2026-05-19 MediPlat 직원 관리 확장 (감사 로깅 / 사용자 필드)

- [x] **로그인 이력 감사 기능** — 기관 관리자/슈퍼관리자가 직원 로그인 활동을 조회 ([커밋 b704d8b](https://github.com/coresolution-develop/counselman/commit/b704d8b))
  - `mp_login_audit` 테이블 신규 (MySQL + H2 DDL, 인덱스: `(inst_code, login_at)`, `(username)`)
  - `PlatformStoreService.recordLogin / recordLogout / listLoginAudits` 추가, `KeyHolder`로 audit id 반환
  - 로그인 성공 시 audit row 생성, 세션에 `mediplatLoginAuditId` 저장
  - `HttpSessionListener.sessionDestroyed`로 명시적 로그아웃 + 세션 만료 둘 다에서 logoutAt/sessionSeconds 기록
  - `/admin/login-audit` 페이지 신규 (Thymeleaf 서버 렌더링, 기관·계정명·기간 필터, 최대 200건)
  - 권한: `PLATFORM_ADMIN`은 전체, `INSTITUTION_ADMIN`은 자기 기관 강제 잠금
  - 단위 테스트 3종 (insert / logout 멱등성 / 기관 격리)
- [x] **직원 등록 시 이메일·휴대폰 입력 기능** — 기관 관리자/슈퍼관리자 사용자 등록 폼에 두 필드 추가 ([커밋 3da97c7](https://github.com/coresolution-develop/counselman/commit/3da97c7))
  - `mp_user` 테이블에 `email VARCHAR(500) NULL`, `phone VARCHAR(500) NULL` 추가 (`ALTER TABLE IF NOT EXISTS` 마이그레이션 포함)
  - CSM `us_col_10`(연락처) / `us_col_11`(이메일)과 자료형·검증 정책 동일 (느슨한 자유 입력)
  - `PlatformUser` 모델 확장, 5곳의 SELECT 매핑을 `mapPlatformUser` 헬퍼로 통합
  - `saveUser` 오버로드 + `bulkSaveUsers` Map 키 `email`/`phone` 지원, 빈 문자열은 NULL 저장
  - React 단건 등록/수정 폼에 두 입력 행 추가 (`type=email`, `type=tel`, `inputMode`, `autoComplete`)
  - `admin.html` 대량 CSV 5/6번 컬럼으로 email/phone 처리 (해당 페이지는 라우팅되지 않은 상태)
  - 단위 테스트 4종 (단건 저장 / 빈값 NULL 처리 / 대량 저장 / 업데이트)

### 2026-05-19 비밀번호 찾기 / 변경 통합

- [x] **SMS OTP 기반 비밀번호 찾기 + 본인 비밀번호 변경** — `/findpwd`에 이메일 링크 / SMS 인증번호 라디오 선택, 로그인된 사용자는 헤더 사용자 메뉴 → `/my/account`에서 본인 비밀번호 변경 ([커밋 b88fa41](https://github.com/coresolution-develop/counselman/commit/b88fa41), [커밋 c867fb9](https://github.com/coresolution-develop/counselman/commit/c867fb9))
  - `CsmSmsOtpService` 신설 — 6자리 OTP, 5분 TTL, 5회 시도 제한 (in-memory `ConcurrentHashMap` + `@Scheduled` purge)
  - `PageController.postFindpwd`에 `channel=email|sms` 분기, `/findpwd/verify-otp` 엔드포인트 추가 — OTP 검증 성공 시 `CsmPasswordResetTokenService` 토큰 발급 후 `/ResetPwd` 자동 이동
  - SMS 발신번호는 `csm.phone_number_{inst}` 첫 행 사용, Bizppurio 게이트웨이로 발송
  - `MeApiController.POST /api/me/password` — 현재 비밀번호 검증 후 AES 업데이트 + `mp_user` bcrypt 동기화. 변경 후 강제 로그아웃 없이 그대로 사용
  - design 톤의 `/my/account` 페이지 신설 (read-only 내 정보 카드 + 비밀번호 변경 카드)
  - `chrome.js` 헤더 `header__user` 버튼에 클릭 핸들러 추가 → `/my/account` 이동
  - MediPlat `design/Login.html`에 `findPwdUrl` 모델 주입 (`platform.bootstrap.counselman-base-url` 기반), `login-app.jsx`의 forgot 링크 연결

- [x] **Findpwd / ResetPwd 디자인 리뉴얼** — MediPlat 로그인 톤(Pretendard, 라이트 그레이/민트 그라데이션 배경, 흰 반투명 카드, 네이비 액센트)으로 재작성. 라디오 버튼 미표시 + "로그인" 링크 가림 + 옛 modal 스택 4종 → 세그먼트 컨트롤 + 인라인 alert로 정리 ([커밋 5c4b489](https://github.com/coresolution-develop/counselman/commit/5c4b489))
  - 모바일 480px 이하 padding/폰트 축소, `prefers-reduced-motion` 대응, `role="alert"` 등 접근성 보강
  - ResetPwd: 토큰 만료/무효 상태도 danger 아이콘 카드 + 복귀 버튼으로 명확하게

- [x] **이메일/휴대폰 형식 검증 + 마스킹 표시** — DB에 garbage 값 저장돼 있을 때 명확히 안내 ([커밋 600708b](https://github.com/coresolution-develop/counselman/commit/600708b))
  - `AuthContactValidator` 유틸 신설 — RFC-5322 lite 이메일 정규식, 한국 휴대폰 정규식(`^01(?:0|1|[6-9])\d{7,8}$`)
  - 이메일 채널: 형식 검증 실패 시 "등록된 이메일 형식이 올바르지 않습니다. 관리자에게 비밀번호 초기화를 요청해 주세요" 안내. 성공 시 응답 `msg`에 마스킹 주소(`al***@gmail.com`) 포함
  - SMS 채널: 기존 길이만 ≥10 → 010/011/016~019 prefix + 정확히 10~11자리. `phoneMask` 응답으로 OTP 화면에 `010-****-1234` 표시
  - 인라인 `maskPhone` 헬퍼 제거, `AuthContactValidator.maskKrMobile`로 일원화

- [x] **`csm.base-url` 명시 설정으로 Host Header Injection 방어** — 비밀번호 재설정 이메일에 포함되는 reset 링크가 `request.getServerName()` 단독 의존 → 공격자가 임의 Host 헤더로 도메인 조작해 토큰 탈취 가능했던 취약점 차단 ([커밋 884a8d9](https://github.com/coresolution-develop/counselman/commit/884a8d9))
  - `csm.base-url` 프로퍼티 추가 (env: `CSM_BASE_URL`). 설정값 우선 사용, 빈 값일 때만 request 헤더로 폴백
  - 환경별 default: `prod=https://csm.sosyge.net/csm`, `local=http://localhost:8081/csm`, `dev`=빈 값 (운영자가 `CSM_BASE_URL` 환경변수로 dev 도메인 주입)
  - `PageController.buildResetLink()` / `resolveCsmBaseUrl()` 메서드로 로직 분리, trailing slash 정규화

- [x] **회귀 테스트 신규 4개 클래스 추가** (총 30+ 케이스 통과)
  - `CsmSmsOtpServiceTest` — OTP 발급/검증/만료/시도제한/재발급
  - `MeApiControllerTest` — 미인증 거부, 현재 비밀번호 mismatch, 동일 비밀번호 거부, `mp_user` sync 호출
  - `AuthContactValidatorTest` — 이메일 valid/invalid, 010/011/016~019 prefix, 길이/국제번호/유선번호 거부, 마스킹 edge case
  - `PageControllerResetLinkTest` — 설정값이 request보다 우선, trailing slash 제거, 빈/공백 폴백, 포트 80/443 생략

### 2026-05-18 운영 핫픽스 (기관 등록 / 공지 / 문자관리 통합)

- [x] **기관 등록 페이지 UI 깨짐 수정** — 새 디자인 사이드바에서 메뉴 라벨이 보이지 않던 문제 해결 ([layout-modern-shell.css](src/main/resources/static/css/csm/Include/layout-modern-shell.css))
  - `cate.css`의 `.nav_link > span { width: 100% }` 가 아이콘 span을 100% 폭으로 늘려 라벨을 width 0으로 찌그러뜨림 → `.nav_section .nav_link.nav-item > .nav-item__icon { width: 20px; flex: 0 0 20px }` 명시
  - 같은 cate.css의 `::before` 의사요소가 모든 `<span>` 직계 자식에 legacy 아이콘 bg-image를 붙여 아이콘이 중복 표시됨 → `content: none` 으로 무력화
  - layout.html의 `csm_header` fragment가 admin 페이지에도 누출되어 빈 헤더(177px)가 공간 차지 → `body:not(.counsel-list-modern) > header#csm-header { display: none }`
  - 하단 액션바가 `left:0; width:100%`로 사이드바를 덮음 → `left: 230px; width: calc(100% - 230px)` + collapse/모바일 분기
- [x] **기관 수정 popup → modal 전환** — `window.open` 대신 인라인 모달로 전환 ([admin.html](src/main/resources/templates/csm/core/admin/admin.html), [admin.js](src/main/resources/static/js/csm/core/admin/admin.js))
  - 기존 `/csm/core/modifyinstPopup` HTML 엔드포인트를 그대로 fetch + `DOMParser`로 input 값 추출 (백엔드 변경 없음)
  - ESC/오버레이 클릭/취소 핸들러, 저장 시 `/csm/core/modifyinst/post/{id}` 호출 후 reload
- [x] **공지사항 시스템 통합** — 새 디자인의 `/notices`(기관 자체 공지)와 `/notice`(core 배포 공지) 분리 문제 해결
  - 일반 기관 사용자가 사이드바 "공지사항" 클릭 시 자체 inst_notice 테이블만 조회해 core 공지가 안 보이던 문제
  - `designNotices()` 컨트롤러를 `listInstNotices` → `listInstitutionNotices` 로 전환하여 core_notice 데이터 표시 ([PageController.java](src/main/java/com/coresolution/csm/controller/PageController.java))
  - `pinned_yn='Y' → pinned (boolean)` 매핑, author="본사", `canWrite=false`로 일반 기관은 작성 비활성
  - core 사용자는 `/core/notice`로 redirect
- [x] **공지 팝업 — core 공지 기반으로 전환** — `getNoticesPopup()`이 옛 `listInstNotices`(기관 자체 공지)만 보여주던 문제
  - `listInstitutionNotices` + `popup_yn='Y'` & `read_yn≠'Y'` 필터로 변경
  - core 작성 공지의 "팝업" 체크박스가 실제로 일반 기관 사용자에게 자동 표출됨
- [x] **상용구 관리 → 새 디자인 모달 통합** — 옛 페이지(`/smsSetting`) 의존 제거 ([design/message-management.html](src/main/resources/templates/design/message-management.html))
  - 페이지 헤더에 "+ 상용구 추가" 버튼, 각 행 hover 시 수정/삭제 액션
  - 작성/수정/삭제 모달 통합 (`/smsInsert`, `/smsUpdate`, `/smsDelete`)
  - 삭제 시 브라우저 `confirm()` 대신 디자인 confirm 모달
  - `init` 중복 가드 (`dataset.bound`) — `DOMContentLoaded` + `turbo:load` 둘 다 호출되어 alert 두 번 뜨던 버그 수정
- [x] **서명관리 탭 신규 추가** — 새 디자인에 누락된 서명관리 메뉴 추가
  - `designMessage()` 컨트롤러에 `signature` 모드 + `/message/signature` 경로 추가
  - 새 디자인 페이지에 서명 카드 목록, 작성/수정/삭제 모달 (`/InsertCard`, `/UpdateCard`, `/DeleteCard`)
  - sent/reserved 발송내역 테이블이 signature 모드에서 잘못 표시되던 조건 (`messageMode != 'template'` → `messageMode == 'sent' or 'reserved'`)

### 2026-05-14 운영 핫픽스 + 도메인 cutover

- [x] **Bug fix — `/csm/users` 역할 수정 반영 안 되던 문제** — `UserApiController.toLong()`이 JSON String을 거부해 `<select>`에서 변경한 `roleId`가 silently dropped → `Number`와 `String` 모두 수용하도록 수정 ([UserApiController.java:289](src/main/java/com/coresolution/csm/controller/UserApiController.java:289))
- [x] **Bug fix — `/csm/roles` 사용자 추가 모달 빈 목록** — `GET /api/roles/users`가 `WHERE us_col_09 = 1`을 사용해 legacy NULL/0 행이 누락 → `us_col_09 != 2` (사용자 목록 페이지와 일치)로 변경 ([RolesApiController.java:315](src/main/java/com/coresolution/csm/controller/RolesApiController.java:315))
- [x] 회귀 테스트 2종 추가 — `UserApiControllerToLongTest`, `RolesApiControllerUserFilterTest`
- [x] **`scripts/deploy-prod.sh` 신규 작성** — PROD_HOST 확인 프롬프트, prod-preflight 자동 호출, timestamped 백업, `--dry-run`, 롤백 명령 안내
- [x] **운영 도메인 cutover** — `csm.sosyge.net` 트래픽을 레거시 ROOT.war(AJP 8009)에서 신규 csm-next/mediplat-next/cancer-treatment-next로 전환. 점진 cutover로 `/api/external/*`만 레거시 유지
- [x] httpd.conf ProxyPass 매핑 — `/csm/*` → 18081, `/resources/*` → 18081/csm/, `/api/external/*` → 8009 (레거시 유지), `/` → 18082 (MediPlat)
- [x] **새벽 03:00 KST 자동 maintenance** — systemd timer (`nightly-maintenance.timer`) — csm-next restart + httpd reload
- [x] STT/요약 환경변수 정정 — `CLOVA_*` 변수명이 코드와 불일치 → 정식 이름 `NCP_CLOVA_INVOKE_URL`, `NCP_CLOVA_SECRET_KEY`, `OPENAI_API_KEY`로 csm-next.env 추가
- [x] SMS 발신번호 Bizppurio 콘솔 등록 (Bizppurio 사용 시 발송 가능)
- [x] MediPlat SSO base URL 도메인 보정 — `MEDIPLAT_PLATFORM_BASE_URL`에서 포트(`:18082`) 제거 후 도메인만 (`https://csm.sosyge.net`)

### 공통
- [x] CSRF 메타 태그 적용 (전체 design 페이지)
- [x] Alpine.js x-teleport 기반 모달 구조 정립
- [x] **`/design/*` URL 정리** — 디자인 페이지를 운영 URL로 승격 (`/counsel/*`, `/notices`, `/message`, `/ward-status`, `/users`, `/roles`)
- [x] 로그인 → `/csm/counsel/list?page=1&perPageNum=10&comment=`로 디자인 페이지 진입
- [x] 기존 `/design/*` URL은 301 redirect로 유지 (북마크 호환)
- [x] **역할 기반 네비게이션 필터링** — `permission_master.menu_key` 기준으로 사이드바 메뉴 노출/숨김 (`resolveAccessibleMenuKeys`)
- [x] **RBAC 권한 체크** — `@PreAuthorize` 백엔드 + 프런트 nav 필터링 연동 완료
- [x] **Turbo + Alpine 초기화 충돌 수정** — `/users`, `/access`, `/roles`, `/counsel/log-settings` Turbo 이동 후 blank 문제 해결
- [x] **관리자 nav active 상태** — `/users`, `/access`, `/room-board/manage` 진입 시 관리자 메뉴 active 표시
- [x] **상담 일지 관리 경로 정리** — `/admin/counsel/log-settings` → `/counsel/log-settings` 승격 (레거시 redirect 유지)

### 모바일 반응형
- [x] 상담 리스트 (`consultation-list.html`) — 768px / 480px 미디어 쿼리, 캘린더 7열 고정, FAB
- [x] 상담 접수 (`consultation-intake.html`) — 1열 전환, 폼 우선 노출, 44px 터치 타겟
- [x] 입원상담 (`inpatient-consultation.html`) — 2단 → 1열 전환, SMS 모달 1열
- [x] 병실현황판 (`ward-status.html`) — 테이블 카드형 전환, 병상 슬롯 row-flow 그리드
- [x] 상담 통계 (`consultation-stats.html`) — 768px / 480px 미디어 쿼리, 차트 높이 조정
- [x] 모바일 사이드바 — Turbo 이동 / pageshow 시 자동 닫힘

### 사용자 관리 (`user-management.html` + `UserApiController.java`)
- [x] 사용자 추가 (POST `/api/users`) — 비밀번호 AES 암호화, RBAC 역할 배정
- [x] 사용자 수정 (PUT `/api/users/{id}`) — 역할 교체 포함
- [x] 비밀번호 초기화 (POST `/api/users/{id}/reset-password`) — 임시 비밀번호 발급 + 클립보드 복사
- [x] 사용자 비활성화 (DELETE `/api/users/{id}`) — 소프트 삭제
- [x] 역할 드롭다운 DB 연동 — `GET /api/roles` 로 `role_{inst}` 테이블 데이터 출력

### 역할 관리 (`role-management.html`)
- [x] 역할 목록 DB 연동 (`role_{inst}` 테이블)
- [x] 역할 추가 / 이름 수정 / 복제
- [x] 권한 변경사항 저장 (`role_permission_{inst}`)
- [x] 역할에 사용자 추가 (`user_role_{inst}`)
- [x] permission master fallback 처리 (테이블 비어있을 때 기본값)

### 입원상담 (`inpatient-consultation.html`)
- [x] 상담자 이름 표시 — ID 대신 이름(`resolveCounselorDisplayName`)
- [x] 전화번호 자동 하이픈 포맷 (`formatPhone`)
- [x] 보호자 전화번호 옆 SMS 전송 버튼 추가
- [x] SMS 전송 모달 — Mediplat 6 신규 디자인 마이그레이션
- [x] SMS 발신번호 / 상용구 / 서명 DB 연동
- [x] SMS 전송 백엔드 연동 (Bizppurio), SMS/LMS 자동 판별
- [x] 최근 전송 내역 조회, 예약발송 UI, 상용구 저장
- [x] 동적 카테고리 체크박스/라디오 토글 연동
- [x] 상담기록 초기화 버튼

### 상담 접수 (`consultation-intake.html`)
- [x] 상단 요약 카드 백엔드 연동 — 전체·대기·입원연계·취소 건수 실시간 반영

### 입원예약관리 (`admission-reservation.html`)
- [x] 페이지 신규 구현 (design 시스템 적용)
- [x] 백엔드 연동 — 입원예약 목록, 가용 병실 목록
- [x] 보호자/연락처 연동 (AES 복호화)
- [x] 저장·입원완료·예약취소 API 연동
- [x] 입원완료 후 병실현황판 동기화 (스냅샷 생성)
- [x] **병실현황판 팝업 연동** — 드롭다운 옆 버튼 클릭 → 현황판 팝업에서 병실 선택 → 자동 반영 (postMessage)

### 병실현황판 (`ward-status.html`)
- [x] 실 데이터 연동 (`RoomBoardView` / `RoomBoardWardView` / `RoomBoardRoomView`)
- [x] 사이드바·헤더 연결, 최신현황·관리화면 버튼 추가
- [x] `/room-board/manage` 경로 승격 (레거시 `/admin/room-board` redirect 유지)
- [x] 재원환자 병상 슬롯 색상 (입원가능/재원중/입원예약)
- [x] **퇴원예고 기능 구현** — 퇴원예정일 등록·수정·삭제, 리스트 표시 (`discharge-notice.html`)
- [x] **병실 카드 퇴원예고·오후 가능 수 표시** — 병상 슬롯에 오전/오후 퇴원 마크 표시
- [x] **오전 퇴원 → 오후 입원 가능 연동** — "만실" 대신 "오후 입원 가능"(앰버) 표시, 행 흐림 해제, 필터 포함, 팝업 선택 버튼 활성화

### 병실현황판 ↔ 입원예약 ↔ 퇴원예고 연동 버그 수정 (`RoomBoardService.java`)
- [x] 미사용 병실 드롭다운 노출 버그 — `use_yn != 'n'` → `use_yn = 'Y'`
- [x] 입원완료 환자 현황판 누락 버그 — `uploaded_at` 타임스탬프 비교 → `snapshot_date` 날짜 비교
- [x] 신규 스냅샷 후 퇴원예고 등록 불가 버그 — `rbs_id` 제약 제거, `rbp_id` 단독 조회
- [x] 입원예약 경로 환자 퇴원 슬롯 미표시 버그 — 이름 기반 fallback 인덱스 추가

### 링크 관리
- [x] **분류 순서 편집 기능** — 링크 카테고리 정렬 순서 직접 수정 가능
- [x] **허브 UI 전면 리디자인 + 환경·분류 컬럼** (2026-08-16) — 상세는 위 "완료된 작업" 최상단 항목 참고

### 챗봇 (`chat-page.html` + `ChatApiController.java`)
- [x] 카카오 OAuth2 로그인 연동
- [x] WebSocket (STOMP/SockJS) 실시간 채팅
- [x] FAQ 패널 — 카테고리 필터, 아코디언 표시
- [x] 관리자 채팅 수신 연동
- [x] **FAQ 우선 응답 흐름** — 첫 메시지 전송 시 키워드 검색 → 결과 표시 → [도움이 됐어요 / 상담사에게 연결] 선택
- [x] **챗봇 상담 접수 플로우** — 이름→연락처→내용→확인 순서, `counsel_reservation` 테이블 자동 저장 (created_by: 챗봇)
- [x] **모바일 키보드 UX (iOS · Android 통합 대응)** — `font-size: 16px` 확대 방지, `.app { position: fixed }` + `visualViewport` API로 iOS offsetTop 스크롤 및 Android 뷰포트 축소 양쪽 대응, `resize` · `scroll` 이벤트 모두 구독, `env(safe-area-inset-bottom)` 제스처 바 여백 적용
- [x] **홈 화면 추가** — 온라인 상담 / 상담 접수 / 상담사 연결 선택 카드
- [x] **상담 접수 폼** — 성함·연락처·상담 내용 입력 후 서버 제출, 접수 완료 화면
- [x] **브라우저 알림(Notification API)** — 새 채팅·상담 요청 수신 시 Push Notification
- [x] **연락처 형식 검증** — `010-xxxx-xxxx` 패턴 클라이언트 검증 추가

### 공지사항
- [x] **DRAFT / PUBLISHED 상태 관리** — 임시저장·게시 상태 컬럼, 필터 탭, 모달 토글

### 퇴원예고 자동완료
- [x] **AM 퇴원 자동 처리** — 당일 13:00에 `PLANNED → COMPLETED` (`DischargeNoticeScheduler`)
- [x] **PM 퇴원 자동 처리** — 다음날 00:05에 전날 PM `PLANNED → COMPLETED`

### 서류관리 (`document-management.html`)
- [x] **취소 버튼 404 버그 수정** — `returnUrl` 컨텍스트 패스 누락 → 컨트롤러에서 `documentsReturnUrl` 모델 주입으로 해결
- [x] **서류 종류(doc_type) 다중 템플릿 지원** — DB 마이그레이션, pill 탭 UI, 종류별 저장/적용/삭제
- [x] **DB 마이그레이션 호환성 수정** — `ADD COLUMN IF NOT EXISTS` (MySQL 8.0+만 지원) → `ensureTableColumn()` 헬퍼로 교체
- [x] **Turbo 이중 초기화 버그 수정** — Alpine CDN `data-turbo-eval="false"` 누락으로 `x-for` 8개 pill 렌더링 → 수정 완료
- [x] **pill 색상 버그 수정** — `color: var(--text-secondary)` (흰색), active `background: var(--brand)` (투명) → 하드코딩 수정
- [x] **캔버스 → TipTap 리치 에디터 전환** — 자유 배치 드래그/드롭 캔버스 제거, Word 방식 TipTap 에디터로 교체
  - Bold / Italic / Underline / 제목 1~3 / 정렬 / 글자색 / 표 삽입
  - `FieldChip` 커스텀 노드 — `{{환자명}}` 스타일 인라인 칩, `data-field-key` 속성으로 admissionPledge.html 주입과 호환

### MediPlat — 기관 관리자
- [x] 기관 관리자 페이지 신규 (`Institution-admin.html`, `institution-admin-app.jsx`)
- [x] `PlatformStoreService.institutionExists()` / `setInstitutionUseYn()` 추가
- [x] 신규 기관 저장 시 COUNSELMAN 자동 활성화
- [x] `POST /admin/institutions/status` (활성/비활성), `POST /api/admin/institutions` (JSON API)
- [x] **기관별 로그인 이력 감사** (2026-05-19) — `/admin/login-audit`, `mp_login_audit` 테이블
- [x] **직원 이메일·휴대폰 필드** (2026-05-19) — `mp_user.email`/`phone`, CSM 자료형 호환

---

## 🔍 검증 필요 (브라우저 확인 미완료)

- [x] ~~**MediPlat 기관 관리자 사용자 권한 저장 오류**~~ — **2026-05-14 해결**. 실제 원인은 CSM의 두 버그(`UserApiController.toLong`이 String roleId 거부, `RolesApiController.getAllUsers`의 `us_col_09 = 1` 필터). 핫픽스 두 개로 dev/prod 검증 통과
- [ ] **채팅 기관별 격리 + 토큰 URL** (2026-05-22) — `./scripts/deploy-dev.sh` 후 (a) `?inst=falh` → `?t=...` redirect, (b) 다른 기관 토픽 SUBSCRIBE 차단, (c) 고객 사칭 차단(senderType=COUNSELOR 보내도 USER 표시), (d) 다른 기관 토큰으로 접속해도 기관명 동적 표시
- [x] ~~**cancer-treatment VIEWER/MEMBER 권한** (2026-05-26)~~ — **2026-06-02 검증 통과**. admin React UI에 권한 라디오 누락이 실제 원인이었고, 추가 후 VIEWER 강등 → 등록 버튼 숨김 확인
- [ ] **CSM 허용 버튼** (`/csm/access`) — toggle POST가 `mp_user_service` 행을 실제로 생성/수정하는지 확인 필요 (현재 FALH 데이터 없어 모두 비활성 상태로 표시됨)
- [ ] **서류관리 TipTap 에디터** — 표 삽입·필드 칩 삽입 → 저장 → 입원서약서(`admissionPledge.html`) 렌더링 흐름 브라우저 E2E 검증
- [ ] **채팅 페이지 폰트 CORS** — `common.css`의 `fonts.gstatic.com/ea/notosanskr/v2/` URL이 deprecated되어 CORS 에러 발생. `https://fonts.googleapis.com/css2?family=Noto+Sans+KR` CDN 또는 로컬 폰트로 교체 필요
- [ ] **기본 아바타 이미지 누락** — 채팅 페이지에서 `/img/default-avatar.png` 404 발생. `src/main/resources/static/img/` 하위에 기본 아바타 이미지 파일 추가 필요
- [ ] **챗봇 FAQ 검색 — 비로그인 접근** — 현재 카카오 로그인 후에만 FAQ 패널 노출. 로그인 전에도 FAQ 조회 가능하도록 검토 필요

---

## 🔄 진행 예정 작업

### 🔧 공통 / 헤더
- [ ] 헤더 — 검색 기능 용도 확정 (전역 검색? 상담 검색?)

### 🏥 입원상담
- [ ] 입원서약서 페이지 연동 (버튼 클릭 → 서약서 페이지 열기/상태 저장)
- [ ] 병실현황판 연동 (버튼 클릭 → 현황판 팝업 모드로 열기)
- [ ] 첨부파일 업로드 — 다중 업로드 지원 (녹음 파일, 소견서·CT·MRI 등)
- [ ] 음성 녹음 기능 (MediaRecorder API)
- [ ] 음성 → 텍스트 변환 백엔드 연동 (CLOVA Speech + GPT 요약)
- [ ] **webm 오디오 STT** — 브라우저 녹음 webm 파일 서버 변환 또는 전사 지원 필요
- [ ] **header 영역 축소 + 상단/하단 고정** — 콘텐츠 영역 확보 (운영 요청 2026-05-14)

### 📥 상담 접수
- [ ] **리스트 행 클릭 시 우측 상세 패널 노출** — 별도 "수정" 버튼 경유 없이 행 자체 클릭으로 즉시 상세 표시
- [ ] **상담중 상태 행 진입 차단** — 다른 사용자가 진행 중인 상담은 진입 불가 처리 (락 표시 또는 disable)

### 💬 챗봇 / 채팅
- [x] ~~**`/csm/chat` 페이지 진입 불가**~~ — **2026-05-22 해결** (chat 보안 강화 및 토큰 URL 작업 중 함께 정리됨)

### 🛏️ 병실현황판
- [ ] 퇴원예고 등록 후 현황판 자동 새로고침 (현재 수동 새로고침 필요)
- [ ] **간헐적 Alpine 버그 수정** — 상담통계와 동일한 `Cannot convert undefined or null to object`. 원인: `mapWard()` 반환 객체의 `get discharge()` / `get afternoon()` getter 함수가 Alpine reactive proxy 초기화 중 잘못된 `this` 컨텍스트로 호출될 가능성. 수정 방향: getter 제거 후 값 즉시 계산으로 대체

### 📊 상담 리스트
- [ ] 리스트 항목 설정 관리 UI — 보여줄/가릴 컬럼 선택, 좌측 고정 설정
- [ ] "상담중" 상태 오표시 수정 — 진입 후 퇴장 시에도 상담중 유지되는 문제, 30분 락 개념 삭제
- [ ] 새로고침 버튼 기능 연동
- [ ] 접수관리 버튼 연동

### 📢 공지사항
- [x] ~~core 공지 → 일반 기관 사용자 자동 노출~~ — **2026-05-18 완료** (`/notices` 페이지 + 팝업)
- [ ] 공지 작성·수정·삭제 — core 공지(`/core/notice`)에서만 가능, 일반 기관 자체 공지 시스템(inst_notice)는 deprecated 상태로 유지. 정책 확정 시 inst_notice 테이블/엔드포인트(`/notices/save`, `/notices/delete`) 제거 검토
- [ ] 공지 읽음 추적 서버 통합 — 현재 client `localStorage` 기반(`csm-read-notices-<userId>`). 브라우저별로 분리되고 다중 기기 동기화 안 됨. `core_notice_read` 테이블 + `/notice/read/{noticeId}` 엔드포인트 활용해 서버측으로 통합 검토

### 📈 상담 통계
- [ ] **간헐적 버그 수정** — Turbo Drive 이동 시 Alpine.js `Cannot convert undefined or null to object` 발생. 원인: `_charts: []`가 Alpine reactive state에 있어 ECharts 인스턴스를 push할 때 Alpine이 deep-proxy 시도. 수정 방향: `_charts`를 클로저 변수로 이동 (reactive state에서 제외)
- [ ] 기존 통계 페이지 로직 참고하여 데이터 연동
- [ ] 신규 디자인으로 업데이트

### 📄 서류관리 (TipTap 에디터)

#### 🐛 알려진 버그
- [ ] **TipTap 툴바 "mismatched transaction" 오류** — `chain().focus()`가 내부적으로 `requestAnimationFrame`을 사용해, `run()` 이후 RAF 콜백이 낡은 트랜잭션을 재적용해 충돌 발생. 모든 툴바 명령(`cmd`, `setTextColor`, `setHeading`, `insertTable`)에서 재현됨
  - **수정 방향**: `chain().focus()` 제거 → `editor.view.focus()` 동기 호출 후 체인 실행 (RAF 없음)
  - **현황**: 수정 커밋 완료 (`080f2b0` + 후속 fix), 브라우저 검증 필요
- [ ] **기본 프리셋 서약서 내용 포맷 비호환** — `_defaultPledgeTemplateContent` (서버에서 전달) 가 구형 캔버스 HTML(`doc-free-layout` 포함)이어서 TipTap 로드 시 plaintext 변환됨 (서식 소실)
  - **수정 방향**: 서버 기본값을 TipTap 호환 HTML로 교체하거나, 기본 프리셋을 JS 상수로 하드코딩

#### 🔧 개선 예정
- [ ] **표 컬럼 리사이즈 UI** — TipTap `resizable: true` 설정됐으나 실제 드래그 핸들 CSS(`prosemirror-tables` 패키지 CSS) 미적용. 별도 CSS 추가 필요
- [ ] **글자색 상태 동기화** — `_syncMarks()`에서 현재 커서 위치의 텍스트 색상을 툴바 색상 선택기에 반영하는 로직 미구현
- [ ] **admissionPledge.html 렌더링 호환** — FieldChip(`data-field-key`) → 실제 환자 데이터 주입 후 PDF 출력 흐름 검증
- [ ] **간병계약서·동의서 기본 콘텐츠** — 입원서약서 외 서류 종류의 기본 프리셋 내용 미존재 (빈 에디터로 시작)

### 🧭 공통 / 네비게이션
- [ ] **좌측 네비게이션 스크롤 CSS 수정**

### 💬 문자 관리
- [ ] 예약 내역 페이지 구현
- [ ] 발송 내역 페이지 구현
- [x] ~~상용구 관리 — 추가·수정·삭제 백엔드 연동~~ — **2026-05-18 완료** (새 디자인 모달 통합)
- [x] ~~서명관리 탭 추가~~ — **2026-05-18 완료**
- [ ] 옛 페이지 정리 — `/smsSetting`, `/cardsetting`이 사이드바에선 접근 불가하지만 직접 URL 입력 시 옛 디자인 노출. `/message`, `/message/signature` 로 301 redirect 또는 컨트롤러/템플릿 제거 검토

### 🌐 MediPlat 포털 CSS (LOW)
- [ ] `ph__search` height 38px → 44px 이상으로 보정 (모바일 터치 타겟)
- [ ] `ph__search input` font-size 13px → 16px (iOS Safari 줌 방지)
- [ ] `ph__icon-btn` `:active` 상태 추가 (현재 `:hover`만 있음)

---

## 📝 기타 메모

| 항목 | 내용 |
|------|------|
| SMS 전송 엔드포인트 | `POST /api/external/sendSMS` (Bizppurio) |
| SMS 이력 조회 | `POST /sms/log` — `{ to_phone: [...] }` |
| 역할 테이블 | `csm.role_{inst}`, `csm.role_permission_{inst}`, `csm.user_role_{inst}` |
| 권한 매핑 테이블 | `csm.permission_master` (code → menu_key), `csm.menu_master` (menu_key → sort_order) |
| 사용자 테이블 | `csm.user_info_{inst}` (`us_col_08`: 0=플랫폼관리자, 1=기관관리자, 2=일반사용자) |
| 상담 테이블 | `csm.counsel_data_{inst}` |
| 동적 카테고리 | `csm.category_{inst}`, `csm.category_field_{inst}` |
| CLOVA/GPT 연동 | 백엔드 준비됨, webm 제외 mp3/wav/m4a 지원 |
| 챗봇 상담 접수 | `csm.counsel_reservation_{inst}` (patient_name, patient_phone, call_summary, created_by, status) |
| 챗봇 채팅방 | `csm.chat_room_{inst}`, `csm.chat_message_{inst}`, `csm.faq_{inst}` |
| 챗봇 임베드 URL | `?t={token}` (16자리 URL-safe). 기관별 토큰은 `csm.chat_inst_token (token PK, inst UNIQUE)` 에 1:1 저장 — 부트스트랩 시 자동 발급, 영구 불변 |
| 챗봇 WS 토픽 | `/topic/chat/{token}/{roomId}`, `/topic/admin/rooms/{token}` — 양쪽 모두 토큰 기반 |
| 챗봇 권한 가드 | `ChatWsAuthInterceptor` (SUBSCRIBE), `ChatWebSocketController` (SEND) — 세션 inst와 토픽 inst 비교 + chat_room.kakao_id 소유권 확인 |
| MediPlat 사용자 | `mediplat.mp_user` (inst_code, username, display_name, dept, email, phone, role_code) — email/phone은 nullable, CSM `us_col_10/11` 호환 |
| MediPlat 로그인 이력 | `mediplat.mp_login_audit` (inst_code, username, login_at, logout_at, session_seconds) — `HttpSessionListener`가 logout 기록 |
| MediPlat 서비스별 권한 | `mediplat.mp_user_service_role (user_id, service_code, role_code)` — VIEWER/MEMBER. 행 없으면 platform role 기반 fallback (PLATFORM_ADMIN/INSTITUTION_ADMIN/USER → MEMBER, else → VIEWER) |
| Cancer-treatment SSO 페이로드 | 5필드 HMAC: `inst|userId|expires|target|role` (role=VIEWER\|MEMBER). 다른 서비스(CSM/RoomBoard/SeminarRoom)는 기존 4필드 유지 |
| 비밀번호 찾기 — OTP 발신번호 | `csm.phone_number_{inst}` 첫 행 사용 (없으면 발송 거부) |
| 비밀번호 찾기 엔드포인트 | `POST /findpwd/post` (channel=email\|sms), `POST /findpwd/verify-otp`, `POST /api/me/password` |
| 비밀번호 reset 도메인 환경변수 | `CSM_BASE_URL` — 빈 값이면 `X-Forwarded-Host` 폴백. prod default `https://csm.sosyge.net/csm` |
