# 상담리스트 캘린더 및 병실현황판 모바일 보강 (2026-05-04)

대상:
- `src/main/resources/templates/design/consultation-list.html`
- `src/main/resources/templates/design/ward-status.html`

결정:
- 상담리스트 캘린더 이전/다음 버튼은 SVG 아이콘만 있으면 아이콘 로딩 실패 시 텍스트가 보이지 않으므로 실제 문자 `‹`, `›`를 추가하고 calendar nav의 SVG는 숨긴다.
- 캘린더는 PC/모바일 모두 7열이 화면 폭에 맞도록 `repeat(7, minmax(0, 1fr))`로 변경하고 날짜 칸 `min-width`를 제거한다.
- 모바일 캘린더는 toolbar를 2줄 구조로, 요일/날짜/상담 카운트 버튼을 작은 터치 영역으로 조정한다.
- 병실현황판은 768px 이하에서 테이블을 카드형 block layout으로 전환한다. 각 `td`에 `data-label`을 부여해 모바일 라벨을 표시한다.
- 병상 슬롯(`ws-beds`)은 모바일에서 row-flow 그리드로 전환해 가로 스크롤을 줄인다.

검증:
- `git diff --check -- src/main/resources/templates/design/consultation-list.html src/main/resources/templates/design/ward-status.html`
- `./gradlew test --tests com.coresolution.csm.controller.ChromeNavigationTemplateTest --tests com.coresolution.csm.controller.RoomBoardControllerTest`
