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
})();
