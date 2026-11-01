# CivicLens AI assessment upgrade

This patch strengthens the existing Incident Intelligence module. It does not turn the duplicate detector or civic workflow engine into LLM agents; those remain deterministic by design.

## What changes

- Gemini 3.8 Flash is configured for `HIGH` thinking by default, with a larger output budget and longer request timeout.
- The prompt asks for an evidence-bounded, multi-part triage assessment: category, severity, safety flag, confidence estimate, explanation, impact summary, risk rationale, recommended internal queue, ordered official response plan, clarifying questions, missing information, safe citizen guidance and evidence suggestions.
- The response uses Gemini's current `generationConfig.responseFormat.text` JSON-schema contract.
- Model output is validated before persistence. Unsupported department IDs are ignored, implausible action claims are rejected/removed, lists are bounded, and a safety-critical assessment cannot remain LOW/MEDIUM.
- The analysis stores the model thinking level for auditability and persists the expanded fields via Flyway V4.
- The frontend shows the returned AI assessment and distinguishes `GEMINI` from `RULE_BASED_FALLBACK`.

## Environment variables

The defaults are already set in `application.yml`; override them only when needed:

```powershell
$env:GEMINI_MODEL = "gemini-3.8-flash"
$env:GEMINI_THINKING_LEVEL = "HIGH"
$env:GEMINI_MAX_OUTPUT_TOKENS = "8192"
$env:GEMINI_TIMEOUT = "PT90S"
```

Keep `GEMINI_API_KEY` private. Do not paste it into source code or commit it.

`HIGH` thinking increases latency and token use compared with `MEDIUM`. For a faster demo, set `GEMINI_THINKING_LEVEL=MEDIUM`; the chosen level is recorded on each completed Gemini analysis. The model's private thought tokens are not requested, logged, saved or shown—only the final validated assessment is exposed.

## Apply

1. Stop Spring Boot, but keep the embedded PostgreSQL window running.
2. Overlay the patch folders into the repository root `CivicLens-AI`.
3. Run backend tests from `backend`:

   ```powershell
   .\\mvnw.cmd test
   ```

   Do not use `clean test` while you need the local embedded database under `backend/target/local-pg`.
4. Restart Spring Boot. Flyway applies `V4__expanded_ai_triage_assessment.sql` automatically.
5. Restart/reload Vite if needed.

## Verify a real Gemini run

1. Log in as a citizen and submit a new report.
2. Open its incident details and wait while analysis is `PENDING` / `PROCESSING`.
3. The completed API response should report:
   - `source: "GEMINI"`
   - `aiGenerated: true`
   - `provider: "gemini"`
   - `model: "gemini-3.8-flash"` (unless overridden)
   - `thinkingLevel: "HIGH"` (unless overridden)
   - `promptVersion: "v2"`
4. Review the generated explanation, risk rationale, impact summary, action plan, clarification questions and citizen guidance.

If `source` is `RULE_BASED_FALLBACK`, the safety fallback worked but Gemini did not complete the request. Check `lastErrorCode` / `lastErrorMessage`, API-key validity, model access, quota and the backend log. `/api/v1/system/ai-status` is configuration/recent-outcome telemetry; it does not itself make a live Gemini call.

## Scope and boundaries

- Only Incident Intelligence uses Gemini. Duplicate/geospatial matching and civic workflow authorization remain deterministic; privileged assignment/status changes are never executed by the model.
- The current attachment UI is local preview only. Images are not stored or sent to Gemini in this patch.
- Confidence is a model estimate, not a calibrated probability. Human officials must verify category and severity.
- Gemini's free-tier availability, quota and pricing depend on the Google AI Studio account and current model terms.
