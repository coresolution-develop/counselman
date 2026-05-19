/* MediPlat · 기관 관리
   Layout: sidebar (institution list) + main area (2×2 grid)
   Platform admin: all 4 panels
   Inst admin: user management + permissions only
*/

const IA_TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "blue",
  "density": "comfortable"
}/*EDITMODE-END*/;

const IA_ACCENTS = {
  blue:   { from: '#0b1f4a', mid: '#173a82', to: '#1d4ed8', tint: '#eff4ff', ring: '#bfdbfe' },
  teal:   { from: '#062f2a', mid: '#0d544a', to: '#0d9488', tint: '#f0fdf9', ring: '#99f6e4' },
  indigo: { from: '#1e1b4b', mid: '#312e81', to: '#4f46e5', tint: '#eef2ff', ring: '#c7d2fe' },
};

const __ADMIN__ = window.__MP_ADMIN__ || {};

/* ---- CSRF (gracefully no-ops if CSRF disabled) ---- */
function getCsrf() {
  return {
    token:  document.querySelector('meta[name="_csrf"]')?.content || '',
    header: document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN',
  };
}

async function postForm(url, data) {
  const fd = new FormData();
  Object.entries(data).forEach(([k, v]) => {
    if (Array.isArray(v)) v.forEach(item => fd.append(k, item));
    else if (v != null) fd.append(k, String(v));
  });
  const { token, header } = getCsrf();
  const headers = {};
  if (token) headers[header] = token;
  const res = await fetch(url, { method: 'POST', headers, body: fd });
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res;
}

async function postJson(url, data) {
  const { token, header } = getCsrf();
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers[header] = token;
  const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(data) });
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json();
}

