# 남은 작업 목록

## [ ] 입원상담 동적 카테고리 — 라벨 클릭 시 체크박스 토글

### 문제
`new.html`의 동적 카테고리 필드에서 체크박스 옆 **라벨 텍스트를 클릭해도 체크박스가 체크되지 않음**.

HTML 표준상 `<label for="inputId">` 와 `<input id="inputId">` 가 연결되어야 라벨 클릭이 체크박스를 토글한다.
현재 템플릿에는 `id` / `for` 속성이 없어 라벨과 입력 요소가 연결되지 않는다.

### 해당 파일
`src/main/resources/templates/csm/counsel/new.html`

### 수정 방향
동적 필드 루프 안에서 각 `<input>`에 고유 `id`를, `<label>`에 대응하는 `for`를 추가.

`fieldKey`와 루프 인덱스를 조합해 고유 ID를 만든다.

**예시 (checkbox_only)**
```html
<!-- 수정 전 -->
<input type="checkbox" th:name="|${fieldKey}_checkbox|" th:value="${c2.cc_col_02}"
       th:checked="${valueMap[fieldKey + '_checkbox'] != null}">
<label th:text="${c2.cc_col_02}">라벨</label>

<!-- 수정 후 -->
<input type="checkbox"
       th:id="|${fieldKey}_checkbox|"
       th:name="|${fieldKey}_checkbox|"
       th:value="${c2.cc_col_02}"
       th:checked="${valueMap[fieldKey + '_checkbox'] != null}">
<label th:for="|${fieldKey}_checkbox|" th:text="${c2.cc_col_02}">라벨</label>
```

같은 방식으로 `radio_only`, `select_only`, `text_only`, 복합 타입(`checkbox_text`, `checkbox_select` 등) 모두 적용.

### 주의사항
- `fieldKey`는 `field_{c1Id}_{c2Id}` 형태로 이미 전역 유일 → `id` 충복 없음
- radio 버튼은 같은 c1 그룹 내 여러 c2가 있으므로 `fieldKey` 자체를 id로 사용하면 됨
- select / text 는 라벨이 `<span>` 으로 되어 있는 케이스도 있으니 `<span>` → `<label for="...">` 으로 교체 필요
