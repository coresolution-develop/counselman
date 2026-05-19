# Nightly Auto-Deploy

작업자가 낮 동안 staging 디렉터리에 빌드 산출물을 올려두고 명시 마커를 만들면, **02:30 KST**에 systemd 타이머가 깨어나 csm / mediplat / cancer-treatment 중 staging에 올라온 것만 자동으로 라이브에 반영합니다.

기존 `nightly-maintenance.timer`(03:00)보다 30분 먼저 실행되도록 배치되어 있어, 새 산출물이 자리잡은 뒤 maintenance가 reload를 수행합니다.

## 서버 디렉터리 구조

```
/opt/deploy/
├── scripts/
│   └── deploy-nightly.sh                   # 이 저장소의 scripts/deploy-nightly.sh 사본
├── staging/                                # 작업자가 산출물을 올리는 위치
│   ├── csm.war                             # (선택) 이 파일이 있으면 csm 배포
│   ├── mediplat.jar                        # (선택) 있으면 mediplat 배포
│   ├── cancer-treatment.jar                # (선택) 있으면 cancer-treatment 배포
│   └── deploy.ok                           # ★ 트리거 마커 (없으면 02:30에 아무것도 안 함)
└── archive/
    └── 2026-05-19_023000/                  # 처리된 배포의 스냅샷 (롤백 시 참고)
        ├── csm.war
        ├── mediplat.jar
        └── deploy.ok
```

라이브 위치 (env로 override 가능):
- csm: `/usr/local/tomcat10/webapps/csm.war` (Tomcat hot-deploy, 재시작 없음)
- mediplat: `/opt/mediplat/app/mediplat.jar` (systemd: `mediplat`)
- cancer-treatment: `/opt/cancer-treatment/app/cancer-treatment.jar` (systemd: `cancer-treatment`)

## 1회 설치 (서버에서)

```bash
# 1) 디렉터리 + 스크립트 배치
sudo mkdir -p /opt/deploy/{staging,archive,scripts}
sudo cp scripts/deploy-nightly.sh /opt/deploy/scripts/
sudo chmod +x /opt/deploy/scripts/deploy-nightly.sh

# 2) 환경 override가 필요하면 (선택)
sudo tee /etc/default/nightly-deploy >/dev/null <<'EOF'
# 기본값이면 비워두세요. 경로/서비스명이 다를 때만 작성.
# MEDIPLAT_SERVICE=mediplat-next
# CANCER_SERVICE=cancer-treatment-next
# BACKUP_KEEP=5
EOF

# 3) systemd 유닛 설치
sudo cp scripts/systemd/nightly-deploy.service /etc/systemd/system/
sudo cp scripts/systemd/nightly-deploy.timer   /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now nightly-deploy.timer

# 4) 동작 확인
systemctl list-timers nightly-deploy.timer
```

## 일상 사용 (작업자)

```bash
# 1) 산출물 빌드 (로컬)
./gradlew packageProdDeploy
# (cancer-treatment까지 묶으려면 build.gradle의 packageProdDeploy에
#  cancerTreatmentProdJar 의존성을 추가해야 합니다. 현재는 csm/mediplat만 묶임.)

# 2) staging으로 업로드 — 그날 배포할 것만 올리세요
scp build/deploy/prod/csm-prod.war           PROD:/opt/deploy/staging/csm.war
scp mediplat/build/libs/mediplat-prod.jar    PROD:/opt/deploy/staging/mediplat.jar
scp cancer-treatment/build/libs/cancer-treatment-prod.jar \
    PROD:/opt/deploy/staging/cancer-treatment.jar

# 3) ★ 마커 생성 — 이 명령이 떨어진 다음날 02:30 KST에 배포됨
ssh PROD 'touch /opt/deploy/staging/deploy.ok'

# (선택) 마지막 순간 취소 — 02:30 전에 실행
ssh PROD 'rm -f /opt/deploy/staging/deploy.ok'

# (선택) 다음 실행 시각 확인
ssh PROD 'systemctl list-timers nightly-deploy.timer'

# (선택) 지금 당장 한 번 돌리고 싶을 때 (수동 실행)
ssh PROD 'sudo systemctl start nightly-deploy.service && journalctl -u nightly-deploy -n 200 --no-pager'
```

## 로그 보기

```bash
journalctl -u nightly-deploy.service -n 200 --no-pager
journalctl -u nightly-deploy.service --since today
```

모든 출력은 `[nightly-deploy HH:MM:SS]` 접두사로 journald에 들어갑니다.

## 롤백

스크립트는 라이브 파일을 덮어쓰기 전에 `*.bak-<TS>` 사본을 남깁니다 (앱당 최근 `BACKUP_KEEP`개, 기본 5개).

```bash
# 직전 배포로 되돌리기 (mediplat 예)
ssh PROD 'ls -1t /opt/mediplat/app/mediplat.jar.bak-*' | head -1
ssh PROD '
  LATEST=$(ls -1t /opt/mediplat/app/mediplat.jar.bak-* | head -1)
  sudo mv "$LATEST" /opt/mediplat/app/mediplat.jar
  sudo systemctl restart mediplat
'
```

`/opt/deploy/archive/<TS>/` 디렉터리에는 그날 staging에서 옮겨진 원본 산출물이 그대로 보존되므로, 동일 산출물을 staging에 다시 올려 재배포할 수도 있습니다.

## 안전장치 요약

| 상황 | 동작 |
|------|------|
| `deploy.ok` 없음 | 로그만 한 줄 남기고 exit 0 (no-op) |
| staging에 산출물 없음 + 마커만 있음 | 마커만 아카이브로 옮기고 종료 |
| 동일 타이머가 이미 실행 중 | `flock`으로 차단, exit 2 |
| 라이브 디렉터리 부재 | 해당 앱만 건너뜀, WARN 로그 |
| `systemctl restart` 실패 | 비-zero exit, 마커는 그대로 두지 않고 아카이브됨 — 운영자가 journal 확인 후 수동 대응 |
| 호스트 down at 02:30 | `Persistent=true` 로 부팅 후 1회 보충 실행 |

## 트레이드오프

- staging에 올린 뒤 `touch deploy.ok`까지 해야 배포됩니다. "파일만 올리면 자동"이 아니므로 한 단계 더 필요하지만, 그만큼 실수 배포가 줄어듭니다.
- 03:00 maintenance와 별개 타이머라 한쪽이 실패해도 다른 쪽에 영향이 없습니다 (`Before=` 순서만 보장).
- 산출물은 항상 staging→archive로 이동(복사 아님)되므로 같은 파일이 다음날 다시 배포되는 일은 없습니다.
