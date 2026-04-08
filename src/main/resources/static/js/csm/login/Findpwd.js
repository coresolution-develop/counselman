/**
 * 
 */

document.addEventListener('DOMContentLoaded', function() {
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
	      
	      findpwd();
	  });
	function findpwd() {
//		console.log("test");

		if (instInput) {
			const inst = instInput.value;
			localStorage.setItem('inst', inst);
		}
		if (idInput) {
			const id = idInput.value;
			localStorage.setItem('userId', id);
		}
		spinner();
		$.ajax({
			url: '/csm/findpwd/post',
			type: 'post',
			beforeSend: function(xhr) {
				const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
				const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
				if (csrfToken) {
					xhr.setRequestHeader(csrfHeader, csrfToken);
				}
			},
			dataType: 'json',
			data: {
				'us_col_04' : $("#us_col_04").val(),
				'us_col_02' : $("#us_col_02").val(),
				'us_col_12' : $("#us_col_12").val()
			},
			success: function (response) {
				hideSpinner();
//            	console.log('Success:', response);
				if(response.result == true) {
					$('#result').css('color', '#fff');
					$('#result').html(response.msg);
				} else {
					$('#result').css('color', 'red');
					$('#us_col_03').val('');
					$('#us_col_12').val('');
					$('#us_col_03').focus();
					$('#result').html(response.msg);
				}
			},
			error: function (error) {
				hideSpinner();
	            console.log('Error:', error);
				
			}
		});
		
	}
	
	
	// 엔터키로 로그인 구현
	$('#us_col_02'),$('#us_col_12').on('keypress', function (e) {
		if (e.keyCode === 13) {
			$('#confirm').click();
		}
	});
	$('#us_col_03').on('keypress', function (e) {
		if (e.keyCode === 13) {
			$('#confirm').click();
		}
	});
	// 입력 필드 및 선택 박스 요소 가져오기
	const inputs = document.querySelectorAll("#us_col_02, #us_col_12, #us_col_04");
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
	
	
});

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
