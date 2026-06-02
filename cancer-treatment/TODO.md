# 암센터 치료스케줄 관리 — TODO

> 최종 업데이트: 2026-06-02

## 완료 (Done)

### 기반 구조
- [x] Spring Boot + Thymeleaf 기반 독립 앱 구성 (dev 포트 **18083**, application default 8083)
- [x] HMAC-SHA256 SSO 인증 연동 (`/mediplat/sso/entry`) — 2026-05-26부터 role을 포함하는 5-필드 페이로드
- [x] 공통 레이아웃 시스템 (`fragments/layout.html` — topnav, sidebar)
- [x] 디자인 토큰 정의 (`--brand`, `--accent`, `--fg*`, `--bg*`, `--border*`)
- [x] SSE(Server-Sent Events) 실시간 일정 동기화

### 권한 관리 (VIEWER / MEMBER) — 2026-05-26
- [x] **SessionUser에 role 필드** + 5-필드 HMAC 서명 검증 (`SsoService.validateAndResolveTarget`)
- [x] **`requireMember(session)` 가드** — 18개 write 엔드포인트(POST/PUT/PATCH/DELETE) 적용, GET은 VIEWER 통과
- [x] **프론트 UI 가드** — `body[data-user-role]` + `role-guard.js` (등록·저장·삭제 버튼 숨김, 세션 블록 드래그 차단, 인라인 편집 alert)
- [x] **역할 기본 매핑** — mediplat의 PLATFORM_ADMIN / INSTITUTION_ADMIN / USER → MEMBER 자동, ROOM_BOARD_VIEWER 등 → VIEWER. mediplat admin의 라디오로 사용자별 오버라이드 가능
- [x] **회귀 테스트 6케이스** — `SsoServiceTests` (legacy 4필드 launch, 5필드 launch, role 위변조, role drop, 만료, canonical 정규화)

### 스케줄 관리 페이지 (`/cancer-treatment-schedule`)
- [x] 오늘의 스케줄 시간표 뷰 (일별 시간 그리드)
- [x] 스케줄 등록 / 수정 / 삭제 모달
- [x] 커스텀 날짜 피커 (달력 드롭다운, 오늘 버튼)
- [x] 커스텀 시간 피커 (시·분 스크롤 컬럼, 스무스 스크롤)
- [x] 상태 인라인 편집 (예약 → 치료완료 → 예약취소 클릭 전환)
- [x] 필터 (날짜, 환자명/병동, 치료명, 상태)
- [x] 일/주/월 뷰 토글
  - 일(Day): 시간 그리드
  - 주(Week): 주간 스트립 + 날짜별 건수 배지
  - 월(Month): 전체 월 달력 + 날짜별 건수 배지
- [x] 우측 미니 캘린더 (날짜 선택 → 스케줄 즉시 로드, 이벤트 도트 표시)
- [x] 사이드바 배지 (오늘 일정 건수, 진행 중 건수) — 모든 페이지 공통 적용

### 치료 캘린더 페이지 (`/treatment-calendar`)
- [x] 월간/주간 뷰 토글
- [x] 5열 달력 그리드 (월–금)
- [x] 날짜 클릭 → 우측 패널 일정 목록 표시
- [x] 치료명 필터 드롭다운
- [x] 스케줄 페이지 디자인 시스템과 통일

### 환자 관리 페이지 (`/patients`)
- [x] 환자 목록 조회 (키워드, 병동 필터)
- [x] 환자 등록 / 인라인 필드 수정

### 치료실 관리 페이지 (`/treatment-rooms`)
- [x] 치료실 목록 CRUD

### 설정 페이지 (`/settings`)
- [x] 카테고리별 설정 항목 CRUD (치료명, 상태값 등)

### 통계 대시보드 (`/dashboard`)
- [x] 날짜별 요약 카드 (전체/예약/완료/취소)
- [x] 사이드바 배지 실시간 반영

