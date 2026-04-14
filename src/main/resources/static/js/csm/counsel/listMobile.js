document.addEventListener("DOMContentLoaded", function () {
  $('.nav_link[data-menu="list"]', $('.nav_section')).addClass('active');

  const query = new URLSearchParams(window.location.search);
  let page = Number(query.get('page') || 1);
  const perPageNum = Number(query.get('perPageNum') || 20);
  let isLoading = false;
  let hasMoreData = true;

  const listEl = document.getElementById('mobileList');
  const emptyStateEl = document.getElementById('emptyState');
  const loadingEl = document.getElementById('loading');
  const scrollDetectorEl = document.getElementById('scrollDetector');
  const newCounselBtn = document.getElementById('new-counsel-mobile');
  const newReservationBtn = document.getElementById('new-reservation-mobile');
  const searchForm = document.getElementById('mobileSearchForm');
  const searchButton = document.getElementById('mobileSearchBtn');
  const keywordInput = document.getElementById('keywordInput');

  function escapeHTML(v) {
    if (v === null || v === undefined) return '';
    return String(v)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function getVal(row, key) {
    if (!row || !key) return '';
    if (key in row) return row[key] ?? '';
    const camel = key.toLowerCase().replace(/_([a-z0-9])/g, (_, g1) => g1.toUpperCase());
    if (camel in row) return row[camel] ?? '';
    const upper = key.toUpperCase();
    if (upper in row) return row[upper] ?? '';
    return '';
  }

  function getCurrentFilters() {
    return {
      dateRange: $('#dateRange').val(),
      searchType: $('#searchType').val(),
      keyword: $('#keywordInput').val(),
      end: $('#end').is(':checked') ? 'on' : ''
    };
  }

  function cardStatusClass(row) {
    const col10 = getVal(row, 'cs_col_10');
    const col19 = getVal(row, 'cs_col_19');
    const col09 = getVal(row, 'cs_col_09');

    if (col10 === 'BC') return 'bg-bc';
    if (col19 === '입원완료') return 'bg-admitted';
    if (col19 === '입원예약') return 'bg-reserved';
    if (['A', 'B', 'C'].includes(String(col09))) return 'bg-abc';
    return '';
  }

  function formatGuardians(row) {
    const guardians = Array.isArray(row.guardians) ? row.guardians : [];
    if (guardians.length === 0) {
      return {
        names: getVal(row, 'cs_col_13') || '-',
        relation: getVal(row, 'cs_col_14') || '-'
      };
    }
    const names = guardians.map(g => g?.name).filter(Boolean).join(', ') || '-';
    const relation = guardians.map(g => g?.relationship).filter(Boolean).join(', ') || '-';
    return { names, relation };
  }

  function appendCards(rows) {
    if (!Array.isArray(rows) || rows.length === 0) return;

    let html = '';
    rows.forEach(row => {
      if (!row || typeof row !== 'object') return;

      const csIdx = getVal(row, 'cs_idx');
      const patient = getVal(row, 'cs_col_01') || '-';
      const counselDate = getVal(row, 'cs_col_16') || '-';
      const counselor = getVal(row, 'cs_col_17') || '-';
      const result = getVal(row, 'cs_col_19') || '-';
      const currentStatus = getVal(row, 'cs_col_11') || '-';
      const { names, relation } = formatGuardians(row);
      const statusClass = cardStatusClass(row);

      html += `
        <article class="counsel-card ${statusClass}" data-cs-idx="${escapeHTML(csIdx)}">
          <div class="card-head">
            <h3 class="patient-name">${escapeHTML(patient)}</h3>
            <span class="result-badge">${escapeHTML(result)}</span>
          </div>
          <div class="card-grid">
            <div class="grid-item"><span>상담일자</span><strong>${escapeHTML(counselDate)}</strong></div>
            <div class="grid-item"><span>상담자</span><strong>${escapeHTML(counselor)}</strong></div>
            <div class="grid-item"><span>보호자명</span><strong>${escapeHTML(names)}</strong></div>
            <div class="grid-item"><span>관계</span><strong>${escapeHTML(relation)}</strong></div>
            <div class="grid-item full"><span>현상태</span><strong>${escapeHTML(currentStatus)}</strong></div>
          </div>
          <div class="card-foot">
            <button type="button" class="view-btn" data-cs-idx="${escapeHTML(csIdx)}">상세보기</button>
          </div>
        </article>
      `;
    });

    listEl.insertAdjacentHTML('beforeend', html);
  }

  function loadCounselData(filters, reset) {
    if (isLoading) return;
    if (!hasMoreData && !reset) return;

    if (reset) {
      page = 1;
      hasMoreData = true;
      listEl.innerHTML = '';
      emptyStateEl.style.display = 'none';
    }

    isLoading = true;
    loadingEl.style.display = 'flex';

    const payload = filters || getCurrentFilters();
    const url = `/csm/counsel/list?page=${page}&perPageNum=${perPageNum}&requestType=json`;
    $.ajax({
      url,
      type: 'GET',
      data: payload,
      dataType: 'json',
      success: function (response) {
        if (!response.success) {
          if (response.redirect) {
            window.location.href = response.redirect;
          }
          return;
        }

        const rows = Array.isArray(response.cslist) ? response.cslist : [];
        if (page === 1 && rows.length === 0) {
          emptyStateEl.style.display = 'block';
        } else {
          emptyStateEl.style.display = 'none';
        }

        appendCards(rows);
        page += 1;
        hasMoreData = !!response.hasMore;
      },
      error: function () {
        alert('데이터 로드 실패');
      },
      complete: function () {
        isLoading = false;
        loadingEl.style.display = 'none';
      }
    });
  }

  function onSearch(e) {
    if (e) e.preventDefault();
    loadCounselData(getCurrentFilters(), true);
  }

  if (searchForm) {
    searchForm.addEventListener('submit', onSearch);
  }
  if (searchButton) {
    searchButton.addEventListener('click', onSearch);
  }
  if (keywordInput) {
    keywordInput.addEventListener('keypress', function (e) {
      if (e.key === 'Enter') {
        e.preventDefault();
        onSearch(e);
      }
    });
  }

  if (newCounselBtn) {
    newCounselBtn.addEventListener('click', function () {
      const spinner = document.getElementById('spinner-overlay');
      if (spinner) spinner.style.display = 'flex';
      window.location.href = '/csm/counsel/new';
    });
  }
  if (newReservationBtn) {
    newReservationBtn.addEventListener('click', function () {
      const spinner = document.getElementById('spinner-overlay');
      if (spinner) spinner.style.display = 'flex';
      window.location.href = '/csm/counsel/reservation';
    });
  }

  listEl.addEventListener('click', function (e) {
    const btn = e.target.closest('.view-btn');
    const card = e.target.closest('.counsel-card');
    const csIdx = (btn && btn.dataset.csIdx) || (card && card.dataset.csIdx);
    if (!csIdx) return;

    const spinner = document.getElementById('spinner-overlay');
    if (spinner) spinner.style.display = 'flex';
    window.location.href = `/csm/counsel/new/${csIdx}`;
  });

  const observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting && !isLoading && hasMoreData) {
      loadCounselData(getCurrentFilters(), false);
    }
  }, { root: null, threshold: 0.1, rootMargin: '140px' });

  if (scrollDetectorEl) {
    observer.observe(scrollDetectorEl);
  }

  loadCounselData(getCurrentFilters(), false);
});
