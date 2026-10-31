
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import './App.css'

type Category =
  | 'Roads'
  | 'Streetlights'
  | 'Garbage'
  | 'Drainage'
  | 'Water'
  | 'Public safety'
  | 'Other'

type IssueStatus = 'Reported' | 'Under review' | 'In progress' | 'Resolved'
type Severity = 'Low' | 'Medium' | 'High' | 'Critical'
type Theme = 'light' | 'dark'

type Issue = {
  id: number
  title: string
  description: string
  category: Category
  location: string
  locality: string
  votes: number
  comments: number
  status: IssueStatus
  severity: Severity
  department: string
  time: string
}

const categories: Array<Category | 'All issues'> = [
  'All issues',
  'Roads',
  'Streetlights',
  'Garbage',
  'Drainage',
  'Water',
  'Public safety',
  'Other',
]

const initialIssues: Issue[] = [
  {
    id: 1,
    title: 'Deep pothole near the main market crossing',
    description:
      'A large pothole has formed near the market crossing. Two-wheelers are swerving to avoid it, especially during evening traffic.',
    category: 'Roads',
    location: 'Market Crossing',
    locality: 'Lamphel',
    votes: 128,
    comments: 24,
    status: 'Under review',
    severity: 'High',
    department: 'Roads & Infrastructure',
    time: '18 min ago',
  },
  {
    id: 2,
    title: 'Streetlights not working on the residential road',
    description:
      'Several streetlights have been out for the past few nights, leaving a long stretch of the road poorly lit after sunset.',
    category: 'Streetlights',
    location: 'Thangmeiband Road',
    locality: 'Thangmeiband',
    votes: 86,
    comments: 12,
    status: 'Reported',
    severity: 'Medium',
    department: 'Electrical Services',
    time: '42 min ago',
  },
  {
    id: 3,
    title: 'Overflowing community waste collection point',
    description:
      'Waste has accumulated around the collection point. The area needs cleaning and a check on collection frequency.',
    category: 'Garbage',
    location: 'Community Park Entrance',
    locality: 'Singjamei',
    votes: 64,
    comments: 18,
    status: 'In progress',
    severity: 'Medium',
    department: 'Sanitation',
    time: '1 hr ago',
  },
  {
    id: 4,
    title: 'Drain cover missing beside a busy footpath',
    description:
      'An open drain is exposed beside the footpath. People walking here have to step into the road to get around it.',
    category: 'Drainage',
    location: 'Keishamthong Junction',
    locality: 'Keishamthong',
    votes: 143,
    comments: 31,
    status: 'Under review',
    severity: 'Critical',
    department: 'Drainage & Public Works',
    time: '2 hrs ago',
  },
  {
    id: 5,
    title: 'Low water pressure in nearby households',
    description:
      'Residents have reported unusually low water pressure this morning. The cause has not yet been confirmed.',
    category: 'Water',
    location: 'Lane 4, Central Colony',
    locality: 'Wangkhei',
    votes: 37,
    comments: 9,
    status: 'Reported',
    severity: 'Low',
    department: 'Water Supply',
    time: '3 hrs ago',
  },
]

type IconName =
  | 'leaf'
  | 'sun'
  | 'moon'
  | 'search'
  | 'plus'
  | 'thumb'
  | 'message'
  | 'pin'
  | 'clock'
  | 'filter'
  | 'check'
  | 'close'
  | 'arrow'
  | 'menu'
  | 'shield'
  | 'sparkle'

