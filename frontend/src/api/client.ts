const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const TOKEN_KEY = 'civiclens-access-token'

export type Role = 'CITIZEN' | 'DEPARTMENT_ADMIN' | 'SYSTEM_ADMIN'
export type IncidentCategory =
  | 'ROAD_DAMAGE' | 'STREETLIGHT' | 'GARBAGE' | 'WATER_LEAKAGE' | 'DRAINAGE'
  | 'FLOODING' | 'SEWAGE' | 'ELECTRICAL_HAZARD' | 'TREE_FALL' | 'TRAFFIC_SIGNAL'
  | 'PUBLIC_PROPERTY_DAMAGE' | 'OTHER'
export type IncidentStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED' | 'REOPENED'
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface UserView {
  id: string
  email: string
  displayName: string
  role: Role
  departmentId: string | null
  departmentName: string | null
  homeCityId: string | null
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: UserView
}

export interface CityView {
  id: string
  code: string
  name: string
  state: string | null
  country: string
  countryCode?: string
  centerLatitude: number | null
  centerLongitude: number | null
  departmentCount: number
  unmappedCategories: IncidentCategory[]
}


export interface LocationSuggestion {
  label: string
  lat: number
  lon: number
  cityName: string
  state: string | null
  country: string
  countryCode: string
  locality: string
}

export interface ResolveCityRequest {
  cityName: string
  state?: string | null
  country: string
  countryCode: string
  centerLatitude?: number | null
  centerLongitude?: number | null
}

export interface DepartmentView {
  id: string
  cityId: string
  code: string
  name: string
  description: string | null
  categories: IncidentCategory[]
  integrationStatus: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  hasNext: boolean
}

export interface IncidentView {
  id: string
  title: string
  description: string
  cityId: string
  cityName: string
  locality: string | null
  addressText: string | null
  latitude: number | null
  longitude: number | null
  category: IncidentCategory
  categorySource: string | null
  severity: Severity | null
  severitySource: string | null
  safetyCritical: boolean
  hazardReported: boolean
  status: IncidentStatus
  departmentId: string | null
  departmentName: string | null
  recommendedDepartmentId: string | null
  recommendedDepartmentName: string | null
  recommendedDepartmentSource: string | null
  analysisStatus: string | null
  supportCount: number
  opposeCount: number
  voteScore: number
  commentCount: number
  priorityScore: number | null
  trendingScore: number | null
  distanceMeters: number | null
  duplicateOfId: string | null
  resolutionNote: string | null
  demoData: boolean
  reportedByMe: boolean
  myVote: 'UP' | 'DOWN' | null
  createdAt: string
  updatedAt: string
  statusChangedAt: string | null
  resolvedAt: string | null
  version: number
  analysis: AnalysisView | null
  availableActions: string[]
}

export interface AnalysisView {
  analysisId: string
  runNumber: number
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  source: 'GEMINI' | 'RULE_BASED_FALLBACK' | null
  aiGenerated: boolean
  provider: string | null
  model: string | null
  thinkingLevel: 'LOW' | 'MEDIUM' | 'HIGH' | null
  promptVersion: string | null
  attemptCount: number
  maxAttempts: number
  nextAttemptAt: string | null
  category: IncidentCategory | null
  severity: Severity | null
  confidence: number | null
  safetyCritical: boolean | null
  explanation: string | null
  safetyConcerns: string[]
  missingInformation: string[]
  recommendedNextAction: string | null
  recommendedDepartmentId: string | null
  recommendedDepartmentName: string | null
  validationWarnings: string[]
  impactSummary: string | null
  riskRationale: string | null
  recommendedActions: string[]
  clarifyingQuestions: string[]
  citizenGuidance: string | null
  evidenceSuggestions: string[]
  lastErrorCode: string | null
  lastErrorMessage: string | null
  createdAt: string
  completedAt: string | null
  disclaimer: string
}

export interface DuplicateCandidate {
  incidentId: string
  title: string
  descriptionExcerpt: string
  locality: string | null
  category: IncidentCategory
  status: IncidentStatus
  createdAt: string
  distanceMeters: number | null
  score: number
  strongMatch: boolean
  supportCount: number
  reasons: { code: string; message: string }[]
}

