document.addEventListener("DOMContentLoaded", function() {
	const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
	if (csrfToken && csrfHeader) {
		$.ajaxSetup({ headers: { [csrfHeader]: csrfToken } });
	}

//	$("#user-new").click(function() {
//		console.log("add - click");
//		$('#head-title').text('사용자 추가');
//
//		// Clear form inputs
//		$("#us_col_02").val('');
//		$("#us_col_03").val('');
//		$("#us_col_04").val('');
//		$("#us_col_05").val('');
//
//		$("#save").attr('id', 'insert').text('Insert');
//		$("#update").attr('id', 'insert').text('Insert');
//
//		$("#popup").css('display', 'flex').hide().fadeIn();
//	});

	// When the "Edit" button is clicked (for editing an existing row)
	$("#user-modi").click(function() {
		console.log("Edit - click");
		$('#head-title').text('사용자 수정');

		// Assuming you have logic to pre-populate the fields with selected row data:
		const selectedRow = $(".selected-row");
		if (selectedRow.length > 0) {
			// Populate modal with selected row data
//			const selectedName = selectedRow.find("td").eq(0).text();
//			const selectedInstitutionCode = selectedRow.find("td").eq(1).text();
//			const selectedInstitutionName = selectedRow.find("td").eq(2).text();
//			const selectedNote = selectedRow.find("td").eq(3).text();
//			const selectedStatus = selectedRow.find("td").eq(4).text();
//			const selectedGrade = selectedRow.find("td").eq(5).text();
//			console.log(selectedName);
//			console.log(selectedInstitutionCode);
//			console.log(selectedInstitutionName);
//			console.log(selectedNote);
//			console.log(selectedStatus);
//			console.log(selectedGrade);
			$("#us_col_02").val(selectedName);
			$("#us_col_05").val(selectedInstitutionName).attr("disabled", true).prop("selected", true);	// 기관
			$("#us_col_08").val(selectedGrade).prop("selected", true);	// 권한
			$("#us_col_07").val(selectedStatus).prop("selected", true);		// 사용상태
			$("#us_col_06").val(selectedNote);	// 비고
			// Change the save button's ID to 'update' and the text to 'Update'
			$("#save").attr('id', 'update').text('Update');
			$("#insert").attr('id', 'update').text('Update');

			// Show the modal
			$("#popup").css('display', 'flex').hide().fadeIn();
		} else {
			alert("Please select a row to edit.");
		}
	});

	$("#user-del").click(function() {
		// Get the hidden ID of the selected row
		const selectedRow = $(".selected-row");
		if (selectedRow.length > 0) {
	        const hiddenId = selectedRow.find("input[name='id']").val();
	        const instCode = selectedRow.find("input[name='inst']").val();
	        const userid = selectedRow.find("input[name='userid']").val();
			console.log("instCode : " +instCode);
			if (confirm("사용자를 삭제하시겠습니까?")) {
				userDelete(hiddenId, instCode, userid);
			}
		} else {
			alert("Please select a row to delete.");
		}
	});
	$("#close").click(function() {
		modalClose(); //모달 닫기 함수 호출
	});
	$("#save").click(function() {
		modalClose(); //모달 닫기 함수 호출
	});
	function modalClose() {
		$("#popup").fadeOut(); //페이드아웃 효과
	}

	$(document).on("click", "#insert", function() {
		console.log("Insert button clicked");
		userInsert(); // Call the insert function
	});

	// When the "Update" button is clicked, call the update function
	$(document).on("click", "#update", function() {
		console.log("Update button clicked");
		userUpdate(); // Call the update function
	});

	function userInsert() {
		console.log($("#us_col_02").val());
		console.log($("#us_col_03").val());
		console.log($("#us_col_08").val());
		console.log($("#us_col_07").val());
		console.log($("#us_col_05").val());
		console.log($("#us_col_06").val());

		let selectedOption = $("#us_col_05 option:selected");
		let col03 = selectedOption.data('col-03');

		$.ajax({
			url: '/csm/core/newuser/post',
			type: 'post',
			dataType: 'json',
			data: {
				'us_col_02': $("#us_col_02").val(),
				'us_col_03': $("#us_col_03").val(),
				'us_col_04': col03,
				'us_col_08': $("#us_col_08").val(),
				'us_col_07': $("#us_col_07").val(),
				'us_col_05': $("#us_col_05").val(),
				'us_col_06': $("#us_col_06").val(),
			},
			success: function(response) {
				console.log('Success:', response);
				window.location.reload();
			},
			error: function(error) {
				console.log('Error:', error);

			}

		});


	}

	function userUpdate() {

		$.ajax({
			url: '/csm/core/user/update',
			type: 'post',
			data: {
				'us_col_02': $("#us_col_02").val(),
				'us_col_03': $("#us_col_03").val(),
				'us_col_08': $("#us_col_08").val(),
				'us_col_07': $("#us_col_07").val(),
				'us_col_06': $("#us_col_06").val(),
				'us_col_09': $("#us_col_09").val(),
				'us_col_10': $("#us_col_10").val(),
				'us_col_01': hiddenId
			},
			success: function(response) {
				console.log('Update Success: ', response);
				window.location.reload();
			},
			error: function(error) {
				console.log('Update Error: ', error);

			}
		});

	}

	function userDelete(hiddenId, instCode, userid) {

		$.ajax({
			url: '/csm/core/user/delete',
			type: 'post',
			dataType: 'json',
			data: {
				'id': hiddenId,
				'us_col_02': instCode,
				'us_col_01': userid 
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


	let hiddenId = "";
	let instCode = "";
	// 테이블 행 선택 및 색상 변경
	const rows = document.querySelectorAll('.grid-body3 .grid-row3');

	let selectedName = "";
	let selectedInstitutionCode = "";
	let selectedInstitutionName = "";
	let selectedNote = "";
	let selectedStatus = "";
	let selectedGrade = "";
	
	rows.forEach(function(row) {
		row.addEventListener('click', function() {
			// 모든 행에서 선택된 상태 제거
			rows.forEach(function(r) {
				r.classList.remove('selected-row');
			});

			// 클릭된 행에 선택된 상태 추가

			this.classList.add('selected-row');
			// 선택된 행의 데이터를 가져오는 로직
			const cells = this.children;
			selectedName = cells[0].textContent.trim();
			selectedInstitutionCode = cells[1].textContent.trim();
			selectedInstitutionName = cells[2].textContent.trim();
			selectedNote = cells[3].textContent.trim();
			selectedStatus = cells[4].textContent.trim();
	        hiddenId = this.querySelector('input[name="id"]').value;
	        instCode = this.querySelector('input[name="inst"]').value;

			console.log("selected Name:", selectedName);
			console.log("Selected Institution Code:", selectedInstitutionCode);
			console.log("Selected Institution Name:", selectedInstitutionName);
			console.log("Selected Note:", selectedNote);
			console.log("Selected Status:", selectedStatus);
			console.log("Selected Grade:", selectedGrade);
			console.log("Hidden ID (us_col_01):", hiddenId);



		});
	});

});
// 사용자 등록 팝업 오픈
function openUserPopup() {
    var width = 500;
    var height = 750;
    var left = (window.screen.width / 2) - (width / 2);
    var top = (window.screen.height / 2) - (height / 2);
    
    window.open("/csm/core/newuserPopup", "popupWindow", 
                "width=" + width + ",height=" + height + ",scrollbars=yes,left=" + left + ",top=" + top);
}
