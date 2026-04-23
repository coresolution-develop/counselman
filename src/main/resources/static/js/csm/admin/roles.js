/* ── 역할 관리 탭 ── */

// 탭 전환
function switchAdminTab(tabId, btn) {
  document.querySelectorAll('.admin-tab-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.admin-tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('admin-tab-' + tabId).classList.add('active');
  btn.classList.add('active');
  if (tabId === 'roles') rolesLoadList();
}

// ── 상태 ──
let rolesState = {
  list: [],           // 역할 목록
  permMaster: [],     // 전체 권한 마스터
  selectedId: null,   // 현재 선택된 role_id
  selectedPerms: [],  // 현재 선택된 역할의 권한 코드 목록
  isSystem: false,
};

// ── 역할 목록 로드 ──
async function rolesLoadList() {
  try {
    const res = await fetch(ctxPath('/api/roles'));
    if (!res.ok) return;
    rolesState.list = await res.json();
    rolesRenderList();
  } catch (e) {
    console.error('roles list load failed', e);
  }
}

function rolesRenderList() {
  const body = document.getElementById('roles-list-body');
  if (!body) return;
  if (!rolesState.list.length) {
    body.innerHTML = '<div class="roles-empty">역할이 없습니다.</div>';
    return;
  }
  body.innerHTML = rolesState.list.map(r => `
    <div class="role-item${r.role_id == rolesState.selectedId ? ' selected' : ''}"
         onclick="rolesSelectRole(${r.role_id})">
      <span class="role-item-lock">${r.is_system ? '🔒' : '&#x1F464;'}</span>
      <div class="role-item-info">
        <div class="role-item-name" title="${escHtml(r.role_name)}">${escHtml(r.role_name)}</div>
        <div class="role-item-users">사용자 ${r.user_count}명</div>
      </div>
    </div>`).join('');
}

// ── 역할 선택 → 편집 패널 표시 ──
async function rolesSelectRole(roleId) {
  rolesState.selectedId = roleId;
  rolesRenderList();

  // 권한 마스터 lazy-load
  if (!rolesState.permMaster.length) {
    try {
      const res = await fetch(ctxPath('/api/roles/permission-master'));
      rolesState.permMaster = await res.json();
    } catch (e) {
      console.error('perm-master load failed', e);
    }
  }

  // 해당 역할 권한 조회
  let rolePerms = [];
  try {
    const res = await fetch(ctxPath(`/api/roles/${roleId}/permissions`));
    rolePerms = await res.json();
  } catch (e) {
    console.error('role perms load failed', e);
  }
  rolesState.selectedPerms = rolePerms;

  const role = rolesState.list.find(r => r.role_id == roleId);
  rolesState.isSystem = role && role.is_system == 1;

  // 편집 폼 렌더링
  document.getElementById('roles-edit-placeholder') && (document.querySelector('.roles-edit-placeholder').style.display = 'none');
  const form = document.getElementById('roles-edit-form');
  form.style.display = 'block';

  const nameInput = document.getElementById('re-role-name');
  const descInput = document.getElementById('re-role-desc');
  nameInput.value = role ? role.role_name : '';
  descInput.value = role ? (role.description || '') : '';
  nameInput.readOnly = rolesState.isSystem;
  descInput.readOnly = rolesState.isSystem;

  const sysBadge = document.getElementById('re-system-badge');
  sysBadge.style.display = rolesState.isSystem ? 'inline-flex' : 'none';

  const saveBtn = document.getElementById('re-save-btn');
  const delBtn = document.getElementById('re-del-btn');
  saveBtn.disabled = rolesState.isSystem;
  delBtn.disabled = rolesState.isSystem;

  rolesRenderPermMatrix(rolePerms);
}

// ── 권한 매트릭스 렌더링 ──
function rolesRenderPermMatrix(checkedCodes) {
  const matrix = document.getElementById('re-perm-matrix');
  if (!matrix) return;

  // 메뉴별 그룹화
  const menuMap = {};
  const menuOrder = [];
  for (const p of rolesState.permMaster) {
    if (!menuMap[p.menu_key]) {
      menuMap[p.menu_key] = { label: p.menu_label, perms: [] };
      menuOrder.push(p.menu_key);
    }
    menuMap[p.menu_key].perms.push(p);
  }

  const checkedSet = new Set(checkedCodes);
  const disabled = rolesState.isSystem ? 'disabled' : '';

  matrix.innerHTML = menuOrder.map(menuKey => {
    const menu = menuMap[menuKey];
    const actions = menu.perms.map(p => {
      const checked = checkedSet.has(p.code) ? 'checked' : '';
      return `<label class="perm-action-item${disabled ? ' is-disabled' : ''}">
        <input type="checkbox" data-code="${p.code}" ${checked} ${disabled}>
        <span>${escHtml(p.action)}</span>
      </label>`;
    }).join('');
    return `<div class="perm-menu-card">
      <div class="perm-menu-title">${escHtml(menu.label)}</div>
      <div class="perm-actions">${actions}</div>
    </div>`;
  }).join('');
}

// ── 권한 저장 ──
async function rolesSavePermissions() {
  const roleId = rolesState.selectedId;
  if (!roleId) return;

  const name = document.getElementById('re-role-name').value.trim();
  const desc = document.getElementById('re-role-desc').value.trim();
  if (!name) { alert('역할 이름을 입력하세요.'); return; }

  // 이름/설명 저장
  try {
    const r = await fetch(ctxPath(`/api/roles/${roleId}`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role_name: name, description: desc }),
    });
    if (!r.ok) { const e = await r.json(); alert(e.error || '저장 실패'); return; }
  } catch (e) {
    alert('저장 중 오류: ' + e); return;
  }

  // 권한 저장
  const codes = Array.from(document.querySelectorAll('#re-perm-matrix input[type=checkbox]:checked'))
    .map(cb => cb.dataset.code);
  try {
    const r = await fetch(ctxPath(`/api/roles/${roleId}/permissions`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ permissions: codes }),
    });
    if (!r.ok) { const e = await r.json(); alert(e.error || '권한 저장 실패'); return; }
  } catch (e) {
    alert('권한 저장 중 오류: ' + e); return;
  }

  await rolesLoadList();
  alert('저장되었습니다.');
}

