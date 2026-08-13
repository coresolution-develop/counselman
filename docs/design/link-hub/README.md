# Handoff: 사내 링크 허브 UI 리디자인

> 출처: Claude Design 프로젝트 "허브 메인 라이트 모드 시안"
> `5fefbda1-4349-4e9a-a341-ac8005c5ad3f`
> 원본 시안(`링크 허브 리디자인.dc.html`)과 스크린샷 8장은 해당 프로젝트에 남아 있습니다.
> 이 문서는 그중 핸드오프 스펙(`design_handoff_link_hub/README.md`)을 그대로 옮긴 것입니다.

## 개요
사내 직원 전용 링크 허브(회사가 운영하는 수십 개 사이트를 한 곳에서 빠르게 찾아 여는 도구)의 UI 리디자인입니다.
목표는 "카드 갤러리"에서 "빠른 실행 도구(런처)"로 전환하는 것 — 검색을 1급 진입점으로 올리고, 링크를 컴팩트한 행으로 압축하고, 운영/개발 환경을 시각적으로 강하게 구분합니다.

포함 화면: 허브 메인(라이트·다크), 커맨드 팔레트, 링크 관리(탭 3개), 계정 설정, 로그인, 토큰·컴포넌트 스펙.

## 디자인 파일에 대해
이 번들의 `링크 허브 리디자인.dc.html`은 **HTML로 만든 디자인 레퍼런스(프로토타입)**입니다. 그대로 가져다 쓰는 프로덕션 코드가 아닙니다.
실제 작업은 이 디자인을 **대상 코드베이스의 기존 환경에서 재현**하는 것입니다.

이 프로젝트의 기술 제약(반드시 지킬 것):
- 백엔드 Spring Boot + **Thymeleaf 서버사이드 렌더링**. React·Vue·빌드 파이프라인 없음
- **순수 CSS + 바닐라 JS**만 사용. Tailwind·SCSS 전제 금지. 토큰은 CSS 변수로 정의
- 아이콘은 인라인 SVG (프로토타입의 SVG를 그대로 옮겨도 됨) 또는 CDN 아이콘 셋
- UI 텍스트 전부 한국어, 데스크톱 Chrome 우선 + 모바일 대응
- 기존 기능 유지: 즐겨찾기 정렬(▲▼★), 개인 링크, 내 메모, 최근 사용, 인기 링크, 이 기기 기억하기, 공지 배너

프로토타입 파일은 React 기반 스트리밍 컴포넌트 포맷(.dc.html)이며 스타일이 전부 인라인입니다. 구현 시에는 아래 토큰과 클래스 규칙으로 **CSS 파일로 정리해서** 옮기세요.

## 충실도
**High-fidelity.** 색상·타이포·간격·상태가 확정값입니다. 아래 수치를 그대로 사용하세요.
라이트 모드가 기본이며 다크 모드는 동일 구조에 토큰만 치환합니다.

---

## 디자인 토큰

CSS 변수로 정의하고, 다크는 `:root[data-theme="dark"]`(또는 `.theme-dark`)에서 재정의합니다.

### 면 · 라인
| 토큰 | 라이트 | 다크 | 용도 |
|---|---|---|---|
| `--bg` | `#ffffff` | `#08090a` | 페이지 캔버스 |
| `--bg-panel` | `#f7f8f8` | `#0f1011` | 사이드바, 테이블 헤더, 서브 패널 |
| `--bg-surface` | `#f3f4f5` | `#191a1b` | 세그먼트 트랙, 인풋 배경 |
| `--bg-hover` | `#f3f4f5` | `rgba(255,255,255,0.055)` | 행 hover |
| `--border` | `#e6e6e6` | `rgba(255,255,255,0.08)` | 기본 테두리 |
| `--border-strong` | `#d5d7dc` | `rgba(255,255,255,0.12)` | 검색창·인풋 테두리 |
| `--line` | `#ececee` | `rgba(255,255,255,0.06)` | 섹션 구분선 |
| `--line-row` | `#f1f1f2` | `rgba(255,255,255,0.04)` | 테이블 행 구분선 |

