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
	$('#submit-btn').click(function() {
		var id_col_02 = $('#id_col_02').val();
		var id_col_03 = $('#id_col_03').val();
		var id_col_09 = $('#id_col_09').val();
		var id_col_06 = $('#id_col_06').val();
		var id_col_07 = $('#id_col_07').val();
		var id_col_08 = $('#id_col_08').val();
		var id_col_05 = $('#id_col_05').val();

		if (!id_col_02 || !id_col_02.trim()) {
			alert('기관명을 입력해주세요.');
			return;
		}
		if (!id_col_03 || !id_col_03.trim()) {
			alert('기관코드를 입력해주세요.');
			return;
		}
		if (!/^[A-Za-z0-9_]{2,20}$/.test(id_col_03.trim())) {
			alert('기관코드는 영문/숫자/밑줄(_)만 사용 가능하며 2~20자이어야 합니다.');
			return;
		}

		spinner();
		$.ajax({
			url : '/csm/core/inst/post',
			type: 'post',
			dataType: 'json',
			data: {
				'id_col_02': id_col_02,
				'id_col_03': id_col_03,
				'id_col_09': id_col_09,
				'id_col_06': id_col_06,
				'id_col_07': id_col_07,
				'id_col_08': id_col_08,
				'id_col_05': id_col_05
			},
			success: function(response) {
				hideSpinner();
				if (response && response.result === '1') {
					showSuccessModal('기관생성이 완료되었습니다.');
				} else {
					alert(response && response.msg ? response.msg : '기관 생성에 실패했습니다. 기관코드가 중복되거나 유효하지 않을 수 있습니다.');
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
