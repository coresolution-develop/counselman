/* ============================================
   Shared chrome (sidebar + header) — injects
   consistent layout into every page.
   ============================================ */

(function () {
  const CURRENT = document.currentScript?.dataset?.page || 'dashboard';
  const CTX = (document.currentScript?.dataset?.ctx || '/').replace(/\/$/, '');
  const path = (url) => {
    if (!url || url === '#' || /^[a-z][a-z0-9+.-]*:/i.test(url)) return url;
    return `${CTX}${url.startsWith('/') ? url : `/${url}`}`;
  };

  // ── 기본 메뉴 정의 ──────────────────────────────────────────────
  const NAV_DEFAULT = [
    { key: 's:상담 업무', section: '상담 업무', items: [
      { id: 'reception',     label: '상담 접수',    icon: 'inbox',      href: '/counsel/intake' },
      { id: 'inpatient',     label: '입원상담',     icon: 'bed',        href: '/counsel/inpatient' },
      { id: 'inpatient-res', label: '입원예약관리', icon: 'calendar',   href: '/counsel/admission-reservation' },
      { id: 'ward',          label: '병실현황판',   icon: 'bed',        href: '/room-board' },
      { id: 'discharge',     label: '퇴원예고',     icon: 'calendar',   href: '/room-board/discharge-notice' },
      { id: 'list',          label: '상담리스트',   icon: 'list',       href: '/counsel/list' },
      { id: 'notice',        label: '공지사항',     icon: 'megaphone',  href: '/notices' },
      { id: 'stats',         label: '상담통계',     icon: 'chart',      href: '#' },
    ]},
    { key: 's:커뮤니케이션', section: '커뮤니케이션', items: [
      { id: 'message',       label: '문자관리',     icon: 'chat',       href: '/message' },
    ]},
    { key: 's:시스템', section: '시스템', items: [
      { id: 'admin',         label: '관리자',       icon: 'shield',     href: '/roles', badge: '3' },
      { id: 'records',       label: '상담일지관리', icon: 'sliders',    href: '/admin/counsel/log-settings' },
    ]},
  ];

  // 작업용 복사본 — applyNavOrder()가 이를 정렬
  let NAV = deepClone(NAV_DEFAULT);

  function deepClone(obj) { return JSON.parse(JSON.stringify(obj)); }

  // ── 순서 적용 ────────────────────────────────────────────────────
  function applyNavOrder(orderRows) {
    if (!orderRows || orderRows.length === 0) return;
    // nav_key → sort_order 맵
    const orderMap = {};
    orderRows.forEach(r => { orderMap[r.nav_key] = Number(r.sort_order); });

    NAV = deepClone(NAV_DEFAULT);

    // 섹션 내 아이템 정렬
    NAV.forEach(sec => {
      sec.items.sort((a, b) => {
        const oa = orderMap['i:' + a.id] ?? 9999;
        const ob = orderMap['i:' + b.id] ?? 9999;
        return oa - ob;
      });
    });

    // 섹션 간 정렬
    NAV.sort((a, b) => {
      const oa = orderMap[a.key] ?? 9999;
      const ob = orderMap[b.key] ?? 9999;
      return oa - ob;
    });
  }

  // ── 아이콘 헬퍼 ─────────────────────────────────────────────────
  const icon = (id) => `<svg><use href="#i-${id}"/></svg>`;

  // ── 사이드바 렌더 ────────────────────────────────────────────────
  function renderSidebar(editMode) {
    const sections = NAV.map(sec => {
      const items = sec.items.map(it => {
        const active = it.id === CURRENT ? ' is-active' : '';
        const badge  = it.badge ? `<span class="nav-item__badge">${it.badge}</span>` : '';
        const handle = editMode
          ? `<span class="nav-drag-handle" title="드래그하여 순서 변경">⠿</span>`
          : '';
        const draggableAttr = editMode ? ' draggable="true"' : '';
        const main = `<a class="nav-item${active}"
              href="${editMode ? '#' : path(it.href)}"
              data-item-key="${it.id}"${draggableAttr}>
            ${handle}
            <span class="nav-item__icon">${icon(it.icon)}</span>
            <span class="nav-item__label">${it.label}</span>
            ${badge}
          </a>`;
        const subs = (it.sub || []).map(s => {
          const sactive = s.id === CURRENT ? ' is-active' : '';
          return `<a class="nav-item nav-item--sub${sactive}" href="${path(s.href)}">
            <span class="nav-item__label">${s.label}</span></a>`;
        }).join('');
        return main + subs;
      }).join('');

      const secDraggable = editMode ? ' draggable="true"' : '';
      return `<div class="nav-section${editMode ? ' nav-section--editable' : ''}"
                   data-section-key="${sec.key}">
        <div class="nav-section__label"${secDraggable}>${sec.section}${editMode ? ' <span class="nav-drag-handle-sec">⠿</span>' : ''}</div>
        <div class="nav-section__items">${items}</div>
      </div>`;
    }).join('');

    const editBtn = editMode
      ? `<button class="sidebar__edit-btn sidebar__edit-btn--active" id="navEditBtn">완료</button>`
      : `<button class="sidebar__edit-btn" id="navEditBtn">순서 편집</button>`;

    return `<aside class="sidebar${editMode ? ' sidebar--edit-mode' : ''}" aria-label="메인 내비게이션">
      <div class="sidebar__brand">
        <div class="sidebar__logo">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M12 4v16M4 12h16"/></svg>
        </div>
        <div class="sidebar__title">CounselMan</div>
      </div>
      <nav class="sidebar__nav" id="navRoot">${sections}</nav>
      <div class="sidebar__footer">
        ${editBtn}
        <div class="sidebar__footer-meta">
          <span class="sidebar__footer-name">MediPlat</span>
          <span class="sidebar__footer-copy">© Coresolution</span>
        </div>
        <a class="sidebar__logout" href="${path('/logout')}">
          ${icon('logout')}
          <span class="sidebar__logout-text">로그아웃</span>
        </a>
      </div>
    </aside>`;
  }

  // ── 헤더 렌더 ────────────────────────────────────────────────────
  function renderHeader(crumb) {
    return `<header class="header">
      <button class="header__toggle" @click="$store.app.toggleSidebar()" aria-label="사이드바 토글">${icon('menu')}</button>
      <div class="header__breadcrumb">
        <span>${crumb.parent || 'MediPlat'}</span>
        ${icon('chevron-right')}
        <strong>${crumb.current}</strong>
      </div>
      <div class="header__spacer"></div>
      <div class="header__search">
        ${icon('search')}
        <input type="text" placeholder="검색" />
        <kbd>⌘K</kbd>
      </div>
      <button class="header__icon-btn" aria-label="알림">${icon('bell')}<span class="dot"></span></button>
      <button class="header__icon-btn" aria-label="도움말">${icon('help')}</button>
      <button class="header__user">
        <div class="header__avatar js-user-avatar">?</div>
        <div class="header__user-meta">
          <span class="header__user-name"></span>
          <span class="header__user-org"></span>
        </div>
      </button>
    </header>`;
  }

  // ── 유저 정보 패치 ───────────────────────────────────────────────
  function patchUserInfo(me) {
    if (!me || !me.id) return;
    const displayName = me.name && me.name.trim() ? me.name : me.id;
    const initial = displayName.charAt(0).toUpperCase();
    const orgLine = [me.instname, me.role, me.id].filter(Boolean).join(' · ');
    document.querySelectorAll('.header__user-name').forEach(el => { el.textContent = displayName; });
    document.querySelectorAll('.header__user-org').forEach(el => { el.textContent = orgLine; });
    document.querySelectorAll('.js-user-avatar').forEach(el => { el.textContent = initial; });
  }

  // ── 드래그 앤 드롭 ───────────────────────────────────────────────
  let dragging = null;     // { type: 'item'|'section', key, el }
  let editMode = false;

  function rebuildSidebar() {
    const old = document.querySelector('.sidebar');
    if (old) old.outerHTML = renderSidebar(editMode);
    attachSidebarListeners();
  }

  function attachSidebarListeners() {
    const editBtn = document.getElementById('navEditBtn');
    if (editBtn) {
      editBtn.addEventListener('click', () => {
        editMode = !editMode;
        if (!editMode) saveNavOrder();
        rebuildSidebar();
      });
    }
    if (editMode) attachDragListeners();
  }

  function attachDragListeners() {
    const navRoot = document.getElementById('navRoot');
    if (!navRoot) return;

    // 아이템 드래그
    navRoot.querySelectorAll('[data-item-key]').forEach(el => {
      el.addEventListener('dragstart', e => {
        dragging = { type: 'item', key: el.dataset.itemKey, el };
        e.dataTransfer.effectAllowed = 'move';
        setTimeout(() => el.classList.add('is-dragging'), 0);
      });
      el.addEventListener('dragend', () => {
        el.classList.remove('is-dragging');
        navRoot.querySelectorAll('.drag-over').forEach(x => x.classList.remove('drag-over'));
        dragging = null;
        syncNavFromDom();
      });
      el.addEventListener('dragover', e => {
        if (!dragging || dragging.type !== 'item') return;
        e.preventDefault();
        el.classList.add('drag-over');
      });
      el.addEventListener('dragleave', () => el.classList.remove('drag-over'));
      el.addEventListener('drop', e => {
        e.preventDefault();
        el.classList.remove('drag-over');
        if (!dragging || dragging.type !== 'item' || dragging.key === el.dataset.itemKey) return;
        // 같은 섹션 내에서만 이동
        const draggedEl = navRoot.querySelector(`[data-item-key="${dragging.key}"]`);
        if (!draggedEl) return;
        const fromSec = draggedEl.closest('.nav-section');
        const toSec   = el.closest('.nav-section');
        if (fromSec === toSec) el.insertAdjacentElement('beforebegin', draggedEl);
      });
    });

    // 섹션 드래그 (섹션 헤더)
    navRoot.querySelectorAll('[data-section-key]').forEach(secEl => {
      const label = secEl.querySelector('.nav-section__label');
      if (!label) return;
      label.addEventListener('dragstart', e => {
        dragging = { type: 'section', key: secEl.dataset.sectionKey, el: secEl };
        e.dataTransfer.effectAllowed = 'move';
        setTimeout(() => secEl.classList.add('is-dragging'), 0);
      });
      label.addEventListener('dragend', () => {
        secEl.classList.remove('is-dragging');
        navRoot.querySelectorAll('.drag-over-sec').forEach(x => x.classList.remove('drag-over-sec'));
        dragging = null;
        syncNavFromDom();
      });
      secEl.addEventListener('dragover', e => {
        if (!dragging || dragging.type !== 'section') return;
        e.preventDefault();
        secEl.classList.add('drag-over-sec');
      });
      secEl.addEventListener('dragleave', () => secEl.classList.remove('drag-over-sec'));
      secEl.addEventListener('drop', e => {
        e.preventDefault();
        secEl.classList.remove('drag-over-sec');
        if (!dragging || dragging.type !== 'section' || dragging.key === secEl.dataset.sectionKey) return;
        const draggedSec = navRoot.querySelector(`[data-section-key="${dragging.key}"]`);
        if (draggedSec) secEl.insertAdjacentElement('beforebegin', draggedSec);
      });
    });
  }

  // DOM에서 현재 순서를 읽어 NAV에 반영
  function syncNavFromDom() {
    const navRoot = document.getElementById('navRoot');
    if (!navRoot) return;
    const newNav = [];
    navRoot.querySelectorAll('.nav-section').forEach(secEl => {
      const secKey = secEl.dataset.sectionKey;
      const orig = NAV_DEFAULT.find(s => s.key === secKey);
      if (!orig) return;
      const items = [];
      secEl.querySelectorAll('[data-item-key]').forEach(itemEl => {
        const it = orig.items.find(i => i.id === itemEl.dataset.itemKey);
        if (it) items.push(it);
      });
      newNav.push({ ...orig, items });
    });
    NAV = newNav;
  }

  // DB에 저장
  async function saveNavOrder() {
    const csrf       = document.querySelector('meta[name="_csrf"]')?.content || '';
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

    const payload = [];
    let secOrder  = 10;
    let itemOrder = 10;

    NAV.forEach(sec => {
      payload.push({ nav_key: sec.key, sort_order: secOrder });
      secOrder += 10;
      sec.items.forEach(it => {
        payload.push({ nav_key: 'i:' + it.id, sort_order: itemOrder });
        itemOrder += 10;
      });
    });

    const btn = document.getElementById('navEditBtn');
    if (btn) { btn.disabled = true; btn.textContent = '저장 중...'; }

    try {
      await fetch(CTX + '/api/nav-order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', [csrfHeader]: csrf },
        body: JSON.stringify(payload),
        credentials: 'same-origin'
      });
    } catch (e) {
      console.error('nav-order 저장 실패', e);
    }

    if (btn) { btn.disabled = false; btn.textContent = '완료'; }
  }

  // ── mount ────────────────────────────────────────────────────────
  window.MPChrome = {
    async mount(crumb) {
      const hdSlot = document.getElementById('slot-header');
      if (hdSlot) hdSlot.outerHTML = renderHeader(crumb || { current: 'Dashboard' });

      // nav 순서 + 유저 정보 병렬 로드
      const [orderRows, me] = await Promise.all([
        fetch(CTX + '/api/nav-order').then(r => r.ok ? r.json() : []).catch(() => []),
        fetch(CTX + '/api/me').then(r => r.ok ? r.json() : {}).catch(() => ({})),
      ]);

      applyNavOrder(orderRows);

      const sbSlot = document.getElementById('slot-sidebar');
      if (sbSlot) sbSlot.outerHTML = renderSidebar(false);

      attachSidebarListeners();
      patchUserInfo(me);
    }
  };
})();
