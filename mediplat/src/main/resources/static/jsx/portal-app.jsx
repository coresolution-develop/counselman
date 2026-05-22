/* MediPlat · Portal (App launcher)
   Three visual variations:
   - "tiles"  : matches reference (rounded blue tiles in a grid)
   - "cards"  : richer cards with icon + description + status
   - "hero"   : featured primary app + grid below
*/

const PORTAL_TWEAKS = /*EDITMODE-BEGIN*/{
  "variant": "cards",
  "accent": "blue",
  "showGreeting": true,
  "showSearch": false,
  "showStatus": true,
  "tileShape": "rounded",
  "showFooterMeta": true
}/*EDITMODE-END*/;

const PORTAL_ACCENTS = {
  blue: { from: '#0b1f4a', mid: '#173a82', to: '#1d4ed8', tint: '#e0e8ff' },
  teal: { from: '#062f2a', mid: '#0d544a', to: '#0d9488', tint: '#d1f5ee' },
  indigo: { from: '#1e1b4b', mid: '#312e81', to: '#4f46e5', tint: '#e0e7ff' },
};

/* ------------ Apps registry (백엔드 주입, fallback: 빈 배열) ------------ */
const APPS = (window.__MP_PORTAL__ && window.__MP_PORTAL__.apps) || [];

/* ------------ App icon glyphs ------------ */
function AppIcon({ k, size = 22, color = '#fff' }) {
  const p = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: color, strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round' };
  switch (k) {
    case 'counsel':
      return <svg {...p}><path d="M21 12a8 8 0 0 1-11.5 7.2L4 21l1.8-5.5A8 8 0 1 1 21 12z"/><path d="M9 11h6M9 14h4"/></svg>;
    case 'bed':
      return <svg {...p}><path d="M3 18V8M21 18v-6a3 3 0 0 0-3-3H10v6"/><path d="M3 14h18M3 18h18"/><circle cx="7" cy="11" r="2"/></svg>;
    case 'calendar':
      return <svg {...p}><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/><circle cx="8" cy="14" r="1" fill={color}/><circle cx="12" cy="14" r="1" fill={color}/><circle cx="16" cy="14" r="1" fill={color}/></svg>;
    case 'sparkle':
      return <svg {...p}><path d="M12 3l1.8 4.6L18 9l-4.2 1.4L12 15l-1.8-4.6L6 9l4.2-1.4z"/><path d="M19 16l.7 1.8L21 18.5l-1.3.7L19 21l-.7-1.8L17 18.5l1.3-.7z"/></svg>;
    case 'route':
      return <svg {...p}><circle cx="6" cy="19" r="2.5"/><circle cx="18" cy="5" r="2.5"/><path d="M8 19h7a4 4 0 0 0 0-8H9a4 4 0 0 1 0-8h6"/></svg>;
    case 'ambulance':
      return <svg {...p}><rect x="2" y="8" width="13" height="9" rx="1.5"/><path d="M15 11h4l2 3v3h-6"/><circle cx="7" cy="18.5" r="1.8"/><circle cx="17" cy="18.5" r="1.8"/><path d="M7 11.5h3M8.5 10v3"/></svg>;
    default:
      return <svg {...p}><rect x="4" y="4" width="16" height="16" rx="3"/></svg>;
  }
}

/* ------------ Misc icons ------------ */
const PIcon = {
  Search: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>,
  Bell: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10 21a2 2 0 0 0 4 0"/></svg>,
  Settings: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.6 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9 1.65 1.65 0 0 0 4.27 7.18l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9c.24.58.85.97 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>,
  Logout: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>,
  Arrow: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M5 12h14M13 5l7 7-7 7"/></svg>,
  Lock: (p) => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>,
};

/* ------------ Brand mark ------------ */
function PBrand({ size = 30 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none">
      <circle cx="24" cy="24" r="18" stroke="#1d4ed8" strokeWidth="3" />
      <circle cx="18" cy="24" r="5" fill="#0b1f4a" />
      <circle cx="32" cy="24" r="5" fill="#10b981" />
    </svg>
  );
}

/* ============================================================== */
/*  Header                                                         */
/* ============================================================== */
function Header({ user, tweaks, onLogout }) {
  return (
    <header className="ph">
      <div className="ph__brand">
        <PBrand size={30} />
        <div>
          <div className="ph__brand-eyebrow">MEDIPLAT</div>
          <div className="ph__brand-name">MEDIPLAT</div>
        </div>
      </div>

      {tweaks.showSearch && (
        <div className="ph__search">
          <PIcon.Search width={15} height={15} />
          <input placeholder="앱·기능 빠른 검색  (예: 상담, 병실, 예약)" />
          <kbd>⌘ K</kbd>
        </div>
      )}

      <div className="ph__right">
        <div className="ph__user">
          <div className="ph__user-name">{user.name}</div>
          <div className="ph__user-meta">{user.org} · {user.role}</div>
        </div>
        <button className="ph__pill" onClick={() => window.location.href = '/admin'}><PIcon.Settings width={14} height={14} />관리</button>
        <button className="ph__pill" onClick={() => window.location.href = '/admin/login-audit'}><PIcon.Search width={14} height={14} />로그인 기록</button>
        <button className="ph__pill ph__pill--ghost" onClick={onLogout}><PIcon.Logout width={14} height={14} />로그아웃</button>
      </div>
    </header>
  );
}

