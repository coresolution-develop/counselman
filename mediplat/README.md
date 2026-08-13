# MediPlat

별도 폴더에서 실행되는 간단한 플랫폼 앱입니다.

루트 실행 체크리스트는 [README.md](../README.md)를 참고하세요.

## 실행

```bash
cd /Users/leesumin/csm/mediplat
../gradlew bootRun
```

기본 포트는 `8082`입니다.

## 기본 URL

- 로그인: `http://localhost:8082/login`
- 서비스 선택: `http://localhost:8082/services`
- 관리자: `http://localhost:8082/admin`

## 기본 관리자 계정

- 기관코드: `PLATFORM_ADMIN_INST_CODE` (기본 `core`)
- 아이디: `PLATFORM_ADMIN_USERNAME` (기본 `coreadmin`)
- 비밀번호: **`PLATFORM_ADMIN_PASSWORD` 환경변수 — 기본값 없음(필수)**

MediPlat 은 기동할 때마다 이 계정을 생성/갱신합니다. `PLATFORM_ADMIN_PASSWORD` 가
비어 있으면 기동에 실패합니다(의도된 fail-fast 동작).

키 목록은 [`.env.example`](.env.example) 을 참고하세요.

일반 기관 사용자는 `MediPlat` 로컬 계정이 아니라 `CounselMan` 실제 계정으로 로그인합니다.

## CounselMan 연동 설정

`mediplat/src/main/resources/application.properties`

- `platform.counselman.datasource.url`
- `platform.counselman.datasource.username`
- `platform.counselman.datasource.password`
- `platform.counselman.datasource.driver-class-name` (선택, 미지정 시 URL 기준 자동 감지)
- `platform.counselman.login.aes-key`
- `platform.bootstrap.counselman-base-url`
- `platform.runtime-env` (`LOCAL`, `DEV`, `PROD`)
- `platform.counselman.sso-shared-secret`
- `platform.counselman.sso-expire-seconds`

`CounselMan` 쪽의 `MEDIPLAT_SSO_SHARED_SECRET`와 `MediPlat` 쪽의
`COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET` 값은 동일해야 합니다.

서비스 URL은 `mp_service_endpoint` 테이블에서 환경별(`LOCAL/DEV/PROD`)로 관리합니다.

## 브랜드 아이콘

코어솔루션 시스템 아이콘 패밀리는 `src/main/resources/static/icons/` 에 있습니다.
파일 규칙·컬러 토큰·크기별 사용 가이드는 [`icons/README.md`](src/main/resources/static/icons/README.md) 참고.

- **MediPlat 자체 마크**: `mediplat-b` 확정. 포털 헤더는 `mediplat-b-symbol.svg`,
  파비콘/앱아이콘 PNG(`favicon*.png`, `favicon.ico`, `apple-touch-icon.png`,
  `android-chrome-*.png`)는 이 마크에서 생성한 것입니다.
- **서비스 카드 아이콘**: `portal-app.jsx` 의 `BRAND_APPICON` 이 `serviceCode` → 앱아이콘
  경로를 매핑합니다. 매핑이 없는 서비스는 기존 라인 글리프(`AppIcon`)로 자동 폴백합니다.

| serviceCode | 아이콘 |
|---|---|
| `COUNSELMAN` | `counselman-appicon.svg` |
| `ROOM_BOARD` | `wardhub-appicon.svg` |
| 그 외 | 라인 글리프 폴백 |

`reshub-*` 는 아직 MediPlat 에 편입되지 않은 시스템이라 파일만 보관돼 있습니다.
새 시스템을 붙일 때 `BRAND_APPICON` 에 한 줄 추가하면 카드에 반영됩니다.

PNG 재생성이 필요하면 SVG 를 브라우저 렌더러로 래스터화하세요. ImageMagick 내장 SVG
렌더러는 `stroke` 패스를 누락시켜 마크가 깨집니다.
