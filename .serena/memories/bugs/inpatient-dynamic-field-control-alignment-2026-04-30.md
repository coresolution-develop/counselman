# 입원상담 동적 항목 text/select 정렬 문제

- 증상: 입원상담 페이지의 동적 체크박스 항목 중 `text` 또는 `select` 컨트롤이 섞인 항목이 있으면 같은 행의 체크박스들이 일정한 열 기준으로 정렬되지 않음.
- 원인: `design/inpatient-consultation.html`의 `.ic-dynamic-grid`가 `flex-wrap` 기반이라, 입력 컨트롤이 붙은 항목만 폭이 커지면 이후 항목들이 가변 폭으로 흘러 정렬 기준이 깨짐.
- 수정: `.ic-dynamic-grid`를 CSS grid로 변경하고, 컨트롤이 있는 `.ic-dynamic-field--with-control`은 2개 칸을 span하도록 조정. 모바일에서는 컨트롤 포함 항목을 한 줄 전체로 사용하게 함.
- 변경 파일: `src/main/resources/templates/design/inpatient-consultation.html`.
- 검증: `./gradlew test` 통과. 이 과정에서 기존 `RoomBoardServiceAdmissionReservationTest`는 현재 `updateAdmissionDetails`가 update count 0이면 예외를 던지는 흐름에 맞게 mock 반환값을 보정함.
- 유사 패턴: 실제 `PageController`가 반환하는 템플릿은 `design/inpatient-consultation`; 대문자 디자인 사본은 라우팅 대상이 아님.