function Icon({
  name,
  size = 18,
  filled = false,
}: {
  name: IconName
  size?: number
  filled?: boolean
}) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.7,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true as const,
  }

  switch (name) {
    case 'leaf':
      return (
        <svg {...common}>
          <path d="M20 4C11 4 5 7 5 14a6 6 0 0 0 6 6c7 0 9-8 9-16Z" />
          <path d="M4 21c3-6 7-9 12-12" />
        </svg>
      )
    case 'sun':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42" />
        </svg>
      )
    case 'moon':
      return (
        <svg {...common}>
          <path d="M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z" />
          <path d="M16 3v4m-2-2h4" />
        </svg>
      )
    case 'search':
      return (
        <svg {...common}>
          <circle cx="10.8" cy="10.8" r="6.8" />
          <path d="m16 16 4.5 4.5" />
        </svg>
      )
    case 'plus':
      return (
        <svg {...common}>
          <path d="M12 5v14M5 12h14" />
        </svg>
      )
    case 'thumb':
      return (
        <svg {...common} fill={filled ? 'currentColor' : 'none'}>
          <path d="M7 10v11H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3Z" />
          <path d="M7 10 12 3a2.5 2.5 0 0 1 2 3v4h4.5a2.5 2.5 0 0 1 2.4 3.2l-2 7A2.5 2.5 0 0 1 16.5 22H7" />
        </svg>
      )
    case 'message':
      return (
        <svg {...common}>
          <path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5Z" />
        </svg>
      )
    case 'pin':
      return (
        <svg {...common}>
          <path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" />
          <circle cx="12" cy="10" r="2.5" />
        </svg>
      )
    case 'clock':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 2" />
        </svg>
      )
    case 'filter':
      return (
        <svg {...common}>
          <path d="M4 7h16M7 12h10m-7 5h4" />
          <circle cx="8" cy="7" r="1.5" />
          <circle cx="15" cy="12" r="1.5" />
        </svg>
      )
    case 'check':
      return (
        <svg {...common}>
          <path d="m5 12 4 4L19 6" />
        </svg>
      )
    case 'close':
      return (
        <svg {...common}>
          <path d="m18 6-12 12M6 6l12 12" />
        </svg>
      )
    case 'arrow':
      return (
        <svg {...common}>
          <path d="M5 12h14m-6-6 6 6-6 6" />
        </svg>
      )
    case 'menu':
      return (
        <svg {...common}>
          <path d="M4 7h16M4 12h16M4 17h16" />
        </svg>
      )
    case 'shield':
      return (
        <svg {...common}>
          <path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z" />
          <path d="m9 12 2 2 4-4" />
        </svg>
      )
    case 'sparkle':
      return (
        <svg {...common}>
          <path d="m12 3 1.9 5.8L20 11l-6.1 2.2L12 19l-2-5.8L4 11l6-2.2L12 3Z" />
          <path d="m19 14 1.2 2.8L23 18l-2.8 1.2L19 22l-1.2-2.8L15 18l2.8-1.2L19 14Z" />
        </svg>
      )
  }
}

function getCategoryClass(category: Category) {
  return category.toLowerCase().replace(/\s+/g, '-')
}

