#!/usr/bin/env bash
# =============================================================================
# lib-csm-bluegreen.sh  — 공유 헬퍼 (⚠️ DRAFT / 미적용)   [v3: 리뷰 반영]
# -----------------------------------------------------------------------------
# 변경점(리뷰 반영):
#  - [버그1] copy_war_to_b: b webapps 를 비우고(옛 csm/ , csm.war* 제거) 새 WAR 만 배치
#            → autoDeploy 가 옛 컨텍스트를 다시 올려 옛 버전이 헬스 200 통과하는 문제 차단.
#            + verify_b_version: WAR 임베드 build.time 으로 'b 가 새 WAR 로 떴는지' 대조.
#  - [버그2] _active_conns: httpd 프로세스 소유 연결만 카운트(ss -p + grep httpd).
#            wait_drained: 0 도달 또는 '감소 멈춤(plateau)' 판정 → 매번 CAP 소진 방지.
#  - [위험1] route_to: graceful reload 후 HTTPD_SETTLE 대기 → 드레인과 경쟁 방지.
#
# 라우팅: conf 심볼릭 스왑 + configtest + graceful reload (balancer-manager/nonce 없음).
# 세션 JDBC 공유 → sticky/balancer/jvmRoute 불필요.
# =============================================================================

# ── 라우팅(conf-swap) ────────────────────────────────────────────────────────
HTTPD_CONF_DIR="${HTTPD_CONF_DIR:-/opt/csm-next/httpd}"
ROUTE_LINK="${ROUTE_LINK:-${HTTPD_CONF_DIR}/csm-route.conf}"
ROUTE_A_CONF="${ROUTE_A_CONF:-${HTTPD_CONF_DIR}/csm-route-a.conf}"   # → 18081
ROUTE_B_CONF="${ROUTE_B_CONF:-${HTTPD_CONF_DIR}/csm-route-b.conf}"   # → 18083
APACHECTL="${APACHECTL:-apachectl}"
HTTPD_RELOAD_CMD="${HTTPD_RELOAD_CMD:-systemctl reload httpd}"       # graceful
HTTPD_PROC="${HTTPD_PROC:-httpd}"        # ss -p 프로세스명(데비안계면 apache2)
HTTPD_SETTLE="${HTTPD_SETTLE:-3}"        # [위험1] reload 후 워커 전환 settle(초)

# ── 인스턴스 ────────────────────────────────────────────────────────────────
WORKER_A_PORT="${WORKER_A_PORT:-18081}"; WORKER_B_PORT="${WORKER_B_PORT:-18083}"
HEALTH_A="${HEALTH_A:-http://127.0.0.1:${WORKER_A_PORT}/csm/login}"
HEALTH_B="${HEALTH_B:-http://127.0.0.1:${WORKER_B_PORT}/csm/login}"
CSM_SERVICE="${CSM_SERVICE:-csm-next}"; B_SERVICE="${B_SERVICE:-csm-next-b}"
B_WEBAPPS="${B_WEBAPPS:-/opt/csm-next/tomcat-b/webapps}"
# WAR/exploded 안의 build-info.properties 상대 경로
BUILDINFO_REL="${BUILDINFO_REL:-WEB-INF/classes/META-INF/build-info.properties}"

# ── 타이밍 ──────────────────────────────────────────────────────────────────
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"; HEALTH_INTERVAL="${HEALTH_INTERVAL:-3}"
DRAIN_CAP="${DRAIN_CAP:-60}"; DRAIN_INTERVAL="${DRAIN_INTERVAL:-2}"; DRAIN_STABLE="${DRAIN_STABLE:-3}"
DRAIN_ZERO_CONFIRM="${DRAIN_ZERO_CONFIRM:-2}"   # [게이트3] 0 을 연속 N회 확인해야 드레인 인정(즉시 통과 방지)

if ! declare -F log >/dev/null 2>&1; then
  log() { printf '[csm-bg %s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
fi

# ── 라우팅 전환: 심볼릭 스왑 → configtest → graceful reload → settle ─────────
route_to() {   # <a|b>
  local which="$1" target prev
  case "$which" in
    a) target="$ROUTE_A_CONF" ;;
    b) target="$ROUTE_B_CONF" ;;
    *) log "  route_to: 잘못된 인자 '$which'"; return 2 ;;
  esac
  [ -e "$target" ] || { log "  ERROR: route conf 없음: $target"; return 1; }
  prev="$(readlink "$ROUTE_LINK" 2>/dev/null || true)"
  ln -sfn "$target" "$ROUTE_LINK"
  if ! $APACHECTL configtest >/dev/null 2>&1; then
    [ -n "$prev" ] && ln -sfn "$prev" "$ROUTE_LINK"
    log "  ERROR: configtest 실패 → 심볼릭 원복, reload 생략(현 라우팅 유지)"; return 1
  fi
  if ! $HTTPD_RELOAD_CMD; then log "  ERROR: httpd reload 실패"; return 1; fi
  log "  라우팅 → ${which} ($(basename "$target")), graceful reload (settle ${HTTPD_SETTLE}s)"
  sleep "$HTTPD_SETTLE"   # [위험1] 옛 워커 in-flight 가 드레인 카운트에 잡히도록 settle 후 진행
}