/* ============================================================== */
/*  Variant: TILES  (matches reference)                            */
/* ============================================================== */
function VariantTiles({ tweaks, onLaunch }) {
  const a = PORTAL_ACCENTS[tweaks.accent] || PORTAL_ACCENTS.blue;
  const radius = tweaks.tileShape === 'rounded' ? 14 : tweaks.tileShape === 'pill' ? 999 : 4;
  return (
    <main className="pv-tiles">
      {tweaks.showGreeting && (
        <div className="pv-tiles__greet">
          <div className="pv-tiles__eyebrow">WELCOME · {new Date().toLocaleDateString('ko-KR', { weekday: 'long' })}</div>
          <h1 className="pv-tiles__title">어디부터 시작할까요?</h1>
        </div>
      )}
      <div className="pv-tiles__grid">
        {APPS.map((app) => (
          <button
            key={app.id}
            className={`pv-tile ${app.active ? '' : 'pv-tile--off'}`}
            disabled={!app.active}
            onClick={() => app.active && onLaunch(app)}
            style={{
              borderRadius: radius,
              background: app.active
                ? `linear-gradient(135deg, ${a.mid}, ${a.to})`
                : 'linear-gradient(135deg, #94a3b8, #6c7d97)',
            }}
          >
            <span className="pv-tile__name">{app.name}</span>
            {!app.active && <span className="pv-tile__lock"><PIcon.Lock width={12} height={12} /> 준비중</span>}
          </button>
        ))}
      </div>
      {tweaks.showFooterMeta && (
        <div className="pv-tiles__foot">
          <span className="pv-tiles__dot" /> {APPS.filter(a => a.active).length}개 앱 사용 가능 · 마지막 접속 오늘 09:24
        </div>
      )}
    </main>
  );
}

