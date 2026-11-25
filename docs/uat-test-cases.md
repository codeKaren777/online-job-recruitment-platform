# COM 311: Software Engineering
# User Acceptance Testing (UAT) – Test Case Design

## 2. Project Information

| Field | Value |
|---|---|
| Student Name | ______________________________ |
| Student ID | ______________________________ |
| Application Name | Online Job Recruitment Platform |
| Target Platform | Web browser using the API/Swagger UI |
| Date | 25/11/26 |
| Template Version | v1.0 |

## 2.1 UAT Scope and Objectives

This document defines planned acceptance tests for the platform’s account and authentication, job seeker profile, employer verification, company, job, application, employer review, and administration workflows. The project repository provides a REST API and Swagger/OpenAPI documentation; it does not contain a user-facing web frontend. Therefore, test steps refer to Swagger UI or an API client and expected results describe the response and resulting workflow state. These cases are designed only and have not been executed.

The objectives are to confirm that users can complete permitted recruitment tasks, invalid data and unauthorized actions are rejected, and records remain restricted to their owners and authorized roles. External email delivery and production infrastructure are outside these UAT cases except where explicitly listed.

## 3. Master UAT Test Scenarios Matrix

| Scenario ID | Module / Workflow | Test Scenario Description | Target Actor(s) | Requirement |
|---|---|---|---|---|
| AUTH_ACCOUNT_001 | Authentication and account | Register and log in; reject invalid credentials; log out. | Job seeker | FR: registration, authentication, session lifecycle |
| AUTH_RESET_001 | Password recovery | Request a reset with registered and unregistered email; reset with valid, expired, and reused tokens. | User | FR: password recovery |
| PROFILE_SELF_001 | Job seeker profile | Create, view, update profile; reject invalid data and unauthorized access. | Job seeker | FR: profile management; profile required before application |
| PROFILE_DOC_001 | Profile documents | Upload, list, download, and delete a private profile document. | Job seeker | FR: private document management |
| USER_EMPLOYER_001 | Employer verification | Submit company details and registration proof; inspect own status; reject invalid upload. | Job seeker / employer applicant | FR: employer verification submission |
| ADMIN_EMPLOYER_001 | Employer verification review | Admin reviews proof and approves/rejects; non-admin is denied. | Administrator | FR: employer request administration |
| COMPANY_OWNER_001 | Company management | Employer creates, lists, and updates owned company; cross-owner access is denied. | Employer | FR: company management and ownership |
| JOB_POST_001 | Job postings | Employer creates and manages jobs; invalid/foreign company data is rejected. | Employer | FR: job posting management |
| JOB_BROWSE_001 | Job search | Job seeker browses, searches, filters, and reads active jobs. | Job seeker | FR: job discovery |
| APP_SUBMIT_001 | Job application | Submit an application with CV and cover letter; require profile and prevent duplicate/closed-job applications. | Job seeker | FR: application submission |
| APP_TRACK_001 | Application tracking | Job seeker lists, reads, updates, and withdraws own pending application. | Job seeker | FR: applicant tracking |
| APP_REVIEW_001 | Employer application review | Job owner reads applicants and updates status; another employer is denied. | Employer | FR: review, shortlist, reject |
| ADMIN_USER_001 | User administration | Admin lists users and performs permitted account administration; other roles are denied. | Administrator | FR: account administration |
| HEALTH_PUBLIC_001 | Health probes | Read minimal health and readiness signals; diagnostics remain unavailable. | Operations user | NFR: safe health monitoring |

## 4. Detailed Test Case Specification

### 4.1 Authentication and account

#### AUTH_ACCOUNT_001 — Register and log in successfully

| Field | Details |
|---|---|
| Test Case ID | AUTH_ACCOUNT_001 |
| Description | Verify that a new job seeker can register and log in with valid credentials. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | The email address is not registered; API/Swagger UI is available. |
| Test Data | Email: `uat.seeker1@example.test`; Password: `ValidPassword123!`; Role: `JOB_SEEKER` |
| Test Steps | 1. Open `POST /api/auth/register` in Swagger UI. <br>2. Submit the test email, password, and `JOB_SEEKER` role. <br>3. Open `POST /api/auth/login`. <br>4. Submit the same email and password. |
| Expected Result | Registration returns `201` with account details and no password hash. Login returns `200` with a bearer access token. |
| Post-conditions | A job seeker account exists and a token is available for subsequent tests. |
| Status |  |

