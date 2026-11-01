import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from 'react'
import './App.css'
import {
  api, ApiError, clearAccessToken, getAccessToken, setAccessToken,
  type AnalysisView, type CityView, type CommentView, type DashboardSummary,
  type DepartmentPerformance, type DepartmentView, type DuplicateCandidate,
  type IncidentCategory, type IncidentStatus, type IncidentView, type RewardSummary,
  type TimelineEntry, type UserView, type Severity,
} from './api/client'
import { reverseWorldwideLocation, searchWorldwideLocations, type LocationSuggestion } from './api/location'

type Theme = 'light' | 'dark'
type View = 'community' | 'report' | 'issue' | 'admin' | 'leaderboard' | 'map'
type AccountRole = 'public' | 'organisation'
type AuthStage = 'choose' | 'login' | 'register' | 'app'
type Attachment = { id: string; file: File }
type DisplayStatus = 'Reported' | 'Under review' | 'Assigned' | 'In Progress' | 'Resolved' | 'Rejected' | 'Reopened'

type Issue = {
  id: string
  reference: string
  title: string
  description: string
  category: string
  categoryCode: IncidentCategory
  locality: string
  cityName: string
  createdAt: string
  votes: number
  commentCount: number
  status: DisplayStatus
  apiStatus: IncidentStatus
  analysisStatus: string | null
  availableActions: string[]
  attachments: Attachment[]
  coordinates: { latitude: number; longitude: number } | null
  myVote: 'UP' | 'DOWN' | null
  reportedByMe: boolean
  demoData: boolean
  severity: string | null
  safetyCritical: boolean
  departmentName: string | null
  departmentId: string | null
  resolutionNote: string | null
}

const categoryOptions: { label: string; value: IncidentCategory; hazard?: boolean }[] = [
  { label: 'Roads & Potholes', value: 'ROAD_DAMAGE' },
  { label: 'Sanitation / Waste Management', value: 'GARBAGE' },
  { label: 'Water Supply / Leakage', value: 'WATER_LEAKAGE' },
  { label: 'Streetlights', value: 'STREETLIGHT' },
  { label: 'Electrical Hazard', value: 'ELECTRICAL_HAZARD', hazard: true },
  { label: 'Drainage', value: 'DRAINAGE' },
  { label: 'Flooding & Waterlogging', value: 'FLOODING' },
  { label: 'Sewage', value: 'SEWAGE' },
  { label: 'Fallen Trees', value: 'TREE_FALL' },
  { label: 'Traffic Signals', value: 'TRAFFIC_SIGNAL' },
  { label: 'Public Property Damage', value: 'PUBLIC_PROPERTY_DAMAGE' },
  { label: 'Public Safety / Other', value: 'OTHER', hazard: true },
]

const categories = categoryOptions.map((item) => item.label)
const categoryLabels = new Map(categoryOptions.map((item) => [item.value, item.label]))
const categoryValues = new Map(categoryOptions.map((item) => [item.label, item.value]))

const displayStatus: Record<IncidentStatus, DisplayStatus> = {
  SUBMITTED: 'Reported',
  UNDER_REVIEW: 'Under review',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  REJECTED: 'Rejected',
  REOPENED: 'Reopened',
}

const statusFilters: Record<string, IncidentStatus[]> = {
  Reported: ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'REOPENED'],
  'In Progress': ['IN_PROGRESS'],
  Resolved: ['RESOLVED'],
}

function mapIncident(item: IncidentView): Issue {
  const locality = [item.locality, item.cityName, item.addressText]
    .filter(Boolean)
    .filter((v, i, a) => a.indexOf(v) === i)
    .join(', ')

  return {
    id: item.id,
    reference: `CL-${item.id.slice(0, 8).toUpperCase()}`,
    title: item.title,
    description: item.description,
    category: categoryLabels.get(item.category) || item.category.replaceAll('_', ' '),
    categoryCode: item.category,
    locality: locality || item.cityName,
    cityName: item.cityName,
    createdAt: new Date(item.createdAt).toLocaleString(),
    votes: item.supportCount,
    commentCount: item.commentCount,
    status: displayStatus[item.status],
    apiStatus: item.status,
    analysisStatus: item.analysisStatus,
    availableActions: item.availableActions || [],
    attachments: [],
    coordinates:
      item.latitude != null && item.longitude != null
        ? { latitude: item.latitude, longitude: item.longitude }
        : null,
    myVote: item.myVote,
    reportedByMe: item.reportedByMe,
    demoData: item.demoData,
    severity: item.severity,
    safetyCritical: item.safetyCritical,
    departmentName: item.departmentName,
    departmentId: item.departmentId,
    resolutionNote: item.resolutionNote,
  }
}

function toAccountRole(user: UserView): AccountRole {
  return user.role === 'CITIZEN' ? 'public' : 'organisation'
}

function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true as const,
  }

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

