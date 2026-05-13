(function () {
    const API = (window.__ctx || '/');

    const els = {
        body:        document.getElementById('pkg-body'),
        message:     document.getElementById('pkg-message'),
        filter:      document.getElementById('pkg-filter'),
        filterCat:   document.getElementById('pkg-filter-category'),
        filterRoom:  document.getElementById('pkg-filter-room'),
        createOpen:  document.getElementById('pkg-create-open'),
        dialog:      document.getElementById('pkg-dialog'),
        form:        document.getElementById('pkg-form'),
        title:       document.getElementById('pkg-dialog-title'),
        id:           document.getElementById('pkg-id'),
        category:     document.getElementById('pkg-category'),
        room:         document.getElementById('pkg-room'),
        name:         document.getElementById('pkg-name'),
        abbreviation: document.getElementById('pkg-abbreviation'),
        unitPrice:    document.getElementById('pkg-unit-price'),
        billingUnit: document.getElementById('pkg-billing-unit'),
        frequency:   document.getElementById('pkg-frequency'),
        order:       document.getElementById('pkg-display-order'),
        dialogMsg:   document.getElementById('pkg-dialog-message'),
        cancel:      document.getElementById('pkg-cancel')
    };

    let state = { categories: [], rooms: [], items: [] };

    function init() {
        if (!els.body) return;
        els.filter.addEventListener('submit', function (e) { e.preventDefault(); load(); });
        els.createOpen.addEventListener('click', function () { openDialog(); });
        els.cancel.addEventListener('click', function () { els.dialog.close(); });
        els.form.addEventListener('submit', save);
        els.body.addEventListener('click', onRowAction);
        els.room.addEventListener('change', refreshItemOptions);
        loadMasters().then(load);
    }

    function refreshItemOptions() {
        const roomId = els.room.value;
        const room = state.rooms.find(function (r) { return String(r.id) === String(roomId); });
        const items = (room && room.treatmentItems) || [];
        const datalist = document.getElementById('pkg-name-options');
        const hint = document.getElementById('pkg-name-hint');
        if (datalist) {
            datalist.innerHTML = items.map(function (it) {
                return '<option value="' + escapeHtml(it) + '">';
            }).join('');
        }
        if (hint) {
            if (!roomId) hint.textContent = '치료실을 먼저 선택하면 등록된 치료항목이 제안됩니다.';
            else if (!items.length) hint.textContent = '이 치료실에 등록된 치료항목이 없습니다. 치료실 페이지에서 항목을 먼저 추가하거나 직접 입력하세요.';
            else hint.textContent = '제안: ' + items.join(', ') + ' (직접 입력도 가능)';
        }
    }

    function loadMasters() {
        return Promise.all([
            fetch(API + 'api/settings', { headers: { Accept: 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : {}; }),
            fetch(API + 'api/treatment-rooms', { headers: { Accept: 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : { items: [] }; })
        ]).then(function (results) {
            state.categories = results[0].packageCategories || [];
            state.rooms      = (results[1] && results[1].items) || [];
            fillCategorySelect(els.filterCat,  '전체 카테고리');
            fillCategorySelect(els.category,   '선택');
            fillRoomSelect    (els.filterRoom, '전체 치료실');
            fillRoomSelect    (els.room,       '선택');
        });
    }

    function fillCategorySelect(select, placeholder) {
        select.innerHTML = '<option value="">' + escapeHtml(placeholder) + '</option>'
            + state.categories.map(function (c) {
                return '<option value="' + escapeHtml(c.id) + '">' + escapeHtml(c.name) + '</option>';
            }).join('');
    }

    function fillRoomSelect(select, placeholder) {
        select.innerHTML = '<option value="">' + escapeHtml(placeholder) + '</option>'
            + state.rooms.map(function (r) {
                return '<option value="' + escapeHtml(r.id) + '">' + escapeHtml(r.roomName) + '</option>';
            }).join('');
    }

    function load() {
        const params = new URLSearchParams();
        if (els.filterCat.value)  params.set('categoryId', els.filterCat.value);
        if (els.filterRoom.value) params.set('roomId',     els.filterRoom.value);
        fetch(API + 'api/treatment-packages?' + params.toString(), { headers: { Accept: 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
            .then(function (data) {
                state.items = data.items || [];
                render();
            })
            .catch(function () {
                setMessage('카탈로그를 불러오지 못했습니다.');
            });
    }

    function render() {
        if (!state.items.length) {
            els.body.innerHTML = '<tr><td colspan="8" class="empty">등록된 치료비 항목이 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = state.items.map(function (it) {
            const unit = it.billingUnit === 'DAY' ? '일' : '주';
            return '<tr data-id="' + escapeHtml(it.id) + '">' +
                '<td>' + escapeHtml(it.categoryName || '-') + '</td>' +
                '<td>' + escapeHtml(it.treatmentRoomName || '-') + '</td>' +
                '<td><strong>' + escapeHtml(it.packageName) + '</strong></td>' +
                '<td>' + (it.abbreviation ? '<code class="abbrev-chip">' + escapeHtml(it.abbreviation) + '</code>' : '<span style="color:#9097a3">-</span>') + '</td>' +
                '<td style="text-align:right">' + formatCurrency(it.unitPrice) + '</td>' +
                '<td>' + unit + ' ' + escapeHtml(it.frequency) + '회</td>' +
                '<td style="text-align:right">' + escapeHtml(it.displayOrder || 0) + '</td>' +
                '<td>' +
                '<button type="button" class="secondary-button" data-action="edit">수정</button> ' +
                '<button type="button" class="secondary-button danger-soft-button" data-action="delete">삭제</button>' +
                '</td></tr>';
        }).join('');
    }

    function onRowAction(event) {
        const button = event.target.closest('button[data-action]');
        if (!button) return;
        const row = event.target.closest('tr[data-id]');
        if (!row) return;
        const item = state.items.find(function (it) { return String(it.id) === row.dataset.id; });
        if (!item) return;
        if (button.dataset.action === 'edit')   openDialog(item);
        if (button.dataset.action === 'delete') removeItem(item);
    }

    function openDialog(item) {
        els.form.reset();
        els.id.value           = item ? item.id : '';
        els.title.textContent  = item ? '항목 수정' : '항목 등록';
        els.category.value     = item ? item.categoryId : '';
        els.room.value         = item ? item.treatmentRoomId : '';
        els.name.value         = item ? item.packageName : '';
        els.abbreviation.value = item ? (item.abbreviation || '') : '';
        els.unitPrice.value    = item ? (item.unitPrice || 0) : 0;
        els.billingUnit.value  = item ? (item.billingUnit || 'WEEK') : 'WEEK';
        els.frequency.value    = item ? (item.frequency || 1) : 1;
        els.order.value        = item ? (item.displayOrder || 0) : 0;
        els.dialogMsg.textContent = '';
        refreshItemOptions();
        els.dialog.showModal();
    }

    function save(event) {
        event.preventDefault();
        const id = els.id.value;
        const url    = id ? API + 'api/treatment-packages/' + encodeURIComponent(id) : API + 'api/treatment-packages';
        const method = id ? 'PUT' : 'POST';
        const body = {
            categoryId:      Number(els.category.value) || null,
            treatmentRoomId: Number(els.room.value) || null,
            packageName:     els.name.value.trim(),
            abbreviation:    els.abbreviation.value.trim(),
            unitPrice:       Number(els.unitPrice.value || 0),
            billingUnit:     els.billingUnit.value,
            frequency:       Number(els.frequency.value || 1),
            displayOrder:    Number(els.order.value || 0)
        };
        fetch(url, {
            method: method,
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })
            .then(function (r) {
                return r.text().then(function (t) {
                    const payload = t ? JSON.parse(t) : {};
                    if (!r.ok) throw new Error(payload.error || '저장 실패');
                    return payload;
                });
            })
            .then(function () {
                els.dialog.close();
                load();
            })
            .catch(function (e) { els.dialogMsg.textContent = e.message; });
    }

    function removeItem(item) {
        if (!window.confirm(item.packageName + ' 항목을 삭제하시겠습니까?')) return;
        fetch(API + 'api/treatment-packages/' + encodeURIComponent(item.id), {
            method: 'DELETE',
            headers: { Accept: 'application/json' }
        })
            .then(function (r) {
                if (!r.ok && r.status !== 204) throw new Error('삭제 실패');
            })
            .then(load)
            .catch(function () { setMessage('항목 삭제에 실패했습니다.'); });
    }

    function setMessage(msg) {
        els.message.textContent = msg || '';
    }

    function formatCurrency(value) {
        const n = Number(value || 0);
        return n.toLocaleString('ko-KR') + ' 원';
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
