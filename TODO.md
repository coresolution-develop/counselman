# MediPlat 작업 현황

> 최종 업데이트: 2026-05-04

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

### 병실현황판 (`ward-status.html`)
- [x] 실 데이터 연동 (`RoomBoardView` / `RoomBoardWardView` / `RoomBoardRoomView`)
- [x] 사이드바·헤더 연결, 최신현황·관리화면 버튼 추가
- [x] `/room-board/manage` 경로 승격 (레거시 `/admin/room-board` redirect 유지)
- [x] 재원환자 병상 슬롯 색상 (입원가능/재원중/입원예약)

### 상담 리스트
- [x] 검색 기능 백엔드 연동
- [x] 환자 정보 삭제 백엔드 연동 (다중 삭제)
- [x] 일괄 문자 보내기 백엔드 연동
- [x] 상담 기록 카드 필터 (전체 / 오늘 / 미완료)
- [x] Turbo 이동 / 대기목록 새로고침 수정

### MediPlat — 기관 관리자
- [x] 기관 관리자 페이지 신규 (`Institution-admin.html`, `institution-admin-app.jsx`)
- [x] `PlatformStoreService.institutionExists()` / `setInstitutionUseYn()` 추가
- [x] 신규 기관 저장 시 COUNSELMAN 자동 활성화
- [x] `POST /admin/institutions/status` (활성/비활성), `POST /api/admin/institutions` (JSON API)

---

## 🔍 검증 필요 (브라우저 확인 미완료)

- [ ] **CSM 허용 버튼** (`/csm/access`) — toggle POST가 `mp_user_service` 행을 실제로 생성/수정하는지 확인 필요 (현재 FALH 데이터 없어 모두 비활성 상태로 표시됨)
- [ ] **서류관리 템플릿** (`/documents#template`) — 표 삽입 → 서명영역 삽입 → 저장 → 입원서약서 렌더링 브라우저 직접 검증

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
- [ ] 퇴원예고 기능 구현 — 퇴원예정일 등록·수정·삭제, 리스트 표시
- [ ] 병실현황판 카드에 입원예약·퇴원예고 수 추가 표시

### 📊 상담 리스트
- [ ] 리스트 항목 설정 관리 UI — 보여줄/가릴 컬럼 선택, 좌측 고정 설정
- [ ] "상담중" 상태 오표시 수정 — 진입 후 퇴장 시에도 상담중 유지되는 문제, 30분 락 개념 삭제
- [ ] 새로고침 버튼 기능 연동
- [ ] 접수관리 버튼 연동

### 📢 공지사항
- [ ] 탭 구현 — 전체 / 고정 / 적용 / 임시저장
- [ ] 공지 작성·수정·삭제 기능 백엔드 연동

### 📈 상담 통계
- [ ] 기존 통계 페이지 로직 참고하여 데이터 연동
- [ ] 신규 디자인으로 업데이트

### 📄 서류관리
- [ ] **자유배치 필드 데이터 바인딩** — 캔버스 요소에 필드 타입/키 저장 모델 추가 (현재 outerHTML로 좌표만 보존)

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
