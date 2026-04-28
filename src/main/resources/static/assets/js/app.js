/* ============================================
   App controller – Alpine.js stores + helpers
   ============================================ */

document.addEventListener('alpine:init', () => {

  Alpine.store('app', {
    sidebarCollapsed: JSON.parse(localStorage.getItem('mp.sidebarCollapsed') || 'false'),
    variation: localStorage.getItem('mp.variation') || 'default',
    layout: localStorage.getItem('mp.layout') || 'grid',
    tweaksOpen: false,

    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
      localStorage.setItem('mp.sidebarCollapsed', JSON.stringify(this.sidebarCollapsed));
    },
    setVariation(v) {
      this.variation = v;
      localStorage.setItem('mp.variation', v);
    },
    setLayout(l) {
      this.layout = l;
      localStorage.setItem('mp.layout', l);
    },
  });

  Alpine.data('roleManager', () => ({
    activeRole: 'counselor',
    search: '',
    dirty: false,
    roles: [
      { id: 'institution', name: '기관 관리자', meta: '사용자 1명', icon: 'building', desc: '기관 전체를 총괄하는 최고 관리자 역할', selected: ['reception','hospitalization','list','notice','statistics','records','message','reservation','admin','institution','users','roles'], users: [{id:'admin', name:'관리자', color:'#059669'}] },
      { id: 'counselor', name: '상담사', meta: '사용자 2명', icon: 'headset', desc: '기본 상담사 권한 세트 – 상담 접수, 리스트, 문자 발송 권한 포함', selected: ['reception','list','notice','statistics','records','message','reservation'], users: [{id:'hyunjeong', name:'김현정', color:'#10b981'}, {id:'jihye', name:'박지혜', color:'#0ea5e9'}] },
      { id: 'intake',    name: '접수 담당', meta: '사용자 1명', icon: 'clipboard', desc: '신규 상담 접수 및 초기 배정을 담당', selected: ['reception','list','message'], users: [{id:'minsoo', name:'이민수', color:'#f59e0b'}] },
      { id: 'staff',     name: '법인 관리자', meta: '사용자 2명', icon: 'briefcase', desc: '법인 차원에서 운영 데이터를 조회 및 관리', selected: ['statistics','records','reservation','institution'], users: [{id:'junwon', name:'최준원', color:'#8b5cf6'}, {id:'soyeon', name:'정소연', color:'#ec4899'}] },
      { id: 'reviewer',  name: '리뷰어', meta: '사용자 3명', icon: 'eye', desc: '상담 기록 검토 전용 – 읽기 전용 권한', selected: ['list','notice','records'], users: [{id:'r1', name:'리뷰어1', color:'#64748b'},{id:'r2', name:'리뷰어2', color:'#64748b'},{id:'r3', name:'리뷰어3', color:'#64748b'}] },
      { id: 'messenger', name: '문자 담당', meta: '사용자 1명', icon: 'message', desc: '문자 발송 및 템플릿 관리 전담', selected: ['message','notice'], users: [{id:'yuna', name:'한유나', color:'#14b8a6'}] },
    ],

    groups: [
      { id: 'consult', label: '상담 업무', items: [
        { id: 'reception', name: '상담 접수', desc: '신규 상담 접수 및 배정', icon: 'inbox' },
        { id: 'hospitalization', name: '입원 상담', desc: '입원 관련 상담 관리', icon: 'bed' },
        { id: 'list', name: '상담 리스트', desc: '전체 상담 이력 조회', icon: 'list' },
        { id: 'statistics', name: '상담 통계', desc: '상담 KPI 및 리포트', icon: 'chart' },
      ]},
      { id: 'comms', label: '커뮤니케이션', items: [
        { id: 'notice', name: '공지사항', desc: '내부 공지 작성 · 조회', icon: 'megaphone' },
        { id: 'message', name: '문자관리', desc: '문자 발송 · 템플릿', icon: 'chat' },
        { id: 'reservation', name: '방문예약관리', desc: '방문 예약 생성 · 조회', icon: 'calendar' },
      ]},
      { id: 'admin', label: '시스템 관리', items: [
        { id: 'admin', name: '관리자', desc: '관리자 전용 메뉴 접근', icon: 'shield' },
        { id: 'records', name: '상담일지 관리', desc: '상담 일지 항목 설정', icon: 'notebook' },
        { id: 'institution', name: '기관 관리', desc: '소속 기관 정보 관리', icon: 'building' },
        { id: 'users', name: '사용자 관리', desc: '계정 생성 · 삭제 · 역할 배정', icon: 'users' },
        { id: 'roles', name: '역할 관리', desc: '권한 역할 생성 · 수정', icon: 'key' },
      ]},
    ],

    get current() { return this.roles.find(r => r.id === this.activeRole); },
    get totalPerms() { return this.groups.reduce((a,g) => a + g.items.length, 0); },

    has(permId) { return this.current?.selected.includes(permId); },
    toggle(permId) {
      const r = this.current; if (!r) return;
      const i = r.selected.indexOf(permId);
      if (i === -1) r.selected.push(permId); else r.selected.splice(i, 1);
      this.dirty = true;
    },
    toggleGroup(groupId) {
      const g = this.groups.find(x => x.id === groupId); if (!g) return;
      const ids = g.items.map(i => i.id);
      const allOn = ids.every(id => this.has(id));
      const r = this.current;
      if (allOn) r.selected = r.selected.filter(x => !ids.includes(x));
      else ids.forEach(id => { if (!r.selected.includes(id)) r.selected.push(id); });
      this.dirty = true;
    },
    groupCount(groupId) {
      const g = this.groups.find(x => x.id === groupId); if (!g) return 0;
      return g.items.filter(i => this.has(i.id)).length;
    },
    selectRole(id) {
      if (this.activeRole === id) return;
      if (document.startViewTransition) {
        document.startViewTransition(() => { this.activeRole = id; this.dirty = false; });
      } else {
        this.activeRole = id; this.dirty = false;
      }
    },
    save() { this.dirty = false; this.$dispatch('toast', { message: '변경사항이 저장되었습니다' }); },
    filteredItems(group) {
      const q = this.search.trim().toLowerCase(); if (!q) return group.items;
      return group.items.filter(i => i.name.toLowerCase().includes(q) || i.desc.toLowerCase().includes(q));
    },
  }));
});

/* Tweaks availability handshake */
window.addEventListener('message', (e) => {
  const msg = e.data || {};
  if (msg.type === '__activate_edit_mode') {
    document.documentElement.dataset.tweaks = 'on';
    document.querySelector('.app')?.setAttribute('data-tweaks', 'on');
  } else if (msg.type === '__deactivate_edit_mode') {
    document.documentElement.dataset.tweaks = 'off';
    document.querySelector('.app')?.setAttribute('data-tweaks', 'off');
  }
});
window.parent.postMessage({ type: '__edit_mode_available' }, '*');
