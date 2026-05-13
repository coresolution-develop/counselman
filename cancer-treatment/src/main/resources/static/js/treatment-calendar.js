(function () {
    const API = (window.__ctx || '/');

    const state = {
        view: 'month',
        cursor: startOfDay(new Date()),
        selectedDate: toIsoDate(new Date()),
        schedules: [],
        patients: [],
        settings: null,
        items: []
    };

    const els = {
        title: document.getElementById('calendar-title'),
        grid: document.getElementById('calendar-grid'),
        prev: document.getElementById('calendar-prev'),
        next: document.getElementById('calendar-next'),
        viewButtons: Array.from(document.querySelectorAll('[data-calendar-view]')),
        treatment: document.getElementById('calendar-treatment-filter'),
        selectedTitle: document.getElementById('selected-date-title'),
        selectedCount: document.getElementById('selected-date-count'),
        selectedList: document.getElementById('selected-date-list')
    };

    function init() {
        if (!els.grid) return;
        els.prev.addEventListener('click', function () {
            state.cursor = state.view === 'month' ? addMonths(state.cursor, -1) : addDays(state.cursor, -7);
            loadItems();
        });
        els.next.addEventListener('click', function () {
            state.cursor = state.view === 'month' ? addMonths(state.cursor, 1) : addDays(state.cursor, 7);
            loadItems();
        });
        els.viewButtons.forEach(function (button) {
            button.addEventListener('click', function () {
                state.view = button.dataset.calendarView;
                els.viewButtons.forEach(function (item) {
                    item.classList.toggle('is-active', item === button);
                });
                loadItems();
            });
        });
        els.treatment.addEventListener('change', render);
        loadItems();
    }

    function loadItems() {
        Promise.all([
            fetch(API + 'api/treatment-schedules', { headers: { Accept: 'application/json' } }).then(json),
            fetch(API + 'api/patients', { headers: { Accept: 'application/json' } }).then(json),
            fetch(API + 'api/settings', { headers: { Accept: 'application/json' } }).then(json)
        ])
            .then(function (results) {
                state.schedules = (results[0].items || []).map(function (item) {
                    return Object.assign({ source: 'schedule' }, item);
                });
                state.patients = results[1].items || [];
                state.settings = results[2];
                renderTreatmentFilter();
                state.items = state.schedules.concat(generateCandidateItems());
                render();
            })
            .catch(function () {
                els.grid.innerHTML = '<div class="calendar-load-error">캘린더 데이터를 불러오지 못했습니다.</div>';
            });
    }

    function json(response) {
        if (!response.ok) throw new Error('calendar-load-failed');
        return response.json();
    }

    function render() {
        const range = state.view === 'month' ? monthRange(state.cursor) : weekRange(state.cursor);
        const filteredItems = filtered();
        const itemsByDate = groupByDate(filteredItems);
        els.title.textContent = title(range.start, range.end);
        els.grid.classList.toggle('is-week-view', state.view === 'week');
        els.grid.innerHTML = range.days.map(function (date) {
            const iso = toIsoDate(date);
            const dayItems = (itemsByDate.get(iso) || []).sort(sortCalendarItems);
            const outside = state.view === 'month' && date.getMonth() !== state.cursor.getMonth();
            return '<button type="button" class="calendar-day-cell' +
                (outside ? ' is-outside' : '') +
                (iso === state.selectedDate ? ' is-selected' : '') +
                '" data-date="' + iso + '">' +
                '<span class="calendar-day-number">' + pad(date.getDate()) + '</span>' +
                renderDayLines(dayItems) +
                '</button>';
        }).join('');
        els.grid.querySelectorAll('[data-date]').forEach(function (button) {
            button.addEventListener('click', function () {
                state.selectedDate = button.dataset.date;
                render();
            });
        });
        renderSelectedDay(itemsByDate.get(state.selectedDate) || []);
    }

    function renderTreatmentFilter() {
        const currentValue = els.treatment.value;
        const treatmentTypes = (state.settings && state.settings.treatmentTypes) || [];
        els.treatment.innerHTML = '<option value="">전체 치료</option>' + treatmentTypes.map(function (item) {
            return '<option value="' + escapeHtml(item.name) + '">' + escapeHtml(item.name) + '</option>';
        }).join('');
        els.treatment.value = treatmentTypes.some(function (item) {
            return item.name === currentValue;
        }) ? currentValue : '';
    }

    function renderDayLines(items) {
        if (items.length === 0) return '<span class="calendar-day-empty">일정 없음</span>';
        const visible = items.slice(0, 9).map(function (item) {
            const option = item.treatmentOption ? '▶' + item.treatmentOption : '';
            const status = item.source === 'candidate' ? '후보' : item.status;
            const line = item.patientName + '▶' + (item.startTime || '-') + '▶' + item.treatmentName + option + '▶' + status;
            return '<span class="calendar-event-line ' + (item.source === 'candidate' ? 'is-candidate' : '') + '">' +
                escapeHtml(line) + '</span>';
        }).join('');
        const more = items.length > 9 ? '<span class="calendar-more">+' + (items.length - 9) + '건</span>' : '';
        return '<span class="calendar-event-list">' + visible + more + '</span>';
    }

    function renderSelectedDay(items) {
        const sorted = items.slice().sort(sortCalendarItems);
        els.selectedTitle.textContent = state.selectedDate;
        els.selectedCount.textContent = sorted.length + '건';
        if (sorted.length === 0) {
            els.selectedList.innerHTML = '<p class="empty">선택 날짜의 치료 일정이 없습니다.</p>';
            return;
        }
        els.selectedList.innerHTML = sorted.map(function (item) {
            return '<article class="day-event ' + (item.source === 'candidate' ? 'is-candidate' : '') + '">' +
                '<time>' + escapeHtml(item.startTime || '-') + '</time>' +
                '<div><strong>' + escapeHtml(item.patientName) + '</strong>' +
                '<span>' + escapeHtml(item.treatmentName) + ' · ' + escapeHtml(item.ward || '-') + '</span></div>' +
                '<em class="' + statusClass(item.status, item.source) + '">' + escapeHtml(item.source === 'candidate' ? '후보' : item.status) + '</em>' +
                '</article>';
        }).join('');
    }

    function generateCandidateItems() {
        const range = state.view === 'month' ? monthRange(state.cursor) : weekRange(state.cursor);
        const days = range.days.filter(function (date) {
            return state.view !== 'month' || date.getMonth() === state.cursor.getMonth();
        });
        return state.patients.flatMap(function (patient) {
            const info = String(patient.treatmentInfo || '').trim();
            if (!info) return [];
            const start = patient.admissionDate ? parseIsoDate(patient.admissionDate) : range.start;
            const end = patient.dischargeDate ? parseIsoDate(patient.dischargeDate) : range.end;
            const weeklyCount = weeklyTreatmentCount(info);
            return days
                .filter(function (date) { return date >= start && date <= end; })
                .filter(function (date) { return candidateWeekdayIndex(date) < weeklyCount; })
                .map(function (date) {
                    return {
                        id: 'candidate-' + patient.id + '-' + toIsoDate(date),
                        source: 'candidate',
                        treatmentDate: toIsoDate(date),
                        startTime: '',
                        patientName: patient.name,
                        ward: patient.ward,
                        treatmentName: summarizeTreatmentInfo(info),
                        treatmentOption: '',
                        status: '후보',
                        treatmentInfo: info,
                        note: patient.note || ''
                    };
                });
        });
    }

    function weeklyTreatmentCount(info) {
        const matches = Array.from(info.matchAll(/주\s*(\d+)/g)).map(function (match) {
            return Number(match[1]);
        });
        if (matches.length === 0) return 1;
        return Math.min(Math.max.apply(null, matches), 5);
    }

    function candidateWeekdayIndex(date) {
        const day = date.getDay();
        return day === 0 || day === 6 ? 99 : day - 1;
    }

    function summarizeTreatmentInfo(info) {
        return info.split(/[,+/]/).map(function (part) {
            return part.trim();
        }).filter(Boolean).slice(0, 2).join('+') || '치료후보';
    }

    function filtered() {
        const treatmentName = els.treatment.value;
        if (!treatmentName) return state.items;
        return state.items.filter(function (item) {
            return item.treatmentName === treatmentName || String(item.treatmentInfo || '').includes(treatmentName);
        });
    }

    function groupByDate(items) {
        const map = new Map();
        items.forEach(function (item) {
            if (!map.has(item.treatmentDate)) map.set(item.treatmentDate, []);
            map.get(item.treatmentDate).push(item);
        });
        return map;
    }

    function monthRange(date) {
        const first = new Date(date.getFullYear(), date.getMonth(), 1);
        const start = addDays(first, first.getDay() === 0 ? -6 : 1 - first.getDay());
        const days = [];
        for (let cursor = start; days.length < 30; cursor = addDays(cursor, 1)) {
            if (cursor.getDay() !== 0 && cursor.getDay() !== 6) days.push(cursor);
            const passedMonth = cursor.getMonth() > date.getMonth() || cursor.getFullYear() > date.getFullYear();
            if (passedMonth && cursor.getDay() === 5 && days.length >= 25) break;
        }
        return { start: days[0], end: days[days.length - 1], days: days };
    }

    function weekRange(date) {
        const monday = addDays(date, date.getDay() === 0 ? -6 : 1 - date.getDay());
        const days = Array.from({ length: 5 }, function (_, index) {
            return addDays(monday, index);
        });
        return { start: days[0], end: days[4], days: days };
    }

    function title(start, end) {
        if (state.view === 'month') {
            return state.cursor.getFullYear() + '년 ' + (state.cursor.getMonth() + 1) + '월';
        }
        return toIsoDate(start) + ' ~ ' + toIsoDate(end);
    }

    function sortCalendarItems(a, b) {
        return String(a.startTime || '99:99').localeCompare(String(b.startTime || '99:99'))
            || String(a.patientName).localeCompare(String(b.patientName), 'ko');
    }

    function addDays(date, amount) {
        const next = new Date(date);
        next.setDate(next.getDate() + amount);
        return startOfDay(next);
    }

    function addMonths(date, amount) {
        return new Date(date.getFullYear(), date.getMonth() + amount, 1);
    }

    function startOfDay(date) {
        return new Date(date.getFullYear(), date.getMonth(), date.getDate());
    }

    function parseIsoDate(value) {
        const parts = value.split('-').map(Number);
        return new Date(parts[0], parts[1] - 1, parts[2]);
    }

    function toIsoDate(date) {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return y + '-' + m + '-' + d;
    }

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function statusClass(status, source) {
        if (source === 'candidate') return 'is-candidate';
        if (status === '치료완료') return 'is-completed';
        if (status === '예약취소') return 'is-canceled';
        return 'is-reserved';
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
