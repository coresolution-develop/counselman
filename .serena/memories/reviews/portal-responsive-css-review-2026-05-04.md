# Portal responsive CSS review - 2026-05-04

Scope: mediplat/src/main/resources/static/jsx/portal-app.jsx, `portalCss` string only. Requested checks: fixed px horizontal scroll risks, <44px touch targets under 480px, hover-only interactions without active, form input font-size <16px, overflow-x coverage.

Findings:
- `ph__search` has `height: 38px` and no 480px override, so the search input hit area remains below the 44px mobile touch target recommendation when search is enabled.
- `ph__search input` uses `font-size: 13px`, which can trigger iOS Safari input zoom; mobile form controls should be 16px or larger.
- `ph__icon-btn` is defined as 36x36 and has `:hover` without `:active`; currently not rendered by Header after prior changes, but remains a CSS risk if reintroduced.

No current findings:
- Fixed px horizontal scroll: no active issue found. Fixed widths are either small icons/badges, max-widths, or overridden at 480px.
- overflow-x: `.pp` has `overflow-x: hidden` and wraps portal content; Tweaks panel is hidden at 480px.
