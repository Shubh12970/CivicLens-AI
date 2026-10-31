# CivicLens AI

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=rect&color=0:052E16,45:166534,100:10B981&height=220&section=header&text=CivicLens%20AI&fontSize=58&fontColor=ECFDF5&animation=fadeIn&fontAlignY=40&desc=Intelligent%20Civic%20Grievance%20%26%20Infrastructure%20Triage&descSize=17&descAlignY=62&descColor=D1FAE5" width="100%" alt="CivicLens AI header"/>

<br/>

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=500&size=19&duration=3000&pause=1000&color=10B981&center=true&vCenter=true&width=750&lines=Report+Smarter.+Act+Transparently.;AI-Powered+Civic+Intelligence.;Building+More+Accountable+Communities." alt="Animated CivicLens AI tagline"/>

<br/><br/>

[![Hackathon](https://img.shields.io/badge/Forge26-Hackathon-166534?style=for-the-badge&labelColor=052E16)](https://github.com/)
![Domain](https://img.shields.io/badge/Domain-CivicTech-047857?style=for-the-badge&labelColor=052E16)
![AI](https://img.shields.io/badge/AI-Gemini-10B981?style=for-the-badge&labelColor=052E16)

**Making civic issues visible, actionable, and accountable.**

</div>

---

## Overview

**CivicLens AI** is an AI-assisted civic engagement platform designed to make reporting and tracking public infrastructure problems more transparent and organized.

From potholes and damaged roads to broken streetlights, overflowing garbage, water leakage, and drainage issues, CivicLens AI provides a centralized workflow for citizens to report problems, discover similar incidents, participate in community discussions, and track progress.

By combining AI-assisted incident intelligence with explainable duplicate detection and auditable departmental workflows, CivicLens AI aims to bridge the gap between **citizen participation and accountable civic action**.

## The Problem

Traditional civic grievance processes can be fragmented, repetitive, and difficult to track.

- **Fragmented reporting:** Civic issues are spread across multiple channels.
- **Duplicate complaints:** Different citizens may report the same underlying incident.
- **Unclear prioritization:** Safety-critical issues can be lost among routine maintenance requests.
- **Limited transparency:** Citizens may struggle to understand the status of a complaint.
- **Departmental ambiguity:** Identifying the responsible department can be difficult.
- **Weak accountability:** Status changes and resolution progress may not be consistently visible.

## Our Approach

<div align="center">

| 01 · REPORT | 02 · ANALYZE | 03 · CONNECT | 04 · RESOLVE |
|:---:|:---:|:---:|:---:|
| Submit civic issues | AI-assisted classification | Detect duplicates and gather support | Track authorized actions |
| <img src="https://img.shields.io/badge/Citizen-065F46?style=flat-square" alt="Citizen"/> | <img src="https://img.shields.io/badge/Intelligence-047857?style=flat-square" alt="Intelligence"/> | <img src="https://img.shields.io/badge/Community-059669?style=flat-square" alt="Community"/> | <img src="https://img.shields.io/badge/Accountability-10B981?style=flat-square" alt="Accountability"/> |

</div>

## Core Capabilities

### Incident Intelligence

Uses a Gemini-powered intelligence module to interpret unstructured reports and suggest structured categories, severity levels, explanations, and responsible departments.

### Explainable Duplicate Detection

Identifies potentially similar incidents using available text, category, and geographic signals. Citizens review candidate matches instead of reports being automatically merged based on similarity alone.

### Community Participation

Provides the backend capabilities for city-specific feeds, incident voting, comments, and discovery of recent or trending issues.

### Department Workflow

Supports authorized assignment, validated status transitions, and incident history to help make departmental actions traceable.

### Accountability & Analytics

Provides dashboard aggregates based on stored data, department reviews under defined rules, and a ledger-based approach to participation rewards.

### Resilient AI Integration

Validates AI-generated output and handles provider failures explicitly, so an unavailable AI service does not silently erase a citizen's report.

---

## System Architecture

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=rect&color=0:052E16,100:166534&height=3" width="100%" alt="Green divider"/>

</div>

```mermaid
flowchart TD
    A["Citizen / Department Official"] --> B["React Frontend"]
    B --> C["Spring Boot REST API"]
    C --> D["Workflow Orchestrator"]

    D --> E["Incident Intelligence"]
    D --> F["Duplicate Detection Service"]
    D --> G["Civic Workflow & Accountability"]

    E <--> H["Google Gemini API"]
    E --> I[("PostgreSQL")]
    F <--> I
    G <--> I
    C --> J["Authentication & Authorization"]

    style A fill:#D1FAE5,stroke:#059669,color:#064E3B
    style B fill:#ECFDF5,stroke:#10B981,color:#064E3B
    style C fill:#DCFCE7,stroke:#16A34A,color:#14532D
    style D fill:#BBF7D0,stroke:#15803D,color:#14532D
    style E fill:#A7F3D0,stroke:#059669,color:#064E3B
    style F fill:#D1FAE5,stroke:#047857,color:#064E3B
    style G fill:#86EFAC,stroke:#166534,color:#14532D
    style H fill:#ECFDF5,stroke:#059669,color:#064E3B
    style I fill:#DCFCE7,stroke:#166534,color:#14532D
    style J fill:#D1FAE5,stroke:#047857,color:#064E3B
```

### Three Core Responsibilities

| Module | Responsibility |
|---|---|
| Incident Intelligence Agent | Uses an LLM to analyze complaints and provide validated, explainable recommendations. |
| Duplicate Detection Service | Uses deterministic matching and geographic signals when available to identify potential duplicates. |
| Civic Workflow & Accountability Service | Enforces permissions, department assignment rules, status transitions, audit history, and reward eligibility. |

These are three logical modules, not necessarily three autonomous AI agents. The architecture intentionally reserves LLM reasoning for tasks that benefit from language understanding.

## Technology Stack

<div align="center">

<img src="https://skillicons.dev/icons?i=react,ts,java,spring,postgres,docker,maven,git,github&theme=light" alt="Technology stack icons"/>

<br/><br/>

![Gemini](https://img.shields.io/badge/Google_Gemini-AI_Integration-166534?style=flat-square&logo=googlegemini&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-Authentication-166534?style=flat-square&logo=springsecurity&logoColor=white)
![JUnit](https://img.shields.io/badge/JUnit_5-Testing-166534?style=flat-square&logo=junit5&logoColor=white)

</div>

| Layer | Technology |
|---|---|
| Frontend | React, TypeScript |
| Backend | Java, Spring Boot |
| API | REST, OpenAPI / Swagger |
| Database | PostgreSQL |
| Persistence | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| AI | Google Gemini API |
| Security | Spring Security, BCrypt, JWT if implemented |
| Testing | JUnit 5, Mockito, integration testing |
| Infrastructure | Docker, Docker Compose |

*The final repository should list only technologies actually used by the implementation.*

## Incident Lifecycle

```mermaid
flowchart TD
    A["Citizen submits report"] --> B["Validate request"]
    B --> C["Check for potential duplicates"]
    C --> D{"Existing incident match?"}

    D -->|Possible match| E["Show candidates to citizen"]
    E --> F{"Same incident?"}
    F -->|Yes| G["Open existing incident"]
    F -->|No| H["Continue new report"]

    D -->|No match| H
    H --> I["Persist incident"]
    I --> J["Run AI analysis"]
    J --> K["Validate and store analysis"]
    K --> L["Department review and assignment"]
    L --> M["Authorized status updates"]
    M --> N["Persist audit history"]
    N --> O["Refresh feed and dashboard"]

    style A fill:#D1FAE5,stroke:#059669,color:#064E3B
    style I fill:#BBF7D0,stroke:#15803D,color:#14532D
    style J fill:#A7F3D0,stroke:#059669,color:#064E3B
    style L fill:#86EFAC,stroke:#166534,color:#14532D
    style N fill:#DCFCE7,stroke:#166534,color:#14532D
    style O fill:#D1FAE5,stroke:#059669,color:#064E3B
```

AI analysis may be asynchronous depending on the final implementation. If analysis fails, the system should expose an appropriate pending or failed state while preserving the report.

## Security & Reliability

- Role-based access control for citizen and department operations.
- Server-side validation of requests and AI-generated output.
- Secure password hashing and environment-based secrets.
- Validated status transitions and auditable changes.
- Duplicate-resistant voting and reward operations.
- Structured error responses and diagnostic logging.
- Separation of public incident information from private account data.
- Explicit handling of external AI service failures.

## Getting Started

The commands below are examples. Follow the final repository's actual directory structure, environment variables, and setup instructions.

### Prerequisites

- Java 21 or the version specified by the backend.
- Maven or the included Maven wrapper.
- PostgreSQL or Docker with Docker Compose.
- Node.js and npm for the frontend.
- A Gemini API key for live AI analysis, if enabled.

### Run the Backend

```powershell
# Start PostgreSQL if Docker Compose is configured
docker compose up -d

# Enter the backend directory
cd backend

# Start Spring Boot on Windows
.\mvnw.cmd spring-boot:run
```

### Run the Frontend

```bash
cd frontend
npm install
npm run dev
```

### Environment Configuration

Use the provided `.env.example` and the backend documentation to configure the required variables, such as database credentials, the Gemini API key, JWT secret, server port, and frontend origin.

**Never commit real credentials or `.env` files to GitHub.**

## Testing

The backend should be tested for:

- Incident creation and retrieval.
- Duplicate detection and matching edge cases.
- AI output validation and provider failures.
- Authentication and role-based permissions.
- Voting, comments, and reward eligibility.
- Department assignment and status transitions.
- Audit history consistency.
- Dashboard aggregation and database persistence.

Refer to the project's test report for commands actually executed and verified results.

## Current Status

> Update this section as implementation progresses. Do not claim completion until the feature has been tested.

- [ ] Backend implementation verified
- [ ] Frontend integration verified
- [ ] Gemini integration verified
- [ ] Duplicate detection tested
- [ ] Department workflow tested
- [ ] End-to-end demo verified

## Future Scope

- Interactive geographic incident discovery.
- Multilingual reporting.
- Image-assisted damage classification.
- Verified municipal system integrations.
- Notifications and status subscriptions.
- Advanced civic analytics and abuse prevention.

These are potential extensions, not claims about existing functionality.

## Team

**Built for Forge26 Hackathon**

CivicLens AI explores how AI-assisted reasoning, community participation, and auditable workflows can help make civic issue management more transparent.

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=rect&color=0:052E16,50:166534,100:10B981&height=100&section=footer&text=Report%20Smarter.%20Act%20Transparently.&fontSize=23&fontColor=ECFDF5&animation=fadeIn" width="100%" alt="CivicLens AI footer"/>

<sub>Building more accountable communities through technology.</sub>

</div>
