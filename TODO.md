# MediPlat 작업 현황

> 최종 업데이트: 2026-08-30

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

#### [P0-7] 서비스 다운 알림 부재 — **미착수**
- **문제**: 2026-08-13 mediplat 12.5시간 중단을 **사용자 신고로 발견**. 모니터링·알림 장치가 전혀 없음
- **증폭 요인**: `Restart=always`라 크래시 루프 중 상태가 `failed`가 아니라 `activating (auto-restart)`로 보여 눈에 띄지 않음
- **수정 방향**: `curl 18081` / `curl 18082` 주기 확인 후 실패 시 알림. systemd timer + 스크립트면 충분
- **작업량**: 2~3시간
- **출처**: 2026-08-13 mediplat 장애 (경위: [docs/prod-deploy-phase1b.md](docs/prod-deploy-phase1b.md) "장애 기록")

#### [P0-8] LMS 제목 줄바꿈 수정 — 배포 완료 / **실사용 검증 대기**
- **배포**: 2026-08-16 20:12 prod 반영(`d6b078c`). 기동·헬스체크 통과, 스키마 변경 없음
- **미완**: 실제 발송으로 확인하지 못함. **월요일 업무 발송 후 아래로 판정**

```sql
-- ERROR 가 0건이면 수정 성공, 재발하면 원인 재조사
SELECT inst_code, status, COUNT(*) AS cnt, MAX(created_at) AS latest
FROM csm.v_transmission_history_all
WHERE created_at >= '2026-08-16 20:12:00'
GROUP BY inst_code, status ORDER BY inst_code, status;
```

- **유실분 재발송 안내 필요** — 효사랑가족요양병원(FALH), 2026-08-14 **7건 미발송**.
  수신번호 `01092308193`(5회) · `01088290939` · `01027215687`.
  화면에는 "전송 완료"로 보였으나 실제로는 나가지 않았다(`ERROR`는 발송내역 기본 필터에서 제외)
- **경위·진단 쿼리**: [docs/sms-batch-ops.md](docs/sms-batch-ops.md) "벤더 결과코드" 절

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

> ✅ **P1-6 은 2026-08-28 완료 / 2026-08-30 prod 배포** — 상세는 아래 "완료된 작업" 참조.

#### [P1-7] prod 카카오 로그인 깨져 있음 — **2026-08-30 실측 확인 / 미착수**
- **증상**: prod 에 `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` 이 **미설정**이다
  (2026-08-30 `csm-next.env` 대조에서 둘 다 `0`)
