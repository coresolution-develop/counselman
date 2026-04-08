// Initialize the navigation flag
let isNavigating = false;

// 내비게이션(링크 클릭) 처리 함수 (스피너와 확인창 포함)
function handleNavigation(url) {
	const currentPath = window.location.pathname;
	const isNewCounselPage = currentPath.startsWith('/csm/counsel/new');

	// 동일한 페이지로 이동하는 경우
	if (currentPath === url) {
		alert('현재 페이지에 있습니다.');
		return; // Prevent navigation
	}

	// /csm/counsel/new 또는 그 하위 페이지에 있을 때, 다른 페이지로 이동하는 경우 경고
	if (isNewCounselPage && currentPath !== url) {
		const confirmLeave = confirm('현재 페이지에서 벗어나면 입력한 내용이 저장되지 않을 수 있습니다. 정말로 이동하시겠습니까?');
		if (!confirmLeave) {
			return; // 내비게이션 취소
		}
	}

	/// 내비게이션 시작 플래그 설정 (이로써 beforeunload 이벤트가 작동하지 않음)
	isNavigating = true;

	// 다중 클릭 방지를 위해 모든 내비게이션 링크 비활성화
	const navLinks = document.querySelectorAll('.nav_link a');
	navLinks.forEach(function(link) {
		link.style.pointerEvents = 'none';
		link.style.opacity = '0.6'; // 시각적 효과 (옵션)
	});

	// 스피너 오버레이 표시 (CSS 트랜지션 적용)
	const spinner = document.getElementById('spinner-overlay');
	if (spinner) {
		spinner.style.display = 'flex'; // 반드시 보이도록
	}

	// 잠시 딜레이 후 내비게이션 (스피너가 보이도록)
	setTimeout(function() {
		window.location.href = url;
	}, 300); // 300ms 딜레이 (CSS 트랜지션과 일치)
}
// 스피너 숨기기 함수
function hideSpinner() {
    const spinner = document.getElementById('spinner-overlay');
    if (spinner) {
        spinner.style.display = 'none';
    }

    const navLinks = document.querySelectorAll('.nav_link a');
    navLinks.forEach(function(link) {
        link.style.pointerEvents = 'auto';
        link.style.opacity = '1';
    });
}
// beforeunload 이벤트: 사용자가 /csm/counsel/new 페이지에서 다른 곳으로 이동할 때 경고
window.addEventListener('beforeunload', function(e) {
	const currentPath = window.location.pathname;
	// isNavigating 플래그가 false일 때만 경고를 띄움
	if (currentPath.startsWith('/csm/counsel/new') && !isNavigating) {
		e.preventDefault();
		e.returnValue = ''; // Chrome 등에서 경고창이 뜨도록 (빈 문자열 지정)
	}
});
// pageshow 이벤트: 캐시 복원 시 스피너 숨김
window.addEventListener('pageshow', function(event) {
    if (event.persisted) { // 캐시에서 복원된 경우
        isNavigating = false;
        hideSpinner();
    }
});
// popstate 이벤트: 뒤로가기/앞으로가기 처리
window.addEventListener('popstate', function() {
    isNavigating = false; // 뒤로가기 시 내비게이션 플래그 초기화
    hideSpinner(); // 스피너 숨기기
});
// DOMContentLoaded 이벤트: 내비게이션 링크와 저장 버튼 이벤트 설정
document.addEventListener('DOMContentLoaded', function() {
	// 내비게이션 링크에 클릭 이벤트 등록
	const navLinks = document.querySelectorAll('.nav_link a');
	navLinks.forEach(function(link) {
		link.addEventListener('click', function(event) {
			// 링크에 'disabled-link' 클래스가 있는 경우, 기본 동작을 방지하고 알림 표시
			if (this.classList.contains('disabled-link')) {
				event.preventDefault(); // 비활성화된 링크의 기본 내비게이션 동작 방지
				alert('접근 권한이 없습니다.'); // 사용자에게 알림
				// handleNavigation을 호출하지 않으므로 스피너가 활성화되지 않음
				return;
			}
			event.preventDefault(); // 다른 모든 유효한 링크의 기본 내비게이션 동작 방지
			const url = this.getAttribute('href');
			handleNavigation(url); // 허용된 링크에 대해 handleNavigation 호출
		});
	});
	
	// 만약 폼을 사용한다면, 아래와 같이 폼 submit 이벤트에서 처리할 수도 있습니다.
    const counselForm = document.getElementById('counselForm');
    if (counselForm) {
        counselForm.addEventListener('submit', function() {
            isNavigating = true; // 폼 제출 시에도 경고창이 뜨지 않음
        });
    }
    // 페이지 로드 시 스피너 숨기기 (뒤로가기 후 잔재 방지)
    hideSpinner();
});