function App() {
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem('civiclens-theme') as Theme) || 'light')
  const [view, setView] = useState<View>('community')
  const [selectedAccountRole, setSelectedAccountRole] = useState<AccountRole | null>(null)
  const [authStage, setAuthStage] = useState<AuthStage>('choose')
  const [authLoading, setAuthLoading] = useState(Boolean(getAccessToken()))
  const [authBusy, setAuthBusy] = useState(false)
  const [currentUser, setCurrentUser] = useState<UserView | null>(null)
  const [loginEmail, setLoginEmail] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [registerName, setRegisterName] = useState('')
  const [loginMessage, setLoginMessage] = useState('')
  const [issues, setIssues] = useState<Issue[]>([])
  const [feedLoading, setFeedLoading] = useState(false)
  const [feedMessage, setFeedMessage] = useState('')
  const [selectedIssueId, setSelectedIssueId] = useState('')
  const [searchText, setSearchText] = useState('')
  const [reportTitle, setReportTitle] = useState('')
  const [reportDescription, setReportDescription] = useState('')
  const [reportCategory, setReportCategory] = useState(categories[0])
  const [reportLocality, setReportLocality] = useState('')
  const [reportCoordinates, setReportCoordinates] = useState<{ latitude: number; longitude: number } | null>(null)
  const [reportHazard, setReportHazard] = useState(false)
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [locationSuggestions, setLocationSuggestions] = useState<LocationSuggestion[]>([])
  const [selectedLocation, setSelectedLocation] = useState<LocationSuggestion | null>(null)
  const [resolvedCity, setResolvedCity] = useState<CityView | null>(null)
  const [activeCity, setActiveCity] = useState<CityView | null>(null)
  const [locationLoading, setLocationLoading] = useState(false)
  const [locationMessage, setLocationMessage] = useState('')
  const [gpsLoading, setGpsLoading] = useState(false)
  const [formMessage, setFormMessage] = useState('')
  const [duplicateCandidates, setDuplicateCandidates] = useState<DuplicateCandidate[]>([])
  const [duplicateBusy, setDuplicateBusy] = useState(false)
  const [sort, setSort] = useState<'Hot' | 'New' | 'Top'>('Hot')
  const [filter, setFilter] = useState('All')
  const [commentText, setCommentText] = useState('')
  const [comments, setComments] = useState<CommentView[]>([])
  const [timeline, setTimeline] = useState<TimelineEntry[]>([])
  const [analysis, setAnalysis] = useState<AnalysisView | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailMessage, setDetailMessage] = useState('')
  const [dashboard, setDashboard] = useState<DashboardSummary | null>(null)
  const [triageIssues, setTriageIssues] = useState<Issue[]>([])
  const [departmentPerformance, setDepartmentPerformance] = useState<DepartmentPerformance[]>([])
  const [departments, setDepartments] = useState<DepartmentView[]>([])
  const [adminCityId, setAdminCityId] = useState('')
  const [adminSelectedCity, setAdminSelectedCity] = useState<CityView | null>(null)
  const [adminLocationQuery, setAdminLocationQuery] = useState('')
  const [adminLocationSuggestions, setAdminLocationSuggestions] = useState<LocationSuggestion[]>([])
  const [adminLocationLoading, setAdminLocationLoading] = useState(false)
  const [adminLocationMessage, setAdminLocationMessage] = useState('')
  const [adminMessage, setAdminMessage] = useState('')
  const [triageCategory, setTriageCategory] = useState<IncidentCategory>('OTHER')
  const [triageSeverity, setTriageSeverity] = useState<Severity>('MEDIUM')
  const [triageSafety, setTriageSafety] = useState(false)
  const [selectedDepartmentId, setSelectedDepartmentId] = useState('')
  const [statusNote, setStatusNote] = useState('')
  const [statusSelection, setStatusSelection] = useState<IncidentStatus | ''>('')
  const [officialName, setOfficialName] = useState('')
  const [officialEmail, setOfficialEmail] = useState('')
  const [officialPassword, setOfficialPassword] = useState('')
  const [officialDepartmentId, setOfficialDepartmentId] = useState('')
  const [officialProvisionMessage, setOfficialProvisionMessage] = useState('')
  const [officialProvisionBusy, setOfficialProvisionBusy] = useState(false)
  const [rewardSummary, setRewardSummary] = useState<RewardSummary | null>(null)
  const [rewardMessage, setRewardMessage] = useState('')
  const [busyAction, setBusyAction] = useState(false)

  const accountRole = currentUser ? toAccountRole(currentUser) : selectedAccountRole
  const isOfficial = currentUser?.role === 'DEPARTMENT_ADMIN' || currentUser?.role === 'SYSTEM_ADMIN'
  const selectedIssue = issues.find((issue) => issue.id === selectedIssueId) || triageIssues.find((issue) => issue.id === selectedIssueId)
  const filteredIssues = issues

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('civiclens-theme', theme)
  }, [theme])

  useEffect(() => {
    const token = getAccessToken()
    if (!token) return
    let active = true
    api.me().then((user) => {
      if (!active) return
      setCurrentUser(user)
      setAuthStage('app')
      setSelectedAccountRole(toAccountRole(user))
      setView(user.role === 'CITIZEN' ? 'community' : 'admin')
      if (user.homeCityId) {
        api.city(user.homeCityId).then(setActiveCity).catch(() => undefined)
      }
    }).catch(() => {
      clearAccessToken()
    }).finally(() => {
      if (active) setAuthLoading(false)
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (authStage !== 'app' || !currentUser) return
    let cancelled = false
    const params = new URLSearchParams({
      page: '0',
      size: '50',
      sort: sort === 'New' ? 'NEWEST' : sort === 'Top' ? 'MOST_SUPPORTED' : 'TRENDING',
    })
    if (activeCity?.id) params.set('cityId', activeCity.id)
    if (searchText.trim()) params.set('q', searchText.trim())
    if (filter !== 'All') (statusFilters[filter] || []).forEach((status) => params.append('status', status))

    Promise.resolve().then(() => {
      if (!cancelled) {
        setFeedLoading(true)
        setFeedMessage('')
      }
      return api.feed(params)
    }).then((page) => {
      if (!cancelled) {
        const mapped = page.items.map(mapIncident)
        setIssues(mapped)
        if (selectedIssueId && !mapped.some((item) => item.id === selectedIssueId)) {
          setSelectedIssueId('')
        }
      }
    }).catch((error: unknown) => {
      if (!cancelled) setFeedMessage(error instanceof Error ? error.message : 'Could not load the community feed.')
    }).finally(() => {
      if (!cancelled) setFeedLoading(false)
    })

    return () => { cancelled = true }
  }, [authStage, currentUser, activeCity?.id, searchText, sort, filter, selectedIssueId])

  useEffect(() => {
    if (view !== 'issue' || !selectedIssueId) return
    let cancelled = false

    const load = async () => {
      setDetailLoading(true)
      setDetailMessage('')

      const [incidentResult, commentsResult, historyResult, analysisResult] = await Promise.allSettled([
        api.incident(selectedIssueId),
        api.comments(selectedIssueId),
        api.history(selectedIssueId),
        api.analysis(selectedIssueId),
      ])

      if (cancelled) return

      if (incidentResult.status === 'fulfilled') {
        const mapped = mapIncident(incidentResult.value)
        setTriageCategory(incidentResult.value.category)
        setTriageSeverity(incidentResult.value.severity || 'MEDIUM')
        setTriageSafety(incidentResult.value.safetyCritical)
        setIssues((current) => [mapped, ...current.filter((item) => item.id !== mapped.id)])
        setTriageIssues((current) => [mapped, ...current.filter((item) => item.id !== mapped.id)])

        if (isOfficial) {
          api.departments(incidentResult.value.cityId).then((items) => {
            if (!cancelled) setDepartments(items)
          }).catch(() => undefined)
          setSelectedDepartmentId(incidentResult.value.departmentId || incidentResult.value.recommendedDepartmentId || '')
        }
      } else {
        setDetailMessage(incidentResult.reason instanceof Error ? incidentResult.reason.message : 'Could not load incident details.')
      }

      if (commentsResult.status === 'fulfilled') setComments(commentsResult.value.items)
      if (historyResult.status === 'fulfilled') setTimeline(historyResult.value)
      if (analysisResult.status === 'fulfilled') setAnalysis(analysisResult.value)
      else if (incidentResult.status === 'fulfilled') setAnalysis(incidentResult.value.analysis || null)
      else setAnalysis(null)

      setDetailLoading(false)
    }

    void load()
    return () => { cancelled = true }
  }, [view, selectedIssueId, isOfficial])

  useEffect(() => {
    const analysisIsPending = analysis
      ? ['PENDING', 'PROCESSING'].includes(analysis.status)
      : ['PENDING', 'PROCESSING'].includes(selectedIssue?.analysisStatus || '')

    if (view !== 'issue' || !selectedIssueId || !analysisIsPending) return

    const timer = window.setInterval(() => {
      api.analysis(selectedIssueId).then(setAnalysis).catch(() => undefined)
    }, 5000)

    return () => window.clearInterval(timer)
  }, [view, selectedIssueId, analysis, selectedIssue?.analysisStatus])

  useEffect(() => {
    if (view !== 'admin' || !currentUser || !isOfficial) return
    let cancelled = false

    const load = async () => {
      setAdminMessage('')

      try {
        let cityId: string | undefined =
          currentUser.role === 'SYSTEM_ADMIN' ? (adminCityId || undefined) : undefined

        const departmentId =
          currentUser.role === 'DEPARTMENT_ADMIN' ? currentUser.departmentId || undefined : undefined

        if (currentUser.departmentId) {
          const dept = await api.department(currentUser.departmentId)
          cityId = dept.cityId
          const list = await api.departments(dept.cityId)

          if (!cancelled) {
            setDepartments(list)
            setOfficialDepartmentId((current) => current || list[0]?.id || '')
          }
        } else if (currentUser.role === 'SYSTEM_ADMIN' && cityId) {
          const list = await api.departments(cityId)

          if (!cancelled) {
            setDepartments(list)
            setOfficialDepartmentId(list[0]?.id || '')
          }
        } else if (currentUser.role === 'SYSTEM_ADMIN') {
          if (!cancelled) {
            setDepartments([])
            setOfficialDepartmentId('')
          }
        }

        const summaryParams = new URLSearchParams()
        const queueParams = new URLSearchParams({ page: '0', size: '50' })

        if (cityId) {
          summaryParams.set('cityId', cityId)
          queueParams.set('cityId', cityId)
        }

        if (departmentId) {
          summaryParams.set('departmentId', departmentId)
          queueParams.set('departmentId', departmentId)
        }

        const [summary, queue, performance] = await Promise.all([
          api.dashboardSummary(summaryParams),
          api.triageQueue(queueParams),
          api.departmentPerformance(cityId),
        ])

        if (!cancelled) {
          setDashboard(summary)
          setTriageIssues(queue.items.map(mapIncident))
          setDepartmentPerformance(performance)
        }
      } catch (error) {
        if (!cancelled) {
          setAdminMessage(error instanceof Error ? error.message : 'Could not load the department dashboard.')
        }
      }
    }

    void load()
    return () => { cancelled = true }
  }, [view, currentUser, isOfficial, adminCityId])

  useEffect(() => {
    if (view !== 'leaderboard' || !currentUser) return
    api.rewards().then(setRewardSummary).catch((error: unknown) => setRewardMessage(error instanceof Error ? error.message : 'Could not load rewards.'))
  }, [view, currentUser])

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

  async function handleAuthSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedAccountRole) return

    setAuthBusy(true)
    setLoginMessage('')

    try {
      const response = await api.login(loginEmail.trim(), loginPassword)
      const isCitizen = response.user.role === 'CITIZEN'

      if ((selectedAccountRole === 'public') !== isCitizen) {
        setLoginMessage(selectedAccountRole === 'organisation'
          ? 'This is a citizen account. Sign in using Public / Citizen access.'
          : 'This is an official account. Sign in using Organisation / Government access.')
        return
      }

      setAccessToken(response.accessToken)
      setCurrentUser(response.user)

      if (response.user.homeCityId) {
        api.city(response.user.homeCityId).then(setActiveCity).catch(() => undefined)
      }

      setView(isCitizen ? 'community' : 'admin')
      setAuthStage('app')
    } catch (error) {
      setLoginMessage(error instanceof Error ? error.message : 'Login failed. Check your credentials.')
    } finally {
      setAuthBusy(false)
    }
  }

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAuthBusy(true)
    setLoginMessage('')

    try {
      const response = await api.register(
        registerName.trim(),
        loginEmail.trim(),
        loginPassword,
        activeCity?.id || null,
      )

      setAccessToken(response.accessToken)
      setCurrentUser(response.user)

      if (response.user.homeCityId) {
        api.city(response.user.homeCityId).then(setActiveCity).catch(() => undefined)
      }

      setSelectedAccountRole('public')
      setView('community')
      setAuthStage('app')
    } catch (error) {
      setLoginMessage(error instanceof Error ? error.message : 'Registration failed.')
    } finally {
      setAuthBusy(false)
    }
  }

  async function signOut() {
    try {
      if (getAccessToken()) await api.logout()
    } catch {
      // The token is cleared locally even if logout cannot reach the API.
    }

    clearAccessToken()
    setCurrentUser(null)
    setSelectedAccountRole(null)
    setAuthStage('choose')
    setLoginEmail('')
    setLoginPassword('')
    setView('community')
    setIssues([])
  }

  async function searchTypedLocation() {
    const query = reportLocality.trim()

    if (query.length < 3) {
      setLocationMessage('Enter at least 3 characters, then search worldwide locations.')
      return
    }

    setLocationLoading(true)
    setLocationMessage('Searching worldwide locations…')
    setLocationSuggestions([])
    setSelectedLocation(null)
    setResolvedCity(null)

    try {
      const results = await searchWorldwideLocations(query)
      setLocationSuggestions(results)
      setLocationMessage(
        results.length
          ? 'Select the correct city or settlement from the results.'
          : 'No settlement found. Try a nearby city or use GPS.',
      )
    } catch (error) {
      setLocationMessage(error instanceof Error ? error.message : 'Location search is temporarily unavailable.')
    } finally {
      setLocationLoading(false)
    }
  }

  async function chooseLocation(suggestion: LocationSuggestion) {
    setSelectedLocation(suggestion)
    setResolvedCity(null)
    setReportLocality(suggestion.locality)
    setReportCoordinates({ latitude: suggestion.lat, longitude: suggestion.lon })
    setLocationSuggestions([])
    setLocationLoading(true)
    setLocationMessage(`Registering ${suggestion.cityName}, ${suggestion.country}…`)

    try {
      const city = await api.resolveCity({
        cityName: suggestion.cityName,
        state: suggestion.state || null,
        country: suggestion.country,
        countryCode: suggestion.countryCode,
        centerLatitude: suggestion.lat,
        centerLongitude: suggestion.lon,
      })

      setResolvedCity(city)
      setActiveCity(city)
      setLocationMessage(
        `${city.name}, ${city.country} is ready. ${city.departmentCount} internal CivicLens triage queues are configured.`,
      )
    } catch (error) {
      setSelectedLocation(null)
      setResolvedCity(null)
      setLocationMessage(error instanceof Error ? error.message : 'Could not register this location.')
    } finally {
      setLocationLoading(false)
    }
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      setLocationMessage('Location is not supported by this browser. Search for a city or settlement.')
      return
    }

    setGpsLoading(true)
    setLocationMessage('Please allow location access if your browser asks.')

    navigator.geolocation.getCurrentPosition(async (position) => {
      const { latitude, longitude } = position.coords
      setReportCoordinates({ latitude, longitude })

      try {
        const location = await reverseWorldwideLocation(latitude, longitude)
        await chooseLocation({ ...location, lat: latitude, lon: longitude })
      } catch (error) {
        setLocationMessage(error instanceof Error ? error.message : 'Could not resolve the current location. Search manually.')
      } finally {
        setGpsLoading(false)
      }
    }, (error) => {
      setGpsLoading(false)
      if (error.code === error.PERMISSION_DENIED) setLocationMessage('Permission denied. Search for a location manually.')
      else if (error.code === error.TIMEOUT) setLocationMessage('Location request timed out. Try again.')
      else setLocationMessage('Unable to detect location. Search for a location manually.')
    }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 })
  }

  async function submitReport(event?: FormEvent<HTMLFormElement>, confirmed = false) {
    event?.preventDefault()

    if (!currentUser || currentUser.role !== 'CITIZEN') {
      setFormMessage('Sign in with a citizen account to submit a report.')
      return
    }

    if (reportTitle.trim().length < 5 || reportDescription.trim().length < 10) {
      setFormMessage('Title must be at least 5 characters and description at least 10 characters.')
      return
    }

    if (!selectedLocation || !resolvedCity || !reportCoordinates) {
      setFormMessage('Search and select a worldwide location. The location must be resolved by CivicLens before submission.')
      return
    }

    setDuplicateBusy(true)
    setFormMessage(confirmed ? 'Submitting your reviewed report…' : 'Checking for similar reports…')

    const category = categoryValues.get(reportCategory) || 'OTHER'
    const candidateIds = duplicateCandidates
      .filter((candidate) => candidate.strongMatch)
      .map((candidate) => candidate.incidentId)

    try {
      if (!confirmed) {
        const duplicateResult = await api.duplicateCheck({
          cityId: resolvedCity.id,
          title: reportTitle.trim(),
          description: reportDescription.trim(),
          locality: reportLocality.trim(),
          latitude: reportCoordinates.latitude,
          longitude: reportCoordinates.longitude,
          category,
        })

        const strongCandidates = duplicateResult.candidates.filter((candidate) => candidate.strongMatch)

        if (strongCandidates.length) {
          setDuplicateCandidates(strongCandidates)
          setFormMessage('We found similar reports in this location. Review them before continuing.')
          return
        }
      }

      const created = await api.createIncident({
        cityId: resolvedCity.id,
        title: reportTitle.trim(),
        description: reportDescription.trim(),
        locality: reportLocality.trim(),
        addressText: selectedLocation.label,
        latitude: reportCoordinates.latitude,
        longitude: reportCoordinates.longitude,
        category,
        hazardReported: reportHazard || categoryOptions.find((item) => item.label === reportCategory)?.hazard === true,
        duplicateCheck: {
          reviewedCandidateIds: confirmed ? candidateIds : [],
          confirmedNotDuplicate: true,
        },
      })

      const mapped = mapIncident(created)
      setIssues((current) => [mapped, ...current.filter((item) => item.id !== mapped.id)])
      setSelectedIssueId(mapped.id)
      setReportTitle('')
      setReportDescription('')
      setReportCategory(categories[0])
      setReportLocality('')
      setReportCoordinates(null)
      setReportHazard(false)
      setSelectedLocation(null)
      setResolvedCity(null)
      setAttachments([])
      setLocationSuggestions([])
      setDuplicateCandidates([])
      setFormMessage('')
      setView('issue')
    } catch (error) {
      if (error instanceof ApiError && error.code === 'POSSIBLE_DUPLICATE' && error.data && typeof error.data === 'object') {
        const data = error.data as { candidates?: DuplicateCandidate[] }
        setDuplicateCandidates((data.candidates || []).filter((candidate) => candidate.strongMatch))
        setFormMessage('A similar report was found during submission. Review it before continuing.')
      } else {
        setFormMessage(error instanceof Error ? error.message : 'Report submission failed.')
      }
    } finally {
      setDuplicateBusy(false)
    }
  }

  async function toggleVote(issue: Issue) {
    if (!currentUser) {
      setFeedMessage('Sign in to support a report.')
      return
    }
    if (issue.reportedByMe) {
      setFeedMessage('You cannot vote on your own report.')
      return
    }

    setBusyAction(true)
    try {
      if (issue.myVote === 'UP') await api.removeVote(issue.id)
      else await api.vote(issue.id, 'UP')

      const updated = mapIncident(await api.incident(issue.id))
      setIssues((current) => current.map((item) => item.id === issue.id ? updated : item))
      setTriageIssues((current) => current.map((item) => item.id === issue.id ? updated : item))
    } catch (error) {
      setFeedMessage(error instanceof Error ? error.message : 'Could not update your vote.')
    } finally {
      setBusyAction(false)
    }
  }

  async function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedIssue || !commentText.trim()) return
    if (!currentUser) {
      setDetailMessage('Sign in to add a comment.')
      return
    }

    setBusyAction(true)
    try {
      const created = await api.addComment(selectedIssue.id, commentText.trim())
      setComments((current) => [...current, created])
      setCommentText('')
      const updated = mapIncident(await api.incident(selectedIssue.id))
      setIssues((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (error) {
      setDetailMessage(error instanceof Error ? error.message : 'Could not post your comment.')
    } finally {
      setBusyAction(false)
    }
  }

  async function updateStatus(status: IncidentStatus) {
    if (!selectedIssue) return

    if (['RESOLVED', 'REJECTED', 'REOPENED'].includes(status) && statusNote.trim().length < 5) {
      setDetailMessage(`Add an audit note of at least 5 characters before changing the status to ${displayStatus[status]}.`)
      return
    }

    setBusyAction(true)
    try {
      const updated = mapIncident(await api.changeStatus(selectedIssue.id, status, statusNote.trim()))
      setIssues((current) => [updated, ...current.filter((item) => item.id !== updated.id)])
      setTriageIssues((current) => current.map((item) => item.id === updated.id ? updated : item))
      setStatusNote('')
      setStatusSelection('')
      setDetailMessage('Incident status updated.')
      setTimeline(await api.history(selectedIssue.id))
    } catch (error) {
      setDetailMessage(error instanceof Error ? error.message : 'Status update failed.')
    } finally {
      setBusyAction(false)
    }
  }

  async function assignDepartment() {
    if (!selectedIssue || !selectedDepartmentId) return

    setBusyAction(true)
    try {
      const updated = mapIncident(await api.assign(selectedIssue.id, selectedDepartmentId, statusNote.trim()))
      setIssues((current) => current.map((item) => item.id === updated.id ? updated : item))
      setTriageIssues((current) => current.map((item) => item.id === updated.id ? updated : item))
      setDetailMessage('Incident assigned to the selected internal triage queue.')
      setStatusNote('')
      setTimeline(await api.history(selectedIssue.id))
    } catch (error) {
      setDetailMessage(error instanceof Error ? error.message : 'Assignment failed.')
    } finally {
      setBusyAction(false)
    }
  }

  async function saveTriage() {
    if (!selectedIssue) return

    setBusyAction(true)
    try {
      const updated = mapIncident(await api.triage(selectedIssue.id, {
        category: triageCategory,
        severity: triageSeverity,
        safetyCritical: triageSafety,
        note: statusNote.trim(),
      }))
      setIssues((current) => current.map((item) => item.id === updated.id ? updated : item))
      setTriageIssues((current) => current.map((item) => item.id === updated.id ? updated : item))
      setDetailMessage('Incident triage details verified and saved.')
      setTimeline(await api.history(selectedIssue.id))
      setStatusNote('')
    } catch (error) {
      setDetailMessage(error instanceof Error ? error.message : 'Could not save triage details.')
    } finally {
      setBusyAction(false)
    }
  }

  // ---------------- DASHBOARD GLOBAL LOCATION SEARCH ----------------

  async function searchDashboardLocation() {
    const query = adminLocationQuery.trim()

    if (query.length < 3) {
      setAdminLocationMessage('Enter at least 3 characters, then search worldwide locations.')
      return
    }

    setAdminLocationLoading(true)
    setAdminLocationMessage('Searching worldwide locations…')
    setAdminLocationSuggestions([])

    try {
      const results = await searchWorldwideLocations(query)
      setAdminLocationSuggestions(results)
      setAdminLocationMessage(
        results.length
          ? 'Select the correct city or settlement. CivicLens will reuse it or register it for dashboard reporting.'
          : 'No location found. Try a nearby city or a more specific search.',
      )
    } catch (error) {
      setAdminLocationMessage(error instanceof Error ? error.message : 'Location search is temporarily unavailable.')
    } finally {
      setAdminLocationLoading(false)
    }
  }

  async function chooseDashboardLocation(suggestion: LocationSuggestion) {
    setAdminLocationLoading(true)
    setAdminLocationMessage(`Resolving ${suggestion.cityName}, ${suggestion.country}…`)
    setAdminLocationSuggestions([])

    try {
      const city = await api.resolveCity({
        cityName: suggestion.cityName,
        state: suggestion.state || null,
        country: suggestion.country,
        countryCode: suggestion.countryCode,
        centerLatitude: suggestion.lat,
        centerLongitude: suggestion.lon,
      })

      setAdminSelectedCity(city)
      setAdminCityId(city.id)
      setAdminLocationQuery([city.name, city.state, city.country].filter(Boolean).join(', '))
      setAdminLocationMessage(`${city.name}, ${city.country} selected. ${city.departmentCount} internal CivicLens triage queues are available.`)
      setAdminMessage('')
    } catch (error) {
      setAdminLocationMessage(error instanceof Error ? error.message : 'Could not resolve this location.')
    } finally {
      setAdminLocationLoading(false)
    }
  }

  function clearDashboardLocation() {
    setAdminSelectedCity(null)
    setAdminCityId('')
    setAdminLocationQuery('')
    setAdminLocationSuggestions([])
    setAdminLocationMessage('Showing incidents across all registered locations.')
    setDepartments([])
    setOfficialDepartmentId('')
  }

  async function provisionDepartmentOfficial(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (currentUser?.role !== 'SYSTEM_ADMIN') return

    if (!officialDepartmentId) {
      setOfficialProvisionMessage('Select an internal department queue first.')
      return
    }

    setOfficialProvisionBusy(true)
    setOfficialProvisionMessage('')

    try {
      const created = await api.createOfficial(
        officialName.trim(),
        officialEmail.trim(),
        officialPassword,
        officialDepartmentId,
      )
      setOfficialProvisionMessage(`Official account created for ${created.displayName} (${created.email}). Share the credentials securely, not in a public channel.`)
      setOfficialName('')
      setOfficialEmail('')
      setOfficialPassword('')
    } catch (error) {
      setOfficialProvisionMessage(error instanceof Error ? error.message : 'Could not create the department account.')
    } finally {
      setOfficialProvisionBusy(false)
    }
  }

  async function retryAnalysis() {
    if (!selectedIssue) return

    setBusyAction(true)
    try {
      setAnalysis(await api.requestAnalysis(selectedIssue.id))
      setDetailMessage('A new analysis run has been queued.')
    } catch (error) {
      setDetailMessage(error instanceof Error ? error.message : 'Could not request another analysis run.')
    } finally {
      setBusyAction(false)
    }
  }

  const allNavItems: { id: View; label: string; icon: string }[] = [
    { id: 'community', label: 'Community', icon: 'home' },
    { id: 'report', label: 'Report an issue', icon: 'plus' },
    { id: 'issue', label: 'Issue details', icon: 'message' },
    { id: 'admin', label: 'Department dashboard', icon: 'chart' },
    { id: 'leaderboard', label: 'Rewards', icon: 'trophy' },
    { id: 'map', label: 'Map & hotspots', icon: 'map' },
  ]

  const navItems = allNavItems.filter((item) =>
    accountRole === 'organisation'
      ? ['admin', 'issue', 'map'].includes(item.id)
      : item.id !== 'admin',
  )

  const displayComments = useMemo(() => comments.filter((item) => !item.deleted), [comments])

  if (authLoading) {
    return <div className="auth-shell"><main className="auth-main"><p className="muted">Restoring your CivicLens session…</p></main></div>
  }

  if (authStage !== 'app' || !currentUser) {
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
              <p className="eyebrow">A BETTER WORLD STARTS TOGETHER</p>
              <h1>Welcome to <span>CivicLens</span></h1>
              <p className="auth-intro">A shared space for citizens to raise concerns and civic teams to act on them.</p>
              <h2 className="auth-question">How would you like to continue?</h2>
              <div className="role-grid">
                <button className="role-card" onClick={() => { setSelectedAccountRole('public'); setAuthStage('login'); setLoginMessage('') }}>
                  <span className="role-icon"><Icon name="users" size={27} /></span>
                  <span className="role-title">Public / Citizen</span>
                  <span className="role-description">Report civic issues, support communities, and track updates.</span>
                  <span className="role-continue">Continue as citizen <span>→</span></span>
                </button>
                <button className="role-card" onClick={() => { setSelectedAccountRole('organisation'); setAuthStage('login'); setLoginMessage('') }}>
                  <span className="role-icon"><Icon name="chart" size={27} /></span>
                  <span className="role-title">Organisation / Government</span>
                  <span className="role-description">Review reports, prioritise work, assign teams and update status.</span>
                  <span className="role-continue">Continue as organisation <span>→</span></span>
                </button>
              </div>
              <p className="auth-footnote">Tiny reports. Clearer action. Stronger communities.</p>
            </>
          ) : (
            <div className="login-card">
              <button className="back-link" type="button" onClick={() => { setAuthStage('choose'); setLoginMessage('') }}>← Choose account type</button>
              <div className="login-icon"><Icon name={selectedAccountRole === 'organisation' ? 'chart' : 'users'} size={26} /></div>
              <p className="eyebrow">{authStage === 'register' ? 'JOIN CIVICLENS' : selectedAccountRole === 'organisation' ? 'CIVIC TEAM ACCESS' : 'COMMUNITY ACCESS'}</p>
              <h1>{authStage === 'register' ? 'Create citizen account' : selectedAccountRole === 'organisation' ? 'Organisation login' : 'Citizen login'}</h1>
              <p className="muted">{authStage === 'register' ? 'Create an account to report and follow civic issues worldwide.' : 'Use your CivicLens account credentials to continue.'}</p>

              <form className="login-form" onSubmit={authStage === 'register' ? handleRegister : handleAuthSubmit}>
                {authStage === 'register' && <label>Full name<input value={registerName} onChange={(event) => setRegisterName(event.target.value)} minLength={2} maxLength={60} placeholder="Your name" required autoComplete="name" /></label>}
                <label>Email address<input type="email" value={loginEmail} onChange={(event) => setLoginEmail(event.target.value)} placeholder="you@example.com" required autoComplete="email" /></label>
                <label>Password<input type="password" value={loginPassword} onChange={(event) => setLoginPassword(event.target.value)} minLength={authStage === 'register' ? 8 : undefined} maxLength={72} placeholder={authStage === 'register' ? 'At least 8 characters' : 'Enter your password'} required autoComplete={authStage === 'register' ? 'new-password' : 'current-password'} /></label>
                {loginMessage && <p className="form-message" role="alert">{loginMessage}</p>}
                <button className="primary-button login-submit" type="submit" disabled={authBusy}>{authBusy ? 'Please wait…' : authStage === 'register' ? 'Create account →' : `Continue to ${selectedAccountRole === 'organisation' ? 'workspace' : 'community'} →`}</button>
                {selectedAccountRole === 'public' && authStage === 'login' && <button type="button" className="text-action auth-switch" onClick={() => { setAuthStage('register'); setLoginMessage('') }}>New to CivicLens? Create a citizen account</button>}
                {authStage === 'register' && <button type="button" className="text-action auth-switch" onClick={() => { setAuthStage('login'); setLoginMessage('') }}>Already registered? Sign in</button>}
                {selectedAccountRole === 'organisation' && <p className="login-demo-note">Official accounts are provisioned by a CivicLens system administrator. Selecting this option does not grant official permissions.</p>}
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
        <a
          className="brand"
          href={accountRole === 'organisation' ? '#admin' : '#home'}
          onClick={(event) => {
            event.preventDefault()
            setView(accountRole === 'organisation' ? 'admin' : 'community')
            setFormMessage('')
          }}
        >
          <span className="brand-mark">C</span>
          <span>
            <strong>CivicLens</strong>
            <small>AI · Civic Community</small>
          </span>
        </a>

        <div className="city-chip">
          <Icon name="pin" size={16} />
          <span>{activeCity ? `${activeCity.name}, ${activeCity.country}` : 'Global · All locations'}</span>
          <span className="online-dot" />
        </div>

        <p className="nav-caption">WORKSPACE</p>
        <nav className="main-nav" aria-label="Main navigation">
          {navItems.map((item) => (
            <button
              key={item.id}
              className={`nav-item ${view === item.id ? 'active' : ''}`}
              onClick={() => { setView(item.id); setFormMessage('') }}
            >
              <Icon name={item.icon} size={18} />
              <span>{item.label}</span>
              {item.id === 'report' && <span className="nav-plus">+</span>}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          {accountRole === 'public' && (
            <div className="points-card">
              <span className="points-icon"><Icon name="trophy" size={20} /></span>
              <div>
                <small>Your contribution</small>
                <strong>{rewardSummary ? `${rewardSummary.balance} points` : 'View rewards'}</strong>
              </div>
            </div>
          )}
          <div className="profile-row">
            <div className="avatar">{currentUser.displayName.charAt(0).toUpperCase()}</div>
            <div>
              <strong>{currentUser.displayName}</strong>
              <small>{currentUser.role.replaceAll('_', ' ')}</small>
            </div>
            <button className="profile-menu" onClick={signOut} aria-label="Sign out" title="Sign out">↪</button>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="mobile-brand"><span className="brand-mark">C</span><strong>CivicLens AI</strong></div>
          <div className="breadcrumb">
            <span>{accountRole === 'organisation' ? 'Organisation workspace' : 'Community workspace'}</span>
            <span>/</span>
            <strong>{navItems.find((item) => item.id === view)?.label}</strong>
          </div>
          <div className="topbar-actions">
            <label className="search-box">
              <span className="search-symbol">⌕</span>
              <input value={searchText} onChange={(event) => setSearchText(event.target.value)} placeholder="Search reports..." aria-label="Search reports" />
            </label>
            <button className="theme-toggle" type="button" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')} aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`} title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}>
              <Icon name={theme === 'light' ? 'moon' : 'sun'} size={19} />
            </button>
            {accountRole === 'public' && (
              <button className="primary-button top-report" onClick={() => { setView('report'); setFormMessage('') }}>
                <Icon name="plus" size={17} /> Report issue
              </button>
            )}
          </div>
        </header>

        <div className="content">
          {view === 'community' && (
            <>
              <section className="welcome-row">
                <div>
                  <p className="eyebrow">CIVIC PULSE · GLOBAL COMMUNITY</p>
                  <h1>Your community, <span>your voice.</span></h1>
                  <p className="muted">Report civic issues wherever you are, support communities, and follow progress together.</p>
                </div>
                <button className="primary-button" onClick={() => { setView('report'); setFormMessage('') }}>
                  <Icon name="plus" size={17} /> Create a report
                </button>
              </section>

              <section className="stats-grid">
                <div className="stat-card"><span className="stat-icon"><Icon name="file" /></span><div><small>Loaded reports</small><strong>{issues.length}</strong><small className="stat-foot">Current feed page</small></div></div>
                <div className="stat-card"><span className="stat-icon"><Icon name="chart" /></span><div><small>In progress</small><strong>{issues.filter((issue) => issue.apiStatus === 'IN_PROGRESS').length}</strong><small className="stat-foot">In loaded reports</small></div></div>
                <div className="stat-card"><span className="stat-icon"><Icon name="users" /></span><div><small>Safety critical</small><strong>{issues.filter((issue) => issue.safetyCritical).length}</strong><small className="stat-foot">In loaded reports</small></div></div>
                <div className="stat-card"><span className="stat-icon"><Icon name="trophy" /></span><div><small>Resolved</small><strong>{issues.filter((issue) => issue.apiStatus === 'RESOLVED').length}</strong><small className="stat-foot">In loaded reports</small></div></div>
              </section>

              <section className="feed-layout">
                <div className="feed-main">
                  <div className="section-heading">
                    <div>
                      <h2>Community feed</h2>
                      <p className="muted">{activeCity ? `Reports near ${activeCity.name}, ${activeCity.country}` : 'Reports from CivicLens communities worldwide'}</p>
                    </div>
                    <div className="sort-tabs">
                      {(['Hot', 'New', 'Top'] as const).map((item) => (
                        <button key={item} className={sort === item ? 'selected' : ''} onClick={() => setSort(item)}>{item}</button>
                      ))}
                    </div>
                  </div>

                  <div className="filter-row">
                    {['All', 'Reported', 'In Progress', 'Resolved'].map((item) => (
                      <button key={item} className={filter === item ? 'filter-active' : ''} onClick={() => setFilter(item)}>{item}</button>
                    ))}
                  </div>

                  {feedMessage && <p className="form-message" role="alert">{feedMessage}</p>}

                  <div className="issue-list">
                    {feedLoading && <div className="empty-state"><strong>Loading reports…</strong></div>}
                    {!feedLoading && filteredIssues.map((issue) => (
                      <IssueCard
                        key={issue.id}
                        issue={issue}
                        voted={issue.myVote === 'UP'}
                        onVote={() => void toggleVote(issue)}
                        onOpen={() => { setSelectedIssueId(issue.id); setView('issue') }}
                        disabled={busyAction || issue.reportedByMe}
                      />
                    ))}
                    {!feedLoading && filteredIssues.length === 0 && (
                      <div className="empty-state">
                        <Icon name="file" size={28} />
                        <strong>No reports found</strong>
                        <p>{feedMessage || 'Try another search or choose a wider location scope.'}</p>
                      </div>
                    )}
                  </div>
                </div>

                <aside className="feed-side">
                  <div className="panel-card city-panel">
                    <div className="panel-title"><h3>Location scope</h3><span className="live-badge">● API connected</span></div>
                    <div className="city-banner">
                      <span className="city-emblem"><Icon name="home" size={27} /></span>
                      <div>
                        <strong>{activeCity ? `${activeCity.name} community` : 'Global CivicLens'}</strong>
                        <small>{activeCity ? [activeCity.state, activeCity.country].filter(Boolean).join(', ') : 'Worldwide location-aware reporting'}</small>
                      </div>
                    </div>
                    <div className="city-stats">
                      <div><strong>{issues.length}</strong><small>Loaded</small></div>
                      <div><strong>{issues.filter((issue) => issue.apiStatus === 'RESOLVED').length}</strong><small>Resolved</small></div>
                      <div><strong>{issues.filter((issue) => issue.safetyCritical).length}</strong><small>Safety alerts</small></div>
                    </div>
                    <button className="outline-button full-width" onClick={() => { setActiveCity(null); setFilter('All') }}>Browse all locations</button>
                    <button className="outline-button full-width" onClick={() => setView('map')}><Icon name="map" size={16} /> Explore report locations</button>
                  </div>

                  <div className="panel-card">
                    <h3>How CivicLens works</h3>
                    <div className="mini-step"><span>1</span><div><strong>Report an issue</strong><small>Choose any supported worldwide location.</small></div></div>
                    <div className="mini-step"><span>2</span><div><strong>AI and community review</strong><small>Check duplicate reports and support useful reports.</small></div></div>
                    <div className="mini-step"><span>3</span><div><strong>Internal triage queue</strong><small>Authorized CivicLens officials manage status and assignment.</small></div></div>
                  </div>
                </aside>
              </section>
            </>
          )}

          {view === 'report' && (
            <section className="form-page">
              <div className="page-heading">
                <p className="eyebrow">HELP IMPROVE YOUR COMMUNITY</p>
                <h1>Report an <span>issue</span></h1>
                <p className="muted">Share clear details and select the real-world location. CivicLens checks for similar reports before saving.</p>
              </div>

              <form className="report-form panel-card" onSubmit={(event) => void submitReport(event)}>
                <div className="form-section-title"><span className="step-number">01</span><div><h2>Issue details</h2><p className="muted">Describe what is happening.</p></div></div>
                <label className="field-label">Issue title <span className="required">*</span><input value={reportTitle} onChange={(event) => setReportTitle(event.target.value)} minLength={5} maxLength={120} placeholder="e.g. Large pothole near the market entrance" required /></label>
                <label className="field-label">Description <span className="required">*</span><textarea value={reportDescription} onChange={(event) => setReportDescription(event.target.value)} rows={5} minLength={10} maxLength={2000} placeholder="Describe the issue, when it started, and how it affects the community…" required /><small className="field-hint">{reportDescription.length}/2000 characters</small></label>

                <div className="form-two-col">
                  <label className="field-label">
                    Category <span className="required">*</span>
                    <select value={reportCategory} onChange={(event) => setReportCategory(event.target.value)}>
                      {categories.map((category) => <option key={category}>{category}</option>)}
                    </select>
                    <small className="field-hint">The category is a citizen suggestion. AI analysis is a separate backend step.</small>
                  </label>

                  <div className="field-label locality-field">
                    <label htmlFor="report-locality">City / locality <span className="required">*</span></label>
                    <div className="locality-input-wrap">
                      <input
                        id="report-locality"
                        value={reportLocality}
                        onChange={(event) => {
                          setReportLocality(event.target.value)
                          setReportCoordinates(null)
                          setSelectedLocation(null)
                          setResolvedCity(null)
                          setLocationSuggestions([])
                          setLocationMessage('')
                        }}
                        placeholder="Search any city or settlement worldwide…"
                        maxLength={200}
                        autoComplete="off"
                        required
                      />
                      <button type="button" className="location-button" onClick={useCurrentLocation} disabled={gpsLoading}>
                        <Icon name="pin" size={15} />{gpsLoading ? 'Locating…' : 'Use my location'}
                      </button>
                    </div>
                    <button type="button" className="text-action" onClick={() => void searchTypedLocation()} disabled={locationLoading}>
                      {locationLoading ? 'Searching…' : 'Search worldwide locations'}
                    </button>
                    {locationMessage && <p className="location-message" role="status">{locationMessage}</p>}
                    {selectedLocation && (
                      <p className="location-message">
                        Selected: {selectedLocation.cityName}{selectedLocation.state ? `, ${selectedLocation.state}` : ''}, {selectedLocation.country}.
                        {resolvedCity ? ` CivicLens location ID: ${resolvedCity.id}` : ' Resolving location…'}
                      </p>
                    )}
                    {locationSuggestions.length > 0 && (
                      <div className="locality-suggestions" role="listbox">
                        {locationSuggestions.map((suggestion) => (
                          <button
                            type="button"
                            role="option"
                            aria-selected={false}
                            className="locality-suggestion"
                            key={`${suggestion.cityName}-${suggestion.lat}-${suggestion.lon}`}
                            onClick={() => void chooseLocation(suggestion)}
                          >
                            <Icon name="pin" size={16} /><span>{suggestion.label}</span>
                          </button>
                        ))}
                      </div>
                    )}
                    <p className="location-message">
                      Location search data © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap contributors</a>.
                    </p>
                  </div>
                </div>

                <label className="hazard-toggle">
                  <input type="checkbox" checked={reportHazard} onChange={(event) => setReportHazard(event.target.checked)} />
                  This report involves an immediate safety hazard or risk to people.
                </label>

                <div className="form-section-title evidence-heading">
                  <span className="step-number">02</span>
                  <div><h2>Attach evidence <span className="optional">(optional)</span></h2><p className="muted">Select files for local preview. Upload persistence is not available in this backend yet.</p></div>
                </div>
                <p className="field-hint evidence-local-note">Evidence is only previewed in this browser session; it is not uploaded or stored by the backend.</p>
                <div className="upload-options">
                  <label className="upload-option"><input type="file" accept="image/*" capture="environment" onChange={addFiles} /><span className="upload-icon"><Icon name="camera" size={21} /></span><strong>Camera</strong><small>Take a photo</small></label>
                  <label className="upload-option"><input type="file" accept="image/*" multiple onChange={addFiles} /><span className="upload-icon"><Icon name="file" size={21} /></span><strong>Photos</strong><small>Choose images</small></label>
                  <label className="upload-option"><input type="file" accept="video/*" multiple onChange={addFiles} /><span className="upload-icon"><span className="video-glyph">▶</span></span><strong>Videos</strong><small>Choose clips</small></label>
                  <label className="upload-option"><input type="file" accept=".pdf,.doc,.docx,.txt,.xls,.xlsx,.csv,application/*,text/*" multiple onChange={addFiles} /><span className="upload-icon"><Icon name="file" size={21} /></span><strong>Files</strong><small>Documents</small></label>
                </div>

                {attachments.length > 0 && (
                  <div className="attachment-list">
                    <div className="attachment-list-heading">{attachments.length} attachment{attachments.length === 1 ? '' : 's'} selected (local only)</div>
                    {attachments.map((attachment) => (
                      <div className="attachment-row" key={attachment.id}>
                        <Icon name="file" size={17} />
                        <span className="attachment-name">{attachment.file.name}</span>
                        <small>{formatBytes(attachment.file.size)}</small>
                        <button type="button" className="remove-file" onClick={() => removeFile(attachment.id)} aria-label={`Remove ${attachment.file.name}`}>×</button>
                      </div>
                    ))}
                  </div>
                )}

                {duplicateCandidates.length > 0 && (
                  <div className="duplicate-panel">
                    <h3>Similar reports found</h3>
                    <p>Review these existing reports. Open an existing report to review its details, or confirm that your report describes a separate problem.</p>
                    {duplicateCandidates.map((candidate) => (
                      <div className="duplicate-candidate" key={candidate.incidentId}>
                        <div>
                          <strong>{candidate.title}</strong>
                          <small>{candidate.locality || 'Location not specified'} · {Math.round(candidate.score * 100)}% match</small>
                          <p>{candidate.reasons.map((reason) => reason.message).join(' · ')}</p>
                        </div>
                        <button type="button" className="outline-button" onClick={() => { setSelectedIssueId(candidate.incidentId); setView('issue') }}>View report</button>
                      </div>
                    ))}
                    <button type="button" className="primary-button" onClick={() => void submitReport(undefined, true)} disabled={duplicateBusy}>
                      {duplicateBusy ? 'Submitting…' : 'I reviewed these; this is a separate issue'}
                    </button>
                  </div>
                )}

                {formMessage && <p className="form-message" role="status">{formMessage}</p>}

                <div className="form-actions">
                  <button type="button" className="outline-button" onClick={() => setView('community')}>Cancel</button>
                  <button type="submit" className="primary-button" disabled={duplicateBusy || locationLoading || !resolvedCity}>
                    {duplicateBusy ? 'Checking…' : 'Check & submit report →'}
                  </button>
                </div>
              </form>
            </section>
          )}

          {view === 'issue' && (
            <section className="detail-page">
              <button className="back-link" onClick={() => setView(isOfficial ? 'admin' : 'community')}>← Back</button>
              {detailLoading && <p className="muted">Loading incident details…</p>}

              {selectedIssue ? (
                <div className="detail-layout">
                  <div className="detail-main panel-card">
                    <div className="detail-topline">
                      <span className="issue-id">{selectedIssue.reference}</span>
                      <StatusBadge status={selectedIssue.status} />
                    </div>
                    <h1>{selectedIssue.title}</h1>
                    <p className="detail-description">{selectedIssue.description}</p>
                    <div className="issue-meta">
                      <span><Icon name="pin" size={16} />{selectedIssue.locality}</span>
                      <span>·</span>
                      <span>{selectedIssue.createdAt}</span>
                      <span>·</span>
                      <span>{selectedIssue.category}</span>
                    </div>
                    {selectedIssue.demoData && <p className="field-hint">Clearly labelled demo report — not a verified real-world municipal case.</p>}

                    <div className="detail-actions">
                      <button
                        className={`vote-button ${selectedIssue.myVote === 'UP' ? 'voted' : ''}`}
                        onClick={() => void toggleVote(selectedIssue)}
                        disabled={busyAction || selectedIssue.reportedByMe}
                      >
                        <Icon name="thumb" size={18} />
                        {selectedIssue.myVote === 'UP' ? 'Supported' : 'Upvote'}
                        <strong>{selectedIssue.votes}</strong>
                      </button>
                      <span className="detail-action-muted"><Icon name="message" size={16} /> {selectedIssue.commentCount} comments</span>
                    </div>

                    <div className="detail-tabs">
                      <span className="active">Comments ({selectedIssue.commentCount})</span>
                      <span>Updates ({timeline.length})</span>
                      <span>Details</span>
                    </div>

                    <form className="comment-form" onSubmit={(event) => void addComment(event)}>
                      <input
                        value={commentText}
                        onChange={(event) => setCommentText(event.target.value)}
                        placeholder={currentUser ? 'Add a constructive comment or update…' : 'Sign in to comment'}
                        disabled={!currentUser}
                      />
                      <button className="primary-button" type="submit" disabled={busyAction || !currentUser}>Post</button>
                    </form>

                    {displayComments.map((comment) => (
                      <div className="comment-item" key={comment.id}>
                        <div className="avatar small-avatar">{comment.authorDisplayName.charAt(0).toUpperCase()}</div>
                        <div>
                          <strong>{comment.authorDisplayName}{comment.official ? ' · Official' : ''}</strong>
                          <p>{comment.body}</p>
                          <small>{new Date(comment.createdAt).toLocaleString()}</small>
                        </div>
                      </div>
                    ))}

                    <div className="timeline-section">
                      <h3>Incident history</h3>
                      {timeline.map((event) => (
                        <div className="timeline-item done" key={event.id}>
                          <span />
                          <div>
                            <strong>{event.type.replaceAll('_', ' ')}{event.toStatus ? ` · ${displayStatus[event.toStatus]}` : ''}</strong>
                            <small>{event.note || event.departmentName || event.actorRole.replaceAll('_', ' ')} · {new Date(event.createdAt).toLocaleString()}</small>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  <aside className="detail-aside">
                    <div className="panel-card">
                      <h3>Report status</h3>
                      <StatusBadge status={selectedIssue.status} />
                      <p className="field-hint">{selectedIssue.departmentName ? `Assigned queue: ${selectedIssue.departmentName}` : 'Awaiting department assignment.'}</p>
                      {selectedIssue.resolutionNote && <p>{selectedIssue.resolutionNote}</p>}
                    </div>

                    <div className="panel-card ai-placeholder">
                      <span className="ai-tag">AI TRIAGE · BACKEND</span>
                      <h3>Incident analysis</h3>
                      {analysis ? (
                        <>
                          <span className={`pending-pill ${analysis.status === 'COMPLETED' ? 'complete-pill' : ''}`}>
                            {analysis.status}
                            {analysis.aiGenerated ? ' · GEMINI' : analysis.source === 'RULE_BASED_FALLBACK' ? ' · RULE-BASED FALLBACK' : ''}
                          </span>
                          {analysis.status === 'COMPLETED' && (
                            <>
                              <p><strong>Suggested category:</strong> {analysis.category || 'Not assessed'}</p>
                              <p><strong>Suggested severity:</strong> {analysis.severity || 'Not assessed'}</p>
                              <p><strong>Confidence:</strong> {analysis.confidence == null ? '—' : `${Math.round(analysis.confidence * 100)}%`}</p>
                              <p>{analysis.explanation}</p>
                              {analysis.recommendedDepartmentName && <p><strong>Recommended queue:</strong> {analysis.recommendedDepartmentName}</p>}
                              {analysis.safetyConcerns.length > 0 && <p><strong>Safety concerns:</strong> {analysis.safetyConcerns.join('; ')}</p>}
                              {analysis.missingInformation.length > 0 && <p><strong>Missing information:</strong> {analysis.missingInformation.join('; ')}</p>}
                            </>
                          )}
                          {analysis.status === 'FAILED' && <p>{analysis.lastErrorMessage || 'Analysis failed.'}</p>}
                          <small>{analysis.disclaimer}</small>
                          {(analysis.status === 'FAILED' || (analysis.status === 'COMPLETED' && analysis.source === 'RULE_BASED_FALLBACK')) &&
                            (selectedIssue.reportedByMe || isOfficial) && (
                              <button className="outline-button full-width" onClick={() => void retryAnalysis()} disabled={busyAction}>Retry AI analysis</button>
                            )}
                        </>
                      ) : (
                        <p>{detailMessage || 'Analysis is queued or not yet available. Analysis will appear here when processing finishes.'}</p>
                      )}
                    </div>

                    {isOfficial && (
                      <div className="panel-card official-actions">
                        <h3>Official workflow</h3>
                        {(selectedIssue.availableActions.includes('ASSIGN') || selectedIssue.availableActions.some((action) => action.startsWith('STATUS:'))) && (
                          <textarea
                            value={statusNote}
                            onChange={(event) => setStatusNote(event.target.value)}
                            rows={2}
                            placeholder="Audit note (optional for assignment; required for resolution, rejection and reopening)"
                          />
                        )}

                        {selectedIssue.availableActions.includes('ASSIGN') && (
                          <>
                            <label className="field-label">
                              Assign internal queue
                              <select value={selectedDepartmentId} onChange={(event) => setSelectedDepartmentId(event.target.value)}>
                                <option value="">Select department</option>
                                {departments.map((department) => (
                                  <option key={department.id} value={department.id}>{department.name}</option>
                                ))}
                              </select>
                            </label>
                            <button className="outline-button full-width" onClick={() => void assignDepartment()} disabled={busyAction || !selectedDepartmentId}>
                              Assign / reassign
                            </button>
                          </>
                        )}

                        {selectedIssue.availableActions.includes('TRIAGE') && (
                          <>
                            <h4>Verify AI triage</h4>
                            <label className="field-label">
                              Confirmed category
                              <select value={triageCategory} onChange={(event) => setTriageCategory(event.target.value as IncidentCategory)}>
                                {categoryOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                              </select>
                            </label>
                            <label className="field-label">
                              Confirmed severity
                              <select value={triageSeverity} onChange={(event) => setTriageSeverity(event.target.value as Severity)}>
                                <option value="LOW">Low</option>
                                <option value="MEDIUM">Medium</option>
                                <option value="HIGH">High</option>
                                <option value="CRITICAL">Critical</option>
                              </select>
                            </label>
                            <label className="hazard-toggle">
                              <input type="checkbox" checked={triageSafety} onChange={(event) => setTriageSafety(event.target.checked)} />
                              Mark as safety critical
                            </label>
                            <button className="outline-button full-width" onClick={() => void saveTriage()} disabled={busyAction}>
                              Save verified triage
                            </button>
                          </>
                        )}

                        {selectedIssue.availableActions.some((action) => action.startsWith('STATUS:')) && (
                          <>
                            <label className="field-label">
                              Update status
                              <select id="issue-status-update" value={statusSelection} onChange={(event) => setStatusSelection(event.target.value as IncidentStatus | '')}>
                                <option value="">Choose allowed status</option>
                                {selectedIssue.availableActions
                                  .filter((action) => action.startsWith('STATUS:'))
                                  .map((action) => {
                                    const status = action.slice(7) as IncidentStatus
                                    return <option key={status} value={status}>{displayStatus[status]}</option>
                                  })}
                              </select>
                            </label>
                            <small className="field-hint">Only server-authorized status transitions are shown. Resolution, rejection and reopening require an audit note of at least five characters.</small>
                            <button
                              className="outline-button full-width"
                              onClick={() => void updateStatus(statusSelection as IncidentStatus)}
                              disabled={busyAction || !statusSelection || (['RESOLVED', 'REJECTED', 'REOPENED'].includes(statusSelection) && statusNote.trim().length < 5)}
                            >
                              Apply status change
                            </button>
                          </>
                        )}
                      </div>
                    )}

                    {currentUser.role === 'CITIZEN' && selectedIssue.reportedByMe && selectedIssue.availableActions.includes('STATUS:REOPENED') && (
                      <div className="panel-card official-actions">
                        <h3>Reopen this report</h3>
                        <p className="field-hint">Use this only if the reported problem has returned or was not actually resolved.</p>
                        <textarea
                          value={statusNote}
                          onChange={(event) => setStatusNote(event.target.value)}
                          rows={2}
                          placeholder="Explain why the report should be reopened (at least 5 characters)"
                        />
                        <button className="outline-button full-width" onClick={() => void updateStatus('REOPENED')} disabled={busyAction || statusNote.trim().length < 5}>
                          Request reopening
                        </button>
                      </div>
                    )}
                  </aside>
                </div>
              ) : !detailLoading && (
                <div className="empty-state">
                  <strong>Incident not found</strong>
                  <p>{detailMessage}</p>
                </div>
              )}

              {detailMessage && <p className="form-message" role="status">{detailMessage}</p>}
            </section>
          )}

          {view === 'admin' && (
            <section className="dashboard-page">
              <div className="page-heading">
                <p className="eyebrow">OPERATIONS · INTERNAL TRIAGE</p>
                <h1>Department <span>dashboard</span></h1>
                <p className="muted">Live metrics and open reports from the backend. Department queues are internal CivicLens workflows, not live municipal integrations.</p>
              </div>

              {currentUser.role === 'SYSTEM_ADMIN' && (
                <div className="admin-city-scope panel-card">
                  <div className="section-heading">
                    <div>
                      <h2>Dashboard location</h2>
                      <p className="muted">Search for any city or settlement worldwide, or view all registered locations.</p>
                    </div>
                    {adminCityId && (
                      <button type="button" className="text-action" onClick={clearDashboardLocation}>Clear location</button>
                    )}
                  </div>

                  <div className="locality-field">
                    <div className="locality-input-wrap">
                      <input
                        value={adminLocationQuery}
                        onChange={(event) => {
                          setAdminLocationQuery(event.target.value)
                          setAdminLocationSuggestions([])
                          setAdminLocationMessage('')
                        }}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault()
                            void searchDashboardLocation()
                          }
                        }}
                        placeholder="Search any city, region or country worldwide…"
                        maxLength={200}
                        autoComplete="off"
                      />
                      <button
                        type="button"
                        className="location-button"
                        onClick={() => void searchDashboardLocation()}
                        disabled={adminLocationLoading}
                      >
                        {adminLocationLoading ? 'Searching…' : 'Search location'}
                      </button>
                    </div>

                    {adminLocationMessage && <p className="location-message" role="status">{adminLocationMessage}</p>}

                    {adminSelectedCity && adminCityId === adminSelectedCity.id && (
                      <p className="location-message">
                        Selected dashboard scope: {adminSelectedCity.name}{adminSelectedCity.state ? `, ${adminSelectedCity.state}` : ''}, {adminSelectedCity.country}.
                      </p>
                    )}

                    {adminLocationSuggestions.length > 0 && (
                      <div className="locality-suggestions" role="listbox">
                        {adminLocationSuggestions.map((suggestion) => (
                          <button
                            type="button"
                            role="option"
                            aria-selected={false}
                            className="locality-suggestion"
                            key={`${suggestion.cityName}-${suggestion.lat}-${suggestion.lon}`}
                            onClick={() => void chooseDashboardLocation(suggestion)}
                          >
                            <Icon name="pin" size={16} />
                            <span>{suggestion.label}</span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                </div>
              )}

              {currentUser.role === 'SYSTEM_ADMIN' && (
                <form className="panel-card official-provision-panel" onSubmit={(event) => void provisionDepartmentOfficial(event)}>
                  <div className="section-heading">
                    <div>
                      <h2>Create department access</h2>
                      <p className="muted">Provision an official account for an internal CivicLens queue at a selected location.</p>
                    </div>
                  </div>

                  {!adminCityId && <p className="field-hint">Search and select a dashboard location above first. Its internal department queues will then appear here.</p>}

                  <div className="form-two-col">
                    <label className="field-label">
                      Official name
                      <input value={officialName} onChange={(event) => setOfficialName(event.target.value)} minLength={2} maxLength={60} required />
                    </label>
                    <label className="field-label">
                      Official email
                      <input type="email" value={officialEmail} onChange={(event) => setOfficialEmail(event.target.value)} maxLength={254} required />
                    </label>
                    <label className="field-label">
                      Temporary password
                      <input type="password" value={officialPassword} onChange={(event) => setOfficialPassword(event.target.value)} minLength={12} maxLength={72} autoComplete="new-password" required />
                      <small className="field-hint">At least 12 characters. Share securely.</small>
                    </label>
                    <label className="field-label">
                      Department queue
                      <select value={officialDepartmentId} onChange={(event) => setOfficialDepartmentId(event.target.value)} required>
                        <option value="">Select queue</option>
                        {departments.map((department) => (
                          <option key={department.id} value={department.id}>{department.name}</option>
                        ))}
                      </select>
                    </label>
                  </div>

                  <button className="primary-button" type="submit" disabled={officialProvisionBusy || !officialDepartmentId}>
                    {officialProvisionBusy ? 'Creating…' : 'Create official account'}
                  </button>
                  {officialProvisionMessage && <p className="form-message" role="status">{officialProvisionMessage}</p>}
                </form>
              )}

              {adminMessage && <p className="form-message" role="alert">{adminMessage}</p>}

              {dashboard && (
                <>
                  <div className="admin-stats">
                    <div className="panel-card"><small>Total incidents</small><strong>{dashboard.totalIncidents}</strong><span>Stored reports</span></div>
                    <div className="panel-card"><small>Open incidents</small><strong>{dashboard.openIncidents}</strong><span>Awaiting closure</span></div>
                    <div className="panel-card"><small>Unassigned</small><strong>{dashboard.unassignedOpen}</strong><span>Needs routing</span></div>
                    <div className="panel-card"><small>Safety critical</small><strong>{dashboard.safetyCriticalOpen}</strong><span>Priority review</span></div>
                  </div>

                  <div className="admin-stats">
                    <div className="panel-card"><small>Aging open reports</small><strong>{dashboard.agingOpen}</strong><span>Older than {dashboard.agingThresholdDays} days</span></div>
                    <div className="panel-card"><small>Resolved</small><strong>{dashboard.byStatus.RESOLVED || 0}</strong><span>Backend aggregate</span></div>
                    <div className="panel-card"><small>Under review</small><strong>{dashboard.byStatus.UNDER_REVIEW || 0}</strong><span>Awaiting verification</span></div>
                    <div className="panel-card"><small>Avg. resolution</small><strong>{dashboard.averageResolutionHours == null ? '—' : `${dashboard.averageResolutionHours}h`}</strong><span>Resolved incidents</span></div>
                  </div>
                </>
              )}

              <div className="panel-card table-panel">
                <div className="section-heading">
                  <div>
                    <h2>Priority triage queue</h2>
                    <p className="muted">Open incidents sorted by backend priority score.</p>
                  </div>
                  <span className="pending-pill">{triageIssues.length} loaded</span>
                </div>

                <div className="responsive-table">
                  <table>
                    <thead>
                      <tr>
                        <th>Issue</th><th>Category</th><th>Location</th><th>Severity</th><th>Status</th><th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {triageIssues.map((issue) => (
                        <tr key={issue.id}>
                          <td>
                            <button className="table-link" onClick={() => { setSelectedIssueId(issue.id); setView('issue') }}>
                              {issue.title}
                            </button>
                            <small>{issue.reference}</small>
                          </td>
                          <td>{issue.category}</td>
                          <td>{issue.locality}</td>
                          <td>{issue.severity || 'Unassessed'}{issue.safetyCritical ? ' · Safety' : ''}</td>
                          <td><StatusBadge status={issue.status} /></td>
                          <td>
                            <button
                              className="outline-button"
                              onClick={() => {
                                setSelectedIssueId(issue.id)
                                setSelectedDepartmentId(issue.departmentId || currentUser.departmentId || '')
                                setView('issue')
                              }}
                            >
                              Review
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {triageIssues.length === 0 && <p className="empty-state">No open incidents in your authorized scope.</p>}
                </div>
              </div>

              <div className="panel-card table-panel">
                <div className="section-heading">
                  <div>
                    <h2>Department performance</h2>
                    <p className="muted">Workload, outcomes, ratings and reward totals.</p>
                  </div>
                </div>
                <div className="responsive-table">
                  <table>
                    <thead>
                      <tr>
                        <th>Department</th><th>Assigned</th><th>Open</th><th>Resolved</th><th>Rating</th><th>Points</th>
                      </tr>
                    </thead>
                    <tbody>
                      {departmentPerformance.map((department) => (
                        <tr key={department.departmentId}>
                          <td>{department.departmentName}</td>
                          <td>{department.assignedTotal}</td>
                          <td>{department.open}</td>
                          <td>{department.resolved}</td>
                          <td>{department.reviewCount ? `${department.averageRating.toFixed(1)} (${department.reviewCount})` : 'No reviews'}</td>
                          <td>{department.rewardPoints}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </section>
          )}

          {view === 'leaderboard' && (
            <section className="dashboard-page">
              <div className="page-heading">
                <p className="eyebrow">COMMUNITY IMPACT</p>
                <h1>Your <span>rewards</span></h1>
                <p className="muted">Your points are calculated from the backend's append-only reward ledger.</p>
              </div>
              {rewardMessage && <p className="form-message" role="alert">{rewardMessage}</p>}
              <div className="leaderboard-layout">
                <div className="panel-card reward-panel">
                  <span className="reward-medal"><Icon name="trophy" size={28} /></span>
                  <h2>Your impact matters</h2>
                  <p className="muted">Points are awarded for qualifying, verified civic actions.</p>
                  <div className="reward-total">
                    <strong>{rewardSummary?.balance ?? '—'}</strong>
                    <span>community points</span>
                  </div>
                  <p className="field-hint">A global citizen leaderboard is not currently exposed by the backend, so no fabricated ranking is shown.</p>
                </div>
                <div className="panel-card">
                  <div className="section-heading">
                    <div><h2>Reward activity</h2><p className="muted">Recent ledger entries</p></div>
                    <Icon name="trophy" size={24} />
                  </div>
                  {rewardSummary?.ledger.items.map((entry) => (
                    <div className="leader-row" key={entry.id}>
                      <span className="rank">+</span>
                      <div className="leader-name">
                        <strong>{entry.reason.replaceAll('_', ' ')}</strong>
                        <small>{new Date(entry.createdAt).toLocaleString()}</small>
                      </div>
                      <strong className="leader-points">+{entry.points} pts</strong>
                    </div>
                  ))}
                  {rewardSummary && rewardSummary.ledger.items.length === 0 && <p className="empty-state">No rewards recorded yet.</p>}
                </div>
              </div>
            </section>
          )}

          {view === 'map' && (
            <section className="dashboard-page">
              <div className="page-heading">
                <p className="eyebrow">GLOBAL LOCATION VIEW</p>
                <h1>Report <span>locations</span></h1>
                <p className="muted">Coordinates are supplied by citizen-selected locations. Select a report to view its details.</p>
              </div>
              <div className="panel-card hotspot-list">
                <h2>Reports with coordinates</h2>
                {issues.filter((issue) => issue.coordinates).map((issue) => (
                  <div className="hotspot-row" key={issue.id}>
                    <span className="hotspot-pin"><Icon name="pin" size={17} /></span>
                    <span>
                      <strong>{issue.title}</strong>
                      <small>{issue.locality} · {issue.coordinates?.latitude.toFixed(5)}, {issue.coordinates?.longitude.toFixed(5)}</small>
                    </span>
                    <a
                      className="text-action"
                      href={`https://www.openstreetmap.org/?mlat=${issue.coordinates?.latitude}&mlon=${issue.coordinates?.longitude}#map=17/${issue.coordinates?.latitude}/${issue.coordinates?.longitude}`}
                      target="_blank"
                      rel="noreferrer"
                    >
                      Open map ↗
                    </a>
                    <button className="outline-button" onClick={() => { setSelectedIssueId(issue.id); setView('issue') }}>View</button>
                  </div>
                ))}
                {issues.filter((issue) => issue.coordinates).length === 0 && (
                  <p className="empty-state">No reports with coordinates are loaded in this feed yet.</p>
                )}
              </div>
            </section>
          )}
        </div>

        <footer className="footer">
          <span>© 2026 CivicLens AI</span>
          <span>Built for clearer civic communication</span>
          <span className="footer-status"><span /> API-connected workspace</span>
        </footer>
      </main>
    </div>
  )
}

function IssueCard({ issue, voted, onVote, onOpen, disabled }: {
  issue: Issue
  voted: boolean
  onVote: () => void
  onOpen: () => void
  disabled: boolean
}) {
  return (
    <article className="issue-card">
      <div className="vote-rail">
        <button className={`vote-button ${voted ? 'voted' : ''}`} onClick={onVote} aria-label={voted ? 'Remove upvote' : 'Upvote issue'} disabled={disabled || issue.reportedByMe}>
          <Icon name="thumb" size={18} />
        </button>
        <strong>{issue.votes}</strong>
        <small>votes</small>
      </div>
      <div className="issue-card-body">
        <div className="issue-card-top">
          <span className="category-pill">{issue.category}</span>
          <StatusBadge status={issue.status} />
        </div>
        <button className="issue-title-button" onClick={onOpen}>{issue.title}</button>
        <p>{issue.description}</p>
        <div className="issue-card-meta">
          <span><Icon name="pin" size={14} />{issue.locality}</span>
          <span>{issue.createdAt}</span>
          <span><Icon name="message" size={14} /> {issue.commentCount} comments</span>
        </div>
        <div className="issue-card-bottom">
          <button onClick={onOpen} className="text-action">View details <span>→</span></button>
          <span className="issue-small-id">{issue.reference}</span>
        </div>
      </div>
    </article>
  )
}

function StatusBadge({ status }: { status: DisplayStatus }) {
  return <span className={`status-badge status-${status.toLowerCase().replaceAll(' ', '-')}`}>{status}</span>
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default App