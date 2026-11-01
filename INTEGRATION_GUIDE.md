# CivicLens AI — frontend/backend integration guide

This package aligns the React/Vite frontend with the Spring Boot API. It preserves the frontend's existing visual system and uses the backend as the source of truth for authentication, incidents, AI analysis, duplicate review, community activity, department workflow and rewards.

## Architecture

- Frontend: React 19 + TypeScript + Vite (`frontend/`, normally `http://localhost:5173`).
- Backend: Java 21 + Spring Boot (`backend/`, normally `http://localhost:8080`).
- Persistence: PostgreSQL with Flyway migrations.
- AI: Gemini incident analysis, with the backend's rule-based fallback when Gemini is unavailable.
- Geocoding: server-side Nominatim adapter for explicit user-triggered searches and reverse-geocoding. The adapter caches results and rate-limits outbound requests within one backend instance.

## Important product boundaries

1. CivicLens supports worldwide locations by resolving geocoder-returned settlements into its own location registry. It does not pre-load every city on Earth.
2. Department records created during location resolution are **internal CivicLens triage queues**. They are not actual municipal departments, government partners, or live government integrations.
3. AI recommendations are suggestions. Authorized officials remain responsible for verifying triage and taking workflow actions.
4. Report attachments are local browser previews only. The backend does not yet have a persistent evidence upload/storage endpoint.
5. The backend does not expose a global citizen leaderboard. The UI shows the authenticated user's real reward balance and ledger instead of fabricated global ranks.

## Backend configuration

Copy `backend/.env.example` values into the environment used to launch Spring Boot. Do not commit real secrets.

Required values for a normal local run:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` (use a cryptographically random value of at least 32 bytes)
- `GEMINI_API_KEY` (optional for rule-based fallback testing, required for a live Gemini call)

Optional but recommended for the demo:

- `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`: create the first SYSTEM_ADMIN only when no system administrator exists. The password must be at least 12 characters. There is no built-in admin credential. Set these before the first Spring Boot start if you need to provision department officials from the UI; the SYSTEM_ADMIN can then create DEPARTMENT_ADMIN accounts for each city's internal queues.
- `BOOTSTRAP_ADMIN_NAME`: display name for the first system administrator.
- `GEOCODING_USER_AGENT`: identifies CivicLens to the selected geocoding provider. Configure a meaningful application identifier and contact URL for hosted use.
- `CORS_ALLOWED_ORIGINS`: include the exact frontend origin used by Vite.

The embedded PostgreSQL launcher is in Maven's test scope and writes local data to `backend/target/local-pg`. Do not run `mvnw clean` while you need to preserve that local database. If another process occupies port 5432, choose a free port such as 55432 and keep `LOCAL_DB_PORT`, `DB_URL`, `LOCAL_DB_PASSWORD` and `DB_PASSWORD` consistent.

### Run embedded PostgreSQL (local development only)

From `backend/`, with `LOCAL_DB_PASSWORD` and `LOCAL_DB_PORT` set:

```powershell
.\mvnw.cmd -q test-compile exec:java@local-db
```

Leave that terminal running. In a second terminal, set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY` and other required environment values, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Check `http://localhost:8080/actuator/health` and `http://localhost:8080/swagger-ui/index.html`.

## Frontend setup

From `frontend/`:

```powershell
Copy-Item .env.example .env
npm install
npm run dev
```

Set `VITE_API_BASE_URL` in `frontend/.env` if the backend is not at `http://localhost:8080`. Vite environment variables are public browser configuration; never put Gemini, JWT signing, or database secrets in a `VITE_` variable.

## Global location flow

1. The citizen manually enters a place query and clicks **Search worldwide locations**, or explicitly selects **Use my location**.
2. The frontend calls `GET /api/v1/locations/search?query=...` or `GET /api/v1/locations/reverse?latitude=...&longitude=...`.
3. The backend geocoding adapter calls the configured provider, normalizes the address fields, caches results, and applies a shared one-request-per-1.1-second throttle per backend process.
4. After the user selects a result while signed in, the frontend calls authenticated `POST /api/v1/cities/resolve` to create or reuse a stable CivicLens city/location record and its default internal triage queues. Location search itself is public; creating registry entries requires authentication.
5. Incident submission uses the returned city UUID.

The default public provider is OpenStreetMap Nominatim. Its public endpoint is capacity-limited: no more than one request per second across the whole application, requires an identifying User-Agent or Referer and visible attribution, and prohibits autocomplete/type-ahead requests. The frontend therefore uses an explicit search button, not a keystroke autocomplete. The backend proxy includes app identification, caching, and a process-wide request throttle. This throttle is not distributed across multiple backend replicas; do not run multiple replicas against the public endpoint. For higher volume, move to a contracted provider or self-host a geocoder. Read the current policy before deploying: https://operations.osmfoundation.org/policies/nominatim/

Attribution displayed in the frontend: © OpenStreetMap contributors, https://www.openstreetmap.org/copyright.

## API integration implemented in the frontend

- Authentication: citizen registration/login, official login with server-side role verification, session restore, logout.
- Location: global search/reverse lookup and dynamic location resolution.
- Incidents: paginated feed, detail, report creation and idempotency key.
- Duplicate review: preflight candidate review plus handling of a duplicate conflict returned by creation.
- AI: analysis status/result, Gemini vs rule-based fallback labelling, retry for authorized users.
- Community: persistent votes, comments and incident history.
- Official workflow: dashboard summary, triage queue, department performance, assignment, verified triage, allowed status transitions.
- Rewards: personal balance and reward ledger.

The UI relies on the backend's `availableActions` list to show status/assignment/triage actions that the current user is allowed to take. Backend authorization remains authoritative and is re-checked server-side.

## Global city schema

`V3__global_location_registry.sql` adds ISO country code and normalized location key without editing the already-applied V1/V2 migrations. The location resolver reuses matching city records and creates default queues when needed. Existing seeded locations are preserved. Default department queue codes are aligned with the V2 seed (`ROADS`, `ELECTRICAL`, `SANITATION`, `WATER`, `DRAINAGE`, `PARKS`).

## Verification checklist

From `backend/`:

```powershell
.\mvnw.cmd test
```

From `frontend/`:

```powershell
npm run build
npm run lint
```

Then run the end-to-end demo:

1. Bootstrap one SYSTEM_ADMIN with environment variables.
2. Register a citizen and log in.
3. Search/select a city outside the original four seeded cities; verify location resolution returns a city UUID and queues.
4. Submit a report; review possible duplicates if returned.
5. Verify analysis shows either validated Gemini output or explicitly labelled rule-based fallback.
6. Upvote/comment from another citizen account.
7. Use SYSTEM_ADMIN to provision a DEPARTMENT_ADMIN for the chosen city's internal queue.
8. Assign, verify triage and update status with required notes.
9. Verify incident history and the reporter's reward ledger.

Do not describe the integrated application as end-to-end verified until the tests and this live flow have actually been run on the target machine.
