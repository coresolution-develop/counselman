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
	
	const deleteButton = document.getElementById('templateDelete');
	const modifyButton = document.getElementById('templateModify');
	let selectedidx = null;
    let selectedTitle = '';
    let selectedContent = '';

	function setupRowClickEvents() {
		const tableRows = document.querySelectorAll('.template-list .template-row');
		tableRows.forEach(row => {
			if (row.dataset.clickBound === '1') return;
			row.dataset.clickBound = '1';
		    row.addEventListener('click', function() {
		        // 기존에 선택된 행의 스타일을 제거
		        tableRows.forEach(r => r.classList.remove('selected-row'));
		
		        // 클릭된 행에 'selected-row' 클래스 추가
		        this.classList.add('selected-row');
		
		        // data-cs-idx 속성에서 cs_idx 값 가져오기
		        const idx = this.getAttribute('data-id');
	            console.log('Selected id:', idx);
				selectedidx = idx;
		        const cells = this.getElementsByTagName('div');
		        if (cells.length >= 3) {
		            selectedTitle = cells[1].textContent.trim();
		            selectedContent = cells[2].textContent.trim();
		        }
		        console.log(selectedTitle);
		        console.log(selectedContent);
		        // 추가적인 동작이 필요한 경우 여기에 작성
		        // 예: 선택된 cs_idx를 다른 곳에 표시하거나 사용
		    });
		    row.addEventListener('dblclick', function() {
	            const idx = this.getAttribute('data-id');
	            selectedidx = idx;
	        });
		    
		    
		});
	}
	
	
    // ✅ "추가" 버튼 클릭 시 모달 열기
    $('#templateInsert').click(function() {
        openModal("insert", "", "");
    });

    // ✅ "수정" 버튼 클릭 시 모달 열기
    modifyButton.addEventListener('click', function() {
        if (selectedidx) {
            openModal("modify", selectedTitle, selectedContent);
        }
    });

    // ✅ 삭제 버튼 클릭
    deleteButton.addEventListener('click', function() {
        if (selectedidx) {
            if (confirm('삭제 시 복구가 불가능 합니다. 삭제하시겠습니까?')) {
                deleteCard();
            }
        } else {
            alert('삭제할 명함을 선택하세요.');
        }
    });

    // ✅ 통합 저장 버튼 (추가 & 수정)
    $('#templateSaveBtn').click(function() {
        const action = $(this).data('action'); // 현재 모달 동작 ("insert" or "modify")
        const title = $('#card_title').val();
        const content = $('#card_content').val();

        if (action === "insert") {
            insertCard(title, content);
        } else if (action === "modify") {
            updateCard(selectedidx, title, content);
        }
    });

    // ✅ 명함 추가 함수 (AJAX)
    function insertCard(title, content) {
        console.log("추가: ", title, content);
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;
        $.ajax({
            url: apiUrl('/InsertCard'),
            type: 'POST',
            contentType: 'application/json',
            headers: headers,
            data: JSON.stringify({
                title: title,
                content: content
            }),
            success: function(response) {
                console.log('Insert Success:', response);
                closeModal();
                window.location.reload();
            },
            error: function(xhr, status, error) {
                console.log('Error inserting card:', xhr.responseText);
            }
        });
    }

    // ✅ 명함 수정 함수 (AJAX)
    function updateCard(id, title, content) {
        console.log("수정: ", id, title, content);
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;
        $.ajax({
            url: apiUrl('/UpdateCard'),
            type: 'POST',
            contentType: 'application/json',
            headers: headers,
            data: JSON.stringify({
                id: id,
                title: title,
                content: content
            }),
            success: function(response) {
                console.log('Update Success:', response);
                closeModal();
                window.location.reload();
            },
            error: function(xhr, status, error) {
                console.log('Error updating card:', xhr.responseText);
            }
        });
    }

    // ✅ 명함 삭제 함수 (AJAX)
    function deleteCard() {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;
        $.ajax({
            url: apiUrl('/DeleteCard'),
            type: 'POST',
            contentType: 'application/json',
            headers: headers,
            data: JSON.stringify({ 'id': selectedidx }),
            success: function(response) {
                console.log('Delete Success:', response);
                window.location.reload();
            },
            error: function(error) {
                console.log('Delete Error:', error);
            }
        });
    }
    
    page = page || 1; // Default to 1 if undefined
	perPageNum = perPageNum || 10; // Default to 20 if undefined
    
    function appendTemplates(smsTemplates) {
        let bodyHtml = '';
        smsTemplates.forEach(c => {
            bodyHtml += `
                <div class="grid-row template-row" data-id="${c.id}">
                    <div>${new Date(c.created_at).toLocaleString()}</div>
                    <div>${c.title}</div>
                    <div>${c.content}</div>
                </div>
            `;
        });
        $('.template-list').append(bodyHtml);

	    // ✅ `scrollDetector`를 항상 가장 아래로 유지
	    ensureScrollDetector();
	
	    // ✅ 새로 추가된 행에도 클릭 이벤트 부여
	    setupRowClickEvents();
    }
    // ✅ 무한스크롤 감지 (IntersectionObserver 설정)
	    observer = new IntersectionObserver(entries => {
	        if (entries[0].isIntersecting && !isLoading && hasMoreData) {
	            loadCardsData();
	        }
	    }, { root: document.querySelector('.template-list'), threshold: 0.1 });

    ensureScrollDetector(); // ✅ scrollDetector 보장
    const scrollDetector = document.getElementById('scrollDetector');
    if (scrollDetector) {
        observer.observe(scrollDetector);
    } else {
        console.error('scrollDetector를 찾을 수 없습니다.');
    }
    
    function ensureScrollDetector() {
        let detector = document.getElementById('scrollDetector');
        const tableBody = document.querySelector('.template-list');

        if (!detector) {
            detector = document.createElement('div');
            detector.id = 'scrollDetector';
            detector.style.height = '10px';
            tableBody.appendChild(detector);
        } else {
	        tableBody.appendChild(detector); // 항상 가장 아래에 위치
	    }
		// ✅ `loading`도 가장 아래로 이동
	    const loadingElement = document.getElementById('loading');
	    if (loadingElement) {
	        tableBody.appendChild(loadingElement);
	    }
	
	    if (observer) {
	        observer.unobserve(detector);
	        observer.observe(detector);
	    }
    }
    
    function loadCardsData() {
        if (isLoading || !hasMoreData) return;

        isLoading = true;
        $('#loading').show();
        let url = `${apiUrl('/cardsetting')}?page=${page}&perPageNum=${perPageNum}&requestType=json`;

        console.log('Loading data: page=', page, 'perPageNum=', perPageNum);

        $.ajax({
            url: url,
            type: 'GET',
            success: function(response) {
                if (response.success) {
                    if (response.card.length > 0) {
                        appendTemplates(response.card);
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

	    // 기존 서버 렌더 행이 있으면 1페이지를 다시 불러오지 않도록 다음 페이지부터 시작
	    const preRenderedRows = document.querySelectorAll('.template-list .template-row').length;
	    setupRowClickEvents();
	    if (preRenderedRows > 0) {
	        page += 1;
	    } else {
	        // 서버 렌더가 없을 때만 첫 페이지 로드
	        loadCardsData();
	    }
	});
// 돔 dom 끝 

// ✅ 모달 창 열기 (추가 & 수정 통합)
function openModal(action, title, content) {
    var modal = document.querySelector('.modal');
    var body = document.querySelector('body');
    var modal_msg = document.querySelector('.menu_msg');
    var titleInput = document.getElementById('card_title');
    var contentInput = document.getElementById('card_content');
    var saveButton = document.getElementById('templateSaveBtn');

    titleInput.value = title;
    contentInput.value = content;

    if (action === "insert") {
        modal_msg.textContent = '서명을 추가합니다.';
        saveButton.textContent = "추가하기";
    } else {
        modal_msg.textContent = '서명을 수정합니다.';
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

// ✅ 스피너 표시
function spinner() {
    const spinner = document.getElementById('spinner-overlay');
    if (spinner) {
        spinner.classList.add('show');
        spinner.style.display = 'flex';
    }
}

// ✅ 스피너 감추기
function hideSpinner() {
    const spinner = document.getElementById('spinner-overlay');
    if (spinner) {
        spinner.style.display = 'none';
    }
}
