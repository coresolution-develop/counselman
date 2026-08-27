# Claude Code 워크트리 — 미병합 커밋 기록

작성일: 2026-08-27  ·  기준 브랜치: `dev` (`f51e552`)

## 왜 이 파일이 있나

`.claude/worktrees/` 는 Claude Code 가 만든 git worktree 다 (브랜치 `claude/*`).
`.gitignore` 에 넣었으므로 저장소에는 안 올라간다. 디스크에서 지우면
**미병합 브랜치의 커밋도 같이 사라진다.** 무엇이 있었는지 남겨 둔다.

⚠️ 이 파일은 커밋 목록일 뿐이다. **커밋 내용 자체를 보존하지 않는다.**
실제로 살릴 것이 있으면 worktree 를 지우기 전에 브랜치를 푸시하거나 patch 로 뽑아야 한다.

## 전체 목록

| worktree | 브랜치 | dev 병합 | 미커밋 |
|---|---|---|---|
| `amazing-fermat-f0fa7d` | `claude/amazing-fermat-f0fa7d` | ⚠️ **미병합 4건** | 0 |
| `brave-bohr-5fe87c` | `claude/brave-bohr-5fe87c` | ✅ 병합됨 | 2 |
| `cranky-varahamihira-cc482a` | `claude/cranky-varahamihira-cc482a` | ✅ 병합됨 | 3 |
| `determined-johnson-a8e884` | `claude/determined-johnson-a8e884` | ⚠️ **미병합 1건** | 5 |
| `eager-davinci-3b99d2` | `claude/eager-davinci-3b99d2` | ✅ 병합됨 | 2 |
| `eloquent-fermi-c926c3` | `claude/eloquent-fermi-c926c3` | ✅ 병합됨 | 0 |
| `focused-euclid-abfcb3` | `(detached 4b252ed)` | ✅ 병합됨 | 0 |
| `hungry-elbakyan-d68617` | `claude/hungry-elbakyan-d68617` | ✅ 병합됨 | 1 |
| `jolly-einstein-c103dd` | `(detached 96a64bb)` | ✅ 병합됨 | 0 |
| `jolly-morse-7c8b0f` | `claude/jolly-morse-7c8b0f` | ✅ 병합됨 | 0 |
| `loving-panini-6e3e73` | `claude/loving-panini-6e3e73` | ✅ 병합됨 | 0 |
| `recursing-davinci-f041d8` | `claude/recursing-davinci-f041d8` | ✅ 병합됨 | 8 |
| `recursing-goodall-67d26a` | `claude/recursing-goodall-67d26a` | ✅ 병합됨 | 0 |
| `stoic-mestorf-025c5f` | `claude/stoic-mestorf-025c5f` | ✅ 병합됨 | 0 |
| `upbeat-swirles-cf1904` | `claude/upbeat-swirles-cf1904` | ✅ 병합됨 | 0 |
| `vigorous-pasteur-e20f5e` | `claude/vigorous-pasteur-e20f5e` | ✅ 병합됨 | 5 |

## 미병합 브랜치 상세

### `claude/amazing-fermat-f0fa7d`

dev 에 없는 커밋:

```
6cc4b99 2026-05-13 docs(todo): log role-management add-user candidate filter bug
0d2f2c6 2026-05-13 chore(cancer-treatment): add prod deploy templates, nginx snippet and preflight checks
d348912 2026-05-13 chore(build): integrate cancer-treatment into deploy bundles + add prod schema check
28965ea 2026-05-13 chore(cancer-treatment): split datasource config into dev/prod/local profiles
```

변경 파일:

```
 TODO.md                                            |   9 +-
 build.gradle                                       |  26 ++-
 cancer-treatment/build.gradle                      |  34 ++++
 .../src/main/resources/application-dev.properties  |  19 +++
 .../main/resources/application-local.properties    |  23 +++
 .../src/main/resources/application-prod.properties |  17 ++
 .../src/main/resources/application.properties      |  12 +-
 .../src/test/resources/application.properties      |  14 ++
 docs/cancer-treatment-deploy-checklist.md          | 179 +++++++++++++++++++++
 docs/cancer-treatment-nginx.conf.example           |  35 ++++
 scripts/cancer-treatment-schema-check.sql          | 100 ++++++++++++
 scripts/cancer-treatment.env.example               |  32 ++++
 scripts/cancer-treatment.service.example           |  45 ++++++
 scripts/prod-preflight.sh                          |  25 ++-
 14 files changed, 556 insertions(+), 14 deletions(-)
```

### `claude/determined-johnson-a8e884`

dev 에 없는 커밋:

```
a52c29c 2026-05-12 feat(cancer-treatment): add cancer treatment schedule management app
```

변경 파일:

