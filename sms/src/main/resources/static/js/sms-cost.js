(function () {
  'use strict';
  var ctx = window.__ctx || '/';
  var api = function (p) { return ctx.replace(/\/$/, '') + p; };

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }
  function num(n) { return (n == null ? 0 : n).toLocaleString('ko-KR'); }

  function loadInstCost() {
    fetch(api('/api/sms/cost'))
      .then(function (res) { return res.json(); })
      .then(function (d) {
        document.getElementById('this-month').textContent = d.thisMonthTotalText || '0원';
        document.getElementById('last-month').textContent = d.lastMonthTotalText || '0원';
        document.getElementById('grand-total').textContent = d.totalText || '0원';
        document.getElementById('unit-prices').textContent =
          'SMS ' + num(d.smsPrice) + ' · LMS ' + num(d.lmsPrice) + ' · MMS ' + num(d.mmsPrice) + ' (원/건)';
        document.getElementById('this-month-sub').textContent =
          'SMS ' + num(d.smsCount) + ' · LMS ' + num(d.lmsCount) + ' · MMS ' + num(d.mmsCount) + ' (누적 건수)';

        var rows = (d.monthlyBilling || []);
        var tb = document.getElementById('monthly-body');
        if (!rows.length) {
          tb.innerHTML = '<tr><td colspan="5" class="empty">청구 내역이 없습니다.</td></tr>';
          return;
        }
        tb.innerHTML = rows.map(function (r) {
          return '<tr>'
            + '<td>' + esc(r.month) + '</td>'
            + '<td class="num">' + num(r.sms) + '</td>'
            + '<td class="num">' + num(r.lms) + '</td>'
            + '<td class="num">' + num(r.mms) + '</td>'
            + '<td class="num">' + esc(r.totalText) + '</td>'
            + '</tr>';
        }).join('');
      })
      .catch(function () {
        document.getElementById('monthly-body').innerHTML =
          '<tr><td colspan="5" class="empty">불러오지 못했습니다.</td></tr>';
      });
  }

  function loadPlatformCost() {
    var section = document.getElementById('platform-section');
    section.style.display = '';
    fetch(api('/api/sms/cost/platform'))
      .then(function (res) {
        if (!res.ok) throw new Error('forbidden');
        return res.json();
      })
      .then(function (d) {
        document.getElementById('platform-total').textContent = d.grandTotalText || '0원';

        var mtb = document.getElementById('platform-monthly-body');
        var monthly = d.monthly || [];
        mtb.innerHTML = monthly.length ? monthly.map(function (m) {
          var count = (m.sms || 0) + (m.lms || 0) + (m.mms || 0);
          return '<tr><td>' + esc(m.month) + '</td><td class="num">' + num(count)
            + '</td><td class="num">' + esc(m.totalText) + '</td></tr>';
        }).join('') : '<tr><td colspan="3" class="empty">-</td></tr>';

        var itb = document.getElementById('platform-inst-body');
        var insts = d.institutions || [];
        itb.innerHTML = insts.length ? insts.map(function (i) {
          return '<tr><td>' + esc(i.inst) + '</td><td class="num">' + esc(i.totalText) + '</td></tr>';
        }).join('') : '<tr><td colspan="2" class="empty">-</td></tr>';
      })
      .catch(function () {
        section.style.display = 'none';
      });
  }

  loadInstCost();
  if (window.__isPlatformAdmin) {
    loadPlatformCost();
  }
})();
