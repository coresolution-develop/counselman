(() => {
  function detectContextPath() {
    const path = window.location.pathname || "";
    if (path.startsWith("/csm/") || path === "/csm") {
      return "/csm";
    }
    return "";
  }

  function todayKey(noticeId) {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, "0");
    const dd = String(now.getDate()).padStart(2, "0");
    return `notice-popup-hide-${noticeId}-${yyyy}${mm}${dd}`;
  }

  function getCsrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
    if (!token || !header) {
      return {};
    }
    return { [header]: token };
  }

  function normalizeText(value) {
    return value == null ? "" : String(value);
  }

  function shouldSkip() {
    const wrapper = document.querySelector(".wrapper");
    const inst = (wrapper?.getAttribute("data-inst") || "").trim().toLowerCase();
    if (!inst || inst === "core") {
      return true;
    }
    const path = window.location.pathname || "";
    return path.endsWith("/notice") || path.endsWith("/notice/");
  }

  document.addEventListener("DOMContentLoaded", async () => {
    if (shouldSkip()) {
      return;
    }

    const overlay = document.getElementById("global-notice-popup-overlay");
    const titleEl = document.getElementById("global-notice-popup-title");
    const bodyEl = document.getElementById("global-notice-popup-body");
    const dateEl = document.getElementById("global-notice-popup-date");
    const pinEl = document.getElementById("global-notice-popup-pin");
    const hideTodayEl = document.getElementById("global-notice-popup-hide-today");
    const closeBtn = document.getElementById("global-notice-popup-close");
    const readBtn = document.getElementById("global-notice-popup-read");
    const moveBtn = document.getElementById("global-notice-popup-move");

    if (!overlay || !titleEl || !bodyEl || !dateEl || !pinEl || !hideTodayEl || !closeBtn || !readBtn || !moveBtn) {
      return;
    }

    const contextPath = detectContextPath();
    let currentNoticeId = 0;

    async function markRead(noticeId) {
      if (!noticeId || noticeId <= 0) {
        return;
      }
      try {
        await fetch(`${contextPath}/notice/read/${noticeId}`, {
          method: "POST",
          headers: getCsrfHeaders(),
        });
      } catch (e) {
        // 읽음 처리 실패 시에도 팝업 동작은 유지
      }
    }

    function closePopup() {
      if (hideTodayEl.checked && currentNoticeId > 0) {
        localStorage.setItem(todayKey(currentNoticeId), "Y");
      }
      overlay.hidden = true;
    }

    closeBtn.addEventListener("click", closePopup);
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) {
        closePopup();
      }
    });

    readBtn.addEventListener("click", async () => {
      await markRead(currentNoticeId);
      closePopup();
    });

    moveBtn.addEventListener("click", async () => {
      await markRead(currentNoticeId);
      const suffix = contextPath || "";
      window.location.href = `${suffix}/notice`;
    });

    try {
      const response = await fetch(`${contextPath}/notice/popup/next`, { cache: "no-store" });
      if (!response.ok) {
        return;
      }
      const json = await response.json();
      if (!json || json.result !== "1" || !json.notice) {
        return;
      }

      const notice = json.notice;
      const noticeId = Number(notice.id || 0);
      if (noticeId <= 0) {
        return;
      }
      if (localStorage.getItem(todayKey(noticeId)) === "Y") {
        return;
      }

      currentNoticeId = noticeId;
      hideTodayEl.checked = false;
      titleEl.textContent = normalizeText(notice.title);
      bodyEl.textContent = normalizeText(notice.body);
      dateEl.textContent = normalizeText(notice.created_at);

      const isPinned = normalizeText(notice.pinned_yn).toUpperCase() === "Y";
      pinEl.hidden = !isPinned;
      overlay.hidden = false;
    } catch (e) {
      // ignore
    }
  });
})();
