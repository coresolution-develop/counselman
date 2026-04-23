/* ── 사용자 관리 — admin.js ── */

/* ─── 팝업 / CRUD (전역) ─── */
function openUserPopup() {
  const w = 500, h = 750;
  const left = (screen.width  / 2) - (w / 2);
  const top  = (screen.height / 2) - (h / 2);
  window.open('/csm/newuserPopup', 'popupWindow',
    `width=${w},height=${h},top=${top},left=${left},scrollbars=yes,resizable=yes`);
}

function userModify(id, instCode) {
  const w = 500, h = 750;
  const left = (screen.width  / 2) - (w / 2);
  const top  = (screen.height / 2) - (h / 2);
  window.open(`/csm/modifyuserPopup?us_col_01=${id}&instCode=${instCode}`, 'popupWindow',
    `width=${w},height=${h},top=${top},left=${left},scrollbars=yes`);
}

function userDelete(id, instCode) {
  $.ajax({
    url: '/csm/user/delete',
    type: 'post',
    dataType: 'json',
    data: { us_col_01: id, us_col_04: instCode },
    success: () => window.location.reload(),
    error: (e) => console.error('Delete error:', e),
  });
}

/* 인라인 버튼 핸들러 */
function handleEditUser(btn) {
  const { id, inst } = btn.dataset;
  if (!id) { alert('유효한 사용자를 선택해 주세요.'); return; }
  userModify(id, inst);
}
function handleDeleteUser(btn) {
  const { id, inst } = btn.dataset;
  if (!id) { alert('유효한 사용자를 선택해 주세요.'); return; }
  if (confirm('사용자를 삭제하시겠습니까?')) userDelete(id, inst);
}

/* ─── Alpine 컴포넌트 ─── */
document.addEventListener('alpine:init', () => {
  Alpine.data('userManager', () => ({
    search:        '',
    statusFilter:  '',
    totalCount:    0,
    activeCount:   0,
    inactiveCount: 0,
    visibleCount:  0,

    init() {
      const rows = document.querySelectorAll('#admin-tab-users .um-row[data-name]');
      this.totalCount   = rows.length;
      this.activeCount  = document.querySelectorAll('#admin-tab-users .um-row[data-status="y"]').length;
      this.inactiveCount = this.totalCount - this.activeCount;
      this.visibleCount  = this.totalCount;

      /* 아바타 색상 */
      const palette = ['#059669','#10b981','#0ea5e9','#8b5cf6','#ec4899','#f59e0b','#14b8a6','#ef4444'];
      rows.forEach(row => {
        const av = row.querySelector('.um-avatar');
        if (!av) return;
        const name = av.dataset.name || '';
        const idx  = [...name].reduce((s, c) => s + c.charCodeAt(0), 0) % palette.length;
        av.style.background = palette[idx];
      });

      this.$watch('search',       () => this._updateVisible());
      this.$watch('statusFilter', () => this._updateVisible());
    },

    _updateVisible() {
      this.$nextTick(() => {
        let n = 0;
        document.querySelectorAll('#admin-tab-users .um-row[data-name]').forEach(el => {
          if (el.style.display !== 'none') n++;
        });
        this.visibleCount = n;
      });
    },

    matchesRow(name, empid, email, status) {
      const q = this.search.trim().toLowerCase();
      const matchQ = !q ||
        name.toLowerCase().includes(q)  ||
        empid.toLowerCase().includes(q) ||
        email.toLowerCase().includes(q);
      const matchS = !this.statusFilter || status === this.statusFilter;
      return matchQ && matchS;
    },
  }));
});
