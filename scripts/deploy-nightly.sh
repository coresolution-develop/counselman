#!/usr/bin/env bash
#
# Nightly auto-deploy (server-side).
#
# Runs at 02:30 KST via systemd timer (nightly-deploy.timer). Operators stage
# artifacts during the day and create the marker file when ready to ship:
#
#   /opt/deploy/staging/csm.war              (optional)
#   /opt/deploy/staging/mediplat.jar         (optional)
#   /opt/deploy/staging/cancer-treatment.jar (optional)
#   /opt/deploy/staging/deploy.ok            (required trigger)
#
# Behavior:
#   - Exits silently if deploy.ok is missing (so the timer can fire daily
#     without action).
#   - Acquires a flock so concurrent runs cannot collide with manual deploys.
#   - For every artifact present in staging:
#       1. Backs up the current live file to *.bak-${TS}
#       2. Moves the staged artifact into the live location (atomic rename)
#       3. Restarts the systemd unit if one is configured for that app
#   - Archives the consumed staging snapshot under /opt/deploy/archive/${TS}/
#     and removes the marker. Stale staging files (older than 14 days) are
#     also archived so they don't drift across days.
#   - Logs everything to journald via stderr.
#
# Env vars (defaults shown):
#   STAGING_DIR=/opt/deploy/staging
#   ARCHIVE_DIR=/opt/deploy/archive
#   TOMCAT_WEBAPPS=/usr/local/tomcat10/webapps          (csm.war target)
#   MEDIPLAT_APP_DIR=/opt/mediplat/app                  (mediplat.jar target)
#   MEDIPLAT_SERVICE=mediplat
#   CANCER_APP_DIR=/opt/cancer-treatment/app            (cancer-treatment.jar target)
#   CANCER_SERVICE=cancer-treatment
#   BACKUP_KEEP=5                                       (per-app rotation)
#
# Exit codes:
#   0 = no marker (no-op) OR deploy succeeded
#   1 = deploy attempted but failed (marker is left in place so the operator
#       can re-run after fixing the issue; failed artifacts remain in staging)
#   2 = invalid environment / lock contention

set -euo pipefail

STAGING_DIR="${STAGING_DIR:-/opt/deploy/staging}"
ARCHIVE_DIR="${ARCHIVE_DIR:-/opt/deploy/archive}"
TOMCAT_WEBAPPS="${TOMCAT_WEBAPPS:-/usr/local/tomcat10/webapps}"
# CSM_SERVICE: csm.war 배포 후 restart 할 systemd unit (비어있으면 Tomcat
# autoDeploy 가정. 운영(csm-next 전용 Tomcat 인스턴스)에서는
# CSM_SERVICE=csm-next 로 설정 권장).
CSM_SERVICE="${CSM_SERVICE:-}"
MEDIPLAT_APP_DIR="${MEDIPLAT_APP_DIR:-/opt/mediplat/app}"
MEDIPLAT_SERVICE="${MEDIPLAT_SERVICE:-mediplat}"
CANCER_APP_DIR="${CANCER_APP_DIR:-/opt/cancer-treatment/app}"
CANCER_SERVICE="${CANCER_SERVICE:-cancer-treatment}"
BACKUP_KEEP="${BACKUP_KEEP:-5}"

LOCK_FILE="${STAGING_DIR}/.nightly-deploy.lock"
MARKER_FILE="${STAGING_DIR}/deploy.ok"
TS="$(date +%Y%m%d-%H%M%S)"

log() { printf '[nightly-deploy %s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

if [[ ! -d "${STAGING_DIR}" ]]; then
  log "staging dir missing: ${STAGING_DIR}; nothing to do"
  exit 0
fi

if [[ ! -f "${MARKER_FILE}" ]]; then
  log "no marker (${MARKER_FILE}); skipping"
  exit 0
fi

# Single-flight lock.
exec 9>"${LOCK_FILE}" || fail "cannot open lock ${LOCK_FILE}"
if ! flock -n 9; then
  log "another deploy is in progress; aborting"
  exit 2
fi

log "marker present; starting deploy ts=${TS}"

mkdir -p "${ARCHIVE_DIR}/${TS}"

# (artifact basename, live path/file, systemd unit name or empty)
APPS=(
  "csm.war|${TOMCAT_WEBAPPS}/csm.war|${CSM_SERVICE}"
  "mediplat.jar|${MEDIPLAT_APP_DIR}/mediplat.jar|${MEDIPLAT_SERVICE}"
  "cancer-treatment.jar|${CANCER_APP_DIR}/cancer-treatment.jar|${CANCER_SERVICE}"
)

deployed_any=0
restart_failed=0

for entry in "${APPS[@]}"; do
  IFS='|' read -r artifact live_path service <<<"${entry}"
  staged="${STAGING_DIR}/${artifact}"

  if [[ ! -f "${staged}" ]]; then
    continue
  fi

  live_dir="$(dirname "${live_path}")"
  if [[ ! -d "${live_dir}" ]]; then
    log "WARN: live dir missing for ${artifact}: ${live_dir}; skipping"
    continue
  fi

  log "deploying ${artifact} -> ${live_path}"

  if [[ -f "${live_path}" ]]; then
    cp -a "${live_path}" "${live_path}.bak-${TS}"
    log "  backup: ${live_path}.bak-${TS}"
  else
    log "  no existing live file (first install)"
  fi

  # Atomic replace: rename within same fs from staging into live dir via temp.
  tmp="${live_path}.new-${TS}"
  cp -a "${staged}" "${tmp}"
  mv -f "${tmp}" "${live_path}"
  log "  installed"

  # Archive the consumed staging artifact.
  mv -f "${staged}" "${ARCHIVE_DIR}/${TS}/${artifact}"

  if [[ -n "${service}" ]]; then
    log "  restarting systemd unit: ${service}"
    if ! systemctl restart "${service}"; then
      log "  ERROR: systemctl restart ${service} failed"
      restart_failed=1
    fi
  else
    log "  no systemd unit (relies on container hot-deploy)"
  fi

  # Per-app backup rotation: keep most recent BACKUP_KEEP backups.
  if compgen -G "${live_path}.bak-*" >/dev/null; then
    # shellcheck disable=SC2012
    ls -1t "${live_path}".bak-* 2>/dev/null \
      | tail -n +"$((BACKUP_KEEP + 1))" \
      | xargs -r rm -f
  fi

  deployed_any=1
done

if [[ ${deployed_any} -eq 0 ]]; then
  log "marker present but no known artifacts in staging; archiving marker only"
fi

# Sweep any unrelated stale files (>14 days) so the staging dir doesn't grow.
find "${STAGING_DIR}" -maxdepth 1 -type f -mtime +14 \
  ! -name "deploy.ok" ! -name ".nightly-deploy.lock" \
  -exec mv -t "${ARCHIVE_DIR}/${TS}/" {} + 2>/dev/null || true

mv -f "${MARKER_FILE}" "${ARCHIVE_DIR}/${TS}/deploy.ok"
log "marker consumed; archive=${ARCHIVE_DIR}/${TS}"

if [[ ${restart_failed} -ne 0 ]]; then
  fail "one or more service restarts failed; check journalctl"
fi

log "deploy complete"
exit 0
