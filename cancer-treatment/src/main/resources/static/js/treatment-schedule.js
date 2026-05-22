(function () {
    var API = (window.__ctx || '/');

    var state = {
        eventSource: null,
        pollingTimer: null,
        reconnectTimer: null
    };

    var els = {
        form:             document.getElementById('schedule-filter'),
        date:             document.getElementById('filter-date'),
        keyword:          document.getElementById('filter-keyword'),
        treatment:        document.getElementById('filter-treatment'),
        status:           document.getElementById('filter-status'),
        body:             document.getElementById('schedule-body'),
        chip:             document.getElementById('realtime-chip'),
        lastSync:         document.getElementById('last-sync'),
        summaryTotal:     document.getElementById('summary-total'),
        summaryReserved:  document.getElementById('summary-reserved'),
        summaryCompleted: document.getElementById('summary-completed'),
        summaryCanceled:  document.getElementById('summary-canceled'),
        sidebarCount:     document.getElementById('sidebar-today-count'),
        queueList:        document.getElementById('queue-list'),
        notesList:        document.getElementById('notes-list'),
        patientList:      document.getElementById('patient-list'),
        badgeToday:       document.getElementById('badge-today'),
        badgeReserved:    document.getElementById('badge-reserved')
    };

    function init() {
        if (!els.form) return;
        els.date.value = new Date().toISOString().slice(0, 10);
        els.form.addEventListener('submit', function (event) {
            event.preventDefault();
            loadSchedules();
        });
        els.body.addEventListener('change', handleStatusChange);
        els.body.addEventListener('dblclick', handleInlineEditStart);
        setupDragAndDrop();
        loadSchedules();
        loadCalendarOverview();
        loadModalMasters();
        setupPatientCombobox();
        setupTreatmentCombobox();
        connectSse();
    }

    function loadModalMasters() {
        window.__sm = window.__sm || { wards: [], types: [], options: [] };
        fetch(API + 'api/settings', { headers: { Accept: 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : {}; })
            .then(function (data) {
                window.__sm.wards   = data.wards            || [];
                window.__sm.types   = data.treatmentTypes   || [];
                window.__sm.options = data.treatmentOptions || [];
            });
    }

    /**
     * Generic combobox helper.
     * opts: { input, dropdown, emptyEl?, search: (q) => Promise<items[]>, render: (item) => htmlString, onSelect: (item) => void, minChars?: number }
     */
    function attachCombobox(opts) {
        var input = opts.input;
        var dropdown = opts.dropdown;
        var emptyEl = opts.emptyEl || null;
        var minChars = opts.minChars == null ? 0 : opts.minChars;
        var debounceMs = opts.debounceMs == null ? 180 : opts.debounceMs;
        var activeIndex = -1;
        var currentItems = [];
        var fetchSeq = 0;
        var timer = null;

        function show(items) {
            currentItems = items || [];
            activeIndex = -1;
            if (!currentItems.length) {
                dropdown.innerHTML = '';
                dropdown.style.display = 'none';
                if (emptyEl) emptyEl.style.display = input.value.trim() ? 'block' : 'none';
                return;
            }
            dropdown.innerHTML = currentItems.slice(0, 30).map(function (it, i) {
                return '<div class="combobox-item" role="option" data-idx="' + i + '">' + opts.render(it) + '</div>';
            }).join('');
            dropdown.style.display = 'block';
            if (emptyEl) emptyEl.style.display = 'none';
        }

        function close() {
            dropdown.style.display = 'none';
            if (emptyEl) emptyEl.style.display = 'none';
            activeIndex = -1;
        }

        function setActive(i) {
            var nodes = dropdown.querySelectorAll('.combobox-item');
            if (!nodes.length) return;
            if (i < 0) i = nodes.length - 1;
            if (i >= nodes.length) i = 0;
            activeIndex = i;
            nodes.forEach(function (n, idx) {
                n.classList.toggle('is-active', idx === activeIndex);
            });
            var act = nodes[activeIndex];
            if (act) act.scrollIntoView({ block: 'nearest' });
        }

        function pick(i) {
            if (i < 0 || i >= currentItems.length) return;
            opts.onSelect(currentItems[i]);
            close();
        }

        function trigger() {
            var q = input.value.trim();
            if (q.length < minChars) {
                close();
                return;
            }
            var mySeq = ++fetchSeq;
            Promise.resolve(opts.search(q)).then(function (items) {
                if (mySeq !== fetchSeq) return;
                show(items);
            }).catch(function () {
                if (mySeq !== fetchSeq) return;
                show([]);
            });
        }

        function scheduleTrigger() {
            clearTimeout(timer);
            timer = setTimeout(trigger, debounceMs);
        }

        input.addEventListener('focus', trigger);
        input.addEventListener('input', scheduleTrigger);
        input.addEventListener('blur', function () {
            setTimeout(close, 150);
        });
        input.addEventListener('keydown', function (e) {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                if (dropdown.style.display === 'none') trigger();
                else setActive(activeIndex + 1);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActive(activeIndex - 1);
            } else if (e.key === 'Enter') {
                if (activeIndex >= 0 && dropdown.style.display !== 'none') {
                    e.preventDefault();
                    pick(activeIndex);
                }
            } else if (e.key === 'Escape') {
                close();
            }
        });
        dropdown.addEventListener('mousedown', function (e) {
            var item = e.target.closest('.combobox-item');
            if (!item) return;
            e.preventDefault();
            pick(parseInt(item.dataset.idx, 10));
        });
    }

    function setupPatientCombobox() {
        var input    = document.getElementById('modal-patient');
        var dropdown = document.getElementById('modal-patient-dropdown');
        var empty    = document.getElementById('modal-patient-empty');
        if (!input || !dropdown) return;

        attachCombobox({
            input: input,
            dropdown: dropdown,
            emptyEl: empty,
            search: function (q) {
                var url = API + 'api/patients' + (q ? '?keyword=' + encodeURIComponent(q) : '');
                return fetch(url, { headers: { Accept: 'application/json' } })
                    .then(function (r) { return r.ok ? r.json() : { items: [] }; })
                    .then(function (data) { return data.items || []; });
            },
            render: function (p) {
                var parts = [];
                if (p.ward)    parts.push(escapeAttr(p.ward));
                if (p.chartNo) parts.push('#' + escapeAttr(p.chartNo));
                if (p.room)    parts.push(escapeAttr(p.room));
                var meta = parts.length ? '<span class="meta">' + parts.join(' · ') + '</span>' : '';
                return '<strong>' + escapeAttr(p.name) + '</strong>' + meta;
            },
            onSelect: function (p) {
                input.value = p.name || '';
                var wardSelect = document.getElementById('modal-ward');
                if (wardSelect && p.ward) setSelectValue(wardSelect, p.ward);
            }
        });
    }

    function setupTreatmentCombobox() {
        var input    = document.getElementById('modal-treatment');
        var dropdown = document.getElementById('modal-treatment-dropdown');
        if (!input || !dropdown) return;

        attachCombobox({
            input: input,
            dropdown: dropdown,
            search: function (q) {
                var types = (window.__sm && window.__sm.types) || [];
                if (!q) return types;
                var lower = q.toLowerCase();
                return types.filter(function (t) {
                    return (t.name || '').toLowerCase().indexOf(lower) >= 0;
                });
            },
            render: function (t) {
                var detail = t.detail ? '<span class="meta">' + escapeAttr(t.detail) + '</span>' : '';
                return '<strong>' + escapeAttr(t.name) + '</strong>' + detail;
            },
            onSelect: function (t) {
                input.value = t.name || '';
            }
        });
    }

    function escapeAttr(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c];
        });
    }

    // Exposed for global modal functions
    window.__sm_helpers = {
        populate: populateScheduleSelects,
        setSelect: setSelectValue
    };

    function populateScheduleSelects() {
        var masters = window.__sm || { wards: [], options: [] };
        fillSelect(document.getElementById('modal-ward'),   masters.wards,   function (it) { return it.name; }, function (it) { return (it.code && it.code !== it.name) ? it.name + ' (' + it.code + ')' : it.name; });
        fillSelect(document.getElementById('modal-option'), masters.options, function (it) { return it.name; }, function (it) { return (it.code && it.code !== it.name) ? it.code + ' — ' + it.name : it.name; });
    }

    function fillSelect(select, items, valueFn, labelFn) {
        if (!select) return;
        select.innerHTML = '<option value="">선택</option>'
            + (items || []).map(function (it) {
                var v = valueFn(it);
                return '<option value="' + escapeAttr(v) + '">' + escapeAttr(labelFn(it)) + '</option>';
            }).join('');
    }

    function setSelectValue(select, value) {
        if (!select) return;
        var v = value == null ? '' : String(value);
        var exists = Array.prototype.some.call(select.options, function (o) { return o.value === v; });
        if (!exists && v) {
            var opt = document.createElement('option');
            opt.value = v;
            opt.textContent = '[기타] ' + v;
            opt.dataset.legacy = '1';
            select.appendChild(opt);
        }
        select.value = v;
    }

    function loadCalendarOverview() {
        fetch(API + 'api/treatment-schedules', { headers: { Accept: 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
            .then(function (data) {
                var counts = {};
                (data.items || []).forEach(function (item) {
                    if (item.treatmentDate) {
                        counts[item.treatmentDate] = (counts[item.treatmentDate] || 0) + 1;
                    }
                });
                window.__calDateCounts = counts;
                if (typeof renderCal === 'function') renderCal();
                if (typeof currentView !== 'undefined') {
                    if (currentView === 'week' && typeof renderWeekStrip === 'function') renderWeekStrip();
                    if (currentView === 'month' && typeof renderMonthView === 'function') renderMonthView();
                }
            })
            .catch(function () {});
    }

    function queryString() {
        var params = new URLSearchParams();
        if (els.date.value)              params.set('date',          els.date.value);
        if (els.keyword.value.trim())    params.set('keyword',       els.keyword.value.trim());
        if (els.treatment.value)         params.set('treatmentName', els.treatment.value);
        if (els.status.value)            params.set('status',        els.status.value);
        return params.toString();
    }

    function loadSchedules() {
        return fetch(API + 'api/treatment-schedules?' + queryString(), {
            headers: { 'Accept': 'application/json' }
        })
            .then(function (response) {
                if (!response.ok) throw new Error('schedule-load-failed');
                return response.json();
            })
            .then(function (payload) {
                renderSummary(payload.summary || {});
                renderRows(payload.items || []);
                setLastSync();
            })
            .catch(function () {
                if (els.body) {
                    els.body.innerHTML = '<div class="empty">스케줄을 불러오지 못했습니다.</div>';
                }
            });
    }

    function renderSummary(summary) {
        if (els.summaryTotal)     els.summaryTotal.textContent     = summary.total     || 0;
        if (els.summaryReserved)  els.summaryReserved.textContent  = summary.reserved  || 0;
        if (els.summaryCompleted) els.summaryCompleted.textContent = summary.completed || 0;
        if (els.summaryCanceled)  els.summaryCanceled.textContent  = summary.canceled  || 0;
        if (els.sidebarCount)     els.sidebarCount.textContent     = summary.total     || 0;
        if (els.badgeToday)       els.badgeToday.textContent       = summary.total     || 0;
        if (els.badgeReserved)    els.badgeReserved.textContent    = summary.reserved  || 0;
    }

    /* ── Timeline Rendering ──────────────────────────────── */

    function renderRows(items) {
        if (!els.body) return;

        if (items.length === 0) {
            els.body.innerHTML = '<div class="empty" style="padding:48px 16px;text-align:center;color:var(--fg4)">조건에 맞는 치료 스케줄이 없습니다.</div>';
            renderQueue([]);
            renderNotes([]);
            renderPatientList([]);
            return;
        }

        // startTime 기준 그룹핑
        var grouped = {};
        items.forEach(function (item) {
            var t = item.startTime || '00:00';
            if (!grouped[t]) grouped[t] = [];
            grouped[t].push(item);
        });

        var times = Object.keys(grouped).sort();
        els.body.innerHTML = times.map(function (time) {
            var blocks = grouped[time].map(function (item) {
                return sessionBlock(item);
            }).join('');
            return '<div class="time-slot">' +
                '<div class="time-label">' + escapeHtml(time) + '</div>' +
                '<div class="time-track">' + blocks + '</div>' +
                '</div>';
        }).join('');

        renderQueue(items);
        renderNotes(items);
        renderPatientList(items);
    }

    function sessionBlock(item) {
        var initial  = item.patientName ? item.patientName.charAt(0) : '?';
        var color    = patientColor(item.patientName);
        var blockCls = 'session-block';
        if (item.status === '치료완료') {
            blockCls += ' success';
        } else if (item.status !== '예약') {
            blockCls += ' muted';
        }
        if (item.status === '예약취소') blockCls += ' cancelled';

        var sColor = item.status === '치료완료' ? 'var(--success-alt)'
                   : item.status === '예약취소' ? 'var(--fg4)'
                   : 'var(--success)';
        var sLabel = item.status || '예약';

        return '<div class="' + blockCls + '" draggable="true"' +
            ' data-id="' + escapeHtml(String(item.id || '')) + '"' +
            ' data-date="' + escapeHtml(item.treatmentDate || '') + '"' +
            ' data-time="' + escapeHtml(item.startTime || '') + '"' +
            ' data-patient="' + escapeHtml(item.patientName || '') + '"' +
            ' data-ward="' + escapeHtml(item.ward || '') + '"' +
            ' data-treatment="' + escapeHtml(item.treatmentName || '') + '"' +
            ' data-option="' + escapeHtml(item.treatmentOption || '') + '"' +
            ' data-status="' + escapeHtml(item.status || '') + '"' +
            ' data-info="' + escapeHtml(item.treatmentInfo || '') + '"' +
            ' data-note="' + escapeHtml(item.note || '') + '"' +
            ' onclick="selectSession(this)">' +
            '<div class="session-avatar" style="background:' + color + '">' + escapeHtml(initial) + '</div>' +
            '<div class="session-info">' +
                '<div class="session-name">' + escapeHtml(item.patientName || '') + '</div>' +
                '<div class="session-type">' + escapeHtml(item.treatmentName || '') +
                    (item.treatmentOption ? ' · ' + escapeHtml(item.treatmentOption) : '') +
                    (item.ward ? ' <span style="color:var(--fg4);font-size:11px">· ' + escapeHtml(item.ward) + '</span>' : '') +
                '</div>' +
                (item.treatmentInfo ? '<div class="print-only print-extra">처방: ' + escapeHtml(item.treatmentInfo) + '</div>' : '') +
                (item.note ? '<div class="print-only print-extra note">메모: ' + escapeHtml(item.note) + '</div>' : '') +
            '</div>' +
            '<div class="session-meta">' +
                '<div class="session-status">' +
                    '<span class="status-dot" style="background:' + sColor + '"></span>' +
                    '<span style="color:' + sColor + '">' + escapeHtml(sLabel) + '</span>' +
                '</div>' +
            '</div>' +
            '</div>';
    }

    /* ── Drag & Drop time change ─────────────────────────── */

    function setupDragAndDrop() {
        if (!els.body) return;
        var dragId = null;
        var dragSourceTime = null;

        els.body.addEventListener('dragstart', function (e) {
            var block = e.target.closest && e.target.closest('.session-block');
            if (!block) return;
            dragId = block.dataset.id;
            dragSourceTime = block.dataset.time;
            block.classList.add('is-dragging');
            if (e.dataTransfer) {
                e.dataTransfer.effectAllowed = 'move';
                try { e.dataTransfer.setData('text/plain', dragId); } catch (_) {}
            }
        });

        els.body.addEventListener('dragend', function (e) {
            var block = e.target.closest && e.target.closest('.session-block');
            if (block) block.classList.remove('is-dragging');
            clearDropTargets();
            dragId = null;
            dragSourceTime = null;
        });

        els.body.addEventListener('dragover', function (e) {
            if (!dragId) return;
            var track = e.target.closest && e.target.closest('.time-track');
            if (!track) return;
            e.preventDefault();
            if (e.dataTransfer) e.dataTransfer.dropEffect = 'move';
            clearDropTargets();
            track.classList.add('is-drop-target');
        });

        els.body.addEventListener('dragleave', function (e) {
            var track = e.target.closest && e.target.closest('.time-track');
            if (track && !track.contains(e.relatedTarget)) {
                track.classList.remove('is-drop-target');
            }
        });

        els.body.addEventListener('drop', function (e) {
            if (!dragId) return;
            var track = e.target.closest && e.target.closest('.time-track');
            if (!track) return;
            e.preventDefault();
            clearDropTargets();

            var slot = track.closest('.time-slot');
            var label = slot && slot.querySelector('.time-label');
            var targetTime = label ? label.textContent.trim() : '';
            var id = dragId;
            dragId = null;
            if (!targetTime || targetTime === dragSourceTime) return;
            patchStartTime(id, targetTime);
        });
    }

    function clearDropTargets() {
        document.querySelectorAll('.time-track.is-drop-target').forEach(function (el) {
            el.classList.remove('is-drop-target');
        });
    }

    function patchStartTime(id, startTime) {
        fetch(API + 'api/treatment-schedules/' + encodeURIComponent(id) + '/time', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ startTime: startTime })
        })
            .then(function (r) {
                if (!r.ok) return r.json().then(function (e) { throw new Error(e.error || '시간 변경 실패'); });
                return r.json();
            })
            .then(function () {
                loadSchedules();
                loadCalendarOverview();
            })
            .catch(function (e) {
                alert(e.message || '시간 변경에 실패했습니다.');
                loadSchedules();
            });
    }

    function renderQueue(items) {
        if (!els.queueList) return;
        var upcoming = items.filter(function (i) { return i.status === '예약'; }).slice(0, 4);
        if (!upcoming.length) {
            els.queueList.innerHTML = '<div style="padding:8px 16px;font-size:12px;color:var(--fg4)">예약된 세션 없음</div>';
            return;
        }
        els.queueList.innerHTML = upcoming.map(function (item) {
            var initial = item.patientName ? item.patientName.charAt(0) : '?';
            return '<div class="queue-item">' +
                '<div class="queue-avatar" style="background:' + patientColor(item.patientName) + '">' + escapeHtml(initial) + '</div>' +
                '<div class="queue-info">' +
                    '<div class="queue-name">' + escapeHtml(item.patientName || '') + '</div>' +
                    '<div class="queue-type">' + escapeHtml(item.treatmentName || '') + '</div>' +
                '</div>' +
                '<div class="queue-time">' + escapeHtml(item.startTime || '') + '</div>' +
                '</div>';
        }).join('');
    }

    function renderNotes(items) {
        if (!els.notesList) return;
        var withNotes = items.filter(function (i) { return i.treatmentInfo || i.note; }).slice(0, 3);
        if (!withNotes.length) {
            els.notesList.innerHTML = '';
            return;
        }
        function sColor(s) {
            return s === '치료완료' ? 'var(--success-alt)' : s === '예약취소' ? 'var(--fg4)' : 'var(--success)';
        }
        function sLabel(s) {
            return s === '치료완료' ? '완료' : s === '예약취소' ? '취소' : '진행 중';
        }
        els.notesList.innerHTML = withNotes.map(function (item) {
            var content = item.treatmentInfo || item.note || '';
            return '<div class="note-card">' +
                '<div class="note-header">' +
                    '<span class="note-title">' + escapeHtml(item.patientName || '') + ' — ' + escapeHtml(item.treatmentName || '') + '</span>' +
                    '<span class="note-tag" style="color:' + sColor(item.status) + '">' + sLabel(item.status) + '</span>' +
                '</div>' +
                '<div class="note-body">' + escapeHtml(content) + '</div>' +
                '</div>';
        }).join('');
    }

    function renderPatientList(items) {
        if (!els.patientList) return;
        var seen = {}, patients = [];
        items.forEach(function (item) {
            if (item.patientName && !seen[item.patientName]) {
                seen[item.patientName] = true;
                patients.push(item);
            }
        });
        patients = patients.slice(0, 5);
        if (!patients.length) {
            els.patientList.innerHTML = '';
            return;
        }
        els.patientList.innerHTML = patients.map(function (item) {
            var initial  = item.patientName.charAt(0);
            var dotColor = item.status === '예약' ? 'var(--success-alt)' : 'var(--fg4)';
            return '<div class="sb-patient">' +
                '<div class="patient-avatar" style="background:' + patientColor(item.patientName) + '">' + escapeHtml(initial) + '</div>' +
                '<span class="patient-name">' + escapeHtml(item.patientName) + '</span>' +
                '<span class="patient-dot" style="background:' + dotColor + '"></span>' +
                '</div>';
        }).join('');
    }

    function patientColor(name) {
        var palette = ['#5e6ad2', '#10b981', '#7170ff', '#27a644', '#7a7fad', '#f59e0b', '#e06c75'];
        if (!name) return palette[0];
        var h = 0;
        for (var i = 0; i < name.length; i++) h = name.charCodeAt(i) + ((h << 5) - h);
        return palette[Math.abs(h) % palette.length];
    }

    /* ── Status Change (select in table — kept for API compatibility) ── */

    function handleStatusChange(event) {
        var target = event.target;
        if (!target.classList.contains('status-select')) return;
        fetch(API + 'api/treatment-schedules/' + encodeURIComponent(target.dataset.id) + '/status', {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'Accept':       'application/json'
            },
            body: JSON.stringify({ status: target.value })
        })
            .then(function (response) {
                if (!response.ok) throw new Error('status-update-failed');
                return response.json();
            })
            .then(loadSchedules)
            .catch(loadSchedules);
    }

    /* ── Inline Edit (no .editable-cell in timeline, kept for compat) ── */

    function handleInlineEditStart(event) {
        var cell = event.target.closest('.editable-cell');
        if (!cell || cell.querySelector('input')) return;
        var row = cell.closest('tr[data-id]');
        if (!row) return;

        var field         = cell.dataset.field;
        var originalValue = cell.textContent === '-' ? '' : cell.textContent.trim();
        var input         = document.createElement('input');
        input.className   = 'inline-edit-input';
        input.value       = originalValue;
        cell.innerHTML    = '';
        cell.appendChild(input);
        input.focus();
        input.select();

        var finished = false;
        input.addEventListener('keydown', function (keyEvent) {
            if (keyEvent.key === 'Enter') {
                keyEvent.preventDefault();
                input.blur();
            }
            if (keyEvent.key === 'Escape') {
                keyEvent.preventDefault();
                finished = true;
                cell.textContent = originalValue || '-';
            }
        });
        input.addEventListener('blur', function () {
            if (finished) return;
            finished = true;
            saveInlineField(row.dataset.id, field, input.value.trim(), originalValue, cell);
        });
    }

    function saveInlineField(id, field, value, originalValue, cell) {
        fetch(API + 'api/treatment-schedules/' + encodeURIComponent(id) + '/field', {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'Accept':       'application/json'
            },
            body: JSON.stringify({ field: field, value: value })
        })
            .then(function (response) {
                if (!response.ok) throw new Error('field-update-failed');
                return response.json();
            })
            .then(function (payload) {
                var nextValue = field === 'note' ? payload.note : payload.treatmentInfo;
                cell.textContent = nextValue || '-';
                setLastSync();
            })
            .catch(function () {
                cell.textContent = originalValue || '-';
            });
    }

    /* ── SSE / Polling ───────────────────────────────────── */

    function connectSse() {
        if (!window.EventSource) {
            startPollingFallback();
            return;
        }
        clearPollingFallback();
        clearTimeout(state.reconnectTimer);
        state.eventSource = new EventSource(API + 'api/treatment-schedules/events');
        setRealtimeState('connecting', '연결 중');

        state.eventSource.addEventListener('connected', function () {
            setRealtimeState('connected', '실시간 연결');
        });
        state.eventSource.addEventListener('schedule-changed', function () {
            loadSchedules();
            loadCalendarOverview();
        });
        state.eventSource.onerror = function () {
            closeSse();
            setRealtimeState('fallback', '자동 조회 전환');
            startPollingFallback();
            state.reconnectTimer = setTimeout(connectSse, 30000);
        };
    }

    function startPollingFallback() {
        if (state.pollingTimer) return;
        state.pollingTimer = setInterval(loadSchedules, 15000);
    }

    function clearPollingFallback() {
        if (!state.pollingTimer) return;
        clearInterval(state.pollingTimer);
        state.pollingTimer = null;
    }

    function closeSse() {
        if (!state.eventSource) return;
        state.eventSource.close();
        state.eventSource = null;
    }

    function setRealtimeState(stateName, label) {
        if (!els.chip) return;
        els.chip.className = 'realtime-chip is-' + stateName;
        els.chip.querySelector('strong').textContent = label;
    }

    function setLastSync() {
        if (!els.lastSync) return;
        els.lastSync.textContent = '마지막 갱신: ' + new Date().toLocaleTimeString('ko-KR', {
            hour:   '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g,  '&amp;')
            .replace(/</g,  '&lt;')
            .replace(/>/g,  '&gt;')
            .replace(/"/g,  '&quot;')
            .replace(/'/g,  '&#39;');
    }

    window.__loadCalOverview = loadCalendarOverview;
    window.addEventListener('beforeunload', closeSse);
    document.addEventListener('DOMContentLoaded', init);
})();

/* ── Schedule Modal (global, called from HTML onclick) ──── */

function openScheduleModal() {
    if (window.__sm_helpers) window.__sm_helpers.populate();
    document.getElementById('modal-title').textContent = '스케줄 등록';
    document.getElementById('modal-schedule-id').value = '';
    document.getElementById('modal-date').value = document.getElementById('filter-date').value || new Date().toISOString().slice(0, 10);
    document.getElementById('modal-time').value = '';
    document.getElementById('modal-patient').value = '';
    document.getElementById('modal-ward').value = '';
    document.getElementById('modal-treatment').value = '';
    document.getElementById('modal-option').value = '';
    document.getElementById('modal-status').value = '예약';
    document.getElementById('modal-info').value = '';
    document.getElementById('modal-note').value = '';
    document.getElementById('modal-delete-btn').style.display = 'none';
    var emptyHint = document.getElementById('modal-patient-empty');
    if (emptyHint) emptyHint.style.display = 'none';
    document.getElementById('schedule-modal-overlay').style.display = 'flex';
}

function selectSession(el) {
    var id        = el.dataset.id;
    var date      = el.dataset.date;
    var time      = el.dataset.time;
    var patient   = el.dataset.patient   || '';
    var ward      = el.dataset.ward      || '';
    var treatment = el.dataset.treatment || '';
    var option    = el.dataset.option    || '';
    var status    = el.dataset.status    || '예약';
    var info      = el.dataset.info      || '';
    var note      = el.dataset.note      || '';

    if (window.__sm_helpers) window.__sm_helpers.populate();
    document.getElementById('modal-title').textContent = '스케줄 수정';
    document.getElementById('modal-schedule-id').value = id;
    document.getElementById('modal-date').value = date;
    document.getElementById('modal-time').value = time;
    document.getElementById('modal-patient').value = patient;
    document.getElementById('modal-treatment').value = treatment;
    if (window.__sm_helpers) {
        window.__sm_helpers.setSelect(document.getElementById('modal-ward'),   ward);
        window.__sm_helpers.setSelect(document.getElementById('modal-option'), option);
    }
    document.getElementById('modal-status').value = status;
    document.getElementById('modal-info').value = info;
    document.getElementById('modal-note').value = note;
    document.getElementById('modal-delete-btn').style.display = '';
    var emptyHint = document.getElementById('modal-patient-empty');
    if (emptyHint) emptyHint.style.display = 'none';
    document.getElementById('schedule-modal-overlay').style.display = 'flex';
}

function closeScheduleModal() {
    document.getElementById('schedule-modal-overlay').style.display = 'none';
}

function saveScheduleModal() {
    var id = document.getElementById('modal-schedule-id').value;
    var body = {
        treatmentDate:   document.getElementById('modal-date').value,
        startTime:       document.getElementById('modal-time').value,
        patientName:     document.getElementById('modal-patient').value.trim(),
        ward:            document.getElementById('modal-ward').value.trim(),
        treatmentName:   document.getElementById('modal-treatment').value.trim(),
        treatmentOption: document.getElementById('modal-option').value.trim(),
        status:          document.getElementById('modal-status').value,
        treatmentInfo:   document.getElementById('modal-info').value.trim(),
        note:            document.getElementById('modal-note').value.trim()
    };
    if (!body.treatmentDate || !body.startTime || !body.patientName || !body.treatmentName) {
        alert('날짜, 시작시간, 환자명, 치료종류는 필수입니다.');
        return;
    }
    var url    = id ? API + 'api/treatment-schedules/' + encodeURIComponent(id) : API + 'api/treatment-schedules';
    var method = id ? 'PUT' : 'POST';
    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify(body)
    })
        .then(function (r) { if (!r.ok) return r.json().then(function (e) { throw new Error(e.error || '저장 실패'); }); return r.json(); })
        .then(function () {
            closeScheduleModal();
            document.getElementById('schedule-filter').dispatchEvent(new Event('submit'));
            if (typeof window.__loadCalOverview === 'function') window.__loadCalOverview();
        })
        .catch(function (e) { alert(e.message); });
}

function deleteScheduleFromModal() {
    var id = document.getElementById('modal-schedule-id').value;
    if (!id || !confirm('이 스케줄을 삭제하시겠습니까?')) return;
    fetch(API + 'api/treatment-schedules/' + encodeURIComponent(id), {
        method: 'DELETE',
        headers: { 'Accept': 'application/json' }
    })
        .then(function (r) { if (!r.ok && r.status !== 204) throw new Error('삭제 실패'); })
        .then(function () {
            closeScheduleModal();
            document.getElementById('schedule-filter').dispatchEvent(new Event('submit'));
            if (typeof window.__loadCalOverview === 'function') window.__loadCalOverview();
        })
        .catch(function (e) { alert(e.message); });
}

function filterByStatus(status) {
    var el = document.getElementById('filter-status');
    if (el) { el.value = status; }
    document.getElementById('schedule-filter').dispatchEvent(new Event('submit'));
}