### 스케줄 DB 영속화 + 환자 FK 연동 — 2026-06-02
- [x] **`TreatmentScheduleRepository`(JDBC) 신설** — `ct_treatment_schedule` 사용, 전 쿼리 `inst_code` 스코프 (테넌트 격리)
- [x] **인메모리 시드 제거** — `CopyOnWriteArrayList` + 하드코딩 6건 제거 → 재시작 시 일정 소실 문제 해결
- [x] **`patientId` FK** — 모달에서 환자 선택 시 id 캡처/저장, 자유입력 시 링크 해제. 환자명·병동은 `ct_patient` live join(항상 최신), 자유입력/삭제 환자는 `patient_name_snapshot` fallback
- [x] **치료명/옵션 자유텍스트 스냅샷 컬럼** (`treatment_name_snapshot`/`treatment_option_snapshot`) — `treatment_type_id` FK 전환은 별도 작업
- [x] **prod 스키마 보장** — `CancerTreatmentSchemaService`가 MySQL CREATE/ALTER 자동 (SQL_INIT_MODE=never 대응)
- [x] **통합 테스트 7건** — 영속성·테넌트 격리·status 매핑·live-join·스냅샷 fallback·시간 정규화·삭제

### 운영 편의 — 2026-05-22
- [x] 스케줄 등록 모달 — 환자명 자동완성 (서버 검색 + 키보드 ↑↓Enter Esc, 차트번호·병실 표시)
- [x] 스케줄 등록 모달 — 치료종류 자동완성 (설정 API 연동, 자유 입력 허용)
- [x] 일별 스케줄 인쇄 레이아웃 (`인쇄` 버튼 연결, `@page A4`, 치료정보·메모 노출)
- [x] 스케줄 드래그&드롭 시간 변경 (시간 그리드 내 기존 슬롯 간 이동, `PATCH /api/treatment-schedules/{id}/time`)
- [x] 사용자 매뉴얼 (`docs/cancer-treatment-user-guide.md`)
- [x] JS API 핫픽스 — `API` 변수가 IIFE 안에 갇혀 `saveScheduleModal`/`deleteScheduleFromModal`에서 깨지던 ReferenceError

### Dev 인프라 — 2026-05-22 (운영자 systemd/nginx 작업, 코드 변경 없음)
- [x] mediplat systemd에 `CANCER_TREATMENT_BASE_URL=https://dev.sosyge.net/cancer-treatment` 추가 → SSO launch URL이 도메인 형태로 발급
- [x] cancer-treatment systemd에 `CANCER_TREATMENT_MEDIPLAT_PORTAL_URL=https://dev.sosyge.net/portal` 추가 → MediPlat 버튼/로그아웃 redirect 정상화
- [x] nginx `location /cancer-treatment/` → `proxy_pass http://127.0.0.1:18083/cancer-treatment/` 등록

---

## 🔍 검증 필요 (브라우저 확인 미완료)

- [x] ~~**VIEWER/MEMBER 권한 흐름** (2026-05-26)~~ — **2026-06-02 검증 통과**. mediplat admin React UI에 권한 라디오 누락이 실제 원인이었고, 추가 후 VIEWER 강등 → 등록 버튼 숨김 확인
- [ ] **자동완성/인쇄/드래그&드롭** (2026-05-22 작업) — 환자/치료종류 자동완성 키보드 네비게이션, 인쇄 미리보기 레이아웃, 드래그 시간 변경 후 SSE로 다른 세션에 반영되는지

---

## 진행 예정 (Planned)

### 기능 개선
- [ ] 치료 캘린더 — 치료명 필터 옵션을 설정 API에서 동적으로 로드
- [ ] 주간 뷰 — 날짜 범위 이동 시 이전/다음 주 네비게이션 버튼 추가
- [ ] 월간 뷰 — 날짜 셀 클릭 시 해당 날짜 스케줄 모달 또는 드로어 표시

### 인쇄 / 내보내기
- [ ] CSV 내보내기 (날짜 범위 지정)

### 통계 강화
- [ ] 대시보드 차트 구현 (치료종류별 건수, 주간 추이)
- [ ] 환자별 치료 이력 뷰

### 운영 편의
- [ ] 반복 스케줄 등록 (매주 특정 요일 자동 생성)
- [ ] 알림 기능 (치료 시작 전 N분 브라우저 알림)

### 권한 모델 확장 (필요 시)
- [ ] 추가 역할 분리 — 현재 VIEWER/MEMBER 2단계. 일반 설정 변경/사용자 관리까지 분리하려면 ADMIN 등급 추가
- [ ] mediplat admin에서 일괄 권한 부여/회수 (현재 1명씩 라디오)
- [ ] role 변경 즉시 반영 — 현재 세션에 박혀있어 재로그인 필요. 강제 만료 또는 짧은 캐시 도입

### 인프라 / 품질
- [ ] 단위 테스트 (Service 레이어) 커버리지 확보
- [ ] Flyway 마이그레이션 스크립트 정리
- [ ] Docker Compose 개발 환경 구성
- [ ] 다크 모드 지원
