# MediPlat 작업 현황

> 최종 업데이트: 2026-04-25

---

## ✅ 완료된 작업

### 공통
- [x] CSRF 메타 태그 적용 (전체 design 페이지)
- [x] Alpine.js x-teleport 기반 모달 구조 정립

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
- [x] 전화번호 자동 하이픈 포맷 (`formatPhone`) — 010-XXXX-XXXX, 02-XXXX-XXXX 등
- [x] 보호자 전화번호 옆 SMS 전송 버튼 추가
- [x] SMS 전송 모달 — Mediplat 6 신규 디자인 마이그레이션 (3-column 레이아웃)
- [x] SMS 발신번호 DB 연동 (`smsSenderOptions` ← `counsel_phone` 테이블)
- [x] SMS 상용구 DB 연동 (`smsPhrases` ← `sms_template` 테이블)
- [x] SMS 서명 DB 연동 (`smsSignatures` ← `card` 테이블)
- [x] SMS 전송 백엔드 연동 (`POST /api/external/sendSMS` — Bizppurio)
- [x] SMS/LMS 자동 판별 (90 byte 기준) + 바이트 카운터
- [x] 최근 전송 내역 조회 (`POST /sms/log`) — 모달 오픈 시 자동 로드
- [x] 예약발송 UI (날짜·시간 입력 → `sendtime` 파라미터 전송)
- [x] 상용구 저장 (`POST /sms/phrase/save`)
- [x] 동적 카테고리 체크박스/라디오 토글 연동
- [x] SMS 상용구 "서명란에 넣기" → "문자입력란에 넣기" 텍스트 변경
- [x] SMS 모달 "서명 및 인사말" 섹션명 → "서명"으로 변경
- [x] 상담기록 초기화 버튼 — 방법·결과·상담내용·비입원사유·입원예정일시·병실 초기화
- [x] SMS 수신번호 추가 시 최근 전송 내역 실시간 갱신 (`refreshSmsHistory` 분리)

### 상담 접수 (`consultation-intake.html`)
- [x] 상단 요약 카드 백엔드 연동 — 전체·대기·입원연계·취소 건수 실시간 반영

### 입원예약관리 (`admission-reservation.html`) — 신규
- [x] 페이지 신규 구현 (design 시스템 적용)
- [x] 네비게이션 연결 — `chrome.js` 상담 업무 섹션, active 상태 (`inpatient-res`)
- [x] 백엔드 연동 — 입원예약 목록 (`items`), 가용 병실 목록 (`rooms`)
- [x] 보호자/연락처 연동 — `counsel_data_{inst}_guardians` 테이블 JOIN + AES 복호화
- [x] 입원예정일 datepicker 연동 (`MPDatePicker`)
- [x] 저장·입원완료·예약취소 API 연동
- [x] 상담기록 버튼 href 수정 (`/design/counsel/inpatient?cs_idx=`)

### 병실현황판 (`ward-status.html`)
- [x] 실 데이터 연동 — `RoomBoardView` / `RoomBoardWardView` / `RoomBoardRoomView` 매핑
- [x] `/room-board` → `design/ward-status` 라우팅 변경 (`RoomBoardController`)
- [x] 사이드바·헤더 연결 (`chrome.js`, `layout.css`, `components.css`)
- [x] 최신현황(새로고침) 버튼, 관리화면 버튼 추가 (관리 권한 조건부 노출)
- [x] 선택 버튼 컬럼 제거 (입원상담 팝업 모드 전용으로 유지)
- [x] 재원환자 명단 병상 슬롯 색상 — 입원가능=녹색, 재원중=회색, 입원예약=앰버

---

## 🔄 진행 예정 작업

### 🔧 공통 / 헤더
- [ ] 헤더 — 사용자 정보 백엔드 연동 여부 확인 (로그인한 사용자 이름·역할 표시)
- [ ] 헤더 — 검색 기능 용도 확인 (전역 검색? 상담 검색?)
- [ ] 모바일 반응형 — 아래 페이지 기준으로 모바일 레이아웃 구현
  - 상담 리스트, 상담 접수, 입원상담, 병실현황판, 공지사항, 상담 통계

---

### 🏥 입원상담
- [ ] 입원서약서 페이지 연동 (버튼 클릭 → 서약서 페이지 열기/상태 저장)
- [ ] 병실현황판 연동 (버튼 클릭 → 현황판 팝업 모드로 열기)
- [ ] 첨부파일 업로드 (녹음 파일, 소견서·CT·MRI·처방전 등) — 다중 업로드 지원
- [ ] 음성 녹음 기능 실제 구현 (MediaRecorder API)
- [ ] 음성 → 텍스트 변환 백엔드 연동 (CLOVA Speech + GPT 요약)

---

### 🛏️ 병실현황판
- [ ] 퇴원예고 기능 구현 — 퇴원예정일 등록·수정·삭제, 리스트 표시
- [ ] 병실현황판 카드에 입원예약·퇴원예고 수 추가 표시
- [ ] 모바일 레이아웃 지원

---

### 📊 상담 리스트
- [ ] 검색 기능 백엔드 연동 (환자명·전화번호·담당자 등)
- [ ] 리스트 항목 설정 관리 UI — 보여줄/가릴 컬럼 선택, 좌측 고정 설정
- [ ] 환자 정보 삭제 백엔드 연동
- [ ] 일괄 문자 보내기 백엔드 연동
- [ ] 상담 기록 카드 필터 (전체 / 오늘 / 미완료)
- [ ] "상담중" 상태 오표시 수정 — 상담 진입 후 즉시 퇴장 시에도 상담중으로 남는 문제, 기본 30분 락 개념 삭제
- [ ] 새로고침 버튼 기능 연동
- [ ] 접수관리 버튼 연동

---

### 📢 공지사항
- [ ] 탭 구현 — 전체 / 고정 / 적용 / 임시저장
- [ ] 공지 작성·수정·삭제 기능 백엔드 연동

---

### 📈 상담 통계
- [ ] 기존 통계 페이지 로직 참고하여 데이터 연동
- [ ] 신규 디자인으로 업데이트

---

### 📓 상담 일지 관리
- [ ] 백엔드 연동 (목록 조회·상세·저장·삭제)

---

### 💬 문자 관리
- [ ] 예약 내역 페이지 구현
- [ ] 발송 내역 페이지 구현
- [ ] 상용구 관리 — 추가·수정·삭제 백엔드 연동 (현재 조회만 됨)

---

### 🔐 관리자 (사용자·역할 관리)
- [ ] 역할 기반 기능 제한 실제 적용 — 역할 설정대로 메뉴·버튼 노출/숨김 처리
- [ ] RBAC 권한 체크 미들웨어 프런트 연동

---

## 📝 기타 메모

| 항목 | 내용 |
|------|------|
| SMS 전송 엔드포인트 | `POST /api/external/sendSMS` (Bizppurio) |
| SMS 이력 조회 | `POST /sms/log` — `{ to_phone: [...] }` |
| 역할 테이블 | `csm.role_{inst}`, `csm.role_permission_{inst}`, `csm.user_role_{inst}` |
| 사용자 테이블 | `csm.user_info_{inst}` |
| 상담 테이블 | `csm.counsel_data_{inst}` |
| 동적 카테고리 | `csm.category_{inst}`, `csm.category_field_{inst}` |
| CLOVA/GPT 연동 | 백엔드 준비됨, 프런트 연결 필요 |
| 모바일 우선순위 | 리스트·접수·입원상담·병실현황판·공지·통계 |
