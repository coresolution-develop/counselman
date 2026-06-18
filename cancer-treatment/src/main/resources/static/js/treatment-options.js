(function () {
    const API = (window.__ctx || '/');
    const CATEGORY = 'treatment-options';
    const els = {
        body: document.getElementById('opt-body'),
        message: document.getElementById('opt-message'),
        open: document.getElementById('opt-create-open'),
        dialog: document.getElementById('opt-dialog'),
        form: document.getElementById('opt-form'),
        title: document.getElementById('opt-dialog-title'),
        id: document.getElementById('opt-id'),
        name: document.getElementById('opt-name'),
        color: document.getElementById('opt-color'),
        displayOrder: document.getElementById('opt-display-order'),
        dialogMessage: document.getElementById('opt-dialog-message'),
        cancel: document.getElementById('opt-cancel')
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
        fetch(API + 'api/settings/' + CATEGORY, { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (p) { items = p.items || []; render(); })
            .catch(function () { els.body.innerHTML = '<tr><td colspan="4" class="empty">불러오지 못했습니다.</td></tr>'; });
    }

    function render() {
        if (items.length === 0) {
            els.body.innerHTML = '<tr><td colspan="4" class="empty">등록된 옵션이 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = items.map(function (o) {
            const swatch = o.color ? '<i class="settings-color-swatch" style="background:' + escapeHtml(o.color) + '"></i>' : '-';
            return '<tr data-id="' + escapeHtml(o.id) + '">' +
                '<td>' + swatch + '</td>' +
                '<td>' + escapeHtml(o.name) + '</td>' +
                '<td style="text-align:right">' + escapeHtml(o.displayOrder || 0) + '</td>' +
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
        const o = row ? items.find(function (x) { return String(x.id) === row.dataset.id; }) : null;
        if (!o) return;
        if (btn.dataset.action === 'edit') openDialog(o);
        if (btn.dataset.action === 'delete') del(o);
    }

    function openDialog(o) {
        els.form.reset();
        els.id.value = o ? o.id : '';
        els.title.textContent = o ? '옵션 수정' : '옵션 등록';
        els.name.value = o ? o.name || '' : '';
        els.color.value = o && o.color ? o.color : '#1a74bf';
        els.displayOrder.value = o ? o.displayOrder || 0 : 0;
        els.dialogMessage.textContent = '';
        els.dialog.showModal();
        els.name.focus();
    }

    function save(event) {
        event.preventDefault();
        const id = els.id.value;
        const name = els.name.value.trim();
        const url = id ? API + 'api/settings/' + CATEGORY + '/' + id : API + 'api/settings/' + CATEGORY;
        fetch(url, {
            method: id ? 'PUT' : 'POST',
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ code: name, name: name, detail: '', color: els.color.value, displayOrder: Number(els.displayOrder.value || 0) })
        }).then(function (res) {
            return res.text().then(function (txt) {
                const data = txt ? JSON.parse(txt) : {};
                if (!res.ok) throw new Error(data.error || '저장에 실패했습니다.');
                return data;
            });
        }).then(function () { els.dialog.close(); load(); })
          .catch(function (e) { els.dialogMessage.textContent = e.message; });
    }

    function del(o) {
        if (!window.confirm('삭제하시겠습니까?')) return;
        fetch(API + 'api/settings/' + CATEGORY + '/' + encodeURIComponent(o.id), { method: 'DELETE', headers: { Accept: 'application/json' } })
            .then(function (res) { if (!res.ok) throw new Error('x'); load(); })
            .catch(function () { els.message.textContent = '삭제하지 못했습니다.'; });
    }

    function escapeHtml(v) {
        return String(v == null ? '' : v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    document.addEventListener('DOMContentLoaded', init);
})();
