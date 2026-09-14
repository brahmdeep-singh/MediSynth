# MediSynth

**MediSynth** is a hospital-focused healthcare software project aimed at supporting healthcare data management and privacy-preserving synthetic data generation.

This repository contains the project documentation, design diagrams, research material, project reports, and the current **frontend prototype** of the MediSynth application.

> **Current Status:** Frontend prototype stage. The backend and database have not been implemented yet.

---

## 📌 Project Overview

MediSynth is being developed as a healthcare-oriented platform with the goal of providing a structured interface for hospital-related workflows and synthetic healthcare data generation.

The current development phase focuses primarily on designing and implementing the application's frontend and documenting the overall system architecture.

The planned system will eventually include:

- User authentication and authorization
- Hospital and patient data management
- Synthetic healthcare data generation
- Dataset management
- Privacy-preserving data workflows
- Role-based access
- Healthcare data analysis and visualization

---

## 🖥️ Current Implementation

The current repository contains the **frontend prototype** of MediSynth.

The frontend focuses on the user interface, navigation, page layouts, forms, and interaction flows required for the application.

### Current frontend areas include

- Login interface
- Registration interface
- Dashboard
- Navigation
- Hospital-related interfaces
- Patient-related interfaces
- Synthetic data generation interface
- Dataset-related screens
- Reusable UI components

Some interactions are currently implemented using **frontend-only or mock logic**.

For example, the login page provides the user interface for entering credentials, but there is currently **no backend authentication service connected to it**.

---

## 🛠️ Technology Stack

### Frontend

- **HTML5**
- **CSS3**
- **JavaScript**
- **React.js**
- **Axios**

### Planned Backend

The backend is planned for a future development phase using:

- **Java**
- **Spring Boot**
- **Spring Security**
- **REST APIs**
- **JWT Authentication**
- **BCrypt Password Hashing**

### Planned Data Processing

The synthetic data component is planned to use:

- **Python**
- **Pandas**
- Statistical sampling techniques

---

## 📁 Repository Structure

```text
MediSynth/
│
├── .github/
│   └── GitHub-related configuration
│
├── assets/
│   └── Project images and other resources
│
├── code/
│   └── Current application source code
│       └── Frontend prototype
│
├── diagrams/
│   └── UML and system design diagrams
│
├── docs/
│   └── Project documentation
│
├── journals/
│   └── Development and research journals
│
├── project-proposal/
│   └── Project proposal documents
│
├── project-report-final/
│   └── Final project report
│
├── project-report-prototype-stage/
│   └── Prototype-stage project report
│
├── .gitignore
├── LICENSE
├── Makefile
├── mkdocs.yml
├── pyproject.toml
└── README.md
```

---

## 💻 Running the Frontend

### Prerequisites

Make sure the following are installed:

- Node.js
- npm

### Installation

Clone the repository:

```bash
git clone <repository-url>
```

Navigate to the project directory:

```bash
cd MediSynth
```

Then navigate to the frontend source directory inside `code/`.

```bash
cd code
```

Install the required dependencies:

```bash
npm install
```

Start the development server:

```bash
npm start
```

The application will normally be available at:

```text
http://localhost:3000
```

> The exact command and directory may change depending on the current React project configuration inside `code/`.

---

## 🔐 Authentication Status

Authentication is currently **frontend-only**.

The current prototype includes login and registration interfaces, but:

- No backend authentication API is connected.
- No database is connected.
- User credentials are not validated against a server.
- JWT tokens are not generated.
- BCrypt password hashing is not implemented.

These features will be added during backend development.

### Planned Authentication Architecture

```text
                    MediSynth
                       │
                       ▼
                React.js Frontend
                       │
                     Axios
                       │
                       ▼
              Spring Boot Backend
                       │
              ┌────────┴────────┐
              │                 │
        Authentication       Database
              │
          JWT / Security
```

---

## 🧪 Synthetic Data Generation

Synthetic healthcare data generation is one of the planned core features of MediSynth.

The intended workflow is:

```text
Healthcare Dataset
        │
        ▼
Data Preprocessing
        │
        ▼
Statistical Analysis
        │
        ▼
Synthetic Data Generation
        │
        ▼
Validation
        │
        ▼
Synthetic Dataset
```

The current frontend provides the interface for this planned functionality. The actual generation and processing functionality will be implemented in a later development phase.

---

## 📊 Development Status

| Component | Status |
|---|---|
| Project documentation | 🟢 Available |
| UML/System diagrams | 🟢 Available |
| React.js frontend | 🟢 Prototype |
| UI/UX | 🟢 Implemented |
| Login interface | 🟢 Implemented |
| Registration interface | 🟢 Implemented |
| Dashboard | 🟢 Prototype |
| Hospital interfaces | 🟢 Prototype |
| Patient interfaces | 🟢 Prototype |
| Synthetic data UI | 🟢 Prototype |
| Backend | 🔴 Not implemented |
| Database | 🔴 Not implemented |
| REST APIs | 🔴 Not implemented |
| Real authentication | 🔴 Not implemented |
| JWT authentication | 🔴 Not implemented |
| Python synthetic-data engine | 🟡 Planned |
| Frontend–backend integration | 🟡 Planned |

---

## 🎯 Future Development

The planned development roadmap is:

### Phase 1 — Frontend Prototype
- Design application interfaces
- Implement React.js components
- Implement navigation and routing
- Create dashboard and healthcare modules
- Develop synthetic-data generation UI

### Phase 2 — Backend Development
- Develop Java Spring Boot backend
- Create REST APIs
- Implement authentication
- Add JWT-based authorization
- Implement BCrypt password hashing

### Phase 3 — Database Integration
- Design database schema
- Connect backend to database
- Implement CRUD operations
- Store users and application data securely

### Phase 4 — Synthetic Data Generation
- Implement data preprocessing
- Implement statistical analysis
- Develop synthetic data generation
- Validate generated datasets
- Integrate the generation module with the application

### Phase 5 — Testing & Deployment
- Frontend testing
- Backend testing
- API testing
- Security testing
- System integration testing
- Deployment

---

## 📚 Project Documentation

Additional project material is organized into separate directories:

- **`diagrams/`** — UML and system design diagrams
- **`docs/`** — General project documentation
- **`journals/`** — Development/research journals
- **`project-proposal/`** — Project proposal
- **`project-report-prototype-stage/`** — Prototype-stage report
- **`project-report-final/`** — Final project report
- **`assets/`** — Images and supporting resources

---

## ⚠️ Current Limitations

The current version is a **frontend prototype** and should not be considered a complete production healthcare system.

Currently:

- Backend services are not available.
- Database integration is not available.
- Authentication is not connected to a server.
- Patient data is not stored through a backend.
- Synthetic data is not generated through a production data engine.
- Security mechanisms such as JWT and BCrypt are planned but not implemented.

No real sensitive patient information should be used with the current prototype.

---

## 👨‍💻 Project

**MediSynth**  
Hospital-focused Synthetic Healthcare Data Platform

**Current Development Stage:** Frontend Prototype

---

## 📄 License

This project is currently developed for academic/project purposes.

See [`LICENSE`](LICENSE) for licensing information.