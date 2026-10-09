# Software Architecture Document

## Online Job Recruitment Platform Backend

---

# 1. Introduction / Purpose

## 1.1 Purpose

This document describes the software architecture of the Online Job Recruitment Platform. It defines the major system components, modules, technologies, data structures, communication methods, security mechanisms, deployment approach, and important architectural decisions.

The architecture is intended to provide a system that is simple to develop, maintain, and scale while supporting the functional requirements of job seekers, employers, and administrators.

## 1.2 Scope

The backend will provide functionality for:

- User registration and login
- Role-based access for Job Seekers, Employers, and Administrators
- Job seeker profile management
- CV and supporting document management
- Job creation and management by employers
- Job browsing and searching
- Job applications
- Application status management
- Administrator account management

The Backend will **not** include email, SMS, interview management, or notification.

---

# 2. System Overview

The system is an online recruitment platform connecting job seekers with employers.

There are three main types of users:

### Job Seeker

A Job Seeker can:

- Register and log in
- Create and update their profile
- Upload their CV and supporting documents
- Browse available jobs
- Search/filter jobs
- Apply for jobs
- View their applications and application statuses

### Employer

An Employer can:

- Register and log in
- Create job advertisements
- View their posted jobs
- View applications for their jobs
- Shortlist applicants
- Reject applicants

### Administrator

An Administrator can:

- View users
- Verify accounts
- Remove suspicious accounts

The system will use a REST API between the frontend and Spring Boot backend.

---

# 3. Architecture Diagram

The system will use a **modular monolithic architecture**.

```text
                         ┌───────────────────┐
                         │     Frontend      │
                         │   Web Application │
                         └─────────┬─────────┘
                                   │
                              HTTPS / REST
                                   │
                                   ▼
                 ┌─────────────────────────────────┐
                 │       Spring Boot Backend       │
                 │                                 │
                 │  ┌──────────┐   ┌────────────┐ │
                 │  │   Auth   │   │  Profile   │ │
                 │  └──────────┘   └────────────┘ │
                 │                                 │
                 │  ┌──────────┐   ┌────────────┐ │
                 │  │   Jobs   │   │Applications│ │
                 │  └──────────┘   └────────────┘ │
                 │                                 │
                 │  ┌──────────┐                   │
                 │  │  Admin   │                   │
                 │  └──────────┘                   │
                 │                                 │
                 │       JWT / Spring Security     │
                 └───────────────┬─────────────────┘
                                 │
                              JPA / JDBC
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │       PostgreSQL       │
                    │        Database        │
                    └────────────────────────┘

                    Docker / Docker Compose
```

The backend and PostgreSQL database will run as Docker containers.

---

# 4. Architecture Style / Pattern

## 4.1 Modular Monolithic Architecture

The backend will be implemented as a **modular monolith**.

This means that the system is deployed as one Spring Boot application, but internally it is divided into independent functional modules.

The main modules are:

```text
Authentication
Profile
Jobs
Applications
Administration
```

Each module contains its own:

```text
Controller
DTO
Entity
Repository
Service
```

This approach is preferred because the system does not currently require the complexity of microservices.

## 4.2 Layered Architecture

Each module will also follow a simple layered structure:

```text
Controller
     ↓
Service
     ↓
Repository
     ↓
Database
```

### Controller

Handles HTTP requests and responses.

### Service

Contains business logic and validation.

### Repository

Handles database operations through Spring Data JPA.

### Entity

Represents persistent data stored in PostgreSQL.

### DTO

Defines the data received from and returned to the frontend.

---

# 5. System Components / Modules

The backend will contain five main functional modules.

## 5.1 Authentication Module

Responsible for:

- User registration
- User login
- Password hashing
- JWT generation
- JWT validation
- User roles
- Authentication-related security

Roles:

```text
JOB_SEEKER
EMPLOYER
ADMIN
```

Main components:

