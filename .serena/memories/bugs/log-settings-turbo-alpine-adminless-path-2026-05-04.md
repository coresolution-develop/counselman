# Consultation log settings requires refresh after Turbo navigation

Symptom: `/csm/admin/counsel/log-settings` rendered partially until refresh, same as `/users` and `/access`.

Root cause: `consultation-log-settings.html` defines `function logSettings()` in a bottom inline script. On Turbo visits, Alpine can scan `x-data="logSettings()"` before that function is available, leaving the root uninitialized until a full refresh.

Fix:
- Added guarded Turbo/Alpine reinitialization after `logSettings()` is defined: if Alpine is already running, queue a microtask and call `Alpine.initTree(root)` only when the root has no `_x_dataStack`.
- Moved visible route from `/admin/counsel/log-settings` to `/counsel/log-settings`.
- Changed old `/admin/counsel/log-settings` to redirect to `/counsel/log-settings` for compatibility.
- Changed log settings save URL used by the page to `/counsel/log-settings/save`, while keeping legacy save mapping.
- Updated `chrome.js` nav href and active aliases, design index link, and `CsmSchemaBootstrapService` default URL.

Verification:
`./gradlew test --tests com.coresolution.csm.controller.ChromeNavigationTemplateTest --tests com.coresolution.csm.controller.RoleManagementTemplateTest --tests com.coresolution.csm.controller.RoomBoardControllerTest` passed.

Legacy note: the old admin GET and save mappings remain server-side for backward compatibility, but new UI links and default nav point to the adminless route.