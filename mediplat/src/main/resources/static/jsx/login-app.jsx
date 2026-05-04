/* MediPlat · Login
   3 visual variations exposed via Tweaks
   - "split"   : panel split, parted gradient hero (matches reference)
   - "stacked" : centered card on full-bleed brand gradient
   - "minimal" : clean white card, top brand bar only
*/

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "variant": "split",
  "accent": "blue",
  "showRemember": true,
  "showForgot": true,
  "heroCopy": "default",
  "showSubtleBg": true
}/*EDITMODE-END*/;

/* ------------ Brand mark (CoreSolution-ish) ------------ */
function BrandMark({ size = 36 }) {
  return (
    <img
      src="/img/core_logo_hospital_w.png"
      alt="CoreSolution"
      style={{ height: size, width: 'auto', display: 'block' }}
    />
  );
}

/* ------------ Icons ------------ */
const Icon = {
  Eye: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>,
  EyeOff: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M17.94 17.94A10.94 10.94 0 0 1 12 19c-6.5 0-10-7-10-7a17.43 17.43 0 0 1 4.06-4.94"/><path d="M22 12s-1.06 2.13-3 4"/><path d="M9.9 4.24A10.5 10.5 0 0 1 12 4c6.5 0 10 7 10 7"/><line x1="2" y1="2" x2="22" y2="22"/></svg>,
  Building: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2"/></svg>,
  User: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M20 21a8 8 0 1 0-16 0"/><circle cx="12" cy="7" r="4"/></svg>,
  Lock: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>,
  Spinner: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" {...p}><path d="M21 12a9 9 0 1 1-6.2-8.55"/></svg>,
  Alert: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>,
  Check: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" {...p}><polyline points="20 6 9 17 4 12"/></svg>,
};

/* ------------ Accent palette swap ------------ */
const ACCENTS = {
  blue: { from: '#0b1f4a', mid: '#173a82', to: '#1d4ed8', tint: '#e0e8ff', cyan: '#38bdf8' },
  teal: { from: '#062f2a', mid: '#0d544a', to: '#0d9488', tint: '#d1f5ee', cyan: '#5eead4' },
  indigo: { from: '#1e1b4b', mid: '#312e81', to: '#4f46e5', tint: '#e0e7ff', cyan: '#a5b4fc' },
};

/* ------------ Hero copy options ------------ */
const HERO_COPY = {
  default: {
    eyebrow: 'INTEGRATED HEALTHCARE PLATFORM',
    title: 'MediPlat',
    body: '기관코드/기관명 + 아이디로 로그인하며, MediPlat 계정 우선 인증 후 미등록 계정은 CounselMan 계정으로 fallback 인증합니다.',
  },
  short: {
    eyebrow: 'INTEGRATED HEALTHCARE PLATFORM',
    title: 'MediPlat',
    body: '하나의 계정으로 병원 운영의 모든 영역을 연결합니다.',
  },
  cta: {
    eyebrow: 'CORESOLUTION · HOSPITAL OS',
    title: '진료의 흐름을\n매끄럽게.',
    body: '상담부터 입원, 병실 운영, AI 분석까지 — 한 곳에서.',
  },
};