#### JOB_BROWSE_002 — Deny job browsing to an employer

| Field | Details |
|---|---|
| Test Case ID | JOB_BROWSE_002 |
| Description | Verify that an employer cannot use the job seeker job-browsing endpoint. |
| Test Type | Negative |
| Priority | Medium |
| Prerequisites | Active employer is logged in. |
| Test Data | Employer bearer token. |
| Test Steps | 1. Authorize as employer. <br>2. Call `GET /api/jobs`. |
| Expected Result | The API returns `403`; no job results are returned. |
| Post-conditions | No data is changed. |
| Status |  |

#### AUTH_ACCOUNT_002 — Reject invalid login credentials

| Field | Details |
|---|---|
| Test Case ID | AUTH_ACCOUNT_002 |
| Description | Verify that login is rejected when the password does not match the registered account. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | `uat.seeker1@example.test` is registered and active. |
| Test Data | Email: `uat.seeker1@example.test`; Password: `WrongPassword123!` |
| Test Steps | 1. Open `POST /api/auth/login`. <br>2. Submit the test email and incorrect password. |
| Expected Result | The API returns `401` with the generic invalid-credentials error; no access token is returned. |
| Post-conditions | No authenticated session is created. |
| Status |  |

#### AUTH_ACCOUNT_003 — Reject public administrator registration

| Field | Details |
|---|---|
| Test Case ID | AUTH_ACCOUNT_003 |
| Description | Verify that public registration cannot create an administrator account. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | The proposed email is not registered. |
| Test Data | Email: `uat.admin-attempt@example.test`; Password: `ValidPassword123!`; Role: `ADMIN` |
| Test Steps | 1. Open `POST /api/auth/register`. <br>2. Submit the test data with role `ADMIN`. |
| Expected Result | The API returns `400`; no account is created. |
| Post-conditions | The proposed email remains unregistered. |
| Status |  |

#### AUTH_ACCOUNT_004 — Log out and reject the revoked token

| Field | Details |
|---|---|
| Test Case ID | AUTH_ACCOUNT_004 |
| Description | Verify that logging out invalidates the caller’s current bearer token. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | A valid job seeker access token has been issued. |
| Test Data | Authorization: valid bearer token from `AUTH_ACCOUNT_001`. |
| Test Steps | 1. Authorize Swagger UI with the token. <br>2. Call `POST /api/auth/logout`. <br>3. Call `GET /api/auth/me` using the same token. |
| Expected Result | Logout returns `204`; the subsequent current-user request returns `401`. |
| Post-conditions | The old token is no longer accepted. |
| Status |  |

#### AUTH_RESET_001 — Request reset without disclosing account existence

| Field | Details |
|---|---|
| Test Case ID | AUTH_RESET_001 |
| Description | Verify that reset initiation returns the same generic response for registered and unregistered email addresses. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | One registered email and one unregistered email are available. |
| Test Data | Emails: `uat.seeker1@example.test` and `unknown.uat@example.test`. |
| Test Steps | 1. Submit the registered email to `POST /api/auth/forgot-password`. <br>2. Record the HTTP status and response body. <br>3. Submit the unregistered email to the same endpoint. <br>4. Compare the HTTP statuses and response bodies. |
| Expected Result | Both responses return `202` and the same generic message; neither response contains a reset token or account-existence indicator. |
| Post-conditions | A reset message may be delivered for a registered account; no token is exposed in the API response. |
| Status |  |

#### AUTH_RESET_002 — Reject expired or reused reset token

| Field | Details |
|---|---|
| Test Case ID | AUTH_RESET_002 |
| Description | Verify that an expired or already consumed password-reset token cannot reset a password. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | A reset token has expired, or has already successfully reset a password. Obtain test tokens through the configured reset delivery channel. |
| Test Data | Expired/reused `resetToken`; new password: `AnotherValidPass123!`. |
| Test Steps | 1. Open `POST /api/auth/reset-password`. <br>2. Submit the expired or previously consumed token with the new password. |
| Expected Result | The API returns `400` with a generic invalid-request error; the password is not changed again. |
| Post-conditions | The expired/reused token remains unusable. |
| Status |  |

