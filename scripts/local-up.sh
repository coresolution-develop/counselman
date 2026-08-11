#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CSM_PORT="${CSM_PORT:-8081}"
MEDIPLAT_PORT="${MEDIPLAT_PORT:-8082}"
CANCER_TREATMENT_PORT="${CANCER_TREATMENT_PORT:-8083}"
LINKS_PORT="${LINKS_PORT:-8085}"
CSM_PID=""
MEDIPLAT_PID=""
CANCER_TREATMENT_PID=""
LINKS_PID=""

load_local_env() {
  local env_file="${ROOT_DIR}/.env.local"
  if [[ ! -f "${env_file}" ]]; then
    return
  fi

  echo "[env] loading ${env_file}"
  set -a
  # shellcheck disable=SC1090
  source "${env_file}"
  set +a
}

init_local_defaults() {
  if [[ -n "${LOCAL_UP_FORCE_PROFILE:-}" ]]; then
    export SPRING_PROFILES_ACTIVE="${LOCAL_UP_FORCE_PROFILE}"
  else
    export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
  fi

  export DEV_DB_HOST="${DEV_DB_HOST:-49.247.42.59}"
  if [[ -n "${LOCAL_UP_FORCE_DB_HOST:-}" ]]; then
    export DEV_DB_HOST="${LOCAL_UP_FORCE_DB_HOST}"
    unset SPRING_DATASOURCE_URL
  fi
  export DEV_DB_PORT="${DEV_DB_PORT:-3306}"
  export DEV_DB_NAME="${DEV_DB_NAME:-csm}"
  export DEV_DB_USERNAME="${DEV_DB_USERNAME:-csdev}"
  # 비밀번호 기본값은 두지 않는다. .env.local(gitignore 대상)에 DEV_DB_PASSWORD를 정의할 것.
  export DEV_DB_PASSWORD="${DEV_DB_PASSWORD:-}"

  # 로컬 실행 시에도 기본 DB는 DEV DB를 사용한다. 필요하면 SPRING_DATASOURCE_*로 명시 오버라이드한다.
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${DEV_DB_HOST}:${DEV_DB_PORT}/${DEV_DB_NAME}?serverTimezone=Asia/Seoul&useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${DEV_DB_USERNAME}}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${DEV_DB_PASSWORD}}"

  export MEDIPLAT_DATASOURCE_URL="${MEDIPLAT_DATASOURCE_URL:-${SPRING_DATASOURCE_URL}}"
  export MEDIPLAT_DATASOURCE_USERNAME="${MEDIPLAT_DATASOURCE_USERNAME:-${SPRING_DATASOURCE_USERNAME}}"
  export MEDIPLAT_DATASOURCE_PASSWORD="${MEDIPLAT_DATASOURCE_PASSWORD:-${SPRING_DATASOURCE_PASSWORD}}"
  export CANCER_TREATMENT_DATASOURCE_URL="${CANCER_TREATMENT_DATASOURCE_URL:-${SPRING_DATASOURCE_URL}}"
  export CANCER_TREATMENT_DATASOURCE_USERNAME="${CANCER_TREATMENT_DATASOURCE_USERNAME:-${SPRING_DATASOURCE_USERNAME}}"
  export CANCER_TREATMENT_DATASOURCE_PASSWORD="${CANCER_TREATMENT_DATASOURCE_PASSWORD:-${SPRING_DATASOURCE_PASSWORD}}"
  export CANCER_TREATMENT_SQL_INIT_MODE="${CANCER_TREATMENT_SQL_INIT_MODE:-always}"
  export CANCER_TREATMENT_BASE_URL="${CANCER_TREATMENT_BASE_URL:-http://localhost:${CANCER_TREATMENT_PORT}/cancer-treatment}"

  # 빈 로컬 DB에서 csm 기동 실패를 유발하는 bootstrap 기본값 OFF
  export PLATFORM_ADMIN_BOOTSTRAP_ENABLED="${PLATFORM_ADMIN_BOOTSTRAP_ENABLED:-false}"
  export PLATFORM_ADMIN_SYNC_PASSWORD_ON_STARTUP="${PLATFORM_ADMIN_SYNC_PASSWORD_ON_STARTUP:-false}"

  # 로컬은 http라 Secure 쿠키가 안 실린다 → hub "이 기기 기억" 기능 로컬 테스트용 비활성
  export HUB_REMEMBER_COOKIE_SECURE="${HUB_REMEMBER_COOKIE_SECURE:-false}"

  # OPENAI_API_KEY가 있으면 로컬 MediPlat 뉴스레터 AI 추천을 자동 활성화
  if [[ -n "${OPENAI_API_KEY:-}" ]]; then
    export MEDIPLAT_NEWSLETTER_AI_ENABLED="${MEDIPLAT_NEWSLETTER_AI_ENABLED:-true}"
  fi
}

