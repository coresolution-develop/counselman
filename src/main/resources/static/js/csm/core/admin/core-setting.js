document.addEventListener("DOMContentLoaded", function () {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
  function jsonHeaders() {
    const h = { "Content-Type": "application/json" };
    if (csrfToken && csrfHeader) h[csrfHeader] = csrfToken;
    return h;
  }
  const rows = document.querySelectorAll(".grid-body4 .grid-row4");
  const btnNew = document.getElementById("user-new");
  const btnMod = document.getElementById("user-modi");
  const btnDel = document.getElementById("user-del");

  const modal = document.getElementById("template-modal");
  const modalTitle = document.getElementById("template-modal-title");
  const inputName = document.getElementById("template-name");
  const inputDesc = document.getElementById("template-desc");
  const btnCancel = document.getElementById("template-cancel");
  const btnSave = document.getElementById("template-save");

  let selectedIdx = null;
  let mode = "create";

  function openModal(nextMode, row) {
    mode = nextMode;
    if (mode === "create") {
      modalTitle.textContent = "템플릿 등록";
      inputName.value = "";
      inputDesc.value = "";
    } else {
      modalTitle.textContent = "템플릿 수정";
      inputName.value = row.children[0].textContent.trim();
      inputDesc.value = row.children[1].textContent.trim();
    }
    modal.style.display = "flex";
  }

  function closeModal() {
    modal.style.display = "none";
  }

  rows.forEach((row) => {
    row.addEventListener("click", function () {
      rows.forEach((r) => r.classList.remove("selected-row"));
      row.classList.add("selected-row");
      selectedIdx = row.getAttribute("data-template-idx");
    });
  });

  btnNew?.addEventListener("click", function () {
    openModal("create");
  });

  btnMod?.addEventListener("click", function () {
    const selected = document.querySelector(".grid-row4.selected-row");
    if (!selected || !selectedIdx) {
      alert("수정할 템플릿을 선택해주세요.");
      return;
    }
    openModal("edit", selected);
  });

  btnDel?.addEventListener("click", function () {
    if (!selectedIdx) {
      alert("삭제할 템플릿을 선택해주세요.");
      return;
    }
    if (!confirm("선택한 템플릿을 삭제하시겠습니까?")) return;
    fetch("/csm/core/setting/templateDelete", {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify({ idx: Number(selectedIdx) }),
    })
      .then((r) => r.json())
      .then((res) => {
        if (res.result === "1") location.reload();
        else alert(res.message || "삭제에 실패했습니다.");
      })
      .catch(() => alert("삭제 요청 중 오류가 발생했습니다."));
  });

  btnCancel?.addEventListener("click", closeModal);

  btnSave?.addEventListener("click", function () {
    const name = inputName.value.trim();
    const description = inputDesc.value.trim();
    if (!name) {
      alert("템플릿명을 입력해주세요.");
      return;
    }

    const url = mode === "create" ? "/csm/core/setting/templateInsert" : "/csm/core/setting/templateModify";
    const payload = mode === "create"
      ? { name, description }
      : { idx: Number(selectedIdx), name, description };

    fetch(url, {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(payload),
    })
      .then((r) => r.json())
      .then((res) => {
        if (res.result === "1") {
          closeModal();
          location.reload();
        } else {
          alert(res.message || "저장에 실패했습니다.");
        }
      })
      .catch(() => alert("저장 요청 중 오류가 발생했습니다."));
  });
});
