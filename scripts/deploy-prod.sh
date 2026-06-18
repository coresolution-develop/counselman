#!/usr/bin/env bash
#
# PROD deployment script.
#
# This script is intentionally cautious. It refuses to run until:
#   - PROD_HOST, PROD_USER are set (export them or define DEFAULT_* below)
#   - `prod-preflight.sh` reports failures=0
#   - the operator types the exact PROD host as confirmation
#
# Run with --dry-run to print every remote command without executing.
#
# Usage:
#   PROD_HOST=<prod-host>            \
#   PROD_USER=root                   \
#   SPRING_PROFILES_ACTIVE=prod      \
#   LOGIN_AES_KEY=...                \
#   MEDIPLAT_SSO_SHARED_SECRET=...   \
#   COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET=... \
#   COUNSELMAN_BASE_URL=...          \
#   MEDIPLAT_PLATFORM_BASE_URL=...   \
#   SPRING_DATASOURCE_URL=...        \
#   SPRING_DATASOURCE_USERNAME=...   \
#   SPRING_DATASOURCE_PASSWORD=...   \
#   BIZPPURIO_PROD_ACCOUNT=...       \
#   BIZPPURIO_PROD_USERNAME=...      \
#   BIZPPURIO_PROD_PASSWORD=...      \
#   ./scripts/deploy-prod.sh [--dry-run] [--skip-preflight] [--csm-only|--mediplat-only]

set -euo pipefail

# ─── Defaults (override via env) ──────────────────────────────────────────
PROD_HOST="${PROD_HOST:-}"
PROD_USER="${PROD_USER:-root}"
TOMCAT_WEBAPPS="${TOMCAT_WEBAPPS:-/usr/local/tomcat10/webapps}"
MEDIPLAT_APP_DIR="${MEDIPLAT_APP_DIR:-/opt/csm-next/app}"
MEDIPLAT_SERVICE="${MEDIPLAT_SERVICE:-mediplat-next}"

DRY_RUN=0
SKIP_PREFLIGHT=0
DEPLOY_CSM=1
DEPLOY_MEDIPLAT=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"

# ─── Arg parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)         DRY_RUN=1 ;;
    --skip-preflight)  SKIP_PREFLIGHT=1 ;;
    --csm-only)        DEPLOY_MEDIPLAT=0 ;;
    --mediplat-only)   DEPLOY_CSM=0 ;;
    -h|--help)
      sed -n '2,30p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      exit 2
      ;;
  esac
  shift
done

# ─── Required env ─────────────────────────────────────────────────────────
if [[ -z "${PROD_HOST}" ]]; then
  echo "[FAIL] PROD_HOST is not set. Export PROD_HOST=<prod-host> and re-run." >&2
  exit 2
fi

echo "=== PROD deployment plan ==="
echo "  Host           : ${PROD_USER}@${PROD_HOST}"
echo "  Tomcat webapps : ${TOMCAT_WEBAPPS}"
echo "  MediPlat dir   : ${MEDIPLAT_APP_DIR}"
echo "  MediPlat svc   : ${MEDIPLAT_SERVICE}"
echo "  Deploy CSM     : $([[ ${DEPLOY_CSM} -eq 1 ]] && echo yes || echo no)"
echo "  Deploy MediPlat: $([[ ${DEPLOY_MEDIPLAT} -eq 1 ]] && echo yes || echo no)"
echo "  Dry-run        : $([[ ${DRY_RUN} -eq 1 ]] && echo yes || echo no)"
echo

# ─── Confirmation gate ────────────────────────────────────────────────────
if [[ ${DRY_RUN} -eq 0 ]]; then
  read -r -p "This will modify PRODUCTION. Type the host (${PROD_HOST}) to continue: " CONFIRM
  if [[ "${CONFIRM}" != "${PROD_HOST}" ]]; then
    echo "[ABORT] host mismatch" >&2
    exit 1
  fi
fi

# ─── Preflight ────────────────────────────────────────────────────────────
if [[ ${SKIP_PREFLIGHT} -eq 0 ]]; then
  echo "[1/5] Running prod-preflight..."
  "${ROOT_DIR}/scripts/prod-preflight.sh"
else
  echo "[1/5] Preflight skipped (--skip-preflight)"
fi

