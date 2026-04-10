document.addEventListener('DOMContentLoaded', () => {
  if (!window.ROOM_BOARD_POPUP_MODE) {
    return;
  }
  document.querySelectorAll('.room-select-btn').forEach((button) => {
    button.addEventListener('click', () => {
      const roomName = String(button.dataset.roomName || '').trim();
      if (!roomName) {
        return;
      }
      try {
        if (window.opener && !window.opener.closed) {
          window.opener.postMessage({
            type: 'csm-room-board-select',
            roomName
          }, window.location.origin);
        }
      } catch (_) {
        // no-op
      }
      window.close();
    });
  });
});
