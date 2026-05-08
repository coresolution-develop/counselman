# /roles data not loading after navigating from /message

Symptom: Navigating to `/csm/roles` from `/csm/message` via the Turbo-enabled design chrome could leave the role management page without API-loaded role data.

Root cause: `src/main/resources/static/assets/js/app.js` registers a shared/demo Alpine component named `roleManager`, and `src/main/resources/templates/design/role-management.html` also used `x-data="roleManager()"` with another component of the same name. On full page load, the page inline registration happens before Alpine starts, so the API-backed component wins. On Turbo navigation, Alpine may initialize the swapped DOM before the page inline script registers its override, so the shared component can be used or initialization can be missed.

Fix: Changed the role management page to use a page-scoped Alpine component name, `roleManagementPage`, and added a guarded `Alpine.initTree(root)` retry when Alpine is already running during a Turbo visit.

Changed files:
- `src/main/resources/templates/design/role-management.html`
- `src/test/java/com/coresolution/csm/controller/RoleManagementTemplateTest.java`

Regression test: `./gradlew test --tests com.coresolution.csm.controller.RoleManagementTemplateTest`

Similar pattern scan: `assets/js/app.js` still has shared `roleManager`; no other design template uses `x-data="roleManager()"` after the fix.