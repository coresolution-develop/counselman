(function () {
  'use strict';

  const state = {
    templateIdx: null,
    templateName: '',
    mainCategories: [],
    subCategories: [],
    options: [],
    selectedMain: null,
    selectedSub: null,
    selectedOption: null,
    sortDirty: false,
    sortables: [],
    form: {
      mode: 'add',
      type: 'main',
      target: null,
    },
  };

  async function api(url, options = {}) {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    const init = { ...options, headers: { ...(options.headers || {}) } };
    if (token && header) init.headers[header] = token;
    if (!init.headers['Content-Type'] && init.method && init.method !== 'GET') {
      init.headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(url, init);
    const text = await res.text();
    let data;
    try {
      data = text ? JSON.parse(text) : null;
    } catch (_) {
      data = text;
    }
    if (!res.ok) {
      throw new Error(typeof data === 'string' ? data : JSON.stringify(data));
    }
    return data;
  }

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function isSelectSub(sub) {
    return Number(sub?.sub_col_05) === 1;
  }

  function disabledTextInput(placeholder) {
    const input = document.createElement('input');
    input.type = 'text';
    input.disabled = true;
    input.placeholder = placeholder || '입력하세요';
    return input;
  }

  function disabledSelect(options) {
    const select = document.createElement('select');
    select.disabled = true;
    const first = document.createElement('option');
    first.textContent = '선택하세요';
    select.appendChild(first);
    (options || []).forEach((o) => {
      const op = document.createElement('option');
      op.textContent = o;
      select.appendChild(op);
    });
    return select;
  }

  function renderSubControl(sub, options) {
    const line = el('div', 'preview-field-item');
    const controls = el('div', 'preview-field-main');
    const chk = Number(sub.sub_col_02) === 1;
    const rad = Number(sub.sub_col_03) === 1;
    const txt = Number(sub.sub_col_04) === 1;
    const sel = Number(sub.sub_col_05) === 1;

    if (chk) {
      const c = document.createElement('input');
      c.type = 'checkbox';
      c.disabled = true;
      controls.appendChild(c);
    }
    if (rad) {
      const r = document.createElement('input');
      r.type = 'radio';
      r.disabled = true;
      controls.appendChild(r);
    }
    controls.appendChild(el('span', 'preview-field-name', sub.sub_col_01 || '-'));

    line.appendChild(controls);

    if (sel) {
      const selectWrap = el('div', 'preview-field-extra');
      selectWrap.appendChild(disabledSelect((options || []).map((o) => o.option_col_01)));
      line.appendChild(selectWrap);
    }
    if (txt) {
      const textWrap = el('div', 'preview-field-extra');
      textWrap.appendChild(disabledTextInput('입력하세요'));
      line.appendChild(textWrap);
    }
    if (!chk && !rad && !sel && !txt) {
      const textWrap = el('div', 'preview-field-extra');
      textWrap.appendChild(disabledTextInput('입력하세요'));
      line.appendChild(textWrap);
    }
    return line;
  }

  async function renderTemplatePreview(templateIdx) {
    const left = document.getElementById('template-preview-left');
    if (!left) return;
    left.innerHTML = '';

    const mainCategories = await api(`/csm/core/setting/maincategory/${templateIdx}`);

    for (const main of (mainCategories || [])) {
      const section = el('div', 'preview-section');
      const body = el('div', 'preview-section-body');
      const group = el('div', 'preview-group');
      const groupLabel = el('div', 'preview-group-label', main.main_col_01 || '대분류');
      const groupFields = el('div', 'preview-group-fields');

      const subs = await api(`/csm/core/setting/subcategory/${main.idx}`);
      for (const sub of (subs || [])) {
        const opts = await api(`/csm/core/setting/options/${sub.idx}`);
        groupFields.appendChild(renderSubControl(sub, opts));
      }

      if (!subs || subs.length === 0) {
        groupFields.appendChild(el('div', 'preview-empty', '소분류가 없습니다.'));
      }

      group.appendChild(groupLabel);
      group.appendChild(groupFields);
      body.appendChild(group);
      section.appendChild(body);
      left.appendChild(section);
    }

    if (!mainCategories || mainCategories.length === 0) {
      left.appendChild(el('div', 'preview-card', '선택한 템플릿에 카테고리가 없습니다.'));
    }
  }

  function subTypeText(sub) {
    const flags = [];
    if (Number(sub.sub_col_02) === 1) flags.push('checkbox');
    if (Number(sub.sub_col_03) === 1) flags.push('radio');
    if (Number(sub.sub_col_04) === 1) flags.push('text');
    if (Number(sub.sub_col_05) === 1) flags.push('select');
    return flags.join(', ');
  }

  function renderMainList() {
    const wrap = document.getElementById('editor-main-list');
    if (!wrap) return;
    wrap.innerHTML = '';

    if (!state.mainCategories.length) {
      wrap.appendChild(el('div', 'editor-empty', '대분류가 없습니다.'));
      renderSubList();
      renderOptionList();
      initSortables();
      return;
    }

    state.mainCategories.forEach((main) => {
      const item = el('button', 'editor-item');
      item.type = 'button';
      item.dataset.id = main.idx;
      item.dataset.type = 'main';
      if (state.selectedMain && Number(state.selectedMain.idx) === Number(main.idx)) {
        item.classList.add('active');
      }
      item.textContent = main.main_col_01 || '-';
      item.addEventListener('click', async () => {
        state.selectedMain = main;
        state.selectedSub = null;
        state.selectedOption = null;
        await loadSubCategories(main.idx);
        renderMainList();
      });
      item.addEventListener('dblclick', () => openFormModal({ mode: 'edit', type: 'main', target: main }));
      wrap.appendChild(item);
    });

    initSortables();
  }

  function renderSubList() {
    const wrap = document.getElementById('editor-sub-list');
    if (!wrap) return;
    wrap.innerHTML = '';

    if (!state.selectedMain) {
      wrap.appendChild(el('div', 'editor-empty', '대분류를 선택하세요.'));
      renderOptionList();
      initSortables();
      return;
    }

    if (!state.subCategories.length) {
      wrap.appendChild(el('div', 'editor-empty', '소분류가 없습니다.'));
      renderOptionList();
      initSortables();
      return;
    }

    state.subCategories.forEach((sub) => {
      const item = el('button', 'editor-item');
      item.type = 'button';
      item.dataset.id = sub.idx;
      item.dataset.type = 'sub';
      if (state.selectedSub && Number(state.selectedSub.idx) === Number(sub.idx)) {
        item.classList.add('active');
      }
      const type = subTypeText(sub);
      item.textContent = type ? `${sub.sub_col_01 || '-'} [${type}]` : (sub.sub_col_01 || '-');
      item.addEventListener('click', async () => {
        state.selectedSub = sub;
        state.selectedOption = null;
        if (isSelectSub(sub)) {
          await loadOptions(sub.idx);
        } else {
          state.options = [];
        }
        renderSubList();
        renderOptionList();
      });
      item.addEventListener('dblclick', () => openFormModal({ mode: 'edit', type: 'sub', target: sub }));
      wrap.appendChild(item);
    });

    initSortables();
  }

  function renderOptionList() {
    const wrap = document.getElementById('editor-option-list');
    if (!wrap) return;
    wrap.innerHTML = '';

    if (!state.selectedSub) {
      wrap.appendChild(el('div', 'editor-empty', '소분류를 선택하세요.'));
      initSortables();
      return;
    }

    if (!isSelectSub(state.selectedSub)) {
      wrap.appendChild(el('div', 'editor-empty', '선택한 소분류는 select 타입이 아닙니다.'));
      initSortables();
      return;
    }

    if (!state.options.length) {
      wrap.appendChild(el('div', 'editor-empty', '옵션이 없습니다.'));
      initSortables();
      return;
    }

    state.options.forEach((option) => {
      const item = el('button', 'editor-item');
      item.type = 'button';
      item.dataset.id = option.idx;
      item.dataset.type = 'option';
      if (state.selectedOption && Number(state.selectedOption.idx) === Number(option.idx)) {
        item.classList.add('active');
      }
      item.textContent = option.option_col_01 || '-';
      item.addEventListener('click', () => {
        state.selectedOption = option;
        renderOptionList();
      });
      item.addEventListener('dblclick', () => openFormModal({ mode: 'edit', type: 'option', target: option }));
      wrap.appendChild(item);
    });

    initSortables();
  }

  function destroySortables() {
    state.sortables.forEach((s) => {
      try {
        s.destroy();
      } catch (_) {
      }
    });
    state.sortables = [];
  }

  function initSortables() {
    if (typeof Sortable === 'undefined') return;
    destroySortables();

    ['editor-main-list', 'editor-sub-list', 'editor-option-list'].forEach((id) => {
      const node = document.getElementById(id);
      if (!node) return;
      const sortable = new Sortable(node, {
        animation: 120,
        draggable: '.editor-item',
        onEnd: () => {
          state.sortDirty = true;
        },
      });
      state.sortables.push(sortable);
    });
  }

  async function loadMainCategories() {
    if (!state.templateIdx) return;
    state.mainCategories = await api(`/csm/core/setting/maincategory/${state.templateIdx}`) || [];

    const stillExists = state.selectedMain && state.mainCategories.some((m) => Number(m.idx) === Number(state.selectedMain.idx));
    if (!stillExists) state.selectedMain = state.mainCategories[0] || null;

    if (state.selectedMain) {
      await loadSubCategories(state.selectedMain.idx);
    } else {
      state.subCategories = [];
      state.options = [];
      state.selectedSub = null;
      state.selectedOption = null;
    }

    renderMainList();
    renderSubList();
    renderOptionList();
  }

  async function loadSubCategories(mainIdx) {
    state.subCategories = await api(`/csm/core/setting/subcategory/${mainIdx}`) || [];

    const stillExists = state.selectedSub && state.subCategories.some((s) => Number(s.idx) === Number(state.selectedSub.idx));
    if (!stillExists) state.selectedSub = state.subCategories[0] || null;

    if (state.selectedSub && isSelectSub(state.selectedSub)) {
      await loadOptions(state.selectedSub.idx);
    } else {
      state.options = [];
      state.selectedOption = null;
    }

    renderSubList();
    renderOptionList();
  }

  async function loadOptions(subIdx) {
    state.options = await api(`/csm/core/setting/options/${subIdx}`) || [];
    const stillExists = state.selectedOption && state.options.some((o) => Number(o.idx) === Number(state.selectedOption.idx));
    if (!stillExists) state.selectedOption = state.options[0] || null;
    renderOptionList();
  }

  function parseOptionTokens(value) {
    return String(value || '')
      .split(',')
      .map((v) => v.trim())
      .filter(Boolean);
  }

  function setChipValues(types) {
    const set = new Set(types || []);
    document.querySelectorAll('.type-chip').forEach((chip) => {
      chip.classList.toggle('active', set.has(chip.dataset.type));
    });
  }

  function getChipValues() {
    return Array.from(document.querySelectorAll('.type-chip.active')).map((chip) => chip.dataset.type);
  }

  function parseSubTypeByChips() {
    const values = getChipValues();
    return {
      sub_col_02: values.includes('checkbox') ? 1 : 0,
      sub_col_03: values.includes('radio') ? 1 : 0,
      sub_col_04: values.includes('text') ? 1 : 0,
      sub_col_05: values.includes('select') ? 1 : 0,
    };
  }

  function openFormModal({ mode, type, target }) {
    state.form.mode = mode;
    state.form.type = type;
    state.form.target = target || null;

    const overlay = document.getElementById('editor-form-overlay');
    const title = document.getElementById('editor-form-title');
    const name = document.getElementById('editor-form-name');
    const options = document.getElementById('editor-form-options');
    const subtypeRow = document.getElementById('editor-subtype-row');
    const optionRow = document.getElementById('editor-sub-option-row');

    const nameText = mode === 'add' ? '추가' : '수정';
    const typeText = type === 'main' ? '대분류' : (type === 'sub' ? '소분류' : '옵션');
    title.textContent = `${typeText} ${nameText}`;

    name.value = '';
    options.value = '';
    setChipValues([]);

    const isSub = type === 'sub';
    subtypeRow.style.display = isSub ? '' : 'none';
    optionRow.style.display = isSub ? '' : 'none';

    if (target) {
      if (type === 'main') name.value = target.main_col_01 || '';
      if (type === 'sub') {
        name.value = target.sub_col_01 || '';
        const chips = [];
        if (Number(target.sub_col_02) === 1) chips.push('checkbox');
        if (Number(target.sub_col_03) === 1) chips.push('radio');
        if (Number(target.sub_col_04) === 1) chips.push('text');
        if (Number(target.sub_col_05) === 1) chips.push('select');
        setChipValues(chips);
      }
      if (type === 'option') name.value = target.option_col_01 || '';
    }

    overlay.classList.add('show');
    setTimeout(() => name.focus(), 0);
  }

  function closeFormModal() {
    const overlay = document.getElementById('editor-form-overlay');
    overlay?.classList.remove('show');
  }

  async function submitFormModal() {
    const name = (document.getElementById('editor-form-name')?.value || '').trim();
    const optionText = (document.getElementById('editor-form-options')?.value || '').trim();
    if (!name) {
      alert('이름을 입력하세요.');
      return;
    }

    const { mode, type, target } = state.form;

    try {
      if (mode === 'add') {
        if (type === 'main') {
          if (!state.templateIdx) throw new Error('템플릿을 먼저 선택하세요.');
          await api('/csm/core/setting/category1', {
            method: 'POST',
            body: JSON.stringify({ template_idx: state.templateIdx, main_col_01: name }),
          });
          await loadMainCategories();
        }

        if (type === 'sub') {
          if (!state.selectedMain) throw new Error('대분류를 먼저 선택하세요.');
          const flags = parseSubTypeByChips();
          if (!flags.sub_col_02 && !flags.sub_col_03 && !flags.sub_col_04 && !flags.sub_col_05) {
            throw new Error('소분류 타입을 1개 이상 선택하세요.');
          }
          await api('/csm/core/setting/category2', {
            method: 'POST',
            body: JSON.stringify({
              main_idx: state.selectedMain.idx,
              sub_col_01: name,
              ...flags,
            }),
          });

          await loadSubCategories(state.selectedMain.idx);

          const createdSub = state.subCategories[state.subCategories.length - 1];
          if (createdSub && Number(flags.sub_col_05) === 1) {
            const values = parseOptionTokens(optionText);
            if (values.length) {
              await api('/csm/core/setting/options/insert', {
                method: 'POST',
                body: JSON.stringify({ subCategoryId: createdSub.idx, options: values }),
              });
              state.selectedSub = createdSub;
              await loadOptions(createdSub.idx);
            }
          }
        }

        if (type === 'option') {
          if (!state.selectedSub || !isSelectSub(state.selectedSub)) {
            throw new Error('select 타입 소분류를 먼저 선택하세요.');
          }
          await api('/csm/core/setting/options/insert', {
            method: 'POST',
            body: JSON.stringify({ subCategoryId: state.selectedSub.idx, options: [name] }),
          });
          await loadOptions(state.selectedSub.idx);
        }
      }

      if (mode === 'edit') {
        if (!target?.idx) throw new Error('수정 대상이 없습니다.');
        const payload = { type, id: target.idx, inputValue: name };
        if (type === 'sub') {
          const flags = parseSubTypeByChips();
          if (!flags.sub_col_02 && !flags.sub_col_03 && !flags.sub_col_04 && !flags.sub_col_05) {
            throw new Error('소분류 타입을 1개 이상 선택하세요.');
          }
          payload.sub_col_02 = flags.sub_col_02;
          payload.sub_col_03 = flags.sub_col_03;
          payload.sub_col_04 = flags.sub_col_04;
          payload.sub_col_05 = flags.sub_col_05;
        }
        await api('/csm/core/setting/modifyCategory', {
          method: 'POST',
          body: JSON.stringify(payload),
        });
        await loadMainCategories();
      }

      await renderTemplatePreview(state.templateIdx);
      closeFormModal();
    } catch (e) {
      alert(`${mode === 'add' ? '추가' : '수정'} 실패: ${e.message}`);
    }
  }

  async function onDeleteMain() {
    if (!state.selectedMain) {
      alert('삭제할 대분류를 선택하세요.');
      return;
    }
    if (!window.confirm('선택한 대분류를 삭제하시겠습니까? (하위 소분류/옵션 포함)')) return;

    try {
      await api('/csm/core/setting/main/delete', {
        method: 'POST',
        body: JSON.stringify({ idx: state.selectedMain.idx }),
      });
      state.selectedMain = null;
      await loadMainCategories();
      await renderTemplatePreview(state.templateIdx);
    } catch (e) {
      alert(`대분류 삭제 실패: ${e.message}`);
    }
  }

  async function onDeleteSub() {
    if (!state.selectedSub) {
      alert('삭제할 소분류를 선택하세요.');
      return;
    }
    if (!window.confirm('선택한 소분류를 삭제하시겠습니까?')) return;

    try {
      await api('/csm/core/setting/sub/delete', {
        method: 'POST',
        body: JSON.stringify({ idx: state.selectedSub.idx }),
      });
      state.selectedSub = null;
      await loadSubCategories(state.selectedMain.idx);
      await renderTemplatePreview(state.templateIdx);
    } catch (e) {
      alert(`소분류 삭제 실패: ${e.message}`);
    }
  }

  async function onDeleteOption() {
    if (!state.selectedOption) {
      alert('삭제할 옵션을 선택하세요.');
      return;
    }
    if (!window.confirm('선택한 옵션을 삭제하시겠습니까?')) return;

    try {
      await api('/csm/core/setting/option/delete', {
        method: 'POST',
        body: JSON.stringify({ idx: state.selectedOption.idx }),
      });
      state.selectedOption = null;
      await loadOptions(state.selectedSub.idx);
      await renderTemplatePreview(state.templateIdx);
    } catch (e) {
      alert(`옵션 삭제 실패: ${e.message}`);
    }
  }

  function collectSortRows() {
    const rows = [];

    const mainItems = Array.from(document.querySelectorAll('#editor-main-list .editor-item'));
    mainItems.forEach((node, idx) => rows.push({ type: 'main', id: Number(node.dataset.id), order: idx + 1 }));

    const subItems = Array.from(document.querySelectorAll('#editor-sub-list .editor-item'));
    subItems.forEach((node, idx) => rows.push({ type: 'sub', id: Number(node.dataset.id), order: idx + 1 }));

    const optionItems = Array.from(document.querySelectorAll('#editor-option-list .editor-item'));
    optionItems.forEach((node, idx) => rows.push({ type: 'option', id: Number(node.dataset.id), order: idx + 1 }));

    return rows.filter((r) => r.id > 0);
  }

  async function saveCategoryOrder() {
    const rows = collectSortRows();
    if (!rows.length) return;
    await api('/csm/core/setting/saveCategoryOrder', {
      method: 'POST',
      body: JSON.stringify(rows),
    });
    state.sortDirty = false;
  }

  function openEditor() {
    if (!state.templateIdx) {
      alert('템플릿을 먼저 선택하세요.');
      return;
    }
    const overlay = document.getElementById('category-editor-overlay');
    if (!overlay) return;
    overlay.classList.add('show');
    loadMainCategories().catch((e) => alert(`카테고리 로딩 실패: ${e.message}`));
  }

  function closeEditor() {
    const overlay = document.getElementById('category-editor-overlay');
    if (!overlay) return;
    overlay.classList.remove('show');
  }

  function bindTypeChips() {
    document.querySelectorAll('.type-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        chip.classList.toggle('active');
      });
    });
  }

  function bindTemplateRows() {
    const rows = Array.from(document.querySelectorAll('.grid-body4 .grid-row4[data-template-idx]'));
    rows.forEach((row) => {
      row.style.cursor = 'pointer';
      row.addEventListener('click', async () => {
        rows.forEach((r) => r.classList.remove('selected-row'));
        row.classList.add('selected-row');

        state.templateIdx = Number(row.dataset.templateIdx || '0');
        state.templateName = (row.children[0]?.textContent || '').trim();

        const title = document.getElementById('preview-template-title');
        if (title) title.textContent = state.templateName || '템플릿을 선택하세요';

        if (!state.templateIdx) return;
        try {
          await renderTemplatePreview(state.templateIdx);
        } catch (e) {
          alert(`템플릿 미리보기 로딩 실패: ${e.message}`);
        }
      });
    });

    if (rows.length > 0) rows[0].click();
  }

  function bindApplyButton() {
    const btn = document.getElementById('template-apply-btn');
    if (!btn) return;
    btn.addEventListener('click', () => {
      if (!state.templateIdx) {
        alert('템플릿을 먼저 선택하세요.');
        return;
      }
      alert(`선택된 템플릿: ${state.templateName || state.templateIdx}`);
    });
  }

  function bindEditorEvents() {
    document.getElementById('open-category-editor')?.addEventListener('click', openEditor);

    document.getElementById('editor-main-add')?.addEventListener('click', () => openFormModal({ mode: 'add', type: 'main' }));
    document.getElementById('editor-main-del')?.addEventListener('click', onDeleteMain);

    document.getElementById('editor-sub-add')?.addEventListener('click', () => {
      if (!state.selectedMain) {
        alert('대분류를 먼저 선택하세요.');
        return;
      }
      openFormModal({ mode: 'add', type: 'sub' });
    });
    document.getElementById('editor-sub-del')?.addEventListener('click', onDeleteSub);

    document.getElementById('editor-option-add')?.addEventListener('click', () => {
      if (!state.selectedSub || !isSelectSub(state.selectedSub)) {
        alert('select 타입 소분류를 먼저 선택하세요.');
        return;
      }
      openFormModal({ mode: 'add', type: 'option' });
    });
    document.getElementById('editor-option-del')?.addEventListener('click', onDeleteOption);

    document.getElementById('editor-cancel')?.addEventListener('click', closeEditor);
    document.getElementById('editor-save')?.addEventListener('click', async () => {
      try {
        if (state.sortDirty) {
          await saveCategoryOrder();
        }
        await renderTemplatePreview(state.templateIdx);
        closeEditor();
      } catch (e) {
        alert(`저장 실패: ${e.message}`);
      }
    });

    const overlay = document.getElementById('category-editor-overlay');
    overlay?.addEventListener('click', (e) => {
      if (e.target === overlay) closeEditor();
    });

    document.getElementById('editor-form-cancel')?.addEventListener('click', closeFormModal);
    document.getElementById('editor-form-submit')?.addEventListener('click', submitFormModal);

    const formOverlay = document.getElementById('editor-form-overlay');
    formOverlay?.addEventListener('click', (e) => {
      if (e.target === formOverlay) closeFormModal();
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    bindTemplateRows();
    bindApplyButton();
    bindEditorEvents();
    bindTypeChips();
  });
})();
