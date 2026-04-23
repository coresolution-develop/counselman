# 권한 관리 재설계 — 설계안 (v3)

> 작성일: 2026-04-23
> 상태: **설계 확정 · 구현 미착수**
> 목적: CSM + Mediplat 공용 권한 시스템을 완전 자유 RBAC 모델로 재구성
> 다음 액션: Step 1 (permission_master 시드 데이터 작성)

---

## 1. 배경 — 현재 구조의 문제

조사로 확인된 기존 권한 구조의 주요 문제점:

1. **`us_col_08` 값 의미 혼재** — 도메인마다 해석이 다름
   - CSM: `1` = 기관관리자
   - Mediplat: `0` = PLATFORM_ADMIN, `1` = USER
   - RoomBoard: `1,2` = 관리, `3` = 뷰어
2. **하드코딩된 권한** — `username == "coreadmin"` ([RoomBoardController.java:311](../src/main/java/com/coresolution/csm/controller/RoomBoardController.java))
3. **이진 권한만 지원** — 정수 한 컬럼으론 메뉴·기능별 세밀한 권한 분리 불가
4. **Spring Security 미활용** — 모든 사용자에게 `ROLE_USER`만 부여, `@PreAuthorize` 미사용
5. **세션 기반만 사용** — DB 권한 변경 시 즉시 반영 불가
6. **메디플랫 이중 저장** — `user_data_core` + `user_data_cs` 동기 비용

### 현재 권한 체크 주요 지점
| 파일 | 라인 | 로직 |
|------|------|------|
| `PageController.java` | 369 | admin 페이지: `us_col_08 != 1` 체크 |
| `PageController.java` | 439, 476, 500 | 사용자 CRUD: 모두 `us_col_08 != 1` |
| `RoomBoardController.java` | 311-315 | `coreadmin` 하드코딩 + `grade == 1,2,3` |
| `InstAuthenticationProvider.java` | 57 | 전원 `ROLE_USER` 부여 |
| `CsmSchemaBootstrapService.java` | 212 | Mediplat 매핑: `"USER" ? 1 : 0` |

---

## 2. 확정된 설계 방향

### 2.1 결정사항 요약
| # | 항목 | 결정 |
|---|---|---|
| 1 | `us_col_08` 처리 | **A안 — 의미 통일** (신규 컬럼 대신 기존 정수 재정의) |
| 2 | 역할 구조 | **항목별(메뉴/기능별) 완전 자유 RBAC** — 고정 프리셋 없음 |
| 3 | Mediplat 호환 | 필수 — 기존 테이블/매핑 재사용 |
| 4 | 시드 역할 | 여러 개 제공 (`SYSTEM_INST_ADMIN` + 템플릿 5종) |
| 5 | 사용자↔역할 | **N:M** — 한 사용자가 복수 역할 겸임 |
| 6 | Mediplat role_code | `USER`로만 생성 — `ROOM_BOARD_VIEWER` 등 제거 |

---

## 3. 아키텍처 — 3-Tier 구조

```
┌─────────────────────────────────────────────┐
│ Tier 1: Platform (Mediplat 최고관리자)        │
│   - 기관별 "활성 서비스" 계약 관리            │
│   - 테이블: mp_institution_service            │
│   - 서비스 코드: COUNSELMAN / ROOM_BOARD /    │
│                  SEMINAR_ROOM                 │
└─────────────────────────────────────────────┘
             ↓ (구매/계약한 서비스 범위)
┌─────────────────────────────────────────────┐
│ Tier 2: Institution (CSM 기관관리자)          │
│   - 기관 내부 기능 on/off                      │
│   - 테이블: module_feature_setting            │
│   - 활성 메뉴 범위 내에서 역할 자유 생성       │
│   - 사용자 ↔ 역할 N:M 매핑                    │
└─────────────────────────────────────────────┘
             ↓ (역할 + 오버라이드)
┌─────────────────────────────────────────────┐
│ Tier 3: User                                │
│   최종 권한 = (역할₁ ∪ 역할₂ ∪ …) ± 오버라이드│
└─────────────────────────────────────────────┘
```

### 3.1 권한 풀 계산 로직

```java
Set<String> permissionPool(String inst) {
    // 1. Mediplat 계약 서비스 조회
    Set<String> contracted = listEnabledServiceCodes(inst);

    // 2. 서비스 → 메뉴 매핑 (코드 상수)
    Set<String> availableMenus = mapServicesToMenus(contracted);

    // 3. 기관 자체 기능 설정으로 필터
    availableMenus.removeIf(menu -> !moduleFeatureService.isEnabled(inst, menu));

    // 4. 메뉴 → 권한 코드 전개
    return expandToPermissions(availableMenus);
}
```

### 3.2 서비스 → 메뉴 매핑 (코드 상수)

