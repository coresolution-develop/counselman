# 죽은 코드 정리 후보

> 최초 작성: 2026-08-12 (Phase 1-A 조사 중 발견분)
> 관련: handoff-2026-08-12.md §6 "죽은 파일 2차 정리 (H-4)" — 템플릿 7 + JS 6 + CSS 2 + JSP 9 + 주석 49줄 (별도 목록 문서 없음, 발견 시 여기에 통합)

## 미삭제 후보 (Phase 1-A 발견)

| # | 위치 | 근거 | 비고 |
|---|---|---|---|
| 1 | `src/main/resources/templates/csm/sms/rate.html` | 이 뷰 이름(`csm/sms/rate`)을 반환하는 컨트롤러가 전 소스에 없음. 요금 화면은 `/rate`·`/message/rate` → `design/rate-management` 로 대체됨 | `sms_price` 를 읽는 레거시 템플릿. 단가 코드 추적 시 혼동 유발 |
| 2 | `design/inpatient-consultation.html:1928` — "이미지 첨부 (MMS)" 버튼 | 클릭 핸들러 없음. MMS 페이로드를 만드는 클라이언트 코드도 없음 | 죽은 UI. 사용자에게 동작하는 것처럼 노출됨 |
| 3 | `design/inpatient-consultation.html:2665` — `applyToSignature()` 함수 | 어떤 버튼에도 바인딩 안 됨 (1881·1882 두 버튼 모두 `applyToBody()` 호출) | 1882 버튼("문자입력란에 넣기")이 `applyToSignature()` 오배선일 가능성 — 삭제 전 의도 확인 필요 |

## 기존 확인·유지 중

| 위치 | 상태 |
|---|---|
| `PageController.sendSmsByLegacyContract` (`/sms/sendSMS`) | 410 Gone 차단됨. `[sms/sendSMS][gone]` 로그 2주 무발생 시 메서드째 제거 예정 (Task 0-E) |
