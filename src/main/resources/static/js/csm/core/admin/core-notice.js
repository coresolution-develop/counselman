document.addEventListener("DOMContentLoaded", () => {
  const API_ROOT = "/csm/core/notice";
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");

  const rows = Array.from(document.querySelectorAll(".notice-row[data-notice-id]"));
  const currentIdEl = document.getElementById("core-notice-current-id");
  const idInput = document.getElementById("notice-id");
  const titleInput = document.getElementById("notice-title");
  const bodyInput = document.getElementById("notice-body");
  const statusInput = document.getElementById("notice-status");
  const priorityInput = document.getElementById("notice-priority");
  const pinnedInput = document.getElementById("notice-pinned");
  const popupInput = document.getElementById("notice-popup");
  const startAtInput = document.getElementById("notice-start-at");
  const endAtInput = document.getElementById("notice-end-at");
  const targetAllInput = document.getElementById("notice-target-all");
  const targetInputs = Array.from(document.querySelectorAll('input[name="targetInstCode"]'));

  const newBtn = document.getElementById("notice-new-btn");
  const saveBtn = document.getElementById("notice-save-btn");
  const publishBtn = document.getElementById("notice-publish-btn");
  const archiveBtn = document.getElementById("notice-archive-btn");
  const filterResetBtn = document.getElementById("notice-filter-reset");

  function requestHeaders() {
    const headers = { "Content-Type": "application/json" };
    if (csrfToken && csrfHeader) {
      headers[csrfHeader] = csrfToken;
    }
    return headers;
  }

  function normalizeDateTimeLocal(value) {
    if (!value) return "";
    const raw = String(value).trim().replace(" ", "T");
    return raw.length >= 16 ? raw.slice(0, 16) : "";
  }

  function resetForm() {
    idInput.value = "";
    titleInput.value = "";
    bodyInput.value = "";
    statusInput.value = "DRAFT";
    priorityInput.value = "3";
    pinnedInput.checked = false;
    popupInput.checked = false;
    startAtInput.value = "";
    endAtInput.value = "";
    targetInputs.forEach((input) => {
      input.checked = true;
    });
    targetAllInput.checked = targetInputs.length > 0;
    currentIdEl.textContent = "신규";
    rows.forEach((row) => row.classList.remove("active"));
  }

  function syncTargetAllState() {
    if (!targetInputs.length) {
      targetAllInput.checked = false;
      return;
    }
    const checkedCount = targetInputs.filter((input) => input.checked).length;
    targetAllInput.checked = checkedCount === targetInputs.length;
  }

  function setActiveRow(noticeId) {
    rows.forEach((row) => {
      const rowId = Number(row.getAttribute("data-notice-id") || 0);
      row.classList.toggle("active", rowId === noticeId);
    });
  }

  function applyNotice(notice) {
    const noticeId = Number(notice?.id || 0);
    idInput.value = noticeId > 0 ? String(noticeId) : "";
    titleInput.value = notice?.title || "";
    bodyInput.value = notice?.body || "";
    statusInput.value = notice?.status || "DRAFT";
    priorityInput.value = String(notice?.priority ?? 3);
    pinnedInput.checked = String(notice?.pinned_yn || "N").toUpperCase() === "Y";
    popupInput.checked = String(notice?.popup_yn || "N").toUpperCase() === "Y";
    startAtInput.value = normalizeDateTimeLocal(notice?.start_at);
    endAtInput.value = normalizeDateTimeLocal(notice?.end_at);

    const targetCodes = Array.isArray(notice?.target_inst_codes)
      ? notice.target_inst_codes.map((item) => String(item))
      : [];
    targetInputs.forEach((input) => {
      input.checked = targetCodes.includes(input.value);
    });
    syncTargetAllState();

    currentIdEl.textContent = noticeId > 0 ? `#${noticeId}` : "신규";
    setActiveRow(noticeId);
  }

  async function fetchNoticeDetail(noticeId) {
    const response = await fetch(`${API_ROOT}/detail/${noticeId}`);
    if (!response.ok) {
      throw new Error("공지 정보를 불러오지 못했습니다.");
    }
    const json = await response.json();
    if (!json || json.result !== "1" || !json.notice) {
      throw new Error(json?.message || "공지 정보를 불러오지 못했습니다.");
    }
    return json.notice;
  }

  function collectPayload() {
    const checkedTargets = targetInputs.filter((input) => input.checked).map((input) => input.value);
    return {
      id: Number(idInput.value || 0),
      title: titleInput.value.trim(),
      body: bodyInput.value.trim(),
      status: statusInput.value,
      priority: Number(priorityInput.value || 3),
      pinnedYn: pinnedInput.checked ? "Y" : "N",
      popupYn: popupInput.checked ? "Y" : "N",
      startAt: startAtInput.value || "",
      endAt: endAtInput.value || "",
      targetInstCodes: checkedTargets,
    };
  }

  async function saveNotice() {
    const payload = collectPayload();
    if (!payload.title) {
      alert("공지 제목을 입력하세요.");
      titleInput.focus();
      return;
    }
    if (!payload.targetInstCodes.length) {
      alert("대상 기관을 1개 이상 선택해 주세요.");
      return;
    }
    const response = await fetch(`${API_ROOT}/save`, {
      method: "POST",
      headers: requestHeaders(),
      body: JSON.stringify(payload),
    });
    const json = await response.json();
    if (!response.ok || !json || json.result !== "1") {
      throw new Error(json?.message || "공지 저장에 실패했습니다.");
    }
    alert("공지를 저장했습니다.");
    window.location.reload();
  }

  async function updateStatus(status) {
    const noticeId = Number(idInput.value || 0);
    if (noticeId <= 0) {
      alert("상태를 변경할 공지를 먼저 선택하세요.");
      return;
    }
    const response = await fetch(`${API_ROOT}/status`, {
      method: "POST",
      headers: requestHeaders(),
      body: JSON.stringify({ id: noticeId, status }),
    });
    const json = await response.json();
    if (!response.ok || !json || json.result !== "1") {
      throw new Error(json?.message || "공지 상태 변경에 실패했습니다.");
    }
    alert("공지 상태를 변경했습니다.");
    window.location.reload();
  }

  rows.forEach((row) => {
    row.addEventListener("click", async () => {
      const noticeId = Number(row.getAttribute("data-notice-id") || 0);
      if (noticeId <= 0) return;
      try {
        const notice = await fetchNoticeDetail(noticeId);
        applyNotice(notice);
      } catch (error) {
        alert(error.message);
      }
    });
  });

  targetAllInput?.addEventListener("change", () => {
    const checked = !!targetAllInput.checked;
    targetInputs.forEach((input) => {
      input.checked = checked;
    });
  });

  targetInputs.forEach((input) => {
    input.addEventListener("change", syncTargetAllState);
  });

  newBtn?.addEventListener("click", resetForm);
  saveBtn?.addEventListener("click", async () => {
    try {
      await saveNotice();
    } catch (error) {
      alert(error.message);
    }
  });
  publishBtn?.addEventListener("click", async () => {
    try {
      await updateStatus("PUBLISHED");
    } catch (error) {
      alert(error.message);
    }
  });
  archiveBtn?.addEventListener("click", async () => {
    try {
      await updateStatus("ARCHIVED");
    } catch (error) {
      alert(error.message);
    }
  });
  filterResetBtn?.addEventListener("click", () => {
    window.location.href = API_ROOT;
  });

  if (rows.length > 0) {
    const firstId = Number(rows[0].getAttribute("data-notice-id") || 0);
    if (firstId > 0) {
      fetchNoticeDetail(firstId)
        .then((notice) => applyNotice(notice))
        .catch(() => resetForm());
    } else {
      resetForm();
    }
  } else {
    resetForm();
  }
});
