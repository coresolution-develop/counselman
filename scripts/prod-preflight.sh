#!/usr/bin/env bash

set -euo pipefail

failures=0
warnings=0

print_header() {
  printf '\n== %s ==\n' "$1"
}

ok() {
  printf '[OK] %s\n' "$1"
}

warn() {
  warnings=$((warnings + 1))
  printf '[WARN] %s\n' "$1"
}

fail() {
  failures=$((failures + 1))
  printf '[FAIL] %s\n' "$1"
}

is_set() {
  local name="$1"
  [[ -n "${!name:-}" ]]
}

require_env() {
  local name="$1"
  if is_set "$name"; then
    ok "$name is set"
  else
    fail "$name is missing"
  fi
}

warn_if_unset() {
  local name="$1"
  if is_set "$name"; then
    ok "$name is set"
  else
    warn "$name is not set"
  fi
}

print_header "Profile"
if [[ "${SPRING_PROFILES_ACTIVE:-}" == "prod" ]]; then
  ok "SPRING_PROFILES_ACTIVE=prod"
else
  fail "SPRING_PROFILES_ACTIVE must be prod, current='${SPRING_PROFILES_ACTIVE:-<unset>}'"
fi

print_header "Required Secrets"
require_env LOGIN_AES_KEY
if is_set LOGIN_AES_KEY; then
  if [[ "${#LOGIN_AES_KEY}" -lt 16 ]]; then
    fail "LOGIN_AES_KEY must be at least 16 characters"
  else
    ok "LOGIN_AES_KEY length is valid"
  fi
fi

require_env MEDIPLAT_SSO_SHARED_SECRET
require_env COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET
if is_set MEDIPLAT_SSO_SHARED_SECRET && is_set COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET; then
  if [[ "$MEDIPLAT_SSO_SHARED_SECRET" == "$COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET" ]]; then
    ok "SSO secrets match"
  else
    fail "MEDIPLAT_SSO_SHARED_SECRET and COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET do not match"
  fi
  if [[ "$MEDIPLAT_SSO_SHARED_SECRET" == "change-me" ]]; then
    fail "SSO secret is still the default value 'change-me'"
  fi
fi

print_header "Service URLs"
require_env COUNSELMAN_BASE_URL
require_env MEDIPLAT_PLATFORM_BASE_URL
if [[ "${COUNSELMAN_BASE_URL:-}" == *localhost* || "${COUNSELMAN_BASE_URL:-}" == *127.0.0.1* ]]; then
  fail "COUNSELMAN_BASE_URL points to a loopback host"
fi
if [[ "${MEDIPLAT_PLATFORM_BASE_URL:-}" == *localhost* || "${MEDIPLAT_PLATFORM_BASE_URL:-}" == *127.0.0.1* ]]; then
  fail "MEDIPLAT_PLATFORM_BASE_URL points to a loopback host"
fi
if is_set COUNSELMAN_BASE_URL && [[ "${COUNSELMAN_BASE_URL}" != */csm ]]; then
  warn "COUNSELMAN_BASE_URL usually should end with /csm"
fi
if [[ "${PLATFORM_RUNTIME_ENV:-}" == "PROD" ]]; then
  ok "PLATFORM_RUNTIME_ENV=PROD"
else
  warn "PLATFORM_RUNTIME_ENV is '${PLATFORM_RUNTIME_ENV:-<unset>}'"
fi

print_header "Database"
require_env SPRING_DATASOURCE_URL
require_env SPRING_DATASOURCE_USERNAME
require_env SPRING_DATASOURCE_PASSWORD
warn_if_unset MEDIPLAT_DATASOURCE_URL
warn_if_unset MEDIPLAT_DATASOURCE_USERNAME
warn_if_unset MEDIPLAT_DATASOURCE_PASSWORD
warn_if_unset PLATFORM_COUNSELMAN_DATASOURCE_URL
warn_if_unset PLATFORM_COUNSELMAN_DATASOURCE_USERNAME
warn_if_unset PLATFORM_COUNSELMAN_DATASOURCE_PASSWORD
warn_if_unset PLATFORM_COUNSELMAN_LOGIN_AES_KEY
if [[ "${SPRING_DATASOURCE_URL:-}" == *"useSSL=false"* ]]; then
  warn "SPRING_DATASOURCE_URL contains useSSL=false"
fi
if [[ "${SPRING_DATASOURCE_URL:-}" == *"49.247.42.59"* ]]; then
  warn "SPRING_DATASOURCE_URL appears to point to the DEV/local host"
fi

print_header "SMS"
require_env BIZPPURIO_PROD_ACCOUNT
require_env BIZPPURIO_PROD_USERNAME
require_env BIZPPURIO_PROD_PASSWORD
if [[ "${BIZPPURIO_PROD_PASSWORD:-}" == "core2468!!" ]]; then
  fail "BIZPPURIO_PROD_PASSWORD is still the repository default"
fi
warn "Confirm the PROD server public IP is registered in Bizppurio whitelist"

print_header "Storage"
audio_dir="${COUNSEL_AUDIO_BASE_DIR:-/mnt/csm-audio}"
file_dir="${COUNSEL_FILE_BASE_DIR:-/mnt/csm-counsel-files}"
for dir in "$audio_dir" "$file_dir"; do
  if [[ -d "$dir" ]]; then
    if [[ -w "$dir" ]]; then
      ok "$dir exists and is writable"
    else
      fail "$dir exists but is not writable by current user"
    fi
  else
    fail "$dir does not exist"
  fi
done

print_header "Build Inputs"
if [[ -f build.gradle && -d mediplat && -f mediplat/build.gradle ]]; then
  ok "CSM and MediPlat Gradle projects are present"
else
  fail "Expected Gradle project files are missing"
fi

print_header "Result"
printf 'failures=%s warnings=%s\n' "$failures" "$warnings"
if [[ "$failures" -gt 0 ]]; then
  exit 1
fi
