document.addEventListener("DOMContentLoaded", function () {
    const form = document.querySelector(".login-form");
    const instInput = document.getElementById("instCode");
    const usernameInput = document.getElementById("username");
    const passwordInput = document.getElementById("password");

    const INST_KEY = "mediplat.login.inst";
    const USERNAME_KEY = "mediplat.login.username";

    function safeStorageGet(key) {
        try {
            return localStorage.getItem(key);
        } catch (e) {
            return null;
        }
    }

    function safeStorageSet(key, value) {
        try {
            localStorage.setItem(key, value);
        } catch (e) {
            // ignore storage errors
        }
    }

    if (instInput && !instInput.value) {
        const savedInst = safeStorageGet(INST_KEY);
        if (savedInst) {
            instInput.value = savedInst;
        }
    }
    if (usernameInput && !usernameInput.value) {
        const savedUsername = safeStorageGet(USERNAME_KEY);
        if (savedUsername) {
            usernameInput.value = savedUsername;
        }
    }

    function persistLoginInputs() {
        if (instInput) {
            safeStorageSet(INST_KEY, (instInput.value || "").trim());
        }
        if (usernameInput) {
            safeStorageSet(USERNAME_KEY, (usernameInput.value || "").trim());
        }
    }

    if (form) {
        form.addEventListener("submit", persistLoginInputs);
    }
    if (instInput) {
        instInput.addEventListener("blur", persistLoginInputs);
    }
    if (usernameInput) {
        usernameInput.addEventListener("blur", persistLoginInputs);
    }

    if (!instInput || !usernameInput || !passwordInput) {
        return;
    }
    if (instInput.value.trim() && usernameInput.value.trim()) {
        passwordInput.focus();
        return;
    }
    if (!instInput.value.trim()) {
        instInput.focus();
        return;
    }
    usernameInput.focus();
});
