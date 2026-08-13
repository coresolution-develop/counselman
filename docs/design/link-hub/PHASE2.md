# 링크 허브 리디자인 — 2단계 남은 작업

1단계(UI 레이어)는 `feat/link-hub-redesign` 브랜치에 반영됨. 스키마 변경 없음.
디자인 스펙은 [README.md](README.md) 참고.

## 1단계에서 한 것

| 파일 | 내용 |
|------|------|
| `web/HubLinkPresenter.java` | 링크 → 화면용 뷰 매핑. host 추출, 환경 판정, 분류 색·축약 |
| `vo/HubLinkView.java` | 행 하나가 필요한 값 묶음 |
| `controller/CompanyLinkController.java` | `/links` 모델을 뷰 기반으로 교체, 분류 nav·환경별 개수 추가 |
| `controller/HubMeController.java` | 계정 화면에 개인 링크 수·메모 글자수 전달 |
| `static/assets/css/hub.css` | 디자인 토큰 전면 도입 + 다크 모드 + 행/팔레트/폼 컴포넌트 |
| `static/js/hub.js` | 카드 → 행 기준으로 전환, 커맨드 팔레트·테마·밀도·필터 추가 |
| `static/js/hub-strength.js` | 비밀번호 강도 표시 (회원가입 · 계정 설정 공용) |
| `templates/design/company-links.html` | 런처 레이아웃으로 재구성 |
| `templates/hub/fragments/shell.html` | 사이드바 232px, nav 3개, 분류 목록, 하단 사용자 |
| `templates/hub/{login,signup,account}.html` | 400px 중앙 폼 / 프로필 헤더 + 2열 카드 |

## 2단계 작업 목록

### A. 링크에 `env` 컬럼 추가 (우선순위 높음)

지금은 `HubLinkPresenter.envOf()`가 **이름에 DEMO 포함 / host가 `dev.`·`-dev.`·`.dev.`** 규칙으로 판정한다.
운영/개발 구분이 목적상 안전장치이므로 운영자가 직접 지정하는 편이 맞다.

- `CompanyLinkService.ensureTable()`에 `ALTER TABLE csm.company_link ADD COLUMN env varchar(10) ...` 추가
  (같은 파일의 DDL이 SSOT라 별도 마이그레이션 스크립트 불필요)
- `CompanyLink`·`CompanyLinkService` CRUD에 env 반영
- `HubLinkPresenter.envOf()`를 컬럼 조회로 교체. 컬럼이 비었을 때만 현재 규칙을 폴백으로 유지
- 관리 화면(탭 1)에 환경 선택 UI 추가

### B. 분류 메타 컬럼 (색상 · 축약)

지금은 `HubLinkPresenter.CATEGORIES` 맵에 분류명 → 색·축약이 하드코딩돼 있고,
표에 없는 분류는 이름 해시로 팔레트에서 고른다.

- `company_link_category`에 `color`, `color_dark`, `short_label` 컬럼 추가
- 관리 화면 탭 2(분류 순서)에서 색상을 8색 세트 중에서만 고르도록 UI 제공
- presenter는 DB 값 우선, 없으면 현재 기본값 폴백

### C. 기기 · 세션 목록

`HubRememberService`에 발급/회전/전체삭제/타기기삭제는 있으나 **목록 조회가 없다.**
계정 설정의 "기기·세션" 카드는 현재 "다른 기기 모두 로그아웃" 버튼만 노출한다.

- `HubRememberService.listForMember(memberId)` 추가 (기기명·최근 사용 시각·현재 세션 여부)
- 계정 화면에 세션 행 렌더 + 개별 로그아웃

### D. 링크 관리 화면 (탭 3개)

`design/company-links-admin.html`은 **1단계에서 손대지 않았다.** 인라인 스타일로 자립해 있고
hub.css 토큰의 예전 이름(`--card-bg` 등)을 별칭으로 이어둬서 색만 새 팔레트를 따른다.

- 탭 구조(링크 / 분류 순서 / 공지)로 재구성
- 링크 추가를 상시 노출 폼 → 슬라이드 패널·모달로
- 테이블 컬럼: 환경 · 뱃지 · 이름 · URL · 분류 · 순서+작업

### E. 회원가입 "소속 분류" 필드

디자인에는 이름 옆에 소속 분류 셀렉트가 있으나 `HubMember`에 해당 필드가 없어 **1단계에서 뺐다.**
필요하면 컬럼 추가 + 가입 폼·서비스에 반영.

## 알아둘 것

- **폰트**: 디자인은 Inter(Google Fonts) 기준이나 사내망 CDN 접근을 고려해 **CDN 링크를 넣지 않았다.**
  `--font-sans`가 Inter를 먼저 찾고 없으면 시스템 폰트로 떨어진다. 필요하면 로컬 호스팅 후
  `font-feature-settings:"cv01","ss03"` 유지.
- **행 마크업**: 디자인 문서는 행 전체를 `<a>`로 쓰라고 하지만 `<a>` 안에 `<button>`은 유효하지 않은
  HTML이라, 행을 `div`로 두고 전체를 덮는 `.lh-row__link`를 깔았다. 액션 버튼은 그 위에 올린다.
- **`links/` 모듈**: 루트 `src/`와 별개인 standalone 앱(포트 8085) 스냅샷이며 2026-06-23 이후 방치돼 있다.
  1단계에서 건드리지 않았다. 동기화할지는 별도 판단 필요.
- **테스트**: `HubTemplateRenderTest`가 실제 Thymeleaf 렌더로 표현식 오류를 잡는다. 템플릿 수정 시 여기부터 돌릴 것.
  `CsmApplicationTests.contextLoads()`는 `SPRING_DATASOURCE_URL` 환경변수가 필요해 로컬에서는 원래 실패한다(1단계와 무관).
