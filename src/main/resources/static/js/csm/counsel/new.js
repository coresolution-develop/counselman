/* =========================
 * CSM Counsel — SMS/예약/모달 JS (refactored)
 * ========================= */

const MAX_BYTES_SMS = 90;        // 단문 기준
const MAX_BYTES_MMS = 2000;      // 장문 기준(LMS)
const AD_HEADER = "(광고)";
const AD_FOOTER = "수신거부: 0808806400";
const APP_CTX = (() => {
  const normalize = (value) => {
    if (!value || value === '/') return '';
    return value.endsWith('/') ? value.slice(0, -1) : value;
  };

  const metaCtx = document.querySelector('meta[name="app-context-path"]')?.getAttribute('content');
  if (metaCtx != null) {
    return normalize(metaCtx.trim());
  }

  const path = window.location.pathname || '';
  const markers = [
    '/counsel/',
    '/core/',
    '/login',
    '/findpwd',
    '/ResetPwd',
    '/admin',
    '/setting',
    '/smsSetting',
    '/statistics'
  ];

  for (const marker of markers) {
    const idx = path.indexOf(marker);
    if (idx >= 0) {
      return normalize(path.substring(0, idx));
    }
  }

  return '';
})();

/* -------------------------
 * Utilities (Global)
 * ------------------------- */
function pad2(n){ return n.toString().padStart(2,'0'); }

/** 템플릿 inline onsubmit 핸들러 호환 */
function handleSubmit(event) {
  if (typeof window.validateCounselSubmit === 'function') {
    return window.validateCounselSubmit(event);
  }
  return true;
}

