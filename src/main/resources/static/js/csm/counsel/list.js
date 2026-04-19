document.addEventListener("DOMContentLoaded", function () {
  // 메뉴 활성화
  $('.nav_link[data-menu="list"]', $('.nav_section')).addClass('active');

  // ===== 전역 상태 =====
  let page = 1;
  let perPageNum = 30;
  let isLoading = false;
  let hasMoreData = true;
  let orderItems = [];
  let originalRows = []; // 초기 데이터 순서 저장
  let sortOrder = [];    // 각 열의 정렬 상태
  let observer;
  let activeQueryToken = 0;

  // DOM 캐시
  const tableBodyEl = document.querySelector('#table-body');
  const deleteButton = document.getElementById('del-counsel');
  const newCounselLink = document.getElementById('new-counsel');
  const newReservationLink = document.getElementById('new-reservation');
  const reservationToggleBtn = document.getElementById('reservationToggleBtn');
  const reservationBox = document.querySelector('.integrated-reservation-box');
  const dateRangeSelectEl = document.getElementById('dateRange');
  const startDateEl = document.getElementById('startDate');
  const endDateEl = document.getElementById('endDate');
  const dateFieldEl = document.getElementById('dateField');
  const calendarEl = document.getElementById('statusCalendar');
  const calTitleEl = document.getElementById('calTitle');
  const calGridEl = document.getElementById('calGrid');
  const calPrevEl = document.getElementById('calPrev');
  const calNextEl = document.getElementById('calNext');
  const dateInputToggleButtons = document.querySelectorAll('.date-input-toggle');
  const dateRangeCardEl = dateRangeSelectEl ? dateRangeSelectEl.closest('.modern-filter-item') : null;
  const detail = document.getElementById('detail');
  const set = document.getElementById('setting');
  const confirmed = document.getElementById('confirm');

  // 선택된 행
  let selectedCsIdx = null;
  const today = new Date();
  const todayStr = formatDateValue(today);
  let calendarViewYear = today.getFullYear();
  let calendarViewMonth = today.getMonth();
  let activeCalendarInput = startDateEl || endDateEl || null;

  // ========= 유틸 =========
  function escapeHTML(v) {
    if (v === null || v === undefined) return '';
    return String(v)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function normalizePotentialRank(v) {
    const rank = String(v || '').trim().toUpperCase();
    return ['A', 'B', 'C'].includes(rank) ? rank : '';
  }

  function renderPotentialBadge(v) {
    const rank = normalizePotentialRank(v);
    if (!rank) return '';
    return `<span class="potential-rank-badge potential-rank-${rank}" title="잠재고객 ${rank}" aria-label="잠재고객 ${rank}">${rank}</span>`;
  }

  function getCurrentFilters() {
    return {
      dateRange: $('#dateRange').val(),
      startDate: $('#startDate').val() || '',
      endDate: $('#endDate').val() || '',
      status: $('#statusFilter').val() || 'all',
      pathType: $('#pathTypeFilter').val() || 'all',
      searchType: $('#searchType').val() || '',
      keyword: $('#keywordInput').val() || '',
      end: $('#end').is(':checked') ? 'on' : ''
    };
  }

  function formatDateValue(dateObj) {
    const yyyy = dateObj.getFullYear();
    const mm = String(dateObj.getMonth() + 1).padStart(2, '0');
    const dd = String(dateObj.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  function applyQuickDateRange(daysText) {
    const days = parseInt(daysText, 10);
    if (Number.isNaN(days) || days <= 0) return;

    const today = new Date();
    const start = new Date(today);
    start.setDate(today.getDate() - days);

    $('#startDate').val(formatDateValue(start));
    $('#endDate').val(formatDateValue(today));
  }

  function syncDateRangeByInput() {
    const startDate = $('#startDate').val();
    const endDate = $('#endDate').val();
    if (startDate || endDate) {
      $('#dateRange').val('custom');
    }
    updateCustomDateRangeVisibility();
  }

  function updateCustomDateRangeVisibility() {
    if (!dateRangeCardEl) return;
    // 날짜 입력창은 항상 노출
    dateRangeCardEl.classList.add('is-custom-range');
  }

  function parseDateFromValue(raw) {
    if (!raw) return null;
    const m = String(raw).trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!m) return null;
    const yyyy = Number(m[1]);
    const mm = Number(m[2]);
    const dd = Number(m[3]);
    if (yyyy < 1900 || mm < 1 || mm > 12 || dd < 1 || dd > 31) return null;
    return new Date(yyyy, mm - 1, dd);
  }

  function closeCalendar() {
    if (!calendarEl) return;
    calendarEl.classList.remove('is-open');
    dateFieldEl?.classList.remove('is-open');
  }

  function renderCalendar(year, month) {
    if (!calTitleEl || !calGridEl) return;

    calTitleEl.textContent = `${year}.${month + 1}`;
    calGridEl.innerHTML = '';

    const firstDay = new Date(year, month, 1).getDay();
    const lastDate = new Date(year, month + 1, 0).getDate();
    const prevLastDate = new Date(year, month, 0).getDate();
    const selectedDate = (activeCalendarInput && activeCalendarInput.value) ? activeCalendarInput.value : todayStr;

    const onPickDate = (val, targetYear, targetMonth) => {
      if (!activeCalendarInput) return;
      activeCalendarInput.value = val;
      activeCalendarInput.dispatchEvent(new Event('change', { bubbles: true }));
      calendarViewYear = targetYear;
      calendarViewMonth = targetMonth;
      renderCalendar(calendarViewYear, calendarViewMonth);
      closeCalendar();
    };

    const prevYear = month === 0 ? year - 1 : year;
    const prevMonth = month === 0 ? 11 : month - 1;
    for (let i = firstDay - 1; i >= 0; i--) {
      const d = prevLastDate - i;
      const val = `${prevYear}-${String(prevMonth + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'sh-cal-day is-muted';
      btn.innerHTML = `<span class="sh-cal-num">${d}</span><span class="sh-cal-sub"></span>`;
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        onPickDate(val, prevYear, prevMonth);
      });
      calGridEl.appendChild(btn);
    }

    for (let d = 1; d <= lastDate; d++) {
      const val = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const isToday = val === todayStr;
      const dow = new Date(year, month, d).getDay();
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'sh-cal-day';
      if (dow === 0) btn.classList.add('is-sun');
      if (val === selectedDate) btn.classList.add('is-selected');
      btn.innerHTML = `<span class="sh-cal-num">${d}</span><span class="sh-cal-sub">${isToday ? '오늘' : ''}</span>`;
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        onPickDate(val, year, month);
      });
      calGridEl.appendChild(btn);
    }

    const nextYear = month === 11 ? year + 1 : year;
    const nextMonth = month === 11 ? 0 : month + 1;
    const totalCells = calGridEl.children.length;
    const nextFill = totalCells <= 35 ? 35 - totalCells : 42 - totalCells;
    for (let i = 1; i <= nextFill; i++) {
      const val = `${nextYear}-${String(nextMonth + 1).padStart(2, '0')}-${String(i).padStart(2, '0')}`;
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'sh-cal-day is-muted';
      btn.innerHTML = `<span class="sh-cal-num">${i}</span><span class="sh-cal-sub"></span>`;
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        onPickDate(val, nextYear, nextMonth);
      });
      calGridEl.appendChild(btn);
    }
  }

  function openCalendar(targetInput) {
    if (!calendarEl) return;
    activeCalendarInput = targetInput || activeCalendarInput || startDateEl || endDateEl;
    const baseDate = parseDateFromValue(activeCalendarInput?.value) || today;
    calendarViewYear = baseDate.getFullYear();
    calendarViewMonth = baseDate.getMonth();
    calendarEl.classList.add('is-open');
    dateFieldEl?.classList.add('is-open');
    renderCalendar(calendarViewYear, calendarViewMonth);
  }

  function validateDateFilterBeforeSearch() {
    const dateRange = $('#dateRange').val();
    const startDate = $('#startDate').val();
    const endDate = $('#endDate').val();

    if (dateRange === 'custom') {
      if (!startDate || !endDate) {
        alert('직접 선택은 시작일과 종료일을 모두 입력해주세요.');
        return false;
      }
      if (startDate > endDate) {
        alert('종료일은 시작일보다 빠를 수 없습니다.');
        return false;
      }
      return true;
    }

    if (dateRange === 'all') {
      $('#startDate').val('');
      $('#endDate').val('');
      return true;
    }

    applyQuickDateRange(dateRange);
    return true;
  }

  // scrollDetector를 .content-mid(있으면) 또는 body에 보장
  function ensureScrollDetector() {
    const container = document.querySelector('.content-mid') || document.body;
    let sd = document.getElementById('scrollDetector');
    if (!sd) {
      sd = document.createElement('div');
      sd.id = 'scrollDetector';
      sd.style.height = '1px';
      sd.style.width = '100%';
      sd.style.pointerEvents = 'none';
    }
    if (!container.contains(sd)) {
      container.appendChild(sd);
    }
    if (observer) {
      try { observer.unobserve(sd); } catch (e) {}
      observer.observe(sd);
    }
    return sd;
  }

  function adjustScrollDetector() {
    ensureScrollDetector(); // 컨테이너 끝에 존재/관찰 유지
  }

  function showSpinner() {
    const spinner = document.getElementById('spinner-overlay');
    if (spinner) spinner.style.display = 'flex';
  }

  function navigateWithSpinner(path) {
    showSpinner();
    setTimeout(function () {
      window.location.href = path;
    }, 100);
  }

  function showSpinnerAndNavigate() {
    navigateWithSpinner('/csm/counsel/new');
  }

  // ========= 상담 접수 카드 아코디언 =========
  if (reservationToggleBtn && reservationBox) {
    const RESERVATION_COLLAPSE_KEY = 'csm-list-reservation-collapsed';

    function applyReservationCollapsedState(collapsed) {
      reservationBox.classList.toggle('is-collapsed', collapsed);
      reservationToggleBtn.setAttribute('aria-expanded', String(!collapsed));
      reservationToggleBtn.textContent = collapsed ? '펼치기' : '접기';
    }

    let collapsed = false;
    try {
      collapsed = localStorage.getItem(RESERVATION_COLLAPSE_KEY) === 'true';
    } catch (e) {
      collapsed = false;
    }
    applyReservationCollapsedState(collapsed);

    reservationToggleBtn.addEventListener('click', function () {
      const nextCollapsed = !reservationBox.classList.contains('is-collapsed');
      applyReservationCollapsedState(nextCollapsed);
      try {
        localStorage.setItem(RESERVATION_COLLAPSE_KEY, String(nextCollapsed));
      } catch (e) {
        // localStorage 접근이 제한된 경우에도 UI는 동작하도록 무시
      }
    });
  }

  // ========= 이벤트 위임(행 선택/더블클릭) =========
  if (tableBodyEl) {
    tableBodyEl.addEventListener('click', function (event) {
  const clickedRow = event.target.closest('tr');
  if (!clickedRow) return;
  document.querySelectorAll('.selected-row').forEach(row => row.classList.remove('selected-row'));
  clickedRow.classList.add('selected-row');
  selectedCsIdx = clickedRow.getAttribute('data-cs-idx'); // 이미 위에서 getVal로 넣음
    });

    tableBodyEl.addEventListener('dblclick', function (event) {
      const clickedRow = event.target.closest('tr');
      if (!clickedRow) return;
      const cs_idx = clickedRow.getAttribute('data-cs-idx');
      if (cs_idx) {
        showSpinner();
        window.location.href = '/csm/counsel/new/' + cs_idx;
      }
    });
  }

  // ========= 삭제 =========
  if (deleteButton) {
    deleteButton.addEventListener('click', function () {
      if (!selectedCsIdx) {
        alert('삭제할 데이터를 먼저 선택해주세요.');
        return;
      }
      if (!confirm('삭제 시 복구가 불가능 합니다. 삭제하시겠습니까?')) return;
      showSpinner();
      const csrfToken  = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
      const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
      fetch('/csm/counsel/delete/' + selectedCsIdx, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
          [csrfHeader]: csrfToken
        }
      })
        .then(r => r.ok ? r.json() : Promise.reject(r))
        .then(res => {
          if (res.result === '1') {
            selectedCsIdx = null;
            window.location.reload();
          } else {
            alert('삭제도중 에러가 발생하였습니다.');
            console.error(res.message);
          }
        })
        .catch(err => {
          console.error('Delete failed:', err);
          alert('삭제 도중 에러가 발생하였습니다.');
        });
    });
  }

  // ========= 신규 등록 =========
  if (newCounselLink) {
    newCounselLink.addEventListener('click', showSpinnerAndNavigate);
  }
  if (newReservationLink) {
    newReservationLink.addEventListener('click', function () {
      navigateWithSpinner('/csm/counsel/reservation');
    });
  }

  // ========= 리스트 설정 팝업 =========
  function openSetting() {
    $("#popup").css('display', 'flex').hide().fadeIn();
  }
  if (set) set.addEventListener('click', openSetting);
  $('#close').click(function () { $("#popup").fadeOut(); });

  // 드래그 리스트 초기화 (inner-content)
  const innerContentList = document.getElementById('inner-content');
  const orderList = document.getElementById('order');
  if (innerContentList) {
    new Sortable(innerContentList, {
      group: { name: 'shared', pull: true, put: true },
      animation: 150,
      ghostClass: 'sortable-ghost',
    });
    innerContentList?.addEventListener('click', (e) => {
  const li = e.target.closest('.sortable-item');
  if (li && orderList) orderList.appendChild(li);
});
  }
  if (orderList) {
    new Sortable(orderList, {
      group: { name: 'shared', pull: true, put: true },
      animation: 150,
      ghostClass: 'sortable-ghost',
      onEnd: function () {
        // data-turn 재할당
        const orderedItems = document.querySelectorAll('#order .sortable-item');
        orderedItems.forEach((item, idx) => item.setAttribute('data-turn', idx + 1));
      }
    });
    orderList?.addEventListener('click', (e) => {
  const li = e.target.closest('.sortable-item');
  if (li && innerContentList) innerContentList.appendChild(li);
});
  }

  // 리스트 설정 저장
  if (confirmed) {
    confirmed.addEventListener('click', function () {
      const data = [];
      const orderedItems = document.querySelectorAll('#order .sortable-item');
      orderedItems.forEach((item, idx) => {
        item.setAttribute('data-turn', idx + 1);
        item.setAttribute('data-view', 'y');
        data.push({
          column: item.getAttribute('data-column'),
          comment: item.getAttribute('data-comment'),
          turn: item.getAttribute('data-turn'),
          viewYn: item.getAttribute('data-view')
        });
      });
      const innerItems = document.querySelectorAll('#inner-content .sortable-item');
      innerItems.forEach(item => {
        item.setAttribute('data-view', 'n');
        data.push({
          column: item.getAttribute('data-column'),
          comment: item.getAttribute('data-comment'),
          turn: null,
          viewYn: item.getAttribute('data-view')
        });
      });
// ★ CSRF 토큰/헤더명 읽기
    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
const clean = data.filter(it => it.column && it.column.trim() !== '');
if (clean.length !== data.length) {
  console.warn('[ListSetting] dropped items without column:', data.length - clean.length);
}
      showSpinner();
      fetch('/csm/counsel/ListSetting', {
        method: 'POST',
      credentials: 'same-origin',               // ★ 세션/쿠키 포함
        headers: { 'Content-Type': 'application/json',
        [csrfHeader]: csrfToken                 // ★ 토큰 첨부
		},
        body: JSON.stringify(data)
      })
        .then(r => r.ok ? r.text() : Promise.reject(r))
        .then(() => location.reload())
        .catch(async err => {
          const t = (await err.text?.()) || '';
          console.error('ListSetting error:', t || err);
          alert('설정 저장 중 오류가 발생했습니다.');
		  hideSpinner();
        });
    });
  }

  // 전체 제거 버튼
  const removeOrder = document.getElementById('removeOrder');
  if (removeOrder) {
    removeOrder.addEventListener('click', function () {
      const orderList = document.getElementById("order");
      const innerContentList = document.getElementById("inner-content");
      if (!orderList || !innerContentList) return;
      while (orderList.firstChild) innerContentList.appendChild(orderList.firstChild);
    });
  }

  // 엔터키로 검색
  $('#keywordInput').on('keypress', function (e) {
    if (e.keyCode === 13) $('#searchBtn').click();
  });

  $('#dateRange').on('change', function () {
    const selected = $(this).val();
    if (selected !== 'custom') {
      closeCalendar();
    }
    if (selected === 'all') {
      $('#startDate').val('');
      $('#endDate').val('');
      updateCustomDateRangeVisibility();
      return;
    }
    if (selected !== 'custom') {
      applyQuickDateRange(selected);
    }
    updateCustomDateRangeVisibility();
  });

  $('#startDate, #endDate').on('change', function () {
    syncDateRangeByInput();
  });

  if (startDateEl) {
    startDateEl.addEventListener('click', (e) => {
      e.stopPropagation();
      openCalendar(startDateEl);
    });
  }
  if (endDateEl) {
    endDateEl.addEventListener('click', (e) => {
      e.stopPropagation();
      openCalendar(endDateEl);
    });
  }
  if (dateInputToggleButtons && dateInputToggleButtons.length > 0) {
    dateInputToggleButtons.forEach((button) => {
      button.addEventListener('click', (e) => {
        e.stopPropagation();
        const targetId = button.getAttribute('data-target');
        const targetInput = targetId ? document.getElementById(targetId) : null;
        const isOpen = calendarEl?.classList.contains('is-open');
        const isSameTarget = targetInput && targetInput === activeCalendarInput;
        closeCalendar();
        if (!isOpen || !isSameTarget) {
          openCalendar(targetInput || startDateEl || endDateEl);
        }
      });
    });
  }
  if (calPrevEl) {
    calPrevEl.addEventListener('click', (e) => {
      e.stopPropagation();
      calendarViewMonth--;
      if (calendarViewMonth < 0) {
        calendarViewMonth = 11;
        calendarViewYear--;
      }
      renderCalendar(calendarViewYear, calendarViewMonth);
    });
  }
  if (calNextEl) {
    calNextEl.addEventListener('click', (e) => {
      e.stopPropagation();
      calendarViewMonth++;
      if (calendarViewMonth > 11) {
        calendarViewMonth = 0;
        calendarViewYear++;
      }
      renderCalendar(calendarViewYear, calendarViewMonth);
    });
  }
  if (calendarEl) {
    calendarEl.addEventListener('click', (e) => {
      e.stopPropagation();
    });
  }
  document.addEventListener('click', (e) => {
    if (dateFieldEl && !dateFieldEl.contains(e.target)) {
      closeCalendar();
    }
  });

  (function initDateFilter() {
    const startDate = $('#startDate').val();
    const endDate = $('#endDate').val();
    const dateRange = $('#dateRange').val();

    if (startDate && endDate) {
      $('#dateRange').val('custom');
      return;
    }

    if (dateRange === '10' || dateRange === '30' || dateRange === '90') {
      applyQuickDateRange(dateRange);
    }

    updateCustomDateRangeVisibility();
  })();

  // ========= 무한 스크롤 =========
  (function initObserver() {
    const rootEl = document.querySelector('.content-mid') || null; // 없으면 viewport
    observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && !isLoading && hasMoreData) {
        loadCounselData(); // formData 없이 호출 시 현재 필터 사용
      }
    }, { root: rootEl, threshold: 0.1, rootMargin: '100px' });

    const sd = ensureScrollDetector(); // sentinel 보장 + observe
    if (!sd) console.error('Initial #scrollDetector not found');
  })();

  // 검색 버튼
  $('#searchBtn').click(function (event) {
    event.preventDefault();
    if (!validateDateFilterBeforeSearch()) {
      return;
    }
    activeQueryToken += 1;
    // 상태 초기화
    page = 1;
    hasMoreData = true;
    isLoading = false;
    $('#table-body').empty();
    $('#table-header').empty();
    originalRows = [];
    // 새 필터로 첫 페이지
    loadCounselData(getCurrentFilters(), activeQueryToken);
  });

  // ========= 서버 키 정규화 =========
  function normalizeOrderItems(items) {
  return (items || [])
    .map(it => {
      if (!it) return null;
      if (it.column_name) return it;
      if (it.coulmn) return { ...it, column_name: it.coulmn };
      return it;
    })
    .filter(Boolean);
}

  // ========= 데이터 로드 =========
  function hasSameColumnOrder(prevItems, nextItems) {
    if (!Array.isArray(prevItems) || !Array.isArray(nextItems)) return false;
    if (prevItems.length !== nextItems.length) return false;
    for (let i = 0; i < prevItems.length; i++) {
      const prev = String(prevItems[i]?.column_name || '').trim();
      const next = String(nextItems[i]?.column_name || '').trim();
      if (prev !== next) return false;
    }
    return true;
  }

  function loadCounselData(formData, queryToken) {
  const token = (Number.isInteger(queryToken) && queryToken > 0)
    ? queryToken
    : (activeQueryToken > 0 ? activeQueryToken : 1);
  if (activeQueryToken === 0) {
    activeQueryToken = token;
  }
  if (isLoading || !hasMoreData) {
    return;
  }
  isLoading = true;
  $('#loading').show();

  const url = `/csm/counsel/list?page=${page}&perPageNum=${perPageNum}&requestType=json`;
  const payload = formData || getCurrentFilters();

  $.ajax({
    url,
    type: 'GET',
    data: payload,
    dataType: 'json',
    success: function (response) {
      if (token !== activeQueryToken) {
        return;
      }
      if (response.success) {
        if (page === 1) {
          const responseOrderItems = normalizeOrderItems(response.orderItems || []);
          if (orderItems.length === 0) {
            orderItems = responseOrderItems;
          } else if (responseOrderItems.length > 0 && !hasSameColumnOrder(orderItems, responseOrderItems)) {
            console.warn('[List] 필터 조회 중 변경된 orderItems 응답은 무시합니다.');
          }
          renderTableHeader(orderItems);
          bindSortEvents();
          sortOrder = Array(orderItems.length).fill(0);
          originalRows = [];
        }

        const list = Array.isArray(response.cslist) ? response.cslist : [];
        appendCounselData(list, orderItems);

        page++;
        hasMoreData = !!response.hasMore;

        adjustScrollDetector();
        checkScrollPosition();
      } else {
        window.location.href = response.redirect;
      }
    },
    error: function (xhr, status, error) {
      if (token !== activeQueryToken) {
        return;
      }
      console.error('Data load failed:', status, error);
      alert('데이터 로드 실패');
    },
    complete: function () {
      if (token !== activeQueryToken) {
        return;
      }
      isLoading = false;
      $('#loading').hide();
    }
  });
}

  // ========= 스크롤 보정 =========
  function checkScrollPosition() {
    const tableContainer = document.querySelector('.content-mid') || document.documentElement;
    const containerHeight = tableContainer.clientHeight;
    const scrollHeight = tableContainer.scrollHeight;
    const scrollTop = tableContainer.scrollTop || document.documentElement.scrollTop || 0;
    const distanceToBottom = scrollHeight - scrollTop - containerHeight;

    if (hasMoreData && !isLoading && (distanceToBottom < 300 || scrollHeight <= containerHeight)) {
      loadCounselData();
    }
  }

  // ========= 헤더 렌더링 =========
  function renderTableHeader(oItems) {
    if (!Array.isArray(oItems) || oItems.length === 0) {
      console.warn('orderItems가 비어있습니다.');
      return;
    }
    let headerHtml = '';
    const itemCount = oItems.length;

    oItems.forEach((col, index) => {
      if (!col) return;
      const label = (col.column_comment && String(col.column_comment).trim().length > 0)
        ? col.column_comment
        : col.column_name; // ★ 폴백
      if (!label) return;

      const leftBorder = (index === 0) ? 'left-border' : '';
      const rightBorder = (index === itemCount - 1) ? 'right-border' : '';
      headerHtml += `
        <th id="header-${index}" class="${leftBorder} ${rightBorder}">
          ${escapeHTML(label)}<span id="arrow-${index}" class="arrow"></span>
        </th>`;
    });

    $('#table-header').html(headerHtml);
  }

  // ========= 데이터 렌더링 =========
  function appendCounselData(cslist, orderItems) {
  if (!Array.isArray(cslist) || cslist.length === 0) return;

  let bodyHtml = '';

  cslist.forEach((row) => {
    if (!row || typeof row !== 'object') return;
    const guardians = Array.isArray(row.guardians) ? row.guardians : [];

    let className = '';
    const col10 = getVal(row, 'cs_col_10');
    const col19 = getVal(row, 'cs_col_19');
    const col09 = getVal(row, 'cs_col_09');

    if (col10 === 'BC') className += ' bg-bc';
    if (col19 === '입원완료') className += ' bg-admitted';
    else if (col19 === '입원예약') className += ' bg-color';
    else if (['A', 'B', 'C'].includes(String(col09))) className += ' bg-abc';

    const csIdx = getVal(row, 'cs_idx');
    bodyHtml += `<tr class="counselRow ${className}" data-cs-idx="${escapeHTML(csIdx)}">`;

    (orderItems || []).forEach(col => {
      const rawColumnName = (col?.column_name || col?.coulmn || '').trim();
      const columnName = rawColumnName.replace(/\[\]$/, '');
      if (!columnName) { bodyHtml += '<td></td>'; return; }

      // 기본 값
      let value = getVal(row, columnName);
      if (Array.isArray(value)) value = value.join(', ');

      if (columnName === 'cs_col_07' || columnName === 'cs_col_11') {
        bodyHtml += `
          <td class="limited-text" title="${escapeHTML(value)}">
            <div class="text-container">${escapeHTML(value)}</div>
          </td>`;
      } else if (columnName === 'cs_col_01') {
        const potentialBadge = renderPotentialBadge(col09);
        bodyHtml += `
          <td>
            <div class="patient-cell-wrap">
              <span class="patient-cell-name">${escapeHTML(value)}</span>
              ${potentialBadge}
            </div>
          </td>`;
      } else if (columnName === 'cs_col_09') {
        const potentialBadge = renderPotentialBadge(value);
        bodyHtml += `<td>${potentialBadge || escapeHTML(value)}</td>`;
      } else if (columnName === 'cs_col_13') {
        // 보호자명
        bodyHtml += '<td>';
        guardians.forEach(g => { bodyHtml += `<p>${escapeHTML(g?.name)}</p>`; });
        bodyHtml += '</td>';
      } else if (columnName === 'cs_col_14') {
        // 관계
        bodyHtml += '<td>';
        guardians.forEach(g => { bodyHtml += `<p>${escapeHTML(g?.relationship)}</p>`; });
        bodyHtml += '</td>';
      } else if (columnName === 'cs_col_15') {
        // 보호자 연락처
        bodyHtml += '<td>';
        guardians.forEach(g => { bodyHtml += `<p>${escapeHTML(g?.contact_number)}</p>`; });
        bodyHtml += '</td>';
      } else {
        bodyHtml += `<td>${escapeHTML(value)}</td>`;
      }
    });

    bodyHtml += '</tr>';
  });

  $('#table-body').append(bodyHtml);

  const newRows = Array.from(document.querySelectorAll('#table-body .counselRow'))
    .slice(originalRows.length);
  originalRows = originalRows.concat(newRows);
  applySort();
}
function snakeToCamel(s) {
  return String(s || '').replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());
}
function getVal(row, key) {
  if (!row) return '';
  if (key in row) return row[key] ?? '';
  // camel 대체 키도 한 번 시도 (cs_col_01 → csCol01)
  const camel = key.toLowerCase().replace(/_([a-z0-9])/g, (_, g1) => g1.toUpperCase());
  return row[camel] ?? '';
}
  // ========= 정렬 =========
  function bindSortEvents() {
    orderItems.forEach((_, index) => {
      const header = document.getElementById(`header-${index}`);
      if (header) header.addEventListener('click', () => sortTable(index));
    });
  }

  function sortTable(n) {
    if (!sortOrder[n]) sortOrder[n] = 0;
    sortOrder[n] = (sortOrder[n] + 1) % 3; // 0:원본, 1:오름, 2:내림
    applySort();
  }

  function applySort() {
    const tbody = document.getElementById("table-body");
    const allRows = Array.from(tbody.children);
    let dataRows = allRows.filter(row => row.classList && row.classList.contains('counselRow'));

    // 원본 기준으로 시작
    dataRows = [...originalRows];

    // 다중 정렬(순차 적용)
    sortOrder.forEach((order, index) => {
      if (order !== 0) {
        dataRows.sort((a, b) => {
          const cellA = (a.cells[index]?.textContent || '').toLowerCase();
          const cellB = (b.cells[index]?.textContent || '').toLowerCase();
          if (cellA < cellB) return order === 1 ? -1 : 1;
          if (cellA > cellB) return order === 1 ? 1 : -1;
          return 0;
        });
      }
    });

    // 테이블 재구성
    allRows.forEach(row => tbody.removeChild(row));
    dataRows.forEach(row => tbody.appendChild(row));

    ensureScrollDetector(); // sentinel 보장/재관찰
    updateArrows();
    checkScrollPosition();
  }

  function updateArrows() {
    orderItems.forEach((_, i) => {
      const arrow = document.getElementById(`arrow-${i}`);
      if (!arrow) return;
      if (sortOrder[i] === 1) arrow.className = 'arrow up';
      else if (sortOrder[i] === 2) arrow.className = 'arrow down';
      else arrow.className = 'arrow';
    });
  }
// 2-1) 서버에서 오는 orderItems 키들 정규화 (COLUMN_NAME / coulmn 섞여도 OK)
function normalizeOrderItems(list) {
  if (!Array.isArray(list)) return [];
  return list.map((it) => {
    const column_name =
      it?.column_name || it?.COLUMN_NAME || it?.coulmn || it?.COULMN || '';
    const column_comment =
      it?.column_comment || it?.COLUMN_COMMENT || it?.comment || '';
    const turn = it?.turn ?? it?.TURN ?? null;
    return { column_name, column_comment, turn };
  }).filter(x => x.column_name);
}

// 2-2) 행(Map)에서 값 꺼낼 때 스네이크/카멜/대문자 키 모두 시도
function getVal(row, key) {
  if (!row || !key) return '';
  // 그대로
  if (key in row) return row[key] ?? '';
  // 카멜 (cs_col_01 → csCol01)
  const camel = key.toLowerCase().replace(/_([a-z0-9])/g, (_, g1) => g1.toUpperCase());
  if (camel in row) return row[camel] ?? '';
  // 대문자
  const upper = key.toUpperCase();
  if (upper in row) return row[upper] ?? '';
  return '';
}
  // ======== 초기 첫 로드 강제 호출(중요!) ========
  if (activeQueryToken === 0) {
    activeQueryToken = 1;
  }
  loadCounselData(getCurrentFilters(), activeQueryToken);
});
