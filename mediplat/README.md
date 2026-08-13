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
- **서비스 카드 아이콘**: 코드가 아니라 **관리자 화면에서 지정**합니다.
  `/admin` → "앱 등록" 패널의 *포털 아이콘* 에서 고르면 `mp_service.icon_key` 에 저장되고,
  포털 카드가 `/icons/{icon_key}-appicon.svg` 를 씁니다. 기존 앱은 수정으로 바꿀 수 있습니다.
  미지정이면 포털에서 기본 아이콘 하나로 통일해 표시합니다.

선택 목록은 `ServiceIconCatalog` 가 기동할 때 `static/icons/*-appicon.svg` 를 훑어 만듭니다.
**새 아이콘을 추가하려면 `{key}-appicon.svg` 를 폴더에 넣고 배포·재기동**하면 됩니다 — 코드 수정은 없습니다.
(`{key}` 는 소문자·숫자·하이픈만. 카탈로그에 없는 key 는 저장 단계에서 거부합니다.)

`icon_key` 컬럼은 기동 시 자동으로 추가됩니다(MySQL은 `INFORMATION_SCHEMA` 확인 후 `ALTER`).
별도 마이그레이션 SQL을 돌릴 필요는 없습니다.

`reshub-*` 는 아직 MediPlat 에 편입되지 않은 시스템이지만, 파일이 있으므로 앱을 등록하면
바로 아이콘으로 고를 수 있습니다.

PNG 재생성이 필요하면 SVG 를 브라우저 렌더러로 래스터화하세요. ImageMagick 내장 SVG
렌더러는 `stroke` 패스를 누락시켜 마크가 깨집니다.