/** ISO 문자열 또는 unix(sec/ms) → "YYYY-MM-DD HH:mm" */
function formatDate(input) {
  let d;
  if (typeof input === 'number') {
    d = (input.toString().length === 10) ? new Date(input * 1000) : new Date(input);
  } else {
    d = new Date(input);
  }
  if (isNaN(d)) return String(input);
  return `${d.getFullYear()}-${pad2(d.getMonth()+1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

/** 문자열 바이트 계산(한글 2바이트) */
function calculateBytes(text) {
  let byteCount = 0;
  for (let i = 0; i < text.length; i++) {
    byteCount += text.charCodeAt(i) > 127 ? 2 : 1;
  }
  return byteCount;
}

/** 전화번호 포맷팅 (전역 1회 정의) */
function oninputPhone(target) {
  let input = target.value.replace(/[^0-9]/g, '');
  let formattedInput = '';
  if (input.startsWith('02')) {
    if (input.length <= 2) formattedInput = input;
    else if (input.length <= 5) formattedInput = input.replace(/(\d{2})(\d{1,3})/, '$1-$2');
    else if (input.length <= 9) formattedInput = input.replace(/(\d{2})(\d{3})(\d{0,4})/, '$1-$2-$3');
    else formattedInput = input.replace(/(\d{2})(\d{4})(\d{4})/, '$1-$2-$3');
  } else if (input.length === 8) {
    formattedInput = input.replace(/(\d{4})(\d{4})/, '$1-$2');
  } else {
    if (input.length <= 3) formattedInput = input;
    else if (input.length <= 6) formattedInput = input.replace(/(\d{3})(\d{1,3})/, '$1-$2');
    else if (input.length <= 10) formattedInput = input.replace(/(\d{3})(\d{3})(\d{0,4})/, '$1-$2-$3');
    else formattedInput = input.replace(/(\d{3})(\d{4})(\d{4})/, '$1-$2-$3');
  }
  target.value = formattedInput;
}

/** 하이픈 지우기 백스페이스 UX */
function handleBackspace(event) {
  if (event.key === 'Backspace') {
    const target = event.target;
    const { selectionStart: s, selectionEnd: e } = target;
    if (s === e && s > 0 && target.value[s - 1] === '-') {
      event.preventDefault();
      target.value = target.value.slice(0, s - 1) + target.value.slice(s);
      target.setSelectionRange(s - 1, s - 1);
    }
  }
}

/** YYYY-MM-DD 자동 하이픈/유효성/만나이 */
const oninputDate = (target) => {
  let val = target.value.replace(/\D/g, '');
  let result = '';
  if (val.length < 6) result = val;
  else if (val.length < 8) result = `${val.slice(0,4)}-${val.slice(4)}`;
  else result = `${val.slice(0,4)}-${val.slice(4,6)}-${val.slice(6,8)}`;
  target.value = result;

  if (result.length === 10 && checkValidDate(result)) {
    const age = calculateAge(result);
    const ageInput = document.getElementById('cs_col_04');
    if (ageInput) ageInput.value = age;
  } else {
    const ageInput = document.getElementById('cs_col_04');
    if (ageInput) ageInput.value = '';
  }
};
const checkValidDate = (value) => {
  try {
    const [y, m, d] = value.split('-').map(v => parseInt(v,10));
    const dt = new Date(y, m-1, d);
    return !isNaN(dt) && dt.getFullYear() === y && dt.getMonth() === m-1 && dt.getDate() === d;
  } catch { return false; }
};
const calculateAge = (birthDateString) => {
  const birth = new Date(birthDateString);
  if (isNaN(birth)) return 0;
  const now = new Date();
  let age = now.getFullYear() - birth.getFullYear();
  const m = now.getMonth() - birth.getMonth();
  const dd = now.getDate() - birth.getDate();
  if (m < 0 || (m === 0 && dd < 0)) age--;
  return Math.max(0, age);
};

/* -------------------------
 * DOM Ready
 * ------------------------- */
document.addEventListener('DOMContentLoaded', function () {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || '';
  if (csrfToken && csrfHeader && typeof $ !== 'undefined' && $.ajaxSetup) {
    $.ajaxSetup({
      beforeSend(xhr) {
        xhr.setRequestHeader(csrfHeader, csrfToken);
      }
    });
  }

  // Enter 키로 의도치 않게 폼 submit(페이지 이동) 되는 것을 방지
  const counselForm = document.getElementById('counselForm');
  if (counselForm) {
    counselForm.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter') return;
      const target = e.target;
      if (!target) return;
      const tag = (target.tagName || '').toUpperCase();
      const type = (target.type || '').toLowerCase();
      // 텍스트영역 줄바꿈/명시 submit 버튼은 허용
      if (tag === 'TEXTAREA' || type === 'submit') return;
      e.preventDefault();
    });
  }

  /* -------- 예약/미예약 보이기 -------- */
  function toggleResultFields(value) {
    const reservationFields = $('#reservationFields');
    const noAdmissionReason = $('#noAdmissionReason');
    const roomBoardSection = document.getElementById('roomBoardSection');

    if (value === '입원예약') {
      reservationFields.css('display', 'flex');
      noAdmissionReason.css('display', 'none');
      if (roomBoardSection) roomBoardSection.style.display = '';
    } else if (value === '입원안함') {
      reservationFields.css('display', 'none');
      noAdmissionReason.css('display', 'flex');
      if (roomBoardSection) roomBoardSection.style.display = 'none';
    } else {
      reservationFields.css('display', 'none');
      noAdmissionReason.css('display', 'none');
      if (roomBoardSection) roomBoardSection.style.display = '';
    }
  }

  const resultSelect = document.getElementById('cs_col_19');
  const admissionPledgeLauncher = document.getElementById('admissionPledgeLauncher');
  const admissionPledgeModuleEnabled = (document.getElementById('module_admission_pledge_enabled')?.value || 'Y') === 'Y';
  const openAdmissionPledgeBtn = document.getElementById('openAdmissionPledgeBtn');
  const openRoomBoardBtn = document.getElementById('openRoomBoardBtn');
  const selectedRoomStatus = document.getElementById('selectedRoomStatus');
  const admissionPledgeStatus = document.getElementById('admissionPledgeStatus');
  const admissionPledgeRequiredInput = document.getElementById('admission_pledge_required');
  const admissionAgreedYnInput = document.getElementById('admission_agreed_yn');
  const admissionSignerNameInput = document.getElementById('admission_signer_name');
  const admissionSignerRelationInput = document.getElementById('admission_signer_relation');
  const admissionSignedAtInput = document.getElementById('admission_signed_at');
  const admissionPledgeTextInput = document.getElementById('admission_pledge_text');
  const admissionSignatureInput = document.getElementById('admission_signature_data');
  const admissionPageInkInput = document.getElementById('admission_page_ink_data');
  const admissionGuardianNameInput = document.getElementById('admission_guardian_name');
  const admissionGuardianRelationInput = document.getElementById('admission_guardian_relation');
  const admissionGuardianAddrInput = document.getElementById('admission_guardian_addr');
  const admissionGuardianPhoneInput = document.getElementById('admission_guardian_phone');
  const admissionGuardianCostYnInput = document.getElementById('admission_guardian_cost_yn');
  const admissionSubGuardianNameInput = document.getElementById('admission_sub_guardian_name');
  const admissionSubGuardianRelationInput = document.getElementById('admission_sub_guardian_relation');
  const admissionSubGuardianAddrInput = document.getElementById('admission_sub_guardian_addr');
  const admissionSubGuardianPhoneInput = document.getElementById('admission_sub_guardian_phone');
  const admissionSubGuardianCostYnInput = document.getElementById('admission_sub_guardian_cost_yn');
  const currentCsIdxRaw = String(document.querySelector('input[name="cs_idx"]')?.value || '').trim();
  const currentCsIdx = /^\d+$/.test(currentCsIdxRaw) ? Number(currentCsIdxRaw) : 0;
  const defaultAdmissionPledgeText = '본인은 입원 연계 및 상담을 위해 제공한 정보가 병원 입원 진행에 활용되는 것에 동의합니다. 또한 상담 과정에서 안내받은 내용을 확인하였으며, 안내된 절차에 따라 성실히 협조할 것을 서약합니다.';

  const nowDateTime = () => {
    const now = new Date();
    return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())} ${pad2(now.getHours())}:${pad2(now.getMinutes())}:${pad2(now.getSeconds())}`;
  };
  const requiresAdmissionPledge = (value) => value === '입원예약' || value === '입원완료';
  const isPngDataUrl = (value) => /^data:image\/png;base64,/.test(String(value || '').trim());
  const extractAdmissionSignatureBundle = (raw) => {
    const text = String(raw || '').trim();
    if (!text) return { primary: '', guardian: '', sub_guardian: '' };
    if (isPngDataUrl(text)) {
      return { primary: text, guardian: '', sub_guardian: '' };
    }
    try {
      const parsed = JSON.parse(text);
      if (!parsed || typeof parsed !== 'object') return { primary: '', guardian: '', sub_guardian: '' };
      const readFirst = (...candidates) => {
        for (const candidate of candidates) {
          const data = String(candidate || '').trim();
          if (isPngDataUrl(data)) return data;
        }
        return '';
      };
      return {
        primary: readFirst(parsed.primary, parsed.main, parsed.signer, parsed.final_signature, parsed.signature),
        guardian: readFirst(parsed.guardian, parsed.guardian_signature, parsed.primary_guardian),
        sub_guardian: readFirst(parsed.sub_guardian, parsed.subGuardian, parsed.sub_guardian_signature, parsed.secondary_guardian)
      };
    } catch (_) {
      return { primary: '', guardian: '', sub_guardian: '' };
    }
  };
  const extractPrimaryAdmissionSignature = (raw) => extractAdmissionSignatureBundle(raw).primary;
  const readCurrentGuardianEntry = (index) => {
    const entry = Array.from(document.querySelectorAll('.guardian-entry'))[index];
    return {
      name: entry?.querySelector("input[name='cs_col_13[]']")?.value?.trim() || '',
      relation: entry?.querySelector("input[name='cs_col_14[]']")?.value?.trim() || '',
      phone: entry?.querySelector("input[name='cs_col_15[]']")?.value?.trim() || ''
    };
  };
  const isAdmissionPledgeComplete = () => {
    if ((admissionPledgeRequiredInput?.value || 'N') !== 'Y') {
      return true;
    }
    return (admissionAgreedYnInput?.value || 'N') === 'Y';
  };
  const setAdmissionPledgeStatus = () => {
    if (!admissionPledgeStatus) return;
    if ((admissionPledgeRequiredInput?.value || 'N') !== 'Y') {
      admissionPledgeStatus.textContent = '해당 없음';
      return;
    }
    admissionPledgeStatus.textContent = isAdmissionPledgeComplete() ? '작성 완료' : '미작성';
  };
  const payloadCsIdx = (payload) => {
    const raw = String(payload?.cs_idx ?? '').trim();
    return /^\d+$/.test(raw) ? Number(raw) : 0;
  };
  const canApplyAdmissionPledgePayload = (payload) => {
    const pIdx = payloadCsIdx(payload);
    if (currentCsIdx > 0) {
      return pIdx > 0 && pIdx === currentCsIdx;
    }
    return pIdx === 0;
  };
  const toggleAdmissionPledgeLauncher = (value) => {
    const required = admissionPledgeModuleEnabled && requiresAdmissionPledge(value);
    if (admissionPledgeRequiredInput) {
      admissionPledgeRequiredInput.value = required ? 'Y' : 'N';
    }
    if (admissionPledgeLauncher) {
      admissionPledgeLauncher.style.display = required ? '' : 'none';
    }
    setAdmissionPledgeStatus();
  };
  const applyAdmissionPledgePayload = (payload) => {
    if (!admissionPledgeModuleEnabled) return;
    if (!payload || typeof payload !== 'object') return;
    if (!canApplyAdmissionPledgePayload(payload)) return;
    if (admissionPledgeRequiredInput) admissionPledgeRequiredInput.value = 'Y';
    if (admissionAgreedYnInput) admissionAgreedYnInput.value = payload.agreed_yn === 'Y' ? 'Y' : 'N';
    if (admissionSignerNameInput) admissionSignerNameInput.value = String(payload.signer_name || '').trim();
    if (admissionSignerRelationInput) admissionSignerRelationInput.value = String(payload.signer_relation || '').trim();
    if (admissionSignedAtInput) admissionSignedAtInput.value = String(payload.signed_at || '').trim();
    if (admissionPledgeTextInput) admissionPledgeTextInput.value = String(payload.pledge_text || '').trim();
    if (admissionSignatureInput) admissionSignatureInput.value = String(payload.signature_data || '').trim();
    if (admissionPageInkInput) admissionPageInkInput.value = String(payload.page_ink_data || '').trim();
    if (admissionGuardianNameInput) admissionGuardianNameInput.value = String(payload.guardian_name || '').trim();
    if (admissionGuardianRelationInput) admissionGuardianRelationInput.value = String(payload.guardian_relation || '').trim();
    if (admissionGuardianAddrInput) admissionGuardianAddrInput.value = String(payload.guardian_addr || '').trim();
    if (admissionGuardianPhoneInput) admissionGuardianPhoneInput.value = String(payload.guardian_phone || '').trim();
    if (admissionGuardianCostYnInput) admissionGuardianCostYnInput.value = payload.guardian_cost_yn === 'Y' ? 'Y' : 'N';
    if (admissionSubGuardianNameInput) admissionSubGuardianNameInput.value = String(payload.sub_guardian_name || '').trim();
    if (admissionSubGuardianRelationInput) admissionSubGuardianRelationInput.value = String(payload.sub_guardian_relation || '').trim();
    if (admissionSubGuardianAddrInput) admissionSubGuardianAddrInput.value = String(payload.sub_guardian_addr || '').trim();
    if (admissionSubGuardianPhoneInput) admissionSubGuardianPhoneInput.value = String(payload.sub_guardian_phone || '').trim();
    if (admissionSubGuardianCostYnInput) admissionSubGuardianCostYnInput.value = payload.sub_guardian_cost_yn === 'Y' ? 'Y' : 'N';
    setAdmissionPledgeStatus();
  };

  if (resultSelect) {
    toggleResultFields(resultSelect.value);
    toggleAdmissionPledgeLauncher(resultSelect.value);
    resultSelect.addEventListener('change', function () {
      toggleResultFields(this.value);
      toggleAdmissionPledgeLauncher(this.value);
      if (requiresAdmissionPledge(this.value) && !isAdmissionPledgeComplete()) {
        openAdmissionPledgeBtn?.click();
      }
    });
  }
  setAdmissionPledgeStatus();

  /* -------- #form-reset : 방법·결과·상담내용 초기화 -------- */
  const formResetBtn = document.getElementById('form-reset');
  if (formResetBtn) {
    formResetBtn.addEventListener('click', function () {
      // 방법 초기화
      const methodSel = document.getElementById('cs_col_18');
      if (methodSel) methodSel.value = '';

      // 결과 초기화 + 연동 UI 리셋
      if (resultSelect) {
        resultSelect.value = '';
        toggleResultFields('');
        toggleAdmissionPledgeLauncher('');
      }

      // 상담내용 초기화
      const contentTA = document.getElementById('cs_col_32');
      if (contentTA) contentTA.value = '';
    });
  }

  const pendingPledge = sessionStorage.getItem('csm-admission-pledge-return');
  if (pendingPledge) {
    try {
      applyAdmissionPledgePayload(JSON.parse(pendingPledge));
    } catch (e) {
      // no-op
    }
    sessionStorage.removeItem('csm-admission-pledge-return');
  }

  window.addEventListener('message', function (event) {
    const message = event?.data;
    if (!message) return;
    if (message.type === 'csm-admission-pledge') {
      applyAdmissionPledgePayload(message.payload || {});
      return;
    }
    if (message.type === 'csm-room-board-select') {
      const roomInput = document.getElementById('cs_col_38');
      const roomName = String(message.roomName || '').trim();
      if (roomInput) {
        roomInput.value = roomName;
      }
      if (selectedRoomStatus) {
        selectedRoomStatus.textContent = roomName ? `선택 병실: ${roomName}` : '선택된 병실 없음';
      }
    }
  });

  if (openAdmissionPledgeBtn) {
    openAdmissionPledgeBtn.addEventListener('click', function () {
      const resultValue = String(resultSelect?.value || '').trim();
      if (!requiresAdmissionPledge(resultValue)) {
        alert('입원완료 또는 입원예약일 때만 입원서약서를 작성할 수 있습니다.');
        return;
      }

      const draftKey = `csm-admission-pledge-draft-${Date.now()}`;
      const currentGuardian = readCurrentGuardianEntry(0);
      const currentSubGuardian = readCurrentGuardianEntry(1);
      const draftPayload = {
        cs_idx: currentCsIdx > 0 ? currentCsIdx : 0,
        agreed_yn: admissionAgreedYnInput?.value || 'N',
        signer_name: admissionSignerNameInput?.value || '',
        signer_relation: admissionSignerRelationInput?.value || '',
        guardian_name: admissionGuardianNameInput?.value || currentGuardian.name,
        guardian_relation: admissionGuardianRelationInput?.value || currentGuardian.relation,
        guardian_addr: admissionGuardianAddrInput?.value || '',
        guardian_phone: admissionGuardianPhoneInput?.value || currentGuardian.phone,
        guardian_cost_yn: admissionGuardianCostYnInput?.value || 'N',
        sub_guardian_name: admissionSubGuardianNameInput?.value || currentSubGuardian.name,
        sub_guardian_relation: admissionSubGuardianRelationInput?.value || currentSubGuardian.relation,
        sub_guardian_addr: admissionSubGuardianAddrInput?.value || '',
        sub_guardian_phone: admissionSubGuardianPhoneInput?.value || currentSubGuardian.phone,
        sub_guardian_cost_yn: admissionSubGuardianCostYnInput?.value || 'N',
        signed_at: admissionSignedAtInput?.value || '',
        pledge_text: admissionPledgeTextInput?.value || defaultAdmissionPledgeText,
        signature_data: admissionSignatureInput?.value || '',
        page_ink_data: admissionPageInkInput?.value || ''
      };
      sessionStorage.setItem(draftKey, JSON.stringify(draftPayload));

      const params = new URLSearchParams();
      const csIdx = String(document.querySelector('input[name="cs_idx"]')?.value || '').trim();
      if (/^\d+$/.test(csIdx)) params.set('csIdx', csIdx);
      params.set('draftKey', draftKey);
      params.set('patientName', String(document.getElementById('cs_col_01')?.value || '').trim());
      params.set('gender', String(document.getElementById('cs_col_02')?.value || '').trim());
      params.set('birth', String(document.getElementById('cs_col_03')?.value || '').trim());
      params.set('chartNo', String(document.getElementById('cs_col_40')?.value || '').trim());
      params.set('room', String(document.getElementById('cs_col_38')?.value || '').trim());
      params.set('phone', String(document.querySelector('input[name=\"cs_col_15[]\"]')?.value || '').trim());
      params.set('returnUrl', `${window.location.pathname}${window.location.search}`);

      const url = `${APP_CTX}/counsel/admission-pledge?${params.toString()}`;
      const popup = window.open(url, 'csmAdmissionPledge', 'width=1280,height=900,resizable=yes,scrollbars=yes');
      if (!popup) {
        window.location.href = url;
      }
    });
  }

  if (openRoomBoardBtn) {
    openRoomBoardBtn.addEventListener('click', function () {
      const params = new URLSearchParams();
      params.set('popup', '1');
      params.set('patientName', String(document.getElementById('cs_col_01')?.value || '').trim());
      params.set('gender', String(document.getElementById('cs_col_02')?.value || '').trim());
      params.set('expectedDate', String(document.getElementById('cs_col_21')?.value || '').trim());
      const url = `${APP_CTX}/room-board?${params.toString()}`;
      const popup = window.open(url, 'csmRoomBoard', 'width=1500,height=900,resizable=yes,scrollbars=yes');
      if (!popup) {
        window.location.href = url;
      }
    });
  }

  window.validateCounselSubmit = function () {
    const patientNameInput = document.getElementById('cs_col_01');
    const patientName = String(patientNameInput?.value || '').trim();
    if (patientNameInput) {
      patientNameInput.value = patientName;
    }
    if (!patientName) {
      alert('환자명을 입력해 주세요.');
      patientNameInput?.focus();
      return false;
    }

    if ((admissionPledgeRequiredInput?.value || 'N') !== 'Y') {
      return true;
    }
    if (!isAdmissionPledgeComplete()) {
      alert('입원서약서 페이지에서 서명 완료 후 저장해 주세요.');
      openAdmissionPledgeBtn?.focus();
      return false;
    }
    if (admissionSignerNameInput && !String(admissionSignerNameInput.value || '').trim()) {
      admissionSignerNameInput.value = String(document.getElementById('cs_col_01')?.value || '').trim();
    }
    if (admissionSignedAtInput && !admissionSignedAtInput.value) {
      admissionSignedAtInput.value = nowDateTime();
    }
    if (admissionSignerRelationInput && !String(admissionSignerRelationInput.value || '').trim()) {
      admissionSignerRelationInput.value = '본인';
    }
    if (admissionPledgeTextInput && !String(admissionPledgeTextInput.value || '').trim()) {
      admissionPledgeTextInput.value = defaultAdmissionPledgeText;
    }
    return true;
  };

  /* -------- flatpickr -------- */
  if (typeof flatpickr === 'function') {
    flatpickr('.purchase_date', {
      locale: 'ko',
      dateFormat: 'Y-m-d',
      allowInput: true,
      onOpen(_, __, instance) {
        setTimeout(() => {
          const r = instance.input.getBoundingClientRect();
          instance.calendarContainer.style.top = (r.bottom + window.scrollY) + 'px';
          instance.calendarContainer.style.left = (r.left + window.scrollX) + 'px';
        }, 50);
      }
    });

    flatpickr('.purchase_time', {
      locale: 'ko',
      enableTime: true,
      noCalendar: true,
      dateFormat: 'H:i',
      defaultDate: '',
      time_24hr: true,
      minuteIncrement: 5,
      allowInput: true,
      position: 'below',
      onReady(_, __, instance) {
        instance.input.style.width = '70px';
      },
      onOpen(_, __, instance) {
        setTimeout(() => {
          const r = instance.input.getBoundingClientRect();
          instance.calendarContainer.style.top = (r.bottom + window.scrollY) + 'px';
          instance.calendarContainer.style.left = (r.left + window.scrollX) + 'px';
        }, 50);
      }
    });

    // 예약 날짜 선택
    flatpickr('#reservation-date', {
      enableTime: false,
      dateFormat: 'Y-m-d',
      locale: 'ko',
      minDate: 'today'
    });

    $('#date-picker-btn').on('click', function () {
      const fp = $('#reservation-date').get(0)?._flatpickr;
      if (fp) fp.open();
    });
  }

  /* -------- 전화번호 입력 포맷 -------- */
  ['protector_phone', 'as_contact'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('input', function(){ oninputPhone(this); });
      el.addEventListener('keydown', handleBackspace);
    }
  });

  /* -------- 문자 모달 열고닫기 -------- */
  let mmsAttachments = []; // [{ name, data (base64, no prefix), dataUrl }]

  function renderMmsPreview() {
    const previewArea = document.getElementById('mms-preview-area');
    if (!previewArea) return;
    if (mmsAttachments.length === 0) {
      previewArea.innerHTML = '';
      return;
    }
    previewArea.innerHTML = mmsAttachments.map((att, idx) => `
      <div class="mms-thumb-item" data-idx="${idx}">
        <img class="mms-thumb-img" src="${att.dataUrl}" alt="${att.name}">
        <span class="mms-thumb-name">${att.name}</span>
        <button type="button" class="mms-remove-btn" data-idx="${idx}">✕</button>
      </div>`).join('');
    previewArea.querySelectorAll('.mms-remove-btn').forEach(btn => {
      btn.addEventListener('click', function (e) {
        e.stopPropagation();
        const idx = parseInt(this.dataset.idx);
        mmsAttachments.splice(idx, 1);
        renderMmsPreview();
        updateByteCountSMS();
      });
    });
  }

  function resetMmsAttachment() {
    mmsAttachments = [];
    const fileInput = document.getElementById('mms-file-input');
    if (fileInput) fileInput.value = '';
    renderMmsPreview();
    updateByteCountSMS();
  }

  function updateByteCountSMS() {
    const textarea = document.getElementById('message-textarea');
    const cardtextarea = document.getElementById('card-textarea');
    const display = document.getElementById('byte-count');
    if (!textarea || !cardtextarea || !display) return;

    const totalText = textarea.value + cardtextarea.value;
    const currentBytes = calculateBytes(totalText);

    if (mmsAttachments.length > 0) {
      display.innerHTML = `${currentBytes} byte <span class="type_mms">MMS (이미지 ${mmsAttachments.length}개 첨부)</span>`;
      display.style.color = 'black';
    } else {
      const isLong = currentBytes > MAX_BYTES_SMS;
      const messageType = isLong ? '장문(LMS)' : '단문(SMS)';
      const maxBytes = isLong ? MAX_BYTES_MMS : MAX_BYTES_SMS;
      display.innerHTML = `${currentBytes} / ${maxBytes} byte <span class="type_${isLong ? 'mms' : 'sms'}">${messageType}</span>`;
      display.style.color = currentBytes > maxBytes ? 'red' : 'black';
    }
  }

  function updateSMSTextareaContent() {
    const textarea = document.getElementById('message-textarea');
    const adCheckbox = document.getElementById('ad-checkbox');
    if (!textarea || !adCheckbox) return;

    // 기존 헤더/푸터 제거
    let text = textarea.value
      .replace(/^\(광고\)\s*\n?/m, '')
      .replace(/\n?\s*수신거부:\s*0808806400\s*$/m, '')
      .replace(/\r/g, '')
      .trim();

    if (adCheckbox.checked) {
      textarea.value = `${AD_HEADER}\n${text}\n${AD_FOOTER}`.trim();
    } else {
      textarea.value = text;
    }
    updateByteCountSMS();
  }

  // 모달 닫기
  function closePhoneContainer() {
    const phoneContainer = document.getElementById('phone-container');
    if (!phoneContainer) return;
    const textarea = document.getElementById('message-textarea');
    const cardtextarea = document.getElementById('card-textarea');
    const inputValueDisplay = document.getElementById('input-value-display');
    const reservationdate = document.getElementById('reservation-date');
    const reservationHour = document.getElementById('reservation-hour');
    const reservationMinute = document.getElementById('reservation-minute');
    const userTemplate = document.getElementById('userTemplate');
    const cardTemplate = document.getElementById('cardTemplate');

    phoneContainer.style.display = 'none';
    if (textarea) textarea.value = '';
    if (cardtextarea) cardtextarea.value = '';
    if (inputValueDisplay) inputValueDisplay.textContent = '';
    if (reservationdate) reservationdate.value = '';
    if (reservationHour) reservationHour.value = '';
    if (reservationMinute) reservationMinute.value = '';
    if (userTemplate) userTemplate.value = '';
    if (cardTemplate) cardTemplate.value = '';

    document.querySelectorAll('.template-radio').forEach(r => r.checked = false);
    document.querySelectorAll('.card-radio').forEach(r => r.checked = false);

    resetMmsAttachment();
    updateByteCountSMS();
  }

  const closeBtn = document.getElementById('close');
  if (closeBtn) closeBtn.addEventListener('click', () => $('#popup').fadeOut());
  const historyPopup = document.getElementById('popup');
  if (historyPopup) {
    historyPopup.addEventListener('click', function (event) {
      if (event.target === historyPopup) {
        $('#popup').fadeOut();
      }
    });
  }

  const closePhoneBtn = document.getElementById('close-button');
  if (closePhoneBtn) closePhoneBtn.addEventListener('click', closePhoneContainer);
  // 모달 바깥 클릭 시 닫기 (flatpickr 캘린더/템플릿 모달 제외)
  document.addEventListener('click', function (event) {
    const phoneContainer = document.getElementById('phone-container');
    const templateModal = document.getElementById('template-save-modal');
    if (!phoneContainer || phoneContainer.style.display !== 'block') return;

    const clickedOnCalendar = Array.from(document.querySelectorAll('.flatpickr-calendar'))
      .some(cal => cal.contains(event.target));
    const clickedOnTimePicker = clickedOnCalendar; // 시간 선택도 같은 컨테이너

    const isTemplateModalClick = templateModal ? templateModal.contains(event.target) : false;
    const isSmsIcon = event.target.classList?.contains('sms_icon');

    if (!phoneContainer.contains(event.target) && !isSmsIcon && !isTemplateModalClick && !clickedOnCalendar && !clickedOnTimePicker) {
      closePhoneContainer();
    }
  });

  /* -------- 입력 이벤트 바인딩 -------- */
  ['message-textarea', 'card-textarea'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('input', updateByteCountSMS);
  });
  const adCheckbox = document.getElementById('ad-checkbox');
  if (adCheckbox) adCheckbox.addEventListener('change', updateSMSTextareaContent);

  updateSMSTextareaContent();
  updateByteCountSMS();

  /* -------- MMS 이미지 첨부 -------- */
  const mmsAttachBtn = document.getElementById('mms-attach-btn');
  const mmsFileInput = document.getElementById('mms-file-input');
  if (mmsAttachBtn && mmsFileInput) {
    mmsAttachBtn.addEventListener('click', () => mmsFileInput.click());
    mmsFileInput.addEventListener('change', function () {
      const files = Array.from(this.files);
      if (!files.length) return;
      const oversized = files.filter(f => f.size > 300 * 1024);
      if (oversized.length) {
        alert(`파일당 300KB 이하만 첨부 가능합니다.\n초과: ${oversized.map(f => f.name).join(', ')}`);
        this.value = '';
        return;
      }
      let loaded = 0;
      files.forEach(file => {
        const reader = new FileReader();
        reader.onload = function (e) {
          mmsAttachments.push({ name: file.name, data: e.target.result.split(',')[1], dataUrl: e.target.result });
          loaded++;
          if (loaded === files.length) {
            renderMmsPreview();
            updateByteCountSMS();
          }
        };
        reader.readAsDataURL(file);
      });
      this.value = '';
    });
  }

  /* -------- SMS 로그 fetch -------- */
  function fetchSmsLog(phoneList) {
    return $.ajax({
      url: `${APP_CTX}/sms/log`,
      type: 'POST',
      contentType: 'application/json',
      dataType: 'json',
      data: JSON.stringify({ to_phone: phoneList })
    });
  }

  /* -------- 전송 버튼 -------- */
  const sendBtn = document.getElementById('send-button');
  if (sendBtn) sendBtn.addEventListener('click', function () {
    const textarea = document.getElementById('message-textarea');
    const cardtextarea = document.getElementById('card-textarea');
    const fromPhoneEl = document.getElementById('from-phone');
    if (!textarea || !cardtextarea || !fromPhoneEl) return;

    const selected = Array.from($('.guardian-checkbox:checked')).map(cb => cb.value.replace(/-/g, ''));
    if (!selected.length) return alert('받는 사람을 선택해 주세요!');

    const message = (textarea.value + '\n' + cardtextarea.value).trim();
    if (!message) return alert('보내기 전에 메시지를 입력해 주세요!');

    let fromPhone = fromPhoneEl.value.replace(/-/g, '');
    if (!fromPhone) {
      alert('발신번호를 선택해 주세요.');
      fromPhoneEl.focus();
      return;
    }

    // 예약 시간 계산 (YYYY-MM-DD + HH + mm)
    const dateStr = $('#reservation-date').val();       // "2025-06-05"
    const hourStr = $('#reservation-hour').val();       // "00"~"23"
    const minStr  = $('#reservation-minute').val();     // "00","05",...

    let reservationUnix = null;
    if (dateStr && hourStr && minStr) {
      const target = new Date(`${dateStr}T${hourStr}:${minStr}:00+09:00`);
      const now = new Date();
      reservationUnix = Math.floor(target.getTime() / 1000);
      const nowUnix = Math.floor(now.getTime() / 1000);
      if (reservationUnix <= nowUnix) {
        alert('현재 시간 이후로만 예약할 수 있습니다.');
        return;
      }
    } else {
      // 즉시 전송: 현재 +5초
      const soon = new Date(Date.now() + 5 * 1000);
      reservationUnix = Math.floor(soon.getTime() / 1000);
    }

    function makeRefkey() {
      const instEl = document.getElementById('inst');
      const inst = instEl ? instEl.value : '';
      const now = new Date();
      const ymdhms = now.getFullYear().toString()
        + pad2(now.getMonth()+1) + pad2(now.getDate())
        + pad2(now.getHours()) + pad2(now.getMinutes()) + pad2(now.getSeconds());
      const rnd = Math.floor(1000 + Math.random() * 9000);
      return inst + ymdhms + rnd;
    }

    const currentBytes = calculateBytes(message);
    const sendType = mmsAttachments.length > 0 ? 'mms' : (currentBytes > MAX_BYTES_SMS ? 'lms' : 'sms');

    const common = {
      // account는 서버 설정(sms.bizppurio.account)에서 주입한다.
      refkey: makeRefkey(),
      type: sendType,
      from: fromPhone,
      content: {}
    };
    // 예약 발송 시간(unixtime, seconds)
    if (reservationUnix) {
      common.sendtime = reservationUnix;
    }

    if (sendType === 'mms') {
      common.content.mms = {
        subject: message.substring(0, 20),
        message,
        file: mmsAttachments.map(a => ({ name: a.name, data: a.data }))
      };
    } else if (sendType === 'lms') {
      common.content.lms = {
        subject: message.substring(0, 20),
        message
      };
    } else {
      common.content.sms = { message };
    }

    console.group('%c[SMS 전송 정보]', 'color:#0b79d0;font-weight:bold;');
    console.log('account:', '(server-configured)');
    console.log('refkey:', common.refkey);
    console.log('type:', common.type);
    console.log('from:', common.from);
    console.log('sendtime(unix):', common.sendtime);
    console.log('content:', common.content);
    console.groupEnd();

    // 수신자별 개별 요청
    const sendRequests = selected.map(to => {
      const payload = Object.assign({}, common, { to });
      return $.ajax({
        url: `${APP_CTX}/api/external/sendSMS`,
        type: 'POST',
        contentType: 'application/json',
        dataType: 'json',
        data: JSON.stringify(payload)
      })
        .then(resp => {
          const ok = resp && resp.description === 'success';
          if (!ok) console.warn(`[${to}] 전송 실패`, resp);
          return { to, ok, resp };
        })
        .catch(err => {
          console.error(`[${to}] 전송 에러`, err);
          return { to, ok: false, err };
        });
    });

    Promise.all(sendRequests).then(function (results) {
      const failed = results.filter(r => !r.ok).map(r => r.to);
      fetchSmsLog(selected)
        .done(function (history) {
          let html = '';
          history.forEach(item => {
            html += `
              <div class="sms-history">
                ${item.contents}
                <div class="sms-date">
                  <div>${item.to_phone}</div>
                  <div>${formatDate(item.created_at)}</div>
                </div>
              </div>`;
          });
          $('#history-wrapper').html(html);

          if (failed.length === 0) {
            alert('문자 전송이 완료되었습니다.');
          } else {
            alert(`일부 전송 실패: ${failed.join(', ')}`);
          }

          // 폼 초기화
          $('#message-textarea').val('');
          $('#card-textarea').val('');
          $('.guardian-checkbox').prop('checked', false);
          $('#from-phone').val('');
          $('#reservation-date').val('');
          $('#reservation-hour').val('');
          $('#reservation-minute').val('');
          resetMmsAttachment();
          updateByteCountSMS();
        })
        .fail(function (xhr) {
          console.error('히스토리 조회 실패', xhr);
        });
    });
  });

  /* -------- 템플릿 라디오 -------- */
  $('.template-radio').on('click', function () {
    const selectedTemplate = $(this).data('template');
    $('#userTemplate').val(selectedTemplate || '');
    $('.template-radio').removeClass('selected');
    $(this).addClass('selected');
    updateSMSTextareaContent();
  });

  $('.card-radio').on('click', function () {
    const selectedCard = $(this).data('card');
    $('#cardTemplate').val(selectedCard || '');
    $('.card-radio').removeClass('selected');
    $(this).addClass('selected');
    updateSMSTextareaContent();
  });

  $('#template-select-btn').on('click', function () {
    const selectedTemplate = $('.template-radio.selected').data('template');
    if (selectedTemplate !== undefined) {
      $('#message-textarea').val(selectedTemplate);
      updateByteCountSMS();
    } else {
      alert('먼저 상용구 항목을 선택하세요.');
    }
  });

  $('#card-select-btn').on('click', function () {
    const selectedCard = $('.card-radio.selected').data('card');
    if (selectedCard !== undefined) {
      $('#card-textarea').val(selectedCard);
      updateByteCountSMS();
    } else {
      alert('먼저 명함 항목을 선택하세요.');
    }
  });

  $('#message-textarea').on('input', function () {
    const newContent = $(this).val();
    $('.template-item.selected').attr('data-template', newContent).data('template', newContent);
  });
  $('#card-textarea').on('input', function () {
    const newContent = $(this).val();
    $('.card-item.selected').attr('data-card', newContent).data('card', newContent);
  });

  /* -------- 리셋 아이콘 -------- */
  const templateResetIcon = document.getElementById('template-reset');
  if (templateResetIcon) templateResetIcon.addEventListener('click', function () {
    $('#message-textarea').val('');
    $('#ad-checkbox').prop('checked', false);
    updateByteCountSMS();
  });

  const cardResetIcon = document.getElementById('card-reset');
  if (cardResetIcon) cardResetIcon.addEventListener('click', function () {
    $('#card-textarea').val('');
    updateByteCountSMS();
  });

  const reservationResetIcon = document.getElementById('reservation-reset');
  if (reservationResetIcon) reservationResetIcon.addEventListener('click', function () {
    $('#reservation-date').val('');
    $('#reservation-hour').val('');
    $('#reservation-minute').val('');
    $('#reservation').val('');
  });

  /* -------- 템플릿 저장 모달 -------- */
  const saveModal = document.getElementById('template-save-modal');
  const saveButton = document.getElementById('save-template-button');
  const cancelButton = document.getElementById('cancel-template-button');
  const templateNameInput = document.getElementById('template-name');
  const templateContentTextarea = document.getElementById('template-content');
  const openSaveModalButton = document.getElementById('template-save-btn');

  function closeTemplateModal(){ if (saveModal) saveModal.style.display = 'none'; }

  if (openSaveModalButton && saveModal && templateContentTextarea && templateNameInput) {
    openSaveModalButton.addEventListener('click', function () {
      saveModal.style.display = 'flex';
      const messageTextarea = document.getElementById('message-textarea');
      templateContentTextarea.value = (messageTextarea?.value || '').trim();
      templateNameInput.value = '';
    });
  }
  if (cancelButton) cancelButton.addEventListener('click', closeTemplateModal);

  if (saveButton && templateNameInput && templateContentTextarea) {
    saveButton.addEventListener('click', function () {
      const templateName = templateNameInput.value.trim();
      const templateContent = templateContentTextarea.value.trim();
      if (!templateName || !templateContent) {
        alert('템플릿 이름과 내용을 모두 입력하세요!');
        return;
      }
      $.ajax({
        url: `${APP_CTX}/sms/template/save`,
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ title: templateName, template: templateContent }),
        success(resp) {
          alert('상용구 저장이 완료되었습니다.');
          console.log('Server response:', resp);
          closeTemplateModal();
        },
        error(xhr) {
          console.error('Error saving template:', xhr.responseText);
          alert('상용구 저장에 실패했습니다.');
        }
      });
    });
  }

  /* -------- 프린트 미리보기/출력 -------- */
  const modal = document.getElementById('dataModal');
  const journalPrintButton = document.getElementById('print-preview');
  const admissionPledgePrintButton = document.getElementById('print-admission-pledge');
  const closeModalButton = document.getElementById('closeModal');
  const escapeHtml = (v) => String(v ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');

  const buildPrintHtmlFromForm = (printMode = 'journal') => {
    const val = (id) => (document.getElementById(id)?.value || '').trim();
    const selectedText = (id) => {
      const el = document.getElementById(id);
      if (!el) return '';
      if (el.tagName === 'SELECT') {
        const opt = el.options?.[el.selectedIndex];
        const optValue = String(opt?.value || '').trim();
        if (!optValue) return '';
        return (opt?.textContent || optValue).trim();
      }
      return (el.value || '').trim();
    };
    const checkedText = (id, text = '선택') => {
      const el = document.getElementById(id);
      return el?.checked ? text : '';
    };
    const combineSelectWithDetail = (selectId, detailId) => {
      const primary = selectedText(selectId);
      const detail = val(detailId);
      if (primary && detail) return `${primary} (${detail})`;
      return primary || detail || '';
    };
    const displayText = (v) => {
      const text = String(v ?? '').trim();
      return text || '-';
    };
    const renderKvRows = (items) => items.map((item) => `
      <tr>
        <th style="text-align:left;border:1px solid #ccc;padding:6px;background:#f7f7f7;">${escapeHtml(item.label)}</th>
        <td style="border:1px solid #ccc;padding:6px;">${escapeHtml(displayText(item.value))}</td>
      </tr>
    `).join('');

    const getFieldBaseFromElement = (el) => {
      if (!el || !el.name) return null;
      const name = el.name;
      const m = name.match(/^(field_\d+_\d+)_(checkbox|text|details|select)$/);
      if (m) return m[1];

      const rm = name.match(/^field_(\d+)_radio$/);
      if (rm && el.type === 'radio') {
        const subId = String(el.value || '').split('_')[0];
        if (/^\d+$/.test(subId)) {
          return `field_${rm[1]}_${subId}`;
        }
      }
      return null;
    };

    const readLabelAround = (el) => {
      if (!el) return '';
      const wrap = el.closest('.input-wrapper');
      if (!wrap) return '';
      const label = wrap.querySelector('label');
      if (label && label.textContent) {
        const text = label.textContent.trim();
        if (text) return text;
      }
      const span = wrap.querySelector('span');
      if (span && span.textContent) {
        const text = span.textContent.trim();
        if (text) return text;
      }
      return '';
    };

    const collectCustomFieldRows = () => {
      const map = new Map();
      document.querySelectorAll("input[name^='field_'], select[name^='field_']").forEach((el) => {
        const base = getFieldBaseFromElement(el);
        if (!base) return;

        if (!map.has(base)) {
          map.set(base, {
            base,
            label: '',
            checked: false,
            radio: false,
            selectValue: '',
            textValues: []
          });
        }
        const entry = map.get(base);

        const nearLabel = readLabelAround(el);
        if (nearLabel) entry.label = nearLabel;

        if (el.tagName === 'SELECT') {
          const value = String(el.value || '').trim();
          if (value) {
            const opt = el.options?.[el.selectedIndex];
            const optText = (opt?.textContent || value).trim();
            entry.selectValue = optText;
          }
          return;
        }

        if (el.type === 'checkbox') {
          if (el.checked) {
            entry.checked = true;
            if (!entry.label) {
              entry.label = String(el.value || '').trim();
            }
          }
          return;
        }

        if (el.type === 'radio') {
          if (el.checked) {
            entry.radio = true;
            if (!entry.label) {
              entry.label = String(el.value || '').trim();
            }
          }
          return;
        }

        if (el.tagName === 'INPUT' && el.type === 'text') {
          const value = String(el.value || '').trim();
          if (value) {
            entry.textValues.push(value);
          }
        }
      });

      return Array.from(map.values()).reduce((acc, entry) => {
        const label = (entry.label || entry.base || '').trim();
        const parts = [];
        if (entry.selectValue) parts.push(entry.selectValue);
        if (entry.textValues.length > 0) parts.push(...entry.textValues);
        if (parts.length === 0 && (entry.checked || entry.radio)) {
          parts.push('선택');
        }
        if (!label || parts.length === 0) return acc;
        acc.push({ label, value: parts.join(' / ') });
        return acc;
      }, []);
    };

    const patientInfoRows = renderKvRows([
      { label: '환자명', value: val('cs_col_01') },
      { label: '성별', value: selectedText('cs_col_02') },
      { label: '생년월일', value: val('cs_col_03') },
      { label: '나이', value: val('cs_col_04') },
      { label: '보험유형', value: selectedText('cs_col_05') },
      { label: '실손보험', value: checkedText('cs_col_06') },
      { label: '현재계신곳', value: combineSelectWithDetail('cs_col_07', 'cs_col_07_text') },
      { label: '상담유입경로', value: combineSelectWithDetail('cs_col_08', 'cs_col_08_text') },
      { label: '잠재고객', value: selectedText('cs_col_09') },
      { label: 'BC', value: checkedText('cs_col_10') }
    ]);

    const counselInfoRows = renderKvRows([
      { label: '상담일자', value: val('cs_col_16') },
      { label: '상담자', value: val('cs_col_17') },
      { label: '상담방법', value: selectedText('cs_col_18') },
      { label: '상담결과', value: selectedText('cs_col_19') },
      { label: '입원예정일', value: val('cs_col_21') },
      { label: '예정시간', value: val('cs_col_21_time') },
      { label: '예상 호실', value: val('cs_col_38') },
      { label: '미입원사유', value: selectedText('cs_col_20') }
    ]);

    const customRows = collectCustomFieldRows().map((item) => `
      <tr>
        <th>${escapeHtml(item.label)}</th>
        <td>${escapeHtml(displayText(item.value))}</td>
      </tr>
    `).join('');

    const guardianRows = Array.from(document.querySelectorAll('.guardian-entry')).map((entry) => {
      const name = entry.querySelector("input[name='cs_col_13[]']")?.value?.trim() || '';
      const relation = entry.querySelector("input[name='cs_col_14[]']")?.value?.trim() || '';
      const phone = entry.querySelector("input[name='cs_col_15[]']")?.value?.trim() || '';
      if (!name && !relation && !phone) return '';
      return `<tr>
        <td>${escapeHtml(name)}</td>
        <td>${escapeHtml(relation)}</td>
        <td>${escapeHtml(phone)}</td>
      </tr>`;
    }).filter(Boolean).join('');

    const attachmentRows = Array.from(document.querySelectorAll('#counselFileList .counsel-file-item')).map((row) => {
      const fileName = row.querySelector('.counsel-file-link')?.textContent?.trim() || '';
      const fileSize = row.querySelector('.counsel-file-size')?.textContent?.trim() || '';
      if (!fileName) return '';
      return `<tr>
        <td>${escapeHtml(fileName)}</td>
        <td>${escapeHtml(fileSize)}</td>
      </tr>`;
    }).filter(Boolean).join('');

    const rows = Array.from(document.querySelectorAll('.logs-body .logs-content')).map((row, index) => {
      const method = row.querySelector('.method')?.textContent?.trim() || '';
      const date = row.querySelector('.date')?.textContent?.trim() || '';
      const result = row.querySelector('.result')?.textContent?.trim() || '';
      const counselor = row.querySelector('.counselor')?.textContent?.trim() || '';
      return `<tr>
        <td>${index + 1}</td>
        <td>${escapeHtml(method)}</td>
        <td>${escapeHtml(date)}</td>
        <td>${escapeHtml(result)}</td>
        <td>${escapeHtml(counselor)}</td>
      </tr>`;
    }).join('');

    const valueByName = (name) => (document.querySelector(`[name="${name}"]`)?.value || '').trim();
    const documentNo = valueByName('cs_idx') || '-';
    const issuedAt = new Date().toLocaleString('ko-KR', { hour12: false });
    const admissionRequired = valueByName('admission_pledge_required') === 'Y';
    const admissionPageInk = valueByName('admission_page_ink_data');

    const readGuardianEntry = (entry) => ({
      name: entry?.querySelector("input[name='cs_col_13[]']")?.value?.trim() || '',
      relation: entry?.querySelector("input[name='cs_col_14[]']")?.value?.trim() || '',
      phone: entry?.querySelector("input[name='cs_col_15[]']")?.value?.trim() || '',
      addr: '',
      costYn: false
    });

    const renderCheckedAttr = (checked) => (checked ? ' checked' : '');
    const renderValueAttr = (value) => escapeHtml(String(value || '').trim());
    const renderGuardianTable = (title, data, imageData, imageAlt) => {
      const signImageMarkup = imageData
        ? `<img class="ap-sign-image" src="${escapeHtml(imageData)}" alt="${escapeHtml(imageAlt)}">`
        : '<img class="ap-sign-image" alt="" style="display:none;">';

      return `
        <div style="text-align: center; margin-bottom: 27px;">
          <table class="ap-guardian-table" style="text-align: center; width: 966px; margin-left: auto; margin-right: auto; border: 1px solid #c7c7c7;">
            <colgroup>
              <col style="width:65px;">
              <col style="width:100px;">
              <col style="width:300px;">
              <col style="width:420px;">
              <col style="width:81px;">
            </colgroup>
            <tr style="height: 56px;">
              <td class="normal" rowspan="3" style="width:65px; text-align: center; background-color:#fafafa; border: 1px solid #c7c7c7;">${title}</td>
              <td class="normal" style="position: relative; width: 170px; border: 1px solid #dadada; border-top: inherit;">성 명</td>
              <td style="width:350px; text-align: left; border: 1px solid #dadada; border-top: inherit;">
                <input class="light ap-guardian-name-input" style="margin-left: 20px; width:260px;" type="text" readonly value="${renderValueAttr(data.name)}"/>
              </td>
              <td class="normal ap-relation-cell" style="border: 1px solid #dadada; border-top: inherit;">
                <span class="ap-relation-label">(관계 :</span>
                <input class="light ap-relation-input" type="text" readonly value="${renderValueAttr(data.relation)}"/>
                <span class="ap-relation-label">)</span>
              </td>
              <td class="ap-sign-cell">
                <div class="normal ap-sign-trigger is-disabled">(서   명)</div>
                ${signImageMarkup}
              </td>
            </tr>
            <tr style="height: 56px;">
              <td style="border: 1px solid #dadada;" class="normal">주 소</td>
              <td colspan="3" style="text-align: left; border: 1px solid #dadada; border-right: inherit;">
                <input class="light ap-address-input" style="margin-left: 20px; width:640px;" type="text" readonly value="${renderValueAttr(data.addr)}"/>
              </td>
            </tr>
            <tr style="height: 56px;">
              <td style="border: 1px solid #dadada; border-left: inherit; border-bottom: inherit;" class="normal">휴대폰 번호</td>
              <td style="text-align: left;">
                <input class="light ap-phone-input" type="text" style="margin-left: 20px;" readonly value="${renderValueAttr(data.phone)}"/>
              </td>
              <td colspan="2" style="text-align: right;">
                <div class="checkbox-wrapper-13" style="margin-right: 21px;">
                  <input type="checkbox" class="normal" disabled${renderCheckedAttr(!!data.costYn)}>
                  <label>비용안내</label>
                </div>
              </td>
            </tr>
          </table>
        </div>`;
    };

    const buildAdmissionPledgePrintMarkup = () => {
      const guardians = Array.from(document.querySelectorAll('.guardian-entry'));
      const mainGuardianFallback = readGuardianEntry(guardians[0]);
      const subGuardianFallback = readGuardianEntry(guardians[1]);
      const mainGuardian = {
        name: valueByName('admission_guardian_name') || mainGuardianFallback.name,
        relation: valueByName('admission_guardian_relation') || mainGuardianFallback.relation,
        addr: valueByName('admission_guardian_addr'),
        phone: valueByName('admission_guardian_phone') || mainGuardianFallback.phone,
        costYn: valueByName('admission_guardian_cost_yn') === 'Y'
      };
      const subGuardian = {
        name: valueByName('admission_sub_guardian_name') || subGuardianFallback.name,
        relation: valueByName('admission_sub_guardian_relation') || subGuardianFallback.relation,
        addr: valueByName('admission_sub_guardian_addr'),
        phone: valueByName('admission_sub_guardian_phone') || subGuardianFallback.phone,
        costYn: valueByName('admission_sub_guardian_cost_yn') === 'Y'
      };
      const gender = selectedText('cs_col_02');
      const signerName = valueByName('admission_signer_name') || val('cs_col_01');
      const signerRelation = valueByName('admission_signer_relation') || '본인';
      const signedAt = valueByName('admission_signed_at');
      const backgroundUrl = `${APP_CTX}/img/background4.png`;
      const primarySignatureImg = '<img class="ap-sign-image ap-sign-image-final" alt="" style="display:none;">';
      const pageInkMarkup = admissionPageInk
        ? `<img class="ap-page-ink-print-img" alt="문서 전체 필기 인쇄 레이어" src="${escapeHtml(admissionPageInk)}" style="position:absolute; inset:0; left:-157px; width:1280px; height:100%; object-fit:fill; z-index:19; pointer-events:none; opacity:1; visibility:visible;">`
        : '';

      return `
        <section class="journal-section journal-section-admission">
          <div class="ap-document-stage" style="position:relative; width:966px; margin:0 auto; overflow:hidden;">
            <section class="ap-paper" style="background-repeat:no-repeat; background-position:center 0; background-image:url('${escapeHtml(backgroundUrl)}'); background-size:966px auto; width:966px; min-width:966px; margin:0 auto;">
              <h1 style="text-align: center; font-size: 30pt; padding-top: 70px;">입 원 서 약 서</h1>

              <div class="bold" style="font-size: 16pt; color:#303030; display: flex; align-items: start; margin: 0 auto; width: 966px;">※ 환자의 인적사항</div>
              <div style="text-align: center; margin-bottom: 20px;">
                <table border="1" style="border-collapse: collapse; text-align: center; width: 966px; margin-left: auto; margin-right: auto;">
                  <tr>
                    <td class="normal" width="170px" height="56px">성명</td>
                    <td class="light" style="text-align: left;">
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(val('cs_col_01'))}">
                    </td>
                    <td class="normal" width="170px" height="56px">차트번호</td>
                    <td class="light" colspan="2" style="text-align: left;">
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(val('cs_col_40'))}">
                    </td>
                  </tr>
                  <tr>
                    <td class="normal" height="56px">입원병실</td>
                    <td class="light" style="text-align: left;">
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(val('cs_col_38'))}">
                    </td>
                    <td class="normal" height="56px">성별 </td>
                    <td class="light" style="cursor: pointer;">
                      <input type="radio" disabled${renderCheckedAttr(gender === '남성' || gender === '남')}><label style="cursor: pointer;">남</label>
                    </td>
                    <td class="light" style="cursor: pointer;">
                      <input type="radio" disabled${renderCheckedAttr(gender === '여성' || gender === '여')}><label style="cursor: pointer;">여</label>
                    </td>
                  </tr>
                  <tr>
                    <td class="normal" height="56px">생년월일</td>
                    <td class="light" style="text-align: left;">
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(val('cs_col_03'))}">
                    </td>
                    <td class="normal" height="56px">전화</td>
                    <td class="light" colspan="2" style="text-align: left;">
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(mainGuardian.phone)}">
                    </td>
                  </tr>
                </table>
              </div>

              <div class="light ap-terms-block" style="font-size:14pt; color:#222222; width:966px; display: flex; align-items: start; flex-direction: column; margin: 0 auto;">
                <span>&nbsp;본인(환자의 주보호자)은 귀 의료기관에서 제시한 제반 규칙을 준수함은 물론, 치료와 퇴원 등 의사 및 간호사(또는 직원)의 정당한 지시에 따르며, 아래의 내용을 읽고 서약 및 동의합니다.</span>
                <span style="color:#f87b0c;">1. 입원 기간 중 예기치 않은 사고(골절, 타박상, 개방성 상처 등)나 응급상황 시 본원에서 치료할 수 없는 상태이거나 의료진 판단으로 응급처치 가능한 병원으로 전원을 요구할 수 있으며 또한 환자 및 보호자가 원할 경우 담당의사와 상의 후 타 병원으로 전원 할 수 있습니다.</span>
                <span style="color:#f87b0c;">2. 노인은 골다공증, 피부의 약화로 쉽게 골절 또는 멍이 들 수 있으므로 의료기관의 정당한 진료지침이나 교육에 반하는 무단 외출・외박 등으로 인하여 발생하는 환자의 손해에 대한 책임은 원칙적으로 모두 환자에게 있습니다.</span>
                <span>3. 진료 상 발생하는 모든 문제에 대하여 분쟁이 생겼을 때에는 『의료사고 피해구제 및 의료분쟁 조정 등에 관한 법률』에 의한 한국 의료분쟁조정중재원에 그 조정을 신청할 수 있음에 동의합니다.</span>
                <span>4. 입원기간 동안 발생하는 진료비는 귀 의료기관에서 정하는 납부기한 내에 납부(연대보증인이 있는 경우에는 환자와 연대보증인이 연대하여 납부)하겠으며, 정당한 이유 없이 체납될 때에는 채권확보를 위한 법적조치에 이의가 없고, 만일 본건을 기초로 의료분쟁 등으로 소송을 제기할 경우 관할법원의 민사소송법에 따릅니다.</span>
                <span>5. 입원기간 중에 환자 및 보호자가 귀 의료기관의 비품이나 기물을 고의 또는 과실로 파괴, 망실, 훼손한 때에는 이를 변상(현물, 현금)합니다.</span>
                <span style="color:#f87b0c;">6. 입원기간 중 환자 또는 보호자 등이 소지 중인 현금, 기타 귀중품 및 개인소지품(완전틀니, 부분틀니 포함, 안경, 보청기등)은 귀 의료기관이 지정한 보관 장소가 있는 경우에는 보관 장소에 보관하고, 보관 장소가 따로 없는 경우에는 귀 의료기관이 지정한 직원에게 보관을 의뢰합니다. 이를 이행치 아니하여 분실 및 훼손되어 발생한 손해에 대하여는 귀 의료기관은 책임이 없습니다.</span>
                <span>7. 개인정보 수집 및 활용 동의</span>
                <span>본원은 진료 등을 위해 아래와 같은 최소한의 개인정보를 수집함. 진료를 위한 필요정보는 의료법에 따라 별도의 동의 없이 수집되며, 동의를 하지 않더라도 진료에는 불이익이 없음.</span>
                <span>(1) 개인정보 수집항목 : (필수항목) 성명, 주소, 전화번호, 주민등록번호, 보험정보</span>
                <span style="margin-bottom: 10px;">&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&nbsp;&nbsp;&nbsp;(선택항목) 이메일, 문자메세지 서비스 수신 동의여부 </span>
                <span style="margin-bottom: 10px;">(2) 개인정보 수집방법 : 진료 목적은 별도로 받지 않으며, 진료목적 외는 서면으로 수집</span>
                <span style="margin-bottom: 10px;">(3) 개인정보의 수집 및 이용목적 : 진단/검진 예약, 조회 및 진료를 위한 본인 확인 절차 등</span>
                <span style="margin-bottom: 20px;">(4) 개인정보의 보유 및 이용기간 : 개인정보의 수집목적 또는 제공받은 목적이 달성될 때 파기</span>

                <span class="bold" style="color:#303030; font-size: 16pt;">※ 환자본인, 주보호자 및 부보호자에 대한 안내</span>
                <span>1. 주보호자는 환자의 입원과 전원, 퇴원 등의 절차상 동의인 이며, 환자 상태의 급격한 변화, 낙상 등의 안전사고, 사망 등 환자입원생활에 관련된 사항에 대해 <b class="bold">일차적 연락대상</b>이며 타보호자는 <b>상담이 제한</b>됩니다. 주보호자 변경 시에는 주보호자변경요청서를 통해서만 가능합니다.</span>
                <span>2. 주보호자 및 부보호자는 환자의 입원비용과 기타 제반 비용 발생 시 매월 <b>정산의 책임</b>을 지게 되며</span>
                <span style="margin-bottom: 10px;">(보증채무최고액:30,000,000원 보증기간:3년), 2개월 미납시 본원은 퇴원권유 할 수 있습니다.</span>
                <span>3. 주보호자는 환자의 입원기록 외 사본 발급 및 제증명 발급의 주체가 되며, 수혈동의서, 신체 보호대 동의서, 심폐소생술거부동의서, 낙상관련설명안내서, 병원비 등의 규정상 동의절차가 필요한 경우 <b>서명 대상자</b>가 됩니다.</span>
                <span>4. 입원생활에 관련 법적 분쟁 발생 시 원칙적으로 환자 본인이 의료기관의 소송 상대방이 되며, 불가피할 경우 주보호자가 <b>법적 대리인</b>이 됩니다.</span>
              </div>
              <br>

              ${renderGuardianTable('주<br>보<br>호<br>자', mainGuardian, '', '주보호자 서명')}
              ${renderGuardianTable('부<br>보<br>호<br>자', subGuardian, '', '부보호자 서명')}

              <div style="text-align: center; margin-bottom: 27px;">
                <div style="margin-bottom: 27px;">
                  <table border="1" class="ap-reason-table" style="font-size:14pt; border-collapse: collapse; text-align: center; width: 966px; margin-left: auto; margin-right: auto; border: 1px solid #c7c7c7;">
                    <tr style="background-color: #fafafa;">
                      <td colspan="3" style="height: 56px; text-align: left;" class="normal"><div style="margin-left: 21px;">환자가 아닌 보호자의 동의사유</div></td>
                    </tr>
                    <tr style="background-color: #ffffff;">
                      <td colspan="3" style="text-align: left; padding: 14px 20px;">
                        <div class="ap-reason-grid">
                          <div class="checkbox-wrapper-13 ap-reason-item"><input type="checkbox" class="normal" disabled><label>환자의 신체적 정신적 장애로 의사결정 불가</label></div>
                          <div class="checkbox-wrapper-13 ap-reason-item"><input type="checkbox" class="normal" disabled><label>환자위임</label></div>
                          <div class="checkbox-wrapper-13 ap-reason-item"><input type="checkbox" class="normal" disabled><label>응급 상황</label></div>
                          <div class="checkbox-wrapper-13 ap-reason-item"><input type="checkbox" class="normal" disabled><label>내용 설명 시 환자의 심신에 중대한 영향 우려</label></div>
                          <div class="checkbox-wrapper-13 ap-reason-item"><input type="checkbox" class="normal" disabled><label>미성년자</label></div>
                        </div>
                      </td>
                    </tr>
                  </table>
                </div>

                <div style="text-align: center; margin-bottom: 27px;">
                  <table style="font-size:14pt; border-collapse: collapse; text-align: center; width: 966px; margin-left: auto; margin-right: auto; border: 1px solid #c7c7c7;">
                    <tr style="height: 56px; background-color: #fafafa;" class="normal">
                      <td colspan="3" style="text-align: left; border-bottom: 1px solid #c7c7c7;">
                        <div class="checkbox-wrapper-13">
                          <input style="font-size:14pt; margin-left: 21px;" type="checkbox" class="normal" disabled>
                          <label>상급병실(특실, 1인실, 2인실)의 이용 시 병실차액이 발생할 수 있습니다.</label>
                        </div>
                      </td>
                    </tr>
                    <tr style="height: 56px; border-bottom: 1px solid #dadada; background-color: #ffffff;">
                      <td style="text-align: center; width: 170px; border-right: 1px solid #dadada;">병실</td>
                      <td style="text-align: center; width: 30%;"><input style="width:190px; text-align: right;" type="text" readonly value=""/> 호</td>
                      <td style="text-align: right; color:#222222;" class="light">
                        <div style="margin-right: 21px; display: flex; justify-content: right;">
                          <div class="checkbox-wrapper-13"><input type="checkbox" class="normal" disabled><label>특실</label>&emsp;&emsp;</div>
                          <div class="checkbox-wrapper-13"><input type="checkbox" class="normal" disabled><label>1인실</label>&emsp;&emsp;</div>
                          <div class="checkbox-wrapper-13"><input type="checkbox" class="normal" disabled><label>2인실</label></div>
                        </div>
                      </td>
                    </tr>
                    <tr style="height: 56px; border-bottom: 1px solid #dadada; background-color: #ffffff; color:#222222;">
                      <td style="text-align: center; border-right: 1px solid #dadada;">비용</td>
                      <td class="light" style="text-align: right;" colspan="2">1일당 : <input type="text" readonly value=""/></td>
                    </tr>
                    <tr style="height: 56px; background-color: #ffffff;">
                      <td colspan="3" style="text-align: left;"><div style="margin-left: 21px;">상급병실 사용에 관련한 차액발생부분 설명을 듣고 동의함.</div></td>
                    </tr>
                  </table>
                </div>
              </div>

              <div style="position: relative;">
                <div class="normal" style="text-align: right; position: relative; z-index: 20; color: #222222; width: 966px; height: 42px; margin: 0 auto; display: flex; justify-content: flex-end; align-items: center;">
                  신청인 ( 관계 :
                  <input style="width:175px;" type="text" readonly value="${renderValueAttr(signerRelation)}"/>) :
                  <input style="width:100px;" type="text" readonly value="${renderValueAttr(signerName)}"/>
                  &emsp;&emsp;&emsp;&emsp;&emsp;&emsp;
                  <div style="display: inline-block; position: relative;" class="ap-sign-trigger is-disabled">
                    ( 서&emsp;${primarySignatureImg}명 )
                  </div>
                </div>
              </div>
              <div class="ap-signed-at normal">서명일시: <span>${escapeHtml(displayText(signedAt))}</span></div>
              <br><br><br><br>
            </section>
            ${pageInkMarkup}
          </div>
        </section>`;
    };

    const admissionPledgeSection = admissionRequired ? buildAdmissionPledgePrintMarkup() : '';
    if (printMode === 'admission') {
      return admissionPledgeSection;
    }

    return `
      <div class="journal-doc">
        <header class="journal-header">
          <div class="journal-title-wrap">
            <h1>입원상담 상담일지</h1>
            <p>INPATIENT COUNSELING REPORT</p>
          </div>
          <div class="journal-meta">
            <div class="meta-item">
              <span>문서번호</span>
              <strong>${escapeHtml(displayText(documentNo))}</strong>
            </div>
            <div class="meta-item">
              <span>상담일자</span>
              <strong>${escapeHtml(displayText(val('cs_col_16')))}</strong>
            </div>
            <div class="meta-item">
              <span>출력일시</span>
              <strong>${escapeHtml(issuedAt)}</strong>
            </div>
          </div>
        </header>

        <section class="journal-section">
          <h2 class="section-title">1. 기본정보</h2>
          <table class="kv-table">
            <tbody>${patientInfoRows}</tbody>
          </table>
        </section>

        <section class="journal-section">
          <h2 class="section-title">2. 상담정보</h2>
          <table class="kv-table">
            <tbody>${counselInfoRows}</tbody>
          </table>
        </section>

        <section class="journal-section">
          <h2 class="section-title">3. 현상태</h2>
          <div class="text-block">${escapeHtml(displayText(val('cs_col_11')))}</div>
        </section>

        <section class="journal-section">
          <h2 class="section-title">4. 상담내용</h2>
          <div class="text-block text-block-lg">${escapeHtml(displayText(val('cs_col_32')))}</div>
        </section>

        <section class="journal-section">
          <h2 class="section-title">5. 환자상세정보</h2>
          <table class="kv-table">
            <tbody>${customRows || '<tr><td colspan="2" class="empty-cell">선택된 항목이 없습니다.</td></tr>'}</tbody>
          </table>
        </section>

        <section class="journal-section">
          <h2 class="section-title">6. 보호자 정보</h2>
          <table class="list-table">
            <thead>
              <tr>
                <th>보호자명</th>
                <th>관계</th>
                <th>연락처</th>
              </tr>
            </thead>
            <tbody>${guardianRows || '<tr><td colspan="3" class="empty-cell">입력된 보호자 정보가 없습니다.</td></tr>'}</tbody>
          </table>
        </section>

        <section class="journal-section">
          <h2 class="section-title">7. 첨부파일</h2>
          <table class="list-table">
            <thead>
              <tr>
                <th>파일명</th>
                <th>용량</th>
              </tr>
            </thead>
            <tbody>${attachmentRows || '<tr><td colspan="2" class="empty-cell">첨부파일이 없습니다.</td></tr>'}</tbody>
          </table>
        </section>

        <section class="journal-section">
          <h2 class="section-title">8. 상담이력</h2>
          <table class="list-table history-table">
            <thead>
              <tr>
                <th>No</th>
                <th>방법</th>
                <th>상담일자</th>
                <th>결과</th>
                <th>상담자</th>
              </tr>
            </thead>
            <tbody>${rows || '<tr><td colspan="5" class="empty-cell">이력이 없습니다.</td></tr>'}</tbody>
          </table>
        </section>

        <footer class="journal-sign">
          <div class="sign-box">
            <span>상담자</span>
            <strong>${escapeHtml(displayText(val('cs_col_17')))}</strong>
          </div>
          <div class="sign-box">
            <span>확인자</span>
            <strong></strong>
          </div>
        </footer>
      </div>`;
  };

  const openPrintWindow = (printMode = 'journal') => {
    const isAdmissionPrint = printMode === 'admission';
    const bodyHtml = buildPrintHtmlFromForm(printMode);
    if (!bodyHtml) {
      alert('입원서약서 데이터가 없습니다.');
      return;
    }

    const popup = window.open('', '_blank', 'width=1280,height=1000,resizable=yes,scrollbars=yes');
    if (!popup) return;

    const journalPrintStyles = `
      :root { color-scheme: light; }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        padding: 0;
        background: #eef2f7;
        color: #1f2937;
        font-family: "Noto Sans KR", "Malgun Gothic", "Apple SD Gothic Neo", sans-serif;
      }
      .print-page {
        width: 1100px;
        margin: 12px auto;
        padding: 12mm;
        background: #fff;
        box-shadow: 0 4px 18px rgba(15, 23, 42, 0.08);
      }
      .journal-doc { font-size: 12px; line-height: 1.5; }
      .print-page .ap-paper {
        background-image: none !important;
        background-color: #fff !important;
      }
      .journal-header {
        display: flex;
        justify-content: space-between;
        gap: 14px;
        border-bottom: 2px solid #0f4068;
        padding-bottom: 10px;
        margin-bottom: 14px;
      }
      .journal-title-wrap h1 {
        margin: 0;
        font-size: 24px;
        line-height: 1.2;
        letter-spacing: -0.3px;
        color: #0f172a;
      }
      .journal-title-wrap p {
        margin: 4px 0 0;
        font-size: 11px;
        color: #475569;
        letter-spacing: 1px;
      }
      .journal-meta {
        min-width: 230px;
        border: 1px solid #c8d7e6;
        border-radius: 8px;
        overflow: hidden;
      }
      .meta-item {
        display: flex;
        justify-content: space-between;
        gap: 10px;
        padding: 7px 10px;
        border-bottom: 1px solid #dbe6f0;
        background: #f8fbff;
      }
      .meta-item:last-child { border-bottom: 0; }
      .meta-item span { color: #334155; font-weight: 600; }
      .meta-item strong { color: #0f172a; font-weight: 700; }
      .journal-section {
        margin-bottom: 12px;
        page-break-inside: avoid;
      }
      .section-title {
        margin: 0 0 6px;
        padding: 6px 10px;
        font-size: 14px;
        font-weight: 700;
        color: #0f2f4a;
        border-left: 4px solid #0f4068;
        background: #f4f8fc;
      }
      table { width: 100%; border-collapse: collapse; table-layout: fixed; }
      .kv-table th,
      .kv-table td,
      .list-table th,
      .list-table td {
        border: 1px solid #ced8e4;
        padding: 7px 8px;
        vertical-align: top;
        word-break: break-word;
      }
      .kv-table th {
        width: 22%;
        text-align: left;
        background: #f7fbff;
        color: #1e3a5f;
        font-weight: 700;
      }
      .kv-table td { background: #fff; color: #111827; }
      .list-table th {
        background: #eff5fb;
        color: #1e3a5f;
        font-weight: 700;
        text-align: center;
      }
      .list-table td { background: #fff; }
      .history-table th:nth-child(1),
      .history-table td:nth-child(1) {
        width: 9%;
        text-align: center;
      }
      .empty-cell {
        text-align: center;
        color: #64748b;
        background: #fbfdff;
        padding: 11px;
      }
      .text-block {
        min-height: 60px;
        padding: 10px;
        border: 1px solid #ced8e4;
        background: #fff;
        white-space: pre-wrap;
        word-break: break-word;
      }
      .text-block-lg { min-height: 120px; }
      .journal-sign {
        margin-top: 18px;
        display: flex;
        justify-content: flex-end;
        gap: 10px;
      }
      .sign-box {
        width: 190px;
        border: 1px solid #ced8e4;
        padding: 7px 10px;
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      .sign-box span { font-weight: 600; color: #334155; }
      .sign-box strong {
        display: inline-block;
        min-width: 90px;
        min-height: 20px;
        border-bottom: 1px solid #d7e0ea;
        text-align: center;
      }
      @media print {
        @page { size: A4 portrait; margin: 10mm; }
        body { background: #fff; }
        .print-page {
          width: auto;
          min-width: 0;
          margin: 0;
          padding: 0;
          box-shadow: none;
        }
      }
    `;

    const admissionPrintStyles = `
      :root { color-scheme: light; }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        padding: 0;
        background: #fff;
        color: #1f2937;
        font-family: "Noto Sans KR", "Malgun Gothic", "Apple SD Gothic Neo", sans-serif;
      }
      .print-page {
        width: auto;
        margin: 0 auto;
        padding: 0;
        background: #fff;
        box-shadow: none;
      }
      .journal-section-admission {
        margin: 0;
        page-break-inside: auto;
      }
      .ap-document-stage {
        position: relative !important;
        width: 966px !important;
        margin: 0 auto !important;
        overflow: hidden !important;
      }
      .print-page .ap-paper {
        background-image: none !important;
        background-color: #fff !important;
      }
      .ap-paper .ap-terms-block span {
        line-height: 30px !important;
        margin-bottom: 5px !important;
      }
      .ap-page-ink-print-img {
        left: -157px !important;
        width: 1280px !important;
        height: 100% !important;
        object-fit: fill !important;
        opacity: 1 !important;
        visibility: visible !important;
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
      }
      .ap-paper table,
      .ap-paper tr,
      .ap-paper th,
      .ap-paper td {
        page-break-inside: avoid !important;
        break-inside: avoid-page !important;
      }
      .ap-guardian-table,
      .ap-reason-table {
        page-break-inside: avoid !important;
        break-inside: avoid-page !important;
      }
      @media print {
        @page { size: A4 portrait; margin: 10mm; }
        body { background: #fff; }
        .print-page {
          width: auto;
          min-width: 0;
          margin: 0;
          padding: 0;
          box-shadow: none;
        }
      }
    `;

    const printStyles = isAdmissionPrint ? admissionPrintStyles : journalPrintStyles;
    const title = isAdmissionPrint ? '입원서약서출력' : '상담일지출력';
    const bodyClassName = isAdmissionPrint ? 'admission-print-body' : '';
    const pageClassName = isAdmissionPrint ? 'print-page admission-print-page' : 'print-page';

    const html = `
      <html>
        <head>
          <title>${title}</title>
          <meta charset="UTF-8">
          <link rel="stylesheet" href="${APP_CTX}/css/csm/counsel/admissionPledge.css">
          <style>${printStyles}</style>
        </head>
        <body class="${bodyClassName}">
          <div class="${pageClassName}">${bodyHtml}</div>
          <script>
            window.addEventListener('load', function() {
              setTimeout(function() { window.print(); }, 120);
            });
            window.onafterprint = function(){ window.close(); };
          <\/script>
        </body>
      </html>`;
    popup.document.open();
    popup.document.write(html);
    popup.document.close();
  };

  if (journalPrintButton && modal) {
    journalPrintButton.addEventListener('click', () => modal.style.display = 'flex');
  } else if (journalPrintButton) {
    // 프린트 모달 마크업이 없는 페이지에서도 일지출력 버튼 동작
    journalPrintButton.addEventListener('click', () => openPrintWindow('journal'));
  }
  if (admissionPledgePrintButton) {
    admissionPledgePrintButton.addEventListener('click', () => openPrintWindow('admission'));
  }
  if (closeModalButton && modal) closeModalButton.addEventListener('click', () => modal.style.display = 'none');
  window.addEventListener('click', (e) => { if (modal && e.target === modal) modal.style.display = 'none'; });

  const printButton = document.getElementById('printBtn');
  if (printButton) printButton.addEventListener('click', () => openPrintWindow('journal'));

  /* -------- 히스토리 모달/보호자 -------- */
  document.addEventListener('click', function (event) {
    if (event.target && event.target.classList.contains('sms_icon')) {
      const phoneContainer = document.getElementById('phone-container');
      const inputValueDisplay = document.getElementById('input-value-display');
      if (!phoneContainer || !inputValueDisplay) return;

      inputValueDisplay.innerHTML = '';

      const guardians = Array.from(document.querySelectorAll('.guardian-entry')).map(entry => ({
        name: entry.querySelector('.protector_name')?.value.trim() || '',
        relationship: entry.querySelector('.protector_connection')?.value.trim() || '',
        phone: entry.querySelector('.protector_phone')?.value.trim() || ''
      }));

      if (guardians.some(g => !g.phone)) {
        alert('보호자의 전화번호를 모두 입력해주세요.');
        return;
      }

      const valid = guardians.filter(g => g.phone);
      valid.forEach((g, idx) => createGuardianRowInPopup(inputValueDisplay, g, idx === valid.length - 1));

      $.ajax({
        url: `${APP_CTX}/sms/log`,
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ to_phone: valid.map(g => g.phone) }),
        success(resp) {
          let html = '';
          resp.forEach(r => {
            html += `
              <div>
                <div class="sms-history">
                  ${r.contents}
                  <div class="sms-date">
                    <div>${r.to_phone}</div>
                    <div>${formatDate(r.created_at)}</div>
                  </div>
                </div>
              </div>`;
          });
          $('#history-wrapper').html(html);
        },
        error(err) { console.error('SMS 로그 조회 실패:', err); }
      });

      phoneContainer.style.display = 'block';
    }

    // 팝업 내 보호자 추가 버튼 (+)
    if (event.target && event.target.classList.contains('add-guardian-btn')) {
      event.target.remove();
      const container = document.getElementById('input-value-display');
      if (!container) return;
      createGuardianRowInPopup(container, { name:'', relationship:'', phone:'' }, true);
      reassignPlusButton();
    }
  });

  function createGuardianRowInPopup(containerElement, guardian, showPlusButton) {
    const row = document.createElement('div');
    row.className = 'guardian-item';

    if (guardian.phone) {
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.classList.add('guardian-checkbox');
      checkbox.checked = true;
      checkbox.value = guardian.phone;
      row.appendChild(checkbox);

      const phoneText = document.createElement('span');
      phoneText.textContent = ` ${guardian.phone} `;
      row.appendChild(phoneText);

      if (guardian.name || guardian.relationship) {
        const details = document.createElement('span');
        details.textContent = ` (${guardian.name}/${guardian.relationship})`;
        row.appendChild(details);
      }
    } else {
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.classList.add('guardian-checkbox');
      checkbox.checked = true;
      checkbox.value = '';
      row.appendChild(checkbox);

      const phoneInput = document.createElement('input');
      phoneInput.type = 'text';
      phoneInput.classList.add('popup-phone-input');
      phoneInput.placeholder = '휴대폰 번호';
      phoneInput.addEventListener('input', function () {
        oninputPhone(this);
        checkbox.value = this.value.trim();
      });
      phoneInput.addEventListener('keydown', handleBackspace);
      row.appendChild(phoneInput);

      const deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.classList.add('remove-input-btn');
      deleteBtn.textContent = '';
      deleteBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        row.remove();
        reassignPlusButton();
      });
      row.appendChild(deleteBtn);
    }

    if (showPlusButton) {
      const plusBtn = document.createElement('button');
      plusBtn.type = 'button';
      plusBtn.classList.add('add-guardian-btn');
      plusBtn.textContent = '';
      row.appendChild(plusBtn);
    }

    containerElement.appendChild(row);
  }

  function reassignPlusButton() {
    document.querySelectorAll('.add-guardian-btn').forEach(btn => btn.remove());
    const container = document.getElementById('input-value-display');
    if (!container) return;

    const items = container.querySelectorAll('.guardian-item');
    if (items.length === 0) {
      createGuardianRowInPopup(container, { name:'', relationship:'', phone:'' }, true);
      return;
    }
    const last = items[items.length - 1];
    if (!last.querySelector('.add-guardian-btn')) {
      const plusBtn = document.createElement('button');
      plusBtn.type = 'button';
      plusBtn.classList.add('add-guardian-btn');
      plusBtn.textContent = '';
      last.appendChild(plusBtn);
    }
  }

  /* -------- 보호자 행 추가/삭제 (폼 영역) -------- */
  window.handleSmsIconClick = function (icon) {
    const inputField = icon.closest('.input-with-icon')?.querySelector('input');
    if (inputField) console.log('Phone number clicked:', inputField.value);
  };

  window.addGuardian = function () {
    const container = document.getElementById('guardianContainer');
    if (!container) return console.error('Guardian container not found');

    container.querySelectorAll('.addGuardianButton').forEach(btn => { btn.removeEventListener('click', addGuardian); btn.remove(); });

    const entry = document.createElement('div');
    entry.className = 'guardian-entry';
    entry.innerHTML = `
      <div class="form-group">
        <label class="protector_name_text" for="protector_name">보호자명</label>
        <input class="protector_name" type="text" name="cs_col_13[]" value="">
      </div>
      <div class="form-group">
        <label class="protector_connection_text" for="protector_connection">관계</label>
        <input class="protector_connection" type="text" name="cs_col_14[]" value="">
      </div>
      <div class="form-group guardian-phone">
        <label class="protector_phone_text" for="protector_phone">연락처</label>
        <div class="input-with-icon">
          <input class="protector_phone" type="text" name="cs_col_15[]" value=""
                 oninput="oninputPhone(this)" onkeydown="handleBackspace(event)" maxlength="13" autocomplete="off">
          <img class="sms_icon" src="${APP_CTX}/icon/ev/sender-icon.png">
        </div>
      </div>
      <button type="button" class="removeGuardian" onclick="removeGuardian(this)"></button>
    `;
    container.appendChild(entry);

    entry.querySelector('.sms_icon')?.addEventListener('click', function () { handleSmsIconClick(this); });

    const entries = container.getElementsByClassName('guardian-entry');
    const last = entries[entries.length - 1];
    if (last && !last.querySelector('.addGuardianButton')) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'addGuardianButton';
      btn.textContent = '';
      btn.onclick = addGuardian;
      last.appendChild(btn);
    }
  };

  window.removeGuardian = function (button) {
    const container = document.getElementById('guardianContainer');
    if (!container) return;
    const entries = container.getElementsByClassName('guardian-entry');
    if (entries.length <= 1) return console.log('최소 1개의 보호자 항목은 유지됩니다.');

    const entry = button.parentNode;
    entry.parentNode.removeChild(entry);

    const remain = container.getElementsByClassName('guardian-entry');
    const last = remain[remain.length - 1];
    if (last && !last.querySelector('.addGuardianButton')) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'addGuardianButton';
      btn.textContent = '';
      btn.onclick = addGuardian;
      last.appendChild(btn);
    }
  };

  /* -------- 기타 초기 세팅 -------- */
  const dateInput = document.getElementById('cs_col_16');
  if (dateInput && !dateInput.value) {
    const today = new Date();
    dateInput.value = `${today.getFullYear()}-${pad2(today.getMonth()+1)}-${pad2(today.getDate())}`;
  }

  const fromPhone = document.getElementById('from-phone');
  const savedFromPhone = localStorage.getItem('fromphone');
  if (fromPhone && savedFromPhone) fromPhone.value = savedFromPhone;
  if (fromPhone) fromPhone.addEventListener('change', () => {
    localStorage.setItem('fromphone', fromPhone.value || '');
  });

  const reserTitle = document.getElementById('reservation-title');
  const reserHidden = document.getElementById('reservation');
  if (reserHidden && reserTitle) {
    reserHidden.addEventListener('input', function () {
      if (this.value.trim() !== '') {
        reserTitle.style.backgroundColor = '#06603A';
        reserTitle.style.color = '#fff';
      } else {
        reserTitle.style.backgroundColor = '#EEE';
        reserTitle.style.color = '#545454';
      }
    });
  }

  // field_*_* 공통 규칙:
  // 같은 prefix(field_1_16)를 가진 _text/_select에 값이 있으면 _checkbox 자동 체크
  function getFieldBase(name) {
    if (!name) return null;
    const m = name.match(/^(field_\d+_\d+)_(checkbox|text|details|select)$/);
    return m ? m[1] : null;
  }

  function hasValue(el) {
    if (!el) return false;
    return String(el.value ?? '').trim() !== '';
  }

  function syncCheckboxByBase(base, checkOnly = false) {
    if (!base) return;

    const checkbox = document.querySelector(`input[type='checkbox'][name='${base}_checkbox']`);
    if (!checkbox) return;

    const textInput = document.querySelector(`input[name='${base}_text'], input[name='${base}_details']`);
    const selectBox = document.querySelector(`select[name='${base}_select']`);

    // checkbox_only 타입: 연동할 text/select 없으므로 건드리지 않음
    if (!textInput && !selectBox) return;

    const hasAnyValue = hasValue(textInput) || hasValue(selectBox);
    if (hasAnyValue) {
      checkbox.checked = true;
    } else if (!checkOnly) {
      checkbox.checked = false;
    }
  }

  // 초기값 반영 (수정 화면 진입 시에도 적용)
  // checkOnly=true: 초기 로드 시 서버 렌더 상태(th:checked)를 신뢰하여 해제하지 않음
  document
    .querySelectorAll("input[name^='field_'], select[name^='field_']")
    .forEach(el => {
      const base = getFieldBase(el.name);
      syncCheckboxByBase(base, true);
    });

  // 입력/선택 변경 시 즉시 반영
  document
    .querySelectorAll("input[name^='field_'][type='text'], select[name^='field_']")
    .forEach(el => {
      const evt = el.tagName === 'SELECT' ? 'change' : 'input';
      el.addEventListener(evt, function () {
        const base = getFieldBase(this.name);
        syncCheckboxByBase(base);
      });
    });

  // 예약시간 select 옵션 채우기
  const hourSelect = document.getElementById('reservation-hour');
  const minSelect  = document.getElementById('reservation-minute');
  if (hourSelect && hourSelect.options.length <= 1) {
    for (let h = 0; h < 24; h++) {
      const opt = document.createElement('option');
      opt.value = pad2(h);
      opt.textContent = pad2(h);
      hourSelect.appendChild(opt);
    }
  }
  if (minSelect && minSelect.options.length <= 1) {
    for (let m = 0; m < 60; m += 5) {
      const opt = document.createElement('option');
      opt.value = pad2(m);
      opt.textContent = pad2(m);
      minSelect.appendChild(opt);
    }
  }

  // 오늘 날짜일 때 과거 시/분 비활성화
  $('#reservation-date').on('change', function () {
    const selectedDate = $(this).val();
    const todayStr = new Date().toISOString().split('T')[0];
    const $hour = $('#reservation-hour');
    const $min  = $('#reservation-minute');

    $hour.find('option').prop('disabled', false);
    $min.find('option').prop('disabled', false);

    if (selectedDate === todayStr) {
      const now = new Date();
      const nowHour = now.getHours();
      const nowMin = now.getMinutes();

      $hour.find('option').each(function(){
        const h = parseInt($(this).val());
        if (!isNaN(h) && h < nowHour) $(this).prop('disabled', true);
      });

      $hour.off('change._today').on('change._today', function () {
        const selH = parseInt($(this).val());
        $min.find('option').prop('disabled', false);
        if (selH === nowHour) {
          $min.find('option').each(function () {
            const m = parseInt($(this).val());
            if (!isNaN(m) && m < nowMin) $(this).prop('disabled', true);
          });
        }
      }).trigger('change._today');
    }
  });

  // 예약 숨김필드(YYYYMMDDHHMM) 세팅 & 현재보다 이전이면 경고
  $('#reservation-date, #reservation-hour, #reservation-minute').on('change', function () {
    const date = $('#reservation-date').val();   // YYYY-MM-DD
    const hour = $('#reservation-hour').val();   // HH
    const min  = $('#reservation-minute').val(); // mm
    const $hidden = $('#reservation');

    if (!date || !hour || !min) {
      $hidden.val('');
      return;
    }

    const compact = date.replace(/-/g,'') + hour + min; // YYYYMMDDHHmm
    const now = new Date();
    const nowCompact = `${now.getFullYear()}${pad2(now.getMonth()+1)}${pad2(now.getDate())}${pad2(now.getHours())}${pad2(now.getMinutes())}`;

    if (compact < nowCompact) {
      alert('현재 시간 이후만 예약할 수 있습니다.');
      $('#reservation-hour').val('');
      $('#reservation-minute').val('');
      $hidden.val('');
      return;
    }
    $hidden.val(compact);
    console.log('📌 예약 발송 시간(YYYYMMDDHHmm):', compact);
  });

  // 디버그: 메시지 입력 로깅(원하면 주석처리)
  const msgTA = document.getElementById('message-textarea');
  if (msgTA) msgTA.addEventListener('input', e => {
    console.log('Current textarea value:', JSON.stringify(e.target.value));
  });

}); // DOMContentLoaded 종료

