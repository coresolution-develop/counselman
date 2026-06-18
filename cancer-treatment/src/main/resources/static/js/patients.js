(function () {
    const API = (window.__ctx || '/');
    const DAYS_PER_WEEK = 7;

    const els = {
        form:           document.getElementById('patient-filter'),
        keyword:        document.getElementById('patient-keyword'),
        ward:           document.getElementById('patient-ward'),
        body:           document.getElementById('patient-body'),
        createOpen:     document.getElementById('patient-create-open'),
        createCancel:   document.getElementById('patient-create-cancel'),
        createForm:     document.getElementById('patient-create-form'),
        formTitle:      document.getElementById('patient-form-title'),
        formSubmit:     document.getElementById('patient-form-submit'),
        formMessage:    document.getElementById('patient-form-message'),
        patientId:      document.getElementById('patient-id'),
        createWard:     document.getElementById('patient-create-ward'),
        weeks:          document.getElementById('patient-weeks'),
        copayment:      document.getElementById('patient-copayment'),
        discountType:   document.getElementById('patient-discount-type'),
        discountValue:  document.getElementById('patient-discount-value'),
        packageGroups:  document.getElementById('patient-package-groups'),
        summaryBody:    document.getElementById('patient-package-summary-body'),
        subtotal:       document.getElementById('patient-subtotal'),
        discountDisp:   document.getElementById('patient-discount-display'),
        grandTotal:     document.getElementById('patient-grand-total')
    };

    let state = { patients: [], packages: [], rooms: [] };

    function init() {
        if (!els.form) return;
        els.form.addEventListener('submit', function (event) { event.preventDefault(); loadPatients(); });
        els.createOpen.addEventListener('click', function () { openForm(null); });
        els.createCancel.addEventListener('click', closeForm);
        els.createForm.addEventListener('submit', savePatient);
        els.body.addEventListener('dblclick', handleInlineEditStart);
        els.body.addEventListener('click', handleRowAction);

        // Live calc triggers
        [els.weeks, els.copayment, els.discountType, els.discountValue].forEach(function (el) {
            if (el) el.addEventListener('input', recalcSummary);
        });
        els.packageGroups.addEventListener('change', recalcSummary);

        loadMasters().then(loadPatients);
    }

    function loadMasters() {
        return Promise.all([
            fetch(API + 'api/settings', { headers: { Accept: 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : {}; }),
            fetch(API + 'api/treatment-packages', { headers: { Accept: 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : { items: [] }; }),
            fetch(API + 'api/treatment-rooms', { headers: { Accept: 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : { items: [] }; }),
            fetch(API + 'api/patients/doctors', { headers: { Accept: 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : { items: [] }; })
        ]).then(function (results) {
            const settings = results[0] || {};
            state.packages = (results[1] && results[1].items) || [];
            state.rooms    = (results[2] && results[2].items) || [];
            const wards = settings.wards || [];
            fillWardSelect(els.createWard, wards, '선택 안함');
            fillWardSelect(els.ward,       wards, '전체 병동');
            fillDoctorList((results[3] && results[3].items) || []);
            renderPackageGroups();
        });
    }

    function fillDoctorList(doctors) {
        const list = document.getElementById('patient-doctor-list');
        if (!list) return;
        list.innerHTML = doctors.map(function (d) {
            return '<option value="' + escapeHtml(d) + '"></option>';
        }).join('');
    }

    // 저장 후 신규 주치의를 추천 목록에 즉시 반영 (가벼운 단일 조회)
    function refreshDoctorList() {
        fetch(API + 'api/patients/doctors', { headers: { Accept: 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : { items: [] }; })
            .then(function (payload) { fillDoctorList(payload.items || []); })
            .catch(function () {});
    }

    function fillWardSelect(select, wards, placeholder) {
        if (!select) return;
        const current = select.value;
        select.innerHTML = '<option value="">' + escapeHtml(placeholder) + '</option>'
            + wards.map(function (w) {
                return '<option value="' + escapeHtml(w.name) + '">' + escapeHtml(w.name) + '</option>';
            }).join('');
        if (current && Array.prototype.some.call(select.options, function (o) { return o.value === current; })) {
            select.value = current;
        }
    }

    function renderPackageGroups() {
        const groups = {};
        state.packages.forEach(function (p) {
            const key = p.treatmentRoomId + '|' + p.categoryId;
            if (!groups[key]) {
                groups[key] = {
                    roomName: p.treatmentRoomName,
                    categoryName: p.categoryName,
                    items: []
                };
            }
            groups[key].items.push(p);
        });
        const html = Object.keys(groups).map(function (key) {
            const g = groups[key];
            const items = g.items.map(function (p) {
                const unit = p.billingUnit === 'DAY' ? '일' : '주';
                const abbrev = p.abbreviation
                    ? ' <code class="abbrev-chip">' + escapeHtml(p.abbreviation) + '</code>'
                    : '';
                return '<label class="package-item">' +
                    '<input type="checkbox" name="packageId" value="' + escapeHtml(p.id) + '" data-unit-price="' + Number(p.unitPrice || 0) + '" data-billing-unit="' + escapeHtml(p.billingUnit) + '" data-frequency="' + Number(p.frequency || 1) + '" data-category="' + escapeHtml(p.categoryName) + '">' +
                    '<span class="package-name">' + escapeHtml(p.packageName) + abbrev + '</span>' +
                    '<span class="package-meta">' + Number(p.unitPrice || 0).toLocaleString('ko-KR') + '원 · ' + unit + ' ' + escapeHtml(p.frequency) + '회</span>' +
                    '</label>';
            }).join('');
            return '<fieldset class="package-group">' +
                '<legend><strong>' + escapeHtml(g.roomName) + '</strong> · ' + escapeHtml(g.categoryName) + '</legend>' +
                '<div class="package-items">' + items + '</div>' +
                '</fieldset>';
        }).join('');
        els.packageGroups.innerHTML = html || '<p class="empty">등록된 카탈로그 항목이 없습니다. <a href="' + API + 'treatment-packages">카탈로그</a>에서 먼저 항목을 등록하세요.</p>';
    }

    function loadPatients() {
        const params = new URLSearchParams();
        if (els.keyword.value.trim()) params.set('keyword', els.keyword.value.trim());
        if (els.ward.value) params.set('ward', els.ward.value);
        fetch(API + 'api/patients?' + params.toString(), { headers: { 'Accept': 'application/json' } })
            .then(function (response) { if (!response.ok) throw new Error(); return response.json(); })
            .then(function (payload) {
                state.patients = payload.items || [];
                renderRows();
            })
            .catch(function () {
                els.body.innerHTML = '<tr><td colspan="9" class="empty">환자명부를 불러오지 못했습니다.</td></tr>';
            });
    }

    function renderRows() {
        if (!state.patients.length) {
            els.body.innerHTML = '<tr><td colspan="9" class="empty">조건에 맞는 환자가 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = state.patients.map(function (item) {
            const noteClass = 'memo-cell editable-cell' + (item.note ? ' memo-cell-flagged' : '');
            const total = computePatientTotal(item);
            return '<tr data-id="' + escapeHtml(item.id) + '">' +
                '<td class="editable-cell" data-field="name"><strong>' + escapeHtml(item.name) + '</strong><small>ID ' + escapeHtml(item.id) + '</small></td>' +
                '<td class="editable-cell" data-field="chartNo">' + escapeHtml(item.chartNo || '-') + '</td>' +
                '<td class="editable-cell" data-field="room">' + escapeHtml(item.room || '-') + '</td>' +
                '<td class="editable-cell" data-field="admissionDate" data-input-type="date">' + escapeHtml(item.admissionDate || '-') + '</td>' +
                '<td class="editable-cell" data-field="treatmentStartDate" data-input-type="date">' + escapeHtml(item.treatmentStartDate || '-') + '</td>' +
                '<td class="memo-cell">' + renderTreatmentSummary(item) + '</td>' +
                '<td class="' + noteClass + '" data-field="note">' + escapeHtml(item.note || '-') + '</td>' +
                '<td style="text-align:right"><strong>' + total.toLocaleString('ko-KR') + ' 원</strong></td>' +
                '<td><button type="button" class="secondary-button" data-action="edit">수정</button></td>' +
                '</tr>';
        }).join('');
    }

    function renderTreatmentSummary(patient) {
        const ids = (patient.prescriptionItemIds || []).map(Number);
        if (!ids.length) return '<span style="color:#9097a3">-</span>';
        const selected = new Set(ids);
        const labels = state.packages
            .filter(function (p) { return selected.has(Number(p.id)); })
            .map(function (p) {
                return p.abbreviation
                    ? '<code class="abbrev-chip">' + escapeHtml(p.abbreviation) + '</code>'
                    : escapeHtml(p.packageName);
            });
        if (!labels.length) return '<span style="color:#9097a3">(' + ids.length + '개 항목)</span>';
        return labels.join(' ');
    }

    // 본인부담 총액 계산은 공용 모듈(patient-cost.js)로 위임 — 동일 로직을 치료현황(병실별)과 공유.
    function computePatientTotal(patient) {
        return window.CtPatientCost.computePatientTotal(patient, state.packages);
    }

    function packageAmount(p, weeks) {
        return window.CtPatientCost.packageAmount(p, weeks);
    }

    function handleRowAction(event) {
        const button = event.target.closest('button[data-action]');
        if (!button) return;
        const row = event.target.closest('tr[data-id]');
        if (!row) return;
        if (button.dataset.action === 'edit') {
            const patient = state.patients.find(function (p) { return String(p.id) === row.dataset.id; });
            if (patient) openForm(patient);
        }
    }

    function openForm(patient) {
        els.createForm.hidden = false;
        els.createOpen.hidden = true;
        setMessage('');
        const isEdit = !!patient;
        els.formTitle.textContent = isEdit ? '환자 수정' : '환자 등록';
        els.formSubmit.textContent = isEdit ? '수정' : '저장';
        els.patientId.value = isEdit ? patient.id : '';
        document.getElementById('patient-name').value = isEdit ? patient.name || '' : '';
        document.getElementById('patient-chart-no').value = isEdit ? patient.chartNo || '' : '';
        document.getElementById('patient-room').value = isEdit ? patient.room || '' : '';
        els.createWard.value = isEdit ? (patient.ward || '') : '';
        document.getElementById('patient-doctor').value = isEdit ? (patient.attendingDoctor || '') : '';
        document.getElementById('patient-admission-date').value = isEdit ? (patient.admissionDate || '') : '';
        document.getElementById('patient-treatment-start-date').value = isEdit ? (patient.treatmentStartDate || '') : '';
        document.getElementById('patient-note').value = isEdit ? (patient.note || '') : '';
        els.weeks.value         = isEdit ? (patient.prescriptionWeeks  ?? 4) : 4;
        els.copayment.value     = isEdit ? (patient.copaymentRate || 100) : 100;
        els.discountType.value  = isEdit ? (patient.totalDiscountType  || 'NONE') : 'NONE';
        els.discountValue.value = isEdit ? (patient.totalDiscountValue ?? 0) : 0;

        const selected = new Set((isEdit && patient.prescriptionItemIds ? patient.prescriptionItemIds : []).map(String));
        els.packageGroups.querySelectorAll('input[name="packageId"]').forEach(function (cb) {
            cb.checked = selected.has(cb.value);
        });
        recalcSummary();
        document.getElementById('patient-name').focus();
    }

    function closeForm() {
        els.createForm.reset();
        els.createForm.hidden = true;
        els.createOpen.hidden = false;
        setMessage('');
        els.packageGroups.querySelectorAll('input[name="packageId"]').forEach(function (cb) {
            cb.checked = false;
        });
    }

    function recalcSummary() {
        const weeks = Number(els.weeks.value || 0);
        const copayment = Number(els.copayment.value || 0);
        const subtotalsByCategory = {};
        let subtotal = 0;
        els.packageGroups.querySelectorAll('input[name="packageId"]:checked').forEach(function (cb) {
            const unitPrice = Number(cb.dataset.unitPrice || 0);
            const frequency = Number(cb.dataset.frequency || 1);
            const billingUnit = cb.dataset.billingUnit;
            const category = cb.dataset.category;
            const amount = billingUnit === 'DAY'
                ? unitPrice * frequency * weeks * DAYS_PER_WEEK
                : unitPrice * frequency * weeks;
            subtotal += amount;
            subtotalsByCategory[category] = (subtotalsByCategory[category] || 0) + amount;
        });
        const afterCopay = subtotal * copayment / 100;
        let discount = 0;
        const dType = els.discountType.value;
        const dValue = Number(els.discountValue.value || 0);
        if (dType === 'PERCENT') discount = afterCopay * Math.min(100, dValue) / 100;
        else if (dType === 'AMOUNT') discount = Math.min(afterCopay, dValue);
        const grand = Math.max(0, Math.round(afterCopay - discount));

        els.summaryBody.innerHTML = Object.keys(subtotalsByCategory).map(function (cat) {
            return '<tr><td>' + escapeHtml(cat) + '</td><td style="text-align:right">' + Math.round(subtotalsByCategory[cat] * copayment / 100).toLocaleString('ko-KR') + ' 원</td></tr>';
        }).join('') || '<tr><td colspan="2" class="empty">선택된 항목이 없습니다.</td></tr>';
        els.subtotal.textContent = Math.round(afterCopay).toLocaleString('ko-KR') + ' 원';
        els.discountDisp.textContent = dType === 'NONE' || dValue === 0
            ? '-'
            : (dType === 'PERCENT' ? '-' + dValue + '%' : '-' + dValue.toLocaleString('ko-KR') + ' 원');
        els.grandTotal.innerHTML = '<strong>' + grand.toLocaleString('ko-KR') + ' 원</strong>';
    }

    function savePatient(event) {
        event.preventDefault();
        setMessage('');
        els.formSubmit.disabled = true;

        const id = els.patientId.value;
        const url    = id ? API + 'api/patients/' + encodeURIComponent(id) : API + 'api/patients';
        const method = id ? 'PUT' : 'POST';
        const selectedIds = Array.prototype.map.call(
            els.packageGroups.querySelectorAll('input[name="packageId"]:checked'),
            function (cb) { return Number(cb.value); });

        fetch(url, {
            method: method,
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name:                document.getElementById('patient-name').value.trim(),
                chartNo:             document.getElementById('patient-chart-no').value.trim(),
                room:                document.getElementById('patient-room').value.trim(),
                ward:                els.createWard.value,
                attendingDoctor:     document.getElementById('patient-doctor').value.trim(),
                admissionDate:       document.getElementById('patient-admission-date').value,
                treatmentStartDate:  document.getElementById('patient-treatment-start-date').value,
                treatmentInfo:       '',
                note:                document.getElementById('patient-note').value.trim(),
                prescriptionWeeks:   Number(els.weeks.value || 0),
                copaymentRate:       Number(els.copayment.value || 100),
                totalDiscountType:   els.discountType.value,
                totalDiscountValue:  Number(els.discountValue.value || 0),
                prescriptionItemIds: selectedIds
            })
        })
            .then(function (response) {
                return response.text().then(function (text) {
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok) throw new Error(payload.error || '저장에 실패했습니다.');
                    return payload;
                });
            })
            .then(function () {
                closeForm();
                loadPatients();
                refreshDoctorList();
            })
            .catch(function (error) {
                setMessage(error.message);
            })
            .finally(function () {
                els.formSubmit.disabled = false;
            });
    }

    function setMessage(message) {
        if (els.formMessage) els.formMessage.textContent = message;
    }

    function handleInlineEditStart(event) {
        const cell = event.target.closest('.editable-cell');
        if (!cell || cell.querySelector('input')) return;
        const row = cell.closest('tr[data-id]');
        if (!row) return;
        const field = cell.dataset.field;
        const originalValue = readCellValue(cell);
        const input = document.createElement('input');
        input.className = 'inline-edit-input';
        input.type = cell.dataset.inputType || 'text';
        input.value = originalValue;
        cell.innerHTML = '';
        cell.appendChild(input);
        input.focus();
        input.select();

        let finished = false;
        input.addEventListener('keydown', function (keyEvent) {
            if (keyEvent.key === 'Enter') { keyEvent.preventDefault(); input.blur(); }
            if (keyEvent.key === 'Escape') { keyEvent.preventDefault(); finished = true; loadPatients(); }
        });
        input.addEventListener('blur', function () {
            if (finished) return;
            finished = true;
            saveInlineField(row.dataset.id, field, input.value.trim());
        });
    }

    function readCellValue(cell) {
        const cloned = cell.cloneNode(true);
        const small = cloned.querySelector('small');
        if (small) small.remove();
        const value = cloned.textContent.trim();
        return value === '-' ? '' : value;
    }

    function saveInlineField(id, field, value) {
        fetch(API + 'api/patients/' + encodeURIComponent(id) + '/field', {
            method: 'PATCH',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ field: field, value: value })
        })
            .then(function (response) { if (!response.ok) throw new Error(); return response.json(); })
            .then(loadPatients)
            .catch(loadPatients);
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
