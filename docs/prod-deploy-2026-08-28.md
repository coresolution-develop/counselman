# prod 배포 런북 — 2026-08-28 (링크 허브 ~ CSM-2..7, 22커밋)

작성일: 2026-08-28 · 대상 브랜치: `dev` → `prod` · **로컬 merge 완료, push 대기 중**

> 이 문서는 **이번 배포에만 해당하는 것**을 적는다.
> 명령·판정 기준의 본체는 [prod-deploy-phase1b.md](prod-deploy-phase1b.md) 다 —
> **여기에 복사하지 않는다.** 절차가 두 곳에 갈리면 한쪽만 고쳐지고 어긋난다.

---

## 왜 지금 멈춰 있나

2026-08-28, 사무실 밖이라 **prod SSH 접속 불가**. 서버는 정상이다
(`curl -I https://csm.sosyge.net/csm/login` → `302 → /login`, 기대 동작).

**로컬 `prod` 브랜치는 fast-forward merge 까지 끝나 있다.** 사무실 복귀 후
`git push origin prod` 부터 재개하면 된다.

---

## 무엇이 나가나 — 22커밋 / 70파일 (+9009 / −432)

| 묶음 | 커밋 | 사용자에게 보이는 변화 |
|---|---|---|
| 링크 허브 리디자인 | `9e1f0e4` `5b9e75d` `cce75da` + 08-16 작업 | ⭐ **`/csm/links` UI 전면 교체.** prod 에 처음 나간다 |
| CSM-3 단가 전 단위 | `5b8a0f9` | 없음 — `/rate` 표시값 동일(33개 조합 렌더 대조) |
| CSM-2 단가 화면 읽기 전용 | `52cc079` | 사내 운영자만. 병원 계정은 원래 못 고쳤다 |
| CSM-4 사용량 outbox | `ec98593` `c09bae0` | 없음 (서버 내부) |
| CSM-6 기관 동기화 통지 | `b831e85` | 없음. `refreshFromPlatform()` 이 10분 주기로 돈다 |
| P1-6 표 열 밀림 | `4d2f516` | 사내 운영자 화면 (`/core/smssetting`) |
| `total_cost` 오버플로 | `f51e552` | 없음 — **잠재 결함**이었다(단가 42,949원 초과 시 도달) |
| mediplat 포털 아이콘 | `5e26691` | 포털에 formflow 아이콘 추가 |
| 테스트·문서·CI | 9커밋 | 없음 |

### 스키마 변경 (기동 시 자동)

- `csm.sms_batch.total_cost` **INT → BIGINT** (`CsmSchemaBootstrapService:444`, `INFORMATION_SCHEMA` 확인 후 실행이라 재실행은 no-op)
- 신규 테이블 4개: `platform_price_cache` · `sms_usage_outbox` · `sms_usage_heartbeat` · `inst_sync_outbox`

---

## 이번 배포의 확정 사항

| 항목 | 결정 | 근거 |
|---|---|---|
| **`core_update` 공지** | **하지 않는다** | 병원 계정에 보이는 변화가 없다. §9.0 의 "공지 1회"는 *"병원 담당자가 단가를 못 고치게 된다"* 를 전제했는데 **병원 계정은 원래 못 고쳤다**(`/core/smssetting` 은 `isCoreInst` 전용, `priceInsert` 는 403 이 먼저) |
| **`CSM_PRICE_PLATFORM_BASE_URL`** | ⛔ **주입하지 않는다** | MediCast **prod** 접속 정보 미확보. 켜기 전 §9.3 `curl` 대조가 필수다 |
| **필수 env 추가** | **없음** | 현재 prod 빌드(`d6b078c`)와 `${ENV}` 선언 집합이 세 파일 모두 동일 |
| 배포 횟수 | **1회** | §9.0 은 2회(CSM-3 → CSM-2)였으나, 그 조합은 어디서도 돌아 본 적이 없다. dev 에 떠 있는 것은 "전부 + URL OFF" 이고 08-28 에 검증했다 |

### ⚠️ 배포 직후부터 단가를 아무도 못 고친다

CSM-2 가 쓰기 경로를 통째로 제거했고(`corePriceInsert` / `corePriceInsertAll` 삭제,
`priceInsert` 410 Gone), 플랫폼 연동은 URL 미설정이라 돌지 않는다.
**`sms_price` 를 쓰는 코드 경로는 `PlatformPriceCache` 하나뿐이고 그것이 꺼져 있다.**

