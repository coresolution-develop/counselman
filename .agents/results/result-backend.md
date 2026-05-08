# Backend Agent Result

**Status:** completed

## Summary

Added chatbot consultation submission endpoint and mobile UX improvements to the CSM project.

## Files Changed

### 1. `/Users/leesumin/csm/src/main/java/com/coresolution/csm/controller/ChatApiController.java`
- Added `POST /api/chat/counsel` endpoint
- Sanitizes `inst` with `replaceAll("[^A-Za-z0-9_]", "")` (consistent with all other methods)
- Uses parameterized JDBC query (9 positional `?` placeholders) — no string interpolation on values
- Sets `cs_col_16` to today's date via `LocalDate.now()`, `cs_col_17` = "챗봇", `cs_col_18` = "챗봇접수", `cs_col_35` = "Y"
- Returns `{"ok": true}` on success or `{"ok": false, "error": "..."}` on exception

### 2. `/Users/leesumin/csm/src/main/resources/templates/design/chat-page.html`
All changes were targeted edits — the file was NOT rewritten.

| Edit | Location | Change |
|------|----------|--------|
| 2a | `.chat-input` CSS | `font-size: 15px` to `16px` (prevents iOS auto-zoom) |
| 2b | Before `</style>` | Added `.msg.bot`, `.quick-actions`, `.quick-btn`, `.quick-btn--primary` styles |
| 2c props | `chatApp()` return object | Added `botStep: null` and `botData: { name, phone, content }` |
| 2c init | `init()` end | Added `visualViewport` resize handler for keyboard-aware layout |
| 2c methods | Before `scrollBottom()` | Added `startCounselFlow()`, `handleBotInput()`, `confirmCounsel()` |
| 2c sendMessage | After guard clauses | Inserted bot-flow intercept block |
| 2d welcome | `.welcome-card` after `<p>` | Added "상담 접수하기" primary button |
| 2d msg-bubble | Message template | Added `white-space: pre-line`, added `quick-actions` with `x-for` buttons |
| 2d msg class | `:class` binding | Added `msg.senderType === 'BOT' ? 'bot' : 'counselor'` branch |
| 2e input | Above `input-area` | Wrapped in outer div; added quick-actions row with "상담 접수" button shown only when `!botStep && roomStatus === 'IDLE'` |

## Acceptance Criteria Checklist

- [x] `POST /api/chat/counsel` endpoint exists at `/api/chat/counsel`
- [x] `inst` parameter is sanitized before table name interpolation
- [x] All 9 column values are passed as parameterized bind variables (no SQL injection via values)
- [x] Correct column mapping: `cs_col_01`/`cs_col_13` = name, `cs_col_15` = phone, `cs_col_16` = today, `cs_col_17` = "챗봇", `cs_col_18` = "챗봇접수", `cs_col_32` = content, `cs_col_33` = inst, `cs_col_35` = "Y"
- [x] iOS zoom fix applied (font-size 16px on textarea)
- [x] `visualViewport` keyboard handler added for mobile layout
- [x] Bot state machine (`botStep`, `botData`) integrated into Alpine.js component
- [x] `sendMessage()` intercepts bot flow before normal STOMP send
- [x] Confirmation step renders action buttons via `x-for` / `confirmCounsel()`
- [x] Welcome card has "상담 접수하기" button
- [x] Quick-actions button in input area shown only when idle and bot not active
- [x] `white-space: pre-line` applied to message bubble for multi-line bot confirmation display
- [x] No full file rewrite — all changes are targeted edits

## Out-of-Scope Notes

- No database migration needed — table `csm.counsel_data_${inst}` is assumed to exist with the specified columns
