// 운전자 모바일 운행 기록 플로우.
// 기기 인식(/fleet/me) → (미등록 시) 등록 → 차량 상태(by-token) → 출발/도착.
// 계기판 입력은 "수동 입력 먼저 열고, OCR은 촬영 후 lazy 프리필" — 오프라인/OCR 실패에도 저장 가능.

import { prepareCapture, recognizeOdometer } from './ocr.js';

const app = document.getElementById('fleet-app');
const qrToken = (window.FLEET && window.FLEET.qrToken) || '';

const PURPOSES = [
    { code: 'BUSINESS', label: '업무' },
    { code: 'COMMUTE', label: '출퇴근' },
    { code: 'GENERAL', label: '일반' },
];

init();

async function init() {
    if (!qrToken) {
        return renderError('잘못된 접근입니다. QR을 다시 스캔해 주세요.');
    }
    try {
        const me = await getJson('/fleet/me');
        if (!me.registered) {
            return renderRegister();
        }
        return loadVehicle();
    } catch (e) {
        return renderError('연결에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    }
}

// ── 최초 기기 등록 ──────────────────────────────────────────────────────
async function renderRegister() {
    let items = [];
    try {
        items = (await getJson('/fleet/drivers')).items || [];
    } catch (e) {
        return renderError('운전자 목록을 불러오지 못했습니다.');
    }
    app.innerHTML = `
        <section class="fleet-card">
            <h2>본인 확인</h2>
            <p class="fleet-hint">이 기기를 사용할 운전자를 한 번만 선택하면 이후에는 자동으로 인식됩니다.</p>
            <div class="fleet-driver-list">
                ${items.map((d) => `
                    <button type="button" class="fleet-driver-item" data-id="${d.id}">
                        <strong>${escapeHtml(d.name)}</strong>
                        ${d.department ? `<span>${escapeHtml(d.department)}</span>` : ''}
                    </button>`).join('')}
            </div>
            ${items.length === 0 ? '<p class="fleet-empty">등록된 운전자가 없습니다. 관리자에게 문의하세요.</p>' : ''}
        </section>`;
    app.querySelectorAll('.fleet-driver-item').forEach((btn) => {
        btn.addEventListener('click', async () => {
            btn.disabled = true;
            try {
                await postJson('/fleet/devices/register', { driverId: Number(btn.dataset.id) });
                await loadVehicle();
            } catch (e) {
                btn.disabled = false;
                toast(e.message || '등록에 실패했습니다.');
            }
        });
    });
}

// ── 차량 상태 분기 ──────────────────────────────────────────────────────
async function loadVehicle() {
    const resp = await fetch('/fleet/vehicles/by-token/' + encodeURIComponent(qrToken), {
        headers: { Accept: 'application/json' },
    });
    if (resp.status === 401) {
        return renderRegister();
    }
    const data = await resp.json().catch(() => ({}));
    if (!resp.ok) {
        return renderError(data.message || '차량 정보를 불러오지 못했습니다.');
    }
    if (data.occupiedByOther) {
        return renderOccupied(data.vehicle);
    }
    if (data.ongoingTrip) {
        return renderArrive(data.vehicle, data.ongoingTrip);
    }
    return renderDepart(data.vehicle);
}

function vehicleHeader(vehicle) {
    return `
        <div class="fleet-vehicle">
            <strong>${escapeHtml(vehicle.plateNo)}</strong>
            ${vehicle.name ? `<span>${escapeHtml(vehicle.name)}</span>` : ''}
            <em>누적 ${Number(vehicle.currentOdometer).toLocaleString()} km</em>
        </div>`;
}

// ── 출발 ────────────────────────────────────────────────────────────────
function renderDepart(vehicle) {
    app.innerHTML = `
        <section class="fleet-card">
            ${vehicleHeader(vehicle)}
            <h2>출발</h2>
            <label class="fleet-field">
                <span>운행 목적</span>
                <select id="fleet-purpose">
                    ${PURPOSES.map((p) => `<option value="${p.code}">${p.label}</option>`).join('')}
                </select>
            </label>
            <label class="fleet-field">
                <span>메모 (선택)</span>
                <input type="text" id="fleet-memo" maxlength="200" placeholder="예: 고객사 방문">
            </label>
            <div id="fleet-capture"></div>
            <button type="button" id="fleet-submit" class="fleet-primary" disabled>출발 기록</button>
        </section>`;
    const capture = mountCapture(document.getElementById('fleet-capture'), '출발 계기판', document.getElementById('fleet-submit'));
    document.getElementById('fleet-submit').addEventListener('click', async () => {
        const state = capture.getState();
        const form = new FormData();
        form.append('vehicleId', vehicle.id);
        form.append('purpose', document.getElementById('fleet-purpose').value);
        form.append('purposeMemo', document.getElementById('fleet-memo').value);
        form.append('odometerStart', String(state.odometer));
        form.append('odometerStartSrc', state.src);
        form.append('photo', state.blob, 'odometer.jpg');
        await submitTrip('/fleet/trips/depart', form, capture, '출발이 기록되었습니다. 안전 운행하세요!');
    });
}

// ── 도착 ────────────────────────────────────────────────────────────────
function renderArrive(vehicle, ongoing) {
    app.innerHTML = `
        <section class="fleet-card">
            ${vehicleHeader(vehicle)}
            <h2>도착</h2>
            <p class="fleet-hint">출발 계기판: ${Number(ongoing.odometerStart).toLocaleString()} km</p>
            <div id="fleet-capture"></div>
            <button type="button" id="fleet-submit" class="fleet-primary" disabled>도착 기록</button>
        </section>`;
    const capture = mountCapture(document.getElementById('fleet-capture'), '도착 계기판', document.getElementById('fleet-submit'));
    document.getElementById('fleet-submit').addEventListener('click', async () => {
        const state = capture.getState();
        const form = new FormData();
        form.append('tripId', ongoing.tripId);
        form.append('odometerEnd', String(state.odometer));
        form.append('odometerEndSrc', state.src);
        form.append('photo', state.blob, 'odometer.jpg');
        await submitTrip('/fleet/trips/arrive', form, capture, '도착이 기록되었습니다. 수고하셨습니다!');
    });
}

function renderOccupied(vehicle) {
    app.innerHTML = `
        <section class="fleet-card">
            ${vehicleHeader(vehicle)}
            <p class="fleet-empty">다른 운전자가 사용 중입니다.<br>운행이 끝나면 다시 시도해 주세요.</p>
        </section>`;
}

// ── 계기판 캡처 위젯 ─────────────────────────────────────────────────────
// 수동 입력을 즉시 열고, 촬영 후 OCR로 프리필한다. 사진이 있으면 OCR 실패해도 저장 가능.
function mountCapture(container, label, submitBtn) {
    container.innerHTML = `
        <div class="fleet-capture">
            <span class="fleet-field-label">${label} (사진 필수)</span>
            <input type="file" id="fleet-photo" accept="image/*" capture="environment">
            <img id="fleet-preview" class="fleet-preview" alt="" hidden>
            <p id="fleet-ocr-status" class="fleet-ocr-status" hidden></p>
            <label class="fleet-field">
                <span>계기판(km)</span>
                <input type="number" id="fleet-odometer" inputmode="numeric" min="0" placeholder="숫자 직접 입력 가능">
            </label>
        </div>`;

    const fileInput = container.querySelector('#fleet-photo');
    const preview = container.querySelector('#fleet-preview');
    const status = container.querySelector('#fleet-ocr-status');
    const odometer = container.querySelector('#fleet-odometer');

    const state = { blob: null, src: 'MANUAL', userEdited: false };

    odometer.addEventListener('input', () => {
        state.userEdited = true;
        state.src = 'MANUAL';
        refreshSubmit();
    });

    fileInput.addEventListener('change', async () => {
        const file = fileInput.files && fileInput.files[0];
        if (!file) {
            return;
        }
        preview.src = URL.createObjectURL(file);
        preview.hidden = false;
        // 원본을 즉시 업로드본으로 세팅 → 처리 전에도 저장 가능(오프라인/처리실패 대비)
        state.blob = file;
        refreshSubmit();

        status.hidden = false;
        status.textContent = '계기판 인식 중… (직접 입력해도 됩니다)';
        try {
            const { uploadBlob, ocrCanvas } = await prepareCapture(file);
            state.blob = uploadBlob; // 축소본으로 교체(용량↓)
            const digits = await recognizeOdometer(ocrCanvas);
            if (digits && !state.userEdited) {
                odometer.value = digits;
                state.src = 'OCR';
                status.textContent = '인식됨 — 값을 확인하고 필요하면 수정하세요.';
            } else {
                status.textContent = digits ? '인식됨 — 입력값을 유지합니다.' : '자동 인식 실패 — 직접 입력해 주세요.';
            }
        } catch (e) {
            status.textContent = '자동 인식 실패 — 직접 입력해 주세요.';
        }
        refreshSubmit();
    });

    function refreshSubmit() {
        const ready = !!state.blob && odometer.value !== '' && Number(odometer.value) >= 0;
        submitBtn.disabled = !ready;
    }

    return {
        getState() {
            return { blob: state.blob, odometer: Number(odometer.value), src: state.src };
        },
        setBusy(busy) {
            submitBtn.disabled = busy;
        },
    };
}

async function submitTrip(url, form, capture, successMessage) {
    capture.setBusy(true);
    try {
        const resp = await fetch(url, { method: 'POST', body: form });
        const data = await resp.json().catch(() => ({}));
        if (!resp.ok) {
            capture.setBusy(false);
            return toast(data.message || '저장에 실패했습니다.');
        }
        renderDone(successMessage, data);
    } catch (e) {
        capture.setBusy(false);
        toast('네트워크 오류로 저장하지 못했습니다. 다시 시도해 주세요.');
    }
}

function renderDone(message, data) {
    const distance = data && data.distance != null ? `<p class="fleet-hint">주행거리 ${Number(data.distance).toLocaleString()} km</p>` : '';
    const overLimit = data && data.overLimit ? '<p class="fleet-warn">주행거리가 상한을 초과했습니다. 관리자 확인이 필요할 수 있습니다.</p>' : '';
    app.innerHTML = `
        <section class="fleet-card fleet-done">
            <div class="fleet-check">✓</div>
            <h2>${escapeHtml(message)}</h2>
            ${distance}
            ${overLimit}
        </section>`;
}

function renderError(message) {
    app.innerHTML = `<section class="fleet-card"><p class="fleet-empty">${escapeHtml(message)}</p></section>`;
}

// ── 유틸 ────────────────────────────────────────────────────────────────
async function getJson(url) {
    const resp = await fetch(url, { headers: { Accept: 'application/json' } });
    if (!resp.ok) {
        throw new Error('요청 실패');
    }
    return resp.json();
}

async function postJson(url, body) {
    const resp = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(body),
    });
    const data = await resp.json().catch(() => ({}));
    if (!resp.ok) {
        throw new Error(data.message || '요청 실패');
    }
    return data;
}

let toastTimer = null;
function toast(message) {
    let el = document.getElementById('fleet-toast');
    if (!el) {
        el = document.createElement('div');
        el.id = 'fleet-toast';
        el.className = 'fleet-toast';
        document.body.appendChild(el);
    }
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 3200);
}

function escapeHtml(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