- 단가 변경이 필요하면 `csm.inst_data_cs` 를 **직접 UPDATE** 한다.
  연동이 꺼져 있어 폴링이 덮어쓰지 않는다
  ([ops-price-management.md](ops-price-management.md) 의 "DB 임의 수정 금지"는 **연동이 살아 있을 때** 얘기다)
- 이 구간은 MediCast prod 접속 정보를 받을 때까지 이어진다
- 2026-08-28 확인: **URL 주입 전까지 단가 변경 예정 없음**

> 📌 **`prod-deploy-phase1b.md` §0-3 의 조치 안내가 이 배포 이후 틀린다.**
> "입력은 `/csm/core/smssetting`" 이라고 적혀 있으나 그 화면은 읽기 전용이 된다.
> `FALLBACK` 기관을 채우려면 위의 DB 직접 UPDATE 를 쓴다.

---

## 사전 점검 — 무엇이 끝났고 무엇이 남았나

### ✅ 로컬에서 이미 통과 (2026-08-28)

| 점검 | 결과 |
|---|---|
| 테스트 (CI 게이트 동일 조건, `-PexcludeIntegration`) | **415건 전부 통과** · 실패 0 · 오류 0 |
| 0-0 필수 env 목록 재추출 | mediplat **6개** / csm **14개** — 2026-08-14 기준표와 동일 |
| 필수 env 증분 | 현재 prod 빌드와 **선언 집합 동일** → 새로 넣을 env 없음 |
| fast-forward 가능 여부 | `dev..origin/prod` 비어 있음 → 충돌 없음 |

> 마지막 항목이 중요하다. 0-0 게이트가 잡으려는 실패는 *"새 커밋이 요구하는 env 가 서버에 없다"* 인데
> **이번 배포에는 새 요구 env 가 없다.** 그래도 게이트는 돈다 — 서버 env 파일이 그 사이
> 바뀌었을 수 있고, 확인 비용이 1분이다.

### 🔲 서버에서 해야 할 것 (SSH 필요)

| 단계 | 문서 | 이번 배포에 필요한가 |
|---|---|---|
| 0-0 필수 env 대조 | phase1b §0-0 | ✅ **필수** — 08-13 12.5시간 중단의 직접 원인 |
| 0-1 `refkey` 중복 검증 | phase1b §0-1 | ✅ **필요** — UNIQUE 제약이 이미 걸려 있다면 no-op 이나, 확인 비용이 낮다 |
| 0-2 발신번호 등록 상태 | phase1b §0-2 | ⬜ 선택 — Phase 1-B 에서 이미 통과했다. 그 뒤 기관이 늘었으면 확인 |
| 0-3 단가 설정 상태 | phase1b §0-3 | ✅ **기록용으로 필요** — `FALLBACK` 기관은 배포 후 화면으로 못 고친다(위 ⚠️ 참조) |
| 1단계 DB 백업 | phase1b §1 | ✅ **필수** — `sms_batch` ALTER 가 들어간다 |

---

## 실행 순서

phase1b 의 단계 번호를 그대로 따른다. **이번 배포에서 달라지는 것만** 아래에 적는다.

### 1. push

```bash
git push origin prod
```

로컬 `prod` 는 이미 merge 돼 있다. 푸시만 하면 GitHub Actions `Deploy PROD` 가 돈다.

### 2. 워크플로 + 적재 확인 → **수동 적용**

phase1b **§2-B → §2-C** 그대로.

> ⚠️ **새벽 02:30 타이머에 맡기지 않는다.** §2-C 의 권고다 —
> 그 시각에 적용되면 ALTER 실패로 문자가 전건 중단돼도 **아침까지 아무도 모른다.**
> 08-09 장애가 정확히 그 형태였다(02:30 배포 → 08:35 발견).
> 수동 적용 후 §2-C 말미의 **마커 정리**까지 해야 새벽 재기동이 없다.

### 3. 기동 확인

phase1b **§2-D**(csm) → **§2-E**(mediplat).

- csm 로그는 `catalina.out` 이 아니라 **journald** 다 (`sudo journalctl -u csm-next`)
- war 배포라 `Started CsmApplication` 이 아니라 **`Started ServletInitializer`** 로 찍힌다