export interface DuplicateCheckResponse {
  candidates: DuplicateCandidate[]
  strongMatchFound: boolean
  minScore: number
  strongMatchScore: number
  checkedAt: string
}

export interface CommentView {
  id: string
  incidentId: string
  authorDisplayName: string
  authorRole: Role
  official: boolean
  body: string
  deleted: boolean
  mine: boolean
  createdAt: string
}

export interface TimelineEntry {
  id: string
  type: string
  fromStatus: IncidentStatus | null
  toStatus: IncidentStatus | null
  departmentId: string | null
  departmentName: string | null
  actorRole: Role
  note: string | null
  createdAt: string
}

export interface DashboardSummary {
  cityId: string | null
  departmentId: string | null
  totalIncidents: number
  openIncidents: number
  byStatus: Record<string, number>
  byCategory: Record<string, number>
  bySeverity: Record<string, number>
  byDepartment: { departmentId: string; departmentName: string; open: number; resolved: number; total: number }[]
  unassignedOpen: number
  agingOpen: number
  agingThresholdDays: number
  safetyCriticalOpen: number
  averageResolutionHours: number | null
  recentlyUpdated: { id: string; title: string; status: IncidentStatus; severity: Severity | null; updatedAt: string }[]
  generatedAt: string
}

export interface DepartmentPerformance {
  departmentId: string
  departmentName: string
  assignedTotal: number
  open: number
  resolved: number
  rejected: number
  averageResolutionHours: number | null
  averageRating: number
  reviewCount: number
  rewardPoints: number
}

export interface RewardSummary {
  userId: string
  balance: number
  ledger: PageResponse<{ id: string; reason: string; points: number; incidentId: string | null; createdAt: string }>
}

export interface ApiErrorBody {
  code?: string
  message?: string
  details?: { field: string; message: string }[]
  data?: unknown
  traceId?: string
}

export class ApiError extends Error {
  status: number
  code?: string
  data?: unknown
  details?: ApiErrorBody['details']

