# 차량운행관리(fleet) P0 실측 체크리스트

코드 P0 완성 후 **실제 구동 + 실차/폰 실측**으로 P0를 마무리하기 위한 절차. 단위테스트 65개 green ≠ 화면 동작
확인이므로, 이 체크리스트를 통과해야 P0 완성으로 본다.

---

## 0. 앱 구동 (csm dev DB 연결)

mediplat 모듈은 독립 Spring Boot 앱(포트 8082)이고, 기동 시 `FleetService.@PostConstruct`가 `mp_fleet_*`
4개 테이블을 자동 생성한다. DB는 `application.properties` 기본값(csm dev)로 연결된다.

```bash
cd /Users/leesumin/git/counselman-develop
# 로컬/LAN http 실측이면 쿠키 Secure를 내려야 FLEET_DEVICE 쿠키가 저장된다(아래 주의 참고)
FLEET_DEVICE_COOKIE_SECURE=false ./gradlew -p mediplat bootRun
```

**성공 판정**
- 로그에 `Tomcat started on port 8082` + `Started MediplatApplication` 출력 → 기동 성공.
- 기동 중 `CREATE TABLE ... mp_fleet_*` 관련 예외가 없어야 함. (있으면 csm 접속 권한/DDL 문제)
- 확인: `curl -I http://localhost:8082/login`  # 200이면 서버 응답 정상.

**주의 (실측 필수 조건)**
- **쿠키 Secure**: `FLEET_DEVICE`는 기본 `Secure=true`라 **http로 접속하면 브라우저가 쿠키를 저장하지 않는다**
  → 운전자 화면이 계속 "미등록"으로 돈다. 로컬/LAN http 실측 시 위처럼 `FLEET_DEVICE_COOKIE_SECURE=false`.
  운영(https)에서는 `true` 유지.
- **폰 접근 주소**: 폰이 서버에 닿아야 한다. 로컬 실측이면 폰을 같은 Wi‑Fi에 두고 `http://<맥 LAN IP>:8082/...`로 접속.
  QR 인쇄도 이 주소로 접속한 상태에서 해야 QR에 올바른 주소가 담긴다(§3).
- **관리자 계정(dev)**: 부트스트랩 관리자 = inst `core` / username `coreadmin` / 비밀번호는
  `platform.bootstrap.admin-password`(env `PLATFORM_ADMIN_PASSWORD`) 값. dev 기본값은 `application.properties` 참조. 운영 값은 env로 주입.
- **OCR 인터넷**: Tesseract.js는 CDN lazy-load라 폰에 인터넷이 있어야 OCR이 동작한다(P0 한계).
  인터넷이 없어도 수동 입력으로 저장은 가능.

---

## 1. 화면별 성공 판정 (Thymeleaf 6종 첫 렌더)

첫 구동은 **관리자 화면부터** 렌더 디버깅한다(운전자 화면은 차량·운전자 등록 후라야 태울 수 있음).
"예상범위 에러"는 그 화면에서 흔히 나올 수 있는, 원인이 명확한 오류를 뜻한다.

| # | 경로 / 템플릿 | 뜨면 정상 | 예상범위 에러(원인) |
|---|---|---|---|
| 1 | `/login` → 로그인 | core / coreadmin / (부트스트랩 비밀번호)로 로그인 → `/portal` 이동 | 로그인 실패 = dev 관리자 미시딩(csm 접속/부트스트랩 확인) |
| 2 | `/portal` · `design/Portal.html` | '차량운행관리' 카드(경로 아이콘) 노출, 클릭 → `/fleet/admin` | 카드 없음 = `mp_service` FLEET 미시딩 또는 core 접근 미부여 |
| 3 | `/fleet/admin` · `fleet-admin.html` | 차량/운전자 섹션·등록 폼 렌더. 등록 시 초록 배너 + 행 추가 | 500 = Thymeleaf 식 오류(로그의 `SpelEvaluationException`/`TemplateProcessingException`) |
| 4 | `/fleet/admin/vehicles/{id}/qr-print` · `fleet-qr-print.html` | 번호판 + QR 이미지 + URL 텍스트 렌더 | QR 안 뜸 = 콘솔에서 `/vendor/qrcode.min.js` 404/로드 실패 확인 |
| 5 | `/fleet/admin/trips` · `fleet-admin-trips.html` | 초기 "조회된 운행 기록 없음", 필터 폼 렌더. 운행 후 사진 썸네일 200 | 썸네일 깨짐 = 사진 서빙 404(경로/inst 스코프) 또는 base-dir 권한 |
| 6 | `/fleet/t/{qrToken}` · `fleet-driver.html` | 모바일 셸 → 운전자 선택 목록 → 등록 → 출발 폼 | 계속 "본인 확인" 반복 = 쿠키 Secure(http 미저장) 문제(§0 주의) |

