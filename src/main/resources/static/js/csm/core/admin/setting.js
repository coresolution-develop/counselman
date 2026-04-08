document.addEventListener("DOMContentLoaded", function () {
	
    let selectedMainCategoryId = null; // Stores the currently selected main category ID
    let selectedSubcategoryId = null; // Stores the currently selected subcategory ID

	let Close = document.getElementById('close');
    let Open = document.getElementById('user-new');
    let Open2 = document.getElementsByClassName('templateFirstInsert');
    let modi = document.getElementById('user-modi');
    let Del = document.getElementById('user-del');
    let TemplateName = '';
    let TemplateNote = '';
    let selectedIdx;
	const tableRows = document.querySelectorAll('.grid-body4 .grid-row4');
	
	tableRows.forEach(row => {
	    row.addEventListener('click', function() {
	        // 기존에 선택된 행의 스타일을 제거
	        tableRows.forEach(r => r.classList.remove('selected-row'));
	
	        // 클릭된 행에 'selected-row' 클래스 추가
	        this.classList.add('selected-row');
	
	        // data-cs-idx 속성에서 cs_idx 값 가져오기
	        selectedIdx = this.getAttribute('data-template-idx');
            console.log('Selected template_idx:', selectedIdx);
            const cells = this.children;
            const selectedTemplateName = cells[0].textContent.trim();
            const selectedTemplateNote = cells[0].textContent.trim();
            const selectedTemplateStatus = cells[0].textContent.trim();
            TemplateName = selectedTemplateName;
            TemplateNote = selectedTemplateNote;
//            console.log(selectedTemplateName);
	
	        // 추가적인 동작이 필요한 경우 여기에 작성
	        // 예: 선택된 cs_idx를 다른 곳에 표시하거나 사용
	    });
	    row.addEventListener('dblclick', function() {
			console.log("더블클릭");
        });
	    
	});
    
	Close.addEventListener('click', function() {
		console.log("qwe");
		
		$("#popup").fadeOut();
	});

	if (Open) {
        Open.addEventListener('click', function () {
            console.log("Template creation button clicked");
	        const modal3 = $('.modal3');
	        const modalText = $('.menu_msg3')[0]; // Access the first DOM element from the jQuery object

        	modalText.innerText = "새로운 템플릿을 추가합니다.";
	        modal3.addClass('show');
	        $('body').css('overflow', 'hidden');
    		$('#templateName').focus();
        });
    } else {
        console.warn("Open button not found in the DOM");
    }

    if (Open2.length > 0) {
        Array.from(Open2).forEach(button => {
            button.addEventListener('click', function () {
                console.log("Template creation button clicked");
		        const modal3 = $('.modal3');
		        const modalText = $('.menu_msg3')[0]; // Access the first DOM element from the jQuery object

            	modalText.innerText = "새로운 템플릿을 추가합니다.";
		        modal3.addClass('show');
		        $('body').css('overflow', 'hidden');
    			$('#templateName').focus();
            });
        });
    } else {
        console.warn("Template creation buttons not found in the DOM");
    }
    if (modi) {
		modi.addEventListener('click', function() {
			console.log('템플릿 수정');
			const modal3 = $('.modal5');
			const modalText = $('.menu_msg5')[0];
			if(selectedIdx) {
				modalText.innerText = '템플릿을 수정합니다.';
				modal3.addClass('show');
				$('.body').css('overflow', 'hidden');
				$('#templateNameModi').val(TemplateName);
				$('#templateDescriptionModi').val(TemplateNote);
				$('#templateNameModi').focus();
			} else {
				alert('템플릿을 먼저 선택해주세요.');
			}
			
		});
	}
    
	$('#TemplateConfirmBtn').click(function() {
		TemplateInsert();
	});
	function TemplateInsert() {
		console.log($('#templateName').val());
		console.log($('#templateDescription').val());
		const templateName = $('#templateName').val();
		const templateDescription = $('#templateDescription').val();
		const templatedata ={
			name: templateName,
			description: templateDescription
		}
		spinner();
		$.ajax({
			url: '/csm/core/setting/templateInsert',
			type: 'POST',
			data: JSON.stringify(templatedata),
			contentType: 'application/json',
			success: function(response) {
				console.log("템플릿 업로드 성공", response);
				closePopup3();
				window.location.reload();
			},
			error: function(error) {
				console.error('템플릿 업로드 실패', error);
			}
		});
	}
	
	$('#TemplateModifyBtn').click(function() {
		TemplateModify();
	});
	function TemplateModify() {
		console.log($('#templateNameModi').val());
		console.log($('#templateDescriptionModi').val());
		console.log(selectedIdx);
		const templateName = $('#templateNameModi').val();
		const templateDescription = $('#templateDescriptionModi').val();
		
		const tempalteData = {
			name : templateName,
			description: templateDescription,
			idx : selectedIdx
		}
		spinner();
		$.ajax({
			url: '/csm/core/setting/templateModify',
			type: 'post',
			data: JSON.stringify(tempalteData),
			contentType: 'application/json',
			success: function(response) {
				console.log('템플릿 수정 성공', response);
				closePopup5();
				window.location.reload();
			},
			error: function(error) {
				console.error('템플릿 수정 실패', error);
			}
		});
	}
	Del.addEventListener('click', function() {
		
		console.log("템플릿 삭제");
		if(selectedIdx) {
			if (confirm("선택하신 템플릿을 삭제하시겠습니까?")) {
				TemplateDelete(selectedIdx);
			}
		} else {
			alert('템플릿을 선택해주세요.');
		}
	});
	function TemplateDelete(selectedIdx) {
		const templateData = {
			idx : selectedIdx
		}
		console.log(templateData);
		spinner();
		$.ajax({
			url: '/csm/core/setting/templateDelete',
			type: 'post',
			data: JSON.stringify(templateData),
			contentType: 'application/json',
			success: function(response) {
				console.log('템플릿 삭제 성공', response);
				window.location.reload();
			},
			error: function(error) {
				console.error('템플릿 수정 실패', error);
			}
		});
	}
	
	$('#new-maincategory').click(function () {
        openAddCategoryModal('parent');
    });
    
    // Add subcategory logic
    $('#new-subcategory').click(function () {
        if (!selectedMainCategoryId) {
            alert("Please select a main category before adding a subcategory.");
            return;
        }
        openAddCategoryModal('child');
    });

    // Set checked status for newly added main category
    function setMainCategoryChecked(mainCategoryId) {
        // Uncheck all main categories
        $('input[name="mainCategory"]').prop('checked', false);
        // Check the new main category
        $(`input[name="mainCategory"][value="${mainCategoryId}"]`).prop('checked', true);
        // Set selectedMainCategoryId to the new category
        selectedMainCategoryId = mainCategoryId;
        console.log(`Main category selected: ${mainCategoryId}`);
    }

    // Add new category
    function addCategory() {
        const categoryName = $('#cc_col_02').val().trim(); // Get the entered category name
        if (!categoryName) {
            alert("Please enter a category name.");
            return;
        }

        let url, data;
        if (currentCategoryType === 'parent') {
            // Adding a main category
            url = '/csm/core/setting/category1';
            data = { main_col_01: categoryName };
        } else if (currentCategoryType === 'child') {
            // Adding a subcategory
            if (!selectedMainCategoryId) {
//                alert("Please select a main category before adding a subcategory.");
                return;
            }

            const isCheckbox = $('#isCheckbox').is(':checked');
            const isRadio = $('#isRadio').is(':checked');
            const isTextbox = $('#isTextbox').is(':checked');
            const isSelectbox = $('#isSelectbox').is(':checked');
            const selectboxOptions = isSelectbox
                ? $('#selectboxOptions')
                      .val()
                      .split(',')
                      .map(option => option.trim())
                      .filter(option => option !== '')
                : [];

            url = '/csm/core/setting/category2';
            data = {
                cc_col_02: categoryName,
                hiddenid: selectedMainCategoryId,
                checkbox: isCheckbox,
                radio: isRadio,
                textbox: isTextbox,
                selectbox: isSelectbox,
                options: selectboxOptions,
            };
        }

        console.log("Data sent to server:", data);

        // AJAX request to add category
        $.ajax({
            url: url,
            type: 'POST',
            dataType: 'json',
            contentType: 'application/json',
            data: JSON.stringify(data),
            success: function (response) {
	            console.log("Main category added successfully:", response);
	            closePopup4(); // Close the modal
	            fetchAndRenderMainCategories(); // Fetch and update the main categories list
	        },
	        error: function (error) {
	            console.error("Error adding main category:", error);
	            alert("Failed to add main category. Please try again.");
	        },
        });
    }

    // Select a main category
    window.selectMainCategory = function (mainCategoryId) {
        selectedMainCategoryId = mainCategoryId;
        console.log(`Main category selected: ${mainCategoryId}`);
        $('#new-subcategory').prop('disabled', false); // Enable subcategory addition
    };
    
    // Open the add category modal
    function openAddCategoryModal(type) {
        currentCategoryType = type;
        const modal4 = $('.modal4');
        const checkboxContainer = $('#checkboxContainer');
        const selectOptions = $('#selectOptions');
        $('#cc_col_02').val(''); // Clear input

        if (type === 'parent') {
            checkboxContainer.hide();
            selectOptions.hide();
        } else if (type === 'child') {
            checkboxContainer.show();
            selectOptions.hide();
        }

        modal4.addClass('show');
        $('body').css('overflow', 'hidden');
    }
	// Attach event listener for adding category
    $('#addbtn').click(addCategory);
    
    function fetchAndRenderMainCategories() {
    $.ajax({
        url: '/csm/core/setting/maincategory', // API to fetch main categories
        type: 'GET',
        dataType: 'json',
        success: function (data) {
            console.log("Fetched main categories:", data);

            // Update the DOM with the fetched main categories
            const mainCategoryDiv = $('#mainCategoryDiv');
            mainCategoryDiv.empty(); // Clear existing categories

            data.forEach(category => {
                const categoryHTML = `
                    <label class="custom-radio">
                        <input type="radio" name="mainCategory" value="${category.id}" onclick="selectMainCategory('${category.id}')">
                        <span class="custom-radio-mark"></span>
                        ${category.cc_col_02}
                        
                    </label>`;
                mainCategoryDiv.append(categoryHTML);
            });
        },
        error: function (error) {
            console.error("Error fetching main categories:", error);
            alert("Failed to fetch main categories. Please try again.");
        },
    });
}
});
// spinner
function spinner() {
	const spinner = document.getElementById('spinner-overlay');
	if (spinner) {
		spinner.classList.add('show'); // Adds CSS transition
		spinner.style.display = 'flex'; // Ensure it's visible
	}
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
	var modal2 = document.querySelector('.moda2');
	var body = document.querySelector('body');
	modal2.classList.toggle('show');
	if (!modal2.classList.contdains('show')) {
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
    $('#templateName').val("");
    $('#templateDescription').val("");
}

function closePopup4() {
    var modal4 = document.querySelector('.modal4');
    var body = document.querySelector('body');
    modal4.classList.toggle('show');
    if (!modal4.classList.contains('show')) {
        body.style.overflow = 'auto';
    }
}
function closePopup5() {
    var modal5 = document.querySelector('.modal5');
    var body = document.querySelector('body');
    modal5.classList.toggle('show');
    if (!modal5.classList.contains('show')) {
        body.style.overflow = 'auto';
    }
}
