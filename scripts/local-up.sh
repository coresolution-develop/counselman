#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CSM_PORT="${CSM_PORT:-8081}"
MEDIPLAT_PORT="${MEDIPLAT_PORT:-8082}"
CSM_PID=""
MEDIPLAT_PID=""

init_local_defaults() {
  export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

  export LOCAL_DB_HOST="${LOCAL_DB_HOST:-127.0.0.1}"
  export LOCAL_DB_PORT="${LOCAL_DB_PORT:-3306}"
  export LOCAL_DB_NAME="${LOCAL_DB_NAME:-csm}"
  export LOCAL_DB_USERNAME="${LOCAL_DB_USERNAME:-root}"
  export LOCAL_DB_PASSWORD="${LOCAL_DB_PASSWORD:-core0220!!}"

  # csm/mediplat 모두 동일한 로컬 DB를 기본으로 사용
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${LOCAL_DB_HOST}:${LOCAL_DB_PORT}/${LOCAL_DB_NAME}?serverTimezone=Asia/Seoul&useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${LOCAL_DB_USERNAME}}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${LOCAL_DB_PASSWORD}}"

  export MEDIPLAT_DATASOURCE_URL="${MEDIPLAT_DATASOURCE_URL:-${SPRING_DATASOURCE_URL}}"
  export MEDIPLAT_DATASOURCE_USERNAME="${MEDIPLAT_DATASOURCE_USERNAME:-${SPRING_DATASOURCE_USERNAME}}"
  export MEDIPLAT_DATASOURCE_PASSWORD="${MEDIPLAT_DATASOURCE_PASSWORD:-${SPRING_DATASOURCE_PASSWORD}}"

  # 빈 로컬 DB에서 csm 기동 실패를 유발하는 bootstrap 기본값 OFF
  export PLATFORM_ADMIN_BOOTSTRAP_ENABLED="${PLATFORM_ADMIN_BOOTSTRAP_ENABLED:-false}"
  export PLATFORM_ADMIN_SYNC_PASSWORD_ON_STARTUP="${PLATFORM_ADMIN_SYNC_PASSWORD_ON_STARTUP:-false}"
}

ensure_java17() {
  local java_home_candidate=""

  if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "${JAVA_HOME}/bin/java" ]]; then
    local current_major
    current_major="$("${JAVA_HOME}/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)"
    if [[ "${current_major}" == "17" || "${current_major}" == "18" || "${current_major}" == "19" || "${current_major}" == "20" || "${current_major}" == "21" ]]; then
      export PATH="${JAVA_HOME}/bin:${PATH}"
      return
    fi
  fi

  if [[ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]]; then
    java_home_candidate="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
    java_home_candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  fi

  if [[ -z "${java_home_candidate}" || ! -x "${java_home_candidate}/bin/java" ]]; then
    echo "[java] Java 17 not found. Install OpenJDK 17 first."
    exit 1
  fi

  export JAVA_HOME="${java_home_candidate}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
}

check_port() {
  local port="$1"
  local name="$2"
  if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[${name}] port ${port} is already in use."
    echo "Stop the existing process first, then run this script again."
    exit 1
  fi
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  if [[ -n "${CSM_PID}" ]] && kill -0 "${CSM_PID}" >/dev/null 2>&1; then
    kill "${CSM_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${MEDIPLAT_PID}" ]] && kill -0 "${MEDIPLAT_PID}" >/dev/null 2>&1; then
    kill "${MEDIPLAT_PID}" >/dev/null 2>&1 || true
  fi

  wait >/dev/null 2>&1 || true
  exit "${exit_code}"
}

start_csm() {
  (
    cd "${ROOT_DIR}"
    exec ./gradlew bootRun --console=plain
  ) \
    > >(sed -u 's/^/[csm] /') \
    2> >(sed -u 's/^/[csm] /' >&2) &
  CSM_PID=$!
}

start_mediplat() {
  (
    cd "${ROOT_DIR}/mediplat"
    exec ../gradlew bootRun --console=plain
  ) \
    > >(sed -u 's/^/[mediplat] /') \
    2> >(sed -u 's/^/[mediplat] /' >&2) &
  MEDIPLAT_PID=$!
}

monitor_processes() {
  while true; do
    if ! kill -0 "${CSM_PID}" >/dev/null 2>&1; then
      wait "${CSM_PID}"
      return $?
    fi

    if ! kill -0 "${MEDIPLAT_PID}" >/dev/null 2>&1; then
      wait "${MEDIPLAT_PID}"
      return $?
    fi

    sleep 1
  done
}

trap cleanup EXIT INT TERM

ensure_java17
init_local_defaults
check_port "${CSM_PORT}" "csm"
check_port "${MEDIPLAT_PORT}" "mediplat"

echo "Starting local services..."
echo "- CounselMan : http://localhost:${CSM_PORT}/csm/login"
echo "- MediPlat   : http://localhost:${MEDIPLAT_PORT}/login"
echo "- JAVA_HOME  : ${JAVA_HOME}"
echo "- SPRING_PROFILE : ${SPRING_PROFILES_ACTIVE}"
echo "- DB_URL      : ${SPRING_DATASOURCE_URL}"

start_csm
start_mediplat
monitor_processes
