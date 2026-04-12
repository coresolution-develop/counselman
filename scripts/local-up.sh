#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CSM_PORT="${CSM_PORT:-8081}"
MEDIPLAT_PORT="${MEDIPLAT_PORT:-8082}"
CSM_PID=""
MEDIPLAT_PID=""

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
check_port "${CSM_PORT}" "csm"
check_port "${MEDIPLAT_PORT}" "mediplat"

echo "Starting local services..."
echo "- CounselMan : http://localhost:${CSM_PORT}/csm/login"
echo "- MediPlat   : http://localhost:${MEDIPLAT_PORT}/login"
echo "- JAVA_HOME  : ${JAVA_HOME}"

start_csm
start_mediplat
monitor_processes
