#!/usr/bin/env bash
# =============================================================================
# csm-next-rolling-restart.sh — 03:00 csm-next 재시작의 무중단(롤링)화 (⚠️ DRAFT/미적용)
# -----------------------------------------------------------------------------
# 현재 csm-next-restart.service(03:00) 는:  systemctl restart csm-next && systemctl reload httpd
# 단순 재시작이라 csm-next(a) 정지~기동 동안 18081 공백(502/503)이 발생한다.
#
# 본 헬퍼는 **앱 코드 변경 없이** 재시작 방식만 b 경유 롤링으로 바꿔 공백을 제거한다.
# (새 WAR 배포가 아니라 "현재 버전 그대로 재기동" = 메모리/캐시 정리 목적.)
#
# 라우팅 제어: conf-swap(심볼릭 + configtest + graceful reload) — lib 참조.
# 드레인: 백엔드 활성연결 0 폴링(상한 DRAIN_CAP).
#
# 적용(운영자, 검토 후): csm-next-restart.service 의 ExecStart 를 본 스크립트로 교체.
#   (lib-csm-bluegreen.sh 를 같은 디렉터리에 배치)
#
# 종료 상태: a(18081) 재기동 완료 + 라우팅 a + b 정지. 무중단.
# 실패 시: a 정상이면 그대로 두고 exit 1(서비스 지속). 코드로 알림.
# =============================================================================
set -euo pipefail

TOMCAT_WEBAPPS="${TOMCAT_WEBAPPS:-/opt/csm-next/tomcat/webapps}"
CSM_SERVICE="${CSM_SERVICE:-csm-next}"

log() { printf '[csm-rolling %s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-csm-bluegreen.sh
. "${SCRIPT_DIR}/lib-csm-bluegreen.sh"

live="${TOMCAT_WEBAPPS}/csm.war"
if [ ! -f "${live}" ]; then
  log "현재 csm.war 없음(${live}) → 일반 재시작 폴백"
  systemctl restart "${CSM_SERVICE}"; exit $?
fi

log "롤링 재시작 시작 (현재 버전 유지)"

# 1) b 를 '현재 버전'으로 기동(정지 보장 후 클린)
if ! copy_war_to_b "${live}"; then
  log "ERROR: b webapps 준비 실패(정지 미확인) → 일반 재시작 폴백"
  systemctl restart "${CSM_SERVICE}"; exit $?
fi
if ! start_b; then
  log "ERROR: b 기동 실패 → a 유지, 일반 재시작 폴백"
  stop_b; systemctl restart "${CSM_SERVICE}"; exit $?
fi

# 2) b 헬스 — 실패 시 b 정지 후 종료(앱은 a 로 계속)
if ! wait_healthy "${HEALTH_B}" "${HEALTH_TIMEOUT}"; then
  log "ERROR: b 헬스 실패 → b 정지, a 유지(서비스 지속), exit 1"
  stop_b; exit 1
fi
log "b(${WORKER_B_PORT}) 정상"

# 2.5) 버전 대조(현재 버전 일치 확인; clean-webapps 보강)
vrc=0; verify_b_version "${live}" || vrc=$?
if [ "${vrc}" -eq 1 ]; then log "ERROR: b 버전 불일치(롤링) → b 정지, exit 1"; stop_b; exit 1; fi

# 3) 트래픽 b 로(conf-swap) → a 드레인
if ! route_to b; then log "ERROR: b 라우팅 전환 실패 → b 정지, exit 1"; stop_b; exit 1; fi
wait_drained "${WORKER_A_PORT}"

# 4) a(csm-next) 재기동 (b 가 트래픽 처리 → 무중단)
log "${CSM_SERVICE} 재기동"
if ! systemctl restart "${CSM_SERVICE}"; then
  log "ERROR: ${CSM_SERVICE} 재기동 실패 (라우팅 b 유지 = 서비스 지속) → exit 1"
  exit 1
fi
if ! wait_healthy "${HEALTH_A}" "${HEALTH_TIMEOUT}"; then
  log "ERROR: a 헬스 실패 → 라우팅 b 유지한 채 exit 1 (서비스 b 지속)"
  exit 1
fi
log "a(${WORKER_A_PORT}) 정상"

# 5) 트래픽 a 복귀 → b 드레인 → b 정지
if ! route_to a; then log "WARN: a 라우팅 복귀 실패 → b 유지한 채 exit 1 (운영자 확인)"; exit 1; fi
wait_drained "${WORKER_B_PORT}"
stop_b

log "롤링 재시작 완료 (end-on-a / ${WORKER_A_PORT} 활성, b 정지). httpd 는 route_to 의 graceful reload 로 갱신됨."
exit 0
