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
//		var us_col_05 = $('#us_col_05 option:selected');
		
		var select = document.getElementById('us_col_05');
		var selectedOption = select.options[select.selectedIndex];
		var us_col_04 = selectedOption.getAttribute('data-col-03');
		
//		var us_col_08 = $('#us_col_08 option:selected');
//		var us_col_07 = $('#us_col_07 option:selected');
//		var us_col_11 = $('#us_col_11').val();
//		var us_col_06 = $('#us_col_06').val();
		
		var us_col_05 = $('#us_col_05').val();
		var us_col_08 = $('#us_col_08').val();
		var us_col_07 = $('#us_col_07').val();
		var us_col_11 = $('#us_col_11').val();
		var us_col_06 = $('#us_col_06').val();
		var us_col_12 = $('#us_col_12').val();
		var us_col_13 = $('#us_col_13').val();
		var us_col_14 = $('#us_col_14').val();
		var us_col_10 = $('#us_col_10').val();
	    var id_col_07 = $('#id_col_07').val();
		console.log('사용자명(로그인 아이디) :'+ us_col_02);
		console.log('소속기관 : ' + us_col_05);
		console.log('소속기관코드 : ' + us_col_04);
		console.log('사용권한 : ' + us_col_08);
		console.log('사용상태 : ' + us_col_07);
		console.log('Email : ' + us_col_11);
		console.log('이름 : ' + us_col_12);
		console.log('직급 : ' + us_col_14);
		console.log('연락처 : ' + us_col_10);
		console.log('비고 : ' +  us_col_06);
		console.log('부서 : ' +  us_col_13);
		console.log('기관코드: '+id_col_07);
		spinner();
		$.ajax({
			url : '/csm/core/newuser/post',
			type: 'post',
			dataType: 'json',
			data: {
				'us_col_02': us_col_02,
				'us_col_04': us_col_04,
				'us_col_05': us_col_05,
				'us_col_06': us_col_06,
				'us_col_07': us_col_07,
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
				console.log('Success: ', response);
				showSuccessModal('이메일로 비밀번호 설정 링크를 전송하였습니다.');
			},
			error: function(error) {
				console.log('Error: ', error);

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