# ── [버그1] b webapps 정리 후 새 WAR 배치 + 버전 대조 ───────────────────────
# [게이트2] rm -rf 는 반드시 b 정지 상태에서만. 살아있으면 정지·LISTEN 소멸까지 확인.
ensure_b_stopped() {
  if systemctl is-active --quiet "$B_SERVICE" 2>/dev/null; then
    log "  b 가동 중 → 정지(클린 전 보장)"; systemctl stop "$B_SERVICE" || true
  fi
  local i=0
  while ss -ltn "sport = :${WORKER_B_PORT}" 2>/dev/null | grep -q LISTEN; do
    [ "$i" -ge 15 ] && { log "  ERROR: b 포트(${WORKER_B_PORT}) LISTEN 지속 → 클린 보류(러닝 인스턴스 보호)"; return 1; }
    sleep 1; i=$(( i + 1 ))
  done
  return 0
}
copy_war_to_b() {   # <src_war> : [게이트2] 정지 보장 → 옛 컨텍스트 비우고 새 WAR 만 배치
  if ! ensure_b_stopped; then return 1; fi
  mkdir -p "$B_WEBAPPS"
  rm -rf "$B_WEBAPPS/csm" "$B_WEBAPPS/csm.war" "$B_WEBAPPS"/csm.war.* 2>/dev/null || true
  cp -a "$1" "$B_WEBAPPS/csm.war"
}
_war_build_time() { unzip -p "$1" "$BUILDINFO_REL" 2>/dev/null | sed -n 's/^build\.time=//p' | head -1; }
_dir_build_time() { sed -n 's/^build\.time=//p' "$1/$BUILDINFO_REL" 2>/dev/null | head -1; }
# verify_b_version <staged_war> : 0 일치 / 1 불일치(하드 실패) / 2 판정불가(경고·진행)
verify_b_version() {
  local staged="$1" want got
  want="$(_war_build_time "$staged")"
  got="$(_dir_build_time "$B_WEBAPPS/csm")"
  if [ -z "$want" ] || [ -z "$got" ]; then
    log "  WARN: build.time 확인 불가(want='${want:-}', got='${got:-}') → clean-webapps 보장에 의존, 진행"
    return 2
  fi
  if [ "$want" = "$got" ]; then log "  버전 일치: b 가 새 WAR 로 기동(build.time=${got})"; return 0; fi
  log "  ERROR: 버전 불일치 — staged=${want}, b=${got}"; return 1
}

# ── [버그2] 드레인: httpd 소유 백엔드 연결만, 0 또는 plateau 로 판정 ─────────
_active_conns() {   # <port> : httpd→tomcat established (httpd 프로세스 소유만)
  ss -tnp state established "dst 127.0.0.1:$1" 2>/dev/null | grep -c "users:((\"${HTTPD_PROC}\""
}
wait_drained() {    # <port> [cap] : [게이트3] 0 을 연속 ZERO_CONFIRM회 확인 또는 plateau 시 진행
  local port="$1" cap="${2:-$DRAIN_CAP}" elapsed=0 n prev=-1 stable=0 zero=0
  while [ "$elapsed" -lt "$cap" ]; do
    n="$(_active_conns "$port")"
    if [ "${n:-0}" -eq 0 ]; then
      zero=$(( zero + 1 ))
      if [ "$zero" -ge "$DRAIN_ZERO_CONFIRM" ]; then
        log "  in-flight 0 확인(${zero}/${DRAIN_ZERO_CONFIRM}회, port ${port}, ${elapsed}s)"; return 0
      fi
    else
      zero=0                                  # 스퓨리어스 0 방지: 비0 나오면 카운트 리셋
      if [ "$n" -eq "$prev" ]; then stable=$(( stable + 1 )); else stable=0; fi
      if [ "$stable" -ge "$DRAIN_STABLE" ]; then
        log "  in-flight plateau(잔여 ${n}, ${elapsed}s, ${DRAIN_STABLE}회 불변) → 진행"; return 0
      fi
      prev="$n"
    fi
    sleep "$DRAIN_INTERVAL"; elapsed=$(( elapsed + DRAIN_INTERVAL ))
  done
  log "  WARN: in-flight 미소진(port ${port}, ${cap}s 초과, 잔여≈$(_active_conns "$port")) → 진행"
  return 0
}

# ── 헬스 폴링 (httpd 우회, 인스턴스 직접) ───────────────────────────────────
wait_healthy() {    # <url> [timeout] : 200/302 면 0, 타임아웃 1
  local url="$1" timeout="${2:-$HEALTH_TIMEOUT}" elapsed=0 code
  while [ "$elapsed" -lt "$timeout" ]; do
    code="$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 5 "$url" 2>/dev/null || true)"
    case "$code" in 200|302) return 0 ;; esac
    sleep "$HEALTH_INTERVAL"; elapsed=$(( elapsed + HEALTH_INTERVAL ))
  done
  return 1
}

# ── b 인스턴스 수명 ─────────────────────────────────────────────────────────
start_b() { log "  ${B_SERVICE} 기동(${WORKER_B_PORT})"; systemctl start "$B_SERVICE"; }
stop_b()  { log "  ${B_SERVICE} 정지(메모리 회수)"; systemctl stop "$B_SERVICE" 2>/dev/null || true; }