/* ============================================================== */
/*  Form (shared across variants)                                  */
/* ============================================================== */
function LoginForm({ tweaks }) {
  const init = window.__MP_LOGIN__ || {};
  const [orgCode, setOrgCode] = React.useState(init.instCode || '');
  const [userId, setUserId] = React.useState(init.username || '');
  const [pw, setPw] = React.useState('');
  const [showPw, setShowPw] = React.useState(false);
  const [remember, setRemember] = React.useState(true);
  const [state, setState] = React.useState(init.error ? 'error' : 'idle');
  const [errorMsg, setErrorMsg] = React.useState(init.error || '');
  const [touched, setTouched] = React.useState(false);

  function submit(e) {
    e.preventDefault();
    setTouched(true);
    if (!userId || !pw) {
      setState('error');
      setErrorMsg('아이디와 비밀번호를 입력해주세요.');
      return;
    }
    setState('loading');
    setErrorMsg('');
    document.getElementById('mp-ic').value = orgCode;
    document.getElementById('mp-un').value = userId;
    document.getElementById('mp-pw').value = pw;
    document.getElementById('mp-login-form').submit();
  }

  const accent = ACCENTS[tweaks.accent] || ACCENTS.blue;
  const isLoading = state === 'loading';
  const isError = state === 'error';

  return (
    <form className="lf" onSubmit={submit} noValidate>
      <Field
        label="기관코드 또는 기관명"
        icon={<Icon.Building width={16} height={16} />}
        value={orgCode}
        onChange={setOrgCode}
        placeholder="falh"
        invalid={touched && !orgCode}
        accent={accent}
      />
      <Field
        label="아이디"
        icon={<Icon.User width={16} height={16} />}
        value={userId}
        onChange={setUserId}
        placeholder="coreadmin"
        invalid={touched && !userId}
        accent={accent}
      />
      <Field
        label="비밀번호"
        icon={<Icon.Lock width={16} height={16} />}
        value={pw}
        onChange={setPw}
        placeholder="비밀번호"
        type={showPw ? 'text' : 'password'}
        invalid={touched && !pw}
        accent={accent}
        rightAdornment={
          <button type="button" className="lf__eye" onClick={() => setShowPw(s => !s)} tabIndex={-1} aria-label="비밀번호 표시">
            {showPw ? <Icon.EyeOff width={16} height={16} /> : <Icon.Eye width={16} height={16} />}
          </button>
        }
      />

      {/* Footer row: remember + forgot */}
      {(tweaks.showRemember || tweaks.showForgot) && (
        <div className="lf__row">
          {tweaks.showRemember && (
            <label className="lf__check">
              <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
              <span className="lf__check-box" style={{ borderColor: remember ? accent.to : undefined, background: remember ? accent.to : undefined }}>
                {remember && <Icon.Check width={10} height={10} />}
              </span>
              기억하기
            </label>
          )}
          <span className="lf__row-spacer" />
          {tweaks.showForgot && <a href="#" className="lf__forgot">비밀번호를 잊으셨나요?</a>}
        </div>
      )}

      {/* Error banner */}
      {isError && (
        <div className="lf__error" role="alert">
          <Icon.Alert width={16} height={16} />
          <span>{errorMsg}</span>
        </div>
      )}

      <button
        type="submit"
        className="lf__submit"
        disabled={isLoading}
        style={{ background: `linear-gradient(135deg, ${accent.mid}, ${accent.to})` }}
      >
        {isLoading ? (
          <>
            <Icon.Spinner width={16} height={16} className="lf__spin" />
            인증 중…
          </>
        ) : '로그인'}
      </button>

      <div className="lf__hint">
        <span>MediPlat 계정 우선 인증</span>
        <span className="lf__hint-sep">→</span>
        <span>미등록 시 CounselMan 계정 fallback</span>
      </div>
    </form>
  );
}

function Field({ label, icon, value, onChange, placeholder, type = 'text', invalid, accent, rightAdornment }) {
  const [focus, setFocus] = React.useState(false);
  const ringColor = invalid ? '#dc2626' : focus ? accent.to : 'transparent';
  const borderColor = invalid ? '#fca5a5' : focus ? accent.to : '#e2e8f0';
  return (
    <label className="lf__field">
      <span className="lf__label">{label}</span>
      <div
        className="lf__input-wrap"
        style={{
          borderColor,
          boxShadow: focus || invalid ? `0 0 0 4px ${invalid ? 'rgba(220,38,38,0.12)' : `${accent.to}1f`}` : 'none',
        }}
      >
        <span className="lf__icon" style={{ color: focus ? accent.to : '#94a3b8' }}>{icon}</span>
        <input
          className="lf__input"
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
        />
        {rightAdornment}
      </div>
    </label>
  );
}

/* ============================================================== */
/*  Variant: SPLIT  (matches reference)                            */
/* ============================================================== */
function VariantSplit({ tweaks }) {
  const a = ACCENTS[tweaks.accent] || ACCENTS.blue;
  const copy = HERO_COPY[tweaks.heroCopy] || HERO_COPY.default;
  const heroBg = `
    radial-gradient(900px 500px at 0% 0%, ${a.cyan}33 0%, transparent 50%),
    radial-gradient(700px 400px at 100% 100%, ${a.to}66 0%, transparent 60%),
    linear-gradient(135deg, ${a.from} 0%, ${a.mid} 55%, ${a.to} 110%)
  `;
  return (
    <div className="card card--split">
      <aside className="card__hero" style={{ background: heroBg }}>
        <HeroDecor accent={a} />
        <div className="card__hero-inner">
          <div className="card__brand">
            <BrandMark size={42} dark />
          </div>
          <div className="card__hero-mid">
            <div className="card__eyebrow">{copy.eyebrow}</div>
            <h1 className="card__title" style={{ whiteSpace: 'pre-line' }}>{copy.title}</h1>
            <p className="card__body">{copy.body}</p>
          </div>
          <div className="card__hero-foot">
            <div className="card__foot-pill"><span /> 안정 운영중</div>
            <div className="card__foot-meta">v 2.4.1 · {new Date().getFullYear()}</div>
          </div>
        </div>
      </aside>
      <section className="card__form">
        <div className="card__form-head">
          <div className="card__form-title">로그인</div>
          <div className="card__form-sub">기관 계정으로 접속해주세요.</div>
        </div>
        <LoginForm tweaks={tweaks} />
      </section>
    </div>
  );
}

