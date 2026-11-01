import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import './App.css'

type Theme = 'light' | 'dark'
type View = 'community' | 'report' | 'issue' | 'admin' | 'leaderboard' | 'map'
type AccountRole = 'public' | 'organisation'
type AuthStage = 'choose' | 'login' | 'app'
type Attachment = { id: string; file: File }
type Issue = {
  id: string
  title: string
  description: string
  category: string
  locality: string
  createdAt: string
  votes: number
  status: 'Reported' | 'In Progress' | 'Resolved'
  attachments: Attachment[]
  coordinates: { latitude: number; longitude: number } | null
}
type LocationSuggestion = { label: string; lat: string; lon: string }

const categories = [
  'Roads & Potholes',
  'Sanitation / Waste Management',
  'Water Supply',
  'Streetlights & Electricity',
  'Drainage & Flooding',
  'Public Safety',
  'Parks & Public Spaces',
  'Other',
]

const initialIssues: Issue[] = [
  {
    id: 'CL-1024',
    title: 'Large pothole near the main gate',
    description: 'A deep pothole has formed near the main gate and is difficult to see after sunset.',
    category: 'Roads & Potholes',
    locality: 'Lamphel, Imphal, Manipur',
    createdAt: 'Today',
    votes: 24,
    status: 'In Progress',
    attachments: [],
    coordinates: null,
  },
  {
    id: 'CL-1023',
    title: 'Garbage collection needed near the market',
    description: 'Waste has accumulated near the market entrance. Please arrange collection.',
    category: 'Sanitation / Waste Management',
    locality: 'Thangal Bazar, Imphal, Manipur',
    createdAt: 'Yesterday',
    votes: 18,
    status: 'Reported',
    attachments: [],
    coordinates: null,
  },
  {
    id: 'CL-1022',
    title: 'Streetlight not working',
    description: 'The streetlight on the lane has not worked for several nights.',
    category: 'Streetlights & Electricity',
    locality: 'Porompat, Imphal, Manipur',
    createdAt: '2 days ago',
    votes: 12,
    status: 'Resolved',
    attachments: [],
    coordinates: null,
  },
]

function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true as const }
  switch (name) {
    case 'sun':
      return <svg {...common}><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42" /></svg>
    case 'moon':
      return <svg {...common}><path d="M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5 8.6 8.6 0 1 0 20.5 14.2Z" /></svg>
    case 'thumb':
      return <svg {...common}><path d="M7 10v11H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3Zm0 0 5-8c1.4 0 2.2 1.2 1.8 2.6L13 9h5.5a2.5 2.5 0 0 1 2.4 3.2l-1.7 6.2a3.5 3.5 0 0 1-3.4 2.6H7" /></svg>
    case 'pin':
      return <svg {...common}><path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></svg>
    case 'home':
      return <svg {...common}><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z" /></svg>
    case 'plus':
      return <svg {...common}><path d="M12 5v14M5 12h14" /></svg>
    case 'map':
      return <svg {...common}><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3Z" /><path d="M9 3v15m6-12v15" /></svg>
    case 'users':
      return <svg {...common}><path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="10" cy="7" r="4" /><path d="M20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></svg>
    case 'chart':
      return <svg {...common}><path d="M4 20V10m6 10V4m6 16v-7m6 7H2" /></svg>
    case 'trophy':
      return <svg {...common}><path d="M8 21h8m-4-4v4M7 4h10v5a5 5 0 0 1-10 0Zm0 2H3v2a5 5 0 0 0 5 5m9-7h4v2a5 5 0 0 1-5 5" /></svg>
    case 'message':
      return <svg {...common}><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5 9 9 0 0 1-4-.9L3 21l1.9-5.5a9 9 0 0 1-.9-4A8.5 8.5 0 0 1 12.5 3H13a8.5 8.5 0 0 1 8 8.5Z" /></svg>
    case 'camera':
      return <svg {...common}><path d="M14 5 12.5 3h-5L6 5H4a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2Z" /><circle cx="12" cy="12" r="4" /></svg>
    case 'file':
      return <svg {...common}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" /><path d="M14 2v6h6M8 13h8m-8 4h8" /></svg>
    default:
      return <svg {...common}><circle cx="12" cy="12" r="9" /></svg>
  }
}

async function searchLocations(query: string): Promise<LocationSuggestion[]> {
  const params = new URLSearchParams({ q: query, format: 'jsonv2', addressdetails: '1', limit: '6' })
  const response = await fetch(`https://nominatim.openstreetmap.org/search?${params.toString()}`)
  if (!response.ok) throw new Error('Location search failed')
  const results: { display_name: string; lat: string; lon: string }[] = await response.json()
  return results.map((item) => ({ label: item.display_name, lat: item.lat, lon: item.lon }))
}