function App() {
  const [theme, setTheme] = useState<Theme>(() => {
    try {
      return localStorage.getItem('civiclens-theme') === 'dark'
        ? 'dark'
        : 'light'
    } catch {
      return 'light'
    }
  })

  const [issues, setIssues] = useState<Issue[]>(initialIssues)
  const [votedIds, setVotedIds] = useState<number[]>([])
  const [activeCategory, setActiveCategory] =
    useState<Category | 'All issues'>('All issues')
  const [activeSort, setActiveSort] = useState('Trending')
  const [search, setSearch] = useState('')
  const [showReportForm, setShowReportForm] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [reportTitle, setReportTitle] = useState('')
  const [reportDescription, setReportDescription] = useState('')
  const [reportCategory, setReportCategory] = useState<Category>('Roads')
  const [reportLocality, setReportLocality] = useState('')
  const [formError, setFormError] = useState('')
  const [reportNotice, setReportNotice] = useState('')

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem('civiclens-theme', theme)
    } catch {
      // The theme still works if browser storage is unavailable.
    }
  }, [theme])

  const visibleIssues = useMemo(() => {
    const query = search.trim().toLowerCase()

    const filtered = issues.filter((issue) => {
      const matchesCategory =
        activeCategory === 'All issues' || issue.category === activeCategory

      const matchesSearch =
        !query ||
        [
          issue.title,
          issue.description,
          issue.location,
          issue.locality,
          issue.category,
        ].some((value) => value.toLowerCase().includes(query))

      return matchesCategory && matchesSearch
    })

    return [...filtered].sort((a, b) => {
      if (activeSort === 'Latest') return b.id - a.id
      if (activeSort === 'Most supported') return b.votes - a.votes
      if (activeSort === 'Critical issues') {
        const severityRank: Record<Severity, number> = {
          Critical: 4,
          High: 3,
          Medium: 2,
          Low: 1,
        }
        return severityRank[b.severity] - severityRank[a.severity]
      }
      return b.votes + b.comments - (a.votes + a.comments)
    })
  }, [issues, activeCategory, activeSort, search])

  function toggleVote(issueId: number) {
    const alreadyVoted = votedIds.includes(issueId)

    setVotedIds((current) =>
      alreadyVoted
        ? current.filter((id) => id !== issueId)
        : [...current, issueId],
    )

    setIssues((current) =>
      current.map((issue) =>
        issue.id === issueId
          ? { ...issue, votes: issue.votes + (alreadyVoted ? -1 : 1) }
          : issue,
      ),
    )
  }

  function submitReport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const title = reportTitle.trim()
    const description = reportDescription.trim()
    const locality = reportLocality.trim()

    if (title.length < 5) {
      setFormError('Please enter a title with at least 5 characters.')
      return
    }

    if (description.length < 15) {
      setFormError('Please describe the issue in at least 15 characters.')
      return
    }

    if (!locality) {
      setFormError('Please enter the locality where the issue is located.')
      return
    }

    const newIssue: Issue = {
      id: Math.max(0, ...issues.map((issue) => issue.id)) + 1,
      title,
      description,
      category: reportCategory,
      location: locality,
      locality,
      votes: 1,
      comments: 0,
      status: 'Reported',
      severity: 'Medium',
      department: 'Pending review',
      time: 'Just now',
    }

    setIssues((current) => [newIssue, ...current])
    setVotedIds((current) => [...current, newIssue.id])
    setActiveCategory('All issues')
    setActiveSort('Latest')
    setSearch('')
    setReportTitle('')
    setReportDescription('')
    setReportLocality('')
    setFormError('')
    setShowReportForm(false)
    setReportNotice(
      'Demo report created locally. It has not been sent to a real department.',
    )
  }

  const isDark = theme === 'dark'

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#home" aria-label="CivicLens AI home">
          <span className="brand-mark">
            <Icon name="leaf" size={25} />
          </span>
          <span className="brand-copy">
            <span className="brand-name">civic<span>lens</span></span>
            <span className="brand-tagline">YOUR CITY, IN FOCUS</span>
          </span>
        </a>

        <nav className={`main-nav ${mobileMenuOpen ? 'nav-open' : ''}`}>
          <a className="nav-link nav-link-active" href="#feed">
            Community
          </a>
          <a className="nav-link" href="#how-it-works">
            How it works
          </a>
          <a className="nav-link" href="#accountability">
            Accountability
          </a>
        </nav>

        <div className="topbar-actions">
          <button
            className="theme-toggle"
            type="button"
            onClick={() => setTheme(isDark ? 'light' : 'dark')}
            aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`}
            title={`Switch to ${isDark ? 'light' : 'dark'} mode`}
          >
            <Icon name={isDark ? 'sun' : 'moon'} size={19} />
          </button>

          <button
            className="button button-primary header-report"
            type="button"
            onClick={() => setShowReportForm(true)}
          >
            <Icon name="plus" size={17} />
            <span>Report an issue</span>
          </button>

          <button
            className="mobile-menu-button"
            type="button"
            aria-label="Toggle navigation"
            aria-expanded={mobileMenuOpen}
            onClick={() => setMobileMenuOpen((open) => !open)}
          >
            <Icon name={mobileMenuOpen ? 'close' : 'menu'} size={22} />
          </button>
        </div>
      </header>

      <main id="home">
        <section className="hero-section">
          <div className="hero-copy">
            <div className="eyebrow">
              <span className="eyebrow-dot" />
              A BETTER CITY STARTS HERE
            </div>
            <h1>
              See a problem.
              <br />
              <span>Make it matter.</span>
            </h1>
            <p className="hero-description">
              Your neighbourhood, your voice. Report local issues, support
              your community, and follow every step toward a better city.
            </p>

            <div className="hero-actions">
              <button
                className="button button-primary button-large"
                type="button"
                onClick={() => setShowReportForm(true)}
              >
                <Icon name="plus" size={18} />
                Report an issue
              </button>
              <a className="text-link" href="#feed">
                Explore community <Icon name="arrow" size={17} />
              </a>
            </div>

            <div className="hero-note">
              <Icon name="shield" size={16} />
              <span>Community-powered. Transparent by design.</span>
            </div>
          </div>

          <div className="hero-art" aria-label="Illustration of a greener city">
            <div className="art-sun" />
            <div className="art-cloud art-cloud-one" />
            <div className="art-cloud art-cloud-two" />
            <div className="art-building building-one">
              <span /><span /><span /><span />
            </div>
            <div className="art-building building-two">
              <span /><span /><span /><span /><span /><span />
            </div>
            <div className="art-building building-three">
              <span /><span /><span /><span />
            </div>
            <div className="art-tree tree-one"><span /></div>
            <div className="art-tree tree-two"><span /></div>
            <div className="art-road" />
            <div className="art-road-line" />
            <div className="art-issue-pin">
              <span className="pin-icon"><Icon name="pin" size={19} /></span>
              <span>
                <strong>Your neighbourhood</strong>
                <small>Every voice counts</small>
              </span>
            </div>
            <div className="art-caption">
              <span className="art-caption-dot" />
              Small actions. Real community.
            </div>
          </div>
        </section>

        <section className="community-section" id="feed">
          <div className="section-heading">
            <div>
              <div className="section-kicker">THE COMMUNITY BOARD</div>
              <h2>What needs attention?</h2>
              <p className="section-description">
                Local concerns, shared openly. Add your voice to the issues
                that matter.
              </p>
            </div>
            <div className="demo-label">
              <span className="demo-label-dot" />
              Interactive demo
            </div>
          </div>

          {reportNotice && (
            <div className="notice-banner" role="status">
              <Icon name="check" size={18} />
              <span>{reportNotice}</span>
              <button
                type="button"
                className="icon-button notice-close"
                aria-label="Dismiss notice"
                onClick={() => setReportNotice('')}
              >
                <Icon name="close" size={17} />
              </button>
            </div>
          )}

          <div className="feed-toolbar">
            <label className="search-box">
              <Icon name="search" size={19} />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search issues, places..."
                aria-label="Search issues and locations"
              />
              {search && (
                <button
                  className="search-clear"
                  type="button"
                  aria-label="Clear search"
                  onClick={() => setSearch('')}
                >
                  <Icon name="close" size={15} />
                </button>
              )}
            </label>

            <label className="sort-control">
              <Icon name="filter" size={17} />
              <span className="sort-label">Sort</span>
              <select
                value={activeSort}
                onChange={(event) => setActiveSort(event.target.value)}
                aria-label="Sort issues"
              >
                <option>Trending</option>
                <option>Latest</option>
                <option>Most supported</option>
                <option>Critical issues</option>
              </select>
            </label>
          </div>

          <div className="feed-layout">
            <aside className="feed-sidebar">
              <div className="sidebar-heading">Browse issues</div>
              <div className="category-list">
                {categories.map((category) => {
                  const count =
                    category === 'All issues'
                      ? issues.length
                      : issues.filter((issue) => issue.category === category)
                          .length

                  return (
                    <button
                      key={category}
                      type="button"
                      className={`category-button ${
                        activeCategory === category ? 'category-active' : ''
                      }`}
                      onClick={() => setActiveCategory(category)}
                    >
                      <span className="category-name">
                        {category === 'All issues' ? (
                          <span className="category-symbol all-symbol">
                            <Icon name="menu" size={17} />
                          </span>
                        ) : (
                          <span
                            className={`category-symbol ${getCategoryClass(category)}`}
                          >
                            <span />
                          </span>
                        )}
                        {category}
                      </span>
                      <span className="category-count">{count}</span>
                    </button>
                  )
                })}
              </div>

              <div className="sidebar-divider" />

              <div className="sidebar-tip">
                <span className="tip-icon"><Icon name="sparkle" size={19} /></span>
                <strong>Better together.</strong>
                <p>
                  Support an existing report before creating a new one about
                  the same problem.
                </p>
                <a href="#how-it-works">
                  Why it matters <Icon name="arrow" size={14} />
                </a>
              </div>
            </aside>

            <div className="issue-feed">
              <div className="feed-heading">
                <div>
                  <h3>
                    {activeCategory === 'All issues'
                      ? 'Community reports'
                      : activeCategory}
                  </h3>
                  <p>
                    {visibleIssues.length}{' '}
                    {visibleIssues.length === 1 ? 'issue' : 'issues'} found
                  </p>
                </div>
                <button
                  type="button"
                  className="button button-outline compact-report"
                  onClick={() => setShowReportForm(true)}
                >
                  <Icon name="plus" size={16} /> Add report
                </button>
              </div>

              {visibleIssues.length > 0 ? (
                <div className="issue-list">
                  {visibleIssues.map((issue) => {
                    const hasVoted = votedIds.includes(issue.id)

                    return (
                      <article className="issue-card" key={issue.id}>
                        <div className="issue-card-main">
                          <div className="issue-meta">
                            <span
                              className={`category-pill ${getCategoryClass(issue.category)}`}
                            >
                              <span className="pill-dot" />
                              {issue.category}
                            </span>
                            <span className="meta-separator">·</span>
                            <span className="issue-time">
                              <Icon name="clock" size={13} />
                              {issue.time}
                            </span>
                          </div>

                          <h4 className="issue-title">{issue.title}</h4>
                          <p className="issue-description">{issue.description}</p>

                          <div className="issue-location">
                            <Icon name="pin" size={15} />
                            <span>{issue.location}</span>
                            <span className="location-separator">·</span>
                            <span>{issue.locality}</span>
                          </div>

                          <div className="issue-footer">
                            <div className="issue-engagement">
                              <button
                                type="button"
                                className={`vote-button ${hasVoted ? 'vote-active' : ''}`}
                                onClick={() => toggleVote(issue.id)}
                                aria-label={`${hasVoted ? 'Remove support from' : 'Support'} ${issue.title}`}
                                aria-pressed={hasVoted}
                              >
                                <Icon name="thumb" size={16} filled={hasVoted} />
                                <span>{issue.votes}</span>
                              </button>
                              <span className="engagement-divider" />
                              <span className="comment-count">
                                <Icon name="message" size={15} />
                                {issue.comments} comments
                              </span>
                            </div>

                            <button
                              type="button"
                              className="issue-detail-button"
                              onClick={() =>
                                setReportNotice(
                                  `Issue details: ${issue.title}. Department: ${issue.department}. This is demonstration data; no live agency status is connected.`,
                                )
                              }
                            >
                              Details <Icon name="arrow" size={15} />
                            </button>
                          </div>
                        </div>

                        <aside className="issue-card-aside">
                          <span className={`severity severity-${issue.severity.toLowerCase()}`}>
                            <span className="severity-dot" />
                            {issue.severity}
                          </span>
                          <span className={`status-badge status-${issue.status.toLowerCase().replace(/\s+/g, '-')}`}>
                            {issue.status === 'Resolved' && <Icon name="check" size={12} />}
                            {issue.status}
                          </span>
                          <span className="department-label">SUGGESTED DEPT.</span>
                          <span className="department-name">{issue.department}</span>
                          <span className="ai-note">
                            <Icon name="sparkle" size={13} /> Demo assessment
                          </span>
                        </aside>
                      </article>
                    )
                  })}
                </div>
              ) : (
                <div className="empty-state">
                  <span className="empty-state-icon"><Icon name="search" size={25} /></span>
                  <h3>No issues found</h3>
                  <p>Try another search or choose a different category.</p>
                  <button
                    className="button button-outline"
                    type="button"
                    onClick={() => {
                      setSearch('')
                      setActiveCategory('All issues')
                    }}
                  >
                    Clear filters
                  </button>
                </div>
              )}

              <div className="feed-end-note">
                <Icon name="shield" size={16} />
                <span>
                  Demo reports are stored only in this page's current session.
                  They have not been sent to a real authority.
                </span>
              </div>
            </div>
          </div>
        </section>

        <section className="how-section" id="how-it-works">
          <div className="how-heading">
            <span className="section-kicker">A SIMPLE WAY FORWARD</span>
            <h2>From concern to community action.</h2>
            <p>Clear steps. Shared responsibility. Better-informed decisions.</p>
          </div>
          <div className="steps-grid">
            <article className="step-card">
              <span className="step-number">01</span>
              <span className="step-icon"><Icon name="plus" size={21} /></span>
              <h3>Report what you see</h3>
              <p>Describe a local issue and tell your community where it needs attention.</p>
            </article>
            <article className="step-card">
              <span className="step-number">02</span>
              <span className="step-icon"><Icon name="thumb" size={21} /></span>
              <h3>Build community support</h3>
              <p>Support existing reports so shared concerns are easier to identify.</p>
            </article>
            <article className="step-card">
              <span className="step-number">03</span>
              <span className="step-icon"><Icon name="check" size={21} /></span>
              <h3>Follow the progress</h3>
              <p>Track updates transparently when an authorized department integration is available.</p>
            </article>
          </div>
        </section>

        <section className="accountability-strip" id="accountability">
          <span className="accountability-icon"><Icon name="shield" size={24} /></span>
          <div>
            <h3>Trust starts with transparency.</h3>
            <p>
              Community support is not the same as safety severity. AI
              suggestions are not official decisions, and a submitted report
              is not proof that an authority has received it.
            </p>
          </div>
          <span className="accountability-tag">BUILT FOR ACCOUNTABILITY</span>
        </section>
      </main>

      <footer className="site-footer">
        <a className="footer-brand" href="#home">
          <Icon name="leaf" size={19} />
          civic<span>lens</span>
        </a>
        <span>More connected communities start with listening.</span>
        <span className="footer-demo">Forge26 · Prototype</span>
      </footer>

      {showReportForm && (
        <div
          className="modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setShowReportForm(false)
              setFormError('')
            }
          }}
        >
          <section
            className="report-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="report-modal-title"
          >
            <div className="modal-heading">
              <div>
                <span className="section-kicker">YOUR COMMUNITY, YOUR VOICE</span>
                <h2 id="report-modal-title">Report an issue</h2>
                <p>Share enough detail for neighbours to understand the problem.</p>
              </div>
              <button
                type="button"
                className="icon-button modal-close"
                aria-label="Close report form"
                onClick={() => {
                  setShowReportForm(false)
                  setFormError('')
                }}
              >
                <Icon name="close" size={20} />
              </button>
            </div>

            <div className="demo-form-note">
              <Icon name="shield" size={16} />
              Demo mode: this report will not be sent to a real department.
            </div>

            <form className="report-form" onSubmit={submitReport}>
              <label>
                Issue title
                <input
                  value={reportTitle}
                  onChange={(event) => setReportTitle(event.target.value)}
                  placeholder="e.g. Broken streetlight near the park"
                  maxLength={100}
                  required
                />
              </label>

              <label>
                Describe the problem
                <textarea
                  value={reportDescription}
                  onChange={(event) => setReportDescription(event.target.value)}
                  placeholder="What happened, and how does it affect the neighbourhood?"
                  rows={4}
                  maxLength={1000}
                  required
                />
              </label>

              <div className="form-row">
                <label>
                  Category
                  <select
                    value={reportCategory}
                    onChange={(event) => setReportCategory(event.target.value as Category)}
                  >
                    {categories.filter((category): category is Category => category !== 'All issues').map((category) => (
                      <option key={category} value={category}>{category}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Locality
                  <input
                    value={reportLocality}
                    onChange={(event) => setReportLocality(event.target.value)}
                    placeholder="e.g. Lamphel"
                    maxLength={100}
                    required
                  />
                </label>
              </div>

              {formError && <p className="form-error" role="alert">{formError}</p>}

              <div className="modal-actions">
                <button
                  type="button"
                  className="button button-outline"
                  onClick={() => {
                    setShowReportForm(false)
                    setFormError('')
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="button button-primary">
                  Create demo report <Icon name="arrow" size={16} />
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}

export default App
