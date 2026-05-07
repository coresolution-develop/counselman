# MediPlat 작업 현황

> 최종 업데이트: 2026-05-07

---

## ✅ 완료된 작업

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

### 챗봇 (`chat-page.html` + `ChatApiController.java`)
- [x] 카카오 OAuth2 로그인 연동
- [x] WebSocket (STOMP/SockJS) 실시간 채팅
- [x] FAQ 패널 — 카테고리 필터, 아코디언 표시
- [x] 관리자 채팅 수신 연동
- [x] **FAQ 우선 응답 흐름** — 첫 메시지 전송 시 키워드 검색 → 결과 표시 → [도움이 됐어요 / 상담사에게 연결] 선택
- [x] **챗봇 상담 접수 플로우** — 이름→연락처→내용→확인 순서, `counsel_reservation` 테이블 자동 저장 (created_by: 챗봇)
- [x] **iOS Chrome 키보드 UX** — `font-size: 16px` 확대 방지, `visualViewport` API로 카카오톡 스타일 높이 동적 조정
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
- [x] **템플릿 저장 유효성 검사 버그 수정** — `textContent` 체크 → `children.length` 체크로 변경 (입력칸·표 요소 저장 불가 문제)
- [x] **취소 버튼 404 버그 수정** — `returnUrl` 컨텍스트 패스 누락 → 컨트롤러에서 `documentsReturnUrl` 모델 주입으로 해결

### MediPlat — 기관 관리자
- [x] 기관 관리자 페이지 신규 (`Institution-admin.html`, `institution-admin-app.jsx`)
- [x] `PlatformStoreService.institutionExists()` / `setInstitutionUseYn()` 추가
- [x] 신규 기관 저장 시 COUNSELMAN 자동 활성화
- [x] `POST /admin/institutions/status` (활성/비활성), `POST /api/admin/institutions` (JSON API)

---

## 🔍 검증 필요 (브라우저 확인 미완료)

- [ ] **CSM 허용 버튼** (`/csm/access`) — toggle POST가 `mp_user_service` 행을 실제로 생성/수정하는지 확인 필요 (현재 FALH 데이터 없어 모두 비활성 상태로 표시됨)
- [ ] **서류관리 템플릿** (`/documents#template`) — 표 삽입 → 서명영역 삽입 → 저장 → 입원서약서 렌더링 브라우저 직접 검증
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

### 🛏️ 병실현황판
- [ ] 퇴원예고 등록 후 현황판 자동 새로고침 (현재 수동 새로고침 필요)

### 📊 상담 리스트
- [ ] 리스트 항목 설정 관리 UI — 보여줄/가릴 컬럼 선택, 좌측 고정 설정
- [ ] "상담중" 상태 오표시 수정 — 진입 후 퇴장 시에도 상담중 유지되는 문제, 30분 락 개념 삭제
- [ ] 새로고침 버튼 기능 연동
- [ ] 접수관리 버튼 연동

### 📢 공지사항
- [ ] 공지 작성·수정·삭제 기능 백엔드 연동

### 📈 상담 통계
- [ ] **간헐적 버그 수정** — 재현 조건 파악 및 원인 분석 필요
- [ ] 기존 통계 페이지 로직 참고하여 데이터 연동
- [ ] 신규 디자인으로 업데이트

### 📄 서류관리
- [ ] **템플릿 관리 UI/UX 개선** — 캔버스 편집 경험 전반 개선
- [ ] **자유배치 필드 데이터 바인딩** — 캔버스 요소에 필드 타입/키 저장 모델 추가 (현재 outerHTML로 좌표만 보존)

### 🧭 공통 / 네비게이션
- [ ] **좌측 네비게이션 스크롤 CSS 수정**

### 💬 문자 관리
- [ ] 예약 내역 페이지 구현
- [ ] 발송 내역 페이지 구현
- [ ] 상용구 관리 — 추가·수정·삭제 백엔드 연동 (현재 조회만 됨)

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
