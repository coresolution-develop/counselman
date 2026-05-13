(function () {
    const API = (window.__ctx || '/');

    const els = {
        body: document.getElementById('room-body'),
        message: document.getElementById('room-message'),
        open: document.getElementById('room-create-open'),
        dialog: document.getElementById('room-dialog'),
        form: document.getElementById('room-form'),
        title: document.getElementById('room-dialog-title'),
        id: document.getElementById('room-id'),
        managementNo: document.getElementById('room-management-no'),
        roomName: document.getElementById('room-name'),
        treatmentItems: document.getElementById('room-treatment-items'),
        managerName: document.getElementById('room-manager-name'),
        note: document.getElementById('room-note'),
        displayOrder: document.getElementById('room-display-order'),
        dialogMessage: document.getElementById('room-dialog-message'),
        cancel: document.getElementById('room-cancel')
    };

    let rooms = [];

    function init() {
        if (!els.body) return;
        els.open.addEventListener('click', function () { openDialog(); });
        els.cancel.addEventListener('click', closeDialog);
        els.form.addEventListener('submit', saveRoom);
        els.body.addEventListener('click', onTableClick);
        loadRooms();
    }

    function loadRooms() {
        fetch(API + 'api/treatment-rooms', { headers: { Accept: 'application/json' } })
            .then(function (response) {
                if (!response.ok) throw new Error('room-load-failed');
                return response.json();
            })
            .then(function (payload) {
                rooms = payload.items || [];
                renderRows();
            })
            .catch(function () {
                els.body.innerHTML = '<tr><td colspan="6" class="empty">치료실 정보를 불러오지 못했습니다.</td></tr>';
            });
    }

    function renderRows() {
        if (rooms.length === 0) {
            els.body.innerHTML = '<tr><td colspan="6" class="empty">등록된 치료실이 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = rooms.map(function (room) {
            return '<tr data-id="' + escapeHtml(room.id) + '">' +
                '<td class="time-cell">' + escapeHtml(room.managementNo || '-') + '</td>' +
                '<td>' + escapeHtml(room.roomName) + '</td>' +
                '<td>' + renderTreatmentItems(room.treatmentItems || []) + '</td>' +
                '<td>' + escapeHtml(room.managerName || '-') + '</td>' +
                '<td class="memo-cell">' + escapeHtml(room.note || '-') + '</td>' +
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
        els.id.value = room ? room.id : '';
        els.title.textContent = room ? '치료실 수정' : '치료실 등록';
        els.managementNo.value = room ? room.managementNo || '' : '';
        els.roomName.value = room ? room.roomName || '' : '';
        els.treatmentItems.value = room ? (room.treatmentItems || []).join('\n') : '';
        els.managerName.value = room ? room.managerName || '' : '';
        els.note.value = room ? room.note || '' : '';
        els.displayOrder.value = room ? room.displayOrder || 0 : 0;
        els.dialogMessage.textContent = '';
        els.dialog.showModal();
        els.managementNo.focus();
    }

    function closeDialog() {
        els.dialog.close();
    }

    function saveRoom(event) {
        event.preventDefault();
        const id = els.id.value;
        const url = id ? API + 'api/treatment-rooms/' + id : API + 'api/treatment-rooms';
        const method = id ? 'PUT' : 'POST';
        fetch(url, {
            method: method,
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({
                managementNo: els.managementNo.value.trim(),
                roomName: els.roomName.value.trim(),
                treatmentItems: readTreatmentItems(),
                managerName: els.managerName.value.trim(),
                note: els.note.value.trim(),
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
                loadRooms();
            })
            .catch(function (error) {
                els.dialogMessage.textContent = error.message === 'room-save-failed'
                    ? '치료실을 저장하지 못했습니다.'
                    : error.message;
            });
    }

    function deleteRoom(room) {
        if (!window.confirm('삭제하시겠습니까?')) return;
        fetch(API + 'api/treatment-rooms/' + encodeURIComponent(room.id), {
            method: 'DELETE',
            headers: { Accept: 'application/json' }
        })
            .then(function (response) {
                if (!response.ok) throw new Error('room-delete-failed');
                loadRooms();
            })
            .catch(function () {
                els.message.textContent = '치료실을 삭제하지 못했습니다.';
            });
    }

    function findRoom(id) {
        return rooms.find(function (room) {
            return String(room.id) === String(id);
        });
    }

    function readTreatmentItems() {
        return els.treatmentItems.value
            .split(/\r?\n|,/)
            .map(function (value) { return value.trim(); })
            .filter(Boolean);
    }

    function renderTreatmentItems(items) {
        if (!items.length) return '-';
        return '<div class="room-item-list">' + items.map(function (item) {
            return '<span>' + escapeHtml(item) + '</span>';
        }).join('') + '</div>';
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
