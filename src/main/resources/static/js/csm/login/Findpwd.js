document.addEventListener('DOMContentLoaded', function () {
    const savedInst = localStorage.getItem('inst');
    const savedId = localStorage.getItem('userId');

    const instInput = document.getElementById('us_col_04');
    const idInput = document.getElementById('us_col_02');

    if (instInput && savedInst) instInput.value = savedInst;
    if (idInput && savedId) idInput.value = savedId;

    const findForm = document.getElementById('findForm');
    const otpForm = document.getElementById('otpForm');
    const resultDiv = document.getElementById('result');
    const otpResultDiv = document.getElementById('otpResult');
    const otpPhoneMask = document.getElementById('otpPhoneMask');

    let pendingInst = '';
    let pendingUserId = '';

    function csrfHeader(xhr) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHdr = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        if (csrfToken) xhr.setRequestHeader(csrfHdr, csrfToken);
    }

    function showResult(msg, ok) {
        resultDiv.style.color = ok ? '#fff' : 'red';
        resultDiv.innerHTML = msg || '';
    }

    function showOtpResult(msg, ok) {
        otpResultDiv.style.color = ok ? '#1a7f37' : 'red';
        otpResultDiv.innerHTML = msg || '';
    }

    function switchToOtp(phoneMask) {
        otpPhoneMask.textContent = phoneMask || '***-****-****';
        findForm.style.display = 'none';
        otpForm.style.display = '';
        showOtpResult('', false);
        document.getElementById('otpCode').focus();
    }

    function switchToFind() {
        otpForm.style.display = 'none';
        findForm.style.display = '';
        document.getElementById('otpCode').value = '';
        showOtpResult('', false);
    }

    function findpwd() {
        const inst = instInput?.value || '';
        const id = idInput?.value || '';
        const name = document.getElementById('us_col_12').value;
        const channel = document.querySelector('input[name="channel"]:checked')?.value || 'email';

        localStorage.setItem('inst', inst);
        localStorage.setItem('userId', id);

        pendingInst = inst;
        pendingUserId = id;

        spinner();
        $.ajax({
            url: '/csm/findpwd/post',
            type: 'post',
            beforeSend: csrfHeader,
            dataType: 'json',
            data: {
                us_col_04: inst,
                us_col_02: id,
                us_col_12: name,
                channel: channel
            },
            success: function (response) {
                hideSpinner();
                if (response.result === true) {
                    if (response.requireOtp === true) {
                        switchToOtp(response.phoneMask);
                    } else {
                        showResult(response.msg, true);
                    }
                } else {
                    showResult(response.msg, false);
                    document.getElementById('us_col_12').focus();
                }
            },
            error: function (error) {
                hideSpinner();
                const message = error?.responseJSON?.msg
                    || error?.responseText
                    || '비밀번호 변경 요청 중 오류가 발생했습니다.';
                showResult(message, false);
            }
        });
    }

    function verifyOtp() {
        const code = (document.getElementById('otpCode').value || '').trim();
        if (!/^\d{6}$/.test(code)) {
            showOtpResult('6자리 숫자를 입력해 주세요.', false);
            return;
        }
        spinner();
        $.ajax({
            url: '/csm/findpwd/verify-otp',
            type: 'post',
            beforeSend: csrfHeader,
            dataType: 'json',
            data: {
                us_col_04: pendingInst,
                us_col_02: pendingUserId,
                code: code
            },
            success: function (response) {
                hideSpinner();
                if (response.result === true && response.redirect) {
                    showOtpResult('인증되었습니다. 이동합니다…', true);
                    window.location.href = response.redirect;
                } else {
                    showOtpResult(response.msg || '인증에 실패했습니다.', false);
                }
            },
            error: function (error) {
                hideSpinner();
                const message = error?.responseJSON?.msg
                    || '인증 요청 중 오류가 발생했습니다.';
                showOtpResult(message, false);
            }
        });
    }

    $('#confirm').click(findpwd);
    $('#otpConfirm').click(verifyOtp);
    $('#otpBack').click(switchToFind);

    // 엔터키
    $('#us_col_12, #us_col_02, #us_col_04').on('keypress', function (e) {
        if (e.keyCode === 13) $('#confirm').click();
    });
    $('#otpCode').on('keypress', function (e) {
        if (e.keyCode === 13) $('#otpConfirm').click();
    });

    // 입력 시 result 초기화
    document.querySelectorAll('#us_col_02, #us_col_12, #us_col_04').forEach(input => {
        input.addEventListener('input', () => { resultDiv.innerHTML = ''; });
    });
});

function spinner() {
    const el = document.getElementById('spinner-overlay');
    if (el) {
        el.classList.add('show');
        el.style.display = 'flex';
    }
}

function hideSpinner() {
    const el = document.getElementById('spinner-overlay');
    if (el) el.style.display = 'none';
}
