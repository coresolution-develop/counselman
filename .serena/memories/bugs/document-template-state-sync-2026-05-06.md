# 서류관리 템플릿 저장/삭제 화면 동기화 버그 (2026-05-06)

## 증상
`/csm/documents#template`에서 템플릿 저장, 적용, 삭제, 기본값 초기화 후 저장된 템플릿 목록과 오른쪽 편집 캔버스가 어긋날 수 있었음. 특히 삭제한 템플릿이 편집기에 계속 남거나, 존재하지 않는 ID 수정이 성공처럼 보일 수 있었음.

## 원인
- 프론트엔드 `document-management.html`은 저장/적용/삭제 후 목록만 `_reloadTemplates()`로 갱신하고 현재 편집 중인 `editingId`, `editingName`, 캔버스 내용을 새 목록 기준으로 다시 맞추지 않았음.
- 백엔드 `CsmAuthService.savePledgeTemplate`은 기존 ID 업데이트 시 실제 `UPDATE` 영향 행 수를 확인하지 않고 항상 id를 반환했음.

## 수정
- `PageController` 템플릿 저장/삭제/적용/초기화 API가 최신 `templates` 목록을 응답에 포함하도록 변경.
- `document-management.html`에 `_applyTemplates`, `_syncEditorWithTemplate`, `_syncEditorWithActiveOrDefault`를 추가해 작업 후 목록과 편집 캔버스를 동기화.
- 삭제 중이던 템플릿을 삭제하면 활성 템플릿 또는 기본 서약서로 편집기를 리셋.
- `CsmAuthService.savePledgeTemplate`가 기존 ID 업데이트 실패 시 `0`을 반환하도록 변경.
- 회귀 테스트: `CsmAuthServiceTransactionTest`에 기존 템플릿 ID 업데이트 성공/실패 케이스 추가.

## 검증
- `./gradlew test --tests com.coresolution.csm.serivce.CsmAuthServiceTransactionTest` 통과
- `./gradlew compileJava processResources testClasses` 통과
- `document-management.html`의 `docPageState` 인라인 스크립트 문법 검사 통과
- `git diff --check` 통과