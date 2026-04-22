$(document).ready(function() {
	// 드롭다운 토글
	$(".drop-down .selected a").click(function(e) {
		e.preventDefault();
		var $options = $(this).parent().siblings('.options');
		$options.find('> ul').toggle();
	});

	// 옵션 선택 및 선택 후 옵션 숨기기
	$(".drop-down .options ul li a").click(function(e) {
		e.preventDefault();
		var text = $(this).html();
		var value = $(this).data('value');
		var $selected = $(this).closest('.options').siblings('.selected');
		$selected.find('> a > span').html(text);
		$("#selectedHospital").val(value);
		$(this).closest('ul').hide();
	});

	// 페이지의 다른 위치를 클릭하면 옵션 숨기기
	$(document).bind('click', function(e) {
		var $clicked = $(e.target);
		if (!$clicked.parents().hasClass("drop-down")) {
			$(".drop-down .options ul").hide();
		}
	});
	$('#submit-btn').click(function() {
		var us_col_02 = $('#us_col_02').val();
	    var us_col_12 = $('#us_col_12').val();
	    var us_col_13 = $('#us_col_13').val();
	    var us_col_14 = $('#us_col_14').val();
	    var us_col_11 = $('#us_col_11').val();
		var us_col_06 = $('#us_col_06').val();
		var us_col_08 = $('#us_col_08').val();
	    var us_col_01 = $('#us_col_01').val();
	    var us_col_07 = $('#us_col_07').val();
	    var new_password = $('#new_password').val();
	    var new_password_confirm = $('#new_password_confirm').val();

	    // 비밀번호 변경 요청 시 확인
	    if (new_password !== '') {
	    	if (new_password !== new_password_confirm) {
	    		showFailModal('새 비밀번호가 일치하지 않습니다.');
	    		$('#new_password_confirm').focus();
	    		return;
	    	}
	    }

		spinner();
		var data = {
			'us_col_01': us_col_01,
			'us_col_02': us_col_02,
			'us_col_12': us_col_12,
			'us_col_13': us_col_13,
			'us_col_14': us_col_14,
			'us_col_11': us_col_11,
			'us_col_06': us_col_06,
			'us_col_08': us_col_08,
			'us_col_07': us_col_07
		};
		if (new_password !== '') {
			data['new_password'] = new_password;
		}
		$.ajax({
			url : '/csm/modifyuserPopup/post',
			type: 'post',
			dataType: 'json',
			data: data,
			success: function(response) {
				hideSpinner();
				console.log('Success: ', response);
				if (response.result === '1') {
					showSuccessModal("변경이 완료되었습니다.");
				} else {
					showFailModal(response.msg || "수정에 실패했습니다. 다시 시도해주세요.");
				}
			},
			error: function(error) {
				hideSpinner();
				console.log('Error: ', error);
				showFailModal("오류가 발생했습니다. 다시 시도해주세요.");
			}
		});
	});
	
});

function showSuccessModal(message) {
    const modal = document.querySelector('.modal');
    const body = document.querySelector('body');
    const menuMsg = document.querySelector('.menu_msg');
    menuMsg.innerText = message;
    modal.classList.add('show');
    body.style.overflow = 'hidden';

    const confirmBtn = document.getElementById('confirmBtn2');
    confirmBtn.onclick = function() {
        modal.classList.remove('show');
        window.opener.location.reload();
        window.close();
    };
}

function showFailModal(message) {
    const modal = document.querySelector('.modal');
    const body = document.querySelector('body');
    const menuMsg = document.querySelector('.menu_msg');
    menuMsg.innerText = message;
    modal.classList.add('show');
    body.style.overflow = 'hidden';

    const confirmBtn = document.getElementById('confirmBtn2');
    confirmBtn.onclick = function() {
        modal.classList.remove('show');
    };
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
// spinner
function spinner() {
	const spinner = document.getElementById('spinner-overlay');
	if (spinner) {
		spinner.classList.add('show'); // Adds CSS transition
		spinner.style.display = 'flex'; // Ensure it's visible
	}
//		$("#popup").css('display', 'flex').hide().fadeIn();
}
// 스피너를 감추는 함수
function hideSpinner() {
    const spinner = document.getElementById('spinner-overlay');
    if (spinner) {
        spinner.style.display = 'none'; // 스피너를 화면에서 감춤
        // spinner.classList.remove('show');
    }
    // $("#popup").fadeOut(); 
    // jQuery로 팝업을 닫고 싶다면 주석 해제 후 사용
}