# 시크릿 평문 기본값을 properties에서 제거했으므로, 누락 시 Spring 스택트레이스 대신
# 어떤 키가 없는지 먼저 알려준다. 값은 .env.local 에 정의한다(.env.example 참고).
require_local_env() {
  local missing=()
  local key
  for key in \
    DEV_DB_PASSWORD \
    LOGIN_AES_KEY \
    SPRING_MAIL_PASSWORD \
    KAKAO_CLIENT_ID \
    KAKAO_CLIENT_SECRET \
    COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET \
    PLATFORM_ADMIN_PASSWORD
  do
    if [[ -z "${!key:-}" ]]; then
      missing+=("${key}")
    fi
  done

  # dev/prod 프로파일에서만 Bizppurio 프로퍼티가 로드된다(local 프로파일엔 없음).
  case "${SPRING_PROFILES_ACTIVE}" in
    prod*) local prefix="BIZPPURIO_PROD" ;;
    dev*)  local prefix="BIZPPURIO_DEV" ;;
    *)     local prefix="" ;;
  esac
  if [[ -n "${prefix}" ]]; then
    for key in "${prefix}_ACCOUNT" "${prefix}_USERNAME" "${prefix}_PASSWORD"; do
      if [[ -z "${!key:-}" ]]; then
        missing+=("${key}")
      fi
    done
  fi

  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "[FAIL] 다음 환경변수가 비어 있습니다: ${missing[*]}" >&2
    echo "       ${ROOT_DIR}/.env.local 에 정의하세요. 키 목록은 .env.example 참고." >&2
    exit 2
  fi
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

kill_port() {
  local port="$1"
  local name="$2"
  local pids
  pids="$(lsof -ti TCP:"${port}" 2>/dev/null || true)"
  if [[ -n "${pids}" ]]; then
    echo "[${name}] killing existing process on port ${port} (PID: ${pids})"
    echo "${pids}" | xargs kill -9 2>/dev/null || true
    sleep 1
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
  if [[ -n "${CANCER_TREATMENT_PID}" ]] && kill -0 "${CANCER_TREATMENT_PID}" >/dev/null 2>&1; then
    kill "${CANCER_TREATMENT_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${LINKS_PID}" ]] && kill -0 "${LINKS_PID}" >/dev/null 2>&1; then
    kill "${LINKS_PID}" >/dev/null 2>&1 || true
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

start_cancer_treatment() {
  (
    cd "${ROOT_DIR}/cancer-treatment"
    exec ../gradlew bootRun --console=plain
  ) \
    > >(sed -u 's/^/[cancer-treatment] /') \
    2> >(sed -u 's/^/[cancer-treatment] /' >&2) &
  CANCER_TREATMENT_PID=$!
}

start_links() {
  (
    cd "${ROOT_DIR}/links"
    exec ../gradlew bootRun --console=plain
  ) \
    > >(sed -u 's/^/[links] /') \
    2> >(sed -u 's/^/[links] /' >&2) &
  LINKS_PID=$!
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

    if ! kill -0 "${CANCER_TREATMENT_PID}" >/dev/null 2>&1; then
      wait "${CANCER_TREATMENT_PID}"
      return $?
    fi

    if ! kill -0 "${LINKS_PID}" >/dev/null 2>&1; then
      wait "${LINKS_PID}"
      return $?
    fi

    sleep 1
  done
}

trap cleanup EXIT INT TERM

ensure_java17
load_local_env
init_local_defaults
require_local_env
kill_port "${CSM_PORT}" "csm"
kill_port "${MEDIPLAT_PORT}" "mediplat"
kill_port "${CANCER_TREATMENT_PORT}" "cancer-treatment"
kill_port "${LINKS_PORT}" "links"

echo "Starting local services..."
echo "- CounselMan       : http://localhost:${CSM_PORT}/csm/login"
echo "- MediPlat         : http://localhost:${MEDIPLAT_PORT}/login"
echo "- Cancer Treatment : http://localhost:${CANCER_TREATMENT_PORT}/login-required"
echo "- Links (hub)      : http://localhost:${LINKS_PORT}/links"
echo "- JAVA_HOME        : ${JAVA_HOME}"
echo "- SPRING_PROFILE   : ${SPRING_PROFILES_ACTIVE}"
echo "- DB_URL           : ${SPRING_DATASOURCE_URL}"

start_csm
start_mediplat
start_cancer_treatment
start_links
monitor_processes