### 4.2 Job seeker profile and documents

#### PROFILE_SELF_001 — Create and read own profile

| Field | Details |
|---|---|
| Test Case ID | PROFILE_SELF_001 |
| Description | Verify that an authenticated job seeker can create and read their own profile. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | Job seeker account is active and logged in. |
| Test Data | `{"displayName":"UAT Applicant","fullName":"UAT Test Applicant","dateOfBirth":"1995-06-15","phone":"+265999000111","location":"Blantyre","biography":"Experienced software tester."}` |
| Test Steps | 1. Authorize as the job seeker. <br>2. Submit the JSON to `PUT /api/profile`. <br>3. Call `GET /api/profile`. |
| Expected Result | Update returns `200`; read returns the saved profile fields for the authenticated user. |
| Post-conditions | The job seeker profile exists. |
| Status |  |

#### PROFILE_SELF_002 — Reject profile update with missing display name

| Field | Details |
|---|---|
| Test Case ID | PROFILE_SELF_002 |
| Description | Verify that profile creation is rejected when the required display name is blank. |
| Test Type | Negative |
| Priority | Medium |
| Prerequisites | Job seeker is logged in. |
| Test Data | `{"displayName":"","biography":"Profile without a name"}` |
| Test Steps | 1. Authorize as the job seeker. <br>2. Submit the JSON to `PUT /api/profile`. |
| Expected Result | The API returns `400` with a validation error; no invalid profile is saved. |
| Post-conditions | Existing profile data remains unchanged. |
| Status |  |

#### PROFILE_DOC_001 — Upload and list a profile document

| Field | Details |
|---|---|
| Test Case ID | PROFILE_DOC_001 |
| Description | Verify that a job seeker can upload a valid PDF and see its metadata in their document list. |
| Test Type | Positive |
| Priority | Medium |
| Prerequisites | Job seeker is logged in and has a profile; a valid PDF under 5 MiB is available. |
| Test Data | File: `uat-cv.pdf`, PDF content, size below 5 MiB. |
| Test Steps | 1. Authorize as the job seeker. <br>2. Upload the PDF to `POST /api/profile/documents` using multipart field `file`. <br>3. Call `GET /api/profile/documents`. |
| Expected Result | Upload returns `201`; the list includes metadata for `uat-cv.pdf` and no public storage path. |
| Post-conditions | The private document is associated with the job seeker. |
| Status |  |

#### PROFILE_DOC_002 — Reject unsupported profile document

| Field | Details |
|---|---|
| Test Case ID | PROFILE_DOC_002 |
| Description | Verify that profile document upload rejects a file that is not an allowed PDF. |
| Test Type | Negative |
| Priority | Medium |
| Prerequisites | Job seeker is logged in and has a profile. |
| Test Data | File: `notes.txt`, plain text content. |
| Test Steps | 1. Authorize as the job seeker. <br>2. Upload `notes.txt` to `POST /api/profile/documents` using multipart field `file`. |
| Expected Result | The API returns `400`; the unsupported file is not listed or stored as a profile document. |
| Post-conditions | No new document is associated with the profile. |
| Status |  |

### 4.3 Employer verification and administration

#### USER_EMPLOYER_001 — Submit employer verification request

| Field | Details |
|---|---|
| Test Case ID | USER_EMPLOYER_001 |
| Description | Verify that a job seeker can submit valid company details with a registration proof PDF. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | Active job seeker is logged in; valid proof-of-registration PDF under 5 MiB is available; no pending request exists for the account. |
| Test Data | Company: `UAT Example Ltd`; Description: `Software services`; Contact: `uat-company@example.test`; Phone: `+265999000111`; Location: `Blantyre`; VAT: `VAT-UAT-2026`; Registration: `REG-UAT-2026`; file: `registration-proof.pdf`. |
| Test Steps | 1. Authorize as the job seeker. <br>2. Open `POST /api/employer-requests`. <br>3. Send multipart `application` JSON with the listed company fields. <br>4. Attach `registration-proof.pdf` as multipart part `document`. |
| Expected Result | The API returns `201` and a pending request; account role remains `JOB_SEEKER` until approval. |
| Post-conditions | Request and private proof document are associated with the applicant. |
| Status |  |