// ── 역할 삭제 ──
async function rolesDeleteRole() {
  const roleId = rolesState.selectedId;
  if (!roleId) return;
  const role = rolesState.list.find(r => r.role_id == roleId);
  if (!confirm(`"${role ? role.role_name : roleId}" 역할을 삭제하시겠습니까?`)) return;

  try {
    const r = await fetch(ctxPath(`/api/roles/${roleId}`), { method: 'DELETE' });
    if (!r.ok) { const e = await r.json(); alert(e.error || '삭제 실패'); return; }
  } catch (e) {
    alert('삭제 중 오류: ' + e); return;
  }

  rolesState.selectedId = null;
  document.getElementById('roles-edit-form').style.display = 'none';
  document.querySelector('.roles-edit-placeholder').style.display = '';
  await rolesLoadList();
}

// ── 역할 복사 ──
async function rolesCopyRole() {
  const roleId = rolesState.selectedId;
  if (!roleId) return;
  const src = rolesState.list.find(r => r.role_id == roleId);
  const newName = prompt('복사할 역할 이름', (src ? src.role_name : '') + ' (복사)');
  if (!newName || !newName.trim()) return;

  try {
    const r = await fetch(ctxPath(`/api/roles/${roleId}/copy`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role_name: newName.trim() }),
    });
    if (!r.ok) { const e = await r.json(); alert(e.error || '복사 실패'); return; }
    const data = await r.json();
    await rolesLoadList();
    rolesSelectRole(data.role_id);
  } catch (e) {
    alert('복사 중 오류: ' + e);
  }
}

// ── 새 역할 생성 모달 ──
function rolesOpenCreate() {
  const overlay = document.createElement('div');
  overlay.className = 'roles-create-modal-overlay';
  overlay.innerHTML = `
    <div class="roles-create-modal">
      <h3>새 역할 만들기</h3>
      <label>역할 이름 <span style="color:red">*</span></label>
      <input id="rcm-name" type="text" placeholder="예: 야간 상담사" maxlength="100" autofocus />
      <label>설명</label>
      <textarea id="rcm-desc" rows="2" placeholder="이 역할에 대한 간단한 설명"></textarea>
      <div class="roles-create-modal-btns">
        <button class="roles-modal-cancel" onclick="this.closest('.roles-create-modal-overlay').remove()">취소</button>
        <button class="roles-modal-confirm" onclick="rolesCreateSubmit(this)">만들기</button>
      </div>
    </div>`;
  document.body.appendChild(overlay);
  overlay.querySelector('#rcm-name').focus();
}

async function rolesCreateSubmit(btn) {
  const overlay = btn.closest('.roles-create-modal-overlay');
  const name = overlay.querySelector('#rcm-name').value.trim();
  const desc = overlay.querySelector('#rcm-desc').value.trim();
  if (!name) { alert('역할 이름을 입력하세요.'); return; }

  try {
    const r = await fetch(ctxPath('/api/roles'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role_name: name, description: desc }),
    });
    if (!r.ok) { const e = await r.json(); alert(e.error || '생성 실패'); return; }
    const data = await r.json();
    overlay.remove();
    await rolesLoadList();
    rolesSelectRole(data.role_id);
  } catch (e) {
    alert('생성 중 오류: ' + e);
  }
}

// ── 유틸 ──
function ctxPath(path) {
  const base = (document.querySelector('meta[name="ctx-path"]')?.content || '').replace(/\/$/, '');
  return base + path;
}

function escHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
