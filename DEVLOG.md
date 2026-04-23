# CSM 개발 로그

> 최종 업데이트: 2026-04-23

---

## 📐 진행 중인 설계/기획 문서

- **[권한 관리 재설계 (v3)](docs/permission-redesign.md)** — 완전 자유 RBAC 모델. 설계 확정, 구현 미착수. Step 1 (permission_master 시드)부터 진행 예정.

---

## ✅ 완료된 작업

### 1. 입원예약관리 페이지 신규 추가
**커밋:** `8106f64`

- `AdmissionReservationApiController.java` 신규 생성
  - REST API: 입원예정일·병실 업데이트, 입원 확정, 예약 취소
  - `/api/admission-reservation/details` POST
  - `/api/admission-reservation/confirm` POST
  - `/api/admission-reservation/cancel` POST
- `admissionReservation.html` 신규 생성 — 입원예약 목록 + 병실 배정 UI
- `admissionReservation.css` 신규 생성
- `AdmissionReservationItem.java` VO 신규 생성
- `RoomBoardService`: `listAdmissionReservations`, `updateAdmissionDetails`, `confirmAdmission`, `cancelAdmissionReservation` 메서드 추가

---

### 2. API URL 404 버그 수정
**원인:** `server.servlet.context-path=/csm` 설정으로 JS에서 하드코딩한 URL이 context-path를 누락  
**수정:** Thymeleaf 인라인 방식 `/*[[@{/api/...}]]*/` 으로 URL 주입

---

### 3. orb-deco 데코레이션 입원예약 페이지 적용
- `admissionReservation.html` `.ar-wrap` → `.ar-wrap orb-deco` 클래스 추가

---

### 4. 상담 접수 대기 — 카드 UI 전환
**파일:** `list.html`, `list-modern.css`

- 기존 세로 리스트 → **가로 스크롤 카드** 형태로 전환
- 카드 내용: 우선순위 뱃지, 환자명, 연락처, 상담자(접수자), 접수시간
- 카드 전체 클릭 → **상담 작성** 이동
- "상세보기" 버튼 → **상담 접수 수정** 이동
- z-index 레이어 구조: 오버레이 링크(z:2) > 콘텐츠(z:1, pointer-events:none) > 액션 버튼(z:3)

---

### 5. 상담 접수 카드 — 상담자 이름 표시 수정
**파일:** `PageController.java` → `resolveReservationActor()`

- 기존: `us_col_02` (로그인 ID) 저장
- 수정: `us_col_12` (이름) 우선, 없으면 `us_col_02` (ID) fallback

---

### 6. 입원상담 연계 시 날짜·시간 분리 처리
**파일:** `PageController.java`, `new.html`

- 상담 접수의 `reserved_at` (`2026-04-22 13:44:00`) → 날짜/시간 분리
  - `cs_col_21` ← 날짜 부분 (`2026-04-22`)
  - `cs_col_21_time` ← 시간 부분 (`13:44`)
- 모델에 `prefillReservedTime` 별도 추가

---

### 7. 상담 접수 — 접수자 목록 표시 및 수정 가능
**파일:** `reservation.html`, `reservation.css`, `CsmAuthService.java`, `PageController.java`

- 예약 목록 테이블에 **접수자** 컬럼 추가 (`.creator-name` 스타일)
- 등록/수정 폼에 **접수한 사람** 입력 필드 추가 (`name="createdBy"`)
- UPDATE SQL에 `created_by = COALESCE(NULLIF(?, ''), created_by)` 적용 — 비워두면 기존값 유지

---

### 8. 예약 취소 시 병실 배정 초기화 버그 수정
**파일:** `RoomBoardService.java` → `cancelAdmissionReservation()`

| 항목 | 수정 전 | 수정 후 |
|------|---------|---------|
| `cs_col_19` (상태) | ✅ 변경 | ✅ 변경 |
| `cs_col_38` (배정병실) | ❌ 미처리 | ✅ `NULL` 초기화 |
| `cs_col_21` (입원예정일) | ❌ 미처리 | ✅ `NULL` 초기화 |

병실현황판 쿼리가 `cs_col_38 IS NOT NULL` 조건으로 조회하므로, 취소 시 반드시 초기화 필요.

---

### 9. 상담 진행중 시각적 표시 기능
**파일:** `CounselReservation.java`, `CsmAuthService.java`, `PageController.java`, `list.html`, `list-modern.css`

- `counsel_reservation_{inst}` 테이블에 `opened_at datetime` 컬럼 자동 추가 (`ensureTableColumn`)
- `/counsel/new?reservationId=...` 접속 시 `touchOpenedAt()` 호출 → `opened_at = NOW()` 기록
- `listCounselReservations` 조회 시 `opened_at` 포함, 60분 이내면 `beingWorkedOn = true`
- 카드 UI 변화:
  - 카드 테두리 → 주황색 (`is-working` 클래스)
  - 깜빡이는 점 + **"상담 진행중"** 텍스트 배너 표시

---

## 🔧 진행 예정 작업

### 1. 동적 카테고리 체크박스 — 라벨 클릭 토글 버그
**파일:** `src/main/resources/templates/csm/counsel/new.html`  
**우선순위:** 중

체크박스 옆 라벨 텍스트 클릭 시 체크박스가 체크되지 않음.  
`<label for="...">` 와 `<input id="...">` 연결이 누락된 상태.

