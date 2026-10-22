# Profile integration contract testing

Use this checklist when joining Profile to your teammate's authentication and
application modules. The interface files are the integration points; no request
may supply the acting user or role. The five own-profile routes are protected
by JWT role checks; employer visibility and database/storage integration remain
separate contracts.

## Contracts and expected behavior

| Contract | Provider | Required behavior |
|---|---|---|
| ProfileIdentity.authenticatedUserId() | Authentication adapter | Return the UUID from verified authentication; reject unauthenticated/malformed identity; never derive the actor from request fields |
| UserService.findById(UUID), requireActive(User) | Existing Users module | Resolve the real account and reject SUSPENDED/REMOVED; Profile also checks persisted UserRole |
| EmployerApplicationAccess.requireSubmittedApplication(employerId, jobId, applicantId) | Applications adapter through Jobs/Applications services | Return normally only for a persisted application by that applicant to that specific employer-owned job; otherwise throw a denial |
| ProfileRls.establish(UUID, role) | Profile transaction boundary | Same-connection, transaction-local context; reject superuser/BYPASSRLS runtime roles |
| PrivateDocumentStorage.upload/delete/signGet | S3 adapter | Private storage, idempotent deletion and expiring GET links; storage exceptions must not expose secrets externally |

Interface source: [ProfileIdentity](../src/main/java/com/groupcc2/recruitment/profile/service/ProfileIdentity.java)
and [EmployerApplicationAccess](../src/main/java/com/groupcc2/recruitment/profile/service/EmployerApplicationAccess.java).
Missing providers deny integration; an always-allow implementation is not a valid
adapter. Profile first verifies the employer's active persisted account/role,
then passes that verified UUID to the application contract.

## Run the existing contract-consumer checks now

From the repository root, with JDK 21:

```bash
sh ./mvnw -B -ntp -Dtest=ProfileServiceTest,DocumentValidatorTest,SecurityBoundaryTest,UserServiceTest test
# Docker required; tests use their own isolated containers/settings.
sh ./mvnw -B -ntp -Dit.test=ProfileRlsIT,MinioStorageIT verify
# Complete suite, including FoundationIT:
sh ./mvnw -B -ntp verify
```

On PowerShell use .\mvnw.cmd instead of sh ./mvnw; quote comma-separated -D
arguments if your shell requires it. test does not run integration tests; verify
does. Results are in target/surefire-reports and target/failsafe-reports.

| Existing test | Consumer expectation it checks |
|---|---|
| ProfileServiceTest.deniesInactive / deniesWrongRole | Calls the Users contract and rejects invalid account access |
| ProfileServiceTest.documentIdCannotOverrideOwnership / foreignDocumentCannotBeSigned | A document ID cannot replace authenticated ownership |
| ProfileServiceTest.employerMustPassSpecificJobApplicationCheck | Passes the exact employer/job/applicant tuple to the application gate; propagates denial before profile lookup; reads after a test gate allows |
| ProfileServiceTest.authorizedDocumentSignsShortLivedGet | Only an owner-scoped document can reach the signing contract; expiry is 120 seconds |
| ProfileServiceTest.storageFailureNeverPersists / flushFailureCleansObject / commitFailureCleansObject | Storage/transaction contract failure behavior |
| ProfileServiceTest.unknownCommitOutcomeRequiresReconciliationInsteadOfDeletingPotentiallyCommittedData | Unknown commit outcome does not delete potentially committed data |
| ProfileServiceTest.cleanupFailureDoesNotHideOriginal / deleteFailureRetainsMetadata | Compensation failure preserves the original failure; deletion failure retains metadata |
| SecurityBoundaryTest.profileIntegrationGateRejectsAllRolesAndMutations | Closed HTTP routes do not invoke ProfileService |
| ProfileRlsIT.ownerIsolationEmployerVisibilityAndTransactionLocalContext | Real restricted-role database policies and context isolation |
| MinioStorageIT.privateBucketUploadSignedGetAndDelete | Real S3 adapter behavior: private upload, signed GET, wrong-method denial, idempotent delete |

ProfileServiceTest uses test-only mocks, including identity, RLS and application
providers. It verifies the consumer's use of contracts; it does **not** verify a
real JWT adapter or prove that an application provider queries real records.
ProfileRlsIT and MinioStorageIT use real separate systems, not a combined full API
workflow. These limits must remain explicit in test reports.

## Authentication provider tests to add when the adapter arrives

Test the actual ProfileIdentity implementation using the real validated security
context and the agreed token subject mapping:

1. Active job seeker A authenticates: adapter returns A's persisted UUID.
2. No authentication, malformed subject, invalid signature, expired token or wrong
   issuer/audience: reject; no profile/storage operation runs.
3. A request includes another user B's identifier: acting UUID remains A; reads,
   lists, deletes and signing never access B's records.
4. A still-valid token belongs to a newly SUSPENDED/REMOVED account: service denies
   through requireActive. A token claim must not override persisted role/status.
5. Two authenticated users reuse pooled connections: context stays transaction-local
   and neither inherits the other's records/settings.

Use actual verification for signature/expiry tests; MockMvc jwt() creates a test
security context and does not prove that token decoding/verification works.
No fake production users, development login shortcuts or public upload routes.

## Applications provider tests to add when the adapter arrives

Create isolated real database fixtures through the owning modules' supported
contracts: employer E1 owns J1, employer E2 owns J2, and seekers A/B exist.
Call the actual EmployerApplicationAccess implementation:

| Fixture/request | Expected |
|---|---|
| E1, J1, A before A applies | Denied |
| E1, J1, A after A submits a real application to J1 | Allowed |
| E2, J1, A even after that application | Denied: wrong employer |
| E1, J2, A | Denied: E1 does not own J2 |
| E1, J1, B when only A applied | Denied: wrong applicant |
| A applies only to another E1-owned job J3, request is for J1 | Denied: require the specific job, not any job of this employer |
| Missing job, applicant or application | Denied safely; no profile/signing lookup |

Then call Profile's readForEmployer, listForEmployer and downloadForEmployer with
that real provider. A document must belong to the authorized applicant; another
applicant's document ID must fail. Employer authorization grants no writes or
deletes. Do not infer extra application status restrictions or transitions.

## Final HTTP workflow and acceptance gates

After verified adapters, a non-bypass runtime DB role, private bucket/account and
role mapping exist, add authentic HTTP tests before deliberately opening routes:

1. Authenticate active seeker A; PUT profile (200), GET profile (200).
2. Upload a valid allowed PDF (201), list metadata (200) with no storage key/public
   URL, and confirm persisted metadata plus the private object.
3. Reject invalid/oversized files; assert no metadata/object leak.
4. Authenticate seeker B; deny access to A's document for deletion/signing.
5. Authenticate employer E1; deny applicant access before application; allow
   read/list/authorized short-lived download only after application to owned J1.
6. Deny E2 for J1 and deny all employer modifications.
7. Delete as A (204); verify metadata and object removal. Exercise storage/DB
   failure recovery as documented in profile-module.md.
8. Check expired/invalid tokens and inactive accounts across the workflow.

Own profile/document route conventions exist in profile-module.md. Employer HTTP
routes and a download HTTP adapter are not exposed on this base; agree those
contracts with their owners before writing route examples. Service download
methods are available for integration tests. Record PASS/FAIL/BLOCKED for each
check with executed test evidence. Health UP or manual mc uploads alone do not
prove these contracts or full HTTP acceptance.

For the actual HTTP routes, Postman setup, curl requests and response/error
contracts, see [Profile and document endpoints](profile-endpoints.md).