```text
auth/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

---

## 5.2 Profile Module

Responsible for Job Seeker professional profiles.

Functions include:

- Creating a profile
- Updating a profile
- Viewing a profile
- Uploading CVs
- Uploading supporting documents

Main components:

```text
profile/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

---

## 5.3 Job Module

Responsible for job advertisements.

Functions include:

- Creating jobs
- Viewing jobs
- Searching jobs
- Filtering jobs
- Viewing an employer's posted jobs
- Determining whether a job is active or expired

Main components:

```text
job/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

A job contains information such as:

```text
Job
├── id
├── employer
├── title
├── description
├── requirements
├── deadline
└── createdAt
```

---

## 5.4 Application Module

Responsible for the job application process.

Functions include:

- Applying for a job
- Viewing a Job Seeker's applications
- Viewing applications for an employer's jobs
- Shortlisting applicants
- Rejecting applicants
- Tracking application status

Application statuses:

```text
PENDING
SHORTLISTED
REJECTED
```

Main components:

```text
application/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

The system will prevent:

- Applying for expired jobs
- Applying for the same job more than once
- An employer reviewing applications belonging to another employer's job

---

## 5.5 Administration Module

Responsible for administrator functions.

Functions include:

- Viewing users
- Verifying accounts
- Removing suspicious accounts

Main components:

```text
admin/
├── controller/
├── dto/
└── service/
```

The module will use the authentication and user information provided by the Auth module.

---

# 6. Technology Stack

| Layer                | Technology                  |
| -------------------- | --------------------------- |
| Backend              | Java                        |
| Backend Framework    | Spring Boot                 |
| API                  | REST                        |
| Security             | Spring Security             |
| Authentication       | JWT                         |
| ORM                  | Spring Data JPA / Hibernate |
| Database             | PostgreSQL                  |
| Containerization     | Docker                      |
| Container Management | Docker Compose              |
| Build Tool           | Maven                       |
| Data Format          | JSON                        |

---

# 7. Data Architecture

## 7.1 Database

PostgreSQL will be used as the primary database.

The system contains highly related data involving users, profiles, jobs, and applications, making a relational database appropriate.

## 7.2 Main Entities

The main entities are:

```text
User
JobSeekerProfile
Document
Job
JobApplication
```

## 7.3 Main Relationships

```text
User
 │
 ├─────────────── 1 : 1 ─────────────── JobSeekerProfile
 │                                      │
 │                                      │ 1 : Many
 │                                      ▼
 │                                  Document
 │
 │
 └─────────────── 1 : Many ─────────── Job
                                          │
                                          │
                                          │ 1 : Many
                                          ▼
                                   JobApplication
                                          ▲
                                          │
                                          │
                                   Job Seeker
```

More specifically:

```text
User
 ├── can be JOB_SEEKER
 │      └── JobSeekerProfile
 │             └── Documents
 │
 ├── can be EMPLOYER
 │      └── Jobs
 │             └── Applications
 │
 └── can be ADMIN
```

## 7.4 Application Constraint

A Job Seeker must not be able to apply for the same job more than once.

Therefore, the database should enforce uniqueness on:

```text
(job_id, job_seeker_id)
```

The application service will also check this condition before creating an application.

---

# 8. API / Communication Architecture

The frontend will communicate with the backend through REST APIs using JSON.

```text
Frontend
    │
    │ HTTP/HTTPS
    │ JSON
    ▼
Spring Boot REST API
    │
    ▼
Services
    │
    ▼
Repositories
    │
    ▼
PostgreSQL
```

## 8.1 Main API Groups

### Authentication

```text
/api/auth/register
/api/auth/login
```

### Profiles

```text
/api/profiles/me
/api/profiles/me/documents
```

### Jobs

```text
/api/jobs
/api/jobs/{id}
/api/jobs/my-jobs
```

### Applications

```text
/api/applications
/api/applications/my-applications
/api/applications/jobs/{jobId}
/api/applications/{id}/status
```

### Administration

