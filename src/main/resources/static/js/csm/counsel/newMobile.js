document.addEventListener('DOMContentLoaded', function () {
  const form = document.getElementById('mobileCounselForm');
  const spinner = document.getElementById('spinner-overlay');
  const goListBtn = document.getElementById('goListBtn');
  const addGuardianBtn = document.getElementById('addGuardianBtn');
  const guardianContainer = document.getElementById('guardianContainer');
  const birthInput = document.getElementById('cs_col_03');
  const ageInput = document.getElementById('cs_col_04');
  const counselDateInput = document.getElementById('cs_col_16');
  const resultSelect = document.getElementById('cs_col_19');
  const counselContentInput = document.getElementById('cs_col_32');
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
  const clovaSttAvailable = (document.getElementById('clova_stt_available')?.value || 'N') === 'Y';
  const openAiSummaryAvailable = (document.getElementById('openai_summary_available')?.value || 'N') === 'Y';

  const startRecordingBtn = document.getElementById('startRecordingBtn');
  const stopRecordingBtn = document.getElementById('stopRecordingBtn');
  const uploadAudioFileBtn = document.getElementById('uploadAudioFileBtn');
  const audioFileInput = document.getElementById('audioFileInput');
  const applyDiseaseMatchBtn = document.getElementById('applyDiseaseMatchBtn');
  const generateSummaryBtn = document.getElementById('generateSummaryBtn');
  const recordingStatusEl = document.getElementById('recordingStatus');
  const recordingIndicatorEl = document.getElementById('recordingIndicator');
  const recordingTimerEl = document.getElementById('recordingTimer');
  const floatingToolsEl = document.querySelector('.floating-tools');
  const recordPreviewEl = document.getElementById('recordPreview');
  const audioRecordListEl = document.getElementById('audioRecordList');
  const audioTempKeyInput = document.getElementById('audio_temp_key');
  const csIdxRaw = document.querySelector('input[name="cs_idx"]')?.value || '';
  const csIdx = /^\d+$/.test(csIdxRaw) ? Number(csIdxRaw) : null;
  const defaultAdmissionPledgeText = '본인은 입원 연계 및 상담을 위해 제공한 정보가 병원 입원 진행에 활용되는 것에 동의합니다. 또한 상담 과정에서 안내받은 내용을 확인하였으며, 안내된 절차에 따라 성실히 협조할 것을 서약합니다.';

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || '';

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
    const markers = ['/counsel/', '/core/', '/login', '/findpwd', '/ResetPwd', '/admin', '/setting', '/statistics'];
    for (const marker of markers) {
      const idx = path.indexOf(marker);
      if (idx >= 0) return normalize(path.substring(0, idx));
    }
    return '';
  })();

  function showSpinner() {
    if (spinner) {
      spinner.style.display = 'flex';
    }
  }

  function pad2(n) {
    return String(n).padStart(2, '0');
  }

  function todayYmd() {
    const d = new Date();
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
  }

  function nowDateTime() {
    const d = new Date();
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
  }

  function requiresAdmissionPledge(value) {
    return value === '입원예약' || value === '입원완료';
  }

  function isPngDataUrl(value) {
    return /^data:image\/png;base64,/.test(String(value || '').trim());
  }

  function extractAdmissionSignatureBundle(raw) {
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
  }
  function extractPrimaryAdmissionSignature(raw) {
    return extractAdmissionSignatureBundle(raw).primary;
  }
  function readCurrentGuardianEntry(index) {
    const entry = Array.from(document.querySelectorAll('.guardian-entry'))[index];
    return {
      name: entry?.querySelector("input[name='cs_col_13[]']")?.value?.trim() || '',
      relation: entry?.querySelector("input[name='cs_col_14[]']")?.value?.trim() || '',
      phone: entry?.querySelector("input[name='cs_col_15[]']")?.value?.trim() || ''
    };
  }

  function isAdmissionPledgeComplete() {
    if ((admissionPledgeRequiredInput?.value || 'N') !== 'Y') {
      return true;
    }
    return (admissionAgreedYnInput?.value || 'N') === 'Y';
  }

  function setAdmissionPledgeStatus() {
    if (!admissionPledgeStatus) return;
    if ((admissionPledgeRequiredInput?.value || 'N') !== 'Y') {
      admissionPledgeStatus.textContent = '해당 없음';
      return;
    }
    admissionPledgeStatus.textContent = isAdmissionPledgeComplete() ? '작성 완료' : '미작성';
  }

  function payloadCsIdx(payload) {
    const raw = String(payload?.cs_idx ?? '').trim();
    return /^\d+$/.test(raw) ? Number(raw) : 0;
  }

  function canApplyAdmissionPledgePayload(payload) {
    const pIdx = payloadCsIdx(payload);
    if (csIdx != null && csIdx > 0) {
      return pIdx > 0 && pIdx === csIdx;
    }
    return pIdx === 0;
  }

  function toggleAdmissionPledgeLauncher(value) {
    const required = admissionPledgeModuleEnabled && requiresAdmissionPledge(value);
    if (admissionPledgeRequiredInput) {
      admissionPledgeRequiredInput.value = required ? 'Y' : 'N';
    }
    if (admissionPledgeLauncher) {
      admissionPledgeLauncher.style.display = required ? '' : 'none';
    }
    setAdmissionPledgeStatus();
  }

  function applyAdmissionPledgePayload(payload) {
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
  }

  function formatDateInput(value) {
    const numbers = (value || '').replace(/\D/g, '').slice(0, 8);
    if (numbers.length < 5) return numbers;
    if (numbers.length < 7) return `${numbers.slice(0, 4)}-${numbers.slice(4)}`;
    return `${numbers.slice(0, 4)}-${numbers.slice(4, 6)}-${numbers.slice(6)}`;
  }

  function calculateAge(ymd) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(ymd)) return '';
    const birth = new Date(ymd);
    if (Number.isNaN(birth.getTime())) return '';
    const now = new Date();
    let age = now.getFullYear() - birth.getFullYear();
    const m = now.getMonth() - birth.getMonth();
    const d = now.getDate() - birth.getDate();
    if (m < 0 || (m === 0 && d < 0)) age--;
    return age >= 0 ? String(age) : '';
  }

  function formatPhone(value) {
    const input = (value || '').replace(/[^0-9]/g, '');
    if (input.startsWith('02')) {
      if (input.length <= 2) return input;
      if (input.length <= 5) return input.replace(/(\d{2})(\d{1,3})/, '$1-$2');
      if (input.length <= 9) return input.replace(/(\d{2})(\d{3})(\d{1,4})/, '$1-$2-$3');
      return input.replace(/(\d{2})(\d{4})(\d{4})/, '$1-$2-$3');
    }
    if (input.length <= 3) return input;
    if (input.length <= 7) return input.replace(/(\d{3})(\d{1,4})/, '$1-$2');
    if (input.length <= 10) return input.replace(/(\d{3})(\d{3})(\d{1,4})/, '$1-$2-$3');
    return input.replace(/(\d{3})(\d{4})(\d{4})/, '$1-$2-$3');
  }

  function bindPhoneMask(input) {
    if (!input) return;
    input.addEventListener('input', function () {
      this.value = formatPhone(this.value);
    });
  }

  function bindAllPhoneMasks() {
    document.querySelectorAll('input[name="cs_col_15[]"]').forEach(bindPhoneMask);
  }

  function guardianTemplate() {
    return `
      <div class="guardian-entry">
        <div class="field-grid">
          <div class="field">
            <label>보호자명</label>
            <input type="text" name="cs_col_13[]">
          </div>
          <div class="field">
            <label>관계</label>
            <input type="text" name="cs_col_14[]">
          </div>
        </div>
        <div class="field guardian-phone-row">
          <label>연락처</label>
          <div class="inline-with-btn">
            <input type="text" name="cs_col_15[]" class="guardian-phone" maxlength="13">
            <button type="button" class="mini-btn remove-guardian">삭제</button>
          </div>
        </div>
      </div>
    `;
  }

  function getFieldBase(name, element) {
    if (element?.dataset?.fieldBase) return element.dataset.fieldBase;
    if (!name) return null;

    const m = name.match(/^(field_\d+_\d+)_(checkbox|text|details|select)$/);
    if (m) return m[1];

    const rm = name.match(/^field_(\d+)_radio$/);
    if (rm && element && element.type === 'radio') {
      const subId = String(element.value || '').split('_')[0];
      if (/^\d+$/.test(subId)) {
        return `field_${rm[1]}_${subId}`;
      }
    }
    return null;
  }

  function hasValue(el) {
    if (!el) return false;
    return String(el.value ?? '').trim() !== '';
  }

  function syncCheckboxByBase(base) {
    if (!base) return;
    const checkbox = document.querySelector(`input[type='checkbox'][name='${base}_checkbox']`);
    if (!checkbox) return;
    const textInput = document.querySelector(`input[name='${base}_text'], input[name='${base}_details']`);
    const selectBox = document.querySelector(`select[name='${base}_select']`);
    checkbox.checked = hasValue(textInput) || hasValue(selectBox) || checkbox.checked;
  }

  function bindFieldAutoCheck() {
    document
      .querySelectorAll("input[name^='field_'], select[name^='field_']")
      .forEach((el) => {
        const base = getFieldBase(el.name, el);
        syncCheckboxByBase(base);
      });

    document
      .querySelectorAll("input[name^='field_'][type='text'], select[name^='field_']")
      .forEach((el) => {
        const evt = el.tagName === 'SELECT' ? 'change' : 'input';
        el.addEventListener(evt, function () {
          const base = getFieldBase(this.name, this);
          syncCheckboxByBase(base);
        });
      });
  }

  const printPreviewBtn = document.getElementById('print-preview');
  const admissionPledgePrintBtn = document.getElementById('print-admission-pledge');

  function getElementByIdOrName(key) {
    return document.getElementById(key) || document.querySelector(`[name="${key}"]`);
  }

  function printValue(key) {
    const el = getElementByIdOrName(key);
    if (!el) return '';
    return String(el.value || '').trim();
  }

  function printSelectedText(key) {
    const el = getElementByIdOrName(key);
    if (!el) return '';
    if (el.tagName === 'SELECT') {
      const opt = el.options?.[el.selectedIndex];
      const optValue = String(opt?.value || '').trim();
      if (!optValue) return '';
      return (opt?.textContent || optValue).trim();
    }
    if (el.type === 'checkbox' || el.type === 'radio') {
      return el.checked ? String(el.value || '').trim() : '';
    }
    return String(el.value || '').trim();
  }

  function printCheckedText(key, text = '선택') {
    const el = getElementByIdOrName(key);
    if (!el) return '';
    if (el.type === 'checkbox') {
      return el.checked ? text : '';
    }
    const value = String(el.value || '').trim();
    if (!value) return '';
    if (value === 'Y' || value.toLowerCase() === 'true') return text;
    return value;
  }

  function combineSelectWithDetail(selectId, detailId) {
    const primary = printSelectedText(selectId);
    const detail = printValue(detailId);
    if (primary && detail) return `${primary} (${detail})`;
    return primary || detail || '';
  }

  function printDisplayText(v) {
    const text = String(v ?? '').trim();
    return text || '-';
  }

  function renderPrintKvRows(items) {
    return items.map((item) => `
      <tr>
        <th style="text-align:left;border:1px solid #ccc;padding:6px;background:#f7f7f7;">${escapeHtml(item.label)}</th>
        <td style="border:1px solid #ccc;padding:6px;">${escapeHtml(printDisplayText(item.value))}</td>
      </tr>
    `).join('');
  }

  function getPrintFieldBase(el) {
    if (!el || !el.name) return null;
    const m = el.name.match(/^(field_\d+_\d+)_(checkbox|text|details|select)$/);
    if (m) return m[1];
    const rm = el.name.match(/^field_(\d+)_radio$/);
    if (rm && el.type === 'radio') {
      const subId = String(el.value || '').split('_')[0];
      if (/^\d+$/.test(subId)) {
        return `field_${rm[1]}_${subId}`;
      }
    }
    return null;
  }

  function readPrintLabelAround(el) {
    if (!el) return '';
    const fromData = String(el.dataset?.label || '').trim();
    if (fromData) return fromData;

    const row = el.closest('.custom-input-row, .input-wrapper');
    if (!row) return '';
    const label = row.querySelector('label');
    if (label && label.textContent) {
      const txt = label.textContent.trim();
      if (txt) return txt;
    }
    const span = row.querySelector('span');
    if (span && span.textContent) {
      const txt = span.textContent.trim();
      if (txt) return txt;
    }
    return '';
  }

  function collectMobileCustomFieldRowsForPrint() {
    const map = new Map();
    document.querySelectorAll("input[name^='field_'], select[name^='field_']").forEach((el) => {
      const base = getPrintFieldBase(el);
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
      const nearLabel = readPrintLabelAround(el);
      if (nearLabel) entry.label = nearLabel;

      if (el.tagName === 'SELECT') {
        const value = String(el.value || '').trim();
        if (value) {
          const opt = el.options?.[el.selectedIndex];
          entry.selectValue = (opt?.textContent || value).trim();
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
      if (parts.length === 0 && (entry.checked || entry.radio)) parts.push('선택');
      if (!label || parts.length === 0) return acc;
      acc.push({ label, value: parts.join(' / ') });
      return acc;
    }, []);
  }

  function buildMobilePrintHtmlFromForm(printMode = 'journal') {
    const patientInfoRows = renderPrintKvRows([
      { label: '환자명', value: printValue('cs_col_01') },
      { label: '성별', value: printSelectedText('cs_col_02') },
      { label: '생년월일', value: printValue('cs_col_03') },
      { label: '나이', value: printValue('cs_col_04') },
      { label: '보험유형', value: printSelectedText('cs_col_05') },
      { label: '실손보험', value: printCheckedText('cs_col_06') },
      { label: '현재계신곳', value: combineSelectWithDetail('cs_col_07', 'cs_col_07_text') },
      { label: '상담유입경로', value: combineSelectWithDetail('cs_col_08', 'cs_col_08_text') },
      { label: '잠재고객', value: printSelectedText('cs_col_09') },
      { label: 'BC', value: printCheckedText('cs_col_10') }
    ]);

    const counselInfoRows = renderPrintKvRows([
      { label: '상담일자', value: printValue('cs_col_16') },
      { label: '상담자', value: printValue('cs_col_17') },
      { label: '상담방법', value: printSelectedText('cs_col_18') },
      { label: '상담결과', value: printSelectedText('cs_col_19') },
      { label: '입원예정일', value: printValue('cs_col_21') },
      { label: '예정시간', value: printValue('cs_col_21_time') },
      { label: '예상 호실', value: printValue('cs_col_38') },
      { label: '미입원사유', value: printSelectedText('cs_col_20') }
    ]);

    const customRows = collectMobileCustomFieldRowsForPrint().map((item) => `
      <tr>
        <th>${escapeHtml(item.label)}</th>
        <td>${escapeHtml(printDisplayText(item.value))}</td>
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

    const historyRows = Array.from(
      document.querySelectorAll('.mobile-print-log-source .logs-body .logs-content')
    ).map((row, index) => {
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

    const documentNo = printValue('cs_idx') || '-';
    const issuedAt = new Date().toLocaleString('ko-KR', { hour12: false });
    const admissionRequired = printValue('admission_pledge_required') === 'Y';
    const admissionPageInk = printValue('admission_page_ink_data');

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
        name: printValue('admission_guardian_name') || mainGuardianFallback.name,
        relation: printValue('admission_guardian_relation') || mainGuardianFallback.relation,
        addr: printValue('admission_guardian_addr'),
        phone: printValue('admission_guardian_phone') || mainGuardianFallback.phone,
        costYn: printValue('admission_guardian_cost_yn') === 'Y'
      };
      const subGuardian = {
        name: printValue('admission_sub_guardian_name') || subGuardianFallback.name,
        relation: printValue('admission_sub_guardian_relation') || subGuardianFallback.relation,
        addr: printValue('admission_sub_guardian_addr'),
        phone: printValue('admission_sub_guardian_phone') || subGuardianFallback.phone,
        costYn: printValue('admission_sub_guardian_cost_yn') === 'Y'
      };
      const gender = printSelectedText('cs_col_02');
      const signerName = printValue('admission_signer_name') || printValue('cs_col_01');
      const signerRelation = printValue('admission_signer_relation') || '본인';
      const signedAt = printValue('admission_signed_at');
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
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(printValue('cs_col_01'))}">
                    </td>
                    <td class="normal" width="170px" height="56px">차트번호</td>
                    <td class="light" colspan="2" style="text-align: left;">
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(printValue('cs_col_40'))}">
                    </td>
                  </tr>
                  <tr>
                    <td class="normal" height="56px">입원병실</td>
                    <td class="light" style="text-align: left;">
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(printValue('cs_col_38'))}">
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
                      <input style="margin-left: 33px;" type="text" readonly value="${renderValueAttr(printValue('cs_col_03'))}">
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
              <div class="ap-signed-at normal">서명일시: <span>${escapeHtml(printDisplayText(signedAt))}</span></div>
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
              <strong>${escapeHtml(printDisplayText(documentNo))}</strong>
            </div>
            <div class="meta-item">
              <span>상담일자</span>
              <strong>${escapeHtml(printDisplayText(printValue('cs_col_16')))}</strong>
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
          <div class="text-block">${escapeHtml(printDisplayText(printValue('cs_col_11')))}</div>
        </section>

        <section class="journal-section">
          <h2 class="section-title">4. 상담내용</h2>
          <div class="text-block text-block-lg">${escapeHtml(printDisplayText(printValue('cs_col_32')))}</div>
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
            <tbody>${historyRows || '<tr><td colspan="5" class="empty-cell">이력이 없습니다.</td></tr>'}</tbody>
          </table>
        </section>

        <footer class="journal-sign">
          <div class="sign-box">
            <span>상담자</span>
            <strong>${escapeHtml(printDisplayText(printValue('cs_col_17')))}</strong>
          </div>
          <div class="sign-box">
            <span>확인자</span>
            <strong></strong>
          </div>
        </footer>
      </div>`;
  }

  function openMobilePrintWindow(printMode = 'journal') {
    const isAdmissionPrint = printMode === 'admission';
    const bodyHtml = buildMobilePrintHtmlFromForm(printMode);
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
  }

  const STOP_WORDS = new Set([
    '기타', '선택', '선택하세요', '없음', '유', '무', '예', '아니오', '확인',
    '입원', '외래', '진료', '상담', '기록', '질환', '병명', '암명', '진단명'
  ]);
  const DISEASE_FIELD_HINTS = ['병명', '암명', '질환', '진단', '주상병', '부상병'];
  const DISEASE_SUFFIX_REGEX = /(암|염|증|질환|장애|골절|치매|폐렴|당뇨|중풍|협심증|심근경색|뇌경색|뇌출혈|파킨슨|우울증|통증)$/;

  function normalizeMatchText(text) {
    return String(text || '').toLowerCase().replace(/[^가-힣a-z0-9]/g, '');
  }

  function splitCandidateWord(word) {
    const raw = String(word || '').trim();
    if (!raw) return [];

    const variants = new Set();
    const push = (v) => {
      const s = String(v || '').trim();
      if (!s || s.length < 2 || STOP_WORDS.has(s)) return;
      variants.add(s);
    };

    push(raw);
    const noParen = raw.replace(/\([^)]*\)/g, ' ').replace(/\[[^\]]*\]/g, ' ').trim();
    push(noParen);

    [raw, noParen].forEach((base) => {
      if (!base) return;
      base.split(/[\/,|+·]/).forEach(push);
      base.split(/\s+/).forEach(push);
    });

    return Array.from(variants);
  }

  function extractDiseaseTokens(sourceText) {
    const words = String(sourceText || '').match(/[가-힣]{2,15}/g) || [];
    const tokens = words
      .map((w) => w.trim())
      .filter((w) => w.length >= 2 && DISEASE_SUFFIX_REGEX.test(w));
    return [...new Set(tokens)].sort((a, b) => b.length - a.length);
  }

  function isDiseaseTarget(target) {
    const label = String(target?.label || '');
    if (DISEASE_FIELD_HINTS.some((hint) => label.includes(hint))) {
      return true;
    }
    return Array.isArray(target?.options) && target.options.some((opt) => DISEASE_SUFFIX_REGEX.test(String(opt || '').trim()));
  }

  function candidateWordsOfTarget(target) {
    const words = new Set();
    if (target.label) words.add(target.label);
    if (Array.isArray(target.options)) target.options.forEach((v) => words.add(v));

    const expanded = [];
    Array.from(words).forEach((v) => {
      splitCandidateWord(v).forEach((part) => expanded.push(part));
    });

    return [...new Set(expanded.map((v) => String(v || '').trim()).filter(Boolean))]
      .filter((w) => w.length >= 2 && !STOP_WORDS.has(w));
  }

  function collectCustomTargets() {
    const targets = new Map();
    document.querySelectorAll("[name^='field_']").forEach((el) => {
      const base = getFieldBase(el.name, el);
      if (!base) return;

      if (!targets.has(base)) {
        targets.set(base, { base, label: '', options: [], checkbox: null, radio: null, select: null, text: null });
      }
      const target = targets.get(base);
      const label = String(el.dataset.label || '').trim();
      if (label) target.label = label;

      if (el.type === 'checkbox') {
        target.checkbox = el;
      } else if (el.type === 'radio') {
        target.radio = el;
      } else if (el.tagName === 'SELECT') {
        target.select = el;
        target.options = Array.from(el.options || [])
          .map((opt) => String(opt.value || '').trim())
          .filter((v) => v && v !== '선택하세요');
      } else if (el.tagName === 'INPUT' && el.type === 'text') {
        target.text = el;
      }
    });
    return targets;
  }

  function findLongestMatchedKeyword(normalizedSource, candidates) {
    const sorted = [...candidates].sort((a, b) => b.length - a.length);
    for (const candidate of sorted) {
      const normalized = normalizeMatchText(candidate);
      if (!normalized || normalized.length < 2) continue;
      if (normalizedSource.includes(normalized)) {
        return candidate;
      }
    }
    return '';
  }

  function findBestOptionMatch(options, normalizedSource, diseaseTokens) {
    const opts = Array.isArray(options) ? options : [];
    if (opts.length === 0) return '';

    let bestValue = '';
    let bestScore = -1;

    opts.forEach((option) => {
      const value = String(option || '').trim();
      if (!value || value === '선택하세요') return;

      const variants = splitCandidateWord(value);
      let score = 0;

      variants.forEach((variant) => {
        const normalizedVariant = normalizeMatchText(variant);
        if (!normalizedVariant || normalizedVariant.length < 2) return;
        if (normalizedSource.includes(normalizedVariant)) {
          score = Math.max(score, normalizedVariant.length);
        }
      });

      if (score === 0 && diseaseTokens.length > 0) {
        const normalizedOption = normalizeMatchText(value);
        diseaseTokens.forEach((token) => {
          const normalizedToken = normalizeMatchText(token);
          if (!normalizedToken) return;
          if (normalizedOption.includes(normalizedToken) || normalizedToken.includes(normalizedOption)) {
            score = Math.max(score, Math.min(normalizedOption.length, normalizedToken.length));
          }
        });
      }

      if (score > bestScore) {
        bestScore = score;
        bestValue = value;
      }
    });

    return bestScore > 0 ? bestValue : '';
  }

  function applyDiseaseMatchFromText(sourceText) {
    const normalizedSource = normalizeMatchText(sourceText);
    if (!normalizedSource) return 0;

    const diseaseTokens = extractDiseaseTokens(sourceText);
    let matchedCount = 0;
    const targets = collectCustomTargets();
    targets.forEach((target) => {
      const candidates = candidateWordsOfTarget(target);
      const matchedKeyword = candidates.length > 0
        ? findLongestMatchedKeyword(normalizedSource, candidates)
        : '';
      const bestOption = findBestOptionMatch(target.options, normalizedSource, diseaseTokens);
      const diseaseFallback = isDiseaseTarget(target) && diseaseTokens.length > 0 ? diseaseTokens[0] : '';

      if (!matchedKeyword && !bestOption && !diseaseFallback) return;
      let touched = false;

      if (target.select && bestOption && target.select.value !== bestOption) {
        target.select.value = bestOption;
        target.select.dispatchEvent(new Event('change', { bubbles: true }));
        touched = true;
      }

      const textCandidate = bestOption || matchedKeyword || diseaseFallback;
      if (target.text && !String(target.text.value || '').trim()) {
        target.text.value = textCandidate;
        target.text.dispatchEvent(new Event('input', { bubbles: true }));
        touched = true;
      }

      if (target.checkbox && !target.checkbox.checked) {
        target.checkbox.checked = true;
        touched = true;
      }
      if (target.radio && !target.radio.checked) {
        target.radio.checked = true;
        touched = true;
      }

      syncCheckboxByBase(target.base);
      if (touched) {
        matchedCount += 1;
      }
    });
    return matchedCount;
  }

  function escapeHtml(value) {
    if (value == null) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function csrfHeaders() {
    if (!csrfToken || !csrfHeader) return {};
    return { [csrfHeader]: csrfToken };
  }

  function ensureAudioTempKey() {
    if (!audioTempKeyInput) return '';
    let key = String(audioTempKeyInput.value || '').trim();
    if (!key) {
      if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        key = window.crypto.randomUUID();
      } else {
        key = `tmp_${Date.now()}_${Math.random().toString(16).slice(2, 12)}`;
      }
      audioTempKeyInput.value = key;
    }
    return key;
  }

  function setRecordingStatus(message, isError) {
    if (!recordingStatusEl) return;
    recordingStatusEl.textContent = message || '대기 중';
    recordingStatusEl.style.color = isError ? '#cc2d2d' : '#5a5a5a';
  }

  function summaryTimestamp() {
    const d = new Date();
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    const hh = String(d.getHours()).padStart(2, '0');
    const mi = String(d.getMinutes()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
  }

  function stripSummaryBlock(text) {
    const raw = String(text || '');
    return raw.replace(/\[상담요약[^\]]*\][\s\S]*?\[\/상담요약\]\s*/g, '').trim();
  }

  function upsertSummaryBlock(summaryText) {
    if (!counselContentInput) return;
    const summary = String(summaryText || '').trim();
    if (!summary) return;

    const block = `[상담요약 ${summaryTimestamp()}]\n${summary}\n[/상담요약]`;
    const withoutSummary = stripSummaryBlock(counselContentInput.value || '');
    counselContentInput.value = withoutSummary ? `${block}\n\n${withoutSummary}` : block;
  }

  function audioPreviewMode(item) {
    const summary = String(item?.summaryText || '').trim();
    const transcript = String(item?.transcript || '').trim();
    if (summary) return 'summary';
    if (transcript) return 'source';
    return 'empty';
  }

  function renderAudioPreview(item) {
    const summary = String(item?.summaryText || '').trim();
    const transcript = String(item?.transcript || '').trim();
    const mode = audioPreviewMode(item);
    const mimeType = String(item?.mimeType || '').trim().toLowerCase();
    const canRetry = clovaSttAvailable && !mimeType.includes('webm');
    const helperMessage = transcript
      ? (openAiSummaryAvailable ? '원문은 저장되어 있습니다. 필요할 때만 요약을 생성합니다.' : '원문은 저장되어 있습니다.')
      : (!clovaSttAvailable
        ? '음성 전사 설정이 없어 텍스트 재시도를 사용할 수 없습니다.'
        : (canRetry
          ? '텍스트 재시도 후 요약을 생성할 수 있습니다.'
          : '이 녹음은 webm 포맷이라 텍스트 재시도를 지원하지 않습니다.'));

    return `
      <div class="counsel-preview-card" data-view="${escapeHtml(mode)}">
        <div class="counsel-preview-toolbar">
          ${summary ? `<button type="button" class="counsel-preview-toggle" data-view-target="summary">요약 보기</button>` : ''}
          ${transcript ? `<button type="button" class="counsel-preview-toggle" data-view-target="source">원문 보기</button>` : ''}
          ${transcript && !summary && openAiSummaryAvailable ? `<button type="button" class="counsel-preview-generate audio-record-summary" data-audio-id="${escapeHtml(item.id)}">요약 생성</button>` : ''}
        </div>
        ${summary ? `<div class="counsel-preview-body counsel-preview-summary">${escapeHtml(summary)}</div>` : ''}
        ${transcript ? `<div class="counsel-preview-body counsel-preview-source">${escapeHtml(transcript)}</div>` : ''}
        <div class="counsel-preview-empty">${escapeHtml(helperMessage)}</div>
      </div>
    `;
  }

  function updatePreviewMode(card, mode) {
    if (!card || !mode) return;
    card.setAttribute('data-view', mode);
  }

  async function generateCounselSummary() {
    if (!counselContentInput) return;
    if (!openAiSummaryAvailable) {
      setRecordingStatus('OpenAI API 키 설정 후 상담 요약을 사용할 수 있습니다.', true);
      return;
    }
    if (isRecording) {
      setRecordingStatus('녹음 중에는 요약을 생성할 수 없습니다.', true);
      return;
    }

    const source = stripSummaryBlock(counselContentInput.value || '');
    if (!source) {
      setRecordingStatus('요약할 상담 내용이 없습니다.', true);
      return;
    }

    if (generateSummaryBtn) {
      generateSummaryBtn.disabled = true;
      generateSummaryBtn.textContent = '요약 중...';
    }
    setRecordingStatus('GPT 요약 생성 중...');

    try {
      const response = await fetch(`${APP_CTX}/counsel/summary/generate`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          ...csrfHeaders()
        },
        body: JSON.stringify({ content: source, csIdx: csIdx || null })
      });

      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        setRecordingStatus(data?.message || '상담 요약 생성 실패', true);
        return;
      }

      const summary = String(data?.summary || '').trim();
      if (!summary) {
        setRecordingStatus('요약 결과가 비어 있습니다.', true);
        return;
      }

      upsertSummaryBlock(summary);
      const matched = applyDiseaseMatchFromText(counselContentInput.value || '');
      if (matched > 0) {
        setRecordingStatus(`상담 요약 반영 완료 (커스텀 ${matched}건 매칭)`);
      } else {
        setRecordingStatus('상담 요약 반영 완료');
      }
    } catch (e) {
      setRecordingStatus('상담 요약 생성 중 오류', true);
    } finally {
      if (generateSummaryBtn) {
        generateSummaryBtn.disabled = false;
        generateSummaryBtn.textContent = '상담 요약';
      }
    }
  }

  function appendTranscriptToCounselContent(text) {
    const clean = String(text || '').trim();
    if (!clean || !counselContentInput) return;
    const prev = String(counselContentInput.value || '').trim();
    counselContentInput.value = prev ? `${prev}\n${clean}` : clean;
  }

  function hasTranscriptInCounselContent(text) {
    const clean = String(text || '').trim();
    if (!clean || !counselContentInput) return false;
    const content = String(counselContentInput.value || '');
    return content.includes(clean);
  }

  function syncCounselContentFromAudioList(list) {
    if (!counselContentInput || !Array.isArray(list) || list.length === 0) return 0;

    // list API는 최신순이므로, 상담내용에는 오래된 전사부터 누적
    const ordered = [...list].reverse();
    let added = 0;
    ordered.forEach((item) => {
      const transcript = String(item?.transcript || '').trim();
      if (!transcript) return;
      if (hasTranscriptInCounselContent(transcript)) return;
      appendTranscriptToCounselContent(transcript);
      added += 1;
    });

    if (added > 0) {
      applyDiseaseMatchFromText(counselContentInput.value || '');
    }
    return added;
  }

  async function fetchAudioList() {
    if (!audioRecordListEl) return;
    const params = new URLSearchParams();
    if (csIdx && csIdx > 0) {
      params.set('csIdx', String(csIdx));
    } else {
      const tempKey = ensureAudioTempKey();
      if (tempKey) params.set('tempKey', tempKey);
    }
    if (![...params.keys()].length) {
      audioRecordListEl.innerHTML = '';
      return;
    }

    try {
      const response = await fetch(`${APP_CTX}/counsel/audio/list?${params.toString()}`, {
        method: 'GET',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      const data = await response.json();
      if (!response.ok || !data.success) {
        audioRecordListEl.innerHTML = '';
        return;
      }
      const list = Array.isArray(data.list) ? data.list : [];
      syncCounselContentFromAudioList(list);
      if (list.length === 0) {
        audioRecordListEl.innerHTML = '';
        return;
      }
      audioRecordListEl.innerHTML = list.map((item) => {
        const transcript = String(item.transcript || '').trim();
        const mimeType = String(item.mimeType || '').trim().toLowerCase();
        const canRetry = clovaSttAvailable && !mimeType.includes('webm');
        return `
          <div class="audio-record-item" data-audio-id="${escapeHtml(item.id)}">
            <div class="audio-record-meta">
              <div class="audio-record-name">${escapeHtml(item.originalFilename || `record-${item.id}`)}</div>
              <div class="audio-record-actions">
                ${!transcript && canRetry ? `<button type="button" class="audio-record-retry" data-audio-id="${escapeHtml(item.id)}">텍스트 재시도</button>` : ''}
                <button type="button" class="audio-record-delete" data-audio-id="${escapeHtml(item.id)}">삭제</button>
              </div>
            </div>
            <audio controls preload="none" src="${escapeHtml(item.streamUrl)}"></audio>
            ${renderAudioPreview(item)}
          </div>
        `;
      }).join('');
    } catch (e) {
      audioRecordListEl.innerHTML = '';
    }
  }

  async function deleteAudio(audioId) {
    if (!audioId) return;
    try {
      const response = await fetch(`${APP_CTX}/counsel/audio/delete/${encodeURIComponent(audioId)}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      if (!response.ok) {
        alert('녹음 파일 삭제 실패');
        return;
      }
      await fetchAudioList();
    } catch (e) {
      alert('녹음 파일 삭제 중 오류');
    }
  }

  async function retryTranscription(audioId, retryBtn) {
    if (!audioId) return;
    if (!clovaSttAvailable) {
      setRecordingStatus('클로바 STT 설정 후 텍스트 재시도를 사용할 수 있습니다.', true);
      return;
    }
    if (retryBtn) {
      retryBtn.disabled = true;
      retryBtn.textContent = '재시도 중...';
    }
    setRecordingStatus('텍스트 변환 재시도 중...');

    try {
      const response = await fetch(`${APP_CTX}/counsel/audio/retranscribe/${encodeURIComponent(audioId)}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        const message = data?.message || '텍스트 재변환 실패';
        setRecordingStatus(message, true);
        return;
      }

      const transcript = String(data?.item?.transcript || '').trim();
      if (!transcript) {
        setRecordingStatus('재변환 결과가 비어 있습니다.', true);
        return;
      }

      if (!hasTranscriptInCounselContent(transcript)) {
        appendTranscriptToCounselContent(transcript);
      }
      const matchedCount = applyDiseaseMatchFromText(counselContentInput ? counselContentInput.value : transcript);
      if (matchedCount > 0) {
        setRecordingStatus(`재변환 완료 (커스텀 ${matchedCount}건 매칭)`);
      } else {
        setRecordingStatus('텍스트 재변환 완료');
      }
      await fetchAudioList();
    } catch (e) {
      setRecordingStatus('텍스트 재변환 중 오류', true);
    } finally {
      if (retryBtn) {
        retryBtn.disabled = false;
        retryBtn.textContent = '텍스트 재시도';
      }
    }
  }

  async function generateAudioSummary(audioId, summaryBtn) {
    if (!audioId) return;
    if (!openAiSummaryAvailable) {
      setRecordingStatus('OpenAI API 키 설정 후 녹취 요약을 사용할 수 있습니다.', true);
      return;
    }
    if (summaryBtn) {
      summaryBtn.disabled = true;
      summaryBtn.textContent = '요약 중...';
    }
    setRecordingStatus('녹취 요약 생성 중...');

    try {
      const response = await fetch(`${APP_CTX}/counsel/audio/summary/${encodeURIComponent(audioId)}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        setRecordingStatus(data?.message || '녹취 요약 생성 실패', true);
        return;
      }
      setRecordingStatus(data?.cached ? '저장된 녹취 요약을 불러왔습니다.' : '녹취 요약 생성 완료');
      await fetchAudioList();
    } catch (e) {
      setRecordingStatus('녹취 요약 생성 중 오류', true);
    } finally {
      if (summaryBtn) {
        summaryBtn.disabled = false;
        summaryBtn.textContent = '요약 생성';
      }
    }
  }

  let mediaRecorder = null;
  let mediaStream = null;
  let chunks = [];
  let isRecording = false;
  let recordStartedAt = 0;
  let finalTranscript = '';
  let speechRecognition = null;
  let recordingTimerInterval = null;
  let recordPreviewObjectUrl = null;

  function formatRecordingDuration(seconds) {
    const safe = Math.max(0, Number(seconds) || 0);
    const mm = String(Math.floor(safe / 60)).padStart(2, '0');
    const ss = String(Math.floor(safe % 60)).padStart(2, '0');
    return `${mm}:${ss}`;
  }

  function updateRecordingTimer() {
    if (!recordingTimerEl) return;
    const elapsed = Math.floor((Date.now() - recordStartedAt) / 1000);
    recordingTimerEl.textContent = formatRecordingDuration(elapsed);
  }

  function setRecordingIndicatorActive(active) {
    if (floatingToolsEl) {
      floatingToolsEl.classList.toggle('is-recording', !!active);
    }
    if (!recordingIndicatorEl) {
      return;
    }

    if (active) {
      if (recordingTimerEl) {
        recordingTimerEl.textContent = '00:00';
      }
      updateRecordingTimer();
      if (recordingTimerInterval) {
        clearInterval(recordingTimerInterval);
      }
      recordingTimerInterval = setInterval(updateRecordingTimer, 1000);
      return;
    }

    if (recordingTimerInterval) {
      clearInterval(recordingTimerInterval);
      recordingTimerInterval = null;
    }
    if (recordingTimerEl) {
      recordingTimerEl.textContent = '00:00';
    }
  }

  function stopSpeechRecognition() {
    if (!speechRecognition) return;
    try {
      speechRecognition.onend = null;
      speechRecognition.stop();
    } catch (_) {}
    speechRecognition = null;
  }

  function startSpeechRecognition() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) return;

    speechRecognition = new SR();
    speechRecognition.lang = 'ko-KR';
    speechRecognition.continuous = true;
    speechRecognition.interimResults = true;
    speechRecognition.onresult = function (event) {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        const text = String(result[0]?.transcript || '').trim();
        if (!text) continue;
        if (result.isFinal) {
          finalTranscript = finalTranscript ? `${finalTranscript} ${text}` : text;
        }
      }
    };
    speechRecognition.onerror = function (event) {
      setRecordingStatus(`음성 인식 오류: ${event.error || 'unknown'}`, true);
    };
    speechRecognition.onend = function () {
      if (isRecording && speechRecognition) {
        try {
          speechRecognition.start();
        } catch (_) {}
      }
    };
    try {
      speechRecognition.start();
    } catch (_) {}
  }

  function extensionFromMimeType(mimeType) {
    const mime = String(mimeType || '').toLowerCase();
    if (mime.includes('mpeg') || mime.includes('mp3')) return 'mp3';
    if (mime.includes('m4a') || mime.includes('mp4')) return 'm4a';
    if (mime.includes('ogg')) return 'ogg';
    if (mime.includes('wav')) return 'wav';
    if (mime.includes('aac')) return 'aac';
    if (mime.includes('ac3')) return 'ac3';
    if (mime.includes('flac')) return 'flac';
    if (mime.includes('webm')) return 'webm';
    return 'webm';
  }

  function isAudioUploadCandidate(file) {
    if (!file) return false;
    const mime = String(file.type || '').toLowerCase();
    if (mime.startsWith('audio/')) return true;

    const name = String(file.name || '').toLowerCase();
    return /\.(mp3|m4a|wav|ogg|aac|flac|ac3|webm)$/i.test(name);
  }

  async function handleAudioUploadSuccess(data, fallbackSuccessMessage) {
    const serverTranscript = String(data?.item?.transcript || '').trim();
    const transcriptSource = String(data?.item?.transcriptSource || '').trim();
    const alreadyHadTranscript = serverTranscript ? hasTranscriptInCounselContent(serverTranscript) : false;
    let statusSet = false;

    if (serverTranscript && !alreadyHadTranscript) {
      appendTranscriptToCounselContent(serverTranscript);
      const sourceForMatch = counselContentInput ? counselContentInput.value : serverTranscript;
      const matchedFromServer = applyDiseaseMatchFromText(sourceForMatch);
      if (transcriptSource === 'browser') {
        setRecordingStatus('클로바 인식 실패로 브라우저 임시 인식으로 저장됨', true);
        statusSet = true;
      } else if (matchedFromServer > 0) {
        setRecordingStatus(`전사 반영 완료 (커스텀 ${matchedFromServer}건 매칭)`);
        statusSet = true;
      } else {
        setRecordingStatus('전사 반영 완료');
        statusSet = true;
      }
    } else if (transcriptSource === 'browser') {
      setRecordingStatus('클로바 인식 실패로 브라우저 임시 인식으로 저장됨', true);
      statusSet = true;
    }

    if (!statusSet) {
      if (serverTranscript && alreadyHadTranscript) {
        setRecordingStatus(fallbackSuccessMessage || '업로드 완료');
      } else if (!serverTranscript) {
        setRecordingStatus(`${fallbackSuccessMessage || '업로드 완료'} (전사 없음)`);
      }
    }
    await fetchAudioList();
  }

  async function uploadAudioBlob(blob, durationSeconds, transcript) {
    const ext = extensionFromMimeType(blob?.type);
    const filename = `record_${Date.now()}.${ext}`;
    const formData = new FormData();
    formData.append('audio', blob, filename);
    if (csIdx && csIdx > 0) {
      formData.append('csIdx', String(csIdx));
    } else {
      formData.append('tempKey', ensureAudioTempKey());
    }
    if (durationSeconds && durationSeconds > 0) {
      formData.append('durationSeconds', String(durationSeconds));
    }
    if (transcript) {
      formData.append('transcript', transcript);
    }

    setRecordingStatus('녹음 파일 업로드 중...');
    try {
      const response = await fetch(`${APP_CTX}/counsel/audio/upload`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders(),
        body: formData
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        const message = data?.message || '녹음 파일 저장 실패';
        setRecordingStatus(message, true);
        return;
      }
      await handleAudioUploadSuccess(data, '녹음 저장 완료');
    } catch (e) {
      setRecordingStatus('녹음 파일 저장 중 오류', true);
    }
  }

  async function uploadAudioFile(file) {
    if (!file) return;
    if (isRecording) {
      setRecordingStatus('녹음 중에는 파일 업로드를 할 수 없습니다.', true);
      return;
    }
    if (!isAudioUploadCandidate(file)) {
      setRecordingStatus('오디오 파일만 업로드할 수 있습니다.', true);
      return;
    }

    if (uploadAudioFileBtn) {
      uploadAudioFileBtn.disabled = true;
      uploadAudioFileBtn.textContent = '업로드 중...';
    }
    setRecordingStatus('음성 파일 업로드 중...');

    try {
      const formData = new FormData();
      formData.append('audio', file, file.name || `upload_${Date.now()}.${extensionFromMimeType(file.type)}`);
      if (csIdx && csIdx > 0) {
        formData.append('csIdx', String(csIdx));
      } else {
        formData.append('tempKey', ensureAudioTempKey());
      }

      const response = await fetch(`${APP_CTX}/counsel/audio/upload`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders(),
        body: formData
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        setRecordingStatus(data?.message || '파일 업로드 실패', true);
        return;
      }

      await handleAudioUploadSuccess(data, '파일 업로드 완료');
    } catch (e) {
      setRecordingStatus('파일 업로드 중 오류', true);
    } finally {
      if (uploadAudioFileBtn) {
        uploadAudioFileBtn.disabled = false;
        uploadAudioFileBtn.textContent = '파일 업로드';
      }
    }
  }

  async function startRecording() {
    if (isRecording) return;
    if (!window.isSecureContext) {
      setRecordingStatus('모바일 녹음은 HTTPS 환경에서만 사용할 수 있습니다.', true);
      return;
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      alert('이 브라우저는 녹음을 지원하지 않습니다.');
      return;
    }
    if (typeof MediaRecorder === 'undefined') {
      alert('이 브라우저는 MediaRecorder를 지원하지 않습니다.');
      return;
    }

    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const options = {};
      const preferredCandidates = [
        'audio/mp4',
        'audio/ogg;codecs=opus',
        'audio/ogg',
        'audio/wav'
      ];
      const fallbackCandidates = [
        'audio/webm;codecs=opus',
        'audio/webm'
      ];
      let matchedType = preferredCandidates.find((type) => {
        try {
          return MediaRecorder.isTypeSupported(type);
        } catch (_) {
          return false;
        }
      });
      if (!matchedType) {
        matchedType = fallbackCandidates.find((type) => {
          try {
            return MediaRecorder.isTypeSupported(type);
          } catch (_) {
            return false;
          }
        });
      }
      if (matchedType) {
        options.mimeType = matchedType;
      }

      mediaRecorder = createMediaRecorder(mediaStream, options);
      chunks = [];
      finalTranscript = '';
      recordStartedAt = Date.now();
      isRecording = true;
      setRecordingIndicatorActive(true);

      mediaRecorder.addEventListener('dataavailable', function (event) {
        if (event.data && event.data.size > 0) {
          chunks.push(event.data);
        }
      });

      mediaRecorder.addEventListener('stop', async function () {
        isRecording = false;
        setRecordingIndicatorActive(false);
        stopSpeechRecognition();

        const blob = new Blob(chunks, { type: mediaRecorder.mimeType || 'audio/webm' });
        if (!blob || blob.size <= 0) {
          setRecordingStatus('녹음 데이터가 비어 있습니다. 다시 시도해 주세요.', true);
          cleanupRecorderState();
          return;
        }
        const durationSeconds = Math.max(0, (Date.now() - recordStartedAt) / 1000);
        const transcript = String(finalTranscript || '').trim();

        if (recordPreviewEl) {
          if (recordPreviewObjectUrl) {
            URL.revokeObjectURL(recordPreviewObjectUrl);
            recordPreviewObjectUrl = null;
          }
          recordPreviewObjectUrl = URL.createObjectURL(blob);
          recordPreviewEl.src = recordPreviewObjectUrl;
          recordPreviewEl.style.display = 'block';
        }

        setRecordingStatus('전사 처리 중...');
        await uploadAudioBlob(blob, durationSeconds, transcript);

        cleanupRecorderState();
      });

      mediaRecorder.addEventListener('error', function (event) {
        const message = event?.error?.message || event?.error?.name || 'unknown';
        setRecordingStatus(`녹음 오류가 발생했습니다: ${message}`, true);
        cleanupRecorderState();
      });

      startMediaRecorder(mediaRecorder);
      startSpeechRecognition();
      startRecordingBtn.disabled = true;
      stopRecordingBtn.disabled = false;
      const recordingMimeType = String(mediaRecorder.mimeType || matchedType || '').trim().toLowerCase();
      if (recordingMimeType.includes('webm')) {
        setRecordingStatus('이 브라우저는 webm 녹음만 지원해 텍스트 재시도는 제한됩니다.', true);
      } else {
        setRecordingStatus('녹음 중...');
      }
    } catch (e) {
      const detail = e?.message ? ` (${e.message})` : '';
      setRecordingStatus(`마이크 권한이 필요합니다.${detail}`, true);
      cleanupRecorderState();
    }
  }

  function stopRecording() {
    if (!isRecording || !mediaRecorder) return;
    try {
      if (typeof mediaRecorder.requestData === 'function') {
        try {
          mediaRecorder.requestData();
        } catch (_) {}
      }
      mediaRecorder.stop();
    } catch (_) {
      setRecordingStatus('녹음 중지 실패', true);
      cleanupRecorderState();
    }
  }

  function createMediaRecorder(stream, options) {
    try {
      if (options && options.mimeType) {
        return new MediaRecorder(stream, options);
      }
      return new MediaRecorder(stream);
    } catch (_) {
      return new MediaRecorder(stream);
    }
  }

  function startMediaRecorder(recorder) {
    if (!recorder) return;
    try {
      recorder.start(1000);
    } catch (_) {
      recorder.start();
    }
  }

  function cleanupRecorderState() {
    if (mediaStream) {
      mediaStream.getTracks().forEach((track) => track.stop());
    }
    mediaStream = null;
    mediaRecorder = null;
    isRecording = false;
    setRecordingIndicatorActive(false);
    if (startRecordingBtn) startRecordingBtn.disabled = false;
    if (stopRecordingBtn) stopRecordingBtn.disabled = true;
  }

  if (goListBtn) {
    goListBtn.addEventListener('click', function () {
      showSpinner();
      window.location.href = `${APP_CTX}/counsel/list?page=1&perPageNum=30`;
    });
  }

  if (printPreviewBtn) {
    printPreviewBtn.addEventListener('click', function () {
      openMobilePrintWindow('journal');
    });
  }

  if (admissionPledgePrintBtn) {
    admissionPledgePrintBtn.addEventListener('click', function () {
      openMobilePrintWindow('admission');
    });
  }

  if (addGuardianBtn && guardianContainer) {
    addGuardianBtn.addEventListener('click', function () {
      guardianContainer.insertAdjacentHTML('beforeend', guardianTemplate());
      bindAllPhoneMasks();
    });
  }

  if (guardianContainer) {
    guardianContainer.addEventListener('click', function (e) {
      const removeBtn = e.target.closest('.remove-guardian');
      if (!removeBtn) return;

      const rows = guardianContainer.querySelectorAll('.guardian-entry');
      if (rows.length <= 1) {
        const row = rows[0];
        if (!row) return;
        row.querySelectorAll('input').forEach((input) => {
          input.value = '';
        });
        return;
      }

      const row = removeBtn.closest('.guardian-entry');
      if (row) row.remove();
    });
  }

  if (birthInput) {
    birthInput.addEventListener('input', function () {
      this.value = formatDateInput(this.value);
      if (ageInput) {
        ageInput.value = calculateAge(this.value);
      }
    });
    birthInput.value = formatDateInput(birthInput.value);
    if (ageInput && (!ageInput.value || ageInput.value.trim() === '')) {
      ageInput.value = calculateAge(birthInput.value);
    }
  }

  if (counselDateInput) {
    counselDateInput.addEventListener('input', function () {
      this.value = formatDateInput(this.value);
    });
    counselDateInput.value = formatDateInput(counselDateInput.value);
    if (!counselDateInput.value) {
      counselDateInput.value = todayYmd();
    }
  }

  if (resultSelect) {
    toggleAdmissionPledgeLauncher(resultSelect.value);
    resultSelect.addEventListener('change', function () {
      toggleAdmissionPledgeLauncher(this.value);
      if (requiresAdmissionPledge(this.value) && !isAdmissionPledgeComplete()) {
        openAdmissionPledgeBtn?.click();
      }
    });
  }
  setAdmissionPledgeStatus();

  if (admissionPledgeTextInput && !String(admissionPledgeTextInput.value || '').trim()) {
    admissionPledgeTextInput.value = defaultAdmissionPledgeText;
  }
  if (generateSummaryBtn && !openAiSummaryAvailable) {
    generateSummaryBtn.disabled = true;
    generateSummaryBtn.title = 'OpenAI API 키 설정 후 사용 가능합니다.';
  }

  const pendingPledge = sessionStorage.getItem('csm-admission-pledge-return');
  if (pendingPledge) {
    try {
      applyAdmissionPledgePayload(JSON.parse(pendingPledge));
    } catch (_) {
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
      if (roomInput) roomInput.value = roomName;
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
        cs_idx: csIdx != null && csIdx > 0 ? csIdx : 0,
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
      if (csIdx != null && csIdx > 0) {
        params.set('csIdx', String(csIdx));
      }
      params.set('draftKey', draftKey);
      params.set('patientName', String(document.getElementById('cs_col_01')?.value || '').trim());
      params.set('gender', String(document.getElementById('cs_col_02')?.value || '').trim());
      params.set('birth', String(document.getElementById('cs_col_03')?.value || '').trim());
      params.set('chartNo', String(document.getElementById('cs_col_40')?.value || '').trim());
      params.set('room', String(document.getElementById('cs_col_38')?.value || '').trim());
      params.set('phone', String(document.querySelector('input[name="cs_col_15[]"]')?.value || '').trim());
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

  bindAllPhoneMasks();
  bindFieldAutoCheck();

  if (startRecordingBtn) {
    startRecordingBtn.addEventListener('click', startRecording);
  }
  if (stopRecordingBtn) {
    stopRecordingBtn.addEventListener('click', stopRecording);
  }
  if (uploadAudioFileBtn && audioFileInput) {
    uploadAudioFileBtn.addEventListener('click', function () {
      if (isRecording) {
        setRecordingStatus('녹음 중에는 파일 업로드를 할 수 없습니다.', true);
        return;
      }
      audioFileInput.click();
    });
    audioFileInput.addEventListener('change', function () {
      const file = this.files && this.files[0] ? this.files[0] : null;
      if (!file) return;
      uploadAudioFile(file).finally(() => {
        this.value = '';
      });
    });
  }
  if (applyDiseaseMatchBtn) {
    applyDiseaseMatchBtn.addEventListener('click', function () {
      const source = counselContentInput ? counselContentInput.value : '';
      const matched = applyDiseaseMatchFromText(source);
      if (matched > 0) {
        setRecordingStatus(`상담내용으로 커스텀 ${matched}건 매칭 완료`);
      } else {
        setRecordingStatus('매칭된 커스텀 항목이 없습니다.');
      }
    });
  }
  if (generateSummaryBtn && openAiSummaryAvailable) {
    generateSummaryBtn.addEventListener('click', generateCounselSummary);
  }

  if (audioRecordListEl) {
    audioRecordListEl.addEventListener('click', function (e) {
      const previewToggle = e.target.closest('.counsel-preview-toggle');
      if (previewToggle) {
        const card = previewToggle.closest('.counsel-preview-card');
        const targetView = String(previewToggle.getAttribute('data-view-target') || '').trim();
        if (!card || !targetView) return;
        updatePreviewMode(card, targetView);
        return;
      }

      const summaryBtn = e.target.closest('.audio-record-summary');
      if (summaryBtn) {
        const audioId = summaryBtn.getAttribute('data-audio-id');
        if (!audioId) return;
        generateAudioSummary(audioId, summaryBtn);
        return;
      }

      const retryBtn = e.target.closest('.audio-record-retry');
      if (retryBtn) {
        const audioId = retryBtn.getAttribute('data-audio-id');
        if (!audioId) return;
        retryTranscription(audioId, retryBtn);
        return;
      }
      const btn = e.target.closest('.audio-record-delete');
      if (!btn) return;
      const audioId = btn.getAttribute('data-audio-id');
      if (!audioId) return;
      if (!window.confirm('해당 녹음 파일을 삭제하시겠습니까?')) return;
      deleteAudio(audioId);
    });
  }

  if (form) {
    form.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter') return;
      const target = e.target;
      if (!target) return;
      const tag = (target.tagName || '').toUpperCase();
      if (tag === 'TEXTAREA') return;
      e.preventDefault();
    });

    form.addEventListener('submit', function (e) {
      if (isRecording) {
        e.preventDefault();
        alert('녹음이 진행 중입니다. 녹음을 중지한 뒤 저장해 주세요.');
        return;
      }
      if ((admissionPledgeRequiredInput?.value || 'N') === 'Y') {
        if (!isAdmissionPledgeComplete()) {
          e.preventDefault();
          alert('입원서약서 페이지에서 서명 완료 후 저장해 주세요.');
          openAdmissionPledgeBtn?.focus();
          return;
        }
        if (!String(admissionSignerNameInput?.value || '').trim()) {
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
      }
      showSpinner();
    });
  }

  ensureAudioTempKey();
  setRecordingIndicatorActive(false);
  fetchAudioList();
});
