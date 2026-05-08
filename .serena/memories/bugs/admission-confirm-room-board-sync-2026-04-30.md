# 입원예약 입원완료 후 병실현황판 미표시

- 증상: 입원예약관리에서 101호로 배정 후 `입원완료` 처리한 환자가 병실현황판 101호에 나타나지 않음.
- 원인: 기존 `/api/admission-reservation/confirm` 요청은 `csIdx`만 전달했고, `RoomBoardService.confirmAdmission`은 `counsel_data_{inst}.cs_col_19`를 `입원완료`로 바꾸기만 했다. 병실현황판은 최신 `room_board_patient_{inst}` 스냅샷을 재원자 목록으로 읽고, `입원예약` 상태만 예약 오버레이로 표시하므로 `입원완료` 환자는 예약에서도 사라지고 재원자에도 추가되지 않았다.
- 수정: confirm 요청에 현재 화면의 `plannedDate`, `roomName`을 포함하고, 서버 트랜잭션에서 배정 정보를 저장한 뒤 최신 병실현황판 스냅샷에 환자 행을 추가한다. 최신 스냅샷이 없으면 `ADMISSION_RESERVATION` 스냅샷을 생성한다. 병실현황판 조회 시에는 최신 스냅샷 이후 `입원완료` 처리된 배정 환자도 합쳐서 보여 기존에 이미 처리된 건도 화면에 반영되게 했다. 병실 미선택 시 `IllegalArgumentException`으로 사용자 메시지를 반환한다.
- 변경 파일: `AdmissionReservationApiController.java`, `RoomBoardService.java`, `admissionReservation.html`, `design/admission-reservation.html`, `RoomBoardServiceAdmissionReservationTest.java`, 기존 `RoomBoardControllerTest.java`의 현재 템플릿 기대값 보정.
- 검증: `./gradlew test` 통과.
- 유사 패턴: `/api/admission-reservation/confirm` 호출부는 `csm/counsel/admissionReservation.html`, `design/admission-reservation.html` 두 곳이며 둘 다 `plannedDate`, `roomName` 전달로 보정됨.