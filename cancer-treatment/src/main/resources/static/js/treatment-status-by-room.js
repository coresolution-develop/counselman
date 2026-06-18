(function () {
    const API = (window.__ctx || '/');
    const ADMISSION_LABELS = { INPATIENT: '입원', OUTPATIENT: '외래' };
    const FILTER_LABELS = { ALL: '전체', INPATIENT: '입원', OUTPATIENT: '외래' };

    const els = {
        rooms: document.getElementById('status-rooms'),
        message: document.getElementById('status-message'),
        filter: document.getElementById('status-filter'),
        print: document.getElementById('status-print'),
        sumRooms: document.getElementById('sum-rooms'),
        sumPatients: document.getElementById('sum-patients'),
        sumAmount: document.getElementById('sum-amount'),
        printMeta: document.getElementById('print-meta'),
        diagBadge: document.getElementById('diag-badge'),
        diagBody: document.getElementById('diag-body')
    };

    let state = { rooms: [], patients: [], packages: [], filter: 'ALL' };
    let diagnostics = { unmatched: [], nameMatched: [], conflicts: [] };

    function init() {
        if (!els.rooms) return;
        els.filter.addEventListener('click', onFilterClick);
        els.print.addEventListener('click', function () { window.print(); });
        loadAll();
    }

    function loadAll() {
        Promise.all([
            fetch(API + 'api/patient-rooms',      { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] }),
            fetch(API + 'api/patients',           { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] }),
            fetch(API + 'api/treatment-packages', { headers: { Accept: 'application/json' } }).then(r => r.ok ? r.json() : { items: [] })
        ])
            .then(function (results) {
                state.rooms = (results[0] && results[0].items) || [];
                state.patients = (results[1] && results[1].items) || [];
                state.packages = (results[2] && results[2].items) || [];
                assignPatients();
                render();
            })
            .catch(function () {
                els.rooms.innerHTML = '<p class="empty">치료현황 데이터를 불러오지 못했습니다.</p>';
            });
    }

    // patient.room 문자열을 room_code 우선, 없으면 room_name으로 매칭한다(1단계 문자열 매칭).
    function assignPatients() {
        const byCode = new Map();
        const byName = new Map();
        state.rooms.forEach(function (room) {
            if (room.roomCode && !byCode.has(room.roomCode)) byCode.set(room.roomCode, room);
            if (room.roomName && !byName.has(room.roomName)) byName.set(room.roomName, room);
            room._patients = [];
        });

        const unassigned = [];
        diagnostics = { unmatched: [], nameMatched: [], conflicts: [] };

        state.patients.forEach(function (patient) {
            const key = String(patient.room || '').trim();
            if (!key) { unassigned.push(patient); diagnostics.unmatched.push({ patient: patient.name, room: '(없음)' }); return; }
            const codeMatch = byCode.get(key);
            const nameMatch = byName.get(key);
            if (codeMatch) {
                codeMatch._patients.push(patient);
                if (nameMatch && nameMatch.id !== codeMatch.id) {
                    diagnostics.conflicts.push({ patient: patient.name, room: key, code: codeMatch.roomName, name: nameMatch.roomName });
                }
            } else if (nameMatch) {
                nameMatch._patients.push(patient);
                diagnostics.nameMatched.push({ patient: patient.name, room: key });
            } else {
                unassigned.push(patient);
                diagnostics.unmatched.push({ patient: patient.name, room: key });
            }
        });
        state._unassigned = unassigned;
    }

    function visibleRooms() {
        if (state.filter === 'ALL') return state.rooms.slice();
        return state.rooms.filter(function (room) { return room.admissionType === state.filter; });
    }

    function render() {
        const rooms = visibleRooms().sort(function (a, b) {
            return (a.displayOrder || 0) - (b.displayOrder || 0) || String(a.roomCode).localeCompare(String(b.roomCode));
        });
        const showUnassigned = state.filter === 'ALL' && state._unassigned.length > 0;

        let html = rooms.map(renderRoom).join('');
        if (showUnassigned) {
            html += renderUnassigned(state._unassigned);
        }
        els.rooms.innerHTML = html || '<p class="empty">표시할 병실이 없습니다. 병실설정에서 병실을 먼저 등록하세요.</p>';

        renderSummary(rooms, showUnassigned);
        renderDiagnostics();
        renderPrintMeta();
    }

    function renderRoom(room) {
        const patients = room._patients || [];
        const subtotal = patients.reduce(function (s, p) { return s + window.CtPatientCost.computePatientTotal(p, state.packages); }, 0);
        const admission = room.admissionType ? (ADMISSION_LABELS[room.admissionType] || room.admissionType) : '-';
        return '<section class="status-room-card">' +
            '<div class="status-room-head">' +
            '<strong>' + escapeHtml(room.roomName) + '</strong>' +
            '<span class="status-room-meta">' + escapeHtml(room.wardName || '병동 미지정') + ' · ' + admission +
            ' · ' + patients.length + '명 · 소계 ' + subtotal.toLocaleString('ko-KR') + '원</span>' +
            '</div>' +
            (patients.length ? renderPatientTable(patients) : '<p class="status-room-empty">배정 환자 없음</p>') +
            '</section>';
    }

    function renderUnassigned(patients) {
        const subtotal = patients.reduce(function (s, p) { return s + window.CtPatientCost.computePatientTotal(p, state.packages); }, 0);
        return '<section class="status-room-card status-room-unassigned">' +
            '<div class="status-room-head">' +
            '<strong>미등록</strong>' +
            '<span class="status-room-meta">마스터에 없는 병실 · ' + patients.length + '명 · 소계 ' + subtotal.toLocaleString('ko-KR') + '원</span>' +
            '</div>' +
            renderPatientTable(patients) +
            '</section>';
    }

    function renderPatientTable(patients) {
        const rows = patients.map(function (p) {
            const total = window.CtPatientCost.computePatientTotal(p, state.packages);
            return '<tr>' +
                '<td>' + escapeHtml(p.name) + '</td>' +
                '<td>' + escapeHtml(p.chartNo || '-') + '</td>' +
                '<td>' + escapeHtml(p.room || '-') + '</td>' +
                '<td>' + escapeHtml(p.treatmentInfo || '-') + '</td>' +
                '<td style="text-align:right">' + total.toLocaleString('ko-KR') + ' 원</td>' +
                '</tr>';
        }).join('');
        return '<div class="table-wrap"><table>' +
            '<thead><tr><th>환자명</th><th>차트번호</th><th>병실/외래</th><th>치료정보</th>' +
            '<th style="text-align:right">본인부담 총액</th></tr></thead>' +
            '<tbody>' + rows + '</tbody></table></div>';
    }

    function renderSummary(rooms, showUnassigned) {
        let patientCount = 0;
        let amount = 0;
        rooms.forEach(function (room) {
            (room._patients || []).forEach(function (p) {
                patientCount += 1;
                amount += window.CtPatientCost.computePatientTotal(p, state.packages);
            });
        });
        let roomCount = rooms.length;
        if (showUnassigned) {
            roomCount += 1;
            state._unassigned.forEach(function (p) {
                patientCount += 1;
                amount += window.CtPatientCost.computePatientTotal(p, state.packages);
            });
        }
        els.sumRooms.textContent = roomCount;
        els.sumPatients.textContent = patientCount;
        els.sumAmount.textContent = amount.toLocaleString('ko-KR') + ' 원';
    }

    function renderDiagnostics() {
        const u = diagnostics.unmatched.length;
        const n = diagnostics.nameMatched.length;
        const c = diagnostics.conflicts.length;
        els.diagBadge.textContent = '미등록 ' + u + ' · 표기불일치 ' + n + (c ? ' · 충돌 ' + c : '');
        let body = '';
        body += '<div class="status-diag-group"><strong>미등록(' + u + ')</strong>: '
            + (u ? diagnostics.unmatched.map(function (d) { return escapeHtml(d.room); }).join(', ') : '없음') + '</div>';
        body += '<div class="status-diag-group"><strong>표기불일치 — name 매칭(' + n + ')</strong>: '
            + (n ? diagnostics.nameMatched.map(function (d) { return escapeHtml(d.room); }).join(', ') : '없음')
            + '<br><small>code가 아닌 병실명으로 매칭됨 — FK 전환 시 정리 대상</small></div>';
        body += '<div class="status-diag-group"><strong>충돌 — code/name 동시 매칭(' + c + ')</strong>: '
            + (c ? diagnostics.conflicts.map(function (d) { return escapeHtml(d.room) + '(code→' + escapeHtml(d.code) + ', name→' + escapeHtml(d.name) + ')'; }).join(', ') : '없음')
            + '<br><small>오매칭 위험 — code 우선 적용됨</small></div>';
        els.diagBody.innerHTML = body;
    }

    function renderPrintMeta() {
        const now = new Date();
        const stamp = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate())
            + ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes());
        els.printMeta.textContent = '필터: ' + (FILTER_LABELS[state.filter] || '전체') + ' · 출력일시: ' + stamp;
    }

    function onFilterClick(event) {
        const button = event.target.closest('button[data-filter]');
        if (!button) return;
        state.filter = button.dataset.filter;
        els.filter.querySelectorAll('button[data-filter]').forEach(function (b) {
            b.classList.toggle('is-active', b === button);
        });
        render();
    }

    function pad(n) { return String(n).padStart(2, '0'); }

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
