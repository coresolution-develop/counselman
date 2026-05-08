# 입원상담 모바일 반응형 보강 (2026-05-04)

대상: `src/main/resources/templates/design/inpatient-consultation.html`, `src/main/resources/templates/design/consultation-list.html`

결정:
- 상담리스트 모바일 FAB의 `신규상담` 액션은 `.btn--primary`와 흰 배경 override가 충돌해 흰 글자/흰 배경이 되지 않도록 primary 액션에 brand background와 흰 글자를 명시한다.
- 입원상담 화면은 데스크톱 2단 구조를 유지하고 `768px` 이하에서 단일 컬럼으로 전환한다.
- `480px` 이하에서는 입력/선택/버튼/date picker 표시 입력을 44px 이상으로 보정하고, 폼 그리드/동적 필드/보호자/상담기록/하단 저장바를 1열 또는 수평 스크롤 가능한 구조로 전환한다.
- 우측 상담 기록 aside의 sticky는 모바일에서 해제한다.
- SMS 모달은 모바일에서 단일 컬럼, 전체 높이 내 스크롤 구조로 전환한다.

검증:
- `git diff --check -- src/main/resources/templates/design/consultation-list.html src/main/resources/templates/design/inpatient-consultation.html`
- `./gradlew test --tests com.coresolution.csm.controller.ChromeNavigationTemplateTest`
