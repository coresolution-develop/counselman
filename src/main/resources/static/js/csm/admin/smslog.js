$(document).ready(function() {
	$('.menu5', $('.nav_section')).addClass('active');
	
	let page = 1;
	let perPageNum = 10;
	let isLoading = false;
	let hasMoreData = true;
	let observer;
	
	const formData = {
		keyword: $('#keywordInput').val(),
	}
	
    page = page || 1; // Default to 1 if undefined
	perPageNum = perPageNum || 10; // Default to 20 if undefined
	
	function appendLogs(smsHistory) {
		let bodyHtml = '';
		let reserveTime = '';
		smsHistory.forEach(h => {
			if (!h.reserve_time) {
			    reserveTime = '';
			} else {
				// Date 객체로 변환 (브라우저 호환 위해)
		        reserveTime = new Date(h.reserve_time).toLocaleString();
		        // 또는, 그냥 reserveTime = h.reserve_time;
			}
			console.log(h.created_at);
			console.log(h.reserve_time);
		    bodyHtml += `
		        <div class="grid-row2" data-id="${h.id}">
		            <div>${new Date(h.created_at).toLocaleString()}</div>
					<div>${reserveTime}</div>
		            <div>${h.from_phone}</div>
		            <div>${h.to_phone}</div>
		            <div>${h.status}</div>
		            <div>${h.contents }</div>
		        </div>
		    `;
		});
		$('.template-list').append(bodyHtml);

		
	    // ✅ `scrollDetector`를 항상 가장 아래로 유지
	    ensureScrollDetector();
	
    }

    // ✅ 무한스크롤 감지 (IntersectionObserver 설정)
	const scrollContainer = document.querySelector('.scroll-container');
    observer = new IntersectionObserver(entries => {
        if (entries[0].isIntersecting && !isLoading && hasMoreData) {
            loadlogData();
        }
    }, { root: scrollContainer, threshold: 1.0 });

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

    function loadlogData(reset = false) {
		if (isLoading || !hasMoreData) return;
				
		if (reset) {
            page = 1;
            hasMoreData = true;
            $('.template-list').empty(); // ✅ 기존 데이터 초기화
        }
        
        let keyword = $('#keywordInput').val();
		
        isLoading = true;
        $('#loading').show();
		let url = `/csm/smslog?page=${page}&perPageNum=${perPageNum}&keyword=${encodeURIComponent(keyword)}&requestType=json`;

        console.log('Loading data: page=', page, 'perPageNum=', perPageNum);
		
        $.ajax({
            url: url,
            type: 'GET',
            success: function(response) {
                if (response.success) {
                    if (response.smsHistory.length > 0) {
                        appendLogs(response.smsHistory);
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
	
	// ✅ 초기 데이터 로드 실행
	loadlogData();

	$('#searchBtn').click(function() {
	    loadlogData(true); // ✅ 검색 시 데이터 초기화 후 재조회
	});
	
});