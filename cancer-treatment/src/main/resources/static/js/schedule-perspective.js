(function () {
    const API = (window.__ctx || '/');
    const PERSPECTIVE = window.__perspective || 'patient';

    // 관점별 그룹 키 + 라벨 (구조는 동일, 축만 다름)
    const CONFIG = {
        patient: { groupOf: function (it) { return it.patientName; }, label: '환자', showPatient: false },
        ward:    { groupOf: function (it) { return it.ward; },        label: '병동', showPatient: true },
        doctor:  { groupOf: function (it) { return it.attendingDoctor; }, label: '의사', showPatient: true }
    };
    const cfg = CONFIG[PERSPECTIVE] || CONFIG.patient;
    const MODE_LABEL = { day: '일', week: '주', month: '월' };

    const els = {
        mode: document.getElementById('persp-mode'),
        prev: document.getElementById('persp-prev'),
        next: document.getElementById('persp-next'),
        today: document.getElementById('persp-today'),
        date: document.getElementById('persp-date'),
        rangeLabel: document.getElementById('persp-range-label'),
        print: document.getElementById('persp-print'),
        unassigned: document.getElementById('persp-unassigned'),
        printMeta: document.getElementById('persp-print-meta'),
        message: document.getElementById('persp-message'),
        body: document.getElementById('persp-body')
    };

    let roomNames = {};
    let state = { mode: 'week', cursor: startOfDay(new Date()) };

    function init() {
        if (!els.body) return;
        els.date.value = toIso(state.cursor);
        els.mode.addEventListener('click', onModeClick);
        els.prev.addEventListener('click', function () { shift(-1); });
        els.next.addEventListener('click', function () { shift(1); });
        els.today.addEventListener('click', function () { state.cursor = startOfDay(new Date()); els.date.value = toIso(state.cursor); load(); });
        els.date.addEventListener('change', function () {
            if (els.date.value) { state.cursor = parseIso(els.date.value); load(); }
        });
        els.print.addEventListener('click', function () { window.print(); });
        fetch(API + 'api/treatment-rooms', { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (payload) {
                (payload.items || []).forEach(function (r) { roomNames[String(r.id)] = r.roomName; });
                load();
            })
            .catch(load);
    }

    function onModeClick(event) {
        const btn = event.target.closest('button[data-mode]');
        if (!btn) return;
        state.mode = btn.dataset.mode;
        els.mode.querySelectorAll('button[data-mode]').forEach(function (b) { b.classList.toggle('is-active', b === btn); });
        load();
    }

    function shift(dir) {
        if (state.mode === 'day') state.cursor = addDays(state.cursor, dir);
        else if (state.mode === 'week') state.cursor = addDays(state.cursor, dir * 7);
        else state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() + dir, 1);
        els.date.value = toIso(state.cursor);
        load();
    }

    function currentRange() {
        if (state.mode === 'day') return { from: state.cursor, to: state.cursor };
        if (state.mode === 'week') {
            const mon = mondayOf(state.cursor);
            return { from: mon, to: addDays(mon, 6) };
        }
        const first = new Date(state.cursor.getFullYear(), state.cursor.getMonth(), 1);
        const last = new Date(state.cursor.getFullYear(), state.cursor.getMonth() + 1, 0);
        return { from: first, to: last };
    }

    function load() {
        const range = currentRange();
        const from = toIso(range.from);
        const to = toIso(range.to);
        els.rangeLabel.textContent = rangeText(range);
        fetch(API + 'api/treatment-schedules?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to),
            { headers: { Accept: 'application/json' } })
            .then(r => r.ok ? r.json() : { items: [] })
            .then(function (payload) { render(range, payload.items || []); })
            .catch(function () { els.body.innerHTML = '<p class="empty">조회 실패</p>'; });
    }

    function render(range, items) {
        els.printMeta.textContent = cfg.label + ' 관점 · ' + rangeText(range) + ' · 출력일시: ' + stamp();
        const groups = groupBy(items, function (it) {
            const v = (cfg.groupOf(it) || '').trim();
            return v || ' 미지정';
        });
        const unassigned = (groups[' 미지정'] || []).length;
        els.unassigned.textContent = unassigned ? (cfg.label + ' 미지정 ' + unassigned + '건') : '';

        const keys = Object.keys(groups).sort(function (a, b) { return a.localeCompare(b, 'ko'); });
        if (keys.length === 0) {
            els.body.innerHTML = '<p class="empty">' + escapeHtml(rangeText(range)) + ' 에 등록된 일정이 없습니다.</p>';
            return;
        }
        let html = '';
        keys.forEach(function (key) {
            const rows = groups[key];
            const isUnassigned = key === ' 미지정';
            const groupLabel = isUnassigned ? (cfg.label + ' 미지정') : key;
            html += '<div class="dd-time' + (isUnassigned ? ' is-unassigned-group' : '') + '">' +
                '<div class="dd-time-label">' + escapeHtml(groupLabel) + ' · ' + rows.length + '건</div>' +
                '<div class="dd-seats">' + renderByDate(rows) + '</div></div>';
        });
        els.body.innerHTML = html;
    }

    // 날짜별로 묶어 표시(주/월에서 가독성). 일 모드는 날짜 1개.
    function renderByDate(rows) {
        const byDate = groupBy(rows, function (it) { return it.treatmentDate || '-'; });
        const dates = Object.keys(byDate).sort();
        return dates.map(function (d) {
            const dayRows = byDate[d].slice().sort(function (a, b) {
                return String(a.startTime).localeCompare(String(b.startTime));
            });
            const head = state.mode === 'day' ? '' : '<div class="persp-date-head">' + escapeHtml(d) + '</div>';
            return head + dayRows.map(renderRow).join('');
        }).join('');
    }

    function renderRow(it) {
        const room = it.treatmentRoomId != null ? (roomNames[String(it.treatmentRoomId)] || '치료실') : '치료실 미지정';
        const seat = it.seatName || '자리 미지정';
        const opt = it.treatmentOption ? '<span class="dd-opt">▶' + escapeHtml(it.treatmentOption) + '</span>' : '';
        const patient = cfg.showPatient ? '<span class="dd-name">' + escapeHtml(it.patientName) + '</span>' : '';
        return '<div class="persp-row">' +
            '<span class="persp-time">' + escapeHtml(it.startTime || '--:--') + '</span>' +
            '<span class="persp-loc">' + escapeHtml(room) + ' · ' + escapeHtml(seat) + '</span>' +
            patient +
            '<span class="dd-treat">' + escapeHtml(it.treatmentName || '-') + '</span>' +
            opt +
            '<em class="sched-status ' + statusClass(it.status) + '">' + escapeHtml(it.status) + '</em>' +
            '</div>';
    }

    function rangeText(range) {
        const mode = MODE_LABEL[state.mode] || '';
        if (state.mode === 'day') return toIso(range.from) + ' (' + mode + ')';
        if (state.mode === 'month') return range.from.getFullYear() + '년 ' + (range.from.getMonth() + 1) + '월 (' + mode + ')';
        return toIso(range.from) + ' ~ ' + toIso(range.to) + ' (' + mode + ')';
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
        return toIso(n) + ' ' + pad(n.getHours()) + ':' + pad(n.getMinutes());
    }
    function startOfDay(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
    function mondayOf(d) { const day = d.getDay(); return addDays(d, day === 0 ? -6 : 1 - day); }
    function addDays(d, n) { const x = new Date(d); x.setDate(x.getDate() + n); return startOfDay(x); }
    function toIso(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
    function parseIso(v) { const p = v.split('-').map(Number); return new Date(p[0], p[1] - 1, p[2]); }
    function pad(n) { return String(n).padStart(2, '0'); }
    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    document.addEventListener('DOMContentLoaded', init);
})();
