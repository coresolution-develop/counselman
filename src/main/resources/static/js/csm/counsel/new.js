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
function handleSubmit() { return true; }

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

    if (value === '입원예약') {
      reservationFields.css('display', 'flex');
      noAdmissionReason.css('display', 'none');
    } else if (value === '입원안함') {
      reservationFields.css('display', 'none');
      noAdmissionReason.css('display', 'flex');
    } else {
      reservationFields.css('display', 'none');
      noAdmissionReason.css('display', 'none');
    }
  }

  const resultSelect = document.getElementById('cs_col_19');
  if (resultSelect) {
    toggleResultFields(resultSelect.value);
    resultSelect.addEventListener('change', function () {
      toggleResultFields(this.value);
    });
  }

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
  function updateByteCountSMS() {
    const textarea = document.getElementById('message-textarea');
    const cardtextarea = document.getElementById('card-textarea');
    const display = document.getElementById('byte-count');
    if (!textarea || !cardtextarea || !display) return;

    const totalText = textarea.value + cardtextarea.value;
    const currentBytes = calculateBytes(totalText);
    const isLong = currentBytes > MAX_BYTES_SMS;
    const messageType = isLong ? '장문' : '단문';
    const maxBytes = isLong ? MAX_BYTES_MMS : MAX_BYTES_SMS;

    display.innerHTML = `${currentBytes} / ${maxBytes} byte <span class="type_${isLong ? 'mms' : 'sms'}">${messageType}</span>`;
    display.style.color = currentBytes > maxBytes ? 'red' : 'black';
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
    const sendType = currentBytes > MAX_BYTES_SMS ? 'lms' : 'sms';

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

    if (sendType === 'lms') {
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
  const openModalButton = document.getElementById('print-preview');
  const closeModalButton = document.getElementById('closeModal');
  const escapeHtml = (v) => String(v ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');

  const buildPrintHtmlFromForm = () => {
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

  const openPrintWindow = () => {
    const bodyHtml = buildPrintHtmlFromForm();
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
  };

  if (openModalButton && modal) {
    openModalButton.addEventListener('click', () => modal.style.display = 'flex');
  } else if (openModalButton) {
    // 프린트 모달 마크업이 없는 페이지에서도 일지출력 버튼 동작
    openModalButton.addEventListener('click', openPrintWindow);
  }
  if (closeModalButton && modal) closeModalButton.addEventListener('click', () => modal.style.display = 'none');
  window.addEventListener('click', (e) => { if (modal && e.target === modal) modal.style.display = 'none'; });

  const printButton = document.getElementById('printBtn');
  if (printButton) printButton.addEventListener('click', openPrintWindow);

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
                 oninput="oninputPhone(this)" onkeydown="handleBackspace(event)" maxlength="13">
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

  function syncCheckboxByBase(base) {
    if (!base) return;

    const checkbox = document.querySelector(`input[type='checkbox'][name='${base}_checkbox']`);
    if (!checkbox) return;

    const textInput = document.querySelector(`input[name='${base}_text'], input[name='${base}_details']`);
    const selectBox = document.querySelector(`select[name='${base}_select']`);

    const shouldCheck = hasValue(textInput) || hasValue(selectBox);
    checkbox.checked = shouldCheck;
  }

  // 초기값 반영 (수정 화면 진입 시에도 적용)
  document
    .querySelectorAll("input[name^='field_'], select[name^='field_']")
    .forEach(el => {
      const base = getFieldBase(el.name);
      syncCheckboxByBase(base);
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
