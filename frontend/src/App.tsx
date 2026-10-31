
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

type Issue = {
  id: number
  title: string
  description: string
  category: Category
  location: string
  status: 'Reported' | 'Under review' | 'In progress' | 'Resolved'
  severity: 'Low' | 'Medium' | 'High' | 'Critical'
  votes: number
  comments: number
  time: string
  demo: boolean
}

const categories: Category[] = [
  'All' as Category,
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
    title: 'Large pothole near the main road',
    description:
      'A deep pothole is making it difficult for two-wheelers and cars to pass safely, especially after rain.',
    category: 'Roads',
    location: 'Lamphel, Imphal',
    status: 'Under review',
    severity: 'High',
    votes: 128,
    comments: 24,
    time: '18 min ago',
    demo: true,
  },
  {
    id: 2,
    title: 'Streetlight not working near the crossing',
    description:
      'The streetlight has been off in the evening, leaving this stretch poorly illuminated.',
    category: 'Streetlights',
    location: 'Singjamei, Imphal',
    status: 'Reported',
    severity: 'Medium',
    votes: 86,
    comments: 12,
    time: '42 min ago',
    demo: true,
  },
  {
    id: 3,
    title: 'Garbage piling up beside the market',
    description:
      'Waste has accumulated beside the market entrance. The collection schedule may need to be reviewed.',
    category: 'Garbage',
    location: 'Thangal Bazar, Imphal',
    status: 'In progress',
    severity: 'Medium',
    votes: 74,
    comments: 18,
    time: '1 hour ago',
    demo: true,
  },
  {
    id: 4,
    title: 'Waterlogging after heavy rainfall',
    description:
      'Rainwater is collecting along the roadside and making the pedestrian path difficult to use.',
    category: 'Drainage',
    location: 'Kakching, Manipur',
    status: 'Reported',
    severity: 'Critical',
    votes: 163,
    comments: 31,
    time: '2 hours ago',
    demo: true,
  },
]

const categoryIcons: Record<Category, string> = {
  Roads: 'road',
  Streetlights: 'light',
  Garbage: 'trash',
  Drainage: 'water',
  Water: 'water',
  'Public safety': 'shield',
  Other: 'pin',
}

function Icon({
  name,
  size = 20,
  className = '',
}: {
  name: string
  size?: number
  className?: string
}) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    className,
    'aria-hidden': true as const,
  }

  switch (name) {
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
        </svg>
      )
    case 'search':
      return (
        <svg {...common}>
          <circle cx="10.8" cy="10.8" r="6.8" />
          <path d="m16 16 5 5" />
        </svg>
      )
    case 'plus':
      return (
        <svg {...common}>
          <path d="M12 5v14M5 12h14" />
        </svg>
      )
    case 'pin':
      return (
        <svg {...common}>
          <path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" />
          <circle cx="12" cy="10" r="2.5" />
        </svg>
      )
    case 'road':
      return (
        <svg {...common}>
          <path d="M8 22 10 2m6 20L14 2M12 5v3m0 4v3m0 4v2" />
        </svg>
      )
    case 'light':
      return (
        <svg {...common}>
          <path d="M9 18h6m-5 4h4m-2-20a7 7 0 0 0-4 12.75c.9.65 1 1.45 1 3.25h6c0-1.8.1-2.6 1-3.25A7 7 0 0 0 12 2Z" />
        </svg>
      )
    case 'trash':
      return (
        <svg {...common}>
          <path d="M3 6h18m-2 0-1 14H6L5 6m4 0V3h6v3m-5 4v6m4-6v6" />
        </svg>
      )
    case 'water':
      return (
        <svg {...common}>
          <path d="M12 2.5S5 10 5 14a7 7 0 0 0 14 0c0-4-7-11.5-7-11.5Z" />
          <path d="M9 15a3 3 0 0 0 3 3" />
        </svg>
      )
    case 'shield':
      return (
        <svg {...common}>
          <path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z" />
          <path d="m9 12 2 2 4-4" />
        </svg>
      )
    case 'arrow':
      return (
        <svg {...common}>
          <path d="M5 12h14m-7-7 7 7-7 7" />
        </svg>
      )
    case 'up':
      return (
        <svg {...common}>
          <path d="m7 14 5-5 5 5" />
        </svg>
      )
    case 'message':
      return (
        <svg {...common}>
          <path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5Z" />
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
        </svg>
      )
    case 'leaf':
      return (
        <svg {...common}>
          <path d="M20 4c-8 0-14 3-14 10a6 6 0 0 0 6 6c7 0 8-8 8-16Z" />
          <path d="M4 21c3-6 7-9 12-12" />
        </svg>
      )
    case 'close':
      return (
        <svg {...common}>
          <path d="m18 6-12 12M6 6l12 12" />
        </svg>
      )
    case 'check':
      return (
        <svg {...common}>
          <path d="m5 12 4 4L19 6" />
        </svg>
      )
    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="9" />
        </svg>
      )
  }
}

