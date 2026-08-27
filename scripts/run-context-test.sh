#!/usr/bin/env bash
# 전체 컨텍스트 테스트(CsmApplicationTests)를 테스트 컨테이너로 돌린다.
#
# ── 왜 스크립트인가 ──
# 이 테스트는 DB 와 필수 환경변수가 둘 다 있어야 뜬다. env 를 손으로 맞추다 보면
# "왜 안 뜨는지" 를 매번 다시 알아내게 된다. 2026-08-27 에 그렇게 30분을 썼다.
#
# ⚠️ 여기 값은 **테스트 컨테이너 전용 더미**다. 운영 값을 넣지 않는다.
#    운영 DB 에 붙으면 schema bootstrap 이 실제 테이블을 건드린다.
set -euo pipefail

PORT=3309

if ! docker ps --filter name=csm-test-mysql --filter status=running -q | grep -q .; then
  echo "csm-test-mysql 이 떠 있지 않습니다. 먼저:"
  echo "  docker compose -f docker-compose.test.yml up -d"
  exit 1
fi

export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:${PORT}/csm?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=csm_test_pw

# dev 프로파일 기동에 필수인 값들. 하나라도 빠지면 컨텍스트가 안 뜬다.
# 이 목록은 ContextWiringTest 의 @TestPropertySource 와 같아야 한다 — 갈리면
# 한쪽만 통과하는 상태가 된다.
export LOGIN_AES_KEY='0123456789abcdef0123456789abcdef'
export MEDIPLAT_SSO_SHARED_SECRET='context-test'
export COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET='context-test'
export BIZPPURIO_DEV_ACCOUNT='context-test'
export BIZPPURIO_DEV_USERNAME='context-test'
export BIZPPURIO_DEV_PASSWORD='context-test'
export BIZPPURIO_PROD_ACCOUNT='context-test'
export BIZPPURIO_PROD_USERNAME='context-test'
export BIZPPURIO_PROD_PASSWORD='context-test'

echo "→ 전체 컨텍스트 테스트 (DB: 127.0.0.1:${PORT})"
exec ./gradlew test --tests 'com.coresolution.csm.CsmApplicationTests' "$@"
