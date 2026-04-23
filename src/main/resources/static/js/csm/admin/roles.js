/* ── 역할 관리 — Alpine.js v3 컴포넌트 ── */

// 관리자 탭 전환 (사용자 관리 탭과 공유)
function switchAdminTab(tabId, btn) {
  document.querySelectorAll('.admin-tab-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.admin-tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('admin-tab-' + tabId)?.classList.add('active');
  btn.classList.add('active');
}

document.addEventListener('alpine:init', () => {
  Alpine.data('rolesManager', () => ({

    /* ── 상태 ── */
    roles:         [],
    permMaster:    [],
    selectedId:    null,
    selectedPerms: [],
    isSystem:      false,
    editName:      '',
    editDesc:      '',
    listLoading:   true,
    saving:        false,
    showModal:     false,
    newName:       '',
    newDesc:       '',
    roleUsers:     [],
    allUsers:      [],
    addUserId:     '',

    /* ── 계산 속성 ── */
    get selectedRole() {
      return this.roles.find(r => r.role_id == this.selectedId) ?? null;
    },
    get availableUsers() {
      const assignedIds = new Set(this.roleUsers.map(u => u.user_id));
      return this.allUsers.filter(u => !assignedIds.has(u.user_id));
    },
    get permMenus() {
      const map = new Map();
      for (const p of this.permMaster) {
        if (!map.has(p.menu_key))
          map.set(p.menu_key, { label: p.menu_label, perms: [] });
        map.get(p.menu_key).perms.push(p);
      }
      return Array.from(map.values());
    },

    /* ── 초기화 ── */
    async init() {
      await Promise.all([this.loadList(), this.loadAllUsers()]);
    },

    /* ── URL 유틸 ── */
    _api(path) {
      const base = (document.querySelector('meta[name="ctx-path"]')?.content ?? '').replace(/\/$/, '');
      return base + path;
    },

    /* ── 역할 목록 로드 ── */
    async loadList() {
      this.listLoading = true;
      try {
        const r = await fetch(this._api('/api/roles'));
        this.roles = r.ok ? await r.json() : [];
      } catch { this.roles = []; }
      finally { this.listLoading = false; }
    },

    /* ── 전체 사용자 로드 ── */
    async loadAllUsers() {
      try {
        const r = await fetch(this._api('/api/roles/users'));
        this.allUsers = r.ok ? await r.json() : [];
      } catch { this.allUsers = []; }
    },

    /* ── 역할 선택 ── */
    async selectRole(roleId) {
      this.selectedId = roleId;
      this.addUserId = '';

      if (!this.permMaster.length) {
        try {
          const r = await fetch(this._api('/api/roles/permission-master'));
          this.permMaster = r.ok ? await r.json() : [];
        } catch { this.permMaster = []; }
      }

      const [permsRes, usersRes] = await Promise.allSettled([
        fetch(this._api(`/api/roles/${roleId}/permissions`)),
        fetch(this._api(`/api/roles/${roleId}/users`)),
      ]);
      this.selectedPerms = permsRes.status === 'fulfilled' && permsRes.value.ok
        ? await permsRes.value.json() : [];
      this.roleUsers = usersRes.status === 'fulfilled' && usersRes.value.ok
        ? await usersRes.value.json() : [];

      const role = this.selectedRole;
      this.isSystem = !!(role?.is_system);
      this.editName  = role?.role_name ?? '';
      this.editDesc  = role?.description ?? '';
    },

    /* ── 권한 토글 ── */
    hasPerm(code) { return this.selectedPerms.includes(code); },
    togglePerm(code) {
      if (this.isSystem) return;
      const i = this.selectedPerms.indexOf(code);
      i >= 0 ? this.selectedPerms.splice(i, 1) : this.selectedPerms.push(code);
    },

    /* ── 저장 ── */
    async savePermissions() {
      if (!this.selectedId) return;
      if (!this.editName.trim()) { alert('역할 이름을 입력하세요.'); return; }
      this.saving = true;
      try {
        let r = await fetch(this._api(`/api/roles/${this.selectedId}`), {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ role_name: this.editName.trim(), description: this.editDesc.trim() }),
        });
        if (!r.ok) { alert((await r.json()).error ?? '저장 실패'); return; }

        r = await fetch(this._api(`/api/roles/${this.selectedId}/permissions`), {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ permissions: [...this.selectedPerms] }),
        });
        if (!r.ok) { alert((await r.json()).error ?? '권한 저장 실패'); return; }

        await this.loadList();
        alert('저장되었습니다.');
      } catch (e) { alert('오류: ' + e); }
      finally { this.saving = false; }
    },

    /* ── 삭제 ── */
    async deleteRole() {
      if (!this.selectedId) return;
      if (!confirm(`"${this.selectedRole?.role_name ?? ''}" 역할을 삭제하시겠습니까?`)) return;
      try {
        const r = await fetch(this._api(`/api/roles/${this.selectedId}`), { method: 'DELETE' });
        if (!r.ok) { alert((await r.json()).error ?? '삭제 실패'); return; }
        this.selectedId = null;
        await this.loadList();
      } catch (e) { alert('오류: ' + e); }
    },

    /* ── 복사 ── */
    async copyRole() {
      if (!this.selectedId) return;
      const newName = prompt('복사할 역할 이름', `${this.selectedRole?.role_name ?? ''} (복사)`);
      if (!newName?.trim()) return;
      try {
        const r = await fetch(this._api(`/api/roles/${this.selectedId}/copy`), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ role_name: newName.trim() }),
        });
        if (!r.ok) { alert((await r.json()).error ?? '복사 실패'); return; }
        const data = await r.json();
        await this.loadList();
        this.selectRole(data.role_id);
      } catch (e) { alert('오류: ' + e); }
    },

    /* ── 역할 사용자 추가/제거 ── */
    async addUserToRole() {
      if (!this.addUserId || !this.selectedId) return;
      try {
        const r = await fetch(this._api(`/api/roles/user/${this.addUserId}`), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ role_id: Number(this.selectedId) }),
        });
        if (!r.ok) { alert((await r.json()).error ?? '추가 실패'); return; }
        this.addUserId = '';
        const res = await fetch(this._api(`/api/roles/${this.selectedId}/users`));
        this.roleUsers = res.ok ? await res.json() : [];
        await this.loadList();
      } catch (e) { alert('오류: ' + e); }
    },
    async removeUserFromRole(userId) {
      if (!this.selectedId) return;
      const u = this.roleUsers.find(u => u.user_id == userId);
      if (!confirm(`"${u?.user_name ?? userId}" 사용자의 역할을 해제하시겠습니까?`)) return;
      try {
        const r = await fetch(this._api(`/api/roles/user/${userId}/${this.selectedId}`), { method: 'DELETE' });
        if (!r.ok) { alert((await r.json()).error ?? '해제 실패'); return; }
        const res = await fetch(this._api(`/api/roles/${this.selectedId}/users`));
        this.roleUsers = res.ok ? await res.json() : [];
        await this.loadList();
      } catch (e) { alert('오류: ' + e); }
    },

    /* ── 새 역할 모달 ── */
    openModal() {
      this.newName = '';
      this.newDesc = '';
      this.showModal = true;
      this.$nextTick(() => this.$refs.newNameInput?.focus());
    },
    async submitCreate() {
      if (!this.newName.trim()) { alert('역할 이름을 입력하세요.'); return; }
      try {
        const r = await fetch(this._api('/api/roles'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ role_name: this.newName.trim(), description: this.newDesc.trim() }),
        });
        if (!r.ok) { alert((await r.json()).error ?? '생성 실패'); return; }
        const data = await r.json();
        this.showModal = false;
        await this.loadList();
        this.selectRole(data.role_id);
      } catch (e) { alert('오류: ' + e); }
    },
  }));
});