/* ============================================================== */
/*  Variant: STACKED                                               */
/* ============================================================== */
function VariantStacked({ tweaks }) {
  const a = ACCENTS[tweaks.accent] || ACCENTS.blue;
  const copy = HERO_COPY[tweaks.heroCopy] || HERO_COPY.default;
  return (
    <div className="stacked-shell" style={{
      background: `
        radial-gradient(900px 500px at 80% -20%, ${a.cyan}55 0%, transparent 50%),
        radial-gradient(700px 400px at -10% 110%, ${a.to}77 0%, transparent 55%),
        linear-gradient(160deg, ${a.from} 0%, ${a.mid} 50%, ${a.to} 110%)`
    }}>
      <HeroDecor accent={a} dim />
      <div className="stacked-shell__top">
        <div className="card__brand">
          <BrandMark size={36} dark />
        </div>
      </div>
      <div className="stacked-shell__center">
        <div className="stacked-headline">
          <div className="card__eyebrow" style={{ color: a.cyan }}>{copy.eyebrow}</div>
          <h1 className="stacked-headline__title">{copy.title}</h1>
          <p className="stacked-headline__body">{copy.body}</p>
        </div>
        <div className="card card--stacked">
          <div className="card__form-head">
            <div className="card__form-title">로그인</div>
            <div className="card__form-sub">MediPlat 계정으로 시작</div>
          </div>
          <LoginForm tweaks={tweaks} />
        </div>
      </div>
      <div className="stacked-shell__foot">
        © {new Date().getFullYear()} CoreSolution · 개인정보처리방침 · 이용약관
      </div>
    </div>
  );
}

/* ============================================================== */
/*  Variant: MINIMAL                                               */
/* ============================================================== */
function VariantMinimal({ tweaks }) {
  const a = ACCENTS[tweaks.accent] || ACCENTS.blue;
  const copy = HERO_COPY[tweaks.heroCopy] || HERO_COPY.default;
  return (
    <div className="minimal-shell">
      <div className="minimal-bar" style={{ background: `linear-gradient(90deg, ${a.from}, ${a.mid} 60%, ${a.to})` }}>
        <div className="card__brand card__brand--row">
          <BrandMark size={28} dark />
        </div>
        <div className="minimal-bar__right">
          <div className="card__foot-pill"><span /> 안정 운영중</div>
        </div>
      </div>
      <div className="minimal-body">
        <div className="minimal-grid">
          <div className="minimal-copy">
            <div className="card__eyebrow" style={{ color: a.to }}>{copy.eyebrow}</div>
            <h1 className="minimal-copy__title" style={{ color: a.from }}>{copy.title}</h1>
            <p className="minimal-copy__body">{copy.body}</p>
            <ul className="minimal-list">
              <li><span style={{ background: a.to }} />단일 계정으로 다중 시스템 접속</li>
              <li><span style={{ background: a.to }} />기관 단위 권한 관리</li>
              <li><span style={{ background: a.to }} />CounselMan fallback 인증 지원</li>
            </ul>
          </div>
          <div className="card card--minimal">
            <div className="card__form-head">
              <div className="card__form-title" style={{ color: a.from }}>로그인</div>
              <div className="card__form-sub">기관 계정으로 접속</div>
            </div>
            <LoginForm tweaks={tweaks} />
          </div>
        </div>
      </div>
      <div className="minimal-foot">© {new Date().getFullYear()} CoreSolution</div>
    </div>
  );
}

