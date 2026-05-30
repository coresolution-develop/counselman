(function () {
  'use strict';
  var ctx = window.__ctx || '/';
  var api = function (p) { return ctx.replace(/\/$/, '') + p; };

  var perPage = 10;
  var state = { page: 1, type: '', keyword: '' };

  var body = document.getElementById('history-body');
  var pager = document.getElementById('pager');

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function statusPill(status, reserveTime) {
    var s = String(status || '');
    if (s === 'SUCCESS' || s === '전송완료') return '<span class="pill ok">완료</span>';
    if (s === '전송중') return '<span class="pill wait">전송중</span>';
    if (reserveTime) return '<span class="pill wait">예약</span>';
    return '<span class="pill fail">' + esc(s || '실패') + '</span>';
  }

  function render(data) {
    var rows = (data && data.rows) || [];
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="5" class="empty">발송 내역이 없습니다.</td></tr>';
      pager.innerHTML = '';
      return;
    }
    body.innerHTML = rows.map(function (r) {
      return '<tr>'
        + '<td>' + esc(r.created_at) + '</td>'
        + '<td>' + esc(String(r.send_type || '').toUpperCase()) + '</td>'
        + '<td>' + esc(r.to_phone) + '</td>'
        + '<td>' + esc(r.contents) + '</td>'
        + '<td>' + statusPill(r.status, r.reserve_time) + '</td>'
        + '</tr>';
    }).join('');
    renderPager(data.total || 0);
  }

  function renderPager(total) {
    var pages = Math.max(1, Math.ceil(total / perPage));
    var html = '';
    html += '<button ' + (state.page <= 1 ? 'disabled' : '') + ' data-page="' + (state.page - 1) + '">이전</button>';
    var start = Math.max(1, state.page - 2);
    var end = Math.min(pages, start + 4);
    for (var p = start; p <= end; p++) {
      html += '<button class="' + (p === state.page ? 'active' : '') + '" data-page="' + p + '">' + p + '</button>';
    }
    html += '<button ' + (state.page >= pages ? 'disabled' : '') + ' data-page="' + (state.page + 1) + '">다음</button>';
    pager.innerHTML = html;
  }

  function load() {
    var q = '?page=' + state.page + '&perPageNum=' + perPage
      + '&type=' + encodeURIComponent(state.type)
      + '&keyword=' + encodeURIComponent(state.keyword);
    body.innerHTML = '<tr><td colspan="5" class="empty">불러오는 중…</td></tr>';
    fetch(api('/api/sms/history' + q))
      .then(function (res) { return res.json(); })
      .then(render)
      .catch(function () {
        body.innerHTML = '<tr><td colspan="5" class="empty">불러오지 못했습니다.</td></tr>';
      });
  }

  pager.addEventListener('click', function (e) {
    var btn = e.target.closest('button[data-page]');
    if (!btn || btn.disabled) return;
    state.page = parseInt(btn.dataset.page, 10);
    load();
  });
  document.getElementById('search-btn').addEventListener('click', function () {
    state.page = 1;
    state.type = document.getElementById('type-filter').value;
    state.keyword = document.getElementById('keyword').value.trim();
    load();
  });
  document.getElementById('keyword').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') document.getElementById('search-btn').click();
  });

  load();
})();
