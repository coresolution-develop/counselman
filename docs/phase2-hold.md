# Phase 2 보류 — 콜백 엔드포인트 보호

> **상태: 일시 중단 (2026-08-13).** 토스페이먼츠 심사 준비(Phase F) 우선.
> 2-A 조사는 **완료**되었고 서버 실측도 끝났다. 재개 시 이 문서만 읽으면 컨텍스트가 복원된다.
> 관련: [sms-batch-ops.md](sms-batch-ops.md), [sms-send-entrypoints.md](sms-send-entrypoints.md)

## 목적 (재개 시 다시 읽을 것)

`POST /api/external/SMSRequest` (비즈뿌리오 결과 리포트 콜백)는 인증·서명 검증이 없다.
지금은 상태값만 바뀌므로 피해가 제한적이지만, **Phase 4 에서 이 콜백이
"발송 실패 → 잔액 환불" 트리거가 된다.** 그 시점에는 외부인이 임의 refkey 로 ERROR 를
밀어넣어 무한 환불을 유발할 수 있다. **Phase 3(지갑) 착수 전에 반드시 막아야 한다.**

## 2-A 조사 결과 (완료)

### 실측 확보된 사실 (서버 access log 기준)

| 항목 | 실측값 |
|---|---|
| 콜백 발신 IP | **115.71.53.78, 115.71.53.79** (access log 188건 전부 이 두 개) |
| 콜백 경로 | **`POST /api/external/SMSRequest` 단일.** 하위에 다른 엔드포인트 없음 |
| 현재 라우팅 | httpd → **AJP 8009 → 레거시 ROOT.war** |
| csm-next 수신 건수 | **0건** |

### 여기서 도출되는 것

1. **Phase 1-B 의 신규 콜백 파서는 실전에서 동작한 적이 없다.**
   dev 검증은 localhost 에서 `/csm/api/external/SMSRequest` 로 직접 주입한 것이라
   이 사실을 드러내지 않았다.
2. **실서버 배포 전에 콜백 라우팅 전환이 선행되어야 한다.**
   Phase 1-B 가 발송하는 `MP-{inst}-{id}` 형식 refkey 를 레거시의
   `substring(0, 4)` 파서가 읽지 못한다. 라우팅 전환 없이 배포하면
   **신규 발송분의 결과 리포트가 전량 유실된다.**
3. **레거시 ROOT.war 종료는 보류다.**
   httpd 372행에 `ProxyPass / http://localhost:8080/` 이 있어 레거시가 다른
   용도로도 쓰일 가능성이 있다. 콜백만 떼어내는 방향으로 접근한다.

### A-3 현행 검증 상태 (변경 없음 — 전부 미구현)

| 항목 | 현재 |
|---|---|
| REFKEY 형식 검증 | 없음 (공백만 체크) |
| instCode 존재 검증 | 없음 (형식 검사만) |
| 존재하지 않는 refkey | 거부되지 않고 200 |
| 멱등성 | **없음.** DONE 행에 ERROR 콜백이 오면 덮어씀 |
| 중복 콜백 시 `sms_request_<inst>` | 매 요청 새 행 INSERT → 증폭 벡터 |
| 본문 크기 제한 | 없음 (multipart 50MB 설정은 JSON 경로에 미적용) |

### A-4 재전송 정책

벤더 규격서가 저장소에 없어 **확인 불가**. 현재 핸들러는 모든 경로에서 200 을 반환한다
(Jackson 파싱 실패만 Spring 이 400). 보수적 설계 유지가 타당하다.

### 구현상 반드시 지킬 사실 (spring-web 6.1.13 소스 확인)

`server.forward-headers-strategy=framework` 가 설정되어 있다
([application.properties:12](../src/main/resources/application.properties)).

- `ForwardedHeaderFilter` 가 `getRemoteAddr()` 를 **X-Forwarded-For 첫 번째 값**으로
  오버라이드한다 (`ForwardedHeaderFilter:333` → `ForwardedHeaderUtils.parseForwardedFor`).
- **같은 필터가 X-Forwarded-\* 헤더를 요청에서 제거한다.** 컨트롤러에서
  `request.getHeader("X-Forwarded-For")` 는 **null 이다.**
