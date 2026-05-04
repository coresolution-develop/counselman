/* MediPlat · 기관 관리
   Two columns:
   - Left  : 기관 사용자 계정 (add/edit form + scrollable user table)
   - Right : 사용자 기능 권한 (user picker + feature checkboxes)
   Tweaks: layout variant, accent color, compact/comfortable density
*/

const IA_TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "blue",
  "density": "comfortable",
  "showAvatars": true,
  "stickyHeader": true
}/*EDITMODE-END*/;

/* ------------ Accent palette ------------ */
const IA_ACCENTS = {
  blue:   { from: '#0b1f4a', mid: '#173a82', to: '#1d4ed8', tint: '#eff4ff', ring: '#bfdbfe' },
  teal:   { from: '#062f2a', mid: '#0d544a', to: '#0d9488', tint: '#f0fdf9', ring: '#99f6e4' },
  indigo: { from: '#1e1b4b', mid: '#312e81', to: '#4f46e5', tint: '#eef2ff', ring: '#c7d2fe' },
};

/* ------------ Server data ------------ */
const __ADMIN__ = (window.__MP_ADMIN__) || {};

const INITIAL_USERS = __ADMIN__.users && __ADMIN__.users.length > 0 ? __ADMIN__.users : [
  { id: 1, org: 'FALH', userId: 'admin',    name: '김관태', status: '사용' },
  { id: 2, org: 'FALH', userId: 'coreadmin',name: '이수민', status: '사용' },
];

const FEATURES = __ADMIN__.features && __ADMIN__.features.length > 0 ? __ADMIN__.features : [
  { key: 'COUNSELMAN',  label: 'CounselMan',     sub: 'COUNSELMAN' },
  { key: 'ROOM_BOARD',  label: '병실현황판',     sub: 'ROOM_BOARD' },
  { key: 'SEMINAR_ROOM',label: '세미나실 예약',  sub: 'SEMINAR_ROOM' },
];

/* Initial permissions map: userId -> Set of feature keys */
function defaultPerms(users) {
  const m = {};
  users.forEach(u => {
    m[u.userId] = new Set(['COUNSELMAN', 'ROOM_BOARD', 'SEMINAR_ROOM']);
  });
  return m;
}

/* ------------ Misc icons ------------ */
const IAIcon = {
  Building: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2"/></svg>,
  User: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M20 21a8 8 0 1 0-16 0"/><circle cx="12" cy="7" r="4"/></svg>,
  Lock: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>,
  Tag: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><circle cx="7" cy="7" r="1.5" fill="currentColor" stroke="none"/></svg>,
  ChevronDown: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><polyline points="6 9 12 15 18 9"/></svg>,
  Check: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" {...p}><polyline points="20 6 9 17 4 12"/></svg>,
  Pen: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>,
  Trash: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4h6v2"/></svg>,
  Plus: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" {...p}><path d="M12 5v14M5 12h14"/></svg>,
  Save: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>,
  Shield: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>,
  Logout: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>,
  ArrowLeft: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M19 12H5M12 5l-7 7 7 7"/></svg>,
  Spinner: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" {...p}><path d="M21 12a9 9 0 1 1-6.2-8.55"/></svg>,
};

/* ------------ Avatar ------------ */
function Avatar({ name, size = 32, accent }) {
  const colors = ['#173a82','#0d544a','#312e81','#7c3aed','#0f766e','#b45309'];
  const idx = name.charCodeAt(0) % colors.length;
  const bg = colors[idx];
  const ch = name.charAt(0);
  return (
    <div style={{
      width: size, height: size,
      borderRadius: '50%',
      background: bg,
      display: 'grid', placeItems: 'center',
      color: '#fff',
      fontSize: size * 0.38,
      fontWeight: 700,
      flexShrink: 0,
      letterSpacing: 0,
    }}>{ch}</div>
  );
}

