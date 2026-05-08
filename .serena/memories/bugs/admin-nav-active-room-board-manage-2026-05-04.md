# Admin nav active state and room-board manage path

Symptom: Left navigation did not show the admin item as active on `/users`, `/access`, and `/admin/room-board`; `/statistics` already matched its `/statistics` nav href.

Root cause: `src/main/resources/static/assets/js/chrome.js` only marked a nav item active when `location.pathname` exactly equaled the nav item's primary `href`. The admin nav item pointed to `/roles`, so sibling admin pages were not active.

Fix:
- Added per-item active aliases in `chrome.js` and configured the admin nav item to include `/roles`, `/users`, `/access`, `/room-board/manage`, and the legacy `/admin/room-board`.
- Added `/room-board/manage` as the visible management page route.
- Kept legacy `/admin/room-board` as a redirect target for compatibility.
- Updated design templates to link to `/room-board/manage` instead of `/admin/room-board`.
- Updated room-board manage forms/fetch URLs to `/room-board/manage/...`, while preserving legacy POST mappings.

Regression tests:
- `ChromeNavigationTemplateTest`
- `RoomBoardControllerTest.legacyRoomBoardAdmin_redirectsToManagePath`

Verification command:
`./gradlew test --tests com.coresolution.csm.controller.ChromeNavigationTemplateTest --tests com.coresolution.csm.controller.RoleManagementTemplateTest --tests com.coresolution.csm.controller.RoomBoardControllerTest`

Note: unauthenticated curl to both old and new room-board management URLs is intercepted by security and redirects to `/csm/login`, so browser verification needs an authenticated session.