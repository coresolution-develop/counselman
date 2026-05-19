document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('resetPwdForm');
    if (!form) return;

    const pw1 = document.getElementById('user_password1');
    const pw2 = document.getElementById('user_password2');
    const resultDiv = document.getElementById('result');
    const submitBtn = document.getElementById('submitBtn');

    function setAlert(msg, ok) {
        if (!msg) {
            resultDiv.style.display = 'none';
            resultDiv.textContent = '';
            return;
        }
        resultDiv.className = 'alert ' + (ok ? 'alert--ok' : 'alert--err');
        resultDiv.textContent = msg;
        resultDiv.style.display = 'block';
    }

    function csrfHeader(xhr) {
        const token  = document.querySelector('meta[name="_csrf"]')?.content || '';
        const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
        if (token) xhr.setRequestHeader(header, token);
    }

    function showSpinner(on) {
        document.getElementById('spinner-overlay')?.classList.toggle('show', !!on);
    }

    function submit() {
        setAlert('', false);
        const p1 = pw1.value;
        const p2 = pw2.value;
        if (!p1 || !p2) {
            setAlert('새 비밀번호와 확인을 모두 입력해 주세요.', false);
            return;
        }
        if (p1 !== p2) {
            setAlert('비밀번호가 일치하지 않습니다.', false);
            pw2.focus();
            return;
        }

        const payload = {
            us_col_01: form.querySelector('input[name="us_col_01"]').value,
            us_col_03: p1,
            inst:      form.querySelector('input[name="inst"]').value,
            token:     form.querySelector('input[name="token"]').value
        };

        submitBtn.disabled = true;
        showSpinner(true);
        $.ajax({
            type: 'POST',
            url: form.getAttribute('action'),
            beforeSend: csrfHeader,
            data: JSON.stringify(payload),
            contentType: 'application/json; charset=utf-8',
            success: function () {
                showSpinner(false);
                setAlert('비밀번호가 변경되었습니다. 잠시 후 로그인 화면으로 이동합니다.', true);
                setTimeout(() => { window.location.href = '/csm/login'; }, 1500);
            },
            error: function (xhr) {
                showSpinner(false);
                submitBtn.disabled = false;
                const msg = xhr?.responseText || '비밀번호 변경에 실패했습니다.';
                setAlert(msg, false);
            }
        });
    }

    form.addEventListener('submit', function (e) { e.preventDefault(); submit(); });

    [pw1, pw2].forEach(el => el.addEventListener('input', () => setAlert('', false)));
});
