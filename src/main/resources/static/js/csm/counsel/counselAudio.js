document.addEventListener('DOMContentLoaded', function () {
  const startRecordingBtn = document.getElementById('startRecordingBtn');
  const stopRecordingBtn = document.getElementById('stopRecordingBtn');
  const uploadAudioFileBtn = document.getElementById('uploadAudioFileBtn');
  const audioFileInput = document.getElementById('audioFileInput');
  const audioDropZoneEl = document.getElementById('audioDropZone');
  const applyDiseaseMatchBtn = document.getElementById('applyDiseaseMatchBtn');
  const generateSummaryBtn = document.getElementById('generateSummaryBtn');
  const recordingStatusEl = document.getElementById('recordingStatus');
  const recordingIndicatorEl = document.getElementById('recordingIndicator');
  const recordingTimerEl = document.getElementById('recordingTimer');
  const recordPreviewEl = document.getElementById('recordPreview');
  const audioRecordListEl = document.getElementById('audioRecordList');
  const audioTempKeyInput = document.getElementById('audio_temp_key');
  const counselContentInput = document.getElementById('cs_col_32');
  const counselForm = document.getElementById('counselForm');
  const clovaSttAvailable = (document.getElementById('clova_stt_available')?.value || 'N') === 'Y';
  const openAiSummaryAvailable = (document.getElementById('openai_summary_available')?.value || 'N') === 'Y';

  if (!startRecordingBtn || !stopRecordingBtn || !audioFileInput || !audioTempKeyInput) {
    return;
  }

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || '';

  const CONTEXT_PATH = (() => {
    if (typeof APP_CTX === 'string') {
      return APP_CTX;
    }
    const normalize = (value) => {
      if (!value || value === '/') return '';
      return value.endsWith('/') ? value.slice(0, -1) : value;
    };
    const metaCtx = document.querySelector('meta[name="app-context-path"]')?.getAttribute('content');
    return normalize((metaCtx || '').trim());
  })();

  const csIdxRaw = document.querySelector('input[name="cs_idx"]')?.value || '';
  const csIdx = /^\d+$/.test(csIdxRaw) ? Number(csIdxRaw) : null;

  function csrfHeaders() {
    if (!csrfToken || !csrfHeader) return {};
    return { [csrfHeader]: csrfToken };
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

  function normalizeAudioMimeType(value) {
    return String(value || '').trim().toLowerCase();
  }

  function supportsAudioRetranscription(item) {
    if (!clovaSttAvailable) return false;
    return !normalizeAudioMimeType(item?.mimeType).includes('webm');
  }

  function audioPreviewMode(item) {
    const summary = String(item?.summaryText || '').trim();
    const transcript = String(item?.transcript || '').trim();
    if (summary) return 'summary';
    if (transcript) return 'source';
    return 'empty';
  }

  function renderPreviewToggle(mode, hasSummary, hasSource) {
    if (!hasSummary || !hasSource) return '';
    const nextView = mode === 'summary' ? 'source' : 'summary';
    const label = nextView === 'summary' ? '요약 보기' : '원문 보기';
    return `<button type="button" class="counsel-preview-toggle" data-view-target="${nextView}">${label}</button>`;
  }

  function renderAudioPreview(item) {
    const summary = String(item?.summaryText || '').trim();
    const transcript = String(item?.transcript || '').trim();
    const mode = audioPreviewMode(item);
    const helperMessage = transcript
      ? (openAiSummaryAvailable ? '원문은 저장되어 있습니다. 필요할 때만 요약을 생성합니다.' : '원문은 저장되어 있습니다.')
      : (!clovaSttAvailable
        ? '음성 전사 설정이 없어 텍스트 재시도를 사용할 수 없습니다.'
        : (supportsAudioRetranscription(item)
          ? '텍스트 재시도 후 요약을 생성할 수 있습니다.'
          : '이 녹음은 webm 포맷이라 텍스트 재시도를 지원하지 않습니다.'));

    return `
      <div class="counsel-preview-card" data-view="${escapeHtml(mode)}">
        <div class="counsel-preview-toolbar">
          ${renderPreviewToggle(mode, !!summary, !!transcript)}
          ${!summary && openAiSummaryAvailable ? `<button type="button" class="counsel-preview-generate audio-record-summary" data-audio-id="${escapeHtml(item.id)}">요약 생성</button>` : ''}
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
    const toggleBtn = card.querySelector('.counsel-preview-toggle');
    if (!toggleBtn) return;
    const nextView = mode === 'summary' ? 'source' : 'summary';
    toggleBtn.setAttribute('data-view-target', nextView);
    toggleBtn.textContent = nextView === 'summary' ? '요약 보기' : '원문 보기';
  }

  function ensureAudioTempKey() {
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
      if (touched) matchedCount += 1;
    });

    return matchedCount;
  }

  function appendTranscriptToCounselContent(text) {
    if (!counselContentInput) return;
    const clean = String(text || '').trim();
    if (!clean) return;
    const prev = String(counselContentInput.value || '').trim();
    counselContentInput.value = prev ? `${prev}\n${clean}` : clean;
  }

  function hasTranscriptInCounselContent(text) {
    if (!counselContentInput) return false;
    const clean = String(text || '').trim();
    if (!clean) return false;
    return String(counselContentInput.value || '').includes(clean);
  }

  function syncCounselContentFromAudioList(list) {
    if (!counselContentInput || !Array.isArray(list) || list.length === 0) return 0;

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

  function formatRecordingDuration(seconds) {
    const safe = Math.max(0, Number(seconds) || 0);
    const mm = String(Math.floor(safe / 60)).padStart(2, '0');
    const ss = String(Math.floor(safe % 60)).padStart(2, '0');
    return `${mm}:${ss}`;
  }

  let recordStartedAt = 0;
  let recordingTimerInterval = null;

  function updateRecordingTimer() {
    if (!recordingTimerEl) return;
    const elapsed = Math.floor((Date.now() - recordStartedAt) / 1000);
    recordingTimerEl.textContent = formatRecordingDuration(elapsed);
  }

  function setRecordingIndicatorActive(active) {
    if (!recordingIndicatorEl) return;
    recordingIndicatorEl.classList.toggle('is-recording', !!active);

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
      const response = await fetch(`${CONTEXT_PATH}/counsel/audio/list?${params.toString()}`, {
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
        const canRetry = supportsAudioRetranscription(item);
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
    } catch (_) {
      audioRecordListEl.innerHTML = '';
    }
  }

  async function deleteAudio(audioId) {
    if (!audioId) return;
    try {
      const response = await fetch(`${CONTEXT_PATH}/counsel/audio/delete/${encodeURIComponent(audioId)}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders()
      });
      if (!response.ok) {
        setRecordingStatus('녹음 파일 삭제 실패', true);
        return;
      }
      setRecordingStatus('녹음 파일 삭제 완료');
      await fetchAudioList();
    } catch (_) {
      setRecordingStatus('녹음 파일 삭제 중 오류', true);
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
      const response = await fetch(`${CONTEXT_PATH}/counsel/audio/retranscribe/${encodeURIComponent(audioId)}`, {
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
    } catch (_) {
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
      setRecordingStatus('OpenAI API 키 설정 후 요약을 생성할 수 있습니다.', true);
      return;
    }
    if (summaryBtn) {
      summaryBtn.disabled = true;
      summaryBtn.textContent = '요약 중...';
    }
    setRecordingStatus('녹취 요약 생성 중...');

    try {
      const response = await fetch(`${CONTEXT_PATH}/counsel/audio/summary/${encodeURIComponent(audioId)}`, {
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
    } catch (_) {
      setRecordingStatus('녹취 요약 생성 중 오류', true);
    } finally {
      if (summaryBtn) {
        summaryBtn.disabled = false;
        summaryBtn.textContent = '요약 생성';
      }
    }
  }

  async function generateCounselSummary() {
    if (generateCounselSummary._running) return;
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

    generateCounselSummary._running = true;
    if (generateSummaryBtn) {
      generateSummaryBtn.disabled = true;
      generateSummaryBtn.textContent = '요약 중...';
    }
    setRecordingStatus('GPT 요약 생성 중...');

    try {
      const response = await fetch(`${CONTEXT_PATH}/counsel/summary/generate`, {
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
    } catch (_) {
      setRecordingStatus('상담 요약 생성 중 오류', true);
    } finally {
      generateCounselSummary._running = false;
      if (generateSummaryBtn) {
        generateSummaryBtn.disabled = false;
        generateSummaryBtn.textContent = '상담 요약';
      }
    }
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
    const summaryHint = (!openAiSummaryAvailable && serverTranscript && !alreadyHadTranscript)
      ? ' / 상담요약은 OpenAI API 키 설정 후 사용 가능합니다.'
      : '';
    let statusSet = false;

    if (serverTranscript && !alreadyHadTranscript) {
      appendTranscriptToCounselContent(serverTranscript);
      const sourceForMatch = counselContentInput ? counselContentInput.value : serverTranscript;
      const matchedFromServer = applyDiseaseMatchFromText(sourceForMatch);
      if (transcriptSource === 'browser') {
        setRecordingStatus('클로바 인식 실패로 브라우저 임시 인식으로 저장됨', true);
        statusSet = true;
      } else if (matchedFromServer > 0) {
        setRecordingStatus(`전사 반영 완료 (커스텀 ${matchedFromServer}건 매칭)${summaryHint}`);
        statusSet = true;
      } else {
        setRecordingStatus(`전사 반영 완료${summaryHint}`);
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
    if (openAiSummaryAvailable && serverTranscript && !alreadyHadTranscript) {
      setRecordingStatus('전사 반영 완료. 자동 상담 요약 생성 중...');
      await generateCounselSummary();
    }
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
      const response = await fetch(`${CONTEXT_PATH}/counsel/audio/upload`, {
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
    } catch (_) {
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

      const response = await fetch(`${CONTEXT_PATH}/counsel/audio/upload`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders(),
        body: formData
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        const message = data?.message || '음성 파일 업로드 실패';
        setRecordingStatus(message, true);
        return;
      }
      await handleAudioUploadSuccess(data, '파일 업로드 완료');
    } catch (_) {
      setRecordingStatus('음성 파일 업로드 중 오류', true);
    } finally {
      if (uploadAudioFileBtn) {
        uploadAudioFileBtn.disabled = false;
        uploadAudioFileBtn.textContent = '파일 업로드';
      }
    }
  }

  let mediaRecorder = null;
  let mediaStream = null;
  let chunks = [];
  let isRecording = false;
  let finalTranscript = '';
  let speechRecognition = null;

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
    speechRecognition.onerror = function () {};
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

  async function startRecording() {
    if (isRecording) return;

    if (!navigator.mediaDevices || typeof navigator.mediaDevices.getUserMedia !== 'function' || typeof window.MediaRecorder === 'undefined') {
      alert('이 브라우저는 녹음을 지원하지 않습니다.');
      return;
    }

    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });

      const preferredMimeTypes = [
        'audio/mp4',
        'audio/ogg;codecs=opus',
        'audio/ogg',
        'audio/wav'
      ];
      const fallbackMimeTypes = [
        'audio/webm;codecs=opus',
        'audio/webm'
      ];

      let recorderOptions = {};
      for (const mime of preferredMimeTypes) {
        if (window.MediaRecorder.isTypeSupported && window.MediaRecorder.isTypeSupported(mime)) {
          recorderOptions = { mimeType: mime };
          break;
        }
      }
      if (!recorderOptions.mimeType) {
        for (const mime of fallbackMimeTypes) {
          if (window.MediaRecorder.isTypeSupported && window.MediaRecorder.isTypeSupported(mime)) {
            recorderOptions = { mimeType: mime };
            break;
          }
        }
      }

      mediaRecorder = Object.keys(recorderOptions).length > 0
        ? new MediaRecorder(mediaStream, recorderOptions)
        : new MediaRecorder(mediaStream);

      chunks = [];
      finalTranscript = '';
      recordStartedAt = Date.now();

      mediaRecorder.ondataavailable = function (event) {
        if (event.data && event.data.size > 0) {
          chunks.push(event.data);
        }
      };

      mediaRecorder.onstop = async function () {
        setRecordingIndicatorActive(false);
        stopSpeechRecognition();

        const tracks = mediaStream ? mediaStream.getTracks() : [];
        tracks.forEach((track) => track.stop());
        mediaStream = null;

        const blob = new Blob(chunks, { type: mediaRecorder.mimeType || 'audio/webm' });
        const durationSeconds = Math.max(0, (Date.now() - recordStartedAt) / 1000);
        const transcript = String(finalTranscript || '').trim();

        if (recordPreviewEl) {
          recordPreviewEl.src = URL.createObjectURL(blob);
          recordPreviewEl.style.display = 'block';
        }

        await uploadAudioBlob(blob, durationSeconds, transcript);
      };

      mediaRecorder.start(300);
      isRecording = true;
      startRecordingBtn.disabled = true;
      stopRecordingBtn.disabled = false;
      setRecordingIndicatorActive(true);
      startSpeechRecognition();
      const recordingMimeType = normalizeAudioMimeType(mediaRecorder.mimeType || recorderOptions.mimeType || '');
      if (recordingMimeType.includes('webm')) {
        setRecordingStatus('이 브라우저는 webm 녹음만 지원해 텍스트 재시도는 제한됩니다.', true);
      } else {
        setRecordingStatus('녹음 중...');
      }
    } catch (_) {
      isRecording = false;
      startRecordingBtn.disabled = false;
      stopRecordingBtn.disabled = true;
      setRecordingIndicatorActive(false);
      setRecordingStatus('마이크 접근 권한이 없거나 녹음을 시작할 수 없습니다.', true);
    }
  }

  function stopRecording() {
    if (!isRecording) return;
    isRecording = false;
    startRecordingBtn.disabled = false;
    stopRecordingBtn.disabled = true;
    setRecordingStatus('녹음 종료 처리 중...');

    try {
      stopSpeechRecognition();
      if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
      }
    } catch (_) {
      setRecordingStatus('녹음 중지 실패', true);
      setRecordingIndicatorActive(false);
    }
  }

  function bindAudioDropZone() {
    if (!audioDropZoneEl || !audioFileInput) return;

    const preventDefault = (event) => {
      event.preventDefault();
      event.stopPropagation();
    };
    const markDragOver = (active) => {
      audioDropZoneEl.classList.toggle('is-dragover', !!active);
    };

    ['dragenter', 'dragover'].forEach((eventName) => {
      audioDropZoneEl.addEventListener(eventName, function (event) {
        preventDefault(event);
        if (!isRecording) {
          markDragOver(true);
        }
      });
    });

    ['dragleave', 'dragend', 'drop'].forEach((eventName) => {
      audioDropZoneEl.addEventListener(eventName, function (event) {
        preventDefault(event);
        markDragOver(false);
      });
    });

    audioDropZoneEl.addEventListener('drop', function (event) {
      if (isRecording) {
        setRecordingStatus('녹음 중에는 파일 업로드를 할 수 없습니다.', true);
        return;
      }

      const files = Array.from(event.dataTransfer?.files || []);
      if (!files.length) {
        setRecordingStatus('드롭한 파일을 찾을 수 없습니다.', true);
        return;
      }

      const file = files.find((candidate) => isAudioUploadCandidate(candidate)) || files[0];
      uploadAudioFile(file);
    });

    audioDropZoneEl.addEventListener('click', function () {
      if (isRecording) {
        setRecordingStatus('녹음 중에는 파일 업로드를 할 수 없습니다.', true);
        return;
      }
      audioFileInput.click();
    });

    audioDropZoneEl.addEventListener('keydown', function (event) {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      event.preventDefault();
      audioDropZoneEl.click();
    });
  }

  startRecordingBtn.addEventListener('click', startRecording);
  stopRecordingBtn.addEventListener('click', stopRecording);

  if (uploadAudioFileBtn) {
    uploadAudioFileBtn.addEventListener('click', function () {
      if (isRecording) {
        setRecordingStatus('녹음 중에는 파일 업로드를 할 수 없습니다.', true);
        return;
      }
      audioFileInput.click();
    });
  }

  audioFileInput.addEventListener('change', function () {
    const file = this.files && this.files[0] ? this.files[0] : null;
    if (!file) return;
    uploadAudioFile(file).finally(() => {
      this.value = '';
    });
  });
  bindAudioDropZone();

  if (applyDiseaseMatchBtn) {
    applyDiseaseMatchBtn.addEventListener('click', function () {
      const source = String(counselContentInput?.value || '').trim();
      if (!source) {
        setRecordingStatus('상담내용이 비어 있어 매칭할 수 없습니다.', true);
        return;
      }
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
    if (!openAiSummaryAvailable) {
      generateSummaryBtn.title = 'OpenAI API 키 설정 후 사용 가능합니다.';
    }
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

      const deleteBtn = e.target.closest('.audio-record-delete');
      if (!deleteBtn) return;
      const audioId = deleteBtn.getAttribute('data-audio-id');
      if (!audioId) return;
      if (!window.confirm('해당 녹음 파일을 삭제하시겠습니까?')) return;
      deleteAudio(audioId);
    });
  }

  if (counselForm) {
    counselForm.addEventListener('submit', function (event) {
      if (isRecording) {
        event.preventDefault();
        setRecordingStatus('녹음이 진행 중입니다. 녹음을 중지한 뒤 저장해 주세요.', true);
      }
    });
  }

  ensureAudioTempKey();
  fetchAudioList();
});