```text
/api/admin/users
/api/admin/users/{id}/verify
/api/admin/users/{id}
```

The exact endpoint naming can be finalized during API implementation.

---

# 9. Security Architecture

Security will be implemented using **Spring Security and JWT**.

## 9.1 Authentication Flow

```text
User
 │
 │ Login credentials
 ▼
Auth Controller
 │
 ▼
Auth Service
 │
 ├── Find User
 ├── Verify Password
 └── Generate JWT
 │
 ▼
JWT returned to client
```

For protected requests:

```text
Frontend
   │
   │ Authorization: Bearer <JWT>
   ▼
JWT Authentication Filter
   │
   ├── Validate JWT
   ├── Extract User
   └── Extract Role
   │
   ▼
Spring Security
   │
   ▼
Controller
```

## 9.2 Role-Based Authorization

The system will use three roles:

```text
JOB_SEEKER
EMPLOYER
ADMIN
```

Examples:

```text
JOB_SEEKER
    → Apply for jobs
    → Manage own profile

EMPLOYER
    → Create jobs
    → Manage own jobs
    → Review applications for own jobs

ADMIN
    → Manage users
```

Authorization will be enforced by Spring Security.

## 9.3 Password Security

Passwords will never be stored as plain text.

They will be securely hashed before being stored in PostgreSQL.

---

# 10. Deployment Architecture

Docker will be used to containerize the backend and database.

```text
                    Internet
                       │
                       ▼
                 Frontend
                       │
                    HTTPS
                       │
                       ▼
              ┌─────────────────┐
              │ Spring Boot     │
              │ Docker Container│
              │      :8080      │
              └────────┬────────┘
                       │
                    JDBC
                       │
                       ▼
              ┌─────────────────┐
              │ PostgreSQL      │
              │ Docker Container│
              │      :5432      │
              └────────┬────────┘
                       │
                       ▼
                Docker Volume
```

Docker Compose will be used to run the backend and PostgreSQL together.

Example structure:

```text
project/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── src/
```

The PostgreSQL data will be stored using a Docker volume so that database data persists when the container is restarted.

---

# 11. Non-Functional Requirements

The architecture should support the following qualities.

## 11.1 Maintainability

The feature-based module structure allows developers to work on specific areas without navigating a large collection of unrelated classes.

For example:

```text
auth/
profile/
job/
application/
admin/
```

Each module has a clear responsibility.

## 11.2 Scalability

The modular monolith allows additional functionality to be added without immediately introducing microservices.

New functionality can be added as another module when required.

## 11.3 Security

Security is provided through:

- JWT authentication
- Spring Security
- Role-based authorization
- Password hashing
- Protected REST endpoints
- Server-side authorization checks

## 11.4 Reliability

The system uses PostgreSQL for persistent relational data and Docker volumes for persistent database storage.

## 11.5 Performance

The backend will use Spring Data JPA and PostgreSQL for efficient database access.

Database queries should retrieve only the data required by each operation, particularly when listing jobs and applications.

## 11.6 Maintainability Through Separation of Concerns

Controllers, services, repositories, entities, and DTOs will have separate responsibilities.

This prevents business logic from being placed directly inside controllers and keeps database operations separate from API handling.

---

# 12. Architecture Decisions

## ADR 1: Modular Monolith

**Decision:** Use a modular monolithic Spring Boot application.

**Reason:**

The current system is not large enough to require independently deployed microservices. A modular monolith is simpler to develop, test, deploy, and maintain while still providing clear separation between functional areas.

---

## ADR 2: PostgreSQL

**Decision:** Use PostgreSQL as the database.

**Reason:**

The platform contains strongly related data such as users, profiles, jobs, and applications. A relational database provides relationships, constraints, and transactional consistency required by these operations.

---

## ADR 3: JWT Authentication

**Decision:** Use JWT with Spring Security.

**Reason:**

JWT provides stateless authentication for the REST API and works well with a separate frontend application.

