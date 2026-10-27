# System Architecture

## Overview

The Online Job Recruitment Platform is a Spring Boot modular monolith. It is deployed as one backend application, but the source code is separated into feature modules with clear ownership boundaries.

```text
                         ┌─────────────────────┐
                         │   Web Client / API   │
                         └──────────┬──────────┘
                                    │ REST / JSON
                                    ▼
┌──────────────────────────────────────────────────────────┐
│                 Spring Boot Backend                      │
│                                                          │
│  ┌──────────┐  ┌─────────┐  ┌────────┐  ┌─────────────┐ │
│  │  Users   │  │ Profile │  │Company │  │    Jobs     │ │
│  └──────────┘  └─────────┘  └────────┘  └─────────────┘ │
│                                                          │
│        Shared Security, Validation and Exceptions        │
└──────────────────────────┬───────────────────────────────┘
                           │ JPA / JDBC
                           ▼
                 ┌─────────────────────┐
                 │ PostgreSQL Database │
                 └─────────────────────┘

                 Docker / Docker Compose
```

## Main modules

### Users

Owns user identity persistence, roles, account status, registration, and account-management rules. The supported roles are `JOB_SEEKER`, `EMPLOYER`, and `ADMIN`. Other modules use the users service contract and never duplicate the users repository.

### Authentication

Owns credential verification and token issuance. Authentication consumes the users service contract to resolve identities and enforce account state; it does not own or duplicate user persistence.

### Profile

Owns job seeker profiles and private document metadata. A job seeker's profile and documents are visible to an employer only when that job seeker has applied to one of the employer's jobs.

### Jobs

Owns employer job postings. Every posting references a company represented by the authenticated employer. Employers can manage their own postings. A posting is considered expired when its due date is reached or has passed.

### Companies

Owns company identity and contact information for employers. An employer may manage only companies they own, and a job may reference only one of those companies. Existing jobs are assigned a migration-created legacy company so the relationship can become mandatory without losing data.

### Applications

Owns job submissions and application statuses. A job seeker can apply to a particular job only once. New applications start as `PENDING`; employers may review their own applications as `SHORTLISTED` or `REJECTED`.

### Shared infrastructure

- `security` contains JWT validation, password encoding, and Spring Security configuration.
- `common.exception` is reserved for shared exception translation.
- Flyway migrations under `src/main/resources/db/migration` own database changes.

## Source structure

Feature modules use the same layers:

```text
<feature>/
├── controller/   HTTP endpoints
├── dto/          request and response models
├── entity/       persistent domain objects
├── repository/   database access
└── service/      business rules and authorization
```

The current package layout is:

```text
com.groupcc2.recruitment/
├── users/         user identity, registration, and account state
├── auth/          credential verification and token issuance
├── profile/       profiles and documents
├── company/       employer companies and company ownership
├── job/           job postings
├── application/  job applications and statuses
├── admin/         administrative orchestration
├── security/      shared authentication infrastructure
└── common/        shared cross-feature concerns
```

Administration uses user/auth services rather than creating a second user persistence model.

## Request flow

Requests follow this path:

```text
HTTP request
    ↓
Controller
    ↓
Service: validation, business rules and authorization
    ↓
Repository
    ↓
PostgreSQL
```

Controllers handle HTTP concerns. Services enforce role and ownership rules. Repositories handle persistence. Cross-feature access goes through service contracts rather than directly duplicating another module's persistence logic.

## Security boundaries

- JWT bearer tokens are validated by shared Spring Security infrastructure.
- Passwords are stored as hashes, never as plain text.
- Registration, login, forgot-password, and reset-password are deliberate
  public authentication entry points. Current-user, logout, and change-password
  require a valid JWT. Other feature routes remain private unless deliberately
  opened with role and ownership tests.
- JWTs carry the server-issued user UUID, application role, and account token
  version. Spring Security validates signature, issuer, audience, expiry,
  account status, role, and current version before accepting a token.
- Employers can access only applications belonging to their own job postings.
- Employers can create or update jobs only for companies they represent.
- Database row-level security is enabled for application tables; its connection settings must be derived from verified authentication, never from client input.

## Deployment

The local Docker Compose setup uses the existing `POSTGRES_USER` account for
both the backend and Flyway. If that account is a PostgreSQL superuser, it
bypasses RLS; this is explicitly a local-development compromise, not a
production database security model. See [database.md](database.md) for details.

For database tables, enums, indexes, constraints, expiry handling, and RLS
details, see [database.md](database.md). For local commands, see [setup.md](setup.md).