| 서비스 코드 | 포함 메뉴 |
|---|---|
| `COUNSELMAN` | 상담접수 · 입원상담 · 상담리스트 · 상담통계 · 상담일지관리 · 문자관리 · 공지사항 |
| `ROOM_BOARD` | 병실현황판 · 입원예약관리 |
| `ADMIN` (상시) | 관리자 (모든 기관) |

### 3.3 사용자 권한 조립 알고리즘

```java
Set<String> resolveUserPermissions(long userId, String inst) {
    Set<String> pool = permissionPool(inst);               // 활성 가능 권한
    Set<String> perms = new HashSet<>();

    // 1. 사용자의 모든 역할 권한 합집합
    for (Role role : userRoles(userId, inst)) {
        perms.addAll(rolePermissions(inst, role.id));
    }

    // 2. 사용자별 오버라이드 적용
    for (UserPermOverride o : userOverrides(userId, inst)) {
        if (o.granted == 1) perms.add(o.code);
        else                perms.remove(o.code);
    }

    // 3. 활성 메뉴 풀로 교집합 (비활성 메뉴 권한 무력화)
    perms.retainAll(pool);

    return perms;  // → GrantedAuthority 로 변환
}
```

---

## 4. 메뉴 → 권한 코드 맵

CSM 좌측 메뉴 10종 기준:

| 좌측 메뉴 | menu_key | 권한 코드 |
|---|---|---|
| 상담 접수 | `counsel_reservation` | `COUNSEL_RESERVATION:READ / CREATE / EDIT / CANCEL / DELETE` |
| 입원상담 | `counsel_write` | `COUNSEL:READ / CREATE / EDIT / DELETE / EXPORT` |
| 상담리스트 | `counsel_list` | `COUNSEL_LIST:READ / EXPORT / BULK_SMS` |
| 공지사항 | `notice` | `NOTICE:READ / CREATE / EDIT / DELETE` |
| 상담통계 | `stats` | `STATS:READ / EXPORT` |
| 상담일지관리 | `counsel_log` | `COUNSEL_LOG:READ / EDIT / DELETE` |
| 문자관리 | `sms` | `SMS:SEND / BULK_SEND / HISTORY_READ / TEMPLATE_EDIT` |
| 병실현황판 | `room_board` | `ROOM_BOARD:READ / WRITE / SNAPSHOT_MANAGE` |
| 입원예약관리 | `admission` | `ADMISSION:READ / UPDATE_DETAILS / CONFIRM / CANCEL` |
| 관리자 | `admin` | `USER:READ / CREATE / EDIT / DELETE / RESET_PW` <br> `ROLE:READ / CREATE / EDIT / DELETE / ASSIGN` <br> `SETTINGS:READ / EDIT` · `CATEGORY:EDIT` |

**메뉴 자체 노출 여부** = `<RESOURCE>:READ` 보유 여부 (별도 `MENU:VIEW_*` 불필요)

---

## 5. DB 스키마

### 5.1 전역 테이블 (신규)

```sql
-- 권한 마스터 (코드 카탈로그)
CREATE TABLE csm.permission_master (
  code         varchar(64) PRIMARY KEY,       -- COUNSEL:CREATE
  menu_key     varchar(32) NOT NULL,          -- counsel_write
  resource     varchar(32) NOT NULL,
  action       varchar(32) NOT NULL,
  label_ko     varchar(100),
  sort_order   int DEFAULT 0
);

-- 메뉴 마스터
CREATE TABLE csm.menu_master (
  menu_key     varchar(32) PRIMARY KEY,
  label_ko     varchar(100),
  path         varchar(200),                  -- /counsel, /room-board, ...
  sort_order   int DEFAULT 0
);
```

### 5.2 기존 테이블 재사용 (신규 생성 불필요)

| 테이블 | 역할 | 위치 |
|---|---|---|
| `mp_institution_service` | Mediplat 서비스 계약 on/off | `PlatformStoreService.java:979` |
| `module_feature_setting` | 기관 기능 on/off | `ModuleFeatureService.java:77` |

### 5.3 기관별 테이블 (신규)

```sql
-- 기관별 역할 (기관관리자가 자유 생성)
CREATE TABLE csm.role_{inst} (
  role_id      bigint AUTO_INCREMENT PRIMARY KEY,
  role_code    varchar(64) UNIQUE,            -- SYSTEM_INST_ADMIN / COUNSELOR_L1 / ...
  role_name    varchar(100),
  description  varchar(255),
  is_system    tinyint(1) DEFAULT 0,          -- 1=편집/삭제 불가
  created_at   datetime,
  updated_at   datetime
);

-- 역할-권한 매핑
CREATE TABLE csm.role_permission_{inst} (
  role_id          bigint,
  permission_code  varchar(64),
  PRIMARY KEY (role_id, permission_code)
);

-- 사용자-역할 (N:M)
CREATE TABLE csm.user_role_{inst} (
  user_id      bigint,
  role_id      bigint,
  assigned_at  datetime,
  assigned_by  varchar(100),
  PRIMARY KEY (user_id, role_id)
);

-- 사용자별 오버라이드
CREATE TABLE csm.user_permission_{inst} (
  user_id          bigint,
  permission_code  varchar(64),
  granted          tinyint(1),                -- 1=추가부여, 0=명시적 거부
  PRIMARY KEY (user_id, permission_code)
);
```