### 텍스트 · 브랜드
| 토큰 | 라이트 | 다크 |
|---|---|---|
| `--fg1` | `#08090a` | `#f7f8f8` |
| `--fg2` | `#4b4f57` | `#d0d6e0` |
| `--fg3` | `#8a8f98` | `#8a8f98` |
| `--fg4` | `#5b6068` | `#62666d` |
| `--brand` (CTA 배경) | `#5e6ad2` | `#5e6ad2` |
| `--brand-hover` | `#4f5bc4` | `#6d78dd` |
| `--accent` (링크/활성 아이콘) | `#4a55b8` | `#7170ff` |
| `--accent-tint` (활성 nav 배경) | `#e9eaf6` | `rgba(255,255,255,0.06)` |
| `--accent-fg` (활성 nav 텍스트) | `#2b2f56` | `#f7f8f8` |
| `--danger` | `#a8442a` | `#d97757` |
| `--danger-tint` | `#fbeae4` | `rgba(217,119,87,0.14)` |

### 환경(운영/개발/DEMO) — 색 + 라벨 + 테두리 형태 3중 표시
개발서버를 운영서버로 착각해 여는 사고를 막는 것이 목적입니다. **색상만으로 구분하지 않습니다.**

| 환경 | 라이트 fg / bg | 다크 fg / bg | 행 테두리 | 행 배경 틴트 |
|---|---|---|---|---|
| 운영 | `#0f7a3d` / `#e3f4e9` | `#4ec98a` / `rgba(78,201,138,0.14)` | `1px solid transparent` | 없음 |
| 개발 | `#8a5a10` / `#fbeedb` | `#e0a13a` / `rgba(224,161,58,0.16)` | `1px dashed #c98b2a` (다크 `rgba(224,161,58,0.5)`) | `#fdf8ef` (다크 `rgba(224,161,58,0.05)`) |
| DEMO | `#4a3ba8` / `#ede9fb` | `#a78bfa` / `rgba(167,139,250,0.16)` | `1px dashed #8b7fd4` (다크 `rgba(167,139,250,0.5)`) | `#f7f5fe` (다크 `rgba(167,139,250,0.05)`) |

라벨 텍스트: `운영` / `개발` / `DEMO`. 라이트 모드 대비: 운영 5.4:1, 개발 5.9:1, DEMO 7.1:1 (WCAG AA 충족).
환경 판정 규칙(프로토타입 로직): 이름에 `DEMO` 포함 → demo, host가 `dev.` 또는 `-dev.` 포함 → dev, 그 외 → prod. 실제 구현에서는 링크 엔티티에 `env` 컬럼을 두고 관리 화면에서 지정하는 방식을 권장합니다.

### 분류 컬러 (색약 대응 8+색, 아이콘·라벨 병행 필수)
| 분류 | 축약 | 라이트 fg | 라이트 틴트 | 다크 fg |
|---|---|---|---|---|
| 오션팔레트 군산 | 오션 | `#0d6b7d` | `#e0f0f4` | `#4cc2d6` |
| 아마존 당진 | 당진 | `#8a5a10` | `#fbeedb` | `#e0a13a` |
| 아마존 완주 | 완주 | `#97452a` | `#f8e8e2` | `#d97757` |
| 스포플랫 | 스포 | `#0f7a3d` | `#e3f4e9` | `#4ec98a` |
| 병원 | 병원 | `#2f5bb8` | `#e6ecfa` | `#7fa9ff` |
| 스테이 / 스테이&카라반 | 스테 / 카라 | `#6b3f8f` | `#f0e7f7` | `#c084d8` |
| 티켓플랫 | 티켓 | `#8c2f5c` | `#f6e6ee` | `#d67ba0` |
| 이지에셋 | 이지 | `#4a6b1f` | `#e8f0e0` | `#9db34f` |
| ATS 군산 DEMO | ATS | `#4a3ba8` | `#ede9fb` | `#a78bfa` |
| 조이랜드 | 조이 | `#3a45a8` | `#e7e9fa` | `#7170ff` |
| 코어솔루션 / 기타 | 코어 / 기타 | `#4b4f57` | `#eff0f1` | `#8a8f98` |

