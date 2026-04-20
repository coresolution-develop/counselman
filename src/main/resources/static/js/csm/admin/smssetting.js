$(document).ready(function() {
	const appContext = (document.querySelector('meta[name="app-context"]')?.getAttribute('content') || '/').replace(/\/$/, '');
	const apiUrl = (path) => `${appContext}${path}`;
	const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || '';
	if (csrfToken && csrfHeader && typeof $ !== 'undefined' && $.ajaxSetup) {
		$.ajaxSetup({
			beforeSend: function(xhr) {
				xhr.setRequestHeader(csrfHeader, csrfToken);
			}
		});
	}

	$('.menu5', $('.nav_section')).addClass('active');

	const query = new URLSearchParams(window.location.search);
	let page = Number(query.get('page') || 1);
	let perPageNum = Number(query.get('perPageNum') || 10);
	let isLoading = false;
	let hasMoreData = true;
	let observer;
	let observerReady = false;
	
	const deleteButton = document.getElementById('templateDelete');
	const modifyButton = document.getElementById('templateModify');
	const modalElement = document.getElementById('smsTemplateModal');
	const titleInput = document.getElementById('sms_title');
	const templateInput = document.getElementById('sms_template');
	const templateLength = document.getElementById('sms_template_length');
	const saveButton = document.getElementById('templateSaveBtn');
	const cancelButton = document.getElementById('templateCancelBtn');
	const closeButton = document.getElementById('templateModalCloseBtn');
	let selectedidx = null;
	let selectedTitle = '';
	let selectedTemplate = '';
	let isSaving = false;

	function updateTemplateLength() {
		if (!templateInput || !templateLength) {
			return;
		}
		templateLength.textContent = templateInput.value.length;
	}

	function setSaveButtonState(disabled) {
		isSaving = disabled;
		if (!saveButton) {
			return;
		}
		saveButton.style.pointerEvents = disabled ? 'none' : 'auto';
		saveButton.style.opacity = disabled ? '0.6' : '1';
	}

	function validateTemplateForm(title, text) {
		if (!title) {
			alert('상용구명을 입력하세요.');
			titleInput?.focus();
			return false;
		}
		if (!text) {
			alert('상용구 내용을 입력하세요.');
			templateInput?.focus();
			return false;
		}
		return true;
	}
	function setupRowClickEvents() {
        const tableRows = document.querySelectorAll('.template-list .template-row');
        tableRows.forEach(row => {
            if (row.dataset.clickBound === '1') return;
            row.dataset.clickBound = '1';
            row.addEventListener('click', function() {
                // 기존 선택된 행 스타일 제거
                tableRows.forEach(r => r.classList.remove('selected-row'));

                this.classList.add('selected-row');

                selectedidx = this.getAttribute('data-id');
                console.log('Selected id:', selectedidx);

                // 선택한 행에서 "title"과 "template" 가져오기
                const cells = this.getElementsByTagName('div');
                if (cells.length >= 3) {
                    selectedTitle = cells[1].textContent.trim();
                    selectedTemplate = cells[2].textContent.trim();
                }
                console.log(selectedTitle);
                console.log(selectedTemplate);
            });

            row.addEventListener('dblclick', function() {
                selectedidx = this.getAttribute('data-id');
            });
        });
    }
    
	modifyButton.addEventListener('click', function() {
        if (selectedidx) {
            openModal("modify", selectedTitle, selectedTemplate);
        } else {
			alert('수정할 상용구를 선택하세요.');
        }
    });
	deleteButton.addEventListener('click', function() {
		if (selectedidx) {
			if (confirm('삭제 시 복구가 불가능 합니다. 삭제하시겠습니까?')) {
				deletetemplate();
			}
		} else {
            alert('삭제할 상용구를 선택하세요.');
        }
		
	});
	
	function deletetemplate() {
	    // Create the AJAX request
	    spinner();
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;
	    $.ajax({
			url: apiUrl('/smsDelete'),
			type: 'post',
			dataType: 'json',
            headers: headers,
            data: {
                'id': selectedidx // Pass the hidden ID for deletion
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
	
	
	$('#templateInsert').click(function() {
		openModal("insert", "", "");
	});
	$('#templateSaveBtn').click(function() {
        if (isSaving) {
			return;
		}

        const action = $(this).data('action');
        const title = ($('#sms_title').val() || '').trim();
        const text = ($('#sms_template').val() || '').trim();

		if (!validateTemplateForm(title, text)) {
			return;
		}

		if (action === "modify" && !selectedidx) {
			alert('수정할 상용구를 다시 선택하세요.');
			closeModal();
			return;
		}

		setSaveButtonState(true);
        if (action === "insert") {
            insertTemplate(title, text);
        } else if (action === "modify") {
            updateTemplate(selectedidx, title, text);
        } else {
			setSaveButtonState(false);
		}
    });

	cancelButton?.addEventListener('click', closeModal);
	closeButton?.addEventListener('click', closeModal);
	templateInput?.addEventListener('input', updateTemplateLength);
	modalElement?.addEventListener('click', function(event) {
		if (event.target === modalElement) {
			closeModal();
		}
	});
	document.addEventListener('keydown', function(event) {
		if (event.key === 'Escape' && modalElement?.classList.contains('show')) {
			closeModal();
		}
	});
	updateTemplateLength();
    
	// ✅ 템플릿 추가 함수 (AJAX)
    function insertTemplate(title, text) {
        console.log("추가: ", title, text);
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;
        $.ajax({
            url: apiUrl('/smsInsert'),
            type: 'POST',
            contentType: 'application/json',
            headers: headers,
            data: JSON.stringify({
                title: title,
                template: text
            }),
            success: function(response) {
                console.log('Insert Success:', response);
				if (String(response?.result) !== '1') {
					alert(response?.message || '상용구 저장에 실패했습니다.');
					return;
				}
                closeModal();
                window.location.reload();
            },
            error: function(xhr, status, error) {
                console.log('Error inserting template:', xhr.responseText);
				alert('상용구 저장 중 오류가 발생했습니다.');
			},
			complete: function() {
				setSaveButtonState(false);
            }
        });
    }
	// ✅ 템플릿 수정 함수 (AJAX)
    function updateTemplate(id, title, text) {
        console.log("수정: ", id, title, text);
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;
        $.ajax({
            url: apiUrl('/smsUpdate'),
            type: 'POST',
            contentType: 'application/json',
            headers: headers,
            data: JSON.stringify({
                id: id,
                title: title,
                template: text
            }),
            success: function(response) {
                console.log('Update Success:', response);
				if (String(response?.result) !== '1') {
					alert(response?.message || '상용구 수정에 실패했습니다.');
					return;
				}
                closeModal();
                window.location.reload();
            },
            error: function(xhr, status, error) {
                console.log('Error updating template:', xhr.responseText);
				alert('상용구 수정 중 오류가 발생했습니다.');
			},
			complete: function() {
				setSaveButtonState(false);
            }
        });
    }
    
    
    
    page = page || 1; // Default to 1 if undefined
	perPageNum = perPageNum || 10; // Default to 20 if undefined
	
	function appendTemplates(smsTemplates) {
        let bodyHtml = '';
        smsTemplates.forEach(t => {
            bodyHtml += `
                <div class="grid-row template-row" data-id="${t.id}">
                    <div>${new Date(t.created_at).toLocaleString()}</div>
                    <div>${t.title}</div>
                    <div>${t.template}</div>
                </div>
            `;
        });
        $('.template-list').append(bodyHtml);

	    // ✅ `scrollDetector`를 항상 가장 아래로 유지
	    ensureScrollDetector();
	
	    // ✅ 새로 추가된 행에도 클릭 이벤트 부여
	    setupRowClickEvents();
    }

    // 기존 서버 렌더 행이 있으면 1페이지를 다시 불러오지 않도록 다음 페이지부터 시작
    const preRenderedRows = document.querySelectorAll('.template-list .template-row').length;
    setupRowClickEvents();
    if (preRenderedRows > 0) {
        page += 1;
    }

    // ✅ 무한스크롤 감지 (IntersectionObserver 설정)
    observer = new IntersectionObserver(entries => {
        if (!observerReady) return;
        if (entries[0].isIntersecting && !isLoading && hasMoreData) {
            loadTemplatesData();
        }
    }, { root: document.querySelector('.template-list'), threshold: 0.8 });

    ensureScrollDetector(); // ✅ scrollDetector 보장

    const scrollDetector = document.getElementById('scrollDetector');
    if (scrollDetector) {
        observerReady = true;
        observer.observe(scrollDetector);
    } else {
        console.error('scrollDetector를 찾을 수 없습니다.');
    }

    if (preRenderedRows === 0) {
        // 서버 렌더가 없을 때만 첫 페이지 로드
        loadTemplatesData();
    }

    function ensureScrollDetector() {
        let detector = document.getElementById('scrollDetector');
        const tableBody = document.querySelector('.template-list');

        if (!detector) {
            detector = document.createElement('div');
            detector.id = 'scrollDetector';
            detector.style.height = '10px';
            tableBody.appendChild(detector);
        } 
        tableBody.appendChild(detector); // 항상 가장 아래에 위치
		// ✅ `loading`도 가장 아래로 이동
	    const loadingElement = document.getElementById('loading');
	    if (loadingElement) {
	        tableBody.appendChild(loadingElement);
	    }
	
	    if (observer) {
            observer.disconnect();
	        observer.observe(detector);
	    }
    }

    function loadTemplatesData() {
        if (isLoading || !hasMoreData) return;

        isLoading = true;
        $('#loading').show();
        let url = `${apiUrl('/smsSetting')}?page=${page}&perPageNum=${perPageNum}&requestType=json`;

        console.log('Loading data: page=', page, 'perPageNum=', perPageNum);

        $.ajax({
            url: url,
            type: 'GET',
            success: function(response) {
                if (response.success) {
                    if (response.smsTemplates.length > 0) {
                        appendTemplates(response.smsTemplates);
                        page++; // ✅ 페이지 증가
                    }
                    hasMoreData = response.hasMore;
                } else {
                    hasMoreData = false;
                }
            },
            error: function() {
                console.error('데이터 로드 실패');
            },
            complete: function() {
                isLoading = false;
                $('#loading').hide();
            }
        });
    }
});
// 돔(DOM) 끝

let lastFocusedElement = null;

// ✅ 모달 창 열기 (통합: 추가 & 수정)
function openModal(action, title, template) {
    var modal = document.getElementById('smsTemplateModal');
    var body = document.querySelector('body');
    var modal_msg = document.getElementById('smsTemplateModalTitle');
    var titleInput = document.getElementById('sms_title');
    var templateInput = document.getElementById('sms_template');
    var templateLength = document.getElementById('sms_template_length');
    var saveButton = document.getElementById('templateSaveBtn');

    titleInput.value = title;
    templateInput.value = template;
	if (templateLength) {
		templateLength.textContent = templateInput.value.length;
	}

    if (action === "insert") {
        modal_msg.textContent = '상용구를 추가합니다.';
        saveButton.textContent = "추가하기";
    } else {
        modal_msg.textContent = '상용구를 수정합니다.';
        saveButton.textContent = "수정하기";
    }

    saveButton.setAttribute("data-action", action); // 동작 설정
    lastFocusedElement = document.activeElement;
    modal.classList.add('show');
    modal.setAttribute('aria-hidden', 'false');
    body.style.overflow = 'hidden';
	setTimeout(function() {
		titleInput.focus();
	}, 0);
}

// ✅ 모달 창 닫기
function closeModal() {
    var modal = document.getElementById('smsTemplateModal');
    var body = document.querySelector('body');
    var saveButton = document.getElementById('templateSaveBtn');
    var titleInput = document.getElementById('sms_title');
    var templateInput = document.getElementById('sms_template');
    var templateLength = document.getElementById('sms_template_length');

    modal.classList.remove('show');
    modal.setAttribute('aria-hidden', 'true');
    body.style.overflow = 'auto';
    saveButton.style.pointerEvents = 'auto';
    saveButton.style.opacity = '1';
	if (titleInput) {
		titleInput.value = '';
	}
	if (templateInput) {
		templateInput.value = '';
	}
	if (templateLength) {
		templateLength.textContent = '0';
	}
	if (lastFocusedElement && typeof lastFocusedElement.focus === 'function') {
		lastFocusedElement.focus();
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