---

## ADR 4: Feature-Based Package Structure

**Decision:** Organize the backend primarily by business module.

**Reason:**

Developers can work on individual modules without all developers modifying shared controller, service, and repository folders.

For example:

```text
Developer A → auth/
Developer B → profile/
Developer C → job/
Developer D → application/
Developer E → admin/
```

This also keeps related code together and makes the project easier to understand.

---

## ADR 5: Docker

**Decision:** Use Docker and Docker Compose.

**Reason:**

Docker provides a consistent development and deployment environment for the Spring Boot application and PostgreSQL database.

---

# 13. Risks and Constraints

## 13.1 Team Development Conflicts

Multiple developers may need to work with shared entities such as `User`, `Job`, and `JobApplication`.

**Mitigation:**

Define module ownership and agree on shared entity structures before development begins.

## 13.2 Database Changes

Changes to database entities can affect multiple modules.

**Mitigation:**

Database changes should be discussed and coordinated before being merged.

## 13.3 File Storage

The system requires CV and supporting document uploads.

The storage implementation should be kept within the profile/document functionality and should follow the deployment environment selected for the project.

## 13.4 Authorization Errors

An authenticated user could attempt to access another user's data.

**Mitigation:**

Authorization must be checked on the backend. For example, an employer must only manage their own jobs and applications belonging to those jobs.

---

# 14. Future Considerations

The current architecture is intentionally limited to the requirements of the system.

If the platform grows significantly in the future, individual modules could potentially be separated into independent services.

For example:

```text
Current

Spring Boot
├── Auth
├── Profile
├── Jobs
├── Applications
└── Admin
```

Could later evolve into:

```text
Auth Service
Profile Service
Job Service
Application Service
Admin Service
```

However, this separation is **not required for the current system**.

The initial implementation should remain a modular monolith to avoid unnecessary complexity.

---

# 15. Backend Project Structure

The final Spring Boot project structure will be:

```text
src/
└── main/
    ├── java/
    │   └── com.groupcc2.recruitment/
    │
    │       ├── RecruitmentApplication.java
    │       │
    │       ├── auth/
    │       │   ├── controller/
    │       │   ├── dto/
    │       │   ├── entity/
    │       │   ├── repository/
    │       │   └── service/
    │       │
    │       ├── profile/
    │       │   ├── controller/
    │       │   ├── dto/
    │       │   ├── entity/
    │       │   ├── repository/
    │       │   └── service/
    │       │
    │       ├── job/
    │       │   ├── controller/
    │       │   ├── dto/
    │       │   ├── entity/
    │       │   ├── repository/
    │       │   └── service/
    │       │
    │       ├── application/
    │       │   ├── controller/
    │       │   ├── dto/
    │       │   ├── entity/
    │       │   ├── repository/
    │       │   └── service/
    │       │
    │       ├── admin/
    │       │   ├── controller/
    │       │   ├── dto/
    │       │   └── service/
    │       │
    │       ├── security/
    │       │   ├── SecurityConfig.java
    │       │   ├── JwtService.java
    │       │   ├── JwtAuthenticationFilter.java
    │       │   └── CustomUserDetailsService.java
    │       │
    │       └── common/
    │           └── exception/
    │               ├── GlobalExceptionHandler.java
    │               ├── ResourceNotFoundException.java
    │               └── BadRequestException.java
    │
    └── resources/
        └── application.yml

Dockerfile
docker-compose.yml
pom.xml
```

The important principle is:

```text
                    RECRUITMENT SYSTEM
                           │
       ┌───────────────────┼───────────────────┐
       │                   │                   │
      Auth              Profile              Jobs
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                      Applications
                           │
                         Admin
```

Each module owns its **controllers, DTOs, entities, repositories, and services**, while `security` provides the shared JWT/Spring Security infrastructure.

This gives the team a clear rule: **if you are implementing a feature, first ask which business module owns it, then work inside that module.**