function App() {
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    try {
      return localStorage.getItem('civiclens-theme') === 'dark'
        ? 'dark'
        : 'light'
    } catch {
      return 'light'
    }
  })

  const [activeCategory, setActiveCategory] = useState('All')
  const [sort, setSort] = useState('Trending')
  const [search, setSearch] = useState('')
  const [issues, setIssues] = useState(initialIssues)
  const [votedIds, setVotedIds] = useState<number[]>([])
  const [showReport, setShowReport] = useState(false)
  const [reportTitle, setReportTitle] = useState('')
  const [reportDescription, setReportDescription] = useState('')
  const [reportCategory, setReportCategory] = useState<Category>('Roads')
  const [reportLocation, setReportLocation] = useState('')
  const [reportMessage, setReportMessage] = useState('')
  const [showFilters, setShowFilters] = useState(false)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem('civiclens-theme', theme)
    } catch {
      // The theme still works if browser storage is unavailable.
    }
  }, [theme])

  const filteredIssues = useMemo(() => {
    const query = search.trim().toLowerCase()

    const result = issues.filter((issue) => {
      const matchesCategory =
        activeCategory === 'All' || issue.category === activeCategory

      const matchesSearch =
        !query ||
        `${issue.title} ${issue.description} ${issue.location} ${issue.category}`
          .toLowerCase()
          .includes(query)

      return matchesCategory && matchesSearch
    })

    return [...result].sort((a, b) => {
      if (sort === 'Most Supported') return b.votes - a.votes
      if (sort === 'Critical Issues') {
        const rank = { Critical: 4, High: 3, Medium: 2, Low: 1 }
        return rank[b.severity] - rank[a.severity]
      }
      if (sort === 'Latest') return b.id - a.id
      return b.votes + b.comments * 2 - (a.votes + a.comments * 2)
    })
  }, [issues, activeCategory, search, sort])

  function toggleVote(id: number) {
    const alreadyVoted = votedIds.includes(id)

    setVotedIds((previous) =>
      alreadyVoted
        ? previous.filter((votedId) => votedId !== id)
        : [...previous, id],
    )

    setIssues((previous) =>
      previous.map((issue) =>
        issue.id === id
          ? { ...issue, votes: issue.votes + (alreadyVoted ? -1 : 1) }
          : issue,
      ),
    )
  }

 function submitReport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!reportTitle.trim() || !reportDescription.trim() || !reportLocation.trim()) {
      setReportMessage('Please complete all required fields.')
      return
    }

    const newIssue: Issue = {
      id: Math.max(0, ...issues.map((issue) => issue.id)) + 1,
      title: reportTitle.trim(),
      description: reportDescription.trim(),
      category: reportCategory,
      location: reportLocation.trim(),
      status: 'Reported',
      severity: 'Medium',
      votes: 1,
      comments: 0,
      time: 'Just now',
      demo: true,
    }

    setIssues((previous) => [newIssue, ...previous])
    setVotedIds((previous) => [...previous, newIssue.id])
    setActiveCategory('All')
    setSearch('')
    setSort('Latest')
    setReportMessage(
      'Demo report created locally. It has not been sent to a government department.',
    )
    setReportTitle('')
    setReportDescription('')
    setReportLocation('')
  }

  const closeReport = () => {
    setShowReport(false)
    setReportMessage('')
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#home" aria-label="CivicLens AI home">
          <span className="brand-mark">
            <Icon name="leaf" size={25} />
          </span>
          <span className="brand-copy">
            <span className="brand-name">CivicLens<span> AI</span></span>
            <span className="brand-tagline">Your city. Your voice.</span>
          </span>
        </a>

        <div className="topbar-location">
          <Icon name="pin" size={17} />
          <span>Imphal, Manipur</span>
          <span className="demo-dot" title="Demonstration city" />
        </div>

        <div className="topbar-actions">
          <label className="search-box">
            <Icon name="search" size={19} />
            <input
              aria-label="Search civic issues"
              placeholder="Search local issues..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <kbd>/</kbd>
          </label>

          <button
            className="theme-toggle"
            type="button"
            onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
            aria-label={
              theme === 'light'
                ? 'Switch to dark mode'
                : 'Switch to light mode'
            }
            title={theme === 'light' ? 'Dark mode' : 'Light mode'}
          >
            <Icon name={theme === 'light' ? 'moon' : 'sun'} size={20} />
          </button>

          <button
            className="button button-primary header-report"
            type="button"
            onClick={() => setShowReport(true)}
          >
            <Icon name="plus" size={18} />
            <span>Report an issue</span>
          </button>
        </div>
      </header>

      <main id="home" className="page-container">
        <section className="welcome-section">
          <div className="welcome-copy">
            <div className="eyebrow">
              <span className="eyebrow-line" />
              YOUR COMMUNITY, IN FOCUS
            </div>
            <h1>
              Make your city
              <br />
              <span>a better place.</span>
            </h1>
            <p className="welcome-description">
              Spot a problem? Speak up. Support your neighbours.
              Together, let's make local change visible.
            </p>
            <div className="welcome-actions">
              <button
                className="button button-primary"
                type="button"
                onClick={() => setShowReport(true)}
              >
                <Icon name="plus" size={19} />
                Report an issue
                <Icon name="arrow" size={17} />
              </button>
              <a className="text-link" href="#community-feed">
                Explore community <Icon name="arrow" size={16} />
              </a>
            </div>
          </div>

          <div className="welcome-art" aria-label="Civic community illustration">
            <div className="art-orbit orbit-one" />
            <div className="art-orbit orbit-two" />
            <div className="art-sun" />
            <div className="art-hill hill-back" />
            <div className="art-hill hill-front" />
            <div className="art-building building-one">
              <span /><span /><span />
            </div>
            <div className="art-building building-two">
              <span /><span /><span /><span />
            </div>
            <div className="art-tree tree-one"><i /><b /></div>
            <div className="art-tree tree-two"><i /><b /></div>
            <div className="art-path" />
            <div className="art-label">
              <span className="art-label-icon"><Icon name="leaf" size={19} /></span>
              <span>
                <strong>Small actions.</strong>
                <small>Stronger communities.</small>
              </span>
            </div>
          </div>
        </section>

        <section className="community-strip" aria-label="Community principles">
          <div className="strip-item">
            <span className="strip-icon"><Icon name="search" size={18} /></span>
            <span><strong>Spot an issue</strong><small>Make problems visible</small></span>
          </div>
          <span className="strip-divider" />
          <div className="strip-item">
            <span className="strip-icon"><Icon name="up" size={19} /></span>
            <span><strong>Raise your voice</strong><small>Support local reports</small></span>
          </div>
          <span className="strip-divider" />
          <div className="strip-item">
            <span className="strip-icon"><Icon name="check" size={19} /></span>
            <span><strong>Track progress</strong><small>Follow every update</small></span>
          </div>
        </section>

        <section id="community-feed" className="feed-section">
          <div className="feed-heading">
            <div>
              <div className="eyebrow"><span className="eyebrow-line" /> THE NEIGHBOURHOOD FEED</div>
              <h2>What's happening <span>around you?</span></h2>
              <p className="feed-subtitle">
                Local issues, community voices, and updates in one place.
              </p>
            </div>
            <span className="demo-label"><span /> DEMO DATA</span>
          </div>

          <div className="feed-toolbar">
            <div className="category-list" aria-label="Filter by category">
              {categories.map((category) => (
                <button
                  key={category}
                  className={`category-chip ${activeCategory === category ? 'active' : ''}`}
                  type="button"
                  onClick={() => setActiveCategory(category)}
                >
                  {String(category) === 'All' ? (
                    <Icon name="filter" size={15} />
                  ) : (
                    <Icon name={categoryIcons[category]} size={15} />
                  )}
                  {category}
                </button>
              ))}
            </div>

            <div className="feed-controls">
              <button
                className={`filter-button ${showFilters ? 'selected' : ''}`}
                type="button"
                onClick={() => setShowFilters((value) => !value)}
                aria-expanded={showFilters}
              >
                <Icon name="filter" size={17} />
                Filters
              </button>
              <label className="sort-select">
                <span>Sort:</span>
                <select value={sort} onChange={(event) => setSort(event.target.value)}>
                  <option>Trending</option>
                  <option>Latest</option>
                  <option>Most Supported</option>
                  <option>Critical Issues</option>
                </select>
              </label>
            </div>
          </div>

          {showFilters && (
            <div className="filter-panel">
              <div>
                <strong>Community feed filters</strong>
                <p>Choose a category above or sort issues by support, recency, or severity.</p>
              </div>
              <button
                type="button"
                className="text-link"
                onClick={() => {
                  setActiveCategory('All')
                  setSearch('')
                  setSort('Trending')
                }}
              >
                Reset filters
              </button>
            </div>
          )}

          <div className="feed-layout">
            <div className="issue-list">
              <div className="results-line">
                <span><strong>{filteredIssues.length}</strong> community reports</span>
                <span>All times illustrative</span>
              </div>

              {filteredIssues.length === 0 ? (
                <div className="empty-state">
                  <span className="empty-icon"><Icon name="search" size={27} /></span>
                  <h3>No issues found</h3>
                  <p>Try another search or select a different category.</p>
                  <button
                    className="button button-secondary"
                    type="button"
                    onClick={() => {
                      setSearch('')
                      setActiveCategory('All')
                    }}
                  >
                    Clear search and filters
                  </button>
                </div>
              ) : (
                filteredIssues.map((issue) => {
                  const voted = votedIds.includes(issue.id)

                  return (
                    <article className="issue-card" key={issue.id}>
                      <button
                        type="button"
                        className={`vote-control ${voted ? 'voted' : ''}`}
                        onClick={() => toggleVote(issue.id)}
                        aria-label={`${voted ? 'Remove support from' : 'Support'} ${issue.title}`}
                        aria-pressed={voted}
                        title={voted ? 'Remove your support' : 'Support this report'}
                      >
                        <Icon name="up" size={21} />
                        <strong>{issue.votes}</strong>
                        <span>{voted ? 'Supported' : 'Support'}</span>
                      </button>

                      <div className="issue-content">
                        <div className="issue-topline">
                          <span className={`category-label category-${issue.category.toLowerCase().replaceAll(' ', '-')}`}>
                            <Icon name={categoryIcons[issue.category]} size={14} />
                            {issue.category}
                          </span>
                          <span className={`severity severity-${issue.severity.toLowerCase()}`}>
                            <span /> {issue.severity} severity
                          </span>
                        </div>

                        <h3>{issue.title}</h3>
                        <p className="issue-description">{issue.description}</p>

                        <div className="issue-location">
                          <Icon name="pin" size={15} />
                          {issue.location}
                        </div>

                        <div className="issue-footer">
                          <span className={`status status-${issue.status.toLowerCase().replaceAll(' ', '-')}`}>
                            <span /> {issue.status}
                          </span>
                          <span className="issue-time"><Icon name="clock" size={14} /> {issue.time}</span>
                          <span className="issue-comments"><Icon name="message" size={15} /> {issue.comments} comments</span>
                        </div>
                      </div>
                    </article>
                  )
                })
              )}
            </div>

            <aside className="sidebar">
              <section className="sidebar-card city-card">
                <div className="sidebar-card-heading">
                  <span className="sidebar-icon"><Icon name="pin" size={19} /></span>
                  <span className="sidebar-kicker">YOUR COMMUNITY</span>
                </div>
                <h3>Imphal, Manipur</h3>
                <p>Local reports for a more liveable city.</p>
                <div className="city-card-footer">
                  <span className="location-status"><span /> Demo community</span>
                  <Icon name="leaf" size={20} />
                </div>
              </section>

              <section className="sidebar-card">
                <div className="sidebar-card-heading">
                  <span className="sidebar-icon"><Icon name="up" size={19} /></span>
                  <span className="sidebar-kicker">COMMUNITY IMPACT</span>
                </div>
                <h3>Every voice counts.</h3>
                <p>Supporting a report helps neighbours see which issues matter to the community.</p>
                <div className="impact-note">
                  <Icon name="leaf" size={17} />
                  <span>Support is not the same as safety severity.</span>
                </div>
              </section>

              <section className="sidebar-card report-cta">
                <span className="cta-decoration"><Icon name="leaf" size={28} /></span>
                <span className="sidebar-kicker">SEE SOMETHING?</span>
                <h3>Be the start of change.</h3>
                <p>Share a local issue with your community.</p>
                <button
                  className="button button-primary cta-button"
                  type="button"
                  onClick={() => setShowReport(true)}
                >
                  Create a report <Icon name="arrow" size={16} />
                </button>
              </section>

              <p className="prototype-note">
                <Icon name="shield" size={15} />
                Prototype preview. Reports and votes are stored only in this page's temporary state.
              </p>
            </aside>
          </div>
        </section>

        <footer className="footer">
          <a className="footer-brand" href="#home">
            <Icon name="leaf" size={19} />
            CivicLens AI
          </a>
          <span>Built for stronger communities.</span>
          <span className="footer-status"><span /> Prototype · Demo data</span>
        </footer>
      </main>

      {showReport && (
        <div className="modal-backdrop" onMouseDown={(event) => {
          if (event.target === event.currentTarget) closeReport()
        }}>
          <section className="report-modal" role="dialog" aria-modal="true" aria-labelledby="report-title">
            <div className="modal-heading">
              <div>
                <span className="eyebrow"><span className="eyebrow-line" /> COMMUNITY REPORT</span>
                <h2 id="report-title">Report an issue</h2>
                <p>Help your neighbours understand what's happening.</p>
              </div>
              <button type="button" className="modal-close" onClick={closeReport} aria-label="Close report form">
                <Icon name="close" size={21} />
              </button>
            </div>

            <form className="report-form" onSubmit={submitReport}>
              <label>
                Issue title <span>*</span>
                <input
                  value={reportTitle}
                  onChange={(event) => setReportTitle(event.target.value)}
                  placeholder="e.g. Broken streetlight near the crossing"
                  maxLength={100}
                  required
                />
              </label>

              <label>
                Description <span>*</span>
                <textarea
                  value={reportDescription}
                  onChange={(event) => setReportDescription(event.target.value)}
                  placeholder="Describe the issue and how it affects your community..."
                  rows={4}
                  maxLength={1000}
                  required
                />
              </label>

              <div className="form-row">
                <label>
                  Category <span>*</span>
                  <select
                    value={reportCategory}
                    onChange={(event) => setReportCategory(event.target.value as Category)}
                  >
                    {categories.filter((category) => String(category) !== 'All').map((category) => (
  <option key={category}>{category}</option>
))}
                  </select>
                </label>

                <label>
                  Locality <span>*</span>
                  <input
                    value={reportLocation}
                    onChange={(event) => setReportLocation(event.target.value)}
                    placeholder="e.g. Lamphel, Imphal"
                    maxLength={120}
                    required
                  />
                </label>
              </div>

              <div className="demo-disclaimer">
                <Icon name="shield" size={18} />
                <p><strong>Prototype notice</strong>This creates a local demo report only. It will not be sent to a municipality or government department.</p>
              </div>

              {reportMessage && (
                <p className="form-message" role="status">{reportMessage}</p>
              )}

              <div className="modal-actions">
                <button className="button button-secondary" type="button" onClick={closeReport}>
                  Close
                </button>
                <button className="button button-primary" type="submit">
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
