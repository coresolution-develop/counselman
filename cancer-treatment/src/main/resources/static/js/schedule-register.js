(function () {
    const API = (window.__ctx || '/');
    const MAX_COUNT = 200;

    const els = {
        roomSelect: document.getElementById('reg-room-select'),
        open: document.getElementById('reg-open'),
        message: document.getElementById('reg-message'),
        calPrev: document.getElementById('cal-prev'),
        calNext: document.getElementById('cal-next'),
        calToday: document.getElementById('cal-today'),
        calTitle: document.getElementById('cal-title'),
        calGrid: document.getElementById('cal-grid'),
        dayDetail: document.getElementById('day-detail'),
        ddTitle: document.getElementById('dd-title'),
        ddSub: document.getElementById('dd-sub'),
        ddUnassigned: document.getElementById('dd-unassigned'),
        ddBody: document.getElementById('dd-body'),
        ddRegister: document.getElementById('dd-register'),
        ddPrint: document.getElementById('dd-print'),
        ddPrintMeta: document.getElementById('dd-print-meta'),
        dialog: document.getElementById('reg-dialog'),
        form: document.getElementById('reg-form'),
        roomName: document.getElementById('reg-room-name'),
        patient: document.getElementById('reg-patient'),
        autofill: document.getElementById('reg-autofill'),
        autoInfo: document.getElementById('reg-auto-info'),
        autoNote: document.getElementById('reg-auto-note'),
        autoAmount: document.getElementById('reg-auto-amount'),
        treatmentName: document.getElementById('reg-treatment-name'),
        seat: document.getElementById('reg-seat'),
        startDate: document.getElementById('reg-start-date'),
        startTime: document.getElementById('reg-start-time'),
        repeatArea: document.getElementById('reg-repeat-area'),
        weekdays: document.getElementById('reg-weekdays'),
        count: document.getElementById('reg-count'),
        countHint: document.getElementById('reg-count-hint'),
        dialogMessage: document.getElementById('reg-dialog-message'),
        warning: document.getElementById('reg-warning'),
        cancel: document.getElementById('reg-cancel'),
        submit: document.getElementById('reg-submit')
    };

    let state = { rooms: [], patients: [], packages: [], seats: [], cursor: new Date(), selectedRoomId: '', selectedDate: '' };

    function init() {
        if (!els.calGrid) return;
        state.cursor = startOfMonth(new Date());
        els.roomSelect.addEventListener('change', onRoomChange);
        els.open.addEventListener('click', function () { openDialog(state.selectedDate || todayIso()); });
        els.calPrev.addEventListener('click', function () { state.cursor = addMonths(state.cursor, -1); renderCalendar(); });
        els.calNext.addEventListener('click', function () { state.cursor = addMonths(state.cursor, 1); renderCalendar(); });
        els.calToday.addEventListener('click', function () { state.cursor = startOfMonth(new Date()); renderCalendar(); });
        els.calGrid.addEventListener('click', onDayClick);
        els.ddRegister.addEventListener('click', function () { openDialog(state.selectedDate || todayIso()); });
        els.ddPrint.addEventListener('click', function () { window.print(); });
        els.patient.addEventListener('change', onPatientChange);
        els.cancel.addEventListener('click', closeDialog);
        els.form.addEventListener('submit', onSubmit);
        els.form.querySelectorAll('input[name="reg-mode"]').forEach(function (r) {
            r.addEventListener('change', applyMode);
        });
        loadData();
        renderCalendar();
    }

    function loadData() {
        Promise.all([
            fetch(API + 'api/treatment-rooms',    { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] }),
            fetch(API + 'api/patients',           { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] }),
            fetch(API + 'api/treatment-packages', { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] })
        ]).then(function (results) {
            state.rooms = (results[0] && results[0].items) || [];
            state.patients = (results[1] && results[1].items) || [];
            state.packages = (results[2] && results[2].items) || [];
            els.roomSelect.innerHTML = '<option value="">치료실 선택</option>' + state.rooms.map(function (r) {
                return '<option value="' + escapeHtml(r.id) + '">' + escapeHtml(r.roomName) + '</option>';
            }).join('');
            els.patient.innerHTML = '<option value="">환자 선택</option>' + state.patients.map(function (p) {
                return '<option value="' + escapeHtml(p.id) + '">' + escapeHtml(p.name) + (p.room ? ' (' + escapeHtml(p.room) + ')' : '') + '</option>';
            }).join('');
        }).catch(function () { els.message.textContent = '데이터를 불러오지 못했습니다.'; });
    }

    function onRoomChange() {
        state.selectedRoomId = els.roomSelect.value;
        els.open.disabled = !state.selectedRoomId;
        state.seats = [];
        state.selectedDate = '';
        els.dayDetail.hidden = true;
        if (!state.selectedRoomId) { renderCalendar(); return; }
        fetch(API + 'api/treatment-seats?roomId=' + encodeURIComponent(state.selectedRoomId), { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (payload) { state.seats = payload.items || []; })
            .catch(function () { state.seats = []; });
        renderCalendar();
    }

    // ── Calendar (월간, 월~일) ──
    function renderCalendar() {
        const first = startOfMonth(state.cursor);
        els.calTitle.textContent = first.getFullYear() + '년 ' + (first.getMonth() + 1) + '월';
        const gridStart = mondayOf(first);
        let html = '<div class="reg-cal-head">' + ['월', '화', '수', '목', '금', '토', '일'].map(function (d) {
            return '<span>' + d + '</span>';
        }).join('') + '</div>';
        let cells = '';
        for (let i = 0; i < 42; i++) {
            const date = addDays(gridStart, i);
            const iso = toIso(date);
            const outside = date.getMonth() !== first.getMonth();
            const clickable = !!state.selectedRoomId;
            cells += '<button type="button" class="reg-cal-cell' + (outside ? ' is-outside' : '') +
                (clickable ? '' : ' is-disabled') + (iso === todayIso() ? ' is-today' : '') +
                '" data-date="' + iso + '"' + (clickable ? '' : ' disabled') + '>' +
                '<span class="reg-cal-num">' + date.getDate() + '</span></button>';
        }
        els.calGrid.innerHTML = html + '<div class="reg-cal-body">' + cells + '</div>';
    }

    function onDayClick(event) {
        const cell = event.target.closest('button[data-date]');
        if (!cell || cell.disabled) return;
        state.selectedDate = cell.dataset.date;
        els.calGrid.querySelectorAll('.reg-cal-cell.is-selected').forEach(function (c) { c.classList.remove('is-selected'); });
        cell.classList.add('is-selected');
        loadDayDetail(cell.dataset.date);
    }

    // ── Day detail: 시간 > 자리 > 환자 > 옵션 > 상태 (세로 중첩) ──
    function loadDayDetail(dateIso) {
        if (!state.selectedRoomId) return;
        const url = API + 'api/treatment-schedules?roomId=' + encodeURIComponent(state.selectedRoomId) +
            '&date=' + encodeURIComponent(dateIso);
        fetch(url, { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (payload) { renderDayDetail(dateIso, payload.items || []); })
            .catch(function () { els.ddBody.innerHTML = '<p class="empty">조회 실패</p>'; els.dayDetail.hidden = false; });
    }

    function renderDayDetail(dateIso, items) {
        const room = state.rooms.find(function (r) { return String(r.id) === String(state.selectedRoomId); });
        const roomName = room ? room.roomName : '-';
        els.ddTitle.textContent = dateIso;
        els.ddSub.textContent = roomName + ' · ' + items.length + '건';
        const unassigned = items.filter(function (it) { return it.seatId == null; }).length;
        els.ddUnassigned.textContent = unassigned ? ('자리 미지정 ' + unassigned + '건') : '';
        els.ddPrintMeta.textContent = '치료실: ' + roomName + ' · 날짜: ' + dateIso + ' · 출력일시: ' + stamp();

        if (items.length === 0) {
            els.ddBody.innerHTML = '<p class="empty">이 날짜에 등록된 일정이 없습니다.</p>';
            els.dayDetail.hidden = false;
            return;
        }
        // 시간 그룹 → 자리 그룹(미지정 마지막) → 환자
        const byTime = groupBy(items, function (it) { return it.startTime || '--:--'; });
        const times = Object.keys(byTime).sort();
        let html = '';
        times.forEach(function (time) {
            html += '<div class="dd-time"><div class="dd-time-label">' + escapeHtml(time) + '</div><div class="dd-seats">';
            const bySeat = groupBy(byTime[time], function (it) { return it.seatName || ' 미지정'; });
            const seatKeys = Object.keys(bySeat).sort(function (a, b) {
                // 미지정(  접두)을 항상 마지막으로
                return a.localeCompare(b);
            });
            seatKeys.forEach(function (seatKey) {
                const isUnassigned = seatKey === ' 미지정';
                const seatLabel = isUnassigned ? '자리 미지정' : seatKey;
                html += '<div class="dd-seat' + (isUnassigned ? ' is-unassigned' : '') + '">' +
                    '<div class="dd-seat-label">' + escapeHtml(seatLabel) + '</div><div class="dd-patients">';
                bySeat[seatKey].forEach(function (it) {
                    const opt = it.treatmentOption ? '<span class="dd-opt">▶' + escapeHtml(it.treatmentOption) + '</span>' : '';
                    html += '<div class="dd-patient">' +
                        '<span class="dd-name">' + escapeHtml(it.patientName) + '</span>' +
                        '<span class="dd-treat">' + escapeHtml(it.treatmentName || '-') + '</span>' +
                        opt +
                        '<em class="sched-status ' + statusClass(it.status) + '">' + escapeHtml(it.status) + '</em>' +
                        '</div>';
                });
                html += '</div></div>';
            });
            html += '</div></div>';
        });
        els.ddBody.innerHTML = html;
        els.dayDetail.hidden = false;
    }

    function statusClass(status) {
        if (status === '치료완료') return 'is-completed';
        if (status === '예약취소') return 'is-canceled';
        return 'is-reserved';
    }

    function groupBy(arr, keyFn) {
        const map = {};
        arr.forEach(function (item) {
            const k = keyFn(item);
            if (!map[k]) map[k] = [];
            map[k].push(item);
        });
        return map;
    }

    function stamp() {
        const n = new Date();
        return n.getFullYear() + '-' + pad(n.getMonth() + 1) + '-' + pad(n.getDate()) + ' ' + pad(n.getHours()) + ':' + pad(n.getMinutes());
    }

    // ── Dialog ──
    function openDialog(dateIso) {
        if (!state.selectedRoomId) return;
        els.form.reset();
        const room = state.rooms.find(function (r) { return String(r.id) === String(state.selectedRoomId); });
        els.roomName.textContent = room ? room.roomName : '-';
        els.seat.innerHTML = '<option value="">자리 미지정</option>' + state.seats.map(function (s) {
            return '<option value="' + escapeHtml(s.id) + '">' + escapeHtml(s.seatName) + '</option>';
        }).join('');
        els.startDate.value = dateIso || todayIso();
        els.startTime.value = '10:00';
        els.autofill.hidden = true;
        els.treatmentName.value = '';
        els.warning.hidden = true;
        els.warning.textContent = '';
        els.dialogMessage.textContent = '';
        // 모드 초기화: 한 번만
        els.form.querySelector('input[name="reg-mode"][value="once"]').checked = true;
        applyMode();
        els.dialog.showModal();
    }

    function closeDialog() { els.dialog.close(); }

    function afterRegister() {
        renderCalendar();
        if (state.selectedDate) {
            // 선택 셀 강조 복원 + day-detail 새로고침
            const cell = els.calGrid.querySelector('button[data-date="' + state.selectedDate + '"]');
            if (cell) cell.classList.add('is-selected');
            loadDayDetail(state.selectedDate);
        }
    }

    function onPatientChange() {
        const patient = state.patients.find(function (p) { return String(p.id) === els.patient.value; });
        if (!patient) { els.autofill.hidden = true; els.treatmentName.value = ''; els.countHint.textContent = ''; return; }
        // 자동조회: 치료정보 / 참고 / 금액
        const amount = window.CtPatientCost.computePatientTotal(patient, state.packages);
        els.autoInfo.textContent = patient.treatmentInfo || '-';
        els.autoNote.textContent = patient.note || '-';
        els.autoAmount.textContent = amount.toLocaleString('ko-KR') + ' 원';
        els.autofill.hidden = false;
        // 치료종류: 치료정보 요약으로 자동 채움(수정 가능)
        els.treatmentName.value = summarize(patient.treatmentInfo) || '치료';
        // count 기본값 = 처방 총 회차(없으면 빈칸 + 안내)
        const sessions = window.CtPatientCost.prescribedSessionCount(patient, state.packages);
        if (sessions != null) {
            els.count.value = Math.min(sessions, MAX_COUNT);
            els.countHint.textContent = '처방 총 회차 ' + sessions + '회 자동 제시 (수정 가능)';
        } else {
            els.count.value = '';
            els.countHint.textContent = '처방 정보가 없어 자동 계산 불가 — 직접 입력하세요.';
        }
    }

    function applyMode() {
        const repeat = isRepeat();
        els.repeatArea.hidden = !repeat;
        if (!repeat) {
            // 한 번만: 요일/횟수 입력값 초기화 → 잘못 전송 방지
            els.weekdays.querySelectorAll('input[type="checkbox"]').forEach(function (cb) { cb.checked = false; });
            els.count.value = '';
        } else {
            // 반복 진입 시 환자 기준 기본값 다시 채움
            onPatientChange();
        }
    }

    function isRepeat() {
        const checked = els.form.querySelector('input[name="reg-mode"]:checked');
        return checked && checked.value === 'repeat';
    }

    function weekdayMask() {
        let mask = 0;
        els.weekdays.querySelectorAll('input[type="checkbox"]:checked').forEach(function (cb) {
            mask |= (1 << Number(cb.dataset.bit));
        });
        return mask;
    }

    function onSubmit(event) {
        event.preventDefault();
        els.dialogMessage.textContent = '';
        els.warning.hidden = true;

        const patient = state.patients.find(function (p) { return String(p.id) === els.patient.value; });
        if (!patient) { els.dialogMessage.textContent = '환자를 선택해주세요.'; return; }
        if (!els.treatmentName.value.trim()) { els.dialogMessage.textContent = '치료종류를 입력해주세요.'; return; }
        if (!els.startDate.value) { els.dialogMessage.textContent = '시작일을 입력해주세요.'; return; }
        if (!els.startTime.value) { els.dialogMessage.textContent = '시작시간을 입력해주세요.'; return; }

        const repeat = isRepeat();
        let mask = 0;
        let count = null;
        if (repeat) {
            // 화면 선제 검증 — 서버(mask=0/count<=0/count>200) 거부 조건과 일치
            mask = weekdayMask();
            if (mask === 0) { els.dialogMessage.textContent = '반복 요일을 한 개 이상 선택해주세요.'; return; }
            count = Number(els.count.value);
            if (!count || count <= 0) { els.dialogMessage.textContent = '반복 횟수를 1 이상으로 입력해주세요.'; return; }
            if (count > MAX_COUNT) { els.dialogMessage.textContent = '반복 횟수는 ' + MAX_COUNT + '회를 초과할 수 없습니다.'; return; }
        }

        const payload = {
            patientId: Number(patient.id),
            patientName: patient.name,
            ward: patient.ward || '',
            treatmentRoomId: Number(state.selectedRoomId),
            seatId: els.seat.value ? Number(els.seat.value) : null,
            treatmentName: els.treatmentName.value.trim(),
            treatmentInfo: patient.treatmentInfo || '',
            note: patient.note || '',
            startDate: els.startDate.value,
            startTime: els.startTime.value,
            repeat: repeat,
            weekdayMask: repeat ? mask : null,
            occurrenceCount: repeat ? count : null,
            prescriptionWeeks: patient.prescriptionWeeks || null
        };

        els.submit.disabled = true;
        fetch(API + 'api/treatment-schedules/recurring', {
            method: 'POST',
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        }).then(function (response) {
            return response.text().then(function (text) {
                const data = text ? JSON.parse(text) : {};
                if (!response.ok) throw new Error(data.error || '등록에 실패했습니다.');
                return data;
            });
        }).then(function (result) {
            // warning은 에러가 아님 — 노랑 톤으로 안내하되 등록은 완료 처리
            if (result.warning) {
                els.warning.textContent = '⚠ ' + result.warning;
                els.warning.hidden = false;
                els.message.textContent = result.createdCount + '건 등록 완료 (주의 안내 있음).';
                setTimeout(function () { closeDialog(); afterRegister(); }, 2500);
            } else {
                els.message.textContent = result.createdCount + '건 등록 완료.';
                closeDialog();
                afterRegister();
            }
        }).catch(function (error) {
            els.dialogMessage.textContent = error.message;
        }).finally(function () {
            els.submit.disabled = false;
        });
    }

    // ── helpers ──
    function summarize(info) {
        if (!info) return '';
        return String(info).split(/[,+/]/).map(function (s) { return s.trim(); }).filter(Boolean).slice(0, 2).join('+').slice(0, 100);
    }
    function startOfMonth(d) { return new Date(d.getFullYear(), d.getMonth(), 1); }
    function mondayOf(d) { const day = d.getDay(); return addDays(d, day === 0 ? -6 : 1 - day); }
    function addDays(d, n) { const x = new Date(d); x.setDate(x.getDate() + n); return new Date(x.getFullYear(), x.getMonth(), x.getDate()); }
    function addMonths(d, n) { return new Date(d.getFullYear(), d.getMonth() + n, 1); }
    function toIso(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
    function todayIso() { return toIso(new Date()); }
    function pad(n) { return String(n).padStart(2, '0'); }
    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    document.addEventListener('DOMContentLoaded', init);
})();
