# Phase 1-B 실서버 배포 절차서

> 작성: 2026-08-13 · 갱신: 2026-08-14 (실배포 결과 반영)
> 대상: dev HEAD `51cf0c2` (Phase 1-B 문자 배치 발송 + 링크 허브/포털 아이콘)
> 관련: [sms-batch-ops.md](sms-batch-ops.md) · [phase2-hold.md](phase2-hold.md)
>
> **이 문서는 순서대로 실행한다.** 각 단계의 "성공 판정"을 만족하지 못하면 다음 단계로 넘어가지 말 것.

## 진행 상태 (2026-08-14 기준)

| 단계 | 상태 |
|---|---|
| csm Phase 1-B 배포 | ✅ **완료** — 2026-08-13 20:34 (절차서 없이 선행 배포됨) |
| 3단계 스키마 검증 | ✅ 7개 기관 전부 `new_cols = 5`, 집계 뷰·collation 정상 |
| 4단계 httpd 콜백 라우팅 | ✅ **완료** — 2026-08-14 09:23 검증 (`callback ignored: empty refkey` 확인) |
| 3-2 인덱스 / 3-3 sms_batch | ⬜ 미확인 |
| 5·6단계 실발송·콜백 | ⬜ 미확인 |
| mediplat 신버전 배포 | ⬜ 보류 — 구버전(08-09 빌드) 가동 중. [장애 기록](#장애-기록--2026-08-13-mediplat-125시간-중단) 참조 |

> **이 절차서는 이번에 지켜지지 않았고, 그 결과 12.5시간 장애가 났다.**
> 다음 배포부터는 0단계부터 순서대로 수행할 것. 특히 **0-0(필수 env 대조)** 은 이번 장애의 직접 원인이다.

---

## 배포 개요 — 먼저 읽을 것

### 배포 소스는 `prod` 브랜치다 (`main` 아님)

| 항목 | 값 |
|---|---|
| 배포 트리거 | **`prod` 브랜치에 push** ([.github/workflows/deploy-prod.yml](../.github/workflows/deploy-prod.yml)) |
| `main` | **prod 배포와 무관.** dev와 89커밋 차이가 있으나 배포 경로가 아니므로 머지 불필요 |
| `prod` 현재 상태 | dev보다 **31커밋 뒤**. dev에만 없는 커밋은 **0개** → **fast-forward 머지 가능** |
| 산출물 | `csm.war` + `mediplat.jar` (**mediplat도 함께 배포된다**) |

### 배포는 즉시 적용되지 않는다 — 2단계 구조

```
prod 브랜치 push
  → [GitHub 러너] 테스트 게이트 + 빌드 + 체크섬
  → [prod 서버 self-hosted 러너] /opt/csm-next/deploy/staging 에 적재 + deploy.ok 생성
  → [systemd 타이머] 02:30 KST 에 실제 적용 + 서비스 재기동
```

**② ALTER 실패 시 문자 발송이 전건 실패**하는 위험이 있으므로, **새벽 자동 적용에 맡기지 말고
staging 적재 확인 후 수동으로 적용해 즉시 검증하는 것을 강력히 권한다.** 2-B 참조.

### 테스트 게이트는 통과한다

게이트는 `./gradlew test -PexcludeIntegration` 이고, 유일한 실패 테스트
`CsmApplicationTests` 는 `@Tag("integration")` 이라 **제외된다.** 로컬 재현 결과 `BUILD SUCCESSFUL`.

### 단계 순서를 하나 바꿨다 — httpd 라우팅을 실발송보다 먼저

요청하신 순서는 `4. 실발송 → 5. httpd` 였으나 **5와 4를 바꿨다.** httpd 전환 전에 실발송을 하면
그 문자의 refkey(`MP-…`)가 레거시로 가서 **6단계 콜백 검증 자체가 불가능**하기 때문이다.
전환을 먼저 하면 실발송 1건으로 발송→접수→콜백 전 구간을 한 번에 검증할 수 있다.

### 서버 정보

| 항목 | 값 |
|---|---|
| 실서버 csm | 포트 **18081**, `catalina.base=/opt/csm-next/tomcat` |
| 개발서버 csm | 포트 8080, `/usr/local/tomcat10` (**이 절차서 대상 아님**) |
| 주의 | 실서버 8084는 ResvHub. csm이 아니다 |

### 배포 시각 — 업무 종료 후(18:00 이후) 권장

**업무시간 중 배포는 피한다.** 사유:

| 사유 | 내용 |
|---|---|
| httpd reload 시 콜백 순단 | 4단계에서 `systemctl reload httpd` 를 하면 순간적으로 콜백 수신이 끊길 수 있다. 발송이 활발한 시간대에 하면 그 구간 결과 리포트가 유실된다 |
| ALTER 실패 시 문자 전면 중단 | 3-1이 실패하면 **문자 발송이 전건 실패**한다. 업무 중이면 곧바로 업무 장애다 |
| 롤백 여유 | 7단계 롤백은 WAR 복원 + Tomcat 재기동 + httpd 원복까지 포함한다. 시간 압박 없이 수행할 여유가 필요하다 |
| 실발송 검증 | 5단계는 실제 문자를 1건 발송한다. 업무 발송과 섞이면 검증 대상 식별이 어려워진다 |

**새벽 배포도 권하지 않는다.** 02:30 타이머에 맡기면 ALTER 실패로 문자가 죽어도
아침까지 아무도 모른다(2-C 참조). **검증할 사람이 깨어 있는 시간에 배포한다.**

### 배포 중단 판정 기준 — 롤백 / 재시도 / 계속 진행

각 단계 실패 시 어떻게 할지 미리 정해둔다. **"중단"은 다음 단계로 넘어가지 않는다는 뜻이다.**

| 단계 | 실패 내용 | 판정 |
|---|---|---|
| 0-1 | refkey 중복 존재 | **배포 중단.** 코드 수정 후 재배포. 데이터 삭제 금지 |
| 0-2 | 사용 기관 발신번호 미등록 | **배포 중단.** 발신번호 등록 후 재개(재시도 가능) |
| 0-3 | 단가 FALLBACK | **계속 진행.** 기록만 남긴다 |
| 1 | 백업 실패 | **배포 중단.** 백업 없이 배포하지 않는다 |
| 2-B | 테스트 게이트 실패 | **배포 중단.** staging 적재 안 됨 |
| 2-C | 타이머 유닛을 못 찾음 | **재시도.** E항 분기 참조 |
| 2-D | 기동 실패(404 / `Started CsmApplication` 없음) | **즉시 롤백(7단계)** |
| **3-1** | **컬럼 5종 중 하나라도 누락** | **즉시 롤백(7단계).** 아래 별도 설명 |
| 3-2 | 인덱스 누락 | **계속 진행.** 보고 후 후속 처리 |
| 3-3 | `sms_batch` 테이블 없음 | **즉시 롤백(7단계)** |
| 3-4 | collation 미통일 / 뷰 없음 | **계속 진행.** 발송에 영향 없음 |
| 4-3 | `httpd -t` 문법 오류 | **재시도.** 백업본 복구 후 재편집 |
| 5 | 발신번호 거부 | **0-2로 복귀(재시도).** 롤백 불필요 |
| 5 | 발송 오류(코드 원인) | **즉시 롤백(7단계)** |
| 5 | `UNKNOWN` 반환 | **중단하고 보고.** 재발송 금지(중복 발송 위험) |
| 6 | 30분 경과 후에도 `SENT` 유지 | **4단계 재점검(재시도).** 롤백은 판단 후 결정 |

> **3-1이 이번 배포의 최대 위험 지점이다.**
> 발송 INSERT가 `cost`·`billable`·`batch_id` 를 직접 참조하는데, 스키마 부트스트랩은
> ALTER 실패를 WARN으로 삼키고 기동을 계속한다. 즉 **기동은 성공했는데 문자만 전건 실패**하는
> 상태가 만들어진다. 컬럼이 하나라도 빠진 채 4·5단계로 넘어가면 원인 파악이 어려워지고,
> 그 사이 사용자가 발송을 시도하면 전부 실패한다.
> **`new_cols = 5` 를 전 기관에서 확인하기 전에는 절대 다음 단계로 가지 않는다.**

---

## 0단계 — 사전 확인 (배포 전, 지금 실행 가능)

**목적**: 배포 후에 발견하면 늦는 것들을 미리 거른다. 특히 0-1은 실패 시 ALTER가 깨져
문자 발송이 전건 중단된다.

### 0-0. 필수 환경변수 대조 — **이번 장애의 직접 원인. 반드시 먼저 한다**

`application.properties` 에 `${ENV_VAR}` 형태로 **기본값 없이** 선언된 값은
서버 env 파일에 없으면 **기동 즉시 크래시**한다.

> **함정**: `@Value("${platform.bootstrap.admin-password:ChangeMe123!}")` 처럼 코드에
> 기본값이 있어도 소용없다. `:기본값` 은 **프로퍼티 키가 아예 없을 때만** 적용된다.
> 키는 존재하는데 그 값이 미해석 `${ENV_VAR}` 이면 그대로
> `PlaceholderResolutionException` 이다. 2026-08-13 mediplat 이 정확히 이걸로 죽었다.

**기본값이 없어 누락 시 크래시하는 변수 (2026-08-14 기준)**

| mediplat (6) | csm (14) |
|---|---|
| `COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET`<br>`LOGIN_AES_KEY`<br>`PLATFORM_ADMIN_PASSWORD`<br>`SPRING_DATASOURCE_PASSWORD`<br>`SPRING_DATASOURCE_URL`<br>`SPRING_DATASOURCE_USERNAME` | `BIZPPURIO_DEV_ACCOUNT` / `_PASSWORD` / `_USERNAME`<br>`BIZPPURIO_PROD_ACCOUNT` / `_PASSWORD` / `_USERNAME`<br>`COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET`<br>`KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET`<br>`LOGIN_AES_KEY`<br>`SPRING_DATASOURCE_PASSWORD` / `_URL` / `_USERNAME`<br>`SPRING_MAIL_PASSWORD` |

**목록은 배포마다 다시 뽑는다.** 새 커밋이 필수 변수를 추가했을 수 있다 (로컬 저장소에서 실행):

```bash
echo "--- mediplat ---"; grep -ohE '\$\{[A-Z][A-Z0-9_]*\}' mediplat/src/main/resources/application*.properties | tr -d '${}' | sort -u
echo "--- csm ---";      grep -ohE '\$\{[A-Z][A-Z0-9_]*\}' src/main/resources/application*.properties     | tr -d '${}' | sort -u
```

**서버에서 대조** (mediplat 예시. csm 은 `/opt/csm-next/env/csm-next.env` 등 해당 파일로 교체):

```bash
for v in COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET LOGIN_AES_KEY PLATFORM_ADMIN_PASSWORD \
         SPRING_DATASOURCE_PASSWORD SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME; do
  printf "%-45s %s\n" "$v" "$(sudo grep -c "^$v=" /opt/csm-next/env/mediplat-next.env)"
done
```

**성공 판정**: 전부 `1`
**실패 시 조치**: **배포 중단.** `0` 인 변수를 env 파일에 추가한 뒤 재개한다.

> **`PLATFORM_ADMIN_PASSWORD` 주의**: 이 값은 단순 인증용이 아니다. mediplat 은 기동할 때마다
> `bootstrapDefaults()` → `upsertUser()` 의 `ON DUPLICATE KEY UPDATE password_hash = ?` 로
> **운영 PLATFORM_ADMIN 계정 비밀번호를 이 값으로 덮어쓴다.** 즉 env 에 넣는 값이 곧 실제
> 관리자 비밀번호다. 임시값·플레이스홀더를 넣지 말 것. 변경은 env 수정 후 재기동이면 된다.

### 0-1. refkey 중복 검증 — 7개 기관 한 번에

`uk_th_refkey` UNIQUE 를 거는 단계가 있다. 중복이 있으면 ALTER가 실패한다.

```sql
SELECT 'core' AS inst, COUNT(*) AS dup FROM (SELECT refkey FROM csm.transmission_history_core WHERE refkey IS NOT NULL GROUP BY refkey HAVING COUNT(*)>1) x
UNION ALL SELECT 'COHS', COUNT(*) FROM (SELECT refkey FROM csm.transmission_history_COHS WHERE refkey IS NOT NULL GROUP BY refkey HAVING COUNT(*)>1) x
UNION ALL SELECT 'FALH', COUNT(*) FROM (SELECT refkey FROM csm.transmission_history_FALH WHERE refkey IS NOT NULL GROUP BY refkey HAVING COUNT(*)>1) x
UNION ALL SELECT 'HSJH', COUNT(*) FROM (SELECT refkey FROM csm.transmission_history_HSJH WHERE refkey IS NOT NULL GROUP BY refkey HAVING COUNT(*)>1) x
UNION ALL SELECT 'HSFH', COUNT(*) FROM (SELECT refkey FROM csm.transmission_history_HSFH WHERE refkey IS NOT NULL GROUP BY refkey HAVING COUNT(*)>1) x
UNION ALL SELECT 'DCHS', COUNT(*) FROM (SELECT refkey FROM csm.transmission_history_DCHS WHERE refkey IS NOT NULL GROUP BY refkey HAVING COUNT(*)>1) x
UNION ALL SELECT 'SLAH', COUNT(*) FROM (SELECT refkey FROM csm.transmission_history_SLAH WHERE refkey IS NOT NULL GROUP BY refkey HAVING COUNT(*)>1) x;
```

> 문자열 리터럴과 숫자만 UNION 하므로 테이블 collation 불일치의 영향을 받지 않는다.

**성공 판정**: 7행 전부 `dup = 0`

**실패 시 조치**: 중복이 있는 기관을 보고할 것. **데이터를 임의로 삭제하지 말 것.**
해당 기관만 UNIQUE 대신 일반 인덱스로 시작하도록 코드를 수정한 뒤 재배포한다.

### 0-2. 발신번호 등록 상태 — 7개 기관 한 번에

배치 API는 **등록된 발신번호만 허용**한다(신규 제약). 비어 있는 기관은 문자를 전혀 못 보낸다.

```sql
SELECT 'core' AS inst, COUNT(*) AS cnt, GROUP_CONCAT(phone_num COLLATE utf8mb4_0900_ai_ci) AS nums FROM csm.phone_number_core
UNION ALL SELECT 'COHS', COUNT(*), GROUP_CONCAT(phone_num COLLATE utf8mb4_0900_ai_ci) FROM csm.phone_number_COHS
UNION ALL SELECT 'FALH', COUNT(*), GROUP_CONCAT(phone_num COLLATE utf8mb4_0900_ai_ci) FROM csm.phone_number_FALH
UNION ALL SELECT 'HSJH', COUNT(*), GROUP_CONCAT(phone_num COLLATE utf8mb4_0900_ai_ci) FROM csm.phone_number_HSJH
UNION ALL SELECT 'HSFH', COUNT(*), GROUP_CONCAT(phone_num COLLATE utf8mb4_0900_ai_ci) FROM csm.phone_number_HSFH
UNION ALL SELECT 'DCHS', COUNT(*), GROUP_CONCAT(phone_num COLLATE utf8mb4_0900_ai_ci) FROM csm.phone_number_DCHS
UNION ALL SELECT 'SLAH', COUNT(*), GROUP_CONCAT(phone_num COLLATE utf8mb4_0900_ai_ci) FROM csm.phone_number_SLAH;
```

**성공 판정**: 실제 문자를 사용하는 기관은 `cnt >= 1`.
그리고 `nums` 값이 **숫자와 하이픈으로만** 구성되어야 한다(서버가 숫자만 남겨 비교하므로
괄호·점 등 다른 문자가 섞이면 매칭 실패 가능).

**실패 시 조치**: `cnt = 0` 인 기관이 문자를 쓰지 않는 곳이면 무시해도 된다. 사용하는 기관이면
**배포 전에** 발신번호를 등록할 것. 등록하지 않고 배포하면 그 기관은 문자 발송이 불가하다.

### 0-3. 단가 설정 상태

```sql
SELECT id_col_03 AS inst, sms_price, lms_price, mms_price,
       CASE WHEN sms_price IS NULL OR sms_price = '' OR sms_price NOT REGEXP '^[0-9]+(\\.[0-9]+)?$'
            THEN 'FALLBACK' ELSE 'OK' END AS sms_status
FROM csm.inst_data_cs
ORDER BY id_col_03;
```

**성공 판정**: 이 단계는 **배포 차단 사유가 아니다.** `FALLBACK` 이어도 발송은 정상 동작하며
기본값 960전(=9.6원)이 적용된다. 실서버 단가가 9.6원이라 값도 일치한다.

**조치**: `FALLBACK` 기관은 기록해 둘 것. **Phase 4(과금) 착수 전에는 반드시 채워야 한다.**
입력은 `/csm/core/smssetting` (플랫폼 관리자 전용).

---

## 1단계 — DB 백업

**목적**: 스키마 변경(컬럼 5종 + 인덱스 + collation 변환)이 들어간다. 되돌릴 수단을 먼저 확보한다.

```bash
sudo mkdir -p /opt/csm-next/backup && cd /opt/csm-next/backup
TS=$(date +%Y%m%d-%H%M%S)
mysqldump -u <DB_USER> -p --single-transaction --routines --triggers csm > csm-${TS}.sql
ls -lh csm-${TS}.sql
```

**성공 판정**: 파일이 생성되고 **크기가 0이 아니다.** 아래로 무결성도 확인한다.

```bash
tail -3 csm-*.sql | grep -c "Dump completed"
```

**성공 판정**: 출력이 `1`

**실패 시 조치**: 백업이 실패하면 **여기서 중단한다.** 배포하지 말 것.

---

## 2단계 — WAR 배포 + 기동

### 2-A. prod 브랜치에 반영 (로컬에서 실행)

```bash
git fetch origin && git checkout prod && git merge --ff-only origin/dev
```

**성공 판정**: `Fast-forward` 메시지 출력. `Not possible to fast-forward` 가 나오면 중단하고 보고할 것.

```bash
git push origin prod
```

**성공 판정**: push 성공. 이후 GitHub Actions `Deploy PROD` 워크플로가 시작된다.

### 2-B. 워크플로 결과 확인 + staging 적재 확인

GitHub → Actions → `Deploy PROD` 실행 완료를 기다린다.

**성공 판정**: 워크플로 전체 초록. 특히 `Test gate` 와 `Stage artifacts` 스텝이 성공.

**실패 시 조치**: `Test gate` 실패면 배포 중단. 테스트 로그를 보내주면 확인한다.

서버에서 적재를 확인한다.

```bash
ls -l /opt/csm-next/deploy/staging/
```

**성공 판정**: `csm.war`, `mediplat.jar`, `deploy.ok` 3개가 있고 **타임스탬프가 방금 시각**이다.

### 2-C. 수동 적용 (권장 — 새벽 타이머에 맡기지 말 것)

> 자동 적용은 02:30 KST 이다. 그 시각에 적용되면 **ALTER 실패로 문자가 전건 중단되어도
> 아침까지 아무도 모른다.** 지금 수동 적용해 즉시 검증한다.

먼저 타이머 유닛 이름을 확인한다(저장소 스크립트와 서버 설정이 다를 수 있다).

```bash
systemctl list-timers --all | grep -i deploy
```

**기대 출력**: `csm-next-deploy.timer` 또는 `nightly-deploy.timer` 중 하나가 보인다.

확인된 유닛의 service 를 즉시 실행한다(아래 `<UNIT>` 을 위에서 확인한 이름으로 교체).

```bash
sudo systemctl start <UNIT>.service && sleep 5 && sudo journalctl -u <UNIT>.service -n 40 --no-pager
```

**성공 판정**: 로그에 `deploying csm.war -> ...` / `installed` / `marker consumed` 가 있고
`ERROR` 가 없다.

#### 유닛을 못 찾거나 경로가 다를 때의 분기 (E항)

저장소 스크립트의 기본 경로와 워크플로가 쓰는 경로가 다르다. **서버 실제 설정이 기준이다.**

| 위치 | 값 |
|---|---|
| 워크플로 적재 경로 | `/opt/csm-next/deploy/staging` |
| `scripts/deploy-nightly.sh` 기본값 | `STAGING_DIR=/opt/deploy/staging`, `TOMCAT_WEBAPPS=/usr/local/tomcat10/webapps` (**개발서버 경로**) |

서버에 설치된 유닛이 override 파일로 경로를 재정의하고 있을 수 있다. 아래로 실제 값을 확인한다.

```bash
systemctl cat <UNIT>.service | grep -E "ExecStart|Environment|EnvironmentFile"
cat /etc/default/nightly-deploy 2>/dev/null; cat /etc/sysconfig/nightly-deploy 2>/dev/null
```

**분기 판정**

| 확인 결과 | 조치 |
|---|---|
| 유닛이 있고 `STAGING_DIR` 이 `/opt/csm-next/deploy/staging`, `TOMCAT_WEBAPPS` 가 `/opt/csm-next/tomcat/webapps` | 그대로 `systemctl start` 실행 |
| 유닛은 있으나 **경로가 개발서버 기본값**(`/opt/deploy/staging`, `/usr/local/tomcat10/webapps`) | **실행하지 말 것.** 그대로 돌리면 워크플로가 적재한 산출물을 못 찾거나 엉뚱한 경로에 배포한다. 환경변수를 덮어 1회 실행: <br>`sudo STAGING_DIR=/opt/csm-next/deploy/staging TOMCAT_WEBAPPS=/opt/csm-next/tomcat/webapps CSM_SERVICE=csm-next /path/to/deploy-nightly.sh` |
| 유닛이 없음 | 수동 반영: 아래 참조 |

유닛이 없을 때의 수동 반영(백업 포함):

```bash
TS=$(date +%Y%m%d-%H%M%S)
sudo cp -a /opt/csm-next/tomcat/webapps/csm.war /opt/csm-next/tomcat/webapps/csm.war.bak-${TS}
sudo cp -a /opt/csm-next/deploy/staging/csm.war /opt/csm-next/tomcat/webapps/csm.war.new-${TS}
sudo mv -f /opt/csm-next/tomcat/webapps/csm.war.new-${TS} /opt/csm-next/tomcat/webapps/csm.war
sudo systemctl restart csm-next
```

**어느 분기를 타든 3단계 검증은 반드시 수행한다.**

> 수동 반영 분기를 탔다면 `/opt/csm-next/deploy/staging/deploy.ok` 가 그대로 남는다.
> 아래 2-D 이후 "타이머 처리"를 반드시 읽을 것.

### 2-C-2. 02:30 타이머가 다시 도는가 — 확인 결과

[scripts/deploy-nightly.sh](../scripts/deploy-nightly.sh) 를 확인한 결과다.

| 질문 | 답 |
|---|---|
| 이미 적용된 staging 을 다시 적용하는가 | **아니다.** 스크립트는 `deploy.ok` 마커가 없으면 `no marker; skipping` 으로 즉시 종료한다(exit 0) |
| 마커는 언제 사라지는가 | 배포 성공 시 스크립트가 마커를 **archive 로 이동**해 소비한다(`marker consumed`). 산출물(`csm.war`)도 함께 archive 로 이동한다 |
| 재적용 부작용 | 정상 종료했다면 **재기동·ALTER 재실행 모두 일어나지 않는다.** 설령 마커가 남아 재실행되어도 산출물이 이미 archive 로 옮겨져 `marker present but no known artifacts` 로 끝나 서비스 재기동이 없다. ALTER 자체도 `INFORMATION_SCHEMA` 존재 확인 후 실행이라 재실행되어도 no-op 이다 |

**따라서 정상 수동 실행 후에는 타이머를 별도로 손댈 필요가 없다.** 다만 아래를 확인한다.

```bash
ls -l /opt/csm-next/deploy/staging/
```

**성공 판정**: `deploy.ok` 와 `csm.war` 이 **없다**(archive 로 소비됨).

**마커가 남아 있다면** — 수동 반영 분기를 탔거나 스크립트가 중간에 실패한 경우다. 둘 중 하나를 택한다.

```bash
# (권장) 이번 배포는 이미 검증했으므로 마커를 치워 새벽 재실행을 막는다
sudo mkdir -p /opt/csm-next/deploy/archive/manual-$(date +%Y%m%d-%H%M%S)
sudo mv /opt/csm-next/deploy/staging/deploy.ok /opt/csm-next/deploy/archive/manual-$(date +%Y%m%d-%H%M%S)/
```

마커를 남겨두면 02:30 에 타이머가 같은 산출물을 다시 적용하고 **csm-next 를 재기동한다.**
검증이 끝난 상태에서의 불필요한 새벽 재기동이므로 권하지 않는다.

### 2-D. 기동 확인

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:18081/csm/login
```

**성공 판정**: `200` 또는 `302`. `404` 면 컨텍스트가 올라오지 않은 것이다.

> **로그 위치 주의 (2026-08-14 실측)**: `catalina.out` 은 2026-05-08 이후 갱신되지 않는 **낡은 파일**이다.
> 현재 csm-next 는 **journald** 로 로그를 남긴다. 아래를 쓸 것.

```bash
sudo journalctl -u csm-next --since "10 min ago" --no-pager | grep -E "Started ServletInitializer|ERROR|Exception" | tail -20
```

**성공 판정**: `Started ServletInitializer` 가 보이고, 그 이후 구간에 치명적 `Exception` 이 없다.

**실패 시 조치**: **7단계 롤백**으로 이동.

### 2-E. mediplat 배포 확인 — **csm 만 보고 끝내지 말 것**

배포 파이프라인은 `csm.war` 과 **`mediplat.jar` 을 함께** 적용한다. 2026-08-13 장애는
csm 이 아니라 **mediplat 이 죽어서** 발생했고, csm 만 확인했다면 놓쳤을 상황이다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:18082/
```

**성공 판정**: `200` 또는 `302`
**실패 판정 (`000`)**: 아래로 상태를 본다.

```bash
sudo systemctl status mediplat-next --no-pager | head -12
```

> **`Restart=always` 가 증상을 가린다.** 크래시 루프 중에는 상태가 `failed` 가 아니라
> **`activating (auto-restart)`** 로 보인다. `active` 가 아니면 정상이 아니다.
> `restart counter is at N` 의 N 이 크면 오래 반복된 것이다(주기 약 8초).

**mediplat 로그는 journald 에 없다** — 유닛이 파일로 리다이렉트한다
(`StandardOutput=append:/opt/csm-next/logs/mediplat.log`). `journalctl` 에는 systemd 메시지만
보이므로 예외를 찾으려면 반드시 파일을 봐야 한다.

```bash
sudo grep -n "APPLICATION FAILED TO START" /opt/csm-next/logs/mediplat.log | tail -1
# 나온 행 번호를 <N> 에 넣어
sudo sed -n '<N>,+45p' /opt/csm-next/logs/mediplat.log
# 위가 비면
sudo grep -n "Caused by:" /opt/csm-next/logs/mediplat.log | tail -10
```

**실패 시 조치**: 7단계의 **mediplat 롤백**으로 이동.

---

## 3단계 — 스키마 반영 검증 (기동 로그가 아니라 DB로 확인)

> **이 단계가 이번 배포에서 가장 중요하다.** 스키마 부트스트랩은 ALTER 실패 시 WARN만 남기고
> 기동을 계속한다. 즉 **앱은 정상으로 보이는데 문자만 전건 실패**하는 상태가 가능하다.
> 발송 INSERT가 `cost`·`billable`·`batch_id` 컬럼을 직접 참조하기 때문이다.

### 3-1. 신규 컬럼 5종 (7개 기관 전부)

```sql
SELECT table_name,
       SUM(column_name IN ('cost','billable','message_key','vendor_code','batch_id')) AS new_cols
FROM information_schema.columns
WHERE table_schema = 'csm' AND table_name LIKE 'transmission_history_%'
GROUP BY table_name
ORDER BY table_name;
```

**성공 판정**: 기관 수만큼의 행이 나오고 **전부 `new_cols = 5`**

**실패 시 조치**: 5 미만인 기관이 있으면 **문자 발송 불가 상태다.** 즉시 7단계 롤백.
`catalina.out` 에서 `grep "schema-bootstrap.*column migration skipped"` 로 원인(권한·잠금 등)을 확인해 보고할 것.

### 3-2. 인덱스 (refkey UNIQUE + batch_id)

```sql
SELECT table_name,
       SUM(index_name = 'uk_th_refkey') AS uk_refkey,
       SUM(index_name = 'ix_th_batch_id') AS ix_batch
FROM information_schema.statistics
WHERE table_schema = 'csm' AND table_name LIKE 'transmission_history_%'
GROUP BY table_name
ORDER BY table_name;
```

**성공 판정**: 전 기관 `uk_refkey >= 1`, `ix_batch >= 1`

**실패 시 조치**: `uk_refkey = 0` 이면 0-1의 중복 검증이 실패한 것이다. **발송은 가능하므로 즉시
롤백할 필요는 없으나**, 중복 발송 차단이 걸리지 않은 상태이니 원인을 확인하고 보고할 것.

### 3-3. sms_batch 테이블

```sql
SELECT COUNT(*) AS sms_batch_exists FROM information_schema.tables
WHERE table_schema = 'csm' AND table_name = 'sms_batch';
```

**성공 판정**: `1`

**실패 시 조치**: `0` 이면 배치 발송이 전부 실패한다. 즉시 7단계 롤백.

### 3-4. collation 통일 + 집계 뷰

```sql
SELECT table_collation, COUNT(*) AS cnt
FROM information_schema.tables
WHERE table_schema = 'csm' AND table_name LIKE 'transmission_history_%'
GROUP BY table_collation;

SELECT COUNT(*) AS view_exists FROM information_schema.views
WHERE table_schema = 'csm' AND table_name = 'v_transmission_history_all';
```

**성공 판정**: collation이 `utf8mb4_0900_ai_ci` **한 종류**, 뷰 `view_exists = 1`

**실패 시 조치**: 이 항목은 **배포 차단 사유가 아니다.** 뷰는 집계 조회용이라 발송에 영향이 없다.
`catalina.out` 에서 `grep "v_transmission_history_all"` 로 원인을 확인해 보고할 것.

---

## 4단계 — httpd 콜백 라우팅 적용 (실발송보다 먼저)

**목적**: 신규 refkey(`MP-…`) 콜백이 레거시가 아니라 csm-next 로 오게 한다.
이걸 먼저 해야 5단계 실발송 1건으로 콜백까지 한 번에 검증할 수 있다.

> 보안상 새로 열리는 것은 없다. `/csm/api/external/SMSRequest` 는 이미 18081로 라우팅되는
> 무인증 공개 경로다. 노출 표면은 동일하다.

### 4-1. 현재 설정 백업 + 위치 확인

```bash
sudo cp -a /etc/httpd/conf/httpd.conf /etc/httpd/conf/httpd.conf.bak-$(date +%Y%m%d-%H%M%S)
sudo grep -n "api/external" /etc/httpd/conf/httpd.conf /etc/httpd/conf.d/*.conf /opt/csm-next/httpd/*.conf 2>/dev/null
```

**성공 판정**: 백업 파일이 생성되고, `/api/external/*` → 8009 규칙의 **파일명과 행 번호**를 확인했다.

**2026-08-14 실측값** — 규칙은 **`/etc/httpd/conf/httpd.conf` 한 곳뿐**이다.

| 항목 | 값 |
|---|---|
| 기존 규칙 위치 | `httpd.conf:415-416` (예외 2줄 삽입 후 기준) |
| 기존 규칙 내용 | `ProxyPass /api/external/ ajp://localhost:8009/api/external/` |
| 소속 VirtualHost | **`<VirtualHost *:443>`** (379~430행) |
| `<VirtualHost *:80>` | 359~375행. `RewriteRule ^ https://... [R=permanent]` 로 **301 리다이렉트만** 한다 |

### 4-2. 예외 라우팅 2줄 추가

확인한 `/api/external/*` → 8009 규칙 **바로 위**에 아래 2줄을 넣는다.
httpd는 **먼저 매칭된 ProxyPass가 이기므로 순서가 중요하다.**

```apache
ProxyPass        /api/external/SMSRequest  http://127.0.0.1:18081/csm/api/external/SMSRequest
ProxyPassReverse /api/external/SMSRequest  http://127.0.0.1:18081/csm/api/external/SMSRequest
```

`sed` 로 넣는다면 (413행 앞 삽입 예시):

```bash
sudo sed -i '413i ProxyPass        /api/external/SMSRequest  http://127.0.0.1:18081/csm/api/external/SMSRequest\nProxyPassReverse /api/external/SMSRequest  http://127.0.0.1:18081/csm/api/external/SMSRequest' /etc/httpd/conf/httpd.conf
sudo sed -n '408,420p' /etc/httpd/conf/httpd.conf
```

**성공 판정**: 새 `SMSRequest` 2줄이 기존 `/api/external/` 2줄보다 **위**에 있다. 순서가 반대면 효과가 없다.

### 4-3. 문법 검사 후 reload

```bash
sudo httpd -t
```

**성공 판정**: `Syntax OK`

**실패 시 조치**: 백업본으로 복구(`sudo cp -a /etc/httpd/conf/httpd.conf.bak-<TS> /etc/httpd/conf/httpd.conf`) 후 다시 시도.

```bash
sudo systemctl reload httpd && sleep 2 && systemctl is-active httpd
```

**성공 판정**: `active`

### 4-4. 라우팅 전환 검증 — 데이터를 건드리지 않는 방법

빈 refkey 로 콜백을 한 번 쏘면 핸들러가 **DB 를 전혀 건드리지 않고** 로그만 남기고 200 을 반환한다.
라우팅만 확인하는 용도로 안전하다.

> **HTTP(80) 으로 쏘면 안 된다.** 80 vhost 는 301 리다이렉트만 하므로 프록시 규칙에 닿지 않는다
> (2026-08-14 실제로 `301` 이 나와 검증이 성립하지 않았다). **반드시 HTTPS 로 쏜다.**

```bash
curl -sk -o /dev/null -w "%{http_code}\n" -X POST \
  --resolve csm.sosyge.net:443:127.0.0.1 \
  -H "Content-Type: application/json" -d '{}' \
  https://csm.sosyge.net/api/external/SMSRequest
```

```bash
sudo journalctl -u csm-next --since "2 min ago" --no-pager | grep "SMSRequest"
```

**성공 판정**: HTTP `200` + 로그에 `callback ignored: empty refkey, payloadKeys=[]`
→ **csm-next 가 받고 있다. 전환 완료.**

**실패 판정**: 로그가 없다 → 아직 레거시(AJP 8009)로 간다. 4-2 의 순서를 재확인한다.

> `--resolve` 로 도메인을 로컬 IP 에 고정해 외부 DNS 를 거치지 않고 httpd 라우팅만 시험한다.

---

## 5단계 — 실발송 테스트 (기관 1곳, 1건)

**목적**: 발송 → 이력 기록 → 접수 → 콜백까지 실제로 도는지 확인한다.

브라우저에서 실서버 csm에 로그인 → 상담리스트 또는 입원상담 화면 → **본인 번호로 문자 1건 발송**.

**성공 판정 (화면)**: "전송 완료" 알림이 뜨고 **실제로 문자가 수신된다.**

**성공 판정 (DB)** — `<inst>` 를 테스트한 기관으로 교체:

```sql
SELECT id, refkey, status, send_type, cost, billable, batch_id, message_key, vendor_code, created_at
FROM csm.transmission_history_<inst>
ORDER BY id DESC LIMIT 3;
```

아래를 **전부** 만족해야 한다.

| 컬럼 | 기대값 |
|---|---|
| `refkey` | `MP-<inst>-<id>` 형식 (예: `MP-COHS-12345`) |
| `status` | `SENT` |
| `send_type` | `sms` 또는 `lms` |
| `cost` | 960 (또는 기관 단가 × 100) |
| `billable` | `Y` |
| `batch_id` | UUID 문자열 (NULL 아님) |
| `vendor_code` | `1000` |
| `message_key` | 비어 있지 않음 |

```sql
SELECT batch_id, inst_code, send_type, total_count, success_count, failed_count, unknown_count,
       unit_cost, total_cost, created_by, created_at
FROM csm.sms_batch ORDER BY created_at DESC LIMIT 3;
```

**성공 판정**: 방금 발송한 배치 1행이 있고 `success_count = 1`, `total_cost = unit_cost`

**실패 시 조치**:
- 화면에 "기관에 등록되지 않은 발신번호입니다" → **0-2 로 돌아가** 발신번호를 등록한다.
- 화면에 "문자 발송 처리 중 오류" → `catalina.out` 에서 `grep "api/counsel/sms/batch"` 확인.
  컬럼 누락이 원인이면 **3-1 로 돌아간다.**
- 발송은 됐는데 `status` 가 `FAILED` → 비즈뿌리오 응답 문제. `response` 컬럼 내용을 보고할 것.
- `UNKNOWN` → 타임아웃. **재발송하지 말 것**(중복 발송 위험). 보고 후 대기.

---

## 6단계 — 콜백 갱신 확인

**목적**: 4단계 라우팅 전환이 실제로 동작하는지, 신규 refkey 파서가 붙는지 확인한다.

비즈뿌리오 결과 리포트는 발송 후 수 초~수 분 내 도착한다. **3~5분 기다린 뒤** 확인한다.

```sql
SELECT id, refkey, status FROM csm.transmission_history_<inst> ORDER BY id DESC LIMIT 3;
```

**성공 판정**: 5단계에서 발송한 건의 `status` 가 `SENT` → **`DONE`** 으로 바뀌었다.

```sql
SELECT refkey, result, phone, insert_date FROM csm.sms_request_<inst> ORDER BY id DESC LIMIT 3;
```

**성공 판정**: 방금 refkey로 1행이 기록되어 있고 `result` 가 `4100` 또는 `6600`

```bash
sudo grep "api/external/SMSRequest" /opt/csm-next/tomcat/logs/catalina.out | tail -10
```

**성공 판정**: `callback received inst=<inst>, refkey=MP-…, status=DONE` 로그가 있다.

**실패 시 조치**:
- `status` 가 `SENT` 그대로이고 로그도 없음 → **4단계 라우팅이 적용되지 않았다.**
  `sudo grep -h "SMSRequest" /var/log/httpd/access_log | tail -5` 로 요청이 어디로 갔는지 확인 후 4-2 재점검.
- 로그에 `legacy refkey fallback` 만 보임 → 구형식 콜백이며 정상이다(배포 전 발송 건).
- 로그에 `no history row updated` → refkey 파싱 문제. 로그를 보고할 것.

> 콜백이 5분 내에 오지 않아도 즉시 실패로 단정하지 말 것. 벤더 지연 가능성이 있다.
> 30분 이상 `SENT` 에 머물면 실패로 판정한다.

---

## 7단계 — 롤백 절차

### 언제 롤백하는가

| 상황 | 조치 |
|---|---|
| 2-D 기동 실패 (`Started ServletInitializer` 없음 / 404) | **즉시 csm 롤백** |
| **2-E mediplat `000` / 크래시 루프** | **즉시 mediplat 롤백** (csm 은 건드리지 않는다) |
| 3-1 컬럼 5종 미달 | **즉시 csm 롤백** (문자 발송 불가 상태) |
| 3-3 `sms_batch` 없음 | **즉시 csm 롤백** |
| 5단계 발송 실패 (원인이 코드) | **즉시 csm 롤백** |
| 3-2 인덱스 누락 / 3-4 뷰 누락 | 롤백 불필요. 보고 후 후속 처리 |
| 0-3 단가 FALLBACK | 롤백 불필요 |

> **csm 과 mediplat 은 독립적으로 롤백한다.** 같은 파이프라인으로 함께 배포되지만 별개 프로세스다.
> 2026-08-13 장애는 mediplat 만 죽고 csm 은 멀쩡했다. **한쪽 문제로 양쪽을 되돌리지 말 것.**

### 롤백 실행 — mediplat

```bash
ls -lt /opt/csm-next/app/mediplat.jar.bak-* | head -3
```

> **백업 파일명의 `<TS>` 는 "백업이 만들어진 시각"이고, 파일의 mtime 은 "그 jar 이 빌드된 시각"이다**
> (`cp -a` 가 mtime 을 보존한다). 되돌릴 대상은 **가장 최근에 만들어진 `bak-` 파일**(이름 기준)이다.
> mtime 으로 고르면 엉뚱한 버전을 집는다.

```bash
# 원인 분석용으로 현재(문제) jar 을 먼저 보관
sudo cp -a /opt/csm-next/app/mediplat.jar /opt/csm-next/app/mediplat.jar.broken-$(date +%Y%m%d-%H%M%S)
sudo systemctl stop mediplat-next && sudo systemctl reset-failed mediplat-next
sudo cp -a /opt/csm-next/app/mediplat.jar.bak-<TS> /opt/csm-next/app/mediplat.jar
sudo systemctl start mediplat-next && sleep 30
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:18082/
```

**성공 판정**: `200` 또는 `302`

> `reset-failed` 를 함께 하는 이유: 크래시 루프로 재시작 카운터가 누적되면 systemd 가
> 시작을 거부할 수 있다.

**구버전으로 되돌린 뒤 확인할 것**: 되돌린 빌드에 `95563da`(부트스트랩이 관리자 수정 서비스를
덮어쓰지 않도록 수정) **이전** 코드가 들어 있으면, 재기동할 때마다 포털 서비스 설정이
초기값으로 덮어써진다. 롤백 후 포털 화면에서 서비스 설정을 확인할 것.

### 롤백 실행 — csm

**① WAR 되돌리기**

```bash
ls -lt /opt/csm-next/tomcat/webapps/csm.war.bak-* 2>/dev/null | head -3
```

**성공 판정**: 백업 WAR이 보인다. 가장 최근 것을 복원한다(아래 `<TS>` 교체).

```bash
sudo systemctl stop csm-next
sudo cp -a /opt/csm-next/tomcat/webapps/csm.war.bak-<TS> /opt/csm-next/tomcat/webapps/csm.war
sudo rm -rf /opt/csm-next/tomcat/webapps/csm
sudo systemctl start csm-next && sleep 20
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:18081/csm/login
```

**성공 판정**: `200` 또는 `302`

**② httpd 2줄 제거 — 반드시 함께 한다**

> 4단계를 적용했다면 **롤백 시 반드시 되돌려야 한다.** 구버전은 `MP-` 형식 refkey를 만들지
> 않으므로 콜백은 레거시 파서(`substring(0,4)`)가 맞다. 2줄을 남겨두면 구버전이 만든
> 콜백을 csm-next 구버전이 받게 되어 동작은 하지만, 원래 구성과 달라져 혼선이 생긴다.

```bash
sudo cp -a /etc/httpd/conf/httpd.conf.bak-<TS> /etc/httpd/conf/httpd.conf
sudo httpd -t && sudo systemctl reload httpd
```

**성공 판정**: `Syntax OK` 후 `systemctl is-active httpd` 가 `active`

**③ DB 스키마는 되돌리지 않는다**

추가된 컬럼·인덱스·`sms_batch` 테이블·뷰는 **그대로 둔다.**

- 구버전 코드는 이 컬럼들을 참조하지 않으므로 **있어도 아무 영향이 없다.**
- 되돌리면 재배포 시 ALTER를 다시 해야 하고, 실패 위험을 한 번 더 감수하게 된다.
- 1단계 백업은 **데이터 손상 시에만** 사용한다. 스키마 되돌리기 용도가 아니다.

**④ prod 브랜치 되돌리기** (재배포 방지)

```bash
git checkout prod && git reset --hard 0903572 && git push --force-with-lease origin prod
```

> `0903572` 는 이번 배포 직전의 prod HEAD 다. 되돌린 뒤에는 다음 02:30 타이머가
> 구버전을 다시 staging 하지 않도록 `/opt/csm-next/deploy/staging/deploy.ok` 유무도 확인할 것.

---

## 배포 후 관찰

### 배포 당일 (배포 직후 ~ 취침 전)

#### D-1. 배치 API 오류 로그

```bash
sudo grep -E '\[api/counsel/sms/batch\]|\[sms-batch\]' /opt/csm-next/tomcat/logs/catalina.out | tail -30
```

**정상 판정**: `type mismatch` 외의 ERROR 가 없다.
**이상 판정**: `history insert fail` → 컬럼 누락. **3-1로 복귀.**
`send fail` 다수 → 게이트웨이 문제. `response` 컬럼 확인 후 보고.

#### D-2. 발신번호 거부 발생 여부 (신규 제약이라 당일이 가장 위험)

```bash
sudo grep -c '등록되지 않은 발신번호' /opt/csm-next/tomcat/logs/catalina.out
```

**정상 판정**: `0`
**이상 판정**: 1 이상이면 해당 기관이 문자를 못 보내고 있다. **0-2로 복귀**해 발신번호를 등록한다.

#### D-3. 실패·결과불명 건

```sql
SELECT inst_code, status, COUNT(*) AS cnt
FROM csm.v_transmission_history_all
WHERE created_at >= CURDATE()
GROUP BY inst_code, status
ORDER BY inst_code, status;
```

**정상 판정**: `SENT` 또는 `DONE` 이 대부분.
**이상 판정**: `FAILED` 가 절반 이상이거나 `READY` 가 남아 있으면 조사 대상.
`UNKNOWN` 은 재발송하지 말 것.

### 다음 영업일 오전 (필수)

#### D-4. 발송 건수가 평소 수준인가 — **급감 = 발신번호 검증에 걸린 것**

```sql
SELECT DATE(created_at) AS d, inst_code, COUNT(*) AS cnt
FROM csm.v_transmission_history_all
WHERE created_at >= CURDATE() - INTERVAL 14 DAY
GROUP BY DATE(created_at), inst_code
ORDER BY d DESC, inst_code;
```

**정상 판정**: 배포 다음 날 건수가 **배포 이전 같은 요일 대비 크게 다르지 않다**
(일 40~50건 규모이므로 요일별 편차 감안).
**이상 판정**: 특정 기관만 0건이거나 급감 → 그 기관의 발신번호 등록을 **0-2 쿼리로 확인**한다.
발송 자체가 막힌 것이므로 **사용자는 조용히 못 보내고 있을 수 있다.** 우선순위 높음.

#### D-5. `SENT` 에 머문 채 `DONE` 으로 안 바뀌는 건이 쌓이는가

```sql
SELECT inst_code, COUNT(*) AS stuck_sent, MIN(created_at) AS oldest
FROM csm.v_transmission_history_all
WHERE status = 'SENT' AND created_at < NOW() - INTERVAL 2 HOUR
GROUP BY inst_code;
```

**정상 판정**: 0건이거나 소수(벤더 지연).
**이상 판정**: 배포 이후 건이 계속 쌓이면 **4단계 httpd 라우팅이 동작하지 않는 것**이다.
아래로 콜백이 어디로 갔는지 확인한다.

```bash
sudo grep -h "SMSRequest" /var/log/httpd/access_log | tail -20
sudo grep "api/external/SMSRequest" /opt/csm-next/tomcat/logs/catalina.out | tail -20
```

httpd access log 에는 요청이 있는데 catalina.out 에 없으면 → 레거시로 가고 있다. **4-2 재점검.**

#### D-6. 단가 폴백 WARN

```bash
sudo grep '\[sms-price\]' /opt/csm-next/tomcat/logs/catalina.out | tail -20
sudo grep -ho '\[sms-price\] inst=[A-Za-z0-9_]* type=[a-z]*' /opt/csm-next/tomcat/logs/catalina.out | sort | uniq -c | sort -rn
```

**정상 판정**: 0건, 또는 0-3에서 이미 파악한 기관만 나온다.
**이상 판정**: 예상 밖 기관이 나오면 단가 데이터를 확인한다.
**배포 차단 사유는 아니지만 Phase 4 전에는 반드시 해소해야 한다.**

#### D-7. READY 24시간 잔재 (크래시 윈도우)

```sql
SELECT inst_code, id, to_phone, created_at
FROM csm.v_transmission_history_all
WHERE status = 'READY' AND created_at < NOW() - INTERVAL 24 HOUR
ORDER BY created_at;
```

**정상 판정**: 0건.
**이상 판정**: 행이 있으면 이력 INSERT 후 발송 전에 앱이 죽은 것이다. 복구 로직은 의도적으로
없으므로, 벤더 콘솔에서 실제 발송 여부를 확인해 수동 정리한다.

### 1주간 (제거 판단용)

```bash
# 레거시 발송 경로 호출 — 화면 이관이 끝났으므로 0건이어야 정상
sudo grep -c '\[api/external/sendSMS\]\[deprecated\]' /opt/csm-next/tomcat/logs/catalina.out

# 구형식 refkey 콜백 — 시간이 지나며 0으로 수렴해야 한다
sudo grep -c 'legacy refkey fallback' /opt/csm-next/tomcat/logs/catalina.out

# 클라이언트/서버 메시지 타입 불일치 (관측용, 즉시 조치 대상 아님)
sudo grep '\[sms-batch\] type mismatch' /opt/csm-next/tomcat/logs/catalina.out | tail -10
```

2주간 각각 0건이면 `relaySendSms` 와 콜백 구형식 폴백을 제거할 수 있다.
상세는 [sms-batch-ops.md](sms-batch-ops.md) "제거 판단용 로그 검색 키" 참조.

## 장애 기록 — 2026-08-13 mediplat 12.5시간 중단

**이 절차서를 만들어 두고 지키지 않아서 난 장애다.** 다음 배포 전에 반드시 읽을 것.

### 타임라인

| 시각 | 사건 |
|---|---|
| 08-11 16:13 | `b11efa2` — 저장소의 평문 시크릿 기본값 제거. `${PLATFORM_ADMIN_PASSWORD:<평문기본값>}` → `${PLATFORM_ADMIN_PASSWORD}` |
| 08-13 18:47 | 해당 코드가 포함된 `csm.war` / `mediplat.jar` 빌드 |
| **08-13 20:34** | **절차서를 거치지 않은 배포.** 두 산출물이 함께 설치됨 |
| 08-13 20:34~ | mediplat 즉시 크래시 루프 (약 8초 주기, 누적 5300회+). csm 은 정상 |
| 08-14 08:5x | 사용자가 루트 URL 503 을 발견해 신고 |
| 08-14 09:0x | `bak-20260813-203439` 로 롤백 → 복구 |
| 08-14 09:23 | 콜백 라우팅 전환 + 검증 완료 |

**중단 시간 약 12.5시간.** 영향: 포털(`/`)·SSO. csm(`/csm`)은 전 구간 정상.

### 원인

```
Could not resolve placeholder 'PLATFORM_ADMIN_PASSWORD' in value "${PLATFORM_ADMIN_PASSWORD}"
  <-- "${platform.bootstrap.admin-password:ChangeMe123!}"
```

코드가 요구하는 필수 env 가 **서버 env 파일에 주입되지 않았다.** 08-11 커밋으로 기본값이
사라졌지만 08-09 빌드가 계속 돌고 있어 **이틀간 드러나지 않다가**, 신규 jar 이 배포되는
순간 폭발했다. **어제 작업한 mediplat 기능 변경 2건과는 무관하다.**

### 왜 12시간이나 몰랐나 — 원인보다 이쪽이 중요하다

| 요인 | 조치 |
|---|---|
| 배포 후 헬스체크 없음 | **2-E 신설.** csm 만 보지 말고 mediplat 도 `curl 18082` 확인 |
| `Restart=always` 가 증상을 가림 (`failed` 아닌 `activating`) | 2-E 에 명시 |
| mediplat 로그가 journald 에 없음 (파일 리다이렉트) → 초기 진단 실패 | 2-E 에 로그 파일 경로 명시 |
| 알림 없음 — 사용자 신고로 발견 | **미해결.** 아래 참조 |
| 무인 새벽 배포 가능 구조 | 2-C 수동 즉시 적용 권장으로 대응 |

### 이미 문서에 있었는데 재발했다

이 함정은 **두 곳에 기록되어 있었다.**

- [scripts/systemd/README.md:168](../scripts/systemd/README.md) — "실제 사례: `platform.bootstrap.admin-password` 는 `:ChangeMe123!` 기본값이 있는데도 `PLATFORM_ADMIN_PASSWORD` 미주입으로 mediplat 이 크래시 루프에 빠졌습니다."
- [docs/handoff-2026-08-12.md](handoff-2026-08-12.md) §7 함정 목록 1번

**문서에 적어두는 것만으로는 재발을 막지 못했다.** 그래서 0-0 을 절차의 **첫 단계**로 넣었다.
읽는 문서가 아니라 실행하는 체크로 바꾸는 것이 요점이다.

## 미해결 사항 (이번 배포 범위 아님)

- **서비스 다운 알림이 없다.** 이번 장애를 사용자가 발견했다. 최소한 `curl 18081/18082` 를
  주기 실행해 실패 시 알리는 장치가 필요하다. systemd timer + 알림 스크립트면 충분하다.
- **`PLATFORM_ADMIN_PASSWORD` 값 점검.** 2026-08-14 시점 임시값이 들어가 있다.
  이 값은 기동 시마다 운영 관리자 비밀번호를 덮어쓰므로, 정식 값으로 교체가 필요하다.

- **콜백 엔드포인트 무인증** — Phase 2에서 처리. [phase2-hold.md](phase2-hold.md) 참조.
  Phase 3(지갑) 착수 전 필수.
- **레거시 ROOT.war(AJP 8009) 종료** — httpd 372행 `ProxyPass /` 건이 있어 보류.
  이번엔 콜백 경로만 떼어낸다.
