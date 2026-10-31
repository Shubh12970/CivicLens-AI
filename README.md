# CivicLens AI

**See a problem. Make it matter.**

CivicLens AI is a civic engagement platform designed to help citizens report local issues, support community priorities, and track progress toward safer, cleaner, and better cities.

The project aims to make civic participation more accessible through a clean, intuitive interface and AI-assisted workflows.

## Features

- **Civic Issue Reporting** — A demonstration interface for reporting local problems.
- **Community Voting** — Support issues through a minimal thumbs-up interaction.
- **Light and Dark Themes** — A sun-and-moon theme switch with a consistent green, mint, and aqua colour palette.
- **Responsive Interface** — A modern React and TypeScript frontend.
- **AI-Assisted Civic Workflows** — Planned integration for helping classify and process reported issues.
- **Backend Integration** — Planned Spring Boot API and database integration.

## Tech Stack

**Frontend**
- React
- TypeScript
- Vite
- CSS

**Planned Backend and AI**
- Java
- Spring Boot
- PostgreSQL
- AI-assisted processing and automation

## Getting Started

### Prerequisites

- Node.js
- npm
- Git

### Installation

Clone the repository:

```bash
git clone https://github.com/Shubh12970/CivicLens-AI.git
cd CivicLens-AI
```

Navigate to the frontend:

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

Start the development server:

```bash
npm run dev
```

Open the local URL displayed in your terminal.

### Production Build

To verify the frontend build:

```bash
npm run build
```

## Project Structure

```text
CivicLens-AI/
├── frontend/
│   ├── src/
│   │   ├── assets/
│   │   ├── App.tsx
│   │   ├── App.css
│   │   ├── index.css
│   │   └── main.tsx
│   ├── package.json
│   └── index.html
├── .gitignore
└── README.md
```

Additional backend and AI service directories can be added as those components are developed.

## Current Status

The frontend prototype has passed the following UI checks:

- Page loading
- Sun/moon theme toggle
- Green palette in light and dark modes
- Thumbs-up voting interaction
- Demonstration report form

**Note:** These checks cover the tested frontend interactions. Report submission and voting are currently demonstration features; persistent storage, backend APIs, and production AI integrations require further implementation and testing.

## Roadmap

- [ ] Complete the civic reporting experience
- [ ] Integrate the Spring Boot backend
- [ ] Connect PostgreSQL for persistent data
- [ ] Implement AI-assisted issue classification
- [ ] Connect voting to persistent records
- [ ] Add authentication and authorization
- [ ] Add automated tests and API validation
- [ ] Deploy the application

## Contributing

Contributions and constructive feedback are welcome. Please discuss significant changes with the project team before submitting them.

## License

A license has not yet been selected. All rights are reserved by default unless a license is added to this repository.
