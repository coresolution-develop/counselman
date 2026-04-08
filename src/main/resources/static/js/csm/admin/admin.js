document.addEventListener("DOMContentLoaded", function() {


	
	$("#user-del").click(function() {
		// Get the hidden ID of the selected row
		const selectedRow = $(".selected-row");
		if (selectedRow.length > 0) {
	        const userid = selectedRow.find("input[name='us_col_02']").val();
	        const instCode = selectedRow.find("input[name='us_col_04']").val();
	        const id = selectedRow.find("input[name='us_col_01']").val();
	        if (!id) {
	        	alert("유효한 사용자를 선택해 주세요.");
	        	return;
	        }
			if (confirm("사용자를 삭제하시겠습니까?")) {
				userDelete(id, instCode);
			}
		} else {
			alert("사용자를 먼저 선택해 주세요.");
		}
	});
	
	$('#user-modi').click(function() {
		const selectedRow = $(".selected-row");
		if (selectedRow.length > 0) {
	        const userid = selectedRow.find("input[name='us_col_02']").val();
	        const instCode = selectedRow.find("input[name='us_col_04']").val();
	        const id = selectedRow.find("input[name='us_col_01']").val();
	        if (!id) {
	        	alert("유효한 사용자를 선택해 주세요.");
	        	return;
	        }
	        
			userModify(id, instCode);
		} else {
			alert("사용자를 먼저 선택해 주세요.");
		}
	});

	function userDelete(id, instCode) {

		$.ajax({
			url: '/csm/user/delete',
			type: 'post',
			dataType: 'json',
			data: {
				'us_col_01': id,
				'us_col_04': instCode
			},
			success: function(response) {
				console.log('Delete Success: ', response);
				window.location.reload();
				
			},
			error: function(error) {
				console.log('Delete Error: ', error);

			}
		});
	}

	function userModify(id, instCode) {
		
	    var width = 500;
	    var height = 750;
	    var left = (window.screen.width / 2) - (width / 2);
	    var top = (window.screen.height / 2) - (height / 2);
	    
	    window.open("/csm/modifyuserPopup?us_col_01=" + id + "&instCode=" + instCode, "popupWindow", 
	                "width=" + width + ",height=" + height + ",scrollbars=yes,left=" + left + ",top=" + top);
	}
	
	const rows = document.querySelectorAll('.grid-body .grid-row');
	
	let selectedId = "";
	let selectedName = "";
	let selectedSub = "";
	let selectedPosition = "";
	let selectedInstitutionCode = "";
	let selectedInstitutionName = "";
	let selectedNote = "";
	let selectedStatus = "";
	let selectedGrade = "";
	let selectedEmail = "";
	
	rows.forEach(function(row) {
		// 사용자 관리 리스트(7개 컬럼)에서만 동작하도록 제한
		if (row.children.length < 7) return;
		row.addEventListener('click', function() {
			// 모든 행에서 선택된 상태 제거
			rows.forEach(function(r) {
				r.classList.remove('selected-row');
			});

			// 클릭된 행에 선택된 상태 추가

			this.classList.add('selected-row');
			// 선택된 행의 데이터를 가져오는 로직
			const cells = this.children;
			selectedId = cells[0].textContent.trim();
			selectedName = cells[1].textContent.trim();
			selectedSub = cells[2].textContent.trim();
			selectedPosition = cells[3].textContent.trim();
			selectedGrade = cells[4].textContent.trim();
			selectedEmail = cells[5].textContent.trim();
			selectedStatus = cells[6].textContent.trim();
//	        hiddenId = this.querySelector('input[name="us_col_02"]').value;

			console.log("selected Id : ", selectedId);
			console.log("Selected Name : ", selectedName);
			console.log("Selected Sub : ", selectedSub);
			console.log("Selected Position :", selectedPosition);
			console.log("Selected Grade :", selectedGrade);
			console.log("Selected Email :", selectedEmail);
			console.log("Selected Status :", selectedStatus);
//			console.log("Hidden ID (us_col_01):", hiddenId);



		});
	});

});

function openUserPopup() {
    var width = 500;
    var height = 750;
    var left = (window.screen.width / 2) - (width / 2);
    var top = (window.screen.height / 2) - (height / 2);
    
    var features = "width=" + width + 
                   ",height=" + height + 
                   ",top=" + top + 
                   ",left=" + left + 
                   ",scrollbars=yes" + 
                   ",resizable=yes" +  // 창 크기 조절 가능
                   ",toolbar=no" +      // 도구 모음 숨기기
                   ",location=yes" +     // 주소창 숨기기 (일부 브라우저에서만 적용)
                   ",directories=no" +  // 디렉토리 숨기기
                   ",status=no" +       // 상태 표시줄 숨기기
                   ",menubar=no";       // 메뉴 바 숨기기
                   
    window.open("/csm/newuserPopup", "popupWindow", features);
}
