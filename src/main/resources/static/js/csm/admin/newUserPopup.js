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
		// 이메일 도메인 추천 리스트
	    var commonDomains = ["gmail.com", "naver.com", "daum.net", "yahoo.com", "outlook.com", "icloud.com", "hanmail.net", "nate.com"];

		
		var us_col_02 = $('#us_col_02').val();
		var us_col_05 = $('#us_col_05').val();
//		var us_col_08 = $('#us_col_08 option:selected');
//		var us_col_07 = $('#us_col_07 option:selected');
//		var us_col_11 = $('#us_col_11').val();
//		var us_col_06 = $('#us_col_06').val();
		
		var us_col_08 = $('#us_col_08').val();
		var us_col_07 = $('#us_col_07').val();
		var us_col_11 = $('#us_col_11').val();
		var us_col_06 = $('#us_col_06').val();
	    var us_col_12 = $('#us_col_12').val();
	    var us_col_14 = $('#us_col_14').val();
	    var us_col_13 = $('#us_col_13').val();
	    
		console.log('아이디 :'+ us_col_02);
		console.log('사용권한 : ' + us_col_08);
		console.log('사용상태 : ' + us_col_07);
		console.log('Email : ' + us_col_11);
		console.log('비고 : ' +  us_col_06);
		console.log('이름 : ' + us_col_12);
		console.log();
		
		if (us_col_08 == '' ||
			us_col_07 == '' ||
			us_col_11 == '' ||
			us_col_12 == '' ||
			us_col_14 == '' ||
			us_col_13 == ''
		) {
//			alert('항목을 작성해주세요.'); 
			showFailModal('항목을 작성해주세요.')
			return;
		}
		// ✅ 이메일 정규식 (기본 유효성 검사)
        var emailPattern = /^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,6})$/;
        if (!emailPattern.test(us_col_11)) {
            showFailModal('올바른 이메일 주소를 입력하세요.');
            $('#us_col_11').focus();
            return;
        }
        // ✅ 이메일 도메인 오타 감지
        var emailParts = us_col_11.split('@');
        if (emailParts.length === 2) {
            var userPart = emailParts[0]; // "user"
            var domainPart = emailParts[1]; // "gamil.com"

            var suggestedDomain = findClosestDomain(domainPart, commonDomains);
            if (suggestedDomain && suggestedDomain !== domainPart) {
                showFailModal(`혹시 이메일이 '<b>${userPart}@${suggestedDomain}</b>' 인가요? <br> 정확한 이메일을 입력해주세요.`);
                $('#us_col_11').focus();
                return;
            }
        }
        
		spinner();
		$.ajax({
			url : '/csm/newuser/post',
			type: 'post',
			dataType: 'json',
			data: {
				'us_col_02': us_col_02,
				'us_col_06': us_col_06,
				'us_col_05': us_col_05,
				'us_col_07': us_col_07,
				'us_col_08': us_col_08,
				'us_col_11': us_col_11,
				'us_col_12': us_col_12,
				'us_col_13': us_col_13,
				'us_col_14': us_col_14
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
	// ✅ 입력된 이메일 도메인과 가장 유사한 도메인 찾기 (Levenshtein Distance 알고리즘 사용)
    function findClosestDomain(inputDomain, domainList) {
        let minDistance = Infinity;
        let closestDomain = null;

        domainList.forEach(domain => {
            let distance = levenshteinDistance(inputDomain, domain);
            if (distance < minDistance && distance <= 2) { // 오타 허용 범위 2글자까지
                minDistance = distance;
                closestDomain = domain;
            }
        });

        return closestDomain;
    }

    // ✅ Levenshtein Distance (문자열 유사도 측정)
    function levenshteinDistance(s1, s2) {
        const len1 = s1.length;
        const len2 = s2.length;
        const matrix = [];

        if (len1 === 0) return len2;
        if (len2 === 0) return len1;

        for (let i = 0; i <= len2; i++) {
            matrix[i] = [i];
        }
        for (let j = 0; j <= len1; j++) {
            matrix[0][j] = j;
        }

        for (let i = 1; i <= len2; i++) {
            for (let j = 1; j <= len1; j++) {
                if (s2.charAt(i - 1) === s1.charAt(j - 1)) {
                    matrix[i][j] = matrix[i - 1][j - 1];
                } else {
                    matrix[i][j] = Math.min(
                        matrix[i - 1][j - 1] + 1, // substitution
                        Math.min(
                            matrix[i][j - 1] + 1, // insertion
                            matrix[i - 1][j] + 1 // deletion
                        )
                    );
                }
            }
        }
        return matrix[len2][len1];
    }
	
});

function showSuccessModal(message) {
    const modal = document.querySelector('.modal');
    const body = document.querySelector('body');
    const menuMsg = document.querySelector('.menu_msg');
    menuMsg.innerText = message;
    menuMsg.style.fontSize = '16px';
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
//        window.opener.location.reload();
//        window.close();
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
