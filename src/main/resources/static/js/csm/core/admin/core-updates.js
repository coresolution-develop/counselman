document.addEventListener("DOMContentLoaded", () => {
  const API_ROOT = "/csm/core/updates";
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");

  const rows = Array.from(document.querySelectorAll(".notice-row[data-update-id]"));
  const currentIdEl = document.getElementById("core-update-current-id");
  const idInput = document.getElementById("update-id");
  const versionInput = document.getElementById("update-version");
  const categoryInput = document.getElementById("update-category");
  const severityInput = document.getElementById("update-severity");
  const titleInput = document.getElementById("update-title");
  const summaryInput = document.getElementById("update-summary");
  const bodyInput = document.getElementById("update-body");
  const statusInput = document.getElementById("update-status");
  const popupInput = document.getElementById("update-popup");
  const publishedAtInput = document.getElementById("update-published-at");

  const newBtn = document.getElementById("update-new-btn");
  const saveBtn = document.getElementById("update-save-btn");
  const publishBtn = document.getElementById("update-publish-btn");
  const archiveBtn = document.getElementById("update-archive-btn");
  const deleteBtn = document.getElementById("update-delete-btn");
  const filterResetBtn = document.getElementById("update-filter-reset");

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
    versionInput.value = "";
    categoryInput.value = "release";
    severityInput.value = "normal";
    titleInput.value = "";
    summaryInput.value = "";
    bodyInput.value = "";
    statusInput.value = "DRAFT";
    popupInput.checked = false;
    publishedAtInput.value = "";
    currentIdEl.textContent = "신규";
    rows.forEach((row) => row.classList.remove("active"));
  }

  function setActiveRow(updateId) {
    rows.forEach((row) => {
      const rowId = Number(row.getAttribute("data-update-id") || 0);
      row.classList.toggle("active", rowId === updateId);
    });
  }

  function applyUpdate(update) {
    const updateId = Number(update?.id || 0);
    idInput.value = updateId > 0 ? String(updateId) : "";
    versionInput.value = update?.version || "";
    categoryInput.value = update?.category || "release";
    severityInput.value = update?.severity || "normal";
    titleInput.value = update?.title || "";
    summaryInput.value = update?.summary || "";
    bodyInput.value = update?.body || "";
    statusInput.value = update?.status || "DRAFT";
    popupInput.checked = String(update?.popup_yn || "N").toUpperCase() === "Y";
    publishedAtInput.value = normalizeDateTimeLocal(update?.published_at);

    currentIdEl.textContent = updateId > 0 ? `#${updateId}` : "신규";
    setActiveRow(updateId);
  }

  async function fetchUpdateDetail(updateId) {
    const response = await fetch(`${API_ROOT}/detail/${updateId}`);
    if (!response.ok) {
      throw new Error("업데이트 정보를 불러오지 못했습니다.");
    }
    const json = await response.json();
    if (!json || json.result !== "1" || !json.update) {
      throw new Error(json?.message || "업데이트 정보를 불러오지 못했습니다.");
    }
    return json.update;
  }

  function collectPayload() {
    return {
      id: Number(idInput.value || 0),
      version: versionInput.value.trim(),
      category: categoryInput.value,
      severity: severityInput.value,
      title: titleInput.value.trim(),
      summary: summaryInput.value.trim(),
      body: bodyInput.value.trim(),
      status: statusInput.value,
      popupYn: popupInput.checked ? "Y" : "N",
      publishedAt: publishedAtInput.value || "",
    };
  }

  async function saveUpdate() {
    const payload = collectPayload();
    if (!payload.version) {
      alert("버전을 입력하세요. (예: v1.3.0)");
      versionInput.focus();
      return;
    }
    if (!payload.title) {
      alert("업데이트 제목을 입력하세요.");
      titleInput.focus();
      return;
    }
    const response = await fetch(`${API_ROOT}/save`, {
      method: "POST",
      headers: requestHeaders(),
      body: JSON.stringify(payload),
    });
    const json = await response.json();
    if (!response.ok || !json || json.result !== "1") {
      throw new Error(json?.message || "업데이트 저장에 실패했습니다.");
    }
    alert("업데이트를 저장했습니다.");
    window.location.reload();
  }

  async function changeStatus(status) {
    const updateId = Number(idInput.value || 0);
    if (updateId <= 0) {
      alert("상태를 변경할 업데이트를 먼저 선택하세요.");
      return;
    }
    const response = await fetch(`${API_ROOT}/status`, {
      method: "POST",
      headers: requestHeaders(),
      body: JSON.stringify({ id: updateId, status }),
    });
    const json = await response.json();
    if (!response.ok || !json || json.result !== "1") {
      throw new Error(json?.message || "업데이트 상태 변경에 실패했습니다.");
    }
    alert("업데이트 상태를 변경했습니다.");
    window.location.reload();
  }

  async function deleteUpdate() {
    const updateId = Number(idInput.value || 0);
    if (updateId <= 0) {
      alert("삭제할 업데이트를 먼저 선택하세요.");
      return;
    }
    if (!confirm("정말 삭제하시겠습니까? 모든 읽음 기록도 함께 삭제됩니다.")) {
      return;
    }
    const response = await fetch(`${API_ROOT}/delete`, {
      method: "POST",
      headers: requestHeaders(),
      body: JSON.stringify({ id: updateId }),
    });
    const json = await response.json();
    if (!response.ok || !json || json.result !== "1") {
      throw new Error(json?.message || "업데이트 삭제에 실패했습니다.");
    }
    alert("업데이트를 삭제했습니다.");
    window.location.reload();
  }

  rows.forEach((row) => {
    row.addEventListener("click", async () => {
      const updateId = Number(row.getAttribute("data-update-id") || 0);
      if (updateId <= 0) return;
      try {
        const update = await fetchUpdateDetail(updateId);
        applyUpdate(update);
      } catch (error) {
        alert(error.message);
      }
    });
  });

  newBtn?.addEventListener("click", resetForm);
  saveBtn?.addEventListener("click", async () => {
    try { await saveUpdate(); } catch (error) { alert(error.message); }
  });
  publishBtn?.addEventListener("click", async () => {
    try { await changeStatus("PUBLISHED"); } catch (error) { alert(error.message); }
  });
  archiveBtn?.addEventListener("click", async () => {
    try { await changeStatus("ARCHIVED"); } catch (error) { alert(error.message); }
  });
  deleteBtn?.addEventListener("click", async () => {
    try { await deleteUpdate(); } catch (error) { alert(error.message); }
  });
  filterResetBtn?.addEventListener("click", () => {
    window.location.href = API_ROOT;
  });

  if (rows.length > 0) {
    const firstId = Number(rows[0].getAttribute("data-update-id") || 0);
    if (firstId > 0) {
      fetchUpdateDetail(firstId)
        .then((update) => applyUpdate(update))
        .catch(() => resetForm());
    } else {
      resetForm();
    }
  } else {
    resetForm();
  }
});
