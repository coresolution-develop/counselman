# 허브 링크 랭킹 — 설계안 (v1)

> 작성일: 2026-08-30
> 상태: **설계 초안 · 구현 미착수**
> 목적: `/links` 허브의 링크 노출을 "관리자 수동 정렬 + 생 클릭수"에서 **점수 기반 랭킹**으로 바꾼다
> 참조: [xai-org/x-algorithm](https://github.com/xai-org/x-algorithm) (Apache 2.0) — 패턴만 차용, 코드 이식 없음
> 다음 액션: **§4 0단계 실측 SQL 3개를 운영 DB에서 돌리고 결과로 Phase 2 진행 여부를 결정**

## 이 폴더의 다른 문서와의 관계

| 문서 | 다루는 것 | 이 문서와의 경계 |
|---|---|---|
| [README.md](README.md) | UI 리디자인 핸드오프 — 디자인 토큰·컴포넌트·레이아웃 | **화면이 어떻게 생겼나.** 이 문서는 건드리지 않는다 |
| [PHASE2.md](PHASE2.md) | 리디자인 2단계 잔여 작업 (기기·세션 목록, 소속 분류 등) | **화면 기능의 미완성분.** 랭킹과 겹치지 않는다 |
| **RANKING.md** (이 문서) | 링크를 **어떤 순서로** 내보낼지 | **무엇이 위에 오나.** 화면 구조는 유지하고 데이터 순서만 바꾼다 |

> 🔴 **한 곳에서 충돌한다.** README.md의 "기술 제약"은 *"기존 기능 유지: … 최근 사용, 인기 링크 …"* 를
> 못박고 있다. 이 문서의 **Phase 3**은 그 두 블록을 추천 영역으로 통합할지 검토 대상으로 둔다(§10-2).
> **Phase 1·2는 두 블록을 그대로 두므로 충돌하지 않는다.** Phase 3에 착수할 때 README.md의 제약을
> 함께 갱신할지 먼저 판단할 것 — 이 문서가 단독으로 뒤집지 않는다.

---

## 1. 배경 — 지금 `/links`가 하는 일

[CompanyLinkController.java:48-99](../../../src/main/java/com/coresolution/csm/controller/CompanyLinkController.java) 한 메서드가 **소스 5개 + 고정 삽입물 1개**를 조립해 `model.addAttribute()` 11번으로 내려보낸다.

| 모델 속성 | 소스 | 현재 정렬 |
|---|---|---|
| `linkRows` / `linkGroupRows` | `CompanyLinkService.listActiveLinks()` | 카테고리 `sort_order` → 링크 `sort_order` → title (전부 관리자 수동) |
| `favoriteRows` | `HubFavoriteService.listFavorites()` | 즐겨찾기 등록순 (`f.sort_order`) |
| `recentRows` | `HubHistoryService.listRecent()` | 내 클릭 최신순 8개 |
| `popularRows` | `HubHistoryService.listPopularPublic(6, 30)` | 전사 클릭 `COUNT(*)` 상위 6 |
| `customRows` / `customGroupRows` | `HubCustomLinkService.listOwn()` | 개인 `sort_order` |
| `notice` | `HubNoticeService.findActive()` | 고정 위치 배너 |

구조 자체는 이미 추천 피드다 — 여러 소스를 모아 한 화면에 블렌딩한다. 빠진 것은 **소스들을 합치는 단계**뿐이다.

### 1.1 확인된 문제 4가지

**① 중복 제거가 없다.**
각 소스가 서로를 모른 채 따로 렌더링된다. 즐겨찾기한 링크가 인기 링크에도, 최근 사용에도 동시에 뜬다. 화면 상단 3블록이 같은 링크 몇 개를 반복 노출하는 만큼 유효 정보량이 줄어든다.

**② 인기 순위가 생 카운트다.** ([HubHistoryService.java:78-99](../../../src/main/java/com/coresolution/csm/serivce/HubHistoryService.java))

```sql
SELECT cl.id, ..., COUNT(*) AS hits
  FROM csm.hub_member_link_history h
  JOIN csm.company_link cl ON cl.id = h.link_id AND cl.use_yn = 'Y'
 WHERE h.link_type = 'PUBLIC' AND h.link_id IS NOT NULL
   AND h.accessed_at >= (NOW() - INTERVAL ? DAY)
 GROUP BY cl.id  ORDER BY hits DESC  LIMIT ?
```

두 가지가 동시에 깨진다.

- **시간 가중이 없다** — 30일 창 안이면 어제 클릭과 29일 전 클릭이 같은 1표다. 특정일에 장애·공지로 몰린 클릭이 한 달 내내 1위를 지킨다.
- **1인 1표가 아니다** — 한 사람이 하루에 50번 눌러도 50표다. 새로고침 습관이 있는 소수가 전사 순위를 결정한다.

x-algorithm README가 경고하는 지점과 같은 축이다 — 가중치는 **행동 확률**에 곱하는 것이지 **누적 카운트**에 곱하는 게 아니다.

**③ 다양성 보정이 없다.** 인기 TOP 6이 전부 한 카테고리(예: "개발")로 채워져도 막을 장치가 없다. 카테고리 색·축약([HubLinkPresenter](../../../src/main/java/com/coresolution/csm/web/HubLinkPresenter.java))까지 만들어 분류를 보여주면서, 정작 노출은 분류를 고려하지 않는다.

**④ 인기 쿼리에 인덱스가 없다.**
`hub_member_link_history`의 인덱스는 `idx_hist_member_time (member_id, accessed_at)` 하나뿐이다([HubMemberService.java:307](../../../src/main/java/com/coresolution/csm/serivce/HubMemberService.java)). 인기 쿼리는 `member_id`를 안 쓰고 `link_type` + `link_id` + `accessed_at`으로 거르므로 **full scan**이다. 이력 테이블은 삭제 로직이 없어 계속 자라기만 한다 — 지금은 빨라도 시한폭탄이다. §7에서 해결한다.

---

## 2. x-algorithm에서 무엇을 가져오고 무엇을 버리는가

### 가져올 것

| 패턴 | 원본 | 이 문서에서 |
|---|---|---|
| 후보 파이프라인 (Source → Filter → Score → Select) | `candidate-pipeline/` | §5.1 |
| 소스 병합 시 중복 제거 | `home-mixer/filters/drop_duplicates_filter.rs` | §5.2 |
| 다중 신호 가중합 `Σ(wᵢ × signalᵢ)` | `home-mixer/scorers/ranking_scorer.rs:369` | §5.3 |
| 가중치를 코드가 아닌 설정으로 | `home-mixer/params/param.rs` | §5.3 |
| 작성자 다양성 감쇠 `(1-floor)·decay^k + floor` | `ranking_scorer.rs:561` | §5.5 |
| 신규 항목 부스트 (콜드스타트) | `home-mixer/scorers/author_cold_start.rs` | §5.6 |
| 랭킹과 노출 권한의 분리 | `visibility-filtering/` | §5.7 |
| 단계별 유입·제거 수 계측 | `candidate-pipeline/pipeline_summary.rs` | §5.8 |

### 버릴 것

`phoenix/`(JAX 트랜스포머), `simclusters/`(그래프 클러스터링), `user-cred-v2/`(PageRank), `clip/`·`grox/`(미디어 분류), `vm-ranker/`(DPP 리랭킹). **전부 수억 규모 데이터가 전제**다. 사내 링크 수십 개 규모에 넣을 것이 하나도 없다.

머신러닝은 도입하지 않는다. 예측 확률 자리에 **규칙 기반 정규화 신호**를 넣는다. 구조는 같고 신호 생성 방식만 다르다.

---

## 3. 목표 / 비목표

**목표**
1. 개인이 자주·최근 쓰는 링크가 추가 조작 없이 상단에 온다
2. 전사 인기 순위가 최근 사용을 반영하고, 소수 사용자에게 지배되지 않는다
3. 한 카테고리가 추천 영역을 독점하지 않는다
4. 신규 등록 링크와 신규 입사자가 빈 화면을 보지 않는다
5. **"왜 이게 안 뜨는가"를 로그로 답할 수 있다**

**비목표**
- 관리자 수동 정렬 폐지 — 카테고리별 전체 목록은 **지금 정렬을 그대로 둔다**. 랭킹은 상단 추천 영역에만 적용한다
- 개인별 A/B 실험 인프라 — 가중치는 설정 파일로 조절하고, 실험 프레임워크는 만들지 않는다
- 개인 클릭 이력의 타인 노출 — 전사 집계는 지금처럼 익명 합계만

---

## 4. 0단계 — 선행 실측 (구현 전 필수)

**이 셋을 먼저 재지 않으면 Phase 2·3은 착수하지 않는다.** 회원 20명·링크 15개 규모라면 개인화 랭킹은 비용만 크고 효과가 없다. Phase 1(§6)만 하고 끝내는 것이 맞다.

```sql
-- ① 신호 밀도: 최근 30일 활동 회원 수 / 총 클릭 / 1인당 클릭
SELECT COUNT(DISTINCT member_id) AS active_members,
       COUNT(*)                  AS total_clicks,
       ROUND(COUNT(*) / NULLIF(COUNT(DISTINCT member_id), 0), 1) AS clicks_per_member
  FROM csm.hub_member_link_history
 WHERE accessed_at >= NOW() - INTERVAL 30 DAY;
```

```sql
-- ② 인벤토리 크기: 활성 링크 수와 카테고리 분포
SELECT COUNT(*) AS links, COUNT(DISTINCT category) AS categories
  FROM csm.company_link WHERE use_yn = 'Y';
```

```sql
-- ③ 쏠림 정도: 상위 3명이 전체 클릭의 몇 %를 차지하는가
SELECT ROUND(100.0 * SUM(CASE WHEN rnk <= 3 THEN c ELSE 0 END) / SUM(c), 1) AS top3_share_pct
  FROM ( SELECT member_id, COUNT(*) c,
                RANK() OVER (ORDER BY COUNT(*) DESC) rnk
           FROM csm.hub_member_link_history
          WHERE accessed_at >= NOW() - INTERVAL 30 DAY
          GROUP BY member_id ) t;
```

### 판단 기준

| 조건 | 결론 |
|---|---|
| ①의 `active_members` < 10 **또는** ②의 `links` < 20 | **Phase 1만 한다.** 개인화는 보류 |
| ①의 `clicks_per_member` < 5 | 개인 신호가 희박 → 개인화 가중치를 낮게 시작 (§5.3 대안 열) |
| ③의 `top3_share_pct` > 50 | 소수 지배 확인 → §5.4의 1인 1일 1표 상한이 필수 |
| 위 어디에도 안 걸림 | Phase 1 → 2 → 3 전부 진행 |

---

## 5. 설계

### 5.1 파이프라인

컨트롤러의 조립 코드를 **`HubFeedService` 한 곳**으로 옮기고 단계를 명시적으로 나눈다.

```
1. SOURCE (병렬 아님, 전부 같은 DB)
   ├ 공용 활성 링크        CompanyLinkService.listActiveLinks()
   ├ 내 즐겨찾기           HubFavoriteService.listFavoriteLinkIds()
   ├ 내 클릭 신호          HubHistoryService.myLinkSignals(memberId)      ← 신규
   ├ 전사 인기 신호        HubHistoryService.popularSignals(days)         ← 교체
   └ 내 커스텀 링크        HubCustomLinkService.listOwn()
                                    ▼
2. DEDUP        후보 키(§5.2)로 병합. 같은 키는 한 행으로 접고 신호를 합친다
                                    ▼
3. FILTER       use_yn / 로그인 상태 / env 칩 (§5.7) — 점수와 무관
                                    ▼
4. SCORE        Σ(weight × signal)  (§5.3)
                                    ▼
5. DIVERSITY    카테고리 감쇠 곱 (§5.5) + 콜드스타트 부스트 (§5.6)
                                    ▼
6. SELECT       상위 K개 → "추천" 영역
                                    ▼
7. SUMMARY      단계별 유입·제거 수 로깅 (§5.8)
```

카테고리별 전체 목록(`linkGroupRows`)은 이 파이프라인을 **타지 않는다**. 지금의 관리자 정렬 그대로 아래에 남는다.

> ⚠️ **점수 계산과 DB 조회를 섞지 않는다.** 신호 수집(1)만 SQL이고, 2~6은 순수 자바 함수여야 한다. 그래야 단위 테스트가 DB 없이 돌고(§8), 가중치를 바꿔가며 결과를 눈으로 비교할 수 있다.

### 5.2 후보 키 — 중복 제거의 기준

**공용 링크와 커스텀 링크는 ID 공간이 다르다.** `company_link.id = 5`와 `hub_member_custom_link.id = 5`는 서로 다른 링크다. `hub_member_link_history`도 `link_id` / `custom_link_id` 두 칼럼으로 이를 나눈다. 숫자 ID만으로 dedup하면 남의 링크와 내 링크가 섞인다.

```java
// 후보 키는 반드시 타입을 포함한다
record CandidateKey(String type, long id) {}   // type ∈ {"PUBLIC", "CUSTOM"}
```

`link_type` 값은 이미 `HubGoController`가 `"PUBLIC"` / `"CUSTOM"`으로 쓰고 있다([HubGoController.java:50,63](../../../src/main/java/com/coresolution/csm/controller/HubGoController.java)). **그 값을 그대로 쓴다.** 새 상수를 만들면 두 곳이 갈린다.

> ⚠️ **URL로 dedup하지 않는다.** 이력은 `url_snapshot`을 남기지만 원본 URL이 수정되면 스냅샷과 어긋난다. 표시용 fallback일 뿐 동일성 판정 기준이 아니다.

### 5.3 스코어링

```java
// HubLinkScorer.java — 순수 함수. DB 접근 없음.
double raw =
      w.favorite   * (isFavorite ? 1.0 : 0.0)
    + w.custom     * (isCustom   ? 1.0 : 0.0)
    + w.myRecency  * myRecencySignal      // 0..1, §5.4 ①
    + w.myFreq     * myFrequencySignal    // 0..1, §5.4 ②
    + w.teamPop    * teamPopularitySignal // 0..1, §5.4 ③
    + w.coldStart  * (isColdStart ? 1.0 : 0.0);  // §5.6
```

모든 신호를 **0~1로 정규화**한 뒤 가중치를 곱한다. 정규화를 안 하면 가중치 숫자가 단위(횟수·일수)에 묶여 의미를 잃는다.

#### 가중치 기본값

x-algorithm의 배분 철학을 따른다 — **하기 어려운 행동일수록 높게**. 별을 눌러 즐겨찾기에 등록하는 것은 링크를 한 번 클릭하는 것보다 훨씬 무거운 의사표시다. 전사 인기는 "내가 안 고른 것"이므로 개인 신호보다 낮게 깐다(x-algorithm의 Out-of-Network Discount와 같은 자리).

| 키 | 기본값 | ①이 희박할 때 대안 | 근거 |
|---|---|---|---|
| `hub.rank.w.favorite` | **3.0** | 3.0 | 명시적 선호. 가장 강한 신호 |
| `hub.rank.w.custom` | **1.5** | 1.5 | 직접 등록한 개인 링크 |
| `hub.rank.w.my-recency` | **2.0** | 1.0 | 최근 쓴 것이 지금도 필요할 확률 |
| `hub.rank.w.my-frequency` | **1.0** | 0.5 | 습관적으로 쓰는 것 |
| `hub.rank.w.team-popularity` | **0.5** | 1.2 | 개인 신호 없을 때의 안전판 |
| `hub.rank.w.cold-start` | **0.8** | 0.8 | 신규 링크 노출 기회 |

> 🔴 **가중치를 코드에 상수로 박지 않는다.** 전부 `application.properties`로 뺀다 — 재배포 없이 조절하기 위한 것이 아니라, **어떤 값이 실제로 쓰이는지 한 파일에서 읽히게** 하기 위한 것이다. `param.rs`가 그 역할을 하는 이유와 같다.

```properties
# application.properties — 허브 랭킹 가중치
hub.rank.w.favorite=3.0
hub.rank.w.custom=1.5
hub.rank.w.my-recency=2.0
hub.rank.w.my-frequency=1.0
hub.rank.w.team-popularity=0.5
hub.rank.w.cold-start=0.8

# 신호 파라미터
hub.rank.recency-half-life-days=7
hub.rank.frequency-cap-days=20
hub.rank.popularity-window-days=30
hub.rank.popularity-half-life-days=7

# 다양성 보정
hub.rank.diversity-decay=0.7
hub.rank.diversity-floor=0.3

# 콜드스타트
hub.rank.cold-start-age-days=14
hub.rank.cold-start-max-clicks=5

# 추천 영역 크기
hub.rank.top-k=8
```

### 5.4 신호 계산

#### ① 개인 최근성 — 지수 감쇠

```java
// 마지막 클릭이 오늘이면 1.0, 반감기(7일)마다 절반. 클릭 이력 없으면 0.
double recency(Long daysSinceLastClick, int halfLife) {
    if (daysSinceLastClick == null) return 0.0;
    return Math.pow(0.5, (double) daysSinceLastClick / halfLife);
}
```

#### ② 개인 빈도 — 클릭 수가 아니라 **사용한 날 수**

```sql
-- 최근 90일, 내가 이 링크를 '며칠에 걸쳐' 썼는가 + 마지막으로 쓴 지 며칠 됐는가
SELECT h.link_type, h.link_id, h.custom_link_id,
       COUNT(DISTINCT DATE(h.accessed_at))  AS used_days,
       DATEDIFF(NOW(), MAX(h.accessed_at))  AS days_since_last
  FROM csm.hub_member_link_history h
 WHERE h.member_id = ?
   AND h.accessed_at >= NOW() - INTERVAL 90 DAY
 GROUP BY h.link_type, h.link_id, h.custom_link_id
```

```java
// used_days 를 상한(20일)으로 정규화. 하루에 몇 번 눌렀는지는 보지 않는다.
double frequency(int usedDays, int cap) { return Math.min(1.0, (double) usedDays / cap); }
```

> ⚠️ **`COUNT(*)`가 아니라 `COUNT(DISTINCT DATE(...))`다.** 새로고침 습관이 신호를 왜곡하지 않게 하는 핵심이고, ③의 1인 1일 1표와 같은 원리다.

#### ③ 전사 인기 — 시간 감쇠 + 1인 1일 1표

```sql
-- 사람 × 링크 × 날짜 조합당 1표로 접은 뒤, 날짜별로 감쇠 가중해 합산
SELECT d.link_id,
       SUM(POW(0.5, DATEDIFF(CURDATE(), d.day) / ?)) AS score   -- ? = 반감기(일)
  FROM ( SELECT DISTINCT h.member_id, h.link_id, DATE(h.accessed_at) AS day
           FROM csm.hub_member_link_history h
          WHERE h.link_type = 'PUBLIC'
            AND h.link_id IS NOT NULL
            AND h.accessed_at >= NOW() - INTERVAL ? DAY ) d
  JOIN csm.company_link cl ON cl.id = d.link_id AND cl.use_yn = 'Y'
 GROUP BY d.link_id
```

반환값은 **정규화 전 원점수**다. 자바에서 배치 내 최댓값으로 나눠 0~1로 만든다:

```java
double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
double normalized = max <= 0 ? 0.0 : score / max;
```

> ⚠️ **`LIMIT`을 SQL에 두지 않는다.** 현재 `listPopularPublic(6, 30)`은 SQL에서 상위 6개를 잘라 내려주는데, 그러면 7위 이하 링크의 인기 신호가 **0으로 소실**된다. 파이프라인은 전체 활성 링크의 신호가 필요하다. 자르기는 §5.1의 6단계(SELECT)에서만 한다.

### 5.5 다양성 보정

x-algorithm `ranking_scorer.rs:561`의 공식을 그대로 쓴다.

```java
/** k = 이 카테고리가 앞 순위에서 이미 몇 번 나왔는가 (0부터). */
static double diversityMultiplier(double decay, double floor, int k) {
    return (1.0 - floor) * Math.pow(decay, k) + floor;
}
```

점수 내림차순으로 훑으면서 카테고리별 등장 횟수를 세고, 곱한 뒤 **다시 정렬**한다.

`decay=0.7, floor=0.3` 기준 배수: 1번째 `1.00` · 2번째 `0.79` · 3번째 `0.64` · 4번째 `0.54` … `0.30` 수렴.

하드 제한(`카테고리당 최대 2개`)을 쓰지 않는 이유: 링크가 한 카테고리에 몰린 조직에서는 하드 제한이 추천 영역을 못 채운다. 곱셈 감쇠는 **다른 카테고리에 후보가 없으면 자연히 같은 카테고리로 채운다.**

> ⚠️ 카테고리가 `NULL`인 링크가 있다([company_link.category](../../../src/main/java/com/coresolution/csm/serivce/CompanyLinkService.java) — `default null`). `HubLinkPresenter`가 화면에서 "기타"로 묶는 것과 **같은 규칙**으로 정규화해서 세야 한다. 안 그러면 미분류 링크들이 서로 감쇠를 안 먹는다.

### 5.6 콜드스타트

두 방향 모두 막힌다.

- **신규 링크** — 클릭 0 → 인기 신호 0 → 영영 추천에 못 뜸 → 클릭 0 (자기강화 루프)
- **신규 회원** — 이력 0 → 개인 신호 전부 0 → 추천 영역이 전사 인기로만 채워짐

신규 링크는 x-algorithm의 New-Author Boost와 같은 방식으로 푼다:

```java
boolean isColdStart(CompanyLink link, long totalClicks) {
    return daysSince(link.getCreatedAt()) <= coldStartAgeDays   // 기본 14일
        && totalClicks < coldStartMaxClicks;                     // 기본 5회
}
```

부스트는 **기간 한정**이다. 14일이 지나면 자동으로 꺼지고, 클릭 5회를 넘겨도 꺼진다 — 즉 "노출 기회를 주되 성과가 없으면 자리를 내준다".

신규 회원은 별도 로직 없이 해결된다. 개인 신호가 전부 0이면 `teamPop` + `coldStart` 항만 남아 자연스럽게 "전사 인기 + 신규 링크" 목록이 된다. **이것이 가중합 구조의 이점**이다 — 분기문 없이 신호 부재가 그대로 처리된다.

### 5.7 가시성 필터 — 점수와 분리

🔴 **권한·상태 판정을 점수에 섞지 않는다.** "점수 -999" 같은 코드가 생기는 순간 순위와 권한이 엉키고, 이후 가중치를 조절할 때마다 권한이 새는지 확인해야 한다. x-algorithm이 랭킹과 `visibility-filtering/`을 다른 서비스로 나눈 이유다.

허브의 가시성 축은 넷이다. **`user_data_{inst}` 기반 `PermissionResolver`는 여기에 관여하지 않는다** — 그것은 CSM 상담 시스템 인증 경로 전용이고([InstAuthenticationProvider.java:35](../../../src/main/java/com/coresolution/csm/handler/InstAuthenticationProvider.java), [PageController.java:339](../../../src/main/java/com/coresolution/csm/controller/PageController.java)), 허브는 `hub_member`라는 별개 계정 체계다.

| 축 | 값 | 판정 |
|---|---|---|
| 링크 활성 | `company_link.use_yn = 'Y'` | `DROP` |
| 커스텀 링크 활성 | `hub_member_custom_link.use_yn = 'Y'` | `DROP` |
| 소유 | 커스텀 링크는 `member_id` 일치 필수 | `DROP` — 이미 `listOwn`이 강제(가드레일 ①-b) |
| 로그인 | 미로그인은 즐겨찾기·커스텀·개인 이력 소스 자체를 안 탄다 | `DROP` |
| 환경 칩 | `company_link.env` (prod/dev/demo) | 화면 필터 — **점수 영향 없음** |

`env` 칩은 서버 필터가 아니라 클라이언트 표시 토글이므로 파이프라인 3단계에 넣지 않는다. 지금 동작을 그대로 둔다.

### 5.8 파이프라인 요약 로깅

`pipeline_summary.rs`가 하는 일의 최소 버전. 각 단계 전후 개수를 한 줄로 남긴다.

```java
log.info("[hub-feed] member={} src(pub={},fav={},hist={},pop={},custom={}) "
       + "dedup={} filtered={} scored={} topK={} took={}ms",
        memberId, pubN, favN, histN, popN, customN,
        afterDedup, afterFilter, scored, topK, elapsedMs);
```

이 한 줄이 *"내 허브에 왜 이 링크가 안 떠요"* 에 답하는 근거가 된다. 어느 단계에서 사라졌는지가 숫자로 보인다.

---

## 6. 구현 계획 — 3단계, 각각 독립 배포 가능

### Phase 1 — 인기 쿼리 교체 + 인덱스 (UI 무변경)

가장 위험이 낮고 효과가 즉시 보인다. **0단계 실측 결과와 무관하게 진행 가능.**

- [ ] §7 인덱스 2개 추가
- [ ] `HubHistoryService.listPopularPublic()`을 §5.4 ③ 쿼리로 교체 (반감기 7일 + 1인 1일 1표)
- [ ] `application.properties`에 `popularity-window-days`, `popularity-half-life-days` 추가
- [ ] 배포 전후 인기 TOP 6 목록을 캡처해 비교 — **순위가 바뀌지 않으면 데이터가 부족하다는 신호**

기존 시그니처 `listPopularPublic(int limit, int days)`는 유지한다. 컨트롤러는 손대지 않는다.

### Phase 2 — 파이프라인 + 스코어러 도입

0단계 판단 기준(§4)을 통과했을 때만.

- [ ] `HubFeedService` 신설 — §5.1의 1~4·6단계
- [ ] `HubLinkScorer` 신설 — 순수 함수, `@ConfigurationProperties`로 가중치 주입
- [ ] `CandidateKey` 도입, dedup 구현 (§5.2)
- [ ] `HubHistoryService.myLinkSignals(memberId)` 신설 (§5.4 ②)
- [ ] `popularSignals(days)` 신설 — `LIMIT` 없는 전체 신호 반환
- [ ] 컨트롤러에 `recommendedRows` 추가. **기존 3블록은 그대로 둔다** (병행 노출로 비교)
- [ ] `HubLinkView.metaText`에 추천 사유 표시 ("자주 사용" / "즐겨찾기" / "전사 인기")

### Phase 3 — 다양성 + 콜드스타트 + 계측

- [ ] `diversityMultiplier` 적용 (§5.5)
- [ ] 콜드스타트 부스트 (§5.6)
- [ ] 파이프라인 요약 로깅 (§5.8)
- [ ] 기존 `recentRows` / `popularRows` 블록 제거 여부 결정 — **추천 영역이 실제로 더 나은지 확인한 뒤에만**

---

## 7. 스키마 변경

DDL은 `HubMemberService.ensureTables()` 방식(멱등 생성)을 따른다. 인덱스는 `addColumnIfMissing`과 같은 패턴으로 존재 확인 후 추가한다.

```sql
-- ① 전사 인기 집계용. 현재 인덱스는 member_id 선두라 이 쿼리를 못 탄다.
ALTER TABLE csm.hub_member_link_history
  ADD KEY idx_hist_link_time (link_type, link_id, accessed_at);

-- ② 개인 신호 집계용. 기존 idx_hist_member_time 은 링크별 GROUP BY 에 부적합.
ALTER TABLE csm.hub_member_link_history
  ADD KEY idx_hist_member_link (member_id, link_id, accessed_at);
```

### 보존 정책 — 별건이지만 같이 봐야 한다

`hub_member_link_history`에 **삭제 로직이 없다.** 클릭할 때마다 한 행씩 무한히 쌓인다. 랭킹이 이 테이블을 매 요청 집계하게 되므로 방치하면 응답 시간이 서서히 늘어난다.

```sql
-- 배치 또는 스케줄러로. 랭킹 최대 창(90일)보다 넉넉히 남긴다.
DELETE FROM csm.hub_member_link_history WHERE accessed_at < NOW() - INTERVAL 180 DAY;
```

`DischargeNoticeScheduler`가 이미 있으므로 같은 방식의 `@Scheduled` 하나면 된다. **Phase 1과 함께 넣는 것을 권한다.**

> 📌 실측 ①의 `total_clicks`가 수십만 이상이면 매 요청 집계 대신 **일 1회 롤업 테이블**(`hub_link_popularity_daily`)로 바꿔야 한다. 그 미만이면 인덱스만으로 충분하다.

---

## 8. 회귀 테스트

2~6단계를 순수 함수로 설계한 이유가 여기 있다. DB 없이 JUnit으로 전부 덮는다.

| # | 대상 | 검증 |
|---|---|---|
| ① | `diversityMultiplier` | k=0 → 1.0, k 증가 시 단조 감소, floor 아래로 안 내려감 |
| ② | `CandidateKey` | `PUBLIC:5` ≠ `CUSTOM:5` — **ID 공간 분리** |
| ③ | dedup | 즐겨찾기 + 인기 + 최근에 모두 있는 링크가 결과에 1행 |
| ④ | `recency` | 0일=1.0, 반감기=0.5, 이력 없음(null)=0.0 |
| ⑤ | `frequency` | cap 초과 입력이 1.0을 넘지 않음 |
| ⑥ | 인기 정규화 | 전 링크 점수 0일 때 0으로 나누기 발생 안 함 |
| ⑦ | 신규 회원 | 개인 신호 전부 0 → 예외 없이 전사 인기 순 결과 |
| ⑧ | 콜드스타트 | 15일 된 링크는 부스트 안 받음, 클릭 6회 링크도 안 받음 |
| ⑨ | 가시성 | `use_yn='N'` 링크는 점수가 아무리 높아도 결과에 없음 |
| ⑩ | 카테고리 NULL | 미분류 링크들이 서로 다양성 감쇠를 먹음(같은 그룹으로 셈) |

SQL 2개(§5.4 ②③)는 통합 테스트 대상이다. 특히 ③의 `DISTINCT member_id, link_id, DATE()`가 **같은 날 중복 클릭을 실제로 1표로 접는지** 확인한다.

---

## 9. 결정사항 (재론 금지)

| # | 결정 | 이유 |
|---|---|---|
| 1 | 머신러닝 도입 안 함 | 데이터 규모 미달. 규칙 기반 신호로 같은 구조를 만든다 |
| 2 | 카테고리별 전체 목록은 관리자 정렬 유지 | 랭킹은 추천 영역에만. 운영자 큐레이션을 뺏지 않는다 |
| 3 | 가중치는 전부 `application.properties` | 재배포 회피가 목적이 아니라 **한 파일에서 읽히게** 하는 것 |
| 4 | 가시성 판정은 점수와 분리 | 섞으면 가중치 조절할 때마다 권한 누수 확인 필요 |
| 5 | 후보 키에 타입 포함 (`PUBLIC`/`CUSTOM`) | ID 공간이 다르다. URL은 스냅샷이라 기준 불가 |
| 6 | 인기 신호는 SQL에서 `LIMIT` 안 함 | 잘라내면 하위 링크 신호가 0으로 소실 |
| 7 | 빈도는 클릭 수가 아니라 **사용한 날 수** | 새로고침 습관이 신호를 왜곡하지 않게 |
| 8 | 다양성은 곱셈 감쇠, 하드 제한 아님 | 한 카테고리에 몰린 조직에서 추천 영역이 안 채워짐 |
| 9 | x-algorithm 코드 직접 이식 없음 | Rust + 사내 인프라 크레이트 의존. 패턴만 차용 (Apache 2.0) |

---

## 10. 미결 질문

1. **추천 영역 K값** — `top-k=8`은 임시값이다. 실측 ②의 활성 링크 수가 20개 미만이면 8개 추천은 전체의 40%라 의미가 옅다. 실측 후 결정.
2. **기존 3블록 존치 여부** — Phase 2에서 병행 노출한 뒤, 추천 영역이 실제로 더 나은지 확인하고 Phase 3에서 결정한다. **지금 정하지 않는다.**
3. **커스텀 링크의 전사 인기** — 개인 링크는 본인만 보므로 전사 집계에서 제외하는 것이 맞다(현재 쿼리도 `link_type='PUBLIC'`만 센다). 유지.
4. **미로그인 사용자** — 지금은 인기 링크만 본다. 추천 영역을 미로그인에도 보일지, 아니면 로그인 유도로 쓸지 미정.

---

## 참고

- [x-algorithm README](https://github.com/xai-org/x-algorithm) — 전체 아키텍처와 설계 결정 5가지
- `home-mixer/params/param.rs` — 가중치 선언 패턴과 실제 운영 기본값
- `home-mixer/scorers/ranking_scorer.rs` — 가중합 + 다양성 감쇠 + OON 할인 구현
- `candidate-pipeline/` — 파이프라인 단계 타입 정의
- [README.md](README.md) · [PHASE2.md](PHASE2.md) — 같은 폴더의 UI 리디자인 문서 (경계는 문서 상단 표 참고)
- [links-deploy.md](../../links-deploy.md) — `/links` 배포 구성 (csm과 같은 `csm` DB 공유)
