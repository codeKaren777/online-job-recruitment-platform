# Independent Antigravity handoff — Issue #8

Branch: feature/issue-8-profile-documents. Use the exact final commit reported by
Codex, not origin/main. No push, PR, merge or issue closure is performed.

```bash
git worktree add --detach /tmp/issue8-antigravity <REPORTED_COMMIT_SHA>
cd /tmp/issue8-antigravity
java -version # JDK 21
# Docker access and network for Maven, Go modules, Debian and base images required.
docker compose --env-file .env.example config --quiet
sh ./mvnw -B -ntp test
sh ./mvnw -B -ntp verify
```

Testcontainers starts isolated PostgreSQL 17, source-built MinIO and Ryuk and
cleans those test containers; it does not need developer .env or persistent
Compose services. Existing developer volumes must be preserved. Do not skip tests
or use privileged runtime connections to claim RLS coverage.

Changed areas: profile controller/DTO/entities/repositories/services/storage,
scoped common exception handler, V3 restrictive mutation policies, AWS SDK BOM,
Compose/source-build MinIO Dockerfile, environment/configuration, tests and docs.
FoundationIT's migration expectation is corrected from V1 to current V3.
SecurityBoundaryTest mocks the new controller dependency in its MVC slice and
asserts the blocked profile routes. Production SecurityConfig is unchanged.

Read docs/profile-module.md first. This is a dependency-gated implementation,
not acceptance certification. Verify Issue #11 contracts at c65f530 (merged in
c51180f), and confirm no duplicate User entity/authentication implementation.

Threat checks: attempt ownership overrides and foreign document identifiers;
wrong-role/inactive identities; employer access before applications and to another
employer's job; ensure UUID comes only from validated identity; connection-pool
transaction-local RLS isolation and role bypass rejection; employer DELETE policy
regression; filename traversal/controls, stream-size lies, PDF signature/MIME;
public bucket policy/ACL rejection, generated keys, GET-only signed URL expiry;
flush/commit/UNKNOWN outcomes and rollback cleanup logging; delete retry behavior.

Full HTTP acceptance is BLOCKED until identity, employer-application contracts and
restricted runtime DB provisioning exist. Add authentic HTTP tests before opening
routes. Don't replace missing dependencies with always-allow contracts or token
role-only checks. Multipart remains disabled for this reason.

Independent acceptance requires passing unit and real PostgreSQL/MinIO tests,
then end-to-end HTTP role/ownership checks with verified authentication and
restricted runtime credentials. Source-built MinIO setup is tested by the same
Dockerfile used in Compose. Local root credentials only provision the bucket;
application credentials must use the scoped policy in profile-module.md.

Known consistency limits: no automatic durable cleanup queue; inventory reconciliation
is needed after process death, UNKNOWN commit outcome or logged compensation
failure. A failed database commit after object deletion leaves metadata requiring
an idempotent owner retry. Confirm these limits are acceptable before deployment.
