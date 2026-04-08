document.addEventListener('DOMContentLoaded', function () {
  const form = document.getElementById('mobileCounselForm');
  const spinner = document.getElementById('spinner-overlay');
  const goListBtn = document.getElementById('goListBtn');
  const addGuardianBtn = document.getElementById('addGuardianBtn');
  const guardianContainer = document.getElementById('guardianContainer');
  const birthInput = document.getElementById('cs_col_03');
  const ageInput = document.getElementById('cs_col_04');
  const counselDateInput = document.getElementById('cs_col_16');
  const counselContentInput = document.getElementById('cs_col_32');

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

  function buildMobilePrintHtmlFromForm() {
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

  function openMobilePrintWindow() {
    const bodyHtml = buildMobilePrintHtmlFromForm();
    const popup = window.open('', '_blank');
    if (!popup) return;

    const printStyles = `
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
        width: 210mm;
        margin: 12px auto;
        padding: 12mm;
        background: #fff;
        box-shadow: 0 4px 18px rgba(15, 23, 42, 0.08);
      }
      .journal-doc { font-size: 12px; line-height: 1.5; }
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
          margin: 0;
          padding: 0;
          box-shadow: none;
        }
      }
    `;

    const html = `
      <html>
        <head>
          <title>상담일지출력</title>
          <meta charset="UTF-8">
          <style>${printStyles}</style>
        </head>
        <body>
          <div class="print-page">${bodyHtml}</div>
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

  async function generateCounselSummary() {
    if (!counselContentInput) return;
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
        return `
          <div class="audio-record-item" data-audio-id="${escapeHtml(item.id)}">
            <div class="audio-record-meta">
              <div class="audio-record-name">${escapeHtml(item.originalFilename || `record-${item.id}`)}</div>
              <div class="audio-record-actions">
                ${!transcript ? `<button type="button" class="audio-record-retry" data-audio-id="${escapeHtml(item.id)}">텍스트 재시도</button>` : ''}
                <button type="button" class="audio-record-delete" data-audio-id="${escapeHtml(item.id)}">삭제</button>
              </div>
            </div>
            <audio controls preload="none" src="${escapeHtml(item.streamUrl)}"></audio>
            ${transcript ? `<div class="audio-record-transcript">${escapeHtml(transcript)}</div>` : ''}
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

  let mediaRecorder = null;
  let mediaStream = null;
  let chunks = [];
  let isRecording = false;
  let recordStartedAt = 0;
  let finalTranscript = '';
  let speechRecognition = null;
  let recordingTimerInterval = null;

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
      const data = await response.json();
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
      const data = await response.json();
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
      const candidates = [
        'audio/mp4',
        'audio/ogg;codecs=opus',
        'audio/ogg',
        'audio/wav',
        'audio/webm;codecs=opus',
        'audio/webm'
      ];
      const matchedType = candidates.find((type) => {
        try {
          return MediaRecorder.isTypeSupported(type);
        } catch (_) {
          return false;
        }
      });
      if (matchedType) {
        options.mimeType = matchedType;
      }

      mediaRecorder = new MediaRecorder(mediaStream, options);
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
        const durationSeconds = Math.max(0, (Date.now() - recordStartedAt) / 1000);
        const transcript = String(finalTranscript || '').trim();

        if (recordPreviewEl) {
          recordPreviewEl.src = URL.createObjectURL(blob);
          recordPreviewEl.style.display = 'block';
        }

        setRecordingStatus('전사 처리 중...');
        await uploadAudioBlob(blob, durationSeconds, transcript);

        if (mediaStream) {
          mediaStream.getTracks().forEach((track) => track.stop());
        }
        mediaStream = null;
        mediaRecorder = null;
        startRecordingBtn.disabled = false;
        stopRecordingBtn.disabled = true;
      });

      mediaRecorder.start(1000);
      startSpeechRecognition();
      startRecordingBtn.disabled = true;
      stopRecordingBtn.disabled = false;
      setRecordingStatus('녹음 중...');
    } catch (e) {
      setRecordingStatus('마이크 권한이 필요합니다.', true);
      if (mediaStream) {
        mediaStream.getTracks().forEach((track) => track.stop());
      }
      mediaStream = null;
      mediaRecorder = null;
      isRecording = false;
      setRecordingIndicatorActive(false);
      startRecordingBtn.disabled = false;
      stopRecordingBtn.disabled = true;
    }
  }

  function stopRecording() {
    if (!isRecording || !mediaRecorder) return;
    try {
      mediaRecorder.stop();
    } catch (_) {
      isRecording = false;
      setRecordingIndicatorActive(false);
      setRecordingStatus('녹음 중지 실패', true);
    }
  }

  if (goListBtn) {
    goListBtn.addEventListener('click', function () {
      showSpinner();
      window.location.href = `${APP_CTX}/counsel/list?page=1&perPageNum=30`;
    });
  }

  if (printPreviewBtn) {
    printPreviewBtn.addEventListener('click', openMobilePrintWindow);
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
  if (generateSummaryBtn) {
    generateSummaryBtn.addEventListener('click', generateCounselSummary);
  }

  if (audioRecordListEl) {
    audioRecordListEl.addEventListener('click', function (e) {
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
      showSpinner();
    });
  }

  ensureAudioTempKey();
  setRecordingIndicatorActive(false);
  fetchAudioList();
});
