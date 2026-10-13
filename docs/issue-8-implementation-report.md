# Issue #8 implementation report

Status: PARTIALLY BLOCKED. The available Profile/private-storage implementation is
reviewable and tested; the complete HTTP feature cannot be accepted on this base.
See [module documentation](profile-module.md) and [QA handoff](issue-8-qa-handoff.md).

## Verified dependencies

- Branch feature/issue-8-profile-documents starts from c51180f, the existing main
  merge of Users PR #12. Its c65f530 implementation is present, compiled and reused.
- Actual UserService contracts: findById(UUID), requireActive(User); UserRole and
  ACTIVE/SUSPENDED/REMOVED account states are owned by users. No auth/User duplication.
- Security validates JWTs but issues no tokens and defines no verified UUID subject
  or role-authority mapping. origin/feature/authentication is still at 8894bec.
- Jobs and Applications contain package placeholders only, so their employer/job/
  application authorization contract and employer HTTP route are absent.
- V2 forces RLS; existing Compose bootstrap superuser bypasses it. ProfileRls
  rejects bypass roles. Actual restricted-role policies are tested independently.
- Decision/backlog/SRS files referenced by repository instructions are absent.

## Acceptance matrix

PASS below means the named implementation or developer test passes; it does not
mean blocked HTTP integration or independent Antigravity acceptance has passed.

| Criterion | Status | Evidence/remaining integration |
|---|---|---|
| Verify/reuse Issue #11 | PASS | c65f530 in c51180f; existing UserService |
| Preserve user work/no push/merge/closure | PASS | instruction file stays untracked; .env/volumes untouched |
| Existing Java 21/Spring modular architecture | PASS | profile-owned layers; AWS adapter; no duplicate User |
| Existing profile/document schema mapping | PASS | JPA validation in FoundationIT; no edits to V1/V2 |
| GET/PUT own profile behavior | PASS (service) / BLOCKED (HTTP) | ProfileServiceTest; ProfileIdentity absent |
| Upload/list/delete own documents | PASS (service) / BLOCKED (HTTP) | service tests; routes/multipart remain closed |
| Request validation/404/create semantics | PASS (implementation) | DTO limits, service validation, absent profile 404 |
| Anonymous/wrong-role boundary | PASS (closed routes/service) | SecurityBoundaryTest and ProfileServiceTest |
| Inactive account denial | PASS (service) | actual requireActive contract invoked/tested |
| Document-ID ownership and signing guard | PASS (service) | owner-scoped query tests; no client owner field |
| Employer specific-job application gate | PASS (contract) / BLOCKED (integration) | exact arguments, denial propagation; application implementation absent |
| Employer authenticated HTTP access | BLOCKED | no approved application/route contracts; no employer endpoint exposed |
| Employer cannot mutate profile/documents | PASS (RLS/service structure) | V3 restrictive guards and ProfileRlsIT |
| Private MinIO/configuration/persistence | PASS (isolated IT/config) | source Dockerfile, private bucket test, named volume |
| Least-privilege deployment credentials | BLOCKED (local setup) | scoped policy documented; developer must provision bucket/identity |
| Filename/type/MIME/signature/size validation | PASS | DocumentValidatorTest; PDF only, 5 MiB streaming bound |
| Generated keys/safe metadata | PASS | generated-key upload test; DTO contains no storage key |
| Short-lived GET-only signed download | PASS (MinIO/service) | unsigned GET 403, signed GET, expiry 120, PUT 403 |
| Object failure prevents metadata | PASS | ProfileServiceTest |
| Flush/confirmed commit rollback cleanup | PASS | transaction synchronization tests |
| UNKNOWN outcome/cleanup failure observable | PASS (reporting strategy) | safe operation UUID logs; no unsafe deletion on UNKNOWN |
| Delete partial-failure handling | PASS (documented retry strategy) | S3 failure retains metadata test; idempotent S3 IT |
| RLS subject to actual restricted role | PASS (isolated PostgreSQL) / BLOCKED (runtime provisioning) | NOSUPERUSER NOBYPASSRLS fixture; runtime-role rejection |
| Transaction-local user isolation | PASS (PostgreSQL) | commit clears self visibility; different users/employers tested |
| Real authentication-to-RLS integration | BLOCKED | no verified JWT identity adapter on base |
| Unit/full Maven verification | PASS | 28 unit/security + 4 real integration tests; zero failures/errors/skips |
| Existing .env Compose validation | BLOCKED | new MINIO_ROOT_USER/MINIO_ROOT_PASSWORD missing; preserved .env |
| Example-environment Compose validation | PASS | docker compose --env-file .env.example config --quiet |
| Documentation and independent handoff | PASS | module guide, setup/policy/examples, handoff and this report |
| Independent Antigravity acceptance | NOT RUN | separate reviewer must execute own checks |

## Checks and limitations

Final commands use a temporary Maven cache because default ~/.m2 writes are
sandboxed. Maven/Docker checks run with approved access; a sandbox-only run cannot
attach Mockito's agent. Exact commands:

```bash
MAVEN_USER_HOME=/tmp/issue8-maven sh ./mvnw -B -ntp -Dmaven.repo.local=/tmp/issue8-maven/repository test
MAVEN_USER_HOME=/tmp/issue8-maven sh ./mvnw -B -ntp -Dmaven.repo.local=/tmp/issue8-maven/repository verify
docker compose config --quiet
docker compose --env-file .env.example config --quiet
git diff --check
```

Unit result: 28 tests, 0 failures/errors/skips. Integration coverage consists of
4 tests: MinioStorageIT (1), ProfileRlsIT (1), FoundationIT (2). Final verify result: BUILD SUCCESS, 28 unit/security tests plus 4 integration tests,
0 failures/errors/skips, elapsed 01:02 minutes. Logs are preserved at
/tmp/issue8-final-test.log and /tmp/issue8-final-verify.log; reports in
 target/surefire-reports and target/failsafe-reports. Earlier schema-fixture,
MVC-fixture and withdrawn image defects were corrected. MinIO source build and
real PostgreSQL tests passed before the final repeat.

Unresolved work is specific: implement the verified identity adapter and application
authorization contract, provision restricted runtime/migration DB credentials and
private MinIO service identity, supply missing approved decisions, then write real
HTTP integration tests before opening routes. No automatic cleanup queue exists;
operator inventory reconciliation handles process death, UNKNOWN commits or failed
compensation. DB failure after object deletion requires an idempotent owner retry.

No production or existing development containers were redeployed. Independently
started Testcontainers supply the test evidence; the running developer backend
and database are not evidence that this feature was deployed.
