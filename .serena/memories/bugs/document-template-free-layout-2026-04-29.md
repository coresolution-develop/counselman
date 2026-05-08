# 서류관리 템플릿 자유 배치 전환 (2026-04-29)

## 증상
사용자가 원하는 것은 이미지처럼 표, 입력칸, 체크박스, 서명 영역을 자유롭게 배치/조정하는 문서 디자이너였으나, 이전 구현은 본문에 표/서명 블록을 삽입하는 수준이라 위치와 크기를 마음대로 조정할 수 없었음.

## 원인
템플릿 관리 UI가 contenteditable 본문 편집 구조였고 각 문서 요소가 독립 좌표/크기를 가진 객체로 저장되지 않았음.

## 수정
- `src/main/resources/templates/design/document-management.html`
  - 템플릿 편집기를 `doc-free-layout` 캔버스 기반으로 변경.
  - 텍스트, 입력칸, 체크박스, 서명영역, 주보호자 표, 부보호자 표, 동의사유 표 추가 버튼 제공.
  - 요소 드래그 이동 및 우하단 핸들 리사이즈 지원.
  - 저장 시 캔버스 outerHTML을 저장하여 left/top/width/height 좌표 보존.
- `src/main/resources/static/css/csm/counsel/admissionPledge.css`
  - 작성 화면에서 `doc-free-layout`, `doc-free-element`, `doc-free-table`, `doc-el-check`, `doc-el-sign`, `doc-free-input` 렌더링 스타일 추가.

## 검증
- `./gradlew compileJava` 성공
- `git diff --check` 성공
- document-management.html 내 inline JS를 `node --check`로 문법 검사 성공
- `./scripts/local-up.sh` 로컬 기동 확인 후 종료

## 참고
실제 서명은 기존 전체 필기 기능으로 자유 배치된 서명영역 위에 작성하는 방식. 개별 필드 데이터 바인딩까지 하려면 후속으로 필드 타입/키 저장 모델을 추가해야 함.