다크 모드 틴트는 다크 fg를 14~16% 알파로: `color-mix(in srgb, var(--c) 16%, transparent)`.

### 타이포
- 폰트: `'Inter', 'SF Pro Display', -apple-system, system-ui, 'Malgun Gothic', sans-serif`, `font-feature-settings: "cv01","ss03"`
- mono: `ui-monospace, 'SF Mono', Menlo, monospace` — host, 시각, 순서값, 카운터에만 사용
- 굵기: 400 / **510(강조 기본)** / 590(라벨·제목). 700 사용 금지
- 스케일: 화면 제목 22px/510/`-0.4px` · 섹션 제목 16px/510/`-0.2px` · 링크 이름 14px/510 · 본문·목록 13px/400 · host·메타 11.5px mono · 라벨 10.5px/590/`0.08em`

### 스페이싱 · radius · 밀도
- 8px 그리드: 4 · 8 · 12 · 16 · 20 · 24 · 28 · 32
- radius: 4 리스트 행 내부 요소 · 6 버튼·인풋·행 · 8 카드·패널 · 12 프레임 · 9999 칩
- 행 높이: 컴팩트 32px · **중간 40px (기본)** · 그리드 카드 72px
- 그림자: 라이트에서만 사용 — 검색창 `rgba(9,9,11,0.05) 0 2px 6px`, 팔레트 `rgba(9,9,11,0.18) 0 12px 40px`. 다크는 그림자 대신 테두리 + inset `rgba(0,0,0,0.2) 0 0 12px inset`

---

## 화면

### 1. 허브 메인 (기본 화면)
**목적**: 검색 → 즐겨찾기 → 최근/인기 순으로 링크 하나를 빠르게 클릭하고 나감.

**레이아웃**: `display:flex`. 좌측 사이드바 232px 고정(`--bg-panel`, 우측 1px `--border`), 우측 본문 `flex:1`.
본문은 상단바 52px(하단 1px `--line`, padding `0 28px`) + 콘텐츠 영역(padding `20px 28px 0`, `flex-direction:column; gap:18px`).
콘텐츠 하단은 `display:grid; grid-template-columns: minmax(0,1fr) 372px; gap:26px` — 좌: 공용 링크, 우: 개인 영역 레일(좌측 1px `--line` 구분선, `padding-left:24px`).
넓은 화면 대응: 컨테이너 `max-width` 없음(전체 폭 사용), 즐겨찾기 그리드는 `repeat(auto-fill, minmax(320px, 1fr))`로 3~5열 가변 처리(프로토타입은 1520px 프레임에서 2열 고정으로 표현).

**사이드바 구성**
- 로고: 24×24 `--brand` radius 6 + 링크 아이콘 13px, 라벨 13.5px/590
- 내비 3개(허브 / 링크 관리 / 계정 설정): 행 padding `8px 9px`, radius 6, 아이콘 15px + 13px 텍스트. 활성 = 배경 `--accent-tint`, 텍스트 `--accent-fg`, 아이콘 `--accent`. 비활성 텍스트 `--fg4`, hover 배경 `#eff0f1`
- "분류" 섹션 라벨 10.5px/590/`0.09em` `--fg3` → 분류 목록(8×8 컬러 스퀘어 radius 2 + 이름 12.5px + 우측 개수 11px)
- 하단 사용자 영역: 상단 1px `--border`, 26px 원형 아바타(`--accent-tint`/`--accent`), 이름 12.5px/510 + 이메일 11px `--fg3`, 우측 로그아웃 아이콘 15px

**본문 상단바**: 좌측 "허브" 13px/510 + "공용 28 · 개인 8" 12px `--fg3`. 우측 테마 토글(Secondary 버튼, 라벨 = 전환될 모드명) + "링크 관리" Primary 버튼.

