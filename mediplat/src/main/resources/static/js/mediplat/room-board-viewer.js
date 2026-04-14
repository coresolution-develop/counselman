document.addEventListener("DOMContentLoaded", function () {
    const tabs = Array.from(document.querySelectorAll(".viewer-mobile-tab"));
    const cards = Array.from(document.querySelectorAll(".viewer-card"));
    if (!tabs.length || !cards.length) {
        return;
    }

    function selectInstitution(instCode) {
        tabs.forEach((tab) => {
            tab.classList.toggle("is-active", tab.dataset.inst === instCode);
        });
        cards.forEach((card) => {
            card.classList.toggle("is-active", card.dataset.inst === instCode);
        });
    }

    tabs.forEach((tab) => {
        tab.addEventListener("click", function () {
            const instCode = String(tab.dataset.inst || "").trim();
            if (!instCode) {
                return;
            }
            selectInstitution(instCode);
        });
    });
});
