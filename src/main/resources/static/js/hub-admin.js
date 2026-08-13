/* 링크 관리 화면 — 탭 · 필터 · 인라인 편집 · 분류 순서 드래그.
   서버 엔드포인트와 파라미터 이름은 기존 그대로다. */
(() => {
  'use strict';

  const csrfToken = () => document.querySelector('meta[name="_csrf"]')?.content || '';

  // ── 탭 ───────────────────────────────────────────────────────────────
  const tabs = [...document.querySelectorAll('[data-tab]')];
  const panels = [...document.querySelectorAll('[data-tab-panel]')];
  if (tabs.length) {
    const activate = (name) => {
      tabs.forEach((tab) => {
        const on = tab.dataset.tab === name;
        tab.classList.toggle('adm-tab--on', on);
        tab.setAttribute('aria-selected', String(on));
      });
      panels.forEach((panel) => { panel.hidden = panel.dataset.tabPanel !== name; });
    };
    tabs.forEach((tab) => tab.addEventListener('click', () => activate(tab.dataset.tab)));
  }

  // ── 링크 탭: 검색 + 분류/환경 필터 ────────────────────────────────────
  const search = document.getElementById('manageSearch');
  const catSel = document.getElementById('manageCat');
  const envSel = document.getElementById('manageEnv');
  if (search || catSel || envSel) {
    const rows = [...document.querySelectorAll('[data-adm-row]')];

    const apply = () => {
      const q = (search?.value || '').trim().toLowerCase();
      const cat = catSel?.value || '';
      const env = envSel?.value || '';
      rows.forEach((row) => {
        const haystack = `${row.dataset.name || ''} ${row.dataset.cat || ''} ${row.dataset.host || ''}`.toLowerCase();
        const hit = (!q || haystack.includes(q))
          && (!cat || row.dataset.cat === cat)
          && (!env || row.dataset.env === env);
        row.hidden = !hit;
        // 걸러진 행의 편집·삭제 패널이 혼자 남지 않도록 같이 닫는다.
        if (!hit) {
          const id = row.querySelector('[data-edit-toggle]')?.dataset.editToggle;
          if (id) {
            const edit = document.getElementById('edit-' + id);
            const del = document.getElementById('del-' + id);
            if (edit) edit.hidden = true;
            if (del) del.hidden = true;
          }
        }
      });
    };

    [search, catSel, envSel].forEach((el) => el?.addEventListener('input', apply));
  }

  // ── 인라인 편집 / 삭제 확인 토글 ──────────────────────────────────────
  const togglePanel = (prefix, id, closeOther) => {
    const panel = document.getElementById(prefix + '-' + id);
    if (!panel) return;
    const other = document.getElementById(closeOther + '-' + id);
    if (other) other.hidden = true;
    panel.hidden = !panel.hidden;
  };
  document.querySelectorAll('[data-edit-toggle]').forEach((btn) => {
    btn.addEventListener('click', () => togglePanel('edit', btn.dataset.editToggle, 'del'));
  });
  document.querySelectorAll('[data-del-toggle]').forEach((btn) => {
    btn.addEventListener('click', () => togglePanel('del', btn.dataset.delToggle, 'edit'));
  });

  // ── 링크 추가 슬라이드 패널 ───────────────────────────────────────────
  document.querySelectorAll('[data-drawer-open]').forEach((btn) => {
    const drawer = document.getElementById(btn.dataset.drawerOpen);
    if (!drawer) return;
    const close = () => { drawer.hidden = true; btn.focus(); };
    btn.addEventListener('click', () => {
      drawer.hidden = false;
      drawer.querySelector('input')?.focus();
    });
    drawer.addEventListener('click', (e) => { if (e.target === drawer) close(); });
    drawer.querySelector('[data-drawer-close]')?.addEventListener('click', close);
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && !drawer.hidden) close();
    });
  });

  // ── 공지 미리보기 ────────────────────────────────────────────────────
  const noticeMessage = document.getElementById('noticeMessage');
  const noticeLevel = document.getElementById('noticeLevel');
  const preview = document.getElementById('noticePreview');
  if (noticeMessage && preview) {
    const target = preview.querySelector('[data-notice-preview]');
    const render = () => {
      const text = noticeMessage.value.trim();
      if (target) target.textContent = text || '메시지를 입력하면 여기에 보입니다.';
      preview.classList.toggle('hub-notice--warn', noticeLevel?.value === 'warn');
    };
    noticeMessage.addEventListener('input', render);
    noticeLevel?.addEventListener('change', render);
    render();
  }

  // ── 분류 순서: 드래그로 정렬 후 저장 ──────────────────────────────────
  const catList = document.getElementById('catOrderList');
  if (catList) {
    const rows = () => [...catList.querySelectorAll('.adm-catrow')];
    let dragged = null;

    // 화면 순서를 그대로 0,1,2… 로 다시 매긴다(표시값도 함께 갱신).
    const renumber = () => {
      rows().forEach((row, i) => {
        const cell = row.querySelector('[data-cat-order]');
        if (cell) cell.textContent = String(i);
      });
    };

    catList.addEventListener('dragstart', (e) => {
      const row = e.target.closest('.adm-catrow');
      if (!row) return;
      dragged = row;
      row.classList.add('is-dragging');
      e.dataTransfer.effectAllowed = 'move';
      // Firefox는 데이터가 설정돼야 드래그를 시작한다.
      e.dataTransfer.setData('text/plain', row.dataset.cat || '');
    });

    catList.addEventListener('dragend', () => {
      dragged?.classList.remove('is-dragging');
      rows().forEach((row) => row.classList.remove('is-over'));
      dragged = null;
      renumber();
    });

    catList.addEventListener('dragover', (e) => {
      e.preventDefault();
      const row = e.target.closest('.adm-catrow');
      if (!row || !dragged || row === dragged) return;
      rows().forEach((r) => r.classList.toggle('is-over', r === row));
      // 커서가 대상 행의 위쪽 절반이면 앞에, 아래쪽이면 뒤에 놓는다.
      const box = row.getBoundingClientRect();
      if (e.clientY < box.top + box.height / 2) row.before(dragged);
      else row.after(dragged);
    });

    catList.addEventListener('drop', (e) => e.preventDefault());

    document.getElementById('catOrderSave')?.addEventListener('click', async (e) => {
      const btn = e.currentTarget;
      const url = btn.dataset.url;
      if (!url) return;
      const params = new URLSearchParams({ _csrf: csrfToken() });
      rows().forEach((row, i) => {
        if (row.dataset.cat) params.append('cat_' + row.dataset.cat, String(i));
      });
      btn.disabled = true;
      const msg = document.getElementById('catOrderMsg');
      try {
        const res = await fetch(url, { method: 'POST', body: params });
        const data = await res.json().catch(() => ({}));
        if (data.ok) {
          if (msg) { msg.style.display = 'inline'; setTimeout(() => { msg.style.display = 'none'; }, 2000); }
        } else {
          alert('저장 실패: ' + (data.msg || ''));
        }
      } catch (err) {
        alert('저장 중 오류: ' + err.message);
      } finally {
        btn.disabled = false;
      }
    });
  }
})();
