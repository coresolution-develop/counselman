(function () {
    const API = (window.__ctx || '/');
    const els = {
        body: document.getElementById('th-body'),
        message: document.getElementById('th-message'),
        open: document.getElementById('th-create-open'),
        dialog: document.getElementById('th-dialog'),
        form: document.getElementById('th-form'),
        title: document.getElementById('th-dialog-title'),
        id: document.getElementById('th-id'),
        name: document.getElementById('th-name'),
        displayOrder: document.getElementById('th-display-order'),
        dialogMessage: document.getElementById('th-dialog-message'),
        cancel: document.getElementById('th-cancel')
    };
    let items = [];

    function init() {
        if (!els.body) return;
        els.open.addEventListener('click', function () { openDialog(); });
        els.cancel.addEventListener('click', function () { els.dialog.close(); });
        els.form.addEventListener('submit', save);
        els.body.addEventListener('click', onClick);
        load();
    }

    function load() {
        fetch(API + 'api/therapists', { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (p) { items = p.items || []; render(); })
            .catch(function () { els.body.innerHTML = '<tr><td colspan="3" class="empty">불러오지 못했습니다.</td></tr>'; });
    }

    function render() {
        if (items.length === 0) {
            els.body.innerHTML = '<tr><td colspan="3" class="empty">등록된 치료사가 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = items.map(function (t) {
            return '<tr data-id="' + escapeHtml(t.id) + '">' +
                '<td>' + escapeHtml(t.name) + '</td>' +
                '<td style="text-align:right">' + escapeHtml(t.displayOrder || 0) + '</td>' +
                '<td><div class="settings-row-actions">' +
                '<button type="button" class="secondary-button" data-action="edit">수정</button>' +
                '<button type="button" class="secondary-button danger-soft-button" data-action="delete">삭제</button>' +
                '</div></td></tr>';
        }).join('');
    }

    function onClick(event) {
        const btn = event.target.closest('button');
        if (!btn) return;
        const row = event.target.closest('tr[data-id]');
        const t = row ? items.find(function (x) { return String(x.id) === row.dataset.id; }) : null;
        if (!t) return;
        if (btn.dataset.action === 'edit') openDialog(t);
        if (btn.dataset.action === 'delete') del(t);
    }

    function openDialog(t) {
        els.form.reset();
        els.id.value = t ? t.id : '';
        els.title.textContent = t ? '치료사 수정' : '치료사 등록';
        els.name.value = t ? t.name || '' : '';
        els.displayOrder.value = t ? t.displayOrder || 0 : 0;
        els.dialogMessage.textContent = '';
        els.dialog.showModal();
        els.name.focus();
    }

    function save(event) {
        event.preventDefault();
        const id = els.id.value;
        const url = id ? API + 'api/therapists/' + id : API + 'api/therapists';
        fetch(url, {
            method: id ? 'PUT' : 'POST',
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: els.name.value.trim(), displayOrder: Number(els.displayOrder.value || 0) })
        }).then(function (res) {
            return res.text().then(function (txt) {
                const data = txt ? JSON.parse(txt) : {};
                if (!res.ok) throw new Error(data.error || '저장에 실패했습니다.');
                return data;
            });
        }).then(function () { els.dialog.close(); load(); })
          .catch(function (e) { els.dialogMessage.textContent = e.message; });
    }

    function del(t) {
        if (!window.confirm('삭제하시겠습니까?')) return;
        fetch(API + 'api/therapists/' + encodeURIComponent(t.id), { method: 'DELETE', headers: { Accept: 'application/json' } })
            .then(function (res) { if (!res.ok) throw new Error('x'); load(); })
            .catch(function () { els.message.textContent = '삭제하지 못했습니다.'; });
    }

    function escapeHtml(v) {
        return String(v == null ? '' : v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    document.addEventListener('DOMContentLoaded', init);
})();