/* -------------------------
 * 로그 상세 모달 (그대로 유지 + 가드)
 * ------------------------- */
function logsOpen(rowEl) {
  if (!rowEl) return;

  document.querySelectorAll('.logs-body .logs-content.is-active').forEach(function (el) {
    el.classList.remove('is-active');
  });
  rowEl.classList.add('is-active');

  var logIdx         = rowEl.dataset.idx;
  var createdAt      = rowEl.dataset.createdAt || '';
  var counselMethod  = rowEl.dataset.counselMethod || '';
  var counselResult  = rowEl.dataset.counselResult || '';
  var counselName    = rowEl.dataset.counselName || '';
  var counselContent = rowEl.dataset.counselContent || '';
  var formattedDate  = (createdAt || '').substring(0, 10);

  function normalizeDate(value) {
    var text = String(value || '').trim();
    if (!text) return '';
    var match = text.match(/^(\d{4}-\d{2}-\d{2})/);
    return match ? match[1] : text;
  }

  function normalizeLogValue(value) {
    return String(value == null ? '' : value).trim();
  }

  function applyLogData(data) {
    var dateValue = normalizeDate(data.counsel_at || data.created_at || '');
    var methodValue = normalizeLogValue(data.counsel_method);
    var resultValue = normalizeLogValue(data.counsel_result);
    var nameValue = normalizeLogValue(data.counsel_name);
    var contentValue = String(data.counsel_content == null ? '' : data.counsel_content);

    $('#log_cs_col_16').val(dateValue || '-');
    $('#log_cs_col_17').val(nameValue || '-');
    $('#log_cs_col_18').val(methodValue || '-');
    $('#log_cs_col_19').val(resultValue || '-');
    $('#log_cs_col_32').val(contentValue);

    var reservationFields = $('#reservationFields-log');
    var noAdmissionReason = $('#noAdmissionReason-log');
    if (resultValue === '입원예약') {
      reservationFields.show(); noAdmissionReason.hide();
    } else if (resultValue === '입원안함') {
      reservationFields.hide(); noAdmissionReason.show();
    } else {
      reservationFields.hide(); noAdmissionReason.hide();
    }
  }

  var popup = document.getElementById('popup');
  if (!popup) return;
  applyLogData({
    counsel_at: formattedDate,
    counsel_method: counselMethod,
    counsel_result: counselResult,
    counsel_name: counselName,
    counsel_content: counselContent
  });

  var guardianContainer = $('#guardianContainer-log').empty();

  $.ajax({
    url: `${APP_CTX}/getGuardianData`,
    type: 'get',
    data: { logIdx: logIdx },
    dataType: 'json',
    success: function (guardians) {
      if (guardians && guardians.length > 0) {
        $.each(guardians, function (index, g) {
          guardianContainer.append(
            '<div class="guardian-entry-log">'
            + '  <div class="form-group form-group2">'
            + '    <label for="protector_name-log">보호자명</label>'
            + '    <input class="protector_name-log" type="text" name="log_cs_col_13[]" value="' + (g.counsel_guardian || '') + '" readonly="readonly">'
            + '  </div>'
            + '  <div class="form-group form-group2">'
            + '    <label for="protector_connection-log">관계</label>'
            + '    <input class="protector_connection-log" type="text" name="log_cs_col_14[]" value="' + (g.counsel_relationship || '') + '" readonly="readonly">'
            + '  </div>'
            + '  <div class="form-group form-group2">'
            + '    <label for="protector_phone">연락처</label>'
            + '    <input class="protector_phone" style="width: calc(100%) !important; padding: 6px 15px 6px 10px !important;"'
            + '           type="text" name="log_cs_col_15[]" value="' + (g.counsel_number || '') + '" readonly="readonly"'
            + '           pattern="^\\+?[0-9\\-]{7,15}$" title="유효한 연락처를 입력해주세요.">'
            + '  </div>'
            + '</div>'
          );
        });
      } else {
        guardianContainer.html('<p>보호자 정보가 없습니다.</p>');
      }
    },
    error: function (xhr, status, error) {
      console.error('Error fetching guardian data:', error);
      guardianContainer.html('<p>보호자 정보를 불러오는 중 오류가 발생했습니다.</p>');
    }
  });

  $.ajax({
    url: `${APP_CTX}/counsel/log/detail`,
    type: 'get',
    data: { logIdx: logIdx },
    dataType: 'json',
    success: function (resp) {
      if (!resp) return;
      if (resp.result && resp.result !== '1') {
        return;
      }
      var detail = resp.log || resp;
      applyLogData(detail);
    },
    error: function (xhr, status, error) {
      console.error('Error fetching counsel log detail:', error);
    }
  });

  $('#popup').css('display', 'flex').hide().fadeIn();
}
  function toggleCsCol07Text(value) {
    const textInput = document.getElementById('cs_col_07_text');
    const show = value === '기타' || value === '요양원' || value === '요양병원' || value === '급성기병원';
    textInput.style.display = show ? '' : 'none';
    if (!show) textInput.value = '';
  }

  function toggleCsCol08Text(value) {
    const textInput = document.getElementById('cs_col_08_text');
    const show = value !== ''; // 비어있지 않으면 표시
    textInput.style.display = show ? '' : 'none';
    if (!show) textInput.value = '';
  }

  document.addEventListener('DOMContentLoaded', function () {
    const sel7 = document.getElementById('cs_col_07');
    const sel8 = document.getElementById('cs_col_08');
    if (sel7) toggleCsCol07Text(sel7.value);
    if (sel8) toggleCsCol08Text(sel8.value);
  });
