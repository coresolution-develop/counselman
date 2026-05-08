# PROD 배포 위험 점검 (2026-05-08)

대상: /Users/leesumin/csm Spring Boot CounselMan + mediplat 하위 앱.

검증:
- `./gradlew packageProdDeploy --console=plain`: 성공. 산출물 `build/deploy/prod/csm-prod.war`, `build/deploy/prod/mediplat-prod.jar` 생성.
- `./gradlew test`: 실패. 34 tests, 3 failed.
  - `CsmApplicationTests.contextLoads`: `ClientRegistrationRepository` bean 없음으로 ApplicationContext 실패. 로그에 Bizppurio `3010 IP blocking`도 노출됨.
  - `ChromeNavigationTemplateTest.adminNavigationHighlightsAllAdminSubPages`: nav active 문자열 기대와 실제 불일치.
  - `CsmAuthServiceTransactionTest.savePledgeTemplate_existingUpdatedRow_returnsId`: expected 7L, actual 0L.
- Serena `get_symbols_overview`: Terraform 미설치로 language server 초기화 실패. shell/rg/nl 기반으로 코드 점검 계속 진행.

주요 위험:
1. HIGH: PROD 런타임에서 `SPRING_PROFILES_ACTIVE=prod`를 명시하지 않으면 기본값이 dev. `application.properties:2`.
2. HIGH: `LOGIN_AES_KEY` 미설정 시 prod `login.aes-key=${LOGIN_AES_KEY}`가 AES128 bean 생성 또는 복호화 흐름을 깨뜨릴 가능성. `application-prod.properties:27`, `AES128.java:21-24`.
3. HIGH: CSM과 MediPlat SSO secret이 반드시 같은 값이어야 함. CSM은 빈 값 기본, MediPlat은 `change-me` 기본. `application.properties:39`, `mediplat/application.properties:26`, `MediplatSsoService:41-42`.
4. HIGH: MediPlat 기본 CounselMan base URL이 localhost라 운영 환경변수/서비스 URL 미설정 시 사용자 브라우저가 localhost:8081/csm으로 이동 가능. `mediplat/application.properties:23`, `CounselManSsoLinkService:86-105`.
5. HIGH: Bizppurio 기본 계정/비밀번호가 코드에 있고 테스트 로그에서 IP whitelist 차단(3010) 확인. `application-prod.properties:36-38`, `ExternalSmsGatewayService:162-164`.
6. MEDIUM/HIGH: CSM prod DB 접속정보와 SMS 비밀번호가 파일에 하드코딩됨. `application-prod.properties:18-20`, `36-38`.
7. MEDIUM: MyBatis `StdOutImpl`이 공통 설정이라 PROD에서도 SQL stdout 로그 가능. `application.properties:57`.
8. MEDIUM: 기동 시 schema bootstrap/migration이 DDL을 실행. 운영 DB 권한/락/스키마 상태 사전 점검 필요. `CsmSchemaBootstrapService:41-49`, `282-299`.
9. MEDIUM: 업로드 경로 `/mnt/csm-audio`, `/mnt/csm-counsel-files` 권한/마운트 필요. `application-prod.properties:29-31`.
10. MEDIUM: CORS origin `*`, frameOptions disabled. `SecurityConfig:72`, `180-192`.
11. LOW/MEDIUM: session timeout 0. `application.properties:9`.

권장 배포 전 체크:
- PROD 서버 환경변수: `SPRING_PROFILES_ACTIVE=prod`, `LOGIN_AES_KEY`, `MEDIPLAT_SSO_SHARED_SECRET`, `COUNSELMAN_MEDIPLAT_SSO_SHARED_SECRET`, `MEDIPLAT_PLATFORM_BASE_URL`, `COUNSELMAN_BASE_URL`, `PLATFORM_RUNTIME_ENV=PROD`, DB/SMS credentials.
- Bizppurio 발신 서버 공인 IP whitelist 등록 확인.
- `/mnt/csm-audio`, `/mnt/csm-counsel-files` 생성 및 Tomcat/app 사용자 rw 권한 확인.
- 운영 DB 백업 후 schema bootstrap DDL 영향 확인.
- `/csm/mediplat/sso/entry` 실제 SSO, logout redirect, room-board popup target smoke test.
