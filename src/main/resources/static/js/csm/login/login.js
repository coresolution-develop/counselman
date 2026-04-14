/**
 * 
 */

document.addEventListener('DOMContentLoaded', function() {
	// CSRF 메타 읽기
	const csrfToken  = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

	const savedInst = localStorage.getItem('inst');
	const savedId = localStorage.getItem('userId');
	
	const instInput = document.getElementById('us_col_04');
	const idInput = document.getElementById('us_col_02');
	
	if (instInput && savedInst) {
		instInput.value = savedInst;
	}
	if (idInput && savedId) {
		idInput.value = savedId;
	}
	
	$("#user-new").click(function() {
		console.log("add - click");
	});
	$("#confirm").click(function(){
//	      modalClose(); //모달 닫기 함수 호출
	      
	      login();
	  });
	function login() {
//		console.log("test");
		if (instInput) {
			const inst = instInput.value;
			localStorage.setItem('inst', inst);
		}
		if (idInput) {
			const id = idInput.value;
			localStorage.setItem('userId', id);
		}
		
		$.ajax({
			url: '/csm/login/post',
			type: 'post',
			dataType: 'json',
			data: {
				'us_col_04' : $("#us_col_04").val(),
				'us_col_02' : $("#us_col_02").val(),
				'us_col_03' : $("#us_col_03").val()
			},
			beforeSend: function (xhr) {
				if (csrfHeader && csrfToken) xhr.setRequestHeader(csrfHeader, csrfToken);
			},
			success: function (response) {
//            console.log('Success:', response);
				if(response.result == "1") {
					$('#result').css('color', '#fff');
					$('#result').html('로그인 중입니다. 잠시만 기다려주세요.');
					if (response.redirect) {
						location.href = response.redirect;
					} else {
						location.href= "/csm/counsel/list?page=1&perPageNum=10&comment=";
					}
				} else {
					$('#result').html(response.message);
					$('#us_col_03').val('');
					$('#us_col_03').focus();
				}
			},
			error: function (error) {
            console.log('Error:', error);
				
			}
		});
		
	}
	
	// 엔터키로 로그인 구현
	$('#us_col_02').on('keypress', function (e) {
		if (e.keyCode === 13) {
			$('#confirm').click();
		}
	});
	$('#us_col_03').on('keypress', function (e) {
		if (e.keyCode === 13) {
			$('#confirm').click();
		}
	});
	$('#us_col_04').on('keypress', function (e) {
		if (e.keyCode === 13) {
			$('#confirm').click();
		}
	});
	// 입력 필드 및 선택 박스 요소 가져오기
	const inputs = document.querySelectorAll("#us_col_02, #us_col_03, #us_col_04");
	const resultDiv = document.getElementById("result");
	
	// 입력 필드나 선택 박스 값이 변경될 때 result 메시지 초기화
	inputs.forEach(input => {
		input.addEventListener("input", function () {
		    resultDiv.innerHTML = "";
		});
		
		// select 요소는 change 이벤트 사용
		if (input.tagName === "SELECT") {
		    input.addEventListener("change", function () {
		        resultDiv.innerHTML = "";
		    });
	    }
	});
	const $form = $('form');
$form.on('submit', function(e) {
  e.preventDefault();   // ★ 중요
  login();
});

	
});