/* ============================================================== */
/*  Variant: CARDS  (richer)                                       */
/* ============================================================== */
function VariantCards({ tweaks, onLaunch }) {
  const a = PORTAL_ACCENTS[tweaks.accent] || PORTAL_ACCENTS.blue;
  return (
    <main className="pv-cards">
      {tweaks.showGreeting && (
        <div className="pv-cards__greet">
          <div className="pv-cards__eyebrow" style={{ color: a.to }}>WELCOME BACK</div>
          <h1 className="pv-cards__title">앱을 선택해 시작하세요</h1>
          <p className="pv-cards__sub">하나의 계정으로 연결된 시스템에 접속할 수 있습니다.</p>
        </div>
      )}
      <div className="pv-cards__sec">
        <div className="pv-cards__sec-head">
          <span className="pv-cards__sec-num" style={{ background: `linear-gradient(135deg, ${a.mid}, ${a.to})` }}>01</span>
          <div>
            <div className="pv-cards__sec-title">활성 앱</div>
            <div className="pv-cards__sec-sub">현재 기관에서 사용 가능한 시스템</div>
          </div>
        </div>
        <div className="pv-cards__grid">
          {APPS.filter(app => app.active).map(app => (
            <button key={app.id} className="pv-card" onClick={() => onLaunch(app)}>
              <div className="pv-card__icon" style={{ background: `linear-gradient(135deg, ${a.mid}, ${a.to})` }}>
                <AppIcon k={app.iconKey} size={22} />
              </div>
              <div className="pv-card__body">
                <div className="pv-card__name">{app.name}</div>
                <div className="pv-card__kr">{app.kr}</div>
                <div className="pv-card__desc">{app.desc}</div>
              </div>
              {tweaks.showStatus && (
                <div className="pv-card__foot">
                  <span className="pv-card__pill"><span className="pv-card__pill-dot" /> 운영중</span>
                  <span className="pv-card__arrow"><PIcon.Arrow width={14} height={14} /></span>
                </div>
              )}
            </button>
          ))}
        </div>
      </div>

      <div className="pv-cards__sec">
        <div className="pv-cards__sec-head">
          <span className="pv-cards__sec-num pv-cards__sec-num--gray">02</span>
          <div>
            <div className="pv-cards__sec-title">준비중</div>
            <div className="pv-cards__sec-sub">출시를 준비하고 있는 시스템</div>
          </div>
        </div>
        <div className="pv-cards__grid">
          {APPS.filter(app => !app.active).map(app => (
            <div key={app.id} className="pv-card pv-card--off">
              <div className="pv-card__icon pv-card__icon--off">
                <AppIcon k={app.iconKey} size={22} color="#fff" />
              </div>
              <div className="pv-card__body">
                <div className="pv-card__name">{app.name}</div>
                <div className="pv-card__kr">{app.kr}</div>
                <div className="pv-card__desc">{app.desc}</div>
              </div>
              {tweaks.showStatus && (
                <div className="pv-card__foot">
                  <span className="pv-card__pill pv-card__pill--off"><PIcon.Lock width={10} height={10} /> 준비중</span>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}

/* ============================================================== */
/*  Variant: HERO                                                  */
/* ============================================================== */
function VariantHero({ tweaks, onLaunch }) {
  const a = PORTAL_ACCENTS[tweaks.accent] || PORTAL_ACCENTS.blue;
  const featured = APPS[0];
  const rest = APPS.slice(1);
  return (
    <main className="pv-hero">
      <button
        className="pv-hero__feature"
        onClick={() => onLaunch(featured)}
        style={{
          background: `
            radial-gradient(700px 300px at 90% 0%, ${a.to}99 0%, transparent 60%),
            linear-gradient(135deg, ${a.from}, ${a.mid} 60%, ${a.to})`
        }}
      >
        <div className="pv-hero__feature-left">
          <div className="pv-hero__feature-eyebrow">자주 사용하는 앱 · 추천</div>
          <div className="pv-hero__feature-name">{featured.name}</div>
          <div className="pv-hero__feature-desc">{featured.desc}</div>
          <div className="pv-hero__feature-cta">바로 시작 <PIcon.Arrow width={14} height={14} /></div>
        </div>
        <div className="pv-hero__feature-right">
          <div className="pv-hero__feature-icon"><AppIcon k={featured.iconKey} size={56} /></div>
          <div className="pv-hero__feature-stats">
            <div><span>오늘 상담</span><strong>18</strong></div>
            <div><span>대기</span><strong>3</strong></div>
            <div><span>입원 연계</span><strong>4</strong></div>
          </div>
        </div>
      </button>

      <div className="pv-hero__sec-head">
        <div className="pv-hero__sec-title">전체 앱</div>
        <div className="pv-hero__sec-sub">{APPS.filter(a => a.active).length}개 활성 · {APPS.filter(a => !a.active).length}개 준비중</div>
      </div>
      <div className="pv-hero__grid">
        {rest.map(app => (
          <button
            key={app.id}
            className={`pv-hero__card ${app.active ? '' : 'pv-hero__card--off'}`}
            disabled={!app.active}
            onClick={() => app.active && onLaunch(app)}
          >
            <div className="pv-hero__card-icon" style={{
              background: app.active
                ? `linear-gradient(135deg, ${a.mid}, ${a.to})`
                : 'linear-gradient(135deg, #94a3b8, #6c7d97)',
            }}>
              <AppIcon k={app.iconKey} size={20} />
            </div>
            <div className="pv-hero__card-name">{app.name}</div>
            <div className="pv-hero__card-desc">{app.desc}</div>
            {!app.active && <div className="pv-hero__card-tag"><PIcon.Lock width={10} height={10} /> 준비중</div>}
          </button>
        ))}
      </div>
    </main>
  );
}

/* ============================================================== */
/*  News article sidebar                                           */
/* ============================================================== */
function NewsletterSidebar() {
  const [state, setState] = React.useState({
    items: [],
    recommendations: [],
    loading: true,
    savingCode: '',
    feedbackCode: '',
    customTitle: '',
    customUrl: '',
    customSaving: false,
    error: '',
  });

  React.useEffect(() => {
    let alive = true;
    fetch('/api/newsletters', { credentials: 'same-origin' })
      .then((res) => {
        if (!res.ok) throw new Error('뉴스/기사 정보를 불러오지 못했습니다.');
        return res.json();
      })
      .then((data) => {
        if (!alive) return;
        setState({
          items: data.items || [],
          recommendations: data.recommendations || [],
          loading: false,
          savingCode: '',
          feedbackCode: '',
          customTitle: '',
          customUrl: '',
          customSaving: false,
          error: '',
        });
      })
      .catch((err) => {
        if (!alive) return;
        setState((prev) => ({ ...prev, loading: false, error: err.message || '뉴스/기사 정보를 불러오지 못했습니다.' }));
      });
    return () => { alive = false; };
  }, []);

  function setSubscribed(item, subscribed) {
    setState((prev) => ({ ...prev, savingCode: item.code, error: '' }));
    fetch('/api/newsletters/subscriptions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ newsletterCode: item.code, subscribed }),
    })
      .then((res) => {
        if (!res.ok) throw new Error('관심 저장 상태를 저장하지 못했습니다.');
        return res.json();
      })
      .then((data) => {
        setState((prev) => ({
          items: data.items || [],
          recommendations: data.recommendations || [],
          loading: false,
          savingCode: '',
          feedbackCode: '',
          customTitle: prev.customTitle,
          customUrl: prev.customUrl,
          customSaving: false,
          error: '',
        }));
      })
      .catch((err) => {
        setState((prev) => ({ ...prev, savingCode: '', error: err.message || '관심 저장 상태를 저장하지 못했습니다.' }));
      });
  }

  function addCustom(event) {
    event.preventDefault();
    const title = state.customTitle.trim();
    const url = state.customUrl.trim();
    if (!title || !url) {
      setState((prev) => ({ ...prev, error: '기사 제목과 URL을 모두 입력해주세요.' }));
      return;
    }
    setState((prev) => ({ ...prev, customSaving: true, error: '' }));
    fetch('/api/newsletters/custom', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ title, url }),
    })
      .then((res) => {
        if (!res.ok) throw new Error('직접 추가한 기사를 저장하지 못했습니다.');
        return res.json();
      })
      .then((data) => {
        setState({
          items: data.items || [],
          recommendations: data.recommendations || [],
          loading: false,
          savingCode: '',
          feedbackCode: '',
          customTitle: '',
          customUrl: '',
          customSaving: false,
          error: '',
        });
      })
      .catch((err) => {
        setState((prev) => ({ ...prev, customSaving: false, error: err.message || '직접 추가한 기사를 저장하지 못했습니다.' }));
      });
  }

  function setFeedback(item, feedback) {
    setState((prev) => ({ ...prev, feedbackCode: item.code, error: '' }));
    fetch('/api/newsletters/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ newsletterCode: item.code, feedback }),
    })
      .then((res) => {
        if (!res.ok) throw new Error('추천 피드백을 저장하지 못했습니다.');
        return res.json();
      })
      .then((data) => {
        setState((prev) => ({
          items: data.items || [],
          recommendations: data.recommendations || [],
          loading: false,
          savingCode: '',
          feedbackCode: '',
          customTitle: prev.customTitle,
          customUrl: prev.customUrl,
          customSaving: false,
          error: '',
        }));
      })
      .catch((err) => {
        setState((prev) => ({ ...prev, feedbackCode: '', error: err.message || '추천 피드백을 저장하지 못했습니다.' }));
      });
  }

  const subscribedItems = state.items.filter((item) => item.subscribed);
  const candidateItems = state.items.filter((item) => !item.subscribed).slice(0, 2);
  const recommendedItems = (state.recommendations.length > 0 ? state.recommendations : candidateItems).slice(0, 2);

  return (
    <aside className="pn" aria-label="뉴스/기사 피드">
      <div className="pn__head">
        <div>
          <div className="pn__eyebrow">NEWS FEED</div>
          <h2 className="pn__title">뉴스/기사 피드</h2>
        </div>
        <span className="pn__count">{subscribedItems.length}개 저장</span>
      </div>

      {state.loading && <div className="pn__empty">뉴스/기사를 불러오는 중입니다.</div>}
      {!state.loading && state.error && <div className="pn__alert">{state.error}</div>}

      {!state.loading && (
        <>
          <section className="pn__section">
            <div className="pn__section-title">내 관심 기사</div>
            {subscribedItems.length === 0 ? (
              <div className="pn__empty">아직 저장한 기사가 없습니다.</div>
            ) : (
              <div className="pn__list">
                {subscribedItems.map((item) => (
                  <NewsletterItem
                    key={item.code}
                    item={item}
                  saving={state.savingCode === item.code}
                  feedbackSaving={state.feedbackCode === item.code}
                  onToggle={() => setSubscribed(item, false)}
                />
                ))}
              </div>
            )}
          </section>

          <section className="pn__section">
            <div className="pn__section-title">추천 기사</div>
            <div className="pn__list">
              {recommendedItems.map((item) => (
                <NewsletterItem
                  key={item.code}
                  item={item}
                  saving={state.savingCode === item.code}
                  feedbackSaving={state.feedbackCode === item.code}
                  onToggle={() => setSubscribed(item, true)}
                  onFeedback={(feedback) => setFeedback(item, feedback)}
                  recommendation
                />
              ))}
            </div>
          </section>

          <section className="pn__section">
            <div className="pn__section-title">직접 기사 추가</div>
            <form className="pn-custom" onSubmit={addCustom}>
              <input
                type="text"
                value={state.customTitle}
                placeholder="기사 제목"
                onChange={(event) => setState((prev) => ({ ...prev, customTitle: event.target.value }))}
              />
              <input
                type="url"
                value={state.customUrl}
                placeholder="https://..."
                onChange={(event) => setState((prev) => ({ ...prev, customUrl: event.target.value }))}
              />
              <button type="submit" disabled={state.customSaving}>
                {state.customSaving ? '저장중' : '추가'}
              </button>
            </form>
          </section>
        </>
      )}
    </aside>
  );
}

function NewsletterItem({ item, saving, feedbackSaving, onToggle, onFeedback, recommendation }) {
  function openNewsletter() {
    if (item.url) {
      window.open(item.url, '_blank', 'noopener,noreferrer');
    }
  }

  function onKeyDown(event) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openNewsletter();
    }
  }

  return (
    <article
      className={`pn-item ${item.subscribed ? 'pn-item--on' : ''}`}
      role="link"
      tabIndex="0"
      onClick={openNewsletter}
      onKeyDown={onKeyDown}
    >
      <div className="pn-item__top">
        <span className="pn-item__category">{item.category}</span>
        <span className="pn-item__cadence">{item.cadence}</span>
      </div>
      <h3 className="pn-item__title">{item.title}</h3>
      <p className="pn-item__summary">{item.summary}</p>
      {recommendation && item.reason && (
        <div className="pn-item__reason">{item.aiRecommended ? 'AI 추천 · ' : ''}{item.reason}</div>
      )}
      {recommendation && onFeedback && (
        <div className="pn-item__feedback" aria-label="추천 피드백">
          <button
            type="button"
            className={item.feedback === 'LIKE' ? 'is-active' : ''}
            disabled={feedbackSaving}
            onClick={(event) => {
              event.stopPropagation();
              onFeedback('LIKE');
            }}
            onKeyDown={(event) => event.stopPropagation()}
          >
            좋아요
          </button>
          <button
            type="button"
            className={item.feedback === 'DISLIKE' ? 'is-active' : ''}
            disabled={feedbackSaving}
            onClick={(event) => {
              event.stopPropagation();
              onFeedback('DISLIKE');
            }}
            onKeyDown={(event) => event.stopPropagation()}
          >
            관심 없음
          </button>
        </div>
      )}
      <div className="pn-item__foot">
        <div className="pn-item__tags">
          {(item.tags || []).slice(0, 3).map((tag) => <span key={tag}>#{tag}</span>)}
        </div>
        <button
          type="button"
          className={`pn-item__btn ${item.subscribed ? 'pn-item__btn--on' : ''}`}
          disabled={saving}
          onClick={(event) => {
            event.stopPropagation();
            onToggle();
          }}
          onKeyDown={(event) => event.stopPropagation()}
        >
          {saving ? '저장중' : item.subscribed ? '저장됨' : '저장'}
        </button>
      </div>
    </article>
  );
}

/* ============================================================== */
/*  Stylesheet                                                     */
/* ============================================================== */
const portalCss = `
html,
body,
#root {
  height: 100%;
  overflow: hidden;
}
.pp {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow-x: clip;
  overflow-y: hidden;
  padding-top: 70px;
}
.pp__body {
  flex: 1;
  min-height: 0;
  width: 100%;
  margin: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 320px);
  gap: 24px;
  align-items: start;
  padding: 22px 24px 56px max(36px, calc((100vw - 1400px) / 2 + 36px));
  overflow: hidden;
}
.pp__body > main {
  width: 100%;
  min-width: 0;
  max-height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-left: 0;
  padding-right: 0;
}
.pp__body > main,
.pn {
  scrollbar-width: thin;
  scrollbar-color: rgba(100,116,139,0.38) transparent;
}
.pp__body > main::-webkit-scrollbar,
.pn::-webkit-scrollbar {
  width: 8px;
}
.pp__body > main::-webkit-scrollbar-track,
.pn::-webkit-scrollbar-track {
  background: transparent;
}
.pp__body > main::-webkit-scrollbar-thumb,
.pn::-webkit-scrollbar-thumb {
  background: rgba(100,116,139,0.28);
  border: 2px solid transparent;
  border-radius: 999px;
  background-clip: content-box;
}
.pp__body > main::-webkit-scrollbar-thumb:hover,
.pn::-webkit-scrollbar-thumb:hover {
  background: rgba(100,116,139,0.48);
  border: 2px solid transparent;
  background-clip: content-box;
}

/* Header */
.ph {
  display: flex; align-items: center; gap: 16px;
  width: 100%;
  min-height: 70px;
  padding: 18px 32px;
  background: rgba(255,255,255,0.78);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--ink-100);
  position: fixed; top: 0; left: 0; right: 0; z-index: 50;
}
.ph__brand { display: flex; align-items: center; gap: 12px; }
.ph__brand-eyebrow { font-size: 9.5px; letter-spacing: 0.2em; color: var(--ink-500); font-weight: 600; line-height: 1; }
.ph__brand-name { font-size: 18px; font-weight: 800; letter-spacing: 0.04em; color: var(--ink-900); margin-top: 4px; line-height: 1; }
.ph__search {
  flex: 1; max-width: 480px;
  display: flex; align-items: center; gap: 8px;
  height: 38px;
  padding: 0 14px;
  background: var(--ink-50);
  border: 1px solid var(--ink-100);
  border-radius: 10px;
  color: var(--ink-500);
}
.ph__search input { border: 0; outline: 0; background: transparent; flex: 1; font-size: 13px; color: var(--ink-900); }
.ph__search input::placeholder { color: var(--ink-400); }
.ph__search kbd { font-family: var(--font); font-size: 10.5px; padding: 1px 5px; background: #fff; border: 1px solid var(--ink-200); border-radius: 4px; color: var(--ink-500); }

.ph__right { display: flex; align-items: center; gap: 10px; margin-left: auto; }
.ph__icon-btn { position: relative; width: 36px; height: 36px; border-radius: 10px; display: grid; place-items: center; color: var(--ink-600); border: 1px solid var(--ink-100); background: #fff; }
.ph__icon-btn:hover { color: var(--ink-900); border-color: var(--ink-200); }
.ph__badge { position: absolute; top: 4px; right: 4px; min-width: 14px; height: 14px; padding: 0 3px; font-size: 9.5px; font-weight: 700; background: #ef4444; color: #fff; border-radius: 999px; display: grid; place-items: center; }
.ph__user { text-align: right; padding: 0 6px; }
.ph__user-name { font-weight: 700; font-size: 13px; }
.ph__user-meta { font-size: 10.5px; letter-spacing: 0.04em; color: var(--ink-500); margin-top: 2px; }
.ph__pill {
  display: inline-flex; align-items: center; gap: 6px;
  height: 36px; padding: 0 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--ink-200);
  color: var(--ink-700);
  font-size: 12.5px;
  font-weight: 600;
}
.ph__pill:hover { border-color: var(--cs-blue-500); color: var(--cs-blue-600); }
.ph__pill--ghost { background: transparent; }

/* ============== TILES ============== */
.pv-tiles { padding: 64px 32px; max-width: 1080px; margin: 0 auto; width: 100%; }
.pv-tiles__greet { text-align: center; margin-bottom: 56px; }
.pv-tiles__eyebrow { font-size: 11px; letter-spacing: 0.22em; color: var(--ink-500); font-weight: 600; }
.pv-tiles__title { font-size: 32px; font-weight: 800; letter-spacing: -0.03em; margin: 8px 0 0; }
.pv-tiles__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.pv-tile {
  position: relative;
  height: 76px;
  color: #fff;
  font-weight: 700; font-size: 15px;
  letter-spacing: -0.01em;
  display: grid; place-items: center;
  box-shadow: 0 6px 16px -8px rgba(11,31,74,0.45);
  transition: transform .12s, box-shadow .14s, filter .14s;
  text-align: center;
  padding: 0 12px;
}
.pv-tile:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 14px 26px -10px rgba(11,31,74,0.5); }
.pv-tile--off { cursor: not-allowed; opacity: 0.85; }
.pv-tile--off:hover { transform: none; }
.pv-tile__name { line-height: 1; }
.pv-tile__lock {
  position: absolute; top: 8px; right: 10px;
  font-size: 9.5px; letter-spacing: 0.04em;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 6px;
  background: rgba(0,0,0,0.18);
  border-radius: 999px;
}
.pv-tiles__foot { margin-top: 40px; text-align: center; font-size: 12px; color: var(--ink-500); display: inline-flex; align-items: center; gap: 8px; justify-content: center; width: 100%; }
.pv-tiles__dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; box-shadow: 0 0 8px #10b981; }

@media (max-width: 768px) {
  .pv-tiles__grid { grid-template-columns: repeat(2, 1fr); }
}

/* ============== CARDS ============== */
.pv-cards { padding: 26px 0 0; max-width: none; margin: 0; width: 100%; }
.pv-cards__greet { margin-bottom: 36px; }
.pv-cards__eyebrow { font-size: 11px; letter-spacing: 0.22em; font-weight: 700; }
.pv-cards__title { font-size: 30px; font-weight: 800; letter-spacing: -0.03em; margin: 8px 0 4px; }
.pv-cards__sub { font-size: 14px; color: var(--ink-500); }

.pv-cards__sec { margin-bottom: 36px; }
.pv-cards__sec-head {
  display: flex; align-items: center; gap: 14px;
  padding: 0 4px;
  margin-bottom: 16px;
}
.pv-cards__sec-num {
  width: 30px; height: 30px;
  border-radius: 8px;
  display: grid; place-items: center;
  color: #fff;
  font-size: 11.5px; font-weight: 700;
  letter-spacing: 0.04em;
  flex-shrink: 0;
}
.pv-cards__sec-num--gray { background: linear-gradient(135deg, #94a3b8, #64748b); }
.pv-cards__sec-title { font-size: 15px; font-weight: 700; letter-spacing: -0.01em; }
.pv-cards__sec-sub { font-size: 12px; color: var(--ink-500); margin-top: 2px; }
.pv-cards__sec-count { margin-left: auto; font-size: 12px; color: var(--ink-500); padding: 4px 10px; background: var(--ink-100); border-radius: 999px; font-weight: 600; }

.pv-cards__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}
.pv-card {
  text-align: left;
  background: #fff;
  border: 1px solid var(--ink-100);
  border-radius: 16px;
  padding: 20px;
  display: flex; flex-direction: column; gap: 14px;
  box-shadow: 0 1px 2px rgba(15,23,42,0.04);
  transition: transform .14s, box-shadow .16s, border-color .14s;
  cursor: pointer;
}
.pv-card:hover { transform: translateY(-3px); box-shadow: 0 18px 30px -14px rgba(11,31,74,0.18); border-color: var(--ink-200); }
.pv-card__icon {
  width: 44px; height: 44px;
  border-radius: 12px;
  display: grid; place-items: center;
  box-shadow: 0 6px 14px -6px rgba(11,31,74,0.45);
}
.pv-card__icon--off { background: linear-gradient(135deg, #94a3b8, #6c7d97); box-shadow: none; }
.pv-card__body { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.pv-card__name { font-weight: 700; font-size: 16px; letter-spacing: -0.02em; min-width: 0; overflow-wrap: anywhere; }
.pv-card__kr { font-size: 11px; letter-spacing: 0.05em; color: var(--ink-500); text-transform: uppercase; font-weight: 600; }
.pv-card__desc { font-size: 12.5px; color: var(--ink-600); line-height: 1.55; margin-top: 4px; min-width: 0; overflow-wrap: anywhere; }
.pv-card__foot { display: flex; align-items: center; justify-content: space-between; padding-top: 10px; border-top: 1px dashed var(--ink-100); }
.pv-card__pill { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; font-weight: 600; color: #047857; background: #ecfdf5; padding: 4px 10px; border-radius: 999px; }
.pv-card__pill-dot { width: 6px; height: 6px; border-radius: 50%; background: #10b981; }
.pv-card__pill--off { color: var(--ink-500); background: var(--ink-100); }
.pv-card__arrow { color: var(--ink-400); display: inline-flex; transition: transform .14s, color .14s; }
.pv-card:hover .pv-card__arrow { transform: translateX(2px); color: var(--cs-blue-600); }
.pv-card--off { cursor: default; opacity: 0.85; }
.pv-card--off:hover { transform: none; box-shadow: 0 1px 2px rgba(15,23,42,0.04); border-color: var(--ink-100); }

/* ============== HERO ============== */
.pv-hero { padding: 36px 32px 56px; max-width: 1240px; margin: 0 auto; width: 100%; }
.pv-hero__feature {
  position: relative;
  width: 100%;
  text-align: left;
  border-radius: 22px;
  overflow: hidden;
  color: #fff;
  padding: 36px 40px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 32px;
  align-items: center;
  cursor: pointer;
  transition: transform .14s, box-shadow .16s;
  box-shadow: 0 24px 48px -16px rgba(11,31,74,0.4);
  margin-bottom: 28px;
}
.pv-hero__feature:hover { transform: translateY(-2px); box-shadow: 0 30px 56px -18px rgba(11,31,74,0.45); }
.pv-hero__feature-left { display: flex; flex-direction: column; gap: 6px; }
.pv-hero__feature-eyebrow { font-size: 11px; letter-spacing: 0.22em; opacity: 0.78; }
.pv-hero__feature-name { font-size: 38px; font-weight: 800; letter-spacing: -0.03em; line-height: 1.05; margin: 6px 0 6px; }
.pv-hero__feature-desc { font-size: 14.5px; opacity: 0.88; line-height: 1.55; max-width: 44ch; }
.pv-hero__feature-cta {
  display: inline-flex; align-items: center; gap: 8px;
  margin-top: 18px;
  height: 40px; padding: 0 18px;
  border-radius: 999px;
  background: rgba(255,255,255,0.16);
  border: 1px solid rgba(255,255,255,0.3);
  width: fit-content;
  font-weight: 700; font-size: 13px;
}
.pv-hero__feature-right { display: flex; flex-direction: column; gap: 18px; align-items: flex-end; min-width: 220px; }
.pv-hero__feature-icon { width: 96px; height: 96px; border-radius: 24px; background: rgba(255,255,255,0.12); border: 1px solid rgba(255,255,255,0.2); display: grid; place-items: center; }
.pv-hero__feature-stats { display: flex; gap: 18px; }
.pv-hero__feature-stats > div { display: flex; flex-direction: column; align-items: flex-end; }
.pv-hero__feature-stats span { font-size: 10.5px; opacity: 0.7; letter-spacing: 0.06em; }
.pv-hero__feature-stats strong { font-size: 22px; font-weight: 800; font-variant-numeric: tabular-nums; }

.pv-hero__sec-head { display: flex; align-items: baseline; gap: 14px; padding: 0 4px; margin-bottom: 12px; }
.pv-hero__sec-title { font-size: 16px; font-weight: 700; letter-spacing: -0.01em; }
.pv-hero__sec-sub { font-size: 12px; color: var(--ink-500); }

.pv-hero__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.pv-hero__card {
  text-align: left;
  background: #fff;
  border: 1px solid var(--ink-100);
  border-radius: 16px;
  padding: 18px;
  display: flex; flex-direction: column; gap: 12px;
  box-shadow: 0 1px 2px rgba(15,23,42,0.04);
  transition: transform .14s, box-shadow .16s, border-color .14s;
  position: relative;
}
.pv-hero__card:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 14px 26px -12px rgba(11,31,74,0.18); border-color: var(--ink-200); }
.pv-hero__card--off { cursor: not-allowed; opacity: 0.85; }
.pv-hero__card-icon { width: 38px; height: 38px; border-radius: 10px; display: grid; place-items: center; }
.pv-hero__card-name { font-weight: 700; font-size: 14.5px; letter-spacing: -0.01em; }
.pv-hero__card-desc { font-size: 12px; color: var(--ink-600); line-height: 1.5; }
.pv-hero__card-tag { position: absolute; top: 12px; right: 12px; display: inline-flex; align-items: center; gap: 4px; font-size: 10px; padding: 2px 8px; background: var(--ink-100); color: var(--ink-500); border-radius: 999px; font-weight: 600; }

/* ============== NEWS ARTICLE FEED ============== */
.pn {
  position: sticky;
  top: 88px;
  max-height: calc(100vh - 104px);
  overflow-y: auto;
  overscroll-behavior: contain;
  background: rgba(255,255,255,0.88);
  border: 1px solid var(--ink-100);
  border-radius: 20px;
  box-shadow: 0 18px 36px -24px rgba(11,31,74,0.36);
  padding: 16px;
}
.pn__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.pn__eyebrow {
  font-size: 10px;
  letter-spacing: 0.2em;
  color: var(--cs-blue-600);
  font-weight: 800;
}
.pn__title {
  margin: 4px 0 0;
  font-size: 17px;
  line-height: 1.2;
  letter-spacing: -0.03em;
}
.pn__count {
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 999px;
  background: #eff6ff;
  color: var(--cs-blue-700);
  font-size: 11px;
  font-weight: 800;
}
.pn__section {
  margin-top: 16px;
}
.pn__section:first-of-type {
  margin-top: 0;
}
.pn__section-title {
  font-size: 12px;
  color: var(--ink-600);
  font-weight: 800;
  margin-bottom: 10px;
}
.pn__list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.pn__empty,
.pn__alert {
  padding: 16px;
  border-radius: 14px;
  background: var(--ink-50);
  border: 1px dashed var(--ink-200);
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.5;
}
.pn__alert {
  border-style: solid;
  background: #fef2f2;
  color: #b91c1c;
}
.pn-item {
  border: 1px solid var(--ink-100);
  border-radius: 15px;
  background: #fff;
  padding: 12px;
  cursor: pointer;
  transition: border-color .14s, box-shadow .16s, transform .14s;
}
.pn-item:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.24);
  outline-offset: 3px;
}
.pn-item:hover {
  border-color: var(--ink-200);
  box-shadow: 0 14px 28px -22px rgba(11,31,74,0.3);
  transform: translateY(-1px);
}
.pn-item--on {
  border-color: #bfdbfe;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}
.pn-item__top,
.pn-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.pn-item__category,
.pn-item__cadence {
  font-size: 10.5px;
  color: var(--ink-500);
  font-weight: 700;
}
.pn-item__category {
  color: var(--cs-blue-700);
}
.pn-item__title {
  margin: 8px 0 4px;
  font-size: 14px;
  line-height: 1.35;
  letter-spacing: -0.02em;
}
.pn-item__summary {
  margin: 0;
  color: var(--ink-600);
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pn-item__reason {
  margin-top: 9px;
  padding: 7px 9px;
  border-radius: 10px;
  background: #ecfdf5;
  color: #047857;
  font-size: 11px;
  font-weight: 700;
}
.pn-item__feedback {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}
.pn-item__feedback button {
  height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  background: var(--ink-50);
  border: 1px solid var(--ink-100);
  color: var(--ink-500);
  font-size: 11px;
  font-weight: 800;
}
.pn-item__feedback button:hover:not(:disabled),
.pn-item__feedback button.is-active {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: var(--cs-blue-700);
}
.pn-item__feedback button:disabled {
  opacity: 0.6;
  cursor: wait;
}
.pn-item__foot {
  margin-top: 12px;
  align-items: flex-end;
}
.pn-item__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  min-width: 0;
}
.pn-item__tags span {
  color: var(--ink-500);
  font-size: 10.5px;
  line-height: 1;
}
.pn-item__btn {
  flex-shrink: 0;
  min-width: 58px;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--cs-blue-600);
  color: #fff;
  font-size: 11.5px;
  font-weight: 800;
}
.pn-item__btn--on {
  background: #e0e7ff;
  color: var(--cs-blue-700);
}
.pn-item__btn:disabled {
  opacity: 0.65;
  cursor: wait;
}
.pn-custom {
  display: grid;
  gap: 8px;
}
.pn-custom input {
  width: 100%;
  height: 34px;
  border: 1px solid var(--ink-200);
  border-radius: 10px;
  background: #fff;
  padding: 0 10px;
  color: var(--ink-900);
  font: inherit;
  font-size: 12px;
  outline: none;
}
.pn-custom input:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.pn-custom button {
  height: 34px;
  border-radius: 10px;
  background: var(--ink-900);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}
.pn-custom button:disabled {
  opacity: 0.65;
  cursor: wait;
}

@media (max-width: 768px) {
  html, body, #root { height: auto; overflow: visible; }
  .ph {
    position: static;
  }
  .pp {
    height: auto;
    min-height: 100vh;
    overflow-x: hidden;
    overflow-y: visible;
    padding-top: 0;
  }
  .pp__body {
    grid-template-columns: 1fr;
    padding: 0 16px 32px;
    overflow: visible;
  }
  .pp__body > main {
    max-height: none;
    overflow-y: visible;
  }
  .pn {
    position: static;
    max-height: none;
    overflow: visible;
    width: 100%;
  }
  .pv-hero__feature { grid-template-columns: 1fr; padding: 28px; }
  .pv-hero__feature-right { align-items: flex-start; }
  .pv-hero__feature-stats { gap: 24px; }
  .pv-hero__feature-stats > div { align-items: flex-start; }
}

@media (max-width: 480px) {
  .ph {
    flex-wrap: wrap;
    align-items: flex-start;
    padding: 14px 16px;
    gap: 12px;
  }
  .ph__brand {
    flex: 1 1 calc(100% - 148px);
    min-width: 0;
  }
  .ph__right {
    flex: 1 1 100%;
    width: 100%;
    margin-left: 0;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
  }
  .ph__user {
    flex: 1 1 100%;
    order: -1;
    padding: 0;
    text-align: left;
    min-width: 0;
  }
  .ph__user-name,
  .ph__user-meta {
    min-width: 0;
    overflow-wrap: anywhere;
  }
  .ph__pill {
    flex: 1 1 calc(50% - 4px);
    justify-content: center;
    min-width: 0;
  }
  .ph__search {
    flex: 1 1 100%;
    width: 100%;
    max-width: none;
    order: 3;
  }

  .pv-cards {
    padding: 24px 16px;
  }
  .pv-cards__grid {
    grid-template-columns: 1fr;
  }
  .pv-tiles {
    padding: 24px 16px;
  }
  .pv-tiles__grid {
    grid-template-columns: 1fr;
  }
  .pv-tile {
    min-height: 56px;
    height: auto;
  }
  .pv-hero {
    padding: 24px 16px;
  }
  .pv-hero__feature {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: 24px;
  }
  .pv-hero__feature-right {
    min-width: 0;
  }
  .pv-hero__feature-icon {
    width: 56px;
    height: 56px;
    border-radius: 16px;
  }
  .pv-hero__feature-name {
    font-size: 22px;
  }
  .pv-hero__feature-stats {
    flex-wrap: wrap;
  }
  .pv-tile,
  .pv-card,
  .pv-hero__feature,
  .pv-hero__card,
  .ph__pill {
    min-height: 44px;
  }
  .pv-tile:active:not(:disabled),
  .pv-card:active,
  .pv-hero__feature:active,
  .pv-hero__card:active:not(:disabled),
  .ph__pill:active {
    transform: scale(0.98);
    filter: brightness(0.98);
  }

  .twk-panel {
    display: none !important;
  }
}
`;

/* ============================================================== */
/*  App                                                            */
/* ============================================================== */
function PortalApp() {
  const [tweaks, setTweak] = window.useTweaks(PORTAL_TWEAKS);
  const initUser = (window.__MP_PORTAL__ && window.__MP_PORTAL__.user) || {};
  const [user] = React.useState({
    name: initUser.name || '',
    org:  initUser.instName || (initUser.org || '').toUpperCase(),
    role: initUser.role || '',
  });

  function onLaunch(app) {
    if (app.serviceCode) {
      window.location.href = '/launch/' + app.serviceCode;
    }
  }
  function onLogout() {
    window.location.href = '/logout';
  }

  let body;
  if (tweaks.variant === 'tiles') body = <VariantTiles tweaks={tweaks} onLaunch={onLaunch} />;
  else if (tweaks.variant === 'hero') body = <VariantHero tweaks={tweaks} onLaunch={onLaunch} />;
  else body = <VariantCards tweaks={tweaks} onLaunch={onLaunch} />;

  return (
    <>
      <style>{portalCss}</style>
      <div className="pp" data-screen-label="Portal">
        <Header user={user} tweaks={tweaks} onLogout={onLogout} />
        <div className="pp__body">
          {body}
          <NewsletterSidebar />
        </div>
      </div>

      <window.TweaksPanel title="Tweaks · 포털">
        <window.TweakSection title="레이아웃">
          <window.TweakRadio
            label="변형"
            value={tweaks.variant}
            options={[
              { value: 'tiles', label: 'Tiles' },
              { value: 'cards', label: 'Cards' },
              { value: 'hero', label: 'Hero' },
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
            label="타일 모서리 (tiles)"
            value={tweaks.tileShape}
            options={[
              { value: 'rounded', label: 'Rounded' },
              { value: 'square', label: 'Square' },
              { value: 'pill', label: 'Pill' },
            ]}
            onChange={(v) => setTweak('tileShape', v)}
          />
        </window.TweakSection>
        <window.TweakSection title="옵션">
          <window.TweakToggle label="인사말 표시" value={tweaks.showGreeting} onChange={(v) => setTweak('showGreeting', v)} />
          <window.TweakToggle label="검색 바" value={tweaks.showSearch} onChange={(v) => setTweak('showSearch', v)} />
          <window.TweakToggle label="상태 칩 (cards)" value={tweaks.showStatus} onChange={(v) => setTweak('showStatus', v)} />
          <window.TweakToggle label="하단 메타 (tiles)" value={tweaks.showFooterMeta} onChange={(v) => setTweak('showFooterMeta', v)} />
        </window.TweakSection>
      </window.TweaksPanel>
    </>
  );
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<PortalApp />);