/* ------------ Header ------------ */
function Header({ onBack, onLogout }) {
  return (
    <header className="ia-header">
      <div className="ia-header__left">
        <button className="ia-header__back" onClick={onBack} title="포털로 돌아가기">
          <IAIcon.ArrowLeft width={16} height={16} />
        </button>
        <div>
          <div className="ia-header__eyebrow">MEDIPLAT ADMIN</div>
          <h1 className="ia-header__title">기관 관리</h1>
        </div>
      </div>
      <div className="ia-header__actions">
        <button className="ia-header__btn" onClick={onBack}>
          <IAIcon.ArrowLeft width={14} height={14} />
          서비스 선택
        </button>
        <button className="ia-header__btn ia-header__btn--ghost" onClick={onLogout}>
          <IAIcon.Logout width={14} height={14} />
          로그아웃
        </button>
      </div>
    </header>
  );
}

/* ------------ Left panel: 기관 사용자 계정 ------------ */
function UserAccountPanel({ users, setUsers, selectedUserId, onSelectUser, accent, density }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  const compact = density === 'compact';

  const emptyForm = { org: 'FALH', userId: '', password: '', name: '', status: '사용' };
  const [form, setForm] = React.useState(emptyForm);
  const [editId, setEditId] = React.useState(null);
  const [saving, setSaving] = React.useState(false);
  const [toast, setToast] = React.useState(null);

  function field(key, val) { setForm(f => ({ ...f, [key]: val })); }

  function showToast(msg) {
    setToast(msg);
    setTimeout(() => setToast(null), 2200);
  }

  function handleEdit(user) {
    setEditId(user.id);
    setForm({ org: user.org, userId: user.userId, password: '', name: user.name, status: user.status });
  }

  function handleDelete(userId) {
    setUsers(prev => prev.filter(u => u.id !== userId));
    if (editId === userId) { setEditId(null); setForm(emptyForm); }
    showToast('삭제되었습니다.');
  }

  function handleSave(e) {
    e.preventDefault();
    if (!form.userId || !form.name) return;
    setSaving(true);
    setTimeout(() => {
      if (editId) {
        setUsers(prev => prev.map(u => u.id === editId
          ? { ...u, userId: form.userId, name: form.name, status: form.status }
          : u
        ));
        showToast('수정되었습니다.');
        setEditId(null);
      } else {
        const newUser = { id: Date.now(), org: form.org, userId: form.userId, name: form.name, status: form.status };
        setUsers(prev => [...prev, newUser]);
        showToast('사용자가 추가되었습니다.');
      }
      setForm(emptyForm);
      setSaving(false);
    }, 700);
  }

  function cancelEdit() { setEditId(null); setForm(emptyForm); }

  const rowH = compact ? 40 : 50;

  return (
    <div className="ia-panel">
      <div className="ia-panel__head">
        <div className="ia-panel__head-icon" style={{ background: `linear-gradient(135deg,${a.mid},${a.to})` }}>
          <IAIcon.User width={16} height={16} />
        </div>
        <div>
          <div className="ia-panel__title">기관 사용자 계정</div>
          <div className="ia-panel__sub">기관 관리자 계정으로 로그인해도 기관 사용자를 추가/수정할 수 있습니다.</div>
        </div>
      </div>

      {/* Form */}
      <form className="ia-form" onSubmit={handleSave}>
        <div className="ia-form__row">
          <div className="ia-field">
            <IAIcon.Building width={15} height={15} className="ia-field__icon" />
            <input
              className="ia-field__input"
              value={form.org}
              onChange={e => field('org', e.target.value)}
              placeholder="기관코드"
              style={{ '--ring': a.to }}
            />
          </div>
          <div className="ia-field">
            <IAIcon.User width={15} height={15} className="ia-field__icon" />
            <input
              className="ia-field__input"
              value={form.userId}
              onChange={e => field('userId', e.target.value)}
              placeholder="아이디"
              style={{ '--ring': a.to }}
            />
          </div>
        </div>
        <div className="ia-field">
          <IAIcon.Lock width={15} height={15} className="ia-field__icon" />
          <input
            className="ia-field__input"
            type="password"
            value={form.password}
            onChange={e => field('password', e.target.value)}
            placeholder={editId ? '비밀번호 (변경 시 입력)' : '비밀번호'}
            style={{ '--ring': a.to }}
          />
        </div>
        <div className="ia-field">
          <IAIcon.Tag width={15} height={15} className="ia-field__icon" />
          <input
            className="ia-field__input"
            value={form.name}
            onChange={e => field('name', e.target.value)}
            placeholder="표시 이름"
            style={{ '--ring': a.to }}
          />
        </div>
        <div className="ia-field">
          <IAIcon.Shield width={15} height={15} className="ia-field__icon" />
          <select
            className="ia-field__input ia-field__select"
            value={form.status}
            onChange={e => field('status', e.target.value)}
            style={{ '--ring': a.to }}
          >
            <option value="사용">사용</option>
            <option value="미사용">미사용</option>
          </select>
          <IAIcon.ChevronDown width={13} height={13} className="ia-field__caret" />
        </div>

        <div className="ia-form__btns">
          <button
            type="submit"
            className="ia-btn ia-btn--primary"
            disabled={saving}
            style={{ background: `linear-gradient(135deg,${a.mid},${a.to})` }}
          >
            {saving
              ? <><IAIcon.Spinner width={15} height={15} className="ia-spin" />저장 중…</>
              : <><IAIcon.Save width={15} height={15} />{editId ? '수정 저장' : '사용자 저장'}</>
            }
          </button>
          {editId && (
            <button type="button" className="ia-btn ia-btn--ghost" onClick={cancelEdit}>
              취소
            </button>
          )}
        </div>
      </form>

      {/* User table */}
      <div className="ia-table-wrap">
        <table className="ia-table">
          <thead>
            <tr>
              {true && <th style={{ width: 36 }}></th>}
              <th>기관코드</th>
              <th>아이디</th>
              <th>표시 이름</th>
              <th>상태</th>
              <th style={{ width: 72 }}></th>
            </tr>
          </thead>
          <tbody>
            {users.map(u => (
              <tr
                key={u.id}
                className={`ia-table__row ${selectedUserId === u.userId ? 'is-selected' : ''} ${u.id === editId ? 'is-editing' : ''}`}
                onClick={() => onSelectUser(u.userId)}
                style={{ '--row-h': rowH + 'px' }}
              >
                <td>
                  <Avatar name={u.name} size={26} accent={accent} />
                </td>
                <td className="ia-table__org">{u.org}</td>
                <td className="ia-table__id">{u.userId}</td>
                <td className="ia-table__name">{u.name}</td>
                <td>
                  <span className={`ia-status ${u.status === '사용' ? 'ia-status--on' : 'ia-status--off'}`}>
                    {u.status}
                  </span>
                </td>
                <td className="ia-table__actions" onClick={e => e.stopPropagation()}>
                  <button className="ia-row-btn" title="수정" onClick={() => handleEdit(u)}>
                    <IAIcon.Pen width={13} height={13} />
                  </button>
                  <button className="ia-row-btn ia-row-btn--del" title="삭제" onClick={() => handleDelete(u.id)}>
                    <IAIcon.Trash width={13} height={13} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Toast */}
      {toast && (
        <div className="ia-toast">
          <IAIcon.Check width={14} height={14} />
          {toast}
        </div>
      )}
    </div>
  );
}

/* ------------ Right panel: 사용자 기능 권한 ------------ */
function PermissionPanel({ users, accent }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  const [selectedUser, setSelectedUser] = React.useState(users[0]?.userId || '');
  const [perms, setPerms] = React.useState(() => defaultPerms(users));
  const [saving, setSaving] = React.useState(false);
  const [saved, setSaved] = React.useState(false);

  // Sync new users added to perms
  React.useEffect(() => {
    setPerms(prev => {
      const next = { ...prev };
      users.forEach(u => {
        if (!next[u.userId]) next[u.userId] = new Set(FEATURES.map(f => f.key));
      });
      return next;
    });
  }, [users]);

  function toggle(featureKey) {
    setPerms(prev => {
      const cur = new Set(prev[selectedUser] || []);
      if (cur.has(featureKey)) cur.delete(featureKey); else cur.add(featureKey);
      return { ...prev, [selectedUser]: cur };
    });
  }

  function handleSave() {
    setSaving(true);
    setTimeout(() => {
      setSaving(false);
      setSaved(true);
      setTimeout(() => setSaved(false), 1800);
    }, 800);
  }

  const curPerms = perms[selectedUser] || new Set();
  const currentUser = users.find(u => u.userId === selectedUser);

  return (
    <div className="ia-panel">
      <div className="ia-panel__head">
        <div className="ia-panel__head-icon ia-panel__head-icon--shield" style={{ background: `linear-gradient(135deg,#6c7d97,#475569)` }}>
          <IAIcon.Shield width={16} height={16} />
        </div>
        <div>
          <div className="ia-panel__title">사용자 기능 권한</div>
          <div className="ia-panel__sub">기관에서 허용된 서비스 중 사용자별로 실제 노출할 기능을 설정합니다.</div>
        </div>
      </div>

      {/* User selector */}
      <div className="ia-perm-select-wrap">
        <div className="ia-field">
          <IAIcon.User width={15} height={15} className="ia-field__icon" />
          <select
            className="ia-field__input ia-field__select"
            value={selectedUser}
            onChange={e => setSelectedUser(e.target.value)}
            style={{ '--ring': a.to }}
          >
            {users.map(u => (
              <option key={u.id} value={u.userId}>{u.name} ({u.userId})</option>
            ))}
          </select>
          <IAIcon.ChevronDown width={13} height={13} className="ia-field__caret" />
        </div>
        {currentUser && (
          <div className="ia-perm-user-badge">
            <Avatar name={currentUser.name} size={28} accent={accent} />
            <div>
              <div style={{ fontWeight: 700, fontSize: 13 }}>{currentUser.name}</div>
              <div style={{ fontSize: 11, color: 'var(--ink-500)' }}>{currentUser.org} · {currentUser.status}</div>
            </div>
          </div>
        )}
      </div>

      {/* Feature checkboxes */}
      <div className="ia-perm-list">
        {FEATURES.map(f => {
          const on = curPerms.has(f.key);
          return (
            <label key={f.key} className={`ia-perm-item ${on ? 'ia-perm-item--on' : ''}`} style={{ '--ring': a.ring, '--to': a.to }}>
              <span className="ia-perm-checkbox" style={{
                borderColor: on ? a.to : undefined,
                background: on ? a.to : undefined,
              }}>
                {on && <IAIcon.Check width={11} height={11} />}
              </span>
              <div className="ia-perm-label">
                <span className="ia-perm-name">{f.label}</span>
                <span className="ia-perm-key">{f.sub}</span>
              </div>
              <input type="checkbox" checked={on} onChange={() => toggle(f.key)} style={{ display: 'none' }} />
            </label>
          );
        })}
      </div>

      <button
        className="ia-btn ia-btn--primary ia-btn--full"
        onClick={handleSave}
        disabled={saving}
        style={{ background: saved ? 'linear-gradient(135deg,#047857,#059669)' : `linear-gradient(135deg,${a.mid},${a.to})` }}
      >
        {saving
          ? <><IAIcon.Spinner width={15} height={15} className="ia-spin" />저장 중…</>
          : saved
            ? <><IAIcon.Check width={15} height={15} />저장 완료!</>
            : <><IAIcon.Shield width={15} height={15} />사용자 권한 저장</>
        }
      </button>

      {/* Summary */}
      <div className="ia-perm-summary">
        <div className="ia-perm-summary__title">현재 선택된 사용자 권한 요약</div>
        {FEATURES.map(f => (
          <div key={f.key} className="ia-perm-summary__row">
            <span className={`ia-perm-dot ${curPerms.has(f.key) ? 'ia-perm-dot--on' : 'ia-perm-dot--off'}`} />
            <span className="ia-perm-summary__label">{f.label}</span>
            <span className={`ia-perm-summary__val ${curPerms.has(f.key) ? 'ia-perm-summary__val--on' : ''}`}>
              {curPerms.has(f.key) ? '허용' : '차단'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ============================================================== */
/*  CSS                                                            */
/* ============================================================== */
const iaCss = `
/* ---- Page ---- */
.ia-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ---- Header ---- */
.ia-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 32px 18px;
  background: rgba(255,255,255,0.82);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--ink-100);
  position: sticky; top: 0; z-index: 10;
}
.ia-header__left { display: flex; align-items: center; gap: 16px; }
.ia-header__back {
  width: 36px; height: 36px;
  border-radius: 10px;
  display: grid; place-items: center;
  color: var(--ink-600);
  border: 1px solid var(--ink-100);
  background: #fff;
  transition: all .12s;
}
.ia-header__back:hover { border-color: var(--ink-200); color: var(--ink-900); }
.ia-header__eyebrow { font-size: 10px; letter-spacing: 0.22em; color: var(--ink-500); font-weight: 700; }
.ia-header__title { font-size: 26px; font-weight: 800; letter-spacing: -0.03em; margin: 3px 0 0; }
.ia-header__actions { display: flex; gap: 10px; }
.ia-header__btn {
  display: inline-flex; align-items: center; gap: 7px;
  height: 38px; padding: 0 18px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--ink-200);
  color: var(--ink-700);
  font-size: 13px; font-weight: 600;
  transition: all .12s;
}
.ia-header__btn:hover { border-color: var(--cs-blue-500); color: var(--cs-blue-600); }
.ia-header__btn--ghost { background: transparent; }

/* ---- Body grid ---- */
.ia-body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(400px,1fr) minmax(340px,.8fr);
  gap: 24px;
  padding: 28px 32px 48px;
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}
@media (max-width: 900px) { .ia-body { grid-template-columns: 1fr; } }

/* ---- Panel ---- */
.ia-panel {
  background: #fff;
  border: 1px solid var(--ink-100);
  border-radius: 18px;
  padding: 26px 26px 24px;
  box-shadow: 0 2px 12px -4px rgba(11,31,74,0.08), 0 1px 3px rgba(11,31,74,0.04);
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: relative;
}
.ia-panel__head { display: flex; align-items: flex-start; gap: 14px; }
.ia-panel__head-icon {
  width: 36px; height: 36px;
  border-radius: 10px;
  display: grid; place-items: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 12px -4px rgba(11,31,74,0.35);
}
.ia-panel__title { font-size: 17px; font-weight: 800; letter-spacing: -0.02em; }
.ia-panel__sub { font-size: 12px; color: var(--ink-500); margin-top: 3px; line-height: 1.5; }

/* ---- Form ---- */
.ia-form { display: flex; flex-direction: column; gap: 10px; }
.ia-form__row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.ia-form__btns { display: flex; gap: 8px; margin-top: 4px; }

/* ---- Field ---- */
.ia-field {
  position: relative;
  display: flex; align-items: center;
}
.ia-field__icon {
  position: absolute; left: 14px;
  color: var(--ink-400);
  flex-shrink: 0;
  pointer-events: none;
  transition: color .12s;
}
.ia-field__input {
  width: 100%;
  height: 44px;
  padding: 0 14px 0 40px;
  background: var(--field-bg, #eef4ff);
  border: 1.5px solid transparent;
  border-radius: 10px;
  font-size: 13.5px;
  color: var(--ink-900);
  outline: none;
  appearance: none;
  transition: border-color .14s, box-shadow .14s, background .14s;
}
.ia-field__input:hover { background: #e6eeff; }
.ia-field__input:focus {
  background: #fff;
  border-color: var(--ring, #1d4ed8);
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--ring, #1d4ed8) 14%, transparent);
}
.ia-field__input:focus ~ .ia-field__icon { color: var(--ring, #1d4ed8); }
.ia-field__select { padding-right: 36px; cursor: pointer; }
.ia-field__caret {
  position: absolute; right: 12px;
  color: var(--ink-400);
  pointer-events: none;
}
/* refocus icon when sibling input focused */
.ia-field:focus-within .ia-field__icon { color: var(--ring, #1d4ed8); }

/* ---- Buttons ---- */
.ia-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 7px;
  height: 46px; padding: 0 22px;
  border-radius: 10px;
  color: #fff;
  font-weight: 700; font-size: 14px;
  letter-spacing: 0.01em;
  box-shadow: 0 6px 14px -8px rgba(11,31,74,0.5);
  transition: transform .12s, box-shadow .14s, opacity .12s, background .2s;
  flex: 1;
}
.ia-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 12px 22px -8px rgba(11,31,74,0.45); }
.ia-btn:active:not(:disabled) { transform: translateY(0); }
.ia-btn:disabled { opacity: 0.8; cursor: progress; }
.ia-btn--ghost { background: #fff; color: var(--ink-700); border: 1.5px solid var(--ink-200); box-shadow: none; flex: 0 0 auto; padding: 0 18px; }
.ia-btn--ghost:hover { border-color: var(--ink-300); color: var(--ink-900); }
.ia-btn--full { width: 100%; flex: unset; }
.ia-spin { animation: ia-spin .9s linear infinite; }
@keyframes ia-spin { to { transform: rotate(360deg); } }

/* ---- Table ---- */
.ia-table-wrap {
  border: 1px solid var(--ink-100);
  border-radius: 12px;
  overflow: hidden;
  overflow-y: auto;
  max-height: 360px;
}
.ia-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}
.ia-table thead th {
  position: sticky; top: 0; z-index: 2;
  background: var(--ink-50);
  font-size: 11px; font-weight: 700;
  letter-spacing: 0.06em; text-transform: uppercase;
  color: var(--ink-500);
  padding: 10px 14px;
  border-bottom: 1px solid var(--ink-100);
  white-space: nowrap;
  text-align: left;
}
.ia-table__row {
  cursor: pointer;
  transition: background .1s;
  height: var(--row-h, 50px);
}
.ia-table__row:hover { background: var(--cs-blue-50, #eff4ff); }
.ia-table__row.is-selected { background: var(--cs-blue-50, #eff4ff); box-shadow: inset 3px 0 0 #1d4ed8; }
.ia-table__row.is-editing { background: #fefce8; box-shadow: inset 3px 0 0 #d97706; }
.ia-table tbody td {
  padding: 8px 14px;
  border-bottom: 1px solid var(--ink-100);
  vertical-align: middle;
}
.ia-table tbody tr:last-child td { border-bottom: 0; }
.ia-table__org { font-size: 12px; color: var(--ink-500); font-variant-numeric: tabular-nums; font-weight: 600; letter-spacing: 0.04em; }
.ia-table__id  { font-weight: 600; color: var(--ink-700); }
.ia-table__name { font-weight: 500; }
.ia-table__actions { text-align: right; }

/* ---- Status chip ---- */
.ia-status {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px; font-weight: 600;
}
.ia-status::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.ia-status--on  { color: #047857; background: #ecfdf5; }
.ia-status--off { color: var(--ink-500); background: var(--ink-100); }

/* ---- Row action buttons ---- */
.ia-row-btn {
  width: 28px; height: 28px;
  border-radius: 7px;
  display: inline-grid; place-items: center;
  color: var(--ink-400);
  border: 1px solid transparent;
  transition: all .1s;
}
.ia-row-btn:hover { background: var(--ink-100); color: var(--ink-700); border-color: var(--ink-200); }
.ia-row-btn--del:hover { background: #fef2f2; color: #dc2626; border-color: #fecaca; }

/* ---- Toast ---- */
.ia-toast {
  position: absolute;
  bottom: 16px; left: 50%;
  transform: translateX(-50%);
  background: var(--ink-900);
  color: #fff;
  padding: 10px 18px;
  border-radius: 999px;
  font-size: 13px; font-weight: 500;
  box-shadow: 0 12px 24px -8px rgba(11,31,74,0.35);
  display: inline-flex; align-items: center; gap: 8px;
  animation: ia-pop .24s cubic-bezier(0.34,1.56,0.64,1);
  z-index: 20;
  white-space: nowrap;
}
.ia-toast svg { color: #10b981; }
@keyframes ia-pop {
  from { transform: translateX(-50%) scale(.92) translateY(4px); opacity: 0; }
  to   { transform: translateX(-50%) scale(1) translateY(0); opacity: 1; }
}

/* ---- Permission panel ---- */
.ia-perm-select-wrap { display: flex; flex-direction: column; gap: 12px; }
.ia-perm-user-badge {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px;
  background: var(--ink-50);
  border: 1px solid var(--ink-100);
  border-radius: 10px;
}
.ia-perm-list { display: flex; flex-direction: column; gap: 8px; }
.ia-perm-item {
  display: flex; align-items: center; gap: 12px;
  padding: 13px 16px;
  border-radius: 10px;
  border: 1.5px solid var(--ink-100);
  background: var(--ink-50);
  cursor: pointer;
  transition: border-color .12s, background .12s, box-shadow .12s;
  user-select: none;
}
.ia-perm-item:hover { border-color: var(--ring, #bfdbfe); background: color-mix(in oklab, var(--to,#1d4ed8) 5%, #fff); }
.ia-perm-item--on { border-color: var(--ring, #bfdbfe); background: color-mix(in oklab, var(--to,#1d4ed8) 6%, #fff); box-shadow: 0 0 0 3px color-mix(in oklab, var(--to,#1d4ed8) 8%, transparent); }
.ia-perm-checkbox {
  width: 20px; height: 20px;
  border: 1.5px solid var(--ink-300);
  border-radius: 5px;
  display: grid; place-items: center;
  color: #fff;
  flex-shrink: 0;
  transition: all .14s;
}
.ia-perm-label { display: flex; flex-direction: column; gap: 1px; flex: 1; }
.ia-perm-name { font-size: 14px; font-weight: 600; letter-spacing: -0.01em; }
.ia-perm-key  { font-size: 10.5px; letter-spacing: 0.08em; color: var(--ink-400); font-weight: 600; }

/* ---- Summary ---- */
.ia-perm-summary {
  padding: 14px 16px;
  background: var(--ink-50);
  border: 1px solid var(--ink-100);
  border-radius: 10px;
  display: flex; flex-direction: column; gap: 8px;
}
.ia-perm-summary__title { font-size: 11px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--ink-400); margin-bottom: 4px; }
.ia-perm-summary__row { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.ia-perm-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.ia-perm-dot--on  { background: #10b981; box-shadow: 0 0 6px #10b981; }
.ia-perm-dot--off { background: var(--ink-300); }
.ia-perm-summary__label { flex: 1; color: var(--ink-700); }
.ia-perm-summary__val { font-size: 11.5px; font-weight: 700; color: var(--ink-400); }
.ia-perm-summary__val--on { color: #047857; }
`;

/* ============================================================== */
/*  App                                                            */
/* ============================================================== */
function InstitutionAdminApp() {
  const [tweaks, setTweak] = window.useTweaks(IA_TWEAK_DEFAULTS);
  const [users, setUsers] = React.useState(INITIAL_USERS);
  const [selectedUserId, setSelectedUserId] = React.useState(INITIAL_USERS[0]?.userId);

  function onBack() { window.location.href = '/portal'; }
  function onLogout() { window.location.href = '/logout'; }

  return (
    <>
      <style>{iaCss}</style>
      <div className="ia-page" data-screen-label="Institution Admin">
        <Header onBack={onBack} onLogout={onLogout} />
        <div className="ia-body">
          <UserAccountPanel
            users={users}
            setUsers={setUsers}
            selectedUserId={selectedUserId}
            onSelectUser={setSelectedUserId}
            accent={tweaks.accent}
            density={tweaks.density}
          />
          <PermissionPanel
            users={users}
            accent={tweaks.accent}
          />
        </div>
      </div>

      <window.TweaksPanel title="Tweaks · 기관 관리">
        <window.TweakSection title="스타일">
          <window.TweakRadio
            label="브랜드 컬러"
            value={tweaks.accent}
            options={[
              { value: 'blue',   label: 'Blue' },
              { value: 'teal',   label: 'Teal' },
              { value: 'indigo', label: 'Indigo' },
            ]}
            onChange={(v) => setTweak('accent', v)}
          />
          <window.TweakRadio
            label="행 밀도"
            value={tweaks.density}
            options={[
              { value: 'comfortable', label: 'Normal' },
              { value: 'compact',     label: 'Compact' },
            ]}
            onChange={(v) => setTweak('density', v)}
          />
        </window.TweakSection>
        <window.TweakSection title="옵션">
          <window.TweakToggle label="아바타 표시" value={tweaks.showAvatars} onChange={(v) => setTweak('showAvatars', v)} />
          <window.TweakToggle label="헤더 sticky" value={tweaks.stickyHeader} onChange={(v) => setTweak('stickyHeader', v)} />
        </window.TweakSection>
      </window.TweaksPanel>
    </>
  );
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<InstitutionAdminApp />);
