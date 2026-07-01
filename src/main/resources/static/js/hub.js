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

  // ── 검색 필터 (내 페이지: 커스텀 링크) ───────────────────────────────
  const search = document.getElementById('hubSearch');
  if (search) {
    search.addEventListener('input', () => {
      const q = search.value.trim().toLowerCase();
      document.querySelectorAll('[data-search-name]').forEach((el) => {
        const name = (el.dataset.searchName || '').toLowerCase();
        el.classList.toggle('hub-hide', q !== '' && !name.includes(q));
      });
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
})();
