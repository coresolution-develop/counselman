#!/usr/bin/env bash

set -euo pipefail

DEV_HOST="49.247.42.59"
DEV_USER="root"
TOMCAT_WEBAPPS="/usr/local/tomcat10/webapps"
MEDIPLAT_APP_DIR="/opt/mediplat/app"
CANCER_TREATMENT_APP_DIR="/opt/cancer-treatment/app"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SSH_SOCKET="/tmp/deploy-dev-ssh-$$"
SSH_OPTS=(-o ControlMaster=auto -o ControlPath="${SSH_SOCKET}" -o ControlPersist=60)

ssh_cmd() { ssh "${SSH_OPTS[@]}" "${DEV_USER}@${DEV_HOST}" "$@"; }
scp_cmd() { scp -o ControlMaster=auto -o "ControlPath=${SSH_SOCKET}" "$@"; }

cleanup_ssh() { ssh "${SSH_OPTS[@]}" -O exit "${DEV_USER}@${DEV_HOST}" 2>/dev/null || true; }
trap cleanup_ssh EXIT

echo "[1/3] Building DEV packages..."
cd "${ROOT_DIR}"
./gradlew packageDevDeploy --console=plain

WAR="${ROOT_DIR}/build/deploy/dev/csm-dev.war"
JAR="${ROOT_DIR}/build/deploy/dev/mediplat-dev.jar"
CANCER_JAR="${ROOT_DIR}/build/deploy/dev/cancer-treatment-dev.jar"

echo "[2/3] Uploading to DEV server (${DEV_HOST})..."
ssh_cmd "mkdir -p ${CANCER_TREATMENT_APP_DIR}"
scp_cmd "${WAR}" "${DEV_USER}@${DEV_HOST}:${TOMCAT_WEBAPPS}/csm.war"
scp_cmd "${JAR}" "${DEV_USER}@${DEV_HOST}:${MEDIPLAT_APP_DIR}/mediplat.jar"
scp_cmd "${CANCER_JAR}" "${DEV_USER}@${DEV_HOST}:${CANCER_TREATMENT_APP_DIR}/cancer-treatment.jar"

echo "[3/3] Restarting services..."
ssh_cmd "sudo systemctl restart mediplat && sudo systemctl restart cancer-treatment"

echo ""
echo "DEV deployment complete!"
echo "  CSM              : http://${DEV_HOST}:8081/csm  (Tomcat auto-reload)"
echo "  MediPlat         : http://${DEV_HOST}:8082"
echo "  Cancer Treatment : http://${DEV_HOST}:8083"
