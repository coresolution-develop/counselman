# 상담 리스트 Turbo 이동/대기목록 새로고침 수정

증상: `/counsel/list` 실제 뷰(`design/consultation-list.html`)에서 상담 접수 대기 새로고침/접수 관리 버튼이 동작하지 않고, 대기 카드 클릭/검색/상담 기록 더블클릭 이동 시 화면 깜빡임이 발생.

원인: 대기 버튼에 Alpine 액션/링크가 없었고, 리스트 내부 이동이 `window.location.href` 직접 할당으로 Turbo Drive를 우회했다. 검색도 일반 GET submit으로 처리되어 내부 이동 방식과 일관성이 없었다.

수정: `listPage()`에 `visit()`, `submitSearch()`, `refreshQueue()`를 추가해 Turbo가 있으면 `Turbo.visit()`를 사용하고 fallback만 `window.location.href`를 쓰도록 변경. 대기목록 refresh는 새 JSON endpoint `/counsel/list/reservations?requestType=json`을 호출해 `queue`만 갱신한다. `PageController`에는 `getCounselReservationQueueJson()`과 중복 제거용 `listCounselReservationQueueItems()` helper를 추가했다.

검증: `./gradlew compileJava` 성공. `./gradlew test`는 기존 `RoomBoardControllerTest`가 `RoomBoardController` 생성자에 필요한 `ObjectMapper` 인자를 넘기지 않아 `compileTestJava`에서 실패(이번 변경과 무관).