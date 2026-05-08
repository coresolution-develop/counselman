# 서류관리 템플릿 표/서명 영역 보강 (2026-04-29)

## 증상
서류관리 페이지의 입원서약서 템플릿 관리 기능이 실제 문서 양식 요구를 충족하지 못함. 특히 표와 서명 영역처럼 입원서약서에 필요한 구조 요소를 편집/저장/작성 화면에 안정적으로 반영하기 어려웠음.

## 원인
- 템플릿 편집기가 Quill 기본 툴바에 의존해 표 편집을 안정적으로 지원하지 못함.
- 작성 화면 저장 시 현재 렌더링된 템플릿 HTML 대신 별도 기본 텍스트 흐름을 사용해, 작성 문서와 템플릿 HTML이 분리될 수 있었음.
- 저장된 서약서가 존재해도 활성 템플릿이 우선 반영될 수 있어 작성 당시 문서 레이아웃 보존이 약함.

## 수정
- `src/main/resources/templates/design/document-management.html`: Quill 의존 편집기를 contenteditable 기반 HTML 편집기로 교체. 굵게/밑줄/번호목록/표 삽입/서명영역 삽입/문단 삽입 도구 추가.
- `src/main/resources/templates/csm/counsel/admissionPledge.html`: 템플릿 HTML을 서약서 본문으로 렌더링하고 hidden pledge text도 동일한 HTML을 담도록 변경.
- `src/main/resources/static/js/csm/counsel/admissionPledge.js`: 저장 payload의 `pledge_text`를 `.ap-terms-block.innerHTML`에서 가져와 작성 당시 템플릿 구조를 보존.
- `src/main/resources/static/css/csm/counsel/admissionPledge.css`: 템플릿 표(`.doc-template-table`)와 서명 영역(`.doc-sign-field`) 렌더링 스타일 추가.
- `src/main/java/com/coresolution/csm/controller/PageController.java`: 저장된 `pledge_text`가 있으면 활성 템플릿보다 우선 렌더링하도록 변경.

## 검증
- `./gradlew compileJava` 성공
- `git diff --check` 성공
- `./scripts/local-up.sh` 로컬 기동 확인 후 종료

## 남은 확인
브라우저에서 `/documents#template` 진입 후 표 삽입, 서명영역 삽입, 저장 및 적용, 새 입원서약서 작성 화면 렌더링을 직접 확인하면 됨.