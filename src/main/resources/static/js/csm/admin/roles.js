/* ── 역할 관리 — Alpine.js v3 ── */

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
    dirty:         false,
    search:        '',
    editingMeta:   false,
    _origPerms:    [],

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
          map.set(p.menu_key, { menu_key: p.menu_key, label: p.menu_label, perms: [] });
        map.get(p.menu_key).perms.push(p);
      }
      return Array.from(map.values());
    },
    get totalPerms() {
      return this.permMaster.length;
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

    /* ── 아이콘 헬퍼 ── */
    _roleIcon(role) {
      if (!role) return 'key';
      if (role.is_system) return 'shield';
      const code = (role.role_code || '').toLowerCase();
      if (code.includes('counsel') || code.includes('상담')) return 'headset';
      if (code.includes('intake') || code.includes('접수') || code.includes('reception')) return 'clipboard';
      if (code.includes('ward') || code.includes('bed') || code.includes('병실')) return 'bed';
      if (code.includes('review') || code.includes('viewer')) return 'eye';
      if (code.includes('message') || code.includes('sms') || code.includes('문자')) return 'message';
      if (code.includes('admin') || code.includes('manage') || code.includes('관리')) return 'building';
      return 'key';
    },
    _permIcon(perm) {
      const action = (perm.action || '').toUpperCase();
      if (action.includes('READ') || action.includes('VIEW') || action.includes('LIST')) return 'eye';
      if (action.includes('CREATE') || action.includes('ADD') || action.includes('WRITE')) return 'plus';
      if (action.includes('EDIT') || action.includes('UPDATE') || action.includes('MODIFY')) return 'edit';
      if (action.includes('DELETE') || action.includes('REMOVE')) return 'trash';
      if (action.includes('ASSIGN') || action.includes('ROLE')) return 'users';
      if (action.includes('SEND') || action.includes('MESSAGE')) return 'message';
      return 'sliders';
    },
    _userColor(userId) {
      const palette = ['#059669','#10b981','#0ea5e9','#8b5cf6','#ec4899','#f59e0b','#14b8a6','#475569'];
      return palette[Math.abs(Number(userId) || 0) % palette.length];
    },

    /* ── 목록 로드 ── */
    async loadList() {
      this.listLoading = true;
      try {
        const r = await fetch(this._api('/api/roles'));
        this.roles = r.ok ? await r.json() : [];
      } catch { this.roles = []; }
      finally { this.listLoading = false; }
    },

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
      this.dirty = false;
      this.editingMeta = false;

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
      this._origPerms = [...this.selectedPerms];

      const role = this.selectedRole;
      this.isSystem = !!(role?.is_system);
      this.editName = role?.role_name ?? '';
      this.editDesc = role?.description ?? '';
    },

    /* ── 권한 토글 ── */
    hasPerm(code) { return this.selectedPerms.includes(code); },
    togglePerm(code) {
      if (this.isSystem) return;
      const i = this.selectedPerms.indexOf(code);
      i >= 0 ? this.selectedPerms.splice(i, 1) : this.selectedPerms.push(code);
      this.dirty = true;
    },

    /* ── 그룹 전체 선택/해제 ── */
    filteredPerms(group) {
      const q = this.search.trim().toLowerCase();
      if (!q) return group.perms;
      return group.perms.filter(p =>
        (p.label_ko || p.action || '').toLowerCase().includes(q) ||
        (p.resource || '').toLowerCase().includes(q) ||
        (p.menu_label || '').toLowerCase().includes(q)
      );
    },
    groupCount(menuKey) {
      const g = this.permMenus.find(x => x.menu_key === menuKey);
      if (!g) return 0;
      return g.perms.filter(p => this.hasPerm(p.code)).length;
    },
    toggleGroup(menuKey) {
      if (this.isSystem) return;
      const g = this.permMenus.find(x => x.menu_key === menuKey);
      if (!g) return;
      const allOn = g.perms.every(p => this.hasPerm(p.code));
      if (allOn) {
        g.perms.forEach(p => {
          const i = this.selectedPerms.indexOf(p.code);
          if (i >= 0) this.selectedPerms.splice(i, 1);
        });
      } else {
        g.perms.forEach(p => {
          if (!this.selectedPerms.includes(p.code)) this.selectedPerms.push(p.code);
        });
      }
      this.dirty = true;
    },

    /* ── 되돌리기 ── */
    async revertChanges() {
      this.selectedPerms = [...this._origPerms];
      const role = this.selectedRole;
      this.editName = role?.role_name ?? '';
      this.editDesc = role?.description ?? '';
      this.editingMeta = false;
      this.dirty = false;
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

        this._origPerms = [...this.selectedPerms];
        this.dirty = false;
        this.editingMeta = false;
        await this.loadList();
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
        this.dirty = false;
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