**공지 배너** (있을 때만): padding `9px 12px`, radius 8, 배경 `#eef0fb`, 테두리 `1px solid #dcdff5`, info 아이콘 15px `--accent`, 텍스트 13px `#2b2f56`, 우측 닫기 X 14px. 한 줄 고정. 닫으면 메시지가 바뀔 때까지 재노출 금지.

**검색(주인공)**: padding `13px 16px`, radius 10, 배경 `--bg`, 테두리 `1px solid --border-strong`, 그림자 `rgba(9,9,11,0.05) 0 2px 6px`. 좌측 검색 아이콘 18px, placeholder 16px `--fg3` "링크 · 분류 · host 검색", 우측 "커맨드 팔레트" 11.5px + `⌘` `K` kbd(11px mono, 배경 `--bg-surface`, 테두리 `--border`, radius 4). 클릭 또는 ⌘/Ctrl+K → 커맨드 팔레트.

**필터 칩 행**: 전체(활성: 배경 `--fg1`, 텍스트 `#fff`) / 운영 21 / 개발 6(테두리 `1px dashed #c98b2a`, 텍스트 `#8a5a10`) / 개인 링크 8. 칩 padding `5px 12px`, radius 9999, 12px. 우측 끝에 밀도 세그먼트(행 / 그리드) — 트랙 `--bg-surface` + 테두리 `--border` radius 7, 활성 세그먼트 배경 `#ffffff`(다크 `rgba(255,255,255,0.08)`).

**링크 행 (핵심 컴포넌트)**
```
grid-template-columns: 42px 26px minmax(0,1fr) auto;  /* 환경라벨 · 분류뱃지 · 이름+host · 액션 */
align-items:center; gap:9px; min-height:40px; padding:6px 9px; border-radius:6px;
border:1px solid transparent;   /* 개발·DEMO는 dashed + 환경 색 */
```
- 환경 라벨: 42px 고정 열, `padding:3px 0`, radius 4, 중앙 정렬, 10.5px/590, 환경 fg/bg
- 분류 뱃지: 26×26, radius 5, 축약 2글자 10px/590, 분류 fg + 틴트 bg
- 이름 14px/510 `--fg1` (1줄 ellipsis) / host 11.5px mono `--fg3` (1줄 ellipsis) — **URL 전체 노출 금지, host만**. 전체 URL은 `title` 속성 또는 tooltip으로
- 액션(▲ ▼ ★): 22×22 버튼 3개, gap 3, 배경 `#eff0f1`(★는 `#fbeedb` + 별 `#b07408`). 기본 `opacity:0`, `:hover`/`:focus-within`에서 `opacity:1`
- 행 hover 배경 `--bg-hover`. 행 전체가 `<a>`이며 키보드 포커스 시 `outline: 2px solid var(--brand)` + 액션 노출

**섹션 헤더**: 아이콘 13px + 라벨 11px/590/`0.08em` `--fg2` + 개수 11px `--fg3` + 남은 폭을 채우는 1px `--line` 선 + (선택) 우측 보조 액션 11.5px `--fg3`.

**좌측 본문 섹션 순서**
1. 즐겨찾기 (11개) — 행 그리드, 우측 "순서 편집"
2. 인기 링크(최근 30일) + 최근 사용 — 2열 병렬. 인기 행은 좌측에 순위 숫자 11px mono 열(16px) 추가, 우측에 클릭수. 최근 사용 행은 우측에 시각 11px mono. 두 목록 모두 34px 컴팩트 행
3. 전체 분류 — 분류별 그룹. 그룹 헤더(8×8 컬러 스퀘어 + 이름 12px/510 + 개수 + 우측 "모두 열기" 11px `--accent`), 그 아래 34px 행(환경 라벨 42px + 이름 13px + 우측 host 11px mono)

