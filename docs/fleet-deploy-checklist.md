# 차량운행관리(fleet) 배포 체크리스트

mediplat 모듈에 내장된 fleet(차량운행관리) 운영 배포 시 확인 사항.

## 1. 계기판 사진 저장 경로 = 일일 백업 대상 (필수)

- 설정 키: `platform.fleet.photo.base-dir` (env: `FLEET_PHOTO_BASE_DIR`)
- 기본값: `${user.home}/mediplat-fleet-photos`
- **DB(`mp_fleet_trip_log`)에는 base-dir 기준 상대 경로만 저장**된다. 실제 이미지 파일은 이 디렉토리에만 존재한다.
- 따라서 이 경로는 **반드시 일일 백업 대상 볼륨**으로 지정해야 한다.
  파일이 유실되면 운행기록부(세무) 증빙 사진이 복구 불가능하게 사라진다.
- 배포 시:
  - [ ] `FLEET_PHOTO_BASE_DIR`를 백업되는 마운트(예: 백업 크론이 도는 `/data/backup/...` 하위)로 지정
  - [ ] 애플리케이션 실행 계정이 해당 경로에 쓰기 권한 보유
  - [ ] 백업 크론에 해당 경로 포함 여부 확인(신규 경로면 백업 잡에 추가)

## 2. 쿠키 보안

- `fleet.device.cookie-secure` (env: `FLEET_DEVICE_COOKIE_SECURE`) — 운영(HTTPS)에서는 **true 유지**.
  로컬 http 테스트에서만 false로 내린다. 쿠키 이름 `FLEET_DEVICE`, path `/fleet`.
- `fleet.device.remember-days` (env: `FLEET_DEVICE_REMEMBER_DAYS`, 기본 90) — 기기 기억 TTL(슬라이딩).

## 3. 업로드 한도

- `spring.servlet.multipart.max-file-size` 기본 12MB / `max-request-size` 15MB.
  폰 원본 사진 크기에 맞춰 조정(env: `FLEET_MULTIPART_MAX_FILE_SIZE`, `FLEET_MULTIPART_MAX_REQUEST_SIZE`).

## 4. 스키마

- 테이블(`mp_fleet_vehicle`, `mp_fleet_driver`, `mp_fleet_trip_log`, `mp_fleet_device_token`)은
  `FleetService`의 `@PostConstruct`가 기동 시 `CREATE TABLE IF NOT EXISTS`로 생성한다. 수동 DDL 불필요.
- 대상 DB는 mediplat 기본 데이터소스(`csm`).

## 5. 상한 경고

- `fleet.trip.max-km` (env: `FLEET_MAX_TRIP_KM`, 기본 1500) — 1회 주행이 이 값을 초과하면
  운행을 차단하지 않고 `over_limit_yn='Y'` 플래그만 세운다(관리자 검토용).
