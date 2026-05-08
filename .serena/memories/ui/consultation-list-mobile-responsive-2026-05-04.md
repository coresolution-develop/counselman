# Consultation list mobile responsive changes - 2026-05-04

Scope: `/csm/counsel/list` currently returns `design/consultation-list` from `PageController`, so changes were applied to `src/main/resources/templates/design/consultation-list.html` only.

Implemented page-scoped media queries inside the template inline style:
- `@media (max-width: 768px)`: scoped `.app[data-screen-label="Consultation List"]` overflow-x hidden; reduced main padding; wrapped page/section headers; search-bar 2 columns; queue responsive grid; disabled sticky fixed-height list section on mobile; table scroll constrained to wrapper; calendar grid reduced to 92px day columns.
- `@media (max-width: 480px)`: main padding 12px; search bar 1 column; search inputs/select/buttons 44px high and 16px font; filter pills horizontal scroll; buttons and pills min-height 44px; queue 1 column; active feedback for touch; footer/modal button stacks; modals sized to viewport; SMS form controls 16px; calendar popover as fixed bottom sheet.

Verification: `git diff --check -- src/main/resources/templates/design/consultation-list.html` passed. Browser render not run in this turn.