/* ============================================================== */
/*  Decorative grid + orb for hero                                 */
/* ============================================================== */
function HeroDecor({ accent, dim = false }) {
  return (
    <div className="hero-decor" aria-hidden="true">
      <svg className="hero-decor__grid" viewBox="0 0 600 600" preserveAspectRatio="none" style={{ opacity: dim ? 0.18 : 0.25 }}>
        <defs>
          <pattern id="grid" width="30" height="30" patternUnits="userSpaceOnUse">
            <path d="M 30 0 L 0 0 0 30" fill="none" stroke="rgba(255,255,255,0.6)" strokeWidth="0.5" />
          </pattern>
          <radialGradient id="grid-fade" cx="50%" cy="50%" r="60%">
            <stop offset="0%" stopColor="white" stopOpacity="1" />
            <stop offset="100%" stopColor="white" stopOpacity="0" />
          </radialGradient>
          <mask id="grid-mask"><rect width="600" height="600" fill="url(#grid-fade)" /></mask>
        </defs>
        <rect width="600" height="600" fill="url(#grid)" mask="url(#grid-mask)" />
      </svg>
      <div className="hero-decor__orb" style={{ background: `radial-gradient(circle, ${accent.cyan}55 0%, transparent 70%)` }} />
      <div className="hero-decor__orb hero-decor__orb--2" style={{ background: `radial-gradient(circle, ${accent.to}66 0%, transparent 70%)` }} />
    </div>
  );
}