**우측 개인 레일**
- 내 링크 헤더: 사람 아이콘 + "내 링크" + 개수 + 우측 버튼 3개(선택 / 가져오기 = Secondary 11px, 추가 = Primary 11px)
- 그룹(스테이&카라반 / 업무 / 기타): 그룹 헤더 11.5px/510 + 개수 + 우측 "모두 열기". 행은 36px, `grid-template-columns: 22px minmax(0,1fr) auto` (분류 뱃지 22px · 이름 13px + host 11px mono · 우측). 계정정보/비밀번호가 설명에 포함된 링크는 "계정" 칩(자물쇠 아이콘 10px + 10px 텍스트, 배경 `--bg-surface`) 표시 — **값은 노출하지 않음**. 연필(편집) 버튼은 hover 시 노출
- 내 메모: 카드 padding 13px, 테두리 `--border`, radius 8, 배경 `--bg-panel`. 헤더(문서 아이콘 + "내 메모" + "나만 볼 수 있음" + 접기 chevron), 본문 textarea(배경 `--bg`, 테두리 `#e0e1e4`, radius 6, 11.5px mono, `line-height:1.75`), 하단 `154 / 2000` 카운터 11px mono + 우측 "저장" Secondary. 최대 2000자

### 2. 커맨드 팔레트 (⌘/Ctrl+K)
**목적**: 검색만으로 링크를 찾아 Enter로 여는 1급 진입점.

오버레이 `rgba(11,12,13,0.3)`, 패널 700px 폭, 상단에서 76px, 배경 `--bg`, 테두리 `--border-strong`, radius 12, 그림자 `rgba(9,9,11,0.18) 0 12px 40px`.
- 입력 행: padding `14px 16px`, 하단 1px `--line`, 검색 아이콘 17px + 입력 16px + "N개 결과" 11.5px + `esc` kbd
- 결과: 분류별 그룹 라벨(10.5px/590/`0.08em`) + 결과 행. 행은 `42px 24px minmax(0,1fr) auto`, padding `8px 10px`. 선택 행은 hover/선택 배경 + 우측 `↵` kbd
- 개발·DEMO 결과: 점선 테두리 + 배경 틴트 + 우측 인라인 경고 "개발서버입니다" 11px 환경 fg
- 액션 섹션: "'<쿼리>' 이름으로 링크 추가 ⌘N", "'<분류>' 분류 전체 열기 · N개 ⌘O"
- 푸터: 배경 `--bg-panel`, 상단 1px `--line`, 10.5px `--fg3` — `↑↓ 이동` `↵ 열기` `⌘↵ 새 탭` `⌘F 즐겨찾기`, 우측 "개발·DEMO는 점선 + 라벨"
- 검색 대상: 링크 이름, 분류명, host. 결과 없으면 빈 상태 + 추가 액션

### 3. 링크 관리 (탭 3개)
**목적**: 공용 링크 CRUD, 분류 순서, 공지 관리. 기존의 긴 단일 스크롤을 탭으로 분리.

헤더: 제목 16px/510 + "공용 28개 · 12개 분류" + 우측 "링크 추가" Primary(클릭 시 우측 슬라이드 패널 또는 모달 — 상시 노출 폼 금지).
탭 바: `padding:9px 12px`, 13px/510, 활성 = `--fg1` + `border-bottom:2px solid var(--brand)`, 비활성 = `--fg3` + transparent.

**탭 1 · 링크**: 검색 인풋 + 분류/환경 필터 → 테이블. 컬럼 `42px 26px minmax(0,2fr) minmax(0,2fr) 150px 96px` = 환경 · 뱃지 · 이름 · URL · 분류 · 순서+작업. 헤더 행 배경 `--bg-panel`, 10.5px/590 라벨. 데이터 행 padding `8px 14px`, 하단 1px `--line-row`, hover `--bg-hover`. 작업(편집·삭제 22×22)은 hover 시 노출, 삭제는 `--danger`.
**탭 2 · 분류 순서**: 좌 440px 리스트 — `16px 10px minmax(0,1fr) 48px 52px` = 드래그 핸들 · 컬러 · 이름 · 링크 수 · 순서값(mono, 배경 `--bg-surface`). 드래그로 정렬, 저장은 목록 상단/하단 고정 버튼. 우측은 선택 시 분류 편집(색상은 위 8색 세트에서만 선택), 미선택 시 점선 빈 상태.
**탭 3 · 공지**: 좌 560px 편집 폼(메시지 textarea, 강조 3택 안내/주의/점검, 노출 토글, 되돌리기 + 저장) + 우측 실제 배너 미리보기.

