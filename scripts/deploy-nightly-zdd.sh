#!/usr/bin/env bash
# =============================================================================
# deploy-nightly-zdd.sh — 무중단(zero-downtime) 야간 배포  (⚠️ DRAFT / 미적용)
# -----------------------------------------------------------------------------
# 기준: 서버 실물 /opt/csm-next/deploy/scripts/deploy-nightly.sh (165줄,
#       sha256 05eb1f7eb2ebdc2f2882e36e3b374352ce8d8627a9e60ea436be11ca2fd754d0)
#       를 베이스로, **csm 처리 구간만** A′ 블루-그린(conf-swap)으로 치환한 신규 산출물.
#       저장소 deploy-nightly.sh 는 수정하지 않는다.
#
# ‼️‼️ 배치 전 필수 — 비-csm 구간 이식(개선③)
#   아래 mediplat / cancer-treatment 블록은 **저장소 사본 기반**이며, 서버 실물
#   (sha256 05eb1f7e…754d0)과 다를 수 있다. 자동 동기화가 없으므로, 운영자는 배치 전
#   서버 실물의 mediplat/cancer 처리 구간(및 env 기본값/경로)을 **그대로 이식**해
#   본 파일의 동일 구간과 1:1 일치시킨 뒤 배치할 것. csm 블록과 헬퍼만 신규다.
#
# 보존(원본과 동일): deploy.ok 마커 / flock / 백업(*.bak-<TS>) / archive 이동 /
#   회전(BACKUP_KEEP) / exit 코드(0=no-op·성공, 1=실패, 2=환경·락).
#
# csm 치환: 기존 `systemctl restart csm-next`(단일 즉시 재시작=다운타임) →
#   b 기동(새 WAR) → 헬스 → 라우팅 b → a 드레인 → a 갱신·재기동 → 라우팅 a → b 드레인 → b 정지.
#   end-on-a: 18081 가 최종 활성(새 WAR), b 는 내림(메모리 회수) → systemd/03:00 정합.
# =============================================================================
set -euo pipefail

# ── env (원본과 동일 + 서버 확정값) ─────────────────────────────────────────
STAGING_DIR="${STAGING_DIR:-/opt/csm-next/deploy/staging}"
ARCHIVE_DIR="${ARCHIVE_DIR:-/opt/csm-next/deploy/archive}"
TOMCAT_WEBAPPS="${TOMCAT_WEBAPPS:-/opt/csm-next/tomcat/webapps}"   # a(csm-next) webapps
CSM_SERVICE="${CSM_SERVICE:-csm-next}"
MEDIPLAT_APP_DIR="${MEDIPLAT_APP_DIR:-/opt/csm-next/app}"
MEDIPLAT_SERVICE="${MEDIPLAT_SERVICE:-mediplat}"
CANCER_APP_DIR="${CANCER_APP_DIR:-/opt/cancer-treatment/app}"
CANCER_SERVICE="${CANCER_SERVICE:-cancer-treatment}"
BACKUP_KEEP="${BACKUP_KEEP:-5}"

LOCK_FILE="${STAGING_DIR}/.nightly-deploy.lock"
MARKER_FILE="${STAGING_DIR}/deploy.ok"
TS="$(date +%Y%m%d-%H%M%S)"

