(function () {
  const bootstrap = window.ADMISSION_PLEDGE_BOOTSTRAP || {};
  const admissionPledge = bootstrap.admissionPledge || {};
  // 개인 서명 기능은 추후 재사용을 위해 코드 유지, 현재는 비활성화
  const ENABLE_INDIVIDUAL_SIGNATURE = false;
  const MAX_PAGE_INK_DATA_LENGTH = 2_900_000;

  const agreeCheckbox = document.getElementById('ap_agree');
  const pledgeTextInput = document.getElementById('ap_pledge_text');
  const signerNameInput = document.getElementById('ap_signer_name');
  const signerRelationInput = document.getElementById('ap_signer_relation');
  const signedAtInput = document.getElementById('ap_signed_at');
  const signedAtView = document.getElementById('ap_signed_at_view');
  const patientNameInput = document.getElementById('ap_patient_name');

  const signatureInput = document.getElementById('ap_signature_data');
  const clearSignatureBtn = document.getElementById('ap_clear_signature');

  const documentStage = document.getElementById('ap_document_stage');
  const pageInkCanvas = document.getElementById('ap_page_ink_canvas');
  const pageInkPrintImg = document.getElementById('ap_page_ink_print_img');
  const pageInkInput = document.getElementById('ap_page_ink_data');
  let lastPageInkData = '';
  let isPrintTransition = false;
  const inkModeToggleBtn = document.getElementById('ap_ink_mode_toggle');
  const inkClearBtn = document.getElementById('ap_ink_clear');
  const penColorInput = document.getElementById('ap_pen_color');
  const penSizeInput = document.getElementById('ap_pen_size');
  const penSizeView = document.getElementById('ap_pen_size_view');

  const cancelBtn = document.getElementById('ap_cancel');
  const printBtn = document.getElementById('ap_print');
  const applyBtn = document.getElementById('ap_apply');

  const signatureModal = document.getElementById('ap_signature_modal');
  const signatureModalTitle = document.getElementById('ap_signature_modal_title');
  const signatureModalCanvas = document.getElementById('ap_signature_modal_canvas');
  const signatureModalClearBtn = document.getElementById('ap_signature_modal_clear');
  const signatureModalCancelBtn = document.getElementById('ap_signature_modal_cancel');
  const signatureModalApplyBtn = document.getElementById('ap_signature_modal_apply');

  const signImages = {
    guardian: document.getElementById('canvasImg1'),
    sub_guardian: document.getElementById('canvasImg2'),
    primary: document.getElementById('canvasImg')
  };

  const signLabels = {
    guardian: '주보호자 서명',
    sub_guardian: '부보호자 서명',
    primary: '신청인 서명'
  };

  const defaultPledgeText = String(bootstrap.defaultAdmissionPledgeText || '').trim()
    || '본인은 입원 연계 및 상담을 위해 제공한 정보가 병원 입원 진행에 활용되는 것에 동의합니다. 또한 상담 과정에서 안내받은 내용을 확인하였으며, 안내된 절차에 따라 성실히 협조할 것을 서약합니다.';

  function normalizePenSize(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return 2;
    return Math.max(1, Math.min(10, Math.round(n)));
  }

  function normalizePenColor(value) {
    const color = String(value || '').trim();
    return /^#[0-9a-fA-F]{6}$/.test(color) ? color : '#111827';
  }

  const penState = {
    color: normalizePenColor(penColorInput?.value || '#111827'),
    size: normalizePenSize(penSizeInput?.value || 2)
  };

  function pad2(n) {
    return String(n).padStart(2, '0');
  }

  function nowDateTime() {
    const d = new Date();
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
  }

  function safeJsonParse(raw) {
    try {
      return JSON.parse(raw);
    } catch (_) {
      return null;
    }
  }

  function isValidImageData(value) {
    return /^data:image\/png;base64,/.test(String(value || '').trim());
  }

  function sanitizeImageData(value) {
    const data = String(value || '').trim();
    return isValidImageData(data) ? data : '';
  }

  function firstValidImage(values) {
    for (const value of values) {
      const data = sanitizeImageData(value);
      if (data) {
        return data;
      }
    }
    return '';
  }

  function parseSignatureBundle(raw) {
    const empty = { primary: '', guardian: '', sub_guardian: '' };
    const text = String(raw || '').trim();
    if (!text) {
      return empty;
    }

    if (isValidImageData(text)) {
      empty.primary = text;
      return empty;
    }

    const parsed = safeJsonParse(text);
    if (!parsed || typeof parsed !== 'object') {
      return empty;
    }

    return {
      primary: firstValidImage([
        parsed.primary,
        parsed.main,
        parsed.signer,
        parsed.final_signature,
        parsed.signature
      ]),
      guardian: firstValidImage([
        parsed.guardian,
        parsed.guardian_signature,
        parsed.primary_guardian
      ]),
      sub_guardian: firstValidImage([
        parsed.sub_guardian,
        parsed.subGuardian,
        parsed.sub_guardian_signature,
        parsed.secondary_guardian
      ])
    };
  }

  function serializeSignatureBundle(bundle) {
    const primary = sanitizeImageData(bundle.primary);
    const guardian = sanitizeImageData(bundle.guardian);
    const subGuardian = sanitizeImageData(bundle.sub_guardian);

    if (!guardian && !subGuardian) {
      return primary;
    }

    return JSON.stringify({
      version: 2,
      primary,
      guardian,
      sub_guardian: subGuardian
    });
  }

  function hasPrimarySignature(raw) {
    const bundle = parseSignatureBundle(raw);
    return !!sanitizeImageData(bundle.primary);
  }

  function consumeDraftPayloadByKey(draftKey) {
    const key = String(draftKey || '').trim();
    if (!key) return null;

    let raw = null;
    try {
      raw = sessionStorage.getItem(key);
      if (raw) {
        sessionStorage.removeItem(key);
      }
    } catch (_) {
      raw = null;
    }

    if (!raw && window.opener && !window.opener.closed) {
      try {
        raw = window.opener.sessionStorage.getItem(key);
        if (raw) {
          window.opener.sessionStorage.removeItem(key);
        }
      } catch (_) {
        raw = null;
      }
    }

    if (!raw) return null;
    const parsed = safeJsonParse(raw);
    return parsed && typeof parsed === 'object' ? parsed : null;
  }

  function storeReturnPayload(payload) {
    const raw = JSON.stringify(payload);
    try {
      sessionStorage.setItem('csm-admission-pledge-return', raw);
    } catch (_) {
      // no-op
    }

    if (window.opener && !window.opener.closed) {
      try {
        window.opener.sessionStorage.setItem('csm-admission-pledge-return', raw);
      } catch (_) {
        // no-op
      }
    }
  }

  function setPledgeTextIfEmpty() {
    if (!pledgeTextInput) return;
    if (!String(pledgeTextInput.value || '').trim()) {
      pledgeTextInput.value = defaultPledgeText;
    }
  }

  function syncSignedAtView() {
    const value = String(signedAtInput?.value || '').trim();
    if (signedAtView) {
      signedAtView.textContent = value || '-';
    }
  }

  function configureCanvasStyle(ctx) {
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.lineWidth = penState.size;
    ctx.strokeStyle = penState.color;
  }

  function drawImageToCanvas(canvas, ctx, dataUrl) {
    const source = sanitizeImageData(dataUrl);
    if (!source) {
      return;
    }
    const image = new Image();
    image.onload = function () {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
      configureCanvasStyle(ctx);
    };
    image.src = source;
  }

  function syncPageInkPrintImage() {
    if (!pageInkPrintImg) return;
    const data = String(pageInkInput?.value || '').trim() || String(lastPageInkData || '').trim();
    if (isValidImageData(data)) {
      pageInkPrintImg.src = data;
      return;
    }
    pageInkPrintImg.removeAttribute('src');
  }

  function exportCanvasForStorage(canvas, maxLength) {
    if (!canvas || typeof canvas.toDataURL !== 'function') {
      return '';
    }

    const targetMax = Number.isFinite(maxLength) && maxLength > 0
      ? Math.floor(maxLength)
      : MAX_PAGE_INK_DATA_LENGTH;

    const rect = canvas.getBoundingClientRect();
    let exportWidth = Math.max(1, Math.floor(rect.width || canvas.width || 1));
    let exportHeight = Math.max(1, Math.floor(rect.height || canvas.height || 1));

    const tmpCanvas = document.createElement('canvas');
    const tmpCtx = tmpCanvas.getContext('2d');
    if (!tmpCtx) {
      try {
        return canvas.toDataURL('image/png');
      } catch (_) {
        return '';
      }
    }

    const toPng = (width, height) => {
      tmpCanvas.width = width;
      tmpCanvas.height = height;
      tmpCtx.clearRect(0, 0, width, height);
      tmpCtx.drawImage(canvas, 0, 0, width, height);
      return tmpCanvas.toDataURL('image/png');
    };

    let dataUrl = toPng(exportWidth, exportHeight);
    let guard = 0;
    while (dataUrl.length > targetMax && exportWidth > 200 && exportHeight > 200 && guard < 12) {
      exportWidth = Math.max(200, Math.floor(exportWidth * 0.9));
      exportHeight = Math.max(200, Math.floor(exportHeight * 0.9));
      dataUrl = toPng(exportWidth, exportHeight);
      guard += 1;
    }
    return dataUrl;
  }

  function setupCanvas(canvas, ctx, getData, setData, getTargetRect, drawingGuardFn) {
    let drawing = false;

    function resizeCanvas() {
      const ratio = Math.max(window.devicePixelRatio || 1, 1);
      const rect = getTargetRect();
      const width = Math.max(1, Math.floor(rect.width));
      const height = Math.max(1, Math.floor(rect.height));
      // print 전환 중 임시로 1px 수준 rect가 들어오면 기존 캔버스를 유지한다.
      if (width < 50 || height < 50) {
        return;
      }
      const existing = String(getData() || '').trim();

      canvas.width = Math.floor(width * ratio);
      canvas.height = Math.floor(height * ratio);
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      configureCanvasStyle(ctx);

      if (isValidImageData(existing)) {
        drawImageToCanvas(canvas, ctx, existing);
      }
    }

    function pointerPoint(event) {
      const rect = canvas.getBoundingClientRect();
      return {
        x: event.clientX - rect.left,
        y: event.clientY - rect.top
      };
    }

    function persist() {
      if (canvas === pageInkCanvas) {
        setData(exportCanvasForStorage(canvas, MAX_PAGE_INK_DATA_LENGTH));
        return;
      }
      setData(canvas.toDataURL('image/png'));
    }

    canvas.addEventListener('pointerdown', function (event) {
      if (drawingGuardFn && !drawingGuardFn()) return;
      drawing = true;
      const point = pointerPoint(event);
      configureCanvasStyle(ctx);
      ctx.beginPath();
      ctx.moveTo(point.x, point.y);
      canvas.setPointerCapture(event.pointerId);
      event.preventDefault();
    });

    canvas.addEventListener('pointermove', function (event) {
      if (!drawing) return;
      const point = pointerPoint(event);
      ctx.lineTo(point.x, point.y);
      ctx.stroke();
      event.preventDefault();
    });

    const endDraw = function (event) {
      if (!drawing) return;
      drawing = false;
      ctx.closePath();
      if (event.pointerId != null) {
        try {
          canvas.releasePointerCapture(event.pointerId);
        } catch (_) {
          // no-op
        }
      }
      persist();
      event.preventDefault();
    };

    canvas.addEventListener('pointerup', endDraw);
    canvas.addEventListener('pointercancel', endDraw);
    canvas.addEventListener('pointerleave', endDraw);

    resizeCanvas();
    return {
      resizeCanvas,
      clear() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        setData('');
      },
      load(dataUrl) {
        const valid = sanitizeImageData(dataUrl);
        if (!valid) {
          this.clear();
          return;
        }
        setData(valid);
        drawImageToCanvas(canvas, ctx, valid);
      },
      value() {
        return String(getData() || '').trim();
      }
    };
  }

  const pageInkCtx = pageInkCanvas.getContext('2d');
  let inkMode = false;

  function setInkMode(enabled) {
    inkMode = !!enabled;
    pageInkCanvas.classList.toggle('is-drawing', inkMode);
    inkModeToggleBtn.textContent = inkMode ? '전체 필기: ON' : '전체 필기: OFF';
  }

  const pageInkPad = setupCanvas(
    pageInkCanvas,
    pageInkCtx,
    () => pageInkInput.value,
    (value) => {
      const raw = String(value || '').trim();
      if (!raw) {
        pageInkInput.value = '';
        lastPageInkData = '';
        syncPageInkPrintImage();
        return;
      }
      const valid = sanitizeImageData(raw);
      if (!valid) {
        syncPageInkPrintImage();
        return;
      }
      pageInkInput.value = valid;
      lastPageInkData = valid;
      syncPageInkPrintImage();
    },
    () => documentStage.getBoundingClientRect(),
    () => inkMode
  );

  const modalCtx = signatureModalCanvas.getContext('2d');
  let modalOpen = false;
  let modalWorkingData = '';
  let currentSignKey = null;

  function syncPenControlView() {
    if (penColorInput) {
      penColorInput.value = penState.color;
    }
    if (penSizeInput) {
      penSizeInput.value = String(penState.size);
    }
    if (penSizeView) {
      penSizeView.textContent = `${penState.size}px`;
    }
  }

  function applyPenStyleToAllCanvases() {
    configureCanvasStyle(pageInkCtx);
    configureCanvasStyle(modalCtx);
  }

  const modalPad = setupCanvas(
    signatureModalCanvas,
    modalCtx,
    () => modalWorkingData,
    (value) => {
      modalWorkingData = value;
    },
    () => signatureModalCanvas.getBoundingClientRect(),
    () => modalOpen
  );

  let signatures = {
    primary: '',
    guardian: '',
    sub_guardian: ''
  };

  function renderSignatureImage(imageEl, data) {
    if (!imageEl) return;
    const valid = sanitizeImageData(data);
    if (!valid) {
      imageEl.removeAttribute('src');
      imageEl.style.display = 'none';
      return;
    }
    imageEl.src = valid;
    imageEl.style.display = '';
  }

  function syncSignatureInput() {
    signatureInput.value = serializeSignatureBundle(signatures);
  }

  function renderSignatures() {
    renderSignatureImage(signImages.guardian, signatures.guardian);
    renderSignatureImage(signImages.sub_guardian, signatures.sub_guardian);
    renderSignatureImage(signImages.primary, signatures.primary);
    syncSignatureInput();
  }

  function clearSignatureImagesOnly() {
    renderSignatureImage(signImages.guardian, '');
    renderSignatureImage(signImages.sub_guardian, '');
    renderSignatureImage(signImages.primary, '');
  }

  function clearAllSignatures() {
    signatures = { primary: '', guardian: '', sub_guardian: '' };
    renderSignatures();
    if (signedAtInput) {
      signedAtInput.value = '';
      syncSignedAtView();
    }
  }

  function openSignatureModal(key) {
    currentSignKey = key;
    modalOpen = true;
    modalWorkingData = sanitizeImageData(signatures[key]);
    signatureModalTitle.textContent = signLabels[key] || '서명';
    signatureModal.classList.add('show');
    signatureModal.setAttribute('aria-hidden', 'false');
    modalPad.resizeCanvas();
    if (modalWorkingData) {
      modalPad.load(modalWorkingData);
    } else {
      modalPad.clear();
    }
  }

  function closeSignatureModal() {
    modalOpen = false;
    currentSignKey = null;
    signatureModal.classList.remove('show');
    signatureModal.setAttribute('aria-hidden', 'true');
  }

  function applySignatureModal() {
    if (!currentSignKey) {
      closeSignatureModal();
      return;
    }

    const signedData = sanitizeImageData(modalPad.value());
    signatures[currentSignKey] = signedData;
    renderSignatures();

    if (signedData && signedAtInput && !String(signedAtInput.value || '').trim()) {
      signedAtInput.value = nowDateTime();
      syncSignedAtView();
    }

    closeSignatureModal();
  }

  function setupVipRoomToggle() {
    const vipCheckbox = document.getElementById('ap_vip_room');
    const roomNoInput = document.getElementById('ap_vip_room_no');
    const type1 = document.getElementById('ap_vip_type_1');
    const type2 = document.getElementById('ap_vip_type_2');
    const type3 = document.getElementById('ap_vip_type_3');
    const priceInput = document.getElementById('ap_vip_price');

    if (!vipCheckbox) return;

    const targets = [roomNoInput, type1, type2, type3, priceInput].filter(Boolean);
    const sync = () => {
      const enabled = !!vipCheckbox.checked;
      targets.forEach((el) => {
        el.disabled = !enabled;
        if (!enabled && el.type === 'checkbox') {
          el.checked = false;
        }
      });
    };

    vipCheckbox.addEventListener('change', sync);
    sync();
  }

  function applyPayload(payload) {
    if (!payload || typeof payload !== 'object') return;

    if (agreeCheckbox) {
      agreeCheckbox.checked = payload.agreed_yn === 'Y';
    }

    const signerName = String(payload.signer_name || '').trim();
    if (signerName && signerNameInput) signerNameInput.value = signerName;

    const signerRelation = String(payload.signer_relation || '').trim();
    if (signerRelation && signerRelationInput) signerRelationInput.value = signerRelation;

    const signedAt = String(payload.signed_at || '').trim();
    if (signedAt && signedAtInput) {
      signedAtInput.value = signedAt;
      syncSignedAtView();
    }

    const pledgeText = String(payload.pledge_text || '').trim();
    if (pledgeTextInput) {
      pledgeTextInput.value = pledgeText || defaultPledgeText;
    }

    const payloadPenColor = normalizePenColor(payload.pen_color);
    const payloadPenSize = normalizePenSize(payload.pen_size);
    penState.color = payloadPenColor;
    penState.size = payloadPenSize;
    syncPenControlView();
    applyPenStyleToAllCanvases();

    if (ENABLE_INDIVIDUAL_SIGNATURE) {
      signatures = parseSignatureBundle(payload.signature_data);
      renderSignatures();
    } else {
      signatures = { primary: '', guardian: '', sub_guardian: '' };
      signatureInput.value = '';
      clearSignatureImagesOnly();
    }

    const pageInkData = sanitizeImageData(payload.page_ink_data);
    if (pageInkData) {
      pageInkInput.value = pageInkData;
      lastPageInkData = pageInkData;
      drawImageToCanvas(pageInkCanvas, pageInkCtx, pageInkData);
      syncPageInkPrintImage();
    } else {
      pageInkInput.value = '';
      lastPageInkData = '';
      syncPageInkPrintImage();
    }
  }

  cancelBtn.addEventListener('click', function () {
    if (window.opener && !window.opener.closed) {
      window.close();
      return;
    }
    const returnUrl = String(bootstrap.returnUrl || '').trim();
    if (returnUrl) {
      window.location.href = returnUrl;
      return;
    }
    window.history.back();
  });

  if (printBtn) {
    printBtn.addEventListener('click', function () {
      resizeForPrint();
      setTimeout(function () {
        window.print();
      }, 0);
    });
  }

  clearSignatureBtn.addEventListener('click', function () {
    clearAllSignatures();
  });

  inkModeToggleBtn.addEventListener('click', function () {
    setInkMode(!inkMode);
  });

  inkClearBtn.addEventListener('click', function () {
    pageInkPad.clear();
  });

  if (penColorInput) {
    penColorInput.addEventListener('input', function () {
      penState.color = normalizePenColor(this.value);
      syncPenControlView();
      applyPenStyleToAllCanvases();
    });
  }

  if (penSizeInput) {
    penSizeInput.addEventListener('input', function () {
      penState.size = normalizePenSize(this.value);
      syncPenControlView();
      applyPenStyleToAllCanvases();
    });
  }

  signatureModalClearBtn.addEventListener('click', function () {
    modalPad.clear();
  });

  signatureModalCancelBtn.addEventListener('click', function () {
    closeSignatureModal();
  });

  signatureModalApplyBtn.addEventListener('click', function () {
    applySignatureModal();
  });

  signatureModal.addEventListener('click', function (event) {
    if (event.target === signatureModal) {
      closeSignatureModal();
    }
  });

  const signatureTriggers = Array.from(document.querySelectorAll('.ap-sign-trigger'));
  if (ENABLE_INDIVIDUAL_SIGNATURE) {
    signatureTriggers.forEach((trigger) => {
      trigger.addEventListener('click', function () {
        const key = String(trigger.dataset.signatureKey || '').trim();
        if (!key || !Object.prototype.hasOwnProperty.call(signatures, key)) {
          return;
        }
        openSignatureModal(key);
      });
    });
  } else {
    signatureTriggers.forEach((trigger) => {
      trigger.classList.add('is-disabled');
      trigger.setAttribute('title', '현재 개인 서명 기능은 비활성화되어 있습니다.');
    });
    signatures = { primary: '', guardian: '', sub_guardian: '' };
    signatureInput.value = '';
    clearSignatureImagesOnly();
    if (clearSignatureBtn) {
      clearSignatureBtn.style.display = 'none';
    }
  }

  function buildPayload() {
    const signerName = String(signerNameInput?.value || '').trim() || String(patientNameInput?.value || '').trim();
    const signerRelation = String(signerRelationInput?.value || '').trim() || '본인';
    const signedAt = String(signedAtInput?.value || '').trim() || nowDateTime();
    const pledgeText = String(pledgeTextInput?.value || '').trim() || defaultPledgeText;
    const csIdxValue = /^\d+$/.test(String(bootstrap.csIdx || '').trim()) ? Number(bootstrap.csIdx) : 0;

    if (signerNameInput) signerNameInput.value = signerName;
    if (signerRelationInput) signerRelationInput.value = signerRelation;
    if (signedAtInput) signedAtInput.value = signedAt;
    if (pledgeTextInput) pledgeTextInput.value = pledgeText;
    syncSignedAtView();

    syncSignatureInput();

    return {
      cs_idx: csIdxValue,
      agreed_yn: agreeCheckbox && agreeCheckbox.checked ? 'Y' : 'N',
      signer_name: signerName,
      signer_relation: signerRelation,
      signed_at: signedAt,
      pledge_text: pledgeText,
      signature_data: String(signatureInput.value || '').trim(),
      page_ink_data: String(pageInkInput.value || '').trim(),
      pen_color: penState.color,
      pen_size: penState.size
    };
  }

  applyBtn.addEventListener('click', function () {
    const payload = buildPayload();

    if (payload.agreed_yn !== 'Y') {
      alert('서약 동의 체크가 필요합니다.');
      agreeCheckbox?.focus();
      return;
    }

    if (!payload.signer_name) {
      alert('신청인 성명을 입력해 주세요.');
      signerNameInput?.focus();
      return;
    }

    if (ENABLE_INDIVIDUAL_SIGNATURE && !hasPrimarySignature(payload.signature_data)) {
      alert('신청인 서명을 작성해 주세요.');
      return;
    }

    storeReturnPayload(payload);

    if (window.opener && !window.opener.closed) {
      try {
        window.opener.postMessage({ type: 'csm-admission-pledge', payload }, window.location.origin);
      } catch (_) {
        window.opener.postMessage({ type: 'csm-admission-pledge', payload }, '*');
      }
      window.close();
      return;
    }

    const returnUrl = String(bootstrap.returnUrl || '').trim();
    if (returnUrl) {
      window.location.href = returnUrl;
      return;
    }
    alert('서약서 정보를 저장했습니다. 이전 화면에서 저장을 진행해 주세요.');
  });

  const draftPayload = consumeDraftPayloadByKey(bootstrap.draftKey);
  if (draftPayload) {
    applyPayload(draftPayload);
  } else if (admissionPledge && typeof admissionPledge === 'object') {
    applyPayload(admissionPledge);
  }

  setPledgeTextIfEmpty();
  setInkMode(false);
  setupVipRoomToggle();
  syncSignedAtView();
  syncPenControlView();
  applyPenStyleToAllCanvases();
  syncPageInkPrintImage();

  window.addEventListener('resize', function () {
    if (isPrintTransition) {
      return;
    }
    if (typeof window.matchMedia === 'function' && window.matchMedia('print').matches) {
      return;
    }
    pageInkPad.resizeCanvas();
    if (modalOpen) {
      modalPad.resizeCanvas();
    }
  });

  function resizeForPrint() {
    isPrintTransition = true;
    try {
      if (pageInkCanvas.width > 0 && pageInkCanvas.height > 0) {
        const exported = sanitizeImageData(exportCanvasForStorage(pageInkCanvas, MAX_PAGE_INK_DATA_LENGTH));
        if (exported) {
          pageInkInput.value = exported;
          lastPageInkData = exported;
        }
      }
    } catch (_) {
      // no-op
    }
    syncPageInkPrintImage();
  }

  function restoreAfterPrint() {
    isPrintTransition = false;
    const repaint = () => {
      pageInkPad.resizeCanvas();
      if (modalOpen) {
        modalPad.resizeCanvas();
      }
      syncPageInkPrintImage();
    };
    setTimeout(repaint, 80);
    setTimeout(repaint, 240);
  }

  window.addEventListener('beforeprint', resizeForPrint);
  window.addEventListener('afterprint', restoreAfterPrint);

  if (typeof window.matchMedia === 'function') {
    const printMedia = window.matchMedia('print');
    if (typeof printMedia.addEventListener === 'function') {
      printMedia.addEventListener('change', function (event) {
        if (event.matches) {
          resizeForPrint();
        } else {
          restoreAfterPrint();
        }
      });
    } else if (typeof printMedia.addListener === 'function') {
      printMedia.addListener(function (event) {
        if (event.matches) {
          resizeForPrint();
        } else {
          restoreAfterPrint();
        }
      });
    }
  }
})();
