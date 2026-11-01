# CivicLens AI — frontend

React + TypeScript + Vite client for the CivicLens AI global civic reporting platform.

## Run locally

```powershell
Copy-Item .env.example .env
npm install
npm run dev
```

By default, the frontend connects to `http://localhost:8080`. Override this with `VITE_API_BASE_URL` in `.env` when needed. `VITE_` values are exposed to the browser; never put database passwords, Gemini API keys, or JWT signing secrets in them.

## Integrated features

- Citizen registration and login; official login with backend role verification.
- Worldwide explicit location search and GPS reverse lookup through the backend.
- Persistent incident feed, report creation, duplicate review and AI analysis.
- Persistent votes, comments and incident history.
- Department dashboard, triage, assignment, allowed status changes and department metrics.
- Personal reward balance and activity ledger.

Attachments currently remain local previews; this backend does not yet provide file upload/storage. A global citizen leaderboard is not currently provided by the API, so the application does not fabricate one.

See `../INTEGRATION_GUIDE.md` for backend configuration, geocoding policy boundaries, global location behavior and the end-to-end test checklist.