각 화면에서 500이 나면 **bootRun 콘솔 스택트레이스의 첫 원인 줄**을 잡아 최소 수정한다(추측 다중 수정 금지).

---

## 2. 실측 순서 (해피패스 1회)

1. §0으로 앱 구동 → §1-1,2로 로그인·포털 카드 확인.
2. `/fleet/admin`에서 **차량 1대 등록**(번호판·초기 계기판) + **운전자 1명 등록**(본인 이름).
3. `/fleet/admin/vehicles/{id}/qr-print`를 **폰 접근 가능한 주소로 접속**해 인쇄(또는 화면 QR을 폰으로 스캔).
   - 인쇄 화면의 URL 텍스트가 폰에서 열리는 주소인지 확인. localhost 경고가 보이면 주소를 바꿔 다시.
4. 폰으로 QR 스캔 → **본인 선택(최초 1회)** → 출발 계기판 촬영 → 목적 선택 → **출발 기록**.
5. 잠시 후 같은 QR 재스캔 → **도착 계기판 촬영 → 도착 기록** → 주행거리 표시 확인.
6. `/fleet/admin/trips`에서 방금 운행이 사진 썸네일과 함께 보이는지 확인.

**성공 판정**: 4~5에서 사진 필수·출발/도착이 저장되고, 6에서 관리자 목록에 반영되면 기능 해피패스 통과.

---

## 3. OCR 실인식률 측정 (P0의 진짜 결론)

OCR은 **보조 수단**이다. P0 성립의 필수 조건과 OCR 품질 목표를 분리해 판정한다.

### 3-1. 필수 조건 (반드시 100%)
- [ ] 사진 없이는 저장 버튼이 활성되지 않는다.
- [ ] OCR이 실패/미로드여도 **수동 입력으로 저장이 된다**.
- [ ] 저장된 운행이 관리자 목록·사진에 정상 반영된다.

→ 위 3개가 하나라도 깨지면 P0 미완(코드 수정 과제).

### 3-2. OCR 품질 목표 (측정)
실제 계기판(또는 유사 7세그먼트/디지털 숫자판)을 **조도·각도를 달리해 N=20회** 촬영하며 프리필 결과를 기록.

| 회차 | 실제 값 | OCR 프리필 | 판정(정확/부분/실패) | 비고(조도·각도) |
|---|---|---|---|---|
| 1 | | | | |
| … | | | | |
| 20 | | | | |

- **정확**: 프리필 = 실제 값(수정 불필요)
- **부분**: 자릿수 일부만 맞음(수정 소폭)
- **실패**: 빈값 또는 크게 틀림(수동 입력)

**합격 기준(보조 수단 기준선)**
- 선명한 정면 촬영에서 **정확 ≥ 70% (20회 중 14회 이상)** → OCR 채택, P0 통과.
- **60% 미만** → OCR 효용이 낮음 → 아래 캡처 UX 조정을 P0 마무리 과제로 착수.

---

## 4. 실패 시 캡처 UX 조정 (P0 마무리 과제)

3-2가 기준 미달이면 다음을 P0 마무리로 처리한다(코드 P0와 별개의 튜닝 사이클):
- 촬영 화면에 **정렬 오버레이 프레임** 추가 → 계기판 영역을 프레임에 맞추게 유도(크롭 정확도↑).
- `ocr.js`의 중앙 크롭 비율(`0.9 x 0.32`) 실측 기반 튜닝.
- 안내 문구/조도 가이드("계기판을 프레임에 맞추고 반사 피하기").
- 필요 시 온라인 한정 Google Vision 폴백 검토(무료 티어) — 단 오프라인·비용 원칙 재확인 후.

> OCR 인식률은 실측 전에는 알 수 없다. 이 문서의 §3 측정 결과가 P0의 실질 결론이며, 결과는 정직하게
> (정확/부분/실패 카운트 그대로) 보고한다.
