/**
 * Hides write-action UI for VIEWER sessions.
 * Server endpoints already return 403 for writes, so this is purely a UX layer.
 *
 * Activation: include this script in any cancer-treatment page that should respect the
 * VIEWER/MEMBER distinction, AND set <body data-user-role="VIEWER|MEMBER">.
 */
(function () {
    var role = document.body && document.body.dataset && document.body.dataset.userRole;
    if (role !== 'VIEWER') return;

    var style = document.createElement('style');
    style.setAttribute('data-role-guard', 'true');
    style.textContent = [
        // Generic "등록" / 저장 / 삭제 buttons across pages.
        'body[data-user-role="VIEWER"] #patient-create-open,',
        'body[data-user-role="VIEWER"] #room-create-open,',
        'body[data-user-role="VIEWER"] #pkg-create-open,',
        'body[data-user-role="VIEWER"] #cat-add,',
        'body[data-user-role="VIEWER"] .btn-danger,',
        'body[data-user-role="VIEWER"] [onclick*="openScheduleModal"],',
        'body[data-user-role="VIEWER"] [onclick*="saveScheduleModal"],',
        'body[data-user-role="VIEWER"] [onclick*="deleteScheduleFromModal"] {',
        '  display: none !important;',
        '}',
        // Drag-to-edit start time visual: revert to default cursor for VIEWER.
        'body[data-user-role="VIEWER"] .session-block {',
        '  cursor: default !important;',
        '}'
    ].join('\n');
    document.head.appendChild(style);

    // Block inline edits triggered programmatically (status badges, drag handles).
    // Capture phase so we run before page handlers; only fire on write-intent clicks.
    document.addEventListener('click', function (event) {
        var target = event.target.closest(
            '[onclick*="openScheduleModal"], [onclick*="saveScheduleModal"],' +
            '[onclick*="deleteScheduleFromModal"], .session-block, .status-badge'
        );
        if (!target) return;
        event.stopPropagation();
        event.preventDefault();
        if (!window.__roleGuardWarned) {
            window.__roleGuardWarned = true;
            alert('조회 권한입니다. 변경하려면 관리자에게 권한 부여를 요청해주세요.');
            setTimeout(function () { window.__roleGuardWarned = false; }, 4000);
        }
    }, true);

    // Hide the drag-and-drop dataset markers so the schedule list does not look interactive.
    document.querySelectorAll('.session-block').forEach(function (el) {
        el.removeAttribute('draggable');
    });
})();