async function reverseGeocode(latitude: number, longitude: number): Promise<string> {
  const params = new URLSearchParams({ lat: String(latitude), lon: String(longitude), format: 'jsonv2', addressdetails: '1' })
  const response = await fetch(`https://nominatim.openstreetmap.org/reverse?${params.toString()}`)
  if (!response.ok) throw new Error('Could not find the address')
  const result: { display_name?: string } = await response.json()
  if (!result.display_name) throw new Error('No address was found for these coordinates')
  return result.display_name
}

function App() {
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem('civiclens-theme') as Theme) || 'light')
  const [view, setView] = useState<View>('community')
  const [accountRole, setAccountRole] = useState<AccountRole | null>(null)
  const [authStage, setAuthStage] = useState<AuthStage>('choose')
  const [loginEmail, setLoginEmail] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [loginMessage, setLoginMessage] = useState('')
  const [issues, setIssues] = useState<Issue[]>(initialIssues)
  const [selectedIssueId, setSelectedIssueId] = useState('CL-1024')
  const [searchText, setSearchText] = useState('')
  const [reportTitle, setReportTitle] = useState('')
  const [reportDescription, setReportDescription] = useState('')
  const [reportCategory, setReportCategory] = useState(categories[0])
  const [reportLocality, setReportLocality] = useState('')
  const [reportCoordinates, setReportCoordinates] = useState<{ latitude: number; longitude: number } | null>(null)
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [locationSuggestions, setLocationSuggestions] = useState<LocationSuggestion[]>([])
  const [locationLoading, setLocationLoading] = useState(false)
  const [locationMessage, setLocationMessage] = useState('')
  const [gpsLoading, setGpsLoading] = useState(false)
  const [formMessage, setFormMessage] = useState('')
  const [votedIds, setVotedIds] = useState<string[]>([])
  const [sort, setSort] = useState<'Hot' | 'New' | 'Top'>('Hot')
  const [filter, setFilter] = useState('All')
  const [commentText, setCommentText] = useState('')
  const [comments, setComments] = useState<Record<string, string[]>>({})
  const searchTimer = useRef<number | undefined>(undefined)
  const selectedLocationRef = useRef(false)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('civiclens-theme', theme)
  }, [theme])

  useEffect(() => {
    const query = reportLocality.trim()
    if (selectedLocationRef.current) {
      selectedLocationRef.current = false
      return
    }
    if (query.length < 2) {
      setLocationSuggestions([])
      setLocationLoading(false)
      setLocationMessage('')
      return
    }
    let cancelled = false
    window.clearTimeout(searchTimer.current)
    searchTimer.current = window.setTimeout(async () => {
      setLocationLoading(true)
      setLocationMessage('')
      try {
        const results = await searchLocations(query)
        if (cancelled) return
        setLocationSuggestions(results)
        setLocationMessage(results.length ? '' : 'Locality not found')
      } catch {
        if (!cancelled) {
          setLocationSuggestions([])
          setLocationMessage('Could not search locations. You can still enter the locality manually.')
        }
      } finally {
        if (!cancelled) setLocationLoading(false)
      }
    }, 550)
    return () => {
      cancelled = true
      window.clearTimeout(searchTimer.current)
    }
  }, [reportLocality])

  const selectedIssue = issues.find((issue) => issue.id === selectedIssueId) || issues[0]
  const filteredIssues = issues
    .filter((issue) => filter === 'All' || issue.status === filter)
    .filter((issue) => `${issue.title} ${issue.description} ${issue.locality} ${issue.category}`.toLowerCase().includes(searchText.toLowerCase()))
    .sort((a, b) => sort === 'New' ? b.id.localeCompare(a.id) : sort === 'Top' ? b.votes - a.votes : b.votes - a.votes)

  function addFiles(event: ChangeEvent<HTMLInputElement>) {
    const incoming = Array.from(event.target.files || [])
    setAttachments((current) => {
      const next = [...current]
      incoming.forEach((file) => {
        if (!next.some((item) => item.file.name === file.name && item.file.size === file.size)) {
          next.push({ id: `${file.name}-${file.size}-${file.lastModified}`, file })
        }
      })
      return next
    })
    event.target.value = ''
  }

  function removeFile(id: string) {
    setAttachments((current) => current.filter((item) => item.id !== id))
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      setLocationMessage('Location is not supported by this browser. Enter the locality manually.')
      return
    }
    setGpsLoading(true)
    setLocationMessage('Please allow location access if your browser asks.')
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const { latitude, longitude } = position.coords
        setReportCoordinates({ latitude, longitude })
        try {
          const address = await reverseGeocode(latitude, longitude)
          selectedLocationRef.current = true
          setReportLocality(address)
          setLocationSuggestions([])
          setLocationMessage('Current location detected.')
        } catch {
          selectedLocationRef.current = true
          setReportLocality(`${latitude.toFixed(6)}, ${longitude.toFixed(6)}`)
          setLocationSuggestions([])
          setLocationMessage('Coordinates detected; address lookup was unavailable.')
        } finally {
          setGpsLoading(false)
        }
      },
      (error) => {
        setGpsLoading(false)
        if (error.code === error.PERMISSION_DENIED) setLocationMessage('Permission denied. You can enter the locality manually.')
        else if (error.code === error.TIMEOUT) setLocationMessage('Location request timed out. Try again or type the locality.')
        else setLocationMessage('Unable to detect location. Please type the locality manually.')
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    )
  }

  function chooseLocation(suggestion: LocationSuggestion) {
    selectedLocationRef.current = true
    setReportLocality(suggestion.label)
    setReportCoordinates({ latitude: Number(suggestion.lat), longitude: Number(suggestion.lon) })
    setLocationSuggestions([])
    setLocationMessage('Location selected.')
  }

  function submitReport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!reportTitle.trim() || !reportDescription.trim() || !reportLocality.trim()) {
      setFormMessage('Please complete the title, description and locality.')
      return
    }
    const nextNumber = 1025 + issues.length - initialIssues.length
    const newIssue: Issue = {
      id: `CL-${nextNumber}`,
      title: reportTitle.trim(),
      description: reportDescription.trim(),
      category: reportCategory,
      locality: reportLocality.trim(),
      createdAt: 'Just now',
      votes: 1,
      status: 'Reported',
      attachments,
      coordinates: reportCoordinates,
    }
    setIssues((current) => [newIssue, ...current])
    setSelectedIssueId(newIssue.id)
    setReportTitle('')
    setReportDescription('')
    setReportCategory(categories[0])
    setReportLocality('')
    setReportCoordinates(null)
    setAttachments([])
    setLocationSuggestions([])
    setLocationMessage('')
    setFormMessage('Report created in this frontend demo. Backend submission is not connected yet.')
    setView('issue')
  }

  function toggleVote(issueId: string) {
    const alreadyVoted = votedIds.includes(issueId)
    setVotedIds((current) => alreadyVoted ? current.filter((id) => id !== issueId) : [...current, issueId])
    setIssues((current) => current.map((issue) => issue.id === issueId ? { ...issue, votes: issue.votes + (alreadyVoted ? -1 : 1) } : issue))
  }

  function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!commentText.trim() || !selectedIssue) return
    setComments((current) => ({ ...current, [selectedIssue.id]: [...(current[selectedIssue.id] || []), commentText.trim()] }))
    setCommentText('')
  }

  const allNavItems: { id: View; label: string; icon: string }[] = [
    { id: 'community', label: 'Community', icon: 'home' },
    { id: 'report', label: 'Report an issue', icon: 'plus' },
    { id: 'issue', label: 'Issue details', icon: 'message' },
    { id: 'admin', label: 'Department dashboard', icon: 'chart' },
    { id: 'leaderboard', label: 'Leaderboard', icon: 'trophy' },
    { id: 'map', label: 'Map & hotspots', icon: 'map' },
  ]
  const navItems = allNavItems.filter((item) => accountRole === 'organisation'
    ? ['admin', 'issue', 'map'].includes(item.id)
    : item.id !== 'admin')

  function handleDemoLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!loginEmail.trim() || !loginPassword.trim()) {
      setLoginMessage('Enter your email and password to continue.')
      return
    }
    setAccountRole(accountRole || 'public')
    setView(accountRole === 'organisation' ? 'admin' : 'community')
    setAuthStage('app')
    setLoginMessage('')
  }

  function signOut() {
    setAuthStage('choose')
    setAccountRole(null)
    setLoginEmail('')
    setLoginPassword('')
    setView('community')
  }

  if (authStage !== 'app') {
    return (
      <div className="auth-shell">
        <div className="auth-decoration auth-decoration-one">✳</div>
        <div className="auth-decoration auth-decoration-two">❧</div>
        <header className="auth-brand">
          <span className="brand-mark"><Icon name="pin" size={23} /></span>
          <span><strong>CivicLens</strong><small>AI-powered civic community</small></span>
        </header>
        <main className="auth-main">
          {authStage === 'choose' ? (
            <>
              <p className="eyebrow">A BETTER CITY STARTS TOGETHER</p>
              <h1>Welcome to <span>CivicLens</span></h1>
              <p className="auth-intro">A shared space for citizens to raise concerns and civic teams to act on them.</p>
              <h2 className="auth-question">How would you like to continue?</h2>
              <div className="role-grid">
                <button className="role-card" onClick={() => { setAccountRole('public'); setAuthStage('login'); setLoginMessage('') }}>
                  <span className="role-icon"><Icon name="users" size={27} /></span>
                  <span className="role-title">Public / Citizen</span>
                  <span className="role-description">Report local issues, support your neighbours, and track updates.</span>
                  <span className="role-continue">Continue as citizen <span>→</span></span>
                </button>
                <button className="role-card" onClick={() => { setAccountRole('organisation'); setAuthStage('login'); setLoginMessage('') }}>
                  <span className="role-icon"><Icon name="chart" size={27} /></span>
                  <span className="role-title">Organisation / Government</span>
                  <span className="role-description">Review complaints, prioritise work, assign teams, and update status.</span>
                  <span className="role-continue">Continue as organisation <span>→</span></span>
                </button>
              </div>
              <p className="auth-footnote">Tiny reports. Clearer action. Greener communities.</p>
            </>
          ) : (
            <div className="login-card">
              <button className="back-link" type="button" onClick={() => { setAuthStage('choose'); setLoginMessage('') }}>← Choose account type</button>
              <div className="login-icon"><Icon name={accountRole === 'organisation' ? 'chart' : 'users'} size={26} /></div>
              <p className="eyebrow">{accountRole === 'organisation' ? 'CIVIC TEAM ACCESS' : 'COMMUNITY ACCESS'}</p>
              <h1>{accountRole === 'organisation' ? 'Organisation login' : 'Citizen login'}</h1>
              <p className="muted">{accountRole === 'organisation' ? 'Access the complaint triage and department workspace.' : 'Sign in to report issues and follow your neighbourhood.'}</p>
              <form className="login-form" onSubmit={handleDemoLogin}>
                <label>Email address<input type="email" value={loginEmail} onChange={(event) => setLoginEmail(event.target.value)} placeholder="you@example.com" required autoComplete="email" /></label>
                <label>Password<input type="password" value={loginPassword} onChange={(event) => setLoginPassword(event.target.value)} placeholder="Enter your password" required autoComplete="current-password" /></label>
                {loginMessage && <p className="form-message" role="alert">{loginMessage}</p>}
                <button className="primary-button login-submit" type="submit">Continue to {accountRole === 'organisation' ? 'workspace' : 'community'} <span>→</span></button>
                <p className="login-demo-note">Demo mode: this screen does not authenticate or store accounts. Connect your backend authentication before production.</p>
              </form>
            </div>
          )}
        </main>
        <footer className="auth-footer">© 2026 CivicLens · Transparent civic action, together</footer>
      </div>
    )
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#home" onClick={(event) => { event.preventDefault(); setView('community') }}>
          <span className="brand-mark">C</span>
          <span><strong>CivicLens</strong><small>AI · Civic Community</small></span>
        </a>
        <div className="city-chip"><Icon name="pin" size={16} /><span>Imphal, Manipur</span><span className="online-dot" /></div>
        <p className="nav-caption">WORKSPACE</p>
        <nav className="main-nav" aria-label="Main navigation">
          {navItems.map((item) => (
            <button key={item.id} className={`nav-item ${view === item.id ? 'active' : ''}`} onClick={() => { setView(item.id); setFormMessage('') }}>
              <Icon name={item.icon} size={18} /><span>{item.label}</span>
              {item.id === 'report' && <span className="nav-plus">+</span>}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          {accountRole === 'public' && <div className="points-card"><span className="points-icon"><Icon name="trophy" size={20} /></span><div><small>Your contribution</small><strong>120 points</strong></div></div>}
          <div className="profile-row"><div className="avatar">A</div><div><strong>{accountRole === 'organisation' ? 'Civic department' : 'Community member'}</strong><small>{accountRole === 'organisation' ? 'Organisation account' : 'Citizen account'}</small></div><button className="profile-menu" onClick={signOut} aria-label="Sign out" title="Sign out">↪</button></div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="mobile-brand"><span className="brand-mark">C</span><strong>CivicLens AI</strong></div>
          <div className="breadcrumb"><span>{accountRole === 'organisation' ? 'Organisation workspace' : 'Community workspace'}</span><span>/</span><strong>{navItems.find((item) => item.id === view)?.label}</strong></div>
          <div className="topbar-actions">
            <label className="search-box"><span className="search-symbol">⌕</span><input value={searchText} onChange={(event) => setSearchText(event.target.value)} placeholder="Search reports..." aria-label="Search reports" /></label>
            <button className="theme-toggle" type="button" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')} aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`} title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}><Icon name={theme === 'light' ? 'moon' : 'sun'} size={19} /></button>
            {accountRole === 'public' && <button className="primary-button top-report" onClick={() => { setView('report'); setFormMessage('') }}><Icon name="plus" size={17} /> Report issue</button>}
          </div>
        </header>

        <div className="content">
          {view === 'community' && (
            <>
              <section className="welcome-row">
                <div><p className="eyebrow">CIVIC PULSE · COMMUNITY</p><h1>Your city, <span>your voice.</span></h1><p className="muted">Report local issues, support your neighbours, and follow progress together.</p></div>
                <button className="primary-button" onClick={() => { setView('report'); setFormMessage('') }}><Icon name="plus" size={17} /> Create a report</button>
              </section>
              <section className="stats-grid">
                <div className="stat-card"><span className="stat-icon"><Icon name="file" /></span><div><small>Community reports</small><strong>{issues.length + 128}</strong><small className="stat-foot">Across your city</small></div></div>
                <div className="stat-card"><span className="stat-icon"><Icon name="chart" /></span><div><small>In progress</small><strong>{issues.filter((issue) => issue.status === 'In Progress').length + 12}</strong><small className="stat-foot">Being addressed</small></div></div>
                <div className="stat-card"><span className="stat-icon"><Icon name="users" /></span><div><small>Community members</small><strong>2,480</strong><small className="stat-foot">Making a difference</small></div></div>
                <div className="stat-card"><span className="stat-icon"><Icon name="trophy" /></span><div><small>Issues resolved</small><strong>{issues.filter((issue) => issue.status === 'Resolved').length + 86}</strong><small className="stat-foot">Together we improve</small></div></div>
              </section>
              <section className="feed-layout">
                <div className="feed-main">
                  <div className="section-heading"><div><h2>Community feed</h2><p className="muted">What needs attention in your neighbourhood?</p></div><div className="sort-tabs">{(['Hot', 'New', 'Top'] as const).map((item) => <button key={item} className={sort === item ? 'selected' : ''} onClick={() => setSort(item)}>{item}</button>)}</div></div>
                  <div className="filter-row">{['All', 'Reported', 'In Progress', 'Resolved'].map((item) => <button key={item} className={filter === item ? 'filter-active' : ''} onClick={() => setFilter(item)}>{item}</button>)}</div>
                  <div className="issue-list">
                    {filteredIssues.map((issue) => <IssueCard key={issue.id} issue={issue} voted={votedIds.includes(issue.id)} onVote={() => toggleVote(issue.id)} onOpen={() => { setSelectedIssueId(issue.id); setView('issue') }} />)}
                    {filteredIssues.length === 0 && <div className="empty-state"><Icon name="file" size={28} /><strong>No reports found</strong><p>Try another search or filter.</p></div>}
                  </div>
                </div>
                <aside className="feed-side">
                  <div className="panel-card city-panel"><div className="panel-title"><h3>City community</h3><span className="live-badge">● Live demo</span></div><div className="city-banner"><span className="city-emblem"><Icon name="home" size={27} /></span><div><strong>Imphal Community</strong><small>Manipur, India</small></div></div><div className="city-stats"><div><strong>2.4k</strong><small>Members</small></div><div><strong>134</strong><small>Reports</small></div><div><strong>86</strong><small>Resolved</small></div></div><button className="outline-button full-width" onClick={() => setView('map')}><Icon name="map" size={16} /> Explore city issues</button></div>
                  <div className="panel-card"><h3>How CivicLens works</h3><div className="mini-step"><span>1</span><div><strong>Report an issue</strong><small>Share the location and evidence.</small></div></div><div className="mini-step"><span>2</span><div><strong>Community supports</strong><small>Vote and add useful updates.</small></div></div><div className="mini-step"><span>3</span><div><strong>Department responds</strong><small>Follow progress transparently.</small></div></div></div>
                </aside>
              </section>
            </>
          )}

          {view === 'report' && (
            <section className="form-page">
              <div className="page-heading"><p className="eyebrow">HELP IMPROVE YOUR CITY</p><h1>Report an <span>issue</span></h1><p className="muted">Share clear details and attach evidence so the right team can understand the problem.</p></div>
              <form className="report-form panel-card" onSubmit={submitReport}>
                <div className="form-section-title"><span className="step-number">01</span><div><h2>Issue details</h2><p className="muted">Describe what is happening.</p></div></div>
                <label className="field-label">Issue title <span className="required">*</span><input value={reportTitle} onChange={(event) => setReportTitle(event.target.value)} maxLength={120} placeholder="e.g. Large pothole near the main gate" required /></label>
                <label className="field-label">Description <span className="required">*</span><textarea value={reportDescription} onChange={(event) => setReportDescription(event.target.value)} rows={5} maxLength={2000} placeholder="Describe the issue, when it started, and how it affects the community..." required /><small className="field-hint">{reportDescription.length}/2000 characters</small></label>
                <div className="form-two-col">
                  <label className="field-label">Category <span className="required">*</span><select value={reportCategory} onChange={(event) => setReportCategory(event.target.value)}>{categories.map((category) => <option key={category}>{category}</option>)}</select><small className="field-hint">This is the citizen-selected category; AI classification is a separate backend step.</small></label>
                  <div className="field-label locality-field"><label htmlFor="report-locality">City / locality <span className="required">*</span></label><div className="locality-input-wrap"><input id="report-locality" value={reportLocality} onChange={(event) => { setReportLocality(event.target.value); setReportCoordinates(null); setLocationMessage('') }} placeholder="Type a city or locality..." maxLength={200} autoComplete="off" required /><button type="button" className="location-button" onClick={useCurrentLocation} disabled={gpsLoading}><Icon name="pin" size={15} />{gpsLoading ? 'Locating…' : 'Use my location'}</button></div>{locationLoading && <p className="location-message">Searching locations…</p>}{locationMessage && <p className="location-message" role="status">{locationMessage}</p>}{locationSuggestions.length > 0 && <div className="locality-suggestions" role="listbox">{locationSuggestions.map((suggestion) => <button type="button" role="option" aria-selected={false} className="locality-suggestion" key={`${suggestion.lat}-${suggestion.lon}`} onClick={() => chooseLocation(suggestion)}><Icon name="pin" size={16} /><span>{suggestion.label}</span></button>)}</div>}</div>
                </div>
                <div className="form-section-title evidence-heading"><span className="step-number">02</span><div><h2>Attach evidence <span className="optional">(optional)</span></h2><p className="muted">Photos, videos or documents can help explain the issue.</p></div></div>
                <div className="upload-options">
                  <label className="upload-option"><input type="file" accept="image/*" capture="environment" onChange={addFiles} /><span className="upload-icon"><Icon name="camera" size={21} /></span><strong>Camera</strong><small>Take a photo</small></label>
                  <label className="upload-option"><input type="file" accept="image/*" multiple onChange={addFiles} /><span className="upload-icon"><Icon name="file" size={21} /></span><strong>Photos</strong><small>Choose images</small></label>
                  <label className="upload-option"><input type="file" accept="video/*" multiple onChange={addFiles} /><span className="upload-icon"><span className="video-glyph">▶</span></span><strong>Videos</strong><small>Choose clips</small></label>
                  <label className="upload-option"><input type="file" accept=".pdf,.doc,.docx,.txt,.xls,.xlsx,.csv,application/*,text/*" multiple onChange={addFiles} /><span className="upload-icon"><Icon name="file" size={21} /></span><strong>Files</strong><small>Documents</small></label>
                </div>
                {attachments.length > 0 && <div className="attachment-list"><div className="attachment-list-heading">{attachments.length} attachment{attachments.length === 1 ? '' : 's'} selected</div>{attachments.map((attachment) => <div className="attachment-row" key={attachment.id}><Icon name="file" size={17} /><span className="attachment-name">{attachment.file.name}</span><small>{formatBytes(attachment.file.size)}</small><button type="button" className="remove-file" onClick={() => removeFile(attachment.id)} aria-label={`Remove ${attachment.file.name}`}>×</button></div>)}</div>}
                <p className="privacy-note"><Icon name="file" size={16} /> Your evidence is selected locally in this demo. Persistent upload requires the backend upload endpoint.</p>
                {formMessage && <p className="form-message" role="status">{formMessage}</p>}
                <div className="form-actions"><button type="button" className="outline-button" onClick={() => setView('community')}>Cancel</button><button type="submit" className="primary-button">Submit report <span>→</span></button></div>
              </form>
            </section>
          )}

          {view === 'issue' && selectedIssue && (
            <section className="detail-page">
              <button className="back-link" onClick={() => setView('community')}>← Back to community</button>
              <div className="detail-layout"><div className="detail-main panel-card"><div className="detail-topline"><span className="issue-id">{selectedIssue.id}</span><StatusBadge status={selectedIssue.status} /></div><h1>{selectedIssue.title}</h1><p className="detail-description">{selectedIssue.description}</p><div className="issue-meta"><span><Icon name="pin" size={16} />{selectedIssue.locality}</span><span>·</span><span>{selectedIssue.createdAt}</span><span>·</span><span>{selectedIssue.category}</span></div>{selectedIssue.attachments.length > 0 && <div className="detail-attachments"><h3>Attached evidence</h3>{selectedIssue.attachments.map((item) => <div key={item.id} className="attachment-row"><Icon name="file" size={16} /><span className="attachment-name">{item.file.name}</span><small>{formatBytes(item.file.size)}</small></div>)}</div>}<div className="detail-actions"><button className={`vote-button ${votedIds.includes(selectedIssue.id) ? 'voted' : ''}`} onClick={() => toggleVote(selectedIssue.id)}><Icon name="thumb" size={18} /> {votedIds.includes(selectedIssue.id) ? 'Supported' : 'Upvote'} <strong>{selectedIssue.votes}</strong></button><span className="detail-action-muted"><Icon name="message" size={16} /> {(comments[selectedIssue.id] || []).length} comments</span></div><div className="detail-tabs"><span className="active">Comments ({(comments[selectedIssue.id] || []).length})</span><span>Updates</span><span>Details</span></div><form className="comment-form" onSubmit={addComment}><input value={commentText} onChange={(event) => setCommentText(event.target.value)} placeholder="Add a constructive comment or update..." /><button className="primary-button" type="submit">Post</button></form>{(comments[selectedIssue.id] || []).map((comment, index) => <div className="comment-item" key={`${index}-${comment}`}><div className="avatar small-avatar">C</div><div><strong>Community member</strong><p>{comment}</p></div></div>)}</div><aside className="detail-aside"><div className="panel-card"><h3>Report status</h3><StatusBadge status={selectedIssue.status} /><div className="timeline"><div className="timeline-item done"><span /><div><strong>Reported</strong><small>Issue submitted to community</small></div></div><div className={`timeline-item ${selectedIssue.status !== 'Reported' ? 'done' : ''}`}><span /><div><strong>In progress</strong><small>Department action stage</small></div></div><div className={`timeline-item ${selectedIssue.status === 'Resolved' ? 'done' : ''}`}><span /><div><strong>Resolved</strong><small>Resolution confirmation</small></div></div></div></div><div className="panel-card ai-placeholder"><span className="ai-tag">AI TRIAGE · BACKEND</span><h3>Incident analysis</h3><p>Category prediction, severity, reasoning and department recommendation will appear here when connected to the AI API.</p><span className="pending-pill">Awaiting backend response</span></div></aside></div>
            </section>
          )}

          {view === 'admin' && <section className="dashboard-page"><div className="page-heading"><p className="eyebrow">OPERATIONS</p><h1>Department <span>dashboard</span></h1><p className="muted">A frontend preview of the triage and accountability workspace.</p></div><div className="admin-stats"><div className="panel-card"><small>Assigned</small><strong>24</strong><span>Awaiting department action</span></div><div className="panel-card"><small>In progress</small><strong>12</strong><span>Work underway</span></div><div className="panel-card"><small>Resolved</small><strong>86</strong><span>Marked resolved</span></div><div className="panel-card"><small>High priority</small><strong>7</strong><span>Needs review</span></div></div><div className="panel-card table-panel"><div className="section-heading"><div><h2>Recent reports</h2><p className="muted">Demo data; real status updates require backend integration.</p></div><span className="pending-pill">Preview</span></div><div className="responsive-table"><table><thead><tr><th>Issue</th><th>Category</th><th>Locality</th><th>Status</th><th>Votes</th></tr></thead><tbody>{issues.map((issue) => <tr key={issue.id}><td><button className="table-link" onClick={() => { setSelectedIssueId(issue.id); setView('issue') }}>{issue.title}</button><small>{issue.id}</small></td><td>{issue.category}</td><td>{issue.locality}</td><td><StatusBadge status={issue.status} /></td><td>{issue.votes}</td></tr>)}</tbody></table></div></div></section>}

          {view === 'leaderboard' && <section className="dashboard-page"><div className="page-heading"><p className="eyebrow">COMMUNITY IMPACT</p><h1>Rewards & <span>leaderboard</span></h1><p className="muted">Recognising citizens and departments that help improve the city.</p></div><div className="leaderboard-layout"><div className="panel-card"><div className="section-heading"><div><h2>Top contributors</h2><p className="muted">Illustrative demo entries</p></div><Icon name="trophy" size={24} /></div>{[{name:'Imphal Civic Circle',points:'4,320',rank:'01',label:'Community'},{name:'P.W.D. Team',points:'3,780',rank:'02',label:'Department'},{name:'Water Works',points:'2,640',rank:'03',label:'Department'},{name:'Community member',points:'120',rank:'04',label:'Citizen'}].map((person) => <div className="leader-row" key={person.rank}><span className={`rank rank-${person.rank}`}>{person.rank}</span><div className="leader-avatar">{person.name.charAt(0)}</div><div className="leader-name"><strong>{person.name}</strong><small>{person.label}</small></div><strong className="leader-points">{person.points} pts</strong></div>)}</div><div className="panel-card reward-panel"><span className="reward-medal"><Icon name="trophy" size={28} /></span><h2>Your impact matters</h2><p className="muted">Reports, constructive updates and verified community support can contribute to a transparent rewards system.</p><div className="reward-total"><strong>120</strong><span>community points</span></div><p className="field-hint">Point totals and reward rules will be provided by the backend.</p></div></div></section>}

          {view === 'map' && <section className="dashboard-page"><div className="page-heading"><p className="eyebrow">CITY VIEW</p><h1>Map & <span>hotspots</span></h1><p className="muted">Explore where reported issues are occurring. Map pins and geographic aggregation require real coordinates and map integration.</p></div><div className="map-layout"><div className="map-preview panel-card"><div className="map-grid-art"><div className="map-road road-a" /><div className="map-road road-b" /><div className="map-road road-c" /><div className="map-park">GREEN SPACE</div>{issues.filter((issue) => issue.coordinates).map((issue, index) => <span key={issue.id} className={`map-dot dot-${index % 3}`} title={issue.title} />)}<div className="map-placeholder-note"><Icon name="map" size={25} /><strong>Map integration placeholder</strong><span>Connect Leaflet / OpenStreetMap to display actual issue coordinates.</span></div></div></div><div className="panel-card hotspot-list"><h2>Reported nearby</h2>{issues.map((issue) => <button className="hotspot-row" key={issue.id} onClick={() => { setSelectedIssueId(issue.id); setView('issue') }}><span className="hotspot-pin"><Icon name="pin" size={17} /></span><span><strong>{issue.title}</strong><small>{issue.locality}</small></span><span className="hotspot-votes">{issue.votes} ↑</span></button>)}</div></div></section>}
        </div>
        <footer className="footer"><span>© 2026 CivicLens AI</span><span>Built for clearer civic communication</span><span className="footer-status"><span /> Frontend demo</span></footer>
      </main>
    </div>
  )
}

function IssueCard({ issue, voted, onVote, onOpen }: { issue: Issue; voted: boolean; onVote: () => void; onOpen: () => void }) {
  return <article className="issue-card"><div className="vote-rail"><button className={`vote-button ${voted ? 'voted' : ''}`} onClick={onVote} aria-label={voted ? 'Remove upvote' : 'Upvote issue'}><Icon name="thumb" size={18} /></button><strong>{issue.votes}</strong><small>votes</small></div><div className="issue-card-body"><div className="issue-card-top"><span className="category-pill">{issue.category}</span><StatusBadge status={issue.status} /></div><button className="issue-title-button" onClick={onOpen}>{issue.title}</button><p>{issue.description}</p><div className="issue-card-meta"><span><Icon name="pin" size={14} />{issue.locality}</span><span>{issue.createdAt}</span><span><Icon name="message" size={14} /> Community discussion</span></div><div className="issue-card-bottom"><button onClick={onOpen} className="text-action">View details <span>→</span></button><span className="issue-small-id">{issue.id}</span></div></div></article>
}

function StatusBadge({ status }: { status: Issue['status'] }) {
  return <span className={`status-badge status-${status.toLowerCase().replace(' ', '-')}`}>{status}</span>
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default App