/* ============================================================== */
/*  Stylesheet (scoped via class)                                  */
/* ============================================================== */
const css = `
.app {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 16px;
}
.app--full { padding: 0; align-items: stretch; grid-template-rows: 1fr; }

/* ----- Cards / shells ----- */
.card {
  background: #fff;
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  width: 100%;
}
.card--split {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  max-width: 980px;
  min-height: 520px;
}
.card--stacked {
  max-width: 440px;
  width: 100%;
  padding: 32px 32px 28px;
  background: rgba(255,255,255,0.98);
  backdrop-filter: blur(10px);
}
.card--minimal {
  max-width: 440px;
  padding: 32px 32px 28px;
  border: 1px solid var(--ink-100);
}

/* ----- Hero (split) ----- */
.card__hero {
  position: relative;
  padding: 36px 36px 32px;
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  isolation: isolate;
}
.card__hero-inner { position: relative; z-index: 2; display: flex; flex-direction: column; height: 100%; gap: 36px; }
.card__hero-mid { display: flex; flex-direction: column; gap: 14px; flex: 1; justify-content: center; }
.card__hero-foot { display: flex; justify-content: space-between; align-items: center; gap: 12px; opacity: 0.85; font-size: 11px; }
.card__foot-pill { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; padding: 4px 10px; border-radius: 999px; background: rgba(255,255,255,0.12); border: 1px solid rgba(255,255,255,0.2); }
.card__foot-pill span { width: 6px; height: 6px; border-radius: 50%; background: #5eead4; box-shadow: 0 0 6px #5eead4; }
.card__foot-meta { font-variant-numeric: tabular-nums; letter-spacing: 0.05em; }

/* ----- Brand mark ----- */
.card__brand { display: flex; align-items: center; gap: 12px; }
.card__brand--row { gap: 10px; }
.card__brand-name { font-weight: 800; font-size: 14px; letter-spacing: 0.15em; line-height: 1; color: #fff; }
.card__brand-sub  { font-size: 9.5px; letter-spacing: 0.18em; line-height: 1.2; color: rgba(255,255,255,0.72); margin-top: 4px; }

.card__eyebrow {
  font-size: 11px;
  letter-spacing: 0.22em;
  font-weight: 600;
  color: rgba(255,255,255,0.78);
}
.card__title {
  font-size: 56px;
  line-height: 1.02;
  letter-spacing: -0.04em;
  font-weight: 800;
  margin: 4px 0 0;
}
.card__body {
  font-size: 13.5px;
  line-height: 1.65;
  color: rgba(255,255,255,0.82);
  max-width: 36ch;
}

/* ----- Form panel (split) ----- */
.card__form { padding: 44px 44px 36px; display: flex; flex-direction: column; justify-content: center; gap: 22px; }
.card__form-head { display: flex; flex-direction: column; gap: 4px; }
.card__form-title { font-size: 22px; font-weight: 800; letter-spacing: -0.02em; }
.card__form-sub { font-size: 13px; color: var(--ink-500); }

/* ----- Form fields ----- */
.lf { display: flex; flex-direction: column; gap: 14px; }
.lf__field { display: flex; flex-direction: column; gap: 7px; }
.lf__label { font-size: 12.5px; font-weight: 600; color: var(--ink-700); letter-spacing: -0.01em; }
.lf__input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  height: 46px;
  border-radius: var(--radius-input);
  background: var(--ink-50);
  border: 1.5px solid var(--ink-100);
  transition: border-color .14s, box-shadow .14s, background .14s;
}
.lf__input-wrap:hover { background: #fff; }
.lf__icon { display: inline-flex; flex: 0 0 auto; color: var(--ink-400); transition: color .14s; }
.lf__input {
  flex: 1; min-width: 0;
  height: 100%;
  border: 0; outline: 0; background: transparent;
  font-size: 14px; color: var(--ink-900);
  letter-spacing: -0.01em;
}
.lf__input::placeholder { color: var(--ink-400); }
.lf__eye { color: var(--ink-400); display: inline-flex; padding: 4px; border-radius: 6px; }
.lf__eye:hover { color: var(--ink-700); background: var(--ink-100); }

.lf__row { display: flex; align-items: center; gap: 10px; margin-top: 2px; }
.lf__row-spacer { flex: 1; }
.lf__check { display: inline-flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--ink-600); cursor: pointer; user-select: none; }
.lf__check input { display: none; }
.lf__check-box {
  width: 16px; height: 16px;
  border: 1.5px solid var(--ink-300);
  border-radius: 4px;
  display: inline-grid; place-items: center;
  color: #fff;
  transition: all .14s;
}
.lf__forgot { font-size: 12.5px; color: var(--ink-600); }
.lf__forgot:hover { color: var(--cs-blue-600); text-decoration: underline; }

.lf__error {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px;
  background: var(--danger-bg);
  border: 1px solid #fecaca;
  border-radius: 10px;
  color: var(--danger);
  font-size: 12.5px;
  font-weight: 500;
  animation: shake .42s cubic-bezier(.36,.07,.19,.97) both;
}
@keyframes shake {
  10%, 90% { transform: translateX(-1px); }
  20%, 80% { transform: translateX(2px); }
  30%, 50%, 70% { transform: translateX(-3px); }
  40%, 60% { transform: translateX(3px); }
}

.lf__submit {
  margin-top: 6px;
  height: 50px;
  border-radius: var(--radius-input);
  color: #fff;
  font-weight: 700;
  font-size: 14.5px;
  letter-spacing: 0.02em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 8px 16px -8px rgba(11,31,74,0.5);
  transition: transform .12s, box-shadow .14s, opacity .14s;
}
.lf__submit:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 14px 22px -8px rgba(11,31,74,0.55); }
.lf__submit:active:not(:disabled) { transform: translateY(0); }
.lf__submit:disabled { opacity: 0.85; cursor: progress; }
.lf__spin { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.lf__hint {
  display: flex; flex-wrap: wrap; gap: 6px;
  font-size: 11.5px;
  color: var(--ink-500);
  margin-top: 4px;
  align-items: center;
  justify-content: center;
}
.lf__hint-sep { color: var(--ink-300); }

/* ----- Hero decor ----- */
.hero-decor { position: absolute; inset: 0; z-index: 1; overflow: hidden; }
.hero-decor__grid { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-decor__orb {
  position: absolute;
  width: 360px; height: 360px;
  border-radius: 50%;
  filter: blur(20px);
  top: -120px; right: -80px;
}
.hero-decor__orb--2 {
  top: auto; right: auto;
  bottom: -160px; left: -120px;
  width: 420px; height: 420px;
}

/* ----- Stacked variant ----- */
.stacked-shell {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto;
  padding: 28px 32px;
  color: #fff;
  isolation: isolate;
}
.stacked-shell__top { position: relative; z-index: 2; }
.stacked-shell__center {
  position: relative; z-index: 2;
  display: grid;
  grid-template-columns: 1fr 440px;
  gap: 64px;
  align-items: center;
  max-width: 1100px;
  width: 100%;
  margin: 0 auto;
}
.stacked-shell__foot {
  position: relative; z-index: 2;
  font-size: 11.5px;
  color: rgba(255,255,255,0.6);
  letter-spacing: 0.04em;
  text-align: center;
  padding-top: 20px;
}
.stacked-headline { color: #fff; display: flex; flex-direction: column; gap: 14px; }
.stacked-headline__title { font-size: 64px; font-weight: 800; letter-spacing: -0.04em; line-height: 1.02; margin: 4px 0 0; white-space: pre-line; }
.stacked-headline__body { font-size: 15px; color: rgba(255,255,255,0.82); max-width: 38ch; line-height: 1.6; }

@media (max-width: 900px) {
  .stacked-shell__center { grid-template-columns: 1fr; gap: 32px; }
  .stacked-headline__title { font-size: 44px; }
}

/* ----- Minimal variant ----- */
.minimal-shell {
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto;
  background: #fff;
}
.minimal-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 32px;
  color: #fff;
}
.minimal-bar__right { display: flex; align-items: center; gap: 8px; font-size: 11px; }
.minimal-body { padding: 64px 32px; display: grid; place-items: center; }
.minimal-grid { display: grid; grid-template-columns: 1fr 440px; gap: 80px; align-items: center; max-width: 1100px; width: 100%; }
.minimal-copy { display: flex; flex-direction: column; gap: 16px; }
.minimal-copy__title { font-size: 56px; font-weight: 800; letter-spacing: -0.04em; line-height: 1.02; margin: 4px 0 0; white-space: pre-line; }
.minimal-copy__body { font-size: 15px; line-height: 1.6; color: var(--ink-600); max-width: 38ch; }
.minimal-list { list-style: none; padding: 0; margin: 12px 0 0; display: flex; flex-direction: column; gap: 10px; }
.minimal-list li { display: flex; align-items: center; gap: 10px; font-size: 13.5px; color: var(--ink-700); }
.minimal-list li span { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 auto; }
.minimal-foot { padding: 24px 32px; font-size: 11.5px; color: var(--ink-400); text-align: center; border-top: 1px solid var(--ink-100); }

@media (max-width: 900px) {
  .minimal-grid { grid-template-columns: 1fr; gap: 40px; }
  .minimal-copy__title { font-size: 40px; }
  .card--split { grid-template-columns: 1fr; }
  .card__hero { padding: 28px; }
  .card__title { font-size: 40px; }
  .card__form { padding: 28px; }
}

/* ----- Subtle background opt-out ----- */
body.no-bg {
  background: #f4f6fa;
}
`;