- → **XFF 를 직접 파싱하지 말고 `getRemoteAddr()` 를 쓸 것.**

## 승인 완료된 결정 (재개 시 그대로 적용)

### 결정 ① — 다층 방어: D + A 조합

| 계층 | 내용 |
|---|---|
| **D. IP 화이트리스트** | 실측 확보 완료 → `115.71.53.78`, `115.71.53.79` |
| **A. 시크릿 경로** | 콜백 URL 에 추측 불가 토큰 포함. 벤더 지원 불필요 |

> 서명 검증(C)·공유 시크릿 헤더(B)는 벤더 지원 여부 미확인이라 채택하지 않았다.

**한계 명시**: B-2(형식 검증)·B-3(멱등)만으로는 공격을 막지 못한다. 멱등은 이미
DONE/ERROR 인 건만 보호하므로, `SENT` 상태 건에 위조 `ERROR` 를 **처음** 밀어넣는 것은
그대로 통과한다. 인증 계층(D 또는 A)이 반드시 필요하다.

### 결정 ② — 화이트리스트 미설정 시 fail-open (한시적)

```properties
sms.bizppurio.callback.allowed-ips=115.71.53.78,115.71.53.79
sms.bizppurio.callback.allow-when-unconfigured=true   # 기본 true, 기동 시 + 매 요청 WARN
```

근거: 현 시점 콜백은 상태값만 바꾼다. fail-closed 오설정 사고는 **발송 결과 전량 유실
→ 복구 불가**인 반면, fail-open 위조 피해는 상태값 오염에 그치고 사후 정정이 가능하다.

> **Phase 4 착수 전 필수 게이트: `allow-when-unconfigured=false` 로 전환.**
> 환불 트리거가 붙는 순간 위 비대칭이 역전된다.

## 재개 시 작업 순서

| # | 항목 | 비고 |
|---|---|---|
| 0 | **콜백 라우팅 전환** | 2-B 보다 선행. 아래 선택지 참조 |
| 1 | B-1 IP 화이트리스트 (CIDR 지원, `getRemoteAddr()` 사용, 거부 시 소스 IP 로깅) | |
| 2 | B-2 REFKEY 형식 검증 (MP- 신형식 + 구형식 폴백 유지, instCode 존재 확인) | |
| 3 | B-3 멱등 (DONE/ERROR 는 상태 불변 + 200, 중복 수신 횟수 관측) | |
| 4 | B-4 본문 크기 제한 (8KB, 초과 시 413) | |
| 5 | B-5 처리 실패 시 200 + 별도 적재 (재전송 정책 미확인 → 보수적) | |
| 6 | C-1 단위 테스트 / C-2 curl 검증 절차 문서화 | |

### 0번 — 콜백 라우팅 전환 선택지 (미결정)

| 안 | 내용 | 비고 |
|---|---|---|
| A | 비즈뿌리오 콘솔의 콜백 URL 을 `/csm/api/external/SMSRequest` 로 변경 | 벤더 콘솔 작업 필요 |
| B | httpd 에서 `/api/external/SMSRequest` 만 18081 로 예외 라우팅 | URL 변경 불필요. **레거시의 다른 용도에 영향 없음** |

어느 쪽이든 전환 자체가 결과 리포트 유실 위험을 동반하므로 별도 검증 절차가 필요하다.
레거시 종료는 이번 범위가 아니다(httpd 372행 `ProxyPass /` 건 때문).

### 구현 제약 (재확인)

1. 구형식 refkey 폴백 경로를 깨뜨리지 말 것. 실서버에 구형식 콜백이 계속 들어온다.
2. 정상 콜백이 거부되면 발송 결과가 유실된다. 거부 시 반드시 관측 가능한 로그를 남길 것.
3. 이 엔드포인트는 **CSRF 예외를 유지**한다. 위 검증들이 그 자리를 대신한다.
4. 검증 로직을 컨트롤러에 인라인으로 넣지 말 것 — Phase 4 환불 트리거 때 다시 손댄다.
5. C-2 검증 시 개발서버는 localhost 호출이므로 화이트리스트에 `127.0.0.1` 포함 필요.
