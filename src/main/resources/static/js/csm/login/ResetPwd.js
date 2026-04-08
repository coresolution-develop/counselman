$(document).ready(function() {
    var errorMessage = $("#errorMessage").text();
    if (errorMessage) {
        showErrorModal(errorMessage);
    }

    $("form").on("submit", function(event) {
        event.preventDefault(); // 기본 폼 제출 방지

        var password1 = $("#user_password1").val();
        var password2 = $("#user_password2").val();

        if (password1 !== password2) {
            showSuccessModal2("비밀번호가 일치하지 않습니다.");
        } else {
            const modal3 = document.querySelector('.modal3');
            const body = document.querySelector('body');
            const menuMsg3 = document.querySelector('.menu_msg3');
            const confirmBtn = document.getElementById('confirmBtn');

            menuMsg3.innerText = "비밀번호를 변경 하시겠습니까?";
            modal3.classList.add('show');
            body.style.overflow = 'hidden';

            confirmBtn.onclick = function() {
                var formData = {
                    us_col_01: $("input[name='us_col_01']").val(),
                    us_col_03: $("#user_password1").val(),
                    inst: $("input[name='inst']").val(),
                    token: $("input[name='token']").val()
                };
				console.log(formData);
                $.ajax({
                    type: "POST",
                    beforeSend: function(xhr) {
                        const csrfToken = document.querySelector("meta[name=\"_csrf\"]")?.getAttribute("content") || "";
                        const csrfHeader = document.querySelector("meta[name=\"_csrf_header\"]")?.getAttribute("content") || "X-CSRF-TOKEN";
                        if (csrfToken) xhr.setRequestHeader(csrfHeader, csrfToken);
                    },
                    url: $("form").attr("action"),
                    data: JSON.stringify(formData),
                    contentType: "application/json; charset=utf-8",
                    success: function(response) {
                        modal3.classList.remove('show');
                        showSuccessModal("비밀번호 변경에 성공했습니다.");
                    },
                    error: function(xhr, status, error) {
                        modal3.classList.remove('show');
                        showSuccessModal("비밀번호 변경에 실패했습니다.");
                    }
                });
            };
        }
    });
});

function showErrorModal(message) {
    var messageDiv = document.getElementById('errorMessage');
    if (messageDiv) {
        messageDiv.textContent = message;
    } else {
        console.warn("errorMessage element not found.");
    }
    var modal = document.getElementById('errorModal');
    if (modal) {
        modal.classList.add('show');
    }
    document.body.style.overflow = 'hidden';

    const confirmBtn = document.getElementById('confirmBtn2');
    if (confirmBtn) {
        confirmBtn.onclick = function() {
            window.location.href = '/csm/login';
        };
    }
}
function closeErrorModal() {
    $("#errorModal").removeClass('show');
    $("body").css("overflow", "auto");
}

function showSuccessModal(message) {
    const modal = document.querySelector('.modal');
    const body = document.querySelector('body');
    const menuMsg = document.querySelector('.menu_msg');
    menuMsg.innerText = message;
    modal.classList.add('show');
    body.style.overflow = 'hidden';

    const confirmBtn = document.getElementById('confirmBtn2');
    confirmBtn.onclick = function() {
        window.location.href = '/csm/login';
    };
}

function showSuccessModal2(message) {
    const modal = document.querySelector('.modal2');
    const body = document.querySelector('body');
    const menuMsg = document.querySelector('.menu_msg2');
    menuMsg.innerText = message;
    modal.classList.add('show');
    body.style.overflow = 'hidden';
}

function redirectToLogin() {
    window.location.href = '/csm/login';
}
function closePopup(){
	var modal = document.querySelector('.modal');
	var body = document.querySelector('body');
	modal.classList.toggle('show');
	if (!modal.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
}

function closePopup2(){
	var modal2 = document.querySelector('.modal2');
	var body = document.querySelector('body');
	modal2.classList.toggle('show');
	if (!modal2.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
}

function closePopup3() {
    var modal3 = document.querySelector('.modal3');
    var body = document.querySelector('body');
    modal3.classList.toggle('show');
    if (!modal3.classList.contains('show')) {
        body.style.overflow = 'auto';
    }
}

function closePopup4() {
    var modal4 = document.querySelector('.modal4');
    var body = document.querySelector('body');
    modal4.classList.toggle('show');
    if (!modal4.classList.contains('show')) {
        body.style.overflow = 'auto';
    }
}

function closeErrorModalAndRedirect(redirect) {
    var modal = document.getElementById('errorModal');
    modal.classList.remove('show');
    document.body.style.overflow = 'auto';

    if (redirect === 'true') {
        window.location.href = '/csm/login';
    }
}