### 4. 계정 설정
좌측 사이드바에 설정 목차(프로필 / 비밀번호 / 기기·세션 / 개인 링크·메모 / 테마) 추가.
본문: 프로필 헤더(40px 아바타 + 이름 16px/510 + "이메일 · 권한 · 개인 링크 N개 · 메모 N자" + 우측 로그아웃) → 2열 그리드 `minmax(0,1fr) minmax(0,1.15fr)`:
- 비밀번호 변경 카드: 현재/새 비밀번호 인풋, 강도 인디케이터(3분할 3px 바 + 텍스트), 우측 하단 "변경하기" Primary
- 기기·세션 카드: 헤더(제목 + "'이 기기 기억하기' 3대" + 우측 "다른 기기 모두 로그아웃" danger Secondary) + 세션 행 3개(`20px minmax(0,1fr) auto` = 기기 아이콘 · 기기명+위치/시각 · 현재 칩 또는 "로그아웃"). 공용 단말은 경고색 부가문구
- 하단 테마 카드: 설명 + 시스템/라이트/다크 세그먼트(계정에 저장)

### 5. 로그인 / 회원가입
`--bg-panel` 캔버스 중앙에 400px 폭 단일 폼(카드 없음). 로고 32px → 제목 22px/510 "링크 허브 로그인" → 설명 13px `--fg4` → 아이디/비밀번호 인풋(padding `10px 12px`, radius 6) → "이 기기 기억하기" 블록(점선 `#c98b2a` + 배경 `#fdf8ef`, 체크박스 15px + 라벨 13px + 경고문 11.5px `#8a5a10`) → 로그인 Primary 풀폭(padding 12px) → "계정이 없으신가요? 회원가입 · 허브로 돌아가기" 12px 중앙.

**회원가입**은 같은 400px 중앙 폼 규격을 그대로 씁니다. 제목 "회원가입", 설명 "사내 가입코드로 계정을 만들고 개인 링크 공간을 시작하세요.", 필드 순서: 이메일 → (이름 · 소속 분류 2열, 분류는 셀렉트 + 컬러 스퀘어) → 비밀번호(강도 3분할 바 + "8자 이상, 영문·숫자 포함" 규칙 11px) → 가입코드(라벨 우측 "관리자에게 발급 요청" 11px, 인풋은 mono + `letter-spacing:0.08em`) → "가입하기" Primary 풀폭 → "이미 계정이 있으신가요? 로그인 · 허브로 돌아가기". 기존 좌측 남색 브랜드 패널은 제거.

---

## 인터랙션 & 동작
- **⌘/Ctrl+K**: 팔레트 열기. `esc` 닫기, `↑↓` 이동, `↵` 열기, `⌘↵` 새 탭, `⌘F` 즐겨찾기 토글, `⌘N` 추가, `⌘O` 분류 전체 열기
- **행 클릭**: `target="_blank" rel="noopener"` 새 탭. 행 전체가 링크, 액션 버튼은 `event.stopPropagation()`
- **부수 액션 노출**: `.lh-row .lh-act{opacity:0}` → `.lh-row:hover .lh-act, .lh-row:focus-within .lh-act{opacity:1}`. `transition: opacity .12s ease` 정도만, 그 외 장식 애니메이션 없음
- **모두 열기**: 그룹 내 링크를 순차 `window.open`. 5개 초과 시 확인 다이얼로그 권장
- **밀도 토글**: 행 / 그리드. 선택값 `localStorage`(또는 계정) 저장
- **테마 토글**: `data-theme` 속성 전환 + 계정 저장. 초기값은 시스템(`prefers-color-scheme`)
- **공지 배너 닫기**: 메시지 해시를 `localStorage`에 저장해 동일 메시지 재노출 금지
- **메모 저장**: 2000자 제한, 카운터 실시간 갱신, 저장 시 인라인 성공 표시
- **빈 상태**: 점선 테두리 박스 + 검색 아이콘 20px + "'<쿼리>'와 일치하는 링크가 없습니다" 12.5px + "이 이름으로 링크 추가 · ⌘N" 11px
- **반응형**: ≥1600px 즐겨찾기 4~5열 · 1280~1600px 3열 + 개인 레일 유지 · <1280px 개인 레일을 본문 하단으로 이동 · <900px 사이드바를 상단 드로어로, 행은 2열 그리드(환경 라벨 + 이름/host), 액션은 항상 노출(터치 타겟 44px 이상)

