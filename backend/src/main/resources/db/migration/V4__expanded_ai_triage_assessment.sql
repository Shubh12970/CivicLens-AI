-- Persist richer Gemini incident-assessment output. Existing analyses remain valid with nullable new fields.
ALTER TABLE incident_analyses ADD COLUMN thinking_level VARCHAR(10);
ALTER TABLE incident_analyses ADD COLUMN impact_summary VARCHAR(700);
ALTER TABLE incident_analyses ADD COLUMN risk_rationale VARCHAR(700);
ALTER TABLE incident_analyses ADD COLUMN recommended_actions TEXT;
ALTER TABLE incident_analyses ADD COLUMN clarifying_questions TEXT;
ALTER TABLE incident_analyses ADD COLUMN citizen_guidance VARCHAR(600);
ALTER TABLE incident_analyses ADD COLUMN evidence_suggestions TEXT;
