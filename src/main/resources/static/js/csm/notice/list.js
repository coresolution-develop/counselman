document.addEventListener("DOMContentLoaded", () => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
  const unreadCountEl = document.getElementById("notice-unread-count");

  function requestHeaders() {
    const headers = {};
    if (csrfToken && csrfHeader) {
      headers[csrfHeader] = csrfToken;
    }
    return headers;
  }

  function updateUnreadCount(nextCount) {
    if (!unreadCountEl) return;
    const safeCount = Number.isFinite(nextCount) ? Math.max(nextCount, 0) : 0;
    unreadCountEl.textContent = String(safeCount);
  }

  async function markRead(card) {
    const noticeId = Number(card.getAttribute("data-notice-id") || 0);
    if (noticeId <= 0) return;

    const response = await fetch(`/csm/notice/read/${noticeId}`, {
      method: "POST",
      headers: requestHeaders(),
    });
    const json = await response.json();
    if (!response.ok || !json || json.result !== "1") {
      throw new Error(json?.message || "읽음 처리에 실패했습니다.");
    }

    card.setAttribute("data-read-yn", "Y");
    const status = card.querySelector(".status");
    if (status) {
      status.classList.remove("unread");
      status.classList.add("read");
      status.textContent = "읽음";
    }
    const readBtn = card.querySelector(".read-btn");
    if (readBtn) {
      readBtn.remove();
    }
    updateUnreadCount(Number(json.unreadCount || 0));
  }

  document.querySelectorAll(".notice-card .read-btn").forEach((btn) => {
    btn.addEventListener("click", async (event) => {
      event.preventDefault();
      const card = btn.closest(".notice-card");
      if (!card) return;
      try {
        await markRead(card);
      } catch (error) {
        alert(error.message);
      }
    });
  });
});