## 상태
서버 렌더링 기준으로 필요한 값: 현재 사용자, 테마, 밀도, 공지(메시지·강조·노출), 링크 목록(이름·url·host·분류·환경·순서·즐겨찾기 여부), 개인 링크(그룹·계정정보 포함 여부), 메모(내용·글자수), 최근 사용(시각), 인기 링크(30일 클릭수), 세션 목록.
클라이언트 상태는 팔레트 열림/쿼리/선택 인덱스, 밀도, 테마, 배너 닫힘, 관리 화면 탭, hover/focus 뿐입니다.

## 참고 CSS (구현 시작점)
```css
.lh-row{display:grid;grid-template-columns:42px 26px 1fr auto;align-items:center;
  gap:9px;min-height:40px;padding:6px 9px;border:1px solid transparent;
  border-radius:var(--radius-md);text-decoration:none}
.lh-row:hover{background:var(--bg-hover)}
.lh-row .lh-act{opacity:0;transition:opacity .12s ease}
.lh-row:hover .lh-act,.lh-row:focus-within .lh-act{opacity:1}
.lh-env{padding:3px 0;border-radius:4px;text-align:center;
  font-size:10.5px;font-weight:590}
.env-prod .lh-env{color:var(--env-prod);background:var(--env-prod-tint)}
.env-dev{border:1px dashed var(--env-dev-line);background:var(--env-dev-bg)}
.env-dev .lh-env{color:var(--env-dev);background:var(--env-dev-tint)}
.lh-badge{width:26px;height:26px;border-radius:5px;display:flex;
  align-items:center;justify-content:center;font-size:10px;font-weight:590;
  color:var(--c);background:color-mix(in srgb,var(--c) 14%,transparent)}
.lh-name{font-size:14px;font-weight:510;color:var(--fg1);
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.lh-host{font-size:11.5px;color:var(--fg3);font-family:var(--font-mono);
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
```

```html
<a class="lh-row" th:classappend="|env-${link.env}|"
   th:href="@{${link.url}}" target="_blank" rel="noopener"
   th:title="${link.url}">
  <span class="lh-env" th:text="#{'env.' + ${link.env}}">운영</span>
  <span class="lh-badge" th:style="|--c:${link.catColor}|"
        th:text="${link.catShort}">오션</span>
  <span class="lh-main">
    <span class="lh-name" th:text="${link.name}"></span>
    <span class="lh-host" th:text="${link.host}"></span>
  </span>
  <span class="lh-act">…</span>
</a>
```

## 에셋
별도 이미지 없음. 아이콘은 전부 인라인 SVG(24 viewBox, stroke 1.6~1.8, round cap, no fill)이며 프로토타입에서 그대로 복사할 수 있습니다. Lucide 아이콘 셋과 동일한 스타일이라 CDN 대체도 가능합니다.
폰트는 Inter(Google Fonts). 사내망에서 CDN 접근이 어렵다면 로컬 호스팅 후 `font-feature-settings:"cv01","ss03"` 유지.

## 파일
- `링크 허브 리디자인.dc.html` — 전체 시안. 섹션 id: `2a` 커맨드 팔레트 · `2b` 링크 관리(탭) · `2c` 계정 설정 · `2d` 로그인 · `2e` 회원가입 · `1a` 허브 메인(라이트) · `1b` 허브 메인(다크) · `1c` 토큰·컴포넌트·마크업 스펙
- `support.js` — 프로토타입 런타임(구현에는 불필요)
