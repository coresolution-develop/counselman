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
    let packagesByRoom = {};

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
            fetch(API + 'api/treatment-rooms',    { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] }),
            fetch(API + 'api/treatment-packages', { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] })
        ])
            .then(function (results) {
                rooms = (results[0] && results[0].items) || [];
                packagesByRoom = {};
                ((results[1] && results[1].items) || []).forEach(function (p) {
                    const key = String(p.treatmentRoomId);
                    if (!packagesByRoom[key]) packagesByRoom[key] = [];
                    packagesByRoom[key].push(p);
                });
                renderRows();
            })
            .catch(function () {
                els.body.innerHTML = '<tr><td colspan="7" class="empty">치료실 정보를 불러오지 못했습니다.</td></tr>';
            });
    }

    function loadRooms() { loadAll(); }

    function renderRows() {
        if (rooms.length === 0) {
            els.body.innerHTML = '<tr><td colspan="7" class="empty">등록된 치료실이 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = rooms.map(function (room) {
            const packages = packagesByRoom[String(room.id)] || [];
            return '<tr data-id="' + escapeHtml(room.id) + '">' +
                '<td class="time-cell">' + escapeHtml(room.managementNo || '-') + '</td>' +
                '<td>' + escapeHtml(room.roomName) + '</td>' +
                '<td>' + renderTreatmentItems(room.treatmentItems || []) + '</td>' +
                '<td>' + escapeHtml(room.managerName || '-') + '</td>' +
                '<td class="memo-cell">' + escapeHtml(room.note || '-') + '</td>' +
                '<td style="text-align:right">' + renderPackagesLink(room.id, packages) + '</td>' +
                '<td><div class="settings-row-actions">' +
                '<button type="button" class="secondary-button" data-action="edit">수정</button>' +
                '<button type="button" class="secondary-button danger-soft-button" data-action="delete">삭제</button>' +
                '</div></td>' +
                '</tr>' +
                (packages.length ? renderPackagesRow(room.id, packages) : '');
        }).join('');
    }

    function renderPackagesLink(roomId, packages) {
        const count = packages.length;
        const total = packages.reduce(function (sum, p) { return sum + (Number(p.unitPrice || 0) * Number(p.frequency || 1)); }, 0);
        const url = API + 'treatment-packages?roomId=' + encodeURIComponent(roomId);
        if (!count) {
            return '<a href="' + url + '" style="color:#5c636f">0개</a>';
        }
        return '<a href="' + url + '" style="color:#1f3b69; font-weight:600">' + count + '개</a>' +
            '<div style="font-size:11px;color:#5c636f">단가합 ' + total.toLocaleString('ko-KR') + '원</div>';
    }

    function renderPackagesRow(roomId, packages) {
        const cells = packages.map(function (p) {
            const unit = p.billingUnit === 'DAY' ? '일' : '주';
            return '<span class="package-pill">' +
                escapeHtml(p.categoryName) + ' · ' + escapeHtml(p.packageName) +
                ' <em>(' + Number(p.unitPrice).toLocaleString('ko-KR') + '원 / ' + unit + ' ' + escapeHtml(p.frequency) + '회)</em>' +
                '</span>';
        }).join('');
        return '<tr class="room-packages-row"><td colspan="7" style="background:#fbfcfe; padding:8px 14px;">' +
            '<div class="room-packages-list">' + cells + '</div>' +
            '</td></tr>';
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