/* ---- Icons ---- */
const I = {
  Building:    (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2"/></svg>,
  User:        (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M20 21a8 8 0 1 0-16 0"/><circle cx="12" cy="7" r="4"/></svg>,
  Lock:        (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>,
  Tag:         (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><circle cx="7" cy="7" r="1.5" fill="currentColor" stroke="none"/></svg>,
  Chevron:     (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><polyline points="6 9 12 15 18 9"/></svg>,
  Check:       (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" {...p}><polyline points="20 6 9 17 4 12"/></svg>,
  Pen:         (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>,
  Plus:        (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" {...p}><path d="M12 5v14M5 12h14"/></svg>,
  Save:        (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>,
  Shield:      (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>,
  Logout:      (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>,
  ArrowLeft:   (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M19 12H5M12 5l-7 7 7 7"/></svg>,
  Spinner:     (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" {...p}><path d="M21 12a9 9 0 1 1-6.2-8.55"/></svg>,
  Globe:       (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>,
  Power:       (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M18.36 6.64a9 9 0 1 1-12.73 0"/><line x1="12" y1="2" x2="12" y2="12"/></svg>,
  App:         (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>,
  Link:        (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>,
  Mail:        (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/></svg>,
  Phone:       (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="6" y="2" width="12" height="20" rx="2"/><line x1="11" y1="18" x2="13" y2="18"/></svg>,
};

/* ---- Shared helpers ---- */
function Avatar({ name = '?', size = 30 }) {
  const colors = ['#173a82','#0d544a','#312e81','#7c3aed','#0f766e','#b45309'];
  const bg = colors[(name.charCodeAt(0) || 0) % colors.length];
  return (
    <div style={{ width: size, height: size, borderRadius: '50%', background: bg, display: 'grid', placeItems: 'center', color: '#fff', fontSize: size * 0.38, fontWeight: 700, flexShrink: 0 }}>
      {name.charAt(0)}
    </div>
  );
}

function Toast({ msg, isErr }) {
  if (!msg) return null;
  return (
    <div className={`ia-toast ${isErr ? 'ia-toast--err' : ''}`}>
      {isErr ? '⚠ ' : <I.Check width={14} height={14} />}
      {msg}
    </div>
  );
}

function useToast() {
  const [toast, setToast] = React.useState(null);
  const show = (msg, isErr = false) => {
    setToast({ msg, isErr });
    setTimeout(() => setToast(null), 2500);
  };
  return [toast, show];
}

function PanelHead({ icon, gradient, title, sub, children }) {
  return (
    <div className="ia-panel__head">
      <div className="ia-panel__head-icon" style={{ background: gradient }}>
        {icon}
      </div>
      <div style={{ flex: 1 }}>
        <div className="ia-panel__title">{title}</div>
        {sub && <div className="ia-panel__sub">{sub}</div>}
      </div>
      {children}
    </div>
  );
}

function Field({ icon, children }) {
  return (
    <div className="ia-field">
      {icon && <span className="ia-field__icon">{icon}</span>}
      {children}
    </div>
  );
}

function Btn({ accent, full, ghost, disabled, loading, onClick, type = 'button', children }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  return (
    <button
      type={type}
      className={`ia-btn ${ghost ? 'ia-btn--ghost' : ''} ${full ? 'ia-btn--full' : ''}`}
      style={!ghost ? { background: `linear-gradient(135deg,${a.mid},${a.to})` } : undefined}
      disabled={disabled || loading}
      onClick={onClick}
    >
      {loading ? <><I.Spinner width={14} height={14} className="ia-spin" />저장 중…</> : children}
    </button>
  );
}

function CheckItem({ on, label, sub, onToggle, accent }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  return (
    <label className={`ia-check-item ${on ? 'ia-check-item--on' : ''}`} style={{ '--ring': a.ring, '--to': a.to }}>
      <span className="ia-checkbox" style={{ borderColor: on ? a.to : undefined, background: on ? a.to : undefined }}>
        {on && <I.Check width={11} height={11} />}
      </span>
      <div className="ia-check-label">
        <span className="ia-check-name">{label}</span>
        <span className="ia-check-sub">{sub}</span>
      </div>
      <input type="checkbox" checked={on} onChange={onToggle} style={{ display: 'none' }} />
    </label>
  );
}

/* ============================================================
   Header
   ============================================================ */
function Header({ onBack, onLogout }) {
  return (
    <header className="ia-header">
      <div className="ia-header__left">
        <button className="ia-header__back" onClick={onBack}><I.ArrowLeft width={16} height={16} /></button>
        <div>
          <div className="ia-header__eyebrow">MEDIPLAT ADMIN</div>
          <h1 className="ia-header__title">기관 관리</h1>
        </div>
      </div>
      <div className="ia-header__actions">
        <button className="ia-header__btn" onClick={onBack}><I.ArrowLeft width={14} height={14} />서비스 선택</button>
        <button className="ia-header__btn ia-header__btn--ghost" onClick={onLogout}><I.Logout width={14} height={14} />로그아웃</button>
      </div>
    </header>
  );
}

/* ============================================================
   SIDEBAR: Institution List
   ============================================================ */
function InstitutionSidebar({ institutions, selectedInstCode, onSelect, onAdded, accent }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  const [search, setSearch] = React.useState('');
  const [showForm, setShowForm] = React.useState(false);
  const [form, setForm] = React.useState({ instCode: '', instName: '', useYn: 'Y' });
  const [saving, setSaving] = React.useState(false);
  const [toast, showToast] = useToast();

  const filtered = institutions.filter(i =>
    i.instCode.toLowerCase().includes(search.toLowerCase()) ||
    i.instName.toLowerCase().includes(search.toLowerCase())
  );

  async function handleSave(e) {
    e.preventDefault();
    if (!form.instCode || !form.instName) return;
    setSaving(true);
    try {
      const data = await postJson('/api/admin/institutions', {
        instCode: form.instCode.trim().toUpperCase(),
        instName: form.instName.trim(),
        useYn: form.useYn,
      });
      showToast(data.isNew ? '기관이 등록되었습니다.' : '기관 정보가 수정되었습니다.');
      onAdded();
      setShowForm(false);
      setForm({ instCode: '', instName: '', useYn: 'Y' });
    } catch (err) {
      showToast(err.message || '저장 실패', true);
    } finally {
      setSaving(false);
    }
  }

  return (
    <aside className="ia-sidebar">
      <div className="ia-sidebar__head">
        <div className="ia-sidebar__title">기관 목록</div>
        <button className="ia-icon-btn" onClick={() => setShowForm(v => !v)} title="기관 추가" style={{ color: showForm ? a.to : undefined }}>
          <I.Plus width={17} height={17} />
        </button>
      </div>

      <div className="ia-field" style={{ marginBottom: 8 }}>
        <span className="ia-field__icon"><I.Globe width={14} height={14} /></span>
        <input className="ia-field__input ia-field__input--sm" placeholder="검색…" value={search} onChange={e => setSearch(e.target.value)} style={{ '--ring': a.to }} />
      </div>

      {showForm && (
        <form className="ia-inset-form" onSubmit={handleSave}>
          <input className="ia-plain-input" placeholder="기관코드 (영문대문자)" value={form.instCode} onChange={e => setForm(f => ({ ...f, instCode: e.target.value.toUpperCase() }))} style={{ '--ring': a.to }} />
          <input className="ia-plain-input" placeholder="기관명" value={form.instName} onChange={e => setForm(f => ({ ...f, instName: e.target.value }))} style={{ '--ring': a.to }} />
          <select className="ia-plain-input" value={form.useYn} onChange={e => setForm(f => ({ ...f, useYn: e.target.value }))} style={{ '--ring': a.to }}>
            <option value="Y">사용</option>
            <option value="N">미사용</option>
          </select>
          <Btn type="submit" accent={accent} loading={saving} full><I.Save width={13} height={13} />기관 저장</Btn>
        </form>
      )}

      <div className="ia-inst-list">
        {filtered.length === 0 && <div className="ia-empty">검색 결과 없음</div>}
        {filtered.map(inst => (
          <button key={inst.instCode} className={`ia-inst-item ${selectedInstCode === inst.instCode ? 'is-selected' : ''}`} onClick={() => onSelect(inst.instCode)} style={{ '--to': a.to, '--tint': a.tint }}>
            <div>
              <div className="ia-inst-item__name">{inst.instName}</div>
              <div className="ia-inst-item__code">{inst.instCode}</div>
            </div>
            <span className={`ia-badge ${inst.useYn === 'Y' ? 'ia-badge--on' : 'ia-badge--off'}`}>{inst.useYn === 'Y' ? '사용' : '중지'}</span>
          </button>
        ))}
      </div>

      <div style={{ position: 'relative' }}><Toast {...(toast || {})} /></div>
    </aside>
  );
}

/* ============================================================
   PANEL 1: Institution Settings + Service Access
   ============================================================ */
function InstSettingsPanel({ selectedInstCode, instInfo, allServices, enabledServiceCodes, onSaved, accent }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  const [localEnabled, setLocalEnabled] = React.useState(new Set(enabledServiceCodes));
  const [saving, setSaving] = React.useState(false);
  const [toggling, setToggling] = React.useState(false);
  const [toast, showToast] = useToast();

  React.useEffect(() => { setLocalEnabled(new Set(enabledServiceCodes)); }, [enabledServiceCodes, selectedInstCode]);

  async function handleSaveAccess(e) {
    e.preventDefault();
    setSaving(true);
    try {
      await postForm('/admin/access', { instCode: selectedInstCode, enabledServiceCodes: [...localEnabled] });
      showToast('서비스 권한이 저장되었습니다.');
      onSaved();
    } catch (err) { showToast(err.message || '저장 실패', true); }
    finally { setSaving(false); }
  }

  async function handleToggleStatus() {
    if (!instInfo) return;
    const newUseYn = instInfo.useYn === 'Y' ? 'N' : 'Y';
    if (!confirm(newUseYn === 'Y' ? '기관을 활성화하시겠습니까?' : '기관 사용을 중지하시겠습니까?')) return;
    setToggling(true);
    try {
      await postForm('/admin/institutions/status', { instCode: selectedInstCode, useYn: newUseYn });
      showToast(newUseYn === 'Y' ? '기관이 활성화되었습니다.' : '기관이 중지되었습니다.');
      onSaved();
    } catch (err) { showToast(err.message || '실패', true); }
    finally { setToggling(false); }
  }

  if (!selectedInstCode) {
    return (
      <div className="ia-panel ia-panel--empty">
        <I.Building width={36} height={36} style={{ color: 'var(--ink-200)' }} />
        <div style={{ color: 'var(--ink-400)', fontSize: 13 }}>기관을 선택하세요</div>
      </div>
    );
  }

  return (
    <div className="ia-panel" style={{ position: 'relative' }}>
      <PanelHead
        icon={<I.Building width={16} height={16} />}
        gradient="linear-gradient(135deg,#6c7d97,#475569)"
        title={instInfo?.instName || selectedInstCode}
        sub={`기관코드: ${selectedInstCode}`}
      >
        <button className={`ia-icon-btn ${instInfo?.useYn === 'Y' ? 'ia-icon-btn--warn' : 'ia-icon-btn--ok'}`} onClick={handleToggleStatus} disabled={toggling} title={instInfo?.useYn === 'Y' ? '기관 중지' : '기관 활성화'}>
          <I.Power width={15} height={15} />
        </button>
      </PanelHead>

      <form onSubmit={handleSaveAccess}>
        <div className="ia-section-label" style={{ marginBottom: 8 }}>앱/서비스 접근 권한</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {allServices.map(svc => (
            <CheckItem key={svc.serviceCode} on={localEnabled.has(svc.serviceCode)} label={svc.serviceName} sub={svc.serviceCode} accent={accent} onToggle={() => setLocalEnabled(prev => { const n = new Set(prev); n.has(svc.serviceCode) ? n.delete(svc.serviceCode) : n.add(svc.serviceCode); return n; })} />
          ))}
          {allServices.length === 0 && <div className="ia-empty">등록된 서비스가 없습니다.</div>}
        </div>
        <Btn type="submit" accent={accent} full loading={saving} style={{ marginTop: 12 }}>
          <I.Shield width={14} height={14} />서비스 권한 저장
        </Btn>
      </form>

      <div style={{ position: 'relative' }}><Toast {...(toast || {})} /></div>
    </div>
  );
}

/* ============================================================
   PANEL 2: App / Service Registration
   ============================================================ */
function AppManagementPanel({ allServices, onSaved, accent }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  const emptyForm = { serviceCode: '', serviceName: '', baseUrlLocal: '', baseUrlDev: '', baseUrlProd: '', userTarget: '', adminTarget: '', ssoEntryPath: '/mediplat/sso/entry', description: '', useYn: 'Y', displayOrder: 0 };
  const [form, setForm] = React.useState(emptyForm);
  const [editCode, setEditCode] = React.useState(null);
  const [saving, setSaving] = React.useState(false);
  const [toast, showToast] = useToast();

  function f(key, val) { setForm(p => ({ ...p, [key]: val })); }

  function handleEdit(svc) {
    setEditCode(svc.serviceCode);
    setForm({ serviceCode: svc.serviceCode, serviceName: svc.serviceName, baseUrlLocal: svc.baseUrlLocal || '', baseUrlDev: svc.baseUrlDev || '', baseUrlProd: svc.baseUrlProd || '', userTarget: svc.userTarget || '', adminTarget: svc.adminTarget || '', ssoEntryPath: svc.ssoEntryPath || '/mediplat/sso/entry', description: svc.description || '', useYn: svc.useYn || 'Y', displayOrder: svc.displayOrder ?? 0 });
  }

  async function handleSave(e) {
    e.preventDefault();
    if (!form.serviceCode || !form.serviceName) return;
    setSaving(true);
    try {
      await postForm('/admin/services', { ...form });
      showToast('앱이 저장되었습니다.');
      setEditCode(null);
      setForm(emptyForm);
      onSaved();
    } catch (err) { showToast(err.message || '저장 실패', true); }
    finally { setSaving(false); }
  }

  return (
    <div className="ia-panel" style={{ position: 'relative' }}>
      <PanelHead
        icon={<I.App width={16} height={16} />}
        gradient={`linear-gradient(135deg,${a.mid},${a.to})`}
        title="앱 등록"
        sub="플랫폼에 연동할 앱/서비스를 등록합니다."
      />

      <form className="ia-form" onSubmit={handleSave}>
        <div className="ia-form__row">
          <Field icon={<I.App width={14} height={14} />}>
            <input className="ia-field__input" placeholder="서비스코드 (영문대문자)" value={form.serviceCode} onChange={e => f('serviceCode', e.target.value.toUpperCase())} disabled={!!editCode} style={{ '--ring': a.to }} />
          </Field>
          <Field icon={<I.Tag width={14} height={14} />}>
            <input className="ia-field__input" placeholder="서비스명" value={form.serviceName} onChange={e => f('serviceName', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
        </div>
        <div className="ia-form__row">
          <Field icon={<I.Link width={14} height={14} />}>
            <input className="ia-field__input" placeholder="localhost URL" value={form.baseUrlLocal} onChange={e => f('baseUrlLocal', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
          <Field icon={<I.Link width={14} height={14} />}>
            <input className="ia-field__input" placeholder="운영 URL" value={form.baseUrlProd} onChange={e => f('baseUrlProd', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
        </div>
        <div className="ia-form__row">
          <Field icon={<I.ArrowLeft width={14} height={14} />}>
            <input className="ia-field__input" placeholder="사용자 진입 경로 (예: /counsel/list)" value={form.userTarget} onChange={e => f('userTarget', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
          <Field icon={<I.Shield width={14} height={14} />}>
            <input className="ia-field__input" placeholder="관리자 진입 경로 (예: /admin)" value={form.adminTarget} onChange={e => f('adminTarget', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
        </div>
        <div className="ia-form__row">
          <Field>
            <select className="ia-field__input ia-field__select" style={{ paddingLeft: 14, '--ring': a.to }} value={form.useYn} onChange={e => f('useYn', e.target.value)}>
              <option value="Y">사용</option>
              <option value="N">미사용</option>
            </select>
            <I.Chevron width={13} height={13} className="ia-field__caret" />
          </Field>
          <Field>
            <input className="ia-field__input" type="number" placeholder="표시 순서" value={form.displayOrder} onChange={e => f('displayOrder', parseInt(e.target.value) || 0)} style={{ paddingLeft: 14, '--ring': a.to }} />
          </Field>
        </div>
        <div className="ia-form__btns">
          <Btn type="submit" accent={accent} loading={saving}><I.Save width={14} height={14} />{editCode ? '앱 수정' : '앱 저장'}</Btn>
          {editCode && <Btn ghost onClick={() => { setEditCode(null); setForm(emptyForm); }}>취소</Btn>}
        </div>
      </form>

      {/* Service list */}
      <div className="ia-table-wrap">
        <table className="ia-table">
          <thead><tr><th>코드</th><th>서비스명</th><th>상태</th><th style={{ width: 44 }}></th></tr></thead>
          <tbody>
            {allServices.length === 0 && <tr><td colSpan={4} className="ia-empty">등록된 앱 없음</td></tr>}
            {allServices.map(svc => (
              <tr key={svc.serviceCode} className={`ia-table__row ${svc.serviceCode === editCode ? 'is-editing' : ''}`}>
                <td className="ia-table__code">{svc.serviceCode}</td>
                <td className="ia-table__name">{svc.serviceName}</td>
                <td><span className={`ia-badge ${svc.useYn === 'Y' ? 'ia-badge--on' : 'ia-badge--off'}`}>{svc.useYn === 'Y' ? '사용' : '중지'}</span></td>
                <td className="ia-table__actions">
                  <button className="ia-row-btn" onClick={() => handleEdit(svc)}><I.Pen width={13} height={13} /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ position: 'relative' }}><Toast {...(toast || {})} /></div>
    </div>
  );
}

/* ============================================================
   PANEL 3: User Management
   ============================================================ */
function UserManagementPanel({ selectedInstCode, users, setUsers, accent, density }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  const compact = density === 'compact';
  const emptyForm = { userId: '', password: '', name: '', email: '', phone: '', roleCode: 'USER', useYn: 'Y' };
  const [form, setForm] = React.useState(emptyForm);
  const [editId, setEditId] = React.useState(null);
  const [saving, setSaving] = React.useState(false);
  const [toast, showToast] = useToast();

  function f(k, v) { setForm(p => ({ ...p, [k]: v })); }

  function handleEdit(u) {
    setEditId(u.userId);
    setForm({
      userId: u.userId,
      password: '',
      name: u.name,
      email: u.email || '',
      phone: u.phone || '',
      roleCode: u.roleCode || 'USER',
      useYn: u.status === '사용' ? 'Y' : 'N',
    });
  }

  async function handleSave(e) {
    e.preventDefault();
    if (!form.userId || !form.name) return;
    if (!editId && !form.password) { showToast('비밀번호를 입력하세요.', true); return; }
    setSaving(true);
    try {
      await postForm('/admin/users', {
        instCode: selectedInstCode,
        username: form.userId,
        password: form.password,
        displayName: form.name,
        email: form.email,
        phone: form.phone,
        roleCode: form.roleCode,
        useYn: form.useYn,
      });
      showToast(editId ? '수정되었습니다.' : '사용자가 추가되었습니다.');
      if (editId) {
        setUsers(p => p.map(u => u.userId === editId
          ? { ...u, name: form.name, email: form.email, phone: form.phone, roleCode: form.roleCode, status: form.useYn === 'Y' ? '사용' : '미사용' }
          : u));
      } else {
        setUsers(p => [...p, {
          id: Date.now(),
          org: selectedInstCode,
          userId: form.userId,
          name: form.name,
          email: form.email,
          phone: form.phone,
          roleCode: form.roleCode,
          status: form.useYn === 'Y' ? '사용' : '미사용',
        }]);
      }
      setEditId(null);
      setForm(emptyForm);
    } catch (err) { showToast(err.message || '저장 실패', true); }
    finally { setSaving(false); }
  }

  return (
    <div className="ia-panel" style={{ position: 'relative' }}>
      <PanelHead icon={<I.User width={16} height={16} />} gradient={`linear-gradient(135deg,${a.mid},${a.to})`} title="사용자 관리" sub="기관 사용자 계정을 추가하거나 수정합니다." />

      <form className="ia-form" onSubmit={handleSave}>
        <div className="ia-form__row">
          <Field icon={<I.User width={14} height={14} />}>
            <input className="ia-field__input" placeholder="아이디" value={form.userId} onChange={e => f('userId', e.target.value)} disabled={!!editId} style={{ '--ring': a.to }} />
          </Field>
          <Field icon={<I.Lock width={14} height={14} />}>
            <input className="ia-field__input" type="password" placeholder={editId ? '비밀번호 (변경 시 입력)' : '비밀번호'} value={form.password} onChange={e => f('password', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
        </div>
        <div className="ia-form__row">
          <Field icon={<I.Tag width={14} height={14} />}>
            <input className="ia-field__input" placeholder="표시 이름" value={form.name} onChange={e => f('name', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
          <Field>
            <select className="ia-field__input ia-field__select" style={{ paddingLeft: 14, '--ring': a.to }} value={form.roleCode} onChange={e => f('roleCode', e.target.value)}>
              <option value="USER">일반 사용자</option>
              <option value="INSTITUTION_ADMIN">기관 관리자</option>
            </select>
            <I.Chevron width={13} height={13} className="ia-field__caret" />
          </Field>
        </div>
        <div className="ia-form__row">
          <Field icon={<I.Mail width={14} height={14} />}>
            <input className="ia-field__input" type="email" inputMode="email" autoComplete="email" placeholder="이메일 (선택)" value={form.email} onChange={e => f('email', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
          <Field icon={<I.Phone width={14} height={14} />}>
            <input className="ia-field__input" type="tel" inputMode="tel" autoComplete="tel" placeholder="휴대폰 번호 (선택)" value={form.phone} onChange={e => f('phone', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
        </div>
        <div className="ia-form__btns">
          <Btn type="submit" accent={accent} loading={saving}><I.Save width={14} height={14} />{editId ? '수정 저장' : '사용자 저장'}</Btn>
          {editId && <Btn ghost onClick={() => { setEditId(null); setForm(emptyForm); }}>취소</Btn>}
        </div>
      </form>

      <div className="ia-table-wrap">
        <table className="ia-table">
          <thead><tr><th style={{ width: 36 }}></th><th>아이디</th><th>이름</th><th>역할</th><th>상태</th><th style={{ width: 44 }}></th></tr></thead>
          <tbody>
            {users.length === 0 && <tr><td colSpan={6} className="ia-empty">사용자 없음</td></tr>}
            {users.map(u => (
              <tr key={u.userId} className={`ia-table__row ${u.userId === editId ? 'is-editing' : ''}`} style={{ '--row-h': compact ? '38px' : '48px' }}>
                <td><Avatar name={u.name || u.userId} size={24} /></td>
                <td className="ia-table__code">{u.userId}</td>
                <td className="ia-table__name">{u.name}</td>
                <td style={{ fontSize: 11, color: 'var(--ink-500)', fontWeight: 600 }}>{u.roleCode === 'INSTITUTION_ADMIN' ? '기관관리자' : '일반'}</td>
                <td><span className={`ia-badge ${u.status === '사용' ? 'ia-badge--on' : 'ia-badge--off'}`}>{u.status}</span></td>
                <td className="ia-table__actions"><button className="ia-row-btn" onClick={() => handleEdit(u)}><I.Pen width={13} height={13} /></button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ position: 'relative' }}><Toast {...(toast || {})} /></div>
    </div>
  );
}

/* ============================================================
   PANEL 4: User Permissions
   ============================================================ */
function UserPermPanel({ selectedInstCode, users, allServices, accent }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  const [selUser, setSelUser] = React.useState(users[0]?.userId || '');
  const [perms, setPerms] = React.useState({});
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [toast, showToast] = useToast();

  React.useEffect(() => {
    if (!users.find(u => u.userId === selUser) && users.length) setSelUser(users[0].userId);
  }, [users]);

  React.useEffect(() => {
    if (!selUser || !selectedInstCode) return;
    setLoading(true);
    fetch(`/admin/async-data?instCode=${encodeURIComponent(selectedInstCode)}&userAccessUsername=${encodeURIComponent(selUser)}`)
      .then(r => r.json())
      .then(d => setPerms(p => ({ ...p, [selUser]: new Set(d.userEnabledServiceCodes || []) })))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [selUser, selectedInstCode]);

  function toggle(code) {
    setPerms(p => {
      const s = new Set(p[selUser] || []);
      s.has(code) ? s.delete(code) : s.add(code);
      return { ...p, [selUser]: s };
    });
  }

  async function handleSave() {
    setSaving(true);
    try {
      await postForm('/admin/user-access', { instCode: selectedInstCode, username: selUser, enabledServiceCodes: [...(perms[selUser] || new Set())] });
      showToast('권한이 저장되었습니다.');
    } catch (err) { showToast(err.message || '저장 실패', true); }
    finally { setSaving(false); }
  }

  const curPerms = perms[selUser] || new Set();
  const curUser = users.find(u => u.userId === selUser);

  return (
    <div className="ia-panel" style={{ position: 'relative' }}>
      <PanelHead icon={<I.Shield width={16} height={16} />} gradient="linear-gradient(135deg,#6c7d97,#475569)" title="사용자 권한" sub="사용자별 서비스 접근 권한을 설정합니다." />

      <Field icon={<I.User width={14} height={14} />}>
        <select className="ia-field__input ia-field__select" value={selUser} onChange={e => setSelUser(e.target.value)} style={{ '--ring': a.to }}>
          {users.length === 0 && <option value="">사용자 없음</option>}
          {users.map(u => <option key={u.userId} value={u.userId}>{u.name} ({u.userId})</option>)}
        </select>
        <I.Chevron width={13} height={13} className="ia-field__caret" />
      </Field>

      {curUser && (
        <div className="ia-user-badge">
          <Avatar name={curUser.name || curUser.userId} size={28} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13 }}>{curUser.name}</div>
            <div style={{ fontSize: 11, color: 'var(--ink-500)' }}>{curUser.org} · {curUser.roleCode === 'INSTITUTION_ADMIN' ? '기관관리자' : '일반사용자'}</div>
          </div>
        </div>
      )}

      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '16px 0' }}>
          <I.Spinner width={20} height={20} className="ia-spin" style={{ color: 'var(--ink-300)' }} />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {allServices.map(svc => (
            <CheckItem key={svc.serviceCode} on={curPerms.has(svc.serviceCode)} label={svc.serviceName} sub={svc.serviceCode} accent={accent} onToggle={() => toggle(svc.serviceCode)} />
          ))}
          {allServices.length === 0 && <div className="ia-empty">서비스 없음</div>}
        </div>
      )}

      <Btn accent={accent} full loading={saving} disabled={!selUser || loading} onClick={handleSave}>
        <I.Shield width={14} height={14} />권한 저장
      </Btn>

      <div style={{ position: 'relative' }}><Toast {...(toast || {})} /></div>
    </div>
  );
}

/* ============================================================
   PANEL 5: Maintenance Page Editor
   ============================================================ */
function MaintenancePagePanel() {
  const orangeGradient = 'linear-gradient(135deg,#c2410c,#ea580c)';
  const [html, setHtml] = React.useState(__ADMIN__.maintenanceHtml || '');
  const [saving, setSaving] = React.useState(false);
  const [toast, showToast] = useToast();

  function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    const csrf = getCsrf();
    const fd = new FormData();
    fd.append('content', html);
    const headers = {};
    if (csrf.token) headers[csrf.header] = csrf.token;
    fetch('/admin/maintenance', { method: 'POST', headers, body: fd })
      .then(res => {
        if (!res.ok) throw new Error('저장 실패');
        showToast('점검 페이지가 저장되었습니다.');
      })
      .catch(err => showToast(err.message || '저장 실패', true))
      .finally(() => setSaving(false));
  }

  return (
    <div className="ia-panel" style={{ position: 'relative' }}>
      <PanelHead
        icon={<I.Shield width={16} height={16} />}
        gradient={orangeGradient}
        title="서비스 점검 페이지"
        sub="서버 점검 시 고객에게 표시되는 페이지를 작성합니다"
      />
      <form className="ia-form" onSubmit={handleSubmit}>
        <textarea
          className="ia-field__textarea"
          placeholder="점검 페이지 HTML을 작성하세요"
          value={html}
          onChange={e => setHtml(e.target.value)}
          style={{ minHeight: 240, fontFamily: 'monospace', fontSize: 13, '--ring': '#ea580c' }}
        />
        <div className="ia-form__btns">
          <Btn type="submit" loading={saving} style={{ background: orangeGradient }}>
            <I.Save width={14} height={14} />저장
          </Btn>
        </div>
      </form>
      <div style={{ position: 'relative' }}><Toast {...(toast || {})} /></div>
    </div>
  );
}

/* ============================================================
   PANEL 6: News Article Management
   ============================================================ */
function NewsletterManagementPanel({ accent }) {
  const a = IA_ACCENTS[accent] || IA_ACCENTS.blue;
  const emptyForm = {
    code: '',
    title: '',
    summary: '',
    category: '',
    tags: '',
    cadence: '출처',
    url: '',
    useYn: 'Y',
    displayOrder: 0,
  };
  const [items, setItems] = React.useState([]);
  const [form, setForm] = React.useState(emptyForm);
  const [editCode, setEditCode] = React.useState(null);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [toast, showToast] = useToast();

  React.useEffect(() => { loadItems(); }, []);

  async function loadItems() {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/newsletters', { credentials: 'same-origin' });
      if (!res.ok) throw new Error('뉴스/기사 목록을 불러오지 못했습니다.');
      const data = await res.json();
      setItems(data.items || []);
    } catch (err) {
      showToast(err.message || '뉴스/기사 목록을 불러오지 못했습니다.', true);
    } finally {
      setLoading(false);
    }
  }

  function f(key, value) {
    setForm(prev => ({ ...prev, [key]: value }));
  }

  function handleEdit(item) {
    setEditCode(item.code);
    setForm({
      code: item.code || '',
      title: item.title || '',
      summary: item.summary || '',
      category: item.category || '',
      tags: item.tags || '',
      cadence: item.cadence || '',
      url: item.url || '',
      useYn: item.useYn || 'Y',
      displayOrder: item.displayOrder ?? 0,
    });
  }

  async function handleSave(event) {
    event.preventDefault();
    if (!form.code || !form.title || !form.url) {
      showToast('코드, 제목, URL은 필수입니다.', true);
      return;
    }
    setSaving(true);
    try {
      const data = await postJson('/api/admin/newsletters', form);
      setItems(data.items || []);
      setEditCode(null);
      setForm(emptyForm);
      showToast('뉴스/기사가 저장되었습니다.');
    } catch (err) {
      showToast(err.message || '저장 실패', true);
    } finally {
      setSaving(false);
    }
  }

  async function handleStatus(item) {
    const nextUseYn = item.useYn === 'Y' ? 'N' : 'Y';
    try {
      const data = await postJson('/api/admin/newsletters/status', { code: item.code, useYn: nextUseYn });
      setItems(data.items || []);
      showToast(nextUseYn === 'Y' ? '뉴스/기사가 활성화되었습니다.' : '뉴스/기사가 비활성화되었습니다.');
    } catch (err) {
      showToast(err.message || '상태 변경 실패', true);
    }
  }

  return (
    <div className="ia-panel ia-panel--newsletter" style={{ position: 'relative' }}>
      <PanelHead
        icon={<I.Mail width={16} height={16} />}
        gradient={`linear-gradient(135deg,${a.mid},${a.to})`}
        title="뉴스/기사 관리"
        sub="포털 추천 피드에 노출할 기사 제목, URL, 태그, 상태를 관리합니다."
      />

      <form className="ia-form" onSubmit={handleSave}>
        <div className="ia-form__row">
          <Field icon={<I.Tag width={14} height={14} />}>
            <input className="ia-field__input" placeholder="코드 (예: KOREA_HEALTH_ARTICLE)" value={form.code} onChange={e => f('code', e.target.value.toUpperCase())} disabled={!!editCode} style={{ '--ring': a.to }} />
          </Field>
          <Field icon={<I.Mail width={14} height={14} />}>
            <input className="ia-field__input" placeholder="기사 제목" value={form.title} onChange={e => f('title', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
        </div>
        <div className="ia-form__row">
          <Field icon={<I.Globe width={14} height={14} />}>
            <input className="ia-field__input" placeholder="카테고리" value={form.category} onChange={e => f('category', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
          <Field icon={<I.Chevron width={14} height={14} />}>
            <input className="ia-field__input" placeholder="출처 (예: 데일리메디)" value={form.cadence} onChange={e => f('cadence', e.target.value)} style={{ '--ring': a.to }} />
          </Field>
        </div>
        <Field icon={<I.Link width={14} height={14} />}>
          <input className="ia-field__input" placeholder="https://..." value={form.url} onChange={e => f('url', e.target.value)} style={{ '--ring': a.to }} />
        </Field>
        <textarea className="ia-field__textarea" placeholder="요약" value={form.summary} onChange={e => f('summary', e.target.value)} style={{ '--ring': a.to }} />
        <div className="ia-form__row">
          <Field>
            <input className="ia-field__input" placeholder="태그 (쉼표 구분)" value={form.tags} onChange={e => f('tags', e.target.value)} style={{ paddingLeft: 14, '--ring': a.to }} />
          </Field>
          <Field>
            <input className="ia-field__input" type="number" placeholder="노출 순서" value={form.displayOrder} onChange={e => f('displayOrder', parseInt(e.target.value) || 0)} style={{ paddingLeft: 14, '--ring': a.to }} />
          </Field>
        </div>
        <div className="ia-form__row">
          <Field>
            <select className="ia-field__input ia-field__select" value={form.useYn} onChange={e => f('useYn', e.target.value)} style={{ paddingLeft: 14, '--ring': a.to }}>
              <option value="Y">사용</option>
              <option value="N">미사용</option>
            </select>
            <I.Chevron width={13} height={13} className="ia-field__caret" />
          </Field>
          <div className="ia-form__btns">
            <Btn type="submit" accent={accent} loading={saving}><I.Save width={14} height={14} />{editCode ? '수정 저장' : '뉴스/기사 저장'}</Btn>
            {editCode && <Btn ghost onClick={() => { setEditCode(null); setForm(emptyForm); }}>취소</Btn>}
          </div>
        </div>
      </form>

      <div className="ia-table-wrap ia-table-wrap--newsletter">
        <table className="ia-table">
          <thead><tr><th>코드</th><th>뉴스/기사</th><th>카테고리</th><th>상태</th><th style={{ width: 86 }}></th></tr></thead>
          <tbody>
            {loading && <tr><td colSpan={5} className="ia-empty">불러오는 중…</td></tr>}
            {!loading && items.length === 0 && <tr><td colSpan={5} className="ia-empty">등록된 뉴스/기사 없음</td></tr>}
            {!loading && items.map(item => (
              <tr key={item.code} className={`ia-table__row ${item.code === editCode ? 'is-editing' : ''}`}>
                <td className="ia-table__code">{item.code}</td>
                <td>
                  <div className="ia-table__name">{item.title}</div>
                  <div className="ia-table__url">{item.url}</div>
                </td>
                <td style={{ fontSize: 12, color: 'var(--ink-500)', fontWeight: 600 }}>{item.category}</td>
                <td><span className={`ia-badge ${item.useYn === 'Y' ? 'ia-badge--on' : 'ia-badge--off'}`}>{item.useYn === 'Y' ? '사용' : '중지'}</span></td>
                <td className="ia-table__actions">
                  <button className="ia-row-btn" onClick={() => handleEdit(item)} title="수정"><I.Pen width={13} height={13} /></button>
                  <button className="ia-row-btn" onClick={() => handleStatus(item)} title={item.useYn === 'Y' ? '비활성화' : '활성화'}><I.Power width={13} height={13} /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ position: 'relative' }}><Toast {...(toast || {})} /></div>
    </div>
  );
}

/* ============================================================
   CSS
   ============================================================ */
const css = `
.ia-page,
.ia-layout,
.ia-sidebar,
.ia-table-wrap {
  scrollbar-width: thin;
  scrollbar-color: rgba(100,116,139,0.38) transparent;
}
.ia-page::-webkit-scrollbar,
.ia-layout::-webkit-scrollbar,
.ia-sidebar::-webkit-scrollbar,
.ia-table-wrap::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
.ia-page::-webkit-scrollbar-track,
.ia-layout::-webkit-scrollbar-track,
.ia-sidebar::-webkit-scrollbar-track,
.ia-table-wrap::-webkit-scrollbar-track {
  background: transparent;
}
.ia-page::-webkit-scrollbar-thumb,
.ia-layout::-webkit-scrollbar-thumb,
.ia-sidebar::-webkit-scrollbar-thumb,
.ia-table-wrap::-webkit-scrollbar-thumb {
  background: rgba(100,116,139,0.28);
  border: 2px solid transparent;
  border-radius: 999px;
  background-clip: content-box;
}
.ia-page::-webkit-scrollbar-thumb:hover,
.ia-layout::-webkit-scrollbar-thumb:hover,
.ia-sidebar::-webkit-scrollbar-thumb:hover,
.ia-table-wrap::-webkit-scrollbar-thumb:hover {
  background: rgba(100,116,139,0.48);
  border: 2px solid transparent;
  background-clip: content-box;
}
.ia-page { min-height: 100vh; display: flex; flex-direction: column; background: transparent; }

/* Header */
.ia-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 28px; background: rgba(255,255,255,0.85); backdrop-filter: blur(12px); border-bottom: 1px solid var(--ink-100); position: sticky; top: 0; z-index: 10; }
.ia-header__left { display: flex; align-items: center; gap: 14px; }
.ia-header__back { width: 34px; height: 34px; border-radius: 9px; display: grid; place-items: center; color: var(--ink-600); border: 1px solid var(--ink-200); background: #fff; transition: all .12s; }
.ia-header__back:hover { color: var(--ink-900); border-color: var(--ink-300); }
.ia-header__eyebrow { font-size: 10px; letter-spacing: 0.2em; color: var(--ink-500); font-weight: 700; }
.ia-header__title { font-size: 22px; font-weight: 800; letter-spacing: -0.03em; margin: 2px 0 0; }
.ia-header__actions { display: flex; gap: 8px; }
.ia-header__btn { display: inline-flex; align-items: center; gap: 6px; height: 36px; padding: 0 16px; border-radius: 999px; background: #fff; border: 1px solid var(--ink-200); color: var(--ink-700); font-size: 13px; font-weight: 600; transition: all .12s; }
.ia-header__btn:hover { border-color: var(--cs-blue-500); color: var(--cs-blue-600); }
.ia-header__btn--ghost { background: transparent; }

/* Layout */
.ia-layout { display: flex; gap: 20px; padding: 20px 24px 48px; max-width: 1440px; width: 100%; margin: 0 auto; box-sizing: border-box; flex: 1; align-items: flex-start; }
.ia-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 20px; }
.ia-main__row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
@media (max-width: 1100px) { .ia-main__row { grid-template-columns: 1fr; } }
@media (max-width: 860px)  { .ia-layout { flex-direction: column; } .ia-sidebar { width: 100%; } }

/* Sidebar */
.ia-sidebar { width: 260px; flex-shrink: 0; background: #fff; border: 1px solid var(--ink-100); border-radius: 16px; padding: 18px; box-shadow: 0 2px 10px -4px rgba(11,31,74,0.07); display: flex; flex-direction: column; gap: 10px; position: sticky; top: 76px; max-height: calc(100vh - 96px); overflow-y: auto; }
.ia-sidebar__head { display: flex; align-items: center; justify-content: space-between; }
.ia-sidebar__title { font-size: 13px; font-weight: 700; color: var(--ink-700); }

/* Panel */
.ia-panel { background: #fff; border: 1px solid var(--ink-100); border-radius: 16px; padding: 22px; box-shadow: 0 2px 10px -4px rgba(11,31,74,0.07); display: flex; flex-direction: column; gap: 14px; }
.ia-panel--empty { align-items: center; justify-content: center; min-height: 180px; gap: 10px; }
.ia-panel__head { display: flex; align-items: flex-start; gap: 12px; }
.ia-panel__head-icon { width: 34px; height: 34px; border-radius: 9px; display: grid; place-items: center; color: #fff; flex-shrink: 0; box-shadow: 0 4px 10px -4px rgba(11,31,74,0.3); }
.ia-panel__title { font-size: 15px; font-weight: 800; letter-spacing: -0.02em; }
.ia-panel__sub { font-size: 11.5px; color: var(--ink-500); margin-top: 2px; line-height: 1.4; }
.ia-section-label { font-size: 10.5px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: var(--ink-400); }

/* Icon button */
.ia-icon-btn { width: 30px; height: 30px; border-radius: 8px; display: grid; place-items: center; color: var(--ink-400); border: 1px solid var(--ink-100); transition: all .12s; flex-shrink: 0; }
.ia-icon-btn:hover { background: var(--ink-50); color: var(--ink-700); border-color: var(--ink-200); }
.ia-icon-btn--warn:hover { background: #fef2f2; color: #dc2626; border-color: #fecaca; }
.ia-icon-btn--ok:hover  { background: #ecfdf5; color: #047857; border-color: #a7f3d0; }

/* Form */
.ia-form { display: flex; flex-direction: column; gap: 9px; }
.ia-form__row { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }
.ia-form__btns { display: flex; gap: 8px; margin-top: 2px; }
.ia-inset-form { background: var(--ink-50); border: 1px solid var(--ink-100); border-radius: 10px; padding: 12px; display: flex; flex-direction: column; gap: 8px; }

/* Fields */
.ia-field { position: relative; display: flex; align-items: center; }
.ia-field__icon { position: absolute; left: 13px; color: var(--ink-400); pointer-events: none; transition: color .12s; display: flex; }
.ia-field__input { width: 100%; height: 40px; padding: 0 13px 0 38px; background: #eef4ff; border: 1.5px solid transparent; border-radius: 9px; font-size: 13px; color: var(--ink-900); outline: none; appearance: none; transition: border-color .14s, box-shadow .14s, background .14s; }
.ia-field__input--sm { height: 36px; font-size: 12.5px; }
.ia-field__input:hover { background: #e6eeff; }
.ia-field__input:focus { background: #fff; border-color: var(--ring, #1d4ed8); box-shadow: 0 0 0 3px color-mix(in oklab, var(--ring, #1d4ed8) 14%, transparent); }
.ia-field__input:disabled { opacity: 0.55; cursor: not-allowed; }
.ia-field__select { padding-right: 32px; cursor: pointer; }
.ia-field__caret { position: absolute; right: 10px; color: var(--ink-400); pointer-events: none; }
.ia-field:focus-within .ia-field__icon { color: var(--ring, #1d4ed8); }
.ia-field__textarea { width: 100%; min-height: 74px; resize: vertical; padding: 12px 13px; background: #eef4ff; border: 1.5px solid transparent; border-radius: 9px; font-size: 13px; color: var(--ink-900); outline: none; transition: border-color .14s, box-shadow .14s, background .14s; }
.ia-field__textarea:focus { background: #fff; border-color: var(--ring, #1d4ed8); box-shadow: 0 0 0 3px color-mix(in oklab, var(--ring, #1d4ed8) 14%, transparent); }
.ia-plain-input { width: 100%; height: 36px; padding: 0 10px; background: #fff; border: 1.5px solid var(--ink-200); border-radius: 8px; font-size: 12.5px; color: var(--ink-900); outline: none; }
.ia-plain-input:focus { border-color: var(--ring, #1d4ed8); box-shadow: 0 0 0 3px color-mix(in oklab, var(--ring,#1d4ed8) 14%, transparent); }

/* Buttons */
.ia-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 42px; padding: 0 18px; border-radius: 9px; color: #fff; font-weight: 700; font-size: 13px; box-shadow: 0 5px 12px -6px rgba(11,31,74,0.45); transition: transform .12s, box-shadow .14s, opacity .12s; flex: 1; }
.ia-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 10px 20px -8px rgba(11,31,74,0.4); }
.ia-btn:active:not(:disabled) { transform: translateY(0); }
.ia-btn:disabled { opacity: 0.75; cursor: progress; }
.ia-btn--ghost { background: #fff !important; color: var(--ink-700); border: 1.5px solid var(--ink-200); box-shadow: none; flex: 0 0 auto; padding: 0 14px; }
.ia-btn--ghost:hover:not(:disabled) { border-color: var(--ink-300); color: var(--ink-900); transform: none; }
.ia-btn--full { width: 100%; flex: unset; }
.ia-spin { animation: ia-spin .9s linear infinite; }
@keyframes ia-spin { to { transform: rotate(360deg); } }

/* Institution list */
.ia-inst-list { display: flex; flex-direction: column; gap: 3px; }
.ia-inst-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 10px; border-radius: 9px; border: 1.5px solid transparent; text-align: left; transition: all .12s; width: 100%; }
.ia-inst-item:hover { background: var(--tint, #eff4ff); border-color: color-mix(in oklab, var(--to, #1d4ed8) 20%, transparent); }
.ia-inst-item.is-selected { background: var(--tint, #eff4ff); border-color: var(--to, #1d4ed8); }
.ia-inst-item__name { font-size: 13px; font-weight: 600; color: var(--ink-800); }
.ia-inst-item__code { font-size: 10.5px; letter-spacing: 0.05em; color: var(--ink-400); font-weight: 600; margin-top: 1px; }

/* Check items */
.ia-check-item { display: flex; align-items: center; gap: 11px; padding: 11px 13px; border-radius: 9px; border: 1.5px solid var(--ink-100); background: var(--ink-50); cursor: pointer; transition: border-color .12s, background .12s, box-shadow .12s; user-select: none; }
.ia-check-item:hover { border-color: var(--ring, #bfdbfe); background: color-mix(in oklab, var(--to,#1d4ed8) 5%, #fff); }
.ia-check-item--on { border-color: var(--ring, #bfdbfe); background: color-mix(in oklab, var(--to,#1d4ed8) 6%, #fff); box-shadow: 0 0 0 2px color-mix(in oklab, var(--to,#1d4ed8) 8%, transparent); }
.ia-checkbox { width: 18px; height: 18px; border: 1.5px solid var(--ink-300); border-radius: 5px; display: grid; place-items: center; color: #fff; flex-shrink: 0; transition: all .14s; }
.ia-check-label { flex: 1; display: flex; flex-direction: column; gap: 1px; }
.ia-check-name { font-size: 13.5px; font-weight: 600; letter-spacing: -0.01em; }
.ia-check-sub  { font-size: 10px; letter-spacing: 0.07em; color: var(--ink-400); font-weight: 600; }

/* Table */
.ia-table-wrap { border: 1px solid var(--ink-100); border-radius: 10px; overflow: hidden; overflow-y: auto; max-height: 280px; }
.ia-table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: 12.5px; }
.ia-table thead th { position: sticky; top: 0; z-index: 2; background: var(--ink-50); font-size: 10.5px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--ink-500); padding: 8px 12px; border-bottom: 1px solid var(--ink-100); text-align: left; white-space: nowrap; }
.ia-table__row { transition: background .1s; height: var(--row-h, 46px); }
.ia-table__row:hover { background: #eff4ff; }
.ia-table__row.is-editing { background: #fefce8; box-shadow: inset 3px 0 0 #d97706; }
.ia-table tbody td { padding: 7px 12px; border-bottom: 1px solid var(--ink-100); vertical-align: middle; }
.ia-table tbody tr:last-child td { border-bottom: 0; }
.ia-table__code { font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--ink-500); }
.ia-table__name { font-weight: 600; color: var(--ink-800); }
.ia-table__actions { text-align: right; }
.ia-table__url { margin-top: 2px; color: var(--ink-400); font-size: 11px; max-width: 460px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ia-table-wrap--newsletter { max-height: 320px; }
.ia-panel--newsletter { width: 100%; }

/* Badges */
.ia-badge { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; white-space: nowrap; }
.ia-badge::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
.ia-badge--on  { color: #047857; background: #ecfdf5; }
.ia-badge--off { color: var(--ink-500); background: var(--ink-100); }

/* Row button */
.ia-row-btn { width: 26px; height: 26px; border-radius: 6px; display: inline-grid; place-items: center; color: var(--ink-400); border: 1px solid transparent; transition: all .1s; }
.ia-row-btn:hover { background: var(--ink-100); color: var(--ink-700); border-color: var(--ink-200); }

/* User badge */
.ia-user-badge { display: flex; align-items: center; gap: 10px; padding: 10px 12px; background: var(--ink-50); border: 1px solid var(--ink-100); border-radius: 9px; }

/* Misc */
.ia-empty { text-align: center; color: var(--ink-400); font-size: 12.5px; padding: 16px 0; }

/* Toast */
.ia-toast { position: absolute; bottom: -8px; left: 50%; transform: translateX(-50%) translateY(100%); background: #0f172a; color: #fff; padding: 9px 16px; border-radius: 999px; font-size: 12.5px; font-weight: 500; box-shadow: 0 10px 20px -6px rgba(11,31,74,0.3); display: inline-flex; align-items: center; gap: 7px; animation: ia-pop .22s cubic-bezier(0.34,1.56,0.64,1); z-index: 30; white-space: nowrap; }
.ia-toast svg { color: #10b981; }
.ia-toast--err { background: #991b1b; }
@keyframes ia-pop { from { opacity: 0; transform: translateX(-50%) translateY(calc(100% + 4px)) scale(.94); } to { opacity: 1; transform: translateX(-50%) translateY(100%) scale(1); } }
`;

/* ============================================================
   App Root
   ============================================================ */
function InstitutionAdminApp() {
  const [tweaks, setTweak] = window.useTweaks(IA_TWEAK_DEFAULTS);
  const canManagePlatform = __ADMIN__.canManagePlatform === true;

  const [institutions, setInstitutions] = React.useState(__ADMIN__.institutions || []);
  const [allServices, setAllServices] = React.useState(__ADMIN__.allServices || []);
  const [selectedInstCode, setSelectedInstCode] = React.useState(__ADMIN__.instCode || '');
  const [enabledServiceCodes, setEnabledServiceCodes] = React.useState(__ADMIN__.enabledServiceCodes || []);
  const [users, setUsers] = React.useState(__ADMIN__.users || []);

  async function loadInstData(instCode) {
    if (!instCode) return;
    try {
      const res = await fetch(`/admin/async-data?instCode=${encodeURIComponent(instCode)}`);
      if (!res.ok) return;
      const d = await res.json();
      setEnabledServiceCodes(d.enabledServiceCodes || []);
      setUsers((d.institutionUsers || []).map(u => ({ id: u.id || u.username, org: u.instCode, userId: u.username, name: u.displayName || u.username, email: u.email || '', phone: u.phone || '', roleCode: u.roleCode || 'USER', status: u.useYn === 'Y' ? '사용' : '미사용' })));
      // async-data only returns {serviceCode, serviceName}, keep full service data from initial load
    } catch (e) { console.error(e); }
  }

  async function handleSelectInst(code) {
    setSelectedInstCode(code);
    await loadInstData(code);
  }

  function reloadPage() { window.location.reload(); }

  const instInfo = institutions.find(i => i.instCode === selectedInstCode);

  return (
    <>
      <style>{css}</style>
      <div className="ia-page">
        <Header onBack={() => window.location.href = '/portal'} onLogout={() => window.location.href = '/logout'} />
        <div className="ia-layout">
          {canManagePlatform && (
            <InstitutionSidebar
              institutions={institutions}
              selectedInstCode={selectedInstCode}
              onSelect={handleSelectInst}
              onAdded={reloadPage}
              accent={tweaks.accent}
            />
          )}

          <div className="ia-main">
            {canManagePlatform && (
              <div className="ia-main__row">
                <InstSettingsPanel
                  selectedInstCode={selectedInstCode}
                  instInfo={instInfo}
                  allServices={allServices}
                  enabledServiceCodes={enabledServiceCodes}
                  onSaved={() => loadInstData(selectedInstCode)}
                  accent={tweaks.accent}
                />
                <AppManagementPanel
                  allServices={allServices}
                  onSaved={reloadPage}
                  accent={tweaks.accent}
                />
            </div>
          )}

            {canManagePlatform && (
              <NewsletterManagementPanel accent={tweaks.accent} />
            )}

            {canManagePlatform && (
              <MaintenancePagePanel />
            )}

            <div className="ia-main__row">
              <UserManagementPanel
                selectedInstCode={selectedInstCode}
                users={users}
                setUsers={setUsers}
                accent={tweaks.accent}
                density={tweaks.density}
              />
              <UserPermPanel
                selectedInstCode={selectedInstCode}
                users={users}
                allServices={allServices}
                accent={tweaks.accent}
              />
            </div>
          </div>
        </div>
      </div>

      <window.TweaksPanel title="Tweaks · 기관 관리">
        <window.TweakSection title="스타일">
          <window.TweakRadio label="브랜드 컬러" value={tweaks.accent}
            options={[{ value: 'blue', label: 'Blue' }, { value: 'teal', label: 'Teal' }, { value: 'indigo', label: 'Indigo' }]}
            onChange={v => setTweak('accent', v)} />
          <window.TweakRadio label="행 밀도" value={tweaks.density}
            options={[{ value: 'comfortable', label: 'Normal' }, { value: 'compact', label: 'Compact' }]}
            onChange={v => setTweak('density', v)} />
        </window.TweakSection>
      </window.TweaksPanel>
    </>
  );
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<InstitutionAdminApp />);
