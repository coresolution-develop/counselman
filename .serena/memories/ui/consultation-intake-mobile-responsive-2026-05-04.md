# 상담접수 모바일 반응형 및 모바일 네비 닫힘 보강 (2026-05-04)

대상:
- `src/main/resources/static/assets/js/app.js`
- `src/main/resources/templates/design/consultation-intake.html`

결정:
- 모바일 좌측 네비는 Alpine store의 `sidebarCollapsed`가 Turbo 이동 후에도 남을 수 있어 `turbo:before-visit`, `turbo:load`, `pageshow`에서 `closeMobileSidebar`를 호출한다.
- 상담접수 화면은 기존 PC 2단 인라인 그리드를 유지하고 `.intake-layout` 클래스만 추가해 768px 이하에서 1열 전환한다.
- 모바일에서는 등록 폼을 `order:-1`로 목록보다 먼저 노출한다.
- 480px 이하에서 통계 카드는 2열 컴팩트, 입력/선택/textarea/버튼은 44px 이상, 필터는 가로 스크롤, 테이블은 760px min-width로 내부 스크롤 처리한다.

검증:
- `git diff --check -- src/main/resources/static/assets/js/app.js src/main/resources/templates/design/consultation-intake.html`
- `./gradlew test --tests com.coresolution.csm.controller.ChromeNavigationTemplateTest`
