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

### A. 링크에 `env` 컬럼 추가 — ✅ 완료

`company_link.env varchar(10)` 추가 (`CompanyLinkService.ensureTable()`의 `ensureColumn`이 기동 시 처리).

- 값은 `prod` / `dev` / `demo`. 비어 있으면 예전처럼 이름·host로 자동 판정한다.
- 관리 화면 링크 추가·편집에 "환경" 셀렉트(자동 판정 / 운영 / 개발 / DEMO) 추가.
- `HubLinkView.envSource`가 저장된 원본값을 들고 있어, 셀렉트가 "자동 판정"과
  "명시적으로 운영"을 구분한다.

### B. 분류 메타 컬럼 (색상 · 축약) — ✅ 완료

`company_link_category`에 `color` / `color_dark` / `short_label` 추가.

- 관리 화면 탭 2에서 분류를 고르면 우측에 색상(정해진 10색 세트) · 축약 · 미리보기가 열린다.
- 저장 엔드포인트: `POST /admin/company-links/category-style`.
- 우선순위는 **운영자 지정값 → 핸드오프 기본 표 → 이름 해시 팔레트**. 색만 고르고
  축약을 비워두면 축약만 기본값으로 채워진다.
- 색상은 8색 세트에서만 고르게 하고, 서비스가 `#rrggbb` 형식을 한 번 더 검증한다
  (값이 그대로 `style` 속성에 들어가므로).

### C. 기기 · 세션 목록

`HubRememberService`에 발급/회전/전체삭제/타기기삭제는 있으나 **목록 조회가 없다.**
계정 설정의 "기기·세션" 카드는 현재 "다른 기기 모두 로그아웃" 버튼만 노출한다.

- `HubRememberService.listForMember(memberId)` 추가 (기기명·최근 사용 시각·현재 세션 여부)
- 계정 화면에 세션 행 렌더 + 개별 로그아웃

### D. 링크 관리 화면 (탭 3개) — ✅ 완료

탭(링크 / 분류 순서 / 공지)으로 재구성. 링크 추가는 우측 슬라이드 패널,
분류 순서는 드래그 정렬, 공지는 실시간 배너 미리보기.
동작은 `static/js/hub-admin.js`에 있다.

**공지 강조는 안내/주의 2택이다.** 디자인은 3택(안내·주의·점검)이나
`HubNoticeService`가 `info`/`warn`만 받는다. 3번째를 넣으려면 서비스·DB를 함께 손대야 한다.

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