### 5.4 기존 `us_col_08` 의미 재정의

| 값 | 의미 |
|---|---|
| `0` | `PLATFORM_ADMIN` — Mediplat 최고관리자 |
| `1` | `INST_ADMIN` — 기관관리자 |
| `2` | `INST_USER` — 일반 사용자 (권한은 역할 테이블로 결정) |

※ `us_col_09` 컬럼 **불필요** — N:M 역할 매핑으로 대체

---

## 6. 시드 역할 (기관 부트스트랩 시 자동 삽입)

| role_code | is_system | 용도 / 권한 |
|---|---|---|
| `SYSTEM_INST_ADMIN` | ✅ | 활성 서비스의 **전체 권한**. 편집/삭제 불가. Mediplat 기관관리자에 자동 부여 |
| `TEMPLATE_COUNSELOR` | ❌ | 상담사 기본 세트: COUNSEL* / COUNSEL_RESERVATION* / COUNSEL_LIST:READ / NOTICE:READ |
| `TEMPLATE_RECEPTION` | ❌ | 접수 전담: COUNSEL_RESERVATION:CREATE/EDIT/READ / COUNSEL_LIST:READ |
| `TEMPLATE_ROOM_BOARD_MANAGER` | ❌ | 병실관리자: ROOM_BOARD:* / ADMISSION:* |
| `TEMPLATE_VIEWER` | ❌ | 뷰어: ROOM_BOARD:READ / COUNSEL_LIST:READ |
| `TEMPLATE_SMS_OPERATOR` | ❌ | 문자담당: SMS:* / COUNSEL_LIST:READ |

- `is_system=1` → UI에서 잠금 (편집/삭제/이름변경 불가)
- `is_system=0` 템플릿 → 복사해 쓰거나 직접 편집·삭제 가능

---

## 7. Mediplat 연동

### 7.1 Mediplat role_code ↔ CSM 매핑

| Mediplat role_code | `us_col_08` | 자동 부여 역할 |
|---|---|---|
| `PLATFORM_ADMIN` | 0 | (전역 — 역할 부여 없음) |
| `INSTITUTION_ADMIN` | 1 | `SYSTEM_INST_ADMIN` |
| `USER` | 2 | *(없음 — 기관관리자가 할당)* |

※ 기존 `ROOM_BOARD_VIEWER` role_code는 **폐지** — 일반 `USER`로 생성 후 기관관리자가 `TEMPLATE_VIEWER` 역할 부여하는 방식

### 7.2 `MediplatRoleMapper` 전담 클래스로 분리

현재 [CsmSchemaBootstrapService.java:212](../src/main/java/com/coresolution/csm/service/CsmSchemaBootstrapService.java) 의 `authority = "USER" ? 1 : 0` 한 줄 매핑을 전담 클래스로 분리:

```java
@Component
public class MediplatRoleMapper {
    public UserAuthInit map(String roleCode) {
        return switch (roleCode) {
            case "PLATFORM_ADMIN"    -> new UserAuthInit(0, null);
            case "INSTITUTION_ADMIN" -> new UserAuthInit(1, "SYSTEM_INST_ADMIN");
            case "USER", null, ""    -> new UserAuthInit(2, null);
            default                  -> throw new IllegalArgumentException(roleCode);
        };
    }
}
```

---

## 8. 권한 체크 통합 지점

### 8.1 Spring Security 어노테이션

```java
@PreAuthorize("hasAuthority('USER:CREATE') and @instGuard.sameInst(#inst)")
public String createUser(@RequestParam String inst, ...) { ... }

@PreAuthorize("hasAuthority('ADMISSION:CONFIRM')")
@PostMapping("/api/admission-reservation/confirm")
public ... { ... }
```

### 8.2 Thymeleaf 메뉴 조건부 노출

```html
<a th:if="${#authorization.expression('hasAuthority(''ROOM_BOARD:READ'')')}"
   href="/room-board">병실현황판</a>
```

### 8.3 `coreadmin` 하드코딩 제거

[RoomBoardController.java:311](../src/main/java/com/coresolution/csm/controller/RoomBoardController.java) → `hasAuthority('ROOM_BOARD:SNAPSHOT_MANAGE')` 로 대체.

