document.addEventListener("DOMContentLoaded", function () {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
  function jsonHeaders() {
    const h = { "Content-Type": "application/json" };
    if (csrfToken && csrfHeader) h[csrfHeader] = csrfToken;
    return h;
  }
  const rows = document.querySelectorAll(".grid-body4 .grid-row4");
  const btnNew = document.getElementById("new-price");
  const btnMod = document.getElementById("modi-price");

  const modalAll = document.getElementById("price-all-modal");
  const modalOne = document.getElementById("price-one-modal");

  let selectedInst = null;
  let selectedSms = "";
  let selectedLms = "";
  let selectedMms = "";

  function open(el) { el.style.display = "flex"; }
  function close(el) { el.style.display = "none"; }

  rows.forEach((row) => {
    row.addEventListener("click", function () {
      rows.forEach((r) => r.classList.remove("selected-row"));
      row.classList.add("selected-row");
      selectedInst = row.getAttribute("data-inst-code");
      selectedSms = row.getAttribute("data-sms") || "";
      selectedLms = row.getAttribute("data-lms") || "";
      selectedMms = row.getAttribute("data-mms") || "";
    });
  });

  btnNew?.addEventListener("click", function () {
    document.getElementById("smspriceAll").value = "";
    document.getElementById("lmspriceAll").value = "";
    document.getElementById("mmspriceAll").value = "";
    open(modalAll);
  });

  btnMod?.addEventListener("click", function () {
    if (!selectedInst) {
      alert("기관을 선택해주세요.");
      return;
    }
    document.getElementById("selected-inst-code").textContent = selectedInst;
    document.getElementById("smsprice").value = selectedSms;
    document.getElementById("lmsprice").value = selectedLms;
    document.getElementById("mmsprice").value = selectedMms;
    open(modalOne);
  });

  document.getElementById("price-all-cancel")?.addEventListener("click", () => close(modalAll));
  document.getElementById("price-one-cancel")?.addEventListener("click", () => close(modalOne));

  document.getElementById("price-all-save")?.addEventListener("click", function () {
    const payload = {
      id_col_03: "all",
      sms_price: document.getElementById("smspriceAll").value.trim(),
      lms_price: document.getElementById("lmspriceAll").value.trim(),
      mms_price: document.getElementById("mmspriceAll").value.trim(),
    };
    fetch("/csm/core/smssetting/priceInsert", {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(payload),
    })
      .then((r) => r.json())
      .then((res) => {
        if (res.result === "1") location.reload();
        else alert(res.message || "저장 실패");
      })
      .catch(() => alert("요청 실패"));
  });

  document.getElementById("price-one-save")?.addEventListener("click", function () {
    if (!selectedInst) return;
    const payload = {
      id_col_03: selectedInst,
      sms_price: document.getElementById("smsprice").value.trim(),
      lms_price: document.getElementById("lmsprice").value.trim(),
      mms_price: document.getElementById("mmsprice").value.trim(),
    };
    fetch("/csm/core/smssetting/priceInsert", {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(payload),
    })
      .then((r) => r.json())
      .then((res) => {
        if (res.result === "1") location.reload();
        else alert(res.message || "저장 실패");
      })
      .catch(() => alert("요청 실패"));
  });
});