/* ============================================================== */
/*  App shell                                                      */
/* ============================================================== */
function LoginApp() {
  const [tweaks, setTweak] = window.useTweaks(TWEAK_DEFAULTS);

  React.useEffect(() => {
    document.body.classList.toggle('no-bg', !tweaks.showSubtleBg);
  }, [tweaks.showSubtleBg]);

  let body;
  if (tweaks.variant === 'stacked') {
    body = <VariantStacked tweaks={tweaks} />;
  } else if (tweaks.variant === 'minimal') {
    body = <VariantMinimal tweaks={tweaks} />;
  } else {
    body = <VariantSplit tweaks={tweaks} />;
  }

  const fullBleed = tweaks.variant === 'stacked' || tweaks.variant === 'minimal';

  return (
    <>
      <style>{css}</style>
      <div className={`app ${fullBleed ? 'app--full' : ''}`} data-screen-label="Login">
        {body}
      </div>

      <window.TweaksPanel title="Tweaks · 로그인">
        <window.TweakSection title="레이아웃">
          <window.TweakRadio
            label="변형"
            value={tweaks.variant}
            options={[
              { value: 'split', label: 'Split' },
              { value: 'stacked', label: 'Stacked' },
              { value: 'minimal', label: 'Minimal' },
            ]}
            onChange={(v) => setTweak('variant', v)}
          />
          <window.TweakRadio
            label="브랜드 컬러"
            value={tweaks.accent}
            options={[
              { value: 'blue', label: 'Blue' },
              { value: 'teal', label: 'Teal' },
              { value: 'indigo', label: 'Indigo' },
            ]}
            onChange={(v) => setTweak('accent', v)}
          />
          <window.TweakRadio
            label="Hero 카피"
            value={tweaks.heroCopy}
            options={[
              { value: 'default', label: '기본' },
              { value: 'short', label: '짧게' },
              { value: 'cta', label: 'CTA' },
            ]}
            onChange={(v) => setTweak('heroCopy', v)}
          />
        </window.TweakSection>
        <window.TweakSection title="옵션">
          <window.TweakToggle label="기억하기 체크박스" value={tweaks.showRemember} onChange={(v) => setTweak('showRemember', v)} />
          <window.TweakToggle label="비밀번호 찾기 링크" value={tweaks.showForgot} onChange={(v) => setTweak('showForgot', v)} />
          <window.TweakToggle label="배경 그라디언트(split)" value={tweaks.showSubtleBg} onChange={(v) => setTweak('showSubtleBg', v)} />
        </window.TweakSection>
      </window.TweaksPanel>
    </>
  );
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<LoginApp />);
