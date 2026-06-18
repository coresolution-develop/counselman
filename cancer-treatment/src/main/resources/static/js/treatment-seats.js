(function () {
    const API = (window.__ctx || '/');

    const els = {
        body: document.getElementById('seat-body'),
        message: document.getElementById('seat-message'),
        filterRoom: document.getElementById('seat-filter-room'),
        open: document.getElementById('seat-create-open'),
        dialog: document.getElementById('seat-dialog'),
        form: document.getElementById('seat-form'),
        title: document.getElementById('seat-dialog-title'),
        id: document.getElementById('seat-id'),
        room: document.getElementById('seat-room'),
        code: document.getElementById('seat-code'),
        name: document.getElementById('seat-name'),
        displayOrder: document.getElementById('seat-display-order'),
        dialogMessage: document.getElementById('seat-dialog-message'),
        cancel: document.getElementById('seat-cancel')
    };

    let rooms = [];
    let seats = [];

    function init() {
        if (!els.body) return;
        els.open.addEventListener('click', function () { openDialog(); });
        els.cancel.addEventListener('click', closeDialog);
        els.form.addEventListener('submit', saveSeat);
        els.body.addEventListener('click', onTableClick);
        els.filterRoom.addEventListener('change', loadSeats);
        loadRooms();
    }

    function loadRooms() {
        fetch(API + 'api/treatment-rooms', { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (payload) {
                rooms = payload.items || [];
                fillRoomSelect(els.filterRoom, '전체 치료실');
                fillRoomSelect(els.room, '선택');
                loadSeats();
            })
            .catch(function () { els.body.innerHTML = '<tr><td colspan="5" class="empty">치료실 정보를 불러오지 못했습니다.</td></tr>'; });
    }

    function fillRoomSelect(select, placeholder) {
        const current = select.value;
        select.innerHTML = '<option value="">' + escapeHtml(placeholder) + '</option>'
            + rooms.map(function (r) {
                return '<option value="' + escapeHtml(r.id) + '">' + escapeHtml(r.roomName) + '</option>';
            }).join('');
        if (current) select.value = current;
    }

    function loadSeats() {
        const roomId = els.filterRoom.value;
        const url = API + 'api/treatment-seats' + (roomId ? '?roomId=' + encodeURIComponent(roomId) : '');
        fetch(url, { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (payload) { seats = payload.items || []; renderRows(); })
            .catch(function () { els.body.innerHTML = '<tr><td colspan="5" class="empty">자리 정보를 불러오지 못했습니다.</td></tr>'; });
    }

    function renderRows() {
        if (seats.length === 0) {
            els.body.innerHTML = '<tr><td colspan="5" class="empty">등록된 자리가 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = seats.map(function (seat) {
            return '<tr data-id="' + escapeHtml(seat.id) + '">' +
                '<td>' + escapeHtml(seat.treatmentRoomName || '-') + '</td>' +
                '<td class="time-cell">' + escapeHtml(seat.seatCode) + '</td>' +
                '<td>' + escapeHtml(seat.seatName) + '</td>' +
                '<td style="text-align:right">' + escapeHtml(seat.displayOrder || 0) + '</td>' +
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
        const seat = row ? findSeat(row.dataset.id) : null;
        if (!seat) return;
        if (button.dataset.action === 'edit') openDialog(seat);
        if (button.dataset.action === 'delete') deleteSeat(seat);
    }

    function openDialog(seat) {
        els.form.reset();
        fillRoomSelect(els.room, '선택');
        els.id.value = seat ? seat.id : '';
        els.title.textContent = seat ? '자리 수정' : '자리 등록';
        els.room.value = seat ? String(seat.treatmentRoomId) : (els.filterRoom.value || '');
        els.code.value = seat ? seat.seatCode || '' : '';
        els.name.value = seat ? seat.seatName || '' : '';
        els.displayOrder.value = seat ? seat.displayOrder || 0 : 0;
        els.dialogMessage.textContent = '';
        els.dialog.showModal();
        els.code.focus();
    }

    function closeDialog() { els.dialog.close(); }

    function saveSeat(event) {
        event.preventDefault();
        const id = els.id.value;
        const url = id ? API + 'api/treatment-seats/' + id : API + 'api/treatment-seats';
        const method = id ? 'PUT' : 'POST';
        fetch(url, {
            method: method,
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({
                treatmentRoomId: els.room.value ? Number(els.room.value) : null,
                seatCode: els.code.value.trim(),
                seatName: els.name.value.trim(),
                displayOrder: Number(els.displayOrder.value || 0)
            })
        })
            .then(function (response) {
                return response.text().then(function (text) {
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok) throw new Error(payload.error || 'seat-save-failed');
                    return payload;
                });
            })
            .then(function () { closeDialog(); loadSeats(); })
            .catch(function (error) {
                els.dialogMessage.textContent = error.message === 'seat-save-failed' ? '자리를 저장하지 못했습니다.' : error.message;
            });
    }

    function deleteSeat(seat) {
        if (!window.confirm('삭제하시겠습니까?')) return;
        fetch(API + 'api/treatment-seats/' + encodeURIComponent(seat.id), {
            method: 'DELETE',
            headers: { Accept: 'application/json' }
        })
            .then(function (response) {
                if (!response.ok) throw new Error('seat-delete-failed');
                loadSeats();
            })
            .catch(function () { els.message.textContent = '자리를 삭제하지 못했습니다.'; });
    }

    function findSeat(id) {
        return seats.find(function (seat) { return String(seat.id) === String(id); });
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
