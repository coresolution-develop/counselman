(function () {
  'use strict';
  var ctx = window.__ctx || '/';
  var api = function (p) { return ctx.replace(/\/$/, '') + p; };

  var state = { type: 'sms' };

  var els = {
    toggle: document.getElementById('type-toggle'),
    from: document.getElementById('from'),
    to: document.getElementById('to'),
    subjectRow: document.getElementById('subject-row'),
    subject: document.getElementById('subject'),
    message: document.getElementById('message'),
    charLen: document.getElementById('char-len'),
    msgKind: document.getElementById('msg-kind'),
    reserve: document.getElementById('reserve'),
    sendBtn: document.getElementById('send-btn'),
    msg: document.getElementById('send-message'),
    pvTo: document.getElementById('pv-to'),
    pvSubject: document.getElementById('pv-subject'),
    pvMessage: document.getElementById('pv-message')
  };

  // Korean = 2 bytes (rough KS X 1001 estimate used by SMS gateways).
  function byteLen(str) {
    var n = 0;
    for (var i = 0; i < str.length; i++) {
      n += str.charCodeAt(i) > 127 ? 2 : 1;
    }
    return n;
  }

  function setType(type) {
    state.type = type;
    Array.prototype.forEach.call(els.toggle.querySelectorAll('button'), function (b) {
      b.classList.toggle('active', b.dataset.type === type);
    });
    els.subjectRow.style.display = (type === 'lms' || type === 'mms') ? 'flex' : 'none';
    els.msgKind.textContent = type.toUpperCase();
  }

  function refreshPreview() {
    els.charLen.textContent = byteLen(els.message.value);
    els.pvTo.textContent = els.to.value || '-';
    els.pvSubject.textContent = els.subject.value || '-';
    els.pvMessage.textContent = els.message.value || '메시지 내용이 여기에 표시됩니다.';
  }

  function showMessage(text, ok) {
    els.msg.textContent = text;
    els.msg.className = 'form-message ' + (ok ? 'ok' : 'err');
  }

  function reserveEpochSeconds() {
    if (!els.reserve.value) return '';
    var ms = new Date(els.reserve.value).getTime();
    if (isNaN(ms)) return '';
    return String(Math.floor(ms / 1000));
  }

  function send() {
    var to = (els.to.value || '').trim();
    var message = (els.message.value || '').trim();
    if (!to) { showMessage('수신번호를 입력해 주세요.', false); return; }
    if (!message) { showMessage('내용을 입력해 주세요.', false); return; }

    var body = {
      type: state.type,
      from: (els.from.value || '').trim(),
      to: to,
      subject: (els.subject.value || '').trim(),
      message: message,
      sendtime: reserveEpochSeconds()
    };

    els.sendBtn.disabled = true;
    showMessage('전송 중…', true);
    fetch(api('/api/sms/send'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (res) {
      return res.json().then(function (data) { return { ok: res.ok, data: data }; });
    }).then(function (r) {
      if (r.ok) {
        showMessage('전송되었습니다.', true);
        els.message.value = '';
        refreshPreview();
      } else {
        showMessage((r.data && r.data.message) || '전송에 실패했습니다.', false);
      }
    }).catch(function () {
      showMessage('전송 중 오류가 발생했습니다.', false);
    }).finally(function () {
      els.sendBtn.disabled = false;
    });
  }

  els.toggle.addEventListener('click', function (e) {
    var btn = e.target.closest('button[data-type]');
    if (btn) { setType(btn.dataset.type); refreshPreview(); }
  });
  els.message.addEventListener('input', refreshPreview);
  els.to.addEventListener('input', refreshPreview);
  els.subject.addEventListener('input', refreshPreview);
  els.sendBtn.addEventListener('click', send);

  setType('sms');
  refreshPreview();
})();