#### USER_EMPLOYER_002 — Reject incomplete employer verification request

| Field | Details |
|---|---|
| Test Case ID | USER_EMPLOYER_002 |
| Description | Verify that employer verification submission is rejected when the VAT number is missing. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Active job seeker is logged in and has no pending request. |
| Test Data | Valid request data except `vatNumber` is blank; valid registration PDF under 5 MiB. |
| Test Steps | 1. Authorize as the job seeker. <br>2. Submit the multipart employer request with blank `vatNumber`. |
| Expected Result | The API returns `400` validation error; no pending request is created. |
| Post-conditions | Applicant remains a job seeker without a new verification request. |
| Status |  |

#### ADMIN_EMPLOYER_001 — Approve verified employer request

| Field | Details |
|---|---|
| Test Case ID | ADMIN_EMPLOYER_001 |
| Description | Verify that an administrator can approve a pending request and enable the applicant as an employer. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | Administrator is logged in; a pending request has valid company details and proof. |
| Test Data | Pending `requestId` created by `USER_EMPLOYER_001`. |
| Test Steps | 1. Authorize as administrator. <br>2. Read `GET /api/admin/employer-requests/{requestId}` and review details. <br>3. Read `GET /api/admin/employer-requests/{requestId}/document`. <br>4. Call `PATCH /api/admin/employer-requests/{requestId}/approve`. |
| Expected Result | Approval succeeds; request status becomes approved, the company is created, and applicant role changes to `EMPLOYER`. |
| Post-conditions | Employer can access employer operations; approval does not create duplicate company records when retried. |
| Status |  |

#### ADMIN_EMPLOYER_002 — Deny employer request review to job seeker

| Field | Details |
|---|---|
| Test Case ID | ADMIN_EMPLOYER_002 |
| Description | Verify that a job seeker cannot view administrator-only employer verification requests. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Job seeker is logged in; at least one pending request exists. |
| Test Data | Job seeker bearer token; pending request ID. |
| Test Steps | 1. Authorize as the job seeker. <br>2. Call `GET /api/admin/employer-requests`. |
| Expected Result | The API returns `403`; no request details or proof document are returned. |
| Post-conditions | No employer request state changes. |
| Status |  |

### 4.4 Company and job workflows

#### COMPANY_OWNER_001 — Create and list an employer-owned company

| Field | Details |
|---|---|
| Test Case ID | COMPANY_OWNER_001 |
| Description | Verify that an employer can create a company and retrieve it from their company list. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | Active employer is logged in. |
| Test Data | `{"name":"UAT Example Ltd","description":"Software services","contactEmail":"uat-company@example.test","contactPhone":"+265999000111","location":"Blantyre"}` |
| Test Steps | 1. Authorize as employer. <br>2. Submit the JSON to `POST /api/companies`. <br>3. Call `GET /api/companies`. |
| Expected Result | Company creation returns `201`; the new company appears in the caller’s list. |
| Post-conditions | Company is owned by the authenticated employer. |
| Status |  |

#### COMPANY_OWNER_002 — Deny access to another employer’s company

| Field | Details |
|---|---|
| Test Case ID | COMPANY_OWNER_002 |
| Description | Verify that an employer cannot read a company owned by a different employer. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Two active employers exist; employer B owns a company; employer A is logged in. |
| Test Data | Employer A token; Employer B `companyId`. |
| Test Steps | 1. Authorize as employer A. <br>2. Call `GET /api/companies/{companyId}` using employer B’s company ID. |
| Expected Result | The API returns `404` or `403` as specified for a non-owned company; no company details are disclosed. |
| Post-conditions | Company data remains unchanged. |
| Status |  |

#### JOB_POST_001 — Create a job for an owned company

| Field | Details |
|---|---|
| Test Case ID | JOB_POST_001 |
| Description | Verify that an employer can publish a valid job for a company they own. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | Active employer is logged in and owns a company. |
| Test Data | `{"companyId":"<owned-company-uuid>","title":"UAT Java Developer","description":"Develop backend services.","requiredQualifications":"Java, Spring Boot","dueDate":"2099-12-31","location":"Blantyre"}` |
| Test Steps | 1. Authorize as employer. <br>2. Submit the JSON to `POST /api/jobs`. |
| Expected Result | The API returns `201`, job details, and a `Location` header for the created job. |
| Post-conditions | Job is associated with the employer-owned company. |
| Status |  |

