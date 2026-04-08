document.addEventListener("DOMContentLoaded", function() {
	
	let selectedCategoryName = '';
	let selectedInputTypes = '';
	let categoryTypePost = '';
	let selectedCategory = '';
	let selectedCategoryId = '';
	let categoryId = "";
    var options = [];
    let subcategoryList = []; // Array to hold multiple subcategories
	
	document.querySelectorAll('.selectable-cell').forEach(cell => {
	    cell.addEventListener('click', function() {
	        const categoryType = this.dataset.categoryType;
	        categoryId = this.dataset.categoryId;
	        const parentId = this.dataset.parentId; // 소분류에만 존재
	        const inputTypes = this.dataset.inputType;
	        selectedCategoryId = categoryId;
	
			// 선택된 카테고리 이름 설정
	        selectedCategoryName = this.textContent.trim();
	        
	        // 선택된 입력 타입 설정
	        selectedInputTypes = inputTypes;
	        
	        if (categoryType === 'parent') {
	            console.log('대분류 선택:', categoryId);
	            // 대분류 처리 로직
	            categoryTypePost = categoryType;
	        } else if (categoryType === 'child') {
	            console.log('소분류 선택:', categoryId, '부모:', parentId, '입력 타입: ', inputTypes);
	            // 소분류 처리 로직
	            categoryTypePost = categoryType;
	            handleSubcategorySelection(categoryId, inputTypes);
	        }
	
	        // 선택된 셀 스타일 변경
	        document.querySelectorAll('.selectable-cell').forEach(c => c.classList.remove('selected-cell'));
	        this.classList.add('selected-cell');
	        console.log("카테고리 타입: "+categoryTypePost)
	    });
	});
	
	
	
	$(document).on('click', '[data-action]', function(e) {
	    e.preventDefault();
	    var action = $(this).data('action');
	    
	    switch(action) {
	        case 'delete-category':
	            deleteCategoryHandler();
	            break;
	        case 'modify-category':
				modifyCategoryHandler();
				break;
	        case 'add-category':
	            addCategoryHandler();
	            break;
	        case 'add-subcategory':
	            addSubcategoryHandler();
	            break;
	        case 'confirm':
	            confirmHandler();
	            break;
	    }
	});
	
	// 대분류 추가 API
//	$('#new-category').click(function() {
//		
//		$.ajax({
//			url: 'csm/setting/category1',
//			type: 'post',
//			dataType: 'json',
//			data: {
//				'cc_col_02': $('#cc_col_02').val(),
//			},
//			success: function(response) {
//				console.log('Success:', response);
////				window.location.reload();
//
//			},
//			error: function(error) {
//				console.log('Error:', error);
//
//			}
//		});
//	});
	function addCategoryHandler() {
		
	}
	$(document).on('click', '[data-action="confirm-parent"]', function() {
        $.ajax({
            url: 'csm/setting/category1',
            type: 'post',
            dataType: 'json',
            data: {
                'cc_col_02': $('#cc_col_02').val(),
            },
            success: function(response) {
                console.log('Success:', response);
            },
            error: function(error) {
                console.log('Error:', error);
            }
        });
    });
    function modifyCategoryHandler() {
		console.log("modify - click");
        $("#popup").css('display', 'flex').hide().fadeIn();
        
		if (categoryTypePost == 'parent'){
			$('.head-title').text('대분류 수정');
			$('.category1').removeClass('off');
	        $('.category2').addClass('off');
	        $('.message').addClass('off');
			
			// 대분류 수정 UI
	        let formHtml = `
	            <div class="category1">
	                <span class="content1">대분류명 :</span>
	                <input type="text" id="cc_col_02" name="cc_col_02" value="${selectedCategoryName}">
	            </div>
	        `;
	        $('.body-contentbox').html(formHtml);
	        
	        // 저장 버튼 변경
	        $('.confirm').attr('data-action', 'confirm-modify-parent').text('Modify');
		} else {
			$('.head-title').text('소분류 수정');
	        $('.category1').addClass('off');
	        $('.category2').removeClass('off');
	        $('.message').addClass('off');
	        
	        // 소분류 수정 UI
	        let formHtml = `
	            <span class="content1 category1name">${selectedCategoryName}</span><br>
	            <input type="text" id="subcategoryName" value="${selectedCategoryName}" placeholder="소분류 이름 입력"><br>
	        `;
	        
	        // 입력 타입에 따른 체크박스 생성
	        const inputTypes = selectedInputTypes.split('');
	        inputTypes.forEach(type => {
	            switch(type) {
	                case 'checkbox':
	                    formHtml += '<input type="checkbox" id="subcategory-checkbox" checked> 체크박스<br>';
	                    break;
	                case 'radio':
	                    formHtml += '<input type="checkbox" id="subcategory-radio" checked> 라디오 버튼<br>';
	                    break;
	                case 'text':
	                    formHtml += '<input type="checkbox" id="subcategory-text" checked> 텍스트 입력<br>';
	                    break;
	                case 'select':
	                    formHtml += '<input type="checkbox" id="subcategory-select" checked> 셀렉트 박스<br>';
	                    formHtml += '<textarea id="subcategory-options" placeholder="옵션을 쉼표로 구분하여 입력"></textarea><br>';
	                    break;
	            }
	        });
	
	        $('.body-contentbox').html(formHtml);
	        
	        // 저장 버튼 변경
	        $('.confirm').attr('data-action', 'confirm-modify-sub').text('Modify');
		}
	}
	// 대분류 수정 API
	$(document).on('click', '[data-action="confirm-modify-parent"]', function() {
	    const updatedData = {
	        cc_col_01: selectedCategoryId,
	        cc_col_02: $('#cc_col_02').val(),
	        type: categoryTypePost
	    };
	
		if (spinner) {
			spinner.classList.add('show'); // Adds CSS transition
			spinner.style.display = 'flex'; // Ensure it's visible
		}
	    $.ajax({
	        url: 'csm/setting/categoryModify',
	        type: 'POST',
	        data: JSON.stringify(updatedData),
	        contentType: 'application/json',
	        success: function(response) {
	            console.log('대분류 업데이트 성공:', response);
	            $("#popup").fadeOut();
	            // 필요한 경우 페이지 새로고침 또는 UI 업데이트
	            window.location.reload();
	        },
	        error: function(error) {
	            console.error('대분류 업데이트 실패:', error);
	        }
	    });
	});
	
	// 소분류 수정 API
	$(document).on('click', '[data-action="confirm-modify-sub"]', function() {
	    const updatedData = {
	        cc_col_01: categoryId,
	        cc_col_02: $('#subcategoryName').val(),
//	        cc_col_04: $('#subcategory-checkbox').is(':checked'),
//	        cc_col_05: $('#subcategory-radio').is(':checked'),
//	        cc_col_06: $('#subcategory-text').is(':checked'),
//	        cc_col_07: $('#subcategory-select').is(':checked'),
//	        options: $('#subcategory-options').val(),
	        type: categoryTypePost
	    };
		if (spinner) {
			spinner.classList.add('show'); // Adds CSS transition
			spinner.style.display = 'flex'; // Ensure it's visible
		}
	    $.ajax({
	        url: 'csm/setting/categoryModify',
	        type: 'POST',
	        data: JSON.stringify(updatedData),
	        contentType: 'application/json',
	        success: function(response) {
	            console.log('소분류 업데이트 성공:', response);
	            $("#popup").fadeOut();
	            // 필요한 경우 페이지 새로고침 또는 UI 업데이트
	            window.location.reload();
	        },
	        error: function(error) {
	            console.error('소분류 업데이트 실패:', error);
	        }
	    });
	});
	
	// 대분류 삭제 API
	function deleteCategoryHandler() {
        console.log("del - click");
        $("#popup").css('display', 'flex').hide().fadeIn();
        $('.head-title').text('대분류 삭제');
        $('.category2, .category1').addClass('off');
        $('.message').removeClass('off');
        $('.messagespan').text('대분류를 삭제하시겠습니까?');
        $(".confirm").attr('data-action', 'confirm-delete').text('DELETE');
    }
    $(document).on('click', '[data-action="confirm-delete"]', function() {
		const spinner = document.getElementById('spinner-overlay');
		if (spinner) {
			spinner.classList.add('show'); // Adds CSS transition
			spinner.style.display = 'flex'; // Ensure it's visible
		}
		let Type = categoryTypePost;
		console.log("카테고리 타입: ",Type, "카테고리 ID: ", categoryId);
        $.ajax({
            url: 'csm/setting/categoryDelete',
            type: 'POST',
			contentType: "application/json",
            data: JSON.stringify({
                'id': parseInt(categoryId),
                'type': categoryTypePost,
            }),
            success: function(response) {
                console.log('Success:', response);
                $("#popup").fadeOut();
                window.location.reload();
            },
            error: function(error) {
                console.log('Error:', error);
            }
        });
    });
//	$('#categorydel').click(function() {
//		
//		$.ajax({
//			url: 'csm/setting/category1/delete',
//			type: 'post',
//			dataType: 'json',
//			data: {
//				'cc_col_01' : hiddenId,
//			},
//			success: function(response) {
//				console.log('Success:', response);
////				window.location.reload();
//
//			},
//			error: function(error) {
//				console.log('Error:', error);
//
//			}
//		});
//	});
	
	// 라디오박스와 체크박스가 동시에 선택되지 않도록 하기 위한 로직
	$('input[name="inputType"]').change(function() {
		if (this.id === 'radio') {
			$('#checkbox').prop('checked', false);
		} else if (this.id === 'checkbox') {
			$('#radio').prop('checked', false);
		}

		// 소분류 이름 입력란 표시
		$('#subcategoryContainer').show();

		// 셀렉트박스를 선택한 경우 옵션 입력란 표시
		if ($('#selectbox').prop('checked')) {
			$('#optionsContainer').show();
		} else {
			$('#optionsContainer').hide();
			options = [];
			$('#optionsList').empty();
		}
	});

    // 옵션 추가 (엔터키를 눌렀을 때)
	$('#optionInput').on('keypress', function(e) {
		// Enter 키를 감지
		if (e.key === 'Enter' || e.keyCode === 13) {
			e.preventDefault();  // 기본 엔터키 동작(폼 제출)을 방지
			let option = $('#optionInput').val().trim();  // 현재 입력 값 가져오기
			if (option) {
				options.push(option);  // 옵션 배열에 추가
				$('#optionsList').append('<li>' + option + '</li>');  // 리스트에 추가
				$('#optionInput').val('');  // 입력란 초기화
			}
			$('#optionInput').focus();  // 커서를 다시 입력란으로 이동
		}
	});
	// 기존 버튼으로 옵션 추가 기능도 유지
	$('#addOption').click(function() {
		let option = $('#optionInput').val().trim();
		if (option) {
			options.push(option);
			$('#optionsList').append('<li>' + option + '</li>');
			$('#optionInput').val('');  // 입력란 초기화
	
			// 입력란에 다시 커서를 위치시킴
			$('#optionInput').focus();
		}
	});
    
    $('input[name="inputType"]').change(function() {
	    if ($(this).attr('id') === 'selectbox' && $(this).prop('checked')) {
	        $('#optionsContainer').show();
	    } else {
	        $('#optionsContainer').hide();
	        // 셀렉트박스 선택 해제 시 옵션 초기화
	        options = [];
	        $('#optionsList').empty();
	    }
	});
    
    
    
	// 소분류 추가 API
//	$('#new-category2').click(function() {
////		console.log('qwe');
//		const selectedRow = $(".selected-cell");
//        if (selectedRow.length > 0) {
//            hiddenId = selectedRow.find("input[type='hidden']").val();
//            console.log("대분류 id: "+hiddenId);
//			$('.category2').removeClass('off');
//			$('.category1').addClass('off');
//			$('.category1name').text(selectedCategory);
//			$('.head-title').text('소분류 추가');
//			
//			 // 기존 'new-category' 이벤트 핸들러 제거
//        	$('#new-category').off('click');
//        	
//        	// ID를 변경하고 새로운 버튼 'SAVE'로 동작하도록
//			$("#new-category").attr('id', 'subcategory').text('SAVE');
//			
//			
//			
//			$("#popup").css('display', 'flex').hide().fadeIn();
//        } else {
//            alert("대분류 항목을 선택해 주세요.");
//        }
//	});
	function addSubcategoryHandler() {
        const selectedRow = $(".selected-cell");
        if (selectedRow.length > 0) {
            hiddenId = selectedRow.find("input[type='hidden']").val();
            console.log("대분류 id: " + hiddenId);
            $('.category2').removeClass('off');
            $('.category1').addClass('off');
            $('.category1name').text(selectedCategory);
            $('.head-title').text('소분류 추가');
            
            $("#popup").css('display', 'flex').hide().fadeIn();
            $(".confirm").attr('data-action', 'confirm-add-subcategory').text('SAVE');
        } else {
            alert("대분류 항목을 선택해 주세요.");
        }
    }
	// 선택된 값 가져오기
	// 소분류 생성
	$('#category2create').click(function() {
		var selectedValues = [];
    	
	    // 라디오 버튼 값 가져오기
	    var radioValue = $('input[name="radio"]:checked').next('label').text();
	    if (radioValue) {
	        selectedValues.push(radioValue);
	    }
	    
	    // 체크박스 값 가져오기
	    $('input[type="checkbox"]:checked').each(function() {
	        selectedValues.push($(this).next('label').text());
	    });
	    
	    // 선택된 값 출력 (테스트용)
	    console.log("선택된 값들:", selectedValues);
	    
	    // 만약 셀렉트박스가 선택이 되면 옵션을 설정하는 칸이 있어야함.
	    
        var subcategoryName = $('#subcategoryName').val().trim();
        
        // subcategoryData를 전역으로 정의하여 다른 핸들러에서 접근 가능하게 변경
		window.subcategoryData = {
			name: subcategoryName,
			hiddenid: hiddenId,
			checkbox: false,    // 체크박스 여부
			radio: false,       // 라디오박스 여부
			textbox: false,     // 텍스트박스 여부
			selectbox: false,   // 셀렉트박스 여부
			options: []         // 셀렉트박스 옵션 저장
		};
		let selectedElements = [];
		let newElement;
        // 라디오박스 선택 시
		if ($('#radio').prop('checked')) {
			newElement = $('<div class="input-group mb-3">' +
						   '<input type="radio" name="' + subcategoryName + '">' +
						   '<label>' + subcategoryName + '</label>' +
						   '</div>');
			selectedElements.push(newElement);
			subcategoryData.radio = true;
		}
		
		// 체크박스 선택 시
		if ($('#checkbox').prop('checked')) {
			newElement = $('<div class="input-group mb-3">' +
						   '<input type="checkbox" name="' + subcategoryName + '">' +
						   '<label>' + subcategoryName + '</label>' +
						   '</div>');
			selectedElements.push(newElement);
			subcategoryData.checkbox = true;
		}
		
		// 텍스트박스 선택 시
		if ($('#textbox').prop('checked')) {
			newElement = $('<div class="input-group mb-3">' +
						   '<input type="text" placeholder="' + subcategoryName + '">' +
						   '</div>');
			selectedElements.push(newElement);
			subcategoryData.textbox = true;
		}
		
		// 셀렉트박스 선택 시
		if ($('#selectbox').prop('checked')) {
			let select = $('<select class="form-control"></select>');
			options.forEach(function(option) {
				select.append($('<option>', {
					value: option,
					text: option
				}));
			});
			newElement = $('<div class="input-group mb-3"></div>').append(select);
			selectedElements.push(newElement);
			subcategoryData.selectbox = true;
			$('#optionsList li').each(function() {
				subcategoryData.options.push($(this).text());
			});
			options = [];  // 옵션 초기화
			$('#optionsList').empty();  // 옵션 리스트 초기화
		}

		// 선택된 엘리먼트를 추가
		selectedElements.forEach(function(el) {
			$('#newElementsContainer').append(el);
		});
		// 데이터를 확인하기 위한 로그
		console.log("subcategoryData:", subcategoryData);
		// 입력 필드 초기화
		$('#subcategoryName').val('');
		$('#optionsContainer').hide();
    });
	// 이벤트 위임으로 동적으로 변경된 #subcategory 버튼에 이벤트 핸들러 등록
//	$(document).on('click', '#subcategory', function() {
//		console.log("aaa");
//		$.ajax({
//			url: '/csm/setting/category2',
//			type: 'post',
//			contentType: 'application/json',
//			data: JSON.stringify(subcategoryData),  // 전송할 데이터
//			success: function(response) {
//				console.log('Success: ', response);
//				window.location.reload();
//			},
//			error: function(error) {
//				console.log('Error: ', error);
//			}
//		});
//		// 입력 필드 초기화
//		$('#subcategoryName').val('');
//		$('#optionsContainer').hide();
//		$('#optionsList').empty();
//		options = [];
//	});
    $(document).on('click', '[data-action="confirm-add-subcategory"]', function() {
	    // 소분류 추가 로직 구현
	    console.log("소분류 추가 확인");
	    console.log("aaa");
		$.ajax({
			url: '/csm/setting/category2',
			type: 'post',
			contentType: 'application/json',
			data: JSON.stringify(subcategoryData),  // 전송할 데이터
			success: function(response) {
				console.log('Success: ', response);
//				window.location.reload();
			},
			error: function(error) {
				console.log('Error: ', error);
			}
		});
		// 입력 필드 초기화
		$('#subcategoryName').val('');
		$('#optionsContainer').hide();
		$('#optionsList').empty();
		options = [];
	    $("#popup").fadeOut();
	});
	// 삭제 버튼 동작
    $(document).on('click', '.remove-element', function() {
		$(this).closest('.input-group').remove();
	});
	let hiddenId = "";
	// 테이블 행 선택 및 색상 변경
//	const selectableCells = document.querySelectorAll('.selectable-cell');
//
//    selectableCells.forEach(function(cell) {
//        cell.addEventListener('click', function() {
//            // 모든 셀에서 선택 상태 제거
//            selectableCells.forEach(function(c) {
//                c.classList.remove('selected-cell');
//            });
//
//            // 클릭된 셀에 선택 상태 추가
//            this.classList.add('selected-cell');
//
//            // 선택된 셀의 데이터 처리
//            const hiddenInput = this.querySelector('input[type="hidden"]');
//            if (hiddenInput) {
//                hiddenId = hiddenInput.value;
//                const cellText = this.textContent.trim();
//                console.log("Selected Category:", cellText);
//                console.log("Hidden ID:", hiddenId);
//            }
//        });
//    });
	function handleSubcategorySelection(categoryId, inputTypes) {
	    console.log(`선택된 소분류 ID: ${categoryId}, 입력 타입: ${inputTypes}`);
	
	    // 입력 타입 문자열을 배열로 변환
	    const types = inputTypes.match(/checkbox|radio|text|select/g) || [];
	    
		// UI 요소를 저장할 배열
	    let uiElements = [];
	    
	    // 각 입력 타입에 대한 처리
	    types.forEach(type => {
	        switch(type) {
	            case 'checkbox':
	                console.log('체크박스 생성');
	                uiElements.push(createCheckbox(categoryId));
	                // 체크박스 생성 로직
	                break;
	            case 'radio':
	                console.log('라디오 버튼 생성');
	                uiElements.push(createRadioButton(categoryId));
	                // 라디오 버튼 생성 로직
	                break;
	            case 'text':
	                console.log('텍스트 입력 필드 생성');
	                uiElements.push(createTextInput(categoryId));
	                // 텍스트 입력 필드 생성 로직
	                break;
	            case 'select':
	                console.log('셀렉트 박스 생성');
	                uiElements.push(createSelectBox(categoryId));
	                // 셀렉트 박스 생성 로직
	                break;
	        }
	    });
		// 생성된 UI 요소들을 DOM에 추가
	    const container = document.getElementById('message');
	    container.innerHTML = ''; // 기존 내용 초기화
	    uiElements.forEach(element => container.appendChild(element));
	}
	
	// UI 요소 생성 함수들
	function createCheckbox(categoryId) {
	    const checkbox = document.createElement('input');
	    checkbox.type = 'checkbox';
	    checkbox.id = `checkbox-${categoryId}`;
	    checkbox.name = `category-${categoryId}`;
	    
	    const label = document.createElement('label');
	    label.htmlFor = checkbox.id;
	    label.textContent = '체크박스';
	
	    const wrapper = document.createElement('div');
	    wrapper.appendChild(checkbox);
	    wrapper.appendChild(label);
	    return wrapper;
	}
	function createRadioButton(categoryId) {
	    const radio = document.createElement('input');
	    radio.type = 'radio';
	    radio.id = `radio-${categoryId}`;
	    radio.name = `category-${categoryId}`;
	    
	    const label = document.createElement('label');
	    label.htmlFor = radio.id;
	    label.textContent = '라디오 버튼';
	
	    const wrapper = document.createElement('div');
	    wrapper.appendChild(radio);
	    wrapper.appendChild(label);
	    return wrapper;
	}
	
	function createTextInput(categoryId) {
	    const input = document.createElement('input');
	    input.type = 'text';
	    input.id = `text-${categoryId}`;
	    input.name = `category-${categoryId}`;
	    input.placeholder = '텍스트 입력';
	    return input;
	}
	
	function createSelectBox(categoryId) {
	    const select = document.createElement('select');
	    select.id = `select-${categoryId}`;
	    select.name = `category-${categoryId}`;
	    
	    const defaultOption = document.createElement('option');
	    defaultOption.value = '';
	    defaultOption.textContent = '선택하세요';
	    select.appendChild(defaultOption);
	
	    // 여기에 실제 옵션들을 추가할 수 있습니다.
	
	    return select;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
});