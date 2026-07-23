/* Service Link Hub — shared behaviors. Endpoints/field names unchanged; UI only. */
(() => {
  'use strict';

  const csrfToken = () => document.querySelector('meta[name="_csrf"]')?.content || '';

  // ── ★ favorite toggle (AJAX, no reload) ──────────────────────────────
  document.querySelectorAll('.lh-fav').forEach((btn) => {
    btn.addEventListener('click', async (event) => {
      event.preventDefault();
      event.stopPropagation();
      if (btn.disabled) return;
      const linkId = btn.dataset.linkId;
      const url = btn.dataset.toggleUrl;
      if (!linkId || !url) return;
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
          btn.classList.toggle('lh-fav--on', data.favorited);
          btn.setAttribute('aria-pressed', String(data.favorited));
          btn.classList.remove('lh-fav--pulse');
          // reflow to restart animation
          void btn.offsetWidth;
          btn.classList.add('lh-fav--pulse');
          // on the personal page, removing a favorite drops its card
          if (!data.favorited && btn.dataset.removeOnUnfav != null) {
            const card = btn.closest('[data-fav-card]');
            if (card) {
              card.style.transition = 'opacity .2s, transform .2s';
              card.style.opacity = '0';
              card.style.transform = 'scale(.96)';
              setTimeout(() => card.remove(), 200);
            }
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
  });

  // ── 검색 필터 (허브 전체: 즐겨찾기 + 내 링크 + 공용 링크) ────────────
  const search = document.getElementById('hubSearch');
  if (search) {
    const sections = document.querySelectorAll('[data-search-section]');
    const noResult = document.getElementById('hubNoResult');

    search.addEventListener('input', () => {
      const q = search.value.trim().toLowerCase();
      document.querySelectorAll('[data-search-name]').forEach((el) => {
        const name = (el.dataset.searchName || '').toLowerCase();
        el.classList.toggle('hub-hide', q !== '' && !name.includes(q));
      });
      // 검색 중에는 열려 있던 수정 폼을 닫는다 — 대상 카드가 사라질 수 있어서.
      if (q !== '') document.querySelectorAll('.hub-edit').forEach((panel) => { panel.hidden = true; });

      // 카드가 하나도 안 남은 섹션은 제목만 떠 있지 않도록 통째로 숨긴다.
      // 검색어가 없을 땐 전부 되돌린다 — 카드가 0개인 섹션(빈 상태 안내)도 다시 보여야 한다.
      let visible = 0;
      sections.forEach((section) => {
        const hits = section.querySelectorAll('[data-search-name]:not(.hub-hide)').length;
        section.classList.toggle('hub-hide', q !== '' && hits === 0);
        visible += hits;
      });
      if (noResult) noResult.hidden = !(q !== '' && visible === 0);
      clearKbd(); // 결과 집합이 바뀌면 키보드 하이라이트 초기화
    });
  }

  // ── 키보드 빠른 실행: Cmd/Ctrl+K 포커스, Enter로 열기, ↑↓ 결과 이동 ──────
  let kbdActive = -1;
  const clearKbd = () => {
    document.querySelectorAll('.hub-kbd-active').forEach((el) => el.classList.remove('hub-kbd-active'));
    kbdActive = -1;
  };
  if (search) {
    // 검색 필터 기준으로 "지금 화면에 보이는" 링크 앵커만 순서대로 모은다.
    const openable = () => {
      const out = [];
      document.querySelectorAll('[data-search-name]').forEach((el) => {
        if (el.classList.contains('hub-hide') || el.closest('.hub-hide')) return;
        const a = el.matches('a') ? el : el.querySelector('a.hub-card__link');
        if (a) out.push({ el, a });
      });
      return out;
    };
    const highlight = (list, idx) => {
      clearKbd();
      if (idx >= 0 && list[idx]) {
        kbdActive = idx;
        list[idx].el.classList.add('hub-kbd-active');
        list[idx].el.scrollIntoView({ block: 'nearest' });
      }
    };

    // 전역 단축키: Cmd/Ctrl+K → 검색창으로 점프
    document.addEventListener('keydown', (e) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        search.focus();
        search.select();
      }
    });

    search.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        const list = openable();
        if (!list.length) return;
        e.preventDefault();
        const next = e.key === 'ArrowDown'
          ? (kbdActive < list.length - 1 ? kbdActive + 1 : 0)
          : (kbdActive > 0 ? kbdActive - 1 : list.length - 1);
        highlight(list, next);
      } else if (e.key === 'Enter') {
        const list = openable();
        if (!list.length) return;
        e.preventDefault();
        const pick = kbdActive >= 0 && list[kbdActive] ? list[kbdActive] : list[0];
        pick.a.click(); // target=_blank 로 새 탭에서 열림
      } else if (e.key === 'Escape') {
        if (search.value) {
          search.value = '';
          search.dispatchEvent(new Event('input', { bubbles: true }));
        } else {
          search.blur();
        }
        clearKbd();
      }
    });
  }

  // ── 커스텀 링크 추가 폼 펼치기/접기 ───────────────────────────────────
  const addToggle = document.getElementById('customAddToggle');
  const addForm = document.getElementById('customAddForm');
  if (addToggle && addForm) {
    addToggle.addEventListener('click', () => {
      addForm.hidden = !addForm.hidden;
      if (!addForm.hidden) addForm.querySelector('input')?.focus();
    });
  }

  // ── 북마크 가져오기 폼 펼치기/접기 ────────────────────────────────────
  const importToggle = document.getElementById('customImportToggle');
  const importForm = document.getElementById('customImportForm');
  if (importToggle && importForm) {
    importToggle.addEventListener('click', () => {
      importForm.hidden = !importForm.hidden;
      if (!importForm.hidden) importForm.querySelector('input')?.focus();
    });
  }

  // ── 공지 배너 닫기 (updated_at 기준으로 기억 → 새 공지는 다시 뜬다) ────────
  const notice = document.querySelector('.hub-notice');
  if (notice) {
    const STORE_KEY = 'hubNoticeDismissed';
    const cur = notice.dataset.noticeKey || '';
    let dismissed = '';
    try { dismissed = localStorage.getItem(STORE_KEY) || ''; } catch (_) { /* 무시 */ }
    if (cur && dismissed === cur) {
      notice.remove();
    } else {
      notice.querySelector('[data-notice-dismiss]')?.addEventListener('click', () => {
        try { localStorage.setItem(STORE_KEY, cur); } catch (_) { /* 무시 */ }
        notice.remove();
      });
    }
  }

  // ── 링크 세트: 카테고리 "모두 열기" (팝업 차단 시 목록 폴백) ────────────
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
      const group = btn.closest('.hub-catgroup');
      if (!group) return;
      const items = [...group.querySelectorAll('.hub-card')]
        .map((card) => ({
          url: card.querySelector('.hub-card__url')?.textContent.trim() || '',
          title: card.querySelector('.hub-card__title')?.textContent.trim() || '',
        }))
        .filter((it) => it.url);
      if (!items.length) return;
      if (!confirm(`${items.length}개 링크를 새 탭으로 엽니다. 계속할까요?`)) return;
      const blocked = [];
      items.forEach((it) => {
        // noopener를 features로 주면 반환값이 항상 null이라 차단 감지가 안 된다.
        // 창을 받은 뒤 opener를 끊어 보안(탭 나빙 방지)과 차단 감지를 모두 챙긴다.
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

  // ── 내 링크 선택 삭제 모드 ────────────────────────────────────────────
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
      boxes().forEach((b) => b.closest('.hub-card')?.classList.toggle('is-selected', b.checked));
    };

    const setMode = (on) => {
      customSection.classList.toggle('is-selecting', on);
      if (bulkBar) bulkBar.hidden = !on;
      selectToggle.classList.toggle('hub-btn--primary', on);
      selectToggle.textContent = on ? '선택 종료' : '선택';
      if (!on) boxes().forEach((b) => { b.checked = false; });
      refresh();
    };

    selectToggle.addEventListener('click', () =>
      setMode(!customSection.classList.contains('is-selecting')));
    if (cancelBtn) cancelBtn.addEventListener('click', () => setMode(false));

    // 선택 모드에서 카드 아무 곳이나 클릭 → 체크 토글 (체크박스 직접 클릭은 native)
    customSection.addEventListener('click', (e) => {
      if (!customSection.classList.contains('is-selecting')) return;
      const card = e.target.closest('.hub-card');
      const box = card?.querySelector('[data-sel-id]');
      if (!box) return;
      if (!e.target.closest('.hub-card__check')) box.checked = !box.checked;
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

  // ── 커스텀 링크 인라인 수정 토글 ──────────────────────────────────────
  document.querySelectorAll('[data-edit-toggle]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = document.getElementById('edit-' + btn.dataset.editToggle);
      if (target) target.hidden = !target.hidden;
    });
  });

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

  // ── 파비콘 타일: 카드/최근목록 URL로 {origin}/favicon.ico 시도, 실패 시 첫 글자 유지 ──
  const originOf = (raw) => { try { return new URL(raw).origin; } catch (_) { return null; } };
  const buildMedia = (cls, label, origin) => {
    const media = document.createElement('span');
    media.className = cls;
    media.textContent = (label || '?').trim().slice(0, 1).toUpperCase() || '?';
    const img = new Image();
    img.alt = '';
    img.loading = 'lazy';
    img.referrerPolicy = 'no-referrer';
    // 로드 성공 시에만 글자 대신 파비콘을 넣는다(404/에러면 글자 타일 유지).
    img.addEventListener('load', () => { media.textContent = ''; media.appendChild(img); });
    img.src = origin + '/favicon.ico';
    return media;
  };
  document.querySelectorAll('.hub-card').forEach((card) => {
    const urlEl = card.querySelector('.hub-card__url');
    const top = card.querySelector('.hub-card__top');
    if (!urlEl || !top || top.querySelector('.hub-card__media')) return;
    const origin = originOf(urlEl.textContent.trim());
    if (!origin) return;
    const title = card.querySelector('.hub-card__title')?.textContent;
    top.insertBefore(buildMedia('hub-card__media', title, origin), top.firstChild);
  });
  document.querySelectorAll('.hub-recent__item').forEach((item) => {
    const urlEl = item.querySelector('.hub-recent__url');
    if (!urlEl || item.querySelector('.hub-recent__media')) return;
    const origin = originOf(urlEl.textContent.trim());
    if (!origin) return;
    const title = item.querySelector('.hub-recent__title')?.textContent;
    item.insertBefore(buildMedia('hub-recent__media', title, origin), item.firstChild);
  });

  // ── 순서 이동 버튼 (즐겨찾기 / 내 링크) ─────────────────────────────────
  // ▲/▼ 클릭으로 카드를 한 칸씩 옮기고 새 순서를 저장한다. 드래그보다 위치가 예측 가능.
  document.querySelectorAll('[data-reorder]').forEach((container) => {
    const reorderUrl = container.dataset.reorderUrl;
    if (!reorderUrl) return;

    const cards = () => [...container.querySelectorAll('[data-reorder-id]')];

    // 내 링크는 카드 뒤에 수정 패널(#edit-{id})이 붙는다 — 카드 이동 후 패널을 카드 바로 뒤로 재정렬.
    const normalizePanels = () => {
      cards().forEach((card) => {
        const panel = document.getElementById('edit-' + card.dataset.reorderId);
        if (panel && card.nextElementSibling !== panel) card.after(panel);
      });
    };

    // 양 끝 카드의 ▲/▼는 비활성화해 "더 갈 곳 없음"을 알린다.
    const refreshEnds = () => {
      const list = cards();
      list.forEach((card, i) => {
        const up = card.querySelector('[data-move="up"]');
        const down = card.querySelector('[data-move="down"]');
        if (up) up.disabled = i === 0;
        if (down) down.disabled = i === list.length - 1;
      });
    };

    const persist = async () => {
      const ids = cards().map((c) => c.dataset.reorderId).join(',');
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
      const card = btn.closest('[data-reorder-id]');
      if (!card) return;
      const list = cards();
      const i = list.indexOf(card);
      if (btn.dataset.move === 'up' && i > 0) list[i - 1].before(card);
      else if (btn.dataset.move === 'down' && i < list.length - 1) list[i + 1].after(card);
      else return;
      normalizePanels();
      refreshEnds();
      persist();
      // 연속 이동이 편하도록 포커스를 유지한다(끝에 닿아 비활성화되면 반대쪽 버튼으로).
      if (!btn.disabled) btn.focus();
      else card.querySelector('[data-move]:not([disabled])')?.focus();
    });

    refreshEnds();
  });
})();
