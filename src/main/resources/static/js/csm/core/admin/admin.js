document.addEventListener("DOMContentLoaded", function() {
	const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
	if (csrfToken && csrfHeader) {
		$.ajaxSetup({ headers: { [csrfHeader]: csrfToken } });
	}

	$("#user-new").click(function() {
		console.log("add - click");
//        $('#head-title').text('기관 추가');
//		
//		// Clear form inputs
//        $("#id_col_02").val('');
//        $("#id_col_03").val('');
//        $("#id_col_05").val('');
//
//        // Change the save button's ID to 'insert' and the text to 'Insert'
//        $("#save").attr('id', 'insert').text('Insert');
//        $("#update").attr('id', 'insert').text('Insert');
//        
//        // Show the modal
//        $("#popup").css('display', 'flex').hide().fadeIn();

	});
	
	// When the "Edit" button is clicked (for editing an existing row)
    $("#user-modi").click(function() {
        console.log("Edit - click");
//        $('#head-title').text('기관 수정');
//
//        // Assuming you have logic to pre-populate the fields with selected row data:
//        const selectedRow = $(".selected-row");
//        if (selectedRow.length > 0) {
//            // Populate modal with selected row data
//            const selectedInstitutionName = selectedRow.find("td").eq(1).text();
//            const selectedInstitutionCode = selectedRow.find("td").eq(0).text();
//            const selectedNote = selectedRow.find("td").eq(2).text();
//
//            $("#id_col_02").val(selectedInstitutionName);
//            $("#id_col_03").val(selectedInstitutionCode);
//            $("#id_col_05").val(selectedNote);
//
//            // Change the save button's ID to 'update' and the text to 'Update'
//            $("#save").attr('id', 'update').text('Update');
//            $("#insert").attr('id', 'update').text('Update');
//
//            // Show the modal
//            $("#popup").css('display', 'flex').hide().fadeIn();
//        } else {
//            alert("Please select a row to edit.");
//        }
    });

	$("#user-del").click(function() {
        // Get the hidden ID of the selected row
        const selectedRow = $(".selected-row");
        if (selectedRow.length > 0) {
            const hiddenId = selectedRow.find("input[type='hidden']").val();

            if (confirm("선택하신 기관을 삭제 하시겠습니까?")) {
                instDelete(hiddenId);
            }
        } else {
            alert("Please select a row to delete.");
        }
    });
	
	$("#save").click(function() {
		modalClose(); //모달 닫기 함수 호출

	});
	
	$("#close").click(function() {
		modalClose(); //모달 닫기 함수 호출
	});
	function modalClose() {
		$("#popup").fadeOut(); //페이드아웃 효과
	}
	$(document).on("click", "#insert", function() {
        console.log("Insert button clicked");
        instInsert(); // Call the insert function
    });
	
	// When the "Update" button is clicked, call the update function
    $(document).on("click", "#update", function() {
        console.log("Update button clicked");
        instUpdate(); // Call the update function
    });
    
    

	function instInsert() {
		console.log($("#id_col_02").val());
		console.log($("#id_col_03").val());
		console.log($("#id_col_05").val());

		$.ajax({
			url: '/csm/core/inst/post',
			type: 'post',
			dataType: 'json',
			data: {
				'id_col_02': $("#id_col_02").val(),
				'id_col_03': $("#id_col_03").val(),
				'id_col_05': $("#id_col_05").val()
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
	
	// Function to update existing data
    function instUpdate() {
		console.log($("#id_col_04 option:selected"));
		const selectedStatus = $("#id_col_04 option:selected").val();
        $.ajax({
            url: '/csm/core/inst/update', // Update URL
            type: 'post',
            dataType: 'json',
            data: {
                'id_col_02': $("#id_col_02").val(),
                'id_col_03': $("#id_col_03").val(),
                'id_col_05': $("#id_col_05").val(),
                'id_col_01': hiddenId,
                'id_col_04': selectedStatus
            },
            success: function(response) {
                console.log('Update Success:', response);
                window.location.reload(); // Reload the page
            },
            error: function(error) {
                console.log('Update Error:', error);
            }
        });
    }
	// Function to delete the selected institution
    function instDelete(hiddenId) {
        $.ajax({
            url: '/csm/core/inst/delete', // Delete URL
            type: 'post',
            dataType: 'json',
            data: {
                'id_col_01': hiddenId // Pass the hidden ID for deletion
            },
            success: function(response) {
                console.log('Delete Success:', response);
                // Optionally reload the page or update the UI
                window.location.reload(); // Reload the page after deletion
            },
            error: function(error) {
                console.log('Delete Error:', error);
            }
        });
    }
    
	let hiddenId = "";
	// 테이블 행 선택 및 색상 변경
	const rows = document.querySelectorAll('.grid-body .grid-row');

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
			const selectedInstitutionCode = cells[0].textContent.trim();
			const selectedInstitutionName = cells[1].textContent.trim();
			const selectedInstNumber = cells[2].textContent.trim();
			const selectedNumber1 = cells[3].textContent.trim();
			const selectedNumber2 = cells[4].textContent.trim();
			const selectedStatus = cells[5].textContent.trim();

			hiddenId = this.querySelector('input[type="hidden"]').value;
			console.log("Selected Institution Code:", selectedInstitutionCode);
			console.log("Selected Institution Name:", selectedInstitutionName);
			console.log("Selected InstNumber:", selectedInstNumber);
			console.log("Selected Number1:", selectedNumber1);
			console.log("Selected Number2:", selectedNumber2);
			console.log("Selected Status:", selectedStatus);
			console.log("Hidden ID (id_col_01):", hiddenId);
			isntcode = selectedInstitutionCode;
			// 모달을 열고 선택된 데이터를 입력 필드에 표시
//            $("#popup").css('display', 'flex').hide().fadeIn(); // 모달 열기
//            $("#id_col_02").val(selectedInstitutionName); // 기관명
//            $("#id_col_03").val(selectedInstitutionCode); // 기관코드
//            $("#id_col_05").val(selectedNote); // 비고


		});
	});





});

// 기관 등록 팝업 오픈
function openInstPopup() {
    var width = 500;
    var height = 850;
    var left = (window.screen.width / 2) - (width / 2);
    var top = (window.screen.height / 2) - (height / 2);
    
    window.open("/csm/core/newinstPopup", "popupWindow", 
                "width=" + width + ",height=" + height + ",scrollbars=yes,left=" + left + ",top=" + top);
}
let isntcode = '';
// 기관 수정 팝업 오픈
function openInstModifyPopup() {
    var width = 500;
    var height = 850;
    var left = (window.screen.width / 2) - (width / 2);
    var top = (window.screen.height / 2) - (height / 2);
	window.open("/csm/core/modifyinstPopup?code="+isntcode, "popupwindow",
				"width=" + width + ",height=" + height + ",scrollbars=yes,left=" + left + ",top=" + top);
}
