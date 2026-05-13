(function () {
    const API = (window.__ctx || '/');

    const els = {
        total: document.getElementById('dashboard-total'),
        reserved: document.getElementById('dashboard-reserved'),
        completed: document.getElementById('dashboard-completed'),
        canceled: document.getElementById('dashboard-canceled'),
        treatmentList: document.getElementById('dashboard-treatment-list'),
        wardList: document.getElementById('dashboard-ward-list'),
        recentList: document.getElementById('dashboard-recent-list'),
        lastSync: document.getElementById('dashboard-last-sync')
    };

    function init() {
        if (!els.total) return;
        loadDashboard();
        setInterval(loadDashboard, 15000);
    }

    function loadDashboard() {
        fetch(API + 'api/dashboard', { headers: { 'Accept': 'application/json' } })
            .then(function (response) {
                if (!response.ok) throw new Error('dashboard-load-failed');
                return response.json();
            })
            .then(render)
            .catch(function () {
                els.recentList.innerHTML = '<p class="empty">대시보드 데이터를 불러오지 못했습니다.</p>';
            });
    }

    function render(payload) {
        const summary = payload.summary || {};
        els.total.textContent = summary.total || 0;
        els.reserved.textContent = summary.reserved || 0;
        els.completed.textContent = summary.completed || 0;
        els.canceled.textContent = summary.canceled || 0;
        els.treatmentList.innerHTML = renderMetricList(payload.byTreatment || {});
        els.wardList.innerHTML = renderMetricList(payload.byWard || {});
        els.recentList.innerHTML = renderRecent(payload.recent || []);
        els.lastSync.textContent = '마지막 갱신: ' + new Date().toLocaleTimeString('ko-KR', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }

    function renderMetricList(data) {
        const entries = Object.entries(data);
        if (entries.length === 0) return '<p class="empty">집계 데이터가 없습니다.</p>';
        const max = Math.max.apply(null, entries.map(function (entry) { return entry[1]; }));
        return entries.map(function (entry) {
            const width = max === 0 ? 0 : Math.round((entry[1] / max) * 100);
            return '<div class="metric-row">' +
                '<div><strong>' + escapeHtml(entry[0]) + '</strong><span>' + entry[1] + '건</span></div>' +
                '<div class="metric-bar"><span style="width:' + width + '%"></span></div>' +
                '</div>';
        }).join('');
    }

    function renderRecent(items) {
        if (items.length === 0) return '<p class="empty">오늘 일정이 없습니다.</p>';
        return items.map(function (item) {
            return '<article class="dashboard-recent-item">' +
                '<time>' + escapeHtml(item.startTime) + '</time>' +
                '<div><strong>' + escapeHtml(item.patientName) + '</strong>' +
                '<span>' + escapeHtml(item.treatmentName) + ' · ' + escapeHtml(item.ward) + '</span></div>' +
                '<em class="' + statusClass(item.status) + '">' + escapeHtml(item.status) + '</em>' +
                '</article>';
        }).join('');
    }

    function statusClass(status) {
        if (status === '치료완료') return 'is-completed';
        if (status === '예약취소') return 'is-canceled';
        return 'is-reserved';
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    document.addEventListener('DOMContentLoaded', init);
})();
