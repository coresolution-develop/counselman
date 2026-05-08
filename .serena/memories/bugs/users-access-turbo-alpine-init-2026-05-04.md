# /users and /access blank after Turbo navigation from /roles

Symptom: After login, clicking Admin navigates to `/csm/roles`; then clicking `/csm/users` or `/csm/access` showed a partially raw page until refresh. Console showed `cdn.min.js:5 Uncaught TypeError: Cannot convert undefined or null to object`.

Root cause: The design pages define their Alpine page factories (`function userMgmt()` and `function accessMgmt()`) in inline scripts at the bottom of the body. On a full page refresh, the function is available when Alpine starts. On Turbo body replacement from `/roles`, Alpine can scan/evaluate the new `x-data="userMgmt()"` or `x-data="accessMgmt()"` root before the inline script has finished evaluating, so the page root is not initialized. Header/chrome may still render, leaving templates, counts, and x-show content in a raw/empty state until refresh.

Fix: After each inline page factory is defined, if `window.Alpine` is already running, queue a microtask that finds the page root and calls `Alpine.initTree(root)` only when it has no `_x_dataStack`. This mirrors the earlier `/roles` fix and is guarded to avoid double initialization.

Changed files:
- `src/main/resources/templates/design/user-management.html`
- `src/main/resources/templates/design/access-management.html`
- `src/test/java/com/coresolution/csm/controller/ChromeNavigationTemplateTest.java`

Verification:
`./gradlew test --tests com.coresolution.csm.controller.ChromeNavigationTemplateTest --tests com.coresolution.csm.controller.RoleManagementTemplateTest --tests com.coresolution.csm.controller.RoomBoardControllerTest` passed.