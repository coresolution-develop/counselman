$(document).ready(function() {
	const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
	if (csrfToken && csrfHeader) {
		$.ajaxSetup({ headers: { [csrfHeader]: csrfToken } });
	}

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
	$('#us_col_05').on('change', function() {
		$.ajax({
			type: 'POST',
			url: '/csm/core/instfind',
			data: {
				'id_col_02': $('#us_col_05').val()
			},
			datatype: 'json',
			success: function(data) {
				if (data && data.instNumber) {
					$('#id_col_07').val(data.instNumber);
					console.log('instNumber: '+ data.instNumber);
				} else {
					console.log('instNumber 데이터가 없습니다.');
				}
			},
			error: function(xht, status, error) {
				console.error('Ajax 요청 실패 : ', status, error);
			}
		});
	});
	$('#submit-btn').click(function() {
		var us_col_02 = $('#us_col_02').val();
		var us_col_03 = $('#us_col_03').val();
		var us_col_12 = $('#us_col_12').val();

		if (!us_col_02 || !us_col_02.trim()) {
			alert('아이디를 입력해주세요.');
			return;
		}
		if (!us_col_03 || !us_col_03.trim()) {
			alert('비밀번호를 입력해주세요.');
			return;
		}
		if (!us_col_12 || !us_col_12.trim()) {
			alert('이름을 입력해주세요.');
			return;
		}

		var select = document.getElementById('us_col_05');
		var selectedOption = select.options[select.selectedIndex];
		var us_col_04 = selectedOption.getAttribute('data-col-03');

		if (!us_col_04 || !us_col_04.trim()) {
			alert('소속기관을 선택해주세요.');
			return;
		}

		var us_col_05 = $('#us_col_05').val();
		var us_col_06 = $('#us_col_06').val();
		var us_col_08 = $('#us_col_08').val();
		var us_col_10 = $('#us_col_10').val();
		var us_col_11 = $('#us_col_11').val();
		var us_col_13 = $('#us_col_13').val();
		var us_col_14 = $('#us_col_14').val();
		var id_col_07 = $('#id_col_07').val();

		spinner();
		$.ajax({
			url : '/csm/core/newuser/post',
			type: 'post',
			dataType: 'json',
			data: {
				'us_col_02': us_col_02,
				'us_col_03': us_col_03,
				'us_col_04': us_col_04,
				'us_col_05': us_col_05,
				'us_col_06': us_col_06,
				'us_col_08': us_col_08,
				'us_col_10': us_col_10,
				'us_col_11': us_col_11,
				'us_col_12': us_col_12,
				'us_col_13': us_col_13,
				'us_col_14': us_col_14,
				'id_col_07': id_col_07
			},
			success: function(response) {
				hideSpinner();
				if (response && response.result === true) {
					showSuccessModal('사용자 등록이 완료되었습니다.');
				} else {
					alert(response && response.msg ? response.msg : '사용자 등록에 실패했습니다.');
				}
			},
			error: function(error) {
				hideSpinner();
				console.log('Error: ', error);
				alert('서버 오류가 발생했습니다.');
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