#### JOB_POST_002 — Reject a job using a company owned by another employer

| Field | Details |
|---|---|
| Test Case ID | JOB_POST_002 |
| Description | Verify that an employer cannot create a job for a company they do not own. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Employer A is logged in; employer B owns the company identified in test data. |
| Test Data | Valid job JSON with `companyId` belonging to employer B. |
| Test Steps | 1. Authorize as employer A. <br>2. Submit the job JSON to `POST /api/jobs`. |
| Expected Result | The API denies the operation with `403` or `404`; no job is created. |
| Post-conditions | Neither employer’s company nor job data is changed. |
| Status |  |

#### JOB_BROWSE_001 — Browse and search active jobs

| Field | Details |
|---|---|
| Test Case ID | JOB_BROWSE_001 |
| Description | Verify that a job seeker can browse active jobs and filter results by query and location. |
| Test Type | Positive |
| Priority | Medium |
| Prerequisites | Job seeker is logged in; at least one matching active job exists. |
| Test Data | Query: `Java`; Location: `Blantyre`; Page: `0`; Size: `20`. |
| Test Steps | 1. Authorize as job seeker. <br>2. Call `GET /api/jobs?q=Java&location=Blantyre&page=0&size=20`. |
| Expected Result | The API returns `200` with a page of matching active jobs and pagination metadata. |
| Post-conditions | No data is changed. |
| Status |  |

### 4.5 Application workflow

#### APP_SUBMIT_001 — Apply to an active job with profile and CV

| Field | Details |
|---|---|
| Test Case ID | APP_SUBMIT_001 |
| Description | Verify that a profiled job seeker can submit one application with a CV and cover letter. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | Job seeker is logged in, profile exists, target job is active and open, and a valid PDF CV under 5 MiB is available. |
| Test Data | `jobId`: open job UUID; `coverLetter`: `I am interested in this role.`; file: `uat-cv.pdf`. |
| Test Steps | 1. Authorize as job seeker. <br>2. Submit multipart `jobId`, `coverLetter`, and `cv` to `POST /api/applications`. |
| Expected Result | The API returns `201` with a `PENDING` application; the application is associated with the authenticated user and selected job. |
| Post-conditions | One application exists for the seeker/job pair. |
| Status |  |

#### APP_SUBMIT_002 — Require profile before applying

| Field | Details |
|---|---|
| Test Case ID | APP_SUBMIT_002 |
| Description | Verify that an active job seeker without a profile cannot apply for a job. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Job seeker is logged in and has no profile; target job is open. |
| Test Data | Open job UUID; valid cover letter; valid PDF CV. |
| Test Steps | 1. Authorize as the job seeker without a profile. <br>2. Submit application multipart data to `POST /api/applications`. |
| Expected Result | The API rejects the request with `409 PROFILE_REQUIRED`; no application is created. |
| Post-conditions | No application exists for this seeker/job pair. |
| Status |  |

#### APP_SUBMIT_003 — Prevent duplicate application

| Field | Details |
|---|---|
| Test Case ID | APP_SUBMIT_003 |
| Description | Verify that a job seeker cannot submit a second application for the same job. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Job seeker has a profile and an existing application for the open job. |
| Test Data | Existing job UUID; valid cover letter and PDF CV. |
| Test Steps | 1. Authorize as the job seeker. <br>2. Submit another application for the same job. |
| Expected Result | The API returns `409` duplicate-application error; the original application remains unchanged. |
| Post-conditions | Exactly one application exists for the user/job pair. |
| Status |  |

#### APP_TRACK_001 — View and withdraw own pending application

| Field | Details |
|---|---|
| Test Case ID | APP_TRACK_001 |
| Description | Verify that a job seeker can view and withdraw their own pending application. |
| Test Type | Positive |
| Priority | Medium |
| Prerequisites | Job seeker is logged in and has a `PENDING` application. |
| Test Data | Own application UUID. |
| Test Steps | 1. Call `GET /api/applications`. <br>2. Call `GET /api/applications/{applicationId}` for the listed application. <br>3. Call `DELETE /api/applications/{applicationId}`. |
| Expected Result | List and detail return the caller’s application; withdrawal returns `204` and retains application history as withdrawn. |
| Post-conditions | Application is withdrawn and cannot be edited as pending. |
| Status |  |