# ─── Build ────────────────────────────────────────────────────────────────
echo "[2/5] Building PROD packages..."
cd "${ROOT_DIR}"
./gradlew packageProdDeploy --console=plain

WAR="${ROOT_DIR}/build/deploy/prod/csm-prod.war"
JAR="${ROOT_DIR}/build/deploy/prod/mediplat-prod.jar"

if [[ ${DEPLOY_CSM} -eq 1 && ! -f "${WAR}" ]]; then
  echo "[FAIL] missing build artifact: ${WAR}" >&2
  exit 1
fi
if [[ ${DEPLOY_MEDIPLAT} -eq 1 && ! -f "${JAR}" ]]; then
  echo "[FAIL] missing build artifact: ${JAR}" >&2
  exit 1
fi

# ─── SSH multiplex ────────────────────────────────────────────────────────
SSH_SOCKET="/tmp/deploy-prod-ssh-$$"
SSH_OPTS=(-o ControlMaster=auto -o ControlPath="${SSH_SOCKET}" -o ControlPersist=60)
cleanup_ssh() { ssh "${SSH_OPTS[@]}" -O exit "${PROD_USER}@${PROD_HOST}" 2>/dev/null || true; }
trap cleanup_ssh EXIT

run_remote() {
  if [[ ${DRY_RUN} -eq 1 ]]; then
    echo "[dry-run] ssh ${PROD_USER}@${PROD_HOST} -- $*"
  else
    ssh "${SSH_OPTS[@]}" "${PROD_USER}@${PROD_HOST}" "$@"
  fi
}

put_file() {
  local src="$1" dest="$2"
  if [[ ${DRY_RUN} -eq 1 ]]; then
    echo "[dry-run] scp ${src} ${PROD_USER}@${PROD_HOST}:${dest}"
  else
    scp -o ControlMaster=auto -o "ControlPath=${SSH_SOCKET}" \
        "${src}" "${PROD_USER}@${PROD_HOST}:${dest}"
  fi
}

# ─── Backup current artifacts ────────────────────────────────────────────
echo "[3/5] Backing up current PROD artifacts (timestamp=${TS})..."
if [[ ${DEPLOY_CSM} -eq 1 ]]; then
  run_remote "test -f ${TOMCAT_WEBAPPS}/csm.war && cp -a ${TOMCAT_WEBAPPS}/csm.war ${TOMCAT_WEBAPPS}/csm.war.bak-${TS} || echo '[WARN] no existing csm.war to back up'"
fi
if [[ ${DEPLOY_MEDIPLAT} -eq 1 ]]; then
  run_remote "test -f ${MEDIPLAT_APP_DIR}/mediplat.jar && cp -a ${MEDIPLAT_APP_DIR}/mediplat.jar ${MEDIPLAT_APP_DIR}/mediplat.jar.bak-${TS} || echo '[WARN] no existing mediplat.jar to back up'"
fi

# ─── Upload ──────────────────────────────────────────────────────────────
echo "[4/5] Uploading artifacts..."
if [[ ${DEPLOY_CSM} -eq 1 ]]; then
  put_file "${WAR}" "${TOMCAT_WEBAPPS}/csm.war"
fi
if [[ ${DEPLOY_MEDIPLAT} -eq 1 ]]; then
  put_file "${JAR}" "${MEDIPLAT_APP_DIR}/mediplat.jar"
fi

# ─── Restart ─────────────────────────────────────────────────────────────
echo "[5/5] Restarting services..."
if [[ ${DEPLOY_MEDIPLAT} -eq 1 ]]; then
  run_remote "sudo systemctl restart ${MEDIPLAT_SERVICE}"
fi
# Tomcat hot-deploys the WAR on its own; no service restart issued here.

echo
echo "PROD deployment complete."
echo "  Rollback CSM     : ssh ${PROD_USER}@${PROD_HOST} 'mv ${TOMCAT_WEBAPPS}/csm.war.bak-${TS} ${TOMCAT_WEBAPPS}/csm.war'"
echo "  Rollback MediPlat: ssh ${PROD_USER}@${PROD_HOST} 'mv ${MEDIPLAT_APP_DIR}/mediplat.jar.bak-${TS} ${MEDIPLAT_APP_DIR}/mediplat.jar && sudo systemctl restart ${MEDIPLAT_SERVICE}'"