```
 .../cancertreatment/service/PatientService.java    |  115 ++
 .../cancertreatment/service/SettingService.java    |  173 +++
 .../cancertreatment/service/SsoService.java        |  108 ++
 .../service/TreatmentRoomService.java              |  140 ++
 .../service/TreatmentScheduleService.java          |  231 ++++
 .../src/main/resources/application.properties      |   16 +
 cancer-treatment/src/main/resources/schema.sql     |  147 +++
 .../src/main/resources/static/css/layout.css       |  173 +++
 .../src/main/resources/static/css/treatment.css    | 1308 ++++++++++++++++++
 .../src/main/resources/static/js/dashboard.js      |   88 ++
 .../src/main/resources/static/js/patients.js       |  209 +++
 .../src/main/resources/static/js/settings.js       |  235 ++++
 .../main/resources/static/js/treatment-calendar.js |  289 ++++
 .../main/resources/static/js/treatment-rooms.js    |  175 +++
 .../main/resources/static/js/treatment-schedule.js |  518 ++++++++
 .../templates/cancer-treatment-schedule.html       | 1396 ++++++++++++++++++++
 .../src/main/resources/templates/dashboard.html    |   80 ++
 .../resources/templates/fragments/app-shell.html   |   37 +
 .../main/resources/templates/fragments/layout.html |  122 ++
 .../main/resources/templates/login-required.html   |   17 +
 .../src/main/resources/templates/patients.html     |  104 ++
 .../src/main/resources/templates/settings.html     |   99 ++
 .../resources/templates/treatment-calendar.html    |  475 +++++++
 .../main/resources/templates/treatment-rooms.html  |   86 ++
 .../CancerTreatmentApplicationTests.java           |   12 +
 .../service/PatientServiceTests.java               |   55 +
 .../service/SettingServiceTests.java               |   61 +
 .../service/TreatmentRoomServiceTests.java         |   72 +
 .../service/TreatmentScheduleServiceTests.java     |   21 +
 48 files changed, 8486 insertions(+)
```

**커밋되지 않은 파일** — 브랜치를 푸시해도 안 따라간다:

```
?? src/main/java/com/coresolution/csm/controller/TreatmentScheduleController.java
?? src/main/java/com/coresolution/csm/serivce/TreatmentScheduleService.java
?? src/main/java/com/coresolution/csm/vo/TreatmentScheduleDto.java
?? src/main/resources/static/css/linear-design-system.css
?? src/main/resources/templates/schedule/
```

## 커밋되지 않은 변경 (전체)

**병합된 브랜치에도 커밋 안 된 파일이 있다.** worktree 를 지우면 이것들도 사라진다 —
브랜치를 푸시해도 따라가지 않는다. 살릴 것이 있는지 지우기 전에 확인할 것.

빌드 산출물(`build/`, `.gradle/`)과 `.agents/state/` 는 제외했다.

### `brave-bohr-5fe87c`

```
 M src/main/java/com/coresolution/csm/controller/RolesApiController.java
 M src/main/java/com/coresolution/csm/serivce/CsmSchemaBootstrapService.java
```

### `cranky-varahamihira-cc482a`

```
 M build.gradle
 M cancer-treatment/build.gradle
 M scripts/deploy-dev.sh
```

### `determined-johnson-a8e884` (미병합 브랜치)

```
?? src/main/java/com/coresolution/csm/controller/TreatmentScheduleController.java
?? src/main/java/com/coresolution/csm/serivce/TreatmentScheduleService.java
?? src/main/java/com/coresolution/csm/vo/TreatmentScheduleDto.java
?? src/main/resources/static/css/linear-design-system.css
?? src/main/resources/templates/schedule/
```

### `eager-davinci-3b99d2`

```
 M .agents/results/result-backend.md
 M src/main/resources/templates/csm/counsel/admissionPledge.html
```

### `hungry-elbakyan-d68617`

```
 M src/main/resources/templates/design/chat-page.html
```

### `recursing-davinci-f041d8`

```
 M TODO.md
 M src/main/java/com/coresolution/csm/vo/CounselReservation.java
 M src/main/resources/static/assets/js/chrome.js
 M src/main/resources/static/css/csm/Include/layout-modern-shell.css
 M src/main/resources/static/css/csm/core/admin/admin.css
 M src/main/resources/static/js/csm/core/admin/admin.js
 M src/main/resources/templates/csm/Include/layout.html
 M src/main/resources/templates/csm/core/admin/admin.html
```

### `vigorous-pasteur-e20f5e`

```
 M src/main/java/com/coresolution/csm/controller/PageController.java
 M src/main/java/com/coresolution/csm/serivce/CsmAuthService.java
 M src/main/java/com/coresolution/csm/vo/CounselReservation.java
 M src/main/resources/templates/design/consultation-intake.html
 M src/main/resources/templates/design/inpatient-consultation.html
```

## 정리 방법

디렉터리를 직접 지우면 `.git/worktrees` 에 메타가 남는다. 반드시:

```bash
git worktree remove .claude/worktrees/<이름>
```

미병합 브랜치를 살리려면 지우기 **전에**:

```bash
git push origin claude/<이름>
```
