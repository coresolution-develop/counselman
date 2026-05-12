#!/usr/bin/env bash

set -euo pipefail

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export LOCAL_UP_FORCE_PROFILE="prod"
export LOCAL_UP_FORCE_DB_HOST="csm.sosyge.net"
export CANCER_TREATMENT_SQL_INIT_MODE="${CANCER_TREATMENT_SQL_INIT_MODE:-never}"

echo "[prod-db] Spring profile forced to: ${LOCAL_UP_FORCE_PROFILE}"
echo "[prod-db] DB host overridden to: ${LOCAL_UP_FORCE_DB_HOST}"
echo "[prod-db] WARNING: Connected to production DB. Do not run destructive operations."

exec "${SCRIPTS_DIR}/local-up.sh" "$@"