#### APP_TRACK_002 — Deny access to another job seeker’s application

| Field | Details |
|---|---|
| Test Case ID | APP_TRACK_002 |
| Description | Verify that a job seeker cannot read an application submitted by another job seeker. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Two job seekers exist; seeker B owns an application; seeker A is logged in. |
| Test Data | Seeker A token; seeker B application UUID. |
| Test Steps | 1. Authorize as seeker A. <br>2. Call `GET /api/applications/{applicationId}` using seeker B’s application ID. |
| Expected Result | The API returns `404`; application data is not disclosed. |
| Post-conditions | Application remains unchanged. |
| Status |  |

#### APP_REVIEW_001 — Employer shortlists an applicant for own job

| Field | Details |
|---|---|
| Test Case ID | APP_REVIEW_001 |
| Description | Verify that the employer who owns the job can review an application and shortlist its applicant. |
| Test Type | Positive |
| Priority | High |
| Prerequisites | Employer owns the job; a job seeker has submitted a pending application to it. |
| Test Data | Application UUID; body: `{"status":"SHORTLISTED"}`. |
| Test Steps | 1. Authorize as the owning employer. <br>2. Call `GET /api/employer/applications?jobId=<job-uuid>`. <br>3. Call `PATCH /api/employer/applications/{applicationId}/status` with the test body. |
| Expected Result | Application details are returned; status update succeeds with `SHORTLISTED`. |
| Post-conditions | Decision is recorded and applicant status reflects `SHORTLISTED`. |
| Status |  |

#### APP_REVIEW_002 — Deny another employer access to application

| Field | Details |
|---|---|
| Test Case ID | APP_REVIEW_002 |
| Description | Verify that an employer who does not own the relevant job cannot view its application. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Employer A is logged in; employer B owns the job with an application. |
| Test Data | Application UUID associated with employer B’s job. |
| Test Steps | 1. Authorize as employer A. <br>2. Call `GET /api/employer/applications/{applicationId}`. |
| Expected Result | The API denies access with `403` or `404`; applicant profile and CV data are not disclosed. |
| Post-conditions | Application remains unchanged. |
| Status |  |

### 4.6 Health and access boundary

#### HEALTH_PUBLIC_001 — Readiness is minimal and diagnostics remain private

| Field | Details |
|---|---|
| Test Case ID | HEALTH_PUBLIC_001 |
| Description | Verify that the readiness endpoint reports only a minimal status and diagnostic management endpoints are not public. |
| Test Type | Positive |
| Priority | Medium |
| Prerequisites | Application is running in the test deployment. |
| Test Data | None |
| Test Steps | 1. Call `GET /actuator/health/readiness` without authentication. <br>2. Call `GET /actuator/env` without authentication. <br>3. Call `GET /actuator/metrics` without authentication. |
| Expected Result | Readiness returns `200` and minimal status while dependencies are healthy; environment and metrics endpoints are not exposed and return `404` or `403`. |
| Post-conditions | No configuration details or metrics are disclosed. |
| Status |  |

#### HEALTH_PUBLIC_002 — Readiness fails when the database is unavailable

| Field | Details |
|---|---|
| Test Case ID | HEALTH_PUBLIC_002 |
| Description | Verify that readiness reports unavailable when PostgreSQL cannot be reached while liveness remains available. |
| Test Type | Negative |
| Priority | High |
| Prerequisites | Isolated test deployment where PostgreSQL availability can be safely interrupted and restored. |
| Test Data | None |
| Test Steps | 1. Confirm `GET /actuator/health/readiness` returns `200`. <br>2. Stop PostgreSQL in the isolated test deployment. <br>3. Call readiness again. <br>4. Call `GET /actuator/health/liveness`. <br>5. Restart PostgreSQL. <br>6. Call readiness after recovery. |
| Expected Result | Readiness becomes `503` while PostgreSQL is down; liveness remains `200`; readiness returns `200` after PostgreSQL recovers. No dependency details are exposed. |
| Post-conditions | PostgreSQL is running and readiness is restored. |
| Status |  |
