(function () {
    const els = {
        form: document.getElementById('patient-filter'),
        keyword: document.getElementById('patient-keyword'),
        ward: document.getElementById('patient-ward'),
        body: document.getElementById('patient-body'),
        createOpen: document.getElementById('patient-create-open'),
        createCancel: document.getElementById('patient-create-cancel'),
        createForm: document.getElementById('patient-create-form'),
        formMessage: document.getElementById('patient-form-message')
    };

    function init() {
        if (!els.form) return;
        els.form.addEventListener('submit', function (event) {
            event.preventDefault();
            loadPatients();
        });
        if (els.createOpen && els.createForm) {
            els.createOpen.addEventListener('click', openCreateForm);
        }
        if (els.createCancel && els.createForm) {
            els.createCancel.addEventListener('click', closeCreateForm);
        }
        if (els.createForm) {
            els.createForm.addEventListener('submit', createPatient);
        }
        els.body.addEventListener('dblclick', handleInlineEditStart);
        loadPatients();
    }

    function loadPatients() {
        const params = new URLSearchParams();
        if (els.keyword.value.trim()) params.set('keyword', els.keyword.value.trim());
        if (els.ward.value) params.set('ward', els.ward.value);
        fetch('/api/patients?' + params.toString(), { headers: { 'Accept': 'application/json' } })
            .then(function (response) {
                if (!response.ok) throw new Error('patient-load-failed');
                return response.json();
            })
            .then(function (payload) {
                renderRows(payload.items || []);
            })
            .catch(function () {
                els.body.innerHTML = '<tr><td colspan="7" class="empty">환자명부를 불러오지 못했습니다.</td></tr>';
            });
    }

    function openCreateForm() {
        els.createForm.hidden = false;
        els.createOpen.hidden = true;
        setMessage('');
        const nameInput = document.getElementById('patient-name');
        if (nameInput) nameInput.focus();
    }

    function closeCreateForm() {
        els.createForm.reset();
        els.createForm.hidden = true;
        els.createOpen.hidden = false;
        setMessage('');
    }

    function createPatient(event) {
        event.preventDefault();
        setMessage('');
        const submitButton = els.createForm.querySelector('button[type="submit"]');
        if (submitButton) submitButton.disabled = true;

        fetch('/api/patients', {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(readCreateForm())
        })
            .then(function (response) {
                return response.json().then(function (payload) {
                    if (!response.ok) {
                        throw new Error(payload.error || 'patient-create-failed');
                    }
                    return payload;
                });
            })
            .then(function () {
                closeCreateForm();
                loadPatients();
            })
            .catch(function (error) {
                setMessage(error.message === 'patient-create-failed'
                    ? '환자를 등록하지 못했습니다.'
                    : error.message);
            })
            .finally(function () {
                if (submitButton) submitButton.disabled = false;
            });
    }

    function readCreateForm() {
        const data = new FormData(els.createForm);
        return {
            name: readFormValue(data, 'name'),
            chartNo: readFormValue(data, 'chartNo'),
            room: readFormValue(data, 'room'),
            ward: readFormValue(data, 'ward'),
            admissionDate: readFormValue(data, 'admissionDate'),
            dischargeDate: readFormValue(data, 'dischargeDate'),
            treatmentInfo: readFormValue(data, 'treatmentInfo'),
            note: readFormValue(data, 'note')
        };
    }

    function readFormValue(data, key) {
        return String(data.get(key) || '').trim();
    }

    function setMessage(message) {
        if (els.formMessage) {
            els.formMessage.textContent = message;
        }
    }

    function renderRows(items) {
        if (items.length === 0) {
            els.body.innerHTML = '<tr><td colspan="7" class="empty">조건에 맞는 환자가 없습니다.</td></tr>';
            return;
        }
        els.body.innerHTML = items.map(function (item) {
            return '<tr data-id="' + escapeHtml(item.id) + '">' +
                '<td class="editable-cell" data-field="name"><strong>' + escapeHtml(item.name) + '</strong><small>ID ' + escapeHtml(item.id) + '</small></td>' +
                '<td class="editable-cell" data-field="chartNo">' + escapeHtml(item.chartNo || '-') + '</td>' +
                '<td class="editable-cell" data-field="room">' + escapeHtml(item.room || '-') + '</td>' +
                '<td class="editable-cell" data-field="admissionDate" data-input-type="date">' + escapeHtml(item.admissionDate || '-') + '</td>' +
                '<td class="editable-cell" data-field="dischargeDate" data-input-type="date">' + escapeHtml(item.dischargeDate || '-') + '</td>' +
                '<td class="memo-cell editable-cell" data-field="treatmentInfo">' + escapeHtml(item.treatmentInfo || '-') + '</td>' +
                '<td class="memo-cell editable-cell" data-field="note">' + escapeHtml(item.note || '-') + '</td>' +
                '</tr>';
        }).join('');
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
            if (keyEvent.key === 'Enter') {
                keyEvent.preventDefault();
                input.blur();
            }
            if (keyEvent.key === 'Escape') {
                keyEvent.preventDefault();
                finished = true;
                loadPatients();
            }
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
        fetch('/api/patients/' + encodeURIComponent(id) + '/field', {
            method: 'PATCH',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ field: field, value: value })
        })
            .then(function (response) {
                if (!response.ok) throw new Error('patient-update-failed');
                return response.json();
            })
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
