# 코어솔루션 시스템 아이콘 패밀리

모든 파일은 512×512 캔버스 SVG입니다. (락업만 가로형)

## 파일 규칙

| 파일 | 용도 | 배경 |
|---|---|---|
| `{key}-symbol.svg` | 심볼 단독 | 투명 (밝은 배경용, Deep Blue 메인) |
| `{key}-appicon.svg` | 앱 아이콘 | Deep Blue + 하단 웨이브, 라운드 R114 적용 |
| `{key}-lockup.svg` | 가로 락업 | 투명, 워드마크 아웃라인 패스 포함 |
| `{key}-favicon.svg` | 16px 전용 | 투명, 부재 축소·굵기 증가 |

key: `counselman`, `wardhub`, `reshub-i`, `reshub-c`, `reshub-b`, `formflow`
신규 편입: `formflow` (FormFlow · 심볼·앱아이콘·파비콘, 락업은 워드마크 소스 대기)
MediPlat 방향안: `mediplat-a`, `mediplat-b`, `mediplat-c` (심볼·앱아이콘만, 방향 확정 대기)
`_before-formflow-appicon.svg` 는 정규화 전 원본입니다.
`_archive-wardhub-grid-*` 는 채택되지 않은 이전 격자안입니다.

## 사용 가이드

- **16px 이하** — `-favicon.svg` 사용. `-symbol.svg`를 축소하면 세부가 뭉개집니다.
- **20~64px** — `-symbol.svg`
- **앱 아이콘 / 런처** — `-appicon.svg`. 라운드는 파일에 이미 클리핑되어 있으므로 CSS `border-radius`를 다시 주지 마세요.
- **헤더 / 로그인 화면** — `-lockup.svg`. 워드마크가 아웃라인 패스라 폰트 설치가 필요 없습니다.
- 딥 블루 배경 위에 심볼을 올릴 때는 `-appicon.svg`의 심볼 그룹(White + Tint)을 사용하세요.

## 컬러 토큰

```
--deep-blue:  #0C4890;  /* 메인 매스, 워드마크 앞부분 */
--light-blue: #2478C0;  /* 보조 요소, 워드마크 뒷부분, 앱아이콘 하단 웨이브 */
--tint:       #B7D6F0;  /* 딥 블루 배경 위 보조 요소 */
--white:      #FFFFFF;  /* 녹아웃, 딥 배경 위 메인 */
```

다색 그라데이션은 사용하지 않습니다. 2톤 톤온톤 + 명도 차만 사용합니다.

## 지오메트리

- 캔버스 512×512, 세이프에어리어 96~416 (콘텐츠 62.5%)
- 앱 아이콘 라운드 반경 114 (iOS 22.3%)
- 형태 겹침 분리는 배경색 녹아웃 스트로크 24
- 광학 보정 배율이 심볼별로 다르게 적용되어 있습니다 (ResHub-I ×1.06, ResHub-C ×0.96, ResHub-B ×1.06). 파일에 이미 반영되어 있으니 추가 스케일은 주지 마세요.

## 웹 파비콘 예시

```html
<link rel="icon" href="/icons/wardhub-favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/icons/wardhub-appicon.svg">
```
