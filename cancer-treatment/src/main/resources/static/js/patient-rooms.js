(function () {
    const API = (window.__ctx || '/');
    const ADMISSION_LABELS = { INPATIENT: '입원(병동)', OUTPATIENT: '외래' };

    const els = {
        body: document.getElementById('room-body'),
        message: document.getElementById('room-message'),
        open: document.getElementById('room-create-open'),
        dialog: document.getElementById('room-dialog'),
        form: document.getElementById('room-form'),
        title: document.getElementById('room-dialog-title'),
        id: document.getElementById('room-id'),
        code: document.getElementById('room-code'),
        roomName: document.getElementById('room-name'),
        ward: document.getElementById('room-ward'),
        displayOrder: document.getElementById('room-display-order'),
        dialogMessage: document.getElementById('room-dialog-message'),
        cancel: document.getElementById('room-cancel')
    };

    let rooms = [];
    let wards = [];

    function init() {
        if (!els.body) return;
        els.open.addEventListener('click', function () { openDialog(); });
        els.cancel.addEventListener('click', closeDialog);
        els.form.addEventListener('submit', saveRoom);
        els.body.addEventListener('click', onTableClick);
        loadAll();
    }

    function loadAll() {
        Promise.all([
            fetch(API + 'api/patient-rooms', { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] }),
            fetch(API + 'api/settings',      { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { wards: [] })
        ])
            .then(function (results) {
                rooms = (results[0] && results[0].items) || [];
                wards = (results[1] && results[1].wards) || [];
                fillWardSelect();
                renderRows();
            })
            .catch(function () {
                els.body.innerHTML = '<tr><td colspan="6" class="empty">병실 정보를 불러오지 못했습니다.</td></tr>';
            });
    }

    function fillWardSelect() {
        const current = els.ward.value;
        els.ward.innerHTML = '<option value="">선택 안함</option>'
            + wards.map(function (w) {
                return '<option value="' + escapeHtml(w.id) + '">' + escapeHtml(w.name)
                    + ' (' + (ADMISSION_LABELS[w.detail] || ADMISSION_LABELS.INPATIENT) + ')</option>';
            }).join('');
        if (current) els.ward.value = current;
    }

    function renderRows() {
        if (rooms.length === 0) {
            els.body.innerHTML = '<tr><td colspan="6" class="empty">등록된 병실이 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = rooms.map(function (room) {
            return '<tr data-id="' + escapeHtml(room.id) + '">' +
                '<td class="time-cell">' + escapeHtml(room.roomCode) + '</td>' +
                '<td>' + escapeHtml(room.roomName) + '</td>' +
                '<td>' + escapeHtml(room.wardName || '-') + '</td>' +
                '<td>' + (room.admissionType ? escapeHtml(ADMISSION_LABELS[room.admissionType] || room.admissionType) : '-') + '</td>' +
                '<td style="text-align:right">' + escapeHtml(room.displayOrder || 0) + '</td>' +
                '<td><div class="settings-row-actions">' +
                '<button type="button" class="secondary-button" data-action="edit">수정</button>' +
                '<button type="button" class="secondary-button danger-soft-button" data-action="delete">삭제</button>' +
                '</div></td>' +
                '</tr>';
        }).join('');
    }

    function onTableClick(event) {
        const button = event.target.closest('button');
        if (!button) return;
        const row = event.target.closest('tr[data-id]');
        const room = row ? findRoom(row.dataset.id) : null;
        if (!room) return;
        if (button.dataset.action === 'edit') openDialog(room);
        if (button.dataset.action === 'delete') deleteRoom(room);
    }

    function openDialog(room) {
        els.form.reset();
        fillWardSelect();
        els.id.value = room ? room.id : '';
        els.title.textContent = room ? '병실 수정' : '병실 등록';
        els.code.value = room ? room.roomCode || '' : '';
        els.roomName.value = room ? room.roomName || '' : '';
        els.ward.value = room && room.wardId != null ? String(room.wardId) : '';
        els.displayOrder.value = room ? room.displayOrder || 0 : 0;
        els.dialogMessage.textContent = '';
        els.dialog.showModal();
        els.code.focus();
    }

    function closeDialog() {
        els.dialog.close();
    }

    function saveRoom(event) {
        event.preventDefault();
        const id = els.id.value;
        const url = id ? API + 'api/patient-rooms/' + id : API + 'api/patient-rooms';
        const method = id ? 'PUT' : 'POST';
        fetch(url, {
            method: method,
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({
                roomCode: els.code.value.trim(),
                roomName: els.roomName.value.trim(),
                wardId: els.ward.value ? Number(els.ward.value) : null,
                displayOrder: Number(els.displayOrder.value || 0)
            })
        })
            .then(function (response) {
                return response.text().then(function (text) {
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok) throw new Error(payload.error || 'room-save-failed');
                    return payload;
                });
            })
            .then(function () {
                closeDialog();
                loadAll();
            })
            .catch(function (error) {
                els.dialogMessage.textContent = error.message === 'room-save-failed'
                    ? '병실을 저장하지 못했습니다.'
                    : error.message;
            });
    }

    function deleteRoom(room) {
        if (!window.confirm('삭제하시겠습니까?')) return;
        fetch(API + 'api/patient-rooms/' + encodeURIComponent(room.id), {
            method: 'DELETE',
            headers: { Accept: 'application/json' }
        })
            .then(function (response) {
                if (!response.ok) throw new Error('room-delete-failed');
                loadAll();
            })
            .catch(function () {
                els.message.textContent = '병실을 삭제하지 못했습니다.';
            });
    }

    function findRoom(id) {
        return rooms.find(function (room) {
            return String(room.id) === String(id);
        });
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