  constructor(status: number, body: ApiErrorBody) {
    super(body.message || `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.data = body.data
    this.details = body.details
  }
}

export function getAccessToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearAccessToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getAccessToken()
  if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
  } catch {
    throw new Error(`Cannot reach CivicLens API at ${API_BASE_URL}. Check that Spring Boot is running.`)
  }

  if (response.status === 204) return undefined as T
  const contentType = response.headers.get('content-type') || ''
  const body = contentType.includes('application/json') ? await response.json().catch(() => null) : await response.text().catch(() => '')
  if (!response.ok) {
    const errorBody: ApiErrorBody = body && typeof body === 'object' ? body as ApiErrorBody : { message: typeof body === 'string' ? body : undefined }
    throw new ApiError(response.status, errorBody)
  }
  return body as T
}

const json = (value: unknown): string => JSON.stringify(value)
const uuidKey = (): string => globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`

export const api = {
  login: (email: string, password: string) => request<AuthResponse>('/api/v1/auth/login', { method: 'POST', body: json({ email, password }) }),
  register: (displayName: string, email: string, password: string, homeCityId?: string | null) => request<AuthResponse>('/api/v1/auth/register', { method: 'POST', body: json({ displayName, email, password, homeCityId: homeCityId || null }) }),
  createOfficial: (displayName: string, email: string, password: string, departmentId: string) => request<UserView>('/api/v1/admin/users', { method: 'POST', body: json({ displayName, email, password, role: 'DEPARTMENT_ADMIN', departmentId }) }),
  me: () => request<UserView>('/api/v1/auth/me'),
  logout: () => request<void>('/api/v1/auth/logout', { method: 'POST' }),
  cities: () => request<CityView[]>('/api/v1/cities'),
  searchLocations: (query: string) => request<LocationSuggestion[]>(`/api/v1/locations/search?query=${encodeURIComponent(query)}`),
  reverseLocation: (latitude: number, longitude: number) => request<LocationSuggestion>(`/api/v1/locations/reverse?latitude=${encodeURIComponent(latitude)}&longitude=${encodeURIComponent(longitude)}`),
  city: (cityId: string) => request<CityView>(`/api/v1/cities/${encodeURIComponent(cityId)}`),
  resolveCity: (body: ResolveCityRequest) => request<CityView>('/api/v1/cities/resolve', { method: 'POST', body: json(body) }),
  departments: (cityId: string) => request<DepartmentView[]>(`/api/v1/cities/${encodeURIComponent(cityId)}/departments`),
  department: (departmentId: string) => request<{ id: string; cityId: string; cityName: string; code: string; name: string; description: string; categories: IncidentCategory[] }>(`/api/v1/departments/${encodeURIComponent(departmentId)}`),
  enums: () => request<Record<string, string[]>>('/api/v1/system/enums'),
  feed: (params: URLSearchParams) => request<PageResponse<IncidentView>>(`/api/v1/incidents?${params.toString()}`),
  incident: (id: string) => request<IncidentView>(`/api/v1/incidents/${encodeURIComponent(id)}`),
  history: (id: string) => request<TimelineEntry[]>(`/api/v1/incidents/${encodeURIComponent(id)}/history`),
  analysis: (id: string) => request<AnalysisView>(`/api/v1/incidents/${encodeURIComponent(id)}/analysis`),
  requestAnalysis: (id: string) => request<AnalysisView>(`/api/v1/incidents/${encodeURIComponent(id)}/analysis`, { method: 'POST' }),
  duplicateCheck: (body: { cityId: string; title: string; description: string; locality: string; latitude: number | null; longitude: number | null; category: IncidentCategory }) => request<DuplicateCheckResponse>('/api/v1/incidents/duplicate-check', { method: 'POST', body: json(body) }),
  createIncident: (body: { cityId: string; title: string; description: string; locality: string; addressText: string; latitude: number | null; longitude: number | null; category: IncidentCategory; hazardReported: boolean; duplicateCheck: { reviewedCandidateIds: string[]; confirmedNotDuplicate: boolean } }) => request<IncidentView>('/api/v1/incidents', { method: 'POST', headers: { 'Idempotency-Key': uuidKey() }, body: json(body) }),
  vote: (id: string, type: 'UP' | 'DOWN' = 'UP') => request<{ incidentId: string; supportCount: number; opposeCount: number; score: number; myVote: 'UP' | 'DOWN' | null }>(`/api/v1/incidents/${encodeURIComponent(id)}/vote`, { method: 'PUT', body: json({ type }) }),
  removeVote: (id: string) => request<{ incidentId: string; supportCount: number; opposeCount: number; score: number; myVote: null }>(`/api/v1/incidents/${encodeURIComponent(id)}/vote`, { method: 'DELETE' }),
  comments: (id: string, page = 0) => request<PageResponse<CommentView>>(`/api/v1/incidents/${encodeURIComponent(id)}/comments?page=${page}&size=50`),
  addComment: (id: string, body: string) => request<CommentView>(`/api/v1/incidents/${encodeURIComponent(id)}/comments`, { method: 'POST', body: json({ body }) }),
  dashboardSummary: (params: URLSearchParams) => request<DashboardSummary>(`/api/v1/dashboard/summary?${params.toString()}`),
  triageQueue: (params: URLSearchParams) => request<PageResponse<IncidentView>>(`/api/v1/dashboard/triage-queue?${params.toString()}`),
  departmentPerformance: (cityId?: string) => request<DepartmentPerformance[]>(`/api/v1/dashboard/department-performance${cityId ? `?cityId=${encodeURIComponent(cityId)}` : ''}`),
  assign: (id: string, departmentId: string, note: string) => request<IncidentView>(`/api/v1/incidents/${encodeURIComponent(id)}/assignment`, { method: 'POST', headers: { 'Idempotency-Key': uuidKey() }, body: json({ departmentId, note }) }),
  changeStatus: (id: string, status: IncidentStatus, note: string) => request<IncidentView>(`/api/v1/incidents/${encodeURIComponent(id)}/status`, { method: 'POST', headers: { 'Idempotency-Key': uuidKey() }, body: json({ status, note }) }),
  triage: (id: string, body: { category?: IncidentCategory; severity?: Severity; safetyCritical?: boolean; note?: string }) => request<IncidentView>(`/api/v1/incidents/${encodeURIComponent(id)}/triage`, { method: 'PATCH', body: json(body) }),
  rewards: () => request<RewardSummary>('/api/v1/me/rewards'),
}