### 4. 이번 배포 전용 확인

phase1b §3 에 더해 아래를 본다.

```sql
-- 신규 테이블 4개 + total_cost 타입
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
 WHERE TABLE_SCHEMA='csm'
   AND TABLE_NAME IN ('platform_price_cache','sms_usage_outbox','sms_usage_heartbeat','inst_sync_outbox');

SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA='csm' AND TABLE_NAME='sms_batch' AND COLUMN_NAME='total_cost';
```

**성공 판정**: 테이블 **4행**, `COLUMN_TYPE` 이 **`bigint`**

화면 확인 두 곳:

- `https://csm.sosyge.net/csm/core/smssetting` — 배너 표시 / 수정 버튼 없음 /
  **표 열이 안 밀림**(P1-6) / 단가 없는 기관이 `null` 이 아니라 **`미설정`**
- `https://csm.sosyge.net/csm/links` — 새 허브 UI

### 5. 롤백이 필요하면

phase1b **§7단계**. 이번 배포에서 특히 새길 것:

> **csm 과 mediplat 은 독립적으로 롤백한다.** 같은 파이프라인으로 나가지만 별개 프로세스다.
> 2026-08-13 에는 mediplat 만 죽고 csm 은 멀쩡했다. **한쪽 문제로 양쪽을 되돌리지 말 것.**

백업 파일은 **이름의 `<TS>` 기준으로 최신**을 고른다. `cp -a` 가 mtime 을 보존하므로
mtime 으로 고르면 엉뚱한 버전을 집는다.

---

## 배포와 별개로 남는 것

### 🔲 링크 허브 정리 SQL — prod DB 에 직접 실행

[scripts/hub-category-env-cleanup.sql](../scripts/hub-category-env-cleanup.sql) (`9c0bf7e`).
`조이랜드 실서버`/`조이랜드 개발서버` → `조이랜드` + env, `ATS 군산 DEMO` → `ATS 군산` + env demo.

**코드 배포로는 반영되지 않는다.** dev 에도 아직 실행 안 됐다.
분류명은 화면을 보고 적은 것이라 **스크립트 안의 2단계 확인 쿼리로 대조한 뒤** 실행한다.

### 🔲 단가 연동 2차 (URL 주입)

MediCast **prod** 의 `base-url` / API 키 확보가 선행. 절차는
[prod-deploy-checklist.md](prod-deploy-checklist.md) §9.2~9.4.

> ⛔ 켜는 순간 `PlatformPriceCache.store()` 가 `platform_price_cache` **와 `inst_data_cs` 를 같이**
> 덮어쓴다. 잘못된 값이 들어오면 URL 을 다시 빼도 **2단계 폴백에 남아 그대로 청구된다.**
> §9.3 `curl` 대조(기관코드 대문자 / 금액 전 단위)를 **붙이기 전에** 한다.

---

## 배포 후 "비어 있는 것이 정상인 자리"

dev 에서 확인된 것과 같다. 고장으로 오인하기 쉽다 —
상세는 [handoff-2026-08-28.md](handoff-2026-08-28.md).

| 보이는 것 | 정상인 이유 |
|---|---|
| `sms_usage_heartbeat` 가 비어 있음 | 연동이 꺼져 있으면 스케줄러가 하트비트를 쓰기 **전에** 조기 리턴한다 ([SmsUsageSender.java:73](../src/main/java/com/coresolution/csm/serivce/SmsUsageSender.java#L73)). 같은 이유로 **누락 복구 스캐너도 안 돈다** |
| outbox 가 쌓이기만 함 (`sent_at` 전부 NULL) | URL 미설정이라 적재만 하고 전송하지 않는다 |
| `/core/smssetting` 전 기관 "수신 이력 없음" | 폴링을 안 하니 당연하다 |
| `priceVersion: null` | 플랫폼 단가를 못 받고 발송했다는 **정보**다. `0`/`-1` 로 채우면 "못 받았다"와 "0번 버전"이 섞인다 |

`sms_usage_outbox` 에는 **`status` 컬럼이 없다.** 상태는 `sent_at` / `next_retry_at` /
`failed_reason` 로 표현한다. PK 는 `batch_id` 이고 `id` 컬럼도 없다.