---

## 9. 권한 관리 UI

### 9.1 관리 UI 자체도 권한 대상

| 권한 코드 | 설명 |
|---|---|
| `ROLE:READ` | 역할 목록 조회 |
| `ROLE:CREATE` | 새 역할 생성 |
| `ROLE:EDIT` | 역할 권한 편집 |
| `ROLE:DELETE` | 역할 삭제 |
| `ROLE:ASSIGN` | 사용자에게 역할 할당/해제 |
| `USER:READ/CREATE/EDIT/DELETE/RESET_PW` | 사용자 관리 |

- 모두 `SYSTEM_INST_ADMIN` 기본 포함
- 기관관리자가 "부관리자" 역할 만들어 일부만 위임 가능

### 9.2 화면 구성

**화면 1: 역할 목록** (`/admin/roles`)
- 시스템 역할(자물쇠 아이콘) + 사용자 정의 역할
- 각 역할의 사용자 수 표시
- `+ 새 역할 만들기` / `복사해서 시작` 버튼

**화면 2: 역할 편집** (권한 매트릭스)
- 역할명 / 설명 입력
- 메뉴별 카드 섹션 × 액션 체크박스
- 비활성 메뉴는 회색 처리
- `is_system=1` 역할은 편집 불가

**화면 3: 사용자 상세 → 역할 탭**
- 할당된 역할 목록 (N:M)
- `+ 역할 추가` 버튼
- 개별 권한 오버라이드 섹션 (추가 부여 / 명시적 거부)

---

## 10. 구현 순서

| # | 작업 | 선행 | 난이도 |
|---|---|---|---|
| **1** | `permission_master` / `menu_master` 시드 데이터 작성 (10 메뉴 × 권한 전수) | - | 저 |
| **2** | 역할 관련 4개 테이블 스키마 (`ensureTableColumn` 패턴) | 1 | 저 |
| **3** | 시드 역할 6종 삽입 로직 (`SYSTEM_INST_ADMIN` + 템플릿 5종) | 2 | 저 |
| **4** | `PermissionResolver` 서비스 — 권한 풀 계산 + 사용자 권한 조립 | 2 | 중 |
| **5** | `MediplatRoleMapper` 분리 · 기관관리자 자동 `SYSTEM_INST_ADMIN` 할당 | 3 | 저 |
| **6** | `InstAuthenticationProvider` 에 GrantedAuthority 주입 | 4 | 중 |
| **7** | 역할 관리 UI (`/admin/roles` 목록·편집) | 6 | 중 |
| **8** | 사용자 상세에 역할 탭 + 개별 오버라이드 UI | 7 | 중 |
| **9** | `@PreAuthorize` 로 컨트롤러 체크 순차 교체 (파일별 점진) | 6 | 중 |
| **10** | `layout.html` 좌측 메뉴 `hasAuthority` 적용 | 6 | 저 |
| **11** | `coreadmin` 하드코딩 제거 | 6 | 저 |

**Step 1 부터 착수 예정.**

---

## 11. 미결 이슈 (구현 중 결정 필요)

1. **`permission_master` 시드 SQL 위치** — `application-*.yml` 의 `schema.sql` vs 자바 부트스트랩 서비스
2. **N:M 역할 충돌 시 정책** — 한 사용자가 `TEMPLATE_COUNSELOR` + `TEMPLATE_VIEWER` 를 동시에 가질 때 권한은 합집합(현 설계) 확정
3. **Mediplat 기관 서비스 변경 시 실시간 반영** — 세션 무효화 vs 다음 로그인 반영
4. **`is_system` 역할 다국어** — 나중에 라벨 `label_ko / label_en` 로 확장할지

---

## 12. 참고 파일

- 현재 권한 체크 지점: [PageController.java](../src/main/java/com/coresolution/csm/controller/PageController.java), [RoomBoardController.java](../src/main/java/com/coresolution/csm/controller/RoomBoardController.java), [InstAuthenticationProvider.java](../src/main/java/com/coresolution/csm/security/InstAuthenticationProvider.java)
- Mediplat 서비스: [PlatformStoreService.java](../mediplat/src/main/java/com/coresolution/mediplat/service/PlatformStoreService.java), [MediplatController.java](../mediplat/src/main/java/com/coresolution/mediplat/controller/MediplatController.java)
- 기능 플래그: [ModuleFeatureService.java](../src/main/java/com/coresolution/csm/serivce/ModuleFeatureService.java)
- 메뉴 렌더링: [layout.html](../src/main/resources/templates/csm/Include/layout.html)
- 부트스트랩: [CsmSchemaBootstrapService.java](../src/main/java/com/coresolution/csm/service/CsmSchemaBootstrapService.java)
