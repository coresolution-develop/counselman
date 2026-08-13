/* 링크 허브 — 런처 동작. 엔드포인트·필드명은 그대로 두고 UI만 바뀐다. */
(() => {
  'use strict';

  const csrfToken = () => document.querySelector('meta[name="_csrf"]')?.content || '';
  const root = document.documentElement;
  const store = {
    get(key) { try { return localStorage.getItem(key); } catch (_) { return null; } },
    set(key, value) { try { localStorage.setItem(key, value); } catch (_) { /* 저장 실패는 무시 */ } },
  };

  // 재정렬 컨테이너별 ▲▼ 활성화 갱신 함수. 행이 동적으로 추가/제거될 때 다시 호출한다.
  const reorderRefreshers = new Map();

  // ── 테마 토글 ────────────────────────────────────────────────────────
  const themeBtn = document.querySelector('[data-theme-toggle]');
  if (themeBtn) {
    // 라벨은 "지금 상태"가 아니라 "누르면 갈 모드"를 가리킨다.
    const syncLabel = () => {
      themeBtn.textContent = root.getAttribute('data-theme') === 'dark' ? '라이트' : '다크';
    };
    syncLabel();
    themeBtn.addEventListener('click', () => {
      const nextDark = root.getAttribute('data-theme') !== 'dark';
      if (nextDark) root.setAttribute('data-theme', 'dark');
      else root.removeAttribute('data-theme');
      store.set('hubTheme', nextDark ? 'dark' : 'light');
      syncLabel();
    });
  }

  // ── 밀도 토글 (행 / 그리드) ──────────────────────────────────────────
  const densityBtns = [...document.querySelectorAll('[data-density]')];
  if (densityBtns.length) {
    const applyDensity = (value) => {
      if (value === 'grid') root.setAttribute('data-density', 'grid');
      else root.removeAttribute('data-density');
      densityBtns.forEach((btn) => {
        btn.classList.toggle('hub-density__btn--on', btn.dataset.density === value);
      });
    };
    applyDensity(store.get('hubDensity') === 'grid' ? 'grid' : 'list');
    densityBtns.forEach((btn) => {
      btn.addEventListener('click', () => {
        applyDensity(btn.dataset.density);
        store.set('hubDensity', btn.dataset.density);
      });
    });
  }

  // ── 필터 칩 (전체 / 운영 / 개발 / 개인) ───────────────────────────────
  const chips = [...document.querySelectorAll('[data-filter]')];
  if (chips.length) {
    const items = [...document.querySelectorAll('[data-filter-item]')];
    const groups = [...document.querySelectorAll('[data-filter-group]')];
    const noResult = document.getElementById('hubNoResult');

    const matches = (item, filter) => {
      if (filter === 'all') return true;
      if (filter === 'mine') return item.hasAttribute('data-mine');
      // "개발" 칩은 DEMO까지 함께 걸러준다 — 둘 다 운영이 아닌 서버라는 점이 핵심이다.
      if (filter === 'dev') return item.dataset.env === 'dev' || item.dataset.env === 'demo';
      return item.dataset.env === filter;
    };

    const apply = (filter) => {
      let visible = 0;
      items.forEach((item) => {
        const hit = matches(item, filter);
        item.classList.toggle('hub-hide', !hit);
        if (hit) visible += 1;
      });
      // 남은 행이 없는 그룹은 제목만 떠 있지 않도록 통째로 감춘다.
      groups.forEach((group) => {
        const hits = group.querySelectorAll('[data-filter-item]:not(.hub-hide)').length;
        group.classList.toggle('hub-hide', hits === 0);
      });
      if (noResult) noResult.hidden = visible !== 0;
      chips.forEach((chip) => chip.classList.toggle('hub-chip--on', chip.dataset.filter === filter));
    };

    chips.forEach((chip) => chip.addEventListener('click', () => apply(chip.dataset.filter)));
  }

  // ── 커맨드 팔레트 ────────────────────────────────────────────────────
  const palette = document.getElementById('hubPalette');
  const paletteInput = document.getElementById('paletteInput');
  const paletteResults = document.getElementById('paletteResults');
  const paletteCount = document.querySelector('[data-palette-count]');

  if (palette && paletteInput && paletteResults) {
    // 본문에 이미 렌더된 행에서 검색 인덱스를 만든다 — 별도 JSON을 내려받지 않는다.
    const index = [...document.querySelectorAll('[data-palette-item]')].map((el) => ({
      name: el.dataset.name || '',
      host: el.dataset.host || '',
      cat: el.dataset.cat || '기타',
      env: el.dataset.env || 'prod',
      envLabel: el.dataset.envlabel || '운영',
      url: el.dataset.url || '',
      open: el.dataset.open || '',
      color: el.dataset.color || '',
      colorDark: el.dataset.colordark || '',
      short: el.dataset.short || '',
      key: `${el.dataset.name || ''} ${el.dataset.cat || ''} ${el.dataset.host || ''}`.toLowerCase(),
    }));

    let results = [];
    let active = 0;
    let lastFocused = null;

    const rowEl = (item, i) => {
      const row = document.createElement('div');
      row.className = `lh-row lh-row--compact env-${item.env}` + (i === active ? ' lh-row--sel' : '');
      row.dataset.idx = String(i);

      const env = document.createElement('span');
      env.className = 'lh-env';
      env.textContent = item.envLabel;

      const badge = document.createElement('span');
      badge.className = 'lh-badge';
      badge.style.setProperty('--c', item.color);
      badge.style.setProperty('--c-dark', item.colorDark);
      badge.textContent = item.short;

      const main = document.createElement('span');
      main.className = 'lh-main';
      const name = document.createElement('span');
      name.className = 'lh-name';
      name.textContent = item.name;
      const host = document.createElement('span');
      host.className = 'lh-host';
      host.textContent = item.host;
      main.append(name, host);

      const tail = document.createElement('span');
      if (item.env === 'dev' || item.env === 'demo') {
        tail.className = 'lh-warn';
        tail.textContent = item.env === 'dev' ? '개발서버입니다' : 'DEMO 환경입니다';
      } else {
        tail.className = 'lh-meta';
        tail.textContent = i === active ? '↵' : '';
      }

      row.append(env, badge, main, tail);
      row.addEventListener('click', () => open(i, false));
      return row;
    };

    const render = () => {
      paletteResults.textContent = '';
      if (paletteCount) paletteCount.textContent = `${results.length}개 결과`;

      if (!results.length) {
        const empty = document.createElement('div');
        empty.className = 'hub-empty';
        const text = document.createElement('div');
        text.className = 'hub-empty__text';
        text.textContent = paletteInput.value.trim()
          ? `'${paletteInput.value.trim()}'와 일치하는 링크가 없습니다`
          : '검색어를 입력하세요';
        empty.appendChild(text);
        paletteResults.appendChild(empty);
        return;
      }

      // 분류별로 묶어 라벨을 붙인다(결과 순서는 유지).
      let currentCat = null;
      results.forEach((item, i) => {
        if (item.cat !== currentCat) {
          currentCat = item.cat;
          const label = document.createElement('div');
          label.className = 'lh-palette__group';
          label.textContent = currentCat;
          paletteResults.appendChild(label);
        }
        paletteResults.appendChild(rowEl(item, i));
      });
      paletteResults.querySelector('.lh-row--sel')?.scrollIntoView({ block: 'nearest' });
    };

    const search = () => {
      const q = paletteInput.value.trim().toLowerCase();
      results = q ? index.filter((item) => item.key.includes(q)) : index.slice(0, 30);
      active = 0;
      render();
    };

    const open = (i, newTab) => {
      const item = results[i];
      if (!item || !item.open) return;
      const win = window.open(item.open, '_blank');
      if (win) { try { win.opener = null; } catch (_) { /* 일부 브라우저 무시 */ } }
      if (!newTab) close();
    };

    const show = () => {
      lastFocused = document.activeElement;
      palette.hidden = false;
      paletteInput.value = '';
      search();
      paletteInput.focus();
    };

    const close = () => {
      palette.hidden = true;
      // 팔레트를 연 요소로 포커스를 되돌린다 — 키보드 사용자가 위치를 잃지 않게.
      if (lastFocused instanceof HTMLElement) lastFocused.focus();
    };

    document.querySelectorAll('[data-palette-open]').forEach((el) => el.addEventListener('click', show));
    palette.addEventListener('click', (e) => { if (e.target === palette) close(); });
    paletteInput.addEventListener('input', search);

    document.addEventListener('keydown', (e) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        if (palette.hidden) show(); else close();
      }
    });

    paletteInput.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') { e.preventDefault(); close(); return; }
      if (!results.length) return;
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        active = active < results.length - 1 ? active + 1 : 0;
        render();
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        active = active > 0 ? active - 1 : results.length - 1;
        render();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        open(active, e.metaKey || e.ctrlKey);
      }
    });
  }

  // ── ★ 즐겨찾기 토글 (AJAX, 새로고침 없이 즐겨찾기 섹션까지 즉시 반영) ────
  const favSection = document.getElementById('favSection');
  const favGrid = favSection?.querySelector('[data-fav-grid]');
  const favCountEl = favSection?.querySelector('[data-fav-count]');

  const syncFavSection = () => {
    if (!favSection || !favGrid) return;
    const count = favGrid.querySelectorAll('[data-fav-row]').length;
    if (favCountEl) favCountEl.textContent = String(count);
    favSection.hidden = count === 0;
    reorderRefreshers.get(favGrid)?.();
  };

  // 공용 목록 행의 data-* 만으로 즐겨찾기 행을 만든다(문자열은 textContent로만 넣어 XSS를 막는다).
  const buildFavRow = (source, linkId, toggleUrl, loginUrl) => {
    const d = source.dataset;
    const row = document.createElement('div');
    row.className = `lh-row env-${d.env || 'prod'}`;
    row.setAttribute('data-fav-row', '');
    row.setAttribute('data-reorder-id', linkId);
    row.dataset.name = d.name || '';
    row.dataset.host = d.host || '';
    row.dataset.env = d.env || 'prod';

    const link = document.createElement('a');
    link.className = 'lh-row__link';
    link.href = d.open || d.url || '#';
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.title = d.url || '';
    link.setAttribute('aria-label', `${d.name || ''} 열기`);

    const env = document.createElement('span');
    env.className = 'lh-env';
    env.textContent = d.envlabel || '운영';

    const badge = document.createElement('span');
    badge.className = 'lh-badge';
    badge.style.setProperty('--c', d.color || '');
    badge.style.setProperty('--c-dark', d.colordark || '');
    badge.textContent = d.short || '';

    const main = document.createElement('span');
    main.className = 'lh-main';
    const name = document.createElement('span');
    name.className = 'lh-name';
    name.textContent = d.name || '';
    const host = document.createElement('span');
    host.className = 'lh-host';
    // 목록과 같게 설정한 URL을 그대로 둘째 줄에 보여준다.
    host.textContent = d.url || d.host || '';
    main.append(name, host);

    const act = document.createElement('span');
    act.className = 'lh-act';
    act.innerHTML = '<button type="button" data-move="up" title="위로 이동" aria-label="위로 이동">▲</button>'
      + '<button type="button" data-move="down" title="아래로 이동" aria-label="아래로 이동">▼</button>';
    const star = document.createElement('button');
    star.type = 'button';
    star.className = 'lh-fav lh-fav--on';
    star.setAttribute('aria-pressed', 'true');
    star.title = '즐겨찾기 해제';
    star.dataset.linkId = linkId;
    star.dataset.toggleUrl = toggleUrl;
    if (loginUrl) star.dataset.loginUrl = loginUrl;
    star.setAttribute('data-remove-on-unfav', '');
    star.textContent = '★';
    act.appendChild(star);

    row.append(link, env, badge, main, act);
    return row;
  };

  const removeFavRow = (linkId) => {
    const row = favGrid?.querySelector(`[data-fav-row][data-reorder-id="${linkId}"]`);
    if (!row) return;
    row.style.transition = 'opacity .2s';
    row.style.opacity = '0';
    setTimeout(() => { row.remove(); syncFavSection(); }, 200);
  };

  // 이벤트 위임 — 동적으로 추가된 즐겨찾기 행의 ★도 그대로 동작한다.
  document.addEventListener('click', async (event) => {
    const btn = event.target.closest('.lh-fav');
    if (!btn || btn.disabled) return;
    event.preventDefault();
    event.stopPropagation();
    const linkId = btn.dataset.linkId;
    const url = btn.dataset.toggleUrl;
    if (!linkId || !url) return;
    const sourceRow = btn.closest('.lh-row');
    btn.disabled = true;
    try {
      const params = new URLSearchParams({ linkId, _csrf: csrfToken() });
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
        body: params,
      });
      if (res.status === 401) { window.location.href = btn.dataset.loginUrl || '/links'; return; }
      const data = await res.json().catch(() => ({}));
      if (res.ok && typeof data.favorited === 'boolean') {
        // 같은 링크의 ★를 전부 동기화한다(공용 목록 ↔ 즐겨찾기 섹션).
        document.querySelectorAll(`.lh-fav[data-link-id="${linkId}"]`).forEach((star) => {
          star.classList.toggle('lh-fav--on', data.favorited);
          star.setAttribute('aria-pressed', String(data.favorited));
        });
        btn.classList.remove('lh-fav--pulse');
        void btn.offsetWidth; // reflow to restart animation
        btn.classList.add('lh-fav--pulse');

        if (data.favorited) {
          // 즐겨찾기 섹션 밖(공용 목록)에서 눌렀을 때만 행을 새로 넣는다.
          if (favGrid && sourceRow && !btn.closest('[data-fav-row]')
              && !favGrid.querySelector(`[data-fav-row][data-reorder-id="${linkId}"]`)) {
            favGrid.appendChild(buildFavRow(sourceRow, linkId, url, btn.dataset.loginUrl));
            syncFavSection();
          }
        } else {
          removeFavRow(linkId);
        }
      } else {
        alert(data.error || '즐겨찾기 처리에 실패했습니다.');
      }
    } catch (err) {
      alert('오류: ' + err.message);
    } finally {
      btn.disabled = false;
    }
  });

  // ── 공지 배너 닫기 (updated_at 기준으로 기억 → 새 공지는 다시 뜬다) ────
  const notice = document.querySelector('.hub-notice');
  if (notice) {
    const STORE_KEY = 'hubNoticeDismissed';
    const cur = notice.dataset.noticeKey || '';
    if (cur && store.get(STORE_KEY) === cur) {
      notice.remove();
    } else {
      notice.querySelector('[data-notice-dismiss]')?.addEventListener('click', () => {
        store.set(STORE_KEY, cur);
        notice.remove();
      });
    }
  }

  // ── 분류 "모두 열기" (팝업 차단 시 목록 폴백) ──────────────────────────
  const renderOpenFallback = (group, blocked) => {
    group.querySelector('.hub-openfallback')?.remove();
    if (!blocked.length) return;
    const box = document.createElement('div');
    box.className = 'hub-openfallback';
    const msg = document.createElement('p');
    msg.className = 'hub-openfallback__msg';
    msg.textContent = `브라우저가 ${blocked.length}개 탭을 막았습니다. 아래를 눌러 열어주세요.`;
    box.appendChild(msg);
    const list = document.createElement('div');
    list.className = 'hub-openfallback__list';
    blocked.forEach((it) => {
      const a = document.createElement('a');
      a.href = it.url;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      a.className = 'hub-openfallback__link';
      a.textContent = it.title || it.url;
      list.appendChild(a);
    });
    box.appendChild(list);
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'hub-btn hub-btn--sm';
    close.textContent = '닫기';
    close.addEventListener('click', () => box.remove());
    box.appendChild(close);
    group.appendChild(box);
  };

  document.querySelectorAll('[data-open-all]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const group = btn.closest('.lh-group');
      if (!group) return;
      const items = [...group.querySelectorAll('.lh-row:not(.hub-hide)')]
        .map((row) => ({
          url: row.querySelector('.lh-row__link')?.href || '',
          title: row.dataset.name || '',
        }))
        .filter((it) => it.url);
      if (!items.length) return;
      if (!confirm(`${items.length}개 링크를 새 탭으로 엽니다. 계속할까요?`)) return;
      const blocked = [];
      items.forEach((it) => {
        // noopener를 features로 주면 반환값이 항상 null이라 차단 감지가 안 된다.
        // 창을 받은 뒤 opener를 끊어 보안(탭 내빙 방지)과 차단 감지를 모두 챙긴다.
        const win = window.open(it.url, '_blank');
        if (win) {
          try { win.opener = null; } catch (_) { /* 일부 브라우저 무시 */ }
        } else {
          blocked.push(it);
        }
      });
      renderOpenFallback(group, blocked);
    });
  });

  // ── 개인 링크: 추가 / 가져오기 폼 토글 ────────────────────────────────
  [['customAddToggle', 'customAddForm'], ['customImportToggle', 'customImportForm']].forEach(([toggleId, formId]) => {
    const toggle = document.getElementById(toggleId);
    const form = document.getElementById(formId);
    if (!toggle || !form) return;
    toggle.addEventListener('click', () => {
      form.hidden = !form.hidden;
      if (!form.hidden) form.querySelector('input')?.focus();
    });
  });

  // ── 개인 링크 인라인 수정 토글 ────────────────────────────────────────
  document.querySelectorAll('[data-edit-toggle]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      const target = document.getElementById('edit-' + btn.dataset.editToggle);
      if (target) target.hidden = !target.hidden;
    });
  });

  // ── 개인 링크 선택 삭제 모드 ──────────────────────────────────────────
  const customSection = document.getElementById('customSection');
  const selectToggle = document.getElementById('customSelectToggle');
  if (customSection && selectToggle) {
    const bulkBar = document.getElementById('customBulkBar');
    const bulkForm = document.getElementById('customBulkForm');
    const bulkIds = document.getElementById('customBulkIds');
    const selCount = customSection.querySelector('[data-sel-count]');
    const bulkDelete = customSection.querySelector('[data-sel-delete]');
    const cancelBtn = document.getElementById('customSelectCancel');

    const boxes = () => [...customSection.querySelectorAll('[data-sel-id]')];
    const selectedIds = () => boxes().filter((b) => b.checked).map((b) => b.dataset.selId);

    const refresh = () => {
      const n = selectedIds().length;
      if (selCount) selCount.textContent = n;
      if (bulkDelete) bulkDelete.disabled = n === 0;
      boxes().forEach((b) => b.closest('.lh-row')?.classList.toggle('is-selected', b.checked));
    };

    const setMode = (on) => {
      customSection.classList.toggle('is-selecting', on);
      if (bulkBar) bulkBar.hidden = !on;
      selectToggle.classList.toggle('hub-btn--primary', on);
      selectToggle.textContent = on ? '선택 종료' : '선택';
      // 선택 모드에서는 행 클릭이 링크를 여는 대신 체크를 토글해야 한다.
      customSection.querySelectorAll('.lh-row__link').forEach((a) => {
        a.style.pointerEvents = on ? 'none' : '';
      });
      if (!on) boxes().forEach((b) => { b.checked = false; });
      refresh();
    };

    selectToggle.addEventListener('click', () =>
      setMode(!customSection.classList.contains('is-selecting')));
    if (cancelBtn) cancelBtn.addEventListener('click', () => setMode(false));

    customSection.addEventListener('click', (e) => {
      if (!customSection.classList.contains('is-selecting')) return;
      const row = e.target.closest('.lh-row');
      const box = row?.querySelector('[data-sel-id]');
      if (!box) return;
      if (!e.target.closest('.lh-row__check')) box.checked = !box.checked;
      refresh();
    });

    if (bulkForm && bulkIds) {
      bulkForm.addEventListener('submit', (e) => {
        const ids = selectedIds();
        if (!ids.length) { e.preventDefault(); return; }
        if (!confirm(ids.length + '개의 링크를 삭제할까요?')) { e.preventDefault(); return; }
        bulkIds.value = ids.join(',');
      });
    }
  }

  // ── 내 메모 저장 (AJAX, no reload) ───────────────────────────────────
  const memoInput = document.getElementById('hubMemo');
  const memoSave = document.querySelector('[data-memo-save]');
  if (memoInput && memoSave) {
    const status = document.querySelector('[data-memo-status]');
    const count = document.querySelector('[data-memo-count]');
    const setStatus = (text) => { if (status) status.textContent = text; };
    const syncCount = () => {
      if (count) count.textContent = `${memoInput.value.length} / ${memoInput.maxLength}`;
    };
    syncCount();

    // 저장 후 편집하면 "저장됨" 표시를 지워 미저장 상태를 감춘 채로 두지 않는다.
    memoInput.addEventListener('input', () => { syncCount(); setStatus(''); });

    memoSave.addEventListener('click', async () => {
      const url = memoSave.dataset.saveUrl;
      if (!url) return;
      memoSave.disabled = true;
      setStatus('저장 중…');
      try {
        const params = new URLSearchParams({ content: memoInput.value, _csrf: csrfToken() });
        const res = await fetch(url, {
          method: 'POST',
          headers: { 'X-Requested-With': 'XMLHttpRequest' },
          body: params,
        });
        if (res.status === 401) { window.location.href = memoSave.dataset.loginUrl || '/links'; return; }
        const data = await res.json().catch(() => ({}));
        if (res.ok && data.ok) {
          setStatus('저장됨');
        } else {
          setStatus('');
          alert(data.error || '메모 저장에 실패했습니다.');
        }
      } catch (err) {
        setStatus('');
        alert('오류: ' + err.message);
      } finally {
        memoSave.disabled = false;
      }
    });
  }

  // ── 순서 이동 버튼 (즐겨찾기 / 개인 링크) ──────────────────────────────
  // ▲/▼ 클릭으로 행을 한 칸씩 옮기고 새 순서를 저장한다. 드래그보다 위치가 예측 가능.
  document.querySelectorAll('[data-reorder]').forEach((container) => {
    const reorderUrl = container.dataset.reorderUrl;
    if (!reorderUrl) return;

    const rows = () => [...container.querySelectorAll('[data-reorder-id]')];

    // 개인 링크는 행 뒤에 수정 패널(#edit-{id})이 붙는다 — 행 이동 후 패널을 바로 뒤로 재정렬.
    const normalizePanels = () => {
      rows().forEach((row) => {
        const panel = document.getElementById('edit-' + row.dataset.reorderId);
        if (panel && row.nextElementSibling !== panel) row.after(panel);
      });
    };

    // 양 끝 행의 ▲/▼는 비활성화해 "더 갈 곳 없음"을 알린다.
    const refreshEnds = () => {
      const list = rows();
      list.forEach((row, i) => {
        const up = row.querySelector('[data-move="up"]');
        const down = row.querySelector('[data-move="down"]');
        if (up) up.disabled = i === 0;
        if (down) down.disabled = i === list.length - 1;
      });
    };

    const persist = async () => {
      const ids = rows().map((r) => r.dataset.reorderId).join(',');
      try {
        const params = new URLSearchParams({ ids, _csrf: csrfToken() });
        const res = await fetch(reorderUrl, {
          method: 'POST',
          headers: { 'X-Requested-With': 'XMLHttpRequest' },
          body: params,
        });
        if (res.status === 401) window.location.href = '/hub/login';
      } catch (_) {
        // 순서는 화면에 이미 반영됨 — 저장 실패는 조용히 넘긴다(다음 조작 때 재시도).
      }
    };

    container.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-move]');
      if (!btn || btn.disabled) return;
      e.preventDefault();
      e.stopPropagation();
      const row = btn.closest('[data-reorder-id]');
      if (!row) return;
      const list = rows();
      const i = list.indexOf(row);
      if (btn.dataset.move === 'up' && i > 0) list[i - 1].before(row);
      else if (btn.dataset.move === 'down' && i < list.length - 1) list[i + 1].after(row);
      else return;
      normalizePanels();
      refreshEnds();
      persist();
      // 연속 이동이 편하도록 포커스를 유지한다(끝에 닿아 비활성화되면 반대쪽 버튼으로).
      if (!btn.disabled) btn.focus();
      else row.querySelector('[data-move]:not([disabled])')?.focus();
    });

    // 즐겨찾기 행이 동적으로 추가/제거될 때 ▲▼ 양 끝 상태를 다시 맞추기 위해 등록해 둔다.
    reorderRefreshers.set(container, refreshEnds);
    refreshEnds();
  });
})();
