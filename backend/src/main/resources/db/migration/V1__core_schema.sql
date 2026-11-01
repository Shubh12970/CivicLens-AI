-- CivicLens AI core schema. All timestamps are timestamptz and written in UTC by the application.
-- Enum-like columns are VARCHAR with CHECK constraints so the database rejects values the Java enums do not know.

CREATE TABLE cities (
    id              UUID PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    state           VARCHAR(120),
    country         VARCHAR(80)  NOT NULL DEFAULT 'India',
    center_latitude  DOUBLE PRECISION,
    center_longitude DOUBLE PRECISION,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE departments (
    id          UUID PRIMARY KEY,
    city_id     UUID         NOT NULL REFERENCES cities (id) ON DELETE RESTRICT,
    code        VARCHAR(60)  NOT NULL,
    name        VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_department_city_code UNIQUE (city_id, code),
    -- Target for the composite FK below so a category mapping can never point at another city's department.
    CONSTRAINT uq_department_id_city UNIQUE (id, city_id)
);

-- Explicit city-to-department routing: which department handles a category in a city.
-- At most one department per (city, category). Categories without a row go to manual triage.
CREATE TABLE department_categories (
    city_id       UUID        NOT NULL,
    category      VARCHAR(40) NOT NULL,
    department_id UUID        NOT NULL,
    PRIMARY KEY (city_id, category),
    CONSTRAINT fk_depcat_department FOREIGN KEY (department_id, city_id)
        REFERENCES departments (id, city_id) ON DELETE RESTRICT,
    CONSTRAINT ck_depcat_category CHECK (category IN ('ROAD_DAMAGE','STREETLIGHT','GARBAGE','WATER_LEAKAGE',
        'DRAINAGE','FLOODING','SEWAGE','ELECTRICAL_HAZARD','TREE_FALL','TRAFFIC_SIGNAL','PUBLIC_PROPERTY_DAMAGE','OTHER'))
);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(60)  NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    department_id UUID REFERENCES departments (id) ON DELETE RESTRICT,
    home_city_id  UUID REFERENCES cities (id) ON DELETE SET NULL,
    token_version INTEGER      NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_user_role CHECK (role IN ('CITIZEN','DEPARTMENT_ADMIN','SYSTEM_ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE','DELETED')),
    -- A department admin must belong to exactly one department; other roles must not.
    CONSTRAINT ck_user_department CHECK ((role = 'DEPARTMENT_ADMIN') = (department_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE incidents (
    id                         UUID PRIMARY KEY,
    city_id                    UUID          NOT NULL REFERENCES cities (id) ON DELETE RESTRICT,
    reporter_id                UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    title                      VARCHAR(120)  NOT NULL,
    description                VARCHAR(2000) NOT NULL,
    normalized_text            VARCHAR(2200) NOT NULL,
    locality                   VARCHAR(120),
    address_text               VARCHAR(250),
    latitude                   DOUBLE PRECISION,
    longitude                  DOUBLE PRECISION,
    category                   VARCHAR(40)   NOT NULL,
    category_source            VARCHAR(20)   NOT NULL,
    severity                   VARCHAR(20),
    severity_source            VARCHAR(30),
    safety_critical            BOOLEAN       NOT NULL DEFAULT FALSE,
    hazard_reported            BOOLEAN       NOT NULL DEFAULT FALSE,
    status                     VARCHAR(20)   NOT NULL,
    department_id              UUID REFERENCES departments (id) ON DELETE RESTRICT,
    recommended_department_id  UUID REFERENCES departments (id) ON DELETE SET NULL,
    recommended_department_source VARCHAR(30),
    duplicate_of_id            UUID REFERENCES incidents (id) ON DELETE SET NULL,
    resolution_note            VARCHAR(2000),
    demo_data                  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMPTZ   NOT NULL,
    updated_at                 TIMESTAMPTZ   NOT NULL,
    status_changed_at          TIMESTAMPTZ   NOT NULL,
    resolved_at                TIMESTAMPTZ,
    version                    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_incident_category CHECK (category IN ('ROAD_DAMAGE','STREETLIGHT','GARBAGE','WATER_LEAKAGE',
        'DRAINAGE','FLOODING','SEWAGE','ELECTRICAL_HAZARD','TREE_FALL','TRAFFIC_SIGNAL','PUBLIC_PROPERTY_DAMAGE','OTHER')),
    CONSTRAINT ck_incident_category_source CHECK (category_source IN ('CITIZEN','AI_SUGGESTED','RULE_BASED','OFFICIAL')),
    CONSTRAINT ck_incident_severity CHECK (severity IS NULL OR severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_incident_severity_source CHECK (severity_source IS NULL OR severity_source IN ('AI_SUGGESTED','RULE_BASED','OFFICIAL')),
    CONSTRAINT ck_incident_severity_pair CHECK ((severity IS NULL) = (severity_source IS NULL)),
    CONSTRAINT ck_incident_status CHECK (status IN ('SUBMITTED','UNDER_REVIEW','ASSIGNED','IN_PROGRESS','RESOLVED','REJECTED','REOPENED')),
    CONSTRAINT ck_incident_recommendation_source CHECK (recommended_department_source IS NULL
        OR recommended_department_source IN ('AI_SUGGESTED','CATEGORY_MAPPING')),
    CONSTRAINT ck_incident_lat CHECK (latitude IS NULL OR (latitude BETWEEN -90 AND 90)),
    CONSTRAINT ck_incident_lng CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180)),
    CONSTRAINT ck_incident_coords_pair CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_incident_not_self_duplicate CHECK (duplicate_of_id IS NULL OR duplicate_of_id <> id)
);
CREATE INDEX ix_incidents_city_created ON incidents (city_id, created_at DESC);
CREATE INDEX ix_incidents_city_status ON incidents (city_id, status);
CREATE INDEX ix_incidents_status ON incidents (status);
CREATE INDEX ix_incidents_category ON incidents (category);
CREATE INDEX ix_incidents_department ON incidents (department_id);
CREATE INDEX ix_incidents_reporter ON incidents (reporter_id);
CREATE INDEX ix_incidents_updated ON incidents (updated_at DESC);
CREATE INDEX ix_incidents_coords ON incidents (latitude, longitude) WHERE latitude IS NOT NULL;

-- Append-only audit timeline. No cascade delete: audit rows outlive any later change to the incident.
CREATE TABLE incident_events (
    id            UUID PRIMARY KEY,
    incident_id   UUID         NOT NULL REFERENCES incidents (id) ON DELETE RESTRICT,
    event_type    VARCHAR(30)  NOT NULL,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20),
    department_id UUID REFERENCES departments (id) ON DELETE RESTRICT,
    actor_id      UUID REFERENCES users (id) ON DELETE RESTRICT,
    actor_role    VARCHAR(30),
    note          VARCHAR(2000),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_event_type CHECK (event_type IN ('CREATED','STATUS_CHANGED','ASSIGNED','TRIAGE_UPDATED',
        'ANALYSIS_COMPLETED','ANALYSIS_FAILED','MARKED_DUPLICATE'))
);
CREATE INDEX ix_incident_events_incident ON incident_events (incident_id, created_at);

-- One row per analysis run. Older runs are kept so changes in AI output stay explainable.
CREATE TABLE incident_analyses (
    id                        UUID PRIMARY KEY,
    incident_id               UUID         NOT NULL REFERENCES incidents (id) ON DELETE RESTRICT,
    run_number                INTEGER      NOT NULL,
    status                    VARCHAR(20)  NOT NULL,
    source                    VARCHAR(30),
    provider                  VARCHAR(40),
    model                     VARCHAR(80),
    prompt_version            VARCHAR(20)  NOT NULL,
    attempt_count             INTEGER      NOT NULL DEFAULT 0,
    max_attempts              INTEGER      NOT NULL,
    next_attempt_at           TIMESTAMPTZ,
    lease_until               TIMESTAMPTZ,
    category                  VARCHAR(40),
    severity                  VARCHAR(20),
    confidence                DOUBLE PRECISION,
    safety_critical           BOOLEAN,
    explanation               VARCHAR(1000),
    safety_concerns           TEXT,
    missing_information       TEXT,
    recommended_next_action   VARCHAR(400),
    recommended_department_id UUID REFERENCES departments (id) ON DELETE SET NULL,
    validation_warnings       TEXT,
    last_error_code           VARCHAR(40),
    last_error_message        VARCHAR(500),
    requested_by_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    completed_at              TIMESTAMPTZ,
    CONSTRAINT uq_analysis_run UNIQUE (incident_id, run_number),
    CONSTRAINT ck_analysis_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    CONSTRAINT ck_analysis_source CHECK (source IS NULL OR source IN ('GEMINI','RULE_BASED_FALLBACK')),
    CONSTRAINT ck_analysis_confidence CHECK (confidence IS NULL OR (confidence BETWEEN 0 AND 1))
);
-- At most one active (pending or processing) analysis per incident: blocks duplicate concurrent processing.
CREATE UNIQUE INDEX uq_analysis_one_active ON incident_analyses (incident_id) WHERE status IN ('PENDING','PROCESSING');
CREATE INDEX ix_analysis_due ON incident_analyses (status, next_attempt_at);

CREATE TABLE votes (
    incident_id UUID        NOT NULL REFERENCES incidents (id) ON DELETE RESTRICT,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    value       SMALLINT    NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (incident_id, user_id),
    CONSTRAINT ck_vote_value CHECK (value IN (-1, 1))
);
CREATE INDEX ix_votes_user ON votes (user_id);

CREATE TABLE comments (
    id                UUID PRIMARY KEY,
    incident_id       UUID          NOT NULL REFERENCES incidents (id) ON DELETE RESTRICT,
    author_id         UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    body              VARCHAR(2000) NOT NULL,
    moderation_status VARCHAR(20)   NOT NULL DEFAULT 'VISIBLE',
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_comment_moderation CHECK (moderation_status IN ('VISIBLE','DELETED','HIDDEN'))
);
CREATE INDEX ix_comments_incident ON comments (incident_id, created_at);

CREATE TABLE department_reviews (
    id            UUID PRIMARY KEY,
    incident_id   UUID         NOT NULL UNIQUE REFERENCES incidents (id) ON DELETE RESTRICT,
    department_id UUID         NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    reviewer_id   UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    rating        SMALLINT     NOT NULL,
    review_text   VARCHAR(1000),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX ix_reviews_department ON department_reviews (department_id, created_at DESC);

-- Reward ledger. Balances are always SUM(points); source_key makes every qualifying event pay out at most once.
CREATE TABLE reward_transactions (
    id                UUID PRIMARY KEY,
    user_id           UUID REFERENCES users (id) ON DELETE RESTRICT,
    department_id     UUID REFERENCES departments (id) ON DELETE RESTRICT,
    reason            VARCHAR(40)  NOT NULL,
    points            INTEGER      NOT NULL,
    incident_id       UUID REFERENCES incidents (id) ON DELETE RESTRICT,
    source_key        VARCHAR(200) NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_reward_recipient CHECK ((user_id IS NULL) <> (department_id IS NULL)),
    CONSTRAINT ck_reward_reason CHECK (reason IN ('REPORT_ACCEPTED','REPORT_RESOLVED','REVIEW_SUBMITTED',
        'DEPARTMENT_RESOLUTION','DEPARTMENT_POSITIVE_REVIEW'))
);
CREATE INDEX ix_rewards_user ON reward_transactions (user_id, created_at DESC);
CREATE INDEX ix_rewards_department ON reward_transactions (department_id);

-- Stored responses for client retries that carry an Idempotency-Key header.
CREATE TABLE idempotency_records (
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    scope           VARCHAR(60)  NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INTEGER      NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, idempotency_key)
);
CREATE INDEX ix_idempotency_created ON idempotency_records (created_at);
