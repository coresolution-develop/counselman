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

    /* 메뉴 단위 가상 아이템 맵 (admin은 resource별 분리) */
    get _menuItems() {
      const MENU_META = {
        counsel_reservation: { desc: '신규 상담 접수 및 배정',   icon: 'clipboard' },
        counsel_write:       { desc: '입원 관련 상담 관리',      icon: 'bed'       },
        counsel_list:        { desc: '전체 상담 이력 조회',      icon: 'list'      },
        counsel_log:         { desc: '상담 일지 작성·수정',      icon: 'edit'      },
        stats:               { desc: '상담 KPI 및 리포트',       icon: 'chart'     },
        notice:              { desc: '내부 공지 작성·조회',      icon: 'bell'      },
        sms:                 { desc: '문자 발송·템플릿',         icon: 'message'   },
        room_board:          { desc: '병실 현황 조회·관리',      icon: 'building'  },
        admission:           { desc: '방문/입원 예약 생성·조회', icon: 'calendar'  },
      };
      const ADMIN_ITEMS = {
        '__admin_USER': { label: '사용자 관리', desc: '계정 생성·삭제·역할 배정', icon: 'users'   },
        '__admin_ROLE': { label: '역할 관리',   desc: '권한 역할 생성·수정',      icon: 'key'     },
        '__admin_SYS':  { label: '시스템 설정', desc: '설정 및 카테고리 관리',    icon: 'sliders' },
      };
      const map = new Map();
      for (const p of this.permMaster) {
        if (p.menu_key === 'admin') {
          const vkey = p.resource === 'USER' ? '__admin_USER'
                     : p.resource === 'ROLE' ? '__admin_ROLE'
                     : '__admin_SYS';
          if (!map.has(vkey)) map.set(vkey, { menu_key: vkey, ...ADMIN_ITEMS[vkey], perms: [] });
          map.get(vkey).perms.push(p);
        } else {
          if (!map.has(p.menu_key)) {
            const meta = MENU_META[p.menu_key] || {};
            map.set(p.menu_key, { menu_key: p.menu_key, label: p.menu_label, desc: meta.desc || '', icon: meta.icon || 'sliders', perms: [] });
          }
          map.get(p.menu_key).perms.push(p);
        }
      }
      return map;
    },

    /* 3개 카테고리 × 메뉴 아이템 */
    get permGroups() {
      const items = this._menuItems;
      return [
        { id: 'consult', label: '상담 업무',    keys: ['counsel_reservation','counsel_write','counsel_list','stats'] },
        { id: 'comms',   label: '커뮤니케이션', keys: ['notice','sms','room_board','admission'] },
        { id: 'admin',   label: '시스템 관리',  keys: ['__admin_USER','__admin_ROLE','counsel_log','__admin_SYS'] },
      ].map(cat => ({ ...cat, items: cat.keys.map(k => items.get(k)).filter(Boolean) }));
    },

    get totalMenus() { return this._menuItems.size; },
    get selectedMenuCount() {
      let n = 0;
      for (const m of this._menuItems.values()) {
        if (m.perms.length > 0 && m.perms.every(p => this.selectedPerms.includes(p.code))) n++;
      }
      return n;
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

    /* ── 권한 체크 (개별, 저장/로드용) ── */
    hasPerm(code) { return this.selectedPerms.includes(code); },
    togglePerm(code) {
      if (this.isSystem) return;
      const i = this.selectedPerms.indexOf(code);
      i >= 0 ? this.selectedPerms.splice(i, 1) : this.selectedPerms.push(code);
      this.dirty = true;
    },

    /* ── 메뉴 단위 토글 ── */
    hasMenu(menuKey) {
      const m = this._menuItems.get(menuKey);
      return m ? m.perms.length > 0 && m.perms.every(p => this.selectedPerms.includes(p.code)) : false;
    },
    menuPartial(menuKey) {
      const m = this._menuItems.get(menuKey);
      if (!m || m.perms.length === 0) return false;
      const some = m.perms.some(p => this.selectedPerms.includes(p.code));
      const all  = m.perms.every(p => this.selectedPerms.includes(p.code));
      return some && !all;
    },
    toggleMenu(menuKey) {
      if (this.isSystem) return;
      const m = this._menuItems.get(menuKey);
      if (!m) return;
      const allOn = m.perms.every(p => this.selectedPerms.includes(p.code));
      if (allOn) {
        m.perms.forEach(p => {
          const i = this.selectedPerms.indexOf(p.code);
          if (i >= 0) this.selectedPerms.splice(i, 1);
        });
      } else {
        m.perms.forEach(p => {
          if (!this.selectedPerms.includes(p.code)) this.selectedPerms.push(p.code);
        });
      }
      this.dirty = true;
    },

    /* ── 카테고리 필터/전체 토글 ── */
    filteredMenus(cat) {
      const q = this.search.trim().toLowerCase();
      if (!q) return cat.items;
      return cat.items.filter(item =>
        item.label.toLowerCase().includes(q) ||
        item.desc.toLowerCase().includes(q)
      );
    },
    categoryCount(catId) {
      const cat = this.permGroups.find(g => g.id === catId);
      if (!cat) return 0;
      return cat.items.filter(m => this.hasMenu(m.menu_key)).length;
    },
    toggleCategory(catId) {
      if (this.isSystem) return;
      const cat = this.permGroups.find(g => g.id === catId);
      if (!cat) return;
      const allOn = cat.items.every(m => this.hasMenu(m.menu_key));
      cat.items.forEach(m => {
        const item = this._menuItems.get(m.menu_key);
        if (!item) return;
        if (allOn) {
          item.perms.forEach(p => {
            const i = this.selectedPerms.indexOf(p.code);
            if (i >= 0) this.selectedPerms.splice(i, 1);
          });
        } else {
          item.perms.forEach(p => {
            if (!this.selectedPerms.includes(p.code)) this.selectedPerms.push(p.code);
          });
        }
      });
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
