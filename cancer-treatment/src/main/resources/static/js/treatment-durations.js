(function () {
    const API = (window.__ctx || '/');
    const els = {
        body: document.getElementById('dur-body'),
        message: document.getElementById('dur-message')
    };
    let items = [];

    function init() {
        if (!els.body) return;
        els.body.addEventListener('click', onClick);
        load();
    }

    function load() {
        fetch(API + 'api/treatment-durations', { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (p) { items = p.items || []; render(); })
            .catch(function () { els.body.innerHTML = '<tr><td colspan="4" class="empty">불러오지 못했습니다.</td></tr>'; });
    }

    function render() {
        if (items.length === 0) {
            els.body.innerHTML = '<tr><td colspan="4" class="empty">등록된 치료 항목이 없습니다. \'치료 설정\'에서 먼저 추가하세요.</td></tr>';
            return;
        }
        els.body.innerHTML = items.map(function (t) {
            return '<tr data-id="' + escapeHtml(t.id) + '">' +
                '<td>' + escapeHtml(t.treatmentName) + '</td>' +
                '<td>' + escapeHtml(t.roomName || '-') + '</td>' +
                '<td><input type="number" class="dur-input" min="0" max="600" step="5" value="' +
                    (t.durationMinutes != null ? escapeHtml(t.durationMinutes) : '') + '" placeholder="미설정"></td>' +
                '<td><button type="button" class="secondary-button" data-action="save">저장</button></td>' +
                '</tr>';
        }).join('');
    }

    function onClick(event) {
        const btn = event.target.closest('button[data-action="save"]');
        if (!btn) return;
        const row = event.target.closest('tr[data-id]');
        if (!row) return;
        const raw = row.querySelector('.dur-input').value.trim();
        const minutes = raw === '' ? null : Number(raw);
        if (minutes != null && (isNaN(minutes) || minutes < 0 || minutes > 600)) {
            els.message.textContent = '소요시간은 0~600분 사이로 입력하세요.';
            return;
        }
        fetch(API + 'api/treatment-durations/' + encodeURIComponent(row.dataset.id), {
            method: 'PATCH',
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ durationMinutes: minutes })
        }).then(function (res) {
            return res.text().then(function (txt) {
                const data = txt ? JSON.parse(txt) : {};
                if (!res.ok) throw new Error(data.error || '저장에 실패했습니다.');
                return data;
            });
        }).then(function () { els.message.textContent = '저장되었습니다.'; load(); })
          .catch(function (e) { els.message.textContent = e.message; });
    }

    function escapeHtml(v) {
        return String(v == null ? '' : v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    document.addEventListener('DOMContentLoaded', init);
})();
