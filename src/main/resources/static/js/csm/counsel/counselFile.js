document.addEventListener('DOMContentLoaded', function () {
  const uploadBtn = document.getElementById('uploadCounselFileBtn');
  const fileInput = document.getElementById('counselFileInput');
  const fileDropZoneEl = document.getElementById('counselFileDropZone');
  const fileListEl = document.getElementById('counselFileList');
  const fileStatusEl = document.getElementById('counselFileStatus');
  const fileTempKeyInput = document.getElementById('file_temp_key');
  const counselContentInput = document.getElementById('cs_col_32');
  const openAiSummaryAvailable = (document.getElementById('openai_summary_available')?.value || 'N') === 'Y';
  const csIdxRaw = document.querySelector('input[name="cs_idx"]')?.value || '';
  const csIdx = /^\d+$/.test(csIdxRaw) ? Number(csIdxRaw) : null;

  if (!fileInput || !fileListEl || !fileTempKeyInput) {
    return;
  }

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

  function csrfHeaders() {
    if (!csrfToken || !csrfHeader) return {};
    return { [csrfHeader]: csrfToken };
  }

  function setFileStatus(message, isError) {
    if (!fileStatusEl) return;
    fileStatusEl.textContent = message || '';
    fileStatusEl.style.color = isError ? '#cc2d2d' : '#5a5a5a';
  }

  function ensureFileTempKey() {
    let key = String(fileTempKeyInput.value || '').trim();
    if (!key) {
      if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        key = window.crypto.randomUUID();
      } else {
        key = `tmp_${Date.now()}_${Math.random().toString(16).slice(2, 12)}`;
      }
      fileTempKeyInput.value = key;
    }
    return key;
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

  function formatFileSize(size) {
    const bytes = Number(size || 0);
    if (!Number.isFinite(bytes) || bytes <= 0) return '-';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  function isCounselFileCandidate(file) {
    if (!file) return false;
    const name = String(file.name || '').trim();
    return name.length > 0;
  }

  function previewMode(item) {
    const summary = String(item?.summaryText || '').trim();
    const extracted = String(item?.extractedText || '').trim();
    if (summary) return 'summary';
    if (extracted) return 'source';
    return 'empty';
  }

  function renderPreviewToggle(mode, hasSummary, hasSource) {
    if (!hasSummary || !hasSource) return '';
    const nextView = mode === 'summary' ? 'source' : 'summary';
    const label = nextView === 'summary' ? '요약 보기' : '원문 보기';
    return `<button type="button" class="counsel-preview-toggle" data-view-target="${nextView}">${label}</button>`;
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

  function readLabelAround(element) {
    if (!element) return '';

    const normalize = (v) => String(v || '').replace(/\s+/g, ' ').trim();

    const fromFor = (() => {
      if (!element.id) return '';
      const byFor = document.querySelector(`label[for='${element.id}']`);
      return normalize(byFor?.textContent || '');
    })();
    if (fromFor) return fromFor;

    if (element.type === 'checkbox' || element.type === 'radio') {
      const siblingLabel = element.closest('.input-wrapper')?.querySelector('label');
      const siblingText = normalize(siblingLabel?.textContent || '');
      if (siblingText) return siblingText;
    }

    const wrapper = element.closest('.input-wrapper');
    if (!wrapper) return '';

    const labels = Array.from(wrapper.querySelectorAll('label, span'));
    for (const node of labels) {
      const text = normalize(node.textContent || '');
      if (text && text !== '선택하세요') {
        return text;
      }
    }
    return '';
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
      const label = String(el.dataset.label || '').trim() || readLabelAround(el);
      if (label && (!target.label || label.length > target.label.length)) {
        target.label = label;
      }

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

  function analyzeCustomMatchFromText(sourceText) {
    const normalizedSource = normalizeMatchText(sourceText);
    if (!normalizedSource) return [];

    const diseaseTokens = extractDiseaseTokens(sourceText);
    const targets = collectCustomTargets();
    const result = [];

    targets.forEach((target) => {
      const candidates = candidateWordsOfTarget(target);
      const matchedKeyword = candidates.length > 0
        ? findLongestMatchedKeyword(normalizedSource, candidates)
        : '';
      const bestOption = findBestOptionMatch(target.options, normalizedSource, diseaseTokens);
      const diseaseFallback = isDiseaseTarget(target) && diseaseTokens.length > 0 ? diseaseTokens[0] : '';
      const selectValue = (target.select && bestOption) ? bestOption : '';
      const textValue = bestOption || matchedKeyword || diseaseFallback;
      const check = !!(target.checkbox || target.radio);
      const previewValue = selectValue || textValue || (check ? '체크' : '');
      if (!previewValue) return;

      const label = String(target.label || target.base || '커스텀항목').trim();
      const type = selectValue
        ? '선택'
        : (target.text ? '입력' : (check ? '체크' : '매칭'));

      result.push({
        id: String(target.base || ''),
        base: String(target.base || ''),
        label,
        type,
        previewValue,
        selectValue,
        textValue,
        check
      });
    });

    return result;
  }

  function renderMatchPreview(sourceText, fileId) {
    const clean = String(sourceText || '').trim();
    if (!clean) {
      return `<div class="counsel-match-preview counsel-match-preview-empty">텍스트 추출 후 커스텀 매칭 후보를 확인할 수 있습니다.</div>`;
    }
    const matches = analyzeCustomMatchFromText(clean);
    if (matches.length === 0) {
      return `<div class="counsel-match-preview"><div class="counsel-match-preview-empty">추출 완료: 매칭된 커스텀 항목이 없습니다.</div></div>`;
    }
    return `
      <div class="counsel-match-preview">
        <div class="counsel-match-actions">
          <button type="button" class="counsel-match-open-modal" data-file-id="${escapeHtml(fileId)}">
            매칭 후보 보기 (${matches.length}건)
          </button>
        </div>
      </div>
    `;
  }

  /* -------- 매칭 모달 -------- */
  let _modalFileId = null;
  let _modalExtracted = null;

  function openMatchModal(fileId, extractedText) {
    const matches = analyzeCustomMatchFromText(String(extractedText || '').trim());
    _modalFileId = fileId;
    _modalExtracted = extractedText;

    const list = document.getElementById('matchModalList');
    const countEl = document.getElementById('matchModalCount');
    const selectAll = document.getElementById('matchModalSelectAll');
    const modal = document.getElementById('matchCandidateModal');
    if (!list || !modal) return;

    list.innerHTML = matches.map((match) => `
      <li class="match-modal-item">
        <label class="match-modal-row">
          <input type="checkbox" class="match-modal-check" checked
            data-match-base="${escapeHtml(match.base)}"
            data-match-label="${escapeHtml(match.label)}"
            data-match-type="${escapeHtml(match.type)}"
            data-match-select="${escapeHtml(match.selectValue || '')}"
            data-match-text="${escapeHtml(match.textValue || '')}"
            data-match-check="${match.check ? 'Y' : 'N'}">
          <span class="match-modal-label">${escapeHtml(match.label)}</span>
          <span class="match-modal-arrow">→</span>
          <span class="match-modal-value">${escapeHtml(match.previewValue)}</span>
          <span class="match-modal-type">${escapeHtml(match.type)}</span>
        </label>
      </li>
    `).join('');

    if (countEl) countEl.textContent = `총 ${matches.length}건`;
    if (selectAll) selectAll.checked = true;
    modal.style.display = 'flex';
  }

  function closeMatchModal() {
    const modal = document.getElementById('matchCandidateModal');
    if (modal) modal.style.display = 'none';
    _modalFileId = null;
    _modalExtracted = null;
  }

  // 모달 이벤트 바인딩
  const _matchModal = document.getElementById('matchCandidateModal');
  const _matchModalClose = document.getElementById('matchModalClose');
  const _matchModalCancel = document.getElementById('matchModalCancel');
  const _matchModalApply = document.getElementById('matchModalApply');
  const _matchModalSelectAll = document.getElementById('matchModalSelectAll');

  if (_matchModalClose) _matchModalClose.addEventListener('click', closeMatchModal);
  if (_matchModalCancel) _matchModalCancel.addEventListener('click', closeMatchModal);
  if (_matchModal) _matchModal.addEventListener('click', (e) => { if (e.target === _matchModal) closeMatchModal(); });

  if (_matchModalSelectAll) {
    _matchModalSelectAll.addEventListener('change', function () {
      document.querySelectorAll('.match-modal-check').forEach(chk => { chk.checked = this.checked; });
    });
  }

  if (_matchModalApply) {
    _matchModalApply.addEventListener('click', function () {
      if (!_modalFileId || !_modalExtracted) return;
      const selectedMatches = Array.from(document.querySelectorAll('.match-modal-check:checked')).map((node) => ({
        base: String(node.getAttribute('data-match-base') || '').trim(),
        label: String(node.getAttribute('data-match-label') || '').trim(),
        type: String(node.getAttribute('data-match-type') || '').trim(),
        selectValue: String(node.getAttribute('data-match-select') || '').trim(),
        textValue: String(node.getAttribute('data-match-text') || '').trim(),
        check: String(node.getAttribute('data-match-check') || '').trim().toUpperCase() === 'Y'
      })).filter((m) => m.base);

      const fileItem = document.querySelector(`.counsel-file-item [data-file-id="${_modalFileId}"]`)?.closest('.counsel-file-item');
      const filename = String(fileItem?.querySelector('.counsel-file-link')?.textContent || '').trim();
      const applied = applyExtractedTextToCounsel(_modalExtracted, filename, selectedMatches);
      if (applied.matchedCount > 0) {
        setFileStatus(`선택 매칭 적용 완료 (커스텀 ${applied.matchedCount}건)`);
      } else {
        setFileStatus('적용할 커스텀 매칭 후보가 없습니다.');
      }
      closeMatchModal();
    });
  }

  function renderPreviewBody(item) {
    const summary = String(item?.summaryText || '').trim();
    const extracted = String(item?.extractedText || '').trim();
    const mode = previewMode(item);
    const helperMessage = extracted
      ? (openAiSummaryAvailable ? '원문은 저장되어 있습니다. 요약 생성 또는 적용(커스텀 반영)을 선택해 주세요.' : '원문은 저장되어 있습니다. 적용 버튼으로 커스텀 항목을 반영할 수 있습니다.')
      : '문서 텍스트 추출 버튼을 눌러 원문과 매칭 후보를 먼저 확인해 주세요.';

    return `
      <div class="counsel-preview-card" data-view="${escapeHtml(mode)}">
        <div class="counsel-preview-toolbar">
          ${renderPreviewToggle(mode, !!summary, !!extracted)}
          ${!summary && openAiSummaryAvailable ? `<button type="button" class="counsel-preview-generate counsel-file-summary" data-file-id="${escapeHtml(item.id)}">요약 생성</button>` : ''}
        </div>
        ${summary ? `<div class="counsel-preview-body counsel-preview-summary">${escapeHtml(summary)}</div>` : ''}
        ${extracted ? `<div class="counsel-preview-body counsel-preview-source">${escapeHtml(extracted)}</div>` : ''}
        <div class="counsel-preview-empty">${escapeHtml(helperMessage)}</div>
        ${renderMatchPreview(extracted, item.id)}
      </div>
    `;
  }

  function applyCustomMatchFromText(sourceText) {
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
      if (touched) matchedCount += 1;
    });
    return matchedCount;
  }

  function renderFileList(list) {
    if (!Array.isArray(list) || list.length === 0) {
      fileListEl.innerHTML = '';
      return;
    }

    fileListEl.innerHTML = list.map((item) => {
      return `
        <div class="counsel-file-item" data-file-id="${escapeHtml(item.id)}">
          <div class="counsel-file-head">
            <a class="counsel-file-link" href="${escapeHtml(item.downloadUrl)}" target="_blank" rel="noopener">
              ${escapeHtml(item.originalFilename || `file-${item.id}`)}
            </a>
            <span class="counsel-file-size">${escapeHtml(formatFileSize(item.fileSize))}</span>
          </div>
          <div class="counsel-file-buttons">
            <button type="button" class="counsel-file-extract" data-file-id="${escapeHtml(item.id)}">텍스트추출</button>
            <button type="button" class="counsel-file-delete" data-file-id="${escapeHtml(item.id)}">삭제</button>
          </div>
          ${renderPreviewBody(item)}
        </div>
      `;
    }).join('');
  }

  function updatePreviewMode(card, mode) {
    if (!card || !mode) return;
    card.setAttribute('data-view', mode);
    const toggleBtn = card.querySelector('.counsel-preview-toggle');
    if (!toggleBtn) return;
    const nextView = mode === 'summary' ? 'source' : 'summary';
    toggleBtn.setAttribute('data-view-target', nextView);
    toggleBtn.textContent = nextView === 'summary' ? '요약 보기' : '원문 보기';
  }

  async function fetchCounselFileList() {
    const params = new URLSearchParams();
    if (csIdx && csIdx > 0) {
      params.set('csIdx', String(csIdx));
    } else {
      const tempKey = ensureFileTempKey();
      if (tempKey) params.set('tempKey', tempKey);
    }
    if (![...params.keys()].length) {
      renderFileList([]);
      return;
    }

    try {
      const response = await fetch(`${APP_CTX}/counsel/file/list?${params.toString()}`, {
        method: 'GET',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      const data = await response.json();
      if (!response.ok || !data.success) {
        renderFileList([]);
        return;
      }
      renderFileList(Array.isArray(data.list) ? data.list : []);
    } catch (_) {
      renderFileList([]);
    }
  }

  async function uploadCounselFile(file) {
    if (!file) return;
    if (!isCounselFileCandidate(file)) {
      setFileStatus('업로드 가능한 파일을 선택해 주세요.', true);
      return;
    }
    if (uploadBtn) {
      uploadBtn.disabled = true;
      uploadBtn.textContent = '업로드 중...';
    }
    setFileStatus('첨부파일 업로드 중...');

    try {
      const formData = new FormData();
      formData.append('file', file, file.name || `file_${Date.now()}`);
      if (csIdx && csIdx > 0) {
        formData.append('csIdx', String(csIdx));
      } else {
        formData.append('tempKey', ensureFileTempKey());
      }

      const response = await fetch(`${APP_CTX}/counsel/file/upload`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders(),
        body: formData
      });
      const data = await response.json();
      if (!response.ok || !data.success) {
        setFileStatus(data?.message || '첨부파일 업로드 실패', true);
        return;
      }

      setFileStatus('첨부파일 업로드 완료. 텍스트추출 후 적용해 주세요.');
      await fetchCounselFileList();
    } catch (_) {
      setFileStatus('첨부파일 업로드 중 오류', true);
    } finally {
      if (uploadBtn) {
        uploadBtn.disabled = false;
        uploadBtn.textContent = '서류 업로드';
      }
    }
  }

  function applySelectedCustomMatches(matches) {
    if (!Array.isArray(matches) || matches.length === 0) return 0;
    const targets = collectCustomTargets();
    let matchedCount = 0;

    matches.forEach((match) => {
      const base = String(match?.base || '').trim();
      if (!base) return;
      const target = targets.get(base);
      if (!target) return;

      const selectValue = String(match?.selectValue || '').trim();
      const textValue = String(match?.textValue || '').trim();
      const shouldCheck = match?.check === true || String(match?.check || '').trim().toUpperCase() === 'Y';

      let touched = false;
      if (target.select && selectValue && target.select.value !== selectValue) {
        target.select.value = selectValue;
        target.select.dispatchEvent(new Event('change', { bubbles: true }));
        touched = true;
      }
      if (target.text && textValue && !String(target.text.value || '').trim()) {
        target.text.value = textValue;
        target.text.dispatchEvent(new Event('input', { bubbles: true }));
        touched = true;
      }
      if (shouldCheck && target.checkbox && !target.checkbox.checked) {
        target.checkbox.checked = true;
        touched = true;
      }
      if (shouldCheck && target.radio && !target.radio.checked) {
        target.radio.checked = true;
        touched = true;
      }
      syncCheckboxByBase(base);
      if (touched) matchedCount += 1;
    });

    return matchedCount;
  }

  function applyExtractedTextToCounsel(text, filename, selectedMatches) {
    if (!counselContentInput) return { appended: false, matchedCount: 0 };
    const clean = String(text || '').trim();
    if (!clean) return { appended: false, matchedCount: 0 };

    const title = String(filename || '').trim();
    const appended = false;
    const matchedCount = applySelectedCustomMatches(selectedMatches);

    document.dispatchEvent(new CustomEvent('counsel-file-text', {
      detail: { text: clean, filename: title, matchedCount, appended }
    }));
    return { appended, matchedCount };
  }

  async function extractCounselFileText(fileId, extractBtn) {
    if (!fileId) return;
    if (extractBtn) {
      extractBtn.disabled = true;
      extractBtn.textContent = '추출중...';
    }
    setFileStatus('첨부파일 텍스트 추출 중...');

    try {
      const response = await fetch(`${APP_CTX}/counsel/file/extract/${encodeURIComponent(fileId)}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        setFileStatus(data?.message || '첨부파일 텍스트 추출 실패', true);
        return;
      }
      const text = String(data?.text || '').trim();
      if (!text) {
        setFileStatus('추출된 텍스트가 없습니다.', true);
        return;
      }

      const matchedCandidates = analyzeCustomMatchFromText(text).length;
      if (matchedCandidates > 0) {
        setFileStatus(`텍스트 추출 완료 (매칭 후보 ${matchedCandidates}건). 적용 버튼으로 커스텀 항목을 반영해 주세요.`);
      } else {
        setFileStatus('텍스트 추출 완료. 적용 가능한 커스텀 매칭 후보가 없습니다.');
      }
      await fetchCounselFileList();
    } catch (_) {
      setFileStatus('첨부파일 텍스트 추출 중 오류', true);
    } finally {
      if (extractBtn) {
        extractBtn.disabled = false;
        extractBtn.textContent = '텍스트추출';
      }
    }
  }

  async function applyCounselFileText(fileId, applyBtn) {
    if (!fileId || !applyBtn) return;
    const originalBtnText = String(applyBtn.textContent || '적용').trim() || '적용';
    applyBtn.disabled = true;
    applyBtn.textContent = '적용중...';

    try {
      const fileItem = applyBtn.closest('.counsel-file-item');
      const extracted = String(fileItem?.querySelector('.counsel-preview-source')?.textContent || '').trim();
      if (!extracted) {
        setFileStatus('먼저 텍스트추출을 실행해 주세요.', true);
        return;
      }
      const filename = String(fileItem?.querySelector('.counsel-file-link')?.textContent || '').trim();
      const selectedMatches = Array.from(fileItem?.querySelectorAll('.counsel-match-check:checked') || []).map((node) => ({
        base: String(node.getAttribute('data-match-base') || '').trim(),
        label: String(node.getAttribute('data-match-label') || '').trim(),
        type: String(node.getAttribute('data-match-type') || '').trim(),
        selectValue: String(node.getAttribute('data-match-select') || '').trim(),
        textValue: String(node.getAttribute('data-match-text') || '').trim(),
        check: String(node.getAttribute('data-match-check') || '').trim().toUpperCase() === 'Y'
      })).filter((match) => match.base);

      const applied = applyExtractedTextToCounsel(extracted, filename, selectedMatches);
      if (applied.matchedCount > 0) {
        setFileStatus(`선택 매칭 적용 완료 (커스텀 ${applied.matchedCount}건)`);
      } else {
        setFileStatus('적용할 커스텀 매칭 후보가 없습니다.');
      }
    } finally {
      applyBtn.disabled = false;
      applyBtn.textContent = originalBtnText;
    }
  }

  async function generateCounselFileSummary(fileId, summaryBtn) {
    if (!fileId) return;
    if (!openAiSummaryAvailable) {
      setFileStatus('OpenAI API 키 설정 후 문서 요약을 생성할 수 있습니다.', true);
      return;
    }
    if (summaryBtn) {
      summaryBtn.disabled = true;
      summaryBtn.textContent = '요약 중...';
    }
    setFileStatus('문서 요약 생성 중...');

    try {
      const response = await fetch(`${APP_CTX}/counsel/file/summary/${encodeURIComponent(fileId)}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        setFileStatus(data?.message || '문서 요약 생성 실패', true);
        return;
      }
      setFileStatus(data?.cached ? '저장된 문서 요약을 불러왔습니다.' : '문서 요약 생성 완료');
      await fetchCounselFileList();
    } catch (_) {
      setFileStatus('문서 요약 생성 중 오류', true);
    } finally {
      if (summaryBtn) {
        summaryBtn.disabled = false;
        summaryBtn.textContent = '요약 생성';
      }
    }
  }

  async function deleteCounselFile(fileId) {
    if (!fileId) return;
    try {
      const response = await fetch(`${APP_CTX}/counsel/file/delete/${encodeURIComponent(fileId)}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        setFileStatus(data?.message || '첨부파일 삭제 실패', true);
        return;
      }
      setFileStatus('첨부파일 삭제 완료');
      await fetchCounselFileList();
    } catch (_) {
      setFileStatus('첨부파일 삭제 중 오류', true);
    }
  }

  function bindFileDropZone() {
    if (!fileDropZoneEl || !fileInput) return;

    const preventDefault = (event) => {
      event.preventDefault();
      event.stopPropagation();
    };
    const markDragOver = (active) => {
      fileDropZoneEl.classList.toggle('is-dragover', !!active);
    };

    ['dragenter', 'dragover'].forEach((eventName) => {
      fileDropZoneEl.addEventListener(eventName, function (event) {
        preventDefault(event);
        markDragOver(true);
      });
    });

    ['dragleave', 'dragend', 'drop'].forEach((eventName) => {
      fileDropZoneEl.addEventListener(eventName, function (event) {
        preventDefault(event);
        markDragOver(false);
      });
    });

    fileDropZoneEl.addEventListener('drop', function (event) {
      const files = Array.from(event.dataTransfer?.files || []);
      if (!files.length) {
        setFileStatus('드롭한 파일을 찾을 수 없습니다.', true);
        return;
      }
      const targets = files.filter((candidate) => isCounselFileCandidate(candidate));
      targets.reduce((chain, file) => chain.then(() => uploadCounselFile(file)), Promise.resolve());
    });

    fileDropZoneEl.addEventListener('click', function () {
      fileInput.click();
    });

    fileDropZoneEl.addEventListener('keydown', function (event) {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      event.preventDefault();
      fileDropZoneEl.click();
    });
  }

  if (uploadBtn) {
    uploadBtn.addEventListener('click', function () {
      fileInput.click();
    });
  }

  fileInput.addEventListener('change', function () {
    const files = Array.from(this.files || []).filter((file) => isCounselFileCandidate(file));
    if (!files.length) return;
    files.reduce((chain, file) => chain.then(() => uploadCounselFile(file)), Promise.resolve()).finally(() => {
      this.value = '';
    });
  });

  bindFileDropZone();

  fileListEl.addEventListener('click', function (e) {
    const previewToggle = e.target.closest('.counsel-preview-toggle');
    if (previewToggle) {
      const card = previewToggle.closest('.counsel-preview-card');
      const targetView = String(previewToggle.getAttribute('data-view-target') || '').trim();
      if (!card || !targetView) return;
      updatePreviewMode(card, targetView);
      return;
    }

    const summaryBtn = e.target.closest('.counsel-file-summary');
    if (summaryBtn) {
      const fileId = summaryBtn.getAttribute('data-file-id');
      if (!fileId) return;
      generateCounselFileSummary(fileId, summaryBtn);
      return;
    }

    const extractBtn = e.target.closest('.counsel-file-extract');
    if (extractBtn) {
      const fileId = extractBtn.getAttribute('data-file-id');
      if (!fileId) return;
      extractCounselFileText(fileId, extractBtn);
      return;
    }

    const applyBtn = e.target.closest('.counsel-file-apply');
    if (applyBtn) {
      const fileId = applyBtn.getAttribute('data-file-id');
      if (!fileId) return;
      applyCounselFileText(fileId, applyBtn);
      return;
    }

    const matchOpenBtn = e.target.closest('.counsel-match-open-modal');
    if (matchOpenBtn) {
      const fileId = matchOpenBtn.getAttribute('data-file-id');
      if (!fileId) return;
      const fileItem = matchOpenBtn.closest('.counsel-file-item');
      const extracted = String(fileItem?.querySelector('.counsel-preview-source')?.textContent || '').trim();
      if (!extracted) { setFileStatus('먼저 텍스트추출을 실행해 주세요.', true); return; }
      openMatchModal(fileId, extracted);
      return;
    }

    const btn = e.target.closest('.counsel-file-delete');
    if (!btn) return;
    const fileId = btn.getAttribute('data-file-id');
    if (!fileId) return;
    if (!window.confirm('해당 첨부파일을 삭제하시겠습니까?')) return;
    deleteCounselFile(fileId);
  });

  ensureFileTempKey();
  fetchCounselFileList();
});
