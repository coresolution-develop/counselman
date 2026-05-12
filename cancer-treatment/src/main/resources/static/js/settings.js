(function () {
    const categories = {
        'treatment-types': {
            title: '치료 항목',
            fields: { code: false, name: true, detail: true },
            labels: { name: '치료 항목명', detail: '치료실' }
        },
        'treatment-options': {
            title: '치료 옵션',
            fields: { code: true, name: true, detail: false, color: true },
            labels: { code: '옵션 코드', name: '옵션명', color: '표시 색상' }
        },
        'treatment-statuses': {
            title: '치료 상태',
            fields: { code: true, name: true, detail: false },
            labels: { code: '상태 코드', name: '상태명' }
        },
        'time-slots': {
            title: '시간 슬롯',
            fields: { code: true, name: false, detail: false },
            labels: { code: '시간', codeType: 'time' }
        },
        'wards': {
            title: '병동/외래 구분',
            fields: { code: true, name: true, detail: false },
            labels: { code: '병동 코드', name: '병동명' }
        }
    };

    const els = {
        grid: document.getElementById('settings-grid'),
        message: document.getElementById('settings-message'),
        dialog: document.getElementById('settings-dialog'),
        form: document.getElementById('settings-form'),
        title: document.getElementById('settings-dialog-title'),
        id: document.getElementById('settings-id'),
        category: document.getElementById('settings-category'),
        code: document.getElementById('settings-code'),
        name: document.getElementById('settings-name'),
        detail: document.getElementById('settings-detail'),
        color: document.getElementById('settings-color'),
        order: document.getElementById('settings-display-order'),
        dialogMessage: document.getElementById('settings-dialog-message'),
        cancel: document.getElementById('settings-cancel')
    };

    let state = {};

    function init() {
        if (!els.grid) return;
        els.grid.addEventListener('click', onGridClick);
        els.form.addEventListener('submit', saveItem);
        els.cancel.addEventListener('click', closeDialog);
        loadSettings();
    }

    function loadSettings() {
        fetch('/api/settings', { headers: { Accept: 'application/json' } })
            .then(function (response) {
                if (!response.ok) throw new Error('settings-load-failed');
                return response.json();
            })
            .then(function (payload) {
                state = {
                    'treatment-types': payload.treatmentTypes || [],
                    'treatment-options': payload.treatmentOptions || [],
                    'treatment-statuses': payload.treatmentStatuses || [],
                    'time-slots': payload.timeSlots || [],
                    wards: payload.wards || []
                };
                renderAll();
            })
            .catch(function () {
                setMessage('설정 목록을 불러오지 못했습니다.');
            });
    }

    function renderAll() {
        Object.keys(categories).forEach(function (category) {
            const card = els.grid.querySelector('[data-category="' + category + '"]');
            const list = card ? card.querySelector('.settings-list') : null;
            if (!list) return;
            const items = state[category] || [];
            if (items.length === 0) {
                list.innerHTML = '<p class="settings-empty">등록된 설정이 없습니다.</p>';
                return;
            }
            list.innerHTML = items.map(function (item) {
                return '<div class="settings-row" data-id="' + escapeHtml(item.id) + '">' +
                    '<div><strong>' + renderColorSwatch(category, item) + escapeHtml(item.name) + '</strong>' +
                    '<span>' + renderMeta(category, item) + '</span></div>' +
                    '<div class="settings-row-actions">' +
                    '<button type="button" class="secondary-button" data-action="edit">수정</button>' +
                    '<button type="button" class="secondary-button danger-soft-button" data-action="delete">삭제</button>' +
                    '</div></div>';
            }).join('');
        });
    }

    function renderMeta(category, item) {
        const parts = [];
        if (category !== 'treatment-types' && item.code) parts.push(item.code);
        if (item.detail) parts.push(item.detail);
        parts.push('순서 ' + (item.displayOrder || 0));
        return parts.map(escapeHtml).join(' · ');
    }

    function onGridClick(event) {
        const button = event.target.closest('button');
        if (!button) return;
        const card = event.target.closest('.settings-card');
        if (!card) return;
        const category = card.dataset.category;
        const action = button.dataset.action;
        const row = event.target.closest('.settings-row');
        const item = row ? findItem(category, row.dataset.id) : null;
        if (action === 'add') openDialog(category);
        if (action === 'edit' && item) openDialog(category, item);
        if (action === 'delete' && item) deleteItem(category, item);
    }

    function openDialog(category, item) {
        const config = categories[category];
        els.form.reset();
        els.id.value = item ? item.id : '';
        els.category.value = category;
        els.title.textContent = config.title + (item ? ' 수정' : ' 추가');
        els.code.value = item ? item.code || '' : '';
        els.name.value = item ? item.name || '' : '';
        els.detail.value = item ? item.detail || '' : '';
        els.color.value = item && item.color ? item.color : '#1a74bf';
        els.order.value = item ? item.displayOrder || 0 : 0;
        els.dialogMessage.textContent = '';
        configureFields(config);
        els.dialog.showModal();
    }

    function configureFields(config) {
        configureField('code', config);
        configureField('name', config);
        configureField('detail', config);
        configureField('color', config);
    }

    function configureField(key, config) {
        const label = els.form.querySelector('[data-field="' + key + '"]');
        const input = els[key];
        label.hidden = !config.fields[key];
        input.required = !!config.fields[key];
        input.type = key === 'code' && config.labels.codeType === 'time' ? 'time' : 'text';
        if (key === 'color') input.type = 'color';
        const text = document.getElementById('settings-' + key + '-label');
        if (text && config.labels[key]) text.textContent = config.labels[key];
    }

    function closeDialog() {
        els.dialog.close();
    }

    function saveItem(event) {
        event.preventDefault();
        const category = els.category.value;
        const id = els.id.value;
        const method = id ? 'PUT' : 'POST';
        const url = id ? '/api/settings/' + category + '/' + id : '/api/settings/' + category;
        fetch(url, {
            method: method,
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({
                code: els.code.value.trim(),
                name: els.name.value.trim(),
                detail: els.detail.value.trim(),
                color: els.color.value,
                displayOrder: Number(els.order.value || 0)
            })
        })
            .then(function (response) {
                return response.text().then(function (text) {
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok) throw new Error(payload.error || 'settings-save-failed');
                    return payload;
                });
            })
            .then(function () {
                closeDialog();
                loadSettings();
            })
            .catch(function (error) {
                els.dialogMessage.textContent = error.message === 'settings-save-failed'
                    ? '설정을 저장하지 못했습니다.'
                    : error.message;
            });
    }

    function deleteItem(category, item) {
        if (!window.confirm('삭제하시겠습니까?')) return;
        fetch('/api/settings/' + category + '/' + item.id, {
            method: 'DELETE',
            headers: { Accept: 'application/json' }
        })
            .then(function (response) {
                if (!response.ok) throw new Error('settings-delete-failed');
                loadSettings();
            })
            .catch(function () {
                setMessage('설정을 삭제하지 못했습니다.');
            });
    }

    function findItem(category, id) {
        return (state[category] || []).find(function (item) {
            return String(item.id) === String(id);
        });
    }

    function renderColorSwatch(category, item) {
        if (category !== 'treatment-options' || !item.color) return '';
        return '<i class="settings-color-swatch" style="background:' + escapeHtml(item.color) + '"></i>';
    }

    function setMessage(message) {
        els.message.textContent = message;
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
