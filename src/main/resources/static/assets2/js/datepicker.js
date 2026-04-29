/**
 * MediPlat Custom Date Picker (mp-dp)
 * ---------------------------------------------------------------
 * Replaces native <input type="date"> and <input type="datetime-local">
 * with a styled popover picker.
 *
 * Usage:
 *   MPDatePicker.init();                 // scans all eligible inputs
 *   MPDatePicker.mount(inputElement);    // attach to a single input
 *   MPDatePicker.observe(rootElement);   // auto-upgrade dynamically-added inputs
 *
 * Use [data-native] on an input to opt out.
 * Fires `input` + `change` events → compatible with Alpine x-model.
 */

(function (global) {
  'use strict';

  const DOW = ['일', '월', '화', '수', '목', '금', '토'];
  const MONTHS = Array.from({ length: 12 }, (_, i) => `${i + 1}월`);

  const SVG_CHEVRON_DOWN = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>';
  const SVG_ARROW_UP = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>';
  const SVG_ARROW_DOWN = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg>';
  const SVG_CALENDAR = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>';
  const SVG_CLOCK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/></svg>';

  let activePicker = null;

  function pad2(n) { return n < 10 ? '0' + n : '' + n; }
  function toISO(d) { return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()); }
  function toISODateTime(d) { return toISO(d) + 'T' + pad2(d.getHours()) + ':' + pad2(d.getMinutes()); }
  function parseISO(s) {
    if (!s) return null;
    const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(s.trim());
    if (!m) return null;
    const d = new Date(+m[1], +m[2] - 1, +m[3]);
    return isNaN(d) ? null : d;
  }
  function parseISODateTime(s) {
    if (!s) return null;
    const m = /^(\d{4})-(\d{1,2})-(\d{1,2})[T ](\d{1,2}):(\d{1,2})/.exec(s.trim());
    if (!m) return null;
    const d = new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]);
    return isNaN(d) ? null : d;
  }
  function isSameDay(a, b) { return a && b && a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate(); }

  function formatDate(d) {
    if (!d) return '';
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} (${DOW[d.getDay()]})`;
  }
  function formatDateTime(d) {
    if (!d) return '';
    const h = d.getHours();
    const ap = h < 12 ? '오전' : '오후';
    const h12 = h % 12 === 0 ? 12 : h % 12;
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${ap} ${pad2(h12)}:${pad2(d.getMinutes())}`;
  }

  function parseTime(s) {
    if (!s) return null;
    const m = /^(\d{1,2}):(\d{1,2})/.exec(s.trim());
    if (!m) return null;
    const h = +m[1], mm = +m[2];
    if (h < 0 || h > 23 || mm < 0 || mm > 59) return null;
    return { h, m: mm };
  }
  function formatTime(t) {
    if (!t) return '';
    const ap = t.h < 12 ? '오전' : '오후';
    const h12 = t.h % 12 === 0 ? 12 : t.h % 12;
    return `${ap} ${pad2(h12)}:${pad2(t.m)}`;
  }
  function toISOTime(t) { return t ? pad2(t.h) + ':' + pad2(t.m) : ''; }

  class DatePicker {
    constructor(input, opts = {}) {
      this.input = input;
      this.isTime = input.type === 'time' || opts.mode === 'time';
      this.isDateTime = input.type === 'datetime-local' || opts.mode === 'datetime';
      this.opts = opts;
      this.popEl = null;
      this.viewYear = 0;
      this.viewMonth = 0;
      this.view = 'day';
      if (this.isTime) {
        this.selectedTime = parseTime(input.value);
      } else {
        this.selected = this.isDateTime ? parseISODateTime(input.value) : parseISO(input.value);
      }

      this._build();
      this._bind();
      this._syncDisplay();
    }

    _build() {
      const orig = this.input;
      const wrap = document.createElement('div');
      wrap.className = 'mp-dp-input';

      const display = document.createElement('input');
      display.type = 'text';
      display.readOnly = true;
      display.placeholder = orig.placeholder || (this.isTime ? '시간 선택' : (this.isDateTime ? '년도-월-일 시:분' : '년도-월-일'));
      display.className = orig.className;
      display.setAttribute('autocomplete', 'off');
      if (orig.disabled) display.disabled = true;
      if (orig.id) display.dataset.forId = orig.id;

      const icon = document.createElement('span');
      icon.className = 'mp-dp-input__icon';
      icon.innerHTML = (this.isTime || this.isDateTime) ? SVG_CLOCK : SVG_CALENDAR;

      orig.parentNode.insertBefore(wrap, orig);
      wrap.appendChild(display);
      wrap.appendChild(icon);
      orig.type = 'hidden';
      wrap.appendChild(orig);

      this.wrap = wrap;
      this.display = display;
    }

    _bind() {
      this.display.addEventListener('mousedown', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.toggle();
      });
      this.display.addEventListener('keydown', (e) => this._onKey(e));
    }

    _syncDisplay() {
      if (this.isTime) {
        this.display.value = formatTime(this.selectedTime);
      } else {
        this.display.value = this.isDateTime ? formatDateTime(this.selected) : formatDate(this.selected);
      }
    }

    _onKey(e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); this.toggle(); }
      else if (e.key === 'Escape') { this.close(); }
    }

    _emit() {
      if (this.isTime) {
        this.input.value = toISOTime(this.selectedTime);
      } else if (this.selected) {
        this.input.value = this.isDateTime ? toISODateTime(this.selected) : toISO(this.selected);
      } else {
        this.input.value = '';
      }
      this._syncDisplay();
      this.input.dispatchEvent(new Event('input', { bubbles: true }));
      this.input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    setDate(d) {
      // Preserve existing time if datetime mode
      if (this.isDateTime && d && this.selected) {
        d = new Date(d.getFullYear(), d.getMonth(), d.getDate(), this.selected.getHours(), this.selected.getMinutes());
      } else if (this.isDateTime && d) {
        // Default to current time
        const now = new Date();
        d = new Date(d.getFullYear(), d.getMonth(), d.getDate(), now.getHours(), now.getMinutes());
      }
      this.selected = d;
      this._emit();
    }

    setTime(hours, minutes) {
      if (this.isTime) {
        this.selectedTime = { h: hours, m: minutes };
        this._emit();
        return;
      }
      if (!this.selected) {
        const now = new Date();
        this.selected = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hours, minutes);
      } else {
        this.selected = new Date(
          this.selected.getFullYear(),
          this.selected.getMonth(),
          this.selected.getDate(),
          hours,
          minutes
        );
      }
      this._emit();
    }

    toggle() { this.popEl ? this.close() : this.open(); }

    open() {
      if (this.popEl) return;
      if (activePicker && activePicker !== this) activePicker.close();
      activePicker = this;

      if (this.isTime) {
        // Nothing to initialize for calendar
      } else {
        const d = this.selected || new Date();
        this.viewYear = d.getFullYear();
        this.viewMonth = d.getMonth();
        this.view = 'day';
      }

      this.popEl = document.createElement('div');
      this.popEl.className = 'mp-dp-pop' + (this.isTime ? ' is-time' : (this.isDateTime ? ' is-datetime' : ''));
      document.body.appendChild(this.popEl);
      this._render();
      this._position();
      this.wrap.classList.add('is-open');

      this._onDocClick = (e) => {
        if (!this.popEl) return;
        if (this.popEl.contains(e.target) || this.wrap.contains(e.target)) return;
        this.close();
      };
      this._onScroll = () => this._position();
      this._onResize = () => this._position();
      setTimeout(() => document.addEventListener('mousedown', this._onDocClick), 0);
      window.addEventListener('scroll', this._onScroll, true);
      window.addEventListener('resize', this._onResize);
    }

    close() {
      if (!this.popEl) return;
      this.popEl.remove();
      this.popEl = null;
      this.wrap.classList.remove('is-open');
      document.removeEventListener('mousedown', this._onDocClick);
      window.removeEventListener('scroll', this._onScroll, true);
      window.removeEventListener('resize', this._onResize);
      if (activePicker === this) activePicker = null;
    }

    _position() {
      if (!this.popEl) return;
      const r = this.wrap.getBoundingClientRect();
      const pop = this.popEl;
      const popW = pop.offsetWidth || (this.isTime ? 220 : (this.isDateTime ? 460 : 272));
      pop.style.left = Math.min(window.innerWidth - popW - 8, r.left) + 'px';
      const below = r.bottom + 4;
      const popH = pop.offsetHeight || 320;
      if (below + popH > window.innerHeight - 8 && r.top - popH - 4 > 8) {
        pop.style.top = (r.top - popH - 4) + 'px';
      } else {
        pop.style.top = below + 'px';
      }
    }

    _render() {
      if (this.isTime) {
        this._renderTimeOnly();
        this._position();
        return;
      }
      if (this.view === 'day') this._renderDay();
      else if (this.view === 'month') this._renderMonth();
      else if (this.view === 'year') this._renderYear();
      this._position();
    }

    _renderTimeOnly() {
      this.popEl.innerHTML = `<div class="mp-dp-time">${this._buildTimeHTML()}</div>`;
      requestAnimationFrame(() => {
        this.popEl.querySelectorAll('.mp-dp-time__col').forEach(col => {
          const sel = col.querySelector('.is-selected');
          if (sel) sel.scrollIntoView({ block: 'center', inline: 'nearest' });
        });
      });
      this._attachTimeHandlers();
    }

    _attachTimeHandlers() {
      if (this._onPopClick) this.popEl.removeEventListener('click', this._onPopClick);
      this._onPopClick = (e) => {
        const btn = e.target.closest('button');
        if (!btn) return;
        const cur = this.selectedTime || { h: new Date().getHours(), m: new Date().getMinutes() };
        if (btn.dataset.hour != null) {
          const h12 = +btn.dataset.hour;
          const ap = cur.h < 12 ? 'am' : 'pm';
          const h24 = ap === 'am' ? (h12 === 12 ? 0 : h12) : (h12 === 12 ? 12 : h12 + 12);
          this.setTime(h24, cur.m);
          this._render();
        } else if (btn.dataset.min != null) {
          this.setTime(cur.h, +btn.dataset.min);
          this._render();
        } else if (btn.dataset.ap) {
          let h = cur.h;
          if (btn.dataset.ap === 'am' && h >= 12) h -= 12;
          if (btn.dataset.ap === 'pm' && h < 12) h += 12;
          this.setTime(h, cur.m);
          this._render();
        }
      };
      this.popEl.addEventListener('click', this._onPopClick);
    }

    _buildCalendarHTML() {
      const y = this.viewYear, m = this.viewMonth;
      const first = new Date(y, m, 1);
      const firstDow = first.getDay();
      const lastDay = new Date(y, m + 1, 0).getDate();
      const prevLast = new Date(y, m, 0).getDate();
      const today = new Date();

      const cells = [];
      for (let i = firstDow - 1; i >= 0; i--) cells.push({ d: new Date(y, m - 1, prevLast - i), muted: true });
      for (let i = 1; i <= lastDay; i++) cells.push({ d: new Date(y, m, i), muted: false });
      while (cells.length < 42) {
        const last = cells[cells.length - 1].d;
        cells.push({ d: new Date(last.getFullYear(), last.getMonth(), last.getDate() + 1), muted: true });
      }

      return `
        <div class="mp-dp-head">
          <button type="button" class="mp-dp-title" data-act="view-month">
            <span>${y}년 ${MONTHS[m]}</span>
            ${SVG_CHEVRON_DOWN}
          </button>
          <div class="mp-dp-nav">
            <button type="button" data-act="prev" aria-label="이전달">${SVG_ARROW_UP}</button>
            <button type="button" data-act="next" aria-label="다음달">${SVG_ARROW_DOWN}</button>
          </div>
        </div>
        <div class="mp-dp-dow">${DOW.map(w => `<span>${w}</span>`).join('')}</div>
        <div class="mp-dp-grid">
          ${cells.map(c => {
            const dow = c.d.getDay();
            const cls = [
              'mp-dp-cell',
              c.muted ? 'is-muted' : '',
              dow === 0 ? 'is-sun' : '',
              dow === 6 ? 'is-sat' : '',
              isSameDay(c.d, today) ? 'is-today' : '',
              isSameDay(c.d, this.selected) ? 'is-selected' : '',
            ].filter(Boolean).join(' ');
            return `<button type="button" class="${cls}" data-iso="${toISO(c.d)}">${c.d.getDate()}</button>`;
          }).join('')}
        </div>
      `;
    }

    _buildTimeHTML() {
      let selH24, selM;
      if (this.isTime) {
        const t = this.selectedTime || { h: new Date().getHours(), m: new Date().getMinutes() };
        selH24 = t.h; selM = t.m;
      } else {
        const cur = this.selected || new Date();
        selH24 = cur.getHours(); selM = cur.getMinutes();
      }
      const selAP = selH24 < 12 ? 'am' : 'pm';
      const selH12 = selH24 % 12 === 0 ? 12 : selH24 % 12;

      const hours = Array.from({ length: 12 }, (_, i) => i + 1);
      const minutes = Array.from({ length: 60 }, (_, i) => i);

      return `
        <div class="mp-dp-time__col" data-col="ap">
          <button type="button" class="mp-dp-time__cell ${selAP === 'am' ? 'is-selected' : ''}" data-ap="am">오전</button>
          <button type="button" class="mp-dp-time__cell ${selAP === 'pm' ? 'is-selected' : ''}" data-ap="pm">오후</button>
        </div>
        <div class="mp-dp-time__col" data-col="hour">
          ${hours.map(h => `<button type="button" class="mp-dp-time__cell ${h === selH12 ? 'is-selected' : ''}" data-hour="${h}">${pad2(h)}</button>`).join('')}
        </div>
        <div class="mp-dp-time__col" data-col="min">
          ${minutes.map(mm => `<button type="button" class="mp-dp-time__cell ${mm === selM ? 'is-selected' : ''}" data-min="${mm}">${pad2(mm)}</button>`).join('')}
        </div>
      `;
    }

    _renderDay() {
      const calHTML = this._buildCalendarHTML();
      const footer = `
        <div class="mp-dp-foot">
          <button type="button" class="mp-dp-foot__clear" data-act="clear">삭제</button>
          <button type="button" class="mp-dp-foot__today" data-act="today">오늘</button>
        </div>
      `;

      if (this.isDateTime) {
        this.popEl.innerHTML = `
          <div class="mp-dp-cal">
            ${calHTML}
            ${footer}
          </div>
          <div class="mp-dp-time">
            ${this._buildTimeHTML()}
          </div>
        `;
        // Scroll selected time cells into view
        requestAnimationFrame(() => {
          this.popEl.querySelectorAll('.mp-dp-time__col').forEach(col => {
            const sel = col.querySelector('.is-selected');
            if (sel) sel.scrollIntoView({ block: 'center', inline: 'nearest' });
          });
        });
      } else {
        this.popEl.innerHTML = calHTML + footer;
      }

      this._attachDayHandlers();
    }

    _attachDayHandlers() {
      // Remove previous handler if any
      if (this._onPopClick) this.popEl.removeEventListener('click', this._onPopClick);

      this._onPopClick = (e) => {
        const btn = e.target.closest('button');
        if (!btn) return;
        const act = btn.dataset.act;

        // Calendar
        if (act === 'prev') { this._shiftMonth(-1); return; }
        if (act === 'next') { this._shiftMonth(1); return; }
        if (act === 'view-month') { this.view = 'month'; this._render(); return; }
        if (act === 'today') {
          const t = new Date();
          const d = new Date(t.getFullYear(), t.getMonth(), t.getDate(),
            this.isDateTime ? t.getHours() : 0,
            this.isDateTime ? t.getMinutes() : 0);
          this.selected = d;
          this._emit();
          if (!this.isDateTime) this.close();
          else this._render();
          return;
        }
        if (act === 'clear') { this.selected = null; this._emit(); this.close(); return; }

        if (btn.dataset.iso) {
          this.setDate(parseISO(btn.dataset.iso));
          if (!this.isDateTime) this.close();
          else this._render();
          return;
        }

        // Time
        if (btn.dataset.hour != null) {
          const h12 = +btn.dataset.hour;
          const cur = this.selected || new Date();
          const ap = cur.getHours() < 12 ? 'am' : 'pm';
          const h24 = ap === 'am' ? (h12 === 12 ? 0 : h12) : (h12 === 12 ? 12 : h12 + 12);
          this.setTime(h24, cur.getMinutes());
          this._render();
          return;
        }
        if (btn.dataset.min != null) {
          const cur = this.selected || new Date();
          this.setTime(cur.getHours(), +btn.dataset.min);
          this._render();
          return;
        }
        if (btn.dataset.ap) {
          const cur = this.selected || new Date();
          let h = cur.getHours();
          if (btn.dataset.ap === 'am' && h >= 12) h -= 12;
          if (btn.dataset.ap === 'pm' && h < 12) h += 12;
          this.setTime(h, cur.getMinutes());
          this._render();
          return;
        }
      };
      this.popEl.addEventListener('click', this._onPopClick);
    }

    _renderMonth() {
      const y = this.viewYear;
      const today = new Date();
      const sel = this.selected;
      const calHTML = `
        <div class="mp-dp-head">
          <button type="button" class="mp-dp-title is-open" data-act="view-year">
            <span>${y}년</span>
            ${SVG_CHEVRON_DOWN}
          </button>
          <div class="mp-dp-nav">
            <button type="button" data-act="year-prev" aria-label="이전년">${SVG_ARROW_UP}</button>
            <button type="button" data-act="year-next" aria-label="다음년">${SVG_ARROW_DOWN}</button>
          </div>
        </div>
        <div class="mp-dp-mm">
          ${MONTHS.map((name, i) => {
            const isToday = today.getFullYear() === y && today.getMonth() === i;
            const isSel = sel && sel.getFullYear() === y && sel.getMonth() === i;
            const cls = [isToday ? 'is-today' : '', isSel ? 'is-selected' : ''].filter(Boolean).join(' ');
            return `<button type="button" class="${cls}" data-month="${i}">${name}</button>`;
          }).join('')}
        </div>
      `;
      if (this.isDateTime) {
        this.popEl.innerHTML = `<div class="mp-dp-cal">${calHTML}</div><div class="mp-dp-time">${this._buildTimeHTML()}</div>`;
      } else {
        this.popEl.innerHTML = calHTML;
      }
      this._attachMonthYearHandlers();
    }

    _renderYear() {
      const startY = Math.floor(this.viewYear / 12) * 12;
      const today = new Date();
      const sel = this.selected;
      const years = Array.from({ length: 12 }, (_, i) => startY + i);
      const calHTML = `
        <div class="mp-dp-head">
          <button type="button" class="mp-dp-title" data-act="back-month">
            <span>${startY} – ${startY + 11}</span>
            ${SVG_CHEVRON_DOWN}
          </button>
          <div class="mp-dp-nav">
            <button type="button" data-act="yrange-prev" aria-label="이전">${SVG_ARROW_UP}</button>
            <button type="button" data-act="yrange-next" aria-label="다음">${SVG_ARROW_DOWN}</button>
          </div>
        </div>
        <div class="mp-dp-mm">
          ${years.map(y => {
            const isToday = today.getFullYear() === y;
            const isSel = sel && sel.getFullYear() === y;
            const cls = [isToday ? 'is-today' : '', isSel ? 'is-selected' : ''].filter(Boolean).join(' ');
            return `<button type="button" class="${cls}" data-year="${y}">${y}</button>`;
          }).join('')}
        </div>
      `;
      if (this.isDateTime) {
        this.popEl.innerHTML = `<div class="mp-dp-cal">${calHTML}</div><div class="mp-dp-time">${this._buildTimeHTML()}</div>`;
      } else {
        this.popEl.innerHTML = calHTML;
      }
      this._attachMonthYearHandlers();
    }

    _attachMonthYearHandlers() {
      if (this._onPopClick) this.popEl.removeEventListener('click', this._onPopClick);
      this._onPopClick = (e) => {
        const btn = e.target.closest('button');
        if (!btn) return;

        // Month view
        if (btn.dataset.act === 'year-prev') { this.viewYear--; this._render(); return; }
        if (btn.dataset.act === 'year-next') { this.viewYear++; this._render(); return; }
        if (btn.dataset.act === 'view-year') { this.view = 'year'; this._render(); return; }
        if (btn.dataset.month != null) {
          this.viewMonth = +btn.dataset.month;
          this.view = 'day';
          this._render();
          return;
        }

        // Year view
        if (btn.dataset.act === 'yrange-prev') { this.viewYear -= 12; this._render(); return; }
        if (btn.dataset.act === 'yrange-next') { this.viewYear += 12; this._render(); return; }
        if (btn.dataset.act === 'back-month') { this.view = 'month'; this._render(); return; }
        if (btn.dataset.year) {
          this.viewYear = +btn.dataset.year;
          this.view = 'month';
          this._render();
          return;
        }

        // Time column (shared while in month/year view so user can still set time)
        if (btn.dataset.hour != null) {
          const h12 = +btn.dataset.hour;
          const cur = this.selected || new Date();
          const ap = cur.getHours() < 12 ? 'am' : 'pm';
          const h24 = ap === 'am' ? (h12 === 12 ? 0 : h12) : (h12 === 12 ? 12 : h12 + 12);
          this.setTime(h24, cur.getMinutes());
          this._render();
          return;
        }
        if (btn.dataset.min != null) {
          const cur = this.selected || new Date();
          this.setTime(cur.getHours(), +btn.dataset.min);
          this._render();
          return;
        }
        if (btn.dataset.ap) {
          const cur = this.selected || new Date();
          let h = cur.getHours();
          if (btn.dataset.ap === 'am' && h >= 12) h -= 12;
          if (btn.dataset.ap === 'pm' && h < 12) h += 12;
          this.setTime(h, cur.getMinutes());
          this._render();
          return;
        }
      };
      this.popEl.addEventListener('click', this._onPopClick);
    }

    _shiftMonth(delta) {
      const d = new Date(this.viewYear, this.viewMonth + delta, 1);
      this.viewYear = d.getFullYear();
      this.viewMonth = d.getMonth();
      this._render();
    }
  }

  // ===================== Public API =====================
  const MPDatePicker = {
    mount(input, opts) {
      if (!input || input.dataset.mpDp === '1') return null;
      if (input.dataset.native === 'true' || input.hasAttribute('data-native')) return null;
      input.dataset.mpDp = '1';
      return new DatePicker(input, opts || {});
    },

    init(root) {
      const scope = root || document;
      scope.querySelectorAll('input[type="date"]:not([data-native]):not([data-mp-dp]), input[type="datetime-local"]:not([data-native]):not([data-mp-dp]), input[type="time"]:not([data-native]):not([data-mp-dp])').forEach(el => {
        this.mount(el);
      });
    },

    observe(root) {
      const target = root || document.body;
      const mo = new MutationObserver(muts => {
        for (const m of muts) {
          m.addedNodes.forEach(n => {
            if (n.nodeType !== 1) return;
            const sel = 'input[type="date"]:not([data-mp-dp]), input[type="datetime-local"]:not([data-mp-dp]), input[type="time"]:not([data-mp-dp])';
            if (n.matches && (n.matches('input[type="date"]') || n.matches('input[type="datetime-local"]') || n.matches('input[type="time"]'))) this.mount(n);
            if (n.querySelectorAll) n.querySelectorAll(sel).forEach(el => this.mount(el));
          });
        }
      });
      mo.observe(target, { childList: true, subtree: true });
    },
  };

  global.MPDatePicker = MPDatePicker;

  function autoInit() {
    MPDatePicker.init();
    MPDatePicker.observe();
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', autoInit);
  } else {
    autoInit();
  }
})(window);