```html
<!-- 수정 전 -->
<input type="checkbox" th:name="|${fieldKey}_checkbox|" ...>
<label th:text="${c2.cc_col_02}">라벨</label>

<!-- 수정 후 -->
<input type="checkbox"
       th:id="|${fieldKey}_checkbox|"
       th:name="|${fieldKey}_checkbox|" ...>
<label th:for="|${fieldKey}_checkbox|" th:text="${c2.cc_col_02}">라벨</label>
```

`radio_only`, `select_only`, `text_only`, `checkbox_text`, `checkbox_select` 등 모든 복합 타입 동일 적용 필요.

---

### 2. 상담 진행중 표시 — 자동 갱신
**우선순위:** 하

현재는 리스트 페이지 진입 시점에만 `beingWorkedOn` 계산.  
장시간 열어둔 경우 실시간 반영이 안 됨.

- 방안: `setInterval`로 30~60초마다 AJAX 갱신, 또는 Server-Sent Events

---

### 3. 상담 접수 대기 카드 — 빈 상태 개선
**우선순위:** 하

현재 빈 상태 메시지 `"현재 대기 중인 상담 접수가 없습니다."` 가 단순 텍스트.  
아이콘 또는 일러스트와 함께 시각적으로 개선.

---

### 4. 모바일 대응 — 상담 접수 카드
**우선순위:** 중

720px 이하에서 가로 스크롤 → 세로 정렬로 전환 중이나,  
카드 높이·간격 등 세부 레이아웃 검수 필요.

---

### 5. 입원예약관리 — 병실 선택 UX 개선
**우선순위:** 중

현재 병실 드롭다운은 단순 `<select>`.  
병동 그룹핑, 잔여 병상 수 표시 등 개선 여지 있음.

---

### 6. 상담 접수 — 삭제 기능
**우선순위:** 중

현재 접수 건 삭제(완전 제거) 기능 없음.  
취소 상태로만 관리 중 → 완전 삭제 또는 보관 처리 기능 필요.

---

### 7. 상담 진행중 표시 — 중단 시 해제 로직
**파일:** `CsmAuthService.java`, `PageController.java`
**우선순위:** 중

현재 `/counsel/new?reservationId=...` 진입 시 `opened_at = NOW()` 기록 → 60분 내 "상담 진행중" 표시. 상담을 중단(취소/뒤로가기/다른 페이지 이동)해도 표시가 풀리지 않음.

- 방안 1: 상태 변경(CANCELLED/COMPLETED) 시 `opened_at = NULL` 초기화
- 방안 2: 페이지 이탈 감지 (`beforeunload` + sendBeacon → `/counsel/release`)
- 방안 3: "중단" 명시적 버튼 추가

---

### 8. 권한 관리 재설계 구현
**설계안:** [docs/permission-redesign.md](docs/permission-redesign.md)
**우선순위:** 상

3-Tier RBAC (Mediplat 서비스 계약 → CSM 기능 설정 → 역할/사용자) 모델로 전환.
Step 1 (permission_master 시드)부터 순차 진행.

---

## 📁 주요 파일 구조 참고

```
src/main/
├── java/com/coresolution/csm/
│   ├── controller/
│   │   ├── PageController.java               # 주요 페이지 컨트롤러
│   │   ├── AdmissionReservationApiController.java  # 입원예약 REST API (신규)
│   │   └── RoomBoardController.java          # 병실현황판 컨트롤러
│   ├── serivce/
│   │   ├── CsmAuthService.java               # 상담 접수 CRUD
│   │   └── RoomBoardService.java             # 병실현황판 / 입원예약 서비스
│   └── vo/
│       ├── CounselReservation.java           # 상담 접수 VO
│       └── AdmissionReservationItem.java     # 입원예약 항목 VO (신규)
└── resources/
    ├── templates/csm/counsel/
    │   ├── list.html                         # 상담 리스트 (상담 접수 대기 카드 포함)
    │   ├── reservation.html                  # 상담 접수 관리
    │   ├── new.html                          # 상담 작성/수정
    │   ├── admissionReservation.html         # 입원예약관리 (신규)
    │   └── roomBoard.html                    # 병실현황판
    └── static/css/csm/counsel/
        ├── list-modern.css                   # 리스트 페이지 스타일 (카드 UI 포함)
        ├── reservation.css                   # 상담 접수 스타일
        └── admissionReservation.css          # 입원예약관리 스타일 (신규)
```

---

## 🗄️ DB 테이블 참고

| 테이블 | 주요 컬럼 | 용도 |
|--------|-----------|------|
| `counsel_reservation_{inst}` | `id`, `patient_name`, `patient_phone`, `created_by`, `priority`, `status`, `opened_at` | 상담 접수 |
| `counsel_data_{inst}` | `cs_idx`, `cs_col_01`(환자명암호화), `cs_col_19`(상태), `cs_col_21`(입원예정일), `cs_col_38`(병실) | 상담 기록 |
| `room_board_room_master_{inst}` | `ward_name`, `room_name`, `licensed_beds` | 병실 기준 정보 |
| `room_board_snapshot_{inst}` | `snapshot_date`, `source_type` | 병실현황 스냅샷 |
| `userdata` | `us_col_02`(로그인ID), `us_col_08`(권한), `us_col_12`(이름) | 사용자 정보 |