- **왜 아무도 몰랐나**: `@ConfigurationProperties`(OAuth2ClientProperties) 바인딩이라
  **미해결 플레이스홀더를 문자열로 조용히 남기고 기동한다.** `@Value` 와 달리 던지지 않는다.
  `clientId` 에 `"${KAKAO_CLIENT_ID}"` 가 그대로 들어간 채 떠 있다
  ([application.properties:63](src/main/resources/application.properties#L63))
- **드러나는 시점**: 챗봇 사용자가 **카카오 로그인 버튼을 눌러야** 실패한다
- **선행 확인 필요**: prod 에서 카카오 로그인을 **실제로 쓰는 기관이 있는지**.
  안 쓰면 우선순위가 내려가고, 쓰면 즉시 조치 대상이다
- **조치**: 카카오 콘솔에서 값 확보 → `csm-next.env` 주입 → `systemctl restart csm-next`.
  redirect URI 가 콘솔에 등록돼 있는지도 같이 확인
- **최소 08-27 부터 이 상태다** (그날 dev 에서 처음 실측됨)

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
- [x] ~~링크 허브 prod 배포~~ — **2026-08-30 완료**. `/csm/links` 200 확인
- [ ] 링크 허브 분류/환경 정리 SQL 실행 — [scripts/hub-category-env-cleanup.sql](scripts/hub-category-env-cleanup.sql).
      **코드 배포로는 반영되지 않는다. dev·prod 양쪽 다 미실행.**
      분류명은 화면을 보고 적은 것이라 스크립트 안의 2단계 확인 쿼리로 대조한 뒤 실행할 것
- [ ] dev `catalina.out` 로테이션 없음 — 2026-08-28 실측 **718MB**. DEBUG 로 계속 쌓인다.
      언젠가 dev 디스크가 찬다. logrotate 또는 로그 레벨 조정 필요 (prod 도 같은 구조인지 확인)
- [ ] `deploy-nightly.sh` 서버본 ↔ 저장소본 드리프트 — 2026-08-30 실측 md5 불일치
      (서버 `75d06d14…` / 저장소 `89f1d13c…`, 줄 번호 3줄 밀림).
      경로는 `/etc/default/csm-next-deploy` 가 덮으므로 동작에 지장은 없으나,
      **절차서 §2-C-2 의 마커 처리 분석이 저장소본 기준**이라 서버 실제 동작과 어긋날 수 있다.
      두 파일을 대조해 한쪽으로 맞출 것

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

### 2026-08-30 prod 배포 (링크 허브 ~ CSM-2..7, 22커밋) — **완료**

08-28 에 push 직전까지 갔다가 prod SSH 미접속으로 멈춰 있던 것을 재개해 완료했다.
전체 기록은 [docs/prod-deploy-2026-08-28.md](docs/prod-deploy-2026-08-28.md).

| | |
|---|---|
| 배포 시각 | 2026-08-30 **22:53** (수동 적용, 02:30 타이머 미사용) |
| 범위 | `d6b078c → 95185e1` · 22커밋 / 70파일 |
| 스키마 | 신규 테이블 4개 + `sms_batch.total_cost` INT → **BIGINT** |
| 백업 | `/opt/csm-next/backup/csm-20260830-222312.sql` (107M) |
| 공지 | **안 함** (런북 확정) |

**사용자에게 보이는 변화**: `/csm/links` 링크 허브 UI 전면 교체(prod 최초),
`/core/smssetting` 읽기 전용화 + 열 밀림 수정(사내 운영자 한정).

#### 이번 배포에서 절차서가 틀렸던 것 2가지 (정정 완료)

1. **§0-0 필수 env 표가 csm 을 14개로 적고 있었다** — `application*.properties` 를 글롭해
   dev/local 프로필 전용 변수가 섞인 목록이었다. 정상 서버에서 `0` 이 **6개** 나와 배포가
   멈췄다. **prod 프로필 실제 필수는 9개.** 08-27 에 이미 "csm prod 필수 env 9개" 로
   확인해 놓고도 절차서에 반영하지 않은 것이 원인이다 → phase1b §0-0 정정
2. **§2-C 가 엉뚱한 env 파일을 보라고 했다** — 실제 유닛이 읽는 것은
   `/etc/default/csm-next-deploy` 다. `EnvironmentFile=-` 의 `-` 때문에 파일이 없어도
   조용히 개발서버 기본값으로 돈다. **`journalctl` 의 `no marker (<경로>)` 로그로 판정하는 것이
   가장 확실하다** → 런북에 반영

#### 부수적으로 확인된 것

- prod 카카오 로그인 미설정 → **P1-7 로 등록**
- `deploy-nightly.sh` 서버본/저장소본 md5 불일치 → **P3 로 등록**
- `pre-push` 훅이 prod push 를 막는다(로컬 전용). 의도된 안전장치이므로
  **의도적 배포일 때만** `--no-verify` 로 우회할 것

### 2026-08-28 [P1-6] `/core/smssetting` 열 밀림 + 단가 `null` 표기 — **완료 / 2026-08-30 prod 배포**

- **증상**: 기관명 아래에 상태값이 붙고, 단가가 "단가 수신" 열에 나오는 등 헤더와 행이 한 칸씩 어긋나 보였다
- **원인**: [admin.css:389](src/main/resources/static/css/csm/core/admin/admin.css#L389) 의
  `.grid-header4, .grid-row4` 가 **3열**(`1fr 500px 250px`)인데 CSM-2 가 "단가 수신" 열을 더해
  셀이 4개가 됐다. **헤더에만** 인라인 `style` 로 4열을 줘서 행만 넷째 셀이 다음 줄로 밀렸다
- **수정**: 열 정의를 `smssetting.css` **한 곳**으로 모으고 템플릿 인라인 `style` 제거.
  이 CSS 는 smssetting 화면에서만 로드되므로 다른 admin 화면에 영향 없다
  - 기관이 없을 때의 안내 행(`grid-row4--empty`)은 셀이 하나뿐이라 1열로 따로 둔다 —
    안 그러면 4열의 첫 180px 안에서 접힌다
- **`null / null / null` → `미설정`**: 필드별로 판정한다. `9.6 / 미설정 / 미설정` 처럼
  **있는 값은 그대로 보인다**. 빈칸으로 두지 않았다 — "0원" 과 "못 받았다" 가 구분되지 않는다
- 동작은 원래 정상이었다(3단계 폴백으로 계산돼 발송된다). 운영자 화면 표기만의 문제다

##### ⚠️ 셀 수만 세는 테스트로는 이 버그를 못 잡는다
깨진 상태에서도 **헤더·행 모두 셀은 4개**였다. 갈린 것은 **CSS 열 수와 셀 수**다.
그래서 [SmsSettingTemplateRenderTest](src/test/java/com/coresolution/csm/template/SmsSettingTemplateRenderTest.java)
가 렌더된 HTML 과 `smssetting.css` 를 **같이** 읽어 대조한다 (4건 추가, 총 13건).

가드 무력화: CSS 열 정의 제거 **1건**, 헤더 인라인 `style` 복원 **1건**,
`null` 처리 되돌림 **2건** 이 문다.

- **동일 패턴 확인**: `setting.html` · `categorysetting.html` 도 같은 클래스를 쓰지만
  셀이 3개라 admin.css 3열과 맞는다. **열이 늘어난 화면은 여기 하나뿐**이다
- 전체 게이트 통과: `./gradlew test -PexcludeIntegration` → **415건 / 실패 0**
- ⚠️ **아직 dev 에도 안 올라갔다.** push 하면 자동 배포된다

### 2026-08-28 CSM-2..7 **dev 배포 검증** (코드 변경 없음)

> 배포 자체는 08-27 23:20 에 이미 끝나 있었다 — `dev` push 는 GitHub Actions 가 자동 배포한다.
> 이 세션은 **그것이 실제로 반영·동작하는지 확인**한 것이다.

| 단계 | 결과 |
|---|---|
| war 반영·reload | ✅ 23:20:44 복사 → 23:20:53 explode → 23:21 컨텍스트 기동. 23:20 대 에러 0건 |
| 새 테이블 4개 | ✅ `platform_price_cache` · `sms_usage_outbox` · `sms_usage_heartbeat` · `inst_sync_outbox` |
| 필수 env | ✅ **새로 필요한 env 없음.** CSM-3/4/6 프로퍼티는 전부 기본값이 있다 |
| 연동 OFF 확인 | ✅ `CSM_PRICE_PLATFORM_BASE_URL` 미설정, `[price-poll]` 로그 0건 |
| outbox 적재 | ✅ 2기관(FALH·COHS) 발송 → `source=SEND`, `sent_at=NULL`, `attempts=0` |

#### `totalCostJeon` 문자열 계약 — **실물로 확인됐다**
- payload 실측: `"totalCostJeon": "900"` — **따옴표 있는 문자열**
- 숫자로 나갔으면 플랫폼이 전량 400 이고 **4xx 는 영구 실패**라 그 사용량은 영영 안 들어갔다.
  `c09bae0` 이 배포 직전에 잡은 것이 맞았다는 확인이다
- `priceVersion: null` 도 정상이다 — 연동 OFF 라 플랫폼 단가를 못 받고 발송했다는 정보다
- `unitCostJeon: 900` 은 **2단계 폴백**(`inst_data_cs`)에서 온 값이다. `priceVersion=null` 이 그 증거

#### 알아 둘 것 — 지금 비어 있는 것이 정상인 자리
- **`sms_usage_heartbeat` 는 비어 있다.** 연동이 꺼져 있으면 스케줄러가 하트비트를 쓰기 **전에**
  조기 리턴한다 ([SmsUsageSender.java:73](src/main/java/com/coresolution/csm/serivce/SmsUsageSender.java#L73)).
  나중에 이걸 보고 "스케줄러가 죽었나" 로 오해하지 말 것. 같은 이유로 **누락 복구 스캐너도 지금은 안 돈다**
- 포털에 "문자" 카드가 없는 것도 정상이다 — 별도 `sms/` 앱은 08-11 에 동결했고 dev DB 행도 지웠다
  ([sms-portal-restore-checklist.md](docs/sms-portal-restore-checklist.md) §3-A).
  **outbox 에 적재되는 경로는 csm 의 `POST /api/counsel/sms/batch` 하나뿐**이고,
  이를 호출하는 화면은 상담리스트(`/counsel/list`) · 입원상담(`/counsel/inpatient`) 둘이다

#### 남은 것
- [ ] **단가 연동 2차 (URL 주입) 미착수** — MediCast **dev** 의 `base-url` / API 키를 몰라 중단했다.
      켜기 전에 §9.3 `curl` 대조가 **필수**다. 켜는 순간 `inst_data_cs`(2단계 폴백)까지 덮어써서,
      잘못된 값이 들어오면 URL 을 다시 빼도 오염이 남는다.
      절차: [docs/prod-deploy-checklist.md](docs/prod-deploy-checklist.md) §9.2~9.4
- [ ] **prod 미배포 22커밋** — 08-16 링크 허브부터 CSM-2..7 까지 전부.
      **로컬 `prod` 브랜치 fast-forward merge 완료 / push 대기** (08-28, 사무실 밖이라 SSH 불가).
      복귀 후 `git push origin prod` 부터 재개.
      런북: [docs/prod-deploy-2026-08-28.md](docs/prod-deploy-2026-08-28.md)
      — 공지 없음 / URL 미주입 / 배포 1회 확정, 사전점검 진행 상황, 배포 후 확인·롤백 포인터
- ✅ P1-6 (`/core/smssetting` 열 밀림 + `null` 표기) — **완료(08-28).** prod 에는 처음부터 정상으로 나간다
- 상세: [docs/handoff-2026-08-28.md](docs/handoff-2026-08-28.md)

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
- 단가 파싱: BigDecimal → 전 단위 정수. 음수·비숫자·NULL은 폴백+WARN
  - **CSM-3에서 HALF_UP 근사를 제거했다.** 소수 3자리(전 미만)는 반올림하지 않고 거부한다 —
    근사하면 고객이 입력한 값과 실제 차감액이 갈리고, 그건 표시 버그가 아니라 요금 분쟁이다
  - 폴백 3단계: `platform_price_cache` → `inst_data_cs` → 프로퍼티
  - ⚠️ **배포 순서 제약**: `CSM_PRICE_PLATFORM_BASE_URL` 은 **맨 마지막에 주입한다.**
    csm 배포(URL 미설정) → 플랫폼 단가 시드 대조 → 그 다음 URL 주입.
    `PlatformPriceCache.store()` 가 `inst_data_cs` 를 **덮어쓰므로**, 플랫폼에 잘못된 단가가
    있는 상태로 켜면 1단계와 **2단계 폴백이 동시에 오염**된다. URL 을 다시 빼도 2단계에 남는다.
    절차: [docs/prod-deploy-checklist.md](docs/prod-deploy-checklist.md) §9 · 코드: `PlatformPricePoller` 클래스 주석
- OTP 발송 이력 기록(`billable='N'`, `cost=0`, 본문 마스킹). 기존 `pwd-otp-` refkey는 콜백 매핑 100% 실패였음
- 기관별 테이블 collation 통일 + 전 기관 집계 뷰 `v_transmission_history_all`
- 예약발송 UI 숨김 (90일 실사용 0건, sendtime 형식 불일치로 미동작)
- 커밋: `bdd77e0` `26eec18` `53d06a4` `eaf7a44` `db111ed` `cb5eb36` `7345595` `ee03b31`

#### CSM-3 `/rate` 화면 금액 전 단위 전환 — **완료(08-26)**
- `/rate` 두 곳(`ratePage`, 월별 사용량 조회)의 `double` 곱셈을 전 단위 정수로 교체.
  모델에는 `BigDecimal` 을 넣는다 (`JeonFormat.toWonDecimal`)
- **화면은 바뀌지 않는다.** 운영 단가(9.6/30/90) 33개 조합을 실제 템플릿으로 렌더해 대조 — 전부 동일
- 소수 단가에서는 실제로 갈린다: SMS 8.7원 × 12,345건 = **107,401.5원**.
  전 단위 → `107,402원`, double(`107,401.4999...`) → `107,401원`
- 검증: [RateTemplateRenderTest](src/test/java/com/coresolution/csm/template/RateTemplateRenderTest.java)
  — 모델 값이 아니라 **렌더된 HTML** 을 본다. double 로 되돌리면 2건이 실패하는 것으로 가드 확인

#### CSM-3 단가 파서가 플랫폼과 갈려 있던 것 — **수정 완료(08-26)**
- 플랫폼 벡터 P01~P20 을 csm 파서에 대조해 **3건 분기 발견**. 전부 csm 만 통과시키던 값이다
  | 입력 | 플랫폼 | csm (수정 전) | 영향 |
  |---|---|---|---|
  | `1e2` | `NOT_NUMERIC` 거부 | **10,000전(100원) 통과** | 같은 문자열이 두 시스템에서 다른 단가 |
  | `+9.6` | `NOT_NUMERIC` 거부 | **960전 통과** | 위와 같음 |
  | `21474836.48` | `TOO_LARGE` 거부 | **2,147,483,648전 통과** | 플랫폼 `Int` 컬럼 상한 초과 |
- 원인: `new BigDecimal(String)` 의 예외에만 의존했다. 자바는 지수 표기·양수 부호를 받아들인다
- 수정: `JeonFormat.parseWonToJeon` 에 플랫폼과 **같은 정규식**(`^-?(\d+(\.\d*)?|\.\d+)$`)과 상한 검사 추가
- **파서를 한 곳으로 모았다.** `SmsService.parseUnitPriceJeon` 이 같은 규칙을 따로 구현하고 있었다 —
  청구 경로라 더 중요했다. 이제 `JeonFormat` 에 위임한다
- 검증: [JeonFormatRoundTripTest](src/test/java/com/coresolution/csm/util/JeonFormatRoundTripTest.java)
  — 왕복(`parseWonToJeon(toWon(x)) == x`) 0~100,000전 전수 + 벡터 P01~P20 대조.
  **왕복만으로는 못 잡았다. 벡터 대조가 잡았다**

#### CSM-4 사용량 outbox — **완료(08-27)**
- **C안**: outbox INSERT 를 발송 경로에 붙이되 트랜잭션으로 묶지 않고, 누락은 스캐너가 복구
- ⚠️ **플랫폼 스펙의 전제가 csm 에 없었다.** §9.2 초안은 `sms_batch UPDATE` 와 outbox INSERT 를
  같은 트랜잭션으로 묶는 그림이었는데, **csm 에는 발송 트랜잭션이 없다** —
  비즈뿌리오 호출을 트랜잭션 밖에 두기로 한 의도적 결정이고, 집계 UPDATE 조차 best-effort 다.
  없는 트랜잭션을 만들면 운영 발송 경로의 동작이 바뀐다 → 플랫폼 CLAUDE.md §9.2 를 실제 구조로 고쳤다
- `sms_batch` = 진실 / `sms_usage_outbox` = 파생. 누락은 막는 대신 **복구 가능**하게 만든다
- 스캐너는 `source='SCAN'` 으로 기록 — **자주 잡히면 그 자체가 신호다** (발송 경로 적재가 실패 중)
- 스캔 지연 10분: `max-recipients(500) × send-delay(100ms) = 50초` + 건당 응답 1초 ≈ 9분.
  **가장 느린 배치보다 길어야** 진행 중인 배치를 누락으로 오인하지 않는다.
  `CSM_SMS_BATCH_MAX_RECIPIENTS` 를 올리면 같이 올린다
- 스케줄러(1분): 전송 + 누락 복구 + **하트비트**. 0건 처리·실패·영구실패를 각각 센다 —
  죽은 것과 보낼 게 없는 것이 로그에서 똑같이 조용하기 때문
- 4xx = 영구 실패(재시도 안 함, `failed_reason` 에 사유). 5xx·네트워크 = 백오프 1→2→4→…→60분
- payload 는 **적재 시점에 통째 저장**. 전송 시 다시 만들면 보내려던 것과 보낸 것이 갈린다

##### `sms_batch.price_version` 고스트 — CSM-4 에서 닫았다
- CSM-3 이 컬럼을 추가하고 주석에 "사용량 이벤트로 회신된다" 고 적었는데
  **채우는 코드가 없어 항상 NULL 이었다** (`SEAL_IMAGE_GRACE_DAYS` 와 같은 종류)
- 별도 티켓으로 빼지 않았다 — 고스트를 남긴 채 티켓을 늘리면 같은 일이 반복된다
- `SmsService.unitPrice()` 가 단가와 버전을 **한 자리에서** 돌려준다. 새 조회를 추가하지 않았다 —
  따로 조회하면 그 사이 폴링이 값을 바꿔 **과금 버전과 회신 버전이 갈린다**
- 2단계 폴백의 버전은 `price()` 의 **같은 SELECT** 에 컬럼 하나를 더한 것이다
- **버전 `null` 은 정상 값**이다 — 플랫폼 단가를 못 받고 발송했다는 정보다.
  `0`/`-1` 로 채우면 "못 받았다" 와 "0번 버전" 이 섞인다
- 기존 발송이 그대로인지 `SmsServicePriceTest` 로 고정 (금액 한 전도 안 달라진다)

##### 검증 — 설계 시 정한 7항목 + 가드 무력화
| 테스트 | 건수 |
|---|---|
| [SmsUsageOutboxIntegrationTest](src/test/java/com/coresolution/csm/integration/SmsUsageOutboxIntegrationTest.java) | 12 — 멱등·payload 고정·접수기준·billable·버전·스캐너·발송 보호 |
| [SmsUsageSenderIntegrationTest](src/test/java/com/coresolution/csm/integration/SmsUsageSenderIntegrationTest.java) | 13 — **실제 HTTP 서버**로 4xx/5xx 분기·백오프·하트비트 |
| [SmsUsageSenderBackoffTest](src/test/java/com/coresolution/csm/serivce/SmsUsageSenderBackoffTest.java) | 6 — 경계와 오버플로 |

가드 무력화 결과: 4xx→재시도 **4건**, payload 재생성 **1건**, 스캔 지연 0 **1건**,
enqueue 예외 전파 **1건** 이 문다.

##### 플랫폼 쪽 후속
- 플랫폼 `CLAUDE.md` §9.2 를 실제 구조로 재작성 (트랜잭션 → 진실/파생 + 스캐너)
- **"csm 의 건수는 접수 시점 값" 을 §9.2 에 명시** — 사후 대사의 근거가 된다
- `PLAT-3` 신규: 사용량 화면에 접수 기준 표시 (Phase 8 에서 판단)

#### CSM-7 `contextLoads` 실패 — **완료(08-27)**

##### 원인이 두 번 바뀌어 있었다
| 날짜 | 일 |
|---|---|
| 2026-05-08 | 체크리스트에 기록: "OAuth2 빈 없음" |
| 2026-05-20 | `ce39394` OAuth2 설정 공통 이동 — **그 원인은 해소됨** |
| 2026-06-17 | `4a46c6e` `@Tag("integration")` → **CI 게이트에서 제외** |
| 2026-08-11 | `b11efa2` 평문 시크릿 제거 → `SPRING_DATASOURCE_URL` 필수 env 화 — **새 원인** |
| 2026-08-27 | 실행해서 확인. **나부터 옛 문서를 인용해 OAuth2 라고 보고했다가 정정** |

- ⚠️ **게이트에서 뺀 2개월 사이 08-13 mediplat 12.5시간 중단이 났다** —
  `PlaceholderResolutionException` 크래시 루프였고, **컨텍스트 테스트가 돌았으면 잡혔을 종류**다
- 교훈은 플랫폼 `CLAUDE.md` §3.2 뒤에 기록 ("게이트에서 뺀 테스트는 실패 중이 아니라 상태 불명이 된다")

##### 빈 배선 확인 — **정상이었다**
CSM-2/3/4 로 추가한 빈 6개가 전부 배선되고 `@Autowired(required=false)` 두 자리도 채워진다.
**배포 불가 상태가 아니었다.** 다만 그때까지 아무도 확인한 적이 없었다.

##### C안: 테스트를 둘로 나눴다
| 테스트 | 어디서 도나 | 검증 | **검증 안 함** |
|---|---|---|---|
| [ContextWiringTest](src/test/java/com/coresolution/csm/ContextWiringTest.java) | **CI 게이트** (태그 없음) | 플레이스홀더·빈 배선·`@Scheduled`·스캔 범위 | **매퍼 SQL 문법**, 스키마 부트스트랩, JDBC 세션, 실제 연결 |
| [CsmApplicationTests](src/test/java/com/coresolution/csm/CsmApplicationTests.java) | 로컬 (컨테이너 필요) | 위 전부 + DB 연결·DDL·매퍼 SQL | — |

- **프로파일을 새로 만들지 않았다.** `application-test.properties` 를 두면 컨텍스트는 쉽게 뜨지만
  **운영이 읽는 `application-dev.properties` 를 안 읽게 되어** 이 테스트의 존재 이유가 사라진다
- DataSource 는 **연결하지 않는 스텁**을 넣는다. 없애면 `JdbcTemplate` 을 쓰는 빈이 여럿이라
  컨텍스트가 통째로 안 뜨고, 검증할 것이 남지 않는다
- **두 테스트가 같은 프로파일·같은 properties 를 읽는지 서로 확인한다** — 갈리면 한쪽만 통과한다
- 실행: `./scripts/run-context-test.sh` (필수 env 를 스크립트가 채운다)
- 가드 무력화: 필수 env 제거 **7건**, 없는 프로퍼티 참조 **7건**, `@Scheduled` 표현식 파손 **7건**,
  프로파일 불일치 **1건** 이 문다

##### 이번에 드러난 사실
- **dev 프로파일 필수 env 12개**가 코드로 고정됐다 (`ContextWiringTest` 의 `@TestPropertySource`).
  새 필수 env 가 생기면 이 테스트가 먼저 터진다. 배포 절차서 §3 대조표와 같이 봐야 한다
- `sms.bizppurio.account:` 처럼 **바깥에 기본값이 있어도 안쪽 `${BIZPPURIO_DEV_ACCOUNT}` 가 평가**된다
  — 08-16 재배포 때 잡았던 중첩 폴백과 같은 구조
- ⚠️ **JPA 의존성이 죽어 있다** — `spring-boot-starter-data-jpa` 는 있는데
  `@Entity`·`JpaRepository`·`EntityManager` 사용처가 **0건**이다. 제거하면 기동이 빨라지고
  자동설정 제외 목록도 줄지만, 이번 범위 밖이라 손대지 않았다

##### ⚠️ mediplat 은 그대로다 (확인만, 손대지 않음)
- **mediplat 에는 `@SpringBootTest` 가 하나도 없다.** 테스트 9개가 전부 단위 테스트다
- 즉 "컨텍스트가 뜨는가" 를 보는 테스트가 **있어 본 적이 없다**. csm 은 있었다가 빠진 것이고
- **08-13 장애는 mediplat 에서 났다.** 지금도 방어는 절차서 0-0 단계(사람이 하는 env 대조)뿐이다
- csm 만 고치면 같은 장애가 mediplat 에서 반복될 수 있다 → 티켓 등록 여부 확인 필요

#### 필수 env ↔ 배포 절차서 §3 대조 — **완료(08-27). 누락 2건 발견**

| | 결과 |
|---|---|
| csm prod 필수 env | 9개 |
| 절차서 §3 에 **없던 것** | ⚠️ **`KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`** |

##### ⚠️ 정정 — 내 앞선 주장이 부정확했다
"필수 env 12개가 코드로 고정됐다" 고 했는데 **`ContextWiringTest` 는 `@Value` 플레이스홀더만 잡는다.**

| 바인딩 | env 가 없으면 |
|---|---|
| `@Value("${X}")` | **던진다.** 기동 실패 |
| `@ConfigurationProperties` | **조용히 `"${X}"` 문자열을 넣는다.** 기동 성공 |

실측: `KAKAO_CLIENT_ID` 없이 컨텍스트가 떴고 `ClientRegistration.clientId` 가
문자열 `"${KAKAO_CLIENT_ID}"` 였다. `spring.mail.password` 도 같았다.

**즉 "기동됐다" 는 "설정이 다 들어왔다" 를 뜻하지 않는다.**
카카오 로그인은 배포 후 **사용자가 눌러 봐야** 깨진 것을 안다.

##### 조치
- `ContextWiringTest.해석되지_않은_플레이스홀더가_남지_않는다()` 신설 —
  해석된 값에 `${` 가 남으면 실패. `KAKAO_CLIENT_ID` 를 빼면 문다
- `application.properties` 의 **틀린 주석 정정** ("미설정 시 기동 실패" → 기동은 된다)
- 절차서 §3 에 두 env 추가 + **§3.1 "이 표가 완전한지 확인하는 방법"** 신설
  (테스트 + prod 프로파일용 grep 한 줄. 손으로 관리하면 또 빠진다)

#### CSM-6 기관 동기화 통지 — **완료(08-27)**

##### ⚠️ 설계 중 티켓 전제가 바뀌었다
티켓은 "통지를 붙인다" 였는데, 확인해 보니 **`use_yn` 변경은 애초에 반영되지 않고 있었다.**

`refreshFromPlatform()` 이 도는 시점이 둘뿐이었다:
- 기동 시(`@PostConstruct`)
- SSO 진입 시 — 그것도 `resolveInst()` 나 `loadUserInfo()` 가 **실패했을 때만**

아는 기관·아는 사용자로 들어오면 refresh 가 안 돈다. ⇒ **비활성화는 재시작 전까지 반영 안 됨.**
통지 코드만 붙였으면 **통지할 것이 없었다.**

##### 구현 (B안)
- `refreshFromPlatform()` 을 **10분 주기**로 실행 (`@Scheduled`).
  `initialDelay` 를 주기와 같게 둬 **기동 직후 한 번 더 도는 낭비를 없앴다**
- 변경 감지: `upsertCoreInstitution` 이 `InstChange` 를 돌려준다.
  **새 조회를 만들지 않았다** — 기존 `SELECT COUNT(*)` 를 값 조회로 바꿨을 뿐.
  따로 조회하면 실제 반영과 통지 내용이 갈릴 수 있다
- 통지 타입 셋: `CREATED` / `USE_YN_CHANGED` / `RENAMED`.
  둘이 같이 바뀌면 **`USE_YN_CHANGED` 를 먼저** — 통지 한 건에 운영상 더 중요한 사실을 싣는다
- `inst_sync_outbox` 신설. PK `(inst_code, change_type)` — **10분마다 도는 경로라
  같은 변경을 반복 적재하면 큐가 같은 통지로 찬다**
- 스케줄러는 `SmsUsageSender` 공유(백오프·4xx·하트비트가 자동으로 같아진다), **테이블만 분리**
- 기동 보호: `enqueueQuietly` 가 전부 삼킨다. `@PostConstruct` 경로라 던지면 **기동이 실패한다**

##### 뷰 재생성 — 확인 결과와 정정
- ⚠️ **제 설계 문장이 틀렸다.** "뷰 재생성은 매번 `DROP/CREATE`" 라고 적었는데
  실제로는 **`CREATE OR REPLACE VIEW`** 다. MySQL 8 에서 실측 확인 —
  **원자적 교체라 "뷰가 없는 순간" 은 생기지 않는다**
- 남는 위험은 **배타적 메타데이터 락 경합**뿐이다. 10분마다 돌면 상시가 된다
- 조치: **기관 목록이 바뀔 때만 다시 만든다.** 뷰 정의는 목록만의 함수라 같으면 SQL 도 같다.
  목록이 같아도 **뷰 존재는 확인**한다 — 수동 DROP 후 영영 복구 안 되는 것을 막는다
- **`refreshFromPlatform` 이 매번 전부 보강하는 성질은 그대로 유지**했다 (C안을 안 택한 이유)

##### MediPlat 변경 — **불필요**
`mp_institution` 은 **csm DB 안에 있고**(`MEDIPLAT_DATASOURCE_URL="${SPRING_DATASOURCE_URL}"`),
MediPlat 이 쓰고 csm 이 읽는 같은 테이블이다. **통지는 csm 안에서 끝난다.**

##### 검증
| 테스트 | 건수 |
|---|---|
| [InstChangeDetectionTest](src/test/java/com/coresolution/csm/serivce/InstChangeDetectionTest.java) | 9 — **변경 없으면 통지 없음**, 세 타입, 우선순위, 경계 |
| [InstSyncIntegrationTest](src/test/java/com/coresolution/csm/integration/InstSyncIntegrationTest.java) | 10 — 실제 MySQL. 반복 적재, `sent_at` 재개, 기동 보호 |

가드 무력화: 변경 판정 제거 **2건**, `sent_at` 초기화 제거 **1건**, 예외 전파 **1건** 이 문다.

##### 플랫폼 쪽 후속
- `CLAUDE.md` **§9.3** 신설 — 엔드포인트·payload·멱등,
  그리고 **`active:false` 시 데이터 흐름은 유지하고 표시만 바꾼다**는 판단과 근거
  (재활성화 구간 / 지연 이벤트)
- **`PLAT-4`** 신규 티켓. **엔드포인트가 없어도 csm 은 막히지 않는다** —
  404 를 영구 실패로 닫고, 생기면 다음 변경부터 나간다.
  다만 그 사이 변경은 안 들어오므로 **구현 후 기관 목록 수동 대조**가 필요하다

#### [CSM-9] 수신번호 표기 규칙이 두 시스템에서 갈린다 — **미착수 / 등록만**

> **나중에 터질 게 확실한 종류다.** 플랫폼 수신거부 구현 **전에는** 방향을 정해야 한다.

- **csm**: `r.replaceAll("[^0-9]", "")` + 길이 8~15 검사가 전부
  ([SmsBatchService.java:98](src/main/java/com/coresolution/csm/serivce/SmsBatchService.java#L98)).
  E.164 정규화 없음, `+82` 변환 없음, 선행 0 처리 없음
- **플랫폼**: E.164 정규화 + blind index(AES-256-GCM + HMAC-SHA256)
- ⇒ **같은 번호가 두 시스템에서 다른 값으로 저장된다.**
  `01012345678` 이 csm 에는 그대로, 플랫폼에는 `+821012345678` 의 해시로 들어간다
- **수신거부·통계·대사 어디서든 갈린다.** 특히 두 시스템이 **같은 비즈뿌리오 계정**을 쓰므로
  플랫폼이 수신거부를 구현하면 **csm 이 보낸 번호는 그 대조를 통과하지 못한다**

##### 해결 방향 두 갈래 — 어느 쪽이든 범위가 크다
| | 내용 | 부담 |
|---|---|---|
| A | **csm 을 플랫폼 규칙으로 맞춘다** | 기존 `transmission_history_*` 의 저장된 번호를 어떻게 할지 결정해야 한다. 마이그레이션 범위가 크다 |
| B | **플랫폼이 csm 표기도 받아들인다** | 플랫폼이 정규화 전 표기를 함께 보관해야 한다. blind index 의 전제("표기가 달라도 같은 값으로 모인다")가 흔들린다 |

- CSM-5 에서 `phone-vectors` 를 복사하지 않은 이유이기도 하다 — csm 에 대응 규칙이 없다
- **착수 시점은 별도로 정한다. 지금은 등록만 한다**

#### [MP-1] mediplat 컨텍스트 테스트 — **미착수 / 등록만**
- **mediplat 에는 `@SpringBootTest` 가 하나도 없다.** 테스트 9개 전부 단위 테스트다
- **2026-08-13 12.5시간 중단이 mediplat 에서 났다** —
  `PLATFORM_ADMIN_PASSWORD` 미주입 → `PlaceholderResolutionException` 크래시 루프.
  **컨텍스트 테스트가 CI 에서 돌았으면 잡혔을 종류다**
- 지금 방어는 절차서 0-0 단계(사람이 하는 env 대조)뿐이다. csm 만 고치면 반복된다
- **csm `ContextWiringTest` 와 같은 방식으로 만들 수 있다** —
  스텁 DataSource + 운영 프로파일 유지 + 미해결 플레이스홀더 검사.
  [ContextWiringTest](src/test/java/com/coresolution/csm/ContextWiringTest.java) 를 그대로 참고
- **착수 시점은 별도로 정한다. 지금은 등록만 한다**

#### [CSM-8] 죽은 JPA 의존성 제거 — **미착수 / 등록만**
- `spring-boot-starter-data-jpa` 가 있는데 `@Entity`·`JpaRepository`·`EntityManager` 사용처가 **0건**
- 제거하면 기동이 빨라지고 `ContextWiringTest` 의 자동설정 제외 목록도 줄어든다
  (지금 `HibernateJpaAutoConfiguration`·`JpaRepositoriesAutoConfiguration` 을 빼고 있다)
- **지금 건드릴 이유가 없다.** 의존성 제거는 회귀 범위가 넓고 급하지 않다

#### CSM-5 벡터 CI — **완료(08-27)**
- 사본: `src/test/resources/` 에 `pricing-vectors.json` · `inst-code-vectors.json` · `vectors.sha256`
- **`pricing` + `inst-code` 둘만 복사했다.** `phone`·`ad-rules`·`ledger`·`settlement` 는
  csm 에 대응 코드가 없다 — 사본만 두면 **"벡터 대조" 라는 이름의 CI 가 아무 규칙도 대조하지 않게 된다**
- 테스트: [PricingVectorsTest](src/test/java/com/coresolution/csm/vectors/PricingVectorsTest.java) ·
  [InstCodeVectorsTest](src/test/java/com/coresolution/csm/vectors/InstCodeVectorsTest.java)
  — **파일을 직접 읽어** 돌린다. 케이스가 늘면 자동으로 같이 돈다
- 손으로 옮겨 뒀던 P01~P20 · I01~I13 **전사본 제거**. 지우기 전 대조 결과 **20/20 · 13/13 일치**했지만
  **운이 좋았던 것이지 보장이 아니었다** — 갈렸는지 확인할 장치가 없었다
- CI: 벡터 잡을 **별도 스텝**으로 분리 (다른 실패에 묻히면 안 된다).
  `ContextWiringTest` 처럼 태그를 안 붙여 게이트에 자동 포함
- 야간 교차 검증 [vectors-cross-check.yml](.github/workflows/vectors-cross-check.yml) 신설.
  **KST 03:30** — 플랫폼(03:00)보다 30분 늦게 돌려 갱신 중인 파일을 읽는 것을 피한다.
  `PLATFORM_REPO_TOKEN` 없으면 **실패가 아니라 warning + 요약**으로 미실행을 알린다
- **파일은 같은데 해시만 다른 경우**도 잡는다 — 그러면 한쪽 CI 만 빨개져 원인을 엉뚱한 데서 찾게 된다

##### ⚠️ 경로가 어긋나 있었다 (착수 중 발견)
- 처음에 `src/test/resources/vectors/` 하위에 뒀는데, **플랫폼 워크플로는
  `counselman/src/test/resources/$file` 을 본다.** 그대로 뒀으면 야간 검증이
  "사본이 없다" 로 **매일 빨개졌을 것**이고, 그러면 곧 아무도 안 본다
- 경로를 맞추고 `플랫폼_워크플로가_찾는_경로에_있다()` 로 고정. 옮기면 문다

##### phone 벡터를 복사하지 않은 근거 (실측)
- csm 수신번호 처리는 `r.replaceAll("[^0-9]","")` + 길이 8~15 검사가 전부다.
  **E.164 정규화 없음, `+82` 변환 없음, blind index 없음, 수신거부 대조 없음**
- `maskPhoneNumber` 는 정규식 치환 하나(`010-1234-5678` → `010-****-5678`)로
  하이픈이 없으면 아무것도 안 한다 — 플랫폼 마스킹 규칙과 다른 물건이다
- ⚠️ **남는 위험**: 두 시스템이 같은 비즈뿌리오 계정을 쓰는데 번호 표기 규칙이 다르다.
  플랫폼이 수신거부를 구현하면 **csm 이 보낸 번호는 그 대조를 통과하지 못한다**.
  CSM-5 범위 밖 — 별도 판단 필요

#### 남은 CSM 티켓 순서 — **확정(08-27)**
1. **CSM-4** 사용량 outbox — 착수 전 설계 승인 필수.
   운영 중인 발송 경로를 건드리므로 영향 범위를 먼저 확인한다
2. **CSM-7** `contextLoads` 실패 해결 — **CSM-5 의 선행 조건**
3. **CSM-5** 벡터 CI

> CSM-5 의 원래 근거가 "전체 테스트가 상시 빨간 상태면 CI 게이트가 의미를 잃는다" 였다.
> 지금 `-PexcludeIntegration` 없이 돌리면 `CsmApplicationTests.contextLoads` 가 실패한다.
> **빨간 상태에 게이트를 얹으면 게이트가 아니라 소음이 된다.** CSM-7 이 먼저다.

#### CSM-2 단가 화면 읽기 전용화 — **완료(08-27)**
- **막는 대상은 사내 운영자다.** `/core/smssetting` 은 `isCoreInst` 전용이고,
  병원 계정(`/rate`)은 원래 단가를 수정할 수 없었다 — 병원 화면은 바뀌는 것이 없다
- 화면: 단가등록·단가수정 버튼과 모달 2개 제거. **단가 표시는 유지** (숨기면 지금 얼마로
  나가는지 csm 에서 못 본다). 편집 전용 JS(`core-smssetting.js`)는 고아가 되어 삭제
- 서버: `POST core/smssetting/priceInsert` → **410 Gone** + 안내 문구.
  매핑은 남긴다 — 404 면 예전 탭·북마크가 "경로 없음" 만 보고 이유를 알 수 없다
- **쓰기 경로를 통째로 제거**: `corePriceInsert` / `corePriceInsertAll` (매퍼·서비스).
  `corePriceInsertAll` 은 **`WHERE` 절이 없는 UPDATE** 였다 — 한 번 저장하면 전 병원 단가가
  바뀌고 이전 값을 남기지 않아 되돌릴 수 없었다. **이번에 없어지는 것이 맞다**
- 배너 + 기관별 "단가 수신" 열(적용 버전 · 경과 시간). 15분 넘으면 `⚠` 로 구분
- 검증
  | 테스트 | 무엇을 |
  |---|---|
  | [PriceInsertGoneTest](src/test/java/com/coresolution/csm/controller/PriceInsertGoneTest.java) | **실제 HTTP 요청**이 410 인가. `all` 일괄 변경도. 권한 없으면 403 이 앞선다 |
  | [SmsSettingTemplateRenderTest](src/test/java/com/coresolution/csm/template/SmsSettingTemplateRenderTest.java) | **렌더된 HTML** 에 편집 UI 자국이 없는가 |
  | [PriceSourcePresenterTest](src/test/java/com/coresolution/csm/web/PriceSourcePresenterTest.java) | 낡음 판정 경계·문구 |
- **화면과 서버를 둘 다 막았다.** 화면만 막으면 "막았다고 믿는데 안 막힌 상태" 가 된다
- 가드 무력화 확인: 410 → 200 되돌리면 3건, 배너 제거 1건, stale 표시 제거 2건이 문다
- 사내 운영자 안내: [docs/ops-price-management.md](docs/ops-price-management.md)

##### 임계값 15분 — 플랫폼 PLAT-1 과 같은 근거
- 처음 24시간을 제안했다가 바꿨다. 폴링 주기(5분)의 **288배**라 사실상 아무것도 안 잡는다
- 플랫폼 PLAT-1 의 `PRICE_POLL_STALE_MINUTES` 기본 15분 = 폴링 주기의 3배.
  **같은 값·같은 근거**를 쓴다 (`CSM_PRICE_STALE_MINUTES`). 두 화면이 같은 상황을 다르게 말하면 안 된다
- 재는 것은 조금 다르다: 플랫폼 = "csm 이 조회한 시각", csm = "단가를 수신한 시각".
  차이는 **폴링은 왔는데 값이 거부된 경우**뿐이고, 그때 플랫폼은 거부 회신으로 `불일치` 를 띄운다
- 방향이 안전하다: csm 시각은 플랫폼보다 **같거나 더 오래됐다**. 절대 더 최신일 수 없다 →
  "플랫폼은 끊김인데 csm 은 정상" 은 나올 수 없다

##### ⚠️ 릴리즈 노트 — 게시할 내용이 사실상 없다
- `/rate` 전 단위 전환: 병원 화면 **표시값 동일** (33개 조합 렌더 대조)
- 단가 화면 제거: **사내 운영자 대상** — 병원 계정에는 변화 없음
- → `core_update` 게시 여부는 배포 시점에 다시 판단한다. 사내 안내는 위 문서로 대체

#### CSM-3 / CSM-2 배포 묶음 — **B안 확정(08-26)**
- **배포 2회 / 공지 1회.** 1차는 CSM-3 코드만 `CSM_PRICE_PLATFORM_BASE_URL` **미설정**으로 →
  단가 동작이 배포 전과 동일하므로 공지할 것이 없다.
  2차에서 CSM-2 + URL 주입 → **그때 한 번 공지**
- ⛔ **CSM-2 화면 변경을 1차 배포본에 담지 않는다.** URL 로 끄는 것이 요점인데
  화면 변경은 URL 과 무관하게 바로 보인다. 담으면 "배포했지만 아무것도 안 변한다" 가 깨지고
  중간 상태("고쳐도 5분 뒤 되돌아감")가 운영자에게 노출된다. **재시작 1회 추가는 감수한다**
- 근거: 병원 담당자에게 큰 변화는 "요금 계산 개선"이 아니라 **"단가를 여기서 못 고치게 된다"** 이고,
  그건 CSM-2 가 들어가야 완성된다
- 절차: [docs/prod-deploy-checklist.md](docs/prod-deploy-checklist.md) §9.0

#### CSM-3 단가 폴백 3단계 — **실제 MySQL 로 검증 완료(08-26)**
- [PlatformPriceFallbackIntegrationTest](src/test/java/com/coresolution/csm/integration/PlatformPriceFallbackIntegrationTest.java)
  — 테스트 전용 컨테이너(3309). `@Tag("integration")` 이라 CI 는 `-PexcludeIntegration` 으로 뺀다
- 각 단계를 **서로 다른 값**으로 구분해 어느 쪽을 읽었는지 확정: 1단계 777전 / 2단계 1,234전 / 3단계 960전
- **재시작 생존이 핵심이다.** 단가 저장 → `inst_data_cs` 를 비움 → 모든 객체를 새로 만듦 →
  여전히 812전. 2단계를 비우지 않으면 캐시가 죽어도 티가 안 난다
- **가드 확인: 캐시를 메모리로 되돌리면 4건이 문다**
  | 테스트 | 메모리 캐시일 때 |
  |---|---|
  | `재시작을_넘겨_캐시_단가가_유지된다` | 812전 → **960전(3단계 폴백)** |
  | `재시작을_넘겨_적용_버전이_유지된다` | 버전 회신이 **empty** — 플랫폼이 적용 여부를 알 수 없다 |
  | `일단계_캐시가_있으면_캐시를_쓴다` | DB 행을 못 본다 |
  | `캐시_값이_DB_행으로_남는다` | 행 자체가 없다 |
- 미러링이 `inst_data_cs` 를 덮어쓰는 것도 테스트로 고정 — **배포 순서 제약의 근거**
- 검증 중 `PlatformPriceCache` 클래스 주석의 "조용히 떨어진다" 가 부정확한 것을 발견해 정정.
  `PLATFORM_CACHE_EMPTY` WARN 은 실제로 뜬다. 다만 "한 번도 못 받았다" 와 구분되지 않고
  시간당 1회로 억제된다 — **로그는 신호일 뿐 방어가 아니고, 그동안 돈은 계속 움직인다**

#### ⚠️ 미해결 — `/rate` 페이지 안에서 반올림 규칙이 두 갈래 (기존 동작)
- 대부분의 금액: 템플릿 `#numbers.formatDecimal` → **HALF_EVEN** (`DecimalFormat` 기본값)
- 월별 표의 **합계** 열: 컨트롤러가 미리 포맷 → **HALF_UP** (원래 `Math.round`)
- `268.5원` 이면 한 화면에 `268` 과 `269` 가 같이 보인다.
  현재 단가에서는 `.5` 가 안 나와 드러나지 않지만 **소수 단가가 들어오면 드러난다**
- 이번 수정 범위 밖이라 동작을 유지했다. `합계_열과_나머지_열의_반올림_규칙이_다르다()` 로 현 상태를 고정해 둠

#### ⚠️ 미해결 — 원 단위 반올림 vs 실제 청구액 (별도 티켓)
- 화면은 원 단위로 반올림해 보여주는데 차감은 전 단위로 한다. 누적되면 화면 합계와 청구액이 갈린다
- 범위: `/rate`, 월별 집계, 청구서, 플랫폼 관리자 화면 — **네 곳을 같은 규칙으로** 정해야 한다
- 어느 쪽으로 통일할지(전 단위 표시 vs 원 단위 청구)는 요금 정책 판단이라 별도 결정 필요

##### 근거 ① — 한 페이지 안에서 이미 규칙이 두 갈래다 (실측)
| 자리 | 경로 | 반올림 |
|---|---|---|
| `/rate` 대부분의 금액 | 템플릿 `#numbers.formatDecimal` | **HALF_EVEN** (`DecimalFormat` 기본값) |
| `/rate` 월별 표 **합계** 열 | 컨트롤러가 미리 포맷 (원래 `Math.round`) | **HALF_UP** |

`268.5원`(MMS 89.5원 × 3건)이면 **한 화면에 `268` 과 `269` 가 같이 보인다.**
`RateTemplateRenderTest.합계_열과_나머지_열의_반올림_규칙이_다르다()` 가 이 상태를 고정해 뒀다 —
티켓 처리 시 이 테스트가 같이 바뀌어야 한다.

현재 단가(9.6/30/90)에서는 `.5` 조합이 안 나와 드러나지 않는다. **소수 단가가 들어오면 드러난다.**
즉 이건 가정이 아니라 **재현 가능한 실제 사례**이고, 표기 규칙을 통일해야 하는 근거다.

##### 근거 ② — 반올림 방향을 아무도 명시한 적이 없다
`Math.round` 는 HALF_UP 이고 `DecimalFormat` 기본은 HALF_EVEN 이다. 둘 다 **언어 기본값을
그대로 쓴 결과**지 요금 정책으로 정한 것이 아니다. 통일할 때 **어느 쪽으로 할지 명시**해야 한다.


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
