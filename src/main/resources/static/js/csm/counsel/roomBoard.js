document.addEventListener('DOMContentLoaded', () => {
  /* ── 팝업 모드: 병실 선택 버튼 ── */
  if (window.ROOM_BOARD_POPUP_MODE) {
    document.querySelectorAll('.room-select-btn').forEach((button) => {
      button.addEventListener('click', () => {
        const roomName = String(button.dataset.roomName || '').trim();
        if (!roomName) return;
        try {
          if (window.opener && !window.opener.closed) {
            window.opener.postMessage(
              { type: 'csm-room-board-select', roomName },
              window.location.origin
            );
          }
        } catch (_) { /* no-op */ }
        window.close();
      });
    });
  }

  /* ── 상태 필터 ── */
  const filterBar = document.getElementById('rb-filter-bar');
  if (!filterBar) return;

  const statusCheckboxes    = filterBar.querySelectorAll('#rb-status-checks input[type="checkbox"]');
  const availableCheckbox   = document.getElementById('filter-available');
  const allCheckboxes       = filterBar.querySelectorAll('input[type="checkbox"]');
  const resetBtn            = document.getElementById('rb-filter-reset');

  function applyFilter() {
    // 상태 필터: 선택된 것 중 하나라도 일치하면 통과 (OR)
    const activeStatus = Array.from(statusCheckboxes)
      .filter(cb => cb.checked)
      .map(cb => cb.value); // 'walk' | 'diaper' | 'oxygen' | 'suction'

    // 입원 가능 필터: 선택 시 availableCount > 0 인 행만 통과 (AND)
    const onlyAvailable = availableCheckbox && availableCheckbox.checked;

    document.querySelectorAll('.rb-table tbody tr').forEach(row => {
      let visible = true;

      // 상태 필터 조건 (체크 항목 없으면 통과)
      if (activeStatus.length > 0) {
        visible = activeStatus.some(key => {
          const attr = 'status' + key.charAt(0).toUpperCase() + key.slice(1);
          return row.dataset[attr] === 'true';
        });
      }

      // 입원 가능 필터 조건 (AND)
      if (visible && onlyAvailable) {
        visible = row.dataset.available === 'true';
      }

      row.classList.toggle('rb-row-hidden', !visible);
    });
  }

  allCheckboxes.forEach(cb => cb.addEventListener('change', applyFilter));

  resetBtn.addEventListener('click', () => {
    allCheckboxes.forEach(cb => cb.checked = false);
    applyFilter();
  });
});
