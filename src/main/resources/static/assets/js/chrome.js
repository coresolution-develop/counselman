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

  const NAV = [
    { section: '상담 업무', items: [
      { id: 'reception',     label: '상담 접수',    icon: 'inbox',      href: '/counsel/intake' },
      { id: 'inpatient',     label: '입원상담',     icon: 'bed',        href: '/counsel/inpatient' },
      { id: 'inpatient-res', label: '입원예약관리', icon: 'calendar',   href: '/counsel/admission-reservation' },
      { id: 'ward',        label: '병실현황판',   icon: 'bed',        href: '/room-board' },
      { id: 'discharge',   label: '퇴원예고',     icon: 'calendar',   href: '/room-board/discharge-notice' },
      { id: 'list',        label: '상담리스트',   icon: 'list',       href: '/counsel/list' },
      { id: 'notice',      label: '공지사항',     icon: 'megaphone',  href: '/notices' },
      { id: 'stats',       label: '상담통계',     icon: 'chart',      href: '#' },
      { id: 'records',     label: '상담일지관리', icon: 'sliders',    href: '/counsel/log-settings' },
    ]},
    { section: '커뮤니케이션', items: [
      { id: 'message',     label: '문자관리',     icon: 'chat',       href: '/message' },
    ]},
    { section: '시스템', items: [
      { id: 'admin',       label: '관리자',       icon: 'shield',     href: '/roles', badge: '3' },
    ]},
  ];

  const icon = (id) => `<svg><use href="#i-${id}"/></svg>`;

  function renderSidebar() {
    const sections = NAV.map(sec => {
      const items = sec.items.map(it => {
        const active = it.id === CURRENT ? ' is-active' : '';
        const badge = it.badge ? `<span class="nav-item__badge">${it.badge}</span>` : '';
        const main = `<a class="nav-item${active}" href="${path(it.href)}">
          <span class="nav-item__icon">${icon(it.icon)}</span>
          <span class="nav-item__label">${it.label}</span>
          ${badge}
        </a>`;
        const subs = (it.sub || []).map(s => {
          const sactive = s.id === CURRENT ? ' is-active' : '';
          return `<a class="nav-item nav-item--sub${sactive}" href="${path(s.href)}"><span class="nav-item__label">${s.label}</span></a>`;
        }).join('');
        return main + subs;
      }).join('');
      return `<div class="nav-section">
        <div class="nav-section__label">${sec.section}</div>
        ${items}
      </div>`;
    }).join('');

    return `<aside class="sidebar" aria-label="메인 내비게이션">
      <div class="sidebar__brand">
        <div class="sidebar__logo">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M12 4v16M4 12h16"/></svg>
        </div>
        <div class="sidebar__title">CounselMan</div>
      </div>
      <nav class="sidebar__nav">${sections}</nav>
      <div class="sidebar__footer">
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

  function renderCounselManSidebar() {
    const sections = NAV.map(sec => {
      const items = sec.items.map(it => {
        const active = it.id === CURRENT ? ' is-active' : '';
        const itemIcon = icon(it.icon);
        return `<a class="nav-item${active}" href="${path(it.href)}" title="${it.label}" aria-label="${it.label}">
          <span class="nav-item__icon">${itemIcon}</span>
          <span class="nav-item__label">${it.label}</span>
        </a>`;
      }).join('');
      return `<div class="nav-section">
        <div class="nav-section__label">${sec.section}</div>
        <div class="nav-section__icons">${items}</div>
      </div>`;
    }).join('');

    return `<aside class="sidebar sidebar--counselman" aria-label="메인 내비게이션">
      <nav class="sidebar__nav">${sections}</nav>
      <div class="sidebar__footer">
        <div class="sidebar__footer-name">CounselMan</div>
        <a class="sidebar__logout" href="${path('/logout')}">
          ${icon('logout')}
          <span class="sidebar__logout-text">로그아웃</span>
        </a>
        <div class="sidebar__footer-copy">Copyright by Coresolution</div>
      </div>
    </aside>`;
  }

  function renderCounselManHeader() {
    return `<header class="header header--counselman">
      <div class="header__brand">
        <button class="header__toggle" @click="$store.app.toggleSidebar()" aria-label="사이드바 토글">${icon('menu')}</button>
        <strong class="header__product">CounselMan</strong>
      </div>
      <div class="header__spacer"></div>
      <button class="header__icon-btn" aria-label="알림">${icon('bell')}</button>
      <button class="header__avatar-btn js-user-avatar" aria-label="사용자">?</button>
      <button class="header__user">
        <div class="header__user-meta">
          <span class="header__user-name"></span>
          <span class="header__user-org"></span>
        </div>
      </button>
    </header>`;
  }

  function patchUserInfo(me) {
    if (!me || !me.id) return;
    const displayName = me.name && me.name.trim() ? me.name : me.id;
    const initial = displayName.charAt(0).toUpperCase();
    const orgLine = [me.instname, me.role, me.id].filter(Boolean).join(' · ');

    document.querySelectorAll('.header__user-name').forEach(el => { el.textContent = displayName; });
    document.querySelectorAll('.header__user-org').forEach(el => { el.textContent = orgLine; });
    document.querySelectorAll('.js-user-avatar').forEach(el => { el.textContent = initial; });
  }

  window.MPChrome = {
    mount(crumb) {
      const sbSlot = document.getElementById('slot-sidebar');
      const hdSlot = document.getElementById('slot-header');
      if (sbSlot) sbSlot.outerHTML = renderSidebar();
      if (hdSlot) hdSlot.outerHTML = renderHeader(crumb || { current: 'Dashboard' });

      fetch(CTX + '/api/me')
        .then(r => r.ok ? r.json() : {})
        .then(patchUserInfo)
        .catch(() => {});
    }
  };
})();
