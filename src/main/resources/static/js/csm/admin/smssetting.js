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
	
	const tableRows = document.querySelectorAll('.template-list .template-row');
	const deleteButton = document.getElementById('templateDelete');
	const modifyButton = document.getElementById('templateModify');
	let selectedidx = null;
	let selectedTitle = '';
	let selectedTemplate = '';
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
        const action = $(this).data('action'); // 현재 모달의 동작 ("insert" or "modify")
        const title = $('#sms_title').val();
        const text = $('#sms_template').val();

        if (action === "insert") {
            insertTemplate(title, text);
        } else if (action === "modify") {
            updateTemplate(selectedidx, title, text);
        }
    });
    
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
                closeModal();
                window.location.reload();
            },
            error: function(xhr, status, error) {
                console.log('Error inserting template:', xhr.responseText);
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
                closeModal();
                window.location.reload();
            },
            error: function(xhr, status, error) {
                console.log('Error updating template:', xhr.responseText);
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

// ✅ 모달 창 열기 (통합: 추가 & 수정)
function openModal(action, title, template) {
    var modal = document.querySelector('.modal');
    var body = document.querySelector('body');
    var modal_msg = document.querySelector('.menu_msg');
    var titleInput = document.getElementById('sms_title');
    var templateInput = document.getElementById('sms_template');
    var saveButton = document.getElementById('templateSaveBtn');

    titleInput.value = title;
    templateInput.value = template;

    if (action === "insert") {
        modal_msg.textContent = '상용구를 추가합니다.';
        saveButton.textContent = "추가하기";
    } else {
        modal_msg.textContent = '상용구를 수정합니다.';
        saveButton.textContent = "수정하기";
    }

    saveButton.setAttribute("data-action", action); // 동작 설정
    modal.classList.toggle('show');

    if (modal.classList.contains('show')) {
        body.style.overflow = 'hidden';
    }
}

// ✅ 모달 창 닫기
function closeModal() {
    var modal = document.querySelector('.modal');
    var body = document.querySelector('body');

    modal.classList.remove('show');
    body.style.overflow = 'auto';
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
