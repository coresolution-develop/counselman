# /csm/access CSM 허용 버튼 분석

Updated after localhost:8081 was running.

Reproduction/inspection:
- `localhost:8081` and `localhost:8082` were both listening.
- Logged in through MediPlat dev credentials for `FALH/coreadmin` and reached `/csm/access` successfully.
- GET `/csm/api/access/users` returned 200 with 11 FALH users, all with `csm_enabled: 0`.
- DB inspection showed 11 FALH `mp_user` rows, but no `mp_user_service` rows for `inst_code='FALH' AND service_code='COUNSELMAN'`.
- The upsert SQL used by `AccessManagementApiController#upsertServiceAccess` was tested inside a manual transaction and rolled back; it works against the current DB schema, and the unique key exists.

Conclusion:
- The visible inactive state is data-driven: `csm_enabled` is computed from `mp_user_service.use_yn = 'Y'`, and there are currently no COUNSELMAN service-access rows for FALH users.
- `syncCsmUsers()` only syncs CSM users into `mp_user`; it does not create/enable `mp_user_service` rows. Therefore after sync, users still appear CSM-disabled until individual/department allow writes rows into `mp_user_service`.
- I did not send the permission-changing POST because it would actually alter CSM access. If the UI click still fails, test one toggle with approval and inspect POST `/csm/api/access/users/{username}/counselman` status.

Relevant files:
- `src/main/resources/templates/design/access-management.html`
- `src/main/java/com/coresolution/csm/controller/AccessManagementApiController.java`