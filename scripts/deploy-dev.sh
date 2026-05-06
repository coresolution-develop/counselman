#!/usr/bin/env bash

set -euo pipefail

DEV_HOST="49.247.42.59"
DEV_USER="root"
TOMCAT_WEBAPPS="/usr/local/tomcat10/webapps"
MEDIPLAT_APP_DIR="/opt/mediplat/app"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[1/3] Building DEV packages..."
cd "${ROOT_DIR}"
./gradlew packageDevDeploy --console=plain

WAR="${ROOT_DIR}/build/deploy/dev/csm-dev.war"
JAR="${ROOT_DIR}/build/deploy/dev/mediplat-dev.jar"

echo "[2/3] Uploading to DEV server (${DEV_HOST})..."
scp "${WAR}" "${DEV_USER}@${DEV_HOST}:${TOMCAT_WEBAPPS}/csm.war"
scp "${JAR}" "${DEV_USER}@${DEV_HOST}:${MEDIPLAT_APP_DIR}/mediplat.jar"

echo "[3/3] Restarting MediPlat..."
ssh "${DEV_USER}@${DEV_HOST}" "sudo systemctl restart mediplat"

echo ""
echo "DEV deployment complete!"
echo "  CSM      : http://${DEV_HOST}:8081/csm  (Tomcat auto-reload)"
echo "  MediPlat : http://${DEV_HOST}:8082"
