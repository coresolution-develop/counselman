# MediPlat

별도 폴더에서 실행되는 간단한 플랫폼 앱입니다.

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

- 기관코드: `core`
- 아이디: `coreadmin`
- 비밀번호: `123qwe`

운영 전에는 반드시 환경변수로 변경해야 합니다.

일반 기관 사용자는 `MediPlat` 로컬 계정이 아니라 `CounselMan` 실제 계정으로 로그인합니다.

## CounselMan 연동 설정

`mediplat/src/main/resources/application.properties`

- `platform.counselman.datasource.url`
- `platform.counselman.datasource.username`
- `platform.counselman.datasource.password`
- `platform.counselman.login.aes-key`
- `platform.bootstrap.counselman-base-url`
- `platform.counselman.sso-shared-secret`
- `platform.counselman.sso-expire-seconds`

`CounselMan` 쪽의 `MEDIPLAT_SSO_SHARED_SECRET`와 `MediPlat` 쪽의
`COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET` 값은 동일해야 합니다.