log()  { printf '[zdd-deploy %s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

# 공유 헬퍼 (같은 디렉터리에 함께 배치)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-csm-bluegreen.sh
. "${SCRIPT_DIR}/lib-csm-bluegreen.sh"

# ── 가드 (원본과 동일) ──────────────────────────────────────────────────────
[ -d "${STAGING_DIR}" ] || { log "staging dir 없음: ${STAGING_DIR}; nothing to do"; exit 0; }
[ -f "${MARKER_FILE}" ] || { log "no marker (${MARKER_FILE}); skipping"; exit 0; }

exec 9>"${LOCK_FILE}" || fail "cannot open lock ${LOCK_FILE}"
if ! flock -n 9; then log "another deploy in progress; aborting"; exit 2; fi

log "marker present; starting zero-downtime deploy ts=${TS}"
mkdir -p "${ARCHIVE_DIR}/${TS}"

# =============================================================================
# csm — A′ 블루-그린 (conf-swap). 실패 시 marker 유지한 채 exit 1.
# =============================================================================
deploy_csm_bluegreen() {
  local staged="${STAGING_DIR}/csm.war"
  local live="${TOMCAT_WEBAPPS}/csm.war"   # a(csm-next)
  [ -f "${staged}" ] || { log "csm: staged 없음 → skip"; return 0; }

  log "csm: A′ 블루-그린 시작 (conf-swap)"

  # 0) 현재 live(a) 백업 — 원본 로직 보존
  if [ -f "${live}" ]; then cp -a "${live}" "${live}.bak-${TS}"; log "  backup: ${live}.bak-${TS}"; fi

  # 1) 새 WAR → b webapps(정지 보장 후 클린), b 기동
  if ! copy_war_to_b "${staged}"; then log "  ERROR: b webapps 준비 실패(정지 미확인) → marker 유지, exit 1"; return 1; fi
  if ! start_b; then log "  ERROR: b 기동 실패"; stop_b; return 1; fi

  # 2) b 헬스 폴링 — 실패 시 b 정지, marker 유지, abort
  if ! wait_healthy "${HEALTH_B}" "${HEALTH_TIMEOUT}"; then
    log "  ERROR: b 헬스체크 실패(${HEALTH_TIMEOUT}s) → b 정지, marker 유지, exit 1"
    stop_b; return 1
  fi
  log "  b(${WORKER_B_PORT}) 정상"

  # 2.5) [버그1] 버전 대조 — b 가 새 WAR 로 떴는지 확인(불일치 시 하드 실패)
  vrc=0; verify_b_version "${staged}" || vrc=$?
  if [ "${vrc}" -eq 1 ]; then log "  ERROR: b 버전 불일치 → b 정지, marker 유지, exit 1"; stop_b; return 1; fi

  # 3) 트래픽 b 로 전환(conf-swap) → a 드레인(활성연결 0)
  if ! route_to b; then log "  ERROR: b 라우팅 전환 실패 → b 정지, exit 1"; stop_b; return 1; fi
  wait_drained "${WORKER_A_PORT}"

  # 4) 새 WAR → a 반영 후 csm-next 재기동 (b 가 트래픽 처리 → a 무중단 갱신)
  #    autoDeploy=true 환경 → mv 즉시 redeploy 가능하므로 명시적 restart 로 확정.
  #    (권고: 전환 기간 csm-next server.xml autoDeploy=false)
  cp -a "${staged}" "${live}.new-${TS}"
  mv -f "${live}.new-${TS}" "${live}"
  log "  a 새 WAR 반영 → ${CSM_SERVICE} 재기동"
  if ! systemctl restart "${CSM_SERVICE}"; then
    log "  ERROR: ${CSM_SERVICE} 재기동 실패 (라우팅 b 유지 = 서비스 지속) → exit 1"
    return 1
  fi

  # 5) a 헬스 — 실패 시 라우팅 b 유지(서비스 b 지속), abort
  if ! wait_healthy "${HEALTH_A}" "${HEALTH_TIMEOUT}"; then
    log "  ERROR: a 헬스 실패 → 라우팅 b 유지한 채 exit 1 (서비스 b 로 지속, 운영자 확인)"
    return 1
  fi
  log "  a(${WORKER_A_PORT}) 정상"

  # 6) 트래픽 a 복귀 → b 드레인 → b 정지(메모리 회수)
  if ! route_to a; then log "  WARN: a 라우팅 복귀 실패 → b 유지한 채 exit 1 (운영자 확인)"; return 1; fi
  wait_drained "${WORKER_B_PORT}"
  stop_b
  log "  csm 무중단 배포 완료 (end-on-a / ${WORKER_A_PORT} 활성, b 정지)"

  # 7) staged archive + 백업 회전 — 원본 로직 보존
  mv -f "${staged}" "${ARCHIVE_DIR}/${TS}/csm.war"
  if compgen -G "${live}.bak-*" >/dev/null; then
    ls -1t "${live}".bak-* 2>/dev/null | tail -n +"$((BACKUP_KEEP + 1))" | xargs -r rm -f
  fi
  return 0
}

# csm 를 먼저 처리. 실패 시 marker/staged 보존한 채 즉시 종료(요구사항).
if ! deploy_csm_bluegreen; then
  fail "csm 무중단 배포 실패 — marker(${MARKER_FILE}) 유지, staged 보존. journal 확인 후 재시도."
fi

# =============================================================================
# mediplat / cancer-treatment — 원본 로직 그대로 (단순 교체 + restart)
#   ‼️ 위 상단 경고 참조: 배치 전 서버 실물(sha256 05eb1f7e…)의 동일 구간으로 이식·대조할 것.
# =============================================================================
APPS=(
  "mediplat.jar|${MEDIPLAT_APP_DIR}/mediplat.jar|${MEDIPLAT_SERVICE}"
  "cancer-treatment.jar|${CANCER_APP_DIR}/cancer-treatment.jar|${CANCER_SERVICE}"
)

restart_failed=0
for entry in "${APPS[@]}"; do
  IFS='|' read -r artifact live_path service <<<"${entry}"
  staged="${STAGING_DIR}/${artifact}"
  [ -f "${staged}" ] || continue

  live_dir="$(dirname "${live_path}")"
  if [ ! -d "${live_dir}" ]; then log "WARN: live dir 없음 ${live_dir}; skip ${artifact}"; continue; fi

  log "deploying ${artifact} -> ${live_path}"
  if [ -f "${live_path}" ]; then cp -a "${live_path}" "${live_path}.bak-${TS}"; log "  backup: ${live_path}.bak-${TS}"; fi

  tmp="${live_path}.new-${TS}"
  cp -a "${staged}" "${tmp}"
  mv -f "${tmp}" "${live_path}"
  mv -f "${staged}" "${ARCHIVE_DIR}/${TS}/${artifact}"
  log "  installed"

  if [ -n "${service}" ]; then
    log "  restarting ${service}"
    systemctl restart "${service}" || { log "  ERROR: restart ${service} 실패"; restart_failed=1; }
  fi

  if compgen -G "${live_path}.bak-*" >/dev/null; then
    ls -1t "${live_path}".bak-* 2>/dev/null | tail -n +"$((BACKUP_KEEP + 1))" | xargs -r rm -f
  fi
done

# ── 마커 소비 + stale 청소 + 종료(원본과 동일) ──────────────────────────────
find "${STAGING_DIR}" -maxdepth 1 -type f -mtime +14 \
  ! -name "deploy.ok" ! -name ".nightly-deploy.lock" \
  -exec mv -t "${ARCHIVE_DIR}/${TS}/" {} + 2>/dev/null || true

mv -f "${MARKER_FILE}" "${ARCHIVE_DIR}/${TS}/deploy.ok"
log "marker consumed; archive=${ARCHIVE_DIR}/${TS}"

[ ${restart_failed} -eq 0 ] || fail "mediplat/cancer 재시작 일부 실패; journalctl 확인"
log "deploy complete"
exit 0
